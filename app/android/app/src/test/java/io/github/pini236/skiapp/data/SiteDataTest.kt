package io.github.pini236.skiapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The data refresh: only valid downloads replace the packaged copy, and nothing breaks an old app. */
class SiteDataTest {
    private val site = File(System.getProperty("site.data") ?: "../../../site/data")
    private val runs = File(site, "runs-and-lifts.json").readText()
    private val videos = File(site, "videos-seed.json").readText()
    private lateinit var dir: File
    private var clock = 1_000_000_000L
    private val calls = mutableListOf<Pair<String, String?>>()

    private fun packaged(name: String) = when (name) {
        "runs-and-lifts.json" -> runs
        "videos-seed.json" -> videos
        else -> error(name)
    }

    /** A site that answers with these bodies and ETags, and 304 when the ETag matches. */
    private fun site(answers: Map<String, Pair<String, String>>) = SiteData.Fetcher { url, etag ->
        val name = url.substringAfterLast('/')
        calls += name to etag
        val (tag, body) = answers.getValue(name)
        if (etag == tag) SiteData.Response(304, tag, null) else SiteData.Response(200, tag, body.toByteArray())
    }

    private fun data(fetcher: SiteData.Fetcher, app: Long = 1) = SiteData(dir, app, ::packaged, fetcher) { clock }

    /** The runs file with one run renamed, as if someone fixed a name on the site. */
    private val newerRuns = runs.replaceFirst("\"name\":\"Tatra 2\"", "\"name\":\"Tatra 2 (new)\"").also { require(it != runs) { "fixture: no Tatra 2 name" } }

    @Before fun setUp() { dir = Files.createTempDirectory("sitedata").toFile() }

    @Test fun packagedFilesAreValid() {
        assertTrue(SiteData.validRuns(runs))
        assertTrue(SiteData.validVideos(videos))
    }

    @Test fun noNetworkKeepsThePackagedCopy() {
        val d = data(SiteData.Fetcher { _, _ -> throw java.io.IOException("offline") })
        assertEquals(SiteData.Outcome.FAILED, d.refresh()["runs-and-lifts.json"])
        assertEquals(runs, d.read("runs-and-lifts.json"))
        assertEquals("packaged", d.source("runs-and-lifts.json"))
    }

    @Test fun aNewerFileIsUsedAndThenAskedForWithItsETag() {
        val d = data(site(mapOf("runs-and-lifts.json" to ("\"a\"" to newerRuns), "videos-seed.json" to ("\"v\"" to videos))))
        val r = d.refresh()
        assertEquals(SiteData.Outcome.UPDATED, r["runs-and-lifts.json"])
        assertEquals(SiteData.Outcome.UNCHANGED, r["videos-seed.json"]) // same as packaged: nothing stored
        assertEquals(newerRuns, d.read("runs-and-lifts.json"))
        assertEquals(videos, d.read("videos-seed.json"))
        // within six hours nothing is asked; after that the ETags go out and the site says "not modified"
        assertTrue(d.refresh().values.all { it == SiteData.Outcome.SKIPPED })
        clock += SiteData.EVERY_MS
        calls.clear()
        assertTrue(d.refresh().values.all { it == SiteData.Outcome.UNCHANGED })
        assertEquals(listOf("runs-and-lifts.json" to "\"a\"", "videos-seed.json" to "\"v\""), calls)
        // a new process reads the same state from disk
        assertEquals(newerRuns, data(site(emptyMap())).read("runs-and-lifts.json"))
    }

    @Test fun brokenOrUnknownDownloadsAreRejected() {
        for (bad in listOf(runs.substring(0, runs.length / 2), "<html>error</html>", "{\"schema\":2,\"pistes\":[],\"lifts\":[]}", "{\"pistes\":[],\"lifts\":[]}")) {
            val d = data(site(mapOf("runs-and-lifts.json" to ("\"x\"" to bad), "videos-seed.json" to ("\"v\"" to videos))))
            assertEquals(bad.take(20), SiteData.Outcome.REJECTED, d.refresh(force = true)["runs-and-lifts.json"])
            assertEquals(runs, d.read("runs-and-lifts.json"))
        }
        assertFalse(SiteData.validVideos("{\"schema\":2,\"videos\":[]}"))
        assertFalse(SiteData.validVideos("[{\"piste\":\"Tatra 2\",\"url\":\"http://insecure\"}]"))
    }

    @Test fun aBadDownloadNeverReplacesAGoodOne() {
        val good = data(site(mapOf("runs-and-lifts.json" to ("\"a\"" to newerRuns), "videos-seed.json" to ("\"v\"" to videos))))
        good.refresh()
        val d = data(site(mapOf("runs-and-lifts.json" to ("\"b\"" to "{oops"), "videos-seed.json" to ("\"v\"" to videos))))
        assertEquals(SiteData.Outcome.REJECTED, d.refresh(force = true)["runs-and-lifts.json"])
        assertEquals(newerRuns, d.read("runs-and-lifts.json"))
    }

    @Test fun aDamagedFileOnDiskFallsBackToThePackagedCopy() {
        data(site(mapOf("runs-and-lifts.json" to ("\"a\"" to newerRuns), "videos-seed.json" to ("\"v\"" to videos)))).refresh()
        File(dir, "runs-and-lifts.json").writeText("{half")
        val d = data(site(emptyMap()))
        assertEquals(runs, d.read("runs-and-lifts.json"))
        assertEquals("packaged", d.source("runs-and-lifts.json"))
    }

    @Test fun aNewAppVersionStartsFromItsOwnPackagedCopy() {
        data(site(mapOf("runs-and-lifts.json" to ("\"a\"" to newerRuns), "videos-seed.json" to ("\"v\"" to videos)))).refresh()
        val next = data(site(emptyMap()), app = 2)
        assertEquals(runs, next.read("runs-and-lifts.json"))
        assertFalse(File(dir, "runs-and-lifts.json").exists())
    }
}

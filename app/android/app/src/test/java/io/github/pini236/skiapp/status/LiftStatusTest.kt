package io.github.pini236.skiapp.status

import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.Runs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** The lift status reads the site's /api/status the way the site does (LSTAT in site/js/app.js). */
class LiftStatusTest {
    private val site = File(System.getProperty("site.data") ?: "../../../site/data").parentFile
    private val app = File(site, "js/app.js").readText()
    private val t0 = Instant.parse("2027-01-12T08:00:00Z").toEpochMilli()

    private fun report(json: String) = Report.parse(json)!!
    private val sample = """{"updated":"2027-01-12T08:00:00Z",
        "lifts":{"Goodaura":{"open":true},"Sadzele":{"open":false,"reason":"wind"},"Kudebi":{"open":false}},
        "pistes":{"Tatra 2":{"open":false},"Kudebi 1":{"open":true}}}"""
    private fun piste(key: String, vararg up: String) = Piste(key, key, "blue", true, "run", emptyList(), fromLifts = up.toList())

    @Test fun theSitesRules() {
        assertTrue("30 minutes, as on the site", app.contains("STALE=30*6e4"))
        assertTrue("the same file", app.contains("fetch('api/status'"))
        assertTrue("every five minutes", app.contains("setInterval(load,5*6e4)"))
        assertEquals(30 * 60_000L, LiftStatus.STALE_MS)
        assertEquals(5 * 60_000L, LiftStatus.EVERY_MS)
    }

    @Test fun aReportNeedsATimeAndLifts() {
        assertNull(Report.parse("""{"lifts":{}}"""))
        assertNull(Report.parse("""{"updated":"2027-01-12T08:00:00Z"}"""))
        assertNull(Report.parse("<html>not found</html>"))
        assertEquals(t0, report("""{"updated":"2027-01-12T12:00:00+04:00","lifts":{}}""").updated)
        val r = report(sample)
        assertEquals(t0, r.updated)
        assertEquals("wind", r.lifts["Sadzele"]!!.reason)
        assertNull(r.lifts["Kudebi"]!!.reason)
        assertEquals(false, r.pistes["Tatra 2"])
    }

    @Test fun olderThanHalfAnHourIsNoInformation() {
        val names = listOf("Goodaura", "Sadzele", "Kudebi", "Zuma")
        val fresh = LiftStatus(names, report(sample), t0 + 29 * 60_000L)
        assertTrue(fresh.fresh)
        assertEquals(true, fresh.isOpen("Goodaura"))
        assertEquals(false, fresh.isOpen("Sadzele"))
        assertNull("the report says nothing about it", fresh.isOpen("Zuma"))
        assertEquals(1, fresh.open)
        assertEquals("wind", fresh.reason("Sadzele"))
        val stale = LiftStatus(names, report(sample), t0 + 31 * 60_000L)
        assertFalse(stale.fresh)
        assertNull(stale.isOpen("Goodaura"))
        assertNull(stale.reason("Sadzele"))
        assertEquals(0, stale.open)
        assertFalse(LiftStatus(names, null, t0).fresh)
        assertEquals(6, LiftStatus(names, report(sample), t0 + 6 * 60_000L + 20_000).minutesAgo)
    }

    @Test fun aRunIsOpenForMeOnlyWithALiftUpToIt() {
        val s = LiftStatus(emptyList(), report(sample), t0)
        assertEquals("closed in the report", false, s.runOpen(piste("Tatra 2", "Goodaura")))
        assertEquals("a lift up to it is open", true, s.runOpen(piste("Soliko 1", "Goodaura", "Sadzele")))
        assertEquals("every lift up to it is closed", false, s.runOpen(piste("Kudebi 1", "Kudebi")))
        assertEquals("no lift in the report: the run's own state", null, s.runOpen(piste("Zuma 1", "Zuma")))
        assertNull(LiftStatus(emptyList(), report(sample), t0 + 31 * 60_000L).runOpen(piste("Tatra 2")))
    }

    @Test fun whatChangedSinceYouChecked() {
        val names = listOf("Goodaura", "Sadzele", "Kudebi", "Zuma")
        val s = LiftStatus(names, report(sample), t0)
        assertEquals(emptyList<Pair<String, Boolean>>(), s.changes(null))
        val before = mapOf("Goodaura" to false, "Sadzele" to false, "Kudebi" to true, "Zuma" to true)
        assertEquals(listOf("Goodaura" to true, "Kudebi" to false), s.changes(before))
        assertEquals(mapOf("Goodaura" to true, "Sadzele" to false, "Kudebi" to false, "Zuma" to null), s.snapshot())
    }

    @Test fun theBoardHasTheSitesLifts() {
        val runs = Runs.parse(File(site, "data/runs-and-lifts.json").readText())
        val names = LiftStatus.names(runs.mainLifts)
        assertEquals(12, names.size)
        assertTrue(names.containsAll(listOf("Goodaura", "New Goodaura", "Sadzele", "Kudebi", "Snow Park")))
        assertTrue("runs know their lifts", runs.pistes.count { it.fromLifts.isNotEmpty() } >= 20)
    }
}

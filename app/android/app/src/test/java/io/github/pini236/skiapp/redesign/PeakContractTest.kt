package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Terrain
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The painted mountain's contract (site/data/peak.json) against its test values (tools/fixtures/peak-projection.json),
 * which tools/build-peak.py writes: where each point is drawn (half a point), whether it is seen, and which run a tap
 * picks. The heights come from the shipped model, as the app reads it.
 */
class PeakContractTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val peak by lazy { PeakContract.parse(File(dir, "peak.json").readText()) }
    private val fix by lazy { JSONObject(File(dir.parentFile.parentFile, "tools/fixtures/peak-projection.json").readText()) }
    private val terrain by lazy { Terrain.parse(File(dir, "terrain.json").readText()) }

    @Test fun twoViewsAndOnlyTheFrontIsPacked() {
        assertEquals(listOf("front", "kobi"), peak.views.map { it.id })
        assertTrue(peak.view("front")!!.packaged && peak.view("front")!!.rev != null)
        val k = peak.view("kobi")!!
        assertTrue(!k.packaged && k.rev != null && k.path == "img/peak/kobi/${k.rev}/")
        assertEquals(7, k.images.size)
        // the architect, 27.11.2026: the set up to 1.1 MB (3 px per point), each picture up to 180 KB
        assertTrue("the Kobi set is ${k.images.sumOf { it.bytes }} bytes", k.images.sumOf { it.bytes } <= 1100 * 1024)
        assertTrue("a Kobi picture is over 180 KB", k.images.all { it.bytes <= 180 * 1024 })
    }

    @Test fun pointsAreWhereTheTestValuesSay() {
        val tol = fix.getDouble("tol").toFloat(); val pts = fix.getJSONArray("points")
        assertTrue(pts.length() >= 20)
        for (i in 0 until pts.length()) {
            val p = pts.getJSONObject(i); val v = peak.view(p.getString("view"))!!; val what = p.getString("what")
            val x = Geo.x(p.getDouble("lon")).toDouble(); val y = Geo.y(p.getDouble("lat")).toDouble()
            val z = terrain.elev(x.toFloat(), y.toFloat()).toDouble()
            assertEquals("$what: height", p.getDouble("z"), z, 0.05)
            val s = v.project(x, y, z)
            assertEquals("$what: x", p.getDouble("x").toFloat(), s.x, tol)
            assertEquals("$what: y", p.getDouble("y").toFloat(), s.y, tol)
            assertEquals("$what: seen", p.getBoolean("visible"), v.visible(s))
        }
    }

    @Test fun tapsPickTheSameRun() {
        val taps = fix.getJSONArray("taps")
        for (i in 0 until taps.length()) {
            val t = taps.getJSONObject(i); val v = peak.view(t.getString("view"))!!
            val want = if (t.isNull("run")) null else t.getString("run")
            assertEquals("tap ${t.getString("view")} ${t.getDouble("x")},${t.getDouble("y")}", want,
                v.tap(t.getDouble("x").toFloat(), t.getDouble("y").toFloat(), peak.radius))
        }
    }

    @Test fun theKobiPicturesInTheSiteAreTheOnesListed() {
        val k = peak.view("kobi")!!
        for (im in k.images) {
            val f = File(dir.parentFile, k.path + im.file)
            assertEquals(im.file, im.bytes.toLong(), f.length())
            val h = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(im.file, im.sha256, h)
        }
    }
}

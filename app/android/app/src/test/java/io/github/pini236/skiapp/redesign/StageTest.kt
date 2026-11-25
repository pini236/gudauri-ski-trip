package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * The stage under the mountain's layers (the architect's Stage, 25.11.2026): one tap for the picture and the live model
 * (condition 2), against the contract's test values; what shows when (condition 5, the saved choice); and the swap,
 * which gives the coming stage what the layers set and never leaves the old one in use (condition 3).
 */
class StageTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val peak by lazy { PeakContract.parse(File(dir, "peak.json").readText()) }
    private val fix by lazy { JSONObject(File(dir.parentFile.parentFile, "tools/fixtures/peak-projection.json").readText()) }

    @Test fun theRadiusIsTheContracts() = assertEquals(peak.radius, StagePick.RADIUS_DP, 0f)

    /** the picture's tap: the contract's lines on the screen (at the fit of a 390-point-wide phone) through the one pick */
    @Test fun thePictureTapsAsTheTestValuesSay() {
        val taps = fix.getJSONArray("taps")
        assertTrue(taps.length() >= 20)
        for (i in 0 until taps.length()) {
            val t = taps.getJSONObject(i); val v = peak.view(t.getString("view"))!!
            val want = if (t.isNull("run")) null else t.getString("run")
            val x = t.getDouble("x").toFloat(); val y = t.getDouble("y").toFloat()
            assertEquals("${v.id} $x,$y", want, StagePick.nearest(PictureStage.linesOn(v, Place(1f, 0f, 0f)), x, y, StagePick.RADIUS_DP))
            // the same tap on a phone twice as dense, the picture twice as big and moved: the same run
            val pl = Place(2f, -37f, 11f)
            assertEquals("${v.id} $x,$y at 2x", want, StagePick.nearest(PictureStage.linesOn(v, pl), x * 2 - 37f, y * 2 + 11f, StagePick.RADIUS_DP * 2))
        }
    }

    /**
     * The live model's tap gives the same pick its screen lines with a seen flag per point (behind a ridge, or behind the
     * eye, is false). The same lines as the picture's, with a run's points hidden, never choose it (condition 2).
     */
    @Test fun aHiddenLineIsNeverChosen() {
        val v = peak.view("front")!!
        val taps = fix.getJSONArray("taps")
        for (i in 0 until taps.length()) {
            val t = taps.getJSONObject(i); if (t.getString("view") != "front" || t.isNull("run")) continue
            val run = t.getString("run")
            val lines = PictureStage.linesOn(v, Place(1f, 0f, 0f)).map { l ->
                StagePick.Line(l.key, l.xy, BooleanArray(l.xy.size / 2) { l.key != run })
            }
            val got = StagePick.nearest(lines, t.getDouble("x").toFloat(), t.getDouble("y").toFloat(), StagePick.RADIUS_DP)
            assertTrue("$run hidden but chosen", got != run)
            // and all seen, it is chosen again: the flags are the only difference
            val seen = lines.map { StagePick.Line(it.key, it.xy, BooleanArray(it.xy.size / 2) { true }) }
            assertEquals(run, StagePick.nearest(seen, t.getDouble("x").toFloat(), t.getDouble("y").toFloat(), StagePick.RADIUS_DP))
        }
    }

    @Test fun whatShows() {
        // the picture by default; the user's saved choice opens the live model; a moment (the film, Kobi) brings it up
        assertEquals(StageRules.Shows.PICTURE, StageRules.shows(chosenLive = false, temp = false, gles = true))
        assertEquals(StageRules.Shows.LIVE, StageRules.shows(chosenLive = true, temp = false, gles = true))
        assertEquals(StageRules.Shows.LIVE, StageRules.shows(chosenLive = false, temp = true, gles = true))
        // without OpenGL ES 3 the picture is the only stage, whatever was chosen (condition 5)
        assertEquals(StageRules.Shows.PICTURE, StageRules.shows(chosenLive = true, temp = true, gles = false))
    }

    /** a stage that writes down what it is told */
    private fun fake(log: MutableList<String>): Stage = Proxy.newProxyInstance(Stage::class.java.classLoader, arrayOf(Stage::class.java)) { _, m, a ->
        log += m.name + (a?.joinToString(",", "(", ")") { it?.toString() ?: "null" } ?: "")
        when (m.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Float.TYPE -> 0f; java.lang.Long.TYPE -> 0L; else -> null }
    } as Stage

    @Test fun theSwapGivesTheNewStageWhatTheLayersSet() {
        val host = StageHost()
        host.setTime(1234L); host.setShift(120f); host.setQuiet(.4f, .2f); host.choose("Tatra 2")
        val a = mutableListOf<String>(); host.impl = fake(a)
        assertTrue(a.toString(), "setTime(1234)" in a && "setShift(120.0)" in a && "setQuiet(0.4,0.2)" in a && "choose(Tatra 2)" in a)
        // the old stage is not told anything after the swap
        val b = mutableListOf<String>(); host.impl = fake(b); a.clear()
        host.home(); host.choose(null)
        assertTrue(a.toString(), a.isEmpty())
        assertTrue(b.toString(), "home" in b && "choose(null)" in b)
        // a view kept on one stage is not given to the other (each keeps its own kind)
        val kept = host.viewNow(); host.impl = fake(a); b.clear(); a.clear()
        host.backTo(kept, "back")
        assertTrue(a.none { it.startsWith("backTo") } && b.isEmpty())
        // with no stage (the data still loading) nothing breaks, and nothing is there
        host.impl = null
        assertNull(host.toScreen(0f, 0f))
        assertEquals(false, host.drawn)
    }
}

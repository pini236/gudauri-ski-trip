package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * X-3 (docs/ARCHITECTURE.md, "החלטות יישור"): the slope that paints a chosen run is measured as the site measures it,
 * on the shared values in docs/parity-cases.json (printed by the site's own code). The site counts in double
 * precision and the app's elevation model in float, hence the small tolerance.
 */
class SlopeRuleTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val cases = JSONObject(File(dir.parentFile.parentFile, "docs/parity-cases.json").readText()).getJSONObject("x3")
    private val scene by lazy { MapScene(Terrain.parse(File(dir, "terrain.json").readText()), Runs.parse(File(dir, "runs-and-lifts.json").readText())) }

    @Test fun alongTheLineOver20MetresEachWay() {
        val run = scene.runs.pistes.first { it.key == cases.getString("run") }
        val lines = scene.topDown(run)
        val slopes = lines.map { scene.lineSlopes(it) }
        val want = cases.getJSONArray("line")
        for (k in 0 until want.length()) {
            val w = want.getJSONObject(k)
            val x = w.getDouble("x").toFloat(); val z = w.getDouble("z").toFloat()
            // the same point of the same draped line
            var best = Float.MAX_VALUE; var got = 0f
            lines.forEachIndexed { li, l -> for (i in 0 until l.size / 3) { val d = hypot(l[i * 3] - x, l[i * 3 + 2] - z); if (d < best) { best = d; got = slopes[li][i] } } }
            assertTrue("point $k is ${best}m away", best < 0.05f)
            assertEquals("line point $k", w.getDouble("deg").toFloat(), got, 0.02f)
        }
    }

    @Test fun theGroundOver20MetresEachWay() {
        val want = cases.getJSONArray("ground")
        for (k in 0 until want.length()) {
            val w = want.getJSONObject(k)
            assertEquals("ground point $k", w.getDouble("deg").toFloat(), scene.groundSlope(w.getDouble("x").toFloat(), w.getDouble("z").toFloat()), 0.02f)
        }
    }

    @Test fun theGroundLayerAroundTheRun() {
        val run = scene.runs.pistes.first { it.key == cases.getString("run") }
        val g = scene.slopeLayer(scene.topDown(run))
        // texels of 10 m, and each one has the colour of its slope; whole near the line, nothing 150 m away
        assertEquals(g.w * MapScene.TEXEL, g.width, 0.01f); assertEquals(g.h * MapScene.TEXEL, g.depth, 0.01f)
        val p = cases.getJSONArray("ground").getJSONObject(0)
        val i = ((p.getDouble("x") - g.x0) / MapScene.TEXEL).toInt(); val j = ((p.getDouble("z") - g.z0) / MapScene.TEXEL).toInt()
        val cx = g.x0 + (i + 0.5f) * MapScene.TEXEL; val cz = g.z0 + (j + 0.5f) * MapScene.TEXEL
        val c = SlopeColors.of(scene.groundSlope(cx, cz))
        val o = (j * g.w + i) * 4
        for (ch in 0..2) assertEquals((c[ch] * 255).toInt().toFloat(), (g.rgba[o + ch].toInt() and 0xFF).toFloat(), 1f)
        assertEquals("the corner is 150 m out", 0, g.rgba[3].toInt() and 0xFF)
        assertTrue("on the line it is whole", (0 until g.w * g.h).any { (g.rgba[it * 4 + 3].toInt() and 0xFF) == 255 })
    }
}

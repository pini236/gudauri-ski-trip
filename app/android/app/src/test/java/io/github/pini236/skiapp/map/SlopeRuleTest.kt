package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * X-3 (docs/ARCHITECTURE.md, "החלטות יישור"): the slope that paints a chosen run is measured as the site measures it,
 * on the values in tools/fixtures/slope-x3.json, which the site's own test writes from its code (tests/slope.spec.ts).
 * The line is sampled as the site samples it (every 10 m along the run's longest line, from the top), and the app's
 * rule runs on those points. The site counts in double precision and the app in float, hence the small tolerance.
 */
class SlopeRuleTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val cases = JSONObject(File(dir.parentFile.parentFile, "tools/fixtures/slope-x3.json").readText())
    private val scene by lazy { MapScene(Terrain.parse(File(dir, "terrain.json").readText()), Runs.parse(File(dir, "runs-and-lifts.json").readText())) }

    /** The site's sampleLine: every 10 m, from the top; the longest of the run's lines. x, z and the distance along. */
    private fun sampled(): Triple<FloatArray, FloatArray, FloatArray> {
        val run = scene.runs.pistes.first { it.key == cases.getString("run") }
        val t = scene.terrain
        val lines = run.lines.map { l ->
            val n = l.size / 2
            if (t.elev(l[0], l[1]) < t.elev(l[(n - 1) * 2], l[(n - 1) * 2 + 1])) FloatArray(l.size).also { r -> for (i in 0 until n) { r[i * 2] = l[(n - 1 - i) * 2]; r[i * 2 + 1] = l[(n - 1 - i) * 2 + 1] } } else l
        }
        val samples = lines.map { l ->
            val xs = arrayListOf(l[0].toDouble()); val zs = arrayListOf(l[1].toDouble()); val ds = arrayListOf(0.0)
            var d = 0.0
            for (i in 1 until l.size / 2) {
                val ax = l[i * 2 - 2].toDouble(); val az = l[i * 2 - 1].toDouble(); val bx = l[i * 2].toDouble(); val bz = l[i * 2 + 1].toDouble()
                val len = Math.hypot(bx - ax, bz - az); val n = maxOf(1, Math.round(len / 10).toInt())
                for (k in 1..n) { val f = k.toDouble() / n; xs += ax + (bx - ax) * f; zs += az + (bz - az) * f; ds += d + len * f }
                d += len
            }
            Triple(xs.map { it.toFloat() }.toFloatArray(), zs.map { it.toFloat() }.toFloatArray(), ds.map { it.toFloat() }.toFloatArray())
        }
        return samples.maxByOrNull { it.third.last() }!!
    }

    @Test fun theRuleIsTheSites() = assertEquals(cases.getInt("half").toFloat(), MapScene.GROUND_D, 0f)

    @Test fun alongTheLineOver20MetresEachWay() {
        val (xs, zs, ds) = sampled()
        val line = FloatArray(xs.size * 3) { k -> val i = k / 3; when (k % 3) { 0 -> xs[i]; 1 -> scene.terrain.elev(xs[i], zs[i]); else -> zs[i] } }
        val slopes = scene.lineSlopes(line)
        val want = cases.getJSONArray("line")
        for (k in 0 until want.length()) {
            val w = want.getJSONObject(k); val i = w.getInt("i")
            // the same point of the same line, as far along
            assertEquals("x of point $k", w.getDouble("x").toFloat(), xs[i], 0.02f)
            assertEquals("z of point $k", w.getDouble("y").toFloat(), zs[i], 0.02f)
            assertEquals("distance of point $k", w.getDouble("d").toFloat(), ds[i], 0.05f)
            assertEquals("line point $k", w.getDouble("deg").toFloat(), slopes[i], 0.02f)
        }
    }

    @Test fun theGroundOver20MetresEachWay() {
        val want = cases.getJSONArray("ground")
        for (k in 0 until want.length()) {
            val w = want.getJSONObject(k)
            assertEquals("ground point $k", w.getDouble("deg").toFloat(), scene.groundSlope(w.getDouble("x").toFloat(), w.getDouble("y").toFloat()), 0.02f)
        }
    }

    @Test fun theGroundLayerAroundTheRun() {
        val run = scene.runs.pistes.first { it.key == cases.getString("run") }
        val g = scene.slopeLayer(scene.topDown(run))
        // texels of 10 m, and each one has the colour of its slope; whole near the line, nothing 150 m away
        assertEquals(g.w * MapScene.TEXEL, g.width, 0.01f); assertEquals(g.h * MapScene.TEXEL, g.depth, 0.01f)
        val p = cases.getJSONArray("ground").getJSONObject(0)
        val i = ((p.getDouble("x") - g.x0) / MapScene.TEXEL).toInt(); val j = ((p.getDouble("y") - g.z0) / MapScene.TEXEL).toInt()
        val cx = g.x0 + (i + 0.5f) * MapScene.TEXEL; val cz = g.z0 + (j + 0.5f) * MapScene.TEXEL
        val c = SlopeColors.of(scene.groundSlope(cx, cz))
        val o = (j * g.w + i) * 4
        for (ch in 0..2) assertEquals((c[ch] * 255).toInt().toFloat(), (g.rgba[o + ch].toInt() and 0xFF).toFloat(), 1f)
        assertEquals("the corner is 150 m out", 0, g.rgba[3].toInt() and 0xFF)
        assertTrue("on the line it is whole", (0 until g.w * g.h).any { (g.rgba[it * 4 + 3].toInt() and 0xFF) == 255 })
    }
}

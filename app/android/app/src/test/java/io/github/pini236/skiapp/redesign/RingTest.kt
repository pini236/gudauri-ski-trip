package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/** The far ground (the architect's option a, 25.11.2026): the budget, the seam under the model, and real heights. */
class RingTest {
    private val file = File("src/main/assets/redesign/live/ring.json")
    private val json = JSONObject(file.readText())
    // the height model's box (site/data/terrain.json's dem) and a flat model at 2,200 m
    private val x0 = -7795.76f; private val y0 = -7792.4f; private val x1 = 6975.15f; private val y1 = 7792.4f
    private val ring = LiveRing.build(json, x0, y0, x1, y1) { _, _ -> 2200f }

    @Test fun withinTheBudget() {
        assertTrue("${file.length()} bytes", file.length() <= LiveRing.MAX_BYTES)
        assertTrue("${ring.triangles} triangles", ring.triangles <= LiveRing.MAX_TRIANGLES)
        assertTrue(ring.indices.all { it in 0 until ring.vertices.size / LiveRing.STRIDE })
    }

    @Test fun saysWhereItComesFrom() {
        assertTrue(json.getString("source").contains("Terrain Tiles on AWS"))
        assertTrue(json.getString("fetched").matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(700.0, json.getDouble("cell"), 0.0)
        // the same projection as the height model
        assertEquals(42.51, json.getJSONObject("proj").getDouble("lat0"), 1e-9)
        assertEquals(44.495, json.getJSONObject("proj").getDouble("lon0"), 1e-9)
    }

    @Test fun underTheModelItLiesUnderTheMesh() {
        val v = ring.vertices
        var inside = 0
        for (k in 0 until v.size / LiveRing.STRIDE) {
            val x = v[k * 6]; val z = v[k * 6 + 2]
            if (x > x0 && x < x1 && z > y0 && z < y1) { assertEquals(2200f - LiveRing.UNDER, v[k * 6 + 1], 0f); inside++ }
        }
        assertTrue(inside > 0)
        // and the cells well inside are left out: fewer triangles than the full grid
        val n = json.getInt("nx") - 1
        assertTrue(ring.triangles < n * n * 2)
    }

    @Test fun outsideTheHeightsAreTheFileS() {
        val h = LiveRing.heights(json); val nx = json.getInt("nx")
        val v = ring.vertices
        // the corner, far from the model, and real mountains (the Caucasus, more than 4 km somewhere)
        assertEquals(h[0].toFloat(), v[1], 0f)
        assertTrue(h.max() > 4000); assertTrue(h.min() > 0)
        assertEquals(h[nx * nx - 1].toFloat(), v[(nx * nx - 1) * 6 + 1], 0f)
    }

    @Test fun normalsAreUnitAndUp() {
        val v = ring.vertices
        for (k in 0 until v.size / LiveRing.STRIDE) {
            val l = sqrt(v[k * 6 + 3] * v[k * 6 + 3] + v[k * 6 + 4] * v[k * 6 + 4] + v[k * 6 + 5] * v[k * 6 + 5])
            assertEquals(1f, l, 1e-4f); assertTrue(v[k * 6 + 4] > 0f)
        }
    }
}

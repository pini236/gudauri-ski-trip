package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * The far ground, schema 2 (the architect's light option, 25.11.2026): the budget, the seam under the model and under
 * the inner ring, real heights, where they come from, and the cast shadow.
 */
class RingTest {
    private val file = File("src/main/assets/redesign/live/ring.json")
    private val relief = File("src/main/assets/redesign/live/ringrelief.webp")
    private val json = JSONObject(file.readText())
    // the height model's box (site/data/terrain.json's dem) and a flat model at 2,200 m
    private val x0 = -7795.76f; private val y0 = -7792.4f; private val x1 = 6975.15f; private val y1 = 7792.4f
    private val ring = LiveRing.build(json, x0, y0, x1, y1) { _, _ -> 2200f }
    private val ni = ring.inner.nx * ring.inner.ny

    @Test fun withinTheBudget() {
        assertTrue("${file.length()} bytes", file.length() <= LiveRing.MAX_BYTES)
        assertTrue("${relief.length()} bytes of relief", relief.length() <= LiveRing.MAX_RELIEF_BYTES)
        // the sixth texture: the whole set stays within 1 MB (the architect, 25.11.2026)
        val set = LiveData.TEXTURES.sumOf { File("src/main/assets/redesign/live/$it.webp").length() } + relief.length()
        assertTrue("$set bytes of textures", set <= 1L shl 20)
        assertTrue("${ring.triangles} triangles", ring.triangles <= LiveRing.MAX_TRIANGLES)
        assertTrue(ring.indices.all { it in 0 until ring.points })
        // the light option: 300 m out to 22 km, 1,400 m out to 42 km
        assertEquals(300f, ring.inner.cell, 0f); assertEquals(1400f, ring.outer.cell, 0f)
        assertTrue(ring.inner.x1 >= 22000f && ring.outer.x1 >= 42000f)
    }

    @Test fun saysWhereItComesFrom() {
        assertEquals(2, json.getInt("schema"))
        assertTrue(json.getString("source").contains("Terrain Tiles on AWS"))
        assertTrue(json.getString("fetched").matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue(json.getJSONObject("inner").getString("ridge").contains("highest sample"))
        assertEquals("ringrelief.webp", json.getJSONObject("relief").getString("file"))
        // the same projection as the height model
        assertEquals(42.51, json.getJSONObject("proj").getDouble("lat0"), 1e-9)
        assertEquals(44.495, json.getJSONObject("proj").getDouble("lon0"), 1e-9)
    }

    @Test fun underTheModelItLiesUnderTheMesh() {
        val v = ring.vertices
        var inside = 0
        for (k in 0 until ni) {
            val x = v[k * 6]; val z = v[k * 6 + 2]
            if (x > x0 && x < x1 && z > y0 && z < y1) { assertEquals(2200f - LiveRing.UNDER, v[k * 6 + 1], 0f); inside++ }
        }
        assertTrue(inside > 0)
    }

    @Test fun underTheInnerRingTheOuterLiesUnderIt() {
        val v = ring.vertices; var n = 0
        for (k in ni until ring.points) {
            val x = v[k * 6]; val z = v[k * 6 + 2]
            // away from the model and its seam: under the inner ring's own heights, by 30 m
            if (!ring.inner.inside(x, z) || (x > x0 - 1000 && x < x1 + 1000 && z > y0 - 1000 && z < y1 + 1000)) continue
            assertEquals(ring.inner.at(x, z) - LiveRing.UNDER_INNER, v[k * 6 + 1], 1f); n++
        }
        assertTrue(n > 0)
    }

    @Test fun outsideTheHeightsAreTheFileS() {
        val h = LiveRing.heights(json.getJSONObject("outer")); val nx = ring.outer.nx
        val v = ring.vertices
        // the outer ring's corners, and real mountains (Kazbek, about 5 km, is in the inner ring)
        assertEquals(h[0].toFloat(), v[ni * 6 + 1], 0f)
        assertEquals(h[nx * nx - 1].toFloat(), v[(ni + nx * nx - 1) * 6 + 1], 0f)
        val hi = LiveRing.heights(json.getJSONObject("inner"))
        assertTrue(hi.max() > 4800); assertTrue(h.min() > 0)
    }

    @Test fun normalsAreUnitAndUp() {
        val v = ring.vertices
        for (k in 0 until ring.points) {
            val l = sqrt(v[k * 6 + 3] * v[k * 6 + 3] + v[k * 6 + 4] * v[k * 6 + 4] + v[k * 6 + 5] * v[k * 6 + 5])
            assertEquals(1f, l, 1e-4f); assertTrue(v[k * 6 + 4] > 0f)
        }
    }

    @Test fun theShadowFallsAwayFromALowSun() {
        // the sun low in the west: some of the ring in shade; the sun overhead: none
        val low = ring.shadows(floatArrayOf(-0.97f, 0.24f, 0f), budgetMs = 60_000)
        assertEquals(ring.points, low.size)
        assertTrue(low.all { it in 0f..1f })
        assertTrue("some shade", low.count { it > .5f } > 100)
        val high = ring.shadows(floatArrayOf(0f, 1f, 0f), budgetMs = 60_000)
        assertTrue(high.all { it == 0f })
        // under the model: none (hidden)
        for (k in 0 until ni) {
            val x = ring.vertices[k * 6]; val z = ring.vertices[k * 6 + 2]
            if (x > x0 + 400 && x < x1 - 400 && z > y0 + 400 && z < y1 - 400) assertEquals(0f, low[k], 0f)
        }
    }

    @Test fun overTheTimeTheOuterRingHasNoShadow() {
        val r = LiveRing.build(json, x0, y0, x1, y1) { _, _ -> 2200f }
        val s = r.shadows(floatArrayOf(-0.97f, 0.24f, 0f), budgetMs = 0)
        assertTrue(r.outerShadowOff)
        for (k in ni until r.points) assertEquals(0f, s[k], 0f)
    }
}

package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * The far ground, schema 2 (the architect's light option, 25.11.2026): the budget, the seam under the model (step 4: the model bends to
 * the ring, the band) and under the inner ring, real heights, where they come from, and the cast shadow.
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
        // every texture of the live model, the ring's relief and the seam's band among them: 1.2 MB packed in all
        // (the architect, 25.11.2026, the lean option with step 4)
        val all = File("src/main/assets/redesign/live").listFiles { f -> f.name.endsWith(".webp") }!!
        assertTrue(all.any { it.name == "ringband.webp" } && all.any { it.name == "ringrelief.webp" })
        val set = all.sumOf { it.length() }
        assertTrue("$set bytes of textures", set <= LiveRing.MAX_SET_BYTES)
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

    @Test fun underTheModelItKeepsItsHeightsAFewMetresLower() {
        // step 4: the inner ring keeps its own heights, 3 m lower under the model (which bends to them); outside, its own
        val h = LiveRing.heights(json.getJSONObject("inner")); val v = ring.vertices
        var inside = 0; var out = 0
        for (k in 0 until ni) {
            val x = v[k * 6]; val z = v[k * 6 + 2]
            if (x > x0 && x < x1 && z > y0 && z < y1) { assertEquals(h[k] - LiveRing.UNDER, v[k * 6 + 1], 0f); inside++ }
            else { assertEquals(h[k].toFloat(), v[k * 6 + 1], 0f); out++ }
        }
        assertTrue(inside > 0 && out > 0)
        assertEquals(3f, LiveRing.UNDER, 0f)
    }

    @Test fun theModelBendsToTheRingOverItsLast1500Metres() {
        // at the edge the two are one height; 1,500 m in and further, the model's own; in between, smooth
        for (y in listOf(-5000f, 0f, 3000f)) {
            assertEquals(ring.inner.at(x0 + 0.01f, y), ring.bent(x0 + 0.01f, y, 2200f), 0.5f)
            assertEquals(2200f, ring.bent(x0 + 1500f, y, 2200f), 0f)
            assertEquals(2200f, ring.bent(0f, y, 2200f), 0f)
            val mid = ring.bent(x0 + 750f, y, 2200f); val e = ring.inner.at(x0 + 750f, y)
            assertEquals(2200f + (e - 2200f) * .5f, mid, .5f)
        }
        // outside the model nothing is bent; the ground there is the ring's
        assertEquals(1234f, ring.bent(x0 - 10f, 0f, 1234f), 0f)
        assertEquals(ring.inner.at(x0 - 300f, 0f), ring.ground(x0 - 300f, 0f), 0f)
        // the mesh's copy: only y changes, only near the edge
        val v = floatArrayOf(x0 + 1f, 2200f, 0f, 9f, 0f, 0f, 2200f, 0f, 9f, 9f)
        val b = ring.bend(v, 5)
        assertEquals(ring.bent(x0 + 1f, 0f, 2200f), b[1], 0f); assertEquals(2200f, b[6], 0f)
        assertEquals(9f, b[3], 0f); assertEquals(x0 + 1f, b[0], 0f)
    }

    @Test fun theBandIsFourGreyPlanesInOneTexture() {
        val j = JSONObject(File("src/main/assets/redesign/live/ringband.json").readText())
        val w = j.getJSONArray("px").getInt(0); val h = j.getJSONArray("px").getInt(1)
        // the model's box and 2 km round it, as the ring reads it
        assertEquals(x0 - 2000f, j.getDouble("x0").toFloat(), .5f); assertEquals(x1 + 2000f, j.getDouble("x1").toFloat(), .5f)
        assertEquals(y0 - 2000f, j.getDouble("z0").toFloat(), .5f); assertEquals(y1 + 2000f, j.getDouble("z1").toFloat(), .5f)
        assertEquals(2000.0, j.getDouble("margin"), 0.0)
        val grey = IntArray(w * h * 4) { i -> val plane = i / (w * h); val g = (plane * 60 + i % 7) and 255; (0xff shl 24) or (g shl 16) or (g shl 8) or g }
        val b = LiveRing.Band.of(j, grey, w, h * 4)
        assertEquals(w, b.w); assertEquals(h, b.h)
        for (i in listOf(0, 5, w * h - 1)) for (k in 0..3) assertEquals((k * 60 + (k * w * h + i) % 7) and 255, b.px[i * 4 + k].toInt() and 255)
    }

    @Test fun theModelSShadowAtItsEdgeIsRedoneOverTheRingSGround() {
        // a low western sun: the band's points are worked out again; the middle of the model keeps its own shadow
        val nx = 60; val ny = 60; val sx = (x1 - x0) / (nx - 1); val sy = (y1 - y0) / (ny - 1)
        val raw = FloatArray(nx * ny) { .5f }
        val o = ring.modelBand(raw, nx, ny, x0, y0, sx, sy, floatArrayOf(-0.97f, 0.24f, 0f))
        assertEquals(.5f, o[30 * nx + 30], 0f)
        assertTrue(o.all { it in 0f..1f })
        assertTrue("the band changed", (0 until nx).any { o[30 * nx + it] != .5f })
        // the sun overhead: nothing in shade in the band (all of it hands over to 0)
        val up = ring.modelBand(raw, nx, ny, x0, y0, sx, sy, floatArrayOf(0f, 1f, 0f))
        assertEquals(0f, up[30 * nx], 0f)
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
        // deep under the model: none (hidden); two cells in, worked out (the seam's triangles), and the softening
        // spreads that one cell further
        for (k in 0 until ni) {
            val x = ring.vertices[k * 6]; val z = ring.vertices[k * 6 + 2]
            if (ring.inward(x, z) > 3 * ring.inner.cell + 1) assertEquals(0f, low[k], 0f)
        }
    }

    @Test fun overTheTimeTheOuterRingHasNoShadow() {
        val r = LiveRing.build(json, x0, y0, x1, y1) { _, _ -> 2200f }
        val s = r.shadows(floatArrayOf(-0.97f, 0.24f, 0f), budgetMs = 0)
        assertTrue(r.outerShadowOff)
        for (k in ni until r.points) assertEquals(0f, s[k], 0f)
    }
}

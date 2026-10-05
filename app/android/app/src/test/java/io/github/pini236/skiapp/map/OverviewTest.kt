package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The map from above (A-30): what a tap finds, the two sides' boxes, and a chair's place on its cable. */
class OverviewTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val runs by lazy { Runs.parse(File(dir, "runs-and-lifts.json").readText()) }
    private val art by lazy { OverviewArt(runs, Terrain.parse(File(dir, "terrain.json").readText())) }

    @Test fun aTapOnARunFindsIt() {
        val t = runs.pistes.first { it.key == "Tatra 2" }.lines.first()
        val m = t.size / 2 / 2
        assertEquals("Tatra 2", art.runAt(t[m * 2] + 3f, t[m * 2 + 1], 20f, emptySet())?.key)
        // the filters hide it from the tap too
        assertTrue(art.runAt(t[m * 2] + 3f, t[m * 2 + 1], 20f, setOf("Tatra 2"))?.key != "Tatra 2")
        // far from every line: nothing
        assertNull(art.runAt(-20000f, -20000f, 20f, emptySet()))
    }

    @Test fun theTwoSides() {
        // the main side holds every main run, with room above for the peaks; the Kobi side is north of it, with the pass
        for (p in runs.mainPistes) for (l in p.lines) for (i in 0 until l.size / 2) assertTrue(p.key, art.main.contains(l[i * 2], l[i * 2 + 1]) || l[i * 2] == art.main.right || l[i * 2 + 1] == art.main.bottom)
        assertTrue(art.kobi.centerY() < art.main.centerY())
        val pass = art.pass!!
        assertTrue(art.kobi.left <= pass[0] && pass[0] <= art.kobi.right)
    }

    @Test fun aChairAlongItsCable() {
        val l = floatArrayOf(0f, 0f, 30f, 40f, 30f, 140f) // 50 m, then 100 m
        val out = FloatArray(2)
        OverviewArt.along(l, 150f, 0f, out); assertEquals(0f, out[0], .01f); assertEquals(0f, out[1], .01f)
        OverviewArt.along(l, 150f, 1f / 3, out); assertEquals(30f, out[0], .01f); assertEquals(40f, out[1], .01f)
        OverviewArt.along(l, 150f, 2f / 3, out); assertEquals(30f, out[0], .01f); assertEquals(90f, out[1], .01f)
        assertEquals(50f, OverviewArt.dist(l, 80f, 40f), .01f)
    }
}

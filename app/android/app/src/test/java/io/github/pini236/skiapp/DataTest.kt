package io.github.pini236.skiapp

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The app reads the site's own data files (read-only). */
class DataTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")

    @Test fun terrainMatchesTheSite() {
        val t = Terrain.parse(File(dir, "terrain.json").readText())
        assertEquals(370, t.nx); assertEquals(391, t.ny)
        assertEquals(t.nx * t.ny, t.h.size)
        val min = t.h.min(); val max = t.h.max()
        assertTrue("elevations $min..$max", min in 1000..2000 && max in 3200..4200)
        // the highest peak in the data sits where the grid is high
        val sadzele = t.peaks.first { it.name == "Sadzele" }
        assertTrue(kotlin.math.abs(t.elev(sadzele.x, sadzele.y) - sadzele.ele) < 120)
    }

    @Test fun runsLieOnTheGrid() {
        val t = Terrain.parse(File(dir, "terrain.json").readText())
        val r = Runs.parse(File(dir, "runs-and-lifts.json").readText())
        assertTrue(r.pistes.size >= 30)
        assertTrue(r.pistes.any { it.key == "Tatra 2" })
        for (p in r.pistes) for (l in p.lines) for (i in 0 until l.size / 2) {
            assertTrue(p.key, l[i * 2] in t.x0..t.x1 && l[i * 2 + 1] in t.y0..t.y1)
        }
    }
}

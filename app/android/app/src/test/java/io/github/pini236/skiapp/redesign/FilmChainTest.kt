package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.map.MapScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * The film's line is the run's way down, its pieces joined (Pini, 25.11.2026: on Kikilo 1 the film ended half way down
 * the line on the screen). On every run of the data: the dot ends at the run's lowest end, and never walks less than
 * the run's longest piece.
 */
class FilmChainTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val scene by lazy { MapScene(Terrain.parse(File(dir, "terrain.json").readText()), Runs.parse(File(dir, "runs-and-lifts.json").readText())) }

    private fun len(l: FloatArray): Float { var a = 0f; for (i in 1 until l.size / 3) a += hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1]); return a }

    @Test fun twoPiecesEndToEndAreOneLine() {
        val a = floatArrayOf(0f, 3000f, 0f, 0f, 2800f, 1000f)
        val b = floatArrayOf(10f, 2795f, 1010f, 10f, 2600f, 1600f)
        val c = FilmRules.chain(listOf(b, a))!!
        assertEquals(1600f + hypot(10f, 10f) - 0f, FilmLine(c).length, 1f)
        assertEquals(2600f, c[c.size - 2], .01f)
    }

    @Test fun aGapOverThirtyMetresIsNotCrossed() {
        val a = floatArrayOf(0f, 3000f, 0f, 0f, 2800f, 1000f)
        val b = floatArrayOf(0f, 2700f, 1100f, 0f, 2600f, 1600f)
        assertEquals(1000f, FilmLine(FilmRules.chain(listOf(a, b))!!).length, .01f)
    }

    @Test fun aBranchOffTheLineThatEndsLowerIsTaken() {
        // a line down, and a piece leaving it half way that ends 100 m lower than the line's own end
        val a = floatArrayOf(0f, 3000f, 0f, 0f, 2900f, 500f, 0f, 2800f, 1000f)
        val b = floatArrayOf(5f, 2900f, 500f, 400f, 2700f, 800f)
        val c = FilmRules.chain(listOf(a, b))!!
        assertEquals(2700f, c[c.size - 2], .01f)
    }

    @Test fun onEveryRunTheDotEndsAtTheLowestEnd() {
        var split = 0
        for (p in scene.runs.pistes) {
            val pieces = scene.topDown(p).filter { it.size >= 6 }
            if (pieces.isEmpty()) continue
            val line = filmLine(scene, p.key)
            assertNotNull(p.key, line)
            val c = FilmRules.chain(pieces)!!
            val low = pieces.minOf { it[it.size - 2] }
            assertEquals("${p.key}: the film ends at the run's lowest end", low, c[c.size - 2], 5f)
            assertTrue("${p.key}: shorter than its longest piece", line!!.length >= pieces.maxOf(::len) - 1f)
            if (pieces.size > 1) split++
        }
        assertTrue(split >= 5)
        // the run of the report: both pieces, 1,150 m and 609 m
        assertEquals(1759f, filmLine(scene, "Kikilo 1")!!.length, 15f)
    }
}

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
        // 1,000 m, the 14 m gap, 590 m
        assertEquals(1590f + hypot(10f, 10f), FilmLine(c).length, 1f)
        assertEquals(2600f, c[c.size - 2], .01f)
    }

    @Test fun aGapOverThirtyMetresIsNotCrossed() {
        val a = floatArrayOf(0f, 3000f, 0f, 0f, 2800f, 1000f)
        val b = floatArrayOf(0f, 2700f, 1100f, 0f, 2600f, 1600f)
        assertEquals(1000f, FilmLine(FilmRules.chain(listOf(a, b))!!).length, .01f)
    }

    @Test fun aLongerBranchOffTheLineIsTaken() {
        // a line down 1,000 m, and a piece leaving it half way that goes on 900 m: 500 + 900 is longer than 1,000
        val a = floatArrayOf(0f, 3000f, 0f, 0f, 2900f, 500f, 0f, 2800f, 1000f)
        val b = floatArrayOf(5f, 2900f, 500f, 905f, 2600f, 500f)
        val c = FilmRules.chain(listOf(a, b))!!
        assertEquals(2600f, c[c.size - 2], .01f)
        assertEquals(1400f, FilmLine(c).length, 6f)
    }

    @Test fun onEveryRunTheDotWalksTheLongestWayToAnEndOfTheLine() {
        var split = 0
        for (p in scene.runs.pistes) {
            val pieces = scene.topDown(p).filter { it.size >= 6 }
            if (pieces.isEmpty()) continue
            val line = filmLine(scene, p.key)
            assertNotNull(p.key, line)
            val c = FilmRules.chain(pieces)!!
            val ex = c[c.size - 3]; val ez = c[c.size - 1]
            // it ends where a piece of the drawn line ends, never in the middle of one
            assertTrue("${p.key}: the film ends in the middle of the line",
                pieces.any { l -> hypot(l[l.size - 3] - ex, l[l.size - 1] - ez) < 1f })
            assertTrue("${p.key}: shorter than its longest piece", line!!.length >= pieces.maxOf(::len) - 1f)
            if (pieces.size > 1) split++
        }
        assertTrue(split >= 5)
        // the run of the report: both pieces, 1,150 m and 609 m, to the run's bottom
        val k = scene.topDown(scene.runs.pistes.first { it.key == "Kikilo 1" })
        val c = FilmRules.chain(k)!!
        assertEquals(1759f, FilmLine(c).length, 15f)
        assertEquals(k.minOf { it[it.size - 2] }, c[c.size - 2], 1f)
    }
}

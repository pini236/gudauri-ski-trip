package io.github.pini236.skiapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arrows down the chosen run (round 21): the first at 90 m, then every 150 m, pointing the way the line runs. */
class RunArrowsTest {
    private fun east(from: Float, n: Int, y: Float = 0f): FloatArray {
        val out = FloatArray(n * 4)
        for (i in 0 until n) { out[i * 4] = from + i * 50f; out[i * 4 + 1] = y; out[i * 4 + 2] = from + (i + 1) * 50f; out[i * 4 + 3] = y }
        return out
    }

    @Test fun firstAtNinetyMetresThenEveryHundredAndFifty() {
        val a = RunArrows.along(east(0f, 12)) // 600 m in pieces of 50
        assertEquals(listOf(100f, 250f, 400f, 550f), (0 until a.size / 3).map { a[it * 3] })
        assertEquals(0f, a[2], 0.001f) // pointing east
    }

    @Test fun pointsTheWayTheSegmentsRun() {
        val south = floatArrayOf(0f, 0f, 0f, 50f, 0f, 50f, 0f, 100f, 0f, 100f, 0f, 150f)
        val a = RunArrows.along(south)
        assertEquals(90f, a[2], 0.001f) // y grows to the south on the map: down the screen
    }

    @Test fun aNewLineStartsOverAtNinety() {
        val two = east(0f, 4) + east(1000f, 4, y = 300f) // two lines that do not meet
        val a = RunArrows.along(two)
        val xs = (0 until a.size / 3).map { a[it * 3] }
        assertEquals(listOf(100f, 1100f), xs)
    }

    @Test fun nothingOnAShortLineOrNoLine() {
        assertEquals(0, RunArrows.along(east(0f, 1)).size)
        assertEquals(0, RunArrows.along(FloatArray(0)).size)
    }

    @Test fun thinnedToAboutSixtyPixelsApart() {
        assertEquals(1, RunArrows.step(0.1f, 3f)) // zoomed in: all of them
        assertTrue(RunArrows.step(2f, 3f) >= 2) // far out: every few
    }
}

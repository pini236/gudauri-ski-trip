package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P.24: the framing of a day's window, the strip, the bars and the wedge. */
class MyDaysRulesTest {
    @Test fun noPointsShowsTheWholePictureCentred() {
        val f = MyDaysRules.frame(emptyList(), 100f, 100f, 400f, 200f)
        assertEquals(.5f, f.k, 1e-6f)
        assertEquals(0f, f.oy, 1e-6f)
        assertEquals(-50f, f.ox, 1e-6f)
    }

    @Test fun framesTheDayWithAMarginAndNeverPastTheEdge() {
        val f = MyDaysRules.frame(listOf(floatArrayOf(100f, 100f, 140f, 120f)), 100f, 100f, 400f, 400f)
        // the box is 40 by 20, with 25% each side 60 by 30: the scale is 100/60
        assertEquals(100f / 60f, f.k, 1e-4f)
        assertTrue(f.ox <= 0f && f.oy <= 0f)
        assertTrue(f.ox >= 100f - 400f * f.k && f.oy >= 100f - 400f * f.k)
    }

    @Test fun neverSmallerThanWhatCoversTheWindow() {
        val f = MyDaysRules.frame(listOf(floatArrayOf(0f, 0f, 390f, 190f)), 100f, 100f, 400f, 200f)
        assertEquals(.5f, f.k, 1e-6f)
    }

    @Test fun nanPointsAreSkipped() {
        val f = MyDaysRules.frame(listOf(floatArrayOf(Float.NaN, Float.NaN, 100f, 100f)), 100f, 100f, 400f, 400f)
        assertTrue(f.k >= .25f)
    }

    @Test fun stripJoinsTheDescentsInTimeOrder() {
        val a = DayPaint.Line(FloatArray(4), intArrayOf(0, 1), longArrayOf(5000, 6000))
        val b = DayPaint.Line(FloatArray(4), intArrayOf(3, 4), longArrayOf(1000, 2000))
        assertArrayEquals(intArrayOf(3, 4, 0, 1), MyDaysRules.strip(listOf(a, b)))
    }

    @Test fun cellsKeepTheFastestBand() {
        assertArrayEquals(intArrayOf(1, 4), MyDaysRules.cells(intArrayOf(0, 1, 2, 4), 2))
        assertEquals(2, MyDaysRules.cells(IntArray(100) { it % 5 }, 2).size)
        assertEquals(0, MyDaysRules.cells(IntArray(0), 4).size)
    }

    @Test fun barsAreRelativeToTheBiggestDay() {
        val b = MyDaysRules.bars(listOf(1000.0, 500.0, 0.0))
        assertEquals(1f, b[0], 1e-6f); assertEquals(.5f, b[1], 1e-6f); assertEquals(.06f, b[2], 1e-6f)
        assertTrue(MyDaysRules.bars(emptyList()).isEmpty())
    }

    @Test fun wedgeFollowsTheAngle() {
        val w = MyDaysRules.wedge(45.0)
        assertEquals(1f, w[1], 1e-4f)
        assertTrue(MyDaysRules.wedge(30.0)[1] < MyDaysRules.wedge(40.0)[1])
    }

    @Test fun recordBeatsCutInTheTag() {
        assertEquals(MyDaysRules.Tag.RECORD, MyDaysRules.tag(true, true))
        assertEquals(MyDaysRules.Tag.CUT, MyDaysRules.tag(false, true))
        assertEquals(MyDaysRules.Tag.NONE, MyDaysRules.tag(false, false))
    }

    @Test fun tripSums() {
        val s = MyDaysRules.sum(listOf(Triple(10, 3000.0, 20000.0), Triple(5, 1500.0, 10000.0)))
        assertEquals(2, s.days); assertEquals(15, s.descents); assertEquals(4500.0, s.dropM, 1e-6); assertEquals(30.0, s.km, 1e-6)
    }
}

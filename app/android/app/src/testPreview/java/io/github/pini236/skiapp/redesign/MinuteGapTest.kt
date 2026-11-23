package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round 4, board L07: "a minute without reception" only for a gap of about a minute inside the descent. */
class MinuteGapTest {
    private fun line(from: Long, to: Long) = DayPaint.Line(FloatArray(4), IntArray(2), longArrayOf(from, to))
    private fun day(vararg ls: DayPaint.Line) = DayPaint.Day(ls.toList(), emptyList())

    @Test fun aMinuteIsNamedAtItsStart() {
        assertEquals(10_000L, minuteGap(day(line(0, 10_000), line(70_000, 100_000)), 0, 100_000))
    }

    @Test fun aShortOrALongGapIsNotAMinute() {
        assertNull(minuteGap(day(line(0, 10_000), line(25_000, 100_000)), 0, 100_000))
        assertNull(minuteGap(day(line(0, 10_000), line(200_000, 300_000)), 0, 300_000))
    }

    @Test fun onlyInsideTheDescent() {
        assertNull(minuteGap(day(line(0, 10_000), line(70_000, 100_000)), 80_000, 100_000))
    }
}

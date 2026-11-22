package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 4, board L06: the day's lines as the designer drew them (design/redesign/tools/day4.py) and the architect's buffer. */
class DayPaintTest {
    private fun pts(n: Int, t0: Long = 0L, kmh: (Int) -> Double = { 30.0 }, gapAt: Int = -1) =
        (0 until n).map { i -> DayPaint.Pt(t0 + i * 1000L + if (gapAt in 0..i) 30_000L else 0L, i * 10f, i * 5f, kmh(i)) }

    @Test fun theBandsAreCutAt20_35_50And62() {
        assertEquals(0, DayPaint.band(19.9)); assertEquals(1, DayPaint.band(20.0)); assertEquals(2, DayPaint.band(35.0))
        assertEquals(3, DayPaint.band(50.0)); assertEquals(4, DayPaint.band(62.0)); assertEquals(4, DayPaint.band(120.0))
        assertEquals(5, DayPaint.DAY.size); assertEquals(5, DayPaint.NIGHT.size)
        assertEquals(0xE0246E, DayPaint.DAY[4]); assertEquals(0xFFF2FA, DayPaint.NIGHT[4])
    }

    @Test fun onlyDescentsAreLightAndAGapBreaksTheLine() {
        val p = pts(100, gapAt = 50)
        val t = p.map { it.t }
        val d = DayPaint.build(p, listOf(DayPaint.Seg("descent", t[0], t[99]), DayPaint.Seg("lift", t[0], t[10]), DayPaint.Seg("stop", t[0], t[99])))
        assertEquals(2, d.lines.size)
        assertEquals(100, d.points)
        assertEquals(1, d.lifts.size)
    }

    @Test fun theSpeedIsAFiveSecondMeanForDrawing() {
        // one fast second among slow ones stays slow in the light: 5 s mean
        val p = pts(21, kmh = { if (it == 10) 100.0 else 10.0 })
        val d = DayPaint.build(p, listOf(DayPaint.Seg("descent", p.first().t, p.last().t)))
        assertEquals(2, d.lines[0].band[10]) // (4 * 10 + 100) / 5 = 28 km/h: still under 35
        assertEquals(0, d.lines[0].band[0])
    }

    @Test fun aLongDayStaysWithinTheBuffer() {
        val p = pts(30_000)
        val d = DayPaint.build(p, listOf(DayPaint.Seg("descent", p.first().t, p.last().t)))
        assertTrue("points ${d.points}", d.points <= DayPaint.MAX_POINTS + 1)
        assertTrue(d.points * 2 <= 20_002)
    }

    @Test fun aLiftIsDotted() {
        val dots = DayPaint.dots(floatArrayOf(0f, 0f, 400f, 0f))
        assertEquals(10, dots.size)
        assertEquals(8f, dots[0][2] - dots[0][0], 1e-3f)
    }
}

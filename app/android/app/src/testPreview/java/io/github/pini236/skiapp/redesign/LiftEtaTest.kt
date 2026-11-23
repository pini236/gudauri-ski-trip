package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round 4, board L04: "about N min to the top", by where you are on the lift's line times its travel time; the top is
 * the higher end, whichever way the line was drawn. No time, off the line or under a minute: nothing.
 */
class LiftEtaTest {
    // a straight lift of 1,000 m east, its bottom at x = 0 (1,500 m up) and its top at x = 1,000 (2,000 m up)
    private val line = floatArrayOf(0f, 0f, 500f, 0f, 1000f, 0f)

    @Test fun thePartStillAheadTimesTheTravelTime() {
        assertEquals(10, liftEta(10.0, line, 1500f, 2000f, 0f, 0f))
        assertEquals(5, liftEta(10.0, line, 1500f, 2000f, 500f, 20f))
        assertEquals(2, liftEta(10.0, line, 1500f, 2000f, 800f, -10f))
    }

    @Test fun theTopIsTheHigherEnd() {
        assertEquals(8, liftEta(10.0, line, 2000f, 1500f, 800f, 0f))
    }

    @Test fun nothingOffTheLineWithoutATimeOrUnderAMinute() {
        assertNull(liftEta(10.0, line, 1500f, 2000f, 500f, 200f))
        assertNull(liftEta(null, line, 1500f, 2000f, 0f, 0f))
        assertNull(liftEta(10.0, line, 1500f, 2000f, 990f, 0f))
        assertNull(liftEta(10.0, floatArrayOf(0f, 0f), 1500f, 2000f, 0f, 0f))
    }
}

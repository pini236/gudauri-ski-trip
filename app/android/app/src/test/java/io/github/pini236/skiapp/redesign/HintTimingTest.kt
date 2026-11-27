package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

/** The hint ring follows the clock: 9 s is 9 s, whatever the animator scale. */
class HintTimingTest {
    @Test fun ringDrainsWithTheClock() {
        assertEquals(1f, HintTiming.left(0, 9000), 1e-6f)
        assertEquals(.5f, HintTiming.left(4500, 9000), 1e-6f)
        assertEquals(0f, HintTiming.left(9000, 9000), 1e-6f)
        assertEquals(0f, HintTiming.left(20000, 9000), 1e-6f)
    }
}

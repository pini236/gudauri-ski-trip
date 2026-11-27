package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

/** The hint ring follows the clock: 13 s is 13 s, whatever the animator scale. */
class HintTimingTest {
    @Test fun ringDrainsWithTheClock() {
        assertEquals(1f, HintTiming.left(0, 13000), 1e-6f)
        assertEquals(.5f, HintTiming.left(6500, 13000), 1e-6f)
        assertEquals(0f, HintTiming.left(13000, 13000), 1e-6f)
        assertEquals(0f, HintTiming.left(20000, 13000), 1e-6f)
    }
}

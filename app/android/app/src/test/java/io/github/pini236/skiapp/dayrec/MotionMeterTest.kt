package io.github.pini236.skiapp.dayrec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMeterTest {
    @Test fun aPhoneLyingStillHasNoVariance() {
        val m = MotionMeter()
        repeat(50) { m.feed(0f, 0f, 9.81f) }
        assertEquals(0f, m.take()!!, 1e-6f)
    }

    @Test fun walkingShakesIt() {
        val m = MotionMeter()
        for (i in 0 until 50) m.feed(0f, 0f, 9.81f + if (i % 2 == 0) 2.5f else -2.5f)
        assertEquals(6.25f, m.take()!!, 0.05f) // +-2.5 around the mean: variance 2.5^2
    }

    @Test fun eachSecondStartsFresh() {
        val m = MotionMeter()
        repeat(10) { m.feed(0f, 0f, 9.81f + it) }
        assertTrue(m.take()!! > 1f)
        repeat(10) { m.feed(0f, 0f, 9.81f) }
        assertEquals(0f, m.take()!!, 1e-6f)
    }

    @Test fun noReadingMeansNoNumber() {
        val m = MotionMeter()
        assertNull(m.take())
        m.feed(0f, 0f, 9.81f); assertNull(m.take()) // one reading is not a variance
    }
}

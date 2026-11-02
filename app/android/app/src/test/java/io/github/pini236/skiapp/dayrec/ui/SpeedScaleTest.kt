package io.github.pini236.skiapp.dayrec.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpeedScaleTest {
    @Test fun theFourStopsAreTheScale() {
        assertEquals(0xFFF5D76E.toInt(), SpeedScale.argb(0.0))
        assertEquals(0xFFF08A24.toInt(), SpeedScale.argb(20.0))
        assertEquals(0xFFD62B2B.toInt(), SpeedScale.argb(40.0))
        assertEquals(0xFF7B2D9B.toInt(), SpeedScale.argb(60.0))
    }

    @Test fun beyondTheScaleIsTheLastStop() {
        assertEquals(SpeedScale.argb(60.0), SpeedScale.argb(95.0))
        assertEquals(SpeedScale.argb(0.0), SpeedScale.argb(null))
        assertEquals(SpeedScale.argb(0.0), SpeedScale.argb(-4.0))
    }

    @Test fun inBetweenIsBlended() {
        val mid = SpeedScale.argb(30.0)
        assertNotEquals(SpeedScale.argb(20.0), mid)
        assertNotEquals(SpeedScale.argb(40.0), mid)
    }
}

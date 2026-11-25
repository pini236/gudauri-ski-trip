package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedRingRulesTest {
    @Test fun theRingFillsOverZeroToEighty() {
        assertEquals(0f, SpeedRingRules.fraction(0f), 1e-6f)
        assertEquals(.5f, SpeedRingRules.fraction(40f), 1e-6f)
        assertEquals(1f, SpeedRingRules.fraction(80f), 1e-6f)
        assertEquals(1f, SpeedRingRules.fraction(112f), 1e-6f)
        assertEquals(0f, SpeedRingRules.fraction(-3f), 1e-6f)
    }

    @Test fun theColoursAreTheHandoffSFiveStops() {
        assertEquals(listOf(0xCFC2F4, 0xA98BEE, 0x7C4FE0, 0xB02FC4, 0xE0246E), SpeedRingRules.DAY.toList())
        assertEquals(listOf(0x5B46A8, 0x8A6BE6, 0xB99CFF, 0xF3A6F0, 0xFFF2FA), SpeedRingRules.NIGHT.toList())
    }

    @Test fun theDayTopIsAWholeNumber() {
        assertEquals("63", LiveNumbersText.whole(62.6))
    }
}

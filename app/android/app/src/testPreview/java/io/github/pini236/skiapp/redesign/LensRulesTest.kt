package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** K08: what each lens counts, and one lens at a time. */
class LensRulesTest {
    @Test fun onlyNamedRunsCountNotBeginnerAreasOrSkiWays() {
        assertTrue(LensRules.counted(true, "run"))
        assertFalse(LensRules.counted(true, "beginner-area"))
        assertFalse(LensRules.counted(true, "ski-way"))
        assertFalse(LensRules.counted(false, "run"))
    }

    @Test fun inTheSunAtLeastHalfTheRestInTheShade() {
        val shares = mapOf("Tatra 1" to .9f, "Kobi" to .5f, "Pirveli" to .2f)
        assertEquals(2 to 2, LensRules.sun(shares, listOf("Tatra 1", "Kobi", "Pirveli", "Shino")))
    }

    @Test fun openCountsOpenAndClosedButNotTheRunsWithNoLift() {
        val st = mapOf("Tatra 1" to true, "Kudebi 1" to false, "Zuma" to null)
        assertEquals(1 to 1, LensRules.openCount({ st[it] }, st.keys))
        assertEquals(LensRules.Open.UNKNOWN, LensRules.open(null))
    }

    @Test fun oneLensAtATime() {
        assertNull(LensRules.tap(LensKind.SUN, LensKind.SUN))
        assertEquals(LensKind.OPEN, LensRules.tap(LensKind.SUN, LensKind.OPEN))
        assertEquals(LensKind.SLOPE, LensRules.tap(null, LensKind.SLOPE))
        assertNull(LensKind.of("snow"))
    }
}

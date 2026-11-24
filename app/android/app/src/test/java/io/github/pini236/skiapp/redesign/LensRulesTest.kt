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
        assertEquals(LensKind.SNOW, LensKind.of("snow"))
        assertNull(LensKind.of("wind"))
    }

    @Test fun newSnowBetweenThePointsByAltitudeAndItsClasses() {
        val pts = listOf(2170f to 2.0, 2710f to 8.0, 3240f to 14.0)
        assertEquals(2.0, LensRules.snowAt(2000f, pts), 1e-9)
        assertEquals(5.0, LensRules.snowAt(2440f, pts), 1e-9)
        assertEquals(14.0, LensRules.snowAt(3400f, pts), 1e-9)
        assertEquals(2, LensRules.snowClass(9.6))
        assertEquals(1, LensRules.snowClass(0.5))
        assertEquals(0, LensRules.snowClass(0.4))
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 4, board L03 ("quiet in a descent"): the toggle hides in a descent and on the lift, shows in the search and standing. */
class ToggleQuietTest {
    @Test fun quietInADescentAndOnTheLift() {
        assertTrue(toggleQuiet(false, "ski")); assertTrue(toggleQuiet(false, "lift"))
    }

    @Test fun shownInTheSearchStandingOrWalking() {
        assertFalse(toggleQuiet(true, "ski")); assertFalse(toggleQuiet(false, null))
        assertFalse(toggleQuiet(false, "stand")); assertFalse(toggleQuiet(false, "walk"))
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChosenRulesTest {
    @Test fun aPieceOnTheScreenIsDrawn() {
        assertTrue(ChosenRules.onScreen(10f, 10f, 200f, 300f, 1080f, 2400f, 20f))
        // crossing the screen with both ends outside
        assertTrue(ChosenRules.onScreen(-500f, 1000f, 1600f, 1100f, 1080f, 2400f, 20f))
    }

    @Test fun aPieceOffTheScreenOrBrokenIsNot() {
        assertFalse(ChosenRules.onScreen(-900f, 10f, -100f, 300f, 1080f, 2400f, 20f))
        assertFalse(ChosenRules.onScreen(10f, 3000f, 200f, 9e6f, 1080f, 2400f, 20f))
        assertFalse(ChosenRules.onScreen(Float.NaN, 10f, 200f, 300f, 1080f, 2400f, 20f))
        assertFalse(ChosenRules.onScreen(10f, 10f, Float.POSITIVE_INFINITY, 300f, 1080f, 2400f, 20f))
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

class PinRulesTest {
    @Test fun aTagAtTheEdgeSlidesIn() {
        assertEquals(78f, PinRules.slide(x = 30f, w = 200f, screenW = 1080f, edge = 8f), 0f)   // left edge 8
        assertEquals(-8f, PinRules.slide(x = 1000f, w = 160f, screenW = 1080f, edge = 8f), 0f) // right edge 1072
        assertEquals(0f, PinRules.slide(x = 540f, w = 200f, screenW = 1080f, edge = 8f), 0f)
        assertEquals(0f, PinRules.slide(x = 30f, w = 0f, screenW = 1080f, edge = 8f), 0f)
    }
}

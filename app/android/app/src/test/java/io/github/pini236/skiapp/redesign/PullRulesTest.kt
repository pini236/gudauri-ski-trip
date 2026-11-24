package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRulesTest {
    private val d = 2.75f
    private val h = 800f

    @Test fun pastAThirdGoesDown() {
        assertEquals(PullRules.To.DOWN, PullRules.settle(h / 3f + 1f, 0f, h, d, canDown = true, canUp = true))
        assertEquals(PullRules.To.BACK, PullRules.settle(h / 3f - 1f, 0f, h, d, canDown = true, canUp = true))
    }

    @Test fun aFlickDownGoesDownFromALittle() {
        assertEquals(PullRules.To.DOWN, PullRules.settle(20f, 1000f * d, h, d, canDown = true, canUp = false))
        // a flick down that never moved the sheet down is not a close
        assertEquals(PullRules.To.BACK, PullRules.settle(-5f, 1000f * d, h, d, canDown = true, canUp = false))
    }

    @Test fun upOnlyWhenThereIsAnUp() {
        assertEquals(PullRules.To.UP, PullRules.settle(-41f * d, 0f, h, d, canDown = true, canUp = true))
        assertEquals(PullRules.To.UP, PullRules.settle(0f, -1000f * d, h, d, canDown = true, canUp = true))
        assertEquals(PullRules.To.BACK, PullRules.settle(-200f * d, -3000f * d, h, d, canDown = true, canUp = false))
    }

    @Test fun noDownWhenNothingGoesDown() {
        assertEquals(PullRules.To.BACK, PullRules.settle(h, 5000f, h, d, canDown = false, canUp = true))
    }

    @Test fun downFollowsTheFingerUpGivesALittle() {
        assertEquals(123f, PullRules.shown(123f, d, canUp = false))
        assertTrue(PullRules.shown(-1000f, d, canUp = true) >= -48f * d)
        assertTrue(PullRules.shown(-1000f, d, canUp = false) >= -16f * d)
        assertEquals(-30f, PullRules.shown(-90f, d, canUp = true))
    }
}

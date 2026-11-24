package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.map.OrbitCamera
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Part 4: when the compass shows. */
class WhereRulesTest {
    private val f = OrbitCamera.State(0f, 2000f, 0f, 6000f, 0.5f, 0.6f)
    private val deg = { d: Double -> Math.toRadians(d).toFloat() }

    @Test fun theOpeningViewHasNoCompass() {
        assertFalse(WhereRules.away(f, f))
        assertFalse(WhereRules.away(f.copy(yaw = f.yaw + deg(2.9)), f))
        assertFalse(WhereRules.away(f.copy(dist = f.dist * 1.04f, tx = 100f), f))
    }

    @Test fun aTurnATiltAZoomOrAMoveShowsIt() {
        assertTrue(WhereRules.away(f.copy(yaw = f.yaw + deg(3.1)), f))
        assertTrue(WhereRules.away(f.copy(pitch = f.pitch - deg(3.1)), f))
        assertTrue(WhereRules.away(f.copy(dist = f.dist * 1.06f), f))
        assertTrue(WhereRules.away(f.copy(tx = 120f, tz = 120f), f))
        // a full turn round is the opening direction again
        assertFalse(WhereRules.away(f.copy(yaw = f.yaw + deg(360.0)), f))
    }
}

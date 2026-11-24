package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.map.OrbitCamera
import org.junit.Assert.assertEquals
import org.junit.Test

/** The live model's gesture bounds (Pini, 24.11.2026): no floating island far out, no jump up close. */
class LiveLimitsTest {
    private val at = OrbitCamera.State(600f, 2300f, -500f, 3000f, .3f, .5f)

    @Test fun theZoomStaysBetweenItsBounds() {
        assertEquals(LiveLimits.MAX_DIST, LiveLimits.step(at, at.copy(dist = 32000f)).dist, 0f)
        assertEquals(LiveLimits.MIN_DIST, LiveLimits.step(at, at.copy(dist = 250f)).dist, 0f)
        assertEquals(5000f, LiveLimits.step(at, at.copy(dist = 5000f)).dist, 0f)
    }

    @Test fun aStepFurtherThanTheViewsDistanceIsAJumpAndIsDropped() {
        val far = LiveLimits.step(at, at.copy(tx = at.tx + 9000f, dist = 2000f, yaw = .4f))
        assertEquals(at.tx, far.tx, 0f); assertEquals(at.tz, far.tz, 0f)
        assertEquals(2000f, far.dist, 0f); assertEquals(.4f, far.yaw, 0f)
    }

    @Test fun anOrdinaryPanPasses() {
        val near = LiveLimits.step(at, at.copy(tx = at.tx + 400f, tz = at.tz - 300f))
        assertEquals(at.tx + 400f, near.tx, 0f); assertEquals(at.tz - 300f, near.tz, 0f)
    }
}

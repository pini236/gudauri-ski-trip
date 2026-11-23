package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** The completion round, part 1, section 2: which live screen shows when. */
class LiveStageTest {
    private val t = 1_000_000L

    @Test fun skiAndLiftAreTheirOwnScreens() {
        assertEquals(LiveStage.DESCENT, liveStage("ski", t, t + 5_000, "lift", false))
        assertEquals(LiveStage.LIFT, liveStage("lift", t, t + 5_000, "stand", true))
    }

    @Test fun aShortStopAfterADescentKeepsTheDescent() {
        assertEquals(LiveStage.DESCENT, liveStage("stand", t, t + 19_999, "ski", true))
        assertEquals(LiveStage.STANDING, liveStage("stand", t, t + 20_000, "ski", false))
        assertEquals(LiveStage.NEAR_LIFT, liveStage("stand", t, t + 20_000, "ski", true))
        // after anything else, at once
        assertEquals(LiveStage.STANDING, liveStage("stand", t, t + 1_000, "lift", false))
    }

    @Test fun fiveMinutesIsABreakEvenNextToALift() {
        assertEquals(LiveStage.NEAR_LIFT, liveStage("stand", t, t + 299_999, "lift", true))
        assertEquals(LiveStage.BREAK, liveStage("stand", t, t + 300_000, "lift", true))
        assertEquals(LiveStage.BREAK, liveStage("stand", t, t + 3_600_000, "ski", false))
    }

    @Test fun walkingAndClimbingAreTheStandingScreenWalking() {
        assertEquals(LiveStage.WALKING, liveStage("walk", t, t, "stand", true))
        assertEquals(LiveStage.WALKING, liveStage("up", t, t, null, false))
        assertEquals(LiveStage.STANDING, liveStage(null, t, t, null, false))
    }

    @Test fun theBoardingStationIsTheLowerEndAndTheNearestWins() {
        // a lift drawn top first: (0, 0) high, (0, 1000) low
        val elev: (Float, Float) -> Float = { _, y -> 3000f - y }
        val lifts = listOf("a" to floatArrayOf(0f, 0f, 0f, 1000f), "b" to floatArrayOf(50f, 1030f, 50f, 0f))
        assertEquals("a", nearBoarding(lifts, elev, 0f, 990f))
        assertEquals("b", nearBoarding(lifts, elev, 45f, 1020f))
        assertNull(nearBoarding(lifts, elev, 0f, 5f))
        assertNull(nearBoarding(lifts, elev, 300f, 1000f))
    }

    @Test fun theDistanceIsTheParametersQueueDistance() {
        val p = JSONObject(File("../../../tools/fixtures/dayrec-params.json").readText())
        val q = p.getJSONObject("segments").getDouble("queue_dist_m")
        assertEquals(q, QUEUE_DIST_M.toDouble(), 1e-9)
    }
}

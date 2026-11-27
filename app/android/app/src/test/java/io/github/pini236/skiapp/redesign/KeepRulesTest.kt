package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P.23: a recording with no real descent (drop under 20 m, or outside the resort) is not kept. */
class KeepRulesTest {
    private val box = floatArrayOf(-1000f, -1000f, 1000f, 1000f)
    private fun seg(type: String, t0: Long, t1: Long, drop: Double) = JSONObject().put("type", type).put("t0", t0).put("t1", t1).put("drop_m", drop)
    private val t = longArrayOf(0, 10, 20, 30)
    private fun xs(vararg v: Float) = v

    @Test fun realDescentInsideIsKept() =
        assertTrue(KeepRules.hasRealDescent(listOf(seg("descent", 0, 30, 120.0)), t, xs(0f, 1f, 2f, 3f), xs(0f, 0f, 0f, 0f), box))

    @Test fun flatWalkingDescentIsNotKept() =
        assertFalse(KeepRules.hasRealDescent(listOf(seg("descent", 0, 30, 5.0)), t, xs(0f, 1f, 2f, 3f), xs(0f, 0f, 0f, 0f), box))

    @Test fun exactlyTheMinimumDropCounts() =
        assertTrue(KeepRules.hasRealDescent(listOf(seg("descent", 0, 30, 20.0)), t, xs(0f, 1f, 2f, 3f), xs(0f, 0f, 0f, 0f), box))

    @Test fun descentOutsideTheResortIsNotKept() =
        assertFalse(KeepRules.hasRealDescent(listOf(seg("descent", 0, 30, 300.0)), t, xs(9000f, 9000f, 9000f, 9000f), xs(0f, 0f, 0f, 0f), box))

    @Test fun aPartInsideCounts() =
        assertTrue(KeepRules.hasRealDescent(listOf(seg("descent", 0, 30, 60.0)), t, xs(9000f, 9000f, 5f, 9000f), xs(0f, 0f, 0f, 0f), box))

    @Test fun liftsAndWalksNeverCount() =
        assertFalse(KeepRules.hasRealDescent(listOf(seg("lift", 0, 30, 500.0), seg("walk", 0, 30, 500.0)), t, xs(0f, 0f, 0f, 0f), xs(0f, 0f, 0f, 0f), box))

    @Test fun noBoxMeansPlaceIsNotJudged() =
        assertTrue(KeepRules.hasRealDescent(listOf(seg("descent", 0, 30, 50.0)), t, xs(9000f, 9000f, 9000f, 9000f), xs(0f, 0f, 0f, 0f), null))

    @Test fun onlyAFreshDayIsJudged() {
        assertTrue(KeepRules.fresh(1_000_000, 1_000_000 + 60_000))
        assertFalse(KeepRules.fresh(1_000_000, 1_000_000 + 3 * 3_600_000L))
        assertEquals(20.0, KeepRules.MIN_DROP_M, 0.0)
    }
}

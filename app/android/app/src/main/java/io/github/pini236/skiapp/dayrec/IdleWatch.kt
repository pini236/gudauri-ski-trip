package io.github.pini236.skiapp.dayrec

import kotlin.math.hypot

/**
 * When to stop a day on its own (condition 2): 20 minutes without moving outside the resort, 60 inside it, with a
 * notice [DayParams.IDLE_WARN_MS] before. "Moving" is getting more than [DayParams.IDLE_MOVE_M] from the place where
 * the stillness started, so a phone resting in a lodge counts as still and a walk to the lift does not.
 * Pure, so a test can run a whole day through it.
 */
class IdleWatch {
    sealed interface Verdict {
        data object Fine : Verdict
        /** [leftMs] until the day is stopped. */
        data class Warn(val leftMs: Long) : Verdict
        /** [outside]: it was the 20-minute rule. */
        data class Stop(val outside: Boolean) : Verdict
    }

    private var ax = 0f; private var ay = 0f; private var since = -1L

    /** One reading in the resort's metres; [t] in any increasing millisecond clock. */
    fun feed(t: Long, x: Float, y: Float): Verdict {
        if (since < 0 || hypot(x - ax, y - ay) > DayParams.IDLE_MOVE_M) { ax = x; ay = y; since = t; return Verdict.Fine }
        val outside = !inside(x, y)
        val limit = if (outside) DayParams.IDLE_OUTSIDE_MS else DayParams.IDLE_INSIDE_MS
        val still = t - since
        return when {
            still >= limit -> Verdict.Stop(outside)
            still >= limit - DayParams.IDLE_WARN_MS -> Verdict.Warn(limit - still)
            else -> Verdict.Fine
        }
    }

    fun reset() { since = -1L }

    companion object {
        fun inside(x: Float, y: Float) = x >= DayParams.AREA_X0 && x <= DayParams.AREA_X1 && y >= DayParams.AREA_Y0 && y <= DayParams.AREA_Y1
    }
}

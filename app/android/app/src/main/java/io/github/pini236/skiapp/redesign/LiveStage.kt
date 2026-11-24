package io.github.pini236.skiapp.redesign

import kotlin.math.hypot

/**
 * The completion round, part 1 (design/handoff/2026-11-21-redesign-day-gaps, section 2): which live screen shows, by the
 * activity the live analysis reports, with no new logic in the measuring. Pure, so a test pins the thresholds.
 */
internal enum class LiveStage { DESCENT, LIFT, STANDING, WALKING, NEAR_LIFT, BREAK }

/** A short stop after a descent stays on the descent's screen (the number falls to zero, the name stays). */
internal const val STAND_KEEPS_DESCENT_MS = 20_000L
/** Standing this long is a break (P03), even next to a lift. */
internal const val BREAK_MS = 5 * 60_000L
/** "Next to the lift": this close to a lift's boarding station (segments.queue_dist_m in tools/fixtures/dayrec-params.json). */
internal const val QUEUE_DIST_M = 80f
/** "You waited" on the lift (L04) only after at least this long standing at its boarding station. */
internal const val WAITED_MIN_MS = 60_000L

/**
 * [act] is the live activity now, [since] when it began, [before] the activity before it, [nearLift] whether you stand
 * within [QUEUE_DIST_M] of a lift's boarding station.
 */
internal fun liveStage(act: String?, since: Long, now: Long, before: String?, nearLift: Boolean): LiveStage = when (act) {
    "ski" -> LiveStage.DESCENT
    "lift" -> LiveStage.LIFT
    "walk", "up" -> LiveStage.WALKING
    "stand" -> when {
        before == "ski" && now - since < STAND_KEEPS_DESCENT_MS -> LiveStage.DESCENT
        now - since >= BREAK_MS -> LiveStage.BREAK
        nearLift -> LiveStage.NEAR_LIFT
        else -> LiveStage.STANDING
    }
    else -> LiveStage.STANDING
}

/**
 * The nearest lift whose boarding station (the lower end of its line, by the ground's heights) is within [maxM] of
 * ([x], [y]): its id, or null. [lifts] are (id, line as x, y pairs).
 */
internal fun nearBoarding(lifts: List<Pair<String, FloatArray>>, elev: (Float, Float) -> Float, x: Float, y: Float, maxM: Float = QUEUE_DIST_M): String? {
    var best: String? = null; var bestD = maxM
    for ((id, p) in lifts) {
        if (p.size < 4) continue
        val n = p.size
        val lowFirst = elev(p[0], p[1]) <= elev(p[n - 2], p[n - 1])
        val bx = if (lowFirst) p[0] else p[n - 2]; val by = if (lowFirst) p[1] else p[n - 1]
        val d = hypot(bx - x, by - y)
        if (d <= bestD) { bestD = d; best = id }
    }
    return best
}

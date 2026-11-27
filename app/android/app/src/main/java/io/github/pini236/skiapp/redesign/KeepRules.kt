package io.github.pini236.skiapp.redesign

import org.json.JSONObject

/**
 * P.23 (handoff 2026-11-27-redesign-my-days, section 1): a recording is kept only if it holds a real descent: a descent
 * segment of the analyzer with a drop of at least `min_descent_drop_m` (tools/fixtures/dayrec-params.json), with at least
 * one reading inside the resort's area. A part of a descent counts. Otherwise nothing is written to the days store.
 */
internal object KeepRules {
    /** `min_descent_drop_m` of tools/fixtures/dayrec-params.json (the analyzer's own threshold for a walk). */
    const val MIN_DROP_M = 20.0
    /** A day that ended this recently is "just ended": the rule applies to it, never to days saved earlier. */
    const val FRESH_MS = 30 * 60_000L

    /**
     * [segs]: the result's segments (type, t0, t1, drop_m); [t], [x], [y]: the readings (ms, site metres); [box]: the resort's
     * box ([WhereRules.resortBox]); with no box (no data) the place is not judged.
     */
    fun hasRealDescent(segs: List<JSONObject>, t: LongArray, x: FloatArray, y: FloatArray, box: FloatArray?, minDrop: Double = MIN_DROP_M): Boolean =
        segs.any { s ->
            s.optString("type") == "descent" && s.optDouble("drop_m", 0.0) >= minDrop &&
                (box == null || t.indices.any { i -> t[i] >= s.optLong("t0") && t[i] <= s.optLong("t1") && WhereRules.atResort(box, x[i], y[i]) })
        }

    fun fresh(endMs: Long, nowMs: Long): Boolean = nowMs - endMs in 0..FRESH_MS
}

package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.map.OrbitCamera
import io.github.pini236.skiapp.data.Runs
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Part 4 (design/handoff/2026-11-23-redesign-on-mountain): the rules behind the compass, apart from how it is drawn. */
internal object WhereRules {
    /**
     * The compass shows once the view is turned from the opening one (handoff section 6): direction or tilt past 3
     * degrees. A zoom or a move aside alone (a run framed, "where am I") is not a turn, so no compass (the review of 24.11).
     */
    fun away(s: OrbitCamera.State, f: OrbitCamera.State): Boolean {
        val lim = Math.toRadians(3.0).toFloat()
        val dy = abs(atan2(sin(s.yaw - f.yaw), cos(s.yaw - f.yaw)))
        return dy > lim || abs(s.pitch - f.pitch) > lim
    }

    /** B2 (26.11.2026): a reading farther than this from every run and lift is "not at the mountain": no dot and no camera move. */
    const val NEAR_M = 2500f

    /** The runs' and lifts' box in the site's metres, grown by [NEAR_M]: x0, y0, x1, y1 (null when there is no data). */
    fun resortBox(runs: Runs): FloatArray? {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        fun take(a: FloatArray) { var i = 0; while (i + 1 < a.size) { x0 = minOf(x0, a[i]); x1 = maxOf(x1, a[i]); y0 = minOf(y0, a[i + 1]); y1 = maxOf(y1, a[i + 1]); i += 2 } }
        for (p in runs.pistes) for (l in p.lines) take(l)
        for (l in runs.lifts) take(l.pts)
        if (x0 > x1) return null
        return floatArrayOf(x0 - NEAR_M, y0 - NEAR_M, x1 + NEAR_M, y1 + NEAR_M)
    }

    /** True when a reading is near the resort; with no box (no data) nothing is trusted. */
    fun atResort(box: FloatArray?, x: Float, y: Float): Boolean = box != null && x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]
}

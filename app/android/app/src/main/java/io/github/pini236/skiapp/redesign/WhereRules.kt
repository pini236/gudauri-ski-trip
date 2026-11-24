package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.map.OrbitCamera
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
}

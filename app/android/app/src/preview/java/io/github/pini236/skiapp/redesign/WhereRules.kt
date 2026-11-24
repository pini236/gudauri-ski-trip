package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.map.OrbitCamera
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Part 4 (design/handoff/2026-11-23-redesign-on-mountain): the rules behind the compass, apart from how it is drawn. */
internal object WhereRules {
    /** The compass shows once the view is not the opening one: direction or tilt past 3 degrees, distance past 5 %, or 150 m aside. */
    fun away(s: OrbitCamera.State, f: OrbitCamera.State): Boolean {
        val lim = Math.toRadians(3.0).toFloat()
        val dy = abs(atan2(sin(s.yaw - f.yaw), cos(s.yaw - f.yaw)))
        return dy > lim || abs(s.pitch - f.pitch) > lim || abs(s.dist - f.dist) > f.dist * .05f || hypot(s.tx - f.tx, s.tz - f.tz) > 150f
    }
}

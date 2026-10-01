package io.github.pini236.skiapp.map

import android.opengl.Matrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Orbit camera over the mountain. World: x east, y up (metres), z south.
 * yaw 0 looks north; pitch is the angle above the horizon. Touch (UI thread) and the
 * renderer (GL thread) both move it, so every access goes through the lock.
 */
class OrbitCamera {
    var tx = 600f; var ty = 2300f; var tz = -500f
    var dist = 11000f
    var yaw = 0.35f
    var pitch = 0.62f
    var fovDeg = 40f

    /** Fling velocity of the target, metres per second. */
    var vx = 0f; var vz = 0f

    data class State(val tx: Float, val ty: Float, val tz: Float, val dist: Float, val yaw: Float, val pitch: Float)

    @Synchronized fun state() = State(tx, ty, tz, dist, yaw, pitch)

    @Synchronized fun set(s: State) { tx = s.tx; ty = s.ty; tz = s.tz; dist = s.dist; yaw = s.yaw; pitch = s.pitch }

    /** Read, change and write back in one go (touch and the renderer both move the camera). */
    @Synchronized fun update(f: (State) -> State) = set(f(state()))

    /** Metres on the ground per screen pixel at the target. */
    private fun metresPerPx(viewH: Int) = (2 * dist * tan(fovDeg * PI / 360) / viewH.coerceAtLeast(1)).toFloat()

    @Synchronized fun fling(vxPx: Float, vyPx: Float, viewH: Int) {
        val k = metresPerPx(viewH)
        val rx = cos(yaw); val rz = -sin(yaw)
        val fx = -sin(yaw); val fz = -cos(yaw)
        val tiltK = 1f / sin(pitch).coerceAtLeast(0.35f)
        vx = -rx * vxPx * k + fx * vyPx * k * tiltK
        vz = -rz * vxPx * k + fz * vyPx * k * tiltK
    }

    @Synchronized fun stopFling() { vx = 0f; vz = 0f }


    /** Advances the fling; returns true while still moving. */
    @Synchronized fun step(dt: Float): Boolean {
        if (vx == 0f && vz == 0f) return false
        tx += vx * dt; tz += vz * dt
        val damp = Math.exp(-3.2 * dt).toFloat()
        vx *= damp; vz *= damp
        if (vx * vx + vz * vz < 4f) { vx = 0f; vz = 0f }
        return true
    }

    @Synchronized fun clampTo(x0: Float, x1: Float, z0: Float, z1: Float, ground: (Float, Float) -> Float) {
        tx = tx.coerceIn(x0, x1); tz = tz.coerceIn(z0, z1)
        ty = ground(tx, tz)
    }

    companion object {
        fun eye(s: State, out: FloatArray) {
            val cp = cos(s.pitch)
            out[0] = s.tx + sin(s.yaw) * cp * s.dist
            out[1] = s.ty + sin(s.pitch) * s.dist
            out[2] = s.tz + cos(s.yaw) * cp * s.dist
        }

        /** View-projection for [s]; writes the eye position into [eyeOut]. */
        fun mvp(s: State, aspect: Float, out: FloatArray, eyeOut: FloatArray, fovDeg: Float = 40f, minEye: ((Float, Float) -> Float)? = null) {
            eye(s, eyeOut)
            if (minEye != null) eyeOut[1] = maxOf(eyeOut[1], minEye(eyeOut[0], eyeOut[2]) + 40f)
            val view = FloatArray(16); val proj = FloatArray(16)
            Matrix.setLookAtM(view, 0, eyeOut[0], eyeOut[1], eyeOut[2], s.tx, s.ty, s.tz, 0f, 1f, 0f)
            val near = (s.dist * 0.02f).coerceIn(5f, 150f)
            Matrix.perspectiveM(proj, 0, fovDeg, aspect, near, s.dist * 5f + 30000f)
            Matrix.multiplyMM(out, 0, proj, 0, view, 0)
        }
    }
}

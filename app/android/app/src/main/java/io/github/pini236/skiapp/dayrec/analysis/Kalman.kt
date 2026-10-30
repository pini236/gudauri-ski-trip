package io.github.pini236.skiapp.dayrec.analysis

import io.github.pini236.skiapp.dayrec.DayLog
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Step 1 of the day analysis: a Kalman filter per axis, state (position, velocity). A port of analyze.py `Axis`. */
internal class Axis {
    var p = 0.0; var v = 0.0
    var a = 0.0; var b = 0.0; var c = 0.0 // covariance [[a, b], [b, c]]

    fun reset(z: Double, rz: Double, v0: Double, rv: Double) { p = z; v = v0; a = rz; b = 0.0; c = rv }

    fun predict(dt: Double, q: Double) {
        p += v * dt
        val q2 = q * q
        val a0 = a; val b0 = b; val c0 = c
        a = a0 + 2 * b0 * dt + c0 * dt * dt + q2 * Math.pow(dt, 4.0) / 4
        b = b0 + c0 * dt + q2 * Math.pow(dt, 3.0) / 2
        c = c0 + q2 * dt * dt
    }

    fun innovPos(z: Double, r: Double) = doubleArrayOf(z - p, a + r)

    fun updPos(y: Double, s: Double) {
        val k0 = a / s; val k1 = b / s
        val a0 = a; val b0 = b; val c0 = c
        p += k0 * y; v += k1 * y
        a = (1 - k0) * a0; b = (1 - k0) * b0; c = c0 - k1 * b0
    }

    fun innovVel(z: Double, r: Double) = doubleArrayOf(z - v, c + r)

    fun updVel(y: Double, s: Double) {
        val k0 = b / s; val k1 = c / s
        val a0 = a; val b0 = b; val c0 = c
        p += k0 * y; v += k1 * y
        a = a0 - k0 * b0; b = b0 - k0 * c0; c = (1 - k1) * c0
    }

    fun state() = doubleArrayOf(p, v, a, b, c)
}

/** Positions and velocities in local metres: [x, y, vx, vy] per point. */
internal fun kalman(xy: List<DoubleArray>, pts: List<DayLog.Point>, P: Params, smooth: Boolean): List<DoubleArray> {
    val q = P.d("kalman", "q_acc"); val rmin = P.d("kalman", "pos_sigma_min_m"); val vmin = P.d("kalman", "vel_sigma_min_mps")
    val gate = P.d("kalman", "gate_chi2"); val gn = P.i("kalman", "gate_reset_n"); val still = P.d("kalman", "still_mps")
    val X = Axis(); val Y = Axis()
    val n = pts.size
    val fx = ArrayList<DoubleArray>(n); val fy = ArrayList<DoubleArray>(n)
    val px = ArrayList<DoubleArray>(n); val py = ArrayList<DoubleArray>(n)
    val dts = DoubleArray(n); val starts = BooleanArray(n)
    var gated = 0
    for (i in 0 until n) {
        val pt = pts[i]
        val x = xy[i][0]; val y = xy[i][1]
        val r = Math.pow(max(rmin, pt.hAcc ?: 10.0), 2.0)
        var vel: DoubleArray? = null
        val s = pt.speed; val b = pt.bearing
        if (s != null && b != null) {
            val rv = Math.pow(max(vmin, pt.speedAcc ?: 1.0), 2.0)
            val br = Math.toRadians(b)
            vel = doubleArrayOf(s * sin(br), -s * cos(br), rv)
        } else if (s != null && s < still) {
            // no bearing because the phone is not moving: the velocity is about zero
            val rv = Math.pow(max(vmin, s + (pt.speedAcc ?: 1.0)), 2.0)
            vel = doubleArrayOf(0.0, 0.0, rv)
        }
        val dt = if (i > 0) (pt.t - pts[i - 1].t) / 1000.0 else 0.0
        if (i == 0 || dt > P.resetGapS || gated >= gn) {
            val v0 = vel ?: doubleArrayOf(0.0, 0.0, 16.0)
            X.reset(x, r, v0[0], v0[2]); Y.reset(y, r, v0[1], v0[2])
            gated = 0
            starts[i] = true
            px += X.state(); py += Y.state(); dts[i] = 0.0
        } else {
            X.predict(dt, q); Y.predict(dt, q)
            starts[i] = false
            px += X.state(); py += Y.state(); dts[i] = dt
            val ix = X.innovPos(x, r); val iy = Y.innovPos(y, r)
            if (ix[0] * ix[0] / ix[1] + iy[0] * iy[0] / iy[1] > gate) gated++
            else { gated = 0; X.updPos(ix[0], ix[1]); Y.updPos(iy[0], iy[1]) }
            if (vel != null) {
                val ux = X.innovVel(vel[0], vel[2]); val uy = Y.innovVel(vel[1], vel[2])
                if (ux[0] * ux[0] / ux[1] + uy[0] * uy[0] / uy[1] <= gate) { X.updVel(ux[0], ux[1]); Y.updVel(uy[0], uy[1]) }
            }
        }
        fx += X.state(); fy += Y.state()
    }
    if (!smooth) return List(n) { doubleArrayOf(fx[it][0], fy[it][0], fx[it][1], fy[it][1]) }
    val sx = rts(fx, px, dts, starts); val sy = rts(fy, py, dts, starts)
    return List(n) { doubleArrayOf(sx[it][0], sy[it][0], sx[it][1], sy[it][1]) }
}

/** Rauch-Tung-Striebel smoother for one axis; never across a reset. Returns [position, velocity] per point. */
private fun rts(f: List<DoubleArray>, p: List<DoubleArray>, dts: DoubleArray, starts: BooleanArray): Array<DoubleArray> {
    val n = f.size
    val out = Array(n) { doubleArrayOf(0.0, 0.0) }
    if (n == 0) return out
    out[n - 1] = doubleArrayOf(f[n - 1][0], f[n - 1][1])
    for (k in n - 2 downTo 0) {
        if (starts[k + 1]) { out[k] = doubleArrayOf(f[k][0], f[k][1]); continue }
        val dt = dts[k + 1]
        val a = f[k][2]; val b = f[k][3]; val c = f[k][4]
        // C = Pf F^T Pp^-1, F = [[1, dt], [0, 1]]
        val m00 = a + b * dt; val m01 = b; val m10 = b + c * dt; val m11 = c
        val pa = p[k + 1][2]; val pb = p[k + 1][3]; val pc = p[k + 1][4]
        val det = pa * pc - pb * pb
        if (det <= 0) { out[k] = doubleArrayOf(f[k][0], f[k][1]); continue }
        val i00 = pc / det; val i01 = -pb / det; val i11 = pa / det
        val c00 = m00 * i00 + m01 * i01; val c01 = m00 * i01 + m01 * i11
        val c10 = m10 * i00 + m11 * i01; val c11 = m10 * i01 + m11 * i11
        val d0 = out[k + 1][0] - p[k + 1][0]; val d1 = out[k + 1][1] - p[k + 1][1]
        out[k] = doubleArrayOf(f[k][0] + c00 * d0 + c01 * d1, f[k][1] + c10 * d0 + c11 * d1)
    }
    return out
}

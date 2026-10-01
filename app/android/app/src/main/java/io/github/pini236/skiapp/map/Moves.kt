package io.github.pini236.skiapp.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The camera seen as a lens: screen point to ground and back. Plain math, no android.opengl,
 * so the gestures and the framing are unit tested on the computer. It ignores the small lift
 * that keeps the eye above a ridge (OrbitCamera.mvp), which is fine for gestures.
 */
class Lens(val s: OrbitCamera.State, val w: Float, val h: Float, fovDeg: Float = 40f) {
    val eye = FloatArray(3).also { OrbitCamera.eye(s, it) }
    private val f = norm(floatArrayOf(s.tx - eye[0], s.ty - eye[1], s.tz - eye[2]))
    private val r = norm(floatArrayOf(-f[2], 0f, f[0])) // forward × up
    private val u = floatArrayOf(r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
    private val tanY = tan(Math.toRadians(fovDeg / 2.0)).toFloat()
    private val tanX = tanY * w / h

    /** Unit direction of the ray through a screen point (pixels, y down). */
    fun ray(px: Float, py: Float): FloatArray {
        val nx = (2 * px / w - 1) * tanX; val ny = (1 - 2 * py / h) * tanY
        return norm(floatArrayOf(f[0] + r[0] * nx + u[0] * ny, f[1] + r[1] * nx + u[1] * ny, f[2] + r[2] * nx + u[2] * ny))
    }

    /** Where the ray through a screen point meets the level plane at height [y]; null above the horizon or too far away. */
    fun ground(px: Float, py: Float, y: Float = s.ty, maxDist: Float = s.dist * 8): FloatArray? {
        val d = ray(px, py)
        if (d[1] > -1e-3f) return null
        val t = (y - eye[1]) / d[1]
        if (t <= 0 || t > maxDist) return null
        return floatArrayOf(eye[0] + d[0] * t, y, eye[2] + d[2] * t)
    }

    /** Screen pixel of a world point, or null when it is behind the camera. */
    fun project(x: Float, y: Float, z: Float): FloatArray? {
        val vx = x - eye[0]; val vy = y - eye[1]; val vz = z - eye[2]
        val zf = vx * f[0] + vy * f[1] + vz * f[2]
        if (zf <= 1f) return null
        val xr = (vx * r[0] + vy * r[1] + vz * r[2]) / (zf * tanX)
        val yu = (vx * u[0] + vy * u[1] + vz * u[2]) / (zf * tanY)
        return floatArrayOf((xr + 1) / 2 * w, (1 - yu) / 2 * h)
    }

    companion object {
        fun norm(v: FloatArray): FloatArray {
            val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-9f)
            return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
        }
    }
}

/**
 * Camera moves for the map gestures, as on phone maps: the ground under the fingers stays under the fingers.
 * Each takes a camera state and returns the next one.
 */
object Moves {
    const val MIN_DIST = 250f
    const val MAX_DIST = 32000f

    /** One finger (or the middle of two) moved from (x0, y0) to (x1, y1). */
    fun pan(s: OrbitCamera.State, w: Float, h: Float, x0: Float, y0: Float, x1: Float, y1: Float): OrbitCamera.State {
        val lens = Lens(s, w, h)
        val a = lens.ground(x0, y0, maxDist = s.dist * 3); val b = lens.ground(x1, y1, maxDist = s.dist * 3)
        if (a != null && b != null) return s.copy(tx = s.tx + a[0] - b[0], tz = s.tz + a[2] - b[2])
        // near the horizon the ground is too far to hold: move at the speed of the ground at the centre
        val k = (2 * s.dist * tan(Math.toRadians(20.0)) / h).toFloat()
        val tiltK = 1f / sin(s.pitch).coerceAtLeast(0.35f)
        val dx = x1 - x0; val dy = y1 - y0
        val rx = cos(s.yaw); val rz = -sin(s.yaw); val fx = -sin(s.yaw); val fz = -cos(s.yaw)
        return s.copy(tx = s.tx - rx * dx * k + fx * dy * k * tiltK, tz = s.tz - rz * dx * k + fz * dy * k * tiltK)
    }

    /** Zoom by [factor] (>1 closer) towards the point (fx, fy): it stays where it is on the screen. */
    fun zoom(s: OrbitCamera.State, w: Float, h: Float, fx: Float, fy: Float, factor: Float): OrbitCamera.State {
        val next = s.copy(dist = (s.dist / factor).coerceIn(MIN_DIST, MAX_DIST))
        val a = Lens(s, w, h).ground(fx, fy, maxDist = s.dist * 3) ?: return next
        val b = Lens(next, w, h).ground(fx, fy, maxDist = next.dist * 3) ?: return next
        return next.copy(tx = next.tx + a[0] - b[0], tz = next.tz + a[2] - b[2])
    }

    /**
     * Turn the map by [angle] radians on the screen (positive = clockwise, as the fingers turn, y down)
     * around the point (fx, fy), which stays put.
     */
    fun rotate(s: OrbitCamera.State, w: Float, h: Float, fx: Float, fy: Float, angle: Float): OrbitCamera.State {
        // the world turns clockwise on the screen when the camera turns anticlockwise seen from above: yaw grows
        val d = angle
        val g = Lens(s, w, h).ground(fx, fy, maxDist = s.dist * 3) ?: floatArrayOf(s.tx, s.ty, s.tz)
        val ox = s.tx - g[0]; val oz = s.tz - g[2]
        val c = cos(d); val sn = sin(d)
        return s.copy(tx = g[0] + ox * c + oz * sn, tz = g[2] - ox * sn + oz * c, yaw = s.yaw + d)
    }

    /** Lowest tilt: almost level, so the low winter sun and the moon can come into view over the ridges. */
    const val MIN_PITCH = 0.035f

    /** Two fingers up or down together: tilt towards the horizon (up) or down to the map from above (down). */
    fun tilt(s: OrbitCamera.State, h: Float, dy: Float): OrbitCamera.State =
        s.copy(pitch = (s.pitch + dy / h * 1.6f).coerceIn(MIN_PITCH, 1.5f))

    /** Wraps an angle difference to -π..π. */
    fun wrap(a: Float): Float {
        var d = a
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        return d
    }

    /** Distance between two screen points. */
    fun gap(x0: Float, y0: Float, x1: Float, y1: Float) = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))

    /** True when two fingers moved by (dx0, dy0) and (dx1, dy1) mostly up or down together: the tilt gesture. */
    fun isTilt(dx0: Float, dy0: Float, dx1: Float, dy1: Float, spreadChange: Float, slop: Float): Boolean =
        dy0 * dy1 > 0 && abs(dy0) > abs(dx0) * 1.5f && abs(dy1) > abs(dx1) * 1.5f && abs(spreadChange) < slop * 1.5f
}

/** Where the camera lands on a chosen run: the whole run on the screen, between the bars, looking uphill. */
object Framing {
    /** Free screen area, as fractions: the stats bar above, the run's sign and buttons below. */
    class Box(val left: Float = 0.08f, val right: Float = 0.92f, val top: Float = 0.16f, val bottom: Float = 0.78f)

    /**
     * [path] is x, y, z triples from the top of the run to the bottom; [ground] gives the height of the snow.
     * The camera looks from below the run up at it, so its top is at the top of the screen.
     */
    fun fit(path: FloatArray, w: Float, h: Float, ground: (Float, Float) -> Float, pitch: Float = 0.8f, box: Box = Box()): OrbitCamera.State {
        val n = path.size / 3
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until n) { minX = minOf(minX, path[i * 3]); maxX = maxOf(maxX, path[i * 3]); minZ = minOf(minZ, path[i * 3 + 2]); maxZ = maxOf(maxZ, path[i * 3 + 2]) }
        val cx = (minX + maxX) / 2; val cz = (minZ + maxZ) / 2
        // look uphill: from the bottom towards the top
        val yaw = kotlin.math.atan2(-(path[0] - path[(n - 1) * 3]), -(path[2] - path[(n - 1) * 3 + 2]))
        var s = OrbitCamera.State(cx, ground(cx, cz), cz, 3000f, yaw, pitch)
        val stride = maxOf(1, n / 120)
        repeat(3) {
            // the closest distance at which every point is inside the box
            var lo = Moves.MIN_DIST; var hi = Moves.MAX_DIST
            repeat(28) {
                val mid = kotlin.math.sqrt(lo * hi)
                if (fits(s.copy(dist = mid), path, stride, w, h, box)) hi = mid else lo = mid
            }
            s = s.copy(dist = hi)
            // then centre the run in the box
            val b = bounds(s, path, stride, w, h) ?: return s
            val bx = (b[0] + b[1]) / 2; val by = (b[2] + b[3]) / 2
            val mx = (box.left + box.right) / 2 * w; val my = (box.top + box.bottom) / 2 * h
            s = Moves.pan(s, w, h, bx, by, mx, my).let { it.copy(ty = ground(it.tx, it.tz)) }
        }
        return s
    }

    /** minX, maxX, minY, maxY of the run on the screen, or null if part of it is behind the camera. */
    fun bounds(s: OrbitCamera.State, path: FloatArray, stride: Int, w: Float, h: Float): FloatArray? {
        val lens = Lens(s, w, h)
        val b = floatArrayOf(Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE)
        var i = 0
        val n = path.size / 3
        while (i < n) {
            val p = lens.project(path[i * 3], path[i * 3 + 1], path[i * 3 + 2]) ?: return null
            b[0] = minOf(b[0], p[0]); b[1] = maxOf(b[1], p[0]); b[2] = minOf(b[2], p[1]); b[3] = maxOf(b[3], p[1])
            i = if (i == n - 1) n else minOf(i + stride, n - 1)
        }
        return b
    }

    private fun fits(s: OrbitCamera.State, path: FloatArray, stride: Int, w: Float, h: Float, box: Box): Boolean {
        val b = bounds(s, path, stride, w, h) ?: return false
        return b[0] >= box.left * w && b[1] <= box.right * w && b[2] >= box.top * h && b[3] <= box.bottom * h
    }
}

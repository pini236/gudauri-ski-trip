package io.github.pini236.skiapp.qa

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import io.github.pini236.skiapp.map.Lens
import io.github.pini236.skiapp.map.MapView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Scripted touch gestures for the emulator run (debug builds only). The emulator's input command has one
 * finger, so pinches, turns and tilts are played here as real touch events into the map, and the log says
 * how the camera moved and how far the ground drifted from the finger.
 */
object GesturePlayer {
    private val main = Handler(Looper.getMainLooper())
    private const val STEPS = 24
    private const val STEP_MS = 16L

    fun play(map: MapView, name: String) {
        val w = map.width.toFloat(); val h = map.height.toFloat()
        val before = map.camera.state()
        // each gesture: pointer paths as functions of 0..1, and the screen point that should stay on its ground
        val c = floatArrayOf(w / 2, h * 0.55f)
        fun at(x: Float, y: Float) = floatArrayOf(x, y)
        val paths: List<(Float) -> FloatArray>
        var anchorFrom: FloatArray? = null; var anchorTo: FloatArray? = null
        when (name) {
            "pan" -> { paths = listOf { k -> at(w * (0.5f - 0.2f * k), h * (0.6f - 0.15f * k)) }; anchorFrom = at(w * 0.5f, h * 0.6f); anchorTo = at(w * 0.3f, h * 0.45f) }
            "pinch-out", "pinch-in" -> {
                val (a, b) = if (name == "pinch-out") 0.08f to 0.3f else 0.3f to 0.08f
                val f = at(w * 0.32f, h * 0.68f) // off-centre on purpose: the zoom goes towards the fingers
                paths = listOf({ k -> at(f[0] - w * (a + (b - a) * k), f[1]) }, { k -> at(f[0] + w * (a + (b - a) * k), f[1]) })
                anchorFrom = f; anchorTo = f
            }
            "turn" -> {
                val r = w * 0.22f; val turn = Math.toRadians(45.0).toFloat()
                paths = listOf({ k -> at(c[0] - r * cos(turn * k), c[1] - r * sin(turn * k)) }, { k -> at(c[0] + r * cos(turn * k), c[1] + r * sin(turn * k)) })
                anchorFrom = c; anchorTo = c
            }
            "tilt-up", "tilt-down" -> {
                val dy = if (name == "tilt-up") -h * 0.18f else h * 0.18f
                paths = listOf({ k -> at(w * 0.35f, h * 0.6f + dy * k) }, { k -> at(w * 0.65f, h * 0.6f + dy * k) })
            }
            "double-tap" -> { taps(map, listOf(c), 2); report(map, name, before, c, c, 900); return }
            "two-tap" -> { taps(map, listOf(at(w * 0.4f, h * 0.55f), at(w * 0.6f, h * 0.55f)), 1); report(map, name, before, null, null, 900); return }
            else -> { Qa.log("gesture unknown $name"); return }
        }
        val down = SystemClock.uptimeMillis()
        fun pts(k: Float) = paths.map { it(k) }
        send(map, ev(down, down, MotionEvent.ACTION_DOWN, pts(0f).take(1)))
        if (paths.size == 2) send(map, ev(down, down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(0f)))
        for (i in 1..STEPS) main.postDelayed({
            val t = SystemClock.uptimeMillis()
            send(map, ev(down, t, MotionEvent.ACTION_MOVE, pts(i / STEPS.toFloat())))
            if (i == STEPS) {
                if (paths.size == 2) send(map, ev(down, t, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pts(1f)))
                send(map, ev(down, t, MotionEvent.ACTION_UP, pts(1f).take(1)))
            }
        }, i * STEP_MS)
        report(map, name, before, anchorFrom, anchorTo, STEPS * STEP_MS + 300)
    }

    private fun taps(map: MapView, points: List<FloatArray>, count: Int) {
        for (n in 0 until count) main.postDelayed({
            val t = SystemClock.uptimeMillis()
            send(map, ev(t, t, MotionEvent.ACTION_DOWN, points.take(1)))
            if (points.size == 2) send(map, ev(t, t, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), points))
            main.postDelayed({
                val u = SystemClock.uptimeMillis()
                if (points.size == 2) send(map, ev(t, u, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), points))
                send(map, ev(t, u, MotionEvent.ACTION_UP, points.take(1)))
            }, 60)
        }, n * 150L)
    }

    /** After the gesture: how the camera moved, and how many pixels the anchored ground ended up from the finger. */
    private fun report(map: MapView, name: String, before: io.github.pini236.skiapp.map.OrbitCamera.State, from: FloatArray?, to: FloatArray?, afterMs: Long) {
        val w = map.width.toFloat(); val h = map.height.toFloat()
        val g = from?.let { Lens(before, w, h).ground(it[0], it[1]) }
        main.postDelayed({
            val a = map.camera.state()
            var drift = ""
            if (g != null && to != null) {
                val p = Lens(a, w, h).project(g[0], g[1], g[2])
                drift = if (p == null) " · anchor behind the camera" else " · anchor drift ${hypot(p[0] - to[0], p[1] - to[1]).toInt()} px"
            }
            Qa.log("gesture $name done · dist ${before.dist.toInt()}→${a.dist.toInt()} · yaw ${deg(before.yaw)}→${deg(a.yaw)} · pitch ${deg(before.pitch)}→${deg(a.pitch)}$drift")
        }, afterMs)
    }

    private fun deg(r: Float) = Math.round(Math.toDegrees(r.toDouble())).toInt()

    private fun send(map: MapView, e: MotionEvent) { map.dispatchTouchEvent(e); e.recycle() }

    private fun ev(down: Long, t: Long, action: Int, pts: List<FloatArray>): MotionEvent {
        val props = Array(pts.size) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pts.size) { MotionEvent.PointerCoords().apply { x = pts[it][0]; y = pts[it][1]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(down, t, action, pts.size, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }
}

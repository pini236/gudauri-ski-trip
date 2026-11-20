package io.github.pini236.skiapp.redesign

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Moves
import io.github.pini236.skiapp.map.OrbitCamera
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.map.Sun
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The live mountain's surface: the free camera of today's map (map/Moves.kt), on demand rendering, and a tap that
 * chooses a run. The gestures as on the map: one finger moves, two pinch towards themselves, turn past a threshold,
 * or tilt when they move up or down together; a double tap comes closer, a two-finger tap goes back.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class LiveView(context: Context, private val scene: MapScene) : GLSurfaceView(context) {
    val camera = OrbitCamera()
    private val density = resources.displayMetrics.density
    private val renderer = LiveRenderer(scene, camera, density) { requestRender() }
    private val worker = Executors.newSingleThreadExecutor()
    /** A run chosen by a tap (its key), or none. */
    var onChoose: ((String?) -> Unit)? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(object : EGLConfigChooser {
            override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
                fun pick(attrs: IntArray): EGLConfig? {
                    val n = IntArray(1); val out = arrayOfNulls<EGLConfig>(1)
                    return if (egl.eglChooseConfig(display, attrs, out, 1, n) && n[0] > 0) out[0] else null
                }
                val es3 = 0x40
                val base = intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_RENDERABLE_TYPE, es3)
                return pick(base + intArrayOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE))
                    ?: pick(base + intArrayOf(EGL10.EGL_NONE))
                    ?: pick(intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE))!!
            }
        })
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        worker.execute { renderer.detail = Detail.make(); requestRender() }
        // the opening: from the south, low over the valley, as the picture looks at the mountain; then a slow approach
        val t = scene.terrain
        val tx = 300f; val tz = -900f
        camera.set(OrbitCamera.State(tx, t.elev(tx, tz), tz, 12500f, 0f, 0.20f))
        renderer.animate(OrbitCamera.State(tx, t.elev(tx, tz), tz, 9400f, 0.06f, 0.30f), 2.2f)
    }

    private var lightKey = Long.MIN_VALUE
    /** The light of a moment: the shader's uniforms at once, the shadows a moment later (worked out off the GL thread). */
    fun setTime(epochMs: Long) {
        val l = Sky.at(epochMs)
        renderer.light = l; requestRender()
        // the shadows, at most once per 10 minutes of the sun
        val key = epochMs / 600_000
        if (key == lightKey) return
        lightKey = key
        worker.execute { if (lightKey == key) renderer.setShadow(Sun.shadows(scene.terrain, l.dir)) }
    }

    fun setPal(p: PeakData.Pal?) { renderer.pal = p; requestRender() }
    fun choose(key: String?) { renderer.chosen = key; requestRender() }
    fun release() = worker.shutdownNow()

    // ---- gestures ----
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    private var vt: VelocityTracker? = null
    private var lastX = 0f; private var lastY = 0f; private var downX = 0f; private var downY = 0f; private var downT = 0L
    private var moved = false; private var multi = false
    private enum class Two { UNDECIDED, TRANSFORM, TILT }
    private var two = Two.UNDECIDED
    private val s0 = FloatArray(2); private val s1 = FloatArray(2); private var startGap = 0f
    private var lastMidX = 0f; private var lastMidY = 0f; private var lastGap = 0f; private var lastAngle = 0f
    private var turned = 0f; private var turning = false; private var twoDownT = 0L
    private var lastTapT = 0L; private var tapX = 0f; private var tapY = 0f
    private val pendingPick = Runnable { pick(tapX, tapY) }
    private val snow: (Float, Float) -> Float = { x, z -> scene.terrain.elev(x, z) }

    private fun move(f: (OrbitCamera.State) -> OrbitCamera.State) { camera.update(f); requestRender() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat(); val h = height.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                renderer.stopAnim(); camera.stopFling()
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; downT = e.eventTime; moved = false; multi = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                multi = true; moved = true; two = Two.UNDECIDED; turned = 0f; turning = false; twoDownT = e.eventTime
                twoFrom(e.getX(0), e.getY(0), e.getX(1), e.getY(1))
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (e.pointerCount >= 2) twoFingers(e, w, h)
                else {
                    if (!moved && hypot(e.x - downX, e.y - downY) > slop) { moved = true; lastX = downX; lastY = downY }
                    if (moved) { val x0 = lastX; val y0 = lastY; move { Moves.pan(it, w, h, x0, y0, e.x, e.y, snow) } }
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.pointerCount == 2 && two == Two.UNDECIDED && e.eventTime - twoDownT < 300) {
                    val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
                    renderer.animate(Moves.zoom(camera.state(), w, h, mx, my, 0.5f, snow), 0.35f)
                    two = Two.TRANSFORM
                }
                val rest = (0 until e.pointerCount).filter { it != e.actionIndex }
                if (rest.size >= 2) twoFrom(e.getX(rest[0]), e.getY(rest[0]), e.getX(rest[1]), e.getY(rest[1]))
                else { lastX = e.getX(rest[0]); lastY = e.getY(rest[0]) }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && !multi && e.eventTime - downT < 300) tap(e.x, e.y, e.eventTime, w, h)
                else if (!multi) {
                    vt?.computeCurrentVelocity(1000)
                    val vx = vt?.xVelocity ?: 0f; val vy = vt?.yVelocity ?: 0f
                    if (hypot(vx, vy) > 300) camera.fling(vx, vy, height)
                }
                vt?.recycle(); vt = null
                requestRender()
            }
            MotionEvent.ACTION_CANCEL -> { vt?.recycle(); vt = null }
        }
        return true
    }

    private fun twoFrom(x0: Float, y0: Float, x1: Float, y1: Float) {
        s0[0] = x0; s0[1] = y0; s1[0] = x1; s1[1] = y1
        startGap = Moves.gap(x0, y0, x1, y1)
        lastMidX = (x0 + x1) / 2; lastMidY = (y0 + y1) / 2; lastGap = startGap; lastAngle = atan2(y1 - y0, x1 - x0)
    }

    private fun twoFingers(e: MotionEvent, w: Float, h: Float) {
        val x0 = e.getX(0); val y0 = e.getY(0); val x1 = e.getX(1); val y1 = e.getY(1)
        val midX = (x0 + x1) / 2; val midY = (y0 + y1) / 2
        val gap = Moves.gap(x0, y0, x1, y1); val angle = atan2(y1 - y0, x1 - x0)
        if (two == Two.UNDECIDED) {
            if (hypot(x0 - s0[0], y0 - s0[1]) < slop && hypot(x1 - s1[0], y1 - s1[1]) < slop) return
            two = if (Moves.isTilt(x0 - s0[0], y0 - s0[1], x1 - s1[0], y1 - s1[1], gap - startGap, slop)) Two.TILT else Two.TRANSFORM
        }
        if (two == Two.TILT) {
            val dy = midY - lastMidY
            move { Moves.tilt(it, h, dy) }
        } else {
            val px = lastMidX; val py = lastMidY
            val factor = if (lastGap > 1f) gap / lastGap else 1f
            val da = Moves.wrap(angle - lastAngle)
            turned += da
            if (!turning && abs(turned) > 0.2f) turning = true
            move {
                var st = Moves.pan(it, w, h, px, py, midX, midY, snow)
                st = Moves.zoom(st, w, h, midX, midY, factor, snow)
                if (turning) Moves.rotate(st, w, h, midX, midY, da, snow) else st
            }
        }
        lastMidX = midX; lastMidY = midY; lastGap = gap; lastAngle = angle
    }

    private fun tap(x: Float, y: Float, t: Long, w: Float, h: Float) {
        if (t - lastTapT < doubleTapMs && hypot(x - tapX, y - tapY) < 48 * density) {
            removeCallbacks(pendingPick); lastTapT = 0L
            renderer.animate(Moves.zoom(camera.state(), w, h, x, y, 2.2f, snow), 0.35f)
            return
        }
        lastTapT = t; tapX = x; tapY = y
        removeCallbacks(pendingPick); postDelayed(pendingPick, doubleTapMs)
    }

    /** The named run whose line passes closest to the finger, within 28 dp, in this frame's projection. */
    private fun pick(px: Float, py: Float) {
        val mvp = FloatArray(16); val eye = FloatArray(3); val v = FloatArray(4); val o = FloatArray(4)
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye) { x, z -> scene.terrain.elev(x, z) }
        var best: String? = null; var bestD = 28 * density
        for (p in scene.runs.pistes) {
            if (!p.named) continue
            for (l in scene.draped[p.key] ?: emptyList()) {
                var i = 0
                while (i < l.size / 3) {
                    v[0] = l[i * 3]; v[1] = l[i * 3 + 1]; v[2] = l[i * 3 + 2]; v[3] = 1f
                    Matrix.multiplyMV(o, 0, mvp, 0, v, 0)
                    if (o[3] > 0) {
                        val sx = (o[0] / o[3] * 0.5f + 0.5f) * width; val sy = (1 - (o[1] / o[3] * 0.5f + 0.5f)) * height
                        val d = hypot(sx - px, sy - py)
                        if (d < bestD) { bestD = d; best = p.key }
                    }
                    i++
                }
            }
        }
        choose(best)
        onChoose?.invoke(best)
    }
}

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
import io.github.pini236.skiapp.qa.Qa
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
    private val data = LiveData.load(context)
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
        // in the background the context goes, and with it the height model on the GPU; it comes back on resume
        preserveEGLContextOnPause = false
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        // the designer's values and textures (design/handoff/2026-11-20-redesign-live): small, so the values at once,
        // the textures off the GL thread, each checked against the designer's texels
        val d = data; renderer.data = d
        camera.fovDeg = d.camera.fovDeg
        val decode = Runnable {
            val list = LiveData.TEXTURES.map { LiveTex.decode(context, it) }
            val bad = d.checks.filter { c -> list.first { it.name == c.tex }.let { t -> !LiveTex.check(t.px, t.w, t.h, c) } }
            Qa.log(if (bad.isEmpty()) "redesign live textures ok" else "redesign live textures bad ${bad.joinToString { it.tex }}")
            renderer.textures = list; requestRender()
        }
        worker.execute(decode)
        // a new GL context (the background, or a second surface) asks for them again
        renderer.needTextures = { if (!worker.isShutdown) worker.execute(decode) }
        // roads, water and the village on the snow (the map's Drape, without the contours), painted once; 1024 wide
        // (about 14 m a pixel) to stay inside the architect's 32 MB of GPU memory with the five textures
        worker.execute {
            val b = runCatching {
                val r = io.github.pini236.skiapp.meet.Relief2D.parse(context.assets.open("data/terrain.json").bufferedReader().use { it.readText() })
                io.github.pini236.skiapp.map.Drape.paint(r, 1024, contours = false)
            }.getOrNull() ?: return@execute
            renderer.drape = b; requestRender(); Qa.log("redesign live drape ready")
        }
        // the opening: the designer's camera (peak-live.json), looking north the way the picture looks (LiveOpen):
        // a little further and lower first, then a slow approach
        val c = d.camera
        val open = OrbitCamera.State(c.tx, scene.terrain.elev(c.tx, c.tz), c.tz, c.dist, LiveOpen.YAW, Math.toRadians(c.pitchDeg.toDouble()).toFloat())
        camera.set(open.copy(dist = open.dist * 1.25f, pitch = open.pitch * .8f))
        renderer.animate(open, 2.2f)
    }


    /**
     * The gestures of today's map (map/Moves.kt) work in its 40 degree lens; the live model sees through the designer's
     * wider one. Same eye, same direction: a point on the screen here is the same ray as this point, moved out from the
     * centre by the ratio of the two lenses, there.
     */
    private fun lx(x: Float) = width / 2f + (x - width / 2f) * lens
    private fun ly(y: Float) = height / 2f + (y - height / 2f) * lens
    private val lens: Float get() = (kotlin.math.tan(Math.toRadians(data.camera.fovDeg / 2.0)) / kotlin.math.tan(Math.toRadians(20.0))).toFloat()

    private var lightKey = Long.MIN_VALUE
    /** The light of a moment: the shader's uniforms at once, the shadows a moment later (worked out off the GL thread). */
    fun setTime(epochMs: Long) {
        val sky = Sky.at(epochMs)
        val (az, alt) = Sun.position(epochMs)
        // at night the light is the night key's moon (34 degrees up, from the south-east), as in the designer's frames:
        // a moon that is down or low on the day would leave the whole mountain in its shade colour
        val nk = data.keys.first { it.name == "night" }
        val moon = Sun.direction(Math.toRadians(nk.az.toDouble()), Math.toRadians(nk.alt.toDouble()))
        val l = LiveLight.at(data.keys, Math.toDegrees(alt).toFloat(), az < Math.PI, sky.sunDir, moon, true)
        renderer.light = l; requestRender()
        // the shadows, at most once per 10 minutes of the sun, softened once (1 2 1)
        val key = epochMs / 600_000
        if (key == lightKey) return
        lightKey = key
        val t = scene.terrain
        worker.execute { if (lightKey == key) renderer.setShadow(LiveLight.soften(Sun.shadows(t, l.dir), t.nx, t.ny)) }
    }

    /**
     * A view for the emulator's pictures (qa.redesign look:...): the opening's target, or the middle of [run], seen from
     * [yawDeg] (0 from the south looking north) at [pitchDeg] and [dist] metres. Logged once it is set.
     */
    fun look(yawDeg: Float, pitchDeg: Float, dist: Float, run: String?) {
        var tx = data.camera.tx; var tz = data.camera.tz
        val at = run?.takeIf { it.startsWith("@") }?.removePrefix("@")?.split(":")?.mapNotNull { it.toFloatOrNull() }
        if (at != null && at.size == 2) { tx = at[0]; tz = at[1] }
        val runKey = run?.takeIf { !it.startsWith("@") }
        runKey?.let { k ->
            val pts = scene.draped[k]?.flatMap { l -> (0 until l.size / 3).map { i -> l[i * 3] to l[i * 3 + 2] } }.orEmpty()
            if (pts.isNotEmpty()) { tx = pts.map { it.first }.average().toFloat(); tz = pts.map { it.second }.average().toFloat() }
        }
        renderer.stopAnim(); camera.stopFling()
        // a run is chosen too, so the picture shows it as light with the slope colours around it
        choose(runKey); post { onChoose?.invoke(runKey) }
        camera.set(OrbitCamera.State(tx, scene.terrain.elev(tx, tz), tz, dist, Math.toRadians(yawDeg.toDouble()).toFloat(), Math.toRadians(pitchDeg.toDouble()).toFloat()))
        requestRender()
        Qa.log("redesign live look $yawDeg $pitchDeg $dist ${run ?: "-"}")
    }

    fun setPal(p: PeakData.Pal?) { renderer.pal = p; requestRender() }
    fun choose(key: String?) {
        renderer.chosen = key; requestRender()
        // the slope colours around it, as the map's (X-3), worked out off the GL thread
        if (key == null) { renderer.ground = null; renderer.chosenLine = null; return }
        if (!worker.isShutdown) worker.execute {
            val lines = scene.draped[key] ?: return@execute
            val g = scene.slopeLayer(lines)
            val sl = lines.map { scene.lineSlopes(it) }
            val line = io.github.pini236.skiapp.map.Ribbon.build(lines, { li, i -> io.github.pini236.skiapp.map.SlopeColors.of(sl[li][i]).let { c -> floatArrayOf(c[0], c[1], c[2], 1f) } })
            if (renderer.chosen == key) { renderer.ground = g; renderer.chosenLine = key to line; requestRender() }
        }
    }
    /** Leaving the live model: the GPU's buffers and texture go now, not when the app's context dies. */
    fun release() { worker.shutdownNow(); queueEvent { renderer.free() } }

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
                    if (moved) { val x0 = lastX; val y0 = lastY; move { Moves.pan(it, w, h, lx(x0), ly(y0), lx(e.x), ly(e.y), snow) } }
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.pointerCount == 2 && two == Two.UNDECIDED && e.eventTime - twoDownT < 300) {
                    val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
                    renderer.animate(Moves.zoom(camera.state(), w, h, lx(mx), ly(my), 0.5f, snow), 0.35f)
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
                var st = Moves.pan(it, w, h, lx(px), ly(py), lx(midX), ly(midY), snow)
                st = Moves.zoom(st, w, h, lx(midX), ly(midY), factor, snow)
                if (turning) Moves.rotate(st, w, h, lx(midX), ly(midY), da, snow) else st
            }
        }
        lastMidX = midX; lastMidY = midY; lastGap = gap; lastAngle = angle
    }

    private fun tap(x: Float, y: Float, t: Long, w: Float, h: Float) {
        if (t - lastTapT < doubleTapMs && hypot(x - tapX, y - tapY) < 48 * density) {
            removeCallbacks(pendingPick); lastTapT = 0L
            renderer.animate(Moves.zoom(camera.state(), w, h, lx(x), ly(y), 2.2f, snow), 0.35f)
            return
        }
        lastTapT = t; tapX = x; tapY = y
        removeCallbacks(pendingPick); postDelayed(pendingPick, doubleTapMs)
    }

    /** The named run whose line passes closest to the finger, within 28 dp, in this frame's projection. */
    private fun pick(px: Float, py: Float) {
        val mvp = FloatArray(16); val eye = FloatArray(3); val v = FloatArray(4); val o = FloatArray(4)
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> scene.terrain.elev(x, z) }
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

/**
 * The live model opens looking the way the picture looks (the architect's condition): the picture's depth is north
 * (PeakData.Proj, d = n - c1 + near), so the camera stands to the south and looks north. In the map's camera
 * (map/Camera.kt) yaw 0 looks along -z, and the site's z grows south (data/Geo.kt): yaw 0 is north.
 */
object LiveOpen { const val YAW = 0f }

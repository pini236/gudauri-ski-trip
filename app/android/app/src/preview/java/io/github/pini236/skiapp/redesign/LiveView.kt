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
import io.github.pini236.skiapp.ui.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

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
    /** Where a tap chose a run (screen pixels), for the ring from the finger (K09). */
    var onTapAt: ((Float, Float) -> Unit)? = null

    /**
     * What is drawn over the model (phase A's names and stations, the handoff of the POC): the four names of the far
     * view (Sadzele, Bidara, Kudebi and New Gudauri, in the picture's words) at their places on the mountain, hidden
     * behind a ridge as the map's labels; and a small block at each end of every lift. Screen pixels, this frame.
     */
    class Overlay(val names: List<Triple<PeakData.Label, Float, Float>>, val stations: FloatArray)
    private var marks: List<Pair<PeakData.Label, FloatArray>>? = null
    private val stationPts: FloatArray by lazy {
        val out = ArrayList<Float>()
        for (l in scene.liftLines) { val n = l.size / 3; if (n < 2) continue
            for (i in intArrayOf(0, n - 1)) { val x = l[i * 3]; val z = l[i * 3 + 2]; out += x; out += scene.terrain.elev(x, z) + 4f; out += z } }
        out.toFloatArray()
    }
    private var lastOv: Overlay? = null; private var lastOvAt: OrbitCamera.State? = null; private var lastOvW = 0
    /** The same overlay while the camera and the size do not change (so an idle screen does not redraw it). */
    fun overlay(labels: List<PeakData.Label>): Overlay {
        val cs = camera.state()
        lastOv?.let { if (cs == lastOvAt && width == lastOvW) return it }
        return overlayNow(labels).also { lastOv = it; lastOvAt = cs; lastOvW = width }
    }
    private fun overlayNow(labels: List<PeakData.Label>): Overlay {
        val t = scene.terrain
        val m = marks ?: labels.mapNotNull { l ->
            val sub = l.sub
            if (sub != null) t.peaks.firstOrNull { it.ele == sub }?.let { p -> l to floatArrayOf(p.x, t.elev(p.x, p.y) + 30f, p.y) }
            else data.village.takeIf { it.isNotEmpty() }?.let { v ->
                val n = v.size / 3; var x = 0f; var z = 0f
                for (i in 0 until n) { x += v[i * 3] / n; z += v[i * 3 + 2] / n }
                l to floatArrayOf(x, t.elev(x, z) + 60f, z)
            }
        }.also { marks = it }
        val mvp = FloatArray(16); val eye = FloatArray(3); val o = FloatArray(4); val v = FloatArray(4)
        if (width == 0) return Overlay(emptyList(), FloatArray(0))
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> t.elev(x, z) }
        fun on(x: Float, y: Float, z: Float): FloatArray? {
            v[0] = x; v[1] = y; v[2] = z; v[3] = 1f; Matrix.multiplyMV(o, 0, mvp, 0, v, 0)
            if (o[3] <= 0f) return null
            return floatArrayOf((o[0] / o[3] * .5f + .5f) * width, (1 - (o[1] / o[3] * .5f + .5f)) * height)
        }
        // behind a ridge, as the map's labels: the mountain more than 8 m above the line from the eye
        fun seenFromEye(x: Float, y: Float, z: Float): Boolean {
            val dx = x - eye[0]; val dy = y - eye[1]; val dz = z - eye[2]
            for (i in 4 until 40) { val f = i / 40f; if (t.elev(eye[0] + dx * f, eye[2] + dz * f) > eye[1] + dy * f + 8f) return false }
            return true
        }
        val names = m.mapNotNull { (l, w) -> if (!seenFromEye(w[0], w[1], w[2])) null else on(w[0], w[1], w[2])?.let { Triple(l, it[0], it[1]) } }
        val st = ArrayList<Float>()
        val sp = stationPts
        for (i in 0 until sp.size / 3) on(sp[i * 3], sp[i * 3 + 1], sp[i * 3 + 2])?.let { if (seenFromEye(sp[i * 3], sp[i * 3 + 1], sp[i * 3 + 2])) { st += it[0]; st += it[1] } }
        return Overlay(names, st.toFloatArray())
    }

    private val tops = HashMap<String, FloatArray>()
    /** The top of a run on the screen now (pixels, x and y), for its sign (K10); null when behind the camera. */
    fun screenTop(key: String): FloatArray? {
        val top = tops.getOrPut(key) {
            var best = floatArrayOf(0f, -1e9f, 0f)
            for (l in scene.draped[key] ?: return null) for (i in 0 until l.size / 3) if (l[i * 3 + 1] > best[1]) best = floatArrayOf(l[i * 3], l[i * 3 + 1], l[i * 3 + 2])
            best
        }
        if (top[1] < -1e8f || width == 0) return null
        val mvp = FloatArray(16); val eye = FloatArray(3); val o = FloatArray(4)
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> scene.terrain.elev(x, z) }
        Matrix.multiplyMV(o, 0, mvp, 0, floatArrayOf(top[0], top[1], top[2], 1f), 0)
        if (o[3] <= 0f) return null
        return floatArrayOf((o[0] / o[3] * .5f + .5f) * width, (1 - (o[1] / o[3] * .5f + .5f)) * height)
    }

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
            // from the top down, so its light can run down the line (section 6): 0 at the top, 1 at the bottom
            val down = scene.runs.pistes.firstOrNull { it.key == key }?.let { scene.topDown(it) } ?: lines
            val sl = down.map { scene.lineSlopes(it) }
            val along = down.map { l -> FloatArray(l.size / 3).also { m -> for (i in 1 until m.size) m[i] = m[i - 1] + hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1]) } }
            val total = along.sumOf { (it.lastOrNull() ?: 0f).toDouble() }.toFloat().coerceAtLeast(1f)
            val before = FloatArray(down.size).also { b -> for (i in 1 until b.size) b[i] = b[i - 1] + (along[i - 1].lastOrNull() ?: 0f) }
            val line = io.github.pini236.skiapp.map.Ribbon.build(down,
                { li, i -> io.github.pini236.skiapp.map.SlopeColors.of(sl[li][i]).let { c -> floatArrayOf(c[0], c[1], c[2], 1f) } },
                { li, i -> (before[li] + along[li][i]) / total })
            if (renderer.chosen == key) { renderer.ground = g; renderer.chosenLine = key to line; renderer.revealFrom = System.currentTimeMillis(); requestRender() }
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
        best?.let { onTapAt?.invoke(px, py); frame(it) }
    }

    // ---- the moment of choice and the two sides of the mountain (the handoff's sections 6 and 10) ----

    /** The two wide views, both aimed at the ground: the front (the opening, section 5) and the Kobi side (section 10). */
    private fun front() = data.camera.let { c -> OrbitCamera.State(c.tx, scene.terrain.elev(c.tx, c.tz), c.tz, c.dist, LiveOpen.YAW, Math.toRadians(c.pitchDeg.toDouble()).toFloat()) }
    private fun kobi() = OrbitCamera.State(KOBI_TX, scene.terrain.elev(KOBI_TX, KOBI_TZ), KOBI_TZ, KOBI_DIST, PI.toFloat(), Math.toRadians(KOBI_PITCH).toFloat())

    /** The share of a run seen from [s]'s eye: the designer's numbers() in kobi_boards.py, point by point. */
    fun seen(key: String, s: OrbitCamera.State): Float {
        val t = scene.terrain; val e = FloatArray(3); OrbitCamera.eye(s, e)
        e[1] = maxOf(e[1], t.elev(e[0], e[2]) + 40f)
        val pts = ArrayList<Float>()
        for (l in scene.draped[key] ?: return 1f) for (i in 0 until l.size / 3) { pts += l[i * 3]; pts += l[i * 3 + 2] }
        if (pts.isEmpty()) return 1f
        var vis = 0
        for (j in 0 until pts.size / 2) {
            val x = pts[j * 2]; val z = pts[j * 2 + 1]; val y = t.elev(x, z) + 6f
            val dx = x - e[0]; val dy = y - e[1]; val dz = z - e[2]; val len = sqrt(dx * dx + dy * dy + dz * dz); val n = (len / 30f).toInt()
            var ok = true
            for (i in 1 until n) {
                val f = i.toFloat() / n; val px = e[0] + dx * f; val pz = e[2] + dz * f
                if (px < t.x0 || px > t.x1 || pz < t.y0 || pz > t.y1) continue
                if (t.elev(px, pz) > e[1] + dy * f + 2f && (1 - f) * len > 60f) { ok = false; break }
            }
            if (ok) vis++
        }
        return vis.toFloat() / (pts.size / 2)
    }

    /** The camera's yaw folded into (-pi, pi]. */
    private fun yawNow(): Float { val y = camera.state().yaw; return (atan2(sin(y), cos(y))) }
    /** Whether the camera looks from the Kobi side: within 60 degrees of yaw 180 (the "Kobi side" sign shows). */
    fun onKobiSide(): Boolean = abs(abs(yawNow()) - PI.toFloat()) <= Math.toRadians(60.0).toFloat()

    /**
     * A run was chosen: if at least half of it shows from here the view stays and frames it; if less, the wide view
     * of the side that shows more of it, turning through the east (1 s, the pitch lifted 3.5 degrees in the middle),
     * then the framing. With reduced motion there is no turn: the views are swapped.
     */
    private fun frame(key: String) {
        if (worker.isShutdown) return
        worker.execute {
            val here = seen(key, camera.state())
            if (here >= .5f) { post { postDelayed({ if (renderer.chosen == key) frameRun(key) }, 300) }; return@execute }
            val f = front(); val k = kobi()
            val sf = seen(key, f); val sk = seen(key, k)
            val wide = if (sk > sf) k else f
            Qa.log("redesign live side ${if (wide === k) "kobi" else "front"} here ${(here * 100).toInt()} front ${(sf * 100).toInt()} kobi ${(sk * 100).toInt()}")
            post { turnTo(wide) { if (renderer.chosen == key) frameRun(key) } }
        }
    }

    /** To a wide view: through the east, both ways (yaw 0 to 180 going up, back going down), or a swap. */
    fun turnTo(to: OrbitCamera.State, then: (() -> Unit)? = null) {
        renderer.stopAnim(); camera.stopFling()
        val y0 = yawNow(); camera.update { it.copy(yaw = y0) }
        // through the east: yaw rises to 180 from the front's side; going back it falls to 0
        val ty = if (to.yaw > 1f) (if (y0 < 0f) -PI.toFloat() else PI.toFloat()) else 0f
        // the end of the turn, logged with the view it ends at (the emulator photographs after it)
        val done = {
            val e = camera.state()
            Qa.log("redesign live turned tx ${"%.1f".format(e.tx)} tz ${"%.1f".format(e.tz)} dist ${"%.1f".format(e.dist)} yaw ${"%.1f".format(Math.toDegrees(e.yaw.toDouble()))} pitch ${"%.1f".format(Math.toDegrees(e.pitch.toDouble()))}")
            then?.invoke(); Unit
        }
        if (Motion.reduced(context)) { camera.set(to.copy(yaw = ty)); requestRender(); post { done() } }
        else renderer.animate(to.copy(yaw = ty), 1.0f, Math.toRadians(3.5).toFloat()) { post { done() } }
    }

    /** The framing of the whole run (section 6): 600 ms, the yaw and pitch kept, the run's box fitted with a margin. */
    private fun frameRun(key: String) {
        val lines = scene.draped[key] ?: return
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (l in lines) for (i in 0 until l.size / 3) { x0 = minOf(x0, l[i * 3]); x1 = maxOf(x1, l[i * 3]); z0 = minOf(z0, l[i * 3 + 2]); z1 = maxOf(z1, l[i * 3 + 2]) }
        val cx = (x0 + x1) / 2; val cz = (z0 + z1) / 2
        val r = hypot(x1 - x0, z1 - z0) / 2
        val vf = Math.toRadians(data.camera.fovDeg / 2.0)
        val hf = atan(tan(vf) * width.toFloat() / height.coerceAtLeast(1))
        val dist = (r / tan(minOf(vf, hf)) * 1.15).toFloat().coerceIn(1400f, 9000f)
        val s = camera.state()
        val to = s.copy(tx = cx, ty = scene.terrain.elev(cx, cz), tz = cz, dist = dist)
        if (Motion.reduced(context)) { camera.set(to); requestRender() } else renderer.animate(to, 0.6f)
        Qa.log("redesign live framed $key")
    }

    /** A run chosen as a tap chooses it (the emulator's pictures). */
    fun pickKey(key: String) { choose(key); onChoose?.invoke(key); frame(key) }

    /** The "Kobi side" sign: back to the front, the same turn the other way. */
    fun backToFront() { turnTo(front()); Qa.log("redesign live front") }

    private companion object {
        // the Kobi side (the handoff's section 10, the target on the ground): eye at -60, 4104, -8038
        const val KOBI_TX = -60f; const val KOBI_TZ = -2596.1f; const val KOBI_DIST = 5690.4f; const val KOBI_PITCH = 17.0
    }
}

/**
 * The live model opens looking the way the picture looks (the architect's condition): the picture's depth is north
 * (PeakData.Proj, d = n - c1 + near), so the camera stands to the south and looks north. In the map's camera
 * (map/Camera.kt) yaw 0 looks along -z, and the site's z grows south (data/Geo.kt): yaw 0 is north.
 */
object LiveOpen { const val YAW = 0f }

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
import kotlin.math.roundToInt
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
    /** When set, a single tap is given here and chooses nothing (the day on the mountain, round 4). */
    var onTap: ((Float, Float) -> Unit)? = null
    /** Asked first on a single tap (K06, a top station): true takes the tap, and no run is chosen. */
    var onPreTap: ((Float, Float) -> Boolean)? = null
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
    private var ovLabels: List<PeakData.Label>? = null
    /** How far (pixels) the overlay shown now is from the one the camera has now: 0 when the names sit on their places. */
    fun overlayDrift(): Float {
        val l = ovLabels ?: return 0f
        val a = overlay(l); val b = overlayNow(l); var d = 0f
        for (i in a.names.indices) b.names.getOrNull(i)?.let { q -> d = maxOf(d, kotlin.math.abs(a.names[i].second - q.second), kotlin.math.abs(a.names[i].third - q.third)) }
        for (i in a.stations.indices) b.stations.getOrNull(i)?.let { d = maxOf(d, kotlin.math.abs(a.stations[i] - it)) }
        return d
    }
    private var lastOv: Overlay? = null; private var lastOvAt: OrbitCamera.State? = null; private var lastOvW = 0; private var lastOvShift = 0f
    /**
     * The same overlay while the camera, the size and the mountain's raise do not change (so an idle screen does not
     * redraw it). The raise too: the shelf and the station lower the mountain back with the camera still (Pini, 0.1.867:
     * the names and the stations stayed 120 up in the sky after back from the shelf).
     */
    fun overlay(labels: List<PeakData.Label>): Overlay {
        ovLabels = labels
        val cs = camera.state(); val sh = renderer.shiftPx
        lastOv?.let { if (cs == lastOvAt && width == lastOvW && sh == lastOvShift) return it }
        return overlayNow(labels).also { lastOv = it; lastOvAt = cs; lastOvW = width; lastOvShift = sh }
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
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> t.elev(x, z) }; shiftUp(mvp, renderer.shiftPx, height)
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
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> scene.terrain.elev(x, z) }; shiftUp(mvp, renderer.shiftPx, height)
        Matrix.multiplyMV(o, 0, mvp, 0, floatArrayOf(top[0], top[1], top[2], 1f), 0)
        if (o[3] <= 0f) return null
        return floatArrayOf((o[0] / o[3] * .5f + .5f) * width, (1 - (o[1] / o[3] * .5f + .5f)) * height)
    }

    init {
        setEGLContextClientVersion(3)
        // the textures stay when the screen pauses: coming back shows the mountain at once, never a blank (handoff
        // 2026-11-21-redesign-around section 14.1); where the device cannot keep the context, onSurfaceCreated reloads
        preserveEGLContextOnPause = true
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
            // a texture that does not load: back to the painted mountain, never a blank sky (the architect, 24.11.2026)
            val list = runCatching { LiveData.TEXTURES.map { LiveTex.decode(context, it) } }.getOrElse { e ->
                Qa.log("redesign live textures failed ${e.javaClass.simpleName}"); post { onFail?.invoke() }; return@Runnable
            }
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
        // the far ground past the model's edge (LiveRing, the architect's option a); without it the model as it was
        worker.execute {
            val r = runCatching {
                val j = org.json.JSONObject(context.assets.open(LiveData.DIR + "ring.json").bufferedReader().use { it.readText() })
                val t = scene.terrain
                LiveRing.build(j, t.x0, t.y0, t.x1, t.y1, t::elev).also { r ->
                    // the relief map (the sixth texture); without it the ring is drawn as before, never not at all
                    r.relief = runCatching { LiveTex.decode(context, "ringrelief") }.onFailure { Qa.log("redesign ring relief failed") }.getOrNull()
                }
            }.onFailure { Qa.log("redesign ring failed ${it.javaClass.simpleName}") }.getOrNull() ?: return@execute
            ring = r
            renderer.ring = r; requestRender(); Qa.log("redesign ring ready ${r.triangles} relief ${r.relief != null}")
            lightDir?.let { ringShadow(r, it) }
        }
        // the opening: the designer's camera (peak-live.json), looking north the way the picture looks (LiveOpen):
        // a little further and lower first, then a slow approach
        val open = front()
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
    private var lastEpoch = System.currentTimeMillis()
    /** Whether the sun is up now (the light's own time): "in the sun now" is off with it down. */
    fun sunUp(): Boolean = Sun.position(lastEpoch).second > 0.0
    val lightTime: Long get() = lastEpoch

    /**
     * K07 and K08, "in the sun now": the share of each run's points (on its draped lines) out of the mountain's cast
     * shadow, the same shadows as the model's light. Null with the sun down or the shadows not worked out yet.
     */
    fun sunShares(): Map<String, Float>? {
        if (!sunUp()) return null
        val sh = renderer.shadowNow() ?: return null
        val t = scene.terrain
        return scene.runs.pistes.associate { p ->
            var n = 0; var lit = 0
            for (l in scene.draped[p.key].orEmpty()) for (i in 0 until l.size / 3) {
                val c = kotlin.math.round((l[i * 3] - t.x0) / t.sx).toInt().coerceIn(0, t.nx - 1)
                val r = kotlin.math.round((l[i * 3 + 2] - t.y0) / t.sy).toInt().coerceIn(0, t.ny - 1)
                n++; if (sh[r * t.nx + c] < .5f) lit++
            }
            p.key to if (n == 0) 0f else lit.toFloat() / n
        }
    }
    /**
     * K08: a lens on (worked out off the GL thread) or off. [runOpen] the "open" lens's rule for a run (LiftStatus), null
     * with no fresh report. In the sun with the sun down, and open with no report, draw nothing (the sheet says why).
     */
    internal fun setLens(kind: LensKind?, night: Boolean, runOpen: ((io.github.pini236.skiapp.data.Piste) -> Boolean?)?, snow: List<Pair<Float, Double>>? = null) {
        if (kind == null) { renderer.lens = null; requestRender(); return }
        if (worker.isShutdown) return
        worker.execute {
            val l = lensOf(kind, night, runOpen, snow)
            post { renderer.lens = l; requestRender(); Qa.log("redesign lens ${kind.id} ${if (l == null) "empty" else "on"}") }
        }
    }

    private fun lensOf(kind: LensKind, night: Boolean, runOpen: ((io.github.pini236.skiapp.data.Piste) -> Boolean?)?, snow: List<Pair<Float, Double>>?): LiveRenderer.Lens? {
        fun hex(h: Long) = floatArrayOf(((h shr 16) and 255) / 255f, ((h shr 8) and 255) / 255f, (h and 255) / 255f)
        fun col(c: String) = plate(c).let { floatArrayOf(it.red, it.green, it.blue) }
        val case = if (night) hex(0x081020) else floatArrayOf(1f, 1f, 1f)
        val t = scene.terrain
        val lines = HashMap<String, LiveRenderer.LensLine>()
        when (kind) {
            LensKind.SUN -> {
                if (!sunUp()) return null
                val sh = renderer.shadowNow() ?: return null
                val sun = hex(0xFFC93A); val shade = hex(0xB9CCFA); val halo = hex(0xFFE9A0).let { floatArrayOf(it[0], it[1], it[2], .35f) }
                for (p in scene.runs.pistes) {
                    val ls = scene.draped[p.key] ?: continue
                    var n = 0; var lit = 0
                    val r = io.github.pini236.skiapp.map.Ribbon.build(ls, { li, i ->
                        val l = ls[li]
                        val c = kotlin.math.round((l[i * 3] - t.x0) / t.sx).toInt().coerceIn(0, t.nx - 1)
                        val rr = kotlin.math.round((l[i * 3 + 2] - t.y0) / t.sy).toInt().coerceIn(0, t.ny - 1)
                        val inSun = sh[rr * t.nx + c] < .5f
                        n++; if (inSun) lit++
                        (if (inSun) sun else shade).let { floatArrayOf(it[0], it[1], it[2], if (inSun) 1f else .95f) }
                    })
                    val most = n > 0 && lit * 2 >= n
                    lines[p.key] = LiveRenderer.LensLine(r, null, if (most) 3f else 1.8f, 1f, most, if (most) halo else null)
                }
            }
            LensKind.OPEN -> {
                val rule = runOpen ?: return null
                val closed = if (night) hex(0x7C8798) else hex(0x8A93A3)
                for (p in scene.runs.pistes) lines[p.key] = when (LensRules.open(rule(p))) {
                    LensRules.Open.OPEN -> LiveRenderer.LensLine(null, col(p.color), 3f, 1f, true)
                    LensRules.Open.CLOSED -> scene.draped[p.key]?.let { ls ->
                        // its progress in metres along each line, for the dash
                        val r = io.github.pini236.skiapp.map.Ribbon.build(ls, { _, _ -> floatArrayOf(closed[0], closed[1], closed[2], 1f) }, { li, i ->
                            val l = ls[li]; var m = 0f
                            for (j in 1..i) m += hypot(l[j * 3] - l[j * 3 - 3], l[j * 3 + 2] - l[j * 3 - 1])
                            m
                        })
                        LiveRenderer.LensLine(r, null, 1.8f, 1f, false, dashed = true)
                    } ?: LiveRenderer.LensLine(null, closed, 1.8f, 1f, false)
                    LensRules.Open.UNKNOWN -> LiveRenderer.LensLine(null, col(p.color), 1.8f, .45f, false)
                }
            }
            LensKind.SLOPE -> for (p in scene.runs.pistes) {
                val ls = scene.draped[p.key] ?: continue
                val sl = ls.map { scene.lineSlopes(it) }
                val r = io.github.pini236.skiapp.map.Ribbon.build(ls, { li, i -> io.github.pini236.skiapp.map.SlopeColors.of(sl[li][i]).let { floatArrayOf(it[0], it[1], it[2], 1f) } })
                lines[p.key] = LiveRenderer.LensLine(r, null, 3f, 1f, true)
            }
            LensKind.SNOW -> {
                val pts = snow?.takeIf { it.isNotEmpty() } ?: return null
                val acc = if (night) hex(0xB79CFF) else hex(0x6B3FD4)
                val cls = listOf(hex(0x9AA7B8), hex(0x7FB2FF), acc)
                for (p in scene.runs.pistes) {
                    val ls = scene.draped[p.key] ?: continue
                    var most = 0
                    val r = io.github.pini236.skiapp.map.Ribbon.build(ls, { li, i ->
                        val c = LensRules.snowClass(LensRules.snowAt(ls[li][i * 3 + 1], pts)); if (c > most) most = c
                        cls[c].let { floatArrayOf(it[0], it[1], it[2], 1f) }
                    })
                    lines[p.key] = when (most) {
                        2 -> LiveRenderer.LensLine(r, null, 3.4f, 1f, true, floatArrayOf(acc[0], acc[1], acc[2], .3f))
                        1 -> LiveRenderer.LensLine(r, null, 3f, 1f, true)
                        else -> LiveRenderer.LensLine(r, null, 1.8f, 1f, false)
                    }
                }
            }
        }
        return LiveRenderer.Lens(lines, case)
    }

    /** The light of a moment: the shader's uniforms at once, the shadows a moment later (worked out off the GL thread). */
    fun setTime(epochMs: Long) {
        lastEpoch = epochMs
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
        lightDir = l.dir
        worker.execute {
            if (lightKey == key) renderer.setShadow(LiveLight.soften(Sun.shadows(t, l.dir), t.nx, t.ny))
            // the far ground's shadow, with the mountain's (the architect: off the drawing thread, 150 ms)
            if (lightKey == key) ring?.let { ringShadow(it, l.dir) }
        }
    }

    @Volatile private var ring: LiveRing? = null
    @Volatile private var lightDir: FloatArray? = null

    /** The ring's cast shadow for the sun in [dir] (on the worker), logged with its time for the emulator. */
    private fun ringShadow(r: LiveRing, dir: FloatArray) {
        val sh = r.shadows(dir)
        renderer.setRingShadow(sh)
        Qa.log("redesign ring shadow ${r.lastShadowMs} ms${if (r.outerShadowOff) " outer off" else ""}")
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
    /** The shelf's and the station's light (K06, K07), or null. */
    fun setFocus(f: LiveRenderer.Focus?) { renderer.focus = f; requestRender() }
    /** The mountain raised on the screen by [px] (K06, K07: 120 points), the view's direction and distance kept. */
    /** The emulator's switch for the air of height (qa.redesign air:off / air:on), a picture of the opening before and after. */
    fun showAir(on: Boolean) { renderer.airOn = on; requestRender(); Qa.log("redesign air ${if (on) "on" else "off"}") }

    /** The emulator's switch for the far ground (qa.redesign ring:off / ring:on), to measure the frames with and without it. */
    fun showRing(on: Boolean) { renderer.ringShown = on; requestRender(); Qa.log("redesign ring ${if (on) "on" else "off"}") }
    fun setShift(px: Float) { if (renderer.shiftPx != px) { renderer.shiftPx = px; requestRender() } }
    val shiftPx: Float get() = renderer.shiftPx
    /** The runs and lifts quiet ([a] of their opacity) under the meeting point. */
    fun setQuiet(a: Float, runs: Float = 1f) { if (renderer.linesQuiet != a || renderer.runsQuiet != runs) { renderer.linesQuiet = a; renderer.runsQuiet = runs; requestRender() } }
    fun choose(key: String?) {
        renderer.frozenMs = null; renderer.chosen = key; chosenDown = null; requestRender()
        // the slope colours around it, as the map's (X-3), worked out off the GL thread
        if (key == null) { renderer.ground = null; renderer.chosenLine = null; renderer.frozenMs = null; return }
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
            val main = down.indices.maxByOrNull { along[it].lastOrNull() ?: 0f } ?: 0
            if (renderer.chosen == key) {
                chosenDown = Down(key, down, sl, along, main)
                renderer.ground = g; renderer.chosenLine = key to line; renderer.revealFrom = System.currentTimeMillis(); requestRender()
                post { onLineReady?.invoke(key) }
            }
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

    private fun move(f: (OrbitCamera.State) -> OrbitCamera.State) { camera.update { LiveLimits.step(it, f(it)) }; requestRender() }

    /**
     * The film (K12): while set, the camera's gestures are off and a finger goes here instead: 0 down, 1 move, 2 up
     * (or cancelled), with its place on the screen. The film decides what a drag on the mountain does.
     */
    var filmTouch: ((Int, Float, Float) -> Unit)? = null

    /** The live model cannot draw here (a texture did not load): the screen goes back to the painted mountain. */
    var onFail: (() -> Unit)? = null

    /**
     * The film's camera (K12, as today's flight down): on the point [x], [h], [y] of the line, 520 away, tilted 27.5
     * degrees, facing [yaw]; the point in the middle of the screen's top [free] part (the sheet takes the rest).
     */
    fun filmCamera(x: Float, h: Float, y: Float, yaw: Float, free: Float) {
        // higher and further behind than 520 m at .48: by day the lit line stayed hidden under the slope (review of 24.11, ח4)
        val dist = 760f
        val need = filmPitchFor(x, h, y, yaw, free, dist)
        // up at once (never a frame in the slope), down slowly (no jump when the slope behind eases)
        filmPitch = if (need >= filmPitch || filmPitch.isNaN()) need else filmPitch + (need - filmPitch) * .06f
        renderer.stopAnim(); camera.stopFling()
        camera.set(filmState(x, h, y, yaw, free, dist, filmPitch)); requestRender()
    }

    private var filmPitch = Float.NaN
    private var filmLow = 0

    private fun filmState(x: Float, h: Float, y: Float, yaw: Float, free: Float, dist: Float, pitch: Float): OrbitCamera.State {
        val vf = tan(Math.toRadians(data.camera.fovDeg / 2.0)).toFloat()
        val dy = (1f - free) * dist * vf / kotlin.math.cos(pitch)
        return OrbitCamera.State(x, h - dy, y, dist, yaw, pitch)
    }

    /**
     * The least tilt, from .72 up, at which the camera stands over the snow and sees the dot: on a steep run the slope
     * behind the dot (where the camera stands) is higher than a camera at .72, and the screen was all snow (Pini,
     * 25.11.2026, Kikilo 1). The eye 60 m over the snow, and the sight down to the dot 15 m over it.
     */
    private fun filmPitchFor(x: Float, h: Float, y: Float, yaw: Float, free: Float, dist: Float): Float {
        val t = scene.terrain; val e = FloatArray(3)
        var pitch = .72f
        while (pitch < 1.35f) {
            OrbitCamera.eye(filmState(x, h, y, yaw, free, dist, pitch), e)
            var clear = e[1] >= t.elev(e[0], e[2]) + 60f
            var k = 1
            while (clear && k < 10) {
                val f = k / 10f; val px = e[0] + (x - e[0]) * f; val py = e[1] + (h - e[1]) * f; val pz = e[2] + (y - e[2]) * f
                if (py < t.elev(px, pz) + 15f * (1 - f) + 2f) clear = false
                k++
            }
            if (clear) break
            pitch += .04f
        }
        if (pitch > .73f) { if (filmLow++ % 60 == 0) Qa.log("redesign ride camera up ${"%.2f".format(pitch)}") }
        return pitch
    }

    /** The film's camera is over the snow (the emulator's check): its eye over the ground and the dot in sight. */
    fun filmClear(x: Float, h: Float, y: Float): Boolean = clearFrom(camera.state(), x, h, y)

    /** The camera in words, for the emulator's log (distance, tilt, target). */
    fun cameraWords(): String = camera.state().let { "cam ${it.dist.roundToInt()} ${"%.2f".format(it.pitch)} ${it.tx.roundToInt()},${it.tz.roundToInt()}" }

    /** Whether the film's camera would be over the snow at [x], [h], [y] facing [yaw] (the emulator's check of every run). */
    fun filmClearAt(x: Float, h: Float, y: Float, yaw: Float, free: Float): Boolean {
        val dist = 760f
        return clearFrom(filmState(x, h, y, yaw, free, dist, filmPitchFor(x, h, y, yaw, free, dist)), x, h, y)
    }

    private fun clearFrom(s: OrbitCamera.State, x: Float, h: Float, y: Float): Boolean {
        val e = FloatArray(3); OrbitCamera.eye(s, e)
        if (e[1] < scene.terrain.elev(e[0], e[2]) + 20f) return false
        for (k in 1 until 10) {
            val f = k / 10f
            if (e[1] + (h - e[1]) * f < scene.terrain.elev(e[0] + (x - e[0]) * f, e[2] + (y - e[2]) * f) - 2f) return false
        }
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        filmTouch?.let { f ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> f(0, e.x, e.y)
                MotionEvent.ACTION_MOVE -> f(1, e.x, e.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> f(2, e.x, e.y)
            }
            return true
        }
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
                    camera.state().let { s0 -> renderer.animate(LiveLimits.step(s0, Moves.zoom(s0, w, h, lx(mx), ly(my), 0.5f, snow)), 0.35f) }
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
            camera.state().let { s0 -> renderer.animate(LiveLimits.step(s0, Moves.zoom(s0, w, h, lx(x), ly(y), 2.2f, snow)), 0.35f) }
            return
        }
        lastTapT = t; tapX = x; tapY = y
        removeCallbacks(pendingPick); postDelayed(pendingPick, doubleTapMs)
    }

    /** The named run whose line passes closest to the finger, within 28 dp, in this frame's projection. */
    private fun pick(px: Float, py: Float) {
        // the day on the mountain (round 4) has no runs to choose: a tap is the screen's (the film stops on it)
        onTap?.let { it(px, py); return }
        if (onPreTap?.invoke(px, py) == true) return
        val mvp = FloatArray(16); val eye = FloatArray(3); val v = FloatArray(4); val o = FloatArray(4)
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> scene.terrain.elev(x, z) }; shiftUp(mvp, renderer.shiftPx, height)
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
    private fun front() = data.camera.let { c -> OrbitCamera.State(c.tx, scene.terrain.elev(c.tx, c.tz), c.tz, c.dist * LiveOpen.DIST, LiveOpen.YAW, Math.toRadians(LiveOpen.PITCH_DEG.toDouble()).toFloat()) }
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

    /**
     * The shelf's middle card (K07, the live handoff's section 10): a run that shows less than half from here turns the
     * view to the wide view of the side that shows more of it; nothing is chosen and nothing is framed.
     */
    /**
     * The shelf's middle card shown on the mountain (Pini, 24.11.2026: "the card should mark the run and turn the view
     * to it"): the side that shows more of it if this one shows less than half, then the run fitted to the screen's
     * top [free] part, over the shelf. Nothing is chosen.
     */
    fun showRun(key: String, top: Float, bottom: Float, shift: Float) {
        if (worker.isShutdown) return
        worker.execute {
            val here = seen(key, camera.state())
            val box = keysBox(setOf(key)) ?: return@execute
            if (here >= .5f) { post { frameIn(box, top, bottom, shift, "redesign shelf framed $key") }; return@execute }
            val f = front(); val k = kobi()
            val wide = if (seen(key, k) > seen(key, f)) k else f
            Qa.log("redesign shelf side ${if (wide === k) "kobi" else "front"} $key")
            post { turnTo(wide) { frameIn(box, top, bottom, shift, "redesign shelf framed $key") } }
        }
    }

    /** The runs [keys] (the station's, K06) fitted between [top] and [bottom] of the screen, the mountain raised [shift]. */
    fun showKeys(keys: Set<String>, top: Float, bottom: Float, shift: Float, log: String) {
        val box = keysBox(keys) ?: return
        frameIn(box, top, bottom, shift, log)
    }

    /** The box (x0, z0, x1, z1) of the lines of the runs [keys], or null when none has a line. */
    private fun keysBox(keys: Set<String>): FloatArray? {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (key in keys) for (l in scene.draped[key].orEmpty()) for (i in 0 until l.size / 3) {
            x0 = minOf(x0, l[i * 3]); x1 = maxOf(x1, l[i * 3]); z0 = minOf(z0, l[i * 3 + 2]); z1 = maxOf(z1, l[i * 3 + 2])
        }
        return if (x0 > x1) null else floatArrayOf(x0, z0, x1, z1)
    }

    fun showSide(key: String) {
        if (worker.isShutdown) return
        worker.execute {
            if (seen(key, camera.state()) >= .5f) return@execute
            val f = front(); val k = kobi()
            val wide = if (seen(key, k) > seen(key, f)) k else f
            Qa.log("redesign shelf side ${if (wide === k) "kobi" else "front"} $key")
            post { turnTo(wide) }
        }
    }

    private var beforeVillage: OrbitCamera.State? = null

    /** The view now, to come back to when a layer that moves the camera (the shelf, the station) closes. */
    fun viewNow(): OrbitCamera.State = camera.state().also { Qa.log("redesign layer from ${pose(it)}") }
    private fun pose(e: OrbitCamera.State) = "dist ${"%.0f".format(e.dist)} pitch ${"%.1f".format(Math.toDegrees(e.pitch.toDouble()))} yaw ${"%.1f".format(Math.toDegrees(e.yaw.toDouble()))}"

    /** Back to [s] (the view before the shelf or the station), 500 ms, or at once with reduced motion; logs [log]. */
    fun backTo(s: OrbitCamera.State, log: String) {
        renderer.stopAnim(); camera.stopFling()
        // the log with the view it ends at and the names' drift, for the emulator (qa/run.sh)
        val done = { postDelayed({ Qa.log("$log ${pose(camera.state())} drift ${"%.0f".format(overlayDrift())}") }, 400) }
        if (Motion.reduced(context)) { camera.set(s); requestRender(); done() }
        else { renderer.animate(s, .5f) { post { done() } }; requestRender() }
    }

    /** The emulator's far view (qa.redesign far): the opening view at the farthest the pinch goes, set at once. */
    fun qaFar() {
        renderer.stopAnim(); camera.stopFling()
        camera.set(front().copy(dist = LiveLimits.MAX_DIST)); requestRender()
        Qa.log("redesign live far ${pose(camera.state())}")
    }

    /**
     * K17's flight to the village picture's view ([pose]: tx, ty, tz, dist, yaw and pitch in degrees), 600 ms slowing at
     * the end, remembering the view to come back to. False, with no flight, from the Kobi side (the village is behind the
     * ridge) or with reduced motion: the picture then swaps in by opacity.
     */
    fun toVillage(pose: FloatArray): Boolean {
        beforeVillage = null
        if (onKobiSide() || Motion.reduced(context)) return false
        renderer.stopAnim(); camera.stopFling()
        val y0 = yawNow(); camera.update { it.copy(yaw = y0) }
        beforeVillage = camera.state()
        val r = { d: Float -> Math.toRadians(d.toDouble()).toFloat() }
        renderer.animate(OrbitCamera.State(pose[0], pose[1], pose[2], pose[3], r(pose[4]), r(pose[5])), .6f) { post { Qa.log("redesign village flown") } }
        requestRender(); return true
    }

    /** Back from the village to the view that was before it (not the opening view), 500 ms; nothing when it didn't fly. */
    fun fromVillage() {
        val b = beforeVillage ?: return
        beforeVillage = null
        renderer.stopAnim(); camera.stopFling()
        renderer.animate(b, .5f) { post { Qa.log("redesign village back") } }
        requestRender()
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
    /** The chosen run's lines from the top down, the slope at each point, the metres along each, and the longest. */
    class Down(val key: String, val lines: List<FloatArray>, val slopes: List<FloatArray>, val along: List<FloatArray>, val main: Int)
    @Volatile private var chosenDown: Down? = null
    /** Called (on the main thread) once a chosen run's line is built and its light starts down it. */
    var onLineReady: ((String) -> Unit)? = null

    /** A chosen run on the screen now, for K11 over the model: per line, x and y in pixels, seen or not (a ridge, behind). */
    class Marks(val key: String, val xs: List<FloatArray>, val ys: List<FloatArray>, val seen: List<BooleanArray>, val down: Down)
    private var lastMk: Marks? = null; private var lastMkAt: OrbitCamera.State? = null
    fun marks(): Marks? {
        val d = chosenDown ?: return null
        if (d.key != renderer.chosen || width == 0) return null
        val cs = camera.state()
        lastMk?.let { if (it.key == d.key && cs == lastMkAt) return it }
        val t = scene.terrain
        val mvp = FloatArray(16); val eye = FloatArray(3); val o = FloatArray(4); val v = FloatArray(4)
        OrbitCamera.mvp(cs, width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> t.elev(x, z) }; shiftUp(mvp, renderer.shiftPx, height)
        val xs = ArrayList<FloatArray>(); val ys = ArrayList<FloatArray>(); val sn = ArrayList<BooleanArray>()
        for (l in d.lines) {
            val n = l.size / 3; val x = FloatArray(n); val y = FloatArray(n); val ok = BooleanArray(n)
            for (i in 0 until n) {
                v[0] = l[i * 3]; v[1] = l[i * 3 + 1] + 4f; v[2] = l[i * 3 + 2]; v[3] = 1f
                Matrix.multiplyMV(o, 0, mvp, 0, v, 0)
                if (o[3] <= 0f) continue
                x[i] = (o[0] / o[3] * .5f + .5f) * width; y[i] = (1 - (o[1] / o[3] * .5f + .5f)) * height
                // behind a ridge (the map's labels' test): the mountain more than 8 m above the line from the eye
                val dx = v[0] - eye[0]; val dy = v[1] - eye[1]; val dz = v[2] - eye[2]
                var seen = true
                for (k in 4 until 32) { val f = k / 32f; if (t.elev(eye[0] + dx * f, eye[2] + dz * f) > eye[1] + dy * f + 8f) { seen = false; break } }
                ok[i] = seen
            }
            xs += x; ys += y; sn += ok
        }
        return Marks(d.key, xs, ys, sn, d).also { lastMk = it; lastMkAt = cs }
    }
    /** Ms since the chosen run's light started down it (the held moment in the emulator). */
    fun chosenMs(): Float = renderer.chosenMs()

    /**
     * The emulator's held moment of the choice (the designer's frames at 0, 150, 300, 600 and 900 ms, as phase A's
     * frame:): the run chosen, then the time held at [ms], the camera where the framing would be then. The ring at
     * [onMoment]'s point.
     */
    var onMoment: ((Float, Float, Float) -> Unit)? = null
    fun moment(key: String, ms: Float) {
        renderer.stopAnim(); camera.stopFling()
        val start = camera.state()
        choose(key); renderer.frozenMs = ms; onChoose?.invoke(key)
        val l = onLineReady
        onLineReady = { k ->
            onLineReady = l
            if (k == key) {
                val to = frameTarget(key)
                if (to != null) {
                    val e0 = ((ms - 300f) / 600f).coerceIn(0f, 1f); val e = e0 * e0 * (3 - 2 * e0)
                    fun f(a: Float, b: Float) = a + (b - a) * e
                    camera.set(OrbitCamera.State(f(start.tx, to.tx), f(start.ty, to.ty), f(start.tz, to.tz), f(start.dist, to.dist), start.yaw, start.pitch))
                }
                // the ring where the finger would have been: the middle of the run as the screen showed it at the touch
                val d = chosenDown
                val mid = d?.lines?.get(d.main)?.let { ln -> val i = ln.size / 3 / 2; floatArrayOf(ln[i * 3], ln[i * 3 + 1], ln[i * 3 + 2]) }
                val p = mid?.let { m -> project(start, m) }
                if (p != null) onMoment?.invoke(p[0], p[1], ms)
                requestRender()
                // the surface may be coming back from the emulator's command at that moment: ask again until it is
                for (t in listOf(250L, 750L, 1500L, 3000L)) postDelayed({ if (renderer.frozenMs == ms) requestRender() }, t)
                post { Qa.log("redesign live moment $key ${ms.toInt()}") }
            }
        }
    }
    private fun project(s: OrbitCamera.State, w: FloatArray): FloatArray? {
        val mvp = FloatArray(16); val eye = FloatArray(3); val o = FloatArray(4)
        OrbitCamera.mvp(s, width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> scene.terrain.elev(x, z) }; shiftUp(mvp, renderer.shiftPx, height)
        Matrix.multiplyMV(o, 0, mvp, 0, floatArrayOf(w[0], w[1], w[2], 1f), 0)
        if (o[3] <= 0f) return null
        return floatArrayOf((o[0] / o[3] * .5f + .5f) * width, (1 - (o[1] / o[3] * .5f + .5f)) * height)
    }

    /** Where the framing of a run lands (section 6): its box in the frame, the yaw and pitch kept. */
    private fun frameTarget(key: String): OrbitCamera.State? {
        val lines = scene.draped[key] ?: return null
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (l in lines) for (i in 0 until l.size / 3) { x0 = minOf(x0, l[i * 3]); x1 = maxOf(x1, l[i * 3]); z0 = minOf(z0, l[i * 3 + 2]); z1 = maxOf(z1, l[i * 3 + 2]) }
        val cx = (x0 + x1) / 2; val cz = (z0 + z1) / 2
        val r = hypot(x1 - x0, z1 - z0) / 2
        val vf = Math.toRadians(data.camera.fovDeg / 2.0)
        val hf = atan(tan(vf) * width.toFloat() / height.coerceAtLeast(1))
        val dist = (r / tan(minOf(vf, hf)) * 1.15).toFloat().coerceIn(1400f, 9000f)
        val s = camera.state()
        return s.copy(tx = cx, ty = scene.terrain.elev(cx, cz), tz = cz, dist = dist)
    }

    /**
     * A chosen run in the middle of the screen above its sheet, all of it in view (the live handoff's section 6; Pini,
     * 24.11.2026: Kikilo 1 cut in a corner under the sheet, the view on the sky): its box fitted to the top part the
     * low sheet leaves, looking down on it at least [LiveLimits.FRAME_PITCH].
     */
    private fun frameRun(key: String) {
        val lines = scene.draped[key] ?: return
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (l in lines) for (i in 0 until l.size / 3) { x0 = minOf(x0, l[i * 3]); x1 = maxOf(x1, l[i * 3]); z0 = minOf(z0, l[i * 3 + 2]); z1 = maxOf(z1, l[i * 3 + 2]) }
        frameLift(floatArrayOf(x0, z0, x1, z1), LiveLimits.CHOSEN_FREE, "redesign live framed $key")
    }

    /**
     * Round 4 (board L06): the day on the mountain. The lines draped on the snow (3 m up, as the map's), the rides'
     * chords 8 m above their ends (day4.py's lift=8), built here off the GL thread; the view framed on the day from the
     * front's side. Null takes it away.
     */
    fun showDay(d: DayPaint.Day?, night: Boolean, frame: Boolean = true, withRuns: Boolean = false) {
        if (worker.isShutdown) return
        if (d == null) { renderer.day = null; requestRender(); return }
        worker.execute {
            val t = scene.terrain
            val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
            val world = d.lines.map { l -> val n = l.xy.size / 2; FloatArray(n * 3) { k -> val i = k / 3
                when (k % 3) { 0 -> l.xy[i * 2]; 1 -> t.elev(l.xy[i * 2], l.xy[i * 2 + 1]) + 3f; else -> l.xy[i * 2 + 1] } } }
            // each point's progress is its time in the day (0 to 1): the film of the day (board L08) reveals up to its clock
            val span = (d.t1 - d.t0).coerceAtLeast(1L).toFloat()
            fun prog(time: Long) = (time - d.t0) / span
            val light = io.github.pini236.skiapp.map.Ribbon.build(world, { li, pi -> val c = ramp[d.lines[li].band[pi]]
                floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f, 1f) }, { li, pi -> prog(d.lines[li].t[pi]) })
            val dots = ArrayList<FloatArray>(); val dotT = ArrayList<FloatArray>()
            for ((ci, c) in d.lifts.withIndex()) {
                val e0 = t.elev(c[0], c[1]) + 8f; val e1 = t.elev(c[2], c[3]) + 8f
                val len = hypot(c[2] - c[0], c[3] - c[1]).coerceAtLeast(1f)
                val lt = d.liftT.getOrNull(ci)
                for (q in DayPaint.dots(c)) {
                    val f0 = hypot(q[0] - c[0], q[1] - c[1]) / len; val f1 = hypot(q[2] - c[0], q[3] - c[1]) / len
                    dots += floatArrayOf(q[0], e0 + (e1 - e0) * f0, q[1], q[2], e0 + (e1 - e0) * f1, q[3])
                    dotT += if (lt == null) floatArrayOf(0f, 0f) else floatArrayOf(prog(lt[0] + ((lt[1] - lt[0]) * f0).toLong()), prog(lt[0] + ((lt[1] - lt[0]) * f1).toLong()))
                }
            }
            val lifts = io.github.pini236.skiapp.map.Ribbon.build(dots, { _, _ -> floatArrayOf(1f, 1f, 1f, 1f) }, { li, pi -> dotT[li][pi] })
            val to = if (frame) DayPaint.box(d)?.let { frameBox(it, front()) } else null
            post {
                renderer.day = LiveRenderer.DayLines(light, lifts, night, withRuns)
                if (to != null) { renderer.stopAnim(); camera.stopFling(); camera.set(to) }
                requestRender()
                Qa.log("redesign live day ${d.points} ${d.lifts.size} ${if (night) "night" else "day"}")
            }
        }
    }

    /** The film of the day (board L08): how far the day is drawn, 0 to 1 of its time; null draws all of it. */
    fun dayReveal(f: Float?) { renderer.dayReveal = f ?: 60000f; requestRender() }

    /**
     * A lift framed whole over its panel (Q03, handoff section 14.6): the box of its two ends with a margin, fitted to
     * the screen's top [free] (the sheet takes the rest), the target lowered so the box sits in the middle of that part.
     */
    fun frameLift(ends: FloatArray, free: Float = .54f, log: String = "redesign live lift framed") = frameIn(ends, 0f, free, 0f, log)

    /**
     * A box of the map (x0, z0, x1, z1) fitted to the screen's part from [top] to [bottom] (fractions of its height) and
     * in its middle, with the mountain raised [shift] px on the screen (the shelf, the station: K06, K07). The review of
     * 24.11: the shelf's run landed under the pill, the raise not counted.
     */
    fun frameIn(ends: FloatArray, top: Float, bottom: Float, shift: Float, log: String) {
        val pad = 260f
        val b = floatArrayOf(minOf(ends[0], ends[2]) - pad, minOf(ends[1], ends[3]) - pad, maxOf(ends[0], ends[2]) + pad, maxOf(ends[1], ends[3]) + pad)
        // looking down on it, never along the snow at the sky
        val s0 = camera.state().let { it.copy(pitch = it.pitch.coerceAtLeast(LiveLimits.FRAME_PITCH)) }
        val fit = frameBox(b, s0)
        val vf = tan(Math.toRadians(data.camera.fovDeg / 2.0)).toFloat()
        val dist = (fit.dist / (bottom - top).coerceIn(.2f, 1f)).coerceIn(1400f, LiveLimits.MAX_DIST)
        // the part's middle, where the box goes, in the view before the raise; up from the screen's middle by
        // (1 - 2c) of the half height: the target lowered along the screen's up
        val c = (top + bottom) / 2f + shift / height.coerceAtLeast(1)
        val dy = (1f - 2f * c) * dist * vf / kotlin.math.cos(s0.pitch).coerceAtLeast(.3f)
        val to = fit.copy(dist = dist, ty = fit.ty - dy)
        renderer.stopAnim(); camera.stopFling()
        // its end logged: on the emulator a frame takes a second, and the glide of 0.6 s (in steps of 0.1) takes several
        if (Motion.reduced(context)) { camera.set(to); requestRender(); Qa.log("redesign frame done") }
        else renderer.animate(to, 0.6f) { Qa.log("redesign frame done") }
        Qa.log(log); Qa.log("redesign frame to ${to.dist.roundToInt()} ${"%.2f".format(to.pitch)} free ${"%.2f".format(bottom)}")
    }

    /**
     * A chosen run framed over the full sheet (S07, the review of 24.11): its box fitted to the screen's top [free] part,
     * as a lift over its panel; [free] null frames it as at the choice (the low sheet).
     */
    fun frameRunAbove(key: String, free: Float?) {
        if (free == null) { frameRun(key); return }
        val lines = scene.draped[key] ?: return
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (l in lines) for (i in 0 until l.size / 3) { x0 = minOf(x0, l[i * 3]); x1 = maxOf(x1, l[i * 3]); z0 = minOf(z0, l[i * 3 + 2]); z1 = maxOf(z1, l[i * 3 + 2]) }
        frameLift(floatArrayOf(x0, z0, x1, z1), free)
        Qa.log("redesign live framed above $key")
    }

    /** The view [s] moved to fit a box of the map (x0, y0, x1, y1, metres) with a margin; its yaw and pitch kept. */
    private fun frameBox(b: FloatArray, s: OrbitCamera.State): OrbitCamera.State {
        val cx = (b[0] + b[2]) / 2; val cz = (b[1] + b[3]) / 2
        val r = hypot(b[2] - b[0], b[3] - b[1]) / 2
        val vf = Math.toRadians(data.camera.fovDeg / 2.0)
        val hf = atan(tan(vf) * width.toFloat().coerceAtLeast(1f) / height.coerceAtLeast(1))
        val dist = (r / tan(minOf(vf, hf)) * 1.1).toFloat().coerceIn(1400f, LiveLimits.MAX_DIST)
        return s.copy(tx = cx, ty = scene.terrain.elev(cx, cz), tz = cz, dist = dist)
    }

    /**
     * The live screen (board L03): the view moves with you, the front's yaw and pitch, the place [x], [y] (metres) in
     * the middle at [dist]; a short glide (or a jump with reduced motion). Only when you moved more than [min] metres.
     */
    fun follow(x: Float, y: Float, dist: Float = 2600f, min: Float = 120f) {
        val s = camera.state()
        if (hypot(s.tx - x, s.tz - y) < min && kotlin.math.abs(s.dist - dist) < 1f) return
        val to = front().copy(tx = x, ty = scene.terrain.elev(x, y), tz = y, dist = dist)
        if (Motion.reduced(context)) { camera.set(to); requestRender() } else renderer.animate(to, 0.8f)
    }

    /**
     * "Where am I" switched on (part 4, section 3): the place [x], [y] brought to the middle when it is off the screen
     * or within [edge] px of its edge; the direction, height and distance kept; 450 ms, or a jump with reduced motion.
     */
    fun center(x: Float, y: Float, edge: Float) {
        val p = toScreen(x, y)
        if (p != null && p[0] in edge..(width - edge) && p[1] in edge..(height - edge)) return
        val to = camera.state().copy(tx = x, ty = scene.terrain.elev(x, y), tz = y)
        renderer.stopAnim(); camera.stopFling()
        if (Motion.reduced(context)) { camera.set(to); requestRender() } else renderer.animate(to, 0.45f)
        Qa.log("redesign where centred")
    }

    /** The compass (part 4, section 6): the view is not the opening one (direction or tilt past 3 degrees, or distance). */
    fun awayFromHome(): Boolean {
        return WhereRules.away(camera.state(), front())
    }

    /** Where north is on the screen, degrees clockwise from up (the compass's needle). */
    fun northDeg(): Float {
        val c = camera.state()
        val a = toScreen(c.tx, c.tz) ?: return 0f
        val b = toScreen(c.tx, c.tz - 400f) ?: return 0f // north is up the map's y (data/Geo.kt)
        return Math.toDegrees(atan2((b[0] - a[0]).toDouble(), (a[1] - b[1]).toDouble())).toFloat()
    }

    /** Whether a frame with the mountain has been drawn (the first hint waits for it). */
    val drawn: Boolean get() = renderer.drawn

    /** The compass's tap: back to the opening view in one move, 600 ms (a jump with reduced motion). */
    fun home() {
        renderer.stopAnim(); camera.stopFling()
        if (Motion.reduced(context)) { camera.set(front()); requestRender() } else renderer.animate(front(), 0.6f)
        Qa.log("redesign live home")
    }

    /** Where a place on the map (metres, [up] above the snow) is on the screen now, or null behind the camera. */
    fun toScreen(x: Float, y: Float, up: Float = 0f): FloatArray? =
        if (width == 0) null else project(camera.state(), floatArrayOf(x, scene.terrain.elev(x, y) + up, y))

    /**
     * The emulator's touch on the live model (qa.redesign spot:<run>): a place on the screen where [key]'s line is drawn
     * now, seen from the eye, off the screen's edges and its bars, and as far as can be from any other named run's line,
     * so a real tap there chooses [key] the way [pick] chooses (28 dp). Null when no such place is on the screen.
     */
    fun spotOf(key: String): FloatArray? {
        if (width == 0) return null
        val mvp = FloatArray(16); val eye = FloatArray(3); val v = FloatArray(4); val o = FloatArray(4)
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, data.camera.fovDeg) { x, z -> scene.terrain.elev(x, z) }; shiftUp(mvp, renderer.shiftPx, height)
        fun on(x: Float, y: Float, z: Float): FloatArray? {
            v[0] = x; v[1] = y; v[2] = z; v[3] = 1f; Matrix.multiplyMV(o, 0, mvp, 0, v, 0)
            if (o[3] <= 0f) return null
            return floatArrayOf((o[0] / o[3] * .5f + .5f) * width, (1 - (o[1] / o[3] * .5f + .5f)) * height)
        }
        fun seen(x: Float, y: Float, z: Float): Boolean {
            val dx = x - eye[0]; val dy = y - eye[1]; val dz = z - eye[2]
            for (i in 4 until 40) { val f = i / 40f; if (scene.terrain.elev(eye[0] + dx * f, eye[2] + dz * f) > eye[1] + dy * f + 8f) return false }
            return true
        }
        val others = ArrayList<Float>()
        for (p in scene.runs.pistes) if (p.named && p.key != key) for (l in scene.draped[p.key] ?: emptyList())
            for (i in 0 until l.size / 3) on(l[i * 3], l[i * 3 + 1], l[i * 3 + 2])?.let { others += it[0]; others += it[1] }
        val m = 48 * density
        var best: FloatArray? = null; var bestD = 28 * density
        for (l in scene.draped[key] ?: return null) for (i in 0 until l.size / 3) {
            val x = l[i * 3]; val y = l[i * 3 + 1]; val z = l[i * 3 + 2]
            val s = on(x, y, z) ?: continue
            if (s[0] < m || s[0] > width - m || s[1] < height * .18f || s[1] > height * .62f || !seen(x, y, z)) continue
            var d = Float.MAX_VALUE; var j = 0
            while (j < others.size) { d = minOf(d, hypot(others[j] - s[0], others[j + 1] - s[1])); j += 2 }
            if (d > bestD) { bestD = d; best = s }
        }
        return best
    }

    /** A run chosen as a tap chooses it (the emulator's pictures). */
    fun pickKey(key: String) { renderer.frozenMs = null; choose(key); onChoose?.invoke(key); frame(key) }

    /** The "Kobi side" sign: back to the front, the same turn the other way. */
    fun backToFront() { turnTo(front()); Qa.log("redesign live front") }
    /** The emulator's turn (qa.redesign turn:<degrees>): the opening view turned round its target, set at once. */
    fun qaTurn(deg: Float) {
        renderer.stopAnim(); camera.stopFling()
        val f = front(); camera.set(f.copy(yaw = f.yaw + Math.toRadians(deg.toDouble()).toFloat())); requestRender()
        Qa.log("redesign live turned $deg")
    }
    /** To the Kobi side (board L06's tag "more runs on the Kobi side"): the same turn as a run chosen there. */
    fun toKobi() { turnTo(kobi()); Qa.log("redesign live kobi") }

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
object LiveOpen {
    const val YAW = 0f
    // closer and more from above than the handoff's camera (6,617 m at 16°): on the phone the runs filled the middle
    // third only and were too thin to touch; S01 has them from the pill to the dock (the review of 24.11, ש1)
    const val DIST = 0.82f
    const val PITCH_DEG = 30f
}


/**
 * The live model's own bounds on a gesture (Pini, 24.11.2026): not so far that the model floats small on an empty sky
 * (the map's 32 km), not so close that the 40 m ground breaks up, and no jump: one finger step never carries the view's
 * centre further than its own distance (holding the snow under the finger can catch a point behind a ridge when close;
 * then the step keeps the zoom and the turn, without the jump).
 */
internal object LiveLimits {
    const val MIN_DIST = 600f
    /** 10 km (the architect, 25.11.2026: never under 7.5; the opening is 6.6, the Kobi side 5.7), with the far ground round it. */
    const val MAX_DIST = 10000f
    /** The screen's top part a chosen run is framed in (the low sheet takes the rest), and the least tilt it is seen at. */
    const val CHOSEN_FREE = .6f
    const val FRAME_PITCH = .5f
    fun step(from: OrbitCamera.State, to: OrbitCamera.State): OrbitCamera.State {
        val d = to.dist.coerceIn(MIN_DIST, MAX_DIST)
        val jump = hypot(to.tx - from.tx, to.tz - from.tz) > maxOf(from.dist, d)
        return if (jump) from.copy(dist = d, yaw = to.yaw, pitch = to.pitch) else to.copy(dist = d)
    }
}

package io.github.pini236.skiapp.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.perf.FrameStats
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import java.text.NumberFormat
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** GL surface that asks for the screen's top refresh rate and 4x anti-aliasing when it can get it. */
@SuppressLint("ViewConstructor")
private class MountainSurface(context: Context, private val refreshHz: Float) : GLSurfaceView(context) {
    var msaa = false
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
                pick(base + intArrayOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE))?.let { msaa = true; return it }
                pick(base + intArrayOf(EGL10.EGL_NONE))?.let { return it }
                return pick(intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, es3, EGL10.EGL_NONE))!!
            }
        })
        preserveEGLContextOnPause = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        if (Build.VERSION.SDK_INT >= 30) holder.surface.setFrameRate(refreshHz, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
    }
}

/** A label on the mountain: Hebrew, Latin names and numbers mixed, laid out natively (bidi) once and drawn every frame. */
private class MapLabel(
    val x: Float, val y: Float, val z: Float, val layout: StaticLayout, val priority: Int, val dark: Boolean, val lift: Boolean = false,
    /** A run's label: its key (hidden with the run by the filters, and alone while a run is chosen). */
    val run: String? = null,
)

private class LabelOverlay(context: Context, private val camera: OrbitCamera) : View(context) {
    var labels: List<MapLabel> = emptyList()
    /** The lifts filtered out (the list's "lifts"): their labels go with their lines. */
    var hideLifts = false
    /** The runs filtered out, and the chosen run: then only its label of all the runs' (the site's layoutLabels). */
    var hiddenRuns: Set<String> = emptySet()
    var chosenRun: String? = null
    var ground: ((Float, Float) -> Float)? = null
    private val mvp = FloatArray(16); private val eye = FloatArray(3); private val v = FloatArray(4); private val out = FloatArray(4)
    private val placed = ArrayList<RectF>()
    private val pool = ArrayList<RectF>() // reused every frame: no allocations while drawing
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad = 5 * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || (labels.isEmpty() && !meOn)) return
        val st = camera.state()
        OrbitCamera.mvp(st, width.toFloat() / height, mvp, eye, minEye = ground)
        placed.clear()
        used = 0
        far = st.dist > 9000f
        // the chosen run's label first, over all the others (the site's 1e3)
        val chosen = chosenRun
        if (chosen != null) for (l in labels) if (l.run == chosen) place(canvas, l)
        for (l in labels) if (chosen == null || l.run != chosen) place(canvas, l)
        if (meOn) drawMe(canvas)
    }

    /** "Where am I" (round 19): the dot, its accuracy circle and, when moving, where it heads; over everything. */
    var meOn = false
    var meX = 0f; var meY = 0f; var meZ = 0f; var meAcc = 0f; var meBearing = Float.NaN; var meDot = true
    var meColor = Color.rgb(31, 95, 196); var mePaper = Color.WHITE
    private val mePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mePath = android.graphics.Path()

    private fun screen(x: Float, y: Float, z: Float, into: FloatArray): Boolean {
        v[0] = x; v[1] = y; v[2] = z; v[3] = 1f
        Matrix.multiplyMV(out, 0, mvp, 0, v, 0)
        if (out[3] <= 0) return false
        into[0] = (out[0] / out[3] * 0.5f + 0.5f) * width
        into[1] = (1 - (out[1] / out[3] * 0.5f + 0.5f)) * height
        return true
    }
    private val p0 = FloatArray(2); private val p1 = FloatArray(2)

    private fun drawMe(canvas: Canvas) {
        if (!screen(meX, meY, meZ, p0)) return
        val d = resources.displayMetrics.density
        // the circle: the accuracy in metres, measured across the screen at the dot's depth
        val r = if (screen(meX + meAcc, meY, meZ, p1)) maxOf(14 * d, kotlin.math.hypot(p1[0] - p0[0], p1[1] - p0[1])) else 14 * d
        mePaint.style = Paint.Style.FILL; mePaint.color = (meColor and 0x00FFFFFF) or (0x29 shl 24)
        canvas.drawCircle(p0[0], p0[1], r, mePaint)
        mePaint.style = Paint.Style.STROKE; mePaint.strokeWidth = 1.5f * d; mePaint.color = (meColor and 0x00FFFFFF) or (0x99 shl 24)
        canvas.drawCircle(p0[0], p0[1], r, mePaint)
        if (!meDot) return
        // where it heads: a small triangle beyond the dot, toward a point 30 m along the bearing
        if (!meBearing.isNaN()) {
            val a = Math.toRadians(meBearing.toDouble())
            if (screen(meX + 30f * kotlin.math.sin(a).toFloat(), meY, meZ - 30f * kotlin.math.cos(a).toFloat(), p1)) {
                val dx = p1[0] - p0[0]; val dy = p1[1] - p0[1]; val n = kotlin.math.hypot(dx, dy)
                if (n > 1f) {
                    val ux = dx / n; val uy = dy / n
                    val tip = 23 * d; val base = 12 * d; val half = 7 * d
                    mePath.reset()
                    mePath.moveTo(p0[0] + ux * tip, p0[1] + uy * tip)
                    mePath.lineTo(p0[0] + ux * base - uy * half, p0[1] + uy * base + ux * half)
                    mePath.lineTo(p0[0] + ux * base + uy * half, p0[1] + uy * base - ux * half)
                    mePath.close()
                    mePaint.style = Paint.Style.FILL; mePaint.color = meColor
                    canvas.drawPath(mePath, mePaint)
                }
            }
        }
        mePaint.style = Paint.Style.FILL
        mePaint.color = Color.argb(60, 0, 0, 0); canvas.drawCircle(p0[0], p0[1] + d, 10.5f * d, mePaint)
        mePaint.color = mePaper; canvas.drawCircle(p0[0], p0[1], 9 * d, mePaint)
        mePaint.color = meColor; canvas.drawCircle(p0[0], p0[1], 6 * d, mePaint)
    }

    private var used = 0
    private var far = false

    private fun place(canvas: Canvas, l: MapLabel) {
        if (l.lift && hideLifts) return
        val run = l.run
        if (run != null && (run in hiddenRuns || (chosenRun != null && run != chosenRun) || (chosenRun == null && far))) return
        v[0] = l.x; v[1] = l.y; v[2] = l.z; v[3] = 1f
        Matrix.multiplyMV(out, 0, mvp, 0, v, 0)
        if (out[3] <= 0) return
        val sx = (out[0] / out[3] * 0.5f + 0.5f) * width
        val sy = (1 - (out[1] / out[3] * 0.5f + 0.5f)) * height
        if (sx < -50 || sx > width + 50 || sy < -20 || sy > height + 20) return
        if (!visible(l)) return
        val w = l.layout.width.toFloat(); val h = l.layout.height.toFloat()
        if (used == pool.size) pool += RectF()
        val r = pool[used]
        r.set(sx - w / 2 - pad, sy - h - pad * 2, sx + w / 2 + pad, sy)
        for (k in 0 until placed.size) if (RectF.intersects(placed[k], r)) return
        used++
        placed += r
        bg.color = if (l.dark) Color.argb(235, 19, 35, 58) else Color.argb(235, 255, 255, 255)
        canvas.drawRect(r, bg)
        bg.color = if (l.dark) Color.WHITE else Color.argb(255, 19, 35, 58)
        canvas.drawRect(sx - 1.5f, sy, sx + 1.5f, sy + pad * 1.6f, bg) // the sign's post
        canvas.save(); canvas.translate(r.left + pad, r.top + pad); l.layout.draw(canvas); canvas.restore()
    }

    /**
     * Not behind a ridge, as the site's labels: a few dozen steps along the line from the eye to the label, and the
     * label is hidden where the mountain rises more than 8 m above that line.
     */
    private fun visible(l: MapLabel): Boolean {
        val g = ground ?: return true
        val dx = l.x - eye[0]; val dy = l.y - eye[1]; val dz = l.z - eye[2]
        for (i in 4 until 40) {
            val t = i / 40f
            if (g(eye[0] + dx * t, eye[2] + dz * t) > eye[1] + dy * t + 8f) return false
        }
        return true
    }
}

/**
 * The map: GL mountain, label overlay and all the gestures. One finger pans (and flings),
 * two fingers zoom, turn, and tilt (moving both up or down). A tap picks the nearest run.
 */
@SuppressLint("ViewConstructor")
class MapView(context: Context, refreshHz: Float, val stats: FrameStats) : FrameLayout(context) {
    private companion object { const val TURN_THRESHOLD = 0.2f } // ~11° of finger turn before the map turns
    val camera = OrbitCamera()
    private val density = resources.displayMetrics.density
    private val surface = MountainSurface(context, refreshHz)
    private val overlay = LabelOverlay(context, camera)
    private val worker = Executors.newSingleThreadExecutor()
    private val renderer: MapRenderer = MapRenderer(camera, stats, density,
        onFrame = { overlay.postInvalidateOnAnimation() },
        requestRender = { surface.requestRender() })

    var scene: MapScene? = null; private set
    var selected: Piste? = null; private set
    var onSelect: ((Piste?) -> Unit)? = null
    /** The activity's listener: the chosen run goes into the saved place (nav/Nav.kt); [via] how (run_open's `via`). */
    var onChosen: ((Piste?, String) -> Unit)? = null
    var onFlying: ((Boolean) -> Unit)? = null
    /** The map screen's listener: a lift the map shows (the meeting point's "see it on the run map"), for its panel. */
    var onLift: ((io.github.pini236.skiapp.data.Lift) -> Unit)? = null
    /** A tap on a lift's line (nearer than any run): the map screen opens its panel (PARITY A-10). */
    var onLiftTap: ((io.github.pini236.skiapp.data.Lift) -> Unit)? = null
    /** A lift shown while no map screen listened; the next map screen opens its panel. */
    var shownLift: io.github.pini236.skiapp.data.Lift? = null
    val msaa get() = surface.msaa

    /** What lies on the snow in 3D (Drape.kt): painted on the map's thread, and again when a new GL context lost it. */
    private var env: io.github.pini236.skiapp.meet.Relief2D? = null
    fun setEnv(r: io.github.pini236.skiapp.meet.Relief2D) {
        if (env === r) return
        env = r
        scene?.let { overlay.labels = buildLabels(it); overlay.invalidate() } // the place labels come with the relief
        paintDrape()
    }
    private fun paintDrape() {
        val r = env ?: return
        inBackground {
            val b = runCatching { Drape.paint(r) }.getOrNull() ?: return@inBackground
            renderer.drape = b
            surface.requestRender()
            Qa.log("map drape ready")
        }
    }

    init {
        renderer.onDrapeLost = { post { paintDrape() } }
        surface.setRenderer(renderer)
        surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        renderer.onFlyEnded = { token, done -> post { flyEnded(token, done) } }
    }

    fun setScene(s: MapScene) {
        scene = s
        overlay.ground = { x, z -> s.terrain.elev(x, z) }
        overlay.labels = buildLabels(s)
        renderer.scene = s
        surface.requestRender()
    }

    fun updateShadow() = renderer.updateShadow()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        selected?.let { select(it, chosen = false) } // the GL context is new when the map tab comes back: paint the chosen run again
    }
    fun onPause() = surface.onPause()
    fun onResume() { renderer.still = io.github.pini236.skiapp.ui.Motion.reduced(context); surface.onResume() }
    fun release() { removeCallbacks(pendingPick); worker.shutdownNow() }

    /**
     * Work for the map's thread; none after release(). A tap's pending pick posted while the map was on the screen still
     * runs if the map left the screen before the activity was destroyed (a view off the screen cannot take it back).
     */
    private fun inBackground(work: () -> Unit) { if (!worker.isShutdown) worker.execute(work) }

    private fun buildLabels(s: MapScene): List<MapLabel> {
        // the language's faces: Karantina and Plex have no Russian or Georgian letters
        val lang = io.github.pini236.skiapp.i18n.Lang.current(resources)
        val display = io.github.pini236.skiapp.i18n.Lang.typeface(context, lang, display = true)
        val body = io.github.pini236.skiapp.i18n.Lang.typeface(context, lang, display = false)
        val nf = NumberFormat.getIntegerInstance(lang.locale)
        fun layout(text: String, tf: android.graphics.Typeface?, sp: Float, color: Int, bold: Boolean = false): StaticLayout {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = if (bold) android.graphics.Typeface.create(tf, android.graphics.Typeface.BOLD) else tf
                textSize = sp * resources.displayMetrics.scaledDensity; this.color = color
            }
            val w = kotlin.math.ceil(Layout.getDesiredWidth(text, p)).toInt()
            // the language's direction, not the first letter's: a Latin name first made a Hebrew label read left to right
            // and put "מ׳" on the wrong side of the height
            val dir = if (lang.rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.LTR
            return StaticLayout.Builder.obtain(text, 0, text.length, p, w).setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(dir).setIncludePad(false).build()
        }
        val out = ArrayList<MapLabel>()
        // peaks: a Latin name, a Hebrew unit and a number in one line, the bidi case
        for (p in s.terrain.peaks) out += MapLabel(p.x, s.terrain.elev(p.x, p.y) + 30, p.y,
            layout(context.getString(R.string.app_peak_label, iso(p.name), nf.format(p.ele)), display, 19f, Color.argb(255, 19, 35, 58)), 100, false)
        // the named lifts, halfway along the cable, as on the site (A-35)
        s.runs.lifts.forEachIndexed { i, l ->
            if (l.name.isBlank()) return@forEachIndexed
            val line = s.liftLines[i]; val n = line.size / 3
            if (n < 2) return@forEachIndexed
            var total = 0f
            for (k in 1 until n) total += hypot(line[k * 3] - line[k * 3 - 3], line[k * 3 + 2] - line[k * 3 - 1])
            var k = 1; var acc = 0f
            while (k < n - 1 && acc + hypot(line[k * 3] - line[k * 3 - 3], line[k * 3 + 2] - line[k * 3 - 1]) < total / 2) { acc += hypot(line[k * 3] - line[k * 3 - 3], line[k * 3 + 2] - line[k * 3 - 1]); k++ }
            val seg = hypot(line[k * 3] - line[k * 3 - 3], line[k * 3 + 2] - line[k * 3 - 1]).coerceAtLeast(0.01f)
            val f = ((total / 2 - acc) / seg).coerceIn(0f, 1f)
            val m = FloatArray(3) { j -> line[(k - 1) * 3 + j] + (line[k * 3 + j] - line[(k - 1) * 3 + j]) * f }
            out += MapLabel(m[0], m[1] + 12, m[2], layout(context.getString(R.string.app_lift_label, iso(l.name)), body, 12f, Color.WHITE), 45, true, lift = true)
        }
        // the village and Kobi, as the site's place labels ("Kobi" stays Latin)
        val relief = env
        for ((n, x, z) in placesOf(relief)) {
            val text = if (n == "Gudauri") context.getString(R.string.map_place_gudauri) else iso(n)
            out += MapLabel(x, s.terrain.elev(x, z) + 10, z, layout(text, display, 15f, io.github.pini236.skiapp.ui.DayColors.muted.toArgb()), 60, false)
        }
        // the named runs, in their colours on the snow's light palette (the 3D map is always snowy, as the site's .r3-labels), 45% down their longest line (the site's)
        for (p in s.runs.pistes) {
            if (!p.named) continue
            val line = s.draped[p.key]?.maxByOrNull { it.size } ?: continue
            val k = (line.size / 3 * 0.45f).toInt().coerceAtMost(line.size / 3 - 1)
            val name = if (p.key == "Firni ?") "Firni (1/2?)" else p.key
            out += MapLabel(line[k * 3], line[k * 3 + 1] + 8, line[k * 3 + 2], layout(iso(name), body, 13f, io.github.pini236.skiapp.ui.DayColors.run(p.color).toArgb(), bold = true), 40, false, run = p.key)
        }
        return out.sortedByDescending { it.priority }
    }

    /** The place labels: only the village and Kobi, as on the site; none until the relief is read. */
    private fun placesOf(r: io.github.pini236.skiapp.meet.Relief2D?) = r?.places.orEmpty().filter { it.first == "Gudauri" || it.first == "Kobi" }

    /** A Latin name kept in its own direction inside a label of any language (first-strong isolate). */
    private fun iso(name: String) = "\u2068$name\u2069"

    // ---- selection ----
    /**
     * [chosen]: the person picked it now (a tap on the map, or [via] the list, the sign's steps, a link); false when the
     * map only paints again what was already chosen.
     */
    fun select(p: Piste?, chosen: Boolean = true, via: String = "map") {
        val s = scene ?: return
        // another run, or none (the panel closed): the flight down the old one stops (PARITY A-8)
        if (flyToken != null && p?.key != flyKey) stopFly()
        selected = p
        overlay.chosenRun = p?.key; overlay.invalidate()
        onSelect?.invoke(p)
        if (chosen) onChosen?.invoke(p, via)
        renderer.marker = null
        if (p == null) { framed = null; renderer.select(null); Qa.log("selected none"); return }
        inBackground {
            val lines = s.topDown(p)
            if (lines.isEmpty()) return@inBackground
            var total = 0f
            val lens = lines.map { l -> var acc = 0f; for (i in 1 until l.size / 3) acc += hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1]); total += acc; acc }
            val slopes = lines.map { s.lineSlopes(it) }
            // progress top-down across all of the run's lines, so the paint flows from the top
            var before = 0f
            val progress = lines.mapIndexed { li, l ->
                var acc = 0f
                FloatArray(l.size / 3) { i -> if (i > 0) acc += hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1]); ((before + acc) / total.coerceAtLeast(1f)).also { if (i == l.size / 3 - 1) before += lens[li] } }
            }
            val casing = Ribbon.build(lines, { _, _ -> floatArrayOf(1f, 1f, 1f, 1f) }, { li, i -> progress[li][i] })
            val paint = Ribbon.build(lines, { li, i -> SlopeColors.of(slopes[li][i]) + 1f }, { li, i -> progress[li][i] })
            val path = lines.fold(FloatArray(0)) { acc, l -> acc + l }
            val sel = Selection(p.key, s.slopeLayer(lines), casing, paint, path)
            renderer.select(sel)
            // land the camera on the whole run, between the bars and above the panel, looking uphill so its top is at the top
            framed = path
            renderer.animateCamera(Framing.fit(path, width.toFloat(), height.toFloat(), { x, z -> s.terrain.elev(x, z) }, box = Framing.Box(bottom = freeBottom)))
            Qa.log("selected ${p.key}")
        }
    }

    /** The meeting point's "see it on the run map": the camera on a lift's whole line, as on a chosen run. */
    fun showLift(id: String) {
        val s = scene ?: return
        val i = s.runs.lifts.indexOfFirst { it.id == id }
        if (i < 0) return
        select(null, chosen = false)
        // the map screen may not be on the screen yet (the meeting point pushes the map, then shows the lift)
        val l = s.runs.lifts[i]
        onLift?.let { it(l) } ?: run { shownLift = l }
        // after the map has its size (it may have just been put on the screen)
        post { inBackground {
            framed = s.liftLines[i]
            renderer.animateCamera(Framing.fit(s.liftLines[i], width.toFloat(), height.toFloat(), { x, z -> s.terrain.elev(x, z) }, box = Framing.Box(bottom = freeBottom)))
            Qa.log("showing lift $id")
        } }
    }

    /** The compass (the site's): the camera turns back to look north, from the south, around the same spot. */
    fun north() { val st = camera.state(); renderer.animateCamera(st.copy(yaw = 0f), 0.6f); Qa.log("map north") }

    /** Puts the camera at a given view (the QA run uses it for repeatable screenshots). */
    fun look(st: OrbitCamera.State) = renderer.animateCamera(st, 0.05f)

    // the flight now (run_fly_start and run_fly_end, as on the site): a token per flight, so the end of an old one
    // (posted from the map's thread) is never taken for the end of a new one
    private var flyToken: Any? = null
    private var flyKey = ""
    private var flyT0 = 0L
    val flying get() = flyToken != null

    fun flyDown() {
        val s = scene ?: return; val p = selected ?: return
        // along the run's longest line, the one its profile shows, as on the site (A-13); not across its other pieces
        fun len(l: FloatArray): Float { var a = 0f; for (i in 1 until l.size / 3) a += hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1]); return a }
        val path = s.topDown(p).maxByOrNull(::len) ?: return
        if (path.size < 6) return
        flyToken?.let { flyEnded(it, false) }
        val token = Any()
        flyToken = token; flyKey = p.key; flyT0 = System.currentTimeMillis()
        renderer.startFly(path, token)
        Telemetry.event("run_fly_start", mapOf("run" to p.key))
        onFlying?.invoke(true); Qa.log("fly started")
    }

    /** Stopped by the person (the bar's button, another run, the panel closed); a touch on the map stops it too. */
    fun stopFly() { val t = flyToken ?: return; renderer.stopFly(); flyEnded(t, false) }

    /** [done]: it reached the bottom of the run (the site's completed: progress at the end). */
    private fun flyEnded(token: Any, done: Boolean) {
        if (token !== flyToken) return
        flyToken = null
        Telemetry.event("run_fly_end", mapOf("run" to flyKey, "completed" to done, "seconds" to ((System.currentTimeMillis() - flyT0) / 1000.0).roundToInt()))
        onFlying?.invoke(false); Qa.log("fly ended")
    }

    /** The line the camera is framing (the chosen run, a lift), and the free part of the screen above the map's panel. */
    @Volatile private var framed: FloatArray? = null
    @Volatile private var freeBottom = Framing.Box().bottom

    /**
     * The map's panel covers the screen below [bottom] (a fraction of the height): the run or lift in view moves to
     * the part above it, so the dot of its profile is never under the panel.
     */
    fun setFreeBottom(bottom: Float) {
        val b = bottom.coerceIn(0.3f, Framing.Box().bottom)
        if (kotlin.math.abs(b - freeBottom) < 0.02f) return
        freeBottom = b
        val path = framed ?: return
        val s = scene ?: return
        inBackground { renderer.animateCamera(Framing.fit(path, width.toFloat(), height.toFloat(), { x, z -> s.terrain.elev(x, z) }, box = Framing.Box(bottom = b)), 0.6f) }
    }

    /** A dot on the mountain at a point of the chosen run (its profile under the finger, T2); null takes it away. */
    fun mark(x: Float, y: Float) { val s = scene ?: return; renderer.marker = floatArrayOf(x, s.terrain.elev(x, y), y); surface.requestRender() }
    fun unmark() { if (renderer.marker != null) { renderer.marker = null; surface.requestRender() } }

    /** "Where am I" (round 19): the dot on the snow, in the site's projection; [dot] false draws only the circle. */
    fun showMe(x: Float, y: Float, accuracy: Float, bearing: Float?, color: Int, paper: Int, dot: Boolean = true) {
        val s = scene ?: return
        overlay.meX = x; overlay.meZ = y; overlay.meY = s.terrain.elev(x, y) + 2f
        overlay.meAcc = accuracy; overlay.meBearing = bearing ?: Float.NaN; overlay.meDot = dot
        overlay.meColor = color; overlay.mePaper = paper; overlay.meOn = true
        overlay.invalidate()
    }
    fun hideMe() { if (overlay.meOn) { overlay.meOn = false; overlay.invalidate() } }

    /** The camera to the dot, keeping the view's distance and heading (once, when the first reading comes). */
    fun lookAt(x: Float, y: Float) {
        val s = scene ?: return
        val st = camera.state()
        renderer.animateCamera(st.copy(tx = x, ty = s.terrain.elev(x, y), tz = y, dist = minOf(st.dist, 2600f)), 0.6f)
    }

    /** The map's filters (the site's): these runs and, with [lifts] false, the lifts are not drawn, and a tap passes them by. */
    fun setHidden(keys: Set<String>, lifts: Boolean) { renderer.hidden = keys; renderer.hideLifts = !lifts; overlay.hideLifts = !lifts; overlay.hiddenRuns = keys; overlay.invalidate(); surface.requestRender() }

    /** The lift status on the mountain (S1): closed lifts and runs, and "only what's open for me". */
    fun setStatus(p: StatusPaint) { renderer.status = p; surface.requestRender() }
    /** Distance along, run length, height and slope where the skier is; null when not flying. */
    fun flyInfo(): FloatArray? = renderer.flyInfo

    // ---- gestures, as on phone maps: the ground under the fingers stays under the fingers ----
    // One finger pans and flings. Two fingers pan, pinch to zoom towards them, and turn the map once the
    // turn passes a threshold (so a pinch does not turn it by accident); two fingers up or down together
    // tilt instead. A double tap zooms in on the spot, a two-finger tap zooms out. A tap picks a run.
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    private var vt: VelocityTracker? = null
    private var lastX = 0f; private var lastY = 0f
    private var downX = 0f; private var downY = 0f; private var downT = 0L
    private var moved = false; private var multi = false

    private enum class Two { UNDECIDED, TRANSFORM, TILT }
    private var two = Two.UNDECIDED
    private val start0 = FloatArray(2); private val start1 = FloatArray(2); private var startGap = 0f
    private var lastMidX = 0f; private var lastMidY = 0f; private var lastGap = 0f; private var lastAngle = 0f
    private var turned = 0f; private var turning = false; private var twoDownT = 0L

    private var lastTapT = 0L; private var lastTapX = 0f; private var lastTapY = 0f
    private val pendingPick = Runnable { pick(lastTapX, lastTapY) }

    private fun move(f: (OrbitCamera.State) -> OrbitCamera.State) { camera.update(f); surface.requestRender() }

    /** The height of the snow, for the gestures; flat before the mountain has loaded. */
    val snow: (Float, Float) -> Float = { x, z -> scene?.terrain?.elev(x, z) ?: 2300f }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat(); val h = height.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                renderer.stopFly(); renderer.cancelCameraAnimation(); camera.stopFling()
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
                    if (!moved && hypot(e.x - downX, e.y - downY) > slop) { moved = true; lastX = downX; lastY = downY } // the ground catches up with the finger
                    if (moved) { val x0 = lastX; val y0 = lastY; move { Moves.pan(it, w, h, x0, y0, e.x, e.y, snow) } }
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.pointerCount == 2 && two == Two.UNDECIDED && e.eventTime - twoDownT < 300) {
                    // a two-finger tap: zoom out around the middle of the fingers
                    val mx = (e.getX(0) + e.getX(1)) / 2; val my = (e.getY(0) + e.getY(1)) / 2
                    renderer.animateCamera(Moves.zoom(camera.state(), w, h, mx, my, 0.5f, snow), 0.35f)
                    Qa.log("gesture two-finger tap: zoom out")
                    two = Two.TRANSFORM // only once
                }
                // carry on smoothly with the fingers that stay: one pans, two start again from where they are
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
                surface.requestRender()
            }
            MotionEvent.ACTION_CANCEL -> { vt?.recycle(); vt = null }
        }
        return true
    }

    private fun twoFrom(x0: Float, y0: Float, x1: Float, y1: Float) {
        start0[0] = x0; start0[1] = y0; start1[0] = x1; start1[1] = y1
        startGap = Moves.gap(x0, y0, x1, y1)
        lastMidX = (x0 + x1) / 2; lastMidY = (y0 + y1) / 2
        lastGap = startGap; lastAngle = atan2(y1 - y0, x1 - x0)
    }

    private fun twoFingers(e: MotionEvent, w: Float, h: Float) {
        val x0 = e.getX(0); val y0 = e.getY(0); val x1 = e.getX(1); val y1 = e.getY(1)
        val midX = (x0 + x1) / 2; val midY = (y0 + y1) / 2
        val gap = Moves.gap(x0, y0, x1, y1); val angle = atan2(y1 - y0, x1 - x0)
        if (two == Two.UNDECIDED) {
            val d0 = hypot(x0 - start0[0], y0 - start0[1]); val d1 = hypot(x1 - start1[0], y1 - start1[1])
            if (d0 < slop && d1 < slop) return
            two = if (Moves.isTilt(x0 - start0[0], y0 - start0[1], x1 - start1[0], y1 - start1[1], gap - startGap, slop)) Two.TILT else Two.TRANSFORM
            Qa.log("gesture two fingers: ${two.name.lowercase()}")
        }
        if (two == Two.TILT) {
            val dy = midY - lastMidY
            move { Moves.tilt(it, h, dy) }
        } else {
            val px = lastMidX; val py = lastMidY
            val factor = if (lastGap > 1f) gap / lastGap else 1f
            val da = Moves.wrap(angle - lastAngle)
            turned += da
            if (!turning && kotlin.math.abs(turned) > TURN_THRESHOLD) turning = true
            move {
                var st = Moves.pan(it, w, h, px, py, midX, midY, snow)
                st = Moves.zoom(st, w, h, midX, midY, factor, snow)
                if (turning) Moves.rotate(st, w, h, midX, midY, da, snow) else st
            }
        }
        lastMidX = midX; lastMidY = midY; lastGap = gap; lastAngle = angle
    }

    /** A tap picks a run; it waits for a possible second tap, which zooms in on the spot instead. */
    private fun tap(x: Float, y: Float, t: Long, w: Float, h: Float) {
        if (t - lastTapT < doubleTapMs && hypot(x - lastTapX, y - lastTapY) < 48 * density) {
            removeCallbacks(pendingPick); lastTapT = 0L
            renderer.animateCamera(Moves.zoom(camera.state(), w, h, x, y, 2.2f, snow), 0.35f)
            Qa.log("gesture double tap: zoom in")
            return
        }
        lastTapT = t; lastTapX = x; lastTapY = y
        removeCallbacks(pendingPick); postDelayed(pendingPick, doubleTapMs)
    }

    /** The run whose line passes closest to the finger, within 28 dp. */
    private fun pick(px: Float, py: Float) {
        val s = scene ?: return
        val mvp = FloatArray(16); val eye = FloatArray(3); val v = FloatArray(4); val o = FloatArray(4)
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye) { x, z -> s.terrain.elev(x, z) }
        var best: Piste? = null; var bestD = 28 * density
        val off = renderer.hidden
        for (p in s.runs.pistes) if (p.key !in off) for (l in s.draped[p.key] ?: emptyList()) {
            var i = 0
            while (i < l.size / 3) {
                v[0] = l[i * 3]; v[1] = l[i * 3 + 1]; v[2] = l[i * 3 + 2]; v[3] = 1f
                Matrix.multiplyMV(o, 0, mvp, 0, v, 0)
                if (o[3] > 0) {
                    val sx = (o[0] / o[3] * 0.5f + 0.5f) * width; val sy = (1 - (o[1] / o[3] * 0.5f + 0.5f)) * height
                    val d = hypot(sx - px, sy - py)
                    if (d < bestD) { bestD = d; best = p }
                }
                i += 2
            }
        }
        // the lifts too (not when filtered out): along each cable, so a long span between two points is found
        var lift = -1
        if (!renderer.hideLifts) s.liftLines.forEachIndexed { li, l ->
            if (s.runs.lifts[li].id.isBlank()) return@forEachIndexed
            for (i in 0 until l.size / 3 - 1) for (k in 0..8) {
                val f = k / 8f
                for (j in 0..2) v[j] = l[i * 3 + j] + (l[i * 3 + 3 + j] - l[i * 3 + j]) * f
                v[3] = 1f
                Matrix.multiplyMV(o, 0, mvp, 0, v, 0)
                if (o[3] <= 0) continue
                val sx = (o[0] / o[3] * 0.5f + 0.5f) * width; val sy = (1 - (o[1] / o[3] * 0.5f + 0.5f)) * height
                val d = hypot(sx - px, sy - py)
                if (d < bestD) { bestD = d; lift = li; best = null }
            }
        }
        if (lift >= 0) { val l = s.runs.lifts[lift]; post { onLiftTap?.invoke(l) }; return }
        if (best != null && best != selected) select(best) else if (best == null && selected != null) select(null)
    }
}

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
import androidx.core.content.res.ResourcesCompat
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.perf.FrameStats
import io.github.pini236.skiapp.qa.Qa
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.atan2
import kotlin.math.hypot

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
private class MapLabel(val x: Float, val y: Float, val z: Float, val layout: StaticLayout, val priority: Int, val dark: Boolean)

private class LabelOverlay(context: Context, private val camera: OrbitCamera) : View(context) {
    var labels: List<MapLabel> = emptyList()
    var ground: ((Float, Float) -> Float)? = null
    private val mvp = FloatArray(16); private val eye = FloatArray(3); private val v = FloatArray(4); private val out = FloatArray(4)
    private val placed = ArrayList<RectF>()
    private val pool = ArrayList<RectF>() // reused every frame: no allocations while drawing
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad = 5 * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || labels.isEmpty()) return
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye, minEye = ground)
        placed.clear()
        var used = 0
        for (l in labels) {
            v[0] = l.x; v[1] = l.y; v[2] = l.z; v[3] = 1f
            Matrix.multiplyMV(out, 0, mvp, 0, v, 0)
            if (out[3] <= 0) continue
            val sx = (out[0] / out[3] * 0.5f + 0.5f) * width
            val sy = (1 - (out[1] / out[3] * 0.5f + 0.5f)) * height
            if (sx < -50 || sx > width + 50 || sy < -20 || sy > height + 20) continue
            val w = l.layout.width.toFloat(); val h = l.layout.height.toFloat()
            if (used == pool.size) pool += RectF()
            val r = pool[used]
            r.set(sx - w / 2 - pad, sy - h - pad * 2, sx + w / 2 + pad, sy)
            if (placed.any { RectF.intersects(it, r) }) continue
            used++
            placed += r
            bg.color = if (l.dark) Color.argb(235, 19, 35, 58) else Color.argb(235, 255, 255, 255)
            canvas.drawRect(r, bg)
            bg.color = if (l.dark) Color.WHITE else Color.argb(255, 19, 35, 58)
            canvas.drawRect(sx - 1.5f, sy, sx + 1.5f, sy + pad * 1.6f, bg) // the sign's post
            canvas.save(); canvas.translate(r.left + pad, r.top + pad); l.layout.draw(canvas); canvas.restore()
        }
    }
}

/**
 * The map: GL mountain, label overlay and all the gestures. One finger pans (and flings),
 * two fingers zoom, turn, and tilt (moving both up or down). A tap picks the nearest run.
 */
@SuppressLint("ViewConstructor")
class MapView(context: Context, refreshHz: Float, val stats: FrameStats) : FrameLayout(context) {
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
    var onFlying: ((Boolean) -> Unit)? = null
    val msaa get() = surface.msaa

    init {
        surface.setRenderer(renderer)
        surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        renderer.onFlyEnded = { post { onFlying?.invoke(false); Qa.log("fly ended") } }
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
        selected?.let { select(it) } // the GL context is new when the map tab comes back: paint the chosen run again
    }
    fun onPause() = surface.onPause()
    fun onResume() = surface.onResume()
    fun release() { worker.shutdownNow() }

    private fun buildLabels(s: MapScene): List<MapLabel> {
        val display = ResourcesCompat.getFont(context, R.font.karantina_bold)
        val body = ResourcesCompat.getFont(context, R.font.plex_hebrew_bold)
        val nf = NumberFormat.getIntegerInstance(Locale.US)
        fun layout(text: String, tf: android.graphics.Typeface?, sp: Float, color: Int): StaticLayout {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = sp * resources.displayMetrics.scaledDensity; this.color = color }
            val w = kotlin.math.ceil(Layout.getDesiredWidth(text, p)).toInt()
            return StaticLayout.Builder.obtain(text, 0, text.length, p, w).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        }
        val out = ArrayList<MapLabel>()
        // peaks: a Latin name, a Hebrew unit and a number in one line, the bidi case
        for (p in s.terrain.peaks) out += MapLabel(p.x, s.terrain.elev(p.x, p.y) + 30, p.y,
            layout("${p.name} · ${nf.format(p.ele)} מ׳", display, 19f, Color.argb(255, 19, 35, 58)), 3, false)
        // top stations of the named lifts
        s.runs.lifts.forEachIndexed { i, l ->
            if (l.name.isBlank()) return@forEachIndexed
            val line = s.liftLines[i]; val n = line.size / 3
            val top = if (line[1] > line[(n - 1) * 3 + 1]) 0 else n - 1
            out += MapLabel(line[top * 3], line[top * 3 + 1] + 20, line[top * 3 + 2], layout("רכבל ${l.name}", body, 12f, Color.WHITE), 2, true)
        }
        return out.sortedByDescending { it.priority }
    }

    // ---- selection ----
    fun select(p: Piste?) {
        val s = scene ?: return
        selected = p
        onSelect?.invoke(p)
        if (p == null) { renderer.select(null); Qa.log("selected none"); return }
        worker.execute {
            val lines = s.topDown(p)
            if (lines.isEmpty()) return@execute
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
            val sel = Selection(p.key, s.highlight(lines), casing, paint, path)
            renderer.select(sel)
            // land the camera low over the run, looking down its fall line
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            for (i in 0 until path.size / 3) { minX = minOf(minX, path[i * 3]); maxX = maxOf(maxX, path[i * 3]); minZ = minOf(minZ, path[i * 3 + 2]); maxZ = maxOf(maxZ, path[i * 3 + 2]) }
            val cx = (minX + maxX) / 2; val cz = (minZ + maxZ) / 2
            val span = maxOf(maxX - minX, maxZ - minZ, 600f)
            val n = path.size / 3
            val yaw = atan2(-(path[(n - 1) * 3] - path[0]), -(path[(n - 1) * 3 + 2] - path[2]))
            renderer.animateCamera(OrbitCamera.State(cx, s.terrain.elev(cx, cz), cz, span * 1.35f, yaw, 0.55f))
            Qa.log("selected ${p.key}")
        }
    }

    /** Puts the camera at a given view (the QA run uses it for repeatable screenshots). */
    fun look(st: OrbitCamera.State) = renderer.animateCamera(st, 0.05f)

    fun flyDown() {
        val s = scene ?: return; val p = selected ?: return
        val path = s.topDown(p).fold(FloatArray(0)) { acc, l -> acc + l }
        if (path.size < 6) return
        renderer.startFly(path); onFlying?.invoke(true); Qa.log("fly started")
    }

    fun stopFly() { renderer.stopFly() }

    // ---- gestures ----
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var vt: VelocityTracker? = null
    private var lastX = 0f; private var lastY = 0f
    private var downX = 0f; private var downY = 0f; private var downT = 0L
    private var twoDist = 0f; private var twoAngle = 0f; private var twoY = 0f
    private var moved = false; private var multi = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                renderer.stopFly(); renderer.cancelCameraAnimation(); camera.stopFling()
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; downT = e.eventTime; moved = false; multi = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                multi = true; moved = true
                twoDist = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                twoAngle = atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))
                twoY = (e.getY(0) + e.getY(1)) / 2
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (e.pointerCount >= 2) {
                    val d = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
                    val a = atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))
                    val y = (e.getY(0) + e.getY(1)) / 2
                    if (twoDist > 0) camera.zoom(d / twoDist)
                    var da = a - twoAngle
                    while (da > Math.PI) da -= (2 * Math.PI).toFloat()
                    while (da < -Math.PI) da += (2 * Math.PI).toFloat()
                    camera.rotate(-da)
                    camera.tilt((y - twoY) / height * 1.6f)
                    twoDist = d; twoAngle = a; twoY = y
                } else if (!multi) {
                    if (!moved && hypot(e.x - downX, e.y - downY) > slop) moved = true
                    if (moved) camera.pan(e.x - lastX, e.y - lastY, height)
                    lastX = e.x; lastY = e.y
                }
                surface.requestRender()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // keep panning smoothly with the finger that stays
                val keep = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(keep); lastY = e.getY(keep)
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && e.eventTime - downT < 300) pick(e.x, e.y)
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

    /** The run whose line passes closest to the finger, within 28 dp. */
    private fun pick(px: Float, py: Float) {
        val s = scene ?: return
        val mvp = FloatArray(16); val eye = FloatArray(3); val v = FloatArray(4); val o = FloatArray(4)
        OrbitCamera.mvp(camera.state(), width.toFloat() / height, mvp, eye) { x, z -> s.terrain.elev(x, z) }
        var best: Piste? = null; var bestD = 28 * density
        for (p in s.runs.pistes) for (l in s.draped[p.key] ?: emptyList()) {
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
        if (best != null && best != selected) select(best) else if (best == null && selected != null) select(null)
    }
}

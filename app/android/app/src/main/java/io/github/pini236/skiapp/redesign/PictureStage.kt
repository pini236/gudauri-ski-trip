package io.github.pini236.skiapp.redesign

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.map.Sun
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The painted mountain as a [Stage] (the opening's handoff, 25.11.2026; the architect's conditions of the same day): the
 * picture of the hour (hero-*), the designer's layer over it (PeakScreen's [layer]), and every place on it from the
 * projection contract (site/data/peak.json, the front view). Zoom and pan only; turning or tilting asks [onRotateTry].
 * A run that the front picture shows less than half of (the Kobi side), or one the picture has no marking for, asks
 * [onNeedLive]: the layers then go to the live model for that moment (Z05).
 *
 * Memory (condition 3): at most two decoded pictures, only while [Draw] is on the screen; leaving it frees them.
 */
internal class PictureStage(private val context: Context, private val scene: MapScene, private val d: PeakData, private val v: PeakContract.View) : Stage {
    private val hero = d.hero
    private val density = context.resources.displayMetrics.density
    private val reduced = Motion.reduced(context)

    /** a run's draped lines in the picture's points, and whether each point is seen there (worked out once, off the main thread) */
    class PicLine(val key: String, val xy: FloatArray, val seen: BooleanArray)
    @Volatile private var pic: List<PicLine>? = null
    private val worker = Executors.newSingleThreadExecutor()
    init {
        worker.execute {
            pic = scene.runs.pistes.flatMap { p ->
                scene.draped[p.key].orEmpty().map { l ->
                    val n = l.size / 3; val xy = FloatArray(n * 2); val ok = BooleanArray(n)
                    for (i in 0 until n) { val s = v.project(l[i * 3].toDouble(), l[i * 3 + 2].toDouble(), l[i * 3 + 1].toDouble()); xy[i * 2] = s.x; xy[i * 2 + 1] = s.y; ok[i] = v.visible(s) }
                    PicLine(p.key, xy, ok)
                }
            }
        }
    }

    // ---- the view: zoom (1 = the fit) round the picture's point (cx, cy), the raise, and the screen
    private var z by mutableFloatStateOf(1f)
    private var cx by mutableFloatStateOf(hero.w / 2)
    private var cy by mutableFloatStateOf(hero.h / 2)
    private var raise by mutableFloatStateOf(0f)
    private var w = 0f; private var h = 0f
    private var scope: CoroutineScope? = null
    private var cam: Job? = null
    private fun place(): Place = PeakGeo.free(w, h, hero, z, Offset(cx, cy)).let { Place(it.k, it.x, it.y - raise) }

    // ---- the look
    private var epoch by mutableStateOf(System.currentTimeMillis())
    private var quietA by mutableFloatStateOf(1f)
    private var focusOn by mutableStateOf<LiveRenderer.Focus?>(null)
    private var lensLines by mutableStateOf<Map<String, LensStroke>?>(null)
    private var lensSeq = 0

    // ---- the chosen run
    private var chosen by mutableStateOf<String?>(null)
    private var chosenAt = 0L
    private var frozen: Float? = null
    private var tick by mutableStateOf(0L)
    private var drawnOnce = false
    private var turned = 0f; private var told = false

    /** a run that this picture cannot show well (the Kobi side, or no marking in the designer's layer): the live model takes it */
    var onNeedLive: ((String) -> Unit)? = null
    /** a two-finger turn tried on the picture (Z03's hint) */
    var onRotateTry: (() -> Unit)? = null

    override var onChoose: ((String?) -> Unit)? = null
    override var onTap: ((Float, Float) -> Unit)? = null
    override var onPreTap: ((Float, Float) -> Boolean)? = null
    @Volatile override var wake: (() -> Unit)? = null
    override var onTapAt: ((Float, Float) -> Unit)? = null
    override var onMoment: ((Float, Float, Float) -> Unit)? = null

    // ---- where things are

    private fun onScreen(p: PeakContract.Screen): FloatArray = place().on(Offset(p.x, p.y)).let { floatArrayOf(it.x, it.y) }

    override fun toScreen(x: Float, y: Float, up: Float): FloatArray? {
        if (w == 0f) return null
        val s = v.project(x.toDouble(), y.toDouble(), (scene.terrain.elev(x, y) + up).toDouble())
        if (s.x < -v.w || s.x > 2 * v.w || s.y < -v.h || s.y > 2 * v.h) return null
        return onScreen(s)
    }

    override fun screenTop(key: String): FloatArray? {
        if (w == 0f) return null
        var best: FloatArray? = null
        for (l in scene.draped[key].orEmpty()) for (i in 0 until l.size / 3) if (best == null || l[i * 3 + 1] > best[1]) best = floatArrayOf(l[i * 3], l[i * 3 + 1], l[i * 3 + 2])
        val b = best ?: return null
        return onScreen(v.project(b[0].toDouble(), b[2].toDouble(), b[1].toDouble()))
    }

    /** the names and stations are the designer's layer's own here (drawn in [Draw]), never twice */
    override fun overlay(labels: List<PeakData.Label>) = LiveView.Overlay(emptyList(), FloatArray(0))
    /** the K11 marks are the designer's layer's own here */
    override fun marks(): LiveView.Marks? = null
    override fun chosenMs(): Float = frozen ?: if (chosen == null) 0f else (System.currentTimeMillis() - chosenAt).toFloat().coerceAtMost(2000f)
    override val drawn: Boolean get() = drawnOnce

    override fun northDeg(): Float {
        val a = toScreen(scene.terrain.x0 / 2 + scene.terrain.x1 / 2, scene.terrain.y0 / 2 + scene.terrain.y1 / 2) ?: return 0f
        val b = toScreen(scene.terrain.x0 / 2 + scene.terrain.x1 / 2, scene.terrain.y0 / 2 + scene.terrain.y1 / 2 - 400f) ?: return 0f
        return Math.toDegrees(atan2((b[0] - a[0]).toDouble(), (a[1] - b[1]).toDouble())).toFloat()
    }
    override fun awayFromHome(): Boolean = z > 1.03f || abs(cx - hero.w / 2) > 4f || abs(cy - hero.h / 2) > 4f
    override fun onKobiSide() = false

    /** the screen lines of the named runs as the picture draws them now (the contract's, in screen pixels) */
    private fun screenLines(): List<StagePick.Line> = linesOn(v, place())

    override fun spotOf(key: String): FloatArray? {
        if (w == 0f) return null
        val lines = screenLines(); val r = StagePick.RADIUS_DP * density; val m = 48 * density
        var best: FloatArray? = null; var bestD = r
        for (l in lines) if (l.key == key) for (i in 0 until l.xy.size / 2) {
            val sx = l.xy[i * 2]; val sy = l.xy[i * 2 + 1]
            if (sx < m || sx > w - m || sy < h * .18f || sy > h * .62f || StagePick.nearest(lines, sx, sy, r) != key) continue
            var dd = Float.MAX_VALUE
            for (o in lines) if (o.key != key) for (j in 0 until o.xy.size / 2) dd = min(dd, hypot(o.xy[j * 2] - sx, o.xy[j * 2 + 1] - sy))
            if (dd > bestD) { bestD = dd; best = floatArrayOf(sx, sy) }
        }
        return best
    }

    // ---- choosing

    /** whether the front picture shows less than half of [key], or the designer's layer has no marking for it */
    fun needsLive(key: String): Boolean {
        if (key !in hero.sel) return true
        val ls = pic?.filter { it.key == key } ?: return false
        var n = 0; var seen = 0
        for (l in ls) for (s in l.seen) { n++; if (s) seen++ }
        return n > 0 && seen * 2 < n
    }

    override fun choose(key: String?) {
        frozen = null
        if (key != null && key != chosen) chosenAt = System.currentTimeMillis()
        chosen = key?.takeIf { it in hero.sel }
        tick++
    }

    override fun pickKey(key: String) {
        if (needsLive(key)) { Qa.log("redesign picture live for $key"); onNeedLive?.invoke(key); return }
        choose(key); onChoose?.invoke(key); frameKey(key, LiveLimits.CHOSEN_FREE, "redesign picture framed $key")
    }

    override fun moment(key: String, ms: Float) {
        choose(key); frozen = ms; onChoose?.invoke(key)
        screenTop(key)?.let { onMoment?.invoke(it[0], it[1], ms) }
    }

    private fun tapAt(x: Float, y: Float) {
        onTap?.let { it(x, y); return }
        wake?.let { it(); return }
        if (onPreTap?.invoke(x, y) == true) return
        val k = StagePick.nearest(screenLines(), x, y, StagePick.RADIUS_DP * density)
        Qa.log("redesign picture tap ${k ?: "none"}")
        if (k != null && needsLive(k)) { onNeedLive?.invoke(k); return }
        choose(k); onChoose?.invoke(k)
        if (k != null) { onTapAt?.invoke(x, y); frameKey(k, LiveLimits.CHOSEN_FREE, "redesign picture framed $k") }
    }

    // ---- the view

    private fun glide(z1: Float, c1: Offset, log: String?) {
        val z0 = z; val c0 = Offset(cx, cy)
        val zt = z1.coerceIn(1f, MAX_Z)
        cam?.cancel()
        val sc = scope
        if (sc == null || reduced) { z = zt; cx = c1.x; cy = c1.y; settle(); log?.let { Qa.log(it) }; Qa.log("redesign frame done"); return }
        cam = sc.launch {
            val a = Animatable(0f)
            a.animateTo(1f, tween(600, easing = EASE)) { z = z0 + (zt - z0) * value; cx = c0.x + (c1.x - c0.x) * value; cy = c0.y + (c1.y - c0.y) * value }
            settle(); Qa.log("redesign frame done")
        }
        log?.let { Qa.log(it) }
    }
    /** the centre held where the picture still covers the screen */
    private fun settle() { if (w > 0f) PeakGeo.centre(w, h, PeakGeo.free(w, h, hero, z, Offset(cx, cy))).let { cx = it.x; cy = it.y } }

    /** a box of the picture's points fitted between [top] and [bottom] of the screen (fractions), raised [sh] px */
    private fun frameBox(x0: Float, y0: Float, x1: Float, y1: Float, top: Float, bottom: Float, sh: Float, log: String?) {
        if (w == 0f || x0 > x1) return
        val kFit = PeakGeo.fit(w, h, hero)
        val pad = 18f
        val bw = (x1 - x0 + 2 * pad).coerceAtLeast(1f); val bh = (y1 - y0 + 2 * pad).coerceAtLeast(1f)
        val k = min(w / bw, (bottom - top).coerceIn(.2f, 1f) * h / bh).coerceIn(kFit, kFit * MAX_Z)
        val mid = ((top + bottom) / 2f) * h + sh
        glide(k / kFit, Offset((x0 + x1) / 2, (y0 + y1) / 2 - (mid - h / 2) / k), log)
    }

    private fun keysBox(keys: Set<String>): FloatArray? {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (r in v.runs) if (r.key in keys) for (l in r.l) for (i in 0 until l.size / 2) { x0 = min(x0, l[i * 2]); x1 = max(x1, l[i * 2]); y0 = min(y0, l[i * 2 + 1]); y1 = max(y1, l[i * 2 + 1]) }
        return if (x0 > x1) null else floatArrayOf(x0, y0, x1, y1)
    }
    private val guard get() = TOP_GUARD_DP * density / h.coerceAtLeast(1f)

    private fun frameKey(key: String, free: Float, log: String) { keysBox(setOf(key))?.let { b -> frameBox(b[0], b[1], b[2], b[3], guard, free, 0f, log) } }

    override fun home() { glide(1f, Offset(hero.w / 2, hero.h / 2), "redesign picture home") }
    override fun backToFront() = home()
    override fun frameRunAbove(key: String, free: Float?) = frameKey(key, free ?: LiveLimits.CHOSEN_FREE, "redesign picture framed above $key")
    override fun frameLift(ends: FloatArray, free: Float, log: String) {
        val a = v.project(ends[0].toDouble(), ends[1].toDouble(), scene.terrain.elev(ends[0], ends[1]).toDouble())
        val b = v.project(ends[2].toDouble(), ends[3].toDouble(), scene.terrain.elev(ends[2], ends[3]).toDouble())
        frameBox(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y), guard, free, 0f, log)
    }
    override fun showRun(key: String, top: Float, bottom: Float, shift: Float) {
        if (needsLive(key)) return
        keysBox(setOf(key))?.let { b -> frameBox(b[0], b[1], b[2], b[3], top, bottom, shift, "redesign shelf framed $key") }
    }
    override fun showKeys(keys: Set<String>, top: Float, bottom: Float, shift: Float, log: String) {
        keysBox(keys)?.let { b -> frameBox(b[0], b[1], b[2], b[3], top, bottom, shift, log) }
    }
    override fun center(x: Float, y: Float, edge: Float) {
        val p = toScreen(x, y)
        if (p != null && p[0] in edge..(w - edge) && p[1] in edge..(h - edge)) return
        val s = v.project(x.toDouble(), y.toDouble(), scene.terrain.elev(x, y).toDouble())
        glide(z, Offset(s.x, s.y), "redesign where centred")
    }
    private class View(val z: Float, val c: Offset)
    override fun viewNow(): Any = View(z, Offset(cx, cy))
    override fun backTo(s: Any, log: String) { if (s is View) glide(s.z, s.c, log) }
    /** the village comes in over the picture by opacity (LiveVillage's way with no flight) */
    override fun toVillage(pose: FloatArray) = false
    override fun fromVillage() {}

    // ---- the look

    override fun setTime(epochMs: Long) { epoch = epochMs }
    override val lightTime: Long get() = epoch
    override fun sunUp(): Boolean = Sun.position(epoch).second > 0.0
    @Volatile private var shades: Pair<Long, Map<String, Float>>? = null
    @Volatile private var shadeAsked = -1L
    /** "in the sun now" (K07, K08): the share of each run out of the mountain's shadow, as the live model works it out */
    override fun sunShares(): Map<String, Float>? {
        if (!sunUp()) return null
        val key = epoch / 600_000
        shades?.let { if (it.first == key) return it.second }
        if (shadeAsked != key && !worker.isShutdown) { shadeAsked = key; val e = epoch; worker.execute { shades = key to shareOf(e); tick++ } }
        return null
    }
    private fun shadowAt(e: Long): FloatArray = Sun.shadows(scene.terrain, Sky.at(e).sunDir)
    private fun shareOf(e: Long): Map<String, Float> {
        val t = scene.terrain; val sh = shadowAt(e)
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
    override fun setPal(p: PeakData.Pal?) {}

    /** a lens's line on the picture: a colour per point (ARGB), its width in dp, its opacity, dashed or not */
    class LensStroke(val col: IntArray, val width: Float, val alpha: Float, val dashed: Boolean)
    override fun setLens(kind: LensKind?, night: Boolean, runOpen: ((io.github.pini236.skiapp.data.Piste) -> Boolean?)?, snow: List<Pair<Float, Double>>?) {
        val seq = ++lensSeq
        if (kind == null) { lensLines = null; return }
        if (worker.isShutdown) return
        val e = epoch
        worker.execute {
            val m = lensOf(kind, night, runOpen, snow, e)
            if (seq == lensSeq) { lensLines = m; Qa.log("redesign lens ${kind.id} ${if (m == null) "empty" else "on"}") }
        }
    }
    /** the same rules as the live model's lenses (LiveView), per point of each run's draped lines */
    private fun lensOf(kind: LensKind, night: Boolean, runOpen: ((io.github.pini236.skiapp.data.Piste) -> Boolean?)?, snow: List<Pair<Float, Double>>?, e: Long): Map<String, LensStroke>? {
        fun argb(c: Long) = (0xFF000000 or c).toInt()
        val out = HashMap<String, LensStroke>(); val t = scene.terrain
        fun each(p: io.github.pini236.skiapp.data.Piste, f: (FloatArray, Int) -> Int): IntArray {
            val cols = ArrayList<Int>()
            for (l in scene.draped[p.key].orEmpty()) for (i in 0 until l.size / 3) cols += f(l, i)
            return cols.toIntArray()
        }
        when (kind) {
            LensKind.SUN -> {
                if (Sun.position(e).second <= 0.0) return null
                val sh = shadowAt(e)
                for (p in scene.runs.pistes) {
                    var n = 0; var lit = 0
                    val cols = each(p) { l, i ->
                        val c = kotlin.math.round((l[i * 3] - t.x0) / t.sx).toInt().coerceIn(0, t.nx - 1)
                        val r = kotlin.math.round((l[i * 3 + 2] - t.y0) / t.sy).toInt().coerceIn(0, t.ny - 1)
                        val sun = sh[r * t.nx + c] < .5f; n++; if (sun) lit++
                        if (sun) argb(0xFFC93A) else argb(0xB9CCFA)
                    }
                    val most = n > 0 && lit * 2 >= n
                    out[p.key] = LensStroke(cols, if (most) 3f else 1.8f, 1f, false)
                }
            }
            LensKind.OPEN -> {
                val rule = runOpen ?: return null
                val closed = if (night) argb(0x7C8798) else argb(0x8A93A3)
                for (p in scene.runs.pistes) {
                    val plateC = plate(p.color).let { android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
                    out[p.key] = when (LensRules.open(rule(p))) {
                        LensRules.Open.OPEN -> LensStroke(each(p) { _, _ -> plateC }, 3f, 1f, false)
                        LensRules.Open.CLOSED -> LensStroke(each(p) { _, _ -> closed }, 1.8f, 1f, true)
                        LensRules.Open.UNKNOWN -> LensStroke(each(p) { _, _ -> plateC }, 1.8f, .45f, false)
                    }
                }
            }
            LensKind.SLOPE -> for (p in scene.runs.pistes) {
                val sl = scene.draped[p.key].orEmpty().map { scene.lineSlopes(it) }
                val cols = ArrayList<Int>()
                for ((li, l) in scene.draped[p.key].orEmpty().withIndex()) for (i in 0 until l.size / 3) cols += io.github.pini236.skiapp.map.SlopeColors.of(sl[li][i]).let { c -> android.graphics.Color.argb(255, (c[0] * 255).toInt(), (c[1] * 255).toInt(), (c[2] * 255).toInt()) }
                out[p.key] = LensStroke(cols.toIntArray(), 3f, 1f, false)
            }
            LensKind.SNOW -> {
                val pts = snow?.takeIf { it.isNotEmpty() } ?: return null
                val cls = listOf(argb(0x9AA7B8), argb(0x7FB2FF), if (night) argb(0xB79CFF) else argb(0x6B3FD4))
                for (p in scene.runs.pistes) {
                    var most = 0
                    val cols = each(p) { l, i -> val c = LensRules.snowClass(LensRules.snowAt(l[i * 3 + 1], pts)); if (c > most) most = c; cls[c] }
                    out[p.key] = LensStroke(cols, if (most >= 1) 3f else 1.8f, 1f, false)
                }
            }
        }
        return out
    }
    override fun setFocus(f: LiveRenderer.Focus?) { focusOn = f }
    override fun setShift(px: Float) { if (px != raise) raise = px }
    override fun setQuiet(a: Float, runs: Float) { quietA = min(a, max(runs, a * .5f)) }

    // ---- the pictures

    private val images = HashMap<String, ImageBitmap>()
    private var cur: PeakData.Light? = null
    private var prev: PeakData.Light? = null
    private val fade = Animatable(1f)

    private fun decode(file: String): ImageBitmap? = runCatching {
        context.assets.open("redesign/img/$file").use { BitmapFactory.decodeStream(it) }!!.also { it.prepareToDraw() }.asImageBitmap()
    }.onFailure { Redesign.fail("picture $file") }.getOrNull()

    /** the decoded pictures go (leaving the picture for the live model, condition 3) */
    fun freePictures() { images.clear(); cur = null; prev = null }
    fun release() { worker.shutdownNow(); freePictures() }

    @Composable
    fun Draw(night: Boolean, modifier: Modifier = Modifier) {
        val sc = rememberCoroutineScope()
        DisposableEffect(Unit) { scope = sc; Qa.log("redesign picture open"); onDispose { scope = null; cam?.cancel(); freePictures(); drawnOnce = false; Qa.log("redesign picture closed") } }
        val measurer = rememberTextMeasurer()
        val unitM = stringResource(R.string.common_unit_m, "\u0000")
        val nf = remember { java.text.NumberFormat.getIntegerInstance() }
        val want = PeakLight.pick(d.lights, epoch, DayNight.Mode.AUTO)
        var shown by remember { mutableStateOf<PeakData.Light?>(null) }
        LaunchedEffect(want.name) {
            val f = hero.images.getValue(want.name)
            if (images[f] == null) withContext(Dispatchers.IO) { decode(f) }?.let { images[f] = it } ?: return@LaunchedEffect
            val old = cur
            prev = old; cur = want; shown = want
            // two at most: the new one and the one it fades from
            val keep = setOfNotNull(f, old?.let { hero.images[it.name] })
            images.keys.retainAll(keep)
            if (old != null && old.name != want.name) { fade.snapTo(0f); fade.animateTo(1f, tween(if (reduced) 1 else 600, easing = LinearEasing)) }
            prev = null; images.keys.retainAll(setOf(f)); tick++
        }
        // the chosen run's moment (the light down it, 2 s), then still
        val ch = chosen
        LaunchedEffect(ch) { if (ch != null) while (chosenMs() < 2000f && frozen == null) withFrameNanos { tick++ } }
        val dim = remember { Animatable(0f) }
        val focused = focusOn != null || lensLines != null
        LaunchedEffect(ch, focused) { dim.animateTo(if (ch != null || focused) 1f else 0f, tween(if (reduced) 1 else if (ch != null) 600 else 300)) }
        Canvas(modifier.fillMaxSize()
            // a turn tried (two fingers turning more than 15 degrees in one gesture): the hint, once a gesture
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); turned = 0f; told = false } }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rot ->
                    turned += rot
                    if (!told && abs(turned) > 15f) { told = true; onRotateTry?.invoke() }
                    cam?.cancel()
                    val p0 = place(); val before = p0.back(centroid)
                    val kFit = PeakGeo.fit(w, h, hero)
                    val nz = (z * zoom).coerceIn(1f, MAX_Z); val k = kFit * nz
                    z = nz; cx = before.x - (centroid.x - w / 2) / k - pan.x / k; cy = before.y - (centroid.y - h / 2 + raise) / k - pan.y / k
                    settle()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { s -> tapAt(s.x, s.y) }, onDoubleTap = { s ->
                    if (chosen != null) return@detectTapGestures
                    val p = place().back(s); glide(if (z > 1.5f) 1f else 2.2f, p, null)
                })
            }
        ) {
            if (tick < 0) return@Canvas
            w = size.width; h = size.height
            val c0 = cur ?: run { drawRect(Color(0xFF9DB6D8)); return@Canvas }
            lastDm = dim.value; tools = Triple(measurer, unitM, nf)
            paint(c0, dim.value, measurer, unitM, nf)
            if (!drawnOnce) { drawnOnce = true; Qa.log("redesign picture drawn ${c0.name}") }
        }
    }

    /** the picture, the designer's layer and the lens or the focus, as the screen shows them now */
    private fun DrawScope.paint(c0: PeakData.Light, dm: Float, measurer: androidx.compose.ui.text.TextMeasurer, unitM: String, nf: java.text.NumberFormat) {
        val pl = place()
        fun img(l: PeakData.Light?, a: Float) {
            val im = l?.let { images[hero.images[it.name]] } ?: return
            drawImage(im, dstOffset = IntOffset(pl.x.toInt(), pl.y.toInt()), dstSize = IntSize((hero.w * pl.k).toInt() + 1, (hero.h * pl.k).toInt() + 1), alpha = a, filterQuality = FilterQuality.High)
        }
        val p0 = prev
        if (p0 != null && fade.value < 1f) img(p0, 1f)
        img(c0, if (p0 != null) fade.value else 1f)
        val light = if (p0 != null && fade.value < .5f) p0 else c0
        val dark = light.night
        val ch = chosen
        // the designer's layer: with a run chosen, its moment (PeakScreen's); with a lens or the shelf's focus, the
        // runs as ghosts (their moment long past: no shade over the mountain) and the lit ones on top
        val ms = if (ch != null) chosenMs() else 2600f
        layer(d, hero, pl, quietA, light, dark, z.pow(-.38f), ch, dm, 1f, ms, near = false, closeUp = false, measurer, unitM, nf, pinch = z)
        lensLines?.let { drawLens(it, pl) }
        focusOn?.let { f -> drawFocus(f, pl, dark) }
    }

    private var lastDm = 0f
    private var tools: Triple<androidx.compose.ui.text.TextMeasurer, String, java.text.NumberFormat>? = null

    /**
     * The screen as the picture shows it now, in one bitmap the size of the screen (condition 3: the swap to the live
     * model fades from this, and the decoded pictures go); null before the first frame.
     */
    fun snapshot(): ImageBitmap? {
        val c0 = cur ?: return null; val t = tools ?: return null
        if (w <= 0f || h <= 0f) return null
        val bmp = ImageBitmap(w.toInt(), h.toInt())
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(density), androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(bmp), androidx.compose.ui.geometry.Size(w, h)) { paint(c0, lastDm, t.first, t.second, t.third) }
        return bmp
    }

    private fun DrawScope.drawLens(m: Map<String, LensStroke>, pl: Place) {
        val ls = pic ?: return
        val counters = HashMap<String, Int>()
        for (l in ls) {
            val s = m[l.key] ?: continue
            var at = counters[l.key] ?: 0
            val n = l.xy.size / 2
            for (i in 0 until n - 1) {
                if (!l.seen[i] || !l.seen[i + 1]) continue
                if (s.dashed && (i / 2) % 2 == 1) continue
                val c = Color(s.col.getOrElse(at + i) { s.col.lastOrNull() ?: 0 })
                drawLine(c, pl.on(Offset(l.xy[i * 2], l.xy[i * 2 + 1])), pl.on(Offset(l.xy[i * 2 + 2], l.xy[i * 2 + 3])), s.width * density, StrokeCap.Round, alpha = s.alpha * quietA)
            }
            at += n; counters[l.key] = at
        }
    }

    private fun DrawScope.drawFocus(f: LiveRenderer.Focus, pl: Place, dark: Boolean) {
        for (r in v.runs) if (r.key in f.lit) {
            val col = scene.runs.pistes.firstOrNull { it.key == r.key }?.let { plate(it.color) } ?: Color.White
            for (l in r.l) for (i in 0 until l.size / 2 - 1) {
                val a = pl.on(Offset(l[i * 2], l[i * 2 + 1])); val b = pl.on(Offset(l[i * 2 + 2], l[i * 2 + 3]))
                drawLine(if (dark) Color(0xFF081020) else Color.White, a, b, f.case * density, StrokeCap.Round)
                drawLine(col, a, b, f.core * density, StrokeCap.Round)
            }
        }
    }

    companion object {
        const val MAX_Z = 3.2f
        private val EASE = CubicBezierEasing(.2f, 0f, 0f, 1f)

        /** the contract's named run lines on the screen through [pl] (the picture's points to pixels) */
        fun linesOn(v: PeakContract.View, pl: Place): List<StagePick.Line> = v.runs.filter { it.named }.flatMap { r ->
            r.l.map { l -> StagePick.Line(r.key, FloatArray(l.size) { i -> if (i % 2 == 0) l[i] * pl.k + pl.x else l[i] * pl.k + pl.y }, null) }
        }
    }
}

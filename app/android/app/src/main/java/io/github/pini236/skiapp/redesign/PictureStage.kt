package io.github.pini236.skiapp.redesign

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
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
 * The Kobi side (P.17, 27.11.2026, design/handoff/2026-11-27-redesign-kobi-side/): a second face of the same stage, with
 * its own picture (downloaded, [KobiSet]), its own projection and its own layer; a run belongs to the face that shows
 * more of it, and choosing one of the other face turns to it (450 ms, the front moves 36 pt and fades, the side comes in).
 * Without a picture of the side yet the run goes to the live model for a moment, as before.
 *
 * Memory (condition 3): at most two decoded pictures, only while [Draw] is on the screen; leaving it frees them.
 */
internal class PictureStage(private val context: Context, private val scene: MapScene, private val d: PeakData, private val front: PeakContract.View, private val kobiC: PeakContract.View? = null) : Stage {
    enum class Face { FRONT, KOBI }
    /** the Kobi side's live layer (parsed off the main thread) and its pictures */
    @Volatile private var kobiHero: PeakData.View? = null
    private var kobiReady by mutableStateOf(false)
    private val kobiSet: KobiSet? = kobiC?.takeIf { !it.packaged && it.rev != null }?.let { KobiSet(context, it) }
    /** the face on the screen now */
    private var side by mutableStateOf(Face.FRONT)
    private fun heroOf(f: Face): PeakData.View = if (f == Face.KOBI) kobiHero ?: d.hero else d.hero
    private fun viewOf(f: Face): PeakContract.View = if (f == Face.KOBI) kobiC ?: front else front
    private val hero: PeakData.View get() = heroOf(side)
    private val v: PeakContract.View get() = viewOf(side)
    private val density = context.resources.displayMetrics.density
    private val reduced = Motion.reduced(context)

    /** a run's draped lines in the picture's points, and whether each point is seen there (worked out once, off the main thread) */
    class PicLine(val key: String, val xy: FloatArray, val seen: BooleanArray)
    @Volatile private var picF: List<PicLine>? = null
    @Volatile private var picK: List<PicLine>? = null
    private val pic: List<PicLine>? get() = if (side == Face.KOBI) picK else picF
    private val worker = Executors.newSingleThreadExecutor()
    private fun linesFor(cv: PeakContract.View): List<PicLine> = scene.runs.pistes.flatMap { p ->
        scene.draped[p.key].orEmpty().map { l ->
            val n = l.size / 3; val xy = FloatArray(n * 2); val ok = BooleanArray(n)
            for (i in 0 until n) { val s = cv.project(l[i * 3].toDouble(), l[i * 3 + 2].toDouble(), l[i * 3 + 1].toDouble()); xy[i * 2] = s.x; xy[i * 2 + 1] = s.y; ok[i] = cv.visible(s) }
            PicLine(p.key, xy, ok)
        }
    }
    init {
        worker.execute {
            picF = linesFor(front)
            if (kobiC != null) {
                picK = linesFor(kobiC)
                kobiHero = runCatching { PeakData.loadKobiView(context, d.geo) }.onFailure { Redesign.fail("kobi layer") }.getOrNull()
                kobiSet?.scan()
                kobiReady = kobiHero != null
                Qa.log("redesign kobi init set=${kobiSet != null} rev=${kobiC.rev} path=${kobiC.path} images=${kobiC.images.size} layer=${kobiHero != null} have=${kobiSet?.arrived}")
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
        if (!scene.terrain.contains(x, y)) return null // a point outside the model is never drawn at the picture's edge (UX part 7)
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
    override fun onKobiSide() = side == Face.KOBI
    /** for the layers: the Kobi side is the face on the screen */
    val onKobi: Boolean get() = side == Face.KOBI
    /** the side can be shown now: its layer is parsed and at least one picture is here */
    val kobiUsable: Boolean get() = kobiReady && kobiSet?.usable() == true
    /** the screen point just above where the Kobi line peeks at the top of the front picture; null elsewhere */
    fun kobiTag(): FloatArray? {
        if (w == 0f || side != Face.FRONT || kobiC == null) return null
        var bx = 0f; var by = Float.MAX_VALUE
        for (r in front.runs) if (r.key == "Kobi") for (l in r.l) for (i in 0 until l.size / 2) if (l[i * 2 + 1] < by) { by = l[i * 2 + 1]; bx = l[i * 2] }
        if (by == Float.MAX_VALUE) return null
        return onScreen(PeakContract.Screen(bx, by, 0f))
    }

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

    private fun seenIn(lines: List<PicLine>?, key: String): Float {
        var n = 0; var seen = 0
        for (l in lines.orEmpty()) if (l.key == key) for (s in l.seen) { n++; if (s) seen++ }
        return if (n == 0) 0f else seen.toFloat() / n
    }
    /** the face that shows more of [key] (the front when equal, and while the lines are not worked out yet) */
    fun faceFor(key: String): Face = if (kobiC != null && seenIn(picK, key) > seenIn(picF, key)) Face.KOBI else Face.FRONT

    /** whether this stage cannot show [key]: the face it belongs to has no marking for it, no picture yet (the side), or shows less than half of it */
    fun needsLive(key: String): Boolean = whyLive(key).also { if (it != null) Qa.log("redesign picture live because $it for $key") } != null

    /** why the picture cannot show [key] (null = it can): one reason per condition, logged, so a fall to live can be told apart */
    fun whyLive(key: String): String? {
        val f = faceFor(key)
        if (f == Face.KOBI && !kobiReady) return "kobi layer not ready"
        if (f == Face.KOBI && kobiSet?.usable() != true) return "kobi pictures not here (set=${kobiSet != null}, arrived=${kobiSet?.arrived})"
        if (key !in heroOf(f).sel) return "no marking on ${f.name}"
        val ls = (if (f == Face.KOBI) picK else picF) ?: return null
        var n = 0; var seen = 0
        for (l in ls) if (l.key == key) for (s in l.seen) { n++; if (s) seen++ }
        return if (n > 0 && seen * 2 < n) "less than half seen on ${f.name} ($seen/$n)" else null
    }

    override fun choose(key: String?) {
        frozen = null
        if (key != null && key != chosen) chosenAt = System.currentTimeMillis()
        chosen = key?.takeIf { it in hero.sel }
        tick++
    }

    /** the Kobi pictures are on their way: a tap on the side waits for the first one (up to 5 s) instead of going live at once */
    @Volatile private var kobiFetching = false

    override fun pickKey(key: String) {
        val ks = kobiSet; val sc = scope
        if (ks != null && sc != null && kobiFetching && !kobiUsable && faceFor(key) == Face.KOBI) {
            Qa.log("redesign kobi waiting for the first picture")
            sc.launch {
                val got = withTimeoutOrNull(5000) { snapshotFlow { ks.arrived }.first { it > 0 } }
                Qa.log("redesign kobi wait ${if (got != null) "done" else "timed out"}")
                pickKeyNow(key)
            }
            return
        }
        pickKeyNow(key)
    }

    private fun pickKeyNow(key: String) {
        Qa.log("redesign picture pick $key face=${faceFor(key)} side=$side lines=${picK != null}/${picF != null}")
        if (needsLive(key)) { Qa.log("redesign picture live for $key"); onNeedLive?.invoke(key); return }
        val f = faceFor(key)
        if (f != side) { turn(f, key); return }
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
        if (k != null && faceFor(k) != side) { onTapAt?.invoke(x, y); turn(faceFor(k), k); return }
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
    /** from the Kobi side: the turn back to the front; on the front: the opening view */
    override fun backToFront() { if (side == Face.KOBI) turn(Face.FRONT, null) else home() }
    override fun frameRunAbove(key: String, free: Float?) = frameKey(key, free ?: LiveLimits.CHOSEN_FREE, "redesign picture framed above $key")
    override fun frameLift(ends: FloatArray, free: Float, log: String) {
        val a = v.project(ends[0].toDouble(), ends[1].toDouble(), scene.terrain.elev(ends[0], ends[1]).toDouble())
        val b = v.project(ends[2].toDouble(), ends[3].toDouble(), scene.terrain.elev(ends[2], ends[3]).toDouble())
        frameBox(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y), guard, free, 0f, log)
    }
    override fun showRun(key: String, top: Float, bottom: Float, shift: Float) {
        if (needsLive(key) || faceFor(key) != side) return
        keysBox(setOf(key))?.let { b -> frameBox(b[0], b[1], b[2], b[3], top, bottom, shift, "redesign shelf framed $key") }
    }
    override fun showKeys(keys: Set<String>, top: Float, bottom: Float, shift: Float, log: String) {
        keysBox(keys)?.let { b -> frameBox(b[0], b[1], b[2], b[3], top, bottom, shift, log) }
    }
    override fun center(x: Float, y: Float, edge: Float) {
        if (!scene.terrain.contains(x, y)) return
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

    /** decoded pictures by key: "f:<light>" (packed, the front) or "k:<light>" (downloaded, the Kobi side); at most two at once */
    private val images = HashMap<String, ImageBitmap>()
    private var cur: PeakData.Light? = null
    private var curKey: String? = null
    private var prev: PeakData.Light? = null
    private var prevKey: String? = null
    private val fade = Animatable(1f)

    /** the picture to draw for [light] on [f]: the front's own, or the Kobi side's nearest one that is here; null if none */
    private fun keyFor(f: Face, light: String): String? = when (f) {
        Face.FRONT -> if (d.hero.images[light] != null) "f:$light" else null
        Face.KOBI -> kobiSet?.nearest(light)?.let { "k:$it" }
    }

    private fun decodeKey(key: String): ImageBitmap? = runCatching {
        val name = key.substring(2)
        val bmp = if (key[0] == 'f') context.assets.open("redesign/${d.hero.images.getValue(name)}").use { BitmapFactory.decodeStream(it) }
        else BitmapFactory.decodeFile(kobiSet!!.file(name)!!.path)
        bmp!!.also { it.prepareToDraw() }.asImageBitmap()
    }.onFailure { Redesign.fail("picture $key") }.getOrNull()

    /** the decoded pictures go (leaving the picture for the live model, condition 3); the next time starts on the front */
    fun freePictures() { images.clear(); cur = null; prev = null; curKey = null; prevKey = null; out = null; side = Face.FRONT }
    fun release() { worker.shutdownNow(); freePictures() }

    // ---- the turn between the faces (Z08): the face that leaves moves 36 pt away and fades, the other comes in from that side
    private class Out(val face: Face, val key: String?, val light: PeakData.Light?, val pl: Place)
    private var out by mutableStateOf<Out?>(null)
    private val slide = Animatable(0f)
    private var slideDir = -1f

    /** turns to [to] and then chooses [then]; if the picture of that face is not here the run goes to the live model for a moment */
    private fun turn(to: Face, then: String?) {
        if (to == side) { if (then != null) { choose(then); onChoose?.invoke(then) }; return }
        if (out != null) return
        val want = PeakLight.pick(d.lights, epoch, DayNight.Mode.AUTO)
        val key = keyFor(to, want.name)
        if (key == null) { if (then != null) onNeedLive?.invoke(then); return }
        // the run that was chosen and does not belong to the face we go to is let go (its sheet closes), unless it is the one asked for
        val was = chosen
        if (was != null && was != then && faceFor(was) != to) { choose(null); onChoose?.invoke(null) }
        val sc = scope
        val from = Out(side, curKey, cur, place())
        cam?.cancel()
        fun arrive() {
            out = from; side = to; prev = null; prevKey = null; cur = want; curKey = key; z = 1f; cx = hero.w / 2; cy = hero.h / 2
            slideDir = if (to == Face.KOBI) -1f else 1f
            then?.let { choose(it); onChoose?.invoke(it) }
        }
        if (sc == null) { // not on the screen: the next Draw loads the picture of this face
            side = to; z = 1f; cx = hero.w / 2; cy = hero.h / 2; then?.let { choose(it); onChoose?.invoke(it) }; return
        }
        sc.launch {
            if (images[key] == null) withContext(Dispatchers.IO) { decodeKey(key) }?.let { images[key] = it }
            if (images[key] == null) { if (then != null) onNeedLive?.invoke(then); return@launch }
            slide.snapTo(0f); arrive(); tick++
            slide.animateTo(1f, tween(if (reduced) 1 else 450, easing = FastOutSlowInEasing)) { tick++ }
            out = null; images.keys.retainAll(setOf(key)); tick++
            Qa.log("redesign picture turned ${to.name.lowercase()}")
        }
    }

    @Composable
    fun Draw(night: Boolean, modifier: Modifier = Modifier) {
        val sc = rememberCoroutineScope()
        DisposableEffect(Unit) { scope = sc; Qa.log("redesign picture open"); onDispose { scope = null; cam?.cancel(); freePictures(); drawnOnce = false; Qa.log("redesign picture closed") } }
        val measurer = rememberTextMeasurer()
        val unitM = stringResource(R.string.common_unit_m, "\u0000")
        val nf = remember { java.text.NumberFormat.getIntegerInstance() }
        val want = PeakLight.pick(d.lights, epoch, DayNight.Mode.AUTO)
        var shown by remember { mutableStateOf<PeakData.Light?>(null) }
        val arrived = kobiSet?.arrived ?: 0
        LaunchedEffect(want.name, side, arrived, out == null) {
            if (out != null) return@LaunchedEffect // a turn is under way; it sets its own picture
            val key = keyFor(side, want.name) ?: return@LaunchedEffect
            if (key == curKey && cur?.name == want.name) return@LaunchedEffect
            if (images[key] == null) withContext(Dispatchers.IO) { decodeKey(key) }?.let { images[key] = it } ?: return@LaunchedEffect
            val old = cur; val oldKey = curKey
            prev = old; prevKey = oldKey; cur = want; curKey = key; shown = want
            // two at most: the new one and the one it fades from
            images.keys.retainAll(setOfNotNull(key, oldKey))
            if (old != null && oldKey != key) { fade.snapTo(0f); fade.animateTo(1f, tween(if (reduced) 1 else 600, easing = LinearEasing)) }
            prev = null; prevKey = null; images.keys.retainAll(setOf(key)); tick++
        }
        // the Kobi side's pictures come in the background, once the first picture is drawn and while the app is on the screen
        // (the architect's conditions: the current light first, resumable, never in the background)
        // (the fetch needs only the set: it no longer waits for the layer to parse, and it tries again after a failure)
        LaunchedEffect(kobiSet) {
            val ks = kobiSet ?: run { Qa.log("redesign kobi fetch: no set"); return@LaunchedEffect }
            while (!drawnOnce) withFrameNanos { }
            val lc = KobiSet.lifecycleOf(context)
            Qa.log("redesign kobi fetch start lifecycle=${lc != null}")
            suspend fun round(active: () -> Boolean) {
                for (attempt in 1..4) {
                    kobiFetching = true
                    val ok = try { ks.fetchAll(want.name, active) } finally { kobiFetching = false }
                    if (ok || !active()) return
                    delay(3000L * attempt)
                }
            }
            if (lc == null) round { true }
            else lc.repeatOnLifecycle(Lifecycle.State.STARTED) { round { lc.currentState.isAtLeast(Lifecycle.State.STARTED) } }
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
                    if (out != null) return@detectTransformGestures
                    cam?.cancel()
                    val p0 = place(); val before = p0.back(centroid)
                    val kFit = PeakGeo.fit(w, h, hero)
                    val nz = (z * zoom).coerceIn(1f, MAX_Z); val k = kFit * nz
                    z = nz; cx = before.x - (centroid.x - w / 2) / k - pan.x / k; cy = before.y - (centroid.y - h / 2 + raise) / k - pan.y / k
                    // B1 (26.11.2026): at the fit the picture does not move at all (no pan up or down); it moves only once zoomed in
                    if (nz <= 1.001f) { z = 1f; cx = hero.w / 2; cy = hero.h / 2 }
                    settle()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { s -> if (out == null) tapAt(s.x, s.y) }, onDoubleTap = { s ->
                    if (chosen != null || out != null) return@detectTapGestures
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

    /** the picture, the designer's layer and the lens or the focus, as the screen shows them now (in a turn, both faces) */
    private fun DrawScope.paint(c0: PeakData.Light, dm: Float, measurer: androidx.compose.ui.text.TextMeasurer, unitM: String, nf: java.text.NumberFormat) {
        val o = out
        if (o != null) {
            val t = slide.value; val m = 36f * density
            paintFace(o.face, o.key, null, null, 1f, o.light ?: c0, o.pl, 1f - t, slideDir * m * t, 0f, null, 1f, measurer, unitM, nf)
            paintFace(side, curKey, prevKey, prev, fade.value, c0, place(), t, -slideDir * m * (1f - t), dm, chosen, z, measurer, unitM, nf)
            return
        }
        val pl = place()
        paintFace(side, curKey, prevKey, prev, fade.value, c0, pl, 1f, 0f, dm, chosen, z, measurer, unitM, nf)
        lensLines?.let { drawLens(it, pl) }
        focusOn?.let { f -> drawFocus(f, pl, (if (prev != null && fade.value < .5f) prev else c0)?.night == true) }
    }

    /** one face: its picture (fading from [pk] while the light changes), its layer; [a] the whole face's opacity, [dx] its shift */
    private fun DrawScope.paintFace(f: Face, key: String?, pk: String?, p0: PeakData.Light?, fv: Float, c0: PeakData.Light, pl0: Place, a: Float, dx: Float, dm: Float, ch: String?,
                                    zoom: Float, measurer: androidx.compose.ui.text.TextMeasurer, unitM: String, nf: java.text.NumberFormat) {
        val pl = Place(pl0.k, pl0.x + dx, pl0.y); val hv = heroOf(f)
        fun img(k: String?, al: Float) {
            val im = k?.let { images[it] } ?: return
            drawImage(im, dstOffset = IntOffset(pl.x.toInt(), pl.y.toInt()), dstSize = IntSize((hv.w * pl.k).toInt() + 1, (hv.h * pl.k).toInt() + 1), alpha = al * a, filterQuality = FilterQuality.High)
        }
        if (p0 != null && pk != null && fv < 1f) img(pk, 1f)
        img(key, if (p0 != null && pk != null) fv else 1f)
        val light = if (p0 != null && fv < .5f) p0 else c0
        val dark = light.night
        // the designer's layer: with a run chosen, its moment (PeakScreen's); with a lens or the shelf's focus, the
        // runs as ghosts (their moment long past: no shade over the mountain) and the lit ones on top
        val ms = if (ch != null) chosenMs() else 2600f
        layer(d, hv, pl, quietA * a, light, dark, zoom.pow(-.38f), ch, dm, 1f, ms, near = false, closeUp = false, measurer, unitM, nf, pinch = zoom)
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

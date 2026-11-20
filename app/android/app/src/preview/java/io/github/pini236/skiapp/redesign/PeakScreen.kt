package io.github.pini236.skiapp.redesign

import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The redesign POC (decisions 83, 84, 85): round 3's mountain as the first screen, by the product designer's handoff
 * (design/handoff/2026-11-20-redesign-poc/README.md; the values below are its values). The picture of the sun's light
 * now, and over it, live, the small real things, the runs, the lifts and the names. A touch on a run is the moment of
 * choosing (K09): a ring from the finger; the rest of the mountain into shade and the light running down the run in
 * its slope's colours (0 to 600 ms); for the twelve runs on the Tatra side the view comes into the close-up picture
 * (300 to 900); the sign is pinned; the sheet rises (900). Then the chosen run: a line of light by day, beads of light
 * by night (K11 a and c). Test build only.
 */

private val Ease = CubicBezierEasing(.2f, 0f, 0f, 1f)
private const val REVEAL = 600f
private val ACCENT_DAY = Color(0xFF6B3FD4)
private val ACCENT_NIGHT = Color(0xFFB79CFF)
private val SHEET_DP = 236.dp

private class Glass(val night: Boolean) {
    val bg = if (night) Color(0xD70F1828) else Color(0xDBFFFFFF)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val ink2 = if (night) Color(0xFFA9B8CC) else Color(0xFF40556A)
    val line = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val acc = if (night) ACCENT_NIGHT else ACCENT_DAY
}

/** The pictures, decoded when needed (1170 px wide, ~12 MB each in memory): the architect's ceiling is two, plus the close-up. */
private class Pictures(private val context: android.content.Context) {
    private val cache = LinkedHashMap<String, ImageBitmap>(8, .75f, true)
    @Synchronized fun peek(file: String): ImageBitmap? = cache[file]
    fun get(file: String): ImageBitmap {
        peek(file)?.let { return it }
        val bmp = context.assets.open("redesign/$file").use { BitmapFactory.decodeStream(it) }.also { it.prepareToDraw() }.asImageBitmap()
        synchronized(this) { cache[file] = bmp; while (cache.size > 3) cache.remove(cache.keys.first()) }
        return bmp
    }
}

/** A picture on the screen: screen = point · k + (x, y), in pixels. */

@Composable
fun PeakScreen(mode: DayNight.Mode, haptics: Haptics, onLeave: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduced = remember { Motion.reduced(context) }
    val data by produceState<PeakData?>(null) { value = withContext(Dispatchers.IO) { PeakData.load(context) } }
    val d = data
    if (d == null) { Box(Modifier.fillMaxSize().background(Color(0xFF9DB6D8))); return }
    val pics = remember { Pictures(context) }
    val scope = rememberCoroutineScope()

    // ---- the light: by the sun now (or the hour dragged on the slider)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var scrubOpen by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val want = scrub?.let { PeakLight.pick(d.lights, PeakLight.janAt(it), DayNight.Mode.AUTO) } ?: PeakLight.pick(d.lights, now, mode)
    var cur by remember { mutableStateOf(want) }
    var prev by remember { mutableStateOf<PeakData.Light?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(want.name) {
        if (want.name == cur.name) return@LaunchedEffect
        // the new picture first, then the soft change
        withContext(Dispatchers.IO) { pics.get(d.hero.images.getValue(want.name)) }
        prev = cur; cur = want
        fade.snapTo(0f); fade.animateTo(1f, tween(if (reduced) 1 else 600, easing = LinearEasing)); prev = null
    }
    val night = if (fade.value < .5f && prev != null) prev!!.night else cur.night
    val g = remember(night) { Glass(night) }

    val img = remember { mutableStateMapOf<String, ImageBitmap>() }
    suspend fun need(file: String) { if (img[file] == null) img[file] = pics.peek(file) ?: withContext(Dispatchers.IO) { pics.get(file) } }

    // ---- the free view (no run chosen): pinch to come closer, drag to move, double tap
    var z by remember { mutableStateOf(1f) }
    var c by remember { mutableStateOf(Offset(d.hero.w / 2, d.hero.h / 2)) }
    var camJob by remember { mutableStateOf<Job?>(null) }

    // ---- the chosen run and its moment
    var key by remember { mutableStateOf<String?>(null) }
    var chosenOnce by remember { mutableStateOf(false) }
    val moment = remember { Animatable(0f) }      // ms since the touch, 0..2000
    val dim = remember { Animatable(0f) }         // the shade over the rest of the mountain
    val close = remember { Animatable(0f) }       // into the close-up picture (Tatra side) or the lift above the sheet
    var sheet by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val ring = remember { Animatable(1f) }
    val run = key?.let { d.runs[it] }
    val near = run?.near == true && key in d.zoom.sel

    LaunchedEffect(cur.name, near, key) {
        need(d.hero.images.getValue(cur.name))
        prev?.let { need(d.hero.images.getValue(it.name)) }
        if (near) need(d.zoom.images.getValue(cur.name))
        val keep = setOfNotNull(d.hero.images[cur.name], prev?.let { d.hero.images[it.name] }, if (near) d.zoom.images[cur.name] else null)
        img.keys.filter { it !in keep }.forEach { img.remove(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF9DB6D8))) {
        val W = constraints.maxWidth.toFloat(); val H = constraints.maxHeight.toFloat()
        val dp = density.density
        val sheetPx = SHEET_DP.value * dp
        // the fit (the handoff): k = max(width / 390, height / 844), centred
        val kFit = PeakGeo.fit(W, H, d.hero)
        fun free(zz: Float = z, cc: Offset = c): Place = PeakGeo.free(W, H, d.hero, zz, cc)
        // the close-up: k = max(width / 390, (height − sheet + 12) / 560), at the top
        val kz = max(W / d.zoom.w, (H - sheetPx + 12 * dp) / d.zoom.h)
        val zoomPlace = Place(kz, (W - d.zoom.w * kz) / 2, 0f)
        val fh = d.zoom.fromHero!!
        // the whole mountain inside the close-up's frame (x_zoom = x_hero · k + tx)
        val heroInZoom = Place(fh.first * kz, fh.second * kz + zoomPlace.x, fh.third * kz + zoomPlace.y)
        // a run without a close-up: the mountain rises until the run is above the sheet (at most the sheet's height − 12)
        val lift = remember(key, W, H) {
            val s = key?.let { d.hero.sel[it] }
            if (s == null || near) 0f else {
                val b = s.bounds(); val f = free(1f, Offset(d.hero.w / 2, d.hero.h / 2))
                (f.on(Offset(0f, b.bottom)).y - (H - sheetPx - 12 * dp)).coerceIn(0f, sheetPx - 12 * dp)
            }
        }
        val freeNow = free()
        val target = if (near) heroInZoom else free(1f, Offset(d.hero.w / 2, d.hero.h / 2)).let { it.copy(y = it.y - lift) }
        val hero = freeNow.lerp(target, close.value)
        val zoomIn = if (near) ((close.value - .5f) / .5f).coerceIn(0f, 1f) else 0f
        // the lines grow gently when the free view comes closer
        val wfHero = z.pow(-.4f) + (1f - z.pow(-.4f)) * close.value

        fun choose(k: String) {
            val again = key != null
            key = k; chosenOnce = true; sheet = false
            Qa.log("redesign chosen $k")
            haptics.tick(.5f)
            camJob?.cancel()
            scope.launch {
                if (reduced) {
                    moment.snapTo(2000f); close.snapTo(1f); dim.animateTo(1f, tween(200)); sheet = true; return@launch
                }
                moment.snapTo(0f)
                if (again) { close.snapTo(0f) }
                launch { dim.animateTo(1f, tween(if (again) 200 else REVEAL.toInt(), easing = LinearEasing)) }
                launch { delay(300); haptics.tick(.6f); close.animateTo(1f, tween(600, easing = Ease)) }
                launch { delay(900); sheet = true }
                moment.animateTo(2000f, tween(2000, easing = LinearEasing))
            }
        }
        fun clear() {
            if (key == null) return
            Qa.log("redesign cleared")
            sheet = false
            scope.launch {
                val t = if (reduced) 1 else 200
                launch { close.animateTo(0f, tween(if (reduced) 1 else 320, easing = Ease)) }
                dim.animateTo(0f, tween(t)); key = null; moment.snapTo(0f)
            }
            // back to the whole mountain
            z = 1f; c = Offset(d.hero.w / 2, d.hero.h / 2)
        }
        BackHandler(enabled = key != null) { clear() }
        fun hit(s: Offset): String? {
            val inZoom = zoomIn > .5f
            return PeakGeo.hit(if (inZoom) d.zoom else d.hero, d.runs.keys, if (inZoom) zoomPlace else hero, s, 22 * dp)
        }
        // the gesture handlers live across compositions (keyed on the size only), so they reach the
        // current zoom, pan and close-up through these, not through the values of the first composition
        val hitNow by rememberUpdatedState { s: Offset -> hit(s) }
        val chooseNow by rememberUpdatedState { k: String -> choose(k) }
        val clearNow by rememberUpdatedState { clear() }
        // the emulator's commands (qa.redesign, debug builds only): the free view as a pinch would leave it, and where a
        // run can be touched in what is drawn now (the script then touches it for real)
        val placeNow by rememberUpdatedState(if (zoomIn > .5f) zoomPlace else hero)
        val viewNow by rememberUpdatedState(if (zoomIn > .5f) d.zoom else d.hero)
        LaunchedEffect(RedesignQa.seq) {
            val cmd = RedesignQa.cmd
            when {
                cmd.startsWith("view:") -> cmd.removePrefix("view:").split(',').mapNotNull { it.trim().toFloatOrNull() }.takeIf { it.size == 3 }?.let { (zz, x, y) ->
                    camJob?.cancel(); z = zz.coerceIn(1f, 2.6f); c = PeakGeo.centre(W, H, free(z, Offset(x, y)))
                    Qa.log("redesign view zoom $z")
                }
                cmd.startsWith("spot:") -> {
                    val k = cmd.removePrefix("spot:")
                    val s = PeakGeo.spot(viewNow, d.runs.keys, k, placeNow, W, H, 22 * dp)
                    Qa.log(if (s == null) "redesign spot $k none" else "redesign spot $k ${s.x.toInt()} ${s.y.toInt()}")
                }
            }
        }
        LaunchedEffect(Unit) { Qa.log("redesign ready") }

        val measurer = rememberTextMeasurer()
        val nf = remember { NumberFormat.getIntegerInstance() }
        val unitM = stringResource(R.string.common_unit_m, "\u0000")

        Canvas(Modifier.fillMaxSize()
            .pointerInput(W, H) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (key != null) return@detectTransformGestures
                    camJob?.cancel()
                    val f0 = free(); val before = f0.back(centroid)
                    val nz = (z * zoom).coerceIn(1f, 2.6f); val k = kFit * nz
                    z = nz; c = Offset(before.x - (centroid.x - W / 2) / k - pan.x / k, before.y - (centroid.y - H / 2) / k - pan.y / k)
                    c = PeakGeo.centre(W, H, free())
                }
            }
            .pointerInput(W, H) {
                detectTapGestures(onTap = { s ->
                    val k = hitNow(s)
                    if (k != null) {
                        touch = s; scope.launch { ring.snapTo(0f); ring.animateTo(1f, tween(if (reduced) 1 else 300)) }
                        if (k != key) chooseNow(k)
                    } else clearNow()
                }, onDoubleTap = { s ->
                    if (key != null) return@detectTapGestures
                    val p = free().back(s); val from = z; val to = if (z > 1.5f) 1f else 2.2f; val c0 = c
                    camJob?.cancel(); camJob = scope.launch {
                        val a = Animatable(0f); a.animateTo(1f, tween(if (reduced) 1 else 380, easing = Ease)) {
                            z = from + (to - from) * value; c = Offset(c0.x + (p.x - c0.x) * value, c0.y + (p.y - c0.y) * value)
                        }
                    }
                })
            }
        ) {
            val dm = dim.value
            val heroFile = d.hero.images.getValue(cur.name)
            // 1. the pictures: the previous light under the new one while it fades in
            fun pic(file: String?, pl: Place, w: Float, h: Float, a: Float) {
                val im = file?.let { img[it] } ?: return
                drawImage(im, dstOffset = IntOffset(pl.x.toInt(), pl.y.toInt()), dstSize = IntSize((w * pl.k).toInt() + 1, (h * pl.k).toInt() + 1), alpha = a, filterQuality = FilterQuality.High)
            }
            val p0 = prev
            if (p0 != null && fade.value < 1f) pic(d.hero.images[p0.name], hero, d.hero.w, d.hero.h, 1f)
            pic(heroFile, hero, d.hero.w, d.hero.h, if (p0 != null) fade.value else 1f)
            if (zoomIn < 1f) layer(d, d.hero, hero, 1f - zoomIn, cur, night, wfHero, key, dm, moment.value, near = false, closeUp = near, measurer, unitM, nf)
            if (zoomIn > 0f) {
                pic(d.zoom.images[cur.name], zoomPlace, d.zoom.w, d.zoom.h, zoomIn)
                layer(d, d.zoom, zoomPlace, zoomIn, cur, night, 1f, key, dm, moment.value, near = true, closeUp = true, measurer, unitM, nf)
            }
            // the ring from the finger
            val tp = touch
            if (tp != null && ring.value < 1f) {
                val r = ring.value; val sc = 1f + .35f * r
                drawCircle(Color.White, 14 * dp * sc, tp, alpha = .55f * (1 - r))
                drawCircle(Color.White, 26 * dp * sc, tp, alpha = .9f * (1 - r), style = Stroke(2 * dp))
            }
        }

        // ---- the sign, pinned at the top of the chosen run (from step 3)
        val s = key?.let { (if (zoomIn > .5f) d.zoom else d.hero).sel[it] }
        if (run != null && s != null) {
            val pa = ((moment.value - 300f) / 300f).coerceIn(0f, 1f) * dim.value
            if (pa > 0f) {
                val m = s.mainSeg; val pl = if (zoomIn > .5f) zoomPlace else hero
                val q = pl.on(Offset(m.x[0], m.y[0] - 4))
                Plate(run, Modifier.absoluteOffset { IntOffset(q.x.toInt(), q.y.toInt()) }, pa)
            }
        }

        // ---- the top: the light of the hour, the way back to the app
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(g, Modifier.weight(1f).clickable { scrubOpen = !scrubOpen; if (!scrubOpen) scrub = null; haptics.tick(.3f) }) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (night) Color(0xFFFFD873) else Color(0xFFF2B33D)))
                        val sv = scrub
                        val time = PeakLight.clock(sv ?: PeakLight.localHour(now))
                        Text(if (sv != null || scrubOpen) stringResource(R.string.poc_peak_light_at, time) else stringResource(R.string.poc_peak_light_now, time),
                            fontFamily = Plex, fontSize = 14.sp, color = g.ink, maxLines = 1)
                    }
                }
                Pill(g, Modifier.clickable { haptics.tick(.3f); onLeave() }) {
                    Text(stringResource(R.string.poc_peak_leave), Modifier.padding(horizontal = 16.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = g.ink)
                }
            }
            AnimatedVisibility(scrubOpen, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                Pill(g, Modifier.fillMaxWidth(), h = 52) {
                    // the hours of the light on the January day the pictures were drawn for
                    Slider(value = scrub ?: (PeakLight.localHour(now).coerceIn(7f, 23.5f)), onValueChange = { scrub = it }, valueRange = 7f..23.5f,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        colors = SliderDefaults.colors(thumbColor = g.acc, activeTrackColor = g.acc, inactiveTrackColor = g.line))
                }
            }
            Text(stringResource(R.string.poc_peak_badge), Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0x66081A33)).padding(horizontal = 10.dp, vertical = 3.dp),
                fontFamily = Plex, fontSize = 11.5.sp, color = Color.White)
        }

        AnimatedVisibility(!chosenOnce, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp), enter = fadeIn(), exit = fadeOut()) {
            Pill(g, h = 48) { Text(stringResource(R.string.poc_peak_hint), Modifier.padding(horizontal = 20.dp), fontFamily = Plex, fontSize = 15.sp, color = g.ink) }
        }

        // ---- step 4: the sheet (K10)
        AnimatedVisibility(sheet && run != null, Modifier.align(Alignment.BottomCenter),
            enter = if (reduced) fadeIn(tween(200)) else slideInVertically(tween(250, easing = Ease)) { it } + fadeIn(tween(150)),
            exit = if (reduced) fadeOut(tween(200)) else slideOutVertically(tween(200)) { it } + fadeOut(tween(200))) {
            var last by remember { mutableStateOf(run) }
            if (run != null) last = run
            last?.let { RunSheet(it, d, g, onClose = { clear() }) }
        }
    }
}

// ------------------------------------------------------------------ the live layer of one view, in its points

private fun st(w: Float, effect: PathEffect? = null) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)
private fun dots(zf: Float) = PathEffect.dashPathEffect(floatArrayOf(1f * zf, 5f * zf))

private fun DrawScope.layer(
    d: PeakData, v: PeakData.View, pl: Place, a: Float, light: PeakData.Light, night: Boolean, wf: Float,
    key: String?, dm: Float, ms: Float, near: Boolean, closeUp: Boolean, measurer: TextMeasurer, unitM: String, nf: NumberFormat,
) {
    if (a <= 0f) return
    val pal = d.pal.getValue(if (night) "night" else "day")
    val zr = v.zfRuns * wf; val zl = v.zfLifts * wf
    val sh = if (!light.night) v.shadow[light.name] else null
    val sel = key?.let { v.sel[it] }
    val normal = a * (1f - dm)
    withTransform({ translate(pl.x, pl.y); scale(pl.k, pl.k, Offset.Zero) }) {
        // 2. the small real things
        val ops = v.details[if (night) "night" else "day"].orEmpty()
        val bsh = v.bshadow(light.name)
        for (op in ops) when (op.k) {
            'l' -> op.path?.let { drawPath(it, op.stroke ?: Color.Gray, alpha = op.o * a, style = st(op.w)) }
            'p' -> op.path?.let { p -> op.fill?.let { drawPath(p, it, alpha = op.o * a, style = Fill) }; if (op.stroke != null && op.w > 0f) drawPath(p, op.stroke, alpha = op.o * a, style = st(op.w)) }
            'c' -> drawCircle(op.fill ?: Color.White, op.r, Offset(op.x, op.y), alpha = op.o * a)
            's' -> if (bsh != null && op.i in bsh.indices) drawPath(bsh[op.i], op.fill ?: pal.sh, alpha = op.o * a, style = Fill)
        }
        // 3. the runs: a lit run's glow at night, the cases, the lines; with a chosen run, the others as ghosts
        if (normal > 0f) {
            if (night) for (p in v.pistes) if (p.lit) {
                drawPath(p.path, Color(0xFFFFD08A), alpha = .2f * normal, style = st(16f))
                drawPath(p.path, Color(0xFFFFE2B0), alpha = .3f * normal, style = st(8f))
            }
            for (p in v.pistes) if (p.named && p.kind != "ski-way") drawPath(p.path, pal.case, alpha = .92f * normal, style = st(4.6f * zr))
            for (p in v.pistes) {
                val w = (if (p.named) 2.7f else 1.5f) * zr
                drawPath(p.path, pal.run(p.color), alpha = normal, style = st(w, if (p.dashed) dots(zr) else null))
            }
        }
        if (dm > 0f) {
            // 2 (chosen). the corridor: shade over everything but a soft band ~250 m wide around the run
            if (sel != null) corridor(sel, v.s, if (night) Color(0x9E020612) else Color(0x80364E80), a * dm)
            for (p in v.pistes) if (p.key != key) drawPath(p.path, pal.run(p.color), alpha = a * dm * (if (p.named) .55f else .35f), style = st(1.5f * zr, if (p.dashed) dots(zr) else null))
        }
        // 4. the lifts (dim with a chosen run: no cabins, no shadows of cabins)
        val la = a * (1f - .6f * dm)
        for (l in v.lifts) {
            if (sh != null && normal > 0f) withTransform({ translate(sh.x * .11f, sh.y * .11f) }) { drawPath(l.groundPath, pal.sh, alpha = .16f * la, style = st(.8f * zl)) }
            drawPath(l.wirePath, pal.wire, alpha = la, style = st(1f * zl))
            for (t in l.towers) {
                drawLine(pal.wire, Offset(t[0], t[1]), Offset(t[2], t[3]), 1.15f * zl, StrokeCap.Round, alpha = la)
                drawLine(pal.wire, Offset(t[2] - 1.6f * zl, t[3]), Offset(t[2] + 1.6f * zl, t[3]), 1.15f * zl, StrokeCap.Round, alpha = la)
            }
            if (normal > 0f) for (cb in l.cabins) {
                if (sh != null) drawOval(pal.sh, Offset(cb[2] + sh.x * .11f - 1.5f * zl, cb[3] + sh.y * .11f - .8f * zl), Size(3f * zl, 1.6f * zl), alpha = .3f * normal)
                val tl = Offset(cb[0] - 1.3f * zl, cb[1] + .4f * zl); val sz = Size(2.6f * zl, (if (l.gondola) 2.6f else 1.7f) * zl)
                drawRoundRect(if (night) Color(0xFFFFD873) else Color(0xFFF6C445), tl, sz, CornerRadius(.7f * zl), alpha = normal)
                drawRoundRect(pal.wire, tl, sz, CornerRadius(.7f * zl), alpha = normal, style = Stroke(.5f))
            }
            for (s in l.stations) {
                val tl = Offset(s[0] - 3.4f * zl, s[1] - 4.2f * zl); val sz = Size(6.8f * zl, 5f * zl)
                drawRoundRect(pal.st, tl, sz, CornerRadius(1.4f * zl), alpha = la)
                drawRoundRect(pal.wire, tl, sz, CornerRadius(1.4f * zl), alpha = la, style = Stroke(1.2f * zl))
            }
        }
        // 5. the chosen run
        if (sel != null && dm > 0f) chosen(d, sel, a * dm, ms, night, near, closeUp, v.s)
    }
    // 5 (screen). names on the far view, only with no run chosen; the km numbers on a chosen run by day
    if (normal > .01f && v.labels.isNotEmpty()) labels(v, pl, night, normal, measurer, unitM, nf)
    if (sel != null && dm > .3f && !night && ms >= REVEAL) kmNumbers(sel.mainSeg, pl, a * dm * ((ms - REVEAL) / 200f).coerceIn(0f, 1f), measurer, near)
}

private fun DrawScope.corridor(sel: PeakData.Sel, s: Float, veil: Color, a: Float) {
    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        val count = nc.saveLayer(null, null)
        nc.drawColor(android.graphics.Color.argb((veil.alpha * a * 255).toInt(), (veil.red * 255).toInt(), (veil.green * 255).toInt(), (veil.blue * 255).toInt()))
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = s * 250f; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
            maskFilter = BlurMaskFilter(s * 56f, BlurMaskFilter.Blur.NORMAL); xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        for (g in sel.segs) nc.drawPath(g.full.asAndroidPath(), p)
        nc.restoreToCount(count)
    }
}

/** The chosen run: the light running down it (0..600 ms, the main piece), then the final marking (K11 a by day, c by night). */
private fun DrawScope.chosen(d: PeakData, sel: PeakData.Sel, a: Float, ms: Float, night: Boolean, near: Boolean, closeUp: Boolean, s: Float) {
    val m = sel.mainSeg
    val head = m.length * (ms / REVEAL).coerceIn(0f, 1f)
    val after = ((ms - REVEAL) / 200f).coerceIn(0f, 1f)      // the change from the running light to the final marking
    // the running light: a white halo, a white line, the slope's colours; its head a white dot
    val ra = a * (1f - after)
    if (ra > 0f) {
        val path = m.path(head)
        drawPath(path, Color.White, alpha = .35f * ra, style = st(16f))
        drawPath(path, Color.White, alpha = ra, style = st(6f))
        slopeLine(d, m, head, 3.6f, ra)
        if (ms < REVEAL) { val q = m.at(head); drawCircle(Color.White, 13f, q, alpha = .5f * ra); drawCircle(Color.White, 5f, q, alpha = ra) }
    }
    // the other pieces of the run come in softly when the light reaches the end
    val others = a * ((ms - REVEAL) / 200f).coerceIn(0f, 1f)
    val fa = a * after
    if (fa <= 0f) return
    val segs = sel.segs
    if (!night) {
        val caseW = if (closeUp) 8.2f else 6.0f; val lineW = if (closeUp) 5.2f else 3.6f
        for ((i, g) in segs.withIndex()) {
            val al = if (i == sel.main) fa else min(fa, others)
            drawPath(g.full, Color.White, alpha = al, style = st(caseW))
            slopeLine(d, g, Float.MAX_VALUE, lineW, al)
            // arrows down the fall line, one each time more than 240 m gathered
            var acc = 0f
            for (j in 1 until g.n - 1) {
                acc += g.m[j] - g.m[j - 1]
                if (acc > 240f && g.vis[j]) {
                    acc = 0f
                    val x = g.x[j]; val y = g.y[j]; val dd = g.dir(g.m[j]); val ax = dd.x; val ay = dd.y
                    val p = Path().apply { moveTo(x - 1.6f * ax - 2.1f * ay, y - 1.6f * ay + 2.1f * ax); lineTo(x + 1.3f * ax, y + 1.3f * ay); lineTo(x - 1.6f * ax + 2.1f * ay, y - 1.6f * ay - 2.1f * ax) }
                    drawPath(p, Color.White, alpha = al, style = st(1.3f))
                }
            }
        }
        // on the main piece: a bead every 500 m, the start and the end
        var b = 500f
        while (b < m.length) {
            if (m.vis[m.index(b)]) { val q = m.at(b); drawCircle(Color.White, 3.1f, q, alpha = fa); drawCircle(Color(0xFF0E2233), 3.1f, q, alpha = fa, style = Stroke(1.2f)) }
            b += 500f
        }
        if (m.vis[0]) { val q = Offset(m.x[0], m.y[0]); drawCircle(Color.White, 4.6f, q, alpha = fa); drawCircle(ACCENT_DAY, 4.6f, q, alpha = fa, style = Stroke(2f)) }
        val e = m.n - 1
        if (m.vis[e]) { val q = Offset(m.x[e], m.y[e]); drawCircle(ACCENT_DAY, 3.6f, q, alpha = fa); drawCircle(Color.White, 3.6f, q, alpha = fa, style = Stroke(1.4f)) }
    } else {
        // night: a warm glow, and a light every 10.5 points on the screen (42 m close up, 82 m far)
        val step = 10.5f / s
        for ((i, g) in segs.withIndex()) {
            val al = if (i == sel.main) fa else min(fa, others)
            drawPath(g.full, Color(0xFFFFD08A), alpha = .2f * al, style = st(14f))
            var t = 0f
            while (t <= g.length) {
                val j = g.index(t)
                if (g.vis[j]) { val q = g.at(t); drawCircle(Color(0xFFFFF3D6), 2.3f, q, alpha = al); drawCircle(d.slope[g.cls[j].coerceIn(0, d.slope.size - 1)], 4.4f, q, alpha = .4f * al) }
                t += step
            }
        }
    }
}

private fun DrawScope.slopeLine(d: PeakData, g: PeakData.Seg, to: Float, w: Float, a: Float) {
    var i = 0
    while (i < g.n - 1 && g.m[i] < to) {
        if (g.vis[i] && g.vis[i + 1]) {
            val b = if (g.m[i + 1] <= to) Offset(g.x[i + 1], g.y[i + 1]) else g.at(to)
            drawLine(d.slope[g.cls[i].coerceIn(0, d.slope.size - 1)], Offset(g.x[i], g.y[i]), b, w, StrokeCap.Round, alpha = a)
        }
        i++
    }
}

private fun DrawScope.labels(v: PeakData.View, pl: Place, night: Boolean, a: Float, measurer: TextMeasurer, unitM: String, nf: NumberFormat) {
    val ink = if (night) Color(0xFFEAF1F8) else Color(0xFF0E2233)
    val halo = if (night) Color(0xF2060B16) else Color(0xF2FFFFFF)
    val sc = pl.k / density   // the label sizes are in picture points, like everything on it
    val name = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = (11.5f * sc).sp, color = ink)
    val sub = TextStyle(fontFamily = Plex, fontSize = (10.5f * sc).sp, color = ink)
    for (l in v.labels) {
        val q = pl.on(Offset(l.x, l.y))
        val t1 = measurer.measure(l.t, name)
        val t2 = l.sub?.let { measurer.measure(unitM.replace("\u0000", nf.format(it)), sub) }
        val hgt = t1.size.height + (t2?.size?.height ?: 0)
        var y = q.y - hgt / 2f
        for (t in listOfNotNull(t1, t2)) {
            val tl = Offset(q.x - t.size.width / 2f, y)
            // the halo around the letters (three layers: 3, 3, 7)
            for (r in floatArrayOf(7f, 3f, 3f)) drawText(t, topLeft = tl, alpha = a, shadow = Shadow(halo, Offset.Zero, r * sc))
            drawText(t, topLeft = tl, alpha = a)
            y += t.size.height
        }
    }
}

private fun DrawScope.kmNumbers(m: PeakData.Seg, pl: Place, a: Float, measurer: TextMeasurer, near: Boolean) {
    if (a <= 0f) return
    val sc = pl.k / density
    val style = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = (9.5f * sc).sp, color = Color.White)
    var b = 500f
    while (b < m.length) {
        if (m.vis[m.index(b)]) {
            val q = m.at(b); val dd = m.dir(b)
            val c = pl.on(Offset(q.x + 13f * dd.y, q.y - 13f * dd.x + 3.4f))
            val t = measurer.measure(("%.1f".format(b / 1000f)).trimEnd('0').trimEnd('.'), style)
            val tl = Offset(c.x - t.size.width / 2f, c.y - t.size.height * .72f)
            drawText(t, topLeft = tl, alpha = a, shadow = Shadow(Color(0xCC0A1932), Offset.Zero, 2.4f * sc), drawStyle = Stroke(2.4f * sc))
            drawText(t, topLeft = tl, alpha = a)
        }
        b += 500f
    }
}

// ------------------------------------------------------------------ the parts over the picture

@Composable
private fun Pill(g: Glass, modifier: Modifier = Modifier, h: Int = 44, content: @Composable () -> Unit) {
    Box(modifier.height(h.dp).shadow(10.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = Color(0x2E0A192D), spotColor = Color(0x2E0A192D))
        .clip(RoundedCornerShape(22.dp)).background(g.bg).border(1.dp, g.line, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { content() }
}

private fun plate(c: String) = when (c) { "green" -> Color(0xFF178A4A); "blue" -> Color(0xFF1B6FDB); "red" -> Color(0xFFD8362C); else -> Color(0xFF14171B) }

/** The sign: its bottom corner at 86% of its width on the point (most of it to the left), a white stem 2 x 12 under it. */
@Composable
private fun Plate(r: PeakData.Run, modifier: Modifier, alpha: Float) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.size(0.dp)) {
            var wPx by remember { mutableStateOf(0) }
            Column(Modifier.graphicsLayer {
                translationX = -wPx * .86f; translationY = -size.height; this.alpha = alpha
            }.onSizeChanged { wPx = it.width }, horizontalAlignment = Alignment.End) {
                Row(Modifier.height(24.dp).shadow(5.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(plate(r.color))
                    .border(1.5.dp, Color.White, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (r.ref.isNotBlank()) r.ref + " · " + r.name else r.name, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, maxLines = 1)
                }
                Box(Modifier.padding(end = 12.dp).width(2.dp).height(12.dp).background(Color.White))
            }
        }
    }
}

@Composable
private fun RunSheet(r: PeakData.Run, d: PeakData, g: Glass, onClose: () -> Unit) {
    val colour = stringResource(when (r.color) { "green" -> R.string.common_color_green; "blue" -> R.string.common_color_blue; "red" -> R.string.common_color_red; else -> R.string.common_color_black })
    val level = stringResource(when (r.color) { "green" -> R.string.map_difficulty_beginner; "blue" -> R.string.map_difficulty_easy; "red" -> R.string.map_difficulty_intermediate; else -> R.string.map_difficulty_hard })
    val closeLabel = stringResource(R.string.common_close)
    val nf = remember { NumberFormat.getIntegerInstance() }
    Column(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }
        .shadow(30.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color(0x2E0A192D), spotColor = Color(0x2E0A192D))
        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(g.bg).navigationBarsPadding()
        .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(5.dp).clip(CircleShape).background(g.line))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.height(30.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(7.dp)).background(plate(r.color)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                Text(r.ref, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, style = TextStyle(textDirection = TextDirection.Ltr))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.name, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 25.sp, color = g.ink, style = TextStyle(textDirection = TextDirection.Ltr))
                Text(colour + stringResource(R.string.run_color_official_suffix) + " · " + level, fontFamily = Plex, fontSize = 13.5.sp, color = g.ink2)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).semantics { contentDescription = closeLabel }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(13.dp)) {
                    drawLine(g.ink2, Offset.Zero, Offset(size.width, size.height), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(g.ink2, Offset(size.width, 0f), Offset(0f, size.height), 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Num(stringResource(R.string.common_unit_km, "%.1f".format(r.len / 1000f)), stringResource(R.string.common_length_label), g)
            Num(stringResource(R.string.common_unit_m, nf.format(r.drop)), stringResource(R.string.run_stat_drop_label), g)
            Num("${r.steep}°", stringResource(R.string.run_stat_steep_label), g)
        }
        // the slope's four colours, with the site's thresholds between them
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.slope.forEachIndexed { i, col ->
                        Box(Modifier.width(18.dp).height(6.dp).clip(CircleShape).background(col))
                        if (i < 3) Text(listOf("15°", "25°", "30°")[i], fontFamily = Plex, fontSize = 12.sp, color = g.ink2)
                    }
                }
            }
        }
    }
}

@Composable
private fun Num(v: String, cap: String, g: Glass) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(v, fontFamily = Plex, fontSize = 30.sp, lineHeight = 34.sp, color = g.ink, maxLines = 1)
        Text(cap, fontFamily = Plex, fontSize = 12.5.sp, color = g.ink2)
    }
}

package io.github.pini236.skiapp.redesign

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The redesign POC (decisions 83, 84): round 3's mountain as the first screen. The picture of the hour in Gudauri,
 * cross-faded (K01, K02); over it, live, every run and lift where it really is (peak.json); a touch on a run is the
 * moment of choosing (K09): a ring from the finger, the rest of the mountain falls into shade, the light runs down the
 * run from top to bottom in the colours of its slope, the view comes closer, the sign is pinned at the top and the
 * sheet rises with three numbers (K10). The chosen run is a line of light by day and beads of light by night (K11,
 * Pini's choice). Test build only.
 */

private val Ease = CubicBezierEasing(.2f, 0f, 0f, 1f)
private const val REVEAL_MS = 600f
private const val SHEET_MS = 900L

private class Ui(val night: Boolean) {
    val glass = if (night) Color(0xE8101A2C) else Color(0xEBFFFFFF)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val ink2 = if (night) Color(0xFFA9B8CC) else Color(0xFF40556A)
    val line = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val acc = if (night) Color(0xFFB79CFF) else Color(0xFF6B3FD4)
    val wire = if (night) Color(0xFFC9D5E2) else Color(0xFF22303F)
    val station = if (night) Color(0xFF0A1120) else Color.White
    val case = if (night) Color(0xFF081020) else Color.White
    val cabin = if (night) Color(0xFFFFD873) else Color(0xFFF6C445)
    val veil = if (night) Color(0x9E020612) else Color(0x80364E80)
    fun run(c: String) = when (c) {
        "green" -> if (night) Color(0xFF4FD893) else Color(0xFF178A4A)
        "blue" -> if (night) Color(0xFF6CB4FF) else Color(0xFF1B6FDB)
        "red" -> if (night) Color(0xFFFF776A) else Color(0xFFD8362C)
        else -> if (night) Color(0xFFEEF2F6) else Color(0xFF14171B)
    }
    /** the plate of a run sign: the official colour of the day palette, always on white text */
    fun plate(c: String) = when (c) { "green" -> Color(0xFF178A4A); "blue" -> Color(0xFF1B6FDB); "red" -> Color(0xFFD8362C); else -> Color(0xFF14171B) }
}

/** The pictures, decoded on demand (each is 1170 x 2532, ~12 MB in memory): the last four are kept. */
private class Pictures(private val context: android.content.Context) {
    private val cache = LinkedHashMap<String, ImageBitmap>(8, .75f, true)
    /** while the hour is dragged all seven stay (about 80 MB); otherwise three */
    @Volatile var limit = 3
    @Synchronized fun peek(file: String): ImageBitmap? = cache[file]
    fun get(file: String): ImageBitmap {
        peek(file)?.let { return it }
        val bmp = context.assets.open("redesign/$file").use { BitmapFactory.decodeStream(it) }.also { it.prepareToDraw() }.asImageBitmap()
        synchronized(this) { cache[file] = bmp; while (cache.size > limit) cache.remove(cache.keys.first()) }
        return bmp
    }
}

/** Where the picture is on the screen: the view's centre (in the picture's 390 x 844 units) and the zoom over "fills the screen". */
private data class Cam(val cx: Float, val cy: Float, val z: Float)

@Composable
fun PeakScreen(mode: DayNight.Mode, haptics: Haptics, onLeave: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduced = remember { Motion.reduced(context) }
    val data by produceState<PeakData?>(null) { value = withContext(Dispatchers.IO) { PeakData.load(context) } }
    val pics = remember { Pictures(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var scrubOpen by remember { mutableStateOf(false) }

    val d = data
    if (d == null) { Box(Modifier.fillMaxSize().background(Color(0xFF9DB6D8))); return }
    val hour = scrub ?: PeakLight.hourFor(now, mode)
    val blend = remember(d, hour) { PeakLight.blend(d.hours, hour) }
    val ui = remember(blend.night) { Ui(blend.night) }

    // the two pictures of the hour; until a new one is decoded the last one stays
    val shown = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(blend.a.file, blend.b.file) {
        val want = listOf(blend.a.file, blend.b.file).distinct()
        for (f in want) if (shown[f] == null) shown[f] = pics.peek(f) ?: withContext(Dispatchers.IO) { pics.get(f) }
        shown.keys.filter { it !in want }.forEach { shown.remove(it) }
    }
    // a drag through the day: every hour ready
    LaunchedEffect(scrubOpen) {
        pics.limit = if (scrubOpen) 8 else 3
        if (scrubOpen) withContext(Dispatchers.IO) { d.hours.forEach { pics.get(it.file) } }
    }

    // ---- the view: pinch to come closer, drag to move; a chosen run brings the view to itself
    var cam by remember { mutableStateOf(Cam(d.w / 2, d.h / 2, 1f)) }
    val scope = rememberCoroutineScope()
    var camJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // ---- the chosen run and the clock of its moment (K09), in milliseconds since the touch
    var sel by remember { mutableStateOf<PeakData.Piste?>(null) }
    val moment = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }
    var sheet by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val ring = remember { Animatable(1f) }
    var chosenOnce by remember { mutableStateOf(false) }
    // the cabins move on the open lifts; the clock of the frames
    var frameT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) { if (!reduced) { val t0 = withFrameNanos { it }; while (true) withFrameNanos { frameT = (it - t0) / 1e9f } } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF9DB6D8))) {
        val W = constraints.maxWidth.toFloat(); val H = constraints.maxHeight.toFloat()
        val s0 = max(W / d.w, H / d.h)
        fun clamp(c: Cam): Cam {
            val z = c.z.coerceIn(1f, 3.2f); val k = s0 * z
            val hx = W / (2 * k); val hy = H / (2 * k)
            return Cam(c.cx.coerceIn(min(hx, d.w / 2), max(d.w - hx, d.w / 2)), c.cy.coerceIn(min(hy, d.h / 2), max(d.h - hy, d.h / 2)), z)
        }
        fun k() = s0 * cam.z
        fun toScreen(p: Offset) = Offset((p.x - cam.cx) * k() + W / 2, (p.y - cam.cy) * k() + H / 2)
        fun toPic(s: Offset) = Offset((s.x - W / 2) / k() + cam.cx, (s.y - H / 2) / k() + cam.cy)
        fun flyTo(to: Cam, ms: Int, after: Long = 0) {
            camJob?.cancel()
            val from = cam; val target = clamp(to)
            camJob = scope.launch {
                if (after > 0) delay(after)
                if (reduced) { cam = target; return@launch }
                val a = Animatable(0f)
                a.animateTo(1f, tween(ms, easing = Ease)) {
                    val t = value; cam = Cam(from.cx + (target.cx - from.cx) * t, from.cy + (target.cy - from.cy) * t, from.z + (target.z - from.z) * t)
                }
            }
        }
        val sheetPx = with(density) { 300.dp.toPx() }
        val topPx = with(density) { 150.dp.toPx() }
        val sidePx = with(density) { 36.dp.toPx() }
        fun choose(p: PeakData.Piste) {
            val line = p.line ?: return
            val again = sel != null
            sel = p; chosenOnce = true; sheet = false
            haptics.tick(.5f)
            scope.launch {
                moment.snapTo(0f)
                if (reduced) { dim.snapTo(1f); moment.snapTo(2000f); sheet = true; return@launch }
                launch { if (!again) dim.animateTo(1f, tween(REVEAL_MS.toInt(), easing = LinearEasing)) else dim.snapTo(1f) }
                launch { delay(320); haptics.click(.6f) }
                launch { delay(SHEET_MS); sheet = true }
                moment.animateTo(2000f, tween(2000, easing = LinearEasing))
            }
            // the view comes to the run, above the sheet
            val b = line.bounds()
            val availW = W - 2 * sidePx; val availH = H - topPx - sheetPx - 24
            val z = min(availW / (b.width.coerceAtLeast(20f) * s0), availH / (b.height.coerceAtLeast(20f) * s0)).coerceIn(1f, 2.6f)
            val k = s0 * z
            val ax = W / 2; val ay = topPx + availH / 2
            flyTo(Cam(b.center.x - (ax - W / 2) / k, b.center.y - (ay - H / 2) / k, z), 620, if (again) 0 else 300)
        }
        fun clear() {
            if (sel == null) return
            sheet = false
            scope.launch { dim.animateTo(0f, tween(if (reduced) 120 else 260)); sel = null; moment.snapTo(0f) }
            flyTo(Cam(d.w / 2, d.h / 2, 1f), if (reduced) 0 else 520)
        }
        androidx.activity.compose.BackHandler(enabled = sel != null) { clear() }
        fun hit(s: Offset): PeakData.Piste? {
            val r = with(density) { 26.dp.toPx() }; var best: PeakData.Piste? = null; var bd = r
            val p = toPic(s); val kk = k()
            for (pi in d.pistes) {
                if (!pi.choosable) continue
                for (c in pi.chunks) {
                    var i = 0
                    while (i < c.size) { val dd = hypot(c[i] - p.x, c[i + 1] - p.y) * kk; if (dd < bd) { bd = dd; best = pi }; i += 2 }
                }
            }
            return best
        }

        val measurer = rememberTextMeasurer()
        val labelStyle = remember { TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
            shadow = Shadow(Color(0x99081A33), Offset(0f, 1f), 6f)) }
        val smallStyle = remember { labelStyle.copy(fontWeight = FontWeight.Normal, fontSize = 11.sp) }
        val kmStyle = remember { TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White, shadow = Shadow(Color(0xE60A1932), Offset.Zero, 5f)) }
        val unitM = stringResource(R.string.common_unit_m, "{n}")

        Canvas(Modifier.fillMaxSize()
            .pointerInput(W, H) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camJob?.cancel()
                    val before = toPic(centroid)
                    val z = (cam.z * zoom).coerceIn(1f, 3.2f); val kk = s0 * z
                    // the point under the fingers stays under them
                    cam = clamp(Cam(before.x - (centroid.x - W / 2) / kk - pan.x / kk, before.y - (centroid.y - H / 2) / kk - pan.y / kk, z))
                }
            }
            .pointerInput(W, H) {
                detectTapGestures(onTap = { s ->
                    val p = hit(s)
                    if (p != null) {
                        touch = s; scope.launch { ring.snapTo(0f); ring.animateTo(1f, tween(420, easing = Ease)) }
                        if (p !== sel) choose(p)
                    } else clear()
                }, onDoubleTap = { s -> if (sel == null) { val p = toPic(s); flyTo(Cam(p.x, p.y, if (cam.z > 1.6f) 1f else 2.2f), 420) } })
            }
        ) {
            val kk = k()
            // the pictures of the hour
            val left = W / 2 - cam.cx * kk; val top = H / 2 - cam.cy * kk
            val dst = IntSize((d.w * kk).toInt() + 1, (d.h * kk).toInt() + 1); val at = IntOffset(left.toInt(), top.toInt())
            val a = shown[blend.a.file] ?: shown.values.firstOrNull(); val b = shown[blend.b.file]
            if (a != null) drawImage(a, dstOffset = at, dstSize = dst, filterQuality = FilterQuality.High)
            if (b != null && b !== a && blend.f > 0f) drawImage(b, dstOffset = at, dstSize = dst, alpha = blend.f, filterQuality = FilterQuality.High)
            val dm = dim.value
            withTransform({ translate(left, top); scale(kk, kk, Offset.Zero) }) {
                val wf = cam.z.pow(-.38f)
                lifts(d, ui, blend, wf, frameT, liftsAlpha = 1f - .55f * dm, reduced = reduced)
                runs(d, ui, blend.night, wf, sel, dm)
                val s = sel
                if (s?.line != null && dm > 0f) {
                    corridor(s, ui, dm)
                    chosen(d, s, ui, blend.night, moment.value, wf, dm)
                }
            }
            // labels on the mountain: the peaks and the village, fading while a run is chosen
            val la = 1f - dm
            if (la > .02f) {
                for (p in d.peaks) {
                    val q = toScreen(Offset(p.x, p.y))
                    val n = measurer.measure(p.name, labelStyle); val e = measurer.measure(unitM.replace("{n}", "%,d".format(p.ele)), smallStyle)
                    drawText(n, topLeft = Offset(q.x - n.size.width / 2, q.y - n.size.height - e.size.height), alpha = la)
                    drawText(e, topLeft = Offset(q.x - e.size.width / 2, q.y - e.size.height), alpha = la * .9f)
                }
                for (p in d.places) { val q = toScreen(Offset(p.x, p.y)); val n = measurer.measure(p.name, labelStyle); drawText(n, topLeft = Offset(q.x - n.size.width / 2, q.y - n.size.height / 2), alpha = la) }
            }
            // the km beads' numbers on a chosen run, by day, once the light has passed them
            val s = sel; val line = s?.line
            if (line != null && !blend.night && dm > .5f) {
                val head = line.length * (moment.value / REVEAL_MS).coerceIn(0f, 1f)
                var m = 500f
                while (m < line.length) {
                    if (m <= head && line.vis[line.index(m)]) {
                        val q = toScreen(line.at(m)); val dir = line.dir(m)
                        val t = measurer.measure(("%.1f".format(m / 1000f)).trimEnd('0').trimEnd('.'), kmStyle)
                        drawText(t, topLeft = Offset(q.x + dir.y * 15 * density.density - t.size.width / 2, q.y - dir.x * 15 * density.density - t.size.height / 2), alpha = dm)
                    }
                    m += 500f
                }
            }
            // the ring from the finger
            val tp = touch
            if (tp != null && ring.value < 1f) {
                val r = ring.value
                drawCircle(Color.White, radius = (14 + 18 * r) * density.density, center = tp, alpha = .9f * (1 - r), style = Stroke(2 * density.density))
                drawCircle(Color.White, radius = 12 * density.density, center = tp, alpha = .5f * (1 - r))
            }
        }

        // ---- the sign pinned at the top of the chosen run
        val s = sel; val line = s?.line
        if (s != null && line != null) {
            val pa = ((moment.value - 450f) / 300f).coerceIn(0f, 1f)
            if (pa > 0f) {
                val q = toScreen(Offset(line.x[0], line.y[0]))
                Plate(s, ui, Modifier.absoluteOffset { IntOffset(q.x.toInt(), q.y.toInt()) }, pa)
            }
        }

        // ---- the top: what this is, the light of the hour, the way back
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Glass(ui, Modifier.weight(1f).height(44.dp).clickable {
                    scrubOpen = !scrubOpen; if (!scrubOpen) scrub = null; haptics.tick(.3f)
                }) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (blend.night) Color(0xFFFFD873) else Color(0xFFF2B33D)))
                        val time = if (scrub != null) PeakLight.clock(scrub!!) else PeakLight.clock(PeakLight.localHour(now).toFloat())
                        Text(if (scrubOpen) stringResource(R.string.poc_peak_light_at, time) else stringResource(R.string.poc_peak_light_now, time),
                            fontFamily = Plex, fontSize = 14.sp, color = ui.ink, maxLines = 1)
                    }
                }
                Glass(ui, Modifier.height(44.dp).clickable { haptics.tick(.3f); onLeave() }) {
                    Text(stringResource(R.string.poc_peak_leave), Modifier.padding(horizontal = 16.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ui.ink)
                }
            }
            AnimatedVisibility(scrubOpen, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                Glass(ui, Modifier.fillMaxWidth().height(52.dp)) {
                    Slider(value = scrub ?: hour, onValueChange = { scrub = it }, valueRange = 7f..23.5f,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        colors = SliderDefaults.colors(thumbColor = ui.acc, activeTrackColor = ui.acc, inactiveTrackColor = ui.line))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.poc_peak_badge), Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0x66081A33)).padding(horizontal = 10.dp, vertical = 3.dp),
                    fontFamily = Plex, fontSize = 11.5.sp, color = Color.White)
            }
        }

        // ---- the hint, until the first choice
        AnimatedVisibility(!chosenOnce, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp), enter = fadeIn(), exit = fadeOut()) {
            Glass(ui, Modifier.height(48.dp)) {
                Text(stringResource(R.string.poc_peak_hint), Modifier.padding(horizontal = 20.dp), fontFamily = Plex, fontSize = 15.sp, color = ui.ink)
            }
        }

        // ---- the sheet (K10): the sign, three numbers, the slope legend
        AnimatedVisibility(sheet && s != null, Modifier.align(Alignment.BottomCenter),
            enter = if (reduced) fadeIn(tween(150)) else slideInVertically(spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180))) {
            var last by remember { mutableStateOf(s) }
            if (s != null) last = s
            last?.let { RunSheet(it, d, ui, onClose = { clear() }) }
        }
    }
}

@Composable
private fun Glass(ui: Ui, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.shadow(10.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = Color(0x33081A33), spotColor = Color(0x33081A33))
        .clip(RoundedCornerShape(22.dp)).background(ui.glass).border(1.dp, ui.line, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Sign(p: PeakData.Piste, ui: Ui, big: Boolean) {
    val h: Dp = if (big) 30.dp else 22.dp
    Box(Modifier.height(h).widthIn(min = if (big) 40.dp else 28.dp).clip(RoundedCornerShape(if (big) 7.dp else 5.dp)).background(ui.plate(p.color))
        .border(1.5.dp, Color(0xE6FFFFFF), RoundedCornerShape(if (big) 7.dp else 5.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(p.ref.ifBlank { " " }, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = if (big) 17.sp else 12.sp, color = Color.White,
            style = TextStyle(textDirection = TextDirection.Ltr))
    }
}

@Composable
private fun Plate(p: PeakData.Piste, ui: Ui, modifier: Modifier, alpha: Float) {
    // anchored by its stem's foot at the top of the run (translate(-86%, -100%) on the canvas)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Box(modifier.width(0.dp).height(0.dp)) {
        Column(Modifier.absoluteOffset(x = (-150).dp, y = (-42).dp).width(170.dp).graphicsLayer(alpha = alpha, translationY = (1 - alpha) * 14f), horizontalAlignment = Alignment.End) {
            Row(Modifier.height(26.dp).shadow(6.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(ui.plate(p.color))
                .border(1.5.dp, Color.White, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((if (p.ref.isNotBlank()) p.ref + " · " else "") + p.name, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
                    style = TextStyle(textDirection = TextDirection.Ltr))
            }
            Box(Modifier.padding(end = 18.dp).width(2.dp).height(14.dp).background(Color.White))
        }
    } }
}

@Composable
private fun RunSheet(p: PeakData.Piste, d: PeakData, ui: Ui, onClose: () -> Unit) {
    val colour = stringResource(when (p.color) { "green" -> R.string.common_color_green; "blue" -> R.string.common_color_blue; "red" -> R.string.common_color_red; else -> R.string.common_color_black })
    val level = stringResource(when (p.color) { "green" -> R.string.map_difficulty_beginner; "blue" -> R.string.map_difficulty_easy; "red" -> R.string.map_difficulty_intermediate; else -> R.string.map_difficulty_hard })
    val closeLabel = stringResource(R.string.poc_peak_close)
    Column(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }
        .shadow(18.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color(0x55081A33), spotColor = Color(0x55081A33))
        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(ui.glass).navigationBarsPadding()
        .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).height(5.dp).clip(CircleShape).background(ui.line))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Sign(p, ui, true)
            Column(Modifier.weight(1f)) {
                Text(p.name, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ui.ink, style = TextStyle(textDirection = TextDirection.Ltr))
                val where = if (p.from.isNotEmpty() && p.to.isNotEmpty()) " · " + stringResource(R.string.poc_peak_from_to, "⁨${p.from.first()}⁩", "⁨${p.to.first()}⁩") else ""
                Text("$colour · $level$where", fontFamily = Plex, fontSize = 13.5.sp, color = ui.ink2, lineHeight = 18.sp)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).semantics { contentDescription = closeLabel }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(14.dp)) {
                    drawLine(ui.ink2, Offset.Zero, Offset(size.width, size.height), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(ui.ink2, Offset(size.width, 0f), Offset(0f, size.height), 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Num("%.1f".format(p.len / 1000f), stringResource(R.string.poc_peak_km), ui)
            Num("%d".format(p.drop), stringResource(R.string.poc_peak_drop), ui)
            Num("${p.steep}°", stringResource(R.string.poc_peak_steep), ui)
        }
        // the slope legend: the run is painted in these four colours
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val labels = listOf("≤15°", "15–25°", "25–30°", ">30°")
            d.slope.forEachIndexed { i, (_, c) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.width(14.dp).height(5.dp).clip(CircleShape).background(Color(c)))
                    Text(labels[i], fontFamily = Plex, fontSize = 12.sp, color = ui.ink2, style = TextStyle(textDirection = TextDirection.Ltr))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun Num(v: String, cap: String, ui: Ui) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(v, fontFamily = Plex, fontSize = 32.sp, lineHeight = 34.sp, color = ui.ink, style = TextStyle(textDirection = TextDirection.Ltr))
        Text(cap, fontFamily = Plex, fontSize = 12.5.sp, color = ui.ink2)
    }
}

// ------------------------------------------------------------------ the live layer, in the picture's 390 x 844 units

private fun st(w: Float, effect: PathEffect? = null) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)

private fun DrawScope.runs(d: PeakData, ui: Ui, night: Boolean, wf: Float, sel: PeakData.Piste?, dm: Float) {
    val line = 1f - dm
    // the one lit run glows at night (Pirveli)
    if (night) for (p in d.pistes) if (p.lit) { drawPath(p.path, Color(0xFFFFD08A), alpha = .2f * line, style = st(16f * wf)); drawPath(p.path, Color(0xFFFFE2B0), alpha = .3f * line, style = st(8f * wf)) }
    if (line > 0f) {
        for (p in d.pistes) if (p.named && p.kind != "ski-way") drawPath(p.path, ui.case, alpha = .92f * line, style = st(4.6f * wf))
        for (p in d.pistes) {
            val w = (if (p.named) 2.7f else 1.5f) * wf
            drawPath(p.path, ui.run(p.color), alpha = line, style = st(w, if (p.dashed) PathEffect.dashPathEffect(floatArrayOf(.01f, 5f * wf)) else null))
        }
    }
    // with a chosen run, the others become thin ghosts in their colour, so it stays clear what joins what
    if (dm > 0f) for (p in d.pistes) if (p !== sel) {
        drawPath(p.path, ui.run(p.color), alpha = dm * (if (p.named) .6f else .38f), style = st(1.5f * wf, if (p.dashed) PathEffect.dashPathEffect(floatArrayOf(.01f, 5f * wf)) else null))
    }
}

private fun DrawScope.lifts(d: PeakData, ui: Ui, blend: PeakLight.Blend, wf: Float, t: Float, liftsAlpha: Float, reduced: Boolean) {
    val hg = 11f
    for (l in d.lifts) {
        // the cable's shadow on the snow, where the sun puts it
        if (!blend.night) withTransform({ translate(blend.dx * hg, blend.dy * hg) }) {
            drawPath(l.ground, Color(0xFF1B2E55), alpha = .16f * liftsAlpha, style = st(.8f * wf))
        }
        drawPath(l.wire, ui.wire, alpha = liftsAlpha, style = st(1f * wf))
        val n = l.x.size; val step = 14
        var i = step
        while (i < n - 3) {
            if (l.vis[i]) {
                drawLine(ui.wire, Offset(l.gx[i], l.gy[i]), Offset(l.x[i], l.y[i]), 1.15f * wf, alpha = liftsAlpha)
                drawLine(ui.wire, Offset(l.x[i] - 1.6f * wf, l.y[i]), Offset(l.x[i] + 1.6f * wf, l.y[i]), 1.15f * wf, alpha = liftsAlpha)
            }
            i += step
        }
        // the cabins: they move slowly up the cable (the open lifts; the status is not wired into the POC)
        if (liftsAlpha > .6f) {
            val gap = 8f; val phase = if (reduced) 0f else (t * 1.1f) % gap
            var c = step / 2f + phase
            while (c < n - 2) {
                val j = c.toInt(); val f = c - j
                if (j + 1 < n && l.vis[j] && l.vis[j + 1]) {
                    val x = l.x[j] + (l.x[j + 1] - l.x[j]) * f; val y = l.y[j] + (l.y[j + 1] - l.y[j]) * f
                    val ch = (if (l.gondola) 2.6f else 1.7f) * wf
                    drawRoundRect(ui.cabin, Offset(x - 1.3f * wf, y + .4f * wf), Size(2.6f * wf, ch), androidx.compose.ui.geometry.CornerRadius(.7f * wf))
                }
                c += gap
            }
        }
        for ((x, y, v) in l.ends) if (v) {
            val r = Rect(x - 3.4f * wf, y - 4.2f * wf, x + 3.4f * wf, y + .8f * wf)
            drawRoundRect(ui.station, r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(1.4f * wf), alpha = liftsAlpha)
            drawRoundRect(ui.wire, r.topLeft, r.size, androidx.compose.ui.geometry.CornerRadius(1.4f * wf), alpha = liftsAlpha, style = Stroke(1.2f * wf))
        }
    }
}

/** the rest of the mountain under a veil, the chosen run in a soft corridor of light (about 250 m wide) */
private fun DrawScope.corridor(p: PeakData.Piste, ui: Ui, dm: Float) {
    drawIntoCanvas { c ->
        c.saveLayer(Rect(Offset.Zero, size * 4f).translate(-size.width, -size.height), Paint())
        drawRect(ui.veil, topLeft = Offset(-2000f, -2000f), size = Size(6000f, 6000f), alpha = dm)
        val w = .128f * 250f
        for ((m, a) in listOf(1.5f to .25f, 1.15f to .3f, .85f to .4f, .55f to .6f, .3f to .8f)) drawPath(p.path, Color.Black, alpha = a, style = st(w * m), blendMode = BlendMode.DstOut)
        c.restore()
    }
}

/** The chosen run, revealed from top to bottom: a line of light by day (K11 a), beads of light by night (K11 c). */
private fun DrawScope.chosen(d: PeakData, p: PeakData.Piste, ui: Ui, night: Boolean, ms: Float, wf: Float, dm: Float) {
    val line = p.line ?: return
    val head = line.length * Ease.transform((ms / REVEAL_MS).coerceIn(0f, 1f))
    val done = ms >= REVEAL_MS
    val n = line.n
    // the line's points that the light has reached, in seen pieces
    fun pieces(to: Float, f: (Path) -> Unit) {
        val path = Path(); var open = false
        for (i in 0 until n) {
            if (line.m[i] > to) { if (open && i > 0 && line.vis[i - 1]) { val q = line.at(to); path.lineTo(q.x, q.y) }; break }
            if (line.vis[i]) { if (open) path.lineTo(line.x[i], line.y[i]) else path.moveTo(line.x[i], line.y[i]); open = true } else open = false
        }
        f(path)
    }
    if (!night) {
        // the light: a soft white halo, a white case, and the slope's colours
        pieces(head) { path ->
            drawPath(path, Color.White, alpha = (if (done) .28f else .38f) * dm, style = st(14f * wf))
            drawPath(path, ui.case, alpha = dm, style = st(6.4f * wf))
        }
        var i = 0
        while (i < n - 1 && line.m[i] < head) {
            if (line.vis[i] && line.vis[i + 1]) {
                val b = if (line.m[i + 1] <= head) Offset(line.x[i + 1], line.y[i + 1]) else line.at(head)
                drawLine(Color(d.slopeColor(line.deg[i])), Offset(line.x[i], line.y[i]), b, 3.6f * wf, StrokeCap.Round, alpha = dm)
            }
            i++
        }
        // chevrons down the fall line, every 240 m
        var m = 240f
        while (m < head - 30) {
            if (line.vis[line.index(m)]) {
                val q = line.at(m); val a = line.dir(m); val s = 1.1f * wf
                val path = Path().apply { moveTo(q.x - a.x * 1.6f * s - a.y * 2.1f * s, q.y - a.y * 1.6f * s + a.x * 2.1f * s); lineTo(q.x + a.x * 1.3f * s, q.y + a.y * 1.3f * s); lineTo(q.x - a.x * 1.6f * s + a.y * 2.1f * s, q.y - a.y * 1.6f * s - a.x * 2.1f * s) }
                drawPath(path, Color.White, alpha = dm, style = st(1.1f * wf))
            }
            m += 240f
        }
        // a bead every half kilometre (its number is drawn on the screen, over the picture)
        m = 500f
        while (m <= head && m < line.length) {
            if (line.vis[line.index(m)]) { val q = line.at(m); drawCircle(Color.White, 2.3f * wf, q, alpha = dm); drawCircle(Color(0xFF0E2233), 2.3f * wf, q, alpha = dm, style = Stroke(.9f * wf)) }
            m += 500f
        }
    } else {
        // night: a warm glow along the run and a string of lights, each with its slope's colour as a halo
        pieces(head) { path -> drawPath(path, Color(0xFFFFD08A), alpha = .22f * dm, style = st(12f * wf)) }
        var m = 0f
        while (m <= head) {
            val i = line.index(m)
            if (line.vis[i]) {
                val q = line.at(m); val c = Color(d.slopeColor(line.deg[i]))
                drawCircle(c, 3.4f * wf, q, alpha = .45f * dm)
                drawCircle(Color(0xFFFFF3D6), 1.7f * wf, q, alpha = dm)
            }
            m += 38f
        }
    }
    // the light's head while it runs down
    if (!done) {
        val q = line.at(head)
        drawCircle(Color.White, 10f * wf, q, alpha = .45f * dm)
        drawCircle(Color.White, 4f * wf, q, alpha = dm)
    }
    // start and end
    val s0 = Offset(line.x[0], line.y[0]); val e = Offset(line.x[n - 1], line.y[n - 1])
    if (line.vis[0]) { drawCircle(Color.White, 3.6f * wf, s0, alpha = dm); drawCircle(ui.acc, 3.6f * wf, s0, alpha = dm, style = Stroke(1.6f * wf)) }
    if (done && line.vis[n - 1]) { drawCircle(ui.acc, 3f * wf, e, alpha = dm); drawCircle(Color.White, 3f * wf, e, alpha = dm, style = Stroke(1.2f * wf)) }
}

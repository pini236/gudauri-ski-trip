package io.github.pini236.skiapp.redesign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.pini236.skiapp.ui.Motion
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.pini236.skiapp.qa.Qa

/**
 * Phase B, the first sketch: the mountain as a live model with the free camera of today's map, in the new look (the
 * light of the hour in the shader, one detail texture, the haze of the valleys), with the runs and lifts on it and a
 * tap that lights a run. The hour slider moves the sun along 12 January, as on the painted picture.
 */
@Composable
fun LiveMountain(scene: MapScene, mode: DayNight.Mode, haptics: Haptics, onPainted: () -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by produceState<PeakData?>(null) { value = withContext(Dispatchers.IO) { PeakData.load(context) } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var scrubOpen by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    // the emulator's held moment (ms since the choice), null in use
    var held by remember { mutableStateOf<Float?>(null) }
    val time = scrub?.let { PeakLight.janAt(it) } ?: DayNight.lightTime(now, mode)
    val night = remember(time) { Sky.at(time).dark }
    val view = remember { LiveView(context, scene).also { v -> v.onChoose = { k -> chosen = k; haptics.tick(.5f); Qa.log("redesign live chosen $k") } } }
    DisposableEffect(Unit) {
        val life = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> view.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> {}
            }
        }
        life?.addObserver(obs)
        Qa.log("redesign live open")
        onDispose { life?.removeObserver(obs); view.release(); Qa.log("redesign live closed") }
    }
    // the emulator's pictures (qa/run.sh): an hour of 12 January (the designer's keys), and a view
    LaunchedEffect(RedesignQa.seq) {
        val c = RedesignQa.cmd
        when {
            c.startsWith("hour:") -> c.removePrefix("hour:").toFloatOrNull()?.let { scrub = it; Qa.log("redesign live hour $it") }
            c.startsWith("look:") -> c.removePrefix("look:").split(",").let { a ->
                val n = a.take(3).mapNotNull { it.toFloatOrNull() }
                if (n.size == 3) view.look(n[0], n[1], n[2], a.getOrNull(3)?.takeIf { it.isNotBlank() })
            }
            // a run chosen the way a tap chooses it (the turn to the Kobi side included), letting it go, and the sign
            c.startsWith("pick:") -> { held = null; view.pickKey(c.removePrefix("pick:")) }
            c == "unpick" -> { held = null; chosen = null; view.choose(null); Qa.log("redesign live unpicked") }
            c == "front" -> view.backToFront()
            // one moment of the choice held (the designer's 0, 150, 300, 600 and 900 ms, as phase A's frame:)
            c.startsWith("moment:") -> c.removePrefix("moment:").split(',').takeIf { it.size == 2 }?.let { (k, t) ->
                t.trim().toFloatOrNull()?.let { ms -> held = ms; view.moment(k.trim(), ms) }
            }
        }
    }
    LaunchedEffect(time) { view.setTime(time) }
    // the "Kobi side" sign shows while the camera looks from within 60 degrees of the Kobi side (the handoff's section 10)
    var kobiSide by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { val k = view.onKobiSide(); if (k != kobiSide) { kobiSide = k; Qa.log("redesign live kobi sign $k") }; delay(250) } }
    LaunchedEffect(data, night) { data?.let { view.setPal(it.pal[if (night) "night" else "day"]) } }

    val bg = if (night) Color(0xD70F1828) else Color(0xDBFFFFFF)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val line = if (night) Color(0x33FFFFFF) else Color(0x220E2233)
    val acc = if (night) Color(0xFFFFD873) else Color(0xFF6B4FD8)
    @Composable fun Pill(modifier: Modifier = Modifier, h: Int = 44, content: @Composable () -> Unit) {
        Box(modifier.height(h.dp).shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp)).background(bg).border(1.dp, line, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { content() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(Modifier.weight(1f).clickable { scrubOpen = !scrubOpen; if (!scrubOpen) scrub = null; haptics.tick(.3f) }) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (night) Color(0xFFFFD873) else Color(0xFFF2B33D)))
                        val sv = scrub
                        val t = PeakLight.clock(sv ?: PeakLight.localHour(time))
                        Text(if (sv != null || scrubOpen) stringResource(R.string.poc_peak_light_at, t) else stringResource(R.string.poc_peak_light_now, t),
                            fontFamily = Plex, fontSize = 14.sp, color = ink, maxLines = 1)
                    }
                }
                Pill(Modifier.clickable { haptics.tick(.3f); onPainted() }) {
                    Text(stringResource(R.string.poc_peak_painted), Modifier.padding(horizontal = 14.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
                Pill(Modifier.clickable { haptics.tick(.3f); onLeave() }) {
                    Text(stringResource(R.string.poc_peak_leave), Modifier.padding(horizontal = 14.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
            }
            AnimatedVisibility(scrubOpen, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                Pill(Modifier.fillMaxWidth(), h = 52) {
                    Slider(value = scrub ?: PeakLight.localHour(time).coerceIn(7f, 23.5f), onValueChange = { scrub = it }, valueRange = 7f..23.5f,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc, inactiveTrackColor = line))
                }
            }
            Text(stringResource(R.string.poc_peak_live_badge), Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0x66081A33)).padding(horizontal = 10.dp, vertical = 3.dp),
                fontFamily = Plex, fontSize = 11.5.sp, color = Color.White)
            AnimatedVisibility(kobiSide, enter = fadeIn(), exit = fadeOut()) {
                val aria = stringResource(R.string.poc_peak_front_aria)
                Pill(Modifier.semantics { contentDescription = aria }.clickable { haptics.tick(.3f); view.backToFront() }) {
                    Text(stringResource(R.string.map_kobi_side), Modifier.padding(horizontal = 16.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
            }
        }
        // the chosen run (K10, as phase A): its sign on the top of the run once the light is down it, and the sheet
        val g = remember(night) { Glass(night) }
        val measurer = rememberTextMeasurer()
        val pd = data
        val run = chosen?.let { k -> pd?.runs?.get(k) }
        var signAt by remember { mutableStateOf<Offset?>(null) }
        val signA = remember { Animatable(0f) }
        LaunchedEffect(chosen, held) {
            signA.snapTo(0f)
            val k = chosen
            if (k == null) { signAt = null; return@LaunchedEffect }
            val h = held
            if (h != null) signA.snapTo(((h - 600f) / 250f).coerceIn(0f, 1f)) else launch { delay(600); signA.animateTo(1f, tween(250)) }
            while (true) { withFrameNanos { }; signAt = view.screenTop(k)?.let { Offset(it[0], it[1]) } }
        }
        val ring = remember { Animatable(1f) }
        var touch by remember { mutableStateOf<Offset?>(null) }
        LaunchedEffect(Unit) { view.onTapAt = { x, y -> touch = Offset(x, y); scope.launch { ring.snapTo(0f); ring.animateTo(1f, tween(if (Motion.reduced(context)) 1 else 300)) } } }
        LaunchedEffect(Unit) { view.onMoment = { x, y, ms -> touch = Offset(x, y); scope.launch { ring.snapTo((ms / 300f).coerceIn(0f, 1f)) } } }
        // the chosen run's K11 marks over the model, every frame: by day arrows down it, a bead each 500 m with its km and
        // the start and the end (once its light is down it, 600 ms, then 200 ms); at night the beads of light
        var mk by remember { mutableStateOf<LiveView.Marks?>(null) }
        var mkMs by remember { mutableStateOf(0f) }
        LaunchedEffect(chosen) { if (chosen == null) { mk = null; return@LaunchedEffect }; while (true) { withFrameNanos { }; mk = view.marks(); mkMs = view.chosenMs() } }
        // phase A's names (only with no run chosen) and the lifts' stations, over the model, every frame the camera moves
        var ov by remember { mutableStateOf<LiveView.Overlay?>(null) }
        LaunchedEffect(pd) { val labels = pd?.hero?.labels ?: return@LaunchedEffect; while (true) { withFrameNanos { }; ov = view.overlay(labels) } }
        val unitM = stringResource(R.string.common_unit_m, "\u0000")
        val nf = remember { java.text.NumberFormat.getIntegerInstance() }
        Canvas(Modifier.fillMaxSize()) {
            val o = ov; val pal = pd?.pal?.get(if (night) "night" else "day")
            if (o != null && pal != null) {
                // stations: 6.8 by 5, corner 1.4, the station colour with the cable's outline (phase A); dimmer under a chosen run
                val sa = if (chosen != null) .4f else 1f
                val w = 6.8f * density; val h = 5f * density; val cr = androidx.compose.ui.geometry.CornerRadius(1.4f * density)
                for (i in 0 until o.stations.size / 2) {
                    val tl = Offset(o.stations[i * 2] - w / 2, o.stations[i * 2 + 1] - h * .84f)
                    drawRoundRect(pal.st, tl, androidx.compose.ui.geometry.Size(w, h), cr, alpha = sa)
                    drawRoundRect(pal.wire, tl, androidx.compose.ui.geometry.Size(w, h), cr, alpha = sa, style = Stroke(1.2f * density))
                }
            }
            // phase A's names belong to its front view: not from the Kobi side, and none cut by the edge of the screen
            if (o != null && chosen == null && !kobiSide) {
                val ink = if (night) Color(0xFFEAF1F8) else Color(0xFF0E2233)
                val halo = if (night) Color(0xF2060B16) else Color(0xF2FFFFFF)
                val name = androidx.compose.ui.text.TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = ink)
                val sub = androidx.compose.ui.text.TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = ink)
                for ((l, x, y) in o.names) {
                    val t1 = measurer.measure(l.t, name)
                    val t2 = l.sub?.let { measurer.measure(unitM.replace("\u0000", nf.format(it)), sub) }
                    val half = maxOf(t1.size.width, t2?.size?.width ?: 0) / 2f + 8 * density
                    if (x - half < 0f || x + half > size.width) continue
                    var yy = y - (t1.size.height + (t2?.size?.height ?: 0)) / 2f
                    for (tx in listOfNotNull(t1, t2)) {
                        val tl = Offset(x - tx.size.width / 2f, yy)
                        for (r in floatArrayOf(7f, 3f, 3f)) drawText(tx, topLeft = tl, shadow = androidx.compose.ui.graphics.Shadow(halo, Offset.Zero, r * density))
                        drawText(tx, topLeft = tl)
                        yy += tx.size.height
                    }
                }
            }
            val m0 = mk
            if (m0 != null && m0.key == chosen) chosenMarks(m0, ((mkMs - 600f) / 200f).coerceIn(0f, 1f), night, measurer)
            val r0 = run; val q = signAt
            if (r0 != null && q != null && signA.value > 0f) sign(r0, q, signA.value, measurer)
            val tp = touch
            if (tp != null && ring.value < 1f) {
                val r = ring.value; val sc = 1f + .35f * r; val dp = density
                drawCircle(Color.White, 14 * dp * sc, tp, alpha = .55f * (1 - r))
                drawCircle(Color.White, 26 * dp * sc, tp, alpha = .9f * (1 - r), style = Stroke(2 * dp))
            }
        }
        AnimatedVisibility(run != null && pd != null && (held ?: 900f) >= 900f, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(150)), exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))) {
            var last by remember { mutableStateOf(run) }
            if (run != null) last = run
            val d0 = pd
            last?.let { r -> if (d0 != null) RunSheet(r, d0, g, onClose = { chosen = null; view.choose(null) }) }
        }
        AnimatedVisibility(chosen == null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp), enter = fadeIn(), exit = fadeOut()) {
            Pill(h = 48) { Text(stringResource(R.string.poc_peak_hint), Modifier.padding(horizontal = 20.dp), fontFamily = Plex, fontSize = 15.sp, color = ink) }
        }
    }
}

/**
 * K11 on the live model, as phase A draws it on the picture (PeakScreen's chosen): by day, arrows down the fall line each
 * 240 m, a bead each 500 m on the longest piece with its km, the start and the end; at night a light each 10.5 dp on the
 * screen, the slope's colour round it. Only where the line is seen (not behind a ridge).
 */
private fun DrawScope.chosenMarks(m: LiveView.Marks, a: Float, night: Boolean, measurer: TextMeasurer) {
    if (a <= 0f) return
    val d = m.down; val dp = density
    // the same colours as the line in the model (SlopeColors, the map's)
    fun col(li: Int, i: Int): Color = io.github.pini236.skiapp.map.SlopeColors.of(d.slopes[li][i]).let { Color(it[0], it[1], it[2]) }
    if (night) {
        val step = 10.5f * dp
        for (li in m.xs.indices) {
            val x = m.xs[li]; val y = m.ys[li]; val ok = m.seen[li]
            var left = 0f
            for (i in 0 until x.size - 1) {
                if (!ok[i] || !ok[i + 1]) { left = 0f; continue }
                val dx = x[i + 1] - x[i]; val dy = y[i + 1] - y[i]; val len = kotlin.math.hypot(dx, dy)
                var t = left
                while (t < len) {
                    val q = Offset(x[i] + dx * t / len, y[i] + dy * t / len)
                    drawCircle(Color(0xFFFFF3D6), 2.3f * dp, q, alpha = a)
                    drawCircle(col(li, i), 4.4f * dp, q, alpha = .4f * a)
                    t += step
                }
                left = t - len
            }
        }
        return
    }
    val navy = Color(0xFF0E2233)
    for (li in m.xs.indices) {
        val x = m.xs[li]; val y = m.ys[li]; val ok = m.seen[li]; val al = d.along[li]
        var acc = 0f
        for (j in 1 until x.size - 1) {
            acc += al[j] - al[j - 1]
            if (acc > 240f && ok[j] && ok[j + 1]) {
                acc = 0f
                var ax = x[j + 1] - x[j - 1]; var ay = y[j + 1] - y[j - 1]; val n = kotlin.math.hypot(ax, ay)
                if (n < 1f) continue
                ax /= n; ay /= n
                val p = Path().apply {
                    moveTo(x[j] - 1.6f * dp * ax - 2.1f * dp * ay, y[j] - 1.6f * dp * ay + 2.1f * dp * ax); lineTo(x[j] + 1.3f * dp * ax, y[j] + 1.3f * dp * ay)
                    lineTo(x[j] - 1.6f * dp * ax + 2.1f * dp * ay, y[j] - 1.6f * dp * ay - 2.1f * dp * ax)
                }
                drawPath(p, Color.White, alpha = a, style = Stroke(1.3f * dp, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
    // on the longest piece: a bead each 500 m with its km (white, a dark outline under the letters), the start and the end
    val mi = d.main; val x = m.xs[mi]; val y = m.ys[mi]; val ok = m.seen[mi]; val al = d.along[mi]
    val style = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color.White)
    var b = 500f; var j = 0
    while (b < (al.lastOrNull() ?: 0f)) {
        while (j < al.size - 2 && al[j + 1] < b) j++
        if (ok[j] && ok[j + 1]) {
            val f = ((b - al[j]) / (al[j + 1] - al[j]).coerceAtLeast(.01f)).coerceIn(0f, 1f)
            val q = Offset(x[j] + (x[j + 1] - x[j]) * f, y[j] + (y[j + 1] - y[j]) * f)
            drawCircle(Color.White, 3.1f * dp, q, alpha = a); drawCircle(navy, 3.1f * dp, q, alpha = a, style = Stroke(1.2f * dp))
            var ax = x[j + 1] - x[j]; var ay = y[j + 1] - y[j]; val n = kotlin.math.hypot(ax, ay).coerceAtLeast(.01f); ax /= n; ay /= n
            val c = Offset(q.x + 13f * dp * ay, q.y - 13f * dp * ax)
            val s = ("%.1f".format(b / 1000f)).trimEnd('0').trimEnd('.')
            val t = measurer.measure(s, style); val o = measurer.measure(s, style.copy(color = Color(0xFF0A1932)))
            val tl = Offset(c.x - t.size.width / 2f, c.y - t.size.height * .72f)
            val r = 1.2f * dp
            for (k in 0 until 8) { val an = k * Math.PI / 4; drawText(o, topLeft = Offset(tl.x + r * kotlin.math.cos(an).toFloat(), tl.y + r * kotlin.math.sin(an).toFloat()), alpha = a * .8f / 2.2f) }
            drawText(t, topLeft = tl, alpha = a)
        }
        b += 500f
    }
    if (ok.isNotEmpty() && ok[0]) { val q = Offset(x[0], y[0]); drawCircle(Color.White, 4.6f * dp, q, alpha = a); drawCircle(ACCENT_DAY, 4.6f * dp, q, alpha = a, style = Stroke(2f * dp)) }
    val e = x.size - 1
    if (e > 0 && ok[e]) { val q = Offset(x[e], y[e]); drawCircle(ACCENT_DAY, 3.6f * dp, q, alpha = a); drawCircle(Color.White, 3.6f * dp, q, alpha = a, style = Stroke(1.4f * dp)) }
}

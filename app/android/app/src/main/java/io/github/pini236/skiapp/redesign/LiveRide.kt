package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.SlopeColors
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Motion
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The run's way down: its pieces joined top to bottom (FilmRules.chain), or null without a line. */
internal fun filmLine(scene: MapScene, key: String): FilmLine? {
    val p = scene.runs.pistes.firstOrNull { it.key == key } ?: return null
    return FilmRules.chain(scene.topDown(p))?.let(::FilmLine)
}

/** The other runs' two ends (their longest line's top and bottom), for the meetings along the film's line. */
internal fun filmEnds(scene: MapScene): List<Pair<String, List<FloatArray>>> = scene.runs.pistes.mapNotNull { p ->
    // a join is named, never a bare section's key (Kikilo 1 showed "u1217683225", the review of 25.11)
    if (!p.named) return@mapNotNull null
    val ls = scene.topDown(p); if (ls.isEmpty()) return@mapNotNull null
    p.title to ls.flatMap { l -> val n = l.size / 3; listOf(floatArrayOf(l[0], l[2]), floatArrayOf(l[(n - 1) * 3], l[(n - 1) * 3 + 2])) }
}

/**
 * "To go down in a film" (board K12, part 4's handoff, section 14): the run's sheet becomes the film's; a dot goes down
 * the line at 10 m a second times the speed, the camera behind it as today's flight, a rail on the left (the run's
 * profile in the new language), the numbers here, the next meeting. [free] is the screen's top part above the sheet.
 */
@Composable
internal fun BoxScope.RideFilm(key: String, title: String, sign: @Composable () -> Unit, scene: MapScene, view: LiveView, night: Boolean,
                               haptics: Haptics, hapticsOn: Boolean, onClose: () -> Unit) {
    val line = remember(scene, key) { filmLine(scene, key) } ?: run { LaunchedEffect(Unit) { onClose() }; return }
    val joins = remember(line) { FilmRules.joins(line, title, filmEnds(scene)) }
    val k = remember(night) { SheetInk(night) }
    val context = LocalContext.current
    val dens = LocalDensity.current.density
    val reduced = remember { Motion.reduced(context) }
    val reader = remember { (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)?.isTouchExplorationEnabled == true }
    val nf = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val km = remember { NumberFormat.getNumberInstance(Locale.getDefault()).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 } }
    val screenH = LocalConfiguration.current.screenHeightDp
    val sheetH = 296
    val free = ((screenH - sheetH).toFloat() / screenH).coerceIn(.3f, .8f)

    var d by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(FilmRules.SPEEDS[0]) }
    var started by remember { mutableStateOf(0L) }
    var scrubbed by remember { mutableStateOf(false) }
    var band by remember { mutableIntStateOf(0) }
    var lastTick by remember { mutableStateOf(0L) }
    val atEnd = d >= line.length - .5f

    fun end(completed: Boolean) {
        if (started == 0L) return
        Telemetry.event("run_fly_end", mapOf("run" to key, "completed" to completed, "seconds" to ((System.currentTimeMillis() - started) / 1000.0).roundToInt()))
        started = 0L
    }
    fun play() {
        if (atEnd) d = 0f
        if (started == 0L) { started = System.currentTimeMillis(); Telemetry.event("run_fly_start", mapOf("run" to key)) }
        playing = true; Qa.log("redesign ride play")
    }
    fun moved(to: Float) {
        d = to.coerceIn(0f, line.length)
        if (!hapticsOn) { band = (line.dropAt(d) / 100f).toInt(); return }
        val now = System.currentTimeMillis()
        val (b, go) = FilmRules.tick(line, d, band, now - lastTick, speed)
        band = b
        if (go) { lastTick = now; if (FilmRules.strong(line, d)) haptics.click(.6f) else haptics.tick(.4f) }
    }
    fun scrub(to: Float) {
        playing = false
        if (!scrubbed) { scrubbed = true; Telemetry.event("run_profile_scrub", mapOf("run" to key)) }
        moved(to)
    }
    fun close() { playing = false; end(false); onClose() }

    BackHandler { close() }
    DisposableEffect(Unit) { onDispose { view.filmTouch = null } }
    // opens standing; starts by itself after 600 ms (not with reduced motion, nor with a screen reader)
    LaunchedEffect(Unit) {
        Qa.log("redesign ride open $key ${line.length.roundToInt()}")
        if (reduced) view.frameRunAbove(key, free)
        if (!reduced && !reader) { kotlinx.coroutines.delay(600); play() }
    }
    // the clock: 10 m a second times the speed; the bottom stops it there
    LaunchedEffect(playing, speed) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (playing) {
            val t = withFrameNanos { it }
            val dt = ((t - last) / 1e9f).coerceAtMost(.1f); last = t
            moved(d + FilmRules.BASE * speed * dt)
            if (d >= line.length) { playing = false; end(true); Qa.log("redesign ride end") }
        }
    }
    // the camera follows the dot (not with reduced motion: there the view keeps the whole run framed)
    var yaw by remember { mutableFloatStateOf(Float.NaN) }
    var framedEnd by remember { mutableStateOf(false) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val t = withFrameNanos { it }
            val dt = ((t - last) / 1e9f).coerceAtMost(.1f); last = t
            val target = line.yawAt(d)
            yaw = if (yaw.isNaN()) target else {
                var dd = target - yaw
                while (dd > Math.PI) dd -= (2 * Math.PI).toFloat()
                while (dd < -Math.PI) dd += (2 * Math.PI).toFloat()
                yaw + dd * (1 - Math.exp(-2.5 * dt).toFloat())
            }
            // at the bottom, stopped: the whole run framed above the sheet, not the camera left behind the dot (Pini,
            // 25.11.2026); "again from the top" or a finger on the line takes the camera back to the dot
            if (!playing && d >= line.length - .5f) {
                if (!framedEnd) { framedEnd = true; view.frameRunAbove(key, free); Qa.log("redesign ride framed end") }
                continue
            }
            framedEnd = false
            val p = line.at(d)
            view.filmCamera(p[0], p[1], p[2], yaw, free)
        }
    }
    // a finger on the mountain: within 44 of the line it moves the dot there; elsewhere it stops the film
    LaunchedEffect(line) {
        var dragging = false
        view.filmTouch = { what, x, y ->
            val reach = 44 * dens
            fun nearOnScreen(): Float? {
                var best = reach; var at: Float? = null
                var s = 0f
                while (s <= line.length) {
                    val p = line.at(s)
                    view.toScreen(p[0], p[2], 3f)?.let { q ->
                        val gap = hypot(q[0] - x, q[1] - y); if (gap < best) { best = gap; at = s }
                    }
                    s += 10f
                }
                return at
            }
            when (what) {
                0 -> { val at = nearOnScreen(); dragging = at != null; if (at != null) scrub(at) else { playing = false; Qa.log("redesign ride stopped") } }
                1 -> if (dragging) nearOnScreen()?.let { scrub(it) }
                else -> dragging = false
            }
        }
    }

    // the dot on the line (the "where am I" dot of the board, .you)
    var dotAt by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(line) {
        while (true) {
            withFrameNanos { }
            val p = line.at(d)
            dotAt = view.toScreen(p[0], p[2], 3f)?.let { Offset(it[0], it[1]) }
        }
    }
    dotAt?.let { a ->
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x402F7BFF), 15.dp.toPx(), a)
            drawCircle(Color.White, 8.dp.toPx() + 1.5.dp.toPx(), a)
            drawCircle(YOU_BLUE, 8.dp.toPx() - 1.5.dp.toPx(), a)
        }
    }

    // the rail on the left in every language: the line's slope colours from the top down, by distance; the handle
    val railH = minOf(400, screenH - sheetH - 16 - 64 - 24).coerceAtLeast(120)
    val colours = remember(line) {
        val steps = 60
        List(steps + 1) { i -> val c = SlopeColors.of(line.slopeAt(line.length * i / steps).toFloat()); Color(c[0], c[1], c[2]) }
    }
    val progressText = stringResource(R.string.app_ride_progress, km.format(d / 1000f), km.format(line.length / 1000f))
    val railA11y = stringResource(R.string.app_ride_rail_a11y, km.format(d / 1000f), km.format(line.length / 1000f))
    val nextNow = FilmRules.next(joins, d)
    val nextWords = nextNow?.let { stringResource(R.string.app_ride_next_join, nf.format(it.first), it.second) } ?: stringResource(R.string.app_ride_no_joins)
    val altWords = stringResource(R.string.app_ride_alt_cap)
    val slopeWords = stringResource(R.string.app_ride_slope_cap)
    Box(Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).statusBarsPadding().padding(top = 64.dp).absoluteOffset(x = 3.dp).width(44.dp).height(railH.dp)
        .semantics {
            contentDescription = railA11y
            stateDescription = "${nf.format(line.altAt(d))} $altWords, ${line.slopeAt(d)}° $slopeWords, $nextWords"
            progressBarRangeInfo = ProgressBarRangeInfo(d, 0f..line.length, (line.length / FilmRules.STEP_M).toInt())
            setProgress { v -> scrub((v / FilmRules.STEP_M).roundToInt() * FilmRules.STEP_M); true }
        }
        .pointerInput(line) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: continue
                    if (c.pressed) { scrub((c.position.y / size.height).coerceIn(0f, 1f) * line.length); c.consume() }
                }
            }
        }) {
        Box(Modifier.align(Alignment.Center).width(30.dp).fillMaxSize().clip(RoundedCornerShape(15.dp)).background(k.surf).border(1.dp, k.line, RoundedCornerShape(15.dp))) {
            Box(Modifier.align(Alignment.Center).width(8.dp).fillMaxSize().padding(vertical = 12.dp).clip(RoundedCornerShape(4.dp)).background(Brush.verticalGradient(colours)))
            val f = if (line.length > 0f) d / line.length else 0f
            Canvas(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                val y = size.height * f
                drawCircle(Color.White, 9.dp.toPx(), Offset(size.width / 2, y))
                drawCircle(YOU_BLUE, 9.dp.toPx(), Offset(size.width / 2, y), style = Stroke(3.dp.toPx()))
            }
        }
    }

    // the film's sheet: glass, 296 high, a handle, the sign and the name with how far, three numbers, the next meeting,
    // the main button and the speed, the hint
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(sheetH.dp).shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        .pullSheet(onDown = { close() })
        .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sign()
            Text("⁦$title⁩", Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = k.ink, maxLines = 1)
            Text(progressText, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = k.ink2, style = tabular)
        }
        Row(Modifier.fillMaxWidth()) {
            RideNumber(nf.format(line.altAt(d)), altWords, k, Modifier.weight(1f))
            RideNumber("${line.slopeAt(d)}°", slopeWords, k, Modifier.weight(1f))
            RideNumber(km.format((line.length - d).coerceAtLeast(0f) / 1000f), stringResource(R.string.app_ride_left_cap), k, Modifier.weight(1f))
        }
        Text(nextWords, fontFamily = Gudi, fontSize = 14.sp, color = k.ink, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val main = when {
                playing -> stringResource(R.string.run_fly_stop)
                atEnd -> stringResource(R.string.app_ride_again)
                else -> "▶ " + stringResource(R.string.app_ride_continue)
            }
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(k.acc)
                .semantics { role = Role.Button }.clickable { haptics.tick(.3f); if (playing) { playing = false; Qa.log("redesign ride stopped") } else play() },
                contentAlignment = Alignment.Center) {
                Text(main, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.onAcc)
            }
            val speedA11y = stringResource(R.string.app_ride_speed_a11y, speed.toString())
            Box(Modifier.width(64.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, k.line, RoundedCornerShape(24.dp))
                .semantics { role = Role.Button; contentDescription = speedA11y }
                .clickable { haptics.tick(.3f); speed = FilmRules.nextSpeed(speed); Qa.log("redesign ride speed $speed") },
                contentAlignment = Alignment.Center) {
                Text("⁦×$speed⁩", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink, style = tabular)
            }
        }
        val hint = stringResource(R.string.app_ride_hint) + if (hapticsOn) " " + stringResource(R.string.app_ride_hint_haptic) else ""
        Text(hint, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, lineHeight = 15.sp)
    }
    // the emulator's switches (qa.redesign ride:<metres>, ride:play, ride:stop, ride:speed)
    LaunchedEffectQa { c ->
        when {
            c == "ride:play" -> play()
            c == "ride:stop" -> playing = false
            c == "ride:speed" -> { speed = FilmRules.nextSpeed(speed); Qa.log("redesign ride speed $speed") }
            c == "ride:close" -> close()
            // where the dot stands against the run's drawn line (metres to the nearest end of a piece of it), and whether
            // the camera is over the snow and sees it
            c == "ride:check" -> {
                val p = line.at(d); val drawn = scene.topDown(scene.runs.pistes.first { it.key == key }).filter { it.size >= 6 }
                val toEnd = drawn.minOf { l -> hypot(l[l.size - 3] - p[0], l[l.size - 1] - p[2]) }
                Qa.log("redesign ride check $key at ${d.roundToInt()} of ${line.length.roundToInt()} end ${toEnd.roundToInt()} clear ${view.filmClear(p[0], p[1], p[2])} ${view.cameraWords()}")
            }
            // every run's film, every 5 % of the way: the camera over the snow and the dot in sight, and the end on an
            // end of the drawn line (one line per run that fails, then the count)
            c == "ride:all" -> {
                var n = 0; var bad = 0
                for (r in scene.runs.pistes) {
                    val l = filmLine(scene, r.key) ?: continue; n++
                    val drawn = scene.topDown(r).filter { it.size >= 6 }
                    val e = l.at(l.length)
                    val toEnd = drawn.minOf { q -> hypot(q[q.size - 3] - e[0], q[q.size - 1] - e[2]) }
                    val snowy = (1..19).map { it * .05f }.filter { f ->
                        val p = l.at(l.length * f); !view.filmClearAt(p[0], p[1], p[2], l.yawAt(l.length * f), free)
                    }
                    if (snowy.isNotEmpty() || toEnd > 5f) {
                        bad++; Qa.log("redesign ride all ${r.key} end ${toEnd.roundToInt()} snow ${snowy.joinToString(",") { "${(it * 100).roundToInt()}%" }}")
                    }
                }
                Qa.log("redesign ride all $n bad $bad")
            }
            c.startsWith("ride:") -> c.removePrefix("ride:").toFloatOrNull()?.let { scrub(it); Qa.log("redesign ride at ${it.roundToInt()}") }
        }
    }
}

private val tabular = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun RideNumber(n: String, cap: String, k: SheetInk, m: Modifier) {
    Column(m, horizontalAlignment = Alignment.Start) {
        Text(n, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = k.ink, style = tabular, maxLines = 1)
        Text(cap, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 1, textAlign = TextAlign.Start)
    }
}

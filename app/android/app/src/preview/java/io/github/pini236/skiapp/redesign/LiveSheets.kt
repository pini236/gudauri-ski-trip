package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.analysis.Analyzer
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The completion round, part 1 (design/handoff/2026-11-21-redesign-day-gaps): the live screen's sheets when you are not
 * skiing or riding. Android's sheet (section 8): full width from the bottom, rounded only on top, an opaque surface at
 * 92 % with no blur, a border only on top.
 */
internal class SheetInk(val night: Boolean) {
    /** Text on red (part 5, section 1): white by day, dark at night, where red is light. */
    val onRed get() = if (night) Color(0xFF1B0705) else Color.White
    val ink = if (night) Color(0xFFEAF1F8) else Color(0xFF0E2233)
    val ink2 = if (night) Color(0xFFA9B9CC) else Color(0xFF40556A)
    val line = if (night) Color(0x2EEAF1F8) else Color(0x240E2233)
    val surf = if (night) Color(0xEB0F1828) else Color(0xEBF7FAFD)
    val good = if (night) Color(0xFF46D08A) else Color(0xFF198549)
    val acc = if (night) Color(0xFFB79CFF) else Color(0xFF6B3FD4)
    val veil = if (night) Color(0x94060B16) else Color(0x705C7294)
    val halo = if (night) Color(0xF2060B16) else Color(0xF2FFFFFF)
    val red = Color(0xFFE5352B)
    val ridgeFill = if (night) Color(0x24B79CFF) else Color(0x1A6B3FD4)
    val ridgeLift = if (night) Color(0xFFA9B9CC) else Color(0xFF40556A)
}

@Composable
internal fun LiveSheet(k: SheetInk, opaque: Boolean = false, content: @Composable () -> Unit) {
    val top = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    // fully opaque on Android: no line, tag or name of the mountain shows through (the designer, 24.11.2026, handoff
    // 2026-11-21-redesign-around section 14.5); [opaque] is kept for the callers that asked for it before
    Column(Modifier.fillMaxWidth().shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
        .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
        content()
    }
}

/** The day so far as four numbers (P01): descents, metres down, km skied, top speed (a dash before the first descent). */
@Composable
internal fun DayFour(stats: Analyzer.Stats?, k: SheetInk) {
    val cells = listOf(
        (stats?.descents?.toString() ?: "0") to pluralStringResource(R.plurals.app_day_sum_descents_n, stats?.descents ?: 0),
        metres(stats?.dropM ?: 0.0) to stringResource(R.string.app_rec_m_down),
        "%.1f".format(Locale.ROOT, (stats?.skiDistM ?: 0.0) / 1000) to stringResource(R.string.app_rec_km_ski),
        (stats?.maxKmh?.takeIf { stats.descents > 0 }?.let { LiveNumbers.maxKmh(it) } ?: "—") to stringResource(R.string.app_rec_kmh_max),
    )
    // the top speed is read as one item (part 8, section 2): "68.3 kilometers per hour, top speed"
    val speed = stats?.maxKmh?.takeIf { stats.descents > 0 }?.let { stringResource(R.string.app_sr_speed_max, LiveNumbers.maxKmh(it)) }
    @Composable fun cell(i: Int, m: Modifier) {
        val (v, l) = cells[i]
        Column(m.clearAndSetSemantics { contentDescription = if (i == 3 && speed != null) speed else "$v $l" }) {
            Text(v, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(27f), color = k.ink, maxLines = 1)
            Text(l, fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2, modifier = Modifier.padding(top = 4.dp), maxLines = 2)
        }
    }
    // in large text the four numbers go to two rows of two (part 8, section 1)
    if (bigText()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (r in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { cell(r * 2, Modifier.weight(1f)); cell(r * 2 + 1, Modifier.weight(1f)) }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in cells.indices) cell(i, Modifier.weight(1f))
    }
}

/**
 * The ridge of the day so far (P01): time left to right in every language, height from the day's low to its high with
 * 40 m of margin; descents in the speed light, rides dotted, the rest faint; your dot at the right end. No future.
 * [elev] per point of [paint]'s track.
 */
@Composable
internal fun MiniRidge(paint: DayPaint.Day?, elev: FloatArray?, night: Boolean, k: SheetInk, onTap: () -> Unit) {
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    val desc = stringResource(R.string.app_rec_ridge_a11y)
    Canvas(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(8.dp))
        .semantics { contentDescription = desc; role = Role.Button }.clickable { Qa.log("redesign rec ridge"); onTap() }) {
        val tr = paint?.track ?: return@Canvas
        if (elev == null || tr.t.size < 2 || elev.size != tr.t.size) return@Canvas
        val lo = elev.min() - 40f; val hi = elev.max() + 40f
        val t0 = tr.t.first(); val span = (tr.t.last() - t0).coerceAtLeast(1).toFloat()
        val dot = 6.5f * density
        fun px(t: Long) = (t - t0) / span * (size.width - dot * 2)
        fun py(e: Float) = size.height - (e - lo) / (hi - lo) * size.height
        val area = Path().apply { moveTo(px(tr.t[0]), size.height); for (i in tr.t.indices) lineTo(px(tr.t[i]), py(elev[i])); lineTo(px(tr.t.last()), size.height); close() }
        drawPath(area, k.ridgeFill)
        val lift = { t: Long -> paint.liftT.any { t >= it[0] && t <= it[1] } }
        for (i in 0 until tr.t.size - 1) {
            val a = Offset(px(tr.t[i]), py(elev[i])); val b = Offset(px(tr.t[i + 1]), py(elev[i + 1]))
            when {
                tr.down[i] -> drawLine(Color(0xFF000000.toInt() or ramp[tr.band[i]]), a, b, 2.4f * density, cap = StrokeCap.Round)
                lift(tr.t[i]) -> drawLine(k.ridgeLift, a, b, 1.3f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * density, 3.4f * density)))
                else -> drawLine(k.ink, a, b, 1f * density, alpha = .55f)
            }
        }
        val end = Offset(px(tr.t.last()), py(elev.last()))
        drawCircle(Color.White, 6.5f * density, end); drawCircle(Color(0xFF2F7BFF), 4.5f * density, end)
    }
}

/** A secondary button (52 high, a border on [k]'s line). */
@Composable
internal fun SecondaryButton(text: String, k: SheetInk, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 52.dp).widthIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, k.line, RoundedCornerShape(26.dp))
        .semantics { role = Role.Button }.clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink, maxLines = 2)
    }
}

/**
 * "Finish" outside a descent (section 2): held for a second, the fill growing right to left; let go before and nothing
 * happens. With a screen reader, a tap (the system then asks to confirm with its own double tap, which is the action).
 */
@Composable
internal fun HoldStop(k: SheetInk, haptics: Haptics, onStop: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hold = remember { Animatable(0f) }
    val label = stringResource(R.string.app_rec_stop)
    Box(Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, k.line, RoundedCornerShape(26.dp))
        // a quick tap does nothing (it must be held); a screen reader's double tap is the action
        .semantics { role = Role.Button; contentDescription = label; onClick(label) { Qa.log("redesign rec sheet stop a11y"); onStop(); true } }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                haptics.tick(.3f)
                val job = scope.launch { hold.animateTo(1f, tween(1000, easing = LinearEasing)); haptics.tick(.8f); Qa.log("redesign rec sheet hold done"); onStop() }
                waitForUpOrCancellation()?.consume()
                if (hold.value < 1f) { job.cancel(); scope.launch { hold.animateTo(0f, tween(200)) } }
            }
        }) {
        Box(Modifier.matchParentSize().padding(0.dp)) {
            Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(hold.value.coerceAtLeast(0.0001f)).height(52.dp).background(k.red.copy(alpha = .22f)))
        }
        Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(k.red))
            Text(label, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink)
        }
    }
}

@Composable
private fun SeeAndStop(k: SheetInk, haptics: Haptics, onSee: () -> Unit, onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(stringResource(R.string.app_rec_see_day), k, Modifier.weight(1f)) { haptics.tick(.3f); Qa.log("redesign rec see day"); onSee() }
        HoldStop(k, haptics, onStop)
    }
}

/** P01: standing (or walking), the day so far. */
@Composable
internal fun StandingSheet(walking: Boolean, stats: Analyzer.Stats?, paint: DayPaint.Day?, elev: FloatArray?, night: Boolean, k: SheetInk,
                           haptics: Haptics, onSee: () -> Unit, onRidge: () -> Unit, onStop: () -> Unit) {
    LiveSheet(k) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (walking) R.string.app_rec_walking else R.string.app_rec_standing), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = k.ink)
            Text(stringResource(R.string.app_rec_day_so_far), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
        }
        DayFour(stats, k)
        MiniRidge(paint, elev, night, k, onRidge)
        SeeAndStop(k, haptics, onSee, onStop)
    }
}

/** P02: next to a lift. Only what is known: no "queue", no guess at a wait. */
@Composable
internal fun NearLiftSheet(lift: io.github.pini236.skiapp.data.Lift, name: String, standingS: Long, state: Pair<Boolean, String?>?, stats: Analyzer.Stats?,
                           k: SheetInk, haptics: Haptics, onSee: () -> Unit, onStop: () -> Unit) {
    LiveSheet(k) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) { LiftSign(k.ink) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_rec_near_lift), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
                Text("⁦$name⁩", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = k.ink, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(LiveNumbers.elapsed(standingS * 1000), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 23.sp, color = k.ink)
                Text(stringResource(R.string.app_rec_here_for), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
            }
        }
        @Composable fun row(label: String, value: String, colour: Color) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
                    Text(value, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colour)
                }
            }
        }
        val dur = lift.dur?.toDoubleOrNull()?.let { stringResource(R.string.app_rec_lift_ride, Math.round(it).toInt()) }
        val rise = lift.rise?.toDoubleOrNull()?.let { stringResource(R.string.app_rec_lift_rise, metres(it)) }
        val ride = listOfNotNull(dur, rise).joinToString(" · ")
        if (ride.isNotEmpty()) row(liftKind(lift.kind), ride, k.ink)
        when {
            state == null -> row(stringResource(R.string.app_rec_lift_state), stringResource(R.string.status_no_report), k.ink2)
            state.first -> row(stringResource(R.string.app_rec_lift_state), stringResource(R.string.app_rec_lift_open), k.good)
            else -> row(stringResource(R.string.app_rec_lift_state), stringResource(R.string.app_rec_lift_closed) + (state.second?.let { " · $it" } ?: ""), k.ink)
        }
        if (stats != null) row(stringResource(R.string.app_rec_day_so_far), stringResource(R.string.app_rec_day_line, stats.descents, metres(stats.dropM),
            "%.1f".format(Locale.ROOT, stats.skiDistM / 1000)), k.ink)
        SeeAndStop(k, haptics, onSee, onStop)
    }
}

/** P03: a break, after five minutes standing. Calm: the only number is how long. */
@Composable
internal fun BreakScreen(minutes: Long, stats: Analyzer.Stats?, paint: DayPaint.Day?, elev: FloatArray?, night: Boolean, k: SheetInk,
                         haptics: Haptics, onSee: () -> Unit, onRidge: () -> Unit, onStop: () -> Unit) {
    Box(Modifier.fillMaxSize().background(k.veil)) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 190.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(k.surf), contentAlignment = Alignment.Center) { CupSign(k.ink) }
            Text(stringResource(R.string.app_rec_break), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = k.ink,
                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(k.halo, Offset.Zero, 16f)))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(minutes.toString(), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(64f), color = k.ink,
                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(k.halo, Offset.Zero, 20f)))
                Text(stringResource(R.string.app_rec_minutes, minutes.toInt()).replace(minutes.toString(), "").trim(), fontFamily = Gudi, fontWeight = FontWeight.Medium,
                    fontSize = 17.sp, color = k.ink, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            LiveSheet(k) {
                Text(stringResource(R.string.app_rec_day_so_far), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
                DayFour(stats, k)
                MiniRidge(paint, elev, night, k, onRidge)
                Text(stringResource(R.string.app_rec_break_note), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2, textAlign = TextAlign.Start)
                SeeAndStop(k, haptics, onSee, onStop)
            }
        }
    }
}

/** The lift's sign: a cable and a cabin. */
@Composable
internal fun LiftSign(c: Color) = Canvas(Modifier.size(26.dp)) {
    val w = size.width; val s = 1.8f * density
    drawLine(c, Offset(0f, w * .22f), Offset(w, w * .08f), s, cap = StrokeCap.Round)
    drawLine(c, Offset(w * .5f, w * .15f), Offset(w * .5f, w * .36f), s)
    drawRoundRect(c, Offset(w * .26f, w * .36f), androidx.compose.ui.geometry.Size(w * .48f, w * .46f), androidx.compose.ui.geometry.CornerRadius(3f * density),
        style = androidx.compose.ui.graphics.drawscope.Stroke(s))
    drawLine(c, Offset(w * .26f, w * .56f), Offset(w * .74f, w * .56f), s)
}

/** A cup, for the break. */
@Composable
private fun CupSign(c: Color) = Canvas(Modifier.size(26.dp)) {
    val w = size.width; val s = 1.8f * density
    val st = androidx.compose.ui.graphics.drawscope.Stroke(s)
    drawRoundRect(c, Offset(w * .16f, w * .34f), androidx.compose.ui.geometry.Size(w * .52f, w * .48f), androidx.compose.ui.geometry.CornerRadius(4f * density), style = st)
    drawArc(c, -90f, 180f, false, Offset(w * .56f, w * .42f), androidx.compose.ui.geometry.Size(w * .24f, w * .24f), style = st)
    for (x in listOf(.32f, .48f)) drawLine(c, Offset(w * x, w * .12f), Offset(w * x, w * .24f), s, cap = StrokeCap.Round)
}

@Composable
internal fun liftKind(kind: String): String = when (kind) {
    "chair_lift" -> stringResource(R.string.lift_kind_chair_lift)
    "gondola" -> stringResource(R.string.lift_kind_gondola)
    "drag_lift" -> stringResource(R.string.lift_kind_drag_lift)
    "magic_carpet" -> stringResource(R.string.lift_kind_magic_carpet)
    "platter" -> stringResource(R.string.lift_kind_platter)
    "t_bar" -> stringResource(R.string.lift_kind_t_bar)
    else -> kind
}

/**
 * A row cut at a whole item (the designer, P04): the items in order, [sep] between them, as many as fit; when one does
 * not, [more] ("…") ends the row. Never a name cut in the middle.
 */
@Composable
internal fun WholeChain(n: Int, item: @Composable (Int) -> Unit, sep: @Composable () -> Unit, more: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = {
        for (i in 0 until n) { if (i > 0) sep(); item(i) }
        more()
    }) { ms, c ->
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val ps = ms.map { it.measure(loose) }
        val dots = ps.last(); val parts = ps.dropLast(1)
        val gap = 6.dp.roundToPx()
        val shown = ArrayList<androidx.compose.ui.layout.Placeable>()
        var w = 0
        var cut = false
        var i = 0
        while (i < parts.size) {
            // a group: the separator before an item (from the second) and the item
            val group = if (i == 0) listOf(parts[0]) else listOf(parts[i], parts[i + 1])
            val gw = group.sumOf { it.width } + gap * group.size - (if (i == 0) gap else 0)
            val last = i + group.size >= parts.size
            val room = c.maxWidth - (if (last) 0 else dots.width + gap)
            if (w + gw > room) { cut = true; break }
            shown += group; w += gw; i += group.size
        }
        if (cut) shown += dots
        val h = shown.maxOfOrNull { it.height } ?: 0
        layout(c.maxWidth, h) {
            var x = 0
            for ((j, p) in shown.withIndex()) { if (j > 0) x += gap; p.placeRelative(x, (h - p.height) / 2); x += p.width }
        }
    }
}

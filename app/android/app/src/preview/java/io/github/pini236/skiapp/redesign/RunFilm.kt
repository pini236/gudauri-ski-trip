package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.dayrec.ui.shortRuns
import io.github.pini236.skiapp.dayrec.ui.titleOf
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One descent as a film (board L09): its real line, turned so that it runs from the top of the screen to the bottom,
 * burning in the speed light from the start to the end in a few seconds; a gap (more than 10 s without a reading) is
 * a dotted line between its two ends, never filled in, with how long it was. Beside it the notes of the board, from the
 * analysis: the top (time, the lift you came up on, the height), the top speed (run and time), one stop when there was
 * one, the bottom (time, the next lift, the height); under it distance, metres down and minutes moving. The pill names
 * the runs (the board's "the longest descent of the day" is a moment's title, and this film opens on any descent).
 */
@Composable
internal fun RunFilm(d: DayModel.Descent, seg: org.json.JSONObject?, segs: List<org.json.JSONObject>, paint: DayPaint.Day, runs: Runs?, night: Boolean, haptics: Haptics, onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val dens = context.resources.displayMetrics.density
    val zone = ZoneId.of(io.github.pini236.skiapp.dayrec.DayParams.TZ)
    val hhmm = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT) }
    val bg = if (night) Color(0xFF0A1322) else Color(0xFFF2F5F9)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // the descent's stretches out of the painted day, by time
    val parts = remember(d, paint) {
        paint.lines.mapNotNull { l ->
            val keep = l.t.indices.filter { l.t[it] in d.t0..d.t1 }
            if (keep.size < 2) null else DayPaint.Line(FloatArray(keep.size * 2) { k -> l.xy[keep[k / 2] * 2 + k % 2] }, IntArray(keep.size) { l.band[keep[it]] }, LongArray(keep.size) { l.t[keep[it]] })
        }
    }
    // turned so the line runs down the screen: the start up, the end down
    val turn = remember(parts) {
        val a = parts.firstOrNull(); val b = parts.lastOrNull()
        if (a == null || b == null) 0.0 else {
            val dx = (b.xy[b.xy.size - 2] - a.xy[0]).toDouble(); val dy = (b.xy[b.xy.size - 1] - a.xy[1]).toDouble()
            // map metres: y north; the screen: y down. Turn the start-to-end direction to point down the screen.
            -Math.PI / 2 - atan2(dy, dx)
        }
    }
    val total = remember(parts) { parts.sumOf { it.band.size } }
    val reveal = remember(d) { Animatable(0f) }
    var plays by remember { mutableIntStateOf(0) }
    LaunchedEffect(d, plays) {
        reveal.snapTo(0f); Qa.log("redesign run film start")
        reveal.animateTo(1f, tween(4500, easing = LinearEasing)); Qa.log("redesign run film end")
    }
    val measurer = rememberTextMeasurer()
    val hm = { t: Long -> hhmm.format(Instant.ofEpochMilli(t).atZone(zone)) }
    val firstRun = d.runs.firstOrNull()?.split(" · ")?.firstOrNull()?.let { k -> titleOf(runs, k) }
    val pin = d.maxKmh?.let { stringResource(R.string.app_film_top_speed, LiveNumbers.maxKmh(it)) }
    val pinSub = d.maxAt?.let { t -> firstRun?.let { stringResource(R.string.app_film_top_speed_sub, it, hm(t)) } ?: hm(t) }
    // the lift you came up on and the one you went to (the analysis's segments), and the heights at the top and the bottom
    val liftUp = remember(segs, d) { segs.lastOrNull { it.optString("type") == "lift" && it.optLong("t1") <= d.t0 + 5_000 }?.let { liftTitle(runs, it) } }
    val liftNext = remember(segs, d) { segs.firstOrNull { it.optString("type") == "lift" && it.optLong("t0") >= d.t1 - 5_000 }?.let { liftTitle(runs, it) } }
    fun alt(k: String) = seg?.takeIf { it.has(k) && !it.isNull(k) }?.optDouble(k)?.let { context.getString(R.string.app_film_alt, String.format(Locale.ROOT, "%,d", Math.round(it))) }
    val topNote = (liftUp?.let { stringResource(R.string.app_film_top, hm(d.t0), it) } ?: hm(d.t0)) to alt("top_m")
    val bottomNote = (liftNext?.let { stringResource(R.string.app_film_bottom, hm(d.t1), it) } ?: hm(d.t1)) to alt("bottom_m")
    // one stop on the way (more than one: not named one by one)
    val stop = seg?.optJSONArray("stops")?.takeIf { it.length() == 1 }?.getJSONObject(0)
    val stopTitle = stringResource(R.string.app_tl_one_stop)
    val stopSub = stop?.let { stringResource(R.string.app_film_stop_sub, ((it.optLong("t1") - it.optLong("t0")) / 1000L).toInt(), hm(it.optLong("t0"))) }
    val gapSub = stringResource(R.string.app_film_gap_sub)
    // the gaps between the stretches, in minutes, worded as on board L13
    val gapOne = stringResource(R.string.app_rec_gap_title)
    val gaps = remember(parts) {
        parts.zipWithNext { a, b -> Math.round((b.t[0] - a.t.last()) / 60_000.0).toInt() }
            .map { m -> if (m <= 1) gapOne else context.getString(R.string.app_rec_gap_len, m) }
    }
    val names = d.runs.joinToString(" › ") { leg -> shortRuns(leg.split(" · ").joinToString(" · ") { k -> titleOf(runs, k) }) }.ifEmpty { stringResource(R.string.app_day_off) }

    Box(Modifier.fillMaxSize().background(bg)) {
        Canvas(Modifier.fillMaxSize().statusBarsPadding().padding(top = 72.dp, bottom = 120.dp).pointerInput(d) {
            detectTapGestures { haptics.tick(.3f); plays++ }
        }.semantics { contentDescription = names }) {
            if (parts.isEmpty()) return@Canvas
            val c = cos(turn).toFloat(); val s = sin(turn).toFloat()
            // turned points: x' = x c - y s, y'(screen, down) = -(x s + y c)
            fun tx(x: Float, y: Float) = x * c - y * s
            fun ty(x: Float, y: Float) = -(x * s + y * c)
            var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            for (l in parts) for (i in 0 until l.band.size) { val X = tx(l.xy[i * 2], l.xy[i * 2 + 1]); val Y = ty(l.xy[i * 2], l.xy[i * 2 + 1]); x0 = minOf(x0, X); x1 = maxOf(x1, X); y0 = minOf(y0, Y); y1 = maxOf(y1, Y) }
            // the line in the outer third (left in Hebrew, as the board; right in a left-to-right language), notes beside it
            val colW = size.width * .46f; val pad = 18 * dens
            val k = minOf((colW - pad * 2) / (x1 - x0).coerceAtLeast(1f), (size.height - pad * 2) / (y1 - y0).coerceAtLeast(1f))
            val left = if (rtl) pad + (colW - pad * 2 - (x1 - x0) * k) / 2 else size.width - colW + pad + (colW - pad * 2 - (x1 - x0) * k) / 2
            val top = pad + (size.height - pad * 2 - (y1 - y0) * k) / 2
            fun at(x: Float, y: Float) = Offset(left + (tx(x, y) - x0) * k, top + (ty(x, y) - y0) * k)
            val note = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            val noteX = if (rtl) colW + 8 * dens else 8 * dens
            val subStyle = TextStyle(fontFamily = Plex, fontSize = 12.5.sp, color = muted)
            // the notes are gathered, then spread down the column so none sits on another (the board's flush():
            // sorted by height, pushed apart both ways), each with a thin leader to its point on the line
            class Note(val p: Offset, val tl: androidx.compose.ui.text.TextLayoutResult, val sl: androidx.compose.ui.text.TextLayoutResult?) {
                val h get() = tl.size.height + (sl?.size?.height ?: 0)
            }
            val notes = ArrayList<Note>()
            fun noteAt(p: Offset, text: String, sub: String? = null) {
                val cs = androidx.compose.ui.unit.Constraints(maxWidth = (size.width - colW - 16 * dens).toInt())
                notes += Note(p, measurer.measure(text, note, constraints = cs), sub?.let { measurer.measure(it, subStyle, constraints = cs) })
            }
            fun drawNotes() {
                notes.sortBy { it.p.y }
                val gap = 6 * dens
                val ys = FloatArray(notes.size) { notes[it].p.y - notes[it].h / 2f }
                for (i in 1 until ys.size) ys[i] = maxOf(ys[i], ys[i - 1] + notes[i - 1].h + gap)
                for (i in ys.size - 2 downTo 0) ys[i] = minOf(ys[i], ys[i + 1] - notes[i].h - gap)
                notes.forEachIndexed { i, nt ->
                    val w = maxOf(nt.tl.size.width, nt.sl?.size?.width ?: 0)
                    // in Hebrew the notes are right of the line and read from the right; in English left of it
                    fun xOf(lw: Int) = if (rtl) size.width - 8 * dens - lw else size.width - colW - 8 * dens - lw
                    val edge = if (rtl) size.width - 8 * dens - w else size.width - colW - 8 * dens
                    val ly = ys[i] + nt.h / 2f
                    val mid = Offset(if (rtl) edge - 14 * dens else edge + 14 * dens, ly)
                    drawLine(rule, nt.p, mid, 1f * dens); drawLine(rule, mid, Offset(if (rtl) edge - 4 * dens else edge + 4 * dens, ly), 1f * dens)
                    drawText(nt.tl, topLeft = Offset(xOf(nt.tl.size.width), ys[i]))
                    nt.sl?.let { drawText(it, topLeft = Offset(xOf(it.size.width), ys[i] + nt.tl.size.height)) }
                }
            }
            val shown = (reveal.value * total).toInt()
            var gi = 0
            var n = 0
            // by day the line is saturated on the snow (round 4, the designer's point 5): a thin dark case under it, and the
            // scale from its second step, so a slow stretch is lilac and not near white; at night as on the mountain
            val case = if (night) Color(0xFF050A16) else Color(0x8C0E2233)
            var prevEnd: Offset? = null
            for (l in parts) {
                val first = at(l.xy[0], l.xy[1])
                // a gap is dotted, never filled in, and says how long it was (board L13's words)
                prevEnd?.let { if (n < shown) {
                    drawLine(muted, it, first, 1.4f * dens, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * dens, 4f * dens)))
                    gaps.getOrNull(gi)?.let { g -> noteAt(Offset((it.x + first.x) / 2, (it.y + first.y) / 2), g, gapSub) }
                }; gi++ }
                for (i in 0 until l.band.size - 1) {
                    if (n++ >= shown) break
                    val a = at(l.xy[i * 2], l.xy[i * 2 + 1]); val b = at(l.xy[i * 2 + 2], l.xy[i * 2 + 3])
                    drawLine(case, a, b, if (night) 7f * dens else 5.6f * dens, cap = StrokeCap.Round)
                    drawLine(Color(0xFF000000.toInt() or ramp[if (night) l.band[i] else maxOf(1, l.band[i])]), a, b, 4.2f * dens, cap = StrokeCap.Round)
                }
                prevEnd = at(l.xy[l.xy.size - 2], l.xy[l.xy.size - 1])
            }
            // the notes: the time at the top and at the bottom, and the top speed's pin
            val a = parts.first(); val z = parts.last()
            noteAt(at(a.xy[0], a.xy[1]), topNote.first, topNote.second)
            if (reveal.value >= 1f) noteAt(at(z.xy[z.xy.size - 2], z.xy[z.xy.size - 1]), bottomNote.first, bottomNote.second)
            // the stop, where it was on the line
            stop?.let { st -> val t = st.optLong("t0"); val pp = paint.track.at(t)
                if (pp != null && reveal.value * (d.t1 - d.t0) >= t - d.t0) { val q = at(pp[0], pp[1]); drawCircle(muted, 3.5f * dens, q); noteAt(q, stopTitle, stopSub) } }
            val mt = d.maxAt
            if (pin != null && mt != null) for (l in parts) {
                val i = l.t.indexOfFirst { it >= mt }; if (i < 0) continue
                val p = at(l.xy[i * 2], l.xy[i * 2 + 1])
                if (reveal.value * (d.t1 - d.t0) >= mt - d.t0) {
                    drawCircle(Color.White, 5f * dens, p); drawCircle(Color(0xFF000000.toInt() or ramp[4]), 5f * dens, p, style = androidx.compose.ui.graphics.drawscope.Stroke(2f * dens))
                    noteAt(p, pin, pinSub)
                }
                break
            }
            drawNotes()
        }
        // the pill: back and the runs
        Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth().height(48.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { onBack() }.semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Text(if (rtl) "›" else "‹", fontFamily = Plex, fontSize = 20.sp, color = ink)
            }
            Text(names, Modifier.weight(1f).padding(end = 14.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink, maxLines = 1)
        }
        // the card: distance, metres down, minutes moving, and the scale without words
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
            val ms = d.movingS.toInt()
            for ((v, key) in listOf(String.format(Locale.ROOT, "%.1f", d.distM / 1000.0) to R.string.app_run_km,
                metres(d.dropM) to R.string.app_rec_m_down,
                String.format(Locale.ROOT, "%d:%02d", ms / 60, ms % 60) to R.string.app_film_moving_min)) {
                Column {
                    Text(v, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 30.sp, color = ink)
                    Text(stringResource(key), fontFamily = Plex, fontSize = 12.sp, color = muted)
                }
            }
            Box(Modifier.weight(1f))
            LightScale(night, muted, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

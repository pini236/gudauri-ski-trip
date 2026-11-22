package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The ridge of the day and its timeline (board L07): the height through the day against the time, the descents in the
 * speed light and the rides dotted, the top speed's bubble and the long breaks named; under it the day as a timeline
 * in the new style. Today's words only: the board's "the ridge of the day", "slow", "fast" and "lift" under the scale
 * have no key yet, so the scale is shown without them; and the analysis has no lift item in DayModel, so the rides are on
 * the ridge and not in the list (as on today's day screen).
 */
@Composable
internal fun DayRidge(paint: DayPaint.Day, elev: FloatArray, day: DayModel.Day, runs: Runs?, night: Boolean, sample: Boolean, haptics: io.github.pini236.skiapp.fx.Haptics, onShare: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.of(io.github.pini236.skiapp.dayrec.DayParams.TZ)
    val dens = context.resources.displayMetrics.density
    val bg = if (night) Color(0xFF0A1322) else Color(0xFFF2F5F9)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val hhmm = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT) }
    fun at(t: Long) = hhmm.format(Instant.ofEpochMilli(t).atZone(zone))
    LaunchedEffect(Unit) { Qa.log("redesign day ridge ${day.chapters.sumOf { it.items.size }}") }
    // one descent as a film (board L09), from a descent in the timeline
    var film by remember { androidx.compose.runtime.mutableStateOf<DayModel.Descent?>(null) }
    val descents = remember(day) { day.chapters.flatMap { it.items }.filterIsInstance<DayModel.Descent>() }
    LaunchedEffect(RedesignQa.seq) {
        when (RedesignQa.cmd) {
            // the emulator opens the longest descent
            "runfilm" -> film = descents.maxByOrNull { it.distM }
            "runfilm:off" -> film = null
        }
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 40.dp)) {
            item {
                // the ridge (L07): ground faint, descents in the light, rides dotted, hours under it
                val measurer = rememberTextMeasurer()
                val sm = day.summary
                val unit = stringResource(R.string.app_rec_unit)
                val bubble = sm.maxAt?.let { "${at(it)} · ${LiveNumbers.maxKmh(sm.maxKmh)} $unit" }
                val breakWords = day.chapters.flatMap { it.items }.filterIsInstance<DayModel.Rest>().filter { it.long }
                    .map { it to stringResource(R.string.app_day_lunch, it.minutes) }
                val tr = paint.track
                val lo = remember(elev) { elev.minOrNull() ?: 0f }; val hi = remember(elev) { elev.maxOrNull() ?: 1f }
                val span = (paint.t1 - paint.t0).coerceAtLeast(1).toFloat()
                Canvas(Modifier.fillMaxWidth().height(176.dp).semantics { contentDescription = bubble ?: "" }) {
                    if (tr.t.size < 2) return@Canvas
                    val top = 34 * dens; val bottom = size.height - 18 * dens
                    fun px(t: Long) = ((t - paint.t0) / span).let { if (rtl) 1f - it else it } * size.width
                    fun py(e: Float) = bottom - (e - lo) / (hi - lo).coerceAtLeast(1f) * (bottom - top)
                    val area = Path().apply {
                        moveTo(px(tr.t[0]), bottom); for (i in tr.t.indices) lineTo(px(tr.t[i]), py(elev[i])); lineTo(px(tr.t.last()), bottom); close()
                    }
                    drawPath(area, Color(0xFF000000.toInt() or ramp[0]), alpha = .30f)
                    for (i in 0 until tr.t.size - 1) {
                        val a = Offset(px(tr.t[i]), py(elev[i])); val b = Offset(px(tr.t[i + 1]), py(elev[i + 1]))
                        if (tr.down[i]) {
                            drawLine(if (night) Color(0xFF050A16) else Color.White, a, b, 4.6f * dens)
                            drawLine(Color(0xFF000000.toInt() or ramp[tr.band[i]]), a, b, 2.6f * dens)
                        } else drawLine(if (night) Color(0xFFC9D5E2) else Color(0xFF22303F), a, b, 1.2f * dens,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * dens, 3.5f * dens)), alpha = .75f)
                    }
                    // the hours
                    val small = TextStyle(fontFamily = Plex, fontSize = 11.sp, color = muted)
                    var h = Instant.ofEpochMilli(paint.t0).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
                    while (h.toInstant().toEpochMilli() < paint.t1) {
                        val x = px(h.toInstant().toEpochMilli())
                        val tl = measurer.measure(hhmm.format(h), small)
                        drawLine(rule, Offset(x, bottom), Offset(x, bottom + 3 * dens), 1f * dens)
                        drawText(tl, topLeft = Offset(x - tl.size.width / 2f, bottom + 4 * dens))
                        h = h.plusHours(1)
                    }
                    // the long breaks, named over the ground
                    for ((r, w) in breakWords) {
                        val x = px(r.t0 + r.minutes * 30_000L)
                        val tl = measurer.measure(w, small)
                        drawText(tl, topLeft = Offset((x - tl.size.width / 2f).coerceIn(0f, size.width - tl.size.width), top - tl.size.height - 2 * dens))
                    }
                    // the top speed's bubble
                    val mt = sm.maxAt
                    if (bubble != null && mt != null) {
                        val k = tr.t.binarySearch(mt).let { if (it < 0) (-it - 1).coerceAtMost(tr.t.size - 1) else it }
                        val p = Offset(px(mt), py(elev[k]))
                        val tl = measurer.measure(bubble, TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                        val w = tl.size.width + 16 * dens; val bh = tl.size.height + 6 * dens
                        val left = (p.x - w / 2).coerceIn(0f, size.width - w); val y = (p.y - 10 * dens - bh).coerceAtLeast(0f)
                        drawLine(Color(0xFF0E2233), p, Offset(p.x, y + bh), 1.2f * dens)
                        drawRoundRect(Color(0xFF0E2233), Offset(left, y), Size(w, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2))
                        drawText(tl, topLeft = Offset(left + 8 * dens, y + 3 * dens))
                        drawCircle(Color.White, 3.5f * dens, p); drawCircle(Color(0xFF000000.toInt() or ramp[4]), 3.5f * dens, p, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * dens))
                    }
                }
                // the scale, without words (no key yet), and a dotted piece for the rides
                Row(Modifier.padding(top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(120.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(ramp.map { Color(0xFF000000.toInt() or it) }.let { if (rtl) it.reversed() else it })))
                    Canvas(Modifier.width(28.dp).height(6.dp)) {
                        drawLine(if (night) Color(0xFFC9D5E2) else Color(0xFF22303F), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.2f * dens,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * dens, 3.5f * dens)))
                    }
                }
            }
            for (ch in day.chapters) {
                item {
                    Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (ch.afternoon) R.string.app_day_afternoon else R.string.app_day_morning), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = muted)
                        Text(stringResource(R.string.app_day_n_descents, ch.descents), fontFamily = Plex, fontSize = 12.sp, color = muted)
                    }
                }
                items(ch.items) { it -> TimelineRow(it, at(it.t0), runs, night, ink, muted, rule) { if (it is DayModel.Descent) { haptics.tick(.3f); film = it } } }
            }
        }
        // the top pill: back, the day and its date, and the sample's badge
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { onBack() }.semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Text(if (rtl) "›" else "‹", fontFamily = Plex, fontSize = 20.sp, color = ink)
            }
            val date = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT).format(Instant.ofEpochMilli(day.summary.start).atZone(zone))
            Text(stringResource(R.string.app_day_title) + " · " + date, Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink, maxLines = 1)
            // today's day screen keeps the story and the share until boards L10 and L11 are built
            val share = stringResource(R.string.app_day_share)
            Text(share, Modifier.padding(end = 6.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { onShare() }
                .padding(horizontal = 12.dp, vertical = 8.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            if (sample) Text(stringResource(R.string.app_day_sample), Modifier.padding(end = 14.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x33A98BEE)).padding(horizontal = 8.dp, vertical = 2.dp), fontFamily = Plex, fontSize = 12.sp, color = ink)
        }
        film?.let { f -> RunFilm(f, paint, runs, night, haptics) { film = null } }
    }
}

/** A run's sign colour, as on the trail signs (board L07). */
private fun signColor(c: String?) = when (c) { "green" -> Color(0xFF178A4A); "red" -> Color(0xFFD8362C); "black" -> Color(0xFF14171B); "blue" -> Color(0xFF1B6FDB); else -> Color(0xFF8A96A3) }

@Composable
private fun TimelineRow(it: DayModel.Item, at: String, runs: Runs?, night: Boolean, ink: Color, muted: Color, rule: Color, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).let { m -> if (it is DayModel.Descent) m.clickable(role = Role.Button) { onOpen() } else m }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(at, Modifier.width(44.dp).padding(top = 1.dp), fontFamily = Plex, fontSize = 13.sp, color = muted)
        Column(Modifier.weight(1f)) {
            val meta = TextStyle(fontFamily = Plex, fontSize = 13.sp, color = muted)
            when (it) {
                is DayModel.Descent -> {
                    if (it.runs.isEmpty()) Text(stringResource(R.string.app_day_off), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink)
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        it.runs.forEachIndexed { i, leg ->
                            if (i > 0) Text("›", fontFamily = Plex, fontSize = 14.sp, color = muted)
                            val keys = leg.split(" · ")
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(signColor(runs?.pistes?.firstOrNull { p -> p.key == keys[0] }?.color)))
                            Text(shortRuns(keys.joinToString(" · ") { k -> titleOf(runs, k) }), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink, maxLines = 1)
                        }
                    }
                    val parts = listOf(
                        String.format(Locale.ROOT, "%.1f", it.distM / 1000.0) + " " + stringResource(R.string.app_rec_km_ski),
                        Math.round(it.dropM).toString() + " " + stringResource(R.string.app_rec_m_down),
                        LiveNumbers.maxKmh(it.maxKmh) + " " + stringResource(R.string.app_rec_kmh_max),
                    )
                    Text(parts.joinToString(" · "), style = meta)
                    if (it.runs.isNotEmpty() && it.offRuns) Text(stringResource(R.string.app_day_off), style = meta)
                }
                is DayModel.Queue -> Text(stringResource(R.string.app_day_queue, it.minutes) + (it.lift?.let { l -> " · $l" } ?: ""), style = meta)
                is DayModel.Rest -> Text(stringResource(if (it.long) R.string.app_day_lunch else R.string.app_day_rest, it.minutes), style = meta)
                is DayModel.Walk -> Text(stringResource(R.string.app_day_walk, Math.round(it.distM)), style = meta)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
}

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.launch
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The ridge of the day and its timeline (board L07): the height through the day against the time, the descents in the
 * speed light and the rides dotted, the top speed's bubble and the long breaks named; under it the day as a timeline
 * in the new style. The rides come from the analysis's lift segments (DayModel has no lift item); the chapters are the board's
 * morning, midday and afternoon ([partOfDay]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayRidge(paint: DayPaint.Day, elev: FloatArray, day: DayModel.Day, segs: List<org.json.JSONObject>, runs: Runs?, night: Boolean, sample: Boolean, haptics: io.github.pini236.skiapp.fx.Haptics, onShare: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val places = io.github.pini236.skiapp.places.LocalPlaces.current
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
    val entries = remember(day, segs, places) {
        val lifts = segs.filter { it.optString("type") == "lift" }.map { o ->
            LiftRow(o.optLong("t0"), liftTitle(runs, o),
                ((o.optLong("t1") - o.optLong("t0")) / 60_000L).toInt(), if (o.isNull("rise_m")) null else o.optDouble("rise_m"),
                runs?.lifts?.firstOrNull { it.id == o.optString("lift") }?.kind)
        }
        val placeList = places?.places.orEmpty()
        val rows = DayTimelineRules.rows(day.chapters.flatMap { it.items },
            lifts.map { DayTimelineRules.LiftIn(it.t0, it.name, it.kind, it.minutes, it.riseM) },
            stopsOf = { d -> segOf(segs, d)?.optJSONArray("stops")?.length() ?: 0 }, topAt = day.summary.maxAt,
            gapOf = { d -> minuteGap(paint, d.t0, d.t1) }) { t0, afterLift ->
            val st = segs.firstOrNull { it.optString("type") == "stop" && it.optLong("t0") == t0 }
            DayTimelineRules.breakKind(st?.takeIf { it.has("lat") && !it.isNull("lat") }?.optDouble("lat"), st?.takeIf { it.has("lon") && !it.isNull("lon") }?.optDouble("lon"), placeList, afterLift)
        }
        rows.groupBy { r -> partOfDay(Instant.ofEpochMilli(r.t0).atZone(zone).hour) }.toSortedMap().toList()
    }
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

    // list index -> the row's time (null for the numbers, the ridge, the bar and the chapter heads), for the scrolled strip (DT2)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowTimes = remember(entries) { buildList<Long?> { add(null); add(null); add(null); for ((_, rows) in entries) { add(null); rows.forEach { add(it.t0) } } } }
    val showStrip by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    val stripAlpha by animateFloatAsState(if (showStrip) 1f else 0f, label = "ridgeStrip")
    val window by remember(rowTimes) { derivedStateOf {
        val vis = listState.layoutInfo.visibleItemsInfo.map { it.index }.filter { it < rowTimes.size && rowTimes[it] != null }
        val times = vis.map { rowTimes[it]!! }
        val next = vis.maxOrNull()?.let { m -> rowTimes.drop(m + 1).firstOrNull { it != null } }
        DayTimelineRules.window(times, next, paint.t0, paint.t1)
    } }

    Box(Modifier.fillMaxSize().background(bg)) {
        // under a descent's film the timeline is hidden from the screen reader too (a row cut at the film's edge read as a
        // 9 dp target, round 4's check)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().statusBarsPadding().then(if (film != null) Modifier.clearAndSetSemantics { } else Modifier), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 40.dp)) {
            val tc = TlColors(ink, muted, rule, glass, night)
            item {
                val sm = day.summary
                FourNumbers(sm.descents, sm.distM, sm.dropM, DayTimelineRules.hours(sm.start, sm.end), tc)
            }
            item {
                // the ridge (L07): ground faint, descents in the light, rides dotted, hours under it
                val measurer = rememberTextMeasurer()
                val sm = day.summary
                val bubble = sm.maxAt?.let { stringResource(R.string.app_ridge_bubble, at(it), LiveNumbers.maxKmh(sm.maxKmh)) }
                val breakWords = day.chapters.flatMap { it.items }.filterIsInstance<DayModel.Rest>().filter { it.long }
                    .map { it to stringResource(R.string.app_rec_minutes, it.minutes) }
                val tr = paint.track
                val lo = remember(elev) { elev.minOrNull() ?: 0f }; val hi = remember(elev) { elev.maxOrNull() ?: 1f }
                val span = (paint.t1 - paint.t0).coerceAtLeast(1).toFloat()
                Canvas(Modifier.fillMaxWidth().height(176.dp).semantics { contentDescription = bubble ?: "" }) {
                    if (tr.t.size < 2) return@Canvas
                    val top = 34 * dens; val bottom = size.height - 18 * dens
                    fun px(t: Long) = ((t - paint.t0) / span) * size.width
                    fun py(e: Float) = bottom - (e - lo) / (hi - lo).coerceAtLeast(1f) * (bottom - top)
                    val small = TextStyle(fontFamily = Gudi, fontSize = 11.sp, color = muted)
                    // the altitude grid (2,200 / 2,600 / 3,000 m) with its labels in a halo of the page's colour
                    val halo = TextStyle(fontFamily = Gudi, fontSize = 11.sp, color = muted, shadow = Shadow(bg, Offset.Zero, 6f))
                    for (g in DayTimelineRules.gridLines(lo, hi)) {
                        val y = py(g.toFloat())
                        drawLine(rule, Offset(0f, y), Offset(size.width, y), 1f * dens)
                        val tl = measurer.measure(String.format(Locale.ROOT, "%,d", g), halo)
                        drawText(tl, topLeft = Offset(2 * dens, y - tl.size.height - 1 * dens))
                    }
                    // the long breaks as bands (rgba 242,193,61 at .18) with a bench and the minutes on top
                    for ((r, w) in breakWords) {
                        val x0 = px(r.t0).coerceIn(0f, size.width); val x1 = px(r.t0 + r.minutes * 60_000L).coerceIn(x0 + 2 * dens, size.width)
                        drawRect(Color(0x2EF2C13D), Offset(x0, 0f), Size(x1 - x0, bottom))
                        val tl = measurer.measure(w, small)
                        val cx = ((x0 + x1) / 2).coerceIn(tl.size.width / 2f, (size.width - tl.size.width / 2f).coerceAtLeast(tl.size.width / 2f))
                        translate(cx - 7 * dens, 2 * dens) { drawNode(Node.Bench, muted, 14 * dens) }
                        drawText(tl, topLeft = Offset(cx - tl.size.width / 2f, 18 * dens))
                    }
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
                    var h = Instant.ofEpochMilli(paint.t0).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
                    while (h.toInstant().toEpochMilli() < paint.t1) {
                        val x = px(h.toInstant().toEpochMilli())
                        val tl = measurer.measure(hhmm.format(h), small)
                        drawLine(rule, Offset(x, bottom), Offset(x, bottom + 3 * dens), 1f * dens)
                        drawText(tl, topLeft = Offset(x - tl.size.width / 2f, bottom + 4 * dens))
                        h = h.plusHours(1)
                    }
                    // the top speed's bubble
                    val mt = sm.maxAt
                    if (bubble != null && mt != null) {
                        val k = tr.t.binarySearch(mt).let { if (it < 0) (-it - 1).coerceAtMost(tr.t.size - 1) else it }
                        val p = Offset(px(mt), py(elev[k]))
                        val tl = measurer.measure(bubble, TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                        val w = tl.size.width + 16 * dens; val bh = tl.size.height + 6 * dens
                        val left = (p.x - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f)); val y = (p.y - 10 * dens - bh).coerceAtLeast(0f)
                        drawLine(Color(0xFF0E2233), p, Offset(p.x, y + bh), 1.2f * dens)
                        drawRoundRect(Color(0xFF0E2233), Offset(left, y), Size(w, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2))
                        drawText(tl, topLeft = Offset(left + 8 * dens, y + 3 * dens))
                        drawCircle(Color.White, 3.5f * dens, p); drawCircle(Color(0xFF000000.toInt() or ramp[4]), 3.5f * dens, p, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * dens))
                    }
                }
                // the scale and the rides' dotted piece, named (board L07's legend)
                FlowRow(Modifier.padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LightScale(night, muted, width = 120.dp, lift = true)
                    if (breakWords.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(width = 18.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x2EF2C13D)).border(1.dp, Color(0x66E2B13C), RoundedCornerShape(3.dp)))
                        Text(stringResource(R.string.app_rec_break), fontFamily = Gudi, fontSize = 11.sp, color = muted)
                    }
                }
            }
            // the day as a timeline (boards DT1-DT5): rows on a spine, chapters by the hour
            item {
                val ds = day.chapters.flatMap { it.items }.filterIsInstance<DayModel.Descent>()
                val total = (day.summary.end - day.summary.start) / 1000.0
                val sec = { type: String, kind: String? -> segs.filter { it.optString("type") == type && (kind == null || it.optString("kind") == kind) }.sumOf { (it.optLong("t1") - it.optLong("t0")) / 1000.0 } }
                WhereBar(DayTimelineRules.where(total, ds.sumOf { if (it.movingS > 0) it.movingS else (it.t1 - it.t0) / 1000.0 }, sec("lift", null), sec("stop", "queue")), ramp, tc)
            }
            for ((part, rows) in entries) {
                item { ChapterHead(if (part == 0) R.string.app_day_morning else R.string.app_day_noon, rows.count { it is DayTimelineRules.Descent }, tc) }
                items(rows) { r ->
                    when (r) {
                        is DayTimelineRules.Lift -> LiftRowView(r, at(r.t0), tc)
                        is DayTimelineRules.Queue -> QueueRowView(r, at(r.t0), tc)
                        is DayTimelineRules.Walk -> WalkRowView(r, at(r.t0), tc)
                        is DayTimelineRules.Break -> BreakRowView(r, at(r.t0), at(r.t0 + r.minutes * 60_000L), tc)
                        is DayTimelineRules.Descent -> {
                            val strip = DayTimelineRules.strip(paint.track.t, paint.track.band, r.d.t0, r.d.t1, 32)
                            DescentRowView(r, at(r.t0), r.gapAt?.let { g -> at(g) }, runs, strip, ramp,
                                r.d.maxAt?.let { m -> if (r.d.t1 > r.d.t0) ((m - r.d.t0) * 32 / (r.d.t1 - r.d.t0)).toInt().coerceIn(0, 31) else -1 } ?: -1, tc) { haptics.tick(.3f); film = r.d }
                        }
                    }
                }
            }
        }
        // the top pill: back, the day and its date, and the sample's badge
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { onBack() }.semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Text(if (rtl) "›" else "‹", fontFamily = Gudi, fontSize = 20.sp, color = ink)
            }
            val date = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT).format(Instant.ofEpochMilli(day.summary.start).atZone(zone))
            Text(stringResource(R.string.app_ridge_title) + " · " + date, Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink, maxLines = 2)
            // the story of the day and its picture (board L10)
            val share = stringResource(R.string.app_day_share)
            Text(share, Modifier.padding(end = 6.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { onShare() }
                .padding(horizontal = 12.dp, vertical = 8.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            if (sample) Text(stringResource(R.string.app_day_sample), Modifier.padding(end = 14.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x33A98BEE)).padding(horizontal = 8.dp, vertical = 2.dp), fontFamily = Gudi, fontSize = 12.sp, color = ink)
        }
        // the scrolled ridge: the card collapses into a strip under the pill, a tap scrolls to that time (board DT2)
        if (stripAlpha > 0.01f && film == null) RidgeStrip(paint, elev, ramp, TlColors(ink, muted, rule, glass, night), window,
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 64.dp).graphicsLayer { alpha = stripAlpha; translationY = -8.dp.toPx() * (1f - stripAlpha) }) { t ->
            DayTimelineRules.scrollTarget(rowTimes, t)?.let { i -> haptics.tick(.2f); scope.launch { listState.animateScrollToItem(i, -(52 * dens).toInt()) } }
        }
        film?.let { f -> RunFilm(f, segOf(segs, f), segs, paint, runs, night, haptics) { film = null } }
    }
}

/** A lift ride in the timeline (board L07): from the analysis's lift segment. */
internal class LiftRow(val t0: Long, val name: String, val minutes: Int, val riseM: Double?, val kind: String? = null)

/** A lift segment's name: the lift's name in the data, by its id (as the live screen), else the segment's own. */
internal fun liftTitle(runs: Runs?, o: org.json.JSONObject): String {
    val id = o.optString("lift")
    return runs?.lifts?.firstOrNull { it.id == id }?.name ?: o.optString("name").takeIf { !o.isNull("name") && it.isNotEmpty() } ?: id
}

/** The analysis's segment of a descent (its stops, its top and bottom), by its start. */
internal fun segOf(segs: List<org.json.JSONObject>, d: DayModel.Descent) = segs.firstOrNull { it.optString("type") == "descent" && it.optLong("t0") == d.t0 }

/** A run's sign colour, as on the trail signs (board L07). */
internal fun signColor(c: String?) = when (c) { "green" -> Color(0xFF178A4A); "red" -> Color(0xFFD8362C); "black" -> Color(0xFF14171B); "blue" -> Color(0xFF1B6FDB); else -> Color(0xFF8A96A3) }

/**
 * The start of a gap of about a minute (30 to 90 s) between the day's lines inside [t0, t1], or null: the words say
 * "a minute without reception", so a longer or shorter gap is not named so.
 */
internal fun minuteGap(paint: DayPaint.Day, t0: Long, t1: Long): Long? {
    val inside = paint.lines.mapNotNull { l -> val ts = l.t.filter { it in t0..t1 }; if (ts.size < 2) null else ts.first() to ts.last() }.sortedBy { it.first }
    return inside.zipWithNext().firstOrNull { (a, b) -> (b.first - a.second) in 30_000L..90_000L }?.first?.second
}

/** The timeline's two chapters (board DT1): "בוקר" before 12:00 and "צהריים" from 12:00. */
internal fun partOfDay(hour: Int): Int = if (hour < MIDDAY_START_HOUR) 0 else 1

internal const val MIDDAY_START_HOUR = 12

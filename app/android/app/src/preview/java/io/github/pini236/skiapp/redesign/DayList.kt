package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayAnalysis
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.dayrec.Moments
import io.github.pini236.skiapp.dayrec.SampleDay
import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.dayrec.ui.MomentText
import io.github.pini236.skiapp.dayrec.ui.StoryData
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * My days (board L12): the trip as layers on one mountain, each ski day of the trip in its own colour, then a row for
 * every recorded day, the newest first, with its own lines, its head (the story's kind of day) and its numbers. A swipe on
 * a row shows its delete (two taps, as today); "delete everything" stays at the bottom, as today. The trip is the days
 * that fall in "your trip" (its ski days, on the phone); without a trip there is no trip on the mountain, only the rows.
 * The example day stays the last row, as today; the board splits it into three to show a trip, which the app does not
 * (it has no words for that split). The board has the painted mountain behind the trip's lines; here they are drawn on
 * the glass alone. The personal records (today's screen) stay, under the rows.
 */
internal class ListedDay(val file: File, val id: String, val date: LocalDate, val sm: DayModel.Summary?, val runs: Set<String>,
                         val head: String?, val lines: List<DayPaint.Line>)

@Composable
internal fun DayList(mode: DayNight.Mode, haptics: Haptics, onOpen: (String) -> Unit, onBack: () -> Unit, scene: io.github.pini236.skiapp.map.MapScene? = null,
                     onRecord: () -> Unit = {}) {
    // your records (completion part 1, P05 and P06), from the card at the top
    var records by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(RedesignQa.seq) { when (RedesignQa.cmd) { "records" -> records = true; "records:off" -> records = false } }
    if (records) { RecordsScreen(mode, scene, haptics, onOpenDay = { records = false; onOpen(it) }, onRecord = { records = false; onRecord() }) { records = false }; return }
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val locale = ctx.resources.configuration.locales[0]
    val zone = ZoneId.of(DayParams.TZ)
    val night = remember(mode) { Sky.at(DayNight.lightTime(System.currentTimeMillis(), mode)).dark }
    val bg = if (night) Color(0xFF0A1322) else Color(0xFFF2F5F9)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val red = Color(0xFFD1342B)
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var version by remember { mutableStateOf(0) }
    var days by remember { mutableStateOf<List<ListedDay>?>(null) }
    var trip by remember { mutableStateOf<ClosedRange<LocalDate>?>(null) }
    var sure by remember { mutableStateOf<String?>(null) }
    var swiped by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version) {
        val words = MomentText.Words(ctx, locale, group = true)
        val (d, t) = withContext(Dispatchers.IO) {
            val t = runCatching { io.github.pini236.skiapp.trip.TripStore(ctx).load()?.skiDays() }.getOrNull()
            DayRec.store(ctx).days().asReversed().map { f -> listed(ctx, f, zone, locale, words) } to t
        }
        days = d; trip = t
        Qa.log("redesign days ${d.size} trip ${tripDays(d, t).map { it.first }.distinct().size}")
    }
    val list = days
    val inTrip = remember(list, trip) { tripDays(list.orEmpty(), trip) }
    val tripDates = inTrip.map { it.first }.distinct().sorted()
    val colours = remember(tripDates.size, night) { tripColours(tripDates.size, night) }
    val colourOf: (LocalDate) -> Color = { date -> colours.getOrElse(tripDates.indexOf(date)) { ink } }
    Box(Modifier.fillMaxSize().background(bg)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 72.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list != null && list.isEmpty()) item {
                Text(stringResource(R.string.app_days_empty), Modifier.padding(4.dp), fontFamily = Plex, fontSize = 15.sp, color = muted)
            }
            // your records, against yourself only (board L12's card; P05)
            if (list != null) item {
                val title = stringResource(R.string.app_day_records)
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(16.dp))
                    .semantics { role = Role.Button }.clickable { haptics.tick(.3f); Qa.log("redesign records card"); records = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink)
                    Text(if (rtl) "‹" else "›", fontFamily = Plex, fontSize = 20.sp, color = muted)
                }
            }
            // the trip as layers: each ski day of the trip in its colour, on one mountain
            if (tripDates.isNotEmpty()) item {
                val dropM = inTrip.sumOf { it.second.sm?.dropM ?: 0.0 }
                val routes = inTrip.flatMap { it.second.runs }.toSet().size
                Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (night) Color(0xFF0E1A2C) else Color(0xFFE4EAF1)).border(1.dp, rule, RoundedCornerShape(20.dp))) {
                    val strokes = remember(inTrip, colours) { inTrip.map { (date, d) -> d.lines to colourOf(date) } }
                    // on the painted mountain from the valley (board L12; the designer's point 10): the picture and each day's
                    // lines projected onto it once, off the main thread; without the ground's heights, from above as before
                    val mountain by androidx.compose.runtime.produceState<Pair<StoryVideo.Mountain, List<Pair<List<FloatArray>, Color>>>?>(null, strokes, night, scene) {
                        val sc = scene ?: return@produceState
                        value = withContext(Dispatchers.Default) {
                            OnMountain.load(ctx, night, sc.terrain::elev)?.let { m -> val pj = m.view.proj!!
                                m to strokes.map { (ls, c) -> ls.map { l -> OnMountain.project(pj, m.elev, l.xy, 2f) } to c } }
                        }
                    }
                    val mt = mountain
                    if (mt != null) {
                        val img = remember(mt) { mt.first.img.asImageBitmap() }
                        Canvas(Modifier.fillMaxSize()) {
                            val pl = OnMountain.place(mt.first.view, mt.second.flatMap { it.first }, size.width, size.height, size.minDimension * .08f, size.height - 120.dp.toPx())
                            val k = pl[0]; val ox = pl[1]; val oy = pl[2]
                            drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize((mt.first.view.w * k).toInt(), (mt.first.view.h * k).toInt()))
                            val case = if (night) Color(0xFF050A16) else Color.White
                            for (pass in 0..1) for ((ls, c) in mt.second) for (q in ls) for (i in 0 until q.size / 2 - 1) {
                                if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
                                val a0 = Offset(ox + q[i * 2] * k, oy + q[i * 2 + 1] * k); val b0 = Offset(ox + q[i * 2 + 2] * k, oy + q[i * 2 + 3] * k)
                                if (pass == 0) drawLine(case, a0, b0, 4.8f * density, cap = StrokeCap.Round) else drawLine(c, a0, b0, 3f * density, cap = StrokeCap.Round)
                            }
                        }
                    } else Canvas(Modifier.fillMaxSize().padding(bottom = 60.dp)) {
                        val fit = fitOf(strokes.flatMap { it.first }, size.width, size.height, size.minDimension * .08f) ?: return@Canvas
                        for ((ls, c) in strokes) for (l in ls) drawLineOf(l, fit, if (night) Color(0xFF050A16) else Color.White, c, 3f, density)
                    }
                    // the days' chips, in the reading order of the trip
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tripDates.forEachIndexed { k, date ->
                            Row(Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x9E080E1C)).padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(colourOf(date)))
                                Text(stringResource(R.string.app_days_day_n, k + 1), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(120.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD6080E1C))))
                        .padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.Bottom) {
                        Text(stringResource(R.string.app_days_trip_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        val line = listOf(pluralStringResource(R.plurals.app_days_trip_days, tripDates.size, tripDates.size),
                            stringResource(R.string.app_days_trip_drop, metres(dropM)),
                            pluralStringResource(R.plurals.app_days_trip_routes, routes, routes)).joinToString(", ")
                        Text("$line.", fontFamily = Plex, fontSize = 13.5.sp, color = Color(0xE6FFFFFF))
                    }
                }
            }
            items(list.orEmpty(), key = { it.id }) { d ->
                val n = tripDates.indexOf(d.date).takeIf { it >= 0 }
                DayRow(d, n, colourOf, night, ink, muted, glass, rule, red, ramp, rtl, open = swiped == d.id, asked = sure == d.id,
                    onSwipe = { o -> swiped = if (o) d.id else null; sure = null; if (o) haptics.tick(.2f) },
                    onOpen = { onOpen(d.id) },
                    onDelete = {
                        if (sure == d.id) { DayRec.store(ctx).delete(d.file); sure = null; swiped = null; version++; Qa.log("redesign days deleted") }
                        else { sure = d.id; haptics.tick(.3f) }
                    })
            }
            // the example day, last, as today
            if (list != null) item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { onOpen(SampleDay.ID) }.heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_day_sample), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF7C4FE0))
                        Text(stringResource(R.string.app_day_sample_note), fontFamily = Plex, fontSize = 13.sp, color = muted)
                    }
                }
            }
            if (!list.isNullOrEmpty()) item {
                Text(stringResource(R.string.app_days_delete_hint), Modifier.padding(horizontal = 4.dp), fontFamily = Plex, fontSize = 12.sp, color = muted)
            }
            if (!list.isNullOrEmpty()) item {
                val all = sure == ALL
                Text(stringResource(if (all) R.string.app_days_delete_all_sure else R.string.app_days_delete_all),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button) {
                        if (all) { DayRec.store(ctx).deleteAll(); sure = null; version++ } else sure = ALL
                    }.heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 14.dp),
                    fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = red)
            }
        }
        // the pill: back and the title
        Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth().height(48.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { onBack() }.semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Text(if (rtl) "›" else "‹", fontFamily = Plex, fontSize = 20.sp, color = ink)
            }
            Text(stringResource(R.string.app_days_title), Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink, maxLines = 1)
        }
    }
}

private const val ALL = "*"

/** One day's row: its lines, "trip day N" (or its date), the head and the numbers; a swipe shows the delete behind it. */
@Composable
private fun DayRow(d: ListedDay, n: Int?, colourOf: (LocalDate) -> Color, night: Boolean, ink: Color, muted: Color, glass: Color, rule: Color,
                   red: Color, ramp: IntArray, rtl: Boolean, open: Boolean, asked: Boolean, onSwipe: (Boolean) -> Unit, onOpen: () -> Unit, onDelete: () -> Unit) {
    val dens = LocalDensity.current
    val reveal = with(dens) { 112.dp.toPx() }
    // the row slides toward the reading start (in Hebrew to the right): the delete is at the end of the reading
    val dir = if (rtl) 1f else -1f
    var drag by remember(open) { mutableFloatStateOf(if (open) reveal else 0f) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
        // behind: the delete, two taps (as today)
        Box(Modifier.matchParentSize().background(red.copy(alpha = .14f)), contentAlignment = if (rtl) Alignment.CenterStart else Alignment.CenterEnd) {
            Text(stringResource(if (asked) R.string.app_days_delete_sure else R.string.app_days_delete),
                Modifier.width(112.dp).fillMaxHeight().clickable(role = Role.Button) { onDelete() }.padding(horizontal = 10.dp, vertical = 12.dp),
                fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = red)
        }
        Row(Modifier.offset { IntOffset((dir * drag).roundToInt(), 0) }.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glass)
            .border(1.dp, rule, RoundedCornerShape(18.dp))
            .draggable(rememberDraggableState { dx -> drag = (drag + dir * dx).coerceIn(0f, reveal) }, Orientation.Horizontal,
                onDragStopped = { val o = drag > reveal / 2; drag = if (o) reveal else 0f; onSwipe(o) })
            .clickable(role = Role.Button) { if (drag > 0f) { drag = 0f; onSwipe(false) } else onOpen() }
            .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // a day without a descent (the designer, point 3): no picture, no story's head, the date and "no descents"
            val empty = d.sm?.descents == 0
            if (d.lines.isNotEmpty() && !empty) Canvas(Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(if (night) Color(0xFF0E1A2C) else Color(0xFFE9EEF4))) {
                val fit = fitOf(d.lines, size.width, size.height, size.minDimension * .12f) ?: return@Canvas
                for (l in d.lines) drawLineOf(l, fit, if (night) Color(0xFF050A16) else Color.White, null, 2.6f, density, ramp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (n != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(colourOf(d.date)))
                    Text(stringResource(R.string.app_days_trip_day_n, n + 1), fontFamily = Plex, fontSize = 12.sp, color = muted)
                }
                else Text(DateTimeFormatter.ofPattern("EEEE d.M", Locale.getDefault()).format(d.date), fontFamily = Plex, fontSize = 12.sp, color = muted)
                Text((if (empty) null else d.head) ?: DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT).format(d.date), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = ink, maxLines = 2)
                val sm = d.sm
                Text(when {
                    sm == null -> stringResource(R.string.app_days_cut)
                    empty -> stringResource(R.string.app_day_empty)
                    else -> stringResource(R.string.app_rec_day_line, sm.descents, metres(sm.dropM), "%.1f".format(Locale.ROOT, sm.distM / 1000))
                }, fontFamily = Plex, fontSize = 13.5.sp, color = muted)
            }
            Text(if (rtl) "‹" else "›", fontFamily = Plex, fontSize = 20.sp, color = muted)
        }
    }
}

/** One recorded day for the list: its date in Gudauri, the result's numbers, its runs, its head and its lines (or none, cut). */
private fun listed(ctx: android.content.Context, f: File, zone: ZoneId, locale: Locale, words: MomentText.Words): ListedDay {
    val id = f.name.removePrefix("day-").removeSuffix(".jsonl")
    val start = id.toLongOrNull() ?: f.lastModified()
    val result = runCatching { DayStore.resultOf(f).takeIf { it.isFile } ?: DayAnalysis.ensure(ctx, f) }.getOrNull()
        ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
    val model = result?.let { runCatching { DayModel.build(it, zone) }.getOrNull() }
    val sm = model?.summary
    val runs = model?.chapters.orEmpty().flatMap { c -> c.items.filterIsInstance<DayModel.Descent>().flatMap { it.runs } }.toSet()
    val head = sm?.let { s ->
        StoryData.read(DayStore.momentsOf(f))?.let { st -> words.render(MomentText.head(st.theme, s.descents, s.dropM, s.distM, locale), Moments.seed("story.head", s.start)).first }
    }
    val lines = if (result == null) emptyList() else runCatching {
        val a = result.optJSONArray("segments")
        val segs = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.map { DayPaint.Seg(it.optString("type"), it.optLong("t0"), it.optLong("t1")) }
        val pts = DayLog.read(f).points.map { DayPaint.Pt(it.t, Geo.x(it.lon), Geo.y(it.lat), it.speed?.let { s -> s * 3.6 }) }
        DayPaint.build(pts, segs).lines
    }.getOrDefault(emptyList())
    val date = Instant.ofEpochMilli(sm?.start ?: start).atZone(zone).toLocalDate()
    return ListedDay(f, id, date, sm, runs, head, lines)
}

/** The recorded days that fall in the trip's ski days, each with its date (a date can have more than one recording). */
internal fun tripDays(days: List<ListedDay>, trip: ClosedRange<LocalDate>?): List<Pair<LocalDate, ListedDay>> =
    if (trip == null) emptyList() else days.filter { it.date in trip && it.sm != null }.map { it.date to it }

/**
 * The trip's colours, one a day, in its order (board L12: blue, violet, burning pink; at night lighter, the last to white).
 * The board draws three days; more are spread evenly between the same three, so the first is blue and the last pink.
 */
internal fun tripColours(n: Int, night: Boolean): List<Color> {
    val anchors = if (night) intArrayOf(0x6CB4FF, 0xB99CFF, 0xFFF2FA) else intArrayOf(0x2F7BFF, 0x8A4FE0, 0xE0246E)
    return tripRgb(n, anchors).map { Color(0xFF000000.toInt() or it) }
}

/** The colours as RGB (testable without Compose). */
internal fun tripRgb(n: Int, anchors: IntArray): List<Int> = List(n) { k ->
    if (n == 1) anchors[0] else {
        val f = k.toFloat() / (n - 1) * (anchors.size - 1)
        val i = f.toInt().coerceAtMost(anchors.size - 2); val t = f - i
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(anchors[i], s) + (ch(anchors[i + 1], s) - ch(anchors[i], s)) * t).roundToInt()
        (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}

private class Fit(val x0: Float, val y1: Float, val k: Float, val ox: Float, val oy: Float)

private fun fitOf(lines: List<DayPaint.Line>, w: Float, h: Float, pad: Float): Fit? {
    var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
    for (l in lines) for (i in 0 until l.xy.size / 2) { val x = l.xy[i * 2]; val y = l.xy[i * 2 + 1]; x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y) }
    if (x0 > x1) return null
    val k = minOf((w - pad * 2) / (x1 - x0).coerceAtLeast(1f), (h - pad * 2) / (y1 - y0).coerceAtLeast(1f))
    return Fit(x0, y1, k, (w - (x1 - x0) * k) / 2, (h - (y1 - y0) * k) / 2)
}

/** A line with its case; in one colour (the trip's day) or by its own speed bands. y in the map's metres grows north. */
private fun DrawScope.drawLineOf(l: DayPaint.Line, f: Fit, case: Color, colour: Color?, w: Float, dens: Float, ramp: IntArray? = null) {
    fun at(i: Int) = Offset(f.ox + (l.xy[i * 2] - f.x0) * f.k, f.oy + (f.y1 - l.xy[i * 2 + 1]) * f.k)
    for (i in 0 until l.xy.size / 2 - 1) drawLine(case, at(i), at(i + 1), (w + 1.8f) * dens, cap = StrokeCap.Round)
    for (i in 0 until l.xy.size / 2 - 1) {
        val c = colour ?: Color(0xFF000000.toInt() or ramp!![l.band[i].coerceIn(0, ramp.size - 1)])
        drawLine(c, at(i), at(i + 1), w * dens, cap = StrokeCap.Round)
    }
}

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
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.dayrec.MomentsStore
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.alpha
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
                         val head: String?, val lines: List<DayPaint.Line>, val cutAt: Long? = null)

/** One day card: the day, its window on the mountain, its speed strip and its tag (P.24, MY1 and MY2). */
private class Card(val d: ListedDay, val win: WinData?, val strip: IntArray, val tag: MyDaysRules.Tag)

@Composable
internal fun DayList(mode: DayNight.Mode, haptics: Haptics, onOpen: (String) -> Unit, onBack: () -> Unit, scene: io.github.pini236.skiapp.map.MapScene? = null,
                     onRecord: () -> Unit = {}) {
    // your records (completion part 1, P05 and P06), from the row under the trip card
    var records by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(RedesignQa.seq) { when (RedesignQa.cmd) { "records" -> records = true; "records:off" -> records = false } }
    if (records) { RecordsScreen(mode, scene, haptics, onOpenDay = { records = false; onOpen(it) }, onRecord = { records = false; onRecord() }) { records = false }; return }
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val locale = ctx.resources.configuration.locales[0]
    val zone = ZoneId.of(DayParams.TZ)
    val night = remember(mode) { Sky.at(DayNight.lightTime(System.currentTimeMillis(), mode)).dark }
    val k = remember(night) { SheetInk(night) }
    val bg = if (night) Color(0xFF0A1322) else Color(0xFFF2F5F9)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val red = Color(0xFFD1342B)
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var version by remember { mutableStateOf(0) }
    var cards by remember { mutableStateOf<List<Card>?>(null) }
    var mountain by remember { mutableStateOf<StoryVideo.Mountain?>(null) }
    var sampleWin by remember { mutableStateOf<WinData?>(null) }
    var recRows by remember { mutableStateOf<List<Records.Row>>(emptyList()) }
    var sure by remember { mutableStateOf<String?>(null) }
    var swiped by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version, night, scene) {
        val words = MomentText.Words(ctx, locale, group = true)
        val loaded = withContext(Dispatchers.IO) {
            val files = DayRec.store(ctx).days().asReversed()
            val listedAll = files.map { f -> listed(ctx, f, zone, locale, words) }
            val best = runCatching { MomentsStore.history(files.firstOrNull()?.parentFile ?: ctx.filesDir).optJSONObject("best") }.getOrNull() ?: JSONObject()
            val rows = Records.rows(best, savedDays(files))
            val recordDay = rows.firstOrNull { it.what == "max_kmh" }?.day?.id
            val m = scene?.let { sc -> OnMountain.load(ctx, night, sc.terrain::elev) }
            val faint = if (m != null) projectRuns(m, scene?.runs) else emptyList()
            val list = listedAll.map { d ->
                val win = if (m == null) null else WinData(faint, projectDay(m, d.lines))
                Card(d, win, MyDaysRules.strip(d.lines), MyDaysRules.tag(d.id == recordDay, d.cutAt != null))
            }
            val sw = if (m == null) null else runCatching {
                val sf = DayAnalysis.ensure(ctx, SampleDay.file(ctx))
                val res = sf?.let { JSONObject(it.readText()) }
                if (res == null) null else WinData(faint, projectDay(m, dayLines(SampleDay.file(ctx), res)))
            }.getOrNull()
            Triple(list, rows, Triple(m, sw, Unit))
        }
        cards = loaded.first; recRows = loaded.second; mountain = loaded.third.first; sampleWin = loaded.third.second
        Qa.log("redesign days ${loaded.first.size} ${if (loaded.third.first != null) "mountain" else "plain"}")
    }
    val list = cards
    val real = list.orEmpty().filter { it.d.sm != null }
    val sum = MyDaysRules.sum(real.map { Triple(it.d.sm!!.descents, it.d.sm.dropM, it.d.sm.distM) })
    val img = remember(mountain) { mountain?.img?.asImageBitmap() }
    Box(Modifier.fillMaxSize().background(bg)) {
        // the painted mountain under a light veil (the v91 rule: the picture fills the screen)
        if (img != null) Canvas(Modifier.fillMaxSize()) {
            val vw = mountain!!.view.w; val vh = mountain!!.view.h
            val cover = maxOf(size.width / vw, size.height / vh)
            drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - vw * cover) / 2).toInt(), 0),
                dstSize = androidx.compose.ui.unit.IntSize((vw * cover).toInt(), (vh * cover).toInt()))
            drawRect(if (night) Color(0xCC0A1322) else Color(0xCCF2F5F9))
        }
        val empty = list != null && list.isEmpty()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 72.dp, bottom = if (empty) 96.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // MY3: before the first day, the sample day in a large window that opens it
            if (empty) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.app_md_empty), Modifier.padding(horizontal = 4.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = k.ink)
                    val sa = stringResource(R.string.app_empty_sample_a11y)
                    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, k.line, RoundedCornerShape(22.dp))
                        .semantics { contentDescription = sa; role = Role.Button }.clickable { haptics.tick(.3f); onOpen(SampleDay.ID) }) {
                        MountainWindow(mountain, sampleWin, night, Modifier.fillMaxSize(), ramp, line = 3.6f)
                        Text(stringResource(R.string.app_day_sample), Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x9E080E1C)).padding(horizontal = 10.dp, vertical = 4.dp),
                            fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                    Text(stringResource(R.string.app_md_sample_note), Modifier.padding(horizontal = 4.dp), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
                }
            }
            // MY1: the trip card: month, four numbers, one column per day, and the records row
            if (real.isNotEmpty()) item {
                val newest = real.first().d.date
                val month = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(newest)
                val cap = k.ink2
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(glass).border(1.dp, k.line, RoundedCornerShape(22.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(month, Modifier.semantics { heading() }, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = k.ink)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dayLabel = pluralStringResource(R.plurals.app_md_days, sum.days, sum.days)
                        val runLabel = pluralStringResource(R.plurals.app_md_runs, sum.descents, sum.descents)
                        Stat(sum.days.toString(), dayLabel.replace(sum.days.toString(), "").trim().ifEmpty { dayLabel }, k, Modifier.weight(1f))
                        Stat(sum.descents.toString(), runLabel.replace(sum.descents.toString(), "").trim().ifEmpty { runLabel }, k, Modifier.weight(1f))
                        Stat(metres(sum.dropM), stringResource(R.string.app_rec_m_down), k, Modifier.weight(1f))
                        Stat("%.0f".format(Locale.ROOT, sum.km), stringResource(R.string.app_md_km), k, Modifier.weight(1f))
                    }
                    // one column per day, oldest first in reading order; the height is the metres down, the newest day bold
                    val chrono = real.reversed()
                    val bars = MyDaysRules.bars(chrono.map { it.d.sm!!.dropM })
                    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        chrono.forEachIndexed { i, c ->
                            val newestCol = i == chrono.size - 1
                            Box(Modifier.weight(1f).fillMaxHeight(bars[i]).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (newestCol) k.acc else k.acc.copy(alpha = .38f)))
                        }
                    }
                    // the two headline records, and the way to the records page
                    val rec = recRows.filter { it.value != null }
                    val top = recRows.firstOrNull { it.what == "max_kmh" && it.value != null }
                    val drop = recRows.firstOrNull { it.what == "drop_m" && it.value != null }
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, k.line, RoundedCornerShape(16.dp))
                        .semantics { role = Role.Button }.clickable { haptics.tick(.3f); Qa.log("redesign records card"); records = true }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.app_day_records), Modifier.weight(1f, fill = false), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 1)
                        if (rec.isNotEmpty()) {
                            if (top != null) RecChip("${LiveNumbers.maxKmh(top.value)} ${stringResource(R.string.app_rec_unit)}", k)
                            if (drop != null) RecChip("${metres(drop.value!!)} ${stringResource(R.string.app_rec_m_down)}", k)
                        }
                        Box(Modifier.weight(1f))
                        Text(if (rtl) "‹" else "›", fontFamily = Gudi, fontSize = 20.sp, color = cap)
                    }
                }
            }
            // one card per day, the newest first (MY1)
            items(list.orEmpty(), key = { it.d.id }) { c ->
                DayCard(c, mountain, night, k, glass, red, ramp, rtl, locale, zone, open = swiped == c.d.id, asked = sure == c.d.id,
                    onSwipe = { o -> swiped = if (o) c.d.id else null; sure = null; if (o) haptics.tick(.2f) },
                    onOpen = { onOpen(c.d.id) },
                    onDelete = {
                        if (sure == c.d.id) { DayRec.store(ctx).delete(c.d.file); sure = null; swiped = null; version++; Qa.log("redesign days deleted") }
                        else { sure = c.d.id; haptics.tick(.3f) }
                    })
            }
            // MY2: the sample day at the end, dimmed, under its note
            if (list != null && list.isNotEmpty()) item {
                val sa = stringResource(R.string.app_empty_sample_a11y)
                Column(Modifier.fillMaxWidth().alpha(.72f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.app_md_sample_note), Modifier.padding(horizontal = 4.dp), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(glass).border(1.dp, k.line, RoundedCornerShape(20.dp))
                        .semantics { contentDescription = sa; role = Role.Button }.clickable { haptics.tick(.3f); onOpen(SampleDay.ID) }.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MountainWindow(mountain, sampleWin, night, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), ramp, line = 2.4f)
                        Text(stringResource(R.string.app_day_sample), Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.acc)
                        Text(if (rtl) "‹" else "›", fontFamily = Gudi, fontSize = 20.sp, color = k.ink2)
                    }
                }
            }
            if (!list.isNullOrEmpty()) item {
                val all = sure == ALL
                Text(stringResource(if (all) R.string.app_days_delete_all_sure else R.string.app_days_delete_all),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button) {
                        if (all) { DayRec.store(ctx).deleteAll(); sure = null; version++ } else sure = ALL
                    }.heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 14.dp),
                    fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = red)
            }
        }
        // MY3: "record a ski day" at the thumb
        if (empty) {
            val rec = stringResource(R.string.app_md_record)
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(k.acc)
                .semantics { role = Role.Button }.clickable { haptics.tick(.4f); onRecord() }, contentAlignment = Alignment.Center) {
                Text(rec, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.onAcc)
            }
        }
        // the pill: back and the title
        Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth().height(48.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, k.line, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { onBack() }.semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Text(if (rtl) "›" else "‹", fontFamily = Gudi, fontSize = 20.sp, color = k.ink)
            }
            Text(stringResource(R.string.app_md_title), Modifier.weight(1f).semantics { heading() }, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 2)
        }
    }
}

private const val ALL = "*"

@Composable
private fun Stat(value: String, label: String, k: SheetInk, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 26.sp, color = k.ink, maxLines = 1)
        Text(label, fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2, maxLines = 2)
    }
}

@Composable
private fun RecChip(text: String, k: SheetInk) {
    Text(text, Modifier.clip(RoundedCornerShape(12.dp)).background(k.acc.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = k.acc, maxLines = 1)
}

/** One day's card (MY1): a 104 window on the mountain, the date and hours, three numbers, the speed strip and a tag; a swipe shows its delete. */
@Composable
private fun DayCard(c: Card, mountain: StoryVideo.Mountain?, night: Boolean, k: SheetInk, glass: Color, red: Color, ramp: IntArray, rtl: Boolean, locale: Locale, zone: ZoneId,
                    open: Boolean, asked: Boolean, onSwipe: (Boolean) -> Unit, onOpen: () -> Unit, onDelete: () -> Unit) {
    val dens = LocalDensity.current
    val reveal = with(dens) { 112.dp.toPx() }
    val dir = if (rtl) 1f else -1f
    var drag by remember(open) { mutableFloatStateOf(if (open) reveal else 0f) }
    val d = c.d
    val sm = d.sm
    val dayText = DateTimeFormatter.ofPattern("EEE · d.M", locale).format(d.date)
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val hours = sm?.let { "⁦" + hm.format(Instant.ofEpochMilli(it.start).atZone(zone)) + "–" + hm.format(Instant.ofEpochMilli(it.end).atZone(zone)) + "⁩" }
    val a11y = if (sm == null) dayText else pluralStringResource(R.plurals.app_md_card_a11y, sm.descents, dayText, metres(sm.dropM), sm.descents)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
        if (drag > 0f) Box(Modifier.matchParentSize().background(red.copy(alpha = .14f)), contentAlignment = if (rtl) Alignment.CenterStart else Alignment.CenterEnd) {
            Text(stringResource(if (asked) R.string.app_days_delete_sure else R.string.app_days_delete),
                Modifier.width(112.dp).fillMaxHeight().clickable(role = Role.Button) { onDelete() }.padding(horizontal = 10.dp, vertical = 12.dp),
                fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = red)
        }
        Row(Modifier.offset { IntOffset((dir * drag).roundToInt(), 0) }.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(glass)
            .border(1.dp, k.line, RoundedCornerShape(20.dp))
            .draggable(rememberDraggableState { dx -> drag = (drag + dir * dx).coerceIn(0f, reveal) }, Orientation.Horizontal,
                onDragStopped = { val o = drag > reveal / 2; drag = if (o) reveal else 0f; onSwipe(o) })
            .semantics(mergeDescendants = true) { contentDescription = a11y; role = Role.Button }
            .clickable { if (drag > 0f) { drag = 0f; onSwipe(false) } else onOpen() }
            .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MountainWindow(mountain, c.win, night, Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)), ramp, line = 2.6f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(dayText, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink, maxLines = 1)
                if (hours != null) Text(hours, fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2, maxLines = 1)
                if (sm != null && sm.descents > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(sm.descents.toString(), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.ink)
                        Text(metres(sm.dropM) + " " + stringResource(R.string.app_rec_m_down), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2, maxLines = 1)
                        Text("%.1f".format(Locale.ROOT, sm.distM / 1000) + " " + stringResource(R.string.app_run_km), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2, maxLines = 1)
                    }
                    SpeedStrip(c.strip, ramp)
                } else if (sm != null) Text(stringResource(R.string.app_day_empty), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2)
                when (c.tag) {
                    MyDaysRules.Tag.RECORD -> Tag(stringResource(R.string.moment_record_max_kmh_title_1), k.acc, k)
                    MyDaysRules.Tag.CUT -> Tag(stringResource(R.string.app_md_cut, d.cutAt?.let { hm.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "—"), Color(if (night) 0xFFFFB454 else 0xFFB45F06), k)
                    MyDaysRules.Tag.NONE -> {}
                }
            }
            Text(if (rtl) "‹" else "›", fontFamily = Gudi, fontSize = 20.sp, color = k.ink2)
        }
    }
}

@Composable
private fun Tag(text: String, colour: Color, k: SheetInk) {
    Text(text, Modifier.clip(RoundedCornerShape(10.dp)).background(colour.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = colour, maxLines = 1)
}

/** One strip over the descents only (no lifts): each cell in the speed light of its band. */
@Composable
private fun SpeedStrip(bands: IntArray, ramp: IntArray) {
    Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
        val cells = MyDaysRules.cells(bands, 48)
        if (cells.isEmpty()) return@Canvas
        val w = size.width / cells.size
        for (i in cells.indices) drawRect(Color(0xFF000000.toInt() or ramp[cells[i].coerceIn(0, ramp.size - 1)]), Offset(i * w, 0f), androidx.compose.ui.geometry.Size(w + 1f, size.height))
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
    val cutAt = if (DayRec.store(ctx).isCut(f)) runCatching { DayLog.read(f).points.lastOrNull()?.t }.getOrNull() else null
    return ListedDay(f, id, date, sm, runs, head, lines, cutAt)
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

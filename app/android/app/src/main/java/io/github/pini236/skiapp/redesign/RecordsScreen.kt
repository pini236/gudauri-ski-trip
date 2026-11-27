package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.dayrec.MomentsStore
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Your records (P.24, boards RS1 to RS3): the top speed big on a window of the mountain with its descent and its point, the
 * number over the picture; under it four tiles, each with its own drawing (the longest descent, the biggest day, the most
 * descents, the steepest). A tap opens that day. Against yourself only, on the phone. Without records: four ghosts and
 * "record a ski day". The sixth row of the history (km skiing) is a number of the trip card, not a tile.
 */
private class Tile(val row: Records.Row, val win: WinData?, val bars: List<Float>, val profile: FloatArray?)

@Composable
internal fun RecordsScreen(mode: DayNight.Mode, scene: MapScene?, haptics: Haptics, onOpenDay: (String) -> Unit, onRecord: () -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val locale = ctx.resources.configuration.locales[0]
    val zone = ZoneId.of(DayParams.TZ)
    val night = remember(mode) { Sky.at(DayNight.lightTime(System.currentTimeMillis(), mode)).dark }
    val k = remember(night) { SheetInk(night) }
    val bg = if (night) Color(0xFF0B1220) else Color(0xFFEEF3F8)
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    var rows by remember { mutableStateOf<List<Records.Row>?>(null) }
    var tiles by remember { mutableStateOf<Map<String, Tile>>(emptyMap()) }
    var mountain by remember { mutableStateOf<StoryVideo.Mountain?>(null) }
    LaunchedEffect(night, scene) {
        val out = withContext(Dispatchers.IO) {
            val files = DayRec.store(ctx).days().filter { !io.github.pini236.skiapp.dayrec.SampleDay.isSample(it) }
            val saved = savedDays(files)
            val best = runCatching { MomentsStore.history(files.firstOrNull()?.parentFile ?: ctx.filesDir).optJSONObject("best") }.getOrNull() ?: JSONObject()
            val rs = Records.rows(best, saved)
            val m = scene?.let { sc -> OnMountain.load(ctx, night, sc.terrain::elev) }
            val faint = if (m != null) projectRuns(m, scene?.runs) else emptyList()
            val map = HashMap<String, Tile>()
            for (r in rs) {
                val day = r.day ?: continue
                val f = files.firstOrNull { it.name == "day-${day.id}.jsonl" } ?: continue
                val lines = dayLines(f, day.result)
                // the lines the drawing is about: the descent of the record, or the whole day
                val about = when (r.what) {
                    "max_kmh", "longest_m" -> {
                        val t = r.atT
                        val hit = if (t == null) null else lines.firstOrNull { l -> l.t.isNotEmpty() && t >= l.t.first() - 1000 && t <= l.t.last() + 1000 }
                        if (hit != null) listOf(hit) else lines
                    }
                    else -> lines
                }
                val win = if (m == null) null else WinData(faint, projectDay(m, about),
                    r.at?.let { a -> m.view.proj?.let { pj -> OnMountain.project(pj, m.elev, floatArrayOf(Geo.x(a[1]), Geo.y(a[0])), 2f) } })
                val drops = day.result.optJSONArray("segments")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
                    .filter { it.optString("type") == "descent" }.map { it.optDouble("drop_m", 0.0) }
                val profile = if (r.what == "drop_m" && scene != null) runCatching {
                    val pts = DayLog.read(f).points
                    val n = 64; val step = (pts.size / n).coerceAtLeast(1)
                    FloatArray(minOf(n, pts.size / step)) { i -> val p = pts[i * step]; scene.terrain.elev(Geo.x(p.lon), Geo.y(p.lat)) }
                }.getOrNull() else null
                map[r.what] = Tile(r, win, if (r.what == "descents") MyDaysRules.descentBars(drops) else emptyList(), profile)
            }
            Triple(rs, map, m)
        }
        rows = out.first; tiles = out.second; mountain = out.third
        Qa.log("redesign records ${out.first.count { it.value != null }} ${if (out.third != null) "mountain" else "plain"}")
    }
    val list = rows
    val empty = list != null && list.all { it.value == null }
    val top = list?.firstOrNull { it.what == "max_kmh" }

    fun runName(key: String) = "\u2066" + (scene?.runs?.pistes?.firstOrNull { it.key == key }?.name?.ifBlank { null } ?: key) + "\u2069"
    fun dateOf(r: Records.Row): String? = r.day?.let { d ->
        val dd = Instant.ofEpochMilli(d.start).atZone(zone).toLocalDate()
        val now = java.time.LocalDate.now(zone)
        DateTimeFormatter.ofPattern(if (dd.year == now.year) "d MMM" else "d MMM yyyy", locale).format(dd)
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.padding(top = 6.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                val back = stringResource(R.string.app_g_back)
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).semantics { role = Role.Button; contentDescription = back }.clickable { haptics.tick(.3f); onBack() },
                    contentAlignment = Alignment.Center) { Text("‹", fontFamily = Gudi, fontSize = 26.sp, color = k.ink) }
                Text(stringResource(R.string.app_day_records), Modifier.semantics { heading() }, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
            }
            // the headline: the top speed on a window of the mountain, the number over the picture (RS1)
            val topTile = tiles["max_kmh"]
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val flew = top?.value?.let { stringResource(R.string.app_film_top_speed, LiveNumbers.maxKmh(it)) }
            val headA11y = if (top?.value == null) stringResource(R.string.app_records_empty_title) else
                listOfNotNull(stringResource(R.string.app_md_rt_speed), "${"%.1f".format(Locale.ROOT, top.value)} ${stringResource(R.string.app_md_unit_kmh)}",
                    top.runs.firstOrNull()?.let(::runName), dateOf(top), stringResource(R.string.app_run_estimate),
                    if (top.fresh) stringResource(R.string.app_records_new) else null).joinToString(", ")
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp)).alpha(if (empty) .55f else 1f)
                .then(if (top?.day != null) Modifier.clickable { haptics.tick(.3f); onOpenDay(top.day.id) } else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = headA11y; if (top?.day != null) role = Role.Button }) {
                MountainWindow(mountain, if (empty) null else topTile?.win, night, Modifier.fillMaxSize(), ramp, line = 3.6f) { p, _ -> flewPin(p, flew, measurer) }
                if (top?.value != null && !empty) {
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC080E1C))))
                        .padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("%.1f".format(Locale.ROOT, top.value), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 52.sp, color = Color.White)
                            Text(stringResource(R.string.app_md_unit_kmh), Modifier.padding(bottom = 10.dp), fontFamily = Gudi, fontSize = 14.sp, color = Color(0xE6FFFFFF))
                            if (top.fresh) Text(stringResource(R.string.app_records_new), Modifier.padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp)).background(k.acc).padding(horizontal = 6.dp, vertical = 2.dp),
                                fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = k.onAcc)
                        }
                        Text(listOfNotNull(top.runs.firstOrNull()?.let(::runName), dateOf(top), stringResource(R.string.app_run_estimate)).joinToString(" · "),
                            fontFamily = Gudi, fontSize = 13.sp, color = Color(0xE6FFFFFF), maxLines = 2)
                    }
                }
            }
            if (list != null) {
                if (empty) {
                    Text(stringResource(R.string.app_records_empty_title), Modifier.fillMaxWidth().padding(top = 22.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold,
                        fontSize = 21.sp, color = k.ink, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.app_records_empty_text), Modifier.fillMaxWidth().padding(top = 6.dp), fontFamily = Gudi, fontSize = 14.5.sp, color = k.ink2, textAlign = TextAlign.Center)
                }
                // the four tiles, two by two; ghosts with a dash when empty (RS3)
                val order = listOf("longest_m", "drop_m", "descents", "steep_deg")
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (pair in order.chunked(2)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (what in pair) {
                            val r = list.firstOrNull { it.what == what } ?: Records.Row(what, null, null)
                            RecordTile(r, tiles[what], mountain, night, k, ramp, Modifier.weight(1f), ::runName, ::dateOf, ghost = empty || r.value == null) {
                                r.day?.let { d -> haptics.tick(.3f); Qa.log("redesign records open $what"); onOpenDay(d.id) }
                            }
                        }
                    }
                }
            }
            Box(Modifier.height(20.dp))
            if (empty) {
                Text(stringResource(R.string.app_records_sample_note), Modifier.fillMaxWidth(), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2, textAlign = TextAlign.Center)
                val rec = stringResource(R.string.app_md_record)
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(k.acc)
                    .semantics { role = Role.Button }.clickable { haptics.tick(.3f); onRecord() }, contentAlignment = Alignment.Center) {
                    Text(rec, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.onAcc)
                }
            } else Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\uD83D\uDD12", fontSize = 14.sp, modifier = Modifier.clearAndSetSemantics { })
                Text(stringResource(R.string.app_md_private), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
            }
            Box(Modifier.navigationBarsPadding().height(18.dp))
        }
    }
}

/** One record tile (RS1): its name, the number, what it says about, and its own drawing; a tap opens the day. */
@Composable
private fun RecordTile(r: Records.Row, t: Tile?, mountain: StoryVideo.Mountain?, night: Boolean, k: SheetInk, ramp: IntArray, modifier: Modifier,
                       runName: (String) -> String, dateOf: (Records.Row) -> String?, ghost: Boolean, onClick: () -> Unit) {
    val title = stringResource(when (r.what) {
        "longest_m" -> R.string.app_md_rt_longest; "drop_m" -> R.string.app_md_rt_bigday
        "descents" -> R.string.app_md_rt_most; else -> R.string.app_md_steepest })
    val v = if (ghost) null else r.value
    val (num, unit) = when {
        v == null -> "—" to ""
        r.what == "longest_m" -> "%.1f".format(Locale.ROOT, v / 1000) to stringResource(R.string.app_run_km)
        r.what == "drop_m" -> metres(v) to stringResource(R.string.app_rec_m_down)
        r.what == "steep_deg" -> "${v.toInt()}°" to ""
        else -> v.toInt().toString() to ""
    }
    val detail = if (v == null) null else when (r.what) {
        "longest_m" -> when {
            r.runs.size >= 2 -> stringResource(R.string.app_records_from_to, runName(r.runs.first()), runName(r.runs.last()))
            r.runs.size == 1 -> runName(r.runs[0]); else -> dateOf(r) }
        "steep_deg" -> r.runs.firstOrNull()?.let(runName) ?: stringResource(R.string.app_md_steep_seg)
        "drop_m" -> listOfNotNull(stringResource(R.string.app_md_rd_drop), dateOf(r)).joinToString(" · ")
        "descents" -> listOfNotNull(stringResource(R.string.app_md_rd_oneday), dateOf(r)).joinToString(" · ")
        else -> dateOf(r)
    }
    val newWord = stringResource(R.string.app_records_new)
    val said = listOfNotNull(title, "$num $unit".trim(), detail, if (r.fresh && v != null) newWord else null).joinToString(", ")
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(k.surf).border(1.dp, k.line, RoundedCornerShape(20.dp)).alpha(if (ghost) .5f else 1f)
        .then(if (v != null) Modifier.clickable(onClick = onClick) else Modifier)
        .clearAndSetSemantics { contentDescription = said; if (v != null) role = Role.Button }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Icon(when (r.what) { "longest_m" -> RecIcons.ruler; "drop_m" -> RecIcons.down; "descents" -> RecIcons.mtn; else -> RecIcons.slope },
                null, Modifier.size(15.dp), tint = k.ink2)
            Text(title, Modifier.weight(1f, fill = false), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = k.ink2, maxLines = 1)
            if (r.fresh && v != null) Text(newWord, Modifier.clip(RoundedCornerShape(8.dp)).background(k.acc).padding(horizontal = 6.dp, vertical = 1.dp),
                fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = k.onAcc)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(num, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 30.sp, color = k.ink, maxLines = 1)
            if (unit.isNotEmpty()) Text(unit, Modifier.padding(bottom = 5.dp), fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2, maxLines = 1)
        }
        // the drawing of this record
        val draw = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp))
        when {
            v == null -> Box(draw.background(k.line.copy(alpha = .5f)))
            r.what == "longest_m" -> MountainWindow(mountain, t?.win, night, draw, ramp, line = 2.6f)
            r.what == "drop_m" -> Canvas(draw.background(if (night) Color(0xFF0E1A2C) else Color(0xFFE4EAF1))) {
                // the day's ridge: the ground it was skied over, as a filled profile
                val p = t?.profile ?: return@Canvas
                if (p.size < 2) return@Canvas
                val lo = p.min(); val hi = p.max().coerceAtLeast(lo + 1f)
                val path = Path().apply {
                    moveTo(0f, size.height)
                    for (i in p.indices) lineTo(i.toFloat() / (p.size - 1) * size.width, size.height - 6.dp.toPx() - (p[i] - lo) / (hi - lo) * (size.height - 14.dp.toPx()))
                    lineTo(size.width, size.height); close()
                }
                drawPath(path, k.acc.copy(alpha = .35f))
                val line = Path().apply { for (i in p.indices) { val x = i.toFloat() / (p.size - 1) * size.width; val y = size.height - 6.dp.toPx() - (p[i] - lo) / (hi - lo) * (size.height - 14.dp.toPx()); if (i == 0) moveTo(x, y) else lineTo(x, y) } }
                drawPath(line, k.acc, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            r.what == "descents" -> Canvas(draw.background(if (night) Color(0xFF0E1A2C) else Color(0xFFE4EAF1))) {
                // one bar for every descent, its height the drop
                val b = t?.bars.orEmpty()
                if (b.isEmpty()) return@Canvas
                val gap = 2.dp.toPx(); val w = ((size.width - gap * (b.size + 1)) / b.size).coerceAtLeast(1f)
                for (i in b.indices) drawRect(k.acc, Offset(gap + i * (w + gap), size.height - 6.dp.toPx() - b[i] * (size.height - 12.dp.toPx())),
                    androidx.compose.ui.geometry.Size(w, b[i] * (size.height - 12.dp.toPx())))
            }
            else -> Canvas(draw.background(if (night) Color(0xFF0E1A2C) else Color(0xFFE4EAF1))) {
                // the wedge at the true angle of the steepest section
                val w = MyDaysRules.wedge(r.value ?: 0.0)
                val base = size.width * .5f; val rise = (base * w[1]).coerceAtMost(size.height - 12.dp.toPx())
                val x0 = (size.width - base) / 2; val y0 = size.height - 6.dp.toPx()
                val path = Path().apply { moveTo(x0, y0); lineTo(x0 + base, y0); lineTo(x0, y0 - rise); close() }
                drawPath(path, k.acc.copy(alpha = .3f)); drawPath(path, k.acc, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        if (detail != null) Text(detail, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 2)
    }
}

/** "Here you flew" (board L06): the pin on the snow and, above it, its label on a navy pill; one form of the number everywhere. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.flewPin(p: Offset, label: String?, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawCircle(Color.White, 5.5f * density, p); drawCircle(Color(0xFFE0246E), 5.5f * density, p, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * density))
    if (label == null) return
    val tl = measurer.measure(label, androidx.compose.ui.text.TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White))
    val w = tl.size.width + 20 * density; val h = tl.size.height + 10 * density
    val x = p.x.coerceIn(w / 2 + 4 * density, size.width - w / 2 - 4 * density)
    val top = (p.y - 14 * density - h).coerceAtLeast(4 * density)
    drawLine(Color(0xFF0E2233), Offset(p.x, p.y - 5.5f * density), Offset(p.x, top + h), 1.5f * density)
    drawRoundRect(Color(0xFF0E2233), Offset(x - w / 2, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
    drawText(tl, topLeft = Offset(x - tl.size.width / 2f, top + 5 * density))
}

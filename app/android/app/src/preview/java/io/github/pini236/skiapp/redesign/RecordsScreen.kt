package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.home.DayNight
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.dayrec.MomentsStore
import io.github.pini236.skiapp.dayrec.SampleDay
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The completion round, part 1 (P05, P06; Android P12): your records, against yourself only. No table, no ranking, no
 * one else, nothing sent. A window on the painted mountain with the record day's descents and "here you flew", then the
 * six rows; empty, the ghosts of three rows and "record" to start.
 */
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

    class Window(val mountain: StoryVideo.Mountain, val thin: List<FloatArray>, val thick: List<Pair<FloatArray, IntArray>>, val pin: FloatArray?)
    var rows by remember { mutableStateOf<List<Records.Row>?>(null) }
    var window by remember { mutableStateOf<Window?>(null) }
    LaunchedEffect(night) {
        val (r, w) = withContext(Dispatchers.IO) {
            val store = DayRec.store(ctx)
            val files = store.days().filter { !SampleDay.isSample(it) }
            val saved = files.mapNotNull { f ->
                val res = runCatching { JSONObject(DayStore.resultOf(f).readText()) }.getOrNull() ?: return@mapNotNull null
                Records.SavedDay(f.name.removePrefix("day-").removeSuffix(".jsonl"), res.optLong("start"), res,
                    runCatching { JSONObject(DayStore.momentsOf(f).readText()) }.getOrNull())
            }
            val best = runCatching { MomentsStore.history(files.firstOrNull()?.parentFile ?: ctx.filesDir).optJSONObject("best") }.getOrNull() ?: JSONObject()
            val rows = Records.rows(best, saved)
            // the window: the top speed's day, its descents faint, the longest descent of all in the light, and the pin
            val top = rows.firstOrNull { it.what == "max_kmh" }
            val w = if (scene == null || top?.day == null) null else runCatching {
                val m = OnMountain.load(ctx, night, scene.terrain::elev) ?: return@runCatching null
                val pj = m.view.proj!!
                fun lines(id: String, keep: (Long) -> Boolean): List<Pair<FloatArray, IntArray>> {
                    val f = files.firstOrNull { it.name == "day-$id.jsonl" } ?: return emptyList()
                    val res = saved.first { it.id == id }.result
                    val segs = res.optJSONArray("segments")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
                        .map { DayPaint.Seg(it.optString("type"), it.optLong("t0"), it.optLong("t1")) }
                    val pts = DayLog.read(f).points.map { DayPaint.Pt(it.t, Geo.x(it.lon), Geo.y(it.lat), it.speed?.let { s -> s * 3.6 }) }
                    return DayPaint.build(pts, segs).lines.filter { l -> l.t.isNotEmpty() && keep(l.t[0]) }.map { OnMountain.project(pj, m.elev, it.xy, 2f) to it.band }
                }
                val thin = lines(top.day.id) { true }.map { it.first }
                val long = rows.firstOrNull { it.what == "longest_m" }
                val thick = long?.day?.let { d -> val t0 = long.atT ?: 0L; lines(d.id) { t -> t >= t0 - 1000 && t <= t0 + 3_600_000 }.take(1) }.orEmpty()
                val pin = top.at?.let { a -> OnMountain.project(pj, m.elev, floatArrayOf(Geo.x(a[1]), Geo.y(a[0])), 2f) }
                Window(m, thin, thick, pin)
            }.getOrNull()
            rows to w
        }
        rows = r; window = w
        Qa.log("redesign records ${r.count { it.value != null }} ${if (w != null) "mountain" else "plain"}")
    }
    val list = rows
    val empty = list != null && list.all { it.value == null }

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // the title (52 from the top on the board, under the status bar here)
            Row(Modifier.padding(top = 6.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                val back = stringResource(R.string.app_g_back)
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).semantics { role = Role.Button; contentDescription = back }.clickable { haptics.tick(.3f); onBack() },
                    contentAlignment = Alignment.Center) { Text("‹", fontFamily = Plex, fontSize = 26.sp, color = k.ink) }
                Text(stringResource(R.string.app_day_records), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
            }
            // the window on the mountain (358 by 214)
            val w = window
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(214.dp).clip(RoundedCornerShape(22.dp)).background(if (night) Color(0xFF0E1A2C) else Color(0xFFE4EAF1))
                .alpha(if (empty) .55f else 1f)) {
                if (w != null && !empty) {
                    val img = remember(w) { w.mountain.img.asImageBitmap() }
                    val topKmh = rows?.firstOrNull { it.what == "max_kmh" }?.value
                    val flew = topKmh?.let { stringResource(R.string.app_film_top_speed, LiveNumbers.maxKmh(it)) }
                    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                    val winA11y = listOfNotNull(stringResource(R.string.moment_record_max_kmh_title_1), flew).joinToString(", ")
                    Canvas(Modifier.fillMaxSize().semantics { role = Role.Button; contentDescription = winA11y }
                        .clickable { rows?.firstOrNull { it.what == "max_kmh" }?.day?.let { onOpenDay(it.id) } }) {
                        // centred a little under the top speed's place (62 of the picture's points), at the covering scale
                        val v = w.mountain.view
                        val kk = maxOf(size.width / v.w, size.height / v.h)
                        val cx = w.pin?.get(0) ?: (v.w / 2); val cy = (w.pin?.get(1) ?: (v.h / 2)) + 62f
                        val ox = (size.width / 2 - cx * kk).coerceIn(minOf(size.width - v.w * kk, 0f), 0f)
                        val oy = (size.height / 2 - cy * kk).coerceIn(minOf(size.height - v.h * kk, 0f), 0f)
                        drawImage(img, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize((v.w * kk).toInt(), (v.h * kk).toInt()))
                        fun at(q: FloatArray, i: Int) = Offset(ox + q[i * 2] * kk, oy + q[i * 2 + 1] * kk)
                        val case = if (night) Color(0xFF050A16) else Color.White
                        for (q in w.thin) for (i in 0 until q.size / 2 - 1) { if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
                            drawLine(Color(0xFF000000.toInt() or ramp[2]), at(q, i), at(q, i + 1), 2.2f * density, alpha = .5f, cap = StrokeCap.Round) }
                        for (pass in 0..1) for ((q, b) in w.thick) for (i in 0 until q.size / 2 - 1) { if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
                            if (pass == 0) drawLine(case, at(q, i), at(q, i + 1), 5.4f * density, cap = StrokeCap.Round)
                            else drawLine(Color(0xFF000000.toInt() or ramp[b[i]]), at(q, i), at(q, i + 1), 3.6f * density, cap = StrokeCap.Round) }
                        w.pin?.let { p -> if (!p[0].isNaN()) flewPin(Offset(ox + p[0] * kk, oy + p[1] * kk), flew, measurer) }
                    }
                }
            }
            if (list != null) {
                if (empty) {
                    Text(stringResource(R.string.app_records_empty_title), Modifier.fillMaxWidth().padding(top = 26.dp), fontFamily = Plex, fontWeight = FontWeight.Bold,
                        fontSize = 21.sp, color = k.ink, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.app_records_empty_text), Modifier.fillMaxWidth().padding(top = 6.dp), fontFamily = Plex, fontSize = 14.5.sp, color = k.ink2, textAlign = TextAlign.Center)
                    Column(Modifier.padding(top = 18.dp).alpha(.5f).clearAndSetSemantics { }) {
                        for (what in listOf("max_kmh", "longest_m", "drop_m")) RecordRowView(Records.Row(what, null, null), k, locale, zone, null, null)
                    }
                } else Column(Modifier.padding(top = 12.dp)) {
                    for (r in list) RecordRowView(r, k, locale, zone, scene, if (r.day == null || r.value == null) null else ({ haptics.tick(.3f); Qa.log("redesign records open ${r.what}"); onOpenDay(r.day.id) }))
                }
            }
            Box(Modifier.weight(1f, fill = false).height(24.dp))
            if (empty) {
                Text(stringResource(R.string.app_records_sample_note), Modifier.fillMaxWidth(), fontFamily = Plex, fontSize = 13.5.sp, color = k.ink2, textAlign = TextAlign.Center)
                val rec = stringResource(R.string.app_rec_sign)
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(k.acc)
                    .semantics { role = Role.Button }.clickable { haptics.tick(.3f); onRecord() }, contentAlignment = Alignment.Center) {
                    Text(rec, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (night) Color(0xFF1A0B3D) else Color.White)
                }
            } else Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔒", fontSize = 14.sp, modifier = Modifier.clearAndSetSemantics { })
                Text(stringResource(R.string.app_records_private), fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
            }
            Box(Modifier.navigationBarsPadding().height(18.dp))
        }
    }
}

@Composable
private fun RecordRowView(r: Records.Row, k: SheetInk, locale: Locale, zone: ZoneId, scene: MapScene?, onClick: (() -> Unit)?) {
    val title = stringResource(when (r.what) {
        "max_kmh" -> R.string.moment_record_max_kmh_title_1; "longest_m" -> R.string.moment_record_longest_m_title_1
        "drop_m" -> R.string.moment_record_drop_m_title_1; "descents" -> R.string.moment_record_descents_title_1
        "ski_dist_m" -> R.string.moment_record_ski_dist_m_title_1; else -> R.string.moment_steepest_title_1 })
    val v = r.value
    val (num, unit) = when {
        v == null -> "—" to ""
        r.what == "max_kmh" -> LiveNumbers.maxKmh(v) to stringResource(R.string.app_rec_unit)
        r.what == "longest_m" || r.what == "ski_dist_m" -> "%.1f".format(Locale.ROOT, v / 1000) to stringResource(R.string.app_run_km)
        r.what == "drop_m" -> metres(v) to stringResource(R.string.app_rec_m_down).substringBefore(' ')
        r.what == "steep_deg" -> "${v.toInt()}°" to ""
        else -> v.toInt().toString() to ""
    }
    fun name(key: String) = "⁦" + (scene?.runs?.pistes?.firstOrNull { it.key == key }?.name?.ifBlank { null } ?: key) + "⁩"
    val date = r.day?.let { d ->
        val dd = Instant.ofEpochMilli(d.start).atZone(zone).toLocalDate()
        val now = java.time.LocalDate.now(zone)
        DateTimeFormatter.ofPattern(if (dd.year == now.year) "d MMM" else "d MMM yyyy", locale).format(dd)
    }
    val detail = if (v == null) null else when (r.what) {
        "max_kmh" -> listOfNotNull(r.runs.firstOrNull()?.let(::name), date, stringResource(R.string.app_run_estimate)).joinToString(" · ")
        "longest_m" -> listOfNotNull(when {
            r.runs.size >= 2 -> stringResource(R.string.app_records_from_to, name(r.runs.first()), name(r.runs.last()))
            r.runs.size == 1 -> name(r.runs[0]); else -> null }, date).joinToString(" · ")
        "drop_m" -> listOfNotNull(stringResource(R.string.app_records_drop_note), date).joinToString(" · ")
        "ski_dist_m" -> listOfNotNull(stringResource(R.string.app_records_dist_note), date).joinToString(" · ")
        "steep_deg" -> r.runs.firstOrNull()?.let { name(it) + " · " + stringResource(R.string.app_records_steep_note) }
        else -> date
    }?.ifBlank { null }
    val newWord = stringResource(R.string.app_records_new)
    val said = listOfNotNull(title, "$num $unit".trim(), detail, if (r.fresh) newWord else null).joinToString(", ")
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .clearAndSetSemantics { contentDescription = said; if (onClick != null) role = Role.Button }) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, color = k.ink)
                    if (r.fresh) Box(Modifier.height(18.dp).clip(RoundedCornerShape(8.dp)).background(k.acc).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text(newWord, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = if (k.ink == Color(0xFF0E2233)) Color.White else Color(0xFF1A0B3D))
                    }
                }
                if (detail != null) Text(detail, Modifier.padding(top = 3.dp), fontFamily = Plex, fontSize = 12.sp, color = k.ink2, maxLines = 1)
            }
            Row(Modifier.width(104.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                Text(num, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 29.sp, color = k.ink)
                if (unit.isNotEmpty()) Text(unit, Modifier.padding(start = 4.dp, bottom = 5.dp), fontFamily = Plex, fontSize = 11.5.sp, color = k.ink2)
            }
        }
    }
}

/** "Here you flew" (board L06): the pin on the snow and, above it, its label on a navy pill; one form of the number everywhere. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.flewPin(p: Offset, label: String?, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawCircle(Color.White, 5.5f * density, p); drawCircle(Color(0xFFE0246E), 5.5f * density, p, style = androidx.compose.ui.graphics.drawscope.Stroke(2.2f * density))
    if (label == null) return
    val tl = measurer.measure(label, androidx.compose.ui.text.TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White))
    val w = tl.size.width + 20 * density; val h = tl.size.height + 10 * density
    val x = p.x.coerceIn(w / 2 + 4 * density, size.width - w / 2 - 4 * density)
    val top = (p.y - 14 * density - h).coerceAtLeast(4 * density)
    drawLine(Color(0xFF0E2233), Offset(p.x, p.y - 5.5f * density), Offset(p.x, top + h), 1.5f * density)
    drawRoundRect(Color(0xFF0E2233), Offset(x - w / 2, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
    drawText(tl, topLeft = Offset(x - tl.size.width / 2f, top + 5 * density))
}

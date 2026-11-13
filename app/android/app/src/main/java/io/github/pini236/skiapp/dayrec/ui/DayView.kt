package io.github.pini236.skiapp.dayrec.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.dayrec.DayAnalysis
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The end of the day (round 29, version 97): three numbers, the day on a map of its own (the runs dim, only your line in
 * colour, by speed), and the timeline, which is the truth. Computed from the log on the phone, off the main thread; nothing
 * leaves the phone.
 */
private class Loaded(val day: DayModel.Day, val track: Track, val story: StoryData?)

/**
 * The last day shown, and how far it was scrolled: coming back from the replay (or from the history) the day is there at
 * once and where it was, instead of "loading" and the top of the page. Keyed by the file and when it last changed (a day
 * that is analysed again is a different one); one day only, in the process's memory.
 */
private object LastDay {
    var key: String? = null
    var loaded: Loaded? = null
    var scroll = 0
    fun keyOf(f: File) = f.absolutePath + "@" + f.lastModified() + "/" + f.length()
}

/** The day's line, thinned for drawing: projected metres (as the map's runs), and the speed of each point in km/h. */
@androidx.compose.runtime.Immutable
class Track(val x: FloatArray, val y: FloatArray, val kmh: DoubleArray, val t: LongArray)

internal fun readTrack(file: File, max: Int = 1800): Track {
    val pts = DayLog.read(file).points
    val step = (pts.size / max).coerceAtLeast(1)
    val keep = pts.filterIndexed { i, _ -> i % step == 0 }
    return Track(FloatArray(keep.size) { Geo.x(keep[it].lon) }, FloatArray(keep.size) { Geo.y(keep[it].lat) },
        DoubleArray(keep.size) { (keep[it].speed ?: 0.0) * 3.6 }, LongArray(keep.size) { keep[it].t })
}

@Composable
fun DayViewScreen(file: File, runs: Runs?, sample: Boolean = false, onReplay: (() -> Unit)? = null, onBack: () -> Unit) {
    val c = Ski.colors
    val ctx = LocalContext.current
    val key = LastDay.keyOf(file)
    var loaded by remember(file) { mutableStateOf<Loaded?>(if (LastDay.key == key) LastDay.loaded else null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        if (loaded != null) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            // the recorder closes the day (and writes the result) after the tap; wait for it, up to a minute
            var waited = 0
            while (DayRec.recording && waited < 200) { delay(300); waited++ }
            val out = runCatching { DayAnalysis.ensure(ctx, file) }.getOrNull()
            val result = out?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            if (result == null) { failed = true; return@withContext }
            val day = DayModel.build(result, ZoneId.of(DayParams.TZ))
            loaded = Loaded(day, readTrack(file), StoryData.read(DayStore.momentsOf(file))).also { LastDay.key = LastDay.keyOf(file); LastDay.loaded = it; LastDay.scroll = 0 }
        }
    }
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_day_title), Modifier.weight(1f), style = Ski.type.title, color = c.ink)
            BackLink(stringResource(R.string.nav_home), onBack)
        }
        val l = loaded
        when {
            l != null -> DayBody(l, runs, sample, onReplay, file)
            failed -> Text(stringResource(R.string.app_day_empty), Modifier.padding(16.dp), style = Ski.type.body, color = c.muted)
            else -> Text(stringResource(R.string.app_day_loading), Modifier.padding(16.dp), style = Ski.type.body, color = c.muted)
        }
    }
}

@Composable
private fun DayBody(l: Loaded, runs: Runs?, sample: Boolean, onReplay: (() -> Unit)?, file: File) {
    val c = Ski.colors
    val s = l.day.summary
    val zone = ZoneId.of(DayParams.TZ)
    val scroll = rememberScrollState(if (LastDay.loaded === l) LastDay.scroll else 0)
    androidx.compose.runtime.DisposableEffect(l) { onDispose { if (LastDay.loaded === l) LastDay.scroll = scroll.value } }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (sample) Text(stringResource(R.string.app_day_sample) + " · " + stringResource(R.string.app_day_sample_note), style = Ski.type.bodyBold, color = c.glacier)
        else Text(DateTimeFormatter.ofPattern("EEEE d.M", Locale.getDefault()).format(Instant.ofEpochMilli(s.start).atZone(zone)), style = Ski.type.body, color = c.muted)
        Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp, c.rule)).padding(vertical = 14.dp)) {
            Sum(s.descents.toString(), stringResource(R.string.app_day_sum_descents), Modifier.weight(1f))
            Sum(LiveNumbers.metres(s.dropM), stringResource(R.string.app_day_sum_drop), Modifier.weight(1f))
            Sum(LiveNumbers.maxKmh(s.maxKmh), stringResource(R.string.app_day_sum_max), Modifier.weight(1f))
        }
        if (l.day.chapters.isEmpty()) Text(stringResource(R.string.app_day_empty), style = Ski.type.body, color = c.muted)
        else {
            l.story?.let { StoryCard(it, s, l.track, sample) { k -> titleOf(runs, k) } }
            DayMap(l.track, runs, s.maxLat, s.maxLon)
            if (onReplay != null) Text(stringResource(R.string.app_day_replay), Modifier.fillMaxWidth().border(BorderStroke(1.5.dp, c.ink)).clickable(role = Role.Button, onClick = onReplay).heightIn(min = 48.dp).padding(vertical = 12.dp),
                textAlign = TextAlign.Center, style = Ski.type.bodyBold, color = c.ink)
            Text(stringResource(R.string.app_day_note), style = Ski.type.small, color = c.muted)
            // the test build's field-tester tools; an empty stand-in in the store build
            if (!sample) io.github.pini236.skiapp.fieldtools.FieldTools.DayExtras(file)
            for (ch in l.day.chapters) Chapter(ch, runs, zone)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Sum(value: String, label: String, modifier: Modifier, size: androidx.compose.ui.unit.TextUnit = 40.sp) {
    val c = Ski.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Ski.type.title.copy(fontSize = size), color = c.ink, maxLines = 1)
        Text(label, style = Ski.type.small.copy(fontSize = 11.5.sp), color = c.muted, textAlign = TextAlign.Center)
    }
}

/** The day on a map of its own: the runs and lifts dim, your line over them in the colours of the speed (round 29, 10 b). */
@Composable
internal fun DayMap(t: Track, runs: Runs?, maxLat: Double?, maxLon: Double?, height: androidx.compose.ui.unit.Dp = 340.dp, showPin: Boolean = true) {
    val c = Ski.colors
    val pin = stringResource(R.string.app_day_pin)
    Box(Modifier.fillMaxWidth().height(height).background(c.glacier.copy(alpha = .10f)).border(BorderStroke(1.dp, c.rule))) {
        Canvas(Modifier.fillMaxSize()) {
            if (t.x.isEmpty()) return@Canvas
            var x0 = t.x.min(); var x1 = t.x.max(); var y0 = t.y.min(); var y1 = t.y.max()
            val pad = 120f // metres of air around the line
            x0 -= pad; x1 += pad; y0 -= pad; y1 += pad
            val k = minOf(size.width / (x1 - x0), size.height / (y1 - y0))
            val ox = (size.width - (x1 - x0) * k) / 2f; val oy = (size.height - (y1 - y0) * k) / 2f
            fun px(x: Float) = ox + (x - x0) * k
            fun py(y: Float) = oy + (y - y0) * k
            val dim = c.muted.copy(alpha = .35f)
            val w = 1.6.dp.toPx()
            fun near(x: Float, y: Float) = x in x0 - 400f..x1 + 400f && y in y0 - 400f..y1 + 400f
            if (runs != null) {
                for (p in runs.pistes) for (ln in p.lines) {
                    var i = 0
                    while (i + 3 < ln.size) {
                        if (near(ln[i], ln[i + 1]) || near(ln[i + 2], ln[i + 3]))
                            drawLine(dim, Offset(px(ln[i]), py(ln[i + 1])), Offset(px(ln[i + 2]), py(ln[i + 3])), w)
                        i += 2
                    }
                }
            }
            for (i in 0 until t.x.size - 1) {
                // a hole in the recording (a minute without points) is not a line
                if (t.t[i + 1] - t.t[i] > 90_000L) continue
                drawLine(Color(SpeedScale.argb(maxOf(t.kmh[i], t.kmh[i + 1]))), Offset(px(t.x[i]), py(t.y[i])), Offset(px(t.x[i + 1]), py(t.y[i + 1])),
                    4.dp.toPx(), StrokeCap.Round)
            }
            if (maxLat != null && maxLon != null) {
                val c0 = Offset(px(Geo.x(maxLon)), py(Geo.y(maxLat)))
                drawCircle(Color.White, 9.dp.toPx(), c0); drawCircle(Color(0xFF7B2D9B), 6.dp.toPx(), c0, style = Stroke(3.dp.toPx()))
            }
        }
        if (showPin) Text(pin, Modifier.align(Alignment.TopStart).padding(8.dp), style = Ski.type.small, color = c.muted)
    }
}

@Composable
private fun Chapter(ch: DayModel.Chapter, runs: Runs?, zone: ZoneId) {
    val c = Ski.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(if (ch.afternoon) R.string.app_day_afternoon else R.string.app_day_morning), style = Ski.type.title.copy(fontSize = 30.sp), color = c.ink)
        Text(stringResource(R.string.app_day_n_descents, ch.descents), Modifier.padding(bottom = 4.dp), style = Ski.type.small, color = c.muted)
    }
    val hhmm = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    for (it in ch.items) {
        val at = hhmm.format(Instant.ofEpochMilli(it.t0).atZone(zone))
        when (it) {
            is DayModel.Descent -> DescentCard(it, at, runs, zone)
            is DayModel.Queue -> Line(stringResource(R.string.app_day_queue, it.minutes) + (it.lift?.let { l -> " · $l" } ?: ""), at, Color(0xFFFCEBD0))
            is DayModel.Rest -> Line(stringResource(if (it.long) R.string.app_day_lunch else R.string.app_day_rest, it.minutes), at, Color(0xFFFCEBD0))
            is DayModel.Walk -> Line(stringResource(R.string.app_day_walk, Math.round(it.distM)), at, null)
        }
    }
}

@Composable
private fun Line(text: String, at: String, bg: Color?) {
    val c = Ski.colors
    Row(Modifier.fillMaxWidth().let { m -> if (bg != null) m.background(bg) else m }.heightIn(min = 40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = if (bg != null) Color(0xFF13233A) else c.muted)
        Text(at, style = Ski.type.small, color = if (bg != null) Color(0xFF13233A) else c.muted)
    }
}

@Composable
private fun DescentCard(d: DayModel.Descent, at: String, runs: Runs?, zone: ZoneId) {
    val c = Ski.colors
    var open by remember(d.t0) { mutableStateOf(false) }
    // the analysis speaks in keys of runs; the skier reads their names (PARITY 17)
    val shown = d.runs.map { shortRuns(it.split(" · ").joinToString(" · ") { k -> titleOf(runs, k) }) }
    val color = d.runs.firstOrNull()?.substringBefore(" · ")?.let { k -> runs?.pistes?.firstOrNull { it.key == k }?.color }
        ?.let { col -> when (col) { "green" -> c.green; "red" -> c.red; "black" -> c.black; else -> c.blue } } ?: c.muted
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = .10f)).clickable(role = Role.Button) { open = !open }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.background(Color(SpeedScale.argb(d.maxKmh))).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(LiveNumbers.maxKmh(d.maxKmh) + " " + stringResource(R.string.app_rec_unit), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = if ((d.maxKmh ?: 0.0) >= 25) Color.White else Color(0xFF13233A))
            }
            Column(Modifier.weight(1f)) {
                if (shown.isEmpty()) Text(stringResource(R.string.app_day_off), style = Ski.type.bodyBold, color = c.ink)
                else for (n in shown) Text(n, style = Ski.type.title.copy(fontSize = 22.sp), color = c.ink)
                if (shown.isNotEmpty() && d.offRuns) Text(stringResource(R.string.app_day_off), style = Ski.type.small, color = c.muted)
            }
            Text(at, style = Ski.type.small, color = c.muted)
        }
        if (open) {
            // the card of the descent: the four numbers and when the top speed was (round 29; a number is a fact, not a goal)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp)) {
                Sum(LiveNumbers.maxKmh(d.avgKmh), stringResource(R.string.app_day_avg), Modifier.weight(1f), 28.sp)
                Sum(Math.round(d.movingS / 60.0).toString(), stringResource(R.string.app_day_time), Modifier.weight(1f), 28.sp)
                Sum(String.format(Locale.ROOT, "%.1f", d.distM / 1000.0), stringResource(R.string.app_rec_dist), Modifier.weight(1f), 28.sp)
                Sum(Math.round(d.dropM).toString(), stringResource(R.string.app_rec_drop), Modifier.weight(1f), 28.sp)
            }
            d.maxAt?.let { m ->
                Text(stringResource(R.string.app_day_top_at, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(m).atZone(zone))),
                    Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp), style = Ski.type.small, color = c.muted)
            }
        }
    }
}

/** The name of a run by its key (the key when the run is not in the data). */
internal fun titleOf(runs: Runs?, key: String): String = runs?.pistes?.firstOrNull { it.key == key }?.title ?: key

/** "Tatra 1 · Tatra 2" as the map says it: "Tatra 1 · 2". */
internal fun shortRuns(names: String): String {
    val parts = names.split(" · ")
    if (parts.size < 2) return names
    val first = parts[0]
    val stem = first.substringBeforeLast(' ', "")
    return if (stem.isNotEmpty() && parts.drop(1).all { it.startsWith("$stem ") }) first + " · " + parts.drop(1).joinToString(" · ") { it.removePrefix("$stem ") } else names
}

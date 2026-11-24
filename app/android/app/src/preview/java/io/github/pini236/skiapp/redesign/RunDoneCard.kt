package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.map.Sky
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The completion round, part 1 (P04; Android P11): "one descent" ended, by itself or by hand. The descent on the painted
 * mountain in the light with "here you flew"; on top "one descent · saved" and a close; in the sheet the run, the top
 * speed (an estimate), four numbers, the descent as a small film, why it stopped, and "another descent", "the film",
 * "share". Its content is the final analysis written beside the log (the architect), not the live view.
 */
@Composable
internal fun RunDoneCard(file: File, p: RunDone.Pending, scene: MapScene, mode: DayNight.Mode, haptics: Haptics, onAgain: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val night = remember(mode) { Sky.at(DayNight.lightTime(System.currentTimeMillis(), mode)).dark }
    val k = remember(night) { SheetInk(night) }
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    // the film or the picture to share, over the card; back returns here
    var sub by remember { mutableStateOf(0) }
    BackHandler(enabled = sub == 0) { onClose() }
    // the app is open: the card is enough, the notification goes (P04)
    LaunchedEffect(Unit) { RunDone.cancel(ctx); haptics.tick(.5f) }

    class Loaded(val d: JSONObject?, val runs: List<String>, val mountain: StoryVideo.Mountain?, val line: List<Pair<FloatArray, IntArray>>, val raw: List<Pair<FloatArray, IntArray>>, val pin: FloatArray?)
    var loaded by remember(file) { mutableStateOf<Loaded?>(null) }
    LaunchedEffect(file, night) {
        loaded = withContext(Dispatchers.Default) {
            // the recorder writes the result before it goes; wait for it a little if the card came first
            var res: JSONObject? = null
            for (i in 0 until 100) { res = runCatching { JSONObject(DayStore.resultOf(file).readText()) }.getOrNull(); if (res != null) break; kotlinx.coroutines.delay(300) }
            val segs = res?.optJSONArray("segments")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            val d = segs.filter { it.optString("type") == "descent" }.maxByOrNull { it.optDouble("dist_m", 0.0) }
            val runs = LinkedHashSet<String>()
            d?.optJSONArray("legs")?.let { a -> for (i in 0 until a.length()) { val l = a.getJSONObject(i); if (l.optString("kind") == "run") l.optJSONArray("runs")?.let { r -> for (j in 0 until r.length()) runs += r.getString(j) } } }
            // no descent: the bare painted mountain stays behind the card, with no lines
            if (d == null) return@withContext Loaded(null, emptyList(), OnMountain.load(ctx, night, scene.terrain::elev), emptyList(), emptyList(), null)
            val pts = DayLog.read(file).points.map { DayPaint.Pt(it.t, Geo.x(it.lon), Geo.y(it.lat), it.speed?.let { s -> s * 3.6 }) }
            val paint = DayPaint.build(pts, listOf(DayPaint.Seg("descent", d.optLong("t0"), d.optLong("t1"))))
            val raw = paint.lines.map { it.xy to it.band }
            val m = OnMountain.load(ctx, night, scene.terrain::elev)
            val pj = m?.view?.proj
            val line = if (pj == null) emptyList() else raw.map { (xy, b) -> OnMountain.project(pj, m!!.elev, xy, 2f) to b }
            val at = d.optJSONObject("max_at")
            val pin = if (pj == null || at == null) null else OnMountain.project(pj, m!!.elev, floatArrayOf(Geo.x(at.optDouble("lon")), Geo.y(at.optDouble("lat"))), 2f)
            Loaded(d, runs.toList(), m, line, raw, pin)
        }
        Qa.log("redesign run done card ${if (loaded?.d != null) "descent" else "empty"}")
    }
    val l = loaded

    Box(Modifier.fillMaxSize().background(if (night) Color(0xFF0B1220) else Color(0xFFEEF3F8))) {
        // the mountain: the whole painting, enlarged 1.7 around the descent (no close view of its own on Android yet)
        val mt = l?.mountain
        if (mt != null && l.line.isEmpty()) {
            val img = remember(mt) { mt.img.asImageBitmap() }
            Canvas(Modifier.fillMaxSize()) {
                // the whole painting covering the screen's top two thirds, its middle in the middle
                val kk = maxOf(size.width / mt.view.w, size.height * .66f / mt.view.h)
                val ox = (size.width - mt.view.w * kk) / 2; val oy = (size.height * .66f - mt.view.h * kk) / 2
                drawImage(img, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize((mt.view.w * kk).toInt(), (mt.view.h * kk).toInt()))
            }
        } else if (mt != null && l.line.isNotEmpty()) {
            val flew = l.d?.takeIf { !it.isNull("max_kmh") }?.let { stringResource(R.string.app_film_top_speed, LiveNumbers.maxKmh(it.optDouble("max_kmh"))) }
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val img = remember(mt) { mt.img.asImageBitmap() }
            Canvas(Modifier.fillMaxSize()) {
                val pl = OnMountain.place(mt.view, l.line.map { it.first }, size.width, size.height, size.minDimension * .12f, size.height * .5f, maxZoom = 1.7f)
                val kk = pl[0]; val ox = pl[1]; val oy = pl[2]
                drawImage(img, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize((mt.view.w * kk).toInt(), (mt.view.h * kk).toInt()))
                val case = if (night) Color(0xFF050A16) else Color.White
                for (pass in 0..1) for ((q, b) in l.line) for (i in 0 until q.size / 2 - 1) {
                    if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
                    val a = Offset(ox + q[i * 2] * kk, oy + q[i * 2 + 1] * kk); val c = Offset(ox + q[i * 2 + 2] * kk, oy + q[i * 2 + 3] * kk)
                    if (pass == 0) drawLine(case, a, c, 7.2f * density, cap = StrokeCap.Round) else drawLine(Color(0xFF000000.toInt() or ramp[b[i]]), a, c, 4.6f * density, cap = StrokeCap.Round)
                }
                l.pin?.takeIf { !it[0].isNaN() }?.let { q -> flewPin(Offset(ox + q[0] * kk, oy + q[1] * kk), flew, measurer) }
            }
        }
        // the pill: saved, and close
        Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(44.dp).shadow(2.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(k.surf).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            // nothing measured: just "one descent", no tick and no "saved" (the designer, 24.11.2026)
            val measured = l?.d != null
            if (measured) Text("✓", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.good)
            Text(stringResource(if (measured) R.string.app_run_saved else R.string.app_rec_one), Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = k.ink)
            val close = stringResource(R.string.common_close)
            Box(Modifier.size(44.dp).semantics { role = Role.Button; contentDescription = close }.clickable { haptics.tick(.3f); onClose() }, contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 16.sp, color = k.ink)
            }
        }
        // the sheet
        Box(Modifier.align(Alignment.BottomCenter)) {
            LiveSheet(k, opaque = true) {
                val d = l?.d
                if (l != null && d == null) Text(stringResource(R.string.app_day_empty), fontFamily = Plex, fontSize = 15.sp, color = k.ink2)
                if (d != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // the run: its sign and name, in the order skied
                        if (l.runs.isEmpty()) Text(stringResource(R.string.app_rec_off), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
                        else WholeChain(l.runs.size, sep = { Text("›", fontFamily = Plex, fontSize = 15.sp, color = k.ink2) },
                            more = { Text("…", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink2) }, item = { i ->
                            val key = l.runs[i]
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val pst = scene.runs?.pistes?.firstOrNull { it.key == key }
                                pst?.refs?.firstOrNull()?.let { ref ->
                                    Box(Modifier.heightIn(min = 30.dp).clip(RoundedCornerShape(7.dp)).background(Color(io.github.pini236.skiapp.game.runColor(pst.color)))
                                        .border(1.5.dp, Color.White, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                                        Text(ref, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                                    }
                                }
                                Text("⁦${pst?.name?.ifBlank { null } ?: key}⁩", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 1, softWrap = false)
                            }
                        })
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (d.isNull("max_kmh")) "—" else LiveNumbers.maxKmh(d.optDouble("max_kmh")), fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 64.sp, color = k.ink)
                            Column(Modifier.padding(bottom = 12.dp)) {
                                Text(stringResource(R.string.app_rec_kmh_max), fontFamily = Plex, fontSize = 12.5.sp, color = k.ink)
                                Text(stringResource(R.string.app_run_estimate), fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
                            }
                        }
                        val s = ((d.optLong("t1") - d.optLong("t0")) / 1000).toInt()
                        val dist = d.optDouble("dist_m", 0.0)
                        val avg = if (s > 0) dist / s * 3.6 else null
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            @Composable fun cell(v: String, label: String) = Column { Text(v, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 25.sp, color = k.ink)
                                Text(label, fontFamily = Plex, fontSize = 11.5.sp, color = k.ink2) }
                            cell("%d:%02d".format(Locale.ROOT, s / 60, s % 60), stringResource(R.string.app_run_min))
                            cell(metres(d.optDouble("drop_m", 0.0)), stringResource(R.string.app_rec_m_down))
                            cell("%.1f".format(Locale.ROOT, dist / 1000), stringResource(R.string.app_run_km))
                            cell(avg?.let { Math.round(it).toString() } ?: "—", stringResource(R.string.app_run_kmh_avg))
                        }
                    }
                    // the descent as a small film: the real line turned so its start is at the top
                    MiniFilm(l.raw, ramp, night)
                }
                // why it stopped (by hand: no line)
                val liftN = p.lift?.let { id -> scene.runs?.lifts?.firstOrNull { it.id == id }?.name?.ifBlank { null } }
                val why = when (p.reason) {
                    "lift" -> liftN?.let { stringResource(R.string.app_run_stop_lift, "⁦$it⁩") }
                    "station" -> liftN?.let { stringResource(R.string.app_run_stop_station, "⁦$it⁩") }
                    "still" -> stringResource(R.string.app_run_stop_still)
                    else -> null
                }
                if (why != null) Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
                    Text(why, Modifier.padding(top = 10.dp), fontFamily = Plex, fontSize = 13.5.sp, color = k.ink2)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val again = stringResource(R.string.app_run_again)
                    Row(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(k.acc).semantics { role = Role.Button }
                        .clickable { haptics.tick(.3f); Qa.log("redesign run done again"); onAgain() }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        val on = if (night) Color(0xFF1A0B3D) else Color.White
                        Box(Modifier.size(12.dp).clip(CircleShape).background(on))
                        Text(again, Modifier.padding(start = 8.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = on)
                    }
                    if (d != null) {
                        SecondaryButton(stringResource(R.string.app_run_film), k) { haptics.tick(.3f); sub = 1 }
                        SecondaryButton(stringResource(R.string.app_day_share), k) { haptics.tick(.3f); sub = 2 }
                    }
                }
            }
        }
        if (sub != 0) DayMountain(file, false, scene, mode, haptics, openFilm = sub == 1, openShare = sub == 2) { sub = 0 }
    }
}

/** 64 by 150: the descent's real line, turned so the start is at the top, in the light with a case. */
@Composable
private fun MiniFilm(lines: List<Pair<FloatArray, IntArray>>, ramp: IntArray, night: Boolean) {
    Canvas(Modifier.size(64.dp, 150.dp)) {
        val all = lines.flatMap { (xy, _) -> (0 until xy.size / 2).map { xy[it * 2] to xy[it * 2 + 1] } }
        if (all.size < 2) return@Canvas
        val (sx, sy) = all.first(); val (ex, ey) = all.last()
        // turn the start-to-end so it points down the screen (map y grows south, as the screen's)
        val ang = (Math.PI / 2 - atan2((ey - sy).toDouble(), (ex - sx).toDouble())).toFloat()
        val c = cos(ang); val s = sin(ang)
        fun rot(x: Float, y: Float) = (x - sx) * c - (y - sy) * s to (x - sx) * s + (y - sy) * c
        val r = all.map { rot(it.first, it.second) }
        val x0 = r.minOf { it.first }; val x1 = r.maxOf { it.first }; val y0 = r.minOf { it.second }; val y1 = r.maxOf { it.second }
        val pad = 6f * density
        val kk = minOf((size.width - pad * 2) / (x1 - x0).coerceAtLeast(1f), (size.height - pad * 2) / (y1 - y0).coerceAtLeast(1f))
        val ox = (size.width - (x1 - x0) * kk) / 2 - x0 * kk; val oy = (size.height - (y1 - y0) * kk) / 2 - y0 * kk
        val case = if (night) Color(0xFF050A16) else Color.White
        for (pass in 0..1) for ((xy, b) in lines) for (i in 0 until xy.size / 2 - 1) {
            val (ax, ay) = rot(xy[i * 2], xy[i * 2 + 1]); val (bx, by) = rot(xy[i * 2 + 2], xy[i * 2 + 3])
            val a = Offset(ox + ax * kk, oy + ay * kk); val e = Offset(ox + bx * kk, oy + by * kk)
            if (pass == 0) drawLine(case, a, e, (4f + 5.2f) * density, cap = StrokeCap.Round) else drawLine(Color(0xFF000000.toInt() or ramp[b[i]]), a, e, 4f * density, cap = StrokeCap.Round)
        }
    }
}

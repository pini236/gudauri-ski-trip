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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.dayrec.DayAnalysis
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.dayrec.Moments
import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.dayrec.ui.MomentText
import io.github.pini236.skiapp.dayrec.ui.StoryData
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Round 4 of the redesign (decision 84), board L06: the end of the day on the mountain. The live model with the day's
 * descents as lines of light coloured by speed, the rides as dotted lines, "here you flew" at the top speed, and a
 * card with the day's head and its three numbers. Test build only, behind DAYREC_UI as the day itself.
 *
 * What the board has and the language file does not yet ("the day in a minute", "the story", the Kobi chip, slow and
 * fast under the scale) is not invented: the replay button keeps today's words, and a touch on the card opens the
 * day's screen as it is (the story, the timeline, the records, the share), so nothing of today is lost.
 */
@Composable
fun DayMountain(file: File, sample: Boolean, scene: MapScene, mode: DayNight.Mode, haptics: Haptics, onBack: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.of(DayParams.TZ)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val time = DayNight.lightTime(now, mode)
    val night = remember(time) { Sky.at(time).dark }
    val view = remember { LiveView(context, scene) }
    DisposableEffect(Unit) {
        val life = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> view.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> {}
            }
        }
        life?.addObserver(obs)
        Qa.log("redesign day open")
        onDispose { life?.removeObserver(obs); view.release(); Qa.log("redesign day closed") }
    }
    // the film of the day (board L08): on, playing, and its clock in ms
    var filming by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var fms by remember { mutableStateOf(0f) }
    // the ridge of the day and its timeline (board L07), opened from the card
    var ridge by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableStateOf(false) }
    // the story of the day (board L10), from the ridge's "share"
    var story by androidx.compose.runtime.saveable.rememberSaveable(file.path) { mutableStateOf(false) }
    BackHandler { if (story) story = false else if (ridge) ridge = false else if (filming) { filming = false; playing = false } else onBack() }

    class Loaded(val paint: DayPaint.Day, val day: DayModel.Day, val story: StoryData?, val elev: FloatArray, val segs: List<JSONObject>)
    var loaded by remember(file) { mutableStateOf<Loaded?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        withContext(Dispatchers.Default) {
            // the recorder closes the day (and writes the result) after the tap; wait for it, up to a minute (as DayView)
            var waited = 0
            while (DayRec.recording && waited < 200) { delay(300); waited++ }
            val out = runCatching { DayAnalysis.ensure(context, file) }.getOrNull()
            val result = out?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            if (result == null) { failed = true; return@withContext }
            val raw = result.optJSONArray("segments")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
            val segs = raw.map { DayPaint.Seg(it.optString("type"), it.optLong("t0"), it.optLong("t1")) }
            val pts = DayLog.read(file).points.map { DayPaint.Pt(it.t, Geo.x(it.lon), Geo.y(it.lat), it.speed?.let { s -> s * 3.6 }) }
            val paint = DayPaint.build(pts, segs)
            val tr = paint.track
            loaded = Loaded(paint, DayModel.build(result, zone), StoryData.read(DayStore.momentsOf(file)), FloatArray(tr.t.size) { scene.terrain.elev(tr.x[it], tr.y[it]) }, raw)
        }
    }
    val paint = loaded?.paint
    val film = remember(loaded) { loaded?.let { DayPaint.film(it.paint, it.day.summary.maxAt) } }
    // the film runs on the frame clock; at its end it stays on the last moment (the whole day drawn)
    LaunchedEffect(filming, playing, film) {
        val f = film ?: return@LaunchedEffect
        if (!filming || !playing) return@LaunchedEffect
        if (fms >= f.length) fms = 0f
        var last = 0L
        while (fms < f.length) withFrameNanos { n -> if (last != 0L) fms = (fms + (n - last) / 1e6f).coerceAtMost(f.length); last = n }
        playing = false; Qa.log("redesign day film end")
    }
    val filmT = if (filming && film != null) film.at(fms) else null
    // the light of the moment: the film's hour while it runs (its shadows at most every 10 minutes of it, LiveView),
    // otherwise now; the lines in the colours of that light
    val lightT = filmT ?: time
    val lineNight = remember(lightT / 600_000) { Sky.at(lightT).dark }
    androidx.compose.runtime.SideEffect { view.setTime(lightT) }
    LaunchedEffect(filmT, paint) { val p = paint ?: return@LaunchedEffect
        view.dayReveal(filmT?.let { ((it - p.t0).toFloat() / (p.t1 - p.t0).coerceAtLeast(1)) }) }
    var framed by remember(paint) { mutableStateOf(false) }
    LaunchedEffect(paint, lineNight) { if (paint != null) { view.showDay(paint, lineNight, frame = !framed); framed = true } }
    // the emulator's held film moments (qa.redesign film:<s> of the film, or film:play and film:off)
    LaunchedEffect(RedesignQa.seq) {
        val c = RedesignQa.cmd
        when {
            c == "ridge" -> { filming = false; playing = false; ridge = true }
            c == "ridge:off" -> ridge = false
            c == "story" -> story = true
            c == "story:off" -> story = false
            c == "film:off" -> { filming = false; playing = false; Qa.log("redesign day film off") }
            c == "film:play" -> { fms = 0f; filming = true; playing = true; Qa.log("redesign day film start") }
            c.startsWith("film:") -> c.removePrefix("film:").toFloatOrNull()?.let { sec -> playing = false; filming = true; fms = sec * 1000f; Qa.log("redesign day film held ${sec.toInt()}") }
        }
    }
    // a tap on the mountain stops the film or lets it run on
    LaunchedEffect(filming) { view.onTap = { _, _ -> if (filming) { playing = !playing; haptics.tick(.3f); Qa.log("redesign day film ${if (playing) "play" else "pause"}") } } }

    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    // the button's violet: the scale's own (DayPaint), by day its third step, at night its second
    val accent = Color(0xFF000000.toInt() or (if (night) DayPaint.NIGHT[1] else DayPaint.DAY[2]))

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

        // "here you flew" at the top speed's place (board L06): a pin on the snow and its label above it
        val s = loaded?.day?.summary
        val measurer = rememberTextMeasurer()
        var pin by remember { mutableStateOf<Offset?>(null) }
        var dot by remember { mutableStateOf<Offset?>(null) }
        val ft = androidx.compose.runtime.rememberUpdatedState(filmT)
        LaunchedEffect(s, paint) {
            val lat = s?.maxLat; val lon = s?.maxLon
            while (true) {
                withFrameNanos { }
                pin = if (lat == null || lon == null) null else view.toScreen(Geo.x(lon), Geo.y(lat), 4f)?.let { Offset(it[0], it[1]) }
                dot = ft.value?.let { tt -> paint?.track?.at(tt) }?.let { q -> view.toScreen(q[0], q[1], 4f) }?.let { Offset(it[0], it[1]) }
            }
        }
        val pinText = s?.maxKmh?.let { stringResource(R.string.app_rec_pin_label, "%.1f".format(java.util.Locale.ROOT, it)) }
        val dens = LocalContext.current.resources.displayMetrics.density
        Canvas(Modifier.fillMaxSize()) {
            // the dot of the film (board L08): yours, blue with a halo, on the real line
            dot?.let { d -> drawCircle(Color(0x553B7BF0), 11f * dens, d); drawCircle(Color.White, 6.5f * dens, d); drawCircle(Color(0xFF2F6FE4), 4.5f * dens, d) }
            // "here you flew" once the film has passed it (all of it on the painted day)
            val maxAt = s?.maxAt
            if (filmT != null && maxAt != null && filmT < maxAt) return@Canvas
            val p = pin ?: return@Canvas
            if (pinText == null) return@Canvas
            val ring = Color(0xFF000000.toInt() or (if (night) DayPaint.NIGHT[4] else DayPaint.DAY[4]))
            drawCircle(Color.White, 5.5f * dens, p); drawCircle(ring, 5.5f * dens, p, style = Stroke(2.2f * dens))
            val tl = measurer.measure(pinText, TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White))
            val w = tl.size.width + 20 * dens; val h = tl.size.height + 10 * dens
            val top = p.y - 14 * dens - h
            drawLine(Color(0xFF0E2233), Offset(p.x, p.y - 5.5f * dens), Offset(p.x, top + h), 1.5f * dens)
            drawRoundRect(Color(0xFF0E2233), Offset(p.x - w / 2, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
            drawText(tl, topLeft = Offset(p.x - tl.size.width / 2f, top + 5 * dens))
        }

        // the top: back, "your day", the date and the hours, and "example" for the sample day (board L06)
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().height(44.dp)
            .shadow(10.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button) { haptics.tick(.3f); onBack() }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (rtl) "›" else "‹", fontFamily = Plex, fontSize = 20.sp, color = ink)
            val when_ = s?.let { x ->
                val d = DateTimeFormatter.ofPattern("d.M.yyyy").format(Instant.ofEpochMilli(x.start).atZone(zone))
                val hm = DateTimeFormatter.ofPattern("HH:mm")
                "$d · ${hm.format(Instant.ofEpochMilli(x.start).atZone(zone))}–${hm.format(Instant.ofEpochMilli(x.end).atZone(zone))}"
            }
            Text(stringResource(R.string.app_day_title) + (when_?.let { " · $it" } ?: ""), Modifier.weight(1f), fontFamily = Plex, fontSize = 14.sp, color = ink, maxLines = 1)
            if (sample) Text(stringResource(R.string.app_day_sample), Modifier.border(1.dp, rule, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ink)
        }

        // the film's panel (board L08): the clock, the descents and metres down so far, and the ridge of the day under it,
        // its cursor at the film's moment; a drag on the ridge moves the film, a tap stops it or lets it run
        val l = loaded
        if (filming && l != null && film != null && filmT != null) {
            val zoneT = Instant.ofEpochMilli(filmT).atZone(zone)
            val desc = remember(l) { l.day.chapters.flatMap { it.items }.filterIsInstance<DayModel.Descent>() }
            val n = desc.count { it.t0 <= filmT }
            val drop = desc.sumOf { d -> if (filmT >= d.t1) d.dropM else if (filmT <= d.t0) 0.0 else d.dropM * (filmT - d.t0) / (d.t1 - d.t0).coerceAtLeast(1) }
            val nf = remember { java.text.NumberFormat.getIntegerInstance() }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp).fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(DateTimeFormatter.ofPattern("HH:mm").format(zoneT), fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 30.sp, color = ink)
                    for ((v, k) in listOf(nf.format(n) to R.string.app_day_sum_descents, nf.format(Math.round(drop)) to R.string.app_rec_m_down)) {
                        Column { Text(v, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink); Text(stringResource(k), fontFamily = Plex, fontSize = 12.sp, color = muted) }
                    }
                }
                val tr = l.paint.track; val el = l.elev
                val ramp = if (lineNight) DayPaint.NIGHT else DayPaint.DAY
                val lo = remember(l) { el.minOrNull() ?: 0f }; val hi = remember(l) { el.maxOrNull() ?: 1f }
                val span = (l.paint.t1 - l.paint.t0).coerceAtLeast(1).toFloat()
                Canvas(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(if (lineNight) Color(0x1FFFFFFF) else Color(0xFFF6F3FD))
                    .pointerInput(film) {
                        detectTapGestures { playing = !playing; haptics.tick(.3f) }
                    }
                    .pointerInput(film) {
                        detectHorizontalDragGestures(onDragStart = { playing = false }) { ch, _ ->
                            val x = if (rtl) size.width - ch.position.x else ch.position.x
                            fms = film.of(l.paint.t0 + ((x / size.width).coerceIn(0f, 1f) * span).toLong())
                        }
                    }) {
                    if (tr.t.size < 2) return@Canvas
                    fun px(t: Long) = ((t - l.paint.t0) / span).let { if (rtl) 1f - it else it } * size.width
                    fun py(e: Float) = size.height - 6 * dens - (e - lo) / (hi - lo).coerceAtLeast(1f) * (size.height - 14 * dens)
                    // the day's ground, faint, and its descents in the speed light up to the film's moment
                    val area = androidx.compose.ui.graphics.Path().apply {
                        moveTo(px(tr.t[0]), size.height); for (i in tr.t.indices) lineTo(px(tr.t[i]), py(el[i])); lineTo(px(tr.t.last()), size.height); close()
                    }
                    drawPath(area, Color(0xFF000000.toInt() or ramp[0]), alpha = .35f)
                    for (i in 0 until tr.t.size - 1) {
                        if (tr.t[i] > filmT) break
                        val a = Offset(px(tr.t[i]), py(el[i])); val b = Offset(px(tr.t[i + 1]), py(el[i + 1]))
                        if (tr.down[i]) drawLine(Color(0xFF000000.toInt() or ramp[tr.band[i]]), a, b, 2.2f * dens)
                        else drawLine(if (lineNight) Color(0xFFC9D5E2) else Color(0xFF22303F), a, b, 1f * dens, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1.5f * dens, 3f * dens)), alpha = .7f)
                    }
                    val cx = px(filmT)
                    drawLine(ink, Offset(cx, 0f), Offset(cx, size.height), 1.6f * dens)
                }
            }
        }
        // the card (board L06): the day's head, its three numbers, the scale and the replay; a touch opens the day's screen
        if (!filming) Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button) { haptics.tick(.3f); ridge = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(rule))
            when {
                l != null -> {
                    val sm = l.day.summary
                    val locale = context.resources.configuration.locales[0]
                    val head = remember(l) {
                        val h = MomentText.head(l.story?.theme, sm.descents, sm.dropM, sm.distM, locale)
                        MomentText.Words(context, locale).render(h, Moments.seed("story.head", sm.start)).first
                    }
                    Text(head ?: stringResource(R.string.app_day_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = ink, maxLines = 2)
                    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                        for ((v, k) in listOf(nf.format(sm.descents) to R.string.app_day_sum_descents, nf.format(Math.round(sm.dropM)) to R.string.app_rec_m_down,
                            "%.1f".format(locale, sm.distM / 1000.0) to R.string.app_rec_km_ski)) {
                            Column {
                                Text(v, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 30.sp, color = ink)
                                Text(stringResource(k), fontFamily = Plex, fontSize = 12.sp, color = muted)
                            }
                        }
                        Box(Modifier.weight(1f))
                        // the scale, slow to fast
                        LightScale(night, muted, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.app_day_minute), Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(accent)
                            .clickable(role = Role.Button) { haptics.tick(.4f); fms = 0f; filming = true; playing = true; Qa.log("redesign day film start") }.padding(top = 14.dp),
                            fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (l.story != null) Text(stringResource(R.string.app_day_story), Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).border(1.5.dp, ink, RoundedCornerShape(26.dp))
                            .clickable(role = Role.Button) { haptics.tick(.3f); story = true }.padding(horizontal = 22.dp).padding(top = 14.dp),
                            fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                failed -> Text(stringResource(R.string.app_day_empty), fontFamily = Plex, fontSize = 14.sp, color = muted)
                else -> Text(stringResource(R.string.app_day_loading), fontFamily = Plex, fontSize = 14.sp, color = muted)
            }
        }
        val lr = l
        if (ridge && lr != null) DayRidge(lr.paint, lr.elev, lr.day, lr.segs, scene.runs, night, sample, haptics, onShare = { story = true }, onBack = { ridge = false })
        val st = lr?.story
        if (story && lr != null && st != null) DayStory(st, lr.day.summary, lr.paint, { runCatching { io.github.pini236.skiapp.dayrec.ui.readTrack(file) }.getOrNull() },
            scene.runs, night, sample, haptics, onBack = { story = false })
    }
}

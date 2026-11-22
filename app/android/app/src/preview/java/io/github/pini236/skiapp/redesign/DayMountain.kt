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
fun DayMountain(file: File, sample: Boolean, scene: MapScene, mode: DayNight.Mode, haptics: Haptics, onReplay: () -> Unit, onDetails: () -> Unit, onBack: () -> Unit) {
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
    LaunchedEffect(time) { view.setTime(time) }
    BackHandler { onBack() }

    class Loaded(val paint: DayPaint.Day, val day: DayModel.Day, val story: StoryData?)
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
            val segs = result.optJSONArray("segments")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { DayPaint.Seg(it.optString("type"), it.optLong("t0"), it.optLong("t1")) } } ?: emptyList()
            val pts = DayLog.read(file).points.map { DayPaint.Pt(it.t, Geo.x(it.lon), Geo.y(it.lat), it.speed?.let { s -> s * 3.6 }) }
            loaded = Loaded(DayPaint.build(pts, segs), DayModel.build(result, zone), StoryData.read(DayStore.momentsOf(file)))
        }
    }
    val paint = loaded?.paint
    LaunchedEffect(paint, night) { if (paint != null) view.showDay(paint, night, frame = true) }

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
        LaunchedEffect(s) {
            val lat = s?.maxLat; val lon = s?.maxLon
            if (lat == null || lon == null) { pin = null; return@LaunchedEffect }
            while (true) { withFrameNanos { }; pin = view.toScreen(Geo.x(lon), Geo.y(lat), 4f)?.let { Offset(it[0], it[1]) } }
        }
        val pinText = s?.maxKmh?.let { stringResource(R.string.app_day_pin) + " · " + "%.1f".format(java.util.Locale.ROOT, it) }
        val dens = LocalContext.current.resources.displayMetrics.density
        Canvas(Modifier.fillMaxSize()) {
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

        // the card (board L06): the day's head, its three numbers, the scale and the replay; a touch opens the day's screen
        val l = loaded
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button) { haptics.tick(.3f); onDetails() }.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        // the scale, slow to fast (its words are not in the language file yet)
                        val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
                        Box(Modifier.padding(bottom = 10.dp).width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(ramp.map { Color(0xFF000000.toInt() or it) }.let { if (rtl) it.reversed() else it })))
                    }
                    Text(stringResource(R.string.app_day_replay), Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(accent)
                        .clickable(role = Role.Button) { haptics.tick(.4f); onReplay() }.padding(top = 14.dp),
                        fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                failed -> Text(stringResource(R.string.app_day_empty), fontFamily = Plex, fontSize = 14.sp, color = muted)
                else -> Text(stringResource(R.string.app_day_loading), fontFamily = Plex, fontSize = 14.sp, color = muted)
            }
        }
    }
}

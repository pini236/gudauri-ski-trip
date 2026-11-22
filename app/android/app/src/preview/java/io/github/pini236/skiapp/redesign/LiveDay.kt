package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Round 4 of the redesign (decision 84), boards L02, L03 and L13: the day while it is recorded, on the mountain. One
 * thing at a time: the speed, large, and where you probably are; the day so far drawn behind you in the speed light,
 * over the runs kept quiet; your dot, and the view moving with you. "Hold to finish" (a long press, so a glove in a
 * pocket does not end the day). Until the first fix "searching for satellites", the time already counting from the tap;
 * a weak signal says so, quietly. Read from [DayRec] as today's live screen; the recorder never waits for it.
 *
 * Kept from today's screen though not on the boards: the choice "all day / one descent" (its place on the boards is the
 * start sheet, L01, not built yet), and the line that says the notifications are off.
 */
@Composable
fun LiveDay(scene: MapScene, mode: DayNight.Mode, haptics: Haptics, liftName: (String) -> String, runName: (String) -> String,
            onSwitch: (single: Boolean) -> Unit, onStop: () -> Unit, onEnded: (java.io.File?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = DayNight.lightTime(now, mode)
    val night = remember(time / 600_000) { Sky.at(time).dark }
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
        Qa.log("redesign rec open")
        onDispose { life?.removeObserver(obs); view.release(); Qa.log("redesign rec closed") }
    }
    LaunchedEffect(time) { view.setTime(time) }
    LaunchedEffect(Unit) { view.onTap = { _, _ -> } }

    // as today's screen: the end of the day outside this screen (Stop in the notification) goes to the day that ended
    val recording = DayRec.recording
    var wasRecording by remember { mutableStateOf(false) }
    var dayFile by remember { mutableStateOf<java.io.File?>(null) }
    DayRec.current?.let { dayFile = it }
    LaunchedEffect(recording) {
        if (LiveNumbers.endedOutside(wasRecording, recording, dayFile != null)) { wasRecording = false; onEnded(dayFile) }
        else if (recording) wasRecording = true
    }
    var picked by remember { mutableStateOf<Boolean?>(null) }
    val single = picked ?: DayRec.single
    LaunchedEffect(now) { if (picked == DayRec.single) picked = null }
    val began = LiveNumbers.began(DayRec.tapAt, DayRec.startedAt)
    val searching = LiveNumbers.searching(recording, DayRec.lastFixAt)
    val weak = recording && !searching && LiveNumbers.weakGps(now, DayRec.lastFixAt, began)

    // the day so far, read from the log every few seconds off the main thread: the descents by the analysis so far, and
    // the stretch since it, while the live view says you ski
    var last by remember { mutableStateOf<FloatArray?>(null) }
    LaunchedEffect(night) {
        while (true) {
            val f = DayRec.current
            if (f != null) {
                val built = withContext(Dispatchers.Default) {
                    runCatching {
                        val pts = DayLog.read(f).points.map { DayPaint.Pt(it.t, Geo.x(it.lon), Geo.y(it.lat), it.speed?.let { s -> s * 3.6 }) }
                        if (pts.isEmpty()) null else {
                            val so = DayRec.sofar
                            val segs = ArrayList<DayPaint.Seg>()
                            so?.segments?.forEach { segs += DayPaint.Seg(it.type, it.t0, it.t1) }
                            val tail = so?.end ?: pts.first().t
                            if (DayRec.live?.act == "ski" && pts.last().t > tail) segs += DayPaint.Seg("descent", tail, pts.last().t)
                            DayPaint.build(pts, segs) to floatArrayOf(pts.last().x, pts.last().y)
                        }
                    }.getOrNull()
                }
                if (built != null) { view.showDay(built.first, night, frame = false, withRuns = true); last = built.second; view.follow(built.second[0], built.second[1]) }
            }
            delay(5000)
        }
    }

    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val red = Color(0xFFE5322D)
    val halo = if (night) Color(0xE6060B16) else Color(0xE6FFFFFF)
    val dens = context.resources.displayMetrics.density

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        // your dot (board L03), on the last reading
        var dot by remember { mutableStateOf<Offset?>(null) }
        LaunchedEffect(Unit) { while (true) { withFrameNanos { }; dot = last?.let { q -> view.toScreen(q[0], q[1], 4f) }?.let { Offset(it[0], it[1]) } } }
        Canvas(Modifier.fillMaxSize()) {
            dot?.let { d -> drawCircle(Color(0x553B7BF0), 13f * dens, d); drawCircle(Color.White, 7.5f * dens, d); drawCircle(Color(0xFF2F6FE4), 5.2f * dens, d) }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // the top: recording and the time from the tap; a touch goes back (the recording goes on)
            Row(Modifier.fillMaxWidth().height(44.dp).shadow(10.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(glass)
                .border(1.dp, rule, RoundedCornerShape(22.dp)).clickable(role = Role.Button) { haptics.tick(.3f); onBack() }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recording) Box(Modifier.size(12.dp).clip(CircleShape).background(red))
                Text(stringResource(R.string.app_rec_now, LiveNumbers.elapsed(LiveNumbers.sinceTap(now, began))), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((on, k) in listOf(!single to R.string.app_rec_all, single to R.string.app_rec_one)) {
                    Text(stringResource(k), Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) ink else glass).border(1.dp, rule, RoundedCornerShape(18.dp))
                        .clickable(role = Role.Tab) { if (!on) { val s1 = k == R.string.app_rec_one; picked = s1; onSwitch(s1) } }.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) (if (night) Color(0xFF0E2233) else Color.White) else ink)
                }
            }
        }

        // the speed, large, and where you probably are (or the search, or the weak signal); the hold to finish under them
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val big = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 120.sp, color = ink, shadow = Shadow(halo, Offset.Zero, 24f))
            val small = TextStyle(fontFamily = Plex, fontSize = 14.sp, color = muted, shadow = Shadow(halo, Offset.Zero, 12f))
            val bold = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ink, shadow = Shadow(halo, Offset.Zero, 12f))
            when {
                searching -> {
                    Text(stringResource(R.string.app_rec_search), style = bold)
                    Text(stringResource(R.string.app_rec_search_note), style = small, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                else -> {
                    Text(LiveNumbers.speedText(DayRec.liveSpeed), style = big, maxLines = 1)
                    Text(stringResource(R.string.app_rec_unit), style = small)
                    val live = DayRec.live
                    val where = when (live?.act) {
                        "lift" -> stringResource(R.string.app_rec_on_lift, live?.lift?.let(liftName) ?: "")
                        "ski" -> live?.runs.orEmpty().let { r -> if (r.isEmpty()) stringResource(R.string.app_rec_off) else r.joinToString(" · ") { runName(it) } }
                        "walk", "up" -> stringResource(R.string.app_rec_walking)
                        null -> ""
                        else -> stringResource(R.string.app_rec_standing)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(where, style = bold, maxLines = 1)
                        if (live?.act == "ski" && !live?.runs.isNullOrEmpty()) Text(stringResource(R.string.app_rec_probably), style = small)
                    }
                    if (weak) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE0A21B)))
                        Text(stringResource(R.string.app_rec_weak_title) + " · " + stringResource(R.string.app_rec_weak_hint), style = small)
                    }
                }
            }
            if (recording && !io.github.pini236.skiapp.meet.Reminders.allowed(context))
                Text(stringResource(R.string.app_rec_no_notif), style = small, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            // hold to finish (L03): the fill grows under the finger for a second; let go before and nothing happens
            val hold = remember { Animatable(0f) }
            val holdLabel = stringResource(R.string.app_rec_hold)
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(56.dp).shadow(12.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(glass)
                .border(1.dp, rule, RoundedCornerShape(28.dp))
                .semantics { role = Role.Button; contentDescription = holdLabel }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        haptics.tick(.3f)
                        val job = scope.launch {
                            hold.animateTo(1f, tween(1000, easing = LinearEasing))
                            haptics.tick(.8f); Qa.log("redesign rec hold done"); onStop()
                        }
                        waitForUpOrCancellation()
                        if (hold.value < 1f) { job.cancel(); scope.launch { hold.animateTo(0f, tween(200)) } }
                    }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxWidth(hold.value.coerceAtLeast(0.0001f)).height(56.dp).background(red.copy(alpha = .22f))) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(red))
                    Text(holdLabel, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink)
                }
            }
        }
    }
}

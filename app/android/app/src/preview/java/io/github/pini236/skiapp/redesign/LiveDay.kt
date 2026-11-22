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
import androidx.compose.ui.text.drawText
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
    var painted by remember { mutableStateOf<DayPaint.Day?>(null) }
    // "watch the descent" from the lift (board L04): the descent's film (L09)
    var seeRide by remember { mutableStateOf<io.github.pini236.skiapp.dayrec.DayEngine.Ride?>(null) }
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
                if (built != null) { view.showDay(built.first, night, frame = false, withRuns = true); painted = built.first; last = built.second; view.follow(built.second[0], built.second[1]) }
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
        var pin by remember { mutableStateOf<Offset?>(null) }
        val ride = DayRec.ride
        val onLift = DayRec.live?.act == "lift" && ride != null
        // when this lift began, as this screen saw it (for "about N min to the top"; opened mid-ride, it errs long)
        val liftNow = DayRec.live?.takeIf { it.act == "lift" }?.lift
        val boardedAt = remember(liftNow) { if (liftNow != null) System.currentTimeMillis() else null }
        val rideState = androidx.compose.runtime.rememberUpdatedState(if (onLift) ride else null)
        LaunchedEffect(Unit) { while (true) { withFrameNanos { }
            dot = last?.let { q -> view.toScreen(q[0], q[1], 4f) }?.let { Offset(it[0], it[1]) }
            pin = rideState.value?.maxAt?.let { m -> view.toScreen(Geo.x(m.lon), Geo.y(m.lat), 4f) }?.let { Offset(it[0], it[1]) } } }
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val pinText = ride?.maxKmh?.let { stringResource(R.string.app_rec_pin_label, "%.1f".format(java.util.Locale.ROOT, it)) }
        Canvas(Modifier.fillMaxSize()) {
            // "here you flew" on the descent you just did, while you ride up (board L04)
            val p = pin
            if (onLift && p != null && pinText != null) {
                val ring = Color(0xFF000000.toInt() or (if (night) DayPaint.NIGHT[4] else DayPaint.DAY[4]))
                drawCircle(Color.White, 5f * dens, p); drawCircle(ring, 5f * dens, p, style = androidx.compose.ui.graphics.drawscope.Stroke(2f * dens))
                val tl = measurer.measure(pinText, TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                val w = tl.size.width + 18 * dens; val h = tl.size.height + 8 * dens; val top = p.y - 12 * dens - h
                drawLine(Color(0xFF0E2233), Offset(p.x, p.y - 5 * dens), Offset(p.x, top + h), 1.4f * dens)
                drawRoundRect(Color(0xFF0E2233), Offset(p.x - w / 2, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                drawText(tl, topLeft = Offset(p.x - tl.size.width / 2f, top + 4 * dens))
            }
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
            // all day or one descent (L02, L03): one wide bar, the choice in purple, as on the board
            Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (night) Color(0x33FFFFFF) else Color(0x330E2233))
                .border(1.dp, rule, RoundedCornerShape(24.dp)).padding(3.dp)) {
                for ((on, k) in listOf(!single to R.string.app_rec_all, single to R.string.app_rec_one)) {
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(21.dp)).background(if (on) Color(0xFF7C4FE0) else Color.Transparent)
                        .clickable(role = Role.Tab) { if (!on) { val s1 = k == R.string.app_rec_one; picked = s1; onSwitch(s1) } }, contentAlignment = Alignment.Center) {
                        Text(stringResource(k), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) Color.White else ink)
                    }
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
                onLift && ride != null -> LiftCard(ride, painted, night, ink, muted, glass, rule, liftName, runName, onSee = { haptics.tick(.3f); seeRide = ride }, onStop = onStop,
                    head = DayRec.live?.lift?.let { id ->
                        val name = liftName(id)
                        val dur = scene.runs?.lifts?.firstOrNull { it.id == id }?.dur?.toDoubleOrNull()
                        val eta = liftEta(dur, boardedAt, now)
                        stringResource(R.string.app_rec_on_lift, name) + (eta?.let { " · " + stringResource(R.string.app_rec_lift_eta, it) } ?: "")
                    })
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
                        // the run's number on its colour, as on the board (L03), when the run has one
                        val on = live?.runs?.firstOrNull()?.takeIf { live?.act == "ski" }?.let { k -> scene.runs?.pistes?.firstOrNull { it.key == k } }
                        on?.refs?.firstOrNull()?.let { ref ->
                            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(io.github.pini236.skiapp.game.runColor(on.color))).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                Text(ref, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                        }
                        Text(where, style = bold, maxLines = 1)
                        if (live?.act == "ski" && !live?.runs.isNullOrEmpty()) Text(stringResource(R.string.app_rec_probably), style = small)
                    }
                    // outside the resort (board L13): the recording goes on, without run names
                    val outside = last?.let { q -> !io.github.pini236.skiapp.dayrec.IdleWatch.inside(q[0], q[1]) } == true
                    if (outside) Text(stringResource(R.string.app_rec_outside) + " · " + stringResource(R.string.app_rec_outside_names), style = small)
                    if (weak) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE0A21B)))
                        Text(stringResource(R.string.app_rec_weak_title) + " · " + stringResource(R.string.app_rec_weak_hint), style = small)
                    }
                }
            }
            if (recording && !io.github.pini236.skiapp.meet.Reminders.allowed(context))
                Text(stringResource(R.string.app_rec_no_notif), style = small, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            // hold to finish (L03): the fill grows under the finger for a second; let go before and nothing happens
            if (!onLift) HoldToFinish(scope, haptics, red, ink, glass, rule, onStop)
        }
        val r = seeRide; val pd = painted
        if (r != null && pd != null) {
            val d = io.github.pini236.skiapp.dayrec.ui.DayModel.Descent(r.t0, r.runs, r.maxKmh, false, r.dropM, r.distM,
                r.t1, r.movingS.toDouble(), r.avgKmh, r.maxAt?.t)
            val segs = remember(r) { DayRec.sofar?.segments?.map { io.github.pini236.skiapp.dayrec.ResultJson.seg(it) }.orEmpty() }
            RunFilm(d, segs.firstOrNull { it.optString("type") == "descent" && it.optLong("t0") == r.t0 }, segs, pd, scene.runs, night, haptics) { seeRide = null }
        }
    }
}

/** "Hold to finish" (board L03): the fill grows under the finger for a second; let go before and nothing happens. */
@Composable
private fun HoldToFinish(scope: kotlinx.coroutines.CoroutineScope, haptics: Haptics, red: Color, ink: Color, glass: Color, rule: Color, onStop: () -> Unit) {
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

/**
 * On the lift (board L04): the descent you just did, while there is time to look. A thumbnail of its line in the light's
 * colours, the runs by name, three numbers (top speed, minutes moving, metres down), the wait for the lift, the day so
 * far, "watch the descent" (its film, L09) and "finish".
 */
@Composable
private fun LiftCard(ride: io.github.pini236.skiapp.dayrec.DayEngine.Ride, painted: DayPaint.Day?, night: Boolean, ink: Color, muted: Color,
                     glass: Color, rule: Color, liftName: (String) -> String, runName: (String) -> String, onSee: () -> Unit, onStop: () -> Unit, head: String? = null) {
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    // the ride's stretches out of the painted day, by time
    val lines = remember(painted, ride.t0, ride.t1) {
        painted?.lines.orEmpty().mapNotNull { l ->
            val keep = l.t.indices.filter { l.t[it] in ride.t0..ride.t1 }
            if (keep.size < 2) null else Triple(FloatArray(keep.size) { l.xy[keep[it] * 2] }, FloatArray(keep.size) { l.xy[keep[it] * 2 + 1] }, IntArray(keep.size) { l.band[keep[it]] })
        }
    }
    val so = DayRec.sofar?.stats
    val num = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 36.sp, color = ink)
    val label = TextStyle(fontFamily = Plex, fontSize = 12.sp, color = muted)
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(glass)
        .border(1.dp, rule, RoundedCornerShape(20.dp)).padding(16.dp)) {
        // the lift and about how long to its top (board L04: an estimate by the lift's travel time in the data)
        head?.let { Text(it, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink, modifier = Modifier.padding(bottom = 6.dp)) }
        Text(stringResource(R.string.app_rec_just_ran), style = label, modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(if (night) Color(0xFF0A1322) else Color(0xFFEEF2F5))) {
                if (lines.isEmpty()) return@Canvas
                var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
                for ((xs, ys, _) in lines) { xs.forEach { x0 = minOf(x0, it); x1 = maxOf(x1, it) }; ys.forEach { y0 = minOf(y0, it); y1 = maxOf(y1, it) } }
                val pad = size.minDimension * .14f
                val k = minOf((size.width - pad * 2) / (x1 - x0).coerceAtLeast(1f), (size.height - pad * 2) / (y1 - y0).coerceAtLeast(1f))
                val ox = (size.width - (x1 - x0) * k) / 2; val oy = (size.height - (y1 - y0) * k) / 2
                // y in the map's metres grows north, the screen's down
                fun at(x: Float, y: Float) = Offset(ox + (x - x0) * k, oy + (y1 - y) * k)
                for ((xs, ys, b) in lines) for (i in 0 until xs.size - 1) {
                    val c = Color(0xFF000000.toInt() or ramp[b[i]])
                    drawLine(c, at(xs[i], ys[i]), at(xs[i + 1], ys[i + 1]), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            Column(Modifier.weight(1f)) {
                val names = ride.runs.joinToString(" · ") { runName(it) }.ifEmpty { stringResource(R.string.app_rec_off) }
                Text(names, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ink, maxLines = 2)
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            @Composable fun cell(v: String, l: String) = Column { Text(v, style = num); Text(l, style = label) }
            cell(ride.maxKmh?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "—", stringResource(R.string.app_rec_kmh_max))
            cell("%d:%02d".format(java.util.Locale.ROOT, ride.movingS / 60, ride.movingS % 60), stringResource(R.string.app_run_min))
            cell("%,d".format(java.util.Locale.ROOT, ride.dropM.toInt()), stringResource(R.string.app_rec_m_down))
        }
        // the wait for this lift (board L04): from the end of the descent to boarding, once the analysis has the ride
        val waited = DayRec.sofar?.segments?.firstOrNull { it.type == "lift" && it.t0 >= ride.t1 }?.let { ((it.t0 - ride.t1) / 60_000L).toInt() }
        if (waited != null && waited >= 1) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.app_rec_waited), style = label)
            Text(stringResource(R.string.app_rec_minutes, waited), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ink)
        }
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(rule))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_rec_day_so_far), style = label)
                if (so != null) Text(stringResource(R.string.app_rec_day_line, so.descents, "%,d".format(java.util.Locale.ROOT, so.dropM.toInt()),
                    "%.1f".format(java.util.Locale.ROOT, so.skiDistM / 1000)), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            }
            val see = stringResource(R.string.app_rec_see_run)
            Box(Modifier.padding(end = 8.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, rule, RoundedCornerShape(22.dp))
                .semantics { role = Role.Button; contentDescription = see }
                .clickable { Qa.log("redesign rec lift see"); onSee() }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(see, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            }
            val stop = stringResource(R.string.app_rec_stop)
            Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFE5322D))
                .semantics { role = Role.Button; contentDescription = stop }
                .clickable { Qa.log("redesign rec lift stop"); onStop() }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(stop, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    }
    LaunchedEffect(ride.t1) { Qa.log("redesign rec lift card ${ride.runs.size}") }
}

/** About how many minutes to the lift's top: its travel time ([durMin], from the data) less the time since boarding; null under one. */
internal fun liftEta(durMin: Double?, boardedAt: Long?, now: Long): Int? {
    if (durMin == null || boardedAt == null) return null
    val left = durMin * 60_000 - (now - boardedAt)
    return Math.round(left / 60_000).toInt().takeIf { it >= 1 }
}

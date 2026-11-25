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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.qa.Qa
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
            onSwitch: (single: Boolean) -> Unit, onStop: () -> Unit, onEnded: (java.io.File?) -> Unit,
            /** The lift's state by its name from a fresh report (open, reason), or null: no report (completion part 1, P02). */
            liftState: (String) -> Pair<Boolean, String?>? = { null }, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val time = DayNight.lightTime(now, mode)
    val night = remember(time / 600_000) { Sky.at(time).dark }
    val view = remember { LiveView(context, scene) }
    // the emulator's switch for U04 (its phone has the notifications allowed)
    var qaNoNotif by remember { mutableStateOf(false) }
    LaunchedEffectQa { c -> if (c == "rec:nonotif") qaNoNotif = true else if (c == "rec:notif") qaNoNotif = false }
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
    // the ground's height under each point of the painted track, for the small ridge (P01)
    var ridgeElev by remember { mutableStateOf<FloatArray?>(null) }
    // "see the day" (P01 to P03): the day so far over this screen, the recording going on; or its ridge
    var seeDay by remember { mutableStateOf(0) }
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
                if (built != null) {
                    view.showDay(built.first, night, frame = false, withRuns = true); painted = built.first; last = built.second; view.follow(built.second[0], built.second[1])
                    val tr = built.first.track
                    ridgeElev = withContext(Dispatchers.Default) { FloatArray(tr.t.size) { scene.terrain.elev(tr.x[it], tr.y[it]) } }
                }
            }
            delay(5000)
        }
    }

    // which screen (completion part 1, section 2): the activity now, since when, and the one before it
    val act = DayRec.live?.act
    var actNow by remember { mutableStateOf<String?>(null) }
    var actSince by remember { mutableLongStateOf(0L) }
    var actBefore by remember { mutableStateOf<String?>(null) }
    if (act != actNow) { actBefore = actNow; actNow = act; actSince = now }
    val nearLift = remember(act, last) {
        if (act != "stand") null else last?.let { q -> scene.runs?.lifts?.let { ls -> nearBoarding(ls.map { it.id to it.pts }, scene.terrain::elev, q[0], q[1]) } }
    }
    // part 8, section 2: while recording only a change of state is announced ("On the lift Goodaura", "Descent 4
    // finished"), never every second
    val hostView = androidx.compose.ui.platform.LocalView.current
    val liftNow = DayRec.live?.takeIf { it.act == "lift" }?.lift?.let { id -> scene.runs?.lifts?.firstOrNull { it.id == id }?.name }
    val onLiftSr = liftNow?.let { stringResource(R.string.app_sr_ev_on_lift, it) }
    LaunchedEffect(onLiftSr) { onLiftSr?.let { hostView.announceForAccessibility(it); Qa.log("redesign sr announce lift") } }
    val doneN = DayRec.sofar?.stats?.descents ?: 0
    var doneSeen by remember { mutableStateOf(-1) }
    val doneSr = stringResource(R.string.app_sr_ev_run_done, doneN.toString())
    LaunchedEffect(doneN) {
        if (doneSeen >= 0 && doneN > doneSeen) { hostView.announceForAccessibility(doneSr); Qa.log("redesign sr announce descent $doneN") }
        doneSeen = doneN
    }
    // the emulator holds a stage it cannot reach in its minutes (qa.redesign stage:standing, stage:near, stage:break, stage:off)
    var qaStage by remember { mutableStateOf<LiveStage?>(null) }
    LaunchedEffect(RedesignQa.seq) {
        val c = RedesignQa.cmd
        if (c.startsWith("stage:")) qaStage = when (c.removePrefix("stage:")) {
            "standing" -> LiveStage.STANDING; "near" -> LiveStage.NEAR_LIFT; "break" -> LiveStage.BREAK; else -> null }
    }
    val stage = qaStage ?: liveStage(act, actSince, now, actBefore, nearLift != null)
    // also on each emulator hold, so a hold of the stage already shown is told in the log
    LaunchedEffect(stage, RedesignQa.seq) { Qa.log("redesign rec stage ${stage.name.lowercase()}") }
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
        val rideState = androidx.compose.runtime.rememberUpdatedState(if (onLift) ride else null)
        LaunchedEffect(Unit) { while (true) { withFrameNanos { }
            dot = last?.let { q -> view.toScreen(q[0], q[1], 4f) }?.let { Offset(it[0], it[1]) }
            pin = rideState.value?.maxAt?.let { m -> view.toScreen(Geo.x(m.lon), Geo.y(m.lat), 4f) }?.let { Offset(it[0], it[1]) } } }
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val pinText = ride?.maxKmh?.let { stringResource(R.string.app_film_top_speed, LiveNumbers.maxKmh(it)) }
        Canvas(Modifier.fillMaxSize()) {
            // "here you flew" on the descent you just did, while you ride up (board L04)
            val p = pin
            if (onLift && p != null && pinText != null) {
                val ring = Color(0xFF000000.toInt() or (if (night) DayPaint.NIGHT[4] else DayPaint.DAY[4]))
                drawCircle(Color.White, 5f * dens, p); drawCircle(ring, 5f * dens, p, style = androidx.compose.ui.graphics.drawscope.Stroke(2f * dens))
                val tl = measurer.measure(pinText, TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White))
                val w = tl.size.width + 18 * dens; val h = tl.size.height + 8 * dens; val top = p.y - 12 * dens - h
                drawLine(Color(0xFF0E2233), Offset(p.x, p.y - 5 * dens), Offset(p.x, top + h), 1.4f * dens)
                drawRoundRect(Color(0xFF0E2233), Offset(p.x - w / 2, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                drawText(tl, topLeft = Offset(p.x - tl.size.width / 2f, top + 4 * dens))
            }
            dot?.let { d -> drawCircle(Color(0x553B7BF0), 13f * dens, d); drawCircle(Color.White, 7.5f * dens, d); drawCircle(Color(0xFF2F6FE4), 5.2f * dens, d) }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // the top: recording and the time from the tap; a touch goes back (the recording goes on)
            if (!searching) Row(Modifier.fillMaxWidth().height(44.dp).shadow(10.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(glass)
                .border(1.dp, rule, RoundedCornerShape(22.dp)).clickable(role = Role.Button) { haptics.tick(.3f); onBack() }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recording) Box(Modifier.size(12.dp).clip(CircleShape).background(red))
                // one descent says so in place of "recording" (completion part 1, P04)
                val clock = LiveNumbers.elapsed(SearchRules.sinceFix(now, DayRec.since))
                Text(if (single) stringResource(R.string.app_rec_one) + " · \u2066$clock\u2069" else stringResource(R.string.app_rec_now, clock),
                    fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            }
            // all day or one descent (L02, L03): one wide bar, the choice in purple, as on the board. Quiet in a descent and
            // on the lift (L03, "quiet in a descent": only the speed, the run and finish); shown in the search and when
            // standing. It fades, and keeps its place, so nothing below jumps
            val quiet = toggleQuiet(searching, DayRec.live?.act)
            val shown by androidx.compose.animation.core.animateFloatAsState(if (quiet) 0f else 1f, tween(300), label = "toggle")
            Row(Modifier.fillMaxWidth().height(48.dp).graphicsLayer { alpha = shown }
                .then(if (quiet) Modifier.clearAndSetSemantics { } else Modifier).clip(RoundedCornerShape(24.dp)).background(if (night) Color(0x33FFFFFF) else Color(0x330E2233))
                .border(1.dp, rule, RoundedCornerShape(24.dp)).padding(3.dp)) {
                for ((on, k) in listOf(!single to R.string.app_rec_all, single to R.string.app_rec_one)) {
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(21.dp)).background(if (on) Color(0xFF7C4FE0) else Color.Transparent)
                        .clickable(role = Role.Tab, enabled = !quiet) { if (!on) { val s1 = k == R.string.app_rec_one; picked = s1; onSwitch(s1) } }, contentAlignment = Alignment.Center) {
                        Text(stringResource(k), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) Color.White else ink)
                    }
                }
            }
        }

        val sheetInk = remember(night) { SheetInk(night) }
        val sheet = recording && !searching && !onLift && stage in setOf(LiveStage.STANDING, LiveStage.WALKING, LiveStage.NEAR_LIFT, LiveStage.BREAK)
        val nearL = (nearLift ?: if (qaStage == LiveStage.NEAR_LIFT) last?.let { q -> scene.runs?.lifts?.let { ls ->
            nearBoarding(ls.map { it.id to it.pts }, scene.terrain::elev, q[0], q[1], 1e6f) } } else null)?.let { id -> scene.runs?.lifts?.firstOrNull { it.id == id } }
        // part 6 (U04): recording with the notifications off: a state line in the live screen's sheet, above its buttons,
        // in every stage with a sheet (standing, next to a lift, a break; handoff 2026-11-23-redesign-edges section 3), never
        // in a descent (no sheet there)
        val noNotif = recording && (!io.github.pini236.skiapp.meet.Reminders.allowed(context) || qaNoNotif)
        androidx.compose.runtime.CompositionLocalProvider(LocalNoNotif provides noNotif) {
        androidx.compose.animation.Crossfade(if (sheet) (if (stage == LiveStage.NEAR_LIFT && nearL == null) LiveStage.STANDING else stage) else null,
            Modifier.fillMaxSize(), animationSpec = tween(250), label = "stage") { st ->
            Box(Modifier.fillMaxSize()) {
                val so = DayRec.sofar?.stats
                val see = { seeDay = 1 }; val seeRidge = { seeDay = 2 }
                when (st) {
                    LiveStage.STANDING, LiveStage.WALKING -> Box(Modifier.align(Alignment.BottomCenter)) {
                        StandingSheet(st == LiveStage.WALKING, so, painted, ridgeElev, night, sheetInk, haptics, see, seeRidge, onStop)
                    }
                    LiveStage.NEAR_LIFT -> if (nearL != null) Box(Modifier.align(Alignment.BottomCenter)) {
                        NearLiftSheet(nearL, liftName(nearL.id), (now - actSince) / 1000, liftState(nearL.name), so, sheetInk, haptics,
                            onSee = see, onStop = onStop)
                    }
                    LiveStage.BREAK -> BreakScreen(((now - actSince) / 60_000).let { if (qaStage == LiveStage.BREAK) maxOf(it, 12) else it }, so, painted, ridgeElev, night, sheetInk, haptics, see, seeRidge, onStop)
                    else -> {}
                }
            }
        }
        }
        // finding the location (version 3 of the search): only the pulsing rings, no clock
        if (searching) SearchPulse(night, SearchRules.showHint(now, began), ink, halo, Color(0xE6FFFFFF).let { if (night) Color(0xCC0F1828) else it })
        val fixSr = stringResource(R.string.app_sr_fix_found)
        var wasSearching by remember { mutableStateOf(false) }
        LaunchedEffect(searching) { if (wasSearching && !searching) hostView.announceForAccessibility(fixSr); wasSearching = searching }
        // the speed, large, and where you probably are (or the search, or the weak signal); the hold to finish under them
        if (!sheet) Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val big = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(120f), color = ink, shadow = Shadow(halo, Offset.Zero, 24f))
            // over the mountain the small words take the board's second ink by day (#40556A): the grey of the sheets falls
            // under 4.5:1 on the snow (the designer's point 4, measured on the search's shot: about 3.9:1)
            val small = TextStyle(fontFamily = Gudi, fontSize = 14.sp, color = if (night) muted else Color(0xFF40556A), shadow = Shadow(halo, Offset.Zero, 12f))
            val bold = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ink, shadow = Shadow(halo, Offset.Zero, 12f))
            when {
                onLift && ride != null -> LiftCard(ride, painted, night, ink, muted, glass, rule, liftName, runName, onSee = { haptics.tick(.3f); seeRide = ride }, onStop = onStop, haptics = haptics,
                    head = DayRec.live?.lift?.let { id ->
                        val name = liftName(id)
                        // where you are on the lift's line, and only on a sound reading (not a weak signal)
                        val lift = scene.runs?.lifts?.firstOrNull { it.id == id }
                        val q = last
                        val eta = if (lift == null || q == null || weak || lift.pts.size < 4) null else {
                            val n = lift.pts.size
                            liftEta(lift.dur?.toDoubleOrNull(), lift.pts, scene.terrain.elev(lift.pts[0], lift.pts[1]),
                                scene.terrain.elev(lift.pts[n - 2], lift.pts[n - 1]), q[0], q[1])
                        }
                        stringResource(R.string.app_rec_on_lift, name) + (eta?.let { " · " + stringResource(R.string.app_rec_lift_eta, it) } ?: "")
                    })
                searching -> {}
                else -> {
                    // the plain speed ring (GD1, GD2): the number inside, the day's best as a tick
                    SpeedRing(LiveNumbers.speedText(DayRec.liveSpeed), stringResource(R.string.app_rec_unit), DayRec.liveSpeed?.let { it * 3.6 },
                        DayRec.sofar?.stats?.maxKmh, night, ink, if (night) muted else Color(0xFF40556A), glass, halo)
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
                                Text(ref, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
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
            // hold to finish (L03): the fill grows under the finger for a second; let go before and nothing happens
            if (!onLift) HoldToFinish(scope, haptics, red, ink, glass, rule, onStop)
        }
        // "see the day" (P01 to P03): the day so far, the recording going on; back returns here
        val cur = DayRec.current
        if (seeDay != 0 && cur != null) DayMountain(cur, false, scene, mode, haptics, live = true, openRidge = seeDay == 2) { seeDay = 0 }
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
                    Text(holdLabel, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink)
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
                     glass: Color, rule: Color, liftName: (String) -> String, runName: (String) -> String, onSee: () -> Unit, onStop: () -> Unit, head: String? = null, haptics: Haptics) {
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    // the ride's stretches out of the painted day, by time
    val lines = remember(painted, ride.t0, ride.t1) {
        painted?.lines.orEmpty().mapNotNull { l ->
            val keep = l.t.indices.filter { l.t[it] in ride.t0..ride.t1 }
            if (keep.size < 2) null else Triple(FloatArray(keep.size) { l.xy[keep[it] * 2] }, FloatArray(keep.size) { l.xy[keep[it] * 2 + 1] }, IntArray(keep.size) { l.band[keep[it]] })
        }
    }
    val so = DayRec.sofar?.stats
    val num = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 36.sp, color = ink)
    val label = TextStyle(fontFamily = Gudi, fontSize = 12.sp, color = muted)
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(glass)
        .border(1.dp, rule, RoundedCornerShape(20.dp)).padding(16.dp)) {
        // the lift and about how long to its top (board L04: an estimate by the lift's travel time in the data)
        head?.let { Text(it, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink, modifier = Modifier.padding(bottom = 6.dp)) }
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
                Text(names, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ink, maxLines = 2)
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            @Composable fun cell(v: String, l: String) = Column { Text(v, style = num); Text(l, style = label) }
            cell(ride.maxKmh?.let { LiveNumbers.maxKmh(it) } ?: "—", stringResource(R.string.app_rec_kmh_max))
            cell("%d:%02d".format(java.util.Locale.ROOT, ride.movingS / 60, ride.movingS % 60), stringResource(R.string.app_run_min))
            cell(metres(ride.dropM), stringResource(R.string.app_rec_m_down))
        }
        // the wait for this lift (board L04): from the end of the descent to boarding, once the analysis has the ride
        val waited = DayRec.sofar?.segments?.firstOrNull { it.type == "lift" && it.t0 >= ride.t1 }?.let { ((it.t0 - ride.t1) / 60_000L).toInt() }
        if (waited != null && waited >= 1) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.app_rec_waited), style = label)
            Text(stringResource(R.string.app_rec_minutes, waited), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ink)
        }
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(rule))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_rec_day_so_far), style = label)
                if (so != null) Text(stringResource(R.string.app_rec_day_line, so.descents, metres(so.dropM),
                    "%.1f".format(java.util.Locale.ROOT, so.skiDistM / 1000)), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            }
            val see = stringResource(R.string.app_rec_see_run)
            Box(Modifier.padding(end = 8.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, rule, RoundedCornerShape(22.dp))
                .semantics { role = Role.Button; contentDescription = see }
                .clickable { Qa.log("redesign rec lift see"); onSee() }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(see, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
            }
            // held for a second, as everywhere outside a descent (completion part 1, section 2)
            HoldStop(remember(night) { SheetInk(night) }, haptics, onStop)
        }
    }
    LaunchedEffect(ride.t1) { Qa.log("redesign rec lift card ${ride.runs.size}") }
}

/**
 * About how many minutes to the lift's top (board L04), by where you are on its line and not by when the screen opened:
 * the part of the line still ahead times its travel time ([durMin], from the data). [pts] is the lift's line (x, y pairs),
 * [ele0] and [ele1] the ground's height at its two ends (the top is the higher one, whichever way the line was drawn).
 * Null without a travel time, off the line (over [maxOffM] metres from it), or under a minute: no guess.
 */
internal fun liftEta(durMin: Double?, pts: FloatArray, ele0: Float, ele1: Float, x: Float, y: Float, maxOffM: Float = 60f): Int? {
    if (durMin == null || pts.size < 4) return null
    var total = 0.0; var best = Double.MAX_VALUE; var at = 0.0
    for (i in 0 until pts.size / 2 - 1) {
        val ax = pts[i * 2]; val ay = pts[i * 2 + 1]; val bx = pts[i * 2 + 2]; val by = pts[i * 2 + 3]
        val dx = bx - ax; val dy = by - ay; val len2 = (dx * dx + dy * dy).toDouble(); val len = Math.sqrt(len2)
        val u = if (len2 == 0.0) 0.0 else (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + dx * u; val py = ay + dy * u
        val d = Math.hypot(x - px, y - py)
        if (d < best) { best = d; at = total + u * len }
        total += len
    }
    if (best > maxOffM || total <= 0.0) return null
    val up = (at / total).let { if (ele0 > ele1) 1 - it else it }
    return Math.round((1 - up) * durMin).toInt().takeIf { it >= 1 }
}

/** "Quiet in a descent" (board L03): the all-day / one-descent toggle is hidden while skiing or riding a lift, shown in the search and when standing. */
internal fun toggleQuiet(searching: Boolean, act: String?): Boolean = !searching && (act == "ski" || act == "lift")

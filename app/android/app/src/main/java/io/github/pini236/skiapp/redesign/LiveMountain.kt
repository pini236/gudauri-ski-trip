package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.trip.PassEditor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.pini236.skiapp.ui.Motion
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.pini236.skiapp.qa.Qa

/**
 * Phase B, the first sketch: the mountain as a live model with the free camera of today's map, in the new look (the
 * light of the hour in the shader, one detail texture, the haze of the valleys), with the runs and lifts on it and a
 * tap that lights a run. The hour slider moves the sun along 12 January, as on the painted picture.
 */
@Composable
fun LiveMountain(scene: MapScene, mode: DayNight.Mode, haptics: Haptics, status: io.github.pini236.skiapp.status.LiftStatus?, inSeason: Boolean,
                 forMe: Boolean, onForMe: (Boolean) -> Unit, around: io.github.pini236.skiapp.home.Around?, onPainted: () -> Unit, onLeave: (() -> Unit)?) {
    val here = around?.meet
    val context = LocalContext.current
    // the font chain by this screen's language (redesign part 7; made at start, again here when the language differs)
    remember(context) { GudiFont.init(context) }
    val scope = rememberCoroutineScope()
    val data by produceState<PeakData?>(null) { value = withContext(Dispatchers.IO) { runCatching { PeakData.load(context) }.onFailure { Redesign.fail("data") }.getOrNull() } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var scrubOpen by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    // K07 (the shelf, "where to?"): open, and the run of its middle card
    var shelfOpen by remember { mutableStateOf(false) }
    var shelfMid by remember { mutableStateOf<ShelfRun?>(null) }
    // K06 (from the station): the top station tapped, and the runs that go down from it
    var fromSt by remember { mutableStateOf<io.github.pini236.skiapp.meet.Station?>(null) }
    val stationRuns = remember(scene) { ShelfData.withLifts(scene) }
    // K08 (the lenses): the chips' row open, and the lens on
    var lensRow by remember { mutableStateOf(false) }
    // K17 (the village): open over the mountain
    var villageOpen by remember { mutableStateOf(false) }
    var lens by remember { mutableStateOf<LensKind?>(null) }
    // part 2 (Q01 to Q03): the lifts' sheet, and one lift in it
    var liftsOpen by remember { mutableStateOf(false) }
    var liftSel by remember { mutableStateOf<io.github.pini236.skiapp.data.Lift?>(null) }
    // part 4 (S05): "the mountain now" opens on the lifts from the pill, on the weather from its temperature
    var nowTab by remember { mutableStateOf(NowTab.LIFTS) }
    // part 4 (S07): the run's sheet at its full height
    var runFull by remember { mutableStateOf(false) }
    var riding by remember { mutableStateOf<String?>(null) }
    // part 5 (T10): a place chosen from the list, marked on the mountain in its kind's colour
    var placeMark by remember { mutableStateOf<io.github.pini236.skiapp.places.Place?>(null) }
    LaunchedEffect(liftsOpen, liftSel) { if (!liftsOpen || liftSel == null) placeMark = null }
    LaunchedEffect(chosen) { if (chosen == null) runFull = false }
    androidx.activity.compose.BackHandler(enabled = liftsOpen) { if (liftSel != null) liftSel = null else liftsOpen = false }
    // part 2 (Q04, Q05): the meeting point on the mountain
    val meet = remember { MeetUi() }
    val plan = here?.plan
    val meetDays = remember(here?.trip, now / 3_600_000) { io.github.pini236.skiapp.meet.Meet.days(here?.trip, java.time.Instant.ofEpochMilli(now)) }
    // the emulator's held moment (ms since the choice), null in use
    var held by remember { mutableStateOf<Float?>(null) }
    var meetOpenQa by remember { mutableStateOf<String?>(null) }
    // part 2 (Q07, Q08): "your trip" over the mountain, and a field of it being filled
    var tripOpen by remember { mutableStateOf(false) }
    var tripEdit by remember { mutableStateOf<io.github.pini236.skiapp.trip.PassEdit?>(null) }
    // part 5 (T09): the full trip form, from "edit" on the pass
    var tripForm by remember { mutableStateOf(false) }
    // "no sign-up, kept on the phone" only for one who is in no group (T09)
    var inGroup by remember { mutableStateOf(false) }
    LaunchedEffect(tripForm) { if (tripForm) inGroup = runCatching { around?.people?.api?.myGroups()?.isNotEmpty() }.getOrNull() ?: false }
    // part 2 (Q09): the ski pass over the mountain
    var skipassOpen by remember { mutableStateOf(false) }
    // part 2 (Q10): before the season the mountain opens on the count of days; closed, it comes back on the next opening
    var seasonOn by remember { mutableStateOf(LiveSeasonRules.shown(around)) }
    // part 3 (R01 to R15): the people, the account and the settings over the mountain
    val people = remember { PeopleUi() }
    val pp = around?.people
    var meNow by remember { mutableStateOf(pp?.api?.me()) }
    LaunchedEffect(people.changes, people.open) { meNow = pp?.api?.me() }
    LaunchedEffect(people.open) { if (people.open) seasonOn = false }
    LaunchedEffect(seasonOn) { Qa.log("redesign season ${if (seasonOn) "shown" else "closed"}") }
    LaunchedEffect(chosen, liftsOpen) { if (chosen != null || liftsOpen) seasonOn = false }
    var qaFlags by remember { mutableStateOf<GroupMeets?>(null) }
    val time = scrub?.let { PeakLight.janAt(it) } ?: DayNight.lightTime(now, mode)
    val night = remember(time) { Sky.at(time).dark }
    val view = remember { LiveView(context, scene).also { v -> v.onChoose = { k -> chosen = k; haptics.tick(.5f); Qa.log("redesign live chosen $k") }; v.onFail = { onPainted() } } }
    // S07: the full sheet takes the screen from 236 down, so the chosen run is framed in the part above it; back to the
    // low sheet, framed as at the choice (the review of 24.11). Not on the choice itself (the choice frames it).
    val scrH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    var wasFull by remember { mutableStateOf(false) }
    LaunchedEffect(runFull) {
        val k = chosen
        if (k != null && runFull != wasFull) view.frameRunAbove(k, if (runFull) (236f / scrH.coerceAtLeast(400)).coerceIn(.2f, .5f) else null)
        wasFull = runFull
    }
    // part 4 (S09): the first hint waits for the mountain to be drawn
    // U05 (part 6): the network, for the pill only; back after none, the group's queue goes out at once
    val online = rememberOnline { around?.meet?.sync?.flush() }
    // the emulator (qa.redesign net:off / net:on / net:auto): no network shown without cutting the emulator's own
    var qaNet by remember { mutableStateOf<Boolean?>(null) }
    var mtnDrawn by remember { mutableStateOf(false) }
    var hintAgain by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (!view.drawn) delay(200); mtnDrawn = true }
    // part 4 (S02 to S04, S08): "where am I" and the compass on the mountain
    val where = rememberWhere(scene)
    val (locate, locateGo) = rememberLocate(where, view)
    val whereAction = rememberWhereAction(where)
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
        Qa.log("redesign live open")
        onDispose { life?.removeObserver(obs); view.release(); Qa.log("redesign live closed") }
    }
    // the emulator's pictures (qa/run.sh): an hour of 12 January (the designer's keys), and a view
    LaunchedEffect(RedesignQa.seq) {
        val c = RedesignQa.cmd
        Qa.log("redesign live cmd $c")
        when {
            c == "hour:off" -> { scrub = null; Qa.log("redesign live hour off") }
            c.startsWith("hour:") -> c.removePrefix("hour:").toFloatOrNull()?.let { scrub = it; Qa.log("redesign live hour $it") }
            c.startsWith("look:") -> c.removePrefix("look:").split(",").let { a ->
                val n = a.take(3).mapNotNull { it.toFloatOrNull() }
                if (n.size == 3) view.look(n[0], n[1], n[2], a.getOrNull(3)?.takeIf { it.isNotBlank() })
            }
            // a run chosen the way a tap chooses it (the turn to the Kobi side included), letting it go, and the sign
            c.startsWith("pick:") -> { held = null; view.pickKey(c.removePrefix("pick:")) }
            c == "run:full" -> runFull = true
            c == "ride" -> chosen?.let { riding = it; Qa.log("redesign ride $it") }
            c == "run:low" -> runFull = false
            c == "unpick" -> { runFull = false; riding = null; held = null; chosen = null; view.choose(null); Qa.log("redesign live unpicked") }
            c == "front" -> view.backToFront()
            c.startsWith("turn:") -> c.removePrefix("turn:").toFloatOrNull()?.let { view.qaTurn(it) }
            // part 2 (Q01 to Q03): the lifts' sheet and one lift
            c == "lifts" -> { liftSel = null; nowTab = NowTab.LIFTS; liftsOpen = true }
            c == "weather" -> { liftSel = null; nowTab = NowTab.WEATHER; liftsOpen = true }
            c == "lifts:off" -> { liftSel = null; liftsOpen = false }
            c.startsWith("lift:") -> LiveLifts.board(scene.runs).firstOrNull { it.name == c.removePrefix("lift:") }?.let { liftSel = it; liftsOpen = true }
            // part 2 (Q04, Q05): the meeting point, open, on a preset or a lift's top, cleared, closed
            c == "meet" -> meetOpenQa = "where"
            c.startsWith("meet:") -> meetOpenQa = c.removePrefix("meet:")
            c == "trip" -> { tripEdit = null; tripOpen = true; Qa.log("redesign trip open ${if (around?.trip != null) "pass" else "empty"}") }
            c == "trip:cal" -> { tripOpen = true; tripEdit = io.github.pini236.skiapp.trip.PassEdit.Dates; Qa.log("redesign trip calendar") }
            c == "trip:off" -> { tripEdit = null; tripOpen = false; tripForm = false }
            c == "trip:form" -> { tripOpen = true; tripEdit = null; tripForm = true }
            c == "skipass" -> skipassOpen = true
            c == "shelf" -> { chosen = null; view.choose(null); shelfOpen = true }
            c == "shelf:off" -> shelfOpen = false
            c == "station:off" -> fromSt = null
            c == "lens:off" -> { lens = null; lensRow = false }
            c == "village" -> { chosen = null; view.choose(null); villageOpen = true }
            c == "village:off" -> { view.fromVillage(); villageOpen = false }
            c.startsWith("lens:") -> LensKind.of(c.removePrefix("lens:"))?.let { lensRow = true; lens = it }
            c.startsWith("station:") -> plan?.stations?.firstOrNull { s -> c.removePrefix("station:") in s.topOf }?.let { s -> chosen = null; view.choose(null); fromSt = s; Qa.log("redesign station ${s.id}") }
            c == "season" -> seasonOn = LiveSeasonRules.shown(around)
            c == "season:off" -> seasonOn = false
            c == "skipass:off" -> { skipassOpen = false; Qa.log("redesign skipass closed") }
            // part 3: the people's layers (the emulator's run, qa/run.sh), the first group of mine for "group" and "invite"
            c == "people:off" -> people.close()
            // part 4: the "where am I" switch as a tap, the first-time sheet, and back to the opening view
            c == "where" -> locate()
            c == "where:ask" -> where.asking = true
            c == "where:off" -> { where.asking = false; where.where.off() }
            c == "home" -> view.home()
            c == "hint:reset" -> { Hints.reset(context); hintAgain++ }
            c == "net:off" -> { qaNet = false; Qa.log("redesign edge net off") }
            c == "net:auto" -> qaNet = null
            c.startsWith("people:") && pp != null -> {
                val what = c.removePrefix("people:")
                if (what == "group" || what == "invite") scope.launch {
                    val g = runCatching { pp.api.myGroups() }.getOrNull()?.firstOrNull()?.id
                    if (g != null) { people.go(PLayer.Group(g)); if (what == "invite") people.go(PLayer.Invite(g)) } else Qa.log("redesign people no group")
                } else when (what) {
                    "me" -> people.go(PLayer.Me)
                    "signin" -> people.go(PLayer.SignIn)
                    "gate" -> people.go(PLayer.Gate)
                    "settings" -> people.go(PLayer.Settings)
                    "lang" -> people.go(PLayer.Language)
                    "about" -> people.go(PLayer.About)
                    "new" -> people.go(PLayer.NewGroup)
                    "delete" -> people.go(PLayer.Delete)
                    "licenses" -> people.go(PLayer.Licenses)
                    else -> if (what.startsWith("join:")) people.go(PLayer.Join(what.removePrefix("join:")))
                        else if (what.startsWith("license:")) people.go(PLayer.License(what.removePrefix("license:")))
                        else if (what.startsWith("tab:")) Qa.log("redesign people tab ignored")
                }
            }
            // one moment of the choice held (the designer's 0, 150, 300, 600 and 900 ms, as phase A's frame:)
            c.startsWith("moment:") -> c.removePrefix("moment:").split(',').takeIf { it.size == 2 }?.let { (k, t) ->
                t.trim().toFloatOrNull()?.let { ms -> held = ms; view.moment(k.trim(), ms) }
            }
        }
    }
    LaunchedEffect(time) { view.setTime(time) }
    // a lift's panel open: the lift whole above it (Q03)
    LaunchedEffect(liftSel) { liftSel?.let { l -> LiveLifts.ends(l, scene.terrain::elev)?.let { view.frameLift(it) } } }
    fun meetPick(s: io.github.pini236.skiapp.meet.Station, p: io.github.pini236.skiapp.meet.Preset? = null) {
        val (d, t) = LiveMeetRules.default(meetDays, java.time.Instant.ofEpochMilli(System.currentTimeMillis()))
        meet.sid = s.id; meet.preset = p; meet.day = d; meet.time = p?.let { io.github.pini236.skiapp.meet.Meet.parseTime(it.time) } ?: t
        meet.undo = null; meet.drops++; meet.fromGroup = false; meet.offlineAt = null
        haptics.tick(.5f)
        io.github.pini236.skiapp.telemetry.Telemetry.event("meet_pick", if (p != null) mapOf("kind" to "preset", "preset" to p.event) else mapOf("kind" to "station", "station" to s.name))
        Qa.log("redesign meet set ${s.id} ${meet.day} ${meet.time}")
    }
    fun meetClear(via: String) {
        val was = meet.sid ?: return
        io.github.pini236.skiapp.telemetry.Telemetry.event("meet_clear", mapOf("via" to via))
        meet.undo = was to meet.preset; meet.undoN++; meet.sid = null; meet.preset = null
        Qa.log("redesign meet cleared $via")
    }
    fun meetOpen(on: Boolean) {
        meet.share = false
        if (on) { liftSel = null; liftsOpen = false; chosen = null; view.choose(null); if (!meet.open) view.backToFront() }
        else { meet.sid = null; meet.undo = null }
        meet.open = on
        Qa.log("redesign meet ${if (on) "open" else "closed"}")
    }
    // a site link or a reminder (Redesign.link): on the mountain, once it is drawn and the stations are known
    val link = Redesign.link
    LaunchedEffect(link, plan) {
        val c = link ?: return@LaunchedEffect
        while (!view.drawn) delay(100)
        when {
            c.startsWith("pick:") -> { held = null; runFull = false; val k = c.removePrefix("pick:"); view.pickKey(k)
                scene.runs.pistes.firstOrNull { it.key == k }?.let { p -> io.github.pini236.skiapp.telemetry.Telemetry.event("run_open", mapOf("run" to p.key, "color" to p.color, "via" to "link")) } }
            c == "front" -> view.backToFront()
            c == "meet" -> meetOpenQa = "where"
            c.startsWith("meet:station:") -> meetOpenQa = c.removePrefix("meet:")
            c.startsWith("people:join:") -> people.go(PLayer.Join(c.removePrefix("people:join:")))
            c.startsWith("people:group:") -> people.go(PLayer.Group(c.removePrefix("people:group:")))
            c == "people:gate" -> people.go(PLayer.Gate)
            c == "people:me" -> people.go(PLayer.Me)
            c == "people:signin" -> people.go(PLayer.SignIn)
            c == "people:about" -> { people.go(PLayer.Me); people.go(PLayer.About) }
            c == "people:licenses" -> { people.go(PLayer.Me); people.go(PLayer.Licenses) }
            c.startsWith("people:license:") -> { people.go(PLayer.Me); people.go(PLayer.Licenses); people.go(PLayer.License(c.removePrefix("people:license:"))) }
            c == "people:new" -> people.go(PLayer.NewGroup)
            c.startsWith("people:invite:") -> { val g = c.removePrefix("people:invite:"); people.go(PLayer.Group(g)); people.go(PLayer.Invite(g)) }
            c.startsWith("people:tripfor:") -> c.removePrefix("people:tripfor:").split(':', limit = 2).takeIf { it.size == 2 }?.let { (g, u) ->
                people.go(PLayer.Group(g)); people.go(PLayer.TripFor(g, u)) }
            c == "trip" -> { tripEdit = null; tripOpen = true }
            c == "skipass" -> skipassOpen = true
        }
        Qa.log("redesign link done $c")
        Redesign.link = null
    }
    LaunchedEffect(meetOpenQa, plan) {
        val q = meetOpenQa ?: return@LaunchedEffect
        if (plan == null) return@LaunchedEffect
        meetOpenQa = null
        when {
            q == "off" -> meetOpen(false)
            q == "where" -> { meetOpen(true); meet.sid = null; Qa.log("redesign meet where ${plan.stations.size}") }
            q == "clear" -> meetClear("button")
            // a group's meetup today at Goodaura's top, as the phone's copy would hold it (the emulator has no server)
            q == "flag" || q == "flag:offline" -> LiveMeetRules.topOf(plan, "Goodaura")?.let { st ->
                val (d, t) = LiveMeetRules.default(listOf(java.time.LocalDate.now(io.github.pini236.skiapp.meet.Meet.GUDAURI)), java.time.Instant.now())
                qaFlags = GroupMeets(listOf(io.github.pini236.skiapp.meet.MeetFlags.In("qa", "qa", st.id, io.github.pini236.skiapp.meet.Meet.at(d, t))),
                    if (q.endsWith("offline")) System.currentTimeMillis() - 3_600_000 else null)
                Qa.log("redesign meet flag ${st.id} $t")
            }
            q == "flag:off" -> qaFlags = null
            q == "share" -> if (meet.sid != null) { meet.share = true; Qa.log("redesign meet share open") }
            q == "share:off" -> meet.share = false
            q.startsWith("preset:") -> io.github.pini236.skiapp.meet.Preset.entries.firstOrNull { it.event == q.removePrefix("preset:") }?.let { p -> plan.preset(p)?.let { meetOpen(true); meetPick(it, p) } }
            q.startsWith("lift:") -> LiveMeetRules.topOf(plan, q.removePrefix("lift:"))?.let { meetOpen(true); meetPick(it) }
            q.startsWith("station:") -> plan.stations.firstOrNull { it.id == q.removePrefix("station:") }?.let { meetOpen(true); meetPick(it) } ?: meetOpen(true)
        }
    }
    // the flags of my groups on the mountain (the architect, 24.11.2026): today's meetups, from the phone's copy
    val synced by produceState(GroupMeets(), here?.sync) { here?.sync?.let { s -> groupMeets(s).collect { value = it } } }
    val meets = qaFlags ?: synced
    val flags = remember(meets, plan, now) {
        if (plan == null) emptyList() else io.github.pini236.skiapp.meet.MeetFlags.pick(meets.list, java.time.Instant.ofEpochMilli(now), plan.byId.keys)
    }
    LaunchedEffect(meet.undoN) { if (meet.undo != null) { delay(5000); meet.undo = null } }
    androidx.activity.compose.BackHandler(enabled = skipassOpen) { skipassOpen = false }
    androidx.activity.compose.BackHandler(enabled = tripOpen && !skipassOpen) { if (tripEdit != null && around?.trip != null) tripEdit = null else tripOpen = false }
    androidx.activity.compose.BackHandler(enabled = meet.open) { if (meet.sid != null) meetClear("back") else meetOpen(false) }
    // in the meeting point a tap on the mountain is its own: the nearest station within 44 (Q04), or a clear (Q05)
    var meetPts by remember { mutableStateOf<List<Pair<io.github.pini236.skiapp.meet.Station, Offset>>>(emptyList()) }
    // the lines under the layers (handoff section 14.4): none under the trip and the ski pass; before the season the
    // lifts low and no runs; under the meeting point both quiet
    fun quietNow() = when {
        tripOpen || skipassOpen -> view.setQuiet(0f, 0f)
        people.top is PLayer.Group -> view.setQuiet(.4f)
        people.open -> view.setQuiet(.25f, .25f)
        meet.open -> view.setQuiet(.4f)
        seasonOn -> view.setQuiet(.3f, 0f)
        else -> view.setQuiet(1f)
    }
    LaunchedEffect(seasonOn, meet.open, tripOpen, skipassOpen, people.top) { quietNow() }
    LaunchedEffect(meet.open, plan) {
        quietNow()
        if (!meet.open || plan == null) { view.onTap = null; meetPts = emptyList(); return@LaunchedEffect }
        view.onTap = { x, y ->
            if (meet.sid != null) meetClear("map")
            else LiveMeetRules.nearest(meetPts, x, y, 44 * context.resources.displayMetrics.density)?.let { meetPick(it) }
        }
        while (true) { withFrameNanos { }; meetPts = stationsOnScreen(view, plan) }
    }
    // the "Kobi side" sign shows while the camera looks from within 60 degrees of the Kobi side (the handoff's section 10)
    var kobiSide by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { val k = view.onKobiSide(); if (k != kobiSide) { kobiSide = k; Qa.log("redesign live kobi sign $k") }; delay(250) } }
    LaunchedEffect(data, night) { data?.let { view.setPal(it.pal[if (night) "night" else "day"]) } }

    val bg = if (night) Color(0xD70F1828) else Color(0xDBFFFFFF)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val line = if (night) Color(0x33FFFFFF) else Color(0x220E2233)
    val acc = if (night) Color(0xFFFFD873) else Color(0xFF6B4FD8)
    @Composable fun Pill(modifier: Modifier = Modifier, h: Int = 44, content: @Composable () -> Unit) {
        Box(modifier.height(h.dp).shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp)).background(bg).border(1.dp, line, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { content() }
    }

    val lightNow = PeakLight.clock(PeakLight.localHour(time))
    // the test build's tools; none in the store build
    people.tools = if (io.github.pini236.skiapp.BuildConfig.FLAVOR == "store") null else ({
        // the POC's light scrub and picture are the emulator's only (Pini, 24.11.2026: no POC tools in the app)
        if (io.github.pini236.skiapp.BuildConfig.DEBUG) TestRow(stringResource(R.string.poc_peak_light_now, lightNow)) { people.close(); scrubOpen = true; Qa.log("redesign tools light") }
        // the picture is a fallback, not a second mountain: only the emulator's runs switch to it
        if (io.github.pini236.skiapp.BuildConfig.DEBUG) TestRow(stringResource(R.string.poc_peak_painted)) { people.close(); onPainted() }
        // only the emulator's runs may leave the mountain (the app has no other home, Pini 24.11.2026)
        if (onLeave != null) TestRow(stringResource(R.string.poc_peak_leave)) { people.close(); onLeave() }
        // the field testers' tools (the manager, 24.11.2026: a row here, the test build only; empty in the store build)
        io.github.pini236.skiapp.fieldtools.FieldTools.AboutRow { t, c -> TestRow(t, c) }
        io.github.pini236.skiapp.fieldtools.FieldTools.DiagRow { t, c -> TestRow(t, c) }
    })
    Box(Modifier.fillMaxSize()) {
        // part 8, section 2: the mountain is one item for the screen reader, with the next and previous run and the
        // opening view as its actions; "where to?" in the dock is the full way to choose a run without seeing it
        val srRuns = remember(scene) { scene.runs.pistes.filter { it.named && it.kind == "run" && it.name.isNotBlank() }.sortedBy { it.name } }
        val srMountain = stringResource(R.string.app_sr_mountain, srRuns.size.toString(), LiveLifts.board(scene.runs).size.toString())
        val srNext = stringResource(R.string.app_sr_next_run); val srPrev = stringResource(R.string.app_sr_prev_run)
        val srFront = stringResource(R.string.poc_peak_front_aria)
        fun srStep(d: Int): Boolean {
            if (srRuns.isEmpty()) return false
            val i = srRuns.indexOfFirst { it.key == chosen }
            val n = srRuns[((if (i < 0) (if (d > 0) -1 else 0) else i) + d + srRuns.size) % srRuns.size]
            view.pickKey(n.key); Qa.log("redesign sr run ${n.key}"); return true
        }
        // on the GL view itself: Compose shows an AndroidView's own view to the screen reader in place of the
        // node, so a semantics modifier here never reached it (the review of 24.11, ש2: no "mountain" in ui.xml)
        val srStepNow by androidx.compose.runtime.rememberUpdatedState<(Int) -> Boolean>({ d -> srStep(d) })
        DisposableEffect(view, srMountain, srNext, srPrev, srFront) {
            view.contentDescription = srMountain
            view.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
            val ids = listOf(
                androidx.core.view.ViewCompat.addAccessibilityAction(view, srNext) { _, _ -> srStepNow(1) },
                androidx.core.view.ViewCompat.addAccessibilityAction(view, srPrev) { _, _ -> srStepNow(-1) },
                androidx.core.view.ViewCompat.addAccessibilityAction(view, srFront) { _, _ -> view.backToFront(); true },
            )
            onDispose { ids.forEach { androidx.core.view.ViewCompat.removeAccessibilityAction(view, it) } }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        placeMark?.let { pl ->
            var at by remember(pl) { mutableStateOf<Offset?>(null) }
            LaunchedEffect(pl) { while (true) { withFrameNanos { }; at = view.toScreen(io.github.pini236.skiapp.data.Geo.x(pl.lon), io.github.pini236.skiapp.data.Geo.y(pl.lat))?.let { Offset(it[0], it[1]) } } }
            at?.let { q -> Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White, 9.dp.toPx(), q); drawCircle(io.github.pini236.skiapp.places.kindColor(pl.kind), 6.5.dp.toPx(), q)
            } }
        }
        // my dot and its circle (S03), under everything drawn over the mountain
        WhereDot(where, view)
        if (!meet.open && !tripOpen && !skipassOpen && !people.open) Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // the test build's tools (the light's clock, the painting, out to the app) are rows in "me" (the review of
            // 24.11): the top holds the pill and "me" only, as on the board (S01). The light's slider opens here from there.
            AnimatedVisibility(scrubOpen, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                Pill(Modifier.fillMaxWidth(), h = 52) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(value = scrub ?: PeakLight.localHour(time).coerceIn(7f, 23.5f), onValueChange = { scrub = it }, valueRange = 7f..23.5f,
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                            colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc, inactiveTrackColor = line))
                        // closed by its own mark (the clock's pill that closed it is now a row in "me"); the light back to now
                        val closeA = stringResource(R.string.common_close)
                        Box(Modifier.size(48.dp).semantics { contentDescription = closeA; role = androidx.compose.ui.semantics.Role.Button }
                            .clickable { haptics.tick(.3f); scrubOpen = false; scrub = null }, contentAlignment = Alignment.Center) {
                            Text("✕", fontFamily = Gudi, fontSize = 16.sp, color = ink)
                        }
                    }
                }
            }
            // the pill of the lifts (Q01, Q02): the report's summary, or no recent information, or none yet before the season
            val (pillText0, pillLive) = LiftsPillText(status, inSeason)
            // U05: no network known in the season and no live report: "no recent information · no signal"
            val pillText1 = if (Online.pillOffline(qaNet ?: online, pillLive, inSeason)) stringResource(R.string.app_now_offline) else pillText0
            // the count closed: it shrinks to a line in the pill ("29 days to the opening")
            val ov0 = around?.opening
            val pillText = if (!seasonOn && !pillLive && ov0 != null && ov0.kind == io.github.pini236.skiapp.season.Season.Kind.COUNTDOWN)
                pluralStringResource(R.plurals.app_season_pill, ov0.days, ov0.days.toString()) else pillText1
            // part 8, section 2: "Mountain status: 11 of 14 lifts open, -7°. Button." (the dot is not read)
            val tpSr = io.github.pini236.skiapp.weather.LocalWeather.current.let { w -> w.usable?.now("goodaura", w.local)?.temp }
            val pillSr = if (pillLive && status?.fresh == true) stringResource(R.string.app_sr_pill, status.open.toString(), status.names.size.toString(),
                tpSr?.let { io.github.pini236.skiapp.weather.deg(it) } ?: "").replace(", .", ".") else null
            // on a tablet the pill is 360 wide at the start side, by "me" (part 8)
            Row(if (isTabletUi()) Modifier.width((TabletUi.PILL + 52).dp) else Modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(Modifier.weight(1f).semantics { if (pillSr != null) contentDescription = pillSr }.clickable {
                haptics.tick(.3f); liftSel = null; nowTab = NowTab.LIFTS; liftsOpen = !liftsOpen
                if (liftsOpen) io.github.pini236.skiapp.telemetry.Telemetry.event("status_view", if (status?.fresh == true) mapOf("state" to "fresh", "open" to status.open, "total" to status.names.size) else mapOf("state" to if (status?.report == null) "none" else "stale"))
                Qa.log("redesign lifts ${if (liftsOpen) "open" else "closed"} ${if (pillLive) "live" else "none"}")
            }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    val kk = remember(night) { SheetInk(night) }
                    StateDot(if (pillLive) true else null, kk)
                    Text(pillText, Modifier.weight(1f, fill = false), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = ink, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    // the temperature at upper Goodaura (S01): from it to the pill's end opens the weather tab
                    val wx = io.github.pini236.skiapp.weather.LocalWeather.current
                    val tp = wx.usable?.now("goodaura", wx.local)?.temp
                    if (tp != null) Box(Modifier.heightIn(min = 44.dp).semantics { role = androidx.compose.ui.semantics.Role.Button }
                        .clickable { haptics.tick(.3f); liftSel = null; nowTab = NowTab.WEATHER; liftsOpen = true; Qa.log("redesign lifts open weather") },
                        contentAlignment = Alignment.Center) {
                        Text("· " + io.github.pini236.skiapp.weather.deg(tp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = ink, maxLines = 1)
                    }
                }
            }
            // "me" at the pill's end (section 1): the account, the settings, the language and about
            if (pp != null) MeButton(meNow, night) { haptics.tick(.3f); liftsOpen = false; liftSel = null; people.go(PLayer.Me) }
            }
            AnimatedVisibility(kobiSide && riding == null, enter = fadeIn(), exit = fadeOut()) {
                val aria = stringResource(R.string.poc_peak_front_aria)
                Pill(Modifier.semantics { contentDescription = aria }.clickable { haptics.tick(.3f); view.backToFront() }) {
                    Text(stringResource(R.string.map_kobi_side), Modifier.padding(horizontal = 16.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
            }
        }
        // the chosen run (K10, as phase A): its sign on the top of the run once the light is down it, and the sheet
        val g = remember(night) { Glass(night) }
        val measurer = rememberTextMeasurer()
        // the km words of the chosen run's beads, measured once each (not in every frame)
        val kmWords = remember(measurer) { HashMap<String, Pair<androidx.compose.ui.text.TextLayoutResult, androidx.compose.ui.text.TextLayoutResult>>() }
        val pd = data
        val run = chosen?.let { k -> pd?.runs?.get(k) }
        var signAt by remember { mutableStateOf<Offset?>(null) }
        val signA = remember { Animatable(0f) }
        LaunchedEffect(chosen, held) {
            signA.snapTo(0f)
            val k = chosen
            if (k == null) { signAt = null; return@LaunchedEffect }
            val h = held
            if (h != null) signA.snapTo(((h - 600f) / 250f).coerceIn(0f, 1f)) else launch { delay(600); signA.animateTo(1f, tween(250)) }
            while (true) { withFrameNanos { }; signAt = view.screenTop(k)?.let { Offset(it[0], it[1]) } }
        }
        val ring = remember { Animatable(1f) }
        var touch by remember { mutableStateOf<Offset?>(null) }
        LaunchedEffect(Unit) { view.onTapAt = { x, y -> touch = Offset(x, y); scope.launch { ring.snapTo(0f); ring.animateTo(1f, tween(if (Motion.reduced(context)) 1 else 300)) } } }
        LaunchedEffect(Unit) { view.onMoment = { x, y, ms -> touch = Offset(x, y); scope.launch { ring.snapTo((ms / 300f).coerceIn(0f, 1f)) } } }
        // the chosen run's K11 marks over the model, every frame: by day arrows down it, a bead each 500 m with its km and
        // the start and the end (once its light is down it, 600 ms, then 200 ms); at night the beads of light
        var mk by remember { mutableStateOf<LiveView.Marks?>(null) }
        var mkMs by remember { mutableStateOf(0f) }
        LaunchedEffect(chosen) { if (chosen == null) { mk = null; return@LaunchedEffect }; while (true) { withFrameNanos { }; mk = view.marks(); mkMs = view.chosenMs() } }
        // the closed lifts' tags on the mountain (Q01): a red dot, the lift's name and "closed", 16 above its top station
        val boardLifts = remember(scene) { LiveLifts.board(scene.runs) }
        val closed = if (status?.fresh == true) boardLifts.filter { status.isOpen(it.name) == false } else emptyList()
        var tags by remember { mutableStateOf<List<Pair<io.github.pini236.skiapp.data.Lift, Offset>>>(emptyList()) }
        LaunchedEffect(closed.map { it.name }) {
            if (closed.isEmpty()) { tags = emptyList(); return@LaunchedEffect }
            val tops = closed.mapNotNull { l -> LiveLifts.ends(l, scene.terrain::elev)?.let { e -> l to e } }
            while (true) { withFrameNanos { }; tags = tops.mapNotNull { (l, e) -> view.toScreen(e[2], e[3], 6f)?.let { p -> l to Offset(p[0], p[1]) } } }
        }
        // phase A's names (only with no run chosen) and the lifts' stations, over the model, every frame the camera moves
        var ov by remember { mutableStateOf<LiveView.Overlay?>(null) }
        LaunchedEffect(pd) { val labels = pd?.hero?.labels ?: return@LaunchedEffect; while (true) { withFrameNanos { }; ov = view.overlay(labels) } }
        val unitM = stringResource(R.string.common_unit_m, "\u0000")
        val nf = remember { java.text.NumberFormat.getIntegerInstance() }
        // nothing of the mountain's names and marks over the pill (the review of 24.11: "New Gudauri" on its words)
        val namesGuard = with(androidx.compose.ui.platform.LocalDensity.current) { (TOP_GUARD_DP + if (lensRow) 56 else 0).dp.toPx() }
        Canvas(Modifier.fillMaxSize().drawWithContent { clipRect(top = namesGuard) { this@drawWithContent.drawContent() } }) {
            val o = ov; val pal = pd?.pal?.get(if (night) "night" else "day")
            if (o != null && pal != null) {
                // stations: 6.8 by 5, corner 1.4, the station colour with the cable's outline (phase A); dimmer under a chosen run
                val sa = if (tripOpen || skipassOpen || people.open) 0f else if (chosen != null || seasonOn) .4f else 1f
                val w = 6.8f * density; val h = 5f * density; val cr = androidx.compose.ui.geometry.CornerRadius(1.4f * density)
                for (i in 0 until o.stations.size / 2) {
                    val tl = Offset(o.stations[i * 2] - w / 2, o.stations[i * 2 + 1] - h * .84f)
                    drawRoundRect(pal.st, tl, androidx.compose.ui.geometry.Size(w, h), cr, alpha = sa)
                    drawRoundRect(pal.wire, tl, androidx.compose.ui.geometry.Size(w, h), cr, alpha = sa, style = Stroke(1.2f * density))
                }
            }
            // phase A's names belong to its front view: not from the Kobi side, and none cut by the edge of the screen
            if (o != null && chosen == null && !kobiSide && !tripOpen && !skipassOpen && !seasonOn && !people.open) {
                val ink = if (night) Color(0xFFEAF1F8) else Color(0xFF0E2233)
                val halo = if (night) Color(0xF2060B16) else Color(0xF2FFFFFF)
                val name = androidx.compose.ui.text.TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = ink)
                val sub = androidx.compose.ui.text.TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = ink)
                for ((l, x, y) in o.names) {
                    val t1 = measurer.measure(l.t, name)
                    val t2 = l.sub?.let { measurer.measure(unitM.replace("\u0000", nf.format(it)), sub) }
                    val half = maxOf(t1.size.width, t2?.size?.width ?: 0) / 2f + 8 * density
                    if (x - half < 0f || x + half > size.width) continue
                    // a name that falls under a closed lift's tag is not drawn (the designer's note on part 2: the tag
                    // over Bidara); the tag is about 130 wide from 40 before its station, 22 high, 16 above it
                    val hh = (t1.size.height + (t2?.size?.height ?: 0)) / 2f
                    if (tags.any { (_, p) -> x + half > p.x - 40 * density && x - half < p.x + 90 * density && y + hh > p.y - 38 * density && y - hh < p.y - 16 * density }) continue
                    var yy = y - (t1.size.height + (t2?.size?.height ?: 0)) / 2f
                    for (tx in listOfNotNull(t1, t2)) {
                        val tl = Offset(x - tx.size.width / 2f, yy)
                        for (r in floatArrayOf(7f, 3f, 3f)) drawText(tx, topLeft = tl, shadow = androidx.compose.ui.graphics.Shadow(halo, Offset.Zero, r * density))
                        drawText(tx, topLeft = tl)
                        yy += tx.size.height
                    }
                }
            }
            val m0 = mk
            // in the film only its dot and the line (GUDI-ANDROID-B: the beads froze the main thread there)
            if (m0 != null && m0.key == chosen && riding == null) {
                chosenMarks(m0, ((mkMs - 600f) / 200f).coerceIn(0f, 1f), night, measurer, kmWords)
                if (mkMs < 600f) runningHead(m0, mkMs / 600f)
            }
            val r0 = run; val q = signAt
            if (r0 != null && q != null && signA.value > 0f) sign(r0, q, signA.value, measurer)
            val tp = touch
            if (tp != null && ring.value < 1f) {
                val r = ring.value; val sc = 1f + .35f * r; val dp = density
                drawCircle(Color.White, 14 * dp * sc, tp, alpha = .55f * (1 - r))
                drawCircle(Color.White, 26 * dp * sc, tp, alpha = .9f * (1 - r), style = Stroke(2 * dp))
            }
        }
        val guardPx = with(androidx.compose.ui.platform.LocalDensity.current) { (TOP_GUARD_DP + 38 + if (lensRow) 56 else 0).dp.toPx() }
        if (chosen == null && !meet.open && !tripOpen && !skipassOpen && !seasonOn) for ((l, p) in tags) {
            // never over the pill or the lenses' row (the review of 24.11)
            if (p.y < guardPx) continue
            val sk = remember(night) { SheetInk(night) }
            val closedSr = stringResource(R.string.app_sr_lift_closed, l.name)
            Row(Modifier.offset { androidx.compose.ui.unit.IntOffset((p.x - 40.dp.toPx()).toInt(), (p.y - 16.dp.toPx() - 22.dp.toPx()).toInt()) }
                .heightIn(min = 44.dp).clickable { haptics.tick(.3f); liftSel = l; liftsOpen = true; Qa.log("redesign lift tag ${l.name}") }
                // "Goodaura, closed." (the red dot is not read)
                .clearAndSetSemantics { contentDescription = closedSr; onClick { liftSel = l; liftsOpen = true; true } },
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(bg).border(1.dp, line, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(sk.red))
                    Text(stringResource(R.string.app_lift_closed_tag, l.name), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = ink, maxLines = 1)
                }
            }
        }
        if (liftsOpen && liftSel == null && nowTab == NowTab.WEATHER) TempTags(view, io.github.pini236.skiapp.weather.LocalWeather.current, night)
        AnimatedVisibility(liftsOpen, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(150)), exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))) {
            val sk = remember(night) { SheetInk(night) }
            val sel = liftSel
            // the sheet follows the finger: down from a lift is the list, down from the list closes
            Box(Modifier.pullSheet(onDown = { if (liftSel != null) liftSel = null else liftsOpen = false }, closes = sel == null)) {
            if (sel != null) {
                LaunchedEffect(sel) { io.github.pini236.skiapp.telemetry.Telemetry.event("lift_open", mapOf("lift" to sel.name)); Qa.log("redesign lift panel ${sel.name}") }
                LiftPanel(sel, status, scene, sk, haptics, onRun = { key -> liftSel = null; liftsOpen = false; view.pickKey(key) },
                    onMeet = plan?.let { pl -> LiveMeetRules.topOf(pl, sel.name)?.let { st -> { meetOpen(true); meetPick(st) } } },
                    onRuns = { liftSel = null; liftsOpen = false },
                    onPlace = { pl -> placeMark = pl; val x = io.github.pini236.skiapp.data.Geo.x(pl.lon); val y = io.github.pini236.skiapp.data.Geo.y(pl.lat)
                        view.center(x, y, 1e6f); Qa.log("redesign places mark ${pl.id}") })
            } else LiftsSheet(boardLifts, status, scene, forMe, sk, haptics, onForMe = { v ->
                onForMe(v); io.github.pini236.skiapp.telemetry.Telemetry.event("status_only_open", mapOf("on" to v)); Qa.log("redesign lifts for me $v")
            }, onLift = { l -> liftSel = l },
                opening = around?.opening?.takeIf { status?.fresh != true && it.kind == io.github.pini236.skiapp.season.Season.Kind.COUNTDOWN }?.let { o ->
                    stringResource(R.string.app_season_planned, io.github.pini236.skiapp.season.shortDate(o.open)) to pluralStringResource(R.plurals.app_season_pill, o.days, o.days.toString())
                }, onOpening = { liftsOpen = false; seasonOn = true; Qa.log("redesign lifts opening row") },
                head = { NowHead(nowTab, sk) { nowTab = it } },
                other = if (nowTab == NowTab.WEATHER) { { WeatherTab(boardLifts, status, around?.trip, sk, night) { l -> liftSel = l } } } else null)
            }
        }
        AnimatedVisibility(run != null && pd != null && (held ?: 900f) >= 900f && !liftsOpen && riding == null, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(150)), exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))) {
            var last by remember { mutableStateOf(run) }
            if (run != null) last = run
            val d0 = pd
            val kk = chosen
            last?.let { r -> if (d0 != null && kk != null) RunSheets(kk, r, d0, g, scene, around?.videos.orEmpty(), night, haptics, runFull, onFull = { runFull = it },
                onPick = { nk -> view.pickKey(nk); Qa.log("redesign run swipe $nk") }, onClose = { runFull = false; chosen = null; view.choose(null) },
                onRide = { riding = kk; Qa.log("redesign ride $kk") }) }
        }
        // K12: the film down the chosen run, in place of its sheet; closing it goes back to the low sheet and the run's frame
        riding?.let { rk ->
            val r = run
            val hapticsOn = remember { io.github.pini236.skiapp.fx.FxPrefs.haptics(view.context) &&
                ((view.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.hasVibrator() == true) }
            val title = scene.runs.pistes.firstOrNull { it.key == rk }?.title ?: rk
            RideFilm(rk, title, {
                // a run with no number (Kikilo 1, Kobi): a small square of its colour, not an empty plate
                if (r != null && r.ref.isBlank()) Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(plate(r.color)))
                else if (r != null) Box(Modifier.height(30.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(7.dp)).background(plate(r.color)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Text(r.ref, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White,
                        style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
                }
            }, scene, view, night, haptics, hapticsOn) {
                riding = null; runFull = false; view.frameRunAbove(rk, null); Qa.log("redesign ride closed")
            }
        }
        // K07: the mountain rises 120 points over the shelf, with a veil, and lights the middle card's run (section 2)
        val scrHdp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.toFloat().coerceAtLeast(400f)
        val shiftPx120 = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
        val shelfShift by androidx.compose.animation.core.animateFloatAsState(if (shelfOpen) with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() } else 0f, tween(300), label = "shelf")
        LaunchedEffect(shelfShift) { view.setShift(shelfShift) }
        val veilA by androidx.compose.animation.core.animateFloatAsState(if (shelfOpen) 1f else 0f, tween(250), label = "veil")
        if (veilA > 0f) Box(Modifier.fillMaxSize().background((if (night) Color(0x5C020612) else Color(0x42283E6E)).copy(alpha = (if (night) .36f else .26f) * veilA)))
        LaunchedEffect(shelfOpen, shelfMid) {
            val r = shelfMid
            view.setFocus(if (shelfOpen && r != null) LiveRenderer.Focus(setOf(r.key), 3.6f, 6f, setOfNotNull(r.lift)) else null)
        }
        // K06: the station's runs lit, the rest ghosts, its lifts on, a ring round it, and the low sheet
        val stShift by androidx.compose.animation.core.animateFloatAsState(if (fromSt != null) with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() } else 0f, tween(300), label = "station")
        LaunchedEffect(stShift, shelfShift) { if (!shelfOpen) view.setShift(stShift) }
        val st = fromSt
        val stRuns = remember(st) { st?.let { FromStation.runs(it.topOf, stationRuns) }.orEmpty() }
        LaunchedEffect(st) {
            if (st != null) {
                view.setFocus(LiveRenderer.Focus(stRuns.map { it.key }.toSet(), 3.2f, 5.4f, st.topOf.toSet()))
                // its runs between the pill and the sheet, the mountain raised 120 (the review of 24.11: they sat under the pill)
                view.showKeys(stRuns.map { it.key }.toSet(), 100f / scrHdp, 1f - 330f / scrHdp, shiftPx120, "redesign station framed ${st.id}")
            }
            else if (!shelfOpen) view.setFocus(null)
        }
        if (st != null) {
            Box(Modifier.fillMaxSize().background((if (night) Color(0xFF020612) else Color(0xFF283E6E)).copy(alpha = if (night) .40f else .30f)))
            StationRing(view, st, night)
            for (r in stRuns) if (r.kind != "beginner-area") RunTag(view, scene, r.key, r.title, r.color, night, side = 0f) { haptics.tick(.4f); fromSt = null; view.pickKey(r.key) }
            Box(Modifier.align(Alignment.BottomCenter)) {
                FromStationSheet(st, stRuns, scene, night, haptics, onPick = { key -> fromSt = null; Qa.log("redesign station pick $key"); view.pickKey(key) },
                    onClose = { fromSt = null; Qa.log("redesign station closed") })
            }
        }
        if (shelfOpen) {
            val shelfRuns = remember(scene) { ShelfData.runs(scene) }
            Shelf(scene, view, shelfRuns, night, haptics,
                open = { flat -> Shelf.start(flat, ShelfMemory.last, null, null) },
                closed = { key -> scene.runs.pistes.firstOrNull { it.key == key }?.let { status?.runOpen(it) == false } ?: false },
                // between the pill (100 down) and the shelf (270 up), the mountain raised 120 (K07)
                onCenter = { shelfMid = it; view.showRun(it.key, 100f / scrHdp, 1f - 270f / scrHdp, shiftPx120) },
                onPick = { key -> ShelfMemory.last = key; shelfOpen = false; Qa.log("redesign shelf pick $key"); view.pickKey(key) },
                onClose = { shelfOpen = false; Qa.log("redesign shelf closed") })
            if (veilA > .5f) shelfMid?.let { r -> RunTag(view, scene, r.key, r.title, r.color, night) }
        }
        AnimatedVisibility(chosen == null && lens == null && !villageOpen && !shelfOpen && fromSt == null && !liftsOpen && !meet.open && !tripOpen && !skipassOpen && !seasonOn && !people.open, Modifier.fillMaxSize(),
            enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            Box(Modifier.fillMaxSize()) {
                Dock(night, haptics, onVillage = { villageOpen = true; Qa.log("redesign dock village") }, onFind = { shelfOpen = true; Qa.log("redesign dock find") },
                    onRecord = around?.onRecord?.let { r -> { Qa.log("redesign dock record"); r() } })
            }
        }
        // K17: the village over the mountain, until "back to the mountain"
        val pd0 = data
        // today's meeting (the first of my groups' flags): its time and place in the sheet, its pin on a station in the picture
        val vMeet = flags.firstOrNull()?.let { f -> plan?.byId?.get(f.station)?.let { st -> f to st } }
        fun hmOf(f: io.github.pini236.skiapp.meet.MeetFlags.In) = f.at.atOffset(io.github.pini236.skiapp.meet.Meet.GUDAURI).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
        if (villageOpen && pd0 != null) Village(scene, pd0, mode, time, night, around, haptics, meetNow = vMeet?.let { (f, st) -> hmOf(f) to st.name },
            onLift = { l -> villageOpen = false; liftSel = l; liftsOpen = true },
            onTrip = { tripEdit = null; tripOpen = true },
            onSkipass = { skipassOpen = true },
            onFood = LiveLifts.board(scene.runs).firstOrNull { it.name == "Goodaura" }?.let { g -> { villageOpen = false; liftSel = g; liftsOpen = true } },
            // the row says where it goes (Pini, 24.11.2026): no group, to join or open one; one, to it; more, to my groups
            // the village gives way to the people's screens (nothing of it shows through them)
            onPeople = { g -> if (pp != null) { view.fromVillage(); villageOpen = false }; if (pp != null) when {
                g.isNullOrEmpty() -> people.go(PLayer.Gate)
                g.size == 1 -> people.go(PLayer.Group(g[0].id))
                else -> people.go(PLayer.Me)
            } },
            onBack = { view.fromVillage(); villageOpen = false; Qa.log("redesign village closed") },
            fly = { p -> view.toVillage(p) },
            meetLifts = vMeet?.second?.bottomOf.orEmpty(),
            onMeet = vMeet?.let { (f, st) -> {
                villageOpen = false; meetOpen(true)
                val z = f.at.atOffset(io.github.pini236.skiapp.meet.Meet.GUDAURI)
                meet.sid = st.id; meet.day = z.toLocalDate(); meet.time = z.toLocalTime(); meet.preset = null
                meet.fromGroup = true; meet.offlineAt = meets.offlineAt
                Qa.log("redesign meet from village ${st.id}")
            } })
        // the flags of my groups (Q05 on the plain mountain): the same flag with its time; a tap opens it
        if (!meet.open && chosen == null && !liftsOpen && plan != null && flags.isNotEmpty()) {
            val sk = remember(night) { SheetInk(night) }
            var at by remember { mutableStateOf<List<Pair<io.github.pini236.skiapp.meet.MeetFlags.In, Offset>>>(emptyList()) }
            LaunchedEffect(flags) { while (true) { withFrameNanos { }; at = flags.mapNotNull { f -> plan.byId[f.station]?.let { s -> view.toScreen(s.x, s.y, 4f)?.let { f to Offset(it[0], it[1]) } } } } }
            fun hm(f: io.github.pini236.skiapp.meet.MeetFlags.In) = f.at.atOffset(io.github.pini236.skiapp.meet.Meet.GUDAURI).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
            Canvas(Modifier.fillMaxSize()) { for ((f, p) in at) meetFlag(p, hm(f), 1f, sk.acc, Color.White, measurer) }
            for ((f, p) in at) {
                val s = plan.byId[f.station] ?: continue
                MeetFlagTarget(p, stringResource(R.string.app_meet_flag_a11y, hm(f), s.name)) {
                    haptics.tick(.3f); meetOpen(true)
                    val z = f.at.atOffset(io.github.pini236.skiapp.meet.Meet.GUDAURI)
                    meet.sid = s.id; meet.day = z.toLocalDate(); meet.time = z.toLocalTime(); meet.preset = null
                    meet.fromGroup = true; meet.offlineAt = meets.offlineAt
                    Qa.log("redesign meet from flag ${s.id}")
                }
            }
        }
        // part 2 (Q04, Q05): the meeting point over the mountain: the pins, the flag, the pill and the sheet
        if (meet.open && plan != null) {
            val sk = remember(night) { SheetInk(night) }
            val st = meet.sid?.let { plan.byId[it] }
            val d0 = meet.day; val t0 = meet.time
            val drop = rememberDrop(if (st != null) meet.drops else null, Motion.reduced(context))
            val hhmm = t0?.let { "%02d:%02d".format(it.hour, it.minute) } ?: ""
            val gap = 13 * context.resources.displayMetrics.density
            val clusters = LiveMeetRules.clusters(meetPts, gap)
            Canvas(Modifier.fillMaxSize()) {
                for (c in clusters) {
                    val (s, p) = c.first()
                    if (c.any { it.first.id == st?.id }) continue
                    meetPin(p, sk.acc, if (st != null) .55f else 1f)
                }
                val at = st?.let { s -> meetPts.firstOrNull { it.first.id == s.id }?.second }
                if (at != null) meetFlag(at, hhmm, drop.value, sk.acc, Color.White, measurer)
            }
            if (st == null) MeetPinTargets(clusters, null) { s -> meetPick(s) }
            else meetPts.firstOrNull { it.first.id == st.id }?.second?.let { at ->
                MeetFlagTarget(at, stringResource(R.string.app_meet_flag_a11y, hhmm, st.name)) { meetClear("pin") }
            }
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val nowI = java.time.Instant.ofEpochMilli(now)
                val text = if (st != null && d0 != null && t0 != null) countdownText(io.github.pini236.skiapp.meet.Meet.left(io.github.pini236.skiapp.meet.Meet.at(d0, t0), nowI))
                    else stringResource(R.string.app_meet_where)
                MeetPill(text, sk, bg, line, stringResource(if (st != null) R.string.meet_clear else R.string.common_close)) {
                    haptics.tick(.3f); if (st != null) meetClear("button") else meetOpen(false)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).pullSheet(onDown = { if (st != null) meetClear("pull") else meetOpen(false) }, closes = st == null),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                meet.undo?.let { u ->
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        MeetUndo {
                            meet.undo = null; io.github.pini236.skiapp.telemetry.Telemetry.event("meet_undo")
                            plan.byId[u.first]?.let { s -> meetPick(s, u.second) }
                        }
                    }
                }
                if (st != null && d0 != null && t0 != null && here != null)
                    MeetSetSheet(here, st, d0, t0, meetDays, sk, haptics, scene, meet.offlineAt, meet.fromGroup, onImage = { meet.share = true; Qa.log("redesign meet share open") }, onDay = { meet.day = it; meet.preset = null; Qa.log("redesign meet day $it") },
                        onTime = { meet.time = it; meet.preset = null; Qa.log("redesign meet time $it") })
                else if (st == null) MeetWhereSheet(plan, sk, haptics) { p, s -> meetPick(s, p) }
            }
            if (meet.share && st != null && d0 != null && t0 != null)
                MeetShareScreen(st, d0, t0, night, haptics, scene) { meet.share = false }
        }
        if (seasonOn && around != null && !meet.open && !tripOpen && !skipassOpen && chosen == null && !liftsOpen)
            LiveSeason(around, night, haptics, onTrip = { tripEdit = null; tripOpen = true }, onSkipass = { skipassOpen = true }, onClose = { seasonOn = false })
        // part 2 (Q07, Q08): "your trip" over the mountain, the veil under the paper
        if (tripOpen && around != null) {
            val sk = remember(night) { SheetInk(night) }
            val t = around.trip
            Box(Modifier.fillMaxSize().background(sk.veil).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {})
            val nowL = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneId.systemDefault())
            if (t != null) LiveTripPass(around, t, nowL, night, haptics, tripSkipass(around, t), onField = { e -> tripEdit = e; Qa.log("redesign trip field $e") },
                onSkipass = { Qa.log("redesign trip skipass"); skipassOpen = true })
            else LiveTripEmpty(night) { tripEdit = io.github.pini236.skiapp.trip.PassEdit.Dates }
            // the top: back at the start, "edit" (the full form) at the end
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val back = stringResource(R.string.app_g_back)
                Box(Modifier.size(44.dp).semantics { contentDescription = back }.clickable { haptics.tick(.2f); tripOpen = false; tripEdit = null }, contentAlignment = Alignment.Center) {
                    Text(if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "›" else "‹", fontFamily = Gudi, fontSize = 24.sp, color = if (night) Color.White else sk.ink)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (t != null) Pill(Modifier.clickable { haptics.tick(.3f); tripEdit = null; tripForm = true }) {
                    Text(stringResource(R.string.trip_edit), Modifier.padding(horizontal = 18.dp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = ink)
                }
            }
            val e = tripEdit
            val todayL = nowL.toLocalDate()
            if (t == null || e == io.github.pini236.skiapp.trip.PassEdit.Dates) Box(Modifier.align(Alignment.BottomCenter)) {
                LiveCalendar(t, todayL, sk, haptics) { nt -> around.onTrip(nt); tripEdit = null; Qa.log("redesign trip saved") }
            }
            if (t != null && e != null && e != io.github.pini236.skiapp.trip.PassEdit.Dates)
                PassEditor(e, t, todayL, { nt -> around.onTrip(nt); tripEdit = null }) { tripEdit = null }
            if (tripForm) LiveTripForm(t, todayL, night, haptics,
                onSave = { nt -> around.onTrip(nt); tripForm = false },
                onDelete = { around.onTrip(null); tripForm = false; tripOpen = false; Qa.log("redesign trip deleted") },
                onBack = { tripForm = false }, localNote = !inGroup)
        }
        if (skipassOpen && around != null) {
            Box(Modifier.fillMaxSize().background(SheetInk(night).veil).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {})
            LiveSkipass(around, night, haptics)
            // the top: back, and "ski pass" as the title by it (handoff section 14.10); ink by day, white at night
            val top = if (night) Color.White else SheetInk(night).ink
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val back = stringResource(R.string.app_g_back)
                Box(Modifier.size(44.dp).semantics { contentDescription = back }.clickable { haptics.tick(.2f); skipassOpen = false }, contentAlignment = Alignment.Center) {
                    Text(if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) "›" else "‹", fontFamily = Gudi, fontSize = 24.sp, color = top)
                }
                Text(stringResource(R.string.app_skipass_title), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = top)
            }
        }
        // part 4 (S03, S04, S08): the location's box under the pill, the map's buttons, and S02 before the phone asks
        val bare = chosen == null && !villageOpen && !shelfOpen && fromSt == null && !liftsOpen && !meet.open && !tripOpen && !skipassOpen && !seasonOn && !people.open
        val dens = androidx.compose.ui.platform.LocalDensity.current.density
        // K06 by itself: after a ride, near the lift's top station, when nothing else is open (once for each ride)
        val stAuto = remember { StationAuto() }
        val wst = where.where.state
        LaunchedEffect(wst) {
            val f = wst as? io.github.pini236.skiapp.map.WhereAmI.State.Fixed ?: return@LaunchedEffect
            val on = (f.fix as? io.github.pini236.skiapp.map.Locator.Fix.OnLift)?.lift?.name
            val reached = stAuto.feed(on) { name -> scene.runs.lifts.firstOrNull { it.name == name }?.let { l -> LiveLifts.ends(l, scene.terrain::elev)?.let { e -> kotlin.math.hypot(f.x - e[2], f.y - e[3]) } } }
            if (reached != null && bare && plan != null) plan.stations.firstOrNull { reached in it.topOf && FromStation.runs(it.topOf, stationRuns).isNotEmpty() }?.let { s ->
                fromSt = s; Qa.log("redesign station auto ${s.id}")
            }
        }
        LaunchedEffect(bare, plan) {
            view.onPreTap = if (bare && plan != null) { x, y -> StationTap.at(view, plan, stationRuns, x, y, dens)?.let { s -> haptics.tick(.4f); fromSt = s; Qa.log("redesign station ${s.id}"); true } ?: false } else null
        }
        if (bare) WhereBox(where, night, onRun = { k -> haptics.tick(.3f); view.pickKey(k) }, onAction = whereAction, below = lensRow)
        // K08: one colour layer at a time: a run chosen, the shelf or a station turns the lens off and closes the row
        LaunchedEffect(chosen, shelfOpen, fromSt) { if (chosen != null || shelfOpen || fromSt != null) { lens = null; lensRow = false } }
        val wxLens = io.github.pini236.skiapp.weather.LocalWeather.current.usable
        val snowPts = remember(wxLens) { wxLens?.points?.map { it.elevation.toFloat() to it.snow24 } }
        LaunchedEffect(lens, night, status, snowPts) { view.setLens(lens, night, status?.takeIf { it.fresh }?.let { st -> { p: io.github.pini236.skiapp.data.Piste -> st.runOpen(p) } }, snowPts) }
        if (bare && lensRow) LensChips(lens, night, haptics) { l -> lens = LensRules.tap(lens, l); LensPrefs.setLast(context, lens ?: LensPrefs.last(context)); Qa.log("redesign lens tap ${l.id}") }
        if (bare && lens != null) LensSheet(lens!!, scene, view, status, inSeason, night) { lens = null; Qa.log("redesign lens off") }
        if (bare) MapColumn(where, view, night, recording = false, onLocate = { haptics.tick(.3f); locate() }, onHome = { haptics.tick(.3f); view.home() },
            layersOn = lensRow, onLayers = {
                haptics.tick(.3f)
                if (lensRow) { lensRow = false; lens = null } else { lensRow = true; lens = LensPrefs.last(context) }
                Qa.log("redesign lens row $lensRow")
            }, bottom = if (lens != null) 196.dp else 84.dp)
        RunHint(scene, view, night, free = bare && !where.asking, chosen = chosen != null, drawn = mtnDrawn, again = hintAgain)
        if (where.asking) WhereAsk(night, onGo = locateGo, onLater = { where.asking = false; Qa.log("redesign where ask later") })
        androidx.activity.compose.BackHandler(enabled = where.asking) { where.asking = false }
        // part 3 (R01 to R15) over everything of the mountain
        if (pp != null && (people.open || people.toast != null)) {
            val todayP = java.time.LocalDate.now(io.github.pini236.skiapp.meet.Meet.GUDAURI)
            LivePeople(pp, people, night, haptics, plan, around?.trip, todayP,
                onOpenMeetup = { _, m ->
                    val s = plan?.byId?.get(m.station)
                    people.close(); meetOpen(true)
                    if (s != null) {
                        val z = m.at.atOffset(io.github.pini236.skiapp.meet.Meet.GUDAURI)
                        meet.sid = s.id; meet.day = z.toLocalDate(); meet.time = z.toLocalTime(); meet.preset = null; meet.fromGroup = true
                    }
                    Qa.log("redesign people meetup open ${m.station}")
                },
                onNewMeetup = { _ -> people.close(); meetOpen(true); Qa.log("redesign people meetup new") })
        }
    }
}

/**
 * K11 on the live model, as phase A draws it on the picture (PeakScreen's chosen): by day, arrows down the fall line each
 * 240 m, a bead each 500 m on the longest piece with its km, the start and the end; at night a light each 10.5 dp on the
 * screen, the slope's colour round it. Only where the line is seen (not behind a ridge).
 */
private fun DrawScope.chosenMarks(m: LiveView.Marks, a: Float, night: Boolean, measurer: TextMeasurer,
                                   kmWords: HashMap<String, Pair<androidx.compose.ui.text.TextLayoutResult, androidx.compose.ui.text.TextLayoutResult>>) {
    if (a <= 0f) return
    val d = m.down; val dp = density
    val w = size.width; val h = size.height
    // the same colours as the line in the model (SlopeColors, the map's)
    fun col(li: Int, i: Int): Color = io.github.pini236.skiapp.map.SlopeColors.of(d.slopes[li][i]).let { Color(it[0], it[1], it[2]) }
    if (night) {
        // a light each 10.5 dp, only on the screen and never more than ChosenRules.MAX_LIGHTS in a frame
        // (GUDI-ANDROID-B: a line seen from close up ran to millions of lights off the screen, and the main thread froze)
        val step = 10.5f * dp
        var count = 0
        for (li in m.xs.indices) {
            val x = m.xs[li]; val y = m.ys[li]; val ok = m.seen[li]
            var left = 0f
            for (i in 0 until x.size - 1) {
                if (!ok[i] || !ok[i + 1] || !ChosenRules.onScreen(x[i], y[i], x[i + 1], y[i + 1], w, h, 20f * dp)) { left = 0f; continue }
                val dx = x[i + 1] - x[i]; val dy = y[i + 1] - y[i]; val len = kotlin.math.hypot(dx, dy)
                if (len > ChosenRules.MAX_SEG * (w + h)) { left = 0f; continue }
                var t = left
                while (t < len) {
                    if (++count > ChosenRules.MAX_LIGHTS) return
                    val q = Offset(x[i] + dx * t / len, y[i] + dy * t / len)
                    drawCircle(Color(0xFFFFF3D6), 2.3f * dp, q, alpha = a)
                    drawCircle(col(li, i), 4.4f * dp, q, alpha = .4f * a)
                    t += step
                }
                left = t - len
            }
        }
        return
    }
    val navy = Color(0xFF0E2233)
    for (li in m.xs.indices) {
        val x = m.xs[li]; val y = m.ys[li]; val ok = m.seen[li]; val al = d.along[li]
        var acc = 0f
        for (j in 1 until x.size - 1) {
            acc += al[j] - al[j - 1]
            if (acc > 240f && ok[j] && ok[j + 1]) {
                acc = 0f
                var ax = x[j + 1] - x[j - 1]; var ay = y[j + 1] - y[j - 1]; val n = kotlin.math.hypot(ax, ay)
                if (n < 1f) continue
                ax /= n; ay /= n
                val p = Path().apply {
                    moveTo(x[j] - 1.6f * dp * ax - 2.1f * dp * ay, y[j] - 1.6f * dp * ay + 2.1f * dp * ax); lineTo(x[j] + 1.3f * dp * ax, y[j] + 1.3f * dp * ay)
                    lineTo(x[j] - 1.6f * dp * ax + 2.1f * dp * ay, y[j] - 1.6f * dp * ay - 2.1f * dp * ax)
                }
                drawPath(p, Color.White, alpha = a, style = Stroke(1.3f * dp, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
    // on the longest piece: a bead each 500 m with its km (white, a dark outline under the letters), the start and the end
    val mi = d.main; val x = m.xs[mi]; val y = m.ys[mi]; val ok = m.seen[mi]; val al = d.along[mi]
    val style = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color.White)
    var b = 500f; var j = 0
    while (b < (al.lastOrNull() ?: 0f)) {
        while (j < al.size - 2 && al[j + 1] < b) j++
        if (ok[j] && ok[j + 1]) {
            val f = ((b - al[j]) / (al[j + 1] - al[j]).coerceAtLeast(.01f)).coerceIn(0f, 1f)
            val q = Offset(x[j] + (x[j + 1] - x[j]) * f, y[j] + (y[j + 1] - y[j]) * f)
            drawCircle(Color.White, 3.1f * dp, q, alpha = a); drawCircle(navy, 3.1f * dp, q, alpha = a, style = Stroke(1.2f * dp))
            var ax = x[j + 1] - x[j]; var ay = y[j + 1] - y[j]; val n = kotlin.math.hypot(ax, ay).coerceAtLeast(.01f); ax /= n; ay /= n
            val c = Offset(q.x + 13f * dp * ay, q.y - 13f * dp * ax)
            val s = ("%.1f".format(b / 1000f)).trimEnd('0').trimEnd('.')
            val (t, o) = kmWords.getOrPut(s) { measurer.measure(s, style) to measurer.measure(s, style.copy(color = Color(0xFF0A1932))) }
            val tl = Offset(c.x - t.size.width / 2f, c.y - t.size.height * .72f)
            val r = 1.2f * dp
            for (k in 0 until 8) { val an = k * Math.PI / 4; drawText(o, topLeft = Offset(tl.x + r * kotlin.math.cos(an).toFloat(), tl.y + r * kotlin.math.sin(an).toFloat()), alpha = a * .8f / 2.2f) }
            drawText(t, topLeft = tl, alpha = a)
        }
        b += 500f
    }
    if (ok.isNotEmpty() && ok[0]) { val q = Offset(x[0], y[0]); drawCircle(Color.White, 4.6f * dp, q, alpha = a); drawCircle(ACCENT_DAY, 4.6f * dp, q, alpha = a, style = Stroke(2f * dp)) }
    val e = x.size - 1
    if (e > 0 && ok[e]) { val q = Offset(x[e], y[e]); drawCircle(ACCENT_DAY, 3.6f * dp, q, alpha = a); drawCircle(Color.White, 3.6f * dp, q, alpha = a, style = Stroke(1.4f * dp)) }
}

/** The head of the light running down a chosen run (K09, as phase A): a white dot with a soft halo, [f] of the way down. */
private fun DrawScope.runningHead(m: LiveView.Marks, f: Float) {
    val d = m.down
    val total = d.along.sumOf { (it.lastOrNull() ?: 0f).toDouble() }.toFloat()
    var left = total * f.coerceIn(0f, 1f)
    for (li in m.xs.indices) {
        val al = d.along[li]; val len = al.lastOrNull() ?: 0f
        if (left > len && li < m.xs.size - 1) { left -= len; continue }
        var j = 0
        while (j < al.size - 2 && al[j + 1] < left) j++
        if (al.size < 2 || !m.seen[li][j]) return
        val k = ((left - al[j]) / (al[j + 1] - al[j]).coerceAtLeast(.01f)).coerceIn(0f, 1f)
        val q = Offset(m.xs[li][j] + (m.xs[li][j + 1] - m.xs[li][j]) * k, m.ys[li][j] + (m.ys[li][j + 1] - m.ys[li][j]) * k)
        drawCircle(Color.White, 13f * density, q, alpha = .5f); drawCircle(Color.White, 5f * density, q)
        return
    }
}

/** Limits on what the chosen run draws over the model in a frame (GUDI-ANDROID-B). */
internal object ChosenRules {
    const val MAX_LIGHTS = 400
    /** A piece longer than this many screens (width plus height) is a line seen from on it: no lights there. */
    const val MAX_SEG = 3f
    /** Whether the piece from (x0, y0) to (x1, y1) can cross the screen, [pad] round it (its box against the screen's). */
    fun onScreen(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, h: Float, pad: Float): Boolean =
        maxOf(x0, x1) >= -pad && minOf(x0, x1) <= w + pad && maxOf(y0, y1) >= -pad && minOf(y0, y1) <= h + pad &&
            x0.isFinite() && y0.isFinite() && x1.isFinite() && y1.isFinite()
}

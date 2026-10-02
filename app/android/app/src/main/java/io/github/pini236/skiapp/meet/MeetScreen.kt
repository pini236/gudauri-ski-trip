package io.github.pini236.skiapp.meet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.group.Button2
import io.github.pini236.skiapp.group.ErrorLine
import io.github.pini236.skiapp.group.GroupApi
import io.github.pini236.skiapp.group.GroupSummary
import io.github.pini236.skiapp.group.Look
import io.github.pini236.skiapp.group.rememberRunner
import io.github.pini236.skiapp.home.SignShape
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.nav.Route
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.TimeDialog
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val GOLD = Color(0xFFF4B942)
private val NAVY = Color(0xFF13233A)
private val FOG = Color(0xFFA3B3C8)

/** Where a group meetup can go from here: my groups, and the one the person came from (the group page's "new meetup"). */
class MeetSave(val api: GroupApi, val from: String?, val onSaved: (groupId: String) -> Unit)

/**
 * The meeting point (design round 3, M1 to M3; round 8, MP1 to MP3), as on the site's #meet: nothing is picked at
 * first, the map asks where to meet; a station (or one of the three spots) prints the meeting card with its countdown,
 * how to get there from the connections in the data, and the ways to share it. The place, the day and the time are in
 * the link, so a link from the site opens the same card here. A signed-in person saves it in a group (Q8).
 */
@Composable
fun MeetScreen(
    plan: MeetPlan?, runs: Runs?, relief: Relief2D?, trip: Trip?, nowMs: Long, start: Route.Meet, save: MeetSave?,
    onRoute: (Route.Meet) -> Unit, onOnMap: (Station) -> Unit, onBack: () -> Unit,
) {
    val c = Ski.colors
    val context = LocalContext.current
    val lang = Lang.current(context.resources)
    val now = Instant.ofEpochMilli(nowMs)
    val days = remember(trip, nowMs / 3_600_000) { Meet.days(trip, now) }
    // the place, day and time: from the link if it has them, else nothing picked, the first ski day and 12:30
    var sid by rememberSaveable { mutableStateOf(start.station) }
    var preset by rememberSaveable { mutableStateOf<String?>(null) }
    var dayIso by rememberSaveable { mutableStateOf((Meet.day(start.day) ?: days.first()).toString()) }
    var timeTxt by rememberSaveable { mutableStateOf(Meet.time(start.time)?.toString() ?: Meet.DEFAULT_TIME) }
    val day = LocalDate.parse(dayIso); val time = Meet.parseTime(timeTxt)
    val station = plan?.byId?.get(sid)
    val view = remember { MeetView() }
    val scope = rememberCoroutineScope()
    var undo by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var toast by remember { mutableIntStateOf(0) }
    var askTime by remember { mutableStateOf(false) }

    // the address follows the choice (a link to share, and the place to come back to after the system closes the app)
    var reported by remember { mutableStateOf<Route.Meet?>(null) }
    LaunchedEffect(sid, dayIso, timeTxt) { val r = if (sid == null) Route.Meet() else Meet.route(sid!!, time, day); reported = r; onRoute(r) }
    LaunchedEffect(Unit) { if (start.station != null) Telemetry.event("meet_link_open") }
    // another meeting point's link while this page is open: that place, day and time
    LaunchedEffect(start) {
        val was = reported ?: return@LaunchedEffect
        if (start == was) return@LaunchedEffect
        sid = start.station; preset = null
        Meet.day(start.day)?.let { dayIso = it.toString() }
        Meet.time(start.time)?.let { timeTxt = "%02d:%02d".format(it.hour, it.minute) }
        if (start.station != null) Telemetry.event("meet_link_open")
        val s = plan?.byId?.get(start.station)
        if (s != null) view.goTo(scope, s.x, s.y, 1800f, 0) else plan?.let { view.fitAll(scope, it.stations, 0) }
        Qa.log("meet ready (link)${if (s != null) " · ${s.id}" else ""}")
    }
    // the first window: on the station of a link, else the whole mountain
    LaunchedEffect(plan, view.ready) {
        if (plan == null || !view.ready) return@LaunchedEffect
        if (sid != null && sid !in plan.byId) sid = null // a link to a station that is not on this map (old data)
        val s = plan.byId[sid]
        if (s != null) view.goTo(scope, s.x, s.y, 1800f, 0) else view.fitAll(scope, plan.stations, 0)
        Qa.log("meet ready ${plan.stations.size} stations${if (s != null) " · ${s.id}" else ""}")
    }
    LaunchedEffect(toast) { if (toast > 0) { delay(4500); undo = null } }

    fun pick(id: String, p: Preset? = null) {
        sid = id; preset = p?.name
        val s = plan?.byId?.get(id) ?: return
        view.goTo(scope, s.x, s.y, max(1400f, if (view.span < 1500f) view.span else 1800f), 650)
        Telemetry.event("meet_pick", if (p != null) mapOf("kind" to "preset", "preset" to p.event) else mapOf("kind" to "station", "station" to s.name))
        Qa.log("meet picked $id")
    }
    fun clear(via: String) {
        val was = sid ?: return
        Telemetry.event("meet_clear", mapOf("via" to via))
        undo = was to preset; sid = null; preset = null; toast++
        Qa.log("meet cleared $via")
    }

    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.nav_meet), stringResource(R.string.nav_home), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // ---- the map (M1, MP1) ----
            val mapH = (LocalConfiguration.current.screenHeightDp * .52f).dp
            Box(Modifier.fillMaxWidth().height(mapH).clipToBounds().background(c.snow)) {
                if (plan != null && runs != null) {
                    val lines = remember(runs) { MeetLines(runs) }
                    MeetMap(view, plan, lines, relief, c, sid, stringResource(R.string.meet_map_aria),
                        onPin = { id -> if (id == sid) clear("pin") else pick(id) }, onEmpty = { clear("map") }, modifier = Modifier.fillMaxSize())
                    station?.let { Callout(view, it) }
                }
                MapButton(stringResource(R.string.meet_whole_mountain), Modifier.align(AbsoluteAlignment.TopLeft).padding(12.dp)) { plan?.let { view.fitAll(scope, it.stations, 700) } }
                Column(Modifier.align(AbsoluteAlignment.TopLeft).padding(start = 12.dp, top = 64.dp).background(c.paper).border(1.5.dp, c.rule)
                    .semantics { contentDescription = context.getString(R.string.meet_zoom) }) {
                    ZoomButton("+", stringResource(R.string.meet_zoom_in)) { view.zoomAt(scope, .6f, station?.x, station?.y, 250) }
                    Box(Modifier.width(44.dp).height(1.5.dp).background(c.rule))
                    ZoomButton("−", stringResource(R.string.meet_zoom_out)) { view.zoomAt(scope, 1 / .6f, null, null, 250) }
                }
                if (station != null) {
                    Row(Modifier.align(AbsoluteAlignment.TopRight).padding(12.dp).background(c.paper).border(1.5.dp, c.rule)
                        .clickable(role = Role.Button) { clear("button") }.heightIn(min = 44.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.x, null, Modifier.size(14.dp), tint = c.ink)
                        Text(stringResource(R.string.meet_clear), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.ink)
                    }
                }
                val u = undo
                if (u != null) {
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).background(NAVY).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.meet_cleared), Modifier.weight(1f), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = Color.White)
                        Box(Modifier.background(GOLD).clickable(role = Role.Button) {
                            undo = null; Telemetry.event("meet_undo"); pick(u.first, u.second?.let { Preset.valueOf(it) })
                        }.heightIn(min = 44.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.meet_undo), style = Ski.type.bodyBold, color = NAVY)
                        }
                    }
                } else if (station == null) {
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).background(NAVY).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AskDot()
                        Text(stringResource(R.string.meet_hint_pick, (plan?.stations?.size ?: 0).toString()), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = Color.White)
                    }
                } else {
                    Text(stringResource(R.string.meet_hint_clear), Modifier.align(AbsoluteAlignment.BottomLeft).padding(12.dp).background(c.paper.copy(alpha = .94f))
                        .border(1.dp, c.rule).padding(horizontal = 10.dp, vertical = 4.dp), style = Ski.type.label.copy(fontSize = 12.5.sp), color = c.ink)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))

            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // ---- when (M2) ----
                Column {
                    H2(stringResource(R.string.meet_when))
                    for (row in days.chunked(4)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (d in row) Chip(dayLabel(d, lang.tag), d == day, Modifier.weight(1f), big = false) { dayIso = d.toString(); preset = null }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (t in Meet.TIMES) Chip(t, t == timeTxt, Modifier.weight(1f), big = true) { timeTxt = t; preset = null }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.meet_other_time), style = Ski.type.small, color = c.muted)
                        Row(Modifier.background(c.paper).border(1.5.dp, c.rule).clickable(role = Role.Button) { askTime = true }.heightIn(min = 44.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.clock, null, Modifier.size(18.dp), tint = c.muted)
                            Text(timeTxt, style = Ski.type.bodyBold.copy(fontSize = 16.sp), color = c.ink)
                        }
                    }
                }
                // ---- in one tap (M2, the group's spots on a post) ----
                if (plan != null) Column {
                    H2(stringResource(R.string.meet_one_tap))
                    PresetPost(plan, preset) { p, s -> timeTxt = p.time; pick(s.id, p) }
                }
                // ---- the card (M3; MP2 the waiting card, MP3 the X) ----
                if (station == null) EmptyCard() else MeetCard(station, time, dayLabel(day, lang.tag), Meet.left(Meet.at(day, time), now)) { clear("card") }

                if (station != null && plan != null && runs != null) {
                    Column {
                        H2(stringResource(R.string.meet_routes))
                        val ways = remember(station) { plan.ways(station) }
                        if (ways.isEmpty()) Text(stringResource(R.string.meet_routes_empty), style = Ski.type.small, color = c.muted)
                        for (w in ways) WayRow(w)
                        Text(stringResource(R.string.meet_routes_hint), Modifier.padding(top = 8.dp), style = Ski.type.small, color = c.muted)
                    }
                    ShareBox(station, time, day, dayLabel(day, lang.tag), runs, relief, lang, save) { onOnMap(station) }
                }
                Text(stringResource(R.string.meet_no_db), style = Ski.type.small, color = c.muted)
            }
        }
    }
    if (askTime) TimeDialog(stringResource(R.string.meet_other_time), time, onPick = { t -> timeTxt = "%02d:%02d".format(t.hour, t.minute); preset = null; askTime = false }, onDismiss = { askTime = false })
}

/** The day on a chip, as the site writes it: "ו׳ 2.10" in Hebrew, "Fri 2 Oct" elsewhere. */
fun dayLabel(d: LocalDate, lang: String): String =
    if (lang == "he") DateTimeFormatter.ofPattern("EEEEE", Locale.forLanguageTag("he")).format(d) + " ${d.dayOfMonth}.${d.monthValue}"
    else runCatching { DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag(lang)).format(d) }.getOrElse { "${d.dayOfMonth}.${d.monthValue}" }

/** A Latin name inside a sentence of another direction, kept whole ("מ-Tatra 1", "של Goodaura ו-New Goodaura"). */
fun iso(name: String) = "\u2068$name\u2069"

/** "התחנה התחתונה של Goodaura ו-New Goodaura" (the site's where). */
@Composable
fun stationWhere(s: Station): String {
    val join = stringResource(R.string.meet_lift_names_join)
    return listOfNotNull(
        s.bottomOf.takeIf { it.isNotEmpty() }?.let { n -> stringResource(R.string.meet_where_bottom_station, n.joinToString(join) { iso(it) }) },
        s.topOf.takeIf { it.isNotEmpty() }?.let { n -> stringResource(R.string.meet_where_top_station, n.joinToString(join) { iso(it) }) },
    ).joinToString(", ")
}

@Composable
private fun altitude(s: Station): String? = s.h?.let { stringResource(R.string.common_unit_m, String.format(Locale.US, "%,d", it)) }

@Composable
private fun H2(text: String) = Text(text, Modifier.padding(bottom = 8.dp), style = Ski.type.title.copy(fontSize = (26 * Ski.type.displayScale).sp), color = Ski.colors.ink)

@Composable
private fun MapButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ski.colors
    Box(modifier) {
        Box(Modifier.background(c.paper).border(1.5.dp, c.rule).clickable(role = Role.Button, onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, style = Ski.type.bodyBold.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
        }
    }
}

@Composable
private fun ZoomButton(text: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.bodyBold.copy(fontSize = 24.sp, textAlign = TextAlign.Center), color = Ski.colors.ink)
    }
}

/** The gold dot that asks where to meet (meet-hint.ask). */
@Composable
private fun AskDot() {
    val t = rememberInfiniteTransition(label = "ask").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "ask")
    Canvas(Modifier.size(22.dp)) {
        val r = 5.dp.toPx()
        drawCircle(GOLD.copy(alpha = .3f * (1 - t.value)), r + 6.dp.toPx() * t.value)
        drawCircle(GOLD, r)
    }
}

/** The dark label over the picked pin: the name, its height in gold, and which station it is (meet-callout). */
@Composable
private fun Callout(view: MeetView, s: Station) {
    if (!view.ready) return
    val d = LocalDensity.current
    val where = stationWhere(s)
    val alt = altitude(s)
    with(d) {
        val w = view.w.toDp(); val x = view.sx(s.x).toDp(); val y = view.sy(s.y).toDp()
        val left = (x - 100.dp).coerceIn(8.dp, max(8f, (w - 208.dp).value).dp)
        val top = max(6f, (y - 128.dp).value).dp
        val tip = (x - left - 8.dp).coerceIn(12.dp, 184.dp)
        // positions on the map are physical (left and top), whatever the reading direction
        Box(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        Box(Modifier.absoluteOffset(left, top).width(200.dp)) {
            Column(Modifier.shadow(10.dp, RectangleShape).background(NAVY).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(s.name, Modifier.weight(1f, fill = false), style = Ski.type.title.copy(fontSize = (30 * Ski.type.displayScale).sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (alt != null) Text(alt, style = Ski.type.label.copy(fontSize = 12.sp), color = GOLD)
                }
                Text(where, style = Ski.type.small.copy(fontSize = 12.sp), color = FOG)
            }
            // the tip points down at the pin
            Box(Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(tip, 8.dp).size(16.dp).rotate(45f).background(NAVY))
        }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, modifier: Modifier, big: Boolean, onClick: () -> Unit) {
    val c = Ski.colors
    Box(
        modifier.offset(y = if (on) (-3).dp else 0.dp)
            .drawBehind { if (on) drawRect(c.ink, Offset(0f, 4.dp.toPx()), size) }
            .background(if (on) GOLD else c.paper).border(1.5.dp, c.ink)
            .clickable(role = Role.Button, onClick = onClick).semantics { selected = on }
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (big) Ski.type.title.copy(fontSize = (21 * Ski.type.displayScale).sp) else Ski.type.bodyBold.copy(fontSize = 13.5.sp),
            color = if (on) NAVY else c.ink, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** The three spots as signs on a post (mp-sign): gold, blue and ink, each a little shorter. */
@Composable
private fun PresetPost(plan: MeetPlan, picked: String?, onPick: (Preset, Station) -> Unit) {
    val c = Ski.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = remember { SignShape(18.dp) }
    Column(
        Modifier.fillMaxWidth().drawBehind {
            val x = if (rtl) size.width - 7.dp.toPx() else 0f
            drawRect(c.ink, Offset(x, -8.dp.toPx()), Size(7.dp.toPx(), size.height + 20.dp.toPx()))
        }.padding(start = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val spec = listOf(Triple(Preset.MORNING, R.string.meet_preset_morning_lift, .92f), Triple(Preset.NOON, R.string.meet_preset_noon, .84f), Triple(Preset.END, R.string.meet_preset_end_of_day, .76f))
        for ((p, label, width) in spec) {
            val s = plan.preset(p) ?: continue
            val on = picked == p.name
            val (bg, fg) = when (p) { Preset.MORNING -> GOLD to NAVY; Preset.NOON -> c.blue to c.onBoard; Preset.END -> c.ink to c.paper }
            Row(
                Modifier.fillMaxWidth(width).absoluteOffset(x = if (on) (-10).dp else 0.dp)
                    .background(bg, shape)
                    .drawWithContent { drawContent(); if (on) drawRect(Color.White.copy(alpha = .55f), Offset(0f, size.height - 5.dp.toPx()), Size(size.width, 5.dp.toPx())) }
                    .clickable(role = Role.Button) { onPick(p, s) }.semantics { selected = on }
                    .heightIn(min = 48.dp).padding(start = 16.dp, end = 30.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(label), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = fg, maxLines = 1)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text("${s.name} ${p.time}", style = Ski.type.title.copy(fontSize = (23 * Ski.type.displayScale).sp), color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** MP2: the card waits, a dashed outline with an empty pin. */
@Composable
private fun EmptyCard() {
    val c = Ski.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).rotate(-1.5f)
            .drawBehind { drawRect(c.dash, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))) }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.pin, null, Modifier.size(34.dp), tint = c.muted)
        Column {
            Text(stringResource(R.string.meet_empty_title), style = Ski.type.title.copy(fontSize = (30 * Ski.type.displayScale).sp), color = c.ink)
            Text(stringResource(R.string.meet_empty_body), Modifier.padding(top = 4.dp), style = Ski.type.small, color = c.muted)
        }
    }
}

/** The countdown on the stub, its number big in the middle (the site's slots()). */
@Composable
private fun slots(left: Meet.Left): Triple<String, String, String> {
    val mark = "\u0001"
    val (text, n) = when (left) {
        Meet.Left.Passed -> return Triple("", stringResource(R.string.meet_countdown_passed), "")
        is Meet.Left.Minutes -> pluralStringResource(R.plurals.meet_countdown_minutes, left.n, mark) to left.n.toString()
        is Meet.Left.Hours -> stringResource(R.string.meet_countdown_hours, mark) to left.hm
        is Meet.Left.Days -> pluralStringResource(R.plurals.meet_countdown_days, left.n, mark) to left.n.toString()
    }
    val i = text.indexOf(mark)
    return if (i < 0) Triple("", text, "") else Triple(text.substring(0, i).trim(), n, text.substring(i + 1).trim())
}

/** M3: the meeting card, a boarding pass of the mountain with its stub (mcard), tilted, with the X to cancel (MP3). */
@Composable
private fun MeetCard(s: Station, time: LocalTime, day: String, left: Meet.Left, onCancel: () -> Unit) {
    val c = Ski.colors
    val (c1, c2, c3) = slots(left)
    val shape = remember { SignShape(18.dp) }
    val where = stationWhere(s)
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        Column(
            Modifier.fillMaxWidth().rotate(-2f).shadow(14.dp, RectangleShape).background(c.paper)
                .drawWithContent {
                    drawContent()
                    // the punched notches where the stub tears off, in the page's colour
                    val y = size.height - 112.dp.toPx(); val r = 11.dp.toPx()
                    drawCircle(c.snow, r, Offset(0f, y)); drawCircle(c.snow, r, Offset(size.width, y))
                },
        ) {
            Row(Modifier.fillMaxWidth().background(NAVY).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.meet_card), style = Ski.type.title.copy(fontSize = (24 * Ski.type.displayScale).sp), color = Color.White)
                Text(stringResource(R.string.meet_card_sub), style = Ski.type.small.copy(fontSize = 12.sp), color = FOG)
            }
            Column(Modifier.fillMaxWidth().border(1.dp, c.rule).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.meet_meet_at), style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
                        Box(Modifier.background(c.blue, shape).padding(start = 14.dp, end = 28.dp, top = 5.dp, bottom = 5.dp)) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(s.name, style = Ski.type.title.copy(fontSize = (34 * Ski.type.displayScale).sp), color = c.onBoard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.meet_time), style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
                        Text("%02d:%02d".format(time.hour, time.minute), style = Ski.type.title.copy(fontSize = (44 * Ski.type.displayScale).sp), color = c.ink)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Meta(stringResource(R.string.meet_gate), where, Modifier.weight(1.6f))
                    Meta(stringResource(R.string.meet_altitude), altitude(s) ?: "—", Modifier.weight(1f))
                    Meta(stringResource(R.string.meet_day), day, Modifier.weight(1f))
                }
            }
            Column(Modifier.fillMaxWidth().height(112.dp).background(GOLD), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (c1.isNotEmpty()) Text(c1, style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = NAVY)
                Text(c2, style = Ski.type.title.copy(fontSize = (64 * Ski.type.displayScale).sp), color = NAVY)
                if (c3.isNotEmpty()) Text(c3, style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = NAVY)
            }
        }
        val cancel = stringResource(R.string.meet_cancel)
        Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset((-6).dp, (-8).dp).size(44.dp).shadow(4.dp, CircleShape).background(c.paper, CircleShape).border(2.dp, c.ink, CircleShape)
            .clickable(role = Role.Button, onClick = onCancel).semantics { contentDescription = cancel }, contentAlignment = Alignment.Center) {
            Icon(Icons.x, null, Modifier.size(14.dp), tint = c.ink)
        }
    }
}

@Composable
private fun Meta(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = Ski.type.small.copy(fontSize = 12.sp), color = Ski.colors.muted)
        Text(value, style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = Ski.colors.ink)
    }
}

/** The chips of a way always point left, as the site draws them (rt-run). */
private class LeftArrow(private val a: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val x = with(density) { a.toPx() }
        return Outline.Generic(Path().apply { moveTo(0f, size.height / 2); lineTo(x, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(x, size.height); close() })
    }
}

/** One way there (M3): where from, then the runs as small trail signs or the lift, and the gold dot of the spot. */
@Composable
private fun WayRow(w: Way) {
    val c = Ski.colors
    val from = when (val f = w.from) {
        is Way.From.Run -> stringResource(R.string.meet_route_from_run, iso(f.key))
        is Way.From.TopOf -> stringResource(R.string.meet_route_from_top_of, iso(f.key))
        is Way.From.BottomOf -> stringResource(R.string.meet_route_from_bottom_station, iso(f.lift))
    }
    val arrow = remember { LeftArrow(12.dp) }
    Column(Modifier.fillMaxWidth().drawBehind { drawRect(c.rule, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }.padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(from, style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = c.ink)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                for ((i, h) in w.hops.withIndex()) {
                    if (i > 0) Sep()
                    when (h) {
                        is Way.Hop.Run -> Box(Modifier.background(c.run(h.piste.color), arrow).height(30.dp).padding(start = 20.dp, end = 10.dp), contentAlignment = Alignment.Center) {
                            Text(if (h.piste.key == "Firni ?") "Firni (1/2?)" else h.piste.key, style = Ski.type.title.copy(fontSize = (21 * Ski.type.displayScale).sp),
                                color = if (h.piste.color == "black") c.paper else Color.White, maxLines = 1)
                        }
                        is Way.Hop.Lift -> Box(Modifier.background(c.ink).height(30.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text("⇡ ${h.name}", style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = c.paper, maxLines = 1)
                        }
                    }
                }
                Sep()
                Box(Modifier.size(14.dp).background(GOLD, CircleShape).border(2.dp, c.ink, CircleShape))
            }
        }
    }
}

@Composable
private fun Sep() {
    val m = Ski.colors.muted
    Canvas(Modifier.width(12.dp).height(2.dp)) {
        drawLine(m, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
    }
}

/** M3's ways to share: WhatsApp, the picture, the link; the run map; and a group, for a signed-in member (Q8). */
@Composable
private fun ShareBox(s: Station, time: LocalTime, day: LocalDate, dayText: String, runs: Runs, relief: Relief2D?, lang: Lang.Language, save: MeetSave?, onOnMap: () -> Unit) {
    val context = LocalContext.current
    val c = Ski.colors
    val hhmm = "%02d:%02d".format(time.hour, time.minute)
    val link = Meet.link(s.id, time, day)
    val where = stationWhere(s).replace("\u2068", "").replace("\u2069", "") // the message is plain text for other apps
    val alt = s.h?.let { stringResource(R.string.meet_share_alt_suffix, String.format(Locale.US, "%,d", it)) } ?: ""
    val message = stringResource(R.string.meet_share_message, s.name, dayText, hhmm, where, alt, link)
    val title = stringResource(R.string.meet_share_title, s.name)
    val label = stringResource(R.string.meet_share_image_card_label, dayText)
    val site = stringResource(R.string.common_site_name)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2200); copied = false } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button2(stringResource(R.string.meet_send_wa), Look.INK, {
            Telemetry.event("meet_share", mapOf("method" to "whatsapp"))
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(message)))) }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button2(stringResource(R.string.meet_share_image), Look.INK, {
                Telemetry.event("meet_share", mapOf("method" to "image"))
                val bmp = MeetImage.draw(s, hhmm, label, site, runs, relief, Lang.typeface(context, lang, true), Lang.typeface(context, lang, false), lang.rtl)
                runCatching { MeetImage.share(context, bmp, message, title) }
            }, Modifier.weight(1f))
            Button2(stringResource(if (copied) R.string.common_link_copied else R.string.meet_copy_link), Look.GHOST, {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title, link))
                Telemetry.event("meet_share", mapOf("method" to "copy")); copied = true
            }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button2(stringResource(R.string.meet_on_map), Look.GHOST, onOnMap, Modifier.weight(1f))
            if (save != null) SaveButton(save, s, Meet.at(day, time), Modifier.weight(1f))
        }
    }
}

/**
 * "Save in the group" (round 14 on the site): from a group's page it goes to that group; otherwise to my only group,
 * or I choose which. Only for someone signed in who is in a group; offline it waits in the queue on the phone.
 */
@Composable
private fun SaveButton(save: MeetSave, s: Station, at: Instant, modifier: Modifier) {
    val r = rememberRunner()
    var groups by remember { mutableStateOf<List<GroupSummary>?>(null) }
    var choose by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (save.api.ready && save.api.me() != null) groups = runCatching { save.api.myGroups() }.getOrNull() }
    LaunchedEffect(saved) { if (saved) { delay(2500); saved = false } }
    val gs = groups.orEmpty()
    if (gs.isEmpty()) return
    fun go(id: String) = r.run {
        save.api.addMeetup(id, s.id, at); saved = true; choose = false; Qa.log("meetup saved in $id")
        if (save.from == id) save.onSaved(id)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button2(stringResource(if (saved) R.string.meet_saved_group else R.string.meet_save_group), Look.INK, {
            when {
                save.from != null && gs.any { it.id == save.from } -> go(save.from)
                gs.size == 1 -> go(gs[0].id)
                else -> choose = !choose
            }
        }, enabled = !r.busy)
        if (choose) {
            Text(stringResource(R.string.meet_pick_group), style = Ski.type.small, color = Ski.colors.muted)
            for (g in gs) Button2(g.name, Look.GHOST, { go(g.id) }, small = true)
        }
        ErrorLine(r)
    }
}

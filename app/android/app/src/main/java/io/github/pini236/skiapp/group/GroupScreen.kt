package io.github.pini236.skiapp.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as A11y
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.home.Hero
import io.github.pini236.skiapp.home.PassShape
import io.github.pini236.skiapp.home.dayRange
import io.github.pini236.skiapp.home.paperGrain
import io.github.pini236.skiapp.home.shortDate
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.trip.DateDialog
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Field
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Note
import io.github.pini236.skiapp.ui.PrimaryButton
import io.github.pini236.skiapp.ui.QuietButton
import io.github.pini236.skiapp.ui.Ski
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class GroupTab { FLIGHTS, MEETUPS, SCORES, MEMBERS;
    val key get() = name.lowercase()
    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: FLIGHTS }
}

/** The group sign: my group if I am in one (the first, for now), otherwise the way in (A1). */
@Composable
fun GroupHub(api: GroupApi, onGroup: (String) -> Unit, entry: @Composable () -> Unit) {
    var groups by remember { mutableStateOf<List<GroupSummary>?>(null) }
    val r = rememberRunner()
    LaunchedEffect(Unit) { r.run { groups = if (api.me() == null) emptyList() else api.myGroups() } }
    val g = groups
    when {
        g == null && r.error == null -> Box(Modifier.fillMaxSize().background(Ski.colors.snow))
        g.isNullOrEmpty() -> entry()
        else -> LaunchedEffect(g) { onGroup(g.first().id) }
    }
}

/** The games with a high-score table (Q9), by their key on the server and the site. */
private val GAMES = listOf("descent" to R.string.games_descent_name, "school" to R.string.games_school_name, "fresh" to R.string.games_fresh_name,
    "snowball" to R.string.games_snowball_name, "merge" to R.string.games_merge_name)

/**
 * The group page (Q6, Q8, Q9, Q10): its head over the view from the village, and four tabs: flights, meetups, high
 * scores, and members (requests and admin actions for admins). After joining as a guest, the gentle offer to keep the
 * place with Google (A5), once now and once a week before the trip.
 */
@Composable
fun GroupScreen(
    api: GroupApi, groupId: String, tab: GroupTab, frame: DayNight.Frame, myTrip: Trip?, now: LocalDateTime, justJoined: Boolean, saveOffers: Int,
    stationName: (String) -> String?,
    onTab: (GroupTab) -> Unit, onBack: () -> Unit, onInvite: () -> Unit, onNewMeetup: () -> Unit, onEditTrip: () -> Unit,
    onMyTrip: (Trip) -> Unit, onLeft: () -> Unit, signInGoogle: suspend () -> Unit, onSaveOffered: () -> Unit,
) {
    val c = Ski.colors
    var group by remember { mutableStateOf<Group?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var forMember by rememberSaveable { mutableStateOf<String?>(null) }
    var offered by rememberSaveable { mutableStateOf(false) }
    val load = rememberRunner(); val r = rememberRunner()
    fun reload() = load.run { group = api.group(groupId) }
    LaunchedEffect(groupId) { reload() }
    // A5: a guest who just joined is offered to keep the place, and once more a week before the trip; never again
    LaunchedEffect(group) {
        val g = group ?: return@LaunchedEffect
        if (offered || api.me()?.registered != false) return@LaunchedEffect
        val soon = g.startsOn?.let { ChronoUnit.DAYS.between(now.toLocalDate(), it) in 0..7 } == true
        if ((justJoined && saveOffers == 0) || (saveOffers == 1 && soon)) { offered = true; sheet = "save"; onSaveOffered() }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().background(c.snow)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // the head: the view, home and invite, and the group's card with its countdown
            Box(Modifier.fillMaxWidth().height(150.dp + top + 26.dp)) {
                Hero(frame, 150.dp + top)
                Row(Modifier.padding(top = top + 6.dp, start = 10.dp, end = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { BackLink(stringResource(R.string.nav_home), onBack, c.ink) }
                    Row(Modifier.heightIn(min = 44.dp).background(c.paper).clickable(role = A11y.Button, onClick = onInvite).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.plus, null, Modifier.size(18.dp), tint = c.glacier)
                        Text(stringResource(R.string.app_g_invite_short), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.glacier)
                    }
                }
                val g = group
                Row(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).fillMaxWidth().height(IntrinsicSize.Min)
                    .shadow(8.dp, RectangleShape).background(c.paper)) {
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Display(g?.name ?: "…", 40f)
                        if (g != null) Muted(listOfNotNull(
                            if (g.startsOn != null && g.endsOn != null) "⁦${dayRange(g.startsOn..g.endsOn)}.${g.endsOn.year}⁩" else null,
                            pluralStringResource(R.plurals.app_g_members, g.members.size, g.members.size),
                            pluralStringResource(R.plurals.app_g_admins, g.admins, g.admins),
                        ).joinToString(" · "), size = 13f)
                    }
                    val days = g?.startsOn?.let { s -> Duration.between(now, s.atStartOfDay()).toHours().let { h -> if (h <= 0) 0 else ((h + 23) / 24).toInt() } }
                    if (days != null) Column(Modifier.width(84.dp).fillMaxHeight().background(c.accent), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(days.toString(), style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 40.sp), color = c.onAccent)
                        Text(pluralStringResource(if (c.dark) R.plurals.app_pass_nights else R.plurals.app_pass_days, days), style = Ski.type.label.copy(fontSize = 11.sp), color = c.onAccent)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
            Tabs(tab, onTab)
            val g = group
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (g == null) { if (load.busy) Muted(stringResource(R.string.app_loading)) else ErrorLine(load); return@Column }
                when (tab) {
                    GroupTab.FLIGHTS -> Flights(g, myTrip,
                        onSame = { sheet = "same" },
                        onShowMine = { r.run { api.showMyTrip(g.id, g.me?.name ?: "", myTrip); group = api.group(g.id) } },
                        onFillFor = { forMember = it; sheet = "fill" })
                    GroupTab.MEETUPS -> Meetups(g, now, stationName, onNewMeetup)
                    GroupTab.SCORES -> Scores(api, g)
                    GroupTab.MEMBERS -> Members(api, g, r, reload = { reload() }, onInvite = onInvite, onEdit = { sheet = "edit" }, onLeft = onLeft)
                }
                ErrorLine(r)
            }
        }
        val g = group
        when (sheet) {
            "save" -> Sheet({ sheet = null }) {
                Display(stringResource(R.string.app_a_welcome), 36f)
                Muted(stringResource(R.string.app_a_welcome_sub), Modifier.padding(top = 6.dp, bottom = 16.dp), 14f)
                GoogleButton(stringResource(R.string.app_a_save_google), { r.run { signInGoogle(); sheet = null } }, enabled = !r.busy)
                ErrorLine(r)
                QuietButton(stringResource(R.string.app_a_not_now), { sheet = null })
                Muted(stringResource(R.string.app_a_ask_once_more), Modifier.padding(top = 12.dp), 12.5f)
            }
            "same", "fill" -> if (g != null) Sheet({ sheet = null }) {
                val fill = sheet == "fill"
                Display(stringResource(if (fill) R.string.app_g_fill_for else R.string.app_g_same_flight), 36f)
                Muted(stringResource(if (fill) R.string.app_g_fill_sub else R.string.app_g_same_sub), Modifier.padding(top = 6.dp, bottom = 14.dp), 14f)
                var picked by remember { mutableStateOf<String?>(null) }
                val trips = g.members.filter { it.trip != null && it.tripId != null }.distinctBy { it.trip }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (m in trips) {
                        val t = m.trip!!; val sel = picked == m.tripId
                        val on = g.members.count { it.trip == t }
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(c.bpPaper2).border(if (sel) 2.dp else 1.dp, if (sel) c.accent else c.rule)
                            .clickable(role = A11y.RadioButton) { picked = m.tripId }.semantics { selected = sel }.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                LegLine(t.out, 28f)
                                Muted(legWhen(t.out, false) + " · " + pluralStringResource(R.plurals.app_g_from_group, on, on), size = 12.5f)
                            }
                            if (sel) Icon(Icons.check, null, Modifier.size(22.dp), tint = c.glacier)
                        }
                    }
                }
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrimaryButton(stringResource(R.string.app_save), Icons.check, {
                        val tid = picked ?: return@PrimaryButton
                        r.run {
                            if (fill) { val who = forMember ?: return@run; api.setMemberTrip(g.id, who, g.members.first { it.tripId == tid }.trip!!) }
                            else { val t = api.sameFlight(g.id, tid); if (myTrip == null) onMyTrip(t) }
                            group = api.group(g.id); sheet = null
                        }
                    })
                    if (!fill) QuietButton(stringResource(R.string.app_g_other_flight), { sheet = null; onEditTrip() })
                    ErrorLine(r)
                }
            }
            "edit" -> if (g != null) Sheet({ sheet = null }) {
                var name by rememberSaveable { mutableStateOf(g.name) }
                var from by rememberSaveable { mutableStateOf(g.startsOn) }
                var to by rememberSaveable { mutableStateOf(g.endsOn) }
                var pick by rememberSaveable { mutableStateOf<String?>(null) }
                Display(stringResource(R.string.app_g_name_dates), 32f)
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(stringResource(R.string.app_g_group_name), name, { name = it.take(60) })
                    GroupDates(from, to, { pick = it })
                    PrimaryButton(stringResource(R.string.app_save), Icons.check, {
                        if (name.isNotBlank()) r.run { api.updateGroup(g.id, name, from, to); group = api.group(g.id); sheet = null }
                    })
                    ErrorLine(r)
                }
                val today = now.toLocalDate()
                when (pick) {
                    "from" -> DateDialog(from, today, today, null, { from = it; if (to != null && to!! < it) to = null }, { pick = null })
                    "to" -> DateDialog(to, from ?: today, from ?: today, null, { to = it }, { pick = null })
                }
            }
        }
    }
}

@Composable
private fun Tabs(tab: GroupTab, onTab: (GroupTab) -> Unit) {
    val c = Ski.colors
    val names = listOf(R.string.app_g_tab_flights, R.string.app_g_tab_meetups, R.string.app_g_tab_scores, R.string.app_g_tab_members)
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().drawBehind { drawRect(c.ink, Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }) {
        for ((i, t) in GroupTab.entries.withIndex()) {
            val on = t == tab
            Box(Modifier.weight(1f).heightIn(min = 46.dp).background(if (on) c.ink else Color.Transparent).clickable(role = A11y.Tab) { onTab(t) }.semantics { selected = on },
                contentAlignment = Alignment.Center) {
                Text(stringResource(names[i]), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = if (on) c.snow else c.muted)
            }
        }
    }
}

/** "TLV ✈ TBS", the plane pointing on. */
@Composable
private fun LegLine(l: Leg, size: Float) {
    val c = Ski.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(l.fromCode ?: l.fromCity.ifBlank { "—" }, style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = size.sp), color = c.ink)
        Icon(Icons.plane, null, Modifier.size((size * .6f).dp), tint = c.glacier)
        Text(l.toCode ?: l.toCity.ifBlank { "—" }, style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = size.sp), color = c.ink)
        if (l.flight.isNotBlank()) Text(l.flight, Modifier.padding(start = 6.dp), style = Ski.type.small.copy(fontSize = 12.5.sp, textDirection = TextDirection.Ltr), color = c.muted)
    }
}

@Composable
private fun legWhen(l: Leg, ret: Boolean): String = listOfNotNull(
    stringResource(if (ret) R.string.app_pass_ret else R.string.app_pass_out), shortDate(l.date),
    l.departs?.toString()?.let { d -> l.arrives?.let { stringResource(R.string.app_g_dep_arr, d, it.toString()) } ?: d },
).joinToString(" · ")

/**
 * Flights (Q6): a small pass for every flight in the group, with who is on it; flights an admin entered for someone are
 * marked; who has no flight yet, with "I'm on the same flight" for me and "fill in for them" for admins.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Flights(g: Group, myTrip: Trip?, onSame: () -> Unit, onShowMine: () -> Unit, onFillFor: (String) -> Unit) {
    val c = Ski.colors
    val legs = buildList {
        for (m in g.members) {
            val t = m.trip ?: continue
            add(Triple(t.out, false, m)); t.ret?.let { add(Triple(it, true, m)) }
        }
    }.groupBy { (l, ret, _) -> Triple(ret, l.date, l.flight.ifBlank { "${l.fromCode}${l.toCode}" }) }
    val me = stringResource(R.string.app_g_me_suffix)
    val shape = remember { PassShape(seamAtEnd = true, r = 11.dp) }
    for ((key, rows) in legs.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))) {
        val leg = rows.first().first
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).shadow(6.dp, shape).background(c.bpPaper, shape).paperGrain(c.dark)) {
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
                LegLine(leg, 30f)
                Muted(legWhen(leg, key.first), size = 12.5f)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((_, _, m) in rows) Row(Modifier.heightIn(min = 30.dp).background(c.bpPaper2).let { if (m.me) it.border(2.dp, c.accent) else it }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.name + if (m.me) " $me" else "", style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = c.ink)
                        if (m.enteredByAdmin != null) Icon(Icons.edit, null, Modifier.size(13.dp), tint = c.muted)
                    }
                }
                rows.map { it.third }.firstOrNull { it.enteredByAdmin != null }?.let { m ->
                    Muted(stringResource(R.string.app_g_entered_by, m.enteredByAdmin!!, m.name), Modifier.padding(top = 6.dp), 12f)
                }
            }
            Column(Modifier.width(64.dp).fillMaxHeight().background(c.bpPaper2).drawBehind {
                drawLine(c.dash, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(rows.size.toString(), style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 30.sp), color = c.ink)
                Text(stringResource(R.string.app_g_on_flight), style = Ski.type.label.copy(fontSize = 11.sp), color = c.muted)
            }
        }
    }
    for (m in g.members.filter { it.trip == null }) {
        Row(Modifier.fillMaxWidth().drawBehind {
            drawRect(c.dash, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(m.name + if (m.me) " $me" else "", style = Ski.type.bodyBold, color = c.ink)
                Muted(stringResource(R.string.app_g_no_flight_yet), size = 12.5f)
            }
            when {
                m.me && myTrip != null -> Button2(stringResource(R.string.app_g_show_mine), Look.INK, onShowMine, small = true, full = false)
                m.me && g.members.any { it.trip != null } -> PrimaryButton(stringResource(R.string.app_g_same_flight), null, onSame, full = false)
                !m.me && g.admin && g.members.any { it.trip != null } -> Button2(stringResource(R.string.app_g_fill_for), Look.GHOST, { onFillFor(m.userId) }, small = true, full = false)
            }
        }
    }
    Note(stringResource(R.string.app_g_flights_private), Icons.lock)
}

/** Meetups (Q8): every meetup of the group, by day, who set it; a new one is picked on the meeting-point map. */
@Composable
private fun Meetups(g: Group, now: LocalDateTime, stationName: (String) -> String?, onNew: () -> Unit) {
    val c = Ski.colors
    val zone = ZoneId.of("Asia/Tbilisi")
    val byDay = g.meetups.sortedBy { it.at }.groupBy { it.at.atZone(zone).toLocalDate() }
    if (byDay.isEmpty()) Muted(stringResource(R.string.app_g_no_meetups), size = 14f)
    val next = g.meetups.filter { it.at.atZone(zone).toLocalDateTime() > now }.minByOrNull { it.at }
    for ((day, list) in byDay) {
        Text(shortDate(day), style = Ski.type.label.copy(fontSize = 13.sp), color = c.muted)
        for (m in list) {
            val at = m.at.atZone(zone)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).shadow(6.dp, RectangleShape).background(c.bpPaper).paperGrain(c.dark)
                .let { if (m == next) it.border(2.dp, c.accent) else it }) {
                Column(Modifier.width(76.dp).fillMaxHeight().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Muted(shortDate(at.toLocalDate()), size = 12f)
                    Text("%02d:%02d".format(at.hour, at.minute), style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 32.sp), color = c.ink)
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val lift = stationName(m.station)
                    val place = if (lift == null) m.station else stringResource(if (m.station.endsWith("t")) R.string.meet_where_top_station else R.string.meet_where_bottom_station, lift)
                    Text(place, style = Ski.type.bodyBold.copy(fontSize = 15.5.sp), color = c.ink)
                    m.byName?.let { Muted(stringResource(R.string.app_g_set_by, it), size = 12.5f) }
                    m.note?.let { Muted(it, size = 12.5f) }
                }
            }
        }
    }
    Button2(stringResource(R.string.app_g_new_meetup), Look.INK, onNew, icon = Icons.pin)
    Note(stringResource(R.string.app_g_meetups_note), Icons.bell)
}

/** High scores (Q9): the best of each member, per game. */
@Composable
private fun Scores(api: GroupApi, g: Group) {
    val c = Ski.colors
    var game by rememberSaveable { mutableStateOf(GAMES.first().first) }
    var rows by remember { mutableStateOf<List<Score>?>(null) }
    val r = rememberRunner()
    LaunchedEffect(game) { rows = null; r.run { rows = api.leaderboard(g.id, game) } }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, name) in GAMES) {
            val on = key == game
            Box(Modifier.heightIn(min = 44.dp).background(if (on) c.accent else c.paper).let { if (on) it else it.border(1.dp, c.rule) }
                .clickable(role = A11y.Tab) { game = key }.semantics { selected = on }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(name), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = if (on) c.onAccent else c.ink)
            }
        }
    }
    val list = rows
    if (list == null) { if (r.busy) Muted(stringResource(R.string.app_loading)) else ErrorLine(r) }
    else if (list.isEmpty()) Muted(stringResource(R.string.app_g_no_scores), size = 14f)
    else Column(Modifier.shadow(6.dp, RectangleShape)) {
        for ((i, s) in list.withIndex()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).background(if (i == 0) Color(0x33F4B942) else c.paper).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text((i + 1).toString(), Modifier.width(28.dp), style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 30.sp), color = if (i == 0) Color(0xFFF4B942) else c.ink)
                Text(s.name + if (s.me) " " + stringResource(R.string.app_g_me_suffix) else "", Modifier.weight(1f), style = Ski.type.bodyBold, color = c.ink)
                Text("%,d".format(s.best), style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 30.sp, textDirection = TextDirection.Ltr), color = c.ink)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
        }
    }
    Note(stringResource(R.string.app_g_scores_note), Icons.trophy)
}

/**
 * Members and management (Q10): requests waiting for an admin, the members with their roles, and the group's actions.
 * Leaving and deleting ask twice.
 */
@Composable
private fun Members(api: GroupApi, g: Group, r: Runner, reload: () -> Unit, onInvite: () -> Unit, onEdit: () -> Unit, onLeft: () -> Unit) {
    val c = Ski.colors
    var armed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(armed) { if (armed != null) { kotlinx.coroutines.delay(4000); armed = null } }
    if (g.admin && g.requests.isNotEmpty()) {
        Text(stringResource(R.string.app_g_requests), style = Ski.type.label.copy(fontSize = 13.sp), color = c.muted)
        for (q in g.requests) SnowCard(Color(0xFFF4B942), q.id.hashCode()) {
            Text(stringResource(if (q.reclaim) R.string.app_g_request_reclaim else R.string.app_g_request_join, q.name), style = Ski.type.bodyBold.copy(fontSize = 15.5.sp), color = c.ink)
            Muted(stringResource(if (q.reclaim) R.string.app_g_request_reclaim_sub else R.string.app_g_request_join_sub), size = 12.5f)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.app_g_approve), Icons.check, { r.run { api.decide(q.id, true); reload() } }, Modifier.weight(1f))
                Button2(stringResource(R.string.app_g_reject), Look.GHOST, { r.run { api.decide(q.id, false); reload() } }, Modifier.weight(1f), icon = Icons.x, small = true)
            }
        }
    }
    Column {
        for (m in g.members) {
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).drawBehind { drawRect(c.rule, Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(m.name, m.role == Role.ADMIN)
                Column(Modifier.weight(1f)) {
                    Text(m.name + if (m.me) " " + stringResource(R.string.app_g_me_suffix) else "", style = Ski.type.bodyBold, color = c.ink)
                    Muted(stringResource(if (m.role == Role.ADMIN) R.string.app_g_role_admin else R.string.app_g_role_member), size = 12.5f)
                }
                if (g.admin && !m.me) Box {
                    val label = stringResource(R.string.app_g_actions_on, m.name)
                    Box(Modifier.size(44.dp).clickable(role = A11y.Button) { menu = true }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                        Icon(Icons.more, null, Modifier.size(22.dp), tint = c.muted)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        if (m.role == Role.MEMBER) DropdownMenuItem({ Text(stringResource(R.string.app_g_make_admin)) }, { menu = false; r.run { api.setRole(g.id, m.userId, Role.ADMIN); reload() } })
                        else DropdownMenuItem({ Text(stringResource(R.string.app_g_unmake_admin)) }, { menu = false; r.run { api.setRole(g.id, m.userId, Role.MEMBER); reload() } })
                        DropdownMenuItem({ Text(stringResource(R.string.app_g_remove), color = c.red) }, { menu = false; r.run { api.removeMember(g.id, m.userId); reload() } })
                    }
                }
            }
        }
    }
    if (g.admin) Muted(stringResource(R.string.app_g_menu_note), size = 13f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (g.admin) QuietButton(stringResource(R.string.app_g_name_dates), onEdit)
        QuietButton(stringResource(R.string.app_g_invite_settings_link), onInvite)
        QuietButton(stringResource(if (armed == "leave") R.string.app_g_leave_confirm else R.string.app_g_leave), {
            if (armed == "leave") r.run { api.leave(g.id); onLeft() } else armed = "leave"
        }, danger = armed == "leave")
        if (g.admin) Button2(stringResource(if (armed == "delete") R.string.app_g_delete_confirm else R.string.app_g_delete), Look.DANGER, {
            if (armed == "delete") r.run { api.deleteGroup(g.id); onLeft() } else armed = "delete"
        }, icon = Icons.trash)
    }
}

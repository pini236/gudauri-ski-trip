package io.github.pini236.skiapp.redesign

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.meet.Meet
import io.github.pini236.skiapp.meet.MeetHere
import io.github.pini236.skiapp.meet.MeetPlan
import io.github.pini236.skiapp.meet.Preset
import io.github.pini236.skiapp.meet.Station
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.hypot

/**
 * The completion round, part 2 (Q04 and Q05, design/handoff/2026-11-21-redesign-around, section 5): the meeting point on
 * the live mountain. The rules are today's (meet/Meet.kt): only the lifts' stations, the same ids and links, the same
 * saving in a group. New are the look, the four times on offer and the day and time the point opens on.
 */
internal object LiveMeetRules {
    /** The four times of Q05's row (the handoff), then "other". */
    val TIMES = listOf("09:30", "12:00", "13:00", "16:30")

    /**
     * The day and time a station opens on with none chosen (section 5): today if it is a ski day, else the first ski day
     * not yet past; the first of the four at least 15 minutes after now (on today), and with none, 09:30 the next day.
     * [days] are the meeting point's days (Meet.days: the trip's ski days, or a week from today), [now] Gudauri's.
     */
    fun default(days: List<LocalDate>, now: Instant): Pair<LocalDate, LocalTime> {
        val here = now.atOffset(Meet.GUDAURI)
        val today = here.toLocalDate()
        val day = days.firstOrNull { it == today } ?: days.firstOrNull { it > today } ?: days.lastOrNull() ?: today
        if (day != today) return day to Meet.parseTime(TIMES.first())
        val soon = here.toLocalTime().plusMinutes(15)
        val t = TIMES.map { Meet.parseTime(it) }.firstOrNull { !it.isBefore(soon) && here.toLocalTime().isBefore(LocalTime.of(23, 45)) }
        return if (t != null) day to t else (days.firstOrNull { it > today } ?: today.plusDays(1)) to Meet.parseTime(TIMES.first())
    }

    /** Pins closer than [gap] on the screen are one (the handoff: 13): each group keeps its stations, the first leads. */
    fun clusters(pts: List<Pair<Station, Offset>>, gap: Float): List<List<Pair<Station, Offset>>> {
        val out = ArrayList<MutableList<Pair<Station, Offset>>>()
        for (p in pts) {
            val near = out.firstOrNull { c -> c.any { hypot(it.second.x - p.second.x, it.second.y - p.second.y) < gap } }
            if (near != null) near += p else out += mutableListOf(p)
        }
        return out
    }

    /** The station nearest a tap at [x], [y] within [reach] pixels, or none (a tap on the mountain away from them). */
    fun nearest(pts: List<Pair<Station, Offset>>, x: Float, y: Float, reach: Float): Station? =
        pts.minByOrNull { hypot(it.second.x - x, it.second.y - y) }?.takeIf { hypot(it.second.x - x, it.second.y - y) <= reach }?.first

    /** The station a lift's "meet here" opens on (Q03): its top. */
    fun topOf(plan: MeetPlan, lift: String): Station? = plan.stations.firstOrNull { s -> s.ends.any { it.lift.name == lift && it.top } }
}

/** The meeting point's state on the mountain: open or not, and the station, day and time chosen. */
internal class MeetUi {
    var open by mutableStateOf(false)
    var sid by mutableStateOf<String?>(null)
    var day by mutableStateOf<LocalDate?>(null)
    var time by mutableStateOf<LocalTime?>(null)
    var preset by mutableStateOf<Preset?>(null)
    /** What a clear can bring back for 5 seconds (the station, the preset), with its count so a new clear restarts it. */
    var undo by mutableStateOf<Pair<String, Preset?>?>(null)
    var undoN by mutableStateOf(0)
    /** The flag's drop, counted so the same station picked again drops again. */
    var drops by mutableStateOf(0)
    /** Opened from a group's flag on the mountain: already in a group, so no "save in the group". */
    var fromGroup by mutableStateOf(false)
    /** When the group's copy shown was saved, if the phone was offline (the architect's condition 4). */
    var offlineAt by mutableStateOf<Long?>(null)
    /** Q06 open over the mountain. */
    var share by mutableStateOf(false)
}

/** The flag's words and its sub-line under the station's name (Q05): "the top station · 2,705 m". */
@Composable
private fun stationSub(s: Station): String {
    val alt = s.h?.let { java.text.NumberFormat.getIntegerInstance().format(it) } ?: "—"
    return stringResource(if (s.ends.any { !it.top }) R.string.app_meet_station_bottom else R.string.app_meet_station_top, alt)
}

/** "In 42 minutes", "in 1:05 hours", "in 3 days", "already passed" (the site's countdown, Meet.left). */
@Composable
internal fun countdownText(left: Meet.Left): String = when (left) {
    Meet.Left.Passed -> stringResource(R.string.meet_countdown_passed)
    is Meet.Left.Minutes -> pluralStringResource(R.plurals.meet_countdown_minutes, left.n, left.n.toString())
    is Meet.Left.Hours -> stringResource(R.string.meet_countdown_hours, left.hm)
    is Meet.Left.Days -> pluralStringResource(R.plurals.meet_countdown_days, left.n, left.n.toString())
}

/** A day's chip, "ג׳ 12.1" or "Tue 12.1". */
private fun dayChip(d: LocalDate, lang: String, fmt: (String, String) -> String): String {
    val wd = if (lang == "he") DateTimeFormatter.ofPattern("EEEEE", Locale.forLanguageTag("he")).format(d)
        else runCatching { DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag(lang)).format(d) }.getOrElse { "" }
    return fmt(wd, "${d.dayOfMonth}.${d.monthValue}")
}

/** The flag in the pill (a pole and a cloth), 20 by 20, in [c]. */
@Composable
private fun FlagIcon(c: Color) {
    Canvas(Modifier.size(20.dp)) {
        val d = density
        drawRect(c, Offset(4 * d, 2 * d), androidx.compose.ui.geometry.Size(2 * d, 16 * d))
        drawPath(Path().apply { moveTo(6 * d, 2.5f * d); lineTo(17 * d, 2.5f * d); lineTo(14 * d, 6.5f * d); lineTo(17 * d, 10.5f * d); lineTo(6 * d, 10.5f * d); close() }, c)
    }
}

/**
 * The flag on the mountain (the handoff's section 1): a pole 3 by 50 its foot on the station, the cloth 26 high in the
 * accent with the time, a swallowtail at its end; always left to right, the pole on the left. [drop] 0 to 1 of the
 * 18-point fall (1 is down).
 */
internal fun DrawScope.meetFlag(at: Offset, time: String, drop: Float, acc: Color, onAcc: Color, measurer: TextMeasurer) {
    val d = density
    val y0 = at.y - (1f - drop) * 18 * d
    val pole = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(at.x - 1.5f * d, y0 - 50 * d, at.x + 1.5f * d, y0, 2 * d, 2 * d)) }
    drawPath(pole, Color(0xFF0E2233))
    drawPath(pole, Color(0xB3FFFFFF), style = Stroke(1 * d))
    val tx = measurer.measure(time, TextStyle(fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = onAcc, fontFeatureSettings = "tnum"))
    val w = tx.size.width + 22 * d; val top = y0 - 50 * d; val h = 26 * d
    val cloth = Path().apply { moveTo(at.x + 1.5f * d, top); lineTo(at.x + 1.5f * d + w, top); lineTo(at.x + 1.5f * d + w - 7 * d, top + h / 2); lineTo(at.x + 1.5f * d + w, top + h); lineTo(at.x + 1.5f * d, top + h); close() }
    drawPath(cloth, acc)
    drawText(tx, topLeft = Offset(at.x + 1.5f * d + 8 * d, top + (h - tx.size.height) / 2))
    // the foot on the station: a white disc with the pole's ring
    drawCircle(Color.White, 7 * d, at); drawCircle(Color(0xFF0E2233), 7 * d, at, style = Stroke(3 * d))
}

/** A pin on a station (section 1): 14 across, white, a ring of 3 in the accent; the chosen 18 with a dark ring. */
internal fun DrawScope.meetPin(at: Offset, acc: Color, alpha: Float) {
    val d = density
    drawCircle(Color(0x59000000), 7.5f * d, at + Offset(0f, 1 * d), alpha = alpha)
    drawCircle(Color.White, 7 * d, at, alpha = alpha)
    drawCircle(acc, 5.5f * d, at, alpha = alpha, style = Stroke(3 * d))
}

/** Q04's pill: the flag, "where do we meet? Tap a station", and an X that closes. Q05's: the countdown, an X that clears. */
@Composable
internal fun MeetPill(text: String, k: SheetInk, bg: Color, line: Color, closeLabel: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(bg).border(1.dp, line, RoundedCornerShape(26.dp))
        .padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FlagIcon(k.ink)
        Text(text, Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink, maxLines = 1)
        Box(Modifier.size(44.dp).clip(CircleShape).semantics { contentDescription = closeLabel; role = Role.Button }.clickable(onClick = onClose),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(14.dp)) {
                val s = 2 * density
                drawLine(k.ink, Offset.Zero, Offset(size.width, size.height), s); drawLine(k.ink, Offset(size.width, 0f), Offset(0f, size.height), s)
            }
        }
    }
}

/** Q04's sheet: "or in one tap" and the group's three spots, each a row of 52 with its time, name, station and an arrow. */
@Composable
internal fun MeetWhereSheet(plan: MeetPlan, k: SheetInk, haptics: Haptics, onPreset: (Preset, Station) -> Unit) {
    LiveSheet(k) {
        Text(stringResource(R.string.meet_one_tap), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.ink)
        Column {
            for ((i, p) in Preset.entries.withIndex()) {
                val s = plan.preset(p) ?: continue
                val name = stringResource(when (p) { Preset.MORNING -> R.string.meet_preset_morning_lift; Preset.NOON -> R.string.meet_preset_noon; Preset.END -> R.string.meet_preset_end_of_day })
                val where = stringResource(if (p.top) R.string.meet_where_top_station else R.string.meet_where_bottom_station, "⁦${p.lift}⁩")
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { role = Role.Button; contentDescription = "${p.time} $name, $where" }
                    .clickable { haptics.tick(.3f); onPreset(p, s) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(66.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, k.line, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                        Text(p.time, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink, style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = k.ink)
                        Text(where, fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
                    }
                    Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›", fontFamily = Plex, fontSize = 22.sp, color = k.ink2)
                }
            }
        }
    }
}

/** A chip of a day or a time (section 1): 44 high, at least 62 wide, a border; chosen in the accent, bold. */
@Composable
private fun Chip(text: String, on: Boolean, k: SheetInk, desc: String = text, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).widthIn(min = 62.dp).clip(RoundedCornerShape(22.dp))
        .then(if (on) Modifier.background(k.acc) else Modifier.border(1.dp, k.line, RoundedCornerShape(22.dp)))
        .semantics { role = Role.Button; contentDescription = desc }.clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Plex, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp, color = if (on) Color.White else k.ink,
            maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

/**
 * Q05's sheet: the station's name and where (25 bold), the time big at the end (44, light); the days, the times and
 * "other"; WhatsApp, the picture and the link; and "save in the group" for someone in one.
 */
@Composable
internal fun MeetSetSheet(here: MeetHere, s: Station, day: LocalDate, time: LocalTime, days: List<LocalDate>, k: SheetInk, haptics: Haptics, scene: io.github.pini236.skiapp.map.MapScene,
                          offlineAt: Long? = null, fromGroup: Boolean = false, onImage: () -> Unit = {}, onDay: (LocalDate) -> Unit, onTime: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val lang = Lang.current(context.resources)
    val hhmm = "%02d:%02d".format(time.hour, time.minute)
    var askTime by remember { mutableStateOf(false) }
    LiveSheet(k) {
        offlineAt?.let { at ->
            val hm = Instant.ofEpochMilli(at).atOffset(Meet.GUDAURI).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
            Text(stringResource(R.string.app_g_offline, hm), fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("⁦${s.name}⁩", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = k.ink, maxLines = 1)
                Text(stationSub(s), fontFamily = Plex, fontSize = 13.sp, color = k.ink2)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(hhmm, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 44.sp, color = k.ink, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in days) {
                val t = dayChip(d, lang.tag) { wd, date -> context.getString(R.string.app_meet_day_chip, wd, date) }
                Chip(t, d == day, k) { haptics.tick(.2f); onDay(d) }
            }
        }
        val other = LiveMeetRules.TIMES.none { it == hhmm }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in LiveMeetRules.TIMES) Chip(t, t == hhmm, k) { haptics.tick(.2f); onTime(Meet.parseTime(t)) }
            val otherWord = stringResource(R.string.app_meet_time_other)
            Chip(if (other) hhmm else otherWord, other, k, desc = stringResource(R.string.meet_other_time)) { askTime = true }
        }
        MeetShareRow(s, day, time, lang, k, haptics, onImage)
        if (!fromGroup) here.save?.let { MeetSaveButton(it, s, Meet.at(day, time), k, haptics) }
    }
    if (askTime) io.github.pini236.skiapp.trip.TimeDialog(stringResource(R.string.meet_other_time), time,
        onPick = { t -> askTime = false; onTime(t) }, onDismiss = { askTime = false })
}

/** WhatsApp (the main button), the picture and the link (Q05, row 4); measured once each went, as today's meeting point. */
@Composable
private fun MeetShareRow(s: Station, day: LocalDate, time: LocalTime, lang: Lang.Language, k: SheetInk, haptics: Haptics, onImage: () -> Unit) {
    val context = LocalContext.current
    val hhmm = "%02d:%02d".format(time.hour, time.minute)
    val link = Meet.shared(s.id, time, day)
    val dayText = io.github.pini236.skiapp.meet.dayLabel(day, lang.tag)
    val where = io.github.pini236.skiapp.meet.stationWhere(s).replace("⁨", "").replace("⁩", "")
    val alt = s.h?.let { stringResource(R.string.meet_share_alt_suffix, String.format(Locale.US, "%,d", it)) } ?: ""
    val message = stringResource(R.string.meet_share_message, s.name, dayText, hhmm, where, alt, link)
    val title = stringResource(R.string.meet_share_title, s.name)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2200); copied = false } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(k.acc)
            .semantics { role = Role.Button }.clickable {
                haptics.tick(.4f)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=" + Uri.encode(message)))) }
                    .onSuccess { Telemetry.event("meet_share", mapOf("method" to "whatsapp")) }
            }, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.meet_send_wa), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 1)
        }
        // the picture (Q06): its own screen
        SecondaryButton(stringResource(R.string.app_meet_btn_image), k) { haptics.tick(.3f); onImage() }
        SecondaryButton(stringResource(if (copied) R.string.common_link_copied else R.string.app_meet_btn_link), k) {
            haptics.tick(.3f)
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title, link))
            Telemetry.event("meet_share", mapOf("method" to "copy")); copied = true; Qa.log("redesign meet link copied")
        }
    }
}

/**
 * "Save in the group" (row 5): 44 high, only for someone in a group; with several, "which group?" and their names; after
 * saving "saved in the group" with a check, and the button off. Offline it waits in the queue, as today.
 */
@Composable
private fun MeetSaveButton(save: io.github.pini236.skiapp.meet.MeetSave, s: Station, at: Instant, k: SheetInk, haptics: Haptics) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<io.github.pini236.skiapp.group.GroupSummary>?>(null) }
    var choose by remember { mutableStateOf(false) }
    var saved by remember(s.id, at) { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (save.api.ready && save.api.me() != null) groups = runCatching { save.api.myGroups() }.getOrNull() }
    val gs = groups.orEmpty()
    if (gs.isEmpty()) return
    fun go(id: String) = scope.launch {
        runCatching { save.api.addMeetup(id, s.id, at) }.onSuccess {
            saved = true; choose = false; Qa.log("meetup saved in $id"); save.after()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, k.line, RoundedCornerShape(22.dp))
            .semantics { role = Role.Button }.clickable(enabled = !saved) {
                haptics.tick(.3f)
                if (gs.size == 1) go(gs[0].id) else choose = !choose
            }, contentAlignment = Alignment.Center) {
            Text(if (saved) "✓ " + stringResource(R.string.meet_saved_group) else stringResource(R.string.meet_save_group),
                fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = if (saved) k.good else k.ink)
        }
        if (choose && !saved) {
            Text(stringResource(R.string.meet_pick_group), fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
            for (g in gs) Chip(g.name, false, k) { go(g.id) }
        }
    }
}

/** The undo bar after a clear (as today): "the choice was cleared", "undo", 5 seconds. */
@Composable
internal fun MeetUndo(onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xF00E2233)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.meet_cleared), Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.White)
        Box(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF4B942)).semantics { role = Role.Button }
            .clickable(onClick = onUndo).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.meet_undo), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF13233A))
        }
    }
}

/** Every station's place on the screen this frame (a little above the snow), those in front of the camera. */
internal fun stationsOnScreen(view: LiveView, plan: MeetPlan): List<Pair<Station, Offset>> =
    plan.stations.mapNotNull { s -> view.toScreen(s.x, s.y, 4f)?.let { s to Offset(it[0], it[1]) } }

/** The pins of Q04 and Q05 as tappable 44-point places, for touch and for a screen reader. */
@Composable
internal fun MeetPinTargets(clusters: List<List<Pair<Station, Offset>>>, chosen: String?, onPick: (Station) -> Unit) {
    for (c in clusters) {
        val (s, p) = c.first()
        if (c.any { it.first.id == chosen }) continue
        val names = c.joinToString(", ") { it.first.name }
        Box(Modifier.offset { IntOffset((p.x - 22.dp.toPx()).toInt(), (p.y - 22.dp.toPx()).toInt()) }.size(44.dp)
            .semantics { role = Role.Button; contentDescription = names }.clickable { onPick(s) })
    }
    Spacer(Modifier.size(0.dp))
}

/** The flag's tappable place (a second tap on it clears), read as "meeting at 13:00, Goodaura". */
@Composable
internal fun MeetFlagTarget(at: Offset, desc: String, onTap: () -> Unit) {
    Box(Modifier.offset { IntOffset((at.x - 12.dp.toPx()).toInt(), (at.y - 58.dp.toPx()).toInt()) }.size(width = 76.dp, height = 64.dp)
        .semantics { role = Role.Button; contentDescription = desc }.clickable(onClick = onTap))
}

/** The flag's fall when it is planted (320 ms with a small spring), or at once with reduced motion. */
@Composable
internal fun rememberDrop(key: Any?, reduced: Boolean): Animatable<Float, *> {
    val a = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        if (reduced) { a.snapTo(1f); return@LaunchedEffect }
        a.snapTo(0f)
        a.animateTo(1f, tween(320, easing = { t -> val c = 1.70158f * 1.15f; val u = t - 1; 1 + (c + 1) * u * u * u + c * u * u }))
    }
    return a
}

/** Today's meetups of my groups, from the phone's copy (Sync), and when a copy shown offline was saved. */
internal class GroupMeets(val list: List<io.github.pini236.skiapp.meet.MeetFlags.In> = emptyList(), val offlineAt: Long? = null)

/**
 * The flags of my groups on the mountain (the architect, 24.11.2026): only the phone's copy, the same flows the group
 * page reads; no new channel and no new call. Live only while a group page is open, as today.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun groupMeets(sync: io.github.pini236.skiapp.server.Sync): kotlinx.coroutines.flow.Flow<GroupMeets> =
    sync.myGroups.flatMapLatest { m ->
        if (m.groups.isEmpty()) kotlinx.coroutines.flow.flowOf(GroupMeets())
        else kotlinx.coroutines.flow.combine(m.groups.map { sync.group(it.id).state }) { arr ->
            GroupMeets(arr.flatMap { sn -> sn.meetups.map { io.github.pini236.skiapp.meet.MeetFlags.In(it.id, sn.groupId, it.station, it.at) } },
                arr.filter { it.offline }.mapNotNull { it.readAt }.minOrNull())
        }
    }


/**
 * Q06: the picture to share, a full screen over the mountain: back and "picture to share" at the top, the picture 326
 * across (corner 18), one line under it, and at the bottom "share" (the system's sheet) and "save on the phone" (Android
 * 10 and up, no permission; before that the button is not shown). Drawn off the main thread; a file with a place in its
 * metadata is never shared (the architect's condition 6).
 */
@Composable
internal fun MeetShareScreen(s: Station, day: LocalDate, time: LocalTime, night: Boolean, haptics: Haptics, scene: io.github.pini236.skiapp.map.MapScene, onBack: () -> Unit) {
    val context = LocalContext.current
    val lang = Lang.current(context.resources)
    val k = remember(night) { SheetInk(night) }
    val page = if (night) Color(0xFF0B1220) else Color(0xFFEEF2F7)
    val hhmm = "%02d:%02d".format(time.hour, time.minute)
    val at = stringResource(R.string.app_meet_share_at, hhmm)
    val place = "${s.name}, ${stationSub(s)}"
    val dayText = runCatching { DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag(lang.tag)).format(day) }.getOrDefault("") + ", ${day.dayOfMonth}.${day.monthValue}"
    val link = Meet.shared(s.id, time, day)
    val where = io.github.pini236.skiapp.meet.stationWhere(s).replace("⁨", "").replace("⁩", "")
    val alt = s.h?.let { stringResource(R.string.meet_share_alt_suffix, String.format(Locale.US, "%,d", it)) } ?: ""
    val message = stringResource(R.string.meet_share_message, s.name, io.github.pini236.skiapp.meet.dayLabel(day, lang.tag), hhmm, where, alt, link)
    val title = stringResource(R.string.meet_share_title, s.name)
    val scope = rememberCoroutineScope()
    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(s.id, day, time, night) {
        bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            OnMountain.load(context, night, scene.terrain::elev)?.let { m ->
                MeetShareImage.draw(m, s, scene.runs, MeetShareImage.Words(at, "⁦${s.name}⁩, ${stationSub0(context, s)}", dayText, "Gudi · Gudauri", hhmm), night,
                    Lang.typeface(context, lang, true), Lang.typeface(context, lang, false), lang.rtl)
            }
        }
        Qa.log("redesign meet image ${if (bmp != null) "drawn" else "none"}")
    }
    androidx.activity.compose.BackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(page).then(Modifier.padding(horizontal = 20.dp)).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(44.dp).semantics { role = Role.Button; contentDescription = back }.clickable { haptics.tick(.2f); onBack() }, contentAlignment = Alignment.Center) {
                Text(if (LocalLayoutDirection.current == LayoutDirection.Rtl) "›" else "‹", fontFamily = Plex, fontSize = 24.sp, color = k.ink)
            }
            Text(stringResource(R.string.app_meet_share_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = k.ink)
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.width(326.dp).height(407.5.dp).clip(RoundedCornerShape(18.dp)).background(k.line)
            .semantics { contentDescription = "$at. $place. $dayText" }) {
            bmp?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.matchParentSize()) }
        }
        Text(stringResource(R.string.app_meet_share_note), Modifier.width(326.dp).padding(top = 12.dp), fontFamily = Plex, fontSize = 13.5.sp, color = k.ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(k.acc).semantics { role = Role.Button }
                .clickable(enabled = bmp != null) {
                    haptics.tick(.4f)
                    val b = bmp ?: return@clickable
                    scope.launch {
                        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MeetShareImage.write(java.io.File(context.cacheDir, "share"), b) }
                        if (f == null) { Qa.log("redesign meet image refused"); return@launch }
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".share", f)
                        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, message)
                            .putExtra(Intent.EXTRA_TITLE, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onSuccess { Telemetry.event("meet_share", mapOf("method" to "image")) }
                    }
                }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.app_meet_share_btn), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) SecondaryButton(stringResource(R.string.app_meet_share_save), k) {
                haptics.tick(.3f)
                val b = bmp ?: return@SecondaryButton
                scope.launch { val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { saveMeetPicture(context, b) }; Qa.log("redesign meet image saved $ok") }
            }
        }
    }
    // the emulator's run: the file as it would leave, checked for a place in its metadata
    LaunchedEffect(bmp) {
        val b = bmp ?: return@LaunchedEffect
        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MeetShareImage.write(java.io.File(context.cacheDir, "share"), b) }
        Qa.log("redesign meet image file ${if (f != null) "clean ${f.length()}" else "refused"}")
    }
}

/** The station's line without Compose ("the top station · 2,705 m"), for drawing off the main thread. */
private fun stationSub0(ctx: Context, s: Station): String {
    val alt = s.h?.let { java.text.NumberFormat.getIntegerInstance().format(it) } ?: "—"
    return ctx.getString(if (s.ends.any { !it.top }) R.string.app_meet_station_bottom else R.string.app_meet_station_top, alt)
}

/** Into the phone's pictures (Android 10 and up, no permission). */
private fun saveMeetPicture(ctx: Context, b: android.graphics.Bitmap): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 29) return false
    val v = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "gudi-meet-${System.currentTimeMillis()}.png"); put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Gudi")
    }
    val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
    return runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }.getOrDefault(false)
}

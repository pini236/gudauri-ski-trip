package io.github.pini236.skiapp.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.group.Sheet
import io.github.pini236.skiapp.ui.Field
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.PrimaryButton
import io.github.pini236.skiapp.ui.Ski
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * Round 20 (decision 65): the pass is the form. The empty pass has TLV and TBS written on it and asks "when do you
 * fly?": one calendar, two taps (out and back), the ski days shaded between them, and the pass fills in. The flight
 * number and the times wait on it as dashed "+" fields; each field opens only its own sheet. Only the outbound date is
 * required, as before; the full form stays behind "edit" (deleting, ski days by hand).
 */
object PassEdits {
    /** One tap on the calendar: the first picks the way out, the second the way back; a tap before the way out, or a third, starts again. */
    fun tap(out: LocalDate?, ret: LocalDate?, day: LocalDate): Pair<LocalDate, LocalDate?> = when {
        out == null || ret != null || day < out -> day to null
        else -> out to day
    }

    /** A new trip from the calendar: Tel Aviv to Tbilisi, as the form starts ([tlv], [tbs]: "TLV · Tel Aviv"). */
    fun newTrip(out: LocalDate, ret: LocalDate?, tlv: String, tbs: String) =
        Trip(Leg(out, from = tlv, to = tbs), ret?.let { Leg(it, from = tbs, to = tlv) })

    /** New dates for a trip: each leg keeps its flight and times; a new return is the way out reversed; the ski days follow the flights again. */
    fun Trip.withDates(o: LocalDate, r: LocalDate?): Trip =
        copy(out = out.copy(date = o), ret = r?.let { d -> (ret ?: Leg(d, from = out.to, to = out.from)).copy(date = d) }, ski = null)

    /** The way out's airports; the way back is the same reversed. */
    fun Trip.withPlaces(from: String, to: String): Trip =
        copy(out = out.copy(from = from, to = to), ret = ret?.copy(from = to, to = from))

    fun Trip.withLeg(isRet: Boolean, f: (Leg) -> Leg): Trip = if (isRet) copy(ret = ret?.let(f)) else copy(out = f(out))
}

/** Which field of the pass is being filled: the dates (the calendar), an airport, a flight number or a time. */
sealed interface PassEdit {
    data object Dates : PassEdit
    data class Place(val from: Boolean) : PassEdit
    data class Flight(val ret: Boolean) : PassEdit
    data class Time(val ret: Boolean, val departs: Boolean) : PassEdit
}

/** The sheet of one field of the pass, over home. [trip] is null for the empty pass (only the dates then). */
@Composable
fun BoxScope.PassEditor(edit: PassEdit, trip: Trip?, today: LocalDate, onSave: (Trip) -> Unit, onDismiss: () -> Unit) {
    with(PassEdits) {
        val tlv = "TLV · " + stringResource(R.string.ticket_city_tlv)
        val tbs = "TBS · " + stringResource(R.string.ticket_city_tbs)
        when (edit) {
            PassEdit.Dates -> CalendarSheet(trip, today, { o, r -> onSave(trip?.withDates(o, r) ?: newTrip(o, r, tlv, tbs)); onDismiss() }, onDismiss)
            is PassEdit.Place -> if (trip != null) {
                val o = trip.out
                if (edit.from) PlaceSheet(stringResource(R.string.app_trip_place_from), o.from, listOf("TLV"), { onSave(trip.withPlaces(it, o.to)); onDismiss() }, onDismiss)
                else PlaceSheet(stringResource(R.string.app_trip_place_to), o.to, listOf("TBS", "KUT", "BUS"), { onSave(trip.withPlaces(o.from, it)); onDismiss() }, onDismiss)
            }
            is PassEdit.Flight -> if (trip != null) FlightSheet((if (edit.ret) trip.ret else trip.out)?.flight.orEmpty(), { f ->
                onSave(trip.withLeg(edit.ret) { it.copy(flight = f) }); onDismiss()
            }, onDismiss)
            is PassEdit.Time -> if (trip != null) {
                val leg = (if (edit.ret) trip.ret else trip.out) ?: return
                val title = stringResource(if (edit.ret) R.string.app_pass_ret else R.string.app_pass_out) + " · " +
                    stringResource(if (edit.departs) R.string.ticket_departs else R.string.ticket_arrives)
                TimeDialog(title, if (edit.departs) leg.departs else leg.arrives ?: leg.departs, { t ->
                    onSave(trip.withLeg(edit.ret) { if (edit.departs) it.copy(departs = t) else it.copy(arrives = t) }); onDismiss()
                }, onDismiss)
            }
        }
    }
}

/**
 * One calendar, two taps (round 20): the way out, then the way back, both in ink; the full ski days between them
 * shaded, as the pass will count them. Days before today cannot be picked. Only the way out is required.
 */
@Composable
private fun BoxScope.CalendarSheet(trip: Trip?, today: LocalDate, onSave: (LocalDate, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val c = Ski.colors
    var out by remember { mutableStateOf(trip?.out?.date) }
    var ret by remember { mutableStateOf(trip?.ret?.date) }
    var month by remember { mutableStateOf(YearMonth.from(out ?: today)) }
    val locale = LocalConfiguration.current.locales[0]
    // the ski days as the pass will count them: the trip's own times, with the new dates
    val ski = remember(out, ret) {
        val o = out ?: return@remember null
        with(PassEdits) { (trip ?: Trip(Leg(o))).withDates(o, ret) }.skiDays()
    }
    Sheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val title = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year
            Text(title, Modifier.weight(1f), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (34f / 44f)), color = c.ink)
            val canBack = month > YearMonth.from(today)
            MonthArrow(Icons.back, stringResource(R.string.trip_month_prev), canBack) { month = month.minusMonths(1) }
            MonthArrow(Icons.forward, stringResource(R.string.trip_month_next), true) { month = month.plusMonths(1) }
        }
        val first = WeekFields.of(locale).firstDayOfWeek
        val days = (0 until 7).map { first.plus(it.toLong()) }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
            for (d in days) Text(d.getDisplayName(TextStyle.NARROW_STANDALONE, locale), Modifier.weight(1f),
                style = Ski.type.small.copy(fontSize = 12.sp, textAlign = TextAlign.Center), color = c.muted)
        }
        val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        for (week in cells.chunked(7)) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until 7) {
                val d = week.getOrNull(i)
                if (d == null) { Spacer(Modifier.weight(1f)); continue }
                val past = d < today
                val flight = d == out || d == ret
                val shaded = !flight && ski != null && d in ski
                Box(Modifier.weight(1f).height(44.dp)
                    .background(if (flight) c.ink else if (shaded) SKI_SHADE else c.paper)
                    .let { if (past) it.alpha(.35f) else it.clickable(role = Role.Button) { PassEdits.tap(out, ret, d).let { (o, r) -> out = o; ret = r } } }
                    .semantics { selected = flight; contentDescription = DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(d) },
                    contentAlignment = Alignment.Center) {
                    Text(d.dayOfMonth.toString(), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = if (flight) c.paper else if (shaded) c.blue else c.ink)
                }
            }
        }
        // the key: the two flights, and how many ski days
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Key(c.ink, stringResource(R.string.trip_legend_flights))
            val n = ski?.let { (it.endInclusive.toEpochDay() - it.start.toEpochDay() + 1).toInt() } ?: 0
            if (n > 0) Key(SKI_SHADE, pluralStringResource(R.plurals.trip_legend_ski, n, n))
        }
        PrimaryButton(stringResource(R.string.app_save), null, { out?.let { o -> onSave(o, ret) } }, Modifier.padding(top = 16.dp).alpha(if (out == null) .5f else 1f))
    }
}

/** The ski days' light blue in the calendar (the canvas's trip-2). */
private val SKI_SHADE = androidx.compose.ui.graphics.Color(0xFFDCE8F6)

@Composable
private fun Key(color: androidx.compose.ui.graphics.Color, text: String) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(color).border(1.dp, Ski.colors.rule))
        Text(text, style = Ski.type.small.copy(fontSize = 12.5.sp), color = Ski.colors.muted)
    }

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) =
    Box(Modifier.size(44.dp).alpha(if (enabled) 1f else .3f).let { if (enabled) it.clickable(role = Role.Button, onClick = onClick) else it }
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = Ski.colors.ink)
    }

/** The flight number, its own small sheet: typed in capitals, saved as it is (empty clears it). */
@Composable
private fun BoxScope.FlightSheet(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Sheet(onDismiss) {
        Text(stringResource(R.string.app_trip_flight_no), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = Ski.colors.ink)
        Field(stringResource(R.string.app_trip_flight_no), f, { f = it.uppercase().take(12) }, Modifier.padding(top = 10.dp, bottom = 12.dp),
            stringResource(R.string.app_trip_flight_hint), ltr = true, caps = true, showLabel = false, focus = focus)
        PrimaryButton(stringResource(R.string.app_save), null, { onSave(f.trim()) })
        Spacer(Modifier.width(1.dp))
    }
}

package io.github.pini236.skiapp.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.dayRange
import io.github.pini236.skiapp.ui.Field
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.PrimaryButton
import io.github.pini236.skiapp.ui.QuietButton
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * "Your trip" (H2, decision 27): the outbound flight (only its date is required) and the return, the full ski days
 * worked out from them, save and delete. No sign-up: it stays on this phone (TripStore).
 *
 * Nothing is typed but the flight numbers (Pini, 2.10.2026): dates from the calendar, times from the clock, airports
 * from a list; the destination starts as Tbilisi, and the way back is the way out reversed.
 */
@Composable
fun TripForm(initial: Trip?, today: LocalDate, onSave: (Trip) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit,
             title: String? = null, intro: String? = null, footer: @Composable () -> Unit = {}) {
    val c = Ski.colors
    val o = initial?.out; val r = initial?.ret
    val tbilisi = "TBS · " + stringResource(R.string.ticket_city_tbs)
    var oDate by rememberSaveable { mutableStateOf(o?.date) }
    var oFlight by rememberSaveable { mutableStateOf(o?.flight.orEmpty()) }
    var oFrom by rememberSaveable { mutableStateOf(o?.from.orEmpty()) }
    var oTo by rememberSaveable { mutableStateOf(if (initial == null) tbilisi else o?.to.orEmpty()) }
    var oDep by rememberSaveable { mutableStateOf(o?.departs) }
    var oArr by rememberSaveable { mutableStateOf(o?.arrives) }
    var rDate by rememberSaveable { mutableStateOf(r?.date) }
    var rFlight by rememberSaveable { mutableStateOf(r?.flight.orEmpty()) }
    var rDep by rememberSaveable { mutableStateOf(r?.departs) }
    var rArr by rememberSaveable { mutableStateOf(r?.arrives) }
    // the ski days set by hand ("שינוי"), or null to follow the flights
    var manual by rememberSaveable { mutableStateOf(initial?.ski != null) }
    var sFrom by rememberSaveable { mutableStateOf(initial?.ski?.start) }
    var sTo by rememberSaveable { mutableStateOf(initial?.ski?.endInclusive) }
    // which picker is open
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var tried by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }

    val errDate = stringResource(R.string.app_trip_err_date)
    val orderBad = oDate != null && rDate != null && rDate!! < oDate!!
    val skiOk = !manual || (sFrom != null && sTo != null && sFrom!! <= sTo!!)
    val flights = oDate?.let { d ->
        Trip(Leg(d, oFlight.trim(), oFrom.trim(), oTo.trim(), oDep, oArr), rDate?.let { Leg(it, rFlight.trim(), oTo.trim(), oFrom.trim(), rDep, rArr) })
    }
    val trip = if (flights != null && !orderBad && skiOk) flights.copy(ski = if (manual) sFrom!!..sTo!! else null) else null
    val pickDate = stringResource(R.string.app_trip_pick_date)
    val pickTime = stringResource(R.string.app_trip_pick_time)
    val pickPlace = stringResource(R.string.app_trip_pick_place)
    val locale = LocalConfiguration.current.locales[0]
    fun day(d: LocalDate?) = d?.let { DateTimeFormatter.ofPattern("EEE · d.M.yyyy", locale).format(it) }
    fun time(t: LocalTime?) = t?.let { TripText.time(it) }

    Box(Modifier.fillMaxSize().background(c.snow)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            TopBar(title ?: stringResource(R.string.app_home_trip), stringResource(R.string.app_cancel), onCancel)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 520.dp).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(intro ?: stringResource(R.string.app_trip_intro), Modifier.padding(bottom = 2.dp), style = Ski.type.small.copy(fontSize = 13.5.sp), color = c.muted)
                    Section(stringResource(R.string.app_pass_out), stringResource(R.string.app_trip_out_note))
                    Pair2(
                        { PickField(stringResource(R.string.app_trip_date), day(oDate), pickDate, Icons.calendar, { pick = "oDate" }, it, ltr = false,
                            error = if (oDate == null && tried) errDate else null) },
                        { Field(stringResource(R.string.app_trip_flight_no), oFlight, { oFlight = it.uppercase().take(12) }, it, stringResource(R.string.app_trip_flight_hint), ltr = true, caps = true) },
                    )
                    Pair2(
                        { PickField(stringResource(R.string.ticket_from), oFrom.ifBlank { null }, pickPlace, Icons.plane, { pick = "oFrom" }, it, ltr = false) },
                        { PickField(stringResource(R.string.ticket_to), oTo.ifBlank { null }, pickPlace, Icons.plane, { pick = "oTo" }, it, ltr = false) },
                    )
                    Pair2(
                        { PickField(stringResource(R.string.ticket_departs), time(oDep), pickTime, Icons.clock, { pick = "oDep" }, it, onClear = { oDep = null }) },
                        { PickField(stringResource(R.string.ticket_arrives), time(oArr), pickTime, Icons.clock, { pick = "oArr" }, it, onClear = { oArr = null }) },
                    )
                    Section(stringResource(R.string.app_pass_ret), stringResource(R.string.app_trip_ret_note))
                    val back = listOf(Leg(today, from = oTo).fromCode ?: oTo.trim(), Leg(today, from = oFrom).fromCode ?: oFrom.trim())
                    if (back.all { it.isNotBlank() }) Text(stringResource(R.string.app_trip_ret_route, back[0], back[1]), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
                    Pair2(
                        { PickField(stringResource(R.string.app_trip_date), day(rDate), pickDate, Icons.calendar, { pick = "rDate" }, it, ltr = false, onClear = { rDate = null },
                            error = if (orderBad) stringResource(R.string.app_trip_err_order) else null) },
                        { Field(stringResource(R.string.app_trip_flight_no), rFlight, { rFlight = it.uppercase().take(12) }, it, stringResource(R.string.app_trip_flight_hint), ltr = true, caps = true) },
                    )
                    Pair2(
                        { PickField(stringResource(R.string.ticket_departs), time(rDep), pickTime, Icons.clock, { pick = "rDep" }, it, onClear = { rDep = null }) },
                        { PickField(stringResource(R.string.ticket_arrives), time(rArr), pickTime, Icons.clock, { pick = "rArr" }, it, onClear = { rArr = null }) },
                    )
                    SkiDays(trip ?: flights, manual, onChange = {
                        // start from what the flights give, so changing a day is one pick
                        flights?.flightSkiDays()?.let { d -> if (sFrom == null) sFrom = d.start; if (sTo == null) sTo = d.endInclusive }
                        manual = true
                    }, onAuto = { manual = false })
                    if (manual) Pair2(
                        { PickField(stringResource(R.string.app_trip_ski_first), day(sFrom), pickDate, Icons.calendar, { pick = "sFrom" }, it, ltr = false,
                            error = if (sFrom == null && tried) errDate else null) },
                        { PickField(stringResource(R.string.app_trip_ski_last), day(sTo), pickDate, Icons.calendar, { pick = "sTo" }, it, ltr = false,
                            error = if (sTo == null && tried) errDate else if (sFrom != null && sTo != null && sTo!! < sFrom!!) stringResource(R.string.app_trip_err_ski_order) else null) },
                    )
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrimaryButton(stringResource(R.string.app_save), Icons.check, { tried = true; if (trip != null) onSave(trip) })
                        if (initial != null) QuietButton(stringResource(if (armed) R.string.app_trip_delete_confirm else R.string.app_trip_delete),
                            { if (armed) onDelete() else armed = true }, danger = armed)
                        footer()
                    }
                }
            }
        }
        val close = { pick = null }
        // each picker says which field it fills: "Outbound · Departs"
        val out = stringResource(R.string.app_pass_out); val ret = stringResource(R.string.app_pass_ret)
        val dateL = stringResource(R.string.app_trip_date); val depL = stringResource(R.string.ticket_departs); val arrL = stringResource(R.string.ticket_arrives)
        fun head(leg: String, field: String) = "$leg · $field"
        when (pick) {
            // the outbound from today on; the return and the ski days within the trip
            "oDate" -> DateDialog(head(out, dateL), oDate, today, today, null, { oDate = it; if (rDate != null && rDate!! < it) rDate = null }, close)
            "rDate" -> DateDialog(head(ret, dateL), rDate, oDate, oDate ?: today, null, { rDate = it }, close)
            "sFrom" -> DateDialog(stringResource(R.string.app_trip_ski_first), sFrom, oDate, oDate, rDate, { sFrom = it }, close)
            "sTo" -> DateDialog(stringResource(R.string.app_trip_ski_last), sTo, sFrom ?: oDate, sFrom ?: oDate, rDate, { sTo = it }, close)
            "oDep" -> TimeDialog(head(out, depL), oDep, { oDep = it }, close)
            "oArr" -> TimeDialog(head(out, arrL), oArr ?: oDep, { oArr = it }, close)
            "rDep" -> TimeDialog(head(ret, depL), rDep, { rDep = it }, close)
            "rArr" -> TimeDialog(head(ret, arrL), rArr ?: rDep, { rArr = it }, close)
            "oFrom" -> PlaceSheet(stringResource(R.string.app_trip_place_from), oFrom, listOf("TLV"), { oFrom = it; close() }, close)
            "oTo" -> PlaceSheet(stringResource(R.string.app_trip_place_to), oTo, listOf("TBS", "KUT", "BUS"), { oTo = it; close() }, close)
        }
    }
}

/** A part of the form: its name in the sign voice, a rule, and a short note at the end. */
@Composable
private fun Section(title: String, note: String) {
    val c = Ski.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
        Box(Modifier.weight(1f).height(1.dp).background(c.rule))
        Text(note, style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
    }
}

@Composable
private fun Pair2(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { a(Modifier.weight(1f)); b(Modifier.weight(1f)) }
}

/** The ski days: worked out from the flights as on the site (Trip.flightSkiDays), or set by hand with "שינוי". */
@Composable
private fun SkiDays(trip: Trip?, manual: Boolean, onChange: () -> Unit, onAuto: () -> Unit) {
    val c = Ski.colors
    val days = trip?.skiDays()
    Row(Modifier.fillMaxWidth().background(c.paper).drawBehind { drawRect(c.accent, Offset.Zero, Size(size.width, 6.dp.toPx())) }
        .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(stringResource(if (manual) R.string.app_trip_ski_manual else R.string.app_trip_ski_calc), style = Ski.type.label.copy(fontSize = 12.sp), color = c.muted)
            Text(
                if (days == null) stringResource(R.string.app_trip_ski_none)
                else pluralStringResource(R.plurals.app_trip_ski_value, trip.skiDayCount(), "\u2066" + dayRange(days) + "\u2069", trip.skiDayCount()),
                style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f), textDirection = TextDirection.Content), color = c.ink,
            )
        }
        Text(stringResource(if (manual) R.string.app_trip_ski_auto else R.string.app_trip_ski_change),
            Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = if (manual) onAuto else onChange).padding(horizontal = 8.dp, vertical = 12.dp),
            style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = c.glacier)
    }
}

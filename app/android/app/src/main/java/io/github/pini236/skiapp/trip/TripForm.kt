package io.github.pini236.skiapp.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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

/**
 * "Your trip" (H2, decision 27): the outbound flight (only its date is required) and the return, the full ski days
 * worked out from them, save and delete. No sign-up: it stays on this phone (TripStore).
 */
@Composable
fun TripForm(initial: Trip?, today: LocalDate, onSave: (Trip) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val c = Ski.colors
    val o = initial?.out; val r = initial?.ret
    var oDate by rememberSaveable { mutableStateOf(TripText.date(o?.date)) }
    var oFlight by rememberSaveable { mutableStateOf(o?.flight.orEmpty()) }
    var oFrom by rememberSaveable { mutableStateOf(o?.from.orEmpty()) }
    var oTo by rememberSaveable { mutableStateOf(o?.to.orEmpty()) }
    var oDep by rememberSaveable { mutableStateOf(TripText.time(o?.departs)) }
    var oArr by rememberSaveable { mutableStateOf(TripText.time(o?.arrives)) }
    var rDate by rememberSaveable { mutableStateOf(TripText.date(r?.date)) }
    var rFlight by rememberSaveable { mutableStateOf(r?.flight.orEmpty()) }
    var rDep by rememberSaveable { mutableStateOf(TripText.time(r?.departs)) }
    var rArr by rememberSaveable { mutableStateOf(TripText.time(r?.arrives)) }
    var tried by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }

    // what the fields say now: values, or what is wrong with them
    val pod = TripText.date(oDate, today); val prd = TripText.date(rDate, today)
    val times = listOf(oDep, oArr, rDep, rArr).map { TripText.time(it) }
    val errDate = stringResource(R.string.app_trip_err_date)
    val errTime = stringResource(R.string.app_trip_err_time)
    fun dateError(v: Any?, required: Boolean): String? = when {
        v === TripText.Bad -> errDate
        v == null && required && tried -> errDate
        else -> null
    }
    val orderBad = pod is LocalDate && prd is LocalDate && prd < pod
    val ok = pod is LocalDate && prd !== TripText.Bad && times.none { it === TripText.Bad } && !orderBad
    val trip = if (ok) Trip(
        Leg(pod as LocalDate, oFlight.trim(), oFrom.trim(), oTo.trim(), times[0] as? java.time.LocalTime, times[1] as? java.time.LocalTime),
        (prd as? LocalDate)?.let { Leg(it, rFlight.trim(), oTo.trim(), oFrom.trim(), times[2] as? java.time.LocalTime, times[3] as? java.time.LocalTime) },
    ) else null

    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding().imePadding()) {
        TopBar(stringResource(R.string.app_home_trip), stringResource(R.string.app_cancel), onCancel)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 520.dp).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_trip_intro), Modifier.padding(bottom = 2.dp), style = Ski.type.small.copy(fontSize = 13.5.sp), color = c.muted)
                Section(stringResource(R.string.app_pass_out), stringResource(R.string.app_trip_out_note))
                Pair2(
                    { Field(stringResource(R.string.app_trip_date), oDate, { oDate = it }, it, stringResource(R.string.app_trip_date_hint), ltr = true, keyboard = KeyboardType.Number, error = dateError(pod, true)) },
                    { Field(stringResource(R.string.app_trip_flight_no), oFlight, { oFlight = it }, it, stringResource(R.string.app_trip_flight_hint), ltr = true) },
                )
                Pair2(
                    { Field(stringResource(R.string.ticket_from), oFrom, { oFrom = it }, it, stringResource(R.string.app_trip_from_hint)) },
                    { Field(stringResource(R.string.ticket_to), oTo, { oTo = it }, it, stringResource(R.string.app_trip_to_hint)) },
                )
                Pair2(
                    { Field(stringResource(R.string.ticket_departs), oDep, { oDep = it }, it, "16:00", ltr = true, keyboard = KeyboardType.Number, error = if (times[0] === TripText.Bad) errTime else null) },
                    { Field(stringResource(R.string.ticket_arrives), oArr, { oArr = it }, it, "20:35", ltr = true, keyboard = KeyboardType.Number, error = if (times[1] === TripText.Bad) errTime else null) },
                )
                Section(stringResource(R.string.app_pass_ret), stringResource(R.string.app_trip_ret_note))
                Pair2(
                    { Field(stringResource(R.string.app_trip_date), rDate, { rDate = it }, it, stringResource(R.string.app_trip_date_hint), ltr = true, keyboard = KeyboardType.Number,
                        error = dateError(prd, false) ?: if (orderBad) stringResource(R.string.app_trip_err_order) else null) },
                    { Field(stringResource(R.string.app_trip_flight_no), rFlight, { rFlight = it }, it, stringResource(R.string.app_trip_flight_hint), ltr = true) },
                )
                Pair2(
                    { Field(stringResource(R.string.ticket_departs), rDep, { rDep = it }, it, "01:35", ltr = true, keyboard = KeyboardType.Number, error = if (times[2] === TripText.Bad) errTime else null) },
                    { Field(stringResource(R.string.ticket_arrives), rArr, { rArr = it }, it, "02:15", ltr = true, keyboard = KeyboardType.Number, error = if (times[3] === TripText.Bad) errTime else null) },
                )
                SkiDays(trip)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrimaryButton(stringResource(R.string.app_save), Icons.check, { tried = true; if (trip != null) onSave(trip) })
                    if (initial != null) QuietButton(stringResource(if (armed) R.string.app_trip_delete_confirm else R.string.app_trip_delete),
                        { if (armed) onDelete() else armed = true }, danger = armed)
                }
            }
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

/** The full ski days, worked out from the flights as on the site (Trip.skiDays). */
@Composable
private fun SkiDays(trip: Trip?) {
    val c = Ski.colors
    val days = trip?.skiDays()
    Column(Modifier.fillMaxWidth().background(c.paper).drawBehind { drawRect(c.accent, Offset.Zero, Size(size.width, 6.dp.toPx())) }
        .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 12.dp)) {
        Text(stringResource(R.string.app_trip_ski_calc), style = Ski.type.label.copy(fontSize = 12.sp), color = c.muted)
        Text(
            if (days == null) stringResource(R.string.app_trip_ski_none)
            else pluralStringResource(R.plurals.app_trip_ski_value, trip.skiDayCount(), "⁦" + dayRange(days) + "⁩", trip.skiDayCount()),
            style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f), textDirection = TextDirection.Content), color = c.ink,
        )
    }
}

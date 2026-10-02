package io.github.pini236.skiapp.trip

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.SkiTheme
import java.time.temporal.ChronoUnit

/**
 * "Your trip" (H2 of round 10): the outbound flight (only the date is required) and the return, kept on the phone only.
 * The full ski days are worked out from the flights as the user types. Save, or delete the trip.
 */
@Composable
fun TripScreen(current: Trip?, onSave: (Trip) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    SkiTheme {
        val c = Ski.colors
        val o = current?.out; val b = current?.back
        var outDate by rememberSaveable { mutableStateOf(o?.date?.let { Trip.showDate(it) } ?: "") }
        var outNo by rememberSaveable { mutableStateOf(o?.number ?: "") }
        var outFrom by rememberSaveable { mutableStateOf(o?.from ?: "") }
        var outTo by rememberSaveable { mutableStateOf(o?.to ?: "") }
        var outDep by rememberSaveable { mutableStateOf(Trip.showTime(o?.departs)) }
        var outArr by rememberSaveable { mutableStateOf(Trip.showTime(o?.arrives)) }
        var backDate by rememberSaveable { mutableStateOf(b?.date?.let { Trip.showDate(it) } ?: "") }
        var backNo by rememberSaveable { mutableStateOf(b?.number ?: "") }
        var backDep by rememberSaveable { mutableStateOf(Trip.showTime(b?.departs)) }
        var backArr by rememberSaveable { mutableStateOf(Trip.showTime(b?.arrives)) }
        var tried by rememberSaveable { mutableStateOf(false) }

        val dateEx = "10.1.2027"; val timeEx = "16:00"
        val badDate = stringResource(R.string.app_trip_bad_date, dateEx)
        val badTime = stringResource(R.string.app_trip_bad_time, timeEx)
        fun dateErr(s: String, required: Boolean) = if (s.isBlank()) (if (required) badDate else null) else if (Trip.parseDate(s) == null) badDate else null
        fun timeErr(s: String) = if (s.isBlank() || Trip.parseTime(s) != null) null else badTime
        val od = Trip.parseDate(outDate); val bd = Trip.parseDate(backDate)
        val orderErr = if (od != null && bd != null && bd.isBefore(od)) stringResource(R.string.app_trip_bad_order) else null
        val errors = listOf(dateErr(outDate, true), timeErr(outDep), timeErr(outArr), dateErr(backDate, false), timeErr(backDep), timeErr(backArr), orderErr)

        fun build(): Trip? {
            if (errors.any { it != null } || od == null) return null
            val out = Flight(od, outNo.trim(), outFrom.trim(), outTo.trim(), Trip.parseTime(outDep), Trip.parseTime(outArr))
            // the return flies the other way: from where the outbound landed
            val back = bd?.let { Flight(it, backNo.trim(), outTo.trim(), outFrom.trim(), Trip.parseTime(backDep), Trip.parseTime(backArr)) }
            return Trip(out, back)
        }
        val preview = build()

        Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_trip_title), Modifier.weight(1f), style = Ski.type.title, color = c.ink)
                Text(stringResource(R.string.app_trip_cancel), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onCancel).padding(12.dp), style = Ski.type.label.copy(fontSize = 15.sp), color = c.accent)
            }
            Text(stringResource(R.string.app_trip_form_lead), Modifier.padding(horizontal = 16.dp), style = Ski.type.small, color = c.muted)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section(stringResource(R.string.app_trip_out), stringResource(R.string.app_trip_out_required))
                Pair2(
                    { Field(stringResource(R.string.app_trip_date), outDate, { outDate = it }, stringResource(R.string.app_trip_date_hint, dateEx), KeyboardType.Decimal, err = if (tried) dateErr(outDate, true) else null) },
                    { Field(stringResource(R.string.app_trip_number), outNo, { outNo = it }, stringResource(R.string.app_trip_number_hint, "6H 897"), ltr = true) },
                )
                Pair2(
                    { Field(stringResource(R.string.ticket_from), outFrom, { outFrom = it }, "TLV") },
                    { Field(stringResource(R.string.ticket_to), outTo, { outTo = it }, "TBS") },
                )
                Pair2(
                    { Field(stringResource(R.string.ticket_departs), outDep, { outDep = it }, timeEx, KeyboardType.Number, ltr = true, err = if (tried) timeErr(outDep) else null) },
                    { Field(stringResource(R.string.ticket_arrives), outArr, { outArr = it }, "20:35", KeyboardType.Number, ltr = true, err = if (tried) timeErr(outArr) else null) },
                )
                Section(stringResource(R.string.app_trip_back), stringResource(R.string.app_trip_back_optional))
                Pair2(
                    { Field(stringResource(R.string.app_trip_date), backDate, { backDate = it }, stringResource(R.string.app_trip_date_hint, "15.1.2027"), KeyboardType.Decimal, err = if (tried) dateErr(backDate, false) ?: orderErr else null) },
                    { Field(stringResource(R.string.app_trip_number), backNo, { backNo = it }, "6H 892", ltr = true) },
                )
                Pair2(
                    { Field(stringResource(R.string.ticket_departs), backDep, { backDep = it }, "01:35", KeyboardType.Number, ltr = true, err = if (tried) timeErr(backDep) else null) },
                    { Field(stringResource(R.string.ticket_arrives), backArr, { backArr = it }, "02:15", KeyboardType.Number, ltr = true, err = if (tried) timeErr(backArr) else null) },
                )
                // the full ski days, worked out from the flights
                Column(Modifier.fillMaxWidth().background(c.paper)) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(c.blue))
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.app_trip_ski_computed), style = Ski.type.label, color = c.muted)
                        val ski = preview?.skiDays()
                        if (ski == null) Text(stringResource(R.string.app_trip_ski_unknown), style = Ski.type.body, color = c.ink)
                        else {
                            val n = ChronoUnit.DAYS.between(ski.first, ski.second).toInt() + 1
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${Trip.showDay(ski.first)}–${Trip.showDay(ski.second)}", style = Ski.type.title.copy(fontSize = 30.sp, textDirection = TextDirection.Ltr), color = c.ink)
                                Text("  ·  " + pluralStringResource(R.plurals.app_trip_ski_count, n, n), style = Ski.type.title.copy(fontSize = 30.sp), color = c.ink)
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.app_trip_save),
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).background(c.glacier)
                        .clickable(role = Role.Button) { tried = true; build()?.let(onSave) }.padding(14.dp),
                    style = Ski.type.bodyBold.copy(fontSize = 16.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = androidx.compose.ui.graphics.Color.White,
                )
                if (current != null) Text(
                    stringResource(R.string.app_trip_delete),
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onDelete).padding(12.dp),
                    style = Ski.type.bodyBold.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = c.accent,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, note: String) {
    val c = Ski.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Ski.type.title.copy(fontSize = 30.sp), color = c.ink)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(1.dp).background(c.rule))
        Text(note, style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
    }
}

@Composable
private fun Pair2(a: @Composable () -> Unit, b: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { a() }
        Box(Modifier.weight(1f)) { b() }
    }
}

/** A square field with its label above, as on the site's forms (round 10). */
@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String, keyboard: KeyboardType = KeyboardType.Text, ltr: Boolean = false, err: String? = null) {
    val c = Ski.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = Ski.type.label.copy(fontSize = 13.sp), color = c.muted)
        val style = Ski.type.body.copy(fontSize = 16.sp, color = c.ink, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(c.paper).border(1.5.dp, if (err != null) c.red else c.rule).padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics { contentDescription = label },
            textStyle = style, singleLine = true, cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = style.copy(color = c.muted)); inner() } },
        )
        if (err != null) Text(err, style = Ski.type.small.copy(fontSize = 12.sp), color = c.red)
    }
}

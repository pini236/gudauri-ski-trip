package io.github.pini236.skiapp.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.group.Sheet
import io.github.pini236.skiapp.ui.Field
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Ski
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
 * Filling the trip without typing (Pini, 2.10.2026: "typing dates and lots of numbers by hand is not comfortable"):
 * dates and times come from the system's pickers, and the airports from a short list with search. The pickers are
 * Material's, dressed in the app's tokens: its colours, Plex for text, square corners.
 */

/** The rule for an airport (K-5, as the site's /^[A-Z]{3}$/): three Latin letters. */
object Airports {
    /** What was typed, as a code (upper case), or null if it is not three Latin letters. */
    fun code(typed: String): String? = typed.trim().uppercase().takeIf { CODE.matches(it) }
    /** A place the form may save: from the list ("TBS · Tbilisi") or a code alone. */
    fun ok(place: String): Boolean = Leg(java.time.LocalDate.MIN, from = place).fromCode != null
    private val CODE = Regex("^[A-Z]{3}$")
}

/** The airports a Gudauri trip starts or ends at, and the hubs on the way; any other one by its three-letter code. */
internal class Airport(val code: String, val city: Int)

internal val AIRPORTS = listOf(
    Airport("TBS", R.string.ticket_city_tbs), Airport("KUT", R.string.ticket_city_kut), Airport("BUS", R.string.app_city_bus),
    Airport("TLV", R.string.ticket_city_tlv), Airport("ETM", R.string.app_city_etm),
    Airport("IST", R.string.app_city_ist), Airport("SAW", R.string.app_city_ist), Airport("DXB", R.string.app_city_dxb),
    Airport("SVO", R.string.app_city_mow), Airport("VKO", R.string.app_city_mow), Airport("DME", R.string.app_city_mow), Airport("LED", R.string.app_city_led),
    Airport("WAW", R.string.app_city_waw), Airport("RIX", R.string.app_city_rix), Airport("VIE", R.string.app_city_vie),
    Airport("MUC", R.string.app_city_muc), Airport("LHR", R.string.app_city_lon),
)

/** The app's look for Material's own pieces (the date and time pickers): tokens, Plex, square corners. */
@Composable
internal fun PickerTheme(content: @Composable () -> Unit) {
    val c = Ski.colors
    val base = if (c.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accent, onPrimaryContainer = c.onAccent,
        secondaryContainer = c.grid, onSecondaryContainer = c.ink, surface = c.paper, onSurface = c.ink,
        surfaceContainerHigh = c.paper, surfaceContainerHighest = c.grid, surfaceVariant = c.grid, onSurfaceVariant = c.muted,
        outline = c.rule, outlineVariant = c.rule, tertiaryContainer = c.accent, onTertiaryContainer = c.onAccent,
    )
    val f = Ski.type.text
    val t = Typography()
    val type = Typography(
        t.displayLarge.copy(fontFamily = f), t.displayMedium.copy(fontFamily = f), t.displaySmall.copy(fontFamily = f),
        t.headlineLarge.copy(fontFamily = f), t.headlineMedium.copy(fontFamily = f), t.headlineSmall.copy(fontFamily = f),
        t.titleLarge.copy(fontFamily = f), t.titleMedium.copy(fontFamily = f), t.titleSmall.copy(fontFamily = f),
        t.bodyLarge.copy(fontFamily = f), t.bodyMedium.copy(fontFamily = f), t.bodySmall.copy(fontFamily = f),
        t.labelLarge.copy(fontFamily = f), t.labelMedium.copy(fontFamily = f), t.labelSmall.copy(fontFamily = f),
    )
    val square = RoundedCornerShape(0.dp)
    MaterialTheme(scheme, Shapes(square, square, square, square, square), type, content)
}

private fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.date(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * A day from the system's calendar, between [min] and [max]; it opens on [initial] or on [open]'s month. [title] says
 * which day this is ("Outbound · Date"), and the chosen day shows big above the month, in the app's own headline type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(title: String, initial: LocalDate?, open: LocalDate?, min: LocalDate?, max: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = Ski.colors
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.millis(),
        initialDisplayedMonthMillis = (initial ?: open ?: min)?.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = utcTimeMillis.date()
                return (min == null || d >= min) && (max == null || d <= max)
            }
        },
    )
    val locale = LocalConfiguration.current.locales[0]
    val chosen = state.selectedDateMillis?.date()
    PickerTheme {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton({ chosen?.let(onPick); onDismiss() }, enabled = chosen != null) { Text(stringResource(R.string.app_pick_ok)) } },
            dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.app_cancel)) } },
        ) {
            DatePicker(
                state, showModeToggle = false,
                title = { Text(title, Modifier.padding(start = 24.dp, end = 12.dp, top = 18.dp), style = Ski.type.label.copy(fontSize = 13.sp), color = c.muted) },
                headline = {
                    Text(chosen?.let { DateTimeFormatter.ofPattern("EEEE · d.M.yyyy", locale).format(it) } ?: stringResource(R.string.app_trip_pick_date),
                        Modifier.padding(start = 24.dp, end = 12.dp, bottom = 10.dp), style = Ski.type.title.copy(fontSize = 34.sp),
                        color = if (chosen == null) c.muted else c.ink, maxLines = 1)
                },
            )
        }
    }
}

/** A time from the system's clock face, in 24 hours; [title] says which time this is ("Outbound · Departs"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(title: String, initial: LocalTime?, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial?.hour ?: 12, initial?.minute ?: 0, is24Hour = true)
    PickerTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton({ onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.app_pick_ok)) } },
            dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.app_cancel)) } },
            title = { Text(title, style = Ski.type.title.copy(fontSize = 34.sp), color = Ski.colors.ink, maxLines = 1) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
        )
    }
}

/**
 * A field that opens a picker: the same look as a typed field (label, box, error), with the picker's icon, and
 * never the keyboard.
 */
@Composable
internal fun PickField(label: String, value: String?, hint: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, error: String? = null, ltr: Boolean = true,
                       onClear: (() -> Unit)? = null) {
    val c = Ski.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = Ski.type.label.copy(fontSize = 13.sp), color = c.muted)
        Row(
            Modifier.fillMaxWidth().height(48.dp).background(c.paper).border(1.5.dp, if (error != null) c.red else c.rule)
                .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label + ", " + (value ?: hint) }
                .padding(start = 12.dp, end = if (value != null && onClear != null) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(value ?: hint, Modifier.weight(1f), style = Ski.type.body.copy(fontSize = 16.sp, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
                color = if (value == null) c.muted.copy(alpha = .75f) else c.ink, maxLines = 1)
            if (value != null && onClear != null) {
                val clear = stringResource(R.string.app_trip_clear)
                Box(Modifier.size(40.dp).clickable(role = Role.Button, onClick = onClear).semantics { contentDescription = "$clear, $label" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.x, null, Modifier.size(16.dp), tint = c.muted)
                }
            } else Icon(icon, null, Modifier.size(18.dp), tint = if (value == null) c.muted else c.glacier)
        }
        if (error != null) Text(error, style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.red)
    }
}

/**
 * The airport, from the short list (with search), or another three-letter code typed in the search: the same rule as
 * the site (K-5, decision 62; its trip.bad_code). No free place names: the pass shows the code in giant letters, and
 * "who flies together" groups by it. Writes "TBS · Tbilisi", or the code alone.
 */
@Composable
internal fun BoxScope.PlaceSheet(title: String, current: String, first: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Ski.colors
    var q by remember { mutableStateOf("") }
    val names = AIRPORTS.associate { it.code to stringResource(it.city) }
    val order = first + AIRPORTS.map { it.code }.filter { it !in first }
    val shown = order.distinct().filter { code -> q.isBlank() || code.contains(q.trim(), true) || names.getValue(code).contains(q.trim(), true) }
    Sheet(onDismiss) {
        Text(title, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = c.ink)
        // as the site's sheet: the words inside the field, and the cursor in it once the sheet is up
        val search = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { search.requestFocus() } }
        Field(stringResource(R.string.app_trip_place_search), q, { q = it.take(40) }, Modifier.padding(top = 10.dp, bottom = 6.dp), showLabel = false, focus = search)
        Column {
            for (code in shown.take(8)) {
                val label = "$code · ${names.getValue(code)}"
                val on = current.startsWith(code)
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onPick(label) }.background(if (on) c.grid else c.paper).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(code, Modifier.width(56.dp), style = Ski.type.title.copy(fontSize = 26.sp, textDirection = TextDirection.Ltr), color = c.ink)
                    Text(names.getValue(code), Modifier.weight(1f), style = Ski.type.body, color = c.ink)
                    if (on) Icon(Icons.check, null, Modifier.size(20.dp), tint = c.glacier)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
            }
            val typed = q.trim()
            val code = Airports.code(typed)
            if (code != null && code !in shown) Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onPick(code) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(code, Modifier.width(56.dp), style = Ski.type.title.copy(fontSize = 26.sp, textDirection = TextDirection.Ltr), color = c.glacier)
                // the site's sheet says the same (app.trip_place_other, shared)
                Text(stringResource(R.string.app_trip_place_other, code), Modifier.weight(1f), style = Ski.type.bodyBold, color = c.glacier)
                Icon(Icons.plus, null, Modifier.size(20.dp), tint = c.glacier)
            }
        }
    }
}

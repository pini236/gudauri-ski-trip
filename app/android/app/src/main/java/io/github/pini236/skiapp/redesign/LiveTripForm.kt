package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.dayRange
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.trip.DateDialog
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.PlaceSheet
import io.github.pini236.skiapp.trip.TimeDialog
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.trip.TripText
import io.github.pini236.skiapp.ui.Icons
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * T09 (design/handoff/2026-11-23-redesign-in-words, section 7): "your trip", the full form, over the mountain. Two glass
 * cards, the way out ("only the date is required") and the way back ("it can do without"): the route in big codes and
 * the details as pills under it. A date opens the calendar of Q08, a time the system's clock, a code the airports, the
 * number a field in place; what is missing is a dashed "+" pill, and a long press clears a value. The ski days in one
 * line, "change" for days by hand. At the bottom "save" and the red "delete the trip" (two taps).
 *
 * The same form fills a member's flight for an admin ([title], [intro]): no delete, no "kept on the phone".
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.LiveTripForm(initial: Trip?, today: LocalDate, night: Boolean, haptics: Haptics,
                                   onSave: (Trip) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit,
                                   title: String? = null, intro: String? = null, localNote: Boolean = true, busy: Boolean = false, err: String? = null) {
    val k = remember(night) { SheetInk(night) }
    val tlv = "TLV · " + stringResource(R.string.ticket_city_tlv)
    val tbs = "TBS · " + stringResource(R.string.ticket_city_tbs)
    var f by remember(initial) { mutableStateOf(TripDraft.of(initial, tlv, tbs)) }
    // which picker is open: cal, oDep, oArr, rDep, rArr, oFrom, oTo, rFrom, rTo, sFrom, sTo; or the number in place: oNum, rNum
    var pick by remember { mutableStateOf<String?>(null) }
    var tried by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { Qa.log("redesign trip delete armed"); delay(4000); armed = false } }
    LaunchedEffect(Unit) { Qa.log("redesign trip form ${if (initial == null) "new" else "edit"}") }
    val initialOut = initial?.out?.date
    val errs = f.errors(today, initialOut)
    val trip = f.trip(today, initialOut)
    LaunchedEffectQa { c ->
        when (c) {
            "form:noRet" -> f = f.withDates(f.oDate ?: today.plusDays(40), null)
            "form:clearDep" -> f = f.copy(oDep = null)
            "form:past" -> { f = f.copy(oDate = today.minusDays(3)); tried = true; Qa.log("redesign trip form past") }
            "form:skiOut" -> { f.oDate?.let { o -> f = f.copy(ski = o.minusDays(1)..o.plusDays(1)) }; Qa.log("redesign trip form ski outside") }
            "form:num" -> pick = "oNum"
            "form:arm" -> armed = true
            "form:save" -> { tried = true; trip?.let { onSave(it) } }
        }
    }
    androidx.activity.compose.BackHandler(enabled = true) { if (pick != null) pick = null else onBack() }

    Box(Modifier.matchParentSize().background(k.veil).clickable(enabled = false) { })
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(44.dp).clip(CircleShape).background(k.surf.copy(alpha = 1f)).semantics { contentDescription = back; role = Role.Button }
                .clickable { haptics.tick(.2f); onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.back, null, Modifier.size(22.dp), tint = k.ink)
            }
            Text(title ?: stringResource(R.string.app_home_trip), Modifier.padding(start = 12.dp).semantics { this.liveRegion = LiveRegionMode.Polite },
                fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (night) Color.White else k.ink)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (intro != null) Card(k) { Small(intro, k) }
            // the way out
            Card(k) {
                CardHead(stringResource(R.string.trip_out), stringResource(R.string.trip_out_required), k)
                Route(f.oFrom, f.oTo, k, auto = false, onFrom = { pick = "oFrom" }, onTo = { pick = "oTo" })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailPill(f.oDate?.let { io.github.pini236.skiapp.season.shortDate(it) }, stringResource(R.string.app_trip_pick_date), k,
                        onClick = { pick = "cal" }, onClear = null)
                    DetailPill(f.oDep?.let { TripText.time(it) }, stringResource(R.string.trip_add_time), k, label = stringResource(R.string.ticket_departs),
                        onClick = { pick = "oDep" }, onClear = { f = f.copy(oDep = null) })
                    DetailPill(f.oArr?.let { TripText.time(it) }, stringResource(R.string.trip_add_time), k, label = stringResource(R.string.ticket_arrives),
                        onClick = { pick = "oArr" }, onClear = { f = f.copy(oArr = null) })
                    NumberPill(f.oFlight, k, editing = pick == "oNum", onOpen = { pick = "oNum" }, onDone = { pick = null },
                        onChange = { f = f.copy(oFlight = it) }, onClear = { f = f.copy(oFlight = "") })
                }
                val oe = errs.out?.takeIf { it != TripError.NEED_DATE || tried }
                if (oe != null) ErrText(oe, k)
            }
            // the way back: one line, "add the return", until it has a date
            if (f.rDate == null) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(22.dp)).background(k.surf.copy(alpha = 1f))
                    .border(1.dp, k.line, RoundedCornerShape(22.dp)).semantics { role = Role.Button }.clickable { haptics.tick(.3f); pick = "cal" }
                    .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_trip_btn_add_return), Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.acc)
                    Text(stringResource(R.string.trip_back_optional), fontFamily = Gudi, fontSize = 12.sp, color = k.ink2)
                }
            } else Card(k) {
                CardHead(stringResource(R.string.trip_back), stringResource(R.string.trip_back_optional), k)
                Route(f.backFrom, f.backTo, k, auto = f.rAuto, onFrom = { pick = "rFrom" }, onTo = { pick = "rTo" })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailPill(io.github.pini236.skiapp.season.shortDate(f.rDate!!), stringResource(R.string.app_trip_pick_date), k,
                        onClick = { pick = "cal" }, onClear = { f = f.withDates(f.oDate ?: today, null) })
                    DetailPill(f.rDep?.let { TripText.time(it) }, stringResource(R.string.trip_add_time), k, label = stringResource(R.string.ticket_departs),
                        onClick = { pick = "rDep" }, onClear = { f = f.copy(rDep = null) })
                    DetailPill(f.rArr?.let { TripText.time(it) }, stringResource(R.string.trip_add_time), k, label = stringResource(R.string.ticket_arrives),
                        onClick = { pick = "rArr" }, onClear = { f = f.copy(rArr = null) })
                    NumberPill(f.rFlight, k, editing = pick == "rNum", onOpen = { pick = "rNum" }, onDone = { pick = null },
                        onChange = { f = f.copy(rFlight = it) }, onClear = { f = f.copy(rFlight = "") })
                }
                errs.back?.let { ErrText(it, k) }
            }
            // the ski days
            Card(k) {
                val days = (trip ?: f.flights()?.copy(ski = f.ski))?.let { t -> t.skiDays()?.let { it to t.skiDayCount() } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (days == null) stringResource(R.string.app_trip_ski_none)
                            else pluralStringResource(R.plurals.app_trip_ski_value, days.second, "⁦" + dayRange(days.first) + "⁩", days.second),
                            fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink, style = TextStyle(textDirection = TextDirection.Content))
                        Small(stringResource(if (f.ski != null) R.string.trip_ski_manual else R.string.app_trip_ski_calc), k)
                    }
                    if (f.ski != null) TextLink(stringResource(R.string.app_trip_ski_auto), k) { haptics.tick(.2f); f = f.copy(ski = null) }
                    else if (f.oDate != null) TextLink(stringResource(R.string.app_trip_ski_change), k) { haptics.tick(.2f); pick = "sFrom" }
                }
                errs.ski?.let { ErrText(it, k) }
            }
            if (err != null) Small(err, k, color = k.red)
            AccButton(stringResource(R.string.trip_save), k, enabled = !busy && trip != null) {
                tried = true; haptics.tick(.4f); trip?.let { onSave(it); Qa.log("redesign trip form saved") }
            }
            if (initial != null && onDelete != null) {
                val text = stringResource(if (armed) R.string.trip_delete_confirm else R.string.trip_delete)
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(if (armed) k.red else Color.Transparent)
                    .border(1.dp, k.red, RoundedCornerShape(26.dp)).semantics { role = Role.Button; if (armed) liveRegion = LiveRegionMode.Assertive }
                    .clickable { haptics.tick(.4f); if (armed) { armed = false; onDelete() } else armed = true }, contentAlignment = Alignment.Center) {
                    Text(text, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = if (armed) k.onRed else k.red)
                }
            }
            if (localNote) Small(stringResource(R.string.app_trip_local_note), k, center = true, modifier = Modifier.fillMaxWidth())
        }
    }

    val close = { pick = null }
    val out = stringResource(R.string.trip_out); val ret = stringResource(R.string.trip_back)
    val depL = stringResource(R.string.ticket_departs); val arrL = stringResource(R.string.ticket_arrives)
    when (pick) {
        // the calendar of Q08, with the draft's dates: it gives the way out and the way back
        "cal" -> {
            Box(Modifier.matchParentSize().background(Color(0x61060B16)).clickable(onClick = close))
            Box(Modifier.align(Alignment.BottomCenter)) {
                LiveCalendar(f.flights(), today, k, haptics) { t -> f = f.withDates(t.out.date, t.ret?.date); pick = null; Qa.log("redesign trip form dates") }
            }
        }
        "oDep" -> TimeDialog("$out · $depL", f.oDep, { f = f.copy(oDep = it) }, close)
        "oArr" -> TimeDialog("$out · $arrL", f.oArr ?: f.oDep, { f = f.copy(oArr = it) }, close)
        "rDep" -> TimeDialog("$ret · $depL", f.rDep, { f = f.copy(rDep = it) }, close)
        "rArr" -> TimeDialog("$ret · $arrL", f.rArr ?: f.rDep, { f = f.copy(rArr = it) }, close)
        "oFrom" -> PlaceSheet(stringResource(R.string.app_trip_place_from), f.oFrom, listOf("TLV"), { f = f.copy(oFrom = it); close() }, close)
        "oTo" -> PlaceSheet(stringResource(R.string.app_trip_place_to), f.oTo, listOf("TBS", "KUT", "BUS"), { f = f.copy(oTo = it); close() }, close)
        // the way back changed by hand stops being automatic; both its ends are kept
        "rFrom" -> PlaceSheet(stringResource(R.string.app_trip_place_from), f.backFrom, listOf("TBS", "KUT", "BUS"),
            { f = f.copy(rFrom = it, rTo = f.backTo); close() }, close)
        "rTo" -> PlaceSheet(stringResource(R.string.app_trip_place_to), f.backTo, listOf("TLV"), { f = f.copy(rFrom = f.backFrom, rTo = it); close() }, close)
        // the ski days by hand: the first, then the last, within the trip
        "sFrom" -> DateDialog(stringResource(R.string.app_trip_ski_first), f.skiDaysNow()?.start, f.oDate, f.oDate, f.rDate,
            { d -> val end = f.skiDaysNow()?.endInclusive?.takeIf { it >= d } ?: d; f = f.copy(ski = d..end); pick = "sTo" }, close)
        "sTo" -> DateDialog(stringResource(R.string.app_trip_ski_last), f.ski?.endInclusive, f.ski?.start ?: f.oDate, f.ski?.start ?: f.oDate, f.rDate,
            { d -> f.ski?.let { s -> f = f.copy(ski = s.start..maxOf(s.start, d)) }; pick = null }, close)
    }
}

/** The ski days the form shows now: by hand, or from the flights (to start "change" from them). */
private fun TripDraft.skiDaysNow(): ClosedRange<LocalDate>? = ski ?: flights()?.flightSkiDays()

/** A glass card (section 7): the sheet's surface, radius 22, 16 inside. */
@Composable
private fun Card(k: SheetInk, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, RoundedCornerShape(22.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun CardHead(title: String, note: String, k: SheetInk) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
        Text(note, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2)
    }
}

/**
 * The route in big codes, always from, arrow, to, left to right (section 9), each a button to the airports; the way
 * back says "automatic" at its end while it is the way out reversed.
 */
@Composable
private fun Route(from: String, to: String, k: SheetInk, auto: Boolean, onFrom: () -> Unit, onTo: () -> Unit) {
    val fc = Leg(LocalDate.MIN, from = from).fromCode; val tc = Leg(LocalDate.MIN, from = to).fromCode
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Code(fc, from, k, onFrom)
                Text("→", fontFamily = Gudi, fontSize = 22.sp, color = k.ink2)
                Code(tc, to, k, onTo)
            }
        }
        if (auto) Text(stringResource(R.string.app_trip_back_auto), fontFamily = Gudi, fontSize = 12.sp, color = k.ink2)
    }
}

@Composable
private fun Code(code: String?, place: String, k: SheetInk, onClick: () -> Unit) {
    val city = Leg(LocalDate.MIN, from = place).fromCity
    Column(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).semantics(mergeDescendants = true) { role = Role.Button }
        .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(code ?: "???", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = if (code == null) k.red else k.ink)
        if (city.isNotBlank()) Text(city, fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2, maxLines = 2)
    }
}

/**
 * A detail as a pill (section 7): 36 high in a 44 touch area; filled, its value (and a small label before it); missing,
 * dashed with its "+". A long press clears a filled value ([onClear] null: it cannot be cleared).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailPill(value: String?, add: String, k: SheetInk, label: String? = null, onClick: () -> Unit, onClear: (() -> Unit)?) {
    val clearLabel = stringResource(R.string.app_trip_clear)
    Box(Modifier.heightIn(min = 44.dp).semantics(mergeDescendants = true) {
        role = Role.Button
        if (value != null && onClear != null) onLongClick(clearLabel) { onClear(); true }
    }.combinedClickable(onClick = onClick, onLongClick = if (value != null) onClear else null), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(18.dp)
        val m = Modifier.heightIn(min = 36.dp).clip(shape)
        Row(if (value != null) m.background(k.line).padding(horizontal = 12.dp)
            else m.drawBehind {
                drawRoundRect(k.ink2, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
            }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (value != null && label != null) Text(label, fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2)
            Text(value ?: if (label != null) "$add · $label" else add, fontFamily = Gudi, fontWeight = if (value != null) FontWeight.Medium else FontWeight.Normal,
                fontSize = 14.sp, color = if (value != null) k.ink else k.ink2, style = TextStyle(fontFeatureSettings = "tnum"))
        }
    }
}

/** The flight number: a pill, or in place a field in capitals; empty, "+ number". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberPill(value: String, k: SheetInk, editing: Boolean, onOpen: () -> Unit, onDone: () -> Unit, onChange: (String) -> Unit, onClear: () -> Unit) {
    if (!editing) { DetailPill(value.ifBlank { null }, stringResource(R.string.trip_add_number), k, onClick = onOpen, onClear = onClear); return }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val hint = stringResource(R.string.app_trip_flight_hint)
    Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextField(value, { onChange(it.uppercase().take(12)) },
                Modifier.widthIn(min = 120.dp, max = 180.dp).heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).border(1.5.dp, k.acc, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp).focusRequester(focus).semantics { contentDescription = hint },
                singleLine = true, textStyle = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = k.ink),
                cursorBrush = SolidColor(k.acc),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, fontFamily = Gudi, fontSize = 14.sp, color = k.ink2.copy(alpha = .6f)); inner() } })
        }
    }
}

@Composable
private fun ErrText(e: TripError, k: SheetInk) {
    val res = when (e) {
        TripError.NEED_DATE -> R.string.trip_need_date
        TripError.PAST_DATE -> R.string.trip_past_date
        TripError.BAD_CODE -> R.string.trip_bad_code
        TripError.BAD_ORDER -> R.string.trip_bad_order
        TripError.SKI_OUTSIDE -> R.string.trip_ski_outside
    }
    Text(stringResource(res), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontFamily = Gudi, fontSize = 12.5.sp, color = k.red)
}

package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.Around
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.PassEdit
import io.github.pini236.skiapp.trip.PassEdits
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Icons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * The completion round, part 2 (Q07 and Q08, design/handoff/2026-11-21-redesign-around, section 6): "your trip" over
 * the mountain. The rules are today's (trip/Trip.kt): saved on the phone, only the way out's date required, the ski days
 * from the flights or by hand, the same stages. New: the one paper thing in the app, no tilt; the return behind; the
 * stub with the number of the stage; the calendar as a sheet.
 */
internal class PassPaper(night: Boolean) {
    val paper = if (night) Color(0xFF16213A) else Color.White
    val ink = if (night) Color(0xFFEAF1F8) else Color(0xFF0E2233)
    val ink2 = if (night) Color(0xFFA9B9CC) else Color(0xFF40556A)
    val line = if (night) Color(0x2EEAF1F8) else Color(0x240E2233)
    val acc = if (night) Color(0xFFB79CFF) else Color(0xFF6B3FD4)
    val skiDay = if (night) Color(0x33B79CFF) else Color(0x246B3FD4)
}

/** The paper with a round notch of [r] at both ends of the line [stubFromBottom] above its foot (section 1). */
private class NotchedPaper(val radius: Dp, val stubFromBottom: Dp, val r: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val card = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(radius.toPx()))) }
        if (stubFromBottom <= 0.dp) return Outline.Generic(card)
        val y = size.height - stubFromBottom.toPx(); val rr = r.toPx()
        val holes = Path().apply {
            addOval(Rect(Offset(0f, y), rr)); addOval(Rect(Offset(size.width, y), rr))
        }
        Outline.Generic(Path().apply { op(card, holes, PathOperation.Difference) })
    }
}

/** The card's shape (UX pass 2, TR3): it ends at the perforation, square below, a half notch (r 10) at each bottom corner. */
private class CardPaper(val radius: Dp, val r: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val rad = radius.toPx(); val rr = r.toPx()
        val z = androidx.compose.ui.geometry.CornerRadius.Zero; val c = androidx.compose.ui.geometry.CornerRadius(rad)
        val card = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), c, c, z, z)) }
        val holes = Path().apply { addOval(Rect(Offset(0f, size.height), rr)); addOval(Rect(Offset(size.width, size.height), rr)) }
        Outline.Generic(Path().apply { op(card, holes, PathOperation.Difference) })
    }
}

/** The stub's shape: its own paper, bottom corners [radius], a half notch at each top corner. */
private class StubPaper(val radius: Dp, val r: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val rad = radius.toPx(); val rr = r.toPx()
        val z = androidx.compose.ui.geometry.CornerRadius.Zero; val c = androidx.compose.ui.geometry.CornerRadius(rad)
        val stub = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), z, z, c, c)) }
        val holes = Path().apply { addOval(Rect(Offset(0f, 0f), rr)); addOval(Rect(Offset(size.width, 0f), rr)) }
        Outline.Generic(Path().apply { op(stub, holes, PathOperation.Difference) })
    }
}

/** A value's field look (TR1): a dashed line under it, [acc] at 45 % (night 55 %); solid and 2 thick while its editor is open. */
private fun Modifier.fieldLine(p: PassPaper, night: Boolean, active: Boolean, on: Boolean): Modifier = if (!on) this else
    this.padding(bottom = 3.dp).drawBehind {
        val y = size.height - 1.dp.toPx()
        if (active) drawLine(p.acc, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        else drawLine(p.acc.copy(alpha = if (night) .55f else .45f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    }

/** "יום א׳ · 10.1.2027" or "Sun · 10.1.2027". */
private fun legDate(d: LocalDate, lang: String): String {
    val wd = if (lang == "he") "יום " + DateTimeFormatter.ofPattern("EEEEE", Locale.forLanguageTag("he")).format(d)
        else runCatching { DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag(lang)).format(d) }.getOrDefault("")
    return "$wd · ${d.dayOfMonth}.${d.monthValue}.${d.year}"
}

@Composable
internal fun skiLine(trip: Trip): String? {
    val r = trip.skiDays() ?: return null
    val n = trip.skiDayCount().takeIf { it > 0 } ?: return null
    val range = stringResource(R.string.app_trip_range, "${r.start.dayOfMonth}", "${r.endInclusive.dayOfMonth}.${r.endInclusive.monthValue}")
    return pluralStringResource(R.plurals.app_trip_ski_line, n, range, n.toString())
}

/** The stub's number and words by the stage (section 6's table): days or nights to the flight, 0 on its day, the ski day. */
@Composable
private fun stub(trip: Trip, now: LocalDateTime, night: Boolean): Pair<String?, String?> = when (val st = trip.stage(now)) {
    is Trip.Stage.Before -> if (st.days == 0) "0" to stringResource(R.string.ticket_stub_departing)
        else st.days.toString() to pluralStringResource(if (night) R.plurals.app_pass_nights else R.plurals.app_pass_days, st.days)
    is Trip.Stage.SkiDay -> st.n.toString() to (stringResource(R.string.ticket_stub_ski_day) + " " + stringResource(R.string.ticket_stub_of, st.of.toString()))
    Trip.Stage.Over -> null to null
}

@Composable
private fun Field(label: String, value: String?, add: String?, p: PassPaper, modifier: Modifier, night: Boolean, active: Boolean, onClick: (() -> Unit)?) {
    Column(modifier.heightIn(min = 44.dp).let { m -> if (onClick != null) m.semantics { role = Role.Button }.clickable(onClick = onClick) else m }) {
        Text(label, fontFamily = Gudi, fontSize = 11.5.sp, color = p.ink2, maxLines = 2)
        val line = Modifier.fieldLine(p, night, active, onClick != null)
        if (value == null && add != null) Text(add, line, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = p.acc, maxLines = 1)
        else Text(value ?: "—", line, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.5.sp, color = if (active) p.acc else p.ink, maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun LegTag(text: String, p: PassPaper) {
    Box(Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = p.ink)
    }
}

/** The two airports' codes and cities, the plane between them pointing the way of the flight. */
@Composable
private fun Codes(from: String?, fromCity: String, to: String?, toCity: String, p: PassPaper, toFaint: Boolean = false, night: Boolean = false, fromActive: Boolean = false, toActive: Boolean = false, onFrom: (() -> Unit)?, onTo: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).let { m -> if (onFrom != null) m.clickable(onClick = onFrom) else m }) {
            Text(from ?: "—", fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(54f), color = p.ink, maxLines = 1)
            Text(fromCity, Modifier.fieldLine(p, night, fromActive, onFrom != null), fontFamily = Gudi, fontSize = 11.5.sp, color = if (fromActive) p.acc else p.ink2, maxLines = 2)
        }
        Icon(Icons.plane, null, Modifier.padding(top = 22.dp).size(28.dp), tint = p.acc)
        Column(Modifier.weight(1f).let { m -> if (onTo != null) m.clickable(onClick = onTo) else m }, horizontalAlignment = Alignment.End) {
            Text(to ?: "—", Modifier.alpha(if (toFaint) .28f else 1f), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(54f), color = p.ink, maxLines = 1)
            Text(toCity, Modifier.fieldLine(p, night, toActive, onTo != null), fontFamily = Gudi, fontSize = 11.5.sp, color = if (toActive) p.acc else p.ink2, maxLines = 2)
        }
    }
}

/** The dashed line over the stub, 2 in [p]'s line. */
@Composable
private fun Perforation(p: PassPaper) {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(p.line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
    }
}

private const val HOLES = 12
private val STUB_H = 100.dp

/**
 * One pass (Q07): the strip, the codes, six fields and, as its own piece of paper, the stub (UX pass 2, TR1 to TR3).
 * [tear] 0 to 1 punches [holes] along the perforation and opens the stub; [fall] then drops it. [onField] opens a field's
 * own sheet, only on the card in front; [editing] marks the field whose editor is open; [onForm] is "edit" (the full form).
 */
@Composable
private fun TripCard(trip: Trip, isRet: Boolean, now: LocalDateTime, night: Boolean, p: PassPaper, front: Boolean, tear: Float, fall: Float, holes: Int,
                     editing: PassEdit?, rise: Float = 1f, reduced: Boolean = false, passenger: io.github.pini236.skiapp.home.Passenger?, onTear: () -> Unit, onForm: (() -> Unit)?, onField: ((PassEdit) -> Unit)?) {
    val lang = io.github.pini236.skiapp.i18n.Lang.current(LocalContext.current.resources).tag
    val leg = if (isRet) trip.ret!! else trip.out
    fun on(e: PassEdit): (() -> Unit)? = if (front && onField != null) ({ onField(e) }) else null
    fun act(e: PassEdit) = front && editing == e
    val shadow = Color(0x4D0A192D)
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().shadow(22.dp, CardPaper(22.dp), ambientColor = shadow, spotColor = shadow).background(p.paper, CardPaper(22.dp))) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.app_home_trip), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = p.ink2)
                    LegTag(if (isRet) stringResource(R.string.app_pass_ret) + " · ${leg.date.dayOfMonth}.${leg.date.monthValue}" else stringResource(R.string.app_pass_out), p)
                    Spacer(Modifier.weight(1f))
                    // "edit" lives in the card, at the end of the first row (TR1): a chip, 32 high inside a 44 touch
                    if (front && onForm != null) {
                        val lbl = stringResource(R.string.trip_edit)
                        Box(Modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.clickable(onClick = onForm), contentAlignment = Alignment.Center) {
                            Row(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(p.skiDay).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.edit, null, Modifier.size(15.dp), tint = p.acc)
                                Text(lbl, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = p.acc, maxLines = 1)
                            }
                        }
                    }
                }
                Codes(leg.fromCode, leg.fromCity, leg.toCode, leg.toCity, p, night = night, fromActive = act(PassEdit.Place(from = !isRet)), toActive = act(PassEdit.Place(from = isRet)),
                    onFrom = on(PassEdit.Place(from = !isRet)), onTo = on(PassEdit.Place(from = isRet)))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(stringResource(R.string.trip_date), legDate(leg.date, lang), null, p, Modifier.weight(1.5f), night, act(PassEdit.Dates), on(PassEdit.Dates))
                        Field(stringResource(R.string.ticket_departs), leg.departs?.toString(), stringResource(R.string.trip_add_time), p, Modifier.weight(1f), night, act(PassEdit.Time(isRet, departs = true)), on(PassEdit.Time(isRet, departs = true)))
                        Field(stringResource(R.string.ticket_arrives), leg.arrives?.toString(), stringResource(R.string.trip_add_time), p, Modifier.weight(1f), night, act(PassEdit.Time(isRet, departs = false)), on(PassEdit.Time(isRet, departs = false)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(stringResource(R.string.app_pass_traveller), passenger?.let { it.name ?: stringResource(R.string.ticket_pax_guest) } ?: stringResource(R.string.ticket_pax_guest),
                            null, p, Modifier.weight(1.5f), night, false, if (front) passenger?.onTap else null)
                        Field(stringResource(R.string.ticket_flight), leg.flight.ifBlank { null }, stringResource(R.string.trip_add_number), p, Modifier.weight(1f), night, act(PassEdit.Flight(isRet)), on(PassEdit.Flight(isRet)))
                        // no baggage in "your trip" (Trip has none, a model change that waits for the architect): the column stays, empty
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        // the stub: its own paper with its own shadow, meeting the card with no gap; a tap tears it, hole by hole (TR3)
        val sx = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
        val tearLabel = stringResource(R.string.ticket_tear_stub)
        // the way back's stub: the ski days, big, and their dates (handoff section 14.9, as Pass.kt today)
        val retDays = trip.skiDayCount().takeIf { isRet && it > 0 }
        val (num, words) = if (isRet) (retDays?.let { it.toString() to pluralStringResource(R.plurals.app_skipass_days_n, it) } ?: (null to null)) else stub(trip, now, night)
        Box(Modifier.fillMaxWidth().height(STUB_H).zIndex(if (tear > 0f) 1f else 0f)
            .graphicsLayer {
                // the hinge on the end side's top corner: the start side opens first, then the stub falls, unclipped
                transformOrigin = TransformOrigin(if (sx > 0) 1f else 0f, 0f)
                // reduced motion: no rotation or fall, the stub only fades out and in
                val ang = if (reduced) 0f else 5f * tear + 15f * fall
                rotationZ = -sx * ang
                translationY = (if (reduced) 0f else 3.dp.toPx() * tear + 250.dp.toPx() * fall * fall + 34.dp.toPx() * (1f - rise))
                alpha = (if (reduced) 1f - fall else 1f - (fall * 1.4f - .4f).coerceIn(0f, 1f)) * rise
            }
            .shadow(10.dp, StubPaper(22.dp), ambientColor = shadow, spotColor = shadow).background(p.paper, StubPaper(22.dp))
            .let { m -> if (front) m.semantics { role = Role.Button; contentDescription = tearLabel }.clickable(onClick = onTear) else m }) {
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(10.dp).align(Alignment.TopCenter)) {
                val y = 1.dp.toPx()
                drawLine(p.line, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                // the holes punch from the start side to the end side, 43 ms apart
                for (i in 0 until holes.coerceAtMost(HOLES)) {
                    val t = (i + .5f) / HOLES; val x = if (sx > 0) size.width * t else size.width * (1 - t)
                    drawCircle(Color(0x8C28385A), 4.dp.toPx(), Offset(x, y))
                }
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (num != null) Text(num, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(66f), color = p.ink, style = TextStyle(fontFeatureSettings = "tnum"))
                Column(Modifier.weight(1f)) {
                    if (words != null) Text(words, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.ink, maxLines = 2)
                    val r = trip.skiDays()
                    (if (retDays != null && r != null) stringResource(R.string.app_trip_range, "${r.start.dayOfMonth}", "${r.endInclusive.dayOfMonth}.${r.endInclusive.monthValue}") else skiLine(trip))
                        ?.let { Text(it, fontFamily = Gudi, fontSize = 12.5.sp, color = p.ink2, maxLines = 1) }
                }
            }
        }
    }
}

/**
 * Q07: the pass over the mountain (the veil under it), the return behind it, 14 narrower each side and 50 above, at
 * 78 % (only its top line shows). A tap on it, or a swipe, swaps (280 ms). Under the pass "the return flight" (or "add
 * the return") and the ski pass's price. "Edit" opens the full form.
 */
@Composable
internal fun BoxScope.LiveTripPass(around: Around, trip: Trip, now: LocalDateTime, night: Boolean, haptics: Haptics, skipassPrice: Int?,
                                   editing: PassEdit?, onForm: () -> Unit, onField: (PassEdit) -> Unit, onSkipass: () -> Unit) {
    val p = remember(night) { PassPaper(night) }
    val scope = rememberCoroutineScope()
    val hasRet = trip.ret != null
    var showRet by remember { mutableStateOf(false) }
    val swapA = remember { Animatable(0f) }
    val tear = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }
    val rise = remember { Animatable(1f) }
    val reduced = io.github.pini236.skiapp.status.reducedMotion()
    var busy by remember { mutableStateOf(false) }
    var holes by remember { mutableIntStateOf(0) }
    val frontRet = showRet && hasRet
    fun swap() = scope.launch {
        if (!hasRet || busy) return@launch
        busy = true
        around.sounds.play("ticket-slide", .7f); haptics.tick(.5f)
        swapA.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        showRet = !showRet
        swapA.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
        Telemetry.event("ticket_swap", mapOf("to" to if (showRet) "ret" else "out"))
        Qa.log("redesign trip swap ${if (showRet) "ret" else "out"}")
        busy = false
    }
    fun tearIt() = scope.launch {
        if (busy) return@launch
        busy = true
        around.sounds.play("ticket-tear", .85f)
        if (reduced) { tear.snapTo(1f); holes = HOLES; haptics.tick(.5f) }
        else tear.animateTo(1f, tween(520, easing = LinearEasing)) {
            val n = floor(value * HOLES).toInt().coerceAtMost(HOLES)
            if (n > holes) { repeat(n - holes) { haptics.tick(.35f + (it % 3) * .1f) }; holes = n }
        }
        haptics.click(1f)
        Telemetry.event("ticket_tear", mapOf("leg" to if (frontRet) "ret" else "out"))
        Qa.log("redesign trip torn")
        fall.animateTo(1f, tween(if (reduced) 300 else 620, easing = FastOutSlowInEasing))
        delay(360) // 1,500 ms from the start in all (520 + 620 + 360)
        // step 4: a new stub rises 34 into place and fades in over 300 ms
        rise.snapTo(0f); tear.snapTo(0f); fall.snapTo(0f); holes = 0
        around.sounds.play("ticket-land", .6f); haptics.click(.5f)
        rise.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        busy = false
    }
    LaunchedEffectQa { c -> when (c) { "trip:swap" -> swap(); "trip:tear" -> tearIt() } }
    val swapLabel = stringResource(if (frontRet) R.string.ticket_swap_out else R.string.ticket_swap_return)
    Column(Modifier.fillMaxWidth().padding(top = 96.dp).statusBarsPadding().padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = if (hasRet) 50.dp else 0.dp)
            .pointerInput(hasRet) {
                var dx = 0f
                detectHorizontalDragGestures(onDragEnd = { if (abs(dx) > size.width * .18f) swap(); dx = 0f }) { ch, d -> ch.consume(); dx += d }
            }) {
            if (hasRet) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).offset(y = (-50).dp).alpha(.78f)
                .semantics { role = Role.Button; contentDescription = swapLabel }.clickable { swap() }) {
                TripCard(trip, !frontRet, now, night, p, front = false, tear = 0f, fall = 0f, holes = 0, editing = null, passenger = around.passenger, onTear = {}, onForm = null, onField = null)
            }
            Box(Modifier.graphicsLayer { translationY = swapA.value * 26.dp.toPx(); alpha = 1f - swapA.value * .5f }) {
                TripCard(trip, frontRet, now, night, p, front = true, tear = tear.value, fall = fall.value, holes = holes, editing = editing, rise = rise.value, reduced = reduced, passenger = around.passenger,
                    onTear = { tearIt() }, onForm = onForm, onField = onField)
            }
        }
    }
    // under the pass: the return and the ski pass, secondary buttons on glass
    val k = remember(night) { SheetInk(night) }
    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(stringResource(if (hasRet) R.string.app_trip_btn_return else R.string.app_trip_btn_add_return), k, Modifier.weight(1f)) {
            haptics.tick(.3f); if (hasRet) swap() else onField(PassEdit.Dates)
        }
        if (skipassPrice != null) GlassButton(stringResource(R.string.app_trip_btn_skipass, "$skipassPrice ₾"), k, Modifier.weight(1f)) { haptics.tick(.3f); onSkipass() }
    }
}

/** A secondary button on glass (52, round, the sheet's surface). */
@Composable
internal fun GlassButton(text: String, k: SheetInk, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(k.surf).border(1.dp, k.line, RoundedCornerShape(26.dp))
        .semantics { role = Role.Button }.clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink, maxLines = 2)
    }
}

/**
 * Q08: no trip yet. The same paper, short: TLV, the destination faint with "where do we land?", and on the stub "when do
 * we fly?" and "no sign-up, kept on the phone". A tap anywhere opens the calendar (it is open under it already).
 */
@Composable
internal fun LiveTripEmpty(night: Boolean, onTap: () -> Unit) {
    val p = remember(night) { PassPaper(night) }
    Column(Modifier.fillMaxWidth().padding(top = 56.dp).statusBarsPadding().padding(horizontal = 20.dp)) {
        Column(Modifier.fillMaxWidth().shadow(22.dp, NotchedPaper(22.dp, 76.dp)).background(p.paper, NotchedPaper(22.dp, 76.dp))
            .clickable(onClick = onTap)) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.app_home_trip), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = p.ink2)
                    LegTag(stringResource(R.string.app_pass_out), p)
                }
                Codes("TLV", stringResource(R.string.ticket_city_tlv), "TBS", stringResource(R.string.trip_land_where), p, toFaint = true, onFrom = null, onTo = null)
            }
            Perforation(p)
            Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.trip_when_fly), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = p.ink)
                Text(stringResource(R.string.app_trip_local_note), Modifier.weight(1f), fontFamily = Gudi, fontSize = 12.5.sp, color = p.ink2)
            }
        }
    }
}

/**
 * Q08's calendar (a sheet): one month, arrows to the months around it; a first tap the way out, a second the way back,
 * a third starts again (PassEdits.tap); the full ski days between them shaded with a dot, as the pass counts them; past
 * days off. The summary line, "save" (on after the first tap) and "no return" (saves the way out only).
 */
@Composable
internal fun LiveCalendar(trip: Trip?, today: LocalDate, k: SheetInk, haptics: Haptics, onSave: (Trip) -> Unit) {
    val p = remember(k) { k }
    var out by remember { mutableStateOf(trip?.out?.date) }
    var ret by remember { mutableStateOf(trip?.ret?.date) }
    var month by remember { mutableStateOf(YearMonth.from(out ?: today)) }
    val locale = LocalConfiguration.current.locales[0]
    val tlv = "TLV · " + stringResource(R.string.ticket_city_tlv)
    val tbs = "TBS · " + stringResource(R.string.ticket_city_tbs)
    fun build(o: LocalDate, r: LocalDate?) = with(PassEdits) { trip?.withDates(o, r) ?: newTrip(o, r, tlv, tbs) }
    val draft = remember(out, ret) { out?.let { build(it, ret) } }
    val ski = draft?.skiDays()
    val acc = p.acc
    // the emulator taps a day as a finger would (cal:tap:2027-01-10), the month following it
    LaunchedEffectQa { c -> if (c.startsWith("cal:tap:")) runCatching { LocalDate.parse(c.removePrefix("cal:tap:")) }.getOrNull()?.let { d ->
        PassEdits.tap(out, ret, d).let { (o, r) -> out = o; ret = r }; month = YearMonth.from(d); Qa.log("redesign trip calendar tap $d") } }
    LiveSheet(k) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val title = month.month.getDisplayName(JTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year
            Text(title, Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = p.ink)
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val canBack = month > YearMonth.from(today)
            MonthArrow(if (rtl) "›" else "‹", stringResource(R.string.trip_month_prev), canBack, p) { month = month.minusMonths(1) }
            MonthArrow(if (rtl) "‹" else "›", stringResource(R.string.trip_month_next), true, p) { month = month.plusMonths(1) }
        }
        val first = WeekFields.of(locale).firstDayOfWeek
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 7) Text(first.plus(i.toLong()).getDisplayName(JTextStyle.NARROW_STANDALONE, locale), Modifier.weight(1f),
                fontFamily = Gudi, fontSize = 11.5.sp, color = p.ink2, textAlign = TextAlign.Center)
        }
        val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val start = month.atDay(1).minusDays(lead.toLong())
        val weeks = ((lead + month.lengthOfMonth()) + 6) / 7
        Column {
            for (w in 0 until weeks) Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val d = start.plusDays((w * 7 + i).toLong())
                    val inMonth = d.month == month.month
                    val past = d < today
                    val edge = d == out || d == ret
                    val skiD = !edge && ski != null && d in ski
                    Box(Modifier.weight(1f).height(44.dp).background(if (skiD) p.skiDayOf() else Color.Transparent)
                        .let { m -> if (past || !inMonth) m else m.clickable { haptics.tick(.2f); PassEdits.tap(out, ret, d).let { (o, r) -> out = o; ret = r } } }
                        .clearAndSetSemantics { selected = edge; contentDescription = DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(d) },
                        contentAlignment = Alignment.Center) {
                        if (edge) Box(Modifier.size(42.dp).clip(CircleShape).background(acc))
                        Text(d.dayOfMonth.toString(), Modifier.alpha(if (!inMonth || past) .45f else 1f), fontFamily = Gudi,
                            fontWeight = if (edge) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp,
                            color = if (edge) Color.White else if (!inMonth) p.ink2 else p.ink)
                        if (skiD) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(5.dp).clip(CircleShape).background(acc))
                    }
                }
            }
        }
        // the summary: a dot and the ski days, and at the end the flights' dates
        if (draft != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(acc))
            Text(skiLine(draft) ?: stringResource(R.string.trip_ski_unknown), Modifier.weight(1f), fontFamily = Gudi, fontSize = 13.sp, color = p.ink, maxLines = 2)
            val o = out!!
            Text(if (ret != null) stringResource(R.string.app_trip_range, "${o.dayOfMonth}.${o.monthValue}", "${ret!!.dayOfMonth}.${ret!!.monthValue}") else "${o.dayOfMonth}.${o.monthValue}",
                fontFamily = Gudi, fontSize = 13.sp, color = p.ink2, style = TextStyle(fontFeatureSettings = "tnum"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).heightIn(min = 52.dp).alpha(if (out == null) .4f else 1f).clip(RoundedCornerShape(26.dp)).background(acc)
                .semantics { role = Role.Button }.clickable(enabled = out != null) { haptics.tick(.4f); out?.let { onSave(build(it, ret)) } }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.trip_save), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            SecondaryButton(stringResource(R.string.app_trip_no_return), k) { haptics.tick(.3f); out?.let { onSave(build(it, null)) } }
        }
    }
}

/** A ski day's shade in the calendar (section 1). */
private fun SheetInk.skiDayOf(): Color = if (acc == Color(0xFFB79CFF)) Color(0x33B79CFF) else Color(0x246B3FD4)

@Composable
internal fun MonthArrow(sign: String, label: String, enabled: Boolean, k: SheetInk, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).alpha(if (enabled) 1f else .35f).semantics { role = Role.Button; contentDescription = label }
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(sign, fontFamily = Gudi, fontSize = 24.sp, color = k.ink)
    }
}

/** The emulator's commands while a screen is shown (qa/run.sh), each once. */
@Composable
internal fun LaunchedEffectQa(on: (String) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(RedesignQa.seq) { on(RedesignQa.cmd) }
}

/** The ski pass's price for the trip's ski days, an adult (round 40's rule), or null when the button is not shown. */
internal fun tripSkipass(around: Around, trip: Trip?): Int? {
    val pr = around.prices ?: return null
    if (!pr.valid(around.today)) return null
    val n = trip?.skiDayCount()?.takeIf { it > 0 } ?: return null
    return pr.price(n, false)?.total
}

package io.github.pini236.skiapp.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Note
import io.github.pini236.skiapp.ui.PrimaryButton
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.floor

private const val HOLES = 11
private val STUB = 100.dp
private val NOTCH = 11.dp

/** "10.1", as the site writes short dates. */
fun shortDate(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}"

/** "11–14.1", or "30.12–2.1" across a month, or "11.1" for one day. */
fun dayRange(r: ClosedRange<LocalDate>): String = when {
    r.start == r.endInclusive -> shortDate(r.start)
    r.start.monthValue == r.endInclusive.monthValue -> "${r.start.dayOfMonth}–${shortDate(r.endInclusive)}"
    else -> "${shortDate(r.start)}–${shortDate(r.endInclusive)}"
}

/** The pass's own colours (--bp-* on the site), and its glow at night. */
private class PassInk(val paper: Color, val paper2: Color, val ink: Color, val muted: Color, val strip: Color, val onStrip: Color, val acc: Color, val dash: Color, val rule: Color, val glow: Shadow?, val dark: Boolean)

@Composable
private fun passInk(): PassInk {
    val c = Ski.colors
    return PassInk(c.bpPaper, c.bpPaper2, c.bpInk, c.bpMuted, c.bpStrip, c.bpOnStrip, c.bpAccent, c.dash, if (c.dark) Color(0xFF3A4A66) else c.rule,
        if (c.dark) Shadow(Color(0xA6FFC85A), blurRadius = 14f) else null, c.dark)
}

@Composable private fun Label(text: String, p: PassInk, modifier: Modifier = Modifier, lines: Int = 1) =
    Text(text, modifier, style = Ski.type.label.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .05.em, textAlign = if (lines > 1) TextAlign.Center else TextAlign.Unspecified),
        color = p.muted, maxLines = lines, overflow = TextOverflow.Ellipsis)

@Composable private fun Value(text: String, p: PassInk, ltr: Boolean = false) =
    Text(text, style = Ski.type.bodyBold.copy(fontSize = 14.sp, lineHeight = 1.2.em, shadow = p.glow, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
        color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)

/** An airport code in the display face (Latin, so Karantina in every language); a place typed without a code shows smaller. */
@Composable private fun Code(code: String?, place: String, p: PassInk, color: Color = p.ink) {
    val t = code ?: place.ifBlank { "—" }
    Text(t, style = TextStyle(fontFamily = if (code != null) Karantina else Ski.type.display, fontWeight = FontWeight.Bold, fontSize = if (code != null) 50.sp else 30.sp,
        lineHeight = .8.em, shadow = p.glow, textDirection = TextDirection.Ltr), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun RowScope.Cell(label: String, p: PassInk, content: @Composable () -> Unit) =
    Column(Modifier.weight(1f)) { Label(label, p); content() }

@Composable private fun Grid(p: PassInk, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().drawBehind { drawRect(p.rule, Offset.Zero, Size(size.width, 1.dp.toPx())) }.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

/** The main part of the pass: the strip, the route and two rows of fields (AB5, H3). */
@Composable
private fun RowScope.Main(p: PassInk, strip: String, date: String, body: @Composable ColumnScope.() -> Unit) {
    val shape = remember { PassShape(seamAtEnd = true, r = NOTCH) }
    Column(Modifier.weight(1f).fillMaxHeight().shadow(10.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
        .clip(shape).background(p.paper).paperGrain(p.dark)) {
        Row(Modifier.fillMaxWidth().background(p.strip).padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strip, Modifier.weight(1f), style = Ski.type.label.copy(fontSize = 12.sp, letterSpacing = .04.em), color = p.onStrip, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, style = Ski.type.label.copy(fontSize = 12.sp, letterSpacing = .04.em, textDirection = TextDirection.Ltr), color = p.onStrip)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            body()
        }
    }
}

/** The route: from, the plane, to. */
@Composable
private fun Route(p: PassInk, fromLabel: String, toLabel: String, from: @Composable () -> Unit, to: @Composable () -> Unit, planeColor: Color = p.acc) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f)) { Label(fromLabel, p); from() }
        Icon(Icons.plane, null, Modifier.size(28.dp).align(Alignment.CenterVertically), tint = planeColor)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Label(toLabel, p); to() }
    }
}

@Composable private fun City(t: String, p: PassInk, end: Boolean = false) =
    Text(t, Modifier.fillMaxWidth(), style = Ski.type.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = if (end) TextAlign.End else TextAlign.Start)

/** The stub's face, and the dashed line of the perforation on its seam side. */
@Composable
private fun StubFace(p: PassInk, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier.fillMaxSize().background(p.paper2).paperGrain(p.dark)
            .drawBehind {
                // the seam: the start side of the stub (right in Hebrew)
                val x = if (rtl) size.width - 1.dp.toPx() else 1.dp.toPx()
                drawLine(p.dash, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }
            .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), content = content,
    )
}

/** The "barcode": the ridge above New Gudauri drawn as bars, the mountain, not a code anyone could scan (as on the site). */
@Composable
private fun Ridge(color: Color) {
    val h = remember { floatArrayOf(.30f, .42f, .55f, .48f, .62f, .80f, .70f, .58f, .66f, .92f, 1f, .86f, .74f, .60f, .68f, .78f, .64f, .50f, .44f, .56f, .70f, .62f, .48f, .36f, .42f, .30f, .24f, .34f, .28f, .20f, .26f, .18f) }
    Canvas(Modifier.width(80.dp).height(28.dp)) {
        val w = size.width / h.size
        for (i in h.indices) {
            val bw = (if (i % 3 == 0) 2f else if (i % 2 == 1) 1.2f else .7f) * size.width / 80f
            drawRect(color, Offset(i * w, size.height * (1 - h[i])), Size(bw, size.height * h[i]))
        }
    }
}

/**
 * The boarding pass for the user's own trip (decisions 19 and 27, H3 and H4): the outbound pass, and the return one a
 * swipe away. A tap on the stub tears it along the perforation, hole by hole, with the recorded tear and a tick of the
 * haptics per hole; dragging the stub down tears it by hand. It comes back. In left-to-right languages the whole pass
 * mirrors: the stub on the right (LT1).
 */
@Composable
fun TripPass(trip: Trip, now: LocalDateTime, haptics: Haptics, sounds: Sounds, onEdit: () -> Unit) {
    val p = passInk()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var showRet by remember { mutableStateOf(false) }
    val leg = if (showRet && trip.ret != null) trip.ret else trip.out
    val slide = remember { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }

    // the tear: 0..1 down the perforation, then the fall, then it comes back
    val tear = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    var holes by remember { mutableStateOf(0) }
    fun onTear(v: Float) {
        val n = floor(v * HOLES).toInt().coerceAtMost(HOLES)
        if (n > holes) { repeat(n - holes) { haptics.tick(.35f + (it % 3) * .1f) }; holes = n }
    }
    fun drop() = scope.launch {
        busy = true
        haptics.click(1f)
        Telemetry.event("ticket_tear", mapOf("leg" to if (leg === trip.ret) "ret" else "out"))
        fall.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        delay(1500)
        tear.snapTo(0f); fall.snapTo(0f); holes = 0
        sounds.play("ticket-land", .6f); haptics.click(.5f)
        busy = false
    }
    fun tearByTap() = scope.launch {
        if (busy) return@launch
        busy = true
        sounds.play("ticket-tear", .85f)
        tear.animateTo(1f, tween(520, easing = LinearEasing)) { onTear(value) }
        drop()
    }
    fun swap(to: Boolean) = scope.launch {
        if (trip.ret == null || busy) return@launch
        busy = true
        val dir = if (to) -1f else 1f // out the way the finger went, the other pass in from the other side
        sounds.play("ticket-slide", .7f); haptics.tick(.5f)
        slide.animateTo(dir * width * 1.1f * (if (rtl) -1f else 1f), tween(220))
        showRet = to
        Telemetry.event("ticket_swap", mapOf("to" to if (to) "ret" else "out"))
        slide.snapTo(-slide.value)
        slide.animateTo(0f, spring(dampingRatio = .75f, stiffness = 500f))
        sounds.play("ticket-land", .75f); haptics.click(.6f)
        busy = false
    }

    val swapLabel = stringResource(if (showRet) R.string.ticket_swap_out else R.string.ticket_swap_return)
    Column {
        Row(
            // the pass grows with its words (Georgian and large text are taller), never under the canvas's 230
            Modifier.fillMaxWidth().heightIn(min = 230.dp).height(IntrinsicSize.Min)
                .onSizeChanged { width = it.width.toFloat() }
                .pointerInput(trip.ret != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val x = slide.value
                            // forward in the reading direction brings the return pass, back brings the outbound
                            val forward = if (rtl) x > 0 else x < 0
                            if (trip.ret != null && abs(x) > width * .22f && forward != showRet) swap(!showRet)
                            else scope.launch { slide.animateTo(0f, spring(dampingRatio = .6f)) }
                        },
                        onDragCancel = { scope.launch { slide.animateTo(0f) } },
                    ) { ch, dx ->
                        if (busy) return@detectHorizontalDragGestures
                        ch.consume()
                        // without a return pass the paper only gives a little
                        scope.launch { slide.snapTo(slide.value + dx * if (trip.ret == null) .25f else 1f) }
                    }
                }
                .semantics { if (trip.ret != null) customActions = listOf(CustomAccessibilityAction(swapLabel) { swap(!showRet); true }) }
                // after the finger is read: the pass follows it without moving the ground the finger is read on
                .graphicsLayer { translationX = slide.value; rotationZ = slide.value / width.coerceAtLeast(1f) * 6f },
        ) {
            val isRet = leg === trip.ret
            Main(p, stringResource(R.string.app_home_trip) + " · " + stringResource(if (isRet) R.string.app_pass_ret else R.string.app_pass_out), shortDate(leg.date)) {
                Route(p, stringResource(R.string.ticket_from), stringResource(R.string.ticket_to),
                    { Code(leg.fromCode, leg.fromCity, p); City(leg.fromCity, p) },
                    { Code(leg.toCode, leg.toCity, p); City(leg.toCity, p, end = true) })
                Grid(p) {
                    Cell(stringResource(R.string.ticket_flight), p) { Value(leg.flight.ifBlank { "—" }, p, ltr = true) }
                    Cell(stringResource(R.string.ticket_departs), p) { Value(leg.departs?.toString() ?: "—", p, ltr = true) }
                    Cell(stringResource(R.string.ticket_arrives), p) { Value(leg.arrives?.toString() ?: "—", p, ltr = true) }
                }
                Grid(p) {
                    Cell(stringResource(R.string.app_pass_traveller), p) { Value(stringResource(R.string.app_pass_me), p) }
                    Cell(stringResource(R.string.ticket_ski_days), p) { Value(trip.skiDays()?.let { dayRange(it) } ?: "—", p, ltr = true) }
                    val other = if (isRet) trip.out else trip.ret
                    Cell(stringResource(if (isRet) R.string.app_pass_out else R.string.app_pass_ret), p) {
                        Value(other?.let { o -> if (o.departs != null && o.departs < LocalTime.of(6, 0)) stringResource(R.string.app_pass_at_night, shortDate(o.date)) else shortDate(o.date) } ?: "—", p)
                    }
                }
            }
            // the slot: what is left once the stub is gone, and the stub itself
            Box(Modifier.width(STUB).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(vertical = 2.dp).border(2.dp, p.dash.copy(alpha = .8f)).padding(6.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.ticket_torn) + "\n" + stringResource(R.string.ticket_see_you), style = Ski.type.small.copy(fontSize = 12.sp, textAlign = TextAlign.Center), color = p.muted)
                }
                val notch = remember { PassShape(seamAtEnd = false, r = NOTCH) }
                val torn = remember { TornShape(seamAtStart = true) }
                val t = tear.value; val f = fall.value
                val s = if (rtl) -1f else 1f
                val tearLabel = stringResource(R.string.ticket_tear_stub)
                StubFace(p,
                    Modifier
                        .semantics { role = Role.Button; contentDescription = tearLabel }
                        .pointerInput(Unit) { detectTapGestures { tearByTap() } }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { if (!busy) scope.launch { if (tear.value > .55f) { sounds.play("ticket-tear", .6f); tear.animateTo(1f) { onTear(value) }; drop() } else { tear.animateTo(0f, spring(dampingRatio = .5f)); holes = 0 } } },
                            ) { ch, dy ->
                                if (busy) return@detectVerticalDragGestures
                                ch.consume()
                                scope.launch { val v = (tear.value + dy / size.height).coerceIn(0f, 1f); tear.snapTo(v); onTear(v) }
                            }
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 1f)
                            rotationZ = s * (9f * t + 25f * f)
                            translationX = s * (4.dp.toPx() * t + 58.dp.toPx() * f)
                            translationY = 2.dp.toPx() * t + 210.dp.toPx() * f * f
                            alpha = 1f - (f * 1.4f - .4f).coerceIn(0f, 1f)
                        }
                        .shadow(if (t > 0f) 6.dp else 10.dp, if (t > 0f) torn else notch, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                        .clip(if (t > 0f) torn else notch),
                ) {
                    if (!isRet) {
                        val days = trip.daysToFlight(now)
                        Label(stringResource(R.string.app_pass_more), p)
                        Big(days.toString(), p)
                        Label(if (days == 0) stringResource(R.string.ticket_stub_departing)
                            else pluralStringResource(if (p.dark) R.plurals.app_pass_nights else R.plurals.app_pass_days, days), p, lines = 2)
                    } else {
                        val n = trip.skiDayCount()
                        Label(stringResource(R.string.app_pass_first), p)
                        Big(if (n > 0) n.toString() else "—", p)
                        Label(pluralStringResource(R.plurals.app_pass_ski_days, if (n > 0) n else 5), p, lines = 2)
                    }
                    if (leg.fromCode != null && leg.toCode != null)
                        Text("${leg.fromCode} › ${leg.toCode}", Modifier.padding(top = 4.dp), style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                            lineHeight = 1.em, shadow = p.glow, textDirection = TextDirection.Ltr), color = p.ink)
                    Spacer(Modifier.weight(1f))
                    Ridge(p.ink)
                }
            }
        }
        // under the pass: what the gestures do, and editing the trip
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val hint = stringResource(R.string.ticket_hint_tear) + if (trip.ret != null) " · " + stringResource(if (showRet) R.string.app_pass_swipe_out else R.string.app_pass_swipe_ret) else ""
            Text(hint, Modifier.weight(1f), style = Ski.type.small.copy(fontSize = 12.sp), color = Ski.colors.muted)
            Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onEdit).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.edit, null, Modifier.size(17.dp), tint = p.acc)
                Text(stringResource(R.string.app_edit), style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = p.acc)
            }
        }
    }
}

@Composable private fun Big(t: String, p: PassInk, color: Color = p.acc) =
    Text(t, style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = .8.em, shadow = p.glow), color = color)

/**
 * No trip yet (H1): the same pass, blank, waiting to be filled in, with "add my flight" over it, and a line on where
 * it is kept (only on this phone).
 */
@Composable
fun EmptyPass(onAdd: () -> Unit) {
    val p = passInk()
    val faint = if (p.dark) Color(0xFF3A4A66) else Color(0xFFC3CEDA)
    Column {
        Box(Modifier.fillMaxWidth().heightIn(min = 216.dp).height(IntrinsicSize.Min)) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Main(p, stringResource(R.string.app_home_trip), stringResource(R.string.app_pass_not_set)) {
                    Route(p, stringResource(R.string.ticket_from), stringResource(R.string.ticket_to),
                        { Code("???", "", p, faint) }, { Code("???", "", p, faint) }, planeColor = faint)
                    Grid(p) {
                        Cell(stringResource(R.string.ticket_flight), p) { Blank(54.dp, p) }
                        Cell(stringResource(R.string.ticket_departs), p) { Blank(40.dp, p) }
                        Cell(stringResource(R.string.ticket_ski_days), p) { Blank(50.dp, p) }
                    }
                }
                val notch = remember { PassShape(seamAtEnd = false, r = NOTCH) }
                Box(Modifier.width(STUB).fillMaxHeight().shadow(10.dp, notch, ambientColor = Color(0x2E000000), spotColor = Color(0x2E000000)).clip(notch)) {
                    StubFace(p, Modifier) {
                        Spacer(Modifier.weight(1f))
                        Label(stringResource(R.string.app_pass_more), p)
                        Big("?", p, faint)
                        Label(pluralStringResource(R.plurals.app_pass_days, 5), p, lines = 2)
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            // over the main part, under the strip
            Box(Modifier.matchParentSize().padding(top = 64.dp, end = STUB), contentAlignment = Alignment.Center) {
                PrimaryButton(stringResource(R.string.app_home_add_trip), Icons.plus, onAdd, full = false)
            }
        }
        Note(stringResource(R.string.app_home_local_note), Icons.lock, Modifier.padding(top = 10.dp))
    }
}

@Composable private fun Blank(w: Dp, p: PassInk) = Box(Modifier.padding(top = 5.dp).width(w).height(12.dp).drawBehind {
    drawLine(p.dash, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
})


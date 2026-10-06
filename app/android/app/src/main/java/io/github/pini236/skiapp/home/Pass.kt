package io.github.pini236.skiapp.home

import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.PassEdit
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Karantina
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

/**
 * Who travels, when there is an account to speak of (decision 42 on the site, decision 50 here): [name] is mine when
 * signed in (null: a guest without one), and a tap opens the account card or the way in (P1 to P7 of round 12).
 */
class Passenger(val name: String?, val onTap: () -> Unit)
val LocalPassenger = compositionLocalOf<Passenger?> { null }

@Composable private fun Value(text: String, p: PassInk, ltr: Boolean = false) =
    Text(text, style = Ski.type.bodyBold.copy(fontSize = 14.sp, lineHeight = 1.2.em, shadow = p.glow, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
        color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)

/**
 * An airport code in the display face (Latin, so Karantina in every language), at the site's size (.bp-code: 54, and 48
 * on a phone up to 420 wide); a place saved without a code, from before K-5, shows smaller.
 */
@Composable private fun Code(code: String?, place: String, p: PassInk, color: Color = p.ink, size: Int? = null) {
    val t = code ?: place.ifBlank { "—" }
    val big = size ?: if (LocalConfiguration.current.screenWidthDp <= 420) 48 else 54
    Text(t, style = TextStyle(fontFamily = if (code != null) Karantina else Ski.type.display, fontWeight = FontWeight.Bold, fontSize = if (code != null) big.sp else 30.sp,
        lineHeight = .8.em, shadow = p.glow, textDirection = TextDirection.Ltr), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun RowScope.Cell(label: String, p: PassInk, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) =
    Column(Modifier.weight(1f).let { if (onClick != null) it.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick) else it }) { Label(label, p); content() }

/** An empty field of the pass that waits to be filled (round 20): "+ number", "+ time", dashed, in the accent. */
@Composable private fun AddChip(text: String, p: PassInk) =
    Text(text, Modifier.padding(top = 2.dp).drawBehind {
        drawRect(p.acc, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
    }.padding(horizontal = 6.dp, vertical = 2.dp), style = Ski.type.bodyBold.copy(fontSize = 12.5.sp, lineHeight = 1.2.em), color = p.acc, maxLines = 1)

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
private fun Route(p: PassInk, fromLabel: String, toLabel: String, from: @Composable () -> Unit, to: @Composable () -> Unit, planeColor: Color = p.acc,
                  onFrom: (() -> Unit)? = null, onTo: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f).let { m -> onFrom?.let { m.clickable(role = Role.Button, onClick = it) } ?: m }) { Label(fromLabel, p); from() }
        Icon(Icons.plane, null, Modifier.size(28.dp).align(Alignment.CenterVertically), tint = planeColor)
        Column(Modifier.weight(1f).let { m -> onTo?.let { m.clickable(role = Role.Button, onClick = it) } ?: m }, horizontalAlignment = Alignment.End) { Label(toLabel, p); to() }
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

/** Where a card sits in the stack, and how dim it is (the site's .bp.is-front, .is-back and .shuffle, AB5). */
private class Pose(val x: Float, val y: Float, val rot: Float, val scale: Float, val dim: Float) {
    fun to(o: Pose, t: Float) = Pose(x + (o.x - x) * t, y + (o.y - y) * t, rot + (o.rot - rot) * t, scale + (o.scale - scale) * t, dim + (o.dim - dim) * t)
}

/** How far the card behind shows above the one in front (its strip: "your trip · return · 15.1"). */
val PASS_PEEK = 46.dp

/**
 * The boarding pass for the user's own trip (decisions 19 and 27, H3 and H4), with the return pass behind it, its top
 * showing, as on the site (AB5, AB6): a tap on the one behind brings it to the front (a swipe on the front one too).
 * A tap on the stub tears it along the perforation, hole by hole, with the recorded tear and a tick of the haptics per
 * hole; dragging the stub down tears it by hand. It comes back. In left-to-right languages the whole stack mirrors:
 * the stub on the right, the card behind leaning the other way (LT1).
 */
@Composable
fun TripPass(trip: Trip, now: LocalDateTime, haptics: Haptics, sounds: Sounds, onEdit: () -> Unit,
             /** Round 20: a tap on a field of the front pass opens its own sheet (null: the pass is only shown). */
             onField: ((PassEdit) -> Unit)? = null) {
    val p = passInk()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val hasRet = trip.ret != null
    var showRet by remember { mutableStateOf(false) }
    val shuffle = remember { Animatable(0f) } // 0..1 while the two cards change places
    val drag = remember { Animatable(0f) } // the front card following a swipe a little
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
        Telemetry.event("ticket_tear", mapOf("leg" to if (showRet && hasRet) "ret" else "out"))
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
    // the site's shuffle: the front card slides down and out, the one behind comes forward, and they settle swapped
    fun swap() = scope.launch {
        if (!hasRet || busy) return@launch
        busy = true
        sounds.play("ticket-slide", .7f); haptics.tick(.5f)
        launch { delay(420); sounds.play("ticket-land", .75f); haptics.click(.6f) }
        launch { drag.animateTo(0f) }
        shuffle.animateTo(1f, tween(640, easing = FastOutSlowInEasing))
        showRet = !showRet
        shuffle.snapTo(0f)
        Telemetry.event("ticket_swap", mapOf("to" to if (showRet) "ret" else "out"))
        busy = false
    }

    // the poses, in the reading direction: the card behind sits up and toward the end, leaning back (the site's px as dp)
    val s = if (rtl) 1f else -1f
    val front = Pose(0f, 0f, 0f, 1f, 0f)
    val back = Pose(-12f * s, -PASS_PEEK.value, 2.6f * s, .955f, if (p.dark) .28f else .10f)
    val outFront = Pose(26f * s, 84f, -7f * s, 1f, 0f)
    val outBack = Pose(-4f * s, -26f, .6f * s, .985f, if (p.dark) .14f else .05f)
    val t = shuffle.value
    val k = .4f
    val poseA = if (t < k) front.to(outFront, t / k) else outFront.to(back, (t - k) / (1 - k)) // the card in front now
    val poseB = if (t < k) back.to(outBack, t / k) else outBack.to(front, (t - k) / (1 - k)) // the card behind now
    val frontIsRet = showRet && hasRet
    val swapLabel = stringResource(if (frontIsRet) R.string.ticket_swap_out else R.string.ticket_swap_return)

    @Composable
    fun card(isRet: Boolean, pose: Pose, inFront: Boolean) {
        Box(
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    translationX = pose.x.dp.toPx() + if (inFront) drag.value else 0f
                    translationY = pose.y.dp.toPx()
                    rotationZ = pose.rot + if (inFront) drag.value / width.coerceAtLeast(1f) * 5f else 0f
                    scaleX = pose.scale; scaleY = pose.scale
                    transformOrigin = TransformOrigin(.5f, .6f)
                }
                .drawWithContent { drawContent(); if (pose.dim > .005f) drawRect(Color.Black, alpha = pose.dim) }
                // the card behind: one tap brings it forward (the whole card, under the one in front only its top shows).
                // To a screen reader it is that one button: its fields are hidden under the front card, so they are not read
                .let { m -> if (!inFront) m.clearAndSetSemantics { contentDescription = swapLabel; role = Role.Button; onClick(swapLabel) { swap(); true } }.clickable(onClick = { swap() }) else m },
        ) {
            PassCard(trip, isRet, now, p, rtl, front = inFront, onField = if (inFront) onField else null,
                tearT = if (inFront) tear.value else 0f, fallT = if (inFront) fall.value else 0f,
                onTap = { tearByTap() },
                onTearDrag = { dy, h -> if (!busy) scope.launch { val v = (tear.value + dy / h).coerceIn(0f, 1f); tear.snapTo(v); onTear(v) } },
                onTearEnd = { if (!busy) scope.launch { if (tear.value > .55f) { sounds.play("ticket-tear", .6f); tear.animateTo(1f) { onTear(value) }; drop() } else { tear.animateTo(0f, spring(dampingRatio = .5f)); holes = 0 } } })
        }
    }

    Column {
        Box(
            Modifier.fillMaxWidth().padding(top = if (hasRet) PASS_PEEK else 0.dp)
                .onSizeChanged { width = it.width.toFloat() }
                .pointerInput(hasRet) {
                    // a swipe on the front card works too: far enough either way, and the cards change places
                    detectHorizontalDragGestures(
                        onDragEnd = { if (hasRet && abs(drag.value) > width * .18f) swap() else scope.launch { drag.animateTo(0f, spring(dampingRatio = .6f)) } },
                        onDragCancel = { scope.launch { drag.animateTo(0f) } },
                    ) { ch, dx ->
                        if (busy) return@detectHorizontalDragGestures
                        ch.consume()
                        scope.launch { drag.snapTo(drag.value + dx * if (hasRet) .45f else .2f) }
                    }
                }
                .semantics { if (hasRet) customActions = listOf(CustomAccessibilityAction(swapLabel) { swap(); true }) },
        ) {
            // drawn back to front; past the middle of a swap the one coming forward is on top
            if (hasRet) {
                if (t < k) { card(!frontIsRet, poseB, false); card(frontIsRet, poseA, true) }
                else { card(frontIsRet, poseA, true); card(!frontIsRet, poseB, false) }
            } else card(false, front, true)
        }
        // under the pass: what the gestures do, and editing the trip
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val hint = (if (hasRet) stringResource(R.string.ticket_hint_swap).trimEnd() + " " else "") + stringResource(R.string.ticket_hint_tear)
            Text(hint, Modifier.weight(1f), style = Ski.type.small.copy(fontSize = 12.sp), color = Ski.colors.muted)
            Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onEdit).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.edit, null, Modifier.size(17.dp), tint = p.acc)
                Text(stringResource(R.string.app_edit), style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = p.acc)
            }
        }
    }
}

/** One pass: the main part and the stub. Only the card in front takes touches on its stub. */
@Composable
private fun PassCard(trip: Trip, isRet: Boolean, now: LocalDateTime, p: PassInk, rtl: Boolean, front: Boolean, tearT: Float, fallT: Float,
                     onTap: () -> Unit, onTearDrag: (dy: Float, height: Float) -> Unit, onTearEnd: () -> Unit, onField: ((PassEdit) -> Unit)? = null) {
    // round 20: each field opens its own sheet; the way back's airports are the way out's, reversed
    fun on(e: PassEdit): (() -> Unit)? = onField?.let { f -> { f(e) } }
    val leg = if (isRet) trip.ret!! else trip.out
    Row(
        // the pass grows with its words (Georgian and large text are taller), never under the canvas's 230
        Modifier.fillMaxWidth().heightIn(min = 230.dp).height(IntrinsicSize.Min),
    ) {
        // the site's strip: "your trip · return", and " · overnight" on a leg that leaves before 06:00 (A-33)
        val night = if (leg.departs != null && leg.departs < LocalTime.of(6, 0)) stringResource(R.string.ticket_note_overnight) else ""
        Main(p, stringResource(if (isRet) R.string.ticket_strip_mine_return else R.string.ticket_strip_mine_out, night), shortDate(leg.date)) {
            Route(p, stringResource(R.string.ticket_from), stringResource(R.string.ticket_to),
                { Code(leg.fromCode, leg.fromCity, p); City(leg.fromCity, p) },
                { Code(leg.toCode, leg.toCity, p); City(leg.toCity, p, end = true) },
                onFrom = on(PassEdit.Place(from = !isRet)), onTo = on(PassEdit.Place(from = isRet)))
            // round 20: what is not filled in waits as "+ number" and "+ time" (on the front pass, when it can be filled)
            val add = onField != null
            Grid(p) {
                Cell(stringResource(R.string.ticket_flight), p, on(PassEdit.Flight(isRet))) {
                    if (leg.flight.isBlank() && add) AddChip(stringResource(R.string.trip_add_number), p) else Value(leg.flight.ifBlank { "—" }, p, ltr = true)
                }
                Cell(stringResource(R.string.ticket_departs), p, on(PassEdit.Time(isRet, departs = true))) {
                    if (leg.departs == null && add) AddChip(stringResource(R.string.trip_add_time), p) else Value(leg.departs?.toString() ?: "—", p, ltr = true)
                }
                Cell(stringResource(R.string.ticket_arrives), p, on(PassEdit.Time(isRet, departs = false))) {
                    if (leg.arrives == null && add) AddChip(stringResource(R.string.trip_add_time), p) else Value(leg.arrives?.toString() ?: "—", p, ltr = true)
                }
            }
            Grid(p) {
                Cell(stringResource(R.string.app_pass_traveller), p) {
                    val who = LocalPassenger.current
                    if (who == null) Value(stringResource(R.string.app_pass_me), p)
                    else Text(who.name ?: stringResource(R.string.ticket_pax_guest),
                        // the site's .bp-who: dotted underline on the name, the guest in the accent; only the front pass takes the tap
                        Modifier.heightIn(min = 24.dp).let { m -> if (front) m.clickable(role = Role.Button, onClick = who.onTap) else m },
                        style = Ski.type.bodyBold.copy(fontSize = 14.sp, lineHeight = 1.2.em, shadow = p.glow, textDecoration = TextDecoration.Underline),
                        color = if (who.name == null) p.acc else p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Cell(stringResource(R.string.ticket_ski_days), p, on(PassEdit.Dates)) { Value(trip.skiDays()?.let { dayRange(it) } ?: "—", p, ltr = true) }
                val other = if (isRet) trip.out else trip.ret
                Cell(stringResource(if (isRet) R.string.app_pass_out else R.string.app_pass_ret), p, on(PassEdit.Dates)) {
                    Value(other?.let { shortDate(it.date) } ?: "—", p)
                }
            }
        }
        // the slot: what is left once the stub is gone, and the stub itself
        Box(Modifier.width(STUB).fillMaxHeight()) {
            if (front) Column(Modifier.fillMaxSize().padding(vertical = 2.dp).border(2.dp, p.dash.copy(alpha = .8f)).padding(6.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.ticket_torn) + "\n" + stringResource(R.string.ticket_see_you), style = Ski.type.small.copy(fontSize = 12.sp, textAlign = TextAlign.Center), color = p.muted)
            }
            val notch = remember { PassShape(seamAtEnd = false, r = NOTCH) }
            val torn = remember { TornShape(seamAtStart = true) }
            val sx = if (rtl) -1f else 1f
            val tearLabel = stringResource(R.string.ticket_tear_stub)
            StubFace(p,
                Modifier
                    .let { m ->
                        if (!front) m else m.semantics { role = Role.Button; contentDescription = tearLabel }
                            .pointerInput(Unit) { detectTapGestures { onTap() } }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(onDragEnd = onTearEnd) { ch, dy -> ch.consume(); onTearDrag(dy, size.height.toFloat()) }
                            }
                    }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 1f)
                        rotationZ = sx * (9f * tearT + 25f * fallT)
                        translationX = sx * (4.dp.toPx() * tearT + 58.dp.toPx() * fallT)
                        translationY = 2.dp.toPx() * tearT + 210.dp.toPx() * fallT * fallT
                        alpha = 1f - (fallT * 1.4f - .4f).coerceIn(0f, 1f)
                    }
                    .shadow(if (tearT > 0f) 6.dp else 10.dp, if (tearT > 0f) torn else notch, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                    .clip(if (tearT > 0f) torn else notch),
            ) {
                if (!isRet) when (val st = trip.stage(now)) {
                    is Trip.Stage.Before -> {
                        val days = st.days
                        Label(stringResource(R.string.app_pass_more), p)
                        Big(days.toString(), p)
                        Label(if (days == 0) stringResource(R.string.ticket_stub_departing)
                            else pluralStringResource(if (p.dark) R.plurals.app_pass_nights else R.plurals.app_pass_days, days), p, lines = 2)
                    }
                    // during the trip: "ski day 2 of 4" (A-43); after the return the stub stays, with no count
                    is Trip.Stage.SkiDay -> {
                        Label(stringResource(R.string.ticket_stub_ski_day), p)
                        Big(st.n.toString(), p)
                        Label(stringResource(R.string.ticket_stub_of, st.of), p, lines = 2)
                    }
                    Trip.Stage.Over -> Unit
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
}

@Composable private fun Big(t: String, p: PassInk, color: Color = p.acc) =
    Text(t, style = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = .8.em, shadow = p.glow), color = color)

/**
 * No trip yet (H1, round 20): the same pass, already from Tel Aviv to Tbilisi (dashed: not set yet), and one question,
 * "when do you fly?", that opens the calendar. The rest waits on the pass once the dates are in.
 */
@Composable
fun EmptyPass(onWhen: () -> Unit) {
    // the site's .bp-empty (round 18): the page's own colours, not the printed pass's, with the strip in the run blue
    // (light with dark words at night) and the blanks in --rule
    val c = Ski.colors
    val p = PassInk(c.paper, c.paper, c.ink, c.muted, c.blue, c.onBoard, c.rule, c.rule, c.rule, null, c.dark)
    val faint = c.rule
    Column {
        Box(Modifier.fillMaxWidth().heightIn(min = 216.dp).height(IntrinsicSize.Min)) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Main(p, stringResource(R.string.app_home_trip), stringResource(R.string.app_pass_not_set)) {
                    Route(p, stringResource(R.string.ticket_from), stringResource(R.string.ticket_to),
                        { Dashed(c.blue) { Code("TLV", "", p, c.blue, size = 46) } },
                        { Dashed(c.blue) { Code("TBS", "", p, c.blue, size = 46) } }, planeColor = faint)
                    // the one question (the canvas's trip-1): a dashed button with the calendar
                    val label = stringResource(R.string.trip_when_fly)
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        Row(Modifier.heightIn(min = 52.dp).background(c.blue.copy(alpha = .08f)).drawBehind {
                            val w = 2.dp.toPx()
                            drawRect(c.blue, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                        }.clickable(role = Role.Button, onClick = onWhen).semantics { contentDescription = label }.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.calendar, null, Modifier.size(24.dp), tint = c.blue)
                            Text(label, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.blue, maxLines = 1)
                        }
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
        }
    }
}

/** A dashed frame around what is written but not set yet (the empty pass's TLV and TBS). */
@Composable
private fun Dashed(color: Color, content: @Composable () -> Unit) = Box(Modifier.drawBehind {
    val w = 1.5.dp.toPx()
    drawRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
}.padding(horizontal = 8.dp, vertical = 6.dp)) { content() }

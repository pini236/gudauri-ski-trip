package io.github.pini236.skiapp.redesign

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.Around
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.season.shortDate
import io.github.pini236.skiapp.skipass.Prices

/**
 * UX pass part 3 (design/handoff/2026-11-26-redesign-ux-pass/part-3-skipass.md, boards SP1, SP2): the ski pass is an
 * object on a lanyard over the veiled mountain, not a number in the sky; three short rows replace the paragraphs; the
 * day tiles are the control (no stepper). Prices and the rule are unchanged (skipass/Prices: the cheapest of up to two
 * tickets, up to 14 days, no information past `until`, GEL only, nothing bought here).
 */
@Composable
internal fun BoxScope.LiveSkipass(around: Around, night: Boolean, haptics: Haptics, onOffice: () -> Unit = {}) {
    val k = remember(night) { SheetInk(night) }
    val prices = around.prices
    val trip = around.trip
    val tripDays = trip?.skiDayCount()?.takeIf { it in 1..Prices.MAX_DAYS }
    var child by remember { mutableStateOf(false) }
    var days by remember { mutableIntStateOf(tripDays ?: 1) }
    var fromTrip by remember { mutableStateOf(tripDays != null) }
    val valid = prices != null && prices.valid(around.today)
    val gel = @Composable { n: Int -> stringResource(R.string.app_skipass_price, n.toString()) }
    LaunchedEffectQa { c ->
        when {
            c == "skipass:child" -> { child = true; Qa.log("redesign skipass child") }
            c == "skipass:adult" -> child = false
            c.startsWith("skipass:days:") -> c.removePrefix("skipass:days:").toIntOrNull()?.let { days = it.coerceIn(1, MAX_TILES); fromTrip = false; Qa.log("redesign skipass days $days") }
        }
    }
    LaunchedEffect(Unit) { Qa.log("redesign skipass open ${if (valid) "valid" else "no-info"} $days") }
    val who = if (child) stringResource(R.string.app_skipass_child, (prices?.childFrom ?: 6).toString(), (prices?.childTo ?: 12).toString())
        else stringResource(R.string.app_skipass_adult)
    val q = if (valid) prices!!.price(days, child) else null

    // the pass swings once when the days change: 3 to 5 to 3 degrees, 300 ms (none with reduced motion)
    val still = io.github.pini236.skiapp.status.reducedMotion()
    val swing = remember { Animatable(3f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(days) {
        if (first) { first = false; return@LaunchedEffect }
        if (!still) { swing.animateTo(5f, tween(150)); swing.animateTo(3f, tween(150)) }
    }
    val paper = if (night) Color(0xFF16213A) else Color.White
    val lanyard = k.acc

    // fit: the pass (452 dp from the top with the lanyard) shrinks, top-anchored, to the room the sheet leaves
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    var sheetPx by remember { mutableIntStateOf(0) }
    val room = screenH - with(density) { sheetPx.toDp().value } - 8f
    val fit = if (sheetPx == 0) 1f else (room / 452f).coerceIn(.45f, 1f)
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = fit; scaleY = fit; transformOrigin = TransformOrigin(.5f, 0f) }) {
    // the lanyard: two bands from above the screen to the slot
    Canvas(Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter)) {
        val w = size.width
        val slotX = w / 2f; val slotY = 142.dp.toPx()
        val stroke = 7.dp.toPx()
        drawLine(lanyard, Offset(w * 150f / 390f, -60f), Offset(slotX - 14.dp.toPx(), slotY), stroke)
        drawLine(lanyard.copy(alpha = .82f), Offset(w * 262f / 390f, -60f), Offset(slotX + 14.dp.toPx(), slotY), stroke)
    }
    Box(Modifier.align(Alignment.TopCenter).padding(top = 136.dp).size(232.dp, 316.dp)
        .graphicsLayer { rotationZ = swing.value; transformOrigin = TransformOrigin(.5f, 0f); shadowElevation = 0f }
        .shadow(18.dp, RoundedCornerShape(22.dp), spotColor = Color(0x57_0A192D)).clip(RoundedCornerShape(22.dp)).background(paper)) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(46.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(k.line))
            Text(stringResource(R.string.app_skipass_season, stringResource(R.string.map_place_gudauri), prices?.season ?: ""), Modifier.padding(top = 10.dp),
                fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = k.ink2)
            Crossfade(Triple(valid, q?.total, days to child), animationSpec = tween(150), label = "skipass") { (ok, total, _) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        !ok -> Text(stringResource(R.string.app_skipass_no_info), Modifier.padding(top = 40.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            color = k.ink, textAlign = TextAlign.Center)
                        total == null -> Text(stringResource(R.string.app_skipass_too_long, days.toString()), Modifier.padding(top = 40.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold,
                            fontSize = 17.sp, color = k.ink, textAlign = TextAlign.Center)
                        else -> {
                            val big = stringResource(R.string.app_skipass_price, total.toString())
                            Row(Modifier.padding(top = 6.dp).clearAndSetSemantics { contentDescription = big }, verticalAlignment = Alignment.Bottom) {
                                Text(total.toString(), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(76f), lineHeight = 84.sp, color = k.ink,
                                    style = TextStyle(fontFeatureSettings = "tnum"))
                                Text("₾", Modifier.padding(start = 3.dp, bottom = 12.dp), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = bigSp(30f), color = k.ink)
                            }
                            Text(pluralStringResource(R.plurals.app_skipass_len, days, days.toString()) + " · " + who, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                color = k.ink, textAlign = TextAlign.Center)
                            if (fromTrip && trip != null) trip.skiDays()?.let { r ->
                                val dates = stringResource(R.string.app_trip_range, shortDate(r.start), shortDate(r.endInclusive))
                                Text(stringResource(R.string.app_skipass_by_trip, dates), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2, textAlign = TextAlign.Center)
                            }
                            if (q != null && q.parts.size == 2)
                                Text(stringResource(R.string.app_skipass_two_tickets), Modifier.padding(top = 2.dp), fontFamily = Gudi, fontSize = 11.5.sp, lineHeight = 15.sp, color = k.ink2,
                                    textAlign = TextAlign.Center, maxLines = 3)
                            val daily = prices?.daily(child)
                            if (daily != null && daily * days > total)
                                Box(Modifier.padding(top = 8.dp).heightIn(min = 26.dp).clip(RoundedCornerShape(13.dp))
                                    .background(if (night) Color(0x3346D08A) else Color(0xFFDDF2E5)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.app_skipass_saves, gel(daily * days - total)), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                        color = if (night) Color(0xFF7BE3AC) else Color(0xFF0F5C31), textAlign = TextAlign.Center)
                                }
                        }
                    }
                }
            }
        }
        // the foot of the pass: the accent band with a two-layer mountain
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(74.dp)) {
            drawRect(k.acc)
            val w = size.width; val h = size.height
            val back = Path().apply { moveTo(0f, h); lineTo(0f, h * .55f); lineTo(w * .3f, h * .2f); lineTo(w * .52f, h * .5f); lineTo(w * .72f, h * .3f); lineTo(w, h * .6f); lineTo(w, h); close() }
            val front = Path().apply { moveTo(0f, h); lineTo(0f, h * .8f); lineTo(w * .22f, h * .55f); lineTo(w * .5f, h * .85f); lineTo(w * .78f, h * .6f); lineTo(w, h * .82f); lineTo(w, h); close() }
            drawPath(back, Color.White.copy(alpha = .18f)); drawPath(front, Color.White.copy(alpha = .22f))
        }
    }
    }

    // the sheet
    Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetPx = it.height }) {
        LiveSheet(k, opaque = true) {
            // 1. adult or child
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(k.line)) {
                for (kid in listOf(false, true)) {
                    val on = child == kid
                    val t = if (!kid) stringResource(R.string.app_skipass_adult) else stringResource(R.string.app_skipass_child, (prices?.childFrom ?: 6).toString(), (prices?.childTo ?: 12).toString())
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).padding(3.dp).clip(RoundedCornerShape(19.dp)).background(if (on) k.acc else Color.Transparent)
                        .semantics { role = Role.RadioButton; selected = on }.clickable { haptics.tick(.3f); child = kid }, contentAlignment = Alignment.Center) {
                        Text(t, fontFamily = Gudi, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, color = if (on) k.onAcc else k.ink2, maxLines = 1)
                    }
                }
            }
            // 2. how many days: the tiles are the control (none past `until`)
            if (valid) {
                Text(stringResource(R.string.app_skipass_days_q), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
                val scroll = rememberScrollState()
                LaunchedEffect(days) { if (days > 6) scroll.animateScrollTo(((days - 6) * 58 * 3).coerceAtMost(scroll.maxValue)) }
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 1..MAX_TILES) {
                        val on = d == days
                        val pr = prices!!.price(d, child)?.total
                        val desc = pluralStringResource(R.plurals.app_skipass_len, d, d.toString()) + " · " + who + (pr?.let { ", " + stringResource(R.string.app_skipass_price, it.toString()) } ?: "")
                        Column(Modifier.width(52.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(if (on) k.acc else k.line)
                            .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on; contentDescription = desc }
                            .clickable { haptics.tick(.3f); days = d; fromTrip = false }.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.toString(), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (on) k.onAcc else k.ink)
                            Text(pr?.toString() ?: "—", fontFamily = Gudi, fontSize = 11.sp, color = if (on) k.onAcc.copy(alpha = .9f) else k.ink2,
                                style = TextStyle(fontFeatureSettings = "tnum"))
                            if (d == 7 || d == 10) Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) k.onAcc else k.acc))
                        }
                    }
                }
                if (days >= 7) Text(stringResource(R.string.app_skipass_named), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
            }
            if (prices != null) {
                // 3. three short rows: where to buy, the deposit, the night
                Column {
                    InfoRow(RowIcon.Pin, stringResource(R.string.app_skipass_office), k, last = false) {
                        Box(Modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.clickable { haptics.tick(.3f); Qa.log("redesign skipass office"); onOffice() }.padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.app_skipass_office_where) + (if (isRtl()) " ‹" else " ›"), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = k.acc)
                        }
                    }
                    prices.deposit?.let { InfoRow(RowIcon.Card, stringResource(R.string.app_skipass_deposit), k, value = stringResource(R.string.app_skipass_deposit_value, gel(it)), last = false) }
                    val nightPrice = if (child) prices.nightChild else prices.nightAdult
                    val free = prices.freeUnder
                    nightPrice?.let { InfoRow(RowIcon.Moon, stringResource(R.string.app_skipass_night_row), k, value = stringResource(R.string.app_skipass_night_value, gel(it)), last = free == null) }
                    free?.let { InfoRow(RowIcon.Card, stringResource(R.string.app_skipass_child_free, it.toString()), k, last = true) }
                }
                // 4. one footnote
                val date = prices.checked.format(java.time.format.DateTimeFormatter.ofPattern("d.M.yyyy"))
                val foot = when {
                    !valid -> stringResource(R.string.app_skipass_check_site)
                    prices.vat -> stringResource(R.string.app_skipass_foot, prices.src, date)
                    else -> stringResource(R.string.app_skipass_checked, date, prices.src)
                }
                Text(foot, Modifier.fillMaxWidth(), fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2, textAlign = TextAlign.Center)
            }
        }
    }
}

private enum class RowIcon { Pin, Card, Moon }

@Composable
private fun isRtl() = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl

/** A row 44 high: an icon and the words at the start, the value (or the link) at the end, a divider under it. */
@Composable
private fun InfoRow(icon: RowIcon, text: String, k: SheetInk, value: String? = null, last: Boolean, end: (@Composable () -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(19.dp).clearAndSetSemantics { }) { drawRowIcon(icon, k.ink2) }
            Text(text, Modifier.weight(1f), fontFamily = Gudi, fontSize = 14.sp, color = k.ink)
            if (value != null) Text(value, fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2, textAlign = TextAlign.End)
            end?.invoke()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRowIcon(icon: RowIcon, c: Color) {
    val w = size.width; val s = Stroke(width = 1.8.dp.toPx())
    when (icon) {
        RowIcon.Pin -> {
            val p = Path().apply { moveTo(w * .5f, w * .95f); cubicTo(w * .1f, w * .55f, w * .15f, w * .05f, w * .5f, w * .05f); cubicTo(w * .85f, w * .05f, w * .9f, w * .55f, w * .5f, w * .95f); close() }
            drawPath(p, c, style = s); drawCircle(c, w * .12f, Offset(w * .5f, w * .4f))
        }
        RowIcon.Card -> {
            drawRoundRect(c, Offset(w * .05f, w * .2f), Size(w * .9f, w * .6f), CornerRadius(w * .1f), style = s)
            drawLine(c, Offset(w * .05f, w * .42f), Offset(w * .95f, w * .42f), s.width)
        }
        RowIcon.Moon -> {
            val p = Path().apply { moveTo(w * .7f, w * .1f); cubicTo(w * .2f, w * .15f, w * .1f, w * .8f, w * .6f, w * .9f); cubicTo(w * .85f, w * .93f, w * .95f, w * .8f, w * .95f, w * .8f); cubicTo(w * .55f, w * .7f, w * .5f, w * .3f, w * .7f, w * .1f); close() }
            drawPath(p, c, style = s)
        }
    }
}

/** The tiles go to two weeks: the rule's limit ([Prices.MAX_DAYS]). */
private const val MAX_TILES = Prices.MAX_DAYS

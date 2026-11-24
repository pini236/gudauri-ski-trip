package io.github.pini236.skiapp.redesign

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalUriHandler
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
import kotlin.math.roundToInt

/**
 * The completion round, part 2 (Q09, design/handoff/2026-11-21-redesign-around, section 7): the ski pass over the
 * mountain. Round 40's prices and rule (skipass/Prices: the cheapest of up to two tickets, up to 14 days, no
 * information past `until`, GEL only, nothing bought here): the price big at the top, the sheet with who, how many
 * days, the tiles of days, the rules and where to buy.
 */
@Composable
internal fun BoxScope.LiveSkipass(around: Around, night: Boolean, haptics: Haptics) {
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

    // the words on the mountain: ink by day, white at night (handoff section 14.3: 4.5:1 at the least)
    val hero = if (night) Color.White else k.ink
    // on the mountain: the price, what it is for, and against the day tickets
    Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 118.dp - 24.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(Triple(valid, q?.total, days to child), animationSpec = tween(150), label = "skipass") { (ok, total, _) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    !ok -> {
                        Text(stringResource(R.string.app_skipass_no_info), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = hero, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.app_skipass_check_site), fontFamily = Gudi, fontSize = 14.sp, color = hero.copy(alpha = .86f), textAlign = TextAlign.Center)
                    }
                    total == null -> Text(stringResource(R.string.app_skipass_too_long, days.toString()), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                        color = hero, textAlign = TextAlign.Center)
                    else -> {
                        val big = stringResource(R.string.app_skipass_price, total.toString())
                        Row(Modifier.clearAndSetSemantics { contentDescription = big }, verticalAlignment = Alignment.Bottom) {
                            Text(total.toString(), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 112.sp, lineHeight = 112.sp, color = hero,
                                style = TextStyle(fontFeatureSettings = "tnum"))
                            Text("₾", Modifier.padding(start = 4.dp, bottom = 18.dp), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 40.sp, color = hero)
                        }
                        Text(pluralStringResource(R.plurals.app_skipass_hero, days, who, days.toString()), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = hero)
                        val daily = prices?.daily(child)
                        if (daily != null && daily * days > total) {
                            val all = daily * days
                            Text(stringResource(R.string.app_skipass_vs_daily, days.toString(), gel(all)) + " · " +
                                stringResource(R.string.app_skipass_save, gel(all - total), (((all - total).toDouble() / all) * 100).roundToInt().toString()),
                                Modifier.padding(top = 4.dp), fontFamily = Gudi, fontSize = 14.sp, color = hero.copy(alpha = .9f), textAlign = TextAlign.Center)
                        }
                        if (q != null && q.parts.size == 2) {
                            val p1 = pluralStringResource(R.plurals.app_skipass_part, q.parts[0], q.parts[0].toString())
                            val p2 = pluralStringResource(R.plurals.app_skipass_part, q.parts[1], q.parts[1].toString())
                            Text(stringResource(R.string.app_skipass_no_pass, days.toString(), stringResource(R.string.app_skipass_and, p1, p2)),
                                Modifier.padding(top = 4.dp), fontFamily = Gudi, fontSize = 13.sp, color = hero.copy(alpha = .86f), textAlign = TextAlign.Center)
                            Text(stringResource(R.string.app_skipass_two_tickets), fontFamily = Gudi, fontSize = 12.5.sp, color = hero.copy(alpha = .8f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    // the sheet
    Box(Modifier.align(Alignment.BottomCenter)) {
        LiveSheet(k, opaque = true) {
            // 1. adult or child
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(k.line)) {
                for (kid in listOf(false, true)) {
                    val on = child == kid
                    val t = if (!kid) stringResource(R.string.app_skipass_adult) else stringResource(R.string.app_skipass_child, (prices?.childFrom ?: 6).toString(), (prices?.childTo ?: 12).toString())
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).padding(3.dp).clip(RoundedCornerShape(19.dp)).background(if (on) k.acc else Color.Transparent)
                        .semantics { role = Role.RadioButton; selected = on }.clickable { haptics.tick(.3f); child = kid }, contentAlignment = Alignment.Center) {
                        Text(t, fontFamily = Gudi, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, color = if (on) Color.White else k.ink2, maxLines = 1)
                    }
                }
            }
            // 2. the days, minus and plus
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthArrow("−", stringResource(R.string.app_skipass_days_less), days > 1, k) { haptics.tick(.3f); days -= 1; fromTrip = false }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$days " + pluralStringResource(R.plurals.app_skipass_days_n, days), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = k.ink,
                        style = TextStyle(fontFeatureSettings = "tnum"))
                    if (fromTrip && trip != null) {
                        val r = trip.skiDays()
                        val dates = r?.let { stringResource(R.string.app_trip_range, shortDate(it.start), shortDate(it.endInclusive)) }
                        Text(listOfNotNull(stringResource(R.string.app_skipass_from_trip), dates).joinToString(" · "), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2, maxLines = 1)
                    }
                }
                MonthArrow("+", stringResource(R.string.app_skipass_days_more), days < MAX_TILES, k) { haptics.tick(.3f); days += 1; fromTrip = false }
            }
            // 3. the tiles of days, each with its price (none past `until`)
            if (valid) {
                val scroll = rememberScrollState()
                LaunchedEffect(days) { if (days > 6) scroll.animateScrollTo(((days - 6) * 58 * 3).coerceAtMost(scroll.maxValue)) }
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 1..MAX_TILES) {
                        val on = d == days
                        val pr = prices!!.price(d, child)?.total
                        val desc = pluralStringResource(R.plurals.app_skipass_hero, d, who, d.toString()) + (pr?.let { ", " + stringResource(R.string.app_skipass_price, it.toString()) } ?: "")
                        Column(Modifier.width(52.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(if (on) k.acc else k.line)
                            .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on; contentDescription = desc }
                            .clickable { haptics.tick(.3f); days = d; fromTrip = false }.padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.toString(), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (on) Color.White else k.ink)
                            Text(pr?.toString() ?: "—", fontFamily = Gudi, fontSize = 11.5.sp, color = if (on) Color.White.copy(alpha = .9f) else k.ink2,
                                style = TextStyle(fontFeatureSettings = "tnum"))
                            if (d == 7 || d == 10) Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) Color.White else k.acc))
                        }
                    }
                }
                if (days >= 7) Text(stringResource(R.string.app_skipass_named), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
            }
            if (prices != null) {
                // 4. the card and the foreigner's price, 5. the night, with "under 6 free" among the rules
                val rules = listOfNotNull(
                    prices.deposit?.let { stringResource(R.string.app_skipass_card, gel(it)) },
                    stringResource(R.string.app_skipass_foreign),
                    prices.freeUnder?.let { stringResource(R.string.app_skipass_child_free, it.toString()) },
                )
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 2.dp, end = 8.dp).size(16.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) {
                        Text("i", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = k.ink2)
                    }
                    Text(rules.joinToString(" "), fontFamily = Gudi, fontSize = 12.5.sp, lineHeight = 18.sp, color = k.ink2)
                }
                (if (child) prices.nightChild else prices.nightAdult)?.let {
                    Text(stringResource(R.string.app_skipass_night, gel(it)), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
                }
                // 6. where to buy, and the resort's app at the end (its store page, as it is)
                val uri = LocalUriHandler.current
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_skipass_buy), Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = k.ink)
                    prices.buyAndroid?.let { link ->
                        val label = stringResource(R.string.app_skipass_app, "Google Play")
                        Box(Modifier.heightIn(min = 44.dp).semantics { role = Role.Button; contentDescription = label }
                            .clickable { Qa.log("redesign skipass store"); uri.openUri(link) }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text("SKIPASS ↗", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.acc)
                        }
                    }
                }
                // 7. checked when, the season, GEL with VAT
                val foot = listOfNotNull(stringResource(R.string.app_skipass_checked, shortDate(prices.checked), prices.src),
                    prices.season?.takeIf { prices.vat }?.let { stringResource(R.string.app_skipass_footer, it) })
                Text(foot.joinToString(" · "), fontFamily = Gudi, fontSize = 12.sp, color = k.ink2)
            }
        }
    }
}

/** The tiles go to two weeks: the rule's limit ([Prices.MAX_DAYS]). */
private const val MAX_TILES = Prices.MAX_DAYS

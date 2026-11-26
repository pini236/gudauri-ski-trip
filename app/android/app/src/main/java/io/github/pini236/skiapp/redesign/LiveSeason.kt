package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import io.github.pini236.skiapp.season.Season
import io.github.pini236.skiapp.season.shortDate
import io.github.pini236.skiapp.trip.Trip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The completion round, part 2 (Q10, design/handoff/2026-11-21-redesign-around, section 8): before the season, with no
 * fresh report on the lifts and the opening date (season.open in resorts.json) not behind us, the mountain opens with
 * the count of days, round 39's rules (Season.decide) for tomorrow, the day itself, a date gone by and a date moved.
 * The sheet under it: the planned date and its source, "your trip" (Q07, Q08) and the ski pass (Q09). A tap on the
 * number, or pulling the sheet down, gives the usual mountain; the count comes back the next time the app opens.
 */
internal object LiveSeasonRules {
    /** Whether the mountain opens on the count: a date ahead or today, or one gone by with no report; never with a fresh report. */
    fun shown(around: Around?): Boolean = around != null && (around.opening != null || around.lateOpen != null)
}

@Composable
internal fun BoxScope.LiveSeason(around: Around, night: Boolean, haptics: Haptics, onTrip: () -> Unit, onSkipass: () -> Unit, onClose: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    val o = around.opening
    val late = around.lateOpen
    val open = o?.open ?: late ?: return
    val date = shortDate(open)
    // the sheet: pulled down, the usual mountain
    Box(Modifier.align(Alignment.BottomCenter).pullSheet(onDown = { Qa.log("redesign season closed pull"); onClose() })) {
        LiveSheet(k, opaque = true) {
            // 1. the planned date, its weekday and the source
            val loc = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            val wd = remember(open, loc) { open.format(DateTimeFormatter.ofPattern("EEEE", loc)) }
            Column {
                Text(stringResource(R.string.app_season_planned, date), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink)
                Text("$wd · " + stringResource(R.string.app_season_source), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
            }
            // 2. your trip: the dates and the ski days, the days to the flight at the end; or "when do you fly?"
            val trip = around.trip
            SeasonRow(stringResource(R.string.trip_title), trip?.let { seasonTripLine(it) },
                if (trip == null) stringResource(R.string.trip_when_fly) else daysToFlight(trip, around.today), trip == null, k) { haptics.tick(.3f); Qa.log("redesign season trip"); onTrip() }
            // 3. the ski pass: the trip's ski days (or one), an adult, the price at the end
            val pr = around.prices?.takeIf { it.valid(around.today) }
            if (pr != null) {
                val n = trip?.skiDayCount()?.takeIf { it in 1..io.github.pini236.skiapp.skipass.Prices.MAX_DAYS } ?: 1
                val total = pr.price(n, false)?.total
                SeasonRow(stringResource(R.string.app_skipass_title),
                    pluralStringResource(R.plurals.app_skipass_hero, n, stringResource(R.string.app_skipass_adult), n.toString()),
                    total?.let { stringResource(R.string.app_skipass_price, it.toString()) }, false, k) { haptics.tick(.3f); Qa.log("redesign season skipass"); onSkipass() }
            }
        }
    }
}

@Composable
private fun SeasonRow(title: String, sub: String?, end: String?, endAcc: Boolean, k: SheetInk, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) { role = Role.Button }.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 2)
            if (sub != null) Text(sub, fontFamily = Gudi, fontSize = 13.sp, color = k.ink2, maxLines = 2)
        }
        if (end != null) Text(end, fontFamily = Gudi, fontWeight = if (endAcc) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp,
            color = if (endAcc) k.acc else k.ink, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun seasonTripLine(t: Trip): String? = skiLine(t)

@Composable
private fun daysToFlight(t: Trip, today: LocalDate): String? {
    val d = (t.out.date.toEpochDay() - today.toEpochDay()).toInt()
    return if (d > 0) pluralStringResource(R.plurals.common_days_to_flight, d, d.toString()) else null
}


/**
 * B8 (handoff 2026-11-26-redesign-hints, section 6, boards CT1 to CT5): before the season the mountain opens free, and the
 * count is a small framed tile on the start side: the days in a ring that fills from the announcement to the opening. A tap
 * opens it (the words), a tap on the open tile opens the season sheet. [expanded] is the owner's (it opens by itself once a day).
 */
@Composable
internal fun BoxScope.SeasonTile(around: Around, night: Boolean, haptics: Haptics, expanded: Boolean, onExpanded: (Boolean) -> Unit, onOpenSheet: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    val o = around.opening
    val late = around.lateOpen
    val open = o?.open ?: late ?: return
    val date = shortDate(open)
    val reduced = io.github.pini236.skiapp.ui.Motion.reduced(androidx.compose.ui.platform.LocalContext.current)
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val number = o != null && o.kind == Season.Kind.COUNTDOWN && o.days > 1
    val today = o != null && o.kind == Season.Kind.OPENING_DAY
    val tomorrow = o != null && o.kind == Season.Kind.COUNTDOWN && o.days == 1
    val title: String; val sub: String
    when {
        today -> { title = stringResource(R.string.app_season_today); sub = stringResource(R.string.app_season_today_sub) }
        tomorrow -> { title = stringResource(R.string.app_season_tomorrow); sub = stringResource(R.string.app_season_tomorrow_sub, date) }
        number -> { title = pluralStringResource(R.plurals.app_season_days_caption, o!!.days)
            sub = if (o.postponed) stringResource(R.string.app_season_moved, date) else stringResource(R.string.app_season_tomorrow_sub, date) }
        else -> { title = stringResource(R.string.app_season_late, date); sub = stringResource(R.string.app_season_late_sub) }
    }
    val a11y = if (number) pluralStringResource(R.plurals.app_season_days_a11y, o!!.days, o.days.toString(), date) else "$title. $sub"
    val progress = when { o == null -> 1f; today -> 1f; else -> o.progress }
    val full = o == null || today
    // closes by itself after 3.5 s (longer when the system asks for it)
    val am = androidx.compose.ui.platform.LocalContext.current.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val stay = if (android.os.Build.VERSION.SDK_INT >= 29) am.getRecommendedTimeoutMillis(3500, android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT) else 3500
    androidx.compose.runtime.LaunchedEffect(expanded) { if (expanded) { kotlinx.coroutines.delay(stay.toLong()); onExpanded(false) } }
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 90.dp)
        .then(if (reduced) Modifier else Modifier.animateContentSize(androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)))
        .shadow(8.dp, shape, clip = false).clip(shape).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, shape)
        .clearAndSetSemantics { contentDescription = a11y; role = Role.Button; onClick { Qa.log("redesign season tile sheet"); onOpenSheet(); true } }
        .clickable { haptics.tick(.3f); if (expanded) { Qa.log("redesign season tile sheet"); onOpenSheet() } else { Qa.log("redesign season tile open"); onExpanded(true) } }
        .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(48.dp)) {
                val w = 3.dp.toPx(); val r = (size.minDimension - w) / 2
                val tl = androidx.compose.ui.geometry.Offset(w / 2, w / 2); val sz = androidx.compose.ui.geometry.Size(2 * r, 2 * r)
                drawArc(k.line, 0f, 360f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                drawArc(if (o == null) k.ink2 else k.acc, -90f, 360f * progress, false, tl, sz,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                val c = center; val ic = k.ink
                if (o == null) { // a clock: the date went by with no report
                    drawLine(ic, c, androidx.compose.ui.geometry.Offset(c.x, c.y - 10.dp.toPx()), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(ic, c, androidx.compose.ui.geometry.Offset(c.x + 7.dp.toPx(), c.y), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                } else if (today) { // a gondola: the cable and a cabin under it
                    drawLine(ic, androidx.compose.ui.geometry.Offset(c.x - 12.dp.toPx(), c.y - 9.dp.toPx()), androidx.compose.ui.geometry.Offset(c.x + 12.dp.toPx(), c.y - 9.dp.toPx()), 1.8.dp.toPx())
                    drawLine(ic, androidx.compose.ui.geometry.Offset(c.x, c.y - 9.dp.toPx()), androidx.compose.ui.geometry.Offset(c.x, c.y - 4.dp.toPx()), 1.8.dp.toPx())
                    drawRoundRect(ic, androidx.compose.ui.geometry.Offset(c.x - 7.dp.toPx(), c.y - 4.dp.toPx()), androidx.compose.ui.geometry.Size(14.dp.toPx(), 11.dp.toPx()),
                        androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
                }
            }
            if (o != null && !today) Text((if (tomorrow) 1 else o.days).toString(), fontFamily = Gudi, fontWeight = FontWeight.SemiBold,
                fontSize = if (o.days >= 100) 17.sp else 20.sp, color = k.ink, style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
        }
        if (expanded) {
            Column(Modifier.widthIn(max = 190.dp)) {
                Text(title, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.ink, maxLines = 2)
                Text(sub, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 2)
            }
            Text(if (rtl) "\u2039" else "\u203A", Modifier.padding(end = 4.dp), fontFamily = Gudi, fontSize = 18.sp, color = k.ink2)
        }
    }
}

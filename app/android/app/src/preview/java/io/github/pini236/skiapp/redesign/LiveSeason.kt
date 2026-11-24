package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val hero = if (night) Color.White else k.ink
    // on the mountain, 150 from the top: the number and its caption, or the words of the day
    val (big, sub, moved) = when {
        o != null && o.kind == Season.Kind.OPENING_DAY -> Triple(stringResource(R.string.app_season_today), stringResource(R.string.app_season_today_sub), null)
        o != null && o.days == 1 -> Triple(stringResource(R.string.app_season_tomorrow), stringResource(R.string.app_season_tomorrow_sub, date), null)
        o != null -> Triple(o.days.toString(), pluralStringResource(R.plurals.app_season_days_caption, o.days),
            if (o.postponed) stringResource(R.string.app_season_moved, date) else null)
        else -> Triple(stringResource(R.string.app_season_late, date), stringResource(R.string.app_season_late_sub), null)
    }
    val number = o != null && o.kind == Season.Kind.COUNTDOWN && o.days > 1
    val a11y = if (number) pluralStringResource(R.plurals.app_season_days_a11y, o!!.days, o.days.toString(), date) else "$big. $sub"
    Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 150.dp - 24.dp).padding(horizontal = 24.dp)
        .clearAndSetSemantics { contentDescription = a11y; role = Role.Button }
        .clickable { haptics.tick(.3f); Qa.log("redesign season closed tap"); onClose() },
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (number) Text(big, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 112.sp, lineHeight = 112.sp, color = hero,
            style = TextStyle(fontFeatureSettings = "tnum"))
        else Text(big, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = if (o != null) 44.sp else 19.sp, lineHeight = if (o != null) 48.sp else 24.sp,
            color = hero, textAlign = TextAlign.Center)
        Text(sub, fontFamily = Gudi, fontWeight = if (number) FontWeight.Bold else FontWeight.Medium, fontSize = if (number) 19.sp else 15.sp,
            color = hero, textAlign = TextAlign.Center)
        if (moved != null) Text(moved, Modifier.padding(top = 4.dp), fontFamily = Gudi, fontSize = 14.sp, color = hero.copy(alpha = .9f), textAlign = TextAlign.Center)
    }
    // the sheet: pulled down, the usual mountain
    Box(Modifier.align(Alignment.BottomCenter).pointerInput(Unit) {
        var dy = 0f
        detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy > 60.dp.toPx()) { Qa.log("redesign season closed pull"); onClose() } }) { _, d -> dy += d }
    }) {
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

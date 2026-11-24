package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.ui.Plex
import io.github.pini236.skiapp.weather.DEFAULT_ALT
import io.github.pini236.skiapp.weather.Forecast
import io.github.pini236.skiapp.weather.LocalWeather
import io.github.pini236.skiapp.weather.WeatherNow
import io.github.pini236.skiapp.weather.WindArrow
import io.github.pini236.skiapp.weather.ago
import io.github.pini236.skiapp.weather.deg
import io.github.pini236.skiapp.weather.pinTexts
import io.github.pini236.skiapp.weather.pointName
import io.github.pini236.skiapp.weather.windWords
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** "The mountain now" (S05, S06): one sheet, two tabs, the lifts (Q01, Q02 as they are) and the weather. */
internal enum class NowTab { LIFTS, WEATHER }

/** The sheet's heading and the switch between the tabs (the same bar as "all day / one descent"). */
@Composable
internal fun NowHead(tab: NowTab, k: SheetInk, onTab: (NowTab) -> Unit) {
    Text0(stringResource(R.string.app_mtn_now_title), k.ink, 21f, FontWeight.Bold)
    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(k.line).padding(3.dp)) {
        for ((t, key) in listOf(NowTab.LIFTS to R.string.app_lifts_title, NowTab.WEATHER to R.string.weather_button)) {
            val on = t == tab
            Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(21.dp)).background(if (on) k.acc else Color.Transparent)
                .semantics { role = Role.Tab; selected = on }.clickable { if (!on) { onTab(t); Qa.log("redesign now ${t.name.lowercase()}") } },
                contentAlignment = Alignment.Center) {
                Text0(stringResource(key), if (on) Color.White else k.ink, 14f, FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Text0(s: String, c: Color, size: Float, w: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE) =
    androidx.compose.material3.Text(s, modifier, fontFamily = Plex, fontWeight = w, fontSize = size.sp, color = c, maxLines = lines)

/** The risk tag's colours (section 1): low green, medium amber, high red; a report's open or closed in the lift colours. */
private fun tagColors(t: NowRules.Tag, k: SheetInk, night: Boolean): Pair<Color, Color> = when {
    t.open == true -> Color(0x291B8A4C) to k.good
    t.open == false -> Color(0x29D8362C) to k.red
    t.level == "low" -> Color(0x291B8A4C) to k.good
    t.level == "medium" -> Color(0x2EC77700) to if (night) Color(0xFFF2B95A) else Color(0xFF8A5300)
    else -> Color(0x29D8362C) to k.red
}

/**
 * S05: the three points top down (Sadzele, upper Goodaura, the village), the closing tags, the ski days, the source line.
 * S06 (no answer, or older than two days): the rows with a dash and "no data", nothing else; an old one shows, in amber.
 */
@Composable
internal fun WeatherTab(lifts: List<Lift>, status: LiftStatus?, trip: io.github.pini236.skiapp.trip.Trip?, k: SheetInk, night: Boolean,
                        onLift: (Lift) -> Unit) {
    val w = LocalWeather.current
    val f = w.usable
    val n = remember { java.text.NumberFormat.getIntegerInstance() }
    val maxH = (LocalConfiguration.current.screenHeightDp - 296 - 8 - 130).coerceAtLeast(200).dp
    LaunchedEffect(w.state) { Qa.log("redesign weather ${w.measure}") }
    Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (id in Forecast.POINTS) {
            val p = w.forecast?.point(id)
            val alt = p?.elevation ?: DEFAULT_ALT.getValue(id)
            val v = f?.now(id, w.local)
            val name = pointName(id)
            val altS = stringResource(R.string.common_unit_m, n.format(alt))
            val wind = if (v != null) windWords(v.wind, v.gust) else stringResource(R.string.weather_none)
            val snowS = v?.snow24?.let { stringResource(R.string.weather_new_snow, n.format(it)) }
            val sentence = listOfNotNull(name, altS, if (v?.temp != null) deg(v.temp) else stringResource(R.string.weather_none), wind.takeIf { v != null }, snowS).joinToString(", ")
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).clearAndSetSemantics { contentDescription = sentence },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text0(deg(v?.temp), if (v?.temp == null) k.ink2 else k.ink, 27f, FontWeight.Light, Modifier.widthIn(min = 58.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text0("⁨$name⁩", k.ink, 15f, FontWeight.Bold, lines = 1)
                        Text0(altS, k.ink2, 13f, lines = 1)
                    }
                    if (v == null) Text0(stringResource(R.string.weather_none), k.ink2, 13f)
                    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (v.wind != null) v.dir?.let { WindArrow(it, 14.dp, k.ink2) }
                        Text0(wind, k.ink2, 13f, lines = 1)
                    }
                }
                if (v?.snow24 != null) Column(Modifier.widthIn(min = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text0(stringResource(R.string.app_wx_snow_n, n.format(v.snow24)), k.ink, 15f, FontWeight.Bold)
                    Text0(stringResource(R.string.app_wx_snow_cap), k.ink2, 11.5f)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
        }
        if (f == null) Text0(stringResource(R.string.weather_sub_none), k.ink2, 13f)
        else {
            // the closing in the wind (an estimate; MTA's report wins)
            val today = w.local.toLocalDate()
            val fresh = status?.fresh == true
            val tags = NowRules.tags(Forecast.RISK_LIFTS.filter { f.hasRisk(it) }.associateWith { f.risk(it, today) },
                if (fresh) Forecast.RISK_LIFTS.associateWith { status!!.isOpen(it) } else emptyMap())
            if (tags.isNotEmpty()) {
                Text0(stringResource(R.string.today_risk_title), k.ink, 15f, FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in tags) {
                        val (bg, fg) = tagColors(t, k, night)
                        val word = when (t.open) {
                            true -> stringResource(R.string.status_lift_open); false -> stringResource(R.string.status_lift_closed)
                            else -> stringResource(when (t.level) { "low" -> R.string.today_risk_low; "medium" -> R.string.today_risk_medium; else -> R.string.today_risk_high })
                        }
                        val lift = lifts.firstOrNull { it.name == t.lift }
                        Box(Modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.clickable(enabled = lift != null) { lift?.let(onLift) },
                            contentAlignment = Alignment.Center) {
                            Text0("⁦${t.lift}⁩ · $word", fg, 12.5f, FontWeight.Bold,
                                Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), lines = 1)
                        }
                    }
                }
                val why = NowRules.why(tags)
                Text0(when (why) {
                    NowRules.Why.REPORT -> stringResource(R.string.today_risk_why_all)
                    NowRules.Why.MIXED -> stringResource(R.string.today_risk_why_mixed, tags.first { it.open != null }.lift, tags.first { it.open == null }.lift)
                    else -> stringResource(R.string.today_risk_why)
                }, k.ink2, 12.5f)
            }
            // the ski days, once the first is in the forecast
            val ski = trip?.skiDays()?.let { r -> generateSequence(r.start) { it.plusDays(1) }.takeWhile { it <= r.endInclusive }.toList() }.orEmpty()
            val cards = NowRules.days(ski, f.point("goodaura")?.days.orEmpty(), today)
            if (cards != null) {
                Text0(stringResource(if (NowRules.during(ski, today)) R.string.today_title else R.string.today_ahead_title), k.ink, 15f, FontWeight.Bold)
                DayCards(cards, f, k)
            }
        }
        // the source line (the credit is a must)
        val uri = LocalUriHandler.current
        val src = when (w.state) {
            Forecast.State.FRESH -> stringResource(R.string.weather_sub_fresh, ago(f!!.updated, w.now))
            Forecast.State.STALE -> stringResource(R.string.weather_stale) + " · " + stringResource(R.string.weather_sub_stale, ago(f!!.updated, w.now))
            Forecast.State.NONE -> null
        }
        src?.let { Text0(it, if (w.state == Forecast.State.STALE) (if (night) Color(0xFFF2B95A) else Color(0xFF8A5300)) else k.ink2, 12.5f) }
        Box(Modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.clickable { runCatching { uri.openUri("https://open-meteo.com/") } }, contentAlignment = Alignment.CenterStart) {
            Text0(stringResource(R.string.weather_credit, "Open-Meteo.com"), k.ink2, 12f)
        }
    }
}

/** Up to four cards in a row, of equal width; more than four, the row scrolls sideways. */
@Composable
private fun DayCards(cards: List<NowRules.DayCard>, f: Forecast, k: SheetInk) {
    val lang = io.github.pini236.skiapp.i18n.Lang.current(LocalContext.current.resources).locale
    var later by remember { mutableStateOf<NowRules.DayCard?>(null) }
    val wide = LocalConfiguration.current.screenWidthDp.dp - 32.dp
    val cw = (wide - 18.dp) / 4
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in cards) {
            val d = f.day("goodaura", c.date)
            val head = c.date.dayOfWeek.getDisplayName(TextStyle.SHORT, lang) + " " + DateTimeFormatter.ofPattern("d.M").format(c.date)
            Column(Modifier.width(cw).heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, k.line, RoundedCornerShape(14.dp))
                .clickable(enabled = !c.inForecast) { later = c }.padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text0(head, k.ink2, 12f, lines = 1)
                Text0(if (c.inForecast) deg(d?.tmax) else "—", k.ink, 19f, FontWeight.Light)
                Text0(when {
                    !c.inForecast -> " "
                    (d?.snow ?: 0) > 0 -> stringResource(R.string.app_wx_snow_n, d!!.snow.toString())
                    else -> stringResource(R.string.app_wx_no_snow)
                }, k.ink2, 12f, lines = 1)
            }
        }
    }
    later?.opensOn?.let { o -> Text0(stringResource(R.string.today_ahead_later, DateTimeFormatter.ofPattern("d.M").format(o)), k.ink2, 12.5f) }
}

/** The temperature tags on the mountain over the sheet (S05): at each point's height, 18 above it. */
@Composable
internal fun BoxScope.TempTags(view: LiveView, w: WeatherNow, night: Boolean) {
    if (w.usable == null) return
    val k = remember(night) { SheetInk(night) }
    val pins = pinTexts(w)
    var at by remember { mutableStateOf<List<Pair<String, Offset>>>(emptyList()) }
    LaunchedEffect(pins.map { it.temp }) {
        while (true) { withFrameNanos { }; at = pins.mapNotNull { p -> view.toScreen(p.x, p.y)?.let { s -> p.temp to Offset(s[0], s[1]) } } }
    }
    val dens = LocalDensity.current
    for ((t, p) in at) {
        Box(Modifier.offset { IntOffset((p.x - with(dens) { 26.dp.toPx() }).toInt(), (p.y - with(dens) { 48.dp.toPx() }).toInt()) }
            .height(30.dp).shadow(6.dp, RoundedCornerShape(15.dp), clip = false).clip(RoundedCornerShape(15.dp)).background(k.surf.copy(alpha = 1f))
            .padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
            Text0(t, k.ink, 15f, FontWeight.Bold)
        }
    }
}

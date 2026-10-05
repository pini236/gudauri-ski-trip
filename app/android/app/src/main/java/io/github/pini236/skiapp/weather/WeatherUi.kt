package io.github.pini236.skiapp.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.status.summaryText
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Ski
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The forecast and the time, for every screen that shows the weather (MainActivity provides it). */
class WeatherNow(val forecast: Forecast?, val now: Instant) {
    val local: LocalDateTime get() = LocalDateTime.ofInstant(now, Forecast.GUDAURI)
    val state: Forecast.State get() = forecast?.state(now) ?: Forecast.State.NONE
    /** The forecast when it may be shown (fresh or old), else null: "no data", no guessing. */
    val usable: Forecast? get() = forecast?.takeIf { state != Forecast.State.NONE }
    val measure: String get() = state.name.lowercase()
}

val LocalWeather = compositionLocalOf { WeatherNow(null, Instant.EPOCH) }

private const val SOURCE = "Open-Meteo.com"
private const val SOURCE_URL = "https://open-meteo.com/"

@Composable private fun nf(): NumberFormat {
    val res = LocalContext.current.resources
    return remember(res) { NumberFormat.getIntegerInstance(Lang.current(res).locale) }
}

/** "−4°", with the true minus, kept left to right. */
fun deg(t: Int) = "⁦" + (if (t < 0) "−" else "") + kotlin.math.abs(t) + "°⁩"

@Composable fun pointName(id: String) = when (id) {
    "village" -> stringResource(R.string.weather_point_village)
    "goodaura" -> stringResource(R.string.weather_point_goodaura)
    else -> "Sadzele"
}

/** "Updated 20 min ago" words: the lift status' (status.ago_*). */
@Composable fun ago(updated: Instant, now: Instant): String {
    val m = ((now.toEpochMilli() - updated.toEpochMilli()) / 60_000L).toInt().coerceAtLeast(0)
    return when {
        m < 1 -> stringResource(R.string.status_ago_now)
        m < 60 -> stringResource(R.string.status_ago_minutes, m.toString())
        else -> stringResource(R.string.status_ago_hours, Math.round(m / 60.0).toString())
    }
}

/** What the arrow says to a screen reader: where the wind comes from. */
@Composable fun windFrom(dir: Int): String = stringResource(when (Math.floorMod(Math.round(dir / 45.0).toInt(), 8)) {
    0 -> R.string.weather_wind_from_n; 1 -> R.string.weather_wind_from_ne; 2 -> R.string.weather_wind_from_e; 3 -> R.string.weather_wind_from_se
    4 -> R.string.weather_wind_from_s; 5 -> R.string.weather_wind_from_sw; 6 -> R.string.weather_wind_from_w; else -> R.string.weather_wind_from_nw
})

/** The wind's arrow: it points where the wind blows to (from + 180), north up; never alone, the words go beside it. */
@Composable
fun WindArrow(dir: Int, size: Dp = 13.dp, color: Color = Ski.colors.ink) {
    val said = windFrom(dir)
    Canvas(Modifier.size(size).rotate(dir + 180f).semantics { contentDescription = said }) {
        val k = this.size.width / 16f
        val st = Stroke(2 * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(8 * k, 1.5f * k), Offset(8 * k, 13.5f * k), 2 * k, StrokeCap.Round)
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(3.5f * k, 6 * k); lineTo(8 * k, 1.5f * k); lineTo(12.5f * k, 6 * k) }, color, style = st)
    }
}

/** "Weather data by Open-Meteo.com", with the link (CC BY 4.0, m-6 section 6). */
@Composable
fun Credit(modifier: Modifier = Modifier) {
    val c = Ski.colors
    val uri = LocalUriHandler.current
    val line = stringResource(R.string.weather_credit, "\u0001")
    val (a, b) = line.split("\u0001").let { it[0] to it.getOrElse(1) { "" } }
    Text(buildAnnotatedString {
        append(a); withStyle(SpanStyle(color = c.glacier, textDecoration = TextDecoration.Underline)) { append(SOURCE) }; append(b)
    }, modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { runCatching { uri.openUri(SOURCE_URL) } }.padding(vertical = 12.dp),
        style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.muted)
}

/** One point (or a run's top or bottom): its name and height, the temperature big, the wind and the new snow (.r19-row). */
@Composable
fun WeatherRow(name: String, alt: Int, v: Forecast.Now?, onClick: (() -> Unit)? = null) {
    val c = Ski.colors
    val n = nf()
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).let { m -> if (onClick != null) m.clickable(role = Role.Button, onClick = onClick) else m }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("⁨$name⁩", style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = c.ink)
                Text(stringResource(R.string.common_unit_m, n.format(alt)), style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.muted)
            }
            Text(v?.let { deg(it.temp) } ?: "—", style = Ski.type.title.copy(fontSize = 32.sp, lineHeight = 32.sp), color = if (v == null) c.muted else c.ink)
            Column(horizontalAlignment = Alignment.End) {
                if (v == null) Text(stringResource(R.string.weather_none), style = Ski.type.small.copy(fontSize = 13.sp), color = c.ink)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WindArrow(v.dir)
                        Text(stringResource(R.string.weather_wind_gusts, n.format(v.wind), n.format(v.gust)), style = Ski.type.small.copy(fontSize = 13.sp), color = c.ink)
                    }
                    Text(stringResource(R.string.weather_new_snow, n.format(v.snow24)), style = Ski.type.small.copy(fontSize = 13.sp), color = c.ink)
                }
            }
        }
        HorizontalDivider(color = c.rule, thickness = 1.dp)
    }
}

/** The list under the map's weather button (and-map-weather): the three points, top down; a tap brings the map to one. */
@Composable
fun ColumnScope.WeatherList(w: WeatherNow, onPoint: (Forecast.Point) -> Unit) {
    val c = Ski.colors
    val f = w.usable
    Text(stringResource(R.string.weather_title), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
    val sub = when (w.state) {
        Forecast.State.FRESH -> stringResource(R.string.weather_sub_fresh, ago(f!!.updated, w.now))
        Forecast.State.STALE -> stringResource(R.string.weather_stale) + " · " + stringResource(R.string.weather_sub_stale, ago(f!!.updated, w.now))
        Forecast.State.NONE -> stringResource(R.string.weather_sub_none)
    }
    Text(sub, Modifier.padding(top = 2.dp, bottom = 8.dp), style = Ski.type.small.copy(fontSize = 13.sp), color = if (w.state == Forecast.State.STALE) c.ink else c.muted)
    for (id in Forecast.POINTS) {
        val p = w.forecast?.point(id)
        WeatherRow(pointName(id), p?.elevation ?: DEFAULT_ALT.getValue(id), f?.now(id, w.local), onClick = p?.let { { onPoint(it) } })
    }
    Text(stringResource(if (f == null) R.string.weather_note_none else R.string.weather_note), Modifier.padding(top = 8.dp),
        style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.muted)
    Credit()
}

/** The points' heights as the canvas draws them (the terrain model), when there is no answer to say them. */
val DEFAULT_ALT = mapOf("village" to 2170, "goodaura" to 2710, "sadzele" to 3240)

/** The run panel (and-run-cond): the forecast at the run's top and bottom, after the details. */
@Composable
fun ColumnScope.RunConditions(top: Int, bottom: Int, run: String) {
    val c = Ski.colors
    val w = LocalWeather.current
    val f = w.usable
    LaunchedEffect(run) { Telemetry.event("weather_view", mapOf("where" to "run", "state" to w.measure)) }
    Text(stringResource(R.string.run_cond_title), Modifier.padding(top = 14.dp, bottom = 2.dp),
        style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (28f / 44f)), color = c.ink)
    if (w.state == Forecast.State.STALE) Text(stringResource(R.string.weather_stale), style = Ski.type.small.copy(fontSize = 13.sp), color = c.ink)
    WeatherRow(stringResource(R.string.run_cond_top), top, f?.at(top.toFloat(), w.local))
    WeatherRow(stringResource(R.string.run_cond_bottom), bottom, f?.at(bottom.toFloat(), w.local))
    Text(if (f != null) stringResource(R.string.run_cond_note, ago(f.updated, w.now)) else stringResource(R.string.weather_sub_none), Modifier.padding(top = 8.dp),
        style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.muted)
    Credit()
}

/** The three steps of the risk meter, ink only (never green and red: those are run colours here). */
@Composable
private fun Meter(level: Int) {
    val c = Ski.colors
    Row(Modifier.width(54.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0..2) Box(Modifier.weight(1f).height(10.dp).background(if (i <= level) c.ink else Color.Transparent).border(1.5.dp, c.ink))
    }
}

@Composable private fun levelWord(l: String) = stringResource(when (l) { "low" -> R.string.today_risk_low; "medium" -> R.string.today_risk_medium; else -> R.string.today_risk_high })

/** The day line: "Ski day 2 of 4 · Tuesday, 12.1". */
@Composable
fun dayLine(date: LocalDate, n: Int, of: Int): String {
    val lang = Lang.current(LocalContext.current.resources)
    val d = if (lang.tag == "en") DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(date)
        else DateTimeFormatter.ofPattern("EEEE", lang.locale).format(date) + ", ${date.dayOfMonth}.${date.monthValue}"
    return stringResource(R.string.trip_ski_day_of, n.toString(), of.toString()) + " · " + d
}

/** The card's frame: the season board's (white, a rule on top, a soft shadow). */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val c = Ski.colors
    Column(Modifier.fillMaxWidth()
        .shadow(6.dp, RectangleShape, ambientColor = Color(0x1A13233A), spotColor = Color(0x1A13233A))
        .background(c.paper)
        .drawWithContent { drawContent(); drawRect(c.ink, Offset.Zero, Size(size.width, 6.dp.toPx())) }
        .padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 6.dp), content = content)
}

@Composable
private fun Head(title: String, w: WeatherNow) {
    val c = Ski.colors
    val f = w.usable
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.weight(1f), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
        Text(when (w.state) {
            Forecast.State.FRESH -> stringResource(R.string.today_updated, ago(f!!.updated, w.now))
            Forecast.State.STALE -> stringResource(R.string.weather_stale)
            Forecast.State.NONE -> stringResource(R.string.weather_none)
        }, style = Ski.type.bodyBold.copy(fontSize = 12.5.sp), color = if (w.state == Forecast.State.STALE) c.ink else c.muted)
    }
}

/**
 * "Today on the mountain" (and-home-today), during the trip: the day of the trip, the three heights now, the wind
 * closure estimate for Sadzele and Kudebi (a live MTA report on a lift replaces it), and the lift status inside.
 */
@Composable
fun TodayBoard(day: Int, of: Int, status: LiftStatus?, onStatus: () -> Unit) {
    val c = Ski.colors
    val w = LocalWeather.current
    val f = w.usable
    val n = nf()
    val today = w.local.toLocalDate()
    LaunchedEffect(Unit) { Telemetry.event("weather_view", mapOf("where" to "home", "state" to w.measure)) }
    Card {
        Head(stringResource(R.string.today_title), w)
        Text(dayLine(today, day, of), Modifier.padding(top = 2.dp, bottom = 8.dp), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
        HorizontalDivider(color = c.rule, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            for ((i, id) in listOf("village", "goodaura", "sadzele").withIndex()) {
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.rule))
                val v = f?.now(id, w.local)
                Column(Modifier.weight(1f).padding(start = if (i == 0) 0.dp else 8.dp, end = 8.dp, top = 8.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("⁨${pointName(id)}⁩", style = Ski.type.small.copy(fontSize = 11.5.sp), color = c.muted)
                    Text(stringResource(R.string.common_unit_m, n.format(f?.point(id)?.elevation ?: DEFAULT_ALT.getValue(id))), style = Ski.type.small.copy(fontSize = 11.5.sp), color = c.muted)
                    Text(v?.let { deg(it.temp) } ?: "—", Modifier.padding(top = 2.dp), style = Ski.type.title.copy(fontSize = 32.sp, lineHeight = 32.sp), color = if (v == null) c.muted else c.ink)
                    if (v == null) Text(stringResource(R.string.weather_none), style = Ski.type.small.copy(fontSize = 12.sp), color = c.ink)
                    else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            WindArrow(v.dir, 12.dp)
                            Text(stringResource(R.string.weather_wind_short, n.format(v.wind)), style = Ski.type.small.copy(fontSize = 12.sp), color = c.ink)
                        }
                        Text(stringResource(R.string.weather_snow_short, n.format(v.snow24)), style = Ski.type.small.copy(fontSize = 12.sp), color = c.ink)
                    }
                }
            }
        }
        HorizontalDivider(color = c.rule, thickness = 1.dp)
        // the wind closure estimate: the server's, per lift; a live report on a lift is said instead
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.today_risk_title), style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = c.muted)
            val live = Forecast.RISK_LIFTS.associateWith { status?.isOpen(it) }
            for (lift in Forecast.RISK_LIFTS) {
                val open = live[lift]
                val level = f?.risk(lift, today)
                Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("⁦$lift⁩", Modifier.weight(1f), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.ink)
                    when {
                        open != null -> { Text(stringResource(if (open) R.string.today_open_mta else R.string.today_closed_mta), style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = c.ink); Box(Modifier.width(54.dp)) }
                        level != null -> { Text(levelWord(level), style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = c.ink); Meter(Forecast.LEVELS.indexOf(level)) }
                        else -> { Text(stringResource(R.string.weather_none), style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = c.muted); Box(Modifier.width(54.dp)) }
                    }
                }
            }
            val reported = live.filterValues { it != null }.keys
            Text(when {
                reported.isEmpty() -> stringResource(R.string.today_risk_why)
                reported.size == live.size -> stringResource(R.string.today_risk_why_all)
                else -> stringResource(R.string.today_risk_why_mixed, "⁦${reported.first()}⁩", "⁦${(live.keys - reported).first()}⁩")
            }, style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.muted)
        }
        // the lift status inside the board (it replaces the season board)
        HorizontalDivider(Modifier.padding(top = 8.dp), color = c.rule, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onStatus).padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(10.dp).background(if (status?.fresh == true) c.green else c.dash))
            if (status?.fresh == true) Text(summaryText(status), Modifier.weight(1f), style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = c.ink)
            else Text(stringResource(R.string.today_lifts_none), Modifier.weight(1f), style = Ski.type.bodyBold.copy(fontSize = 13.5.sp), color = c.ink)
            Text(stringResource(R.string.status_heading_lift_status), style = Ski.type.bodyBold.copy(fontSize = 12.5.sp), color = c.glacier)
        }
        Credit()
    }
}

/**
 * Before the trip, once its first ski day is inside the forecast (m-6 section 5): one line per ski day, at the top of
 * the Goodaura gondola; a day still beyond the forecast says when it opens.
 */
@Composable
fun AheadBoard(skiDays: List<LocalDate>, daysToFlight: Int) {
    val c = Ski.colors
    val w = LocalWeather.current
    val f = w.usable
    val n = nf()
    val lang = Lang.current(LocalContext.current.resources)
    LaunchedEffect(Unit) { Telemetry.event("weather_view", mapOf("where" to "home", "state" to w.measure)) }
    Card {
        Head(stringResource(R.string.today_ahead_title), w)
        val count = if (daysToFlight == 0) stringResource(R.string.ticket_stub_departing) else pluralStringResource(R.plurals.ticket_stub_days, daysToFlight, daysToFlight)
        Text(pointName("goodaura") + ", " + stringResource(R.string.common_unit_m, n.format(f?.point("goodaura")?.elevation ?: DEFAULT_ALT.getValue("goodaura"))) + " · " + count,
            Modifier.padding(top = 2.dp, bottom = 8.dp), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
        val span = (w.forecast?.days ?: 15).coerceAtLeast(1)
        for ((i, d) in skiDays.withIndex()) {
            val v = f?.day("goodaura", d)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(72.dp)) {
                    Text(io.github.pini236.skiapp.meet.dayLabel(d, lang.tag), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.ink)
                    Text(stringResource(R.string.today_ahead_day, (i + 1).toString()), style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
                }
                if (v != null) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WindArrow(v.dir, 12.dp)
                        Text(stringResource(R.string.weather_wind_gusts, n.format(v.wind), n.format(v.gust)) + " · " +
                            (if (v.snow > 0) stringResource(R.string.weather_new_snow, n.format(v.snow)) else stringResource(R.string.weather_no_new_snow)),
                            style = Ski.type.small.copy(fontSize = 13.sp, lineHeight = 17.sp), color = c.ink)
                    }
                    Text(deg(v.tmax), style = Ski.type.title.copy(fontSize = 26.sp, lineHeight = 26.sp), color = c.ink)
                } else {
                    val opens = d.minusDays(span - 1L)
                    Text(if (f != null && opens.isAfter(w.local.toLocalDate())) stringResource(R.string.today_ahead_later, "${opens.dayOfMonth}.${opens.monthValue}")
                        else stringResource(R.string.weather_none), Modifier.weight(1f), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
                    Text("—", style = Ski.type.title.copy(fontSize = 26.sp, lineHeight = 26.sp), color = c.muted)
                }
            }
            if (i < skiDays.size - 1) HorizontalDivider(color = c.rule, thickness = 1.dp)
        }
        Text(stringResource(R.string.today_ahead_note), Modifier.padding(top = 8.dp), style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.muted)
        Credit()
    }
}


/** The three points where the answer has none (the lift ends in the site's data, as the server's). */
val DEFAULT_LL = mapOf("village" to (42.471394 to 44.49246), "goodaura" to (42.492379 to 44.494273), "sadzele" to (42.508985 to 44.503209))

/** One point of the map's weather layer, in words, at its place in the site's projection. */
class PinText(val id: String, val x: Float, val y: Float, val title: String, val temp: String, val wind: String?, val snow: String?, val dir: Int?)

@Composable
fun pinTexts(w: WeatherNow): List<PinText> {
    val n = nf()
    val f = w.usable
    val stale = if (w.state == Forecast.State.STALE) "\n" + stringResource(R.string.weather_stale) else ""
    return Forecast.POINTS.map { id ->
        val p = w.forecast?.point(id)
        val (la, lo) = DEFAULT_LL.getValue(id)
        val v = f?.now(id, w.local)
        val alt = stringResource(R.string.common_unit_m, n.format(p?.elevation ?: DEFAULT_ALT.getValue(id)))
        PinText(id, io.github.pini236.skiapp.data.Geo.x(lo), io.github.pini236.skiapp.data.Geo.y(la),
            "⁨${pointName(id)}⁩ · $alt$stale", v?.let { deg(it.temp) } ?: "—",
            v?.let { stringResource(R.string.weather_wind_short, n.format(it.wind)) } ?: stringResource(R.string.weather_none),
            v?.let { stringResource(R.string.weather_new_snow, n.format(it.snow24)) }, v?.dir)
    }
}

/** The map's "weather" button (r19-wchip): a cloud and the word, lit while the layer is on. */
@Composable
fun WeatherKey(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    val fg = if (on) c.paper else c.ink
    Row(modifier.heightIn(min = 44.dp).background(if (on) c.ink else c.paper).border(1.5.dp, c.ink)
        .clickable(role = Role.Switch, onClick = onClick).semantics { stateDescription = if (on) "✓" else "" }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.size(18.dp)) {
            val k = size.width / 24f
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(7 * k, 18 * k); lineTo(17 * k, 18 * k)
                cubicTo(19.2f * k, 18 * k, 21 * k, 16.2f * k, 21 * k, 14 * k); cubicTo(21 * k, 11.8f * k, 19.2f * k, 10 * k, 17 * k, 10 * k)
                cubicTo(16.4f * k, 7.2f * k, 13.9f * k, 5 * k, 11 * k, 5 * k); cubicTo(7.7f * k, 5 * k, 5.2f * k, 7.6f * k, 5.5f * k, 12 * k)
                cubicTo(3.6f * k, 12.3f * k, 3 * k, 13.6f * k, 3 * k, 15 * k); cubicTo(3 * k, 16.7f * k, 4.6f * k, 18 * k, 7 * k, 18 * k); close()
            }, fg, style = Stroke(2 * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text(stringResource(R.string.weather_button), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = fg)
    }
}

/** The points from above (Overview): the same cards as in 3D, over the meeting map's window. */
@Composable
fun MapPins2D(view: io.github.pini236.skiapp.meet.MeetView, pins: List<PinText>, modifier: Modifier) {
    val c = Ski.colors
    androidx.compose.foundation.layout.Box(modifier) {
        val d = androidx.compose.ui.platform.LocalDensity.current
        for (p in pins) {
            val px = view.sx(p.x); val py = view.sy(p.y)
            Canvas(Modifier.matchParentSize()) {
                drawCircle(c.paper, 6.dp.toPx(), Offset(px, py)); drawCircle(c.ink, 4.dp.toPx(), Offset(px, py))
                drawRect(c.ink, Offset(px - .75.dp.toPx(), py - 14.dp.toPx()), Size(1.5.dp.toPx(), 14.dp.toPx()))
            }
            androidx.compose.foundation.layout.Box(Modifier.layout { m, cons ->
                val pl = m.measure(cons.copy(minWidth = 0, minHeight = 0))
                layout(cons.maxWidth, cons.maxHeight) {
                    val gap = with(d) { 14.dp.roundToPx() }
                    val x = (px - pl.width / 2f).toInt().coerceIn(8, (cons.maxWidth - pl.width - 8).coerceAtLeast(8))
                    val y = (py - gap - pl.height).toInt().let { if (it < 8) (py + gap).toInt() else it }
                    pl.place(x, y)
                }
            }) {
                Column(Modifier.shadow(4.dp, RectangleShape).background(c.paper).border(1.5.dp, c.ink).padding(start = 9.dp, end = 9.dp, top = 6.dp, bottom = 7.dp)) {
                    Text(p.title, style = Ski.type.bodyBold.copy(fontSize = 11.5.sp, lineHeight = 14.sp), color = c.muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.temp, style = Ski.type.title.copy(fontSize = 30.sp, lineHeight = 30.sp), color = if (p.dir == null) c.muted else c.ink)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                p.dir?.let { WindArrow(it, 12.dp) }
                                p.wind?.let { Text(it, style = Ski.type.small.copy(fontSize = 12.sp, lineHeight = 15.sp), color = c.ink) }
                            }
                            p.snow?.let { Text(it, style = Ski.type.small.copy(fontSize = 12.sp, lineHeight = 15.sp), color = c.ink) }
                        }
                    }
                }
            }
        }
    }
}

package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.season.shortDate
import io.github.pini236.skiapp.weather.deg
import kotlin.math.cos
import kotlin.math.sin

private val Amber = Color(0xFFF0A020)

/**
 * P.25, the top pill's content (handoff 2026-11-27-redesign-top-pill, TP1 to TP7): the lifts part, a ring with the gondola
 * and "11 of 14", and the weather part, the condition, the temperature and one line. The states come from [TopPillModel].
 * Before the season the lifts part is the wait ring (it replaces the countdown tile). Not tested on a phone.
 */
@Composable
internal fun TopPillContent(lifts: TopPillModel.Lifts, weather: TopPillModel.Weather?, offline: Boolean, k: SheetInk, reduced: Boolean,
                            onLifts: () -> Unit, onWeather: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LiftsPart(lifts, offline, k, Modifier.weight(1f), onLifts)
        if (weather != null) {
            Box(Modifier.padding(vertical = 12.dp).width(1.dp).fillMaxHeight().background(k.line))
            WeatherPart(weather, k, reduced, onWeather)
        }
    }
}

@Composable
private fun LiftsPart(l: TopPillModel.Lifts, offline: Boolean, k: SheetInk, modifier: Modifier, onClick: () -> Unit) {
    val count = l.kind == TopPillModel.LiftsKind.COUNT
    val date = l.date?.let { shortDate(it) } ?: ""
    val l1: androidx.compose.ui.text.AnnotatedString
    val l2: String
    var l2Color = k.ink2
    when (l.kind) {
        TopPillModel.LiftsKind.LIVE, TopPillModel.LiftsKind.ALL_OPEN -> {
            l1 = openOf(l.open, l.total, k)
            if (l.kind == TopPillModel.LiftsKind.ALL_OPEN) l2 = stringResource(R.string.app_tp_all_open)
            else if (l.windStopped > 0) { l2 = pluralStringResource(R.plurals.app_tp_wind, l.windStopped, l.windStopped.toString()); l2Color = Amber }
            else l2 = stringResource(R.string.app_tp_open_label)
        }
        TopPillModel.LiftsKind.CLOSED -> {
            l1 = plain(stringResource(R.string.app_tp_closed), k)
            l2 = when {
                l.tomorrowAt != null -> stringResource(R.string.app_tp_opens_tomorrow, l.tomorrowAt)
                l.windStopped > 0 -> pluralStringResource(R.plurals.app_tp_wind, l.windStopped, l.windStopped.toString())
                else -> stringResource(R.string.app_tp_lifts)
            }
        }
        TopPillModel.LiftsKind.NO_DATA -> { l1 = plain(stringResource(R.string.app_tp_lifts), k); l2 = stringResource(if (offline) R.string.app_now_offline else R.string.app_tp_no_data) }
        TopPillModel.LiftsKind.COUNT -> {
            l1 = plain(stringResource(if (l.days == 1) R.string.app_season_tomorrow else R.string.app_tp_days_to_open), k)
            l2 = if (l.postponed) stringResource(R.string.app_season_moved, date) else stringResource(R.string.app_tp_lifts_up_on, date)
        }
        TopPillModel.LiftsKind.TODAY -> { l1 = plain(stringResource(R.string.app_season_today), k); l2 = stringResource(R.string.app_season_today_sub) }
        TopPillModel.LiftsKind.LATE -> { l1 = plain(stringResource(R.string.app_season_late, date), k); l2 = stringResource(R.string.app_season_late_sub) }
    }
    val a11y = when (l.kind) {
        TopPillModel.LiftsKind.LIVE, TopPillModel.LiftsKind.ALL_OPEN -> stringResource(R.string.app_tp_sr_lifts, l.open.toString(), l.total.toString())
        else -> "${l1.text}. $l2"
    }
    Row(modifier.fillMaxHeight().clearAndSetSemantics { contentDescription = a11y; role = Role.Button }.clickable(onClick = onClick).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(38.dp)) {
                val w = 3.2.dp.toPx(); val r = (size.minDimension - w) / 2
                val tl = Offset(w / 2, w / 2); val sz = Size(2 * r, 2 * r)
                drawArc(k.line, 0f, 360f, false, tl, sz, style = Stroke(w))
                when (l.kind) {
                    TopPillModel.LiftsKind.LIVE, TopPillModel.LiftsKind.ALL_OPEN -> if (l.total > 0) {
                        val g = l.open.toFloat() / l.total
                        val a = (l.windStopped.toFloat() / l.total).coerceAtMost(1f - g)
                        if (g > 0f) drawArc(k.good, -90f, 360f * g, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                        if (a > 0f) drawArc(Amber, -90f + 360f * g, 360f * a, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    TopPillModel.LiftsKind.COUNT, TopPillModel.LiftsKind.TODAY, TopPillModel.LiftsKind.LATE ->
                        drawArc(k.acc, -90f, 360f * l.progress, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                    else -> {}
                }
                when (l.kind) {
                    TopPillModel.LiftsKind.COUNT -> {}
                    TopPillModel.LiftsKind.LATE -> clock(k.ink, center)
                    else -> gondola(k.ink, center)
                }
            }
            if (count) Text(l.days.toString(), fontFamily = Gudi, fontWeight = FontWeight.SemiBold, fontSize = if (l.days >= 100) 14.sp else 17.sp,
                color = k.ink, style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(l1, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l2, fontFamily = Gudi, fontSize = 12.sp, color = l2Color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun plain(s: String, k: SheetInk) = buildAnnotatedString { append(s) }

/** "11 of 14": the open count large, the rest ("of 14") smaller and quieter. */
@Composable
private fun openOf(open: Int, total: Int, k: SheetInk): androidx.compose.ui.text.AnnotatedString {
    val full = stringResource(R.string.app_tp_open_of, open.toString(), total.toString())
    val i = full.indexOf(open.toString()).let { if (it < 0) full.length else it + open.toString().length }
    return buildAnnotatedString {
        append(full.substring(0, i))
        withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = k.ink2)) { append(full.substring(i)) }
    }
}

@Composable
private fun WeatherPart(w: TopPillModel.Weather, k: SheetInk, reduced: Boolean, onClick: () -> Unit) {
    val word = when (w.line) {
        TopPillModel.Line.FRESH_SNOW -> stringResource(R.string.app_tp_fresh_snow, w.n.toString())
        TopPillModel.Line.GUSTS -> stringResource(R.string.app_tp_gusts, w.n.toString())
        TopPillModel.Line.SNOW_COMING -> stringResource(R.string.app_tp_snow_coming, w.n.toString())
        TopPillModel.Line.COND -> stringResource(when (w.cond) {
            TopPillModel.Cond.SUN -> R.string.app_tp_c_sun
            TopPillModel.Cond.CLEAR, TopPillModel.Cond.MOON -> R.string.app_tp_c_clear
            TopPillModel.Cond.CLOUD -> R.string.app_tp_c_cloud
            TopPillModel.Cond.SNOW -> R.string.app_tp_c_snow
            TopPillModel.Cond.WIND -> R.string.app_tp_c_wind
            TopPillModel.Cond.FOG -> R.string.app_tp_c_fog
            TopPillModel.Cond.RAIN -> R.string.app_tp_c_rain
        })
    }
    val t = deg(w.temp)
    val a11y = stringResource(R.string.app_tp_sr_weather, t)
    // a soft pulse around the flake after fresh snow, off when the system asks for less motion
    val pulse = if (w.line == TopPillModel.Line.FRESH_SNOW && !reduced) rememberInfiniteTransition(label = "tp-pulse").animateFloat(0f, 1f,
        infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "tp-pulse-v").value else 0f
    Row(Modifier.fillMaxHeight().clearAndSetSemantics { contentDescription = a11y; role = Role.Button }.clickable(onClick = onClick).padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(k.ink.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp)) {
                if (pulse > 0f) drawCircle(k.acc.copy(alpha = .45f * (1f - pulse)), radius = size.minDimension / 2 * (.8f + .35f * pulse), style = Stroke(1.5.dp.toPx()))
                condIcon(w.cond, k.ink, center)
            }
        }
        Column {
            Text(t, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = k.ink, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
            Text(word, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun DrawScope.gondola(c: Color, ctr: Offset) {
    val s = 1.8.dp.toPx()
    drawLine(c, Offset(ctr.x - 8.dp.toPx(), ctr.y - 6.dp.toPx()), Offset(ctr.x + 8.dp.toPx(), ctr.y - 6.dp.toPx()), s, StrokeCap.Round)
    drawLine(c, Offset(ctr.x, ctr.y - 6.dp.toPx()), Offset(ctr.x, ctr.y - 3.dp.toPx()), s)
    drawRoundRect(c, Offset(ctr.x - 5.dp.toPx(), ctr.y - 3.dp.toPx()), Size(10.dp.toPx(), 8.dp.toPx()), CornerRadius(2.5.dp.toPx()), style = Stroke(s))
}

private fun DrawScope.clock(c: Color, ctr: Offset) {
    drawLine(c, ctr, Offset(ctr.x, ctr.y - 7.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
    drawLine(c, ctr, Offset(ctr.x + 5.dp.toPx(), ctr.y), 1.8.dp.toPx(), StrokeCap.Round)
}

/** The condition's 18 dp picture, drawn from lines so it takes the pill's ink at night too. */
private fun DrawScope.condIcon(cond: TopPillModel.Cond, c: Color, ctr: Offset) {
    val s = 1.7.dp.toPx(); val u = 1.dp.toPx()
    fun ray(a: Double, r0: Float, r1: Float, cc: Offset = ctr) =
        drawLine(c, Offset(cc.x + (r0 * cos(a)).toFloat(), cc.y + (r0 * sin(a)).toFloat()), Offset(cc.x + (r1 * cos(a)).toFloat(), cc.y + (r1 * sin(a)).toFloat()), s, StrokeCap.Round)
    when (cond) {
        TopPillModel.Cond.SUN -> { drawCircle(c, 3.6f * u, ctr, style = Stroke(s)); for (i in 0 until 8) ray(i * Math.PI / 4, 6f * u, 8.2f * u) }
        TopPillModel.Cond.CLEAR -> { drawCircle(c, 3.6f * u, ctr, style = Stroke(s)); for (i in 0 until 4) ray(i * Math.PI / 2, 6f * u, 8.2f * u) }
        TopPillModel.Cond.MOON -> { drawArc(c, 60f, 220f, false, Offset(ctr.x - 6f * u, ctr.y - 6f * u), Size(12f * u, 12f * u), style = Stroke(s, cap = StrokeCap.Round)) }
        TopPillModel.Cond.SNOW -> for (i in 0 until 3) ray(i * Math.PI / 3, -7.5f * u, 7.5f * u)
        TopPillModel.Cond.WIND -> { for ((dy, len) in listOf(-4f to 8f, 0f to 10f, 4f to 6f))
            drawLine(c, Offset(ctr.x - len * u, ctr.y + dy * u), Offset(ctr.x + len * u, ctr.y + dy * u), s, StrokeCap.Round) }
        TopPillModel.Cond.CLOUD -> { drawRoundRect(c, Offset(ctr.x - 8f * u, ctr.y - 2f * u), Size(16f * u, 8f * u), CornerRadius(4f * u), style = Stroke(s))
            drawArc(c, 180f, 180f, false, Offset(ctr.x - 4f * u, ctr.y - 7f * u), Size(8f * u, 10f * u), style = Stroke(s)) }
        TopPillModel.Cond.FOG -> for (dy in listOf(-4f, 0f, 4f)) drawLine(c, Offset(ctr.x - 8f * u, ctr.y + dy * u), Offset(ctr.x + 8f * u, ctr.y + dy * u), s, StrokeCap.Round)
        TopPillModel.Cond.RAIN -> { drawRoundRect(c, Offset(ctr.x - 8f * u, ctr.y - 6f * u), Size(16f * u, 7f * u), CornerRadius(3.5f * u), style = Stroke(s))
            for (dx in listOf(-4f, 0f, 4f)) drawLine(c, Offset(ctr.x + dx * u, ctr.y + 3f * u), Offset(ctr.x + (dx - 1.5f) * u, ctr.y + 7f * u), s, StrokeCap.Round) }
    }
}

package io.github.pini236.skiapp.season

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Ski
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the home board shows before the season opens (round 39, "the lift goes up"): the state, the date, and how far from the announcement to the date we are. */
class OpeningView(val kind: Season.Kind, val days: Int, val open: LocalDate, val progress: Float, val postponed: Boolean) {
    companion object {
        /** From the state and the season; null when there is nothing to count (a fresh report, no date, or the date has passed). */
        fun of(season: Season?, s: Season.State, today: LocalDate): OpeningView? {
            if (season == null || (s.kind != Season.Kind.COUNTDOWN && s.kind != Season.Kind.OPENING_DAY)) return null
            val from = season.announced
            val p = if (s.kind == Season.Kind.OPENING_DAY) 1f else if (from == null || !from.isBefore(season.open)) 0f else
                ((today.toEpochDay() - from.toEpochDay()).toFloat() / (season.open.toEpochDay() - from.toEpochDay())).coerceIn(0.02f, 0.98f)
            return OpeningView(s.kind, s.days ?: 0, season.open, p, s.postponed)
        }
    }
}

/** The ski-pass row on home (round 40): [days] and [total] when there is a trip with ski days and a price for them, else just the day ticket. */
class SkipassEntry(val days: Int?, val total: Int?, val daily: Int)

/** "20 Dec", "20 дек.", "20 დეკ.": the date as the phone's language writes it. */
@Composable
fun shortDate(d: LocalDate): String {
    val loc = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return remember(d, loc) { d.format(DateTimeFormatter.ofPattern("d MMM", loc)) }
}

/**
 * The board before the opening: how many days, and a lift line with a cabin that has gone part of the way up from the day
 * MTA announced the date to the date itself. On the day, "opening day, waiting for the lifts' report". It is planned, and
 * it can move (so it says whose date it is); with a fresh lift report this board is the lift board instead.
 */
@Composable
fun OpeningBoard(v: OpeningView, skip: SkipassEntry?, onSkipass: () -> Unit) {
    val c = Ski.colors
    val date = shortDate(v.open)
    val today = v.kind == Season.Kind.OPENING_DAY
    val tomorrow = v.kind == Season.Kind.COUNTDOWN && v.days == 1
    val head = when { today -> stringResource(R.string.app_season_today); tomorrow -> stringResource(R.string.app_season_tomorrow)
        v.postponed -> stringResource(R.string.app_season_moved, date); else -> stringResource(R.string.status_mountain_asleep) }
    val side = when { today -> stringResource(R.string.app_season_today_sub); tomorrow -> stringResource(R.string.app_season_tomorrow_sub, date)
        v.postponed -> stringResource(R.string.app_season_moved_sub); else -> stringResource(R.string.app_season_planned, date) }
    val label = if (today) head + ". " + side else pluralStringResource(R.plurals.app_season_days_a11y, v.days, v.days.toString(), date)
    Column(
        Modifier.fillMaxWidth().shadow(6.dp, RectangleShape, ambientColor = Color(0x1A13233A), spotColor = Color(0x1A13233A)).background(c.paper)
            .drawBehind { drawRect(c.dash, Offset.Zero, Size(size.width, 6.dp.toPx())) }
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(head, Modifier.weight(1f), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = c.ink)
            Text(side, style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
        }
        if (!today) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(v.days.toString(), style = Ski.type.title.copy(fontSize = 84.sp, lineHeight = 78.sp, textDirection = TextDirection.Ltr), color = c.ink)
            Text(pluralStringResource(R.plurals.app_season_days_caption, v.days), Modifier.padding(bottom = 8.dp), style = Ski.type.small.copy(fontSize = 15.sp), color = c.muted)
        }
        LiftLine(v.progress)
        Text(stringResource(R.string.app_season_source), style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
        if (skip != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
            SkipassRow(skip, onSkipass)
        }
    }
}

/** A ridge, a lift line up it, and a cabin [t] of the way (0 to 1). Mirrored in right-to-left languages, so the climb always goes toward the peak the way the text reads. */
@Composable
private fun LiftLine(t: Float) {
    val c = Ski.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val still = Motion.reduced(LocalContext.current)
    val sway = if (still) 0f else rememberInfiniteTransition(label = "cabin").animateFloat(-3f, 3f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "sway").value
    val ridge = if (c.dark) Color(0xFF33507A) else Color(0xFF9FB4CC)
    val snow = if (c.dark) Color(0xFFDCE6F5) else Color.White
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width; val h = size.height
        // the drawing is made in a 340 x 150 box
        val sx = w / 340f; val sy = h / 150f
        fun x(v: Float) = (if (rtl) 340f - v else v) * sx
        fun y(v: Float) = v * sy
        val m = Path().apply {
            moveTo(x(0f), y(150f)); lineTo(x(0f), y(118f)); lineTo(x(60f), y(96f)); lineTo(x(110f), y(108f)); lineTo(x(170f), y(56f))
            lineTo(x(215f), y(78f)); lineTo(x(270f), y(22f)); lineTo(x(340f), y(70f)); lineTo(x(340f), y(150f)); close()
        }
        drawPath(m, ridge)
        val cap = Path().apply { moveTo(x(270f), y(22f)); lineTo(x(252f), y(40f)); lineTo(x(270f), y(36f)); lineTo(x(284f), y(42f)); close() }
        drawPath(cap, snow)
        val x0 = x(24f); val y0 = y(128f); val x1 = x(294f); val y1 = y(24f)
        drawLine(c.ink, Offset(x0, y0), Offset(x1, y1), 1.5.dp.toPx())
        val cx = x0 + (x1 - x0) * t; val cy = y0 + (y1 - y0) * t
        drawLine(c.accent, Offset(x0, y0), Offset(cx, cy), 3.5.dp.toPx(), StrokeCap.Round)
        drawCircle(c.ink, 5.dp.toPx(), Offset(x0, y0)); drawCircle(c.ink, 5.dp.toPx(), Offset(x1, y1))
        // the cabin hangs from the line and sways a little
        rotate(sway, Offset(cx, cy)) {
            drawLine(c.ink, Offset(cx, cy), Offset(cx, cy + 12.dp.toPx()), 1.5.dp.toPx())
            val bw = 20.dp.toPx(); val bh = 15.dp.toPx()
            drawRect(Color(0xFFFFC62B), Offset(cx - bw / 2, cy + 12.dp.toPx()), Size(bw, bh))
            drawRect(c.ink, Offset(cx - bw / 2, cy + 12.dp.toPx()), Size(bw, bh), style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** The ski-pass row: what the trip's ski days cost on one pass, or the day ticket's price; a tap opens the ski-pass screen. */
@Composable
fun SkipassRow(e: SkipassEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    val ctx = LocalContext.current
    val gel = { n: Int -> ctx.getString(R.string.app_skipass_price, n.toString()) }
    val daily = gel(e.daily)
    val left = if (e.days != null && e.total != null) pluralStringResource(R.plurals.app_skipass_pass_days, e.days, e.days) else stringResource(R.string.app_skipass_title)
    val sub = stringResource(R.string.app_skipass_daily, daily)
    val right = if (e.days != null && e.total != null) gel(e.total) else stringResource(R.string.app_skipass_per_day, daily)
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(left, style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = c.ink)
            if (e.days != null && e.total != null) Text(sub, style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
        }
        Text(right, style = Ski.type.bodyBold.copy(fontSize = 20.sp, textDirection = TextDirection.Ltr), color = c.ink)
        Icon(Icons.forward, null, Modifier.size(18.dp), tint = c.glacier)
    }
}

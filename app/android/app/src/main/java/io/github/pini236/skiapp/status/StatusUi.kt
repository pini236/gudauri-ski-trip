package io.github.pini236.skiapp.status

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.SignShape
import io.github.pini236.skiapp.home.drawSnow
import io.github.pini236.skiapp.home.snowCap
import io.github.pini236.skiapp.meet.iso
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.delay

/**
 * The lift status screens of the site (design round 3, S1 to S3; LSTAT in site/js/app.js): the bar over the map, the
 * snowy signs while there is no report, and the departures board when there is one.
 */

/** The system's "remove animations" (animator duration scale 0): the board's rows and the live dot stay still. */
@Composable
fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { io.github.pini236.skiapp.ui.Motion.reduced(ctx) }
}

/** "6 min ago", "now", "2 hr ago" (status.ago_*). */
@Composable
fun agoText(s: LiftStatus): String {
    val m = s.minutesAgo
    return when {
        m < 1 -> stringResource(R.string.status_ago_now)
        m < 60 -> stringResource(R.string.status_ago_minutes, m.toString())
        else -> stringResource(R.string.status_ago_hours, Math.round(m / 60.0).toString())
    }
}

/** "11 of 12 lifts open · updated 6 min ago", the two numbers in the sign voice. */
@Composable
fun summaryText(s: LiftStatus): AnnotatedString {
    val raw = stringResource(R.string.status_bar_summary, "\u0001", "\u0002", agoText(s))
    val big = SpanStyle(fontFamily = Ski.type.display, fontWeight = FontWeight.Bold, fontSize = (20 * Ski.type.displayScale).sp)
    return buildAnnotatedString {
        var rest = raw
        while (rest.isNotEmpty()) {
            val i = rest.indexOfFirst { it == '\u0001' || it == '\u0002' }
            if (i < 0) { append(rest); break }
            append(rest.substring(0, i))
            withStyle(big) { append((if (rest[i] == '\u0001') s.open else s.names.size).toString()) }
            rest = rest.substring(i + 1)
        }
    }
}

/** S1's bar over the map (#mstat): a grey dot, or a green one that pulses while the report is fresh. A tap opens the board. */
@Composable
fun StatusBar(s: LiftStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    val still = reducedMotion()
    // the ring runs only while the report is live (and read only when drawing, so it does not recompose the screen)
    val pulse = if (s.fresh && !still) rememberInfiniteTransition(label = "live").animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "ring") else null
    val green = c.green
    Row(
        modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick)
            .background(c.paper.copy(alpha = .94f)).border(1.dp, c.rule)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).drawBehind {
            pulse?.value?.let { k -> drawCircle(green.copy(alpha = 1f - k), radius = size.minDimension / 2 + 8.dp.toPx() * k) }
            drawCircle(if (s.fresh) green else c.dash)
        })
        val text = if (s.fresh) summaryText(s) else AnnotatedString(stringResource(if (s.report == null) R.string.status_bar_no_data_yet else R.string.status_bar_no_recent))
        Text(text, style = Ski.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The board's content (the site's lstat block): the snowy signs without a fresh report (S3), the board with one (S2). */
@Composable
fun ColumnScope.StatusBoard(s: LiftStatus, changes: List<Pair<String, Boolean>>, forMe: Boolean, onForMe: (Boolean) -> Unit, inSeason: Boolean) {
    val c = Ski.colors
    if (!s.fresh) {
        Text(stringResource(if (inSeason) R.string.status_no_recent_data else R.string.status_mountain_asleep), Modifier.semantics { heading() },
            style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
        Text(stringResource(if (inSeason) R.string.status_lead_in_season else R.string.status_lead_off_season), Modifier.padding(top = 4.dp),
            style = Ski.type.body, color = c.muted)
        SnowySigns(s.names.take(6))
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.status_no_guessing_bold)) }
            append(" "); append(stringResource(R.string.status_no_guessing_text))
        }, style = Ski.type.small, color = c.muted)
        return
    }
    Text(stringResource(R.string.status_heading_lift_status), Modifier.semantics { heading() },
        style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
    if (changes.isNotEmpty()) {
        Column(Modifier.padding(top = 10.dp).fillMaxWidth().background(Color(0xFFF4B942)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            for ((n, open) in changes) Text(stringResource(if (open) R.string.status_change_opened else R.string.status_change_closed, iso(n)),
                style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = Color(0xFF13233A))
        }
    }
    DepartureBoard(s, Modifier.padding(top = 10.dp))
    // "only what's open for me": the site's chip (.fchip), square, 44 high
    val label = stringResource(R.string.status_only_open_for_me)
    val state = stringResource(if (forMe) R.string.app_on else R.string.app_off)
    Box(
        Modifier.padding(top = 10.dp).heightIn(min = 44.dp)
            .background(if (forMe) c.ink else c.paper).border(1.5.dp, c.ink)
            .clickable(role = Role.Switch) { onForMe(!forMe) }.semantics { stateDescription = state }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = if (forMe) Color.White else c.ink) }
    Text(stringResource(R.string.status_board_hint, s.open.toString(), s.names.size.toString()), Modifier.padding(top = 10.dp), style = Ski.type.small, color = c.muted)
}

/** S3: the lifts' signs on a post, under soft heaps of snow, with a question mark instead of a state. */
@Composable
private fun SnowySigns(names: List<String>) {
    val ink = Color(0xFF13233A)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 30.dp)
            .drawBehind {
                // the post at the start side, a little out from the signs, past the first and the last one
                val w = 7.dp.toPx(); val x = if (rtl) size.width - w - 6.dp.toPx() else 6.dp.toPx()
                drawRect(ink, Offset(x, -12.dp.toPx()), Size(w, size.height + 12.dp.toPx()))
            }
            .padding(start = 27.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        val shape = remember { SignShape(18.dp) }
        names.forEachIndexed { i, n ->
            Box(
                Modifier.fillMaxWidth().height(52.dp)
                    .drawWithCache {
                        val snow = snowCap(i + 1, size.width, density)
                        onDrawWithContent { drawContent(); drawSnow(snow, density) }
                    },
            ) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(shape).background(if (i % 2 == 0) Color(0xFF1F5FC4) else ink)
                        .padding(start = 14.dp, end = 30.dp, top = 12.dp), // the arrow's side is the end
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(iso(n), Modifier.weight(1f), style = Ski.type.sign.copy(fontSize = (28 * Ski.type.displayScale).sp), color = Color.White.copy(alpha = .6f), maxLines = 1)
                    Text("?", style = Ski.type.sign.copy(fontSize = (28 * Ski.type.displayScale).sp), color = Color.White.copy(alpha = .6f))
                }
            }
        }
    }
}

/** S2: a departures board, dark in day and night, its rows flipping in one after the other. */
@Composable
private fun DepartureBoard(s: LiftStatus, modifier: Modifier) {
    val head = Color(0xFFA3B3C8); val line = Color(0xFF2A3B55); val fg = Color(0xFFEAF0F7)
    val still = reducedMotion()
    val reasons = mapOf(
        "wind" to stringResource(R.string.status_reason_wind), "weather" to stringResource(R.string.status_reason_weather),
        "maintenance" to stringResource(R.string.status_reason_maintenance), "season" to stringResource(R.string.status_reason_season),
    )
    val aria = stringResource(R.string.status_board_aria)
    Column(modifier.fillMaxWidth().background(Color(0xFF0D1522)).padding(horizontal = 10.dp, vertical = 6.dp).semantics { contentDescription = aria }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 26.dp).drawBehind { drawRect(line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((w, k) in listOf(1.4f to R.string.status_board_col_lift, 0f to R.string.status_board_col_state, 1f to R.string.status_board_col_note))
                Text(stringResource(k), if (w > 0) Modifier.weight(w) else Modifier.width(70.dp), style = Ski.type.small.copy(fontSize = 11.5.sp), color = head)
        }
        s.names.forEachIndexed { i, n ->
            val o = s.isOpen(n)
            val turn = remember(s.report?.updated) { Animatable(if (still) 1f else 0f) }
            LaunchedEffect(s.report?.updated) { if (!still) { delay(60L * i); turn.animateTo(1f, tween(500)) } }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 36.dp)
                    .graphicsLayer { rotationX = 90f * (1 - turn.value); alpha = turn.value; cameraDistance = 12 * density }
                    .drawBehind { drawRect(line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(iso(n), Modifier.weight(1.4f), style = Ski.type.sign.copy(fontSize = (22 * Ski.type.displayScale).sp), color = fg, maxLines = 1, textAlign = TextAlign.Start)
                Text(if (o == true) stringResource(R.string.status_lift_open) else if (o == false) stringResource(R.string.status_lift_closed) else "—",
                    Modifier.width(70.dp), style = Ski.type.bodyBold.copy(fontSize = 14.sp),
                    color = if (o == true) Color(0xFF3CC47C) else if (o == false) Color(0xFFFF6A5F) else fg)
                Text(s.reason(n)?.let { reasons[it] ?: it }.orEmpty(), Modifier.weight(1f), style = Ski.type.body.copy(fontSize = 14.sp), color = fg)
            }
        }
    }
}

/** The home board's dot: grey without a report, green when the board is live. */
@Composable
fun LiveDot(on: Boolean) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) Ski.colors.green else Ski.colors.dash))
}

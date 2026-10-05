package io.github.pini236.skiapp.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.group.Sheet
import io.github.pini236.skiapp.ui.Ski
import java.text.NumberFormat

/** The round 19 locate icon: a ring, a dot and four ticks (proto.js ICON.locate), in the given colour. */
private fun DrawScope.locateIcon(c: Color) {
    val k = size.width / 24f
    val st = Stroke(2 * k, cap = StrokeCap.Round)
    val m = Offset(12 * k, 12 * k)
    drawCircle(c, 6.5f * k, m, style = st)
    drawCircle(c, 2.2f * k, m)
    drawLine(c, Offset(12 * k, 1.5f * k), Offset(12 * k, 4.5f * k), 2 * k, StrokeCap.Round)
    drawLine(c, Offset(12 * k, 19.5f * k), Offset(12 * k, 22.5f * k), 2 * k, StrokeCap.Round)
    drawLine(c, Offset(1.5f * k, 12 * k), Offset(4.5f * k, 12 * k), 2 * k, StrokeCap.Round)
    drawLine(c, Offset(19.5f * k, 12 * k), Offset(22.5f * k, 12 * k), 2 * k, StrokeCap.Round)
}

/** "Where am I": off on paper, lit in the glacier blue (the and-map-buttons and and-loc-on boards). */
@Composable
fun LocateKey(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    val label = stringResource(R.string.loc_button)
    val state = stringResource(if (on) R.string.app_on else R.string.app_off)
    Box(modifier.size(52.dp).background(if (on) c.glacier else c.paper).border(1.5.dp, if (on) c.glacier else c.rule)
        .clickable(role = Role.Switch, onClick = onClick).semantics { contentDescription = label; stateDescription = state },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) { locateIcon(if (on) c.paper else c.ink) }
    }
}

/** What the line at the bottom says for a state: its title, its second line, and its action if it has one. */
class WhereWords(val title: String, val sub: String, val dotOff: Boolean, val action: String? = null)

@Composable
fun whereWords(s: WhereAmI.State): WhereWords? {
    val nf = NumberFormat.getIntegerInstance(io.github.pini236.skiapp.i18n.Lang.current(androidx.compose.ui.platform.LocalContext.current.resources).locale)
    return when (s) {
        WhereAmI.State.Off -> null
        WhereAmI.State.Waiting -> WhereWords(stringResource(R.string.loc_waiting_title), stringResource(R.string.loc_waiting_sub), true)
        WhereAmI.State.Denied -> WhereWords(stringResource(R.string.loc_denied_title), stringResource(R.string.loc_denied_sub_phone), true, stringResource(R.string.loc_denied_action_phone))
        WhereAmI.State.Unavailable -> WhereWords(stringResource(R.string.loc_unavail_title), stringResource(R.string.loc_unavail_sub_phone), true, stringResource(R.string.loc_unavail_action_phone))
        is WhereAmI.State.Fixed -> {
            val acc = nf.format(roundAcc(s.accuracy))
            val where = stringResource(R.string.common_unit_m, nf.format(s.alt)) + " · " + stringResource(R.string.loc_accuracy, acc)
            when (val f = s.fix) {
                Locator.Fix.Outside -> WhereWords(stringResource(R.string.loc_out_title), stringResource(R.string.loc_out_sub), true)
                Locator.Fix.Approx -> WhereWords(stringResource(R.string.loc_approx_title, acc), stringResource(R.string.loc_approx_sub), false, stringResource(R.string.loc_approx_action))
                Locator.Fix.Low -> WhereWords(stringResource(R.string.loc_low_title, acc), stringResource(R.string.loc_low_sub), false)
                Locator.Fix.Free -> WhereWords(stringResource(R.string.loc_free), where, false)
                is Locator.Fix.OnRun -> WhereWords(stringResource(R.string.loc_on_run, "⁨${f.piste.key}⁩", stringResource(colorName(f.piste.color))), where, false)
                is Locator.Fix.OnLift -> WhereWords(stringResource(R.string.loc_on_lift, "⁨${f.lift.name}⁩"),
                    stringResource(R.string.loc_going_up) + " · " + stringResource(R.string.loc_accuracy, acc), false)
            }
        }
    }
}

/** The accuracy as the canvas writes it: "about 12 m", "about 400 m". */
fun roundAcc(a: Float): Int = when {
    a < 20f -> kotlin.math.max(1, a.toInt())
    a < 100f -> (a / 5f).toInt() * 5
    else -> (a / 50f).toInt() * 50
}

private fun colorName(c: String) = when (c) {
    "green" -> R.string.common_color_green
    "blue" -> R.string.common_color_blue
    "red" -> R.string.common_color_red
    else -> R.string.common_color_black
}

/** The line at the bottom of the map (.r19-where): a dot (grey when there is no place), the words, and an action. */
@Composable
fun WhereLine(w: WhereWords, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    Row(modifier.heightIn(min = 48.dp).shadow(6.dp).background(c.paper.copy(alpha = .96f)).border(1.5.dp, c.ink)
        .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val dot = if (w.dotOff) c.dash else c.glacier
        Box(Modifier.size(15.dp).background(dot, CircleShape).padding(1.5.dp).background(c.paper, CircleShape).padding(1.5.dp).background(dot, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(w.title, style = Ski.type.bodyBold.copy(fontSize = 14.5.sp, lineHeight = 19.sp), color = c.ink)
            Text(w.sub, style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = c.muted)
        }
        if (w.action != null) Box(Modifier.heightIn(min = 44.dp).border(1.5.dp, c.rule).background(c.paper).clickable(role = Role.Button, onClick = onAction)
            .padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(w.action, style = Ski.type.bodyBold.copy(fontSize = 13.sp), color = c.glacier)
        }
    }
}

/** The dot on the map from above (Overview): the same circle and dot as in 3D, at the meeting map's scale. */
@Composable
fun MeDot2D(view: io.github.pini236.skiapp.meet.MeetView, s: WhereAmI.State.Fixed, modifier: Modifier) {
    val c = Ski.colors
    Canvas(modifier) {
        val p = Offset(view.sx(s.x), view.sy(s.y))
        val r = kotlin.math.max(14.dp.toPx(), s.accuracy * view.k)
        drawCircle(c.glacier.copy(alpha = .16f), r, p)
        drawCircle(c.glacier.copy(alpha = .6f), r, p, style = Stroke(1.5.dp.toPx()))
        s.bearing?.takeIf { s.fix != Locator.Fix.Approx && s.fix != Locator.Fix.Low }?.let { b ->
            val a = Math.toRadians(b.toDouble()); val ux = kotlin.math.sin(a).toFloat(); val uy = -kotlin.math.cos(a).toFloat()
            val tip = 23.dp.toPx(); val base = 12.dp.toPx(); val half = 7.dp.toPx()
            drawPath(Path().apply {
                moveTo(p.x + ux * tip, p.y + uy * tip)
                lineTo(p.x + ux * base - uy * half, p.y + uy * base + ux * half)
                lineTo(p.x + ux * base + uy * half, p.y + uy * base - ux * half); close()
            }, c.glacier)
        }
        drawCircle(Color.Black.copy(alpha = .24f), 10.5.dp.toPx(), p + Offset(0f, 1.dp.toPx()))
        drawCircle(c.paper, 9.dp.toPx(), p)
        drawCircle(c.glacier, 6.dp.toPx(), p)
    }
}

/** Before the phone asks (the r19-sheet): the location stays on the phone, only while the map is open, and offline. */
@Composable
fun BoxScope.AskSheet(onGo: () -> Unit, onLater: () -> Unit) {
    val c = Ski.colors
    Sheet(onLater) {
        Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).background(c.glacier))
        Text(stringResource(R.string.loc_ask_title), Modifier.padding(top = 12.dp, bottom = 12.dp),
            style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (34f / 44f)), color = c.ink)
        @Composable fun Bullet(icon: DrawScope.(Color) -> Unit, text: androidx.compose.ui.text.AnnotatedString) {
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.padding(top = 2.dp).size(22.dp)) { icon(c.glacier) }
                Text(text, Modifier.weight(1f), style = Ski.type.body.copy(fontSize = 14.5.sp, lineHeight = 21.sp), color = c.ink)
            }
        }
        val bold = stringResource(R.string.loc_ask_stays_b); val rest = stringResource(R.string.loc_ask_stays)
        Bullet({ col -> phoneIcon(col) }, buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }; append(" "); append(rest) })
        Bullet({ col -> mapIcon(col) }, buildAnnotatedString { append(stringResource(R.string.loc_ask_map)) })
        Bullet({ col -> pinIcon(col) }, buildAnnotatedString { append(stringResource(R.string.loc_ask_offline)) })
        Row(Modifier.padding(top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).heightIn(min = 48.dp).background(c.accent).border(1.5.dp, c.accent).clickable(role = Role.Button, onClick = onGo),
                contentAlignment = Alignment.Center) { Text(stringResource(R.string.loc_ask_go), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = c.onAccent) }
            Box(Modifier.weight(1f).heightIn(min = 48.dp).background(c.paper).border(1.5.dp, c.ink).clickable(role = Role.Button, onClick = onLater),
                contentAlignment = Alignment.Center) { Text(stringResource(R.string.loc_ask_later), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = c.ink) }
        }
        Text(stringResource(R.string.loc_ask_after_phone), style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.muted)
    }
}

// the three icons of the sheet (proto.js ICON.phone, map, sat), 24-unit boxes
private fun DrawScope.phoneIcon(c: Color) {
    val k = size.width / 24f; val st = Stroke(2 * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRoundRect(c, Offset(6 * k, 2.5f * k), androidx.compose.ui.geometry.Size(12 * k, 19 * k), androidx.compose.ui.geometry.CornerRadius(1.5f * k), style = st)
    drawLine(c, Offset(10.5f * k, 18.5f * k), Offset(13.5f * k, 18.5f * k), 2 * k, StrokeCap.Round)
}
private fun DrawScope.mapIcon(c: Color) {
    val k = size.width / 24f; val st = Stroke(2 * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(9 * k, 4 * k); lineTo(3 * k, 6.5f * k); lineTo(3 * k, 19.5f * k); lineTo(9 * k, 17 * k); lineTo(15 * k, 19.5f * k)
        lineTo(21 * k, 17 * k); lineTo(21 * k, 4 * k); lineTo(15 * k, 6.5f * k); close()
        moveTo(9 * k, 4 * k); lineTo(9 * k, 17 * k); moveTo(15 * k, 6.5f * k); lineTo(15 * k, 19.5f * k)
    }, c, style = st)
}
private fun DrawScope.pinIcon(c: Color) {
    val k = size.width / 24f; val st = Stroke(2 * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(12 * k, 21 * k)
        cubicTo(12 * k, 21 * k, 5.5f * k, 14.8f * k, 5.5f * k, 10 * k)
        cubicTo(5.5f * k, 6.4f * k, 8.4f * k, 3.5f * k, 12 * k, 3.5f * k)
        cubicTo(15.6f * k, 3.5f * k, 18.5f * k, 6.4f * k, 18.5f * k, 10 * k)
        cubicTo(18.5f * k, 14.8f * k, 12 * k, 21 * k, 12 * k, 21 * k); close()
    }, c, style = st)
    drawCircle(c, 2.3f * k, Offset(12 * k, 10 * k), style = st)
}

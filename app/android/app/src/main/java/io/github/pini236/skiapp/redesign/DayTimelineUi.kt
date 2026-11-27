package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.dayrec.ui.shortRuns
import io.github.pini236.skiapp.dayrec.ui.titleOf
import java.util.Locale

/** The colours a row draws with, by the page's day or night. */
internal class TlColors(val ink: Color, val muted: Color, val rule: Color, val glass: Color, val night: Boolean) {
    val amber = Color(0xFFE2B13C)
    val acc = Color(0xFF7C5BD8)
}

/** The width of the time column: wider in large text (board DT3, 55 against 42). */
@Composable private fun timeWidth() = if (LocalDensity.current.fontScale > 1.15f) 55.dp else 44.dp

/** Four numbers over the ridge (board DT1): runs, km skied, metres down, hours on the mountain. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FourNumbers(descents: Int, distM: Double, dropM: Double, hours: String, c: TlColors) {
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val km = String.format(Locale.ROOT, "%.1f", distM / 1000.0)
        for ((v, cap) in listOf(
            descents.toString() to pluralStringResource(R.plurals.app_day_sum_descents_n, descents),
            km to stringResource(R.string.app_md_km),
            metres(dropM) to stringResource(R.string.app_rec_m_down),
            hours to stringResource(R.string.app_dt_hours),
        )) Column {
            Text(v, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 26.sp, color = c.ink)
            Text(cap, fontFamily = Gudi, fontSize = 12.sp, color = c.muted)
        }
    }
}

/** "לאן הלך היום" (board DT1): one bar of four parts by time, and its legend with the minutes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WhereBar(w: DayTimelineRules.Where, ramp: IntArray, c: TlColors) {
    val total = w.total.coerceAtLeast(1.0)
    val parts = listOf(
        Triple(R.string.app_dt_skiing, w.ski, null as Color?),
        Triple(R.string.app_dt_lifts, w.lift, c.muted),
        Triple(R.string.app_dt_queues, w.queue, c.amber),
        Triple(R.string.app_dt_breaks, w.rest, c.rule),
    )
    Text(stringResource(R.string.app_dt_where), Modifier.padding(top = 4.dp, bottom = 8.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        for ((_, s, col) in parts) if (s > 0) Box(Modifier.weight(s.toFloat().coerceAtLeast(0.001f)).height(14.dp)
            .let { m -> if (col != null) m.background(col) else m.drawBehind {
                // skiing takes the speed light, slow to fast
                val n = ramp.size
                for (i in 0 until n) drawRect(Color(0xFF000000.toInt() or ramp[i]), Offset(size.width * i / n, 0f), androidx.compose.ui.geometry.Size(size.width / n + 1f, size.height))
            } })
    }
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((name, s, col) in parts) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(col ?: Color(0xFF000000.toInt() or ramp[ramp.size / 2])))
            Text(stringResource(name) + " · " + DayTimelineRules.minutes(s), fontFamily = Gudi, fontSize = 12.sp, color = c.muted)
        }
    }
}

/** A part of the day's chapters: the head with its descents count. */
@Composable
internal fun ChapterHead(title: Int, descents: Int, c: TlColors) {
    Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.muted)
        Text(stringResource(R.string.app_day_n_descents, descents), fontFamily = Gudi, fontSize = 12.sp, color = c.muted)
    }
}

internal enum class Node { Lift, Descent, Cup, Fork, Bench, Walk, Queue }

/** The node of a row on the spine: time column | node | content (board DT1). [spine] draws the line through it. */
@Composable
internal fun TimelineLine(time: String, node: Node, nodeColor: Color, spineColor: Color, dotted: Boolean, c: TlColors, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(time, Modifier.width(timeWidth()).padding(top = 8.dp), fontFamily = Gudi, fontSize = 13.sp, color = c.muted)
        Box(Modifier.width(38.dp).fillMaxHeight1().drawBehind {
            val x = size.width / 2
            drawLine(spineColor, Offset(x, 0f), Offset(x, size.height), 2.5.dp.toPx(),
                pathEffect = if (dotted) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())) else null, cap = StrokeCap.Round)
        }, contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 4.dp).size(30.dp).clip(RoundedCornerShape(15.dp)).background(nodeColor), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(18.dp)) { drawNode(node, Color.White) }
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) { content() }
    }
}

private fun Modifier.fillMaxHeight1() = this.heightIn(min = 40.dp).fillMaxHeight()

/** The glyphs, drawn small and plain (no stop type beyond the analysis's: lift, descent, break, walk, queue). */
private fun DrawScope.drawNode(n: Node, col: Color) {
    val w = size.width; val s = Stroke(width = w * .11f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(col, Offset(w * x1, w * y1), Offset(w * x2, w * y2), s.width, StrokeCap.Round)
    when (n) {
        Node.Descent -> { line(.5f, .15f, .5f, .85f); line(.22f, .58f, .5f, .86f); line(.78f, .58f, .5f, .86f) }
        Node.Lift -> { line(.1f, .2f, .9f, .2f); line(.5f, .2f, .5f, .45f); drawRoundRect(col, Offset(w * .28f, w * .45f), androidx.compose.ui.geometry.Size(w * .44f, w * .38f), androidx.compose.ui.geometry.CornerRadius(w * .08f), style = s) }
        Node.Cup -> { drawPath(Path().apply { moveTo(w * .2f, w * .3f); lineTo(w * .2f, w * .6f); quadraticTo(w * .2f, w * .85f, w * .45f, w * .85f); lineTo(w * .5f, w * .85f); quadraticTo(w * .7f, w * .85f, w * .7f, w * .6f); lineTo(w * .7f, w * .3f); close() }, col, style = s)
            line(.7f, .42f, .85f, .42f); line(.85f, .42f, .85f, .55f) }
        Node.Fork -> { line(.3f, .12f, .3f, .88f); line(.18f, .12f, .18f, .38f); line(.42f, .12f, .42f, .38f); line(.7f, .12f, .7f, .88f); line(.7f, .12f, .85f, .4f) }
        Node.Bench -> { line(.15f, .45f, .85f, .45f); line(.15f, .45f, .15f, .8f); line(.85f, .45f, .85f, .8f); line(.2f, .25f, .8f, .25f) }
        Node.Walk -> { drawCircle(col, w * .08f, Offset(w * .55f, w * .15f)); line(.5f, .3f, .4f, .62f); line(.4f, .62f, .25f, .88f); line(.4f, .62f, .6f, .88f); line(.5f, .3f, .7f, .5f) }
        Node.Queue -> { line(.25f, .12f, .75f, .12f); line(.25f, .88f, .75f, .88f); line(.3f, .12f, .7f, .5f); line(.7f, .5f, .3f, .88f); line(.7f, .12f, .3f, .5f); line(.3f, .5f, .7f, .88f) }
    }
}

@Composable
internal fun LiftRowView(r: DayTimelineRules.Lift, at: String, c: TlColors) {
    TimelineLine(at, Node.Lift, c.muted, c.muted, dotted = true, c = c) {
        Text(stringResource(R.string.app_tl_lift_head, r.name), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = c.ink)
        Text(stringResource(R.string.app_tl_lift_meta, r.minutes, r.riseM?.let { String.format(Locale.ROOT, "%,d", Math.round(it)) } ?: "—"), fontFamily = Gudi, fontSize = 13.sp, color = c.muted)
        if (r.queueMin > 0) Text(stringResource(R.string.app_day_queue, r.queueMin), Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(c.amber.copy(alpha = .22f)).padding(horizontal = 8.dp, vertical = 2.dp),
            fontFamily = Gudi, fontSize = 12.sp, color = c.ink)
    }
}

@Composable
internal fun QueueRowView(r: DayTimelineRules.Queue, at: String, c: TlColors) {
    TimelineLine(at, Node.Queue, c.amber, c.muted, dotted = true, c = c) {
        Text(stringResource(R.string.app_day_queue, r.minutes), fontFamily = Gudi, fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun WalkRowView(r: DayTimelineRules.Walk, at: String, c: TlColors) {
    TimelineLine(at, Node.Walk, c.muted, c.muted, dotted = true, c = c) {
        Text(stringResource(R.string.app_tl_walk, Math.round(r.distM)), fontFamily = Gudi, fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun BreakRowView(r: DayTimelineRules.Break, from: String, to: String, c: TlColors) {
    val (node, where) = when (val k = r.kind) {
        is DayTimelineRules.BreakKind.Cafe -> Node.Cup to k.name
        is DayTimelineRules.BreakKind.Food -> Node.Fork to k.name
        is DayTimelineRules.BreakKind.Bench -> Node.Bench to k.atopLift?.let { stringResource(R.string.app_dt_atop, it) }
    }
    TimelineLine(from, node, c.amber, c.muted, dotted = true, c = c) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.amber.copy(alpha = .18f)).padding(12.dp)) {
            Text(stringResource(R.string.app_day_lunch, r.minutes), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink)
            Text("$from–$to" + (where?.let { " · $it" } ?: ""), fontFamily = Gudi, fontSize = 13.sp, color = c.muted)
        }
    }
}

/**
 * A descent: a glass card with the run signs and names (every leg its own line, wrapping and never cut), the speed strip
 * of the descent, the meta and its chips (board DT1). [strip]: speed bands from slow to fast, one per bin.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DescentRowView(r: DayTimelineRules.Descent, at: String, gapAt: String?, runs: Runs?, strip: IntArray, ramp: IntArray, topBin: Int, c: TlColors, onOpen: () -> Unit) {
    val d = r.d
    TimelineLine(at, Node.Descent, c.acc, c.acc.copy(alpha = .55f), dotted = false, c = c) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.glass).clickable(role = Role.Button) { onOpen() }.padding(12.dp)) {
            if (d.runs.isEmpty()) Text(stringResource(R.string.app_day_off), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
            else d.runs.forEachIndexed { i, leg ->
                val keys = leg.split(" · ")
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (i > 0) Text("›", fontFamily = Gudi, fontSize = 16.sp, color = c.muted)
                    Box(Modifier.padding(top = 5.dp).size(11.dp).clip(RoundedCornerShape(2.dp)).background(signColor(runs?.pistes?.firstOrNull { p -> p.key == keys[0] }?.color)))
                    Text(shortRuns(keys.joinToString(" · ") { k -> titleOf(runs, k) }), Modifier.weight(1f, fill = false), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                }
            }
            if (strip.isNotEmpty()) Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp).clip(RoundedCornerShape(5.dp))) {
                val n = strip.size
                for (i in 0 until n) drawRect(Color(0xFF000000.toInt() or ramp[strip[i].coerceIn(0, ramp.size - 1)]), Offset(size.width * i / n, 0f), androidx.compose.ui.geometry.Size(size.width / n + 1f, size.height))
                if (topBin in 0 until n) { val x = size.width * (topBin + .5f) / n; drawCircle(Color.White, 5.dp.toPx(), Offset(x, size.height / 2)); drawCircle(Color(0xFF0E2233), 5.dp.toPx(), Offset(x, size.height / 2), style = Stroke(1.5.dp.toPx())) }
            }
            Text(stringResource(R.string.app_tl_run_meta, String.format(Locale.ROOT, "%.1f", d.distM / 1000.0), metres(d.dropM), LiveNumbers.maxKmh(d.maxKmh)),
                Modifier.padding(top = 6.dp), fontFamily = Gudi, fontSize = 13.sp, color = c.muted)
            if (r.top) Text(stringResource(R.string.app_tl_top_speed, LiveNumbers.maxKmh(d.maxKmh), d.runs.firstOrNull()?.split(" · ")?.firstOrNull()?.let { k -> titleOf(runs, k) } ?: ""),
                fontFamily = Gudi, fontSize = 13.sp, color = c.muted)
            val chips = ArrayList<String>()
            if (r.stops > 0) chips += pluralStringResource(R.plurals.app_dt_stops, r.stops, r.stops)
            if (d.runs.isNotEmpty() && d.offRuns) chips += stringResource(R.string.app_day_off)
            gapAt?.let { chips += stringResource(R.string.app_tl_gap_at, it) }
            if (chips.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in chips) Text(t, Modifier.clip(RoundedCornerShape(10.dp)).background(c.rule).padding(horizontal = 8.dp, vertical = 3.dp), fontFamily = Gudi, fontSize = 12.sp, color = c.ink)
            }
        }
    }
}

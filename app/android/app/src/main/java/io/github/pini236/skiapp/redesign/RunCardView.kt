package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import io.github.pini236.skiapp.data.RunFacts

/**
 * The run's profile (the run card): its elevation along its length, the top at the start side (mirrors with the language),
 * the line coloured per stretch by the slope thresholds, a tick every 500 m, the heights at both ends and a pin with the
 * degrees over the steepest point. One item for the screen reader (numbers only, no new words).
 */
@Composable
internal fun RunProfile(facts: RunFacts, slope: List<Color>, k: SheetInk, paper: Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pts = facts.points
    val segs = remember(facts) { RunCardRules.segments(pts) }
    val pin = remember(facts) { RunCardRules.steepestAt(pts) }
    val ticks = remember(facts) { RunCardRules.ticks(facts.length) }
    val measurer = rememberTextMeasurer()
    val deg = if (pin >= 0) Math.round(pts[pin].a) else 0
    val desc = "${facts.top} – ${facts.bot}, $deg°"
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(104.dp)) {
            if (pts.size < 2 || slope.size < 4) return@Canvas
            val hi = pts.maxOf { it.h }; val lo = pts.minOf { it.h }
            val span = (hi - lo).coerceAtLeast(1f)
            val padT = 30.dp.toPx(); val padB = 12.dp.toPx()
            val len = facts.length.coerceAtLeast(1f)
            fun x(d: Float): Float { val f = d / len; return size.width * (if (rtl) 1f - f else f) }
            fun y(h: Float): Float = padT + (size.height - padT - padB) * (1f - (h - lo) / span)
            val base = size.height - padB
            // the area under each stretch, then the line on a paper case
            for (s in segs) {
                val a = if (s.cls == 3) .30f else .20f
                val path = Path().apply {
                    moveTo(x(pts[s.from].d), base)
                    for (i in s.from..s.to) lineTo(x(pts[i].d), y(pts[i].h))
                    lineTo(x(pts[s.to].d), base); close()
                }
                drawPath(path, slope[s.cls].copy(alpha = a))
            }
            val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.d), y(p.h)) else lineTo(x(p.d), y(p.h)) } }
            drawPath(line, paper, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (s in segs) {
                val sp = Path().apply { for (i in s.from..s.to) if (i == s.from) moveTo(x(pts[i].d), y(pts[i].h)) else lineTo(x(pts[i].d), y(pts[i].h)) }
                drawPath(sp, slope[s.cls], style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawLine(k.line, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            for (t in ticks) drawLine(k.ink2.copy(alpha = .6f), Offset(x(t), base), Offset(x(t), base + 5.dp.toPx()), 1.dp.toPx())
            if (pin >= 0) {
                val px = x(pts[pin].d); val py = y(pts[pin].h)
                val txt = measurer.measure("$deg°", TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White))
                val w = txt.size.width + 14.dp.toPx(); val h = 22.dp.toPx()
                val left = (px - w / 2f).coerceIn(0f, size.width - w)
                val top = (py - h - 8.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(k.ink, Offset(left, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(11.dp.toPx()))
                drawText(txt, topLeft = Offset(left + 7.dp.toPx(), top + (h - txt.size.height) / 2f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${facts.top}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = k.ink2, fontFamily = Gudi)
            Box(Modifier)
            Text("${facts.bot}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = k.ink2, fontFamily = Gudi)
        }
    }
}

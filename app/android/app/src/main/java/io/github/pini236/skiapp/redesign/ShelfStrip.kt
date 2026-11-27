package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The colour of a tick's tone (the lenses' own colours, the run's colour for [Tone.RUN]). */
internal fun toneColor(t: Tone, run: Color, k: SheetInk): Color = when (t) {
    Tone.RUN -> run
    Tone.SUN -> Color(0xFFFFC93A)
    Tone.SHADE -> Color(0xFFB9CCFA)
    Tone.OPEN -> run
    Tone.CLOSED -> Color(if (k.night) 0xFF7C8798 else 0xFF8A93A3)
    Tone.UNKNOWN -> k.ink2.copy(alpha = .35f)
    Tone.SNOW_HI -> k.acc
    Tone.SNOW_LO -> Color(0xFF7FB2FF)
    Tone.NONE -> k.ink2.copy(alpha = .3f)
}

/**
 * The run strip (handoff version 2, SV3 to SV6): one tick per run in the cards' order, 8 wide (narrower when there are many),
 * 3 apart (8 at a group boundary), 12 to 30 high; the middle card's run has a ring and a small pointer. A tap on a tick
 * brings its card, a drag scrubs. When the chip changes the ticks move to their new places in 300 ms, 8 ms apart from the start.
 */
@Composable
internal fun RunStrip(ticks: List<TickSpec>, colourOf: (String) -> Color, mid: Int, k: SheetInk, a11y: String, onSeek: (Int, Boolean) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var from by remember { mutableStateOf(ticks) }
    var cur by remember { mutableStateOf(ticks) }
    val p = remember { Animatable(1f) }
    val reduced = io.github.pini236.skiapp.ui.Motion.reduced(androidx.compose.ui.platform.LocalContext.current)
    LaunchedEffect(ticks) {
        if (ticks.map { it.key } == cur.map { it.key } && ticks.map { it.h to it.tone } == cur.map { it.h to it.tone }) return@LaunchedEffect
        from = cur; cur = ticks
        p.snapTo(0f)
        p.animateTo(1f, tween(if (reduced) 150 else 300 + 8 * ticks.size, easing = LinearEasing))
    }
    Canvas(Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = a11y }
        .pointerInput(ticks.size, rtl) { detectTapGestures { o -> onSeek(Shelf.tickAt(o.x, size.width.toFloat(), ticks.size, rtl), true) } }
        .pointerInput(ticks.size, rtl) { detectDragGestures { c, _ -> c.consume(); onSeek(Shelf.tickAt(c.position.x, size.width.toFloat(), ticks.size, rtl), false) } }) {
        val now = cur
        val a = layoutOf(now, size.width, density); val old = layoutOf(from, size.width, density)
        val oldIdx = from.withIndex().associate { it.value.key to it.index }
        val n = now.size
        val dur = 300f + 8f * n
        val base = size.height - 6.dp.toPx()
        for ((i, t) in now.withIndex()) {
            val oi = oldIdx[t.key]
            // staggered from the start side: tick i begins 8 ms after tick i-1
            val e = if (reduced) p.value else ((p.value * dur - 8f * i) / 300f).coerceIn(0f, 1f).let { x -> x * x * (3 - 2 * x) }
            val x0 = oi?.let { old.x[it] } ?: a.x[i]; val h0 = oi?.let { from[it].h } ?: t.h
            val c1 = toneColor(t.tone, colourOf(t.key), k)
            val c0 = oi?.let { toneColor(from[it].tone, colourOf(t.key), k) } ?: c1
            val x = mirror(x0 + (a.x[i] - x0) * e, a.w, size.width, rtl)
            val h = (h0 + (t.h - h0) * e).dp.toPx()
            val c = lerp(c0, c1, e)
            val r = a.w / 2f
            if (i == mid) {
                // the ring: 2 of the background's colour, then 2 of the ink
                drawRoundRect(if (k.night) Color(0xFF0F1828) else Color.White, Offset(x - 4.dp.toPx(), base - h - 4.dp.toPx()), Size(a.w + 8.dp.toPx(), h + 8.dp.toPx()), CornerRadius(r + 4.dp.toPx()), style = Stroke(2.dp.toPx()))
                drawRoundRect(k.ink, Offset(x - 2.dp.toPx(), base - h - 2.dp.toPx()), Size(a.w + 4.dp.toPx(), h + 4.dp.toPx()), CornerRadius(r + 2.dp.toPx()), style = Stroke(2.dp.toPx()))
                val cx = x + a.w / 2f
                drawPath(Path().apply { moveTo(cx - 3.dp.toPx(), size.height - 1.dp.toPx()); lineTo(cx + 3.dp.toPx(), size.height - 1.dp.toPx()); lineTo(cx, size.height - 6.dp.toPx()); close() }, k.ink)
            }
            drawRoundRect(c, Offset(x, base - h), Size(a.w, h), CornerRadius(r))
        }
    }
}

private class TickLayout(val x: FloatArray, val w: Float)

/** The ticks' left edges (start-side order, not mirrored) and the tick width: 8 dp, or less when 8, 3 and 8 at the groups do not fit. */
private fun layoutOf(ts: List<TickSpec>, width: Float, density: Float): TickLayout {
    val n = ts.size
    if (n == 0) return TickLayout(FloatArray(0), 0f)
    val gap = 3f * density; val gGap = 8f * density
    val gaps = ts.withIndex().sumOf { (i, t) -> if (i == 0) 0.0 else (if (t.groupStart) gGap else gap).toDouble() }.toFloat()
    val w = minOf(8f * density, ((width - gaps) / n).coerceAtLeast(1.5f * density))
    val total = n * w + gaps
    var x = (width - total) / 2f
    val xs = FloatArray(n)
    for ((i, t) in ts.withIndex()) { if (i > 0) x += if (t.groupStart) gGap else gap; xs[i] = x; x += w }
    return TickLayout(xs, w)
}

private fun mirror(x: Float, w: Float, total: Float, rtl: Boolean) = if (rtl) total - x - w else x

/** The chip of the shelf (handoff version 2): 38 high, an icon (16) and the word; the lit one takes the colour of what it paints. */
@Composable
internal fun ShelfChip(sort: ShelfSort, label: String, on: Boolean, enabled: Boolean, k: SheetInk, a11y: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    val darkText = Color(0xFF0F1828)
    val (fill, text) = when {
        !on -> k.surf.copy(alpha = 1f) to k.ink
        sort == ShelfSort.SUN -> Color(0xFFFFC93A) to Color(0xFF3B2A00)
        sort == ShelfSort.OPEN -> k.good to (if (k.night) darkText else Color.White)
        sort == ShelfSort.SNOW -> k.acc to k.onAcc
        else -> k.ink to (if (k.night) darkText else Color.White)
    }
    Box(modifier.heightIn44().alpha(if (enabled) 1f else .4f).semantics { role = Role.Tab; selected = on; contentDescription = a11y }.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(Modifier.height(38.dp).let { if (on) it.shadow(8.dp, shape, clip = false, ambientColor = Color(0x400A192D), spotColor = Color(0x400A192D)) else it }
            .clip(shape).background(fill).let { if (on) it else it.border(1.dp, k.line, shape) }.padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(16.dp)) { chipIcon(sort, text) }
            Text(label, fontFamily = Gudi, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 13.5.sp, color = text, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

private fun Modifier.heightIn44() = this.then(Modifier.height(44.dp))

/** Small line icons (16): a lift, a slope, a ruler, the sun, a tick, a snowflake. */
private fun DrawScope.chipIcon(s: ShelfSort, c: Color) {
    val w = size.width; val st = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    when (s) {
        ShelfSort.LIFT -> { drawLine(c, Offset(w * .08f, w * .2f), Offset(w * .92f, w * .2f), st.width, StrokeCap.Round)
            drawLine(c, Offset(w * .5f, w * .2f), Offset(w * .5f, w * .5f), st.width, StrokeCap.Round)
            drawRoundRect(c, Offset(w * .3f, w * .5f), Size(w * .4f, w * .36f), CornerRadius(w * .08f), style = st) }
        ShelfSort.EASY -> drawPath(Path().apply { moveTo(w * .08f, w * .86f); lineTo(w * .46f, w * .16f); lineTo(w * .92f, w * .86f); close() }, c, style = st)
        ShelfSort.LONG -> { drawLine(c, Offset(w * .1f, w * .5f), Offset(w * .9f, w * .5f), st.width, StrokeCap.Round)
            drawLine(c, Offset(w * .1f, w * .3f), Offset(w * .1f, w * .7f), st.width, StrokeCap.Round)
            drawLine(c, Offset(w * .9f, w * .3f), Offset(w * .9f, w * .7f), st.width, StrokeCap.Round) }
        ShelfSort.SUN -> { drawCircle(c, w * .2f, Offset(w / 2, w / 2), style = st)
            for (i in 0 until 8) { val a = Math.PI / 4 * i; drawLine(c, Offset(w / 2 + (w * .34f * Math.cos(a)).toFloat(), w / 2 + (w * .34f * Math.sin(a)).toFloat()),
                Offset(w / 2 + (w * .46f * Math.cos(a)).toFloat(), w / 2 + (w * .46f * Math.sin(a)).toFloat()), st.width, StrokeCap.Round) } }
        ShelfSort.OPEN -> drawPath(Path().apply { moveTo(w * .15f, w * .54f); lineTo(w * .4f, w * .78f); lineTo(w * .86f, w * .24f) }, c, style = st)
        ShelfSort.SNOW -> for (i in 0 until 3) { val a = Math.PI / 3 * i
            drawLine(c, Offset(w / 2 + (w * .42f * Math.cos(a)).toFloat(), w / 2 + (w * .42f * Math.sin(a)).toFloat()),
                Offset(w / 2 - (w * .42f * Math.cos(a)).toFloat(), w / 2 - (w * .42f * Math.sin(a)).toFloat()), st.width, StrokeCap.Round) }
    }
}

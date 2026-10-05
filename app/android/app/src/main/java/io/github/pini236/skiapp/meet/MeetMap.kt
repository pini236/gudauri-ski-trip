package io.github.pini236.skiapp.meet

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.ui.SkiColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * What the meeting point's map shows: a window on the mountain in the site's metres ([x], [y] at the top left, [span]
 * metres across), and the moves between windows (the site's goTo, fitAll and zoomAt in site/js/app.js).
 */
@Stable
class MeetView {
    /** Reduced motion (ui/Motion.kt): the map jumps to a place instead of gliding there, as on the site. */
    var still = false
    var x by mutableFloatStateOf(0f); private set
    var y by mutableFloatStateOf(0f); private set
    var span by mutableFloatStateOf(1f); private set
    var w by mutableFloatStateOf(1f); internal set
    var h by mutableFloatStateOf(1f); internal set
    /** the first window is set once the map knows its size */
    var ready by mutableStateOf(false); internal set
    private var move: Job? = null

    val k: Float get() = w / span
    fun sx(mx: Float) = (mx - x) * k
    fun sy(my: Float) = (my - y) * k
    fun mx(px: Float) = x + px / k
    fun my(py: Float) = y + py / k

    fun stop() { move?.cancel(); move = null }

    private fun to(nx: Float, ny: Float, ns: Float) { x = nx; y = ny; span = ns }

    /** A spot a little below the middle, so the pin above it shows whole (the site's goTo). */
    fun goTo(scope: CoroutineScope, mx: Float, my: Float, s: Float, ms: Int) {
        val tx = mx - s / 2; val ty = my - s * h / w * 0.55f
        animate(scope, tx, ty, s, ms) { t -> if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2 }
    }

    fun fitAll(scope: CoroutineScope, stations: List<Station>, ms: Int) {
        if (stations.isEmpty()) return
        val a = stations.minOf { it.x }; val c = stations.maxOf { it.x }; val b = stations.minOf { it.y }; val d = stations.maxOf { it.y }
        val s = max(c - a, (d - b) * w / h) * 1.15f
        goTo(scope, (a + c) / 2, (b + d) / 2 + (d - b) * .05f, s, ms)
    }

    /**
     * A box of the map, whole, in the top [free] part of the screen (above a panel), [pad] times its size and at least
     * [least] metres across (the run map's fit, 1.12, and its focusOn, 1.5 and 900 m).
     */
    fun frame(scope: CoroutineScope?, a: Float, b: Float, c: Float, d: Float, pad: Float, least: Float, free: Float, ms: Int) {
        val f = free.coerceIn(.3f, 1f)
        val s = (max(max(c - a, (d - b) * w / (h * f)), least) * pad).coerceIn(300f, 9000f)
        val tx = (a + c) / 2 - s / 2; val ty = (b + d) / 2 - s * h / w * f / 2
        if (scope == null || ms <= 0) { stop(); to(tx, ty, s) } else animate(scope, tx, ty, s, ms) { t -> if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2 }
    }

    /** Zoom by [f] around a point of the map (the middle when null); 300 m to 9 km across. */
    fun zoomAt(scope: CoroutineScope?, f: Float, px: Float?, py: Float?, ms: Int) {
        val ns = (span * f).coerceIn(300f, 9000f)
        val cx = px ?: (x + span / 2); val cy = py ?: (y + span * h / w / 2)
        val fx = (cx - x) / span; val fy = (cy - y) / (span * h / w)
        val tx = cx - fx * ns; val ty = cy - fy * ns * h / w
        if (scope == null || ms <= 0) { stop(); to(tx, ty, ns) } else animate(scope, tx, ty, ns, ms) { t -> 1 - (1 - t).pow(3) }
    }

    fun pan(dx: Float, dy: Float) { stop(); x -= dx / k; y -= dy / k }

    private fun animate(scope: CoroutineScope, tx: Float, ty: Float, ts: Float, ms: Int, ease: (Float) -> Float) {
        stop()
        if (ms <= 0 || !ready || still) { to(tx, ty, ts); return }
        val fx = x; val fy = y; val fs = span
        move = scope.launch {
            val t0 = withFrameNanos { it }
            while (isActive) {
                val t = min(1f, (withFrameNanos { it } - t0) / 1e6f / ms)
                val e = ease(t)
                to(fx + (tx - fx) * e, fy + (ty - fy) * e, fs + (ts - fs) * e)
                if (t >= 1f) break
            }
        }
    }
}

/** The runs and lifts of the main side as paths in metres, built once (the site's mm-run and mm-lift). */
class MeetLines(runs: Runs) {
    val runs: Map<String, android.graphics.Path> = runs.mainPistes.filter { it.named }.groupBy { it.color }.mapValues { (_, ps) ->
        android.graphics.Path().also { p -> ps.forEach { r -> r.lines.forEach { l -> for (i in 0 until l.size / 2) if (i == 0) p.moveTo(l[0], l[1]) else p.lineTo(l[i * 2], l[i * 2 + 1]) } } }
    }
    val lifts = android.graphics.Path().also { p -> runs.mainLifts.filter { it.name.isNotEmpty() }.forEach { l -> for (i in 0 until l.pts.size / 2) if (i == 0) p.moveTo(l.pts[0], l.pts[1]) else p.lineTo(l.pts[i * 2], l.pts[i * 2 + 1]) } }
}

/** The pin of the site (mm-pin): a drop with a dot, 28 units tall, its tip on the station. */
private val PIN = android.graphics.Path().apply {
    moveTo(0f, 0f); cubicTo(0f, 0f, -14f, -17f, -14f, -28f)
    arcTo(RectF(-14f, -42f, 14f, -14f), 180f, 180f, false)
    cubicTo(14f, -17f, 0f, 0f, 0f, 0f); close()
}

private class Paints(c: SkiColors, density: Float) {
    val d = density
    fun stroke(color: Color, a: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color.copy(alpha = color.alpha * a).toArgb(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    fun fill(color: Color, a: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color.copy(alpha = color.alpha * a).toArgb() }
    val hill = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        if (c.dark) { // grayscale, then brightness .42, then contrast 1.35, as the site's night map (CSS filter order)
            val g = ColorMatrix().apply { setSaturation(0f) }
            val k = .42f * 1.35f; val off = (1 - 1.35f) * 127.5f
            g.postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, off, 0f, k, 0f, 0f, off, 0f, 0f, k, 0f, off, 0f, 0f, 0f, 1f, 0f)))
            colorFilter = ColorMatrixColorFilter(g); alpha = (255 * .95f).toInt()
        } else { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY); alpha = (255 * .62f).toInt() }
    }
    private val contourI = c.contourI
    val c50 = stroke(c.contour, .55f); val c100 = stroke(c.contour, .7f); val c250 = stroke(contourI, .8f)
    val village = fill(c.village, .75f)
    val river = stroke(c.water, .9f)
    val water = fill(c.water); val waterEdge = stroke(c.waterEdge)
    val casing = stroke(c.casing, .85f)
    val roadMain = stroke(c.roadMain); val road = stroke(c.road)
    val runs = mapOf("green" to c.green, "blue" to c.blue, "red" to c.red, "black" to c.black).mapValues { stroke(it.value, .8f) }
    val lift = stroke(c.lift)
    val pinBody = fill(c.paper); val pinLine = stroke(c.ink); val pinOn = fill(Color(0xFFF4B942))
    val ring = stroke(Color(0xFFF4B942))
}

/**
 * The map of the meeting point (M1, MP1): the mountain from above, the runs and lifts, and a pin on every station.
 * A tap near a pin reports it (44 dp around the whole pin, as on the site), a tap elsewhere reports the empty map;
 * the screen decides (a second tap on the picked pin clears it). Dragging moves the map, two fingers zoom (a drag is
 * not a tap).
 */
@Composable
fun MeetMap(
    view: MeetView, plan: MeetPlan, lines: MeetLines, relief: Relief2D?, colors: SkiColors, selected: String?,
    label: String, onPin: (stationId: String) -> Unit, onEmpty: () -> Unit, modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val p = remember(colors, density) { Paints(colors, density) }
    // the picked pin's ring pulses (mmpulse); nothing moves, and nothing draws again, while none is picked
    val pulse = if (selected != null) rememberInfiniteTransition(label = "pin").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "ring") else null
    Canvas(
        modifier
            .semantics { contentDescription = label }
            .onSizeChanged {
                val first = !view.ready
                view.w = it.width.toFloat().coerceAtLeast(1f); view.h = it.height.toFloat().coerceAtLeast(1f)
                if (first && it.width > 0) view.ready = true
            }
            .pointerInput(plan, selected) {
                detectTapGestures { at ->
                    val u = density
                    // distance to the whole pin (its tip and its head), in dp
                    fun dpin(s: Station): Float {
                        val tx = view.sx(s.x); val ty = view.sy(s.y)
                        val hy = ty - 26f * u * (if (s.id == selected) 1.2f else .8f)
                        return min(hypot(tx - at.x, ty - at.y), hypot(tx - at.x, hy - at.y)) / u
                    }
                    val best = plan.stations.minByOrNull { dpin(it) } ?: return@detectTapGestures
                    if (dpin(best) <= 44f) onPin(best.id) else onEmpty()
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (zoom != 1f) view.zoomAt(null, 1f / zoom, view.mx(centroid.x), view.my(centroid.y), 0)
                    if (pan != Offset.Zero) view.pan(pan.x, pan.y)
                }
            },
    ) {
        drawRect(colors.snow)
        if (!view.ready) return@Canvas
        val k = view.k
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            n.save(); n.scale(k, k); n.translate(-view.x, -view.y)
            fun w(paint: Paint, px: Float) = paint.apply { strokeWidth = px * density / k }
            relief?.let { r ->
                r.hill?.let { n.drawBitmap(it, null, RectF(r.x0, r.y0, r.x1, r.y1), p.hill) }
                n.drawPath(r.c50, w(p.c50, .45f)); n.drawPath(r.c100, w(p.c100, .6f)); n.drawPath(r.c250, w(p.c250, 1f))
                n.drawPath(r.village, p.village)
                n.drawPath(r.rivers, w(p.river, 1.4f))
                n.drawPath(r.water, p.water); n.drawPath(r.water, w(p.waterEdge, .8f))
                for (cls in 4 downTo 0) n.drawPath(r.roads[cls], w(p.casing, when (cls) { 0 -> 5f; 3, 4 -> 2.4f; else -> 3.4f }))
                for (cls in 4 downTo 0) n.drawPath(r.roads[cls], w(if (cls == 0) p.roadMain else p.road, when (cls) { 0 -> 3f; 1, 2 -> 1.8f; else -> 1.1f }))
            }
            for ((color, path) in lines.runs) n.drawPath(path, w(p.runs[color] ?: p.runs.getValue("black"), 3f))
            n.drawPath(lines.lifts, w(p.lift, 1.6f))
            n.restore()

            // the pins, at a fixed size on the screen, the picked one last (on top) and bigger
            fun pin(s: Station, on: Boolean) {
                val sc = density * (if (on) 1.2f else .8f)
                n.save(); n.translate(view.sx(s.x), view.sy(s.y)); n.scale(sc, sc)
                if (on && pulse != null) {
                    val f = pulse.value
                    n.save(); n.scale(1f + f * .8f, 1f + f * .8f)
                    p.ring.alpha = (255 * (1 - f)).toInt(); n.drawOval(RectF(-11f, -5f, 11f, 5f), p.ring.apply { strokeWidth = 2.5f })
                    n.restore()
                }
                n.drawPath(PIN, if (on) p.pinOn else p.pinBody); n.drawPath(PIN, p.pinLine.apply { strokeWidth = 2.2f })
                n.drawCircle(0f, -28f, 5.5f, p.pinBody); n.drawCircle(0f, -28f, 5.5f, p.pinLine.apply { strokeWidth = 1.5f })
                n.restore()
            }
            for (s in plan.stations) if (s.id != selected) pin(s, false)
            plan.byId[selected]?.let { pin(it, true) }
        }
    }
}

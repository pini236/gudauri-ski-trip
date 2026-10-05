package io.github.pini236.skiapp.map

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.meet.MeetView
import io.github.pini236.skiapp.meet.Relief2D
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.ui.SkiColors
import java.nio.ByteBuffer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The map from above (PARITY A-30, P-D1), the site's top view (site/js/app.js draw and layoutLabels, relief.js svgRelief
 * and svgMarks): the shaded relief with the contours, the village, rivers, lakes and roads; every run (dashed for a ski
 * way or a section without a name) and lift with its stations; the names that fit; the Kobi side's signpost; and, as
 * the site's map, the chosen run painted from the top in slope colours over its ground, the profile's dot, and the lift
 * status: closed lifts and runs grey and dashed, and chairs moving on the open lifts (A-35).
 *
 * Here the whole mountain is one map, the Kobi side in its true place to the north: its signpost frames it, where the
 * site opens an inset (decision 35).
 */
class OverviewArt(val runs: Runs, terrain: Terrain) {
    /** A run: its lines as paths (made at the first drawing), and where its name goes. */
    class RunArt(val p: Piste, val label: FloatArray?) { val paths: List<Path> by lazy { p.lines.map { path(it) } } }
    class LiftArt(val l: Lift, val ends: FloatArray, val label: FloatArray?, val len: Float) { val path: Path by lazy { path(l.pts) } }
    /** A box in metres (x east, y south). */
    class Area(val left: Float, var top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
        fun centerY() = (top + bottom) / 2
    }

    val pistes: List<RunArt> = runs.pistes.map { p ->
        // the name halfway along its longest line, at a point of the line (the site's middle point)
        RunArt(p, if (!p.named) null else p.lines.maxByOrNull { it.size }?.let { l -> val m = l.size / 2 / 2; floatArrayOf(l[m * 2], l[m * 2 + 1]) })
    }
    val lifts: List<LiftArt> = runs.lifts.map { l ->
        val n = l.pts.size / 2
        var len = 0f
        for (i in 1 until n) len += hypot(l.pts[i * 2] - l.pts[i * 2 - 2], l.pts[i * 2 + 1] - l.pts[i * 2 - 1])
        LiftArt(l, floatArrayOf(l.pts[0], l.pts[1], l.pts[(n - 1) * 2], l.pts[(n - 1) * 2 + 1]),
            if (l.name.isBlank()) null else (n / 2).let { m -> floatArrayOf(l.pts[m * 2], l.pts[m * 2 + 1]) }, len)
    }
    /** Kobi Pass, the top of Firni: the first point of the Kobi run (the site's PASS). */
    val pass: FloatArray? = runs.pistes.firstOrNull { it.key == "Kobi" }?.lines?.firstOrNull()?.let { floatArrayOf(it[0], it[1]) }
    /** The main side's box, with room above for the peaks (the site's fit: 350 m more at the top). */
    val main: Area = box(runs.mainPistes.flatMap { it.lines } + runs.mainLifts.map { it.pts }).apply { top -= 350f }
    /** The Kobi side's box, with the pass. */
    val kobi: Area = box(runs.pistes.filter { it.lat > Runs.KOBI_LAT }.flatMap { it.lines } + runs.lifts.filter { it.lat > Runs.KOBI_LAT }.map { it.pts } + listOfNotNull(pass))
    val peaks = terrain.peaks.filter { !it.name.startsWith("Kobi Pass") }

    /** The run under a tap ([tol] metres), not one the filters hide; the shortest distance wins. */
    fun runAt(x: Float, y: Float, tol: Float, hidden: Set<String>): Piste? =
        runs.pistes.filter { it.key !in hidden }.mapNotNull { p -> p.lines.minOfOrNull { l -> dist(l, x, y) }?.let { p to it } }.filter { it.second <= tol }.minByOrNull { it.second }?.first

    fun liftAt(x: Float, y: Float, tol: Float): Lift? =
        runs.lifts.map { it to dist(it.pts, x, y) }.filter { it.second <= tol }.minByOrNull { it.second }?.first

    companion object {
        private fun box(lines: List<FloatArray>): Area {
            var a = Float.MAX_VALUE; var b = Float.MAX_VALUE; var c = -Float.MAX_VALUE; var d = -Float.MAX_VALUE
            for (l in lines) for (i in 0 until l.size / 2) { a = min(a, l[i * 2]); c = max(c, l[i * 2]); b = min(b, l[i * 2 + 1]); d = max(d, l[i * 2 + 1]) }
            return Area(a, b, c, d)
        }

        private fun path(l: FloatArray) = Path().also { p -> for (i in 0 until l.size / 2) if (i == 0) p.moveTo(l[0], l[1]) else p.lineTo(l[i * 2], l[i * 2 + 1]) }

        /** The distance from a point to a line of x,y pairs, in metres. */
        fun dist(l: FloatArray, x: Float, y: Float): Float {
            val n = l.size / 2
            if (n == 1) return hypot(l[0] - x, l[1] - y)
            var best = Float.MAX_VALUE
            for (i in 0 until n - 1) {
                val ax = l[i * 2]; val ay = l[i * 2 + 1]; val dx = l[i * 2 + 2] - ax; val dy = l[i * 2 + 3] - ay
                val len2 = dx * dx + dy * dy
                val f = if (len2 > 0) (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0f, 1f) else 0f
                best = min(best, hypot(ax + dx * f - x, ay + dy * f - y))
            }
            return best
        }

        /** The point a fraction [f] along a line of x,y pairs [len] metres long (a chair on its cable). */
        fun along(l: FloatArray, len: Float, f: Float, out: FloatArray) {
            var want = f * len; val n = l.size / 2
            for (i in 1 until n) {
                val seg = hypot(l[i * 2] - l[i * 2 - 2], l[i * 2 + 1] - l[i * 2 - 1])
                if (want <= seg || i == n - 1) { val t = if (seg > 0) (want / seg).coerceIn(0f, 1f) else 0f; out[0] = l[i * 2 - 2] + (l[i * 2] - l[i * 2 - 2]) * t; out[1] = l[i * 2 - 1] + (l[i * 2 + 1] - l[i * 2 - 1]) * t; return }
                want -= seg
            }
            out[0] = l[0]; out[1] = l[1]
        }
    }
}

/** The chosen run on the map from above: its ground in slope colours (X-3), and its lines in slope colours, top first. */
class OverviewPaint(val key: String, val ground: Bitmap?, val box: RectF?, val segs: FloatArray, val colors: IntArray, val at: FloatArray) {
    companion object {
        /** From the 3D scene's draped lines (top first) and their slopes, and its ground layer. Off the main thread. */
        fun of(scene: MapScene, p: Piste): OverviewPaint {
            val lines = scene.topDown(p)
            val layer = scene.slopeLayer(lines)
            // the layer's RGBA, premultiplied for a bitmap; at most 60% as on the site (opacity .6 at the line)
            val px = ByteArray(layer.rgba.size)
            for (i in 0 until layer.w * layer.h) {
                val a = (layer.rgba[i * 4 + 3].toInt() and 0xFF) * 6 / 10
                for (c in 0..2) px[i * 4 + c] = ((layer.rgba[i * 4 + c].toInt() and 0xFF) * a / 255).toByte()
                px[i * 4 + 3] = a.toByte()
            }
            val bmp = Bitmap.createBitmap(layer.w, layer.h, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(ByteBuffer.wrap(px)) }
            var total = 0f
            val lens = lines.map { l -> var a = 0f; for (i in 1 until l.size / 3) a += hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1]); total += a; a }
            val segs = ArrayList<Float>(); val cols = ArrayList<Int>(); val at = ArrayList<Float>()
            var before = 0f
            lines.forEachIndexed { li, l ->
                val s = scene.lineSlopes(l); var acc = 0f
                for (i in 0 until l.size / 3 - 1) {
                    segs += l[i * 3]; segs += l[i * 3 + 2]; segs += l[i * 3 + 3]; segs += l[i * 3 + 5]
                    val c = SlopeColors.of(s[i])
                    cols += android.graphics.Color.rgb((c[0] * 255).toInt(), (c[1] * 255).toInt(), (c[2] * 255).toInt())
                    at += (before + acc) / total.coerceAtLeast(1f)
                    acc += hypot(l[i * 3 + 3] - l[i * 3], l[i * 3 + 5] - l[i * 3 + 2])
                }
                before += lens[li]
            }
            return OverviewPaint(p.key, bmp, RectF(layer.x0, layer.z0, layer.x0 + layer.width, layer.z0 + layer.depth), segs.toFloatArray(), cols.toIntArray(), at.toFloatArray())
        }
    }
}

/** The words the map from above writes, in the app's language. */
class OverviewWords(val kobi: String, val gudauri: String, val peak: (String, Int) -> String, val run: (Piste) -> String, val label: String)

private class OverviewPaints(c: SkiColors, val d: Float, body: Typeface?) {
    fun stroke(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color.toArgb(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    fun fill(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color.toArgb() }
    val casing = stroke(c.casing)
    val runs = mapOf("green" to c.green, "blue" to c.blue, "red" to c.red, "black" to c.black)
    val run = stroke(c.green)
    val lift = stroke(c.lift); val station = fill(c.lift); val stationEdge = stroke(c.casing)
    val dash = c.dash.toArgb()
    val chair = fill(Color(0xFFF4B942)); val chairEdge = stroke(c.ink)
    val paintCasing = stroke(Color.White); val paintSeg = stroke(Color.White)
    val ground = Paint(Paint.FILTER_BITMAP_FLAG)
    val mark = fill(c.ink); val markEdge = stroke(Color.White)
    val peakMk = fill(c.peak)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(body, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    val halo = Paint(text).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = c.casing.toArgb() }
    val colors = c
    // the relief (meet/MeetMap.kt's), its widths set each frame
    fun relief(color: Color, a: Float) = stroke(color.copy(alpha = color.alpha * a))
    val c50 = relief(c.contour, .55f); val c100 = relief(c.contour, .7f); val c250 = relief(c.contourI, .8f)
    val village = fill(c.village.copy(alpha = c.village.alpha * .75f))
    val river = relief(c.water, .9f); val water = fill(c.water); val waterEdge = relief(c.waterEdge, 1f)
    val roadCasing = relief(c.casing, .85f); val roadMain = relief(c.roadMain, 1f); val road = relief(c.road, 1f)
    val hill = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        if (c.dark) { // grayscale, brightness .42, contrast 1.35: the site's night map
            val g = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            val kk = .42f * 1.35f; val off = (1 - 1.35f) * 127.5f
            g.postConcat(android.graphics.ColorMatrix(floatArrayOf(kk, 0f, 0f, 0f, off, 0f, kk, 0f, 0f, off, 0f, 0f, kk, 0f, off, 0f, 0f, 0f, 1f, 0f)))
            colorFilter = android.graphics.ColorMatrixColorFilter(g); alpha = (255 * .95f).toInt()
        } else { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY); alpha = (255 * .62f).toInt() }
    }
    val tri = Path()
    val rect = RectF()
}

/** A name on the map: where (metres), what, its size and colour, and what a tap on it does (the Kobi signpost). */
private class Lbl(val x: Float, val y: Float, val text: String, val size: Float, val color: Int, val dy: Float, val run: Piste? = null, val kobi: Boolean = false)

/**
 * The map from above. [view] is its window (the meeting point's, in metres); [paint] the chosen run's; [marker] the
 * profile's dot; [status] the lift report, used only when fresh. A tap on a name, a run or a lift reports it, elsewhere
 * the empty map; dragging moves the map and two fingers zoom.
 */
@Composable
fun Overview(
    view: MeetView, art: OverviewArt, relief: Relief2D?, colors: SkiColors, body: Typeface?, words: OverviewWords,
    selected: Piste?, paint: OverviewPaint?, hidden: Set<String>, hideLifts: Boolean, status: LiftStatus?, forMe: Boolean,
    marker: FloatArray?, still: Boolean,
    onRun: (Piste) -> Unit, onLift: (Lift) -> Unit, onEmpty: () -> Unit, onKobi: () -> Unit, modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val p = remember(colors, density, body) { OverviewPaints(colors, density, body) }
    // the chosen run is painted from its top down in 1.3 s, as on the site; at once with reduced motion
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(paint?.key) { if (paint != null && !still) { reveal.snapTo(0f); reveal.animateTo(1f, tween(1300, easing = LinearEasing)) } else reveal.snapTo(1f) }
    val fresh = status?.fresh == true
    val openLifts = if (!fresh || still) emptyList() else art.lifts.filter { !hideLifts && status!!.isOpen(it.l.name.ifBlank { null }) == true }
    // the chairs: three on each open lift, a lap in at least 8 s (the site's len/60); nothing moves without them
    val clock = if (openLifts.isNotEmpty()) rememberInfiniteTransition(label = "chairs").animateFloat(0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing), RepeatMode.Restart), label = "t") else null
    val names = remember(art, words, colors, relief) {
        buildList {
            art.pass?.let { add(Lbl(it[0], it[1], words.kobi, 13f, colors.glacier.toArgb(), -6f, kobi = true)) }
            for (pk in art.peaks) add(Lbl(pk.x, pk.y, words.peak(pk.name, pk.ele), 12f, colors.peak.toArgb(), -11f))
            for (r in art.pistes) r.label?.let { add(Lbl(it[0], it[1], words.run(r.p), 13f, colors.run(r.p.color).toArgb(), -6f, run = r.p)) }
            for (l in art.lifts) l.label?.let { add(Lbl(it[0], it[1], "⇡ " + l.l.name, 11.5f, colors.lift.toArgb(), -6f)) }
            relief?.places?.filter { it.first == "Gudauri" || it.first == "Kobi" }?.forEach { (n, x, y) ->
                add(Lbl(x, y, if (n == "Gudauri") words.gudauri else n, 13f, colors.muted.toArgb(), -6f))
            }
        }
    }
    val shown = remember { ArrayList<Pair<Lbl, RectF>>() }
    val pos = remember { FloatArray(2) }
    Canvas(
        modifier
            .semantics { contentDescription = words.label }
            .onSizeChanged {
                val first = !view.ready
                view.w = it.width.toFloat().coerceAtLeast(1f); view.h = it.height.toFloat().coerceAtLeast(1f)
                if (first && it.width > 0) view.ready = true
            }
            .pointerInput(art, hidden, hideLifts) {
                detectTapGestures { at ->
                    // a name first (the Kobi signpost, a run's name), then a run's line, then a lift's, as the site's hit areas
                    synchronized(shown) { shown.firstOrNull { (l, r) -> (l.kobi || l.run != null) && r.contains(at.x, at.y) }?.first }?.let { l ->
                        if (l.kobi) onKobi() else l.run?.let(onRun); return@detectTapGestures
                    }
                    val mx = view.mx(at.x); val my = view.my(at.y); val m = 1f / view.k
                    art.runAt(mx, my, 8f * density * m, hidden)?.let { onRun(it); return@detectTapGestures }
                    if (!hideLifts) art.liftAt(mx, my, 7f * density * m)?.let { onLift(it); return@detectTapGestures }
                    onEmpty()
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
        val u = 1f / k // metres per pixel
        val far = u / density > 9f // the site hides the thinnest contours and tracks when far out
        val t = clock?.value
        // the dashes in screen pixels, this zoom's (the site's non-scaling strokes)
        fun dashes(on: Float, off: Float) = DashPathEffect(floatArrayOf(on * density * u, off * density * u), 0f)
        val dShut = dashes(6f, 5f); val dUnnamed = dashes(5f, 4f); val dWay = dashes(8f, 5f); val dClosed = dashes(4f, 4f); val dIdle = dashes(2f, 3f)
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            n.save(); n.scale(k, k); n.translate(-view.x, -view.y)
            fun w(paint: Paint, px: Float) = paint.apply { strokeWidth = px * density * u }
            relief?.let { r -> drawRelief(n, r, p, density, u, far) }
            paint?.let { pt -> pt.ground?.let { b -> pt.box?.let { n.drawBitmap(b, null, it, p.ground.apply { alpha = (255 * reveal.value * 1.6f).toInt().coerceIn(0, 255) }) } } }

            // the runs: the casing, then the colour; a ski way dashed 8 5, a section without a name thinner and dashed 5 4
            val sel = selected?.key
            for (r in art.pistes) {
                val q = r.p
                if (q.key in hidden) continue
                val shut = fresh && status!!.runOpen(q) == false
                val alpha = when { sel != null && q.key != sel -> .28f; shut && forMe -> .15f; else -> 1f }
                val cas = w(p.casing, if (q.named) 6.5f else 4.5f).apply { this.alpha = (255 * alpha).toInt(); pathEffect = null }
                val col = w(p.run, if (q.named) (if (q.kind == "ski-way") 2.6f else 3.4f) else 2f).apply {
                    color = if (shut) p.dash else (p.runs[q.color] ?: colors.black).toArgb(); this.alpha = (255 * alpha).toInt()
                    pathEffect = when { shut -> dShut; !q.named -> dUnnamed; q.kind == "ski-way" -> dWay; else -> null }
                }
                for (path in r.paths) { n.drawPath(path, cas); n.drawPath(path, col) }
            }
            // the chosen run, from the top down in slope colours over a white casing
            paint?.let { pt ->
                val shownTo = reveal.value
                val cas = w(p.paintCasing, 10f)
                val seg = w(p.paintSeg, 6f)
                for (i in pt.colors.indices) {
                    if (pt.at[i] > shownTo) break
                    n.drawLine(pt.segs[i * 4], pt.segs[i * 4 + 1], pt.segs[i * 4 + 2], pt.segs[i * 4 + 3], cas)
                }
                for (i in pt.colors.indices) {
                    if (pt.at[i] > shownTo) break
                    n.drawLine(pt.segs[i * 4], pt.segs[i * 4 + 1], pt.segs[i * 4 + 2], pt.segs[i * 4 + 3], seg.apply { color = pt.colors[i] })
                }
            }
            // the lifts: casing, the line (a gondola thicker; one out of use dashed 2 3; a closed one grey, dashed 4 4), stations
            if (!hideLifts) for (l in art.lifts) {
                val closed = fresh && status!!.isOpen(l.l.name.ifBlank { null }) == false
                n.drawPath(l.path, w(p.casing, 4.5f).apply { alpha = 255; pathEffect = null })
                n.drawPath(l.path, w(p.lift, if (l.l.kind == "gondola") 2.4f else 1.6f).apply {
                    color = if (closed) p.dash else colors.lift.toArgb()
                    pathEffect = when { closed -> dClosed; l.l.status == "inactive" -> dIdle; else -> null }
                })
                for (e in 0..1) {
                    p.station.color = if (closed) p.dash else colors.lift.toArgb()
                    n.drawCircle(l.ends[e * 2], l.ends[e * 2 + 1], 3.2f * density * u, p.station)
                    n.drawCircle(l.ends[e * 2], l.ends[e * 2 + 1], 3.2f * density * u, w(p.stationEdge, 1.5f))
                }
            }
            // the chairs on the open lifts
            if (t != null) for (l in openLifts) {
                val dur = max(8f, (if (l.l.len > 0) l.l.len.toFloat() else l.len) / 60f)
                for (c in 0..2) {
                    OverviewArt.along(l.l.pts, l.len, ((t / dur) + c / 3f) % 1f, pos)
                    n.drawCircle(pos[0], pos[1], 3.6f * density * u, p.chair)
                    n.drawCircle(pos[0], pos[1], 3.6f * density * u, w(p.chairEdge, 1f))
                }
            }
            // the peaks' marks
            for (pk in art.peaks) {
                val s = density * u
                p.tri.apply { reset(); moveTo(pk.x, pk.y - 8 * s); lineTo(pk.x + 5.5f * s, pk.y + 1.5f * s); lineTo(pk.x - 5.5f * s, pk.y + 1.5f * s); close() }
                n.drawPath(p.tri, p.peakMk); n.drawPath(p.tri, w(p.stationEdge, 1.2f))
            }
            marker?.let { m ->
                n.drawCircle(m[0], m[1], 7f * density * u, p.mark); n.drawCircle(m[0], m[1], 7f * density * u, w(p.markEdge, 3f))
            }
            n.restore()

            // the names that fit, in screen pixels: the chosen run's first, then in the site's order; one that would
            // cover another already placed is left out
            val list = ArrayList<Pair<Lbl, RectF>>()
            val order = if (sel == null) names else names.sortedByDescending { it.run?.key == sel }
            val pad = 2f * density
            for (l in order) {
                if (l.run != null && l.run.key in hidden) continue
                if (hideLifts && l.text.startsWith("⇡")) continue
                val sx = view.sx(l.x); val sy = view.sy(l.y) + l.dy * density
                if (sx < -200 || sy < -50 || sx > size.width + 200 || sy > size.height + 50) continue
                p.text.textSize = l.size * density; val wpx = p.text.measureText(l.text)
                val r = RectF(sx - wpx / 2 - pad, sy - l.size * density - pad, sx + wpx / 2 + pad, sy + l.size * density * .25f + pad)
                if (list.any { RectF.intersects(it.second, r) }) continue
                list += l to r
                val dim = sel != null && l.run != null && l.run.key != sel
                p.halo.textSize = l.size * density; p.halo.strokeWidth = 3.2f * density; p.halo.alpha = if (dim) 72 else 255
                p.text.color = l.color; p.text.alpha = if (dim) 72 else 255
                n.drawText(l.text, sx, sy, p.halo); n.drawText(l.text, sx, sy, p.text)
            }
            synchronized(shown) { shown.clear(); shown.addAll(list) }
        }
    }
}

/** The relief under the map (the meeting point's, meet/MeetMap.kt), with the site's "far" thinning. */
private fun drawRelief(n: android.graphics.Canvas, r: Relief2D, p: OverviewPaints, density: Float, u: Float, far: Boolean) {
    fun w(paint: Paint, px: Float) = paint.apply { strokeWidth = px * density * u }
    r.hill?.let { p.rect.set(r.x0, r.y0, r.x1, r.y1); n.drawBitmap(it, null, p.rect, p.hill) }
    if (!far) n.drawPath(r.c50, w(p.c50, .45f))
    n.drawPath(r.c100, w(p.c100, .6f)); n.drawPath(r.c250, w(p.c250, 1f))
    n.drawPath(r.village, p.village)
    n.drawPath(r.rivers, w(p.river, 1.4f))
    n.drawPath(r.water, p.water); n.drawPath(r.water, w(p.waterEdge, .8f))
    val top = if (far) 3 else 4
    for (cls in top downTo 0) n.drawPath(r.roads[cls], w(p.roadCasing, when (cls) { 0 -> 5f; 3, 4 -> 2.4f; else -> 3.4f }))
    for (cls in top downTo 0) n.drawPath(r.roads[cls], w(if (cls == 0) p.roadMain else p.road, when (cls) { 0 -> 3f; 1, 2 -> 1.8f; else -> 1.1f }))
}

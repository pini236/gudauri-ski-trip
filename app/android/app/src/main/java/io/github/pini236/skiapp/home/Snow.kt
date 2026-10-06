package io.github.pini236.skiapp.home

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import io.github.pini236.skiapp.ui.SkiColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Snow on the signs and cards, drawn as the site's js/snow.js draws it (round 18): a cap to the element's real width,
 * resting on its top edge from end to end and over its top strip, stopping where a sign's arrow begins. Three kinds:
 * [DRIFT] (the game signs), [LOW] (the narrow lift signs), and [PILE] (the white cards with a coloured top strip: piles
 * taller toward the middle, no outline, a soft shadow). The colours are the --sn* tokens, moonlit at night. The seed
 * keeps each sign's own drift, the same numbers as on the site.
 */
enum class SnowKind { DRIFT, LOW, PILE }

/** A cap, ready to draw: built once for a width (in drawWithCache), drawn every frame without allocating. */
class SnowCap internal constructor(
    internal val body: Path, internal val edge: Path?, internal val drips: Path,
    internal val pile: Boolean, internal val top: Float, internal val height: Float, private val d: Float,
) {
    /** The soft shadow under a pile (the site's feGaussianBlur 1.2): a blurred paint, where the phone can (API 28+). */
    internal val blur: Paint? = if (pile && Build.VERSION.SDK_INT >= 28) Paint().also {
        it.asFrameworkPaint().apply { isAntiAlias = true; maskFilter = BlurMaskFilter(2f * d, BlurMaskFilter.Blur.NORMAL) }
    } else null
}

/**
 * The cap for an element [w] pixels wide ([d] = density): [border] is its top strip in dp (6 on the cards), [arrow] the
 * width of a sign's arrow in dp, on the start side in a right-to-left language ([rtl]) and on the end side otherwise.
 */
fun snowCap(seed: Int, w: Float, d: Float, kind: SnowKind = SnowKind.DRIFT, border: Float = 0f, arrow: Float = 0f, rtl: Boolean = true): SnowCap {
    // the site's numbers are CSS pixels: work in dp, then scale
    val wd = w / d
    val pile = kind == SnowKind.PILE
    val t = if (pile) 34f else 16f
    val b = t + border
    var r = ((seed + 1) * 9301.0) % 233280.0
    fun rnd(a: Double, z: Double): Float { r = (r * 9301 + 49297) % 233280; return (a + (z - a) * r / 233280).toFloat() }
    fun rnd(a: Float, z: Float) = rnd(a.toDouble(), z.toDouble())
    var x0 = -3f; var x1 = wd + 3
    val bt = b > t
    if (arrow > 0) { if (rtl) x0 = arrow - 5 else x1 = wd - arrow + 5 }
    // the ends roll over the edge of the sign, down past its top border
    val end = b + if (bt) 1f else 4f
    val up = arrayListOf(Offset(x0, end), Offset(x0 + 3, t - 2))
    var pk = true
    if (pile) {
        // piles, taller toward the middle, with low dips between them
        var x = x0 + rnd(12f, 18f)
        while (x < x1 - 20) {
            val mid = 1 - abs((x - x0) / (x1 - x0) - .5f) * 1.1f
            up += if (pk) Offset(x, t - 8 - mid * rnd(10f, 20f)) else Offset(x, t - rnd(1f, 6f))
            x += if (pk) rnd(30f, 48f) else rnd(22f, 34f); pk = !pk
        }
    } else {
        // drifts of different sizes with flatter stretches between them
        val hi = if (kind == SnowKind.LOW) 10f else 14f
        var x = x0 + rnd(14f, 24f)
        while (x < x1 - 18) {
            up += if (pk) Offset(x, t - (if (rnd(0f, 1f) < .45f) rnd(hi * .6f, hi) else rnd(2.5f, hi * .45f))) else Offset(x, t - rnd(1f, 2.5f))
            x += if (pk) rnd(18f, 34f) else rnd(16f, 30f); pk = !pk
        }
    }
    up += Offset(x1 - 3, t - 2); up += Offset(x1, end)
    // the underside: a wavy lip a few pixels over the face, always below the top border
    val lip = if (pile) 4f to 8f else if (bt) 2f to 4.5f else 3f to 6.5f
    val lo = ArrayList<Offset>()
    var xl = x1 - 5
    while (xl > x0 + 8) { lo += Offset(xl, b + rnd(lip.first, lip.second)); xl -= rnd(22f, 40f) }
    lo += Offset(x0 + 3, b + if (bt) 2f else 5f)
    val body = curve(up + lo + up[0], d).apply { close() }
    val drips = Path()
    val n = rnd(1f, 3.4f).roundToInt()
    val at = ArrayList<Float>()
    for (i in 0 until n) {
        val dx = rnd(x0 + 26, x1 - 26); val dl = rnd(5f, 9.5f) * (if (pile) 1.25f else 1f); val dw = rnd(2.4f, 3.6f) * (if (pile) 1.35f else 1f); val y0 = b + 2
        // on a pile, no two drips side by side
        if (pile && at.any { abs(it - dx) < 24 }) continue
        at += dx
        drips.moveTo((dx - dw) * d, y0 * d)
        drips.cubicTo((dx - dw) * d, (y0 + dl * .7f) * d, (dx - dw * .4f) * d, (y0 + dl) * d, dx * d, (y0 + dl) * d)
        drips.cubicTo((dx + dw * .4f) * d, (y0 + dl) * d, (dx + dw) * d, (y0 + dl * .7f) * d, (dx + dw) * d, y0 * d)
        drips.close()
    }
    return SnowCap(body, if (pile) null else curve(up, d), drips, pile, t * d, (b + if (pile) 24f else 16f) * d, d)
}

/** A smooth line through the points (Catmull-Rom as cubic curves), as the site's curve(). */
private fun curve(p: List<Offset>, d: Float) = Path().apply {
    moveTo(p[0].x * d, p[0].y * d)
    for (k in 0 until p.size - 1) {
        val a = p[max(k - 1, 0)]; val b = p[k]; val c = p[k + 1]; val e = p[min(k + 2, p.size - 1)]
        cubicTo((b.x + (c.x - a.x) / 6) * d, (b.y + (c.y - a.y) / 6) * d, (c.x - (e.x - b.x) / 6) * d, (c.y - (e.y - b.y) / 6) * d, c.x * d, c.y * d)
    }
}

/** The colours of a cap: the snow's gradient, its drips, outline and shadow (the --sn* tokens). Built with the cap. */
class SnowPaint(c: SkiColors, cap: SnowCap) {
    internal val fill = Brush.verticalGradient(0f to c.sn1, (if (cap.pile) .7f else .55f) to c.sn2, 1f to c.sn3, startY = 0f, endY = cap.height)
    internal val drips = c.sn3
    internal val edge = c.snEdge
    internal val shadow = c.snShadow
}

/**
 * The cap on the element's top edge (its y = 0): the shadow it casts (soft under a pile), the drips under its lip, the
 * snow, and its outline (light, by day only; none on a pile).
 */
fun DrawScope.drawSnow(s: SnowCap, p: SnowPaint) {
    translate(0f, -s.top) {
        val dy = (if (s.pile) 2.2f else 1.6f) * density
        translate(0f, dy) {
            val blur = s.blur
            if (blur != null) {
                blur.color = p.shadow
                drawIntoCanvas { it.drawPath(s.body, blur); it.drawPath(s.drips, blur) }
            } else { drawPath(s.body, p.shadow); drawPath(s.drips, p.shadow) }
        }
        drawPath(s.drips, p.drips)
        drawPath(s.body, p.fill)
        if (s.edge != null && p.edge.alpha > 0f) drawPath(s.edge, p.edge, style = Stroke(1f * density))
    }
}

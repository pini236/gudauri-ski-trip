package io.github.pini236.skiapp.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.pini236.skiapp.ui.Ski
import kotlin.math.abs

/**
 * A trail sign: a board with an arrow pointing on, in the reading direction (left in Hebrew, right in English,
 * LT1 to LT3 of round 10). The arrow is on the end side of the layout, so the same code flips with the language.
 */
class SignShape(private val notch: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = notch * density.density
        val p = Path()
        if (layoutDirection == LayoutDirection.Rtl) {
            p.moveTo(0f, size.height / 2); p.lineTo(n, 0f); p.lineTo(size.width, 0f); p.lineTo(size.width, size.height); p.lineTo(n, size.height)
        } else {
            p.moveTo(0f, 0f); p.lineTo(size.width - n, 0f); p.lineTo(size.width, size.height / 2); p.lineTo(size.width - n, size.height); p.lineTo(0f, size.height)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** One sign on the post (site: .board). [widthFraction] staggers the signs as on the home page. */
@Composable
fun TrailSign(title: String, sub: String, color: Color, onColor: Color, widthFraction: Float, onClick: () -> Unit) {
    val shape = SignShape(26f)
    Box(
        Modifier.fillMaxWidth(widthFraction).heightIn(min = 72.dp)
            .shadow(6.dp, shape, clip = false)
            .clip(shape).background(color)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, end = 40.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column {
            Text(title, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * 0.9f), color = onColor)
            Text(sub, style = Ski.type.small, color = onColor)
        }
    }
}

/**
 * Fresh snow lying on a top edge, with a few drips: the same drift as the site's snowCap() (site/js/app.js), for
 * the season board and the snowy signs. [seed] picks the lumps; [height] is the drawing's height in dp.
 */
@Composable
fun SnowCap(seed: Int, modifier: Modifier = Modifier) {
    val shade = Color(0x40000000)
    Canvas(modifier) {
        val w = size.width; val top = size.height * 0.46f
        var r = ((seed + 1) * 9301) % 233280
        fun rnd(a: Float, b: Float): Float { r = (r * 9301 + 49297) % 233280; return a + (b - a) * r / 233280f }
        val u = size.height / 56f
        val x0 = 6 * u; val x1 = w - 6 * u
        val up = mutableListOf(Offset(x0 - 4 * u, top + 6 * u), Offset(x0 + 4 * u, top - 2 * u))
        var x = x0 + 14 * u; var peak = true
        while (x < x1 - 20 * u) {
            val mid = 1 - abs((x - x0) / (x1 - x0) - .5f) * 1.1f
            up += if (peak) Offset(x, top - (8 + mid * rnd(10f, 20f)) * u) else Offset(x, top - rnd(1f, 6f) * u)
            x += (if (peak) rnd(30f, 48f) else rnd(22f, 34f)) * u
            peak = !peak
        }
        up += Offset(x1 - 6 * u, top - 3 * u); up += Offset(x1 + 2 * u, top + 5 * u)
        val lo = mutableListOf<Offset>(); x = x1 - 2 * u
        while (x > x0 + 8 * u) { lo += Offset(x, top + rnd(8f, 13f) * u); x -= rnd(26f, 44f) * u }
        lo += Offset(x0 + 2 * u, top + 10 * u)
        val pts = up + lo + up[0]
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (k in 0 until pts.size - 1) {
                val p0 = pts[maxOf(k - 1, 0)]; val p1 = pts[k]; val p2 = pts[k + 1]; val p3 = pts[minOf(k + 2, pts.size - 1)]
                cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
            }
            close()
            repeat(2) {
                val dx = rnd(x0 + 40 * u, x1 - 40 * u); val dl = rnd(7f, 13f) * u; val dw = rnd(4f, 6f) * u; val y0 = top + 8 * u
                moveTo(dx - dw, y0)
                cubicTo(dx - dw, y0 + dl * .6f, dx - dw / 2, y0 + dl, dx, y0 + dl)
                cubicTo(dx + dw / 2, y0 + dl, dx + dw, y0 + dl * .6f, dx + dw, y0)
                close()
            }
        }
        translate(0f, 2.5f * u) { drawPath(path, shade) }
        drawPath(path, Color.White)
    }
}

/** The dark post the signs hang from, on the start side (right in Hebrew). */
@Composable
fun SignPost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ink = Ski.colors.ink
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - 20.dp.toPx() - 6.dp.toPx() else 20.dp.toPx()
            drawRect(ink, Offset(x, -8.dp.toPx()), Size(6.dp.toPx(), size.height + 400.dp.toPx()))
        }
        Column(Modifier.fillMaxWidth().padding(start = 30.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) { content() }
    }
}

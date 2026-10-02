package io.github.pini236.skiapp.home

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random

/**
 * A part of the boarding pass with round bites out of the two corners on the perforation (the site's .nl/.nr masks):
 * the main part has them at its end side, the stub at its start side, so the two meet in half-circles. In Hebrew the
 * stub is on the left, in English on the right (LT1); the shape follows the layout direction.
 */
class PassShape(private val seamAtEnd: Boolean, private val r: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rr = with(density) { r.toPx() }
        val end = (layoutDirection == LayoutDirection.Ltr) == seamAtEnd
        val x = if (end) size.width else 0f
        val bites = Path().apply {
            addOval(Rect(x - rr, -rr, x + rr, rr))
            addOval(Rect(x - rr, size.height - rr, x + rr, size.height + rr))
        }
        val p = Path().apply { op(Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }, bites, PathOperation.Difference) }
        return Outline.Generic(p)
    }
}

/** The stub once torn: a ragged edge along the perforation, as on the site (.bp-stub.torn). */
class TornShape(private val seamAtStart: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val atRight = (layoutDirection == LayoutDirection.Rtl) == seamAtStart
        val teeth = 24
        val p = Path()
        if (atRight) {
            p.moveTo(0f, 0f)
            for (i in 0..teeth) p.lineTo(size.width * if (i % 2 == 0) .93f else 1f, size.height * i / teeth)
            p.lineTo(0f, size.height)
        } else {
            p.moveTo(size.width, 0f)
            for (i in 0..teeth) p.lineTo(size.width * if (i % 2 == 0) .07f else 0f, size.height * i / teeth)
            p.lineTo(size.width, size.height)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** The paper's grain (the site's --bp-grain): fine noise, dark specks by day and pale ones at night, tiled. */
private object Grain {
    private fun make(dark: Boolean): ShaderBrush {
        val n = 128
        val rnd = Random(7)
        val px = IntArray(n * n) {
            val a = (rnd.nextFloat() * rnd.nextFloat() * (if (dark) 30 else 40)).toInt()
            if (dark) (a shl 24) or 0xFFFFFF else (a shl 24) or 0x13233A
        }
        val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        return ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    val day by lazy { make(false) }
    val night by lazy { make(true) }
}

fun Modifier.paperGrain(dark: Boolean) = drawBehind { drawRect(if (dark) Grain.night else Grain.day) }

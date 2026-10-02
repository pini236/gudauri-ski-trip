package io.github.pini236.skiapp.home

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** The real view from the village (site/img/pano, design/round3/panorama.py), decoded off the main thread and kept for reuse. */
private object Pano {
    private val cache = LruCache<String, ImageBitmap>(4)

    fun load(context: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        return runCatching { context.assets.open("pano/$name.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
            .getOrNull()?.also { cache.put(name, it) }
    }

    // positions in the images, as fractions (design/round3/pano.json): the village of New Gudauri
    val village = mapOf(false to Offset(.4525f, .6926f), true to Offset(.4742f, .6926f))

    // window lights around the village, in dp from it (the site's 16 lights)
    val lights = listOf(-44 to 6, -35 to 2, -28 to 9, -19 to 4, -12 to 11, -5 to 1, 3 to 7, 9 to 13, 16 to 3, 24 to 10, 31 to 5, 39 to 12, -23 to 15, 0 to 16, 20 to 17, 46 to 8)

    // stars at fixed places, so the sky looks the same every night (percent of the sky)
    val stars = listOf(4 to 8, 11 to 19, 18 to 5, 25 to 14, 33 to 7, 40 to 21, 47 to 4, 55 to 16, 61 to 9, 68 to 24, 74 to 6, 81 to 15, 88 to 10, 94 to 22,
        7 to 30, 29 to 28, 51 to 31, 72 to 33, 90 to 29, 15 to 40, 38 to 38, 63 to 41, 84 to 37, 97 to 4)

    val moon = Path().apply { moveTo(17f, 3f); arcTo(androidx.compose.ui.geometry.Rect(3f, 3f, 31f, 31f), -90f, 180f, false); arcTo(androidx.compose.ui.geometry.Rect(9.5f, 3f, 24.5f, 31f), 90f, -180f, false); close() }
}

@Composable
private fun panoImage(name: String): ImageBitmap? {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, name) { value = withContext(Dispatchers.IO) { Pano.load(ctx, name) } }
    return img
}

/**
 * The sky and the mountains at the top of the home page, as the site does it (6.3, N1): the sky's colours, the glow of
 * the low sun, stars and the moon at night, and the view from the village cross-fading through the day, with the
 * village's windows lit after dark. Everything here is physical (left is west), so it does not mirror in Hebrew.
 * It fades into the page at the bottom.
 */
@Composable
fun Hero(frame: DayNight.Frame, height: Dp, modifier: Modifier = Modifier) {
    val page = Ski.colors.snow
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clipToBounds()
            .background(Brush.verticalGradient(0f to Color(0xFF000000 or frame.skyTop.toLong()), .75f to Color(0xFF000000 or frame.skyBottom.toLong()))),
    ) {
        val wide = maxWidth > 560.dp
        val iw = if (wide) 1440f else 390f
        val s = max(maxWidth.value / iw, maxHeight.value / 400f)
        val w = iw * s; val h = 400 * s
        val left = (maxWidth.value - w) / 2; val top = min(0f, maxHeight.value * .9f - .75f * h)

        Canvas(Modifier.fillMaxSize()) {
            // the glow of the low sun: east (right) in the morning, west in the evening
            if (frame.glow > 0.01f) {
                val c = Offset(size.width * if (frame.glowMorning) .96f else .04f, size.height * .4f)
                val glowColor = Color(0xFF000000 or frame.glowColor.toLong())
                scale(1f, 420f / 560f, c) {
                    drawCircle(Brush.radialGradient(listOf(glowColor, glowColor.copy(alpha = 0f)), c, 280.dp.toPx()), 280.dp.toPx(), c, alpha = frame.glow)
                }
            }
            if (frame.stars > 0.01f) for ((i, xy) in Pano.stars.withIndex()) {
                val r = if (i % 3 == 2) 1.5f.dp.toPx() else 1f.dp.toPx()
                drawCircle(Color.White, r, Offset(size.width * xy.first / 100f, size.height * xy.second / 100f), alpha = frame.stars)
            }
            if (frame.moon > 0.01f) translate(size.width * .30f, size.height * .19f) {
                scale(36.dp.toPx() / 34f, Offset.Zero) {
                    drawCircle(Color(0xFFF4F7FB), 16f, Offset(17f, 17f), alpha = frame.moon * .12f)
                    drawPath(Pano.moon, Color(0xFFF4F7FB), alpha = frame.moon)
                }
            }
        }

        // the mountains: a under b, b at opacity mix; a new pair fades in over the old one
        Box(Modifier.absoluteOffset(left.dp, top.dp).size(w.dp, h.dp)) {
            Crossfade(frame.imgA to frame.imgB, animationSpec = tween(900), label = "pano") { (a, b) ->
                Box(Modifier.fillMaxSize()) {
                    val prefix = if (wide) "pano-wide-" else "pano-"
                    panoImage(prefix + a)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    if (a != b && frame.mix > 0.01f) panoImage(prefix + b)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = frame.mix) }
                }
            }
            if (frame.lights > 0.01f) Canvas(Modifier.fillMaxSize()) {
                val v = Pano.village.getValue(wide)
                val base = Offset(size.width * v.x, size.height * v.y)
                for ((dx, dy) in Pano.lights) {
                    val p = base + Offset(dx.dp.toPx(), dy.dp.toPx())
                    drawCircle(Color(0xFFFFC46E), 3.dp.toPx(), p + Offset(1.dp.toPx(), 1.dp.toPx()), alpha = frame.lights * .35f)
                    drawRect(Color(0xFFFFD890), p, Size(2.dp.toPx(), 2.dp.toPx()), alpha = frame.lights)
                }
            }
        }

        // into the page
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp).background(Brush.verticalGradient(listOf(page.copy(alpha = 0f), page))))
    }
}

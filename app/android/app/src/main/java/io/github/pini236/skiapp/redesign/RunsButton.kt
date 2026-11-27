package io.github.pini236.skiapp.redesign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The "מסלולים" button of the bottom bar, version 4, option A "the window" (design/handoff/2026-11-27-redesign-run-button-v4):
 * a surface with the app's own mountain picture, in the light of the hour, cropped on the peak at the end edge and fading
 * toward the word; the word, the count, an up chevron. Identical at 28 and at 120 runs (only the count changes).
 */

/** The picture of one light for the button: the crop of the hero picture, and whether it is the night one (brightness 1.7). */
internal class RunsPic(val img: ImageBitmap, val night: Boolean)

/** The button's picture, crossfading 600 ms when the mountain's light changes (the same seven lights as the mountain). */
internal class RunsPicture {
    var cur by mutableStateOf<RunsPic?>(null)
    var prev by mutableStateOf<RunsPic?>(null)
    val fade = Animatable(1f)
}

/** What the button shows: the picture and the count of named runs. */
internal class RunsFace(val pic: RunsPicture, val count: Int)

/**
 * Where the button is, and where the shelf's panel is, in the window, for the opening's travelling surface; [p] is the
 * opening's progress, 0 shut and 1 open (it eases itself).
 */
internal class RunsFlight {
    var button by mutableStateOf<Rect?>(null)
    var panel by mutableStateOf<Rect?>(null)
    val p = Animatable(0f)
    companion object {
        val OPEN = CubicBezierEasing(.2f, .8f, .2f, 1f)
        val CLOSE = CubicBezierEasing(.3f, 0f, .2f, 1f)
    }
}

internal object RunsCrop {
    /** the hero picture is cropped 264 by 96 points around the peak, centre (128, 172) (end on the left) or (262, 176) (end on the right) */
    private const val W = 264f
    private const val H = 96f

    /** The crop's x, y, width and height in the picture of [fullW] by [fullH] pixels, whose view is [viewW] points wide. */
    fun rect(fullW: Int, fullH: Int, viewW: Float, endOnLeft: Boolean): IntArray {
        val k = fullW / viewW
        val rw = (W * k).roundToInt().coerceIn(1, fullW); val rh = (H * k).roundToInt().coerceIn(1, fullH)
        val cx = (if (endOnLeft) 128f else 262f) * k; val cy = (if (endOnLeft) 172f else 176f) * k
        return intArrayOf((cx - rw / 2f).roundToInt().coerceIn(0, fullW - rw), (cy - rh / 2f).roundToInt().coerceIn(0, fullH - rh), rw, rh)
    }

    /** The part of a picture [imgW] by [imgH] that covers a window of [aspect] (width over height), centred: x, y, width, height. */
    fun cover(imgW: Int, imgH: Int, aspect: Float): IntArray {
        var sw = imgW; var sh = (sw / aspect).roundToInt()
        if (sh > imgH) { sh = imgH; sw = (sh * aspect).roundToInt() }
        return intArrayOf((imgW - sw) / 2, (imgH - sh) / 2, sw, sh)
    }

    /**
     * Decodes [file] of the packaged pictures at half size and cuts the window's crop out of it. A thumbnail asset per light
     * (about 6 KB each, cut by the build) is for the architect to approve; until then the crop is cut from the hero picture
     * already in the package, off the main thread, and only the crop stays.
     */
    fun load(context: Context, file: String, viewW: Float, endOnLeft: Boolean): ImageBitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val full = runCatching { context.assets.open("redesign/$file").use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull() ?: return null
        val r = rect(full.width, full.height, viewW, endOnLeft)
        val crop = Bitmap.createBitmap(full, r[0], r[1], r[2], r[3])
        if (crop !== full) full.recycle()
        return crop.asImageBitmap()
    }
}

/** The picture for the light [light] of [d]; loaded off the main thread, the old one fading out in 600 ms (150 ms less in reduced motion: none). */
@Composable
internal fun rememberRunsPicture(d: PeakData?, light: String?, rtl: Boolean): RunsPicture {
    val context = LocalContext.current
    val st = remember { RunsPicture() }
    val reduced = Motion.reduced(context)
    LaunchedEffect(d, light, rtl) {
        val dd = d ?: return@LaunchedEffect
        val lt = light ?: return@LaunchedEffect
        val file = dd.hero.images[lt] ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) { RunsCrop.load(context, file, dd.hero.w, rtl) } ?: return@LaunchedEffect
        val old = st.cur
        st.prev = old
        st.cur = RunsPic(bmp, lt == "night")
        if (old != null) { st.fade.snapTo(0f); st.fade.animateTo(1f, tween(if (reduced) 1 else 600, easing = LinearEasing)) } else st.fade.snapTo(1f)
        st.prev = null
    }
    return st
}

private val NIGHT_FILTER = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    1.7f, 0f, 0f, 0f, 0f,
    0f, 1.7f, 0f, 0f, 0f,
    0f, 0f, 1.7f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f)))

/** One picture over the window, covering it, centred on the peak: the window widens or narrows and the crop follows. */
private fun DrawScope.drawRunsImage(p: RunsPic, alpha: Float = 1f) {
    val img = p.img
    val c = RunsCrop.cover(img.width, img.height, size.width / size.height)
    drawImage(img, srcOffset = IntOffset(c[0], c[1]), srcSize = IntSize(c[2], c[3]),
        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha, colorFilter = if (p.night) NIGHT_FILTER else null)
}

/** The fade toward the word: opaque to 46% of the window from its outer edge, gone at 96%. Never mirrors the picture. */
private fun fadeBrush(endOnLeft: Boolean): Brush =
    if (endOnLeft) Brush.horizontalGradient(0f to Color.Black, .46f to Color.Black, .96f to Color.Transparent, 1f to Color.Transparent)
    else Brush.horizontalGradient(0f to Color.Transparent, .04f to Color.Transparent, .54f to Color.Black, 1f to Color.Black)

/** The picture's window at the end edge. [scale]: the picture's zoom around the peak (the press, the opening's flight). */
@Composable
private fun RunsWindow(pic: RunsPicture, endOnLeft: Boolean, scale: () -> Float, modifier: Modifier) {
    val mask = remember(endOnLeft) { fadeBrush(endOnLeft) }
    Spacer(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
        .drawBehind {
            val s = scale()
            withTransform({ scale(s, s, pivot = center) }) {
                pic.prev?.let { drawRunsImage(it) }
                pic.cur?.let { drawRunsImage(it, pic.fade.value) }
            }
        })
}

private fun surfaceBrush(night: Boolean): Brush =
    Brush.verticalGradient(if (night) listOf(Color(0xFF1C2B47), Color(0xFF121C30)) else listOf(Color.White, Color(0xFFEEF4FA)))

/** The up chevron: a bare stroke with a soft halo, over the picture. */
@Composable
private fun RunsChevron(k: SheetInk, modifier: Modifier) {
    Canvas(modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply { moveTo(w * .12f, h * .66f); lineTo(w * .5f, h * .3f); lineTo(w * .88f, h * .66f) }
        drawPath(p, k.halo.copy(alpha = .6f), style = Stroke((2.4f + 4f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, k.ink, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The rim: a 1 hairline, a light edge at the top, a soft shade at the bottom. Drawn over the content, inside the shape. */
@Composable
private fun RunsRim(k: SheetInk, radius: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val r = CornerRadius(radius.dp.toPx())
        drawRoundRect(if (k.night) Color(0x1FEAF1F8) else Color(0x1A0E2233), cornerRadius = r, style = Stroke(1.dp.toPx()))
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (k.night) .18f else .9f), Color.Transparent), startY = 0f, endY = size.height * .35f),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x240A192D)), startY = size.height * .7f, endY = size.height),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), cornerRadius = r, style = Stroke(3.dp.toPx()))
    }
}

/**
 * The button, 48 high, filling the bar's middle (193 wide at 390). Narrow (the bar's room under 175: recording with the
 * clock, large text): the count drops first, the picture is 84 wide, the word is 16. Press: scale .965, the picture 1.1
 * around the peak and the window 150 wide. [hidden]: the opening's travelling surface is drawn in its place.
 */
@Composable
internal fun RunsButton(k: SheetInk, face: RunsFace, hidden: Boolean, flight: RunsFlight, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val word = stringResource(R.string.resort_pass_runs)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(90), label = "runs-press")
    val shape = RoundedCornerShape(24.dp)
    val a11y = word + ", " + face.count
    BoxWithConstraints(modifier.height(48.dp).hintTarget("find").onGloballyPositioned { if (flight.p.value <= 0.001f) flight.button = it.boundsInWindow() }
        .graphicsLayer { val s = 1f - .035f * press; scaleX = s; scaleY = s; alpha = if (hidden) 0f else 1f }
        .semantics(mergeDescendants = true) { contentDescription = a11y; role = Role.Button }
        .clickable(interactionSource = src, indication = null, enabled = !hidden, onClick = onClick)) {
        val narrow = maxWidth < 175.dp
        Box(Modifier.fillMaxSize().shadow(8.dp, shape, clip = false).clip(shape).background(surfaceBrush(k.night))) {
            RunsWindow(face.pic, rtl, { 1f + .1f * press }, Modifier.align(Alignment.CenterEnd).width((if (narrow) 84f else 132f + 18f * press).dp).fillMaxHeight())
            Row(Modifier.fillMaxSize().padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(word, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = (if (narrow) 16 else 17).sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Clip)
                if (!narrow) Text(face.count.toString(), Modifier.padding(start = 7.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = k.ink2,
                    maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
            }
            RunsChevron(k, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            RunsRim(k, 24f)
        }
    }
}

/**
 * The opening's travelling surface (the table in the handoff): the button's own surface moves and grows into the shelf's
 * panel (420 ms to open, 280 to close), its picture zooming 2.1 times around the peak and fading, its word fading, glass
 * fading in; the last quarter hands over to the panel itself. Drawn over the screen from the button's rectangle, so
 * it is the only copy of the button while it flies ([RunsFlight.p] between 0 and 1).
 */
@Composable
internal fun BoxScope.RunsGhost(k: SheetInk, face: RunsFace, flight: RunsFlight) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    // the window's origin on the screen: the ghost sits in a full-size box at the window's origin
    var org by remember { mutableStateOf(Offset.Zero) }
    val p = flight.p.value
    val from = flight.button ?: return
    val to = flight.panel ?: return
    if (p <= 0.001f) return
    val gone = ((p - .75f) / .25f).coerceIn(0f, 1f)
    if (gone >= 1f) return
    val cover = (p / .6f).coerceIn(0f, 1f)
    val l = from.left + (to.left - from.left) * p; val t = from.top + (to.top - from.top) * p
    val w = from.width + (to.width - from.width) * p; val h = from.height + (to.height - from.height) * p
    val shape = RoundedCornerShape((24f + (22f - 24f) * p).dp)
    Box(Modifier.fillMaxSize().onGloballyPositioned { org = it.boundsInWindow().topLeft }) {
        Box(Modifier.atScreen { IntOffset((l - org.x).roundToInt(), (t - org.y).roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .graphicsLayer { alpha = 1f - gone }.clip(shape)) {
            Box(Modifier.fillMaxSize().alpha(1f - cover).background(surfaceBrush(k.night)))
            Box(Modifier.fillMaxSize().alpha(cover).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, shape))
            RunsWindow(face.pic, rtl, { 1f + 1.1f * p }, Modifier.align(Alignment.CenterEnd).width(132.dp).fillMaxHeight().alpha(1f - cover))
            Row(Modifier.fillMaxSize().padding(start = 18.dp).alpha(1f - cover), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.resort_pass_runs), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

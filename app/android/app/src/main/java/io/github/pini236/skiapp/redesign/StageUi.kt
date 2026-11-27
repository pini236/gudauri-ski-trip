package io.github.pini236.skiapp.redesign

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The projection contract as the app packs it (site/data/peak.json, tools/build-app-data.py). */
internal fun PeakContract.Companion.load(context: Context): PeakContract =
    parse(context.assets.open("data/peak.json").bufferedReader().use { it.readText() })

/**
 * The live model's last frame as one bitmap the size of the screen (condition 3: the swap is a picture of the leaving
 * stage, never two engines at once). Null when it cannot be read (the view not laid out, or the copy failed).
 */
internal object StageSnap {
    suspend fun of(v: LiveView): ImageBitmap? {
        if (v.width == 0 || v.height == 0 || !v.isAttachedToWindow) return null
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val ok = suspendCancellableCoroutine { c ->
            runCatching { PixelCopy.request(v, b, { r -> if (c.isActive) c.resume(r == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper())) }
                .onFailure { if (c.isActive) c.resume(false) }
        }
        return if (ok) b.asImageBitmap() else { b.recycle(); null }
    }
}

private class StageInk(night: Boolean) {
    val bg = if (night) Color(0xEB0F1828) else Color(0xF2FFFFFF)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val line = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
}

/** Z03: turning or tilting is the live model's; one line under the pill, with "switch to live". Gone by itself after 6 s. */
@Composable
internal fun BoxScope.StageHint(night: Boolean, onGo: () -> Unit, onOff: () -> Unit) {
    val k = StageInk(night)
    LaunchedEffect(Unit) { delay(6000); onOff() }
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp).fillMaxWidth()
        .shadow(10.dp, shape, clip = false).clip(shape).background(k.bg).border(1.dp, k.line, shape)
        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(16.dp)) { cube(k.ink) }
        Text(stringResource(R.string.app_mode_live_hint), Modifier.weight(1f), fontFamily = Gudi, fontSize = 14.sp, color = k.ink, maxLines = 2)
        Box(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(k.ink).clickable(onClick = onGo).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.app_mode_live_go), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (night) Color(0xFF0F1828) else Color.White, maxLines = 1)
        }
    }
}

/** Z05: the live model for a moment; the tag under the pill at the screen's start, the switch in the column unmoved. */
@Composable
internal fun BoxScope.TempTag(night: Boolean) {
    val k = StageInk(night)
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 60.dp, end = 12.dp)
        .shadow(8.dp, shape, clip = false).clip(shape).background(k.bg).border(1.dp, k.line, shape).padding(horizontal = 12.dp, vertical = 7.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.size(14.dp)) { cube(k.ink) }
        Text(stringResource(R.string.app_mode_live_temp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = k.ink, maxLines = 1)
    }
}

private fun DrawScope.cube(c: Color) {
    val w = size.width; val h = size.height; val sw = 1.5.dp.toPx()
    val top = Offset(w * .5f, h * .08f); val l = Offset(w * .1f, h * .3f); val r = Offset(w * .9f, h * .3f); val m = Offset(w * .5f, h * .52f)
    val bl = Offset(w * .1f, h * .72f); val br = Offset(w * .9f, h * .72f); val b = Offset(w * .5f, h * .94f)
    drawPath(Path().apply { moveTo(top.x, top.y); lineTo(r.x, r.y); lineTo(br.x, br.y); lineTo(b.x, b.y); lineTo(bl.x, bl.y); lineTo(l.x, l.y); close() }, c, style = Stroke(sw, join = StrokeJoin.Round))
    drawPath(Path().apply { moveTo(l.x, l.y); lineTo(m.x, m.y); lineTo(r.x, r.y); moveTo(m.x, m.y); lineTo(b.x, b.y) }, c, style = Stroke(sw, join = StrokeJoin.Round))
}

/**
 * P.17 (KB1, 27.11.2026): the glass tag "צד Kobi" just above where the Kobi line peeks at the top of the front picture,
 * 36 high inside a touch area of 44. A tap turns the picture to the Kobi side (the stage's own turn, or the live model for
 * a moment while the side's pictures have not come). Follows the picture as it moves; never above the top pill's row.
 */
@Composable
internal fun BoxScope.KobiTag(picture: PictureStage, night: Boolean, onTap: () -> Unit) {
    val k = StageInk(night)
    var at by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(picture) { while (true) { withFrameNanos { }; val p = picture.kobiTag(); val n = p?.let { Offset(it[0], it[1]) }; if (n != at) at = n } }
    val a = at ?: return
    var tw by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val screenW = LocalConfiguration.current.screenWidthDp * density.density
    val topMin = WindowInsets.statusBars.getTop(density) + with(density) { 72.dp.toPx() }
    val label = stringResource(R.string.map_kobi_side)
    val aria = stringResource(R.string.map_kobi_side_aria)
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.atScreen { IntOffset((a.x - tw / 2f).coerceIn(8.dp.toPx(), (screenW - tw - 8.dp.toPx()).coerceAtLeast(8.dp.toPx())).toInt(), (a.y - 48.dp.toPx()).coerceAtLeast(topMin).toInt()) }
        .height(44.dp).widthIn(min = 44.dp).onSizeChanged { tw = it.width }
        .semantics { contentDescription = aria; role = Role.Button }.clickable(onClick = onTap), contentAlignment = Alignment.Center) {
        Row(Modifier.height(36.dp).shadow(8.dp, shape, clip = false).clip(shape).background(k.bg).border(1.dp, k.line, shape).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(16.dp)) { overRidge(k.ink) }
            Text(label, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.ink, maxLines = 1)
        }
    }
}

/** P.17: the Kobi side's picture has not come and the live model cannot run here: one line under the pill, gone by itself after 4 s. */
@Composable
internal fun BoxScope.KobiWait(night: Boolean, onOff: () -> Unit) {
    val k = StageInk(night)
    LaunchedEffect(Unit) { delay(4000); onOff() }
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp, start = 12.dp, end = 12.dp)
        .shadow(8.dp, shape, clip = false).clip(shape).background(k.bg).border(1.dp, k.line, shape).padding(horizontal = 14.dp, vertical = 9.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_kobi_side_wait), fontFamily = Gudi, fontSize = 14.sp, color = k.ink, maxLines = 2)
    }
}

/** A ridge with an arrow over it: turning to the other side of the mountain. */
private fun DrawScope.overRidge(c: Color) {
    val w = size.width; val h = size.height; val sw = 1.6.dp.toPx()
    drawPath(Path().apply { moveTo(w * .06f, h * .82f); lineTo(w * .34f, h * .42f); lineTo(w * .52f, h * .62f); lineTo(w * .64f, h * .5f); lineTo(w * .94f, h * .82f) }, c, style = Stroke(sw, join = StrokeJoin.Round, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    drawPath(Path().apply { moveTo(w * .3f, h * .2f); quadraticTo(w * .62f, h * .02f, w * .86f, h * .22f); moveTo(w * .86f, h * .22f); lineTo(w * .7f, h * .2f); moveTo(w * .86f, h * .22f); lineTo(w * .84f, h * .06f) }, c, style = Stroke(sw, join = StrokeJoin.Round, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

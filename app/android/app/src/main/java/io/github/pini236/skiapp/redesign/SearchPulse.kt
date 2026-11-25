package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Motion
import kotlin.math.min

/** Rules of the search screen and the speed ring (handoff 2026-11-25-redesign-search-pulse, version 3). Pure, so they are tested. */
internal object SearchRules {
    /** The hint line shows only after this long without a location. */
    const val HINT_AFTER_MS = 30_000L
    const val PERIOD_MS = 2400
    /** The ring's scale: 0 to 80 km/h; above it the ring is full. */
    const val RING_MAX_KMH = 80.0

    fun showHint(now: Long, began: Long): Boolean = began > 0L && now - began >= HINT_AFTER_MS

    /** The recording's clock: from the first reading of the location, never before it (0:00 on the live screen at that moment). */
    fun sinceFix(now: Long, firstFixAt: Long): Long = if (firstFixAt > 0L) (now - firstFixAt).coerceAtLeast(0L) else 0L

    /** The fill of the speed ring, 0..1. */
    fun fill(kmh: Double?): Float = if (kmh == null || kmh <= 0.0) 0f else min(1.0, kmh / RING_MAX_KMH).toFloat()

    /** Where a ring pulse is (0..1 of its cycle) for pulse [i] of three, started 800 ms apart, at [tMs]. */
    fun phase(tMs: Long, i: Int): Float = (((tMs - i * 800L) % PERIOD_MS + PERIOD_MS) % PERIOD_MS).toFloat() / PERIOD_MS

    /** Scale of a pulse: 0.22 to 1, on the handoff's ease-out curve (approximated). */
    fun scale(p: Float): Float = 0.22f + 0.78f * (1f - (1f - p) * (1f - p) * (1f - p))

    /** Opacity of a pulse: 0.95 down to 0.35 over 70 percent of the cycle, and to 0 at the end. */
    fun alpha(p: Float): Float = if (p < .7f) 0.95f - 0.6f * (p / .7f) else 0.35f * (1f - (p - .7f) / .3f)
}

/**
 * Finding the location (version 3): only the pulsing rings, the glass circle with the aim, "Finding your location", and
 * after 30 seconds the hint. No timer and no steps. Reduced motion: three fixed rings.
 */
@Composable
internal fun SearchPulse(night: Boolean, hintShown: Boolean, ink: Color, halo: Color, glass: Color, modifier: Modifier = Modifier) {
    val reduced = Motion.reduced(LocalContext.current)
    val ring = if (night) Color(0xE6B0CEFF) else Color(0xF2FFFFFF)
    val glow = if (night) Color(0x73789AFF) else Color(0x8CFFFFFF)
    val clock = rememberInfiniteTransition(label = "search")
    val t by clock.animateFloat(0f, SearchRules.PERIOD_MS.toFloat() * 3, infiniteRepeatable(tween(SearchRules.PERIOD_MS * 3, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val breathe by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(SearchRules.PERIOD_MS / 2, easing = LinearEasing), RepeatMode.Reverse), label = "breathe")
    val title = stringResource(R.string.app_search_title)
    val hint = stringResource(R.string.app_search_hint)
    Box(modifier.fillMaxSize().semantics { contentDescription = title + (if (hintShown) ". $hint" else "") }) {
        // the centre sits at 360 of 844 (about 43 percent of the height)
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val c = Offset(size.width / 2, size.height * 360f / 844f)
            val full = 210.dp.toPx()
            val pulses = if (reduced) listOf(.42f to .85f, .66f to .45f, .9f to .2f)
            else List(3) { i -> val p = SearchRules.phase(t.toLong(), i); SearchRules.scale(p) to SearchRules.alpha(p) }
            for ((s, a) in pulses) {
                drawCircle(glow.copy(alpha = glow.alpha * a), full * s + 6.dp.toPx(), c, style = Stroke(10.dp.toPx()))
                drawCircle(ring.copy(alpha = ring.alpha * a), full * s, c, style = Stroke(2.dp.toPx()))
            }
            val k = if (reduced) 1f else 1f + .06f * breathe
            drawCircle(glass, 46.dp.toPx() * k, c)
            val a = ink.copy(alpha = if (reduced) 1f else .55f + .45f * breathe)
            val r = 10.dp.toPx(); val w = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(a, r, c, style = w)
            for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
                drawLine(a, Offset(c.x + dx * (r - 3.dp.toPx()), c.y + dy * (r - 3.dp.toPx())), Offset(c.x + dx * (r + 8.dp.toPx()), c.y + dy * (r + 8.dp.toPx())), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        run {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * (360f / 844f) + 150.dp - 18.dp).widthIn(max = 320.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val shadow = androidx.compose.ui.graphics.Shadow(halo, Offset.Zero, 18f)
                    androidx.compose.material3.Text(title, style = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ink, shadow = shadow, textAlign = TextAlign.Center))
                    androidx.compose.material3.Text(hint, Modifier.padding(top = 8.dp).alpha(if (hintShown) 1f else 0f),
                        style = TextStyle(fontFamily = Gudi, fontSize = 15.sp, color = ink.copy(alpha = .8f), shadow = shadow, textAlign = TextAlign.Center))
                }
            }
        }
    }
}

/** The plain speed ring (GD1/GD2): a 270 degree arc from the bottom left, filled by the speed on a 0 to 80 scale, the day's best as a tick. */
@Composable
internal fun SpeedRing(kmhText: String, unit: String, kmh: Double?, dayBest: Double?, night: Boolean, ink: Color, secondary: Color, glass: Color, halo: Color, modifier: Modifier = Modifier) {
    val ramp = if (night) listOf(0xFF5B46A8, 0xFF8A6BE6, 0xFFB99CFF, 0xFFF3A6F0, 0xFFFFF2FA) else listOf(0xFFCFC2F4, 0xFFA98BEE, 0xFF7C4FE0, 0xFFB02FC4, 0xFFE0246E)
    val track = if (night) Color(0x29FFFFFF) else Color(0x8CFFFFFF)
    val fill = SearchRules.fill(kmh)
    val color = Color(ramp[min(4, (fill * 5).toInt())])
    Box(modifier.size(236.dp).semantics { contentDescription = "$kmhText $unit" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(236.dp).clearAndSetSemantics { }) {
            drawCircle(glass, 96.dp.toPx(), center)
            val rad = 104.dp.toPx(); val tl = Offset(center.x - rad, center.y - rad); val sz = Size(rad * 2, rad * 2)
            val st = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 135f, 270f, false, tl, sz, style = st)
            if (fill > 0f) drawArc(color, 135f, 270f * fill, false, tl, sz, style = st)
            dayBest?.let { b ->
                val ang = Math.toRadians(135.0 + 270.0 * SearchRules.fill(b))
                val r0 = rad - 9.dp.toPx(); val r1 = rad + 9.dp.toPx()
                drawLine(ink, Offset(center.x + (r0 * Math.cos(ang)).toFloat(), center.y + (r0 * Math.sin(ang)).toFloat()),
                    Offset(center.x + (r1 * Math.cos(ang)).toFloat(), center.y + (r1 * Math.sin(ang)).toFloat()), 4.dp.toPx())
            }
        }
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom) {
            val shadow = androidx.compose.ui.graphics.Shadow(halo, Offset.Zero, 12f)
            androidx.compose.material3.Text(kmhText, style = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 72.sp, color = ink, shadow = shadow), maxLines = 1)
            androidx.compose.material3.Text(unit, Modifier.padding(start = 4.dp, bottom = 12.dp), style = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = secondary))
        }
    }
}

package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.PI
import kotlin.math.sin

/**
 * The live screen while it looks for the first reading (handoff 2026-11-25-redesign-search-pulse, Pini's version 3:
 * boards GX00 and TM00): the mountain under a veil, three rings going out from a breathing glass circle with the aim,
 * "finding your location", and nothing else; no speed, no bars, no time. The line "outside, under an open sky..." comes
 * only after 30 seconds without a reading. The rings are hidden from the screen reader, which hears the title.
 */
@Composable
internal fun SearchPulse(night: Boolean, searchingFor: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember { Motion.reduced(context) }
    var t by remember { mutableLongStateOf(0L) }
    // the frame clock only while this is on the screen (a composable off the screen gets no frames)
    if (!still) LaunchedEffect(Unit) { val t0 = withFrameMillis { it }; while (true) withFrameMillis { t = it - t0 } }
    val veil = if (night) Color(0x94060B16) else Color(0x705C7294)
    val line = if (night) Color(0xE6B0CEFF) else Color(0xF2FFFFFF)
    val glow = if (night) Color(0x7378AAFF) else Color(0x8CFFFFFF)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val sub = if (night) Color(0xB3EAF0F7) else Color(0xFF40556A)
    val halo = if (night) Color(0xE6060B16) else Color(0xE6FFFFFF)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val title = stringResource(R.string.app_search_title)
    val hintOn = searchingFor >= SearchRules.HINT_AFTER_MS
    val hintA by animateFloatAsState(if (hintOn) 1f else 0f, tween(if (still) 0 else 300), label = "hint")
    BoxWithConstraints(modifier.fillMaxSize().background(veil).semantics { contentDescription = title }) {
        val cy = maxHeight * SearchRules.CENTRE_Y
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val c = Offset(size.width / 2, cy.toPx())
            val full = 210.dp.toPx()
            if (still) for ((s, a) in SearchRules.STILL) ring(c, full * s, a, line, glow)
            else for (k in 0 until 3) {
                val (s, a) = SearchRules.ringAt(t + k * SearchRules.STAGGER_MS)
                ring(c, full * s, a, line, glow)
            }
            // the glass circle, breathing, and the aim in it
            val breathe = if (still) 1f else SearchRules.breathe(t)
            val r = 46.dp.toPx() * breathe
            drawCircle(Color(0x33000000), r + 2.dp.toPx(), c.copy(y = c.y + 3.dp.toPx()))
            drawCircle(glass, r, c)
            val u = 40.dp.toPx() / 24f; val o = Offset(c.x - 12 * u, c.y - 12 * u)
            val aimA = if (still) 1f else SearchRules.aimAlpha(t)
            val aim = ink.copy(alpha = aimA); val w = Stroke(1.6f * u, cap = StrokeCap.Round)
            drawCircle(aim, 6.5f * u, Offset(o.x + 12 * u, o.y + 12 * u), style = w)
            drawCircle(aim, 1.6f * u, Offset(o.x + 12 * u, o.y + 12 * u))
            for ((x0, y0, x1, y1) in listOf(floatArrayOf(12f, 2.5f, 12f, 5.7f), floatArrayOf(12f, 18.3f, 12f, 21.5f),
                    floatArrayOf(2.5f, 12f, 5.7f, 12f), floatArrayOf(18.3f, 12f, 21.5f, 12f)))
                drawLine(aim, Offset(o.x + x0 * u, o.y + y0 * u), Offset(o.x + x1 * u, o.y + y1 * u), 1.6f * u, StrokeCap.Round)
        }
        Column(Modifier.fillMaxWidth().offset(y = cy + 128.dp).padding(horizontal = 32.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text(title, style = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ink,
                shadow = Shadow(halo, Offset.Zero, 18f)), textAlign = TextAlign.Center)
            androidx.compose.material3.Text(stringResource(R.string.app_search_hint), Modifier.padding(top = 6.dp).graphicsLayer { alpha = hintA },
                style = TextStyle(fontFamily = Gudi, fontSize = 15.sp, color = sub, shadow = Shadow(halo, Offset.Zero, 18f)), textAlign = TextAlign.Center)
        }
    }
}

private fun DrawScope.ring(c: Offset, r: Float, a: Float, line: Color, glow: Color) {
    if (a <= 0f) return
    // the glow of 24 on both sides, as two soft strokes under the line
    drawCircle(glow.copy(alpha = glow.alpha * a * .35f), r, c, style = Stroke(16.dp.toPx()))
    drawCircle(glow.copy(alpha = glow.alpha * a * .25f), r, c, style = Stroke(8.dp.toPx()))
    drawCircle(line.copy(alpha = line.alpha * a), r, c, style = Stroke(2.dp.toPx()))
}

/** The search's numbers (the handoff, section "the rings"), for the screen and its tests. */
internal object SearchRules {
    const val PERIOD_MS = 2400L
    const val STAGGER_MS = 800L
    const val HINT_AFTER_MS = 30_000L
    /** The centre at 360 of 844 (version 3: no rows above it). */
    const val CENTRE_Y = 360f / 844f
    /** Reduced motion: three fixed rings, their scale and opacity. */
    val STILL = listOf(.42f to .85f, .66f to .45f, .9f to .2f)
    private val EASE = CubicBezierEasing(.2f, .6f, .3f, 1f)

    /** A ring [t] ms into its loop: its scale (0.22 to 1) and opacity (0.95, 0.35 at 70 percent, 0 at the end). */
    fun ringAt(t: Long): Pair<Float, Float> {
        val f = (t.mod(PERIOD_MS)).toFloat() / PERIOD_MS
        val s = .22f + .78f * EASE.transform(f)
        val a = if (f < .7f) .95f + (.35f - .95f) * (f / .7f) else .35f * (1f - (f - .7f) / .3f)
        return s to a
    }

    /** The glass circle: 1 to 1.06 and back, on the same 2.4 s. */
    fun breathe(t: Long): Float = 1f + .03f * (1f - kotlin.math.cos(2 * PI * (t.mod(PERIOD_MS)) / PERIOD_MS).toFloat())

    /** The aim in it: 0.55 to 1 and back. */
    fun aimAlpha(t: Long): Float = .775f + .225f * sin(2 * PI * (t.mod(PERIOD_MS)) / PERIOD_MS - PI / 2).toFloat()
}

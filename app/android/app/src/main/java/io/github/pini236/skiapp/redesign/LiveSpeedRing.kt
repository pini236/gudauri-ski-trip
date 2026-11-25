package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.ui.Motion
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The speed in a descent (handoff 2026-11-25-redesign-search-pulse, "the simple ring"; Pini, 25.11.2026, boards GD1 and
 * GD2): a 270 degree ring on glass, filled from 0 to the speed on a 0 to 80 km/h scale in the light's colours, the
 * day's top speed a tick on it, the number large in the middle and the run under it. The screen reader hears the speed
 * and the run only when it is touched, never on every reading.
 */
@Composable
internal fun SpeedRing(kmh: Double?, dayMax: Double?, night: Boolean, glass: Color, ink: Color, ink2: Color, unit: String,
                       run: String, runRef: String?, runColor: Color?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember { Motion.reduced(context) }
    val k = (kmh ?: 0.0).toFloat().coerceAtLeast(0f)
    val shown by animateFloatAsState(k, tween(if (still) 0 else 300), label = "speed")
    val fill by animateFloatAsState(SpeedRingRules.fraction(k), tween(if (still) 0 else 300), label = "fill")
    val track = if (night) Color(0x29FFFFFF) else Color(0x8CFFFFFF)
    val stops = (if (night) SpeedRingRules.NIGHT else SpeedRingRules.DAY).map { Color(0xFF000000.toInt() or it) }
    val measurer = rememberTextMeasurer()
    val maxText = dayMax?.let { LiveNumbersText.whole(it) }
    val number = if (kmh == null) "—" else shown.roundToInt().toString()
    Box(modifier.size(236.dp).semantics { contentDescription = "$number $unit, $run" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(192.dp).shadow(14.dp, CircleShape).clip(CircleShape).background(glass))
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val r = 104.dp.toPx(); val w = 12.dp.toPx(); val c = center
            val tl = Offset(c.x - r, c.y - r); val box = Size(2 * r, 2 * r)
            drawArc(track, 135f, 270f, false, tl, box, style = Stroke(w, cap = StrokeCap.Round))
            if (fill > 0.001f) rotate(135f, c) {
                val brush = Brush.sweepGradient(*stops.mapIndexed { i, col -> (i / (stops.size - 1f)) * .75f to col }.toTypedArray(), center = c)
                drawArc(brush, 0f, 270f * fill, false, tl, box, style = Stroke(w, cap = StrokeCap.Round))
            }
            // the day's top: a tick across the ring, its number outside it
            if (dayMax != null && maxText != null) {
                val a = Math.toRadians((135.0 + 270.0 * SpeedRingRules.fraction(dayMax.toFloat())))
                val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
                drawLine(ink, Offset(c.x + ux * (r - 9.dp.toPx()), c.y + uy * (r - 9.dp.toPx())), Offset(c.x + ux * (r + 9.dp.toPx()), c.y + uy * (r + 9.dp.toPx())), 4.dp.toPx())
                val t = measurer.measure(maxText, TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = ink2))
                val o = r + 9.dp.toPx() + 16.dp.toPx()
                drawText(t, topLeft = Offset(c.x + ux * o - t.size.width / 2f, c.y + uy * o - t.size.height / 2f))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(number, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 92.sp, letterSpacing = (-.04).em, color = ink,
                    style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
                Text(unit, Modifier.padding(bottom = 16.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ink2)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (runRef != null && runColor != null) Box(Modifier.clip(RoundedCornerShape(5.dp)).background(runColor).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text(runRef, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
                Text(run, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink, maxLines = 1)
            }
        }
    }
}

/** The ring's scale and colours (the handoff's table), for the screen and its tests. */
internal object SpeedRingRules {
    const val FULL_KMH = 80f
    /** The light's colours, low to high (the day's lines; at night their night ramp). */
    val DAY = intArrayOf(0xCFC2F4, 0xA98BEE, 0x7C4FE0, 0xB02FC4, 0xE0246E)
    val NIGHT = intArrayOf(0x5B46A8, 0x8A6BE6, 0xB99CFF, 0xF3A6F0, 0xFFF2FA)
    /** How much of the ring is filled: 0 to 1 over 0 to 80 km/h, full above it. */
    fun fraction(kmh: Float): Float = (kmh / FULL_KMH).coerceIn(0f, 1f)
}

/** A km/h value as a whole number, for the day's top on the ring. */
internal object LiveNumbersText {
    fun whole(kmh: Double): String = Math.round(kmh).toString()
}

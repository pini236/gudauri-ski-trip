package io.github.pini236.skiapp.ticket

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Palette
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.random.Random

private const val HOLES = 11

/**
 * The boarding pass with its tear-off stub, and one snowy trail sign: the tests for touch, sound and
 * haptics landing at the same moment. Sample data only: the crew's own flight is never packed into the app.
 */
@Composable
fun TicketScreen(haptics: Haptics, sounds: Sounds) {
    val tm = rememberTextMeasurer()
    var tear by remember { mutableFloatStateOf(0f) } // 0..1 down the perforation
    var torn by remember { mutableIntStateOf(0) }
    var falling by remember { mutableStateOf(false) }
    var fall by remember { mutableStateOf(Triple(0f, 0f, 0f)) } // dy, rotation, alpha
    var frame by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val wobble = remember { Animatable(0f) }

    LaunchedEffect(falling) {
        if (!falling) return@LaunchedEffect
        var t = 0f; var last = 0L; var vy = 0f; var dy = 0f; var rot = 0f
        while (t < 1.6f) {
            androidx.compose.runtime.withFrameNanos { n ->
                val dt = if (last == 0L) 0.016f else (n - last) / 1e9f; last = n; t += dt
                vy += 2600f * dt; dy += vy * dt; rot += 70f * dt
                fall = Triple(dy, rot, (1f - (t - 0.9f) / 0.5f).coerceIn(0f, 1f)); frame++
            }
        }
        // the stub comes back for another go
        falling = false; tear = 0f; torn = 0; fall = Triple(0f, 0f, 1f)
        haptics.click(0.5f); sounds.play("ticket-land", 0.6f)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("כרטיס לדוגמה: גוררים את הספח למטה כדי לתלוש", fontFamily = Plex, fontSize = 14.sp, color = Palette.muted)
        Canvas(
            Modifier.fillMaxWidth().height(230.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (falling) return@detectDragGestures
                            if (tear > 0.55f) { tear = 1f; torn = HOLES; falling = true; haptics.click(1f) }
                            else scope.launch { // not far enough: the paper springs back
                                val a = Animatable(tear); a.animateTo(0f, spring(dampingRatio = 0.5f)) { tear = value; frame++ }; torn = 0
                            }
                        },
                    ) { change, drag ->
                        if (falling) return@detectDragGestures
                        val stubW = size.width * 0.30f
                        if (change.position.x > stubW * 1.4f && tear == 0f) return@detectDragGestures
                        change.consume()
                        val before = torn
                        tear = (tear + drag.y / size.height).coerceIn(0f, 1f)
                        torn = floor(tear * HOLES).toInt()
                        if (torn > before) {
                            if (before == 0) sounds.play("ticket-tear", 0.9f)
                            repeat(torn - before) { haptics.tick(0.35f + Random.nextFloat() * 0.3f) } // one bite per hole
                        }
                        if (tear >= 1f) { torn = HOLES; falling = true; haptics.click(1f) }
                        frame++
                    }
                },
        ) {
            frame
            drawTicket(tm, tear, torn, falling, fall)
        }
        Canvas(
            Modifier.fillMaxWidth().height(170.dp).pointerInput(Unit) {
                detectTapGestures { haptics.tick(0.6f); scope.launch { wobble.snapTo(6f); wobble.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow)) } }
            },
        ) { drawSnowSign(tm, wobble.value) }
        Text("צליל הקריעה: \"Perforated Tear\" מאת everythingsounds (Freesound), ברישיון CC BY 4.0. צלילי הכרטיס: Kenney (CC0).",
            fontFamily = Plex, fontSize = 11.sp, color = Palette.muted)
    }
}

private fun DrawScope.text(tm: TextMeasurer, s: String, at: Offset, sizeSp: Float, color: Color, display: Boolean = false, bold: Boolean = false) {
    val st = TextStyle(fontFamily = if (display) Karantina else Plex, fontWeight = if (display || bold) FontWeight.Bold else FontWeight.Normal, fontSize = sizeSp.sp, color = color)
    drawText(tm, s, at, st)
}

private fun DrawScope.drawTicket(tm: TextMeasurer, tear: Float, torn: Int, falling: Boolean, fall: Triple<Float, Float, Float>) {
    val w = size.width; val h = size.height
    val stubW = w * 0.30f
    val r = 6.dp.toPx()
    val paper = Palette.ticketPaper
    // main part (start side, on the right in Hebrew)
    rotate(-2f) {
        drawRoundRect(Color(0x2213233A), Offset(stubW + 3, 6f), Size(w - stubW, h), CornerRadius(r))
        drawRoundRect(paper, Offset(stubW, 0f), Size(w - stubW, h), CornerRadius(r))
        drawRect(Palette.glacier, Offset(stubW, 0f), Size(w - stubW, 34.dp.toPx()))
        text(tm, "כרטיס עלייה למטוס · דוגמה", Offset(stubW + 14.dp.toPx(), 7.dp.toPx()), 14f, Color.White, bold = true)
        text(tm, "TLV", Offset(stubW + 16.dp.toPx(), 48.dp.toPx()), 58f, Palette.ink, display = true)
        text(tm, "TBS", Offset(w - 100.dp.toPx(), 48.dp.toPx()), 58f, Palette.ink, display = true)
        drawLine(Palette.muted, Offset(stubW + 110.dp.toPx(), 86.dp.toPx()), Offset(w - 112.dp.toPx(), 86.dp.toPx()), strokeWidth = 2.dp.toPx())
        text(tm, "טיסה GD 101 · 15.12 · 16:00", Offset(stubW + 16.dp.toPx(), 150.dp.toPx()), 15f, Palette.ink, bold = true)
        text(tm, "שער — · מושב —", Offset(stubW + 16.dp.toPx(), 178.dp.toPx()), 13f, Palette.muted)
        // perforation: punched holes, the torn ones open to the background
        val holeR = 3.5f.dp.toPx()
        for (i in 0 until HOLES) {
            val y = h * (i + 0.5f) / HOLES
            drawCircle(if (i < torn) Palette.snow else Color(0xFFE2DED6), holeR, Offset(stubW, y))
        }
        // the stub: hinges away from the top as it tears, then falls
        val pivot = Offset(stubW, h)
        val ang = if (falling) 0f else -tear * 9f
        translate(0f, fall.first) {
            rotate(ang + fall.second, pivot) {
                val a = fall.third
                drawRoundRect(paper.copy(alpha = a), Offset(0f, 0f), Size(stubW - 2, h), CornerRadius(r))
                drawRect(Palette.glacier.copy(alpha = a), Offset(0f, 0f), Size(stubW - 2, 34.dp.toPx()))
                text(tm, "ספח", Offset(12.dp.toPx(), 7.dp.toPx()), 14f, Color.White.copy(alpha = a), bold = true)
                text(tm, "TBS", Offset(12.dp.toPx(), 52.dp.toPx()), 46f, Palette.ink.copy(alpha = a), display = true)
                text(tm, "15.12", Offset(12.dp.toPx(), 150.dp.toPx()), 16f, Palette.ink.copy(alpha = a), bold = true)
            }
        }
    }
}

/** A trail sign on its post with fresh snow on top and a few drips, the arrow pointing on (left, in Hebrew). */
private fun DrawScope.drawSnowSign(tm: TextMeasurer, wobble: Float) {
    val w = size.width; val h = size.height
    val bw = w * 0.78f; val bh = 64.dp.toPx(); val left = (w - bw) / 2; val top = 46.dp.toPx()
    drawRect(Color(0xFF3A4556), Offset(w / 2 - 5.dp.toPx(), top + bh - 4), Size(10.dp.toPx(), h - top - bh + 4))
    rotate(wobble, Offset(w / 2, top + bh)) {
        val arrow = 28.dp.toPx()
        val board = Path().apply {
            moveTo(left + arrow, top); lineTo(left + bw, top); lineTo(left + bw, top + bh); lineTo(left + arrow, top + bh); lineTo(left, top + bh / 2); close()
        }
        drawPath(board, Palette.glacier)
        text(tm, "מפת המסלולים", Offset(left + arrow + 14.dp.toPx(), top + 6.dp.toPx()), 40f, Color.White, display = true)
        // fresh snow: soft lumps along the top edge, a little shadow, and rounded drips
        val snow = Path().apply {
            moveTo(left + arrow - 6, top + 2)
            var x = left + arrow - 6
            var i = 0
            while (x < left + bw + 6) {
                val bump = (9 + (i * 37 % 11)).dp.toPx() * 0.6f
                val nx = (x + (26 + i * 13 % 17).dp.toPx() * 0.7f).coerceAtMost(left + bw + 6)
                quadraticTo((x + nx) / 2, top - bump, nx, top + 2)
                x = nx; i++
            }
            lineTo(left + bw + 6, top + 7.dp.toPx()); lineTo(left + arrow - 6, top + 7.dp.toPx()); close()
        }
        drawPath(snow, Color(0x3313233A), alpha = 1f)
        translate(0f, -2.dp.toPx()) { drawPath(snow, Color.White) }
        for ((fx, len) in listOf(0.30f to 14f, 0.55f to 9f, 0.82f to 18f)) {
            val cx = left + bw * fx
            drawRoundRect(Color.White, Offset(cx - 3.dp.toPx(), top + 3.dp.toPx()), Size(6.dp.toPx(), len.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawCircle(Color.White, 4.dp.toPx(), Offset(cx, top + len.dp.toPx()))
        }
    }
}

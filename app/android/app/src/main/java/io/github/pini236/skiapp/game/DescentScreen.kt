package io.github.pini236.skiapp.game

import androidx.compose.foundation.Canvas
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.i18n.Lang
import androidx.compose.ui.platform.LocalContext
import io.github.pini236.skiapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.data.Profile
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.map.Button
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Palette
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.sin
import kotlin.random.Random

private class Flake(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float)

@Composable
fun DescentScreen(profile: Profile?, haptics: Haptics, sounds: Sounds) {
    if (profile == null) { Box(Modifier.fillMaxSize()) { Text(stringResource(R.string.loading), Modifier.align(Alignment.Center), fontFamily = Plex) }; return }
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { Descent(profile.h, profile.step, profile.len) }
    val flakes = remember(round) { ArrayList<Flake>() }
    var frame by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf("") }
    var done by remember(round) { mutableStateOf(false) }
    var shake by remember { mutableStateOf(0f) }
    val res = LocalContext.current.resources
    val nf = remember { NumberFormat.getIntegerInstance(Lang.current(res).locale) }

    LaunchedEffect(round) { Telemetry.event("game_start", mapOf("game" to "descent", "run" to profile.key)) }
    val startedAt = remember(round) { System.currentTimeMillis() }
    LaunchedEffect(done) {
        if (done) Telemetry.event("game_end", mapOf("game" to "descent", "run" to profile.key, "flips" to game.flips, "landings" to game.landings,
            "crashes" to game.crashes, "seconds" to (System.currentTimeMillis() - startedAt) / 1000))
    }
    LaunchedEffect(round) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                val dt = if (last == 0L) 0.016f else (t - last) / 1e9f
                last = t
                val wasGround = !game.air
                game.update(dt)
                // snow: spray behind the skis on the snow, a burst on every landing
                if (!game.air && game.crash <= 0 && Random.nextFloat() < game.s * dt * 2.5f) {
                    repeat(3) { flakes += Flake(game.x - 0.6f, game.y + 0.1f, -game.s * 0.25f + Random.nextFloat() * 2 - 1, 1f + Random.nextFloat() * 3, 0.6f, 0.06f + Random.nextFloat() * 0.08f) }
                }
                if (game.eventJump > 0) { haptics.click(0.4f + 0.5f * game.eventJump); sounds.play("ticket-slide", 0.35f, 1.5f); game.eventJump = 0f }
                if (game.eventLand > 0) {
                    val k = game.eventLand
                    haptics.thud(k); sounds.play("ticket-land", 0.4f + 0.6f * k, 0.8f)
                    repeat((10 + 24 * k).toInt()) { flakes += Flake(game.x, game.y + 0.2f, Random.nextFloat() * 8 - 4, Random.nextFloat() * 5, 0.9f, 0.08f + Random.nextFloat() * 0.1f) }
                    shake = 4f * k; game.eventLand = 0f
                }
                if (game.eventFlip) { haptics.tick(0.8f); game.eventFlip = false }
                if (game.eventCrash) { haptics.thud(1f); haptics.click(1f); sounds.play("ticket-land", 1f, 0.6f); shake = 6f; game.eventCrash = false }
                val it = flakes.iterator()
                while (it.hasNext()) { val f = it.next(); f.life -= dt; f.vy -= 9f * dt; f.x += f.vx * dt; f.y += f.vy * dt; if (f.life <= 0) it.remove() }
                if (flakes.size > 400) flakes.subList(0, flakes.size - 400).clear()
                shake *= Math.exp(-10.0 * dt).toFloat()
                if (wasGround && game.finished && !done) { done = true; haptics.click(1f) }
                frame++
            }
        }
    }
    LaunchedEffect(round) {
        while (true) {
            hud = res.getString(R.string.descent_hud, profile.key, nf.format(game.distance.toInt()), nf.format(profile.len.toInt()), game.speedKmh.toInt(), game.flips)
            delay(100)
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(round) {
            awaitEachGesture {
                awaitFirstDown(); game.press()
                waitForUpOrCancellation(); game.release()
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame // redraw every game frame
            drawScene(game, flakes, shake)
        }
        Text(hud, Modifier.align(Alignment.TopCenter).padding(top = 40.dp).background(Color(0xCC13233A)).padding(horizontal = 10.dp, vertical = 4.dp),
            fontFamily = Plex, fontSize = 14.sp, color = Color.White)
        if (done) {
            Column(Modifier.align(Alignment.Center).background(Color(0xF2FFFFFF)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.descent_done), fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Palette.ink)
                Text(stringResource(R.string.descent_score, game.flips, game.landings, game.crashes), fontFamily = Plex, fontSize = 15.sp, color = Palette.ink)
                Box(Modifier.padding(top = 12.dp)) { Button(stringResource(R.string.descent_again)) { round++ } }
            }
        } else {
            Text(stringResource(if (game.air) R.string.descent_hint_air else R.string.descent_hint_ground),
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE613233A)).padding(14.dp),
                fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        }
    }
}

private fun DrawScope.drawScene(g: Descent, flakes: List<Flake>, shake: Float) {
    val w = size.width; val h = size.height
    // a tall phone sees fewer metres across, so the skier is big enough to read (the site shows 46 m)
    val metres = if (h > w * 1.3f) 26f else 46f
    val scale = w / metres
    val camX = g.x + metres * 0.2f // the skier left of centre, the run ahead in view
    val camY = g.groundAt(camX) + 4f
    val shX = if (shake > 0.05f) (Random.nextFloat() - 0.5f) * shake * 3 else 0f
    fun sx(x: Float) = (x - camX) * scale + w * 0.5f + shX
    fun sy(y: Float) = h * 0.56f - (y - camY) * scale
    val px1 = w / 1080f // the ridges' waves were drawn for a 1080-pixel-wide screen
    val horizon = sy(camY - 4f) - h * 0.12f

    drawRect(Brush.verticalGradient(listOf(Color(0xFF9FC3E6), Palette.sky1, Palette.sky2)))
    // far ridges, slower than the snow
    for ((k, col) in listOf(0.12f to Color(0xFFC9D6E3), 0.25f to Color(0xFFB2C3D4))) {
        val p = Path(); p.moveTo(0f, h)
        var px = 0f
        while (px <= w + 20) {
            val wx = (px / scale + camX * k)
            val ry = horizon - h * k * 0.3f - (sin(wx * 0.045f) * 40 + sin(wx * 0.11f + 1) * 22 + sin(wx * 0.017f) * 70) * (1 + k) * px1
            p.lineTo(px, ry); px += 12f
        }
        p.lineTo(w, h); p.close(); drawPath(p, col)
    }
    // the run itself: its real profile
    val ground = Path()
    val x0 = camX - w / 2 / scale - 4; val x1 = camX + w / 2 / scale + 4
    ground.moveTo(sx(x0), h)
    var x = x0
    while (x <= x1) { ground.lineTo(sx(x), sy(g.groundAt(x))); x += 2f }
    ground.lineTo(sx(x1), h); ground.close()
    drawPath(ground, Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE7F2)), startY = sy(g.groundAt(camX)) - 80, endY = h))
    // trees along the run, every so often
    var tx = (x0 / 23).toInt() * 23f
    while (tx < x1) {
        val hash = ((tx * 7919).toInt() and 0xff) / 255f
        if (hash > 0.35f) {
            val bx = sx(tx + hash * 9); val by = sy(g.groundAt(tx + hash * 9)) + 4
            val th = (3.5f + hash * 3) * scale
            val tree = Path().apply { moveTo(bx, by - th); lineTo(bx - th * 0.32f, by); lineTo(bx + th * 0.32f, by); close() }
            drawPath(tree, Color(0xFF2E5A45))
        }
        tx += 23f
    }
    // the skier
    translate(sx(g.x), sy(g.y)) {
        rotate(-Math.toDegrees(g.ang.toDouble()).toFloat(), pivot = Offset.Zero) {
            val m = scale
            val squash = 1f - 0.25f * g.squash.coerceIn(-1f, 1f)
            val body = (1.55f - 0.55f * g.crouch) * m * squash
            drawLine(Palette.ink, Offset(-1.0f * m, 0f), Offset(1.0f * m, 0f), strokeWidth = 0.12f * m, cap = StrokeCap.Round) // skis
            drawLine(Palette.ink, Offset(0f, -0.1f * m), Offset(0.1f * m, -body * 0.45f), strokeWidth = 0.28f * m, cap = StrokeCap.Round) // legs
            drawLine(Color(0xFFF07A2E), Offset(0.1f * m, -body * 0.45f), Offset(0.25f * m, -body * 0.92f), strokeWidth = 0.42f * m, cap = StrokeCap.Round) // jacket
            drawCircle(Color(0xFFF2D3B5), radius = 0.2f * m, center = Offset(0.32f * m, -body * 1.08f))
            drawCircle(Palette.red, radius = 0.21f * m, center = Offset(0.32f * m, -body * 1.15f)) // hat
        }
    }
    for (f in flakes) drawCircle(Color.White.copy(alpha = (f.life * 1.4f).coerceIn(0f, 1f)), radius = f.size * scale, center = Offset(sx(f.x), sy(f.y)))
}

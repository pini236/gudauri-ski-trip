package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.delay
import kotlin.math.hypot

/**
 * The first time (S09): no tour, one hint at a time on the mountain itself, each once ever (kept on the phone, nothing
 * measured). Hint 1, a ring on a blue run near the middle, 1,200 ms after the mountain is up on the first opening. Hints 2
 * and 3 point at "where to?" and "the village" in the bottom bar, which comes with the village and the shelf (K17, K06).
 */
internal object Hints {
    private const val PREFS = "redesign"
    const val RUN = "hint_run"
    fun seen(ctx: android.content.Context, id: String) = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(id, false)
    fun done(ctx: android.content.Context, id: String) { ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(id, true).apply(); Qa.log("redesign hint done $id") }
    /** The emulator's run starts it again (qa.redesign hint:reset). */
    fun reset(ctx: android.content.Context) {
        val sp = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val e = sp.edit(); sp.all.keys.filter { it.startsWith("hint_") }.forEach { e.remove(it) }; e.apply()
    }
    /** Bumped by Settings' "show the hints again": the mountain starts its hints from the first again. */
    var again by androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * Hint 1. [free]: no sheet open and not recording; [chosen]: a run was chosen (the hint's action). The ring is on the
 * blue run whose middle is nearest the screen's middle, kept there while the view moves.
 */
@Composable
internal fun BoxScope.RunHint(scene: MapScene, view: Stage, night: Boolean, free: Boolean, chosen: Boolean, drawn: Boolean, again: Int) {
    val ctx = LocalContext.current
    var on by remember(again) { mutableStateOf(false) }
    // the emulator's (debug) build shows it only once asked (qa.redesign hint:reset), so it stays out of the other shots
    var gone by remember(again) { mutableStateOf(Hints.seen(ctx, Hints.RUN) || (Qa.enabled && again == 0)) }
    LaunchedEffect(drawn, again) { if (drawn && !gone) { delay(1200); on = true; Qa.log("redesign hint run") } }
    // the emulator keeps it until the run is chosen (its pictures take longer than 8 seconds)
    LaunchedEffect(on) { if (on && !Qa.enabled) { delay(8000); if (on) { on = false; gone = true; Hints.done(ctx, Hints.RUN) } } }
    LaunchedEffect(chosen) { if (chosen && on) { on = false; gone = true; Hints.done(ctx, Hints.RUN) } }
    if (!on || !free) return
    val mids = remember(scene) {
        scene.runs.pistes.filter { it.color == "blue" && it.named }.mapNotNull { p ->
            val l = p.lines.maxByOrNull { it.size } ?: return@mapNotNull null
            val n = l.size / 2; if (n < 2) null else floatArrayOf(l[(n / 2) * 2], l[(n / 2) * 2 + 1])
        }
    }
    var at by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(mids, size) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val c = Offset(size.width / 2f, size.height / 2f)
            at = mids.mapNotNull { m -> view.toScreen(m[0], m[1])?.let { Offset(it[0], it[1]) } }
                .filter { it.x in 0f..size.width.toFloat() && it.y in 0f..size.height.toFloat() }.minByOrNull { hypot(it.x - c.x, it.y - c.y) }
            delay(100)
        }
    }
    val ink = if (night) Color(0xFFEAF1F8) else Color(0xFF0E2233)
    val bg = if (night) Color(0xFF0F1828) else Color(0xFFF7FAFD)
    val text = stringResource(R.string.app_hint_run)
    val close = stringResource(R.string.app_hint_close_a11y)
    val dens = LocalDensity.current
    var bw by remember { mutableStateOf(0) }; var bh by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        val p = at ?: return@Box
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .3f), 23.dp.toPx(), p)
            drawCircle(Color.White, 23.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
        }
        // the bubble above the ring, its point (12) down at the ring
        val gap = with(dens) { 23.dp.toPx() + 12.dp.toPx() + 4.dp.toPx() }
        val left = (p.x - bw / 2f).coerceIn(with(dens) { 12.dp.toPx() }, (size.width - bw - with(dens) { 12.dp.toPx() }).coerceAtLeast(0f))
        Canvas(Modifier.fillMaxSize()) {
            val t = p.y - 23.dp.toPx() - 4.dp.toPx()
            drawPath(Path().apply { moveTo(p.x - 8.dp.toPx(), t - 12.dp.toPx()); lineTo(p.x + 8.dp.toPx(), t - 12.dp.toPx()); lineTo(p.x, t); close() }, ink)
        }
        Text(text, Modifier.atScreen { IntOffset(left.toInt(), (p.y - gap - bh).toInt()) }.widthIn(max = 230.dp)
            .onSizeChanged { bw = it.width; bh = it.height }
            .shadow(22.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x4D0A192D), spotColor = Color(0x4D0A192D))
            .clip(RoundedCornerShape(16.dp)).background(ink)
            .semantics { role = Role.Button; contentDescription = "$text $close" }
            .clickable { on = false; gone = true; Hints.done(ctx, Hints.RUN) }.padding(horizontal = 14.dp, vertical = 10.dp),
            fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = bg)
    }
}

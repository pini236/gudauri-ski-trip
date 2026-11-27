package io.github.pini236.skiapp.redesign

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Motion
import kotlinx.coroutines.delay

/**
 * First-run hints, version 2 (handoff 2026-11-26-redesign-hints): one guided sequence. A dim with a hole round the target,
 * a bubble with the text, an X in a ring that drains over 13 seconds, and dots. A tap anywhere is the next hint (the tap is
 * consumed), the X closes all, the last one ends it. Kept on the phone only ([Hints]); nothing is measured.
 */
internal class HintStep(val id: String, @StringRes val text: Int, val radiusDp: Float? = null)

/** Where the controls are on the screen, by the id a hint points at (the controls report themselves). */
internal object HintTargets {
    val rects = mutableStateMapOf<String, Rect>()
}

@Composable
internal fun Modifier.hintTarget(id: String): Modifier {
    DisposableEffect(id) { onDispose { HintTargets.rects.remove(id) } }
    return this.onGloballyPositioned { HintTargets.rects[id] = it.boundsInRoot() }
}

/**
 * [prefKey] is [Hints.MOUNTAIN] or [Hints.VILLAGE]; [ready]: the screen is quiet (nothing open, not recording). [runAt] gives
 * the point of the run the "run" step rings, from the screen's size (null: none on the screen, the step is skipped).
 */
@Composable
internal fun BoxScope.HintSequence(prefKey: String, steps: List<HintStep>, ready: Boolean, night: Boolean, again: Int, runAt: ((IntSize) -> Offset?)? = null) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current
    val reduced = Motion.reduced(ctx)
    val am = remember { ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    val screenReader = am.isTouchExplorationEnabled
    // the emulator's (debug) build shows them only once asked (qa.redesign hint:reset), so they stay out of the other shots
    val started = !(Qa.enabled && again == 0)
    var step by remember(again, steps.size) { mutableIntStateOf(Hints.next(ctx, prefKey)) }
    var on by remember(again, steps.size) { mutableStateOf(false) }
    var resumed by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val life = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) on = false
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed++
        }
        life?.lifecycle?.addObserver(obs)
        onDispose { life?.lifecycle?.removeObserver(obs) }
    }
    fun finish() { on = false; step = -1; Hints.setNext(ctx, prefKey, -1); Qa.log("redesign hint done $prefKey") }
    fun advance() {
        val n = step + 1
        if (n >= steps.size) finish() else { step = n; Hints.setNext(ctx, prefKey, n); Qa.log("redesign hint step ${steps[n].id}") }
    }
    LaunchedEffect(ready, started, resumed, again, step >= 0) {
        if (!ready || !started || step < 0) on = false
        else if (!on) { delay(1200); on = true; Qa.log("redesign hint step ${steps.getOrNull(step)?.id}") }
    }
    val cur = steps.getOrNull(step)
    var runPt by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(on, cur?.id, size) {
        if (on && cur?.id == "run" && runAt != null) while (true) { withFrameNanos { }; runPt = runAt(size); delay(100) }
    }
    val rect: Rect? = when {
        cur == null -> null
        cur.id == "run" -> runPt?.let { with(dens) { Rect(it.x - 23.dp.toPx(), it.y - 23.dp.toPx(), it.x + 23.dp.toPx(), it.y + 23.dp.toPx()) } }
        else -> HintTargets.rects[cur.id]
    }
    // a control that is not on the screen (or no run in sight): the step is skipped
    LaunchedEffect(on, cur?.id, rect == null) { if (on && rect == null) { delay(900); if (on) advance() } }
    val progress = remember { Animatable(1f) }
    val total = if (Build.VERSION.SDK_INT >= 29) am.getRecommendedTimeoutMillis(13000, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS) else 13000
    LaunchedEffect(on, step, rect != null) {
        progress.snapTo(1f)
        if (on && rect != null && !Qa.enabled && !screenReader) {
            if (reduced) { val secs = (total / 1000).coerceAtLeast(1); for (i in 1..secs) { delay(1000); progress.snapTo(1f - i / secs.toFloat()) } }
            else progress.animateTo(0f, tween(total, easing = LinearEasing))
            advance()
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it })
    if (!on || rect == null || cur == null) return
    BackHandler { finish() }
    val ink = if (night) Color(0xFFEAF1F8) else Color(0xFF0E2233)
    val bg = if (night) Color(0xFF0F1828) else Color(0xFFF7FAFD)
    val dim = if (night) Color(0x57000000) else Color(0x4208101E)
    val outer = if (night) Color(0xBF060B16) else Color.White
    val hole by animateRectAsState(rect, if (reduced) snap<Rect>() else tween<Rect>(300, easing = FastOutSlowInEasing), label = "hint-hole")
    val shrink = remember { Animatable(1.18f) }
    LaunchedEffect(step) { shrink.snapTo(if (reduced) 1f else 1.18f); shrink.animateTo(1f, tween(200)) }
    val isRun = cur.id == "run"
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(dim)
        val g = 5.dp.toPx()
        val k = shrink.value
        val w = (hole.width + 2 * g) * k; val h = (hole.height + 2 * g) * k
        val tl = Offset(hole.center.x - w / 2, hole.center.y - h / 2)
        val r = cur.radiusDp?.dp?.toPx() ?: (minOf(w, h) / 2f)
        drawRoundRect(Color.Black, tl, Size(w, h), CornerRadius(r), blendMode = BlendMode.Clear)
        if (isRun) drawRoundRect(Color.White.copy(alpha = .3f), tl, Size(w, h), CornerRadius(r))
        drawRoundRect(ink, tl, Size(w, h), CornerRadius(r), style = Stroke(2.5.dp.toPx()))
        val o = 2.5.dp.toPx()
        drawRoundRect(outer, Offset(tl.x - o, tl.y - o), Size(w + 2 * o, h + 2 * o), CornerRadius(r + o), style = Stroke(2.5.dp.toPx()))
    }
    // a tap anywhere: the next hint (consumed: nothing under it hears the touch)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { advance() } })
    // the bubble: above the target, below it in the top quarter, beside it (toward the centre) for the button column
    val text = stringResource(cur.text)
    val n = step + 1
    val a11y = stringResource(R.string.app_hint_step_a11y, n.toString(), steps.size.toString()) + ". " + text
    val nextLabel = stringResource(R.string.app_hint_next_a11y)
    val skipLabel = stringResource(R.string.app_hints_skip_a11y)
    var bw by remember(step) { mutableIntStateOf(0) }
    var bh by remember(step) { mutableIntStateOf(0) }
    val edge = with(dens) { 12.dp.toPx() }
    val tip = with(dens) { 12.dp.toPx() }
    val pointH = with(dens) { 8.dp.toPx() }
    val gapAll = tip + pointH
    val beside = cur.id == "mode"
    val below = !beside && hole.center.y < size.height * .25f
    val left = beside && hole.center.x > size.width / 2f
    val bx: Float; val by: Float
    if (beside) {
        bx = if (left) hole.left - gapAll - bw else hole.right + gapAll
        by = (hole.center.y - bh / 2f).coerceIn(edge, (size.height - bh - edge).coerceAtLeast(edge))
    } else {
        bx = (hole.center.x - bw / 2f).coerceIn(edge, (size.width - bw - edge).coerceAtLeast(edge))
        by = if (below) hole.bottom + gapAll else hole.top - gapAll - bh
    }
    Canvas(Modifier.fillMaxSize()) {
        if (bw == 0) return@Canvas
        val half = 7.dp.toPx()
        val p = Path()
        if (beside) {
            val py = hole.center.y.coerceIn(by + 14.dp.toPx(), by + bh - 14.dp.toPx())
            val baseX = if (left) bx + bw else bx
            val tipX = if (left) baseX + pointH else baseX - pointH
            p.moveTo(baseX, py - half); p.lineTo(baseX, py + half); p.lineTo(tipX, py)
        } else {
            val px = hole.center.x.coerceIn(bx + 14.dp.toPx(), bx + bw - 14.dp.toPx())
            val baseY = if (below) by else by + bh
            val tipY = if (below) baseY - pointH else baseY + pointH
            p.moveTo(px - half, baseY); p.lineTo(px + half, baseY); p.lineTo(px, tipY)
        }
        p.close()
        drawPath(p, ink)
    }
    Column(Modifier.atScreen { IntOffset(bx.toInt(), by.toInt()) }.widthIn(max = 300.dp).onSizeChanged { bw = it.width; bh = it.height }
        .alpha(if (bw == 0) 0f else 1f)
        .shadow(22.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x4D0A192D), spotColor = Color(0x4D0A192D))
        .clip(RoundedCornerShape(16.dp)).background(ink)
        .semantics(mergeDescendants = true) {
            contentDescription = a11y
            customActions = listOf(CustomAccessibilityAction(nextLabel) { advance(); true }, CustomAccessibilityAction(skipLabel) { finish(); true })
        }
        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, Modifier.weight(1f).padding(top = 4.dp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.6.sp, color = bg)
            // the X in its ring: 32 to see, 44 to touch; the ring drains over the hint's time
            Box(Modifier.size(44.dp).clickable(onClickLabel = skipLabel, role = Role.Button) { finish() }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(32.dp)) {
                    val c = center; val rr = 14.dp.toPx()
                    drawCircle(bg.copy(alpha = .22f), rr, c, style = Stroke(2.dp.toPx()))
                    drawArc(bg, -90f, 360f * progress.value, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    val x = 6.dp.toPx(); val sw = 1.8.dp.toPx()
                    drawLine(bg, Offset(c.x - x, c.y - x), Offset(c.x + x, c.y + x), sw, StrokeCap.Round)
                    drawLine(bg, Offset(c.x - x, c.y + x), Offset(c.x + x, c.y - x), sw, StrokeCap.Round)
                }
            }
        }
        // the dots: how many, and which one
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in steps.indices) {
                Box(Modifier.size(if (i == step) 16.dp else 6.dp, 6.dp).clip(RoundedCornerShape(3.dp))
                    .background(bg.copy(alpha = if (i == step) 1f else if (i < step) .6f else .32f)))
            }
        }
    }
}

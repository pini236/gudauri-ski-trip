package io.github.pini236.skiapp.redesign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * B10 (handoff 2026-11-26-redesign-b10): a long press on an icon-only button shows its name in a small pill; the button's own
 * action does not run. The pill stays while the finger is down and 1.5 s after it lifts.
 */
internal class NameHold {
    var shown by mutableStateOf(false)
    var down by mutableStateOf(false)
    var seq = 0
}

@Composable
internal fun rememberNameHold(): NameHold = remember { NameHold() }

/** The press of an icon button: a tap runs [onClick]; a long press shows the name (the pill is [NamePill], inside the button). */
@Composable
internal fun Modifier.nameHold(hold: NameHold, k: SheetInk, enabled: Boolean = true, withClickSemantics: Boolean = true, onClick: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    val tint = if (k.night) Color(0x38EAF1F8) else Color(0x290E2233)
    return this
        .drawBehind { if (hold.down) drawCircle(tint) }
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    val mine = ++hold.seq
                    hold.down = true
                    tryAwaitRelease()
                    hold.down = false
                    if (hold.shown) scope.launch { delay(1500); if (hold.seq == mine) hold.shown = false }
                },
                onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); hold.shown = true },
                onTap = { if (enabled) click() })
        }
        .let { if (withClickSemantics) it.semantics { if (enabled) semanticsOnClick { click(); true } } else it }
}

/** The pill itself: 32 high, radius 16, the ink on the background's colour; 10 from the button toward the screen's centre (or above it). */
@Composable
internal fun NamePill(hold: NameHold, name: String, k: SheetInk, above: Boolean = false) {
    val reduced = io.github.pini236.skiapp.ui.Motion.reduced(LocalContext.current)
    val ts = remember { MutableTransitionState(false) }
    ts.targetState = hold.shown
    if (!ts.currentState && !ts.targetState) return
    val density = LocalDensity.current
    val gap = with(density) { 10.dp.roundToPx() }
    val edge = with(density) { 12.dp.roundToPx() }
    val provider = remember(above, gap, edge) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val w = popupContentSize.width; val h = popupContentSize.height
                val x: Int; val y: Int
                if (above) { x = anchorBounds.center.x - w / 2; y = anchorBounds.top - gap - h }
                else {
                    x = if (anchorBounds.center.x > windowSize.width / 2) anchorBounds.left - gap - w else anchorBounds.right + gap
                    y = anchorBounds.center.y - h / 2
                }
                return IntOffset(x.coerceIn(edge, maxOf(edge, windowSize.width - w - edge)), y.coerceIn(edge, maxOf(edge, windowSize.height - h - edge)))
            }
        }
    }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        AnimatedVisibility(ts,
            enter = if (reduced) EnterTransition.None else fadeIn(tween(150)) + scaleIn(tween(150), initialScale = .92f),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(150)) + scaleOut(tween(150), targetScale = .92f)) {
            val shape = RoundedCornerShape(16.dp)
            Box(Modifier.clearAndSetSemantics {}.shadow(8.dp, shape, clip = false).height(32.dp).clip(shape).background(k.ink).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center) {
                Text(name, fontFamily = Gudi, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = if (k.night) Color(0xFF0F1828) else Color.White, maxLines = 1)
            }
        }
    }
}

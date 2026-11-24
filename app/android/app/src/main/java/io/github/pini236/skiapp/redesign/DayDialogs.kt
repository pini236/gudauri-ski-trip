package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.qa.Qa

/**
 * The ski day's two windows in the new design (handoff 2026-11-24-redesign-day-dialogs): X01 "the day was cut" and X02
 * "before recording". The mountain stays behind under an even dim (#060B16 at .38); a glass sheet at the bottom, full
 * width with its top corners 28 (Android); a head in the middle (a 52 circle with a 26 sign), the buttons the sheet's width.
 */
private val DIM = Color(0x61060B16)

@Composable
private fun DayDialog(night: Boolean, onDismiss: (() -> Unit)?, title: String, sign: @Composable (Color) -> Unit, body: @Composable ColumnScope.(SheetInk) -> Unit) {
    val k = remember(night) { SheetInk(night) }
    Dialog(onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = onDismiss != null, dismissOnClickOutside = false)) {
        // X01 answers only with its two buttons: back does nothing (as before); X02 closes on back
        BackHandler { onDismiss?.invoke() }
        Box(Modifier.fillMaxSize().background(DIM).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() }) {
            val top = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().pullSheet(onDown = onDismiss)
                .shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .semantics { paneTitle = title }
                .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // a handle only on the one that closes by a pull
                if (onDismiss != null) Box(Modifier.size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line)) else Box(Modifier.height(4.dp))
                Box(Modifier.size(52.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) { sign(k.ink) }
                Text(title, Modifier.focusRequester(focus).focusable().semantics { heading() }, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                    color = k.ink, textAlign = TextAlign.Center)
                body(k)
            }
        }
    }
}

@Composable
private fun Line(text: String, k: SheetInk) =
    Text(text, Modifier.widthIn(max = 300.dp), fontFamily = Gudi, fontSize = 14.5.sp, lineHeight = 20.sp, color = k.ink2, textAlign = TextAlign.Center)

@Composable
private fun Main(text: String, k: SheetInk, onClick: () -> Unit) =
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(k.acc).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.onAcc, textAlign = TextAlign.Center)
    }

private fun night() = Sky.at(DayNight.lightTime(System.currentTimeMillis(), DayNight.Mode.AUTO)).dark

/** X01: a day the phone cut short; go on with it, or close it. No handle, no tap outside, no back: both answers always seen. */
@Composable
internal fun CutDay(onResume: () -> Unit, onClose: () -> Unit) {
    LaunchedEffect(Unit) { Qa.log("redesign day cut") }
    DayDialog(remember { night() }, null, stringResource(R.string.app_rec_cut_title), sign = { c ->
        Canvas(Modifier.size(26.dp)) {
            val s = 1.8.dp.toPx()
            drawCircle(c, size.minDimension / 2 - s / 2, style = Stroke(s))
            for (x in floatArrayOf(.4f, .6f)) drawLine(c, Offset(size.width * x, size.height * .33f), Offset(size.width * x, size.height * .67f), s, StrokeCap.Round)
        }
    }) { k ->
        Line(stringResource(R.string.app_rec_cut_text), k)
        Box(Modifier.height(2.dp))
        Main(stringResource(R.string.app_rec_cut_go), k) { Qa.log("redesign day cut go"); onResume() }
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, k.line, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button) { Qa.log("redesign day cut close"); onClose() }, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.app_rec_cut_close), Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = k.ink, textAlign = TextAlign.Center)
        }
    }
}

/** X02: what is in the way of recording (no permission, only approximate, or location off), and the way to the settings. */
@Composable
internal fun Blocked(kind: String, onSettings: () -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(kind) { Qa.log("redesign day blocked $kind") }
    val text = when (kind) { "precise" -> R.string.app_rec_err_precise; "off" -> R.string.app_rec_err_off; else -> R.string.app_rec_err_perm }
    DayDialog(remember { night() }, onDismiss, stringResource(R.string.app_rec_perm_title), sign = { c ->
        Canvas(Modifier.size(26.dp)) {
            val s = 1.8.dp.toPx(); val w = size.width; val h = size.height
            drawRoundRect(c, Offset(w * .2f, h * .45f), Size(w * .6f, h * .43f), CornerRadius(2.5.dp.toPx()), style = Stroke(s))
            drawArc(c, 180f, 180f, false, Offset(w * .32f, h * .14f), Size(w * .36f, h * .5f), style = Stroke(s, cap = StrokeCap.Round))
            drawLine(c, Offset(w * .32f, h * .39f), Offset(w * .32f, h * .45f), s); drawLine(c, Offset(w * .68f, h * .39f), Offset(w * .68f, h * .45f), s)
        }
    }) { k ->
        Line(stringResource(text), k)
        Box(Modifier.height(2.dp))
        Main(stringResource(R.string.app_rec_settings), k) { Qa.log("redesign day blocked settings"); onSettings() }
    }
}

package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Plex

/**
 * The first time (board L01): one sheet before the system's location question. What the day does, that it all stays on
 * the phone, which permission is enough, and all day or one descent; "start" asks the system, "not now" closes. The
 * board's second sentence under "everything stays on the phone" has no key of its own, so the line is the existing one.
 */
@Composable
internal fun DayStart(single: Boolean, haptics: Haptics, onGo: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val night = remember { Sky.at(DayNight.lightTime(System.currentTimeMillis(), DayNight.Mode.AUTO)).dark }
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xF20F1828) else Color(0xFAFFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val accent = Color(0xFF7C4FE0)
    var one by remember { mutableStateOf(single) }
    LaunchedEffect(Unit) { Qa.log("redesign rec first") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { onDismiss() }
        Box(Modifier.fillMaxSize().background(Color(0x66050A16)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }) {
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp).fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(rule))
                Text(stringResource(R.string.app_rec_first_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = ink)
                // three lines, each with its sign (board L01): the pocket, the lock, the pin
                for ((line, icon) in listOf(R.string.app_rec_first_text to 0, R.string.app_rec_perm_local to 1, R.string.app_rec_perm_ok to 2))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = if (night) .28f else .12f)), contentAlignment = Alignment.Center) {
                            StartSign(icon, if (night) Color(0xFFB99CFF) else accent, Modifier.size(18.dp))
                        }
                        Text(stringResource(line), Modifier.weight(1f), fontFamily = Plex, fontWeight = if (icon == 0) FontWeight.Normal else FontWeight.Bold, fontSize = 14.sp, color = ink)
                    }
                // all day or one descent: the same recording either way (round 29)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, rule, RoundedCornerShape(22.dp)).padding(3.dp)) {
                    for ((on, key) in listOf(false to R.string.app_rec_all, true to R.string.app_rec_one)) {
                        val sel = one == on
                        Text(stringResource(key), Modifier.weight(1f).clip(RoundedCornerShape(19.dp)).background(if (sel) accent else Color.Transparent)
                            .clickable(role = Role.Button) { haptics.tick(.2f); one = on }.padding(vertical = 10.dp),
                            fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sel) Color.White else ink, textAlign = TextAlign.Center)
                    }
                }
                // "start" with the recording's dot (board L01)
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(accent)
                    .clickable(role = Role.Button) { haptics.tick(.5f); Qa.log("redesign rec first go ${if (one) "run" else "day"}"); onGo(one) }.padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(Color(0xFFE5322D)))
                    Text(stringResource(R.string.app_rec_first_go), Modifier.padding(start = 10.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
                Text(stringResource(R.string.app_rec_first_later), Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button) { onDismiss() }.padding(vertical = 10.dp),
                    fontFamily = Plex, fontSize = 15.sp, color = muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The first-time sheet's three signs (board L01), drawn: 0 the pocket, 1 the lock, 2 the pin. */
@Composable
private fun StartSign(kind: Int, c: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height; val st = androidx.compose.ui.graphics.drawscope.Stroke(w * .11f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        when (kind) {
            0 -> { // a pocket: its outline and its seam, the phone peeking out
                drawRoundRect(c, androidx.compose.ui.geometry.Offset(w * .32f, 0f), androidx.compose.ui.geometry.Size(w * .36f, h * .5f),
                    androidx.compose.ui.geometry.CornerRadius(w * .06f), style = st)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * .1f, h * .32f); lineTo(w * .9f, h * .32f); lineTo(w * .9f, h * .72f)
                    quadraticTo(w * .5f, h * 1.02f, w * .1f, h * .72f); close()
                }
                drawPath(p, c, style = st)
            }
            1 -> { // a lock
                drawRoundRect(c, androidx.compose.ui.geometry.Offset(w * .16f, h * .44f), androidx.compose.ui.geometry.Size(w * .68f, h * .52f),
                    androidx.compose.ui.geometry.CornerRadius(w * .08f), style = st)
                drawArc(c, 180f, 180f, false, androidx.compose.ui.geometry.Offset(w * .3f, h * .06f), androidx.compose.ui.geometry.Size(w * .4f, h * .66f), style = st)
            }
            else -> { // a pin
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * .5f, h * .98f)
                    cubicTo(w * .14f, h * .6f, w * .1f, h * .42f, w * .1f, h * .4f)
                    cubicTo(w * .1f, h * .1f, w * .3f, h * .02f, w * .5f, h * .02f)
                    cubicTo(w * .7f, h * .02f, w * .9f, h * .1f, w * .9f, h * .4f)
                    cubicTo(w * .9f, h * .42f, w * .86f, h * .6f, w * .5f, h * .98f); close()
                }
                drawPath(p, c, style = st)
                drawCircle(c, w * .12f, androidx.compose.ui.geometry.Offset(w * .5f, h * .4f))
            }
        }
    }
}

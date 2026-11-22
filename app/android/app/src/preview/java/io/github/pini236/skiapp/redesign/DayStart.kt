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
                Text(stringResource(R.string.app_rec_first_text), fontFamily = Plex, fontSize = 15.sp, color = muted)
                for (line in listOf(R.string.app_rec_perm_local, R.string.app_rec_perm_ok)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Text(stringResource(line), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
                // all day or one descent: the same recording either way (round 29)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, rule, RoundedCornerShape(22.dp)).padding(3.dp)) {
                    for ((on, key) in listOf(false to R.string.app_rec_all, true to R.string.app_rec_one)) {
                        val sel = one == on
                        Text(stringResource(key), Modifier.weight(1f).clip(RoundedCornerShape(19.dp)).background(if (sel) ink else Color.Transparent)
                            .clickable(role = Role.Button) { haptics.tick(.2f); one = on }.padding(vertical = 10.dp),
                            fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (sel) (if (night) Color(0xFF0E2233) else Color.White) else ink, textAlign = TextAlign.Center)
                    }
                }
                Text(stringResource(R.string.app_rec_first_go), Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(accent)
                    .clickable(role = Role.Button) { haptics.tick(.5f); Qa.log("redesign rec first go ${if (one) "run" else "day"}"); onGo(one) }.padding(vertical = 15.dp),
                    fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
                Text(stringResource(R.string.app_rec_first_later), Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button) { onDismiss() }.padding(vertical = 10.dp),
                    fontFamily = Plex, fontSize = 15.sp, color = muted, textAlign = TextAlign.Center)
            }
        }
    }
}

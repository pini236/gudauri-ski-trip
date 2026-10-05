package io.github.pini236.skiapp.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.fx.FxPrefs
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.DayColors

/**
 * The sound on and off inside a game, as the site's button in the descent and the snowball fight (.snd, A-41): a dark
 * square of 44 with the speaker. It is the settings' sound, so it holds in every game and in the settings too.
 */
@Composable
internal fun SoundKey(label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(FxPrefs.sound(context)) }
    Box(modifier.size(44.dp).background(DayColors.ink.copy(alpha = .7f))
        .clickable(role = Role.Button) {
            on = !on; FxPrefs.set(context, "sound", on)
            Telemetry.event("settings_change", mapOf("setting" to "sound", "on" to on))
        }
        .semantics { contentDescription = label; stateDescription = if (on) "🔊" else "🔇" },
        contentAlignment = Alignment.Center) {
        Text(if (on) "🔊" else "🔇", color = Color.White, fontSize = 18.sp)
    }
}

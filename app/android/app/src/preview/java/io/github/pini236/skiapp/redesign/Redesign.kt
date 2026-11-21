package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.ui.Plex

/**
 * The redesign POC in the test build (decisions 83, 84): the app opens on the new mountain; "To the app" shows the
 * app as it is, and a small sign on home brings the mountain back. Release builds only: the debug build is the
 * emulator's (qa/run.sh), whose steps tap the current home. The store build has an empty twin (src/store).
 */
object Redesign {
    /** The emulator run's hook (qa.redesign, debug builds only, app/android/qa/README.md). */
    fun qa(cmd: String) = RedesignQa.send(cmd)

    @Composable
    fun BoxScope.Host(home: Boolean, mode: DayNight.Mode, haptics: Haptics) {
        // the emulator's scripts drive the debug build through the current screens (qa/run.sh): there the mountain
        // opens only when the run asks for it (qa.redesign open), so every other part sees the app as it is
        val debug = io.github.pini236.skiapp.BuildConfig.DEBUG
        if (debug && !RedesignQa.asked) return
        var open by rememberSaveable { mutableStateOf(!debug) }
        LaunchedEffect(RedesignQa.seq) {
            when (RedesignQa.cmd) {
                "open" -> open = true
                "close" -> open = false
            }
        }
        if (open) {
            BackHandler { open = false }
            val view = LocalView.current
            // light icons over the sky
            SideEffect { (view.context as? android.app.Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false } }
            Box(Modifier.fillMaxSize()) { PeakScreen(mode, haptics) { open = false } }
        } else {
            // over the sky of home, under the top row, where nothing is (the designer: at the bottom it covered a sign)
            AnimatedVisibility(home, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 84.dp, end = 16.dp), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.height(44.dp).shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color(0xF00E2233))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp)).clickable { haptics.tick(.4f); open = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFB79CFF)))
                    Text(stringResource(R.string.poc_peak_enter), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }
}

/**
 * The emulator's commands to the mountain (qa.redesign): open, close, view:<zoom>,<x>,<y> (the free view, as a pinch
 * would leave it; adb has one finger), and spot:<run> (logs where on the screen that run can be touched now, so the
 * script taps there with a real touch). The screen writes "redesign ..." lines for the script to wait on.
 */
object RedesignQa {
    var asked by mutableStateOf(false); private set
    var seq by mutableStateOf(0); private set
    var cmd: String = ""; private set
    fun send(c: String) { asked = true; cmd = c; seq++ }
}

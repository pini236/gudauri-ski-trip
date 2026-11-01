package io.github.pini236.skiapp.dayrec.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.delay

/**
 * The ski day's entry and live screen (round 29, approved 1.11.2026: one entry, "Record", from anywhere at any time; at the
 * top of the live screen the choice between all day and one descent). Behind the `dayrec` capability: nothing here is
 * reached while the site's data does not list it (dayrec/DayFeature.kt).
 */

/** The map's button (round 29, n-map): a dark card with the red record circle. */
@Composable
fun RecordButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    Row(
        modifier.background(c.ink).clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(stringResource(R.string.app_rec_sign), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.paper, maxLines = 1)
            Text(stringResource(R.string.app_rec_one) + " · " + stringResource(R.string.app_rec_all), style = Ski.type.small.copy(fontSize = 12.sp), color = c.paper.copy(alpha = .85f), maxLines = 1)
        }
        RecDot(Modifier.size(48.dp))
    }
}

/** The red record circle with its ring. */
@Composable
fun RecDot(modifier: Modifier = Modifier) {
    Box(modifier.border(BorderStroke(2.dp, Color(0x88E5322D)), CircleShape).padding(5.dp).background(Color(0xFFE5322D), CircleShape))
}

/** What the system's location question is about, before it comes (round 29, a-perm): three lines with an icon. */
@Composable
fun PermissionSheet(onGo: () -> Unit, onDismiss: () -> Unit) {
    val c = Ski.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(c.paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.app_rec_perm_title), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = c.ink)
            PermRow(Icons.phone, stringResource(R.string.app_rec_perm_pocket))
            PermRow(Icons.lock, stringResource(R.string.app_rec_perm_local))
            PermRow(Icons.check, stringResource(R.string.app_rec_perm_ok))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().background(c.blue).clickable(role = Role.Button, onClick = onGo).heightIn(min = 52.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_rec_perm_go), style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = c.onBoard)
            }
        }
    }
}

@Composable
private fun PermRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp), tint = Ski.colors.blue)
        Text(text, style = Ski.type.body, color = Ski.colors.ink)
    }
}

/**
 * The live screen: the speed now, what you are doing, four numbers, and "Finish". Read from [DayRec] once a second; the
 * recorder never waits for it. "Probably" on what you do (a causal guess); the timeline at the end of the day is the truth.
 */
@Composable
fun DayLiveScreen(liftName: (String) -> String, onSwitch: (single: Boolean) -> Unit, onStop: () -> Unit, onBack: () -> Unit) {
    val c = Ski.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val live = DayRec.live
    val st = DayRec.sofar?.stats
    val single = DayRec.single
    val recording = DayRec.recording
    val since = DayRec.since
    val elapsed = if (since > 0) now - since else 0L
    val red = Color(0xFFE5322D)
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            BackLink(stringResource(R.string.nav_home), onBack)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(stringResource(R.string.app_rec_all), !single, Modifier.weight(1f)) { if (single) onSwitch(false) }
            Choice(stringResource(R.string.app_rec_one), single, Modifier.weight(1f)) { if (!single) onSwitch(true) }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recording) RecDot(Modifier.size(14.dp))
            Text(stringResource(R.string.app_rec_now, LiveNumbers.elapsed(elapsed)), style = Ski.type.bodyBold, color = red)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(LiveNumbers.kmh(DayRec.speedNow), style = Ski.type.title.copy(fontSize = 120.sp), color = c.ink)
            Text(stringResource(R.string.app_rec_unit), Modifier.padding(bottom = 18.dp), style = Ski.type.number.copy(fontSize = 28.sp), color = c.ink)
        }
        Text(what(live, liftName), style = Ski.type.bodyBold.copy(fontSize = 17.sp), color = c.ink, modifier = Modifier.heightIn(min = 44.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp, c.rule)).padding(vertical = 14.dp)) {
            Num(LiveNumbers.maxKmh(live?.maxKmh ?: st?.maxKmh), stringResource(R.string.app_rec_max), Modifier.weight(1f))
            Num(LiveNumbers.km(st?.skiDistM), stringResource(R.string.app_rec_dist), Modifier.weight(1f))
            Num(LiveNumbers.metres(st?.dropM), stringResource(R.string.app_rec_drop), Modifier.weight(1f))
            Num((st?.descents ?: 0).toString(), stringResource(R.string.app_rec_runs), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.border(BorderStroke(1.5.dp, c.ink), RectangleShape).clickable(role = Role.Button, onClick = onStop).heightIn(min = 52.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).background(c.ink))
                Text(stringResource(R.string.app_rec_stop), style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = c.ink)
            }
            Text(stringResource(R.string.app_rec_soon), Modifier.weight(1f), style = Ski.type.small, color = c.muted, textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun Choice(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ski.colors
    Box(modifier.background(if (on) c.ink else c.paper).border(BorderStroke(1.5.dp, c.ink)).clickable(role = Role.Tab, onClick = onClick).heightIn(min = 44.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.bodyBold, color = if (on) c.paper else c.ink)
    }
}

@Composable
private fun Num(value: String, label: String, modifier: Modifier) {
    val c = Ski.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Ski.type.title.copy(fontSize = 34.sp), color = c.ink, maxLines = 1)
        Text(label, style = Ski.type.small.copy(fontSize = 11.5.sp), color = c.muted, textAlign = TextAlign.Center)
    }
}

/** What the live view says you are doing: a guess ("probably"), in words. */
@Composable
private fun what(live: io.github.pini236.skiapp.dayrec.DayEngine.Snapshot?, liftName: (String) -> String): String {
    live ?: return ""
    val probably = stringResource(R.string.app_rec_probably)
    return when (live.act) {
        "lift" -> stringResource(R.string.app_rec_on_lift, live.lift?.let(liftName) ?: "")
        "ski" -> {
            val runs = live.runs.orEmpty()
            if (runs.isEmpty()) stringResource(R.string.app_rec_off) else probably + " " + stringResource(R.string.app_rec_on_run, runs.joinToString(" · "))
        }
        "walk", "up" -> stringResource(R.string.app_rec_walking)
        else -> stringResource(R.string.app_rec_standing)
    }
}

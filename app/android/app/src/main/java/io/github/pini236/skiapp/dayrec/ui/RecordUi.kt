package io.github.pini236.skiapp.dayrec.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

/**
 * What is in the way of recording (round 29, states), on a screen of its own and not a toast that is gone: no permission,
 * only the approximate location, or the phone's location switched off; with the way to the settings where it is fixed.
 */
@Composable
fun BlockedSheet(kind: String, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val c = Ski.colors
    val text = when (kind) { "precise" -> R.string.app_rec_err_precise; "off" -> R.string.app_rec_err_off; else -> R.string.app_rec_err_perm }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(c.paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.app_rec_perm_title), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = c.ink)
            PermRow(Icons.lock, stringResource(text))
            Row(Modifier.fillMaxWidth().background(c.blue).clickable(role = Role.Button, onClick = onSettings).heightIn(min = 52.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_rec_settings), style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = c.onBoard)
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
 * While the phone looks for its first reading (between the tap and the first dot) the numbers breathe: a soft pulse of their
 * opacity, so the screen is seen to be waiting and not to be saying "0". No new text, no change of layout or colour; it
 * stops (without a jump: a short fade to full) when the reading comes, and it is still when the phone asks for reduced
 * motion. Read inside the graphics layer, so it redraws the two blocks and recomposes nothing.
 */
@Composable
private fun searchPulse(searching: Boolean): () -> Float {
    val still = io.github.pini236.skiapp.ui.Motion.reduced(LocalContext.current)
    val settle = animateFloatAsState(if (searching || still) 0f else 1f, tween(250), label = "settle")
    val running = !still && (searching || settle.value < 1f)
    val pulse: State<Float> = if (running) rememberInfiniteTransition(label = "search").animateFloat(
        initialValue = .35f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    ) else remember { mutableStateOf(1f) }
    return { if (searching) pulse.value else pulse.value + (1f - pulse.value) * settle.value }
}

/**
 * The live screen: the speed now, what you are doing, four numbers, and "Finish". Read from [DayRec] once a second; the
 * recorder never waits for it. "Probably" on what you do (a causal guess); the timeline at the end of the day is the truth.
 */
@Composable
fun DayLiveScreen(liftName: (String) -> String, runName: (String) -> String = { it }, runs: io.github.pini236.skiapp.data.Runs? = null, onSwitch: (single: Boolean) -> Unit, onStop: () -> Unit, onEnded: (java.io.File?) -> Unit = {}, onBack: () -> Unit) {
    val c = Ski.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val live = DayRec.live
    val st = DayRec.sofar?.stats
    // the recorder's fields are plain ones, read when the second ticks: the choice just tapped is shown at once, and the recorder's word takes over when it agrees
    var picked by remember { mutableStateOf<Boolean?>(null) }
    val single = picked ?: DayRec.single
    LaunchedEffect(now) { if (picked == DayRec.single) picked = null }
    val recording = DayRec.recording
    // Stop in the notification (or anything else that ends the day outside this screen): the screen goes to the day that ended, as its own Stop does
    var wasRecording by remember { mutableStateOf(false) }
    var dayFile by remember { mutableStateOf<java.io.File?>(null) }
    DayRec.current?.let { dayFile = it }
    LaunchedEffect(recording) {
        if (LiveNumbers.endedOutside(wasRecording, recording, dayFile != null)) { wasRecording = false; onEnded(dayFile) }
        else if (recording) wasRecording = true
    }
    val pulse = searchPulse(LiveNumbers.searching(recording, DayRec.lastFixAt))
    // from the tap, not from the first reading: the counter neither sits on 0:00 while the phone finds its satellites nor goes back to it when they are found
    val elapsed = LiveNumbers.sinceTap(now, DayRec.startedAt)
    val red = Color(0xFFE5322D)
    // the day so far under the numbers (round 29, "the map under the live screen"): read from the log every few seconds, off the main thread
    var track by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            DayRec.current?.let { f -> track = withContext(Dispatchers.IO) { runCatching { readTrack(f, 900) }.getOrNull() } }
            delay(5000)
        }
    }
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            BackLink(stringResource(R.string.nav_home), onBack)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(stringResource(R.string.app_rec_all), !single, Modifier.weight(1f)) { if (single) { picked = false; onSwitch(false) } }
            Choice(stringResource(R.string.app_rec_one), single, Modifier.weight(1f)) { if (!single) { picked = true; onSwitch(true) } }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recording) RecDot(Modifier.size(14.dp))
            Text(stringResource(R.string.app_rec_now, LiveNumbers.elapsed(elapsed)), style = Ski.type.bodyBold, color = red)
        }
        Row(Modifier.graphicsLayer { alpha = pulse() }, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(LiveNumbers.kmh(DayRec.speedNow), style = Ski.type.title.copy(fontSize = 120.sp), color = c.ink)
            Text(stringResource(R.string.app_rec_unit), Modifier.padding(bottom = 18.dp), style = Ski.type.number.copy(fontSize = 28.sp), color = c.ink)
        }
        Text(what(live, liftName, runName), style = Ski.type.bodyBold.copy(fontSize = 17.sp), color = c.ink, modifier = Modifier.heightIn(min = 44.dp))
        if (recording && LiveNumbers.weakGps(now, DayRec.lastFixAt, DayRec.startedAt)) Text(stringResource(R.string.app_rec_gps_weak), style = Ski.type.small, color = red)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().graphicsLayer { alpha = pulse() }.border(BorderStroke(1.dp, c.rule)).padding(vertical = 14.dp)) {
            Num(LiveNumbers.maxKmh(live?.maxKmh ?: st?.maxKmh), stringResource(R.string.app_rec_max), Modifier.weight(1f))
            Num(LiveNumbers.km(st?.skiDistM), stringResource(R.string.app_rec_dist), Modifier.weight(1f))
            Num(LiveNumbers.metres(st?.dropM), stringResource(R.string.app_rec_drop), Modifier.weight(1f))
            Num((st?.descents ?: 0).toString(), stringResource(R.string.app_rec_runs), Modifier.weight(1f))
        }
        track?.takeIf { it.x.size > 1 }?.let { t -> Spacer(Modifier.height(12.dp)); DayMap(t, runs, null, null, height = 200.dp, showPin = false) }
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
private fun what(live: io.github.pini236.skiapp.dayrec.DayEngine.Snapshot?, liftName: (String) -> String, runName: (String) -> String): String {
    live ?: return ""
    val probably = stringResource(R.string.app_rec_probably)
    return when (live.act) {
        "lift" -> stringResource(R.string.app_rec_on_lift, live.lift?.let(liftName) ?: "")
        "ski" -> {
            val runs = live.runs.orEmpty()
            if (runs.isEmpty()) stringResource(R.string.app_rec_off) else probably + " " + stringResource(R.string.app_rec_on_run, runs.joinToString(" · ") { runName(it) })
        }
        "walk", "up" -> stringResource(R.string.app_rec_walking)
        else -> stringResource(R.string.app_rec_standing)
    }
}

/** A day the phone cut short (it closed the app, or switched off) found on opening: go on with it, or close it (round 29, states). */
@Composable
fun CutDayDialog(onResume: () -> Unit, onClose: () -> Unit) {
    val c = Ski.colors
    Dialog(onDismissRequest = {}) {
        Column(Modifier.fillMaxWidth().background(c.paper).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.app_rec_cut_title), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = c.ink)
            Text(stringResource(R.string.app_rec_cut_text), style = Ski.type.body, color = c.ink)
            Row(Modifier.fillMaxWidth().background(c.blue).clickable(role = Role.Button, onClick = onResume).heightIn(min = 52.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_rec_cut_go), style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = c.onBoard)
            }
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClose).heightIn(min = 52.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_rec_cut_close), style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = c.glacier)
            }
        }
    }
}

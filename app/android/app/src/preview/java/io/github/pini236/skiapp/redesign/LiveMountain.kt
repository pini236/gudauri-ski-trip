package io.github.pini236.skiapp.redesign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.pini236.skiapp.qa.Qa

/**
 * Phase B, the first sketch: the mountain as a live model with the free camera of today's map, in the new look (the
 * light of the hour in the shader, one detail texture, the haze of the valleys), with the runs and lifts on it and a
 * tap that lights a run. The hour slider moves the sun along 12 January, as on the painted picture.
 */
@Composable
fun LiveMountain(scene: MapScene, mode: DayNight.Mode, haptics: Haptics, onPainted: () -> Unit, onLeave: () -> Unit) {
    val context = LocalContext.current
    val data by produceState<PeakData?>(null) { value = withContext(Dispatchers.IO) { PeakData.load(context) } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var scrubOpen by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    val time = scrub?.let { PeakLight.janAt(it) } ?: DayNight.lightTime(now, mode)
    val night = remember(time) { Sky.at(time).dark }
    val view = remember { LiveView(context, scene).also { v -> v.onChoose = { k -> chosen = k; haptics.tick(.5f); Qa.log("redesign live chosen $k") } } }
    DisposableEffect(Unit) {
        val life = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> view.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> view.onResume()
                else -> {}
            }
        }
        life?.addObserver(obs)
        Qa.log("redesign live open")
        onDispose { life?.removeObserver(obs); view.release(); Qa.log("redesign live closed") }
    }
    // the emulator's pictures (qa/run.sh): an hour of 12 January (the designer's keys), and a view
    LaunchedEffect(RedesignQa.seq) {
        val c = RedesignQa.cmd
        when {
            c.startsWith("hour:") -> c.removePrefix("hour:").toFloatOrNull()?.let { scrub = it; Qa.log("redesign live hour $it") }
            c.startsWith("look:") -> c.removePrefix("look:").split(",").let { a ->
                val n = a.take(3).mapNotNull { it.toFloatOrNull() }
                if (n.size == 3) view.look(n[0], n[1], n[2], a.getOrNull(3)?.takeIf { it.isNotBlank() })
            }
            // a run chosen the way a tap chooses it (the turn to the Kobi side included), letting it go, and the sign
            c.startsWith("pick:") -> view.pickKey(c.removePrefix("pick:"))
            c == "unpick" -> { chosen = null; view.choose(null); Qa.log("redesign live unpicked") }
            c == "front" -> view.backToFront()
        }
    }
    LaunchedEffect(time) { view.setTime(time) }
    // the "Kobi side" sign shows while the camera looks from within 60 degrees of the Kobi side (the handoff's section 10)
    var kobiSide by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { val k = view.onKobiSide(); if (k != kobiSide) { kobiSide = k; Qa.log("redesign live kobi sign $k") }; delay(250) } }
    LaunchedEffect(data, night) { data?.let { view.setPal(it.pal[if (night) "night" else "day"]) } }

    val bg = if (night) Color(0xD70F1828) else Color(0xDBFFFFFF)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val line = if (night) Color(0x33FFFFFF) else Color(0x220E2233)
    val acc = if (night) Color(0xFFFFD873) else Color(0xFF6B4FD8)
    @Composable fun Pill(modifier: Modifier = Modifier, h: Int = 44, content: @Composable () -> Unit) {
        Box(modifier.height(h.dp).shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp)).background(bg).border(1.dp, line, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { content() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(Modifier.weight(1f).clickable { scrubOpen = !scrubOpen; if (!scrubOpen) scrub = null; haptics.tick(.3f) }) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (night) Color(0xFFFFD873) else Color(0xFFF2B33D)))
                        val sv = scrub
                        val t = PeakLight.clock(sv ?: PeakLight.localHour(time))
                        Text(if (sv != null || scrubOpen) stringResource(R.string.poc_peak_light_at, t) else stringResource(R.string.poc_peak_light_now, t),
                            fontFamily = Plex, fontSize = 14.sp, color = ink, maxLines = 1)
                    }
                }
                Pill(Modifier.clickable { haptics.tick(.3f); onPainted() }) {
                    Text(stringResource(R.string.poc_peak_painted), Modifier.padding(horizontal = 14.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
                Pill(Modifier.clickable { haptics.tick(.3f); onLeave() }) {
                    Text(stringResource(R.string.poc_peak_leave), Modifier.padding(horizontal = 14.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
            }
            AnimatedVisibility(scrubOpen, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut() + slideOutVertically { -it / 3 }) {
                Pill(Modifier.fillMaxWidth(), h = 52) {
                    Slider(value = scrub ?: PeakLight.localHour(time).coerceIn(7f, 23.5f), onValueChange = { scrub = it }, valueRange = 7f..23.5f,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc, inactiveTrackColor = line))
                }
            }
            Text(stringResource(R.string.poc_peak_live_badge), Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0x66081A33)).padding(horizontal = 10.dp, vertical = 3.dp),
                fontFamily = Plex, fontSize = 11.5.sp, color = Color.White)
            AnimatedVisibility(kobiSide, enter = fadeIn(), exit = fadeOut()) {
                val aria = stringResource(R.string.poc_peak_front_aria)
                Pill(Modifier.semantics { contentDescription = aria }.clickable { haptics.tick(.3f); view.backToFront() }) {
                    Text(stringResource(R.string.map_kobi_side), Modifier.padding(horizontal = 16.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
                }
            }
        }
        // the chosen run's name, at the bottom; a tap on it lets it go
        val run = chosen?.let { k -> scene.runs.pistes.firstOrNull { it.key == k } }
        AnimatedVisibility(run != null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp), enter = fadeIn(), exit = fadeOut()) {
            var last by remember { mutableStateOf(run) }
            if (run != null) last = run
            last?.let { r ->
                val c = data?.pal?.get(if (night) "night" else "day")?.let { p ->
                    when (r.color) { "green" -> p.green; "blue" -> p.blue; "red" -> p.red; "black" -> p.black; else -> ink }
                } ?: ink
                Pill(Modifier.clickable { chosen = null; view.choose(null) }, h = 48) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                        Text(r.name.ifBlank { r.key }, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink)
                        Text("✕", fontSize = 14.sp, color = ink.copy(alpha = .6f))
                    }
                }
            }
        }
        AnimatedVisibility(chosen == null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp), enter = fadeIn(), exit = fadeOut()) {
            Pill(h = 48) { Text(stringResource(R.string.poc_peak_hint), Modifier.padding(horizontal = 20.dp), fontFamily = Plex, fontSize = 15.sp, color = ink) }
        }
    }
}

package io.github.pini236.skiapp.dayrec.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.map.MapView
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the day was at a time: the position (metres, as the map's) and the speed (km/h) between the two readings around it. */
internal object ReplayAt {
    class Spot(val x: Float, val y: Float, val kmh: Double)

    /** [t] clamped to the day; null for a day with no readings. A gap in the recording holds the last reading still. */
    fun spot(tr: Track, t: Long): Spot? {
        val n = tr.t.size
        if (n == 0) return null
        if (t <= tr.t[0]) return Spot(tr.x[0], tr.y[0], tr.kmh[0])
        if (t >= tr.t[n - 1]) return Spot(tr.x[n - 1], tr.y[n - 1], tr.kmh[n - 1])
        var lo = 0; var hi = n - 1
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (tr.t[m] <= t) lo = m else hi = m }
        val dt = tr.t[hi] - tr.t[lo]
        if (dt > 90_000L) return Spot(tr.x[lo], tr.y[lo], 0.0) // a hole in the recording: still, not a line across it
        val f = ((t - tr.t[lo]).toFloat() / dt).coerceIn(0f, 1f)
        return Spot(tr.x[lo] + (tr.x[hi] - tr.x[lo]) * f, tr.y[lo] + (tr.y[hi] - tr.y[lo]) * f, tr.kmh[lo] + (tr.kmh[hi] - tr.kmh[lo]) * f)
    }

    /** The day, 300 times faster than it was lived: a day of eight hours is a minute and a half. */
    const val RATE = 300L
}

/**
 * The day again on the 3D map (round 29, "replay"): a dot goes along your line with the time and the speed, and the camera after
 * it. The shared 3D map is the one the map screen uses; nothing leaves the phone.
 */
@Composable
fun ReplayScreen(view: MapView, file: File, onBack: () -> Unit) {
    val c = Ski.colors
    val track by produceState<Track?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { readTrack(file, 1500) }.getOrNull() } }
    DisposableEffect(view) {
        view.select(null); view.unmark()
        onDispose { view.unmark() }
    }
    val tr = track
    val t0 = tr?.t?.firstOrNull() ?: 0L
    val t1 = tr?.t?.lastOrNull() ?: 0L
    var at by remember(tr) { mutableFloatStateOf(0f) } // ms from the start
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing, tr) {
        var last = System.nanoTime()
        while (playing && tr != null) {
            delay(100)
            val now = System.nanoTime()
            at += ((now - last) / 1_000_000L) * ReplayAt.RATE
            last = now
            if (at >= (t1 - t0)) { at = (t1 - t0).toFloat(); playing = false }
        }
    }
    val spot = tr?.let { ReplayAt.spot(it, t0 + at.toLong()) }
    var lookedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(spot?.x, spot?.y) {
        spot ?: return@LaunchedEffect
        view.mark(spot.x, spot.y)
        val now = System.currentTimeMillis()
        if (now - lookedAt > 600) { lookedAt = now; view.lookAt(spot.x, spot.y) } // the camera follows, not on every reading
    }
    val zone = ZoneId.of(DayParams.TZ)
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_day_replay), Modifier.weight(1f), style = Ski.type.title, color = c.ink)
            BackLink(stringResource(R.string.nav_home), onBack)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.paper).border(BorderStroke(1.dp, c.rule)).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(LiveNumbers.maxKmh(spot?.kmh), style = Ski.type.title.copy(fontSize = 56.sp), color = c.ink)
                Text(stringResource(R.string.app_rec_unit), Modifier.padding(bottom = 10.dp), style = Ski.type.number.copy(fontSize = 18.sp), color = c.ink)
                Text(if (tr != null) DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(t0 + at.toLong()).atZone(zone)) else "",
                    Modifier.weight(1f).padding(bottom = 10.dp), style = Ski.type.bodyBold, color = c.muted, textAlign = TextAlign.End)
            }
            Slider(value = at, onValueChange = { at = it; playing = false }, valueRange = 0f..maxOf(1f, (t1 - t0).toFloat()), enabled = tr != null)
            Text(stringResource(if (playing) R.string.app_dayrec_stop else R.string.app_day_replay_play), Modifier.fillMaxWidth().border(BorderStroke(1.5.dp, c.ink))
                .clickable(role = Role.Button, enabled = tr != null) { if (at >= (t1 - t0)) at = 0f; playing = !playing }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                textAlign = TextAlign.Center, style = Ski.type.bodyBold, color = c.ink)
        }
    }
}

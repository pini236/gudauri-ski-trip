package io.github.pini236.skiapp.map

import androidx.compose.foundation.background
import io.github.pini236.skiapp.telemetry.Telemetry
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import io.github.pini236.skiapp.i18n.Lang
import androidx.compose.ui.platform.LocalContext
import io.github.pini236.skiapp.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.ui.Palette
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.SkiTheme
import io.github.pini236.skiapp.group.Sheet
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.status.StatusBar
import io.github.pini236.skiapp.status.StatusBoard
import androidx.compose.foundation.layout.statusBarsPadding
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt

/** What the map shows of the lift status (13.4, S1 to S3), and the board's sheet. */
class MapStatus(
    val status: LiftStatus, val changes: List<Pair<String, Boolean>>, val inSeason: Boolean,
    val forMe: Boolean, val onForMe: (Boolean) -> Unit, val sheet: Boolean, val onSheet: (Boolean) -> Unit,
)

/**
 * The 3D map screen: the GL mountain with a run sign and the fly-down button on top, and the lift status: its bar at
 * the top (S1), and a tap on it opens the board (S2) or the snowy signs (S3), as in the site's map panel.
 */
@Composable
fun MapScreen(view: MapView, scene: MapScene?, ms: MapStatus? = null) {
    var selected by remember { mutableStateOf<Piste?>(view.selected) }
    var flying by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }
    var flyStart by remember { mutableStateOf(0L) }
    DisposableEffect(view) {
        view.onSelect = { selected = it }
        view.onFlying = { now ->
            if (flying && !now) selected?.let {
                Telemetry.event("run_fly_end", mapOf("run" to it.key, "completed" to !stopped, "seconds" to (System.currentTimeMillis() - flyStart) / 1000))
            }
            flying = now
        }
        onDispose { view.onSelect = null; view.onFlying = null }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
        if (scene == null) {
            Text(stringResource(R.string.app_map_loading), Modifier.align(Alignment.Center), fontFamily = Ski.type.text, fontSize = 16.sp, color = Palette.ink)
        }
        val p = selected
        if (p != null) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (flying) FlyBar(view, p.name)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // the run's sign: its colour and its name, square corners like the trail signs
                    Box(Modifier.background(Palette.run(p.color)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(p.name, fontFamily = Ski.type.display, fontWeight = FontWeight.Bold, fontSize = (30 * Ski.type.displayScale).sp, color = Color.White)
                    }
                    Spacer1()
                    Button(stringResource(if (flying) R.string.run_fly_stop else R.string.run_fly_button)) {
                        if (flying) { stopped = true; view.stopFly() }
                        else { stopped = false; flyStart = System.currentTimeMillis(); view.flyDown(); Telemetry.event("run_fly_start", mapOf("run" to p.key)) }
                    }
                    Button("✕", stringResource(R.string.app_close)) { view.select(null) }
                }
            }
        } else if (scene != null) {
            Text(stringResource(R.string.app_map_hint),
                Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color(0xE6FFFFFF)).padding(10.dp),
                fontFamily = Ski.type.text, fontSize = 13.sp, color = Palette.ink)
        }
        // the map keeps its day colours (the spike), and so do the bar and the board over it
        if (ms != null && scene != null) SkiTheme(dark = false) {
            Box(Modifier.fillMaxSize()) {
                if (!flying) StatusBar(ms.status, { ms.onSheet(true) }, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp))
                if (ms.sheet) Sheet({ ms.onSheet(false) }) {
                    StatusBoard(ms.status, ms.changes, ms.forMe, ms.onForMe, ms.inSeason)
                }
            }
        }
    }
}

@Composable private fun Spacer1() = Box(Modifier.size(0.dp))

/** While flying down: where the skier is, as on the site (6.4.1): the run, distance, height and slope. */
@Composable
private fun FlyBar(view: MapView, name: String) {
    var line by remember { mutableStateOf("") }
    val res = LocalContext.current.resources
    val nf = remember { NumberFormat.getIntegerInstance(Lang.current(res).locale) }
    LaunchedEffect(view) {
        while (true) {
            view.flyInfo()?.let { f -> line = res.getString(R.string.run_fly_hud_readout, nf.format(f[0].toInt()), nf.format(f[2].toInt()), f[3].roundToInt()) }
            delay(100)
        }
    }
    Row(Modifier.background(Color(0xE613233A)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontFamily = Ski.type.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        Text("  ·  $line", fontFamily = Ski.type.text, fontSize = 15.sp, color = Color.White)
    }
}

@Composable
fun Button(label: String, description: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).let { m -> if (description != null) m.semantics { contentDescription = description } else m }.background(Palette.ink).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Ski.type.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White) }
}

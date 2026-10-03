package io.github.pini236.skiapp.map

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.navigationBarsPadding
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.data.RunFacts
import io.github.pini236.skiapp.data.Video
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * The 3D map screen: the GL mountain, and over it the map's panel (13.3): a chosen run (T1 to T4), a lift, or the list
 * of all runs with the filters (map/RunSheet.kt); the fly-down bar; and the lift status: its bar at the top (S1), and a
 * tap on it opens the board (S2) or the snowy signs (S3), as in the site's map panel.
 */
@Composable
fun MapScreen(view: MapView, scene: MapScene?, ms: MapStatus? = null, videos: List<Video> = emptyList()) {
    var selected by remember { mutableStateOf<Piste?>(view.selected) }
    var flying by remember { mutableStateOf(view.flying) }
    var lift by remember { mutableStateOf<Lift?>(view.shownLift.also { view.shownLift = null }) }
    var list by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(emptySet<String>()) }
    DisposableEffect(view) {
        view.onSelect = { p -> selected = p; if (p != null) { lift = null; list = false }; expanded = false; view.unmark() }
        view.onFlying = { now -> flying = now } // run_fly_start and run_fly_end are sent by the map (MapView.flyDown)
        view.onLift = { l -> if (lift?.id != l.id) { lift = l; list = false; Qa.log("lift open ${l.name}") } }
        onDispose { view.onSelect = null; view.onFlying = null; view.onLift = null }
    }
    // the filters: a colour hides its runs, "unnamed" the sections without a name, "lifts" the lifts
    LaunchedEffect(hidden, scene) {
        val runs = scene?.runs ?: return@LaunchedEffect
        view.setHidden(runs.pistes.filter { it.color in hidden || (!it.named && "unnamed" in hidden) }.map { it.key }.toSet(), "lifts" !in hidden)
    }
    val order = remember(scene) { scene?.runs?.pistes?.let { RunFacts.order(it) } ?: emptyList() }
    val facts by produceState<RunFacts?>(null, selected?.key, scene) {
        val p = selected; val s = scene
        value = if (p == null || s == null) null else withContext(Dispatchers.Default) { RunFacts.of(s.terrain, p) }
    }
    val actions = remember(view, scene) {
        PanelActions(
            goRun = { key, via -> scene?.runs?.pistes?.firstOrNull { it.key == key }?.let { view.select(it, via = via) } },
            goLift = { l ->
                view.select(null); lift = l; list = false; Qa.log("lift open ${l.name}")
                if (l.id.isNotBlank()) view.showLift(l.id) // which calls onLift: the panel is already this lift's
            },
            fly = { if (selected != null) { expanded = false; view.unmark(); view.flyDown() } },
            stopFly = { view.stopFly() },
            mark = { x, y -> view.mark(x, y) },
            unmark = { view.unmark() },
        )
    }
    // a tap on a lift's line, as on a connection's tag; lift_open however the panel opened (a tap, a tag, the meeting point)
    DisposableEffect(view, actions) { view.onLiftTap = { l -> actions.goLift(l) }; onDispose { view.onLiftTap = null } }
    LaunchedEffect(lift) { lift?.let { l -> Telemetry.event("lift_open", if (l.name.isBlank()) emptyMap() else mapOf("lift" to l.name)) } }
    // the part of the screen above the panel, where the camera frames the run or the lift
    val onPanel: (Int) -> Unit = remember(view) { { h -> if (view.height > 0) view.setFreeBottom(1f - h.toFloat() / view.height - 0.03f) } }
    LaunchedEffect(selected == null && lift == null && !list) { if (selected == null && lift == null && !list) view.setFreeBottom(1f) }
    BackHandler(enabled = selected != null || lift != null || list) {
        when { selected != null -> view.select(null); lift != null -> lift = null; else -> list = false }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
        if (scene == null) {
            Text(stringResource(R.string.app_map_loading), Modifier.align(Alignment.Center), fontFamily = Ski.type.text, fontSize = 16.sp, color = Palette.ink)
        }
        // the map keeps its day colours (the spike), and so do the panel, the bar and the board over it
        if (scene != null) SkiTheme(dark = false) {
            Box(Modifier.fillMaxSize()) {
                val p = selected; val l = lift
                when {
                    p != null -> {
                        MapPanel(expanded, { expanded = it; Qa.log("panel ${if (it) "open" else "closed"}") }, { view.select(null) }, onHeight = onPanel,
                            head = {
                                if (flying) Box(Modifier.padding(bottom = 8.dp)) { FlyBar(view, runName(p)) }
                                RunHead(p, facts, order, flying, true, actions)
                            },
                            body = { RunBody(p, facts, scene.runs, scene.terrain, videos, actions) })
                    }
                    l != null -> MapPanel(true, {}, { lift = null }, head = { LiftHead(l) }, body = { LiftBody(l, scene.runs, scene.terrain, actions) }, bodyMax = 0.45f, onHeight = onPanel)
                    list -> MapPanel(true, {}, { list = false },
                        head = { Text(stringResource(R.string.map_overview_heading), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = Ski.colors.ink) },
                        body = {
                            RunList(scene.runs, order, hidden, { k ->
                                val on = k in hidden
                                hidden = if (on) hidden - k else hidden + k
                                Telemetry.event("map_filter", mapOf("filter" to k, "on" to on))
                                Qa.log("filter $k ${if (on) "on" else "off"}")
                            }, scene.runs.fetched, scene.runs.researchDate, actions)
                        }, bodyMax = 0.5f, onHeight = onPanel)
                    else -> Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.app_map_hint), Modifier.weight(1f).background(Color(0xE6FFFFFF)).padding(10.dp),
                            fontFamily = Ski.type.text, fontSize = 13.sp, color = Palette.ink)
                        Button(stringResource(R.string.map_overview_heading)) { list = true; Qa.log("run list open") }
                    }
                }
                if (ms != null) {
                    if (!flying) StatusBar(ms.status, { ms.onSheet(true) }, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp))
                    if (ms.sheet) Sheet({ ms.onSheet(false) }) {
                        StatusBoard(ms.status, ms.changes, ms.forMe, ms.onForMe, ms.inSeason)
                    }
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

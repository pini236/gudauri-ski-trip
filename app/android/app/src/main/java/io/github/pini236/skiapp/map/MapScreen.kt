package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.weather.WeatherList
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.runtime.SideEffect
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
/** The emulator run's switch between the views (qa.mapview), read once by the map screen. */
object MapMode {
    var qa by mutableStateOf<String?>(null)
    /** The map is seen from above now (the frame counter has nothing to count then). */
    var top by mutableStateOf(false)
}

@Composable
fun MapScreen(view: MapView, scene: MapScene?, ms: MapStatus? = null, videos: List<Video> = emptyList(),
              /** Night (the day and night switch): the panel, the bar and the board go dark, as on the site (A-11). */
              dark: Boolean = false,
              /** The map from above (A-30): the relief under it, read on demand ([askRelief]) as for the meeting point. */
              relief: io.github.pini236.skiapp.meet.Relief2D? = null, askRelief: () -> Unit = {}) {
    var selected by remember { mutableStateOf<Piste?>(view.selected) }
    var flying by remember { mutableStateOf(view.flying) }
    var lift by remember { mutableStateOf<Lift?>(view.shownLift.also { view.shownLift = null }) }
    var list by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(emptySet<String>()) }
    // the view: 3D or from above (A-30), kept on the phone as the site keeps it in the browser
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("map", android.content.Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(prefs.getString("view", "3d") ?: "3d") }
    fun setMode(m: String, measure: Boolean) {
        if (m == mode) return
        mode = m; prefs.edit().putString("view", m).apply(); Qa.log("map view $m")
        if (measure) Telemetry.event("map_view", mapOf("view" to m))
    }
    LaunchedEffect(MapMode.qa) { MapMode.qa?.let { setMode(it, false); MapMode.qa = null } }
    val top = mode == "2d"
    SideEffect { MapMode.top = top }
    // the relief: the map from above draws on it, and in 3D the contours, village, roads and water lie on the snow (A-16)
    LaunchedEffect(Unit) { askRelief() }
    LaunchedEffect(relief, view) { relief?.let { view.setEnv(it) } }
    val ov = remember { io.github.pini236.skiapp.meet.MeetView() }
    val scope = rememberCoroutineScope()
    val art = remember(scene) { scene?.let { OverviewArt(it.runs, it.terrain) } }
    // "where am I" (round 19): only in the test build until Pini approves the privacy line (decision 58)
    val where = remember(scene) {
        if (io.github.pini236.skiapp.BuildConfig.LOCATION && scene != null) WhereAmI(context.applicationContext, Locator(scene.runs, scene.terrain), scene.terrain) else null
    }
    var asking by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) where?.start() else where?.refused()
    }
    // the button goes off in the background and when the map closes (m-7 section 5)
    DisposableEffect(where) {
        val w = where
        val life = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        if (w == null || life == null) return@DisposableEffect onDispose {}
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) w.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) w.pause()
        }
        life.addObserver(obs)
        onDispose { life.removeObserver(obs); w.pause() }
    }
    fun locate() {
        val w = where ?: return
        when {
            w.state != WhereAmI.State.Off -> w.off()
            w.anyPermission -> w.start()
            else -> asking = true
        }
    }
    fun whereAction() {
        val w = where ?: return
        when (w.state) {
            WhereAmI.State.Denied -> runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)))
            }
            WhereAmI.State.Unavailable -> runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            // approximate: ask for the precise location (Android 12's upgrade dialog)
            else -> askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val glacier = Ski.colors.glacier.toArgb(); val paperArgb = Ski.colors.paper.toArgb()
    val here = where?.state as? WhereAmI.State.Fixed
    LaunchedEffect(here, top) {
        val s = here
        if (s == null || top || s.fix == Locator.Fix.Outside) view.hideMe() else view.showMe(s.x, s.y, s.accuracy, s.bearing, glacier, paperArgb)
    }
    // the weather layer (round 19, and-map-weather): off until tapped; on, the three points on the mountain and the list
    val weather = io.github.pini236.skiapp.weather.LocalWeather.current
    var weatherOn by rememberSaveable { mutableStateOf(false) }
    var weatherSheet by remember { mutableStateOf(false) }
    fun toggleWeather() {
        weatherOn = !weatherOn; weatherSheet = weatherOn
        if (weatherOn) Telemetry.event("weather_view", mapOf("where" to "map", "state" to weather.measure))
        Qa.log("weather layer ${if (weatherOn) "on " + weather.measure else "off"}")
    }
    val pins = if (weatherOn) io.github.pini236.skiapp.weather.pinTexts(weather) else emptyList()
    val pinKey = pins.joinToString("|") { it.title + it.temp + it.wind + it.snow + it.dir }
    LaunchedEffect(pinKey, top, scene) {
        view.setWeatherPins(if (top) emptyList() else pins.map { MapView.Pin(it.x, it.y, it.title, it.temp, it.wind, it.snow, it.dir) })
    }
    // the first reading brings the camera to the dot, once each time the button goes on
    var looked by remember { mutableStateOf(false) }
    LaunchedEffect(where?.state) {
        val s = where?.state
        if (s == WhereAmI.State.Off) looked = false
        if (s is WhereAmI.State.Fixed && !looked && s.fix != Locator.Fix.Outside) {
            looked = true
            if (top) ov.goTo(scope, s.x, s.y, minOf(ov.span, 2500f), 600) else view.lookAt(s.x, s.y)
        }
    }
    var marker2d by remember { mutableStateOf<FloatArray?>(null) }
    var panelFrac by remember { mutableStateOf(0f) }
    var fitted by remember { mutableStateOf(false) }
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
    val ovPaint by produceState<OverviewPaint?>(null, selected?.key, scene, top) {
        val p = selected; val s = scene
        value = if (!top || p == null || s == null) null else withContext(Dispatchers.Default) { OverviewPaint.of(s, p) }
    }
    // from above: the whole main side when the view opens, and a chosen run framed above the panel (the site's fit and focusOn)
    ov.still = io.github.pini236.skiapp.ui.Motion.reduced(context)
    LaunchedEffect(top, ov.ready, art) {
        val a = art ?: return@LaunchedEffect
        if (top && ov.ready && !fitted && selected == null) { ov.frame(null, a.main.left, a.main.top, a.main.right, a.main.bottom, 1.12f, 0f, 1f, 0); fitted = true; Qa.log("overview fit") }
    }
    LaunchedEffect(selected?.key, top, ov.ready, panelFrac > 0f) {
        val p = selected ?: return@LaunchedEffect
        if (!top || !ov.ready) return@LaunchedEffect
        var a = Float.MAX_VALUE; var b = Float.MAX_VALUE; var c = -Float.MAX_VALUE; var d = -Float.MAX_VALUE
        for (l in p.lines) for (i in 0 until l.size / 2) { a = minOf(a, l[i * 2]); c = maxOf(c, l[i * 2]); b = minOf(b, l[i * 2 + 1]); d = maxOf(d, l[i * 2 + 1]) }
        if (a > c) return@LaunchedEffect
        ov.frame(scope, a, b, c, d, 1.5f, 900f, 1f - panelFrac, 700); fitted = true; Qa.log("overview frame ${p.key}")
    }
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
            // the flight is the 3D map's: from above, the map turns to 3D first
            fly = { if (selected != null) { expanded = false; view.unmark(); marker2d = null; setMode("3d", true); view.flyDown() } },
            stopFly = { view.stopFly() },
            mark = { x, y -> view.mark(x, y); marker2d = floatArrayOf(x, y) },
            unmark = { view.unmark(); marker2d = null },
            flyAt = { view.flyInfo()?.get(0) },
        )
    }
    // a tap on a lift's line, as on a connection's tag; lift_open however the panel opened (a tap, a tag, the meeting point)
    DisposableEffect(view, actions) { view.onLiftTap = { l -> actions.goLift(l) }; onDispose { view.onLiftTap = null } }
    LaunchedEffect(lift) { lift?.let { l -> Telemetry.event("lift_open", if (l.name.isBlank()) emptyMap() else mapOf("lift" to l.name)) } }
    // the part of the screen above the panel, where the camera frames the run or the lift
    val onPanel: (Int) -> Unit = remember(view) { { h -> if (view.height > 0) { view.setFreeBottom(1f - h.toFloat() / view.height - 0.03f); panelFrac = (h.toFloat() / view.height + .03f).coerceIn(0f, .7f) } } }
    LaunchedEffect(selected == null && lift == null && !list) { if (selected == null && lift == null && !list) { view.setFreeBottom(1f); panelFrac = 0f } }
    BackHandler(enabled = selected != null || lift != null || list) {
        when { selected != null -> view.select(null); lift != null -> lift = null; else -> list = false }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = Modifier.fillMaxSize())
        if (top && scene != null && art != null) {
            val res = context.resources
            val lang = Lang.current(res)
            val body = remember(lang) { Lang.typeface(context, lang, display = false) }
            val words = remember(lang) {
                val nf = NumberFormat.getIntegerInstance(lang.locale)
                OverviewWords(res.getString(R.string.map_kobi_side_label), res.getString(R.string.map_place_gudauri),
                    { n, e -> res.getString(R.string.app_peak_label, "\u2068$n\u2069", nf.format(e)) },
                    { p -> if (p.key == "Firni ?") "Firni (1/2?)" else p.key }, res.getString(R.string.map_map_aria))
            }
            val hiddenKeys = remember(hidden, scene) { scene.runs.pistes.filter { it.color in hidden || (!it.named && "unnamed" in hidden) }.map { it.key }.toSet() }
            SkiTheme(dark = dark) {
                Overview(ov, art, relief, Ski.colors, body, words, selected, ovPaint, hiddenKeys, "lifts" in hidden, ms?.status, ms?.forMe == true,
                    marker2d, ov.still,
                    onRun = { p -> view.select(p, via = "map") },
                    onLift = { l -> actions.goLift(l) },
                    onEmpty = {},
                    onKobi = { ov.frame(scope, art.kobi.left, art.kobi.top, art.kobi.right, art.kobi.bottom, 1.3f, 900f, 1f - panelFrac, 700); Qa.log("overview kobi") },
                    modifier = Modifier.fillMaxSize())
            }
        }
        if (top && pins.isNotEmpty() && ov.ready) SkiTheme(dark = dark) { io.github.pini236.skiapp.weather.MapPins2D(ov, pins, Modifier.fillMaxSize()) }
        if (top && here != null && here.fix != Locator.Fix.Outside && ov.ready) SkiTheme(dark = dark) { MeDot2D(ov, here, Modifier.fillMaxSize()) }
        if (scene == null) {
            Text(stringResource(R.string.app_map_loading), Modifier.align(Alignment.Center), fontFamily = Ski.type.text, fontSize = 16.sp, color = Palette.ink)
        }
        // the panel, the bar and the board over the map in the app's colours: dark at night, as the site's map
        if (scene != null) SkiTheme(dark = dark) {
            Box(Modifier.fillMaxSize()) {
                val p = selected; val l = lift
                when {
                    p != null -> {
                        MapPanel(expanded, { expanded = it; Qa.log("panel ${if (it) "open" else "closed"}") }, { view.select(null) }, onHeight = onPanel,
                            head = {
                                if (flying) Box(Modifier.padding(bottom = 8.dp)) { FlyBar(view, runName(p)) }
                                RunHead(p, facts, order, flying, true, actions)
                            },
                            body = { RunBody(p, facts, scene.runs, scene.terrain, videos, actions, flying) })
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
                    else -> Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // "where am I" at the start and the weather at the end, above the line (and-map-buttons)
                        if (!flying) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                            if (where != null) LocateKey(where.on, ::locate) else Box(Modifier)
                            if (io.github.pini236.skiapp.BuildConfig.WEATHER) io.github.pini236.skiapp.weather.WeatherKey(weatherOn, ::toggleWeather)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val words = where?.let { whereWords(it.state) }
                            if (words != null) WhereLine(words, ::whereAction, Modifier.weight(1f))
                            else Text(stringResource(R.string.app_map_hint), Modifier.weight(1f).background(Color(0xE6FFFFFF)).padding(10.dp),
                                fontFamily = Ski.type.text, fontSize = 13.sp, color = Palette.ink)
                            Button(stringResource(R.string.map_overview_heading)) { list = true; Qa.log("run list open") }
                        }
                    }
                }
                // with a panel open, the button sits above it
                if (where != null && !flying && (p != null || l != null || list) && panelFrac > 0f)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(panelFrac.coerceIn(.05f, .9f))) {
                        LocateKey(where.on, ::locate, Modifier.align(Alignment.TopStart).offset(y = (-64).dp).padding(start = 12.dp))
                    }
                MapControls(top, flying, { m -> setMode(m, true) },
                    zoomIn = { ov.zoomAt(scope, 1f / 1.5f, null, null, 300) }, zoomOut = { ov.zoomAt(scope, 1.5f, null, null, 300) },
                    fit = { art?.let { a -> ov.frame(scope, a.main.left, a.main.top, a.main.right, a.main.bottom, 1.12f, 0f, 1f - panelFrac, 600) } },
                    kobi = { art?.let { a -> ov.frame(scope, a.kobi.left, a.kobi.top, a.kobi.right, a.kobi.bottom, 1.3f, 900f, 1f - panelFrac, 700); Qa.log("overview kobi") } },
                    north = { view.north() }, metresPerDp = if (ov.ready) 1f / ov.k * context.resources.displayMetrics.density else 0f,
                    bottom = panelFrac)
                if (ms != null) {
                    if (!flying) StatusBar(ms.status, { ms.onSheet(true) }, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp))
                    if (ms.sheet) Sheet({ ms.onSheet(false) }) {
                        StatusBoard(ms.status, ms.changes, ms.forMe, ms.onForMe, ms.inSeason)
                    }
                }
                if (weatherSheet) Sheet({ weatherSheet = false }) {
                    WeatherList(weather) { pt ->
                        weatherSheet = false
                        val (la, lo) = io.github.pini236.skiapp.weather.DEFAULT_LL.getValue(pt.id)
                        val x = io.github.pini236.skiapp.data.Geo.x(lo); val y = io.github.pini236.skiapp.data.Geo.y(la)
                        if (top) ov.goTo(scope, x, y, minOf(ov.span, 3000f), 600) else view.lookAt(x, y)
                        Qa.log("weather point ${pt.id}")
                    }
                }
                if (asking) AskSheet(onGo = {
                    asking = false
                    askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }, onLater = { asking = false })
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

/**
 * The map's controls, as the site's (A-30): the view switch (3D, from above) at the top; from above, zoom in, out and
 * the whole map, the Kobi side and a scale; in 3D, the compass, which turns the camera back to look north.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MapControls(
    top: Boolean, flying: Boolean, onMode: (String) -> Unit, zoomIn: () -> Unit, zoomOut: () -> Unit, fit: () -> Unit, kobi: () -> Unit,
    north: () -> Unit, metresPerDp: Float, bottom: Float,
) {
    if (flying) return
    val c = Ski.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    @Composable fun Key(label: String, description: String, on: Boolean = false, onClick: () -> Unit) {
        Box(Modifier.size(44.dp).background(if (on) c.ink else c.paper.copy(alpha = .94f), shape)
            .border(1.dp, c.rule, shape).clickable(onClick = onClick).semantics { contentDescription = description },
            contentAlignment = Alignment.Center) { Text(label, fontFamily = Ski.type.text, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (on) c.paper else c.ink) }
    }
    val typeLabel = stringResource(R.string.map_view_type)
    Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.semantics { contentDescription = typeLabel }.background(c.paper.copy(alpha = .94f), shape).border(1.dp, c.rule, shape)) {
            for ((m, label) in listOf("3d" to stringResource(R.string.map_view_3d), "2d" to stringResource(R.string.map_view_top))) {
                val on = (m == "2d") == top
                Box(Modifier.heightIn(min = 44.dp).background(if (on) c.ink else Color.Transparent, shape).clickable { onMode(m) }
                    .semantics { contentDescription = label + if (on) " ✓" else "" }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontFamily = Ski.type.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) c.paper else c.ink)
                }
            }
        }
        if (top) {
            val kobiLabel = stringResource(R.string.map_kobi_side)
            Box(Modifier.heightIn(min = 44.dp).background(c.paper.copy(alpha = .94f), shape).border(1.dp, c.rule, shape).clickable(onClick = kobi)
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(12.dp).background(c.blue))
                    Text(kobiLabel, fontFamily = Ski.type.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
                }
            }
            // the scale: the round length that is at least 70 dp long (the site's)
            if (metresPerDp > 0f) {
                val m = listOf(50, 100, 200, 250, 500, 1000, 2000).firstOrNull { it / metresPerDp >= 70f } ?: 2000
                Column(Modifier.background(c.paper.copy(alpha = .85f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Box(Modifier.size(width = (m / metresPerDp).dp, height = 3.dp).background(c.ink))
                    Text(if (m >= 1000) "\u2066${m / 1000} km\u2069" else "\u2066$m m\u2069", fontFamily = Ski.type.text, fontSize = 11.sp, color = c.ink)
                }
            }
        }
    }
    Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 112.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (top) {
            Key("+", stringResource(R.string.map_zoom_in), onClick = zoomIn)
            Key("−", stringResource(R.string.map_zoom_out), onClick = zoomOut)
            Key("⤢", stringResource(R.string.map_zoom_fit), onClick = fit)
        } else {
            // the site's compass: red to the north, ink to the south
            val label = stringResource(R.string.map_compass)
            Box(Modifier.size(44.dp).background(c.paper.copy(alpha = .94f), shape).border(1.dp, c.rule, shape).clickable(onClick = north)
                .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                    val w = size.width; val h = size.height
                    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, h * .1f); lineTo(w * .7f, h / 2); lineTo(w * .3f, h / 2); close() }, c.red)
                    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, h * .9f); lineTo(w * .7f, h / 2); lineTo(w * .3f, h / 2); close() }, c.ink)
                }
            }
        }
    }
}

package io.github.pini236.skiapp

import android.content.Intent
import io.github.pini236.skiapp.telemetry.Telemetry
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.data.Profile
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.SiteData
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.game.DescentScreen
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.MapScreen
import io.github.pini236.skiapp.map.MapView
import io.github.pini236.skiapp.map.OrbitCamera
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.map.Sun
import io.github.pini236.skiapp.i18n.Lang
import androidx.compose.ui.res.stringResource
import io.github.pini236.skiapp.nav.Nav
import io.github.pini236.skiapp.nav.Route
import io.github.pini236.skiapp.perf.FrameStats
import io.github.pini236.skiapp.perf.Startup
import io.github.pini236.skiapp.qa.GesturePlayer
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ticket.TicketScreen
import io.github.pini236.skiapp.home.HomeActions
import io.github.pini236.skiapp.home.HomeScreen
import io.github.pini236.skiapp.trip.TripScreen
import io.github.pini236.skiapp.trip.TripStore
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Palette
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Feasibility spike (docs/APP-NATIVE.md, stage 13.1): the 3D map, the descent game and the boarding pass,
 * with a frame counter on top. Not the real app: just enough to measure what is risky.
 */
class MainActivity : ComponentActivity() {
    private companion object { const val SKY_EVERY_MS = 5 * 60_000L }
    private val uiStats = FrameStats()
    private val glStats = FrameStats()
    private var refreshHz = 60f
    private lateinit var haptics: Haptics
    private lateinit var sounds: Sounds
    private lateinit var mapView: MapView
    private val metricsThread = HandlerThread("frame-metrics").apply { start() }
    private val loader = Executors.newSingleThreadExecutor()
    private lateinit var siteData: SiteData

    private var scene by mutableStateOf<MapScene?>(null)
    private var profile by mutableStateOf<Profile?>(null)
    private var sunNote by mutableStateOf("")
    private lateinit var nav: Nav
    private lateinit var trips: TripStore
    private var trip by mutableStateOf<io.github.pini236.skiapp.trip.Trip?>(null)
    private var showStats by mutableStateOf(true)
    private var qaPending: Intent? = null
    private var clockMs: Long? = null // the QA run pins the time of day; otherwise it is now

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the app opens on the home page (round 10, H1 to H4); the map, the game and the spike's pass are places on top
        nav = Nav(Route.Home, savedInstanceState?.getStringArrayList("nav"))
        trips = TripStore(this)
        trip = trips.load()
        Qa.init(this)
        startTelemetry()
        enableEdgeToEdge()
        preferTopRefreshRate()
        haptics = Haptics(this)
        sounds = Sounds(this)
        mapView = MapView(this, refreshHz, glStats)
        mapView.onChosen = { p ->
            if (nav.top is Route.Map) nav.replaceTop(Route.Map(p?.key))
            if (p != null) Telemetry.event("run_open", mapOf("run" to p.key, "color" to p.color, "via" to "map"))
        }
        @Suppress("DEPRECATION")
        val version = if (Build.VERSION.SDK_INT >= 28) packageManager.getPackageInfo(packageName, 0).longVersionCode else packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
        siteData = SiteData(java.io.File(filesDir, "site-data"), version, { name -> assets.open("data/$name").bufferedReader().use { it.readText() } })

        if (Build.VERSION.SDK_INT >= 26) {
            window.addOnFrameMetricsAvailableListener({ _, m, _ ->
                uiStats.onFrame(m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP), m.getMetric(FrameMetrics.TOTAL_DURATION))
            }, Handler(metricsThread.looper))
        }
        val start = Process.getStartUptimeMillis()
        window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (Startup.firstFrameMs < 0) Startup.firstFrameMs = SystemClock.uptimeMillis() - start
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        handleQa(intent)
        loadInBackground()
        setContent { App() }
    }

    /** Usage and crashes (telemetry/Telemetry.kt), with the properties every event carries, then app_open. */
    private fun startTelemetry() {
        val lang = Lang.current(resources)
        val manual = if (Build.VERSION.SDK_INT >= 33) !getSystemService(android.app.LocaleManager::class.java).applicationLocales.isEmpty else Lang.chosen(this) != null
        Telemetry.start(this, BuildConfig.FLAVOR, Telemetry.Common(
            appVersion = BuildConfig.VERSION_NAME,
            build = if (BuildConfig.DEBUG) "debug" else if (BuildConfig.FLAVOR == "preview") "test" else "store",
            lang = lang.tag,
            langSource = if (manual) "manual" else "auto",
            theme = "auto", // no day-and-night choice in the app yet
            deviceClass = if (resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone",
        ))
        val link = intent?.data != null
        Telemetry.event("app_open", mapOf("source" to if (link) "link" else "direct", "cold" to true))
    }

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putStringArrayList("nav", nav.save())
        Qa.log("state saved ${nav.save()}")
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleQa(intent) }

    private fun preferTopRefreshRate() {
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        display ?: return
        val cur = display.mode
        val best = display.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }.maxByOrNull { it.refreshRate } ?: cur
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
        refreshHz = best.refreshRate
    }

    private fun loadInBackground() = loader.execute {
        val t0 = SystemClock.uptimeMillis()
        fun asset(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        val terrain = Terrain.parse(asset("data/terrain.json"))
        val runs = Runs.parse(siteData.read("runs-and-lifts.json"))
        Qa.log("data runs from ${siteData.source("runs-and-lifts.json")}")
        val profiles = Profile.parse(asset("data/profiles.json"))
        Startup.dataMs = SystemClock.uptimeMillis() - t0
        runOnUiThread { profile = profiles.firstOrNull { it.key == "Tatra 2" } ?: profiles.firstOrNull() }
        val t1 = SystemClock.uptimeMillis()
        val s = MapScene(terrain, runs)
        Startup.meshMs = SystemClock.uptimeMillis() - t1
        val note = placeSun(s, clockMs ?: System.currentTimeMillis())
        runOnUiThread {
            sunNote = note
            scene = s; mapView.setScene(s); reportFullyDrawn()
            // back where the user was: the run chosen before the system closed the app
            nav.find<Route.Map>()?.run?.let { key -> s.runs.pistes.firstOrNull { it.key == key }?.let { mapView.select(it); Qa.log("restored run $key") } }
            Qa.log("scene ready")
        }
        castShadows(s)
        runOnUiThread { qaPending?.let { qaPending = null; applyQaMap(it, sunDone = true) }; mapView.postDelayed(skyTick, SKY_EVERY_MS) }
        // new runs, lifts and videos from the site, if there are any: read at the next launch (data/SiteData.kt)
        val refreshed = siteData.refresh()
        Qa.log("data refresh ${refreshed.entries.joinToString { "${it.key} ${it.value}" }}")
    }

    /** The sun moves: light and shadows again every few minutes (unless the QA run pinned the time). */
    private val skyTick: Runnable = object : Runnable {
        override fun run() {
            val s = scene ?: return
            if (clockMs == null) loader.execute { val note = placeSun(s, System.currentTimeMillis()); runOnUiThread { sunNote = note }; castShadows(s) }
            mapView.postDelayed(this, SKY_EVERY_MS)
        }
    }

    /** Light: the sky over Gudauri at that time, as on the site: the sun by day, the moon at night (map/Sky.kt). */
    private fun placeSun(s: MapScene, timeMs: Long): String {
        s.light = Sky.at(timeMs)
        return s.light.note
    }

    private fun castShadows(s: MapScene) {
        val t2 = SystemClock.uptimeMillis()
        s.shadow = Sun.shadows(s.terrain, s.light.dir)
        Startup.shadowMs = SystemClock.uptimeMillis() - t2
        runOnUiThread {
            mapView.updateShadow()
            Qa.log("shadow ready · first frame ${Startup.firstFrameMs} · data ${Startup.dataMs} · mesh ${Startup.meshMs} · shadow ${Startup.shadowMs} ms")
        }
    }

    // ---- test hooks, debug builds only (app/android/qa/README.md): the emulator run drives the app with "qa." extras ----
    private fun handleQa(i: Intent?) {
        if (i == null || !Qa.enabled) return
        val keys = i.extras?.keySet()?.filter { it.startsWith("qa.") }.orEmpty()
        if (keys.isEmpty()) return
        i.getStringExtra("qa.tab")?.let { when (it) { "descent" -> nav.switchTo(Route.Game("descent")); "ticket" -> nav.switchTo(Route.Ticket); "trip" -> nav.switchTo(Route.Trip); "home" -> nav.toStart(); else -> nav.switchTo(nav.find<Route.Map>() ?: Route.Map()) } }
        // a sample trip for the home page's screenshots, or none (the guest home, H1)
        i.getStringExtra("qa.trip")?.let { t -> if (t == "none") { trips.clear(); trip = null } else { trip = Qa.sampleTrip(); trips.save(trip!!) }; Qa.log("trip $t") }
        i.getStringExtra("qa.stats")?.let { showStats = it != "off" }
        i.getStringExtra("qa.time")?.let { clockMs = Qa.gudauriTime(it) }
        Qa.log("intent ${keys.sorted()}")
        if (scene == null) qaPending = i else applyQaMap(i) // the rest needs the mountain
    }

    private fun applyQaMap(i: Intent, sunDone: Boolean = false) {
        val s = scene ?: return
        if (i.hasExtra("qa.time") && !sunDone) {
            val ms = clockMs ?: return
            loader.execute { val note = placeSun(s, ms); runOnUiThread { sunNote = note }; castShadows(s) }
        }
        i.getStringExtra("qa.face")?.let { body ->
            // turn to the sun or the moon, almost level, to see it over the ridges (after the new time's light is in)
            loader.execute {
                runOnUiThread {
                    val d = if (body == "moon") s.light.moonDir else s.light.sunDir
                    val st = mapView.camera.state()
                    mapView.look(st.copy(yaw = -kotlin.math.atan2(d[0], -d[2]), pitch = io.github.pini236.skiapp.map.Moves.MIN_PITCH))
                    Qa.log("facing $body")
                }
            }
        }
        i.getStringExtra("qa.cam")?.let { c ->
            val v = Qa.parseCamera(c) ?: return@let Qa.log("bad camera $c")
            mapView.look(OrbitCamera.State(v[0], s.terrain.elev(v[0], v[1]), v[1], v[2], Math.toRadians(v[3].toDouble()).toFloat(), Math.toRadians(v[4].toDouble()).toFloat()))
        }
        i.getStringExtra("qa.run")?.let { key ->
            if (key == "none") mapView.select(null)
            else s.runs.pistes.firstOrNull { it.key == key || it.name == key }?.let { mapView.select(it) } ?: Qa.log("no run $key")
        }
        if (i.getBooleanExtra("qa.fly", false)) mapView.postDelayed({ mapView.flyDown() }, 1500)
        i.getStringExtra("qa.gesture")?.let { GesturePlayer.play(mapView, it) }
    }

    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onDestroy() { super.onDestroy(); mapView.removeCallbacks(skyTick); sounds.release(); mapView.release(); metricsThread.quitSafely(); loader.shutdownNow() }

    @Composable
    private fun App() {
        BackHandler(enabled = nav.canBack) { nav.back() }
        // screen_view with the contract's screen names (docs/GROWTH.md); a chosen run is run_open, not a screen.
        // The spike's pass has no screen of its own in the contract (the pass lives on the home screen).
        val screen = when (val r = nav.top) { Route.Home -> "home"; Route.Trip -> "trip"; is Route.Map -> "map"; is Route.Game -> "game:" + r.name; else -> null }
        LaunchedEffect(screen) {
            if (screen != null) Telemetry.event("screen_view", if (screen.startsWith("game:")) mapOf("screen" to "game", "game" to screen.removePrefix("game:")) else mapOf("screen" to screen))
        }
        // the direction of the language on screen (i18n/Lang.kt): Hebrew today, so right to left
        CompositionLocalProvider(LocalLayoutDirection provides Lang.current(resources).direction) {
            Box(Modifier.fillMaxSize().background(Palette.snow)) {
                when (val r = nav.top) {
                    Route.Home -> HomeScreen(trip, haptics, sounds, HomeActions(
                        addTrip = { nav.push(Route.Trip) }, editTrip = { nav.push(Route.Trip) },
                        map = { nav.push(nav.find<Route.Map>() ?: Route.Map()) }, games = { nav.push(Route.Game("descent")) },
                    ), clockMs)
                    Route.Trip -> TripScreen(trip,
                        onSave = { t -> trips.save(t); trip = t; nav.back(); haptics.click(0.5f) },
                        onDelete = { trips.clear(); trip = null; nav.back() },
                        onCancel = { nav.back() })
                    is Route.Map -> Box(Modifier.fillMaxSize().navigationBarsPadding()) { MapScreen(mapView, scene); if (showStats) StatsBar(true, Modifier.align(Alignment.TopStart)) }
                    is Route.Game -> Box(Modifier.fillMaxSize().navigationBarsPadding()) { DescentScreen(profile, haptics, sounds); if (showStats) StatsBar(false, Modifier.align(Alignment.TopStart)) }
                    Route.Ticket -> Box(Modifier.statusBarsPadding().padding(top = 28.dp)) { TicketScreen(haptics, sounds) }
                    else -> { LaunchedEffect(r) { nav.toStart() } } // a place the app does not have yet: home
                }
            }
        }
    }

    /** The spike's instruments: frames per second, missed frames, refresh rate and start-up times. */
    @Composable
    private fun StatsBar(gl: Boolean, modifier: Modifier) {
        var line by remember { mutableStateOf("") }
        LaunchedEffect(gl) {
            var n = 0
            while (true) {
                val now = System.nanoTime()
                val st = (if (gl) glStats else uiStats).snapshot(refreshHz, now)
                val frames = if (st.active) "${st.fps.toInt()} פריימים · החמצות ${"%.1f".format(st.jankPct)}% · הגרוע ${st.worstMs.toInt()} מ״ש" else "במנוחה"
                val extra = if (gl) " · ${if (mapView.msaa) "החלקה 4×" else "בלי החלקה"} · $sunNote" else ""
                line = "${refreshHz.toInt()}Hz · $frames$extra\nפתיחה ${Startup.firstFrameMs} · נתונים ${Startup.dataMs} · רשת ${Startup.meshMs} · צל ${Startup.shadowMs} מ״ש · ${haptics.level}"
                if (n++ % 4 == 0) Qa.log("stats ${if (gl) "map" else "ui"} · ${line.replace('\n', ' ')}")
                delay(500)
            }
        }
        Text(line, modifier.statusBarsPadding().padding(4.dp).background(Color(0xB313233A)).padding(horizontal = 6.dp, vertical = 2.dp),
            fontFamily = Plex, fontSize = 11.sp, lineHeight = 14.sp, color = Color.White)
    }
}

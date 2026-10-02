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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.Instant
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.github.pini236.skiapp.home.AboutScreen
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.home.HomeAction
import io.github.pini236.skiapp.home.HomeScreen
import io.github.pini236.skiapp.home.SoonScreen
import io.github.pini236.skiapp.account.AccountScreen
import io.github.pini236.skiapp.account.GoogleSignIn
import io.github.pini236.skiapp.group.CodeScreen
import io.github.pini236.skiapp.group.DevServer
import io.github.pini236.skiapp.group.GroupApi
import io.github.pini236.skiapp.group.GroupEntryScreen
import io.github.pini236.skiapp.group.GroupHub
import io.github.pini236.skiapp.group.GroupScreen
import io.github.pini236.skiapp.group.GroupTab
import io.github.pini236.skiapp.group.InviteCode
import io.github.pini236.skiapp.group.InviteScreen
import io.github.pini236.skiapp.group.InvitedScreen
import io.github.pini236.skiapp.group.NewGroupScreen
import io.github.pini236.skiapp.group.LiveGroupApi
import io.github.pini236.skiapp.server.PrefsSessionStore
import io.github.pini236.skiapp.server.Server
import io.github.pini236.skiapp.server.ServerTripSync
import io.github.pini236.skiapp.group.ReclaimScreen
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.trip.TripForm
import io.github.pini236.skiapp.trip.TripStore
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.SkiTheme
import io.github.pini236.skiapp.ui.Palette
import io.github.pini236.skiapp.ui.Plex
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * The app (docs/APP-NATIVE.md): it opens on the home page (round 10, H1 to H4) with the user's own trip and the post of
 * signs; the signs lead to the 3D map and the descent game of the feasibility spike (13.1), and to the places still
 * to come. Debug and test builds keep the spike's frame counter on the map and the game.
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
    private var showStats by mutableStateOf(true)
    private var qaPending: Intent? = null
    private var clockMs: Long? = null // the QA run pins the time of day; otherwise it is now
    private fun nowMs() = clockMs ?: System.currentTimeMillis()

    // the user's own trip (decision 27), kept only on this phone
    private lateinit var trips: TripStore
    private var trip by mutableStateOf<Trip?>(null)
    // day and night (6.3, N1): the choice is kept; auto follows the clock in Gudauri
    private var dnMode by mutableStateOf(DayNight.Mode.AUTO)
    private var tick by mutableStateOf(0L)
    // accounts and the group (13.5): the real server (server/), or the pretend one of debug builds for the emulator run.
    // My trip goes to the server only once this phone has a session there (it joined or made a group).
    private val server by lazy { Server.of(this) }
    private val tripSync by lazy { ServerTripSync(this, server) }
    private val accountPrefs by lazy { getSharedPreferences("account", MODE_PRIVATE) }
    private val groupApi: GroupApi by lazy {
        DevServer.create() ?: LiveGroupApi(server, PrefsSessionStore(this), tripSync, { Lang.current(resources).tag },
            ready = GoogleSignIn.WEB_CLIENT_ID.isNotEmpty(), saved = accountPrefs.getString("me", null),
            keep = { v -> accountPrefs.edit().apply { if (v == null) remove("me") else putString("me", v) }.apply() })
    }
    private var justJoined by mutableStateOf(false)

    /** Google's sheet, then the server. A debug build without the web client id signs in to the pretend server directly. */
    private suspend fun signInGoogle() {
        if (BuildConfig.DEBUG && GoogleSignIn.WEB_CLIENT_ID.isEmpty() && groupApi.ready) { groupApi.signInWithGoogle("dev", "dev"); return }
        val g = GoogleSignIn.signIn(this)
        groupApi.signInWithGoogle(g.idToken, g.nonce)
    }

    /** "Your trip": on the phone, and on the server when this phone has a session there (server/TripSync.kt). */
    private fun keepTrip(t: Trip?) {
        if (t == null) trips.clear() else trips.save(t)
        trip = t
        if (groupApi is LiveGroupApi) tripSync.pushed(t)
    }

    private fun openPrivacy() = startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://${Route.SITE_HOST}/privacy?utm_source=app")))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nav = Nav(Route.Home, savedInstanceState?.getStringArrayList("nav"))
        Qa.init(this)
        trips = TripStore(this)
        trip = trips.load()
        dnMode = getSharedPreferences("daynight", MODE_PRIVATE).getString("mode", null)?.let { m -> DayNight.Mode.entries.firstOrNull { it.name == m } } ?: DayNight.Mode.AUTO
        tick = nowMs()
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
        if (groupApi is LiveGroupApi) tripSync.flush() // a trip saved without signal goes now
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
            theme = dnMode.name.lowercase(),
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
        // the group's starting point on the pretend server: none (a stranger), member (a guest in a group), admin
        i.getStringExtra("qa.group")?.let { DevServer.seed(groupApi, it); accountPrefs.edit().clear().apply(); justJoined = false; Qa.log("group seed $it") }
        i.getStringExtra("qa.tab")?.let { t -> when (t) {
            "map" -> nav.switchTo(Route.Map(nav.find<Route.Map>()?.run)); "descent" -> nav.switchTo(Route.Game("descent")); "trip" -> nav.switchTo(Route.Trip)
            "home" -> nav.toStart()
            else -> Route.parse(t)?.let { nav.toStart(); nav.push(it) } ?: Qa.log("bad tab $t")
        } }
        i.getStringExtra("qa.stats")?.let { showStats = it != "off" }
        i.getStringExtra("qa.time")?.let { clockMs = Qa.gudauriTime(it); tick = nowMs() }
        i.getStringExtra("qa.sentry")?.let { Telemetry.testCrashReport(it); loader.execute { Telemetry.flush(); Qa.log("telemetry ${if (Telemetry.hasKeys) "keys" else "no keys"}, flushed") } }
        i.getStringExtra("qa.mode")?.let { m -> DayNight.Mode.entries.firstOrNull { it.name.equals(m, true) }?.let { dnMode = it } }
        // a trip for the run, from the script (never packed in the app: decision 27), or "none" for the guest's home
        i.getStringExtra("qa.trip")?.let { t -> if (t == "none") { trips.clear(); trip = null } else Trip.fromJson(t)?.let { trips.save(it); trip = it } ?: Qa.log("bad trip $t"); Qa.log("trip ${if (trip == null) "none" else "set"}") }
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
        // the clock: Gudauri's hour for the sky and the countdown, once every half minute (or pinned by the QA run)
        LaunchedEffect(Unit) { while (true) { delay(30_000); tick = nowMs() } }
        val frame = remember(tick, dnMode) { DayNight.at(tick, dnMode) }
        val top = nav.top
        // screen_view with the contract's screen names (docs/GROWTH.md); a chosen run is run_open, not a screen.
        // The group is not in the contract yet, so it sends none.
        val screen = when (top) {
            Route.Home -> "home"; is Route.Map -> "map"; is Route.Meet -> "meet"; Route.Games -> "games"; Route.About -> "about"; Route.Trip -> "trip"
            is Route.Game -> "game:" + top.name; else -> null
        }
        LaunchedEffect(screen) {
            if (screen != null) Telemetry.event("screen_view", if (screen.startsWith("game:")) mapOf("screen" to "game", "game" to screen.removePrefix("game:")) else mapOf("screen" to screen))
        }
        // the whole app goes dark at night, as the site does; the spike's map and game keep their day colours
        val spike = top is Route.Map || top is Route.Game
        val view = LocalView.current
        SideEffect { WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = spike || !frame.dark }
        SkiTheme(dark = frame.dark) {
            Box(Modifier.fillMaxSize().background(if (spike) Palette.snow else Ski.colors.snow)) {
                when (top) {
                    Route.Home -> HomeScreen(trip, frame, dnMode, LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()), haptics, sounds,
                        onMode = {
                            dnMode = dnMode.next(); haptics.tick(.4f)
                            getSharedPreferences("daynight", MODE_PRIVATE).edit().putString("mode", dnMode.name).apply()
                            Telemetry.event("theme_set", mapOf("mode" to dnMode.name.lowercase()))
                        },
                        go = { a ->
                            haptics.tick(.4f)
                            nav.push(when (a) {
                                HomeAction.MAP, HomeAction.STATUS -> Route.Map(nav.find<Route.Map>()?.run)
                                HomeAction.MEET -> Route.Meet()
                                HomeAction.GAMES -> Route.Game("descent") // the games page arrives with stage 13.6
                                HomeAction.GROUP -> Route.Group()
                                HomeAction.ABOUT -> Route.About
                                HomeAction.TRIP -> Route.Trip
                            })
                        })
                    Route.Trip -> TripForm(trip, LocalDate.now(), onSave = { t -> keepTrip(t); Qa.log("trip saved"); nav.back() },
                        onDelete = { keepTrip(null); Qa.log("trip deleted"); nav.back() }, onCancel = { nav.back() })
                    is Route.Meet -> SoonScreen(stringResource(R.string.nav_meet), stringResource(R.string.app_soon_meet)) { nav.back() }
                    is Route.Group -> if (!groupApi.ready) SoonScreen(stringResource(R.string.app_sign_group), stringResource(R.string.app_soon_group)) { nav.back() }
                        else if (top.id == null) GroupHub(groupApi, onGroup = { id -> nav.replaceTop(Route.Group(id, GroupTab.FLIGHTS.key)) }) {
                            GroupEntryScreen(groupApi, onBack = { nav.back() },
                                onCode = { c -> nav.push(if (InviteCode.isCode(c)) Route.Join(c) else Route.JoinCode) },
                                onCreate = { nav.push(Route.GroupNew) }, signInGoogle = ::signInGoogle, onPrivacy = ::openPrivacy)
                        }
                        else GroupScreen(groupApi, top.id, GroupTab.of(top.tab), frame, trip, LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()),
                            justJoined = justJoined, saveOffers = accountPrefs.getInt("save_offers", 0),
                            stationName = { key -> scene?.runs?.lifts?.firstOrNull { it.id.isNotEmpty() && it.id == key.dropLast(1) }?.name },
                            onTab = { nav.replaceTop(Route.Group(top.id, it.key)) }, onBack = { nav.toStart() },
                            onInvite = { nav.push(Route.GroupInvite(top.id)) }, onNewMeetup = { nav.push(Route.Meet()) }, onEditTrip = { nav.push(Route.Trip) },
                            onMyTrip = { t -> keepTrip(t) }, onLeft = { nav.toStart() }, signInGoogle = ::signInGoogle,
                            onSaveOffered = { justJoined = false; accountPrefs.edit().putInt("save_offers", accountPrefs.getInt("save_offers", 0) + 1).apply() })
                    Route.GroupNew -> NewGroupScreen(groupApi, trip, LocalDate.now(), onCancel = { nav.back() },
                        onCreated = { id -> nav.back(); if (nav.top is Route.Group) nav.replaceTop(Route.Group(id, GroupTab.FLIGHTS.key)) else nav.push(Route.Group(id, GroupTab.FLIGHTS.key)) })
                    is Route.GroupInvite -> InviteScreen(groupApi, top.id) { nav.back() }
                    Route.JoinCode -> CodeScreen("", onBack = { nav.back() }) { c -> nav.replaceTop(Route.Join(c)) }
                    is Route.Join -> InvitedScreen(groupApi, top.code, frame, dnMode, onMode = { dnMode = dnMode.next() }, onAbout = { nav.push(Route.About) },
                        onBack = { nav.back() }, onTypeCode = { nav.replaceTop(Route.JoinCode) }, onReclaim = { nav.push(Route.Reclaim(top.code)) },
                        onJoined = { id, guest -> justJoined = guest; nav.toStart(); nav.push(Route.Group(id, GroupTab.FLIGHTS.key)) })
                    is Route.Reclaim -> ReclaimScreen(groupApi, top.code, onBack = { nav.back() }, signInGoogle = ::signInGoogle) { id ->
                        nav.toStart(); nav.push(Route.Group(id, GroupTab.FLIGHTS.key))
                    }
                    Route.Account -> AccountScreen(groupApi, onBack = { nav.back() }, signInGoogle = ::signInGoogle) { nav.toStart() }
                    Route.About -> AboutScreen(BuildConfig.VERSION_NAME, onPrivacy = ::openPrivacy,
                        onAccount = if (groupApi.ready) ({ nav.push(Route.Account) }) else null) { nav.back() }
                    else -> {
                        if (top is Route.Game) DescentScreen(profile, haptics, sounds) else MapScreen(mapView, scene)
                        if (showStats) StatsBar(top !is Route.Game, Modifier.align(Alignment.TopStart))
                        // the way home, over the mountain or the game
                        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(6.dp).background(Color(0xE6FFFFFF))) {
                            BackLink(stringResource(R.string.nav_home), { nav.back() }, Palette.glacier)
                        }
                    }
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

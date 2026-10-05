package io.github.pini236.skiapp

import android.content.Intent
import io.github.pini236.skiapp.telemetry.OpenSource
import io.github.pini236.skiapp.telemetry.Telemetry
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import java.time.ZoneId
import java.time.LocalDateTime
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
import io.github.pini236.skiapp.data.Video
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.game.Bests
import io.github.pini236.skiapp.game.DescentScreen
import io.github.pini236.skiapp.game.FreshSnowScreen
import io.github.pini236.skiapp.game.SchoolScreen
import io.github.pini236.skiapp.game.GamesScreen
import io.github.pini236.skiapp.game.MergeScreen
import io.github.pini236.skiapp.game.appGames
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
import io.github.pini236.skiapp.meet.Meet
import io.github.pini236.skiapp.meet.MeetPlan
import io.github.pini236.skiapp.meet.MeetSave
import io.github.pini236.skiapp.meet.MeetScreen
import io.github.pini236.skiapp.meet.Relief2D
import io.github.pini236.skiapp.meet.Preset
import io.github.pini236.skiapp.meet.Reminders
import androidx.compose.runtime.produceState
import io.github.pini236.skiapp.group.ErrorLine
import io.github.pini236.skiapp.group.rememberRunner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.json.JSONObject
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.status.Report
import io.github.pini236.skiapp.status.StatusSource
import io.github.pini236.skiapp.map.StatusPaint
import io.github.pini236.skiapp.map.MapStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import io.github.pini236.skiapp.group.MeasuredGroupApi
import io.github.pini236.skiapp.server.PrefsSessionStore
import io.github.pini236.skiapp.server.Server
import io.github.pini236.skiapp.server.Sync
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
    companion object {
        private const val SKY_EVERY_MS = 5 * 60_000L
        /** A place to open (a path of nav/Nav.kt), from the app's own notifications: a meetup's reminder (meet/Reminders.kt). */
        const val OPEN = "open"
        /** The app went to the background (not a turn of the screen): coming back is a warm app_open. */
        @Volatile private var backgrounded = false
    }
    /** How the app was brought back (onNewIntent), for the warm app_open. */
    private var warmSource: String? = null
    private val uiStats = FrameStats()
    private val glStats = FrameStats()
    private var refreshHz = 60f
    private lateinit var haptics: Haptics
    private lateinit var sounds: Sounds
    private lateinit var mapView: MapView
    private val metricsThread = HandlerThread("frame-metrics").apply { start() }
    private val loader = Executors.newSingleThreadExecutor()
    /**
     * The sky's tick waits on the main thread's own handler, not on the map's view: a view that is off the screen (the
     * home page is showing) cannot take back what it posted, and the tick then ran after onDestroy (GUDI-ANDROID-2).
     */
    private val ui = Handler(Looper.getMainLooper())

    /** Work for the loader thread from the main thread; none once the screen is gone (its loader is shut down). */
    private fun inBackground(work: () -> Unit) { if (!loader.isShutdown) loader.execute(work) }
    private lateinit var siteData: SiteData

    private var scene by mutableStateOf<MapScene?>(null)
    /** The meeting point's stations (from the same data as the map), and its top view, read the first time it opens. */
    private var meetPlan by mutableStateOf<MeetPlan?>(null)
    /** The runs' videos (site/data/videos-seed.json), for the run's panel (13.3). */
    private var videos by mutableStateOf(emptyList<Video>())
    private var relief by mutableStateOf<Relief2D?>(null)
    private var reliefAsked = false
    private var profiles by mutableStateOf<List<Profile>>(emptyList())
    private var sunNote by mutableStateOf("")
    private lateinit var nav: Nav
    // the frame counter of the feasibility check: development and test builds only, never the store build
    private var showStats by mutableStateOf(BuildConfig.DEBUG || BuildConfig.FLAVOR == "preview")
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
    /** The server (or, in debug builds, the pretend one) as is; [groupApi] is the same with the measuring events. */
    private val rawGroupApi: GroupApi by lazy {
        DevServer.create() ?: LiveGroupApi(server, PrefsSessionStore(this), tripSync, { Lang.current(resources).tag },
            ready = GoogleSignIn.WEB_CLIENT_ID.isNotEmpty(), saved = accountPrefs.getString("me", null),
            keep = { v -> accountPrefs.edit().apply { if (v == null) remove("me") else putString("me", v) }.apply() }, sync = Sync.of(this, Bests.GAMES))
    }
    private val groupApi: GroupApi by lazy { MeasuredGroupApi(rawGroupApi, getSharedPreferences("join_via", MODE_PRIVATE)) }
    private var justJoined by mutableStateOf(false)

    // the lift status (13.4, S1 to S3): the site's /api/status, read every five minutes while the app is open
    private val statusSource by lazy { StatusSource(this) }
    private var report by mutableStateOf<Report?>(null)
    private var statusLoaded by mutableStateOf(false)
    private var statusPinned = false // the emulator run's own report
    private var liftNames by mutableStateOf<List<String>>(emptyList())
    private var forMe by mutableStateOf(false)

    // the weather by altitude (round 19, m-6): the site's /api/weather, every ten minutes at most while the app is open
    private val weatherSource by lazy { io.github.pini236.skiapp.weather.WeatherSource(this) }
    private var forecast by mutableStateOf<io.github.pini236.skiapp.weather.Forecast?>(null)
    private var weatherPinned = false // the emulator run's own answer
    private var statusSheet by mutableStateOf(false)

    /**
     * Google's sheet, then the server. A debug build talks to the pretend server (DevServer), so it signs in there
     * directly: the emulator run never meets Google's real sheet, now that the web client id is set.
     */
    private suspend fun signInGoogle() {
        if (BuildConfig.DEBUG && groupApi.ready) { groupApi.signInWithGoogle("dev", "dev"); return }
        val g = GoogleSignIn.signIn(this)
        groupApi.signInWithGoogle(g.idToken, g.nonce)
        // a new phone (or the app installed again) with no trip yet: the trip this account keeps on the server comes back
        if (trip == null) runCatching { groupApi.myTripOnServer() }.getOrNull()?.let { trips.save(it); trip = it; Qa.log("trip restored") }
    }

    /** "Your trip": on the phone, and on the server when this phone has a session there (server/TripSync.kt). */
    private fun keepTrip(t: Trip?) {
        if (t == null) trips.clear() else trips.save(t)
        trip = t
        if (rawGroupApi is LiveGroupApi) tripSync.pushed(t)
    }

    /**
     * The privacy page in the app's language: #he, #en, #ru or #ka opens that article. Hebrew too: without a hash the
     * page goes by the browser's languages, and a browser without Hebrew opens English (decision 48).
     */
    private fun openPrivacy() {
        val tag = Lang.current(resources).tag
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://${Route.SITE_HOST}/privacy?utm_source=app#$tag")))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nav = Nav(Route.Home, savedInstanceState?.getStringArrayList("nav"))
        Qa.init(this)
        Qa.log("language ${Lang.current(resources).tag} · phone ${android.content.res.Resources.getSystem().configuration.locales.toLanguageTags()} · ${if (Lang.manual(this)) "chosen in the app" else "automatic"}")
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
        mapView.onChosen = { p, via ->
            if (nav.top is Route.Map) nav.replaceTop(Route.Map(p?.key))
            if (p != null) Telemetry.event("run_open", mapOf("run" to p.key, "color" to p.color, "via" to via))
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
        report = statusSource.cached()
        forecast = weatherSource.cached()
        handleQa(intent)
        handleOpen(intent)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { loadStatus(); delay(LiftStatus.EVERY_MS) } } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { loadWeather(); delay(io.github.pini236.skiapp.weather.WeatherSource.EVERY_MS) } } }
        // a trip, a score or a meetup saved without signal goes now
        if (rawGroupApi is LiveGroupApi) { tripSync.flush(); Sync.of(this, Bests.GAMES).flush() }
        loadInBackground()
        setContent { App() }
    }

    /** Usage and crashes (telemetry/Telemetry.kt), with the properties every event carries, then app_open. */
    private fun startTelemetry() {
        val lang = Lang.current(resources)
        val manual = Lang.manual(this)
        Telemetry.start(this, BuildConfig.FLAVOR, Telemetry.Common(
            appVersion = BuildConfig.VERSION_NAME,
            build = if (BuildConfig.DEBUG) "debug" else if (BuildConfig.FLAVOR == "preview") "test" else "store",
            lang = lang.tag,
            langSource = if (manual) "manual" else "auto",
            theme = dnMode.name.lowercase(),
            deviceClass = if (resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone",
        ))
        // a notification, a link (shared from the app or the site: share_link), or the first open of a Play install
        backgrounded = false
        val source = OpenSource.of(intent, OPEN)
        if (source != "direct") Telemetry.event("app_open", mapOf("source" to source, "cold" to true))
        else OpenSource.firstFromStore(this) { store ->
            Telemetry.event("app_open", (if (store == null) mapOf("source" to "direct") else mapOf<String, Any>("source" to "store") + store) + ("cold" to true))
        }
    }

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putStringArrayList("nav", nav.save())
        Qa.log("state saved ${nav.save()}")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        warmSource = OpenSource.of(intent, OPEN)
        handleQa(intent); handleOpen(intent)
    }

    /**
     * A reminder's tap opens its meetup's card, over home; a link to the site (a run, a meeting point, an invite) opens
     * the same place in the app. Each once: a turn of the screen does not open it again.
     */
    /** A run a link opened before the map was ready: chosen (and measured) when it is. */
    private var linkRun: String? = null

    private fun handleOpen(i: Intent?) {
        i?.data?.let { uri ->
            i.data = null
            val r = Route.fromSiteLink(uri.toString()) ?: return@let
            nav.toStart(); if (r != Route.Home) nav.push(r)
            if (r is Route.Meet && r.station != null) Telemetry.event("meet_link_open")
            // a run's link: chosen on the map now if it is ready, or as soon as it is; run_open with via=link (P-D2)
            if (r is Route.Map && r.run != null) {
                val p = scene?.runs?.pistes?.firstOrNull { it.key == r.run }
                if (p != null) mapView.select(p, via = "link") else linkRun = r.run
            }
        }
        val r = Route.parse(i?.getStringExtra(OPEN) ?: return) ?: return
        i.removeExtra(OPEN)
        nav.toStart(); nav.push(r)
    }

    /**
     * The reminders of every meetup of my groups (Q8), from what the phone keeps: armed again when the app opens, when a
     * group's meetups change, after a meetup is saved, and after signing out (then none are left).
     */
    /**
     * The games' high scores on this phone that the group's table has not had yet (the site's sendBests): when the app
     * opens home in a group, and after a new best. Through the queue, so no signal is fine.
     */
    private fun sendBests() {
        if (!groupApi.ready) return
        lifecycleScope.launch {
            for ((g, b) in Bests.unsent(this@MainActivity)) {
                if (runCatching { groupApi.submitBest(g, b) }.getOrDefault(false)) Bests.markSent(this@MainActivity, g, b) else break
            }
        }
    }

    private fun armReminders() {
        if (!groupApi.ready) return
        val plan = meetPlan ?: return
        lifecycleScope.launch {
            val all = runCatching { groupApi.allMeetups() }.getOrNull() ?: return@launch
            val items = all.map { (g, m) ->
                val at = m.at.atOffset(Meet.GUDAURI)
                Reminders.Item(m.id, m.at, plan.byId[m.station]?.name ?: m.station, g.name, "%02d:%02d".format(at.hour, at.minute),
                    Meet.route(m.station, at.toLocalTime(), at.toLocalDate()).path)
            }
            withContext(Dispatchers.IO) { Reminders.arm(this@MainActivity, items) }
        }
    }

    /** The flat relief, for the meeting point's map and the run map from above: read once, on its own thread (the loader may still be casting the map's shadows). */
    private fun askRelief() {
        if (reliefAsked) return
        reliefAsked = true
        Thread({ val r = Relief2D.parse(assets.open("data/terrain.json").bufferedReader().use { it.readText() }); runOnUiThread { relief = r; Qa.log("meet relief ready") } }, "meet-relief").start()
    }

    /** The lift status from the site, or the last good one on the phone (status/LiftStatus.kt). */
    private suspend fun loadStatus() {
        if (statusPinned) return
        val season = LiftStatus.inSeason(LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs()), Meet.GUDAURI).monthValue)
        val r = withContext(Dispatchers.IO) { statusSource.load(season) }
        if (statusPinned) return
        report = r; statusLoaded = true
        Qa.log("status ${if (r == null) "none" else "report"}")
    }

    /** The forecast from the site, or the last good one on the phone (weather/Weather.kt). */
    private suspend fun loadWeather() {
        if (weatherPinned) return
        val f = withContext(Dispatchers.IO) { weatherSource.load() }
        if (weatherPinned) return
        forecast = f
        Qa.log("weather ${f?.state(Instant.ofEpochMilli(nowMs()))?.name?.lowercase() ?: "none"}")
    }

    /** The emulator run's forecast (qa.weather): "fresh" (20 minutes old), "stale" (20 hours old) or "none"; never the site. */
    private fun pinWeather(kind: String) {
        val ago = if (kind == "stale") 20 * 3600_000L else 20 * 60_000L
        val f = if (kind == "none") null else io.github.pini236.skiapp.weather.Forecast.sample(Instant.ofEpochMilli(nowMs() - ago))
        weatherPinned = true
        weatherSource.pin(f?.json)
        forecast = f
        Qa.log("weather pinned $kind")
    }

    /**
     * The emulator run's report (qa.status): "fresh" (six minutes old: two lifts closed, Tatra 2 closed, and Goodaura
     * opened since the last look), "stale" (two hours old) or "none". The site is not read while it is pinned.
     */
    private fun pinStatus(kind: String) {
        val names = listOf("Snow Park", "Pirveli", "Sadzele", "Khada", "Goodaura", "Soliko", "Zuma", "Shino", "Kudebi", "Firni", "Kikilo", "New Goodaura")
        val shut = mapOf("Sadzele" to "wind", "Kudebi" to "maintenance")
        val ago = if (kind == "stale") 2 * 3600_000L else 6 * 60_000L
        val r = if (kind == "none") null else Report.parse(JSONObject()
            .put("updated", Instant.ofEpochMilli(nowMs() - ago).toString())
            .put("lifts", JSONObject().apply { for (n in names) put(n, JSONObject().put("open", n !in shut).apply { shut[n]?.let { put("reason", it) } }) })
            .put("pistes", JSONObject().put("Tatra 2", JSONObject().put("open", false)))
            .toString())
        statusPinned = true
        statusSource.pin(r, r?.let { names.associateWith { n -> n != "Goodaura" } })
        report = r; statusLoaded = true
        Qa.log("status pinned $kind")
    }

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
        runOnUiThread { this.profiles = profiles; liftNames = LiftStatus.names(runs.mainLifts) }
        val vids = runCatching { Video.parse(siteData.read("videos-seed.json")) }.getOrElse { Telemetry.handled(it); emptyList() }
        runOnUiThread { videos = vids }
        val plan = MeetPlan.build(runs, terrain)
        runOnUiThread { meetPlan = plan; armReminders() }
        val t1 = SystemClock.uptimeMillis()
        val s = MapScene(terrain, runs)
        Startup.meshMs = SystemClock.uptimeMillis() - t1
        val note = placeSun(s, clockMs ?: System.currentTimeMillis())
        runOnUiThread {
            sunNote = note
            scene = s; mapView.setScene(s); reportFullyDrawn()
            // back where the user was: the run chosen before the system closed the app
            val fromLink = linkRun; linkRun = null
            nav.find<Route.Map>()?.run?.let { key -> s.runs.pistes.firstOrNull { it.key == key }?.let { mapView.select(it, chosen = key == fromLink, via = "link"); Qa.log("restored run $key") } }
            Qa.log("scene ready")
        }
        castShadows(s)
        runOnUiThread { if (isDestroyed) return@runOnUiThread; qaPending?.let { qaPending = null; applyQaMap(it, sunDone = true) }; ui.postDelayed(skyTick, SKY_EVERY_MS) }
        // new runs, lifts and videos from the site, if there are any: read at the next launch (data/SiteData.kt)
        // whatever goes wrong with a refresh, the app keeps the data it has (it never crashes over it)
        val refreshed = runCatching { siteData.refresh() }.getOrElse { Telemetry.handled(it); emptyMap() }
        Qa.log("data refresh ${refreshed.entries.joinToString { "${it.key} ${it.value}" }}")
    }

    /** The sun moves: light and shadows again every few minutes (unless the QA run pinned the time). */
    private val skyTick: Runnable = object : Runnable {
        override fun run() {
            val s = scene ?: return
            if (isDestroyed) return
            if (clockMs == null) inBackground { val note = placeSun(s, System.currentTimeMillis()); runOnUiThread { sunNote = note }; castShadows(s) }
            ui.postDelayed(this, SKY_EVERY_MS)
        }
    }

    /** Light: the sky over Gudauri at that time, as on the site: the sun by day, the moon at night (map/Sky.kt). */
    /** The mountain's light: now, or noon or 22:00 in Gudauri when the switch says day or night, as on the site (A-11). */
    private fun placeSun(s: MapScene, timeMs: Long): String {
        s.light = Sky.at(DayNight.lightTime(timeMs, dnMode))
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
        i.getStringExtra("qa.group")?.let { DevServer.seed(rawGroupApi, it); accountPrefs.edit().clear().apply(); justJoined = false; Qa.log("group seed $it") }
        // the merging game's board (13.6): "near" (one swipe left from a whole snowman), or a new game
        i.getStringExtra("qa.merge")?.let { b ->
            val m = io.github.pini236.skiapp.game.Merge(kotlin.random.Random(7))
            if (b == "near") m.set(listOf(listOf(6, 6, -1, -1), listOf(4, 3, 2, 1), listOf(-1, -1, -1, -1), listOf(-1, -1, -1, -1))) else m.newGame()
            getSharedPreferences("merge", MODE_PRIVATE).edit().putString("save", m.save()).putInt("got", if (b == "near") 6 else 0).apply(); Qa.log("merge board $b")
        }
        // the ski school: every lesson open, or the lesson on the slope ended now
        i.getStringExtra("qa.school")?.let { a -> when (a) {
            "open" -> { io.github.pini236.skiapp.game.Bests.openAllLessons(this); Qa.log("school lessons open") }
            "finish" -> io.github.pini236.skiapp.game.SchoolQa.finish?.invoke()
        } }
        // the descent: the run started now, or skied to the bottom at once
        i.getStringExtra("qa.descent")?.let { a -> when (a) {
            "go" -> io.github.pini236.skiapp.game.DescentQa.go?.invoke()
            "bottom" -> io.github.pini236.skiapp.game.DescentQa.bottom?.invoke()
        } }
        // the snowball fight: every rung open, or the fight now won at once
        i.getStringExtra("qa.snowball")?.let { a -> when (a) {
            "open" -> { for (n in 1..io.github.pini236.skiapp.game.RUNGS.size) io.github.pini236.skiapp.game.Bests.setLevel(this, "snowball", n, 1); Qa.log("snowball rungs open") }
            "win" -> io.github.pini236.skiapp.game.SnowballQa.win?.invoke()
        } }
        i.getStringExtra("qa.tab")?.let { t -> when (t) {
            "map" -> nav.switchTo(Route.Map(nav.find<Route.Map>()?.run)); "games" -> nav.switchTo(Route.Games); "merge", "fresh", "school", "snowball", "descent" -> { nav.switchTo(Route.Games); nav.push(Route.Game(t)) }; "trip" -> nav.switchTo(Route.Trip)
            "home" -> nav.toStart()
            else -> Route.parse(t)?.let { nav.toStart(); nav.push(it) } ?: Qa.log("bad tab $t")
        } }
        i.getStringExtra("qa.stats")?.let { showStats = it != "off" }
        i.getStringExtra("qa.time")?.let { clockMs = Qa.gudauriTime(it); tick = nowMs() }
        i.getStringExtra("qa.status")?.let { pinStatus(it) }
        i.getStringExtra("qa.weather")?.let { pinWeather(it) }
        i.getStringExtra("qa.sheet")?.let { statusSheet = it == "on"; Qa.log("status sheet ${if (statusSheet) "open" else "closed"}") }
        i.getStringExtra("qa.forme")?.let { forMe = it == "on"; Qa.log("for me ${if (forMe) "on" else "off"}") }
        // the next meetup's reminder, now (the run cannot wait for the real quarter of an hour before)
        i.getStringExtra("qa.remind")?.let { Reminders.armed(this).minByOrNull { it.at }?.let { r -> Reminders.show(this, r) } ?: Qa.log("no reminder armed") }
        i.getStringExtra("qa.sentry")?.let { Telemetry.testCrashReport(it); inBackground { Telemetry.flush(); Qa.log("telemetry ${if (Telemetry.hasKeys) "keys" else "no keys"}, flushed") } }
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
            inBackground { val note = placeSun(s, ms); runOnUiThread { sunNote = note }; castShadows(s) }
        }
        i.getStringExtra("qa.face")?.let { body ->
            // turn to the sun or the moon, almost level, to see it over the ridges (after the new time's light is in)
            inBackground {
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
        i.getStringExtra("qa.mapview")?.let { io.github.pini236.skiapp.map.MapMode.qa = it }
        i.getStringExtra("qa.run")?.let { key ->
            if (key == "none") mapView.select(null)
            else s.runs.pistes.firstOrNull { it.key == key || it.name == key }?.let { mapView.select(it) } ?: Qa.log("no run $key")
        }
        if (i.getBooleanExtra("qa.fly", false)) mapView.postDelayed({ mapView.flyDown() }, 1500)
        i.getStringExtra("qa.gesture")?.let { GesturePlayer.play(mapView, it) }
    }

    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onResume() {
        super.onResume(); mapView.onResume()
        if (backgrounded) { backgrounded = false; Telemetry.event("app_open", mapOf("source" to (warmSource ?: "direct"), "cold" to false)) }
        warmSource = null
    }

    override fun onStop() { super.onStop(); if (!isChangingConfigurations) backgrounded = true }

    /** A language from the sheet (home or settings; null: by the phone): the app comes back in it (the phone recreates the screen). */
    private fun setLang(tag: String?) {
        Telemetry.event("lang_set", mapOf("lang" to (tag ?: "auto"), "previous" to Lang.current(resources).tag))
        Qa.log("lang set ${tag ?: "auto"}")
        Lang.set(this, tag)
    }

    /** The day and night switch (home, the invitation, settings): kept, measured, and the events after it carry the new mode. */
    private fun nextMode() {
        dnMode = dnMode.next(); haptics.tick(.4f)
        getSharedPreferences("daynight", MODE_PRIVATE).edit().putString("mode", dnMode.name).apply()
        Telemetry.event("theme_set", mapOf("mode" to dnMode.name.lowercase()))
        Telemetry.setTheme(dnMode.name.lowercase())
        // the mountain's light and shadows follow the switch
        scene?.let { s -> inBackground { val note = placeSun(s, clockMs ?: System.currentTimeMillis()); runOnUiThread { sunNote = note }; castShadows(s) } }
    }
    override fun onDestroy() { super.onDestroy(); ui.removeCallbacks(skyTick); sounds.release(); mapView.release(); metricsThread.quitSafely(); loader.shutdownNow() }

    @Composable
    private fun App() {
        BackHandler(enabled = nav.canBack) { nav.back() }
        // the clock: Gudauri's hour for the sky and the countdown, once every half minute (or pinned by the QA run)
        LaunchedEffect(Unit) { while (true) { delay(30_000); tick = nowMs() } }
        // the notification permission for the meetups' reminders, asked when the app first opens (Pini, 2.10.2026)
        val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            Qa.log("notifications ${if (ok) "allowed" else "refused"}"); armReminders()
        }
        LaunchedEffect(Unit) {
            if (Reminders.shouldAsk(this@MainActivity, Reminders.Ask.OPEN)) {
                Reminders.markAsked(this@MainActivity, Reminders.Ask.OPEN); Qa.log("notifications asked")
                askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val frame = remember(tick, dnMode) { DayNight.at(tick, dnMode) }
        val top = nav.top
        // the lift status now; on the mountain, closed lifts and runs (S1), and "only what's open for me"
        val lstat = remember(tick, report, liftNames) { LiftStatus(liftNames, report, tick) }
        val paint = remember(lstat.fresh, report, forMe, scene) {
            val runs = scene?.runs
            if (!lstat.fresh || runs == null) StatusPaint.NONE
            else StatusPaint(runs.pistes.filter { lstat.runOpen(it) == false }.map { it.key }.toSet(),
                runs.mainLifts.filter { lstat.isOpen(it.name.ifBlank { null }) == false }.map { it.name }.toSet(), forMe,
                runs.mainLifts.filter { lstat.isOpen(it.name.ifBlank { null }) == true }.map { it.name }.toSet())
        }
        // the group sign's line on home: my first group, read again each time home shows (A-27)
        var firstGroup by remember { mutableStateOf<Pair<String, Int>?>(null) }
        // me, for the pass's passenger (A-32): read again each time home shows (after signing in or out elsewhere)
        var me by remember { mutableStateOf(if (groupApi.ready) groupApi.me() else null) }
        LaunchedEffect(top) {
            if ((top == Route.Home || top == Route.About) && groupApi.ready) { me = groupApi.me(); if (top == Route.Home) { firstGroup = runCatching { groupApi.firstGroup() }.getOrNull(); if (firstGroup != null) sendBests() } }
        }
        // the account on the pass and on the settings' ski pass (A-32)
        val account = if (groupApi.ready) io.github.pini236.skiapp.home.Account(me, onManage = { nav.push(Route.Account) },
            onSignIn = { nav.push(Route.Account) },
            onSignOut = { lifecycleScope.launch { runCatching { groupApi.signOut() }; me = groupApi.me(); firstGroup = null; armReminders() } }) else null
        LaunchedEffect(paint) { mapView.setStatus(paint); if (paint !== StatusPaint.NONE) Qa.log("status on map: ${paint.closedLifts.size} lifts, ${paint.closedRuns.size} runs closed${if (paint.forMe) ", for me" else ""}") }
        // "opened since you checked": against the state the board showed last time, which is then kept
        val changes = remember(statusSheet, report) { if (statusSheet && lstat.fresh) lstat.changes(statusSource.before()) else emptyList() }
        LaunchedEffect(statusSheet, report) { if (statusSheet && lstat.fresh) statusSource.seen(lstat.snapshot()) }
        // status_view: once per visit to the map, after the first answer (docs/GROWTH.md)
        val onMap = top is Route.Map
        var statusViewed by remember(onMap) { mutableStateOf(false) }
        LaunchedEffect(onMap, statusLoaded) {
            if (onMap && statusLoaded && !statusViewed) {
                statusViewed = true
                Telemetry.event("status_view", if (lstat.fresh) mapOf("state" to "fresh", "open" to lstat.open, "total" to lstat.names.size) else mapOf("state" to if (report == null) "none" else "stale"))
            }
        }
        LaunchedEffect(statusSheet) { if (statusSheet) Qa.log("status board ${if (lstat.fresh) "live" else "snowy"}") }
        // screen_view with the contract's screen names (docs/GROWTH.md); a chosen run is run_open, not a screen. The way
        // into the groups (A1) is "signin" when nobody is signed in on this phone.
        val screen = when (top) {
            Route.Home -> "home"; is Route.Map -> "map"; is Route.Meet -> "meet"; Route.Games -> "games"; Route.About -> "about"; Route.Trip -> "trip"
            is Route.Game -> "game:" + top.name
            is Route.Group -> if (top.id == null && groupApi.me() == null) "signin" else "group"
            Route.GroupNew, is Route.GroupInvite, is Route.TripFor -> "group"
            is Route.Join, Route.JoinCode, is Route.Reclaim -> "join"
            Route.Account -> "account"
            else -> null
        }
        LaunchedEffect(screen) {
            if (screen != null) Telemetry.event("screen_view", if (screen.startsWith("game:")) mapOf("screen" to "game", "game" to screen.removePrefix("game:")) else mapOf("screen" to screen))
        }
        // the whole app goes dark at night, as the site does; the spike's map and game keep their day colours
        // the game keeps its day colours; the map follows the day and night switch, as on the site (A-11)
        val spike = top is Route.Game
        // the merging game is a blue evening whatever the hour, and the snowball fight and the descent a blue sky: light icons over them
        val evening = top == Route.Game("merge") || top == Route.Game("snowball") || top == Route.Game("descent")
        val view = LocalView.current
        SideEffect { WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !evening && (spike || !frame.dark) }
        SkiTheme(dark = frame.dark) {
          androidx.compose.runtime.CompositionLocalProvider(io.github.pini236.skiapp.weather.LocalWeather provides
              remember(forecast, tick) { io.github.pini236.skiapp.weather.WeatherNow(forecast, Instant.ofEpochMilli(tick)) }) {
            Box(Modifier.fillMaxSize().background(if (spike) Palette.snow else Ski.colors.snow)) {
                when (top) {
                    Route.Home -> HomeScreen(trip, frame, dnMode, LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()), haptics, sounds, status = lstat, group = firstGroup,
                        onLang = ::setLang,
                        onMode = ::nextMode,
                        account = account,
                        go = { a ->
                            haptics.tick(.4f)
                            if (a == HomeAction.STATUS) statusSheet = true
                            if (a == HomeAction.PRIVACY) { openPrivacy(); return@HomeScreen }
                            nav.push(when (a) {
                                HomeAction.MAP, HomeAction.STATUS -> Route.Map(nav.find<Route.Map>()?.run)
                                HomeAction.MEET -> Route.Meet()
                                HomeAction.GAMES -> Route.Games
                                HomeAction.GROUP -> Route.Group()
                                HomeAction.ABOUT -> Route.About
                                HomeAction.TRIP -> Route.Trip
                                HomeAction.PRIVACY -> Route.Home
                            })
                        })
                    Route.Trip -> TripForm(trip, LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()).toLocalDate(), onSave = { t -> keepTrip(t); Qa.log("trip saved"); nav.back() },
                        onDelete = { keepTrip(null); Qa.log("trip deleted"); nav.back() }, onCancel = { nav.back() })
                    is Route.Meet -> {
                        LaunchedEffect(Unit) {
                            // its own thread: the loader may still be casting the map's shadows
                            askRelief()
                        }
                        // from a group's page, a new meetup goes back to that group (Q8)
                        val fromGroup = (nav.routes.getOrNull(nav.routes.size - 2) as? Route.Group)?.id
                        MeetScreen(meetPlan, scene?.runs, relief, trip, tick, top,
                            save = if (groupApi.ready) MeetSave(groupApi, fromGroup, after = ::armReminders) { id -> nav.back(); if (nav.top is Route.Group) nav.replaceTop(Route.Group(id, GroupTab.MEETUPS.key)) } else null,
                            onRoute = { r -> if (nav.top is Route.Meet && nav.top != r) nav.replaceTop(r) },
                            onOnMap = { st -> nav.push(Route.Map(nav.find<Route.Map>()?.run)); mapView.showLift(st.ends.first().lift.id) },
                            onBack = { nav.back() })
                    }
                    is Route.Group -> if (!groupApi.ready) SoonScreen(stringResource(R.string.app_sign_group), stringResource(R.string.app_soon_group)) { nav.back() }
                        else if (top.id == null) GroupHub(groupApi, onGroup = { id -> nav.replaceTop(Route.Group(id, GroupTab.FLIGHTS.key)) }) { changes ->
                            GroupEntryScreen(groupApi, changes = changes, onBack = { nav.back() },
                                onCode = { c -> nav.push(if (InviteCode.isCode(c) || InviteCode.isToken(c)) Route.Join(c) else Route.JoinCode) },
                                onCreate = { nav.push(Route.GroupNew) }, signInGoogle = ::signInGoogle, onPrivacy = ::openPrivacy)
                        }
                        else GroupScreen(groupApi, top.id, GroupTab.of(top.tab), frame, trip, LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()),
                            justJoined = justJoined, saveOffers = accountPrefs.getInt("save_offers", 0),
                            station = { key -> meetPlan?.byId?.get(key) },
                            spot = { m -> val plan = meetPlan; val at = m.at.atOffset(Meet.GUDAURI); val hm = "%02d:%02d".format(at.hour, at.minute)
                                Preset.entries.firstOrNull { p -> p.time == hm && plan?.preset(p)?.id == m.station } },
                            onOpenMeetup = { m -> val at = m.at.atOffset(Meet.GUDAURI); nav.push(Meet.route(m.station, at.toLocalTime(), at.toLocalDate())) },
                            onMeetups = ::armReminders,
                            onTab = { nav.replaceTop(Route.Group(top.id, it.key)) }, onBack = { nav.toStart() },
                            onInvite = { nav.push(Route.GroupInvite(top.id)) }, onNewMeetup = { nav.push(Route.Meet()) }, onEditTrip = { nav.push(Route.Trip) },
                            onFillNew = { uid -> nav.push(Route.TripFor(top.id, uid)) },
                            onMyTrip = { t -> keepTrip(t) }, onLeft = { nav.toStart() }, signInGoogle = ::signInGoogle,
                            onSaveOffered = { justJoined = false; accountPrefs.edit().putInt("save_offers", accountPrefs.getInt("save_offers", 0) + 1).apply() })
                    Route.GroupNew -> NewGroupScreen(groupApi, trip, LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()).toLocalDate(), onCancel = { nav.back() },
                        onCreated = { id -> nav.back(); if (nav.top is Route.Group) nav.replaceTop(Route.Group(id, GroupTab.FLIGHTS.key)) else nav.push(Route.Group(id, GroupTab.FLIGHTS.key)) })
                    is Route.GroupInvite -> InviteScreen(groupApi, top.id) { nav.back() }
                    is Route.TripFor -> {
                        // an admin fills in a flight for a member, or fixes one an admin entered (A-20): the same form, filled
                        // with that flight, sent to the group (set_member_trip)
                        val member by produceState<Pair<String, Trip?>?>(null, top) {
                            value = runCatching { groupApi.group(top.group).members.firstOrNull { it.userId == top.user } }.getOrNull()?.let { it.name to it.trip } ?: ("…" to null)
                        }
                        val name = member?.first
                        val r = rememberRunner()
                        if (member != null) key(member) { TripForm(member?.second, LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()).toLocalDate(),
                            onSave = { t -> r.run { groupApi.setMemberTrip(top.group, top.user, t); Qa.log("member trip saved"); nav.back() } },
                            onDelete = {}, onCancel = { nav.back() },
                            title = stringResource(R.string.group_trip_for, name ?: "…"), intro = stringResource(R.string.app_g_fill_new_sub), footer = { ErrorLine(r) }, canDelete = false) }
                    }
                    Route.JoinCode -> CodeScreen("", onBack = { nav.back() }) { c -> nav.replaceTop(Route.Join(c)) }
                    is Route.Join -> InvitedScreen(groupApi, top.code, frame, dnMode, onMode = ::nextMode, onAbout = { nav.push(Route.About) },
                        onBack = { nav.back() }, onTypeCode = { nav.replaceTop(Route.JoinCode) }, onReclaim = { nav.push(Route.Reclaim(top.code)) },
                        onJoined = { id, guest -> justJoined = guest; nav.toStart(); nav.push(Route.Group(id, GroupTab.FLIGHTS.key)) })
                    is Route.Reclaim -> ReclaimScreen(groupApi, top.code, onBack = { nav.back() }, signInGoogle = ::signInGoogle) { id ->
                        nav.toStart(); nav.push(Route.Group(id, GroupTab.FLIGHTS.key))
                    }
                    Route.Account -> AccountScreen(groupApi, onBack = { nav.back() }, signInGoogle = ::signInGoogle) { nav.toStart(); armReminders() }
                    // the games page (13.6, the site's #games, GP2), and the games the app has
                    Route.Games -> GamesScreen(appGames(runs = 1), onOpen = { g -> haptics.tick(.4f); nav.push(Route.Game(g)) }) { nav.back() }
                    Route.Game("merge") -> MergeScreen(haptics, onBack = { nav.back() }) { sendBests() }
                    Route.Game("fresh") -> FreshSnowScreen(haptics, onBack = { nav.back() }) { sendBests() }
                    Route.Game("school") -> SchoolScreen(haptics, onBack = { nav.back() }) { sendBests() }
                    Route.Game("snowball") -> io.github.pini236.skiapp.game.SnowballScreen(haptics, onBack = { nav.back() }, onBest = { sendBests() })
                    Route.Game("descent") -> io.github.pini236.skiapp.game.DescentScreen(profiles, haptics, onBack = { nav.back() }, onBest = { sendBests() })
                    Route.About -> AboutScreen(BuildConfig.VERSION_NAME, onPrivacy = ::openPrivacy, account = account, mode = dnMode, onMode = ::nextMode,
                        lang = Lang.current(resources), langManual = Lang.manual(this@MainActivity), onLang = ::setLang,
                        onResetBests = { Bests.reset(this@MainActivity); Qa.log("bests reset") }) { nav.back() }
                    else -> {
                        MapScreen(mapView, scene, videos = videos, ms = MapStatus(lstat, changes, LiftStatus.inSeason(LocalDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.systemDefault()).monthValue),
                            forMe, { on -> forMe = on; Telemetry.event("status_only_open", mapOf("on" to on)); Qa.log("for me ${if (on) "on" else "off"}") },
                            statusSheet, { open -> statusSheet = open }), dark = frame.dark, relief = relief, askRelief = ::askRelief)
                        // on the map the counter sits under the bar of the view switch and the lift status, and from above
                        // (no 3D to count) it is not shown
                        if (showStats && (top is Route.Game || !io.github.pini236.skiapp.map.MapMode.top))
                            StatsBar(top !is Route.Game, Modifier.align(Alignment.TopStart).then(if (top is Route.Game) Modifier else Modifier.padding(top = 128.dp)))
                        // the way home, over the mountain or the game
                        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(6.dp).background(Color(0xE6FFFFFF))) {
                            BackLink(stringResource(R.string.nav_home), { nav.back() }, Palette.glacier)
                        }
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

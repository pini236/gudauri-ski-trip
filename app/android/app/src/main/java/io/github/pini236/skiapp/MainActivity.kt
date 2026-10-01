package io.github.pini236.skiapp

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.game.DescentScreen
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.MapScreen
import io.github.pini236.skiapp.map.MapView
import io.github.pini236.skiapp.map.Sun
import io.github.pini236.skiapp.perf.FrameStats
import io.github.pini236.skiapp.perf.Startup
import io.github.pini236.skiapp.ticket.TicketScreen
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
    private val uiStats = FrameStats()
    private val glStats = FrameStats()
    private var refreshHz = 60f
    private lateinit var haptics: Haptics
    private lateinit var sounds: Sounds
    private lateinit var mapView: MapView
    private val metricsThread = HandlerThread("frame-metrics").apply { start() }
    private val loader = Executors.newSingleThreadExecutor()

    private var scene by mutableStateOf<MapScene?>(null)
    private var profile by mutableStateOf<Profile?>(null)
    private var sunNote by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferTopRefreshRate()
        haptics = Haptics(this)
        sounds = Sounds(this)
        mapView = MapView(this, refreshHz, glStats)

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
        loadInBackground()
        setContent { App() }
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
        val runs = Runs.parse(asset("data/runs-and-lifts.json"))
        val profiles = Profile.parse(asset("data/profiles.json"))
        Startup.dataMs = SystemClock.uptimeMillis() - t0
        runOnUiThread { profile = profiles.firstOrNull { it.key == "Tatra 2" } ?: profiles.firstOrNull() }
        val t1 = SystemClock.uptimeMillis()
        val s = MapScene(terrain, runs)
        Startup.meshMs = SystemClock.uptimeMillis() - t1
        // light: the real sun over Gudauri now; at night, a low winter afternoon sun so the shading shows
        val (az, alt) = Sun.position(System.currentTimeMillis())
        val real = alt > Math.toRadians(6.0)
        s.sunDir = if (real) Sun.direction(az, alt) else Sun.direction(Math.toRadians(215.0), Math.toRadians(20.0))
        s.sunIsReal = real
        runOnUiThread {
            sunNote = if (real) "שמש אמיתית עכשיו" else "לילה בגודאורי: שמש של אחר הצהריים"
            scene = s; mapView.setScene(s); reportFullyDrawn()
        }
        val t2 = SystemClock.uptimeMillis()
        s.shadow = Sun.shadows(terrain, s.sunDir)
        Startup.shadowMs = SystemClock.uptimeMillis() - t2
        runOnUiThread { mapView.updateShadow() }
    }

    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onDestroy() { super.onDestroy(); sounds.release(); mapView.release(); metricsThread.quitSafely(); loader.shutdownNow() }

    @Composable
    private fun App() {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var tab by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxSize().background(Palette.snow)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> MapScreen(mapView, scene)
                        1 -> DescentScreen(profile, haptics, sounds)
                        else -> Box(Modifier.statusBarsPadding().padding(top = 28.dp)) { TicketScreen(haptics, sounds) }
                    }
                    StatsBar(tab == 0, Modifier.align(Alignment.TopStart))
                }
                Row(Modifier.fillMaxWidth().background(Palette.ink).navigationBarsPadding()) {
                    listOf("מפה", "ירידה", "כרטיס").forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f).heightIn(min = 56.dp).background(if (i == tab) Palette.glacier else Palette.ink).clickable { tab = i; haptics.tick(0.4f) },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White) }
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
            while (true) {
                val now = System.nanoTime()
                val st = (if (gl) glStats else uiStats).snapshot(refreshHz, now)
                val frames = if (st.active) "${st.fps.toInt()} פריימים · החמצות ${"%.1f".format(st.jankPct)}% · הגרוע ${st.worstMs.toInt()} מ״ש" else "במנוחה"
                val extra = if (gl) " · ${if (mapView.msaa) "החלקה 4×" else "בלי החלקה"} · $sunNote" else ""
                line = "${refreshHz.toInt()}Hz · $frames$extra\nפתיחה ${Startup.firstFrameMs} · נתונים ${Startup.dataMs} · רשת ${Startup.meshMs} · צל ${Startup.shadowMs} מ״ש · ${haptics.level}"
                delay(500)
            }
        }
        Text(line, modifier.statusBarsPadding().padding(4.dp).background(Color(0xB313233A)).padding(horizontal = 6.dp, vertical = 2.dp),
            fontFamily = Plex, fontSize = 11.sp, lineHeight = 14.sp, color = Color.White)
    }
}

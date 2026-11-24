package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import io.github.pini236.skiapp.map.MapScene
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight

/**
 * The redesign POC in the test build (decisions 83, 84): the app opens on the new mountain; "To the app" shows the
 * app as it is, and a small sign on home brings the mountain back. Release builds only: the debug build is the
 * emulator's (qa/run.sh), whose steps tap the current home. The store build has an empty twin (src/store).
 */
object Redesign {
    /**
     * The redesign is the app (Pini, 24.11.2026: no switch, and no old design shown, ever; docs/ARCHITECTURE.md). The old
     * screens stay in the code only until they are deleted (after 100 percent and a stable week). A test can turn it off.
     */
    @Volatile internal var on: () -> Boolean = { true }

    /**
     * Something of the redesign did not load ([what], for the log). The screen stays in the new design: the painted
     * mountain, or the sky's colour with the pill and the bar and "no recent data" with a retry; never today's screens.
     */
    fun fail(what: String) { failed = what; io.github.pini236.skiapp.qa.Qa.log("redesign failed $what") }
    @Volatile var failed: String? = null
        private set

    /**
     * A site link or a reminder's tap, for the mountain (never today's screens; Pini, 24.11.2026): a run chosen, the
     * meeting point on a station, an invite, a group. The mountain takes it ([LiveMountain]) and clears it.
     */
    internal var link by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Takes [r] for the mountain; false when it is not one the mountain opens (the caller then goes as before). */
    fun link(r: io.github.pini236.skiapp.nav.Route): Boolean {
        if (!on()) return false
        link = linkOf(r) ?: return false
        io.github.pini236.skiapp.qa.Qa.log("redesign link $link")
        return true
    }

    /** "Me" or signing in (the trip card's passenger), on the mountain; false when the mountain is not on. */
    fun people(signIn: Boolean): Boolean {
        if (!on()) return false
        link = if (signIn) "people:signin" else "people:me"
        return true
    }

    /** The mountain's word for a route (kept apart, for the unit test). */
    internal fun linkOf(r: io.github.pini236.skiapp.nav.Route): String? = when (r) {
        is io.github.pini236.skiapp.nav.Route.Map -> r.run?.let { "pick:$it" } ?: "front"
        is io.github.pini236.skiapp.nav.Route.Meet -> r.station?.let { "meet:station:$it" } ?: "meet"
        is io.github.pini236.skiapp.nav.Route.Join -> "people:join:${r.code}"
        is io.github.pini236.skiapp.nav.Route.Group -> r.id?.let { "people:group:$it" } ?: "people:gate"
        else -> null
    }

    /** The live model needs OpenGL ES 3 (the manifest asks for it; a phone that still lacks it keeps the painted mountain). */
    fun liveOk(glEsVersion: Int): Boolean = glEsVersion >= 0x30000

    /** The emulator run's hook (qa.redesign, debug builds only, app/android/qa/README.md). */
    fun qa(cmd: String) { if (on() && io.github.pini236.skiapp.BuildConfig.DEBUG) RedesignQa.send(cmd) }
    /** Whether the new mountain covers the app: then the screens under it are not read aloud (MainActivity). */
    var covering by mutableStateOf(false); private set

    /** Completion part 1 (P04): a recording of one descent ended; its card waits, and a notification in the pocket. */
    fun runDone(ctx: android.content.Context, day: java.io.File, autostop: String?, lift: String?) { if (on()) RunDone.ended(ctx, day, autostop, lift) }

    /**
     * Round 4 (decision 84, board L06): the end of a day on the live mountain, in place of today's day screen. Behind
     * DAYREC_UI as the day itself (the route is only reached with it); in the debug build only when the emulator asks
     * (qa.redesign day), so the other parts see the day screen as it is. A touch on its card opens the ridge of the day and
     * its timeline (L07), a descent there its film (L09), and "share" the story (L10) with its picture. Returns false when it does not show, and the caller draws today's screen.
     */
    @Composable
    fun day(file: java.io.File, sample: Boolean, scene: MapScene?, mode: DayNight.Mode, haptics: Haptics, onAgain: () -> Unit = {}, onBack: () -> Unit): Boolean {
        if (!on()) return false
        if (scene == null) return false
        if (io.github.pini236.skiapp.BuildConfig.DEBUG && !RedesignQa.dayOn) return false
        // one descent that ended (completion part 1, P04): its card, until it is closed; then the day as usual
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val id = file.name.removePrefix("day-").removeSuffix(".jsonl")
        val pending = androidx.compose.runtime.remember(id) { androidx.compose.runtime.mutableStateOf(RunDone.pending(ctx)?.takeIf { it.id == id }) }
        // stopped by hand, the card is set a moment later, once the recorder has written the result
        androidx.compose.runtime.LaunchedEffect(id) {
            while (io.github.pini236.skiapp.dayrec.DayRec.recording) kotlinx.coroutines.delay(300)
            pending.value = RunDone.pending(ctx)?.takeIf { it.id == id }
        }
        val p = pending.value
        if (p != null) { RunDoneCard(file, p, scene, mode, haptics, onAgain = { RunDone.clear(ctx); onAgain() }, onClose = { RunDone.clear(ctx); pending.value = null; onBack() }); return true }
        DayMountain(file, sample, scene, mode, haptics, onBack = onBack)
        return true
    }

    /**
     * Round 4 (decision 84, boards L02, L03, L13): the day while it is recorded, on the mountain, in place of today's
     * live screen. The same gate as [day]. Returns false when it does not show, and the caller draws today's screen.
     */
    @Composable
    fun live(scene: MapScene?, mode: DayNight.Mode, haptics: Haptics, liftName: (String) -> String, runName: (String) -> String,
             onSwitch: (Boolean) -> Unit, onStop: () -> Unit, onEnded: (java.io.File?) -> Unit,
             liftState: (String) -> Pair<Boolean, String?>? = { null }, onBack: () -> Unit): Boolean {
        if (!on()) return false
        if (scene == null) return false
        if (io.github.pini236.skiapp.BuildConfig.DEBUG && !RedesignQa.dayOn) return false
        LiveDay(scene, mode, haptics, liftName, runName, onSwitch, onStop, onEnded, liftState, onBack)
        return true
    }

    /**
     * Round 4 (board L01): the first time, before the system's location question, in place of today's sheet: what the day
     * does, that everything stays on the phone, which permission is enough, and all day or one descent. Gated as [day].
     */
    @Composable
    fun first(single: Boolean, haptics: Haptics, onGo: (Boolean) -> Unit, onDismiss: () -> Unit): Boolean {
        if (!on()) return false
        if (io.github.pini236.skiapp.BuildConfig.DEBUG && !RedesignQa.dayOn) return false
        DayStart(single, haptics, onGo, onDismiss)
        return true
    }

    /**
     * Round 4 (board L05, Android): the permanent notification's words while recording, or null for today's. Not a
     * composable: the recording service asks it (title, text, the expanded text). Gated as [day].
     */
    fun notice(c: android.content.Context, elapsedMs: Long, live: io.github.pini236.skiapp.dayrec.DayEngine.Snapshot?,
               ride: io.github.pini236.skiapp.dayrec.DayEngine.Ride?): Triple<String, String, String?>? {
        if (!on()) return null
        if (io.github.pini236.skiapp.BuildConfig.DEBUG && !RedesignQa.dayOn) return null
        return Notice.content(c, elapsedMs, live, ride)?.let { Triple(it.title, it.text, it.big) }
    }

    /** What, besides the minute, changes that notification (empty when it is today's). */
    fun noticeKey(live: io.github.pini236.skiapp.dayrec.DayEngine.Snapshot?, ride: io.github.pini236.skiapp.dayrec.DayEngine.Ride?): String =
        if (!on() || (io.github.pini236.skiapp.BuildConfig.DEBUG && !RedesignQa.dayOn)) "" else Notice.key(live, ride)

    /**
     * Round 4 (board L12): my days, with the trip as layers on one mountain, in place of today's list. Gated as [day].
     */
    @Composable
    fun days(mode: DayNight.Mode, haptics: Haptics, onOpen: (String) -> Unit, scene: MapScene? = null, onRecord: () -> Unit = {}, onBack: () -> Unit): Boolean {
        if (!on()) return false
        if (io.github.pini236.skiapp.BuildConfig.DEBUG && !RedesignQa.dayOn) return false
        DayList(mode, haptics, onOpen, onBack, scene, onRecord)
        return true
    }

    @Composable
    fun BoxScope.Host(home: Boolean, mode: DayNight.Mode, haptics: Haptics, scene: MapScene?, status: io.github.pini236.skiapp.status.LiftStatus? = null,
                       inSeason: Boolean = false, forMe: Boolean = false, onForMe: (Boolean) -> Unit = {}, around: io.github.pini236.skiapp.home.Around? = null) {
        if (!on()) return
        // the emulator's scripts drive the debug build through the current screens (qa/run.sh): there the mountain
        // opens only when the run asks for it (qa.redesign open), so every other part sees the app as it is
        val debug = io.github.pini236.skiapp.BuildConfig.DEBUG
        if (debug && !RedesignQa.asked) return
        var open by rememberSaveable { mutableStateOf(!debug) }
        // the mountain is the app (Pini, 24.11.2026): it closes only in the emulator's runs, which drive the screens
        // under it; anywhere else back leaves the app and nothing leads to the old home
        val close: (() -> Unit)? = if (debug) ({ open = false }) else null
        if (!debug && !open) open = true
        // a link opens the mountain (its sheet comes with it)
        androidx.compose.runtime.LaunchedEffect(link) { if (link != null) open = true }
        LaunchedEffect(RedesignQa.seq) {
            when (RedesignQa.cmd) {
                "open" -> open = true
                "close" -> open = false
            }
        }
        DisposableEffect(open) { covering = open; onDispose { covering = false } }
        if (open) {
            if (close != null) BackHandler { close() }
            val view = LocalView.current
            // light icons over the sky
            SideEffect { (view.context as? android.app.Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false } }
            // phase B: the live model under the picture; the picture fades away over it (and back), 700 ms
            var live by rememberSaveable { mutableStateOf(false) }
            val shown = remember { Animatable(if (live) 1f else 0f) }
            LaunchedEffect(live) { shown.animateTo(if (live) 1f else 0f, tween(700)) }
            BackHandler(enabled = live) { live = false }
            LaunchedEffect(RedesignQa.seq) {
                when (RedesignQa.cmd) {
                    "live" -> if (scene != null) live = true
                    "painted" -> live = false
                }
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            // the live model: OpenGL ES 3 and its own file (5 KB); without either, the painted mountain only
            val gles = remember {
                liveOk((ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) &&
                    runCatching { LiveData.load(ctx) }.onFailure { io.github.pini236.skiapp.qa.Qa.log("redesign failed live data") }.isSuccess
            }
            Box(Modifier.fillMaxSize()) {
                // the live model only over home: never with the map open too (the height model would sit on the GPU
                // twice, docs/ARCHITECTURE.md)
                if (scene != null && home && gles && (live || shown.value > 0f)) LiveMountain(scene, mode, haptics, status, inSeason, forMe, onForMe, around, onPainted = { live = false }, onLeave = close)
                if (!live || shown.value < 1f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - shown.value }) {
                    PeakScreen(mode, haptics, onLive = if (scene != null && home && gles) ({ live = true }) else null, onLeave = close)
                }
            }
        } else {
            // over the sky of home, under the top row, where nothing is (the designer: at the bottom it covered a sign)
            AnimatedVisibility(home, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 84.dp, end = 16.dp), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.height(44.dp).shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color(0xF00E2233))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp)).clickable { haptics.tick(.4f); open = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFB79CFF)))
                    Text(stringResource(R.string.poc_peak_enter), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }
}

/**
 * The emulator's commands to the mountain (qa.redesign): open, close, view:<zoom>,<x>,<y> (the free view, as a pinch
 * would leave it; adb has one finger), and spot:<run> (logs where on the screen that run can be touched now, so the
 * script taps there with a real touch). The screen writes "redesign ..." lines for the script to wait on.
 */
object RedesignQa {
    var asked by mutableStateOf(false); private set
    var seq by mutableStateOf(0); private set
    var cmd: String = ""; private set
    /** The day on the mountain (round 4) in the debug build: once asked (qa.redesign day), until the run ends. */
    var dayOn by mutableStateOf(false); private set
    /**
     * The emulator sends its commands faster than a slow emulator draws (run 377: "unpick" then "home" half a second
     * apart, and the screens, which read [cmd] on a change of [seq], saw only the second). So one at a time: each next
     * one two drawn frames after the last, which grows with the emulator's load as the screens do.
     */
    fun send(c: String) {
        pending.addLast(c)
        if (!pumping) pump()
    }
    private val pending = ArrayDeque<String>()
    private var pumping = false
    private fun pump() {
        val c = pending.removeFirstOrNull() ?: run { pumping = false; return }
        pumping = true
        asked = true; if (c == "day") dayOn = true; cmd = c; seq++
        val ch = android.view.Choreographer.getInstance()
        ch.postFrameCallback { ch.postFrameCallback { android.os.Handler(android.os.Looper.getMainLooper()).post { pump() } } }
    }
}

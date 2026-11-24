package io.github.pini236.skiapp.redesign

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.map.Locator
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.WhereAmI
import io.github.pini236.skiapp.map.roundAcc
import io.github.pini236.skiapp.map.whereWords
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Plex
import kotlin.math.hypot

/**
 * Part 4 of the redesign, "where am I" on the mountain (design/handoff/2026-11-23-redesign-on-mountain, section 3).
 * The behaviour, the rules and the words are today's (map/WhereAmI.kt, m-7): nothing leaves the phone, off on every
 * opening, only while the mountain is in front. Only how it looks is new.
 */
internal class WhereUi(context: android.content.Context, scene: MapScene) {
    val where = WhereAmI(context.applicationContext, Locator(scene.runs, scene.terrain), scene.terrain)
    /** S02 over the mountain: the first switching on ever, before the phone asks. */
    var asking by mutableStateOf(false)
    /** "Out of the area" and "no permission" keep the button lit until it is tapped (section 3). */
    val lit: Boolean get() = where.state != WhereAmI.State.Off
}

/** The blue of "where am I" lit and of my dot (section 1), the same by day and night. */
internal val YOU_BLUE = Color(0xFF2F7BFF)

private const val ASKED = "redesign_where_asked"

@Composable
internal fun rememberWhere(scene: MapScene): WhereUi {
    val context = LocalContext.current
    val ui = remember(scene) { WhereUi(context, scene) }
    // the button goes off in the background and when the mountain closes (m-7 section 5)
    DisposableEffect(ui) {
        val w = ui.where
        val life = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> w.resume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> w.pause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> w.stop()
                else -> {}
            }
        }
        life?.addObserver(obs)
        if (life?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true) w.resume()
        onDispose { life?.removeObserver(obs); w.stop() }
    }
    return ui
}

/** The column of the map's buttons (section 1): the compass (only once turned) and "where am I", at the screen's end. */
@Composable
internal fun BoxScope.MapColumn(ui: WhereUi, view: LiveView, night: Boolean, recording: Boolean, onLocate: () -> Unit, onHome: () -> Unit,
                                 layersOn: Boolean? = null, onLayers: () -> Unit = {}, bottom: androidx.compose.ui.unit.Dp = 84.dp) {
    val k = remember(night) { SheetInk(night) }
    var away by remember { mutableStateOf(false) }
    var north by remember { mutableStateOf(0f) }
    LaunchedEffect(view) { while (true) { withFrameNanos { }; val a = view.awayFromHome(); if (a != away) { away = a; Qa.log("redesign compass ${if (a) "shown" else "gone"}") }; if (a) north = view.northDeg(); kotlinx.coroutines.delay(120) } }
    Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // K08: the layers button (the lenses' row); lit while the row is open, the mark in the background's colour
        if (layersOn != null) {
            val ld = stringResource(R.string.app_map_layers_a11y)
            val ls = stringResource(if (layersOn) R.string.app_on else R.string.app_off)
            Round(k, Modifier.semantics { contentDescription = ld; stateDescription = ls; role = Role.Switch }.clickable(onClick = onLayers), fill = if (layersOn) k.ink else null) {
                Canvas(Modifier.size(20.dp)) { layers(if (layersOn) (if (night) Color(0xFF0F1828) else Color.White) else k.ink) }
            }
        }
        AnimatedVisibility(away, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            val home = stringResource(R.string.app_map_home_view_a11y)
            Round(k, Modifier.semantics { contentDescription = home; role = Role.Button }.clickable(onClick = onHome)) {
                Canvas(Modifier.size(20.dp).rotate(north)) { compass(k.ink, Color(0xFFE5352B)) }
            }
        }
        val on = ui.lit || recording
        val label = stringResource(R.string.loc_button)
        val st = stringResource(if (on) R.string.app_on else R.string.app_off)
        Round(k, Modifier.semantics { contentDescription = label; stateDescription = st; role = Role.Switch }
            .clickable(enabled = !recording, onClick = onLocate), fill = if (on) YOU_BLUE else null) {
            Canvas(Modifier.size(20.dp)) { locator(if (on) Color.White else k.ink, filled = on) }
        }
    }
}

@Composable
private fun Round(k: SheetInk, modifier: Modifier, fill: Color? = null, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).shadow(8.dp, CircleShape, clip = false).clip(CircleShape).background(fill ?: k.surf.copy(alpha = 1f))
        .let { if (fill == null) it.border(1.dp, k.line, CircleShape) else it }.then(modifier), contentAlignment = Alignment.Center) { content() }
}

/** A compass: a needle, the north half red. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.compass(ink: Color, red: Color) {
    val w = size.width; val h = size.height; val c = Offset(w / 2, h / 2)
    drawPath(Path().apply { moveTo(c.x, 0f); lineTo(c.x + w * .2f, c.y); lineTo(c.x - w * .2f, c.y); close() }, red)
    drawPath(Path().apply { moveTo(c.x, h); lineTo(c.x + w * .2f, c.y); lineTo(c.x - w * .2f, c.y); close() }, ink, style = Stroke(1.6.dp.toPx()))
}

/** The locator's arrow (filled when on). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.locator(c: Color, filled: Boolean) {
    val w = size.width; val h = size.height
    val p = Path().apply { moveTo(w * .9f, h * .1f); lineTo(w * .1f, h * .45f); lineTo(w * .48f, h * .55f); lineTo(w * .58f, h * .92f); close() }
    if (filled) drawPath(p, c) else drawPath(p, c, style = Stroke(1.8.dp.toPx()))
}

/**
 * My dot and the accuracy circle on the mountain (section 1): 16, the blue, a white ring of 3 and a halo of 7; the circle
 * by the accuracy in metres at the view's scale, at least 28. Low accuracy: only the circle; no answer: nothing.
 */
@Composable
internal fun BoxScope.WhereDot(ui: WhereUi, view: LiveView) {
    val s = ui.where.state as? WhereAmI.State.Fixed ?: return
    if (s.fix == Locator.Fix.Outside) return
    var at by remember { mutableStateOf<Pair<Offset, Float>?>(null) }
    LaunchedEffect(s) {
        while (true) {
            withFrameNanos { }
            val p = view.toScreen(s.x, s.y); val q = view.toScreen(s.x + s.accuracy, s.y)
            at = if (p != null) Offset(p[0], p[1]) to (if (q != null) hypot(q[0] - p[0], q[1] - p[1]) else 0f) else null
        }
    }
    val a = at ?: return
    val dot = s.fix != Locator.Fix.Low && s.fix != Locator.Fix.Approx
    Canvas(Modifier.fillMaxSize()) {
        val r = maxOf(a.second, 14.dp.toPx())
        drawCircle(Color(0x242F7BFF), r, a.first)
        drawCircle(Color(0x8C2F7BFF), r, a.first, style = Stroke(1.5.dp.toPx()))
        if (dot) {
            drawCircle(Color(0x402F7BFF), 15.dp.toPx(), a.first)
            drawCircle(Color.White, 8.dp.toPx() + 3.dp.toPx() / 2, a.first)
            drawCircle(YOU_BLUE, 8.dp.toPx() - 3.dp.toPx() / 2, a.first)
        }
    }
}

/**
 * The location's box under the pill (S03, S04): 12 from the sides, 106 from the top; a title and a line, an action at the
 * end when there is one. On a run, a dot of its colour; a tap opens the run's sheet. A live region for screen readers.
 */
@Composable
internal fun BoxScope.WhereBox(ui: WhereUi, night: Boolean, onRun: (String) -> Unit, onAction: () -> Unit, below: Boolean = false) {
    val k = remember(night) { SheetInk(night) }
    val s = ui.where.state
    val words = whereWords(s)
    AnimatedVisibility(words != null, // under the lenses' chips (K08) it moves down to 150 from the top
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = if (below) 106.dp else 62.dp, start = 12.dp, end = 12.dp),
        enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        val w = words ?: return@AnimatedVisibility
        val run = ((s as? WhereAmI.State.Fixed)?.fix as? Locator.Fix.OnRun)?.piste
        // on a run or free: only the accuracy under it (S03), not the height
        val sub = if (s is WhereAmI.State.Fixed && (s.fix is Locator.Fix.OnRun || s.fix == Locator.Fix.Free))
            stringResource(R.string.loc_accuracy, roundAcc(s.accuracy).toString()) else w.sub
        val free = s is WhereAmI.State.Fixed && s.fix == Locator.Fix.Free
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).shadow(10.dp, RoundedCornerShape(18.dp), clip = false).clip(RoundedCornerShape(18.dp))
            .background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .let { m -> if (run != null) m.clickable { onRun(run.key) } else m }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (run != null) Box(Modifier.size(10.dp).clip(CircleShape).background(runColor(run.color)))
            Column(Modifier.weight(1f)) {
                // not on a run nor a lift: only the accuracy, as the title (section 3)
                if (!free) Text(w.title, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
                Text(sub, fontFamily = Plex, fontWeight = if (free) FontWeight.Bold else FontWeight.Normal, fontSize = if (free) 15.sp else 13.sp, color = if (free) k.ink else k.ink2)
            }
            if (w.action != null) Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, k.line, RoundedCornerShape(18.dp))
                .semantics { role = Role.Button }.clickable(onClick = onAction).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(w.action, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = k.ink)
            }
        }
    }
}

private fun runColor(c: String) = when (c) {
    "green" -> Color(0xFF198549); "blue" -> Color(0xFF2F6FD6); "red" -> Color(0xFFD1342B); else -> Color(0xFF1A1A1A)
}

/** S02: before the phone asks, the first time ever: three lines, "continue", "not now", and the line under them. */
@Composable
internal fun BoxScope.WhereAsk(night: Boolean, onGo: () -> Unit, onLater: () -> Unit) {
    LaunchedEffect(Unit) { Qa.log("redesign where ask") }
    val k = remember(night) { SheetInk(night) }
    Box(Modifier.matchParentSize().background(Color(0x61060B16)).clickable(onClick = onLater))
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
        LiveSheet(k) {
            Text(stringResource(R.string.loc_ask_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
            for ((icon, title, sub) in listOf(Triple(Icons.lock, stringResource(R.string.loc_ask_stays_b), stringResource(R.string.loc_ask_stays)),
                Triple(Icons.cloud, stringResource(R.string.loc_ask_offline), null), Triple(Icons.pin, stringResource(R.string.app_loc_ask_mountain), null))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = k.ink) }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontFamily = Plex, fontWeight = if (sub != null) FontWeight.Bold else FontWeight.Normal, fontSize = 14.5.sp, color = k.ink)
                        if (sub != null) Text(sub, fontFamily = Plex, fontSize = 13.sp, color = k.ink2)
                    }
                }
            }
            AccButton(stringResource(R.string.loc_ask_go), k, onClick = onGo)
            TextLink(stringResource(R.string.loc_ask_later), k) { onLater() }
            Small(stringResource(R.string.loc_ask_after_phone), k, Modifier.fillMaxWidth(), center = true)
        }
    }
}

/** The switch's logic (section 3): off, on at once, or the S02 sheet the first time ever (no permission yet). */
@Composable
internal fun rememberLocate(ui: WhereUi, view: LiveView): Pair<() -> Unit, () -> Unit> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("redesign", android.content.Context.MODE_PRIVATE) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) ui.where.start() else ui.where.refused()
    }
    // on, with a place: brought to the middle if off the screen or near its edge (60), once
    val st = ui.where.state
    var centred by remember { mutableStateOf(false) }
    LaunchedEffect(st) {
        if (st == WhereAmI.State.Off) centred = false
        if (!centred && st is WhereAmI.State.Fixed && st.fix != Locator.Fix.Outside) { centred = true; view.center(st.x, st.y, 60 * context.resources.displayMetrics.density) }
        Qa.log("redesign where ${whereTag(st)}")
    }
    val toggle = {
        val w = ui.where
        when {
            w.state != WhereAmI.State.Off -> w.off()
            w.anyPermission -> w.start()
            !prefs.getBoolean(ASKED, false) -> ui.asking = true
            else -> ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val go = {
        ui.asking = false; prefs.edit().putBoolean(ASKED, true).apply()
        ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    return toggle to go
}

/** The box's action: the app's settings (no permission), the location settings (none on), the precise location. */
@Composable
internal fun rememberWhereAction(ui: WhereUi): () -> Unit {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.any { it }) ui.where.start() }
    return {
        when (ui.where.state) {
            WhereAmI.State.Denied -> runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)))
            }
            WhereAmI.State.Unavailable -> runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            else -> ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}

/** The state as the emulator's log names it. */
internal fun whereTag(s: WhereAmI.State): String = when (s) {
    WhereAmI.State.Off -> "off"
    WhereAmI.State.Waiting -> "waiting"
    WhereAmI.State.Denied -> "denied"
    WhereAmI.State.Unavailable -> "unavailable"
    is WhereAmI.State.Fixed -> when (val f = s.fix) {
        Locator.Fix.Outside -> "outside"; Locator.Fix.Approx -> "approx"; Locator.Fix.Low -> "low"; Locator.Fix.Free -> "free"
        is Locator.Fix.OnRun -> "run ${f.piste.key}"; is Locator.Fix.OnLift -> "lift ${f.lift.name}"
    }
}

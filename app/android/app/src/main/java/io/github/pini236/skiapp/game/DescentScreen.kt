package io.github.pini236.skiapp.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Profile
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Synth
import io.github.pini236.skiapp.home.SignShape
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Ski
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

// one look: a bright winter day, the same in both themes (the site's descent page)
private val INK = Color(0xFF13233A)
private val MUTED = Color(0xFF4B5A6F)
private val RULE = Color(0xFFCBD5DF)
private val ACCENT = Color(0xFFF4B942)
private val WARN = Color(0xFFF08A3C)

/** What the emulator run reaches into: start the run now, or ski it to the bottom at once. */
internal object DescentQa { var go: (() -> Unit)? = null; var bottom: (() -> Unit)? = null }

private enum class Ride { MENU, PLAY, END }

/** A time as the site's fmt(): m:ss.s. */
fun descentTime(s: Float): String { val m = floor(s / 60).toInt(); val r = s - m * 60; return "$m:" + (if (r < 10) "0" else "") + String.format(Locale.US, "%.1f", r) }

/**
 * The crew's descent on the phone (13.6), the site's game (site/games/descent): five real runs, one button, the
 * others to pick up on the way, the avalanche behind and the ghost of the best run. Three stars a run (the lift, the
 * whole crew, the run's mission), points as on the site; the best time and its ghost are kept on the phone, and the
 * best points go to the group's table. The others have no names in the app (Pini, 4.10.2026), only coats; [names] can
 * give a coat a name later (the group's names, docs/ROADMAP.md).
 */
@Composable
fun DescentScreen(profiles: List<Profile>, haptics: Haptics, onBack: () -> Unit, onBest: () -> Unit = {}, names: (Int) -> String? = { null }) {
    val context = LocalContext.current
    val synth = remember { Synth(context) }
    val still = remember { Motion.reduced(context) }
    val prefs = remember { context.getSharedPreferences("descent", Context.MODE_PRIVATE) }
    var me by remember { mutableIntStateOf(prefs.getInt("me", 5).coerceIn(0, COATS.size - 1)) }
    var runIdx by remember { mutableIntStateOf(prefs.getInt("run", 2)) }
    var useAva by remember { mutableStateOf(prefs.getBoolean("ava", true)) }
    var useGhost by remember { mutableStateOf(prefs.getBoolean("ghost", true)) }
    var phase by remember { mutableStateOf(Ride.MENU) }
    var game by remember { mutableStateOf<DescentGame?>(null) }
    var menuV by remember { mutableIntStateOf(0) }
    // what the run says, and its numbers, as they change
    var pop by remember { mutableStateOf<Say?>(null) }; var popN by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf<Triple<Say?, Say, Boolean>?>(null) }; var bannerN by remember { mutableIntStateOf(0) }
    var tip by remember { mutableStateOf<Say?>(null) }; var tipN by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(1) }; var comboN by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf(Hud()) }
    var hiss by remember { mutableStateOf<Synth.Hiss?>(null) }
    var newBest by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    if (profiles.isEmpty()) { Box(Modifier.fillMaxSize().background(Color(0xFF5E9BD6))) { Text(stringResource(R.string.app_loading), Modifier.align(Alignment.Center), color = Color.White) }; return }
    runIdx = runIdx.coerceIn(0, profiles.size - 1)
    val view = remember {
        DescentView(context, still).apply {
            val l = Lang.current(context.resources)
            text = Lang.typeface(context, l, false); display = Lang.typeface(context, l, true)
            words = { id, a -> context.getString(id, *a) }
        }
    }
    view.names = names
    val density = LocalDensity.current
    view.mapTop = WindowInsets.statusBars.getTop(density) + with(density) { 92.dp.toPx() }
    val course = remember(runIdx, me) { Course(profiles[runIdx], runIdx, me) }
    if (phase == Ride.MENU) view.preview = course

    fun stars(i: Int) = Bests.level(context, "descent", i + 1)
    fun bestTime(key: String) = prefs.getFloat("best-$key", -1f)

    // usage statistics: level = the run, score = the points; best = a new record of the points (the contract, and the
    // group's table; on the site it is still a faster time, S-33)
    var started by remember { mutableStateOf(0L) }
    fun gameEnd(g: DescentGame, ok: Boolean) {
        if (started == 0L) return
        val best = Bests.ended(context, "descent", null, g.score)
        Telemetry.event("game_end", mapOf("game" to "descent", "level" to g.c.key, "score" to g.score,
            "seconds" to ((System.currentTimeMillis() - started) / 1000.0).roundToInt(), "completed" to ok, "best" to best))
        started = 0L
        if (best) onBest()
    }
    fun playing() = phase == Ride.PLAY && game?.state != DescentGame.State.END
    fun stop() { hiss?.stop(); hiss = null }
    fun toMenu() { game?.let { if (playing()) gameEnd(it, false) }; stop(); view.game = null; game = null; phase = Ride.MENU; menuV++ }
    fun start() {
        game?.let { if (playing()) gameEnd(it, false) }
        val c = Course(profiles[runIdx], runIdx, me)
        val ghost = if (useGhost) DescentGame.Ghost.load(prefs.getString("ghost-${c.key}", null)) else null
        pop = null; banner = null; tip = null; combo = 1; newBest = false; copied = false
        val out = object : DescentGame.Out {
            override fun pop(say: Say) { pop = say; popN++; Qa.log("descent pop ${context.resources.getResourceEntryName(say.id)}") }
            override fun banner(big: Say?, text: Say, dog: Boolean) { banner = Triple(big, text, dog); bannerN++; Qa.log("descent banner ${context.resources.getResourceEntryName(text.id)}") }
            override fun coach(id: String, say: Say, force: Boolean) {
                if (!force && prefs.getBoolean("coach-$id", false)) return
                prefs.edit().putBoolean("coach-$id", true).apply(); tip = say; tipN++
            }
            override fun tone(f: Double, d: Double, v: Float, wave: Int, end: Double) = synth.tone(0.0, f, f * end, d, v, tri = wave == DescentGame.TRI, square = wave == DescentGame.SQUARE)
            override fun buzz(kind: Int) = when (kind) { DescentGame.TICK -> haptics.tick(.5f); DescentGame.CLICK -> haptics.click(.6f); else -> haptics.thud(.9f) }
            override fun combo(n: Int) { combo = n; comboN++ }
            override fun finished(ok: Boolean) {
                val g = game ?: return
                Bests.setLevel(context, "descent", runIdx + 1, g.stars)
                val prev = bestTime(g.c.key)
                val best = ok && (prev < 0 || g.t < prev)
                if (best) { prefs.edit().putFloat("best-${g.c.key}", g.t).putInt("bestWho-${g.c.key}", me).putString("ghost-${g.c.key}", g.record().save()).apply() }
                newBest = best
                gameEnd(g, ok)
                hiss?.gain = 0f
                phase = Ride.END; menuV++
                Qa.log("descent done ${g.c.key} ${if (ok) "lift" else "caught"} ${g.stars} stars ${g.score} points")
            }
        }
        val g = DescentGame(c, me, out, useAva, ghost, still = still)
        game = g; view.preview = null; view.game = g
        hiss?.stop(); hiss = synth.hiss().apply { freq = 700.0 }
        started = System.currentTimeMillis()
        Telemetry.event("game_start", mapOf("game" to "descent", "level" to c.key))
        phase = Ride.PLAY
        Qa.log("descent start ${c.key}")
    }
    view.onFrame = { g ->
        val n = Hud.of(g)
        if (n != hud) hud = n
        hiss?.let { h -> h.gain = if (g.state == DescentGame.State.RUN || g.state == DescentGame.State.FIN) min(.35f, g.s / 80 + (if (g.c.zoneAt(g.x)?.kind == Course.WIND) .15f else 0f)) * .6f else 0f }
    }
    DisposableEffect(Unit) {
        DescentQa.go = { if (phase != Ride.PLAY) start() }
        DescentQa.bottom = { game?.toBottom() }
        onDispose { game?.let { if (playing()) gameEnd(it, false) }; hiss?.stop(); DescentQa.go = null; DescentQa.bottom = null }
    }
    BackHandler(enabled = phase != Ride.MENU) { toMenu() }

    Box(Modifier.fillMaxSize().background(Color(0xFF5E9BD6))) {
        AndroidView({ view }, Modifier.fillMaxSize().semantics { contentDescription = context.getString(R.string.game_descent_canvas_label) })
        when (phase) {
            Ride.MENU -> Shade { Menu(profiles, me, runIdx, useAva, useGhost, menuV, ::stars, ::bestTime, onBack,
                onCoat = { me = it; prefs.edit().putInt("me", it).apply() }, onRun = { runIdx = it; prefs.edit().putInt("run", it).apply() },
                onAva = { useAva = !useAva; prefs.edit().putBoolean("ava", useAva).apply() }, onGhost = { useGhost = !useGhost; prefs.edit().putBoolean("ghost", useGhost).apply() },
                onGo = { start() }) }
            Ride.PLAY -> game?.let { g ->
                PlayHud(g, hud, combo, comboN, still)
                Banner(banner, bannerN, still)
                Pop(pop, popN, still)
                Pad(hud, tip, tipN, still, onDown = { g.press(); haptics.tick(.3f) }, onUp = { g.release() })
                if (hud.count != null) Count(g, hud.count!!)
            }
            Ride.END -> game?.let { g -> Shade { End(g, newBest, bestTime(g.c.key), copied,
                onAgain = { start() }, onMenu = { toMenu() },
                onCopy = {
                    val ctx = context
                    val text = if (g.ok) ctx.getString(R.string.game_descent_share_result, g.c.key, descentTime(g.t), "★".repeat(g.stars) + "☆".repeat(3 - g.stars), g.flips.toString(), g.stats.pops.toString(), g.got.toString(), g.stats.coins.toString())
                        else ctx.getString(R.string.game_descent_share_caught, g.c.key, g.x.roundToInt().toString())
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.game_descent_title), text))
                    copied = true; Qa.log("descent copied")
                }) } }
        }
    }
}

/** The numbers on the screen during a run, compared each frame so the words change only when they do. */
private data class Hud(val time: String = "", val kmh: Int = 0, val alt: Int = 0, val gap: Int? = null, val crew: String = "", val coins: Int = 0,
                       val pad: Int = 0, val load: Int = 0, val count: String? = null) {
    companion object {
        fun of(g: DescentGame): Hud {
            val gi = g.ghostAt(g.t)
            val crew = buildString { for (sp in g.c.spots) append(if (sp.lost) 'x' else if (g.crew.any { it.coat == sp.coat }) 'o' else '.') }
            val pad = if (g.air) 1 else if (g.holding && g.loading) 2 else 0
            val count = if (g.state == DescentGame.State.COUNT) { val n = kotlin.math.ceil(g.countT).toInt(); if (n > 0) "$n" else "" } else null
            return Hud(descentTime(g.t), (g.s * 3.6f).roundToInt(), g.c.realH(g.x).roundToInt(), if (gi >= 0) (g.x - g.ghost!!.r[gi + 1]).roundToInt() else null,
                crew, g.stats.coins, pad, if (pad == 2) (min(1f, g.loadT / .32f) * 20).roundToInt() else 0, count)
        }
    }
}

// ---------- the menu and the end ----------

@Composable
private fun Shade(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x405E9BD6), .6f to Color(0xB80D1522), 1f to Color(0xB80D1522)))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun H2(text: String) = Text(text, style = Ski.type.title.copy(fontSize = (28 * Ski.type.displayScale).sp, lineHeight = 1.em), color = Color.White)

@Composable
private fun Note(text: String) = Text(text, style = Ski.type.small.copy(fontSize = 11.5.sp, lineHeight = 1.4.em), color = Color.White.copy(alpha = .82f))

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("UNUSED_PARAMETER") // a new [menuV] reads the stars and the best times again
private fun Menu(profiles: List<Profile>, me: Int, run: Int, ava: Boolean, ghost: Boolean, menuV: Int, stars: (Int) -> Int, best: (String) -> Float, onBack: () -> Unit,
                 onCoat: (Int) -> Unit, onRun: (Int) -> Unit, onAva: () -> Unit, onGhost: () -> Unit, onGo: () -> Unit) {
    val scale = Ski.type.displayScale
    BackLink(stringResource(R.string.game_descent_back_to_games).trim('→', '←', ' '), onBack, Color.White)
    Text(stringResource(R.string.game_descent_title), style = Ski.type.title.copy(fontSize = (58 * scale).sp, lineHeight = .9.em, shadow = Shadow(INK, Offset(0f, 3f), 0f)), color = Color.White)
    Text(stringResource(R.string.game_descent_intro), Modifier.widthIn(max = 520.dp), style = Ski.type.body.copy(fontSize = 14.sp, lineHeight = 1.45.em), color = Color.White)
    H2(stringResource(R.string.game_descent_your_coat))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in COATS.indices.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in row) Coat(i, i == me, Modifier.weight(1f)) { onCoat(i) }
        }
    }
    H2(stringResource(R.string.game_descent_which_run))
    // the runs as signs on a post, each shorter than the one above
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth().drawBehind {
        val w = 7.dp.toPx(); drawRect(INK, Offset(if (rtl) size.width - w else 0f, -6.dp.toPx()), Size(w, size.height + 16.dp.toPx()))
    }.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profiles.forEachIndexed { i, p -> RunSign(i, p, i == run, stars(i), best(p.key)) { onRun(i) } }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(stringResource(R.string.game_descent_opt_avalanche), ava, onAva)
        Chip(stringResource(R.string.game_descent_opt_ghost), ghost, onGhost)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        How(R.string.game_descent_how_hold_title, R.string.game_descent_how_hold_text, Modifier.weight(1f))
        How(R.string.game_descent_how_release_title, R.string.game_descent_how_release_text, Modifier.weight(1f))
    }
    Legend()
    GoButton(stringResource(R.string.game_descent_go), onGo)
    Note(stringResource(R.string.game_descent_profile_note))
}

/** A coat to ski in (the site's friend buttons, without names): the skier's little picture in that coat. */
@Composable
private fun Coat(i: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val col = Color(COATS[i].color); val name = stringResource(COATS[i].name)
    Box(modifier.heightIn(min = 64.dp).drawBehind { drawRect(col, size = Size(size.width, 8.dp.toPx())) }.padding(top = 8.dp)
        .background(if (on) Color.White else Color.White.copy(alpha = .9f)).then(if (on) Modifier.border(3.dp, ACCENT) else Modifier)
        .clickable(role = Role.RadioButton, onClick = onClick).semantics { contentDescription = name; selected = on }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(36.dp)) {
            // the site's svg, viewBox -15 -28 30 30
            val k = size.width / 30f; fun p(x: Float, y: Float) = Offset((x + 15) * k, (y + 28) * k)
            drawPath(Path().apply { moveTo(p(-8f, -2f).x, p(-8f, -2f).y); lineTo(p(-7f, -15f).x, p(-7f, -15f).y); quadraticTo(p(0f, -22f).x, p(0f, -22f).y, p(7f, -15f).x, p(7f, -15f).y); lineTo(p(8f, -2f).x, p(8f, -2f).y); close() }, col)
            drawCircle(Color(0xFFF4F7FB), 5.5f * k, p(0f, -21f)); drawCircle(INK, 5.5f * k, p(0f, -21f), style = Stroke(1.4f * k))
            drawRect(INK, p(-5f, -23f), Size(10 * k, 3 * k))
            drawLine(INK, p(-13f, 0f), p(13f, 0f), 3 * k, StrokeCap.Round)
        }
    }
}

@Composable
private fun RunSign(i: Int, p: Profile, on: Boolean, stars: Int, best: Float, onClick: () -> Unit) {
    val scale = Ski.type.displayScale
    val c = remember(p) { Course(p, i, 0) } // the run as ridden: from its top to its lowest point
    val mission = MISSIONS[p.key]
    val off by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = .55f, stiffness = 500f), label = "run")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val stats = stringResource(R.string.game_descent_run_card_stats, String.format(Locale.US, "%.1f", c.len / 1000), (c.top - c.bot).toString()) +
        (if (best > 0) stringResource(R.string.game_descent_best_suffix, descentTime(best)) else "")
    Column(Modifier.fillMaxWidth(1f - i * .05f).absoluteOffset(x = (-10 * off).dp).heightIn(min = 58.dp)
        .background(Color(runColor(p.color)), SignShape(18.dp))
        .then(if (on) Modifier.drawBehind { drawRect(Color.White.copy(alpha = .6f), Offset(0f, size.height - 5.dp.toPx()), Size(size.width, 5.dp.toPx())) } else Modifier)
        .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = on }
        .padding(start = if (rtl) 30.dp else 16.dp, end = if (rtl) 16.dp else 30.dp, top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(p.key, style = Ski.type.title.copy(fontSize = (30 * scale).sp, lineHeight = 1.em), color = Color.White, maxLines = 1)
            }
            Text("★".repeat(stars) + "☆".repeat(3 - stars), Modifier.semantics { contentDescription = "" }, style = Ski.type.body.copy(fontSize = 15.sp, letterSpacing = 1.sp), color = ACCENT, maxLines = 1)
        }
        Text(stats, style = Ski.type.small.copy(fontSize = 12.5.sp), color = Color.White.copy(alpha = .95f))
        if (mission != null) Text("★ " + stringResource(mission.text), style = Ski.type.small.copy(fontSize = 12.5.sp), color = Color.White.copy(alpha = .95f))
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).background(if (on) Color.White else Color.Transparent).border(2.dp, Color.White)
        .clickable(role = Role.Switch, onClick = onClick).semantics { selected = on }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.bodyBold, color = if (on) INK else Color.White)
    }
}

@Composable
private fun How(title: Int, text: Int, modifier: Modifier) {
    Column(modifier.drawBehind { drawRect(ACCENT, size = Size(size.width, 3.dp.toPx())) }.background(Color.White.copy(alpha = .14f))
        .padding(start = 8.dp, end = 8.dp, top = 11.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = Ski.type.title.copy(fontSize = (22 * Ski.type.displayScale).sp, lineHeight = 1.em), color = Color.White)
        Text(stringResource(text), style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 1.3.em), color = Color.White)
    }
}

/** What is on the run, drawn with the same shapes as the game (the site's legend). */
@Composable
private fun Legend() {
    val items = listOf(R.string.game_descent_legend_khachapuri, R.string.game_descent_legend_rock, R.string.game_descent_legend_gully,
        R.string.game_descent_legend_snowcat, R.string.game_descent_legend_snow, R.string.game_descent_legend_wind)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in items.indices.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (k in row) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(24.dp)) { scaleTo24 { legendIcon(k) } }
                Text(stringResource(items[k]), style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 1.3.em), color = Color.White)
            }
        }
    }
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.scaleTo24(block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
    scale(size.width / 24f, size.width / 24f, Offset.Zero) { block() }
private fun androidx.compose.ui.graphics.drawscope.DrawScope.legendIcon(k: Int) {
    fun poly(vararg xy: Float) = Path().apply { moveTo(xy[0], xy[1]); var i = 2; while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }; close() }
    when (k) {
        0 -> { drawOval(Color(0xFFC9822E), Offset(2f, 7f), Size(20f, 10f)); drawOval(Color(0xFFF7E3A1), Offset(5f, 8.5f), Size(14f, 6f)); drawCircle(Color(0xFFF4A11D), 2f, Offset(12f, 11.5f)) }
        1 -> { drawPath(poly(3f, 20f, 6f, 9f, 12f, 5f, 18f, 8f, 21f, 20f), Color(0xFF5B6675)); drawPath(poly(6f, 9f, 12f, 4f, 18f, 8f, 14f, 9f, 9f, 9f), Color.White) }
        2 -> { drawRect(Color(0xFF6F88A3), Offset(2f, 6f), Size(20f, 12f)); drawRect(Color(0xFF3E6E9C), Offset(2f, 14f), Size(20f, 4f)) }
        3 -> { drawRect(Color(0xFF2A2F38), Offset(4f, 12f), Size(16f, 5f)); drawRect(Color(0xFFD1342B), Offset(5f, 7f), Size(12f, 5f)); drawRect(Color(0xFFD1342B), Offset(11f, 3f), Size(5f, 4f)) }
        4 -> for ((x, y, r) in listOf(Triple(6f, 16f, 5f), Triple(13f, 15f, 6f), Triple(19f, 16f, 4f))) { drawCircle(Color.White, r, Offset(x, y)); drawCircle(Color(0xFF8FA7C0), r, Offset(x, y), style = Stroke(1f)) }
        else -> { drawLine(Color.White, Offset(2f, 8f), Offset(16f, 8f), 2.4f); drawLine(Color.White, Offset(5f, 13f), Offset(22f, 13f), 2.4f); drawLine(Color.White, Offset(2f, 18f), Offset(14f, 18f), 2.4f) }
    }
}

@Composable
private fun GoButton(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).drawBehind { drawRect(INK, Offset(0f, 5.dp.toPx()), size) }.background(ACCENT)
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.title.copy(fontSize = (34 * Ski.type.displayScale).sp, lineHeight = 1.em), color = INK, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OutlineButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).border(2.dp, Color.White).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.bodyBold, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun End(g: DescentGame, newBest: Boolean, best: Float, copied: Boolean, onAgain: () -> Unit, onMenu: () -> Unit, onCopy: () -> Unit) {
    val scale = Ski.type.displayScale; val c = g.c
    H2(stringResource(if (g.ok) (if (g.got == 5) R.string.game_descent_end_all_crew else R.string.game_descent_end_reached_lift_all) else R.string.game_descent_end_caught))
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).rotate(-2f).shadow(10.dp).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(c.key + (if (c.lift.isNotEmpty()) stringResource(R.string.game_descent_end_to_lift_suffix, c.lift) else ""), style = Ski.type.label.copy(fontSize = 12.sp), color = MUTED)
        Text(if (g.ok) descentTime(g.t) else stringResource(R.string.game_descent_end_distance, g.x.roundToInt().toString(), java.text.NumberFormat.getIntegerInstance(Locale.US).format(c.len.roundToInt())),
            style = Ski.type.title.copy(fontSize = (64 * scale).sp, lineHeight = .85.em), color = INK)
        Row { for (k in 0 until 3) Text("★", style = Ski.type.body.copy(fontSize = 30.sp, letterSpacing = 4.sp), color = if (g.goals[k]) ACCENT else RULE) }
        Row(Modifier.fillMaxWidth().drawBehind { drawRect(RULE, size = Size(size.width, 1.dp.toPx())) }.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat(R.string.game_descent_stat_flips, "${g.flips}", Modifier.weight(1f))
            Stat(R.string.game_descent_stat_crew, "${g.got}/5", Modifier.weight(1f))
            Stat(R.string.game_descent_stat_khachapuri, "${g.stats.coins}/${g.coinsTotal}", Modifier.weight(1f))
            Stat(R.string.game_descent_stat_points, "${g.score}", Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().drawBehind { drawRect(RULE, size = Size(size.width, 1.dp.toPx())) }.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val lines = listOf(stringResource(R.string.game_descent_goal_reach_lift), stringResource(R.string.game_descent_goal_bring_crew_count, g.got.toString()),
                MISSIONS[c.key]?.let { stringResource(it.text) } ?: "")
            lines.forEachIndexed { k, s ->
                val ok = g.goals[k]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (ok) "★" else "☆", style = Ski.type.body.copy(fontSize = 13.sp), color = if (ok) ACCENT else RULE)
                    Text(s, style = (if (ok) Ski.type.bodyBold else Ski.type.body).copy(fontSize = 13.sp), color = if (ok) INK else MUTED)
                }
            }
        }
        val bestLine = if (newBest) stringResource(R.string.game_descent_end_new_best) else if (best > 0) stringResource(R.string.game_descent_end_best, descentTime(best)) else ""
        if (bestLine.isNotEmpty()) Text(bestLine, style = Ski.type.small.copy(fontSize = 13.sp), color = MUTED)
    }
    GoButton(stringResource(R.string.game_descent_again), onAgain)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlineButton(stringResource(R.string.game_descent_to_menu), Modifier.weight(1f), onMenu)
        OutlineButton(stringResource(R.string.game_descent_copy_result), Modifier.weight(1f), onCopy)
    }
    if (copied) Note(stringResource(R.string.game_descent_copied))
}

@Composable
private fun Stat(label: Int, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(label), style = Ski.type.small.copy(fontSize = 11.5.sp), color = MUTED, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = Ski.type.title.copy(fontSize = (28 * Ski.type.displayScale).sp, lineHeight = 1.em), color = INK, maxLines = 1)
    }
}

// ---------- on the run ----------

@Composable
private fun PlayHud(g: DescentGame, h: Hud, combo: Int, comboN: Int, still: Boolean) {
    val scale = Ski.type.displayScale
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        // the run's sign, and the time, the speed, the height and the ghost
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(Modifier.background(Color(runColor(g.c.color)), SignShape(16.dp)).padding(start = 26.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(g.c.key, style = Ski.type.title.copy(fontSize = (26 * scale).sp, lineHeight = 1.em), color = Color.White) }
            }
            Column(Modifier.background(INK.copy(alpha = .84f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(h.time, style = Ski.type.title.copy(fontSize = (36 * scale).sp, lineHeight = .9.em), color = Color.White)
                Text("${h.kmh} ${stringResource(R.string.game_descent_hud_speed_unit)} · ${stringResource(R.string.game_descent_meters, java.text.NumberFormat.getIntegerInstance(Locale.US).format(h.alt))}",
                    style = Ski.type.bodyBold.copy(fontSize = 11.sp), color = ACCENT)
                h.gap?.let { Text(stringResource(if (it >= 0) R.string.game_descent_hud_gap_ahead else R.string.game_descent_hud_gap_behind, kotlin.math.abs(it).toString()),
                    style = Ski.type.bodyBold.copy(fontSize = 11.sp), color = ACCENT) }
            }
        }
        // the others: a square each, on once picked up, faint when lost; and the khachapuri. On the side the reading starts
        // (the right in Hebrew, as on the site; in the other languages the left, under the run's sign, clear of the time)
        Row(Modifier.align(Alignment.TopStart).padding(top = 62.dp, start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            g.c.spots.forEachIndexed { k, sp ->
                val st = h.crew.getOrNull(k) ?: '.'
                Box(Modifier.size(14.dp).alpha(when (st) { 'o' -> 1f; 'x' -> .12f; else -> .35f }).background(Color(COATS[sp.coat].color)).border(2.dp, Color.White))
            }
            Text("${h.coins} 🥟", Modifier.padding(start = 6.dp), style = Ski.type.bodyBold.copy(fontSize = 13.sp, shadow = Shadow(INK.copy(alpha = .6f), Offset(0f, 1f), 2f)), color = Color.White)
        }
        // the combo, on the left
        val bump = remember { Animatable(1f) }
        LaunchedEffect(comboN) { if (comboN > 0 && !still) { bump.snapTo(1.25f); bump.animateTo(1f, tween(200)) } }
        Text(stringResource(R.string.game_descent_combo, combo.toString()), Modifier.align(AbsoluteAlignment.TopLeft).padding(top = 128.dp, start = 64.dp)
            .graphicsLayer { scaleX = bump.value; scaleY = bump.value }.alpha(if (combo > 1) 1f else .45f).background(ACCENT).padding(horizontal = 10.dp, vertical = 3.dp),
            style = Ski.type.title.copy(fontSize = (24 * scale).sp, lineHeight = 1.em), color = INK)
        // the sound, on the left under the sign, as the site's (A-41)
        SoundKey(stringResource(R.string.game_descent_sound), Modifier.align(AbsoluteAlignment.TopLeft).padding(top = 120.dp, start = 12.dp))
    }
}

/** A warning sliding in from the right (the site's .banner), for 2.6 seconds. */
@Composable
private fun Banner(b: Triple<Say?, Say, Boolean>?, n: Int, still: Boolean) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(n) { if (n > 0) { on = true; kotlinx.coroutines.delay(2600); on = false } }
    val k by animateFloatAsState(if (on) 0f else 1.1f, if (still) tween(0) else spring(dampingRatio = .6f, stiffness = 400f), label = "banner")
    if (b == null || k >= 1.09f) return
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.align(AbsoluteAlignment.TopRight).padding(top = 122.dp, end = 12.dp).widthIn(max = maxWidth - 120.dp)
            .graphicsLayer { translationX = size.width * k }
            .background(WARN, AbsoluteArrowLeft).padding(start = 30.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val big = if (b.third) "🐕" else b.first?.let { context.say(it) }
            if (big != null) Text(big, style = Ski.type.title.copy(fontSize = (24 * Ski.type.displayScale).sp, lineHeight = 1.em), color = INK, maxLines = 1)
            Text(context.say(b.second), style = Ski.type.bodyBold.copy(fontSize = 15.sp, lineHeight = 1.25.em), color = INK)
        }
    }
}

/** A sign with its arrow on the left, whatever the language (the site's banner clip-path). */
private val AbsoluteArrowLeft = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val a = with(density) { 18.dp.toPx() }
        return androidx.compose.ui.graphics.Outline.Generic(Path().apply { moveTo(0f, size.height / 2); lineTo(a, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(a, size.height); close() })
    }
}

@Composable
private fun Pop(say: Say?, n: Int, still: Boolean) {
    val context = LocalContext.current
    val t = remember { Animatable(1f) }
    LaunchedEffect(n) { if (n > 0) { t.snapTo(0f); t.animateTo(1f, tween(1100, easing = LinearEasing)) } }
    if (say == null || t.value >= 1f) return
    val k = t.value
    val alpha = if (still) 1f else when { k < .2f -> k / .2f; k < .75f -> 1f; else -> 1 - (k - .75f) / .25f }
    val sc = if (still) 1f else when { k < .2f -> .6f + (k / .2f) * .48f; k < .3f -> 1.08f - (k - .2f) / .1f * .08f; else -> 1f }
    val dy = if (still) 0f else when { k < .2f -> 20f * (1 - k / .2f); k < .75f -> 0f; else -> -30f * (k - .75f) / .25f }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Text(context.say(say), Modifier.align(Alignment.TopCenter).offset(y = maxHeight * .34f - 28.dp + dy.dp).graphicsLayer { this.alpha = alpha; scaleX = sc; scaleY = sc },
            style = Ski.type.title.copy(fontSize = (minOf(56f, maxOf(34f, maxWidth.value * .11f)) * Ski.type.displayScale).sp, lineHeight = 1.em,
                shadow = Shadow(INK, Offset(0f, 6f), 0f)), color = Color.White, maxLines = 1, softWrap = false)
    }
}

/** The one button (the site's .pad): hold to crouch and load (it fills), let go to jump; in the air, hold to flip. */
@Composable
private fun Pad(h: Hud, tip: Say?, tipN: Int, still: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    val context = LocalContext.current
    var down by remember { mutableStateOf(false) }
    var coaching by remember { mutableStateOf(false) }
    LaunchedEffect(tipN) { if (tipN > 0) { coaching = true; kotlinx.coroutines.delay(3000); coaching = false } }
    val pulse = rememberInfiniteTransition(label = "coach").animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse")
    val (big, small) = when (h.pad) {
        1 -> R.string.game_descent_coach_flip to R.string.game_descent_coach_flip_hint
        2 -> R.string.game_descent_coach_release to R.string.game_descent_coach_release_hint
        else -> R.string.game_descent_jump to R.string.game_descent_coach_jump_hint
    }
    val label = stringResource(R.string.game_descent_jump)
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (coaching && tip != null) {
                Text(context.say(tip), Modifier.padding(bottom = 10.dp).shadow(6.dp).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp),
                    style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = INK)
            }
            val k = if (coaching && !still) pulse.value else if (coaching) 1f else 0f
            Row(Modifier.fillMaxWidth().heightIn(min = 84.dp)
                .graphicsLayer { if (down) { scaleX = .97f; scaleY = .97f } }
                .drawBehind { if (k > 0) drawRect(ACCENT.copy(alpha = .45f * k), Offset(-6.dp.toPx() * k, -6.dp.toPx() * k), Size(size.width + 12.dp.toPx() * k, size.height + 12.dp.toPx() * k)) }
                .background(if (down) ACCENT else INK.copy(alpha = .55f))
                .drawBehind { if (h.load > 0) drawRect(Color.White.copy(alpha = .35f), size = Size(size.width * h.load / 20f, size.height)) }
                .border(2.dp, if (down) ACCENT else if (k > .5f) ACCENT else Color.White.copy(alpha = .75f))
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume(); down = true; onDown(); waitForUpOrCancellation(); down = false; onUp() } }
                .semantics { role = Role.Button; contentDescription = label; onClick { onDown(); onUp(); true } }
                .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val fg = if (down) INK else Color.White
                Canvas(Modifier.size(26.dp)) { val s = size.width / 34f
                    drawLine(fg, Offset(17 * s, 26 * s), Offset(17 * s, 8 * s), 3.2f * s)
                    drawPath(Path().apply { moveTo(9 * s, 15 * s); lineTo(17 * s, 7 * s); lineTo(25 * s, 15 * s) }, fg, style = Stroke(3.2f * s)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(big), style = Ski.type.title.copy(fontSize = (28 * Ski.type.displayScale).sp, lineHeight = 1.em), color = fg, maxLines = 1)
                    Text(stringResource(small), style = Ski.type.small.copy(fontSize = 12.sp, lineHeight = 1.25.em), color = fg.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 3, 2, 1, go: and the run's three stars. */
@Composable
private fun Count(g: DescentGame, n: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        Text(n.ifEmpty { stringResource(R.string.game_descent_count_go) }, style = Ski.type.title.copy(fontSize = 140.sp, lineHeight = .8.em, shadow = Shadow(INK, Offset(0f, 10f), 0f)), color = Color.White, maxLines = 1)
        Column(Modifier.widthIn(max = 360.dp).background(INK.copy(alpha = .86f)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.game_descent_count_three_stars, g.c.key), style = Ski.type.bodyBold.copy(fontSize = 12.sp), color = ACCENT)
            for (s in listOf(stringResource(R.string.game_descent_goal_reach_lift), stringResource(R.string.game_descent_goal_bring_crew), MISSIONS[g.c.key]?.let { stringResource(it.text) } ?: ""))
                Text("★ $s", style = Ski.type.body.copy(fontSize = 14.sp), color = Color.White)
        }
    }
}

package io.github.pini236.skiapp.game

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Synth
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Ski
import kotlin.math.abs
import kotlin.math.roundToInt

// one look: a bright winter afternoon, the same in both themes (the site's snowball page)
private val INK = Color(0xFF13233A)
private val MUTED = Color(0xFF4B5A6F)
private val RULE = Color(0xFFCBD5DF)
private val ACCENT = Color(0xFFF4B942)

/** What the emulator run reaches into: the fight now, won at once (every character out). */
internal object SnowballQa { var win: (() -> Unit)? = null }

private enum class Fight { MENU, PLAY, END }

/**
 * The snowball fight on the phone (13.6), the site's game (site/games/snowball): you behind your wall, the characters
 * behind theirs; stand up and aim with a finger, let go to throw, and duck before their ball arrives. A ladder of five
 * rungs, each with three stars (to win, without being hit, the rung's goal); a rung opens when the one before it has a
 * star. The characters have no names in the app (Pini, 4.10.2026), only coats and hats: you pick your coat; [names] can
 * give a coat a name later (the group's names, docs/ROADMAP.md).
 */
@Composable
fun SnowballScreen(haptics: Haptics, onBack: () -> Unit, onBest: () -> Unit = {}, names: (Int) -> String? = { null }) {
    val context = LocalContext.current
    val synth = remember { Synth(context) }
    val still = remember { Motion.reduced(context) }
    val prefs = remember { context.getSharedPreferences("snowball", Context.MODE_PRIVATE) }
    var me by remember { mutableIntStateOf(prefs.getInt("me", 5).coerceIn(0, COATS.size - 1)) }
    var rung by remember { mutableIntStateOf(prefs.getInt("rung", 0).coerceIn(0, RUNGS.size - 1)) }
    var phase by remember { mutableStateOf(Fight.MENU) }
    var fight by remember { mutableStateOf<SnowballFight?>(null) }
    var result by remember { mutableStateOf<SnowballFight.Result?>(null) }
    var hpV by remember { mutableIntStateOf(0) } // bumps when anyone's balls change
    var starsV by remember { mutableIntStateOf(0) }
    var pop by remember { mutableStateOf<Say?>(null) }
    var popN by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf(true) }
    val view = remember { SnowballView(context, still) }
    fun stars(i: Int) = Bests.level(context, "snowball", i + 1)

    // usage statistics, as the site: level = the rung, score = hits, completed = won; the record is the most hits
    var started by remember { mutableStateOf(0L) }
    fun gameEnd(won: Boolean): Boolean {
        if (started == 0L) return false
        val hits = fight?.stats?.hits ?: 0
        val best = Bests.ended(context, "snowball", null, hits)
        Telemetry.event("game_end", mapOf("game" to "snowball", "level" to rung + 1, "score" to hits,
            "seconds" to ((System.currentTimeMillis() - started) / 1000.0).roundToInt(), "completed" to won, "best" to best))
        started = 0L
        if (best) onBest()
        return best
    }
    fun playing() = phase == Fight.PLAY && result == null
    fun toMenu() { if (playing()) gameEnd(false); view.fight = null; fight = null; phase = Fight.MENU; starsV++ }
    fun start() {
        if (playing()) gameEnd(false)
        result = null; pop = null; hint = true
        val out = object : SnowballFight.Out {
            override fun hp() { hpV++ }
            override fun pop(say: Say) { pop = say; popN++; Qa.log("snowball pop ${context.resources.getResourceEntryName(say.id)}") }
            override fun noise(d: Double, v: Float, f: Double, type: SnowballFight.Noise) =
                synth.grain(0.0, d, v, when (type) { SnowballFight.Noise.LOW -> Synth.Filter.LOW; SnowballFight.Noise.HIGH -> Synth.Filter.HIGH; else -> Synth.Filter.BAND }, f, 1.0)
            override fun tone(f: Double, d: Double, v: Float, e: Double) = synth.tone(0.0, f, f * e, d, v, tri = true)
            override fun buzz(strong: Boolean) = if (strong) haptics.thud(.8f) else haptics.click(.6f)
            override fun finished(r: SnowballFight.Result) {
                Bests.setLevel(context, "snowball", rung + 1, r.stars)
                gameEnd(r.won)
                result = r; starsV++; phase = Fight.END
                Qa.log("snowball done ${rung + 1} ${if (r.won) "won" else "lost"} ${r.stars} stars, ${r.stats.hits}:${r.stats.taken}")
            }
        }
        val g = SnowballFight(rung, me, out, still = still)
        fight = g; view.fight = g
        started = System.currentTimeMillis()
        Telemetry.event("game_start", mapOf("game" to "snowball", "level" to rung + 1))
        phase = Fight.PLAY; hpV++
        Qa.log("snowball start ${rung + 1}")
    }
    view.onFrame = { g -> if (hint && g.t > 7) hint = false }
    DisposableEffect(Unit) {
        SnowballQa.win = { fight?.knockOut() }
        onDispose { if (playing()) gameEnd(false); SnowballQa.win = null }
    }
    BackHandler(enabled = phase != Fight.MENU) { toMenu() }

    Box(Modifier.fillMaxSize().background(Color(0xFF9CC3E6))) {
        AndroidView({ view }, Modifier.fillMaxSize().semantics { contentDescription = context.getString(R.string.game_snowball_canvas_label) })
        when (phase) {
            Fight.MENU -> Shade { Menu(me, rung, starsV, ::stars, onBack, onCoat = { me = it; prefs.edit().putInt("me", it).apply() },
                onRung = { rung = it; prefs.edit().putInt("rung", it).apply() }, onGo = { start() }) }
            Fight.PLAY -> fight?.let { g ->
                Hud(g, hpV, names)
                Pop(pop, popN, still)
                Hint(hint)
                // the sound, at the bottom right, as the site's (A-41)
                SoundKey(stringResource(R.string.game_snowball_sound),
                    Modifier.align(AbsoluteAlignment.BottomRight).navigationBarsPadding().absolutePadding(right = 12.dp, bottom = 14.dp))
            }
            Fight.END -> result?.let { r -> Shade { End(rung, r,
                onAgain = { start() },
                onNext = { if (rung < RUNGS.size - 1) { rung++; prefs.edit().putInt("rung", rung).apply() }; start() },
                onMenu = { toMenu() }) } }
        }
    }
}

// ---------- the menu and the end ----------

/** The site's .menu: the field shows through a gradient that darkens toward the bottom. */
@Composable
private fun Shade(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x339CC3E6), .55f to Color(0xC70D1522), 1f to Color(0xC70D1522)))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

private val titleShadow = Shadow(INK, Offset(0f, 3f), 0f)

@Composable
private fun H2(text: String) = Text(text, style = Ski.type.title.copy(fontSize = (28 * Ski.type.displayScale).sp, lineHeight = 1.em), color = Color.White)

@Composable
@Suppress("UNUSED_PARAMETER") // a new [starsV] reads the stars again
private fun Menu(me: Int, rung: Int, starsV: Int, stars: (Int) -> Int, onBack: () -> Unit, onCoat: (Int) -> Unit, onRung: (Int) -> Unit, onGo: () -> Unit) {
    val scale = Ski.type.displayScale
    BackLink(stringResource(R.string.game_snowball_back_to_games).trim('→', '←', ' '), onBack, Color.White)
    Text(stringResource(R.string.game_snowball_title), style = Ski.type.title.copy(fontSize = (58 * scale).sp, lineHeight = .9.em, shadow = titleShadow), color = Color.White)
    Text(stringResource(R.string.game_snowball_intro), Modifier.widthIn(max = 520.dp), style = Ski.type.body.copy(fontSize = 14.sp, lineHeight = 1.45.em), color = Color.White)
    H2(stringResource(R.string.game_snowball_your_coat))
    // the six coats, three in a row, each with its character
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in COATS.indices.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in row) CoatButton(i, i == me, Modifier.weight(1f)) { onCoat(i) }
        }
    }
    H2(stringResource(R.string.game_snowball_ladder))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RUNGS.forEachIndexed { i, r ->
            val open = i == 0 || stars(i - 1) > 0
            RungRow(i, r, foesOf(i, me), open, i == rung, stars(i)) { onRung(i) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        How(R.string.game_snowball_how_drag_title, R.string.game_snowball_how_drag_text, Modifier.weight(1f))
        How(R.string.game_snowball_how_release_title, R.string.game_snowball_how_release_text, Modifier.weight(1f))
        How(R.string.game_snowball_how_duck_title, R.string.game_snowball_how_duck_text, Modifier.weight(1f))
    }
    GoButton(stringResource(R.string.game_snowball_go), onGo)
}

/** Characters as on the field (SnowballView.drawCharacter): [coats] side by side, feet at the bottom. */
@Composable
private fun Characters(coats: List<Int>, metre: Float, modifier: Modifier) {
    val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }; val line = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
    val oval = remember { RectF() }
    Canvas(modifier) {
        val s = metre * density; val step = .7f * s
        val x0 = size.width / 2 - step * (coats.size - 1) / 2
        drawIntoCanvas { c -> coats.forEachIndexed { k, i -> SnowballView.drawCharacter(c.nativeCanvas, fill, line, oval, x0 + k * step, size.height - 2, s, COATS[i].color.toInt(), 0f) } }
    }
}

@Composable
private fun CoatButton(i: Int, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val name = stringResource(COATS[i].name)
    Box(modifier.heightIn(min = 64.dp).drawBehind { drawRect(Color(COATS[i].color), size = Size(size.width, 8.dp.toPx())) }
        .padding(top = 8.dp).background(if (on) Color.White else Color.White.copy(alpha = .9f))
        .then(if (on) Modifier.border(3.dp, ACCENT) else Modifier)
        .clickable(role = Role.RadioButton, onClick = onClick).semantics { contentDescription = name; selected = on }, contentAlignment = Alignment.BottomCenter) {
        Characters(listOf(i), 26f, Modifier.size(48.dp, 56.dp))
    }
}

@Composable
private fun RungRow(i: Int, r: Rung, foes: List<Int>, open: Boolean, on: Boolean, stars: Int, onClick: () -> Unit) {
    val scale = Ski.type.displayScale
    val c = Color(COATS[foes[0]].color)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).alpha(if (open) 1f else .55f)
        .drawBehind { drawRect(Color.White); val b = 10.dp.toPx(); drawRect(c, Offset(if (rtl) size.width - b else 0f, 0f), Size(b, size.height)) }
        .then(if (on) Modifier.border(3.dp, ACCENT) else Modifier)
        .clickable(enabled = open, role = Role.RadioButton, onClick = onClick).semantics { selected = on }
        .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${i + 1}", style = Ski.type.title.copy(fontSize = (34 * scale).sp, lineHeight = 1.em), color = MUTED)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(r.arena), Modifier.weight(1f), style = Ski.type.title.copy(fontSize = (24 * scale).sp, lineHeight = 1.em), color = INK)
                Text(if (open) "★".repeat(stars) + "☆".repeat(3 - stars) else "🔒", style = Ski.type.body.copy(fontSize = 16.sp, letterSpacing = 1.sp), color = ACCENT)
            }
            Text(stringResource(R.string.game_snowball_rung_wind, r.windMax.toString()) + " · ★ " + stringResource(r.goal),
                style = Ski.type.small.copy(fontSize = 12.sp), color = MUTED)
        }
        // who stands behind the far wall on this rung
        Characters(foes, 15f, Modifier.size((12 + 11 * foes.size).dp, 32.dp))
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
private fun End(rung: Int, r: SnowballFight.Result, onAgain: () -> Unit, onNext: () -> Unit, onMenu: () -> Unit) {
    val scale = Ski.type.displayScale; val s = r.stats
    H2(stringResource(if (r.won) R.string.game_snowball_won else R.string.game_snowball_lost))
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).rotate(-2f).shadow(10.dp).background(Color.White).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${rung + 1}. ${stringResource(RUNGS[rung].arena)}", style = Ski.type.label.copy(fontSize = 12.sp), color = MUTED)
        // hits : taken, as on the site (numbers read left to right in every language)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text("${s.hits} : ${s.taken}", style = Ski.type.title.copy(fontSize = (58 * scale).sp, lineHeight = .9.em), color = INK)
        }
        Row { for (k in 0 until 3) Text("★", style = Ski.type.body.copy(fontSize = 30.sp, letterSpacing = 4.sp), color = if (r.goals[k]) ACCENT else RULE) }
        Row(Modifier.fillMaxWidth().drawBehind { drawRect(RULE, size = Size(size.width, 1.dp.toPx())) }.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat(R.string.game_snowball_stat_hits, "${s.hits}", Modifier.weight(1f))
            Stat(R.string.game_snowball_stat_hats, "${s.hats}", Modifier.weight(1f))
            Stat(R.string.game_snowball_stat_accuracy, "${if (s.thrown > 0) (s.hits * 100f / s.thrown).roundToInt() else 0}%", Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().drawBehind { drawRect(RULE, size = Size(size.width, 1.dp.toPx())) }.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(R.string.game_snowball_goal_win, R.string.game_snowball_goal_no_hit_taken, RUNGS[rung].goal).forEachIndexed { k, g ->
                val ok = r.goals[k]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (ok) "★" else "☆", style = Ski.type.body.copy(fontSize = 13.sp), color = if (ok) ACCENT else RULE)
                    Text(stringResource(g), style = (if (ok) Ski.type.bodyBold else Ski.type.body).copy(fontSize = 13.sp), color = if (ok) INK else MUTED)
                }
            }
        }
    }
    GoButton(stringResource(R.string.game_snowball_again), onAgain)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r.won && rung < RUNGS.size - 1) OutlineButton(stringResource(R.string.game_snowball_next_level), Modifier.weight(1f), onNext)
        else Spacer(Modifier.weight(1f))
        OutlineButton(stringResource(R.string.game_snowball_to_menu), Modifier.weight(1f), onMenu)
    }
}

@Composable
private fun Stat(label: Int, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(label), style = Ski.type.small.copy(fontSize = 11.5.sp), color = MUTED)
        Text(value, style = Ski.type.title.copy(fontSize = (30 * Ski.type.displayScale).sp, lineHeight = 1.em), color = INK)
    }
}

// ---------- in the fight: your balls on the left, the rung and the wind in the middle, theirs on the right ----------

/** A coat's tag (the site's .tag): its colour on top, a name when there is one, and the balls left, full or dashed. */
@Composable
private fun Tag(coat: Int, hp: Int, max: Int, name: String?) {
    Row(Modifier.drawBehind { drawRect(Color(COATS[coat].color), size = Size(size.width, 5.dp.toPx())) }.padding(top = 5.dp).background(INK.copy(alpha = .86f))
        .padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (name != null) Text(name, Modifier.padding(end = 4.dp), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = Color.White, maxLines = 1)
        for (k in 0 until max) Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2
            if (k < hp) { drawCircle(Color.White, r); drawCircle(Color(0xFFC9D8E7), r * .9f, center.copy(y = center.y + r * .25f), alpha = .6f); drawCircle(INK.copy(alpha = .3f), r - .5f, style = Stroke(1f)) }
            else drawCircle(Color.White.copy(alpha = .8f), r - .5f, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER") // a new [hpV] reads the balls again
private fun Hud(g: SnowballFight, hpV: Int, names: (Int) -> String?) {
    val scale = Ski.type.displayScale
    // laid out as the site's (direction:ltr): you on the left, they on the right, in every language
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { Tag(g.me, g.myHp, 3, names(g.me)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${g.rungIndex + 1} · ${stringResource(g.rung.arena)}", Modifier.background(ACCENT).padding(horizontal = 12.dp, vertical = 3.dp),
                    style = Ski.type.title.copy(fontSize = (24 * scale).sp, lineHeight = 1.em), color = INK, maxLines = 1)
                val w = g.wind
                Text(if (abs(w) < .25f) stringResource(R.string.game_snowball_no_wind)
                    else stringResource(R.string.game_snowball_wind, fmt(abs(w)), if (w > 0) "⟶" else "⟵"),
                    Modifier.background(Color.White.copy(alpha = .88f)).padding(horizontal = 10.dp, vertical = 3.dp), style = Ski.type.bodyBold.copy(fontSize = 12.5.sp), color = INK)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (o in g.foes) Tag(o.coat, o.hp, g.rung.hp, names(o.coat))
            }
        }
    }
}

private fun fmt(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

/** The big word (the site's .pop): white with an ink shadow, it jumps in, stays a moment and floats away. */
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
        Text(context.say(say), Modifier.align(Alignment.TopCenter).offset(y = maxHeight * .28f - 28.dp + dy.dp).graphicsLayer { this.alpha = alpha; scaleX = sc; scaleY = sc },
            style = Ski.type.title.copy(fontSize = (minOf(58f, maxOf(34f, maxWidth.value * .11f)) * Ski.type.displayScale).sp, lineHeight = 1.em,
                shadow = Shadow(INK, Offset(0f, 6f), 0f)), color = Color.White, maxLines = 1, softWrap = false)
    }
}

/** How to play, at the bottom, until seven seconds into the fight. */
@Composable
private fun Hint(on: Boolean) {
    val a by animateFloatAsState(if (on) 1f else 0f, tween(500), label = "hint")
    if (a <= 0f) return
    // clear of the sound at the bottom right (SoundKey)
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp).absolutePadding(right = 48.dp), contentAlignment = Alignment.BottomCenter) {
        Text(stringResource(R.string.game_snowball_hint), Modifier.fillMaxWidth().alpha(a).background(Color.White.copy(alpha = .92f)).padding(horizontal = 12.dp, vertical = 7.dp),
            style = Ski.type.bodyBold.copy(fontSize = 14.sp, lineHeight = 1.35.em), color = INK)
    }
}

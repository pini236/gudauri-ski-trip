package io.github.pini236.skiapp.game

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.FxPrefs
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Synth
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// one look: a cold blue evening on the slope, snow tiles on packed snow (the site's palette)
private val BG_TOP = Color(0xFF2C4A6C); private val BG = Color(0xFF16263A); private val BOARD = Color(0xFF25405E); private val CELL = Color(0xFF2E4E70)
private val INK = Color(0xFF0D1522); private val PAPER = Color(0xFFF4F8FB); private val ACCENT = Color(0xFFF4B942); private val SOFT = Color(0xFFA9C1D8)

/** A tile on the screen: where it is, and how it came (new pops in, merged bumps, gone slides under and goes). */
private class TileView(val id: Int, val lv: Int, var r: Int, var c: Int, val kind: Int) {
    companion object { const val PLAIN = 0; const val NEW = 1; const val MERGED = 2; const val GONE = 3 }
}

private class Puff(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, var life: Float, val gold: Boolean)
private class Plus(val id: Int, val r: Int, val c: Int, val n: Int)

/**
 * "Merging snowballs" on the phone (13.6), the site's game (site/games/merge): a swipe anywhere slides the board, two of a
 * kind become the next, a puff of snow and a crunch for every merge, the next goal and the ladder of names, three undos,
 * and the board kept on the phone. The high score is the phone's (games/Bests.kt) and goes to the group's table.
 */
@Composable
fun MergeScreen(haptics: Haptics, onBack: () -> Unit, onBest: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val dens = LocalDensity.current.density
    val still = remember { Motion.reduced(context) }
    val slide = if (still) 0 else 110
    val synth = remember { Synth(context) }
    val prefs = remember { context.getSharedPreferences("merge", Context.MODE_PRIVATE) }
    val game = remember { Merge(Random.Default).also { if (!it.restore(prefs.getString("save", null))) it.newGame() } }
    val names = (1..Merge.LEVELS).map { stringResource(nameRes(it)) }
    val tiles = remember { mutableStateListOf<TileView>().apply { addAll(game.tiles.map { TileView(it.id, it.lv, it.r, it.c, TileView.PLAIN) }) } }
    var score by remember { mutableIntStateOf(game.score) }
    var best by remember { mutableIntStateOf(Bests.get(context, "merge")) }
    var maxLv by remember { mutableIntStateOf(game.maxLv) }
    var undos by remember { mutableIntStateOf(game.undos) }
    var canUndo by remember { mutableStateOf(game.canUndo) }
    var got by remember { mutableIntStateOf(prefs.getInt("got", 0)) }
    var over by remember { mutableStateOf<String?>(null) } // "won" or "over"
    var wonShown by remember { mutableStateOf(game.maxLv >= Merge.WON) }
    var toast by remember { mutableStateOf<Int?>(null) }
    var sound by remember { mutableStateOf(FxPrefs.sound(context)) }
    val pluses = remember { mutableStateListOf<Plus>() }
    val puffs = remember { ArrayList<Puff>() }
    var frame by remember { mutableIntStateOf(0) } // a new frame of the puffs
    var puffing by remember { mutableStateOf(false) }
    val nudge = remember { Animatable(0f) }
    var nudgeDir by remember { mutableStateOf(IntOffset(1, 0)) } // a move that moves nothing: the board leans that way
    val bump = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var started by remember { mutableStateOf(0L) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var boardPx by remember { mutableStateOf(0f) }

    // usage statistics, as the site: a game runs from the first move until the board is full, a new game, or leaving
    fun gameStart() { if (started == 0L) { started = System.currentTimeMillis(); bestAtStart = best; Telemetry.event("game_start", mapOf("game" to "merge")) } }
    fun gameEnd() {
        if (started == 0L) return
        Telemetry.event("game_end", mapOf("game" to "merge", "score" to score, "seconds" to ((System.currentTimeMillis() - started) / 1000.0).roundToInt(),
            "completed" to (maxLv >= Merge.WON), "best" to (score > bestAtStart)))
        started = 0L
    }
    DisposableEffect(Unit) { onDispose { gameEnd() } }
    fun save() { prefs.edit().putString("save", game.save()).apply() }
    fun sync() {
        tiles.clear(); tiles.addAll(game.tiles.map { TileView(it.id, it.lv, it.r, it.c, TileView.PLAIN) })
        score = game.score; maxLv = game.maxLv; undos = game.undos; canUndo = game.canUndo
    }
    fun newGame() { gameEnd(); game.newGame(); wonShown = false; over = null; sync(); tiles.replaceAll { TileView(it.id, it.lv, it.r, it.c, TileView.NEW) }; save(); Qa.log("merge new game") }

    fun puff(r: Int, c: Int, lv: Int) {
        if (still || boardPx <= 0f) return
        val gap = boardPx * .025f; val cs = (boardPx - 5 * gap) / 4
        val cx = gap + c * (cs + gap) + cs / 2; val cy = gap + r * (cs + gap) + cs / 2
        repeat(10 + lv * 3) {
            val a = Random.nextFloat() * 6.283f; val v = (.6f + Random.nextFloat()) * cs * (.9f + lv * .06f)
            puffs += Puff(cx, cy, cos(a) * v, sin(a) * v - cs * .4f, 2f * dens + Random.nextFloat() * cs * .05f, .5f + Random.nextFloat() * .3f, lv >= 10 && Random.nextFloat() < .4f)
        }
        if (!puffing) {
            puffing = true
            scope.launch {
                var last = 0L
                while (puffs.isNotEmpty()) {
                    val now = withFrameNanos { it }
                    val dt = if (last == 0L) .016f else min(.05f, (now - last) / 1e9f); last = now
                    for (p in puffs) { p.vy += cs * 2.2f * dt; p.vx *= exp(-3 * dt); p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt }
                    puffs.removeAll { it.life <= 0 }
                    frame++
                }
                puffing = false
            }
        }
    }

    fun move(d: Merge.Dir) {
        if (over != null) return
        val mv = game.move(d)
        if (mv == null) {
            nudgeDir = when (d) { Merge.Dir.LEFT -> IntOffset(-1, 0); Merge.Dir.RIGHT -> IntOffset(1, 0); Merge.Dir.UP -> IntOffset(0, -1); Merge.Dir.DOWN -> IntOffset(0, 1) }
            if (!still) scope.launch { nudge.snapTo(0f); nudge.animateTo(1f, tween(180)); nudge.snapTo(0f) }
            return
        }
        gameStart()
        synth.swish()
        // 1. everything slides; the two halves of a merge slide into the same cell and go
        val byId = tiles.associateBy { it.id }
        for (t in mv.slid) byId[t.id]?.let { it.r = t.r; it.c = t.c }
        val gone = mv.merges.flatMap { (t, from) -> from.map { f -> TileView(f.id, f.lv, t.r, t.c, TileView.GONE) } }
        val goneIds = gone.map { it.id }.toSet()
        tiles.replaceAll { v -> if (v.id in goneIds) gone.first { it.id == v.id } else TileView(v.id, v.lv, v.r, v.c, TileView.PLAIN) }
        canUndo = game.canUndo; undos = game.undos
        // 2. when they arrive: the bigger ball pops in with a puff, the score counts up, a new flake appears
        scope.launch {
            delay(slide.toLong())
            tiles.removeAll { it.id in goneIds }
            for ((t, _) in mv.merges) { tiles += TileView(t.id, t.lv, t.r, t.c, TileView.MERGED); pluses += Plus(t.id, t.r, t.c, 1 shl (t.lv + 1)); puff(t.r, t.c, t.lv) }
            mv.spawned?.let { tiles += TileView(it.id, it.lv, it.r, it.c, TileView.NEW) }
            if (mv.merges.isNotEmpty()) {
                val top = mv.merges.maxOf { it.first.lv }
                synth.crunch(top, .18f + .05f * mv.merges.size)
                if (mv.merges.size > 1) haptics.click(.6f) else haptics.tick(.5f)
                score = game.score
                if (!still) launch { bump.snapTo(1.25f); bump.animateTo(1f, tween(250)) }
                if (score > best) { best = score; if (Bests.offer(context, "merge", score)) onBest(score) }
                if (game.maxLv > maxLv) maxLv = game.maxLv
                if (top > got) { got = top; prefs.edit().putInt("got", top).apply(); if (top >= 3) { toast = top; launch { delay(1700); if (toast == top) toast = null } } }
            }
            save(); Qa.log("merge moved $d · score $score · top ${names[maxLv]}")
            if (maxLv >= Merge.WON && !wonShown) { wonShown = true; delay(500); over = "won" }
            else if (!game.canMove()) { gameEnd(); delay(400); over = "over"; Qa.log("merge over") }
        }
    }

    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keys.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BG_TOP, BG)))
        // the arrow keys too, as on the site (a keyboard on the phone or a tablet)
        .focusRequester(keys).focusable()
        .onPreviewKeyEvent { e ->
            val d = when (e.key) { Key.DirectionLeft -> Merge.Dir.LEFT; Key.DirectionRight -> Merge.Dir.RIGHT; Key.DirectionUp -> Merge.Dir.UP; Key.DirectionDown -> Merge.Dir.DOWN; else -> null }
            if (d != null && e.type == KeyEventType.KeyDown) { move(d); true } else false
        }
        // the swipe fires while the finger is still moving, anywhere on the screen
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val o = down.position
                while (true) {
                    val ev = awaitPointerEvent()
                    val p = ev.changes.firstOrNull() ?: break
                    if (!p.pressed) break
                    val dx = p.position.x - o.x; val dy = p.position.y - o.y
                    if (maxOf(abs(dx), abs(dy)) >= 18 * density) {
                        move(if (abs(dx) > abs(dy)) (if (dx < 0) Merge.Dir.LEFT else Merge.Dir.RIGHT) else (if (dy < 0) Merge.Dir.UP else Merge.Dir.DOWN))
                        break
                    }
                }
            }
        }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // back to the games page (the site's "→ All games"; the arrow is the link's own icon here)
            Box(Modifier.widthIn(max = 440.dp).fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                BackLink(stringResource(R.string.game_merge_back_to_games).trim('→', '←', ' '), onBack, Color(0xFFC7D7E6))
            }
            // the title and the two scores
            Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.game_merge_title), Modifier.weight(1f), style = Ski.type.title.copy(fontSize = 40.sp, lineHeight = 36.sp), color = PAPER, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScoreBox(stringResource(R.string.game_merge_score), score, Modifier.scale(bump.value))
                    ScoreBox(stringResource(R.string.game_merge_best), best)
                }
            }
            // the next step, and how far up the ladder
            Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (maxLv >= Merge.LEVELS - 1) stringResource(R.string.game_merge_goal_top) else stringResource(R.string.game_merge_goal_next, names[min(Merge.LEVELS - 1, maxLv + 1)]),
                    style = Ski.type.small.copy(fontSize = 13.sp), color = Color(0xFFC7D7E6))
                Box(Modifier.weight(1f).height(8.dp).background(BOARD)) {
                    Box(Modifier.fillMaxWidth(maxLv / (Merge.LEVELS - 1f)).height(8.dp).background(ACCENT))
                }
            }
            // the board
            BoxWithConstraints(Modifier.widthIn(max = 440.dp).fillMaxWidth().weight(1f, fill = false)) {
                val side = min(maxWidth.value, maxHeight.value).dp
                val sidePx = with(LocalDensity.current) { side.toPx() }
                LaunchedEffect(sidePx) { boardPx = sidePx }
                val gapPx = sidePx * .025f; val csPx = (sidePx - 5 * gapPx) / 4
                val cs = with(LocalDensity.current) { csPx.toDp() }
                val boardLabel = stringResource(R.string.game_merge_board_label)
                Box(Modifier.align(Alignment.Center).size(side).absoluteOffset { val k = (sin(nudge.value * Math.PI).toFloat() * 6 * dens).roundToInt(); IntOffset(nudgeDir.x * k, nudgeDir.y * k) }.background(BOARD)
                    .semantics { contentDescription = boardLabel; stateDescription = tiles.filter { it.kind != TileView.GONE }.sortedWith(compareBy({ it.r }, { it.c })).joinToString { names[it.lv] } }, contentAlignment = AbsoluteAlignment.TopLeft) {

                    for (r in 0 until 4) for (c in 0 until 4) Box(Modifier.absoluteOffset { IntOffset((gapPx + c * (csPx + gapPx)).roundToInt(), (gapPx + r * (csPx + gapPx)).roundToInt()) }.size(cs).background(CELL))
                    for (t in tiles.sortedBy { if (it.kind == TileView.GONE) 0 else 1 }) key(t.id) {
                        val pos by animateIntOffsetAsState(IntOffset((gapPx + t.c * (csPx + gapPx)).roundToInt(), (gapPx + t.r * (csPx + gapPx)).roundToInt()), tween(slide, easing = FastOutSlowInEasing), label = "tile")
                        val pop = remember { Animatable(if (t.kind == TileView.NEW && !still) 0f else if (t.kind == TileView.MERGED && !still) .7f else 1f) }
                        LaunchedEffect(Unit) {
                            if (t.kind == TileView.NEW && !still) { delay(slide.toLong()); pop.animateTo(1f, tween(200)) }
                            if (t.kind == TileView.MERGED && !still) { pop.animateTo(1.2f, tween(140)); pop.animateTo(1f, tween(120)) }
                        }
                        Box(Modifier.absoluteOffset { pos }.size(cs).scale(pop.value)) { TileFace(t.lv, names[t.lv]) }
                    }
                    // the puffs and the points over the board
                    Canvas(Modifier.size(side)) {
                        frame.let { }
                        for (p in puffs) drawCircle(if (p.gold) ACCENT else Color.White, p.r, Offset(p.x, p.y), alpha = (p.life * 2.5f).coerceIn(0f, 1f))
                    }
                    for (p in pluses.toList()) key("plus" + p.id) {
                        val a = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { a.animateTo(1f, tween(800)); pluses.remove(p) }
                        Text("+${p.n}", Modifier.absoluteOffset { IntOffset((gapPx + p.c * (csPx + gapPx) + csPx * .3f).roundToInt(), (gapPx + p.r * (csPx + gapPx) + csPx * .1f - csPx * .4f * a.value).roundToInt()) }.alpha(1 - a.value),
                            style = Ski.type.title.copy(fontSize = (csPx * .32f / LocalDensity.current.density / LocalDensity.current.fontScale).sp), color = ACCENT)
                    }
                }
            }
            // undo, a new game, the sound
            Row(Modifier.widthIn(max = 440.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton(stringResource(R.string.game_merge_undo, undos), Modifier.weight(1f), enabled = canUndo) { if (game.undo()) { sync(); save() } }
                GameButton(stringResource(R.string.game_merge_new_game), Modifier.weight(1f)) { newGame() }
                val soundLabel = stringResource(R.string.game_merge_sound)
                GameButton(if (sound) "🔊" else "🔇", Modifier.size(52.dp, 48.dp).semantics { contentDescription = soundLabel }) {
                    sound = !sound; FxPrefs.set(context, "sound", sound); Telemetry.event("settings_change", mapOf("setting" to "sound", "on" to sound))
                }
            }
            // the ladder of names
            Ladder(names, maxOf(got, maxLv), maxLv)
            Text(stringResource(R.string.game_merge_hint), style = Ski.type.small.copy(fontSize = 12.sp), color = SOFT, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
        // a new step reached for the first time
        toast?.let { lv ->
            Row(Modifier.align(Alignment.Center).background(PAPER).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp)) { TileFace(lv, null) }
                Column {
                    Text(stringResource(R.string.game_merge_toast_new), style = Ski.type.small.copy(fontSize = 12.sp), color = Color(0xFF4B5A6F))
                    Text(names[lv], style = Ski.type.title.copy(fontSize = 34.sp, lineHeight = 32.sp), color = INK)
                }
            }
        }
        // the end, or a whole snowman
        over?.let { kind ->
            Column(Modifier.fillMaxSize().background(Color(0xD10D1522)).clickable(enabled = false) {}.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Text(stringResource(if (kind == "won") R.string.game_merge_won_title else R.string.game_merge_over_title), style = Ski.type.title.copy(fontSize = 56.sp, lineHeight = 52.sp), color = PAPER)
                Text(if (kind == "won") stringResource(R.string.game_merge_won_text) else stringResource(R.string.game_merge_over_text, names[maxLv], score.toString()),
                    style = Ski.type.body.copy(fontSize = 14.sp), color = PAPER, textAlign = TextAlign.Center)
                Box(Modifier.heightIn(min = 56.dp).widthIn(min = 200.dp).background(ACCENT).clickable(role = Role.Button) {
                    // "continue" after the snowman, when that merge filled the board: the end (X-5, the site's noRoom)
                    if (kind == "won") { over = null; if (!game.canMove()) scope.launch { gameEnd(); delay(400); over = "over"; Qa.log("merge over") } } else newGame()
                }.padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Text(stringResource(if (kind == "won") R.string.game_merge_continue else R.string.game_merge_again), style = Ski.type.title.copy(fontSize = 30.sp), color = INK)
                }
                if (kind == "over" && game.canUndo) Box(Modifier.heightIn(min = 48.dp).widthIn(min = 200.dp).border(2.dp, PAPER).clickable(role = Role.Button) { over = null; if (game.undo()) { sync(); save() } }
                    .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.game_merge_undo_last), style = Ski.type.bodyBold.copy(fontSize = 16.sp), color = PAPER)
                }
            }
        }
    }
}

private fun nameRes(n: Int) = when (n) {
    1 -> R.string.game_merge_name_1; 2 -> R.string.game_merge_name_2; 3 -> R.string.game_merge_name_3; 4 -> R.string.game_merge_name_4
    5 -> R.string.game_merge_name_5; 6 -> R.string.game_merge_name_6; 7 -> R.string.game_merge_name_7; 8 -> R.string.game_merge_name_8
    9 -> R.string.game_merge_name_9; 10 -> R.string.game_merge_name_10; 11 -> R.string.game_merge_name_11; else -> R.string.game_merge_name_12
}

@Composable
private fun ScoreBox(label: String, n: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(BOARD).padding(horizontal = 8.dp, vertical = 4.dp).widthIn(min = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Ski.type.small.copy(fontSize = 11.sp), color = SOFT)
        Text(n.toString(), style = Ski.type.title.copy(fontSize = 28.sp, lineHeight = 28.sp), color = PAPER)
    }
}

@Composable
private fun GameButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).background(BOARD).alpha(if (enabled) 1f else .4f).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = PAPER, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ladder(names: List<String>, got: Int, now: Int) {
    FlowRow(Modifier.widthIn(max = 440.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEachIndexed { i, n ->
            Text(n, Modifier.background(if (i <= got) PAPER else BOARD).let { if (i == now) it.border(2.dp, ACCENT) else it }.padding(horizontal = 6.dp, vertical = 3.dp),
                style = Ski.type.small.copy(fontSize = 11.sp), color = if (i <= got) INK else Color(0xFF7F98B2))
        }
    }
}

/**
 * A tile: a snowball that grows with the level, then a snowman (level 7 on) with a hat, a scarf, a giant's glow and the
 * king's crown (the site's ballSVG, in its 100 by 100 box); and the name along the bottom.
 */
@Composable
private fun TileFace(lv: Int, name: String?) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawBall(lv, name != null) }
        if (name != null) {
            BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                val fs = (maxWidth.value * .13f).sp
                Text(name, Modifier.fillMaxWidth().background(Color(0xD92E4E70)), style = Ski.type.bodyBold.copy(fontSize = fs, lineHeight = fs * 1.25f),
                    color = Color(0xFFE3ECF4), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

private fun DrawScope.drawBall(lv: Int, labelled: Boolean) {
    val w = size.width; val h = size.height
    if (lv < Merge.WON) {
        // a ball: bigger with every step, lit from the top left, a little shadow under it
        val s = w * (28 + lv * 8) / 100f
        val c = Offset(w / 2, if (labelled) h * .43f else h / 2)
        drawCircle(Color(0x40000000), s / 2, c + Offset(0f, 3f))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE3ECF4), Color(0xFFB8CADB)), center = c + Offset(-s * .15f, -s * .2f), radius = s * .75f), s / 2, c)
        return
    }
    // the snowman, drawn in the site's 100 by 100 box (the bottom 16% left for the name)
    val k = min(w, h * (if (labelled) .84f else 1f)) / 100f
    val ox = (w - 100 * k) / 2; val oy = 0f
    fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)
    val hat = lv >= 8; val scarf = lv >= 9; val big = lv >= 10; val gold = lv >= 11
    if (big) drawCircle(if (gold) ACCENT else PAPER, 48 * k, p(50f, 50f), alpha = .25f)
    val edge = Color(0xFFB8CADB)
    for ((y, r, col) in listOf(Triple(74f, 22f, Color(0xFFEDF3F8)), Triple(44f, 16f, PAPER), Triple(20f, 12f, Color.White))) {
        drawCircle(col, r * k, p(50f, y)); drawCircle(edge, r * k, p(50f, y), style = Stroke(2 * k))
    }
    drawCircle(INK, 1.8f * k, p(45f, 18f)); drawCircle(INK, 1.8f * k, p(55f, 18f))
    drawPath(Path().apply { moveTo(p(50f, 21f).x, p(50f, 21f).y); lineTo(p(62f, 23f).x, p(62f, 23f).y); lineTo(p(50f, 24f).x, p(50f, 24f).y); close() }, Color(0xFFF07A2E))
    drawCircle(INK, 1.8f * k, p(50f, 40f)); drawCircle(INK, 1.8f * k, p(50f, 48f))
    if (scarf) {
        drawPath(Path().apply {
            moveTo(p(37f, 31f).x, p(37f, 31f).y); quadraticTo(p(50f, 37f).x, p(50f, 37f).y, p(63f, 31f).x, p(63f, 31f).y)
            lineTo(p(63f, 35f).x, p(63f, 35f).y); quadraticTo(p(50f, 41f).x, p(50f, 41f).y, p(37f, 35f).x, p(37f, 35f).y); close()
        }, Color(0xFFD1342B))
        drawRect(Color(0xFFD1342B), p(56f, 33f), Size(5 * k, 13 * k))
    }
    if (hat && !gold) { drawRect(INK, p(40f, 2f), Size(20 * k, 9 * k)); drawRect(INK, p(35f, 10f), Size(30 * k, 3 * k)) }
    if (gold) drawPath(Path().apply {
        moveTo(p(40f, 6f).x, p(40f, 6f).y); lineTo(p(44f, 0f).x, p(44f, 0f).y); lineTo(p(50f, 5f).x, p(50f, 5f).y)
        lineTo(p(56f, 0f).x, p(56f, 0f).y); lineTo(p(60f, 6f).x, p(60f, 6f).y); close()
    }, ACCENT)
}

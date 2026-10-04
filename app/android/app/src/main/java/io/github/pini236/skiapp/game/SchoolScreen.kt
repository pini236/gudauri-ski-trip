package io.github.pini236.skiapp.game

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Synth
import io.github.pini236.skiapp.home.SignShape
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.BackLink
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Ski
import kotlin.math.roundToInt

// one look: a bright training slope, the same in both themes (the site's school page)
private val INK = Color(0xFF13233A)
private val MUTED = Color(0xFF4B5A6F)
private val RULE = Color(0xFFCBD5DF)
private val ACCENT = Color(0xFFF4B942)
private val GREEN = Color(0xFF1B8A4C)
private val RED = Color(0xFFD1342B)
private val BLUE = Color(0xFF1F5FC4)
private val PAGE = Color(0xFFF4F8FB)

/** What the emulator run reaches into: the lesson on the slope now, ended at once. */
internal object SchoolQa { var finish: (() -> Unit)? = null }

/** A [Say] in words: its resource, with the arguments (and a [Say] among them) said too. */
fun Context.say(s: Say): String = getString(s.id, *s.args.map { if (it is Say) say(it) else it.toString() }.toTypedArray())

private enum class Phase { MENU, BRIEF, RUN, RESULT }

/**
 * The ski school on the phone (13.6), the site's game (site/games/school): seven short lessons from the first stop in a
 * wedge to carving, each with one principle, its own control and a drill built for it, and a coach who says what is
 * happening and why. A lesson opens when the one before it has a star; the stars of each lesson are kept on the phone,
 * and their sum is the school's record (the group's table too).
 */
@Composable
fun SchoolScreen(haptics: Haptics, onBack: () -> Unit, onBest: () -> Unit = {}) {
    val context = LocalContext.current
    val synth = remember { Synth(context) }
    val still = remember { Motion.reduced(context) }
    var phase by remember { mutableStateOf(Phase.MENU) }
    var cur by remember { mutableIntStateOf(0) }
    var starsV by remember { mutableIntStateOf(0) } // bumps when the stars change
    var result by remember { mutableStateOf<SchoolRun.Result?>(null) }
    // the lesson's words and numbers, as the run says them
    var coach by remember { mutableStateOf<Pair<Say, Int>?>(null) }
    var checks by remember { mutableStateOf(emptyList<SchoolRun.Check>()) }
    var pop by remember { mutableStateOf<Say?>(null) }
    var popN by remember { mutableIntStateOf(0) }
    var kmh by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var wedgePct by remember { mutableIntStateOf(15) }
    var skiL by remember { mutableStateOf(false) }
    var skiR by remember { mutableStateOf(false) }
    var wedgeOn by remember { mutableStateOf(false) }
    var hiss by remember { mutableStateOf<Synth.Hiss?>(null) }

    val view = remember {
        SchoolView(context, still).apply {
            text = Lang.typeface(context, Lang.current(context.resources), false)
            words = { context.say(it) }
        }
    }
    fun stars(i: Int) = Bests.lesson(context, i + 1)

    // usage statistics, as the site: level = the lesson's number, score = its stars, completed = at least one star
    var started by remember { mutableStateOf(0L) }
    fun gameEnd(n: Int, best: Boolean) {
        if (started == 0L) return
        Telemetry.event("game_end", mapOf("game" to "school", "level" to cur + 1, "score" to n,
            "seconds" to ((System.currentTimeMillis() - started) / 1000.0).roundToInt(), "completed" to (n > 0), "best" to best))
        started = 0L
    }
    fun stop() { hiss?.stop(); hiss = null; view.run = null; skiL = false; skiR = false; wedgeOn = false }
    fun toMenu() { if (phase == Phase.RUN && result == null) gameEnd(0, false); stop(); phase = Phase.MENU; starsV++ }
    fun brief(i: Int) { if (phase == Phase.RUN && result == null) gameEnd(0, false); stop(); cur = i; result = null; phase = Phase.BRIEF; Qa.log("school brief ${i + 1}") }
    fun start() {
        val lv = LESSONS[cur]
        result = null; coach = null; pop = null; kmh = 0; over = false
        val out = object : SchoolRun.Out {
            override fun coach(say: Say, tone: Int) { coach = say to tone; Qa.log("school coach ${context.resources.getResourceEntryName(say.id)}") }
            override fun pop(say: Say) { pop = say; popN++; Qa.log("school pop ${context.resources.getResourceEntryName(say.id)}") }
            override fun checks(items: List<SchoolRun.Check>) { checks = items }
            override fun ding(f: Double, d: Double, v: Float, square: Boolean) = synth.ding(f, d, v, square)
            override fun buzz(strength: Float) = if (strength >= 1f) haptics.thud(.9f) else haptics.click(strength)
            override fun finished(r: SchoolRun.Result) {
                hiss?.gain = 0f
                val best = Bests.ended(context, "school", (cur + 1).toString(), r.stars)
                gameEnd(r.stars, best)
                if (best) onBest()
                result = r; starsV++
                Qa.log("school done ${cur + 1} ${r.stars} stars")
            }
        }
        val run = SchoolRun(lv, out)
        view.place(run)
        view.run = run
        hiss?.stop(); hiss = synth.hiss()
        started = System.currentTimeMillis()
        Telemetry.event("game_start", mapOf("game" to "school", "level" to cur + 1))
        phase = Phase.RUN
        Qa.log("school start ${cur + 1}")
    }
    view.onFrame = { r ->
        val k = r.kmh.roundToInt(); if (k != kmh) kmh = k
        if (r.over != over) over = r.over
        val w = (r.wedgeCtl * 100).roundToInt(); if (w != wedgePct) wedgePct = w
        // skis on the snow: louder and brighter with the speed, the skid and the wedge
        hiss?.let { h -> if (r.running) { h.gain = minOf(.22f, r.s.v * .012f + r.s.skid * .12f + r.s.wedge * .05f); h.freq = 900.0 + r.s.v * 80 + r.s.skid * 1500 } }
    }
    LaunchedEffect(result) { if (result != null) { kotlinx.coroutines.delay(900); if (phase == Phase.RUN) { stop(); phase = Phase.RESULT } } }
    DisposableEffect(Unit) {
        SchoolQa.finish = { view.run?.finish() }
        onDispose { if (phase == Phase.RUN && result == null) gameEnd(0, false); hiss?.stop(); SchoolQa.finish = null }
    }
    BackHandler(enabled = phase != Phase.MENU) { toMenu() }

    Box(Modifier.fillMaxSize().background(PAGE)) {
        // the slope stays put under the pages (one view for the whole visit), drawing only while a lesson runs
        AndroidView({ view }, Modifier.fillMaxSize().semantics { contentDescription = context.getString(R.string.game_school_canvas_label) })
        when (phase) {
            Phase.MENU -> Over { Menu(starsV, ::stars, onBack) { brief(it) } }
            Phase.BRIEF -> Over { Brief(cur, onGo = { start() }, onLessons = { toMenu() }) }
            Phase.RESULT -> result?.let { r -> Over { ResultCard(cur, r, onNext = { if (r.stars > 0) { if (cur < LESSONS.size - 1) brief(cur + 1) else toMenu() } else brief(cur) },
                onRetry = { brief(cur) }, onLessons = { toMenu() }) } }
            Phase.RUN -> {
                val lv = LESSONS[cur]
                Hud(cur, lv, kmh, over, coach, checks)
                Pop(pop, popN, still)
                if (lv.mode == SchoolMode.WEDGE) Slider(wedgePct)
                if (lv.mode == SchoolMode.SKIS) Skis(skiL, skiR) { right, down ->
                    if (right) skiR = down else skiL = down
                    view.run?.ski(right, down)
                    if (down) haptics.tick(.4f)
                }
                if (lv.wedgeBtn) WedgeButton(wedgeOn) { down -> wedgeOn = down; view.run?.wedgeBtn = down; if (down) haptics.tick(.4f) }
            }
        }
    }
}

// ---------- the menu, the brief and the result ----------

@Composable
private fun Over(content: @Composable () -> Unit) { Box(Modifier.fillMaxSize().background(PAGE)) { content() } }

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

private fun firstSentence(s: String) = s.substringBefore('.')

@Composable
@Suppress("UNUSED_PARAMETER") // a new [starsV] reads the stars again
private fun Menu(starsV: Int, stars: (Int) -> Int, onBack: () -> Unit, onPick: (Int) -> Unit) {
    val scale = Ski.type.displayScale
    Page {
        BackLink(stringResource(R.string.game_school_back_to_games).trim('→', '←', ' '), onBack, INK)
        Text(stringResource(R.string.game_school_title), style = Ski.type.title.copy(fontSize = (58 * scale).sp, lineHeight = .9.em), color = INK)
        Text(stringResource(R.string.game_school_intro), style = Ski.type.body.copy(fontSize = 14.sp), color = MUTED)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LESSONS.forEachIndexed { i, l ->
                val open = i == 0 || stars(i - 1) > 0; val st = stars(i)
                LessonRow(i, l, open, st) { onPick(i) }
            }
        }
        Text(stringResource(R.string.game_school_disclaimer), style = Ski.type.small.copy(fontSize = 12.sp), color = MUTED)
    }
}

@Composable
private fun LessonRow(i: Int, l: Lesson, open: Boolean, stars: Int, onClick: () -> Unit) {
    val scale = Ski.type.displayScale
    val c = Color(l.color)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).alpha(if (open) 1f else .5f)
        .drawBehind {
            drawRect(RULE, Offset(0f, size.height), Size(size.width, 2.dp.toPx())) // the site's 0 2px 0 shadow
            drawRect(Color.White)
            val b = 10.dp.toPx(); drawRect(c, Offset(if (rtl) size.width - b else 0f, 0f), Size(b, size.height))
        }
        .clickable(enabled = open, role = Role.Button, onClick = onClick)
        .padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${i + 1}", style = Ski.type.title.copy(fontSize = (36 * scale).sp, lineHeight = 1.em), color = MUTED)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(l.title), Modifier.weight(1f), style = Ski.type.title.copy(fontSize = (25 * scale).sp, lineHeight = 1.em), color = INK)
                Text(if (open) "★".repeat(stars) + "☆".repeat(3 - stars) else "🔒", style = Ski.type.body.copy(fontSize = 18.sp, letterSpacing = 1.sp), color = ACCENT)
            }
            Text(firstSentence(stringResource(l.drill)) + ".", style = Ski.type.small.copy(fontSize = 12.5.sp), color = MUTED)
        }
    }
}

/** The lesson's card (the site's .card): the trail colour on top. */
@Composable
private fun Card(color: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().drawBehind { drawRect(RULE, Offset(0f, size.height), Size(size.width, 2.dp.toPx())) }.background(Color.White)
        .drawBehind { drawRect(color, size = Size(size.width, 8.dp.toPx())) }.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun Box2(label: String?, text: String) {
    Text(buildString { if (label != null) append(label).append(' '); append(text) }, Modifier.fillMaxWidth().background(Color(0xFFEEF2F5)).padding(horizontal = 10.dp, vertical = 8.dp),
        style = Ski.type.body.copy(fontSize = 13.5.sp, lineHeight = 1.45.em), color = INK)
}

@Composable
private fun Goals(l: Lesson, met: BooleanArray?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        l.goals.forEachIndexed { i, g ->
            val ok = met?.getOrNull(i) == true
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (ok) "★" else "☆", style = Ski.type.body.copy(fontSize = 13.5.sp), color = if (ok) ACCENT else RULE)
                Text(stringResource(g), style = (if (ok) Ski.type.bodyBold else Ski.type.body).copy(fontSize = 13.5.sp, lineHeight = 1.4.em), color = if (ok) INK else MUTED)
            }
        }
    }
}

@Composable
private fun GoButton(text: String, onClick: () -> Unit) {
    val scale = Ski.type.displayScale
    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).drawBehind { drawRect(INK, Offset(0f, 4.dp.toPx()), size) }.background(ACCENT)
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.title.copy(fontSize = (34 * scale).sp, lineHeight = 1.em), color = INK, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OutlineButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 48.dp).border(2.dp, INK).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Ski.type.bodyBold, color = INK, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Brief(i: Int, onGo: () -> Unit, onLessons: () -> Unit) {
    val l = LESSONS[i]; val scale = Ski.type.displayScale
    Page {
        Card(Color(l.color)) {
            Text(stringResource(R.string.game_school_brief_header, (i + 1).toString(), LESSONS.size.toString()), style = Ski.type.label.copy(fontSize = 12.sp), color = MUTED)
            Text(stringResource(l.title), style = Ski.type.title.copy(fontSize = (40 * scale).sp, lineHeight = .95.em), color = INK)
            Figure(l.fig)
            Text(stringResource(l.rule), style = Ski.type.body.copy(fontSize = 16.sp), color = INK)
            Box2(stringResource(R.string.game_school_brief_controls), stringResource(l.ctrl))
            Box2(stringResource(R.string.game_school_brief_drill), stringResource(l.drill))
            Goals(l, null)
        }
        GoButton(stringResource(R.string.game_school_go), onGo)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(stringResource(R.string.game_school_back_to_lessons), Modifier.weight(1f), onLessons)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultCard(i: Int, r: SchoolRun.Result, onNext: () -> Unit, onRetry: () -> Unit, onLessons: () -> Unit) {
    val l = LESSONS[i]; val context = LocalContext.current; val scale = Ski.type.displayScale
    Page {
        Card(Color(l.color)) {
            Text(stringResource(R.string.game_school_result_header, (i + 1).toString(), stringResource(l.title)), style = Ski.type.label.copy(fontSize = 12.sp), color = MUTED)
            Row {
                Text("★".repeat(r.stars), style = Ski.type.title.copy(fontSize = (56 * scale).sp, lineHeight = .9.em, letterSpacing = 4.sp), color = ACCENT)
                Text("★".repeat(3 - r.stars), style = Ski.type.title.copy(fontSize = (56 * scale).sp, lineHeight = .9.em, letterSpacing = 4.sp), color = RULE)
            }
            Text(context.say(r.line), style = Ski.type.body.copy(fontSize = 16.sp), color = INK)
            Goals(l, r.goals)
            Box2(null, stringResource(R.string.game_school_remember, stringResource(l.tip)))
        }
        GoButton(stringResource(if (i < LESSONS.size - 1) (if (r.stars > 0) R.string.game_school_next_lesson else R.string.game_school_try_again) else R.string.game_school_back_to_lessons), onNext)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(stringResource(R.string.game_school_retry), Modifier.weight(1f), onRetry)
            OutlineButton(stringResource(R.string.game_school_back_to_lessons), Modifier.weight(1f), onLessons)
        }
    }
}

// ---------- the lesson cards' drawings (the site's D), forward up as on the slope; the words in a legend under them ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Figure(f: SchoolFig) {
    val legend: List<Pair<Int, Color>> = when (f) {
        SchoolFig.WEDGE -> listOf(R.string.game_school_d_tips_close to MUTED, R.string.game_school_d_tails_apart to MUTED)
        SchoolFig.OUTER -> listOf(R.string.game_school_d_press_right to RED, R.string.game_school_d_turn_left to MUTED)
        SchoolFig.SHAPE -> listOf(R.string.game_school_d_straight_fast to RED, R.string.game_school_d_across_slow to BLUE)
        SchoolFig.RHYTHM -> listOf(R.string.game_school_d_tick to MUTED)
        SchoolFig.LOOK -> listOf(R.string.game_school_d_eyes_far to MUTED)
        SchoolFig.CARVE -> listOf(R.string.game_school_d_carve_lines to INK, R.string.game_school_d_skid_smear to MUTED)
        SchoolFig.FINAL -> emptyList()
    }
    val h = when (f) { SchoolFig.OUTER, SchoolFig.SHAPE -> 130f; SchoolFig.FINAL -> 110f; else -> 120f }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(220f / h)) {
            scale(size.width / 220f, size.width / 220f, Offset.Zero) { drawFig(f) }
        }
        if (legend.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((id, c) in legend) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp, 4.dp).background(c, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                Text(stringResource(id), style = Ski.type.body.copy(fontSize = 13.sp, lineHeight = 1.35.em), color = c)
            }
        }
    }
}

private fun DrawScope.drawFig(f: SchoolFig) {
    fun p(vararg xy: Float) = Path().apply { moveTo(xy[0], xy[1]); var i = 2; while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 } }
    val dash = { a: Float, b: Float -> PathEffect.dashPathEffect(floatArrayOf(a, b)) }
    when (f) {
        SchoolFig.WEDGE -> {
            drawLine(INK, Offset(98f, 14f), Offset(60f, 106f), 10f, StrokeCap.Square)
            drawLine(INK, Offset(122f, 14f), Offset(160f, 106f), 10f, StrokeCap.Square)
        }
        SchoolFig.OUTER -> {
            drawPath(Path().apply { moveTo(110f, 120f); quadraticTo(110f, 60f, 50f, 14f) }, RULE, style = Stroke(3f, pathEffect = dash(6f, 5f)))
            translate(104f, 70f) { rotate(-20f, Offset.Zero) {
                drawRect(INK, Offset(-20f, -36f), Size(8f, 70f)); drawRect(RED, Offset(12f, -36f), Size(8f, 70f))
            } }
        }
        SchoolFig.SHAPE -> {
            drawPath(Path().apply { moveTo(110f, 124f); cubicTo(200f, 104f, 200f, 80f, 110f, 66f); cubicTo(20f, 52f, 20f, 28f, 110f, 6f) }, BLUE, style = Stroke(4f))
            drawLine(RED, Offset(110f, 6f), Offset(110f, 124f), 3f, pathEffect = dash(5f, 5f))
        }
        SchoolFig.RHYTHM -> {
            drawPath(Path().apply { moveTo(110f, 116f); cubicTo(170f, 100f, 170f, 80f, 110f, 64f); cubicTo(50f, 48f, 50f, 28f, 110f, 8f) }, BLUE, style = Stroke(4f))
            drawCircle(ACCENT, 7f, Offset(152f, 90f)); drawCircle(ACCENT, 7f, Offset(68f, 36f))
        }
        SchoolFig.LOOK -> {
            drawCircle(Color(0xFFF07A2E), 10f, Offset(110f, 108f))
            drawLine(MUTED, Offset(110f, 98f), Offset(150f, 14f), 2f, pathEffect = dash(4f, 4f))
            drawRect(BLUE.copy(alpha = .25f), Offset(80f, 70f), Size(4f, 16f)); drawRect(BLUE, Offset(132f, 42f), Size(4f, 16f)); drawRect(BLUE, Offset(150f, 12f), Size(4f, 16f))
        }
        SchoolFig.CARVE -> {
            drawPath(Path().apply { moveTo(30f, 110f); quadraticTo(110f, 70f, 190f, 10f) }, INK, style = Stroke(2f))
            drawPath(Path().apply { moveTo(40f, 114f); quadraticTo(120f, 74f, 200f, 14f) }, INK, style = Stroke(2f))
            drawPath(Path().apply { moveTo(20f, 40f); quadraticTo(50f, 50f, 80f, 70f) }, Color(0xFF9FB5CB).copy(alpha = .6f), style = Stroke(16f))
        }
        SchoolFig.FINAL -> {
            val m = p(10f, 100f, 80f, 30f, 120f, 60f, 160f, 14f, 210f, 100f).apply { close() }
            drawPath(m, Color(0xFFE9F1F8)); drawPath(m, Color(0xFF9FB5CB), style = Stroke(2f))
            drawPath(Path().apply { moveTo(150f, 26f); cubicTo(120f, 50f, 170f, 60f, 130f, 90f) }, BLUE, style = Stroke(3f))
        }
    }
}

// ---------- on the slope: the sign and the speed, the drill, the coach, the checklist, and the lesson's controls ----------

@Composable
private fun Hud(i: Int, l: Lesson, kmh: Int, over: Boolean, coach: Pair<Say, Int>?, checks: List<SchoolRun.Check>) {
    val context = LocalContext.current; val scale = Ski.type.displayScale
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text("${i + 1}. ${stringResource(l.title)}", Modifier.background(INK, SignShape(16.dp)).padding(start = 14.dp, end = 26.dp, top = 4.dp, bottom = 4.dp),
                style = Ski.type.title.copy(fontSize = (24 * scale).sp, lineHeight = 1.em), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.widthIn(min = 76.dp).background(if (over) RED else INK.copy(alpha = .86f)).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$kmh", style = Ski.type.title.copy(fontSize = (30 * scale).sp, lineHeight = 1.em), color = Color.White)
                Text(stringResource(R.string.game_school_kmh), style = Ski.type.small.copy(fontSize = 11.sp), color = Color.White.copy(alpha = .85f))
            }
        }
        // the drill, in short
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Text(firstSentence(stringResource(l.drill)), Modifier.drawBehind { drawRect(INK.copy(alpha = .15f), Offset(0f, size.height), Size(size.width, 2.dp.toPx())) }
                .background(Color.White.copy(alpha = .92f)).padding(horizontal = 12.dp, vertical = 5.dp),
                style = Ski.type.bodyBold.copy(fontSize = if (ltr) 13.sp else 14.sp, lineHeight = 1.25.em), color = INK, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        // the coach
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            coach?.let { (s, tone) ->
                Text(context.say(s), Modifier.widthIn(max = 480.dp).background(when (tone) { SchoolRun.GOOD -> GREEN; SchoolRun.BAD -> RED; else -> INK })
                    .padding(horizontal = 12.dp, vertical = 6.dp), style = Ski.type.bodyBold.copy(fontSize = 15.sp, lineHeight = 1.3.em), color = Color.White, textAlign = TextAlign.Center)
            }
        }
        // the checklist, on the right
        Column(Modifier.align(AbsoluteAlignment.Right).padding(top = 10.dp).widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = AbsoluteAlignment.Right) {
            for (c in checks) {
                val (mark, col) = when (c.mark) { SchoolRun.OK -> "✓ " to GREEN; SchoolRun.NO -> "✗ " to RED; else -> "☐ " to MUTED }
                Text(mark + context.say(c.say), Modifier.background(Color.White.copy(alpha = .9f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    style = Ski.type.label.copy(fontSize = 12.5.sp), color = col)
            }
        }
    }
}

/** The big word in the middle (the site's .pop): it jumps in, stays a moment and floats away. */
@Composable
private fun Pop(say: Say?, n: Int, still: Boolean) {
    val context = LocalContext.current
    val t = remember { Animatable(1f) }
    LaunchedEffect(n) { if (n > 0) { t.snapTo(0f); t.animateTo(1f, tween(1100, easing = LinearEasing)) } }
    if (say == null || t.value >= 1f) return
    val k = t.value
    val alpha = if (still) 1f else when { k < .2f -> k / .2f; k < .75f -> 1f; else -> 1 - (k - .75f) / .25f }
    val sc = if (still) 1f else when { k < .2f -> .6f + (k / .2f) * .46f; k < .3f -> 1.06f - (k - .2f) / .1f * .06f; else -> 1f }
    val dy = if (still) 0f else when { k < .2f -> 20f * (1 - k / .2f); k < .75f -> 0f; else -> -25f * (k - .75f) / .25f }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Text(context.say(say), Modifier.align(Alignment.TopCenter).offset(y = maxHeight * .3f - 30.dp + dy.dp).graphicsLayer { this.alpha = alpha; scaleX = sc; scaleY = sc },
            style = Ski.type.title.copy(fontSize = (minOf(54f, maxOf(34f, maxWidth.value * .1f)) * Ski.type.displayScale).sp, lineHeight = 1.em,
                shadow = Shadow(Color.White, Offset(0f, 4f), 14f)), color = INK, maxLines = 1, softWrap = false)
    }
}

/** Lesson 1: the wedge's width, from the finger's height (the slider on the left only shows it; on the left in every language, so offsets that do not mirror). */
@Composable
private fun Slider(pct: Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = maxHeight * .3f; val h = maxHeight * .52f
        Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = 14.dp, y = top).width(200.dp).height(h)) {
            Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = 26.dp).width(12.dp).height(h)
                .background(Brush.verticalGradient(0f to Color(0xFFEEF2F5), .6f to ACCENT, 1f to RED)))
            Text(stringResource(R.string.game_school_slider_straight), Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = 44.dp, y = (-2).dp),
                style = Ski.type.label.copy(fontSize = 11.sp), color = MUTED, maxLines = 1, softWrap = false)
            Text(stringResource(R.string.game_school_slider_wide), Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(x = 44.dp, y = 2.dp),
                style = Ski.type.label.copy(fontSize = 11.sp), color = MUTED, maxLines = 1, softWrap = false)
            Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = 4.dp, y = h * (pct / 100f) - 15.dp).size(56.dp, 30.dp).background(INK), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.game_school_slider_pct, pct.toString()), style = Ski.type.label.copy(fontSize = 11.sp, lineHeight = 1.em), color = Color.White,
                    textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** A button held down (a ski, the wedge): down when the finger lands, up when it leaves. */
private fun Modifier.hold(label: String, on: (Boolean) -> Unit) = this
    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume(); on(true); waitForUpOrCancellation(); on(false) } }
    .semantics { role = Role.Button; contentDescription = label; onClick { on(true); on(false); true } }

/** Lesson 2: the two skis, the left one on the left in every language. */
@Composable
private fun Skis(l: Boolean, r: Boolean, on: (right: Boolean, down: Boolean) -> Unit) {
    val scale = Ski.type.displayScale
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val left = stringResource(R.string.game_school_ski_left); val right = stringResource(R.string.game_school_ski_right)
            val press = stringResource(R.string.game_school_press_it)
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            // the row is laid out by the screen's direction; the left ski must stay on the left
            for (isRight in if (rtl) listOf(true, false) else listOf(false, true)) {
                val down = if (isRight) r else l
                Column(Modifier.weight(1f).heightIn(min = 96.dp).background(if (down) ACCENT else Color.White.copy(alpha = .88f)).border(2.dp, if (down) ACCENT else INK)
                    .hold(if (isRight) right else left) { on(isRight, it) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (isRight) right else left, style = Ski.type.title.copy(fontSize = (30 * scale).sp, lineHeight = 1.em), color = INK, textAlign = TextAlign.Center)
                    Text(press, style = Ski.type.small.copy(fontSize = 11.5.sp), color = INK)
                }
            }
        }
    }
}

/** Lesson 7: the wedge, held to slow down, at the bottom left. */
@Composable
private fun WedgeButton(on: Boolean, onHold: (Boolean) -> Unit) {
    val scale = Ski.type.displayScale
    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp)) {
        val label = stringResource(R.string.game_school_wedge_btn)
        Column(Modifier.align(AbsoluteAlignment.BottomLeft).width(minOf(maxWidth * .42f, 170.dp)).heightIn(min = 84.dp)
            .background(if (on) ACCENT else Color.White.copy(alpha = .85f)).border(2.dp, if (on) ACCENT else INK).hold(label, onHold),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            Text(label, style = Ski.type.title.copy(fontSize = (30 * scale).sp, lineHeight = 1.em), color = INK)
            Text(stringResource(R.string.game_school_wedge_btn_hint), style = Ski.type.small.copy(fontSize = 11.5.sp), color = INK, textAlign = TextAlign.Center)
        }
    }
}

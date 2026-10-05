package io.github.pini236.skiapp.game

import io.github.pini236.skiapp.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** Words from the game to the screen: a string resource and its arguments in the Hebrew order (a [Say] inside is said too). */
data class Say(val id: Int, val args: List<Any> = emptyList(), val count: Int = -1, val tail: String = "")

/** The ski school's seven lessons, each with its own control. */
enum class SchoolMode { WEDGE, SKIS, STEER, RHYTHM, LEAN }

/** The little drawing on a lesson's card (the site's D). */
enum class SchoolFig { WEDGE, OUTER, SHAPE, RHYTHM, LOOK, CARVE, FINAL }

/**
 * One lesson (the site's LS): its trail colour, control and drill, the slope in degrees, how long it is and how fast you
 * start (m, m/s). Gates are x, y (m down the slope) and width.
 */
class Lesson(
    val mode: SchoolMode, val color: Long, val slope: Float, val len: Float, val start: Float, val fig: SchoolFig,
    val title: Int, val rule: Int, val ctrl: Int, val drill: Int, val goals: IntArray, val tip: Int,
    val gates: List<FloatArray> = emptyList(), val demo: Float = 0f, val traps: FloatArray? = null, val trapLimit: Int = 0,
    val beat: Float = 0f, val traffic: Boolean = false, val fade: Boolean = false, val wedgeBtn: Boolean = false, val limit: Int = 0,
)

private const val GREEN = 0xFF1B8A4C
private const val BLUE = 0xFF1F5FC4
private const val RED = 0xFFD1342B

/** The site's seven lessons (site/games/school), in its order. */
val LESSONS = listOf(
    Lesson(SchoolMode.WEDGE, GREEN, 9f, 125f, 4f, SchoolFig.WEDGE, R.string.game_school_lesson1_title, R.string.game_school_lesson1_rule,
        R.string.game_school_lesson1_controls, R.string.game_school_lesson1_drill,
        intArrayOf(R.string.game_school_lesson1_goal1, R.string.game_school_lesson1_goal2, R.string.game_school_lesson1_goal3), R.string.game_school_tip_1),
    Lesson(SchoolMode.SKIS, GREEN, 10f, 175f, 3f, SchoolFig.OUTER, R.string.game_school_lesson2_title, R.string.game_school_lesson2_rule,
        R.string.game_school_lesson2_controls, R.string.game_school_lesson2_drill,
        intArrayOf(R.string.game_school_lesson2_goal1, R.string.game_school_lesson2_goal2, R.string.game_school_lesson2_goal3), R.string.game_school_tip_2,
        gates = listOf(floatArrayOf(-5f, 28f, 6f), floatArrayOf(5f, 52f, 6f), floatArrayOf(-5f, 76f, 6f), floatArrayOf(5f, 100f, 6f), floatArrayOf(-5f, 124f, 6f), floatArrayOf(5f, 148f, 6f))),
    Lesson(SchoolMode.STEER, BLUE, 15f, 285f, 3f, SchoolFig.SHAPE, R.string.game_school_lesson3_title, R.string.game_school_lesson3_rule,
        R.string.game_school_lesson3_controls, R.string.game_school_lesson3_drill,
        intArrayOf(R.string.game_school_lesson3_goal1, R.string.game_school_lesson3_goal2, R.string.game_school_lesson3_goal3), R.string.game_school_tip_3,
        demo = 3.5f, traps = floatArrayOf(70f, 130f, 190f, 250f), trapLimit = 22),
    Lesson(SchoolMode.RHYTHM, BLUE, 14f, 240f, 5f, SchoolFig.RHYTHM, R.string.game_school_lesson4_title, R.string.game_school_lesson4_rule,
        R.string.game_school_lesson4_controls, R.string.game_school_lesson4_drill,
        intArrayOf(R.string.game_school_lesson4_goal1, R.string.game_school_lesson4_goal2, R.string.game_school_lesson4_goal3), R.string.game_school_tip_4,
        beat = 1.15f),
    Lesson(SchoolMode.STEER, RED, 15f, 260f, 4f, SchoolFig.LOOK, R.string.game_school_lesson5_title, R.string.game_school_lesson5_rule,
        R.string.game_school_lesson5_controls, R.string.game_school_lesson5_drill,
        intArrayOf(R.string.game_school_lesson5_goal1, R.string.game_school_lesson5_goal2, R.string.game_school_lesson5_goal3), R.string.game_school_tip_5,
        gates = listOf(floatArrayOf(0f, 35f, 6f), floatArrayOf(-6f, 70f, 6f), floatArrayOf(5f, 105f, 6f), floatArrayOf(-4f, 140f, 6f), floatArrayOf(6f, 175f, 6f),
            floatArrayOf(-5f, 210f, 6f), floatArrayOf(3f, 245f, 6f)), traffic = true, fade = true),
    Lesson(SchoolMode.LEAN, RED, 16f, 285f, 6f, SchoolFig.CARVE, R.string.game_school_lesson6_title, R.string.game_school_lesson6_rule,
        R.string.game_school_lesson6_controls, R.string.game_school_lesson6_drill,
        intArrayOf(R.string.game_school_lesson6_goal1, R.string.game_school_lesson6_goal2, R.string.game_school_lesson6_goal3), R.string.game_school_tip_6),
    Lesson(SchoolMode.STEER, BLUE, 16f, 340f, 4f, SchoolFig.FINAL, R.string.game_school_lesson7_title, R.string.game_school_lesson7_rule,
        R.string.game_school_lesson7_controls, R.string.game_school_lesson7_drill,
        intArrayOf(R.string.game_school_lesson7_goal1, R.string.game_school_lesson7_goal2, R.string.game_school_lesson7_goal3), R.string.game_school_tip_7,
        gates = listOf(floatArrayOf(-5f, 40f, 7f), floatArrayOf(5f, 85f, 7f), floatArrayOf(-6f, 130f, 7f), floatArrayOf(4f, 175f, 7f), floatArrayOf(-5f, 220f, 7f),
            floatArrayOf(5f, 265f, 7f), floatArrayOf(0f, 310f, 8f)), wedgeBtn = true, traffic = true, limit = 40),
)

/**
 * One lesson on the slope (the site's ski school, 13.6): the skier's physics, the control of the lesson and the drill
 * the coach watches. x runs across the slope (right positive) and y down it, in metres; the screen sees the slope from
 * behind the skier, forward up, [w] by [h] pixels with [px] pixels a metre. The words and the sounds go to [out].
 */
class SchoolRun(val lv: Lesson, private val out: Out) {
    /** What the screen hears from the lesson. */
    interface Out {
        fun coach(say: Say, tone: Int) {}
        fun pop(say: Say) {}
        fun checks(items: List<Check>) {}
        fun ding(f: Double, d: Double = .3, v: Float = .18f, square: Boolean = false) {}
        fun buzz(strength: Float) {}
        fun finished(r: Result) {}
    }
    /** A line of the drill's checklist: open, done (OK) or missed (NO). */
    data class Check(val say: Say, val mark: Int)
    /** The end: which of the three goals were met, the stars, and the line under them. */
    class Result(val goals: BooleanArray, val stars: Int, val line: Say)

    class Skier { var x = 0f; var y = -2f; var h = 0f; var v = 0f; var wedge = 0f; var skid = 0f; var edge = 0f; var crashT = 0f; var jerk = 0f }
    class Gate(val x: Float, val y: Float, val w: Float) { var done: Boolean? = null }
    class Person(var x: Float, var y: Float, val v: Float, var ph: Float, val amp: Float, val color: Long) { var hit = false }
    class Track(val x: Float, val y: Float, val h: Float, val sk: Float, val wd: Float)
    class Finger(var x: Float, var y: Float)
    private class Arc(val side: Float, val h0: Float) { var dur = 0f; var clean = true }

    var w = 1080f; var h = 2200f; var px = 54f
    fun sx(x: Float) = w / 2 + (x - camX) * px
    fun sy(y: Float) = h * .7f - (y - camY) * px

    val s = Skier().also { it.v = lv.start }
    val gates = lv.gates.map { Gate(it[0], it[1], it[2]) }
    val people = if (!lv.traffic) emptyList() else List(6) { k ->
        Person((if (k % 2 == 1) 1 else -1) * (2f + (k * 37) % 6), 45 + k * (lv.len - 70) / 6, 2 + (k % 3) * .8f, k * 1.3f, 2.5f + k % 3,
            longArrayOf(0xFF1B8A4C, 0xFF7B4FC4, 0xFFF4B942, 0xFF1F5FC4)[k % 4])
    }
    val tracks = ArrayList<Track>()
    var finger: Finger? = null
    var t = 0f; private set
    var camY = s.y; private set
    var camX = 0f; private set
    var running = true; private set
    var result: Result? = null; private set

    // the controls: the wedge slider, the two skis and the wedge button held, the rhythm's turn
    var wedgeCtl = if (lv.mode == SchoolMode.WEDGE) .15f else 0f; private set
    var skiL = false; private set
    var skiR = false; private set
    var wedgeBtn = false
    private var turnDir = 1f
    private var target = 0f

    // the drill's tally (the site's st)
    var time = 0f; private set
    var maxV = 0f; private set
    var passed = 0; private set
    var crashes = 0; private set
    private var under = 0f
    val stops = ArrayList<Float?>()
    private var stopIdx = 0
    private var band = 0f
    private var bandT = 0f
    var wrong = 0; private set
    val traps = ArrayList<Boolean>()
    private var trapIdx = 0
    var taps = 0; private set
    var good = 0; private set
    var perfect = 0; private set
    var streak = 0; private set
    var best = 0; private set
    private var beatN = 0
    var pulse = 0f; private set
    var arcs = 0; private set
    var skidT = 0f; private set
    private var arc: Arc? = null
    private val beat0 = .6f
    private var lastTap = 0f
    private var finishIn = 0f

    /** The speed now, km/h. */
    val kmh get() = s.v * KMH
    /** Over the final test's limit. */
    val over get() = lv.limit > 0 && kmh > lv.limit

    private var coachSaid: Pair<Say, Int>? = null
    private fun coach(say: Say, tone: Int = PLAIN) { val k = say to tone; if (k == coachSaid) return; coachSaid = k; out.coach(say, tone) }
    private fun say(id: Int, vararg a: Any) = Say(id, a.toList())

    init {
        coach(say(if (lv.mode == SchoolMode.STEER && lv.demo > 0) R.string.game_school_coach_watch_demo else R.string.game_school_coach_start))
        checks()
    }

    // ---------- input ----------
    /** A ski button held or let go (lesson 2): the coach checks the side before each gate. */
    fun ski(right: Boolean, down: Boolean) {
        if (right) skiR = down else skiL = down
        if (!down || !running || lv.mode != SchoolMode.SKIS) return
        val g = nextGate() ?: return
        val turnsLeft = right
        val need = if (g.x < s.x - 1) -1 else if (g.x > s.x + 1) 1 else 0
        if (need != 0 && (need < 0) != turnsLeft) {
            wrong++
            coach(say(if (turnsLeft) R.string.game_school_coach_wrong_left else R.string.game_school_coach_wrong_right), BAD)
            out.buzz(.7f); out.ding(200.0, .2, .15f, true); checks()
        } else if (need != 0) coach(say(if (turnsLeft) R.string.game_school_coach_correct_left else R.string.game_school_coach_correct_right), GOOD)
    }

    /** Lesson 4: every tap starts a turn the other way, graded against the beat. */
    fun tap() {
        if (!running || lv.mode != SchoolMode.RHYTHM) return
        taps++; lastTap = t
        val b = lv.beat; val ph = ((t - beat0) % b + b) % b; val off = min(ph, b - ph) * (if (ph < b / 2) 1 else -1)
        turnDir = -turnDir; target = turnDir * .95f
        val a = abs(off)
        when {
            a < .12f -> { perfect++; good++; streak++; coach(say(R.string.game_school_beat_perfect), GOOD); out.ding(880.0, .12, .15f) }
            a < .25f -> { good++; streak++; coach(say(R.string.game_school_beat_good), GOOD); out.ding(660.0, .1, .12f) }
            else -> { streak = 0; coach(say(if (off > 0) R.string.game_school_beat_late else R.string.game_school_beat_early), BAD); out.ding(220.0, .12, .1f, true) }
        }
        best = max(best, streak); checks()
    }

    private fun nextGate() = gates.firstOrNull { it.done == null }

    // ---------- the slope ----------
    fun update(dt: Float) {
        t += dt
        val a = lv.slope * PI.toFloat() / 180
        var want: Float? = s.h; var rateMax = 2.4f; var wedge = 0f
        when (lv.mode) {
            SchoolMode.WEDGE -> {
                want = 0f
                finger?.let { val top = h * .3f; val bot = h * .82f; wedgeCtl = ((it.y - top) / (bot - top)).coerceIn(0f, 1f) }
                wedge = wedgeCtl
            }
            SchoolMode.SKIS -> { wedge = .25f; rateMax = 1.3f; want = if (skiR && !skiL) -.75f else if (skiL && !skiR) .75f else s.h * .6f }
            SchoolMode.STEER -> {
                val f = finger
                if (lv.demo > 0 && t < lv.demo) want = 0f
                else if (f != null) { val dx = f.x - sx(s.x); val fwd = sy(s.y) - f.y; want = atan2(dx, max(-px * 1.5f, fwd)) }
                wedge = if (lv.wedgeBtn && wedgeBtn) 1f else 0f
            }
            SchoolMode.RHYTHM -> { want = target; rateMax = 1.6f }
            SchoolMode.LEAN -> {
                val f = finger
                val e = if (f != null) ((f.x - w / 2) / (w * .32f)).coerceIn(-1f, 1f) else s.edge * .98f
                // how fast the skis really change edge (not the finger): smoothed, so touches that come in bursts do not
                // count as snaps (X-5, the site's fix)
                val e0 = s.edge
                s.edge += (e - s.edge).coerceIn(-3 * dt, 3 * dt)
                s.jerk += (abs(s.edge - e0) / dt - s.jerk) * min(1f, dt * 10)
                want = null
            }
        }
        want = want?.coerceIn(-1.9f, 1.9f)
        s.wedge += (wedge - s.wedge) * min(1f, dt * 8)
        var rate: Float
        if (lv.mode == SchoolMode.LEAN) {
            // the ski's shape turns you: more edge, a tighter turn; snapping from edge to edge breaks the grip
            rate = s.v * s.edge * .11f
            val sk = max(0f, s.jerk - 2.2f) / .8f // a full edge change in under about 0.9 s is a snap
            s.skid += (min(1f, sk) - s.skid) * min(1f, dt * 8)
            if (s.h > 1.9f && rate > 0 || s.h < -1.9f && rate < 0) rate = 0f
        } else {
            val d = want!! - s.h
            rate = (d * 3.2f).coerceIn(-rateMax, rateMax)
            val carveRate = s.v / 9 + .25f
            val skid = if (lv.mode == SchoolMode.RHYTHM) 0f else max(0f, abs(rate) - carveRate) // the rhythm lesson is about timing, not edging
            s.skid += (min(1f, skid / 1.2f) - s.skid) * min(1f, dt * 10)
        }
        s.h += rate * dt
        var acc = G * sin(a) * cos(s.h) - .05f * G * cos(a) - .0035f * s.v * s.v - s.wedge * (2.6f + s.v * .35f) - s.skid * s.v * .9f - abs(rate) * s.v * .06f
        if (s.crashT > 0) { s.crashT -= dt; acc = -6f }
        // in the turning and edging lessons you never stall: a push with the poles, so a skier who turned up the hill can
        // turn back (X-5: the edging lesson stalled)
        s.v = max(if ((lv.mode == SchoolMode.SKIS || lv.mode == SchoolMode.LEAN) && running) 1.2f else 0f, s.v + acc * dt)
        if (!running) s.v = max(0f, s.v - 6 * dt)
        s.x += sin(s.h) * s.v * dt; s.y += cos(s.h) * s.v * dt; s.x = s.x.coerceIn(-14f, 14f)
        camY += (s.y - camY) * min(1f, dt * 6); camX += (s.x * .55f - camX) * min(1f, dt * 3)
        val lt = tracks.lastOrNull()
        if (s.v > .3f && (lt == null || hypot(lt.x - s.x, lt.y - s.y) > .25f)) tracks += Track(s.x, s.y, s.h, s.skid, s.wedge)
        if (tracks.size > 2500) tracks.subList(0, 500).clear()
        if (!running) return
        time += dt
        val kmh = kmh
        // the top speed counts once you steer: not in the demo, nor the moment after it (X-5)
        if (!(lv.demo > 0 && t < lv.demo + 1.5f)) maxV = max(maxV, kmh)
        if (lv.limit > 0 && kmh <= lv.limit) under += dt
        drills(dt, kmh)
        for (g in gates) if (g.done == null && s.y >= g.y) {
            val ok = abs(s.x - g.x) <= g.w / 2; g.done = ok
            if (ok) { passed++; out.ding(660.0 + passed * 30); out.pop(say(R.string.game_school_pop_gate)) } else { out.pop(say(R.string.game_school_pop_gate_missed)); out.buzz(.5f) }
            checks()
        }
        for (p in people) {
            p.ph += dt * .6f; p.x += cos(p.ph) * p.amp * dt * .8f; p.y += p.v * dt
            if (!p.hit && hypot(p.x - s.x, p.y - s.y) < 1.3f && s.crashT <= 0) {
                p.hit = true; crashes++; s.crashT = .8f; s.v *= .3f
                out.pop(say(R.string.game_school_pop_crash)); coach(say(R.string.game_school_coach_priority), BAD); out.buzz(1f); checks()
            } else if (!p.hit && p.y > s.y && p.y - s.y < 9 && abs(p.x - s.x) < 3) coach(say(R.string.game_school_coach_skier_ahead))
        }
        if (finishIn > 0) { finishIn -= dt; if (finishIn <= 0) finish() }
        if (running && s.y >= lv.len) finish()
    }

    // ---------- what each drill watches and says ----------
    private fun drills(dt: Float, kmh: Float) {
        when (lv.mode) {
            SchoolMode.WEDGE -> {
                val line = STOPS.getOrNull(stopIdx)
                if (line != null && finishIn <= 0) {
                    val d = line - s.y
                    if (s.v < .12f && d >= 0 && d < 6 && t > 1) {
                        stops += d; stopIdx++
                        out.pop(say(if (d < 1.5f) R.string.game_school_pop_stop_perfect else R.string.game_school_pop_stopped)); out.ding(if (d < 1.5f) 880.0 else 660.0)
                        coach(say(R.string.game_school_coach_narrow_continue), GOOD); checks()
                        if (stopIdx >= STOPS.size) finishIn = .9f
                    } else if (s.y > line) {
                        stops += null; stopIdx++
                        out.pop(say(R.string.game_school_pop_line_passed)); out.buzz(.7f); checks()
                        if (stopIdx >= STOPS.size) finishIn = .9f
                    } else if (d < 14 && s.v > 2) coach(say(R.string.game_school_coach_line_close))
                }
                if (s.y > BAND[0] && s.y < BAND[1]) {
                    bandT += dt
                    if (kmh >= 6 && kmh <= 10) { band += dt; coach(say(R.string.game_school_coach_in_band, kmh.roundToInt()), GOOD) }
                    else coach(say(if (kmh > 10) R.string.game_school_coach_too_fast else R.string.game_school_coach_too_slow), BAD)
                } else if (!(line != null && line - s.y < 14 && s.v > 2)) {
                    val c = wedgeCtl
                    coach(say(if (c < .2f) R.string.game_school_coach_skis_straight else if (c < .6f) R.string.game_school_coach_narrow_pizza else R.string.game_school_coach_wide_pizza),
                        if (c >= .6f) GOOD else PLAIN)
                }
            }
            SchoolMode.SKIS -> {
                val g = nextGate()
                if (g != null && !skiL && !skiR) coach(say(if (g.x < s.x) R.string.game_school_coach_next_gate_left else R.string.game_school_coach_next_gate_right))
            }
            SchoolMode.STEER -> {
                val traps = lv.traps ?: return
                if (lv.demo > 0 && t < lv.demo) { coach(say(R.string.game_school_coach_demo_speed, kmh.roundToInt())); return }
                if (lv.demo > 0 && t < lv.demo + .1f && t - dt < lv.demo) out.pop(say(R.string.game_school_pop_your_turn))
                val tr = traps.getOrNull(trapIdx)
                if (tr != null && s.y >= tr) {
                    val ok = kmh <= lv.trapLimit; this.traps += ok; trapIdx++
                    out.pop(if (ok) say(R.string.game_school_pop_trap_ok) else say(R.string.game_school_pop_trap_caught, kmh.roundToInt()))
                    if (ok) out.ding(880.0) else out.buzz(.9f)
                    checks()
                }
                val ah = abs(s.h)
                coach(say(if (ah < .35f) R.string.game_school_coach_straight else if (ah < 1f) R.string.game_school_coach_diagonal else R.string.game_school_coach_across),
                    if (ah < .35f) BAD else if (ah > 1) GOOD else PLAIN)
            }
            SchoolMode.RHYTHM -> {
                val b = lv.beat; val n = floor((t - beat0) / b).toInt()
                if (n > beatN) {
                    beatN = n; out.ding(1200.0, .05, .12f, true); pulse = 1f
                    if (t - lastTap > b * 1.8f && taps > 0) { streak = 0; coach(say(R.string.game_school_coach_missed_beat), BAD) }
                }
                pulse = max(0f, pulse - dt * 3)
                if (taps == 0) coach(say(R.string.game_school_coach_listen))
            }
            SchoolMode.LEAN -> {
                if (s.skid > .25f) { skidT += dt; coach(say(R.string.game_school_coach_skid), BAD) }
                else if (abs(s.edge) > .3f) coach(say(R.string.game_school_coach_on_edge), GOOD)
                else coach(say(R.string.game_school_coach_flat))
                // a clean arc: from one side to the other, long enough, without skidding
                val side = if (abs(s.edge) > .3f) sign(s.edge) else 0f
                if (side != 0f) {
                    val c = arc
                    if (c == null || c.side != side) {
                        if (c != null && c.clean && c.dur > .8f && abs(s.h - c.h0) > .6f) {
                            arcs++; out.pop(say(R.string.game_school_pop_clean_arc, arcs)); out.ding(700.0 + arcs * 40); checks()
                        }
                        arc = Arc(side, s.h)
                    }
                    arc!!.dur += dt; if (s.skid > .25f) arc!!.clean = false
                }
            }
        }
    }

    /** The drill's checklist. */
    fun checks() {
        fun mark(done: Boolean?, ok: Boolean) = if (done == null) OPEN else if (ok) OK else NO
        val items: List<Check> = when {
            lv.mode == SchoolMode.WEDGE -> {
                val l = STOPS.indices.map { i -> Check(say(R.string.game_school_check_stop_line, i + 1), if (i >= stops.size) OPEN else if (stops[i] == null) NO else OK) }.toMutableList()
                l.add(2, Check(if (bandT > 0) say(R.string.game_school_check_band_pct, (band / max(.01f, bandT) * 100).roundToInt()) else say(R.string.game_school_check_band),
                    if (bandT > 0 && s.y > BAND[1]) (if (band / bandT >= .7f) OK else NO) else OPEN))
                l
            }
            lv.mode == SchoolMode.SKIS -> listOf(
                Check(say(R.string.game_school_check_gates, passed, gates.size), mark(if (gates.all { it.done != null }) true else null, passed == gates.size)),
                Check(say(R.string.game_school_check_wrong, wrong), if (wrong > 1) NO else OPEN))
            lv.traps != null -> lv.traps.indices.map { i -> Check(say(R.string.game_school_check_trap, i + 1), if (i >= traps.size) OPEN else if (traps[i]) OK else NO) }
            lv.mode == SchoolMode.RHYTHM -> listOf(
                Check(say(R.string.game_school_check_on_beat, good), if (good >= 10) OK else OPEN),
                Check(say(R.string.game_school_check_perfect, perfect), if (perfect >= 8) OK else OPEN),
                Check(say(R.string.game_school_check_streak, streak, best), if (best >= 8) OK else OPEN))
            lv.mode == SchoolMode.LEAN -> listOf(
                Check(say(R.string.game_school_check_arcs, arcs), if (arcs >= 8) OK else OPEN),
                Check(say(R.string.game_school_check_skid, fixed1(skidT)), if (skidT >= 1.5f) NO else OPEN))
            else -> listOf(
                Check(say(R.string.game_school_check_gates, passed, gates.size), OPEN),
                Check(say(R.string.game_school_check_crashes, crashes), if (crashes > 0) NO else OPEN))
        }
        out.checks(items)
    }

    /** The lesson is over: the three goals, the stars and the line under them. */
    fun finish() {
        if (!running) return
        running = false
        val g: BooleanArray; val line: Say
        when {
            lv.mode == SchoolMode.WEDGE -> {
                val ok = stops.filterNotNull(); val avg = if (ok.isEmpty()) 99f else ok.sum() / ok.size; val bnd = if (bandT > 0) band / bandT else 0f
                g = booleanArrayOf(ok.size == 3, bnd >= .7f, ok.size == 3 && avg <= 1.5f)
                line = say(R.string.game_school_res_wedge, ok.size, if (avg < 90) say(R.string.game_school_res_meters, fixed1(avg)) else "-", (bnd * 100).roundToInt())
            }
            lv.mode == SchoolMode.SKIS -> {
                g = booleanArrayOf(passed >= 4, passed == 6, passed == 6 && wrong <= 1)
                line = say(R.string.game_school_res_skis, passed, wrong)
            }
            lv.traps != null -> {
                val n = traps.count { it }
                g = booleanArrayOf(n >= 3, n == 4, n == 4 && maxV <= 35)
                line = say(R.string.game_school_res_traps, n, maxV.roundToInt())
            }
            lv.mode == SchoolMode.RHYTHM -> {
                g = booleanArrayOf(good >= 10, perfect >= 8, best >= 8)
                line = say(R.string.game_school_res_rhythm, good, perfect, best)
            }
            lv.mode == SchoolMode.LEAN -> {
                g = booleanArrayOf(arcs >= 4, arcs >= 8, arcs >= 8 && skidT < 1.5f)
                line = say(R.string.game_school_res_lean, arcs, fixed1(skidT))
            }
            lv.fade -> {
                g = booleanArrayOf(passed >= 5, passed >= 6 && crashes == 0, passed == 7 && crashes == 0)
                line = say(R.string.game_school_res_look, passed, crashes)
            }
            else -> {
                val u = if (time > 0) under / time else 0f; val all = passed == gates.size && crashes == 0
                g = booleanArrayOf(passed >= 5, all, all && u >= .9f)
                line = say(R.string.game_school_res_final, passed, gates.size, crashes, (u * 100).roundToInt())
            }
        }
        val r = Result(g, g.count { it }, line)
        result = r
        out.finished(r)
    }

    companion object {
        const val G = 9.81f
        const val KMH = 3.6f
        /** The wedge lesson's three stop lines, and the green band between the second and the third (m down the slope). */
        val STOPS = floatArrayOf(35f, 72f, 112f)
        val BAND = floatArrayOf(80f, 102f)
        const val PLAIN = 0; const val GOOD = 1; const val BAD = 2
        const val OPEN = 0; const val OK = 1; const val NO = 2
        private fun fixed1(f: Float) = String.format(Locale.ROOT, "%.1f", f)
    }
}

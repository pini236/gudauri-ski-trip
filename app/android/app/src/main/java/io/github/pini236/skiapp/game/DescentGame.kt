package io.github.pini236.skiapp.game

import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Profile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** The third star of each run (the site's MISSION): its words and whether it was met. */
class Mission(val text: Int, val ok: (DescentGame.Stats) -> Boolean)

val MISSIONS = mapOf(
    "Pirveli" to Mission(R.string.game_descent_mission_pirveli) { it.flips >= 3 },
    "Kikilo 1" to Mission(R.string.game_descent_mission_kikilo_1) { it.coins >= 25 },
    "Tatra 2" to Mission(R.string.game_descent_mission_tatra_2) { it.overCat },
    "Sadzele 3" to Mission(R.string.game_descent_mission_sadzele_3) { it.falls == 0 },
    "Sadzele 1" to Mission(R.string.game_descent_mission_sadzele_1) { it.maxFlip >= 2 },
)

/** The run colours on the signs (the site's RUNCOL). */
fun runColor(c: String): Long = when (c) { "green" -> 0xFF1B8A4C; "blue" -> 0xFF1F5FC4; "red" -> 0xFFD1342B; else -> 0xFF13233A }

/** The site's rng(): the same numbers from the same seed, so a run is laid out as on the site. */
class SiteRng(private var seed: Int) {
    fun next(): Double {
        seed += 0x6D2B79F5
        var t = (seed xor (seed ushr 15)) * (1 or seed)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/**
 * A run of the descent laid out for the game (the site's buildRun): the real profile from its highest point to its
 * lowest after it, exaggerated 1.5 times, with kickers where a gentler stretch meets a steeper one, gullies on the
 * steeper kickers, rocks, a snowcat, deep powder, ice, wind on the ridge above 2,900 m, khachapuri over every kicker,
 * the five others waiting along the run, and a dog near the bottom. [index] is the run's place among the five (the
 * seed), [me] the coat you wear (the others are the rest).
 */
class Course(p: Profile, index: Int, me: Int) {
    class Kicker(val x: Float) { var gap = false }
    class Gap(val x0: Float, val x1: Float) { var warned = false }
    class Rock(val x: Float, val h: Float) { var hit = false }
    class Cat(var x: Float, val v: Float) { var over = false; var hit = false; var warned = false }
    class Zone(val kind: Int, val x0: Float, val x1: Float) { var warned = false }
    class Coin(val x: Float, val dy: Float) { var got = false }
    class Spot(val coat: Int, val x: Float) { var got = false; var lost = false; var joinT = 0f }
    class Sign(val x: Float, val text: Int)

    val key = p.key; val color = p.color; val lift = p.lift
    /** The real heights every 5 m, top to bottom. */
    val h: FloatArray
    val len: Float; val top: Int; val bot: Int
    val kickers = ArrayList<Kicker>(); val gaps = ArrayList<Gap>(); val rocks = ArrayList<Rock>(); val cats = ArrayList<Cat>()
    val zones = ArrayList<Zone>(); val coins = ArrayList<Coin>(); val spots: List<Spot>; val signs = ArrayList<Sign>()
    val dogX: Float
    private val terrain: FloatArray

    init {
        // ride from the highest point of the line to its lowest point after it; the layout in doubles, as on the site
        var i0 = 0; for (i in p.h.indices) if (p.h[i] > p.h[i0]) i0 = i
        var i1 = i0; for (i in i0 until p.h.size) if (p.h[i] < p.h[i1]) i1 = i
        val hh = p.h.copyOfRange(i0, i1 + 1); h = FloatArray(hh.size) { hh[it].toFloat() }
        val n = hh.size; val rl = (n - 1) * 5.0; len = rl.toFloat(); top = Math.round(hh[0]).toInt(); bot = Math.round(hh[n - 1]).toInt()
        val rnd = SiteRng(index * 7919 + 17); fun r() = rnd.next()
        val hard = when (p.color) { "green" -> 0; "blue" -> 1; "red" -> 2; else -> 3 }
        val base = DoubleArray(n) { (hh[it] - hh[0]) * EXAG }
        fun steep(i: Int) = (base[max(0, i - 4)] - base[min(n - 1, i + 4)]) / 40
        // kickers: where a gentler stretch meets a steeper one (a natural lip), or every ~170 m
        var lastK = -999
        for (i in 24 until n - 30) {
            val s1 = (base[i - 6] - base[i]) / 30; val s2 = (base[i] - base[i + 6]) / 30
            if ((s2 - s1 > .08 && i * 5 - lastK > 90) || i * 5 - lastK > 170) { kickers += Kicker(i * 5f); lastK = i * 5 }
        }
        // gullies to jump: on the steeper kickers in the middle of the run (none on the green)
        val want = intArrayOf(0, 1, 2, 2)[hard]
        val cand = kickers.filter { it.x > rl * .25 && it.x < rl * .85 }.sortedByDescending { steep((it.x / 5).toInt() - 6) }
        for (k in cand) { if (gaps.size >= want) break; if (gaps.any { abs(it.x0 - k.x) < 250 }) continue; k.gap = true; gaps += Gap(k.x + .5f, k.x + 7.5f) }
        terrain = FloatArray(n) { i ->
            val x = i * 5.0; var y = base[i] + kotlin.math.sin(x * .09) * .35 + kotlin.math.sin(x * .031 + 1.3) * .6
            for (k in kickers) { val d = x - k.x; val hg = if (k.gap) 3.0 else 2.2; if (d > -15 && d <= 0) y += hg * ((d + 15) / 15).pow(2) }
            y.toFloat()
        }
        fun busy(x: Double, rr: Double) = x < 60 || x > rl - 60 || kickers.any { x > it.x - rr - 15 && x < it.x + rr + 22 } || gaps.any { x > it.x0 - rr - 15 && x < it.x1 + rr + 15 }
        // rocks to hop over
        val rockX = ArrayList<Double>()
        run { var x = 110 + r() * 80; while (x < rl - 80) { if (!busy(x, 10.0)) { rocks += Rock(x.toFloat(), (.7 + r() * .35).toFloat()); rockX += x }; x += intArrayOf(260, 190, 150, 120)[hard] * (.7 + r() * .6) } }
        // one snowcat grooming its way up the run, from about the middle
        val catX = run { var x = rl * (.42 + r() * .12); var k = 0; while (k < 40 && busy(x, 20.0)) { x += 9; k++ }; x }
        cats += Cat(catX.toFloat(), 1.6f)
        // deep powder slows you, ice is fast, wind on the ridge (only above 2,900 m real) pushes you back
        val zoneX = ArrayList<DoubleArray>()
        run {
            var zx = 150 + r() * 100
            while (zx < rl - 120) {
                val kind = if (r() < .5) POWDER else ICE; val w = if (kind == POWDER) 22 + r() * 10 else 30 + r() * 15
                if (!busy(zx, w) && !rockX.any { it > zx - 8 && it < zx + w + 8 }) { zones += Zone(kind, zx.toFloat(), (zx + w).toFloat()); zoneX += doubleArrayOf(zx, zx + w) }
                zx += 230 + r() * 200
            }
            var nw = 0; var x = 60.0
            while (x < rl - 60 && nw < 3) { if (realH(x.toFloat()) > 2900 && !busy(x, 45.0)) { zones += Zone(WIND, x.toFloat(), (x + 45).toFloat()); zoneX += doubleArrayOf(x, x + 45); nw++; x += 240 }; x += 5 }
        }
        // khachapuri: an arc over every kicker (the reward for jumping) and short lines on the snow
        for (k in kickers) { val span = if (k.gap) 20 else 16; for (j in 0 until 5) { val f = (j + .5f) / 5; coins += Coin(k.x + 3 + span * f, 1.2f + 4.2f * sin(PI.toFloat() * f)) } }
        run { var x = 90.0; while (x < rl - 60) { if (!(busy(x, 14.0) || rockX.any { abs(it - x) < 18 })) for (j in 0 until 4) coins += Coin((x + j * 2.6).toFloat(), .9f); x += 110 + r() * 70 } }
        // the five others wait along the run, not on top of anything else
        spots = COATS.indices.filter { it != me }.mapIndexed { k, c ->
            var x = rl * (.1 + .16 * k) + 12; var j = 0
            while (j < 30 && (busy(x, 6.0) || rockX.any { abs(it - x) < 12 } || zoneX.any { x > it[0] - 6 && x < it[1] + 6 })) { x += 7; j++ }
            Spot(c, x.toFloat())
        }
        dogX = (rl * .72).toFloat()
        // signs a little ahead of each surprise
        for (g in gaps) signs += Sign(g.x0 - 45, R.string.game_descent_sign_gully)
        for (z in zones) if (z.kind != WIND) signs += Sign(z.x0 - 30, if (z.kind == POWDER) R.string.game_descent_powder else R.string.game_descent_ice)
        signs += Sign(cats[0].x - 60, R.string.game_descent_sign_snowcat)
    }

    fun realH(x: Float): Float { val i = (x / 5).coerceIn(0f, h.size - 1.001f); val i0 = i.toInt(); return h[i0] + (h[i0 + 1] - h[i0]) * (i - i0) }
    fun inGap(x: Float): Gap? { for (g in gaps) if (x > g.x0 && x < g.x1) return g; return null }
    fun baseGround(x: Float): Float { val i = (x / 5).coerceIn(0f, terrain.size - 2f); val i0 = floor(i).toInt().coerceAtMost(terrain.size - 2); val f = i - i0; return terrain[i0] * (1 - f) + terrain[i0 + 1] * f }
    fun ground(x: Float) = baseGround(x) - (if (inGap(x) != null) 7 else 0)
    /** The snow's angle, negative going down (a gully's walls don't count). */
    fun slope(x: Float) = atan2(baseGround(x + 1) - baseGround(x - 1), 2f)
    fun zoneAt(x: Float): Zone? { for (z in zones) if (x >= z.x0 && x <= z.x1) return z; return null }

    companion object { const val EXAG = 1.5f; const val POWDER = 0; const val ICE = 1; const val WIND = 2 }
}

/**
 * The crew's descent (13.6), the site's game (site/games/descent, version 5): one button, as a skier jumps (hold to
 * crouch and load, let go to pop away from the snow; in the air hold to flip), on a run's real profile, picking up the
 * others on the way, over rocks, gullies and the snowcat, ahead of the avalanche, against the ghost of the best run.
 * Units are metres and seconds, y up. The others have no names in the app (Pini, 4.10.2026), only their coats.
 */
class DescentGame(val c: Course, val me: Int, private val out: Out, val avalancheOn: Boolean = true, val ghost: Ghost? = null,
                  private val rnd: Random = Random, private val still: Boolean = false) {
    interface Out {
        fun pop(say: Say) {}
        fun banner(big: Say?, text: Say, dog: Boolean = false) {}
        fun coach(id: String, say: Say, force: Boolean = false) {}
        fun tone(f: Double, d: Double, v: Float, wave: Int = SINE, end: Double = .5) {}
        fun buzz(kind: Int) {}
        fun combo(n: Int) {}
        fun finished(ok: Boolean) {}
    }
    class Stats { var flips = 0; var coins = 0; var overCat = false; var falls = 0; var maxFlip = 0; var pops = 0 }
    /** The best run's positions every tenth of a second: t, x, y, angle. */
    class Ghost(val t: Float, val who: Int, val r: FloatArray) {
        fun save(): String = buildString { append(t).append(';').append(who).append(';'); r.joinTo(this, ",") }
        companion object {
            fun load(s: String?): Ghost? = runCatching {
                val p = s!!.split(';'); Ghost(p[0].toFloat(), p[1].toInt(), if (p[2].isEmpty()) FloatArray(0) else p[2].split(',').map { it.toFloat() }.toFloatArray())
            }.getOrNull()
        }
    }
    enum class State { COUNT, RUN, FIN, END }
    class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val r: Float, val col: Int, val conf: Boolean)
    class Pos(val x: Float, val y: Float, val ang: Float, val air: Boolean, val crouch: Float)
    class Rider(val coat: Int, val from: Float)

    var state = State.COUNT; private set
    var t = 0f; private set
    var countT = 3f; private set
    val stats = Stats()
    var score = 0; private set
    var combo = 1; private set
    var flips = 0; private set
    val crew = ArrayList<Rider>()
    val bits = ArrayList<Bit>()
    val tracks = ArrayList<FloatArray>().apply { add(floatArrayOf(3f, 3f)) }
    private val hist = ArrayList<Pos>()
    private val rec = ArrayList<Float>()
    var shake = 0f; private set
    var slow = 1f; private set
    var zoomKick = 0f; internal set
    var avaX = -110f; private set
    private var avaV = 8f
    var dogOn = false; private set
    private var dogDone = false
    var dogT = 0f; private set
    var windT = 0f; private set
    private var finT = 0f

    // you
    var x = 3f; var y = 0f; var s = 0f; var vx = 0f; var vy = 0f; var air = false; var ang = 0f
    var airRot = 0f; var airT = 0f; var crash = 0f; var crouch = 0f; var inv = 0f; var squash = 0f
    var loading = false; var loadT = 0f; var flipArm = false; var holding = false; var tucking = false
    init { y = c.ground(3f); ang = c.slope(3f) }

    val coinsTotal get() = c.coins.size
    val got get() = crew.size

    // ---------- the one button ----------
    fun press() {
        if (state != State.RUN) return
        holding = true
        if (air) { flipArm = true; return } // a fresh press in the air starts a flip
        loading = crash <= 0; loadT = 0f
    }
    /** Letting go is the jump: the longer the crouch (up to a third of a second), the higher, and highest right at the lip. */
    fun release() {
        val was = holding; holding = false; flipArm = false
        if (!was || !loading) return
        loading = false
        if (state != State.RUN || air || crash > 0) return
        val lip = c.kickers.firstOrNull { it.x - x > -.6f && it.x - x < 3.2f }; val k = min(1f, loadT / .32f)
        var pop = 5.4f + 3.8f * k
        if (lip != null) { pop *= 1.35f; out.pop(Say(R.string.game_descent_pop_perfect)); stats.pops++; score += 40 * combo; out.tone(520.0, .12, .3f, TRI, 1.6) }
        val a = c.slope(x)
        takeOff(s * cos(a) + pop * -sin(a) * .55f, s * sin(a) + pop * cos(a)); squash = 1f
        if (!still) zoomKick = .06f + .08f * k
        repeat(if (lip != null) 28 else 10 + (10 * k).roundToInt()) {
            val d = if (rnd.nextFloat() < .5f) -1f else 1f
            bits += Bit(x + d * .4f, y + .1f, d * (2 + rnd.nextFloat() * 4) + vx * .3f, 1.5f + rnd.nextFloat() * 3.5f, .45f + rnd.nextFloat() * .4f, .14f + rnd.nextFloat() * .2f, WHITE, false)
        }
        out.tone(if (lip != null) 330.0 else 200.0 + 60 * k, .09, .22f, SQUARE, 1.9); out.buzz(TICK)
    }
    private fun takeOff(tvx: Float, tvy: Float) {
        air = true; vx = tvx; vy = tvy; airRot = 0f; airT = 0f; loading = false; flipArm = false
        tracks.lastOrNull()?.let { it[1] = x }
    }

    private fun spray(px: Float, py: Float, n: Int, spd: Float, col: Int = WHITE, up: Float = 3f) {
        repeat(n) { bits += Bit(px, py, -spd * (.3f + rnd.nextFloat() * .6f), 1 + rnd.nextFloat() * up, .5f + rnd.nextFloat() * .5f, .12f + rnd.nextFloat() * .2f, col, false) }
    }
    private fun burst(px: Float, py: Float, n: Int) {
        repeat(n) { i -> val a = rnd.nextFloat() * PI.toFloat(); val v = 4 + rnd.nextFloat() * 7
            bits += Bit(px, py, cos(a) * v, sin(a) * v + 2, 1 + rnd.nextFloat() * .8f, .18f, COATS[i % COATS.size].color.toInt(), true) }
    }
    private fun loseFriend(): Boolean { val one = crew.removeLastOrNull() ?: return false; c.spots.first { it.coat == one.coat }.lost = true; return true }
    private fun setCombo(n: Int) { combo = min(6, n); out.combo(combo) }

    private fun crashNow(msg: Int?) {
        crash = 1f; inv = 2f; air = false; y = c.ground(x); s *= .4f; shake = if (still) 0f else 14f
        out.buzz(CRASH); out.tone(70.0, .35, .7f); spray(x, y + .5f, 40, 6f)
        stats.falls++; setCombo(1)
        out.pop(Say(if (loseFriend()) R.string.app_descent_left_behind else msg ?: R.string.game_descent_pop_ouch))
    }
    private fun fellIn(gx0: Float, gx1: Float) {
        tracks.lastOrNull()?.let { it[1] = min(it[1], gx0) }
        x = gx1 + 1.5f; y = c.ground(x); air = false; s = 4f; crash = 1.1f; inv = 2.2f; ang = c.slope(x); shake = if (still) 0f else 18f
        out.buzz(CRASH); out.tone(55.0, .5, .8f)
        spray(x, y + .3f, 50, 5f, WHITE, 5f); stats.falls++; setCombo(1); tracks += floatArrayOf(x, x)
        out.pop(Say(if (loseFriend()) R.string.app_descent_gully_swallowed else R.string.game_descent_pop_fell_gully))
    }
    private fun land() {
        val a = c.slope(x); val wall = c.ground(x) - y
        val g = c.inGap(x)
        if (g != null || wall > 1.4f) {
            val q = g ?: c.gaps.firstOrNull { x >= it.x1 - .5f && x < it.x1 + 4 }
            if (q != null) fellIn(q.x0, q.x1) else fellIn(x - 5, x)
            return
        }
        val rel = atan2(sin(ang - a), cos(ang - a))
        val n = floor((abs(airRot) + .9f) / (2 * PI.toFloat())).toInt()
        air = false; y = c.ground(x); tracks += floatArrayOf(x, x)
        if (abs(rel) > .75f) { crashNow(null); return }
        val along = vx * cos(a) + vy * sin(a)
        s = min(VMAX, max(4f, along)); ang = a; squash = -min(1f, airT * .9f); if (!still && airT > .8f) shake = max(shake, 3 + airT * 2)
        val clean = abs(rel) < .14f
        if (n > 0) {
            flips += n; stats.flips += n; stats.maxFlip = max(stats.maxFlip, n)
            val mult = (1 + crew.size) * combo; score += n * 100 * mult; s = min(VMAX + 3, s + 3 * n); slow = if (still) 1f else .35f; out.buzz(CLICK)
            out.pop(Say(R.plurals.game_descent_pop_flips, listOf(n), count = n, tail = "!" + (if (mult > 1) " ×$mult" else "")))
            out.tone(140.0, .2, .5f); spray(x, y, 26, s * .6f); setCombo(combo + 1)
        } else {
            spray(x, y, 12, s * .4f); out.tone(100.0, .12, .25f)
            if (airT > .5f) { score += 20 * combo; if (clean) { s += 1.5f; score += 15; out.pop(Say(R.string.game_descent_pop_clean_landing)); setCombo(combo + 1) } }
        }
        if (holding) { loading = true; loadT = 0f; flipArm = false }
    }

    // ---------- a step ----------
    fun step(dtIn: Float) {
        var dt = min(.05f, dtIn)
        if (state == State.COUNT) {
            val before = ceil(countT); countT -= dt
            if (countT <= -.4f) { state = State.RUN; s = 7f; out.tone(660.0, .25, .3f, TRI, 1.0) }
            else if (ceil(countT) != before) out.tone(440.0, .12, .25f, TRI, 1.0)
        }
        if (state == State.RUN || state == State.FIN) { dt *= slow; slow = min(1f, slow + dt * 1.8f); update(dt) }
    }

    private fun update(dt: Float) {
        t += dt
        tucking = state == State.RUN && holding && (if (air) flipArm else crash <= 0); if (loading) loadT += dt
        val a = c.slope(x); val z = c.zoneAt(x)
        inv = max(0f, inv - dt)
        if (state == State.FIN) { // glide to a stop at the station and celebrate
            finT += dt; s = max(0f, s - 9 * dt); x += s * cos(a) * dt
            if (air) { vy -= G * dt; y += vy * dt; x += vx * dt * .3f; if (y <= c.ground(x)) air = false }
            if (!air) y = c.ground(x); ang += (a - ang) * min(1f, dt * 10)
            if (rnd.nextFloat() < dt * 6) burst(x + 2 + rnd.nextFloat() * 6, c.ground(x) + 6, 8)
            if (finT > 1.6f) { finish(true); return }
        } else if (crash > 0) {
            crash -= dt; s = max(0f, s - 6 * dt); x += s * cos(a) * dt; y = c.ground(x); ang += dt * 9; if (crash <= 0) { ang = a; s = max(s, 4f) }
        } else if (!air) {
            crouch += ((if (tucking) 1f else 0f) - crouch) * min(1f, dt * 8)
            var drag = if (tucking) .0022f else .0034f; var mu = .035f; var push = 0f
            if (z?.kind == Course.POWDER) { drag *= 4.5f; mu = .09f }
            if (z?.kind == Course.ICE) { drag *= .8f; mu = .008f }
            if (z?.kind == Course.WIND) push = if (tucking) .8f else 4.2f
            s += (-G * sin(a) - drag * s * s - mu * G * cos(a) - push) * dt
            s = min(VMAX, max(s, 3.2f)) // skating on the flats; a racer's top speed
            val nx = x + s * cos(a) * dt; val ty = y + s * sin(a) * dt
            // the snow bends away faster than gravity can hold you: airborne off the lip
            val bend = (a - c.slope(x + 2.5f)) / 2.5f
            if (s > 7 && bend * s * s > G * cos(a) * 1.05f) { takeOff(s * cos(a), s * sin(a)); x = nx; y = ty }
            else { x = nx; y = c.ground(nx); tracks.lastOrNull()?.let { it[1] = x }; c.inGap(x)?.let { fellIn(it.x0, it.x1) } }
            ang += (a - ang) * min(1f, dt * 14)
            val sp = if (z?.kind == Course.POWDER) 4 else 1
            if (rnd.nextFloat() < s * dt * 2.5f * sp) spray(x - .6f, y + .1f, sp + (if (s > 14) 1 else 0), s * .5f, WHITE, if (sp > 1) 5f else 3f)
        } else {
            airT += dt
            val v = hypot(vx, vy); val d = 1 - .0034f * v * dt; vx *= d; vy *= d // the same air drag as on the snow
            if (z?.kind == Course.WIND) vx = max(2f, vx - 3 * dt)
            val gk = if (abs(vy) < 2.2f) .7f else if (vy < 0) 1.12f else 1f // a floaty top and a firmer fall
            vy -= G * gk * dt; x += vx * dt; y += vy * dt
            val spin = if (tucking) 7.6f else 0f; ang += spin * dt; airRot += spin * dt
            if (!tucking) { // let go and the skier settles toward the snow below (and finishes a nearly complete flip)
                val tgt = c.slope(x); val rel = atan2(sin(ang - tgt), cos(ang - tgt))
                val tau = 2 * PI.toFloat(); val m = airRot % tau
                val fwd = if (airRot > .5f && rel > 0) min(tau - (if (m == 0f) tau else m), 9f) else 0f
                val st = if (fwd > 0 && fwd < 1.6f) min(fwd, 4 * dt) else -sign(rel) * min(abs(rel), 2.6f * dt)
                ang += st; if (st > 0) airRot += st
            }
            crouch += ((if (tucking) 1f else 0f) - crouch) * min(1f, dt * 10)
            if (y <= c.ground(x)) land()
        }
        if (squash != 0f) { squash *= .0008f.pow(dt); if (abs(squash) < .02f) squash = 0f }
        val hgt = y - c.ground(x)
        if (state == State.RUN) {
            // rocks and the snowcat: jump them or fall
            for (r in c.rocks) if (!r.hit && abs(x - r.x) < .8f && hgt < r.h && inv <= 0 && crash <= 0) { r.hit = true; crashNow(R.string.game_descent_crash_rock) }
            for (k in c.cats) {
                if (abs(x - k.x) > 3) k.x -= k.v * dt * (if (c.inGap(k.x - 2.5f) != null) 0 else 1)
                if (!k.warned && k.x - x < 70 && k.x > x) { k.warned = true; out.banner(Say(R.string.game_descent_banner_snowcat), Say(R.string.game_descent_banner_snowcat_text)) }
                if (abs(x - k.x) < 2f) {
                    if (hgt < 2.1f && !k.over && inv <= 0 && crash <= 0 && !k.hit) { k.hit = true; crashNow(R.string.game_descent_crash_snowcat) }
                    else if (hgt >= 2.1f && !k.over) { k.over = true; stats.overCat = true; score += 300 * combo; out.pop(Say(R.string.game_descent_pop_over_snowcat)); out.tone(520.0, .2, .35f, TRI, 1.5); setCombo(combo + 1) }
                }
            }
            if (!air && crash <= 0 && c.kickers.any { it.x - x > 4 && it.x - x < 14 }) out.coach("load", Say(R.string.game_descent_coach_hold_now))
            if (!air && crash <= 0 && holding && c.kickers.any { it.x - x > 0 && it.x - x < 3.2f }) out.coach("jump", Say(R.string.game_descent_coach_release_line))
            if (air && airT > .25f && !holding) out.coach("flip", Say(R.string.game_descent_coach_air_flip))
            for (g in c.gaps) if (!g.warned && g.x0 - x < 60 && g.x0 > x) { g.warned = true; out.banner(Say(R.string.game_descent_banner_gully), Say(R.string.game_descent_banner_gully_text)) }
            for (zz in c.zones) if (!zz.warned && zz.x0 - x < 35 && zz.x1 > x) {
                zz.warned = true
                when (zz.kind) {
                    Course.WIND -> { out.banner(Say(R.string.game_descent_banner_wind), Say(R.string.game_descent_banner_wind_text)); out.coach("wind", Say(R.string.game_descent_coach_wind), true) }
                    Course.POWDER -> out.banner(Say(R.string.game_descent_powder), Say(R.string.game_descent_banner_powder_text))
                    else -> out.banner(Say(R.string.game_descent_ice), Say(R.string.game_descent_banner_ice_text))
                }
            }
            // khachapuri
            for (k in c.coins) {
                if (k.got) continue
                val cy = c.baseGround(k.x) + k.dy
                if (abs(x - k.x) < 1.3f && abs(y + .9f - cy) < 1.5f) {
                    k.got = true; stats.coins++; score += 10 * combo; out.tone(990.0 + stats.coins % 5 * 80, .07, .12f, TRI, 1.2)
                    bits += Bit(k.x, cy, 0f, 3f, .5f, .25f, GOLD, false)
                }
            }
            // the others: ski through one to pick them up
            for (sp in c.spots) {
                if (sp.got || sp.lost) continue
                if (abs(x - sp.x) < 4 && hgt < 12 && crash <= 0) {
                    sp.got = true; sp.joinT = .7f; crew += Rider(sp.coat, sp.x); score += 150
                    out.pop(Say(R.string.app_descent_joined)); setCombo(combo + 1); out.buzz(CLICK); out.tone(330.0, .1, .2f)
                }
            }
            // a Gudauri dog joins for a while near the bottom
            if (!dogOn && !dogDone && x > c.dogX) { dogOn = true; dogT = 0f; out.banner(null, Say(R.string.game_descent_banner_dog), dog = true) }
            if (x >= c.len - 8) {
                state = State.FIN; finT = 0f; holding = false; loading = false; flipArm = false; tucking = false
                out.pop(if (c.lift.isNotEmpty()) Say(R.string.game_descent_lift_name, listOf(c.lift)) else Say(R.string.game_descent_finish_pop))
                out.tone(520.0, .3, .4f, TRI, 2.0); burst(x + 3, y + 3, 60)
            }
        }
        if (dogOn) { dogT += dt; if (dogT > 14 || state == State.FIN) { dogOn = false; dogDone = true } }
        // the crew ride in your exact tracks, a fixed distance apart
        val lastH = hist.lastOrNull(); if (lastH == null || x - lastH.x > .15f || air != lastH.air) hist += Pos(x, y, ang, air, crouch)
        if (hist.size > 4000) hist.subList(0, 1000).clear()
        for (sp in c.spots) sp.joinT = max(0f, sp.joinT - dt)
        if (rec.isEmpty() || t - rec[rec.size - 4] >= .1f) { rec += r2(t); rec += r1(x); rec += r1(y); rec += r2(ang) }
        // the avalanche: fast on steep snow, slow on the flats; it never falls hopelessly far behind
        if (avalancheOn && state == State.RUN) {
            val asl = max(0f, -sin(c.slope(max(0f, avaX))))
            var tv = min(24f, 5 + t * .05f + asl * 55); if (x - avaX > 140) tv = max(tv, s + 3)
            avaV += (tv - avaV) * min(1f, dt * .8f); if (t > 2) avaX += avaV * dt
            if (avaX >= x - 1) { finish(false); return }
        }
        for (p in bits) { p.vy -= G * (if (p.conf) .25f else .35f) * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.life -= dt }
        bits.removeAll { it.life <= 0 }
        if (bits.size > 500) bits.subList(0, bits.size - 500).clear()
        shake = max(0f, shake - dt * 40); windT += dt
        zoomKick = max(0f, zoomKick - .006f)
    }
    private fun r1(v: Float) = (v * 10).roundToInt() / 10f
    private fun r2(v: Float) = (v * 100).roundToInt() / 100f

    /** The rider's own path at distance [px] along the run (where the crew ride). */
    fun histAt(px: Float): Pos {
        if (hist.isEmpty() || px <= hist[0].x) return Pos(px, c.ground(px), c.slope(px), false, 0f)
        var lo = 0; var hi = hist.size - 1
        while (hi - lo > 1) { val m = (lo + hi) ushr 1; if (hist[m].x < px) lo = m else hi = m }
        return hist[lo]
    }
    /** Where the ghost is at [tt]: index into its record (t, x, y, angle by fours), or -1. */
    fun ghostAt(tt: Float): Int {
        val g = ghost ?: return -1; if (g.r.isEmpty()) return -1
        var i = 0; while (i + 4 < g.r.size && g.r[i + 4] < tt) i += 4
        return i
    }

    // ---------- the end ----------
    var ok = false; private set
    var goals = BooleanArray(3); private set
    var stars = 0; private set
    /** The run as a ghost for next time (when it is a new best). */
    fun record() = Ghost(t, me, rec.toFloatArray())
    fun finish(reached: Boolean) {
        if (state == State.END) return
        state = State.END; holding = false; loading = false; flipArm = false; tucking = false; ok = reached
        score += if (reached) got * 200 + max(0, (600 - t * 3).roundToInt()) else 0
        goals = booleanArrayOf(reached, reached && got == 5, reached && (MISSIONS[c.key]?.ok?.invoke(stats) ?: false)); stars = goals.count { it }
        out.finished(reached)
    }
    /** For the emulator run: straight to the bottom of the run, as if skied. */
    internal fun toBottom() { if (state != State.RUN) { state = State.RUN; s = 7f }; x = c.len - 9; y = c.ground(x); air = false; crash = 0f }

    companion object {
        const val G = 9.81f; const val VMAX = 30f; const val SPACING = 4.2f
        const val SINE = 0; const val TRI = 1; const val SQUARE = 2
        const val TICK = 0; const val CLICK = 1; const val CRASH = 2
        const val WHITE = 0xFFFFFFFF.toInt(); val GOLD = 0xFFF4B942.toInt()
    }
}

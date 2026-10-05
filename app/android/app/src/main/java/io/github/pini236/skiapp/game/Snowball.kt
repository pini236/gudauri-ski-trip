package io.github.pini236.skiapp.game

import io.github.pini236.skiapp.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The coats of the six characters (the site's FR), the same colours as in the other games. In the app they have no
 * names (Pini, 4.10.2026: no names in the games in the app), only the coat and the hat; [name] is the colour, for a
 * screen reader.
 */
class Coat(val color: Long, val name: Int)

val COATS = listOf(
    Coat(0xFFD1342B, R.string.common_color_red), Coat(0xFF1F5FC4, R.string.common_color_blue), Coat(0xFFF4B942, R.string.app_color_yellow),
    Coat(0xFF1B8A4C, R.string.common_color_green), Coat(0xFF7B4FC4, R.string.app_color_purple), Coat(0xFFF07A2E, R.string.app_color_orange),
)

/**
 * One rung of the ladder (the site's RUNGS): more characters behind the far wall, shorter peeks, stronger wind and
 * better aim. [goal] is the third star's words, [ok] whether it was met.
 */
class Rung(val arena: Int, val n: Int, val hp: Int, val windA: Float, val windB: Float, val err: Float,
           val upA: Float, val upB: Float, val waitA: Float, val waitB: Float, val goal: Int, val ok: (SnowballFight.Stats) -> Boolean) {
    /** The strongest wind on this rung (the ladder's "wind up to"). */
    val windMax: Int get() = max(abs(windA), abs(windB)).roundToInt()
}

val RUNGS = listOf(
    Rung(R.string.game_snowball_arena_hotel_yard, 1, 3, 0f, 1f, .9f, 1.8f, 2.4f, 1.6f, 2.6f, R.string.game_snowball_goal_win_untouched) { it.taken == 0 },
    Rung(R.string.game_snowball_arena_lift, 1, 3, -2f, 2f, .6f, 1.4f, 1.9f, 1.3f, 2.2f, R.string.game_snowball_goal_hat) { it.hats >= 1 },
    Rung(R.string.game_snowball_arena_deep_snow, 2, 2, -2f, 2f, .55f, 1.3f, 1.8f, 1.6f, 2.8f, R.string.game_snowball_goal_accuracy) { it.thrown > 0 && it.hits.toFloat() / it.thrown >= .5f },
    Rung(R.string.game_snowball_arena_ridge, 2, 2, -4f, 4f, .42f, 1.1f, 1.6f, 1.3f, 2.4f, R.string.game_snowball_goal_two_hats) { it.hats >= 2 },
    Rung(R.string.game_snowball_arena_summit, 3, 2, -5f, 5f, .36f, 1.0f, 1.4f, 1.5f, 2.8f, R.string.game_snowball_goal_win_untouched) { it.taken == 0 },
)

/** Who stands behind the far wall on rung [i] when you wear coat [me] (the site's foesOf). */
fun foesOf(i: Int, me: Int): List<Int> {
    val o = COATS.indices.filter { it != me }
    return List(RUNGS[i].n) { j -> o[(i + j * 2) % o.size] }
}

/**
 * The snowball fight (13.6), the site's game (design/games/snowball, version 2): seen from behind your own wall, in
 * metres, x to the side, y up and z away from you; your wall at z 1.4, theirs at 10 and the characters behind it at
 * 10.6. A finger down stands you up to aim (a ring shows where the ball lands without wind), letting go throws, and
 * with no finger you are down behind the wall. The characters peek, rise, throw and hide; balls wear their wall down
 * and knock hats off. The screen ([w], [h], [f], [y0], in pixels, [dp] pixels to the site's CSS pixel) is set by the
 * view; [rnd] makes the tests repeatable.
 */
class SnowballFight(val rungIndex: Int, val me: Int, private val out: Out, private val rnd: Random = Random, private val still: Boolean = false) {
    interface Out {
        fun hp() {}
        fun pop(say: Say) {}
        fun noise(d: Double, v: Float, f: Double, type: Noise = Noise.LOW) {}
        fun tone(f: Double, d: Double, v: Float, e: Double) {}
        fun buzz(strong: Boolean) {}
        fun finished(r: Result) {}
    }
    enum class Noise { LOW, HIGH, BAND }
    class Stats { var thrown = 0; var hits = 0; var hats = 0; var taken = 0 }
    class Result(val won: Boolean, val goals: BooleanArray, val stars: Int, val stats: Stats)
    enum class State { PLAY, DONE, END }

    class Foe(val coat: Int, var x: Float, val home: Float, var hp: Int, var timer: Float) {
        var up = 0f; var want = 0f; var phase = HIDE; var hat = true; var hit = 0f; var wob = 0f; var throwT = 0f; var goal = Float.NaN
    }
    class Ball(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, val vz: Float, val mine: Boolean) {
        var dead = false
        /** The last 8 places, oldest first (x, y, z). */
        val trail = FloatArray(24); var tn = 0
        fun push() { if (tn == 8) { System.arraycopy(trail, 3, trail, 0, 21); tn = 7 }; trail[tn * 3] = x; trail[tn * 3 + 1] = y; trail[tn * 3 + 2] = z; tn++ }
    }
    class Bit(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, val vz: Float, var life: Float, val r: Float)
    class Hat(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float, var r: Float, var vr: Float, val coat: Int)
    /** Snow on your face, on the screen (pixels): it slides down and fades. */
    class Splat(val x: Float, val y: Float, val r: Float, var life: Float, val max: Float, val seed: Float)
    class Aim(var x: Float, var y: Float)

    val rung = RUNGS[rungIndex]
    var w = 390f; var h = 844f; var dp = 1f
    /** Focal length and the horizon's height, from the screen (the site's resize). */
    var f = 600f; var y0 = 300f
    fun place(width: Float, height: Float, density: Float) { w = width; h = height; dp = density; f = min(w * 1.75f, h * 1.15f); y0 = h * .42f }

    var state = State.PLAY; private set
    var t = 0f; private set
    val stats = Stats()
    val foes: List<Foe>
    val balls = ArrayList<Ball>(); val bits = ArrayList<Bit>(); val hats = ArrayList<Hat>(); val splats = ArrayList<Splat>()
    /** The far wall's top every 25 cm from x -6 to 6: it wears down where it is hit. */
    val farWall = FloatArray(49) { FWALL + .06f * sin(it * 1.7f) }
    var wind = 0f; internal set
    var aim: Aim? = null
    var reload = 0f; private set
    var myHp = 3; private set
    var camH = .98f; internal set
    var camX = 0f; private set
    var shake = 0f; private set
    var won = false; private set
    private var endT = 0f
    private var wonPop = -1f

    init {
        val xs = when (rung.n) { 1 -> floatArrayOf(0f); 2 -> floatArrayOf(-1.5f, 1.5f); else -> floatArrayOf(-2f, 0f, 2f) }
        foes = foesOf(rungIndex, me).mapIndexed { i, c -> Foe(c, xs[i], xs[i], rung.hp, r(1.2f, 2.4f) + i * .9f) }
        wind = (r(rung.windA, rung.windB) * 2).roundToInt() / 2f
    }

    private fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    fun base(o: Foe) = FWALL - 1.95f + o.up * .95f // feet: hidden below the wall top when crouched, standing when up
    fun farWallTop(x: Float): Float = farWall[((x + 6) / .25f).roundToInt().coerceIn(0, farWall.size - 1)]

    // ---------- aiming: a finger anywhere, the ring above it; letting go throws when you are up ----------
    /** The ring's point on the plane of the far characters, as seen when standing (the site's ringTarget). */
    fun ringX(a: Aim) = (a.x - w / 2) / (f / ZO)
    fun ringY(a: Aim) = 1.75f - ((a.y - OFF * dp) - y0) / (f / ZO)
    fun down(x: Float, y: Float) { if (state == State.PLAY) aim = Aim(x, y) }
    fun move(x: Float, y: Float) { aim?.let { it.x = x; it.y = y } }
    fun cancel() { aim = null }
    fun up() {
        val a = aim ?: return; aim = null
        if (state != State.PLAY) return
        if (reload > 0 || camH < 1.5f) return // not up yet: you just duck back down
        throwAt(ringX(a), ringY(a))
    }
    fun throwAt(tx: Float, ty: Float) {
        val x0 = .32f; val yy = camH - .3f; val z0 = HAND_Z; val tt = FLIGHT
        balls += Ball(x0, yy, z0, (tx - x0) / tt, (ty - yy + .5f * G * tt * tt) / tt, (ZO - z0) / tt, true)
        reload = .45f; stats.thrown++
        out.noise(.1, .18f, 2600.0, Noise.HIGH); out.tone(200.0, .08, .12f, 1.6)
    }

    // ---------- the characters behind the far wall ----------
    private fun foeStep(o: Foe, dt: Float) {
        o.hit = max(0f, o.hit - dt); o.wob *= exp(-6 * dt); o.throwT = max(0f, o.throwT - dt)
        if (o.hp <= 0) { o.want = 0f; o.up += (0 - o.up) * min(1f, dt * 10); return }
        o.timer -= dt
        if (o.phase == HIDE && o.timer <= 0) { // up somewhere along the wall (sometimes just a peek)
            if (rnd.nextFloat() < .25f) { o.phase = PEEK; o.timer = r(.35f, .6f); o.want = .45f }
            else { o.phase = AIM; o.timer = r(rung.upA, rung.upB) * .6f; o.want = 1f }
        } else if (o.phase == PEEK && o.timer <= 0) { o.phase = HIDE; o.want = 0f; o.timer = r(.5f, 1.1f) }
        else if (o.phase == AIM && o.timer <= 0) { throwFoe(o); o.phase = AFTER; o.timer = r(rung.upA, rung.upB) * .4f; o.throwT = .25f }
        else if (o.phase == AFTER && o.timer <= 0) { o.phase = HIDE; o.want = 0f; o.timer = r(rung.waitA, rung.waitB); if (rnd.nextFloat() < .5f) o.goal = o.home + r(-.8f, .8f) }
        if (o.phase == HIDE && !o.goal.isNaN()) o.x += (o.goal - o.x) * min(1f, dt * 2.5f)
        o.up += (o.want - o.up) * min(1f, dt * 9)
    }
    private fun throwFoe(o: Foe) {
        val x0 = o.x - .3f; val yy = 1.5f; val z0 = ZO - .4f; val tt = r(.85f, 1.1f)
        // at your head when standing, with an error that shrinks up the ladder; better players lean into the wind
        val tx = r(-1f, 1f) * rung.err; val ty = 1.55f + r(-1f, 1f) * rung.err * .6f; val tz = .3f; val k = 1 - rung.err * .5f
        balls += Ball(x0, yy, z0, (tx - x0) / tt - .5f * wind * k * tt, (ty - yy + .5f * G * tt * tt) / tt, (tz - z0) / tt, false)
        out.noise(.08, .1f, 2000.0, Noise.HIGH)
    }

    // ---------- a step ----------
    fun update(dt: Float) {
        if (state == State.END) return
        t += dt
        reload = max(0f, reload - dt)
        val want = if (aim != null && state == State.PLAY) 1.75f else .98f // ducked, you still see their heads over your wall
        camH += (want - camH) * min(1f, dt * 11)
        camX += ((aim?.let { (it.x - w / 2) / w * .6f } ?: 0f) - camX) * min(1f, dt * 4) // lean a little toward the aim
        for (o in foes) foeStep(o, dt)
        for (b in balls) {
            if (b.dead) continue
            val pz = b.z
            b.vx += wind * .5f * dt; b.vy -= G * dt; b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt; b.push()
            if (b.mine) {
                if (pz < ZF && b.z >= ZF && b.y < farWallTop(b.x) && b.y > 0) { b.dead = true; wallHit(b.x, b.y); continue }
                if (pz < ZO && b.z >= ZO) for (o in foes) {
                    if (o.hp <= 0) continue
                    val bs = base(o); val head = bs + 1.62f; val wt = farWallTop(o.x)
                    if (abs(b.x - o.x) < .25f && abs(b.y - head) < .25f && head > wt) { b.dead = true; hitFoe(o, b, true); break }
                    if (abs(b.x - o.x) < .33f && b.y < bs + 1.32f && b.y > max(wt, bs + .4f)) { b.dead = true; hitFoe(o, b, false); break }
                }
                if (!b.dead && b.y < 0) { b.dead = true; splatAt(b.x, 0f, b.z, 8); out.noise(.1, .15f, 600.0); if (b.z > ZO - 2) out.pop(Say(R.string.game_snowball_pop_miss)) }
                if (b.z > ZO + 6) b.dead = true
            } else {
                if (pz > ZW && b.z <= ZW && b.y < WALL) { b.dead = true; splatAt(b.x, b.y, ZW, 16); out.noise(.15, .35f, 900.0); shake = if (still) 0f else 4f; continue }
                if (pz > .3f && b.z <= .3f) {
                    b.dead = true
                    val exposed = camH > 1.3f; val dx = abs(b.x - camX); val dy = b.y - camH
                    if (exposed && dx < .42f && dy > -.95f && dy < .3f) meHit(b)
                    else if (dx < 1.2f) { out.noise(.2, .25f, 3000.0, Noise.BAND); out.pop(Say(R.string.game_snowball_pop_over_you)) }
                }
                if (b.y < 0) { b.dead = true; splatAt(b.x, 0f, b.z, 8) }
            }
        }
        balls.removeAll { it.dead }
        for (p in bits) { p.vy -= G * .7f * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt; p.life -= dt }
        bits.removeAll { it.life <= 0 }
        for (o in hats) { o.vy -= G * dt; o.x += o.vx * dt; o.y += o.vy * dt; o.z += o.vz * dt; o.r += o.vr * dt; if (o.y < 0) { o.y = 0f; o.vx = 0f; o.vy = 0f; o.vz = 0f; o.vr = 0f } }
        for (s in splats) s.life -= dt
        splats.removeAll { it.life <= 0 }
        shake = max(0f, shake - dt * 30)
        if (wonPop >= 0 && t >= wonPop) { wonPop = -1f; out.pop(Say(R.string.game_snowball_won)) }
        if (state == State.DONE) { endT += dt; if (endT > 1.6f) finish() }
    }

    private fun splatAt(x: Float, y: Float, z: Float, n: Int) {
        repeat(n) { val a = r(0f, PI.toFloat()); val v = r(.6f, 2.6f); bits += Bit(x, y + .05f, z, cos(a) * v, sin(a) * v, r(-.6f, .6f), r(.35f, .8f), r(.04f, .09f)) }
    }
    private fun wallHit(x: Float, y: Float) {
        splatAt(x, y, ZF, 12); out.noise(.12, .22f, 800.0)
        val i = ((x + 6) / .25f).roundToInt()
        for (k in -2..2) { val j = i + k; if (j in farWall.indices) farWall[j] = max(.55f, farWall[j] - .09f * (1 - abs(k) / 3f)) }
    }
    private fun hitFoe(o: Foe, b: Ball, head: Boolean) {
        o.hp--; o.hit = .5f; o.wob = 1f; stats.hits++
        splatAt(b.x, b.y, ZO, 22); out.buzz(head); out.noise(.2, .4f, 1200.0); out.tone(if (head) 520.0 else 380.0, .16, .3f, 1.5)
        if (head && o.hat) {
            o.hat = false; stats.hats++
            hats += Hat(o.x, base(o) + 1.8f, ZO, b.vx * .15f, 3.5f, 2f, 0f, r(-9f, 9f), o.coat)
            out.pop(Say(R.string.game_snowball_pop_hat_off))
        } else out.pop(Say(if (head) R.string.game_snowball_pop_head else R.string.game_snowball_pop_hit))
        if (o.hp <= 0) { out.pop(Say(R.string.game_snowball_pop_out)); o.phase = OUT }
        out.hp()
        if (foes.all { it.hp <= 0 }) { state = State.DONE; won = true; endT = 0f; wonPop = t + .7f }
    }
    private fun meHit(b: Ball) {
        myHp--; stats.taken++; shake = if (still) 0f else 16f
        out.buzz(true); out.noise(.25, .5f, 1400.0); out.tone(140.0, .25, .35f, .6)
        // snow stuck on the screen, sliding down
        repeat(9) { splats += Splat(w / 2 + (b.x - camX) * w * .5f + r(-w * .25f, w * .25f), r(h * .2f, h * .65f), r(28f, 70f) * dp, r(1.6f, 2.4f), 2.4f, rnd.nextFloat() * 9) }
        out.pop(Say(R.string.game_snowball_pop_got_hit)); out.hp()
        if (myHp <= 0) { state = State.DONE; won = false; endT = 0f }
    }

    /** The end of the fight: the three stars (to win, without being hit, the rung's goal). Once. */
    fun finish() {
        if (state == State.END) return
        state = State.END; aim = null
        val g = booleanArrayOf(won, won && stats.taken == 0, won && rung.ok(stats))
        out.finished(Result(won, g, g.count { it }, stats))
    }
    /** For the emulator run: every character out, as if hit. */
    internal fun knockOut() { for (o in foes) o.hp = 0; out.hp(); state = State.DONE; won = true; endT = 0f; wonPop = t + .7f }

    companion object {
        const val G = 9.81f; const val ZW = 1.4f; const val ZF = 10f; const val ZO = 10.6f; const val WALL = 1f; const val FWALL = 1.05f
        const val HAND_Z = .5f; const val FLIGHT = .85f
        /** The ring sits this far above the finger (CSS pixels), so the finger never hides it. */
        const val OFF = 70f
        const val HIDE = 0; const val PEEK = 1; const val AIM = 2; const val AFTER = 3; const val OUT = 4
    }
}

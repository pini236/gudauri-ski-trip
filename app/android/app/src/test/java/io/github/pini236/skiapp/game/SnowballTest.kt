package io.github.pini236.skiapp.game

import io.github.pini236.skiapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The snowball fight (game/Snowball.kt), the site's rules: throws, the far wall, ducking, the stars. */
class SnowballTest {
    private class Ear : SnowballFight.Out {
        val pops = ArrayList<Int>(); var done: SnowballFight.Result? = null
        override fun pop(say: Say) { pops += say.id }
        override fun finished(r: SnowballFight.Result) { done = r }
    }
    private val dt = 1 / 60f
    private fun SnowballFight.go(seconds: Float) { var t = 0f; while (t < seconds) { update(dt); t += dt } }
    /** A fight on [rung] with no wind, every character standing and aiming, and never throwing. */
    private fun standing(rung: Int = 0, ear: Ear = Ear()) = SnowballFight(rung, 5, ear, Random(1)).apply {
        place(390f, 844f, 1f); wind = 0f
        for (o in foes) { o.up = 1f; o.want = 1f; o.phase = SnowballFight.AIM; o.timer = 999f }
    }

    @Test fun theFoesAreTheOtherCoatsAndMoreUpTheLadder() {
        assertEquals(listOf(1, 1, 2, 2, 3), RUNGS.indices.map { foesOf(it, 5).size })
        for (me in COATS.indices) for (i in RUNGS.indices) { val f = foesOf(i, me); assertFalse(me in f); assertEquals(f.size, f.toSet().size) }
    }

    @Test fun aBallAtTheHeadKnocksTheHatOffAndAtTheBodyIsAHit() {
        val ear = Ear(); val g = standing(0, ear)
        val o = g.foes[0]; val head = g.base(o) + 1.62f
        g.camH = 1.75f; g.throwAt(o.x, head); g.go(1.2f)
        assertEquals(1, g.stats.hits); assertEquals(1, g.stats.hats); assertFalse(o.hat); assertEquals(2, o.hp)
        assertTrue(R.string.game_snowball_pop_hat_off in ear.pops)
        g.camH = 1.75f; g.throwAt(o.x, g.base(o) + 1.17f); g.go(1.2f)
        assertEquals(2, g.stats.hits); assertEquals(1, g.stats.hats)
        assertTrue(R.string.game_snowball_pop_hit in ear.pops)
        assertEquals(2, g.stats.thrown)
    }

    @Test fun aLowBallHitsTheirWallAndWearsItDown() {
        val g = standing()
        val before = g.farWallTop(0f)
        g.camH = 1.75f; g.throwAt(0f, .3f); g.go(1.2f)
        assertEquals(0, g.stats.hits)
        assertTrue("the wall wore down", g.farWallTop(0f) < before - .05f)
    }

    @Test fun theirBallHitsYouOnlyWhenYouAreUp() {
        val ear = Ear(); val g = standing(0, ear)
        // up and aiming: a ball at your face
        g.down(195f, 400f); g.go(.5f)
        g.balls += SnowballFight.Ball(g.camX, g.camH, .6f, 0f, 0f, -5f, false); g.go(.3f)
        assertEquals(2, g.myHp); assertEquals(1, g.stats.taken); assertTrue(g.splats.isNotEmpty())
        assertTrue(R.string.game_snowball_pop_got_hit in ear.pops)
        // down behind the wall: it goes over you
        g.cancel(); g.go(.6f)
        g.balls += SnowballFight.Ball(g.camX, 1.75f, .6f, 0f, 0f, -5f, false); g.go(.3f)
        assertEquals(2, g.myHp)
        assertTrue(R.string.game_snowball_pop_over_you in ear.pops)
    }

    @Test fun lettingGoBeforeYouAreUpIsJustDucking() {
        val g = standing()
        g.down(195f, 300f); g.up()
        assertEquals(0, g.stats.thrown)
        g.down(195f, 300f); g.go(.5f); g.up()
        assertEquals(1, g.stats.thrown)
    }

    @Test fun threeHitsWinWithAllStarsAndThreeTakenLose() {
        val ear = Ear(); val g = standing(0, ear)
        val o = g.foes[0]
        repeat(3) { g.camH = 1.75f; g.throwAt(o.x, g.base(o) + 1.62f); g.go(1.2f) }
        assertEquals(0, o.hp)
        assertTrue(R.string.app_snowball_out in ear.pops)
        g.go(2f)
        val r = ear.done!!
        assertTrue(r.won); assertEquals(3, r.stars); assertTrue(r.goals.all { it })
        assertTrue(R.string.game_snowball_won in ear.pops)

        val ear2 = Ear(); val q = standing(1, ear2)
        q.down(195f, 400f); q.go(.5f)
        repeat(3) { q.balls += SnowballFight.Ball(q.camX, q.camH, .6f, 0f, 0f, -5f, false); q.go(.3f) }
        q.go(2f)
        val l = ear2.done!!
        assertFalse(l.won); assertEquals(0, l.stars); assertEquals(3, l.stats.taken)
        q.finish(); assertEquals(l, ear2.done) // once
    }

    @Test fun theRungGoals() {
        val s = SnowballFight.Stats()
        assertTrue(RUNGS[0].ok(s)); s.taken = 1; assertFalse(RUNGS[0].ok(s))
        assertFalse(RUNGS[1].ok(s)); s.hats = 1; assertTrue(RUNGS[1].ok(s)); assertFalse(RUNGS[3].ok(s))
        s.thrown = 4; s.hits = 2; assertTrue(RUNGS[2].ok(s)); s.hits = 1; assertFalse(RUNGS[2].ok(s))
        assertEquals(listOf(1, 2, 2, 4, 5), RUNGS.map { it.windMax })
    }
}

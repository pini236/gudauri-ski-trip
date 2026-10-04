package io.github.pini236.skiapp.game

import io.github.pini236.skiapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** The ski school's lessons on the slope (game/School.kt), the site's rules: the drills, the coach and the stars. */
class SchoolTest {
    private class Ear : SchoolRun.Out {
        val pops = ArrayList<Int>(); val coach = ArrayList<Pair<Int, Int>>(); var done: SchoolRun.Result? = null
        override fun pop(say: Say) { pops += say.id }
        override fun coach(say: Say, tone: Int) { coach += say.id to tone }
        override fun finished(r: SchoolRun.Result) { done = r }
    }
    private val dt = 1 / 60f
    private fun SchoolRun.go(seconds: Float, until: SchoolRun.() -> Boolean = { false }, each: SchoolRun.() -> Unit = {}) {
        var t = 0f; while (t < seconds && running && !until()) { each(); update(dt); t += dt }
    }

    @Test fun sevenLessonsInTheSiteOrderWithThreeGoalsEach() {
        assertEquals(7, LESSONS.size)
        assertEquals(listOf(SchoolMode.WEDGE, SchoolMode.SKIS, SchoolMode.STEER, SchoolMode.RHYTHM, SchoolMode.STEER, SchoolMode.LEAN, SchoolMode.STEER), LESSONS.map { it.mode })
        assertTrue(LESSONS.all { it.goals.size == 3 })
        assertEquals(6, LESSONS[1].gates.size); assertEquals(7, LESSONS[6].gates.size)
    }

    @Test fun inTheWedgeABrakeBeforeTheLineIsAStopAndRunningPastItIsNot() {
        val ear = Ear(); val r = SchoolRun(LESSONS[0], ear)
        // the finger low on the screen: a wide wedge, and the skier stops long before the first line (35 m)
        r.finger = SchoolRun.Finger(r.w / 2, r.h * .82f)
        r.go(4f)
        assertTrue("stopped", r.s.v < .2f)
        assertTrue("no stop counted far from the line", r.stops.isEmpty())
        // no wedge down to the line, and the wide one when it is about as far as the stop takes
        r.go(40f, until = { stops.isNotEmpty() }) { finger!!.y = if (SchoolRun.STOPS[0] - s.y < s.v * s.v / 5 + 1f) h * .82f else h * .3f }
        assertEquals(1, r.stops.size)
        val d = r.stops[0]!!
        assertTrue("stopped before the line, $d m", d in 0f..6f)
        assertTrue(R.string.game_school_pop_stop_perfect in ear.pops || R.string.game_school_pop_stopped in ear.pops)
        // straight on with no wedge: the second line goes by
        r.finger = SchoolRun.Finger(r.w / 2, r.h * .3f)
        r.go(30f) { if (stops.size >= 2) finger = null }
        assertEquals(null, r.stops[1])
        assertTrue(R.string.game_school_pop_line_passed in ear.pops)
    }

    @Test fun theOuterSkiTurnsAndTheWrongOneIsCounted() {
        val ear = Ear(); val r = SchoolRun(LESSONS[1], ear)
        r.go(.5f)
        // the first gate is on the left (x -5): the right ski, the outer one, turns left
        r.ski(right = false, down = true); r.ski(right = false, down = false)
        assertEquals(1, r.wrong)
        assertEquals(R.string.game_school_coach_wrong_right to SchoolRun.BAD, ear.coach.last())
        r.ski(right = true, down = true)
        assertEquals(R.string.game_school_coach_correct_left to SchoolRun.GOOD, ear.coach.last())
        val x0 = r.s.x; r.go(2f); assertTrue("turned left", r.s.x < x0 - .5f)
        r.ski(right = true, down = false)
        // steer for each gate with the right ski and pass all six
        r.go(200f) { val g = gates.firstOrNull { it.done == null }; if (g != null) { val left = g.x < s.x; if (skiR != left) { ski(true, left); ski(false, !left) } } }
        val res = ear.done!!
        assertEquals(6, r.passed)
        assertTrue(res.goals[0] && res.goals[1]); assertEquals(R.string.game_school_res_skis, res.line.id)
    }

    @Test fun aTapOnTheBeatIsPerfectAndOffItIsEarlyOrLate() {
        val ear = Ear(); val r = SchoolRun(LESSONS[3], ear)
        val b = LESSONS[3].beat
        r.go(.6f + b - .01f) // just before the beat at .6 + one beat
        r.tap(); assertEquals(1, r.perfect)
        r.go(b * .3f); r.tap() // a third of a beat late
        assertEquals(R.string.game_school_beat_late to SchoolRun.BAD, ear.coach.last())
        assertEquals(0, r.streak)
        r.go(b * .45f); r.tap() // the next beat comes about a quarter beat later: early
        assertEquals(R.string.game_school_beat_early to SchoolRun.BAD, ear.coach.last())
        // ten taps on the beat: the first goal
        var next = .6f + b * (kotlin.math.floor((r.t - .6f) / b) + 1)
        repeat(10) { r.go(next - r.t); r.tap(); next += b }
        assertTrue("${r.good}", r.good >= 10); assertTrue(r.best >= 8)
    }

    @Test fun straightDownIsCaughtInTheTrapAndTurningAcrossSlowsDown() {
        val ear = Ear(); val r = SchoolRun(LESSONS[2], ear)
        r.go(25f) // the demo, then no finger: straight on
        assertTrue(r.traps.isNotEmpty()); assertFalse("caught", r.traps[0])
        assertTrue(R.string.game_school_coach_straight in ear.coach.map { it.first })
        // a second skier who turns across the slope before each trap
        val ear2 = Ear(); val q = SchoolRun(LESSONS[2], ear2)
        // straight-ish while slow, across the slope when fast, the other way each time
        var side = 1f; var across = false
        q.go(120f) {
            if (t < lv.demo) return@go
            if (!across && kmh > 15) across = true
            if (across && kmh < 9) { across = false; side = -side }
            val a = if (across) side * 1.6f else side * .5f
            finger = SchoolRun.Finger(sx(s.x) + sin(a) * w * .4f, sy(s.y) - kotlin.math.cos(a) * w * .4f)
        }
        assertTrue("passed at least one trap: ${q.traps}", q.traps.any { it })
        assertTrue(R.string.game_school_coach_across to SchoolRun.GOOD in ear2.coach)
    }

    @Test fun slowEdgeChangesDrawCleanArcsAndASnapSkids() {
        val ear = Ear(); val r = SchoolRun(LESSONS[5], ear)
        r.go(40f) { val k = sin(t * 1.4f); finger = SchoolRun.Finger(w / 2 + k * w * .3f, h / 2) }
        assertTrue("arcs ${r.arcs}", r.arcs >= 3)
        assertTrue(R.string.game_school_pop_clean_arc in ear.pops)
        val snap = SchoolRun(LESSONS[5], Ear())
        snap.go(6f) { finger = SchoolRun.Finger(if ((t * 4).toInt() % 2 == 0) 0f else w, h / 2) }
        assertTrue("skidding ${snap.skidT}", snap.skidT > 0)
    }

    @Test fun theEndCountsTheStarsAndSaysTheLine() {
        val ear = Ear(); val r = SchoolRun(LESSONS[0], ear)
        r.finish()
        val res = ear.done!!
        assertEquals(0, res.stars)
        assertEquals(R.string.game_school_res_wedge, res.line.id)
        assertEquals("-", res.line.args[1]) // no stop: no average distance
        r.finish(); assertEquals(res, ear.done) // once
        assertFalse(r.running)
    }
}

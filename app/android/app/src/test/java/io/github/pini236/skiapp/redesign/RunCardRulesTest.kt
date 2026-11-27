package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.RunPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCardRulesTest {
    private fun p(d: Float, a: Float) = RunPoint(0f, 0f, d, 2000f - d / 4f, a)

    @Test fun slopeClassesFollowTheThresholds() {
        assertEquals(0, RunCardRules.slopeClass(15f)); assertEquals(1, RunCardRules.slopeClass(15.1f))
        assertEquals(1, RunCardRules.slopeClass(25f)); assertEquals(2, RunCardRules.slopeClass(30f)); assertEquals(3, RunCardRules.slopeClass(30.5f))
    }

    @Test fun segmentsShareEndPointsAndCoverTheRun() {
        val pts = listOf(p(0f, 5f), p(10f, 8f), p(20f, 20f), p(30f, 21f), p(40f, 35f))
        val s = RunCardRules.segments(pts)
        assertEquals(listOf(0, 1, 3), s.map { it.cls })
        assertEquals(0, s.first().from); assertEquals(4, s.last().to)
        for (i in 1 until s.size) assertEquals(s[i - 1].to, s[i].from)
    }

    @Test fun oneClassIsOneSegment() {
        val s = RunCardRules.segments(listOf(p(0f, 5f), p(10f, 6f), p(20f, 7f)))
        assertEquals(1, s.size); assertEquals(0, s[0].from); assertEquals(2, s[0].to)
    }

    @Test fun steepestIsTheHighestSlope() {
        assertEquals(2, RunCardRules.steepestAt(listOf(p(0f, 5f), p(10f, 8f), p(20f, 22f), p(30f, 9f))))
        assertEquals(-1, RunCardRules.steepestAt(emptyList()))
    }

    @Test fun ticksEvery500mNotAtTheEnds() {
        assertEquals(listOf(500f, 1000f), RunCardRules.ticks(1500f))
        assertEquals(listOf(500f), RunCardRules.ticks(1200f))
        assertTrue(RunCardRules.ticks(400f).isEmpty())
    }

    @Test fun footRowAndFrames() {
        assertEquals(3, RunCardRules.frames(7)); assertEquals(1, RunCardRules.frames(1)); assertEquals(0, RunCardRules.frames(0))
        assertTrue(RunCardRules.footRow(2, false)); assertTrue(RunCardRules.footRow(0, true)); assertFalse(RunCardRules.footRow(0, false))
    }
}

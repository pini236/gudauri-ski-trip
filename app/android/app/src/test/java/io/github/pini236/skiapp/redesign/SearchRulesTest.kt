package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The search before the first reading (handoff 2026-11-25-redesign-search-pulse): the rings, and the counter after it. */
class SearchRulesTest {
    @Test fun aRingGrowsAndFades() {
        val (s0, a0) = SearchRules.ringAt(0)
        assertEquals(.22f, s0, 1e-3f); assertEquals(.95f, a0, 1e-3f)
        // 70 percent of the loop: 0.35
        assertEquals(.35f, SearchRules.ringAt((SearchRules.PERIOD_MS * .7).toLong()).second, 1e-2f)
        // near the end: almost full size, almost gone
        val (s1, a1) = SearchRules.ringAt(SearchRules.PERIOD_MS - 1)
        assertTrue(s1 > .99f); assertTrue(a1 < .01f)
        // a loop: the same again after 2.4 s
        assertEquals(SearchRules.ringAt(300), SearchRules.ringAt(300 + SearchRules.PERIOD_MS))
    }

    @Test fun threeRingsAlwaysOnTheirWayOut() {
        // 0.8 s apart: at any moment three different sizes
        for (t in 0L until SearchRules.PERIOD_MS step 100) {
            val sizes = (0 until 3).map { SearchRules.ringAt(t + it * SearchRules.STAGGER_MS).first }.toSet()
            assertEquals(3, sizes.size)
        }
    }

    @Test fun theGlassBreathesAndTheAimGlows() {
        assertEquals(1f, SearchRules.breathe(0), 1e-4f)
        assertEquals(1.06f, SearchRules.breathe(SearchRules.PERIOD_MS / 2), 1e-4f)
        assertEquals(.55f, SearchRules.aimAlpha(0), 1e-3f)
        assertEquals(1f, SearchRules.aimAlpha(SearchRules.PERIOD_MS / 2), 1e-3f)
    }

    @Test fun theStillRingsAreTheBoardS() {
        assertEquals(listOf(.42f to .85f, .66f to .45f, .9f to .2f), SearchRules.STILL)
        assertEquals(30_000L, SearchRules.HINT_AFTER_MS)
    }

    @Test fun theCounterStartsAtTheFirstReading() {
        // before it: 0:00 (no time in the search); after it: from it, never from the tap
        assertEquals(0L, LiveNumbers.fromFix(1_000_000L, 0L))
        assertEquals(0L, LiveNumbers.fromFix(1_000_000L, 1_000_500L))
        assertEquals(14_000L, LiveNumbers.fromFix(1_014_000L, 1_000_000L))
        assertEquals("0:14", LiveNumbers.elapsed(LiveNumbers.fromFix(1_014_000L, 1_000_000L)))
    }
}

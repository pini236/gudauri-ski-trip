package io.github.pini236.skiapp.redesign

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redesign is the app (no switch, docs/ARCHITECTURE.md, 24.11.2026); when what it cannot work without does not load,
 * every way in gives today's screen back. The composable ways in are the same first line; these are the ones a plain
 * test can reach.
 */
class RedesignOffTest {
    private val was = Redesign.on

    @After fun back() { Redesign.on = was }

    @Test fun offTheNotificationKeepsTodaysWords() {
        Redesign.on = { false }
        assertEquals("", Redesign.noticeKey(null, null))
    }

    @Test fun offTheEmulatorsCommandsAreNotTaken() {
        Redesign.on = { false }
        val before = RedesignQa.seq
        Redesign.qa("open")
        assertEquals(before, RedesignQa.seq)
        assertFalse(Redesign.covering)
    }

    @Test fun theLiveModelNeedsOpenGlEs3() {
        assertFalse(Redesign.liveOk(0x20000))
        assertTrue(Redesign.liveOk(0x30000))
        assertTrue(Redesign.liveOk(0x30002))
    }

    // last: the fall is for the rest of the run (Redesign.on is reset, the fall itself is not undone)
    @Test fun aFailedLoadFallsBackToTodaysScreens() {
        Redesign.on = was
        Redesign.fail("picture test.webp")
        assertFalse(Redesign.on())
        assertEquals("", Redesign.noticeKey(null, null))
    }
}

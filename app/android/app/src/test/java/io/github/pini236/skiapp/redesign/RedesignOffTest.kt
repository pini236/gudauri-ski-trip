package io.github.pini236.skiapp.redesign

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redesign is the app (no switch, and no old design shown, docs/ARCHITECTURE.md, 24.11.2026). What did not load keeps
 * the new design (Redesign.fail); [Redesign.on] is turned off here only to test the way the old code stays inert.
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

    @Test fun aFailedLoadKeepsTheNewDesign() {
        Redesign.on = was
        Redesign.fail("picture test.webp")
        assertTrue(Redesign.on())
        assertEquals("picture test.webp", Redesign.failed)
    }
}

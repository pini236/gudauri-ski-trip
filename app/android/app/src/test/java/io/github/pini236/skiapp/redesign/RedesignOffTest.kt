package io.github.pini236.skiapp.redesign

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The store build without its switch (docs/ARCHITECTURE.md, 24.11.2026): every way into the redesign gives today's
 * screen back before anything of the redesign is touched. The composable ways in are the same first line; these are the
 * ones a plain test can reach.
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
}

package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.nav.Route as R
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

    @Test fun siteLinksAndRemindersOpenOnTheMountain() {
        assertEquals("pick:Tatra 2", Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Map("Tatra 2")))
        assertEquals("front", Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Map()))
        assertEquals("meet:station:gd-top", Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Meet("gd-top", "13:00", "2027-01-12")))
        assertEquals("meet", Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Meet()))
        assertEquals("people:join:ABC123", Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Join("ABC123")))
        assertEquals("people:group:g1", Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Group("g1")))
        assertEquals("people:gate", Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Group()))
        assertEquals(null, Redesign.linkOf(io.github.pini236.skiapp.nav.Route.Home))
        // nothing of the site's links leads to an old screen (Pini, 24.11.2026); only the games and the day stay routes
        assertEquals("people:join:ABC123", Redesign.linkOf(R.Reclaim("ABC123")))
        assertEquals("people:gate", Redesign.linkOf(R.JoinCode))
        assertEquals("people:me", Redesign.linkOf(R.Account))
        assertEquals("people:about", Redesign.linkOf(R.About))
        assertEquals("people:licenses", Redesign.linkOf(R.Licenses))
        assertEquals("people:license:heebo", Redesign.linkOf(R.License("heebo")))
        assertEquals("people:new", Redesign.linkOf(R.GroupNew))
        assertEquals("people:invite:g1", Redesign.linkOf(R.GroupInvite("g1")))
        assertEquals("people:tripfor:g1:u2", Redesign.linkOf(R.TripFor("g1", "u2")))
        assertEquals("trip", Redesign.linkOf(R.Trip))
        assertEquals("skipass", Redesign.linkOf(R.Skipass))
        for (r in listOf(R.Games, R.Game("merge"), R.Day, R.Days, R.DayOf("d1"))) assertEquals(null, Redesign.linkOf(r))
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** Part 6 (U01): our sheet before the phone's question, at most twice, once a meetup. */
class EdgesTest {
    @Test fun offersANewMeetupOnly() {
        assertEquals("a", NotifOffer.next(listOf("a", "b"), emptySet(), 0))
        assertEquals("b", NotifOffer.next(listOf("a", "b"), setOf("a"), 1))
        assertNull(NotifOffer.next(listOf("a"), setOf("a"), 1))
        assertNull(NotifOffer.next(emptyList(), emptySet(), 0))
    }

    @Test fun neverMoreThanTwice() {
        assertNull(NotifOffer.next(listOf("a", "b", "c"), setOf("a", "b"), 2))
        assertNull(NotifOffer.next(listOf("c"), emptySet(), NotifOffer.MAX))
    }

    @Test fun thePillSaysNoSignalOnlyInTheSeasonWithNoLiveReportAndNoNetwork() {
        assertTrue(Online.pillOffline(false, live = false, inSeason = true))
        assertFalse(Online.pillOffline(null, live = false, inSeason = true))
        assertFalse(Online.pillOffline(true, live = false, inSeason = true))
        assertFalse(Online.pillOffline(false, live = true, inSeason = true))
        assertFalse(Online.pillOffline(false, live = false, inSeason = false))
    }
}

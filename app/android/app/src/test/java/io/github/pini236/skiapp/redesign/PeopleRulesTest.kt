package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.group.GroupTab
import io.github.pini236.skiapp.group.Me
import io.github.pini236.skiapp.group.Meetup
import io.github.pini236.skiapp.group.Member
import io.github.pini236.skiapp.group.Role
import io.github.pini236.skiapp.group.Score
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The completion round, part 3 (design/handoff/2026-11-23-redesign-people): the rules behind the people screens. */
class PeopleRulesTest {
    private val d = { day: Int -> LocalDate.of(2027, 1, day) }

    @Test fun aPersonKeepsOneOfSevenColoursEverywhere() {
        val a = PeopleRules.colorIndex("0b6c1d0e-4f1a-4bde-9c6e-2a5d7f3b1c90")
        assertEquals(a, PeopleRules.colorIndex("0b6c1d0e-4f1a-4bde-9c6e-2a5d7f3b1c90"))
        assertTrue(a in 0..6)
        // the sum of the code units: "a" (97) is 6, "b" (98) is 0
        assertEquals(6, PeopleRules.colorIndex("a"))
        assertEquals(0, PeopleRules.colorIndex("b"))
        assertEquals(7, PeopleRules.COLORS.size)
    }

    @Test fun theCircleShowsTheFirstLetter() {
        assertEquals("נ", PeopleRules.initial("נועה"))
        assertEquals("D", PeopleRules.initial(" dana"))
        assertEquals("?", PeopleRules.initial(""))
        assertEquals("?", PeopleRules.initial(null))
    }

    @Test fun meOpensTheAccountTheGuestOrTheWayIn() {
        assertEquals(PeopleRules.MeKind.ACCOUNT, PeopleRules.meKind(Me("u", true, "פיני"), 0))
        assertEquals(PeopleRules.MeKind.GUEST, PeopleRules.meKind(Me("u", false, null), 1))
        // a guest identity with no group left: nothing to keep, the way in
        assertEquals(PeopleRules.MeKind.NOBODY, PeopleRules.meKind(Me("u", false, null), 0))
        assertEquals(PeopleRules.MeKind.NOBODY, PeopleRules.meKind(null, 0))
    }

    @Test fun theGroupOpensOnFlightsThenMeetupsThenScores() {
        assertEquals(GroupTab.FLIGHTS, PeopleRules.openingTab(d(10), d(15), d(9)))
        assertEquals(GroupTab.MEETUPS, PeopleRules.openingTab(d(10), d(15), d(10)))
        assertEquals(GroupTab.MEETUPS, PeopleRules.openingTab(d(10), d(15), d(15)))
        assertEquals(GroupTab.SCORES, PeopleRules.openingTab(d(10), d(15), d(16)))
        assertEquals(GroupTab.MEETUPS, PeopleRules.openingTab(null, null, d(9)))
        assertEquals(254, PeopleRules.sheetTop(GroupTab.FLIGHTS))
        assertEquals(254, PeopleRules.sheetTop(GroupTab.SCORES))
    }

    @Test fun aMeetupMoreThanAnHourPastLeavesTheList() {
        val now = Instant.parse("2027-01-12T10:00:00Z")
        val past = Meetup("a", "s", now.minusSeconds(3601), null, null)
        val justPast = Meetup("b", "s", now.minusSeconds(3600), null, null)
        val ahead = Meetup("c", "s", now.plusSeconds(60), null, null)
        assertEquals(listOf("b", "c"), PeopleRules.upcoming(listOf(ahead, past, justPast), now).map { it.id })
    }

    @Test fun aTieSharesThePlaceAndTheNextSkips() {
        val rows = listOf(3000, 2048, 2048, 900).mapIndexed { i, b -> Score("u$i", "n$i", b, false) }
        assertEquals(listOf(1, 2, 2, 4), PeopleRules.places(rows))
        assertEquals(emptyList<Int>(), PeopleRules.places(emptyList()))
    }

    @Test fun aPastedLinkFillsTheBoxes() {
        assertEquals("ABCDEF", PeopleRules.codeFrom("https://gudauri-ski-trip.vercel.app/join/abcdef"))
        assertEquals("ABCDEF", PeopleRules.codeFrom("abc-def"))
        // I and O are not in the alphabet
        assertEquals("ABCDE", PeopleRules.codeFrom("abcdeo"))
        val token = "A".repeat(40)
        assertEquals(token, PeopleRules.codeFrom("https://gudauri-ski-trip.vercel.app/j/$token"))
    }

    @Test fun oneCardPerFlightWithWhoIsOnIt() {
        val out = Leg(d(10), "GD 201", "TLV", "TBS", LocalTime.of(15, 30))
        val other = Leg(d(10), "GD 205", "TLV", "TBS", LocalTime.of(9, 0))
        val back = Leg(d(15), "GD 202", "TBS", "TLV")
        val ms = listOf(
            Member("a", "נועה", Role.ADMIN, me = true, trip = Trip(out, back)),
            Member("b", "דני", Role.MEMBER, me = false, trip = Trip(out.copy(flight = "gd-201"))),
            Member("c", "רון", Role.MEMBER, me = false, trip = Trip(other)),
            Member("e", "טל", Role.MEMBER, me = false),
        )
        val outs = PeopleRules.cards(ms, ret = false)
        // the earlier flight first; "GD 201" and "gd-201" are one flight
        assertEquals(listOf(1, 2), outs.map { it.members.size })
        assertTrue(outs[1].mine)
        assertEquals(1, PeopleRules.cards(ms, ret = true).size)
    }

    @Test fun sixCirclesAndTheRestAsANumber() {
        assertEquals(4 to 0, PeopleRules.circles(4))
        assertEquals(6 to 3, PeopleRules.circles(9))
    }
}

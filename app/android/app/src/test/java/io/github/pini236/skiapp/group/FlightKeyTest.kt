package io.github.pini236.skiapp.group

import io.github.pini236.skiapp.trip.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/**
 * X-4: the group's flight cards are made as on the site. The same four cases as the site's test
 * (tests/account.spec.ts, "X-4"): the outbound legs of two members, and whether they share one card.
 */
class FlightKeyTest {
    private val d = LocalDate.of(2027, 1, 10)
    private fun key(flight: String, from: String = "", to: String = "") = FlightKey.of(Leg(d, flight, from, to), false)

    @Test fun theSitesFourCases() {
        assertEquals("with airports and without: one card", key("897", "TLV", "TBS"), key("897"))
        assertEquals("a space or not: one card", key("IZ 897"), key("iz-897"))
        assertNotEquals("no number, other airports: two cards", key("", "TLV", "TBS"), key("", "TLV", "KUT"))
        assertNotEquals("a number and none: two cards", key("897", "TLV", "TBS"), key("", "TLV", "TBS"))
    }

    @Test fun theDirectionAndTheNumber() {
        assertEquals("IZ897", FlightKey.number(" iz - 897 "))
        // out and back on the same day and number are two cards
        assertEquals(2, setOf(FlightKey.of(Leg(d, "IZ897"), false), FlightKey.of(Leg(d, "IZ897"), true)).size)
        // airports typed as "TLV · Tel Aviv" and "tlv" are one
        assertEquals(FlightKey.of(Leg(d, "", "TLV · Tel Aviv", "TBS"), false), FlightKey.of(Leg(d, "", "tlv", "tbs"), false))
    }
}

package io.github.pini236.skiapp.trip

import io.github.pini236.skiapp.trip.PassEdits.withDates
import io.github.pini236.skiapp.trip.PassEdits.withLeg
import io.github.pini236.skiapp.trip.PassEdits.withPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Round 20 (decision 65): the pass is the form; one calendar, two taps. */
class PassEditsTest {
    private val d10 = LocalDate.of(2027, 1, 10)
    private val d15 = LocalDate.of(2027, 1, 15)

    @Test fun twoTaps() {
        // the first tap is the way out, the second the way back
        assertEquals(d10 to null, PassEdits.tap(null, null, d10))
        assertEquals(d10 to d15, PassEdits.tap(d10, null, d15))
        // a tap before the way out starts again from it; so does a third tap
        assertEquals(d10 to null, PassEdits.tap(d15, null, d10))
        assertEquals(d15 to null, PassEdits.tap(d10, d15, d15))
        // the same day twice: out and back that day (the full form allows it too)
        assertEquals(d10 to d10, PassEdits.tap(d10, null, d10))
    }

    @Test fun aNewTripFromTheCalendar() {
        val t = PassEdits.newTrip(d10, d15, "TLV · Tel Aviv", "TBS · Tbilisi")
        assertEquals("TLV", t.out.fromCode); assertEquals("TBS", t.out.toCode)
        assertEquals("TBS", t.ret!!.fromCode); assertEquals("TLV", t.ret!!.toCode)
        // no times yet: the ski days are the days between the flights, as the calendar shades them
        assertEquals(LocalDate.of(2027, 1, 11)..LocalDate.of(2027, 1, 14), t.skiDays())
        assertNull(PassEdits.newTrip(d10, null, "TLV", "TBS").ret)
    }

    @Test fun newDatesKeepTheFlights() {
        val t = Trip(Leg(d10, "6H 897", "TLV", "TBS", LocalTime.of(16, 0), LocalTime.of(20, 35)), null, ski = d10..d10)
        val n = t.withDates(LocalDate.of(2027, 1, 12), LocalDate.of(2027, 1, 17))
        assertEquals("6H 897", n.out.flight); assertEquals(LocalTime.of(20, 35), n.out.arrives)
        // a new return is the way out reversed; the ski days follow the flights again
        assertEquals("TBS", n.ret!!.from); assertEquals("TLV", n.ret!!.to)
        assertNull(n.ski)
        assertNull(n.withDates(d10, null).ret)
    }

    @Test fun placesAndLegs() {
        val t = PassEdits.newTrip(d10, d15, "TLV", "TBS").withPlaces("TLV", "KUT · Kutaisi")
        assertEquals("KUT", t.ret!!.fromCode)
        val f = t.withLeg(true) { it.copy(flight = "IZ 892") }
        assertEquals("IZ 892", f.ret!!.flight); assertEquals("", f.out.flight)
    }
}

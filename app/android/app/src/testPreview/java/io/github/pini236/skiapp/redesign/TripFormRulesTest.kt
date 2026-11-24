package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Part 5 (T09): the full trip form's rules, the same as today's form (trip/TripForm.kt). */
class TripFormRulesTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val tlv = "TLV · Tel Aviv"; private val tbs = "TBS · Tbilisi"
    private val d = { m: Int, day: Int -> LocalDate.of(if (m >= 11) 2026 else 2027, m, day) }

    @Test fun aNewFormStartsTelAvivToTbilisiAndNeedsOnlyTheDate() {
        val f = TripDraft.of(null, tlv, tbs)
        assertEquals(TripError.NEED_DATE, f.errors(today, null).out)
        val ok = f.withDates(d(1, 10), null)
        assertFalse(ok.errors(today, null).any)
        val t = ok.trip(today, null)!!
        assertEquals("TLV", t.out.fromCode); assertEquals("TBS", t.out.toCode); assertNull(t.ret)
    }

    @Test fun theWayBackIsTheWayOutReversedUnlessChanged() {
        val f = TripDraft.of(null, tlv, tbs).withDates(d(1, 10), d(1, 15))
        assertTrue(f.rAuto)
        val t = f.trip(today, null)!!
        assertEquals("TBS", t.ret!!.fromCode); assertEquals("TLV", t.ret!!.toCode)
        val changed = f.copy(rFrom = "KUT · Kutaisi")
        assertFalse(changed.rAuto)
        assertEquals("KUT", changed.trip(today, null)!!.ret!!.fromCode)
        // read back from the saved trip, a changed way back stays changed and a reversed one stays automatic
        assertFalse(TripDraft.of(changed.trip(today, null), tlv, tbs).rAuto)
        assertTrue(TripDraft.of(t, tlv, tbs).rAuto)
    }

    @Test fun theErrorsAreTheSitesKeysOnTheirCards() {
        val f = TripDraft.of(null, tlv, tbs)
        assertEquals(TripError.PAST_DATE, f.withDates(d(11, 1), null).errors(today, null).out)
        // a saved trip keeps its own past date
        assertNull(f.withDates(d(11, 1), null).errors(today, d(11, 1)).out)
        assertEquals(TripError.BAD_ORDER, f.copy(oDate = d(1, 15), rDate = d(1, 10)).errors(today, null).back)
        assertEquals(TripError.BAD_CODE, f.copy(oDate = d(1, 10), oTo = "Tbilisi").errors(today, null).out)
        assertEquals(TripError.BAD_CODE, f.copy(oDate = d(1, 10), rDate = d(1, 15), rTo = "home").errors(today, null).back)
        assertEquals(TripError.SKI_OUTSIDE, f.copy(oDate = d(1, 10), rDate = d(1, 15), ski = d(1, 9)..d(1, 12)).errors(today, null).ski)
        assertEquals(TripError.SKI_OUTSIDE, f.copy(oDate = d(1, 10), rDate = d(1, 15), ski = d(1, 13)..d(1, 16)).errors(today, null).ski)
        assertNotNull(f.copy(oDate = d(1, 10), rDate = d(1, 15), ski = d(1, 11)..d(1, 14)).trip(today, null))
    }

    @Test fun newDatesDropSkiDaysOutsideAndAReturnThatWentAway() {
        val f = TripDraft.of(Trip(Leg(d(1, 10), "IZ 897", tlv, tbs, LocalTime.of(16, 0), LocalTime.of(20, 35)),
            Leg(d(1, 15), "IZ 892", tbs, tlv, LocalTime.of(1, 35)), d(1, 11)..d(1, 14)), tlv, tbs)
        assertEquals(d(1, 11)..d(1, 14), f.withDates(d(1, 10), d(1, 15)).ski)
        assertNull(f.withDates(d(1, 12), d(1, 15)).ski)
        val noRet = f.withDates(d(1, 10), null)
        assertNull(noRet.rDate); assertEquals("", noRet.rFlight); assertNull(noRet.rDep)
        // the flights' ski days, as today: landing 20:35 and back at 01:35, 11 to 14
        assertEquals(d(1, 11)..d(1, 14), f.copy(ski = null).trip(today, null)!!.skiDays())
    }
}

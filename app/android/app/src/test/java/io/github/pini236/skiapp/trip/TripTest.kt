package io.github.pini236.skiapp.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TripTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private val d = { day: Int -> LocalDate.of(2027, 1, day) }

    /** The group's trip (docs/STATUS.md): 10.1 16:00 → 20:35, back 15.1 01:35 → 02:15. */
    private val group = Trip(
        Leg(d(10), "6H 897", "TLV · תל אביב", "TBS · טביליסי", t(16, 0), t(20, 35)),
        Leg(d(15), "6H 892", "TBS · טביליסי", "TLV · תל אביב", t(1, 35), t(2, 15)),
    )

    @Test fun theGroupSkisElevenToFourteen() {
        assertEquals(d(11)..d(14), group.skiDays())
        assertEquals(4, group.skiDayCount())
    }

    @Test fun skiDayEdges() {
        // landing early in the morning: that day counts; an evening flight home: the last day counts
        val early = Trip(Leg(d(10), departs = t(5, 0), arrives = t(8, 30)), Leg(d(15), departs = t(19, 0)))
        assertEquals(d(10)..d(15), early.skiDays())
        // an overnight flight lands the next day
        val night = Trip(Leg(d(10), departs = t(23, 0), arrives = t(3, 0)), Leg(d(15), departs = t(10, 0)))
        assertEquals(d(11)..d(14), night.skiDays())
        // no return yet, or no full day at all
        assertNull(Trip(Leg(d(10))).skiDays())
        assertNull(Trip(Leg(d(10), arrives = t(20, 0)), Leg(d(11), departs = t(6, 0))).skiDays())
    }

    @Test fun countdownRoundsUpAndStopsAtZero() {
        assertEquals(1, group.daysToFlight(LocalDateTime.of(2027, 1, 10, 15, 59)))
        assertEquals(1, group.daysToFlight(LocalDateTime.of(2027, 1, 9, 16, 0)))
        assertEquals(2, group.daysToFlight(LocalDateTime.of(2027, 1, 9, 15, 0)))
        assertEquals(0, group.daysToFlight(LocalDateTime.of(2027, 1, 10, 16, 0)))
        assertEquals(0, group.daysToFlight(LocalDateTime.of(2027, 1, 12, 9, 0)))
    }

    @Test fun codesAndCities() {
        assertEquals("TLV", group.out.fromCode); assertEquals("תל אביב", group.out.fromCity)
        assertEquals("TBS", Leg(d(1), to = "tbs").toCode)
        assertNull(Leg(d(1), to = "טביליסי").toCode); assertEquals("טביליסי", Leg(d(1), to = "טביליסי").toCity)
    }

    @Test fun savedAndReadBack() {
        assertEquals(group, Trip.fromJson(group.toJson().toString()))
        val one = Trip(Leg(d(10)))
        assertEquals(one, Trip.fromJson(one.toJson().toString()))
        assertNull(Trip.fromJson("{oops"))
        assertNull(Trip.fromJson("{\"v\":2,\"out\":{\"date\":\"2027-01-10\"}}"))
        assertNull(Trip.fromJson(null))
    }

    @Test fun readsDatesAndTimesAsTyped() {
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(d(10), TripText.date("10.1.2027", today))
        assertEquals(d(10), TripText.date("10/1/27", today))
        assertEquals(d(10), TripText.date(" 2027-01-10 ", today))
        assertEquals(d(10), TripText.date("10.1", today)) // the next 10 January
        assertEquals(LocalDate.of(2026, 12, 20), TripText.date("20.12", today))
        assertNull(TripText.date("", today))
        assertEquals(TripText.Bad, TripText.date("31.2.2027", today))
        assertEquals(TripText.Bad, TripText.date("tomorrow", today))
        assertEquals(t(16, 0), TripText.time("16:00"))
        assertEquals(t(1, 35), TripText.time("1.35"))
        assertEquals(t(16, 5), TripText.time("1605"))
        assertEquals(t(9, 0), TripText.time("9"))
        assertNull(TripText.time(" "))
        assertEquals(TripText.Bad, TripText.time("25:00"))
        assertEquals("10.1.2027", TripText.date(d(10)))
        assertEquals("01:35", TripText.time(t(1, 35)))
    }

    @Test fun aCityAfterTheCodeWithoutTheDot() {
        val l = Leg(d(10), from = "TLV · תל אביב", to = "TBS Tbilisi")
        assertEquals("TLV", l.fromCode); assertEquals("תל אביב", l.fromCity)
        assertEquals("TBS", l.toCode); assertEquals("Tbilisi", l.toCity)
        val plain = Leg(d(10), from = "Tel Aviv")
        assertNull(plain.fromCode); assertEquals("Tel Aviv", plain.fromCity)
        assertNull(Leg(d(10), from = "tbs Tbilisi").fromCode)
    }

    @Test fun skiDaysSetByHandWinAndSurviveSaving() {
        val mine = group.copy(ski = d(12)..d(13))
        assertEquals(d(12)..d(13), mine.skiDays())
        assertEquals(2, mine.skiDayCount())
        assertEquals(d(11)..d(14), mine.flightSkiDays())
        assertEquals(mine, Trip.fromJson(mine.toJson().toString()))
    }

    @Test fun readsATripSavedByTheParallelBuild() {
        val old = """{"v":1,"out":{"date":"2027-01-10","number":"GD 101","from":"TLV · Tel Aviv","to":"TBS · Tbilisi","departs":"16:00","arrives":"20:35"},
            "back":{"date":"2027-01-15","number":"GD 102","departs":"01:35","arrives":"02:15"}}"""
        val t = Trip.fromJson(old)!!
        assertEquals("GD 101", t.out.flight); assertEquals("GD 102", t.ret!!.flight)
        assertEquals(d(11)..d(14), t.skiDays())
    }
}

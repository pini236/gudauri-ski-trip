package io.github.pini236.skiapp.trip

import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class TripTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val crewLike = Trip(
        Flight(d(2027, 1, 10), "GD 101", "TLV · תל אביב", "TBS", LocalTime.of(16, 0), LocalTime.of(20, 35)),
        Flight(d(2027, 1, 15), "GD 102", "TBS", "TLV", LocalTime.of(1, 35), LocalTime.of(2, 15)),
    )

    @Test fun skiDaysLikeTheCrew() {
        // land in the evening of the 10th, fly back at 01:35 on the 15th: four full days, 11 to 14 (docs/STATUS.md)
        assertEquals(d(2027, 1, 11) to d(2027, 1, 14), crewLike.skiDays())
    }

    @Test fun skiDaysMorningLandingAndEveningReturn() {
        val t = Trip(Flight(d(2027, 2, 1), arrives = LocalTime.of(9, 30)), Flight(d(2027, 2, 5), departs = LocalTime.of(19, 0)))
        assertEquals(d(2027, 2, 1) to d(2027, 2, 5), t.skiDays())
    }

    @Test fun noReturnNoSkiDays() = assertNull(Trip(Flight(d(2027, 1, 10))).skiDays())

    @Test fun daysToRoundsUpAndStopsAtZero() {
        assertEquals(1, crewLike.daysTo(LocalDateTime.of(2027, 1, 10, 9, 0)))
        assertEquals(2, crewLike.daysTo(LocalDateTime.of(2027, 1, 8, 17, 0)))
        assertEquals(0, crewLike.daysTo(LocalDateTime.of(2027, 1, 10, 16, 0)))
        assertEquals(0, crewLike.daysTo(LocalDateTime.of(2027, 1, 12, 8, 0)))
    }

    @Test fun jsonRoundTrip() {
        assertEquals(crewLike, Trip.fromJson(crewLike.toJson()))
        val onlyDate = Trip(Flight(d(2027, 3, 3)))
        assertEquals(onlyDate, Trip.fromJson(onlyDate.toJson()))
    }

    @Test fun badJsonIsNullNotACrash() {
        assertNull(Trip.fromJson(null))
        assertNull(Trip.fromJson("{"))
        assertNull(Trip.fromJson("""{"out":{"date":"2027-13-40"}}"""))
    }

    @Test fun parseDatesAndTimes() {
        assertEquals(d(2027, 1, 10), Trip.parseDate("10.1.2027"))
        assertEquals(d(2027, 1, 10), Trip.parseDate(" 10/01/27 "))
        assertNull(Trip.parseDate("31.2.2027"))
        assertNull(Trip.parseDate("tomorrow"))
        assertEquals(LocalTime.of(1, 35), Trip.parseTime("01:35"))
        assertEquals(LocalTime.of(16, 0), Trip.parseTime("1600"))
        assertNull(Trip.parseTime("25:00"))
    }

    @Test fun airportCodeAndName() {
        assertEquals("TLV", airportCode("TLV · תל אביב"))
        assertEquals("TBS", airportCode("tbs"))
        assertEquals("", airportCode("תל אביב"))
        assertEquals("תל אביב", airportName("TLV · תל אביב"))
    }

    @Test fun tripIsARoute() {
        assertEquals(Route.Trip, Route.parse("trip"))
        assertEquals("trip", Route.Trip.path)
        assertNull(Route.parse("trip/x"))
    }

    @Test fun homeViewFollowsTheTimeInGudauri() {
        fun at(h: Int, m: Int = 0) = LocalDateTime.of(2027, 1, 12, h, m).toInstant(ZoneOffset.ofHours(4)).toEpochMilli()
        val noon = Sky.pano(at(12, 40))
        assertFalse(noon.dark)
        assertTrue(noon.from in setOf("morning", "noon") && noon.to in setOf("noon", "gold"))
        val night = Sky.pano(at(23))
        assertTrue(night.dark)
        assertEquals("night", night.from)
        assertEquals("13:35", Sky.clock(at(13, 35)))
        assertEquals("00:05", Sky.clock(at(0, 5)))
    }
}

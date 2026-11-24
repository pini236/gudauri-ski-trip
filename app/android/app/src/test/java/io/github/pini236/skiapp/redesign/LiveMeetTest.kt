package io.github.pini236.skiapp.redesign

import androidx.compose.ui.geometry.Offset
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.meet.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** The completion round, part 2 (Q04, Q05): the day and time a meeting point opens on, and the pins on the screen. */
class LiveMeetTest {
    private val jan = (11..14).map { LocalDate.of(2027, 1, it) }
    /** Gudauri's clock (UTC+4) at [d] [hm]. */
    private fun at(d: LocalDate, hm: String) = d.atTime(LocalTime.parse(hm)).toInstant(ZoneOffset.ofHours(4))

    @Test fun onASkiDayTheFirstTimeAtLeastAQuarterAfterNow() {
        assertEquals(jan[1] to LocalTime.of(12, 0), LiveMeetRules.default(jan, at(jan[1], "10:10")))
        // 11:50 is less than 15 minutes before 12:00: the next one
        assertEquals(jan[1] to LocalTime.of(13, 0), LiveMeetRules.default(jan, at(jan[1], "11:50")))
        assertEquals(jan[1] to LocalTime.of(9, 30), LiveMeetRules.default(jan, at(jan[1], "07:00")))
    }

    @Test fun lateInTheDayHalfPastNineTheNextSkiDay() {
        assertEquals(jan[2] to LocalTime.of(9, 30), LiveMeetRules.default(jan, at(jan[1], "16:20")))
        // the last ski day: the day after
        assertEquals(LocalDate.of(2027, 1, 15) to LocalTime.of(9, 30), LiveMeetRules.default(jan, at(jan[3], "18:00")))
    }

    @Test fun beforeTheTripTheFirstSkiDayAtHalfPastNine() {
        assertEquals(jan[0] to LocalTime.of(9, 30), LiveMeetRules.default(jan, at(LocalDate.of(2026, 12, 20), "14:00")))
    }

    private fun st(id: String) = Station(id, 0f, 0f, 2000, listOf(Station.End(Lift(id, "chair_lift", floatArrayOf(0f, 0f, 1f, 1f)), false)))

    @Test fun pinsCloserThanThirteenAreOne() {
        val pts = listOf(st("a") to Offset(100f, 100f), st("b") to Offset(110f, 100f), st("c") to Offset(200f, 100f))
        val c = LiveMeetRules.clusters(pts, 13f)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), c.map { g -> g.map { it.first.id } })
    }

    @Test fun aTapTakesTheNearestStationWithinReachOnly() {
        val pts = listOf(st("a") to Offset(100f, 100f), st("b") to Offset(110f, 100f))
        assertEquals("b", LiveMeetRules.nearest(pts, 108f, 104f, 44f)?.id)
        assertNull(LiveMeetRules.nearest(pts, 300f, 300f, 44f))
    }
}

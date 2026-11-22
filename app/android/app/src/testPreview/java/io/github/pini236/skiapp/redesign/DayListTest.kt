package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.dayrec.ui.DayModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Round 4, board L12: the trip's colours, one a day, and which recorded days are the trip. */
class DayListTest {
    private val day = intArrayOf(0x2F7BFF, 0x8A4FE0, 0xE0246E)

    @Test fun threeDaysAreTheBoardsThreeColours() {
        assertEquals(listOf(0x2F7BFF, 0x8A4FE0, 0xE0246E), tripRgb(3, day))
    }

    @Test fun oneDayIsBlueAndMoreAreSpreadBetweenTheSameThree() {
        assertEquals(listOf(0x2F7BFF), tripRgb(1, day))
        val four = tripRgb(4, day)
        assertEquals(0x2F7BFF, four.first()); assertEquals(0xE0246E, four.last())
        assertEquals(2, tripRgb(2, day).size); assertEquals(0xE0246E, tripRgb(2, day)[1])
    }

    private fun listed(date: String, cut: Boolean = false) = ListedDay(File("day-$date.jsonl"), date, LocalDate.parse(date),
        if (cut) null else DayModel.Summary(0, 0, 3, 2, 400.0, 3000.0, null, null, null, null), setOf("Tatra 2"), null, emptyList())

    @Test fun theTripIsTheDaysInItsSkiDaysAndACutDayIsNot() {
        val days = listOf(listed("2027-01-15"), listed("2027-01-13"), listed("2027-01-12", cut = true), listed("2027-01-11"), listed("2026-12-30"))
        val trip = LocalDate.parse("2027-01-11")..LocalDate.parse("2027-01-14")
        assertEquals(listOf("2027-01-13", "2027-01-11"), tripDays(days, trip).map { it.first.toString() })
        assertEquals(emptyList<Any>(), tripDays(days, null))
    }
}

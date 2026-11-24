package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Part 4 (S05): the weather tab's tags and day cards. */
class NowRulesTest {
    private val d = { day: Int -> LocalDate.of(2027, 1, day) }

    @Test fun theHighestRiskFirstAndTheReportWins() {
        val t = NowRules.tags(mapOf("Kudebi" to "medium", "Sadzele" to "high"), emptyMap())
        assertEquals(listOf("Sadzele", "Kudebi"), t.map { it.lift })
        assertEquals(NowRules.Why.ESTIMATE, NowRules.why(t))
        val r = NowRules.tags(mapOf("Kudebi" to "medium", "Sadzele" to "high"), mapOf("Sadzele" to false))
        assertEquals(listOf("Kudebi", "Sadzele"), r.map { it.lift })
        assertEquals(false, r[1].open)
        assertEquals(NowRules.Why.MIXED, NowRules.why(r))
        assertEquals(NowRules.Why.REPORT, NowRules.why(NowRules.tags(mapOf("Sadzele" to "high"), mapOf("Sadzele" to true))))
    }

    @Test fun moreThanARowKeepsOnlyHighAndMedium() {
        val t = NowRules.tags(mapOf("a" to "low", "b" to "high", "c" to "medium", "e" to "low"), emptyMap())
        assertEquals(listOf("b", "c"), t.map { it.lift })
        // a lift with no estimate and no report has no tag
        assertTrue(NowRules.tags(mapOf("a" to null), emptyMap()).isEmpty())
        assertNull(NowRules.why(emptyList()))
    }

    @Test fun theDaysShowOnceTheFirstIsInTheForecast() {
        val ski = (11..14).map(d)
        val fc = (1..10).map(d)
        assertNull(NowRules.days(ski, fc, d(1)))
        val fc2 = (5..12).map(d)
        val cards = NowRules.days(ski, fc2, d(5))!!
        assertEquals(4, cards.size)
        assertTrue(cards[1].inForecast)
        assertFalse(cards[2].inForecast)
        // the forecast reaches 7 days past its first: the 13th opens on the 6th
        assertEquals(d(6), cards[2].opensOn)
    }

    @Test fun duringTheTripTodayIsFirst() {
        val ski = (11..14).map(d)
        val cards = NowRules.days(ski, (12..20).map(d), d(12))!!
        assertEquals(d(12), cards.first().date)
        assertTrue(NowRules.during(ski, d(12)))
        assertFalse(NowRules.during(ski, d(15)))
        assertNull(NowRules.days(ski, (12..20).map(d), d(15)))
    }
}

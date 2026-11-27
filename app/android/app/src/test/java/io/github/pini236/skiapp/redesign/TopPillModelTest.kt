package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.redesign.TopPillModel.Cond
import io.github.pini236.skiapp.redesign.TopPillModel.Line
import io.github.pini236.skiapp.redesign.TopPillModel.LiftsKind
import io.github.pini236.skiapp.season.OpeningView
import io.github.pini236.skiapp.season.Season
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.status.Report
import io.github.pini236.skiapp.weather.Forecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** P.25: every state of the top pill (TP1 to TP7) from fixtures; the live report only starts in December (S-20). */
class TopPillModelTest {
    private val t0 = Instant.parse("2027-01-12T08:00:00Z")
    private val names = (1..14).map { "L$it" }

    private fun status(open: Int, wind: Int = 0, ageMin: Long = 5, total: Int = 14): LiftStatus {
        val ls = names.take(total).mapIndexed { i, n ->
            val o = i < open
            val reason = if (!o && i < open + wind) ",\"reason\":\"wind\"" else ""
            "\"$n\":{\"open\":$o$reason}"
        }.joinToString(",")
        val r = Report.parse("""{"updated":"$t0","lifts":{$ls}}""")
        return LiftStatus(names.take(total), r, t0.toEpochMilli() + ageMin * 60_000)
    }
    private fun opening(kind: Season.Kind, days: Int, progress: Float = .4f, moved: Boolean = false) =
        OpeningView(kind, days, LocalDate.of(2026, 12, 20), progress, moved)

    @Test fun tp1_inSeason() {
        val l = TopPillModel.lifts(status(11), null, null, 11)
        assertEquals(LiftsKind.LIVE, l.kind); assertEquals(11, l.open); assertEquals(14, l.total); assertEquals(0, l.windStopped)
    }
    @Test fun tp2_windOnlyFromReportReason() {
        val l = TopPillModel.lifts(status(9, wind = 2), null, null, 11)
        assertEquals(LiftsKind.LIVE, l.kind); assertEquals(9, l.open); assertEquals(2, l.windStopped)
        // closed without a reason is not wind, however windy the forecast
        assertEquals(0, TopPillModel.lifts(status(9), null, null, 11).windStopped)
    }
    @Test fun tp3_allOpen() = assertEquals(LiftsKind.ALL_OPEN, TopPillModel.lifts(status(14), null, null, 10).kind)
    @Test fun tp4_closedEveningSaysTomorrow() {
        val e = TopPillModel.lifts(status(0), null, null, 18)
        assertEquals(LiftsKind.CLOSED, e.kind); assertEquals("09:00", e.tomorrowAt)
        assertNull(TopPillModel.lifts(status(0), null, null, 11).tomorrowAt)
    }
    @Test fun tp5_noDataWhenStaleOrMissing() {
        assertEquals(LiftsKind.NO_DATA, TopPillModel.lifts(status(11, ageMin = 31), null, null, 11).kind)
        assertEquals(LiftsKind.NO_DATA, TopPillModel.lifts(null, null, null, 11).kind)
        assertEquals(LiftsKind.NO_DATA, TopPillModel.lifts(LiftStatus(names, null, 0), null, null, 11).kind)
    }
    @Test fun staleReportBeatsCountOnlyWhenFresh() {
        // a fresh report wins over the count; a stale one falls back to the count
        assertEquals(LiftsKind.LIVE, TopPillModel.lifts(status(5), opening(Season.Kind.COUNTDOWN, 20), null, 11).kind)
        assertEquals(LiftsKind.COUNT, TopPillModel.lifts(status(5, ageMin = 40), opening(Season.Kind.COUNTDOWN, 20), null, 11).kind)
    }
    @Test fun tp6_preSeason() {
        val c = TopPillModel.lifts(null, opening(Season.Kind.COUNTDOWN, 23, .55f), null, 11)
        assertEquals(LiftsKind.COUNT, c.kind); assertEquals(23, c.days); assertEquals(.55f, c.progress, 0f)
        assertEquals(LiftsKind.TODAY, TopPillModel.lifts(null, opening(Season.Kind.OPENING_DAY, 0, 1f), null, 11).kind)
        assertEquals(LiftsKind.LATE, TopPillModel.lifts(null, null, LocalDate.of(2026, 12, 20), 11).kind)
        assertTrue(TopPillModel.lifts(null, opening(Season.Kind.COUNTDOWN, 9, moved = true), null, 11).postponed)
    }

    private fun now(temp: Int = -7, gust: Int? = 20, snow24: Int? = 0) = Forecast.Now(temp, 10, gust, 0, snow24)
    private val noon = LocalDateTime.of(2027, 1, 12, 12, 0)
    private val morning = LocalDateTime.of(2027, 1, 12, 8, 0)
    private val evening = LocalDateTime.of(2027, 1, 12, 20, 0)

    @Test fun weatherSunAndWords() {
        val w = TopPillModel.weather(now(), 0, noon, false, null)!!
        assertEquals(Cond.SUN, w.cond); assertEquals(Line.COND, w.line); assertEquals(-7, w.temp)
    }
    @Test fun weatherMoonAtNight() = assertEquals(Cond.MOON, TopPillModel.weather(now(-14), 0, evening, false, null)!!.cond)
    @Test fun weatherGusts() {
        val w = TopPillModel.weather(now(-9, gust = 65), 3, noon, false, null)!!
        assertEquals(Cond.WIND, w.cond); assertEquals(Line.GUSTS, w.line); assertEquals(65, w.n)
        assertEquals(Line.COND, TopPillModel.weather(now(gust = 50), 3, noon, false, null)!!.line)
    }
    @Test fun weatherFreshSnowMorningOnly() {
        val w = TopPillModel.weather(now(-11, snow24 = 18), 73, morning, false, null)!!
        assertEquals(Line.FRESH_SNOW, w.line); assertEquals(18, w.n); assertEquals(Cond.SNOW, w.cond)
        assertEquals(Line.COND, TopPillModel.weather(now(snow24 = 18), 3, noon, false, null)!!.line)
        assertEquals(Line.COND, TopPillModel.weather(now(snow24 = 4), 3, morning, false, null)!!.line)
    }
    @Test fun weatherPreSeasonSnowComing() {
        val w = TopPillModel.weather(now(-8), 3, noon, true, 20)!!
        assertEquals(Line.SNOW_COMING, w.line); assertEquals(20, w.n)
        assertEquals(Line.COND, TopPillModel.weather(now(-8), 3, noon, true, 0)!!.line)
        assertEquals(Line.COND, TopPillModel.weather(now(-8), 3, noon, true, null)!!.line)
    }
    @Test fun weatherNoTemperatureNoPart() = assertNull(TopPillModel.weather(Forecast.Now(null, 1, 1, 0, 0), 0, noon, false, null))
    @Test fun weatherNoForecastNoPart() = assertNull(TopPillModel.weather(null, null, noon, false, null))
    @Test fun conditionCodes() {
        assertEquals(Cond.FOG, TopPillModel.cond(45, 10, false))
        assertEquals(Cond.RAIN, TopPillModel.cond(61, 10, false))
        assertEquals(Cond.SNOW, TopPillModel.cond(75, 10, false))
        assertEquals(Cond.CLOUD, TopPillModel.cond(3, 10, false))
        assertEquals(Cond.SUN, TopPillModel.cond(null, null, false))
    }
}

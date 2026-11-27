package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.places.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boards DT1-DT5: the rows of the day timeline, the café rule and the "where the day went" parts. */
class DayTimelineTest {
    private val min = 60_000L
    private fun place(kind: String, name: String?, lat: Double, lon: Double) = Place("p$name$kind", kind, name, lat, lon, false, false)
    private fun rows(items: List<DayModel.Item>, lifts: List<DayTimelineRules.LiftIn>) =
        DayTimelineRules.rows(items, lifts, { 0 }, null, { null }) { _, lift -> DayTimelineRules.BreakKind.Bench(lift) }

    @Test fun aQueueBeforeALiftJoinsItAndMovesItsTime() {
        val r = rows(listOf(DayModel.Queue(1_000 * min, 3, "Kobi")), listOf(DayTimelineRules.LiftIn(1_004 * min, "Kobi", "gondola", 6, 544.0)))
        assertEquals(1, r.size)
        val l = r[0] as DayTimelineRules.Lift
        assertEquals(1_000 * min, l.t0); assertEquals(3, l.queueMin); assertEquals("Kobi", l.name)
    }

    @Test fun aFarQueueStaysItsOwnRow() {
        val r = rows(listOf(DayModel.Queue(1_000 * min, 4, null)), listOf(DayTimelineRules.LiftIn(1_030 * min, "Kobi", null, 6, null)))
        assertEquals(2, r.size)
        assertTrue(r[0] is DayTimelineRules.Queue); assertTrue(r[1] is DayTimelineRules.Lift)
    }

    @Test fun onlyALongBreakIsARowAndItNamesTheLiftBeforeIt() {
        val items = listOf(DayModel.Rest(2_000 * min, 12, false), DayModel.Rest(2_100 * min, 35, true))
        val r = rows(items, listOf(DayTimelineRules.LiftIn(2_050 * min, "Kobi", null, 5, null)))
        assertEquals(2, r.size)
        val b = r[1] as DayTimelineRules.Break
        assertEquals(DayTimelineRules.BreakKind.Bench("Kobi"), b.kind)
    }

    @Test fun theBreakIsACafeOnlyWithANamedPlaceInside250m() {
        val near = place("cafe", "Ice Bar", 42.4924, 44.4943)
        val far = place("cafe", "Far", 42.50, 44.50)
        val unnamed = place("food", null, 42.4924, 44.4943)
        val k = DayTimelineRules.breakKind(42.492372, 44.494273, listOf(far, unnamed, near), "Kobi")
        assertEquals(DayTimelineRules.BreakKind.Cafe("Ice Bar"), k)
        assertEquals(DayTimelineRules.BreakKind.Bench("Kobi"), DayTimelineRules.breakKind(42.492372, 44.494273, listOf(far, unnamed), "Kobi"))
        assertEquals(DayTimelineRules.BreakKind.Bench(null), DayTimelineRules.breakKind(null, null, listOf(near), null))
    }

    @Test fun aRestaurantGetsTheFork() {
        val k = DayTimelineRules.breakKind(42.0, 44.0, listOf(place("food", "Gradus", 42.0005, 44.0)), null)
        assertEquals(DayTimelineRules.BreakKind.Food("Gradus"), k)
    }

    @Test fun theDayPartsAddUpAndNeverGoNegative() {
        val w = DayTimelineRules.where(3600.0, 1200.0, 900.0, 300.0)
        assertEquals(1200.0, w.rest, 1e-9); assertEquals(3600.0, w.total, 1e-9)
        val over = DayTimelineRules.where(1000.0, 800.0, 800.0, 400.0)
        assertEquals(0.0, over.rest, 1e-9); assertEquals(1000.0, over.total, 1e-6)
    }

    @Test fun hoursHaveOneDecimal() {
        assertEquals("3.5", DayTimelineRules.hours(0, 3 * 3_600_000L + 30 * min))
    }

    @Test fun theStripTakesTheMeanBandPerBinAndFillsGaps() {
        val t = longArrayOf(0, 10, 20, 90); val band = intArrayOf(0, 2, 4, 4)
        val s = DayTimelineRules.strip(t, band, 0, 100, 4)
        assertEquals(listOf(2, 2, 2, 4), s.toList())
        assertEquals(0, DayTimelineRules.strip(t, band, 200, 300, 4).size)
    }

    @Test fun gridLinesAreOnlyTheOnesInsideTheDaysHeights() {
        assertEquals(listOf(2200, 2600, 3000), DayTimelineRules.gridLines(2100f, 3100f))
        assertEquals(listOf(2600), DayTimelineRules.gridLines(2300f, 2900f))
        assertEquals(emptyList<Int>(), DayTimelineRules.gridLines(2300f, 2500f))
    }

    @Test fun aTapOnTheStripIsATimeInsideTheDay() {
        assertEquals(500L, DayTimelineRules.tapTime(50f, 100f, 0, 1000))
        assertEquals(1000L, DayTimelineRules.tapTime(400f, 100f, 0, 1000))
        assertEquals(0L, DayTimelineRules.tapTime(-5f, 100f, 0, 1000))
    }

    @Test fun theWindowRunsFromTheFirstVisibleRowToTheNextOneAndIsNeverInvisible() {
        assertEquals(100L to 400L, DayTimelineRules.window(listOf(100, 200, 300), 400L, 0, 1000))
        assertEquals(100L to 1000L, DayTimelineRules.window(listOf(100, 200), null, 0, 1000))
        assertEquals(100L to 130L, DayTimelineRules.window(listOf(100), 101L, 0, 1000))
        assertEquals(null, DayTimelineRules.window(emptyList(), null, 0, 1000))
    }

    @Test fun aTapScrollsToTheFirstRowAtOrAfterThatTime() {
        val times = listOf<Long?>(null, null, null, null, 100L, 200L, null, 300L)
        assertEquals(5, DayTimelineRules.scrollTarget(times, 150L))
        assertEquals(7, DayTimelineRules.scrollTarget(times, 250L))
        assertEquals(7, DayTimelineRules.scrollTarget(times, 999L))
        assertEquals(null, DayTimelineRules.scrollTarget(listOf(null, null), 5L))
    }
}

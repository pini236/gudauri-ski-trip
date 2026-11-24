package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "To go down in a film" (K12): the line, the numbers, the next meeting, the ticks and the speeds. */
class FilmRulesTest {
    // 1,000 m due south (y grows), losing 300 m of height; then 500 m losing 300 m more (31 degrees)
    private val line = FilmLine(floatArrayOf(0f, 3000f, 0f, 0f, 2700f, 1000f, 0f, 2400f, 1500f))

    @Test fun theLineIsMeasuredAlongTheGround() {
        assertEquals(1500f, line.length, .01f)
        assertEquals(2850f, line.at(500f)[1], .01f)
        assertEquals(1250f, line.at(1250f)[2], .01f)
        // past the ends it stays at the ends
        assertEquals(0f, line.at(-50f)[2], .01f)
        assertEquals(1500f, line.at(9999f)[2], .01f)
    }

    @Test fun heightAndSlopeAsTheFlightReadsThem() {
        assertEquals(2846, line.altAt(500f))
        assertEquals(17, line.slopeAt(500f)) // atan(300 / 1000)
        assertEquals(31, line.slopeAt(1250f)) // atan(300 / 500)
        assertTrue(FilmRules.strong(line, 1250f))
        assertFalse(FilmRules.strong(line, 500f))
    }

    @Test fun aClimbIsNoSlope() {
        val up = FilmLine(floatArrayOf(0f, 2000f, 0f, 0f, 2100f, 500f))
        assertEquals(0, up.slopeAt(250f))
    }

    @Test fun theNearestPointOnTheLine() {
        val (at, off) = line.nearest(40f, 700f)
        assertEquals(700f, at, .01f)
        assertEquals(40f, off, .01f)
    }

    @Test fun aRunMeetsTheLineOnlyWithinSixtyMetres() {
        val ends = listOf(
            "Tatra 1" to listOf(floatArrayOf(50f, 400f), floatArrayOf(900f, 900f)),
            "Kudebi" to listOf(floatArrayOf(61f, 1200f)),
            "Soliko" to listOf(floatArrayOf(-20f, 1100f), floatArrayOf(10f, 300f)),
            "Self" to listOf(floatArrayOf(0f, 50f)),
        )
        val j = FilmRules.joins(line, "Self", ends)
        assertEquals(listOf(FilmJoin(300f, "Soliko"), FilmJoin(400f, "Tatra 1")), j)
    }

    @Test fun theNextMeetingIsAheadRoundedToTenMetres() {
        val j = listOf(FilmJoin(300f, "Soliko"), FilmJoin(400f, "Tatra 1"))
        assertEquals(240 to "Soliko", FilmRules.next(j, 63f))
        assertEquals(100 to "Tatra 1", FilmRules.next(j, 298f)) // Soliko is here already
        assertNull(FilmRules.next(j, 400f))
    }

    @Test fun aTickEachHundredMetresDownNotTooFastAtEight() {
        // 30 m along loses 9 m: still the first band
        assertEquals(0 to false, FilmRules.tick(line, 30f, 0, 1000, 8))
        // 340 m along has lost 102 m: a tick
        assertEquals(1 to true, FilmRules.tick(line, 340f, 0, 1000, 8))
        // at ×8 within 150 ms of the last: later
        assertEquals(0 to false, FilmRules.tick(line, 340f, 0, 100, 8))
        // at ×2 a tick whenever the band changes
        assertEquals(1 to true, FilmRules.tick(line, 340f, 0, 100, 2))
    }

    @Test fun theSpeedGoesEightTwoFourAndRound() {
        assertEquals(2, FilmRules.nextSpeed(8))
        assertEquals(4, FilmRules.nextSpeed(2))
        assertEquals(8, FilmRules.nextSpeed(4))
        assertEquals(10f, FilmRules.BASE)
    }
}

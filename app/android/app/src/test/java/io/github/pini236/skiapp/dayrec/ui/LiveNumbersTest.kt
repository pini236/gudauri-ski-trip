package io.github.pini236.skiapp.dayrec.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveNumbersTest {
    @Test fun speedIsWholeKmh() {
        assertEquals("0", LiveNumbers.kmh(null))
        assertEquals("0", LiveNumbers.kmh(-1.0))
        assertEquals("36", LiveNumbers.kmh(10.0))
        assertEquals("63", LiveNumbers.kmh(17.5))
    }

    @Test fun distanceAndDrop() {
        assertEquals("0.0", LiveNumbers.km(null))
        assertEquals("12.4", LiveNumbers.km(12_400.0))
        assertEquals("1.0", LiveNumbers.km(960.0))
        assertEquals("548", LiveNumbers.metres(547.6))
    }

    @Test fun elapsedHasMinutesAndHours() {
        assertEquals("0:12", LiveNumbers.elapsed(12_000))
        assertEquals("5:00", LiveNumbers.elapsed(300_000))
        assertEquals("1:05:03", LiveNumbers.elapsed(3_903_000))
        assertEquals("0:00", LiveNumbers.elapsed(-5))
    }

    @Test fun weakGpsAfterTwentySecondsWithoutAReading() {
        assertEquals(false, LiveNumbers.weakGps(30_000, 20_000, 1_000)) // a reading 10 s ago
        assertEquals(true, LiveNumbers.weakGps(50_000, 20_000, 1_000)) // 30 s ago
        assertEquals(false, LiveNumbers.weakGps(30_000, 0, 1_000)) // no reading yet: waiting for the satellites is not a weak signal
        assertEquals(false, LiveNumbers.weakGps(80_000, 0, 1_000)) // still within the first wait
        assertEquals(true, LiveNumbers.weakGps(100_000, 0, 1_000)) // no reading at all for over a minute and a half
        assertEquals(false, LiveNumbers.weakGps(10_000, 0, 1_000)) // just started
        assertEquals(false, LiveNumbers.weakGps(99_000, 0, 0)) // not recording
    }
}

/** The replay of a day on the 3D map: where the dot is at a time. */
class ReplayAtTest {
    private val tr = Track(floatArrayOf(0f, 100f, 200f, 5000f), floatArrayOf(0f, 0f, 0f, 0f), doubleArrayOf(0.0, 20.0, 40.0, 10.0), longArrayOf(0, 10_000, 20_000, 400_000))

    @org.junit.Test fun betweenTwoReadingsItIsBetweenThem() {
        val s = ReplayAt.spot(tr, 5_000)!!
        org.junit.Assert.assertEquals(50f, s.x, 0.001f)
        org.junit.Assert.assertEquals(10.0, s.kmh, 0.001)
    }

    @org.junit.Test fun beforeAndAfterTheDayItStaysAtTheEnds() {
        org.junit.Assert.assertEquals(0f, ReplayAt.spot(tr, -5)!!.x, 0.001f)
        org.junit.Assert.assertEquals(5000f, ReplayAt.spot(tr, 999_999)!!.x, 0.001f)
    }

    @org.junit.Test fun aHoleInTheRecordingHoldsStillAndNothingIsDrawnAcrossIt() {
        val s = ReplayAt.spot(tr, 100_000)!! // between the readings at 20 s and 400 s: a hole
        org.junit.Assert.assertEquals(200f, s.x, 0.001f)
        org.junit.Assert.assertEquals(0.0, s.kmh, 0.001)
    }

    @org.junit.Test fun noReadingsNoSpot() {
        org.junit.Assert.assertNull(ReplayAt.spot(Track(FloatArray(0), FloatArray(0), DoubleArray(0), LongArray(0)), 5))
    }

    @Test fun counterNeverGoesBackWhenTheFirstReadingComesLate() {
        val tap = 1_000_000L
        // 8 s after the tap the counter says 8 s; the first reading (at 8.5 s) must not change what the counter counts from
        val before = LiveNumbers.sinceTap(tap + 8_000L, tap)
        val after = LiveNumbers.sinceTap(tap + 8_500L, tap)
        assertEquals(8_000L, before)
        assertTrue(after >= before)
    }

    @Test fun counterIsZeroBeforeTheRecordingBegan() {
        assertEquals(0L, LiveNumbers.sinceTap(5_000L, 0L))
        assertEquals(0L, LiveNumbers.sinceTap(1_000L, 2_000L))
    }

    @Test fun searchingOnlyWhileRecordingWithoutAReading() {
        assertTrue(LiveNumbers.searching(true, 0L))
        assertTrue(!LiveNumbers.searching(true, 5L))
        assertTrue(!LiveNumbers.searching(false, 0L))
    }
}

package io.github.pini236.skiapp.dayrec.ui

import org.junit.Assert.assertEquals
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
        assertEquals(true, LiveNumbers.weakGps(30_000, 0, 1_000)) // no reading at all since the start
        assertEquals(false, LiveNumbers.weakGps(10_000, 0, 1_000)) // just started
        assertEquals(false, LiveNumbers.weakGps(99_000, 0, 0)) // not recording
    }
}

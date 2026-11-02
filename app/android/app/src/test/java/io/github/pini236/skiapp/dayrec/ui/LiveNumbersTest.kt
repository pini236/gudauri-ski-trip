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
}

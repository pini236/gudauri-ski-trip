package io.github.pini236.skiapp.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasureTest {
    @Test fun europeIsTheSitesZones() {
        for (z in listOf("Europe/Berlin", "Europe/London", "Europe/Moscow", "Atlantic/Canary", "Atlantic/Reykjavik", "Asia/Nicosia", "Asia/Famagusta")) assertTrue(z, Measure.europe(z))
    }

    @Test fun otherZonesAreNot() {
        for (z in listOf("Asia/Jerusalem", "Asia/Tbilisi", "America/New_York", "Atlantic/Bermuda", "Africa/Cairo", "")) assertFalse(z, Measure.europe(z))
    }
}

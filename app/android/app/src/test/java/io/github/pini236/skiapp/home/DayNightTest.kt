package io.github.pini236.skiapp.home

import io.github.pini236.skiapp.home.DayNight.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The home page's sky, as on the site: the same images and darkness through a January day in Gudauri. */
class DayNightTest {
    private fun gud(h: Int, m: Int = 0) = LocalDateTime.of(2027, 1, 12, h, m).toInstant(ZoneOffset.ofHours(4)).toEpochMilli()

    @Test fun throughTheDay() {
        val noon = DayNight.at(gud(12, 30), Mode.AUTO)
        assertFalse(noon.dark); assertTrue(noon.imgA in setOf("morning", "noon")); assertEquals("12:30", noon.clock)
        val night = DayNight.at(gud(23), Mode.AUTO)
        assertTrue(night.dark); assertEquals("night", night.imgA); assertEquals(1f, night.stars, 0f)
        val dawn = DayNight.at(gud(7, 30), Mode.AUTO)
        assertTrue(dawn.imgA == "dawn" || dawn.imgB == "dawn")
        val dusk = DayNight.at(gud(17, 30), Mode.AUTO)
        assertTrue(setOf(dusk.imgA, dusk.imgB).any { it == "sunset" || it == "dusk" })
    }

    @Test fun theModesOverrideTheClock() {
        assertFalse(DayNight.at(gud(23), Mode.DAY).dark)
        assertTrue(DayNight.at(gud(12), Mode.NIGHT).dark)
        assertEquals("23:00", DayNight.at(gud(23), Mode.DAY).clock) // the clock always tells the real time
        assertEquals(Mode.DAY, Mode.AUTO.next()); assertEquals(Mode.AUTO, Mode.NIGHT.next())
    }
}

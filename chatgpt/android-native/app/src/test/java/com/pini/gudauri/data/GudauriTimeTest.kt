package com.pini.gudauri.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GudauriTimeTest {
    @Test fun `geometric sunrise agrees with current website including leap day`() {
        // Snapshots calculated directly from the read-only website's DN.sunTimes formula.
        for ((date,rise,set,noon) in listOf(
            listOf("2027-01-11",8.607298353501161,17.720457348906802,13.163877851203981),
            listOf("2026-06-21",5.497557442276177,20.61977589105716,13.058666666666667),
            listOf("2026-12-21",8.577692419984094,17.455336592735485,13.016514506359789),
            listOf("2028-02-29",7.768876596148919,18.733582924307548,13.251229760228235)
        )) {
            val t=GudauriTime.sunTimes(LocalDate.parse(date as String))
            assertEquals(rise as Double,t.rise,1e-9);assertEquals(set as Double,t.set,1e-9);assertEquals(noon as Double,t.noon,1e-9)
        }
    }
    @Test fun `forced theme never changes the actual Gudauri clock`() {
        val now=Instant.parse("2027-01-11T10:10:00Z")
        val states=ThemeMode.entries.map { GudauriTime.at(now,it) }
        assertTrue(states.all { it.actualTime.hour==14 && it.actualTime.minute==10 && it.actualTime.toInstant()==now })
        assertFalse(states[1].dark); assertTrue(states[2].dark)
        assertEquals("noon",states[1].second.image)
        assertEquals("night",states[2].first.image)
    }
    @Test fun `automatic night handles date rollover in Georgia`() {
        val state=GudauriTime.at(Instant.parse("2027-01-10T22:00:00Z"),ThemeMode.AUTO)
        assertTrue(state.dark);assertEquals(LocalDate.of(2027,1,11),state.actualTime.toLocalDate())
        assertEquals("לילה",state.phase)
        val day=GudauriTime.at(Instant.parse("2027-01-11T10:00:00Z"),ThemeMode.AUTO)
        assertFalse(day.dark);assertEquals("יום",day.phase)
    }
    @Test fun `theme migration preserves explicit preferences and allows auto again`() {
        assertEquals(ThemeMode.DAY,ThemeMode.fromSaved(1));assertEquals(ThemeMode.NIGHT,ThemeMode.fromSaved(2))
        assertEquals(ThemeMode.AUTO,ThemeMode.fromSaved(0));assertEquals(ThemeMode.AUTO,ThemeMode.fromSaved(999))
        assertEquals(ThemeMode.DAY,ThemeMode.AUTO.next());assertEquals(ThemeMode.NIGHT,ThemeMode.DAY.next());assertEquals(ThemeMode.AUTO,ThemeMode.NIGHT.next())
    }
    @Test fun `flight countdown changes at Jerusalem midnight and never becomes negative`() {
        val flight=Flight("","","2027-01-10","","16:00","20:35","")
        assertEquals(1,GudauriTime.daysToFlight(flight,Instant.parse("2027-01-09T21:59:59Z")))
        assertEquals(0,GudauriTime.daysToFlight(flight,Instant.parse("2027-01-09T22:00:00Z")))
        assertEquals(0,GudauriTime.daysToFlight(flight,Instant.parse("2027-01-11T10:00:00Z")))
        assertEquals("לילות לטיסה",GudauriTime.countdownLabel(flight,Instant.parse("2027-01-09T20:00:00Z"),true))
        assertEquals("יוצאים לדרך",GudauriTime.countdownLabel(flight,Instant.parse("2027-01-10T10:00:00Z"),false))
        assertEquals("הטיסה כבר יצאה",GudauriTime.countdownLabel(flight,Instant.parse("2027-01-11T10:00:00Z"),false))
    }
    @Test fun `panorama blend and mixed colors stay in their range all year`() {
        for (month in 1..12) for (hour in 0..23) for (mode in ThemeMode.entries) {
            val state=GudauriTime.at(LocalDate.of(2027,month,15).atTime(hour,0).atZone(GudauriTime.zone).toInstant(),mode)
            assertTrue(state.blend in 0f..1f)
            assertEquals(255,(state.mixColor(state.first.top,state.second.top) ushr 24) and 255)
            assertTrue(state.first.hour <= state.second.hour)
        }
    }
}

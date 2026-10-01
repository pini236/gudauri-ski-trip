package com.pini.gudauri.data

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MeetingTest {
    private val root = File(requireNotNull(System.getProperty("gudauri.dataDir")))
    private fun data() = DataParser.parse(File(root,"runs-and-lifts.json").readText(),File(root,"terrain.json").readText(),trip=File(root,"trip.json").readText())
    @Test fun `all station identifiers endpoints coordinates and elevations match the website inventory`() {
        val model = MeetingModel(data())
        assertEquals(12,model.lifts.size);assertEquals(20,model.stations.size)
        val inventory = JSONObject(File(requireNotNull(root.parentFile?.parentFile),"chatgpt/android-native/docs/CONTENT_INVENTORY.json").readText()).getJSONArray("meetingStations")
        repeat(inventory.length()) { i ->
            val expected = inventory.getJSONObject(i); val actual = model.stations[i]
            assertEquals(expected.getString("id"),actual.id)
            assertEquals(expected.getDouble("x"),actual.point.x.toDouble(),.02)
            assertEquals(expected.getDouble("y"),actual.point.z.toDouble(),.02)
            assertEquals(expected.getInt("elevation"),actual.elevation)
            val ends = expected.getJSONArray("ends")
            assertEquals(ends.length(),actual.ends.size)
            repeat(ends.length()) { j ->
                val end=ends.getJSONObject(j)
                assertEquals(end.getLong("liftId"),actual.ends[j].lift.id)
                assertEquals(end.getString("end"),actual.ends[j].end.suffix)
            }
        }
    }
    @Test fun `shared stations keep the first website ID and accept every endpoint alias`() {
        val model=MeetingModel(data())
        val noon= requireNotNull(model.station("158744075t"))
        assertEquals("158744055b",noon.id)
        assertTrue(noon.description.contains("Snow Park"));assertTrue(noon.description.contains("Goodaura"))
        val morning = model.presets.first { it.id=="am" };val evening=model.presets.first { it.id=="pm" }
        assertEquals(morning.stationId,evening.stationId)
        assertEquals(3,model.presets.size);assertEquals(LocalTime.of(13,0),model.presets.first { it.id=="noon" }.time)
        assertNull(model.station("unknown"))
    }
    @Test fun `trip dates and common times are derived without selecting a meeting`() {
        val model=MeetingModel(data())
        assertEquals((11..14).map { LocalDate.of(2027,1,it) },model.days)
        assertEquals(6,model.times.size)
        assertTrue(MeetingModel(data().copy(trip=null)).days.isEmpty())
    }
    @Test fun `routes use only source connections are unique and limited to four`() {
        val model=MeetingModel(data())
        model.stations.forEach { station ->
            val routes=model.routes(station)
            assertTrue(routes.size<=4);assertEquals(routes.distinct().size,routes.size)
            routes.forEach { route ->
                if(route.liftId!=null) assertTrue(station.ends.any { it.end==StationEnd.TOP && it.lift.id==route.liftId })
                else {
                    val run=model.data.pistesByKey.getValue(route.runKeys.last())
                    assertTrue(station.ends.any { it.end==StationEnd.BOTTOM && it.lift.name in run.toLifts })
                    if(route.runKeys.size==2) assertTrue(route.runKeys.first() in run.fromPistes)
                }
            }
        }
    }
    @Test fun `countdown uses actual Gudauri time handles past midnight and minute carry`() {
        val choice=MeetingChoice("158744055b",LocalDate.of(2027,1,11),LocalTime.of(13,0))
        assertEquals(Instant.parse("2027-01-11T09:00:00Z"),choice.instant)
        assertEquals("כבר עבר",MeetingModel.countdown(choice,choice.instant.plusSeconds(1)))
        assertEquals("עוד 30 דקות למפגש",MeetingModel.countdown(choice,choice.instant.minusSeconds(1800)))
        assertEquals("עוד 2:00 שעות למפגש",MeetingModel.countdown(choice,choice.instant.minusSeconds(7190)))
        assertEquals("עוד 2 ימים למפגש",MeetingModel.countdown(choice,choice.instant.minusSeconds(86401)))
    }
    @Test fun `message is local and canonical without member names or invented navigation`() {
        val model=MeetingModel(data());val choice=MeetingChoice("158744075t",model.days.first(),LocalTime.of(13,0))
        val message=model.message(choice)
        assertTrue(message.contains("#meet/158744055b/1300/20270111"))
        assertTrue(message.contains("2702 מ׳"))
        data().trip!!.members.forEach { assertFalse(message.contains(it)) }
    }
}

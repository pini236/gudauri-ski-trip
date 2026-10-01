package com.pini.gudauri.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.hypot

class MountainDataTest {
    private fun data():MountainData {
        val root=File(requireNotNull(System.getProperty("gudauri.dataDir")))
        fun read(name:String)=File(root,"$name.json").readText()
        return DataParser.parse(read("runs-and-lifts"),read("terrain"),read("videos-seed"),read("trip"))
    }
    @Test fun `decodes real data without dropping unnamed or partial runs`() {
        val data=data()
        assertEquals(34,data.pistes.size);assertEquals(27,data.pistes.count{it.named})
        assertEquals(16,data.lifts.size);assertEquals(43,data.videos.size)
        assertEquals(6,data.pistes.count{it.research?.partial!=null})
        assertEquals(372,data.pistesByKey.getValue("Soliko 2").length)
        assertEquals("ski-way",data.pistesByKey.getValue("Shino").kind)
        assertEquals("beginner-area",data.pistesByKey.getValue("Baby").kind)
    }
    @Test fun `terrain uses little endian and bilinear interpolation at boundaries`() {
        val t=Terrain(2,2,0f,0f,100f,100f,shortArrayOf(1000,1100,1200,1300),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        assertEquals(1150f,t.elevation(50f,50f),.01f)
        assertEquals(1000f,t.elevation(-10f,-10f),.01f)
        assertEquals(1300f,t.elevation(110f,110f),.1f)
        val real=data().terrain
        assertEquals(real.nx*real.ny,real.heights.size)
        assertTrue(real.heights.all { it in 500..4000 })
        assertTrue(real.elevation(0f,0f) in 1500f..3500f)
    }
    @Test fun `projection agrees with web coordinate origin and northern orientation`() {
        val origin=DataParser.project(42.51,44.495)
        assertEquals(0f,origin.x,.001f);assertEquals(0f,origin.z,.001f)
        val north=DataParser.project(42.52,44.495)
        assertEquals(-1113.2f,north.z,.02f)
        assertEquals(0f,north.x,.001f)
    }
    @Test fun `all geometry and video keys retain the existing source relationships`() {
        val d=data();val t=d.terrain
        d.pistes.flatMap{it.segments}.flatMap{it.points}.forEach { p->assertTrue("point $p outside DEM",p.x in t.x0..t.x1 && p.z in t.z0..t.z1) }
        d.videos.forEach{assertTrue(d.pistesByKey.containsKey(it.piste))}
        d.pistes.forEach{p->(p.joins+p.fromPistes).forEach{assertTrue(d.pistesByKey.containsKey(it))}}
        assertEquals("2027-01-10",requireNotNull(d.trip).outbound.date)
        assertEquals("2027-01-15",requireNotNull(d.trip?.inbound).date)
        assertTrue(d.stats.getValue("Tatra 2").drop in 400..900)
        assertTrue(d.stats.values.all{it.top>=it.bottom && it.drop>=0})
    }
    @Test fun `flight content includes new fields and inclusive ski days`() {
        val trip = requireNotNull(data().trip)
        assertEquals("ישראייר", trip.airline)
        assertEquals("גודאורי, גאורגיה", trip.destination)
        assertEquals("תיק יד", trip.baggage)
        assertEquals("TLV", trip.outbound.fromCode); assertEquals("TBS", trip.outbound.toCode)
        val back = requireNotNull(trip.inbound)
        assertEquals("TBS", back.fromCode); assertEquals("TLV", back.toCode)
        assertEquals("6H 892", back.flight); assertEquals("02:15", back.arrives)
        assertTrue(back.note.contains("14")); assertTrue(back.note.contains("15"))
        assertEquals(listOf(11,12,13,14).map { LocalDate.of(2027,1,it) }, trip.skiDays?.dates)
        assertEquals(6, trip.members.size)
    }
    @Test fun `lift unknowns never become literal null and cover data survives`() {
        val d = data()
        assertTrue(d.lifts.flatMap { listOf(it.status,it.capacity,it.duration,it.occupancy,it.year,it.sourceRise) }.none { it == "null" })
        assertEquals(true,d.lifts.first { it.name == "Kikilo" }.bubble)
        assertEquals(false,d.lifts.first { it.name == "Pirveli" }.bubble)
        assertEquals("547",d.lifts.first { it.name == "Goodaura" }.sourceRise)
        assertEquals(0,d.missing.size)
        assertTrue(d.pistes.flatMap { it.segments }.any { it.osmDifficulty.isNotEmpty() })
    }
    @Test fun `videos keep research fields associations and descending research order`() {
        val videos = data().videos
        assertEquals(43,videos.size); assertEquals(37,videos.map { it.url }.distinct().size)
        assertTrue(videos.all { it.by.isNotBlank() && it.addedAt != null && it.published.isNotBlank() && it.evidence.isNotBlank() })
        assertTrue(videos.zipWithNext().all { (a,b) -> requireNotNull(a.addedAt) >= requireNotNull(b.addedAt) })
        assertEquals(18,videos.map { it.piste }.distinct().size)
    }
    @Test fun `missing and corrupt optional files do not disable the mountain`() {
        val root=File(requireNotNull(System.getProperty("gudauri.dataDir")))
        val runs=File(root,"runs-and-lifts.json").readText(); val terrain=File(root,"terrain.json").readText()
        for ((videos,trip) in listOf(null to null,"not json" to "{}")) {
            val d=DataParser.parse(runs,terrain,videos,trip)
            assertEquals(34,d.pistes.size); assertEquals(16,d.lifts.size)
            assertTrue(d.videos.isEmpty()); assertNull(d.trip)
            assertEquals(OptionalContent.entries.toSet(),d.warnings.map { it.content }.toSet())
            assertTrue(d.stats.getValue("Tatra 2").available)
        }
        val trip = File(root,"trip.json").readText()
        val d=DataParser.parse(runs,terrain,"[]",trip)
        assertTrue(d.warnings.isEmpty()); assertNotNull(d.trip)
    }
    @Test fun `a flight without a return remains valid while invalid dates are rejected`() {
        val root=File(requireNotNull(System.getProperty("gudauri.dataDir")))
        val json=JSONObject(File(root,"trip.json").readText()).apply { remove("return") }
        val trip=DataParser.trip(json.toString())
        assertNull(trip.inbound); assertEquals("6H 897",trip.outbound.flight)
        json.getJSONObject("skiDays").put("to","2027-01-01")
        assertThrows(IllegalArgumentException::class.java) { DataParser.trip(json.toString()) }
    }
    @Test fun `terrain rejects mismatched dimensions truncated contours and odd sample bytes`() {
        val root=File(requireNotNull(System.getProperty("gudauri.dataDir")))
        val original=File(root,"terrain.json").readText()
        val badDimensions=JSONObject(original).apply { getJSONObject("dem").put("nx",0) }
        assertThrows(IllegalArgumentException::class.java) { DataParser.terrain(badDimensions.toString()) }
        val oddBytes=JSONObject(original).apply { getJSONObject("dem").put("b64","AA==") }
        assertThrows(IllegalArgumentException::class.java) { DataParser.terrain(oddBytes.toString()) }
        val badContours=JSONObject(original).apply { put("contours","AAA=") }
        assertThrows(IllegalArgumentException::class.java) { DataParser.terrain(badContours.toString()) }
    }
    @Test fun `profile selects geometric length instead of vertex count and points downhill`() {
        val t=Terrain(2,2,0f,0f,100f,100f,shortArrayOf(1000,1100,1200,1300),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val dense=Segment(1,false,(0..10).map { Point(it.toFloat(),0f) })
        val long=Segment(2,false,listOf(Point(0f,0f),Point(100f,0f)))
        val chosen=t.longestDescendingPoints(listOf(dense,long))
        assertEquals(long.points.reversed(),chosen)
        assertTrue(t.profile(chosen).first().second >= t.profile(chosen).last().second)
        val polygon=Segment(3,true,listOf(Point(0f,0f),Point(20f,0f),Point(20f,20f)))
        assertTrue(t.longestDescendingPoints(listOf(polygon)).isEmpty())
        assertFalse(t.stats(listOf(polygon)).available)
    }
    @Test fun `all three audited profile regressions choose their longer line`() {
        val d=data()
        for ((key,expected) in listOf("Kudebi 1" to 831f,"Zuma" to 349f,"Sportuli 2" to 491f)) {
            val points=d.terrain.longestDescendingPoints(d.pistesByKey.getValue(key).segments)
            val length=points.zipWithNext().sumOf { (a,b) -> hypot((b.x-a.x).toDouble(),(b.z-a.z).toDouble()) }.toFloat()
            assertEquals(key,expected,length,2f)
            val profile=d.terrain.profile(points)
            assertTrue(key,profile.first().second >= profile.last().second)
        }
        assertFalse(d.stats.getValue("u473280432").available)
    }
}

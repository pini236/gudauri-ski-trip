package com.pini.gudauri.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

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
        assertEquals("2027-01-10",d.trip.outbound.date)
        assertEquals("2027-01-15",d.trip.inbound.date)
        assertTrue(d.stats.getValue("Tatra 2").drop in 400..900)
        assertTrue(d.stats.values.all{it.top>=it.bottom && it.drop>=0})
    }
}

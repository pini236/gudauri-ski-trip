package com.pini.gudauri.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class RunProfileTest {
    private fun terrain() = Terrain(3,2,0f,0f,1000f,100f,shortArrayOf(1000,1250,1500,1000,1250,1500),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
    private fun real():MountainData {
        val root=File(requireNotNull(System.getProperty("gudauri.dataDir")))
        return DataParser.parse(File(root,"runs-and-lifts.json").readText(),File(root,"terrain.json").readText())
    }
    @Test fun `samples the longest real line downhill without bridging gaps`() {
        val t=terrain()
        val short=Segment(1,false,listOf(Point(0f,0f),Point(40f,0f)))
        val line=Segment(2,false,listOf(Point(100f,0f),Point(400f,0f)))
        val polygon=Segment(3,true,listOf(Point(0f,0f),Point(1000f,0f),Point(1000f,100f)))
        val profile=requireNotNull(RunProfile.create(t,listOf(short,line,polygon)))
        assertEquals(31,profile.samples.size)
        assertEquals(Point(400f,0f),profile.samples.first().point)
        assertEquals(Point(100f,0f),profile.samples.last().point)
        assertEquals(300f,profile.length,.001f)
        assertTrue(profile.samples.zipWithNext().all { (a,b)->b.distance>a.distance })
        assertEquals(atan(.5f)*180f/PI.toFloat(),profile.samples[15].slopeDegrees,.01f)
        assertEquals(.5f,requireNotNull(profile.steepest).grade,.001f)
    }
    @Test fun `exclusive slope boundaries match the website including exactly 15 25 and 30`() {
        assertEquals(SlopeBand.GENTLE,SlopeBand.at(0f))
        assertEquals(SlopeBand.GENTLE,SlopeBand.at(14.999f))
        assertEquals(SlopeBand.MODERATE,SlopeBand.at(15f))
        assertEquals(SlopeBand.MODERATE,SlopeBand.at(24.999f))
        assertEquals(SlopeBand.STEEP,SlopeBand.at(25f))
        assertEquals(SlopeBand.STEEP,SlopeBand.at(29.999f))
        assertEquals(SlopeBand.VERY_STEEP,SlopeBand.at(30f))
        assertEquals(SlopeBand.VERY_STEEP,SlopeBand.at(89f))
        assertThrows(IllegalArgumentException::class.java){SlopeBand.at(Float.NaN)}
    }
    @Test fun `distance selection clamps endpoints and follows metres not sample indices`() {
        val profile=requireNotNull(RunProfile.create(terrain(),listOf(Segment(1,false,listOf(Point(0f,0f),Point(3f,0f),Point(103f,0f))))))
        assertEquals(profile.samples.first(),profile.sampleAtFraction(-1f))
        assertEquals(profile.samples.last(),profile.sampleAtFraction(2f))
        assertEquals(50f,profile.sampleAtFraction(.5f).distance,3f)
        assertThrows(IllegalArgumentException::class.java){profile.sampleAtFraction(Float.NaN)}
    }
    @Test fun `area empty and zero length geometry have no invented profile`() {
        val t=terrain()
        assertNull(RunProfile.create(t,emptyList()))
        assertNull(RunProfile.create(t,listOf(Segment(1,true,listOf(Point(0f,0f),Point(50f,0f),Point(50f,50f))))))
        assertNull(RunProfile.create(t,listOf(Segment(1,false,listOf(Point(20f,0f),Point(20f,0f))))))
        assertThrows(IllegalArgumentException::class.java){RunProfile.create(t,emptyList(),0f)}
    }
    @Test fun `short and flat lines never produce division by zero or false steep stretches`() {
        val p=requireNotNull(RunProfile.create(terrain(),listOf(Segment(1,false,listOf(Point(20f,0f),Point(20f,30f))))))
        assertNull(p.steepest)
        assertEquals(0f,p.startingGrade,.001f)
        assertEquals(p.samples.last(),p.first150)
        assertTrue(p.samples.all { it.height.isFinite() && it.slopeDegrees==0f })
    }
    @Test fun `duplicate vertices are harmless and do not create a zero distance selection`() {
        val points=listOf(Point(10f,0f),Point(10f,0f),Point(200f,0f),Point(200f,0f))
        val p=requireNotNull(RunProfile.create(terrain(),listOf(Segment(1,false,points))))
        assertEquals(190f,p.length,.001f)
        assertTrue(p.samples.zipWithNext().all { (a,b)->b.distance>a.distance })
    }
    @Test fun `real profiles match independently evaluated website samples and steep indices`() {
        // Expected values evaluated from the unchanged website sampleLine/runProfile,
        // using the actual DEM and geometry (baseline d0a2e88), not this Kotlin code.
        val d=real()
        data class Expected(val key:String,val count:Int,val length:Float,val firstHeight:Float,val middleHeight:Float,val lastHeight:Float,val firstSlope:Float,val steepStart:Int,val steepEnd:Int,val grade:Float)
        val expected=listOf(
            Expected("Tatra 2",229,2294.4763f,2664.566f,2397.0576f,2172.739f,5.94793f,65,76,.365681f),
            Expected("Kudebi 1",85,830.9352f,2808.2734f,2727.6604f,2660.9585f,8.76638f,16,27,.264068f),
            Expected("Zuma",37,348.5274f,2189.173f,2161.1814f,2136.6255f,9.13984f,17,28,.189662f),
            Expected("Sportuli 2",49,491.0797f,2368.9509f,2330.9714f,2276.7925f,15.89111f,27,37,.23501f),
            Expected("Pirveli",118,1167.8773f,2152.0513f,2078.319f,1995.1986f,6.11949f,58,68,.246132f))
        for(e in expected){
            val p=requireNotNull(RunProfile.create(d.terrain,d.pistesByKey.getValue(e.key).segments))
            assertEquals(e.key,e.count,p.samples.size)
            assertEquals(e.key,e.length,p.length,.02f)
            assertEquals(e.key,e.firstHeight,p.samples.first().height,.02f)
            assertEquals(e.key,e.middleHeight,p.samples[p.samples.size/2].height,.02f)
            assertEquals(e.key,e.lastHeight,p.samples.last().height,.02f)
            assertEquals(e.key,e.firstSlope,p.samples.first().slopeDegrees,.02f)
            assertEquals(e.key,e.steepStart,requireNotNull(p.steepest).startIndex)
            assertEquals(e.key,e.steepEnd,requireNotNull(p.steepest).endIndex)
            assertEquals(e.key,e.grade,requireNotNull(p.steepest).grade,.001f)
        }
        assertNull(RunProfile.create(d.terrain,d.pistesByKey.getValue("u473280432").segments))
    }
    @Test fun `all real line profiles stay finite ordered and bounded by their original geometry`() {
        val d=real()
        for(run in d.pistes){
            val p=RunProfile.create(d.terrain,run.segments) ?: continue
            assertTrue(run.key,p.length>0f)
            assertTrue(run.key,p.samples.all { it.distance.isFinite() && it.height.isFinite() && it.slopeDegrees in 0f..90f })
            assertTrue(run.key,p.samples.zipWithNext().all { (a,b)->b.distance>a.distance })
            assertEquals(run.key,p.samples.first(),p.sampleAtFraction(0f))
            assertEquals(run.key,p.samples.last(),p.sampleAtFraction(1f))
        }
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Kobi side's set (P.17): which picture stands in for one that has not come, and the order to fetch them in. */
class KobiSetTest {
    private val lights = listOf("dawn" to false, "morning" to false, "noon" to false, "after" to false, "sunset" to false, "blue" to false, "night" to true)

    @Test fun theLightItselfWhenItIsHere() {
        assertEquals("noon", KobiSet.Rules.nearest(lights, setOf("noon", "night"), "noon"))
    }

    @Test fun nothingHereMeansNoSide() {
        assertNull(KobiSet.Rules.nearest(lights, emptySet(), "noon"))
    }

    @Test fun theNearestOfTheSameKind() {
        // noon is missing: the day's nearer picture, not the night's
        assertEquals("morning", KobiSet.Rules.nearest(lights, setOf("morning", "night", "sunset"), "noon"))
        assertEquals("after", KobiSet.Rules.nearest(lights, setOf("after", "night"), "noon"))
    }

    @Test fun aNightPictureStandsInOnlyWhenNoDayPictureIsHere() {
        assertEquals("dawn", KobiSet.Rules.nearest(lights, setOf("night", "dawn"), "blue"))
        assertEquals("night", KobiSet.Rules.nearest(lights, setOf("night"), "noon"))
        // for the night itself, the nearer picture of the evening
        assertEquals("blue", KobiSet.Rules.nearest(lights, setOf("dawn", "blue"), "night"))
    }

    @Test fun fetchStartsWithTheLightAskedForThenTheNearOnes() {
        val o = KobiSet.Rules.order(lights, "noon")
        assertEquals("noon", o.first())
        assertEquals(lights.map { it.first }.toSet(), o.toSet())
        assertEquals(7, o.size)
        // the night picture, a different kind, comes last
        assertEquals("night", o.last())
    }

    @Test fun theSidesLayerIsPackedAndHasTheRunsOfTheSide() {
        val d = PeakData.parse(File("src/main/assets/redesign/peak-poc.json").readText())
        val k = PeakData.kobiView(File("src/main/assets/redesign/peak-kobi.json").readText(), d.geo)
        assertEquals(390f, k.w, 0f); assertEquals(844f, k.h, 0f)
        assertTrue("Kobi can be chosen on its side", "Kobi" in k.sel)
        assertTrue(k.pistes.any { it.key == "Kobi" } && k.lifts.any { it.name == "Kobi" })
    }
}

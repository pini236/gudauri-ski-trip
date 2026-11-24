package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * K17, the village as a painted picture (the architect's conditions, docs/ARCHITECTURE.md, 24.11.2026): every painted
 * picture of the period (the mountain, the close-up and the village) at most 1.75 MB packed, village.json at most 250 KB,
 * and the village read in the mountain's own form.
 */
class VillageTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/android/app/src/main/assets/redesign").isDirectory }
    private val assets = File(root, "app/android/app/src/main/assets/redesign")

    @Test fun everyPaintedPictureTogetherIsWithinTheCeiling() {
        val pics = listOf("img", "village/img").flatMap { d -> File(assets, d).listFiles()?.toList().orEmpty() }.filter { f -> f.isFile }
        val total = pics.sumOf { it.length() }
        assertTrue("the painted pictures are $total bytes, over 1.75 MB", total <= 1_750_000L)
        assertTrue(pics.any { it.name.startsWith("vill-") })
    }

    @Test fun theVillageDataIsWithinItsCeilingAndHasEveryLight() {
        val f = File(assets, "village/village.json")
        assertTrue("village.json is ${f.length()} bytes, over 250 KB", f.length() <= 250_000L)
        val v = JSONObject(f.readText()).getJSONObject("village")
        val images = v.getJSONObject("images")
        for (light in listOf("dawn", "morning", "noon", "after", "sunset", "blue", "night"))
            assertTrue("no picture for $light", File(assets, "village/" + images.getString(light)).isFile)
    }

    @Test fun theVillageReadsAsAView() {
        val view = PeakData.village(File(assets, "village/village.json").readText())
        assertEquals(390f, view.w)
        assertTrue(view.details.containsKey("day") && view.details.containsKey("night"))
        assertTrue(view.lifts.isNotEmpty() && view.pistes.isNotEmpty())
        assertTrue(view.sel.isEmpty())
    }

    @Test fun theFlightGoesToThePicturesOwnView() {
        val pose = VillageMarks.parse(File(assets, "village/village.json").readText()).pose!!
        // tx, ty, tz, dist, yaw and pitch as the handoff gives them (step 1 of the move)
        assertEquals(-420f, pose[0], .01f); assertEquals(4780f, pose[2], .01f)
        assertEquals(1150f, pose[3], .01f); assertEquals(0f, pose[4], .01f); assertEquals(36f, pose[5], .01f)
    }
}

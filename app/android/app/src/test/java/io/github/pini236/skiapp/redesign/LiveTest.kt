package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Phase B's pure parts (the architect's conditions, docs/ARCHITECTURE.md): the live model opens from the picture's
 * side, and the designer's handoff (design/handoff/2026-11-20-redesign-live): its values, the light by the sun's
 * height, the texel checks and the softened shadow.
 */
class LiveTest {
    private val d = PeakData.parse(File("src/main/assets/redesign/peak-poc.json").readText())

    @Test fun theLiveModelOpensFromThePicturesSide() {
        // the picture looks north: of two points on one meridian, the northern one is farther (higher on the screen)
        val p = d.hero.proj!!
        for (c in d.check) {
            val south = p.project(c.lat, c.lon, c.z) ?: continue; val north = p.project(c.lat + 0.004, c.lon, c.z) ?: continue
            assertTrue("north of ${c.what} is farther in the picture", north.y < south.y)
        }
        // and yaw 0 of the map's camera looks north (map/Camera.kt: forward is -z; data/Geo.kt: z grows south)
        assertEquals(0f, LiveOpen.YAW, 0f)
    }

    private val live = LiveData.parse(File("src/main/assets/redesign/live/peak-live.json").readText())
    private val up = floatArrayOf(0f, 1f, 0f)
    private fun light(alt: Float, am: Boolean = true, moon: Boolean = true) = LiveLight.at(live.keys, alt, am, floatArrayOf(.3f, .2f, -.9f), up, moon)

    @Test fun theHandoffIsReadAsTheDesignerWroteIt() {
        assertEquals(7, live.keys.size)
        assertEquals(listOf("dawn", "morning", "noon", "after", "sunset", "blue", "night"), live.keys.map { it.name })
        assertEquals(50f, live.camera.fovDeg, 0f); assertEquals(16f, live.camera.pitchDeg, 0f); assertEquals(0f, live.camera.yawDeg, 0f)
        assertEquals(LiveOpen.YAW, Math.toRadians(live.camera.yawDeg.toDouble()).toFloat(), 0f)
        assertTrue(live.village.size % 3 == 0 && live.village.isNotEmpty())
        // every texture the shader reads is in the test build, and every check names one of them
        for (t in LiveData.TEXTURES) assertTrue(t, File("src/main/assets/redesign/live/$t.webp").exists())
        assertTrue(live.checks.all { it.tex in LiveData.TEXTURES })
        // the five, packed, inside the architect's 1 MB
        assertTrue(LiveData.TEXTURES.sumOf { File("src/main/assets/redesign/live/$it.webp").length() } <= 1L shl 20)
    }

    @Test fun eachKeyIsItselfAtItsOwnHeight() {
        for (k in live.keys.filter { it.name != "night" }) {
            val m = light(k.alt, am = k.side == "am")
            for (i in 0..2) { assertEquals(k.name, k.lit[i], m.lit[i], 1e-4f); assertEquals(k.name, k.hor[i], m.hor[i], 1e-4f) }
            assertEquals(k.name, k.direct, m.direct, 1e-4f)
        }
        // above noon's height (the end of the season) it is noon; deep under the horizon it is night
        val noon = live.keys.first { it.name == "noon" }; val night = live.keys.first { it.name == "night" }
        assertEquals(noon.lit[1], light(40f).lit[1], 0f)
        assertEquals(night.zen[2], light(-20f).zen[2], 0f); assertEquals(1f, light(-20f).stars, 0f)
    }

    @Test fun theLightChangesSmoothlyThroughTheDay() {
        // no jump anywhere between -20 and 30 degrees, on either side of noon
        for (am in listOf(true, false)) {
            var prev = light(-20f, am)
            var a = -20f
            while (a < 30f) {
                a += .1f
                val m = light(a, am)
                for (i in 0..2) assertTrue("$am $a", abs(m.lit[i] - prev.lit[i]) < .03f && abs(m.zen[i] - prev.zen[i]) < .03f)
                assertTrue("$am $a glow", abs(m.glow - prev.glow) < .1f)
                prev = m
            }
        }
        // the pink band only around dawn and sunset; the alpenglow only with a low sun
        assertEquals(1f, light(3.7f).bandOn, 1e-4f); assertEquals(0f, light(16.5f).bandOn, 1e-4f)
        assertTrue(light(3f).glow > .9f); assertEquals(0f, light(10f).glow, 0f)
        // without the moon the night is dimmer
        assertTrue(light(-20f, moon = false).direct < light(-20f).direct)
        // the light of a low sun comes from at least 1.2 degrees above the horizon
        val low = LiveLight.at(live.keys, 0f, true, floatArrayOf(1f, 0f, 0f), up, true)
        assertEquals(Math.sin(Math.toRadians(1.2)).toFloat(), low.dir[1], 1e-5f)
    }

    @Test fun theTexelCheckCatchesATextureUpsideDown() {
        // 4 by 2: the top row red, the bottom row blue
        val w = 4; val h = 2; val px = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) { val i = (y * w + x) * 4; px[i] = if (y == 0) 200.toByte() else 0; px[i + 2] = if (y == 0) 0 else 200.toByte(); px[i + 3] = -1 }
        val top = LiveData.Check("relief", .5f, .25f, intArrayOf(200, 0, 0))
        assertTrue(LiveTex.check(px, w, h, top))
        assertTrue(!LiveTex.check(px, w, h, LiveData.Check("relief", .5f, .75f, intArrayOf(200, 0, 0))))
        // halving (for a GPU whose largest texture is small) keeps the average
        val half = LiveTex.half(LiveTex("horizon", w, h, px))
        assertEquals(2, half.w); assertEquals(1, half.h); assertEquals(100, half.px[0].toInt() and 255)
    }

    @Test fun theSoftenedShadowKeepsItsLevelAndLosesItsSteps() {
        val nx = 5; val ny = 5
        val flat = FloatArray(nx * ny) { .6f }
        assertTrue(LiveLight.soften(flat, nx, ny).all { abs(it - .6f) < 1e-6f })
        val step = FloatArray(nx * ny) { if (it % nx >= 2) 1f else 0f }
        val s = LiveLight.soften(step, nx, ny)
        assertEquals(.25f, s[2 * nx + 1], 1e-6f); assertEquals(.75f, s[2 * nx + 2], 1e-6f)
    }
}

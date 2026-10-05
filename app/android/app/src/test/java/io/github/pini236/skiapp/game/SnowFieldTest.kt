package io.github.pini236.skiapp.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Fresh snow's surface (game/SnowField.kt), the site's height field: presses, the rim, the snowcat and new snow. */
class SnowFieldTest {
    private fun field() = SnowField(80, 120, Random(3))

    @Test fun aFingerPressesTheSnowDownAndALittleUpAroundIt() {
        val f = field()
        val c = 60 * 80 + 40
        val moved = f.press(40f, 60f, 6f, 6f, 0f, 1.3f)
        assertTrue(moved > 0)
        assertTrue("the middle is lower", f.h[c] < f.base[c] - 1f)
        assertTrue(f.touched[c] != 0.toByte())
        // the rim, a little past the stamp: powder rises there
        val rim = 60 * 80 + 40 + 8
        assertTrue("the rim rises", f.h[rim] > f.base[rim])
        assertTrue(f.tracks() > 0)
    }

    @Test fun theFrozenCrustCracksAndTheSnowcatSmoothsItAgain() {
        val f = field(); f.kind = SnowKind.CRUST
        f.press(40f, 60f, 13f, 13f, 0f, 1.7f)
        assertTrue(f.crack.any { it })
        f.groom(40f, 60f, 0f) // the blade across the middle
        val c = 60 * 80 + 40
        assertTrue("flattened near the base, with corduroy", kotlin.math.abs(f.h[c] - (f.base[c] - .1f)) < .05f)
        assertTrue(f.touched[c] == 0.toByte() && !f.crack[c])
    }

    @Test fun newSnowFillsEveryTrackBackIn() {
        val f = field()
        f.press(40f, 60f, 6f, 6f, 0f, 1.3f)
        repeat(400) { f.fill(.05f) }
        assertEquals(0, f.tracks())
        assertTrue(f.h.indices.all { f.h[it] == f.base[it] })
    }

    @Test fun aBootLeavesASoleNotABowl() {
        assertEquals(0f, SnowField.boot(0.9f, 0.9f), 0f) // beside the heel
        assertTrue(SnowField.boot(0f, -.38f) > 0) // the toe
        assertTrue(SnowField.boot(0f, .55f) > 0) // the heel
    }

    @Test fun theLightIsBrightOnFreshSnow() {
        val f = field(); val px = IntArray(80 * 120)
        f.render(px)
        val c = px[60 * 80 + 40]
        assertTrue((c shr 16 and 0xFF) > 195 && (c and 0xFF) > 200 && (c and 0xFF) >= (c shr 16 and 0xFF))
    }
}

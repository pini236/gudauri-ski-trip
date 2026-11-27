package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "מסלולים" button's picture geometry (run button v4, A): the crop on the peak, and the window covering it. */
class RunsButtonTest {
    @Test fun cropIsTheWindowAtHalfScaleCentredOnThePeak() {
        // the hero picture at 3 pixels a point, 390 points wide: 264 by 96 points is 792 by 288 pixels around (128, 172)
        assertArrayEquals(intArrayOf(0, 372, 792, 288), RunsCrop.rect(1170, 2532, 390f, true))
        // at the other end: around (262, 176), cut at the picture's edge
        assertArrayEquals(intArrayOf(378, 384, 792, 288), RunsCrop.rect(1170, 2532, 390f, false))
    }

    @Test fun cropStaysInsideThePicture() {
        for (end in listOf(true, false)) for (w in listOf(585, 1170, 1560)) {
            val h = w * 2532 / 1170
            val r = RunsCrop.rect(w, h, 390f, end)
            assertTrue("crop outside for $w $end", r[0] >= 0 && r[1] >= 0 && r[0] + r[2] <= w && r[1] + r[3] <= h)
        }
    }

    @Test fun windowCoversWithoutStretching() {
        // 132 by 48 (aspect 2.75) over the 2.75 crop: all of it
        assertArrayEquals(intArrayOf(0, 0, 792, 288), RunsCrop.cover(792, 288, 132f / 48f))
        // narrow, 84 by 48: the middle of it, full height
        val n = RunsCrop.cover(792, 288, 84f / 48f)
        assertEquals(288, n[3]); assertEquals(504, n[2]); assertEquals(144, n[0])
        // pressed, 150 by 48 (wider than the crop): full width, cut from the top and bottom
        val w = RunsCrop.cover(792, 288, 150f / 48f)
        assertEquals(792, w[2]); assertEquals(0, w[0]); assertTrue(w[3] < 288)
    }
}

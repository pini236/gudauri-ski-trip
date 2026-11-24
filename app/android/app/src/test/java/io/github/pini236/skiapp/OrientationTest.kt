package io.github.pini236.skiapp

import android.content.pm.ActivityInfo
import io.github.pini236.skiapp.ui.Orientation
import org.junit.Assert.assertEquals
import org.junit.Test

/** Redesign part 8: a phone upright, a tablet (600 dp and over) free; the store build untouched for now. */
class OrientationTest {
    @Test fun aPhoneStaysUprightATabletTurns() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Orientation.wanted(411, enabled = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Orientation.wanted(599, enabled = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, Orientation.wanted(600, enabled = true))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, Orientation.wanted(800, enabled = true))
    }

    @Test fun withoutTheNewDesignNothingIsLocked() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, Orientation.wanted(411, enabled = false))
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

/** Part 8, large text: the huge numbers grow with the phone to 130 percent and no further. */
class BigTextTest {
    @Test fun aHugeNumberStopsAt130Percent() {
        assertEquals(120f, BigText.hugeSp(120f, 1f), 1e-4f)
        assertEquals(120f, BigText.hugeSp(120f, 1.3f), 1e-4f)
        // at 200 percent the screen shows 120 x 1.3, not 240
        assertEquals(120f * 1.3f, BigText.hugeSp(120f, 2f) * 2f, 1e-3f)
    }
}

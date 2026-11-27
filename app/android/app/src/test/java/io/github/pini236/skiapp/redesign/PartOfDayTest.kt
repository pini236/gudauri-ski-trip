package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

/** Round 4, board L07: the timeline's three chapters, morning before 12:00, midday until 14:00, afternoon after. */
class PartOfDayTest {
    @Test fun theChaptersSplitAtNoonAndTwo() {
        assertEquals(0, partOfDay(9))
        assertEquals(0, partOfDay(11))
        assertEquals(1, partOfDay(12))
        assertEquals(1, partOfDay(13))
        assertEquals(1, partOfDay(14))
        assertEquals(1, partOfDay(16))
    }
}

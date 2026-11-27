package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRulesTest {
    private val keys = listOf("first_color", "fall", "steepest", "fastest", "longest_descent", "offpiste", "queue")

    @Test fun nothingDroppedKeepsTheStory() {
        val (c, m) = StoryRules.fill(keys, listOf(0, 2, 3), { true })
        assertEquals(listOf(0, 2, 3), c)
        assertEquals(listOf(4, 6), m)
    }

    @Test fun hiddenKeyIsReplacedByTheNextMoreItem() {
        val (c, m) = StoryRules.fill(keys, listOf(0, 1, 2), { true })
        assertEquals(listOf(0, 3, 2), c)   // "fall" gives way to the first non-story item
        assertEquals(listOf(4, 6), m)
    }

    @Test fun keyWithoutWordsIsReplaced() {
        val (c, _) = StoryRules.fill(keys, listOf(0, 2), { it != 2 })
        assertEquals(listOf(0, 3), c)
    }

    @Test fun noHolesWhenNothingToBackfill() {
        val (c, m) = StoryRules.fill(listOf("steepest", "fall"), listOf(0, 1), { true })
        assertEquals(listOf(0), c)
        assertTrue(m.isEmpty())
    }

    @Test fun moreNeverHoldsHiddenOrWordless() {
        val (_, m) = StoryRules.fill(keys, listOf(0), { it != 6 })
        assertEquals(listOf(2, 3, 4), m)
    }

    @Test fun tapHalvesFollowTheLayoutDirection() {
        assertTrue(StoryRules.tapBack(10f, 100f, rtl = false))
        assertFalse(StoryRules.tapBack(90f, 100f, rtl = false))
        assertTrue(StoryRules.tapBack(90f, 100f, rtl = true))
        assertFalse(StoryRules.tapBack(10f, 100f, rtl = true))
    }

    @Test fun nextAndPrev() {
        assertEquals(1, StoryRules.next(0, 3)); assertNull(StoryRules.next(2, 3))
        assertEquals(0, StoryRules.prev(0)); assertEquals(1, StoryRules.prev(2))
    }

    @Test fun segments() {
        assertEquals(1f, StoryRules.segmentFill(0, 2, .3f), 0f)
        assertEquals(.3f, StoryRules.segmentFill(2, 2, .3f), 0f)
        assertEquals(0f, StoryRules.segmentFill(3, 2, .3f), 0f)
        assertEquals(1f, StoryRules.segmentFill(2, 2, 7f), 0f)
    }

    @Test fun buttonStates() {
        assertEquals(StoryRules.Btn.BUSY, StoryRules.button("save", "save", false, null))
        assertEquals(StoryRules.Btn.IDLE, StoryRules.button("share", "save", true, null))
        assertEquals(StoryRules.Btn.SAVED, StoryRules.button("save", null, true, null))
        assertEquals(StoryRules.Btn.FAILED, StoryRules.button("share", null, false, "share"))
        assertEquals(StoryRules.Btn.IDLE, StoryRules.button("share", null, false, "save"))
    }

    @Test fun fillOfABusyButton() {
        assertEquals(1f, StoryRules.fill(false, null), 0f)
        assertEquals(0f, StoryRules.fill(true, null), 0f)
        assertEquals(.64f, StoryRules.fill(true, .64f), 0f)
    }

    @Test fun pinShowsDegreesOrSpeedOnly() {
        assertEquals("28°", StoryRules.pin("steepest", 28, null))
        assertEquals("68", StoryRules.pin("fastest", null, 68))
        assertNull(StoryRules.pin("queue", 28, 68))
        assertNull(StoryRules.pin("steepest", null, null))
    }

    @Test fun easeRunsFromZeroToOneAndNeverBackwards() {
        assertEquals(0f, StoryRules.ease(0f), 1e-6f)
        assertEquals(1f, StoryRules.ease(1f), 1e-6f)
        assertEquals(1f, StoryRules.ease(3f), 1e-6f)
        var last = 0f
        for (i in 1..10) { val v = StoryRules.ease(i / 10f); assertTrue(v >= last); last = v }
        assertTrue(StoryRules.ease(.5f) > .5f)
    }

    @Test fun soloOnlyForOneCard() {
        assertTrue(StoryRules.solo(1)); assertFalse(StoryRules.solo(0)); assertFalse(StoryRules.solo(3))
    }

    @Test fun clockReads() {
        assertEquals("0:25 / 1:00", StoryRules.clockPair(25_000, 60_000))
        assertEquals("0:00", StoryRules.clock(-5))
        assertEquals(.5f, StoryRules.videoFrac(30_000, 60_000), 1e-6f)
        assertEquals(0f, StoryRules.videoFrac(10, 0), 1e-6f)
    }

    @Test fun secondButtonQueuesAfterTheRunningOne() {
        assertEquals(StoryRules.Press.RUN, StoryRules.press("save", null, null))
        assertEquals(StoryRules.Press.QUEUE, StoryRules.press("share", "save", null))
        assertEquals(StoryRules.Press.IGNORE, StoryRules.press("save", "save", null))
        assertEquals(StoryRules.Press.IGNORE, StoryRules.press("share", "save", "share"))
    }

    @Test fun cardToggleAndReplay() {
        assertEquals(StoryRules.Film.PAUSED, StoryRules.toggle(StoryRules.Film.PLAYING))
        assertEquals(StoryRules.Film.PLAYING, StoryRules.toggle(StoryRules.Film.PAUSED))
        assertEquals(StoryRules.Film.PLAYING, StoryRules.toggle(StoryRules.Film.ENDED))
    }
}

class ShareCardSizeTest {
    @org.junit.Test fun pictureIsFourByFive() {
        val (w, h) = StoryRules.cardSize(300f, 400f, false)
        org.junit.Assert.assertEquals(300f, w, .01f); org.junit.Assert.assertEquals(375f, h, .01f)
    }
    @org.junit.Test fun filmFillsFreeHeightAtNineBySixteen() {
        val (w, h) = StoryRules.cardSize(300f, 400f, true)
        org.junit.Assert.assertEquals(400f, h, .01f); org.junit.Assert.assertEquals(225f, w, .01f)
    }
    @org.junit.Test fun filmNeverWiderThanTheSpace() {
        val (w, h) = StoryRules.cardSize(300f, 900f, true)
        org.junit.Assert.assertEquals(300f, w, .01f); org.junit.Assert.assertEquals(533.33f, h, .01f)
    }
}

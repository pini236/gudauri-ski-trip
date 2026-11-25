package io.github.pini236.skiapp.redesign

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class TagLayoutTest {
    @Test fun aFreeTagStays() {
        val dy = TagLayout.place(listOf(Rect(0f, 0f, 100f, 24f)), listOf(Rect(0f, 100f, 80f, 122f)), 26f, 2, 3f)
        assertEquals(0f, dy[0], 0f)
    }

    @Test fun aTagOnAPinTakesTheNearestFreeStep() {
        val dy = TagLayout.place(listOf(Rect(0f, 100f, 100f, 124f)), listOf(Rect(20f, 105f, 90f, 127f)), 26f, 2, 3f)
        // up one step (79..101) still touches the pin with its gap (97..127); down one step (131..153) is free
        assertEquals(26f, dy[0], 0f)
    }

    @Test fun twoTagsDoNotLandOnEachOther() {
        val pin = Rect(0f, 100f, 100f, 124f)
        val a = Rect(20f, 140f, 90f, 162f); val b = Rect(30f, 145f, 95f, 167f)
        val dy = TagLayout.place(listOf(pin), listOf(a, b), 26f, 2, 3f)
        assertEquals(0f, dy[0], 0f)
        val bb = b.translate(0f, dy[1]); val aa = a.translate(0f, dy[0])
        assert(!aa.inflate(3f).overlaps(bb)) { "b=$bb a=$aa" }
        assert(!pin.inflate(3f).overlaps(bb)) { "b=$bb" }
    }

    @Test fun withNoFreePlaceTheTagStays() {
        val wall = Rect(0f, -1000f, 200f, 1000f)
        val dy = TagLayout.place(listOf(wall), listOf(Rect(10f, 10f, 50f, 30f)), 26f, 2, 3f)
        assertEquals(0f, dy[0], 0f)
    }
}

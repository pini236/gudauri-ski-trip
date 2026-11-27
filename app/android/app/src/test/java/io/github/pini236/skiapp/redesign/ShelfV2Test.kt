package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfV2Test {
    private fun r(key: String, len: Int, steep: Int = 15, color: String = "blue", lift: String? = "A", back: String? = "B", min: String? = "4") =
        ShelfRun(key, key, color, "run", len, 200, steep, lift, back, min, 2500)

    private val runs = listOf(r("a", 1000, 12), r("b", 3000, 30, "red"), r("c", 2000, 20, "red", lift = "C"))

    @Test fun openFirstThenClosedThenUnknown() {
        val g = Shelf.byOpen(runs, { 0f }) { k -> when (k) { "a" -> true; "b" -> false; else -> null } }
        assertEquals(listOf("a", "b", "c"), g.flatMap { it.keys })
        assertEquals(listOf<Boolean?>(true, false, null), g.map { it.open })
    }

    @Test fun snowMostFirst() {
        val g = Shelf.bySnow(runs) { k -> when (k) { "a" -> 2.0; "b" -> 14.0; else -> 0.0 } }
        assertEquals(listOf("b", "a", "c"), g.single().keys)
    }

    @Test fun litAndRanks() {
        val flat = Shelf.flat(Shelf.longest(runs))
        assertEquals(setOf("b", "c", "a"), Shelf.lit(ShelfSort.LONG, null, flat))
        assertEquals(listOf("b", "c", "a"), Shelf.ranks(ShelfSort.LONG, flat))
        assertTrue(Shelf.ranks(ShelfSort.LIFT, flat).isEmpty())
        val g = ShelfGroup(listOf("a", "c"), lift = "A")
        assertEquals(setOf("a", "c"), Shelf.lit(ShelfSort.LIFT, g, flat))
        assertTrue(Shelf.lit(ShelfSort.SUN, g, flat).isEmpty())
    }

    @Test fun tickHeightAndHit() {
        assertEquals(12f, Shelf.tickHeight(0f, 0f, 10f), 0f); assertEquals(30f, Shelf.tickHeight(10f, 0f, 10f), 0f)
        assertEquals(20f, Shelf.tickHeight(3f, 3f, 3f), 0f)
        assertEquals(0, Shelf.tickAt(-5f, 100f, 10, false)); assertEquals(9, Shelf.tickAt(500f, 100f, 10, false))
        assertEquals(9, Shelf.tickAt(2f, 100f, 10, true)); assertEquals(4, Shelf.tickAt(48f, 100f, 10, false))
    }

    @Test fun answersFollowTheTable() {
        val a = r("a", 1500, 21)
        assertEquals(ShelfAnswer("4", AUnit.MIN, Cap.BACK, "B"), ShelfAnswers.answer(ShelfSort.LIFT, a, 1, 3, null, null, null))
        assertEquals(ShelfAnswer("21", AUnit.DEG, Cap.STEEP), ShelfAnswers.answer(ShelfSort.EASY, a, 1, 3, null, null, null))
        assertEquals(ShelfAnswer("1.5", AUnit.KM, Cap.RANK, "7", "28"), ShelfAnswers.answer(ShelfSort.LONG, a, 7, 28, null, null, null))
        assertEquals(ShelfAnswer("42", AUnit.PCT, Cap.SUN), ShelfAnswers.answer(ShelfSort.SUN, a, 1, 3, .42f, null, null))
        assertEquals(ShelfAnswer("", AUnit.WORD_OPEN, Cap.OPEN, "A"), ShelfAnswers.answer(ShelfSort.OPEN, a, 1, 3, null, true, null))
        assertEquals(AUnit.WORD_CLOSED, ShelfAnswers.answer(ShelfSort.OPEN, a, 1, 3, null, false, null).unit)
        assertEquals(ShelfAnswer("12", AUnit.CM, Cap.SNOW), ShelfAnswers.answer(ShelfSort.SNOW, a, 1, 3, null, null, 12.4))
        assertEquals(Cap.NONE, ShelfAnswers.answer(ShelfSort.OPEN, a, 1, 3, null, null, null).cap)
    }

    @Test fun ticksMarkGroupStartsAndTones() {
        val flat = Shelf.flat(Shelf.byLift(runs) { 0f })
        val t = ShelfAnswers.ticks(ShelfSort.LIFT, flat, runs.associateBy { it.key }, { null }, { null }, { null })
        assertEquals(3, t.size); assertEquals(1, t.count { it.groupStart }); assertTrue(t.all { it.h == 20f })
        val o = ShelfAnswers.ticks(ShelfSort.OPEN, flat, runs.associateBy { it.key }, { null }, { k -> if (k == "a") true else null }, { null })
        assertEquals(Tone.OPEN, o.first { it.key == "a" }.tone); assertEquals(Tone.UNKNOWN, o.first { it.key == "b" }.tone)
    }

    @Test fun pictureRaisedKeepsItsBottom() {
        val b = Place(2f, -10f, 0f) // a picture 400 high at k=2 on a 800 high screen: bottom 800
        val p = PeakGeo.raised(Place(2f, -10f, 0f), 400f, 390f, 800f, 120f)
        assertEquals(800f, p.y + 400f * p.k, .01f)
        assertEquals(b, PeakGeo.raised(b, 400f, 390f, 800f, 0f))
    }
}

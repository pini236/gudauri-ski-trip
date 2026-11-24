package io.github.pini236.skiapp.redesign

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * K07: the shelf's orders match the designer's, on today's data (design/handoff/2026-11-24-redesign-village-shelf-lenses,
 * village.json's "shelf", there for checking only: the app computes them).
 */
class ShelfRulesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "design/handoff").isDirectory }
    private val shelf = JSONObject(File(root, "design/handoff/2026-11-24-redesign-village-shelf-lenses/village.json").readText()).getJSONObject("shelf")
    private val runs = shelf.getJSONArray("runs").let { a ->
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            fun s(k: String) = if (o.isNull(k)) null else o.get(k).toString()
            ShelfRun(o.getString("key"), o.getString("name"), o.getString("color"), o.getString("kind"),
                o.getInt("len"), o.getInt("drop"), o.getInt("steep"), s("group"), s("back"), s("back_min"))
        }
    }
    private fun list(k: String) = shelf.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }

    @Test fun byLiftFromTheVillageUpAndTheRunsWithNoLiftLast() {
        val want = shelf.getJSONArray("by_lift").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val bottoms = want.filter { !it.isNull("lift") }.associate { it.getString("lift") to it.getDouble("bottom_m").toFloat() }
        val got = Shelf.byLift(runs, bottoms::get)
        assertEquals(want.map { if (it.isNull("lift")) null else it.getString("lift") }, got.map { it.lift })
        assertEquals(want.map { o -> o.getJSONArray("runs").let { a -> (0 until a.length()).map { a.getString(it) } } }, got.map { it.keys })
    }

    @Test fun easyToHardByColourThenTheSteepestStretch() {
        val got = Shelf.easyToHard(runs)
        assertEquals(list("easy_to_hard"), got.flatMap { it.keys })
        assertEquals(listOf("green", "blue", "red", "black"), got.map { it.color })
    }

    @Test fun theLongestFirst() = assertEquals(list("longest"), Shelf.longest(runs).single().keys)

    @Test fun inTheSunAtLeastHalfThenTheShade() {
        val share = mapOf("Tatra 1" to .9f, "Kobi" to .5f, "Pirveli" to .2f)
        val g = Shelf.bySun(runs.filter { it.key in share }, { share.getValue(it) })
        assertEquals(listOf(listOf("Tatra 1", "Kobi"), listOf("Pirveli")), g.map { it.keys })
        assertEquals(listOf(true, false), g.map { it.sun })
        // the sun down: "in the sun now" is off, and the shelf is by lift
        assertEquals(Shelf.byLift(runs) { null }, Shelf.groups(ShelfSort.SUN, runs, { null }, null))
    }

    @Test fun theCardItOpensOnAndKeepsAcrossOrders() {
        val flat = Shelf.flat(Shelf.easyToHard(runs))
        assertEquals(flat.indexOfFirst { it.first == "Kudebi 1" }, Shelf.start(flat, "Kudebi 1", "Tatra 1", null))
        assertEquals(flat.indexOfFirst { it.first == "Tatra 1" }, Shelf.start(flat, null, "Tatra 1", null))
        val byLift = Shelf.flat(Shelf.byLift(runs) { null })
        assertEquals(byLift.indexOfFirst { it.second.lift == "Kudebi" }, Shelf.start(byLift, null, null, "Kudebi"))
        assertEquals(0, Shelf.start(flat, null, null, null))
        val long = Shelf.flat(Shelf.longest(runs))
        assertEquals("Sadzele 1", long[Shelf.keep(long, "Sadzele 1")].first)
    }

    @Test fun lengthsInKilometresOrUnderAThousandInMetres() {
        assertEquals("2.5" to true, Shelf.length(2515))
        assertEquals("700" to false, Shelf.length(696))
        assertEquals("160" to false, Shelf.length(162))
        assertEquals(ShelfSort.LIFT, ShelfSort.of("nope"))
    }
}

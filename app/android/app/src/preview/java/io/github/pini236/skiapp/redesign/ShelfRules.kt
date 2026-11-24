package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.RunFacts

/**
 * K07, the shelf of runs ("where to?", design/handoff/2026-11-24-redesign-village-shelf-lenses, section 2): the named runs
 * as cards in one row, in one of four orders. No typing and no search (decision 50).
 */
internal enum class ShelfSort(val id: String) {
    LIFT("lift"), EASY("easy"), LONG("long"), SUN("sun");

    companion object { fun of(id: String?): ShelfSort = values().firstOrNull { it.id == id } ?: LIFT }
}

/**
 * One card: [steep] the steepest stretch in whole degrees, [lift] the first lift that brings you to its top (its group),
 * [back] the first lift at its bottom and its ride minutes as the data has them.
 */
internal data class ShelfRun(
    val key: String, val title: String, val color: String, val kind: String,
    val len: Int, val drop: Int, val steep: Int, val lift: String?, val back: String?, val backMin: String?,
)

/**
 * A group of cards with its title: by lift ([lift], null for the runs with no lift in the data), by colour ([color]),
 * the longest (one group, no title of its own), or in the sun and in the shade ([sun]).
 */
internal data class ShelfGroup(val keys: List<String>, val lift: String? = null, val color: String? = null, val sun: Boolean? = null)

internal object Shelf {
    val COLORS = listOf("green", "blue", "red", "black")
    private fun colorAt(c: String) = COLORS.indexOf(c).let { if (it < 0) COLORS.size else it }
    private val byColorThenName = compareBy<ShelfRun> { colorAt(it.color) }.then { a, b -> RunFacts.natural(a.title, b.title) }

    /**
     * By lift: a group for each lift, from the village up (by the height of its bottom station, [bottom]); a run belongs
     * to the first lift of its fromLifts, so each run shows once. In a group: green, blue, red, black, then by name. The
     * runs with no lift in the data (parts of the map) last, without guessing a lift for them.
     */
    fun byLift(runs: List<ShelfRun>, bottom: (String) -> Float?): List<ShelfGroup> {
        val (with, without) = runs.partition { it.lift != null }
        val groups = with.groupBy { it.lift!! }.entries
            .sortedWith(compareBy<Map.Entry<String, List<ShelfRun>>> { bottom(it.key) ?: Float.MAX_VALUE }.thenBy { it.key })
            .map { (l, rs) -> ShelfGroup(rs.sortedWith(byColorThenName).map { it.key }, lift = l) }
        return if (without.isEmpty()) groups else groups + ShelfGroup(without.sortedWith(byColorThenName).map { it.key })
    }

    /** Easy to hard: by colour; in a colour by the steepest stretch, the gentlest first, then by name. */
    fun easyToHard(runs: List<ShelfRun>): List<ShelfGroup> =
        runs.sortedWith(compareBy<ShelfRun> { colorAt(it.color) }.thenBy { it.steep }.then { a, b -> RunFacts.natural(a.title, b.title) })
            .groupBy { it.color }.map { (c, rs) -> ShelfGroup(rs.map { it.key }, color = c) }

    /** The longest: by length, the longest first (then by name). */
    fun longest(runs: List<ShelfRun>): List<ShelfGroup> =
        listOf(ShelfGroup(runs.sortedWith(compareByDescending<ShelfRun> { it.len }.then { a, b -> RunFacts.natural(a.title, b.title) }).map { it.key }))

    /**
     * In the sun now: the runs at least half in the sun ([sunShare], 0 to 1), the most in the sun first; then the rest in
     * the shade, by the same share. Null when the sun is down (the chip is off then).
     */
    fun bySun(runs: List<ShelfRun>, sunShare: (String) -> Float): List<ShelfGroup> {
        val s = runs.map { it to sunShare(it.key) }
        val order = compareByDescending<Pair<ShelfRun, Float>> { it.second }.then { a, b -> RunFacts.natural(a.first.title, b.first.title) }
        val (sun, shade) = s.partition { it.second >= .5f }
        return listOfNotNull(
            sun.takeIf { it.isNotEmpty() }?.let { g -> ShelfGroup(g.sortedWith(order).map { it.first.key }, sun = true) },
            shade.takeIf { it.isNotEmpty() }?.let { g -> ShelfGroup(g.sortedWith(order).map { it.first.key }, sun = false) },
        )
    }

    fun groups(sort: ShelfSort, runs: List<ShelfRun>, bottom: (String) -> Float?, sunShare: ((String) -> Float)?): List<ShelfGroup> = when (sort) {
        ShelfSort.LIFT -> byLift(runs, bottom)
        ShelfSort.EASY -> easyToHard(runs)
        ShelfSort.LONG -> longest(runs)
        ShelfSort.SUN -> sunShare?.let { bySun(runs, it) } ?: byLift(runs, bottom)
    }

    /** The cards in order, each with its group. */
    fun flat(groups: List<ShelfGroup>): List<Pair<String, ShelfGroup>> = groups.flatMap { g -> g.keys.map { it to g } }

    /**
     * Which card the shelf opens on: the run chosen last since the app opened; else, with "where am I" on a run or a
     * lift, that run or the first card of the lift's group; else the first card.
     */
    fun start(flat: List<Pair<String, ShelfGroup>>, last: String?, hereRun: String?, hereLift: String?): Int {
        fun at(k: String?) = k?.let { kk -> flat.indexOfFirst { it.first == kk } }?.takeIf { it >= 0 }
        return at(last) ?: at(hereRun) ?: hereLift?.let { l -> flat.indexOfFirst { it.second.lift == l }.takeIf { it >= 0 } } ?: 0
    }

    /** A new order keeps the middle card the same run (section 2); its place in the new row. */
    fun keep(flat: List<Pair<String, ShelfGroup>>, key: String?): Int = key?.let { k -> flat.indexOfFirst { it.first == k } }?.takeIf { it >= 0 } ?: 0

    /** The length on a card: km with one decimal, under 1,000 m in metres rounded to 10 (the caption "m"). */
    fun length(m: Int): Pair<String, Boolean> =
        if (m < 1000) (((m + 5) / 10) * 10).toString() to false else String.format(java.util.Locale.US, "%.1f", m / 1000.0) to true
}

package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.RunFacts

/**
 * K07, the shelf of runs ("where to?", design/handoff/2026-11-24-redesign-village-shelf-lenses, section 2): the named runs
 * as cards in one row, in one of four orders. No typing and no search (decision 50).
 */
internal enum class ShelfSort(val id: String) {
    LIFT("lift"), EASY("easy"), LONG("long"), SUN("sun"), OPEN("open"), SNOW("snow");

    /** The chips that paint the mountain by a lens (the other three light runs). */
    val lens: LensKind? get() = when (this) { SUN -> LensKind.SUN; OPEN -> LensKind.OPEN; SNOW -> LensKind.SNOW; else -> null }

    companion object { fun of(id: String?): ShelfSort = values().firstOrNull { it.id == id } ?: LIFT }
}

/**
 * One card: [steep] the steepest stretch in whole degrees, [lift] the first lift that brings you to its top (its group),
 * [back] the first lift at its bottom and its ride minutes as the data has them.
 */
internal data class ShelfRun(
    val key: String, val title: String, val color: String, val kind: String,
    val len: Int, val drop: Int, val steep: Int, val lift: String?, val back: String?, val backMin: String?,
    /** The run's top height in metres (the snow chip reads the forecast at it). */
    val top: Int = 0,
)

/**
 * A group of cards with its title: by lift ([lift], null for the runs with no lift in the data), by colour ([color]),
 * the longest (one group, no title of its own), or in the sun and in the shade ([sun]).
 */
internal data class ShelfGroup(val keys: List<String>, val lift: String? = null, val color: String? = null, val sun: Boolean? = null,
                               val open: Boolean? = null)

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

    /** Open first (by lift inside), then the closed, then the runs with no lift report (a group with no flag). */
    fun byOpen(runs: List<ShelfRun>, bottom: (String) -> Float?, runOpen: (String) -> Boolean?): List<ShelfGroup> {
        val order = byLift(runs, bottom).flatMap { it.keys }
        fun part(v: Boolean?) = order.filter { runOpen(it) == v }
        return listOf(true, false, null).mapNotNull { v -> part(v).takeIf { it.isNotEmpty() }?.let { ShelfGroup(it, open = v) } }
    }

    /** New snow: the most centimetres first (then by name); one group. */
    fun bySnow(runs: List<ShelfRun>, snowCm: (String) -> Double): List<ShelfGroup> =
        listOf(ShelfGroup(runs.sortedWith(compareByDescending<ShelfRun> { snowCm(it.key) }.then { a, b -> RunFacts.natural(a.title, b.title) }).map { it.key }))

    fun groups(sort: ShelfSort, runs: List<ShelfRun>, bottom: (String) -> Float?, sunShare: ((String) -> Float)?,
               runOpen: ((String) -> Boolean?)? = null, snowCm: ((String) -> Double)? = null): List<ShelfGroup> = when (sort) {
        ShelfSort.OPEN -> runOpen?.let { byOpen(runs, bottom, it) } ?: byLift(runs, bottom)
        ShelfSort.SNOW -> snowCm?.let { bySnow(runs, it) } ?: byLift(runs, bottom)
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

    /** What the mountain lights for the middle card: its group's runs by lift or by colour, the six longest by length; the lens chips light nothing (they paint). */
    fun lit(sort: ShelfSort, group: ShelfGroup?, flat: List<Pair<String, ShelfGroup>>): Set<String> = when (sort) {
        ShelfSort.LIFT, ShelfSort.EASY -> group?.keys?.toSet().orEmpty()
        ShelfSort.LONG -> flat.take(6).map { it.first }.toSet()
        else -> emptySet()
    }

    /** The six longest in rank order (1 to 6), for the discs on the mountain. */
    fun ranks(sort: ShelfSort, flat: List<Pair<String, ShelfGroup>>): List<String> = if (sort == ShelfSort.LONG) flat.take(6).map { it.first } else emptyList()

    /** A tick's height in dp (12 to 30) by its value between [lo] and [hi]; an even height when there is no spread. */
    fun tickHeight(v: Float, lo: Float, hi: Float): Float = if (hi - lo < 1e-6f) 20f else 12f + 18f * ((v - lo) / (hi - lo)).coerceIn(0f, 1f)

    /** Which tick a touch at [x] of [width] is on, among [n] (the first at the start side, [rtl] mirrored). */
    fun tickAt(x: Float, width: Float, n: Int, rtl: Boolean): Int {
        if (n <= 0 || width <= 0f) return 0
        val f = (x / width).coerceIn(0f, .9999f)
        return ((if (rtl) 1f - f else f) * n).toInt().coerceIn(0, n - 1)
    }

    /** The length on a card: km with one decimal, under 1,000 m in metres rounded to 10 (the caption "m"). */
    fun length(m: Int): Pair<String, Boolean> =
        if (m < 1000) (((m + 5) / 10) * 10).toString() to false else String.format(java.util.Locale.US, "%.1f", m / 1000.0) to true
}

/**
 * K06, "from the station" (the handoff's section 3): the runs that go down from a top station, those whose fromLifts
 * hold a lift that ends there (a shared station gathers every lift's runs, each run once), the longest first.
 */
internal object FromStation {
    fun runs(topOf: Collection<String>, all: List<Pair<ShelfRun, List<String>>>): List<ShelfRun> =
        all.filter { (_, from) -> from.any { it in topOf } }.map { it.first }.distinctBy { it.key }
            .sortedWith(compareByDescending<ShelfRun> { it.len }.then { a, b -> RunFacts.natural(a.title, b.title) })
}

/**
 * K06 opening by itself (the handoff's section 3): with "where am I" on, after a ride on a lift, once the location is off
 * the lift and within 80 m of that lift's top station; once for each ride.
 */
internal class StationAuto {
    private var riding: String? = null

    /** [onLift] the lift the location is on now (null off any lift); [toTop] metres from a lift's top station. The lift reached, once. */
    fun feed(onLift: String?, toTop: (String) -> Float?): String? {
        if (onLift != null) { riding = onLift; return null }
        val r = riding ?: return null
        val d = toTop(r) ?: return null
        if (d > 80f) return null
        riding = null
        return r
    }
}

/** What a strip tick is coloured by (the theme turns a tone into a colour). */
internal enum class Tone { RUN, SUN, SHADE, OPEN, CLOSED, UNKNOWN, SNOW_HI, SNOW_LO, NONE }

/** One tick of the strip: the run, its height in dp (12 to 30), its tone, and whether a group starts here (a wider gap). */
internal data class TickSpec(val key: String, val h: Float, val tone: Tone, val groupStart: Boolean)

/** The big number's unit, and the caption under it (the variables are in [ShelfAnswer.a] and [ShelfAnswer.b]). */
internal enum class AUnit { MIN, DEG, KM, M, PCT, CM, WORD_OPEN, WORD_CLOSED, NONE }
internal enum class Cap { BACK, STEEP, RANK, SUN, OPEN, CLOSED, SNOW, NONE }
internal data class ShelfAnswer(val num: String, val unit: AUnit, val cap: Cap, val a: String = "", val b: String = "")

internal object ShelfAnswers {
    /** The strip's ticks for [sort], in the cards' order; [sunShare] 0 to 1, [open] null for no report, [snowCm] for the run. */
    fun ticks(sort: ShelfSort, flat: List<Pair<String, ShelfGroup>>, by: Map<String, ShelfRun>, sunShare: (String) -> Float?,
              open: (String) -> Boolean?, snowCm: (String) -> Double?): List<TickSpec> {
        val rs = flat.mapNotNull { (k, g) -> by[k]?.let { Triple(it, g, it) } }
        fun spread(f: (ShelfRun) -> Float): Pair<Float, Float> = rs.map { f(it.first) }.let { (it.minOrNull() ?: 0f) to (it.maxOrNull() ?: 0f) }
        val (sl, sh) = spread { it.steep.toFloat() }; val (ll, lh) = spread { it.len.toFloat() }
        val snowMax = rs.maxOfOrNull { (snowCm(it.first.key) ?: 0.0).toFloat() } ?: 0f
        var prev: ShelfGroup? = null
        return rs.map { (r, g, _) ->
            val start = prev != null && prev !== g; prev = g
            when (sort) {
                ShelfSort.LIFT -> TickSpec(r.key, 20f, Tone.RUN, start)
                ShelfSort.EASY -> TickSpec(r.key, Shelf.tickHeight(r.steep.toFloat(), sl, sh), Tone.RUN, start)
                ShelfSort.LONG -> TickSpec(r.key, Shelf.tickHeight(r.len.toFloat(), ll, lh), Tone.RUN, start)
                ShelfSort.SUN -> sunShare(r.key).let { s -> TickSpec(r.key, Shelf.tickHeight(s ?: 0f, 0f, 1f), if ((s ?: 0f) >= .5f) Tone.SUN else Tone.SHADE, start) }
                ShelfSort.OPEN -> when (open(r.key)) {
                    true -> TickSpec(r.key, 30f, Tone.OPEN, start); false -> TickSpec(r.key, 12f, Tone.CLOSED, start); null -> TickSpec(r.key, 12f, Tone.UNKNOWN, start) }
                ShelfSort.SNOW -> (snowCm(r.key) ?: 0.0).let { c ->
                    TickSpec(r.key, Shelf.tickHeight(c.toFloat(), 0f, snowMax), when { c >= 10 -> Tone.SNOW_HI; c >= 1 -> Tone.SNOW_LO; else -> Tone.NONE }, start) }
            }
        }
    }

    /** The big number of [r] for [sort] (the table of the handoff, version 2). [rank] 1 based in the longest order, [n] the number of cards. */
    fun answer(sort: ShelfSort, r: ShelfRun, rank: Int, n: Int, sunShare: Float?, open: Boolean?, snowCm: Double?): ShelfAnswer = when (sort) {
        ShelfSort.LIFT -> if (r.back != null && r.backMin != null) ShelfAnswer(r.backMin, AUnit.MIN, Cap.BACK, r.back) else ShelfAnswer("–", AUnit.NONE, Cap.NONE)
        ShelfSort.EASY -> ShelfAnswer(r.steep.toString(), AUnit.DEG, Cap.STEEP)
        ShelfSort.LONG -> Shelf.length(r.len).let { (v, km) -> ShelfAnswer(v, if (km) AUnit.KM else AUnit.M, Cap.RANK, rank.toString(), n.toString()) }
        ShelfSort.SUN -> if (sunShare == null) ShelfAnswer("–", AUnit.NONE, Cap.NONE) else ShelfAnswer(Math.round(sunShare * 100).toString(), AUnit.PCT, Cap.SUN)
        ShelfSort.OPEN -> when (open) {
            true -> ShelfAnswer("", AUnit.WORD_OPEN, Cap.OPEN, r.lift ?: "")
            false -> ShelfAnswer("", AUnit.WORD_CLOSED, Cap.CLOSED, r.lift ?: "")
            null -> ShelfAnswer("–", AUnit.NONE, Cap.NONE)
        }
        ShelfSort.SNOW -> if (snowCm == null) ShelfAnswer("–", AUnit.NONE, Cap.NONE) else ShelfAnswer(Math.round(snowCm).toString(), AUnit.CM, Cap.SNOW)
    }

    /** Whether the snow chip has anything to paint: some run with a centimetre or more. */
    fun anySnow(runs: List<ShelfRun>, snowCm: (ShelfRun) -> Double?): Boolean = runs.any { (snowCm(it) ?: 0.0) >= 1.0 }
}

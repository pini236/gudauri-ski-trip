package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.places.Place
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rules of the day timeline "הרכס של היום" (boards DT1-DT5, approved 27.11.2026): which rows the page has, what a long
 * break is called, and where the day went. Pure, so a test runs it. Nothing here invents a stop type: the analysis knows
 * a queue, a lift, a descent, a long break and a walk, and that is all a row can be.
 */
internal object DayTimelineRules {
    /** A long break this close to a named café or restaurant of places.json takes its name (board DT5). */
    const val PLACE_M = 250.0
    /** A queue ends this close before a lift's start for the two to be one row. */
    const val QUEUE_JOIN_MS = 3 * 60_000L

    sealed interface BreakKind {
        data class Cafe(val name: String) : BreakKind
        data class Food(val name: String) : BreakKind
        /** No named place near: a bench, and the lift the break follows, if it follows one. */
        data class Bench(val atopLift: String?) : BreakKind
    }

    /** A row of the timeline. */
    sealed interface Row { val t0: Long }
    class Lift(override val t0: Long, val name: String, val kind: String?, val minutes: Int, val riseM: Double?, val queueMin: Int) : Row
    class Descent(val d: DayModel.Descent, val stops: Int, val top: Boolean, val gapAt: Long?) : Row { override val t0 get() = d.t0 }
    class Break(override val t0: Long, val minutes: Int, val kind: BreakKind) : Row
    class Walk(override val t0: Long, val distM: Double) : Row
    /** A queue with no lift after it in the data. */
    class Queue(override val t0: Long, val minutes: Int) : Row

    /** What a lift ride gives the builder: its start, its name and kind, its minutes and its rise. */
    class LiftIn(val t0: Long, val name: String, val kind: String?, val minutes: Int, val riseM: Double?)

    /**
     * The day's rows in time order. A queue right before a lift is that lift's chip and moves the row's time to its start;
     * a rest that is not long is not a row; a long break is named by [kindOf] (given its start and the lift it follows).
     */
    fun rows(
        items: List<DayModel.Item>, lifts: List<LiftIn>,
        stopsOf: (DayModel.Descent) -> Int, topAt: Long?, gapOf: (DayModel.Descent) -> Long?,
        kindOf: (t0: Long, afterLift: String?) -> BreakKind,
    ): List<Row> {
        data class E(val t: Long, val item: Any)
        val all = (items.map { E(it.t0, it) } + lifts.map { E(it.t0, it) }).sortedBy { it.t }
        val out = ArrayList<Row>()
        var lastLift: String? = null
        var i = 0
        while (i < all.size) {
            val e = all[i].item
            when (e) {
                is DayModel.Queue -> {
                    val next = all.getOrNull(i + 1)?.item as? LiftIn
                    if (next != null && next.t0 - (e.t0 + e.minutes * 60_000L) <= QUEUE_JOIN_MS) {
                        out += Lift(e.t0, next.name, next.kind, next.minutes, next.riseM, e.minutes); lastLift = next.name; i += 2; continue
                    }
                    out += Queue(e.t0, e.minutes)
                }
                is LiftIn -> { out += Lift(e.t0, e.name, e.kind, e.minutes, e.riseM, 0); lastLift = e.name }
                is DayModel.Descent -> out += Descent(e, stopsOf(e), e.maxAt != null && e.maxAt == topAt, gapOf(e))
                is DayModel.Rest -> if (e.long) out += Break(e.t0, e.minutes, kindOf(e.t0, lastLift))
                is DayModel.Walk -> out += Walk(e.t0, e.distM)
            }
            i++
        }
        return out
    }

    /**
     * What a long break is: the nearest named café or restaurant inside [PLACE_M] gives a cup or a fork and its name; else a
     * bench, with the lift the break follows. No coordinate: a bench.
     */
    fun breakKind(lat: Double?, lon: Double?, places: List<Place>, afterLift: String?): BreakKind {
        if (lat != null && lon != null) {
            val near = places.filter { (it.kind == "cafe" || it.kind == "food") && !it.name.isNullOrBlank() }
                .map { it to distM(lat, lon, it.lat, it.lon) }.filter { it.second <= PLACE_M }.minByOrNull { it.second }?.first
            if (near != null) return if (near.kind == "cafe") BreakKind.Cafe(near.name!!) else BreakKind.Food(near.name!!)
        }
        return BreakKind.Bench(afterLift)
    }

    /** The four parts of "לאן הלך היום", in seconds; [rest] is what is left of the day (breaks and walking). */
    class Where(val ski: Double, val lift: Double, val queue: Double, val rest: Double) {
        val total get() = ski + lift + queue + rest
    }

    fun where(totalS: Double, skiS: Double, liftS: Double, queueS: Double): Where {
        val used = skiS + liftS + queueS
        // the three parts can pass the day's length (overlapping segments): then they are scaled down, never a negative rest
        val k = if (used > totalS && used > 0) totalS / used else 1.0
        return Where(skiS * k, liftS * k, queueS * k, (totalS - used * k).coerceAtLeast(0.0))
    }

    /** Whole minutes of a part of the bar, for its legend. */
    fun minutes(s: Double) = Math.round(s / 60.0).toInt()

    /** Hours on the mountain, one decimal ("3.5"). */
    fun hours(startMs: Long, endMs: Long): String = String.format(java.util.Locale.ROOT, "%.1f", (endMs - startMs).coerceAtLeast(0L) / 3_600_000.0)

    /**
     * The speed bands of a descent for its strip: [bins] bins between [t0] and [t1], each the mean band of the track points
     * in it (an empty bin takes the one before; before any point, the first). Empty when the track has no point inside.
     */
    fun strip(t: LongArray, band: IntArray, t0: Long, t1: Long, bins: Int): IntArray {
        if (t1 <= t0 || bins <= 0) return IntArray(0)
        val sum = IntArray(bins); val n = IntArray(bins)
        for (i in t.indices) if (t[i] in t0..t1) { val b = ((t[i] - t0) * bins / (t1 - t0)).toInt().coerceIn(0, bins - 1); sum[b] += band[i]; n[b]++ }
        if (n.all { it == 0 }) return IntArray(0)
        val out = IntArray(bins); var prev = -1
        for (b in 0 until bins) { if (n[b] > 0) prev = Math.round(sum[b].toDouble() / n[b]).toInt(); out[b] = prev }
        val first = out.firstOrNull { it >= 0 } ?: 0
        for (b in 0 until bins) if (out[b] < 0) out[b] = first
        return out
    }

    /** Metres between two places (haversine). */
    fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0; val p = PI / 180
        val a = sin((lat2 - lat1) * p / 2).let { it * it } + cos(lat1 * p) * cos(lat2 * p) * sin((lon2 - lon1) * p / 2).let { it * it }
        return 2 * r * asin(min(1.0, sqrt(a)))
    }
}

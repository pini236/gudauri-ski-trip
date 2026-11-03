package io.github.pini236.skiapp.dayrec.ui

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * The end of the day, as the screens draw it (round 29, the timeline): from a day's analysis result to chapters of cards.
 * Pure, so a test can run it; nothing here reads a position (the result has none, but the max speed's place and the falls).
 *
 * The timeline is the truth (the live screen only guessed): a descent is a card with its runs in order and its top speed; a
 * lift appears only as the queue that came before it (round 29, version 2: "lift only when there was a queue"); a long rest is
 * a bar; a stretch off the runs is said so, and is never given a name.
 */
object DayModel {
    sealed interface Item { val t0: Long }
    data class Descent(override val t0: Long, val runs: List<String>, val maxKmh: Double?, val offRuns: Boolean, val dropM: Double, val distM: Double,
        /** The card of a descent (round 29): its end, the time it moved, its average, and when the top speed was. */
        val t1: Long = t0, val movingS: Double = 0.0, val avgKmh: Double? = null, val maxAt: Long? = null) : Item
    /** A wait for a lift of at least [QUEUE_MIN] minutes; [lift] is the name of the lift after it, when there was one. */
    data class Queue(override val t0: Long, val minutes: Int, val lift: String?) : Item
    data class Rest(override val t0: Long, val minutes: Int, val long: Boolean) : Item
    data class Walk(override val t0: Long, val distM: Double) : Item

    class Chapter(val afternoon: Boolean, val items: List<Item>) { val descents get() = items.count { it is Descent } }

    class Summary(
        val start: Long, val end: Long, val descents: Int, val lifts: Int, val dropM: Double, val distM: Double,
        val maxKmh: Double?, val maxAt: Long?, val maxLat: Double?, val maxLon: Double?,
    )

    class Day(val summary: Summary, val chapters: List<Chapter>)

    /** A queue shorter than this is not worth a line. */
    const val QUEUE_MIN = 3
    /** A rest of this many minutes or more is shown. */
    const val REST_MIN = 10
    /** From this hour on (the resort's clock) it is the afternoon. */
    const val NOON_HOUR = 13

    fun build(result: JSONObject, zone: ZoneId): Day {
        val segs = result.optJSONArray("segments")
        val st = result.optJSONObject("stats")
        val items = ArrayList<Item>()
        if (segs != null) for (i in 0 until segs.length()) {
            val s = segs.getJSONObject(i)
            val t0 = s.optLong("t0")
            when (s.optString("type")) {
                "descent" -> {
                    val runs = LinkedHashSet<String>(); var off = false
                    s.optJSONArray("legs")?.let { l ->
                        for (k in 0 until l.length()) {
                            val g = l.getJSONObject(k)
                            when (g.optString("kind")) {
                                "run" -> g.optJSONArray("runs")?.let { r -> val names = (0 until r.length()).map { r.getString(it) }; if (names.isNotEmpty()) runs += names.joinToString(" · ") }
                                "off" -> off = true
                            }
                        }
                    }
                    if (s.optJSONArray("offpiste")?.length()?.let { it > 0 } == true) off = true
                    items += Descent(t0, runs.toList(), s.dbl("max_kmh"), off, s.optDouble("drop_m", 0.0), s.optDouble("dist_m", 0.0),
                        s.optLong("t1", t0), s.optDouble("moving_s", 0.0), s.dbl("avg_kmh"), s.optJSONObject("max_at")?.let { m -> if (m.has("t") && !m.isNull("t")) m.optLong("t") else null })
                }
                "stop" -> {
                    val min = ((s.optLong("t1") - t0) / 60_000L).toInt()
                    when (s.optString("kind")) {
                        "queue" -> if (min >= QUEUE_MIN) items += Queue(t0, min, nextLift(segs, i))
                        "long" -> if (min >= REST_MIN) items += Rest(t0, min, true)
                        "rest" -> if (min >= REST_MIN) items += Rest(t0, min, false)
                    }
                }
                "walk" -> {
                    val d = s.optDouble("dist_m", 0.0)
                    if (d >= WALK_M) items += Walk(t0, d)
                }
            }
        }
        val at = st?.optJSONObject("max_at")
        val summary = Summary(
            result.optLong("start"), result.optLong("end"), st?.optInt("descents", 0) ?: 0, st?.optInt("lifts", 0) ?: 0,
            st?.optDouble("drop_m", 0.0) ?: 0.0, st?.optDouble("ski_dist_m", 0.0) ?: 0.0, st?.dbl("max_kmh"),
            at?.optLong("t"), at?.dbl("lat"), at?.dbl("lon"),
        )
        val chapters = ArrayList<Chapter>()
        for (it in items) {
            val pm = Instant.ofEpochMilli(it.t0).atZone(zone).hour >= NOON_HOUR
            if (chapters.isEmpty() || chapters.last().afternoon != pm) chapters += Chapter(pm, mutableListOf<Item>())
            (chapters.last().items as MutableList<Item>) += it
        }
        return Day(summary, chapters)
    }

    /** A walk shorter than this is the skier moving about, not a line of the day. */
    const val WALK_M = 150.0

    private fun nextLift(segs: org.json.JSONArray, from: Int): String? {
        for (i in from + 1 until minOf(segs.length(), from + 3)) {
            val s = segs.getJSONObject(i)
            if (s.optString("type") == "lift") return s.opt("name")?.takeIf { it != JSONObject.NULL }?.toString()
        }
        return null
    }

    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null
}

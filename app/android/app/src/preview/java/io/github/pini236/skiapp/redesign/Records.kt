package io.github.pini236.skiapp.redesign

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * The completion round, part 1 (P05, P06): your records, against yourself only. The numbers come from the moments
 * engine's history (history.best), which the phone already keeps; the date, the run and the place from the saved day
 * whose number equals the record. A deleted day leaves the record without them. The example day never counts. Pure,
 * so a test pins the matching.
 */
internal object Records {
    /** A saved day: its id (the file's name), when it began, its analysis and its moments (null if missing). */
    class SavedDay(val id: String, val start: Long, val result: JSONObject, val moments: JSONObject?)

    /** One row: [what] the history's key, [value] the record (null: none yet), and where it comes from when the day is still there. */
    class Row(
        val what: String, val value: Double?, val day: SavedDay?, val runs: List<String> = emptyList(),
        val at: DoubleArray? = null, val atT: Long? = null, val fresh: Boolean = false,
    )

    /** The order of the six rows (the handoff's table). */
    val ORDER = listOf("max_kmh", "longest_m", "drop_m", "descents", "ski_dist_m", "steep_deg")

    private fun near(a: Double, b: Double) = abs(a - b) < 0.05 * maxOf(1.0, abs(b) / 1000.0)
    private fun num(o: JSONObject?, k: String): Double? = if (o == null || !o.has(k) || o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }
    private fun descents(r: JSONObject): List<JSONObject> =
        r.optJSONArray("segments")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty().filter { it.optString("type") == "descent" }
    private fun runsOf(d: JSONObject): List<String> {
        val out = LinkedHashSet<String>()
        val legs = d.optJSONArray("legs") ?: JSONArray()
        for (i in 0 until legs.length()) { val l = legs.getJSONObject(i); if (l.optString("kind") == "run") l.optJSONArray("runs")?.let { a -> for (j in 0 until a.length()) out += a.getString(j) } }
        return out.toList()
    }
    private fun items(m: JSONObject?): List<JSONObject> = m?.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    private fun isDay(r: JSONObject) = r.optString("mode", "day") == "day"

    /**
     * The rows for [best] (history.best) over the saved [days] (no example day among them), oldest first. "New" is on a
     * record set on the last day recorded: that day has a record moment for it, or it is the only day.
     */
    fun rows(best: JSONObject, days: List<SavedDay>): List<Row> {
        val last = days.maxByOrNull { it.start }
        fun fresh(what: String, d: SavedDay?): Boolean {
            if (d == null || d !== last) return false
            if (days.size == 1) return true
            return items(d.moments).any { (it.optString("key") == "record" && it.optJSONObject("p")?.optString("what") == what) ||
                (what == "steep_deg" && it.optString("key") == "steepest" && it.optJSONObject("p")?.optBoolean("ever") == true) }
        }
        return ORDER.map { what ->
            val v = num(best, what)
            if (v == null) return@map Row(what, null, null)
            // the first day that matches is the day the record was set (an equal later day set no record)
            val sorted = days.sortedBy { it.start }
            when (what) {
                "max_kmh" -> {
                    val d = sorted.firstOrNull { num(it.result.optJSONObject("stats"), "max_kmh")?.let { m -> near(m, v) } == true }
                    val maxAt = d?.result?.optJSONObject("stats")?.optJSONObject("max_at")
                    val t = maxAt?.optLong("t")
                    val desc = d?.let { descents(it.result).firstOrNull { s -> t != null && t >= s.optLong("t0") && t <= s.optLong("t1") } }
                    Row(what, v, d, desc?.let { runsOf(it) }.orEmpty(), maxAt?.let { doubleArrayOf(it.optDouble("lat"), it.optDouble("lon")) }, t, fresh(what, d))
                }
                "longest_m" -> {
                    var hit: Pair<SavedDay, JSONObject>? = null
                    for (d in sorted) { val s = descents(d.result).firstOrNull { near(it.optDouble("dist_m"), v) }; if (s != null) { hit = d to s; break } }
                    Row(what, v, hit?.first, hit?.second?.let { runsOf(it) }.orEmpty(), null, hit?.second?.optLong("t0"), fresh(what, hit?.first))
                }
                "descents" -> {
                    val d = sorted.firstOrNull { isDay(it.result) && descents(it.result).size == v.toInt() && v > 0 }
                    Row(what, v, d, fresh = fresh(what, d))
                }
                "drop_m", "ski_dist_m" -> {
                    val d = sorted.firstOrNull { isDay(it.result) && descents(it.result).isNotEmpty() && num(it.result.optJSONObject("stats"), what)?.let { m -> near(m, v) } == true }
                    Row(what, v, d, fresh = fresh(what, d))
                }
                else -> { // steep_deg: the day with a "steepest" moment at that angle, and its run
                    var hit: Pair<SavedDay, JSONObject>? = null
                    for (d in sorted) { val it = items(d.moments).firstOrNull { i -> i.optString("key") == "steepest" && i.optJSONObject("p")?.optInt("deg") == v.toInt() }; if (it != null) { hit = d to it; break } }
                    Row(what, v, hit?.first, listOfNotNull(hit?.second?.optJSONObject("p")?.optString("run")?.takeIf { it.isNotEmpty() }), null, null, fresh(what, hit?.first))
                }
            }
        }
    }
}

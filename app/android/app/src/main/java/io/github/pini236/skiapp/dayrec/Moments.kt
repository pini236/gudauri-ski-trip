package io.github.pini236.skiapp.dayrec

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.CRC32

/**
 * The moments engine (tools/dayrec/moments.py, the reference; docs/AI-PRODUCT.md opportunity 1): from the final analysis of a
 * day and the skier's own history it picks the moments of the day. It writes no text: every moment is a key and parameters, and
 * the text comes from the language file (`moment.<key>`). On the phone, without a network and without a language model.
 *
 * A pure function of its input, ported rule by rule so that the output is the same JSON as the reference's (the test values
 * are compared exactly). The history never holds a position, and the places a moment carries (the fastest point, a fall) are
 * for the phone's own map and never for the share picture.
 */
object Moments {
    const val HISTORY_V = 1

    /** A run of the network: the colour, the kind and whether it has a name (the moments name "the first red"). */
    class RunInfo(val color: String?, val kind: String, val named: Boolean)

    /** [runs-and-lifts.json] to the runs the engine reads. */
    fun runsOf(runsAndLifts: JSONObject): Map<String, RunInfo> {
        val out = LinkedHashMap<String, RunInfo>()
        val a = runsAndLifts.getJSONArray("pistes")
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            val named = r.opt("named").let { it == true || it == "True" || it == "true" }
            out[r.getString("key")] = RunInfo(r.optString("color").takeIf { r.has("color") && !r.isNull("color") && it.isNotEmpty() }, r.optString("kind", "run"), named)
        }
        return out
    }

    fun emptyHistory(): JSONObject = JSONObject().put("v", HISTORY_V).put("days", 0)
        .put("runs", JSONObject()).put("colors", JSONObject()).put("best", JSONObject())

    /** The same text variant on both apps: the app shows variant seed % (number of variants of the key). */
    fun seed(key: String, t: Long): Long {
        val c = CRC32(); c.update("$key|$t".toByteArray(Charsets.UTF_8))
        return c.value and 0x7fffffffL
    }

    private fun list(a: JSONArray?): List<JSONObject> = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
    private fun strings(a: JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }
    private fun num(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null
    private fun segs(res: JSONObject) = list(res.optJSONArray("segments"))
    private fun descents(res: JSONObject) = segs(res).filter { it.getString("type") == "descent" }
    private fun runLegs(res: JSONObject) = descents(res).flatMap { list(it.getJSONArray("legs")) }.filter { it.getString("kind") == "run" }

    private fun legAt(res: JSONObject, t: Long): JSONObject? {
        for (s in descents(res)) for (lg in list(s.getJSONArray("legs"))) if (lg.getLong("t0") <= t && t <= lg.getLong("t1")) return lg
        return null
    }

    private fun where(into: JSONObject, lg: JSONObject?): JSONObject {
        if (lg == null) return into.put("kind", "unmatched")
        into.put("kind", lg.getString("kind"))
        if (lg.getString("kind") == "run") into.put("runs", JSONArray(strings(lg.getJSONArray("runs"))))
        return into
    }

    /** The runs of a descent by the distance on each (names only from legs on one run), longest first. */
    private fun mainRuns(s: JSONObject, n: Int = 3): List<String> {
        val d = LinkedHashMap<String, Double>()
        for (lg in list(s.getJSONArray("legs"))) {
            val r = strings(lg.getJSONArray("runs"))
            if (lg.getString("kind") == "run" && r.size == 1) d[r[0]] = (d[r[0]] ?: 0.0) + lg.getDouble("dist_m")
        }
        return d.entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }.take(n)
    }

    /** [(key, t of the first leg)] of the named runs skied today: a leg on that one run of at least color_min_m. */
    private fun done(res: JSONObject, runs: Map<String, RunInfo>, M: JSONObject): List<Pair<String, Long>> {
        val out = LinkedHashMap<String, Long>()
        for (lg in runLegs(res)) {
            val r = strings(lg.getJSONArray("runs"))
            if (r.size == 1 && lg.getDouble("dist_m") >= M.getDouble("color_min_m")) {
                val k = r[0]
                if (runs[k]?.named == true && k !in out) out[k] = lg.getLong("t0")
            }
        }
        return out.entries.map { it.key to it.value }.sortedWith(compareBy({ it.second }, { it.first }))
    }

    private fun dayBests(res: JSONObject): Map<String, Double?> {
        val ds = descents(res)
        val st = res.optJSONObject("stats") ?: JSONObject()
        return linkedMapOf(
            "max_kmh" to num(st, "max_kmh"),
            "drop_m" to if (ds.isNotEmpty()) num(st, "drop_m") else null,
            "descents" to if (ds.isNotEmpty()) ds.size.toDouble() else null,
            "longest_m" to ds.maxOfOrNull { it.getDouble("dist_m") },
            "ski_dist_m" to if (ds.isNotEmpty()) num(st, "ski_dist_m") else null,
        )
    }

    private val RUN_RECORDS = setOf("max_kmh", "longest_m")
    private fun isDay(res: JSONObject) = res.optString("mode", "day") == "day"

    /** The history after this day: first time of each run and colour, and the personal bests. Never a position. */
    fun update(history: JSONObject, res: JSONObject, runs: Map<String, RunInfo>, M: JSONObject): JSONObject {
        val h = JSONObject().put("v", HISTORY_V).put("days", history.optInt("days", 0))
            .put("runs", copy(history.optJSONObject("runs"))).put("colors", copy(history.optJSONObject("colors"))).put("best", copy(history.optJSONObject("best")))
        val day = isDay(res)
        if (day && descents(res).isNotEmpty()) h.put("days", h.getInt("days") + 1)
        val hr = h.getJSONObject("runs"); val hc = h.getJSONObject("colors"); val hb = h.getJSONObject("best")
        for ((k, t) in done(res, runs, M)) {
            if (!hr.has(k)) hr.put(k, t)
            val c = runs[k]?.color
            if (c != null) hc.put(c, if (hc.has(c)) minOf(t, hc.getLong(c)) else t)
        }
        for ((k, v) in dayBests(res)) {
            if (v == null || (!day && k !in RUN_RECORDS)) continue
            if (!hb.has(k) || v > hb.getDouble(k)) hb.put(k, v)
        }
        return h
    }

    private fun copy(o: JSONObject?): JSONObject = if (o == null) JSONObject() else JSONObject(o.toString())

    private class Item(val key: String, val t: Long, val p: JSONObject)

    /** {moments, items, story, share, history}. [history] null is a skier with none. */
    fun moments(res: JSONObject, history: JSONObject?, runs: Map<String, RunInfo>, M: JSONObject): JSONObject {
        val hist = history ?: emptyHistory()
        val items = ArrayList<Item>()
        val end = if (res.has("end") && !res.isNull("end")) res.getLong("end") else 0L
        val ds = descents(res)
        val day = isDay(res)
        val hBest = hist.optJSONObject("best") ?: JSONObject()
        val hRuns = hist.optJSONObject("runs") ?: JSONObject()
        val hColors = hist.optJSONObject("colors") ?: JSONObject()
        val known = hist.optInt("days", 0) > 0 || hBest.length() > 0
        val st = res.optJSONObject("stats") ?: JSONObject()
        val prio = M.getJSONObject("priority")
        fun add(key: String, t: Long, p: JSONObject) { items += Item(key, t, p) }
        fun longest() = ds.fold(null as JSONObject?) { b, s -> if (b == null || s.getDouble("dist_m") > b.getDouble("dist_m") || (s.getDouble("dist_m") == b.getDouble("dist_m") && s.getLong("t0") < b.getLong("t0"))) s else b }

        // the fastest moment of the day ("here you flew")
        val maxAt = st.optJSONObject("max_at")
        if (num(st, "max_kmh") != null && maxAt != null && maxAt.length() > 0) {
            val p = JSONObject().put("kmh", st.get("max_kmh")).put("at", JSONObject().put("lat", maxAt.get("lat")).put("lon", maxAt.get("lon")))
            add("fastest", maxAt.getLong("t"), where(p, legAt(res, maxAt.getLong("t"))))
        }
        // the longest descent
        longest()?.let { s ->
            add("longest_descent", s.getLong("t0"), JSONObject().put("t1", s.get("t1")).put("dist_m", s.get("dist_m")).put("drop_m", s.get("drop_m"))
                .put("moving_s", s.get("moving_s")).put("runs", JSONArray(mainRuns(s))))
        }
        // the first of each colour today, and whether it is the first ever
        val doneToday = done(res, runs, M)
        for (c in strings(M.getJSONArray("colors_first"))) {
            val first = doneToday.firstOrNull { runs[it.first]?.color == c }
            if (first != null) add("first_color", first.second, JSONObject().put("color", c).put("run", first.first).put("first_ever", !hColors.has(c)))
        }
        // runs skied for the first time (only against a history: on the first day everything is new)
        if (known) {
            val new = doneToday.filter { !hRuns.has(it.first) }
            if (new.isNotEmpty()) add("new_runs", new[0].second, JSONObject().put("runs", JSONArray(new.map { it.first })))
        }
        // personal records against the history, and "all the blues"
        if (known) {
            val bests = dayBests(res)
            val recordKeys = strings(M.getJSONArray("records"))
            val tOf = mapOf("max_kmh" to (maxAt?.optLong("t", end) ?: end), "longest_m" to (longest()?.getLong("t0") ?: end))
            for (k in recordKeys) {
                val v = bests[k]
                if (v == null || (!day && k !in RUN_RECORDS) || !hBest.has(k) || v <= hBest.getDouble(k)) continue
                add("record", tOf[k] ?: end, JSONObject().put("what", k).put("value", if (k == "descents") v.toInt() else v).put("before", hBest.get(k)))
            }
            val allr = LinkedHashMap<String, MutableSet<String>>()
            for ((k, r) in runs) if (r.named && r.kind == "run" && r.color != null) allr.getOrPut(r.color) { LinkedHashSet() } += k
            val had = hRuns.keys().asSequence().toSet()
            val now = had + doneToday.map { it.first }
            for (c in strings(M.getJSONArray("colors_all"))) {
                val want = allr[c] ?: emptySet()
                if (want.isNotEmpty() && now.containsAll(want) && !had.containsAll(want)) {
                    val t = doneToday.filter { it.first in want && it.first !in had }.maxOf { it.second }
                    add("all_color", t, JSONObject().put("color", c).put("n", want.size))
                }
            }
        }
        // the queues
        val all = segs(res)
        data class Q(val s: Long, val t0: Long, val lift: String?)
        val qs = ArrayList<Q>()
        for ((i, s) in all.withIndex()) {
            if (s.getString("type") == "stop" && s.optString("kind") == "queue") {
                val nxt = all.drop(i + 1).firstOrNull { it.getString("type") == "lift" }
                qs += Q(Math.rint((s.getLong("t1") - s.getLong("t0")) / 1000.0).toLong(), s.getLong("t0"), nxt?.let { if (it.has("name") && !it.isNull("name")) it.getString("name") else null })
            }
        }
        if (qs.isNotEmpty()) {
            val q = qs.fold(qs[0]) { b, x -> if (x.s > b.s || (x.s == b.s && x.t0 < b.t0)) x else b }
            if (q.s >= M.getInt("queue_min_s")) {
                add("queue", q.t0, JSONObject().put("s", q.s).put("lift", q.lift ?: JSONObject.NULL).put("total_s", qs.sumOf { it.s }).put("count", qs.size))
            }
        }
        // the drop against known heights: the first in the list that gives a ratio in range
        val drop = num(st, "drop_m") ?: 0.0
        if (day && drop >= M.getDouble("drop_min_m")) {
            val lo = M.getJSONArray("drop_times").getDouble(0); val hi = M.getJSONArray("drop_times").getDouble(1)
            for (ref in list(M.getJSONArray("drop_refs"))) {
                val times = drop / ref.getDouble("m")
                if (times in lo..hi) {
                    add("drop_compare", end, JSONObject().put("ref", ref.getString("id")).put("times", BigDecimal(times).setScale(1, RoundingMode.HALF_EVEN).toDouble()).put("drop_m", st.get("drop_m")))
                    break
                }
            }
        }
        // "here you fell" (a question, never a safety claim)
        for (f in list(res.optJSONArray("falls"))) {
            val p = JSONObject().put("at", JSONObject().put("lat", f.opt("lat") ?: JSONObject.NULL).put("lon", f.opt("lon") ?: JSONObject.NULL))
            add("fall", f.getLong("t"), where(p, legAt(res, f.getLong("t"))))
        }
        // off-piste descents
        for (s in ds) for (o in list(s.optJSONArray("offpiste"))) {
            add("offpiste", o.getLong("t0"), JSONObject().put("t1", o.get("t1")).put("drop_m", o.get("drop_m")).put("dist_m", o.get("dist_m")))
        }
        val sorted = items.sortedWith(compareBy<Item>({ it.t }, { -prio.getInt(it.key) }, { it.key }))
        val records = strings(M.getJSONArray("records"))
        fun sub(m: Item) = if (m.key == "record") records.indexOf(m.p.getString("what")) else 0
        val rank = sorted.indices.sortedWith(compareBy<Int>({ -prio.getInt(sorted[it].key) }, { sub(sorted[it]) }, { sorted[it].t }, { sorted[it].key }))
        // the story: the best moments, at most story_per_key of a kind, in the order of the day
        val story = ArrayList<Int>(); val per = HashMap<String, Int>()
        val storyMax = M.getInt("story_max"); val perKey = M.getInt("story_per_key")
        for (i in rank) {
            val k = sorted[i].key
            if (story.size < storyMax && (per[k] ?: 0) < perKey) { story += i; per[k] = (per[k] ?: 0) + 1 }
        }
        story.sort()
        val exclude = strings(M.getJSONArray("share_exclude"))
        val share = rank.firstOrNull { sorted[it].key !in exclude }
        val out = JSONObject().put("moments", M.getInt("version"))
        out.put("items", JSONArray().also { a -> sorted.forEach { a.put(JSONObject().put("key", it.key).put("t", it.t).put("seed", seed(it.key, it.t)).put("p", it.p)) } })
        out.put("story", JSONArray(story)).put("share", share ?: JSONObject.NULL).put("history", update(hist, res, runs, M))
        return out
    }

    /**
     * The card on the lift: the last descent before the lift the skier is on now, from the final analysis of the log so far
     * (run off the main thread, only once the lift is certain). null if the last segment is not a lift or there was no descent
     * before it.
     */
    fun liftCard(res: JSONObject): JSONObject? {
        val all = segs(res)
        if (all.isEmpty() || all.last().getString("type") != "lift") return null
        val s = all.dropLast(1).lastOrNull { it.getString("type") == "descent" } ?: return null
        val t0 = s.getLong("t0"); val t1 = s.getLong("t1")
        val p = JSONObject().put("t1", s.get("t1")).put("runs", JSONArray(mainRuns(s))).put("dist_m", s.get("dist_m")).put("drop_m", s.get("drop_m"))
            .put("moving_s", s.get("moving_s")).put("max_kmh", s.opt("max_kmh") ?: JSONObject.NULL)
            .put("falls", list(res.optJSONArray("falls")).count { it.getLong("t") in t0..t1 })
        return JSONObject().put("key", "last_descent").put("t", t0).put("seed", seed("last_descent", t0)).put("p", p)
    }
}

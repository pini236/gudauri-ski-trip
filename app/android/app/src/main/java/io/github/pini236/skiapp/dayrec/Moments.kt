package io.github.pini236.skiapp.dayrec

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.CRC32
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Terrain

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
    const val HISTORY_V = 2
    private val COLOR_ORDER = listOf("green", "blue", "red", "black")
    private val DAY_SUM = listOf("drop_m", "descents", "ski_dist_m") // what the history keeps of a day: numbers only, never a place

    /**
     * A run of the network: the colour, the kind, whether it has a name (the moments name "the first red"), the lifts it
     * starts from, and [steepG], its steepest ~100 m stretch as a grade (the number of the run panel).
     */
    class RunInfo(val color: String?, val kind: String, val named: Boolean, val fromLifts: List<String> = emptyList(), val steepG: Double = 0.0)

    /**
     * [runs-and-lifts.json] to the runs the engine reads. With the terrain model the steepest stretch is measured too (the
     * reference's `_steep_g`: the lines that are not an area or a tunnel, windows of ~100 m of at least 80 m); without it
     * every run has none.
     */
    fun runsOf(runsAndLifts: JSONObject, terrain: Terrain? = null): Map<String, RunInfo> {
        val out = LinkedHashMap<String, RunInfo>()
        val a = runsAndLifts.getJSONArray("pistes")
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            val named = r.opt("named").let { it == true || it == "True" || it == "true" }
            val from = r.optJSONArray("fromLifts")?.let { f -> (0 until f.length()).map { f.getString(it) } } ?: emptyList()
            out[r.getString("key")] = RunInfo(r.optString("color").takeIf { r.has("color") && !r.isNull("color") && it.isNotEmpty() }, r.optString("kind", "run"), named,
                from, if (terrain != null) steepG(r, terrain) else 0.0)
        }
        return out
    }

    private fun steepG(run: JSONObject, t: Terrain): Double {
        var best = 0.0
        val segs = run.getJSONArray("segs")
        for (si in 0 until segs.length()) {
            val seg = segs.getJSONObject(si)
            val g = seg.getJSONArray("g")
            if (seg.optBoolean("area", false) || seg.optBoolean("tunnel", false) || g.length() < 2) continue
            val n = g.length()
            val acc = DoubleArray(n); val h = DoubleArray(n)
            var px = 0.0; var py = 0.0
            for (i in 0 until n) {
                val p = g.getJSONArray(i)
                val x = Geo.x(p.getDouble(1)).toDouble(); val y = Geo.y(p.getDouble(0)).toDouble()
                if (i > 0) acc[i] = acc[i - 1] + Math.hypot(x - px, y - py)
                h[i] = t.elev(x.toFloat(), y.toFloat()).toDouble()
                px = x; py = y
            }
            var j = 0
            for (i in 0 until n) {
                while (j < n - 1 && acc[j] - acc[i] < 100) j++
                val dl = acc[j] - acc[i]
                if (dl >= 80) best = maxOf(best, Math.abs(h[j] - h[i]) / dl)
            }
        }
        return best
    }

    fun emptyHistory(): JSONObject = JSONObject().put("v", HISTORY_V).put("days", 0)
        .put("runs", JSONObject()).put("colors", JSONObject()).put("best", JSONObject())
        .put("first", JSONObject.NULL).put("prev", JSONObject.NULL)

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

    private fun deg(g: Double): Int = Math.rint(Math.toDegrees(Math.atan(g))).toInt()
    private fun r1(x: Double): Double = BigDecimal(x).setScale(1, RoundingMode.HALF_EVEN).toDouble()
    private fun rnd(x: Double): Long = Math.rint(x).toLong()

    /** The steepest run skied today (a leg of at least color_min_m on it): {run, deg, t}, or null. */
    private class Steep(val run: String, val deg: Int, val t: Long)
    private fun steepest(res: JSONObject, runs: Map<String, RunInfo>, M: JSONObject): Steep? {
        var best: Steep? = null
        for ((k, t) in done(res, runs, M)) {
            val d = deg(runs[k]?.steepG ?: 0.0)
            if (best == null || d > best.deg) best = Steep(k, d, t)
        }
        return best
    }

    /** The colours of a descent: of each run it went on for at least color_min_m (legs on that one run). */
    private fun descColors(s: JSONObject, runs: Map<String, RunInfo>, M: JSONObject): Set<String> {
        val d = LinkedHashMap<String, Double>()
        for (lg in list(s.getJSONArray("legs"))) {
            val r = strings(lg.getJSONArray("runs"))
            if (lg.getString("kind") == "run" && r.size == 1) d[r[0]] = (d[r[0]] ?: 0.0) + lg.getDouble("dist_m")
        }
        return d.filter { (k, m) -> runs[k] != null && m >= M.getDouble("color_min_m") && runs[k]!!.color != null }.keys.map { runs[it]!!.color!! }.toSet()
    }

    private class Q(val s: Long, val t0: Long, val lift: String?)

    /** The queues: a queue stop and the lift after it. */
    private fun queues(res: JSONObject): List<Q> {
        val all = segs(res)
        val qs = ArrayList<Q>()
        for ((i, s) in all.withIndex()) {
            if (s.getString("type") == "stop" && s.optString("kind") == "queue") {
                val nxt = all.drop(i + 1).firstOrNull { it.getString("type") == "lift" }
                qs += Q(rnd((s.getLong("t1") - s.getLong("t0")) / 1000.0), s.getLong("t0"), nxt?.let { if (it.has("name") && !it.isNull("name")) it.getString("name") else null })
            }
        }
        return qs
    }

    /** A lift whose queues were shorter on one side of an hour than the other: {lift, split, early, late}, the largest difference, or null. */
    private fun queuePattern(res: JSONObject, M: JSONObject): JSONObject? {
        val I = M.getJSONObject("insights")
        val off = M.getDouble("utc_offset_h")
        val by = java.util.TreeMap<String, MutableList<Pair<Long, Long>>>()
        for (q in queues(res)) if (q.lift != null) by.getOrPut(q.lift) { ArrayList() } += q.t0 to q.s
        var best: JSONObject? = null
        for ((lift, raw) in by) {
            val q = raw.sortedWith(compareBy({ it.first }, { it.second }))
            fun hour(t: Long) = ((t / 1000) / 3600.0 + off) % 24
            val hours = q.map { (hour(it.first).toInt() + 1) }.toSortedSet()
            for (hsplit in hours) {
                val a = q.filter { hour(it.first) < hsplit }.map { it.second }
                val b = q.filter { hour(it.first) >= hsplit }.map { it.second }
                if (a.size < I.getInt("queue_pattern_each") || b.size < I.getInt("queue_pattern_each")) continue
                val e = rnd(a.sum().toDouble() / a.size / 60.0); val l = rnd(b.sum().toDouble() / b.size / 60.0)
                if (Math.abs(e - l) >= I.getInt("queue_pattern_min_min") && (best == null || Math.abs(e - l) > Math.abs(best.getLong("early") - best.getLong("late")))) {
                    best = JSONObject().put("lift", lift).put("split", hsplit).put("early", e).put("late", l)
                }
            }
        }
        return best
    }

    /** One suggestion: new_run, finish_color or early_lift. Never a colour harder than the hardest skied today. */
    private fun tomorrow(res: JSONObject, hist: JSONObject, runs: Map<String, RunInfo>, M: JSONObject, done: List<Pair<String, Long>>, qp: JSONObject?): JSONObject? {
        val today = done.mapNotNull { runs[it.first]?.color }.filter { it in COLOR_ORDER }.toSet()
        val ever = (hist.optJSONObject("runs")?.keys()?.asSequence()?.toSet() ?: emptySet()) + done.map { it.first }
        if (today.isNotEmpty()) {
            val hard = today.maxByOrNull { COLOR_ORDER.indexOf(it) }!!
            val rides = LinkedHashMap<String, LongArray>()
            for (s in segs(res)) if (s.getString("type") == "lift" && s.has("name") && !s.isNull("name") && s.getString("name").isNotEmpty()) {
                rides.getOrPut(s.getString("name")) { longArrayOf(0, s.getLong("t0")) }[0]++
            }
            if (rides.isNotEmpty()) {
                val lift = rides.entries.sortedWith(compareBy({ -it.value[0] }, { it.value[1] })).first().key
                val cand = runs.filter { (k, r) -> r.named && r.kind == "run" && r.color == hard && k !in ever && lift in r.fromLifts }.keys.sorted()
                if (cand.isNotEmpty()) return JSONObject().put("kind", "new_run").put("run", cand[0]).put("color", hard).put("lift", lift)
            }
            for (c in today.sortedByDescending { COLOR_ORDER.indexOf(it) }) {
                val left = runs.filter { (k, r) -> r.named && r.kind == "run" && r.color == c && k !in ever }.keys.sorted()
                if (left.size in 1..M.getJSONObject("insights").getInt("finish_max")) {
                    return JSONObject().put("kind", "finish_color").put("color", c).put("n", left.size).put("runs", JSONArray(left))
                }
            }
        }
        if (qp != null && qp.getLong("early") < qp.getLong("late")) return JSONObject().put("kind", "early_lift").put("lift", qp.getString("lift")).put("split", qp.get("split"))
        return null
    }

    private fun copy(o: JSONObject?): JSONObject = if (o == null) JSONObject() else JSONObject(o.toString())
    private fun obj(h: JSONObject, k: String): JSONObject? = h.optJSONObject(k)

    /** The trip so far after this day (a copy): ski days, drop, the days on each run, the colours skied for the first time ever. */
    private fun tripAdd(tr: JSONObject, hist: JSONObject, res: JSONObject, runs: Map<String, RunInfo>, M: JSONObject): JSONObject {
        val days = tr.getInt("days") + 1
        val drop = r1(tr.getDouble("drop_m") + (num(res.optJSONObject("stats") ?: JSONObject(), "drop_m") ?: 0.0))
        val out = JSONObject().put("start", tr.get("start")).put("days", days).put("drop_m", drop)
            .put("runs", copy(tr.optJSONObject("runs"))).put("first", copy(tr.optJSONObject("first")))
        val tRuns = out.getJSONObject("runs"); val tFirst = out.getJSONObject("first")
        val hColors = hist.optJSONObject("colors") ?: JSONObject()
        for ((k, _) in done(res, runs, M)) {
            tRuns.put(k, tRuns.optInt(k, 0) + 1)
            val c = runs[k]?.color
            if (c != null && !hColors.has(c) && !tFirst.has(c)) tFirst.put(c, JSONObject().put("run", k).put("day_n", days))
        }
        return out
    }

    /**
     * The history after this day: first time of each run and colour, the personal bests, the first and the last day's totals,
     * and the trip so far. Never a position. [trip] is {start, last} of "your trip" (the app knows it), or null; the trip
     * totals restart when start changes.
     */
    fun update(history: JSONObject, res: JSONObject, runs: Map<String, RunInfo>, M: JSONObject, trip: JSONObject? = null): JSONObject {
        val h = JSONObject().put("v", HISTORY_V).put("days", history.optInt("days", 0))
            .put("runs", copy(history.optJSONObject("runs"))).put("colors", copy(history.optJSONObject("colors"))).put("best", copy(history.optJSONObject("best")))
            .put("first", history.opt("first") ?: JSONObject.NULL).put("prev", history.opt("prev") ?: JSONObject.NULL) // a v1 history has neither: none yet
            .put("trip", history.opt("trip") ?: JSONObject.NULL)
        val day = isDay(res)
        val hasDescents = descents(res).isNotEmpty()
        if (day && hasDescents && trip != null) {
            var tr = history.optJSONObject("trip")
            if (tr == null || tr.get("start") != trip.get("start")) tr = JSONObject().put("start", trip.get("start")).put("days", 0).put("drop_m", 0.0).put("runs", JSONObject()).put("first", JSONObject())
            h.put("trip", tripAdd(tr, history, res, runs, M))
        }
        if (day && hasDescents) {
            h.put("days", h.getInt("days") + 1)
            val b = dayBests(res)
            val prev = JSONObject(); for (k in DAY_SUM) prev.put(k, b[k] ?: JSONObject.NULL)
            h.put("prev", prev)
            if (h.isNull("first")) h.put("first", JSONObject(prev.toString()))
        }
        val hr = h.getJSONObject("runs"); val hc = h.getJSONObject("colors"); val hb = h.getJSONObject("best")
        steepest(res, runs, M)?.let { st -> if (st.deg > hb.optInt("steep_deg", -1)) hb.put("steep_deg", st.deg) }
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

    private class Item(val key: String, val t: Long, val p: JSONObject)

    /** {moments, items, story, share, theme, history}. [history] null is a skier with none; [trip] is {start, last} of "your trip". */
    fun moments(res: JSONObject, history: JSONObject?, runs: Map<String, RunInfo>, M: JSONObject, trip: JSONObject? = null): JSONObject {
        val hist = history ?: emptyHistory()
        val newh = update(hist, res, runs, M, trip)
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
        val I = M.getJSONObject("insights")
        fun add(key: String, t: Long, p: JSONObject) { items += Item(key, t, p) }
        fun longest() = ds.fold(null as JSONObject?) { b, s -> if (b == null || s.getDouble("dist_m") > b.getDouble("dist_m") || (s.getDouble("dist_m") == b.getDouble("dist_m") && s.getLong("t0") < b.getLong("t0"))) s else b }
        fun stopsOf(s: JSONObject) = s.optJSONArray("stops")?.length() ?: 0

        // the fastest moment of the day ("here you flew")
        val maxAt = st.optJSONObject("max_at")
        if (num(st, "max_kmh") != null && maxAt != null && maxAt.length() > 0) {
            val p = JSONObject().put("kmh", st.get("max_kmh")).put("at", JSONObject().put("lat", maxAt.get("lat")).put("lon", maxAt.get("lon")))
            where(p, legAt(res, maxAt.getLong("t")))
            // the how-manieth descent of the day, of how many, and (with two or more) in which half
            val nth = ds.indexOfFirst { it.getLong("t0") <= maxAt.getLong("t") && maxAt.getLong("t") <= it.getLong("t1") } + 1
            if (nth > 0) {
                p.put("nth", nth).put("of", ds.size)
                if (ds.size >= 2) p.put("when", if (2 * nth <= ds.size) "early" else "late")
            }
            add("fastest", maxAt.getLong("t"), p)
        }
        // the longest descent
        longest()?.let { s ->
            add("longest_descent", s.getLong("t0"), JSONObject().put("t1", s.get("t1")).put("dist_m", s.get("dist_m")).put("drop_m", s.get("drop_m"))
                .put("moving_s", s.get("moving_s")).put("runs", JSONArray(mainRuns(s))).put("stops", stopsOf(s)))
        }
        // the first of each colour today, and whether it is the first ever
        val doneToday = done(res, runs, M)
        for (c in strings(M.getJSONArray("colors_first"))) {
            val first = doneToday.firstOrNull { runs[it.first]?.color == c }
            if (first != null) add("first_color", first.second, JSONObject().put("color", c).put("run", first.first).put("first_ever", !hColors.has(c))
                .put("day_n", hist.optInt("days", 0) + 1).put("n", ds.count { c in descColors(it, runs, M) }))
        }
        // runs skied for the first time (only against a history: on the first day everything is new)
        if (known) {
            val new = doneToday.filter { !hRuns.has(it.first) }
            if (new.isNotEmpty()) {
                val ever = hRuns.keys().asSequence().toSet() + doneToday.map { it.first }
                add("new_runs", new[0].second, JSONObject().put("runs", JSONArray(new.map { it.first })).put("done", ever.size)
                    .put("total", runs.values.count { it.named && it.kind == "run" && it.color != null }))
            }
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
        val qs = queues(res)
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
                    add("drop_compare", end, JSONObject().put("ref", ref.getString("id")).put("times", r1(times)).put("drop_m", st.get("drop_m")))
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

        // insights (the story of the day as insights): against the skier's own days only, never against others, never praising speed
        val bests = dayBests(res)
        // against the first day (from day 3) or the day before: only a rise
        if (day && ds.isNotEmpty()) {
            class C(val vs: String, val k: String, val ratio: Double, val before: Double)
            val cands = ArrayList<C>()
            val refs = listOf(Triple("first", hist.optJSONObject("first"), I.getDouble("vs_first_times")), Triple("prev", hist.optJSONObject("prev"), 1 + I.getDouble("vs_prev_rise")))
            for ((vs, ref, need) in refs) {
                if (ref == null || (vs == "first" && hist.optInt("days", 0) < 2)) continue
                for (k in DAY_SUM) {
                    val rv = num(ref, k); val bv = bests[k]
                    if (rv != null && rv != 0.0 && bv != null && bv != 0.0 && bv / rv >= need) cands += C(vs, k, bv / rv, rv)
                }
                if (cands.isNotEmpty()) break // the first day, when it says something, says more than the day before
            }
            if (cands.isNotEmpty()) {
                val c = cands.maxWithOrNull(compareBy<C>({ it.ratio }, { -DAY_SUM.indexOf(it.k) }))!!
                add("vs_day", end, JSONObject().put("vs", c.vs).put("what", c.k).put("value", bests[c.k]).put("before", c.before).put("times", r1(c.ratio)))
            }
        }
        // flow: fewer stops inside the descents of the second half of the day
        if (ds.size >= 2 * I.getInt("flow_min_each")) {
            val h = ds.size / 2
            val am = ds.take(h); val pm = ds.drop(h)
            val a = am.sumOf { stopsOf(it) }; val b = pm.sumOf { stopsOf(it) }
            if (a - b >= I.getInt("flow_min_fewer")) add("flow", pm[0].getLong("t0"), JSONObject().put("am", a).put("am_n", am.size).put("pm", b).put("pm_n", pm.size))
        }
        // the favourite run of the day
        if (ds.isNotEmpty()) {
            val cnt = LinkedHashMap<String, Int>()
            for (x in ds) mainRuns(x, 1).firstOrNull()?.let { cnt[it] = (cnt[it] ?: 0) + 1 }
            if (cnt.isNotEmpty()) {
                val (fav, n) = cnt.entries.sortedWith(compareBy({ -it.value }, { it.key })).first().let { it.key to it.value }
                if (n >= I.getInt("favorite_min") && n >= I.getDouble("favorite_share") * ds.size) {
                    val t = ds.first { mainRuns(it, 1).firstOrNull() == fav }.getLong("t0")
                    add("favorite", t, JSONObject().put("run", fav).put("n", n).put("of", ds.size))
                }
            }
        }
        // where the day went: skiing, on lifts, in queues (minutes)
        if (day && ds.size >= I.getInt("time_split_min_descents")) {
            add("time_split", end, JSONObject().put("ski", rnd((num(st, "ski_s") ?: 0.0) / 60.0)).put("lift_min", rnd((num(st, "lift_s") ?: 0.0) / 60.0))
                .put("queue", rnd(qs.sumOf { it.s } / 60.0)))
        }
        // the steepest run skied today, and whether it is the steepest ever
        val stp = steepest(res, runs, M)
        if (stp != null && stp.deg >= I.getInt("steep_min_deg")) {
            val prev = if (hBest.has("steep_deg")) hBest.getInt("steep_deg") else null
            add("steepest", stp.t, JSONObject().put("run", stp.run).put("deg", stp.deg).put("ever", known && prev != null && stp.deg > prev))
        }
        // a lift whose queue is shorter at one time of the day
        val qp = if (day) queuePattern(res, M) else null
        if (qp != null) add("queue_pattern", end, qp)
        // one suggestion for tomorrow
        if (day) tomorrow(res, hist, runs, M, doneToday, qp)?.let { add("tomorrow", end, it) }
        // the end of the trip, on its last ski day (from its second day)
        val tr = newh.optJSONObject("trip")
        if (day && ds.isNotEmpty() && trip != null && trip.optBoolean("last", false) && tr != null && tr.get("start") == trip.get("start") && tr.getInt("days") >= I.getInt("trip_min_days")) {
            val tRuns = tr.getJSONObject("runs")
            val p = JSONObject().put("days", tr.getInt("days")).put("drop", rnd(tr.getDouble("drop_m"))).put("done", tRuns.length()).put("first", tr.getJSONObject("first"))
            if (tRuns.length() > 0) {
                val (run, n) = tRuns.keys().asSequence().map { it to tRuns.getInt(it) }.sortedWith(compareBy({ -it.second }, { it.first })).first()
                if (n >= 2) p.put("run", run).put("n", n)
            }
            add("trip", end, p)
        }

        val sorted = items.sortedWith(compareBy<Item>({ it.t }, { -prio.getInt(it.key) }, { it.key }))
        val records = strings(M.getJSONArray("records"))
        fun sub(m: Item) = if (m.key == "record") records.indexOf(m.p.getString("what")) else 0
        val rank = sorted.indices.sortedWith(compareBy<Int>({ -prio.getInt(sorted[it].key) }, { sub(sorted[it]) }, { sorted[it].t }, { sorted[it].key }))
        // the story: the best moments, at most story_per_key of a kind, in the order of the day
        val story = ArrayList<Int>(); val per = HashMap<String, Int>()
        val storyMax = M.getInt("story_max"); val perKey = M.getInt("story_per_key")
        val last = strings(M.getJSONArray("story_last"))
        for (i in rank) {
            val k = sorted[i].key
            if (k in last) continue
            if (story.size < storyMax && (per[k] ?: 0) < perKey) { story += i; per[k] = (per[k] ?: 0) + 1 }
        }
        // a day without a comparison against my own days gets an insight in its place (story_fallback, the first ranked)
        val keys = story.map { sorted[it].key }.toSet()
        val fallback = strings(M.getJSONArray("story_fallback"))
        if ("vs_day" !in keys && keys.none { it in fallback }) {
            val fb = rank.firstOrNull { sorted[it].key in fallback }
            if (fb != null) {
                if (story.size >= storyMax) story.removeAt(story.size - 1) // the last ranked of the story
                story += fb
            }
        }
        story.sort()
        for (k in last) story += sorted.indices.filter { sorted[it].key == k } // the end of the trip, then tomorrow: outside story_max, at the end
        val exclude = strings(M.getJSONArray("share_exclude"))
        val share = rank.firstOrNull { sorted[it].key !in exclude }
        val out = JSONObject().put("moments", M.getInt("version"))
        out.put("items", JSONArray().also { a -> sorted.forEach { a.put(JSONObject().put("key", it.key).put("t", it.t).put("seed", seed(it.key, it.t)).put("p", it.p)) } })
        out.put("story", JSONArray(story)).put("share", share ?: JSONObject.NULL).put("theme", theme(sorted, ds, M) ?: JSONObject.NULL).put("history", newh)
        return out
    }

    /**
     * The theme of the day, for the title of the story: progress (the first of a colour ever, or a record of the longest
     * descent), explore, endurance (a record of descents, drop or distance), favorite, easy (up to two descents), else
     * steady. A speed record never sets the theme. null with no descent.
     */
    private fun theme(items: List<Item>, ds: List<JSONObject>, M: JSONObject): String? {
        if (ds.isEmpty()) return null
        if (items.any { it.key == "first_color" && it.p.getBoolean("first_ever") } || items.any { it.key == "record" && it.p.getString("what") == "longest_m" }) return "progress"
        if (items.any { it.key == "new_runs" && it.p.getJSONArray("runs").length() >= M.getJSONObject("insights").getInt("explore_min_new") }) return "explore"
        if (items.any { it.key == "record" && it.p.getString("what") in DAY_SUM }) return "endurance"
        if (items.any { it.key == "favorite" }) return "favorite"
        if (ds.size <= 2) return "easy"
        return "steady"
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
        val stops = s.optJSONArray("stops")?.length() ?: 0
        val ds = descents(res)
        val p = JSONObject().put("t1", s.get("t1")).put("runs", JSONArray(mainRuns(s))).put("dist_m", s.get("dist_m")).put("drop_m", s.get("drop_m"))
            .put("moving_s", s.get("moving_s")).put("max_kmh", s.opt("max_kmh") ?: JSONObject.NULL)
            .put("falls", list(res.optJSONArray("falls")).count { it.getLong("t") in t0..t1 })
            .put("stops", stops).put("nonstop", stops == 0)
            .put("longest_so_far", ds.size > 1 && ds.filter { it.getLong("t0") != t0 }.all { s.getDouble("dist_m") > it.getDouble("dist_m") })
        return JSONObject().put("key", "last_descent").put("t", t0).put("seed", seed("last_descent", t0)).put("p", p)
    }
}

package io.github.pini236.skiapp.dayrec.analysis

import io.github.pini236.skiapp.dayrec.DayLog
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The day analysis (decision 76, docs/ARCHITECTURE.md m-8): raw log in, day model out. A port of tools/dayrec/analyze.py
 * step by step, in two modes over the same steps:
 *   [final] everything known, smoothed backwards (Kalman + RTS, a full Viterbi): the truth of the day;
 *   [live]  causal only, every value at time t uses readings up to t.
 * Every threshold comes from [Params] (tools/fixtures/dayrec-params.json). The results must match
 * tools/fixtures/dayrec-cases.json by the rules of tools/dayrec/compare.py; a difference that comes from a near tie in
 * floating point is reported to the tool's owner, never tuned away here (tools/dayrec/README.md).
 */
class Analyzer(private val net: Net, private val P: Params) {

    // ---------- results ----------

    class Gap(val t0: Long, val t1: Long)
    /** "Here you fell": [t] is the blow, [at] is when it is known; the place only in the final result. */
    class Fall(val t: Long, val at: Long, val lat: Double? = null, val lon: Double? = null)
    class MaxAt(val t: Long, val lat: Double, val lon: Double)
    class Stop(val t0: Long, val t1: Long)
    class OffPiste(val t0: Long, val t1: Long, val dropM: Double, val distM: Double)

    /** One part of the day: a lift ride, a descent, a stop, a walk or an ascent on foot. */
    class Seg(val type: String, var i0: Int, var i1: Int) {
        var t0 = 0L; var t1 = 0L
        internal var li = -1
        internal var pieces: List<Pc> = emptyList()
        // lift
        var lift: String? = null; var name: String? = null
        var from: String? = null; var fromUnmapped = false; var to: String? = null; var toUnmapped = false
        var riseM: Double? = null
        // descent
        var legs: List<Leg> = emptyList(); var distM: Double? = null; var dropM: Double? = null
        var movingS = 0; var avgKmh: Double? = null; var maxKmh: Double? = null; var maxAt: MaxAt? = null
        var stops: List<Stop> = emptyList(); var offpiste: List<OffPiste> = emptyList()
        var topM: Double? = null; var bottomM: Double? = null
        // stop
        var lat = 0.0; var lon = 0.0; var kind: String? = null
    }

    class Stats(
        val descents: Int, val lifts: Int, val skiDistM: Double, val dropM: Double, val skiS: Int, val liftS: Int,
        val liftRiseM: Double, val maxKmh: Double?, val maxAt: MaxAt?,
    )

    class DayResult(
        val points: Int, val start: Long, val end: Long, val baro: Boolean, val coverage: Double,
        val gaps: List<Gap>, val falls: List<Fall>, val segments: List<Seg>, val stats: Stats?,
    )

    /** A change of the live view: the activity (and the lift or the runs), from [t]. */
    class LiveEvent(val t: Long, val act: String, val lift: String?, val runs: List<String>?)
    class AutoStop(val t: Long, val reason: String)
    class LiveResult(val events: List<LiveEvent>, val maxKmh: Double?, val falls: List<Fall>, val autostop: AutoStop?)

    /** A piece of the day with one state (STAND, WALK, SKI, UP, or 4 + the lift's index). */
    internal class Pc(var s: Int, var i0: Int, var i1: Int) { fun copy() = Pc(s, i0, i1) }

    // ---------- shared ----------

    private class Prep(
        val xy: List<DoubleArray>, val kf: List<DoubleArray>, val ts: LongArray, val src: DoubleArray, val baro: Boolean,
        val h: DoubleArray, val vz: DoubleArray,
    )

    private fun prep(pts: List<DayLog.Point>, live: Boolean): Prep {
        val xy = pts.map { doubleArrayOf(net.proj.x(it.lon), net.proj.y(it.lat)) }
        val kf = kalman(xy, pts, P, smooth = !live)
        val ts = LongArray(pts.size) { pts[it].t }
        val (src, baro) = altSource(pts)
        val h = smoothAlt(ts, src, P, live)
        val vz = verticalSpeed(ts, h, P, live)
        return Prep(xy, kf, ts, src, baro, h, vz)
    }

    // ---------- 9. "Here you fell" (m-8 condition 10): fun, not safety ----------

    private fun impactPeaks(e: JSONObject): Pair<Double, Double> {
        fun peak(a: org.json.JSONArray?): Double {
            if (a == null) return 0.0
            var m = 0.0
            var i = 0
            while (i + 2 < a.length()) { m = max(m, sqrt(sq(a.getDouble(i)) + sq(a.getDouble(i + 1)) + sq(a.getDouble(i + 2)))); i += 3 }
            return m
        }
        return peak(e.optJSONArray("acc")) / G to peak(e.optJSONArray("gyr"))
    }

    /**
     * A fall: a blow (the impact window: at least trigger_g and rot_min_radps), while skiing (not on a lift), going at least
     * v_before_mps in the before_s before it, and at most v_after_mps through still_s after settle_s. A dismissed impact
     * ("I did not fall") never counts. [at] is when it is known (t + settle_s + still_s).
     */
    private fun falls(pts: List<DayLog.Point>, ts: LongArray, events: List<JSONObject>, act: IntArray): List<Fall> {
        val trigger = P.d("fall", "trigger_g"); val rot = P.d("fall", "rot_min_radps")
        val beforeMs = (P.d("fall", "before_s") * 1000).toLong(); val vBefore = P.d("fall", "v_before_mps")
        val settleMs = (P.d("fall", "settle_s") * 1000).toLong(); val stillMs = (P.d("fall", "still_s") * 1000).toLong()
        val vAfter = P.d("fall", "v_after_mps"); val stillS = P.d("fall", "still_s"); val debounceMs = P.d("fall", "debounce_s") * 1000
        val out = ArrayList<Fall>()
        // "I did not fall" is a second line with the same t (the log is append-only); it cancels the blow
        val gone = events.filter { it.optString("ev") == "impact" && it.optBoolean("dismissed", false) }.map { it.getLong("t") }.toSet()
        for (e in events.filter { it.optString("ev") == "impact" && it.has("t") }.sortedBy { it.getLong("t") }) {
            if (e.optBoolean("dismissed", false) || !e.has("acc") || e.getLong("t") in gone) continue
            val (pa, pw) = impactPeaks(e)
            if (pa < trigger || pw < rot) continue
            val t = e.getLong("t")
            val i = ts.indices.lastOrNull { ts[it] <= t } ?: continue
            if (act[i] >= 4) continue
            val before = ts.indices.filter { t - beforeMs <= ts[it] && ts[it] <= t }.mapNotNull { pts[it].speed }
            val a0 = t + settleMs; val a1 = a0 + stillMs
            val after = ts.indices.filter { a0 <= ts[it] && ts[it] <= a1 }.map { pts[it].speed }
            if (before.isEmpty() || before.max() < vBefore) continue
            if (after.size < stillS - 1 || after.any { it == null || it > vAfter }) continue
            if (out.isNotEmpty() && t - out.last().t < debounceMs) continue
            out += Fall(t, a1)
        }
        return out
    }

    // ---------- 10. One descent ("measure a run"): when it stops by itself ----------

    /**
     * In a recording of one descent (header mode "run"), the moment it stops by itself, from live values only: on a lift; or
     * still near a lift's boarding station for station_still_s; or still anywhere for still_s. Only after the skier went down
     * at least min_drop_m and skied at least min_ski_s.
     */
    private fun autostop(pts: List<DayLog.Point>, ts: LongArray, kf: List<DoubleArray>, act: IntArray): AutoStop? {
        val minDrop = P.d("autostop", "min_drop_m"); val minSki = P.d("autostop", "min_ski_s"); val still = P.d("autostop", "still_mps")
        val stationM = P.d("autostop", "station_m"); val stationStill = P.d("autostop", "station_still_s"); val stillS = P.d("autostop", "still_s")
        var top: Double? = null; var skiS = 0.0; var stillFrom = -1; var prev = -1
        var h: Double? = null
        for (i in pts.indices) {
            val a = act[i]
            val z = pts[i].pressure?.let { baroAlt(it) } ?: pts[i].alt
            if (z != null) { top = if (top == null) z else max(top, z); h = z }
            if (prev >= 0 && act[prev] == SKI) skiS += (ts[i] - ts[prev]) / 1000.0
            prev = i
            val went = top != null && h != null && top - h >= minDrop && skiS >= minSki
            if (!went) { stillFrom = -1; continue }
            if (a >= 4) return AutoStop(ts[i], "lift")
            val s = pts[i].speed
            if (s != null && s <= still) {
                if (stillFrom < 0) stillFrom = i
                val dur = (ts[i] - ts[stillFrom]) / 1000.0
                val x = kf[i][0]; val y = kf[i][1]
                val near = net.lifts.any { l -> (0 until l.stations.size - 1).any { k -> l.line.at(l.stations[k].u).let { hypot(x - it[0], y - it[1]) <= stationM } } }
                if (near && dur >= stationStill) return AutoStop(ts[i], "station")
                if (dur >= stillS) return AutoStop(ts[i], "still")
            } else stillFrom = -1
        }
        return null
    }

    // ---------- live ----------

    /** The live view as it changes: the list of changes, the live maximum speed, the falls and (a single run) the auto stop. */
    fun live(header: JSONObject, pts: List<DayLog.Point>, events: List<JSONObject> = emptyList()): LiveResult {
        if (pts.isEmpty()) return LiveResult(emptyList(), null, emptyList(), null)
        val p = prep(pts, true)
        val em = emissions(net, pts, p.kf, p.vz, P)
        val act = viterbiActivity(net, em.e, em.feats, P, live = true)
        val mt = match(net, pts, p.kf, pts.indices.toList(), P, live = true)
        val evs = ArrayList<LiveEvent>()
        var last: Triple<String, String?, List<String>?>? = null
        for ((i, a) in act.withIndex()) {
            val actName = if (a >= 4) "lift" else ACT[a]
            val lift = if (a >= 4) net.lifts[a - 4].id else null
            val runs: List<String>? = if (a == SKI) mt[i].piste.let { s -> if (s >= 0) net.labelAt(s, p.kf[i][0], p.kf[i][1]) else null } else null
            val key = Triple(actName, lift, runs)
            if (key != last) { evs += LiveEvent(p.ts[i], actName, lift, runs); last = key }
        }
        // live maximum: a reading counts once its window is complete (2 s later), only while skiing
        val ok = validSpeeds(pts, p.xy, P)
        val ski = act.indices.filter { act[it] == SKI }
        val best = maxSpeed(pts, ok, ski, P)
        return LiveResult(evs, best?.let { kmh(it.first) }, falls(pts, p.ts, events, act),
            if (header.optString("mode", "day") == "run") autostop(pts, p.ts, p.kf, act) else null)
    }

    // ---------- final ----------

    fun final(header: JSONObject, pts: List<DayLog.Point>, events: List<JSONObject> = emptyList()): DayResult {
        if (pts.isEmpty()) return DayResult(0, 0, 0, false, 0.0, emptyList(), emptyList(), emptyList(), null)
        val p = prep(pts, false)
        val em = emissions(net, pts, p.kf, p.vz, P)
        val path = viterbiActivity(net, em.e, em.feats, P, live = false)
        val pieces = pieces(path, p.ts, p.kf, em.feats, pts, p.h)
        val segs = segments(pieces, pts, p.xy, p.kf, p.ts, p.h)
        val state = IntArray(pts.size)
        for (pc in pieces) for (i in pc.i0..pc.i1) state[i] = pc.s
        val fl = falls(pts, p.ts, events, state).map { f ->
            val i = p.ts.indices.last { p.ts[it] <= f.t }
            Fall(f.t, f.at, Math.rint(net.proj.lat(p.kf[i][1]) * 1e6) / 1e6, Math.rint(net.proj.lon(p.kf[i][0]) * 1e6) / 1e6)
        }
        calibrate(segs, p.ts, p.src)
        val gaps = (1 until p.ts.size).filter { p.ts[it] - p.ts[it - 1] > P.gapS * 1000 }.map { Gap(p.ts[it - 1], p.ts[it]) }
        val span = (p.ts.last() - p.ts.first()) / 1000.0 + 1
        return DayResult(pts.size, p.ts.first(), p.ts.last(), p.baro, Math.rint(pts.size / span * 1e4) / 1e4, gaps, fl, segs, stats(segs))
    }

    private fun dur(ts: LongArray, pc: Pc) = (ts[pc.i1] - ts[pc.i0]) / 1000.0

    private fun mergeEqual(ps: List<Pc>): ArrayList<Pc> {
        val out = ArrayList<Pc>()
        for (p in ps) if (out.isNotEmpty() && out.last().s == p.s) out.last().i1 = p.i1 else out += p.copy()
        return out
    }

    private fun quiet(i0: Int, i1: Int, kf: List<DoubleArray>): Pc {
        var sum = 0.0
        for (i in i0..i1) sum += hypot(kf[i][2], kf[i][3])
        val vh = sum / (i1 - i0 + 1)
        return Pc(if (vh < P.d("activity", "walk_mean_mps") / 2) STAND else WALK, i0, i1)
    }

    /**
     * The first index (up: skiing starts) or the last index (down: skiing ends) near i, within edge_window_s, where the raw
     * Doppler speed is at least ski_edge_mps for edge_hold readings in a row. null if not found.
     */
    private fun edge(pts: List<DayLog.Point>, ts: LongArray, i: Int, lo: Int, hi: Int, up: Boolean): Int? {
        val w = P.d("segments", "edge_window_s") * 1000
        val v = P.d("segments", "ski_edge_mps"); val n = P.i("segments", "edge_hold")
        fun fast(k: Int) = pts[k].speed.let { it != null && it >= v }
        val cands = (lo + 1..hi).filter { abs(ts[it] - ts[i]) <= w }
        if (up) {
            for (k in cands) if (k + n - 1 <= hi && (k until k + n).all { fast(it) }) return k
        } else {
            for (k in cands.asReversed()) if (k - n + 1 >= lo && (k - n + 1..k).all { fast(it) }) return k
        }
        return null
    }

    private fun pieces(path: IntArray, ts: LongArray, kf: List<DoubleArray>, feats: List<Map<Int, LiftFeature>>, pts: List<DayLog.Point>, h: DoubleArray): List<Pc> {
        val S = "segments"; val L = "lift"
        var pcs = ArrayList<Pc>()
        for ((i, s) in path.withIndex()) if (pcs.isNotEmpty() && pcs.last().s == s) pcs.last().i1 = i else pcs += Pc(s, i, i)

        fun u(i: Int, li: Int) = net.lifts[li].line.nearest(kf[i][0], kf[i][1]).s
        // a lift stopped for a while, or the signal wandered: the same lift again within pause_max_s, with no skiing in
        // between and not further down the cable, is one ride
        val pauseMax = P.d(L, "pause_max_s"); val snap = P.d(L, "station_snap_m")
        var j = 0
        while (j < pcs.size) {
            val a = pcs[j]
            if (a.s < 4) { j++; continue }
            var k = j + 1
            while (k < pcs.size && pcs[k].s in intArrayOf(STAND, WALK, UP) && (ts[pcs[k].i1] - ts[a.i1]) / 1000.0 <= pauseMax) k++
            if (k > j + 1 && k < pcs.size && pcs[k].s == a.s && (ts[pcs[k].i0] - ts[a.i1]) / 1000.0 <= pauseMax &&
                u(pcs[k].i0, a.s - 4) >= u(a.i1, a.s - 4) - snap
            ) {
                a.i1 = pcs[k].i1
                for (r in k downTo j + 1) pcs.removeAt(r)
            } else j++
        }
        // a "ride" that does not climb along the cable is not a ride
        val minProgress = P.d(L, "min_progress_m")
        for (pc in pcs) {
            if (pc.s >= 4) {
                val li = pc.s - 4
                val u0 = feats[pc.i0][li]?.u; val u1 = feats[pc.i1][li]?.u
                if (u0 == null || u1 == null || u1 - u0 < minProgress) {
                    var sum = 0.0
                    for (i in pc.i0..pc.i1) sum += hypot(kf[i][2], kf[i][3])
                    pc.s = if (sum / (pc.i1 - pc.i0 + 1) < 0.5) STAND else UP
                }
            }
        }
        pcs = mergeEqual(pcs)
        // the edges of a ride, to the second: it starts where the position along the cable starts to grow and ends where it
        // stops growing (standing in the queue or at the top station is not riding)
        var out = ArrayList<Pc>()
        val edgeMps = P.d(L, "edge_mps"); val hold = P.i(L, "edge_hold")
        for (pc in pcs) {
            if (pc.s < 4) { out += pc; continue }
            val line = net.lifts[pc.s - 4].line
            val idx = (pc.i0..pc.i1).toList()
            val us = idx.map { line.nearest(kf[it][0], kf[it][1]).s }
            // moving[k]: from reading k to k+1 the position along the cable grew at edge_mps or more
            val mv = (0 until us.size - 1).map { k -> (us[k + 1] - us[k]) / max(0.001, (ts[idx[k + 1]] - ts[idx[k]]) / 1000.0) >= edgeMps }
            val starts = (0..mv.size - hold).filter { k -> (k until k + hold).all { mv[it] } }
            if (starts.isEmpty()) { out += pc; continue }
            val kst = starts.first()
            val kend = starts.maxOf { it + hold }
            val a = pc.i0 + kst; val b = pc.i0 + kend
            if (a > pc.i0) out += quiet(pc.i0, a - 1, kf)
            out += Pc(pc.s, a, b)
            if (b < pc.i1) out += quiet(b + 1, pc.i1, kf)
        }
        pcs = mergeEqual(out)
        // a few seconds of "ski" that do not go down are the filter slowing down (off a lift), not skiing
        val skiMin = P.d(S, "ski_min_s"); val hyst = P.d("alt", "hyst_m")
        for (pc in pcs) if (pc.s == SKI && dur(ts, pc) < skiMin && drop(h, pc.i0..pc.i1, P) < hyst) pc.s = WALK
        pcs = mergeEqual(pcs)
        // the edges of skiing, to the second: where the raw Doppler speed crosses ski_edge_mps (held edge_hold readings)
        for (jj in 0 until pcs.size - 1) {
            val a = pcs[jj]; val b = pcs[jj + 1]
            if ((a.s == STAND || a.s == WALK) && b.s == SKI) {
                val k = edge(pts, ts, b.i0, a.i0, b.i1, up = true)
                if (k != null) { a.i1 = k - 1; b.i0 = k }
            } else if (a.s == SKI && (b.s == STAND || b.s == WALK)) {
                val k = edge(pts, ts, a.i1, a.i0, b.i1, up = false)
                if (k != null) { a.i1 = k; b.i0 = k + 1 }
            }
        }
        // skiing next to a lift (straight off it, or straight onto it): the slow seconds become a stop or a walk
        out = ArrayList()
        for ((jj, pc0) in pcs.withIndex()) {
            var pc = pc0
            if (pc.s != SKI) { out += pc; continue }
            if (jj > 0 && pcs[jj - 1].s >= 4) {
                val k = edge(pts, ts, pc.i0, pc.i0 - 1, pc.i1, up = true)
                if (k != null && k > pc.i0) { out += quiet(pc.i0, k - 1, kf); pc = Pc(pc.s, k, pc.i1) }
            }
            var tail: Pc? = null
            if (jj + 1 < pcs.size && pcs[jj + 1].s >= 4) {
                val k = edge(pts, ts, pc.i1, pc.i0, pc.i1, up = false)
                if (k != null && k < pc.i1) { tail = quiet(k + 1, pc.i1, kf); pc = Pc(pc.s, pc.i0, k) }
            }
            out += pc
            if (tail != null) out += tail
        }
        pcs = mergeEqual(out.filter { it.i1 >= it.i0 })
        // short stand, walk and up pieces join a neighbouring stand, walk or up piece. Never a lift or a descent: their edges
        // are the moments the state changed, to the second. Between a lift and a descent a short piece stays (a real stop at
        // the top), unless it is a flicker shorter than flicker_s.
        val minPiece = P.d(S, "min_piece_s"); val flicker = P.d(S, "flicker_s")
        fun isQuiet(s: Int) = s == STAND || s == WALK || s == UP
        var changed = true
        while (changed && pcs.size > 1) {
            changed = false
            for ((jj, pc) in pcs.withIndex()) {
                if (!isQuiet(pc.s) || dur(ts, pc) >= minPiece) continue
                val prev = if (jj > 0) pcs[jj - 1] else null
                val nxt = if (jj + 1 < pcs.size) pcs[jj + 1] else null
                if (prev != null && isQuiet(prev.s)) {
                    if (dur(ts, pc) > dur(ts, prev)) prev.s = pc.s // the longer of the two names the joined piece
                    prev.i1 = pc.i1
                } else if (nxt != null && isQuiet(nxt.s)) {
                    if (dur(ts, pc) > dur(ts, nxt)) nxt.s = pc.s
                    nxt.i0 = pc.i0
                } else if (dur(ts, pc) < flicker && prev != null) prev.i1 = pc.i1
                else if (dur(ts, pc) < flicker && nxt != null) nxt.i0 = pc.i0
                else continue
                pcs.removeAt(jj)
                pcs = mergeEqual(pcs)
                changed = true
                break
            }
        }
        return pcs
    }

    private fun dist(kf: List<DoubleArray>, ts: LongArray, idx: List<Int>): Double {
        var s = 0.0
        for (k in 0 until idx.size - 1) {
            val a = idx[k]; val b = idx[k + 1]
            if ((ts[b] - ts[a]) / 1000.0 <= P.resetGapS) s += hypot(kf[b][0] - kf[a][0], kf[b][1] - kf[a][1])
        }
        return s
    }

    private fun station(lift: Lift, u: Double): Pair<String?, Boolean> {
        val st = lift.stations.minByOrNull { abs(it.u - u) }!!
        return if (abs(st.u - u) <= P.d("lift", "station_snap_m")) st.id to false else null to true
    }

    private fun segments(pcs: List<Pc>, pts: List<DayLog.Point>, xy: List<DoubleArray>, kf: List<DoubleArray>, ts: LongArray, h: DoubleArray): List<Seg> {
        val S = "segments"
        val restMin = P.d(S, "rest_min_s"); val upIn = P.d(S, "up_in_descent_s")
        // descents: ski pieces joined across short stands and walks
        val groups = ArrayList<Pair<Int, List<Pc>>>() // kind -1 = a descent
        var j = 0
        while (j < pcs.size) {
            val p = pcs[j]
            if (p.s == SKI) {
                val g = arrayListOf(p); var k = j + 1
                while (k < pcs.size) {
                    val q = pcs[k]
                    if (q.s == SKI) { g += q; k++; continue }
                    if (((q.s == STAND || q.s == WALK) && dur(ts, q) < restMin || q.s == UP && dur(ts, q) < upIn) && k + 1 < pcs.size && pcs[k + 1].s == SKI) { g += q; k++; continue }
                    break
                }
                groups += -1 to g; j = k
            } else { groups += p.s to listOf(p); j++ }
        }
        var segs = ArrayList<Seg>()
        for ((kind, g) in groups) {
            val i0 = g.first().i0; val i1 = g.last().i1
            if (kind == -1) {
                val idx = (i0..i1).toList()
                val d = dist(kf, ts, idx); val dr = drop(h, idx, P)
                if (dr < P.d(S, "min_descent_drop_m") && d < P.d(S, "min_descent_m")) { segs += Seg("walk", i0, i1); continue }
                segs += Seg("descent", i0, i1).also { it.pieces = g }
            } else if (kind >= 4) segs += Seg("lift", i0, i1).also { it.li = kind - 4 }
            else if (kind == UP) segs += Seg("ascent", i0, i1)
            else if (kind == WALK) segs += Seg("walk", i0, i1)
            else segs += Seg("stop", i0, i1)
        }
        // neighbouring walks (a short descent that became a walk) join
        val joined = ArrayList<Seg>()
        for (s in segs) if (joined.isNotEmpty() && s.type == "walk" && joined.last().type == "walk") joined.last().i1 = s.i1 else joined += s
        segs = joined
        val ok = validSpeeds(pts, xy, P)
        for ((n, s) in segs.withIndex()) {
            val i0 = s.i0; val i1 = s.i1
            s.t0 = ts[i0]; s.t1 = ts[i1]
            val idx = (i0..i1).toList()
            when (s.type) {
                "lift" -> {
                    val lift = net.lifts[s.li]
                    val u0 = lift.line.nearest(kf[i0][0], kf[i0][1]).s; val u1 = lift.line.nearest(kf[i1][0], kf[i1][1]).s
                    s.lift = lift.id; s.name = lift.name
                    station(lift, u0).let { s.from = it.first; s.fromUnmapped = it.second }
                    station(lift, u1).let { s.to = it.first; s.toUnmapped = it.second }
                    s.riseM = round1(lift.eleAt(u1) - lift.eleAt(u0))
                }
                "descent" -> {
                    val ski = s.pieces.filter { it.s == SKI }.flatMap { (it.i0..it.i1).toList() }
                    val mt = match(net, pts, kf, idx, P, live = false)
                    s.legs = legs(net, pts, kf, idx, mt, P)
                    s.distM = round1(dist(kf, ts, idx))
                    s.dropM = round1(drop(h, idx, P))
                    val mov = s.pieces.filter { it.s == SKI }.sumOf { (ts[it.i1] - ts[it.i0]) / 1000.0 }
                    s.movingS = Math.rint(mov).toInt()
                    s.avgKmh = if (mov > 0) kmh(s.distM!! / mov) else null
                    val best = maxSpeed(pts, ok, ski, P)
                    if (best != null) {
                        val (v, at) = best
                        s.maxKmh = kmh(v)
                        s.maxAt = MaxAt(ts[at], Math.rint(net.proj.lat(kf[at][1]) * 1e6) / 1e6, Math.rint(net.proj.lon(kf[at][0]) * 1e6) / 1e6)
                    } else s.maxKmh = null
                    s.stops = s.pieces.filter { it.s != SKI }.map { Stop(ts[it.i0], ts[it.i1]) }
                    s.offpiste = offpiste(kf, ts, h, idx)
                }
                "stop" -> {
                    val mx = idx.sumOf { kf[it][0] } / idx.size; val my = idx.sumOf { kf[it][1] } / idx.size
                    s.lat = Math.rint(net.proj.lat(my) * 1e6) / 1e6; s.lon = Math.rint(net.proj.lon(mx) * 1e6) / 1e6
                    val nxt = if (n + 1 < segs.size) segs[n + 1] else null
                    var kind = "rest"
                    if (nxt != null && nxt.type == "lift" && (ts[nxt.i0] - ts[i1]) / 1000.0 <= P.d(S, "queue_gap_s")) {
                        val lift = net.lifts[nxt.li]
                        val u = lift.line.nearest(mx, my).s
                        val st = lift.stations.subList(0, lift.stations.size - 1).minByOrNull { abs(it.u - u) }!!
                        val pt = lift.line.at(st.u)
                        if (hypot(mx - pt[0], my - pt[1]) <= P.d(S, "queue_dist_m")) kind = "queue"
                    }
                    if (kind == "rest" && (ts[i1] - ts[i0]) / 1000.0 >= P.d(S, "long_break_s")) kind = "long"
                    s.kind = kind
                }
                "walk" -> s.distM = round1(dist(kf, ts, idx))
                "ascent" -> s.riseM = round1(h[i1] - h[i0])
            }
        }
        return segs
    }

    /**
     * Off-piste events in a descent: a stretch going down at least drop_m, of which at least far_frac of the readings are more
     * than far_m from every run and road. Stretches of far readings join across bridge_s.
     */
    private fun offpiste(kf: List<DoubleArray>, ts: LongArray, h: DoubleArray, idx: List<Int>): List<OffPiste> {
        val farM = P.d("offpiste", "far_m"); val bridge = P.d("offpiste", "bridge_s"); val farFrac = P.d("offpiste", "far_frac"); val dropM = P.d("offpiste", "drop_m")
        val far = idx.map { net.nearestPisteDist(kf[it][0], kf[it][1]) > farM && net.nearestRoadDist(kf[it][0], kf[it][1]) > farM }
        val runs = ArrayList<IntArray>()
        var n = 0
        while (n < idx.size) {
            if (far[n]) {
                var m = n
                while (m + 1 < idx.size && far[m + 1]) m++
                if (runs.isNotEmpty() && (ts[idx[n]] - ts[idx[runs.last()[1]]]) / 1000.0 <= bridge) runs.last()[1] = m
                else runs += intArrayOf(n, m)
                n = m + 1
            } else n++
        }
        val out = ArrayList<OffPiste>()
        for ((a, b) in runs.map { it[0] to it[1] }) {
            val sub = idx.subList(a, b + 1)
            val frac = far.subList(a, b + 1).count { it }.toDouble() / sub.size
            val dr = drop(h, sub, P)
            if (frac >= farFrac && dr >= dropM) out += OffPiste(ts[sub.first()], ts[sub.last()], round1(dr), round1(dist(kf, ts, sub)))
        }
        return out
    }

    /** Absolute altitude: offsets measured at known stations (median, jump guard); applied from then on. */
    private fun calibrate(segs: List<Seg>, ts: LongArray, src: DoubleArray) {
        val w = P.d("alt", "cal_window_s") * 1000 / 2
        val meas = ArrayList<Pair<Long, Double>>()
        for (s in segs) {
            if (s.type != "lift") continue
            val lift = net.lifts[s.li]
            for ((sid, i) in listOf(s.from to s.i0, s.to to s.i1)) {
                if (sid == null) continue
                val st = lift.stations.first { it.id == sid }
                meas += ts[i] to (st.ele - windowMean(ts, src, i, ts[i] - w, ts[i] + w))
            }
        }
        val offs = offsets(meas, P)
        fun alt(i: Int): Double? {
            if (offs.isEmpty()) return null
            var o = offs[0].second
            for ((t, v) in offs) if (t <= ts[i]) o = v
            return round1(src[i] + o)
        }
        for (s in segs) if (s.type == "descent") { s.topM = alt(s.i0); s.bottomM = alt(s.i1) }
    }

    private fun stats(segs: List<Seg>): Stats {
        val d = segs.filter { it.type == "descent" }; val l = segs.filter { it.type == "lift" }
        val best = d.filter { it.maxKmh != null }.maxByOrNull { it.maxKmh!! }
        return Stats(
            d.size, l.size, round1(d.sumOf { it.distM!! }), round1(d.sumOf { it.dropM!! }), d.sumOf { it.movingS },
            Math.rint(l.sumOf { (it.t1 - it.t0) / 1000.0 }).toInt(), round1(l.sumOf { it.riseM!! }), best?.maxKmh, best?.maxAt,
        )
    }

    companion object {
        const val G = 9.80665
        private val ACT = arrayOf("stand", "walk", "ski", "up")
    }
}

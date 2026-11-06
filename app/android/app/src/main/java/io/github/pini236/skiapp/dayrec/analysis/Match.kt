package io.github.pini236.skiapp.dayrec.analysis

import io.github.pini236.skiapp.dayrec.DayLog
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** Steps 5 and 6 of the day analysis: map matching of a descent (a Viterbi over the lines plus "off"), and its legs. A port of analyze.py. */

/** Per point: the piste index (or -1 for "off") and the distance to it. */
internal class Matched(val piste: Int, val dist: Double)

internal fun match(net: Net, pts: List<DayLog.Point>, kf: List<DoubleArray>, idx: List<Int>, P: Params, live: Boolean): List<Matched> {
    val offE = -0.5 * sq(P.d("match", "off_z"))
    val candM = P.d("match", "cand_m"); val sigMin = P.d("match", "sigma_min_m"); val half = P.d("match", "halfwidth_m")
    val offSwitch = P.d("match", "off_switch"); val sw = P.d("match", "switch"); val jump = P.d("match", "jump"); val nearM = P.d("match", "near_m")
    var prev: Map<Int, Double>? = null
    val bps = ArrayList<Map<Int, Int>>(); val outLive = ArrayList<Int>(); val cands = ArrayList<Map<Int, Double>>()
    for ((n, i) in idx.withIndex()) {
        val x = kf[i][0]; val y = kf[i][1]
        val ha = pts[i].hAcc ?: 10.0
        val sig = sqrt(sq(max(sigMin, ha)) + sq(half))
        val near = LinkedHashMap<Int, Double>(); for ((k, d) in net.nearPistes(x, y, candM)) near[k] = d
        cands += near
        val cur = LinkedHashMap<Int, Double>(); cur[-1] = offE
        for ((k, d) in near) cur[k] = -0.5 * sq(d / sig)
        var V: Map<Int, Double>; val bp: Map<Int, Int>
        if (prev == null) { V = cur; bp = cur.keys.associateWith { it } }
        else {
            val px = kf[idx[n - 1]][0]; val py = kf[idx[n - 1]][1]
            val nv = LinkedHashMap<Int, Double>(); val nb = LinkedHashMap<Int, Int>()
            for ((s, e) in cur) {
                var best = Double.NaN; var arg = 0; var have = false
                for ((r, v) in prev) {
                    val c = if (r == s) 0.0
                    else if (r == -1 || s == -1) offSwitch
                    else {
                        var cc = sw
                        if ((cands[n - 1][r] ?: 1e9) > nearM && net.pistes[r].dist(px, py) > nearM) cc += jump
                        cc
                    }
                    val sc = v - c
                    if (!have || sc > best || (sc == best && r < arg)) { best = sc; arg = r; have = true }
                }
                nv[s] = best + e; nb[s] = arg
            }
            val mx = nv.values.max()
            V = nv.mapValuesTo(LinkedHashMap()) { it.value - mx }
            bp = nb
        }
        bps += bp
        prev = V
        outLive += V.entries.maxWith(compareBy<Map.Entry<Int, Double>>({ it.value }, { -it.key })).key
    }
    val path = IntArray(idx.size)
    if (live) outLive.forEachIndexed { k, s -> path[k] = s }
    else if (idx.isNotEmpty()) {
        path[idx.size - 1] = prev!!.entries.maxWith(compareBy<Map.Entry<Int, Double>>({ it.value }, { -it.key })).key
        for (n in idx.size - 1 downTo 1) path[n - 1] = bps[n][path[n]]!!
    }
    return path.mapIndexed { n, s ->
        if (s == -1) Matched(-1, cands[n].values.minOrNull() ?: 1e9) else Matched(s, cands[n][s] ?: 1e9)
    }
}

/** A run of a descent: the named runs (or unnamed, off, unmatched) between two times. */
class Leg(var t0: Long, var t1: Long, var kind: String, var runs: List<String>, var distM: Double)

private class Piece(val key: List<String>, var n0: Int, var n1: Int) { fun copy() = Piece(key, n0, n1) }

/** Group a matched descent into legs: run (names), unnamed, off, unmatched. */
internal fun legs(net: Net, pts: List<DayLog.Point>, kf: List<DoubleArray>, idx: List<Int>, mt: List<Matched>, P: Params): List<Leg> {
    val minLeg = P.d("match", "min_leg_m"); val offMin = P.d("match", "off_min_m")
    val meanMax = P.d("match", "leg_mean_max_m"); val inFrac = P.d("match", "leg_in_frac")
    val sigMin = P.d("match", "sigma_min_m"); val half = P.d("match", "halfwidth_m")
    val raw = ArrayList<List<String>>()
    for ((n, i) in idx.withIndex()) {
        val s = mt[n].piste
        if (s == -1) raw += listOf("off")
        else { val lab = net.labelAt(s, kf[i][0], kf[i][1]); raw += if (lab.isNotEmpty()) listOf("run") + lab else listOf("unnamed") }
    }
    // pieces of equal label
    var pieces = ArrayList<Piece>()
    for ((n, k) in raw.withIndex()) {
        if (pieces.isNotEmpty() && pieces.last().key == k) pieces.last().n1 = n else pieces += Piece(k, n, n)
    }

    fun step(a: Int, b: Int) = hypot(kf[idx[a]][0] - kf[idx[b]][0], kf[idx[a]][1] - kf[idx[b]][1])
    fun length(pc: Piece): Double {
        var s = 0.0
        for (n in pc.n0 + 1..pc.n1) s += step(n, n - 1)
        if (pc.n0 > 0) s += step(pc.n0, pc.n0 - 1) // the step into the piece belongs to it
        return s
    }
    fun mergeEqual(ps: List<Piece>): ArrayList<Piece> {
        val out = ArrayList<Piece>()
        for (pc in ps) if (out.isNotEmpty() && out.last().key == pc.key) out.last().n1 = pc.n1 else out += pc.copy()
        return out
    }

    // short pieces join the one before (or after, for the first)
    var changed = true
    while (changed && pieces.size > 1) {
        changed = false
        for ((j, pc) in pieces.withIndex()) {
            if (length(pc) < minLeg) {
                if (j > 0) pieces[j - 1].n1 = pc.n1 else pieces[1].n0 = pc.n0
                pieces.removeAt(j)
                pieces = mergeEqual(pieces)
                changed = true
                break
            }
        }
    }
    val out = ArrayList<Leg>()
    for (pc in pieces) {
        val k = pc.key
        val ns = pc.n0..pc.n1
        var kind = k[0]
        if (kind == "off") {
            val nd = ns.map { net.nearestPisteDist(kf[idx[it]][0], kf[idx[it]][1]) }
            if (nd.sum() / nd.size < offMin) kind = "unmatched"
        } else {
            val ds = ns.map { mt[it].dist }
            var sigIn = 0
            for (n in ns) {
                val ha = pts[idx[n]].hAcc ?: 10.0
                val sig = sqrt(sq(max(sigMin, ha)) + sq(half))
                if (mt[n].dist <= 2 * sig) sigIn++
            }
            if (ds.sum() / ds.size > meanMax || sigIn < inFrac * ds.size) kind = "unmatched"
        }
        out += Leg(pts[idx[pc.n0]].t, pts[idx[pc.n1]].t, kind, if (kind == "run") k.drop(1) else emptyList(), round1(length(pc)))
    }
    // equal neighbours after reclassifying
    val res = ArrayList<Leg>()
    for (lg in out) {
        if (res.isNotEmpty() && res.last().kind == lg.kind && res.last().runs == lg.runs) {
            res.last().t1 = lg.t1; res.last().distM = round1(res.last().distM + lg.distM)
        } else res += lg
    }
    return res
}

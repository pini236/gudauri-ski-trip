package io.github.pini236.skiapp.dayrec.analysis

import io.github.pini236.skiapp.dayrec.DayLog
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Step 4 of the day analysis: activity, a Viterbi over stand, walk, ski, up and one state per lift. A port of analyze.py. */

internal const val NEG = -1e9
internal const val STAND = 0
internal const val WALK = 1
internal const val SKI = 2
internal const val UP = 3
/** Set after the activity Viterbi, from the roads (driveMask); a lift is 4 and up. -1 is taken by the descent group of the segments. */
internal const val DRIVE = -2

/** A lift close enough to a point: its distance, the position along the cable and the cable's direction there. */
internal class LiftFeature(val d: Double, val u: Double, val ux: Double, val uy: Double)

internal fun liftFeatures(net: Net, x: Double, y: Double, P: Params): Map<Int, LiftFeature> {
    val out = HashMap<Int, LiftFeature>(4)
    val m = P.d("lift", "max_dist_m")
    for (l in net.lifts) {
        val b = l.line.box
        if (x < b[0] - m || x > b[2] + m || y < b[1] - m || y > b[3] + m) continue
        val n = l.line.nearest(x, y)
        if (n.d <= m) { val a = l.line.at(n.s); out[l.idx] = LiftFeature(n.d, n.s, a[2], a[3]) }
    }
    return out
}

internal class Emissions(val e: List<DoubleArray>, val feats: List<Map<Int, LiftFeature>>)

internal fun emissions(net: Net, pts: List<DayLog.Point>, kf: List<DoubleArray>, vz: DoubleArray, P: Params): Emissions {
    val nl = net.lifts.size
    val E = ArrayList<DoubleArray>(pts.size); val feats = ArrayList<Map<Int, LiftFeature>>(pts.size)
    val standSigma = P.d("activity", "stand_sigma_mps"); val levelSigma = P.d("activity", "level_vz_sigma_mps")
    val walkMean = P.d("activity", "walk_mean_mps"); val walkSigma = P.d("activity", "walk_sigma_mps")
    val skiMin = P.d("activity", "ski_min_mps"); val skiSigma = P.d("activity", "ski_sigma_mps"); val skiVz = P.d("activity", "ski_vz_sigma_mps")
    val upVz = P.d("activity", "up_vz_mps"); val upSigma = P.d("activity", "up_vz_sigma_mps"); val upCost = P.d("activity", "up_cost")
    val motionCap = P.d("activity", "motion_cap"); val motionLow = P.d("activity", "motion_low"); val motionHigh = P.d("activity", "motion_high"); val motionSigma = P.d("activity", "motion_sigma")
    val sigmaM = P.d("lift", "sigma_m"); val speedLo = P.d("lift", "speed_lo"); val speedHi = P.d("lift", "speed_hi")
    val speedRel = P.d("lift", "speed_rel_sigma"); val cross = P.d("lift", "cross_sigma_mps"); val vzMin = P.d("lift", "vz_min_mps"); val vzSigma = P.d("lift", "vz_sigma_mps")
    for (i in pts.indices) {
        val pt = pts[i]
        val x = kf[i][0]; val y = kf[i][1]; val vx = kf[i][2]; val vy = kf[i][3]
        val vh = hypot(vx, vy)
        val z = vz[i]
        val m = pt.motion
        val lvl = -0.5 * sq(z / levelSigma)
        val mlow = if (m == null) 0.0 else -min(motionCap, 0.5 * sq(m / motionLow))
        val mhigh = if (m == null) 0.0 else -min(motionCap, 0.5 * sq(max(0.0, motionHigh - m) / motionSigma))
        val e = DoubleArray(4 + nl)
        e[STAND] = -0.5 * sq(vh / standSigma) + lvl + mlow
        e[WALK] = -0.5 * sq((vh - walkMean) / walkSigma) + lvl + mhigh
        e[SKI] = -0.5 * sq(max(0.0, skiMin - vh) / skiSigma) - 0.5 * sq(max(0.0, z) / skiVz) + mhigh
        e[UP] = -0.5 * sq(max(0.0, upVz - z) / upSigma) - upCost
        val f = liftFeatures(net, x, y, P)
        feats += f
        val ha = pt.hAcc ?: 10.0
        val sd = max(sigmaM, ha)
        for (li in 0 until nl) {
            val lf = f[li]
            if (lf == null) { e[4 + li] = NEG; continue }
            val sp = net.lifts[li].speed
            val va = vx * lf.ux + vy * lf.uy
            val vc = -vx * lf.uy + vy * lf.ux
            // the speed in the data is a guide, not a rule: lifts slow down and the data can be old
            val over = max(max(0.0, speedLo * sp - va), va - speedHi * sp)
            e[4 + li] = -0.5 * sq(lf.d / sd) - 0.5 * sq(over / (speedRel * sp)) - 0.5 * sq(vc / cross) -
                0.5 * sq(max(0.0, vzMin - z) / vzSigma) + mlow
        }
        E += e
    }
    return Emissions(E, feats)
}

internal fun sq(v: Double) = v * v

private fun boardOk(net: Net, li: Int, f: Map<Int, LiftFeature>, P: Params): Boolean {
    val lf = f[li] ?: return false
    val st = net.lifts[li].stations
    return (0 until st.size - 1).any { Math.abs(lf.u - st[it].u) <= P.d("lift", "board_m") }
}

/** The index with the highest value, the lowest index on a tie (Python: max(range(S), key=(V[s], -s))). */
private fun argmax(v: DoubleArray): Int { var b = 0; for (s in 1 until v.size) if (v[s] > v[b]) b = s; return b }

/** The path through the activity states: live = the best state at each step, final = back-traced from the end. */
internal fun viterbiActivity(net: Net, E: List<DoubleArray>, feats: List<Map<Int, LiftFeature>>, P: Params, live: Boolean): IntArray {
    val sw0 = P.d("activity", "switch"); val far = P.d("activity", "board_far")
    val T = E.size
    if (T == 0) return IntArray(0)
    val S = E[0].size
    var V = E[0].copyOf()
    val bps = ArrayList<IntArray>(T)
    val cur = IntArray(T)
    cur[0] = argmax(V)
    for (t in 1 until T) {
        // best and second best of the previous step
        var b1 = -1; var b2 = -1
        for (s in 0 until S) {
            if (b1 < 0 || V[s] > V[b1]) { b2 = b1; b1 = s }
            else if (b2 < 0 || V[s] > V[b2]) b2 = s
        }
        val nv = DoubleArray(S); val bp = IntArray(S)
        for (s in 0 until S) {
            val o = if (b1 != s) b1 else b2
            var sw = V[o] - sw0
            if (s >= 4 && !boardOk(net, s - 4, feats[t], P)) sw -= far
            if (V[s] >= sw) { nv[s] = V[s] + E[t][s]; bp[s] = s }
            else { nv[s] = sw + E[t][s]; bp[s] = o }
        }
        val mx = nv.max()
        V = DoubleArray(S) { nv[it] - mx }
        bps += bp
        cur[t] = argmax(V)
    }
    if (live) return cur
    val path = IntArray(T)
    path[T - 1] = argmax(V)
    for (t in T - 1 downTo 1) path[t - 1] = bps[t - 1][path[t]]
    return path
}

package io.github.pini236.skiapp.dayrec.analysis

import io.github.pini236.skiapp.dayrec.DayLog
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.round

/** Steps 2 and 3 of the day analysis: the maximum speed from the raw Doppler readings, and the altitude. Ports of analyze.py. */

internal fun kmh(v: Double) = Math.rint(v * 3.6 * 100) / 100

/** Python's round(x, 1) for the metres that are reported: half to even on the scaled value. */
internal fun round1(v: Double) = Math.rint(v * 10) / 10

/** The raw Doppler readings that can be trusted: a small accuracy, a believable acceleration, and agreeing with the raw positions. */
internal fun validSpeeds(pts: List<DayLog.Point>, xy: List<DoubleArray>, P: Params): BooleanArray {
    val n = pts.size
    val W = P.i("max_speed", "pos_window_s"); val mdt = P.d("max_speed", "max_pair_dt_s")
    val ok = BooleanArray(n)
    for (i in 0 until n) {
        val s = pts[i].speed; val sa = pts[i].speedAcc
        if (s == null || sa == null || sa > P.d("max_speed", "speed_acc_max_mps")) continue
        var good = true
        for (j in intArrayOf(i - 1, i + 1)) { // acceleration against both neighbours
            if (j in 0 until n && pts[j].speed != null) {
                val dt = abs(pts[j].t - pts[i].t) / 1000.0
                if (dt <= mdt && abs(pts[j].speed!! - s) / dt > P.d("max_speed", "accel_max_mps2")) good = false
            }
        }
        if (!good) continue
        // the Doppler speed agrees with how far the raw positions moved, over i-W .. i+W
        val lo = i - W; val hi = i + W
        if (lo < 0 || hi >= n) continue
        var path = 0.0; var dur = 0.0; var sd = 0.0
        for (k in lo + 1..hi) {
            val dt = (pts[k].t - pts[k - 1].t) / 1000.0
            if (dt > mdt || pts[k].speed == null || pts[k - 1].speed == null) { good = false; break }
            path += hypot(xy[k][0] - xy[k - 1][0], xy[k][1] - xy[k - 1][1])
            dur += dt
            sd += (pts[k].speed!! + pts[k - 1].speed!!) / 2 * dt
        }
        if (!good) continue
        if (abs(path - sd) > max(P.d("max_speed", "pos_match_rel") * sd, P.d("max_speed", "pos_match_abs_mps") * dur)) continue
        ok[i] = true
    }
    return ok
}

/** The highest speed held over sustain_s readings in a row, all valid: (value m/s, point index) or null. */
internal fun maxSpeed(pts: List<DayLog.Point>, ok: BooleanArray, idx0: List<Int>, P: Params): Pair<Double, Int>? {
    val n = P.i("max_speed", "sustain_s"); val mdt = P.d("max_speed", "max_pair_dt_s")
    var best: Pair<Double, Int>? = null
    val idx = idx0.sorted()
    for (a in 0..idx.size - n) {
        val w = idx.subList(a, a + n)
        if ((0 until n - 1).any { w[it + 1] != w[it] + 1 }) continue
        if (!w.all { ok[it] }) continue
        if ((0 until n - 1).any { (pts[w[it + 1]].t - pts[w[it]].t) / 1000.0 > mdt }) continue
        val v = w.minOf { pts[it].speed!! }
        val at = w.minWithOrNull(compareBy<Int>({ pts[it].speed!! }, { it }))!!
        if (best == null || v > best.first) best = v to at
    }
    return best
}

internal fun baroAlt(p: Double) = 44330.77 * (1 - Math.pow(p / 1013.25, 0.190263))

/** Barometer if most readings have it (relative), else GPS altitude: (series, barometer?). */
internal fun altSource(pts: List<DayLog.Point>): Pair<DoubleArray, Boolean> {
    val nb = pts.count { it.pressure != null }
    val baro = nb * 2 >= pts.size && nb > 0
    val out = DoubleArray(pts.size)
    var last: Double? = null
    for ((i, q) in pts.withIndex()) {
        var v: Double? = if (baro) (q.pressure?.let { baroAlt(it) } ?: last) else (q.alt ?: last)
        if (v == null) v = 0.0
        out[i] = v; last = v
    }
    return out to baro
}

/** Mean of vals over the points with lo <= t <= hi (times in ms), around index i. */
internal fun windowMean(ts: LongArray, vals: DoubleArray, i: Int, lo: Double, hi: Double): Double {
    var s = 0.0; var n = 0
    var j = i
    while (j >= 0 && ts[j] >= lo) { if (ts[j] <= hi) { s += vals[j]; n++ }; j-- }
    j = i + 1
    while (j < ts.size && ts[j] <= hi) { if (ts[j] >= lo) { s += vals[j]; n++ }; j++ }
    return if (n > 0) s / n else vals[i]
}

internal fun smoothAlt(ts: LongArray, src: DoubleArray, P: Params, live: Boolean): DoubleArray {
    val w = P.d("alt", "ma_s") * 1000
    return DoubleArray(ts.size) { i -> val t = ts[i].toDouble(); if (live) windowMean(ts, src, i, t - w, t) else windowMean(ts, src, i, t - w / 2, t + w / 2) }
}

internal fun verticalSpeed(ts: LongArray, h: DoubleArray, P: Params, live: Boolean): DoubleArray {
    val w = P.d("alt", "vz_window_s") * 1000
    val n = ts.size
    val out = DoubleArray(n)
    var j = 0; var k = 0
    for (i in 0 until n) {
        val t = ts[i].toDouble()
        val lo = t - (if (live) w else w / 2)
        val hi = if (live) t else t + w / 2
        while (ts[j] < lo) j++
        if (k < i) k = i
        while (k + 1 < n && ts[k + 1] <= hi) k++
        val dt = (ts[k] - ts[j]) / 1000.0
        out[i] = if (dt > 0) (h[k] - h[j]) / dt else 0.0
    }
    return out
}

/** Vertical drop with a hysteresis threshold, over the given point indexes. */
internal fun drop(h: DoubleArray, idx: Iterable<Int>, P: Params): Double {
    val th = P.d("alt", "hyst_m")
    var tot = 0.0; var ref: Double? = null
    for (i in idx) {
        val v = h[i]
        if (ref == null || v > ref + th) ref = v // a climb of more than the threshold moves the reference up
        else if (ref - v >= th) { tot += ref - v; ref = v }
    }
    return tot
}

/**
 * [(from time, offset)] from station measurements [(time, station altitude - source altitude)]. A measurement is used if
 * it is within cal_jump_max_m of the last one used, or of the last one refused (two in a row that agree win, so one bad
 * first station does not lock the day). The offset is the median of the last cal_median_n used.
 */
internal fun offsets(meas: List<Pair<Long, Double>>, P: Params): List<Pair<Long, Double>> {
    val acc = ArrayList<Double>(); var lastRej: Double? = null; val offs = ArrayList<Pair<Long, Double>>()
    val jump = P.d("alt", "cal_jump_max_m"); val nMed = P.i("alt", "cal_median_n")
    for ((t, m) in meas) {
        if (acc.isEmpty() || abs(m - acc.last()) <= jump || (lastRej != null && abs(m - lastRej) <= jump)) {
            acc += m; lastRej = null
            val k = acc.takeLast(nMed).sorted()
            val h = k.size / 2
            offs += t to (if (k.size % 2 == 1) k[h] else (k[h - 1] + k[h]) / 2)
        } else lastRej = m
    }
    return offs
}

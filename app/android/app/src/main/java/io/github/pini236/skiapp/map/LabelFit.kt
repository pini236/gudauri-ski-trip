package io.github.pini236.skiapp.map

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Run names beside the line, curving with it (round 21, docs/PARITY.md 16): the site's `site/js/labelfit.js`, port for port.
 * Pure geometry. A window is `[s, t0, t1, cL, cR]` (tools/build-labels.py): the segment s, the part of it from t0 to t1
 * (thousandths of its length) and the clear room left and right of the line, in metres. [segs] are the run's segments as
 * x,y pairs in metres, by the data's own index (null for one that is not a line).
 */
object LabelFit {
    /** [pts] x,y pairs of the baseline the name is written along, [side] 'L' or 'R' of the line, [win] the window it used. */
    class Fit(val pts: FloatArray, val side: Char, val win: DoubleArray)

    private fun len(l: List<FloatArray>): Float { var d = 0f; for (i in 1 until l.size) d += hypot(l[i][0] - l[i - 1][0], l[i][1] - l[i - 1][1]); return d }

    private fun pairs(a: FloatArray) = List(a.size / 2) { floatArrayOf(a[it * 2], a[it * 2 + 1]) }

    /** The part of the polyline between distances [a] and [b]. */
    internal fun cut(line: List<FloatArray>, a: Float, b: Float): List<FloatArray> {
        val o = ArrayList<FloatArray>(); var acc = 0f
        for (i in 1 until line.size) {
            val p = line[i - 1]; val q = line[i]; val l = hypot(q[0] - p[0], q[1] - p[1])
            if (acc + l >= a && acc <= b && l > 0) {
                val u0 = max(0f, (a - acc) / l); val u1 = min(1f, (b - acc) / l)
                if (o.isEmpty()) o += floatArrayOf(p[0] + (q[0] - p[0]) * u0, p[1] + (q[1] - p[1]) * u0)
                o += floatArrayOf(p[0] + (q[0] - p[0]) * u1, p[1] + (q[1] - p[1]) * u1)
            }
            acc += l
        }
        return o
    }

    /** Every point [k] metres to the left of the direction of travel (screen, y down). */
    internal fun shift(l: List<FloatArray>, k: Float): List<FloatArray> = l.mapIndexed { i, p ->
        val a = l[max(i - 1, 0)]; val b = l[min(i + 1, l.size - 1)]
        var tx = b[0] - a[0]; var ty = b[1] - a[1]; val t = hypot(tx, ty).let { if (it == 0f) 1f else it }
        tx /= t; ty /= t
        floatArrayOf(p[0] + ty * k, p[1] - tx * k)
    }

    /**
     * The first window with room for a name [wM] metres long and [hM] high, [off] metres from the line, or null: then the name waits
     * for a closer zoom. Above the line (the left of travel when it runs east) is preferred.
     */
    fun fit(windows: List<DoubleArray>, segs: List<FloatArray?>, wM: Float, hM: Float, off: Float): Fit? {
        if (windows.isEmpty()) return null
        val needL = off + hM * .8f + hM * .2f; val needR = off + hM * 1.05f + hM * .2f // the far edge plus a margin stays clear of other lines
        for (w in windows) {
            val s = w[0].toInt(); val t0 = w[1]; val t1 = w[2]
            val seg = segs.getOrNull(s) ?: continue
            if (!(t0 < t1)) continue
            val line = pairs(seg); val tot = len(line)
            val a = (tot * t0 / 1000).toFloat(); val b = (tot * t1 / 1000).toFloat()
            if (b - a < wM * 1.1f) continue
            val mid = (a + b) / 2
            var sub = cut(line, mid - wM * .55f, mid + wM * .55f); if (sub.size < 2) continue
            var left = w[3]; var right = w[4]
            if (sub.first()[0] > sub.last()[0]) { sub = sub.reversed(); left = w[4]; right = w[3] } // read left to right
            val side = when { left >= needL -> 'L'; right >= needR -> 'R'; else -> continue }
            val pts = if (side == 'L') shift(sub, off) else shift(sub, -(off + hM * .8f))
            val out = FloatArray(pts.size * 2); pts.forEachIndexed { i, p -> out[i * 2] = p[0]; out[i * 2 + 1] = p[1] }
            return Fit(out, side, w)
        }
        return null
    }

    private val FAM = Regex("^(.*\\S)\\s+(\\d+)$")
    private fun fam(k: String): Pair<String, Int>? = FAM.find(k)?.let { it.groupValues[1] to it.groupValues[2].toInt() }

    /** One name for a stretch two runs share: "Tatra 1 · 2" when they are of one family, else the first run's own name (the site's sharedName). */
    fun sharedName(a: String, b: String): String {
        val x = fam(a); val y = fam(b)
        return if (x != null && y != null && x.first == y.first) "${x.first} ${min(x.second, y.second)} · ${max(x.second, y.second)}" else a
    }
}

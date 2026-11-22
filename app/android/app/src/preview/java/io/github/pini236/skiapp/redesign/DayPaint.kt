package io.github.pini236.skiapp.redesign

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Round 4 of the redesign (decision 84, board L06): the day painted on the mountain, as lines of light coloured by
 * speed. Pure, so a test can run it; built off the GL thread and never sent anywhere (the architect, 72cbe1d0: no
 * coordinate leaves the phone).
 *
 * The designer's rules (design/redesign/tools/day4.py): the speed is a 5 s mean of the Doppler speed and the position
 * a 7 s mean, both for drawing only; a gap of more than 10 s breaks the line; only the descents are drawn as light,
 * the lifts as a dotted straight line between where the ride began and ended. The light in five bands, cut at 20, 35,
 * 50 and 62 km/h, its own scale (away from the run colours and the slope colours).
 */
object DayPaint {
    /** One second of the day, in the map's metres (data/Geo.kt) and km/h (null: the phone could not say). */
    class Pt(val t: Long, val x: Float, val y: Float, val kmh: Double?)
    /** A part of the day by the analysis (the result's segments): its type and its times. */
    class Seg(val type: String, val t0: Long, val t1: Long)

    /** A stretch of a descent: x, y in metres and the band (0 to 4) of each point. */
    class Line(val xy: FloatArray, val band: IntArray)
    /** The day as it is drawn: the descents' lines and the lifts' chords (x0, y0, x1, y1). */
    class Day(val lines: List<Line>, val lifts: List<FloatArray>) {
        val points get() = lines.sumOf { it.band.size }
    }

    val BANDS = doubleArrayOf(20.0, 35.0, 50.0, 62.0)
    /** The light by day and at night (the designer's RAMP), slow to fast, as 0xRRGGBB. */
    val DAY = intArrayOf(0xCFC2F4, 0xA98BEE, 0x7C4FE0, 0xB02FC4, 0xE0246E)
    val NIGHT = intArrayOf(0x5B46A8, 0x8A6BE6, 0xB99CFF, 0xF3A6F0, 0xFFF2FA)
    /** The case under the light: white by day, the night's deep blue at night (board L06). */
    const val CASE_DAY = 0xFFFFFF
    const val CASE_NIGHT = 0x050A16
    /** The lifts' dotted line. */
    const val LIFT_DAY = 0x22303F
    const val LIFT_NIGHT = 0xC9D5E2

    const val GAP_MS = 10_000L
    /** Up to 20k vertices in the day's buffer (the architect): two per point. */
    const val MAX_POINTS = 10_000

    fun band(kmh: Double): Int = BANDS.count { kmh >= it }

    /** A centred mean of [w] values, the ends repeated (numpy's edge padding, as day4.py). */
    internal fun mean(v: DoubleArray, w: Int): DoubleArray {
        val h = w / 2; val n = v.size
        return DoubleArray(n) { i -> var s = 0.0; for (k in -h..h) s += v[(i + k).coerceIn(0, n - 1)]; s / w }
    }

    fun build(pts: List<Pt>, segs: List<Seg>, max: Int = MAX_POINTS): Day {
        if (pts.isEmpty()) return Day(emptyList(), emptyList())
        val kmh = mean(DoubleArray(pts.size) { pts[it].kmh ?: 0.0 }, 5)
        val xs = mean(DoubleArray(pts.size) { pts[it].x.toDouble() }, 7)
        val ys = mean(DoubleArray(pts.size) { pts[it].y.toDouble() }, 7)
        val t = LongArray(pts.size) { pts[it].t }
        fun from(t0: Long) = t.binarySearch(t0).let { if (it < 0) -it - 1 else it }
        fun until(t1: Long) = t.binarySearch(t1).let { if (it < 0) -it - 1 else it + 1 }
        // the stretches first, broken at the gaps, then thinned evenly if the day is longer than the buffer
        val runs = ArrayList<IntArray>()
        for (s in segs) if (s.type == "descent") {
            val a = from(s.t0); val b = until(s.t1).coerceAtMost(pts.size)
            var start = a
            for (i in a + 1..b) if (i == b || t[i] - t[i - 1] > GAP_MS) {
                if (i - start >= 2) runs += IntArray(i - start) { start + it }
                start = i
            }
        }
        val total = runs.sumOf { it.size }
        val step = if (total > max) ceil(total.toDouble() / max).toInt() else 1
        val lines = runs.map { r ->
            // every step-th point, and always the last, so a stretch keeps its end
            val keep = r.filterIndexed { i, _ -> i % step == 0 || i == r.size - 1 }
            Line(FloatArray(keep.size * 2) { k -> (if (k % 2 == 0) xs[keep[k / 2]] else ys[keep[k / 2]]).toFloat() }, IntArray(keep.size) { band(kmh[keep[it]]) })
        }.filter { it.band.size >= 2 }
        val lifts = segs.filter { it.type == "lift" }.mapNotNull { s ->
            val a = from(s.t0); val b = until(s.t1).coerceAtMost(pts.size) - 1
            if (b <= a) null else floatArrayOf(xs[a].toFloat(), ys[a].toFloat(), xs[b].toFloat(), ys[b].toFloat())
        }
        return Day(lines, lifts)
    }

    /** The box of the day's lines, in metres: x0, y0, x1, y1. */
    fun box(d: Day): FloatArray? {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (l in d.lines) for (i in 0 until l.xy.size / 2) { x0 = minOf(x0, l.xy[i * 2]); x1 = maxOf(x1, l.xy[i * 2]); y0 = minOf(y0, l.xy[i * 2 + 1]); y1 = maxOf(y1, l.xy[i * 2 + 1]) }
        return if (x0 > x1) null else floatArrayOf(x0, y0, x1, y1)
    }

    /** A lift's chord as a dotted line: short pieces along it (1 on, 4 off as the board's dash, in metres here). */
    fun dots(c: FloatArray, on: Float = 8f, period: Float = 40f): List<FloatArray> {
        val len = hypot(c[2] - c[0], c[3] - c[1]); if (len < 1f) return emptyList()
        val out = ArrayList<FloatArray>()
        var d = 0f
        while (d < len) {
            val e = minOf(d + on, len)
            val f0 = d / len; val f1 = e / len
            out += floatArrayOf(c[0] + (c[2] - c[0]) * f0, c[1] + (c[3] - c[1]) * f0, c[0] + (c[2] - c[0]) * f1, c[1] + (c[3] - c[1]) * f1)
            d += period
        }
        return out
    }
}

package io.github.pini236.skiapp.redesign

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * "ימי הגלישה שלי" and the records (P.24, handoff 2026-11-27-redesign-my-days, sections 3 and 4): the pure parts, so a
 * test pins them: the window framing of a day card, the speed strip, the trip's bars, the wedge of the steepest descent.
 */
internal object MyDaysRules {
    /** A window's picture scale and offset: [k], [ox], [oy]. */
    class Frame(val k: Float, val ox: Float, val oy: Float)

    /**
     * Frames a window [w] by [h] on the picture ([vw] by [vh] points) around [pts] (x, y pairs, NaN skipped): the points
     * with a 25% margin on each side, at least the scale that covers the window, and never past the picture's edge.
     * Without a point: the whole picture, centred, at the covering scale.
     */
    fun frame(pts: List<FloatArray>, w: Float, h: Float, vw: Float, vh: Float, margin: Float = .25f): Frame {
        val cover = max(w / vw, h / vh)
        var a0 = Float.MAX_VALUE; var a1 = -Float.MAX_VALUE; var b0 = Float.MAX_VALUE; var b1 = -Float.MAX_VALUE
        for (l in pts) for (i in 0 until l.size / 2) {
            val x = l[i * 2]; val y = l[i * 2 + 1]
            if (x.isNaN() || y.isNaN()) continue
            a0 = min(a0, x); a1 = max(a1, x); b0 = min(b0, y); b1 = max(b1, y)
        }
        if (a0 > a1) return Frame(cover, (w - vw * cover) / 2, (h - vh * cover) / 2)
        val ew = (a1 - a0).coerceAtLeast(1f) * (1 + 2 * margin); val eh = (b1 - b0).coerceAtLeast(1f) * (1 + 2 * margin)
        val k = max(cover, min(w / ew, h / eh))
        val ox = (w / 2f - (a0 + a1) / 2 * k).coerceIn(min(w - vw * k, 0f), 0f)
        val oy = (h / 2f - (b0 + b1) / 2 * k).coerceIn(min(h - vh * k, 0f), 0f)
        return Frame(k, ox, oy)
    }

    /** The speed strip of a day: the bands (0 to 4) of the descents in time order, nothing between them (no lifts). */
    fun strip(lines: List<DayPaint.Line>): IntArray = lines.sortedBy { it.t.firstOrNull() ?: 0L }.flatMap { it.band.toList() }.toIntArray()

    /** [bands] thinned to at most [n] cells (each the fastest band it covers), so a strip draws in a fixed number of cells. */
    fun cells(bands: IntArray, n: Int): IntArray {
        if (bands.isEmpty() || n <= 0) return IntArray(0)
        if (bands.size <= n) return bands
        return IntArray(n) { c ->
            val from = (c.toLong() * bands.size / n).toInt(); val to = max(from + 1, ((c + 1).toLong() * bands.size / n).toInt())
            var m = 0; for (i in from until min(to, bands.size)) m = max(m, bands[i]); m
        }
    }

    /** The bars' heights, 0..1, the biggest day full (a day with no drop has a stub of 0.06); empty without days. */
    fun bars(drops: List<Double>): List<Float> {
        val top = drops.maxOrNull() ?: return emptyList()
        return drops.map { if (top <= 0.0) .06f else (it / top).toFloat().coerceIn(.06f, 1f) }
    }

    /** The descent bars of the "most descents" tile: height of each descent's drop, 0..1. */
    fun descentBars(drops: List<Double>): List<Float> = bars(drops)

    /** The wedge of the steepest descent: the slope's angle in degrees to the end point of a unit-wide wedge (x, y; y up). */
    fun wedge(deg: Double, width: Float = 1f): FloatArray {
        val a = Math.toRadians(deg.coerceIn(0.0, 89.0))
        return floatArrayOf(width, (width * Math.tan(a)).toFloat())
    }

    /** What a day card says in its tag: a record on that day, or a cut day; the record wins when both. */
    enum class Tag { NONE, RECORD, CUT }
    fun tag(isRecordDay: Boolean, cut: Boolean): Tag = when { isRecordDay -> Tag.RECORD; cut -> Tag.CUT; else -> Tag.NONE }

    /** The trip's four numbers over the days (descents, drop m, ski km) for days with a summary. */
    class TripSum(val days: Int, val descents: Int, val dropM: Double, val km: Double)
    fun sum(days: List<Triple<Int, Double, Double>>): TripSum = TripSum(days.size, days.sumOf { it.first }, days.sumOf { it.second }, days.sumOf { it.third } / 1000.0)

    /** Equal to the tolerance used for records (the day that set it). */
    fun same(a: Double, b: Double) = abs(a - b) < 0.05 * max(1.0, abs(b) / 1000.0)
}

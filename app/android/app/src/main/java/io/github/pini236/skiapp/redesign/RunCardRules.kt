package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.RunPoint

/** The run card's profile (docs: design/handoff/2026-11-27-redesign-run-card): slope classes, stretches, the steepest pin, ticks. */
internal object RunCardRules {
    /** The approved slope thresholds: up to 15 degrees 0, to 25 1, to 30 2, above 3. */
    fun slopeClass(deg: Float): Int = when { deg <= 15f -> 0; deg <= 25f -> 1; deg <= 30f -> 2; else -> 3 }

    /** A stretch of the profile: points [from]..[to] (inclusive) of one slope class. */
    class Seg(val from: Int, val to: Int, val cls: Int)

    /** Consecutive points of one class joined into stretches; neighbours share their end point so the line has no gaps. */
    fun segments(points: List<RunPoint>): List<Seg> {
        if (points.size < 2) return emptyList()
        val out = ArrayList<Seg>()
        var start = 0
        var cls = slopeClass(points[1].a)
        for (i in 1 until points.size) {
            val c = slopeClass(points[i].a)
            if (c != cls) { out += Seg(start, i - 1, cls); start = i - 1; cls = c }
        }
        out += Seg(start, points.size - 1, cls)
        return out
    }

    /** The middle of the steepest stretch (by the 40 m slope of each point), or -1 for no points. */
    fun steepestAt(points: List<RunPoint>): Int {
        if (points.isEmpty()) return -1
        var best = 0
        for (i in points.indices) if (points[i].a > points[best].a) best = i
        return best
    }

    /** Distances (metres from the top) of a baseline tick every [every] metres, none at the two ends. */
    fun ticks(length: Float, every: Float = 500f): List<Float> {
        val out = ArrayList<Float>()
        var d = every
        while (d < length - 1f) { out += d; d += every }
        return out
    }

    /** At most [max] video frames in the foot row. */
    fun frames(videos: Int, max: Int = 3): Int = videos.coerceIn(0, max)

    /** The foot row shows when there is a video or a forecast. */
    fun footRow(videos: Int, hasForecast: Boolean): Boolean = videos > 0 || hasForecast
}

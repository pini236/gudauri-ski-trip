package io.github.pini236.skiapp.redesign

import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * "To go down in a film" (board K12, part 4's handoff, section 14): the run's longest line, top down, as the flight
 * down of today walks it (map/MapRenderer.kt FlyDown), with what the film's sheet and rail read at a distance along it.
 * [path] is x, height, y triples in metres (MapScene.topDown); no other data, no guess.
 */
internal class FilmLine(private val path: FloatArray) {
    private val n = path.size / 3
    private val cum = FloatArray(maxOf(n, 1)).also { c -> for (i in 1 until n) c[i] = c[i - 1] + hypot(path[i * 3] - path[i * 3 - 3], path[i * 3 + 2] - path[i * 3 - 1]) }

    /** The line's length (when a run is partial, this, not the run's length, is what the film shows). */
    val length: Float get() = if (n < 2) 0f else cum[n - 1]

    /** The point at [d] metres along the line: x, height, y. */
    fun at(d: Float): FloatArray {
        val out = FloatArray(3)
        if (n == 0) return out
        if (n == 1) { for (j in 0..2) out[j] = path[j]; return out }
        val dd = d.coerceIn(0f, length)
        var i = 0
        while (i < n - 2 && cum[i + 1] < dd) i++
        val seg = (cum[i + 1] - cum[i]).coerceAtLeast(0.01f)
        val f = ((dd - cum[i]) / seg).coerceIn(0f, 1f)
        for (j in 0..2) out[j] = path[i * 3 + j] + (path[(i + 1) * 3 + j] - path[i * 3 + j]) * f
        return out
    }

    /** The height here, as the flight's readout (the snow 4 m under the draped line), whole metres. */
    fun altAt(d: Float): Int = Math.round(at(d)[1] - 4f)

    /** The slope here in whole degrees, over 30 m of the line (as FlyDown's readout); never negative. */
    fun slopeAt(d: Float): Int {
        val a = at((d - 15).coerceAtLeast(0f)); val b = at((d + 15).coerceAtMost(length))
        val run = hypot(b[0] - a[0], b[2] - a[2]).coerceAtLeast(1f)
        return Math.round(Math.toDegrees(atan((a[1] - b[1]).coerceAtLeast(0f) / run).toDouble()).toFloat())
    }

    /** Where the camera looks at [d]: down the line (60 m behind to 120 m ahead), as FlyDown's yaw. */
    fun yawAt(d: Float): Float {
        val a = at((d - 60).coerceAtLeast(0f)); val b = at((d + 120).coerceAtMost(length))
        return atan2(-(b[0] - a[0]), -(b[2] - a[2]))
    }

    /** The descent in height from the top to [d], metres (only downhill counts). */
    fun dropAt(d: Float): Float = (at(0f)[1] - at(d)[1]).coerceAtLeast(0f)

    /** The point on the line nearest (x, y): its distance along the line, and how far it is from the line. */
    fun nearest(x: Float, y: Float): Pair<Float, Float> {
        if (n < 2) return 0f to Float.MAX_VALUE
        var best = Float.MAX_VALUE; var at = 0f
        for (i in 0 until n - 1) {
            val ax = path[i * 3]; val ay = path[i * 3 + 2]; val bx = path[(i + 1) * 3]; val by = path[(i + 1) * 3 + 2]
            val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            val t = if (l2 < 1e-6f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
            val dist = hypot(x - (ax + dx * t), y - (ay + dy * t))
            if (dist < best) { best = dist; at = cum[i] + (cum[i + 1] - cum[i]) * t }
        }
        return at to best
    }
}

/** A run that meets the film's line at [at] metres along it. */
internal data class FilmJoin(val at: Float, val run: String)

internal object FilmRules {
    /** The film's pace: a steady 10 m a second along the line, times the speed; a value to show, not a measurement. */
    const val BASE = 10f
    /** The speed button: ×8 first, then ×2, ×4, and round. */
    val SPEEDS = intArrayOf(8, 2, 4)
    fun nextSpeed(s: Int): Int = SPEEDS[(SPEEDS.indexOf(s).coerceAtLeast(0) + 1) % SPEEDS.size]

    /** A run's end within this of the line meets it (the rule of the connections in the data). */
    const val JOIN_M = 60f

    /**
     * Where other runs meet the line: for each end (top and bottom) of each other run, the nearest point of the line,
     * only when within [JOIN_M]; the run's name once, at its first meeting. [ends]: the run's name with its two ends.
     */
    fun joins(line: FilmLine, self: String, ends: List<Pair<String, List<FloatArray>>>): List<FilmJoin> {
        val out = HashMap<String, Float>()
        for ((name, pts) in ends) {
            if (name == self) continue
            for (p in pts) {
                val (at, off) = line.nearest(p[0], p[1])
                if (off <= JOIN_M) out[name] = minOf(out[name] ?: Float.MAX_VALUE, at)
            }
        }
        return out.map { FilmJoin(it.value, it.key) }.sortedBy { it.at }
    }

    /**
     * The next meeting ahead of [d] and how far, rounded to 10 m, or null after the last ("to the end, no crossings").
     * A meeting right here (under 5 m ahead) is behind you already.
     */
    fun next(joins: List<FilmJoin>, d: Float): Pair<Int, String>? =
        joins.firstOrNull { it.at - d >= 5f }?.let { Math.round((it.at - d) / 10f) * 10 to it.run }

    /** The rail's steps for the screen reader: 100 m along the line. */
    const val STEP_M = 100f

    /**
     * A tick for each 100 m of height lost; [strong] when the slope here is 25 degrees or more. [lastBand] is the 100 m
     * band of the last tick; the result is the new band (or the same), and whether to tick. At ×8 no more than one tick
     * in 150 ms ([sinceMs] since the last one).
     */
    fun tick(line: FilmLine, d: Float, lastBand: Int, sinceMs: Long, speed: Int): Pair<Int, Boolean> {
        val band = (line.dropAt(d) / 100f).toInt()
        if (band == lastBand) return band to false
        if (speed >= 8 && sinceMs < 150) return lastBand to false
        return band to true
    }

    fun strong(line: FilmLine, d: Float): Boolean = line.slopeAt(d) >= 25
}

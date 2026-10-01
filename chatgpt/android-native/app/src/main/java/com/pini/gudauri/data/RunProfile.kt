package com.pini.gudauri.data

import kotlin.math.*

/** These are slope colours, not official piste difficulty colours. Upper bounds are exclusive. */
enum class SlopeBand(val rgb: Int, val label: String) {
    GENTLE(0x3FA85F, "פחות מ־15°"), MODERATE(0xF2C13D, "15° עד פחות מ־25°"),
    STEEP(0xF08A3C, "25° עד פחות מ־30°"), VERY_STEEP(0xDC3B33, "30° ומעלה");

    companion object {
        fun at(degrees: Float): SlopeBand {
            require(degrees.isFinite() && degrees >= 0f) { "Invalid slope" }
            return when {
                degrees < 15f -> GENTLE
                degrees < 25f -> MODERATE
                degrees < 30f -> STEEP
                else -> VERY_STEEP
            }
        }
    }
}

data class ProfileSample(val point: Point, val distance: Float, val height: Float, val slopeDegrees: Float) {
    val band: SlopeBand get() = SlopeBand.at(slopeDegrees)
}
data class ProfileStretch(val startIndex: Int, val endIndex: Int, val grade: Float)

/** One real line only. No invented connectors between a piste's disconnected segments. */
class RunProfile private constructor(val samples: List<ProfileSample>, val steepest: ProfileStretch?) {
    val length: Float get() = samples.last().distance
    // Immutable samples: compute bounds once, not on every chart vertex/frame.
    val top: Float = samples.maxOf { it.height }
    val bottom: Float = samples.minOf { it.height }
    val first150: ProfileSample get() = samples.firstOrNull { it.distance >= 150f } ?: samples.last()
    val startingGrade: Float get() = (samples.first().height - first150.height) / first150.distance

    /** A single selection index can later drive the native chart and both map renderers. */
    fun sampleAtFraction(fraction: Float): ProfileSample = samples[indexAtFraction(fraction)]
    fun indexAtFraction(fraction: Float): Int {
        require(fraction.isFinite()) { "Invalid profile position" }
        val distance = fraction.coerceIn(0f, 1f) * length
        // Binary search by physical distance, not point count: source intervals are unequal.
        var lo = 0; var hi = samples.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (samples[mid].distance < distance) lo = mid + 1 else hi = mid
        }
        return if (lo > 0 && distance - samples[lo - 1].distance < samples[lo].distance - distance) lo - 1 else lo
    }

    companion object {
        fun create(terrain: Terrain, segments: List<Segment>, step: Float = 10f): RunProfile? {
            require(step.isFinite() && step >= 1f) { "Invalid profile sampling step" }
            val points = terrain.longestDescendingPoints(segments)
            if (points.size < 2) return null
            val positions = mutableListOf<Pair<Point, Float>>()
            positions.add(points.first() to 0f)
            var distance = 0f
            points.zipWithNext().forEach { (a, b) ->
                val length = hypot(b.x - a.x, b.z - a.z)
                if (length > 0f) {
                    val count = max(1, (length / step).roundToInt())
                    repeat(count) { k ->
                        val fraction = (k + 1f) / count
                        positions.add(Point(a.x + (b.x - a.x) * fraction, a.z + (b.z - a.z) * fraction) to distance + length * fraction)
                    }
                    distance += length
                }
            }
            if (distance == 0f) return null
            val heights = positions.map { (point, _) -> terrain.elevation(point.x, point.z) }
            var left = 0; var right = 0
            val samples = positions.mapIndexed { i, (point, d) ->
                while (left < i && d - positions[left].second > 20f && d - positions[left + 1].second >= 20f) left++
                // Same approximately 40 m window as the website: include the first sample
                // at least 20 m to either side, clamped at each end of the line.
                while (left > 0 && d - positions[left].second < 20f) left--
                while (right < positions.lastIndex && positions[right].second - d < 20f) right++
                val window = positions[right].second - positions[left].second
                val degrees = if (window > 0f) Math.toDegrees(atan(abs(heights[right] - heights[left]) / window).toDouble()).toFloat() else 0f
                ProfileSample(point, d, heights[i], degrees)
            }
            var best: ProfileStretch? = null
            var end = 0
            samples.forEachIndexed { start, sample ->
                while (end < samples.lastIndex && samples[end].distance - sample.distance < 100f) end++
                val window = samples[end].distance - sample.distance
                if (window >= 60f) {
                    val grade = (sample.height - samples[end].height) / window
                    if (grade > (best?.grade ?: 0f)) best = ProfileStretch(start, end, grade)
                }
            }
            return RunProfile(samples, best)
        }
    }
}

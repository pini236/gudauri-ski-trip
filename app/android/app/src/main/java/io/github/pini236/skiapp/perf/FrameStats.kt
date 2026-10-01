package io.github.pini236.skiapp.perf

/**
 * Rolling frame statistics. Feed it the timestamp (ns) of every frame actually drawn and, when known,
 * how long the frame took. A gap of more than 250 ms means the screen was idle, not a dropped frame.
 */
class FrameStats {
    private val stamps = LongArray(240)
    private val costs = LongArray(240)
    private var n = 0
    private var head = 0

    @Synchronized
    fun onFrame(stampNs: Long, costNs: Long = 0) {
        if (n > 0) {
            val prev = stamps[(head - 1 + stamps.size) % stamps.size]
            if (stampNs - prev > 250_000_000L) n = 0
        }
        stamps[head] = stampNs
        costs[head] = costNs
        head = (head + 1) % stamps.size
        if (n < stamps.size) n++
    }

    class Snapshot(val fps: Float, val jankPct: Float, val worstMs: Float, val active: Boolean)

    /** Over the last second of frames: average fps, share of frames that missed a vsync, worst frame. */
    @Synchronized
    fun snapshot(refreshHz: Float, nowNs: Long): Snapshot {
        if (n < 2) return Snapshot(0f, 0f, 0f, false)
        val last = stamps[(head - 1 + stamps.size) % stamps.size]
        if (nowNs - last > 400_000_000L) return Snapshot(0f, 0f, 0f, false)
        val budget = 1e9f / refreshHz
        var count = 0
        var janky = 0
        var worst = 0L
        var first = last
        var i = 1
        while (i < n) {
            val a = stamps[(head - 1 - i + 2 * stamps.size) % stamps.size]
            val b = stamps[(head - i + 2 * stamps.size) % stamps.size]
            if (last - a > 1_000_000_000L) break
            val gap = b - a
            if (gap > budget * 1.5f) janky++
            if (gap > worst) worst = gap
            count++
            first = a
            i++
        }
        if (count == 0) return Snapshot(0f, 0f, 0f, false)
        val fps = count * 1e9f / (last - first).coerceAtLeast(1)
        return Snapshot(fps, janky * 100f / count, worst / 1e6f, true)
    }
}

/** Cold start: from the moment the process started to the first frame on screen. */
object Startup {
    @Volatile var firstFrameMs: Long = -1
    @Volatile var dataMs: Long = -1
    @Volatile var meshMs: Long = -1
    @Volatile var shadowMs: Long = -1
}

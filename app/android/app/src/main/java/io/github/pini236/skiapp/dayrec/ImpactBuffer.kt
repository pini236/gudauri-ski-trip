package io.github.pini236.skiapp.dayrec

import kotlin.math.max
import kotlin.math.sqrt

/**
 * "Here you fell" (docs/ARCHITECTURE.md m-8, condition 10): the motion sensors kept in memory for about ten seconds, and a
 * window of them written to the day's log only around a blow. Nothing else of the sensors ever reaches the disk.
 *
 * The accelerometer is the clock: every reading is stored with the gyroscope's last value. When the size of the
 * acceleration reaches [triggerG] (and not more than once in [debounceS]) the highest reading of the next
 * [PEAK_SEARCH_MS] is the blow; [postS] seconds after it the window is cut: [preS] seconds before the peak and [postS]
 * after it on a grid of [hz] a second with exactly one sample at the peak's time (tools/dayrec/README.md, "The raw log").
 * Pure: the caller feeds the readings and receives the finished windows.
 */
class ImpactBuffer(
    private val triggerG: Double, private val preS: Double, private val postS: Double,
    private val debounceS: Double, private val hz: Int,
    private val onImpact: (t: Long, t0: Long, hz: Int, acc: DoubleArray, gyr: DoubleArray) -> Unit,
) {
    private class S(val t: Long, val a: DoubleArray, val g: DoubleArray)

    private val ring = ArrayDeque<S>()
    private val gyro = DoubleArray(3)
    private var lastTrigger = Long.MIN_VALUE
    private var peakT = -1L; private var peakMag = 0.0; private var armedAt = -1L

    /** The gyroscope's reading, in rad/s; it is paired with the next accelerometer reading. */
    @Synchronized fun feedGyro(x: Float, y: Float, z: Float) { gyro[0] = x.toDouble(); gyro[1] = y.toDouble(); gyro[2] = z.toDouble() }

    /** One accelerometer reading at [t] (epoch ms), in m/s^2 with gravity. */
    @Synchronized
    fun feedAcc(t: Long, x: Float, y: Float, z: Float) {
        val s = S(t, doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble()), gyro.copyOf())
        ring.addLast(s)
        val keep = ((preS + postS + 2) * 1000).toLong()
        while (ring.isNotEmpty() && t - ring.first().t > keep) ring.removeFirst()

        val mag = sqrt(s.a[0] * s.a[0] + s.a[1] * s.a[1] + s.a[2] * s.a[2])
        if (armedAt < 0) {
            if (mag / G >= triggerG && (lastTrigger == Long.MIN_VALUE || t - lastTrigger >= debounceS * 1000)) {
                armedAt = t; lastTrigger = t; peakT = t; peakMag = mag
            }
        } else {
            if (t - armedAt <= PEAK_SEARCH_MS && mag > peakMag) { peakMag = mag; peakT = t }
            if (t - peakT >= postS * 1000) { emit(peakT); armedAt = -1L; peakT = -1L }
        }
    }

    /** A blow that did not get its [postS] seconds before the day ended is dropped: a window is never cut short. */
    @Synchronized fun clear() { ring.clear(); armedAt = -1L; peakT = -1L; lastTrigger = Long.MIN_VALUE }

    private fun emit(t: Long) {
        val n = ((preS + postS) * hz).toInt() + 1
        val step = 1000.0 / hz
        val t0 = t - (preS * 1000).toLong()
        val acc = DoubleArray(n * 3); val gyr = DoubleArray(n * 3)
        var j = 0
        val list = ring.toList()
        for (k in 0 until n) {
            val g = t0 + Math.round(k * step)
            while (j + 1 < list.size && list[j + 1].t <= g) j++
            val s = list[max(0, j)] // the latest reading at or before the grid time (the first one before any)
            for (c in 0..2) { acc[k * 3 + c] = s.a[c]; gyr[k * 3 + c] = s.g[c] }
        }
        onImpact(t, t0, hz, acc, gyr)
    }

    companion object {
        const val G = 9.80665
        /** A blow lasts well under this: the highest reading in it is the moment the blow is said to have happened. */
        const val PEAK_SEARCH_MS = 500L
    }
}

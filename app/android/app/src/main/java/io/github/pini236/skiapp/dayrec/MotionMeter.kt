package io.github.pini236.skiapp.dayrec

/**
 * The phone's movement for one second, as one number: the variance of the accelerometer's size over the readings
 * since the last [take] (condition 1: the raw accelerometer is enough to tell standing from walking, so the system's
 * activity recognition, and its permission, are not used). Pure, fed by the sensor's listener.
 */
class MotionMeter {
    private var n = 0; private var mean = 0.0; private var m2 = 0.0

    /** One accelerometer reading, in m/s^2. */
    @Synchronized
    fun feed(x: Float, y: Float, z: Float) {
        val v = Math.sqrt((x * x + y * y + z * z).toDouble())
        n++
        val d = v - mean; mean += d / n; m2 += d * (v - mean) // Welford: stable for many readings
    }

    /** The variance since the last call, or null when the phone gave no reading (no accelerometer, or asleep). */
    @Synchronized
    fun take(): Float? {
        val out = if (n >= 2) (m2 / n).toFloat() else null
        n = 0; mean = 0.0; m2 = 0.0
        return out
    }
}

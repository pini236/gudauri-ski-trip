package io.github.pini236.skiapp.dayrec.ui

/**
 * The speed scale of the day's map (round 29): its own, so it is not mixed up with the run colours or the slope colours on the
 * same map. 0 light yellow, 20 orange, 40 red, 60 and up purple, blended between.
 */
object SpeedScale {
    private val stops = listOf(0.0 to 0xFFF5D76E.toInt(), 20.0 to 0xFFF08A24.toInt(), 40.0 to 0xFFD62B2B.toInt(), 60.0 to 0xFF7B2D9B.toInt())

    /** The colour (ARGB) for [kmh]; null or negative is the first. */
    fun argb(kmh: Double?): Int {
        val v = (kmh ?: 0.0).coerceIn(0.0, stops.last().first)
        for (i in 0 until stops.size - 1) {
            val (a, ca) = stops[i]; val (b, cb) = stops[i + 1]
            if (v <= b) return mix(ca, cb, ((v - a) / (b - a)).toFloat())
        }
        return stops.last().second
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(shift: Int) = ((((a shr shift) and 0xFF) * (1 - f) + ((b shr shift) and 0xFF) * f).toInt().coerceIn(0, 255)) shl shift
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
}

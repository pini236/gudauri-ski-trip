package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.Sky
import kotlin.math.floor

/**
 * Which pictures of the mountain to show now (K01, K02): the seven pictures are drawn for the sun of 12.1.2027, so
 * the hour in Gudauri today is first carried onto that day by its sunrise, noon and sunset (an hour after today's
 * sunrise is an hour after January's), then the two pictures around it are cross-faded. The day and night switch
 * keeps its meaning: day is the morning picture (as on the canvas, 10:35), night is 22:00.
 */
object PeakLight {
    class Blend(val a: PeakData.Hour, val b: PeakData.Hour, val f: Float) {
        val night get() = if (f < .5f) a.night else b.night
        /** the sun's offset of a lift's shadow, per metre of height */
        val dx get() = a.dx + (b.dx - a.dx) * f
        val dy get() = a.dy + (b.dy - a.dy) * f
    }

    private val JAN = java.time.LocalDate.of(2027, 1, 12).atTime(12, 0).toEpochSecond(java.time.ZoneOffset.ofHours(4)) * 1000
    private val janSun by lazy { Sky.sunTimes(JAN) }

    /** Gudauri's hour now (UTC+4) */
    fun localHour(epochMs: Long): Double { val l = epochMs / 3_600_000.0 + 4; return l - floor(l / 24) * 24 }

    /** today's hour carried onto 12 January by the sun */
    fun janHour(epochMs: Long): Float {
        val t = Sky.sunTimes(epochMs); val h = localHour(epochMs)
        val from = doubleArrayOf(0.0, t[0], t[2], t[1], 24.0); val to = doubleArrayOf(0.0, janSun[0], janSun[2], janSun[1], 24.0)
        var i = 0; while (i < 3 && h > from[i + 1]) i++
        val f = ((h - from[i]) / (from[i + 1] - from[i]).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
        return (to[i] + (to[i + 1] - to[i]) * f).toFloat()
    }

    fun hourFor(epochMs: Long, mode: DayNight.Mode): Float = when (mode) {
        DayNight.Mode.DAY -> 10.6f; DayNight.Mode.NIGHT -> 22f; DayNight.Mode.AUTO -> janHour(epochMs)
    }

    /** the two pictures around a January hour, and how far between them */
    fun blend(hours: List<PeakData.Hour>, h: Float): Blend {
        val night = hours.first { it.key == "night" }
        val dawn = hours.first()
        // the keys: night until an hour before the dawn picture, the day's pictures, night an hour after the blue hour
        val keys = buildList {
            add(night to 0f); add(night to dawn.h - 1f)
            hours.filter { it.key != "night" }.forEach { add(it to it.h) }
            add(night to hours.first { it.key == "blue" }.h + 1.1f); add(night to 24.01f)
        }
        var i = 0; while (i < keys.size - 2 && keys[i + 1].second < h) i++
        val (a, ha) = keys[i]; val (b, hb) = keys[i + 1]
        val f = if (a === b) 0f else ((h - ha) / (hb - ha)).coerceIn(0f, 1f)
        return Blend(a, b, f)
    }

    fun clock(h: Float): String { val m = ((h * 60).toInt() + 1440) % 1440; return "%02d:%02d".format(m / 60, m % 60) }
}

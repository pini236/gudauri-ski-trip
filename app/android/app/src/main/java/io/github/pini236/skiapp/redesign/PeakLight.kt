package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.Sun
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Which picture of the mountain to show (the handoff's rule, the architect's condition: by the sun, not the clock).
 * The sun over Gudauri now: from 1° up, the nearest of the five day pictures by height and direction together;
 * between 1° and 9° under the horizon, the blue hour; lower, night. The day and night switch: day is the morning
 * picture, night is night. The pictures change with a soft 600 ms cross-fade (the screen's).
 */
object PeakLight {
    /** the January day the pictures were drawn for, at noon in Gudauri: the hour slider moves along it */
    private val JAN = java.time.LocalDate.of(2027, 1, 12).atStartOfDay().toEpochSecond(java.time.ZoneOffset.ofHours(4)) * 1000

    fun pick(lights: List<PeakData.Light>, epochMs: Long, mode: DayNight.Mode): PeakData.Light {
        val by = { n: String -> lights.first { it.name == n } }
        if (mode == DayNight.Mode.DAY) return by("morning")
        if (mode == DayNight.Mode.NIGHT) return by("night")
        val (azR, altR) = Sun.position(epochMs)
        val alt = Math.toDegrees(altR); val az = Math.toDegrees(azR)
        return when {
            alt >= 1 -> lights.filter { !it.night }.minBy { l -> val da = abs(az - l.az).let { if (it > 180) 360 - it else it }; hypot(alt - l.alt, da) }
            alt >= -9 -> by("blue")
            else -> by("night")
        }
    }

    /** the hour slider: an hour of 12 January in Gudauri, as a moment */
    fun janAt(hour: Float): Long = JAN + (hour * 3_600_000).toLong()

    /** Gudauri's clock now (UTC+4) */
    fun localHour(epochMs: Long): Float { val l = epochMs / 3_600_000.0 + 4; return (l - floor(l / 24) * 24).toFloat() }

    fun clock(h: Float): String { val m = ((h * 60).toInt() + 1440) % 1440; return "%02d:%02d".format(m / 60, m % 60) }
}

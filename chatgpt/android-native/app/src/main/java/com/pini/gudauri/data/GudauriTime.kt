package com.pini.gudauri.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.*

enum class ThemeMode(val savedValue: Int, val label: String, val icon: String) {
    AUTO(0, "אוטומטי", "◐"), DAY(1, "יום", "☀"), NIGHT(2, "לילה", "☾");
    fun next() = entries[(ordinal + 1) % entries.size]
    companion object {
        // Preserve explicit day/night preferences from the original app.
        fun fromSaved(value: Int) = entries.firstOrNull { it.savedValue == value } ?: AUTO
    }
}

data class SunTimes(val rise: Double, val set: Double, val noon: Double)
data class SkyFrame(val hour: Double, val image: String, val top: Int, val bottom: Int,
    val stars: Float = 0f, val moon: Float = 0f, val windows: Float = 0f, val snow: Float = 0f)
data class Daylight(val actualTime: ZonedDateTime, val mode: ThemeMode, val dark: Boolean,
    val phase: String, val first: SkyFrame, val second: SkyFrame, val blend: Float) {
    fun mix(a: Float, b: Float) = a + (b - a) * blend
    fun mixColor(a: Int, b: Int): Int {
        var color = 0xFF000000.toInt()
        for (shift in listOf(16, 8, 0)) {
            val channel = (((a ushr shift) and 255) + (((b ushr shift) and 255) - ((a ushr shift) and 255)) * blend).roundToInt()
            color = color or (channel.coerceIn(0, 255) shl shift)
        }
        return color
    }
}

/** Same geometric sunrise and panorama anchors as the read-only website. Not a weather forecast. */
object GudauriTime {
    val zone: ZoneId = ZoneId.of("Asia/Tbilisi")
    fun sunTimes(date: LocalDate): SunTimes {
        val rad = PI / 180
        val n = date.dayOfYear
        val declination = -23.44 * cos(2 * PI / 365 * (n + 10)) * rad
        val b = 2 * PI / 364 * (n - 81)
        val equation = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val half = acos((-tan(42.51 * rad) * tan(declination)).coerceIn(-1.0, 1.0)) / rad / 15
        val noon = 12 - (44.495 - 4 * 15) / 15 - equation / 60
        return SunTimes(noon - half, noon + half, noon)
    }
    private fun color(hex: Long) = (0xFF000000L or hex).toInt()
    private fun frames(t: SunTimes): List<SkyFrame> {
        fun night(hour: Double) = SkyFrame(hour, "night", color(0x050A15), color(0x1A2645), 1f, 1f, 1f, 1f)
        return listOf(night(0.0), night(t.rise - 1.2),
            SkyFrame(t.rise - .3, "dawn", color(0x2C3B66), color(0xE8A987), .2f, .3f, .8f),
            SkyFrame(t.rise + 1.2, "morning", color(0x6FA6DC), color(0xDCEAF4)),
            SkyFrame(t.noon, "noon", color(0x4F90D2), color(0xD2E4F3)),
            SkyFrame(t.set - 1.8, "gold", color(0x6F9CCB), color(0xF1DDC2)),
            SkyFrame(t.set - .35, "sunset", color(0x3A4677), color(0xF09A6A), .1f, .2f, .6f),
            SkyFrame(t.set + .45, "dusk", color(0x1B2448), color(0x6E5D86), .6f, .8f, 1f, .3f),
            night(t.set + 1.3), night(24.0))
    }
    fun at(now: Instant, mode: ThemeMode): Daylight {
        val local = now.atZone(zone); val t = sunTimes(local.toLocalDate())
        val actualHour = local.hour + local.minute / 60.0
        val hour = when (mode) { ThemeMode.DAY -> t.noon; ThemeMode.NIGHT -> 22.0; else -> actualHour }
        val dark = mode == ThemeMode.NIGHT || (mode == ThemeMode.AUTO && (hour < t.rise - .3 || hour > t.set + .3))
        val phase = if (dark) { if (hour > t.set && hour < t.set + 1.3) "דמדומים" else "לילה" }
            else if (hour < t.rise + .8) "זריחה" else if (hour < t.set - 2) "יום" else if (hour < t.set - .6) "שעת זהב" else "שקיעה"
        val frames = frames(t); var i = 0
        while (i < frames.size - 2 && frames[i + 1].hour < hour) i++
        val a = frames[i]; val b = frames[i + 1]
        val blend = ((hour - a.hour) / (b.hour - a.hour)).coerceIn(0.0, 1.0).toFloat()
        return Daylight(local, mode, dark, phase, a, b, blend)
    }
    fun daysToFlight(flight: Flight, now: Instant): Int = ChronoUnit.DAYS.between(
        now.atZone(ZoneId.of("Asia/Jerusalem")).toLocalDate(), LocalDate.parse(flight.date)
    ).coerceAtLeast(0).toInt()
    fun countdownLabel(flight: Flight, now: Instant, dark: Boolean): String {
        if (daysToFlight(flight, now) > 0) return if (dark) "לילות לטיסה" else "ימים לטיסה"
        val today = now.atZone(ZoneId.of("Asia/Jerusalem")).toLocalDate()
        return if (LocalDate.parse(flight.date).isBefore(today)) "הטיסה כבר יצאה" else "יוצאים לדרך"
    }
}

package io.github.pini236.skiapp.map

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * The sky and the light over the mountain at a moment in Gudauri, as on the site (day and night, N1 and N2):
 * the same sky colours through the day, warm light when the sun is low, moonlight at night. The app also
 * draws the sun and the moon where they really are (the site does not). Plain math, unit tested.
 */
object Sky {
    private const val RAD = Math.PI / 180
    private const val LAT = 42.51; private const val LON = 44.495; private const val TZ = 4 // UTC+4, no daylight saving

    class Light(
        /** Towards the light that casts the shadows: the sun by day, the moon (or a dim sky light) at night. */
        val dir: FloatArray,
        val color: FloatArray,
        val ambTop: FloatArray, val ambGround: FloatArray,
        val skyTop: FloatArray, val skyBottom: FloatArray,
        /** Towards the sun and the moon, for drawing them; the sun's also lights the moon's phase. */
        val sunDir: FloatArray, val moonDir: FloatArray,
        val sunUp: Boolean, val moonUp: Boolean,
        val glow: Float, val glowColor: FloatArray,
        val dark: Boolean,
        val hour: Float,
    ) {
        val note get() = when {
            sunUp && !dark -> "שמש אמיתית עכשיו"
            moonUp -> "לילה בגודאורי: אור הירח"
            else -> "לילה בגודאורי"
        }
    }

    private class Key(val h: Double, val top: Int, val bottom: Int, val glow: Float, val glowC: Int)

    /** The site's keyframes (site/js/app.js, keys()), by local hour. */
    private fun keys(rise: Double, set: Double, noon: Double): List<Key> {
        val night = { h: Double -> Key(h, 0x050A15, 0x1A2645, 0f, 0xFFFFFF) }
        return listOf(
            night(0.0), night(rise - 1.2),
            Key(rise - .3, 0x2C3B66, 0xE8A987, .7f, 0xFFB38A),
            Key(rise + 1.2, 0x6FA6DC, 0xDCEAF4, .35f, 0xFFF2D6),
            Key(noon, 0x4F90D2, 0xD2E4F3, .2f, 0xFFFFFF),
            Key(set - 1.8, 0x6F9CCB, 0xF1DDC2, .6f, 0xFFD29A),
            Key(set - .35, 0x3A4677, 0xF09A6A, 1f, 0xFF9A6A),
            Key(set + .45, 0x1B2448, 0x6E5D86, .35f, 0xC98AA0),
            night(set + 1.3), night(24.0),
        )
    }

    /** Sunrise, sunset and noon in Gudauri's local hours for the local date of [epochMs] (the site's formula). */
    fun sunTimes(epochMs: Long): DoubleArray {
        val localDays = floor((epochMs / 3_600_000.0 + TZ) / 24.0)
        val y = java.time.LocalDate.ofEpochDay(localDays.toLong())
        val n = y.dayOfYear
        val dec = -23.44 * cos(2 * Math.PI / 365 * (n + 10)) * RAD
        val b = 2 * Math.PI / 364 * (n - 81)
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val half = Math.acos(-tan(LAT * RAD) * tan(dec)) / RAD / 15
        val noon = 12 - (LON - TZ * 15) / 15 - eot / 60
        return doubleArrayOf(noon - half, noon + half, noon)
    }

    fun at(epochMs: Long): Light {
        val (rise, set, noon) = sunTimes(epochMs).let { Triple(it[0], it[1], it[2]) }
        val h = ((epochMs / 3_600_000.0 + TZ) % 24 + 24) % 24
        val k = keys(rise, set, noon)
        var i = 0
        while (i < k.size - 2 && k[i + 1].h < h) i++
        val a = k[i]; val b = k[i + 1]
        val f = ((h - a.h) / (b.h - a.h).let { if (it == 0.0) 1.0 else it }).coerceIn(0.0, 1.0).toFloat()
        val dark = h < rise - .3 || h > set + .3

        val (saz, salt) = Sun.position(epochMs)
        val sunDir = Sun.direction(saz, salt)
        val (maz, malt) = moon(epochMs)
        val moonDir = Sun.direction(maz, malt)
        val sunUp = salt > 0
        val moonUp = malt > Math.toRadians(4.0)

        val altDeg = Math.toDegrees(salt).toFloat()
        val dir: FloatArray; val color: FloatArray; val top: FloatArray; val ground: FloatArray
        if (sunUp) {
            val low = (1 - altDeg / 12f).coerceIn(0f, 1f) // 1 at the horizon, 0 above 12°
            val warm = mix(rgb(0xFFF6EA), rgb(0xFF9F7A), low)
            dir = sunDir
            color = scale(warm, 0.5f + 0.3f * (altDeg / 20f).coerceAtMost(1f))
            val hemi = 0.55f - 0.15f * low
            top = scale(rgb(if (low > .5f) 0xB9B0D0 else 0xDDE8F5), hemi)
            ground = scale(rgb(0x7D879A), hemi)
        } else {
            // moonlight, from the real moon when it is up; otherwise a faint light from the south-east, as on the site
            dir = if (moonUp) moonDir else Sun.direction(150 * RAD, 38 * RAD)
            color = scale(rgb(0x9FB4E0), if (moonUp) 0.3f else 0.18f)
            val hemi = if (dark) 0.42f else 0.5f
            top = scale(rgb(0x4A5A86), hemi)
            ground = scale(rgb(0x1C2438), hemi)
        }
        return Light(
            dir, color, top, ground,
            mix(rgb(a.top), rgb(b.top), f), mix(rgb(a.bottom), rgb(b.bottom), f),
            sunDir, moonDir, sunUp, moonUp,
            a.glow + (b.glow - a.glow) * f, rgb(if (f < .5f) a.glowC else b.glowC),
            dark, h.toFloat(),
        )
    }

    /** The moon's azimuth (from north, clockwise) and altitude in radians: a low-precision formula, to a degree or so. */
    fun moon(epochMs: Long): Pair<Double, Double> {
        val d = epochMs / 86_400_000.0 + 2440587.5 - 2451545.0
        val e = 23.4397 * RAD
        val l0 = (218.316 + 13.176396 * d) * RAD
        val m = (134.963 + 13.064993 * d) * RAD
        val f = (93.272 + 13.229350 * d) * RAD
        val l = l0 + 6.289 * RAD * sin(m)
        val b = 5.128 * RAD * sin(f)
        val dec = asin(sin(b) * cos(e) + cos(b) * sin(e) * sin(l))
        val ra = atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))
        val st = (280.16 + 360.9856235 * d) * RAD + LON * RAD
        val hh = st - ra
        val phi = LAT * RAD
        val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(hh))
        val az = atan2(-sin(hh), tan(dec) * cos(phi) - sin(phi) * cos(hh))
        return Pair((az + 2 * Math.PI) % (2 * Math.PI), alt)
    }

    fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)
    private fun mix(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { a[it] + (b[it] - a[it]) * t }
    private fun scale(a: FloatArray, k: Float) = FloatArray(3) { a[it] * k }
}

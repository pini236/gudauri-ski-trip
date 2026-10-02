package io.github.pini236.skiapp.home

import io.github.pini236.skiapp.map.Sky
import kotlin.math.floor

/**
 * Day and night on the home page, as on the site (6.3, N1, site/js/app.js DN): three modes, auto follows the clock
 * in Gudauri (UTC+4) and that day's sunrise and sunset there. The real view from the village in seven moments of the
 * day (site/img/pano, design/round3/panorama.py), cross-faded, and the sky colours, stars and village lights in
 * between. Plain math, unit tested; the screen only draws what [at] returns.
 */
object DayNight {
    enum class Mode { AUTO, DAY, NIGHT;
        fun next() = entries[(ordinal + 1) % entries.size]
    }

    private class Key(val h: Double, val img: String, val top: Int, val bottom: Int, val glow: Float, val glowC: Int,
                      val star: Float, val moon: Float, val win: Float)

    private fun night(h: Double) = Key(h, "night", 0x050A15, 0x1A2645, 0f, 0xFFFFFF, 1f, 1f, 1f)

    /** The site's keyframes (keys() in site/js/app.js), by Gudauri hour. */
    private fun keys(rise: Double, set: Double, noon: Double) = listOf(
        night(0.0), night(rise - 1.2),
        Key(rise - .3, "dawn", 0x2C3B66, 0xE8A987, .7f, 0xFFB38A, .2f, .3f, .8f),
        Key(rise + 1.2, "morning", 0x6FA6DC, 0xDCEAF4, .35f, 0xFFF2D6, 0f, 0f, 0f),
        Key(noon, "noon", 0x4F90D2, 0xD2E4F3, .2f, 0xFFFFFF, 0f, 0f, 0f),
        Key(set - 1.8, "gold", 0x6F9CCB, 0xF1DDC2, .6f, 0xFFD29A, 0f, 0f, 0f),
        Key(set - .35, "sunset", 0x3A4677, 0xF09A6A, 1f, 0xFF9A6A, .1f, .2f, .6f),
        Key(set + .45, "dusk", 0x1B2448, 0x6E5D86, .35f, 0xC98AA0, .6f, .8f, 1f),
        night(set + 1.3), night(24.0),
    )

    class Frame(
        /** The two images to show: [imgA] under [imgB], which is drawn at opacity [mix]. */
        val imgA: String, val imgB: String, val mix: Float,
        val skyTop: Int, val skyBottom: Int,
        val glow: Float, val glowColor: Int, val glowMorning: Boolean,
        val stars: Float, val moon: Float, val lights: Float,
        /** The whole app goes dark (the night palette), as the site's data-theme. */
        val dark: Boolean,
        /** The clock in Gudauri, "13:35". */
        val clock: String,
    )

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun mixRgb(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (lerp(((a shr s) and 255).toFloat(), ((b shr s) and 255).toFloat(), t) + .5f).toInt() shl s
        return ch(16) or ch(8) or ch(0)
    }

    fun at(epochMs: Long, mode: Mode): Frame {
        val local = epochMs / 3_600_000.0 + 4
        val now = local - floor(local / 24) * 24
        val (rise, set, noon) = Sky.sunTimes(epochMs).let { Triple(it[0], it[1], it[2]) }
        val h = when (mode) { Mode.DAY -> noon; Mode.NIGHT -> 22.0; Mode.AUTO -> now }
        val k = keys(rise, set, noon)
        var i = 0
        while (i < k.size - 2 && k[i + 1].h < h) i++
        val a = k[i]; val b = k[i + 1]
        val span = (b.h - a.h).takeIf { it != 0.0 } ?: 1.0
        val f = ((h - a.h) / span).toFloat().coerceIn(0f, 1f)
        val dark = mode == Mode.NIGHT || (mode == Mode.AUTO && (h < rise - .3 || h > set + .3))
        val hh = floor(now).toInt(); val mm = floor((now - hh) * 60).toInt()
        return Frame(
            a.img, b.img, if (a.img == b.img) 0f else f,
            mixRgb(a.top, b.top, f), mixRgb(a.bottom, b.bottom, f),
            lerp(a.glow, b.glow, f), if (f < .5f) a.glowC else b.glowC, h < noon,
            lerp(a.star, b.star, f), lerp(a.moon, b.moon, f), lerp(a.win, b.win, f),
            dark, "%02d:%02d".format(hh, mm),
        )
    }
}

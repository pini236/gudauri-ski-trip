package io.github.pini236.skiapp.redesign

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The designer's handoff for the live model (design/handoff/2026-11-20-redesign-live, copied into the test build's
 * assets): the world box of the textures, the opening camera, the constants, the seven keys of light and the village's
 * lights, and the texel checks that catch a texture loaded upside down or with its channels swapped.
 */
class LiveData(
    val x0: Float, val x1: Float, val z0: Float, val z1: Float,
    val camera: Cam,
    val ripple: Float, val haze: FloatArray, val valley: Float, val air: FloatArray, val skyTop: Float,
    val strip: FloatArray,
    val keys: List<Key>,
    val village: FloatArray,
    val checks: List<Check>,
) {
    class Cam(val tx: Float, val ty: Float, val tz: Float, val dist: Float, val yawDeg: Float, val pitchDeg: Float, val fovDeg: Float)
    class Key(
        val name: String, val alt: Float, val side: String,
        val lit: FloatArray, val shade: FloatArray, val rock: FloatArray, val rockS: FloatArray,
        val zen: FloatArray, val hor: FloatArray, val band: FloatArray?, val direct: Float, val stars: Float,
    )
    class Check(val tex: String, val u: Float, val v: Float, val rgb: IntArray)

    companion object {
        const val DIR = "redesign/live/"
        val TEXTURES = listOf("relief", "cover", "tile", "horizon", "cirrus")

        fun load(context: Context): LiveData = parse(context.assets.open(DIR + "peak-live.json").bufferedReader().use { it.readText() })

        private fun v3(a: JSONArray?) = a?.let { FloatArray(3) { i -> it.getDouble(i).toFloat() } }

        fun parse(json: String): LiveData {
            val o = JSONObject(json)
            val w = o.getJSONObject("world")
            val c = o.getJSONObject("camera")
            val k = o.getJSONObject("k")
            val hz = k.getJSONObject("haze"); val air = k.getJSONObject("far_air")
            val h = o.getJSONObject("tex").getJSONObject("horizon")
            val lights = o.getJSONArray("lights")
            val keys = List(lights.length()) { i ->
                val l = lights.getJSONObject(i)
                Key(l.getString("name"), l.getDouble("alt").toFloat(), l.getString("side"),
                    v3(l.getJSONArray("lit"))!!, v3(l.getJSONArray("shade"))!!, v3(l.getJSONArray("rock"))!!, v3(l.getJSONArray("rockS"))!!,
                    v3(l.getJSONArray("zen"))!!, v3(l.getJSONArray("hor"))!!, if (l.isNull("band")) null else v3(l.getJSONArray("band")),
                    l.getDouble("direct").toFloat(), l.getDouble("stars").toFloat())
            }
            val vil = o.getJSONArray("village")
            val village = FloatArray(vil.length() * 3) { vil.getJSONArray(it / 3).getDouble(it % 3).toFloat() }
            val ch = o.getJSONArray("check")
            val checks = List(ch.length()) { i ->
                val e = ch.getJSONObject(i); val rgb = e.getJSONArray("rgb")
                Check(e.getString("tex").substringBefore('.'), e.getDouble("u").toFloat(), e.getDouble("v").toFloat(), IntArray(3) { rgb.getInt(it) })
            }
            return LiveData(
                w.getDouble("x0").toFloat(), w.getDouble("x1").toFloat(), w.getDouble("z0").toFloat(), w.getDouble("z1").toFloat(),
                Cam(c.getDouble("tx").toFloat(), c.getDouble("ty").toFloat(), c.getDouble("tz").toFloat(), c.getDouble("dist").toFloat(),
                    c.getDouble("yaw_deg").toFloat(), c.getDouble("pitch_deg").toFloat(), c.getDouble("fov_deg").toFloat()),
                k.getDouble("ripple").toFloat(),
                floatArrayOf(hz.getDouble("start_x_dist").toFloat(), hz.getDouble("length_x_dist").toFloat(), hz.getDouble("max").toFloat()),
                hz.getDouble("valley").toFloat(),
                floatArrayOf(air.getDouble("at_9km").toFloat(), air.getDouble("more").toFloat()),
                k.getDouble("sky_top_deg").toFloat(),
                floatArrayOf(h.getDouble("lo_deg").toFloat(), h.getDouble("hi_deg").toFloat(), h.getDouble("far_m").toFloat()),
                keys, village, checks,
            )
        }
    }
}

/**
 * The light of a moment for the live model, by the designer's rule (README section 4): the seven keys ordered by the
 * sun's height on its side of noon, blended linearly between neighbours; above noon's height it is noon, more than 12
 * degrees under the horizon it is night. Pure, so the tests can walk a whole day.
 */
object LiveLight {
    class Mix(
        val lit: FloatArray, val shade: FloatArray, val rock: FloatArray, val rockS: FloatArray,
        val zen: FloatArray, val hor: FloatArray, val band: FloatArray, val bandOn: Float,
        val direct: Float, val stars: Float,
        /** Towards the light: the sun above 5.5 degrees under the horizon (lit as from 1.2 at least), else the moon. */
        val dir: FloatArray, val glow: Float, val sunUp: Float,
    )

    private const val NIGHT_BELOW = -12f
    private const val SUN_UNTIL = -5.5f

    /**
     * [altDeg] the sun's altitude, [am] before noon, [sunDir] towards the sun, [nightDir] the engine's light without a
     * sun (the moon, or the weak light from the south-east), [moonUp] whether that is the moon.
     */
    fun at(keys: List<LiveData.Key>, altDeg: Float, am: Boolean, sunDir: FloatArray, nightDir: FloatArray, moonUp: Boolean): Mix {
        fun k(n: String) = keys.first { it.name == n }
        val seq = if (am) listOf("night", "blue", "dawn", "morning", "noon") else listOf("night", "blue", "sunset", "after", "noon")
        val alts = seq.map { if (it == "night") NIGHT_BELOW else k(it).alt }
        var a = k(seq.last()); var b = a; var t = 0f
        when {
            altDeg >= alts.last() -> {}
            altDeg <= alts.first() -> { a = k("night"); b = a }
            else -> {
                var i = 0
                while (altDeg > alts[i + 1]) i++
                a = k(seq[i]); b = k(seq[i + 1]); t = (altDeg - alts[i]) / (alts[i + 1] - alts[i])
            }
        }
        fun m(x: FloatArray, y: FloatArray) = FloatArray(3) { x[it] + (y[it] - x[it]) * t }
        val bandOn = (if (a.band != null) 1 - t else 0f) + (if (b.band != null) t else 0f)
        val band = a.band ?: b.band ?: floatArrayOf(1f, 1f, 1f)
        var direct = a.direct + (b.direct - a.direct) * t
        val stars = a.stars + (b.stars - a.stars) * t
        val sun = altDeg > SUN_UNTIL
        val dir: FloatArray
        if (sun) {
            // the sun's own direction, lifted to at least 1.2 degrees for the light and the shadows
            val h = Math.hypot(sunDir[0].toDouble(), sunDir[2].toDouble()).coerceAtLeast(1e-6)
            val lift = Math.toRadians(1.2)
            val alt = maxOf(Math.atan2(sunDir[1].toDouble(), h), lift)
            dir = floatArrayOf((sunDir[0] / h * Math.cos(alt)).toFloat(), Math.sin(alt).toFloat(), (sunDir[2] / h * Math.cos(alt)).toFloat())
        } else {
            dir = nightDir.copyOf()
            if (!moonUp) direct *= .6f
        }
        val glow = if (sun) direct * (1f - smooth(4f, 7f, altDeg)) else 0f
        val sunUp = if (altDeg > 0f && direct > 0f && stars < 1f) 1f else 0f
        return Mix(m(a.lit, b.lit), m(a.shade, b.shade), m(a.rock, b.rock), m(a.rockS, b.rockS), m(a.zen, b.zen), m(a.hor, b.hor),
            band, bandOn, direct, stars, dir, glow, sunUp)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** The cast shadow, smoothed once with a 3 by 3 (1 2 1) kernel so its edge is not stepped (the handoff, section 3). */
    fun soften(s: FloatArray, nx: Int, ny: Int): FloatArray {
        val o = s.copyOf()
        for (r in 1 until ny - 1) for (c in 1 until nx - 1) {
            var a = 0f
            for (j in -1..1) for (i in -1..1) a += s[(r + j) * nx + c + i] * (if (i != 0 && j != 0) 1 else if (i != 0 || j != 0) 2 else 4)
            o[r * nx + c] = a / 16f
        }
        return o
    }
}

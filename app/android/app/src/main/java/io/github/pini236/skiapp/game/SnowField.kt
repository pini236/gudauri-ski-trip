package io.github.pini236.skiapp.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** The kinds of snow (the site's KINDS): how deep a press goes, how much rises around it, how rough, how soft. */
enum class SnowKind(val depth: Float, val berm: Float, val rough: Float, val soft: Float) {
    POWDER(1.0f, .35f, .12f, 1.0f), CRUST(.75f, .15f, .5f, .25f), WET(.6f, .55f, .03f, .5f)
}

/**
 * Fresh snow's surface (the site's fresh snow, 13.6): a height field of [gw] by [gh] cells, lit by a low sun from the
 * top left. A press pushes the snow down and a little up around the rim (the frozen crust cracks), the snowcat's blade
 * flattens it and leaves corduroy, and fresh snow fills every track back in.
 */
class SnowField(val gw: Int, val gh: Int, private val rnd: Random = Random.Default) {
    val h = FloatArray(gw * gh)
    val base = FloatArray(gw * gh)
    /** 0 untouched; a track keeps the snow it was made in (1, or 2 for wet: X-5, the site's fix). */
    val touched = ByteArray(gw * gh)
    val crack = BooleanArray(gw * gh)
    var kind = SnowKind.POWDER
    /** Tiny ice crystals that catch the sun. */
    private val sparkle = FloatArray(1 shl 16) { rnd.nextFloat() }

    init {
        for (y in 0 until gh) for (x in 0 until gw) {
            val v = 2 + .12 * sin(x * .03 + y * .012) + .09 * sin(y * .035 - x * .01 + 1.3) + .015 * sin(x * .21 + y * .17)
            base[y * gw + x] = v.toFloat()
        }
        base.copyInto(h)
    }

    /** All new snow at once. */
    fun fresh() { base.copyInto(h); touched.fill(0); crack.fill(false) }

    /** Fresh snow falling: every track fills back in by [k] of what is left. */
    fun fill(k: Float) {
        for (i in h.indices) if (h[i] != base[i]) {
            h[i] += (base[i] - h[i]) * k
            if (abs(h[i] - base[i]) < .01f) { h[i] = base[i]; touched[i] = 0; crack[i] = false }
        }
    }

    /**
     * An oval stamp at ([cx0], [cy0]) in cells, [rx] by [ry], turned by [ang]: the snow inside goes down by [depth] (by
     * [profile] of the stamp's own coordinates, or a soft bowl), and a little of it rises around the rim. The snow moved.
     */
    fun press(cx0: Float, cy0: Float, rx: Float, ry: Float, ang: Float, depth: Float, profile: ((Float, Float) -> Float)? = null): Float {
        val k = kind; val ca = cos(ang); val sa = sin(ang); val r0 = max(rx, ry) * 1.7f
        var moved = 0f
        val x0 = max(1, floor(cx0 - r0).toInt()); val x1 = min(gw - 2, ceil(cx0 + r0).toInt())
        val y0 = max(1, floor(cy0 - r0).toInt()); val y1 = min(gh - 2, ceil(cy0 + r0).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x - cx0; val dy = y - cy0
            val u = (dx * ca + dy * sa) / rx; val v = (-dx * sa + dy * ca) / ry
            val r = hypot(u, v); val i = y * gw + x
            if (r < 1) {
                val pr = profile?.invoke(u, v) ?: cos(r * PI.toFloat() / 2).pow(if (k.soft < .5f) .4f else .8f)
                val jitter = 1 + (rnd.nextFloat() - .5f) * k.rough
                val target = base[i] - depth * k.depth * pr * jitter
                if (h[i] > target) { moved += h[i] - target; h[i] = target; touched[i] = if (k == SnowKind.WET) 2 else 1 }
                if (k == SnowKind.CRUST && r > .82f && rnd.nextFloat() < .35f) crack[i] = true
            } else if (r < 1.6f) {
                val add = k.berm * depth * .25f * sin((r - 1) / .6f * PI.toFloat())
                if (add > 0 && h[i] < base[i] + .6f) h[i] += add * (.6f + rnd.nextFloat() * .4f) * .25f
            }
        }
        // cracks run out from the stamp in the frozen crust
        if (k == SnowKind.CRUST && moved > .5f) repeat(3) {
            var a = rnd.nextFloat() * 2 * PI.toFloat(); var x = cx0 + cos(a) * rx; var y = cy0 + sin(a) * ry
            repeat(14) {
                a += (rnd.nextFloat() - .5f) * .8f; x += cos(a); y += sin(a)
                val xi = x.toInt(); val yi = y.toInt()
                if (xi in 1 until gw && yi in 1 until gh) crack[yi * gw + xi] = true
            }
        }
        return moved
    }

    /** The snowcat: a wide blade flattens everything in front of it, and the tiller leaves corduroy behind. */
    fun groom(cx0: Float, cy0: Float, ang: Float): Float {
        val half = 24f; val ca = cos(ang); val sa = sin(ang); val r0 = half + 2
        var m = 0f
        val x0 = max(1, floor(cx0 - r0).toInt()); val x1 = min(gw - 2, ceil(cx0 + r0).toInt())
        val y0 = max(1, floor(cy0 - r0).toInt()); val y1 = min(gh - 2, ceil(cy0 + r0).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x - cx0; val dy = y - cy0
            val along = dx * ca + dy * sa; val cross = -dx * sa + dy * ca
            if (abs(along) < 2.2f && abs(cross) < half) {
                val i = y * gw + x
                val edge = min(1f, (half - abs(cross)) / 3)
                val target = base[i] - .1f + .035f * sin(cross * 1.25f)
                val nh = h[i] + (target - h[i]) * edge
                m += abs(h[i] - nh); h[i] = nh; touched[i] = 0; crack[i] = false
            }
        }
        return m
    }

    /** The light on the snow into [px] (ARGB, a pixel a cell): slopes facing the sun bright, the far walls of a hole in blue shadow. */
    fun render(px: IntArray) {
        val wet = kind == SnowKind.WET
        for (y in 0 until gh) for (x in 0 until gw) {
            val i = y * gw + x
            val hx = h[i + if (x < gw - 1) 1 else 0] - h[i - if (x > 0) 1 else 0]
            val hy = h[i + if (y < gh - 1) gw else 0] - h[i - if (y > 0) gw else 0]
            val l = (.82f + (hx * .9f + hy * 1.2f) * 1.6f).coerceIn(.35f, 1.12f)
            val depth = max(0f, base[i] - h[i])
            var r = 226 * l + 20; var g = 236 * l + 14; var b = 248 * l + 8
            r -= depth * 22; g -= depth * 12
            // a track keeps the snow it was made in; the untouched snow is the kind chosen now
            if (if (touched[i] != 0.toByte()) touched[i] == 2.toByte() else wet) { r -= 6; g -= 3 }
            if (crack[i]) { r -= 40; g -= 30; b -= 18 }
            if (l > .95f && sparkle[i and 65535] > .985f && touched[i] == 0.toByte()) { r = 255f; g = 255f; b = 255f }
            px[i] = (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
    }

    /** How much of the snow has tracks, in percent (every seventh cell, as the site counts). */
    fun tracks(): Int {
        var c = 0; var i = 0
        while (i < touched.size) { if (touched[i] != 0.toByte()) c++; i += 7 }
        return Math.round(c * 7f / touched.size * 100)
    }

    companion object {
        /** A boot's sole: a heel and a toe, with a tread (the stamp's own coordinates). */
        fun boot(u: Float, v: Float): Float {
            val toe = hypot(u * 1.05f, (v + .38f) / .62f); val heel = hypot(u * 1.25f, (v - .55f) / .45f)
            return if (min(toe, heel) < 1) (if (sin(v * 22) > .2f) 1f else .9f) else 0f
        }
    }
}

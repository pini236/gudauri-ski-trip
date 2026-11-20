package io.github.pini236.skiapp.redesign

import kotlin.math.floor

/**
 * The live mountain's one detail texture (the architect's condition: one, not one per hour), made in code until the
 * designer's arrives. It tiles. R: the snow's grain and wind ripples; G: rock bands (stretched along x) and the glints;
 * B: large patches that break up the clean edges of rock and snow. A: 255.
 */
object Detail {
    fun make(n: Int = LiveRenderer.DETAIL): ByteArray {
        val out = ByteArray(n * n * 4)
        for (y in 0 until n) for (x in 0 until n) {
            val u = x.toFloat() / n; val v = y.toFloat() / n
            // ripples: noise stretched across the slope
            val r = fbm(u * 10, v * 34, 4, 10, 34) * .7f + fbm(u * 32, v * 32, 5, 32, 32) * .3f
            val g = fbm(u * 3, v * 40, 3, 3, 40) * .6f + noise(u * 64, v * 64, 64, 64) * .4f
            val b = fbm(u * 4, v * 4, 4, 4, 4)
            val o = (y * n + x) * 4
            out[o] = (r.coerceIn(0f, 1f) * 255).toInt().toByte()
            out[o + 1] = (g.coerceIn(0f, 1f) * 255).toInt().toByte()
            out[o + 2] = (b.coerceIn(0f, 1f) * 255).toInt().toByte()
            out[o + 3] = 255.toByte()
        }
        return out
    }

    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 65535f
    }

    /** Value noise that repeats every [px] by [py] cells. */
    private fun noise(x: Float, y: Float, px: Int, py: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        fun h(i: Int, j: Int) = hash(Math.floorMod(i, px), Math.floorMod(j, py))
        val a = h(x0, y0); val b = h(x0 + 1, y0); val c = h(x0, y0 + 1); val d = h(x0 + 1, y0 + 1)
        return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy
    }

    private fun fbm(x: Float, y: Float, oct: Int, px: Int, py: Int): Float {
        var s = 0f; var a = .5f; var f = 1; var t = 0f
        for (i in 0 until oct) { s += a * noise(x * f, y * f, px * f, py * f); t += a; a *= .5f; f *= 2 }
        return s / t
    }
}

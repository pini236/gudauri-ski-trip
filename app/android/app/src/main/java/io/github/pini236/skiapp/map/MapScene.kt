package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Approved slope thresholds (decision 10): 15°, 25°, 30°, with the site's colours (GudRelief.SLOPE in site/js/relief.js,
 * checked in SlopeColorsTest). RGB 0..1. Defined once: a chosen run's paint, and the ground around it ([MapScene.slopeLayer]).
 */
object SlopeColors {
    val LIMITS = floatArrayOf(15f, 25f, 30f)
    val HEX = listOf("#3FA85F", "#F2C13D", "#F08A3C", "#DC3B33")
    private val RGB = HEX.map { h -> FloatArray(3) { i -> h.substring(1 + i * 2, 3 + i * 2).toInt(16) / 255f } }

    fun of(deg: Float): FloatArray = RGB[LIMITS.indexOfFirst { deg < it }.let { if (it < 0) LIMITS.size else it }].copyOf()
}

/** Interleaved terrain vertices: position(3) normal(3) colour(3) slope(1) = 10 floats. */
class TerrainMesh(val vertices: FloatArray, val indices: IntArray, val count: Int) {
    companion object {
        const val STRIDE = 10

        fun build(t: Terrain): TerrainMesh {
            val nx = t.nx; val ny = t.ny
            val v = FloatArray(nx * ny * STRIDE)
            for (r in 0 until ny) for (c in 0 until nx) {
                val i = r * nx + c
                val h = t.at(c, r)
                val gx = (t.at(minOf(c + 1, nx - 1), r) - t.at(maxOf(c - 1, 0), r)) / (2 * t.sx)
                val gz = (t.at(c, minOf(r + 1, ny - 1)) - t.at(c, maxOf(r - 1, 0))) / (2 * t.sy)
                val len = sqrt(gx * gx + 1 + gz * gz)
                val slope = Math.toDegrees(atan(hypot(gx, gz).toDouble())).toFloat()
                // the site's snow texture: snow, steep faces shed it to rock, steep low valley walls are scrub
                val rock = ((slope - 36) / 18).coerceIn(0f, 1f) * 0.75f
                val low = ((1750 - h) / 350).coerceIn(0f, 1f) * ((slope - 18) / 14).coerceIn(0f, 1f)
                var cr = 246f; var cg = 249f; var cb = 252f
                cr = cr * (1 - rock) + 150 * rock; cg = cg * (1 - rock) + 153 * rock; cb = cb * (1 - rock) + 160 * rock
                cr = cr * (1 - low) + 150 * low; cg = cg * (1 - low) + 160 * low; cb = cb * (1 - low) + 158 * low
                val cool = ((3000 - h) / 1600).coerceIn(0f, 1f) * 6
                val o = i * STRIDE
                v[o] = t.x0 + c * t.sx; v[o + 1] = h; v[o + 2] = t.y0 + r * t.sy
                v[o + 3] = -gx / len; v[o + 4] = 1 / len; v[o + 5] = -gz / len
                v[o + 6] = (cr - cool) / 255f; v[o + 7] = (cg - cool * 0.5f) / 255f; v[o + 8] = cb / 255f
                v[o + 9] = slope
            }
            val idx = IntArray((nx - 1) * (ny - 1) * 6)
            var k = 0
            for (r in 0 until ny - 1) for (c in 0 until nx - 1) {
                val a = r * nx + c; val b = a + 1; val e = a + nx; val f = e + 1
                idx[k++] = a; idx[k++] = e; idx[k++] = b
                idx[k++] = b; idx[k++] = e; idx[k++] = f
            }
            return TerrainMesh(v, idx, idx.size)
        }
    }
}

/**
 * Screen-space ribbons for lines on the mountain: per vertex position(3) prev(3) next(3) side(1) rgba(4) progress(1).
 */
class Ribbon(val vertices: FloatArray, val indices: IntArray) {
    companion object {
        const val STRIDE = 15

        fun build(lines: List<FloatArray>, colour: (line: Int, point: Int) -> FloatArray, progress: (line: Int, point: Int) -> Float = { _, _ -> 0f }): Ribbon {
            var points = 0
            for (l in lines) points += l.size / 3
            val v = FloatArray(points * 2 * STRIDE)
            val idx = ArrayList<Int>(points * 6)
            var base = 0
            var o = 0
            lines.forEachIndexed { li, l ->
                val n = l.size / 3
                if (n < 2) return@forEachIndexed
                for (i in 0 until n) {
                    val p = i * 3; val pr = maxOf(0, i - 1) * 3; val nx = minOf(n - 1, i + 1) * 3
                    val col = colour(li, i); val pg = progress(li, i)
                    for (side in intArrayOf(-1, 1)) {
                        v[o] = l[p]; v[o + 1] = l[p + 1]; v[o + 2] = l[p + 2]
                        v[o + 3] = l[pr]; v[o + 4] = l[pr + 1]; v[o + 5] = l[pr + 2]
                        v[o + 6] = l[nx]; v[o + 7] = l[nx + 1]; v[o + 8] = l[nx + 2]
                        v[o + 9] = side.toFloat()
                        v[o + 10] = col[0]; v[o + 11] = col[1]; v[o + 12] = col[2]; v[o + 13] = if (col.size > 3) col[3] else 1f
                        v[o + 14] = pg
                        o += STRIDE
                    }
                    if (i < n - 1) { val a = base + i * 2; idx += listOf(a, a + 1, a + 2, a + 2, a + 1, a + 3) }
                }
                base += n * 2
            }
            return Ribbon(v.copyOf(o), idx.toIntArray())
        }
    }
}

/** A run's lines for the GPU: its key, the white casing and the coloured core. */
class PisteLines(val key: String, val casing: Ribbon, val core: Ribbon)

/** Everything the map needs, prepared off the GL thread. */
class MapScene(val terrain: Terrain, val runs: Runs) {
    val mesh = TerrainMesh.build(terrain)
    var shadow: FloatArray = FloatArray(terrain.nx * terrain.ny)
    /** The sky and the light now (or at the QA run's pinned time); the shadows above are cast by its [Sky.Light.dir]. */
    @Volatile var light: Sky.Light = Sky.at(System.currentTimeMillis())

    /** Draped lines (x, y, z triples) for each run, densified every 18 m and lifted above the snow. */
    val draped: Map<String, List<FloatArray>> = runs.pistes.associate { p -> p.key to p.lines.map { drape(it, 4f) } }
    val liftLines: List<FloatArray> = runs.lifts.map { cable(it) }

    /**
     * White casing and coloured core for every run, built here so the first GL frame only uploads. The core's progress
     * is metres along the line: a closed run is drawn dashed (the lift status, S1).
     */
    val pisteRibbons: List<PisteLines> = runs.pistes.mapNotNull { p ->
        val lines = draped[p.key] ?: return@mapNotNull null
        if (lines.isEmpty()) return@mapNotNull null
        val c = MapRenderer.runRgb(p.color)
        val m = metres(lines)
        PisteLines(p.key, Ribbon.build(lines, { _, _ -> floatArrayOf(1f, 1f, 1f, 1f) }), Ribbon.build(lines, { _, _ -> c }, { li, i -> m[li][i] }))
    }
    /** One ribbon per lift, by name, so a closed one can be drawn grey and dashed. */
    val liftRibbons: List<Pair<String, Ribbon>> = runs.lifts.mapIndexed { i, l ->
        val m = metres(listOf(liftLines[i]))
        l.name to Ribbon.build(listOf(liftLines[i]), { _, _ -> floatArrayOf(0.227f, 0.271f, 0.337f, 1f) }, { _, k -> m[0][k] })
    }

    /** Metres along each line (x, y, z triples), from its first point. */
    private fun metres(lines: List<FloatArray>): List<FloatArray> = lines.map { l ->
        val n = l.size / 3
        FloatArray(n).also { c -> for (i in 1 until n) c[i] = c[i - 1] + hypot(hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1]), l[i * 3 + 1] - l[i * 3 - 2]) }
    }

    fun drape(xy: FloatArray, lift: Float): FloatArray {
        val out = ArrayList<Float>()
        val n = xy.size / 2
        for (i in 0 until n) {
            val ax = xy[i * 2]; val ay = xy[i * 2 + 1]
            if (i > 0) {
                val bx = xy[i * 2 - 2]; val by = xy[i * 2 - 1]
                val steps = kotlin.math.ceil(hypot(ax - bx, ay - by) / 18f).toInt()
                for (k in 1 until steps) {
                    val f = k.toFloat() / steps
                    val x = bx + (ax - bx) * f; val y = by + (ay - by) * f
                    out += x; out += terrain.elev(x, y) + lift; out += y
                }
            }
            out += ax; out += terrain.elev(ax, ay) + lift; out += ay
        }
        return out.toFloatArray()
    }

    /** A lift: the straight chord between the stations, kept above the snow (the site's cable()). */
    private fun cable(l: Lift): FloatArray {
        val d = drape(l.pts, 0f)
        val n = d.size / 3
        val e0 = d[1] + 10; val e1 = d[(n - 1) * 3 + 1] + 10
        var tot = 0f
        for (i in 1 until n) tot += hypot(d[i * 3] - d[i * 3 - 3], d[i * 3 + 2] - d[i * 3 - 1])
        var acc = 0f
        for (i in 0 until n) {
            if (i > 0) acc += hypot(d[i * 3] - d[i * 3 - 3], d[i * 3 + 2] - d[i * 3 - 1])
            val f = if (tot > 0) acc / tot else 0f
            d[i * 3 + 1] = maxOf(e0 + (e1 - e0) * f, d[i * 3 + 1] + 9)
        }
        return d
    }

    /** A run's lines from the top down, joined in order of their highest point. */
    fun topDown(p: Piste): List<FloatArray> = (draped[p.key] ?: emptyList()).map { l ->
        val n = l.size / 3
        if (l[1] < l[(n - 1) * 3 + 1]) FloatArray(l.size).also { r -> for (i in 0 until n) for (j in 0..2) r[i * 3 + j] = l[(n - 1 - i) * 3 + j] } else l
    }.sortedByDescending { it[1] }

    /** Slope in degrees along a draped line at each point, over about 40 m. */
    fun lineSlopes(l: FloatArray): FloatArray {
        val n = l.size / 3
        val cum = FloatArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + hypot(l[i * 3] - l[i * 3 - 3], l[i * 3 + 2] - l[i * 3 - 1])
        return FloatArray(n) { i ->
            var a = i; var b = i
            while (a > 0 && cum[i] - cum[a] < 20) a--
            while (b < n - 1 && cum[b] - cum[i] < 20) b++
            val run = (cum[b] - cum[a]).coerceAtLeast(1f)
            Math.toDegrees(atan((kotlin.math.abs(l[a * 3 + 1] - l[b * 3 + 1]) / run).toDouble())).toFloat()
        }
    }

    /**
     * The slope of the ground at a point (X-3, docs/ARCHITECTURE.md): the smoothed height's central difference, 20 m
     * each way along both axes, as the site's GudRelief.slopeCanvas. Not the model's own points (about 40 m apart, so
     * 80 m across), which smoothed a short wall away.
     */
    fun groundSlope(x: Float, z: Float): Float {
        val t = terrain
        val gx = (t.elev(x + GROUND_D, z) - t.elev(x - GROUND_D, z)) / (2 * GROUND_D)
        val gz = (t.elev(x, z + GROUND_D) - t.elev(x, z - GROUND_D)) / (2 * GROUND_D)
        return Math.toDegrees(atan(hypot(gx, gz).toDouble())).toFloat()
    }

    /**
     * The ground around a chosen run, coloured by slope (T1, X-3): a texture of 10 m over the run's box and 150 m
     * around it, as the site's slopeCanvas, laid on the terrain by the renderer. Alpha is how much each texel belongs
     * to the run's surroundings: whole near the line, fading out by 150 m.
     */
    fun slopeLayer(lines: List<FloatArray>): SlopeLayer {
        val t = terrain
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (l in lines) for (i in 0 until l.size / 3) {
            minX = minOf(minX, l[i * 3]); maxX = maxOf(maxX, l[i * 3])
            minZ = minOf(minZ, l[i * 3 + 2]); maxZ = maxOf(maxZ, l[i * 3 + 2])
        }
        val x0 = maxOf(t.x0, minX - AROUND); val z0 = maxOf(t.y0, minZ - AROUND)
        val x1 = minOf(t.x1, maxX + AROUND); val z1 = minOf(t.y1, maxZ + AROUND)
        val w = maxOf(2, ((x1 - x0) / TEXEL).roundToInt()); val h = maxOf(2, ((z1 - z0) / TEXEL).roundToInt())
        val out = ByteArray(w * h * 4)
        for (j in 0 until h) for (i in 0 until w) {
            val x = x0 + (i + 0.5f) * TEXEL; val z = z0 + (j + 0.5f) * TEXEL
            var best = Float.MAX_VALUE
            for (l in lines) {
                for (k in 0 until l.size / 3 - 1) {
                    val ax = l[k * 3]; val az = l[k * 3 + 2]; val dx = l[k * 3 + 3] - ax; val dz = l[k * 3 + 5] - az
                    val len2 = dx * dx + dz * dz
                    val f = if (len2 > 0) (((x - ax) * dx + (z - az) * dz) / len2).coerceIn(0f, 1f) else 0f
                    val ex = ax + dx * f - x; val ez = az + dz * f - z
                    best = minOf(best, ex * ex + ez * ez)
                }
            }
            val a = ((AROUND - sqrt(best)) / 60f).coerceIn(0f, 1f)
            val c = SlopeColors.of(groundSlope(x, z))
            val o = (j * w + i) * 4
            out[o] = (c[0] * 255).roundToInt().toByte(); out[o + 1] = (c[1] * 255).roundToInt().toByte()
            out[o + 2] = (c[2] * 255).roundToInt().toByte(); out[o + 3] = (a * 255).roundToInt().toByte()
        }
        return SlopeLayer(x0, z0, w * TEXEL, h * TEXEL, w, h, out)
    }

    companion object {
        /** X-3: the ground's slope over 20 m each way, in texels of 10 m, out to 150 m from the run (decision 10). */
        const val GROUND_D = 20f
        const val TEXEL = 10f
        const val AROUND = 150f
    }
}

/** The ground around a chosen run in slope colours: its corner and size in metres, and RGBA texels from the corner. */
class SlopeLayer(val x0: Float, val z0: Float, val width: Float, val depth: Float, val w: Int, val h: Int, val rgba: ByteArray)

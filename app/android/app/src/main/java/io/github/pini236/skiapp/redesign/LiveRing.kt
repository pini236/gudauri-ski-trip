package io.github.pini236.skiapp.redesign

import java.util.Base64
import java.util.zip.Inflater
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * The far ground round the live mountain (assets/redesign/live/ring.json, schema 2, built by hand with
 * tools/build-ring.py; the architect's light option of 25.11.2026 and the designer's handoff
 * design/handoff/2026-11-25-redesign-ring-detail): real heights from the same source and projection as the height
 * model, in two grids. The inner one (300 m cells, 22.2 km each way) carries the near mountains and Kazbek; the outer
 * one (1,400 m, 42 km) the far ones. A relief map (ringrelief.webp, 90 m) shades the inner one like the mountain.
 *
 * Only a view: no tap, no labels, no "where am I"; a cast shadow, worked out off the drawing thread ([shadows]).
 * Under the height model the inner ring lies under the mesh (from the mesh's own heights), and where the outer ring is
 * under the inner one it lies under it: no two surfaces fight, and there is no seam.
 */
class LiveRing private constructor(
    /** x, y, z, then the normal's x, y, z, for every vertex: the inner grid's first, then the outer's. */
    val vertices: FloatArray,
    val indices: IntArray,
    /** Half the ring's side (metres from the middle): its last 5 km go into the air. */
    val half: Float,
    val inner: Grid, val outer: Grid,
    /** The relief map's box over the inner ring: x0, y0, x1, y1 (metres). */
    val reliefBox: FloatArray,
    /** The model's box (x0, y0, x1, y1) and its heights, for the cast shadow. */
    val model: FloatArray, private val elev: (Float, Float) -> Float,
    /** The front seam of step 4 (the model bent to the ring, the band); false is the ring of before, for the emulator's "before". */
    val seam: Boolean = true,
) {
    val triangles get() = indices.size / 3
    val points get() = vertices.size / STRIDE

    /** The relief map, decoded off the GL thread by whoever loads the ring; null draws the ring without it. */
    @Volatile var relief: LiveTex? = null
    /** The band (step 4, ringband.webp): the model's own maps carried past its edge; null draws the seam as before. */
    @Volatile var band: Band? = null

    /**
     * The model's maps past its edge (the designer's step 4): slope east, slope south, occlusion and rock potential as
     * one RGBA texture over [x0]..[x1], [z0]..[z1] (metres, row 0 north), [w] by [h] texels.
     */
    class Band(val x0: Float, val z0: Float, val x1: Float, val z1: Float, val margin: Float, val w: Int, val h: Int, val px: ByteArray) {
        companion object {
            /**
             * [grey]: the four planes stacked top to bottom in one grey picture ([w] by 4 [h], the red channel read),
             * as ARGB ints; [json] is ringband.json.
             */
            fun of(json: JSONObject, grey: IntArray, w: Int, h4: Int): Band {
                val h = h4 / 4
                check(w == json.getJSONArray("px").getInt(0) && h == json.getJSONArray("px").getInt(1)) { "ringband: ${w}x$h" }
                val px = ByteArray(w * h * 4)
                for (k in 0..3) for (i in 0 until w * h) px[i * 4 + k] = (grey[k * w * h + i] shr 16).toByte()
                return Band(json.getDouble("x0").toFloat(), json.getDouble("z0").toFloat(), json.getDouble("x1").toFloat(),
                    json.getDouble("z1").toFloat(), json.getDouble("margin").toFloat(), w, h, px)
            }
        }
    }

    /** How far [x], [y] is inside the model's edge (metres; below 0 outside it). */
    fun inward(x: Float, y: Float): Float = minOf(minOf(x - model[0], model[2] - x), minOf(y - model[1], model[3] - y))

    /**
     * The model's height bent to the ring's over its last [BEND] metres (the designer's step 4, Y03): at the edge the
     * two are one height, so the ground goes on as one, with no flat band and no step.
     */
    fun bent(x: Float, y: Float, h: Float): Float {
        if (!seam) return h
        val d = inward(x, y)
        if (d >= BEND || d < 0f) return h
        val t = 1f - d / BEND; val w = t * t * (3f - 2f * t)
        return h + (inner.at(x, y) - h) * w
    }

    /** A copy of the model's interleaved vertices ([stride] floats, y second) with the last [BEND] metres bent. */
    fun bend(v: FloatArray, stride: Int): FloatArray {
        val o = v.copyOf()
        for (k in 0 until v.size / stride) { val i = k * stride; o[i + 1] = bent(v[i], v[i + 2], v[i + 1]) }
        return o
    }

    /** A grid of the file: its own heights (not the lowered ones), [nx] by [ny] from ([x0], [y0]) every [cell] metres. */
    class Grid(val x0: Float, val y0: Float, val cell: Float, val nx: Int, val ny: Int, val h: ShortArray) {
        val x1 get() = x0 + (nx - 1) * cell
        val y1 get() = y0 + (ny - 1) * cell
        fun inside(x: Float, y: Float) = x >= x0 && x <= x1 && y >= y0 && y <= y1
        /** Bilinear, clamped to the edge. */
        fun at(x: Float, y: Float): Float {
            val c = ((x - x0) / cell).coerceIn(0f, nx - 1.0001f); val r = ((y - y0) / cell).coerceIn(0f, ny - 1.0001f)
            val c0 = c.toInt(); val r0 = r.toInt(); val fc = c - c0; val fr = r - r0; val i = r0 * nx + c0
            return (h[i] * (1 - fc) + h[i + 1] * fc) * (1 - fr) + (h[i + nx] * (1 - fc) + h[i + nx + 1] * fc) * fr
        }
    }

    /** The ground's height at a point: the model in its box, then the inner grid, then the outer one. */
    fun ground(x: Float, y: Float): Float = when {
        x > model[0] && x < model[2] && y > model[1] && y < model[3] -> bent(x, y, elev(x, y))
        inner.inside(x, y) -> inner.at(x, y)
        else -> outer.at(x, y)
    }

    /** Whether the outer ring's shadow was over the time budget once (then it stays off: no flicker between hours). */
    @Volatile var outerShadowOff = false
        private set

    /** How long the last [shadows] took (ms), for the log. */
    @Volatile var lastShadowMs = 0L
        private set

    /**
     * The cast shadow at every vertex for the sun in [dir] (x east, y up, z south), 0 in the sun and 1 in the shade,
     * softened 1 2 1 in each grid: a march toward the sun over [ground], half a cell a step and longer further out, to
     * 24 km or until the ray is over 5,200 m. The inner ring first; the outer one only while the whole stays within
     * [budgetMs] (the architect: 150 ms on a middling phone), otherwise the outer ring has none from then on. Vertices
     * deeper under the model than two cells have none (they are hidden). Off the drawing thread.
     */
    fun shadows(dir: FloatArray, budgetMs: Long = SHADOW_MS): FloatArray {
        val t0 = System.nanoTime()
        val out = FloatArray(points)
        if (dir[1] <= 0f) { out.fill(1f); lastShadowMs = 0; return out }
        val horiz = sqrt(dir[0] * dir[0] + dir[2] * dir[2]).coerceAtLeast(1e-4f)
        val ux = dir[0] / horiz; val uz = dir[2] / horiz; val rise = dir[1] / horiz
        fun march(k: Int, base: Float) {
            val x = vertices[k * STRIDE]; val z = vertices[k * STRIDE + 2]
            // under the model: hidden, but for two cells in (the seam's triangles)
            if (inward(x, z) > 2 * inner.cell) return
            val h0 = ground(x, z) + 3f
            var s = 0f; var dd = base
            while (dd < 24_000f) {
                val ray = h0 + rise * dd
                if (ray > 5200f) break
                val over = ground(x + ux * dd, z + uz * dd) - ray
                if (over > 0) { s = maxOf(s, (over / 30f).coerceAtMost(1f)); if (s >= 1f) break }
                dd += base * (1f + dd / 6000f)
            }
            out[k] = s
        }
        val ni = inner.nx * inner.ny
        for (k in 0 until ni) march(k, inner.cell / 2)
        System.arraycopy(LiveLight.soften(out.copyOfRange(0, ni), inner.nx, inner.ny), 0, out, 0, ni)
        if (!outerShadowOff) {
            val deadline = t0 + budgetMs * 1_000_000
            var k = ni
            while (k < points) {
                march(k, outer.cell / 2); k++
                if (System.nanoTime() > deadline) { outerShadowOff = true; break }
            }
            if (outerShadowOff) out.fill(0f, ni, points)
            else System.arraycopy(LiveLight.soften(out.copyOfRange(ni, points), outer.nx, outer.ny), 0, out, ni, points - ni)
        }
        lastShadowMs = (System.nanoTime() - t0) / 1_000_000
        return out
    }

    /**
     * The model's own cast shadow [raw] (a value a grid point, [nx] by [ny] from [gx0], [gy0] every [sx], [sy]), redone
     * in its last [BEND] metres over the same ground as the ring (the bent model, then the ring past its box), so the
     * two shade as one at the edge; 400 m of hand-over into [raw] inside that. Before softening. Off the drawing thread.
     */
    fun modelBand(raw: FloatArray, nx: Int, ny: Int, gx0: Float, gy0: Float, sx: Float, sy: Float, dir: FloatArray,
                  budgetMs: Long = SHADOW_MS): FloatArray {
        if (dir[1] <= 0f || !seam) return raw
        val deadline = System.nanoTime() + budgetMs * 1_000_000
        val horiz = sqrt(dir[0] * dir[0] + dir[2] * dir[2]).coerceAtLeast(1e-4f)
        val ux = dir[0] / horiz; val uz = dir[2] / horiz; val rise = dir[1] / horiz
        fun march(x: Float, z: Float): Float {
            val h0 = ground(x, z) + 3f
            var s = 0f; var dd = 35f
            while (dd < 24_000f) {
                val ray = h0 + rise * dd
                if (ray > 5200f) break
                val over = ground(x + ux * dd, z + uz * dd) - ray
                if (over > 0) { s = maxOf(s, (over / 30f).coerceAtMost(1f)); if (s >= 1f) break }
                dd += 35f * (1f + dd / 3000f)
            }
            return s
        }
        // the rays every BAND_STEP cells only (the shadow is softened anyway), the cells between them bilinear: a ninth
        // of the work. Over the budget the band keeps the model's own shadow (the seam still bends, unshaded)
        val gx = (nx - 1) / BAND_STEP + 2; val gy = (ny - 1) / BAND_STEP + 2
        val coarse = FloatArray(gx * gy) { -1f }
        val reach = BEND + BAND_STEP * maxOf(sx, sy)
        for (j in 0 until gy) for (i in 0 until gx) {
            val c = minOf(i * BAND_STEP, nx - 1); val r = minOf(j * BAND_STEP, ny - 1)
            val x = gx0 + c * sx; val z = gy0 + r * sy
            if (inward(x, z) >= reach) continue
            coarse[j * gx + i] = march(x, z)
            if (System.nanoTime() > deadline) { bandOff = true; return raw }
        }
        val out = raw.copyOf()
        for (r in 0 until ny) for (c in 0 until nx) {
            val x = gx0 + c * sx; val z = gy0 + r * sy
            val din = inward(x, z)
            if (din >= BEND) continue
            val k = r * nx + c
            val fi = c.toFloat() / BAND_STEP; val fj = r.toFloat() / BAND_STEP
            val i0 = minOf(fi.toInt(), gx - 2); val j0 = minOf(fj.toInt(), gy - 2)
            val ax = (fi - i0).coerceIn(0f, 1f); val az = (fj - j0).coerceIn(0f, 1f)
            fun at(i: Int, j: Int) = coarse[j * gx + i].let { if (it < 0f) raw[k] else it }
            val s = (at(i0, j0) * (1 - ax) + at(i0 + 1, j0) * ax) * (1 - az) + (at(i0, j0 + 1) * (1 - ax) + at(i0 + 1, j0 + 1) * ax) * az
            val w = ((BEND - din) / BAND_HANDOVER).coerceIn(0f, 1f)
            out[k] = raw[k] * (1 - w) + s * w
        }
        bandOff = false
        return out
    }

    /** The last [modelBand] ran over its budget, and the band kept the model's own shadow. */
    @Volatile var bandOff = false
        private set

    companion object {
        const val STRIDE = 6
        /** How far under the mesh the inner ring lies where the model is over it (metres; the model bends to it). */
        const val UNDER = 3f
        /** The model's last metres, bent to the ring's heights (the designer's step 4). */
        const val BEND = 1500f
        /** Inside the bend, the model's own shadow hands over to the band's over this (metres). */
        const val BAND_HANDOVER = 400f
        /** The band's shadow is marched every this many cells of the model, the cells between bilinear. */
        const val BAND_STEP = 3
        /** Where the model's shader hands its maps over to the band (metres inside the edge). */
        const val SEAM_IN = 600f
        /** Every packed texture of the live model, the ring's two included (the architect, 25.11.2026: 1.2 MB with the band). */
        const val MAX_SET_BYTES = 1_200_000L
        /** Where the outer ring is under the inner one it lies this far under it (metres). */
        const val UNDER_INNER = 30f
        /** The budget (the architect, 25.11.2026, the light option): the file, the triangles, the relief map. */
        const val MAX_BYTES = 64 * 1024
        const val MAX_TRIANGLES = 48_000
        const val MAX_RELIEF_BYTES = 80 * 1024
        /** The cast shadow's time budget (ms). */
        const val SHADOW_MS = 150L

        /** The heights of a grid's [json] (metres, row by row, y south, row 0 north). */
        fun heights(json: JSONObject): ShortArray {
            val nx = json.getInt("nx"); val ny = json.getInt("ny")
            val packed = Base64.getDecoder().decode(json.getString("h"))
            val inf = Inflater(); inf.setInput(packed)
            val raw = ByteArray(nx * ny * 2); var n = 0
            while (n < raw.size && !inf.finished()) n += inf.inflate(raw, n, raw.size - n)
            inf.end()
            check(n == raw.size) { "ring: ${n / 2} heights of ${nx * ny}" }
            val h = ShortArray(nx * ny)
            for (j in 0 until ny) {
                var v = 0
                for (i in 0 until nx) {
                    val k = j * nx + i
                    val d = (raw[k * 2].toInt() and 0xff) or (raw[k * 2 + 1].toInt() shl 8)
                    v = if (i == 0) d.toShort().toInt() else v + d.toShort().toInt()
                    h[k] = v.toShort()
                }
            }
            return h
        }

        fun grid(j: JSONObject) = Grid(j.getDouble("x0").toFloat(), j.getDouble("y0").toFloat(), j.getDouble("cell").toFloat(),
            j.getInt("nx"), j.getInt("ny"), heights(j))

        /** Places and normals (central differences of [ys]) of a grid, into [v] from vertex [at]. */
        private fun place(g: Grid, ys: FloatArray, v: FloatArray, at: Int) {
            val nx = g.nx; val ny = g.ny; val cell = g.cell
            for (j in 0 until ny) for (i in 0 until nx) {
                val k = j * nx + i
                val i0 = maxOf(i - 1, 0); val i1 = minOf(i + 1, nx - 1); val j0 = maxOf(j - 1, 0); val j1 = minOf(j + 1, ny - 1)
                val sx = (ys[j * nx + i1] - ys[j * nx + i0]) / (cell * (i1 - i0)); val sz = (ys[j1 * nx + i] - ys[j0 * nx + i]) / (cell * (j1 - j0))
                val l = sqrt(sx * sx + 1f + sz * sz); val o = (at + k) * STRIDE
                v[o] = g.x0 + i * cell; v[o + 1] = ys[k]; v[o + 2] = g.y0 + j * cell
                v[o + 3] = -sx / l; v[o + 4] = 1f / l; v[o + 5] = -sz / l
            }
        }

        /**
         * The mesh of [json] round the height model's box ([x0]..[x1], [y0]..[y1]), whose height at a point is [elev]:
         * an inner vertex keeps its own height, [UNDER] lower where the model is over it (the model bends to the ring over
         * its last [BEND] metres, the designer's step 4), an inner cell whose corners are all well inside
         * it is left out; an outer vertex inside the inner ring lies [UNDER_INNER] under it, and an outer cell two inner
         * cells inside the inner ring is left out.
         */
        fun build(json: JSONObject, x0: Float, y0: Float, x1: Float, y1: Float, seam: Boolean = true, elev: (Float, Float) -> Float): LiveRing {
            check(json.getInt("schema") == 2) { "ring.json: schema ${json.getInt("schema")}" }
            val gi = grid(json.getJSONObject("inner")); val go = grid(json.getJSONObject("outer"))
            fun inModel(x: Float, y: Float, m: Float) = x > x0 + m && x < x1 - m && y > y0 + m && y < y1 - m
            // the inner ring: its own heights everywhere, a little lower under the model (which bends to them)
            val yi = FloatArray(gi.nx * gi.ny)
            for (j in 0 until gi.ny) for (i in 0 until gi.nx) {
                val x = gi.x0 + i * gi.cell; val y = gi.y0 + j * gi.cell
                yi[j * gi.nx + i] = if (seam) gi.h[j * gi.nx + i] - if (inModel(x, y, 0f)) UNDER else 0f
                // before step 4 (the emulator's "before" picture only): 25 m under the model, a 900 m blend out of it
                else if (inModel(x, y, 0f)) elev(x, y) - 25f else {
                    val cx = x.coerceIn(x0, x1); val cy = y.coerceIn(y0, y1)
                    val w = (sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / 900f).coerceIn(0f, 1f)
                    elev(cx, cy) * (1 - w) + gi.h[j * gi.nx + i] * w
                }
            }
            fun innerY(x: Float, y: Float): Float {
                val c = ((x - gi.x0) / gi.cell).coerceIn(0f, gi.nx - 1.0001f); val r = ((y - gi.y0) / gi.cell).coerceIn(0f, gi.ny - 1.0001f)
                val c0 = c.toInt(); val r0 = r.toInt(); val fc = c - c0; val fr = r - r0; val k = r0 * gi.nx + c0
                return (yi[k] * (1 - fc) + yi[k + 1] * fc) * (1 - fr) + (yi[k + gi.nx] * (1 - fc) + yi[k + gi.nx + 1] * fc) * fr
            }
            // the outer ring: under the inner one where they overlap
            val yo = FloatArray(go.nx * go.ny)
            for (j in 0 until go.ny) for (i in 0 until go.nx) {
                val x = go.x0 + i * go.cell; val y = go.y0 + j * go.cell
                yo[j * go.nx + i] = if (gi.inside(x, y)) innerY(x, y) - UNDER_INNER else go.h[j * go.nx + i].toFloat()
            }
            val ni = gi.nx * gi.ny
            val v = FloatArray((ni + go.nx * go.ny) * STRIDE)
            place(gi, yi, v, 0); place(go, yo, v, ni)
            val idx = ArrayList<Int>(MAX_TRIANGLES * 3)
            for (j in 0 until gi.ny - 1) for (i in 0 until gi.nx - 1) {
                val x = gi.x0 + i * gi.cell; val y = gi.y0 + j * gi.cell
                // all four corners a cell inside the model: hidden under it
                if (inModel(x, y, gi.cell) && inModel(x + gi.cell, y + gi.cell, gi.cell)) continue
                val a = j * gi.nx + i; val b = a + 1; val c = a + gi.nx; val d = c + 1
                // counter-clockwise seen from above (y up, z south), as the mesh's
                idx += a; idx += c; idx += b; idx += b; idx += c; idx += d
            }
            val m = 2 * gi.cell
            fun inInner(x: Float, y: Float) = x > gi.x0 + m && x < gi.x1 - m && y > gi.y0 + m && y < gi.y1 - m
            for (j in 0 until go.ny - 1) for (i in 0 until go.nx - 1) {
                val x = go.x0 + i * go.cell; val y = go.y0 + j * go.cell
                if (inInner(x, y) && inInner(x + go.cell, y + go.cell)) continue
                val a = ni + j * go.nx + i; val b = a + 1; val c = a + go.nx; val d = c + 1
                idx += a; idx += c; idx += b; idx += b; idx += c; idx += d
            }
            val rb = json.getJSONObject("relief")
            return LiveRing(v, idx.toIntArray(), (go.nx - 1) * go.cell / 2f, gi, go,
                floatArrayOf(rb.getDouble("x0").toFloat(), rb.getDouble("y0").toFloat(), rb.getDouble("x1").toFloat(), rb.getDouble("y1").toFloat()),
                floatArrayOf(x0, y0, x1, y1), elev, seam)
        }
    }
}

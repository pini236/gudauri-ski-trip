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
) {
    val triangles get() = indices.size / 3
    val points get() = vertices.size / STRIDE

    /** The relief map, decoded off the GL thread by whoever loads the ring; null draws the ring without it. */
    @Volatile var relief: LiveTex? = null

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
        x > model[0] && x < model[2] && y > model[1] && y < model[3] -> elev(x, y)
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
     * under the model have none (they are hidden). Off the drawing thread.
     */
    fun shadows(dir: FloatArray, budgetMs: Long = SHADOW_MS): FloatArray {
        val t0 = System.nanoTime()
        val out = FloatArray(points)
        if (dir[1] <= 0f) { out.fill(1f); lastShadowMs = 0; return out }
        val horiz = sqrt(dir[0] * dir[0] + dir[2] * dir[2]).coerceAtLeast(1e-4f)
        val ux = dir[0] / horiz; val uz = dir[2] / horiz; val rise = dir[1] / horiz
        fun march(k: Int, base: Float) {
            val x = vertices[k * STRIDE]; val z = vertices[k * STRIDE + 2]
            if (x > model[0] && x < model[2] && z > model[1] && z < model[3]) return
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

    companion object {
        const val STRIDE = 6
        /** How far under the mesh the inner ring's middle lies (metres). */
        const val UNDER = 25f
        /** Where the outer ring is under the inner one it lies this far under it (metres). */
        const val UNDER_INNER = 30f
        /** Outside the model the ring comes from the model's edge to its own heights over this (the designer's soft seam). */
        const val SEAM = 900f
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
         * an inner vertex inside the box lies [UNDER] under the model, an inner cell whose corners are all well inside
         * it is left out; an outer vertex inside the inner ring lies [UNDER_INNER] under it, and an outer cell two inner
         * cells inside the inner ring is left out.
         */
        fun build(json: JSONObject, x0: Float, y0: Float, x1: Float, y1: Float, elev: (Float, Float) -> Float): LiveRing {
            check(json.getInt("schema") == 2) { "ring.json: schema ${json.getInt("schema")}" }
            val gi = grid(json.getJSONObject("inner")); val go = grid(json.getJSONObject("outer"))
            fun inModel(x: Float, y: Float, m: Float) = x > x0 + m && x < x1 - m && y > y0 + m && y < y1 - m
            // the inner ring: under the model, a soft seam out of it, then its own heights
            val yi = FloatArray(gi.nx * gi.ny)
            for (j in 0 until gi.ny) for (i in 0 until gi.nx) {
                val x = gi.x0 + i * gi.cell; val y = gi.y0 + j * gi.cell
                yi[j * gi.nx + i] = if (inModel(x, y, 0f)) elev(x, y) - UNDER else {
                    val cx = x.coerceIn(x0, x1); val cy = y.coerceIn(y0, y1)
                    val w = (sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / SEAM).coerceIn(0f, 1f)
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
                floatArrayOf(x0, y0, x1, y1), elev)
        }
    }
}

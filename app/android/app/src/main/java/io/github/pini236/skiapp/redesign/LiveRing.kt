package io.github.pini236.skiapp.redesign

import java.util.Base64
import java.util.zip.Inflater
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * The ring of far ground round the live mountain (the architect's option a, docs/ARCHITECTURE.md, 25.11.2026): real
 * heights from the same source and projection as the height model, 700 m cells, about 84 km square
 * (assets/redesign/live/ring.json, built by hand with tools/build-ring.py).
 *
 * Only a view: no shadow, no tap, no labels, no "where am I". Under the height model the ring is lowered under the mesh
 * (taken from the mesh's own heights there), so the two never fight and there is no seam at the model's edge.
 */
class LiveRing private constructor(
    /** x, y, z, then the normal's x, y, z, for every vertex. */
    val vertices: FloatArray,
    val indices: IntArray,
) {
    val triangles get() = indices.size / 3

    companion object {
        const val STRIDE = 6
        /** How far under the mesh the ring's middle lies (metres). */
        const val UNDER = 25f
        /** The budget (the architect): the file and the triangles. */
        const val MAX_BYTES = 64 * 1024
        const val MAX_TRIANGLES = 40_000

        /** The heights of [json] (metres, row by row, y south). */
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

        /**
         * The mesh of [json] round the height model's box ([x0]..[x1], [y0]..[y1]), whose height at a point is [elev]:
         * a vertex inside the box lies [UNDER] under the model, a cell whose corners are all well inside it is left out.
         */
        fun build(json: JSONObject, x0: Float, y0: Float, x1: Float, y1: Float, elev: (Float, Float) -> Float): LiveRing {
            val nx = json.getInt("nx"); val ny = json.getInt("ny")
            val cell = json.getDouble("cell").toFloat()
            val gx = json.getDouble("x0").toFloat(); val gy = json.getDouble("y0").toFloat()
            val h = heights(json)
            fun inside(x: Float, y: Float, m: Float) = x > x0 + m && x < x1 - m && y > y0 + m && y < y1 - m
            val ys = FloatArray(nx * ny)
            for (j in 0 until ny) for (i in 0 until nx) {
                val x = gx + i * cell; val y = gy + j * cell
                ys[j * nx + i] = if (inside(x, y, 0f)) elev(x, y) - UNDER else h[j * nx + i].toFloat()
            }
            val v = FloatArray(nx * ny * STRIDE)
            for (j in 0 until ny) for (i in 0 until nx) {
                val k = j * nx + i
                val dx = ys[j * nx + minOf(i + 1, nx - 1)] - ys[j * nx + maxOf(i - 1, 0)]
                val dz = ys[minOf(j + 1, ny - 1) * nx + i] - ys[maxOf(j - 1, 0) * nx + i]
                val sx = dx / (cell * (minOf(i + 1, nx - 1) - maxOf(i - 1, 0))); val sz = dz / (cell * (minOf(j + 1, ny - 1) - maxOf(j - 1, 0)))
                val l = sqrt(sx * sx + 1f + sz * sz)
                v[k * 6] = gx + i * cell; v[k * 6 + 1] = ys[k]; v[k * 6 + 2] = gy + j * cell
                v[k * 6 + 3] = -sx / l; v[k * 6 + 4] = 1f / l; v[k * 6 + 5] = -sz / l
            }
            val idx = ArrayList<Int>((nx - 1) * (ny - 1) * 6)
            for (j in 0 until ny - 1) for (i in 0 until nx - 1) {
                val x = gx + i * cell; val y = gy + j * cell
                // all four corners a cell inside the model: hidden under it
                if (inside(x, y, cell) && inside(x + cell, y + cell, cell)) continue
                val a = j * nx + i; val b = a + 1; val c = a + nx; val d = c + 1
                // counter-clockwise seen from above (y up, z south), as the mesh's
                idx += a; idx += c; idx += b; idx += b; idx += c; idx += d
            }
            return LiveRing(v, idx.toIntArray())
        }
    }
}

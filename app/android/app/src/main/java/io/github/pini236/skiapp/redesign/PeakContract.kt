package io.github.pini236.skiapp.redesign

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.Inflater
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The painted mountain's projection contract (site/data/peak.json, schema 1; the architect's condition 1 of
 * 25.11.2026, docs/ARCHITECTURE.md), the same in every client and checked against tools/fixtures/peak-projection.json.
 * Pure: no Android classes, so the unit tests run it as it is. Points are metres of the site's projection (x east,
 * y south, around terrain.json's proj) and the height model's metres.
 */
class PeakContract(val radius: Float, val views: List<View>) {

    fun view(id: String) = views.firstOrNull { it.id == id }

    class Image(val light: String, val file: String, val alt: Float, val az: Float, val night: Boolean, val bytes: Int, val sha256: String)
    class Run(val key: String, val named: Boolean, val l: List<FloatArray>)
    class Lift(val name: String, val wire: List<FloatArray>, val ground: List<FloatArray>)
    class Screen(val x: Float, val y: Float, val d: Float)

    class View(
        val id: String, val packaged: Boolean, val rev: String?, val path: String?,
        az: Double, val s: Double, val cx: Double, val cn: Double, val near: Double,
        val w: Int, val h: Int, val yc: Double, val zref: Double, val px: Double, val step: Double,
        val f: DoubleArray, val c: DoubleArray,
        val cell: Int, val tol: Float, val dnx: Int, val dny: Int, val depth: IntArray,
        val images: List<Image>, val runs: List<Run>, val lifts: List<Lift>,
    ) {
        private val fe = sin(Math.toRadians(az)); private val fn = cos(Math.toRadians(az))
        private val re = cos(Math.toRadians(az)); private val rn = -sin(Math.toRadians(az))

        private fun table(t: DoubleArray, d: Double): Double {
            val i = (d / step).coerceIn(0.0, (t.size - 1).toDouble()); val i0 = floor(i).toInt().coerceAtMost(t.size - 2)
            val k = i - i0; return t[i0] * (1 - k) + t[i0 + 1] * k
        }

        /** where the picture draws ground at site x, y (metres east, south) and height z */
        fun project(x: Double, y: Double, z: Double): Screen {
            val e = x - cx; val n = -y - cn
            val u = e * re + n * rn; val d = e * fe + n * fn + near
            return Screen((w / 2.0 + s * u).toFloat(), (yc - s * (table(f, d) + (z - zref) * table(c, d))).toFloat(), d.toFloat())
        }

        /** whether a projected point is seen: inside the picture and not behind a ridge (the depth grid, nearest cell) */
        fun visible(p: Screen): Boolean {
            if (p.x < 0 || p.x > w || p.y < 0 || p.y > h) return false
            val j = (p.x / cell).toInt().coerceIn(0, dnx - 1); val i = (p.y / cell).toInt().coerceIn(0, dny - 1)
            return p.d <= depth[i * dnx + j] + tol
        }

        /** the named run whose drawn line is nearest to the point, within [radius] points; null if none */
        fun tap(x: Float, y: Float, radius: Float): String? {
            var best: String? = null; var bd = radius
            for (r in runs) {
                if (!r.named) continue
                for (l in r.l) { val dd = dist(x, y, l); if (dd < bd) { best = r.key; bd = dd } }
            }
            return best
        }
    }

    companion object {
        /** the distance from a point to a polyline [x0, y0, x1, y1, ...] */
        fun dist(px: Float, py: Float, l: FloatArray): Float {
            var m = Float.MAX_VALUE
            var i = 0
            while (i + 3 < l.size) {
                val ax = l[i]; val ay = l[i + 1]; val vx = l[i + 2] - ax; val vy = l[i + 3] - ay
                val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy).coerceAtLeast(1e-9f)).coerceIn(0f, 1f)
                m = minOf(m, hypot(ax + t * vx - px, ay + t * vy - py)); i += 2
            }
            return m
        }

        private fun floats(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
        private fun doubles(a: JSONArray) = DoubleArray(a.length()) { a.getDouble(it) }
        private fun lines(a: JSONArray) = (0 until a.length()).map { floats(a.getJSONArray(it)) }

        private fun inflate(b: ByteArray, n: Int): ByteArray {
            val out = ByteArray(n); val inf = Inflater(); inf.setInput(b)
            var k = 0; while (k < n && !inf.finished()) k += inf.inflate(out, k, n - k)
            inf.end(); check(k == n) { "peak.json: depth is $k bytes, not $n" }; return out
        }

        fun parse(json: String): PeakContract {
            val o = JSONObject(json)
            check(o.getInt("schema") == 1) { "peak.json: schema ${o.getInt("schema")}" }
            val vs = o.getJSONArray("views")
            return PeakContract(o.getDouble("radius").toFloat(), (0 until vs.length()).map { k ->
                val v = vs.getJSONObject(k); val dp = v.getJSONObject("depth")
                val nx = dp.getInt("nx"); val ny = dp.getInt("ny")
                val raw = inflate(Base64.getDecoder().decode(dp.getString("b64")), nx * ny * 2)
                val sh = ShortArray(nx * ny).also { ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
                val im = v.getJSONArray("images"); val rs = v.getJSONArray("runs"); val ls = v.getJSONArray("lifts")
                val c = v.getJSONArray("c")
                View(
                    v.getString("id"), v.getBoolean("packaged"), v.optString("rev").ifEmpty { null }, v.optString("path").ifEmpty { null },
                    v.getDouble("az"), v.getDouble("S"), c.getDouble(0), c.getDouble(1), v.getDouble("near"),
                    v.getInt("W"), v.getInt("H"), v.getDouble("y_c"), v.getDouble("zref"), v.getDouble("px"), v.getDouble("step"),
                    doubles(v.getJSONArray("F")), doubles(v.getJSONArray("C")),
                    dp.getInt("cell"), dp.getDouble("tol").toFloat(), nx, ny, IntArray(nx * ny) { sh[it].toInt() and 0xffff },
                    (0 until im.length()).map { im.getJSONObject(it).run {
                        Image(getString("light"), getString("file"), getDouble("alt").toFloat(), getDouble("az").toFloat(),
                            getBoolean("night"), getInt("bytes"), getString("sha256"))
                    } },
                    (0 until rs.length()).map { rs.getJSONObject(it).run { Run(getString("key"), getBoolean("named"), lines(getJSONArray("l"))) } },
                    (0 until ls.length()).map { ls.getJSONObject(it).run { Lift(getString("name"), lines(getJSONArray("wire")), lines(getJSONArray("ground"))) } },
                )
            })
        }
    }
}

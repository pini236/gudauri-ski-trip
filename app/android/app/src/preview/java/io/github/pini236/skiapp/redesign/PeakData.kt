package io.github.pini236.skiapp.redesign

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import org.json.JSONArray
import org.json.JSONObject

/**
 * The live layer of the redesign POC (decisions 83, 84): assets/redesign/peak.json, written by
 * design/redesign/tools/peak_android.py. Every run and lift is already projected with peak.py's formula onto the
 * 390 x 844 picture (the "hero" view, 12.1.2027), cut where a ridge hides it. The app only draws; it computes nothing
 * about the ground. Test build only (src/preview).
 */
class PeakData(
    val w: Float, val h: Float,
    val hours: List<Hour>, val pistes: List<Piste>, val lifts: List<Lift>,
    val peaks: List<Label>, val places: List<Label>,
    /** the site's slope classes: up to [first] degrees, this colour */
    val slope: List<Pair<Float, Long>>,
) {
    class Hour(val key: String, val h: Float, val night: Boolean, val dx: Float, val dy: Float, val file: String)
    class Label(val name: String, val ele: Int, val x: Float, val y: Float)

    /** The chosen-run line: top to bottom, per point the screen place, if it is seen, metres along, slope, height. */
    class Line(val x: FloatArray, val y: FloatArray, val vis: BooleanArray, val m: FloatArray, val deg: FloatArray, val z: FloatArray) {
        val n get() = x.size
        val length get() = m.last()
        /** the index of the last point at or before [metres] */
        fun index(metres: Float): Int { var i = 0; while (i < n - 1 && m[i + 1] <= metres) i++; return i }
        fun at(metres: Float): Offset {
            val i = index(metres).coerceAtMost(n - 2); val t = ((metres - m[i]) / (m[i + 1] - m[i]).coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
            return Offset(x[i] + (x[i + 1] - x[i]) * t, y[i] + (y[i + 1] - y[i]) * t)
        }
        /** the direction of travel (down the run) at [metres], unit length */
        fun dir(metres: Float): Offset {
            val i = index(metres).coerceIn(1, n - 2); val dx = x[i + 1] - x[i - 1]; val dy = y[i + 1] - y[i - 1]
            val l = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-3f); return Offset(dx / l, dy / l)
        }
        /** the box of the part that is seen */
        fun bounds(): Rect {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (i in 0 until n) if (vis[i]) { l = minOf(l, x[i]); r = maxOf(r, x[i]); t = minOf(t, y[i]); b = maxOf(b, y[i]) }
            return if (l > r) Rect(x.min(), y.min(), x.max(), y.max()) else Rect(l, t, r, b)
        }
    }

    class Piste(
        val key: String, val name: String, val color: String, val kind: String, val named: Boolean, val lit: Boolean, val ref: String,
        /** the seen pieces, each a polyline */
        val chunks: List<FloatArray>,
        val len: Int, val drop: Int, val steep: Int, val from: List<String>, val to: List<String>, val line: Line?,
    ) {
        val path: Path by lazy { Path().apply { chunks.forEach { c -> moveTo(c[0], c[1]); var i = 2; while (i < c.size) { lineTo(c[i], c[i + 1]); i += 2 } } } }
        val dashed get() = !named || kind == "ski-way"
        val choosable get() = named && line != null && kind != "ski-way"
    }

    class Lift(val name: String, val x: FloatArray, val y: FloatArray, val gx: FloatArray, val gy: FloatArray, val vis: BooleanArray,
               val ends: List<Triple<Float, Float, Boolean>>, val gondola: Boolean) {
        /** the cable 11 m above the ground, and its shadow on the snow, as seen pieces */
        val wire: Path by lazy { pieces(x, y) }
        val ground: Path by lazy { pieces(gx, gy) }
        private fun pieces(xs: FloatArray, ys: FloatArray) = Path().apply {
            var open = false
            for (i in xs.indices) {
                if (vis[i]) { if (open) lineTo(xs[i], ys[i]) else moveTo(xs[i], ys[i]); open = true } else open = false
            }
        }
    }

    companion object {
        fun load(context: Context): PeakData {
            val o = JSONObject(context.assets.open("redesign/peak.json").bufferedReader().use { it.readText() })
            fun fa(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
            fun ba(a: JSONArray) = BooleanArray(a.length()) { a.getInt(it) != 0 }
            fun sl(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getString(it) }
            fun <T> list(a: JSONArray, f: (JSONObject) -> T) = List(a.length()) { f(a.getJSONObject(it)) }
            val hours = list(o.getJSONArray("hours")) { Hour(it.getString("key"), it.getDouble("h").toFloat(), it.getBoolean("night"),
                it.getDouble("dx").toFloat(), it.getDouble("dy").toFloat(), it.getString("file")) }
            val pistes = list(o.getJSONArray("pistes")) { p ->
                val chunks = p.getJSONArray("chunks").let { a -> List(a.length()) { fa(a.getJSONArray(it)) } }.filter { it.size >= 4 }
                val line = p.optJSONObject("path")?.let { q -> Line(fa(q.getJSONArray("x")), fa(q.getJSONArray("y")), ba(q.getJSONArray("v")),
                    fa(q.getJSONArray("m")), fa(q.getJSONArray("d")), fa(q.getJSONArray("z"))) }?.takeIf { it.n >= 3 }
                Piste(p.getString("key"), p.getString("name"), p.getString("color"), p.getString("kind"), p.getBoolean("named"), p.getBoolean("lit"),
                    p.optString("ref"), chunks, p.optInt("len"), p.optInt("drop"), p.optInt("steep"), sl(p.optJSONArray("frm")), sl(p.optJSONArray("to")), line)
            }
            val lifts = list(o.getJSONArray("lifts")) { l ->
                val ends = l.getJSONArray("ends").let { a -> List(a.length()) { a.getJSONArray(it).let { e -> Triple(e.getDouble(0).toFloat(), e.getDouble(1).toFloat(), e.getInt(2) != 0) } } }
                Lift(l.getString("name"), fa(l.getJSONArray("x")), fa(l.getJSONArray("y")), fa(l.getJSONArray("gx")), fa(l.getJSONArray("gy")), ba(l.getJSONArray("v")), ends, l.getBoolean("gondola"))
            }
            fun labels(a: JSONArray) = list(a) { Label(it.getString("name"), it.optInt("ele"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) }
            val slope = o.getJSONArray("slope").let { a -> List(a.length()) { a.getJSONArray(it).let { s -> s.getDouble(0).toFloat() to (0xFF000000 or s.getString(1).removePrefix("#").toLong(16)) } } }
            return PeakData(o.getDouble("w").toFloat(), o.getDouble("h").toFloat(), hours, pistes, lifts, labels(o.getJSONArray("peaks")), labels(o.getJSONArray("places")), slope)
        }
    }

    fun slopeColor(deg: Float): Long = slope.first { deg < it.first }.second
}

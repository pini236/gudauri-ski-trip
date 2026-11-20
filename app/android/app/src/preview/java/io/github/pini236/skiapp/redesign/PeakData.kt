package io.github.pini236.skiapp.redesign

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import org.json.JSONArray
import org.json.JSONObject

/**
 * The redesign POC's data (decision 85): assets/redesign/peak-poc.json, the product designer's handoff
 * (design/handoff/2026-11-20-redesign-poc, schema 0). Everything is already projected onto the pictures, in their
 * points (390 wide); the app projects nothing. Two views: "hero" (the whole mountain, 390 x 844) and "zoom" (the
 * Tatra side, 390 x 560). Test build only (src/preview).
 */
class PeakData(
    val lights: List<Light>,
    /** the site's slope classes: their colours, by class 0..3 */
    val slope: List<Color>,
    val pal: Map<String, Pal>,
    val hero: View, val zoom: View,
    val runs: Map<String, Run>,
) {
    class Light(val name: String, val hour: Float, val alt: Float, val az: Float, val night: Boolean)
    class Pal(val green: Color, val blue: Color, val red: Color, val black: Color, val case: Color, val wire: Color, val st: Color, val sh: Color) {
        fun run(c: String) = when (c) { "green" -> green; "blue" -> blue; "red" -> red; else -> black }
    }
    class Run(val name: String, val color: String, val ref: String, val len: Int, val drop: Int, val steep: Int, val near: Boolean)

    /** One drawing step of the small real things (rivers, roads, buildings, the village lights): `k` l, p, c or sh. */
    class Op(val k: Char, val p: FloatArray?, val fill: Color?, val stroke: Color?, val w: Float, val o: Float, val i: Int, val x: Float, val y: Float, val r: Float) {
        val path: Path? by lazy { p?.let { poly(it, k == 'p') } }
    }

    class Piste(val key: String, val name: String, val color: String, val named: Boolean, val kind: String, val lit: Boolean, val l: List<FloatArray>) {
        val path by lazy { Path().apply { l.forEach { addPoly(this, it, false) } } }
        val dashed get() = !named || kind == "ski-way"
    }

    class Lift(val name: String, val gondola: Boolean, val wire: List<FloatArray>, val ground: List<FloatArray>,
               val towers: List<FloatArray>, val cabins: List<FloatArray>, val stations: List<FloatArray>) {
        val wirePath by lazy { Path().apply { wire.forEach { addPoly(this, it, false) } } }
        val groundPath by lazy { Path().apply { ground.forEach { addPoly(this, it, false) } } }
    }

    /** One piece of a chosen run, top to bottom, a point every 10 m: place, seen, metres from its start, slope class. */
    class Seg(val x: FloatArray, val y: FloatArray, val vis: BooleanArray, val m: FloatArray, val cls: IntArray) {
        val n get() = x.size
        val length get() = m.last()
        fun index(metres: Float): Int { var i = 0; while (i < n - 1 && m[i + 1] <= metres) i++; return i }
        fun at(metres: Float): Offset {
            val i = index(metres).coerceAtMost(n - 2); val t = ((metres - m[i]) / (m[i + 1] - m[i]).coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
            return Offset(x[i] + (x[i + 1] - x[i]) * t, y[i] + (y[i + 1] - y[i]) * t)
        }
        /** the direction down the run at [metres], unit length */
        fun dir(metres: Float): Offset {
            val i = index(metres).coerceIn(1, (n - 2).coerceAtLeast(1)); val j = (i + 1).coerceAtMost(n - 1); val h = (i - 1).coerceAtLeast(0)
            val dx = x[j] - x[h]; val dy = y[j] - y[h]; val l = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-3f); return Offset(dx / l, dy / l)
        }
        /** the seen part up to [to] metres, as one path */
        fun path(to: Float = Float.MAX_VALUE): Path {
            val p = Path(); var open = false
            for (i in 0 until n) {
                if (m[i] > to) { if (open && vis[i]) { val q = at(to); p.lineTo(q.x, q.y) }; break }
                if (vis[i]) { if (open) p.lineTo(x[i], y[i]) else p.moveTo(x[i], y[i]); open = true } else open = false
            }
            return p
        }
        val full by lazy { path() }
    }

    class Sel(val segs: List<Seg>, val main: Int) {
        val mainSeg get() = segs[main]
        fun bounds(): Rect {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (s in segs) for (i in 0 until s.n) if (s.vis[i]) { l = minOf(l, s.x[i]); r = maxOf(r, s.x[i]); t = minOf(t, s.y[i]); b = maxOf(b, s.y[i]) }
            return if (l > r) Rect.Zero else Rect(l, t, r, b)
        }
    }

    class Label(val t: String, val sub: Int?, val x: Float, val y: Float)

    class View(
        val w: Float, val h: Float, val images: Map<String, String>, val s: Float, val zfRuns: Float, val zfLifts: Float,
        /** per light: the screen offset of the shadow of a point 100 m up */
        val shadow: Map<String, Offset>,
        val details: Map<String, List<Op>>, val bshadow: Map<String, List<Path>>,
        val pistes: List<Piste>, val lifts: List<Lift>, val sel: Map<String, Sel>, val labels: List<Label>,
        /** zoom only: x_zoom = x_hero · k + tx */
        val fromHero: Triple<Float, Float, Float>?,
    )

    companion object {
        private fun poly(a: FloatArray, close: Boolean) = Path().apply { addPoly(this, a, close) }
        private fun addPoly(p: Path, a: FloatArray, close: Boolean) {
            if (a.size < 4) return
            p.moveTo(a[0], a[1]); var i = 2; while (i + 1 < a.size) { p.lineTo(a[i], a[i + 1]); i += 2 }
            if (close) p.close()
        }
        private fun col(s: String?): Color? = s?.removePrefix("#")?.let { h -> Color((0xFF000000 or h.toLong(16)).toInt()) }
        private fun fa(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
        private fun lfa(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { fa(a.getJSONArray(it)) }
        private fun <T> list(a: JSONArray?, f: (JSONObject) -> T) = if (a == null) emptyList() else List(a.length()) { f(a.getJSONObject(it)) }

        private fun view(v: JSONObject): View {
            val images = v.getJSONObject("images").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            val shadow = v.optJSONObject("shadow")?.let { o -> o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { Offset(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) } } } ?: emptyMap()
            val details = v.getJSONObject("details").let { o -> o.keys().asSequence().associateWith { k ->
                list(o.getJSONArray(k)) { op ->
                    Op(op.getString("k")[0], op.optJSONArray("p")?.let(::fa), col(op.optString("f", null)), col(op.optString("s", null)),
                        op.optDouble("w", 0.0).toFloat(), op.optDouble("o", 1.0).toFloat(), op.optInt("i", -1),
                        op.optDouble("x", 0.0).toFloat(), op.optDouble("y", 0.0).toFloat(), op.optDouble("r", 0.0).toFloat())
                } } }
            val bshadow = v.optJSONObject("bshadow")?.let { o -> o.keys().asSequence().associateWith { k -> lfa(o.getJSONArray(k)).map { poly(it, true) } } } ?: emptyMap()
            val pistes = list(v.getJSONArray("pistes")) { p ->
                Piste(p.getString("key"), p.optString("name", p.getString("key")), p.getString("color"), p.getBoolean("named"), p.optString("kind", "run"), p.optBoolean("lit"), lfa(p.getJSONArray("l")))
            }
            val lifts = list(v.getJSONArray("lifts")) { l ->
                Lift(l.getString("name"), l.optBoolean("gondola"), lfa(l.optJSONArray("wire")), lfa(l.optJSONArray("ground")), lfa(l.optJSONArray("towers")),
                    lfa(l.optJSONArray("cabins")), lfa(l.optJSONArray("stations")))
            }
            val sel = v.getJSONObject("sel").let { o -> o.keys().asSequence().associateWith { k ->
                val e = o.getJSONObject(k)
                val segs = list(e.getJSONArray("segs")) { sg ->
                    val n = sg.getInt("n"); val p = sg.getJSONArray("p")
                    Seg(FloatArray(n) { p.getDouble(it * 5).toFloat() }, FloatArray(n) { p.getDouble(it * 5 + 1).toFloat() },
                        BooleanArray(n) { p.getInt(it * 5 + 2) != 0 }, FloatArray(n) { p.getDouble(it * 5 + 3).toFloat() }, IntArray(n) { p.getInt(it * 5 + 4) })
                }.filter { it.n >= 2 }
                Sel(segs, e.optInt("main").coerceIn(0, (segs.size - 1).coerceAtLeast(0)))
            }.filterValues { it.segs.isNotEmpty() } }
            val labels = list(v.optJSONArray("labels")) { Label(it.getString("t"), if (it.has("sub")) it.getInt("sub") else null, it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) }
            val fh = v.optJSONObject("from_hero")?.let { Triple(it.getDouble("k").toFloat(), it.getDouble("tx").toFloat(), it.getDouble("ty").toFloat()) }
            return View(v.getDouble("W").toFloat(), v.getDouble("H").toFloat(), images, v.getJSONObject("proj").getDouble("S").toFloat(),
                v.optDouble("zf_runs", 1.0).toFloat(), v.optDouble("zf_lifts", 1.0).toFloat(), shadow, details, bshadow, pistes, lifts, sel, labels, fh)
        }

        fun load(context: Context): PeakData {
            val o = JSONObject(context.assets.open("redesign/peak-poc.json").bufferedReader().use { it.readText() })
            val lights = list(o.getJSONArray("lights")) { Light(it.getString("name"), it.getDouble("hour").toFloat(), it.optDouble("alt", -90.0).toFloat(), it.optDouble("az", 0.0).toFloat(), it.getBoolean("night")) }
            val slope = o.getJSONArray("slope").let { a -> List(a.length()) { col(a.getJSONArray(it).getString(1))!! } }
            val pal = o.getJSONObject("pal").let { p -> p.keys().asSequence().associateWith { k -> p.getJSONObject(k).let {
                Pal(col(it.getString("green"))!!, col(it.getString("blue"))!!, col(it.getString("red"))!!, col(it.getString("black"))!!,
                    col(it.getString("case"))!!, col(it.getString("wire"))!!, col(it.getString("st"))!!, col(it.optString("sh", "#1B2E55"))!!)
            } } }
            val views = o.getJSONObject("views")
            val runs = o.getJSONObject("runs").let { r -> r.keys().asSequence().associateWith { k -> r.getJSONObject(k).let {
                Run(it.optString("name", k), it.getString("color"), it.optString("ref"), it.optInt("len"), it.optInt("drop"), it.optInt("steep"), it.optBoolean("near"))
            } } }
            return PeakData(lights, slope, pal, view(views.getJSONObject("hero")), view(views.getJSONObject("zoom")), runs)
        }
    }
}

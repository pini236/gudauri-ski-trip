package io.github.pini236.skiapp.dayrec.analysis

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The resort network for the day analysis: projection, terrain model, runs and lifts. A port of tools/dayrec/net.py,
 * line by line (it reads the same site/data files), in double precision like the reference.
 */
const val KM = 111320.0

/** Local metres, exactly as site/js/app.js: x east, y south. */
class Proj(val lat0: Double, val lon0: Double) {
    private val kx = KM * cos(lat0 * Math.PI / 180)
    fun x(lon: Double) = (lon - lon0) * kx
    fun y(lat: Double) = -(lat - lat0) * KM
    fun lat(y: Double) = lat0 - y / KM
    fun lon(x: Double) = lon0 + x / kx
}

/** The terrain model of site/data/terrain.json, bilinear like GudRelief.load (site/js/relief.js). */
class Dem(t: JSONObject) {
    private val nx: Int; private val ny: Int
    private val x0: Double; private val y0: Double; private val sx: Double; private val sy: Double
    private val h: ShortArray

    init {
        val d = t.getJSONObject("dem")
        nx = d.getInt("nx"); ny = d.getInt("ny")
        x0 = d.getDouble("x0"); y0 = d.getDouble("y0")
        sx = (d.getDouble("x1") - x0) / (nx - 1); sy = (d.getDouble("y1") - y0) / (ny - 1)
        val raw = Base64.getDecoder().decode(d.getString("b64"))
        h = ShortArray(raw.size / 2) { ((raw[it * 2].toInt() and 0xff) or (raw[it * 2 + 1].toInt() shl 8)).toShort() }
    }

    fun elev(x: Double, y: Double): Double {
        var c = (x - x0) / sx; var r = (y - y0) / sy
        c = max(0.0, min(nx - 1.0001, c)); r = max(0.0, min(ny - 1.0001, r))
        val c0 = c.toInt(); val r0 = r.toInt(); val fc = c - c0; val fr = r - r0
        val i = r0 * nx + c0
        return (h[i] * (1 - fc) + h[i + 1] * fc) * (1 - fr) + (h[i + nx] * (1 - fc) + h[i + nx + 1] * fc) * fr
    }
}

/** The nearest point of a line to a point: distance, position along the line, and the point. */
class Near(val d: Double, val s: Double, val x: Double, val y: Double)

/** A polyline in local metres, with distance and position along it. */
class Line(pts: List<DoubleArray>) {
    val p: List<DoubleArray> = pts
    val cum = DoubleArray(pts.size)
    val length: Double
    val box: DoubleArray

    init {
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
        length = cum[pts.size - 1]
        box = doubleArrayOf(pts.minOf { it[0] }, pts.minOf { it[1] }, pts.maxOf { it[0] }, pts.maxOf { it[1] })
    }

    fun nearest(x: Double, y: Double): Near {
        var bd = 1e18; var bs = 0.0; var bx = p[0][0]; var by = p[0][1]
        for (i in 0 until p.size - 1) {
            val ax = p[i][0]; val ay = p[i][1]; val dx = p[i + 1][0] - ax; val dy = p[i + 1][1] - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 == 0.0) 0.0 else max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / l2))
            val qx = ax + t * dx; val qy = ay + t * dy
            val d = hypot(x - qx, y - qy)
            if (d < bd) { bd = d; bs = cum[i] + t * sqrt(l2); bx = qx; by = qy }
        }
        if (p.size == 1) return Near(hypot(x - p[0][0], y - p[0][1]), 0.0, p[0][0], p[0][1])
        return Near(bd, bs, bx, by)
    }

    /** Point and unit direction at position s along the line: (x, y, ux, uy). */
    fun at(s0: Double): DoubleArray {
        val s = max(0.0, min(length, s0))
        for (i in 0 until p.size - 1) {
            if (cum[i + 1] >= s || i == p.size - 2) {
                val seg = cum[i + 1] - cum[i]
                val t = if (seg == 0.0) 0.0 else (s - cum[i]) / seg
                val ax = p[i][0]; val ay = p[i][1]; val ux = p[i + 1][0] - ax; val uy = p[i + 1][1] - ay
                val n = hypot(ux, uy).let { if (it == 0.0) 1.0 else it }
                return doubleArrayOf(ax + t * ux, ay + t * uy, ux / n, uy / n)
            }
        }
        return doubleArrayOf(p[0][0], p[0][1], 1.0, 0.0)
    }

    fun reversed() = Line(p.reversed())
}

fun pointInPolygon(x: Double, y: Double, poly: List<DoubleArray>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i][0]; val yi = poly[i][1]; val xj = poly[j][0]; val yj = poly[j][1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** Buckets of line indexes by cell, for the candidates near a point. */
class Grid(lines: List<Line>, private val cell: Double = 200.0) {
    private val b = HashMap<Long, ArrayList<Int>>()
    private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)

    init {
        lines.forEachIndexed { i, ln ->
            for (cx in floor(ln.box[0] / cell).toInt()..floor(ln.box[2] / cell).toInt())
                for (cy in floor(ln.box[1] / cell).toInt()..floor(ln.box[3] / cell).toInt())
                    b.getOrPut(key(cx, cy)) { ArrayList() }.add(i)
        }
    }

    /** Sorted line indexes in the cells that touch the square of radius r around the point. */
    fun near(x: Double, y: Double, r: Double): List<Int> {
        val out = java.util.TreeSet<Int>()
        for (cx in floor((x - r) / cell).toInt()..floor((x + r) / cell).toInt())
            for (cy in floor((y - r) / cell).toInt()..floor((y + r) / cell).toInt())
                b[key(cx, cy)]?.let { out.addAll(it) }
        return out.toList()
    }
}

/** One line (or area) of a run: a segment of site/data/runs-and-lifts.json. */
class Piste(val idx: Int, val key: String, val named: Boolean, val seg: String, val line: Line, val area: Boolean) {
    /** The sampled labels along the line (see [Net.labels]). */
    var samples: List<Pair<DoubleArray, List<String>>> = emptyList()

    fun dist(x: Double, y: Double): Double =
        if (area && pointInPolygon(x, y, line.p)) 0.0 else line.nearest(x, y).d
}

class Station(val id: String, val u: Double, val ele: Double, val name: String?)

/** A lift oriented from its bottom to its top, with its stations along it. */
class Lift(val idx: Int, raw: JSONObject, proj: Proj, dem: Dem, params: Params) {
    val id: String = raw.get("id").toString()
    val name: String? = raw.optString("name").takeIf { raw.has("name") && !raw.isNull("name") }
    val kind: String? = raw.optString("kind").takeIf { raw.has("kind") && !raw.isNull("kind") }
    val line: Line
    val length: Double get() = line.length
    val speed: Double
    val stations = ArrayList<Station>()

    init {
        val g = raw.getJSONArray("g")
        var ln = Line((0 until g.length()).map { val q = g.getJSONArray(it); doubleArrayOf(proj.x(q.getDouble(1)), proj.y(q.getDouble(0))) })
        var e0 = dem.elev(ln.p.first()[0], ln.p.first()[1]); var e1 = dem.elev(ln.p.last()[0], ln.p.last()[1])
        if (e1 < e0) { ln = ln.reversed(); val t = e0; e0 = e1; e1 = t }
        line = ln
        val dur = if (raw.has("dur") && !raw.isNull("dur")) raw.getDouble("dur") else 0.0
        // the cable speed from the sources (lifts[].speed, tools/dayrec/net.py), else len / dur, else a default by kind
        val cable = if (raw.has("speed") && !raw.isNull("speed")) raw.getDouble("speed") else 0.0
        speed = if (cable != 0.0) cable else if (dur != 0.0) {
            val len = if (raw.has("len") && !raw.isNull("len") && raw.getDouble("len") != 0.0) raw.getDouble("len") else ln.length
            len / (dur * 60.0)
        } else params.defaultLiftSpeed(kind)
        // stations: bottom, mid (only from data, lifts[].mid), top. u is the position along the cable.
        stations += Station("bottom", 0.0, e0, null)
        val mids = raw.optJSONArray("mid")
        if (mids != null) for (i in 0 until mids.length()) {
            val m = mids.getJSONObject(i); val pos = m.getJSONArray("pos")
            val mx = proj.x(pos.getDouble(1)); val my = proj.y(pos.getDouble(0))
            val n = ln.nearest(mx, my)
            if (n.d > params.d("lift", "mid_max_off_m")) throw IllegalArgumentException("lift $id: mid station ${m.opt("osm")} is ${n.d.toInt()} m from the cable")
            val ele = if (m.has("ele") && !m.isNull("ele")) m.getDouble("ele") else dem.elev(mx, my)
            stations += Station("mid${i + 1}", n.s, ele, m.optString("name").takeIf { m.has("name") && !m.isNull("name") })
        }
        stations += Station("top", ln.length, e1, null)
        stations.sortBy { it.u }
    }

    fun eleAt(u: Double): Double {
        for (k in 0 until stations.size - 1) {
            val a = stations[k]; val b = stations[k + 1]
            if (u <= b.u || b === stations.last()) {
                val f = if (b.u == a.u) 0.0 else max(0.0, min(1.0, (u - a.u) / (b.u - a.u)))
                return a.ele + f * (b.ele - a.ele)
            }
        }
        return stations.last().ele
    }
}

/** The network of an resort: built once from the two data files and the parameters. */
class Net(runsAndLifts: JSONObject, terrainJson: JSONObject, val params: Params, patch: JSONObject? = null) {
    val proj: Proj
    val pistes = ArrayList<Piste>()
    val lifts = ArrayList<Lift>()
    private val grid: Grid
    private val roads = ArrayList<Line>()
    private val roadGrid: Grid

    init {
        val rl = JSONObject(runsAndLifts.toString()) // a copy, so a test patch never leaks
        patch?.optJSONObject("lifts")?.let { pl ->
            val ls = rl.getJSONArray("lifts")
            for (id in pl.keys()) for (k in 0 until ls.length()) {
                val l = ls.getJSONObject(k)
                if (l.get("id").toString() == id) { val extra = pl.getJSONObject(id); for (key in extra.keys()) l.put(key, extra.get(key)) }
            }
        }
        val pj = terrainJson.getJSONObject("proj")
        proj = Proj(pj.getDouble("lat0"), pj.getDouble("lon0"))
        val dem = Dem(terrainJson)
        val runs = rl.getJSONArray("pistes")
        for (r in 0 until runs.length()) {
            val run = runs.getJSONObject(r)
            val named = run.opt("named").let { it == true || it == "True" || it == "true" }
            val segs = run.getJSONArray("segs")
            for (s in 0 until segs.length()) {
                val seg = segs.getJSONObject(s); val g = seg.getJSONArray("g")
                val line = Line((0 until g.length()).map { val q = g.getJSONArray(it); doubleArrayOf(proj.x(q.getDouble(1)), proj.y(q.getDouble(0))) })
                pistes += Piste(pistes.size, run.getString("key"), named, seg.get("id").toString(), line, seg.optBoolean("area", false))
            }
        }
        val ls = rl.getJSONArray("lifts")
        for (k in 0 until ls.length()) {
            val l = ls.getJSONObject(k)
            val g = l.optJSONArray("g")
            if (l.optString("status") == "inactive" || g == null || g.length() < 2) continue
            val lift = Lift(lifts.size, l, proj, dem, params)
            if (lift.length < params.d("lift", "min_len_m")) continue
            lifts += lift
        }
        grid = Grid(pistes.map { it.line })
        // roads of the terrain model (local metres already, as site/js/relief.js reads them)
        val rd = terrainJson.optJSONObject("env")?.optJSONArray("roads")
        if (rd != null) for (k in 0 until rd.length()) {
            val flat = rd.getJSONArray(k).getJSONArray(1)
            val pts = (0 until flat.length() - 1 step 2).map { doubleArrayOf(flat.getDouble(it), flat.getDouble(it + 1)) }
            if (pts.size >= 2) roads += Line(pts)
        }
        roadGrid = Grid(roads)
        labels()
    }

    /** [(piste index, distance)] for the lines within r metres. */
    fun nearPistes(x: Double, y: Double, r: Double): List<Pair<Int, Double>> {
        val out = ArrayList<Pair<Int, Double>>()
        for (i in grid.near(x, y, r)) { val d = pistes[i].dist(x, y); if (d <= r) out += i to d }
        return out
    }

    val hasRoads: Boolean get() = roads.isNotEmpty()

    fun nearestRoadDist(x: Double, y: Double, r: Double = 400.0): Double =
        roadGrid.near(x, y, r).minOfOrNull { roads[it].nearest(x, y).d } ?: 1e9

    fun nearestPisteDist(x: Double, y: Double, r: Double = 400.0): Double =
        nearPistes(x, y, r).minOfOrNull { it.second } ?: 1e9

    /** Every line, sampled along it, with the named runs that share it (README, "shared stretch"). */
    private fun labels() {
        val step = params.d("match", "sample_m"); val near = params.d("match", "shared_m"); val keep = params.d("match", "shared_min_m")
        for (q in pistes) {
            val n = max(1, (q.line.length / step).toInt())  // int(length // step): the length is never negative
            val pts = (0..n).map { k -> q.line.at(q.line.length * k / n).let { doubleArrayOf(it[0], it[1]) } }
            val sets = pts.map { (x, y) -> HashSet<String>().also { s -> for ((i, _) in nearPistes(x, y, near)) { val o = pistes[i]; if (o.named && o.key != q.key) s += o.key } } }
            val others = HashSet<String>().also { o -> sets.forEach { o += it } }
            val kept = List(sets.size) { HashSet<String>() }
            val span = q.line.length / n
            for (k in others) {
                var i = 0
                while (i < sets.size) {
                    if (k in sets[i]) {
                        var j = i
                        while (j + 1 < sets.size && k in sets[j + 1]) j++
                        if ((j - i) * span >= keep) for (t in i..j) kept[t] += k
                        i = j + 1
                    } else i++
                }
            }
            q.samples = pts.indices.map { i ->
                val names = HashSet<String>(kept[i]); if (q.named) names += q.key
                pts[i] to names.sorted()
            }
        }
    }

    /** The named runs at the point of a line nearest to (x, y). */
    fun labelAt(pisteIdx: Int, x: Double, y: Double): List<String> {
        val q = pistes[pisteIdx]
        if (q.area) return if (q.named) listOf(q.key) else emptyList()
        val s = q.line.nearest(x, y).s
        val n = q.samples.size - 1
        val k = if (n == 0) 0 else if (q.line.length != 0.0) Math.rint(s / q.line.length * n).toInt() else 0 // Python's round: half to even
        return q.samples[max(0, min(n, k))].second
    }
}

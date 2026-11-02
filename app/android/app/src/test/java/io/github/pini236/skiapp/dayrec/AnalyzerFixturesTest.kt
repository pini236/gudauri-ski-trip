package io.github.pini236.skiapp.dayrec

import io.github.pini236.skiapp.dayrec.analysis.Analyzer
import io.github.pini236.skiapp.dayrec.analysis.Net
import io.github.pini236.skiapp.dayrec.analysis.Params
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * The day analysis against the reference tool's test values (decision 76, m-8 condition 4): every case of
 * tools/fixtures/dayrec-cases.json, final and live, compared by the rules of tools/dayrec/compare.py (by event, not by
 * floating number): the same segments and legs, times within 3 s, distances and drops within 3 percent (at least a metre),
 * the maximum speed within 0.1 km/h. A difference that comes from a near tie in floating point is reported to the tool's
 * owner (README, "Running the test values in an app"); no parameter is tuned here.
 */
class AnalyzerFixturesTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val root = dir.parentFile.parentFile
    private val cases = JSONObject(File(root, "tools/fixtures/dayrec-cases.json").readText())
    private val params = Params(JSONObject(File(root, "tools/fixtures/dayrec-params.json").readText()))
    private val runs by lazy { JSONObject(File(dir, "runs-and-lifts.json").readText()) }
    private val terrain by lazy { JSONObject(File(dir, "terrain.json").readText()) }
    private val baseNet by lazy { Net(runs, terrain, params) }

    @Test fun theCasesAreOfTheVersionOfTheAnalysisThisCodeImplements() {
        assertEquals(cases.getInt("analysis"), params.analysis)
        assertEquals("tools/fixtures/dayrec-params.json", cases.getString("params"))
    }

    @Test fun everyCaseMatchesTheReference() {
        val failures = ArrayList<String>()
        val list = cases.getJSONArray("cases")
        for (c in 0 until list.length()) {
            val case = list.getJSONObject(c)
            val name = case.getString("name")
            val errs = try { run(case) } catch (e: Throwable) { listOf("threw ${e.javaClass.simpleName}: ${e.message}") }
            if (errs.isNotEmpty()) failures += "$name: " + errs.take(4).joinToString("; ")
        }
        assertTrue("${failures.size} of ${list.length()} cases differ:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun run(case: JSONObject): List<String> {
        val day = DayLog.read(File(root, case.getString("log")))
        val errs = ArrayList<String>()
        if (day.dropped != case.getInt("dropped_lines")) errs += "dropped ${day.dropped} lines, expected ${case.getInt("dropped_lines")}"
        val net = case.optJSONObject("network_patch")?.let { Net(runs, terrain, params, it) } ?: baseNet
        val a = Analyzer(net, params)
        val exp = case.getJSONObject("expected")
        errs += Compare.final(exp.getJSONObject("final"), AnalysisJson.final(a.final(day.header, day.points, day.events)))
        errs += Compare.live(exp.getJSONObject("live"), AnalysisJson.live(a.live(day.header, day.points, day.events)))
        return errs
    }
}

/** The result as the reference writes it (README, "The analysis result"), so that compare.py's rules port as they are. */
internal object AnalysisJson {
    private fun n(v: Double?): Any = v ?: JSONObject.NULL

    fun final(r: Analyzer.DayResult): JSONObject {
        val o = JSONObject()
        o.put("gaps", JSONArray().also { g -> r.gaps.forEach { g.put(JSONObject().put("t0", it.t0).put("t1", it.t1)) } })
        o.put("falls", JSONArray().also { g -> r.falls.forEach { g.put(JSONObject().put("t", it.t).put("lat", n(it.lat)).put("lon", n(it.lon))) } })
        o.put("segments", JSONArray().also { arr -> r.segments.forEach { arr.put(seg(it)) } })
        val st = r.stats
        o.put("stats", if (st == null) JSONObject() else JSONObject()
            .put("descents", st.descents).put("lifts", st.lifts).put("ski_dist_m", st.skiDistM).put("drop_m", st.dropM)
            .put("ski_s", st.skiS).put("lift_s", st.liftS).put("lift_rise_m", st.liftRiseM).put("max_kmh", n(st.maxKmh)))
        return o
    }

    private fun station(id: String?) = JSONObject().put("station", id ?: JSONObject.NULL)

    private fun seg(s: Analyzer.Seg): JSONObject {
        val o = JSONObject().put("type", s.type).put("t0", s.t0).put("t1", s.t1)
        when (s.type) {
            "lift" -> o.put("lift", s.lift).put("from", station(s.from)).put("to", station(s.to)).put("rise_m", n(s.riseM))
            "stop" -> o.put("kind", s.kind)
            "walk" -> o.put("dist_m", n(s.distM))
            "drive" -> o.put("dist_m", n(s.distM)).put("rise_m", n(s.riseM))
            "descent" -> {
                o.put("legs", JSONArray().also { a -> s.legs.forEach { l -> a.put(JSONObject().put("kind", l.kind).put("runs", JSONArray(l.runs)).put("t0", l.t0).put("t1", l.t1).put("dist_m", l.distM)) } })
                o.put("dist_m", n(s.distM)).put("drop_m", n(s.dropM)).put("max_kmh", n(s.maxKmh))
                o.put("offpiste", JSONArray().also { a -> s.offpiste.forEach { p -> a.put(JSONObject().put("t0", p.t0).put("t1", p.t1).put("drop_m", p.dropM).put("dist_m", p.distM)) } })
            }
        }
        return o
    }

    fun live(r: Analyzer.LiveResult): JSONObject {
        val o = JSONObject()
        o.put("events", JSONArray().also { a ->
            r.events.forEach { e ->
                val j = JSONObject().put("t", e.t).put("act", e.act)
                if (e.lift != null) j.put("lift", e.lift)
                if (e.act == "ski") j.put("runs", e.runs?.let { JSONArray(it) } ?: JSONObject.NULL)
                a.put(j)
            }
        })
        o.put("max_kmh", n(r.maxKmh))
        o.put("falls", JSONArray().also { g -> r.falls.forEach { g.put(JSONObject().put("t", it.t).put("at", it.at)) } })
        if (r.autostop != null) o.put("autostop", JSONObject().put("t", r.autostop.t).put("reason", r.autostop.reason))
        return o
    }
}

/** tools/dayrec/compare.py, rule by rule. */
internal object Compare {
    private const val T_MS = 3000L
    private const val REL = 0.03
    private const val ABS_M = 1.0
    private const val KMH = 0.1

    private fun d(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null

    private fun num(a: Double?, b: Double?, path: String, errs: MutableList<String>, rel: Double = REL, ab: Double = ABS_M) {
        if (a == null || b == null) { if ((a == null) != (b == null)) errs += "$path: $a != $b"; return }
        if (abs(a - b) > max(rel * max(abs(a), abs(b)), ab)) errs += "$path: $a != $b"
    }

    private fun time(a: Long?, b: Long?, path: String, errs: MutableList<String>) {
        if (a == null || b == null || abs(a - b) > T_MS) errs += "$path: time $a != $b"
    }

    private fun kmh(a: Double?, b: Double?, path: String, errs: MutableList<String>) {
        if (a == null || b == null) { if ((a == null) != (b == null)) errs += "$path: $a != $b" }
        else if (abs(a - b) > KMH + 1e-9) errs += "$path: $a != $b km/h"
    }

    private fun segKey(s: JSONObject): List<Any?> {
        val k = arrayListOf<Any?>(s.getString("type"))
        if (s.getString("type") == "lift") k.addAll(listOf(s.getString("lift"), s.getJSONObject("from").opt("station").let { if (it == JSONObject.NULL) null else it }, s.getJSONObject("to").opt("station").let { if (it == JSONObject.NULL) null else it }))
        if (s.getString("type") == "stop") k += s.getString("kind")
        return k
    }

    private fun list(a: JSONArray?): List<JSONObject> = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }

    fun final(exp: JSONObject, got: JSONObject): List<String> {
        val errs = ArrayList<String>()
        val es = list(exp.optJSONArray("segments")); val gs = list(got.optJSONArray("segments"))
        val ek = es.map { segKey(it) }; val gk = gs.map { segKey(it) }
        if (ek != gk) return listOf("segments differ: expected $ek, got $gk")
        for ((n, pair) in es.zip(gs).withIndex()) {
            val (e, g) = pair
            val p = "segments[$n] ${e.getString("type")}"
            time(e.getLong("t0"), g.getLong("t0"), "$p.t0", errs); time(e.getLong("t1"), g.getLong("t1"), "$p.t1", errs)
            if (e.getString("type") == "descent") {
                val el = list(e.getJSONArray("legs")).map { it.getString("kind") to (0 until it.getJSONArray("runs").length()).map { i -> it.getJSONArray("runs").getString(i) } }
                val gl = list(g.getJSONArray("legs")).map { it.getString("kind") to (0 until it.getJSONArray("runs").length()).map { i -> it.getJSONArray("runs").getString(i) } }
                if (el != gl) { errs += "$p.legs: expected $el, got $gl"; continue }
                for ((m, lp) in list(e.getJSONArray("legs")).zip(list(g.getJSONArray("legs"))).withIndex()) {
                    val (a, b) = lp
                    time(a.getLong("t0"), b.getLong("t0"), "$p.legs[$m].t0", errs); time(a.getLong("t1"), b.getLong("t1"), "$p.legs[$m].t1", errs)
                    num(d(a, "dist_m"), d(b, "dist_m"), "$p.legs[$m].dist_m", errs)
                }
                for (k in listOf("dist_m", "drop_m")) num(d(e, k), d(g, k), "$p.$k", errs)
                val eo = list(e.optJSONArray("offpiste")); val go = list(g.optJSONArray("offpiste"))
                if (eo.size != go.size) errs += "$p.offpiste: ${eo.size} != ${go.size}"
                else for ((m, op) in eo.zip(go).withIndex()) {
                    val (a, b) = op
                    time(a.getLong("t0"), b.getLong("t0"), "$p.offpiste[$m].t0", errs); time(a.getLong("t1"), b.getLong("t1"), "$p.offpiste[$m].t1", errs)
                    num(d(a, "drop_m"), d(b, "drop_m"), "$p.offpiste[$m].drop_m", errs)
                }
                kmh(d(e, "max_kmh"), d(g, "max_kmh"), "$p.max_kmh", errs)
            }
            if (e.getString("type") == "walk") num(d(e, "dist_m"), d(g, "dist_m"), "$p.dist_m", errs)
        }
        val est = exp.optJSONObject("stats") ?: JSONObject(); val gst = got.optJSONObject("stats") ?: JSONObject()
        for (k in listOf("descents", "lifts")) if (est.opt(k) != gst.opt(k)) errs += "stats.$k: ${est.opt(k)} != ${gst.opt(k)}"
        for (k in listOf("ski_dist_m", "drop_m")) num(d(est, k), d(gst, k), "stats.$k", errs)
        kmh(d(est, "max_kmh"), d(gst, "max_kmh"), "stats.max_kmh", errs)
        events(list(exp.optJSONArray("falls")), list(got.optJSONArray("falls")), "falls", errs)
        val eg = exp.optJSONArray("gaps")?.length() ?: 0; val gg = got.optJSONArray("gaps")?.length() ?: 0
        if (eg != gg) errs += "gaps: $eg != $gg"
        return errs
    }

    private fun liveKey(e: JSONObject): List<Any?> {
        val runs = e.opt("runs")
        val r: Any? = if (runs == null || runs == JSONObject.NULL) null else (0 until (runs as JSONArray).length()).map { runs.getString(it) }
        return listOf(e.getString("act"), e.optString("lift", "").takeIf { e.has("lift") }, r)
    }

    fun live(exp: JSONObject, got: JSONObject): List<String> {
        val ee = list(exp.getJSONArray("events")); val ge = list(got.getJSONArray("events"))
        val ek = ee.map { liveKey(it) }; val gk = ge.map { liveKey(it) }
        if (ek != gk) return listOf("live events differ: expected $ek, got $gk")
        val errs = ArrayList<String>()
        for ((n, p) in ee.zip(ge).withIndex()) time(p.first.getLong("t"), p.second.getLong("t"), "live[$n]", errs)
        kmh(d(exp, "max_kmh"), d(got, "max_kmh"), "live.max_kmh", errs)
        events(list(exp.optJSONArray("falls")), list(got.optJSONArray("falls")), "live.falls", errs)
        val ea = exp.optJSONObject("autostop"); val ga = got.optJSONObject("autostop")
        if ((ea == null) != (ga == null) || (ea != null && ga != null && ea.getString("reason") != ga.getString("reason"))) errs += "live.autostop: $ea != $ga"
        else if (ea != null && ga != null) time(ea.getLong("t"), ga.getLong("t"), "live.autostop", errs)
        return errs
    }

    /** Falls: the same number, each within 3 s. */
    private fun events(e: List<JSONObject>, g: List<JSONObject>, path: String, errs: MutableList<String>) {
        if (e.size != g.size) { errs += "$path: ${e.size} != ${g.size}"; return }
        for ((n, p) in e.zip(g).withIndex()) time(p.first.getLong("t"), p.second.getLong("t"), "$path[$n]", errs)
    }
}

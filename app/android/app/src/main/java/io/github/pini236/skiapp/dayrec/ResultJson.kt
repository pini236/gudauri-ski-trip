package io.github.pini236.skiapp.dayrec

import io.github.pini236.skiapp.dayrec.analysis.Analyzer
import org.json.JSONArray
import org.json.JSONObject

/**
 * The analysis result as a file (tools/dayrec/README.md, "The analysis result"): one JSON per day, beside the raw log,
 * that can always be computed again from the log. It stays on the phone with the log; nothing here is sent anywhere.
 */
object ResultJson {
    private fun n(v: Double?): Any = v ?: JSONObject.NULL
    private fun station(id: String?, unmapped: Boolean) =
        if (unmapped) JSONObject().put("station", JSONObject.NULL).put("unmapped", true) else JSONObject().put("station", id ?: JSONObject.NULL)
    private fun at(m: Analyzer.MaxAt?): Any = m?.let { JSONObject().put("t", it.t).put("lat", it.lat).put("lon", it.lon) } ?: JSONObject.NULL

    fun of(r: Analyzer.DayResult, resort: String, analysis: Int): JSONObject {
        val o = JSONObject().put("analysis", analysis).put("v", 1).put("resort", resort).put("points", r.points)
            .put("start", r.start).put("end", r.end).put("baro", r.baro).put("coverage", r.coverage)
        o.put("gaps", JSONArray().also { a -> r.gaps.forEach { a.put(JSONObject().put("t0", it.t0).put("t1", it.t1)) } })
        o.put("falls", JSONArray().also { a -> r.falls.forEach { a.put(JSONObject().put("t", it.t).put("lat", n(it.lat)).put("lon", n(it.lon))) } })
        o.put("segments", JSONArray().also { a -> r.segments.forEach { a.put(seg(it)) } })
        r.stats?.let {
            o.put("stats", JSONObject().put("descents", it.descents).put("lifts", it.lifts).put("ski_dist_m", it.skiDistM)
                .put("drop_m", it.dropM).put("ski_s", it.skiS).put("lift_s", it.liftS).put("lift_rise_m", it.liftRiseM)
                .put("max_kmh", n(it.maxKmh)).put("max_at", at(it.maxAt)))
        } ?: o.put("stats", JSONObject())
        return o
    }

    fun seg(s: Analyzer.Seg): JSONObject {
        val o = JSONObject().put("type", s.type).put("t0", s.t0).put("t1", s.t1)
        when (s.type) {
            "lift" -> o.put("lift", s.lift).put("name", s.name ?: JSONObject.NULL)
                .put("from", station(s.from, s.fromUnmapped)).put("to", station(s.to, s.toUnmapped)).put("rise_m", n(s.riseM))
            "descent" -> {
                o.put("legs", JSONArray().also { a -> s.legs.forEach { l ->
                    a.put(JSONObject().put("t0", l.t0).put("t1", l.t1).put("dist_m", l.distM).put("kind", l.kind).put("runs", JSONArray(l.runs)))
                } })
                o.put("dist_m", n(s.distM)).put("drop_m", n(s.dropM)).put("moving_s", s.movingS).put("avg_kmh", n(s.avgKmh))
                    .put("max_kmh", n(s.maxKmh)).put("max_at", at(s.maxAt))
                o.put("stops", JSONArray().also { a -> s.stops.forEach { a.put(JSONObject().put("t0", it.t0).put("t1", it.t1)) } })
                o.put("offpiste", JSONArray().also { a -> s.offpiste.forEach {
                    a.put(JSONObject().put("t0", it.t0).put("t1", it.t1).put("drop_m", it.dropM).put("dist_m", it.distM))
                } })
                o.put("top_m", n(s.topM)).put("bottom_m", n(s.bottomM))
            }
            "stop" -> o.put("lat", s.lat).put("lon", s.lon).put("kind", s.kind)
            "walk" -> o.put("dist_m", n(s.distM))
            "ascent" -> o.put("rise_m", n(s.riseM))
        }
        return o
    }
}

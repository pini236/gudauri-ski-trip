package io.github.pini236.skiapp.data

import org.json.JSONArray
import org.json.JSONObject

/** A run from site/data/runs-and-lifts.json. Each line is x,y pairs in projected metres; area segments are left out. */
class Piste(val key: String, val name: String, val color: String, val named: Boolean, val kind: String, val lines: List<FloatArray>)

class Lift(val name: String, val kind: String, val pts: FloatArray, val id: String = "")

class Runs(val pistes: List<Piste>, val lifts: List<Lift>) {
    companion object {
        /** JSON null counts as missing (Android's org.json would otherwise return the text "null"). */
        private fun JSONObject.str(k: String, fallback: String) = if (isNull(k)) fallback else getString(k)

        private fun line(g: JSONArray): FloatArray {
            val out = FloatArray(g.length() * 2)
            for (i in 0 until g.length()) {
                val p = g.getJSONArray(i)
                out[i * 2] = Geo.x(p.getDouble(1))
                out[i * 2 + 1] = Geo.y(p.getDouble(0))
            }
            return out
        }

        fun parse(json: String): Runs {
            val o = JSONObject(json)
            val ps = o.getJSONArray("pistes")
            val pistes = (0 until ps.length()).map { i ->
                val p = ps.getJSONObject(i)
                val segs = p.getJSONArray("segs")
                val lines = (0 until segs.length()).map { segs.getJSONObject(it) }
                    .filter { !it.optBoolean("area", false) }
                    .map { line(it.getJSONArray("g")) }
                    .filter { it.size >= 4 }
                Piste(p.getString("key"), p.str("name", p.getString("key")), p.str("color", "black"), p.optBoolean("named", false), p.str("kind", "run"), lines)
            }
            val ls = o.getJSONArray("lifts")
            val lifts = (0 until ls.length()).map { i ->
                val l = ls.getJSONObject(i)
                Lift(l.str("name", ""), l.str("kind", ""), line(l.getJSONArray("g")), l.opt("id")?.toString() ?: "")
            }
            return Runs(pistes, lifts)
        }
    }
}

/** A run's profile in the descent game (exported from site/games/descent/index.html by tools/build-app-data.py). */
class Profile(val key: String, val color: String, val len: Float, val top: Float, val bot: Float, val step: Float, val h: FloatArray) {
    companion object {
        fun parse(json: String): List<Profile> {
            val a = JSONArray(json)
            return (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                val h = p.getJSONArray("h")
                Profile(
                    p.getString("key"), p.getString("color"), p.getDouble("len").toFloat(),
                    p.getDouble("top").toFloat(), p.getDouble("bot").toFloat(), p.getDouble("step").toFloat(),
                    FloatArray(h.length()) { h.getDouble(it).toFloat() },
                )
            }
        }
    }
}

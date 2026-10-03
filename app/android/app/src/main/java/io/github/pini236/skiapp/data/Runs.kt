package io.github.pini236.skiapp.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A run from site/data/runs-and-lifts.json. Each line is x,y pairs in projected metres; area segments are left out.
 * [toLifts], [fromPistes] and [fromLifts] are the connections the site computed (the meeting point's "how to get
 * there", and a run is open only if a lift up to it is), and [lat] is the mean latitude of all its points, areas too
 * (the site's split of the Kobi side, [Runs.KOBI_LAT]).
 */
class Piste(
    val key: String, val name: String, val color: String, val named: Boolean, val kind: String, val lines: List<FloatArray>,
    val toLifts: List<String> = emptyList(), val fromPistes: List<String> = emptyList(), val lat: Double = 0.0,
    val fromLifts: List<String> = emptyList(),
)

/** A lift: its line in projected metres, from its first point. [status] "inactive" for one that does not run. */
class Lift(val name: String, val kind: String, val pts: FloatArray, val id: String = "", val status: String? = null, val lat: Double = 0.0)

class Runs(val pistes: List<Piste>, val lifts: List<Lift>) {
    /** The main side of the mountain, without Kobi (the site's mainPistes and mainLifts). */
    val mainPistes: List<Piste> get() = pistes.filter { it.lat <= KOBI_LAT }
    val mainLifts: List<Lift> get() = lifts.filter { it.lat <= KOBI_LAT }

    companion object {
        /** The Kobi side is everything north of Kobi Pass, the top of Firni (site/js/app.js). */
        const val KOBI_LAT = 42.5115

        /** JSON null counts as missing (Android's org.json would otherwise return the text "null"). */
        private fun JSONObject.str(k: String, fallback: String) = if (isNull(k)) fallback else getString(k)
        private fun JSONObject.strings(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()

        /** The mean latitude of every point of these lines (the site's meanLat). */
        private fun meanLat(lines: List<JSONArray>): Double {
            var t = 0.0; var n = 0
            for (g in lines) for (i in 0 until g.length()) { t += g.getJSONArray(i).getDouble(0); n++ }
            return if (n > 0) t / n else 0.0
        }

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
                val all = (0 until segs.length()).map { segs.getJSONObject(it) }
                val lines = all
                    .filter { !it.optBoolean("area", false) }
                    .map { line(it.getJSONArray("g")) }
                    .filter { it.size >= 4 }
                Piste(p.getString("key"), p.str("name", p.getString("key")), p.str("color", "black"), p.optBoolean("named", false), p.str("kind", "run"), lines,
                    p.strings("toLifts"), p.strings("fromPistes"), meanLat(all.map { it.getJSONArray("g") }), p.strings("fromLifts"))
            }
            val ls = o.getJSONArray("lifts")
            val lifts = (0 until ls.length()).map { i ->
                val l = ls.getJSONObject(i)
                val g = l.getJSONArray("g")
                Lift(l.str("name", ""), l.str("kind", ""), line(g), l.opt("id")?.toString() ?: "", if (l.isNull("status")) null else l.optString("status"), meanLat(listOf(g)))
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

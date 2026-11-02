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
    /** What the run's panel shows (13.3): the length in metres, the trail numbers, OSM's grades, grooming and lights. */
    val len: Int = 0, val refs: List<String> = emptyList(), val osmDiff: List<String> = emptyList(),
    val groom: List<String> = emptyList(), val lit: List<String> = emptyList(), val joins: List<String> = emptyList(),
    /** The OSM ways it is drawn from, and whether one of its parts is an area (a beginner zone). */
    val osmIds: List<Long> = emptyList(), val hasArea: Boolean = false, val research: Research? = null,
    /**
     * Round 21 (docs/PARITY.md 16): where the name may go beside the line. [segLines] is every segment of the run by its index in
     * the data (null for an area or a stub: the windows' `s` counts them all), [label] the windows `[s, t0, t1, cL, cR]` of its own
     * name, and [shared] the windows of a stretch it shares with another run (the other's key). All empty when the data has none.
     */
    val segLines: List<FloatArray?> = emptyList(), val label: List<DoubleArray> = emptyList(), val shared: List<SharedWin> = emptyList(),
) {
    /** What the run is called on screen (the site's pname): `name`, and the key when there is none. The key is a stable id and may be a raw OSM id. */
    val title: String get() = name.ifBlank { key }
}

/** A name window of a stretch two runs share: `[s, t0, t1, cL, cR]` and the key of the other run (tools/build-labels.py). */
class SharedWin(val w: DoubleArray, val other: String)

/** Where a filled-in run comes from (site/data/runs-and-lifts.json, research); the notes are in Hebrew in the data. */
class Research(
    val conf: String, val status: String, val notes: String, val sources: List<String>, val gps: Int,
    val osmIds: List<Long>, val historical: Boolean, val partial: String?,
)

/** A lift: its line in projected metres, from its first point. [status] "inactive" for one that does not run. */
class Lift(
    val name: String, val kind: String, val pts: FloatArray, val id: String = "", val status: String? = null, val lat: Double = 0.0,
    /** The lift's panel (13.3): length in metres, ride minutes, seats, bubble, people an hour, rise and year, from OSM. */
    val len: Int = 0, val dur: String? = null, val occ: String? = null, val bubble: String? = null,
    val cap: String? = null, val rise: String? = null, val year: String? = null,
)

/** [fetched]: when the lines were taken from OSM; [researchDate]: when the missing runs were filled in (the list's source). */
class Runs(val pistes: List<Piste>, val lifts: List<Lift>, val fetched: String = "", val researchDate: String = "") {
    /** The main side of the mountain, without Kobi (the site's mainPistes and mainLifts). */
    val mainPistes: List<Piste> get() = pistes.filter { it.lat <= KOBI_LAT }
    val mainLifts: List<Lift> get() = lifts.filter { it.lat <= KOBI_LAT }

    companion object {
        /** The Kobi side is everything north of Kobi Pass, the top of Firni (site/js/app.js). */
        const val KOBI_LAT = 42.5115

        /** JSON null counts as missing (Android's org.json would otherwise return the text "null"). */
        private fun JSONObject.str(k: String, fallback: String) = if (isNull(k)) fallback else getString(k)
        private fun JSONObject.strOrNull(k: String): String? = if (!has(k) || isNull(k)) null else get(k).toString().takeIf { it.isNotBlank() }
        private fun JSONObject.strings(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        private fun JSONObject.longs(k: String): List<Long> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getLong(it) } }.orEmpty()

        private fun research(r: JSONObject?) = r?.let {
            Research(it.str("conf", ""), it.str("status", ""), it.str("notes", ""), it.strings("sources"), it.optInt("gps", 0),
                it.longs("osmIds"), it.optBoolean("historical", false), it.strOrNull("partial"))
        }

        /** The mean latitude of every point of these lines (the site's meanLat). */
        private fun meanLat(lines: List<JSONArray>): Double {
            var t = 0.0; var n = 0
            for (g in lines) for (i in 0 until g.length()) { t += g.getJSONArray(i).getDouble(0); n++ }
            return if (n > 0) t / n else 0.0
        }

        /**
         * The windows of a `label` or `shared` field: `{"v":1,"a":[[s,t0,t1,cL,cR(,other)],...]}`. A field of another version, or one
         * that does not read, is no windows (a reader meets a new field without breaking: architect condition 5).
         */
        internal fun windows(o: JSONObject?): List<Pair<DoubleArray, String?>> = runCatching {
            if (o == null || o.optInt("v", 0) != 1) return@runCatching emptyList()
            val a = o.getJSONArray("a")
            (0 until a.length()).map { i ->
                val w = a.getJSONArray(i)
                Pair(DoubleArray(5) { w.getDouble(it) }, if (w.length() > 5) w.getString(5) else null)
            }
        }.getOrDefault(emptyList())

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
                // the data's own segment index is kept for the name windows (they count the areas too)
                val segLines = all.map { sg -> if (sg.optBoolean("area", false)) null else line(sg.getJSONArray("g")).takeIf { it.size >= 4 } }
                val lines = segLines.filterNotNull()
                Piste(p.getString("key"), p.str("name", p.getString("key")), p.str("color", "black"), p.optBoolean("named", false), p.str("kind", "run"), lines,
                    p.strings("toLifts"), p.strings("fromPistes"), meanLat(all.map { it.getJSONArray("g") }), p.strings("fromLifts"),
                    p.optInt("len", 0), p.strings("refs"), p.strings("osmDiff"), p.strings("groom"), p.strings("lit"), p.strings("joins"),
                    all.map { it.optLong("id") }, all.any { it.optBoolean("area", false) }, research(p.optJSONObject("research")),
                    segLines, windows(p.optJSONObject("label")).map { it.first }, windows(p.optJSONObject("shared")).mapNotNull { (w, o) -> o?.let { SharedWin(w, it) } })
            }
            val ls = o.getJSONArray("lifts")
            val lifts = (0 until ls.length()).map { i ->
                val l = ls.getJSONObject(i)
                val g = l.getJSONArray("g")
                Lift(l.str("name", ""), l.str("kind", ""), line(g), l.opt("id")?.toString() ?: "", if (l.isNull("status")) null else l.optString("status"), meanLat(listOf(g)),
                    l.optInt("len", 0), l.strOrNull("dur"), l.strOrNull("occ"), l.strOrNull("bubble"), l.strOrNull("cap"), l.strOrNull("rise"), l.strOrNull("year"))
            }
            return Runs(pistes, lifts, o.str("fetched", ""), o.optJSONObject("research")?.str("date", "") ?: "")
        }
    }
}

/** A run's profile in the descent game (exported from site/games/descent/index.html by tools/build-app-data.py). */
/** [lift] is the lift at the bottom of the run, when there is one. */
class Profile(val key: String, val color: String, val len: Float, val top: Float, val bot: Float, val step: Float, val h: DoubleArray, val lift: String = "") {
    companion object {
        fun parse(json: String): List<Profile> {
            val a = JSONArray(json)
            return (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                val h = p.getJSONArray("h")
                Profile(
                    p.getString("key"), p.getString("color"), p.getDouble("len").toFloat(),
                    p.getDouble("top").toFloat(), p.getDouble("bot").toFloat(), p.getDouble("step").toFloat(),
                    DoubleArray(h.length()) { h.getDouble(it) }, p.optString("lift", ""),
                )
            }
        }
    }
}

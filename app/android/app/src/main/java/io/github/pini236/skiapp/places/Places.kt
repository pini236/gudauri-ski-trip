package io.github.pini236.skiapp.places

import org.json.JSONArray
import org.json.JSONObject

/**
 * Food and services near the stations (round 31, approved 1.11.2026), the lean `places.json` the ski expert builds from the
 * open map (research/places/places.lean.json, copied to site/data/places.json). Read as the site reads it
 * (site/js/app.js, PL): every station has the places inside 250 m, nearest first, or `info: "none"` — and "none" means
 * there is no information, never "there is nothing here". A kind this app does not know is ignored, a file with a higher
 * `schema` is not used, and nothing here says when anything is open.
 */
class Place(val id: String, val kind: String, val name: String?, val lat: Double, val lon: Double, val seasonal: Boolean, val slope: Boolean)

/** A place near a station, and how far, in metres. */
class Near(val place: Place, val d: Int)

class Station(
    val id: String, val lat: Double, val lon: Double, val ends: List<StationEnd>, val info: String, private val refs: List<Pair<String, Int>>,
) {
    /** The places the station has, nearest first, that this app knows how to show. */
    fun near(by: Map<String, Place>): List<Near> =
        if (info != "some") emptyList() else refs.mapNotNull { (id, d) -> by[id]?.takeIf { it.kind in Places.KINDS }?.let { Near(it, d) } }
}

/** [liftId]: the lift's OSM id as the data writes it; [bottom]: the lift's lower end (otherwise its top). */
class StationEnd(val liftId: String, val bottom: Boolean)

class Places private constructor(val stations: List<Station>, private val byId: Map<String, Place>, val places: List<Place>) {
    /** The station at one end of a lift, or null for a lift that is on none (the unnamed conveyor and the like). */
    fun stationOf(liftId: String, bottom: Boolean): Station? =
        stations.firstOrNull { s -> s.ends.any { it.liftId == liftId && it.bottom == bottom } }

    fun near(s: Station): List<Near> = s.near(byId)

    /** The stations that have something to show, with the kinds they have in the order of [KINDS]. */
    fun withPlaces(): List<Pair<Station, List<String>>> = stations.mapNotNull { s ->
        val n = near(s); if (n.isEmpty()) null else s to KINDS.filter { k -> n.any { it.place.kind == k } }
    }

    /** A café or a restaurant on the slope belongs to no station. */
    fun onSlope(): List<Place> = places.filter { it.slope && it.kind in KINDS }

    companion object {
        /** The kinds the app shows, in the order of the dots. */
        val KINDS = listOf("food", "cafe", "aid", "wc")

        /** The feature's name in resorts.json "features" (the site's `has('places')`). */
        const val FEATURE = "places"

        /** The file, or null when it is not the structure this app knows (a higher schema, a broken file): then there are no places. */
        fun parse(json: String?): Places? = runCatching {
            val o = JSONObject(json ?: return null)
            if (o.optInt("schema", 0) != 1) return null
            val ps = o.getJSONArray("places").objs().map { p ->
                val ll = p.getJSONArray("ll")
                Place(p.getString("id"), p.optString("kind"), p.optString("name").takeIf { it.isNotBlank() && !p.isNull("name") },
                    ll.getDouble(0), ll.getDouble(1), p.optBoolean("seasonal", false), p.optBoolean("slope", false))
            }
            val ss = o.getJSONArray("stations").objs().map { s ->
                val ll = s.getJSONArray("ll")
                Station(s.getString("id"), ll.getDouble(0), ll.getDouble(1),
                    s.getJSONArray("ends").objs().map { StationEnd(it.get("liftId").toString(), it.getString("end") == "bottom") },
                    s.optString("info"), s.optJSONArray("places")?.objs()?.map { it.getString("p") to it.optInt("d") }.orEmpty())
            }
            Places(ss, ps.associateBy { it.id }, ps)
        }.getOrNull()

        /** Whether resorts.json lists the feature for a resort. */
        fun enabled(resortsJson: String?, resort: String = "gudauri"): Boolean = runCatching {
            val all = JSONObject(resortsJson ?: return false).getJSONArray("resorts")
            all.objs().firstOrNull { it.optString("id") == resort }
                ?.optJSONArray("features")?.let { f -> (0 until f.length()).any { f.optString(it) == FEATURE } } ?: false
        }.getOrDefault(false)

        private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    }
}

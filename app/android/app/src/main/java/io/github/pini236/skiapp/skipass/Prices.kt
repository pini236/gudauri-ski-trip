package io.github.pini236.skiapp.skipass

import io.github.pini236.skiapp.season.Season
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One ticket of the table: [days] and the foreign visitor's price in whole GEL for an adult and a child. */
class Pass(val id: String, val days: Int, val adult: Int, val child: Int, val scope: String, val named: Boolean)

/** What something costs: the sum, and the tickets it is made of, in days (one ticket for a day count that is in the table). */
class Quote(val total: Int, val parts: List<Int>)

/**
 * The ski-pass prices (round 40, approved 18.11.2026): site/data/prices.json, the resort's own price list copied with its
 * source and the date it was checked. GEL only (no conversion: that needs a rate and a source). A price list past its
 * `until` date is "no information" (never an old price shown as current). The rule for a number of days that is not a
 * ticket is the shared one (tools/check-skipass.py; the cases tools/fixtures/skipass-calc.json): the cheapest way to
 * cover it with at most two tickets, a tie to the bigger ticket first, up to [MAX_DAYS], otherwise no price.
 */
class Prices(
    val passes: List<Pass>, val until: LocalDate, val checked: LocalDate, val src: String, val url: String,
    val nightAdult: Int?, val nightChild: Int?, val childFrom: Int, val childTo: Int, val freeUnder: Int?, val deposit: Int?,
    val rules: List<String>, val buyIos: String?, val buyAndroid: String?,
) {
    /** False from the day after `until`: the screen says there is no information. */
    fun valid(today: LocalDate) = !today.isAfter(until)

    fun price(days: Int, child: Boolean): Quote? {
        val table = passes.associate { it.days to if (child) it.child else it.adult }
        table[days]?.let { return Quote(it, listOf(days)) }
        if (days < 1 || days > MAX_DAYS) return null
        val keys = table.keys.sortedDescending()
        var best: Quote? = null
        for (i in keys.indices) for (j in i until keys.size) {
            val a = keys[i]; val b = keys[j]
            if (a + b != days) continue
            val total = table.getValue(a) + table.getValue(b)
            if (best == null || total < best.total) best = Quote(total, listOf(a, b))
        }
        return best
    }

    /** The cost of every day at the day-ticket's price, for "daily × n". */
    fun daily(child: Boolean): Int? = passes.firstOrNull { it.days == 1 }?.let { if (child) it.child else it.adult }

    companion object {
        const val FEATURE = "skipass"
        const val MAX_DAYS = 14

        // the only store links this screen opens, as the build check (tools/check-skipass.py) holds them: no parameters but `hl`
        private val APPLE = Regex("^https://apps\\.apple\\.com/[A-Za-z0-9_./-]+(\\?hl=[A-Za-z-]+)?$")
        private val PLAY = Regex("^https://play\\.google\\.com/store/apps/details\\?id=[A-Za-z0-9_.]+(&hl=[A-Za-z-]+)?$")

        fun appleLink(s: String?): String? = s?.takeIf { APPLE.matches(it) }
        fun playLink(s: String?): String? = s?.takeIf { PLAY.matches(it) }

        /** The file, or null when it is not the structure this app knows (a higher schema, another currency or audience, a broken file). */
        fun parse(json: String?): Prices? = runCatching {
            val o = JSONObject(json ?: return null)
            if (o.optInt("schema", 0) != 1 || o.optString("currency") != "GEL" || o.optString("audience") != "foreign") return null
            val ps = o.getJSONArray("passes").objs().map { p ->
                val d = p.getInt("days"); val a = p.getInt("adult"); val c = p.getInt("child")
                if (d < 1 || a < 1 || c < 1) return null
                Pass(p.getString("id"), d, a, c, p.optString("scope"), p.optBoolean("named", false))
            }
            if (ps.isEmpty() || ps.map { it.days }.let { it != it.distinct().sorted() }) return null
            val night = o.optJSONObject("night")
            val kid = o.optJSONArray("child")
            val buy = o.optJSONObject("buy")
            Prices(ps, Season.localDate(o.optString("until")) ?: return null, Season.localDate(o.optString("checked")) ?: return null,
                o.getString("src"), o.getString("url").takeIf { it.startsWith("https://") } ?: return null,
                night?.optInt("adult")?.takeIf { it > 0 }, night?.optInt("child")?.takeIf { it > 0 },
                kid?.optInt(0, 6) ?: 6, kid?.optInt(1, 12) ?: 12, o.optInt("free_under").takeIf { it > 0 }, o.optInt("card_deposit").takeIf { it > 0 },
                o.optJSONArray("rules")?.let { r -> (0 until r.length()).map { r.optString(it) } }.orEmpty(),
                appleLink(buy?.optString("ios")), playLink(buy?.optString("android")))
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

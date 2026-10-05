package io.github.pini236.skiapp.status

import android.content.Context
import io.github.pini236.skiapp.data.HttpFetcher
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.SiteData
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * One report from the site's /api/status (design round 3, S1 to S3), the same file and format the site reads (LSTAT in
 * site/js/app.js): {updated:"ISO time", lifts:{"<lift name>":{open, reason?}}, pistes:{"<run key>":{open}}}.
 * The function that fills it from the MTA status page is written when that page works again (December).
 */
class Report(val updated: Long, val lifts: Map<String, Report.LiftState>, val pistes: Map<String, Boolean>, val json: String) {
    class LiftState(val open: Boolean, val reason: String?)

    companion object {
        /** A report needs a time and lifts, as on the site; anything else is "no report". */
        fun parse(json: String): Report? = runCatching {
            val o = JSONObject(json)
            val t = o.optString("updated").takeIf { it.isNotBlank() && !o.isNull("updated") } ?: return null
            val ls = o.optJSONObject("lifts") ?: return null
            val updated = runCatching { OffsetDateTime.parse(t).toInstant() }.getOrElse { Instant.parse(t) }.toEpochMilli()
            val lifts = ls.keys().asSequence().mapNotNull { k ->
                ls.optJSONObject(k)?.let { l -> k to LiftState(l.optBoolean("open"), l.optString("reason").takeIf { r -> r.isNotBlank() && !l.isNull("reason") }) }
            }.toMap()
            val ps = o.optJSONObject("pistes")
            val pistes = ps?.keys()?.asSequence()?.mapNotNull { k -> ps.optJSONObject(k)?.let { k to it.optBoolean("open") } }?.toMap().orEmpty()
            Report(updated, lifts, pistes, json)
        }.getOrNull()
    }
}

/**
 * The state of the lifts now. No report, or one older than 30 minutes: "no current information", and the map stays
 * as it is. We never guess (the site's rule).
 */
class LiftStatus(val names: List<String>, val report: Report?, val now: Long) {
    val fresh: Boolean = report != null && now - report.updated < STALE_MS

    /** true or false from a fresh report, null when there is none or it says nothing about this lift. */
    fun isOpen(name: String?): Boolean? = if (!fresh || name == null) null else report!!.lifts[name]?.open

    fun reason(name: String): String? = if (fresh) report!!.lifts[name]?.reason else null

    /** Open for me: the run itself, and a lift up to it (the site's runOpen). */
    fun runOpen(p: Piste): Boolean? {
        if (!fresh) return null
        val r = report!!.pistes[p.key]
        if (r == false) return false
        val up = p.fromLifts.filter { it in report.lifts }
        return if (up.isNotEmpty()) up.any { report.lifts.getValue(it).open } else r
    }

    val open: Int get() = names.count { isOpen(it) == true }

    /** The state at this report, kept to tell "opened since you checked" next time. */
    fun snapshot(): Map<String, Boolean?> = names.associateWith { isOpen(it) }

    /**
     * The lifts that changed since [before], with their state now. As on the site, but a lift the report says nothing
     * about is left out rather than called closed.
     */
    fun changes(before: Map<String, Boolean?>?): List<Pair<String, Boolean>> {
        if (before == null || !fresh) return emptyList()
        return names.mapNotNull { n -> val o = isOpen(n); if (o != null && before.containsKey(n) && before[n] != o) n to o else null }
    }

    /** Minutes since the report, for "updated 6 min ago". */
    val minutesAgo: Long get() = report?.let { ((now - it.updated) / 60_000.0).let { m -> Math.round(m) } } ?: 0

    companion object {
        const val STALE_MS = 30 * 60_000L
        const val EVERY_MS = 5 * 60_000L

        /** The lifts on the board: the main side's named lifts that run (the site's names). */
        fun names(mainLifts: List<io.github.pini236.skiapp.data.Lift>): List<String> =
            mainLifts.filter { it.name.isNotBlank() && it.status != "inactive" }.map { it.name }

        /** In season (December to April) "no current information"; out of it, the mountain sleeps. */
        fun inSeason(month: Int) = month in listOf(12, 1, 2, 3, 4)
    }
}

/**
 * Where the report comes from: the site's /api/status, read again every five minutes while the app is open, with the
 * last good one kept on the phone, so a restart on the mountain without reception still shows it while it is fresh.
 * Out of season the site is asked once a day at most (R-18: it used to be every five minutes all year), so an early
 * opening still shows within a day.
 */
class StatusSource(ctx: Context, private val fetch: SiteData.Fetcher = HttpFetcher()) {
    private val prefs = ctx.getSharedPreferences("lstat", Context.MODE_PRIVATE)

    fun cached(): Report? = prefs.getString("report", null)?.let { Report.parse(it) }

    /** Ask the site; on any failure the cached report (checked for freshness by [LiftStatus] like any other). */
    fun load(inSeason: Boolean = true, now: Long = System.currentTimeMillis()): Report? {
        if (!inSeason && now - prefs.getLong("asked", 0L) in 0 until DAY_MS) return cached()
        prefs.edit().putLong("asked", now).apply()
        val got = runCatching { fetch.get(URL, null) }.getOrNull()
        val r = got?.takeIf { it.code == 200 }?.body?.let { String(it, Charsets.UTF_8) }?.takeIf { it.trimStart().startsWith("{") }?.let { Report.parse(it) }
        if (r != null) prefs.edit().putString("report", r.json).apply()
        return r ?: cached()
    }

    /** The state the board showed last time ("opened since you checked"). */
    fun before(): Map<String, Boolean?>? = prefs.getString("seen", null)?.let { s ->
        runCatching { val o = JSONObject(s); o.keys().asSequence().associateWith { k -> if (o.isNull(k)) null else o.getBoolean(k) } }.getOrNull()
    }

    fun seen(state: Map<String, Boolean?>) {
        val o = JSONObject(); state.forEach { (k, v) -> o.put(k, v ?: JSONObject.NULL) }
        prefs.edit().putString("seen", o.toString()).apply()
    }

    /** The emulator run: a report of its own, no reading from the site. */
    fun pin(r: Report?, before: Map<String, Boolean?>?) {
        prefs.edit().clear().apply()
        r?.let { prefs.edit().putString("report", it.json).apply() }
        before?.let { seen(it) }
    }

    companion object {
        const val URL = "https://gudauri-ski-trip.vercel.app/api/status"
        const val DAY_MS = 24 * 3600_000L
    }
}

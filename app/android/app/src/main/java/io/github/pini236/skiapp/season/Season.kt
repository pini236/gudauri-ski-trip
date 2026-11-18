package io.github.pini236.skiapp.season

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The countdown to the season's opening (round 39, approved 18.11.2026; the architect's review in docs/ARCHITECTURE.md).
 * The date is `season.open` in resorts.json: a LOCAL date in the resort's time zone (`tz`, Asia/Tbilisi), never read from
 * the phone's clock, so a phone on another zone counts the same days as the site. The rules, in order (the shared cases
 * are tools/fixtures/season-countdown.json, and the same file is tested by the site's checker and the iPhone):
 * a fresh lift report is the lift board; no readable date is no countdown; days before the date is the countdown, the
 * day itself is "opening day", and after it, with no report, "no information" (never "delayed").
 */
class Season(val open: LocalDate, val announced: LocalDate?, val src: String?, val url: String?, val updated: LocalDate?, val tz: ZoneId) {
    enum class Kind { NONE, COUNTDOWN, OPENING_DAY, NO_INFO, LIVE }

    /** [days] is set for COUNTDOWN (above 0) and OPENING_DAY (0); [postponed] only while counting down. */
    class State(val kind: Kind, val days: Int?, val postponed: Boolean)

    /** Today in the resort's own calendar. */
    fun today(nowMs: Long): LocalDate = Instant.ofEpochMilli(nowMs).atZone(tz).toLocalDate()

    companion object {
        const val FEATURE_RESORT = "gudauri"
        private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val DEFAULT_TZ: ZoneId = ZoneId.of("Asia/Tbilisi")

        /** A real calendar date written YYYY-MM-DD, otherwise null ("20.12.2026" and "2026-12-20T09:00" are not). */
        fun localDate(s: String?): LocalDate? = if (s != null && DATE.matches(s)) runCatching { LocalDate.parse(s) }.getOrNull() else null

        /** The resort's `season` from resorts.json; null when it has none, or `open` is not a readable date (then no countdown). */
        fun parse(resortsJson: String?, resort: String = FEATURE_RESORT): Season? = runCatching {
            val all = JSONObject(resortsJson ?: return null).getJSONArray("resorts")
            val r = (0 until all.length()).map { all.getJSONObject(it) }.firstOrNull { it.optString("id") == resort } ?: return null
            from(r.optJSONObject("season") ?: return null, r.optString("tz"))
        }.getOrNull()

        /** The `season` object itself (the shared cases give it without the rest of the resort). */
        fun from(s: JSONObject, tz: String? = null): Season? {
            val open = localDate(s.optString("open").takeIf { !s.isNull("open") }) ?: return null
            val zone = runCatching { ZoneId.of(tz) }.getOrDefault(DEFAULT_TZ)
            return Season(open, localDate(s.optString("announced").takeIf { !s.isNull("announced") }),
                s.optString("src").takeIf { it.isNotBlank() }, s.optString("url").takeIf { it.startsWith("https://") },
                localDate(s.optString("updated").takeIf { !s.isNull("updated") }), zone)
        }

        /** The rules above. [season] is null when the file has none or no readable date; [seenOpen] is the date this phone saw before. */
        fun decide(season: Season?, today: LocalDate, fresh: Boolean, seenOpen: LocalDate?): State {
            if (fresh) return State(Kind.LIVE, null, false)
            if (season == null) return State(Kind.NONE, null, false)
            val days = (season.open.toEpochDay() - today.toEpochDay()).toInt()
            return when {
                days > 0 -> State(Kind.COUNTDOWN, days, seenOpen != null && seenOpen.isBefore(season.open))
                days == 0 -> State(Kind.OPENING_DAY, 0, false)
                else -> State(Kind.NO_INFO, null, false)
            }
        }
    }
}

/**
 * The opening date this phone saw last, kept only here (the site has no `prev` field), to tell "the opening moved" from a
 * date that was never different: a new user sees the date only. Stored when the board is shown.
 */
class SeasonSeen(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("season", Context.MODE_PRIVATE)

    fun open(): LocalDate? = Season.localDate(prefs.getString("seen_open", null))

    /** Remembers [open], and never lets a date that was seen earlier be replaced by an earlier one (a rollback would hide a move). */
    fun remember(open: LocalDate) {
        val was = open()
        if (was == null || open.isAfter(was)) prefs.edit().putString("seen_open", open.toString()).apply()
    }

    /** The emulator run: a state of its own. */
    fun pin(open: LocalDate?) { prefs.edit().apply { if (open == null) remove("seen_open") else putString("seen_open", open.toString()) }.apply() }
}

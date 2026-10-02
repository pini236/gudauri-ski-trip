package io.github.pini236.skiapp.trip

import android.content.Context
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * "Your trip" (decision 27, round 10 H1 to H4): the user's own flights, kept on the phone only, without signing up.
 * The crew's flight and names are never packed into the app (docs/USERS.md); this is whatever the user types.
 * Only the outbound date is required (H2). Times are as printed on the ticket, local to each airport.
 */
data class Flight(
    val date: LocalDate,
    val number: String = "",
    val from: String = "",
    val to: String = "",
    val departs: LocalTime? = null,
    val arrives: LocalTime? = null,
)

data class Trip(val out: Flight, val back: Flight? = null) {

    /**
     * The full ski days: from the day after landing (the same day when landing before noon) to the day before the
     * return flight (the same day when it leaves in the evening). A return in the small hours (the crew's 01:35)
     * leaves the evening before free: the last ski day is the day before. Null when there is no return yet.
     */
    fun skiDays(): Pair<LocalDate, LocalDate>? {
        val back = back ?: return null
        val landed = out.arrives ?: out.departs
        val first = if (landed != null && landed.isBefore(LocalTime.NOON)) out.date else out.date.plusDays(1)
        val leaves = back.departs
        val last = if (leaves != null && !leaves.isBefore(LocalTime.of(18, 0))) back.date else back.date.minusDays(1)
        return if (last.isBefore(first)) null else first to last
    }

    /** Days to the outbound flight, rounded up as on the site (0 once it has left). */
    fun daysTo(now: LocalDateTime): Long {
        val at = out.date.atTime(out.departs ?: LocalTime.MIDNIGHT)
        val mins = Duration.between(now, at).toMinutes()
        return if (mins <= 0) 0 else (mins + 24 * 60 - 1) / (24 * 60)
    }

    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("out", flightJson(out))
        back?.let { put("back", flightJson(it)) }
    }.toString()

    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private fun flightJson(f: Flight) = JSONObject().apply {
            put("date", f.date.format(DATE))
            if (f.number.isNotBlank()) put("number", f.number)
            if (f.from.isNotBlank()) put("from", f.from)
            if (f.to.isNotBlank()) put("to", f.to)
            f.departs?.let { put("departs", it.format(TIME)) }
            f.arrives?.let { put("arrives", it.format(TIME)) }
        }

        private fun flight(o: JSONObject) = Flight(
            date = LocalDate.parse(o.getString("date"), DATE),
            number = o.optString("number"),
            from = o.optString("from"),
            to = o.optString("to"),
            departs = o.optString("departs").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it, TIME) },
            arrives = o.optString("arrives").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it, TIME) },
        )

        /** A saved trip, or null when there is none or it cannot be read (never a crash). */
        fun fromJson(s: String?): Trip? = runCatching {
            val o = JSONObject(s ?: return null)
            Trip(flight(o.getJSONObject("out")), o.optJSONObject("back")?.let { flight(it) })
        }.getOrNull()

        /** "10.1.2027", "10.1.27" or "10/1/2027". Null when it is not a real date. */
        fun parseDate(s: String): LocalDate? {
            val m = Regex("""^\s*(\d{1,2})[./-](\d{1,2})[./-](\d{2}|\d{4})\s*$""").find(s) ?: return null
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
            return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
        }

        /** "16:00" or "1600". Null when it is not a time. */
        fun parseTime(s: String): LocalTime? {
            val m = Regex("""^\s*(\d{1,2}):?(\d{2})\s*$""").find(s) ?: return null
            val (h, mi) = m.destructured
            return runCatching { LocalTime.of(h.toInt(), mi.toInt()) }.getOrNull()
        }

        fun showDate(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}.${d.year}"
        fun showDay(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}"
        fun showTime(t: LocalTime?) = t?.format(TIME) ?: ""
    }
}

/** The airport code for the big letters on the pass: the first three Latin letters typed ("TLV · Tel Aviv" -> TLV). */
fun airportCode(s: String): String = Regex("[A-Za-z]{3}").find(s)?.value?.uppercase() ?: ""

/** The rest of what was typed, after the code ("TLV · תל אביב" -> "תל אביב"). */
fun airportName(s: String): String = s.replace(Regex("^\\s*[A-Za-z]{3}\\s*[·:,-]?\\s*"), "").trim()

/**
 * Kept on the phone only, in the app's own preferences. System backup (allowBackup) may copy it to the user's Google
 * account and back on a new phone, as the privacy policy says; nothing is sent to us.
 */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)
    fun load(): Trip? = Trip.fromJson(prefs.getString("trip", null))
    fun save(t: Trip) = prefs.edit().putString("trip", t.toJson()).apply()
    fun clear() = prefs.edit().remove("trip").apply()
}

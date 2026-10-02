package io.github.pini236.skiapp.trip

import android.content.Context
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * "Your trip" (decision 27, screens H1 to H4): one person's own flights, kept only on this phone, no sign-up.
 * Nothing about the group is packed in the app (trip.json never is); the group's flights come back through the
 * group (stage 13.5).
 */
data class Leg(
    val date: LocalDate,
    val flight: String = "",
    val from: String = "",
    val to: String = "",
    val departs: LocalTime? = null,
    val arrives: LocalTime? = null,
) {
    /** "TLV · Tel Aviv" (or "TLV Tel Aviv") -> TLV and Tel Aviv; a place typed without an airport code has none. */
    val fromCode: String? get() = code(from)
    val toCode: String? get() = code(to)
    val fromCity: String get() = city(from)
    val toCity: String get() = city(to)

    // three letters alone or before "·" ("tlv", "TLV · Tel Aviv"), or three capitals before a space ("TLV Tel Aviv");
    // "Tel Aviv" is a city, not the code TEL
    private fun code(s: String) = (Regex("^\\s*([A-Za-z]{3})\\s*(·|$)").find(s) ?: Regex("^\\s*([A-Z]{3})\\s").find(s))?.groupValues?.get(1)?.uppercase()
    private fun city(s: String) = if (code(s) != null) s.trim().drop(3).trimStart(' ', '·').trim() else s.trim()
}

data class Trip(val out: Leg, val ret: Leg? = null, val ski: ClosedRange<LocalDate>? = null) {

    /** The ski days: the ones the user set by hand ("שינוי" in H2), or the full days the flights leave. */
    fun skiDays(): ClosedRange<LocalDate>? = ski ?: flightSkiDays()

    /**
     * Full ski days: from the day after landing (the same day if you land by 09:00) to the day before the return
     * flight (the same day if it leaves at 18:00 or later). The group's trip, landing 10.1 at 20:35 and flying back
     * 15.1 at 01:35, gives 11 to 14 January, four days, as on the site. Null when there is no return, or no full day.
     */
    fun flightSkiDays(): ClosedRange<LocalDate>? {
        val r = ret ?: return null
        val dep = out.departs
        val arr = out.arrives
        // an overnight flight (lands earlier on the clock than it left) lands the next day
        val landed = if (dep != null && arr != null && arr < dep) out.date.plusDays(1) else out.date
        val first = if (arr != null && arr <= LocalTime.of(9, 0)) landed else landed.plusDays(1)
        val last = if (r.departs != null && r.departs >= LocalTime.of(18, 0)) r.date else r.date.minusDays(1)
        return if (first <= last) first..last else null
    }

    fun skiDayCount(): Int = skiDays()?.let { (it.endInclusive.toEpochDay() - it.start.toEpochDay() + 1).toInt() } ?: 0

    /** Whole days until the outbound flight, rounded up as on the site (0 once it has left). */
    fun daysToFlight(now: LocalDateTime): Int {
        val dep = LocalDateTime.of(out.date, out.departs ?: LocalTime.MIDNIGHT)
        val mins = Duration.between(now, dep).toMinutes()
        return if (mins <= 0) 0 else ((mins + 24 * 60 - 1) / (24 * 60)).toInt()
    }

    fun toJson(): JSONObject = JSONObject().put("v", 1).put("out", leg(out)).apply {
        ret?.let { put("ret", leg(it)) }
        ski?.let { put("ski", JSONObject().put("from", it.start.toString()).put("to", it.endInclusive.toString())) }
    }

    companion object {
        private val T = DateTimeFormatter.ofPattern("HH:mm")

        private fun leg(l: Leg) = JSONObject().put("date", l.date.toString()).put("flight", l.flight).put("from", l.from).put("to", l.to)
            .put("departs", l.departs?.format(T) ?: "").put("arrives", l.arrives?.format(T) ?: "")

        // "number" and "back": how the parallel build of 2.10.2026 saved a trip (it was on the install link for a
        // few minutes); read too, so a trip saved there is not lost
        private fun leg(o: JSONObject) = Leg(
            LocalDate.parse(o.getString("date")), o.optString("flight").ifBlank { o.optString("number") }, o.optString("from"), o.optString("to"),
            o.optString("departs").takeIf { it.isNotBlank() }?.let { LocalTime.parse(it, T) },
            o.optString("arrives").takeIf { it.isNotBlank() }?.let { LocalTime.parse(it, T) },
        )

        /** A saved trip, or null for anything odd (a newer format, a damaged file): the home page then invites to add one. */
        fun fromJson(s: String?): Trip? = runCatching {
            val o = JSONObject(s ?: return null)
            if (o.optInt("v", 1) > 1) return null
            val ski = o.optJSONObject("ski")?.let { LocalDate.parse(it.getString("from"))..LocalDate.parse(it.getString("to")) }?.takeIf { it.start <= it.endInclusive }
            Trip(leg(o.getJSONObject("out")), (o.optJSONObject("ret") ?: o.optJSONObject("back"))?.let { leg(it) }, ski)
        }.getOrNull()
    }
}

/** Kept only on this phone (SharedPreferences), and gone with "delete the trip" or the app. */
class TripStore(context: Context) {
    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)
    fun load(): Trip? = Trip.fromJson(prefs.getString("trip", null))
    fun save(t: Trip) = prefs.edit().putString("trip", t.toJson().toString()).apply()
    fun clear() = prefs.edit().remove("trip").apply()
}

/**
 * The form's words to values and back (H2): dates as people write them here ("10.1.2027", "10/1/27", "10.1" for the
 * next 10 January) and times ("16:00", "16.00", "1600", "16"). Blank is null; anything unreadable is [Bad].
 */
object TripText {
    object Bad

    fun date(s: String, today: LocalDate): Any? {
        val t = s.trim()
        if (t.isEmpty()) return null
        runCatching { return LocalDate.parse(t) } // 2027-01-10
        val m = Regex("^(\\d{1,2})[./-](\\d{1,2})(?:[./-](\\d{2}|\\d{4}))?$").find(t) ?: return Bad
        val (d, mo, y) = m.destructured
        return runCatching {
            if (y.isEmpty()) {
                val thisYear = LocalDate.of(today.year, mo.toInt(), d.toInt())
                if (thisYear < today) thisYear.plusYears(1) else thisYear
            } else LocalDate.of(if (y.length == 2) 2000 + y.toInt() else y.toInt(), mo.toInt(), d.toInt())
        }.getOrElse { Bad }
    }

    fun time(s: String): Any? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val m = Regex("^(\\d{1,2})(?:[:.]?(\\d{2}))?$").find(t) ?: return Bad
        val (h, mi) = m.destructured
        return runCatching { LocalTime.of(h.toInt(), if (mi.isEmpty()) 0 else mi.toInt()) }.getOrElse { Bad }
    }

    fun date(d: LocalDate?) = d?.let { "${it.dayOfMonth}.${it.monthValue}.${it.year}" } ?: ""
    fun time(t: LocalTime?) = t?.let { "%02d:%02d".format(it.hour, it.minute) } ?: ""
}

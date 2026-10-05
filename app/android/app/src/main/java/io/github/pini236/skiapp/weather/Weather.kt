package io.github.pini236.skiapp.weather

import android.content.Context
import io.github.pini236.skiapp.data.HttpFetcher
import io.github.pini236.skiapp.data.SiteData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.floor

/**
 * Weather by altitude (round 19, decision 58; docs/ARCHITECTURE.md m-6): the server fetches Open-Meteo once an hour
 * for three points and the ridge, and every client reads its one answer at /api/weather. The client does not compute
 * the risk or know its thresholds; it only picks the hour, keeps the last answer, and says how old it is, by the
 * rules in tools/fixtures/weather-m6.json (the site reads the same file in its tests).
 */
class Forecast(val updated: Instant, val points: List<Point>, private val ridgeDates: List<LocalDate>, private val risk: Map<String, List<String>>, val json: String) {

    class Point(val id: String, val elevation: Int, val snow24: Double, val hours: List<LocalDateTime>, val h: Map<String, DoubleArray>,
                val days: List<LocalDate>, val d: Map<String, DoubleArray>)

    /** What a point (or an altitude between them) has at one hour. */
    data class Now(val temp: Int, val wind: Int, val gust: Int, val dir: Int, val snow24: Int)

    /** One day of a point (the days board before the trip). */
    data class Day(val date: LocalDate, val tmin: Int, val tmax: Int, val snow: Int, val wind: Int, val gust: Int, val dir: Int)

    enum class State { FRESH, STALE, NONE }

    fun state(now: Instant): State {
        val ms = now.toEpochMilli() - updated.toEpochMilli()
        return when {
            ms <= FRESH_MS -> State.FRESH
            ms <= STALE_MS -> State.STALE
            else -> State.NONE
        }
    }

    fun point(id: String) = points.firstOrNull { it.id == id }

    /** The values of the hour that has begun (11:25 reads 11:00); null when that hour is not in the answer. */
    fun now(id: String, at: LocalDateTime): Now? {
        val p = point(id) ?: return null
        val i = p.hours.indexOf(at.withMinute(0).withSecond(0).withNano(0)).takeIf { it >= 0 } ?: return null
        return Now(round(p.h.getValue("temp")[i]), round(p.h.getValue("wind")[i]), round(p.h.getValue("gust")[i]), p.h.getValue("dir")[i].toInt(), round(p.snow24))
    }

    /** At an altitude (a run's top or bottom): linear between the points above and below, the nearest one outside them. */
    fun at(alt: Float, at: LocalDateTime): Now? {
        val ps = points.sortedBy { it.elevation }.mapNotNull { p -> raw(p, at)?.let { p.elevation to it } }
        if (ps.size < points.size || ps.isEmpty()) return null
        if (alt <= ps.first().first) return ps.first().second.rounded()
        if (alt >= ps.last().first) return ps.last().second.rounded()
        for (i in 0 until ps.size - 1) {
            val (ea, a) = ps[i]; val (eb, b) = ps[i + 1]
            if (alt >= ea && alt <= eb) {
                val t = (alt - ea) / (eb - ea).toDouble()
                fun mix(x: Double, y: Double) = x + t * (y - x)
                return Now(round(mix(a[0], b[0])), round(mix(a[1], b[1])), round(mix(a[2], b[2])), (if (t < .5) a[3] else b[3]).toInt(), round(mix(a[4], b[4])))
            }
        }
        return null
    }

    private fun raw(p: Point, at: LocalDateTime): DoubleArray? {
        val i = p.hours.indexOf(at.withMinute(0).withSecond(0).withNano(0)).takeIf { it >= 0 } ?: return null
        return doubleArrayOf(p.h.getValue("temp")[i], p.h.getValue("wind")[i], p.h.getValue("gust")[i], p.h.getValue("dir")[i], p.snow24)
    }
    private fun DoubleArray.rounded() = Now(round(this[0]), round(this[1]), round(this[2]), this[3].toInt(), round(this[4]))

    fun day(id: String, date: LocalDate): Day? {
        val p = point(id) ?: return null
        val i = p.days.indexOf(date).takeIf { it >= 0 } ?: return null
        fun v(k: String) = round(p.d.getValue(k)[i])
        return Day(date, v("tmin"), v("tmax"), v("snow"), v("wind"), v("gust"), p.d.getValue("dir")[i].toInt())
    }

    /** The last day the answer reaches (the days board: "opens on" for the days after it). */
    val lastDay: LocalDate? get() = points.firstOrNull()?.days?.lastOrNull()
    val days: Int get() = points.firstOrNull()?.days?.size ?: 0

    /** The server's daily estimate for a lift ("low", "medium", "high"), or null. */
    fun risk(lift: String, date: LocalDate): String? {
        val i = ridgeDates.indexOf(date).takeIf { it >= 0 } ?: return null
        return risk[lift]?.getOrNull(i)?.takeIf { it in LEVELS }
    }

    companion object {
        val GUDAURI: ZoneId = ZoneId.of("Asia/Tbilisi")
        const val FRESH_MS = 12 * 3600_000L
        const val STALE_MS = 48 * 3600_000L
        val LEVELS = listOf("low", "medium", "high")
        /** The three points, top down, as the lists show them. */
        val POINTS = listOf("sadzele", "goodaura", "village")
        /** The lifts whose closing in the wind the boards estimate (FEATURES section c). */
        val RISK_LIFTS = listOf("Sadzele", "Kudebi")

        /** Half up, as JavaScript's Math.round (the site's rounding; -2.5 is -2). */
        fun round(x: Double): Int = floor(x + .5).toInt()

        private fun JSONArray.doubles() = DoubleArray(length()) { optDouble(it, Double.NaN) }
        private fun JSONArray.strings() = (0 until length()).map { optString(it) }

        fun parse(json: String): Forecast? = runCatching {
            val o = JSONObject(json)
            if (o.optInt("schema", 0) != 1) return null
            val pts = o.getJSONArray("points")
            val points = (0 until pts.length()).map { i ->
                val p = pts.getJSONObject(i)
                val h = p.getJSONObject("hourly"); val d = p.getJSONObject("daily")
                Point(p.getString("id"), p.getInt("elevation"), p.optDouble("snow24", 0.0),
                    h.getJSONArray("time").strings().map { LocalDateTime.parse(it) },
                    listOf("temp", "wind", "gust", "dir").associateWith { h.getJSONArray(it).doubles() },
                    d.getJSONArray("date").strings().map { LocalDate.parse(it) },
                    listOf("tmin", "tmax", "snow", "wind", "gust", "dir").associateWith { d.getJSONArray(it).doubles() })
            }
            if (points.isEmpty() || points.any { p -> p.h.values.any { it.size != p.hours.size } || p.d.values.any { it.size != p.days.size } }) return null
            val ridge = o.optJSONObject("ridge")?.optJSONObject("daily")
            val dates = ridge?.optJSONArray("date")?.strings()?.map { LocalDate.parse(it) }.orEmpty()
            val risk = ridge?.optJSONObject("risk")?.let { r -> r.keys().asSequence().associateWith { r.getJSONArray(it).strings() } }.orEmpty()
            Forecast(Instant.parse(o.getString("updated")), points, dates, risk, json)
        }.getOrNull()
    }
}

/**
 * Where the forecast comes from: the site's /api/weather (which leads to the server), at most every ten minutes while
 * the app is open, and the last good answer kept in a file, so the mountain without reception still shows it with
 * how old it is (m-6 section 4). Nothing about the person goes with the request.
 */
class WeatherSource(ctx: Context, private val fetch: SiteData.Fetcher = HttpFetcher()) {
    private val file = File(ctx.filesDir, "weather.json")
    private val prefs = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(): Forecast? = runCatching { file.readText() }.getOrNull()?.let { Forecast.parse(it) }

    fun load(now: Long = System.currentTimeMillis()): Forecast? {
        if (prefs.getBoolean("pinned", false) || now - prefs.getLong("asked", 0L) in 0 until EVERY_MS) return cached()
        prefs.edit().putLong("asked", now).apply()
        val got = runCatching { fetch.get(URL, null) }.getOrNull()
        val text = got?.takeIf { it.code == 200 }?.body?.let { String(it, Charsets.UTF_8) }?.takeIf { it.trimStart().startsWith("{") }
        val f = text?.let { Forecast.parse(it) }
        val old = cached()
        // never a forecast older than the one already here
        if (f != null && (old == null || !f.updated.isBefore(old.updated))) {
            runCatching { val tmp = File(file.parentFile, file.name + ".tmp"); tmp.writeText(text); tmp.renameTo(file) }
            return f
        }
        return old
    }

    /** The emulator run: an answer of its own, never the site. */
    fun pin(json: String?) {
        prefs.edit().putBoolean("pinned", true).apply()
        if (json == null) file.delete() else file.writeText(json)
    }

    companion object {
        const val URL = "https://gudauri-ski-trip.vercel.app/api/weather"
        const val EVERY_MS = 10 * 60_000L
    }
}

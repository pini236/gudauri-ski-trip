package io.github.pini236.skiapp.meet

import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.nav.Route
import io.github.pini236.skiapp.trip.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A place to meet: one end of a lift, or the shared end of several (design round 3, M1 to M3; the site's MEET in
 * site/js/app.js). Its [id] is the first lift's id and "b" (bottom) or "t" (top), exactly as the site makes it, because
 * a meetup in a group keeps this id (server/CONTRACT.md, meetups.station) and the site and the app show each other's.
 */
class Station(val id: String, val x: Float, val y: Float, val h: Int?, val ends: List<End>) {
    class End(val lift: Lift, val top: Boolean)

    /** The site's name: the first lift that starts here, or else the first that ends here. */
    val name: String get() = (ends.firstOrNull { !it.top } ?: ends.first()).lift.name
    val bottomOf: List<String> get() = ends.filter { !it.top }.map { it.lift.name }
    val topOf: List<String> get() = ends.filter { it.top }.map { it.lift.name }
}

/** The group's three spots in one tap (M2), as on the site: the morning lift, noon up top, the end of the day. */
enum class Preset(val lift: String, val top: Boolean, val time: String, val event: String) {
    MORNING("Goodaura", false, "09:30", "morning"),
    NOON("Goodaura", true, "13:00", "noon"),
    END("New Goodaura", false, "16:30", "end"),
}

/** How to get to a station, only from the connections in the data (M3): where from, then the runs or the lift. */
class Way(val from: From, val hops: List<Hop>) {
    sealed interface From {
        data class Run(val key: String) : From
        data class TopOf(val key: String) : From
        data class BottomOf(val lift: String) : From
    }
    sealed interface Hop {
        data class Run(val piste: Piste) : Hop
        data class Lift(val name: String) : Hop
    }
    internal val sameAs: String get() = hops.joinToString("|") { if (it is Hop.Run) "r:" + it.piste.key else "l:" + (it as Hop.Lift).name }
}

/** The stations of the mountain and what is known about each, built once from the site's data. */
class MeetPlan(val stations: List<Station>, private val runs: Runs) {
    val byId: Map<String, Station> = stations.associateBy { it.id }
    private val byKey = runs.pistes.associateBy { it.key }

    fun preset(p: Preset): Station? = stations.firstOrNull { s -> s.ends.any { it.lift.name == p.lift && it.top == p.top } }

    /** The site's routes(): runs that end at a lift starting here (with the run before them), or the lift up to here. */
    fun ways(s: Station): List<Way> {
        val out = ArrayList<Way>()
        for (e in s.ends) {
            if (!e.top) {
                for (p in runs.pistes) {
                    if (!p.named || e.lift.name !in p.toLifts) continue
                    val before = p.fromPistes.mapNotNull { byKey[it] }.firstOrNull { it.named && it.key != p.key }
                    out += Way(if (before != null) Way.From.Run(before.key) else Way.From.TopOf(p.key),
                        listOfNotNull(before, p).map { Way.Hop.Run(it) })
                }
            } else out += Way(Way.From.BottomOf(e.lift.name), listOf(Way.Hop.Lift(e.lift.name)))
        }
        return out.distinctBy { it.sameAs }.take(4)
    }

    companion object {
        /** Ends of lifts closer than this are one station (shared top stations). */
        const val MERGE_M = 70f

        /** Both ends of every named lift on the main side that runs, merged when close, the lower end "b". */
        fun build(runs: Runs, t: Terrain): MeetPlan {
            class Open(val id: String, val x: Float, val y: Float, val h: Int, val ends: MutableList<Station.End>)
            val st = ArrayList<Open>()
            for (l in runs.mainLifts) {
                if (l.name.isEmpty() || l.status == "inactive" || l.pts.size < 4) continue
                val n = l.pts.size
                var lo = floatArrayOf(l.pts[0], l.pts[1]); var hi = floatArrayOf(l.pts[n - 2], l.pts[n - 1])
                if (t.elev(lo[0], lo[1]) > t.elev(hi[0], hi[1])) { val k = lo; lo = hi; hi = k }
                for ((q, top) in listOf(lo to false, hi to true)) {
                    val near = st.firstOrNull { hypot(it.x - q[0], it.y - q[1]) < MERGE_M }
                    if (near != null) { near.ends += Station.End(l, top); continue }
                    st += Open(l.id + if (top) "t" else "b", q[0], q[1], t.elev(q[0], q[1]).roundToInt(), mutableListOf(Station.End(l, top)))
                }
            }
            return MeetPlan(st.map { Station(it.id, it.x, it.y, it.h, it.ends) }, runs)
        }
    }
}

/** The rules of the meeting point that are not drawing: the times on offer, the days, the countdown, the link. */
object Meet {
    /** Gudauri's clock (UTC+4, no summer time): the site counts down in it, and a meetup is saved with it. */
    val GUDAURI: ZoneOffset = ZoneOffset.ofHours(4)
    val TIMES = listOf("09:30", "11:00", "12:30", "13:30", "15:00", "16:30")
    const val DEFAULT_TIME = "12:30"

    /** The ski days of "your trip" (up to two weeks), or, with none, the next week from today in Gudauri. */
    fun days(trip: Trip?, now: Instant): List<LocalDate> {
        val r = trip?.skiDays()
        var a: LocalDate; val b: LocalDate
        if (r != null) { a = r.start; b = r.endInclusive } else {
            a = now.atOffset(GUDAURI).toLocalDate()
            if (trip != null && trip.out.date > a) a = trip.out.date.plusDays(1)
            b = a.plusDays(6)
        }
        return generateSequence(a) { it.plusDays(1) }.takeWhile { it <= b }.take(14).toList()
    }

    fun at(day: LocalDate, time: LocalTime): Instant = day.atTime(time).toInstant(GUDAURI)

    sealed interface Left {
        data object Passed : Left
        data class Minutes(val n: Int) : Left
        /** hours and minutes, "1:05" */
        data class Hours(val hm: String) : Left
        data class Days(val n: Int) : Left
    }

    /** As the site counts (site/js/app.js countdown()): minutes under an hour, h:mm under a day, then whole days up. */
    fun left(at: Instant, now: Instant): Left {
        val diff = (at.toEpochMilli() - now.toEpochMilli()) / 60000.0
        return when {
            diff < 0 -> Left.Passed
            diff < 60 -> Left.Minutes(diff.roundToInt())
            diff < 24 * 60 -> { val m = diff.roundToInt(); Left.Hours("${m / 60}:${"%02d".format(m % 60)}") }
            else -> Left.Days(ceil(diff / 1440).toInt())
        }
    }

    /** The site's link to this meeting point; the app reads the same link back (nav/Nav.kt, Route.Meet). */
    fun link(station: String, time: LocalTime, day: LocalDate): String =
        "https://${Route.SITE_HOST}/#" + route(station, time, day).path

    fun route(station: String, time: LocalTime, day: LocalDate): Route.Meet =
        Route.Meet(station, "%02d%02d".format(time.hour, time.minute), "%04d%02d%02d".format(day.year, day.monthValue, day.dayOfMonth))

    /** "0930" and "20270111" from a link, or null when they do not read as a time and a date. */
    fun time(hhmm: String?): LocalTime? = hhmm?.takeIf { it.length == 4 }?.let { runCatching { LocalTime.of(it.take(2).toInt(), it.drop(2).toInt()) }.getOrNull() }
    fun day(yyyymmdd: String?): LocalDate? = yyyymmdd?.takeIf { it.length == 8 }?.let {
        runCatching { LocalDate.of(it.take(4).toInt(), it.substring(4, 6).toInt(), it.drop(6).toInt()) }.getOrNull()
    }
    fun parseTime(s: String): LocalTime = LocalTime.of(s.take(2).toInt(), s.takeLast(2).toInt())
}

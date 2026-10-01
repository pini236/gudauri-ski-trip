package com.pini.gudauri.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class StationEnd(val suffix: String) { BOTTOM("b"), TOP("t") }
data class LiftEnd(val lift: Lift, val end: StationEnd) {
    val alias: String get() = "${lift.id}${end.suffix}"
}
data class MeetingStation(val id: String, val point: Point, val elevation: Int, val ends: List<LiftEnd>) {
    val name: String get() = (ends.firstOrNull { it.end == StationEnd.BOTTOM } ?: ends.first()).lift.name.orEmpty()
    val description: String get() = listOf(StationEnd.BOTTOM to "התחנה התחתונה של", StationEnd.TOP to "התחנה העליונה של")
        .mapNotNull { (end, label) -> ends.filter { it.end == end }.takeIf { it.isNotEmpty() }
            ?.let { "$label ${it.joinToString(" ו-") { endpoint -> endpoint.lift.name.orEmpty() }}" } }.joinToString(", ")
}
data class MeetingChoice(val stationId: String, val day: LocalDate, val time: LocalTime) {
    init { require(time.second == 0 && time.nano == 0 && day.year in 1..9999) }
    val instant: Instant get() = day.atTime(time).atZone(ZoneId.of("Asia/Tbilisi")).toInstant()
}
data class MeetingPreset(val id: String, val label: String, val stationId: String, val time: LocalTime)
data class MeetingRoute(val from: String, val runKeys: List<String> = emptyList(), val liftId: Long? = null)

/** Derived from the same source order, projection, split and strict merge radius as the site. */
class MeetingModel(val data: MountainData) {
    private val kobiBoundary = DataParser.project(42.5115, 44.495).z
    val lifts = data.lifts.filter { it.name != null && it.status != "inactive" && it.points.map(Point::z).average() >= kobiBoundary }
    val pistes = data.pistes.filter { it.named && it.segments.flatMap { s -> s.points }.map(Point::z).average() >= kobiBoundary }
    val days: List<LocalDate> = data.trip?.skiDays?.dates.orEmpty()
    val times = listOf("09:30", "11:00", "12:30", "13:30", "15:00", "16:30").map(LocalTime::parse)
    val stations: List<MeetingStation> = buildStations()
    private val aliases = stations.flatMap { station -> (station.ends.map(LiftEnd::alias) + station.id).map { it to station } }.toMap()
    fun station(id: String): MeetingStation? = aliases[id]
    val presets: List<MeetingPreset> = listOf(
        Triple("am", "רכבל הבוקר", Triple("Goodaura", StationEnd.BOTTOM, "09:30")),
        Triple("noon", "צהריים", Triple("Goodaura", StationEnd.TOP, "13:00")),
        Triple("pm", "סוף יום", Triple("New Goodaura", StationEnd.BOTTOM, "16:30"))
    ).mapNotNull { (id, label, detail) -> stations.firstOrNull { s -> s.ends.any { it.lift.name == detail.first && it.end == detail.second } }
        ?.let { MeetingPreset(id, label, it.id, LocalTime.parse(detail.third)) } }

    private fun buildStations(): List<MeetingStation> {
        val result = mutableListOf<MeetingStation>()
        lifts.forEach { lift ->
            val a = lift.points.first(); val b = lift.points.last()
            val (low, high) = if (data.terrain.elevation(a.x, a.z) > data.terrain.elevation(b.x, b.z)) b to a else a to b
            listOf(low to StationEnd.BOTTOM, high to StationEnd.TOP).forEach { (point, end) ->
                val endpoint = LiftEnd(lift, end)
                val index = result.indexOfFirst { hypot(it.point.x-point.x, it.point.z-point.z) < 70f }
                if (index >= 0) result[index] = result[index].copy(ends = result[index].ends + endpoint)
                else result.add(MeetingStation(endpoint.alias, point, data.terrain.elevation(point.x, point.z).roundToInt(), listOf(endpoint)))
            }
        }
        return result
    }

    /** Source connections only: not turn-by-turn navigation or a promise of open lifts. */
    fun routes(station: MeetingStation): List<MeetingRoute> = station.ends.flatMap { (lift, end) ->
        if (end == StationEnd.TOP) listOf(MeetingRoute("מהתחנה התחתונה של ${lift.name}", liftId=lift.id))
        else data.pistes.filter { it.named && lift.name in it.toLifts }.map { run ->
            val prior = run.fromPistes.mapNotNull(data.pistesByKey::get).firstOrNull { it.named && it.key != run.key }
            MeetingRoute(prior?.let { "מ-${it.key}" } ?: "מראש ${run.key}", listOfNotNull(prior?.key, run.key))
        }
    }.distinctBy { it.runKeys to it.liftId }.take(4)

    fun message(choice: MeetingChoice): String {
        val station = requireNotNull(station(choice.stationId))
        return "נפגשים ב-${station.name} ביום ${choice.day.format(DateTimeFormatter.ofPattern("d.M.yyyy"))} בשעה ${choice.time}.\n" +
            "${station.description}, ${station.elevation} מ׳.\nעל המפה: ${AppLinks.meeting(choice.copy(stationId=station.id))}"
    }
    companion object {
        fun countdown(choice: MeetingChoice, now: Instant): String {
            val minutes = (choice.instant.toEpochMilli()-now.toEpochMilli())/60_000.0
            return when {
                minutes < 0 -> "כבר עבר"
                minutes < 60 -> "עוד ${minutes.roundToInt()} דקות למפגש"
                minutes < 1440 -> {
                    // Carry rounded minutes rather than producing the site's edge case 1:60.
                    val rounded = minutes.roundToInt()
                    "עוד ${rounded/60}:${(rounded%60).toString().padStart(2,'0')} שעות למפגש"
                }
                else -> "עוד ${ceil(minutes/1440).toInt()} ימים למפגש"
            }
        }
    }
}

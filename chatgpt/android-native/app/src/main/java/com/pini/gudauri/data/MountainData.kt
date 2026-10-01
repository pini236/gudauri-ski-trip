package com.pini.gudauri.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.*

data class Point(val x: Float, val z: Float)
data class Segment(val id: Long, val area: Boolean, val points: List<Point>, val osmDifficulty: String = "")
data class Research(val confidence: String, val status: String, val notes: String,
    val sources: List<String>, val partial: String?, val historical: Boolean, val gps: Int,
    val userConfirmed: Boolean = false, val crossChecked: Boolean = false)
data class Piste(val key: String, val color: String, val named: Boolean, val length: Int,
    val segments: List<Segment>, val fromLifts: List<String>, val toLifts: List<String>,
    val joins: List<String>, val fromPistes: List<String>, val osmIds: List<Long>,
    val kind: String, val research: Research?, val osmDifficulty: List<String>,
    val refs: List<String>, val groom: List<String>, val lit: List<String>,
    val name: String = key, val osmNames: List<String> = emptyList())
data class Lift(val id: Long, val name: String?, val kind: String, val length: Int,
    val points: List<Point>, val status: String, val capacity: String, val duration: String,
    val occupancy: String, val year: String, val bubble: Boolean? = null, val sourceRise: String = "")
data class Video(val piste: String, val url: String, val title: String, val channel: String,
    val length: String, val confidence: String, val by: String = "", val addedAt: Long? = null,
    val published: String = "", val evidence: String = "")
data class Peak(val name: String, val height: Int, val point: Point, val pass: Boolean)
data class Place(val name: String, val point: Point)
data class Flight(val from: String, val to: String, val date: String, val flight: String,
    val departs: String, val arrives: String, val note: String,
    val fromCode: String = "", val toCode: String = "")
data class SkiDays(val from: LocalDate, val to: LocalDate) {
    init { require(ChronoUnit.DAYS.between(from, to) in 0..365) { "Invalid ski-day range" } }
    val dates: List<LocalDate> get() = List(ChronoUnit.DAYS.between(from, to).toInt() + 1) { from.plusDays(it.toLong()) }
}
data class Trip(val outbound: Flight, val inbound: Flight?, val members: List<String>,
    val destination: String = "", val airline: String = "", val baggage: String = "", val skiDays: SkiDays? = null)
data class MissingPiste(val name: String, val color: String)
enum class OptionalContent { TRIP, VIDEOS }
data class ContentWarning(val content: OptionalContent)
data class ProfileStats(val top: Int, val bottom: Int, val drop: Int, val maxGrade: Float,
    val available: Boolean = true)
data class Contour(val height: Int, val points: List<Point>)
data class Road(val kind: Int, val points: List<Point>)

class Terrain(val nx: Int, val ny: Int, val x0: Float, val z0: Float, val x1: Float,
    val z1: Float, val heights: ShortArray, val peaks: List<Peak>, val places: List<Place>,
    val contours: List<Contour>, val roads: List<Road>, val village: List<List<Point>>,
    val water: List<List<Point>>, val rivers: List<List<Point>>) {
    init {
        require(nx >= 2 && ny >= 2 && nx.toLong() * ny <= 4_000_000) { "Invalid terrain dimensions" }
        require(heights.size.toLong() == nx.toLong() * ny) { "Terrain sample count does not match dimensions" }
        require(listOf(x0, z0, x1, z1).all { it.isFinite() } && x1 > x0 && z1 > z0) { "Invalid terrain bounds" }
    }
    val sx = (x1 - x0) / (nx - 1)
    val sz = (z1 - z0) / (ny - 1)
    fun elevation(x: Float, z: Float): Float {
        val c = ((x - x0) / sx).coerceIn(0f, nx - 1.0001f)
        val r = ((z - z0) / sz).coerceIn(0f, ny - 1.0001f)
        val ci = c.toInt(); val ri = r.toInt(); val fc = c - ci; val fr = r - ri
        val i = ri * nx + ci
        return (heights[i] * (1-fc) + heights[i+1] * fc) * (1-fr) +
            (heights[i+nx] * (1-fc) + heights[i+nx+1] * fc) * fr
    }
    fun stats(segments: List<Segment>): ProfileStats {
        var top = Float.NEGATIVE_INFINITY; var bottom = Float.POSITIVE_INFINITY; var grade = 0f
        segments.filterNot { it.area }.forEach { segment ->
            val profile = profile(segment.points)
            profile.forEach { (_, h) -> top = max(top, h); bottom = min(bottom, h) }
            var j = 0
            profile.forEachIndexed { i, q ->
                while (j < profile.lastIndex && profile[j].first-q.first < 100) j++
                val length = profile[j].first-q.first
                if (length >= 80) grade = max(grade, abs(profile[j].second-q.second)/length)
            }
        }
        if (!top.isFinite()) return ProfileStats(0, 0, 0, 0f, available = false)
        return ProfileStats(top.roundToInt(), bottom.roundToInt(), (top-bottom).roundToInt(), grade)
    }
    fun profile(points: List<Point>): List<Pair<Float, Float>> {
        var distance = 0f
        return points.mapIndexed { i, p ->
            if (i > 0) distance += hypot(p.x-points[i-1].x, p.z-points[i-1].z)
            distance to elevation(p.x, p.z)
        }
    }
    /** Distance, not vertex density, chooses the line. Never bridge disconnected segments. */
    fun longestDescendingPoints(segments: List<Segment>): List<Point> {
        val points = segments.filter { !it.area && it.points.size >= 2 }
            .maxByOrNull { segment -> segment.points.zipWithNext().sumOf { (a, b) ->
                hypot((b.x - a.x).toDouble(), (b.z - a.z).toDouble())
            } }?.points.orEmpty()
        if (points.size < 2) return emptyList()
        return if (elevation(points.first().x, points.first().z) < elevation(points.last().x, points.last().z)) points.reversed() else points
    }
}

data class MountainData(val terrain: Terrain, val pistes: List<Piste>, val lifts: List<Lift>,
    val videos: List<Video>, val trip: Trip?, val fetched: String,
    val missing: List<MissingPiste> = emptyList(), val warnings: List<ContentWarning> = emptyList()) {
    val pistesByKey = pistes.associateBy { it.key }
    val stats = pistes.associate { it.key to terrain.stats(it.segments) }
}

object DataParser {
    fun project(lat: Double, lon: Double) = Point(
        ((lon-44.495)*111320*cos(42.51*PI/180)).toFloat(), (-(lat-42.51)*111320).toFloat())
    private fun JSONArray.points(): List<Point> = (0 until length()).map {
        getJSONArray(it).let { q ->
            val lat = q.getDouble(0); val lon = q.getDouble(1)
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { "Invalid geographic coordinate" }
            project(lat, lon)
        }
    }
    private fun JSONArray.flatPoints(): List<Point> {
        require(length() % 2 == 0) { "Incomplete coordinate pair" }
        return (0 until length() step 2).map {
            Point(getDouble(it).toFloat(), getDouble(it+1).toFloat()).also { p ->
                require(p.x.isFinite() && p.z.isFinite()) { "Invalid projected coordinate" }
            }
        }
    }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONObject.strings(key: String) = optJSONArray(key)?.strings().orEmpty()
    // org.json may stringify JSON null as "null". Unknown content must stay unknown.
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()
    private fun shortArray(encoded: String): ShortArray {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size % 2 == 0) { "Incomplete 16-bit terrain sample" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also { buffer.get(it) }
    }
    fun terrain(text: String): Terrain {
        val t = JSONObject(text); val d = t.getJSONObject("dem"); val env = t.getJSONObject("env")
        val peaks = t.getJSONArray("peaks").let { a -> (0 until a.length()).map { i ->
            val p=a.getJSONObject(i); Peak(p.getString("n"),p.getInt("ele"),Point(p.getDouble("x").toFloat(),p.getDouble("y").toFloat()),p.optBoolean("pass")) } }
        val places = env.getJSONArray("places").let { a -> (0 until a.length()).map { i ->
            val p=a.getJSONObject(i); Place(p.getString("n"),Point(p.getDouble("x").toFloat(),p.getDouble("y").toFloat())) } }
        val raw = shortArray(t.getString("contours")); val contours = mutableListOf<Contour>(); var i=0
        while(i<raw.size) {
            require(raw.size - i >= 4) { "Incomplete contour header" }
            val h=raw[i++].toInt(); val n=raw[i++].toInt(); var x=raw[i++].toFloat(); var z=raw[i++].toFloat()
            require(n >= 1 && (n - 1) * 2 <= raw.size - i) { "Incomplete contour geometry" }
            val points=mutableListOf(Point(x,z))
            repeat(n-1) { x+=raw[i++]; z+=raw[i++]; points.add(Point(x,z)) }
            contours.add(Contour(h,points))
        }
        fun areas(key:String) = env.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONArray(it).flatPoints() } }
        val roads=env.getJSONArray("roads").let { a -> (0 until a.length()).map { j -> a.getJSONArray(j).let { Road(it.getInt(0),it.getJSONArray(1).flatPoints()) } } }
        val water=env.getJSONArray("water").let { a -> (0 until a.length()).map { a.getJSONObject(it).getJSONArray("g").flatPoints() } }
        return Terrain(d.getInt("nx"),d.getInt("ny"),d.getDouble("x0").toFloat(),d.getDouble("y0").toFloat(),d.getDouble("x1").toFloat(),d.getDouble("y1").toFloat(),shortArray(d.getString("b64")),peaks,places,contours,roads,areas("village"),water,areas("rivers"))
    }
    fun parse(runs: String, terrain: String, videos: String? = null, trip: String? = null): MountainData {
        val d=JSONObject(runs)
        val pistes=d.getJSONArray("pistes").let { a -> (0 until a.length()).map { i ->
            val p=a.getJSONObject(i)
            val segments=p.getJSONArray("segs").let { s -> (0 until s.length()).map { j -> s.getJSONObject(j).let {
                val area = it.optBoolean("area"); val points = it.getJSONArray("g").points()
                require(points.size >= if (area) 3 else 2) { "Incomplete piste geometry" }
                Segment(it.getLong("id"),area,points,it.text("diff"))
            } } }
            val r=p.optJSONObject("research")
            val research=r?.let { Research(it.text("conf"),it.text("status"),it.text("notes"),it.strings("sources"),it.text("partial").takeIf(String::isNotEmpty),it.optBoolean("historical"),it.optInt("gps"),it.optBoolean("userConfirmed"),it.optBoolean("crossChecked")) }
            val ids=r?.optJSONArray("osmIds")?.let { ids -> (0 until ids.length()).map { ids.getLong(it) } }?.takeIf { it.isNotEmpty() } ?: segments.map { it.id }.distinct()
            Piste(p.getString("key"),p.getString("color"),p.getBoolean("named"),p.getInt("len"),segments,p.strings("fromLifts"),p.strings("toLifts"),p.strings("joins"),p.strings("fromPistes"),ids,p.text("kind").ifEmpty { "run" },research,p.strings("osmDiff"),p.strings("refs"),p.strings("groom"),p.strings("lit"),p.text("name"),p.strings("osmNames"))
        } }
        val lifts=d.getJSONArray("lifts").let { a -> (0 until a.length()).map { i -> val l=a.getJSONObject(i)
            val points = l.getJSONArray("g").points(); require(points.size >= 2) { "Incomplete lift geometry" }
            Lift(l.getLong("id"),l.text("name").takeIf(String::isNotEmpty),l.getString("kind"),l.getInt("len"),points,l.text("status"),l.text("cap"),l.text("dur"),l.text("occ"),l.text("year"),when(l.text("bubble")) { "yes" -> true; "no" -> false; else -> null },l.text("rise")) } }
        require(pistes.map { it.key }.distinct().size == pistes.size) { "Duplicate piste keys" }
        require(lifts.map { it.id }.distinct().size == lifts.size) { "Duplicate lift identifiers" }
        val t = terrain(terrain)
        (pistes.flatMap { it.segments }.flatMap { it.points } + lifts.flatMap { it.points }).forEach { p ->
            require(p.x in t.x0..t.x1 && p.z in t.z0..t.z1) { "Geometry outside terrain bounds" }
        }
        val warnings = mutableListOf<ContentWarning>()
        fun <T> optional(content: OptionalContent, text: String?, parse: (String) -> T): T? {
            if (text != null) try { return parse(text) } catch (_: Exception) { /* Isolate optional content. */ }
            warnings.add(ContentWarning(content)); return null
        }
        val v = optional(OptionalContent.VIDEOS, videos, DataParser::videos).orEmpty()
        val tr = optional(OptionalContent.TRIP, trip, DataParser::trip)
        val missing = d.optJSONArray("missing")?.let { a -> (0 until a.length()).map { i ->
            val m = a.getJSONObject(i); MissingPiste(m.getString("name"), m.getString("color"))
        } }.orEmpty()
        return MountainData(t,pistes,lifts,v,tr,d.getString("fetched"),missing,warnings)
    }
    fun videos(text: String): List<Video> = JSONArray(text).let { a -> (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        Video(o.getString("piste"),o.getString("url"),o.getString("title"),o.text("channel"),o.text("length"),o.text("confidence"),o.text("by"),if(o.isNull("at")) null else o.getLong("at"),o.text("published"),o.text("evidence"))
    }.sortedByDescending { it.addedAt ?: Long.MIN_VALUE } }
    fun trip(text: String): Trip {
        val tr = JSONObject(text)
        fun flight(f: JSONObject): Flight {
            val date = f.getString("date"); LocalDate.parse(date)
            val departs = f.getString("departs"); val arrives = f.getString("arrives")
            LocalTime.parse(departs); LocalTime.parse(arrives)
            return Flight(f.getString("from"),f.getString("to"),date,f.getString("flight"),departs,arrives,f.text("note"),f.text("fromCode"),f.text("toCode"))
        }
        val ski = tr.optJSONObject("skiDays")?.let { SkiDays(LocalDate.parse(it.getString("from")),LocalDate.parse(it.getString("to"))) }
        return Trip(flight(tr.getJSONObject("outbound")),tr.optJSONObject("return")?.let(::flight),tr.strings("members"),tr.text("destination"),tr.text("airline"),tr.text("baggage"),ski)
    }
}

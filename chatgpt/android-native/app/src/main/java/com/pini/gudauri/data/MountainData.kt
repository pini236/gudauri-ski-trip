package com.pini.gudauri.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.*

data class Point(val x: Float, val z: Float)
data class Segment(val id: Long, val area: Boolean, val points: List<Point>)
data class Research(val confidence: String, val status: String, val notes: String,
    val sources: List<String>, val partial: String?, val historical: Boolean, val gps: Int)
data class Piste(val key: String, val color: String, val named: Boolean, val length: Int,
    val segments: List<Segment>, val fromLifts: List<String>, val toLifts: List<String>,
    val joins: List<String>, val fromPistes: List<String>, val osmIds: List<Long>,
    val kind: String, val research: Research?, val osmDifficulty: List<String>,
    val refs: List<String>, val groom: List<String>, val lit: List<String>)
data class Lift(val id: Long, val name: String?, val kind: String, val length: Int,
    val points: List<Point>, val status: String, val capacity: String, val duration: String,
    val occupancy: String, val year: String)
data class Video(val piste: String, val url: String, val title: String, val channel: String,
    val length: String, val confidence: String)
data class Peak(val name: String, val height: Int, val point: Point, val pass: Boolean)
data class Place(val name: String, val point: Point)
data class Flight(val from: String, val to: String, val date: String, val flight: String,
    val departs: String, val arrives: String, val note: String)
data class Trip(val outbound: Flight, val inbound: Flight, val members: List<String>)
data class ProfileStats(val top: Int, val bottom: Int, val drop: Int, val maxGrade: Float)
data class Contour(val height: Int, val points: List<Point>)
data class Road(val kind: Int, val points: List<Point>)

class Terrain(val nx: Int, val ny: Int, val x0: Float, val z0: Float, val x1: Float,
    val z1: Float, val heights: ShortArray, val peaks: List<Peak>, val places: List<Place>,
    val contours: List<Contour>, val roads: List<Road>, val village: List<List<Point>>,
    val water: List<List<Point>>, val rivers: List<List<Point>>) {
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
        if (!top.isFinite()) return ProfileStats(0, 0, 0, 0f)
        return ProfileStats(top.roundToInt(), bottom.roundToInt(), (top-bottom).roundToInt(), grade)
    }
    fun profile(points: List<Point>): List<Pair<Float, Float>> {
        var distance = 0f
        return points.mapIndexed { i, p ->
            if (i > 0) distance += hypot(p.x-points[i-1].x, p.z-points[i-1].z)
            distance to elevation(p.x, p.z)
        }
    }
}

data class MountainData(val terrain: Terrain, val pistes: List<Piste>, val lifts: List<Lift>,
    val videos: List<Video>, val trip: Trip, val fetched: String) {
    val pistesByKey = pistes.associateBy { it.key }
    val stats = pistes.associate { it.key to terrain.stats(it.segments) }
}

object DataParser {
    fun project(lat: Double, lon: Double) = Point(
        ((lon-44.495)*111320*cos(42.51*PI/180)).toFloat(), (-(lat-42.51)*111320).toFloat())
    private fun JSONArray.points(): List<Point> = (0 until length()).map {
        getJSONArray(it).let { q -> project(q.getDouble(0), q.getDouble(1)) }
    }
    private fun JSONArray.flatPoints(): List<Point> = (0 until length() step 2).map {
        Point(getDouble(it).toFloat(), getDouble(it+1).toFloat())
    }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONObject.strings(key: String) = optJSONArray(key)?.strings().orEmpty()
    private fun shortArray(encoded: String): ShortArray {
        val bytes = Base64.getDecoder().decode(encoded)
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
            val h=raw[i++].toInt(); val n=raw[i++].toInt(); var x=raw[i++].toFloat(); var z=raw[i++].toFloat()
            val points=mutableListOf(Point(x,z))
            repeat(n-1) { x+=raw[i++]; z+=raw[i++]; points.add(Point(x,z)) }
            contours.add(Contour(h,points))
        }
        fun areas(key:String) = env.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONArray(it).flatPoints() } }
        val roads=env.getJSONArray("roads").let { a -> (0 until a.length()).map { j -> a.getJSONArray(j).let { Road(it.getInt(0),it.getJSONArray(1).flatPoints()) } } }
        val water=env.getJSONArray("water").let { a -> (0 until a.length()).map { a.getJSONObject(it).getJSONArray("g").flatPoints() } }
        return Terrain(d.getInt("nx"),d.getInt("ny"),d.getDouble("x0").toFloat(),d.getDouble("y0").toFloat(),d.getDouble("x1").toFloat(),d.getDouble("y1").toFloat(),shortArray(d.getString("b64")),peaks,places,contours,roads,areas("village"),water,areas("rivers"))
    }
    fun parse(runs: String, terrain: String, videos: String, trip: String): MountainData {
        val d=JSONObject(runs)
        val pistes=d.getJSONArray("pistes").let { a -> (0 until a.length()).map { i ->
            val p=a.getJSONObject(i)
            val segments=p.getJSONArray("segs").let { s -> (0 until s.length()).map { j -> s.getJSONObject(j).let { Segment(it.getLong("id"),it.optBoolean("area"),it.getJSONArray("g").points()) } } }
            val r=p.optJSONObject("research")
            val research=r?.let { Research(it.getString("conf"),it.getString("status"),it.getString("notes"),it.strings("sources"),if(it.isNull("partial")) null else it.optString("partial").takeIf(String::isNotEmpty),it.optBoolean("historical"),it.optInt("gps")) }
            val ids=r?.optJSONArray("osmIds")?.let { ids -> (0 until ids.length()).map { ids.getLong(it) } }?.takeIf { it.isNotEmpty() } ?: segments.map { it.id }.distinct()
            Piste(p.getString("key"),p.getString("color"),p.getBoolean("named"),p.getInt("len"),segments,p.strings("fromLifts"),p.strings("toLifts"),p.strings("joins"),p.strings("fromPistes"),ids,p.optString("kind","run"),research,p.strings("osmDiff"),p.strings("refs"),p.strings("groom"),p.strings("lit"))
        } }
        val lifts=d.getJSONArray("lifts").let { a -> (0 until a.length()).map { i -> val l=a.getJSONObject(i)
            Lift(l.getLong("id"),if(l.isNull("name")) null else l.optString("name"),l.getString("kind"),l.getInt("len"),l.getJSONArray("g").points(),l.optString("status"),l.optString("cap"),l.optString("dur"),l.optString("occ"),l.optString("year")) } }
        val v=JSONArray(videos).let { a -> (0 until a.length()).map { i -> val o=a.getJSONObject(i)
            Video(o.getString("piste"),o.getString("url"),o.getString("title"),o.optString("channel"),o.optString("length"),o.optString("confidence")) } }
        val tr=JSONObject(trip)
        fun flight(key:String):Flight { val f=tr.getJSONObject(key); return Flight(f.getString("from"),f.getString("to"),f.getString("date"),f.getString("flight"),f.getString("departs"),f.getString("arrives"),f.optString("note")) }
        return MountainData(terrain(terrain),pistes,lifts,v,Trip(flight("outbound"),flight("return"),tr.strings("members")),d.getString("fetched"))
    }
}

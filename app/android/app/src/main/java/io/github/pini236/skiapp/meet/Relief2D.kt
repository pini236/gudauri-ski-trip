package io.github.pini236.skiapp.meet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * The top view of the mountain as the site draws it under the meeting point (site/js/relief.js, svgRelief): the shaded
 * relief picture, the contour lines (every 50 m, stronger every 100 and 250), the village, rivers, lakes and roads.
 * Everything is in the site's projected metres (x east, y south), from site/data/terrain.json; built once, off the
 * main thread, when the meeting point first opens.
 */
class Relief2D(
    val hill: Bitmap?,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    /** contours by weight: every 50 m, every 100 m, every 250 m */
    val c50: Path, val c100: Path, val c250: Path,
    val village: Path, val rivers: Path, val water: Path,
    /** road classes 0 (main) to 4 (tracks) */
    val roads: List<Path>,
    /** the named places (Gudauri, Kobi...): name and where, in metres */
    val places: List<Triple<String, Float, Float>> = emptyList(),
) {
    companion object {
        fun parse(json: String): Relief2D {
            val t = JSONObject(json)
            val d = t.getJSONObject("dem")
            val hill = runCatching {
                val src = t.getJSONObject("hill").getString("src")
                val bytes = Base64.getDecoder().decode(src.substringAfter("base64,"))
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()

            // contours: [level, n, x, y, dx, dy, ...] as little-endian int16 (relief.js)
            val c50 = Path(); val c100 = Path(); val c250 = Path()
            val raw = Base64.getDecoder().decode(t.getString("contours"))
            val s = ShortArray(raw.size / 2).also { ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
            var i = 0
            while (i + 3 < s.size) {
                val lev = s[i].toInt(); val n = s[i + 1].toInt()
                var x = s[i + 2].toFloat(); var y = s[i + 3].toFloat(); i += 4
                val p = if (lev % 250 == 0) c250 else if (lev % 100 == 0) c100 else c50
                p.moveTo(x, y)
                for (k in 1 until n) { x += s[i]; y += s[i + 1]; p.lineTo(x, y); i += 2 }
            }

            val env = t.getJSONObject("env")
            fun flat(a: JSONArray, p: Path, close: Boolean) {
                for (k in 0 until a.length() / 2) { val x = a.getDouble(k * 2).toFloat(); val y = a.getDouble(k * 2 + 1).toFloat(); if (k == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                if (close) p.close()
            }
            val village = Path().also { p -> env.getJSONArray("village").let { v -> for (k in 0 until v.length()) flat(v.getJSONArray(k), p, true) } }
            val rivers = Path().also { p -> env.getJSONArray("rivers").let { v -> for (k in 0 until v.length()) flat(v.getJSONArray(k), p, false) } }
            val water = Path().also { p -> env.getJSONArray("water").let { v -> for (k in 0 until v.length()) flat(v.getJSONObject(k).getJSONArray("g"), p, true) } }
            val roads = List(5) { Path() }
            env.getJSONArray("roads").let { v ->
                for (k in 0 until v.length()) { val r = v.getJSONArray(k); flat(r.getJSONArray(1), roads[r.getInt(0).coerceIn(0, 4)], false) }
            }
            val places = env.optJSONArray("places")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { o -> Triple(o.getString("n"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat()) } }.orEmpty()
            return Relief2D(hill, d.getDouble("x0").toFloat(), d.getDouble("y0").toFloat(), d.getDouble("x1").toFloat(), d.getDouble("y1").toFloat(),
                c50, c100, c250, village, rivers, water, roads, places)
        }
    }
}

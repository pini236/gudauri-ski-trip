package io.github.pini236.skiapp.fieldtools

import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.analysis.Proj
import org.json.JSONObject

/**
 * The raw log a field tester sends: the part of the day inside the site's rectangle, and nothing else. A port of
 * tools/dayrec/export.py, checked against tools/fixtures/dayrec-export.json (RawExportTest). Rules (architect, 31.10.2026):
 *
 * - The rectangle is the extent of the terrain model plus the margin (500 m). A point is kept only if it is inside, so the
 *   home, the hotel and the drive in are never in the file, nor their barometer or motion readings (fields of the point).
 * - Every stretch of cut points becomes one line `{"ev":"gap","t0":..,"t1":..}`, and the header says `"cropped":true`.
 * - The window of a blow the skier dismissed is never exported. A window that stayed is exported only when the tester
 *   agreed to it in its own line of the consent, and only if the blow is inside.
 * - The header carries no device, account, name or advertising id: the fields about the log, and what the caller adds.
 * - Events this export does not know are dropped.
 */
object RawExport {
    class Rect(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
        fun has(x: Double, y: Double) = x in x0..x1 && y in y0..y1
    }

    private val HEADER_KEEP = listOf("v", "tz", "resort", "start", "mode")
    private val KEPT_EVENTS = setOf("start", "resume", "stop", "full")

    /** The projection and the rectangle (the terrain model's extent plus [marginM] on every side) of terrain.json. */
    fun rectOf(terrain: JSONObject, marginM: Double): Pair<Proj, Rect> {
        val d = terrain.getJSONObject("dem")
        val p = terrain.getJSONObject("proj")
        return Proj(p.getDouble("lat0"), p.getDouble("lon0")) to
            Rect(d.getDouble("x0") - marginM, d.getDouble("y0") - marginM, d.getDouble("x1") + marginM, d.getDouble("y1") + marginM)
    }

    private class Row(val t: Long, val order: Int, val line: String)

    /** The cropped log as text, or null when nothing of the day is inside. */
    fun crop(text: String, proj: Proj, rect: Rect, extra: JSONObject, allowImpacts: Boolean): String? {
        val day = DayLog.parse(text.lineSequence())
        val rows = ArrayList<Row>()
        var inside = 0
        var gapFrom = -1L; var gapTo = -1L
        fun closeGap() { if (gapFrom >= 0) { rows += Row(gapFrom, 1, JSONObject().put("ev", "gap").put("t0", gapFrom).put("t1", gapTo).toString()); gapFrom = -1 } }
        for (p in day.points) {
            if (rect.has(proj.x(p.lon), proj.y(p.lat))) {
                closeGap(); inside++
                val o = JSONObject().put("t", p.t).put("la", p.lat).put("lo", p.lon)
                fun put(k: String, v: Double?) { o.put(k, v ?: JSONObject.NULL) }
                put("a", p.alt); put("p", p.pressure); put("s", p.speed); put("sa", p.speedAcc)
                put("ha", p.hAcc); put("va", p.vAcc); put("b", p.bearing); put("m", p.motion)
                rows += Row(p.t, 2, o.toString())
            } else {
                if (gapFrom < 0) gapFrom = p.t
                gapTo = p.t
            }
        }
        closeGap()
        if (inside == 0) return null
        val dismissed = day.events.filter { it.optString("ev") == "impact" && it.optBoolean("dismissed", false) }.map { it.optLong("t") }.toSet()
        for (e in day.events) {
            when (val ev = e.optString("ev")) {
                in KEPT_EVENTS -> {
                    val o = JSONObject().put("ev", ev).put("t", e.optLong("t", 0))
                    if (e.has("reason")) o.put("reason", e.get("reason"))
                    rows += Row(e.optLong("t", 0), 0, o.toString())
                }
                "impact" -> {
                    val t = e.optLong("t")
                    if (e.optBoolean("dismissed", false)) rows += Row(t, 3, JSONObject().put("ev", "impact").put("t", t).put("dismissed", true).toString())
                    else if (allowImpacts && t !in dismissed && e.has("acc")) {
                        val near = day.points.minByOrNull { Math.abs(it.t - t) }?.takeIf { Math.abs(it.t - t) <= 5000 }
                        if (near != null && rect.has(proj.x(near.lon), proj.y(near.lat))) {
                            val o = JSONObject().put("ev", "impact").put("t", t)
                            for (k in listOf("hz", "t0", "acc", "gyr")) if (e.has(k)) o.put(k, e.get(k))
                            rows += Row(t, 3, o.toString())
                        }
                    }
                }
            }
        }
        val head = JSONObject()
        for (k in HEADER_KEEP) if (day.header.has(k)) head.put(k, day.header.get(k))
        for (k in extra.keys()) head.put(k, extra.get(k))
        head.put("cropped", true)
        val sorted = rows.sortedWith(compareBy<Row>({ it.t }, { it.order }))
        return (listOf(head.toString()) + sorted.map { it.line }).joinToString("\n", postfix = "\n")
    }
}

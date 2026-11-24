package io.github.pini236.skiapp.redesign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.meet.Station
import java.io.File

/**
 * The completion round, part 2 (Q06, design/handoff/2026-11-21-redesign-around, section 5): the meeting point as one
 * picture, 4 by 5 (1080 by 1350). The far view of the mountain (the painted picture of the hour's light, as the day's
 * story uses), the station in the middle across and 70 points above the middle; the runs at half; only this station's
 * lifts; the flag with the time; a dark fall at the bottom with "meeting at 13:00", the station and its height, the day
 * and "Gudi · Gudauri". No person's name, no group's name, no coordinates; a PNG, so no place in its metadata (the
 * architect, 24.11.2026; [hasPlace] checks the file written).
 */
internal object MeetShareImage {
    const val W = 1080; const val H = 1350
    /** The board is drawn on 326 points across: its points to the picture's pixels. */
    private const val S = W / 326f

    class Words(val at: String, val place: String, val day: String, val site: String, val time: String)

    fun draw(m: StoryVideo.Mountain, s: Station, runs: Runs, words: Words, night: Boolean, display: Typeface?, body: Typeface?, rtl: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pj = m.view.proj ?: return bmp
        val vw = m.view.w; val vh = m.view.h
        val st = OnMountain.project(pj, m.elev, floatArrayOf(s.x, s.y), 4f)
        // the far view, nearer than covering by half, the station at the middle across and 70 points above the middle
        val k = maxOf(W / vw, H / vh) * 1.5f
        val tx = W / 2f; val ty = H / 2f - 70 * S
        val ox = (if (st[0].isNaN()) (W - vw * k) / 2 else tx - st[0] * k).coerceIn(minOf(W - vw * k, 0f), 0f)
        val oy = (if (st[1].isNaN()) (H - vh * k) / 2 else ty - st[1] * k).coerceIn(minOf(H - vh * k, 0f), 0f)
        c.drawBitmap(m.img, null, RectF(ox, oy, ox + vw * k, oy + vh * k), Paint(Paint.FILTER_BITMAP_FLAG))
        fun path(xy: FloatArray): Path? {
            val p = OnMountain.project(pj, m.elev, xy, 2f)
            val out = Path(); var on = false; var any = false
            for (i in 0 until p.size / 2) {
                val x = p[i * 2]; val y = p[i * 2 + 1]
                if (x.isNaN() || y.isNaN()) { on = false; continue }
                val px = ox + x * k; val py = oy + y * k
                if (on) out.lineTo(px, py) else out.moveTo(px, py); on = true; any = true
            }
            return if (any) out else null
        }
        // the runs at half, in their colours, on a white case
        val case = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3.6f * S; color = 0x80FFFFFF.toInt(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val line = Paint(case).apply { strokeWidth = 2.2f * S }
        for (p in runs.pistes) for (l in p.lines) path(l)?.let { pa ->
            c.drawPath(pa, case)
            line.color = io.github.pini236.skiapp.game.runColor(p.color); line.alpha = 128; c.drawPath(pa, line)
        }
        // only this station's lifts
        val wire = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * S; color = if (night) 0xFFEAF1F8.toInt() else 0xFF0E2233.toInt() }
        for (e in s.ends) path(e.lift.pts)?.let { c.drawPath(it, wire) }
        // the flag: the pole 3 by 50 its foot on the station, the cloth 26 high in the accent with the time
        if (!st[0].isNaN() && !st[1].isNaN()) {
            val fx = ox + st[0] * k; val fy = oy + st[1] * k
            val acc = if (night) 0xFFB79CFF.toInt() else 0xFF6B3FD4.toInt()
            val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E2233.toInt() }
            c.drawRoundRect(RectF(fx - 1.5f * S, fy - 50 * S, fx + 1.5f * S, fy), 2 * S, 2 * S, pole)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(body, Typeface.BOLD); textSize = 13.5f * S; color = 0xFFFFFFFF.toInt(); fontFeatureSettings = "tnum" }
            val w = tp.measureText(words.time) + 22 * S; val top = fy - 50 * S; val h = 26 * S; val x0 = fx + 1.5f * S
            c.drawPath(Path().apply { moveTo(x0, top); lineTo(x0 + w, top); lineTo(x0 + w - 7 * S, top + h / 2); lineTo(x0 + w, top + h); lineTo(x0, top + h); close() },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = acc })
            c.drawText(words.time, x0 + 8 * S, top + h / 2 - (tp.ascent() + tp.descent()) / 2, tp)
            c.drawCircle(fx, fy, 7 * S, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            c.drawCircle(fx, fy, 7 * S, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E2233.toInt(); style = Paint.Style.STROKE; strokeWidth = 3 * S })
        }
        // the dark fall at the bottom, and the words on it
        val fall = H * .38f
        c.drawRect(0f, H - fall, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, H - fall, 0f, H.toFloat(), intArrayOf(0x00081020, 0xD1081020.toInt(), 0xD1081020.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        })
        val pad = 20 * S
        fun text(t: String, size: Float, face: Typeface?, bold: Boolean, alpha: Int, y: Float, atEnd: Boolean = false) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL); textSize = size * S; color = 0xFFFFFFFF.toInt(); this.alpha = alpha
                textAlign = if (rtl != atEnd) Paint.Align.RIGHT else Paint.Align.LEFT
            }
            c.drawText(t, if (rtl != atEnd) W - pad else pad, y, p)
        }
        // the board: the time big, the station and its height, then the day at the start and the site at the end
        var y = H - pad - 4 * S
        text(words.day, 13f, body, false, 225, y); text(words.site, 13f, body, false, 225, y, atEnd = true); y -= 26 * S
        text(words.place, 15f, body, true, 255, y); y -= 34 * S
        text(words.at, 31f, display ?: body, true, 255, y)
        return bmp
    }

    /** The picture to a PNG in the cache (for the share sheet); null if it could not, or if the file has a place in it. */
    fun write(dir: File, bmp: Bitmap): File? {
        dir.mkdirs()
        val f = File(dir, "meet.png")
        runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }.onFailure { return null }
        if (hasPlace(f.readBytes())) { f.delete(); return null }
        return f
    }

    /**
     * Whether a PNG holds metadata that could carry a place (the architect's condition 6): an eXIf chunk, or a text
     * chunk naming GPS. Bitmap.compress writes neither; this proves it on the file itself.
     */
    fun hasPlace(png: ByteArray): Boolean {
        if (png.size < 8) return false
        var i = 8
        while (i + 8 <= png.size) {
            val len = ((png[i].toInt() and 0xFF) shl 24) or ((png[i + 1].toInt() and 0xFF) shl 16) or ((png[i + 2].toInt() and 0xFF) shl 8) or (png[i + 3].toInt() and 0xFF)
            val type = String(png, i + 4, 4, Charsets.US_ASCII)
            if (type == "eXIf") return true
            if (type == "tEXt" || type == "iTXt" || type == "zTXt") {
                val body = String(png, i + 8, len.coerceAtMost(png.size - i - 8), Charsets.ISO_8859_1)
                if (body.contains("GPS", ignoreCase = true) || body.contains("exif", ignoreCase = true)) return true
            }
            if (type == "IEND" || len < 0) break
            i += 12 + len
        }
        return false
    }
}

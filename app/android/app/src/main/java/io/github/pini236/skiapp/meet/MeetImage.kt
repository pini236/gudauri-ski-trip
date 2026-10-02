package io.github.pini236.skiapp.meet

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import io.github.pini236.skiapp.data.Runs
import java.io.File

/**
 * The picture for sharing a meeting point (M3), drawn as the site draws it (site/js/app.js, MEET image()): the mountain
 * around the spot, the runs and lifts, the pin, the name on a blue sign and the time in gold, 1080 square.
 */
object MeetImage {
    private const val W = 1080f

    fun draw(s: Station, time: String, label: String, site: String, runs: Runs, relief: Relief2D?, display: Typeface?, body: Typeface?, rtl: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W.toInt(), W.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val span = 2200f; val k = W / span; val ox = s.x - span / 2; val oy = s.y - span * .55f
        fun X(v: Float) = (v - ox) * k
        fun Y(v: Float) = (v - oy) * k
        c.drawColor(0xFFEEF2F5.toInt())
        relief?.hill?.let { c.drawBitmap(it, null, RectF(X(relief.x0), Y(relief.y0), X(relief.x1), Y(relief.y1)), Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * .55f).toInt() }) }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        fun stroke(pts: FloatArray, col: Int, w: Float) {
            val p = Path(); for (i in 0 until pts.size / 2) if (i == 0) p.moveTo(X(pts[0]), Y(pts[1])) else p.lineTo(X(pts[i * 2]), Y(pts[i * 2 + 1]))
            line.color = col; line.strokeWidth = w
            c.drawPath(p, line)
        }
        val cols = mapOf("green" to 0xFF1B8A4C.toInt(), "blue" to 0xFF1F5FC4.toInt(), "red" to 0xFFD1342B.toInt(), "black" to 0xFF13233A.toInt())
        for (p in runs.mainPistes) if (p.named) for (l in p.lines) { stroke(l, 0xFFFFFFFF.toInt(), 11f); stroke(l, cols[p.color] ?: cols.getValue("black"), 6f) }
        for (l in runs.mainLifts) if (l.name.isNotEmpty()) { stroke(l.pts, 0xFFFFFFFF.toInt(), 6f); stroke(l.pts, 0xFF3A4556.toInt(), 3f) }

        // the pin
        val px = X(s.x); val py = Y(s.y)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4B942.toInt() }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF13233A.toInt(); strokeWidth = 6f }
        c.drawOval(RectF(px - 46, py - 18, px + 46, py + 18), ink)
        val pin = Path().apply {
            moveTo(px, py); cubicTo(px, py, px - 50, py - 60, px - 50, py - 100)
            arcTo(RectF(px - 50, py - 150, px + 50, py - 50), 180f, 180f, false)
            cubicTo(px + 50, py - 60, px, py, px, py); close()
        }
        c.drawPath(pin, gold); c.drawPath(pin, ink)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        c.drawCircle(px, py - 100, 19f, white); c.drawCircle(px, py - 100, 19f, ink)

        // the dark foot, the name on a blue sign, the time in gold
        c.drawRect(0f, W - 420, W, W, Paint().apply {
            shader = LinearGradient(0f, W - 420, 0f, W, intArrayOf(0x0013233A, 0xD913233A.toInt(), 0xF213233A.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        })
        val disp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; isFakeBoldText = display == null }
        fun fit(t: String, max: Float, start: Float): Float { var size = start; disp.textSize = size; while (size > 60 && disp.measureText(t) > max) { size -= 6; disp.textSize = size }; return size }
        val tw = minOf(300f, (fit(time, 300f, 150f).let { disp.measureText(time) }) + 50)
        val fs = fit(s.name, W - 60 - tw - 30 - 110 - 40, 150f); val nw = disp.measureText(s.name) + 110
        val sign = Path().apply { moveTo(60f, W - 170); lineTo(110f, W - 270); lineTo(60 + nw, W - 270); lineTo(60 + nw, W - 70); lineTo(110f, W - 70); close() }
        c.drawPath(sign, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F5FC4.toInt() })
        disp.textSize = fs; disp.color = 0xFFFFFFFF.toInt(); disp.textAlign = Paint.Align.LEFT
        c.drawText(s.name, 130f, W - 112, disp)
        c.drawRect(W - 60 - tw, W - 270, W - 60, W - 70, gold)
        fit(time, tw - 40, 150f); disp.color = 0xFF13233A.toInt(); disp.textAlign = Paint.Align.CENTER
        c.drawText(time, W - 60 - tw / 2, W - 112, disp)
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = body; color = 0xFFFFFFFF.toInt(); textSize = 40f; isFakeBoldText = true; textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT }
        c.drawText(label, if (rtl) W - 60 else 60f, W - 330, txt)
        c.drawRect(W - 420, 40f, W - 60, 110f, Paint().apply { color = 0xFF13233A.toInt() })
        txt.textSize = 34f; txt.textAlign = Paint.Align.RIGHT
        c.drawText(site, W - 90, 88f, txt)
        return bmp
    }

    /** The picture and the message to the system's share sheet (WhatsApp shows both); the file is in the cache. */
    fun share(context: Context, bmp: Bitmap, text: String, title: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "meet.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".share", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_TITLE, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

package io.github.pini236.skiapp.dayrec.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/**
 * The picture of the day for sharing (round 29): the head over the numbers, the day's line in the colours of the speed, and up to
 * three insights. No name, no fall, no place name, and nothing the engine keeps for the skier's eyes alone (a fall, an off-piste
 * line, tomorrow, the end of the trip, where the time went): the engine says which (`share_exclude`, and [KEPT] here).
 */
object StoryImage {
    private const val W = 1080
    private const val H = 1350

    /** The kinds that never go in the picture. */
    internal val KEPT = setOf("fall", "offpiste", "tomorrow", "trip", "time_split")

    class Card(val title: String?, val line: String?)

    fun draw(title: String?, sub: String?, cards: List<Card>, track: Track?, site: String, display: Typeface?, body: Typeface?, rtl: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF13233A.toInt())
        val align = if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        val x = 70f; val width = W - 140
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; isFakeBoldText = display == null; color = 0xFFFFFFFF.toInt(); textSize = 110f }
        val body1 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = body; color = 0xFFC9D6E6.toInt(); textSize = 38f }
        val gold = TextPaint(body1).apply { color = 0xFFF4B942.toInt(); isFakeBoldText = true; textSize = 40f }
        val white = TextPaint(body1).apply { color = 0xFFFFFFFF.toInt(); textSize = 40f }
        var y = 80f
        fun text(t: String, p: TextPaint, maxLines: Int = 3) {
            val l = StaticLayout.Builder.obtain(t, 0, t.length, p, width).setAlignment(align).setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            c.save(); c.translate(x, y); l.draw(c); c.restore(); y += l.height
        }
        title?.let { text(it, head, 2); y += 6f }
        sub?.let { text(it, body1, 2); y += 20f }
        // the day's line in the colours of the speed, in a box of its own
        val box = RectF(x, y, x + width, y + 470f)
        c.drawRect(box, Paint().apply { color = 0xFF1B3050.toInt() })
        if (track != null && track.x.size > 1) {
            val pad = 40f
            val x0 = track.x.min(); val x1 = track.x.max(); val y0 = track.y.min(); val y1 = track.y.max()
            val k = minOf((box.width() - 2 * pad) / maxOf(x1 - x0, 1f), (box.height() - 2 * pad) / maxOf(y1 - y0, 1f))
            val ox = box.left + (box.width() - (x1 - x0) * k) / 2f; val oy = box.top + (box.height() - (y1 - y0) * k) / 2f
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 7f }
            val seg = Path()
            for (i in 0 until track.x.size - 1) {
                if (track.t[i + 1] - track.t[i] > 90_000L) continue // a hole in the recording is not a line
                seg.reset()
                seg.moveTo(ox + (track.x[i] - x0) * k, oy + (track.y[i] - y0) * k); seg.lineTo(ox + (track.x[i + 1] - x0) * k, oy + (track.y[i + 1] - y0) * k)
                line.color = SpeedScale.argb(maxOf(track.kmh[i], track.kmh[i + 1]))
                c.drawPath(seg, line)
            }
        }
        y = box.bottom + 40f
        for (card in cards.take(3)) {
            card.title?.let { text(it, gold, 1) }
            card.line?.let { text(it, white, 3) }
            y += 24f
        }
        val foot = TextPaint(body1).apply { textSize = 34f; color = 0xFF8DA2BD.toInt(); textAlign = Paint.Align.CENTER }
        c.drawText(site, W / 2f, H - 50f, foot)
        return bmp
    }

    fun share(context: Context, bmp: Bitmap, text: String, title: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "day.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".share", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_TITLE, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

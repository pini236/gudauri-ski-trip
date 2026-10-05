package io.github.pini236.skiapp.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import io.github.pini236.skiapp.meet.Relief2D
import io.github.pini236.skiapp.ui.DayColors

/**
 * What lies on the snow in 3D, as the site's terrain texture (relief.js, "texture"): the contours every 100 m (stronger
 * every 500 m), the village, the rivers, the lakes and the roads, drawn once into a picture that the terrain shader
 * lays over the mountain by its x and z. The snow and the rock stay the mesh's own colours; this picture is clear
 * everywhere else.
 */
object Drape {
    /** The picture's width: the site's 2048, about 7 m a pixel on this mountain. */
    const val WIDTH = 2048

    fun paint(r: Relief2D, width: Int = WIDTH): Bitmap {
        val w = r.x1 - r.x0; val h = r.y1 - r.y0
        val tw = width; val th = Math.round(width * h / w)
        val b = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val s = tw / w
        c.scale(s, s); c.translate(-r.x0, -r.y0)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
        fun stroke(color: Int, metres: Float) { p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = metres }
        fun fill(color: Int) { p.style = Paint.Style.FILL; p.color = color }
        // the contours: every 100 m light, every 500 m stronger (the site's 0.8 and 1.3 times four of its pixels)
        stroke(Color.argb(36, 70, 90, 115), 3.2f); c.drawPath(r.c100, p)
        stroke(Color.argb(77, 70, 90, 115), 5.2f); c.drawPath(r.c500, p)
        fill(Color.argb(191, 214, 203, 190)); c.drawPath(r.village, p)
        stroke(Color.argb(179, 120, 160, 200), maxOf(1.2f / s, 6f)); c.drawPath(r.rivers, p)
        fill(DayColors.water.toArgb()); c.drawPath(r.water, p)
        stroke(Color.rgb(0x6f, 0x9c, 0xc4), 1f / s); c.drawPath(r.water, p)
        // the roads, small ones first so the main road is on top
        val rw = floatArrayOf(14f, 10f, 7f, 6f, 4f)
        for (k in 4 downTo 0) {
            stroke(if (k == 0) Color.rgb(0x8a, 0x7f, 0x76) else Color.argb(217, 140, 128, 118), maxOf(1f / s, rw[k]))
            c.drawPath(r.roads[k], p)
        }
        return b
    }
}

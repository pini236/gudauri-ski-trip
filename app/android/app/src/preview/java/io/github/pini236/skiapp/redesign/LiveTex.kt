package io.github.pini236.skiapp.redesign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** One of the designer's textures, decoded off the GL thread: RGBA bytes, row 0 at the top (v = 0, as the shader reads). */
class LiveTex(val name: String, val w: Int, val h: Int, val px: ByteArray) {
    companion object {
        /** All opaque: decoded without premultiplying, so the channels arrive as the designer wrote them. */
        fun decode(context: Context, name: String): LiveTex {
            val o = BitmapFactory.Options().apply { inPremultiplied = false; inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = context.assets.open(LiveData.DIR + "$name.webp").use { BitmapFactory.decodeStream(it, null, o) }
                ?: error("live texture $name did not decode")
            val w = bmp.width; val h = bmp.height
            val argb = IntArray(w * h); bmp.getPixels(argb, 0, w, 0, 0, w, h); bmp.recycle()
            val px = ByteArray(w * h * 4)
            for (i in argb.indices) {
                val c = argb[i]
                px[i * 4] = (c shr 16).toByte(); px[i * 4 + 1] = (c shr 8).toByte(); px[i * 4 + 2] = c.toByte(); px[i * 4 + 3] = (-1).toByte()
            }
            return LiveTex(name, w, h, px)
        }

        /** The designer's texel check (`check` in peak-live.json): the pixel at (u * w, v * h), within [tol] per channel. */
        fun check(px: ByteArray, w: Int, h: Int, c: LiveData.Check, tol: Int = 12): Boolean {
            val x = (c.u * w).toInt().coerceIn(0, w - 1); val y = (c.v * h).toInt().coerceIn(0, h - 1)
            val i = (y * w + x) * 4
            return (0..2).all { kotlin.math.abs((px[i + it].toInt() and 255) - c.rgb[it]) <= tol }
        }

        /** Half the width and height (a 2 by 2 box), for a GPU whose largest texture is smaller than the horizon. */
        fun half(t: LiveTex): LiveTex {
            val w = t.w / 2; val h = t.h / 2; val o = ByteArray(w * h * 4)
            for (y in 0 until h) for (x in 0 until w) for (k in 0..3) {
                fun at(xx: Int, yy: Int) = t.px[(yy * t.w + xx) * 4 + k].toInt() and 255
                o[(y * w + x) * 4 + k] = ((at(2 * x, 2 * y) + at(2 * x + 1, 2 * y) + at(2 * x, 2 * y + 1) + at(2 * x + 1, 2 * y + 1) + 2) / 4).toByte()
            }
            return LiveTex(t.name, w, h, o)
        }
    }
}

package io.github.pini236.skiapp.redesign

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile

/**
 * Round 4, board L11: the day in a minute as a video, made on the phone only (the architect, 21.11.2026: MediaCodec and
 * MediaMuxer, no library, no server; up to 60 s at 720p; a temporary file; out only through the system's share sheet).
 * The same rules as the picture: no coordinates as text, no fall, no off-piste (its stretches are not drawn), no place in
 * the file's metadata (the muxer is never given one, and [Mp4Meta] checks the written file before it may leave), no
 * user's, group's or friend's name. What is drawn: the day's lines of light by speed, filling in on the film's clock, the
 * kind of day, its numbers, "Gudi" and "Gudauri" with the date.
 */
internal object StoryVideo {
    const val W = 720
    const val H = 1280
    const val FPS = 30
    const val SECONDS = 60

    class Words(val title: String?, val numbers: List<Pair<String, String>>, val site: String, val place: String)

    /**
     * Writes the video to [out]; false if the phone could not, or if the written file has a place in it (then it is
     * deleted). [progress] gets 0..1 from the encoder's thread.
     */
    fun make(out: File, paint: DayPaint.Day, film: DayPaint.Film, hide: List<LongArray>, night: Boolean, words: Words,
             display: Typeface?, body: Typeface?, rtl: Boolean, progress: (Float) -> Unit): Boolean {
        out.delete()
        val ok = runCatching { encode(out, paint, film, hide, night, words, display, body, rtl, progress) }.getOrDefault(false)
        if (!ok || !out.isFile || Mp4Meta.hasPlace(out)) { out.delete(); return false }
        return true
    }

    private fun encode(out: File, paint: DayPaint.Day, film: DayPaint.Film, hide: List<LongArray>, night: Boolean, words: Words,
                       display: Typeface?, body: Typeface?, rtl: Boolean, progress: (Float) -> Unit): Boolean {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            // baseline: no B-frames, so the frames come out in order and get their times here
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        // never setLocation: no place in the file
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var written = 0
        val info = MediaCodec.BufferInfo()
        val frameUs = 1_000_000L / FPS
        fun drain(end: Boolean) {
            while (true) {
                val i = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    i >= 0 -> {
                        val buf = codec.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && track >= 0) {
                            // the surface stamps the frames by the wall clock of drawing; the video's clock is its own
                            info.presentationTimeUs = written * frameUs; written++
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
        val frames = FPS * SECONDS
        val scene = Frame(paint, hide, night, words, display, body, rtl, W, H, null)
        try {
            for (f in 0 until frames) {
                val ms = f * 1000f / FPS * (film.length.toFloat() / (SECONDS * 1000f))
                val c = surface.lockHardwareCanvas()
                try { scene.draw(c, film.at(ms)) } finally { surface.unlockCanvasAndPost(c) }
                drain(false)
                if (f % FPS == 0) progress(f.toFloat() / frames)
            }
            codec.signalEndOfInputStream()
            drain(true)
            progress(1f)
        } finally {
            runCatching { codec.stop() }; codec.release(); surface.release()
            runCatching { if (track >= 0) muxer.stop() }; muxer.release()
        }
        return written > 0
    }

    /**
     * The board's picture (L11, 4:5): the whole day's lines, the lifts dotted, the fastest moment's pin, and the words
     * under a dark veil. Drawn from above: the board draws the day on the painted mountain, which this does not yet.
     */
    fun still(paint: DayPaint.Day, hide: List<LongArray>, night: Boolean, words: Words, display: Typeface?, body: Typeface?,
              rtl: Boolean, pin: FloatArray?): android.graphics.Bitmap {
        val w = 1080; val h = 1350
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        Frame(paint, hide, night, words, display, body, rtl, w, h, pin).draw(Canvas(b), Long.MAX_VALUE)
        return b
    }

    /** One frame: the sky, the lifts and the lines drawn up to [now] (none in a hidden stretch), the pin, the words. */
    private class Frame(val paint: DayPaint.Day, val hide: List<LongArray>, val night: Boolean, val words: Words,
                        val display: Typeface?, val body: Typeface?, val rtl: Boolean, val w: Int, val h: Int, val pin: FloatArray?) {
        private val u = w / 318f // the board's card is 318 wide
        private val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
        private val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), if (night) 0xFF0C1730.toInt() else 0xFFDCE6F1.toInt(),
                if (night) 0xFF1B2B52.toInt() else 0xFFF4F7FA.toInt(), Shader.TileMode.CLAMP)
        }
        private val case = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 5.2f * u; color = if (night) 0xFF050A16.toInt() else 0xFFFFFFFF.toInt(); alpha = 230 }
        private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = 3f * u }
        private val lift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 1.4f * u; strokeCap = Paint.Cap.ROUND; color = if (night) 0xCCB8C4D6.toInt() else 0xCC22303F.toInt()
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(.01f, 4f * u), 0f)
        }
        private val veilTop = h - 190f / 398f * h
        private val veil = Paint().apply {
            shader = LinearGradient(0f, veilTop, 0f, h.toFloat(), intArrayOf(0x00080E1C, 0xDB080E1C.toInt(), 0xDB080E1C.toInt()),
                floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1; textSize = 25f * u; typeface = Typeface.create(display ?: body ?: Typeface.DEFAULT, Typeface.BOLD) }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1; textSize = 27f * u; typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(body ?: Typeface.DEFAULT, 300, false) else body ?: Typeface.DEFAULT }
        private val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt(); textSize = 11f * u; typeface = body ?: Typeface.DEFAULT }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt(); textSize = 11.5f * u; typeface = body ?: Typeface.DEFAULT }
        private val fit: FloatArray
        init {
            var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            fun take(x: Float, y: Float) { x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y) }
            for (l in paint.lines) for (i in 0 until l.xy.size / 2) take(l.xy[i * 2], l.xy[i * 2 + 1])
            for (c in paint.lifts) { take(c[0], c[1]); take(c[2], c[3]) }
            if (x0 > x1) { x0 = 0f; x1 = 1f; y0 = 0f; y1 = 1f }
            // the lines fill the card down into the veil, as on the board; the words sit over their lower part
            val pad = 22f * u; val area = h - 70f * u
            val k = minOf((w - pad * 2) / (x1 - x0).coerceAtLeast(1f), (area - pad * 2) / (y1 - y0).coerceAtLeast(1f))
            fit = floatArrayOf(x0, y1, k, (w - (x1 - x0) * k) / 2, pad + (area - pad * 2 - (y1 - y0) * k) / 2)
        }
        fun draw(c: Canvas, now: Long) {
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            fun x(v: Float) = fit[3] + (v - fit[0]) * fit[2]
            fun y(v: Float) = fit[4] + (fit[1] - v) * fit[2]
            paint.lifts.forEachIndexed { i, l ->
                if ((paint.liftT.getOrNull(i)?.get(0) ?: Long.MIN_VALUE) > now) return@forEachIndexed
                c.drawLine(x(l[0]), y(l[1]), x(l[2]), y(l[3]), lift)
            }
            for (pass in 0..1) for (l in paint.lines) for (i in 0 until l.xy.size / 2 - 1) {
                if (l.t[i + 1] > now || hidden(l.t[i], hide) || hidden(l.t[i + 1], hide)) continue
                val p = if (pass == 0) case else light.apply { color = 0xFF000000.toInt() or ramp[l.band[i].coerceIn(0, ramp.size - 1)] }
                c.drawLine(x(l.xy[i * 2]), y(l.xy[i * 2 + 1]), x(l.xy[i * 2 + 2]), y(l.xy[i * 2 + 3]), p)
            }
            pin?.let { pt ->
                val px = x(pt[0]); val py = y(pt[1])
                c.drawCircle(px, py, 4.6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1 })
                c.drawCircle(px, py, 4.6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * u; color = 0xFFE0246E.toInt() })
            }
            c.drawRect(0f, veilTop, w.toFloat(), h.toFloat(), veil)
            val left = 16f * u; val right = w - 16f * u
            val align = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val start = if (rtl) right else left
            // from the bottom up: the line of Gudi and the place, the numbers, the title
            val foot = h - 14f * u
            small.textAlign = Paint.Align.LEFT; c.drawText(words.site, left, foot, small)
            small.textAlign = Paint.Align.RIGHT; c.drawText(words.place, right, foot, small)
            val capY = foot - small.textSize - 8f * u
            val numY = capY - cap.textSize - 2f * u
            var cx = start
            num.textAlign = align; cap.textAlign = align
            for ((v, l) in words.numbers) {
                c.drawText(v, cx, numY, num); c.drawText(l, cx, capY, cap)
                val step = maxOf(num.measureText(v), cap.measureText(l)) + 18f * u
                cx += if (rtl) -step else step
            }
            title.textAlign = align
            words.title?.let { c.drawText(it, start, numY - num.textSize - 8f * u, title) }
        }
    }

    /** A moment in a stretch that must not be drawn (off-piste: the board's "no off-piste part"). */
    fun hidden(t: Long, spans: List<LongArray>): Boolean = spans.any { t >= it[0] && t <= it[1] }
}

/** What an MP4 says about a place: the QuickTime `©xyz` atom and the 3GPP `loci` box. Read from the written file. */
internal object Mp4Meta {
    private val PLACES = listOf(byteArrayOf(0xA9.toByte(), 'x'.code.toByte(), 'y'.code.toByte(), 'z'.code.toByte()), "loci".toByteArray(), "@xyz".toByteArray())

    fun hasPlace(f: File): Boolean = RandomAccessFile(f, "r").use { r ->
        // the boxes at the top level; the place is in moov (udta or meta), never inside mdat, so mdat is skipped
        var at = 0L
        while (at + 8 <= r.length()) {
            r.seek(at)
            var size = r.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { r.readFully(it) }
            var head = 8L
            if (size == 1L) { size = r.readLong(); head = 16L } else if (size == 0L) size = r.length() - at
            if (size < head) return@use true // a broken file is not shared
            if (String(type, Charsets.ISO_8859_1) != "mdat") {
                val n = (size - head).coerceAtMost(4L shl 20).toInt()
                val body = ByteArray(n); r.seek(at + head); r.readFully(body)
                if (hasPlace(body)) return@use true
            }
            at += size
        }
        false
    }

    fun hasPlace(bytes: ByteArray): Boolean = PLACES.any { p -> (0..bytes.size - p.size).any { i -> p.indices.all { bytes[i + it] == p[it] } } }
}

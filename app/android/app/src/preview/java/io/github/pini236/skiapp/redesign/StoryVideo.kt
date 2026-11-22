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
        val scene = Frame(paint, hide, night, words, display, body, rtl)
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

    /** One frame: the background, the lines drawn up to [now] (none in a hidden stretch), the words. */
    private class Frame(val paint: DayPaint.Day, val hide: List<LongArray>, val night: Boolean, val words: Words,
                        val display: Typeface?, val body: Typeface?, val rtl: Boolean) {
        private val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
        private val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), if (night) 0xFF0C1730.toInt() else 0xFFDCE6F1.toInt(),
                if (night) 0xFF1B2B52.toInt() else 0xFFF4F7FA.toInt(), Shader.TileMode.CLAMP)
        }
        private val case = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = 9f; color = if (night) 0xFF050A16.toInt() else 0xFFFFFFFF.toInt() }
        private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = 5.5f }
        private val veil = Paint().apply { shader = LinearGradient(0f, H - 460f, 0f, H.toFloat(), 0x00080E1C, 0xDB080E1C.toInt(), Shader.TileMode.CLAMP) }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1; textSize = 52f; typeface = display ?: Typeface.DEFAULT_BOLD }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1; textSize = 56f; typeface = Typeface.create(body ?: Typeface.DEFAULT, Typeface.NORMAL) }
        private val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt(); textSize = 24f; typeface = body ?: Typeface.DEFAULT }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt(); textSize = 24f; typeface = body ?: Typeface.DEFAULT }
        private val fit: FloatArray
        init {
            var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            for (l in paint.lines) for (i in 0 until l.xy.size / 2) { val x = l.xy[i * 2]; val y = l.xy[i * 2 + 1]; x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y) }
            if (x0 > x1) { x0 = 0f; x1 = 1f; y0 = 0f; y1 = 1f }
            val pad = 70f; val area = H - 480f
            val k = minOf((W - pad * 2) / (x1 - x0).coerceAtLeast(1f), (area - pad * 2) / (y1 - y0).coerceAtLeast(1f))
            fit = floatArrayOf(x0, y1, k, (W - (x1 - x0) * k) / 2, (area - (y1 - y0) * k) / 2)
        }
        fun draw(c: Canvas, now: Long) {
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
            fun x(v: Float) = fit[3] + (v - fit[0]) * fit[2]
            fun y(v: Float) = fit[4] + (fit[1] - v) * fit[2]
            for (pass in 0..1) for (l in paint.lines) for (i in 0 until l.xy.size / 2 - 1) {
                if (l.t[i + 1] > now || hidden(l.t[i], hide) || hidden(l.t[i + 1], hide)) continue
                val p = if (pass == 0) case else light.apply { color = 0xFF000000.toInt() or ramp[l.band[i].coerceIn(0, ramp.size - 1)] }
                c.drawLine(x(l.xy[i * 2]), y(l.xy[i * 2 + 1]), x(l.xy[i * 2 + 2]), y(l.xy[i * 2 + 3]), p)
            }
            c.drawRect(0f, H - 460f, W.toFloat(), H.toFloat(), veil)
            val left = 48f; val right = W - 48f
            fun at(p: Paint, s: String, yy: Float) {
                p.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
                c.drawText(s, if (rtl) right else left, yy, p)
            }
            words.title?.let { at(title, it, H - 300f) }
            // the numbers in a row, in the reading order
            val cell = (right - left) / words.numbers.size.coerceAtLeast(1)
            words.numbers.forEachIndexed { k, (v, l) ->
                val cx = if (rtl) right - k * cell else left + k * cell
                num.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT; cap.textAlign = num.textAlign
                c.drawText(v, cx, H - 200f, num); c.drawText(l, cx, H - 160f, cap)
            }
            small.textAlign = Paint.Align.LEFT; c.drawText(words.site, left, H - 70f, small)
            small.textAlign = Paint.Align.RIGHT; c.drawText(words.place, right, H - 70f, small)
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

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

    /** [light]: the numbers' thin face (the board's weight 300); the text's is the caller's regular body face. */
    class Words(val title: String?, val numbers: List<Pair<String, String>>, val site: String, val place: String, val light: Typeface? = null)

    /**
     * The painted mountain the day is drawn on (round 4, the designer's point 6: "the day on the painted mountain, from
     * the valley, as on the day sheet"): the whole mountain's picture for the light ([img], the POC's "hero" view), its
     * projection, and the ground's height for a point in the map's metres (the projection wants a height).
     */
    class Mountain(val img: android.graphics.Bitmap, val view: PeakData.View, val elev: (Float, Float) -> Float)

    /**
     * Writes the video to [out]; false if the phone could not, or if the written file has a place in it (then it is
     * deleted). [progress] gets 0..1 from the encoder's thread.
     */
    fun make(out: File, paint: DayPaint.Day, film: DayPaint.Film, hide: List<LongArray>, night: Boolean, words: Words,
             display: Typeface?, body: Typeface?, rtl: Boolean, mountain: Mountain? = null, progress: (Float) -> Unit): Boolean {
        out.delete()
        val ok = runCatching { encode(out, paint, film, hide, night, words, display, body, rtl, mountain, progress) }.getOrDefault(false)
        if (!ok || !out.isFile || Mp4Meta.hasPlace(out)) { out.delete(); return false }
        return true
    }

    private fun encode(out: File, paint: DayPaint.Day, film: DayPaint.Film, hide: List<LongArray>, night: Boolean, words: Words,
                       display: Typeface?, body: Typeface?, rtl: Boolean, mountain: Mountain?, progress: (Float) -> Unit): Boolean {
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
        val scene = Frame(paint, hide, night, words, display, body, rtl, W, H, null, mountain)
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
     * under a dark veil; on the painted mountain from the valley when [mountain] is given, otherwise from above.
     */
    fun still(paint: DayPaint.Day, hide: List<LongArray>, night: Boolean, words: Words, display: Typeface?, body: Typeface?,
              rtl: Boolean, pin: FloatArray?, mountain: Mountain? = null): android.graphics.Bitmap {
        val w = 1080; val h = 1350
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        Frame(paint, hide, night, words, display, body, rtl, w, h, pin, mountain).draw(Canvas(b), Long.MAX_VALUE)
        return b
    }

    /** One frame: the sky, the lifts and the lines drawn up to [now] (none in a hidden stretch), the pin, the words. */
    private class Frame(val paint: DayPaint.Day, val hide: List<LongArray>, val night: Boolean, val words: Words,
                        val display: Typeface?, val body: Typeface?, val rtl: Boolean, val w: Int, val h: Int, val pin: FloatArray?,
                        val mountain: Mountain? = null) {
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
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1; textSize = 27f * u; typeface = words.light ?: body ?: Typeface.DEFAULT }
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

        // on the mountain: every point of the day projected once onto the painted picture (its points, 390 wide), then
        // the picture scaled to cover the card and framed on the day; the picture cropped once, so a video frame only
        // copies it
        private val proj = mountain?.view?.proj
        private val onMountain: Array<FloatArray>? = proj?.let { pj -> paint.lines.map { l -> project(pj, l.xy, 2f) }.toTypedArray() }
        private val liftsOn: Array<FloatArray>? = proj?.let { pj -> paint.lifts.map { c ->
            // the wire, a little over the ground, in eight steps so it bends with the picture
            val xy = FloatArray(18); for (i in 0..8) { val t = i / 8f; xy[i * 2] = c[0] + (c[2] - c[0]) * t; xy[i * 2 + 1] = c[1] + (c[3] - c[1]) * t }
            project(pj, xy, 12f) }.toTypedArray() }
        private val place: FloatArray? = mountain?.let { m ->
            var a0 = Float.MAX_VALUE; var a1 = -Float.MAX_VALUE; var b0 = Float.MAX_VALUE; var b1 = -Float.MAX_VALUE
            for (l in onMountain!!) for (i in 0 until l.size / 2) { val x = l[i * 2]; val y = l[i * 2 + 1]; if (x.isNaN()) continue
                a0 = minOf(a0, x); a1 = maxOf(a1, x); b0 = minOf(b0, y); b1 = maxOf(b1, y) }
            val vw = m.view.w; val vh = m.view.h
            val cover = maxOf(w / vw, h / vh)
            if (a0 > a1) return@let floatArrayOf(cover, 0f, (h - vh * cover) / 2)
            val pad = 34f * u; val area = h - 120f * u
            // as near as the day allows, never less than the whole picture's width, never more than 2.6 times that
            val k = minOf((w - pad * 2) / (a1 - a0).coerceAtLeast(1f), (area - pad * 2) / (b1 - b0).coerceAtLeast(1f)).coerceIn(cover, cover * 2.6f)
            val ox = (w / 2f - (a0 + a1) / 2 * k).coerceIn(w - vw * k, 0f)
            val oy = (area / 2f - (b0 + b1) / 2 * k).coerceIn(h - vh * k, 0f)
            floatArrayOf(k, ox, oy)
        }
        private val backdrop: android.graphics.Bitmap? = mountain?.let { m -> place?.let { pl ->
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val k = pl[0]
            Canvas(bmp).drawBitmap(m.img, null, android.graphics.RectF(pl[1], pl[2], pl[1] + m.view.w * k, pl[2] + m.view.h * k),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            bmp
        } }

        /** The map's metres to the picture's points: back to latitude and longitude, the ground's height, the projection. */
        private fun project(pj: PeakData.Proj, xy: FloatArray, up: Float): FloatArray {
            val m = mountain!!
            val out = FloatArray(xy.size)
            for (i in 0 until xy.size / 2) {
                val x = xy[i * 2]; val y = xy[i * 2 + 1]
                val p = pj.project(MapMetres.lat(y), MapMetres.lon(x), (m.elev(x, y) + up).toDouble())
                out[i * 2] = p?.x ?: Float.NaN; out[i * 2 + 1] = p?.y ?: Float.NaN
            }
            return out
        }
        fun draw(c: Canvas, now: Long) {
            val bd = backdrop; val pl = place; val on = onMountain
            if (bd != null && pl != null && on != null) { drawOnMountain(c, now, bd, pl, on); drawWords(c); return }
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
            drawWords(c)
        }

        /** The day on the painted mountain: the picture, the rides dotted, the lines of light, the pin. */
        private fun drawOnMountain(c: Canvas, now: Long, bd: android.graphics.Bitmap, pl: FloatArray, on: Array<FloatArray>) {
            c.drawBitmap(bd, 0f, 0f, null)
            val k = pl[0]; val ox = pl[1]; val oy = pl[2]
            liftsOn?.forEachIndexed { i, l ->
                if ((paint.liftT.getOrNull(i)?.get(0) ?: Long.MIN_VALUE) > now) return@forEachIndexed
                for (j in 0 until l.size / 2 - 1) { if (l[j * 2].isNaN() || l[j * 2 + 2].isNaN()) continue
                    c.drawLine(ox + l[j * 2] * k, oy + l[j * 2 + 1] * k, ox + l[j * 2 + 2] * k, oy + l[j * 2 + 3] * k, lift) }
            }
            for (pass in 0..1) paint.lines.forEachIndexed { li, l -> val q = on[li]
                for (i in 0 until l.xy.size / 2 - 1) {
                    if (l.t[i + 1] > now || hidden(l.t[i], hide) || hidden(l.t[i + 1], hide)) continue
                    if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
                    val p = if (pass == 0) case else light.apply { color = 0xFF000000.toInt() or ramp[l.band[i].coerceIn(0, ramp.size - 1)] }
                    c.drawLine(ox + q[i * 2] * k, oy + q[i * 2 + 1] * k, ox + q[i * 2 + 2] * k, oy + q[i * 2 + 3] * k, p)
                }
            }
            val pj = proj
            if (pin != null && pj != null && now == Long.MAX_VALUE) {
                val q = project(pj, pin, 2f)
                if (!q[0].isNaN()) {
                    val px = ox + q[0] * k; val py = oy + q[1] * k
                    c.drawCircle(px, py, 4.6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1 })
                    c.drawCircle(px, py, 4.6f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * u; color = 0xFFE0246E.toInt() })
                }
            }
        }

        /** The veil and the words over it: the kind of day, the numbers, "Gudi" and the place. */
        private fun drawWords(c: Canvas) {
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

/** The map's metres (data/Geo.kt: x east, y south, around 42.51, 44.495) back to latitude and longitude. */
internal object MapMetres {
    private val kx = 111320.0 * kotlin.math.cos(io.github.pini236.skiapp.data.Geo.LAT0 * kotlin.math.PI / 180)
    fun lat(y: Float) = io.github.pini236.skiapp.data.Geo.LAT0 - y / 111320.0
    fun lon(x: Float) = io.github.pini236.skiapp.data.Geo.LON0 + x / kx
}

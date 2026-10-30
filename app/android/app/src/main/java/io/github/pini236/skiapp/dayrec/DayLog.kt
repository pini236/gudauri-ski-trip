package io.github.pini236.skiapp.dayrec

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * The raw log of one ski day (docs/ARCHITECTURE.md m-8, condition 3; docs/FEATURES.md section 10): one JSON object per
 * line, appended to the end of one file, so a crash loses at most the last seconds and never the day.
 *
 * - First line: the header `{"v":1,"tz":..,"resort":..,"device":..}`. Then a point per second (see [Point]) and now and
 *   then an event line `{"e":"gap"|"resume"|"end",...}`.
 * - A reader skips a line it does not know (an unknown "e") and a field it does not know, so the format can only grow.
 *   A last line cut in half by a crash is thrown away.
 * - **The file never leaves the phone** (m-7): no code here sends, logs or measures a position.
 */
class DayLog private constructor(val file: File, private val out: FileOutputStream, private val now: () -> Long) {

    /** One second of sensors. The position and the speed come from the GPS; null is "this phone could not say". */
    class Point(
        val t: Long, val lat: Double, val lon: Double,
        val alt: Double? = null, val pressure: Float? = null,
        val speed: Float? = null, val speedAcc: Float? = null,
        val hAcc: Float? = null, val vAcc: Float? = null,
        val bearing: Float? = null, val motion: Float? = null,
    )

    private var lastSync = now()
    private var closed = false
    var points = 0; private set

    /** Size so far, so the recorder can stop at the day's limit ([MAX_BYTES]). */
    val bytes get() = file.length()
    val full get() = bytes >= MAX_BYTES

    @Synchronized
    fun add(p: Point) {
        line(JSONObject().apply {
            put("t", p.t); put("la", fix(p.lat, 6)); put("lo", fix(p.lon, 6))
            p.alt?.let { put("al", fix(it, 1)) }; p.pressure?.let { put("p", fix(it.toDouble(), 2)) }
            p.speed?.let { put("sp", fix(it.toDouble(), 2)) }; p.speedAcc?.let { put("spa", fix(it.toDouble(), 2)) }
            p.hAcc?.let { put("ha", fix(it.toDouble(), 1)) }; p.vAcc?.let { put("va", fix(it.toDouble(), 1)) }
            p.bearing?.let { put("br", fix(it.toDouble(), 1)) }; p.motion?.let { put("mv", fix(it.toDouble(), 3)) }
        })
        points++
    }

    /** No reading between [from] and [to] (more than [GAP_MS]): the timeline shows it as a hole and does not hide it. */
    @Synchronized fun gap(from: Long, to: Long) = line(JSONObject().put("e", "gap").put("from", from).put("to", to))

    /** The day was cut (the process was killed) and goes on from the same file. */
    @Synchronized fun resumed(t: Long) = line(JSONObject().put("e", "resume").put("t", t))

    /** The last line of a day that ended on purpose; a file without it is a day that was cut. */
    @Synchronized
    fun end(t: Long, reason: String) {
        if (closed) return
        line(JSONObject().put("e", "end").put("t", t).put("reason", reason))
        sync(); closed = true; runCatching { out.close() }
    }

    /** Closes the file as it is, without a closing line: the day stays "cut" and can be continued. */
    @Synchronized
    fun release() { if (closed) return; sync(); closed = true; runCatching { out.close() } }

    private fun line(o: JSONObject) {
        if (closed) return
        out.write((o.toString() + "\n").toByteArray(Charsets.UTF_8))
        out.flush() // to the system now: a crash of the app (not of the phone) loses nothing written
        if (now() - lastSync >= SYNC_MS) sync()
    }

    /** To the disk: at most every [SYNC_MS], so a power loss costs at most that. */
    private fun sync() { runCatching { out.fd.sync() }; lastSync = now() }

    companion object {
        const val VERSION = 1
        const val SYNC_MS = 30_000L
        const val GAP_MS = 10_000L
        const val MAX_BYTES = 10L shl 20

        fun fix(v: Double, decimals: Int): Double {
            val f = Math.pow(10.0, decimals.toDouble())
            return Math.round(v * f) / f
        }

        /** Starts a new day's file with its header. */
        fun create(file: File, tz: String, resort: String, device: String, now: () -> Long = System::currentTimeMillis): DayLog {
            file.parentFile?.mkdirs()
            val log = DayLog(file, FileOutputStream(file, false), now)
            log.line(JSONObject().put("v", VERSION).put("tz", tz).put("resort", resort).put("device", device))
            return log
        }

        /** Goes on writing to the end of a day that was cut. */
        fun reopen(file: File, now: () -> Long = System::currentTimeMillis): DayLog {
            if (!file.isFile) throw IOException("no such day")
            // a last line cut in half would be glued to the next one: start on a new line
            val tail = file.length() > 0 && lastBytes(file, 1).lastOrNull() != '\n'.code.toByte()
            val out = FileOutputStream(file, true)
            if (tail) out.write('\n'.code)
            return DayLog(file, out, now)
        }

        /** The last [n] bytes of a file, without reading the rest of it. */
        fun lastBytes(file: File, n: Int): ByteArray = java.io.RandomAccessFile(file, "r").use { r ->
            val len = minOf(n.toLong(), r.length()).toInt()
            ByteArray(len).also { r.seek(r.length() - len); r.readFully(it) }
        }

        fun read(file: File): Day = parse(file.readText(Charsets.UTF_8).lineSequence())

        fun parse(lines: Sequence<String>): Day {
            val all = lines.filter { it.isNotBlank() }.toList()
            var header: JSONObject? = null
            val points = ArrayList<Point>(); val gaps = ArrayList<LongArray>(); val resumes = ArrayList<Long>()
            var end: JSONObject? = null
            all.forEachIndexed { i, text ->
                // only the last line can be cut; a bad line in the middle is a damaged file and is skipped too
                val o = runCatching { JSONObject(text) }.getOrNull() ?: return@forEachIndexed
                when {
                    i == 0 && o.has("v") -> header = o
                    o.has("e") -> when (o.optString("e")) {
                        "gap" -> gaps += longArrayOf(o.optLong("from"), o.optLong("to"))
                        "resume" -> resumes += o.optLong("t")
                        "end" -> end = o
                        else -> {} // an event from a newer version: ignored
                    }
                    o.has("t") && o.has("la") && o.has("lo") -> points += Point(
                        o.getLong("t"), o.getDouble("la"), o.getDouble("lo"),
                        o.dbl("al"), o.flt("p"), o.flt("sp"), o.flt("spa"), o.flt("ha"), o.flt("va"), o.flt("br"), o.flt("mv"),
                    )
                }
            }
            return Day(header?.optInt("v", 1) ?: 1, header?.optString("tz").orEmpty(), header?.optString("resort").orEmpty(),
                points, gaps, resumes, end?.optLong("t"), end?.optString("reason"))
        }

        private fun JSONObject.dbl(k: String): Double? = if (has(k)) optDouble(k) else null
        private fun JSONObject.flt(k: String): Float? = if (has(k)) optDouble(k).toFloat() else null
    }

    /** A day as read back: [endedAt] is null for a day that was cut. */
    class Day(
        val version: Int, val tz: String, val resort: String,
        val points: List<Point>, val gaps: List<LongArray>, val resumes: List<Long>,
        val endedAt: Long?, val endReason: String?,
    ) {
        val cut get() = endedAt == null
    }
}

/** The days on the phone, in one private folder that is left out of every backup (res/xml/data_extraction_rules.xml). */
class DayStore(private val root: File) {
    val dir get() = File(root, DIR).also { it.mkdirs() }

    fun newFile(startedAt: Long) = File(dir, "day-$startedAt.jsonl")

    fun days(): List<File> = dir.listFiles { f -> f.isFile && f.name.startsWith("day-") && f.name.endsWith(".jsonl") }.orEmpty().sortedBy { it.name }

    /** The newest day that has no closing line: it was cut (the process died, or the phone was switched off). */
    fun unfinished(except: File? = null): File? =
        days().asReversed().firstOrNull { it != except && isCut(it) }

    /** Only the tail of the file is read to know (the closing line is short). */
    fun isCut(f: File): Boolean = runCatching {
        val last = String(DayLog.lastBytes(f, 256), Charsets.UTF_8).lineSequence().lastOrNull { it.isNotBlank() }
        last == null || !last.contains("\"e\":\"end\"")
    }.getOrDefault(true)

    fun totalBytes() = days().sumOf { it.length() }

    fun delete(f: File) { f.delete() }
    fun deleteAll() { days().forEach { it.delete() } }

    companion object {
        const val DIR = "dayrec"
        fun device(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().lowercase(Locale.ROOT)
    }
}

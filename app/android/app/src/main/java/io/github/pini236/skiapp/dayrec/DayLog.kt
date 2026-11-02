package io.github.pini236.skiapp.dayrec

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * The raw log of one ski day, in the format of the reference tool (tools/dayrec/README.md, "The raw log", and
 * rawlog.py, which this reader follows rule by rule): one JSON object per line, appended to the end of one file, so a
 * crash loses at most the last seconds and never the day.
 *
 * - First line, the header `{"v":1,"tz":..,"resort":..,"device":..,"start":ms,"mode":"day"}`. A point is a line with `t`
 *   (the keys of [Point], a missing reading written as `null`); an event is a line with `ev`: `start`, `resume`,
 *   `stop` (with a reason), `full`, and `impact` (a window of the motion sensors around a blow, and its dismissal).
 * - A reader skips a line it does not parse, anywhere, and an event type or a field it does not know, so the format only
 *   grows. The holes are not written: the analysis finds them in the time between points (`gap_s`).
 * - **The file never leaves the phone** (m-7): no code here sends, logs or measures a position.
 */
class DayLog private constructor(val file: File, private val out: FileOutputStream, private val now: () -> Long) {

    /** One second of sensors. The position comes from the GPS; null is "this phone could not say". */
    class Point(
        val t: Long, val lat: Double, val lon: Double,
        val alt: Double? = null, val pressure: Double? = null,
        val speed: Double? = null, val speedAcc: Double? = null,
        val hAcc: Double? = null, val vAcc: Double? = null,
        val bearing: Double? = null, val motion: Double? = null,
    )

    private var lastSync = now()
    private var closed = false
    var points = 0; private set

    /** Size so far, so the recorder can stop at the day's limit ([MAX_BYTES]). */
    val bytes get() = file.length()
    val atLimit get() = bytes >= MAX_BYTES

    @Synchronized
    fun add(p: Point) {
        line(JSONObject().apply {
            put("t", p.t); put("la", fix(p.lat, 7)); put("lo", fix(p.lon, 7))
            put("a", p.alt?.let { fix(it, 1) } ?: JSONObject.NULL)
            put("p", p.pressure?.let { fix(it, 3) } ?: JSONObject.NULL)
            put("s", p.speed?.let { fix(it, 2) } ?: JSONObject.NULL)
            put("sa", p.speedAcc?.let { fix(it, 2) } ?: JSONObject.NULL)
            put("ha", p.hAcc?.let { fix(it, 1) } ?: JSONObject.NULL)
            put("va", p.vAcc?.let { fix(it, 1) } ?: JSONObject.NULL)
            put("b", p.bearing?.let { fix(it, 1) } ?: JSONObject.NULL)
            put("m", p.motion?.let { fix(it, 3) } ?: JSONObject.NULL)
        })
        points++
    }

    /** The day goes on from a file that was cut (after [reopen]). */
    @Synchronized fun resumed(t: Long) = line(JSONObject().put("ev", "resume").put("t", t))

    /** The day began (the second line of a new file). */
    @Synchronized fun started(t: Long) = line(JSONObject().put("ev", "start").put("t", t))

    /** The last line of a day that ended on purpose; a file without it (and without `full`) is a day that was cut. */
    @Synchronized
    fun stop(t: Long, reason: String) {
        if (closed) return
        line(JSONObject().put("ev", "stop").put("t", t).put("reason", reason))
        finish()
    }

    /** The file reached its ceiling: nothing is written after this line. */
    @Synchronized
    fun full(t: Long) {
        if (closed) return
        line(JSONObject().put("ev", "full").put("t", t))
        finish()
    }

    /**
     * A blow, "here you fell" (m-8 condition 10): a window of the accelerometer (m/s^2, with gravity) and the gyroscope
     * (rad/s), [hz] a second, interleaved x,y,z; the sample at exactly [t] is in it. Written after the window closes,
     * so it may come after points later than [t].
     */
    @Synchronized
    fun impact(t: Long, t0: Long, hz: Int, acc: DoubleArray, gyr: DoubleArray) = line(impactJson(t, t0, hz, acc, gyr))

    /** "I did not fall": a second line with the same `t`; the window is deleted when the day is closed ([compact]). */
    @Synchronized fun dismiss(t: Long) = line(JSONObject().put("ev", "impact").put("t", t).put("dismissed", true))

    /** Closes the file as it is, without a closing line: the day stays "cut" and can be gone on with. */
    @Synchronized
    fun release() { if (closed) return; sync(); closed = true; runCatching { out.close() } }

    private fun finish() { sync(); closed = true; runCatching { out.close() } }

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
        const val MAX_BYTES = 10L shl 20

        /** The line of a blow, as it is written and as the analysis reads it. */
        fun impactJson(t: Long, t0: Long, hz: Int, acc: DoubleArray, gyr: DoubleArray): JSONObject {
            fun arr(a: DoubleArray, decimals: Int) = JSONArray().also { j -> a.forEach { j.put(fix(it, decimals)) } }
            return JSONObject().put("ev", "impact").put("t", t).put("hz", hz).put("t0", t0).put("acc", arr(acc, 2)).put("gyr", arr(gyr, 3))
        }

        /** Rounds a reading, as the reference tool writes it. */
        fun fix(v: Double, decimals: Int): Double {
            val f = Math.pow(10.0, decimals.toDouble())
            return Math.round(v * f) / f
        }

        /** Starts a new day's file with its header and its `start` event. */
        fun create(file: File, tz: String, resort: String, device: String, startedAt: Long, mode: String = "day", now: () -> Long = System::currentTimeMillis): DayLog {
            file.parentFile?.mkdirs()
            val log = DayLog(file, FileOutputStream(file, false), now)
            log.line(JSONObject().put("v", VERSION).put("tz", tz).put("resort", resort).put("device", device).put("start", startedAt).put("mode", mode))
            log.started(startedAt)
            return log
        }

        /**
         * Goes on writing to a day that was cut. First the file is rewritten without the windows of dismissed blows
         * (recovery is one of the two moments for it) and without a line a crash cut; then, if it does not end with a
         * newline, one is written before anything else (README rule 2).
         */
        fun reopen(file: File, now: () -> Long = System::currentTimeMillis): DayLog {
            if (!file.isFile) throw IOException("no such day")
            compact(file)
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

        /**
         * Closing a day, and recovering after a crash: the motion windows of dismissed blows are deleted for real
         * (m-8 condition 10). Every other line stays as it was, in order; the dismissal line stays, without a window;
         * a line that does not parse is dropped. The result replaces the file atomically (rawlog.compact).
         */
        fun compact(file: File) {
            val lines = file.readText(Charsets.UTF_8).split('\n').filter { it.isNotBlank() }
            val parsed = lines.map { runCatching { JSONObject(it) }.getOrNull() }
            val gone = HashSet<Long>()
            parsed.forEach { o -> if (o != null && o.optString("ev") == "impact" && o.optBoolean("dismissed", false)) gone += o.optLong("t") }
            val kept = lines.filterIndexed { i, _ ->
                val o = parsed[i] ?: return@filterIndexed false
                !(o.optString("ev") == "impact" && !o.optBoolean("dismissed", false) && o.optLong("t") in gone)
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(kept.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw IOException("cannot replace ${file.name}") }
        }

        fun read(file: File): Day = parse(file.readText(Charsets.UTF_8).lineSequence())

        /** rawlog.read: a header, the points sorted by time (the same second twice: the first), the events, and how many lines were dropped. */
        fun parse(lines: Sequence<String>): Day {
            var header: JSONObject? = null
            val points = ArrayList<Point>(); val events = ArrayList<JSONObject>(); var bad = 0
            for (raw in lines) {
                val text = raw.trim()
                if (text.isEmpty()) continue
                val o = runCatching { JSONObject(text) }.getOrNull()
                if (o == null) { bad++; continue }
                when {
                    header == null && o.has("v") && !o.has("t") -> header = o
                    o.has("ev") -> events += o
                    o.has("t") && o.has("la") && o.has("lo") -> {
                        if (o.isNull("la") || o.isNull("lo")) { bad++; continue }
                        points += Point(o.getLong("t"), o.getDouble("la"), o.getDouble("lo"),
                            o.dbl("a"), o.dbl("p"), o.dbl("s"), o.dbl("sa"), o.dbl("ha"), o.dbl("va"), o.dbl("b"), o.dbl("m"))
                    }
                    else -> bad++
                }
            }
            val sorted = points.sortedBy { it.t }
            val unique = ArrayList<Point>(sorted.size)
            for (p in sorted) if (unique.isEmpty() || p.t > unique.last().t) unique += p
            return Day(header ?: JSONObject(), unique, events, bad)
        }

        private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k) else null
    }

    /** A day as read back. */
    class Day(val header: JSONObject, val points: List<Point>, val events: List<JSONObject>, val dropped: Int) {
        val tz get() = header.optString("tz")
        val resort get() = header.optString("resort")
        val mode get() = header.optString("mode", "day")
        val stop: JSONObject? get() = events.lastOrNull { it.optString("ev") == "stop" }
        val endedAt: Long? get() = (stop ?: events.lastOrNull { it.optString("ev") == "full" })?.optLong("t")
        val endReason: String? get() = stop?.optString("reason") ?: if (events.any { it.optString("ev") == "full" }) "full" else null
        val resumes get() = events.count { it.optString("ev") == "resume" }
        /** A day with neither a `stop` nor a `full` line: it was cut. */
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
        val tail = String(DayLog.lastBytes(f, 512), Charsets.UTF_8)
        !(tail.contains("\"ev\":\"stop\"") || tail.contains("\"ev\":\"full\""))
    }.getOrDefault(true)

    fun totalBytes() = days().sumOf { it.length() }

    /** One day goes with its result and its moments; the history of the skier is not recomputed (architect, 30.10.2026). */
    fun delete(f: File) { f.delete(); resultOf(f).delete(); momentsOf(f).delete() }
    /** "Delete everything" also deletes the history the moments are compared with. */
    fun deleteAll() { days().forEach { delete(it) }; MomentsStore.clear(dir) }

    companion object {
        const val DIR = "dayrec"
        /** A day's analysis result, beside its log. It never matches the log's pattern, so it is not a day. */
        fun resultOf(day: File) = File(day.parentFile, day.name.removeSuffix(".jsonl") + ".analysis.json")
        /** The moments of a day, picked once when the day was closed (the history of that moment is not kept), beside its log. */
        fun momentsOf(day: File) = File(day.parentFile, day.name.removeSuffix(".jsonl") + ".moments.json")
        fun device(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim().lowercase(Locale.ROOT)
    }
}

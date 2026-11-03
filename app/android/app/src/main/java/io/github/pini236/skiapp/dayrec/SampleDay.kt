package io.github.pini236.skiapp.dayrec

import android.content.Context
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The example day of "My days" (round 29, "example"): a day built from the real lines and lifts of the resort (a log of the
 * test values, tools/fixtures/dayrec-logs/full_day.jsonl), so that a new skier, and the store's reviewer, who is not on the
 * mountain, see a whole timeline without snow. It is marked "example", is opened like any day, and is never counted: its
 * moments are computed against an empty history and never written into the real one.
 */
object SampleDay {
    const val ID = "sample"
    const val DIR = "sample-day"

    /** The log of the example day, unpacked on first use into the cache (it can always be unpacked again). */
    fun file(ctx: Context): File {
        val dir = File(ctx.cacheDir, DIR).also { it.mkdirs() }
        val f = File(dir, "day-0.jsonl")
        if (!f.isFile || f.length() == 0L) {
            // never a crash for an example: without the file the day screen says there is nothing in it
            runCatching {
                val tmp = File(dir, "day-0.jsonl.tmp")
                ctx.assets.open("data/sample-day.bin").use { a -> GZIPInputStream(a).use { g -> tmp.outputStream().use { o -> g.copyTo(o) } } }
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            }
        }
        return f
    }

    fun isSample(day: File) = day.parentFile?.name == DIR
}

package io.github.pini236.skiapp.dayrec

import android.content.Context
import io.github.pini236.skiapp.data.SiteData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The history of the skier and the moments of each day, on the phone only (decision 76, architect 30.10.2026): beside the
 * days, in the folder that is left out of every backup. The history holds counts and times, never a position, grows by one
 * day at a time and is not recomputed when a day is deleted; "delete everything" deletes it. A day is applied once, even when
 * its result is computed again.
 */
object MomentsStore {
    private const val HISTORY = "moments-history.json"
    private const val APPLIED = "moments-applied.json"

    fun history(dir: File): JSONObject = runCatching { JSONObject(File(dir, HISTORY).readText()) }.getOrElse { Moments.emptyHistory() }

    fun clear(dir: File) { File(dir, HISTORY).delete(); File(dir, APPLIED).delete() }

    private fun applied(dir: File): MutableSet<Long> = runCatching {
        val a = JSONArray(File(dir, APPLIED).readText()); (0 until a.length()).map { a.getLong(it) }.toMutableSet()
    }.getOrElse { mutableSetOf() }

    private fun write(f: File, json: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    @Synchronized
    fun apply(ctx: Context, day: File, result: File) {
        val dir = day.parentFile ?: return
        val res = JSONObject(result.readText())
        val start = res.optLong("start", 0L)
        if (start == 0L) return
        val done = applied(dir)
        if (start in done) return
        val c = ctx.applicationContext
        val read = { name: String -> c.assets.open("data/$name").bufferedReader().use { it.readText() } }
        val params = JSONObject(read("dayrec-params.json")).getJSONObject("moments")
        @Suppress("DEPRECATION")
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        val version = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val site = SiteData(File(c.filesDir, "site-data"), version, read)
        val runs = Moments.runsOf(JSONObject(site.read("runs-and-lifts.json")))
        val out = Moments.moments(res, history(dir), runs, params)
        write(DayStore.momentsOf(day), JSONObject().put("moments", out.get("moments")).put("items", out.get("items"))
            .put("story", out.get("story")).put("share", out.get("share")).toString())
        write(File(dir, HISTORY), out.getJSONObject("history").toString())
        done += start
        write(File(dir, APPLIED), JSONArray(done.toList()).toString())
    }
}

package io.github.pini236.skiapp.redesign

import android.content.Context
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.SiteData
import io.github.pini236.skiapp.dayrec.DayEngine
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Round 4, board L05 (Android): the permanent notification while the phone is in the pocket. No live speed (a number
 * that updates every few seconds looks stuck): in a descent the run's name, "probably", over the time recorded; on the
 * lift, the descent that just ended, its name and its numbers. "Stop" stays as today. Left out, and logged: the board's
 * "descent 4 · today's top" line and "about 5 min to the top" (no key for the first; the second needs the ride's start,
 * which the live snapshot does not keep). Test build only: the store build keeps today's notification.
 */
internal object Notice {
    class Content(val title: String, val text: String, val big: String?)

    private var names: Pair<Map<String, String>, Map<String, String>>? = null

    /** The runs' shown names (key to name) and the lifts' (id to name), read once from the site's data on the phone. */
    @Synchronized
    private fun names(c: Context): Pair<Map<String, String>, Map<String, String>> = names ?: runCatching {
        @Suppress("DEPRECATION")
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        val version = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val read = { n: String -> c.assets.open("data/$n").bufferedReader().use { it.readText() } }
        val rl = JSONObject(SiteData(File(c.filesDir, "site-data"), version, read).read("runs-and-lifts.json"))
        val p = rl.getJSONArray("pistes"); val l = rl.getJSONArray("lifts")
        val runs = (0 until p.length()).associate { val o = p.getJSONObject(it); o.getString("key") to (o.optString("name").ifBlank { o.getString("key") }) }
        val lifts = (0 until l.length()).associate { val o = l.getJSONObject(it); o.get("id").toString() to (o.optString("name").ifBlank { o.get("id").toString() }) }
        runs to lifts
    }.getOrDefault(emptyMap<String, String>() to emptyMap<String, String>()).also { names = it }

    /** What changes the notification besides the minute: the activity, the lift, the runs, the ended descent. */
    fun key(live: DayEngine.Snapshot?, ride: DayEngine.Ride?): String =
        "${live?.act}|${live?.lift}|${live?.runs?.joinToString(",")}|${ride?.t1}"

    fun content(c: Context, elapsedMs: Long, live: DayEngine.Snapshot?, ride: DayEngine.Ride?): Content? {
        val (runs, lifts) = names(c)
        val title = c.getString(R.string.app_rec_now, LiveNumbers.elapsed(elapsedMs))
        val onLift = live?.act == "lift" && ride != null
        if (onLift) {
            val lift = live!!.lift?.let { lifts[it] ?: it }
            val head = if (lift != null) c.getString(R.string.app_rec_on_lift, lift) else title
            val ran = ride!!.runs.joinToString(" · ") { runs[it] ?: it }.ifEmpty { c.getString(R.string.app_rec_off) }
            val meta = c.getString(R.string.app_tl_run_meta, String.format(Locale.ROOT, "%.1f", ride.distM / 1000.0),
                metres(ride.dropM), ride.maxKmh?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—")
            val text = c.getString(R.string.app_rec_just_ran) + ": " + ran
            return Content(head, text, text + "\n" + meta)
        }
        val on = live?.runs?.takeIf { it.isNotEmpty() && live.act != "lift" } ?: return null
        return Content(title, on.joinToString(" · ") { runs[it] ?: it } + " · " + c.getString(R.string.app_rec_probably), null)
    }
}

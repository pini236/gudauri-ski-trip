package io.github.pini236.skiapp.redesign

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.pini236.skiapp.MainActivity
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.dayrec.ui.LiveNumbers
import io.github.pini236.skiapp.qa.Qa
import org.json.JSONObject
import java.io.File

/**
 * The completion round, part 1 (P04): "one descent" ended. The card waits until the app is opened (it never vanishes by
 * itself); with the phone in the pocket, one ordinary notification in place of the permanent one (the architect,
 * 21.11.2026): its own id and channel, not ongoing, auto cancel, an immutable intent to the card, and nothing without
 * the notifications permission. Its words come from the final analysis written beside the log: the run's name, the top
 * speed and the time; no place, no fall, no name of a person.
 */
internal object RunDone {
    private const val PREFS = "redesign"
    private const val KEY = "run_done"
    private const val CHANNEL = "dayrec_done"
    const val ID = 7603

    /** A day that ended as one descent and whose card has not been closed: its id, why it stopped, and the lift. */
    class Pending(val id: String, val reason: String?, val lift: String?)

    fun pending(ctx: Context): Pending? = runCatching {
        val o = JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null)
        Pending(o.getString("id"), o.optString("reason").ifEmpty { null }, o.optString("lift").ifEmpty { null })
    }.getOrNull()

    fun clear(ctx: Context) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply(); cancel(ctx) }
    fun cancel(ctx: Context) { runCatching { ctx.getSystemService(NotificationManager::class.java)?.cancel(ID) } }

    /** Called by the recorder after the result is written, for a recording of one descent. */
    fun ended(ctx: Context, day: File, autostop: String?, lift: String?) {
        val id = day.name.removePrefix("day-").removeSuffix(".jsonl")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONObject().put("id", id).put("reason", autostop ?: "").put("lift", lift ?: "").toString()).apply()
        Qa.log("redesign run done $id ${autostop ?: "user"}")
        if (!io.github.pini236.skiapp.meet.Reminders.allowed(ctx)) return
        val res = runCatching { JSONObject(DayStore.resultOf(day).readText()) }.getOrNull() ?: return
        val d = res.optJSONArray("segments")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
            .filter { it.optString("type") == "descent" }.maxByOrNull { it.optDouble("dist_m", 0.0) } ?: return
        val runs = LinkedHashSet<String>()
        d.optJSONArray("legs")?.let { a -> for (i in 0 until a.length()) { val l = a.getJSONObject(i); if (l.optString("kind") == "run") l.optJSONArray("runs")?.let { r -> for (j in 0 until r.length()) runs += r.getString(j) } } }
        val names = runNames(ctx)
        val run = if (runs.isEmpty()) ctx.getString(R.string.app_rec_off) else runs.joinToString(" › ") { names[it] ?: it }
        val kmh = if (d.isNull("max_kmh")) "—" else LiveNumbers.maxKmh(d.optDouble("max_kmh"))
        val s = ((d.optLong("t1") - d.optLong("t0")) / 1000).toInt()
        val time = "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.app_run_done_title), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN, "days/$id")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(ctx.getString(R.string.app_run_done_title)).setContentText(ctx.getString(R.string.app_run_done_line, run, kmh, time))
            .setCategory(Notification.CATEGORY_STATUS).setAutoCancel(true).setContentIntent(open).build())
        Qa.log("redesign run done notified")
    }

    /** The runs' names by key, from the site's data the app keeps. */
    private fun runNames(ctx: Context): Map<String, String> = runCatching {
        @Suppress("DEPRECATION")
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val version = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val site = io.github.pini236.skiapp.data.SiteData(File(ctx.filesDir, "site-data"), version) { n -> ctx.assets.open("data/$n").bufferedReader().use { it.readText() } }
        val a = JSONObject(site.read("runs-and-lifts.json")).getJSONArray("pistes")
        (0 until a.length()).associate { i -> val p = a.getJSONObject(i); p.getString("key") to (p.optString("name").ifBlank { p.getString("key") }) }
    }.getOrDefault(emptyMap())
}

package io.github.pini236.skiapp.meet

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.pini236.skiapp.MainActivity
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.qa.Qa
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * The group's meetups remind a quarter of an hour before (Q8, "תזכורת רבע שעה לפני"), from the phone itself: the alarm
 * is set on the phone when the meetups are known, so it rings with no signal on the mountain. Each meetup's reminder is
 * on unless the person turned it off (kept on the phone). The armed list is kept too, so a restart sets them again.
 * The alarms are inexact (no special permission): Android may ring a few minutes late when the phone sleeps deep.
 */
object Reminders {
    const val BEFORE_MIN = 15L
    private const val PREFS = "reminders"
    private const val ACTION = "io.github.pini236.skiapp.REMIND"
    private const val CHANNEL = "meetups"

    /** One meetup to remind of: where (the station's name), the group, its time in Gudauri, and the page it opens. */
    data class Item(val id: String, val at: Instant, val place: String, val group: String, val time: String, val open: String) {
        fun json(): JSONObject = JSONObject().put("id", id).put("at", at.toEpochMilli()).put("place", place).put("group", group).put("time", time).put("open", open)
        companion object {
            fun of(o: JSONObject) = Item(o.getString("id"), Instant.ofEpochMilli(o.getLong("at")), o.getString("place"), o.getString("group"), o.getString("time"), o.optString("open"))
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(c: Context, id: String): Boolean = id !in prefs(c).getStringSet("off", emptySet())!!

    fun set(c: Context, id: String, on: Boolean) {
        val off = prefs(c).getStringSet("off", emptySet())!!.toMutableSet()
        if (on) off -= id else off += id
        prefs(c).edit().putStringSet("off", off).apply()
    }

    /** The notification permission (Android 13 and later), asked once from the meetups (Q8). */
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun asked(c: Context) = prefs(c).getBoolean("asked", false)
    fun markAsked(c: Context) = prefs(c).edit().putBoolean("asked", true).apply()

    /**
     * Sets the alarms of these meetups (the ones whose reminder is on and still ahead), and takes away the ones that
     * are no longer there. The whole list each time: it is all the meetups of all my groups.
     */
    fun arm(c: Context, items: List<Item>, now: Instant = Instant.now()) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val want = due(items, prefs(c).getStringSet("off", emptySet())!!, now)
        val before = armed(c)
        for (old in before) if (want.none { it.id == old.id }) am.cancel(pending(c, old))
        for (it in want) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.at.minusSeconds(BEFORE_MIN * 60).toEpochMilli(), pending(c, it))
        prefs(c).edit().putString("armed", JSONArray(want.map { it.json() }).toString()).apply()
        if (want.size != before.size || want.isNotEmpty()) Qa.log("reminders armed ${want.size}")
    }

    /** The ones to remind of: not turned off, and their quarter of an hour before is still ahead. */
    fun due(items: List<Item>, off: Set<String>, now: Instant): List<Item> =
        items.filter { it.id !in off && it.at.minusSeconds(BEFORE_MIN * 60).isAfter(now) }

    /** After the phone restarts (alarms do not survive it). */
    fun rearm(c: Context) = arm(c, armed(c))

    fun armed(c: Context): List<Item> = runCatching {
        val a = JSONArray(prefs(c).getString("armed", "[]")); (0 until a.length()).map { Item.of(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun pending(c: Context, it: Item): PendingIntent =
        PendingIntent.getBroadcast(c, it.id.hashCode(), Intent(c, ReminderReceiver::class.java).setAction(ACTION).putExtra("item", it.json().toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** The notification: "In 15 minutes: Goodaura", the group and the time; a tap opens the meetup's card. */
    fun show(c: Context, it: Item) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.app_remind_channel), NotificationManager.IMPORTANCE_HIGH))
        if (!allowed(c)) { Qa.log("reminder not allowed"); return }
        val open = PendingIntent.getActivity(c, it.id.hashCode(), Intent(c, MainActivity::class.java).putExtra(MainActivity.OPEN, it.open)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(c.getString(R.string.app_remind_title, it.place))
            .setContentText(c.getString(R.string.app_remind_body, it.group, it.time))
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setContentIntent(open).setAutoCancel(true).build()
        nm.notify(it.id.hashCode(), n)
        Qa.log("reminder shown ${it.id}")
    }

    internal fun fire(c: Context, intent: Intent) {
        val it = runCatching { Item.of(JSONObject(intent.getStringExtra("item") ?: return)) }.getOrNull() ?: return
        show(c, it)
        prefs(c).edit().putString("armed", JSONArray(armed(c).filter { a -> a.id != it.id }.map { a -> a.json() }).toString()).apply()
    }
}

/** The alarm of a reminder, and the restart of the phone (which clears every alarm). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Reminders.rearm(context)
            else -> Reminders.fire(context, intent)
        }
    }
}

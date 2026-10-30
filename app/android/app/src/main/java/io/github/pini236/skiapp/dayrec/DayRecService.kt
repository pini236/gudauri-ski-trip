package io.github.pini236.skiapp.dayrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import io.github.pini236.skiapp.MainActivity
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.qa.Qa
import java.io.File

/**
 * The recorder (docs/ARCHITECTURE.md m-8, conditions 1 to 3): a foreground service of type location with an always-on
 * notification, started by a tap. Every second it writes one line to the day's file ([DayLog]) and goes on with the
 * screen off, because it holds a partial wake lock; a hole of more than ten seconds is written down, not hidden.
 *
 * - The GPS only, through the system's LocationManager (no library, no network location, no activity recognition).
 * - It stops by itself after a long stillness ([IdleWatch]), with a notice first, and when the day's file is full.
 * - If the system kills it the file is left as it is, without a closing line, and the next opening can go on with it
 *   ([DayStore.unfinished]). It does not restart by itself: a start from the background is not allowed anyway.
 */
class DayRecService : Service() {
    private var thread: HandlerThread? = null
    private var lm: LocationManager? = null
    private var sensors: SensorManager? = null
    private var lock: PowerManager.WakeLock? = null
    private var log: DayLog? = null
    private val motion = MotionMeter()
    private val idle = IdleWatch()
    @Volatile private var pressure: Float? = null
    private var firstAt = 0L
    private var lastAt = 0L
    private var warnedLeft = -1L
    private var shownMinute = -1L
    private var stopping = false
    private var started = false

    private val gps = LocationListener { onLocation(it) }
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_PRESSURE -> pressure = e.values[0]
                Sensor.TYPE_ACCELEROMETER -> motion.feed(e.values[0], e.values[1], e.values[2])
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> if (!started) begin(intent.getStringExtra(RESUME)) else Unit
            STOP -> finish(DayRec.REASON_USER)
            else -> if (!started) stopSelf() // a restart by the system with no tap behind it: nothing to record
        }
        return START_NOT_STICKY
    }

    private fun begin(resume: String?) {
        started = true
        val c = Lang.wrap(this)
        if (DayRec.check(this) != null) { abort(); return }
        // the notification first: a foreground service has five seconds to show it
        try {
            val n = notification(c, 0, -1)
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(ID, n)
        } catch (e: Exception) { Qa.log("dayrec cannot start: ${e.javaClass.simpleName}"); abort(); return }

        val store = DayRec.store(this)
        val now = System.currentTimeMillis()
        val file = resume?.let { File(it) }?.takeIf { it.isFile && it.parentFile == store.dir }
        log = try {
            if (file != null) DayLog.reopen(file).also { it.resumed(now) }
            else DayLog.create(store.newFile(now), DayParams.TZ, DayParams.RESORT, DayStore.device())
        } catch (e: Exception) { Qa.log("dayrec cannot write: ${e.javaClass.simpleName}"); abort(); return }
        DayRec.current = log!!.file
        firstAt = now; lastAt = 0L

        // 14 hours is longer than any lift day: a lock that cannot be forgotten
        lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "skiapp:dayrec").apply { acquire(14 * 3600_000L) }
        val t = HandlerThread("dayrec").also { it.start() }; thread = t
        lm = getSystemService(LocationManager::class.java)
        sensors = getSystemService(SensorManager::class.java)
        try {
            @Suppress("MissingPermission")
            lm!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, DayParams.GPS_MIN_TIME_MS, 0f, gps, t.looper)
        } catch (e: Exception) { Qa.log("dayrec no gps: ${e.javaClass.simpleName}"); abort(); return }
        // the accelerometer fast and batched (the hardware collects and hands over every second); the barometer slowly
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME, 1_000_000) }
        sensors?.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sensors?.registerListener(sensorListener, it, 1_000_000) }
        DayRec.started()
    }

    private fun onLocation(l: Location) {
        val log = log ?: return
        val t = if (l.time > 0) l.time else System.currentTimeMillis()
        if (t <= lastAt) return // the same second twice, or the clock stepping back
        if (lastAt != 0L && t - lastAt > DayParams.GAP_MS) log.gap(lastAt, t)
        if (lastAt == 0L) firstAt = t
        lastAt = t
        log.add(DayLog.Point(
            t, l.latitude, l.longitude,
            alt = if (l.hasAltitude()) l.altitude else null, pressure = pressure,
            speed = if (l.hasSpeed()) l.speed else null,
            speedAcc = if (Build.VERSION.SDK_INT >= 26 && l.hasSpeedAccuracy()) l.speedAccuracyMetersPerSecond else null,
            hAcc = if (l.hasAccuracy()) l.accuracy else null,
            vAcc = if (Build.VERSION.SDK_INT >= 26 && l.hasVerticalAccuracy()) l.verticalAccuracyMeters else null,
            bearing = if (l.hasBearing()) l.bearing else null, motion = motion.take(),
        ))
        if (log.full) { finish(DayRec.REASON_FULL); return }
        when (val v = idle.feed(t, Geo.x(l.longitude), Geo.y(l.latitude))) {
            IdleWatch.Verdict.Fine -> if (warnedLeft >= 0) { warnedLeft = -1; refresh(t) }
            is IdleWatch.Verdict.Warn -> { warnedLeft = v.leftMs; refresh(t) }
            is IdleWatch.Verdict.Stop -> { finish(if (v.outside) DayRec.REASON_OUTSIDE else DayRec.REASON_IDLE); return }
        }
        refresh(t)
    }

    /** The notification again, when the minute changes (and for the warning), not every second. */
    private fun refresh(t: Long) {
        val minutes = (t - firstAt) / 60_000L
        val key = minutes * 2 + if (warnedLeft >= 0) 1 else 0
        if (key == shownMinute) return
        shownMinute = key
        val c = Lang.wrap(this)
        getSystemService(NotificationManager::class.java)?.notify(ID, notification(c, minutes.toInt(), warnedLeft))
    }

    private fun notification(c: Context, minutes: Int, warnLeftMs: Long): Notification {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.app_dayrec_channel), NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags)
        val stop = PendingIntent.getService(c, 2, Intent(c, DayRecService::class.java).setAction(STOP), flags)
        val text = if (warnLeftMs >= 0) c.getString(R.string.app_dayrec_idle, ((warnLeftMs + 59_999) / 60_000).toInt()) else c.getString(R.string.app_dayrec_text, minutes)
        return Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(c.getString(R.string.app_dayrec_title)).setContentText(text)
            .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, c.getString(R.string.app_dayrec_stop), stop).build())
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }

    /** Ends the day on purpose: the closing line, the event, and the service goes away. */
    private fun finish(reason: String) {
        if (stopping) return
        stopping = true
        val minutes = if (lastAt > firstAt) ((lastAt - firstAt) / 60_000L).toInt() else 0
        runCatching { log?.end(if (lastAt > 0) lastAt else System.currentTimeMillis(), reason) }
        if (log != null) DayRec.stopped(minutes, reason)
        log = null
        teardown()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    /** The service could not start: nothing was recorded, so nothing is reported. */
    private fun abort() {
        stopping = true
        runCatching { log?.release() }; log = null
        teardown()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }; stopSelf()
    }

    private fun teardown() {
        runCatching { lm?.removeUpdates(gps) }
        runCatching { sensors?.unregisterListener(sensorListener) }
        runCatching { thread?.quitSafely() }; thread = null
        runCatching { lock?.takeIf { it.isHeld }?.release() }; lock = null
        DayRec.current = null
    }

    /** Killed by the system, or the app was closed: the file stays as it is, cut, and can be gone on with. */
    override fun onDestroy() {
        if (!stopping) { runCatching { log?.release() }; log = null; teardown() }
        super.onDestroy()
    }

    companion object {
        const val START = "io.github.pini236.skiapp.dayrec.START"
        const val STOP = "io.github.pini236.skiapp.dayrec.STOP"
        const val RESUME = "resume"
        private const val CHANNEL = "dayrec"
        private const val ID = 7601
    }
}

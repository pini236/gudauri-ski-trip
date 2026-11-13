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
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import io.github.pini236.skiapp.MainActivity
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.qa.Diag
import io.github.pini236.skiapp.qa.Qa
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * The recorder (docs/ARCHITECTURE.md m-8, conditions 1 to 3): a foreground service of type location with an always-on
 * notification, started by a tap. Every second it writes one line to the day's file ([DayLog]) and goes on with the
 * screen off, because it holds a partial wake lock. A hole in the points is not written: the analysis finds it in the time
 * between two points (gap_s in dayrec-params.json). Around a blow (condition 10) a window of the motion sensors is written.
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
    @Volatile private var log: DayLog? = null
    private val motion = MotionMeter()
    @Volatile private var impacts: ImpactBuffer? = null
    private var clockOffset = 0L
    private val idle = IdleWatch()
    @Volatile private var pressure: Float? = null
    private var firstAt = 0L
    private var lastAt = 0L
    private var warnedLeft = -1L
    private var shownMinute = -1L
    private var stopping = false
    private var started = false
    // the analysis (decision 76): fed on the recorder's own thread, so no lock
    private var handler: Handler? = null
    private var engine: DayEngine? = null
    private var nextLiveAt = 0L
    private var nextFinalAt = 0L
    private var finals: ExecutorService? = null
    private val finalBusy = AtomicBoolean(false)
    @Volatile private var runMode = false
    // for the test build's diagnostic log (qa/Diag): counts and states only, never a position
    private var fixes = 0
    @Volatile private var satsUsed = 0
    @Volatile private var satsSeen = 0
    private var gnssOn = false
    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            satsUsed = used; satsSeen = status.satelliteCount
        }
        override fun onFirstFix(ttffMillis: Int) { Diag.log("dayrec gnss first fix after ${ttffMillis}ms") }
    }
    private val pulse = object : Runnable {
        override fun run() {
            if (stopping) return
            val last = DayRec.lastFixAt
            Diag.log("dayrec pulse fixes=$fixes lastFix=${if (last > 0) "${(System.currentTimeMillis() - last) / 1000}s ago" else "none"} sats=$satsUsed/$satsSeen mode=${if (runMode) "run" else "day"} live=${DayRec.live != null} engine=${engine != null}")
            // no reading at all this long after the start: ask the GPS again (some phones leave the first request unanswered), a few times
            if (fixes == 0 && kicks < MAX_KICKS && System.currentTimeMillis() - DayRec.startedAt >= KICK_AFTER_MS) {
                kicks++
                runCatching {
                    lm?.removeUpdates(gps)
                    @Suppress("MissingPermission")
                    lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, DayParams.GPS_MIN_TIME_MS, 0f, gps, handler!!.looper)
                    Diag.log("dayrec no reading yet: the GPS asked again ($kicks)")
                }.onFailure { Diag.log("dayrec the GPS could not be asked again: ${it.javaClass.simpleName}") }
            }
            handler?.postDelayed(this, PULSE_MS)
        }
    }
    private var kicks = 0

    private val gps = LocationListener { onLocation(it) }
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_PRESSURE -> pressure = e.values[0]
                Sensor.TYPE_ACCELEROMETER -> {
                    motion.feed(e.values[0], e.values[1], e.values[2])
                    impacts?.feedAcc(e.timestamp / 1_000_000L + clockOffset, e.values[0], e.values[1], e.values[2])
                }
                Sensor.TYPE_GYROSCOPE -> impacts?.feedGyro(e.values[0], e.values[1], e.values[2])
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Diag.log("dayrec service command ${intent?.action?.substringAfterLast('.') ?: "none"} started=$started stopping=$stopping")
        when (intent?.action) {
            START -> if (!started) begin(intent.getStringExtra(RESUME), intent.getBooleanExtra(RUN, false)) else Unit
            STOP -> finish(DayRec.REASON_USER)
            SWITCH -> if (started && !stopping) switchMode(intent.getBooleanExtra(RUN, false), intent.getLongExtra(AT, 0L))
            else -> if (!started) stopSelf() // a restart by the system with no tap behind it: nothing to record
        }
        return START_NOT_STICKY
    }

    /**
     * All day to one descent or back, inside the same recording: nothing is stopped, the counter and the map go on, and
     * the earlier readings stay in the log. One descent counts from now (the choice is made in the first moments).
     */
    private fun switchMode(run: Boolean, at: Long) {
        handler?.post {
            if (runMode == run) return@post
            runMode = run
            engine?.setRun(run, if (lastAt > 0) lastAt else System.currentTimeMillis())
            nextLiveAt = 0L
            DayRec.single = run
            Diag.log("dayrec mode ${if (run) "run" else "day"} applied${if (at > 0) " ${System.currentTimeMillis() - at}ms after the tap" else ""}")
        }
    }

    /**
     * Starts the recording. Only what must be done at once is done here, on the main thread (the notification, which a
     * foreground service has five seconds to show, the wake lock and the thread); the day's file, the analysis' network, the
     * GPS and the sensors are set up on the recorder's own thread ([setup]), because the calls to the system's location and
     * sensor services can block for a while on some phones (a cold GPS chip) and the main thread is also drawing the live
     * screen that the tap just opened.
     */
    private fun begin(resume: String?, run: Boolean) {
        started = true
        val begun = SystemClock.elapsedRealtime()
        val c = Lang.wrap(this)
        DayRec.check(this)?.let { Diag.log("dayrec cannot start: $it"); abort(); return }
        // the notification first: a foreground service has five seconds to show it
        try {
            val n = notification(c, 0, -1)
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(ID, n)
        } catch (e: Exception) { Diag.log("dayrec cannot start: ${e.javaClass.simpleName} ${e.message?.take(80)}"); abort(); return }
        Diag.log("dayrec foreground up after ${SystemClock.elapsedRealtime() - begun}ms")

        runMode = run
        DayRec.live = null; DayRec.ride = null; DayRec.sofar = null; DayRec.speedNow = null; DayRec.since = 0L; DayRec.single = run; DayRec.startedAt = DayRec.tapAt.takeIf { it > 0 && System.currentTimeMillis() - it < 15_000L } ?: System.currentTimeMillis(); DayRec.lastFixAt = 0L
        clockOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        impacts = ImpactBuffer(DayParams.FALL_TRIGGER_G, DayParams.FALL_PRE_S, DayParams.FALL_POST_S, DayParams.FALL_DEBOUNCE_S, DayParams.FALL_HZ) { t, t0, hz, a, g ->
            runCatching { log?.impact(t, t0, hz, a, g) }
            // the sensors and the engine are the recorder thread's: the blow goes over as the log has it
            handler?.post { engine?.addEvent(DayLog.impactJson(t, t0, hz, a, g)) }
        }
        firstAt = System.currentTimeMillis(); lastAt = 0L; fixes = 0; kicks = 0; DayRec.fixCount = 0

        // 14 hours is longer than any lift day: a lock that cannot be forgotten
        lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "skiapp:dayrec").apply { acquire(14 * 3600_000L) }
        val t = HandlerThread("dayrec").also { it.start() }; thread = t
        handler = Handler(t.looper)
        finals = Executors.newSingleThreadExecutor()
        lm = getSystemService(LocationManager::class.java)
        sensors = getSystemService(SensorManager::class.java)
        handler!!.post { setup(resume, run, begun) }
    }

    /** On the recorder's thread: the file, the readings' sources and the analysis. Anything that fails ends the start cleanly. */
    private fun setup(resume: String?, run: Boolean, begun: Long) {
        if (stopping) return
        val fail = { why: String -> Diag.log("dayrec cannot start: $why"); Handler(Looper.getMainLooper()).post { abort() } }
        val store = DayRec.store(this)
        val now = System.currentTimeMillis()
        val file = resume?.let { File(it) }?.takeIf { it.isFile && it.parentFile == store.dir }
        val made = try {
            if (file != null) DayLog.reopen(file).also { it.resumed(now) }
            else DayLog.create(store.newFile(now), DayParams.TZ, DayParams.RESORT, DayStore.device(), now, mode = if (run) "run" else "day")
        } catch (e: Exception) { fail("cannot write ${e.javaClass.simpleName}"); return }
        log = made
        DayRec.current = made.file
        val before = file?.let { runCatching { DayLog.read(it) }.getOrNull() }
        runMode = (before?.mode ?: if (run) "run" else "day") == "run"
        if (before != null) DayRec.single = runMode
        val header = before?.header ?: JSONObject().put("v", DayLog.VERSION).put("tz", DayParams.TZ).put("resort", DayParams.RESORT).put("mode", if (runMode) "run" else "day")
        // the GPS first, before the analysis' network is loaded: the readings queue behind this task on the same thread, so none is lost
        try {
            @Suppress("MissingPermission")
            lm!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, DayParams.GPS_MIN_TIME_MS, 0f, gps, handler!!.looper)
        } catch (e: Exception) { fail("no gps ${e.javaClass.simpleName}"); return }
        val gpsAt = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= 24) runCatching {
            @Suppress("MissingPermission", "DEPRECATION")
            gnssOn = lm!!.registerGnssStatusCallback(gnss, handler)
        }
        Diag.log("dayrec gps asked for after ${gpsAt - begun}ms, satellite status ${if (gnssOn) "on" else "off"} took ${SystemClock.elapsedRealtime() - gpsAt}ms")
        // the accelerometer and the gyroscope at 50 a second, batched (the hardware collects and hands over every second); the
        // barometer slowly. The windows around a blow come from these two (condition 10). They call on this thread, not the main one.
        val period = 1_000_000 / DayParams.FALL_HZ
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors?.registerListener(sensorListener, it, period, 1_000_000, handler) }
        sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors?.registerListener(sensorListener, it, period, 1_000_000, handler) }
        sensors?.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sensors?.registerListener(sensorListener, it, 1_000_000, handler) }
        Diag.log("dayrec sensors registered after ${SystemClock.elapsedRealtime() - begun}ms")
        DayRec.started()
        Diag.log("dayrec ready ${System.currentTimeMillis() - DayRec.startedAt}ms after the tap (setup ${SystemClock.elapsedRealtime() - begun}ms) mode=${if (runMode) "run" else "day"}")
        handler?.postDelayed(pulse, PULSE_MS)
        // the network and the numbers (a second or two): the readings that come meanwhile wait in the queue
        runCatching {
            val e = DayAnalysis.engine(this, header)
            before?.let { d -> d.points.forEach(e::add); d.events.forEach(e::addEvent) }
            e.onRide = { DayRec.ride = it; Qa.log("dayrec ride ${it.runs.size} runs") }
            engine = e
            Diag.log("dayrec analysis ready after ${SystemClock.elapsedRealtime() - begun}ms")
        }.onFailure { Diag.log("dayrec no analysis: ${it.javaClass.simpleName}") }
    }

    private fun onLocation(l: Location) {
        val log = log ?: return
        val t = if (l.time > 0) l.time else System.currentTimeMillis()
        if (t <= lastAt) return // the same second twice, or the clock stepping back
        if (lastAt != 0L && t - lastAt > DayParams.GAP_MS) Diag.log("dayrec hole ${(t - lastAt) / 1000}s")
        if (lastAt == 0L) {
            firstAt = t
            Diag.log("dayrec first fix ${System.currentTimeMillis() - DayRec.startedAt}ms after the tap, accuracy ${if (l.hasAccuracy()) "${l.accuracy.toInt()}m" else "?"}, sats $satsUsed/$satsSeen")
        }
        fixes++; DayRec.fixCount = fixes
        lastAt = t
        val point = DayLog.Point(
            t, l.latitude, l.longitude,
            alt = if (l.hasAltitude()) l.altitude else null, pressure = pressure?.toDouble(),
            speed = if (l.hasSpeed()) l.speed.toDouble() else null,
            speedAcc = if (Build.VERSION.SDK_INT >= 26 && l.hasSpeedAccuracy()) l.speedAccuracyMetersPerSecond.toDouble() else null,
            hAcc = if (l.hasAccuracy()) l.accuracy.toDouble() else null,
            vAcc = if (Build.VERSION.SDK_INT >= 26 && l.hasVerticalAccuracy()) l.verticalAccuracyMeters.toDouble() else null,
            bearing = if (l.hasBearing()) l.bearing.toDouble() else null, motion = motion.take()?.toDouble(),
        )
        log.add(point)
        DayRec.speedNow = point.speed; DayRec.lastFixAt = System.currentTimeMillis(); DayRec.since = firstAt // (DayRec.single is the screens' word and the switch's: a reading must not put it back before the switch is applied)
        analyse(point)
        if (stopping) return
        if (log.atLimit) { finish(DayRec.REASON_FULL); return }
        when (val v = idle.feed(t, Geo.x(l.longitude), Geo.y(l.latitude))) {
            IdleWatch.Verdict.Fine -> if (warnedLeft >= 0) { warnedLeft = -1; refresh(t) }
            is IdleWatch.Verdict.Warn -> { warnedLeft = v.leftMs; refresh(t) }
            is IdleWatch.Verdict.Stop -> { finish(if (v.outside) DayRec.REASON_OUTSIDE else DayRec.REASON_IDLE); return }
        }
        refresh(t)
    }

    /**
     * The analysis of what was just written (decision 76): the live view every [LIVE_EVERY_S] seconds (every second in a
     * single run, whose stop is decided from it), and the whole day smoothed now and then for the screens, on a thread of its
     * own. A failure here never touches the recording: the log is the truth, and the result can be computed again from it.
     */
    private fun analyse(p: DayLog.Point) {
        val e = engine ?: return
        e.add(p)
        if (p.t < nextLiveAt) return
        nextLiveAt = p.t + (if (runMode) 1_000L else LIVE_EVERY_S * 1_000L)
        val s = runCatching { e.live() }.onFailure { Qa.log("dayrec live failed: ${it.javaClass.simpleName}") }.getOrNull() ?: return
        DayRec.live = s
        s.autostop?.takeIf { runMode }?.let { Qa.log("dayrec autostop ${it.reason}"); finish(DayRec.REASON_AUTO); return }
        if (!runMode && p.t >= nextFinalAt && finalBusy.compareAndSet(false, true)) {
            nextFinalAt = p.t + FINAL_EVERY_MS
            finals?.execute {
                try { DayRec.sofar = e.final() } catch (x: Throwable) { Qa.log("dayrec final failed: ${x.javaClass.simpleName}") } finally { finalBusy.set(false) }
            } ?: finalBusy.set(false)
        }
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

    /**
     * Ends the day on purpose: the closing line, the event, and the service goes away. Before it does, the day's result is
     * written beside the log, on a thread of its own and with the notification still up (a day of eight hours takes a few
     * seconds to analyse, too long for the main thread); if the system kills the process in between, the result is made the
     * next time it is asked for ([DayAnalysis.ensure]).
     */
    private fun finish(reason: String) {
        if (stopping) return
        stopping = true
        val minutes = if (lastAt > firstAt) ((lastAt - firstAt) / 60_000L).toInt() else 0
        val at = if (lastAt > 0) lastAt else System.currentTimeMillis()
        val file = log?.file
        runCatching { if (reason == DayRec.REASON_FULL) log?.full(at) else log?.stop(at, reason) }
        Diag.log("dayrec finishing reason=$reason fixes=$fixes")
        if (log != null) DayRec.stopped(minutes, reason)
        log = null
        DayRec.finishing = file != null
        teardown()
        val ctx = applicationContext
        val done = {
            DayRec.finishing = false
            Handler(Looper.getMainLooper()).post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            Unit
        }
        if (file == null) { done(); return }
        Thread {
            runCatching { DayAnalysis.ensure(ctx, file) }.onFailure { Qa.log("dayrec result failed: ${it.javaClass.simpleName}") }
                .onSuccess { Qa.log("dayrec result ${if (it != null) "written" else "none"}") }
            done()
        }.start()
    }

    /** The service could not start: nothing was recorded, so nothing is reported. */
    private fun abort() {
        stopping = true
        DayRec.tapAt = 0L
        runCatching { log?.release() }; log = null
        teardown()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }; stopSelf()
    }

    private fun teardown() {
        runCatching { handler?.removeCallbacks(pulse) }
        if (Build.VERSION.SDK_INT >= 24) runCatching { lm?.unregisterGnssStatusCallback(gnss) }
        runCatching { lm?.removeUpdates(gps) }
        runCatching { sensors?.unregisterListener(sensorListener) }
        impacts?.clear(); impacts = null
        engine = null; handler = null
        runCatching { finals?.shutdown() }; finals = null
        runCatching { thread?.quitSafely() }; thread = null
        runCatching { lock?.takeIf { it.isHeld }?.release() }; lock = null
        DayRec.current = null
    }

    /** Killed by the system, or the app was closed: the file stays as it is, cut, and can be gone on with. */
    override fun onDestroy() {
        Diag.log("dayrec service destroyed stopping=$stopping fixes=$fixes")
        if (!stopping) { runCatching { log?.release() }; log = null; teardown() }
        super.onDestroy()
    }

    companion object {
        const val START = "io.github.pini236.skiapp.dayrec.START"
        const val STOP = "io.github.pini236.skiapp.dayrec.STOP"
        const val RESUME = "resume"
        const val RUN = "run"
        const val SWITCH = "switch"
        /** When the switch was tapped (epoch ms), so the diagnostic log can say how long the service took to apply it. */
        const val AT = "at"
        private const val PULSE_MS = 15_000L
        private const val KICK_AFTER_MS = 20_000L
        private const val MAX_KICKS = 4
        /** The live view is worked out this often in a day (in a single run, every second). */
        const val LIVE_EVERY_S = 5L
        /** The whole day so far, smoothed, for the screens. */
        const val FINAL_EVERY_MS = 120_000L
        private const val CHANNEL = "dayrec"
        private const val ID = 7601
    }
}

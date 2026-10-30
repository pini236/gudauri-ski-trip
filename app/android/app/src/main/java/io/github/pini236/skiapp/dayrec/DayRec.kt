package io.github.pini236.skiapp.dayrec

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import java.io.File

/**
 * The ski day's recorder, seen from the screens (docs/FEATURES.md section 10, docs/ARCHITECTURE.md m-8). Starting is
 * always a tap, from the open app (Android 12 needs that for a foreground service of type location): there is no
 * start in the background and no "always" permission (condition 2).
 *
 * Nothing in this package sends, measures or logs a position. The only things that leave are the two events
 * `dayrec_start` and `dayrec_stop` with minutes and a reason (condition 1, docs/GROWTH.md).
 */
object DayRec {
    enum class Start {
        Started,
        /** Nothing is allowed yet: the screen asks for the precise location first. */
        NeedPermission,
        /** Only the approximate location is allowed: no recording, and the screen says why (condition 2). */
        NeedPrecise,
        /** The phone's location is switched off. */
        NoLocation,
        Already,
    }

    /** The day being recorded, or null. Set by the service. */
    @Volatile var current: File? = null; internal set
    /** The day just stopped is still being analysed (its result is written before the service goes): no new start meanwhile. */
    @Volatile internal var finishing = false
    val recording get() = current != null || finishing

    /** What the live view says now (the screens read it; see [DayEngine.live]), or null when nothing is recorded. */
    @Volatile var live: DayEngine.Snapshot? = null; internal set
    /** The descent that just ended, while the skier rides the lift. Null until the first one. */
    @Volatile var ride: DayEngine.Ride? = null; internal set
    /** The whole day so far, smoothed (every [DayRecService.FINAL_EVERY_MS]); the live view's numbers come from [live]. */
    @Volatile var sofar: io.github.pini236.skiapp.dayrec.analysis.Analyzer.DayResult? = null; internal set

    fun store(ctx: Context) = DayStore(ctx.applicationContext.filesDir)

    /** [run]: a recording of one descent, which stops by itself at the bottom (docs/FEATURES.md section 10, 1a). */
    fun start(ctx: Context, resume: File? = null, run: Boolean = false): Start {
        if (recording) return Start.Already
        val check = check(ctx)
        if (check != null) return check
        val i = Intent(ctx, DayRecService::class.java).setAction(DayRecService.START)
        resume?.let { i.putExtra(DayRecService.RESUME, it.absolutePath) }
        if (run) i.putExtra(DayRecService.RUN, true)
        ContextCompat.startForegroundService(ctx, i)
        return Start.Started
    }

    /** Stops by the user's tap. */
    fun stop(ctx: Context) {
        if (!recording) return
        ctx.startService(Intent(ctx, DayRecService::class.java).setAction(DayRecService.STOP))
    }

    internal fun check(ctx: Context): Start? {
        val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine) return if (coarse) Start.NeedPrecise else Start.NeedPermission
        val lm = ctx.getSystemService(LocationManager::class.java)
        val on = runCatching {
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        return if (on && runCatching { LocationManager.GPS_PROVIDER in lm.allProviders }.getOrDefault(false)) null else Start.NoLocation
    }

    /**
     * A day that was cut (the app was killed, the phone switched off) and that the user does not go on with: closes it
     * with a line of its own, so it is a finished day from now on (reason `crash_recovery`).
     */
    fun finishCut(ctx: Context, file: File) {
        val day = runCatching { DayLog.read(file) }.getOrNull() ?: return
        if (!day.cut) return
        val last = day.points.lastOrNull()?.t ?: System.currentTimeMillis()
        runCatching { DayLog.reopen(file).also { it.stop(last, REASON_CRASH) } }
        runCatching { DayAnalysis.ensure(ctx, file) } // heavy for a long day: callers are off the main thread
        stopped(minutes = ((last - (day.points.firstOrNull()?.t ?: last)) / 60_000L).toInt(), reason = REASON_CRASH)
    }

    internal fun started() { Telemetry.event("dayrec_start"); Qa.log("dayrec start") }

    internal fun stopped(minutes: Int, reason: String) {
        Telemetry.event("dayrec_stop", mapOf("minutes" to minutes, "reason" to reason))
        Qa.log("dayrec stop $reason ${minutes}m")
    }

    const val REASON_USER = "user"
    const val REASON_IDLE = "idle"
    const val REASON_OUTSIDE = "outside"
    const val REASON_CRASH = "crash_recovery"
    const val REASON_FULL = "full"
    /** A single run that stopped by itself at the bottom. */
    const val REASON_AUTO = "auto"
}

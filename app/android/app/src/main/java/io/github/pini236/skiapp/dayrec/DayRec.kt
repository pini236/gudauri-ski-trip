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
    val recording get() = current != null

    fun store(ctx: Context) = DayStore(ctx.applicationContext.filesDir)

    fun start(ctx: Context, resume: File? = null): Start {
        if (recording) return Start.Already
        val check = check(ctx)
        if (check != null) return check
        val i = Intent(ctx, DayRecService::class.java).setAction(DayRecService.START)
        resume?.let { i.putExtra(DayRecService.RESUME, it.absolutePath) }
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
}

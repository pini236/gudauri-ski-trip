package io.github.pini236.skiapp.qa

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import io.github.pini236.skiapp.BuildConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * A short diagnostic log in the TEST build (the ski day's recording on a real phone), so that a report like "the counter is
 * stuck" can be read from the phone and not guessed: permission and location state, whether the service started and how
 * long it took, the first reading and then a pulse every 15 seconds, the mode switches, and the screen's frame figures
 * while recording. It never holds a position, a place or a name: counts, milliseconds and states only.
 *
 * Off in the store build (the same switch as the ski day's screens, [BuildConfig.DAYREC_UI]): nothing is written there.
 * The file is small (it keeps the last ~48 KB) and is sent from the about page, test build only (fieldtools/FieldTools).
 * Every line also goes to [Qa.log], which the emulator run in GitHub reads.
 */
object Diag {
    @Volatile private var file: File? = null
    private val ex = Executors.newSingleThreadExecutor { r -> Thread(r, "diag").apply { isDaemon = true } }
    private val stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    const val KEEP = 48_000
    const val MAX = 64_000

    fun init(ctx: Context) {
        if (!BuildConfig.DAYREC_UI) return
        file = File(ctx.applicationContext.filesDir, "diag.txt")
    }

    /** The file to share, or null when there is nothing (or this is the store build). */
    fun file(): File? = file?.takeIf { it.isFile && it.length() > 0 }

    fun log(msg: String) {
        Qa.log(msg)
        val f = file ?: return
        val line = stamp.format(Instant.now()) + " " + msg + "\n"
        ex.execute {
            runCatching {
                f.appendText(line)
                if (f.length() > MAX) f.writeText(trim(f.readText()))
            }
        }
    }

    /** The newest [KEEP] characters, from the start of a line. */
    fun trim(text: String): String {
        if (text.length <= KEEP) return text
        val cut = text.length - KEEP
        val nl = text.indexOf('\n', cut)
        return if (nl in 0 until text.length - 1) text.substring(nl + 1) else text.substring(cut)
    }

    /** What the phone is, and why the last run of the app ended (the system's own record), at every start of the app. */
    fun startup(ctx: Context, version: String) {
        if (file == null) return
        val app = ctx.applicationContext
        ex.execute {
            runCatching {
                val am = app.getSystemService(ActivityManager::class.java)
                val pm = app.getSystemService(PowerManager::class.java)
                val restricted = if (Build.VERSION.SDK_INT >= 28) am.isBackgroundRestricted else false
                val ignoring = pm.isIgnoringBatteryOptimizations(app.packageName)
                val saver = pm.isPowerSaveMode
                log("app start v$version · ${Build.MANUFACTURER} ${Build.MODEL} · android ${Build.VERSION.SDK_INT} · bgRestricted=$restricted · batteryOptimizationOff=$ignoring · powerSave=$saver")
                if (Build.VERSION.SDK_INT >= 30) {
                    val why = am.getHistoricalProcessExitReasons(app.packageName, 0, 3)
                        .joinToString(" | ") { "reason=${it.reason} importance=${it.importance} ${stamp.format(Instant.ofEpochMilli(it.timestamp))}" }
                    if (why.isNotEmpty()) log("earlier runs ended: $why")
                }
            }
        }
    }
}

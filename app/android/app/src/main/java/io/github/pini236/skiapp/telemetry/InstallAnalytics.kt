package io.github.pini236.skiapp.telemetry

import android.content.Context

/**
 * app_open.first_open counts freshly installed apps that were opened, never downloads or people.
 * Only a local boolean is stored; installation timestamps stay on the phone.
 */
object InstallAnalytics {
    private const val PREFS = "app_open"
    private const val LAUNCHED = "launch_recorded"

    /**
     * Call on every cold launch, even with analytics disabled: enabling analytics later must not turn an existing
     * installation into a new one. The old Install Referrer marker also rules out launches from older versions.
     * Unknown installation times, and installs updated before their first launch, are conservatively excluded.
     */
    @Synchronized
    fun recordLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(LAUNCHED, false)) return false
        val legacyOpened = prefs.getBoolean("first", false)
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val first = qualifiesFirstOpen(
            launchRecorded = false,
            legacyOpened = legacyOpened,
            firstInstallTime = installed?.firstInstallTime ?: 0L,
            lastUpdateTime = installed?.lastUpdateTime ?: 0L,
        )
        // Finish the one-time disk write before tagging the event: process termination must not leave a
        // successfully reported first launch with an unpersisted marker. A failed write is not counted as new.
        val recorded = prefs.edit().putBoolean(LAUNCHED, true).commit()
        return first && recorded
    }

    internal fun qualifiesFirstOpen(
        launchRecorded: Boolean,
        legacyOpened: Boolean,
        firstInstallTime: Long,
        lastUpdateTime: Long,
    ): Boolean = !launchRecorded && !legacyOpened && firstInstallTime > 0L && firstInstallTime == lastUpdateTime
}

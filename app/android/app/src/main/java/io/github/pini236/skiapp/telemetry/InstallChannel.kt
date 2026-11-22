package io.github.pini236.skiapp.telemetry

import android.content.Context
import android.os.Build

/**
 * install_channel, a property of every event (docs/GROWTH.md): how this copy of the app got onto the phone.
 * A closed list, so a raw package name never leaves the phone; anything that does not match is [UNKNOWN].
 * A property of the build and the phone, not of a person: registered with the other common properties, no identify().
 */
object InstallChannel {
    const val STORE = "store"
    const val SIDELOAD = "sideload"
    const val PREVIEW = "preview"
    const val EMULATOR = "emulator"
    const val UNKNOWN = "unknown"

    val ALL = setOf(STORE, SIDELOAD, PREVIEW, EMULATOR, UNKNOWN)

    private const val PLAY = "com.android.vending"

    // no installer at all (adb, a file the person opened) or the system's own package installers
    private val SIDELOAD_INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")

    /**
     * The emulator run and the test build come from the build, not from a guess about the hardware: [emulator] is
     * Telemetry.qaRun(), [preview] is the preview flavor. Then the installer: Google Play is "store" (it does not tell the
     * closed test from production, so testers still in the closed test mark themselves is_internal), no installer or a
     * system installer is "sideload", any other package is "unknown".
     */
    fun of(emulator: Boolean, preview: Boolean, installer: String?): String = when {
        emulator -> EMULATOR
        preview -> PREVIEW
        installer == PLAY -> STORE
        installer.isNullOrEmpty() || installer in SIDELOAD_INSTALLERS -> SIDELOAD
        else -> UNKNOWN
    }

    /** installingPackageName from API 30, getInstallerPackageName below it; a failed read is unknown, never a crash. */
    fun detect(context: Context, emulator: Boolean, preview: Boolean): String {
        if (emulator || preview) return of(emulator, preview, null)
        return runCatching {
            @Suppress("DEPRECATION")
            val installer = if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                else context.packageManager.getInstallerPackageName(context.packageName)
            of(false, false, installer)
        }.getOrDefault(UNKNOWN)
    }
}

/**
 * is_internal, a property of every event: true only on a phone that was marked by hand (Pini, testers). Hidden switch in
 * the settings (a long press on the version line), kept on the phone, never sent to our server, never tied to an account.
 * When off the property is not sent at all.
 */
object InternalFlag {
    private const val PREFS = "telemetry"
    private const val KEY = "internal"

    fun on(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }

    /** The properties the flag adds to the common ones: only "is_internal" = true, nothing when off. */
    fun props(on: Boolean): Map<String, Any> = if (on) mapOf("is_internal" to true) else emptyMap()
}

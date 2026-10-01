package io.github.pini236.skiapp.telemetry

import android.content.Context
import com.posthog.PostHog
import com.posthog.PersonProfiles
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import io.github.pini236.skiapp.BuildConfig
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/**
 * Usage and crash reporting (decision 32, docs/PRIVACY.md): PostHog for what is used, Sentry for crashes, both in
 * the EU, nothing that identifies a person, and one switch that turns both off.
 *
 * - The keys exist only in GitHub's secret store and reach the app when a build is made there (build.gradle.kts).
 *   A build without them (local, the emulator run) sends nothing at all.
 * - Anonymous: no identify(), no person profiles, no screen recording, no default personal data in crash reports.
 *   The id is PostHog's random install id; it changes when the app is reinstalled. Dropping the IP address is a
 *   project setting on both services (docs/APP-NATIVE.md, "Usage and crashes").
 * - The switch (settings, 13.7) is kept on the phone; off means not started at all, and turning it off later stops
 *   both at once.
 * - Event names are the site's (docs/GROWTH.md): page_view, run_open, run_fly, game_start, game_end, …
 */
object Telemetry {
    private const val PREFS = "telemetry"
    @Volatile private var usage = false
    @Volatile private var crashes = false

    val hasKeys: Boolean get() = BuildConfig.POSTHOG_KEY.isNotBlank() || BuildConfig.SENTRY_DSN.isNotBlank()

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)

    fun start(context: Context, channel: String) {
        if (!enabled(context)) return
        val app = context.applicationContext
        if (BuildConfig.SENTRY_DSN.isNotBlank() && !crashes) {
            SentryAndroid.init(app) { o ->
                o.dsn = BuildConfig.SENTRY_DSN
                o.isSendDefaultPii = false
                o.environment = channel
                o.tracesSampleRate = 0.0
                o.isAttachScreenshot = false
                o.isAttachViewHierarchy = false
            }
            crashes = true
        }
        if (BuildConfig.POSTHOG_KEY.isNotBlank() && !usage) {
            val config = PostHogAndroidConfig(apiKey = BuildConfig.POSTHOG_KEY, host = BuildConfig.POSTHOG_HOST).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = false // our own page_view, with the site's names
                captureDeepLinks = false
                sessionReplay = false
                personProfiles = PersonProfiles.NEVER
            }
            PostHogAndroid.setup(app, config)
            PostHog.optIn()
            usage = true
        }
    }

    /** The user's switch. Off stops both now and keeps them off on the next launches. */
    fun setEnabled(context: Context, on: Boolean, channel: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        if (on) {
            if (usage) PostHog.optIn()
            start(context, channel)
        } else {
            if (usage) { PostHog.optOut(); PostHog.close(); usage = false }
            if (crashes) { Sentry.close(); crashes = false }
        }
    }

    fun event(name: String, props: Map<String, Any> = emptyMap()) {
        if (usage) PostHog.capture(event = name, properties = props + ("platform" to "android"))
    }
}

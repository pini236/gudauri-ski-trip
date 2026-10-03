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
 *   A build without them (a local one) sends nothing at all. The emulator run has them, to check that events and
 *   crash reports arrive; its events say build=debug and the dashboards leave them out.
 * - Anonymous: no identify(), no person profiles, no screen recording, no default personal data in crash reports.
 *   The id is PostHog's random install id; it changes when the app is reinstalled. Dropping the IP address is a
 *   project setting on both services (docs/APP-NATIVE.md, "Usage and crashes").
 * - The switch (settings, 13.7) is kept on the phone; off means not started at all, and turning it off later stops
 *   both at once.
 * - Events and their properties are the contract in docs/GROWTH.md ("מדידת שימוש"), the same on the site and on the
 *   iPhone: only the events in that table, no autocapture. The properties every event carries are registered once
 *   ([start]): platform, app_version, build, lang, lang_source, theme, device_class.
 * - personProfiles NEVER: no person profile for anyone (the contract's identified_only without identify() gives the
 *   same; NEVER also keeps it so if identify() were ever called by mistake).
 */
object Telemetry {
    private const val PREFS = "telemetry"
    @Volatile private var usage = false
    @Volatile private var build: String? = null
    @Volatile private var crashes = false

    val hasKeys: Boolean get() = BuildConfig.POSTHOG_KEY.isNotBlank() || BuildConfig.SENTRY_DSN.isNotBlank()

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)

    /** The properties every event carries (docs/GROWTH.md): what build, which language, what kind of screen. */
    class Common(val appVersion: String, val build: String, val lang: String, val langSource: String, val theme: String, val deviceClass: String)

    private var common: Common? = null

    fun start(context: Context, channel: String, common: Common) {
        this.common = common
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
                captureApplicationLifecycleEvents = false // our own app_open, from the contract
                captureScreenViews = false // our own screen_view, with the contract's names
                captureDeepLinks = false
                sessionReplay = false
                personProfiles = PersonProfiles.NEVER
            }
            PostHogAndroid.setup(app, config)
            PostHog.optIn()
            PostHog.register("platform", "android")
            PostHog.register("app_version", common.appVersion)
            // "build" is a key PostHog keeps for itself, and register() drops it without a word: it goes with each event
            build = common.build
            PostHog.register("lang", common.lang)
            PostHog.register("lang_source", common.langSource)
            PostHog.register("theme", common.theme)
            PostHog.register("device_class", common.deviceClass)
            usage = true
        }
    }

    /** The user's switch. Off stops both now and keeps them off on the next launches. */
    fun setEnabled(context: Context, on: Boolean, channel: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        if (on) {
            if (usage) PostHog.optIn()
            common?.let { start(context, channel, it) }
            event("settings_change", mapOf("setting" to "analytics", "on" to true))
        } else {
            // off: nothing more is sent, not even this change (docs/GROWTH.md)
            if (usage) { PostHog.optOut(); PostHog.close(); usage = false }
            if (crashes) { Sentry.close(); crashes = false }
        }
    }

    /** The day and night mode changed (theme_set): the events after it carry the new one (PARITY X-2). */
    fun setTheme(mode: String) {
        common = common?.let { Common(it.appVersion, it.build, it.lang, it.langSource, mode, it.deviceClass) }
        if (usage) PostHog.register("theme", mode)
    }

    fun event(name: String, props: Map<String, Any> = emptyMap()) {
        if (usage) PostHog.capture(event = name, properties = build?.let { props + ("build" to it) } ?: props)
    }

    /** The emulator run's check that crash reports reach Sentry (debug builds only, MainActivity's qa.sentry). */
    /** An error the app got over (it did not crash), so it still shows in Sentry. */
    fun handled(e: Throwable) { if (crashes) Sentry.captureException(e) }

    fun testCrashReport(note: String) { if (crashes) Sentry.captureMessage("qa check: $note") }

    /** Send what is queued now (the emulator run checks arrival right after). */
    fun flush() { if (usage) PostHog.flush(); if (crashes) Sentry.flush(5000) }
}

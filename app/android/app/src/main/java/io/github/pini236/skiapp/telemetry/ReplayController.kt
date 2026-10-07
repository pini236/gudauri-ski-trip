package io.github.pini236.skiapp.telemetry

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.pini236.skiapp.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions
import java.io.File
import kotlin.random.Random

/** Opt-in replay, with a fail-closed route allowlist. Map/location, home/trip, accounts and groups are excluded. */
object ReplayController {
    private const val PREFS = "session-replay"
    internal val privacy = ReplayPrivacy()
    private val main = Handler(Looper.getMainLooper())
    private var consent = false
    private var analytics = false
    private var device = false
    private var foreground = false
    private var publicScreen = false
    private var view: View? = null
    private var options: SentryOptions? = null
    private var cache: File? = null
    private var scheduled: Long? = null
    private var qa = false

    fun consent(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("consent", false)

    /** An explicit QA build may validate replay on an emulator; it still needs the separate opt-in toggle. */
    fun enableQa(enabled: Boolean) { qa = BuildConfig.DEBUG && enabled }

    internal fun prepare(context: Context, master: Boolean, robot: Boolean) {
        consent = consent(context)
        analytics = master
        device = !robot && ((!BuildConfig.DEBUG && !emulator()) || qa)
        update()
    }

    internal fun configure(o: SentryOptions) {
        options = o
        if (qa) o.environment = "replay-qa"
        cache = o.cacheDirPath?.let(::File)
        // Never finalize/upload a video left by another process, even when today's consent is on.
        pruneReplayCaches()
        o.sessionReplay.sessionSampleRate = 0.0
        o.sessionReplay.onErrorSampleRate = 0.0
        o.sessionReplay.setMaskAllText(true)
        o.sessionReplay.setMaskAllImages(true)
        o.sessionReplay.isCaptureSurfaceViews = false
        o.sessionReplay.setNetworkCaptureBodies(false)
        o.sessionReplay.beforeErrorSampling = SentryReplayOptions.BeforeErrorSamplingCallback { _, _ -> privacy.ticket() != null }
        o.beforeSendReplay = SentryOptions.BeforeSendReplayCallback { event, _ ->
            if (privacy.permit(event.replayId.toString(), event.eventId.toString())) event else {
                // This event is denied, including its video. Leave ordinary crash/cache files alone.
                event.videoFile?.delete()
                null
            }
        }
        o.setTransportFactory(ReplayTransportFactory(privacy))
    }

    fun setConsent(context: Context, on: Boolean) {
        // Commit only this explicit consent flag; it must survive a crash/restart before recording starts.
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("consent", on).commit()
        consent = on && saved
        update()
        if (!consent) pruneReplayCaches()
    }

    internal fun setAnalytics(on: Boolean) { analytics = on; update() }
    internal fun sdkClosed() { options = null; scheduled = null; pruneReplayCaches() }

    /** Called synchronously BEFORE changing the navigation stack or displaying an external sheet. */
    fun beforeNavigation() { publicScreen = false; update() }

    /** Called after Compose commits the new content. Starting waits for two frames to remove the old screen. */
    fun contentShown(path: String, host: View) {
        view = host
        publicScreen = ReplayPrivacy.publicScreen(path)
        // Public fixed names only; never route IDs, invite codes, flight details or user text.
        if (publicScreen) Sentry.configureScope { it.screen = if (path == "games") "games" else "game:" + path.removePrefix("games/") }
        update()
    }

    fun foreground(on: Boolean) { foreground = on; if (!on) publicScreen = false; update() }

    private fun update() {
        val changed = privacy.update(ReplayPrivacy.Eligibility(consent, analytics, device, foreground, publicScreen))
        if (changed) {
            scheduled = null
            options?.sessionReplay?.onErrorSampleRate = 0.0
            // SDK lifecycle APIs are queued. Upload permission above is revoked immediately, ahead of rendering.
            Sentry.replay().stop()
            main.post { if (privacy.ticket() == null) pruneReplayCaches() }
        }
        scheduleStart()
    }

    private fun scheduleStart() {
        val ticket = privacy.ticket() ?: return
        if (options == null || scheduled == ticket) return
        val host = view ?: return
        scheduled = ticket
        host.postOnAnimation { host.postOnAnimation frame@ {
            if (privacy.ticket() != ticket || options == null) return@frame
            // Manual starts bypass SDK sampling. Decide once here: 5% full public-screen recordings,
            // otherwise buffer until a sampled error. Limits still apply in the Sentry project.
            options?.sessionReplay?.onErrorSampleRate = 1.0
            if (Random.nextDouble() < 0.05 || qa) Sentry.replay().start() else Sentry.replay().startBuffering()
            // The SDK posts start() to this same main looper; binding runs after that command.
            main.post {
                if (!privacy.bind(ticket, options?.replayController?.replayId.toString())) {
                    Sentry.replay().stop()
                }
            }
        } }
    }

    /** Only the SDK's replay_<32hex> directories under its app-private cache path are touched. */
    private fun pruneReplayCaches() {
        val parent = cache?.canonicalFile ?: return
        parent.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("replay_[a-fA-F0-9]{32}")) && it.canonicalFile.parentFile == parent }
            ?.forEach { it.deleteRecursively() }
    }

    private fun emulator(): Boolean = Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
        Build.MODEL.contains("Emulator") || Build.MODEL.contains("sdk_gphone") || Build.MODEL.contains("google_sdk") ||
        Build.HARDWARE in setOf("goldfish", "ranchu") || Build.PRODUCT.contains("sdk")
}

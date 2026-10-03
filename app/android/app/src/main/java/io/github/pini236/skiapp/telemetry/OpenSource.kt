package io.github.pini236.skiapp.telemetry

import android.content.Context
import android.content.Intent
import android.os.Build
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

/**
 * app_open's `source` (docs/GROWTH.md): a tap on one of the app's notifications (a meetup's reminder), a link the
 * app shared (share_link: its utm_medium is "share", nav/Nav.kt's Route.SHARED), any other link, the first open of an
 * install from Google Play (store, with the Play link's campaign), or direct.
 */
object OpenSource {
    private const val PLAY = "com.android.vending"

    fun of(intent: Intent?, notificationExtra: String): String = when {
        intent == null -> "direct"
        intent.hasExtra(notificationExtra) -> "notification"
        intent.data != null -> ofQuery(intent.data?.encodedQuery)
        else -> "direct"
    }

    /** A link's query: shared from the app or the site (utm_medium=share), or any other link. */
    fun ofQuery(query: String?): String = if (utm(query)["utm_medium"] == "share") "share_link" else "link"

    /** The utm_* of a link's query or of the Play Store's referrer ("utm_source=x&utm_medium=y"), as event properties. */
    fun utm(query: String?): Map<String, String> = query.orEmpty().split('&').mapNotNull { kv ->
        val i = kv.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val k = kv.substring(0, i)
        val v = runCatching { java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8") }.getOrNull()?.take(100) ?: return@mapNotNull null
        if (k in UTM && v.isNotBlank()) k to v else null
    }.toMap()

    private val UTM = setOf("utm_source", "utm_medium", "utm_campaign", "utm_content")

    /**
     * Once per install, on its first open: when Google Play installed the app (not an update, not the test build from
     * GitHub), the Play link's campaign (Install Referrer) for app_open's source "store"; null otherwise. [done] runs
     * once, also when the store does not answer.
     */
    fun firstFromStore(context: Context, done: (Map<String, Any>?) -> Unit) {
        val prefs = context.getSharedPreferences("app_open", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first", false)) return done(null)
        prefs.edit().putBoolean("first", true).apply()
        val fromPlay = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val installer = if (Build.VERSION.SDK_INT >= 30) context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                else context.packageManager.getInstallerPackageName(context.packageName)
            installer == PLAY && info.firstInstallTime == info.lastUpdateTime
        }.getOrDefault(false)
        if (!fromPlay) return done(null)
        val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
        var answered = false
        fun answer(props: Map<String, Any>?) {
            if (answered) return
            answered = true
            runCatching { client.endConnection() }
            done(props)
        }
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    val ref = if (code == InstallReferrerClient.InstallReferrerResponse.OK) runCatching { client.installReferrer.installReferrer }.getOrNull() else null
                    answer(utm(ref)) // installed from Play: "store" even when the referrer could not be read
                }
                override fun onInstallReferrerServiceDisconnected() = answer(emptyMap())
            })
        }.onFailure { answer(emptyMap()) }
    }
}

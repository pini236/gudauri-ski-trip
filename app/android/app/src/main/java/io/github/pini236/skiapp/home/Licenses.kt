package io.github.pini236.skiapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One font or library and what it is under (A-24, round 18): [file] is its full text in assets/licenses (the folder
 * licenses/, NOTICE.txt there says the same); the Google libraries are under the Android SDK's terms, a page on the web.
 */
internal class OpenLicense(val key: String, val name: String, val license: String, val file: String?, val url: String? = null)

private const val OFL = "SIL Open Font License 1.1"
private const val APACHE = "Apache License 2.0"

/** The rows of the licenses page, in the board's order: the fonts, then the libraries. */
internal val OPEN_LICENSES = listOf(
    OpenLicense("karantina", "Karantina", OFL, "OFL-Karantina.txt"),
    OpenLicense("plex-hebrew", "IBM Plex Sans Hebrew", OFL, "OFL-IBMPlexSansHebrew.txt"),
    OpenLicense("plex", "IBM Plex Sans", OFL, "OFL-IBMPlexSans.txt"),
    OpenLicense("oswald", "Oswald", OFL, "OFL-Oswald.txt"),
    OpenLicense("noto-georgian", "Noto Sans Georgian", OFL, "OFL-NotoSansGeorgian.txt"),
    OpenLicense("androidx", "Jetpack Compose, AndroidX", APACHE, "Apache-2.0.txt"),
    OpenLicense("kotlin", "Kotlin, kotlinx", APACHE, "Apache-2.0.txt"),
    OpenLicense("okhttp", "OkHttp", APACHE, "Apache-2.0.txt"),
    OpenLicense("posthog", "PostHog Android", "MIT", "MIT-PostHog.txt"),
    OpenLicense("sentry", "Sentry Android", "MIT", "MIT-Sentry.txt"),
    OpenLicense("google", "Sign in with Google, Install Referrer", "", null, "https://developer.android.com/studio/terms"),
)

/** The licenses page: a row for each, and a tap opens its text (or, for Google's, its terms on the web). */
@Composable
fun LicensesScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val c = Ski.colors
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.app_licenses_title), stringResource(R.string.common_about_settings), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            for (l in OPEN_LICENSES) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { if (l.file != null) onOpen(l.key) else l.url?.let(uri::openUri) }
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = Ski.type.bodyBold.copy(textDirection = TextDirection.Content), color = c.ink)
                        Text(l.license.ifEmpty { stringResource(R.string.app_licenses_android_sdk) }, style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
                    }
                    Icon(Icons.forward, null, Modifier.size(18.dp), tint = c.glacier)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
            }
        }
    }
}

/** One license's full text, as it was written: in English, left to right, read from the app's assets. */
@Composable
fun LicenseScreen(key: String, onBack: () -> Unit) {
    val c = Ski.colors
    val context = LocalContext.current
    val l = OPEN_LICENSES.firstOrNull { it.key == key }
    val text by produceState("", l) {
        value = l?.file?.let { f -> withContext(Dispatchers.IO) { runCatching { context.assets.open("licenses/$f").bufferedReader().use { it.readText() } }.getOrNull() } }.orEmpty()
    }
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(l?.name ?: "", stringResource(R.string.app_licenses_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (l != null) Text(l.license, style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
            Text(text, Modifier.fillMaxWidth(), style = Ski.type.small.copy(fontSize = 13.sp, lineHeight = 19.sp, textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
                color = c.ink)
        }
    }
}

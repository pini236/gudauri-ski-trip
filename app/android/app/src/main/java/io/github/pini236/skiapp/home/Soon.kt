package io.github.pini236.skiapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.pini236.skiapp.BuildConfig
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.group.Toggle
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Note
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar

/**
 * A sign's place that the app does not have yet (the meeting point is stage 13.4, the group 13.5): its title, what it
 * will be, and the way home. Honest and short, no fake screen.
 */
@Composable
fun SoonScreen(title: String, text: String, onBack: () -> Unit) {
    val c = Ski.colors
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(title, stringResource(R.string.nav_home), onBack)
        Text(text, Modifier.padding(horizontal = 16.dp), style = Ski.type.body, color = c.muted)
    }
}

/**
 * About, for now (the full page with settings is stage 13.7): the version, the credits the data, the recordings and
 * the libraries require (OpenStreetMap's ODbL and the tear's CC BY ask for a visible credit; the license texts are
 * packed in assets/licenses), and the privacy policy.
 */
@Composable
fun AboutScreen(version: String, onPrivacy: () -> Unit, onAccount: (() -> Unit)?, onBack: () -> Unit) {
    val c = Ski.colors
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.common_about_settings), stringResource(R.string.nav_home), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.app_about_version, version), style = Ski.type.bodyBold, color = c.ink)
            if (onAccount != null) Text(stringResource(R.string.app_a_account), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onAccount).padding(vertical = 10.dp),
                style = Ski.type.bodyBold.copy(textDecoration = TextDecoration.Underline), color = c.glacier)
            // the switch of the site's settings (round 11, AN1/AN2): off stops usage statistics and crash reports at once
            val context = LocalContext.current
            var analytics by remember { mutableStateOf(Telemetry.enabled(context)) }
            Toggle(stringResource(R.string.about_analytics), stringResource(if (analytics) R.string.about_analytics_on else R.string.about_analytics_off), analytics, {
                analytics = it
                Telemetry.setEnabled(context, it, BuildConfig.FLAVOR)
            })
            Text(stringResource(R.string.about_credits), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
            // the map's data, as the site credits it: OpenStreetMap's licence (ODbL) asks for the credit and its link
            val uri = LocalUriHandler.current
            Text(stringResource(R.string.about_credit_osm_label) + " © " + stringResource(R.string.about_credit_licensed, stringResource(R.string.about_credit_osm_link), "ODbL"),
                Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { uri.openUri("https://www.openstreetmap.org/copyright") }.padding(vertical = 10.dp),
                style = Ski.type.small.copy(textDecoration = TextDecoration.Underline), color = c.muted)
            Text(stringResource(R.string.about_credit_terrain_label) + " " + stringResource(R.string.about_credit_terrain), style = Ski.type.small, color = c.muted)
            Text(stringResource(R.string.app_ticket_credits), style = Ski.type.small, color = c.muted)
            Text(stringResource(R.string.about_credit_fonts_label) + " " + stringResource(R.string.app_about_fonts), style = Ski.type.small, color = c.muted)
            Text(stringResource(R.string.app_about_libs), style = Ski.type.small, color = c.muted)
            Text(stringResource(R.string.about_credit_mta), style = Ski.type.small, color = c.muted)
            Text(stringResource(R.string.app_about_privacy), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onPrivacy).padding(vertical = 10.dp),
                style = Ski.type.bodyBold.copy(textDecoration = TextDecoration.Underline), color = c.glacier)
            Note(stringResource(R.string.app_about_more), Icons.phone)
        }
    }
}

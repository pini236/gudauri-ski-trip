package io.github.pini236.skiapp.home

import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.fx.FxPrefs
import io.github.pini236.skiapp.i18n.Lang
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
import io.github.pini236.skiapp.telemetry.ReplayController
import io.github.pini236.skiapp.ui.Icons
import androidx.compose.material3.Icon
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
 * About and settings (13.7), as the site's #about (decision 21, round 11): the account's ski pass, the settings
 * (sound, vibration, the display, the language, measuring, resetting the high scores), who built it, and the credits the
 * data, the recordings and the libraries require (OpenStreetMap's ODbL and the tear's CC BY ask for a visible credit; the
 * license texts are packed in assets/licenses), the privacy policy and "unofficial".
 */
@Composable
fun AboutScreen(
    version: String, onPrivacy: () -> Unit, account: Account?, mode: DayNight.Mode, onMode: () -> Unit,
    lang: Lang.Language, onLang: (String?) -> Unit, onResetBests: () -> Unit, onLicenses: () -> Unit, onBack: () -> Unit,
) {
    val c = Ski.colors
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var langs by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.snow)) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(stringResource(R.string.common_about_settings), stringResource(R.string.nav_home), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // the account's ski pass (the site's #abMe, P4 and P5 of round 12)
            if (account != null) {
                val me = account.me
                H2(stringResource(if (me != null) R.string.acct_title else R.string.acct_signin_title))
                SkiPass(stringResource(if (me != null) R.string.acct_pass_top_me else R.string.acct_pass_top_guest),
                    stringResource(when { me == null -> R.string.acct_no_account; me.registered -> R.string.acct_google_name; else -> R.string.acct_signed_guest })) {
                    io.github.pini236.skiapp.group.Avatar(me?.name ?: "?", me != null, 52)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(me?.name ?: stringResource(R.string.ticket_pax_guest), style = Ski.type.title.copy(fontSize = 34.sp, lineHeight = 32.sp), color = c.ink)
                        Text(stringResource(when { me == null -> R.string.app_g_rest_works; me.registered -> R.string.acct_sync_note; else -> R.string.app_a_guest_note }),
                            style = Ski.type.small.copy(fontSize = 13.5.sp), color = c.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (me != null) Link(stringResource(R.string.acct_manage), account.onManage)
                            if (me == null) Link(stringResource(R.string.acct_signin_title), account.onSignIn)
                            else if (me.registered) Link(stringResource(R.string.acct_sign_out_short), account.onSignOut, c.ink)
                        }
                    }
                }
            }
            // the settings (the site's .ab-set)
            H2(stringResource(R.string.about_settings))
            var sound by remember { mutableStateOf(FxPrefs.sound(context)) }
            var haptics by remember { mutableStateOf(FxPrefs.haptics(context)) }
            val canVibrate = remember { runCatching { (context.getSystemService(android.os.Vibrator::class.java))?.hasVibrator() == true }.getOrDefault(true) }
            Column {
                // round 20: the settings without a line under them; a phone that cannot vibrate has no vibration row
                Toggle(stringResource(R.string.about_sound), null, sound, {
                    sound = it; FxPrefs.set(context, "sound", it); Telemetry.event("settings_change", mapOf("setting" to "sound", "on" to it))
                })
                if (canVibrate) Toggle(stringResource(R.string.about_haptics), null, haptics, {
                    haptics = it; FxPrefs.set(context, "haptics", it); Telemetry.event("settings_change", mapOf("setting" to "haptics", "on" to it))
                })
                val modes = mapOf(DayNight.Mode.AUTO to R.string.daynight_mode_auto, DayNight.Mode.DAY to R.string.daynight_mode_day, DayNight.Mode.NIGHT to R.string.daynight_mode_night)
                Row2(stringResource(R.string.about_display), null, stringResource(modes.getValue(mode)), onClick = onMode)
                Row2(stringResource(R.string.about_language) + if (lang.tag != "en") " · Language" else "", null, lang.name) { langs = true }
                // the switch of the site's settings (round 11, AN1/AN2): off stops usage statistics and crash reports at once
                var analytics by remember { mutableStateOf(Telemetry.enabled(context)) }
                Toggle(stringResource(R.string.about_analytics), stringResource(if (analytics) R.string.about_analytics_on else R.string.about_analytics_off), analytics, {
                    analytics = it
                    Telemetry.setEnabled(context, it, BuildConfig.FLAVOR)
                })
                // Consent to screen replay is separate from the existing anonymous event/crash switch.
                var replay by remember { mutableStateOf(ReplayController.consent(context)) }
                Toggle(stringResource(R.string.app_replay_title), stringResource(if (replay && analytics) R.string.app_replay_on else R.string.app_replay_off), replay && analytics, {
                    ReplayController.setConsent(context, it)
                    replay = ReplayController.consent(context)
                }, enabled = analytics)
                Text(stringResource(R.string.app_replay_details), style = Ski.type.small, color = c.muted,
                    modifier = Modifier.padding(vertical = 8.dp))
                // reset the high scores: twice, as on the site
                var armed by remember { mutableStateOf(false) }
                var done by remember { mutableStateOf(false) }
                LaunchedEffect(armed) { if (armed) { kotlinx.coroutines.delay(4000); armed = false } }
                Row2(stringResource(R.string.about_reset), when { done -> stringResource(R.string.about_reset_done); armed -> stringResource(R.string.about_reset_confirm); else -> null },
                    null, danger = armed) {
                    if (armed) { armed = false; done = true; onResetBests(); Telemetry.event("best_reset") } else { armed = true; done = false }
                }
            }
            // who built it (the site's .ab-who)
            H2(stringResource(R.string.about_who))
            SkiPass(stringResource(R.string.about_pass_top), stringResource(R.string.about_builder)) {
                BuilderPhoto()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.about_builder_name), style = Ski.type.title.copy(fontSize = 34.sp, lineHeight = 32.sp), color = c.ink)
                    Text(stringResource(R.string.about_bio), style = Ski.type.small.copy(fontSize = 13.5.sp), color = c.muted)
                    Link("github.com/pini236", { uri.openUri("https://github.com/pini236") })
                }
            }
            // the credits (the site's .ab-credits)
            H2(stringResource(R.string.about_credits))
            Credit(stringResource(R.string.about_credit_osm_label), "© " + stringResource(R.string.about_credit_licensed, stringResource(R.string.about_credit_osm_link), "ODbL"),
                "https://www.openstreetmap.org/copyright")
            Credit(stringResource(R.string.about_credit_terrain_label), stringResource(R.string.about_credit_terrain))
            // the weather's source, as its licence asks (round 19, m-6 section 6)
            Credit(stringResource(R.string.about_credit_weather_label), stringResource(R.string.about_credit_licensed, "Open-Meteo.com", "CC BY 4.0"), "https://open-meteo.com/")
            Credit(stringResource(R.string.about_credit_tear_label), stringResource(R.string.about_credit_licensed, stringResource(R.string.about_credit_tear_link), "CC BY 4.0"),
                "https://freesound.org/people/everythingsounds/sounds/198233/")
            Credit(stringResource(R.string.about_credit_cards_label), stringResource(R.string.about_credit_licensed, "Kenney", "CC0"), "https://kenney.nl/assets/casino-audio")
            Credit(stringResource(R.string.about_credit_fonts_label), stringResource(R.string.app_about_fonts))
            Credit(null, stringResource(R.string.app_about_libs))
            Credit(stringResource(R.string.about_credit_videos_label), stringResource(R.string.app_about_credit_videos))
            // the full texts the fonts' and the libraries' licenses ask to show (A-24, round 18)
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onLicenses), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_licenses_row), Modifier.weight(1f), style = Ski.type.title.copy(fontSize = 24.sp), color = c.ink)
                    Icon(Icons.forward, null, Modifier.size(18.dp), tint = c.glacier)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
            }
            Credit(null, stringResource(R.string.about_credit_mta))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Link(stringResource(R.string.app_about_privacy), onPrivacy)
                Text(" · " + stringResource(R.string.about_unofficial), Modifier.weight(1f), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
            }
            Text(stringResource(R.string.app_about_version, version), style = Ski.type.small, color = c.muted)
        }
    }
    if (langs) LangSheet({ tag -> langs = false; onLang(tag) }) { langs = false }
    }
}

@Composable
private fun H2(text: String) = Text(text, Modifier.padding(top = 14.dp).semantics { heading() },
    style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = Ski.colors.ink)

@Composable
private fun Link(text: String, onClick: () -> Unit, color: Color = Ski.colors.glacier) =
    Text(text, Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = color)

/** A settings row that is not a switch (the site's .ab-row button): its name and line, and the value at the end. */
@Composable
private fun Row2(title: String, sub: String?, value: String?, danger: Boolean = false, onClick: () -> Unit) {
    val c = Ski.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ski.type.bodyBold.copy(fontSize = 15.5.sp), color = if (danger) c.red else c.ink)
            if (sub != null) Text(sub, style = Ski.type.small, color = if (danger) c.red else c.muted)
        }
        if (value != null) Text(value, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.glacier)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
}

/** A ski pass card (the site's .ab-pass): a blue strip with two words, and its body, tilted a little. */
@Composable
private fun SkiPass(top: String, right: String, body: @Composable RowScope.() -> Unit) {
    val c = Ski.colors
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().rotate(-1.5f).shadow(10.dp, shape).background(c.paper, shape)) {
        Row(Modifier.fillMaxWidth().background(c.blue).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            // onBoard: white by day, dark on the bright night blue (PARITY A-38, as the site's round 16)
            Text(top, style = Ski.type.label.copy(fontSize = 12.sp, letterSpacing = .05.em), color = c.onBoard)
            Text(right, style = Ski.type.label.copy(fontSize = 12.sp, letterSpacing = .05.em), color = c.onBoard)
        }
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, content = body)
    }
}

/**
 * The builder's picture, his GitHub picture as on the site, packed in the app (res/drawable-nodpi/builder.png), so the
 * page shows it without a signal and asks nothing of github.com (the architect's review of R-3, 5.10.2026). Pixel art:
 * scaled without smoothing.
 */
@Composable
private fun BuilderPhoto() {
    val c = Ski.colors
    Box(Modifier.size(88.dp).background(c.grid, RoundedCornerShape(8.dp)).border(3.dp, c.paper, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Image(BitmapPainter(ImageBitmap.imageResource(R.drawable.builder), filterQuality = FilterQuality.None), null,
            Modifier.size(82.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
    }
}

/** A credit: what, in bold, and whose; with [url], the words are the link (OpenStreetMap's licence asks for it). */
@Composable
private fun Credit(label: String?, text: String, url: String? = null) {
    val c = Ski.colors
    val uri = LocalUriHandler.current
    Text(buildAnnotatedString {
        if (label != null) { pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)); append(label + " "); pop() }
        append(text)
    }, if (url != null) Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { uri.openUri(url) }.padding(vertical = 8.dp) else Modifier,
        style = Ski.type.small.copy(fontSize = 13.sp, textDecoration = if (url != null) TextDecoration.Underline else null), color = c.muted)
}

package io.github.pini236.skiapp.skipass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.season.shortDate
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The ski-pass screen (round 40, alternative ב, approved 18.11.2026): what the ski days cost on a pass against day tickets,
 * by the trip's ski days or by a number the user sets, for an adult or a child. The prices are the resort's own (MTA, foreign
 * visitors, GEL), each with where they come from and the day they were checked; past `until` it says there is no
 * information, and it never shows an estimate. Nothing is bought here: the tickets are at the resort's ticket offices, and the
 * resort's own pass app is a plain link to its store page.
 */
@Composable
fun SkipassScreen(prices: Prices?, tripDays: Int?, today: LocalDate, onBack: () -> Unit) {
    val c = Ski.colors
    val ctx = LocalContext.current
    var child by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableStateOf((tripDays ?: DEFAULT_DAYS).coerceIn(1, MAX)) }
    var fromTrip by rememberSaveable { mutableStateOf(tripDays != null && tripDays in 1..MAX) }
    val gel = { n: Int -> ctx.getString(R.string.app_skipass_price, n.toString()) }
    val gelOne = { x: Double -> ctx.getString(R.string.app_skipass_price, if (x == Math.floor(x)) x.toInt().toString() else "%.1f".format(java.util.Locale.US, x)) }
    val valid = prices != null && prices.valid(today)
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.app_skipass_title), stringResource(R.string.nav_home), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // adult or child
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).border(1.dp, c.rule, RectangleShape).background(c.snow)) {
                for (kid in listOf(false, true)) {
                    val on = child == kid
                    val text = if (!kid) stringResource(R.string.app_skipass_adult) else stringResource(R.string.app_skipass_child, (prices?.childFrom ?: 6).toString(), (prices?.childTo ?: 12).toString())
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).background(if (on) c.ink else c.snow).clickable(role = Role.RadioButton) { child = kid }
                        .semantics { contentDescription = text }, contentAlignment = Alignment.Center) {
                        Text(text, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = if (on) c.paper else c.muted)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(c.paper).border(1.dp, c.rule, RectangleShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (fromTrip) stringResource(R.string.app_skipass_from_trip) else stringResource(R.string.app_skipass_days), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
                // the days, with a big plus and minus
                val less = stringResource(R.string.app_skipass_days_less); val more = stringResource(R.string.app_skipass_days_more)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Step("−", less, days > 1) { days -= 1; fromTrip = false }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(days.toString(), style = Ski.type.title.copy(fontSize = 40.sp, lineHeight = 40.sp, textDirection = TextDirection.Ltr), color = c.ink)
                        Text(stringResource(R.string.app_skipass_days), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
                    }
                    Step("+", more, days < MAX) { days += 1; fromTrip = false }
                }
                if (!valid || prices == null) {
                    Text("—", style = Ski.type.title.copy(fontSize = 68.sp, lineHeight = 62.sp), color = c.muted)
                    Warn(stringResource(R.string.app_skipass_no_info) + "\n" + stringResource(R.string.app_skipass_check_site))
                } else {
                    val q = prices.price(days, child)
                    if (q == null) Warn(stringResource(R.string.app_skipass_too_long, days.toString()))
                    else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(gel(q.total), style = Ski.type.title.copy(fontSize = 68.sp, lineHeight = 62.sp, textDirection = TextDirection.Ltr), color = c.ink)
                            Text(stringResource(R.string.app_skipass_per_day, gelOne(q.total.toDouble() / days)), Modifier.padding(bottom = 6.dp), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
                        }
                        val daily = prices.daily(child)
                        if (daily != null) {
                            val all = daily * days
                            Bar(stringResource(R.string.app_skipass_daily_x, days.toString()), gel(all), 1f, c.dash)
                            Bar(if (q.parts.size == 1) pluralStringResource(R.plurals.app_skipass_pass_days, days, days) else q.parts.joinToString(" + "), gel(q.total), q.total.toFloat() / all, c.green)
                            if (all > q.total) Text(stringResource(R.string.app_skipass_save, gel(all - q.total), (((all - q.total).toDouble() / all) * 100).roundToInt().toString()),
                                style = Ski.type.bodyBold.copy(fontSize = 16.sp), color = c.green)
                        }
                        if (q.parts.size == 2) {
                            val part = { n: Int -> ctx.resources.getQuantityString(R.plurals.app_skipass_part, n, n) }
                            Text(stringResource(R.string.app_skipass_no_pass, days.toString(), stringResource(R.string.app_skipass_and, part(q.parts[0]), part(q.parts[1]))),
                                style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
                            Text(stringResource(R.string.app_skipass_two_tickets), style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.muted)
                        }
                    }
                }
            }
            if (prices != null) {
                // what the price list says besides the prices (the file's own list of rules; one this app has no words for is skipped)
                val rules = prices.rules.mapNotNull { key -> rule(ctx, key, prices, gel) }
                val lines = listOfNotNull(
                    prices.nightAdult?.let { ctx.getString(R.string.app_skipass_night, gel(it)) },
                    prices.freeUnder?.let { ctx.getString(R.string.app_skipass_child_free, it.toString()) },
                ) + rules
                if (lines.isNotEmpty()) Text(lines.joinToString("\n"), style = Ski.type.small.copy(fontSize = 12.5.sp, lineHeight = 19.sp), color = c.muted)
                Text(stringResource(R.string.app_skipass_buy), style = Ski.type.bodyBold.copy(fontSize = 15.sp), color = c.ink)
                prices.buyAndroid?.let { link -> StoreLink(stringResource(R.string.app_skipass_app, "Google Play"), link) }
                Text(stringResource(R.string.app_skipass_checked, shortDate(prices.checked), prices.src), style = Ski.type.small.copy(fontSize = 11.5.sp), color = c.muted)
            }
        }
    }
}

private const val MAX = 16
private const val DEFAULT_DAYS = 4

/** The words for one of the price list's rules, by its key; null when this app has none (an unknown key is skipped, never shown as a key). */
private fun rule(ctx: android.content.Context, key: String, p: Prices, gel: (Int) -> String): String? {
    val id = ctx.resources.getIdentifier(key.replace('.', '_'), "string", ctx.packageName)
    if (id == 0) return null
    return runCatching {
        if (key == "app.skipass_card") ctx.getString(id, p.deposit?.let(gel) ?: return null) else ctx.getString(id)
    }.getOrNull()
}

@Composable
private fun Step(sign: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Ski.colors
    Box(Modifier.size(48.dp).border(1.5.dp, if (enabled) c.ink else c.rule, RectangleShape)
        .let { if (enabled) it.clickable(role = Role.Button, onClick = onClick) else it }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(sign, style = Ski.type.bodyBold.copy(fontSize = 24.sp), color = if (enabled) c.ink else c.rule)
    }
}

/** A bar for what something costs, as long as it is against the day tickets' (the longest, [share] = 1). */
@Composable
private fun Bar(label: String, value: String, share: Float, color: androidx.compose.ui.graphics.Color) {
    val c = Ski.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(96.dp), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
        Box(Modifier.weight(1f)) { Box(Modifier.fillMaxWidth(share.coerceIn(0.05f, 1f)).heightIn(min = 24.dp).background(color)) }
        Text(value, style = Ski.type.bodyBold.copy(fontSize = 13.sp, textDirection = TextDirection.Ltr, textAlign = TextAlign.End), color = c.ink)
    }
}

@Composable
private fun Warn(text: String) {
    val c = Ski.colors
    val amber = if (c.dark) androidx.compose.ui.graphics.Color(0xFFFFD27A) else androidx.compose.ui.graphics.Color(0xFF8A5A00)
    val bg = if (c.dark) androidx.compose.ui.graphics.Color(0xFF3A2E10) else androidx.compose.ui.graphics.Color(0xFFFFF3D6)
    Text(text, Modifier.fillMaxWidth().background(bg).border(1.dp, amber, RectangleShape).padding(horizontal = 10.dp, vertical = 8.dp),
        style = Ski.type.small.copy(fontSize = 13.sp), color = amber)
}

/** A link to the resort's own app in its store, opened as it is: no referrer, no campaign (this app is not what the resort's pass app counts). */
@Composable
private fun StoreLink(text: String, url: String) {
    val c = Ski.colors
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.5.dp, c.ink, RectangleShape).clickable(role = Role.Button) { uri.openUri(url) }, contentAlignment = Alignment.Center) {
        Text(text + " ↗", style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.ink)
    }
}

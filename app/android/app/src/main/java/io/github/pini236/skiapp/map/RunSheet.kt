package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.weather.RunConditions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.RunFacts
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.data.Video
import io.github.pini236.skiapp.nav.Route
import io.github.pini236.skiapp.home.SignShape
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The map's panel (13.3), as the site's map panel on the phone, after the approved run view (round 3, T1 to T4): a
 * chosen run's sign with its number, the steps to the run before and after (also a swipe), share, the numbers, the
 * elevation profile that moves a dot on the mountain, "what's ahead", the comparison, the fly down, the connections, the
 * notes, where the line comes from, and its videos. A lift's panel, and the list of all runs with the map's filters.
 * The panel sits over the bottom of the map and leaves the mountain free; a tap on its handle shows all of it.
 */

/** A run's name as the site shows it (dispName). */
@Composable
fun runName(p: Piste): String = if (!p.named) stringResource(R.string.map_unnamed_segment) else if (p.key == "Firni ?") "Firni (1/2?)" else p.key

@Composable
private fun colorName(c: String) = stringResource(when (c) { "green" -> R.string.common_color_green; "blue" -> R.string.common_color_blue; "red" -> R.string.common_color_red; else -> R.string.common_color_black })

@Composable
private fun rateName(c: String) = stringResource(when (c) { "green" -> R.string.map_difficulty_beginner; "blue" -> R.string.map_difficulty_easy; "red" -> R.string.map_difficulty_intermediate; else -> R.string.map_difficulty_hard })

private fun rate(c: String) = when (c) { "green" -> 1; "blue" -> 2; "red" -> 3; else -> 4 }

@Composable
private fun osmGrade(g: String): Pair<String, String>? = when (g) {
    "novice" -> stringResource(R.string.run_osm_grade_novice) to "green"
    "easy" -> stringResource(R.string.run_osm_grade_easy) to "blue"
    "intermediate" -> stringResource(R.string.run_osm_grade_intermediate) to "red"
    "advanced" -> stringResource(R.string.run_osm_grade_advanced) to "black"
    "expert" -> stringResource(R.string.run_osm_grade_expert) to "black"
    else -> null
}

@Composable
private fun nf(): NumberFormat {
    val res = LocalContext.current.resources
    return remember(res) { NumberFormat.getIntegerInstance(Lang.current(res).locale) }
}

/** A length as the site writes it: metres, or kilometres with two decimals from 1,000 m (fmtLen). */
@Composable
fun lengthText(m: Int): String =
    if (m >= 1000) stringResource(R.string.common_unit_km, String.format(java.util.Locale.ROOT, "%.2f", m / 1000.0)) else stringResource(R.string.common_unit_m, nf().format(m))

@Composable
private fun metres(n: Int) = stringResource(R.string.common_unit_m, nf().format(n))

/** Opens a link outside the app (YouTube, OpenStreetMap): the app has no web view. */
fun openLink(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The run's link on the site (#map/run/<key>), which opens the same run in the app or on the site. */
fun runLink(key: String) = "https://${Route.SITE_HOST}/?${Route.SHARED}#map/run/" + java.net.URLEncoder.encode(key, "UTF-8").replace("+", "%20")

/** The panel's frame: paper over the map's bottom, a handle that shows all of it or only its head. */
@Composable
fun BoxScope.MapPanel(
    expanded: Boolean, onExpand: (Boolean) -> Unit, onClose: () -> Unit,
    head: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit, bodyMax: Float = 0.36f,
    onHeight: (Int) -> Unit = {},
) {
    val c = Ski.colors
    // the head and the open body together stay under about two thirds of the screen: the run, the lift status bar
    // and the way home stay in sight above it
    val max = (LocalConfiguration.current.screenHeightDp * bodyMax).dp
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) }.shadow(12.dp, RectangleShape).background(c.paper)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val more = stringResource(if (expanded) R.string.app_panel_less else R.string.app_panel_more)
            Box(Modifier.weight(1f).heightIn(min = 30.dp).clickable(role = Role.Button) { onExpand(!expanded) }.semantics { contentDescription = more },
                contentAlignment = Alignment.Center) {
                Box(Modifier.width(44.dp).height(5.dp).background(c.rule))
            }
            val close = stringResource(R.string.common_close)
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = close }, contentAlignment = Alignment.Center) {
                Text("✕", style = Ski.type.body.copy(fontSize = 18.sp), color = c.ink)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) { head() }
        if (expanded) Column(Modifier.heightIn(max = max).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) { body() }
        else Box(Modifier.height(12.dp))
    }
}

@Composable
private fun H3(text: String) = Text(text, Modifier.padding(top = 18.dp, bottom = 6.dp).semantics { heading() },
    style = Ski.type.sign.copy(fontSize = Ski.type.sign.fontSize * 0.85f), color = Ski.colors.ink)

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) = Text(text, modifier.padding(top = 6.dp), style = Ski.type.small, color = Ski.colors.muted)

/** A row of the details (the site's dl.kv): the label, and its value. */
@Composable
private fun Kv(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(118.dp).padding(end = 8.dp), style = Ski.type.small, color = Ski.colors.muted)
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun KvText(label: String, value: String) = Kv(label) { Text(value, style = Ski.type.body, color = Ski.colors.ink) }

/** A run or lift to go to (the site's .tag): the run's colour at its start. */
@Composable
private fun Tag(text: String, color: Color?, onClick: () -> Unit) {
    val c = Ski.colors
    Row(Modifier.heightIn(min = 40.dp).border(1.5.dp, c.rule).background(c.snow).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (color != null) Box(Modifier.size(10.dp).background(color)) else Text("⇡", style = Ski.type.bodyBold, color = c.ink)
        Text(text, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(content: @Composable () -> Unit) = FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }

/** Four squares, filled to the run's difficulty (the site's pips). */
@Composable
private fun Pips(color: String) {
    val col = Ski.colors.run(color)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(end = 8.dp)) {
        for (i in 1..4) Box(Modifier.size(10.dp).border(1.5.dp, col).background(if (i <= rate(color)) col else Color.Transparent))
    }
}

/** What the panel needs from the map screen to move about: to another run, to a lift, to the list, to the fly down. */
class PanelActions(
    val goRun: (key: String, via: String) -> Unit,
    val goLift: (Lift) -> Unit,
    val fly: () -> Unit,
    val stopFly: () -> Unit,
    val mark: (x: Float, y: Float) -> Unit,
    val unmark: () -> Unit,
    /** While flying down: how far along the run (metres), for the profile's dot. */
    val flyAt: () -> Float? = { null },
)

/** The head of a run's panel: the sign (T4), its number, the steps to the run before and after, share, and the numbers. */
@Composable
fun ColumnScope.RunHead(p: Piste, facts: RunFacts?, order: List<String>, flying: Boolean, canFly: Boolean, a: PanelActions) {
    val c = Ski.colors
    val ctx = LocalContext.current
    val i = order.indexOf(p.key)
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    // a swipe across the head goes to the next or the previous run (as on the site: left is the next one)
    var dx by remember(p.key) { mutableStateOf(0f) }
    val swipe = with(LocalDensity.current) { 70.dp.toPx() }
    Column(Modifier.fillMaxWidth().pointerInput(p.key, i) {
        if (i < 0) return@pointerInput
        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
            if (abs(dx) >= swipe) a.goRun(order[(i + (if (dx < 0) 1 else -1) + order.size) % order.size], "swipe")
            dx = 0f
        }) { _, d -> dx += d }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shape = remember { SignShape(16.dp) }
            Box(Modifier.background(c.run(p.color), shape).padding(start = if (ltr) 14.dp else 26.dp, end = if (ltr) 26.dp else 14.dp, top = 6.dp, bottom = 4.dp)) {
                Text(runName(p), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = Color.White)
            }
            if (p.refs.isNotEmpty()) {
                val title = stringResource(R.string.run_ref_title)
                Box(Modifier.border(2.dp, c.ink).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp).semantics { contentDescription = title + " " + p.refs[0] }) {
                    // the text face: the display face draws 7 like a Hebrew letter
                    Text(p.refs[0], style = Ski.type.bodyBold.copy(fontSize = 20.sp), color = Color(0xFF13233A))
                }
            }
        }
        Text(colorName(p.color) + stringResource(if (p.named) R.string.run_color_official_suffix else R.string.run_color_osm_suffix) + " · " + rateName(p.color),
            Modifier.padding(top = 6.dp), style = Ski.type.small, color = c.muted)
        // length, drop, steepest stretch (T4's three numbers)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat(stringResource(R.string.common_length_label), lengthText(p.len))
            if (facts != null) {
                Stat(stringResource(R.string.run_stat_drop_label), metres(facts.drop))
                Stat(stringResource(R.string.run_stat_steep_label), nf().format((facts.maxG * 100).roundToInt()) + "%")
            }
        }
        // before, share, after (the site's run-nav); a run outside the list only shares
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (i >= 0) {
                val prev = order[(i - 1 + order.size) % order.size]; val next = order[(i + 1) % order.size]
                val back = if (ltr) "←" else "→"; val fwd = if (ltr) "→" else "←"
                val prevDesc = stringResource(R.string.run_nav_prev_aria, prev)
                NavButton("$back $prev", prevDesc, Modifier.weight(1f)) { a.goRun(prev, "swipe") }
                ShareButton(p, Modifier.weight(1f))
                val nextDesc = stringResource(R.string.run_nav_next_aria, next)
                NavButton("$next $fwd", nextDesc, Modifier.weight(1f)) { a.goRun(next, "swipe") }
            } else ShareButton(p, Modifier.weight(1f))
        }
        // with reduced motion there is no fly down, as on the site (A-12)
        if (canFly && facts != null && (flying || !io.github.pini236.skiapp.status.reducedMotion())) {
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 44.dp).background(if (flying) c.red else c.ink)
                .clickable(role = Role.Button) { if (flying) a.stopFly() else a.fly() }, contentAlignment = Alignment.Center) {
                Text(stringResource(if (flying) R.string.run_fly_stop else R.string.run_fly_button), style = Ski.type.bodyBold, color = if (flying) Color.White else c.snow)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = Ski.type.small.copy(fontSize = 12.sp), color = Ski.colors.muted)
        Text(value, style = Ski.type.number, color = Ski.colors.ink)
    }
}

@Composable
private fun NavButton(text: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ski.colors
    Box(modifier.heightIn(min = 44.dp).border(1.5.dp, c.rule).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center) {
        // run names are Latin: kept in their own direction inside the Hebrew line
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(text, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.ink, maxLines = 1)
        }
    }
}

@Composable
private fun ShareButton(p: Piste, modifier: Modifier) {
    val c = Ski.colors
    val ctx = LocalContext.current
    val title = stringResource(R.string.run_share_title, p.key)
    Box(modifier.heightIn(min = 44.dp).background(c.glacier).clickable(role = Role.Button) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, title + "\n" + runLink(p.key))
        runCatching { ctx.startActivity(Intent.createChooser(send, title)) }
        Telemetry.event("run_share", mapOf("run" to p.key, "method" to "native"))
        Qa.log("shared ${p.key}")
    }, contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.run_share_button), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.snow)
    }
}

/** The rest of a run's panel: the details, the profile and briefing (T2, T3), connections, notes, research, videos. */
@Composable
fun ColumnScope.RunBody(p: Piste, facts: RunFacts?, runs: Runs, terrain: Terrain, videos: List<Video>, a: PanelActions, flying: Boolean = false) {
    val c = Ski.colors
    val ctx = LocalContext.current
    val n = nf()
    // the details (the site's dl.kv)
    if (facts != null) {
        KvText(stringResource(R.string.run_stat_altitude_label), stringResource(R.string.run_stat_altitude_value, n.format(facts.top), n.format(facts.bot)))
        KvText(stringResource(R.string.run_stat_drop_label), metres(facts.drop) +
            if (p.lines.size == 1 && p.len > 0) " · " + stringResource(R.string.run_stat_avg_gradient, n.format((facts.drop * 100.0 / p.len).roundToInt())) else "")
        Kv(stringResource(R.string.run_stat_steep_label)) {
            Column {
                Text(n.format((facts.maxG * 100).roundToInt()) + "%", style = Ski.type.body, color = c.ink)
                Text(stringResource(R.string.run_stat_steep_hint), style = Ski.type.small, color = c.muted)
            }
        }
    }
    Kv(stringResource(R.string.run_difficulty_label)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Pips(p.color); Text(rateName(p.color), style = Ski.type.body, color = c.ink) }
    }
    if (p.osmDiff.isNotEmpty()) {
        val none = stringResource(R.string.run_osm_grade_none)
        KvText(stringResource(R.string.run_osm_grade_label), p.osmDiff.map { osmGrade(it)?.first ?: none }.joinToString(" / "))
    }
    if (p.refs.isNotEmpty()) KvText(stringResource(R.string.run_ref_label), p.refs.joinToString(", "))
    if (p.groom.isNotEmpty()) KvText(stringResource(R.string.run_grooming_label), if ("classic" in p.groom) stringResource(R.string.run_groomed_value) else p.groom.joinToString(", "))

    // the forecast at the run's top and bottom (round 19, and-run-cond), after the details and before the profile
    if (facts != null && p.named) RunConditions(facts.top, facts.bot, p.key)

    if (facts != null && facts.points.size >= 4) RunProfile(p, facts, runs, terrain, a, flying)

    // connections (the site's: lifts at the top and the bottom, runs it joins and comes from)
    H3(stringResource(R.string.run_connections_heading))
    Conn(stringResource(R.string.run_lift_at_top), p.fromLifts.mapNotNull { name -> runs.lifts.firstOrNull { it.name == name } }, emptyList(), runs, a)
    Conn(stringResource(R.string.run_lift_at_bottom), p.toLifts.mapNotNull { name -> runs.lifts.firstOrNull { it.name == name } }, emptyList(), runs, a)
    Conn(stringResource(R.string.run_joins_label), emptyList(), p.joins, runs, a)
    Conn(stringResource(R.string.run_from_runs_label), emptyList(), p.fromPistes, runs, a)
    Hint(stringResource(R.string.run_elevation_accuracy_hint))
    Hint(stringResource(R.string.run_connections_hint))

    // notes (the site's notesFor)
    H3(stringResource(R.string.run_notes_heading))
    val notes = notes(p)
    if (notes.isEmpty()) Bullet(stringResource(R.string.run_notes_none)) else notes.forEach { Bullet(it) }

    p.research?.let { Research(p, it) }
    val ids = (p.research?.osmIds?.takeIf { it.isNotEmpty() } ?: p.osmIds).distinct()
    if (ids.isNotEmpty()) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("OSM: ", style = Ski.type.small, color = c.muted)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Tags { ids.forEach { id ->
                Text(id.toString(), Modifier.heightIn(min = 32.dp).clickable { openLink(ctx, "https://www.openstreetmap.org/way/$id") }.padding(vertical = 6.dp, horizontal = 2.dp),
                    style = Ski.type.small.copy(textDecoration = TextDecoration.Underline), color = c.glacier)
            } }
        }
    }
    if (p.named) Videos(p, Video.of(videos, p.key))
}

@Composable
private fun Bullet(text: String) = Row(Modifier.padding(vertical = 3.dp)) {
    Text("•  ", style = Ski.type.body, color = Ski.colors.muted)
    Text(text, style = Ski.type.body.copy(fontSize = 14.sp), color = Ski.colors.ink)
}

@Composable
private fun Conn(label: String, lifts: List<Lift>, keys: List<String>, runs: Runs, a: PanelActions) {
    Kv(label) {
        if (lifts.isEmpty() && keys.isEmpty()) Text("—", style = Ski.type.body, color = Ski.colors.ink)
        else Tags {
            lifts.forEach { l -> Tag(l.name, null) { a.goLift(l) } }
            keys.forEach { k -> runs.pistes.firstOrNull { it.key == k }?.let { r -> Tag(runName(r), Ski.colors.run(r.color)) { a.goRun(k, "list") } } }
        }
    }
}

/** The site's notesFor(): what is odd about this run's line in the data. */
@Composable
private fun notes(p: Piste): List<String> {
    val out = ArrayList<String>()
    if (p.key == "Zuma") out += stringResource(R.string.run_note_zuma_two_lines)
    val mism = p.osmDiff.filter { it != "—" }.mapNotNull { osmGrade(it) }.filter { it.second != p.color }
    if (p.named && mism.isNotEmpty()) out += stringResource(R.string.run_note_osm_grade_mismatch, mism.joinToString(", ") { it.first }, colorName(p.color))
    if (p.named && "—" in p.osmDiff) out += stringResource(R.string.run_note_osm_no_grade)
    if (p.lines.size > 1) out += pluralStringResource(R.plurals.run_note_separate_segments, p.lines.size, nf().format(p.lines.size))
    if (p.hasArea) out += stringResource(R.string.run_note_area_polygon)
    if ("yes" in p.lit) out += stringResource(R.string.run_note_lit)
    if (p.kind == "ski-way") out += stringResource(R.string.run_note_ski_way)
    if (p.kind == "beginner-area") out += stringResource(R.string.run_note_beginner_area)
    if (!p.named) out += stringResource(R.string.run_note_unnamed_osm)
    return out
}

/** A text of the strings file by its name (the research notes, by run), or [fallback] when it has none. */
@Composable
private fun byName(name: String, fallback: String): String {
    val ctx = LocalContext.current
    @Suppress("DiscouragedApi")
    val id = remember(name) { ctx.resources.getIdentifier(name, "string", ctx.packageName) }
    return if (id != 0) stringResource(id) else fallback
}

/** Where the line comes from (the site's researchBlock): certainty, source, GPS tracks, what was found, notes, sources. */
@Composable
private fun Research(p: Piste, r: io.github.pini236.skiapp.data.Research) {
    val c = Ski.colors
    val k = "research_" + p.key.lowercase().replace(' ', '_')
    H3(stringResource(R.string.run_source_heading))
    KvText(stringResource(R.string.run_confidence_label), when (r.conf) {
        "high" -> stringResource(R.string.run_confidence_high); "medium" -> stringResource(R.string.run_confidence_medium)
        "low" -> stringResource(R.string.run_confidence_low); else -> r.conf
    })
    KvText(stringResource(R.string.run_source_label), when (r.status) {
        "osm-named" -> stringResource(R.string.run_source_osm_named); "osm-unnamed-match" -> stringResource(R.string.run_source_osm_unnamed_match)
        "gps" -> stringResource(R.string.run_source_gps); else -> r.status
    } + if (r.historical) stringResource(R.string.run_source_historical) else "")
    if (r.gps > 0) KvText(stringResource(R.string.run_gps_tracks_label), pluralStringResource(R.plurals.run_gps_tracks_value, r.gps, nf().format(r.gps)))
    r.partial?.let { KvText(stringResource(R.string.run_coverage_label), byName(k + "_partial", it)) }
    Hint(byName(k + "_notes", r.notes))
    val mta = stringResource(R.string.research_source_mta)
    r.sources.forEach { s -> Hint("•  " + if (s.any { it in '֐'..'׿' }) mta else s) }
}

/** The elevation profile (T2): drag along it and the dot moves on the mountain; then "what's ahead" and the comparison (T3). */
@Composable
private fun RunProfile(p: Piste, f: RunFacts, runs: Runs, terrain: Terrain, a: PanelActions, flying: Boolean = false) {
    val c = Ski.colors
    val n = nf()
    val pts = f.points
    var at by remember(p.key) { mutableIntStateOf(0) }
    // flying down: the profile's dot goes with the camera, as on the site (A-13)
    LaunchedEffect(flying, f) {
        if (!flying) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val d = a.flyAt() ?: break
            var best = at; var bd = Float.MAX_VALUE
            for (i in pts.indices) { val e = abs(pts[i].d - d); if (e < bd) { bd = e; best = i } }
            at = best
        }
    }
    var scrubbed by remember(p.key) { mutableStateOf(false) }
    val hs = remember(f) { pts.map { it.h } }
    val hmax = hs.max(); val hmin = hs.min(); val dmax = f.length.coerceAtLeast(1f)
    H3(stringResource(R.string.run_profile_heading))
    val desc = stringResource(R.string.run_profile_range_aria)
    // the profile reads from the top of the run, at the left, in every language (as on the site)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val here = stringResource(R.string.run_profile_from_start) + " " + metres(pts[at].d.roundToInt()) + ", " +
            stringResource(R.string.run_profile_altitude_label) + " " + metres(pts[at].h.roundToInt()) + ", " + "${pts[at].a.roundToInt()}°"
        fun go(best: Int) {
            at = best
            a.mark(pts[best].x, pts[best].y)
            if (!scrubbed) { scrubbed = true; Telemetry.event("run_profile_scrub", mapOf("run" to p.key)); Qa.log("profile scrub ${p.key}") }
        }
        // a screen reader moves along it too, as along a slider (the site's range input): up and down by about 100 m (A-15)
        Box(Modifier.fillMaxWidth().height(132.dp).semantics {
            contentDescription = desc; stateDescription = here
            progressBarRangeInfo = ProgressBarRangeInfo(at.toFloat(), 0f..(pts.size - 1).toFloat(), steps = 0)
            setProgress { v -> go(v.roundToInt().coerceIn(0, pts.size - 1)); true }
        }) {
            fun pick(x: Float, w: Float) {
                val d = ((x - 8f) / (w - 16f)).coerceIn(0f, 1f) * dmax
                var best = 0; var bd = Float.MAX_VALUE
                for (i in pts.indices) { val e = abs(pts[i].d - d); if (e < bd) { bd = e; best = i } }
                go(best)
            }
            Canvas(Modifier.fillMaxWidth().height(132.dp)
                .pointerInput(f) { detectTapGestures { o -> pick(o.x, size.width.toFloat()) } }
                // sideways only: a swipe up or down on the profile scrolls the panel
                .pointerInput(f) { detectHorizontalDragGestures { ch, _ -> pick(ch.position.x, size.width.toFloat()) } }) {
                val w = size.width; val h = size.height - 18.dp.toPx()
                val pad = 8f
                fun x(d: Float) = pad + d / dmax * (w - 2 * pad)
                fun y(v: Float) = pad + (hmax - v) / ((hmax - hmin).takeIf { it > 0 } ?: 1f) * (h - 30f)
                val base = h - 16f
                val line = Path(); val fill = Path()
                fill.moveTo(pad, base)
                pts.forEachIndexed { i, q -> if (i == 0) line.moveTo(x(q.d), y(q.h)) else line.lineTo(x(q.d), y(q.h)); fill.lineTo(x(q.d), y(q.h)) }
                fill.lineTo(w - pad, base); fill.close()
                // the steepest 100 m behind the line
                if (f.steepG > 0) drawRect(Color(0x33DC3B33), Offset(x(f.steepD), 0f), Size(x(pts[f.steepJ].d) - x(f.steepD), base))
                drawPath(fill, c.ink.copy(alpha = 0.13f))
                drawPath(line, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                // the slope band under it, in the slope colours
                var i = 0
                while (i < pts.size - 1) {
                    val col = SlopeColors.of(pts[i].a); var j = i + 1
                    while (j < pts.size - 1 && SlopeColors.of(pts[j].a).contentEquals(col)) j++
                    drawRect(Color(col[0], col[1], col[2]), Offset(x(pts[i].d), h - 14f), Size(x(pts[j].d) - x(pts[i].d) + 0.6f, 7.dp.toPx()))
                    i = j
                }
                val q = pts[at]
                // the theme's colours, so the profile reads at night too (the site's dark panel, A-11)
                drawLine(c.glacier, Offset(x(q.d), 0f), Offset(x(q.d), h - 8f), 1.5.dp.toPx())
                drawCircle(c.paper, 7.dp.toPx(), Offset(x(q.d), y(q.h)))
                drawCircle(c.glacier, 5.dp.toPx(), Offset(x(q.d), y(q.h)))
            }
            Text(n.format(hmax.roundToInt()), Modifier.align(Alignment.TopEnd), style = Ski.type.small.copy(fontSize = 11.sp), color = c.muted)
            Text(n.format(hmin.roundToInt()), Modifier.align(Alignment.BottomEnd), style = Ski.type.small.copy(fontSize = 11.sp), color = c.muted)
        }
    }
    val q = pts[at]
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(stringResource(R.string.run_profile_from_start), metres(q.d.roundToInt()))
        Stat(stringResource(R.string.run_profile_altitude_label), metres(q.h.roundToInt()))
        Stat(stringResource(R.string.run_profile_slope_here), "${q.a.roundToInt()}°")
    }
    // the slope key (the site's .slope-key)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val labels = listOf(stringResource(R.string.map_slope_upto15), "15°–25°", "25°–30°", stringResource(R.string.map_slope_over30))
        SlopeColors.HEX.forEachIndexed { k, hex ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(android.graphics.Color.parseColor(hex))))
                Text(" " + labels[k], style = Ski.type.small.copy(fontSize = 12.sp), color = c.ink)
            }
        }
    }

    // what's ahead (T3): the start, the steep part, the finish; the lifts and runs in it are links, as on the site (A-15)
    H3(stringResource(R.string.run_ahead_heading))
    val lifts = { names: List<String> -> names.joinToString(", ") }
    val liftLinks = { names: List<String> -> names.mapNotNull { nm -> runs.lifts.firstOrNull { it.name == nm }?.let { l -> nm to { a.goLift(l) } } } }
    Ahead(stringResource(R.string.run_ahead_start_title, n.format(pts.first().h.roundToInt())),
        stringResource(R.string.run_ahead_start_text, RunFacts.deg(f.g0.toDouble()).toString(),
            if (p.fromLifts.isNotEmpty()) stringResource(R.string.run_start_from_lift, lifts(p.fromLifts)) else ""), false, liftLinks(p.fromLifts))
    if (f.steepG > 0) Ahead(stringResource(R.string.run_ahead_steep_title, n.format(f.steepD.roundToInt())),
        stringResource(R.string.run_ahead_steep_text, RunFacts.deg(f.maxG).toString(), n.format((f.maxG * 100).roundToInt())), true)
    val end = when {
        p.toLifts.isNotEmpty() -> stringResource(R.string.run_end_to_lift, lifts(p.toLifts))
        p.joins.isNotEmpty() -> stringResource(R.string.run_end_continue_to, p.joins.joinToString(", "))
        else -> ""
    }
    val endLinks = if (p.toLifts.isNotEmpty()) liftLinks(p.toLifts)
        else p.joins.mapNotNull { k -> runs.pistes.firstOrNull { it.key == k }?.let { k to { a.goRun(k, "list") } } }
    Ahead(stringResource(R.string.run_ahead_end_title, n.format(pts.last().h.roundToInt())),
        stringResource(R.string.run_ahead_end_text, n.format(f.length.roundToInt()), end), false, endLinks)
    // the comparison: about as steep as, about as long as (T3)
    val cmp = remember(p.key) { RunFacts.compare(terrain, runs.pistes, p.key, CMP) }
    if (cmp != null) {
        val (steep, long) = cmp
        val line = stringResource(R.string.run_compare_line, "\u0000", "\u0001")
        Column(Modifier.padding(top = 10.dp)) {
            // the sentence with the two runs as buttons in it
            val parts = line.split('\u0000', '\u0001')
            Tags {
                parts.forEachIndexed { k, s ->
                    if (s.isNotBlank()) Text(s.trim(), Modifier.padding(vertical = 10.dp), style = Ski.type.body, color = c.ink)
                    val key = when { k == 0 && parts.size > 1 -> if (line.indexOf('\u0000') < line.indexOf('\u0001')) steep else long
                        k == 1 && parts.size > 2 -> if (line.indexOf('\u0000') < line.indexOf('\u0001')) long else steep; else -> null }
                    key?.let { kk -> runs.pistes.firstOrNull { it.key == kk }?.let { r -> Tag(runName(r), c.run(r.color)) { a.goRun(kk, "list") } } }
                }
            }
        }
    }
    Hint(stringResource(R.string.run_profile_hint))
}

/** The steepest stretch of every run, once (comparable()): the comparison of any run reads them. */
private val CMP = java.util.concurrent.ConcurrentHashMap<String, Pair<Double, Int>>()

@Composable
private fun Ahead(title: String, text: String, steep: Boolean, links: List<Pair<String, () -> Unit>> = emptyList()) {
    val c = Ski.colors
    // each name in the sentence, in order, opens its lift or run
    val t = text.trim()
    val linked = buildAnnotatedString {
        append(t)
        var from = 0
        for ((name, go) in links) {
            val i = t.indexOf(name, from)
            if (i < 0) continue
            addLink(LinkAnnotation.Clickable(name, TextLinkStyles(SpanStyle(color = c.glacier, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))) { go() }, i, i + name.length)
            from = i + name.length
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(Modifier.padding(top = 5.dp, end = 10.dp).size(10.dp).background(if (steep) Color(0xFFDC3B33) else c.ink))
        Column {
            Text(title, style = Ski.type.bodyBold, color = c.ink)
            Text(linked, style = Ski.type.body.copy(fontSize = 14.sp), color = c.muted)
        }
    }
}

/** A run's videos (the site's vidBlock): the thumbnail opens YouTube; and a search for more. */
@Composable
private fun Videos(p: Piste, list: List<Video>) {
    val c = Ski.colors
    val ctx = LocalContext.current
    H3(stringResource(R.string.run_videos_heading))
    if (list.isEmpty()) Hint(stringResource(R.string.run_videos_empty))
    for (v in list) {
        val id = v.youtubeId
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
            // the YouTube id only, never an address (docs/GROWTH.md; A-36); the site counts only YouTube's videos too
            if (id != null) Telemetry.event("video_play", mapOf("run" to p.key, "video" to id))
            openLink(ctx, v.url)
        }) {
            if (id != null) Thumb(id, if (v.title.isNotBlank()) stringResource(R.string.run_video_play_aria_titled, v.title) else stringResource(R.string.run_video_play_aria))
            Text(v.title.ifBlank { runCatching { URL(v.url).host.removePrefix("www.") }.getOrDefault(v.url) }, Modifier.padding(top = 4.dp),
                style = Ski.type.bodyBold.copy(fontSize = 14.sp, textDecoration = TextDecoration.Underline), color = c.glacier)
            val sub = listOf(v.channel, v.length).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = Ski.type.small, color = c.muted)
        }
    }
    val q = "Gudauri " + (if (p.key == "Firni ?") "Firni" else p.key) + " ski"
    Text(stringResource(R.string.run_videos_search_youtube, if (p.key == "Firni ?") "Firni" else p.key),
        Modifier.padding(top = 8.dp).heightIn(min = 44.dp).clickable { openLink(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q)) }.padding(vertical = 10.dp),
        style = Ski.type.small.copy(textDecoration = TextDecoration.Underline), color = c.glacier)
}

/** A video's picture from YouTube, loaded once and kept while the app runs. */
@Composable
private fun Thumb(id: String, description: String) {
    var bmp by remember(id) { mutableStateOf(THUMBS[id]) }
    LaunchedEffect(id) {
        if (bmp == null) bmp = withContext(Dispatchers.IO) {
            runCatching { URL("https://i.ytimg.com/vi/$id/hqdefault.jpg").openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()?.also { THUMBS[id] = it }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF13233A)).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop) }
        Box(Modifier.size(56.dp, 40.dp).background(Color(0xE6DC3B33)), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 20.sp) }
    }
}

private val THUMBS = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

/** A lift's panel (the site's showLift): type, length, ride time, seats, people an hour, stations and rise, year, runs. */
@Composable
fun ColumnScope.LiftHead(l: Lift) {
    val c = Ski.colors
    Text("⇡ " + l.name.ifBlank { stringResource(R.string.lift_unnamed) }, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (32f / 44f)), color = c.ink)
}

@Composable
fun ColumnScope.LiftBody(l: Lift, runs: Runs, terrain: Terrain, a: PanelActions) {
    val c = Ski.colors
    val n = nf()
    val ctx = LocalContext.current
    val kind = when (l.kind) {
        "chair_lift" -> stringResource(R.string.lift_kind_chair_lift); "gondola" -> stringResource(R.string.lift_kind_gondola)
        "platter" -> stringResource(R.string.lift_kind_platter); "magic_carpet" -> stringResource(R.string.lift_kind_magic_carpet)
        "drag_lift" -> stringResource(R.string.lift_kind_drag_lift); "t_bar" -> stringResource(R.string.lift_kind_t_bar); else -> l.kind
    }
    KvText(stringResource(R.string.lift_type_label), kind + if (l.status == "inactive") stringResource(R.string.lift_inactive_suffix) else "")
    KvText(stringResource(R.string.common_length_label), lengthText(l.len))
    l.dur?.let { KvText(stringResource(R.string.lift_ride_time_label), stringResource(R.string.lift_ride_time_value, it)) }
    l.occ?.let { KvText(stringResource(R.string.lift_seats_label), it + if (l.bubble == "yes") stringResource(R.string.lift_bubble_suffix) else "") }
    l.cap?.let { KvText(stringResource(R.string.lift_capacity_label), stringResource(R.string.lift_capacity_value, it)) }
    val m = l.pts.size / 2
    if (m >= 2) {
        val ea = terrain.elev(l.pts[0], l.pts[1]).roundToInt(); val eb = terrain.elev(l.pts[(m - 1) * 2], l.pts[(m - 1) * 2 + 1]).roundToInt()
        KvText(stringResource(R.string.lift_stations_label), stringResource(R.string.lift_stations_value, n.format(minOf(ea, eb)), n.format(maxOf(ea, eb))))
        KvText(stringResource(R.string.lift_rise_label), stringResource(R.string.lift_rise_model_value, n.format(abs(eb - ea))) +
            (l.rise?.let { stringResource(R.string.lift_rise_osm_suffix, it) } ?: ""))
    }
    l.year?.let { KvText(stringResource(R.string.lift_built_label), it) }
    H3(stringResource(R.string.lift_runs_from_top_heading))
    LiftRuns(runs.pistes.filter { l.name.isNotBlank() && l.name in it.fromLifts }, a)
    H3(stringResource(R.string.lift_runs_to_bottom_heading))
    LiftRuns(runs.pistes.filter { l.name.isNotBlank() && l.name in it.toLifts }, a)
    if (l.id.isNotBlank()) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text("OSM: ${l.id}", Modifier.padding(top = 10.dp).heightIn(min = 32.dp).clickable { openLink(ctx, "https://www.openstreetmap.org/way/${l.id}") }.padding(vertical = 6.dp),
            style = Ski.type.small.copy(textDecoration = TextDecoration.Underline), color = c.glacier)
    }
}

@Composable
private fun LiftRuns(list: List<Piste>, a: PanelActions) {
    if (list.isEmpty()) Text("—", style = Ski.type.body, color = Ski.colors.ink)
    else Tags { list.forEach { r -> Tag(runName(r), Ski.colors.run(r.color)) { a.goRun(r.key, "list") } } }
}

/** The map's filters (the site's): the four colours, the unnamed sections and the lifts. */
val FILTERS = listOf("green", "blue", "red", "black", "unnamed", "lifts")

/** All the runs (the site's overview): the list by colour, the partly found ones, the filters, and where it all comes from. */
@Composable
fun ColumnScope.RunList(runs: Runs, order: List<String>, hidden: Set<String>, onFilter: (String) -> Unit, fetched: String, researchDate: String, a: PanelActions) {
    val c = Ski.colors
    // the filters
    Text(stringResource(R.string.map_filter), Modifier.padding(top = 4.dp, bottom = 6.dp), style = Ski.type.small, color = c.muted)
    Tags {
        for (k in FILTERS) {
            val on = k !in hidden
            val label = when (k) { "unnamed" -> stringResource(R.string.map_unnamed); "lifts" -> stringResource(R.string.map_lifts); else -> colorName(k) }
            Row(Modifier.heightIn(min = 44.dp).border(1.5.dp, if (on) c.ink else c.rule).background(if (on) c.paper else c.snow)
                .clickable(role = Role.Checkbox) { onFilter(k) }.semantics { contentDescription = label + if (on) " ✓" else "" }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val sw = when (k) { "unnamed" -> c.muted; "lifts" -> c.lift; else -> c.run(k) }
                Box(Modifier.size(12.dp).background(if (on) sw else Color.Transparent).border(1.5.dp, sw))
                Text(label, style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = if (on) c.ink else c.muted)
            }
        }
    }
    Text(stringResource(R.string.map_overview_lead), Modifier.padding(top = 12.dp), style = Ski.type.body.copy(fontSize = 14.sp), color = c.ink)
    H3(stringResource(R.string.map_overview_on_map_count, order.size.toString()))
    for (k in order) {
        val p = runs.pistes.firstOrNull { it.key == k } ?: continue
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { a.goRun(k, "list") }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).background(c.run(p.color)))
            Text(runName(p), Modifier.weight(1f), style = Ski.type.bodyBold, color = c.ink)
            Text(lengthText(p.len), style = Ski.type.small, color = c.muted)
        }
    }
    val partial = runs.pistes.filter { it.research?.partial != null }
    if (partial.isNotEmpty()) {
        H3(stringResource(R.string.map_overview_partial_count, partial.size.toString()))
        Tags { partial.forEach { p -> Tag(p.key, c.run(p.color)) { a.goRun(p.key, "list") } } }
        Hint(stringResource(R.string.map_overview_partial_hint))
    }
    H3(stringResource(R.string.map_overview_source_heading))
    Hint(stringResource(R.string.map_overview_source_text, fetched, researchDate))
}

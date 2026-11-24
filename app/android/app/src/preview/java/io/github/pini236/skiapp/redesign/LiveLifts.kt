package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.places.LocalPlaces
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.status.agoText
import io.github.pini236.skiapp.ui.Plex
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The completion round, part 2 (Q01 to Q03, design/handoff/2026-11-21-redesign-around): the lifts in the new look. The
 * report and its rules are today's (LiftStatus: stale after half an hour, a run open only with a lift up to it); only
 * how they are shown is new. No report: the facts of each lift, never "closed" and never "no report" on every row.
 */
internal object LiveLifts {
    /** The board's lifts, in today's order (the main side's named lifts that run). */
    fun board(runs: Runs?): List<Lift> = runs?.mainLifts.orEmpty().filter { it.name.isNotBlank() && it.status != "inactive" }

    /** Bottom and top of a lift on the ground ([x0, y0, x1, y1], the top second), from its line's two ends. */
    fun ends(l: Lift, elev: (Float, Float) -> Float): FloatArray? {
        val m = l.pts.size / 2
        if (m < 2) return null
        val a = floatArrayOf(l.pts[0], l.pts[1]); val b = floatArrayOf(l.pts[(m - 1) * 2], l.pts[(m - 1) * 2 + 1])
        return if (elev(a[0], a[1]) <= elev(b[0], b[1])) floatArrayOf(a[0], a[1], b[0], b[1]) else floatArrayOf(b[0], b[1], a[0], a[1])
    }

    /** The rise in metres from the height model (the lift's two ends), or null without a line. */
    fun rise(l: Lift, elev: (Float, Float) -> Float): Int? = ends(l, elev)?.let { e -> abs(elev(e[2], e[3]) - elev(e[0], e[1])).roundToInt() }
}

@Composable
private fun reasonText(r: String?): String? = when (r) {
    "wind" -> stringResource(R.string.status_reason_wind)
    "weather" -> stringResource(R.string.status_reason_weather)
    "maintenance" -> stringResource(R.string.status_reason_maintenance)
    "season" -> stringResource(R.string.status_reason_season)
    else -> null
}

/** The pill at the top of the mountain: the report's summary, or no recent information, or none yet before the season. */
@Composable
internal fun LiftsPillText(s: LiftStatus?, inSeason: Boolean): Pair<String, Boolean> = when {
    s != null && s.fresh -> stringResource(R.string.status_bar_summary, s.open.toString(), s.names.size.toString(), agoText(s)) to true
    inSeason -> stringResource(R.string.status_bar_no_recent) to false
    else -> stringResource(R.string.status_bar_no_data_yet) to false
}

/** The state dot: open green, closed red, without a report an empty ring. */
@Composable
internal fun StateDot(open: Boolean?, k: SheetInk) {
    val m = Modifier.size(9.dp).clip(CircleShape)
    Box(when (open) { true -> m.background(k.good); false -> m.background(k.red); null -> m.border(1.5.dp, k.ink2, CircleShape) })
}

/**
 * Q01 and Q02: the lifts' sheet. With a report: "lifts 12/14" and "only what's open for me"; each row its kind and ride
 * time and, at its end, open or closed (with the reason). Without one: the count alone and "no report" once, and each
 * row ends in its rise. A tap on a row opens that lift (Q03).
 */
@Composable
internal fun LiftsSheet(lifts: List<Lift>, s: LiftStatus?, scene: MapScene, forMe: Boolean, k: SheetInk, haptics: Haptics,
                        onForMe: (Boolean) -> Unit, onLift: (Lift) -> Unit, opening: Pair<String, String>? = null, onOpening: () -> Unit = {},
                        head: (@Composable () -> Unit)? = null, other: (@Composable () -> Unit)? = null) {
    val fresh = s?.fresh == true
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    LiveSheet(k) {
        // "the mountain now" (S05): its heading and tabs above; the weather tab instead of the lifts
        head?.invoke()
        if (other != null) { other(); return@LiveSheet }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.app_lifts_title), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
            Text(if (fresh) stringResource(R.string.app_lifts_count, s!!.open.toString(), lifts.size.toString()) else lifts.size.toString(),
                Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 23.sp, color = k.ink)
            if (fresh) {
                val on = forMe
                Box(Modifier.heightIn(min = 44.dp).semantics { role = Role.Switch }.clickable { haptics.tick(.2f); onForMe(!on) }, contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.status_only_open_for_me), Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) k.acc else Color.Transparent)
                        .border(1.dp, if (on) k.acc else k.line, RoundedCornerShape(18.dp)).padding(horizontal = 13.dp, vertical = 8.dp),
                        fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (on) Color.White else k.ink)
                }
            } else Text(stringResource(R.string.status_no_report), fontFamily = Plex, fontSize = 13.5.sp, color = k.ink2)
        }
        // before the season (Q02, handoff section 14.7): the planned opening is the first row; a tap opens the count
        if (opening != null) Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) { role = Role.Button }
            .clickable { haptics.tick(.3f); onOpening() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(opening.first, Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 1)
            Text(opening.second, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.acc, maxLines = 1)
        }
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            lifts.forEachIndexed { i, l ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 0.dp).heightIn(min = 1.dp).background(k.line))
                val open = s?.isOpen(l.name)
                val kind = liftKind(l.kind)
                val kt = l.dur?.let { stringResource(R.string.app_lift_kind_time, kind, it) } ?: kind
                val end = when {
                    fresh && open == true -> stringResource(R.string.status_lift_open)
                    fresh && open == false -> reasonText(s!!.reason(l.name))?.let { stringResource(R.string.app_lift_closed_why, it) } ?: stringResource(R.string.status_lift_closed)
                    fresh -> null
                    else -> LiveLifts.rise(l, scene.terrain::elev)?.let { stringResource(R.string.app_lift_rise_up, nf.format(it)) }
                }
                val a11y = stringResource(R.string.app_lift_a11y_row, l.name, kind, l.dur ?: "", end ?: "")
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = a11y; role = Role.Button }
                    .clickable { haptics.tick(.25f); onLift(l) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (fresh) StateDot(open, k)
                    Column(Modifier.weight(1f)) {
                        Text("⁦${l.name}⁩", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = k.ink, maxLines = 1)
                        Text(kt, fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2, maxLines = 1)
                    }
                    end?.let { Text(it, fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.5.sp,
                        color = when { !fresh -> k.ink2; open == false -> k.red; else -> k.good }) }
                }
            }
        }
    }
}

/**
 * Q03: one lift. Its sign, name, kind and year, the state; three numbers (minutes, metres up, km), only those in the
 * data; the stations; the runs from the top (named ones); the places near its two ends (today's block); "meet here" and
 * "the runs". A field the data lacks is not shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiftPanel(l: Lift, s: LiftStatus?, scene: MapScene, k: SheetInk, haptics: Haptics,
                       onRun: (String) -> Unit, onMeet: (() -> Unit)?, onRuns: () -> Unit, onPlace: (io.github.pini236.skiapp.places.Place) -> Unit = {}) {
    val fresh = s?.fresh == true
    val open = s?.isOpen(l.name)
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    val elev = scene.terrain::elev
    LiveSheet(k, opaque = true) {
        // the facts scroll (the places near the stations make it long); the two buttons stay at the bottom, always whole
        // the panel opens to 46 % of the screen at most (handoff section 14.6): the facts scroll within it, the buttons below
        val maxFacts = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .46f - 150f).coerceAtLeast(160f).dp
        Column(Modifier.heightIn(max = maxFacts).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(k.line), contentAlignment = Alignment.Center) { LiftSign(k.ink) }
                Column(Modifier.weight(1f)) {
                    Text("⁦${l.name}⁩", fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 25.sp, color = k.ink, maxLines = 1)
                    val sub = listOfNotNull(liftKind(l.kind), l.year?.let { stringResource(R.string.app_lift_built_in, it) }).joinToString(" · ")
                    Text(sub, fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
                }
                when {
                    fresh && open != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StateDot(open, k)
                        val t = if (open) stringResource(R.string.status_lift_open)
                            else reasonText(s!!.reason(l.name))?.let { stringResource(R.string.app_lift_closed_why, it) } ?: stringResource(R.string.status_lift_closed)
                        Text(t, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = if (open) k.good else k.red)
                    }
                    !fresh -> Text(stringResource(R.string.status_no_report), fontFamily = Plex, fontSize = 14.5.sp, color = k.ink2)
                }
            }
            // three numbers, those in the data only, side by side
            val nums = listOfNotNull(
                l.dur?.let { it to stringResource(R.string.app_lift_cap_min) },
                LiveLifts.rise(l, elev)?.let { nf.format(it) to stringResource(R.string.app_lift_cap_rise) },
                l.len.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.getDefault(), it / 1000.0) to stringResource(R.string.app_lift_cap_km) },
            )
            if (nums.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for ((v, c) in nums) Column {
                    Text(v, fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 34.sp, color = k.ink)
                    Text(c, fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
                }
            }
            LiveLifts.ends(l, elev)?.let { e ->
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lift_stations_label), Modifier.weight(1f), fontFamily = Plex, fontSize = 13.5.sp, color = k.ink2)
                    Text(stringResource(R.string.lift_stations_value, nf.format(elev(e[0], e[1]).roundToInt()), nf.format(elev(e[2], e[3]).roundToInt())),
                        fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = k.ink)
                }
            }
            val fromTop = scene.runs.pistes.filter { it.named && l.name in it.fromLifts }
            if (fromTop.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lift_runs_from_top_heading), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = k.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (p in fromTop) Row(Modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.clickable { haptics.tick(.3f); onRun(p.key) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(Color(io.github.pini236.skiapp.game.runColor(p.color))))
                        Text("⁦${p.title}⁩", fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = k.ink)
                    }
                }
            }
            LiftPlacesLine(l, k, onPlace)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onMeet != null) Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(k.acc)
                .semantics { role = Role.Button }.clickable { haptics.tick(.4f); onMeet() }, contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.app_lift_meet_here), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            SecondaryButton(stringResource(R.string.app_lift_runs_btn), k, if (onMeet == null) Modifier.weight(1f) else Modifier) { haptics.tick(.3f); onRuns() }
        }
    }
}

@Composable
private fun <T> remember(calc: () -> T): T = androidx.compose.runtime.remember(calc)

/**
 * Near the lift's stations, as on the board (Q03, handoff section 14.6): for each station one line of up to three
 * names and "all N" when there are more (a tap shows them all), or "no information"; then "check on the spot".
 */
@Composable
private fun LiftPlacesLine(l: Lift, k: SheetInk, onPlace: (io.github.pini236.skiapp.places.Place) -> Unit) {
    val places = LocalPlaces.current ?: return
    val top = places.stationOf(l.id, bottom = false); val bottom = places.stationOf(l.id, bottom = true)
    if (top == null && bottom == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((st, head) in listOf(top to R.string.places_near_top, bottom to R.string.places_near_bottom)) {
            if (st == null) continue
            val near = places.near(st)
            var all by androidx.compose.runtime.remember(st) { androidx.compose.runtime.mutableStateOf(false) }
            // "all N" (T10): the list in a sheet of its own, by distance
            if (all) PlacesSheet(st, near, head == R.string.places_near_bottom, l, k, onPlace = { pl -> all = false; onPlace(pl) }) { all = false }
            Text(stringResource(head), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = k.ink)
            if (near.isEmpty()) Text(stringResource(R.string.places_none), fontFamily = Plex, fontSize = 13.sp, color = k.ink2)
            else Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).let { m -> if (near.size > 3) m.semantics { role = Role.Button }.clickable { all = !all } else m },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = near.take(3).map { "⁦${io.github.pini236.skiapp.places.label(it.place)}⁩" }
                Text(shown.joinToString(", "), Modifier.weight(1f, fill = false), fontFamily = Plex,
                    fontSize = 13.5.sp, color = k.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (near.size > 3) Text(stringResource(R.string.places_all_n, near.size.toString()), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = k.acc)
            }
        }
        Text(stringResource(R.string.places_check), fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
    }
}

/**
 * T10, all the places near a station: the head (near the bottom or top station; the lift, how many, up to 250 m), a row
 * for each by distance (the kind's circle, the name or the kind, the kind, seasonal, the distance), "check on the spot".
 * No hours, phones or ratings. A tap moves the mountain to the place and marks it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlacesSheet(st: io.github.pini236.skiapp.places.Station, near: List<io.github.pini236.skiapp.places.Near>, bottom: Boolean, l: Lift, k: SheetInk,
                        onPlace: (io.github.pini236.skiapp.places.Place) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(st.id) { io.github.pini236.skiapp.qa.Qa.log("redesign places ${st.id} ${near.size}") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = k.surf.copy(alpha = 1f)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (bottom) R.string.places_near_bottom else R.string.places_near_top), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = k.ink)
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.app_places_head_n, near.size, "⁦${l.name}⁩", near.size.toString(), "250"),
                fontFamily = Plex, fontSize = 13.sp, color = k.ink2)
            val nf = remember { java.text.NumberFormat.getIntegerInstance() }
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                for ((i, n) in near.withIndex()) {
                    if (i > 0) Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(k.line))
                    val kind = kindWord(n.place.kind)
                    val title = n.place.name ?: kind
                    val sub = when {
                        n.place.name == null -> stringResource(R.string.app_places_unnamed)
                        n.place.seasonal -> kind + " · " + stringResource(R.string.places_seasonal)
                        else -> kind
                    }
                    val dist = stringResource(R.string.common_unit_m, nf.format(n.d))
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).semantics(mergeDescendants = true) { contentDescription = "$title, $kind, $dist"; role = Role.Button }
                        .clickable { onPlace(n.place) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(io.github.pini236.skiapp.places.kindColor(n.place.kind)), contentAlignment = Alignment.Center) {
                            Text(kind.take(1), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("⁦$title⁩", fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink, maxLines = 1)
                            Text(sub, fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2, maxLines = 1)
                        }
                        Text(dist, fontFamily = Plex, fontSize = 13.5.sp, color = k.ink2)
                    }
                }
            }
            Text(stringResource(R.string.places_check), Modifier.padding(vertical = 8.dp), fontFamily = Plex, fontSize = 12.5.sp, color = k.ink2)
        }
    }
}

@Composable
private fun kindWord(kind: String) = stringResource(when (kind) {
    "food" -> R.string.places_kind_food; "cafe" -> R.string.places_kind_cafe; "aid" -> R.string.places_kind_aid; else -> R.string.places_kind_wc
})

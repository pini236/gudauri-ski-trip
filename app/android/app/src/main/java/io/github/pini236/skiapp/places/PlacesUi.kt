package io.github.pini236.skiapp.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.meet.MeetView
import io.github.pini236.skiapp.meet.Station as MeetStation
import io.github.pini236.skiapp.ui.Ski
import kotlin.math.roundToInt

/** The places, for every screen that shows them (MainActivity provides it): null when the feature is off or the file is missing. */
val LocalPlaces = compositionLocalOf<Places?> { null }

/** One colour per kind (site/css/site.css, `--pl-*`): the same in the day and the night. */
fun kindColor(kind: String): Color = when (kind) {
    "food" -> Color(0xFFE8702A); "cafe" -> Color(0xFF8A5A33); "aid" -> Color(0xFF1E9E4A); else -> Color(0xFF6A4FB5)
}

@Composable
private fun kindName(kind: String) = stringResource(when (kind) {
    "food" -> R.string.places_kind_food; "cafe" -> R.string.places_kind_cafe; "aid" -> R.string.places_kind_aid; else -> R.string.places_kind_wc
})

/** A place's name, or its kind when the map gives it none. */
@Composable
private fun label(p: Place): String = p.name ?: kindName(p.kind)

@Composable
private fun Dot(kind: String, diameter: androidx.compose.ui.unit.Dp = 10.dp, ring: Boolean = true) {
    val paper = Ski.colors.paper
    Canvas(Modifier.size(diameter)) {
        val r = size.minDimension / 2
        if (ring) drawCircle(paper, r + 1.5.dp.toPx())
        drawCircle(kindColor(kind), r)
    }
}

/** A row of the list: the dot, the name, the kind when there is a name, and "seasonal". */
@Composable
private fun PlaceRow(n: Near) {
    val c = Ski.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(n.place.kind, ring = false)
        Text(buildString {
            append("⁨").append(label(n.place)).append("⁩")
        }, style = Ski.type.body.copy(fontSize = 14.sp), color = c.ink)
        if (n.place.name != null) Text(kindName(n.place.kind), style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
        if (n.place.seasonal) Text(stringResource(R.string.places_seasonal), style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
    }
}

/** What is near one end of a lift: up to three, "all N" for the rest, "no information" for a station without any. */
@Composable
private fun Block(places: Places, s: Station?, head: String) {
    val c = Ski.colors
    var all by remember { mutableStateOf(false) }
    val it = s?.let { places.near(it) }.orEmpty()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(head, Modifier.padding(bottom = 6.dp), style = Ski.type.bodyBold, color = c.ink)
        if (it.isEmpty()) {
            Text(stringResource(R.string.places_none), style = Ski.type.small, color = c.muted)
            return
        }
        for (n in it.take(3)) PlaceRow(n)
        if (it.size > 3) {
            Text(stringResource(R.string.places_all_n, it.size), Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { all = !all }
                .semantics { stateDescription = if (all) "✓" else "" }.padding(top = 12.dp), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.glacier)
            if (all) for (n in it.drop(3)) PlaceRow(n)
        }
        Text(stringResource(R.string.places_check), Modifier.padding(top = 6.dp), style = Ski.type.small, color = c.muted)
    }
}

/** The lift panel's "near the station", top then bottom. A lift that is on no station (the unnamed conveyor) gets nothing. */
@Composable
fun ColumnScope.LiftPlaces(l: Lift) {
    val places = LocalPlaces.current ?: return
    val top = places.stationOf(l.id, bottom = false); val bottom = places.stationOf(l.id, bottom = true)
    if (top == null && bottom == null) return
    Block(places, top, stringResource(R.string.places_near_top))
    Block(places, bottom, stringResource(R.string.places_near_bottom))
}

/** The meeting card's one line: the names, or just the count at a base with many. Nothing when the station has no information. */
@Composable
fun MeetPlaces(s: MeetStation, modifier: Modifier = Modifier) {
    val places = LocalPlaces.current ?: return
    val near = s.ends.mapNotNull { places.stationOf(it.lift.id, bottom = !it.top) }.firstOrNull { places.near(it).isNotEmpty() }?.let { places.near(it) } ?: return
    val text = if (near.size > 3) stringResource(R.string.places_meet_count, near.size)
    else stringResource(R.string.places_meet_names, near.joinToString(", ") { label(it.place) })
    Text(text, modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), style = Ski.type.small, color = Ski.colors.muted)
}

/** The map's "food" key: off on every visit; on, the dots grow into tags. Like the weather key, beside it. */
@Composable
fun PlacesKey(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ski.colors
    val fg = if (on) c.paper else c.ink
    val aria = stringResource(R.string.places_chip_aria)
    Row(modifier.heightIn(min = 44.dp).background(if (on) c.ink else c.paper).border(1.5.dp, c.ink)
        .clickable(role = Role.Switch, onClick = onClick).semantics { contentDescription = aria; stateDescription = if (on) "✓" else "" }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Canvas(Modifier.size(18.dp)) {
            val k = size.width / 24f
            val st = Stroke(2 * k, cap = StrokeCap.Round)
            // a fork and a knife (the site's chip icon)
            drawPath(Path().apply { moveTo(7 * k, 3 * k); lineTo(7 * k, 11 * k); moveTo(5 * k, 3 * k); lineTo(5 * k, 8 * k)
                moveTo(9 * k, 3 * k); lineTo(9 * k, 8 * k); moveTo(7 * k, 11 * k); lineTo(7 * k, 21 * k) }, fg, style = st)
            drawPath(Path().apply { moveTo(17 * k, 21 * k); lineTo(17 * k, 3 * k); cubicTo(14.5f * k, 4 * k, 13 * k, 7 * k, 13 * k, 11 * k); lineTo(17 * k, 11 * k) }, fg, style = st)
        }
        Text(stringResource(R.string.places_chip), style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = fg)
    }
}

/**
 * The dots over the top view (the site's PL.layout): small coloured dots beside every station that has something, the key
 * turns them into tags with a count; a café on the slope gets a dashed ring. Not for touch: the map under it still moves.
 */
@Composable
fun PlacesLayer(view: MeetView, tags: Boolean, modifier: Modifier = Modifier) {
    val places = LocalPlaces.current ?: return
    val c = Ski.colors
    val d = LocalDensity.current
    val withPlaces = remember(places) { places.withPlaces() }
    val slope = remember(places) { places.onSlope() }
    // a tag that would sit on another one falls back to the dots, as on the site
    val taken = ArrayList<FloatArray>()
    Box(modifier.clearAndSetSemantics { }) {
        for ((st, kinds) in withPlaces) {
            val x = view.sx(Geo.x(st.lon)) + with(d) { 9.dp.toPx() }; val y = view.sy(Geo.y(st.lat)) - with(d) { 9.dp.toPx() }
            val count = places.near(st).size
            val w = with(d) { (kinds.size * 15 + 14 + if (tags) 16 else 0).dp.toPx() }; val h = with(d) { 24.dp.toPx() }
            var tag = tags
            if (tags) {
                val box = floatArrayOf(x, y - h / 2, w, h)
                if (taken.any { box[0] < it[0] + it[2] && box[0] + box[2] > it[0] && box[1] < it[1] + it[3] && box[1] + box[3] > it[1] }) tag = false else taken += box
            }
            Row(Modifier.offset { IntOffset(x.roundToInt(), (y - with(d) { 5.dp.toPx() }).roundToInt()) }
                .then(if (tag) Modifier.background(c.paper).border(1.5.dp, c.ink).padding(horizontal = 7.dp, vertical = 3.dp) else Modifier),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tag) 5.dp else 3.dp)) {
                for (k in kinds) Dot(k, ring = !tag)
                if (tag) Text(count.toString(), style = Ski.type.bodyBold.copy(fontSize = 12.sp), color = c.ink)
            }
        }
        for (p in slope) {
            val x = view.sx(Geo.x(p.lon)); val y = view.sy(Geo.y(p.lat))
            Box(Modifier.offset { IntOffset((x - with(d) { 6.dp.toPx() }).roundToInt(), (y - with(d) { 6.dp.toPx() }).roundToInt()) }) {
                Canvas(Modifier.size(12.dp)) {
                    drawCircle(c.paper, size.minDimension / 2)
                    drawCircle(kindColor(p.kind), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
            }
            if (tags) Text(p.name ?: "", Modifier.offset { IntOffset((x + with(d) { 10.dp.toPx() }).roundToInt(), (y - with(d) { 9.dp.toPx() }).roundToInt()) }
                .background(c.paper).border(1.5.dp, c.ink).padding(horizontal = 6.dp, vertical = 2.dp), style = Ski.type.small.copy(fontSize = 12.sp), color = c.ink)
        }
    }
}

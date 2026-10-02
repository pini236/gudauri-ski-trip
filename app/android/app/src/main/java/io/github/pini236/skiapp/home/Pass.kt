package io.github.pini236.skiapp.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.Flight
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.trip.airportCode
import io.github.pini236.skiapp.trip.airportName
import io.github.pini236.skiapp.ui.Ski
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLES = 11

/** The plane between the airport codes, pointing on in the reading direction. */
@Composable
private fun Plane(color: Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.size(28.dp)) {
        val s = size.width / 24f
        val p = androidx.compose.ui.graphics.Path().apply {
            // the site's plane icon, pointing right; mirrored in right-to-left
            val pts = listOf(2f to 13.5f, 2f to 11.5f, 10f to 7f, 10f to 2.5f, 13f to 2.5f, 13f to 7f, 21f to 11.5f, 21f to 13.5f, 13f to 11f, 13f to 16f,
                15.5f to 18f, 15.5f to 19.5f, 12f to 18.5f, 8.5f to 19.5f, 8.5f to 18f, 11f to 16f, 11f to 11f)
            pts.forEachIndexed { i, (x, y) ->
                // rotate -90° about the centre (as on the site), then mirror for RTL
                val rx = y; val ry = 24f - x
                val fx = if (rtl) 24f - rx else rx
                if (i == 0) moveTo(fx * s, ry * s) else lineTo(fx * s, ry * s)
            }
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun Label(t: String) = Text(t, style = Ski.type.label.copy(fontSize = 11.sp), color = Ski.colors.bpMuted)

@Composable
private fun Value(t: String, ltr: Boolean = false) = Text(t, style = Ski.type.bodyBold.copy(fontSize = 14.sp, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content), color = Ski.colors.bpInk)

/** The perforation between the pass and its stub: a dashed line and a half-circle bite at each end. */
@Composable
private fun Perforation(bg: Color) {
    val dash = Ski.colors.dash
    Canvas(Modifier.width(2.dp).fillMaxHeight()) {
        drawLine(dash, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        drawCircle(bg, 11.dp.toPx(), Offset(size.width / 2, 0f))
        drawCircle(bg, 11.dp.toPx(), Offset(size.width / 2, size.height))
    }
}

/**
 * The approved horizontal pass (AB5 by day, AB6 at night, H3 and H4 of round 10), for the user's own trip.
 * Tapping the pass switches outbound and return; tapping the stub tears it along the perforation, one bite per
 * hole, and it comes back (as on the site and in the spike).
 */
@Composable
fun TripPass(trip: Trip, daysLeft: Long, bg: Color, haptics: Haptics, sounds: Sounds, onEdit: () -> Unit) {
    val c = Ski.colors
    var showBack by remember { mutableStateOf(false) }
    val leg: Flight = if (showBack && trip.back != null) trip.back else trip.out
    val scope = rememberCoroutineScope()
    val tear = remember { Animatable(0f) }
    var torn by remember { mutableStateOf(false) }
    val title = stringResource(R.string.app_trip_title)
    val legName = stringResource(if (leg === trip.out) R.string.app_trip_out else R.string.app_trip_back)
    val fromCode = airportCode(leg.from).ifEmpty { "—" }; val toCode = airportCode(leg.to).ifEmpty { "—" }
    val swapDesc = stringResource(if (showBack) R.string.ticket_swap_out else R.string.ticket_swap_return)

    Column {
        Row(
            Modifier.fillMaxWidth().height(230.dp).shadow(10.dp, RectangleShape, clip = false)
                .clickable(enabled = trip.back != null, role = Role.Button, onClick = {
                    showBack = !showBack; haptics.tick(0.5f); sounds.play("ticket-slide", 0.7f)
                }).semantics { contentDescription = swapDesc },
        ) {
            // the pass itself (start side)
            Column(Modifier.weight(1f).fillMaxHeight().background(c.bpPaper)) {
                Row(Modifier.fillMaxWidth().background(c.bpStrip).padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$title · $legName", style = Ski.type.label.copy(fontSize = 12.sp), color = c.bpOnStrip)
                    Text(Trip.showDay(leg.date), style = Ski.type.label.copy(fontSize = 12.sp), color = c.bpOnStrip)
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Label(stringResource(R.string.ticket_from))
                            Text(fromCode, style = Ski.type.title.copy(fontSize = 50.sp, textDirection = TextDirection.Ltr), color = c.bpInk)
                            Text(airportName(leg.from), style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.bpInk, maxLines = 1)
                        }
                        Box(Modifier.align(Alignment.CenterVertically)) { Plane(c.bpAccent) }
                        Column(horizontalAlignment = Alignment.End) {
                            Label(stringResource(R.string.ticket_to))
                            Text(toCode, style = Ski.type.title.copy(fontSize = 50.sp, textDirection = TextDirection.Ltr), color = c.bpInk)
                            Text(airportName(leg.to), style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.bpInk, maxLines = 1)
                        }
                    }
                    Grid(
                        stringResource(R.string.ticket_flight) to (leg.number.ifBlank { "—" } to true),
                        stringResource(R.string.ticket_departs) to (Trip.showTime(leg.departs).ifEmpty { "—" } to false),
                        stringResource(R.string.ticket_arrives) to (Trip.showTime(leg.arrives).ifEmpty { "—" } to false),
                    )
                    val ski = trip.skiDays()
                    Grid(
                        stringResource(R.string.app_trip_traveller) to (stringResource(R.string.app_trip_me) to false),
                        stringResource(R.string.ticket_ski_days) to ((ski?.let { "${Trip.showDay(it.first)}–${Trip.showDay(it.second)}".let { s -> s } } ?: "—") to true),
                        stringResource(R.string.app_trip_back) to ((trip.back?.let { Trip.showDay(it.date) + (it.departs?.let { t -> " · " + Trip.showTime(t) } ?: "") } ?: "—") to true),
                    )
                }
            }
            Perforation(bg)
            // the stub (end side): the slot behind it, and the stub that tears and comes back
            Box(Modifier.width(100.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.ticket_torn), style = Ski.type.small, color = c.bpMuted, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.ticket_see_you), style = Ski.type.small, color = c.bpMuted, textAlign = TextAlign.Center)
                }
                val tearDesc = stringResource(R.string.ticket_tear_stub)
                Column(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val t = tear.value
                            translationY = t * t * 900f; rotationZ = t * 24f; alpha = (1f - (t - .6f) / .4f).coerceIn(0f, 1f)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                        }
                        .background(c.bpPaper2)
                        .clickable(role = Role.Button, enabled = !torn) {
                            torn = true
                            Telemetry.event("ticket_tear", mapOf("leg" to if (leg === trip.out) "out" else "ret"))
                            sounds.play("ticket-tear", 0.9f)
                            scope.launch { repeat(HOLES) { haptics.tick(0.35f + 0.3f * (it % 3) / 2f); delay(32) } }
                            scope.launch {
                                tear.animateTo(1f, tween(1100))
                                delay(600)
                                tear.snapTo(0f); torn = false
                                haptics.click(0.5f); sounds.play("ticket-land", 0.6f)
                            }
                        }
                        .semantics { contentDescription = tearDesc }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Label(stringResource(R.string.app_trip_more))
                    Text(daysLeft.toString(), style = Ski.type.title.copy(fontSize = 60.sp, lineHeight = 52.sp), color = c.bpAccent)
                    Text(pluralStringResource(R.plurals.app_trip_days_to, daysLeft.toInt().coerceAtLeast(0)), style = Ski.type.label.copy(fontSize = 11.sp), color = c.bpMuted, textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(1f))
                    Text("$fromCode › $toCode", style = Ski.type.number.copy(fontSize = 20.sp, textDirection = TextDirection.Ltr), color = c.bpInk)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val hint = (if (trip.back != null) stringResource(R.string.ticket_hint_swap) else "") + stringResource(R.string.ticket_hint_tear)
            Text(hint, Modifier.weight(1f), style = Ski.type.small.copy(fontSize = 12.sp), color = c.muted)
            Text(stringResource(R.string.app_trip_edit), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onEdit).padding(horizontal = 8.dp, vertical = 12.dp),
                style = Ski.type.label.copy(fontSize = 13.5.sp), color = c.accent)
        }
    }
}

@Composable
private fun Grid(vararg cells: Pair<String, Pair<String, Boolean>>) {
    val c = Ski.colors
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.forEach { (label, v) -> Column(Modifier.weight(1f)) { Label(label); Value(v.first, v.second) } }
        }
    }
}

/** No trip yet (H1): the same pass, blank, with "add my flight" on it. */
@Composable
fun EmptyPass(bg: Color, onAdd: () -> Unit) {
    val c = Ski.colors
    val faint = Color(0xFFC3CEDA)
    Column {
        Box(Modifier.fillMaxWidth().height(216.dp)) {
            Row(Modifier.fillMaxSize().shadow(10.dp, RectangleShape, clip = false)) {
                Column(Modifier.weight(1f).fillMaxHeight().background(c.bpPaper)) {
                    Row(Modifier.fillMaxWidth().background(c.bpStrip).padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.app_trip_title), style = Ski.type.label.copy(fontSize = 12.sp), color = c.bpOnStrip)
                        Text(stringResource(R.string.app_trip_not_set), style = Ski.type.label.copy(fontSize = 12.sp), color = c.bpOnStrip)
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column { Label(stringResource(R.string.ticket_from)); Text("???", style = Ski.type.title.copy(fontSize = 50.sp), color = faint) }
                        Plane(faint)
                        Column(horizontalAlignment = Alignment.End) { Label(stringResource(R.string.ticket_to)); Text("???", style = Ski.type.title.copy(fontSize = 50.sp), color = faint) }
                    }
                }
                Perforation(bg)
                Column(Modifier.width(100.dp).fillMaxHeight().background(c.bpPaper2), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Label(stringResource(R.string.app_trip_more))
                    Text("?", style = Ski.type.title.copy(fontSize = 60.sp), color = faint)
                    Text(pluralStringResource(R.plurals.app_trip_days_to, 5), style = Ski.type.label.copy(fontSize = 11.sp), color = c.bpMuted)
                }
            }
            Box(Modifier.fillMaxSize().padding(top = 64.dp, end = 100.dp), contentAlignment = Alignment.Center) {
                Text(
                    "+  " + stringResource(R.string.app_trip_add),
                    Modifier.shadow(6.dp, RectangleShape).background(c.glacier).clickable(role = Role.Button, onClick = onAdd)
                        .heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 14.dp),
                    style = Ski.type.bodyBold.copy(fontSize = 16.5.sp), color = Color.White,
                )
            }
        }
        Text(stringResource(R.string.app_trip_local_note), Modifier.padding(top = 10.dp), style = Ski.type.small, color = c.muted)
    }
}

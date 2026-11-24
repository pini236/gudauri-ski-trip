package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.meet.MeetPlan
import io.github.pini236.skiapp.meet.Station
import io.github.pini236.skiapp.qa.Qa
import kotlin.math.hypot

/** K06: which top station a tap is on (44 points to touch, before the lift's line), if any run goes down from it. */
internal object StationTap {
    fun at(view: LiveView, plan: MeetPlan, runs: List<Pair<ShelfRun, List<String>>>, px: Float, py: Float, density: Float): Station? =
        plan.stations.asSequence().filter { s -> s.topOf.isNotEmpty() && FromStation.runs(s.topOf, runs).isNotEmpty() }
            .mapNotNull { s -> view.toScreen(s.x, s.y)?.let { p -> s to hypot(p[0] - px, p[1] - py) } }
            .filter { it.second <= 22f * density }.minByOrNull { it.second }?.first
}

/**
 * K06's low sheet: "from the top of Goodaura", its height and how many runs go down, then a row for each run, the
 * longest first (up to five, then it scrolls): the sign, the name, and its length and the way back at the end.
 */
@Composable
internal fun FromStationSheet(st: Station, runs: List<ShelfRun>, scene: MapScene, night: Boolean, haptics: Haptics, onPick: (String) -> Unit, onClose: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    val km = remember { java.text.NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 } }
    androidx.activity.compose.BackHandler { onClose() }
    Box(Modifier.pullSheet(onDown = onClose)) {
        LiveSheet(k) {
            val first = st.topOf.firstOrNull() ?: st.name
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.app_station_title, "⁦$first⁩"), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
                val h = st.h ?: scene.terrain.elev(st.x, st.y).toInt()
                Text(pluralStringResource(R.plurals.app_station_sub, runs.size, nf.format(h), runs.size.toString()), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
            }
            Column(Modifier.heightIn(max = (46 * 5 + 8).dp).verticalScroll(rememberScrollState())) {
                for (r in runs) {
                    val (len, isKm) = Shelf.length(r.len)
                    val lenShown = (if (isKm) km.format(r.len / 1000.0) + " " + stringResource(R.string.app_shelf_km_cap) else stringResource(R.string.common_unit_m, nf.format(len.toInt())))
                    val end = lenShown + " · " + (if (r.kind == "beginner-area") stringResource(R.string.app_shelf_beginner) else shelfBack(r))
                    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "${r.title}, $end" }
                        .clickable { haptics.tick(.4f); onPick(r.key) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(width = 6.dp, height = 22.dp).clip(RoundedCornerShape(3.dp)).background(plate(r.color)))
                        Text("⁦${r.title}⁩", Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(end, Modifier.width(190.dp), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(k.line))
                }
            }
        }
    }
}

/** The ring around the station on the mountain (the handoff's section 3), following the view. */
@Composable
internal fun StationRing(view: LiveView, st: Station, night: Boolean) {
    val k = remember(night) { SheetInk(night) }
    var at by remember(st.id) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(st.id) { while (true) { withFrameNanos { }; at = view.toScreen(st.x, st.y)?.let { Offset(it[0], it[1]) } } }
    Canvas(Modifier.fillMaxSize()) {
        val p = at ?: return@Canvas
        drawCircle(k.halo, 13.dp.toPx(), p, style = Stroke(5.dp.toPx()))
        drawCircle(k.ink, 13.dp.toPx(), p, style = Stroke(2.dp.toPx()))
    }
}

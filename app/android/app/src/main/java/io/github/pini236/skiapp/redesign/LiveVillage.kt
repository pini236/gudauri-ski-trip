package io.github.pini236.skiapp.redesign

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.Around
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.places.LocalPlaces
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The village's pins and tags (village.json's `stations` and `pins`), in the picture's points. */
/** [pose]: the live model's view that sits on the picture (`live_pose`: tx, ty, tz, dist, yaw and pitch in degrees), if any. */
internal class VillageMarks(val stations: List<Mark>, val pins: List<Mark>, val pose: FloatArray? = null) {
    class Mark(val kind: String, val lift: String?, val x: Float, val y: Float, val dx: Float, val dy: Float, val arrow: Boolean)

    companion object {
        fun parse(json: String): VillageMarks {
            val v = org.json.JSONObject(json).getJSONObject("village")
            val st = v.getJSONArray("stations").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                .filter { it.getString("end") == "bottom" && !it.isNull("tag") }
                .map { o -> o.getJSONObject("tag").let { t -> Mark("station", o.getString("lift"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), t.getDouble("dx").toFloat(), t.getDouble("dy").toFloat(), t.getBoolean("arrow")) } }
            // the pins the product shows (the board's examples, "tickets2", "food_board" and "meet_soliko", are notes only)
            val pins = v.getJSONArray("pins").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                .filter { it.getString("kind") in setOf("tickets", "food", "parking") }
                .map { o -> Mark(o.getString("kind"), null, o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), 0f, 0f, false) }
            val pose = v.optJSONObject("live_pose")?.let { o -> listOf("tx", "ty", "tz", "dist", "yaw_deg", "pitch_deg").map { o.getDouble(it).toFloat() }.toFloatArray() }
            return VillageMarks(st, pins, pose)
        }

        /**
         * A pin that falls on another (centres under 30 points apart) moves aside 34 (the handoff's section 5): the lower
         * one moves. In the pins' own order.
         */
        fun spread(pins: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
            val out = arrayOfNulls<Pair<Float, Float>>(pins.size)
            val placed = ArrayList<Pair<Float, Float>>()
            for (i in pins.indices.sortedBy { pins[it].second }) {
                val (x, y) = pins[i]
                var nx = x
                while (placed.any { (ox, oy) -> kotlin.math.hypot(ox - nx, oy - y) < 30f }) nx += 34f
                (nx to y).also { out[i] = it; placed += it }
            }
            return out.map { it!! }
        }
    }
}

/**
 * K17, the village: a painted picture by the mountain's light with its live layer (roads, buildings and their shadows,
 * runs and lifts), the lifts' tags and the pins over it, "back to the mountain" by "me", and its sheet. Read only on entry
 * (the architect's conditions): at most two pictures decoded at a time.
 */
@Composable
internal fun BoxScope.Village(
    scene: MapScene, data: PeakData, mode: DayNight.Mode, now: Long, night: Boolean, around: Around?, haptics: Haptics,
    meetNow: Pair<String, String>?, onLift: (Lift) -> Unit, onTrip: () -> Unit, onSkipass: () -> Unit, onFood: (() -> Unit)?, onPeople: (List<io.github.pini236.skiapp.group.GroupSummary>?) -> Unit,
    onBack: () -> Unit,
    fly: (FloatArray) -> Boolean = { false },
    meetLifts: List<String> = emptyList(),
    onMeet: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val k = remember(night) { SheetInk(night) }
    val loaded by produceState<Pair<PeakData.View, VillageMarks>?>(null) {
        value = withContext(Dispatchers.IO) {
            val json = context.assets.open("redesign/village/village.json").bufferedReader().use { it.readText() }
            PeakData.village(json) to VillageMarks.parse(json)
        }
    }
    val light = PeakLight.pick(data.lights, now, mode)
    val pic by produceState<ImageBitmap?>(null, loaded, light.name) {
        val v = loaded?.first ?: return@produceState
        val f = v.images[light.name] ?: return@produceState
        value = withContext(Dispatchers.IO) { context.assets.open("redesign/village/$f").use { BitmapFactory.decodeStream(it) }.also { it.prepareToDraw() }.asImageBitmap() }
    }
    val enter = remember { Animatable(0f) }
    // the entry (the handoff's "the move"): the camera flies to the picture's view in 600 ms and the picture comes up over it
    // from 350 to 600; from the Kobi side (the village unseen) a swap by opacity of 300, and with reduced motion of 200
    LaunchedEffect(loaded) {
        val l = loaded ?: return@LaunchedEffect
        val flies = l.second.pose?.let(fly) ?: false
        val reduced = io.github.pini236.skiapp.ui.Motion.reduced(context)
        enter.animateTo(1f, if (flies) tween(250, delayMillis = 350) else tween(if (reduced) 200 else 300))
        Qa.log("redesign village open ${light.name}${if (flies) " flown" else ""}")
    }
    androidx.activity.compose.BackHandler { onBack() }
    val (view, marks) = loaded ?: return
    val measurer = rememberTextMeasurer()
    val unitM = stringResource(R.string.common_unit_m, "\u0000")
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    BoxWithConstraints(Modifier.fillMaxSize().alpha(enter.value)) {
        val dens = LocalDensity.current
        val wPx = with(dens) { maxWidth.toPx() }
        val kk = wPx / view.w
        val hPx = view.h * kk
        val pl = Place(kk, 0f, 0f)
        Box(Modifier.fillMaxSize().background(if (night) Color(0xFF0B1424) else Color(0xFFE9EEF3)))
        // the pins: tickets, food (its count from places.json now), parking (no tap); a meeting today at a station here
        val places = LocalPlaces.current
        val food = remember(places, scene) {
            val g = scene.runs.lifts.firstOrNull { it.name == "Goodaura" }
            g?.let { l -> places?.stationOf(l.id, true)?.let { st -> places.near(st).count { it.place.kind == "food" || it.place.kind == "cafe" } } } ?: 0
        }
        val shown = marks.pins.filter { it.kind != "food" || (places != null && food > 0) }
        val at = VillageMarks.spread(shown.map { it.x to it.y })
        val pinText = shown.map { m -> when (m.kind) {
            "tickets" -> stringResource(R.string.app_vill_pin_tickets)
            "food" -> stringResource(R.string.app_vill_pin_food, food.toString())
            else -> stringResource(R.string.app_vill_pin_parking)
        } }
        // never one tag over another (the review of 25.11: large text and night): the pins keep their places, a lift's tag
        // that would touch one moves a step up or down, and the picture's own names leave out what these tags hold
        val screenW = with(dens) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val tagStyle = TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
        val px = { d: Float -> with(dens) { d.dp.toPx() } }
        val pinBoxes = pinText.zip(at).map { (t, p) ->
            val w = measurer.measure(t, tagStyle).size.width + px(18f)
            val x = p.first * kk; val y = p.second * kk - px(34f)
            val l = x - w / 2f + PinRules.slide(x, w, screenW, px(8f))
            androidx.compose.ui.geometry.Rect(l, y, l + w, y + px(24f))
        }
        val liveStations = marks.stations.filter { m -> scene.runs.lifts.any { it.name == m.lift } }
        val stLabel = { m: VillageMarks.Mark -> "⁦${m.lift}⁩" + if (m.arrow) " ↑" else "" }
        val stBoxes0 = liveStations.map { m ->
            val w = measurer.measure(stLabel(m), tagStyle).size.width + px(16f)
            val x = (m.x + m.dx) * kk; val y = (m.y + m.dy) * kk
            androidx.compose.ui.geometry.Rect(x, y, x + w, y + px(22f))
        }
        val stDy = TagLayout.place(pinBoxes, stBoxes0, px(26f), 2, px(3f))
        val reserved = pinBoxes + stBoxes0.mapIndexed { i, r -> r.translate(0f, stDy[i]) }
        Canvas(Modifier.fillMaxWidth().height(with(dens) { hPx.toDp() })) {
            pic?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), hPx.toInt())) }
            layer(data, view, pl, 1f, light, night, 1f, null, 0f, 0f, 0f, near = true, closeUp = true, measurer, unitM, nf,
                reserved = reserved.map { it.inflate(px(2f)) })
        }
        // the lifts' tags: "Goodaura ↑", "Soliko ↑", "Zuma"; a tap opens the lift's panel
        for ((i, m) in liveStations.withIndex()) {
            val lift = scene.runs.lifts.first { it.name == m.lift }
            val dy = stDy[i]
            Row(Modifier.offset { IntOffset(((m.x + m.dx) * kk).toInt(), ((m.y + m.dy) * kk + dy).toInt()) }.height(22.dp).clip(RoundedCornerShape(11.dp))
                .background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, RoundedCornerShape(11.dp)).semantics { role = Role.Button }
                .clickable { haptics.tick(.3f); onLift(lift) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stLabel(m), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = k.ink, maxLines = 1)
            }
        }
        for ((i, m) in shown.withIndex()) {
            val p = at[i]; val text = pinText[i]
            val tap: (() -> Unit)? = when (m.kind) { "tickets" -> onSkipass; "food" -> onFood; else -> null }
            Pin(text, Offset(p.first * kk, p.second * kk), k, night, tap?.let { t -> { haptics.tick(.3f); t() } })
        }
        // a meeting today at a station in the picture (the mountain's flag rule, Q05): on its station, in the accent
        if (meetNow != null) marks.stations.firstOrNull { it.lift in meetLifts }?.let { m ->
            Pin(stringResource(R.string.app_vill_pin_meet, meetNow.first), Offset(m.x * kk, m.y * kk), k, night, onMeet?.let { t -> { haptics.tick(.3f); t() } }, bg = k.acc)
        }
        // back to the mountain, by "me" (the pill's place)
        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 8.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(32.dp).shadow(8.dp, RoundedCornerShape(16.dp), clip = false).clip(RoundedCornerShape(16.dp)).background(k.surf.copy(alpha = 1f))
                .border(1.dp, k.line, RoundedCornerShape(16.dp)).semantics { role = Role.Button }.clickable { haptics.tick(.3f); onBack() }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("↑ " + stringResource(R.string.app_vill_back), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = k.ink)
            }
        }
        VillageSheet(scene, around, k, haptics, food, places != null, meetNow, onTrip, onSkipass, onFood, onPeople, onBack)
    }
}

@Composable
private fun Pin(text: String, at: Offset, k: SheetInk, night: Boolean, onTap: (() -> Unit)?, bg: Color = k.ink) {
    val fg = if (night) Color(0xFF0F1828) else Color.White
    var w by remember { mutableStateOf(0) }
    val dens = LocalDensity.current
    // the tag stays whole on the screen (the review of 24.11, ק3: "food and drink" was 18 dp wide at the edge); the stem
    // keeps its place, so the tag slides along it
    val screenW = with(dens) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val edge = with(dens) { 8.dp.toPx() }
    val shift = PinRules.slide(at.x, w.toFloat(), screenW, edge)
    Column(Modifier.offset { IntOffset((at.x - w / 2f).toInt(), (at.y - with(dens) { 34.dp.toPx() }).toInt()) }
        .onSizeChanged { w = it.width },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.offset { IntOffset(shift.toInt(), 0) }.let { if (onTap != null) it.semantics { role = Role.Button }.clickable(onClick = onTap) else it }
            .height(24.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
            Text(text, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = fg, maxLines = 1)
        }
        Box(Modifier.size(2.dp, 10.dp).background(bg))
    }
}

/** "10–15.1" (one month) or "28.1–2.2": the trip's dates as the board writes them. */
internal fun tripSpan(a: java.time.LocalDate, b: java.time.LocalDate?): String = when {
    b == null || b == a -> "${a.dayOfMonth}.${a.monthValue}"
    a.monthValue == b.monthValue && a.year == b.year -> "${a.dayOfMonth}–${b.dayOfMonth}.${b.monthValue}"
    else -> "${a.dayOfMonth}.${a.monthValue}–${b.dayOfMonth}.${b.monthValue}"
}

/**
 * The village's sheet (section 5): "the village", its place and height, then the trip, the ski pass, food and drink, the
 * people and the evening in the hut, each with what it says now; and the credit. A pull down goes back to the mountain.
 */
@Composable
private fun BoxScope.VillageSheet(
    scene: MapScene, around: Around?, k: SheetInk, haptics: Haptics, food: Int, hasPlaces: Boolean, meetNow: Pair<String, String>?,
    onTrip: () -> Unit, onSkipass: () -> Unit, onFood: (() -> Unit)?, onPeople: (List<io.github.pini236.skiapp.group.GroupSummary>?) -> Unit, onBack: () -> Unit,
) {
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    val goodaura = scene.runs.lifts.firstOrNull { it.name == "Goodaura" }
    val h = goodaura?.let { l -> LiveLifts.ends(l, scene.terrain::elev)?.let { e -> scene.terrain.elev(e[0], e[1]).toInt() } }
    val groups by produceState<List<io.github.pini236.skiapp.group.GroupSummary>?>(null, around?.people) {
        value = runCatching { around?.people?.api?.myGroups() }.getOrNull()
    }
    val top = sheetShape()
    // two heights (section "the sheet"): low (296, four rows) and full (to 120 from the top, scrolling inside); a pull up
    // opens it full, a pull down from full goes back to low, and from low back to the mountain
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(RedesignQa.seq) { when (RedesignQa.cmd) { "village:full" -> full = true; "village:low" -> full = false } }
    var acted by remember { mutableStateOf(false) }
    Column(Modifier.align(Alignment.BottomCenter).sheetWidth()
        .then(if (full) Modifier.padding(top = 120.dp).fillMaxHeight() else Modifier.heightIn(max = 296.dp + 34.dp))
        .animateContentSize(tween(220))
        .pointerInput(Unit) {
            detectVerticalDragGestures(onDragStart = { acted = false }) { _, dy ->
                if (acted) return@detectVerticalDragGestures
                when {
                    dy < -18f && !full -> { acted = true; full = true; Qa.log("redesign village sheet full") }
                    dy > 18f && full -> { acted = true; full = false; Qa.log("redesign village sheet low") }
                    dy > 18f -> { acted = true; onBack() }
                }
            }
        }
        .shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
        .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
        Text(stringResource(R.string.app_dock_village), Modifier.padding(top = 8.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
        if (h != null) Text(stringResource(R.string.app_vill_sub, "New Gudauri", nf.format(h)), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
        Column(Modifier.padding(top = 6.dp).verticalScroll(rememberScrollState())) {
            val t = around?.trip
            val tripEnd = when {
                t == null -> stringResource(R.string.trip_when_fly)
                else -> when (val st = t.stage(java.time.LocalDateTime.now(io.github.pini236.skiapp.meet.Meet.GUDAURI))) {
                    is io.github.pini236.skiapp.trip.Trip.Stage.SkiDay -> stringResource(R.string.trip_ski_day_of, st.n.toString(), st.of.toString())
                    else -> t.skiDayCount().let { n -> pluralStringResource(R.plurals.app_vill_trip_dates, n, tripSpan(t.out.date, t.ret?.date), n.toString()) }
                }
            }
            VRow(stringResource(R.string.app_home_trip), tripEnd, k) { haptics.tick(.3f); onTrip() }
            val pr = around?.prices?.takeIf { it.valid(around.today) }
            val tripPrice = around?.let { tripSkipass(it, t) }
            val passEnd = when {
                pr == null -> stringResource(R.string.weather_none)
                tripPrice != null && t != null -> t.skiDayCount().let { n -> pluralStringResource(R.plurals.app_vill_pass_trip, n, n.toString(), "${nf.format(tripPrice)} ₾") }
                else -> pr.daily(false)?.let { stringResource(R.string.app_vill_pass_day, "${nf.format(it)} ₾") } ?: stringResource(R.string.weather_none)
            }
            VRow(stringResource(R.string.app_skipass_title), passEnd, k) { haptics.tick(.3f); onSkipass() }
            if (hasPlaces) VRow(stringResource(R.string.app_vill_food),
                if (food > 0) pluralStringResource(R.plurals.app_vill_food_n, food, food.toString()) else stringResource(R.string.places_none), k,
                onFood?.takeIf { food > 0 }?.let { f -> { haptics.tick(.3f); f() } })
            val g = groups
            val peopleEnd = when {
                meetNow != null -> stringResource(R.string.app_vill_grp_meet, meetNow.first, meetNow.second)
                g.isNullOrEmpty() -> stringResource(R.string.app_vill_grp_none)
                g.size == 1 -> g[0].name
                else -> pluralStringResource(R.plurals.app_vill_grp_n, g.size, g.size.toString())
            }
            VRow(stringResource(R.string.app_grp_title), peopleEnd, k) { haptics.tick(.3f); onPeople(g) }
            around?.onGames?.let { go -> VRow(stringResource(R.string.app_cabin_title), pluralStringResource(R.plurals.app_vill_cabin_n, around.games, around.games.toString()), k) { haptics.tick(.3f); go() } }
            Text(stringResource(R.string.app_vill_credit), Modifier.padding(top = 10.dp), fontFamily = Gudi, fontSize = 12.sp, color = k.ink2)
        }
    }
}

@Composable
private fun VRow(title: String, end: String, k: SheetInk, onClick: (() -> Unit)?) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).let { if (onClick != null) it.semantics(mergeDescendants = true) { role = Role.Button }.clickable(onClick = onClick) else it },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(end, Modifier.weight(1.4f, fill = false), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
    }
}


/**
 * The village's tags laid out so none sits on another: the [fixed] boxes stay; each of the [movable] boxes, in order, takes
 * the first of 0, -step, +step, -2·step, +2·step… (up to [steps] each way) that touches neither a fixed box nor a movable
 * one already placed (with [gap] between); if none is free it stays where it was. Returns each movable box's move down.
 */
internal object TagLayout {
    fun place(fixed: List<androidx.compose.ui.geometry.Rect>, movable: List<androidx.compose.ui.geometry.Rect>, step: Float, steps: Int, gap: Float): FloatArray {
        val taken = fixed.map { it.inflate(gap) }.toMutableList()
        val out = FloatArray(movable.size)
        for ((i, r) in movable.withIndex()) {
            val tries = listOf(0f) + (1..steps).flatMap { listOf(-it * step, it * step) }
            val dy = tries.firstOrNull { d -> taken.none { it.overlaps(r.translate(0f, d)) } } ?: 0f
            out[i] = dy; taken += r.translate(0f, dy).inflate(gap)
        }
        return out
    }
}

/** How far a village tag of width [w] slides so it stays inside [edge] of a screen [screenW] wide, centred on [x]. */
internal object PinRules {
    fun slide(x: Float, w: Float, screenW: Float, edge: Float): Float {
        if (w <= 0f || w + 2 * edge > screenW) return 0f
        val left = x - w / 2f; val right = x + w / 2f
        return when { left < edge -> edge - left; right > screenW - edge -> (screenW - edge) - right; else -> 0f }
    }
}

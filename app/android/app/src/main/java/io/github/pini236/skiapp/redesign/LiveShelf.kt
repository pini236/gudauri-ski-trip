package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.positionInParent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.RunFacts
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.qa.Qa
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** The shelf's runs from the data: the named runs, their numbers as the run's sheet has them (RunFacts). */
internal object ShelfData {
    fun runs(scene: MapScene): List<ShelfRun> = scene.runs.pistes.filter { it.named }.map { p ->
        val f = RunFacts.of(scene.terrain, p)
        val back = p.toLifts.firstOrNull()
        ShelfRun(p.key, p.title, p.color, p.kind, p.len, f?.drop ?: 0, f?.let { RunFacts.deg(it.maxG) } ?: 0,
            p.fromLifts.firstOrNull(), back, back?.let { b -> scene.runs.lifts.firstOrNull { it.name == b }?.dur }, f?.top ?: 0)
    }

    /** Each named run with all its fromLifts (K06 gathers the runs of a station's lifts). */
    fun withLifts(scene: MapScene): List<Pair<ShelfRun, List<String>>> {
        val by = runs(scene).associateBy { it.key }
        return scene.runs.pistes.filter { it.named }.mapNotNull { p -> by[p.key]?.let { it to p.fromLifts } }
    }

    /** The height of each lift's bottom station, from the height model (the order of "by lift", from the village up). */
    fun bottoms(scene: MapScene): Map<String, Float> = scene.runs.lifts.mapNotNull { l ->
        LiveLifts.ends(l, scene.terrain::elev)?.let { e -> l.name to scene.terrain.elev(e[0], e[1]) }
    }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.min() }

    /** A run's line turned so its start is up and its end down, in a box of [w] by [h] with a margin of [m]. */
    fun shape(scene: MapScene, key: String, w: Float, h: Float, m: Float): List<FloatArray> {
        val p = scene.runs.pistes.firstOrNull { it.key == key } ?: return emptyList()
        val lines = scene.topDown(p).map { l -> FloatArray(l.size / 3 * 2) { i -> l[(i / 2) * 3 + (i % 2) * 2] } }
        if (lines.isEmpty()) return emptyList()
        val a = lines.first(); val z = lines.last()
        val dx = z[z.size - 2] - a[0]; val dy = z[z.size - 1] - a[1]
        // turn the start-to-end direction to point down the box
        val ang = (Math.PI / 2 - atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val c = cos(ang); val s = sin(ang)
        val turned = lines.map { l -> FloatArray(l.size) { i -> val x = l[i - i % 2]; val y = l[i - i % 2 + 1]; if (i % 2 == 0) x * c - y * s else x * s + y * c } }
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (l in turned) for (i in 0 until l.size / 2) { x0 = minOf(x0, l[i * 2]); x1 = maxOf(x1, l[i * 2]); y0 = minOf(y0, l[i * 2 + 1]); y1 = maxOf(y1, l[i * 2 + 1]) }
        val k = minOf((w - 2 * m) / (x1 - x0).coerceAtLeast(1f), (h - 2 * m) / (y1 - y0).coerceAtLeast(1f))
        val ox = (w - (x1 - x0) * k) / 2f; val oy = (h - (y1 - y0) * k) / 2f
        return turned.map { l -> FloatArray(l.size) { i -> if (i % 2 == 0) ox + (l[i] - x0) * k else oy + (l[i] - y0) * k } }
    }
}

/** The run chosen from the shelf last, since the app opened (the card the shelf opens on next time). */
internal object ShelfMemory { @Volatile var last: String? = null }

internal object ShelfPrefs {
    private fun p(c: android.content.Context) = c.getSharedPreferences("redesign", android.content.Context.MODE_PRIVATE)
    fun sort(c: android.content.Context) = ShelfSort.of(p(c).getString("shelf_sort", null))
    fun setSort(c: android.content.Context, s: ShelfSort) = p(c).edit().putString("shelf_sort", s.id).apply()
}

@Composable
private fun colourWord(c: String) = stringResource(when (c) {
    "green" -> R.string.common_color_green; "blue" -> R.string.common_color_blue; "red" -> R.string.common_color_red; else -> R.string.common_color_black })

@Composable
private fun levelWord(c: String) = stringResource(when (c) {
    "red" -> R.string.map_difficulty_intermediate; "black" -> R.string.map_difficulty_hard; else -> R.string.map_difficulty_easy })

/** "Red · intermediate"; a beginner area "green · beginner area"; a ski way only "ski way" (section 2). */
@Composable
internal fun shelfKind(r: ShelfRun): String = when (r.kind) {
    "ski-way" -> stringResource(R.string.map_legend_way)
    "beginner-area" -> colourWord(r.color) + " · " + stringResource(R.string.app_shelf_beginner)
    else -> colourWord(r.color) + " · " + levelWord(r.color)
}

@Composable
internal fun shelfBack(r: ShelfRun): String = when {
    r.back == null -> stringResource(R.string.app_shelf_back_none)
    r.backMin != null -> stringResource(R.string.app_shelf_back, r.back, r.backMin)
    else -> stringResource(R.string.app_shelf_back_nomin, r.back)
}

/**
 * The bottom bar (design/handoff/2026-11-27-redesign-run-button-v4, "The bar"): 10 from the sides, 12 from the bottom,
 * 64 high, radius 32, an opaque surface on Android; from the start: "the menu", the "מסלולים" button (A, the window, in the
 * middle), the record button. The menu and record are 48 high. [runsHidden]: the opening's surface is drawn in the button's place.
 */
@Composable
internal fun BoxScope.Dock(night: Boolean, haptics: Haptics, onVillage: (() -> Unit)?, onFind: () -> Unit, onRecord: (() -> Unit)?,
                           runs: RunsFace, flight: RunsFlight, runsHidden: Boolean) {
    val k = remember(night) { SheetInk(night) }
    // on a tablet 420 wide at the start side, the map's buttons above it (part 8)
    val tab = isTabletUi()
    Row((if (tab) Modifier.align(Alignment.BottomStart).width(TabletUi.DOCK.dp) else Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        .navigationBarsPadding().padding(start = if (tab) 12.dp else 10.dp, end = 10.dp, bottom = 12.dp).height(64.dp)
        .shadow(18.dp, RoundedCornerShape(32.dp), clip = false).clip(RoundedCornerShape(32.dp)).background(k.surf.copy(alpha = 1f))
        .border(1.dp, k.line, RoundedCornerShape(32.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onVillage != null) {
        val menu = stringResource(R.string.app_dock_menu)
        Row(Modifier.height(48.dp).hintTarget("village").clip(RoundedCornerShape(24.dp)).background(k.ink).semantics { contentDescription = menu; role = Role.Button }
            .clickable { haptics.tick(.3f); onVillage() }.padding(start = 14.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val ic = if (night) Color(0xFF0F1828) else Color.White
            Canvas(Modifier.size(20.dp)) { // four equal rounded squares, gap a third of a square, centred
                val sq = 8.dp.toPx(); val gap = sq / 3f; val o = (size.width - 2 * sq - gap) / 2f
                for (i in 0..1) for (j in 0..1)
                    drawRoundRect(ic, androidx.compose.ui.geometry.Offset(o + i * (sq + gap), o + j * (sq + gap)),
                        androidx.compose.ui.geometry.Size(sq, sq), androidx.compose.ui.geometry.CornerRadius(2.2.dp.toPx()))
            }
            Text(menu, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ic, maxLines = 1)
        }
        }
        RunsButton(k, runs, runsHidden, flight, Modifier.weight(1f)) { haptics.tick(.3f); onFind() }
        if (onRecord != null) RecordButton(k, haptics, onRecord)
    }
}

/**
 * The record button (RX3, handoff 2026-11-25-redesign-record-button): inside the bar, 44 high, like a camera's. Before a
 * recording a red disc in a ring, nothing inside; while recording a red square in the ring, and the time beside it (from
 * the first reading of the location; none before it) in a pill. A touch opens the sheet, or the live screen while
 * recording; it never stops the recording by itself.
 */
@Composable
private fun RecordButton(k: SheetInk, haptics: Haptics, onRecord: () -> Unit) {
    val recording = io.github.pini236.skiapp.dayrec.DayRec.recording
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording) { while (recording) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val first = io.github.pini236.skiapp.dayrec.DayRec.since
    val time = if (recording && first > 0L) io.github.pini236.skiapp.dayrec.ui.LiveNumbers.elapsed(SearchRules.sinceFix(now, first)) else null
    val red = Color(0xFFE5352B)
    val label = if (recording) stringResource(R.string.app_sr_rec_live, time ?: "0:00") else stringResource(R.string.app_rec_sign)
    val tint = if (k.night) Color(0x29FF7468) else Color(0x1FE5352B)
    val hold = rememberNameHold()
    val reduced = io.github.pini236.skiapp.ui.Motion.reduced(androidx.compose.ui.platform.LocalContext.current)
    val square by androidx.compose.animation.core.animateFloatAsState(if (recording) 1f else 0f, if (reduced) tween(0) else tween(200), label = "rec-square")
    Row(Modifier.height(48.dp).hintTarget("rec").then(if (reduced) Modifier else Modifier.animateContentSize(tween(200))).clip(RoundedCornerShape(24.dp))
        .background(if (recording) tint else Color.Transparent)
        .semantics { contentDescription = label; role = Role.Button }.nameHold(hold, k) { haptics.tick(.4f); onRecord() },
        verticalAlignment = Alignment.CenterVertically) {
        NamePill(hold, stringResource(R.string.app_rec_sign), k, above = true)
        Canvas(Modifier.size(48.dp)) {
            val c = center
            drawCircle(k.ink, 21.75.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
            // the disc (30) turns into the square (16, corners 3)
            val side = (30f + (16f - 30f) * square).dp.toPx()
            val r = (15f + (3f - 15f) * square).dp.toPx()
            drawRoundRect(red, androidx.compose.ui.geometry.Offset(c.x - side / 2, c.y - side / 2), androidx.compose.ui.geometry.Size(side, side),
                androidx.compose.ui.geometry.CornerRadius(r))
        }
        if (time != null) Text("\u2066$time\u2069", Modifier.padding(start = 0.dp, end = 12.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = k.ink, maxLines = 1, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.house(c: Color) {
    val w = size.width; val h = size.height; val st = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply { moveTo(w * .12f, h * .48f); lineTo(w * .5f, h * .14f); lineTo(w * .88f, h * .48f) }, c, style = st)
    drawPath(Path().apply { moveTo(w * .24f, h * .4f); lineTo(w * .24f, h * .86f); lineTo(w * .76f, h * .86f); lineTo(w * .76f, h * .4f) }, c, style = st)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.glass(c: Color) {
    val w = size.width; val st = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(c, w * .3f, Offset(w * .42f, w * .42f), style = st)
    drawLine(c, Offset(w * .64f, w * .64f), Offset(w * .9f, w * .9f), st.width, StrokeCap.Round)
}

/** A chip (section 1): 36 high (44 to touch), radius 18; on: ink behind, the background's colour in bold. */
@Composable
internal fun Chip(text: String, on: Boolean, k: SheetInk, enabled: Boolean = true, a11y: String? = null, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).alpha(if (enabled) 1f else .4f).semantics { role = Role.Tab; selected = on; a11y?.let { contentDescription = it } }
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) k.ink else k.surf.copy(alpha = 1f))
            .let { if (on) it else it.border(1.dp, k.line, RoundedCornerShape(18.dp)) }.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(text, fontFamily = Gudi, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp,
                color = if (on) (if (k.night) Color(0xFF0F1828) else Color.White) else k.ink, maxLines = 1)
        }
    }
}

/** What the mountain shows for the shelf's chip and middle card (the shelf reports it, the mountain paints it). */
internal data class ShelfMode(val sort: ShelfSort, val lit: Set<String>, val lifts: Set<String>, val lens: LensKind?, val ranks: List<String>)

/**
 * K07, the shelf, version 2 (design/handoff/2026-11-27-redesign-shelf-layers, "Version 2"): the strip panel (chip name, group,
 * position, one tick per run), six chips that each order the cards and paint the mountain, and the cards in one row, the middle
 * one lit on the mountain. [runOpen] null when there is no fresh lift report; [snowCm] null when there is no forecast; [openHint]
 * the line a tap on the dim "open" chip shows; [onMode] what to paint; [onPanel] the strip panel's rectangle (the runs button lands there).
 */
@Composable
internal fun BoxScope.Shelf(scene: MapScene, view: Stage, runs: List<ShelfRun>, night: Boolean, haptics: Haptics, open: (List<Pair<String, ShelfGroup>>) -> Int,
                            closed: (String) -> Boolean, onCenter: (ShelfRun) -> Unit, onPick: (String) -> Unit, onClose: () -> Unit,
                            onPanel: (androidx.compose.ui.geometry.Rect) -> Unit = {},
                            runOpen: ((String) -> Boolean?)? = null, snowCm: ((ShelfRun) -> Double?)? = null, openHint: String = "",
                            onMode: (ShelfMode) -> Unit = {}) {
    val context = LocalContext.current
    val k = remember(night) { SheetInk(night) }
    var chosenSort by remember { mutableStateOf(ShelfPrefs.sort(context)) }
    val sun = remember(night, view.lightTime / 600_000) { view.sunShares() }
    val byKey = remember(runs) { runs.associateBy { it.key } }
    val snowOf = remember(snowCm, runs) { snowCm?.let { f -> runs.associate { it.key to f(it) } } }
    val snowAny = snowOf != null && snowOf.values.any { (it ?: 0.0) >= 1.0 }
    // the sun down: "in the sun now" is off; no lift report: "open" is off; both fall back to by lift (the empty answers never grey the mountain)
    val sort = when {
        chosenSort == ShelfSort.SUN && sun == null -> ShelfSort.LIFT
        chosenSort == ShelfSort.OPEN && runOpen == null -> ShelfSort.LIFT
        else -> chosenSort
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    val noSunText = stringResource(R.string.app_lens_no_sun)
    val bottoms = remember(scene) { ShelfData.bottoms(scene) }
    val groups = remember(sort, runs, sun, runOpen, snowOf) {
        Shelf.groups(sort, runs, bottoms::get, sun?.let { m -> { key: String -> m[key] ?: 0f } }, runOpen, snowOf?.let { m -> { key: String -> m[key] ?: 0.0 } }.takeIf { snowAny })
    }
    val flat = remember(groups) { Shelf.flat(groups) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = remember { open(flat) })
    var keep by remember { mutableStateOf<String?>(null) }
    // the opening's table (run button v4): the panel's first row fades 270 to 470 ms, the chips rise 14 from 140, the cards rise 46 from 160,
    // the middle one first and 45 ms apart; reduced motion: everything cross-fades in 150 ms and nothing moves
    val reduced = io.github.pini236.skiapp.ui.Motion.reduced(context)
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(700f, tween(if (reduced) 150 else 700, easing = androidx.compose.animation.core.LinearEasing)) }
    val rise = { delay: Float, dur: Float -> if (reduced) enter.value / 700f else RunsFlight.OPEN.transform(((enter.value - delay) / dur).coerceIn(0f, 1f)) }
    val startMid = remember { list.firstVisibleItemIndex }
    // a new order keeps the middle card the same run (section 2)
    LaunchedEffect(flat) { keep?.let { list.scrollToItem(Shelf.keep(flat, it)) } }
    val mid by remember { derivedStateOf {
        val info = list.layoutInfo; val c = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - c) }?.index ?: list.firstVisibleItemIndex
    } }
    LaunchedEffect(flat) {
        snapshotFlow { mid }.collect { i ->
            val key = flat.getOrNull(i)?.first ?: return@collect
            keep = key; byKey[key]?.let { onCenter(it) }; haptics.tick(.2f); Qa.log("redesign shelf center $key")
        }
    }
    // what the mountain paints: the chip's lens, or the middle card's group (by lift, by colour) or the six longest
    val group = flat.getOrNull(mid)?.second
    val lens = when (sort) { ShelfSort.SNOW -> if (snowAny) LensKind.SNOW else null; else -> sort.lens }
    LaunchedEffect(sort, group, flat, lens) {
        onMode(ShelfMode(sort, Shelf.lit(sort, group, flat), setOfNotNull(group?.lift), lens, Shelf.ranks(sort, flat)))
    }
    LaunchedEffect(Unit) { Qa.log("redesign shelf open ${sort.id} ${flat.size}") }
    LaunchedEffect(notice) { if (notice != null) { delay(2000); notice = null } }
    androidx.activity.compose.BackHandler { onClose() }
    // the emulator's switches (qa.redesign shelf:sort:<id>, shelf:next)
    LaunchedEffect(RedesignQa.seq) {
        val c = RedesignQa.cmd
        if (c.startsWith("shelf:sort:")) { chosenSort = ShelfSort.of(c.removePrefix("shelf:sort:")); Qa.log("redesign shelf sort ${chosenSort.id}") }
        if (c == "shelf:next") list.animateScrollToItem((mid + 1).coerceAtMost(flat.size - 1))
    }
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val tickSpecs = remember(sort, flat, sun, runOpen, snowOf) {
        ShelfAnswers.ticks(sort, flat, byKey, { key -> if (sun == null) null else (sun[key] ?: 0f) }, { key -> runOpen?.invoke(key) }, { key -> snowOf?.get(key) })
    }
    val runColour: (String) -> Color = { key -> byKey[key]?.let { plate(it.color) } ?: Color.Gray }
    val chipName = stringResource(sortLabel(sort))
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 22.dp)) {
        // the strip panel: the chip's name, the group or the count, the position, and the ticks
        val g = group
        val groupTitle: String? = when {
            g == null -> null
            g.lift != null -> pluralStringResource(R.plurals.app_shelf_group_lift, g.keys.size, g.lift, g.keys.size.toString())
            g.color != null -> pluralStringResource(R.plurals.app_shelf_group_color, g.keys.size, stringResource(when (g.color) {
                "green" -> R.string.app_shelf_colors_green; "blue" -> R.string.app_shelf_colors_blue; "red" -> R.string.app_shelf_colors_red; else -> R.string.app_shelf_colors_black }), g.keys.size.toString())
            g.sun == true -> stringResource(R.string.app_shelf_group_sun, g.keys.size.toString())
            g.sun == false -> stringResource(R.string.app_shelf_group_shade, g.keys.size.toString())
            g.open == true -> stringResource(R.string.status_lift_open) + " · " + g.keys.size
            g.open == false -> stringResource(R.string.app_lens_closed_leg) + " · " + g.keys.size
            sort == ShelfSort.LONG || sort == ShelfSort.SNOW -> null
            else -> pluralStringResource(R.plurals.app_shelf_group_rest, g.keys.size, g.keys.size.toString())
        }
        val pos = if (flat.isEmpty()) "" else stringResource(R.string.app_shelf_pos, (mid + 1).toString(), flat.size.toString())
        val noSnow = sort == ShelfSort.SNOW && !snowAny
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().onGloballyPositioned { onPanel(it.boundsInWindow()) }
            .graphicsLayer { alpha = rise(270f, 200f) }.shadow(10.dp, shape, clip = false).clip(shape).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, shape)
            .padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.animation.Crossfade(notice ?: (if (noSnow) stringResource(R.string.app_shelf_snow_none) else chipName + (groupTitle?.let { " · $it" } ?: "")), animationSpec = tween(150), label = "strip-row") { t ->
                    Text(t, Modifier.fillMaxWidth(.78f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(pos, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = k.ink2, maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            val name = flat.getOrNull(mid)?.first?.let { byKey[it]?.title } ?: ""
            RunStrip(tickSpecs, runColour, mid, k, stringResource(R.string.app_shelf_tick_a11y, name, (mid + 1).toString(), flat.size.toString())) { i, tap ->
                if (i != mid && i in flat.indices) { haptics.tick(.2f); scope.launch { if (tap) list.animateScrollToItem(i) else list.scrollToItem(i) } }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        // the chips: the row scrolls and the lit chip is always fully in view
        val chipScroll = rememberScrollState()
        val chipBox = remember { mutableMapOf<ShelfSort, Pair<Int, Int>>() }
        LaunchedEffect(sort) {
            chipBox[sort]?.let { (l, w) ->
                val view0 = with(density) { screenW.roundToPx() }
                if (l < chipScroll.value) chipScroll.animateScrollTo(l - 12) else if (l + w > chipScroll.value + view0) chipScroll.animateScrollTo(l + w - view0 + 12)
            }
        }
        Row(Modifier.fillMaxWidth().graphicsLayer { val r = rise(140f, 330f); alpha = r; if (!reduced) translationY = (1f - r) * 14.dp.toPx() }
            .horizontalScroll(chipScroll).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in ShelfSort.values()) {
                val label = stringResource(sortLabel(s))
                val enabled = when (s) { ShelfSort.SUN -> sun != null; ShelfSort.OPEN -> runOpen != null; else -> true }
                ShelfChip(s, label, sort == s, enabled, k, stringResource(R.string.app_shelf_sort_a11y, label),
                    Modifier.onGloballyPositioned { c -> chipBox[s] = c.positionInParent().x.toInt() to c.size.width }) {
                    if (s == ShelfSort.SUN && sun == null) { notice = noSunText; Qa.log("redesign shelf no sun"); return@ShelfChip }
                    if (s == ShelfSort.OPEN && runOpen == null) { notice = openHint; Qa.log("redesign shelf no report"); return@ShelfChip }
                    haptics.tick(.3f); chosenSort = s; ShelfPrefs.setSort(context, s); Qa.log("redesign shelf sort ${s.id}")
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        val side = ((screenW - 232.dp) / 2).coerceAtLeast(12.dp)
        val rankOf = remember(flat) { flat.withIndex().associate { it.value.first to it.index + 1 } }
        LazyRow(Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 18f) onClose() } },
            state = list, contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(19.dp),
            flingBehavior = rememberSnapFlingBehavior(list)) {
            itemsIndexed(flat, key = { _, it -> it.first }) { i, (key, _) ->
                val r = byKey[key] ?: return@itemsIndexed
                val ans = ShelfAnswers.answer(sort, r, rankOf[key] ?: 1, flat.size, sun?.get(key), runOpen?.invoke(key), snowOf?.get(key))
                ShelfCard(scene, r, k, closed(key), sort, ans, Modifier.graphicsLayer {
                    val e = rise(160f + 45f * minOf(kotlin.math.abs(i - startMid), 5), 390f)
                    alpha = e * (if (i == mid) 1f else .72f); if (!reduced) translationY = (1f - e) * 46.dp.toPx()
                }) {
                    if (i == mid) { haptics.tick(.5f); onPick(key) } else scope.launch { list.animateScrollToItem(i) }
                }
            }
        }
        // the sources, only while the sun or snow chip is on (one line under the cards)
        val source = when (sort) {
            ShelfSort.SUN -> stringResource(R.string.app_lens_sun_note)
            ShelfSort.SNOW -> if (snowAny) stringResource(R.string.app_lens_snow_note) + " Open-Meteo" else null
            else -> null
        }
        if (source != null) Text(source, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(k.halo, Offset.Zero, 8f)))
    }
}

private fun sortLabel(s: ShelfSort): Int = when (s) {
    ShelfSort.LIFT -> R.string.app_shelf_by_lift; ShelfSort.EASY -> R.string.app_shelf_by_diff; ShelfSort.LONG -> R.string.app_shelf_longest
    ShelfSort.SUN -> R.string.app_shelf_sun; ShelfSort.OPEN -> R.string.app_shelf_open; ShelfSort.SNOW -> R.string.app_wx_snow_cap
}

/**
 * One card (version 2): 232 by 172, radius 24, padding 14 / 16. At the start a panel 52 wide, the run's colour at 12%, with its
 * shape (40 by 110); beside it the sign and the name, the chip's answer as the big number (42 light) with its unit and a caption
 * in the accent, and a foot line. The number rolls (digits slide 12 up, 200 ms) when the chip changes.
 */
@Composable
private fun ShelfCard(scene: MapScene, r: ShelfRun, k: SheetInk, closed: Boolean, sort: ShelfSort, ans: ShelfAnswer, modifier: Modifier, onClick: () -> Unit) {
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    val (len, km) = Shelf.length(r.len)
    val lenShown = if (km) java.text.NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(r.len / 1000.0) else nf.format(len.toInt())
    val kind = shelfKind(r); val back = shelfBack(r)
    val a11y = stringResource(R.string.app_shelf_card_a11y, r.title, colourWord(r.color), if (r.kind == "run") levelWord(r.color) else kind,
        lenShown + " " + (if (km) stringResource(R.string.app_shelf_km_cap) else stringResource(R.string.app_shelf_m_cap)), nf.format(r.drop), back)
    val col = plate(r.color)
    val reduced = io.github.pini236.skiapp.ui.Motion.reduced(LocalContext.current)
    val unitText = when (ans.unit) {
        AUnit.MIN -> stringResource(R.string.app_shelf_unit_min); AUnit.DEG -> "°"; AUnit.KM -> stringResource(R.string.app_shelf_km_cap); AUnit.M -> stringResource(R.string.app_shelf_m_cap)
        AUnit.PCT -> "%"; AUnit.CM -> stringResource(R.string.app_shelf_unit_cm); else -> ""
    }
    val big = when (ans.unit) { AUnit.WORD_OPEN -> stringResource(R.string.app_shelf_word_open); AUnit.WORD_CLOSED -> stringResource(R.string.app_shelf_word_closed); else -> ans.num }
    val caption = when (ans.cap) {
        Cap.BACK -> stringResource(R.string.app_shelf_cap_back, ans.a); Cap.STEEP -> stringResource(R.string.app_shelf_cap_steep)
        Cap.RANK -> stringResource(R.string.app_shelf_cap_rank, ans.a, ans.b); Cap.SUN -> stringResource(R.string.app_shelf_cap_sun)
        Cap.OPEN -> stringResource(R.string.app_shelf_cap_open, ans.a); Cap.CLOSED -> stringResource(R.string.app_shelf_cap_closed, ans.a)
        Cap.SNOW -> stringResource(R.string.app_shelf_cap_snow); Cap.NONE -> ""
    }
    val lenFoot = lenShown + " " + (if (km) stringResource(R.string.app_shelf_km_cap) else stringResource(R.string.app_shelf_m_cap))
    val dropFoot = nf.format(r.drop) + " " + stringResource(R.string.app_shelf_drop_cap)
    val steepFoot = stringResource(R.string.app_shelf_foot_steep, r.steep.toString())
    val colourFoot = colourWord(r.color)
    val foot = (if (sort == ShelfSort.LONG) listOf(dropFoot, steepFoot, colourFoot) else listOf(lenFoot, dropFoot, colourFoot)).joinToString(" · ")
    val shape = RoundedCornerShape(24.dp)
    Row(modifier.width(232.dp).height(172.dp).shadow(14.dp, shape, clip = false).clip(shape)
        .background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, shape)
        .semantics(mergeDescendants = true) { contentDescription = a11y; role = Role.Button }.clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val pic = remember(r.key) { ShelfData.shape(scene, r.key, 40f, 110f, 4f) }
        Box(Modifier.width(52.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(col.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(40.dp).height(110.dp)) {
                val s = density
                for (l in pic) {
                    if (l.size < 4) continue
                    val p = Path().apply { moveTo(l[0] * s, l[1] * s); for (i in 1 until l.size / 2) lineTo(l[i * 2] * s, l[i * 2 + 1] * s) }
                    drawPath(p, col, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                pic.firstOrNull()?.takeIf { it.size >= 2 }?.let { l ->
                    val o = Offset(l[0] * s, l[1] * s)
                    drawCircle(Color.White, 2.6.dp.toPx(), o); drawCircle(col, 2.6.dp.toPx(), o, style = Stroke(1.6.dp.toPx()))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.height(20.dp).width(6.dp).clip(RoundedCornerShape(3.dp)).background(col))
                Text(r.title, Modifier.weight(1f, fill = false), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr))
                if (closed) Text(stringResource(R.string.app_lens_closed_leg), Modifier.clip(RoundedCornerShape(9.dp)).background(k.red.copy(alpha = .14f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = k.red)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
            // the big number: digits slide 12 up in 200 ms when the chip changes
            androidx.compose.animation.AnimatedContent(big to unitText, transitionSpec = {
                if (reduced) androidx.compose.animation.fadeIn(tween(150)) togetherWith androidx.compose.animation.fadeOut(tween(150))
                else (androidx.compose.animation.slideInVertically(tween(200)) { it / 3 } + androidx.compose.animation.fadeIn(tween(200))) togetherWith
                    (androidx.compose.animation.slideOutVertically(tween(200)) { -it / 3 } + androidx.compose.animation.fadeOut(tween(120)))
            }, label = "answer") { (num, unit) ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(num, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 42.sp, color = k.ink, maxLines = 1, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                    if (unit.isNotEmpty()) Text(unit, Modifier.padding(bottom = 7.dp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink2, maxLines = 1)
                }
            }
            if (caption.isNotEmpty()) Text(caption, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.acc, maxLines = 1, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(foot, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The name tag of a lit run on the mountain (section 1, as the POC's): 22 high, radius 11, a dot of the run's colour,
 * 11.5 bold; [side] dp beside the line's middle, on the side with more room.
 */
@Composable
internal fun RunTag(view: Stage, scene: MapScene, key: String, title: String, color: String, night: Boolean, side: Float = 44f, onTap: (() -> Unit)? = null) {
    val k = remember(night) { SheetInk(night) }
    val mid = remember(key) {
        scene.runs.pistes.firstOrNull { it.key == key }?.lines?.maxByOrNull { it.size }?.let { l -> val n = l.size / 2; if (n == 0) null else floatArrayOf(l[(n / 2) * 2], l[(n / 2) * 2 + 1]) }
    } ?: return
    var at by remember(key) { mutableStateOf<Offset?>(null) }
    val w = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    LaunchedEffect(key) { while (true) { withFrameNanos { }; at = view.toScreen(mid[0], mid[1])?.let { Offset(it[0], it[1]) } } }
    val p = at ?: return
    // never over the pill at the top (the review of 24.11: tags drawn over it)
    if (p.y < with(LocalDensity.current) { (TOP_GUARD_DP + 11).dp.toPx() }) return
    val dx = with(LocalDensity.current) { side.dp.toPx() }
    // on the side with more room: right of the line's middle when it is in the left half, else left of it (measured width)
    var tagW by remember { mutableStateOf(0) }
    val left = if (p.x > w / 2f) p.x - dx - tagW else p.x + dx
    Row(Modifier.atScreen { IntOffset(left.toInt(), (p.y - 11.dp.toPx()).toInt()) }.onSizeChanged { tagW = it.width }
        .height(22.dp).clip(RoundedCornerShape(11.dp)).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, RoundedCornerShape(11.dp))
        .let { if (onTap != null) it.clickable(onClick = onTap) else it }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(plate(color)))
        Text(title, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = k.ink, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr))
    }
}

/**
 * A rank on the mountain (version 2, "הארוכים"): a disc 22 with the number 1 to 6, ink with a 2 ring of the background,
 * about a third of the way down the run's line.
 */
@Composable
internal fun RankDisc(view: Stage, scene: MapScene, key: String, rank: Int, night: Boolean) {
    val k = remember(night) { SheetInk(night) }
    val pt = remember(key) {
        scene.runs.pistes.firstOrNull { it.key == key }?.lines?.maxByOrNull { it.size }?.let { l -> val n = l.size / 2; if (n == 0) null else { val i = n / 3; floatArrayOf(l[i * 2], l[i * 2 + 1]) } }
    } ?: return
    var at by remember(key) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(key) { while (true) { withFrameNanos { }; at = view.toScreen(pt[0], pt[1])?.let { Offset(it[0], it[1]) } } }
    val p = at ?: return
    if (p.y < with(LocalDensity.current) { (TOP_GUARD_DP + 11).dp.toPx() }) return
    val r = with(LocalDensity.current) { 11.dp.toPx() }
    Box(Modifier.atScreen { IntOffset((p.x - r).toInt(), (p.y - r).toInt()) }.size(22.dp).border(2.dp, if (night) Color(0xFF0F1828) else Color.White, CircleShape)
        .clip(CircleShape).background(k.ink), contentAlignment = Alignment.Center) {
        Text(rank.toString(), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = if (night) Color(0xFF0F1828) else Color.White, maxLines = 1)
    }
}

/** How far down the top pill reaches (with the status bar): nothing of the mountain's is drawn over it. */
internal const val TOP_GUARD_DP = 112

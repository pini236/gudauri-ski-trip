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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
            p.fromLifts.firstOrNull(), back, back?.let { b -> scene.runs.lifts.firstOrNull { it.name == b }?.dur })
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
 * The bottom bar (design/handoff/2026-11-23-redesign-on-mountain, section 1): 10 from the sides, 12 from the bottom,
 * 60 high, radius 30, an opaque surface on Android; from the start: "the village", "where to?", the record button.
 */
@Composable
internal fun BoxScope.Dock(night: Boolean, haptics: Haptics, onVillage: (() -> Unit)?, onFind: () -> Unit, onRecord: (() -> Unit)?) {
    val k = remember(night) { SheetInk(night) }
    // on a tablet 420 wide at the start side, the map's buttons above it (part 8)
    val tab = isTabletUi()
    Row((if (tab) Modifier.align(Alignment.BottomStart).width(TabletUi.DOCK.dp) else Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        .navigationBarsPadding().padding(start = if (tab) 12.dp else 10.dp, end = 10.dp, bottom = 12.dp).height(60.dp)
        .shadow(18.dp, RoundedCornerShape(30.dp), clip = false).clip(RoundedCornerShape(30.dp)).background(k.surf.copy(alpha = 1f))
        .border(1.dp, k.line, RoundedCornerShape(30.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onVillage != null) {
        val village = stringResource(R.string.app_sr_dock_village)
        Row(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).semantics { contentDescription = village; role = Role.Button }
            .clickable { haptics.tick(.3f); onVillage() }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Canvas(Modifier.size(20.dp)) { house(k.ink) }
            Text(stringResource(R.string.app_dock_village), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.ink, maxLines = 1)
        }
        }
        val find = stringResource(R.string.app_sr_dock_find)
        // in large text "where to?" keeps only its icon, in a 44 circle; its words stay for the screen reader (part 8)
        if (bigText()) {
            Box(Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).background(k.line).semantics { contentDescription = find; role = Role.Button }
                .clickable { haptics.tick(.3f); onFind() }, contentAlignment = Alignment.Center) { Canvas(Modifier.size(18.dp)) { glass(k.ink2) } }
        } else
        Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(k.line).semantics { contentDescription = find; role = Role.Button }
            .clickable { haptics.tick(.3f); onFind() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(18.dp)) { glass(k.ink2) }
            Text(stringResource(R.string.app_dock_find), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = k.ink2, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        if (onRecord != null) {
            val rec = stringResource(R.string.app_sr_dock_rec)
            Box(Modifier.size(44.dp).clip(CircleShape).background(k.red).semantics { contentDescription = rec; role = Role.Button }
                .clickable { haptics.tick(.4f); onRecord() }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
            }
        }
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

/**
 * K07, the shelf (section 2): the chips of the four orders, the group's title, and the cards in one row, the middle one
 * lit on the mountain (raised 120 points over the shelf). [open] the run it opens on; [onCenter] the run in the middle
 * (its light and its lift); [onPick] a run chosen; [onClose] the shelf closed.
 */
@Composable
internal fun BoxScope.Shelf(scene: MapScene, view: LiveView, runs: List<ShelfRun>, night: Boolean, haptics: Haptics, open: (List<Pair<String, ShelfGroup>>) -> Int,
                            closed: (String) -> Boolean, onCenter: (ShelfRun) -> Unit, onPick: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val k = remember(night) { SheetInk(night) }
    var chosenSort by remember { mutableStateOf(ShelfPrefs.sort(context)) }
    val sun = remember(night, view.lightTime / 600_000) { view.sunShares() }
    // the sun down: "in the sun now" is off and the shelf is by lift (section 2)
    val sort = if (chosenSort == ShelfSort.SUN && sun == null) ShelfSort.LIFT else chosenSort
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var noSun by remember { mutableStateOf(0) }
    val bottoms = remember(scene) { ShelfData.bottoms(scene) }
    val groups = remember(sort, runs, sun) { Shelf.groups(sort, runs, bottoms::get, sun?.let { m -> { key: String -> m[key] ?: 0f } }) }
    val flat = remember(groups) { Shelf.flat(groups) }
    val byKey = remember(runs) { runs.associateBy { it.key } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = remember { open(flat) })
    var keep by remember { mutableStateOf<String?>(null) }
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
    LaunchedEffect(Unit) { Qa.log("redesign shelf open ${sort.id} ${flat.size}") }
    LaunchedEffect(noSun) { if (noSun > 0) { delay(2000); noSun = 0 } }
    androidx.activity.compose.BackHandler { onClose() }
    // the emulator's switches (qa.redesign shelf:sort:<id>, shelf:next)
    LaunchedEffect(RedesignQa.seq) {
        val c = RedesignQa.cmd
        if (c.startsWith("shelf:sort:")) { chosenSort = ShelfSort.of(c.removePrefix("shelf:sort:")); Qa.log("redesign shelf sort ${chosenSort.id}") }
        if (c == "shelf:next") list.animateScrollToItem((mid + 1).coerceAtMost(flat.size - 1))
    }
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 22.dp)) {
        // the group's title, with a halo as the peaks' names
        val g = flat.getOrNull(mid)?.second
        val title = when {
            g == null -> ""
            g.lift != null -> pluralStringResource(R.plurals.app_shelf_group_lift, g.keys.size, g.lift, g.keys.size.toString())
            g.color != null -> pluralStringResource(R.plurals.app_shelf_group_color, g.keys.size, stringResource(when (g.color) {
                "green" -> R.string.app_shelf_colors_green; "blue" -> R.string.app_shelf_colors_blue; "red" -> R.string.app_shelf_colors_red; else -> R.string.app_shelf_colors_black }), g.keys.size.toString())
            g.sun == true -> stringResource(R.string.app_shelf_group_sun, g.keys.size.toString())
            g.sun == false -> stringResource(R.string.app_shelf_group_shade, g.keys.size.toString())
            sort == ShelfSort.LONG -> stringResource(R.string.app_shelf_pos, (mid + 1).toString(), flat.size.toString())
            else -> pluralStringResource(R.plurals.app_shelf_group_rest, g.keys.size, g.keys.size.toString())
        }
        val shade = androidx.compose.ui.graphics.Shadow(k.halo, Offset.Zero, 8f)
        Text(title, Modifier.padding(horizontal = 16.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = k.ink,
            style = androidx.compose.ui.text.TextStyle(shadow = shade))
        if (noSun > 0) Text(stringResource(R.string.app_lens_no_sun), Modifier.padding(horizontal = 16.dp), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2,
            style = androidx.compose.ui.text.TextStyle(shadow = shade))
        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in ShelfSort.values()) {
                val label = stringResource(when (s) { ShelfSort.LIFT -> R.string.app_shelf_by_lift; ShelfSort.EASY -> R.string.app_shelf_easy_hard
                    ShelfSort.LONG -> R.string.app_shelf_longest; ShelfSort.SUN -> R.string.app_shelf_sun })
                Chip(label, sort == s, k, enabled = s != ShelfSort.SUN || sun != null, a11y = stringResource(R.string.app_shelf_sort_a11y, label)) {
                    if (s == ShelfSort.SUN && sun == null) { noSun++; Qa.log("redesign shelf no sun"); return@Chip }
                    haptics.tick(.3f); chosenSort = s; ShelfPrefs.setSort(context, s); Qa.log("redesign shelf sort ${s.id}")
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
        val side = ((screenW - 232.dp) / 2).coerceAtLeast(12.dp)
        LazyRow(Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 18f) onClose() } },
            state = list, contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(19.dp),
            flingBehavior = rememberSnapFlingBehavior(list)) {
            itemsIndexed(flat, key = { _, it -> it.first }) { i, (key, _) ->
                val r = byKey[key] ?: return@itemsIndexed
                ShelfCard(scene, r, k, closed(key), Modifier.alpha(if (i == mid) 1f else .72f)) {
                    if (i == mid) { haptics.tick(.5f); onPick(key) } else scope.launch { list.animateScrollToItem(i) }
                }
            }
        }
    }
}

/** One card (section 1): 232 by 156, radius 22; the run's shape at the start, then its sign and name, kind, two numbers and the way back. */
@Composable
private fun ShelfCard(scene: MapScene, r: ShelfRun, k: SheetInk, closed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    val (len, km) = Shelf.length(r.len)
    val lenShown = if (km) java.text.NumberFormat.getNumberInstance().apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(r.len / 1000.0) else nf.format(len.toInt())
    val kind = shelfKind(r); val back = shelfBack(r)
    val a11y = stringResource(R.string.app_shelf_card_a11y, r.title, colourWord(r.color), if (r.kind == "run") levelWord(r.color) else kind,
        lenShown + " " + (if (km) stringResource(R.string.app_shelf_km_cap) else stringResource(R.string.app_shelf_m_cap)), nf.format(r.drop), back)
    val col = plate(r.color)
    Row(modifier.width(232.dp).height(156.dp).shadow(14.dp, RoundedCornerShape(22.dp), clip = false).clip(RoundedCornerShape(22.dp))
        .background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, RoundedCornerShape(22.dp))
        .semantics(mergeDescendants = true) { contentDescription = a11y; role = Role.Button }.clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val shape = remember(r.key) { ShelfData.shape(scene, r.key, 40f, 92f, 4f) }
        Canvas(Modifier.width(40.dp).height(92.dp)) {
            val s = density
            for (l in shape) {
                if (l.size < 4) continue
                val p = Path().apply { moveTo(l[0] * s, l[1] * s); for (i in 1 until l.size / 2) lineTo(l[i * 2] * s, l[i * 2 + 1] * s) }
                drawPath(p, col, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            shape.firstOrNull()?.takeIf { it.size >= 2 }?.let { l ->
                val o = Offset(l[0] * s, l[1] * s)
                drawCircle(Color.White, 2.6.dp.toPx(), o); drawCircle(col, 2.6.dp.toPx(), o, style = Stroke(1.6.dp.toPx()))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.height(20.dp).width(6.dp).clip(RoundedCornerShape(3.dp)).background(col))
                Text(r.title, Modifier.weight(1f, fill = false), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = k.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr))
                if (closed) Text(stringResource(R.string.app_lens_closed_leg), Modifier.clip(RoundedCornerShape(9.dp)).background(k.red.copy(alpha = .14f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = k.red)
            }
            Text(kind, fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text(lenShown, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 24.sp, color = k.ink)
                    Text(if (km) stringResource(R.string.app_shelf_km_cap) else stringResource(R.string.app_shelf_m_cap), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
                }
                Column {
                    Text(nf.format(r.drop), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 24.sp, color = k.ink)
                    Text(stringResource(R.string.app_shelf_drop_cap), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
                }
            }
            Text(back, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The name tag of a lit run on the mountain (section 1, as the POC's): 22 high, radius 11, a dot of the run's colour,
 * 11.5 bold; [side] dp beside the line's middle, on the side with more room.
 */
@Composable
internal fun RunTag(view: LiveView, scene: MapScene, key: String, title: String, color: String, night: Boolean, side: Float = 44f, onTap: (() -> Unit)? = null) {
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
    Row(Modifier.onSizeChanged { tagW = it.width }.offset { IntOffset(left.toInt(), (p.y - 11.dp.toPx()).toInt()) }
        .height(22.dp).clip(RoundedCornerShape(11.dp)).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, RoundedCornerShape(11.dp))
        .let { if (onTap != null) it.clickable(onClick = onTap) else it }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(plate(color)))
        Text(title, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = k.ink, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr))
    }
}

/** How far down the top pill reaches (with the status bar): nothing of the mountain's is drawn over it. */
internal const val TOP_GUARD_DP = 112

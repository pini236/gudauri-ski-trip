package io.github.pini236.skiapp.redesign

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.gestures.detectTapGestures
import io.github.pini236.skiapp.home.Around
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Icons

/** What each tile of the menu does (UX pass part 1). Null: the tile is not shown. */
internal class MenuActions(
    val onTrip: () -> Unit,
    val onSkipass: () -> Unit,
    val onGroup: (List<io.github.pini236.skiapp.group.GroupSummary>?) -> Unit,
    val onFood: (() -> Unit)?,
    val onGames: (() -> Unit)?,
    val onDays: (() -> Unit)?,
    val onLifts: () -> Unit,
    val onAccount: (() -> Unit)?,
    val onSettings: (() -> Unit)?,
    val onLanguage: (() -> Unit)?,
)

/**
 * The menu (UX pass part 1, boards MN1 to MN6): everything that is not the mountain, in one bottom sheet: the trip as a
 * small boarding pass, six tiles with one live line each, and the account, the settings and the language. It opens over
 * the mountain with no camera flight; a drag down, back, or a touch on the mountain closes it.
 */
@Composable
internal fun BoxScope.MenuSheet(
    scene: MapScene, around: Around?, night: Boolean, haptics: Haptics, liftsLine: String, meetNow: Pair<String, String>?,
    food: Int, actions: MenuActions, onClose: () -> Unit,
) {
    val k = remember(night) { SheetInk(night) }
    val ctx = LocalContext.current
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(if (io.github.pini236.skiapp.ui.Motion.reduced(ctx)) 1 else 250)); Qa.log("redesign menu open") }
    androidx.activity.compose.BackHandler { onClose() }
    LaunchedEffect(Unit) { if (Hints.next(ctx, Hints.VILLAGE) == -1 && Hints.next(ctx, Hints.MENU) == 0) Hints.setNext(ctx, Hints.MENU, -1) }
    // the veil over the mountain; a touch on it closes the menu
    Box(Modifier.fillMaxSize().alpha(enter.value).background(if (night) Color(0x5C020612) else Color(0x38283E6E))
        .pointerInput(Unit) { detectTapGestures { onClose() } })
    val groups by produceState<List<io.github.pini236.skiapp.group.GroupSummary>?>(
        GroupListCache.read(ctx)?.map { r -> GroupListCache.dates(ctx)[r.id].let { io.github.pini236.skiapp.group.GroupSummary(r.id, r.name, it?.first, it?.second) } }, around?.people) {
        val fresh = runCatching { around?.people?.api?.myGroups() }.getOrNull()
        if (fresh != null) {
            GroupListCache.writeDates(ctx, fresh)
            val old = GroupListCache.read(ctx).orEmpty().associateBy { it.id }
            GroupListCache.write(ctx, fresh.map { io.github.pini236.skiapp.group.MyGroup(it.id, it.name, old[it.id]?.members, old[it.id]?.admin == true) })
        }
        value = fresh ?: value
    }
    var groupWaiting by remember { mutableStateOf(false) }
    LaunchedEffect(groups, groupWaiting) { if (groupWaiting && groups != null) { groupWaiting = false; actions.onGroup(groups) } }
    val days by produceState(0) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { io.github.pini236.skiapp.dayrec.DayRec.store(ctx).days().size }.getOrDefault(0) } }
    val top = sheetShape()
    val tile = if (night) Color(0x12FFFFFF) else Color(0xBDFFFFFF)
    val tint = k.acc.copy(alpha = if (night) .17f else .11f)
    val menuName = stringResource(R.string.app_dock_menu)
    var acted by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(Modifier.align(Alignment.BottomCenter).sheetWidth().alpha(enter.value)
        .semantics { contentDescription = menuName }
        .pointerInput(Unit) { detectVerticalDragGestures(onDragStart = { acted = 0 }) { _, dy -> if (acted == 0 && dy > 18f) { acted = 1; onClose() } } }
        .shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
        .navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp)
        .heightIn(max = 760.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
        Box(Modifier.hintTarget("trip")) { TripTile(around, k, haptics, actions.onTrip) }
        val t = around?.trip
        val pr = around?.prices?.takeIf { it.valid(around.today) }
        val tripPrice = around?.let { tripSkipass(it, t) }
        val passLine = when {
            pr == null -> stringResource(R.string.weather_none)
            tripPrice != null && t != null -> t.skiDayCount().let { n -> pluralStringResource(R.plurals.app_vill_pass_trip, n, n.toString(), "${nf.format(tripPrice)} ₾") }
            else -> pr.daily(false)?.let { stringResource(R.string.app_vill_pass_day, "${nf.format(it)} ₾") } ?: stringResource(R.string.weather_none)
        }
        val g = groups
        val groupLine = when {
            meetNow != null -> stringResource(R.string.app_vill_grp_meet, meetNow.first, meetNow.second)
            g == null && around?.people != null -> null // still loading: a bar, never "none"
            g.isNullOrEmpty() -> stringResource(R.string.app_menu_grp_none)
            g.size == 1 -> g[0].name
            else -> pluralStringResource(R.plurals.app_vill_grp_n, g.size, g.size.toString())
        }
        val tiles = buildList<MTile> {
            add(MTile(Icons.map, stringResource(R.string.app_skipass_title), passLine, null, actions.onSkipass))
            add(MTile(Icons.people, stringResource(R.string.app_grp_title), groupLine, "group", { if (g == null && around?.people != null) groupWaiting = true else actions.onGroup(g) }, busy = groupWaiting))
            if (actions.onFood != null && food > 0) add(MTile(Icons.pin, stringResource(R.string.app_vill_food),
                pluralStringResource(R.plurals.app_menu_food_n, food, food.toString()), null, actions.onFood))
            actions.onGames?.let { go -> add(MTile(Icons.trophy, stringResource(R.string.app_cabin_title),
                pluralStringResource(R.plurals.app_menu_games_n, around?.games ?: 0, (around?.games ?: 0).toString()), null, go)) }
            actions.onDays?.let { go -> add(MTile(Icons.calendar, stringResource(R.string.app_days_title),
                if (days > 0) pluralStringResource(R.plurals.app_me_days_sub, days, days) else stringResource(R.string.app_menu_days_none), null, go)) }
            add(MTile(Icons.lift, stringResource(R.string.app_menu_lifts), liftsLine, null, actions.onLifts))
        }
        // from font scale 1.3 the grid is one column (part 1)
        tiles.chunked(if (bigText()) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m -> Box(Modifier.weight(1f).let { if (m.hint != null) it.hintTarget(m.hint) else it }) { Tile(m, k, tile, tint, haptics) } }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.onAccount?.let { Foot(Icons.user, stringResource(R.string.app_menu_account), k, haptics, Modifier.weight(1f), it) }
            actions.onSettings?.let { Foot(Icons.gear, stringResource(R.string.about_settings), k, haptics, Modifier.weight(1f), it) }
            actions.onLanguage?.let { Foot(Icons.globe, io.github.pini236.skiapp.i18n.Lang.current(ctx.resources).name, k, haptics, Modifier.weight(1f), it) }
        }
    }
    HintSequence(Hints.MENU, listOf(HintStep("trip", R.string.app_hint_trip, 16f), HintStep("group", R.string.app_hint_group, 16f)), ready = true, night = night, again = Hints.again)
}

private class MTile(val icon: ImageVector, val title: String, val line: String?, val hint: String?, val onTap: () -> Unit, val busy: Boolean = false)

@Composable
private fun Tile(m: MTile, k: SheetInk, bg: Color, tint: Color, haptics: Haptics) {
    val big = bigText()
    Column(Modifier.fillMaxWidth().alpha(if (m.busy) .6f else 1f).heightIn(min = if (big) 124.dp else 104.dp).clip(RoundedCornerShape(22.dp)).background(bg)
        .border(1.dp, k.line, RoundedCornerShape(22.dp))
        .semantics(mergeDescendants = true) { role = Role.Button }.clickable { haptics.tick(.3f); m.onTap() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) { Icon(m.icon, null, Modifier.size(20.dp), tint = k.acc) }
        Column {
            Text(m.title, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // a changed line cross-fades in place (150 ms), the frame never moves
            androidx.compose.animation.Crossfade(m.line, animationSpec = androidx.compose.animation.core.tween(150), label = "line") { line ->
                if (line == null) SkeletonBar(k, 90, 12, Modifier.padding(top = 4.dp))
                else Text(line, fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2, maxLines = if (big) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Foot(icon: ImageVector, text: String, k: SheetInk, haptics: Haptics, mod: Modifier, onTap: () -> Unit) {
    Row(mod.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, k.line, RoundedCornerShape(22.dp))
        .semantics { role = Role.Button }.clickable { haptics.tick(.3f); onTap() }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = k.ink)
        Text(text, Modifier.padding(start = 6.dp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The trip as a small boarding pass (MN2): no trip, or the two airports and the dates with the count on the stub. */
@Composable
private fun TripTile(around: Around?, k: SheetInk, haptics: Haptics, onTap: () -> Unit) {
    val t = around?.trip
    val paper = if (k.night) Color(0xFF1B2638) else Color(0xFFFFFDF7)
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 112.dp).shadow(6.dp, shape, clip = false).clip(shape).background(paper)
        .semantics(mergeDescendants = true) { role = Role.Button }.clickable { haptics.tick(.3f); onTap() }) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.app_home_trip), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
            if (t == null) {
                Text(stringResource(R.string.trip_when_fly), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
                Box(Modifier.padding(top = 6.dp).heightIn(min = 28.dp).clip(RoundedCornerShape(14.dp)).background(k.acc).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("+ " + stringResource(R.string.app_menu_trip_add), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = k.onAcc)
                }
            } else {
                Text("⁦${t.out.fromCode ?: t.out.from}⁩ ✈ ⁦${t.out.toCode ?: t.out.to}⁩", fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 25.sp, color = k.ink, maxLines = 1)
                Text(pluralStringResource(R.plurals.app_vill_trip_dates, t.skiDayCount(), tripSpan(t.out.date, t.ret?.date), t.skiDayCount().toString()),
                    fontFamily = Gudi, fontSize = 13.sp, color = k.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // the dashed seam, then the stub
        Box(Modifier.width(2.dp).fillMaxSize().padding(vertical = 10.dp).background(k.line))
        Column(Modifier.width(112.dp).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (t == null) Text("✈", fontSize = 30.sp, color = k.ink2)
            else when (val st = t.stage(java.time.LocalDateTime.now(io.github.pini236.skiapp.meet.Meet.GUDAURI))) {
                is Trip.Stage.Before -> {
                    Text(st.days.toString(), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 44.sp, color = k.ink)
                    Text(pluralStringResource(R.plurals.app_pass_days, st.days), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2, maxLines = 2)
                }
                is Trip.Stage.SkiDay -> {
                    Text(st.n.toString(), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 44.sp, color = k.ink)
                    Text(stringResource(R.string.trip_ski_day_of, st.n.toString(), st.of.toString()), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2, maxLines = 2)
                }
                else -> Icon(Icons.check, null, Modifier.size(34.dp), tint = k.good)
            }
        }
    }
}

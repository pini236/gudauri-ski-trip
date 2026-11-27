package io.github.pini236.skiapp.redesign

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.places.LocalPlaces
import io.github.pini236.skiapp.places.Place
import io.github.pini236.skiapp.ui.Icons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FOOD_COL = Color(0xFFC2540A)
private val CAFE_COL = Color(0xFF7A4A2B)

/** The ticket office's pin id: the village picture's pin (village.json `tickets`), not a place of places.json. */
internal const val TICKETS = "tickets"

/** A pin on a picture or the mountain: a 26 circle with a white border (40 and a 3 ring when chosen), an icon 14 (22) or a letter; [at] in screen px. */
@Composable
private fun PinDot(col: Color, icon: ImageVector?, letter: String?, on: Boolean, k: SheetInk, haptics: Haptics, at: Offset, onTap: (() -> Unit)?) {
    val dens = LocalDensity.current
    val d = if (on) 40.dp else 26.dp
    val px = with(dens) { d.toPx() }
    Box(Modifier.atScreen { IntOffset((at.x - px / 2).toInt(), (at.y - px / 2).toInt()) }.size(d)
        .let { m -> if (on) m.border(3.dp, k.acc, CircleShape) else m }.clip(CircleShape).background(col).border(2.dp, Color.White, CircleShape)
        .let { m -> if (onTap != null) m.semantics { role = Role.Button }.clickable { haptics.tick(.3f); onTap() } else m }, contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, Modifier.size(if (on) 22.dp else 14.dp), tint = Color.White)
        else if (letter != null) Text(letter, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = if (on) 18.sp else 13.sp, color = Color.White)
    }
}

/** The chosen pin's name, 38 above it (height 26), centred on it and whole on the screen. */
@Composable
private fun NameTag(text: String, at: Offset, k: SheetInk, night: Boolean) {
    val dens = LocalDensity.current
    var w by remember(text) { mutableStateOf(0) }
    val screenW = with(dens) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val shift = PinRules.slide(at.x, w.toFloat(), screenW, with(dens) { 8.dp.toPx() })
    val top = at.y - with(dens) { 38.dp.toPx() + 26.dp.toPx() }
    Box(Modifier.atScreen { IntOffset((at.x - w / 2f + shift).toInt(), top.toInt()) }.onSizeChanged { w = it.width }
        .height(26.dp).clip(RoundedCornerShape(13.dp)).background(k.ink).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = if (night) Color(0xFF0F1828) else Color.White, maxLines = 1)
    }
}

/** The ticket office as the sheet's head (the ski pass's "איפה הקופה?"): no coordinates in the data, so no navigation. */
@Composable
private fun TicketHead(k: SheetInk, onClose: () -> Unit) {
    Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(k.ink), contentAlignment = Alignment.Center) { Icon(Icons.pin, null, Modifier.size(22.dp), tint = k.surf) }
        Text(stringResource(R.string.app_vill_pin_tickets), Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(44.dp).clip(CircleShape).semantics { role = Role.Button }.clickable(onClick = onClose), contentAlignment = Alignment.Center) { Icon(Icons.x, stringResource(R.string.app_vill_back), Modifier.size(20.dp), tint = k.ink) }
    }
}

/** The places the screen lists (also the menu tile's count): null when the feature is off. */
internal fun foodItems(places: io.github.pini236.skiapp.places.Places?, scene: MapScene, view: PeakData.View?): List<FoodItem>? {
    places ?: return null
    return FoodRules.items(places, scene.runs.lifts, { p -> inPicture(view, p) }, { p -> scene.terrain.elev(Geo.x(p.lon), Geo.y(p.lat)) })
}

private fun picturePoint(view: PeakData.View?, p: Place): Offset? {
    val pr = view?.proj ?: return null
    return pr.project(p.lat, p.lon, pr.zref)
}

private fun inPicture(view: PeakData.View?, p: Place): Boolean {
    val o = picturePoint(view, p) ?: return false
    return view != null && o.x in 0f..view.w && o.y in 0f..view.h
}

/**
 * UX pass part 4: food and drink. Two tabs (the village by the picture, the rest of the mountain), a list, and one place with
 * "ניווט" and "שיתוף". The village tab sits on the village picture with its pins; the mountain tab keeps the live mountain behind.
 */
@Composable
internal fun FoodScreen(
    scene: MapScene, data: PeakData?, mode: DayNight.Mode, now: Long, night: Boolean, haptics: Haptics,
    /** a pin to open on: [TICKETS] from the ski pass ("איפה הקופה?"), selected with its name tag and the sheet low; null for the list. */
    focus: String? = null,
    /** the live mountain's screen point of a model point (x, y as `Geo`), or null when it is not on the screen; for the mountain tab's pins. */
    toScreen: (Float, Float) -> Offset? = { _, _ -> null },
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val places = LocalPlaces.current
    val k = remember(night) { SheetInk(night) }
    val view by produceState<PeakData.View?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { PeakData.village(context.assets.open("redesign/village/village.json").bufferedReader().use { it.readText() }) }.getOrNull() }
    }
    val light = data?.let { PeakLight.pick(it.lights, now, mode) }
    val pic by produceState<ImageBitmap?>(null, view, light?.name) {
        val v = view ?: return@produceState; val f = v.images[light?.name] ?: return@produceState
        value = withContext(Dispatchers.IO) { runCatching { context.assets.open("redesign/village/$f").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull() }
    }
    val marks by produceState<VillageMarks?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { VillageMarks.parse(context.assets.open("redesign/village/village.json").bufferedReader().use { it.readText() }) }.getOrNull() }
    }
    val all = remember(view, places, scene) { foodItems(places, scene, view).orEmpty() }
    val village = all.filter { it.inVillage }; val rest = all.filter { !it.inVillage }
    var tab by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf(focus) }
    androidx.activity.compose.BackHandler { if (sel != null) sel = null else onBack() }
    val shown = if (tab == 0) village else rest
    val chosen = all.firstOrNull { it.place.id == sel }
    val ticketSel = sel == TICKETS
    // the mountain tab's pins follow the live mountain: its screen points, read every frame while the tab is open
    var mpos by remember { mutableStateOf<List<Offset?>>(emptyList()) }
    LaunchedEffect(tab, all) {
        if (tab != 1) return@LaunchedEffect
        val pinned = all.filter { !it.inVillage }
        while (true) {
            withFrameNanos { }
            val n = pinned.map { toScreen(Geo.x(it.place.lon), Geo.y(it.place.lat)) }
            if (n != mpos) mpos = n
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dens = LocalDensity.current
        val kk = with(dens) { maxWidth.toPx() } / (view?.w ?: 390f)
        if (tab == 0) {
            Box(Modifier.fillMaxSize().background(if (night) Color(0xFF0B1424) else Color(0xFFE9EEF3)).clickable(enabled = sel != null) { sel = null })
            val v = view
            if (v != null) Canvas(Modifier.fillMaxWidth().height(with(dens) { (v.h * kk).toDp() })) {
                pic?.let { drawImage(it, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), (v.h * kk).toInt())) }
            }
            // the ticket office and the parking (village.json, from the buildings) with the places, one spread so none sits on another
            val extra = marks?.pins?.filter { it.kind == "tickets" || it.kind == "parking" }.orEmpty()
            val pts = village.map { picturePoint(view, it.place) ?: Offset.Zero } + extra.map { Offset(it.x, it.y) }
            val at = VillageMarks.spread(pts.map { it.x to it.y })
            for ((i, f) in village.withIndex()) {
                val on = f.place.id == sel
                PinDot(kindColor(f.place.kind), kindIcon(f.place.kind), null, on, k, haptics, Offset(at[i].first * kk, at[i].second * kk)) { sel = f.place.id }
                if (on) NameTag("⁦${f.place.name}⁩", Offset(at[i].first * kk, at[i].second * kk), k, night)
            }
            for ((j, m) in extra.withIndex()) {
                val i = village.size + j
                val tk = m.kind == "tickets"
                val on = tk && ticketSel
                val c = Offset(at[i].first * kk, at[i].second * kk)
                PinDot(if (tk) k.ink else Color(0xFF5A6B7D), if (tk) Icons.pin else null, if (tk) null else "P", on, k, haptics, c, if (tk) ({ sel = TICKETS }) else null)
                if (on) NameTag(stringResource(R.string.app_vill_pin_tickets), c, k, night)
            }
        } else {
            Box(Modifier.fillMaxSize().background(if (night) Color(0x5C020612) else Color(0x24283E6E)).clickable(enabled = sel != null) { sel = null })
            // the pins sit on the live mountain above the sheet (396), never under it or the back button
            val limit = with(dens) { (maxHeight - 396.dp - 14.dp).toPx() }
            for ((i, f) in rest.withIndex()) {
                val o = mpos.getOrNull(i) ?: continue
                if (o.y > limit || o.y < with(dens) { 60.dp.toPx() } || o.x < 0f || o.x > with(dens) { maxWidth.toPx() }) continue
                val on = f.place.id == sel
                PinDot(kindColor(f.place.kind), kindIcon(f.place.kind), null, on, k, haptics, o) { sel = f.place.id }
                if (on) NameTag("⁦${f.place.name}⁩", o, k, night)
            }
        }
        // back
        Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 8.dp).size(44.dp).shadow(8.dp, CircleShape, clip = false).clip(CircleShape)
            .background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, CircleShape).semantics { role = Role.Button }
            .clickable { haptics.tick(.3f); if (sel != null) sel = null else onBack() }, contentAlignment = Alignment.Center) {
            Icon(Icons.back, stringResource(R.string.app_vill_back), Modifier.size(22.dp), tint = k.ink)
        }
        val top = sheetShape()
        Column(Modifier.align(Alignment.BottomCenter).sheetWidth().height(if (ticketSel) 232.dp else if (tab == 0) 452.dp else 396.dp)
            .shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
            .navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
            if (ticketSel) TicketHead(k) { sel = null }
            else if (chosen != null) PlaceHead(chosen, k, context) { sel = null }
            else {
                Text(stringResource(R.string.app_vill_food), Modifier.padding(top = 8.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink)
                Row(Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(k.line), verticalAlignment = Alignment.CenterVertically) {
                    Tab(stringResource(R.string.app_food_tab_village, village.size.toString()), tab == 0, k, Modifier.weight(1f)) { tab = 0; sel = null }
                    Tab(stringResource(R.string.app_food_tab_rest, rest.size.toString()), tab == 1, k, Modifier.weight(1f)) { tab = 1; sel = null }
                }
            }
            if (!ticketSel) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (chosen != null) Text(stringResource(if (tab == 0) R.string.app_food_more_village else R.string.app_food_more_rest), Modifier.padding(top = 10.dp, bottom = 2.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink2)
                if (shown.isEmpty()) Text(stringResource(R.string.places_none), Modifier.padding(vertical = 14.dp), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
                for (f in shown) if (f.place.id != sel) FoodRow(f, k) { haptics.tick(.3f); sel = f.place.id }
                Text(stringResource(R.string.places_check) + " · " + (places?.attribution?.substringBefore(", http")?.replace("(c)", "©")?.takeIf { it.isNotBlank() } ?: "© OpenStreetMap"), Modifier.padding(top = 10.dp), fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2)
            }
        }
    }
}

private fun kindColor(kind: String) = if (kind == "cafe") CAFE_COL else FOOD_COL
private fun kindIcon(kind: String): ImageVector = if (kind == "cafe") Icons.cup else Icons.fork

@Composable
private fun kindText(kind: String) = stringResource(if (kind == "cafe") R.string.places_kind_cafe else R.string.places_kind_food)

@Composable
private fun whereText(w: FoodWhere): String = when (w) {
    is FoodWhere.FromLift -> stringResource(R.string.app_food_from_lift, w.metres.toString(), w.lift)
    is FoodWhere.NearTop -> stringResource(R.string.app_food_near_top, w.lift)
    is FoodWhere.NearBottom -> stringResource(R.string.app_food_near_bottom, w.lift)
    is FoodWhere.OnRun -> stringResource(R.string.app_food_on_run, w.lift)
    FoodWhere.None -> ""
}

@Composable
private fun Tab(text: String, on: Boolean, k: SheetInk, m: Modifier, onClick: () -> Unit) {
    Box(m.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(19.dp)).background(if (on) k.acc else Color.Transparent).semantics { role = Role.Tab }.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) k.onAcc else k.ink2, maxLines = 1)
    }
}

@Composable
private fun FoodRow(item: FoodItem, k: SheetInk, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).semantics(mergeDescendants = true) { role = Role.Button }.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(kindColor(item.place.kind)), contentAlignment = Alignment.Center) { Icon(kindIcon(item.place.kind), null, Modifier.size(19.dp), tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("⁦${item.place.name}⁩", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(kindText(item.place.kind), whereText(item.where)).filter { s -> s.isNotEmpty() }.joinToString(" · "), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.forward, null, Modifier.size(18.dp), tint = k.ink2)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
    }
}

@Composable
private fun PlaceHead(item: FoodItem, k: SheetInk, context: android.content.Context, onClose: () -> Unit) {
    val name = item.place.name.orEmpty()
    Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(kindColor(item.place.kind)), contentAlignment = Alignment.Center) { Icon(kindIcon(item.place.kind), null, Modifier.size(22.dp), tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text("⁦$name⁩", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(kindText(item.place.kind), whereText(item.where)).filter { s -> s.isNotEmpty() }.joinToString(" · "), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
            if (item.place.seasonal) Text(stringResource(R.string.places_seasonal), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).semantics { role = Role.Button }.clickable(onClick = onClose), contentAlignment = Alignment.Center) { Icon(Icons.x, stringResource(R.string.app_vill_back), Modifier.size(20.dp), tint = k.ink) }
    }
    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(k.acc).semantics { role = Role.Button }.clickable {
            val uri = Uri.parse("geo:${item.place.lat},${item.place.lon}?q=${item.place.lat},${item.place.lon}(${Uri.encode(name)})")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.forward, null, Modifier.size(18.dp), tint = k.onAcc)
            Text(stringResource(R.string.app_food_navigate), Modifier.padding(start = 6.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.onAcc)
        }
        Row(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).border(1.dp, k.line, RoundedCornerShape(23.dp)).semantics { role = Role.Button }.clickable {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$name\nhttps://www.google.com/maps/search/?api=1&query=${item.place.lat},${item.place.lon}")
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.share, null, Modifier.size(18.dp), tint = k.ink)
            Text(stringResource(R.string.app_food_share), Modifier.padding(start = 6.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
        }
    }
}

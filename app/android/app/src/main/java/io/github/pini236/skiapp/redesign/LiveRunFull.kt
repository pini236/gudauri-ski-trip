package io.github.pini236.skiapp.redesign

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.RunFacts
import io.github.pini236.skiapp.data.Video
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.openLink
import io.github.pini236.skiapp.map.runLink
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.weather.Forecast
import io.github.pini236.skiapp.weather.LocalWeather
import io.github.pini236.skiapp.weather.deg
import java.net.URL
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The run card (RK1, RK2, RK5): a floating card 18 from the sides and 44 from the bottom, the previous and next runs' cards
 * peeking 10 at both sides, the page dots under it. A drag sideways moves the card with its neighbours and settles the next
 * one in the centre (towards the start: the next, so everything mirrors with the layout direction); a pull up opens it,
 * a pull down returns it to the low height and then closes it.
 */
@Composable
internal fun RunSheets(key: String, r: PeakData.Run, d: PeakData, g: Glass, scene: MapScene, videos: List<Video>, night: Boolean,
                       haptics: Haptics, full: Boolean, onFull: (Boolean) -> Unit, onPick: (String) -> Unit, onClose: () -> Unit, onRide: (() -> Unit)? = null) {
    val order = remember(scene) { scene.runs.pistes.let { RunFacts.order(it) } }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val next = NowRules.neighbour(order, key, 1); val prev = NowRules.neighbour(order, key, -1)
    val nextA = next?.let { stringResource(R.string.run_nav_next_aria, it) }
    val prevA = prev?.let { stringResource(R.string.run_nav_prev_aria, it) }
    val vids = remember(videos, key) { Video.of(videos, key) }
    val k = remember(night) { SheetInk(night) }
    val dens = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var wPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(dens) { 8.dp.toPx() }
    val limPx = with(dens) { 60.dp.toPx() }
    // the next run's card sits at the start side (right in Hebrew, left in English); the previous one at the end side
    val nextSign = if (rtl) 1f else -1f
    // the new run is in the centre as soon as its key arrives
    LaunchedEffect(key) { dragX.snapTo(0f) }
    fun settle(to: String?, sign: Float) {
        scope.launch {
            if (to == null) { dragX.animateTo(0f, spring(.8f, 500f)); return@launch }
            haptics.tick(.3f)
            dragX.animateTo(sign * (wPx + gapPx), tween(300))
            onPick(to)
            kotlinx.coroutines.delay(600)
            if (dragX.value != 0f) dragX.animateTo(0f, tween(200))
        }
    }
    val card = RoundedCornerShape(28.dp)
    val at = order.indexOf(key)
    Column(Modifier.sheetWidth().navigationBarsPadding().semantics {
        customActions = listOfNotNull(
            nextA?.let { a -> CustomAccessibilityAction(a) { onPick(next); true } },
            prevA?.let { a -> CustomAccessibilityAction(a) { onPick(prev); true } })
    }) {
        Box(Modifier.padding(horizontal = 18.dp).pointerInput(key, full, next, prev) {
            detectHorizontalDragGestures(onDragEnd = {
                val v = dragX.value
                when {
                    v * nextSign > limPx && next != null -> settle(next, nextSign)
                    v * nextSign < -limPx && prev != null -> settle(prev, -nextSign)
                    else -> settle(null, 0f)
                }
            }, onDragCancel = { settle(null, 0f) }) { change, a ->
                change.consume()
                var nv = dragX.value + a
                if (nv * nextSign > 0f && next == null) nv = 0f
                if (nv * nextSign < 0f && prev == null) nv = 0f
                scope.launch { dragX.snapTo(nv) }
            }
        }.pullSheet(
            // the sheet follows the finger (Pini, 24.11.2026): down from the full one is the low one, down from the low one closes
            onDown = if (full) ({ onFull(false); Qa.log("redesign run low") }) else onClose,
            onUp = if (!full) ({ onFull(true); Qa.log("redesign run full") }) else null,
            closes = !full)) {
            Box(Modifier.absoluteOffset { IntOffset(dragX.value.roundToInt(), 0) }) {
                // the neighbours: the same shape, 8 from the card, 72% (only the edge shows)
                if (next != null) Box(Modifier.matchParentSize().absoluteOffset { IntOffset((nextSign * (wPx + gapPx)).roundToInt(), 0) }
                    .graphicsLayer { alpha = .72f }.clip(card).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, card))
                if (prev != null) Box(Modifier.matchParentSize().absoluteOffset { IntOffset((-nextSign * (wPx + gapPx)).roundToInt(), 0) }
                    .graphicsLayer { alpha = .72f }.clip(card).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, card))
                Box(Modifier.onSizeChanged { wPx = it.width.toFloat() }) {
                    RunFull(key, r, d, scene, vids, night, haptics, onRide, open = full,
                        onOpen = { haptics.tick(.3f); onFull(true); Qa.log("redesign run full") })
                }
            }
        }
        // the dots sit under the card, outside it: 18 from the bottom, the current one 18 wide, at most seven
        Spacer(Modifier.height(14.dp))
        if (at >= 0) Row(Modifier.align(Alignment.CenterHorizontally).height(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in NowRules.window(order.size, at)) Box(Modifier.size(if (i == at) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == at) k.ink else k.ink.copy(alpha = .32f)))
        } else Spacer(Modifier.height(6.dp))
        Spacer(Modifier.height(18.dp))
    }
}

/** S07, the full sheet: the head with sharing, the three numbers, the conditions at the top and the bottom, the videos, the dots. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RunFull(key: String, r: PeakData.Run, d: PeakData, scene: MapScene, vids: List<Video>, night: Boolean, haptics: Haptics,
                    onRide: (() -> Unit)? = null, open: Boolean, onOpen: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    val ctx = LocalContext.current
    val nf = remember { NumberFormat.getIntegerInstance() }
    val piste = scene.runs.pistes.firstOrNull { it.key == key }
    val facts = remember(piste) { piste?.let { RunFacts.of(scene.terrain, it) } }
    val h = LocalConfiguration.current.screenHeightDp
    val top = RoundedCornerShape(28.dp)
    LaunchedEffect(key, open) { if (open) Qa.log("redesign run full $key ${vids.size}") }
    val w = LocalWeather.current
    val f = w.usable
    Column(Modifier.fillMaxWidth().then(if (open) Modifier.heightIn(max = (h - 112 - 44 - 38).coerceAtLeast(320).dp) else Modifier).shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
        .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // the head: the run's plate, the name and the line under it, sharing
        val colour = stringResource(when (r.color) { "green" -> R.string.common_color_green; "blue" -> R.string.common_color_blue; "red" -> R.string.common_color_red; else -> R.string.common_color_black })
        val level = stringResource(when (r.color) { "green" -> R.string.map_difficulty_beginner; "blue" -> R.string.map_difficulty_easy; "red" -> R.string.map_difficulty_intermediate; else -> R.string.map_difficulty_hard })
        val shareT = stringResource(R.string.run_share_title, r.name)
        val shareA = stringResource(R.string.run_share_button)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // a run with no number (Kikilo 1, Kobi): a small square of its colour, not an empty plate
            if (r.ref.isBlank()) Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(plate(r.color)))
            else Box(Modifier.height(30.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(7.dp)).background(plate(r.color)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                Text(r.ref, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, style = TextStyle(textDirection = TextDirection.Ltr))
            }
            Column(Modifier.weight(1f)) {
                Text(r.name, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = k.ink, style = TextStyle(textDirection = TextDirection.Ltr))
                Row(Modifier.padding(top = 3.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(k.line).padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(plate(r.color)))
                    Text("$colour · $level", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = k.ink, maxLines = 1)
                }
                val from = piste?.fromLifts?.firstOrNull()?.let { id -> scene.runs.lifts.firstOrNull { it.id == id }?.name }
                val to = piste?.toLifts?.firstOrNull()?.let { id -> scene.runs.lifts.firstOrNull { it.id == id }?.name }
                if (from != null && to != null) Text("$from \u2193 $to", Modifier.padding(top = 3.dp), fontFamily = Gudi, fontSize = 13.sp, color = k.ink2,
                    style = TextStyle(textDirection = TextDirection.Ltr), maxLines = 1)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, k.line, CircleShape).semantics { contentDescription = shareA; role = Role.Button }.clickable {
                haptics.tick(.3f)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, shareT).putExtra(Intent.EXTRA_TEXT, shareT + "\n" + runLink(key))
                runCatching { ctx.startActivity(Intent.createChooser(send, shareT)) }
                Telemetry.event("run_share", mapOf("run" to key, "method" to "native"))
            }, contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(io.github.pini236.skiapp.ui.Icons.share, null, Modifier.size(20.dp), tint = k.ink) }
        }
        if (facts != null && facts.points.size > 1) RunProfile(facts, d.slope, k, k.surf.copy(alpha = 1f))
        // the three numbers
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // the number alone, its unit in the line under it (S07: "km length", "m drop"; the review of 24.11)
            for ((v, c) in listOf("%.1f".format(r.len / 1000f) to stringResource(R.string.app_shelf_km_cap) + " " + stringResource(R.string.common_length_label),
                nf.format(r.drop) to stringResource(R.string.app_shelf_drop_cap),
                "${r.steep}°" to stringResource(R.string.run_stat_steep_label))) Column {
                Text(v, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 26.sp, color = k.ink, maxLines = 1)
                Text(c, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2)
            }
        }
        if (onRide != null) RideButton(k, haptics, onRide)
        val topNow = if (facts != null) f?.at(facts.top.toFloat(), w.local)?.temp else null
        if (!open) {
            if (RunCardRules.footRow(vids.size, f != null)) FootRow(vids, topNow, k, haptics, onOpen)
            return@Column
        }
        // the conditions at the run's top and bottom (the interpolation by height, as today)
        var note by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.run_cond_title), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink)
            Box(Modifier.size(44.dp).semantics { role = Role.Button }.clickable { note = !note }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, k.ink2, CircleShape), contentAlignment = Alignment.Center) {
                    Text("i", fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = k.ink2)
                }
            }
        }
        if (note && f != null) Text(stringResource(R.string.run_cond_note, io.github.pini236.skiapp.weather.ago(f.updated, w.now)), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
        if (facts != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((title, alt) in listOf(R.string.run_cond_top to facts.top, R.string.run_cond_bottom to facts.bot)) {
                val v = f?.at(alt.toFloat(), w.local)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.dp, k.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(deg(v?.temp), fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 23.sp, color = if (v?.temp == null) k.ink2 else k.ink)
                    Text(stringResource(title), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = k.ink)
                    Text(v?.wind?.let { stringResource(R.string.app_run_cond_wind, nf.format(it)) } ?: stringResource(R.string.weather_none),
                        fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
                }
            }
        }
        // the videos: up to three, and "all N"; a tap opens YouTube
        Text(stringResource(R.string.app_run_vids_from), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink)
        var all by remember(key) { mutableStateOf(false) }
        if (vids.isEmpty()) {
            Text(stringResource(R.string.run_videos_empty), fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
            val q = "Gudauri " + r.name + " ski"
            Text(stringResource(R.string.run_videos_search_youtube, "Gudauri", r.name),
                Modifier.heightIn(min = 44.dp).clickable { openLink(ctx, "https://www.youtube.com/results?search_query=" + android.net.Uri.encode(q)) }.padding(vertical = 10.dp),
                fontFamily = Gudi, fontSize = 13.5.sp, color = k.acc)
        }
        for ((i, v) in (if (all) vids else vids.take(3)).withIndex()) {
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(k.line))
            VideoRow(v, k) { haptics.tick(.25f); openLink(ctx, v.url); Telemetry.event("video_play", mapOf("run" to key)) }
        }
        if (!all && vids.size > 3) Text(pluralStringResource(R.plurals.app_run_videos_all, vids.size, vids.size.toString()),
            Modifier.heightIn(min = 44.dp).clickable { all = true }.padding(vertical = 10.dp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = k.acc)
    }
}

/** One video (section 1): the picture 92 by 52 with its length, the title in up to two lines, the channel. */
@Composable
private fun VideoRow(v: Video, k: SheetInk, onClick: () -> Unit) {
    var bmp by remember(v.url) { mutableStateOf<Bitmap?>(THUMBS[v.youtubeId ?: ""]) }
    LaunchedEffect(v.url) {
        val id = v.youtubeId ?: return@LaunchedEffect
        if (bmp == null) bmp = withContext(Dispatchers.IO) {
            runCatching { URL("https://i.ytimg.com/vi/$id/mqdefault.jpg").openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()?.also { THUMBS[id] = it }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics(mergeDescendants = true) { role = Role.Button }.clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(92.dp, 52.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF2B3440), Color(0xFF55616F)))),
            contentAlignment = Alignment.Center) {
            val b = bmp
            if (b != null) Image(b.asImageBitmap(), null, Modifier.size(92.dp, 52.dp), contentScale = ContentScale.Crop)
            else androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(size.width * .25f, size.height * .15f); lineTo(size.width * .85f, size.height * .5f); lineTo(size.width * .25f, size.height * .85f); close() }, Color.White)
            }
            if (v.length.isNotBlank()) Text(v.length, Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x8C000000)).padding(horizontal = 4.dp),
                fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(v.title, fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = k.ink, maxLines = 2, style = TextStyle(textDirection = TextDirection.Content))
            Text(v.channel, fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 1)
        }
    }
}

private val THUMBS = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

/** K12's way in, on the run's sheet: "to go down in a film", 48 high, the accent's outline. */
@Composable
internal fun RideButton(k: SheetInk, haptics: Haptics, onRide: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(k.acc)
        .semantics { role = Role.Button }.clickable { haptics.tick(.3f); onRide() }, contentAlignment = Alignment.Center) {
        Text("\u25B6 " + stringResource(R.string.app_run_ride), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.onAcc)
    }
}

/** The foot row of the low card: up to three video frames overlapping, the count, the temperature now, and a chevron that opens the card. */
@Composable
private fun FootRow(vids: List<Video>, topTemp: Int?, k: SheetInk, haptics: Haptics, onOpen: () -> Unit) {
    val n = vids.size
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(k.line).semantics(mergeDescendants = true) { role = Role.Button }
        .clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val fr = RunCardRules.frames(n)
        if (fr > 0) Box(Modifier.width((52 + (fr - 1) * 30).dp).height(34.dp)) {
            for (i in 0 until fr) Box(Modifier.absoluteOffset(x = (i * 30).dp).size(52.dp, 34.dp).clip(RoundedCornerShape(8.dp)).border(1.5.dp, k.surf.copy(alpha = 1f), RoundedCornerShape(8.dp))) {
                VideoThumb(vids[i], 52.dp, 34.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            if (n > 0) Text(pluralStringResource(R.plurals.app_run_vids_n, n, n.toString()), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = k.ink)
            if (topTemp != null) Text(deg(topTemp) + " · " + stringResource(R.string.app_run_cond_now), fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(k.surf.copy(alpha = 1f)), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
                val st = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(size.width * .1f, size.height * .7f); lineTo(size.width * .5f, size.height * .3f); lineTo(size.width * .9f, size.height * .7f) }, k.ink, style = st)
            }
        }
    }
}

/** A video's frame at a fixed size (the thumbnail cache is shared with the rows of the open card). */
@Composable
private fun VideoThumb(v: Video, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    var bmp by remember(v.url) { mutableStateOf<Bitmap?>(THUMBS[v.youtubeId ?: ""]) }
    LaunchedEffect(v.url) {
        val id = v.youtubeId ?: return@LaunchedEffect
        if (bmp == null) bmp = withContext(Dispatchers.IO) {
            runCatching { URL("https://i.ytimg.com/vi/$id/mqdefault.jpg").openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()?.also { THUMBS[id] = it }
        }
    }
    Box(Modifier.size(w, h).background(Brush.linearGradient(listOf(Color(0xFF2B3440), Color(0xFF55616F))))) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.size(w, h), contentScale = ContentScale.Crop) }
    }
}

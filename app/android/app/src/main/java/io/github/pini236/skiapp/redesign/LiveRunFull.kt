package io.github.pini236.skiapp.redesign

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
 * The run's two heights (S07): the low one is K10 as it was; a pull up, or "videos" in it, opens the full one. Sideways on
 * either: the next or the previous run in the shelf's order (towards the start: the next).
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
    var dx = 0f
    Box(Modifier.semantics {
        customActions = listOfNotNull(
            nextA?.let { a -> CustomAccessibilityAction(a) { onPick(next); true } },
            prevA?.let { a -> CustomAccessibilityAction(a) { onPick(prev); true } })
    }.pointerInput(key, full) {
        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
            val lim = 60.dp.toPx()
            // towards the start (right in Hebrew, left in English) is the next run
            val toStart = if (rtl) dx > lim else dx < -lim
            val toEnd = if (rtl) dx < -lim else dx > lim
            when { toStart && next != null -> { haptics.tick(.3f); onPick(next) }; toEnd && prev != null -> { haptics.tick(.3f); onPick(prev) } }
        }) { _, a -> dx += a }
    }.pullSheet(
        // the sheet follows the finger (Pini, 24.11.2026): down from the full one is the low one, down from the low one closes
        onDown = if (full) ({ onFull(false); Qa.log("redesign run low") }) else onClose,
        onUp = if (!full) ({ onFull(true); Qa.log("redesign run full") }) else null,
        closes = !full)) {
        if (!full) RunSheet(r, d, g, onClose) {
            // K12: "to go down in a film", once it is built (part 4's handoff, section 14)
            if (onRide != null) RideButton(SheetInk(night), haptics, onRide)
            // the way to the videos from the low sheet: it opens the full one
            Text(stringResource(R.string.app_run_videos_n, vids.size.toString()),
                Modifier.heightIn(min = 44.dp).semantics { role = Role.Button }.clickable { haptics.tick(.3f); onFull(true); Qa.log("redesign run full") }
                    .padding(vertical = 10.dp), fontFamily = Gudi, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = SheetInk(night).acc)
        } else RunFull(key, r, scene, vids, night, haptics, order, onRide)
    }
}

/** S07, the full sheet: the head with sharing, the three numbers, the conditions at the top and the bottom, the videos, the dots. */
@Composable
private fun RunFull(key: String, r: PeakData.Run, scene: MapScene, vids: List<Video>, night: Boolean, haptics: Haptics, order: List<String>, onRide: (() -> Unit)? = null) {
    val k = remember(night) { SheetInk(night) }
    val ctx = LocalContext.current
    val nf = remember { NumberFormat.getIntegerInstance() }
    val piste = scene.runs.pistes.firstOrNull { it.key == key }
    val facts = remember(piste) { piste?.let { RunFacts.of(scene.terrain, it) } }
    val h = LocalConfiguration.current.screenHeightDp
    val top = sheetShape()
    LaunchedEffect(key) { Qa.log("redesign run full $key ${vids.size}") }
    Column(Modifier.sheetWidth().heightIn(max = (h - 236).coerceAtLeast(320).dp).shadow(8.dp, top).clip(top).background(k.surf.copy(alpha = 1f)).border(1.dp, k.line, top)
        .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
        // the head: the run's plate, the name and the line under it, sharing
        val colour = stringResource(when (r.color) { "green" -> R.string.common_color_green; "blue" -> R.string.common_color_blue; "red" -> R.string.common_color_red; else -> R.string.common_color_black })
        val level = stringResource(when (r.color) { "green" -> R.string.map_difficulty_beginner; "blue" -> R.string.map_difficulty_easy; "red" -> R.string.map_difficulty_intermediate; else -> R.string.map_difficulty_hard })
        val shareT = stringResource(R.string.run_share_title, r.name)
        val shareA = stringResource(R.string.run_share_button)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.height(30.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(7.dp)).background(plate(r.color)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                Text(r.ref, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, style = TextStyle(textDirection = TextDirection.Ltr))
            }
            Column(Modifier.weight(1f)) {
                Text(r.name, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = k.ink, style = TextStyle(textDirection = TextDirection.Ltr))
                Text(colour + stringResource(R.string.run_color_official_suffix) + " · " + level, fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, k.line, CircleShape).semantics { contentDescription = shareA; role = Role.Button }.clickable {
                haptics.tick(.3f)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, shareT).putExtra(Intent.EXTRA_TEXT, shareT + "\n" + runLink(key))
                runCatching { ctx.startActivity(Intent.createChooser(send, shareT)) }
                Telemetry.event("run_share", mapOf("run" to key, "method" to "native"))
            }, contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(io.github.pini236.skiapp.ui.Icons.share, null, Modifier.size(20.dp), tint = k.ink) }
        }
        // the three numbers
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // the number alone, its unit in the line under it (S07: "km length", "m drop"; the review of 24.11)
            for ((v, c) in listOf("%.1f".format(r.len / 1000f) to stringResource(R.string.app_shelf_km_cap) + " " + stringResource(R.string.common_length_label),
                nf.format(r.drop) to stringResource(R.string.app_shelf_drop_cap),
                "${r.steep}°" to stringResource(R.string.run_stat_steep_label))) Column {
                Text(v, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 30.sp, color = k.ink, maxLines = 1)
                Text(c, fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2)
            }
        }
        if (onRide != null) RideButton(k, haptics, onRide)
        // the conditions at the run's top and bottom (the interpolation by height, as today)
        val w = LocalWeather.current
        val f = w.usable
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
        Text(stringResource(R.string.app_run_videos_n, vids.size.toString()), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink)
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
        // the dots: where this run is among all of them
        val at = order.indexOf(key)
        if (at >= 0) Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in NowRules.window(order.size, at)) Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == at) k.ink else k.line))
        }
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
    Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.5.dp, k.acc, RoundedCornerShape(24.dp))
        .semantics { role = Role.Button }.clickable { haptics.tick(.3f); onRide() }, contentAlignment = Alignment.Center) {
        Text("\u25B6 " + stringResource(R.string.app_run_ride), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.acc)
    }
}

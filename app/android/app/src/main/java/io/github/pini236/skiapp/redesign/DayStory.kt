package io.github.pini236.skiapp.redesign

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.dayrec.Moments
import io.github.pini236.skiapp.dayrec.ui.DayModel
import io.github.pini236.skiapp.dayrec.ui.MomentText
import io.github.pini236.skiapp.dayrec.ui.StoryData
import io.github.pini236.skiapp.dayrec.ui.Track
import io.github.pini236.skiapp.dayrec.ui.storyKind
import io.github.pini236.skiapp.dayrec.ui.titleOf
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.qa.Qa
import io.github.pini236.skiapp.telemetry.Telemetry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The story of the day (board L10), in the approved words (`moment.*`): the head for the kind of day over its numbers,
 * then each insight as a card, with a window onto the day's light where the moment has a time and place. The share at
 * the bottom draws the same picture as today's story (shareStory: no fall, no off-piste, no name). Left out, and logged:
 * the fall's card ("a kiss to the snow?"), since its "I did not fall" needs a way to dismiss a blow in a closed day
 * (rewriting the log and its analysis) that the app does not have yet, and the board's "yes, it was" has no key.
 */
@Composable
internal fun DayStory(story: StoryData, sm: DayModel.Summary, paint: DayPaint.Day, hide: List<LongArray>, track: () -> Track?, runs: Runs?, night: Boolean, sample: Boolean,
                      haptics: Haptics, shareOnly: Boolean = false, elev: ((Float, Float) -> Float)? = null,
                      /** The day and its segments, for "where the day went" under a one-moment story (DS5). */
                      day: DayModel.Day? = null, segs: List<org.json.JSONObject> = emptyList(),
                      /** "See the day on the mountain" under the video card: leaves the story and plays the day's film. */
                      onSeeMountain: (() -> Unit)? = null, onBack: () -> Unit) {
    // board L11: "share" opens what leaves the phone, the picture or the one-minute video; from the day's sheet (L06,
    // [shareOnly]) it opens straight, and back goes back to the sheet
    var sharing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(shareOnly) }
    BackHandler { if (sharing && !shareOnly) sharing = false else onBack() }
    // the emulator's run opens and closes the sheet
    LaunchedEffect(RedesignQa.seq) { when (RedesignQa.cmd) { "share" -> sharing = true; "share:off" -> sharing = false } }
    val ctx = LocalContext.current
    val locale = ctx.resources.configuration.locales[0]
    val zone = ZoneId.of(DayParams.TZ)
    val dens = ctx.resources.displayMetrics.density
    val bg = if (night) Color(0xFF0A1322) else Color(0xFFF2F5F9)
    val ink = if (night) Color(0xFFEAF0F7) else Color(0xFF0E2233)
    val muted = if (night) Color(0xB3EAF0F7) else Color(0xFF5B6B7B)
    val glass = if (night) Color(0xEB0F1828) else Color(0xF5FFFFFF)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // run names are isolated so a Latin name with a number reads whole inside a Hebrew sentence ("Sadzele 1", not "1 Sadzele")
    val name: (String) -> String = { k -> "\u2068" + titleOf(runs, k) + "\u2069" }
    val words = remember(ctx) { MomentText.Words(ctx, locale, group = true) }
    val texts = remember(story, locale) { story.items.map { item -> MomentText.plan(item, zone, locale, name = name)?.let { words.render(it, item.optLong("seed")) }
        ?.takeIf { it.first != null || it.second.isNotEmpty() } } }
    val keys = remember(story) { story.items.map { it.optString("key") } }
    val (cardIdx, moreIdx) = remember(story, texts) { StoryRules.fill(keys, story.story) { texts[it] != null } }
    val cards = remember(cardIdx) { cardIdx.map { story.items[it] } }
    val head = remember(story, sm) { words.render(MomentText.head(story.theme, sm.descents, sm.dropM, sm.distM, locale), Moments.seed("story.head", sm.start)) }
    // the painted mountain under the cards' windows (board L10, the designer's point 9): the picture and the day's lines
    // projected onto it once, off the main thread; without the ground's heights the windows stay drawn from above
    val mountain by androidx.compose.runtime.produceState<Pair<StoryVideo.Mountain, List<FloatArray>>?>(null, paint, night, elev) {
        val e = elev ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            OnMountain.load(ctx, night, e)?.let { m -> m to paint.lines.map { l -> OnMountain.project(m.view.proj!!, e, l.xy, 2f) } }
        }
    }
    val mImg = remember(mountain) { mountain?.first?.img?.asImageBitmap() }
    // the share picture, drawn once off the main thread: the page's own preview (DS4), then handed to the share sheet
    val preview by androidx.compose.runtime.produceState<Pair<android.graphics.Bitmap, String>?>(null, story, night, elev) {
        value = sharePicture(ctx, story, sm, paint, hide, night, track, name, elev)
    }
    val previewImg = remember(preview) { preview?.first?.asImageBitmap() }
    // "where the day went" for the one-moment page (DS5)
    val whereData = remember(day, segs) {
        day?.let { d ->
            val ds = d.chapters.flatMap { it.items }.filterIsInstance<DayModel.Descent>()
            val total = (d.summary.end - d.summary.start) / 1000.0
            val sec = { type: String, kind: String? -> segs.filter { it.optString("type") == type && (kind == null || it.optString("kind") == kind) }.sumOf { (it.optLong("t1") - it.optLong("t0")) / 1000.0 } }
            DayTimelineRules.where(total, ds.sumOf { if (it.movingS > 0) it.movingS else (it.t1 - it.t0) / 1000.0 }, sec("lift", null), sec("stop", "queue"))
        }
    }
    LaunchedEffect(story) {
        Qa.log("redesign day story ${cards.size}")
        if (!sample) Telemetry.event("story_view", mapOf("moments" to cards.count { it.getString("key") != "tomorrow" && it.getString("key") != "trip" }.coerceAtMost(3), "surface" to "end_of_day"))
    }
    var playStart by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    var playSingle by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    var shareVideo by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val accent = Color(0xFF7C4FE0)
    val chevron = if (rtl) "‹" else "›"
    val windowPaint = MomentWindowPaint(paint, mountain, mImg, night, ramp, dens)
    Box(Modifier.fillMaxSize().background(bg)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 72.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                Column(Modifier.padding(horizontal = 4.dp).padding(bottom = 2.dp)) {
                    head.first?.let { Text(it, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = ink) }
                    head.second.forEach { Text(it, fontFamily = Gudi, fontSize = 14.sp, color = muted) }
                }
            }
            if (cards.isNotEmpty()) item {
                Text(stringResource(R.string.app_ds_watch), Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(accent)
                    .clickable(role = Role.Button) { haptics.tick(.4f); playSingle = null; playStart = 0 }.padding(vertical = 15.dp),
                    fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
            }
            itemsIndexed(cardIdx) { n, ix ->
                val item = story.items[ix]
                val (t, ls) = texts[ix] ?: return@itemsIndexed
                val soft = item.getString("key") == "tomorrow"
                if (StoryRules.solo(cardIdx.size)) {
                    // DS5: one moment is the big card, the window over the card's words
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button) { haptics.tick(.3f); playSingle = null; playStart = n }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MomentWindow(item, 362.dp, 70f, 18.dp, windowPaint, h = 210.dp, pin = pinOf(item))
                        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            t?.let { Text(it, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (soft) muted else ink) }
                            ls.forEach { Text(it, fontFamily = Gudi, fontSize = 14.5.sp, color = muted) }
                        }
                    }
                    return@itemsIndexed
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) { haptics.tick(.3f); playSingle = null; playStart = n }
                    .padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MomentWindow(item, 76.dp, 46f, 14.dp, windowPaint)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        t?.let { Text(it, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = if (soft) muted else ink) }
                        ls.forEach { Text(it, fontFamily = Gudi, fontSize = 13.5.sp, color = muted) }
                    }
                    Text(chevron, fontFamily = Gudi, fontSize = 22.sp, color = muted)
                }
            }
            if (StoryRules.solo(cardIdx.size) && whereData != null) item {
                WhereBar(whereData, ramp, TlColors(ink, muted, rule, glass, night))
            }
            if (moreIdx.isNotEmpty()) {
                item { Text(stringResource(R.string.app_ds_more), Modifier.padding(horizontal = 4.dp).padding(top = 10.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink) }
                items(moreIdx) { ix ->
                    val (t, ls) = texts[ix] ?: return@items
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { haptics.tick(.3f); playSingle = ix; playStart = 0 }
                        .padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MomentWindow(story.items[ix], 40.dp, 46f, 12.dp, windowPaint)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            t?.let { Text(it, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink) }
                            ls.forEach { Text(it, fontFamily = Gudi, fontSize = 13.sp, color = muted) }
                        }
                        Text(chevron, fontFamily = Gudi, fontSize = 20.sp, color = muted)
                    }
                }
            }
            // the end of the page: the share picture's own section (DS4): the picture 150 x 188, "share" and "save" beside it
            item {
                Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.app_ds_picture), Modifier.padding(horizontal = 4.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PicturePreview(previewImg, rule)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((label, primary) in listOf(R.string.app_day_share to true, R.string.app_share_save to false)) {
                                Text(stringResource(label), Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                                    .background(if (primary) accent else Color.Transparent).border(1.5.dp, if (primary) Color.Transparent else rule, RoundedCornerShape(18.dp))
                                    .clickable(role = Role.Button) { haptics.tick(.4f); Qa.log("redesign day story share ${storyKind(cards.firstOrNull()?.getString("key") ?: "other")}"); shareVideo = false; sharing = true }
                                    .padding(vertical = 17.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    color = if (primary) Color.White else ink, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        // the pill: back, the day and its date, and the sample's badge
        Row(Modifier.statusBarsPadding().padding(8.dp).fillMaxWidth().height(48.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.app_g_back)
            Box(Modifier.size(48.dp).clickable(role = Role.Button) { onBack() }.semantics { contentDescription = back }, contentAlignment = Alignment.Center) {
                Text(if (rtl) "›" else "‹", fontFamily = Gudi, fontSize = 20.sp, color = ink)
            }
            val date = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT).format(Instant.ofEpochMilli(sm.start).atZone(zone))
            Text(stringResource(R.string.app_day_story_title) + " · " + date, Modifier.weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink, maxLines = 2)
            if (sample) Text(stringResource(R.string.app_day_sample), Modifier.padding(end = 14.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x33A98BEE)).padding(horizontal = 8.dp, vertical = 2.dp), fontFamily = Gudi, fontSize = 12.sp, color = ink)
        }
        playStart?.let { start ->
            val deck = playSingle?.let { listOf(it) } ?: cardIdx
            StoryPlayer(deck, start, story, texts, windowPaint, night, rtl, ink, muted, glass, haptics, previewImg,
                onShare = { video -> shareVideo = video; playStart = null; sharing = true }, onClose = { playStart = null })
        }
        if (sharing) DayShare(story, sm, paint, hide, track, name, night, sample, haptics, elev = elev, startVideo = shareVideo, pre = preview,
            onSeeMountain = onSeeMountain, onBack = { if (shareOnly) onBack() else sharing = false })
    }
}

/** The share picture's small preview (DS4, DS3): 150 x 188; a quiet box while it is being drawn. */
@Composable
private fun PicturePreview(img: androidx.compose.ui.graphics.ImageBitmap?, rule: Color) {
    Box(Modifier.size(150.dp, 188.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, rule, RoundedCornerShape(14.dp)).background(rule), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** The pin over a moment's place: the steepest pitch's degrees or the fastest's speed (StoryRules.pin). */
private fun pinOf(item: org.json.JSONObject): String? {
    val p = item.optJSONObject("p")
    return StoryRules.pin(item.optString("key"), p?.takeIf { it.has("deg") }?.optInt("deg"), p?.takeIf { it.has("kmh") }?.optInt("kmh"))
}

/** What a window onto the mountain needs, packed so the cards and the player share it. */
internal class MomentWindowPaint(val paint: DayPaint.Day, val mountain: Pair<StoryVideo.Mountain, List<FloatArray>>?, val img: androidx.compose.ui.graphics.ImageBitmap?,
                                 val night: Boolean, val ramp: IntArray, val dens: Float)

/**
 * A window onto the day's light at the moment's place ([pts]: about that many points of the painted picture across). Nothing without a time.
 * [h] makes it a wide window (DS5: [size] wide by [h] high); [from] and [glide] (0..1, already eased) move the camera from the last
 * moment's place to this one's; [pin] is the label over the moment's place ("28°").
 */
@Composable
private fun MomentWindow(item: org.json.JSONObject, size: androidx.compose.ui.unit.Dp, pts: Float, radius: androidx.compose.ui.unit.Dp, w: MomentWindowPaint, modifier: Modifier = Modifier,
                         h: androidx.compose.ui.unit.Dp? = null, from: org.json.JSONObject? = null, glide: Float = 1f, pin: String? = null) {
    val paint = w.paint; val night = w.night; val dens = w.dens; val ramp = w.ramp
    val at = item.optLong("t").takeIf { it > 0 }?.let { paint.track.at(it) } ?: return
    val atFrom = from?.optLong("t")?.takeIf { it > 0 }?.let { paint.track.at(it) }
    val mt = w.mountain; val mi = w.img
    val atOn = if (mt != null) OnMountain.project(mt.first.view.proj!!, mt.first.elev, at, 2f).takeIf { !it[0].isNaN() } else null
    val fromOn = if (mt != null && atFrom != null) OnMountain.project(mt.first.view.proj!!, mt.first.elev, atFrom, 2f).takeIf { !it[0].isNaN() } else null
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val box = if (h != null) modifier.fillMaxWidth().height(h) else modifier.size(size)
    if (mt != null && mi != null && atOn != null) Canvas(box.clip(RoundedCornerShape(radius))) {
        val k = size.toPx().coerceAtMost(this.size.minDimension) / pts
        val cx = if (fromOn != null) StoryRules.lerp(fromOn[0], atOn[0], glide) else atOn[0]
        val cy = if (fromOn != null) StoryRules.lerp(fromOn[1], atOn[1], glide) else atOn[1]
        val ox = this.size.width / 2 - cx * k; val oy = this.size.height / 2 - cy * k
        drawImage(mi, dstOffset = androidx.compose.ui.unit.IntOffset(ox.toInt(), oy.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize((mt.first.view.w * k).toInt(), (mt.first.view.h * k).toInt()))
        for (pass in 0..1) paint.lines.forEachIndexed { li, l -> val q = mt.second[li]
            for (i in 0 until l.band.size - 1) {
                if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
                val a0 = Offset(ox + q[i * 2] * k, oy + q[i * 2 + 1] * k); val b0 = Offset(ox + q[i * 2 + 2] * k, oy + q[i * 2 + 3] * k)
                if (kotlin.math.abs(a0.x - this.size.width / 2) > this.size.width || kotlin.math.abs(a0.y - this.size.height / 2) > this.size.height) continue
                if (pass == 0) drawLine(if (night) Color(0xFF050A16) else Color.White, a0, b0, 4.4f * dens, cap = StrokeCap.Round)
                else drawLine(Color(0xFF000000.toInt() or ramp[l.band[i]]), a0, b0, 2.6f * dens, cap = StrokeCap.Round)
            }
        }
        val here = Offset(ox + atOn[0] * k, oy + atOn[1] * k)
        drawCircle(Color.White, 5f * dens, here)
        drawCircle(Color(0xFFE0246E), 5f * dens, here, style = androidx.compose.ui.graphics.drawscope.Stroke(2f * dens))
        pin?.let { drawPin(measurer, it, here, dens) }
    } else Canvas(box.clip(RoundedCornerShape(radius)).background(if (night) Color(0xFF0E1A2C) else Color(0xFFE9EEF4))) {
        val half = 320f   // metres from the centre to the window's edge
        val k = this.size.minDimension / 2 / half
        val cx = if (atFrom != null) StoryRules.lerp(atFrom[0], at[0], glide) else at[0]
        val cy = if (atFrom != null) StoryRules.lerp(atFrom[1], at[1], glide) else at[1]
        fun p(x: Float, y: Float) = Offset(this.size.width / 2 + (x - cx) * k, this.size.height / 2 - (y - cy) * k)
        for (l in paint.lines) for (i in 0 until l.band.size - 1) {
            val ax = l.xy[i * 2]; val ay = l.xy[i * 2 + 1]
            if (kotlin.math.abs(ax - cx) > half * 1.5f || kotlin.math.abs(ay - cy) > half * 1.5f) continue
            val a = p(ax, ay); val b = p(l.xy[i * 2 + 2], l.xy[i * 2 + 3])
            drawLine(if (night) Color(0xFF050A16) else Color.White, a, b, 4.4f * dens, cap = StrokeCap.Round)
            drawLine(Color(0xFF000000.toInt() or ramp[l.band[i]]), a, b, 2.6f * dens, cap = StrokeCap.Round)
        }
        val here = p(at[0], at[1])
        drawCircle(Color.White, 4.5f * dens, here)
        drawCircle(Color(0xFF2F6FE4), 3f * dens, here)
        pin?.let { drawPin(measurer, it, here, dens) }
    }
}

/** The label over a moment's place: a dark rounded pill with the number, its tail on the place (DS2, "28°"). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPin(measurer: androidx.compose.ui.text.TextMeasurer, label: String, at: Offset, dens: Float) {
    val tl = measurer.measure(label, androidx.compose.ui.text.TextStyle(fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White))
    val pw = tl.size.width + 18 * dens; val ph = tl.size.height + 8 * dens
    val left = (at.x - pw / 2).coerceIn(0f, (size.width - pw).coerceAtLeast(0f)); val top = (at.y - 14 * dens - ph).coerceAtLeast(0f)
    drawLine(Color(0xFF0E2233), at, Offset(at.x, top + ph), 1.4f * dens)
    drawRoundRect(Color(0xFF0E2233), Offset(left, top), androidx.compose.ui.geometry.Size(pw, ph), androidx.compose.ui.geometry.CornerRadius(ph / 2))
    drawText(tl, topLeft = Offset(left + 9 * dens, top + 4 * dens))
}

/**
 * The player (DS2, DS3): segments at the top (one per moment, then the finale), 6 s a moment, a tap on the inline-end half
 * goes back and on the rest forward, a press holds. The finale is the share picture's own page. The camera glides between moments (300 ms).
 */
@Composable
private fun StoryPlayer(deck: List<Int>, start: Int, story: StoryData, texts: List<Pair<String?, List<String>>?>, w: MomentWindowPaint, night: Boolean, rtl: Boolean,
                        ink: Color, muted: Color, glass: Color, haptics: Haptics, previewImg: androidx.compose.ui.graphics.ImageBitmap?,
                        onShare: (Boolean) -> Unit, onClose: () -> Unit) {
    BackHandler { onClose() }
    val count = deck.size + 1   // the moments, then the finale
    var cur by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(start.coerceIn(0, count - 1)) }
    var frac by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(0f) }
    var held by remember { androidx.compose.runtime.mutableStateOf(false) }
    // the camera glides from the last moment's place to this one's (300 ms); the first moment and the finale just appear
    val glide = remember { androidx.compose.animation.core.Animatable(1f) }
    var fromIx by remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    var lastCur by remember { androidx.compose.runtime.mutableStateOf(cur) }
    LaunchedEffect(cur) {
        val prev = lastCur; lastCur = cur
        if (prev != cur && prev < deck.size && cur < deck.size) {
            fromIx = deck[prev]; glide.snapTo(0f)
            glide.animateTo(1f, androidx.compose.animation.core.tween(StoryRules.GLIDE_MS, easing = androidx.compose.animation.core.Easing { StoryRules.ease(it) }))
        } else { fromIx = null; glide.snapTo(1f) }
    }
    LaunchedEffect(cur, held) {
        if (held || cur >= count - 1) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameMillis { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameMillis { it }
            frac += (now - last).toFloat() / StoryRules.MOMENT_MS; last = now
            if (frac >= 1f) {
                val n = StoryRules.next(cur, count)
                if (n != null) { frac = 0f; cur = n }
                return@LaunchedEffect
            }
        }
    }
    val bg = if (night) Color(0xFF0A1322) else Color(0xFFF2F5F9)
    val accent = Color(0xFF7C4FE0)
    val rule = if (night) Color(0x33FFFFFF) else Color(0x1F0E2233)
    Box(Modifier.fillMaxSize().background(bg)
        .pointerInput(count, rtl) {
            detectTapGestures(onPress = { held = true; tryAwaitRelease(); held = false },
                onTap = { o ->
                    if (StoryRules.tapBack(o.x, size.width.toFloat(), rtl)) { cur = StoryRules.prev(cur); frac = 0f }
                    else { val n = StoryRules.next(cur, count); if (n == null) onClose() else { cur = n; frac = 0f } }
                })
        }) {
        val finale = cur >= deck.size
        if (!finale) {
            val ix = deck[cur]
            val item = story.items[ix]
            val (t, ls) = texts[ix] ?: Pair<String?, List<String>>(null, emptyList())
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    MomentWindow(item, 340.dp, 110f, 24.dp, w, Modifier.fillMaxSize(), from = fromIx?.let { story.items.getOrNull(it) }, glide = glide.value, pin = pinOf(item))
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(24.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    t?.let { Text(it, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ink) }
                    ls.forEach { Text(it, fontFamily = Gudi, fontSize = 15.sp, color = muted) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_ds_picture), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = ink, textAlign = TextAlign.Center)
                PicturePreview(previewImg, rule)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, primary) in listOf(R.string.app_day_share to true, R.string.app_share_save to false)) {
                        Text(stringResource(label), Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (primary) accent else Color.Transparent).border(1.5.dp, if (primary) Color.Transparent else rule, RoundedCornerShape(18.dp))
                            .clickable(role = Role.Button) { haptics.tick(.4f); onShare(false) }.padding(vertical = 17.dp),
                            fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (primary) Color.White else ink, textAlign = TextAlign.Center)
                    }
                }
                Text(stringResource(R.string.app_share_video), Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onShare(true) }.padding(12.dp),
                    fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
            }
        }
        // segments: they follow the layout direction (Row starts at the reading start), each filling toward the reading end
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (s in 0 until count) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(rule)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(StoryRules.segmentFill(s, cur, frac)).background(ink))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_ds_i_of_n, cur + 1, count), Modifier.weight(1f), fontFamily = Gudi, fontSize = 13.sp, color = muted)
                val close = stringResource(R.string.app_g_back)
                Box(Modifier.size(44.dp).clickable(role = Role.Button) { onClose() }.semantics { contentDescription = close }, contentAlignment = Alignment.Center) {
                    Text("✕", fontFamily = Gudi, fontSize = 18.sp, color = ink)
                }
            }
        }
    }
}

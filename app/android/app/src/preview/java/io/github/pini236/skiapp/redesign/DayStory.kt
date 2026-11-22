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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.pini236.skiapp.ui.Plex
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
                      haptics: Haptics, onBack: () -> Unit) {
    // board L11: "share" opens what leaves the phone, the picture or the one-minute video
    var sharing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    BackHandler { if (sharing) sharing = false else onBack() }
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
    val name: (String) -> String = { k -> titleOf(runs, k) }
    val words = remember(ctx) { MomentText.Words(ctx, locale) }
    val cards = remember(story) { story.cards().filter { it.getString("key") != "fall" && it.getString("key") != "offpiste" } }
    val head = remember(story, sm) { words.render(MomentText.head(story.theme, sm.descents, sm.dropM, sm.distM, locale), Moments.seed("story.head", sm.start)) }
    LaunchedEffect(story) {
        Qa.log("redesign day story ${cards.size}")
        if (!sample) Telemetry.event("story_view", mapOf("moments" to cards.count { it.getString("key") != "tomorrow" && it.getString("key") != "trip" }.coerceAtMost(3), "surface" to "end_of_day"))
    }
    Box(Modifier.fillMaxSize().background(bg)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 72.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                Column(Modifier.padding(horizontal = 4.dp).padding(bottom = 2.dp)) {
                    head.first?.let { Text(it, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 27.sp, color = ink) }
                    head.second.forEach { Text(it, fontFamily = Plex, fontSize = 14.sp, color = muted) }
                }
            }
            items(cards) { item ->
                val plan = remember(item) { MomentText.plan(item, zone, locale, name = name) } ?: return@items
                val (t, ls) = remember(item) { words.render(plan, item.optLong("seed")) }
                if (t == null && ls.isEmpty()) return@items
                val soft = item.getString("key") == "tomorrow"
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glass).border(1.dp, rule, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // a window onto the day's light at the moment's place
                    val at = item.optLong("t").takeIf { it > 0 }?.let { paint.track.at(it) }
                    if (at != null) Canvas(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(if (night) Color(0xFF0E1A2C) else Color(0xFFE9EEF4))) {
                        val half = 320f   // metres from the centre to the window's edge
                        val k = size.minDimension / 2 / half
                        fun p(x: Float, y: Float) = Offset(size.width / 2 + (x - at[0]) * k, size.height / 2 - (y - at[1]) * k)
                        for (l in paint.lines) for (i in 0 until l.band.size - 1) {
                            val ax = l.xy[i * 2]; val ay = l.xy[i * 2 + 1]
                            if (kotlin.math.abs(ax - at[0]) > half * 1.5f || kotlin.math.abs(ay - at[1]) > half * 1.5f) continue
                            val a = p(ax, ay); val b = p(l.xy[i * 2 + 2], l.xy[i * 2 + 3])
                            drawLine(if (night) Color(0xFF050A16) else Color.White, a, b, 4.4f * dens, cap = StrokeCap.Round)
                            drawLine(Color(0xFF000000.toInt() or ramp[l.band[i]]), a, b, 2.6f * dens, cap = StrokeCap.Round)
                        }
                        drawCircle(Color.White, 4.5f * dens, Offset(size.width / 2, size.height / 2))
                        drawCircle(Color(0xFF2F6FE4), 3f * dens, Offset(size.width / 2, size.height / 2))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        t?.let { Text(it, fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = if (soft) muted else ink) }
                        ls.forEach { Text(it, fontFamily = Plex, fontSize = 13.5.sp, color = muted) }
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
                Text(if (rtl) "›" else "‹", fontFamily = Plex, fontSize = 20.sp, color = ink)
            }
            val date = DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT).format(Instant.ofEpochMilli(sm.start).atZone(zone))
            Text(stringResource(R.string.app_day_story_title) + " · " + date, Modifier.weight(1f), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink, maxLines = 1)
            if (sample) Text(stringResource(R.string.app_day_sample), Modifier.padding(end = 14.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x33A98BEE)).padding(horizontal = 8.dp, vertical = 2.dp), fontFamily = Plex, fontSize = 12.sp, color = ink)
        }
        // the share: the same picture as today's story
        Text(stringResource(R.string.app_day_share), Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
            .heightIn(min = 52.dp).shadow(12.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(Color(0xFF7C4FE0))
            .clickable(role = Role.Button) { haptics.tick(.4f); Qa.log("redesign day story share ${storyKind(cards.firstOrNull()?.getString("key") ?: "other")}"); sharing = true }
            .padding(vertical = 15.dp), fontFamily = Plex, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
        if (sharing) DayShare(story, sm, paint, hide, track, name, night, sample, haptics, onBack = { sharing = false })
    }
}

package io.github.pini236.skiapp.dayrec.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.dayrec.DayParams
import io.github.pini236.skiapp.i18n.Lang
import io.github.pini236.skiapp.dayrec.Moments
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.ui.Ski
import org.json.JSONObject
import java.io.File
import java.time.ZoneId

/** What the engine chose for a day (the `.moments.json` beside the day): the items, the indexes of the story, the theme. */
class StoryData(val items: List<JSONObject>, val story: List<Int>, val share: Int?, val theme: String?) {
    /** The items of the story card, in its order: up to three insights, then the end of the trip and tomorrow. */
    fun cards(): List<JSONObject> = story.mapNotNull { items.getOrNull(it) }

    companion object {
        fun read(f: File): StoryData? = runCatching {
            val o = JSONObject(f.readText())
            val a = o.getJSONArray("items"); val s = o.getJSONArray("story")
            StoryData((0 until a.length()).map { a.getJSONObject(it) }, (0 until s.length()).map { s.getInt(it) },
                if (o.isNull("share")) null else o.getInt("share"), o.optString("theme").takeIf { o.has("theme") && !o.isNull("theme") && it.isNotEmpty() })
        }.getOrNull()
    }
}

/** The kind of a moment as the measurement may name it: the closed list of docs/GROWTH.md, and `other` for the rest. */
internal fun storyKind(key: String): String = if (key in KINDS) key else "other"
private val KINDS = setOf("fastest", "longest_descent", "first_color", "new_runs", "record", "all_color", "queue", "queue_pattern", "vs_day", "flow",
    "favorite", "time_split", "steepest", "drop_compare", "tomorrow", "trip")

/**
 * "The story of the day" (round 29, the insights): a head for the kind of day over its numbers, up to three insights against
 * the skier's own days, and one idea for tomorrow. Words from the language file; no position, no name of anyone else.
 */
@Composable
fun StoryCard(d: StoryData, s: DayModel.Summary, track: Track? = null, sample: Boolean = false, name: (String) -> String = { it }) {
    val c = Ski.colors
    val ctx = LocalContext.current
    val locale = ctx.resources.configuration.locales[0]
    val zone = ZoneId.of(DayParams.TZ)
    val words = remember(ctx) { MomentText.Words(ctx, locale) }
    val cards = remember(d) { d.cards().filter { it.getString("key") != "fall" && it.getString("key") != "offpiste" } }
    val head = remember(d, s) { MomentText.head(d.theme, s.descents, s.dropM, s.distM, locale) }
    LaunchedEffect(d) { if (!sample) Telemetry.event("story_view", mapOf("moments" to cards.count { it.getString("key") != "tomorrow" && it.getString("key") != "trip" }.coerceAtMost(3), "surface" to "end_of_day")) }
    Column(Modifier.fillMaxWidth().background(c.paper).border(BorderStroke(1.dp, c.rule)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val (title, lines) = words.render(head, Moments.seed("story.head", s.start))
        title?.let { Text(it, style = Ski.type.title.copy(fontSize = 32.sp), color = c.ink) }
        lines.forEach { Text(it, style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted) }
        for (it in cards) {
            val plan = MomentText.plan(it, zone, locale, name = name) ?: continue
            val seed = it.optLong("seed")
            val (t, ls) = words.render(plan, seed)
            if (t == null && ls.isEmpty()) continue
            val soft = it.getString("key") == "tomorrow"
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                t?.let { x -> Text(x, style = Ski.type.bodyBold, color = if (soft) c.muted else c.ink) }
                ls.forEach { x -> Text(x, style = Ski.type.body, color = c.ink) }
            }
        }
        Text(stringResource(R.string.app_day_share), Modifier.fillMaxWidth().border(BorderStroke(1.5.dp, c.ink)).clickable(role = Role.Button) {
            // the picture is drawn off the main thread's frame: a bitmap of a megapixel and a line of the day
            val lang = Lang.current(ctx.resources)
            val cardsOut = cards.filter { it.getString("key") !in StoryImage.KEPT }.take(3).mapNotNull { x ->
                val plan = MomentText.plan(x, zone, locale, name = name) ?: return@mapNotNull null
                val (t, ls) = words.render(plan, x.optLong("seed")); StoryImage.Card(t, ls.firstOrNull())
            }
            val (title, lines) = words.render(head, Moments.seed("story.head", s.start))
            val bmp = StoryImage.draw(title, lines.firstOrNull(), cardsOut, track, ctx.getString(R.string.common_site_name), Lang.typeface(ctx, lang, true), Lang.typeface(ctx, lang, false), lang.rtl)
            StoryImage.share(ctx, bmp, ctx.getString(R.string.common_site_name), ctx.getString(R.string.app_day_share))
            if (!sample) Telemetry.event("story_share", mapOf("method" to "native", "top" to storyKind(cards.firstOrNull { it.getString("key") !in StoryImage.KEPT }?.getString("key") ?: "other")))
        }.heightIn(min = 48.dp).padding(vertical = 12.dp), textAlign = TextAlign.Center, style = Ski.type.bodyBold, color = c.ink)
    }
}

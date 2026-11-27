package io.github.pini236.skiapp.redesign

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.game.Bests
import io.github.pini236.skiapp.game.LESSONS
import io.github.pini236.skiapp.game.RUNGS
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.ui.Icons

/**
 * UX pass, part 6 (boards GA1, GA2): "משחקים" replaces "ערב בבקתה". The first card is the game last played (or the merge
 * game, to start), then the other four in a grid with my best. Only the entry and the list are new; the games and their
 * end screens do not change. The decisions are plain functions in [GamesRules] so a unit test can hold them.
 */
internal object GamesRules {
    val ALL = listOf("merge", "descent", "school", "snowball", "fresh")
    /** With no history: the merge game first, then the order of the board. */
    val FIRST = "merge"
    val DEFAULT_REST = listOf("descent", "school", "snowball", "fresh")

    /** The first game, and the other four: by last played ([last] is the key played last, null with no history). */
    fun order(last: String?): Pair<String, List<String>> {
        val first = last?.takeIf { it in ALL } ?: FIRST
        val rest = if (last == null || last !in ALL) DEFAULT_REST else ALL.filter { it != first }.sortedBy { DEFAULT_REST.indexOf(it).let { i -> if (i < 0) 99 else i } }
        return first to rest
    }
    /** Two columns up to font scale 1.3, then one. */
    fun columns(fontScale: Float): Int = if (fontScale <= 1.3f) 2 else 1
    const val LAST_PREF = "last_played"
}

private fun played(ctx: android.content.Context, game: String): Boolean = when (game) {
    "school" -> (1..LESSONS.size).any { Bests.lesson(ctx, it) > 0 }
    "snowball", "descent" -> Bests.get(ctx, game) > 0 || (1..RUNGS.size).any { Bests.level(ctx, game, it) > 0 }
    else -> Bests.get(ctx, game) > 0
}

private fun nameOf(game: String): Int = when (game) {
    "merge" -> R.string.games_merge_name; "descent" -> R.string.games_descent_name; "school" -> R.string.games_school_name
    "snowball" -> R.string.games_snowball_name; else -> R.string.games_fresh_name
}
private fun tagOf(game: String): Int = when (game) {
    "merge" -> R.string.games_merge_tag; "descent" -> R.string.games_descent_tag; "school" -> R.string.games_school_tag
    "snowball" -> R.string.games_snowball_tag; else -> R.string.games_fresh_tag
}
private fun thumbOf(game: String) = if (game == "fresh") "fresh-snow" else game

/** My best, one line, from the strings the old list used; never played says so. */
@Composable
private fun bestOf(game: String, version: Int): String {
    val ctx = LocalContext.current
    if (version < 0) return ""
    if (!played(ctx, game)) return stringResource(R.string.app_cabin_not_played)
    return when (game) {
        "school" -> {
            val n = (1..LESSONS.size).count { Bests.lesson(ctx, it) > 0 }.coerceAtLeast(1)
            stringResource(R.string.app_cabin_lesson, n.toString(), LESSONS.size.toString())
        }
        "snowball" -> {
            val n = (1..RUNGS.size).count { Bests.level(ctx, game, it) > 0 }.coerceAtLeast(1)
            stringResource(R.string.app_cabin_level, n.toString(), RUNGS.size.toString())
        }
        else -> Bests.get(ctx, game).let { s -> if (s > 0) stringResource(R.string.app_cabin_best, s.toString()) else stringResource(R.string.games_fresh_meta) }
    }
}

@Composable
internal fun GamesPage(mode: DayNight.Mode, haptics: Haptics, onOpen: (String) -> Unit, onTable: (() -> Unit)?, tableName: String?, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val night = remember(mode) { Sky.at(DayNight.lightTime(System.currentTimeMillis(), mode)).dark }
    val k = remember(night) { SheetInk(night) }
    val bg = if (night) Color(0xFF0A1322) else Color(0xFFE9EFF6)
    val tile = if (night) Color(0x12FFFFFF) else Color(0xBDFFFFFF)
    val prefs = remember { ctx.getSharedPreferences("games_page", android.content.Context.MODE_PRIVATE) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { version++ }
    val last = remember(version) { prefs.getString(GamesRules.LAST_PREF, null)?.takeIf { it in GamesRules.ALL } }
    val (first, rest) = remember(last) { GamesRules.order(last) }
    val open = { g: String -> haptics.tick(.3f); prefs.edit().putString(GamesRules.LAST_PREF, g).apply(); onOpen(g) }
    val cols = GamesRules.columns(LocalDensity.current.fontScale)
    val back = stringResource(R.string.app_g_back)

    // the veiled mountain: the sunset picture by day, the night picture at night, under the sheet's veil
    val hero = remember(night) {
        runCatching { ctx.assets.open("redesign/img/hero-${if (night) "night" else "sunset"}.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    Box(Modifier.fillMaxSize().background(bg)) {
        if (hero != null) Image(hero, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(k.veil))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(k.surf.copy(alpha = 1f)).semantics { contentDescription = back; role = Role.Button }
                    .clickable { haptics.tick(.2f); onBack() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.back, null, Modifier.size(22.dp), tint = k.ink)
                }
                Text(stringResource(R.string.app_cabin_title), Modifier.padding(start = 12.dp).weight(1f), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Color.White)
                Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(k.surf).border(1.dp, k.line, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.app_games_offline), fontFamily = Gudi, fontSize = 12.sp, color = k.ink2)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FirstCard(first, last != null, version, k, tile, open)
                rest.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { g -> Box(Modifier.weight(1f)) { GridCard(g, version, k, tile, open) } }
                        if (row.size < cols) repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
                if (onTable != null && tableName != null) {
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(k.surf).border(1.dp, k.line, RoundedCornerShape(24.dp))
                        .semantics { role = Role.Button }.clickable { haptics.tick(.3f); onTable() }, contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.app_cabin_table, tableName), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(game: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val img = remember(game) { runCatching { ctx.assets.open("thumbs/${thumbOf(game)}.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull() }
    if (img != null) Image(img, null, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(Color(0x22000000)))
}

@Composable
private fun FirstCard(game: String, hasLast: Boolean, version: Int, k: SheetInk, tile: Color, open: (String) -> Unit) {
    val name = stringResource(nameOf(game))
    val best = if (hasLast) bestOf(game, version) else stringResource(tagOf(game))
    val line = stringResource(if (hasLast) R.string.app_games_last else R.string.app_games_start_here)
    val a11y = "$name. $best"
    Row(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(tile).border(1.dp, k.line, RoundedCornerShape(20.dp))
        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = a11y }.clickable { open(game) }) {
        // the picture at the start side; mirrored with the layout direction
        Thumb(game, Modifier.width(150.dp).fillMaxSize().clearAndSetSemantics { })
        Column(Modifier.weight(1f).fillMaxSize().padding(14.dp).clearAndSetSemantics { }, verticalArrangement = Arrangement.Center) {
            Text(line, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = k.acc)
            Text(name, Modifier.padding(top = 2.dp), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(best, Modifier.padding(top = 2.dp), fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(top = 8.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(k.acc).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(if (hasLast) R.string.app_games_continue else R.string.app_games_play), fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = k.onAcc)
            }
        }
    }
}

@Composable
private fun GridCard(game: String, version: Int, k: SheetInk, tile: Color, open: (String) -> Unit) {
    val name = stringResource(nameOf(game))
    val best = bestOf(game, version)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tile).border(1.dp, k.line, RoundedCornerShape(20.dp))
        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "$name. $best" }.clickable { open(game) }) {
        Thumb(game, Modifier.fillMaxWidth().height(118.dp).clearAndSetSemantics { })
        Column(Modifier.padding(12.dp).clearAndSetSemantics { }) {
            Text(name, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = k.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(best, Modifier.padding(top = 2.dp), fontFamily = Gudi, fontSize = 12.sp, color = k.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

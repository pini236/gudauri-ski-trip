package io.github.pini236.skiapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Icons
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.qa.Qa
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import io.github.pini236.skiapp.status.LiftStatus
import io.github.pini236.skiapp.status.LiveDot
import io.github.pini236.skiapp.status.summaryText
import java.time.LocalDateTime
import java.time.Month
import kotlin.random.Random

/** Where the home page's signs and buttons lead (MainActivity turns them into routes). */
enum class HomeAction { MAP, MEET, GAMES, GROUP, ABOUT, TRIP, STATUS }

/**
 * The home page (round 10, H1 to H4; decision 36): the sky and the view from the village by the hour in Gudauri,
 * the user's own boarding pass (or an empty one inviting to add it), the state of the season when there is no trip,
 * and the post of signs. Left-to-right languages mirror it (LT1 to LT3): the post on the left, arrows to the right.
 */
@Composable
fun HomeScreen(trip: Trip?, frame: DayNight.Frame, mode: DayNight.Mode, now: LocalDateTime, haptics: Haptics, sounds: Sounds,
               onMode: () -> Unit, status: LiftStatus? = null, onLang: ((String?) -> Unit)? = null,
               /** My first group's name and how many are in it (0: not known yet), for the group sign, as on the site. */
               group: Pair<String, Int>? = null, go: (HomeAction) -> Unit) {
    val c = Ski.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var langs by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(c.snow).verticalScroll(rememberScrollState())) {
        Hero(frame, 250.dp + top)
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxWidth().padding(top = top)) {
            Head(frame, mode, onMode, onLang?.let { { langs = true; Qa.log("lang sheet open") } }) { go(HomeAction.ABOUT) }
            if (trip != null) {
                // with a return pass, its top shows above the outbound one (PASS_PEEK), over the mountains
                Spacer(Modifier.height(if (trip.ret != null) 20.dp else 56.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { TripPass(trip, now, haptics, sounds) { go(HomeAction.TRIP) } }
                Spacer(Modifier.height(38.dp))
            } else {
                Spacer(Modifier.height(58.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { EmptyPass { go(HomeAction.TRIP) } }
                Spacer(Modifier.height(40.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { SeasonBoard(now, status) { go(HomeAction.STATUS) } }
                Spacer(Modifier.height(30.dp))
            }
            SignPost(listOf(
                SignSpec(stringResource(R.string.nav_map), stringResource(R.string.home_board_map_sub), SignColors.blue, Color.White, .90f) { go(HomeAction.MAP) },
                SignSpec(stringResource(R.string.nav_meet), stringResource(R.string.home_board_meet_sub), SignColors.gold, SignColors.ink, .82f) { go(HomeAction.MEET) },
                SignSpec(stringResource(R.string.nav_games), stringResource(R.string.home_board_games_sub), SignColors.green, Color.White, .86f) { go(HomeAction.GAMES) },
                // in a group: its name and how many are in it (the site's groupBoardSub); otherwise the way in
                SignSpec(stringResource(R.string.app_sign_group), group?.let { (name, n) -> if (n > 0) name + " · " + pluralStringResource(R.plurals.group_members_n, n, n) else name }
                    ?: stringResource(R.string.home_board_group_sub), if (c.dark) SignColors.inkNight else SignColors.ink, Color.White, .78f) { go(HomeAction.GROUP) },
            ))
        }
    }
    if (langs && onLang != null) LangSheet({ tag -> langs = false; onLang(tag) }) { langs = false }
    }
}

/** The head over the sky: the place and the time there, the day-and-night button and the way to about and settings. */
@Composable
internal fun Head(frame: DayNight.Frame, mode: DayNight.Mode, onMode: () -> Unit, onLang: (() -> Unit)? = null, onAbout: () -> Unit) {
    val c = Ski.colors
    // a soft halo keeps the words readable over any sky (the site's --sky-halo)
    val halo = Shadow(if (c.dark) Color(0x990D1522) else Color(0x99FFFFFF), blurRadius = 10f)
    val names = mapOf(DayNight.Mode.AUTO to R.string.daynight_mode_auto, DayNight.Mode.DAY to R.string.daynight_mode_day, DayNight.Mode.NIGHT to R.string.daynight_mode_night)
    val modeLabel = stringResource(R.string.daynight_button_aria, stringResource(names.getValue(mode)), stringResource(names.getValue(mode.next())))
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_location), style = Ski.type.brand.copy(shadow = halo), color = c.ink)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.nav_gudauri_time), style = Ski.type.small.copy(fontSize = 12.sp, shadow = halo), color = c.ink)
                Text(frame.clock, style = Ski.type.bodyBold.copy(fontSize = 15.sp, shadow = halo, textDirection = TextDirection.Ltr), color = c.ink)
            }
        }
        if (onLang != null) LangTag(halo, onLang)
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onMode).semantics { contentDescription = modeLabel }, contentAlignment = Alignment.Center) {
            Icon(Icons.dayNight, null, Modifier.size(22.dp), tint = c.ink)
        }
        val about = stringResource(R.string.common_about_settings)
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onAbout).semantics { contentDescription = about }, contentAlignment = Alignment.Center) {
            Icon(Icons.gear, null, Modifier.size(22.dp), tint = c.ink)
        }
    }
}

/**
 * The state of the season, as on the site when there is no report (S3): out of season the mountain sleeps under the
 * snow; in season with no fresh report it says so. With a fresh report (13.4) the snow is gone and it says how many
 * lifts are open (S1's line). A tap opens the board over the map.
 */
@Composable
private fun SeasonBoard(now: LocalDateTime, status: LiftStatus?, onClick: () -> Unit) {
    val c = Ski.colors
    val inSeason = now.month in listOf(Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH, Month.APRIL)
    if (status != null && status.fresh) {
        Column(
            Modifier.fillMaxWidth()
                .shadow(6.dp, RectangleShape, ambientColor = Color(0x1A13233A), spotColor = Color(0x1A13233A))
                .background(c.paper)
                .drawWithContent { drawContent(); drawRect(c.green, Offset.Zero, Size(size.width, 6.dp.toPx())) }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(true)
                Text(stringResource(R.string.status_heading_lift_status), style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
            }
            Text(summaryText(status), Modifier.padding(top = 4.dp), style = Ski.type.small, color = c.muted)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth()
            .shadow(6.dp, RectangleShape, ambientColor = Color(0x1A13233A), spotColor = Color(0x1A13233A))
            .background(c.paper)
            .drawWithCache {
                val snow = snowCap(7, size.width, density)
                onDrawWithContent {
                    drawContent()
                    drawRect(c.dash, Offset.Zero, Size(size.width, 6.dp.toPx()))
                    drawSnow(snow, density)
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(if (inSeason) R.string.status_no_recent_data else R.string.status_mountain_asleep), Modifier.weight(1f),
                style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (30f / 44f)), color = c.ink)
            Text(stringResource(R.string.status_heading_lift_status), style = Ski.type.label, color = c.glacier)
        }
        Text(stringResource(if (inSeason) R.string.status_lead_in_season else R.string.status_lead_off_season), Modifier.padding(top = 4.dp),
            style = Ski.type.small, color = c.muted)
    }
}

/**
 * Fresh snow lying on a top edge, with a few drips (the canvas's cap(), the snowy signs of round 8): soft mounds along
 * the edge, white with a faint blue outline. Fixed by [seed], so it looks the same every time.
 */
fun snowCap(seed: Int, w: Float, d: Float, height: Float = 22f): Path {
    val h = height * d
    val rnd = Random(seed)
    val n = maxOf(4, (w / d / 34).toInt())
    val pts = List(n + 1) { i -> Offset(i * w / n, (6 + rnd.nextFloat() * 8) * d) }
    return Path().apply {
        moveTo(0f, h); lineTo(0f, pts[0].y)
        for (i in 0 until n) {
            val a = pts[i]; val b = pts[i + 1]
            quadraticTo((a.x + b.x) / 2, minOf(a.y, b.y) - (6 + rnd.nextFloat() * 6) * d, b.x, b.y)
        }
        lineTo(w, h - 6 * d)
        var x = w
        while (x > 0) {
            val nx = maxOf(0f, x - (18 + rnd.nextFloat() * 30) * d)
            if (rnd.nextFloat() < .35f && nx > 8 * d) {
                val dx = (x + nx) / 2
                lineTo(dx + 5 * d, h - 6 * d)
                quadraticTo(dx + 4 * d, h + (6 + rnd.nextFloat() * 6) * d, dx, h + (8 + rnd.nextFloat() * 5) * d)
                quadraticTo(dx - 4 * d, h + 6 * d, dx - 5 * d, h - 6 * d)
            }
            quadraticTo((x + nx) / 2, h + (-2 + rnd.nextFloat() * 4) * d, nx, h - 6 * d)
            x = nx
        }
        close()
    }
}

/** The cap sits on the edge: its bottom 8 over the board, the rest above it, with a little shadow. */
fun DrawScope.drawSnow(p: Path, d: Float, height: Float = 22f) {
    translate(0f, -(height - 8) * d) {
        translate(0f, 2 * d) { drawPath(p, Color(0x2E13233A)) }
        drawPath(p, Color.White)
        drawPath(p, Color(0xFFC9D8E8), style = Stroke(1 * d))
    }
}

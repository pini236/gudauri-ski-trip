package io.github.pini236.skiapp.home

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import io.github.pini236.skiapp.ui.Motion
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
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
enum class HomeAction { MAP, MEET, GAMES, GROUP, ABOUT, TRIP, STATUS, PRIVACY }

/**
 * The home page (round 10, H1 to H4; decision 36): the sky and the view from the village by the hour in Gudauri,
 * the user's own boarding pass (or an empty one inviting to add it), the state of the season when there is no trip,
 * and the post of signs. Left-to-right languages mirror it (LT1 to LT3): the post on the left, arrows to the right.
 */
@Composable
fun HomeScreen(trip: Trip?, frame: DayNight.Frame, mode: DayNight.Mode, now: LocalDateTime, haptics: Haptics, sounds: Sounds,
               onMode: () -> Unit, status: LiftStatus? = null, onLang: ((String?) -> Unit)? = null,
               /** My first group's name and how many are in it (0: not known yet), for the group sign, as on the site. */
               group: Pair<String, Int>? = null,
               /** The account on the pass (A-32): null while accounts are not ready; else me (null when not signed in). */
               account: Account? = null, go: (HomeAction) -> Unit) {
    val c = Ski.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var langs by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf(false) }
    val passenger = account?.let { a -> Passenger(a.me?.name ?: a.me?.let { "?" }) { if (a.me == null) a.onSignIn() else who = !who } }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(c.snow).verticalScroll(rememberScrollState())) {
        Hero(frame, 250.dp + top)
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxWidth().padding(top = top)) {
            Head(frame, mode, onMode, onLang?.let { { langs = true; Qa.log("lang sheet open") } }) { go(HomeAction.ABOUT) }
            // the part of the day, the site's chip on the sky ("sunset")
            PhaseChip(frame)
            if (trip != null) {
                // with a return pass, its top shows above the outbound one (PASS_PEEK), over the mountains
                Spacer(Modifier.height(if (trip.ret != null) 0.dp else 30.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    CompositionLocalProvider(LocalPassenger provides passenger) { TripPass(trip, now, haptics, sounds) { go(HomeAction.TRIP) } }
                }
                // tapping my name on the pass (P7): the account and signing out
                val me = account?.me
                if (who && me != null) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    WhoCard(me, onManage = { who = false; account.onManage() }, onSignIn = { who = false; account.onSignIn() },
                        onSignOut = { who = false; account.onSignOut() })
                }
                // the weather on home (round 19, m-6 section 5): during the trip "today on the mountain", with the lift
                // status inside it; before it, once the first ski day is inside the forecast's 15 days, the ski days
                val stage = trip.stage(now)
                val ski = trip.skiDays()
                if (stage is Trip.Stage.SkiDay) {
                    Spacer(Modifier.height(30.dp))
                    Box(Modifier.padding(horizontal = 16.dp)) { io.github.pini236.skiapp.weather.TodayBoard(stage.n, stage.of, status) { go(HomeAction.STATUS) } }
                } else if (stage is Trip.Stage.Before && ski != null && !ski.start.isAfter(now.toLocalDate().plusDays(14))) {
                    Spacer(Modifier.height(30.dp))
                    val days = generateSequence(ski.start) { it.plusDays(1) }.takeWhile { !it.isAfter(ski.endInclusive) }.toList()
                    Box(Modifier.padding(horizontal = 16.dp)) { io.github.pini236.skiapp.weather.AheadBoard(days, stage.days) }
                }
                Spacer(Modifier.height(38.dp))
            } else {
                Spacer(Modifier.height(32.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { EmptyPass { go(HomeAction.TRIP) } }
                Spacer(Modifier.height(40.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { SeasonBoard(now, status) { go(HomeAction.STATUS) } }
                Spacer(Modifier.height(30.dp))
            }
            SignPost(listOf(
                SignSpec(stringResource(R.string.nav_map), stringResource(R.string.home_board_map_sub), c.blue, c.onBoard, .90f) { go(HomeAction.MAP) },
                SignSpec(stringResource(R.string.nav_meet), stringResource(R.string.home_board_meet_sub), SignColors.gold, SignColors.ink, .82f) { go(HomeAction.MEET) },
                SignSpec(stringResource(R.string.nav_games), stringResource(R.string.home_board_games_sub), c.green, c.onBoard, .86f) { go(HomeAction.GAMES) },
                // in a group: its name and how many are in it (the site's groupBoardSub); otherwise the way in
                SignSpec(stringResource(R.string.app_sign_group), group?.let { (name, n) -> if (n > 0) name + " · " + pluralStringResource(R.plurals.group_members_n, n, n) else name }
                    ?: stringResource(R.string.home_board_group_sub), c.ink, c.paper, .78f) { go(HomeAction.GROUP) },
            ))
            Tally()
            // the site's two links at the bottom of home
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 24.dp)) {
                HomeLink(stringResource(R.string.home_about_link)) { go(HomeAction.ABOUT) }
                HomeLink(stringResource(R.string.home_privacy_link)) { go(HomeAction.PRIVACY) }
            }
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
    // the site's phone head (.home-top, round 18): the place in the text face, then the time there in the sign voice
    // under its words (.dn-clock), and the buttons together after it
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.home_location), Modifier.weight(1f), style = Ski.type.bodyBold.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = halo), color = c.ink)
        Column(Modifier.padding(end = 6.dp), horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.nav_gudauri_time), style = Ski.type.small.copy(fontSize = 11.sp, lineHeight = 13.sp, shadow = halo), color = c.muted)
            Text(frame.clock, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (22f / 44f), shadow = halo, textDirection = TextDirection.Ltr), color = c.ink)
        }
        if (onLang != null) LangTag(halo, onLang)
        DayNightButton(mode, modeLabel, onMode)
        val about = stringResource(R.string.common_about_settings)
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onAbout).semantics { contentDescription = about }, contentAlignment = Alignment.Center) {
            Icon(Icons.gear, null, Modifier.size(22.dp), tint = c.ink)
        }
    }
}

/**
 * The day-and-night button as on the site (.dn-btn in the home head, P-K2): a round 44 in paper with an ink ring, and
 * the mode it is in: half a circle for auto, the gold sun for day, the crescent for night. A new mode turns in, as the
 * site's dnin, and simply shows with reduced motion.
 */
@Composable
private fun DayNightButton(mode: DayNight.Mode, label: String, onMode: () -> Unit) {
    val c = Ski.colors
    val still = Motion.reduced(LocalContext.current)
    val turn = remember { Animatable(1f) }
    LaunchedEffect(mode) { if (!still) { turn.snapTo(0f); turn.animateTo(1f, tween(500, easing = CubicBezierEasing(.3f, 1.4f, .5f, 1f))) } }
    Box(Modifier.padding(horizontal = 4.dp).size(44.dp).clip(CircleShape).background(c.paper).border(1.5.dp, c.ink, CircleShape)
        .clickable(role = Role.Button, onClick = onMode).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        val (icon, tint) = when (mode) {
            DayNight.Mode.AUTO -> Icons.dayNight to c.ink
            DayNight.Mode.DAY -> Icons.sun to SUN
            DayNight.Mode.NIGHT -> Icons.moon to c.ink
        }
        Icon(icon, null, Modifier.size(24.dp).graphicsLayer {
            val t = turn.value
            rotationZ = -90f * (1f - t); scaleX = .4f + .6f * t; scaleY = scaleX; alpha = t.coerceIn(0f, 1f)
        }, tint = tint)
    }
}

/** The sun's gold on the site's day button (#F4B942), the same by day and by night. */
private val SUN = Color(0xFFF4B942)

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
                // the site's #seasonBoard: data-snow="7" data-snow-pile over its 6 top border
                val snow = snowCap(7, size.width, density, SnowKind.PILE, border = 6f); val paint = SnowPaint(c, snow)
                onDrawWithContent {
                    drawContent()
                    drawRect(c.dash, Offset.Zero, Size(size.width, 6.dp.toPx()))
                    drawSnow(snow, paint)
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

/** What home needs of the account (A-32): me (null: not signed in), and where the pass's name leads. */
class Account(val me: io.github.pini236.skiapp.group.Me?, val onManage: () -> Unit, val onSignIn: () -> Unit, val onSignOut: () -> Unit)

/** The part of the day over the sky (the site's .sky-phase): a small chip at the start, under the place. */
@Composable
private fun PhaseChip(frame: DayNight.Frame) {
    val c = Ski.colors
    val res = when (frame.phase) {
        "sunrise" -> R.string.daynight_phase_sunrise; "golden_hour" -> R.string.daynight_phase_golden_hour; "sunset" -> R.string.daynight_phase_sunset
        "twilight" -> R.string.daynight_phase_twilight; "night" -> R.string.daynight_phase_night; else -> R.string.daynight_phase_day
    }
    Text(stringResource(res), Modifier.padding(start = 16.dp, top = 8.dp).background(if (c.dark) Color(0x8C0D1522) else Color(0x8CFFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp),
        style = Ski.type.bodyBold.copy(fontSize = 12.5.sp), color = c.ink)
}

/** The runs on the official map by colour (the site's .tally): a coloured rule, the number, the colour. */
@Composable
private fun Tally() {
    val c = Ski.colors
    // the counts the site shows (site/index.html), from MTA's official map, not from the lines we have
    val counts = listOf("green" to 5, "blue" to 16, "red" to 4, "black" to 2)
    val names = mapOf("green" to R.string.common_color_green, "blue" to R.string.common_color_blue, "red" to R.string.common_color_red, "black" to R.string.common_color_black)
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 56.dp).semantics(mergeDescendants = true) {}) {
        Text(stringResource(R.string.home_tally), Modifier.padding(bottom = 10.dp), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((col, n) in counts) Column(Modifier.weight(1f).drawBehind { drawRect(c.run(col), size = androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx())) }.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(n.toString(), style = Ski.type.title.copy(fontSize = 38.sp, lineHeight = 34.sp), color = c.ink)
                Text(stringResource(names.getValue(col)), style = Ski.type.small.copy(fontSize = 13.sp), color = c.muted)
            }
        }
    }
}

@Composable
private fun HomeLink(text: String, onClick: () -> Unit) =
    Text(text, Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        style = Ski.type.small.copy(fontSize = 13.5.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), color = Ski.colors.muted)

/**
 * The card under the pass when I tap my name (P7, round 12): who I am and how I signed in, the account, and signing out
 * (a guest in a group has no way back in: "sign in" instead, to keep the place).
 */
@Composable
private fun WhoCard(me: io.github.pini236.skiapp.group.Me, onManage: () -> Unit, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val c = Ski.colors
    Column(Modifier.fillMaxWidth().shadow(14.dp).background(c.paper).drawBehind { drawRect(c.ink, size = androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx())) }
        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            io.github.pini236.skiapp.group.Avatar(me.name ?: "?", true, 44)
            Column {
                Text(me.name ?: "?", style = Ski.type.title.copy(fontSize = 30.sp, lineHeight = 30.sp), color = c.ink)
                Text(stringResource(if (me.registered) R.string.acct_signed_google else R.string.acct_signed_guest), style = Ski.type.small.copy(fontSize = 12.5.sp), color = c.muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.acct_manage), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onManage).padding(vertical = 12.dp),
                style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.glacier)
            if (me.registered) Text(stringResource(R.string.acct_sign_out_short), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onSignOut).padding(vertical = 12.dp),
                style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.ink)
            else Text(stringResource(R.string.acct_signin_title), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onSignIn).padding(vertical = 12.dp),
                style = Ski.type.bodyBold.copy(fontSize = 14.sp), color = c.glacier)
        }
    }
}

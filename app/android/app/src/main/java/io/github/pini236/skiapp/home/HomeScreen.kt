package io.github.pini236.skiapp.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Sounds
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.trip.Trip
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.SkiTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What the home page can open. Places that are not built yet in the app (meeting point, group) have no sign yet. */
class HomeActions(val addTrip: () -> Unit, val editTrip: () -> Unit, val map: () -> Unit, val games: () -> Unit)

/**
 * The home page of the app (round 10: H1 without a trip, H3 by day and H4 at night with one). The view from the
 * village at the time in Gudauri (the site's seven pictures), the boarding pass of the user's own trip or a blank one
 * waiting to be filled, the state of the season, and the trail signs. Dark at night in Gudauri, as on the site.
 */
@Composable
fun HomeScreen(trip: Trip?, haptics: Haptics, sounds: Sounds, actions: HomeActions, nowMs: Long? = null) {
    var now by remember { mutableLongStateOf(nowMs ?: System.currentTimeMillis()) }
    LaunchedEffect(nowMs) { if (nowMs == null) while (true) { delay(30_000); now = System.currentTimeMillis() } else now = nowMs }
    val pano = Sky.pano(now)
    SkiTheme(dark = pano.dark) {
        val c = Ski.colors
        val bg = c.snow
        Box(Modifier.fillMaxSize().background(bg)) {
            PanoView(pano, Modifier.fillMaxWidth().height(260.dp))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
                Header(Sky.clock(now), pano.dark)
                Spacer(Modifier.height(54.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (trip == null) EmptyPass(bg, actions.addTrip)
                    else TripPass(trip, trip.daysTo(LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())), bg, haptics, sounds, actions.editTrip)
                    if (trip == null) { Spacer(Modifier.height(36.dp)); SeasonBoard(actions.map) }
                }
                Spacer(Modifier.height(30.dp))
                SignPost(Modifier.fillMaxWidth()) {
                    TrailSign(stringResource(R.string.nav_map), stringResource(R.string.home_board_map_sub), c.blue, c.onBoard, .9f, actions.map)
                    TrailSign(stringResource(R.string.nav_games), stringResource(R.string.home_board_games_sub), c.green, c.onBoard, .86f, actions.games)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** The two pictures either side of now, cross-faded (assets/pano, from site/img/pano). */
@Composable
private fun PanoView(p: Sky.Pano, modifier: Modifier) {
    val ctx = LocalContext.current
    fun load(name: String): ImageBitmap? = runCatching { ctx.assets.open("pano/pano-$name.webp").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
    val a = remember(p.from) { load(p.from) }
    val b = remember(p.to) { load(p.to) }
    Box(modifier) {
        a?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter) }
        b?.let { Image(it, null, Modifier.fillMaxSize().alpha(p.mix), contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter) }
        // the view melts into the page below
        Box(Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Ski.colors.snow))))
    }
}

@Composable
private fun Header(clock: String, dark: Boolean) {
    val ink = if (dark) Color(0xFFEAF0F7) else Ski.colors.ink
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(stringResource(R.string.home_location), style = Ski.type.brand, color = ink)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.nav_gudauri_time) + " ", style = Ski.type.small.copy(fontSize = 12.sp), color = ink)
                Text(clock, style = Ski.type.bodyBold, color = ink)
            }
        }
    }
}

/** The state of the season, as on the site: no lift report yet, so the board sleeps under the snow (S3). */
@Composable
private fun SeasonBoard(openMap: () -> Unit) {
    val c = Ski.colors
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(c.paper)) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(c.dash))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.status_mountain_asleep), Modifier.weight(1f), style = Ski.type.title.copy(fontSize = 30.sp), color = c.ink)
                    Text(stringResource(R.string.app_home_season_link), Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = openMap).padding(start = 8.dp, top = 14.dp), style = Ski.type.label, color = c.glacier)
                }
                Text(stringResource(R.string.status_lead_off_season), Modifier.padding(top = 4.dp), style = Ski.type.small, color = c.muted)
            }
        }
        SnowCap(7, Modifier.fillMaxWidth().height(40.dp).offset(y = (-22).dp))
    }
}

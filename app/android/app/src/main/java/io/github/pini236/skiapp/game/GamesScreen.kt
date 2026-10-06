package io.github.pini236.skiapp.game

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.home.SignColors
import io.github.pini236.skiapp.home.SignShape
import io.github.pini236.skiapp.home.drawSnow
import io.github.pini236.skiapp.home.snowCap
import io.github.pini236.skiapp.home.SnowKind
import io.github.pini236.skiapp.home.SnowPaint
import io.github.pini236.skiapp.ui.Motion
import io.github.pini236.skiapp.ui.Ski
import io.github.pini236.skiapp.ui.TopBar

/**
 * A game on the games page, as the site lists it (site/index.html, #gamesPage): its trail colour, dark words on the light
 * colours, its place in the site's order (which sets how far its sign reaches), and the first one wider.
 */
class GameCard(val key: String, val order: Int, val color: Color, val ink: Boolean, val name: String, val thumb: String = key)

/** The games the app has (13.6), in the site's order; round 20: their names and pictures, no tag or label. */
@Composable
fun appGames(): List<GameCard> = listOf(
    GameCard("descent", 0, SignColors.blue, false, stringResource(R.string.games_descent_name)),
    GameCard("school", 1, SignColors.green, false, stringResource(R.string.games_school_name)),
    GameCard("fresh", 2, Color(0xFF5B9BFF), true, stringResource(R.string.games_fresh_name), thumb = "fresh-snow"),
    GameCard("merge", 3, SignColors.gold, true, stringResource(R.string.games_merge_name)),
    GameCard("snowball", 4, Color(0xFF13233A), false, stringResource(R.string.games_snowball_name)),
)

// how far each sign reaches from the post, by its place in the site's list (.games-list li:nth-child)
private val REACH = floatArrayOf(.97f, .90f, .84f, .90f, .80f)

/**
 * The games page (13.6), the site's #games of round 8 (GP2, decision 20): every game a trail sign in its colour on one
 * post, fresh snow on each, its picture with a play mark, and the sign leans a little under the finger.
 */
@Composable
fun GamesScreen(games: List<GameCard>, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val c = Ski.colors
    Column(Modifier.fillMaxSize().background(c.snow).statusBarsPadding()) {
        TopBar(stringResource(R.string.nav_games), stringResource(R.string.nav_home), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 32.dp)) {
            // round 20: no opening line, only the signs and their pictures
            GamePost(games, onOpen)
        }
    }
}

@Composable
private fun GamePost(games: List<GameCard>, onOpen: (String) -> Unit) {
    val post = Ski.colors.ink
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .drawBehind {
                // the post at the start side, with a cap of snow (.games-list::before and ::after)
                val x = if (rtl) size.width - 10.dp.toPx() - 10.dp.toPx() else 10.dp.toPx()
                drawRect(post, Offset(x, 6.dp.toPx()), Size(10.dp.toPx(), size.height - 6.dp.toPx()))
                val cx = if (rtl) size.width - 5.dp.toPx() - 20.dp.toPx() else 5.dp.toPx()
                drawRoundRect(Color(0x40000000), Offset(cx, 0f), Size(20.dp.toPx(), 14.dp.toPx()), CornerRadius(8.dp.toPx()))
                drawRoundRect(Color.White, Offset(cx, -2.dp.toPx()), Size(20.dp.toPx(), 14.dp.toPx()), CornerRadius(8.dp.toPx()))
            }
            .padding(start = 34.dp, top = 30.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        games.forEachIndexed { i, g -> GameSign(g, i, feature = g.order == 0) { onOpen(g.key) } }
    }
}

@Composable
private fun GameSign(g: GameCard, index: Int, feature: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val snowColors = Ski.colors
    val still = remember { Motion.reduced(context) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shape = remember { SignShape(30.dp) }
    val fg = if (g.ink) Color(0xFF13233A) else Color.White
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // the sign swings on the post a little under the finger (the site's :active), not with reduced motion
    val tilt by animateFloatAsState(if (pressed && !still) (if (rtl) -.6f else .6f) else 0f, spring(dampingRatio = .45f, stiffness = 600f), label = "tilt")
    val thumb = remember(g.thumb) {
        runCatching { context.assets.open("thumbs/${g.thumb}.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }
    Box(
        Modifier.fillMaxWidth(REACH[g.order.coerceIn(0, REACH.size - 1)]).height(if (feature) 122.dp else 96.dp)
            .graphicsLayer { rotationZ = tilt; transformOrigin = TransformOrigin(if (rtl) 1f else 0f, .5f) }
            // fresh snow on top, up to where the arrow starts (js/snow.js: data-snow, data-snow-arrow 30)
            .drawWithCache {
                val snow = snowCap(index + 7, size.width, density, arrow = 30f, rtl = rtl); val paint = SnowPaint(snowColors, snow)
                onDrawWithContent { drawContent(); drawSnow(snow, paint) }
            }
    ) {
        Row(
            Modifier.fillMaxSize()
                .shadow(6.dp, shape, ambientColor = Color(0x3313233A), spotColor = Color(0x3313233A))
                .background(g.color, shape)
                .clickable(press, indication = null, role = Role.Button, onClick = onClick),
        ) {
            Thumb(thumb, if (feature) 124.dp else 96.dp, rtl)
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp, end = 32.dp, top = 8.dp), verticalArrangement = Arrangement.Center) {
                // round 20: the name only, no line under it
                Text(g.name, style = Ski.type.title.copy(fontSize = if (feature) 29.sp else 25.sp, lineHeight = if (feature) 28.sp else 24.sp), color = fg,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The game's picture and a play mark pointing the way of reading (round 20: no label on it). */
@Composable
private fun Thumb(img: ImageBitmap?, w: androidx.compose.ui.unit.Dp, rtl: Boolean) {
    Box(Modifier.width(w).fillMaxHeight().background(Color(0xFFD5DFE8))
        .drawWithCache { onDrawWithContent { drawContent(); val x = if (rtl) 0f else size.width - 4.dp.toPx(); drawRect(Color(0x8CFFFFFF), Offset(x, 0f), Size(4.dp.toPx(), size.height)) } }) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Canvas(Modifier.size(28.dp).align(Alignment.Center)) {
            val r = size.width / 2
            drawCircle(Color(0xD113233A), r)
            val k = size.width / 34f
            // the site's triangle (M21.5 11.5 v11 L12.5 17), its point at the end of the reading direction
            val p = Path().apply {
                if (rtl) { moveTo(21.5f * k, 11.5f * k); lineTo(21.5f * k, 22.5f * k); lineTo(12.5f * k, 17f * k) }
                else { moveTo(12.5f * k, 11.5f * k); lineTo(12.5f * k, 22.5f * k); lineTo(21.5f * k, 17f * k) }
                close()
            }
            drawPath(p, Color.White)
        }
    }
}

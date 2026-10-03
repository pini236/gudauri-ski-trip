package io.github.pini236.skiapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.github.pini236.skiapp.i18n.Lang
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The design tokens of the site (site/css/site.css, :root and its dark twin), for the skeleton's screens (stage 13.2).
 * One place, two palettes: day and night. A token here has the same name as on the site, so a change there is easy
 * to carry over. The spike's screens still use [Palette] (day only) until they are rebuilt.
 */
@Immutable
data class SkiColors(
    val snow: Color, val paper: Color, val ink: Color, val muted: Color, val rule: Color, val glacier: Color,
    val grid: Color, val lift: Color, val casing: Color,
    val green: Color, val blue: Color, val red: Color, val black: Color, val onBoard: Color,
    val accent: Color, val onAccent: Color, val dash: Color,
    val sky1: Color, val sky2: Color,
    val water: Color, val road: Color, val contour: Color, val peak: Color,
    // the map drawn flat (the meeting point): the site's --water-edge, --road-main, --vill, --contour-i
    val waterEdge: Color, val roadMain: Color, val village: Color, val contourI: Color,
    val shadow: Color,
    // the boarding pass (--bp-*)
    val bpPaper: Color, val bpPaper2: Color, val bpInk: Color, val bpMuted: Color, val bpStrip: Color, val bpOnStrip: Color, val bpAccent: Color,
    val dark: Boolean,
) {
    /** The colour of a run on the official map (the accuracy rules: colours always as MTA's map). */
    fun run(color: String) = when (color) { "green" -> green; "blue" -> blue; "red" -> red; else -> black }
}

val DayColors = SkiColors(
    snow = Color(0xFFEEF2F5), paper = Color(0xFFFFFFFF), ink = Color(0xFF13233A), muted = Color(0xFF4B5A6F), rule = Color(0xFFCBD5DF), glacier = Color(0xFF1F5FC4),
    grid = Color(0xFFDCE4EC), lift = Color(0xFF3A4556), casing = Color(0xFFFFFFFF),
    green = Color(0xFF1B8A4C), blue = Color(0xFF1F5FC4), red = Color(0xFFD1342B), black = Color(0xFF13233A), onBoard = Color(0xFFFFFFFF),
    accent = Color(0xFF1F5FC4), onAccent = Color(0xFFFFFFFF), dash = Color(0xFF8E9CAD),
    sky1 = Color(0xFFDCE8F1), sky2 = Color(0xFFEEF2F5),
    water = Color(0xFF8DB6D8), road = Color(0xFFA49A91), contour = Color(0xFF8193A8), peak = Color(0xFF3D4A5C),
    waterEdge = Color(0xFF5F8FBB), roadMain = Color(0xFF8A7C70), village = Color(0xFFE3D8CB), contourI = Color(0xFF62758C),
    shadow = Color(0x2E13233A),
    bpPaper = Color(0xFFFFFFFF), bpPaper2 = Color(0xFFF4F7FA), bpInk = Color(0xFF13233A), bpMuted = Color(0xFF4B5A6F), bpStrip = Color(0xFF1F5FC4), bpOnStrip = Color(0xFFFFFFFF), bpAccent = Color(0xFF1F5FC4),
    dark = false,
)

val NightColors = SkiColors(
    snow = Color(0xFF0D1522), paper = Color(0xFF16223A), ink = Color(0xFFEAF0F7), muted = Color(0xFFA3B3C8), rule = Color(0xFF2A3B55), glacier = Color(0xFF8DB9FF),
    grid = Color(0xFF18263A), lift = Color(0xFFC9D3E0), casing = Color(0xFF0D1522),
    green = Color(0xFF3CC47C), blue = Color(0xFF5B9BFF), red = Color(0xFFFF6A5F), black = Color(0xFFEAF0F7), onBoard = Color(0xFF0D1522),
    accent = Color(0xFFF4B942), onAccent = Color(0xFF0D1522), dash = Color(0xFF51627D),
    sky1 = Color(0xFF0B1320), sky2 = Color(0xFF16223A),
    water = Color(0xFF2C5A82), road = Color(0xFF6D6760), contour = Color(0xFF5A6D85), peak = Color(0xFFC9D3E0),
    waterEdge = Color(0xFF4D82B3), roadMain = Color(0xFF8F857B), village = Color(0xFF3A352F), contourI = Color(0xFF7A90AA),
    shadow = Color(0x73000000),
    bpPaper = Color(0xFF16223A), bpPaper2 = Color(0xFF1B2944), bpInk = Color(0xFFFFD98A), bpMuted = Color(0xFFC9B98F), bpStrip = Color(0xFFF4B942), bpOnStrip = Color(0xFF0D1522), bpAccent = Color(0xFFFFD98A),
    dark = true,
)

/** Type: Karantina for display (700, the trail-sign voice), IBM Plex Sans Hebrew for text. Sizes as on the site. */
@Immutable
data class SkiType(
    /** per language (i18n/Lang.kt): Karantina and Plex have Hebrew and Latin only */
    val display: FontFamily = Karantina,
    val text: FontFamily = Plex,
    /** Oswald and the narrow Georgian set bigger than Karantina at the same size (i18n/Lang.kt) */
    val displayScale: Float = 1f,
    val brand: TextStyle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = (34 * displayScale).sp, lineHeight = 1.em),
    val sign: TextStyle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = (26 * displayScale).sp, lineHeight = 1.em),
    val title: TextStyle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = (44 * displayScale).sp, lineHeight = 1.em),
    val number: TextStyle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = (22 * displayScale).sp, lineHeight = 1.em),
    val body: TextStyle = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 1.5.em),
    val bodyBold: TextStyle = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 1.5.em),
    val small: TextStyle = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 1.4.em),
    val label: TextStyle = TextStyle(fontFamily = text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 1.2.em),
)

/** Sizes the site's rules fix: touch targets of at least 44, square corners, the sign's arrow notch. */
object Dimens {
    val touch = 44.dp
    val chip = 44.dp
    val gutter = 16.dp
    val signNotch = 14.dp
    val ticketTilt = 2f // degrees, the boarding pass on the home page
}

private val LocalColors = staticCompositionLocalOf { DayColors }
private val LocalType = staticCompositionLocalOf { SkiType() }

/** The app's theme: day or night by the system setting (or the user's choice, later), and the language's fonts and direction. */
@Composable
fun SkiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val lang = Lang.current(LocalContext.current.resources)
    CompositionLocalProvider(
        LocalColors provides if (dark) NightColors else DayColors,
        LocalType provides SkiType(display = Lang.display(lang), text = Lang.body(lang), displayScale = Lang.displayScale(lang)),
        LocalLayoutDirection provides lang.direction,
        content = content,
    )
}

object Ski {
    val colors: SkiColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: SkiType @Composable @ReadOnlyComposable get() = LocalType.current
}

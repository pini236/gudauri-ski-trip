package io.github.pini236.skiapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.pini236.skiapp.R

/** The spike's day-only colours, now read from the tokens (Tokens.kt). The skeleton's screens use [Ski.colors]. */
object Palette {
    val snow = DayColors.snow
    val paper = DayColors.paper
    val ink = DayColors.ink
    val muted = DayColors.muted
    val rule = DayColors.rule
    val glacier = DayColors.glacier
    val green = DayColors.green
    val blue = DayColors.blue
    val red = DayColors.red
    val black = DayColors.black
    val sky1 = DayColors.sky1
    val sky2 = DayColors.sky2
    val ticketPaper = Color(0xFFFBF8F2)

    fun run(color: String) = DayColors.run(color)
}

val Karantina = FontFamily(Font(R.font.karantina_bold, FontWeight.Bold))
val Plex = FontFamily(
    Font(R.font.plex_hebrew_regular, FontWeight.Normal),
    Font(R.font.plex_hebrew_bold, FontWeight.Bold),
)

package io.github.pini236.skiapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.pini236.skiapp.R

/** The site's tokens (site/css/site.css, :root). */
object Palette {
    val snow = Color(0xFFEEF2F5)
    val paper = Color(0xFFFFFFFF)
    val ink = Color(0xFF13233A)
    val muted = Color(0xFF4B5A6F)
    val rule = Color(0xFFCBD5DF)
    val glacier = Color(0xFF1F5FC4)
    val green = Color(0xFF1B8A4C)
    val blue = Color(0xFF1F5FC4)
    val red = Color(0xFFD1342B)
    val black = Color(0xFF13233A)
    val sky1 = Color(0xFFDCE8F1)
    val sky2 = Color(0xFFEEF2F5)
    val ticketPaper = Color(0xFFFBF8F2)

    fun run(color: String) = when (color) {
        "green" -> green
        "blue" -> blue
        "red" -> red
        else -> black
    }
}

val Karantina = FontFamily(Font(R.font.karantina_bold, FontWeight.Bold))
val Plex = FontFamily(
    Font(R.font.plex_hebrew_regular, FontWeight.Normal),
    Font(R.font.plex_hebrew_bold, FontWeight.Bold),
)

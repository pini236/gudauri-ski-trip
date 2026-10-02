package io.github.pini236.skiapp.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The line icons of the canvas (design/round10/build.py, icon()): 24-unit grid, stroke 2, square ends. Drawn the way
 * they read left to right; the ones that point somewhere (back, the plane) mirror themselves in Hebrew (autoMirror).
 * Tinted by the caller (Icon's tint), so one icon serves day, night and the pass's gold.
 */
object Icons {
    private fun line(name: String, mirror: Boolean, vararg d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
        for (p in d) addPath(PathParser().parsePathString(p).toNodes(), stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter)
    }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

    val gear = line("gear", false, circle(12f, 12f, 3.2f), "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1")
    /** Back: points against the reading direction (left in English, right in Hebrew). */
    val back = line("back", true, "M19 12H5", "M11 6l-6 6 6 6")
    /** Forward: on in the reading direction (right in English, left in Hebrew). */
    val forward = line("forward", true, "M5 12h14", "M13 6l6 6-6 6")
    val edit = line("edit", false, "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val plus = line("plus", false, "M12 5v14M5 12h14")
    val check = line("check", false, "M4 12.5l5 5L20 6.5")
    val lock = line("lock", false, "M5 10.5h14v10H5z", "M8 10.5V7.5a4 4 0 0 1 8 0v3")
    val phone = line("phone", false, "M7 2.5h10v19H7z", "M11 18.5h2")
    val people = line("people", false, circle(9f, 8.5f, 3.2f), "M3 20c0-3.6 2.7-6 6-6s6 2.4 6 6", circle(17f, 9.5f, 2.5f), "M16 14.2c3 .2 5 2.4 5 5.8")
    val cloud = line("cloud", false, "M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 9.5 4.3 4.3 0 0 0 7 18z")
    val trash = line("trash", false, "M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13")
    val key = line("key", false, circle(8f, 15f, 4f), "M11 12l9-9M16.5 6.5l2.5 2.5M14 9l2 2")
    val link = line("link", false, "M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1", "M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1")
    val copy = line("copy", false, "M8 8h12v12H8z", "M4 16V4h12")
    val share = line("share", false, circle(6f, 12f, 2.5f), circle(18f, 6f, 2.5f), circle(18f, 18f, 2.5f), "M8.2 10.8l7.6-3.6M8.2 13.2l7.6 3.6")
    val out = line("out", true, "M10 4H4v16h6", "M14 12h7M17.5 8.5L21 12l-3.5 3.5")
    val bell = line("bell", false, "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0")
    val pin = line("pin", false, "M12 21s-6.5-6.4-6.5-11.2a6.5 6.5 0 0 1 13 0C18.5 14.6 12 21 12 21z", circle(12f, 9.8f, 2.4f))
    val clock = line("clock", false, circle(12f, 12f, 9f), "M12 7v5l3.5 2")
    val calendar = line("calendar", false, "M4 6h16v14H4z", "M4 10h16M8 3.5v4M16 3.5v4")
    val search = line("search", false, circle(10.5f, 10.5f, 6.5f), "M15.5 15.5L20 20")
    val trophy = line("trophy", false, "M8 4h8v5a4 4 0 0 1-8 0z", "M8 6H4.5a3 3 0 0 0 3.5 4M16 6h3.5a3 3 0 0 1-3.5 4M12 13v4M8.5 20.5h7M10 17h4")
    val x = line("x", false, "M6 6l12 12M18 6L6 18")
    val more = ImageVector.Builder("more", 24.dp, 24.dp, 24f, 24f).apply {
        for (y in listOf(5f, 12f, 19f)) addPath(PathParser().parsePathString(circle(12f, y, 1.6f)).toNodes(), fill = SolidColor(androidx.compose.ui.graphics.Color.Black))
    }.build()

    /** Day and night: a circle, half of it filled (the canvas's button). */
    val dayNight = ImageVector.Builder("daynight", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(circle(12f, 12f, 9f)).toNodes(), stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 1.8f)
        path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) { moveTo(12f, 3f); arcTo(9f, 9f, 0f, false, true, 12f, 21f); close() }
    }.build()

    /** The plane of the boarding pass, nose forward: right in English, left in Hebrew (from TLV to TBS). */
    val plane = ImageVector.Builder("plane", 24.dp, 24.dp, 24f, 24f, autoMirror = true).apply {
        group(rotate = 90f, pivotX = 12f, pivotY = 12f) {
            addPath(PathParser().parsePathString("M2 13.5v-2l8-4.5V2.5a1.5 1.5 0 0 1 3 0V7l8 4.5v2l-8-2.5v5l2.5 2v1.5L12 18.5 8.5 19.5V18l2.5-2v-5z").toNodes(),
                fill = SolidColor(androidx.compose.ui.graphics.Color.Black))
        }
    }.build()
}

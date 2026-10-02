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
    val edit = line("edit", false, "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")
    val plus = line("plus", false, "M12 5v14M5 12h14")
    val check = line("check", false, "M4 12.5l5 5L20 6.5")
    val lock = line("lock", false, "M5 10.5h14v10H5z", "M8 10.5V7.5a4 4 0 0 1 8 0v3")
    val phone = line("phone", false, "M7 2.5h10v19H7z", "M11 18.5h2")

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

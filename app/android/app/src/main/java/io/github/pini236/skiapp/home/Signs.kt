package io.github.pini236.skiapp.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.ui.Ski

/** The trail sign's board: square corners and an arrow at the end of the reading direction (left in Hebrew, right in English; LT1 to LT3). */
class SignShape(private val arrow: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = with(density) { arrow.toPx() }
        val p = Path()
        if (layoutDirection == LayoutDirection.Rtl) {
            p.moveTo(0f, size.height / 2); p.lineTo(a, 0f); p.lineTo(size.width, 0f); p.lineTo(size.width, size.height); p.lineTo(a, size.height)
        } else {
            p.moveTo(0f, 0f); p.lineTo(size.width - a, 0f); p.lineTo(size.width, size.height / 2); p.lineTo(size.width - a, size.height); p.lineTo(0f, size.height)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** The sign colours are fixed, day and night, as in the canvas (H3, H4): the trail colours, and the group in ink. */
object SignColors {
    val blue = Color(0xFF1F5FC4)
    val gold = Color(0xFFF4B942)
    val green = Color(0xFF1B8A4C)
    val ink = Color(0xFF13233A)
    val inkNight = Color(0xFF2C3E5C)
}

class SignSpec(val title: String, val sub: String, val color: Color, val fg: Color, val width: Float, val onClick: () -> Unit)

/**
 * The post with its signs (the site's home, H1 and H3): a dark post at the start side, the signs hanging from it
 * and pointing on. The post runs down past the last sign, to the bottom of the page.
 */
@Composable
fun SignPost(signs: List<SignSpec>, modifier: Modifier = Modifier) {
    val postColor = Ski.colors.ink
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val px = with(LocalDensity.current) { 20.dp.toPx() }
    Column(
        modifier.fillMaxWidth()
            .drawBehind {
                val x = if (rtl) size.width - px - 6.dp.toPx() else px
                // on past the last sign, down to the bottom of the screen (the scrolling page clips it there)
                drawRect(postColor, Offset(x, -8.dp.toPx()), Size(6.dp.toPx(), size.height + 2000.dp.toPx()))
            }
            .padding(start = 30.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (s in signs) Sign(s)
    }
}

@Composable
private fun Sign(s: SignSpec) {
    val shape = remember { SignShape(26.dp) }
    Box(
        Modifier.fillMaxWidth(s.width)
            .shadow(5.dp, shape, ambientColor = Color(0x2913233A), spotColor = Color(0x2913233A))
            .background(s.color, shape)
            .clickable(role = Role.Button, onClick = s.onClick)
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 40.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column {
            Text(s.title, style = Ski.type.title.copy(fontSize = Ski.type.title.fontSize * (40f / 44f)), color = s.fg, maxLines = 1)
            Text(s.sub, style = Ski.type.small.copy(fontSize = 13.sp), color = s.fg)
        }
    }
}

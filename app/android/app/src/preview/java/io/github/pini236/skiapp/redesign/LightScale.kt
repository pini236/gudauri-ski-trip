package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Plex

/**
 * The speed light's scale (boards L06, L07, L09): "slow", the five colours, "fast"; on the ridge also a dotted piece
 * for the rides, named. The gradient runs from the reading start, so in Hebrew slow is on the right.
 */
@Composable
internal fun LightScale(night: Boolean, muted: Color, width: Dp = 44.dp, lift: Boolean = false, modifier: Modifier = Modifier) {
    val ramp = if (night) DayPaint.NIGHT else DayPaint.DAY
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.app_speed_slow), fontFamily = Plex, fontSize = 11.sp, color = muted)
        Box(Modifier.width(width).height(5.dp).clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(ramp.map { Color(0xFF000000.toInt() or it) }.let { if (rtl) it.reversed() else it })))
        Text(stringResource(R.string.app_speed_fast), fontFamily = Plex, fontSize = 11.sp, color = muted)
        if (lift) {
            Canvas(Modifier.padding(start = 4.dp).width(22.dp).height(6.dp)) {
                drawLine(if (night) Color(0xFFC9D5E2) else Color(0xFF22303F), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.2f * density,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5f * density, 3.5f * density)))
            }
            Text(stringResource(R.string.app_ridge_legend_lift), fontFamily = Plex, fontSize = 11.sp, color = muted)
        }
    }
}

package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.map.SlopeColors
import io.github.pini236.skiapp.status.LiftStatus

internal object LensPrefs {
    private fun p(c: android.content.Context) = c.getSharedPreferences("redesign", android.content.Context.MODE_PRIVATE)
    fun last(c: android.content.Context) = LensKind.of(p(c).getString("lens_last", null))
    fun setLast(c: android.content.Context, k: LensKind?) = p(c).edit().putString("lens_last", k?.id).apply()
}

@Composable
internal fun lensName(l: LensKind) = stringResource(when (l) {
    LensKind.SUN -> R.string.app_shelf_sun; LensKind.OPEN -> R.string.status_lift_open; LensKind.SLOPE -> R.string.app_lens_slope; LensKind.SNOW -> R.string.app_wx_snow_cap })

/** The row of lens chips under the pill (106 from the top, section 4), one lit at most. */
@Composable
internal fun BoxScope.LensChips(on: LensKind?, night: Boolean, haptics: Haptics, onTap: (LensKind) -> Unit) {
    val k = remember(night) { SheetInk(night) }
    Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 62.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (l in LensKind.values()) {
            val name = lensName(l)
            Chip(name, on == l, k, a11y = stringResource(R.string.app_lens_a11y, name)) { haptics.tick(.3f); onTap(l) }
        }
    }
}

/** The layers button's mark: three stacked sheets. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.layers(c: Color) {
    val w = size.width; val h = size.height; val st = 1.7.dp.toPx()
    for ((i, y) in listOf(.3f, .5f, .7f).withIndex()) {
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * .5f, h * (y - .16f)); lineTo(w * .9f, h * y); lineTo(w * .5f, h * (y + .16f)); lineTo(w * .1f, h * y); close() }
        if (i == 0) drawPath(p, c) else drawPath(p, c, style = androidx.compose.ui.graphics.drawscope.Stroke(st))
    }
}

/**
 * The lens's low sheet (section 1: a number 34 light, a title 16 bold and a line 13.5 under it, the legend and a note),
 * in place of the bottom bar. A pull down turns the lens off.
 */
@Composable
internal fun BoxScope.LensSheet(lens: LensKind, scene: MapScene, view: LiveView, status: LiftStatus?, inSeason: Boolean, night: Boolean, onOff: () -> Unit) {
    val k = remember(night) { SheetInk(night) }
    val counted = remember(scene) { scene.runs.pistes.filter { LensRules.counted(it.named, it.kind) } }
    val now = view.lightTime
    fun hm(h: Double): String { val m = Math.round(((h % 24) + 24) % 24 * 60).toInt(); return "%02d:%02d".format(m / 60 % 24, m % 60) }
    val clock = java.time.Instant.ofEpochMilli(now).atOffset(io.github.pini236.skiapp.meet.Meet.GUDAURI).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
    var num: String? = null; val title: String; var sub: String? = null; var legend: List<Pair<Color, String>> = emptyList(); var note: String? = null
    when (lens) {
        LensKind.SUN -> {
            val sh = if (view.sunUp()) view.sunShares() else null
            if (sh == null) { title = stringResource(R.string.app_lens_no_sun); sub = stringResource(R.string.app_lens_sunrise, hm(Sky.sunTimes(now)[0])) }
            else {
                val (n, shade) = LensRules.sun(sh, counted.map { it.key })
                num = n.toString(); title = pluralStringResource(R.plurals.app_lens_sun_title, n)
                sub = stringResource(R.string.app_lens_sun_sub, shade.toString(), clock)
                legend = listOf(Color(0xFFFFC93A) to stringResource(R.string.app_lens_sun_leg), Color(0xFFB9CCFA) to stringResource(R.string.app_lens_shade_leg))
                note = stringResource(R.string.app_lens_sun_note)
            }
        }
        LensKind.OPEN -> {
            if (status?.fresh != true) {
                title = stringResource(R.string.app_lens_no_report)
                sub = stringResource(R.string.status_board_hint, status?.open?.toString() ?: "0", LiveLifts.board(scene.runs).size.toString()).takeIf { inSeason }
            } else {
                val (n, closed) = LensRules.openCount({ key -> counted.firstOrNull { it.key == key }?.let { status.runOpen(it) } }, counted.map { it.key })
                num = n.toString(); title = pluralStringResource(R.plurals.app_lens_open_title, n)
                sub = stringResource(R.string.app_lens_open_sub, closed.toString(), io.github.pini236.skiapp.status.agoText(status))
                legend = listOf(k.ink to stringResource(R.string.status_lift_open), Color(if (night) 0xFF7C8798 else 0xFF8A93A3) to stringResource(R.string.app_lens_closed_leg),
                    k.ink2.copy(alpha = .45f) to stringResource(R.string.app_lens_unknown_leg))
            }
        }
        LensKind.SNOW -> {
            val wx = io.github.pini236.skiapp.weather.LocalWeather.current
            val f = wx.usable
            if (f == null) title = stringResource(R.string.weather_none)
            else {
                fun cm(id: String) = f.point(id)?.snow24?.let { Math.round(it).toInt() }
                val top = cm("sadzele"); val mid = cm("goodaura"); val low = cm("village")
                if (listOfNotNull(top, mid, low).all { it == 0 }) title = stringResource(R.string.app_lens_snow_none)
                else {
                    num = (top ?: 0).toString(); title = stringResource(R.string.app_lens_snow_title)
                    sub = stringResource(R.string.app_lens_snow_sub, (mid ?: 0).toString(), stringResource(R.string.weather_point_goodaura), (low ?: 0).toString())
                    legend = listOf(k.acc to stringResource(R.string.app_lens_snow_leg_1), Color(0xFF7FB2FF) to stringResource(R.string.app_lens_snow_leg_2),
                        Color(0xFF9AA7B8) to stringResource(R.string.app_lens_snow_leg_3))
                }
                note = stringResource(R.string.app_lens_snow_note) + " Open-Meteo" +
                    (if (f.state(java.time.Instant.now()) == io.github.pini236.skiapp.weather.Forecast.State.STALE) " · " + stringResource(R.string.weather_stale) else "")
            }
        }
        LensKind.SLOPE -> {
            title = stringResource(R.string.app_lens_slope_title)
            legend = SlopeColors.HEX.mapIndexed { i, h -> Color(android.graphics.Color.parseColor(h)) to stringResource(when (i) {
                0 -> R.string.app_lens_slope_leg_1; 1 -> R.string.app_lens_slope_leg_2; 2 -> R.string.app_lens_slope_leg_3; else -> R.string.app_lens_slope_leg_4 }) }
            note = stringResource(R.string.app_lens_slope_note)
        }
    }
    Column(Modifier.align(Alignment.BottomCenter).sheetWidth().navigationBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy > 18f) onOff() } }
        .shadow(14.dp, RoundedCornerShape(28.dp), clip = false).clip(RoundedCornerShape(28.dp)).background(k.surf.copy(alpha = 1f))
        .border(1.dp, k.line, RoundedCornerShape(28.dp)).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(k.line))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            num?.let { Text(it, fontFamily = Gudi, fontWeight = FontWeight.Light, fontSize = 34.sp, color = k.ink) }
            Column {
                Text(title, fontFamily = Gudi, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = k.ink)
                sub?.let { Text(it, fontFamily = Gudi, fontSize = 13.5.sp, color = k.ink2) }
            }
        }
        if (legend.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((c, t) in legend) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(14.dp, 5.dp)) { drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round) }
                Text(t, fontFamily = Gudi, fontSize = 11.5.sp, color = k.ink2)
            }
        }
        note?.let { Text(it, fontFamily = Gudi, fontSize = 12.5.sp, color = k.ink2) }
    }
}

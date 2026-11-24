package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.pini236.skiapp.ui.Orientation

/**
 * Part 8, the tablet (design/handoff/2026-11-23-redesign-everyone, section 3; Android only, from 600 dp): the mountain
 * fills the screen in either direction, and a sheet becomes a side panel: 400 wide, 12 from the edges, at the end side,
 * from 108 at the top to 12 at the bottom, corners 28, the mountain beside it still to be touched. The same component as
 * the sheet, only placed otherwise (the architect's condition 4): no screen, flow or data of its own.
 */
internal object TabletUi {
    const val PANEL = 400
    const val PILL = 360
    const val DOCK = 420
    const val FULL = 560
}

@Composable internal fun isTabletUi(): Boolean = Orientation.isTablet(LocalConfiguration.current.smallestScreenWidthDp)

/** A sheet's shape: the top corners on a phone, all four on a tablet's side panel. */
@Composable internal fun sheetShape(): Shape = if (isTabletUi()) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Where a sheet sits: on a phone across the bottom, as before; on a tablet the side panel at the end. [content] gets the
 * modifier for the sheet's own surface.
 */
@Composable
internal fun SheetPlace(content: @Composable (Modifier) -> Unit) {
    if (!isTabletUi()) { content(Modifier.fillMaxWidth()); return }
    val h = LocalConfiguration.current.screenHeightDp
    Box(Modifier.fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomEnd) {
        content(Modifier.width(TabletUi.PANEL.dp).heightIn(max = (h - 108 - 12).coerceAtLeast(200).dp))
    }
}

/** A full screen (the ski pass, the trip, the settings): on a tablet one column at most 560 wide, in the middle. */
@Composable
internal fun BoxScope.FullColumn(content: @Composable (Modifier) -> Unit) {
    if (!isTabletUi()) { content(Modifier.fillMaxWidth()); return }
    Box(Modifier.align(Alignment.Center).width(TabletUi.FULL.dp)) { content(Modifier.fillMaxWidth()) }
}

/**
 * The same place as a modifier, for a sheet that lays itself out: full width on a phone; on a tablet the node still
 * spans the width (its parent places it as before) and the sheet itself is 400 wide at the end, 12 from the edges.
 * Put it first, before the sheet's shadow and background.
 */
@Composable
internal fun Modifier.sheetWidth(): Modifier =
    if (!isTabletUi()) fillMaxWidth()
    else fillMaxWidth().wrapContentWidth(Alignment.End).padding(12.dp).width(TabletUi.PANEL.dp)

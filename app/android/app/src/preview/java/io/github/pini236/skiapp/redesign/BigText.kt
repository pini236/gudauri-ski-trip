package io.github.pini236.skiapp.redesign

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Part 8, large text (design/handoff/2026-11-23-redesign-everyone, section 1): text grows with the phone's setting up
 * to 200 percent (sp); the huge numbers (40 and over: the speed, the ski pass's price, the meetup's time, the count to
 * the opening) only to 130; and where there is no room the layout changes rather than cutting the text.
 */
internal object BigText {
    /** From this scale on, the layouts switch (two rows of numbers, buttons one under the other, the dock's icon only). */
    const val FROM = 1.3f
    const val HUGE_MAX = 1.3f

    /** A huge number's size in sp, so that with the phone's [scale] it grows to 130 percent at most. */
    fun hugeSp(size: Float, scale: Float): Float = if (scale <= HUGE_MAX) size else size * HUGE_MAX / scale
}

@Composable internal fun bigText(): Boolean = LocalDensity.current.fontScale >= BigText.FROM

/** A number's size: as it is up to 40, capped at 130 percent of the phone's scale from 40 up. */
@Composable internal fun bigSp(size: Float): TextUnit =
    if (size < 40f) size.sp else BigText.hugeSp(size, LocalDensity.current.fontScale).sp

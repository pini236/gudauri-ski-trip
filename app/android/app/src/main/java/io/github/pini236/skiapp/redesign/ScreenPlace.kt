package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset

/**
 * Places a tag or a tap area at a point of the screen, measured from its top left, the same in every language. The
 * mountain (GL) and the village's picture are never mirrored, so a thing put on them must not be either: Modifier.offset
 * flips x in Hebrew, and even absoluteOffset starts from the parent's start, which in Hebrew is the right edge (the
 * review of 25.11). Size and touch are the content's own.
 */
internal fun Modifier.atScreen(at: Density.() -> IntOffset): Modifier =
    this.fillMaxSize().wrapContentSize(AbsoluteAlignment.TopLeft, unbounded = true).absoluteOffset(at)

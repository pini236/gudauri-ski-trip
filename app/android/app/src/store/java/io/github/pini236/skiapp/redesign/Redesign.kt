package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene

/** The store build has nothing here: the redesign POC (decisions 83, 84) lives in the test build only (src/preview). */
object Redesign {
    @Suppress("UNUSED_PARAMETER") fun qa(cmd: String) {}

    @Suppress("UNUSED_PARAMETER")
    @Composable fun day(file: java.io.File, sample: Boolean, scene: MapScene?, mode: DayNight.Mode, haptics: Haptics, onBack: () -> Unit): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    @Composable fun BoxScope.Host(home: Boolean, mode: DayNight.Mode, haptics: Haptics, scene: MapScene?) {}
}

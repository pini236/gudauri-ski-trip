package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.home.DayNight
import io.github.pini236.skiapp.map.MapScene

/** The store build has nothing here: the redesign POC (decisions 83, 84) lives in the test build only (src/preview). */
object Redesign {
    @Suppress("UNUSED_PARAMETER") fun qa(cmd: String) {}
    /** Whether the new mountain covers the app (never in the store build). */
    val covering: Boolean get() = false

    @Suppress("UNUSED_PARAMETER") fun runDone(ctx: android.content.Context, day: java.io.File, autostop: String?, lift: String?) {}

    @Suppress("UNUSED_PARAMETER")
    @Composable fun day(file: java.io.File, sample: Boolean, scene: MapScene?, mode: DayNight.Mode, haptics: Haptics, onAgain: () -> Unit = {}, onBack: () -> Unit): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    @Composable fun live(scene: MapScene?, mode: DayNight.Mode, haptics: Haptics, liftName: (String) -> String, runName: (String) -> String,
                         onSwitch: (Boolean) -> Unit, onStop: () -> Unit, onEnded: (java.io.File?) -> Unit,
                         liftState: (String) -> Pair<Boolean, String?>? = { null }, onBack: () -> Unit): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    @Composable fun first(single: Boolean, haptics: Haptics, onGo: (Boolean) -> Unit, onDismiss: () -> Unit): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun notice(c: android.content.Context, elapsedMs: Long, live: io.github.pini236.skiapp.dayrec.DayEngine.Snapshot?,
               ride: io.github.pini236.skiapp.dayrec.DayEngine.Ride?): Triple<String, String, String?>? = null

    @Suppress("UNUSED_PARAMETER")
    fun noticeKey(live: io.github.pini236.skiapp.dayrec.DayEngine.Snapshot?, ride: io.github.pini236.skiapp.dayrec.DayEngine.Ride?): String = ""

    @Suppress("UNUSED_PARAMETER")
    @Composable fun days(mode: DayNight.Mode, haptics: Haptics, onOpen: (String) -> Unit, scene: MapScene? = null, onRecord: () -> Unit = {}, onBack: () -> Unit): Boolean = false

    @Suppress("UNUSED_PARAMETER") @Composable fun BoxScope.Host(home: Boolean, mode: DayNight.Mode, haptics: Haptics, scene: MapScene?, status: io.github.pini236.skiapp.status.LiftStatus? = null,
                                 inSeason: Boolean = false, forMe: Boolean = false, onForMe: (Boolean) -> Unit = {}, around: io.github.pini236.skiapp.home.Around? = null) {}
}

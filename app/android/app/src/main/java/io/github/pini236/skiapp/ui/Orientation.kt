package io.github.pini236.skiapp.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import io.github.pini236.skiapp.BuildConfig

/**
 * The redesign's part 8 (design/handoff/2026-11-23-redesign-everyone, section 3; the architect's conditions,
 * docs/ARCHITECTURE.md, 23.11.2026): a phone stays upright, a tablet turns.
 *
 * - At run time and not in the manifest: a manifest lock would hold tablets too up to Android 15.
 * - By the window, not the device: worked out again on every change of configuration (a foldable opened, a split
 *   window), with the one threshold [TABLET_DP], the same as the measuring's device class.
 * - From Android 16 the system ignores a lock on 600 dp and over; a tablet works both ways in any case.
 * - For now in the test build only, with the new design; the store build turns as it does today.
 */
object Orientation {
    const val TABLET_DP = 600

    fun isTablet(c: Configuration) = isTablet(c.smallestScreenWidthDp)
    fun isTablet(smallestDp: Int) = smallestDp >= TABLET_DP

    /** What the window asks for: upright under [TABLET_DP], free over it. */
    fun wanted(smallestDp: Int, enabled: Boolean = BuildConfig.FLAVOR == "preview"): Int =
        if (enabled && !isTablet(smallestDp)) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    fun apply(a: Activity, c: Configuration = a.resources.configuration) {
        val w = wanted(c.smallestScreenWidthDp)
        if (a.requestedOrientation != w) a.requestedOrientation = w
    }
}

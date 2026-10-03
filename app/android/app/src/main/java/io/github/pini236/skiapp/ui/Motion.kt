package io.github.pini236.skiapp.ui

import android.content.Context
import android.provider.Settings

/**
 * The system's "remove animations" (animator duration scale 0), as the site's prefers-reduced-motion (PARITY A-12).
 * Compose's own animations already follow it; this is for the ones the app moves itself: the mountain's camera, the
 * run's paint and the dimming, the meeting point's map, and the fly down (hidden, as on the site).
 */
object Motion {
    fun reduced(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

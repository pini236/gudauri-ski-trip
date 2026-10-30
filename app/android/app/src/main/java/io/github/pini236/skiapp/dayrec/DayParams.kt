package io.github.pini236.skiapp.dayrec

/**
 * The recorder's numbers, in one place. docs/ARCHITECTURE.md m-8 puts them in tools/fixtures/dayrec-params.json, shared
 * with the iPhone and the reference tool; that file does not exist yet (the reference session writes it), so they live
 * here, each with the condition it comes from, and move to that file when it is there. A test pins the area to
 * site/data/terrain.json.
 */
object DayParams {
    /** GPS: a reading every second, the fastest nearly every phone gives (condition 1: no "faster than that"). */
    const val GPS_MIN_TIME_MS = 1_000L
    /** Condition 2: stop alone after 20 minutes without moving outside the resort, or 60 inside it. */
    const val IDLE_OUTSIDE_MS = 20 * 60_000L
    const val IDLE_INSIDE_MS = 60 * 60_000L
    /** ...with a notice this long before. */
    const val IDLE_WARN_MS = 2 * 60_000L
    /** Moving means getting farther than this from where the idle time started. */
    const val IDLE_MOVE_M = 30f
    /** Condition 3: a hole longer than this is written down. */
    const val GAP_MS = 10_000L
    /** "Here you fell" (condition 10): fall.* in tools/fixtures/dayrec-params.json; a test pins these to that file. */
    const val FALL_HZ = 50
    const val FALL_PRE_S = 2.5
    const val FALL_POST_S = 2.5
    const val FALL_TRIGGER_G = 2.5
    const val FALL_DEBOUNCE_S = 3.0
    /** The terrain model's extent, in metres from the resort's origin (site/data/terrain.json, dem.x0..y1). */
    const val AREA_X0 = -7795.76f
    const val AREA_Y0 = -7792.4f
    const val AREA_X1 = 6975.15f
    const val AREA_Y1 = 7792.4f
    /** Where "the resort" is for the Gudauri capability (resorts.json). */
    const val RESORT = "gudauri"
    const val TZ = "Asia/Tbilisi"
    /** The feature's name in resorts.json "features" (condition 7). */
    const val FEATURE = "dayrec"
}

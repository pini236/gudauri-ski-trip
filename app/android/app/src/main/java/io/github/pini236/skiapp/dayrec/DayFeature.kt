package io.github.pini236.skiapp.dayrec

import io.github.pini236.skiapp.BuildConfig
import org.json.JSONObject

/**
 * Condition 7: the feature is built behind a capability in the site's data, not behind a version. The app shows
 * "start a day" only when resorts.json lists "dayrec" for the resort, so a store build ships with it hidden and it is
 * switched on (or off again) without a new version.
 */
object DayFeature {
    /** [resortsJson] is site/data/resorts.json; a file the app cannot read means the feature is off. */
    fun enabled(resortsJson: String?, resort: String = DayParams.RESORT): Boolean = runCatching {
        val all = JSONObject(resortsJson ?: return false).getJSONArray("resorts")
        (0 until all.length()).map { all.getJSONObject(it) }.firstOrNull { it.optString("id") == resort }
            ?.optJSONArray("features")?.let { f -> (0 until f.length()).any { f.optString(it) == DayParams.FEATURE } } ?: false
    }.getOrDefault(false)

    /**
     * What the screens ask: the capability, and the build. The store build keeps the ski day's screens hidden even though the
     * site's data lists the capability (that data is already on), until the Play declaration and the privacy line are out.
     */
    fun visible(resortsJson: String?): Boolean = BuildConfig.DAYREC_UI && enabled(resortsJson)
}

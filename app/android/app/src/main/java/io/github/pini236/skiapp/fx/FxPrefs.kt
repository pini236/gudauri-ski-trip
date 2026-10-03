package io.github.pini236.skiapp.fx

import android.content.Context

/**
 * Sound and vibration on or off (the settings, 13.7; the site's prefs.js): kept on the phone, and they apply
 * everywhere the app plays or vibrates, the pass and the games alike. Both on by default, as on the site.
 */
object FxPrefs {
    private const val PREFS = "fx"
    fun sound(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("sound", true)
    fun haptics(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("haptics", true)
    fun set(context: Context, key: String, on: Boolean) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
}

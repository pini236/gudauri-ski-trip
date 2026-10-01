package io.github.pini236.skiapp.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Plex
import java.util.Locale

/**
 * The four languages (decision 32, docs/APP-NATIVE.md decision 13): Hebrew first, then English, Russian and Georgian.
 * What the user reads decides everything else: the language of the strings actually in use (R.string.lang, one per
 * values-xx file) sets the layout direction and the fonts, so a phone in a language the app does not have yet gets a
 * whole Hebrew screen, never Hebrew words in a left-to-right layout.
 *
 * The choice is the app's own (not the phone's): the phone's per-app language on Android 13+, and on older phones a
 * saved choice applied in attachBaseContext. The settings screen (13.7) calls [set].
 */
object Lang {
    enum class Script { HEBREW, LATIN, CYRILLIC, GEORGIAN }

    class Language(val tag: String, val name: String, val rtl: Boolean, val script: Script) {
        val locale: Locale get() = Locale.forLanguageTag(tag)
        val direction get() = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    val ALL = listOf(
        Language("he", "עברית", rtl = true, script = Script.HEBREW),
        Language("en", "English", rtl = false, script = Script.LATIN),
        Language("ru", "Русский", rtl = false, script = Script.CYRILLIC),
        Language("ka", "ქართული", rtl = false, script = Script.GEORGIAN),
    )

    fun byTag(tag: String?): Language = ALL.firstOrNull { it.tag == tag?.substringBefore('-')?.let { t -> if (t == "iw") "he" else t } } ?: ALL[0]

    /** The language of the words on screen now. */
    fun current(res: Resources): Language = byTag(res.getString(R.string.lang))

    /**
     * Fonts per script. Karantina has Hebrew and Latin only, and IBM Plex Sans Hebrew no full Cyrillic: Russian and
     * Georgian use the system's fonts until their own are chosen in the canvas (decision 32).
     */
    fun display(l: Language): FontFamily = if (l.script == Script.HEBREW || l.script == Script.LATIN) Karantina else FontFamily.SansSerif
    fun body(l: Language): FontFamily = if (l.script == Script.HEBREW || l.script == Script.LATIN) Plex else FontFamily.SansSerif

    private const val PREFS = "lang"

    /** null = follow the phone. */
    fun set(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tag", tag).apply()
            (context as? android.app.Activity)?.recreate()
        }
    }

    /** The user's own choice on older phones (null = follow the phone). */
    fun chosen(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tag", null)

    /** Older phones: the saved choice, applied to the activity's resources (MainActivity.attachBaseContext). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tag", null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        val conf = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return base.createConfigurationContext(conf)
    }
}

package io.github.pini236.skiapp.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.ui.Karantina
import io.github.pini236.skiapp.ui.Plex
import java.util.Locale

/**
 * The four languages (decision 32, docs/APP-NATIVE.md decision 13): Hebrew first, then English, Russian and Georgian.
 * What the user reads decides everything else: the language of the strings actually in use (R.string.lang, one per
 * values-xx file) sets the layout direction and the fonts, never words of one language in the layout of another.
 * Without a choice in the app, a phone set to Hebrew gets Hebrew and any other English ([auto], Pini, 3.10.2026;
 * English is the store's default language, decision 33); values/ is English too, for anything else that reads it.
 *
 * The choice is the app's own (not the phone's): the phone's per-app language on Android 13+, and on older phones a
 * saved choice applied in attachBaseContext. The language tag on the home page calls [set] (3.10.2026; home/LangSheet.kt).
 */
object Lang {
    enum class Script { HEBREW, LATIN, CYRILLIC, GEORGIAN }

    class Language(val tag: String, val name: String, val rtl: Boolean, val script: Script) {
        val locale: Locale get() = Locale.forLanguageTag(tag)
        val direction get() = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        /** The short mark on the home's language tag, as on the privacy page's buttons: עב, EN, RU, KA. */
        val code: String get() = if (tag == "he") "עב" else tag.uppercase()
    }

    val ALL = listOf(
        Language("he", "עברית", rtl = true, script = Script.HEBREW),
        Language("en", "English", rtl = false, script = Script.LATIN),
        Language("ru", "Русский", rtl = false, script = Script.CYRILLIC),
        Language("ka", "ქართული", rtl = false, script = Script.GEORGIAN),
    )

    /** A language the app does not have is English, as on a phone in such a language. */
    fun byTag(tag: String?): Language = ALL.firstOrNull { it.tag == tag?.substringBefore('-')?.let { t -> if (t == "iw") "he" else t } } ?: ENGLISH

    val ENGLISH get() = ALL[1]

    /** The language of the words on screen now. */
    fun current(res: Resources): Language = byTag(res.getString(R.string.lang))

    /**
     * Fonts per language, as approved in the canvas (FT1, decision 36): Hebrew Karantina and IBM Plex Sans Hebrew;
     * English Karantina and IBM Plex Sans; Russian Oswald 600 and IBM Plex Sans; Georgian Noto Sans Georgian, narrow
     * (width 62.5%) and 800 for headings, regular for text. All OFL, packed in the app (licences in app/android/licenses).
     */
    @OptIn(ExperimentalTextApi::class)
    fun display(l: Language): FontFamily = when (l.script) {
        Script.HEBREW, Script.LATIN -> Karantina
        Script.CYRILLIC -> FontFamily(Font(R.font.oswald, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(600))))
        Script.GEORGIAN -> FontFamily(Font(R.font.noto_sans_georgian, FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(62.5f))))
    }

    @OptIn(ExperimentalTextApi::class)
    fun body(l: Language): FontFamily = when (l.script) {
        Script.HEBREW -> Plex
        Script.LATIN, Script.CYRILLIC -> FontFamily(
            Font(R.font.plex_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.plex_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        )
        Script.GEORGIAN -> FontFamily(
            Font(R.font.noto_sans_georgian, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.noto_sans_georgian, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        )
    }

    /** The same faces as [display] and [body] (bold), for text drawn outside Compose: the map's labels. */
    fun typeface(context: Context, l: Language, display: Boolean): android.graphics.Typeface? {
        val res = when (l.script) {
            Script.CYRILLIC -> if (display) R.font.oswald else R.font.plex_sans
            Script.GEORGIAN -> R.font.noto_sans_georgian
            // English text in IBM Plex Sans, as the site (A-28): the map's labels and the meetup picture too
            Script.LATIN -> if (display) R.font.karantina_bold else R.font.plex_sans
            else -> if (display) R.font.karantina_bold else R.font.plex_hebrew_bold
        }
        val tf = androidx.core.content.res.ResourcesCompat.getFont(context, res) ?: return null
        if (res == R.font.karantina_bold || res == R.font.plex_hebrew_bold) return tf
        // the variable fonts: their bold weight (the old phones get a drawn bold)
        return if (Build.VERSION.SDK_INT >= 28) android.graphics.Typeface.create(tf, if (display && l.script == Script.CYRILLIC) 600 else 700, false)
        else android.graphics.Typeface.create(tf, android.graphics.Typeface.BOLD)
    }

    /** Display sizes per language: Oswald and the narrow Georgian are bigger than Karantina at the same size (FT1, LT2, LT3). */
    fun displayScale(l: Language): Float = when (l.script) { Script.CYRILLIC -> 0.85f; Script.GEORGIAN -> 0.9f; else -> 1f }

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

    /** Whether the language was chosen in the app, not taken from the phone. */
    fun manual(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) !context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty else chosen(context) != null

    /** The user's own choice on older phones (null = follow the phone). */
    fun chosen(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tag", null)

    /**
     * Without a language chosen in the app: Hebrew on a phone set to Hebrew, English on any other, Russian and Georgian
     * too (Pini, 3.10.2026); the tag on the home page picks any of the four.
     */
    fun auto(phone: Locale?): String = if (phone?.language == "he" || phone?.language == "iw") "he" else "en"

    /**
     * The language for the activity's resources (MainActivity.attachBaseContext), and for a notification's words: a
     * choice made in the app is the phone's per-app language on Android 13+ (the system applies it) or the saved one
     * on older phones; with no choice, [auto], one language only, so the phone's other languages never get in.
     */
    fun wrap(base: Context): Context {
        val phone = base.resources.configuration.locales[0]
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            if (!base.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty) return base
            auto(phone)
        } else base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tag", null) ?: auto(phone)
        val locale = Locale.forLanguageTag(tag)
        val conf = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return base.createConfigurationContext(conf)
    }
}

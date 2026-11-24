package io.github.pini236.skiapp.redesign

import android.content.Context
import android.graphics.fonts.Font as PFont
import android.graphics.fonts.FontFamily as PFamily
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.i18n.Lang

/**
 * Part 7 of the redesign (design/handoff/2026-11-23-redesign-languages, section 1; the architect's conditions,
 * docs/ARCHITECTURE.md, 23.11.2026): one chain of fonts for the whole new design, in every language, chosen by the
 * letter and not by the language: Heebo (Hebrew and Latin), then the system's Roboto for Cyrillic (not packed on
 * Android, as recommended), then Noto Sans Georgian (packed), then the system's own.
 *
 * - From API 29 the chain is one Typeface with custom fallbacks, so numbers, times and run names look the same in every
 *   language. Its weights (300 to 900) come from Typeface.create on the same collection.
 * - Under API 29 (minSdk 26) there is no custom fallback: the font by the language, as today (Georgian first in
 *   Georgian, Heebo otherwise; the rest falls to the system's fonts by the letter). Marked in docs/PARITY.md.
 */
object GudiFont {
    /** The weights the new design uses (the board's 300, 400, 500, 700 and 900). */
    val WEIGHTS = intArrayOf(300, 400, 500, 700, 900)

    @Volatile private var made: Pair<String, FontFamily>? = null
    @Volatile private var app: Context? = null

    /** Kept from the redesign's entry, so the family can be read where there is no Context (a drawing, a style). */
    fun init(context: Context) { app = context.applicationContext; family(context) }

    /** The family now (Heebo by language until [init] has run). */
    val current: FontFamily get() = app?.let { family(it) } ?: made?.second ?: byLanguage("he")

    /** The new design's family for the language on screen now; made once per language. */
    fun family(context: Context): FontFamily {
        val tag = Lang.current(context.resources).tag
        made?.let { (t, f) -> if (t == tag) return f }
        val f = if (Build.VERSION.SDK_INT >= 29) runCatching { chain(context) }.getOrNull() ?: byLanguage(tag) else byLanguage(tag)
        made = tag to f
        return f
    }

    @OptIn(ExperimentalTextApi::class)
    internal fun byLanguage(tag: String): FontFamily {
        fun of(res: Int, wdth: Boolean) = WEIGHTS.map { w ->
            Font(res, FontWeight(w), variationSettings = if (wdth) FontVariation.Settings(FontVariation.weight(w), FontVariation.width(100f)) else FontVariation.Settings(FontVariation.weight(w)))
        }
        return FontFamily(if (tag == "ka") of(R.font.noto_sans_georgian, true) else of(R.font.heebo, false))
    }

    @RequiresApi(29)
    private fun chain(context: Context): FontFamily {
        val res = context.resources
        fun fam(id: Int, wdth: Boolean): PFamily {
            val fonts = WEIGHTS.map { w -> PFont.Builder(res, id).setWeight(w).setFontVariationSettings(if (wdth) "'wght' $w, 'wdth' 100" else "'wght' $w").build() }
            return PFamily.Builder(fonts.first()).also { b -> fonts.drop(1).forEach { b.addFont(it) } }.build()
        }
        val tf = android.graphics.Typeface.CustomFallbackBuilder(fam(R.font.heebo, false))
            .addCustomFallback(fam(R.font.noto_sans_georgian, true))
            .setSystemFallback("sans-serif")
            .build()
        return FontFamily(androidx.compose.ui.text.font.Typeface(tf))
    }
}

/** The new design's text font: the chain above. */
internal val Gudi: FontFamily get() = GudiFont.current

/** Makes [GudiFont] at the app's start (a provider runs before any screen); it holds no data and answers nothing. */
class GudiFontInit : android.content.ContentProvider() {
    override fun onCreate(): Boolean { context?.let { runCatching { GudiFont.init(it) } }; return true }
    override fun query(u: android.net.Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): android.database.Cursor? = null
    override fun getType(u: android.net.Uri): String? = null
    override fun insert(u: android.net.Uri, v: android.content.ContentValues?): android.net.Uri? = null
    override fun delete(u: android.net.Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: android.net.Uri, v: android.content.ContentValues?, s: String?, a: Array<out String>?) = 0
}

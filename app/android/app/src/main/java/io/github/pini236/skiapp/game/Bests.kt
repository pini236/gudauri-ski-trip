package io.github.pini236.skiapp.game

import android.content.Context

/**
 * Each game's high score on this phone (the site keeps them in the browser), cleared by the settings' "reset the high
 * scores". [offer] keeps a higher one and says whether it was; the group's table gets it too (submit_score, only the best
 * counts there).
 */
object Bests {
    private const val PREFS = "bests"
    fun get(context: Context, game: String): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(game, 0)
    fun offer(context: Context, game: String, score: Int): Boolean {
        if (score <= get(context, game)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(game, score).apply()
        return true
    }
    fun all(context: Context): Map<String, Int> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()

    // what already went to the group's table (the site's gud-best-sent): a reset does not clear it, so only a better
    // score goes again
    private const val SENT = "bests_sent"
    /** The high scores the group's table has not had yet. */
    fun unsent(context: Context): Map<String, Int> {
        val sent = context.getSharedPreferences(SENT, Context.MODE_PRIVATE)
        return all(context).filter { (g, b) -> b > sent.getInt(g, 0) }
    }
    fun markSent(context: Context, game: String, score: Int) = context.getSharedPreferences(SENT, Context.MODE_PRIVATE).edit().putInt(game, score).apply()
}

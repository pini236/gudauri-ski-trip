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
    /**
     * The end of a game (game_end), as the site's telemetry.js best(): the ski school counts its stars, the best of each
     * lesson added up; every other game its best score. True when it is a new record (game_end's `best`).
     */
    fun ended(context: Context, game: String, level: String?, score: Int): Boolean {
        var s = maxOf(0, score)
        if (game == "school" && level != null) {
            val lv = context.getSharedPreferences(LEVELS, Context.MODE_PRIVATE)
            if (s > lv.getInt(level, 0)) lv.edit().putInt(level, s).apply()
            s = lv.all.values.sumOf { (it as? Int) ?: 0 }
        }
        return offer(context, game, s)
    }
    private const val LEVELS = "bests_school"
    /** The ski school's stars in a lesson ([level] its number, from 1): the best of it, and what opens the next one. */
    fun lesson(context: Context, level: Int): Int = context.getSharedPreferences(LEVELS, Context.MODE_PRIVATE).getInt(level.toString(), 0)
    /** For the emulator run: every lesson with a star, so all of them are open. */
    internal fun openAllLessons(context: Context) = context.getSharedPreferences(LEVELS, Context.MODE_PRIVATE).edit().apply {
        for (i in 1..7) putInt(i.toString(), maxOf(1, lesson(context, i)))
    }.apply()
    /**
     * The stars of each level in the other games with levels (the snowball fight's rungs, the descent's runs; the site's
     * snw2-stars-*, ski-*): the best of each, which opens the next one. They do not add up to the record.
     */
    fun level(context: Context, game: String, level: Int): Int = context.getSharedPreferences(STARS + game, Context.MODE_PRIVATE).getInt(level.toString(), 0)
    /** Keeps [stars] for [level] when more than before; true when it was. */
    fun setLevel(context: Context, game: String, level: Int, stars: Int): Boolean {
        if (stars <= level(context, game, level)) return false
        context.getSharedPreferences(STARS + game, Context.MODE_PRIVATE).edit().putInt(level.toString(), stars).apply()
        return true
    }
    private const val STARS = "bests_levels_"
    private val LEVEL_GAMES = listOf("snowball", "descent")

    /** The games with a table in the group, by their key on the server and the site (account.js GAMES). */
    val GAMES = listOf("descent", "school", "fresh", "snowball", "merge")

    /** What each game keeps on the phone besides its record: the descent's best times and ghosts, the merge's board, the lake's longest chain, the choices. */
    private val GAME_PREFS = listOf("descent", "merge", "snowball", "fresh")

    /**
     * The settings' reset, as the site's (every game key in the browser, GUD_GAME_KEYS): the records, the stars, the
     * best times and ghosts, the saved board, the choices, and what was sent to the group (what the group's table
     * already had stays there).
     */
    fun reset(context: Context) {
        val all = listOf(PREFS, LEVELS, SENT) + LEVEL_GAMES.map { STARS + it } + GAME_PREFS
        for (p in all) context.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun all(context: Context): Map<String, Int> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()

    // what already went to the group's table (the site's gud-best-sent), so only a better score goes again; a reset
    // clears it too, as on the site (A-40)
    private const val SENT = "bests_sent"
    /** The high scores the group's table has not had yet. */
    fun unsent(context: Context): Map<String, Int> {
        val sent = context.getSharedPreferences(SENT, Context.MODE_PRIVATE)
        return all(context).filter { (g, b) -> b > sent.getInt(g, 0) }
    }
    fun markSent(context: Context, game: String, score: Int) = context.getSharedPreferences(SENT, Context.MODE_PRIVATE).edit().putInt(game, score).apply()
}

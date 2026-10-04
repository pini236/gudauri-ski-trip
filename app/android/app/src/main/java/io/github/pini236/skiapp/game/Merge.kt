package io.github.pini236.skiapp.game

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * "Merging snowballs" (decision 17; site/games/merge, from design/games/merge): 2048 on snow. Twelve steps, from a flake
 * to the king of Kazbek; a swipe slides every tile, two of a kind become the next. The rules are the site's, so a board
 * plays the same: merges are scored 2^(level+1), a new tile is a flake (90%) or a small ball, three undos a game, and a
 * whole snowman (level 7) is "won" (the game goes on).
 *
 * The board is [Merge.SIZE] rows of [Merge.SIZE], column 0 on the left of the screen, and [Dir] is as on the screen (the
 * site draws its columns right to left; a swipe is a swipe either way).
 */
class Merge(private val rnd: Random = Random.Default) {
    class Tile(val id: Int, val lv: Int, var r: Int, var c: Int)

    /** What a move did: tiles that slid (id to their cell), merges (the new tile and the two it came from), the new tile. */
    class Moved(val slid: List<Tile>, val merges: List<Pair<Tile, List<Tile>>>, val gained: Int, val spawned: Tile?, val newTop: Int?)

    enum class Dir { LEFT, RIGHT, UP, DOWN }

    var grid: Array<Array<Tile?>> = Array(SIZE) { arrayOfNulls(SIZE) }; private set
    var score = 0; private set
    var maxLv = 0; private set
    var undos = UNDOS; private set
    private var nid = 1
    private val hist = ArrayDeque<Triple<Array<Array<Tile?>>, Int, Int>>()

    val tiles: List<Tile> get() = grid.flatMap { row -> row.filterNotNull() }
    val canUndo get() = undos > 0 && hist.isNotEmpty()

    fun newGame() {
        grid = Array(SIZE) { arrayOfNulls(SIZE) }; score = 0; maxLv = 0; undos = UNDOS; hist.clear()
        spawn(); spawn()
    }

    private fun empty() = buildList { for (r in 0 until SIZE) for (c in 0 until SIZE) if (grid[r][c] == null) add(r to c) }

    private fun spawn(): Tile? {
        val e = empty()
        if (e.isEmpty()) return null
        val (r, c) = e[rnd.nextInt(e.size)]
        return Tile(nid++, if (rnd.nextFloat() < .9f) 0 else 1, r, c).also { grid[r][c] = it }
    }

    /** The cell of line [i] at place [j], where j = 0 is the side the tiles slide toward. */
    private fun cellOf(dir: Dir, i: Int, j: Int): Pair<Int, Int> = when (dir) {
        Dir.LEFT -> i to j
        Dir.RIGHT -> i to SIZE - 1 - j
        Dir.UP -> j to i
        Dir.DOWN -> SIZE - 1 - j to i
    }

    private fun copy() = Array(SIZE) { r -> Array(SIZE) { c -> grid[r][c]?.let { Tile(it.id, it.lv, it.r, it.c) } } }

    /** A swipe; null when nothing could move (the board only nudges). */
    fun move(dir: Dir): Moved? {
        val before = Triple(copy(), score, maxLv)
        var moved = false
        val merges = ArrayList<Pair<Tile, List<Tile>>>()
        val slid = ArrayList<Tile>()
        for (i in 0 until SIZE) {
            val items = (0 until SIZE).mapNotNull { j -> cellOf(dir, i, j).let { (r, c) -> grid[r][c] } }
            val out = ArrayList<List<Tile>>()
            var k = 0
            while (k < items.size) {
                val a = items[k]; val b = items.getOrNull(k + 1)
                if (b != null && a.lv == b.lv && a.lv < LEVELS - 1) { out += listOf(a, b); k += 2 } else { out += listOf(a); k++ }
            }
            for (j in 0 until SIZE) cellOf(dir, i, j).let { (r, c) -> grid[r][c] = null }
            out.forEachIndexed { j, o ->
                val (r, c) = cellOf(dir, i, j)
                if (o.size == 2) {
                    moved = true
                    val t = Tile(nid++, o[0].lv + 1, r, c); grid[r][c] = t; merges += t to o
                } else {
                    val a = o[0]
                    if (a.r != r || a.c != c) { moved = true; a.r = r; a.c = c; slid += a }
                    grid[r][c] = a
                }
            }
        }
        if (!moved) return null
        hist.addLast(before); if (hist.size > UNDOS) hist.removeFirst()
        var gained = 0
        var top: Int? = null
        for ((t, _) in merges) { gained += 1 shl (t.lv + 1); if (t.lv > maxLv) { maxLv = t.lv; top = t.lv } }
        score += gained
        return Moved(slid, merges, gained, spawn(), top)
    }

    fun canMove(): Boolean {
        if (empty().isNotEmpty()) return true
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val v = grid[r][c]!!.lv
            if (c < SIZE - 1 && grid[r][c + 1]!!.lv == v) return true
            if (r < SIZE - 1 && grid[r + 1][c]!!.lv == v) return true
        }
        return false
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        val (g, s, m) = hist.removeLast()
        grid = g; score = s; maxLv = m; undos--
        return true
    }

    /** The board kept on the phone (the site's "save"): the game goes on after the app closes. */
    fun save(): String = JSONObject().put("score", score).put("maxLv", maxLv).put("undos", undos)
        .put("g", JSONArray(tiles.map { JSONArray(listOf(it.id, it.lv, it.r, it.c)) })).toString()

    fun restore(s: String?): Boolean = runCatching {
        val o = JSONObject(s ?: return false)
        val g = Array(SIZE) { arrayOfNulls<Tile>(SIZE) }
        val a = o.getJSONArray("g")
        for (i in 0 until a.length()) { val t = a.getJSONArray(i); val tile = Tile(t.getInt(0), t.getInt(1), t.getInt(2), t.getInt(3)); g[tile.r][tile.c] = tile }
        grid = g; score = o.getInt("score"); maxLv = o.getInt("maxLv"); undos = o.optInt("undos", UNDOS); hist.clear()
        nid = 1 + (tiles.maxOfOrNull { it.id } ?: 0)
        true
    }.getOrDefault(false)

    /** For the tests: a board as written, levels by row (-1 empty). */
    internal fun set(levels: List<List<Int>>) {
        grid = Array(SIZE) { r -> Array(SIZE) { c -> levels[r][c].takeIf { it >= 0 }?.let { Tile(nid++, it, r, c) } } }
        maxLv = tiles.maxOfOrNull { it.lv } ?: 0
    }

    companion object {
        const val SIZE = 4
        const val LEVELS = 12
        const val UNDOS = 3
        /** A whole snowman: "won" (the game goes on, to the hat, the scarf, the giant and the king). */
        const val WON = 7
    }
}

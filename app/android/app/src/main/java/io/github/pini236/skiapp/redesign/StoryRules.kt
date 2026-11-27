package io.github.pini236.skiapp.redesign

/**
 * Pure rules of the day story page, its player and the share sheet buttons (canvas DS1-DS5, DV4-DV8; handoff
 * `design/handoff/2026-11-27-redesign-day-pages/README.md` sections 3 and 4). No Android types, so they are unit tested.
 */
internal object StoryRules {
    /** Engine keys that never get a card (the fall and the off-piste part stay out, as before). */
    val HIDDEN = setOf("fall", "offpiste")

    /** One moment of the player lasts this long. */
    const val MOMENT_MS = 6000L

    /**
     * The cards of the story with no holes: each item of [story] (indexes into [keys]) whose key is hidden or has no words
     * ([hasWords] false) is replaced by the next item that is not in the story ("עוד מהיום"), in the day's order.
     * Returns the indexes of the cards, and of the rows left in "more".
     */
    fun fill(keys: List<String>, story: List<Int>, hasWords: (Int) -> Boolean): Pair<List<Int>, List<Int>> {
        val ok = { i: Int -> keys.getOrNull(i)?.let { it !in HIDDEN } == true && hasWords(i) }
        val pool = keys.indices.filter { it !in story && ok(it) }.toMutableList()
        val cards = ArrayList<Int>()
        for (i in story) {
            if (i !in keys.indices) continue
            if (ok(i)) cards += i else if (pool.isNotEmpty()) cards += pool.removeAt(0)
        }
        return cards to pool
    }

    /** Where a tap at [x] of a [width] wide player goes: the inline-end half goes back, the rest forward. */
    fun tapBack(x: Float, width: Float, rtl: Boolean): Boolean = if (rtl) x >= width / 2 else x < width / 2

    /** The moment after [i] of [count] (the last one is the finale); null past the end. */
    fun next(i: Int, count: Int): Int? = if (i + 1 < count) i + 1 else null

    /** The moment before [i]; stays on the first. */
    fun prev(i: Int): Int = if (i > 0) i - 1 else 0

    /** How full segment [seg] is while the player is at [cur] with [frac] of the moment elapsed. */
    fun segmentFill(seg: Int, cur: Int, frac: Float): Float = when {
        seg < cur -> 1f
        seg == cur -> frac.coerceIn(0f, 1f)
        else -> 0f
    }

    enum class Btn { IDLE, BUSY, SAVED, FAILED }

    /**
     * What a share-sheet button shows. [kind] is "share" or "save"; [pressed] the one running now, [saved] the saved flash
     * (save only), [failKind] the one that failed last.
     */
    fun button(kind: String, pressed: String?, saved: Boolean, failKind: String?): Btn = when {
        pressed == kind -> Btn.BUSY
        kind == "save" && saved -> Btn.SAVED
        failKind == kind -> Btn.FAILED
        else -> Btn.IDLE
    }

    /** The fill of a busy button: the video's progress, or full for a picture (which is quick). */
    fun fill(video: Boolean, progress: Float?): Float = if (!video) 1f else (progress ?: 0f).coerceIn(0f, 1f)

    /** The glide between two moments' cameras lasts this long (DS2). */
    const val GLIDE_MS = 300

    /** Ease-out for the glide, close to the board's (0.2, 0, 0, 1) curve: fast start, soft landing. [f] 0..1. */
    fun ease(f: Float): Float { val g = 1f - f.coerceIn(0f, 1f); return 1f - g * g * g * g }

    fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

    /** The pin on the mountain window (DS2: "28°"): the steepest pitch in degrees, the fastest in km/h; other moments have none. */
    fun pin(key: String, deg: Int?, kmh: Int?): String? = when (key) {
        "steepest" -> deg?.takeIf { it > 0 }?.let { "$it°" }
        "fastest" -> kmh?.takeIf { it > 0 }?.toString()
        else -> null
    }

    /** DS5: a day with one moment shows it big, then where the day went, then the picture. */
    fun solo(cards: Int): Boolean = cards == 1

    /** "m:ss" of [ms] (the video card's clock, "0:25 / 1:00"). */
    fun clock(ms: Long): String { val s = (ms.coerceAtLeast(0L) + 500L) / 1000L; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

    fun clockPair(posMs: Long, totalMs: Long): String = clock(posMs) + " / " + clock(totalMs)

    /** How much of the video has played, 0..1. */
    fun videoFrac(posMs: Long, totalMs: Long): Float = if (totalMs <= 0L) 0f else (posMs.toFloat() / totalMs).coerceIn(0f, 1f)

    enum class Press { RUN, QUEUE, IGNORE }

    /**
     * A tap on share or save (DV: while one runs, the other stays enabled and queues after it). [pressed] is the one running,
     * [queued] the one waiting. Nothing running: run it. The other running: queue this one (one place only). The same one again: ignore.
     */
    fun press(kind: String, pressed: String?, queued: String?): Press = when {
        pressed == null -> Press.RUN
        pressed == kind -> Press.IGNORE
        queued == kind -> Press.IGNORE
        else -> Press.QUEUE
    }

    /** The in-card video's state: playing, paused, or at its end (the replay circle). */
    enum class Film { PLAYING, PAUSED, ENDED }

    /** A tap on the card toggles play and pause; at the end it replays. */
    fun toggle(s: Film): Film = when (s) { Film.PLAYING -> Film.PAUSED; Film.PAUSED -> Film.PLAYING; Film.ENDED -> Film.PLAYING }
}

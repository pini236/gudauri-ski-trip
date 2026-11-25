package io.github.pini236.skiapp.redesign

import kotlin.math.hypot

/**
 * The mountain under the app's layers (the architect's review of 25.11.2026, docs/ARCHITECTURE.md, "Stage"): the live
 * model ([LiveView]) or the painted picture ([PictureStage]), and everything [LiveMountain] draws over it asks only
 * this. Places are metres of the site's projection (x east, y south, data/Geo.kt); the screen is pixels.
 */
internal interface Stage {
    // ---- where things are on the screen now
    fun toScreen(x: Float, y: Float, up: Float = 0f): FloatArray?
    fun screenTop(key: String): FloatArray?
    fun spotOf(key: String): FloatArray?
    fun overlay(labels: List<PeakData.Label>): LiveView.Overlay
    /** the chosen run's K11 marks for [LiveMountain] to draw; null where the stage draws them itself (the picture) */
    fun marks(): LiveView.Marks?
    fun chosenMs(): Float
    val drawn: Boolean
    fun northDeg(): Float
    fun awayFromHome(): Boolean
    fun onKobiSide(): Boolean

    // ---- choosing
    fun choose(key: String?)
    fun pickKey(key: String)
    fun moment(key: String, ms: Float)

    // ---- the view
    fun home()
    fun backToFront()
    fun frameRunAbove(key: String, free: Float?)
    fun frameLift(ends: FloatArray, free: Float = .54f, log: String = "redesign live lift framed")
    fun showRun(key: String, top: Float, bottom: Float, shift: Float)
    fun showKeys(keys: Set<String>, top: Float, bottom: Float, shift: Float, log: String)
    fun center(x: Float, y: Float, edge: Float)
    /** the view now, to come back to (opaque: each stage keeps its own kind) */
    fun viewNow(): Any
    fun backTo(s: Any, log: String)
    fun toVillage(pose: FloatArray): Boolean
    fun fromVillage()

    // ---- the look
    fun setTime(epochMs: Long)
    val lightTime: Long
    fun sunUp(): Boolean
    fun sunShares(): Map<String, Float>?
    fun setPal(p: PeakData.Pal?)
    fun setLens(kind: LensKind?, night: Boolean, runOpen: ((io.github.pini236.skiapp.data.Piste) -> Boolean?)?, snow: List<Pair<Float, Double>>? = null)
    fun setFocus(f: LiveRenderer.Focus?)
    fun setShift(px: Float)
    fun setQuiet(a: Float, runs: Float = 1f)

    // ---- what the stage tells
    var onChoose: ((String?) -> Unit)?
    var onTap: ((Float, Float) -> Unit)?
    var onPreTap: ((Float, Float) -> Boolean)?
    var wake: (() -> Unit)?
    var onTapAt: ((Float, Float) -> Unit)?
    var onMoment: ((Float, Float, Float) -> Unit)?
}

/**
 * The one tap (the architect's condition 2): the named run whose line, in screen pixels as the stage draws it now,
 * passes nearest the finger within [radiusPx]; a point the stage does not see (behind a ridge, or behind the eye) is
 * never chosen. Ties keep the first run in [keys]' order. Both stages call this, with the same radius in dp
 * ([RADIUS_DP], the contract's radius in site/data/peak.json).
 */
internal object StagePick {
    const val RADIUS_DP = 24f

    /** [lines] per run: screen points x0, y0, x1, y1, ...; [seen] per point, null when every point is seen */
    class Line(val key: String, val xy: FloatArray, val seen: BooleanArray?)

    fun nearest(lines: List<Line>, px: Float, py: Float, radiusPx: Float): String? {
        var best: String? = null; var bd = radiusPx
        for (l in lines) {
            val p = l.xy; val n = p.size / 2
            for (i in 0 until n - 1) {
                if (l.seen != null && (!l.seen[i] || !l.seen[i + 1])) continue
                val ax = p[i * 2]; val ay = p[i * 2 + 1]; val vx = p[i * 2 + 2] - ax; val vy = p[i * 2 + 3] - ay
                val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy).coerceAtLeast(1e-9f)).coerceIn(0f, 1f)
                val d = hypot(ax + t * vx - px, ay + t * vy - py)
                if (d < bd) { bd = d; best = l.key }
            }
        }
        return best
    }
}

/**
 * The picture or the live model (the opening's handoff, 25.11.2026): the user's choice, on the phone only (condition 6).
 * The picture is the default.
 */
internal object StagePrefs {
    private const val FILE = "redesign_stage"; private const val KEY = "live"
    fun live(c: android.content.Context) = c.getSharedPreferences(FILE, 0).getBoolean(KEY, false)
    fun setLive(c: android.content.Context, on: Boolean) = c.getSharedPreferences(FILE, 0).edit().putBoolean(KEY, on).apply()
}

/** What the stage under the layers is now. The pure rule, for the tests. */
internal object StageRules {
    enum class Shows { PICTURE, LIVE }
    /**
     * [chosenLive] the user's choice; [temp] a moment that needs the live model (the film, the Kobi side); [gles] whether
     * the live model can run here. Without GLES 3 the picture is the only stage (condition 5).
     */
    fun shows(chosenLive: Boolean, temp: Boolean, gles: Boolean) = if (gles && (chosenLive || temp)) Shows.LIVE else Shows.PICTURE
}

/**
 * The stage [LiveMountain] holds: the picture or the live model under it, swapped without the layers knowing. What the
 * layers set (the light, the lens, the focus, the raise, the quiet, the chosen run and the callbacks) is kept here and
 * given again to the stage that comes in, so a swap never loses it. With no stage yet (the data still loading) the
 * questions get "nothing there" and the settings wait.
 */
internal class StageHost : Stage {
    var impl: Stage? = null
        set(s) {
            field = s
            if (s == null) return
            s.onChoose = onChoose; s.onTap = onTap; s.onPreTap = onPreTap; s.wake = wake; s.onTapAt = onTapAt; s.onMoment = onMoment
            time?.let { s.setTime(it) }; s.setPal(pal)
            s.setLens(lens?.kind, lens?.night ?: false, lens?.runOpen, lens?.snow)
            s.setFocus(focus); s.setShift(shift); s.setQuiet(quietA, quietRuns); s.choose(chosen)
        }

    private class LensArgs(val kind: LensKind?, val night: Boolean, val runOpen: ((io.github.pini236.skiapp.data.Piste) -> Boolean?)?, val snow: List<Pair<Float, Double>>?)
    private var time: Long? = null; private var pal: PeakData.Pal? = null; private var lens: LensArgs? = null
    private var focus: LiveRenderer.Focus? = null; private var shift = 0f; private var quietA = 1f; private var quietRuns = 1f
    var chosen: String? = null; private set

    override fun toScreen(x: Float, y: Float, up: Float) = impl?.toScreen(x, y, up)
    override fun screenTop(key: String) = impl?.screenTop(key)
    override fun spotOf(key: String) = impl?.spotOf(key)
    override fun overlay(labels: List<PeakData.Label>) = impl?.overlay(labels) ?: LiveView.Overlay(emptyList(), FloatArray(0))
    override fun marks() = impl?.marks()
    override fun chosenMs() = impl?.chosenMs() ?: 0f
    override val drawn get() = impl?.drawn == true
    override fun northDeg() = impl?.northDeg() ?: 0f
    override fun awayFromHome() = impl?.awayFromHome() == true
    override fun onKobiSide() = impl?.onKobiSide() == true
    override fun choose(key: String?) { chosen = key; impl?.choose(key) }
    override fun pickKey(key: String) { chosen = key; impl?.pickKey(key) }
    override fun moment(key: String, ms: Float) { chosen = key; impl?.moment(key, ms) }
    override fun home() { impl?.home() }
    override fun backToFront() { impl?.backToFront() }
    override fun frameRunAbove(key: String, free: Float?) { impl?.frameRunAbove(key, free) }
    override fun frameLift(ends: FloatArray, free: Float, log: String) { impl?.frameLift(ends, free, log) }
    override fun showRun(key: String, top: Float, bottom: Float, shift: Float) { impl?.showRun(key, top, bottom, shift) }
    override fun showKeys(keys: Set<String>, top: Float, bottom: Float, shift: Float, log: String) { impl?.showKeys(keys, top, bottom, shift, log) }
    override fun center(x: Float, y: Float, edge: Float) { impl?.center(x, y, edge) }
    /** the view now, with the stage it belongs to: a view of the other stage is never given back */
    override fun viewNow(): Any = impl.let { s -> Pair(s, s?.viewNow()) }
    override fun backTo(s: Any, log: String) { val p = s as? Pair<*, *> ?: return; val v = p.second; if (p.first === impl && v != null) impl?.backTo(v, log) }
    override fun toVillage(pose: FloatArray) = impl?.toVillage(pose) == true
    override fun fromVillage() { impl?.fromVillage() }
    override fun setTime(epochMs: Long) { time = epochMs; impl?.setTime(epochMs) }
    override val lightTime get() = impl?.lightTime ?: time ?: System.currentTimeMillis()
    override fun sunUp() = impl?.sunUp() == true
    override fun sunShares() = impl?.sunShares()
    override fun setPal(p: PeakData.Pal?) { pal = p; impl?.setPal(p) }
    override fun setLens(kind: LensKind?, night: Boolean, runOpen: ((io.github.pini236.skiapp.data.Piste) -> Boolean?)?, snow: List<Pair<Float, Double>>?) {
        lens = LensArgs(kind, night, runOpen, snow); impl?.setLens(kind, night, runOpen, snow)
    }
    override fun setFocus(f: LiveRenderer.Focus?) { focus = f; impl?.setFocus(f) }
    override fun setShift(px: Float) { shift = px; impl?.setShift(px) }
    override fun setQuiet(a: Float, runs: Float) { quietA = a; quietRuns = runs; impl?.setQuiet(a, runs) }

    override var onChoose: ((String?) -> Unit)? = null
        set(f) { val g: ((String?) -> Unit)? = f?.let { h -> { k -> chosen = k; h(k) } }; field = g; impl?.onChoose = g }
    override var onTap: ((Float, Float) -> Unit)? = null; set(f) { field = f; impl?.onTap = f }
    override var onPreTap: ((Float, Float) -> Boolean)? = null; set(f) { field = f; impl?.onPreTap = f }
    @Volatile override var wake: (() -> Unit)? = null; set(f) { field = f; impl?.wake = f }
    override var onTapAt: ((Float, Float) -> Unit)? = null; set(f) { field = f; impl?.onTapAt = f }
    override var onMoment: ((Float, Float, Float) -> Unit)? = null; set(f) { field = f; impl?.onMoment = f }
}

package io.github.pini236.skiapp.redesign

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Where a picture (in its points) sits on the screen: screen = point · k + (x, y). */
data class Place(val k: Float, val x: Float, val y: Float) {
    fun on(p: Offset) = Offset(p.x * k + x, p.y * k + y)
    fun back(s: Offset) = Offset((s.x - x) / k, (s.y - y) / k)
    fun lerp(o: Place, t: Float) = Place(k + (o.k - k) * t, x + (o.x - x) * t, y + (o.y - y) * t)
}

/**
 * The geometry of the painted mountain on a screen, pure (unit tested, PeakGeoTest): the fit of the handoff, the free
 * view (pinch and drag), and which run a touch picks in the projection drawn now.
 */
object PeakGeo {
    /** The handoff's fit: k = max(width / 390, height / 844), centred. */
    fun fit(w: Float, h: Float, v: PeakData.View) = max(w / v.w, h / v.h)

    /**
     * The free view at zoom [z] (1 = the fit) around the picture's point [c]: the centre is held so the picture always
     * covers the screen (no edge of it comes in, at any zoom).
     */
    fun free(w: Float, h: Float, v: PeakData.View, z: Float, c: Offset): Place {
        val k = fit(w, h, v) * z
        val hx = w / (2 * k); val hy = h / (2 * k)
        val cx = c.x.coerceIn(min(hx, v.w / 2), max(v.w - hx, v.w / 2)); val cy = c.y.coerceIn(min(hy, v.h / 2), max(v.h - hy, v.h / 2))
        return Place(k, w / 2 - cx * k, h / 2 - cy * k)
    }

    /** The picture's point at the middle of the screen in [p] (what a pinch keeps there). */
    fun centre(w: Float, h: Float, p: Place) = p.back(Offset(w / 2, h / 2))

    /**
     * The chosen-run key whose line passes closest to the screen point [s], in the projection [pl] drawn now, within
     * [maxPx] screen pixels; only runs that can be chosen ([runs], with a selection in the view).
     */
    fun hit(v: PeakData.View, runs: Set<String>, pl: Place, s: Offset, maxPx: Float): String? {
        val p = pl.back(s); var best: String? = null; var bd = maxPx
        for (pi in v.pistes) {
            if (!pi.named || pi.key !in runs || pi.key !in v.sel) continue
            for (a in pi.l) { var i = 0; while (i + 1 < a.size) { val d = hypot(a[i] - p.x, a[i + 1] - p.y) * pl.k; if (d < bd) { bd = d; best = pi.key }; i += 2 } }
        }
        return best
    }

    /** A point on the screen where [key] is drawn in [pl] and nothing else is nearer (for the emulator's taps), or null. */
    fun spot(v: PeakData.View, runs: Set<String>, key: String, pl: Place, w: Float, h: Float, maxPx: Float): Offset? {
        val pi = v.pistes.firstOrNull { it.key == key } ?: return null
        var best: Offset? = null; var margin = 0f
        for (a in pi.l) { var i = 0; while (i + 1 < a.size) {
            val s = pl.on(Offset(a[i], a[i + 1]))
            if (s.x > 40 && s.y > 200 && s.x < w - 40 && s.y < h - 200 && hit(v, runs, pl, s, maxPx) == key) {
                // the farthest from any other run
                var other = Float.MAX_VALUE
                for (q in v.pistes) if (q.key != key) for (b in q.l) { var j = 0; while (j + 1 < b.size) { other = min(other, hypot(b[j] - a[i], b[j + 1] - a[i + 1]) * pl.k); j += 2 } }
                if (other > margin) { margin = other; best = s }
            }
            i += 2 } }
        return best
    }
}

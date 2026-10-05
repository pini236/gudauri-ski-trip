package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Lift
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import kotlin.math.hypot

/**
 * "Where am I" (round 19, decision 58; docs/ARCHITECTURE.md m-7 section 6): which run or lift a reading is on, by the
 * one rule the site uses too, checked against the shared test values in tools/fixtures/location-m7.json.
 *
 * Nothing leaves this class and nothing is written anywhere: it keeps only the run of the reading before and the last
 * three positions along a lift, in memory, and [reset] drops them when the button goes off.
 */
class Locator(runs: Runs, private val terrain: Terrain) {

    sealed interface Fix {
        /** Outside the terrain model: "not in Gudauri right now", no dot. */
        data object Outside : Fix
        /** The user allowed only an approximate location: a big circle, no snapping. */
        data object Approx : Fix
        /** Accuracy over 50 m, or over 30 m when not on a lift: no snapping. */
        data object Low : Fix
        data class OnLift(val lift: Lift) : Fix
        data class OnRun(val piste: Piste) : Fix
        /** Accurate, but not within 30 m of a run. */
        data object Free : Fix
    }

    private class Cable(val lift: Lift, val up: Boolean, val len: Float)

    private val pistes = runs.pistes.filter { it.named && it.lines.isNotEmpty() }
    private val cables = runs.lifts.filter { it.name.isNotBlank() && it.status != "inactive" && it.pts.size >= 4 }.map { l ->
        val p = l.pts
        Cable(l, terrain.elev(p[0], p[1]) < terrain.elev(p[p.size - 2], p[p.size - 1]), length(p))
    }

    private var prev: Piste? = null
    private var trackLift: Lift? = null
    private val track = FloatArray(3)
    private var tracked = 0

    fun reset() { prev = null; forget() }

    private fun forget() { trackLift = null; tracked = 0 }

    fun inside(x: Float, y: Float) = x >= terrain.x0 && x <= terrain.x1 && y >= terrain.y0 && y <= terrain.y1

    /** One reading, in the site's projection (metres), with the radius the phone reports. */
    fun feed(x: Float, y: Float, accuracy: Float, approximate: Boolean): Fix {
        if (!inside(x, y)) { reset(); return Fix.Outside }
        if (approximate) { reset(); return Fix.Approx }
        if (accuracy > LOW) { reset(); return Fix.Low }
        // a lift: within 25 m of its cable, and moving up along it in three readings in a row
        var best: Cable? = null; var bestD = Float.MAX_VALUE; var bestAt = 0f
        for (c in cables) {
            val d = near(x, y, c.lift.pts)
            if (d <= LIFT && d < bestD) { best = c; bestD = d; bestAt = if (c.up) along else c.len - along }
        }
        if (best == null) forget() else {
            if (trackLift !== best.lift) { trackLift = best.lift; tracked = 0 }
            if (tracked == 3) { track[0] = track[1]; track[1] = track[2]; tracked = 2 }
            track[tracked++] = bestAt
            if (tracked == 3 && track[1] > track[0] && track[2] > track[1]) { prev = null; return Fix.OnLift(best.lift) }
        }
        if (accuracy > SNAP) { prev = null; return Fix.Low }
        // a run: the nearest one within 30 m, and the one before stays while it is within 30 m and less than 10 m farther
        var run: Piste? = null; var runD = Float.MAX_VALUE
        for (p in pistes) { val d = distance(x, y, p); if (d < runD) { run = p; runD = d } }
        if (run == null || runD > SNAP) { prev = null; return Fix.Free }
        val before = prev
        if (before != null && before !== run) {
            val d = distance(x, y, before)
            if (d <= SNAP && d - runD < STICK) run = before
        }
        prev = run
        return Fix.OnRun(run)
    }

    private fun distance(x: Float, y: Float, p: Piste): Float {
        var best = Float.MAX_VALUE
        for (l in p.lines) best = minOf(best, near(x, y, l))
        return best
    }

    /** The position along the line of the last [near], from its first point, in metres. */
    private var along = 0f

    /** The distance from (x, y) to a polyline, and where along it the nearest point is ([along]). */
    private fun near(x: Float, y: Float, pts: FloatArray): Float {
        var best = Float.MAX_VALUE; var at = 0f; var acc = 0f
        var i = 2
        while (i < pts.size) {
            val ax = pts[i - 2]; val ay = pts[i - 1]; val bx = pts[i]; val by = pts[i + 1]
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
            val d = hypot(x - (ax + dx * t), y - (ay + dy * t))
            val l = kotlin.math.sqrt(l2)
            if (d < best) { best = d; at = acc + t * l }
            acc += l
            i += 2
        }
        along = at
        return best
    }

    companion object {
        const val LOW = 50f
        const val SNAP = 30f
        const val STICK = 10f
        const val LIFT = 25f

        private fun length(p: FloatArray): Float {
            var s = 0f; var i = 2
            while (i < p.size) { s += hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1]); i += 2 }
            return s
        }
    }
}

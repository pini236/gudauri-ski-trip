package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Piste
import kotlin.math.hypot

/**
 * The open end of a partial run (round 21, option A; docs/PARITY.md 16): the last stretch of the line fades to nothing, and nothing is
 * drawn across the hole. Only from conf medium up; the data says which end (start or end of the descent), the elevation model which
 * end of the drawn line is the top. A fade says only that the map stops drawing here, not that the run is open or safe. Site:
 * `applyOpenEnds` in site/js/app.js; not in 3D, as on the site.
 */
object OpenEnds {
    /** The metres of the line that fade. */
    const val FADE_M = 130f

    /** The line at index [line] of the run's drawn lines fades from A (the open end, gone) to B (whole), in metres. */
    class Fade(val line: Int, val ax: Float, val ay: Float, val bx: Float, val by: Float)

    fun fades(p: Piste, elev: (Float, Float) -> Float): List<Fade> {
        val out = ArrayList<Fade>()
        val done = HashSet<String>()
        for (o in p.open) {
            if (o.conf != "medium" && o.conf != "high") continue
            // both ends of every drawn line, and the one that is the top (start) or the bottom (end) of the whole run
            var best = -1; var bestFirst = true; var bestH = 0f
            for ((i, l) in p.lines.withIndex()) {
                if (l.size < 4) continue
                for (first in booleanArrayOf(true, false)) {
                    val x = if (first) l[0] else l[l.size - 2]; val y = if (first) l[1] else l[l.size - 1]
                    val h = elev(x, y)
                    val better = best < 0 || (if (o.at == "start") h > bestH else h < bestH)
                    if (better) { best = i; bestFirst = first; bestH = h }
                }
            }
            if (best < 0 || !done.add("${o.at}$best")) continue
            val l = p.lines[best]
            val n = l.size / 2
            // L[0] is the open end
            fun px(k: Int) = if (bestFirst) l[k * 2] else l[(n - 1 - k) * 2]
            fun py(k: Int) = if (bestFirst) l[k * 2 + 1] else l[(n - 1 - k) * 2 + 1]
            var acc = 0f; var i = 1
            while (i < n && acc + hypot(px(i) - px(i - 1), py(i) - py(i - 1)) < FADE_M) { acc += hypot(px(i) - px(i - 1), py(i) - py(i - 1)); i++ }
            if (i >= n) i = n - 1 // a short stretch: the whole of it fades
            if (hypot(px(i) - px(0), py(i) - py(0)) < 1f) continue
            out += Fade(best, px(0), py(0), px(i), py(i))
        }
        return out
    }
}

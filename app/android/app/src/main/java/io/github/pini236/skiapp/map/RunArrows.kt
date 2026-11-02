package io.github.pini236.skiapp.map

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Small arrows down the chosen run (round 21, docs/PARITY.md 16): the first at 90 m, then every 150 m of the line, pointing the way it
 * runs downhill (the paint's segments are top first, so their own direction is the fall of the elevation model). The site thins them
 * by zoom to about 60 px apart (`layoutArrows` in site/js/app.js); [step] says how many to skip.
 */
object RunArrows {
    const val FIRST_M = 90f
    const val EVERY_M = 150f

    /**
     * [segs] x1,y1,x2,y2 for each piece of the chosen run's lines, one line after the other (a piece that does not start where the
     * one before ended begins a new line). Returns x, y, angle in degrees for each arrow.
     */
    fun along(segs: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        var acc = 0f; var next = FIRST_M
        var px = Float.NaN; var py = Float.NaN
        for (i in 0 until segs.size / 4) {
            val x1 = segs[i * 4]; val y1 = segs[i * 4 + 1]; val x2 = segs[i * 4 + 2]; val y2 = segs[i * 4 + 3]
            if (px.isNaN() || hypot(x1 - px, y1 - py) > 1f) { acc = 0f; next = FIRST_M } // a new line
            if (acc >= next) {
                out += x1; out += y1; out += Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
                next += EVERY_M
            }
            acc += hypot(x2 - x1, y2 - y1)
            px = x2; py = y2
        }
        return out.toFloatArray()
    }

    /** Every [step]th arrow stays: about 60 px apart on screen, the arrows being 150 m apart on the ground. [mPerPx]: metres per screen pixel. */
    fun step(mPerPx: Float, density: Float): Int = maxOf(1, Math.ceil((60.0 * mPerPx * 1.0 / EVERY_M) * density).toInt())
}

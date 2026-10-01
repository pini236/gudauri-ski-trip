package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Terrain
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Where the sun is over Gudauri (low-precision solar position, good to a fraction of a degree). */
object Sun {
    private const val RAD = Math.PI / 180

    /** Azimuth from north, clockwise, and altitude above the horizon, both in radians. */
    fun position(epochMs: Long, latDeg: Double = 42.475, lonDeg: Double = 44.48): Pair<Double, Double> {
        val d = epochMs / 86_400_000.0 + 2440587.5 - 2451545.0
        val g = (357.529 + 0.98560028 * d) * RAD
        val q = 280.459 + 0.98564736 * d
        val l = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * RAD
        val e = (23.439 - 0.00000036 * d) * RAD
        val ra = atan2(cos(e) * sin(l), cos(l))
        val dec = asin(sin(e) * sin(l))
        val gmst = (18.697374558 + 24.06570982441908 * d) * 15.0
        val h = ((gmst + lonDeg) * RAD) - ra
        val lat = latDeg * RAD
        val alt = asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(h))
        val az = atan2(-sin(h), tan(dec) * cos(lat) - sin(lat) * cos(h))
        return Pair((az + 2 * Math.PI) % (2 * Math.PI), alt)
    }

    /** Unit vector towards the sun in world space (x east, y up, z south). */
    fun direction(az: Double, alt: Double) = floatArrayOf(
        (sin(az) * cos(alt)).toFloat(), sin(alt).toFloat(), (-cos(az) * cos(alt)).toFloat(),
    )

    /**
     * Soft shadow per grid vertex: march towards the sun over the elevation grid and see whether the
     * mountain rises above the ray. 0 = in the sun, 1 = in the shade.
     */
    fun shadows(t: Terrain, dir: FloatArray): FloatArray {
        val out = FloatArray(t.nx * t.ny)
        val horiz = kotlin.math.sqrt(dir[0] * dir[0] + dir[2] * dir[2]).coerceAtLeast(1e-4f)
        val ux = dir[0] / horiz; val uz = dir[2] / horiz
        val rise = dir[1] / horiz // metres up per metre along the ground
        val step = 35f
        val maxD = 9000f
        for (r in 0 until t.ny) for (c in 0 until t.nx) {
            val x = t.x0 + c * t.sx; val z = t.y0 + r * t.sy
            val h0 = t.at(c, r) + 3f
            var s = 0f
            var dd = step
            while (dd < maxD) {
                val px = x + ux * dd; val pz = z + uz * dd
                if (px < t.x0 || px > t.x1 || pz < t.y0 || pz > t.y1) break
                val over = t.elev(px, pz) - (h0 + rise * dd)
                if (over > 0) { s = maxOf(s, (over / 30f).coerceAtMost(1f)); if (s >= 1f) break }
                if (h0 + rise * dd > 3400f) break
                dd += step * (1f + dd / 3000f)
            }
            out[r * t.nx + c] = s
        }
        return out
    }
}

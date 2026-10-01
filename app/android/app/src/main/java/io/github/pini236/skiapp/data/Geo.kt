package io.github.pini236.skiapp.data

import kotlin.math.PI
import kotlin.math.cos

/** The site's projection (site/js/app.js): metres, x east, y south, around lat0/lon0. */
object Geo {
    const val LAT0 = 42.51
    const val LON0 = 44.495
    private val kx = 111320.0 * cos(LAT0 * PI / 180)
    private const val KY = 111320.0

    fun x(lon: Double) = ((lon - LON0) * kx).toFloat()
    fun y(lat: Double) = (-(lat - LAT0) * KY).toFloat()
}

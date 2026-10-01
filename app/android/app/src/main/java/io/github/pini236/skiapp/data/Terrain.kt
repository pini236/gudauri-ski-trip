package io.github.pini236.skiapp.data

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class Peak(val name: String, val ele: Int, val x: Float, val y: Float)

/** Elevation grid from site/data/terrain.json: nx*ny int16 metres, little-endian, row 0 at y0 (north). */
class Terrain(
    val nx: Int, val ny: Int,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val h: ShortArray,
    val peaks: List<Peak>,
) {
    val sx = (x1 - x0) / (nx - 1)
    val sy = (y1 - y0) / (ny - 1)

    fun at(c: Int, r: Int): Float = h[r * nx + c].toFloat()

    /** Bilinear elevation in metres, clamped to the grid's edge (the site's elev()). */
    fun elev(x: Float, y: Float): Float {
        val c = ((x - x0) / sx).coerceIn(0f, nx - 1.0001f)
        val r = ((y - y0) / sy).coerceIn(0f, ny - 1.0001f)
        val c0 = c.toInt(); val r0 = r.toInt(); val fc = c - c0; val fr = r - r0
        val i = r0 * nx + c0
        return (h[i] * (1 - fc) + h[i + 1] * fc) * (1 - fr) + (h[i + nx] * (1 - fc) + h[i + nx + 1] * fc) * fr
    }

    companion object {
        fun parse(json: String): Terrain {
            val t = JSONObject(json)
            val d = t.getJSONObject("dem")
            val bytes = Base64.getDecoder().decode(d.getString("b64"))
            val h = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(h)
            val pk = t.getJSONArray("peaks")
            val peaks = (0 until pk.length()).map {
                val p = pk.getJSONObject(it)
                Peak(p.getString("n"), p.getInt("ele"), p.getDouble("x").toFloat(), p.getDouble("y").toFloat())
            }
            return Terrain(
                d.getInt("nx"), d.getInt("ny"),
                d.getDouble("x0").toFloat(), d.getDouble("y0").toFloat(),
                d.getDouble("x1").toFloat(), d.getDouble("y1").toFloat(),
                h, peaks,
            )
        }
    }
}

package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every named run of the real map lands whole on a phone screen, between the bars (emulator finding: Sadzele 2). */
class FramingRealTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")

    @Test fun everyRunFitsOnThePhone() {
        val t = Terrain.parse(File(dir, "terrain.json").readText())
        val scene = MapScene(t, Runs.parse(File(dir, "runs-and-lifts.json").readText()))
        val w = 1080f; val h = 2127f
        val box = Framing.Box()
        val bad = ArrayList<String>()
        for (p in scene.runs.pistes) {
            if (!p.named) continue
            val path = scene.topDown(p).fold(FloatArray(0)) { acc, l -> acc + l }
            if (path.size < 6) continue
            var s = Framing.fit(path, w, h, { x, z -> t.elev(x, z) })
            // what the renderer does every frame: the centre stays on the map, on the snow
            val cx = s.tx.coerceIn(t.x0, t.x1); val cz = s.tz.coerceIn(t.y0, t.y1)
            s = s.copy(tx = cx, tz = cz, ty = t.elev(cx, cz))
            val b = Framing.bounds(s, path, 1, w, h)
            val eye = FloatArray(3).also { OrbitCamera.eye(s, it) }
            val under = t.elev(eye[0], eye[2])
            val why = when {
                b == null -> "partly behind the camera"
                b[0] < box.left * w - 2 || b[1] > box.right * w + 2 || b[2] < box.top * h - 2 || b[3] > box.bottom * h + 2 ->
                    "outside the box: x ${b[0].toInt()}..${b[1].toInt()}, y ${b[2].toInt()}..${b[3].toInt()}"
                eye[1] < under + 40 -> "the eye is inside the mountain (${eye[1].toInt()} m, snow ${under.toInt()} m)"
                else -> null
            }
            if (why != null) bad += "${p.key}: $why · dist ${s.dist.toInt()} yaw ${Math.toDegrees(s.yaw.toDouble()).toInt()}°"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}

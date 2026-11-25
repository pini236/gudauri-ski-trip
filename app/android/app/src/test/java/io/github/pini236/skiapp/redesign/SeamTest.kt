package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.map.MapScene
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The front seam (the designer's step 4, 25.11.2026) on the real mountain: the model's last 1,500 m bend to the ring's
 * heights. The runs and the lifts are drawn at the model's own heights, so none of them may stand where the ground moved
 * enough to bury a line or lift it off the snow.
 */
class SeamTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val terrain by lazy { Terrain.parse(File(dir, "terrain.json").readText()) }
    private val scene by lazy { MapScene(terrain, Runs.parse(File(dir, "runs-and-lifts.json").readText())) }
    private val ring by lazy {
        LiveRing.build(JSONObject(File("src/main/assets/redesign/live/ring.json").readText()), terrain.x0, terrain.y0, terrain.x1, terrain.y1, terrain::elev)
    }

    @Test fun noRunOrLiftStandsWhereTheGroundBends() {
        var worst = 0f; var where = ""
        for (p in scene.runs.pistes) for (l in scene.draped[p.key] ?: emptyList()) for (i in 0 until l.size / 3) {
            val x = l[i * 3]; val z = l[i * 3 + 2]; val h = terrain.elev(x, z)
            val d = abs(ring.bent(x, z, h) - h)
            if (d > worst) { worst = d; where = "${p.key} at ${x.toInt()},${z.toInt()}, ${ring.inward(x, z).toInt()} m in" }
        }
        // lifts: their lines in projected metres, x and y pairs
        for (lift in scene.runs.lifts) for (k in 0 until lift.pts.size / 2) {
            val x = lift.pts[k * 2]; val z = lift.pts[k * 2 + 1]; val h = terrain.elev(x, z)
            val d = abs(ring.bent(x, z, h) - h)
            if (d > worst) { worst = d; where = "${lift.name} at ${x.toInt()},${z.toInt()}" }
        }
        // a line is drawn a few metres over the snow and lifted toward the eye: 4 m of bend is not seen
        assertTrue("$worst m under $where", worst <= 4f)
    }
}

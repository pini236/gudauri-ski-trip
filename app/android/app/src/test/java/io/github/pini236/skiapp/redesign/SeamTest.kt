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
        LiveRing.build(JSONObject(File("src/main/assets/redesign/live/ring.json").readText()), terrain.x0, terrain.y0, terrain.x1, terrain.y1, elev = terrain::elev)
    }

    @Test fun nothingToDrawOrTouchStandsWhereTheGroundBends() {
        // the architect's condition 3: no run, lift, name or place to touch in the last 1,500 m of the model (the bend is
        // only in the drawing; terrain.json and every height the app reads elsewhere stay as they are)
        var near = Float.MAX_VALUE; var what = ""
        fun at(x: Float, z: Float, name: String) { val d = ring.inward(x, z); if (d < near) { near = d; what = name } }
        for (p in scene.runs.pistes) for (l in scene.draped[p.key] ?: emptyList()) for (i in 0 until l.size / 3) at(l[i * 3], l[i * 3 + 2], p.key)
        for (lift in scene.runs.lifts) for (k in 0 until lift.pts.size / 2) at(lift.pts[k * 2], lift.pts[k * 2 + 1], lift.name)
        for (pk in terrain.peaks) at(pk.x, pk.y, "peak ${pk.ele}")
        // the picture's names (Sadzele, Bidara, Kudebi, New Gudauri) stand on peaks and the village: covered by both
        val live = LiveData.parse(File("src/main/assets/redesign/live/peak-live.json").readText())
        for (k in 0 until live.village.size / 3) at(live.village[k * 3], live.village[k * 3 + 2], "village light")
        assertTrue("$what is ${near.toInt()} m inside the edge", near >= LiveRing.BEND)
    }
}

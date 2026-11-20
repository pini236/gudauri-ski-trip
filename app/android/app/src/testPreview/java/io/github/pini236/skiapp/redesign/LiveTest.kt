package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Phase B's pure parts (the architect's conditions, docs/ARCHITECTURE.md): the live model opens from the picture's
 * side, and its one detail texture tiles and keeps to its budget.
 */
class LiveTest {
    private val d = PeakData.parse(File("src/preview/assets/redesign/peak-poc.json").readText())

    @Test fun theLiveModelOpensFromThePicturesSide() {
        // the picture looks north: of two points on one meridian, the northern one is farther (higher on the screen)
        val p = d.hero.proj!!
        for (c in d.check) {
            val south = p.project(c.lat, c.lon, c.z) ?: continue; val north = p.project(c.lat + 0.004, c.lon, c.z) ?: continue
            assertTrue("north of ${c.what} is farther in the picture", north.y < south.y)
        }
        // and yaw 0 of the map's camera looks north (map/Camera.kt: forward is -z; data/Geo.kt: z grows south)
        assertEquals(0f, LiveOpen.YAW, 0f)
    }

    @Test fun theDetailTextureTilesAndKeepsItsBudget() {
        val n = 128
        val px = Detail.make(n)
        assertEquals(n * n * 4, px.size)
        // the full size on the GPU with its mipmaps: well inside 1 MB packed + 32 MB of GPU memory
        assertTrue(LiveRenderer.DETAIL * LiveRenderer.DETAIL * 4 <= 1 shl 20)
        fun at(x: Int, y: Int, c: Int) = px[((y * n + x) * 4) + c].toInt() and 0xFF
        // the seam (last column to the first, last row to the first) is no rougher than any step inside
        for (c in 0..2) {
            var inside = 0.0; var seamX = 0.0; var seamY = 0.0
            for (y in 0 until n) { for (x in 0 until n - 1) inside += abs(at(x, y, c) - at(x + 1, y, c)); seamX += abs(at(n - 1, y, c) - at(0, y, c)) }
            for (x in 0 until n) seamY += abs(at(x, n - 1, c) - at(x, 0, c))
            val step = inside / (n * (n - 1))
            assertTrue("channel $c across x: ${seamX / n} vs $step", seamX / n <= step * 3 + 2)
            assertTrue("channel $c across y: ${seamY / n} vs $step", seamY / n <= step * 3 + 2)
        }
    }
}

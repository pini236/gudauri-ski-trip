package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Runs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Round 21 on the phone: the data's name windows read without breaking, and the site's fit (labelfit.js) port for port. */
class LabelFitTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val line = floatArrayOf(0f, 0f, 500f, 0f, 1000f, 0f) // 1 km east

    @Test fun aNameGoesAboveTheLineWhenThereIsRoom() {
        val f = LabelFit.fit(listOf(doubleArrayOf(0.0, 0.0, 1000.0, 60.0, 60.0)), listOf(line), 100f, 10f, 6f)
        assertNotNull(f)
        assertEquals('L', f!!.side)
        assertTrue("the baseline is above the line (y up is smaller)", f.pts[1] < 0f)
        assertEquals(-6f, f.pts[1], 0.001f)
        assertEquals(500f - 55f, f.pts.first(), 0.01f) // 1.1 x the name width, centred on the middle of the window
    }

    @Test fun belowTheLineWhenAboveIsFull() {
        val f = LabelFit.fit(listOf(doubleArrayOf(0.0, 0.0, 1000.0, 5.0, 60.0)), listOf(line), 100f, 10f, 6f)
        assertEquals('R', f!!.side)
        assertTrue(f.pts[1] > 0f)
    }

    @Test fun noRoomMeansNoName() {
        assertNull(LabelFit.fit(listOf(doubleArrayOf(0.0, 0.0, 1000.0, 5.0, 5.0)), listOf(line), 100f, 10f, 6f))
        assertNull("a window shorter than the name", LabelFit.fit(listOf(doubleArrayOf(0.0, 0.0, 50.0, 60.0, 60.0)), listOf(line), 100f, 10f, 6f))
        assertNull("a segment that is not a line", LabelFit.fit(listOf(doubleArrayOf(1.0, 0.0, 1000.0, 60.0, 60.0)), listOf(line, null), 100f, 10f, 6f))
        assertNull(LabelFit.fit(emptyList(), listOf(line), 100f, 10f, 6f))
    }

    @Test fun aRunToTheWestIsReadLeftToRightWithTheSidesSwapped() {
        val west = floatArrayOf(1000f, 0f, 500f, 0f, 0f, 0f)
        // the data's left (of travel) is 60 clear, the right 5: reading west-to-east flips them, so the room is below
        val f = LabelFit.fit(listOf(doubleArrayOf(0.0, 0.0, 1000.0, 60.0, 5.0)), listOf(west), 100f, 10f, 6f)
        assertEquals('R', f!!.side)
        assertTrue("the baseline runs from the west end to the east end", f.pts[0] < f.pts[f.pts.size - 2])
    }

    @Test fun theDatasWindowsReadAndPointAtRealSegments() {
        val runs = Runs.parse(File(dir, "runs-and-lifts.json").readText())
        val withLabel = runs.pistes.filter { it.label.isNotEmpty() }
        assertTrue("the data carries name windows", withLabel.size >= 20)
        assertTrue(runs.pistes.any { it.shared.isNotEmpty() })
        for (p in withLabel) for (w in p.label) assertNotNull("${p.key}: window ${w[0]}", p.segLines.getOrNull(w[0].toInt()))
        for (p in runs.pistes) for (w in p.shared) assertNotNull("${p.key}: shared ${w.other}", p.segLines.getOrNull(w.w[0].toInt()))
        // the lines are the same arrays the segments hold: nothing is copied
        for (p in runs.pistes) assertEquals(p.lines.size, p.segLines.count { it != null })
    }

    @Test fun aFieldOfAnotherVersionOrOfAnotherShapeIsNoWindows() {
        assertTrue(Runs.windows(org.json.JSONObject("""{"v":2,"a":[[0,1,2,3,4]]}""")).isEmpty())
        assertTrue(Runs.windows(org.json.JSONObject("""{"v":1,"a":"x"}""")).isEmpty())
        assertTrue(Runs.windows(null).isEmpty())
        assertEquals(1, Runs.windows(org.json.JSONObject("""{"v":1,"a":[[0,1,2,3,4,"B"]]}""")).size)
    }

    @Test fun aSharedStretchIsNamedAsOneFamilyWhenItIsOne() {
        assertEquals("Tatra 1 · 2", LabelFit.sharedName("Tatra 1", "Tatra 2"))
        assertEquals("Tatra 1 · 2", LabelFit.sharedName("Tatra 2", "Tatra 1"))
        assertEquals("Goodaura 1", LabelFit.sharedName("Goodaura 1", "Kudebi 2"))
        assertEquals("Shino", LabelFit.sharedName("Shino", "Tatra 2"))
    }
}

package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.OpenEnd
import io.github.pini236.skiapp.data.Piste
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The open end of a partial run (round 21): the field reads without breaking, and the line fades only from medium confidence up. */
class OpenEndsTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val fx = JSONObject(File(dir.parentFile.parentFile, "tools/fixtures/labels-open.json").readText())

    private fun run(vararg open: OpenEnd, line: FloatArray = floatArrayOf(0f, 0f, 100f, 0f, 200f, 0f, 400f, 0f)) =
        Piste("A", "A", "blue", true, "run", listOf(line), open = open.toList())

    @Test fun theDatasOpenFieldIsTheFixtures() {
        val runs = Runs.parse(File(dir, "runs-and-lifts.json").readText())
        val want = fx.getJSONObject("open")
        for (k in want.keys()) {
            val p = runs.pistes.first { it.key == k }
            val a = want.getJSONArray(k)
            assertEquals(k, (0 until a.length()).map { a.getJSONObject(it).getString("at") + ":" + a.getJSONObject(it).getString("conf") }, p.open.map { it.at + ":" + it.conf })
        }
        assertEquals("only the runs the fixture lists carry the field", want.length(), runs.pistes.count { it.open.isNotEmpty() })
    }

    @Test fun aFieldThatDoesNotReadIsNone() {
        assertTrue(Runs.openEnds(null).isEmpty())
        assertTrue(Runs.openEnds(JSONArray("""["x"]""")).isEmpty())
        assertEquals(1, Runs.openEnds(JSONArray("""[{"at":"end","conf":"medium","src":"s"},{"at":"middle","conf":"high"}]""")).size)
    }

    @Test fun fadesTheLowEndOfTheLineForAnEndAndTheHighForAStart() {
        val down = { x: Float, _: Float -> 2000f - x } // the line runs downhill to the east
        val end = OpenEnds.fades(run(OpenEnd("end", "medium")), down)
        assertEquals(1, end.size)
        assertEquals(400f, end[0].ax, 0.001f) // the open end is the east end
        assertTrue("B is 130 m or more along, toward the top (the first point beyond 130 m)", end[0].bx < 400f && 400f - end[0].bx >= 130f)
        val start = OpenEnds.fades(run(OpenEnd("start", "high")), down)
        assertEquals(0f, start[0].ax, 0.001f)
    }

    @Test fun lowConfidenceDrawsNothing() {
        assertTrue(OpenEnds.fades(run(OpenEnd("end", "low")), { x, _ -> -x }).isEmpty())
        assertTrue(OpenEnds.fades(run(), { x, _ -> -x }).isEmpty())
    }

    @Test fun aShortLineFadesWhole() {
        val f = OpenEnds.fades(run(OpenEnd("end", "medium"), line = floatArrayOf(0f, 0f, 60f, 0f)), { x, _ -> -x })
        assertEquals(1, f.size)
        assertEquals(60f, f[0].ax, 0.001f); assertEquals(0f, f[0].bx, 0.001f)
    }

    @Test fun everyFixtureRunFadesOnlyAtItsMediumEnds() {
        val runs = Runs.parse(File(dir, "runs-and-lifts.json").readText())
        val t = Terrain.parse(File(dir, "terrain.json").readText())
        val want = fx.getJSONObject("open")
        for (k in want.keys()) {
            val p = runs.pistes.first { it.key == k }
            val a = want.getJSONArray(k)
            val drawn = (0 until a.length()).count { a.getJSONObject(it).getString("conf").let { c -> c == "medium" || c == "high" } }
            assertEquals(k, drawn, OpenEnds.fades(p) { x, y -> t.elev(x, y) }.size)
        }
    }
}

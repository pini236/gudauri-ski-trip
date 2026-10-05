package io.github.pini236.skiapp.map

import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Where am I" against the shared test values (tools/fixtures/location-m7.json, m-7 section 6): the site feeds the
 * same readings and must give the same states, as with the slope values of X-3.
 */
class LocateTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val runs = Runs.parse(File(dir, "runs-and-lifts.json").readText())
    private val terrain = Terrain.parse(File(dir, "terrain.json").readText())
    private val fixture = JSONObject(File(dir, "../../tools/fixtures/location-m7.json").readText())

    private fun name(f: Locator.Fix) = when (f) {
        Locator.Fix.Outside -> "outside"
        Locator.Fix.Approx -> "approx"
        Locator.Fix.Low -> "low"
        Locator.Fix.Free -> "free"
        is Locator.Fix.OnLift -> "lift ${f.lift.name}"
        is Locator.Fix.OnRun -> "run ${f.piste.key}"
    }

    @Test fun everyReadingOfEveryCaseAsTheSharedValues() {
        val cases = fixture.getJSONArray("cases")
        assertTrue(cases.length() >= 7)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val loc = Locator(runs, terrain)
            val rs = c.getJSONArray("readings")
            for (j in 0 until rs.length()) {
                val r = rs.getJSONObject(j)
                val e = r.getJSONObject("expect")
                val want = e.getString("state") + (e.optString("run").takeIf { it.isNotEmpty() }?.let { " $it" } ?: "") +
                    (e.optString("lift").takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
                val got = loc.feed(Geo.x(r.getDouble("lon")), Geo.y(r.getDouble("lat")), r.getDouble("accuracy").toFloat(), r.optBoolean("approximate"))
                assertEquals("${c.getString("name")}, reading ${j + 1}", want, name(got))
            }
        }
    }

    @Test fun offReturnsToNothing() {
        // after reset nothing of the readings before is left: the run between the two is the nearer one again
        val loc = Locator(runs, terrain)
        assertEquals("run Kudebi 1", name(loc.feed(Geo.x(44.502039), Geo.y(42.493934), 6f, false)))
        loc.reset()
        assertEquals("run Kudebi 2", name(loc.feed(Geo.x(44.502894), Geo.y(42.494613), 6f, false)))
    }
}

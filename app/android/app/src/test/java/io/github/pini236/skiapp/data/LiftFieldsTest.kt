package io.github.pini236.skiapp.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The data comes down from the site without a new app (SiteData), so a build in a phone reads whatever the site holds
 * tomorrow. A lift may gain fields (architect, 31.10.2026: "speed" and a "research" object on lifts, and a corrected
 * "dur"); an unknown field must never fail the parse or reject the download (docs/ARCHITECTURE.md).
 */
class LiftFieldsTest {
    private val site = File(System.getProperty("site.data") ?: "../../../site/data")
    private val text = File(site, "runs-and-lifts.json").readText()

    /** The real file, with [change] applied to every lift. */
    private fun withLifts(change: (JSONObject, Int) -> Unit): String {
        val o = JSONObject(text)
        val ls = o.getJSONArray("lifts")
        for (i in 0 until ls.length()) change(ls.getJSONObject(i), i)
        return o.toString()
    }

    private fun research() = JSONObject().put("conf", "medium").put("status", "osm").put("notes", "x").put("sources", org.json.JSONArray().put("a"))
        .put("date", "2026-10-31").put("extra", JSONObject().put("deep", org.json.JSONArray().put(1).put(null)))

    @Test fun anUnknownFieldOnALiftIsIgnored() {
        val before = Runs.parse(text)
        for (speed in listOf<Any?>(5.0, 5, "5.0", "fast", JSONObject.NULL, org.json.JSONArray().put(1.2), JSONObject().put("ms", 5.0))) {
            val json = withLifts { l, _ -> l.put("speed", speed); l.put("research", research()); l.put("newThing", true) }
            val after = Runs.parse(json)
            assertEquals("speed=$speed", before.lifts.size, after.lifts.size)
            assertEquals(before.lifts.map { it.name }, after.lifts.map { it.name })
            assertEquals(before.lifts.map { it.len }, after.lifts.map { it.len })
            assertEquals(before.lifts.map { it.dur }, after.lifts.map { it.dur })
            assertEquals(before.lifts.map { it.pts.toList() }, after.lifts.map { it.pts.toList() })
            assertTrue("speed=$speed", SiteData.validRuns(json))
        }
    }

    @Test fun aNullResearchOrSpeedIsNotAField() {
        val json = withLifts { l, _ -> l.put("research", JSONObject.NULL); l.put("speed", JSONObject.NULL) }
        assertEquals(Runs.parse(text).lifts.map { it.dur }, Runs.parse(json).lifts.map { it.dur })
        assertTrue(SiteData.validRuns(json))
    }

    /** "dur" is minutes: a corrected one may be written as text or as a whole number, and the panel says the same. */
    @Test fun aCorrectedDurIsReadAsText() {
        val asText = Runs.parse(withLifts { l, i -> if (i == 0) l.put("dur", "6") })
        val asNumber = Runs.parse(withLifts { l, i -> if (i == 0) l.put("dur", 6) })
        assertEquals("6", asText.lifts[0].dur)
        assertEquals("6", asNumber.lifts[0].dur)
    }

    @Test fun aLiftWithoutDurHasNone() {
        val json = withLifts { l, i -> if (i == 0) l.remove("dur") }
        assertEquals(null, Runs.parse(json).lifts[0].dur)
        assertTrue(SiteData.validRuns(json))
    }
}

package io.github.pini236.skiapp.redesign

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The completion round, part 1 (P05): the records against yourself, matched to the saved days. */
class RecordsTest {
    private fun descent(t0: Long, t1: Long, dist: Double, vararg runs: String) = JSONObject().put("type", "descent").put("t0", t0).put("t1", t1).put("dist_m", dist)
        .put("legs", JSONArray().put(JSONObject().put("kind", "run").put("runs", JSONArray(runs.toList()))))
    private fun day(id: String, start: Long, kmh: Double, maxT: Long, drop: Double, ski: Double, vararg ds: JSONObject, moments: JSONObject? = null) =
        Records.SavedDay(id, start, JSONObject().put("mode", "day").put("start", start)
            .put("stats", JSONObject().put("max_kmh", kmh).put("drop_m", drop).put("ski_dist_m", ski).put("max_at", JSONObject().put("t", maxT).put("lat", 42.48).put("lon", 44.48)))
            .put("segments", JSONArray(ds.toList())), moments)
    private fun record(what: String) = JSONObject().put("items", JSONArray().put(JSONObject().put("key", "record").put("p", JSONObject().put("what", what))))

    @Test fun eachRecordFindsItsDayRunAndPlace() {
        val d1 = day("a", 1000, 61.2, 150, 4000.0, 20000.0, descent(100, 200, 2400.0, "tatra-2"), descent(300, 400, 3100.0, "kudebi", "soliko"))
        val d2 = day("b", 2000, 68.9, 2150, 5545.0, 31300.0, descent(2100, 2200, 2900.0, "sadzele-1"), descent(2300, 2400, 900.0, "baby"),
            moments = record("max_kmh"))
        val best = JSONObject().put("max_kmh", 68.9).put("longest_m", 3100.0).put("drop_m", 5545.0).put("descents", 2).put("ski_dist_m", 31300.0)
        val rows = Records.rows(best, listOf(d1, d2)).associateBy { it.what }
        assertEquals("b", rows["max_kmh"]!!.day!!.id); assertEquals(listOf("sadzele-1"), rows["max_kmh"]!!.runs); assertTrue(rows["max_kmh"]!!.fresh)
        assertEquals("a", rows["longest_m"]!!.day!!.id); assertEquals(listOf("kudebi", "soliko"), rows["longest_m"]!!.runs); assertFalse(rows["longest_m"]!!.fresh)
        assertEquals("b", rows["drop_m"]!!.day!!.id)
        // two days of two descents: the record was set on the first
        assertEquals("a", rows["descents"]!!.day!!.id)
        // no steep record yet: a dash, nothing to open
        assertNull(rows["steep_deg"]!!.value); assertNull(rows["steep_deg"]!!.day)
    }

    @Test fun aDeletedDayKeepsTheRecordWithoutItsDay() {
        val d2 = day("b", 2000, 50.0, 2150, 100.0, 1000.0, descent(2100, 2200, 900.0, "baby"))
        val rows = Records.rows(JSONObject().put("max_kmh", 68.9).put("drop_m", 5545.0), listOf(d2)).associateBy { it.what }
        assertEquals(68.9, rows["max_kmh"]!!.value!!, 1e-9); assertNull(rows["max_kmh"]!!.day); assertFalse(rows["max_kmh"]!!.fresh)
        assertNull(rows["drop_m"]!!.day)
    }

    @Test fun theOnlyDayIsNewAndTheSteepestHasItsRun() {
        val m = JSONObject().put("items", JSONArray().put(JSONObject().put("key", "steepest").put("p", JSONObject().put("run", "sadzele-1").put("deg", 28).put("ever", false))))
        val d = day("a", 1000, 61.2, 150, 4000.0, 20000.0, descent(100, 200, 2400.0, "tatra-2"), moments = m)
        val rows = Records.rows(JSONObject().put("max_kmh", 61.2).put("steep_deg", 28), listOf(d)).associateBy { it.what }
        assertTrue(rows["max_kmh"]!!.fresh)
        assertEquals(listOf("sadzele-1"), rows["steep_deg"]!!.runs); assertTrue(rows["steep_deg"]!!.fresh)
    }

    @Test fun nothingYetIsSixDashes() {
        val rows = Records.rows(JSONObject(), emptyList())
        assertEquals(Records.ORDER, rows.map { it.what })
        assertTrue(rows.all { it.value == null && it.day == null })
    }

    @Test fun aRecordingWithoutDescentsIsNoDay() {
        val d = day("a", 1000, 61.2, 150, 4000.0, 20000.0, descent(100, 200, 2400.0, "tatra-2"))
        val empty = Records.SavedDay("z", 9000, JSONObject().put("mode", "day").put("start", 9000).put("stats", JSONObject()).put("segments", JSONArray()), null)
        val rows = Records.rows(JSONObject().put("max_kmh", 61.2), listOf(d, empty)).associateBy { it.what }
        assertEquals("a", rows["max_kmh"]!!.day!!.id); assertTrue(rows["max_kmh"]!!.fresh)
    }
}

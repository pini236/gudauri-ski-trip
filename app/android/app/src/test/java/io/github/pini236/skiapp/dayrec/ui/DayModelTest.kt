package io.github.pini236.skiapp.dayrec.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DayModelTest {
    private val zone = ZoneId.of("Asia/Tbilisi")
    // 2027-01-12 09:00 in Tbilisi (UTC+4) is 05:00 UTC
    private val nine = 1_799_730_000_000L
    private fun min(m: Int) = m * 60_000L

    private fun result(vararg segs: String) = JSONObject("""{"start":$nine,"end":${nine + min(300)},"stats":{"descents":2,"lifts":1,"ski_dist_m":4200.5,"drop_m":900.0,"max_kmh":63.4,
        "max_at":{"t":${nine + min(5)},"lat":42.5,"lon":44.5}},"segments":[${segs.joinToString(",")}]}""")

    private fun descent(t0: Long, runs: String, max: Double, kind: String = "run") =
        """{"type":"descent","t0":$t0,"t1":${t0 + min(4)},"legs":[{"kind":"$kind","runs":[$runs]}],"max_kmh":$max,"drop_m":300,"dist_m":1500,"offpiste":[]}"""

    @Test fun summaryComesFromTheStats() {
        val d = DayModel.build(result(), zone)
        assertEquals(2, d.summary.descents); assertEquals(900.0, d.summary.dropM, 0.0); assertEquals(63.4, d.summary.maxKmh!!, 0.0)
        assertEquals(nine + min(5), d.summary.maxAt)
    }

    @Test fun aSharedStretchIsOneNameWithBothNumbers() {
        val d = DayModel.build(result(descent(nine, "\"Tatra 1\",\"Tatra 2\"", 44.0)), zone)
        val c = d.chapters.single().items.single() as DayModel.Descent
        assertEquals(listOf("Tatra 1 · Tatra 2"), c.runs)
        assertFalse(c.offRuns)
    }

    @Test fun aLiftShowsOnlyAsAQueue() {
        val d = DayModel.build(result(
            """{"type":"stop","kind":"queue","t0":$nine,"t1":${nine + min(2)}}""",
            """{"type":"lift","t0":${nine + min(2)},"t1":${nine + min(10)},"lift":"x","name":"Goodaura"}""",
            """{"type":"stop","kind":"queue","t0":${nine + min(20)},"t1":${nine + min(31)}}""",
            """{"type":"lift","t0":${nine + min(31)},"t1":${nine + min(40)},"lift":"y","name":"Kudebi"}"""), zone)
        val items = d.chapters.single().items
        assertEquals(1, items.size)
        val q = items.single() as DayModel.Queue
        assertEquals(11, q.minutes); assertEquals("Kudebi", q.lift)
    }

    @Test fun offTheRunsIsSaidAndNeverNamed() {
        val d = DayModel.build(result(descent(nine, "", 38.0, kind = "off")), zone)
        val c = d.chapters.single().items.single() as DayModel.Descent
        assertTrue(c.offRuns); assertTrue(c.runs.isEmpty())
    }

    @Test fun theAfternoonIsAChapterOfItsOwn() {
        val d = DayModel.build(result(descent(nine, "\"Tatra 1\"", 40.0), descent(nine + min(5 * 60), "\"Kikilo 1\"", 41.0)), zone)
        assertEquals(listOf(false, true), d.chapters.map { it.afternoon })
        assertEquals(listOf(1, 1), d.chapters.map { it.descents })
    }

    @Test fun shortRestsAndShortWalksAreLeftOut() {
        val d = DayModel.build(result(
            """{"type":"stop","kind":"rest","t0":$nine,"t1":${nine + min(4)}}""",
            """{"type":"stop","kind":"long","t0":${nine + min(10)},"t1":${nine + min(60)}}""",
            """{"type":"walk","t0":${nine + min(70)},"t1":${nine + min(72)},"dist_m":40}"""), zone)
        val r = d.chapters.single().items.single() as DayModel.Rest
        assertEquals(50, r.minutes); assertTrue(r.long)
        assertNull(DayModel.build(result(), zone).chapters.firstOrNull())
    }
}

package io.github.pini236.skiapp.dayrec

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The ski day's raw log (m-8 condition 3) in the format of the reference tool (tools/dayrec/README.md): written to the end,
 * readable after a crash, and never lost for a half line.
 */
class DayLogTest {
    private lateinit var root: File
    private lateinit var store: DayStore
    private var clock = 0L

    @Before fun setUp() { root = Files.createTempDirectory("dayrec").toFile(); store = DayStore(root); clock = 1_000_000L }

    private fun log(file: File = store.newFile(1_000), mode: String = "day") = DayLog.create(file, "Asia/Tbilisi", "gudauri", "test phone", 1_000, mode) { clock }
    private fun p(t: Long, speed: Float? = 5f) = DayLog.Point(t, 42.470395, 44.49234, alt = 2650.4, pressure = 740.12f, speed = speed, speedAcc = 0.4f,
        hAcc = 3.5f, vAcc = 4f, bearing = 181f, motion = 0.25f)

    @Test fun aDayIsWrittenAndReadBackLineByLine() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000)); l.add(p(11_000, speed = null)); l.stop(12_000, "user")
        val d = DayLog.read(f)
        assertEquals("Asia/Tbilisi", d.tz); assertEquals("gudauri", d.resort); assertEquals("day", d.mode)
        assertEquals(1_000L, d.header.getLong("start")) // the header carries the start, in ms
        assertEquals(2, d.points.size)
        assertEquals(42.470395, d.points[0].lat, 1e-9); assertEquals(44.49234, d.points[0].lon, 1e-9)
        assertEquals(5f, d.points[0].speed!!, 1e-6f); assertNull(d.points[1].speed) // "this phone could not say" stays a missing field
        assertEquals(0.4f, d.points[0].speedAcc!!, 1e-6f); assertEquals(740.12f, d.points[0].pressure!!, 1e-4f)
        assertEquals(12_000L, d.endedAt); assertEquals("user", d.endReason); assertFalse(d.cut)
    }

    @Test fun theLinesAreTheReferenceOnes() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000, speed = null)); l.stop(12_000, "idle")
        val lines = f.readLines().filter { it.isNotBlank() }.map { JSONObject(it) }
        assertEquals(4, lines.size) // header, start, a point, stop
        assertEquals(1, lines[0].getInt("v")); assertEquals("day", lines[0].getString("mode")); assertEquals(1_000L, lines[0].getLong("start"))
        assertEquals("start", lines[1].getString("ev"))
        // every key of a point is there, a reading the phone did not give is null (README: "חסר")
        val pt = lines[2]
        for (k in listOf("t", "la", "lo", "a", "p", "s", "sa", "ha", "va", "b", "m")) assertTrue(k, pt.has(k))
        assertTrue(pt.isNull("s")); assertEquals(0.25, pt.getDouble("m"), 1e-9)
        assertEquals("stop", lines[3].getString("ev")); assertEquals("idle", lines[3].getString("reason"))
    }

    @Test fun aSingleRunMeasurementSaysSoInItsHeader() {
        val f = store.newFile(1_000)
        log(f, mode = "run").release()
        assertEquals("run", DayLog.read(f).mode)
    }

    @Test fun aLastLineCutInHalfIsThrownAwayAndTheDayIsKept() {
        val f = store.newFile(1_000)
        val l = log(f)
        for (i in 0 until 5) l.add(p(10_000L + i * 1_000))
        l.release()
        f.appendText("{\"t\":15000,\"la\":42.47") // the process died in the middle of a line
        val d = DayLog.read(f)
        assertEquals(5, d.points.size); assertTrue(d.cut); assertEquals(1, d.dropped)
    }

    @Test fun aLineThatDoesNotParseIsDroppedWhereverItIs() {
        val d = DayLog.parse(sequenceOf(
            "{\"v\":1,\"tz\":\"Asia/Tbilisi\",\"resort\":\"gudauri\",\"start\":1,\"mode\":\"day\"}",
            "{\"t\":1000,\"la\":42.4,\"lo\":44.4}",
            "{\"t\":2000,\"la\":42.",   // cut in the middle of the file, not at its end
            "{\"t\":3000,\"la\":42.4,\"lo\":44.4}",
        ))
        assertEquals(listOf(1000L, 3000L), d.points.map { it.t }); assertEquals(1, d.dropped)
    }

    @Test fun aDayThatWasCutIsFoundAndGoneOnWithAfterANewline() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000)); l.release()
        f.appendText("{\"t\":11000,\"la\":42.4") // cut: the file does not end with a newline
        assertEquals(f, store.unfinished())
        val again = DayLog.reopen(f) { clock }
        again.resumed(60_000); again.add(p(61_000)); again.stop(62_000, "user")
        val d = DayLog.read(f)
        assertEquals(listOf(10_000L, 61_000L), d.points.map { it.t }) // the half line did not swallow the next one
        assertEquals(1, d.resumes); assertFalse(d.cut)
        assertNull(store.unfinished())
    }

    @Test fun theSameSecondTwiceKeepsTheFirstAndTheOrderIsByTime() {
        val d = DayLog.parse(sequenceOf(
            "{\"v\":1}",
            "{\"t\":2000,\"la\":42.4,\"lo\":44.4,\"s\":1.0}",
            "{\"t\":1000,\"la\":42.4,\"lo\":44.4,\"s\":2.0}",
            "{\"t\":2000,\"la\":42.4,\"lo\":44.4,\"s\":9.0}",
        ))
        assertEquals(listOf(1000L, 2000L), d.points.map { it.t }); assertEquals(1.0f, d.points[1].speed!!, 1e-6f)
    }

    @Test fun anEndedDayIsNotCutAndTheNewestCutDayWins() {
        val a = store.newFile(1_000); log(a).also { it.add(p(1)); it.stop(2, "user") }
        val b = store.newFile(2_000); log(b).also { it.add(p(3)); it.release() }
        val c = store.newFile(3_000); log(c).also { it.add(p(4)); it.release() }
        val d = store.newFile(4_000); log(d).also { it.add(p(5)); it.full(6) }
        assertFalse(store.isCut(a)); assertTrue(store.isCut(b)); assertFalse(store.isCut(d))
        assertEquals(c, store.unfinished())
        assertEquals(b, store.unfinished(except = c)) // the day being recorded right now is not "cut"
        assertEquals("full", DayLog.read(d).endReason)
    }

    @Test fun aReaderSkipsWhatItDoesNotKnow() {
        // a newer app wrote an event type and fields this one has never seen
        val d = DayLog.parse(sequenceOf(
            "{\"v\":1,\"tz\":\"Asia/Tbilisi\",\"resort\":\"gudauri\",\"device\":\"x\",\"future\":1}",
            "{\"t\":1000,\"la\":42.4,\"lo\":44.4,\"newfield\":7}",
            "{\"ev\":\"tow\",\"t\":1500,\"win\":[1,2,3]}",
            "{\"ev\":\"stop\",\"t\":2000,\"reason\":\"user\"}",
        ))
        assertEquals(1, d.points.size); assertEquals("user", d.endReason)
    }

    @Test fun aPointWithoutAPlaceIsDropped() {
        val d = DayLog.parse(sequenceOf("{\"v\":1}", "{\"t\":1000,\"la\":null,\"lo\":44.4}", "{\"t\":2000,\"lo\":44.4}"))
        assertTrue(d.points.isEmpty()); assertEquals(2, d.dropped)
    }

    @Test fun anImpactWindowIsOneLineAndADismissalIsASecond() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000))
        l.impact(10_500, 8_000, 50, DoubleArray(6) { it * 1.0 }, DoubleArray(6) { it * 0.5 })
        l.dismiss(10_500)
        l.stop(30_000, "user")
        val ev = DayLog.read(f).events.filter { it.optString("ev") == "impact" }
        assertEquals(2, ev.size)
        assertEquals(50, ev[0].getInt("hz")); assertEquals(8_000L, ev[0].getLong("t0")); assertEquals(6, ev[0].getJSONArray("acc").length())
        assertTrue(ev[1].getBoolean("dismissed"))
    }

    @Test fun closingTheDayDeletesTheWindowOfADismissedBlowForReal() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000))
        l.impact(10_500, 8_000, 50, doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(0.1, 0.2, 0.3))   // dismissed
        l.impact(20_500, 18_000, 50, doubleArrayOf(4.0, 5.0, 6.0), doubleArrayOf(0.4, 0.5, 0.6))  // a real fall
        l.dismiss(10_500)
        l.stop(30_000, "user")
        DayLog.compact(f)
        val text = f.readText()
        assertFalse("the dismissed window is still in the file", "\"acc\":[1" in text)
        assertTrue("the other window must stay", "\"acc\":[4" in text)
        val ev = DayLog.read(f).events.filter { it.optString("ev") == "impact" }
        assertEquals(2, ev.size) // the real blow, and the dismissal line without a window
        assertTrue(ev.any { it.optBoolean("dismissed") && !it.has("acc") })
        assertTrue(f.readText().endsWith("\n")); assertFalse(File(f.parentFile, f.name + ".tmp").exists())
    }

    @Test fun recoveringACutDayAlsoDeletesTheWindowsThatWereDismissed() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.impact(10_500, 8_000, 50, doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(0.1, 0.2, 0.3))
        l.dismiss(10_500)
        l.release()
        DayLog.reopen(f) { clock }.release()
        assertFalse("\"acc\":[1" in f.readText())
    }

    @Test fun theSizeIsKnownAndTheLimitIsTenMegabytes() {
        val l = log()
        assertFalse(l.atLimit); assertTrue(l.bytes > 0)
        assertEquals(10L * 1024 * 1024, DayLog.MAX_BYTES)
        // a day of eight hours is far from the limit: 28,800 points, about 100 bytes each
        val f = store.newFile(5_000); val big = log(f)
        for (i in 0 until 2_000) big.add(p(10_000L + i * 1_000))
        big.release()
        assertTrue("about ${f.length() / 2000} bytes a point", f.length() / 2000 in 60..160)
        assertTrue(f.length() / 2000 * 28_800 < DayLog.MAX_BYTES)
    }

    @Test fun theHistoryCanBeDeletedOneDayOrAll() {
        val a = store.newFile(1_000); log(a).release()
        val b = store.newFile(2_000); log(b).release()
        assertEquals(listOf(a, b), store.days()); assertTrue(store.totalBytes() > 0)
        store.delete(a); assertEquals(listOf(b), store.days())
        store.deleteAll(); assertTrue(store.days().isEmpty()); assertEquals(0L, store.totalBytes())
    }

    @Test fun coordinatesKeepSevenDecimalsAndNothingMore() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(DayLog.Point(1_000, 42.4703951234, 44.4923412345))
        l.release()
        val pt = JSONObject(f.readLines()[2])
        assertEquals(42.4703951, pt.getDouble("la"), 1e-12); assertEquals(44.4923412, pt.getDouble("lo"), 1e-12)
    }
}

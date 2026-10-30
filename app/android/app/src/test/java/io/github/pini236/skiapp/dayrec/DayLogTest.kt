package io.github.pini236.skiapp.dayrec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The ski day's raw log (m-8 condition 3): written to the end, readable after a crash, and never lost for a half line. */
class DayLogTest {
    private lateinit var root: File
    private lateinit var store: DayStore
    private var clock = 0L

    @Before fun setUp() { root = Files.createTempDirectory("dayrec").toFile(); store = DayStore(root); clock = 1_000_000L }

    private fun log(file: File = store.newFile(1_000)) = DayLog.create(file, "Asia/Tbilisi", "gudauri", "test phone") { clock }
    private fun p(t: Long, speed: Float? = 5f) = DayLog.Point(t, 42.470395, 44.49234, alt = 2650.4, pressure = 740.12f, speed = speed, speedAcc = 0.4f,
        hAcc = 3.5f, vAcc = 4f, bearing = 181f, motion = 0.25f)

    @Test fun aDayIsWrittenAndReadBackLineByLine() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000)); l.add(p(11_000, speed = null)); l.end(12_000, "user")
        val d = DayLog.read(f)
        assertEquals(1, d.version); assertEquals("Asia/Tbilisi", d.tz); assertEquals("gudauri", d.resort)
        assertEquals(2, d.points.size)
        assertEquals(42.470395, d.points[0].lat, 1e-9); assertEquals(44.49234, d.points[0].lon, 1e-9)
        assertEquals(5f, d.points[0].speed!!, 1e-6f); assertNull(d.points[1].speed) // "this phone could not say" stays a missing field
        assertEquals(0.4f, d.points[0].speedAcc!!, 1e-6f); assertEquals(740.12f, d.points[0].pressure!!, 1e-4f)
        assertEquals(12_000L, d.endedAt); assertEquals("user", d.endReason); assertFalse(d.cut)
    }

    @Test fun oneLineForEachThing() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000)); l.gap(10_000, 25_000); l.add(p(25_000)); l.end(26_000, "idle")
        val lines = f.readLines().filter { it.isNotBlank() }
        assertEquals(1 + 2 + 1 + 1, lines.size) // header, two points, the hole, the end
        assertTrue(lines.all { it.startsWith("{") && it.endsWith("}") })
    }

    @Test fun aHoleIsInTheTimelineAndNotHidden() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000)); l.gap(10_000, 31_000); l.add(p(31_000)); l.release()
        val d = DayLog.read(f)
        assertEquals(1, d.gaps.size); assertEquals(10_000L, d.gaps[0][0]); assertEquals(31_000L, d.gaps[0][1])
    }

    @Test fun aLastLineCutInHalfIsThrownAwayAndTheDayIsKept() {
        val f = store.newFile(1_000)
        val l = log(f)
        for (i in 0 until 5) l.add(p(10_000L + i * 1_000))
        l.release()
        f.appendText("{\"t\":15000,\"la\":42.47") // the process died in the middle of a line
        val d = DayLog.read(f)
        assertEquals(5, d.points.size); assertTrue(d.cut)
    }

    @Test fun aDayThatWasCutIsFoundAndGoneOnWithOnANewLine() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(p(10_000)); l.release()
        f.appendText("{\"t\":11000,\"la\":42.4") // cut
        assertEquals(f, store.unfinished())
        val again = DayLog.reopen(f) { clock }
        again.resumed(60_000); again.add(p(61_000)); again.end(62_000, "user")
        val d = DayLog.read(f)
        assertEquals(2, d.points.size) // the half line did not swallow the next one
        assertEquals(listOf(60_000L), d.resumes); assertFalse(d.cut)
        assertNull(store.unfinished())
    }

    @Test fun anEndedDayIsNotCutAndTheNewestCutDayWins() {
        val a = store.newFile(1_000); log(a).also { it.add(p(1)); it.end(2, "user") }
        val b = store.newFile(2_000); log(b).also { it.add(p(3)); it.release() }
        val c = store.newFile(3_000); log(c).also { it.add(p(4)); it.release() }
        assertFalse(store.isCut(a)); assertTrue(store.isCut(b))
        assertEquals(c, store.unfinished())
        assertEquals(b, store.unfinished(except = c)) // the day being recorded right now is not "cut"
    }

    @Test fun aReaderSkipsWhatItDoesNotKnow() {
        // a newer app wrote an event type and fields this one has never seen
        val d = DayLog.parse(sequenceOf(
            "{\"v\":1,\"tz\":\"Asia/Tbilisi\",\"resort\":\"gudauri\",\"device\":\"x\",\"future\":1}",
            "{\"t\":1000,\"la\":42.4,\"lo\":44.4,\"newfield\":7}",
            "{\"e\":\"impact\",\"t\":1500,\"win\":[1,2,3]}",
            "{\"e\":\"end\",\"t\":2000,\"reason\":\"user\"}",
        ))
        assertEquals(1, d.points.size); assertEquals("user", d.endReason)
    }

    @Test fun theSizeIsKnownAndTheLimitIsTenMegabytes() {
        val l = log()
        assertFalse(l.full); assertTrue(l.bytes > 0)
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

    @Test fun coordinatesKeepSixDecimalsAndNothingMore() {
        val f = store.newFile(1_000)
        val l = log(f)
        l.add(DayLog.Point(1_000, 42.4703951234, 44.4923412345))
        l.release()
        val line = f.readLines()[1]
        assertTrue(line, "\"la\":42.470395," in line || "\"la\":42.470395}" in line)
    }
}

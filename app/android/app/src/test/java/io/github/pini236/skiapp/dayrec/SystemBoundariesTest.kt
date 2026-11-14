package io.github.pini236.skiapp.dayrec

import io.github.pini236.skiapp.dayrec.analysis.Analyzer
import io.github.pini236.skiapp.dayrec.analysis.Net
import io.github.pini236.skiapp.dayrec.analysis.Params
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The system calls the recorder's handlers from threads of its own (docs: qa/system-boundaries.md): the sensors and the
 * GPS on the recorder's looper, the stop button on the main thread, the final analysis on an executor. A handler that is
 * only ever called from one thread in a test hides a crash that a phone finds at once (the iPhone's build 55 did that with
 * the accelerometer). So here each piece the handlers touch is driven from several threads at the same time, with no phone:
 * nothing may throw, and what is handed over must be whole.
 */
class SystemBoundariesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val root = dir.parentFile.parentFile
    private val params = Params(JSONObject(File(root, "tools/fixtures/dayrec-params.json").readText()))
    private val analyzer by lazy {
        Analyzer(Net(JSONObject(File(dir, "runs-and-lifts.json").readText()), JSONObject(File(dir, "terrain.json").readText()), params), params)
    }

    /** [n] threads start together and all must finish; the first thing any of them threw is returned (null: nothing). */
    private fun together(n: Int, body: (Int) -> Unit): Throwable? {
        val failed = AtomicReference<Throwable?>(null)
        val go = CountDownLatch(1); val done = CountDownLatch(n)
        repeat(n) { i ->
            Thread {
                try { go.await(); body(i) } catch (t: Throwable) { failed.compareAndSet(null, t) } finally { done.countDown() }
            }.start()
        }
        go.countDown()
        assertTrue("the threads did not finish", done.await(60, TimeUnit.SECONDS))
        return failed.get()
    }

    // --- the analysis: fed on the recorder's thread, the whole day asked for on another (DayRecService.analyse) ---

    @Test fun theWholeDayAskedForWhileReadingsComeInIsAlwaysWhole() {
        val list = JSONObject(File(root, "tools/fixtures/dayrec-cases.json").readText()).getJSONArray("cases")
        val c = (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("name") == "full_day" }
        val d = DayLog.read(File(root, c.getString("log")))
        val e = DayEngine(analyzer, JSONObject(d.header.toString()))
        val feeding = Thread { for (p in d.points.take(1800)) e.add(p) }
        val asked = AtomicReference<Throwable?>(null)
        val asking = Thread {
            try { var k = 0; while (k < 8) { if (e.size > 60) { e.final(); k++ } else Thread.yield() } } catch (t: Throwable) { asked.set(t) }
        }
        feeding.start(); asking.start()
        feeding.join(60_000); asking.join(120_000)
        assertNull("final() on a day that is growing threw: ${asked.get()}", asked.get())
        assertEquals(1800.coerceAtMost(d.points.size), e.size)
    }

    // --- the blow's window: the accelerometer and the gyroscope feed it, the recorder's stop clears it ---

    @Test fun theBlowWindowSurvivesTheSensorsAndAClearFromOtherThreads() {
        val hits = java.util.concurrent.atomic.AtomicInteger()
        val b = ImpactBuffer(DayParams.FALL_TRIGGER_G, DayParams.FALL_PRE_S, DayParams.FALL_POST_S, DayParams.FALL_DEBOUNCE_S, DayParams.FALL_HZ) { _, _, _, a, g ->
            assertEquals(a.size, g.size); hits.incrementAndGet()
        }
        val err = together(3) { i ->
            when (i) {
                0 -> { var t = 100_000L; repeat(6_000) { val a = if (it % 997 == 0) 5f * ImpactBuffer.G else 9.8f; b.feedAcc(t, 0f, 0f, a); t += 20 } }
                1 -> repeat(6_000) { b.feedGyro(0.1f, 0.2f, 0.3f) }
                else -> repeat(200) { b.clear(); Thread.yield() }
            }
        }
        assertNull(err?.toString(), err)
    }

    @Test fun theMotionNumberTakenWhileTheAccelerometerFeedsIsNeverNegativeOrNan() {
        val m = MotionMeter()
        val bad = AtomicReference<Float?>(null)
        val err = together(2) { i ->
            if (i == 0) repeat(100_000) { m.feed(0f, 0f, 9.81f + (it % 7)) }
            else repeat(2_000) { m.take()?.let { v -> if (v.isNaN() || v < 0f) bad.set(v) } }
        }
        assertNull(err?.toString(), err)
        assertNull("a motion number was ${bad.get()}", bad.get())
    }

    // --- the day's file: readings and the closing line come from different threads (the stop button and a late reading) ---

    @Test fun aReadingAndTheClosingLineFromTwoThreadsLeaveAFileThatReadsBack() {
        val store = DayStore(tmp.root)
        val f = store.newFile(1_000)
        val log = DayLog.create(f, "Asia/Tbilisi", "gudauri", "test phone", 1_000) { 5_000_000L }
        val err = together(3) { i ->
            when (i) {
                0 -> for (k in 0 until 3_000) log.add(DayLog.Point(10_000L + k * 1000, 42.47, 44.49, alt = 2650.0, speed = 5.0))
                1 -> { Thread.sleep(5); log.stop(4_000_000L, "user") }
                else -> repeat(50) { runCatching { DayLog.read(f) }; Thread.yield() } // the screens read the file being written
            }
        }
        assertNull(err?.toString(), err)
        val d = DayLog.read(f)
        assertEquals("the day closes once", "user", d.endReason)
        assertTrue(d.points.isNotEmpty())
        assertEquals(0, d.dropped)
    }

    // --- the recorder's state the screens read once a second: written on one thread, read on another ---

    @Test fun theSnapshotTheScreensReadIsAlwaysAWholeOne() {
        val e = DayEngine(analyzer, JSONObject().put("v", 1).put("tz", "Asia/Tbilisi").put("resort", "gudauri").put("mode", "day"))
        val seen = AtomicReference<Throwable?>(null)
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val reader = Thread {
            try { while (running.get()) { e.snapshot?.let { s -> check(s.act.isNotEmpty()); check(s.falls.size >= 0); s.runs?.forEach { r -> check(r.isNotEmpty()) } } } } catch (t: Throwable) { seen.set(t) }
        }
        reader.start()
        val d = DayLog.read(File(root, JSONObject(File(root, "tools/fixtures/dayrec-cases.json").readText()).getJSONArray("cases").getJSONObject(0).getString("log")))
        for ((i, p) in d.points.take(900).withIndex()) { e.add(p); if (i % 5 == 0) e.live() }
        running.set(false); reader.join(10_000)
        assertNull(seen.get()?.toString(), seen.get())
    }
}

package io.github.pini236.skiapp.dayrec

import io.github.pini236.skiapp.dayrec.analysis.Analyzer
import io.github.pini236.skiapp.dayrec.analysis.Net
import io.github.pini236.skiapp.dayrec.analysis.Params
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.abs

/**
 * The day engine against the reference tool's test values (decision 76): the recorded days of tools/fixtures are fed to it
 * one reading at a time, as the service does, and what it says along the way has to agree with the whole-day analysis the
 * reference tool wrote down. No screens here: the live view, the lift-ride summary, the single run that stops by itself,
 * and the result file.
 */
class DayEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val root = dir.parentFile.parentFile
    private val cases = JSONObject(File(root, "tools/fixtures/dayrec-cases.json").readText())
    private val params = Params(JSONObject(File(root, "tools/fixtures/dayrec-params.json").readText()))
    private val analyzer by lazy {
        Analyzer(Net(JSONObject(File(dir, "runs-and-lifts.json").readText()), JSONObject(File(dir, "terrain.json").readText()), params), params)
    }

    private fun case(name: String): JSONObject {
        val list = cases.getJSONArray("cases")
        return (0 until list.length()).map { list.getJSONObject(it) }.first { it.getString("name") == name }
    }

    private fun day(c: JSONObject) = DayLog.read(File(root, c.getString("log")))

    /** The service's way: a reading in, the live view every [every] seconds, a blow handed over when its window has closed. */
    private fun replay(e: DayEngine, d: DayLog.Day, every: Long, stopWhen: (DayEngine.Snapshot) -> Boolean = { false }): DayEngine.Snapshot? {
        val blows = d.events.filter { it.optString("ev") == "impact" }.sortedBy { it.optLong("t") }
        var bi = 0
        var next = 0L
        var s: DayEngine.Snapshot? = null
        for (p in d.points) {
            e.add(p)
            while (bi < blows.size && blows[bi].optLong("t") + 3_000 <= p.t) e.addEvent(blows[bi++])
            if (p.t >= next) {
                next = p.t + every * 1000
                s = e.live()
                if (s != null && stopWhen(s)) return s
            }
        }
        return s
    }

    @Test fun aWholeDayInTheWayOfTheServiceAgreesWithTheWholeDayAnalysis() {
        val c = case("full_day")
        val d = day(c)
        val e = DayEngine(analyzer, d.header)
        val rides = ArrayList<DayEngine.Ride>()
        e.onRide = { rides += it }
        val last = replay(e, d, DayRecService.LIVE_EVERY_S)
        assertNotNull(last)
        val exp = c.getJSONObject("expected").getJSONObject("final")
        val stats = exp.getJSONObject("stats")
        // the live maximum is of raw readings, in a window: it agrees with the day's to a tenth of a km/h
        assertEquals(stats.getDouble("max_kmh"), last!!.maxKmh!!, 0.5)
        // a summary for most of the day's descents, and each of them is a real descent, seen within a few seconds
        val final = analyzer.final(d.header, d.points, d.events)
        val descents = final.segments.filter { it.type == "descent" }
        assertEquals(stats.getInt("descents"), descents.size)
        assertTrue("rides ${rides.size} of ${descents.size}", rides.size >= descents.size * 3 / 4)
        val matched = rides.count { r -> descents.any { abs(it.t0 - r.t0) <= 5_000 && abs(it.t1 - r.t1) <= 5_000 } }
        assertTrue("only $matched of ${rides.size} rides are a descent of the day", matched >= rides.size * 9 / 10)
        // the day has short descents too (a 38 m one): only that each summary has real numbers
        for (r in rides) { assertTrue("drop ${r.dropM} dist ${r.distM}", r.dropM > 10 && r.distM > 100 && r.t1 > r.t0) }
        assertEquals(rides.last().t0, e.lastRide!!.t0)
    }

    @Test fun theLiveViewSaysWhatTheSkierIsDoingAndOnWhichRun() {
        val c = case("run_single")
        val d = day(c)
        val e = DayEngine(analyzer, d.header)
        val acts = ArrayList<String>()
        replay(e, d, 5) { s -> if (acts.lastOrNull() != s.act) acts += s.act; false }
        // stand, the lift, stand, then the descent, and a run named while it is skied
        assertTrue(acts.toString(), acts.contains("lift") && acts.contains("ski"))
        assertTrue(acts.indexOf("lift") < acts.indexOf("ski"))
        val s = e.snapshot!!
        assertEquals(c.getJSONObject("expected").getJSONObject("final").getJSONObject("stats").getDouble("max_kmh"), s.maxKmh!!, 0.5)
    }

    @Test fun aSingleRunStopsWhereTheReferenceSaysItDoes() {
        for (name in listOf("run_from_middle", "run_short_stop", "run_still")) {
            val c = case(name)
            val d = day(c)
            val want = c.getJSONObject("expected").getJSONObject("live").getJSONObject("autostop")
            val e = DayEngine(analyzer, d.header)
            assertTrue(name, e.isRun)
            val s = replay(e, d, 1) { it.autostop != null }
            assertNotNull(name, s?.autostop)
            assertEquals(name, want.getString("reason"), s!!.autostop!!.reason)
            assertTrue("$name: stopped at ${s.autostop!!.t}, wanted ${want.getLong("t")}", abs(s.autostop!!.t - want.getLong("t")) <= 3_000)
            // the service stops at the moment it is known: nothing after it is read
            assertTrue(name, s.t >= s.autostop!!.t && s.t - s.autostop!!.t <= 2_000)
        }
    }

    @Test fun aDayNeverStopsByItself() {
        val d = day(case("full_day"))
        val e = DayEngine(analyzer, d.header)
        assertTrue(!e.isRun)
        assertNull(replay(e, d, 60)!!.autostop)
    }

    @Test fun aFallIsInTheLiveViewAndAnDismissedOneIsNot() {
        for (name in listOf("falls", "fall_dismissed")) {
            val c = case(name)
            val d = day(c)
            val e = DayEngine(analyzer, d.header)
            val s = replay(e, d, 5)!!
            val want = c.getJSONObject("expected").getJSONObject("live").getJSONArray("falls").length()
            assertEquals(name, want, s.falls.size)
        }
    }

    @Test fun theResultIsWrittenBesideTheLogAndIsNotMadeAgainWhileItIsOfThisAnalysis() {
        val c = case("run_single")
        val log = File(tmp.root, "day-1.jsonl").also { File(root, c.getString("log")).copyTo(it) }
        val out = DayAnalysis.write(analyzer, params.analysis, log)
        assertNotNull(out)
        assertEquals(DayStore.resultOf(log), out)
        assertEquals("day-1.analysis.json", out!!.name)
        val r = JSONObject(out.readText())
        val exp = c.getJSONObject("expected").getJSONObject("final")
        assertEquals(params.analysis, r.getInt("analysis"))
        assertEquals("gudauri", r.getString("resort"))
        assertEquals(exp.getJSONArray("segments").length(), r.getJSONArray("segments").length())
        assertEquals(exp.getJSONObject("stats").getInt("descents"), r.getJSONObject("stats").getInt("descents"))
        val descent = (0 until r.getJSONArray("segments").length()).map { r.getJSONArray("segments").getJSONObject(it) }.first { it.getString("type") == "descent" }
        for (k in listOf("legs", "dist_m", "drop_m", "moving_s", "avg_kmh", "max_kmh", "max_at", "stops", "offpiste", "top_m", "bottom_m")) assertTrue(k, descent.has(k))
        val stamp = out.lastModified()
        Thread.sleep(20)
        assertEquals(out, DayAnalysis.write(analyzer, params.analysis, log))
        assertEquals(stamp, out.lastModified())
        // another analysis makes it again
        val again = DayAnalysis.write(analyzer, params.analysis + 1, log)!!
        assertEquals(params.analysis + 1, JSONObject(again.readText()).getInt("analysis"))
    }

    @Test fun theResultIsNotADayAndGoesWithItsDay() {
        val store = DayStore(tmp.root)
        val log = store.newFile(5)
        File(root, case("run_single").getString("log")).copyTo(log)
        DayAnalysis.write(analyzer, params.analysis, log)
        assertEquals(listOf(log), store.days())
        store.delete(log)
        assertTrue(!log.exists() && !DayStore.resultOf(log).exists())
    }
}

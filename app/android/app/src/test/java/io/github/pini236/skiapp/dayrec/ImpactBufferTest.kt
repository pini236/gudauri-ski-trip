package io.github.pini236.skiapp.dayrec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** "Here you fell" (condition 10): a window of the motion sensors around a blow, in the reference format. */
class ImpactBufferTest {
    private class Hit(val t: Long, val t0: Long, val hz: Int, val acc: DoubleArray, val gyr: DoubleArray)

    private fun buffer(out: MutableList<Hit>) = ImpactBuffer(DayParams.FALL_TRIGGER_G, DayParams.FALL_PRE_S, DayParams.FALL_POST_S, DayParams.FALL_DEBOUNCE_S, DayParams.FALL_HZ) { t, t0, hz, a, g -> out += Hit(t, t0, hz, a, g) }

    /** Quiet skiing for [secs], then a blow of [peakG] at [blowAt], at 50 readings a second from [from]. */
    private fun run(b: ImpactBuffer, from: Long, secs: Int, blowAt: Long? = null, peakG: Double = 4.0) {
        var t = from
        while (t < from + secs * 1000L) {
            b.feedGyro(0.1f, 0.2f, 0.3f)
            val a = if (blowAt != null && t == blowAt) peakG * ImpactBuffer.G else 9.8
            b.feedAcc(t, 0f, 0f, a.toFloat())
            t += 20
        }
    }

    @Test fun theWindowIsFiveSecondsAroundThePeakWithOneSampleAtItsTime() {
        val hits = ArrayList<Hit>(); val b = buffer(hits)
        run(b, 100_000, 20, blowAt = 108_000)
        assertEquals(1, hits.size)
        val h = hits[0]
        assertEquals(108_000L, h.t); assertEquals(105_500L, h.t0); assertEquals(50, h.hz)
        assertEquals(251 * 3, h.acc.size); assertEquals(251 * 3, h.gyr.size)
        val k = ((h.t - h.t0) / 20).toInt() // the sample at exactly t
        assertEquals(4.0, h.acc[k * 3 + 2] / ImpactBuffer.G, 0.01)
        assertEquals(0.3, h.gyr[k * 3 + 2], 1e-6)
    }

    @Test fun theWindowIsWrittenOnlyAfterItsLastSecondsHavePassed() {
        val hits = ArrayList<Hit>(); val b = buffer(hits)
        run(b, 100_000, 9, blowAt = 108_000)   // only one second after the blow
        assertTrue(hits.isEmpty())
        run(b, 109_000, 3)
        assertEquals(1, hits.size)
    }

    @Test fun aSmallBumpIsNotABlow() {
        val hits = ArrayList<Hit>(); val b = buffer(hits)
        run(b, 100_000, 20, blowAt = 108_000, peakG = 2.0)
        assertTrue(hits.isEmpty())
    }

    @Test fun twoBlowsCloserThanTheDebounceAreOne() {
        val hits = ArrayList<Hit>(); val b = buffer(hits)
        var t = 100_000L
        while (t < 120_000) {
            b.feedGyro(0f, 0f, 1f)
            b.feedAcc(t, 0f, 0f, ((if (t == 108_000L || t == 109_000L) 4.0 else 1.0) * ImpactBuffer.G).toFloat()); t += 20
        }
        assertEquals(1, hits.size)
        // ...and one after the debounce is another
        val more = ArrayList<Hit>(); val c = buffer(more)
        t = 100_000L
        while (t < 125_000) {
            c.feedGyro(0f, 0f, 1f)
            c.feedAcc(t, 0f, 0f, ((if (t == 108_000L || t == 112_000L) 4.0 else 1.0) * ImpactBuffer.G).toFloat()); t += 20
        }
        assertEquals(2, more.size)
    }

    @Test fun theHighestReadingJustAfterTheTriggerIsThePeak() {
        val hits = ArrayList<Hit>(); val b = buffer(hits)
        var t = 100_000L
        while (t < 112_000) {
            b.feedGyro(0f, 0f, 1f)
            val g = when (t) { 108_000L -> 3.0; 108_020L -> 5.5; 108_040L -> 3.2; else -> 1.0 }
            b.feedAcc(t, 0f, 0f, (g * ImpactBuffer.G).toFloat()); t += 20
        }
        assertEquals(108_020L, hits[0].t)
    }

    @Test fun aWindowThatDidNotGetItsLastSecondsIsDroppedWhenTheDayEnds() {
        val hits = ArrayList<Hit>(); val b = buffer(hits)
        run(b, 100_000, 10, blowAt = 108_000)
        b.clear()
        run(b, 110_000, 10)
        assertTrue(hits.isEmpty())
    }

    @Test fun theFallNumbersAreTheSharedFilesNumbers() {
        val dir = File(System.getProperty("site.data") ?: "../../../site/data")
        val f = File(dir.parentFile.parentFile, "tools/fixtures/dayrec-params.json")
        val fall = org.json.JSONObject(f.readText()).getJSONObject("fall")
        assertEquals(fall.getInt("hz"), DayParams.FALL_HZ)
        assertEquals(fall.getDouble("pre_s"), DayParams.FALL_PRE_S, 0.0)
        assertEquals(fall.getDouble("post_s"), DayParams.FALL_POST_S, 0.0)
        assertEquals(fall.getDouble("trigger_g"), DayParams.FALL_TRIGGER_G, 0.0)
        assertEquals(fall.getDouble("debounce_s"), DayParams.FALL_DEBOUNCE_S, 0.0)
    }
}

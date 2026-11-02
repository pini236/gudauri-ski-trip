package io.github.pini236.skiapp.dayrec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a day stops by itself (m-8 condition 2): 20 minutes still outside the resort, 60 inside, with a notice first. */
class IdleWatchTest {
    private val min = 60_000L
    private val inX = 0f; private val inY = 0f // the resort's origin: inside
    private val outX = 20_000f; private val outY = 0f // far outside the terrain model

    /** A phone lying still, one reading a second, until [minutes] have passed; the last verdict. */
    private fun still(w: IdleWatch, x: Float, y: Float, minutes: Long, from: Long = 0): IdleWatch.Verdict {
        var v: IdleWatch.Verdict = IdleWatch.Verdict.Fine
        var t = from
        while (t <= from + minutes * min) { v = w.feed(t, x, y); t += 1_000 }
        return v
    }

    @Test fun insideTheResortAnHourStopsIt() {
        assertTrue(IdleWatch.inside(inX, inY)); assertFalse(IdleWatch.inside(outX, outY))
        assertEquals(IdleWatch.Verdict.Fine, still(IdleWatch(), inX, inY, 50))
        assertEquals(IdleWatch.Verdict.Stop(outside = false), still(IdleWatch(), inX, inY, 60))
    }

    @Test fun outsideTheResortTwentyMinutesStopIt() {
        assertEquals(IdleWatch.Verdict.Fine, still(IdleWatch(), outX, outY, 15))
        assertEquals(IdleWatch.Verdict.Stop(outside = true), still(IdleWatch(), outX, outY, 20))
    }

    @Test fun aNoticeComesTwoMinutesBeforeAndSaysHowMuchIsLeft() {
        val v = still(IdleWatch(), inX, inY, 59)
        assertTrue(v is IdleWatch.Verdict.Warn)
        assertTrue((v as IdleWatch.Verdict.Warn).leftMs in 0..2 * min)
        assertEquals(IdleWatch.Verdict.Fine, still(IdleWatch(), inX, inY, 57)) // not yet
    }

    @Test fun movingStartsTheCountAgain() {
        val w = IdleWatch()
        assertTrue(still(w, inX, inY, 59) is IdleWatch.Verdict.Warn)
        assertEquals(IdleWatch.Verdict.Fine, w.feed(59 * min + 5_000, inX + 80f, inY)) // a walk to the lift
        assertEquals(IdleWatch.Verdict.Fine, still(w, inX + 80f, inY, 10, from = 59 * min + 6_000))
    }

    @Test fun aPhoneRestingInALodgeIsStillEvenWithGpsJitter() {
        val w = IdleWatch(); var v: IdleWatch.Verdict = IdleWatch.Verdict.Fine
        var t = 0L; var k = 0
        while (t <= 61 * min) { v = w.feed(t, inX + (k % 3) * 8f, inY - (k % 4) * 6f); t += 1_000; k++ } // wanders 20 m at most
        assertEquals(IdleWatch.Verdict.Stop(outside = false), v)
    }

    @Test fun crossingTheEdgeUsesTheRuleOfWhereItIsNow() {
        val w = IdleWatch()
        assertEquals(IdleWatch.Verdict.Stop(outside = true), still(w, outX, outY, 21))
    }
}

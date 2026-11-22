package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round 4, board L04: "about N min to the top", by the lift's travel time less the time since boarding. */
class LiftEtaTest {
    @Test fun theTravelTimeLessTheRide() {
        assertEquals(5, liftEta(6.6, 0L, 90_000L))
        assertEquals(1, liftEta(4.2, 0L, 200_000L))
    }

    @Test fun nothingUnderAMinuteOrWithoutATime() {
        assertNull(liftEta(4.2, 0L, 240_000L)); assertNull(liftEta(null, 0L, 0L)); assertNull(liftEta(4.2, null, 0L))
    }
}

package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.dayrec.DayEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Round 4, board L05: the notification changes with the lift, the run and an ended descent, and not with the speed. */
class NoticeTest {
    private fun snap(act: String, lift: String? = null, runs: List<String>? = null, max: Double? = null) =
        DayEngine.Snapshot(0L, act, lift, runs, max, emptyList(), null)

    @Test fun theKeyFollowsWhatTheNotificationShows() {
        val a = Notice.key(snap("descent", runs = listOf("Tatra 2"), max = 40.0), null)
        assertEquals(a, Notice.key(snap("descent", runs = listOf("Tatra 2"), max = 61.0), null))
        assertNotEquals(a, Notice.key(snap("descent", runs = listOf("Tatra 1")), null))
        assertNotEquals(a, Notice.key(snap("lift", lift = "7"), null))
        val ride = DayEngine.Ride(1L, 2L, listOf("Tatra 2"), 1000.0, 300.0, 50.0, null, 120, 30.0, JSONObject())
        assertNotEquals(Notice.key(snap("lift", lift = "7"), null), Notice.key(snap("lift", lift = "7"), ride))
    }
}

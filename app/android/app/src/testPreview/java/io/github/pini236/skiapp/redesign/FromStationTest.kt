package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** K06: the runs going down from a top station, each once, the longest first; a station with none opens nothing. */
class FromStationTest {
    private fun r(key: String, len: Int) = ShelfRun(key, key, "blue", "run", len, 100, 10, null, null, null)

    @Test fun aSharedStationGathersEveryLiftsRunsOnceTheLongestFirst() {
        val all = listOf(r("Tatra 1", 2400) to listOf("Goodaura"), r("Shino", 900) to listOf("Shino"),
            r("Tatra 2", 2700) to listOf("Goodaura", "Shino"), r("Kudebi 1", 1800) to listOf("Kudebi"))
        assertEquals(listOf("Tatra 2", "Tatra 1", "Shino"), FromStation.runs(listOf("Goodaura", "Shino"), all).map { it.key })
    }

    @Test fun aBottomOrAnEmptyStationHasNoRuns() =
        assertTrue(FromStation.runs(emptyList(), listOf(r("Tatra 1", 2400) to listOf("Goodaura"))).isEmpty())

    @Test fun theSameLengthGoesByName() =
        assertEquals(listOf("Soliko 2", "Soliko 10"), FromStation.runs(listOf("Soliko"),
            listOf(r("Soliko 10", 500) to listOf("Soliko"), r("Soliko 2", 500) to listOf("Soliko"))).map { it.key })
}

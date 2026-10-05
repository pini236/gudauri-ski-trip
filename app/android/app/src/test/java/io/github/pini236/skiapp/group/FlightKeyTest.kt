package io.github.pini236.skiapp.group

import io.github.pini236.skiapp.trip.Leg
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** X-4: the group's flight cards are made as on the site, on the shared cases in docs/parity-cases.json. */
class FlightKeyTest {
    private val cases = JSONObject(File(File(System.getProperty("site.data") ?: "../../../site/data").parentFile.parentFile, "docs/parity-cases.json").readText())
        .getJSONObject("x4").getJSONArray("cases")

    @Test fun theSharedCases() {
        for (k in 0 until cases.length()) {
            val c = cases.getJSONObject(k)
            val legs = c.getJSONArray("legs")
            val keys = (0 until legs.length()).map { i ->
                val l = legs.getJSONObject(i)
                FlightKey.of(Leg(LocalDate.parse(l.getString("date")), l.getString("flight"), l.getString("from"), l.getString("to")), false)
            }.toSet()
            assertEquals(c.getString("name"), c.getInt("cards"), keys.size)
        }
    }

    @Test fun theDirectionAndTheNumber() {
        val d = LocalDate.of(2027, 1, 10)
        assertEquals("IZ897", FlightKey.number(" iz - 897 "))
        // out and back on the same day and number are two cards
        assertEquals(2, setOf(FlightKey.of(Leg(d, "IZ897"), false), FlightKey.of(Leg(d, "IZ897"), true)).size)
        // airports typed as "TLV · Tel Aviv" and "tlv" are one
        assertEquals(FlightKey.of(Leg(d, "", "TLV · Tel Aviv", "TBS"), false), FlightKey.of(Leg(d, "", "tlv", "tbs"), false))
    }
}

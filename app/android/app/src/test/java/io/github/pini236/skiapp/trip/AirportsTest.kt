package io.github.pini236.skiapp.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** K-5 (decision 62): an airport from the list or a three-letter code, the same rule as the site's trip.bad_code. */
class AirportsTest {
    @Test fun aTypedCode() {
        assertEquals("LCA", Airports.code(" lca "))
        assertNull(Airports.code("TB"))
        assertNull(Airports.code("Berlin"))
        assertNull(Airports.code("תלא"))
        assertNull(Airports.code("TL1"))
    }

    @Test fun whatTheFormSaves() {
        assertTrue(Airports.ok("TBS · טביליסי"))
        assertTrue(Airports.ok("LCA"))
        assertFalse(Airports.ok("Berlin Schönefeld"))
        assertFalse(Airports.ok("ברלין"))
        assertFalse(Airports.ok(""))
    }
}

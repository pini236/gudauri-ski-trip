package io.github.pini236.skiapp.redesign

import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.data.Runs
import io.github.pini236.skiapp.data.Terrain
import io.github.pini236.skiapp.map.Locator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs

/** UX part 7, far from the mountain (bug B2): outside the model there is no dot and no follow, and the card's distance is right. */
class FarTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val terrain by lazy { Terrain.parse(File(dir, "terrain.json").readText()) }
    private val runs by lazy { Runs.parse(File(dir, "runs-and-lifts.json").readText()) }

    /** A place [km] east of the model's east edge, at the projection's latitude. */
    private fun eastOf(km: Double) = Geo.LAT0 to Geo.lon(terrain.x1 + (km * 1000).toFloat())

    @Test fun pointsOutsideTheModelAreOutsideEverywhere() {
        for (km in listOf(1.0, 1000.0)) {
            val (lat, lon) = eastOf(km)
            val x = Geo.x(lon); val y = Geo.y(lat)
            assertFalse("$km km out is not inside the model", terrain.contains(x, y))
            assertTrue(FarRules.outside(terrain, lat, lon))
            assertTrue(FarRules.outside(lat, lon))
            // the locator says Outside, so WhereDot draws nothing and "where am I" never centres (LiveWhere)
            assertEquals(Locator.Fix.Outside, Locator(runs, terrain).feed(x, y, 5f, false))
        }
    }

    @Test fun theResortCentreIsInside() {
        assertTrue(terrain.contains(Geo.x(FarRules.LON), Geo.y(FarRules.LAT)))
        assertFalse(FarRules.outside(terrain, FarRules.LAT, FarRules.LON))
        // the resort's centre is the Goodaura lift's bottom
        val g = runs.lifts.first { it.name == "Goodaura" }
        assertEquals(Geo.x(FarRules.LON), g.pts[0], 1f); assertEquals(Geo.y(FarRules.LAT), g.pts[1], 1f)
    }

    @Test fun projectionGoesBackExactly() {
        for ((lat, lon) in listOf(42.47 to 44.49, 32.0853 to 34.7818, 51.5 to -0.12)) {
            assertEquals(lat, Geo.lat(Geo.y(lat)), 1e-4)
            assertEquals(lon, Geo.lon(Geo.x(lon)), 1e-4)
        }
    }

    @Test fun distanceIsWithinOnePercent() {
        // references on the WGS84 ellipsoid (Lambert's formula): Tel Aviv 1,436.7 km, Tbilisi 88.4 km
        for ((p, ref) in listOf((32.0853 to 34.7818) to 1436.67, (41.7151 to 44.8271) to 88.43)) {
            val km = FarRules.km(p.first, p.second)
            assertTrue("$km against $ref", abs(km - ref) / ref < 0.01)
        }
        // the far test points: 1 km and 1,000 km east of the edge
        for (out in listOf(1.0, 1000.0)) {
            val (lat, lon) = eastOf(out)
            val km = FarRules.km(lat, lon)
            assertTrue("$out km past the edge gave $km", km > out && km < out + 25)
        }
    }

    @Test fun bearingPointsToGudauri() {
        // from due west the way is east; from the south, north
        assertEquals(90.0, FarRules.bearing(FarRules.LAT, FarRules.LON - 1.0), 1.0)
        assertEquals(0.0, FarRules.bearing(FarRules.LAT - 1.0, FarRules.LON), 0.5)
        // Tel Aviv: north-east
        val b = FarRules.bearing(32.0853, 34.7818)
        assertTrue("$b", b in 30.0..50.0)
    }

    @Test fun theArrowTurnsWithThePhone() {
        assertEquals(90f, FarRules.arrow(90.0, null), 0.01f)
        assertEquals(60f, FarRules.arrow(90.0, 30f), 0.01f)
        assertEquals(330f, FarRules.arrow(10.0, 40f), 0.01f)
    }

    @Test fun kilometresAreWrittenAsTheBoardsSay() {
        assertEquals("3.2", FarRules.formatKm(3.24, Locale.US))
        assertEquals("9.9", FarRules.formatKm(9.94, Locale.US))
        assertEquals("10", FarRules.formatKm(10.4, Locale.US))
        assertEquals("1,430", FarRules.formatKm(1430.4, Locale.US))
        assertEquals("1,430", FarRules.formatKm(1430.4, Locale("iw")))
    }
}

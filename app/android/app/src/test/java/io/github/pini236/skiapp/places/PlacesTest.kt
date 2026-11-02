package io.github.pini236.skiapp.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Food and services near the stations (round 31): read as the site reads places.json (site/js/app.js, PL). */
class PlacesTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")

    // a small file in the lean format: a base with three places (one of a kind this app does not know), a station with none, and a café on the slope
    private val file = """{"schema":1,"kinds":["food","cafe","aid","wc"],"maxDistance":250,
      "stations":[
        {"id":"a-bottom","label":"A bottom","ll":[42.47,44.49],"ends":[{"lift":"A","liftId":11,"end":"bottom"},{"lift":null,"liftId":12,"end":"top"}],"places":[{"p":"n1","d":58},{"p":"n2","d":90},{"p":"n3","d":99},{"p":"n4","d":120}],"info":"some"},
        {"id":"a-top","label":"A top","ll":[42.49,44.50],"ends":[{"lift":"A","liftId":11,"end":"top"}],"places":[],"info":"none"}],
      "places":[
        {"id":"n1","kind":"food","name":"Posticino","ll":[42.4701,44.4901],"seasonal":false,"slope":false},
        {"id":"n2","kind":"aid","name":null,"ll":[42.4702,44.4902],"seasonal":false,"slope":false},
        {"id":"n3","kind":"sauna","name":"Hot","ll":[42.4703,44.4903],"seasonal":false,"slope":false},
        {"id":"n4","kind":"wc","ll":[42.4704,44.4904],"seasonal":true,"slope":false},
        {"id":"n5","kind":"cafe","name":"2300","ll":[42.48,44.50],"seasonal":true,"slope":true}]}"""

    @Test fun aStationHasItsPlacesNearestFirstAndOnlyOfKindsTheAppKnows() {
        val p = Places.parse(file)!!
        assertEquals("a-bottom", p.stationOf("11", bottom = true)?.id)
        val base = p.stationOf("12", bottom = false)!!
        assertEquals("a-bottom", base.id)
        // "sauna" is not a kind the app knows: ignored, not shown as something else
        assertEquals(listOf("n1" to 58, "n2" to 90, "n4" to 120), p.near(base).map { it.place.id to it.d })
        assertEquals(listOf("food", "aid", "wc"), p.withPlaces().single().second)
    }

    @Test fun noInformationIsNotNothing() {
        val p = Places.parse(file)!!
        val top = p.stationOf("11", bottom = false)!!
        assertEquals("none", top.info)
        assertTrue(p.near(top).isEmpty())
        assertFalse(p.withPlaces().any { it.first.id == "a-top" })
        // a lift on no station (the unnamed conveyor) has no section at all
        assertNull(p.stationOf("999", bottom = true))
    }

    @Test fun anUnnamedPlaceHasNoNameAndAnOnSlopeCafeBelongsToNoStation() {
        val p = Places.parse(file)!!
        assertNull(p.places.first { it.id == "n2" }.name)
        assertEquals("Posticino", p.places.first { it.id == "n1" }.name)
        assertTrue(p.places.first { it.id == "n4" }.seasonal)
        assertEquals(listOf("2300"), p.onSlope().map { it.name })
    }

    @Test fun aFileTheAppCannotReadGivesNoPlaces() {
        assertNull(Places.parse(null))
        assertNull(Places.parse("not json"))
        assertNull(Places.parse(file.replace("\"schema\":1", "\"schema\":2")))
        assertNull(Places.parse("""{"stations":[],"places":[]}"""))
        assertNull(Places.parse("""{"schema":1,"stations":[{"id":"x"}],"places":[]}"""))
    }

    @Test fun theFeatureIsOnlyWhereResortsJsonSaysSo() {
        val resorts = File(dir, "resorts.json").readText()
        assertTrue(Places.enabled(resorts, "gudauri"))
        assertFalse(Places.enabled(resorts, "soelden"))
        assertFalse(Places.enabled(null))
        assertFalse(Places.enabled("""{"resorts":[{"id":"gudauri","features":["trip"]}]}"""))
    }

    @Test fun theSitesFileReadsAndEveryPlaceItNamesExists() {
        val p = Places.parse(File(dir, "places.json").readText())
        assertNotNull(p)
        p!!
        assertTrue(p.stations.isNotEmpty())
        val ids = p.places.map { it.id }.toSet()
        assertEquals(ids.size, p.places.size)
        for (s in p.stations) {
            assertTrue(s.id, s.ends.isNotEmpty())
            // every reference resolves (a lean file with a place it does not carry would show a station with nothing)
            if (s.info == "some") assertTrue(s.id, p.near(s).isNotEmpty())
            if (s.info != "some") assertTrue(s.id, p.near(s).isEmpty())
        }
        assertTrue(p.withPlaces().isNotEmpty())
        // every lift end the file names is one the app's data has
        val runs = io.github.pini236.skiapp.data.Runs.parse(File(dir, "runs-and-lifts.json").readText())
        val lifts = runs.lifts.map { it.id }.toSet()
        for (s in p.stations) for (e in s.ends) assertTrue("${s.id}: lift ${e.liftId}", e.liftId in lifts)
    }
}

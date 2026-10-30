package io.github.pini236.skiapp.dayrec

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The recorder is behind a capability in the site's data (m-8 condition 7), and nothing leaves the phone from here (condition 1). */
class DayFeatureTest {
    private val site = File(System.getProperty("site.data") ?: "../../../site/data")
    private fun resorts(features: String, id: String = "gudauri") = """{"schema":1,"resorts":[{"id":"$id","features":$features}]}"""

    @Test fun onlyAResortThatListsItHasTheFeature() {
        assertTrue(DayFeature.enabled(resorts("""["trip","dayrec"]""")))
        assertFalse(DayFeature.enabled(resorts("""["trip"]""")))
        assertFalse(DayFeature.enabled(resorts("[]")))
        assertFalse(DayFeature.enabled(resorts("""["dayrec"]""", id = "soelden"))) // another resort's capability
    }

    @Test fun anythingTheAppCannotReadMeansOff() {
        assertFalse(DayFeature.enabled(null))
        assertFalse(DayFeature.enabled(""))
        assertFalse(DayFeature.enabled("[]"))
        assertFalse(DayFeature.enabled("""{"resorts":[{"id":"gudauri"}]}""")) // no features list
    }

    @Test fun theSitesRealFileIsReadable() {
        // today it does not list the feature: a build that ships with it hidden (the switch is the site's data)
        val real = File(site, "resorts.json").readText()
        assertEquals(JSONObject(real).getJSONArray("resorts").getJSONObject(0).getJSONArray("features").toString().contains("dayrec"), DayFeature.enabled(real))
    }

    @Test fun theAreaIsTheTerrainModelsExtent() {
        val dem = JSONObject(File(site, "terrain.json").readText()).getJSONObject("dem")
        assertEquals(dem.getDouble("x0").toFloat(), DayParams.AREA_X0, 0.01f)
        assertEquals(dem.getDouble("y0").toFloat(), DayParams.AREA_Y0, 0.01f)
        assertEquals(dem.getDouble("x1").toFloat(), DayParams.AREA_X1, 0.01f)
        assertEquals(dem.getDouble("y1").toFloat(), DayParams.AREA_Y1, 0.01f)
    }

    @Test fun nothingInTheRecorderSendsLogsOrShowsAPosition() {
        // condition 1: no coordinate in the measurement, Sentry, the system log or a debug line. The package is read as text.
        val dir = File("src/main/java/io/github/pini236/skiapp/dayrec")
        val src = dir.listFiles { f -> f.extension == "kt" }!!.associate { it.name to it.readText() }
        assertTrue(src.isNotEmpty())
        for ((name, text) in src) {
            val code = text.lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }.joinToString("\n")
            assertFalse("$name logs to the system", Regex("""\b(Log\.[a-z]\(|println\(|Timber|Sentry\.|PostHog)""").containsMatchIn(code))
            // every debug line ("Qa.log") and every event names no place
            Regex("""(Qa\.log|Telemetry\.event|Telemetry\.handled)\(([^\n]*)""").findAll(code).forEach { m ->
                assertFalse("$name sends a position: ${m.value}", Regex("""\b(lat|lon|latitude|longitude|\.altitude|\.speed|bearing|\.x\b|\.y\b)""").containsMatchIn(m.groupValues[2]))
            }
        }
        // only the recorder's two events, and only minutes and a reason
        val events = Regex("""Telemetry\.event\("(\w+)"""").findAll(src.values.joinToString("\n")).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("dayrec_start", "dayrec_stop"), events)
    }
}

package io.github.pini236.skiapp.weather

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Weather by altitude against the shared readings (tools/fixtures/weather-m6.json, m-6), which the site reads too. */
class WeatherTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val fixture = JSONObject(File(dir, "../../tools/fixtures/weather-m6.json").readText())
    private val f = Forecast.parse(fixture.getJSONObject("answer").toString())!!
    private val cases = fixture.getJSONObject("cases")
    private fun local(s: String) = LocalDateTime.parse(s)
    private fun now(o: JSONObject) = Forecast.Now(o.getInt("temp"), o.getInt("wind"), o.getInt("gust"), o.getInt("dir"), o.getInt("snow24"))

    @Test fun freshStaleAndNoneByAge() {
        val a = cases.getJSONArray("state")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val at = local(c.getString("now")).atZone(Forecast.GUDAURI).toInstant()
            assertEquals(c.getString("now"), Forecast.State.valueOf(c.getString("state").uppercase()), f.state(at))
        }
    }

    @Test fun theHourThatHasBegun() {
        val a = cases.getJSONArray("now")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            assertEquals(c.toString(), now(c.getJSONObject("expect")), f.now(c.getString("point"), local(c.getString("now"))))
        }
        // an hour the answer does not have
        assertNull(f.now("village", local("2027-01-20T10:00")))
    }

    @Test fun atTheAltitudeOfARunsTopAndBottom() {
        val a = cases.getJSONArray("altitude")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            assertEquals(c.toString(), now(c.getJSONObject("expect")), f.at(c.getDouble("alt").toFloat(), local(c.getString("now"))))
        }
    }

    @Test fun theServersRiskForADay() {
        val a = cases.getJSONArray("risk")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i); val e = c.getJSONObject("expect")
            for (lift in Forecast.RISK_LIFTS) assertEquals(e.getString(lift), f.risk(lift, LocalDate.parse(c.getString("date"))))
        }
        assertNull(f.risk("Sadzele", LocalDate.parse("2027-03-01")))
    }

    @Test fun aBrokenOrOtherAnswerIsNone() {
        assertNull(Forecast.parse("{}"))
        assertNull(Forecast.parse("{\"schema\":2}"))
        assertNull(Forecast.parse("<html>"))
        assertNotNull(f.day("goodaura", LocalDate.parse("2027-01-14")))
        assertEquals(LocalDate.parse("2027-01-26"), f.lastDay)
    }
}

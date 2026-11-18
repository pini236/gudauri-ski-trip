package io.github.pini236.skiapp.season

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** The countdown rules against the cases the site's checker and the iPhone read too (tools/fixtures/season-countdown.json). */
class SeasonTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/fixtures").isDirectory }
    private val fx = JSONObject(File(root, "tools/fixtures/season-countdown.json").readText())

    @Test fun theSharedCases() {
        val cases = fx.getJSONArray("cases")
        val tz = java.time.ZoneId.of(fx.getString("tz"))
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val name = c.getString("name")
            val season = c.optJSONObject("season")?.let { Season.from(it, fx.getString("tz")) }
            val today = Instant.parse(c.getString("now")).atZone(tz).toLocalDate()
            val seen = Season.localDate(c.optString("seen_open").takeIf { it.isNotBlank() && !c.isNull("seen_open") })
            val got = Season.decide(season, today, c.getString("report") == "fresh", seen)
            val e = c.getJSONObject("expect")
            assertEquals(name, e.getString("state").uppercase(), got.kind.name)
            assertEquals(name, if (e.isNull("days")) null else e.getInt("days"), got.days)
            assertEquals(name, e.getBoolean("postponed"), got.postponed)
        }
    }

    @Test fun theDateIsTheResortsOwnNotThePhones() {
        // 19:59 UTC on 19 December is 23:59 in Tbilisi: still tomorrow; 20:30 UTC is already the 20th
        val s = Season.parse(File(root, "site/data/resorts.json").readText())
        assertNotNull(s)
        assertEquals(LocalDate.of(2026, 12, 19), s!!.today(Instant.parse("2026-12-19T19:59:00Z").toEpochMilli()))
        assertEquals(LocalDate.of(2026, 12, 20), s.today(Instant.parse("2026-12-19T20:30:00Z").toEpochMilli()))
    }

    @Test fun theRealFileHasTheSeason() {
        val s = Season.parse(File(root, "site/data/resorts.json").readText())!!
        assertEquals(LocalDate.of(2026, 12, 20), s.open)
        assertEquals("MTA", s.src)
        assertEquals("https://mta.ski/en/news/skiseason2027", s.url)
    }

    @Test fun aWrongFileIsNoSeason() {
        assertNull(Season.parse(null))
        assertNull(Season.parse("not json"))
        assertNull(Season.parse("""{"resorts":[{"id":"gudauri","tz":"Asia/Tbilisi"}]}"""))
        assertNull(Season.parse("""{"resorts":[{"id":"gudauri","season":{"open":"20.12.2026"}}]}"""))
        assertNull(Season.parse("""{"resorts":[{"id":"other","season":{"open":"2026-12-20"}}]}"""))
        // an unknown time zone is the resort's own default, not a reason to lose the date
        assertEquals(Season.DEFAULT_TZ, Season.parse("""{"resorts":[{"id":"gudauri","tz":"Mars/Base","season":{"open":"2026-12-20"}}]}""")!!.tz)
        // a link that is not https is dropped, the date stays
        assertNull(Season.parse("""{"resorts":[{"id":"gudauri","season":{"open":"2026-12-20","url":"http://x"}}]}""")!!.url)
    }
}

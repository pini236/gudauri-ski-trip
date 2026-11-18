package io.github.pini236.skiapp.skipass

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The price rule against the shared cases (tools/fixtures/skipass-calc.json), and the real price list. */
class PricesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/fixtures").isDirectory }
    private val prices = Prices.parse(File(root, "site/data/prices.json").readText())

    @Test fun theRealFileReads() {
        val p = prices!!
        assertEquals(8, p.passes.size)
        assertEquals(LocalDate.of(2027, 4, 30), p.until)
        assertEquals("MTA", p.src)
        assertEquals(6 to 12, p.childFrom to p.childTo)
        assertEquals(5, p.deposit)
        assertEquals(80, p.nightAdult)
        assertEquals(listOf("app.skipass_card", "app.skipass_foreign", "app.skipass_named"), p.rules)
        assertNotNull(p.buyIos); assertNotNull(p.buyAndroid)
    }

    @Test fun theSharedCases() {
        val fx = JSONObject(File(root, "tools/fixtures/skipass-calc.json").readText())
        val cases = fx.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val days = c.getInt("days")
            for ((who, child) in listOf("adult" to false, "child" to true)) {
                val want = c.optJSONObject(who)
                val got = prices!!.price(days, child)
                if (want == null) { assertNull("$days $who", got); continue }
                assertNotNull("$days $who", got)
                assertEquals("$days $who total", want.getInt("total"), got!!.total)
                val parts = want.getJSONArray("parts")
                assertEquals("$days $who parts", (0 until parts.length()).map { parts.getInt(it) }, got.parts)
            }
        }
    }

    @Test fun theTablePassesAreTheFixturesPasses() {
        val fx = JSONObject(File(root, "tools/fixtures/skipass-calc.json").readText()).getJSONArray("passes")
        assertEquals(fx.length(), prices!!.passes.size)
        for (i in 0 until fx.length()) {
            val f = fx.getJSONObject(i); val p = prices.passes[i]
            assertEquals(f.getInt("days"), p.days); assertEquals(f.getInt("adult"), p.adult); assertEquals(f.getInt("child"), p.child)
        }
    }

    @Test fun anOldPriceListIsNoInformation() {
        val p = prices!!
        assertTrue(p.valid(LocalDate.of(2027, 4, 30)))
        assertFalse(p.valid(LocalDate.of(2027, 5, 1)))
    }

    @Test fun onlyTheTwoStoreLinksAreOpened() {
        assertNotNull(Prices.appleLink("https://apps.apple.com/app/id6698866856"))
        assertNotNull(Prices.appleLink("https://apps.apple.com/app/id6698866856?hl=he"))
        assertNull(Prices.appleLink("https://apps.apple.com/app/id1?ct=x"))
        assertNull(Prices.appleLink("http://apps.apple.com/app/id1"))
        assertNull(Prices.appleLink("https://evil.example/apps.apple.com/app/id1"))
        assertNotNull(Prices.playLink("https://play.google.com/store/apps/details?id=com.lemondo.skipass"))
        assertNotNull(Prices.playLink("https://play.google.com/store/apps/details?id=com.lemondo.skipass&hl=ka"))
        assertNull(Prices.playLink("https://play.google.com/store/apps/details?id=com.lemondo.skipass&referrer=utm_source%3Dx"))
        assertNull(Prices.playLink("https://play.google.com/store/apps/details?id=a&utm_source=x"))
        assertNull(Prices.playLink(null))
    }

    @Test fun aFileThatIsNotKnownIsNotUsed() {
        assertNull(Prices.parse(null))
        assertNull(Prices.parse("{}"))
        val raw = File(root, "site/data/prices.json").readText()
        assertNull(Prices.parse(raw.replace("\"schema\": 1", "\"schema\": 2")))
        assertNull(Prices.parse(raw.replace("\"GEL\"", "\"ILS\"")))
        assertNull(Prices.parse(raw.replace("\"foreign\"", "\"local\"")))
        // a bad link inside a good file is only ignored
        val bad = Prices.parse(raw.replace("https://play.google.com/store/apps/details?id=com.lemondo.skipass", "https://evil.example/"))
        assertNotNull(bad); assertNull(bad!!.buyAndroid); assertNotNull(bad.buyIos)
    }

    @Test fun theFeatureIsListedForGudauri() {
        assertTrue(Prices.enabled(File(root, "site/data/resorts.json").readText()))
        assertFalse(Prices.enabled("""{"resorts":[{"id":"gudauri","features":["places"]}]}"""))
        assertFalse(Prices.enabled(null))
    }
}

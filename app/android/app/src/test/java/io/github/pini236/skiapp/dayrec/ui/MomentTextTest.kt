package io.github.pini236.skiapp.dayrec.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.util.Locale

/**
 * The words of the moments against i18n/strings.json: the placeholder order table is exactly the Hebrew text's (Android numbers
 * the arguments by it), and every item of the engine's test values turns into keys that exist, with a value for every
 * placeholder of every variant of every language.
 */
class MomentTextTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val root = dir.parentFile.parentFile
    private val strings = JSONObject(File(root, "i18n/strings.json").readText()).getJSONObject("strings")
    private val ph = Regex("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}")
    private val zone = ZoneId.of("Asia/Tbilisi")

    private fun hebrewOrder(key: String): List<String> {
        val he = strings.getJSONObject(key).get("he")
        val forms = if (he is JSONObject) he.keys().asSequence().map { he.getString(it) }.toList() else listOf(he as String)
        val out = ArrayList<String>()
        for (f in forms) for (m in ph.findAll(f)) if (m.groupValues[1] !in out) out += m.groupValues[1]
        return out
    }

    private fun momentKeys() = strings.keys().asSequence().filter { it.startsWith("moment.") }.toList()

    @Test fun theOrderTableIsTheHebrewOrder() {
        for (k in momentKeys()) assertEquals(k, hebrewOrder(k), MomentText.orderOf(k))
    }

    private fun items(): List<JSONObject> {
        val fx = JSONObject(File(root, "tools/fixtures/dayrec-moments.json").readText())
        val out = ArrayList<JSONObject>()
        fun grab(o: JSONObject) { val a = (o.optJSONObject("expected") ?: o).optJSONArray("items") ?: return; for (i in 0 until a.length()) out += a.getJSONObject(i) }
        for (name in listOf("cases", "chain", "trip_chain")) { val a = fx.getJSONArray(name); for (i in 0 until a.length()) grab(a.getJSONObject(i)) }
        return out
    }

    @Test fun everyItemOfTheTestValuesHasWords() {
        val seen = HashSet<String>()
        for (it in items()) {
            val plan = MomentText.plan(it, zone, Locale.ENGLISH)
            assertNotNull(it.getString("key"), plan)
            seen += it.getString("key")
            val parts = listOfNotNull(plan!!.title) + plan.lines
            for (part in parts) {
                var n = 0
                while (strings.has("${part.key}.${n + 1}")) {
                    val full = "${part.key}.${n + 1}"
                    for (name in MomentText.orderOf(full)) assertTrue("$full: no value for {$name}", part.args.containsKey(name))
                    n++
                }
                assertTrue("${part.key}: no text", n > 0)
                assertEquals("${part.key}: plural by count", strings.getJSONObject("${part.key}.1").get("he") is JSONObject, part.qty != null)
            }
        }
        assertTrue("the test values cover many kinds of moment: $seen", seen.size >= 12)
    }

    @Test fun formatsLikeTheCopyReadmeSays() {
        assertEquals("3", MomentText.times(3.0, Locale.ENGLISH))
        assertEquals("2,9", MomentText.times(2.9, Locale("ru")))
        assertEquals("1,7", MomentText.dec(1691.3 / 1000.0, Locale("ru")))
    }

    @Test fun headIsTheThemeOverTheNumbers() {
        val h = MomentText.head("explore", 12, 5544.4, 31300.0, Locale.ENGLISH)
        assertEquals("moment.story.head.explore", h.title!!.key)
        assertEquals(12, h.lines[0].qty)
        assertEquals(5544, h.lines[0].args["drop"])
        assertEquals("31.3", h.lines[0].args["km"])
        assertEquals("moment.story.head.steady", MomentText.head(null, 1, 100.0, 1000.0, Locale.ENGLISH).title!!.key)
    }
}

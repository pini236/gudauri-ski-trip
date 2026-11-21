package io.github.pini236.skiapp.fieldtools

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The field testers' export against tools/fixtures/dayrec-export.json (made by tools/dayrec/export.py): the same lines for the
 * same log, with and without the tester's agreement to motion windows. A line is compared as JSON (a whole number may be
 * written 8 or 8.0).
 */
class RawExportTest {
    private val root = File(System.getProperty("site.data") ?: "../../../site/data").parentFile.parentFile
    private val fx = JSONObject(File(root, "tools/fixtures/dayrec-export.json").readText())
    private val input = (0 until fx.getJSONArray("input").length()).joinToString("\n", postfix = "\n") { fx.getJSONArray("input").getString(it) }

    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { same(a.get(it), b.get(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.get(it), b.get(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }

    private fun crop(allow: Boolean): String {
        val terrain = JSONObject(File(root, "site/data/terrain.json").readText())
        val margin = JSONObject(File(root, "tools/fixtures/dayrec-params.json").readText()).getJSONObject("export").getDouble("margin_m")
        val (proj, rect) = RawExport.rectOf(terrain, margin)
        // the rectangle of the fixture is the one the app makes from the terrain model
        val r = fx.getJSONArray("rect")
        assertEquals(r.getDouble(0), rect.x0, 1e-6); assertEquals(r.getDouble(1), rect.y0, 1e-6)
        assertEquals(r.getDouble(2), rect.x1, 1e-6); assertEquals(r.getDouble(3), rect.y1, 1e-6)
        return RawExport.crop(input, proj, rect, fx.getJSONObject("header_extra"), allow)!!
    }

    @Test fun `gives the lines of the reference tool`() {
        val cases = fx.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val got = crop(c.getBoolean("allow_impacts")).trim().split('\n')
            val want = c.getJSONArray("expected")
            assertEquals(c.getString("name"), want.length(), got.size)
            for (k in 0 until got.size) assertTrue("${c.getString("name")} line $k: ${got[k]} != ${want.getString(k)}", same(JSONObject(got[k]), JSONObject(want.getString(k))))
        }
    }

    @Test fun `header has no identity and says it was cut`() {
        val head = JSONObject(crop(false).lineSequence().first())
        for (k in listOf("device", "account")) assertFalse(k, head.has(k))
        assertTrue(head.getBoolean("cropped"))
    }

    @Test fun `a day with nothing inside gives nothing`() {
        val (proj, rect) = RawExport.rectOf(JSONObject(File(root, "site/data/terrain.json").readText()), 500.0)
        val far = "{\"v\":1,\"tz\":\"Asia/Tbilisi\",\"resort\":\"gudauri\",\"start\":1}\n{\"t\":1000,\"la\":50.0,\"lo\":10.0}\n"
        assertEquals(null, RawExport.crop(far, proj, rect, JSONObject(), true))
    }

    @Test fun `the gate wants the code, which is not in the repository`() {
        assertEquals(TesterGate.hash("ab-cd 12"), TesterGate.hash("ABCD12"))
        assertNotEquals(TesterGate.hash("ABCD12"), TesterGate.hash("ABCD13"))
    }
}

package io.github.pini236.skiapp.i18n

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The generated language files (tools/build-app-strings.py, from i18n/strings.json): every language has every
 * name with the same placeholders, the language lists match the files and Lang.ALL, and a store build never carries
 * a language that still waits for a native speaker (i18n/REVIEW.md).
 */
class StringsTest {
    private val gen = File("build/generated/i18n")
    private val repo = File(System.getProperty("site.data") ?: "../../../site/data").parentFile.parentFile

    private fun entries(f: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = HashMap<String, String>()
        val s = doc.getElementsByTagName("string")
        for (i in 0 until s.length) {
            val n = s.item(i)
            if (n.attributes.getNamedItem("translatable")?.nodeValue != "false") out[n.attributes.getNamedItem("name").nodeValue] = n.textContent
        }
        val p = doc.getElementsByTagName("plurals")
        for (i in 0 until p.length) out["plurals:" + p.item(i).attributes.getNamedItem("name").nodeValue] = p.item(i).textContent
        return out
    }

    private fun placeholders(s: String) = Regex("%(\\d+)\\$[sd]").findAll(s).map { it.groupValues[1] }.toSortedSet()

    private fun languages(build: String): List<String> {
        val dir = File(gen, build)
        val config = Regex("android:name=\"([a-z-]+)\"").findAll(File(dir, "xml/locales_config.xml").readText()).map { it.groupValues[1] }.toList()
        val default = Regex("name=\"lang\"[^>]*>([a-z]+)<").find(File(dir, "values/strings.xml").readText())!!.groupValues[1]
        val others = dir.listFiles { f -> f.name.startsWith("values-") }!!.map { it.name.removePrefix("values-") }.sorted()
        assertEquals("$build: locales_config lists the files", (listOf(default) + others).sorted(), config.sorted())
        return config
    }

    @Test fun everyLanguageHasEveryString() {
        val dir = File(gen, "debug")
        val base = entries(File(dir, "values/strings.xml"))
        assertTrue(base.size > 700)
        for (lang in languages("debug").drop(1)) {
            val other = entries(File(dir, "values-$lang/strings.xml"))
            assertEquals("values-$lang: names", base.keys, other.keys)
            for ((k, v) in base) if (!k.startsWith("plurals:")) assertEquals("values-$lang/$k: placeholders", placeholders(v), placeholders(other.getValue(k)))
        }
        assertTrue("unknown language", Lang.ALL.map { it.tag }.containsAll(languages("debug")))
    }

    @Test fun storeBuildsCarryOnlyReviewedLanguages() {
        val strings = JSONObject(File(repo, "i18n/strings.json").readText()).getJSONObject("strings")
        val pending = HashSet<String>()
        for (k in strings.keys()) strings.getJSONObject(k).optJSONArray("check")?.let { a -> for (i in 0 until a.length()) pending += a.getString(i) }
        val release = languages("release")
        assertTrue("store build carries unreviewed $pending ∩ $release", release.none { it in pending })
        assertEquals("he", release.first())
    }
}

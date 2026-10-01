package io.github.pini236.skiapp.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The language files stay in step: every values-xx/strings.xml has every name of the default (Hebrew) file, with the
 * same placeholders, and every language the app has is in locales_config.xml and in Lang.ALL.
 */
class StringsTest {
    private val res = File("src/main/res")

    private fun strings(f: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }

    private fun placeholders(s: String) = Regex("%(\\d+\\$)?[sd]").findAll(s).map { it.value }.sorted().toList()

    private val languages = res.listFiles { f -> f.isDirectory && Regex("values-[a-z]{2}").matches(f.name) && File(f, "strings.xml").isFile }!!.map { it.name.removePrefix("values-") }

    @Test fun everyLanguageHasEveryString() {
        val base = strings(File(res, "values/strings.xml"))
        assertTrue(base.size > 10)
        for (lang in languages) {
            val other = strings(File(res, "values-$lang/strings.xml"))
            assertEquals("values-$lang: names", base.keys, other.keys)
            for ((k, v) in base) assertEquals("values-$lang/$k: placeholders", placeholders(v), placeholders(other.getValue(k)))
        }
    }

    @Test fun theLanguageListMatchesTheFiles() {
        val config = Regex("android:name=\"([a-z-]+)\"").findAll(File(res, "xml/locales_config.xml").readText()).map { it.groupValues[1] }.toSet()
        val default = Regex("name=\"lang\"[^>]*>([a-z]+)<").find(File(res, "values/strings.xml").readText())!!.groupValues[1]
        assertEquals(setOf(default) + languages, config)
        val known = Lang.ALL.map { it.tag }.toSet()
        assertTrue("unknown language in locales_config: $config", known.containsAll(config))
    }
}

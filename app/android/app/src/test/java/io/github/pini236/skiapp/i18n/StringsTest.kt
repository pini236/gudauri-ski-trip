package io.github.pini236.skiapp.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The generated language files (tools/build-app-strings.py, from i18n/strings.json): every language has every
 * name with the same placeholders, the language lists match the files and Lang.ALL, and a store build carries exactly
 * the released languages (build.gradle.kts).
 */
class StringsTest {
    private val gen = File("build/generated/i18n")

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
        val others = dir.listFiles { f -> f.name.startsWith("values-") }!!.map { it.name.removePrefix("values-").let { q -> if (q == "iw") "he" else q } }.sorted()
        assertEquals("$build: locales_config lists the files", (listOf(default) + others).distinct().sorted(), config.sorted())
        return config
    }

    @Test fun everyLanguageHasEveryString() {
        val dir = File(gen, "debug")
        val base = entries(File(dir, "values/strings.xml"))
        assertTrue(base.size > 700)
        for (lang in languages("debug")) {
            val other = entries(File(dir, "values-${if (lang == "he") "iw" else lang}/strings.xml"))
            assertEquals("values-$lang: names", base.keys, other.keys)
            for ((k, v) in base) if (!k.startsWith("plurals:")) assertEquals("values-$lang/$k: placeholders", placeholders(v), placeholders(other.getValue(k)))
        }
        assertTrue("unknown language", Lang.ALL.map { it.tag }.containsAll(languages("debug")))
    }

    /** A phone in a language the app does not have reads values/: English (3.10.2026); Hebrew is values-iw. */
    @Test fun englishIsTheDefault() {
        for (build in listOf("debug", "release")) {
            val default = Regex("name=\"lang\"[^>]*>([a-z]+)<").find(File(gen, "$build/values/strings.xml").readText())!!.groupValues[1]
            assertEquals("$build: values/", "en", default)
            assertTrue("$build: values-iw", File(gen, "$build/values-iw/strings.xml").readText().contains(">he<"))
            assertTrue("$build: values-en", File(gen, "$build/values-en/strings.xml").readText().contains(">en<"))
        }
        assertEquals("en", Lang.byTag("fr").tag)
        assertEquals("he", Lang.byTag("iw").tag)
        // without a choice in the app: Hebrew on a phone set to Hebrew, English on any other (Pini, 3.10.2026)
        assertEquals("he", Lang.auto(java.util.Locale.forLanguageTag("he-IL")))
        assertEquals("he", Lang.auto(java.util.Locale("iw")))
        for (t in listOf("en-US", "ru-RU", "ka-GE", "fr-FR")) assertEquals(t, "en", Lang.auto(java.util.Locale.forLanguageTag(t)))
    }

    @Test fun storeBuildsCarryTheReleasedLanguages() {
        // the released languages are set in the build (releaseLanguages); Hebrew is always first, the source
        val gradle = File("build.gradle.kts").readText()
        val released = Regex("val releaseLanguages = \"([a-z,]+)\"").find(gradle)!!.groupValues[1].split(",")
        assertEquals("he", released.first())
        assertEquals(released.sorted(), languages("release").sorted())
    }

    /** A joiner or a suffix keeps its spaces (Android trims them unless escaped): "Goodaura ו-New Goodaura", not "Goodauraו-". */
    @Test fun spacesAtTheEdgesAreKept() {
        assertEquals("\\u0020ו-", entries(File(gen, "debug/values-iw/strings.xml"))["meet_lift_names_join"])
        assertEquals("\\u0020and\\u0020", entries(File(gen, "debug/values/strings.xml"))["meet_lift_names_join"])
    }
}

package io.github.pini236.skiapp.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A-24: every row of the licenses page has its text packed (licenses/ goes into assets/licenses), or a page on the web. */
class LicensesTest {
    @Test fun everyLicenseHasItsText() {
        val dir = File("../licenses")
        for (l in OPEN_LICENSES) {
            if (l.file != null) assertTrue(l.file, File(dir, l.file).isFile)
            else assertTrue(l.key, l.url?.startsWith("https://") == true)
        }
        assertTrue(OPEN_LICENSES.map { it.key }.toSet().size == OPEN_LICENSES.size)
    }
}

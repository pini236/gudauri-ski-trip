package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Part 7 of the redesign: the architect's conditions on the fonts (docs/ARCHITECTURE.md, 23.11.2026): the new fonts
 * together within 600 KB in the package, and every packed font with its licence beside it.
 */
class GudiFontTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/android/licenses").isDirectory }

    @Test fun theNewFontsAreWithinTheBudget() {
        val new = listOf(File(root, "app/android/app/src/preview/res/font/heebo.ttf"))
        val total = new.sumOf { it.length() }
        assertTrue("the new fonts are $total bytes, over 600 KB", total in 1..600_000L)
    }

    @Test fun heeboCarriesItsLicence() {
        val ofl = File(root, "app/android/licenses/OFL-Heebo.txt")
        assertTrue(ofl.isFile && ofl.readText().contains("SIL Open Font License"))
        assertTrue(File(root, "app/android/licenses/NOTICE.txt").readText().contains("Heebo"))
    }

    @Test fun everyWeightOfTheBoardIsMade() {
        assertTrue(GudiFont.WEIGHTS.toList() == listOf(300, 400, 500, 700, 900))
    }
}

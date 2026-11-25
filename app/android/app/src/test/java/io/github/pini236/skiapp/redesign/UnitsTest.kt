package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun theUnitStaysWithItsNumber() {
        assertEquals("התחנה התחתונה · 2,161 מ׳", Units.keep("התחנה התחתונה · 2,161 מ׳"))
        assertEquals("the top station · 2,705 m", Units.keep("the top station · 2,705 m"))
        assertEquals("3 m · later", Units.keep("3 m · later"))
        assertEquals("Tatra 2 more", Units.keep("Tatra 2 more"))   // a word, not a unit
    }
}

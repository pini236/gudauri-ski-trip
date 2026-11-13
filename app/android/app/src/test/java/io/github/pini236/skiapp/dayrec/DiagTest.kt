package io.github.pini236.skiapp.dayrec

import io.github.pini236.skiapp.qa.Diag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagTest {
    @Test fun shortTextIsKept() {
        assertEquals("a\nb\n", Diag.trim("a\nb\n"))
    }

    @Test fun longTextKeepsTheNewestLinesWhole() {
        val lines = (1..3000).joinToString("") { "line $it ........................\n" }
        val out = Diag.trim(lines)
        assertTrue(out.length <= Diag.KEEP)
        assertTrue(out.startsWith("line "))
        assertTrue(out.endsWith("line 3000 ........................\n"))
    }
}

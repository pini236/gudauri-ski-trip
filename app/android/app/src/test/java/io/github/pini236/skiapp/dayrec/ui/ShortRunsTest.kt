package io.github.pini236.skiapp.dayrec.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortRunsTest {
    @Test fun aSharedStretchIsSaidAsTheMapSaysIt() {
        assertEquals("Tatra 1 · 2", shortRuns("Tatra 1 · Tatra 2"))
        assertEquals("Goodaura 1 · 2 · 3", shortRuns("Goodaura 1 · Goodaura 2 · Goodaura 3"))
    }

    @Test fun differentNamesStayWhole() {
        assertEquals("Tatra 1 · Kikilo 1", shortRuns("Tatra 1 · Kikilo 1"))
        assertEquals("Shino", shortRuns("Shino"))
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRulesTest {
    @Test fun clockStartsAtTheFirstFix() {
        assertEquals(0L, SearchRules.sinceFix(50_000L, 0L)) // no fix yet: no time
        assertEquals(0L, SearchRules.sinceFix(10_000L, 10_000L))
        assertEquals(12_000L, SearchRules.sinceFix(22_000L, 10_000L))
    }
    @Test fun hintOnlyAfterThirtySeconds() {
        assertFalse(SearchRules.showHint(29_000L, 1L)); assertTrue(SearchRules.showHint(31_001L, 1L)); assertFalse(SearchRules.showHint(99_000L, 0L))
    }
    @Test fun ringFill() {
        assertEquals(0f, SearchRules.fill(null)); assertEquals(.5f, SearchRules.fill(40.0)); assertEquals(1f, SearchRules.fill(120.0))
    }
    @Test fun pulsesAreStaggered() {
        assertEquals(0f, SearchRules.phase(0, 0)); assertEquals(.8f / 2.4f, SearchRules.phase(800, 0), 1e-4f)
        assertTrue(SearchRules.alpha(0f) > SearchRules.alpha(.5f)); assertEquals(0f, SearchRules.alpha(1f), 1e-4f)
    }
}

package io.github.pini236.skiapp.redesign

import org.junit.Assert.assertEquals
import org.junit.Test

/** UX pass part 6: which game is first, the order of the rest, and the grid's columns. */
class GamesRulesTest {
    @Test fun noHistoryStartsWithTheMergeGame() {
        assertEquals("merge" to listOf("descent", "school", "snowball", "fresh"), GamesRules.order(null))
    }
    @Test fun theLastPlayedIsFirstAndLeavesTheRest() {
        val (first, rest) = GamesRules.order("school")
        assertEquals("school", first)
        assertEquals(listOf("merge", "descent", "snowball", "fresh").sorted(), rest.sorted())
        assertEquals(4, rest.size)
    }
    @Test fun anUnknownKeyIsNoHistory() {
        assertEquals(GamesRules.order(null), GamesRules.order("zzz"))
    }
    @Test fun twoColumnsUpToFontScale13() {
        assertEquals(2, GamesRules.columns(1f)); assertEquals(2, GamesRules.columns(1.3f)); assertEquals(1, GamesRules.columns(1.31f))
    }
}

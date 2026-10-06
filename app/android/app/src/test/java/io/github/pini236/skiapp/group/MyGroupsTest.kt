package io.github.pini236.skiapp.group

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** "My groups" (K-4) in the site's order: by start date, the undated last, ties as they came (account.js, refresh). */
class MyGroupsTest {
    private fun g(id: String, start: String?) = GroupSummary(id, id, start?.let(LocalDate::parse), null)

    @Test fun byStartDateUndatedLast() {
        val list = listOf(g("later", "2027-02-14"), g("none", null), g("first", "2027-01-10"), g("tie", "2027-02-14"))
        assertEquals(listOf("first", "later", "tie", "none"), list.byStart().map { it.id })
    }
}

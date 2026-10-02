package io.github.pini236.skiapp.group

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every refusal the server can send (server/CONTRACT.md, "שגיאות") reads as its own message, not "something went wrong". */
class ErrorsTest {
    private val contract = File(File(System.getProperty("site.data") ?: "../../../site/data").parentFile.parentFile, "server/CONTRACT.md").readText()

    /** Codes a person never meets as such: a broken request, or a server fault that only "try again" can answer. */
    private val general = setOf("invalid_json", "use_post", "not_found", "conflict", "cannot_move_meetup", "server_error")

    @Test fun everyCodeInTheContractHasAMessage() {
        val table = contract.substringAfter("## שגיאות").substringBefore("\n## ")
        val codes = Regex("`([a-z_]+)`").findAll(table.lines().filter { it.startsWith("| `") }.joinToString("\n")).map { it.groupValues[1] }.toSet()
        assertTrue("the errors table was not found in the contract", codes.size > 20)
        val fallback = errorRes("no_such_code")
        for (code in codes - general) assertNotEquals("no message for $code", fallback, errorRes(code))
    }
}

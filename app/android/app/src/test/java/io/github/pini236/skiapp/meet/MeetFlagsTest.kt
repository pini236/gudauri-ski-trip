package io.github.pini236.skiapp.meet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The meeting point's flag on the mountain against the cases the iPhone reads too (tools/fixtures/meet-flag.json). */
class MeetFlagsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "tools/fixtures").isDirectory }
    private val fx = JSONObject(File(root, "tools/fixtures/meet-flag.json").readText())

    @Test fun theSharedCases() {
        val tz = ZoneId.of(fx.getString("tz"))
        val known = fx.getJSONArray("known").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        val cases = fx.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val ms = c.getJSONArray("meetups").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map {
                MeetFlags.In(it.getString("id"), it.getString("group"), it.getString("station"), Instant.parse(it.getString("at")))
            } }
            val want = c.getJSONArray("flags").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { f -> f.getString("station") to f.getString("id") } } }
            val got = MeetFlags.pick(ms, Instant.parse(c.getString("now")), known, tz).map { it.station to it.id }
            assertEquals(c.getString("name"), want, got)
        }
    }
}

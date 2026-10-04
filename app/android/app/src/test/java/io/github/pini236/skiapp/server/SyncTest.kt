package io.github.pini236.skiapp.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** The phone's copy and the queue of writes, against a fake server that can lose its signal. */
class SyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private var online = true
    private val calls = mutableListOf<Request>()
    private var members = """[{"user_id":"u1","display_name":"פיני","role":"admin","trip_id":null},{"user_id":"u2","display_name":"דובי","role":"member","trip_id":"t2"}]"""
    private val meetups = JSONArray()
    private var best = 0
    private var refuse: String? = null
    private var hiccup: String? = null
    private var garbled: String? = null
    private val planned = mutableListOf<Pair<Long, Runnable>>()

    private val fake = Transport { r ->
        if (!online) throw Offline(java.io.IOException("no signal"))
        calls += r
        val path = r.url.substringAfter(".co")
        when {
            r.method == "POST" && path.startsWith("/functions/v1/api/") -> {
                val action = path.substringAfterLast('/')
                if (action == refuse) return@Transport Response(403, """{"error":"not_member"}""")
                if (action == hiccup) return@Transport Response(503, """{"error":"server_error"}""")
                if (action == garbled) return@Transport Response(200, "<html>proxy</html>")
                when (action) {
                    "submit_score" -> { best = maxOf(best, JSONObject(r.body!!).getInt("score")); Response(200, """{"best":$best}""") }
                    "group_leaderboard" -> Response(200, if (best > 0) """[{"user_id":"u1","display_name":"פיני","best":$best}]""" else "[]")
                    "set_my_membership" -> Response(200, "{}")
                    else -> Response(404, """{"error":"unknown_action"}""")
                }
            }
            r.method == "GET" && path.startsWith("/rest/v1/group_members?select=groups") ->
                Response(200, """[{"groups":{"id":"g1","name":"גודאורי","starts_on":"2027-01-10","ends_on":"2027-01-15"}}]""")
            r.method == "GET" && path.startsWith("/rest/v1/group_members") -> Response(200, members)
            r.method == "GET" && path.startsWith("/rest/v1/trips") ->
                Response(200, """[{"id":"t2","owner_id":"u2","entered_by":"u1","out_date":"2027-01-10","out_flight":"6H 897","out_from":"TLV","out_to":"TBS"}]""")
            r.method == "GET" && path.startsWith("/rest/v1/meetups") -> Response(200, meetups.toString())
            r.method == "POST" && path == "/rest/v1/meetups" -> {
                val row = JSONObject(r.body!!).put("id", "m${meetups.length() + 1}").put("created_by", "u1")
                meetups.put(row)
                Response(201, JSONArray().put(row).toString())
            }
            r.method == "GET" && path.startsWith("/rest/v1/invites") -> Response(200, """[{"id":"i1","code":"ABCDEF","token":"${"t".repeat(32)}","requires_approval":false,"max_uses":null,"expires_at":null}]""")
            r.method == "GET" && path.startsWith("/rest/v1/join_requests") -> Response(200, "[]")
            else -> Response(404, "{}")
        }
    }

    private val store = MemorySessionStore(Session("a", "r", now / 1000 + 3600, "u1", false))
    private fun sync() = Sync(Server("https://x.supabase.co", "pk", store, fake, { now }), tmp.root, listOf("descent"), realtime = null, worker = { it.run() },
        later = { ms, task -> planned += ms to task })

    @Test fun aGroupIsKeptOnThePhone() {
        val s = sync()
        val g = s.group("g1")
        assertNull(g.state.value.readAt)
        g.open()
        val snap = g.state.value
        assertEquals("גודאורי", snap.group!!.name)
        assertEquals(2, snap.members.size)
        assertEquals("6H 897", snap.flights.getValue("t2").out.flight)
        assertEquals("ABCDEF", snap.invite!!.code)
        assertEquals(mapOf("t2" to "u1"), snap.enteredBy) // I entered Dubi's flight as an admin
        assertEquals(now, snap.readAt)

        // a new launch with no signal: the same group, from the file, marked as not fresh
        online = false
        val again = sync().group("g1")
        assertEquals("גודאורי", again.state.value.group!!.name)
        assertEquals("6H 897", again.state.value.flights.getValue("t2").out.flight)
        again.refresh()
        assertTrue(again.state.value.offline)
    }

    @Test fun writesWithoutSignalWaitAndGoLater() {
        val s = sync()
        s.group("g1").open()
        online = false
        val g = s.group("g1")
        g.submitScore("descent", 120)
        g.submitScore("descent", 150) // replaces the one still waiting
        val local = g.addMeetup("goodaura-top", Instant.parse("2027-01-11T09:30:00Z"))
        g.changeMeetup(local, "kudebi-top", Instant.parse("2027-01-11T10:00:00Z"), "קפה")
        g.setMine("פיני ז.", "t9")

        // shown at once
        var snap = g.state.value
        assertEquals(150, snap.scores.getValue("descent").single().best)
        assertEquals("kudebi-top", snap.meetups.single().station)
        assertEquals("פיני ז.", snap.me("u1")!!.name)
        assertEquals(3, snap.waiting)

        // the app closes; the queue is in a file
        online = true
        calls.clear()
        val later = sync()
        later.flush()
        val sent = calls.filter { it.method == "POST" }.map { it.url.substringAfter(".co") }
        assertEquals(listOf("/functions/v1/api/submit_score", "/rest/v1/meetups", "/functions/v1/api/set_my_membership"), sent.take(3))
        assertEquals(150, best)
        val meetupSent = JSONObject(calls.first { it.url.endsWith("/rest/v1/meetups") }.body!!)
        assertEquals("kudebi-top", meetupSent.getString("station"))
        assertEquals("קפה", meetupSent.getString("note"))

        later.group("g1").refresh()
        snap = later.group("g1").state.value
        assertEquals(0, snap.waiting)
        assertEquals("m1", snap.meetups.single().id)
        assertEquals("u1", snap.meetups.single().createdBy)
    }

    @Test fun aRefusedWriteIsDroppedAndTheTruthShown() {
        val s = sync()
        val g = s.group("g1")
        g.open()
        refuse = "set_my_membership"
        g.setMine("שם אחר", null)
        assertEquals(0, g.state.value.waiting)
        assertEquals("פיני", g.state.value.me("u1")!!.name)
        // and the page can say why, until it is closed
        assertEquals("not_member", g.state.value.refused)
        g.refresh()
        assertEquals("not_member", g.state.value.refused)
        g.clearRefused()
        assertNull(g.state.value.refused)
    }

    @Test fun aServerHiccupKeepsTheWriteInTheQueue() {
        val s = sync()
        val g = s.group("g1")
        g.open()
        hiccup = "set_my_membership"
        g.setMine("שם אחר", null)
        assertEquals("a 503 is not a refusal: it waits", 1, g.state.value.waiting)
        hiccup = null
        s.flush()
        assertEquals(0, g.state.value.waiting)
    }

    @Test fun aMeetupRemovedBeforeItWasSentNeverGoes() {
        val s = sync()
        val g = s.group("g1")
        g.open()
        online = false
        val local = g.addMeetup("goodaura-top", Instant.parse("2027-01-11T09:30:00Z"))
        g.removeMeetup(local)
        assertEquals(0, g.state.value.waiting)
        online = true
        calls.clear()
        s.flush()
        assertFalse(calls.any { it.url.endsWith("/rest/v1/meetups") && it.method == "POST" })
    }

    @Test fun removedFromTheGroup() {
        val s = sync()
        val g = s.group("g1")
        g.open()
        members = """[{"user_id":"u2","display_name":"דובי","role":"admin","trip_id":null}]"""
        g.refresh()
        assertTrue(g.state.value.gone)
        assertFalse(java.io.File(tmp.root, "groups/g1.json").exists())
    }

    @Test fun myGroupsAndForgetting() {
        val s = sync()
        s.refreshMine()
        assertEquals("g1", s.myGroups.value.groups.single().id)
        assertEquals("g1", sync().myGroups.value.groups.single().id) // from the file
        s.group("g1").open()
        s.forget()
        assertTrue(s.myGroups.value.groups.isEmpty())
        assertTrue(tmp.root.listFiles()!!.isEmpty())
    }

    @Test fun anAnswerThatIsNotJsonKeepsTheWriteAndDoesNotCrash() {
        val s = sync()
        val g = s.group("g1")
        g.open()
        garbled = "submit_score"
        g.submitScore("descent", 40)
        assertEquals("the score waits", 1, g.state.value.waiting)
        assertEquals(listOf(Sync.FIRST_RETRY_MS), planned.map { it.first })
        garbled = null
        planned.removeAt(0).second.run() // the planned try
        assertEquals(0, g.state.value.waiting)
        assertEquals(40, best)
        assertEquals("a queue that got through starts the count again", 0L, s.nextRetryMs)
    }

    @Test fun aStuckQueueIsTriedAgainWithAGrowingWait() {
        val s = sync()
        val g = s.group("g1")
        g.open()
        hiccup = "submit_score"
        g.submitScore("descent", 10)
        val waits = mutableListOf<Long>()
        repeat(9) {
            waits += planned.single().first
            planned.removeAt(0).second.run()
        }
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L, 300_000L), waits)
        hiccup = null
        planned.removeAt(0).second.run()
        assertEquals(10, best)
        assertTrue("nothing more is planned", planned.isEmpty())
    }

    @Test fun noSignalPlansATryAndAQueueThatWentThroughPlansNone() {
        val s = sync()
        val g = s.group("g1")
        g.open()
        online = false
        g.submitScore("descent", 7)
        assertEquals(1, planned.size)
        g.submitScore("descent", 8) // tried again by the person: still one try planned, not two
        assertEquals(1, planned.size)
        online = true
        planned.removeAt(0).second.run()
        assertEquals(8, best)
        assertTrue(planned.isEmpty())
    }
}

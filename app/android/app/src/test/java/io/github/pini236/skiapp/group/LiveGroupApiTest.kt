package io.github.pini236.skiapp.group

import io.github.pini236.skiapp.server.MemorySessionStore
import io.github.pini236.skiapp.server.Request
import io.github.pini236.skiapp.server.Response
import io.github.pini236.skiapp.server.Server
import io.github.pini236.skiapp.server.Groups
import io.github.pini236.skiapp.server.Session
import io.github.pini236.skiapp.server.Sync
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The screens' API over the server client: how the server's rows become the group page, and its refusals. */
class LiveGroupApiTest {
    private val now = 1_800_000_000_000L
    private val sent = mutableListOf<Request>()
    /** Answers by the start of the path after the host ("rest/v1/groups", "functions/v1/api/join_group"). */
    private val answers = mutableMapOf<String, String>()
    private val store = MemorySessionStore()
    private val pushed = mutableListOf<Trip>()
    private val adopted = mutableListOf<Pair<String, Trip>>()
    private var myTripId: String? = null
    private var kept: String? = null

    /** The phone's one trip row on the server (server/TripSync.kt), in memory. */
    private val myTrip = object : MyTripOnServer {
        override val tripId get() = myTripId
        override fun sendNow(trip: Trip): String { pushed += trip; myTripId = "my-trip"; return "my-trip" }
        override fun adopt(id: String, trip: Trip) { adopted += id to trip; myTripId = id }
    }

    private fun api(): LiveGroupApi {
        val server = Server("https://x.supabase.co", "pk", store, { r ->
            sent += r
            val path = r.url.removePrefix("https://x.supabase.co/").substringBefore('?')
            answers[path]?.let { Response(200, it) } ?: Response(400, """{"error":"invalid_input"}""")
        }, { now })
        return LiveGroupApi(server, store, myTrip, { "he" }, ready = true, keep = { kept = it })
    }

    private fun signedIn(user: String = "u1", anon: Boolean = false) =
        store.save(Session("tok", "ref", now / 1000 + 3600, user, anon))

    private val trip = """{"id":"t1","owner_id":"u2","entered_by":"u1","out_date":"2027-01-10","out_flight":"GD 101","out_from":"TLV","out_to":"TBS",
        "out_departs":"16:00:00","out_arrives":"20:35:00","ret_date":"2027-01-15","ret_flight":"GD 102","ret_from":"TBS","ret_to":"TLV",
        "ret_departs":"01:35:00","ret_arrives":"02:15:00","ski_from":null,"ski_to":null}"""

    @Test fun theGroupPageFromTheRows() = runBlocking {
        signedIn("u1")
        answers["rest/v1/groups"] = """[{"id":"g1","name":"Gudauri 2027","starts_on":"2027-01-10","ends_on":"2027-01-15"}]"""
        answers["rest/v1/group_members"] = """[{"user_id":"u1","display_name":"Noa","role":"admin","trip_id":null},
            {"user_id":"u2","display_name":"Avi","role":"member","trip_id":"t1"}]"""
        answers["rest/v1/trips"] = "[$trip]"
        answers["rest/v1/invites"] = """[{"id":"i1","group_id":"g1","code":"KZBQRM","token":"${"t".repeat(40)}","requires_approval":true,"max_uses":null,"expires_at":null}]"""
        answers["rest/v1/join_requests"] = """[{"id":"r1","group_id":"g1","user_id":"u9","display_name":"Dana","kind":"reclaim","reclaim_user_id":"u3","status":"pending"}]"""
        answers["rest/v1/meetups"] = """[{"id":"m1","station":"158744075b","meet_at":"2027-01-11T05:30:00+00:00","note":null,"created_by":"u2"}]"""
        val g = api().group("g1")
        assertEquals("Gudauri 2027", g.name)
        assertEquals(LocalDate.of(2027, 1, 10), g.startsOn)
        assertTrue(g.admin)
        val avi = g.members[1]
        assertFalse(avi.me)
        assertEquals("GD 101", avi.trip!!.out.flight)
        assertEquals("TLV", avi.trip!!.out.fromCode)
        // Noa filled it in for Avi, and Avi has not changed it
        assertEquals("Noa", avi.enteredByAdmin)
        assertEquals(JoinRequest("r1", "u9", "Dana", reclaim = true), g.requests.single())
        assertEquals("KZBQRM", g.invite!!.code)
        assertEquals("Avi", g.meetups.single().byName)
        assertEquals(1, g.admins)
    }

    @Test fun theGroupPageFromThePhonesCopy() {
        signedIn("u1")
        val avi = Trip(Leg(LocalDate.of(2027, 1, 10), "GD 101", "TLV", "TBS"))
        val s = Sync.Snapshot(
            "g1", Groups.Group("g1", "Gudauri 2027", LocalDate.of(2027, 1, 10), null),
            listOf(Groups.Member("u1", "Noa", "admin"), Groups.Member("u2", "Avi", "member", "t1")),
            flights = mapOf("t1" to avi), enteredBy = mapOf("t1" to "u1"),
            meetups = listOf(Groups.Meetup("local:x", "g1", "158744075b", Instant.parse("2027-01-11T05:30:00Z"), null, "u1")),
            invite = Groups.Invite("i1", "KZBQRM", "t".repeat(40), true, null, null),
            requests = listOf(Groups.JoinRequest("r1", "g1", "u9", "Dana", "join", null, "pending")),
            readAt = now, offline = true, waiting = 1,
        )
        val shown = api().shown(s)
        val g = shown.group!!
        assertTrue(g.admin)
        assertEquals("GD 101", g.members[1].trip!!.out.flight)
        assertEquals("Noa", g.members[1].enteredByAdmin)
        // a meetup made here without signal shows at once, under my name
        assertEquals("Noa", g.meetups.single().byName)
        assertEquals(JoinRequest("r1", "u9", "Dana", reclaim = false), g.requests.single())
        assertEquals("KZBQRM", g.invite!!.code)
        assertTrue(shown.offline && shown.waiting)
        assertEquals(now, shown.readAt)
    }

    @Test fun aGroupNeverReadOrLeftShowsNoGroup() {
        signedIn("u1")
        assertNull(api().shown(Sync.Snapshot("g1")).group)
        val left = api().shown(Sync.Snapshot("g1", gone = true, readAt = now))
        assertNull(left.group)
        assertTrue(left.gone)
    }

    @Test fun aMemberSeesNoRequestsAndTheirOwnTripIsNotEnteredByAnyone() = runBlocking {
        signedIn("u2")
        answers["rest/v1/groups"] = """[{"id":"g1","name":"G","starts_on":null,"ends_on":null}]"""
        answers["rest/v1/group_members"] = """[{"user_id":"u2","display_name":"Avi","role":"member","trip_id":"t1"}]"""
        answers["rest/v1/trips"] = "[" + trip.replace("\"entered_by\":\"u1\"", "\"entered_by\":\"u2\"") + "]"
        answers["rest/v1/invites"] = "[]"
        answers["rest/v1/meetups"] = "[]"
        val g = api().group("g1")
        assertNull(g.members.single().enteredByAdmin)
        assertTrue(g.requests.isEmpty())
        assertTrue(sent.none { it.url.contains("join_requests") })
    }

    @Test fun theSameFlightOverwritesTheTripThePhoneKeeps() = runBlocking {
        signedIn("u1")
        myTripId = "mine"
        answers["rest/v1/trips"] = "[$trip]"
        answers["functions/v1/api/same_flight"] = """{"trip_id":"mine"}"""
        val t = api().sameFlight("g1", "t1")
        assertEquals("GD 101", t.out.flight)
        val body = JSONObject(sent.last { it.url.endsWith("same_flight") }.body!!)
        assertEquals("g1", body.getString("group_id"))
        assertEquals("t1", body.getString("trip_id"))
        assertEquals("mine", body.getString("my_trip_id"))
        // one row a person (decision 27): the server wrote it, so nothing is sent from the phone and no copy is made
        assertEquals(listOf("mine" to t), adopted)
        assertTrue(pushed.isEmpty())
        assertTrue(sent.none { it.url.contains("set_my_membership") || (it.url.contains("rest/v1/trips") && it.method != "GET") })
    }

    @Test fun theSameFlightWithNoTripOnTheServerYetAdoptsTheNewRow() = runBlocking {
        signedIn("u1")
        answers["rest/v1/trips"] = "[$trip]"
        answers["functions/v1/api/same_flight"] = """{"trip_id":"new-row"}"""
        val t = api().sameFlight("g1", "t1")
        assertFalse(JSONObject(sent.last { it.url.endsWith("same_flight") }.body!!).has("my_trip_id"))
        assertEquals(listOf("new-row" to t), adopted)
        assertEquals("new-row", myTripId)
    }

    @Test fun theSameFlightRefusedKeepsTheRowThePhoneHas() = runBlocking {
        signedIn("u1")
        myTripId = "mine"
        answers["rest/v1/trips"] = "[$trip]"
        try {
            api().sameFlight("g1", "t1"); fail()
        } catch (e: ApiException) {
            assertEquals("invalid_input", e.code)
        }
        assertTrue(adopted.isEmpty())
        assertEquals("mine", myTripId)
    }

    @Test fun joiningAsAGuestKeepsTheNameAndTheStatus() = runBlocking {
        answers["auth/v1/signup"] = """{"access_token":"a","refresh_token":"r","expires_at":${now / 1000 + 3600},"user":{"id":"u5","is_anonymous":true}}"""
        answers["functions/v1/api/join_group"] = """{"status":"pending","group_id":"g1"}"""
        val api = api()
        assertNull(api.me())
        assertEquals(JoinResult(JoinStatus.PENDING, "g1"), api.join("kzb-qrm", "Avi"))
        val me = api.me()!!
        assertEquals("u5", me.userId)
        assertFalse(me.registered)
        assertEquals("Avi", me.name)
        assertEquals("Avi", JSONObject(kept!!).getString("name"))
    }

    @Test fun theSitesActions() = runBlocking {
        signedIn("u1")
        answers["functions/v1/api/claim_admin"] = "{}"
        answers["functions/v1/api/revoke_invite"] = "{}"
        answers["functions/v1/api/cancel_join_request"] = "{}"
        answers["rest/v1/join_requests"] = """[{"id":"r1","group_id":"g1","user_id":"u1","display_name":"Noa","kind":"approval","reclaim_user_id":null,"status":"pending"},
            {"id":"r2","group_id":"g2","user_id":"u1","display_name":"Noa","kind":"approval","reclaim_user_id":null,"status":"pending"}]"""
        val a = api()
        a.claimAdmin("g1")
        a.revokeInvite("i1")
        // only the request to this group is withdrawn
        a.cancelRequest("g1")
        val calls = sent.filter { it.url.contains("/functions/v1/api/") }.map { it.url.substringAfterLast('/') to JSONObject(it.body!!) }
        assertEquals(listOf("claim_admin", "revoke_invite", "cancel_join_request"), calls.map { it.first })
        assertEquals("g1", calls[0].second.getString("group_id"))
        assertEquals("i1", calls[1].second.getString("invite_id"))
        assertEquals("r1", calls[2].second.getString("request_id"))
    }

    @Test fun aNewPhoneGetsMyTripBack() = runBlocking {
        signedIn("u2")
        answers["rest/v1/trips"] = "[${trip.replace("\"entered_by\":\"u1\"", "\"entered_by\":null")}]"
        val t = api().myTripOnServer()
        assertEquals("GD 101", t!!.out.flight)
        assertEquals("t1", adopted.single().first)
        val q = sent.last().url
        assertTrue(q, q.contains("owner_id=eq.u2") && q.contains("order=updated_at.desc") && q.contains("limit=1"))
        // nobody signed in: nothing to bring back
        store.save(null)
        assertNull(api().myTripOnServer())
    }

    @Test fun refusalsKeepTheServersCode() = runBlocking {
        signedIn()
        try {
            api().setRole("g1", "u2", Role.ADMIN); fail()
        } catch (e: ApiException) {
            assertEquals("invalid_input", e.code)
        }
        store.save(null)
        try {
            api().group("g1"); fail()
        } catch (e: ApiException) {
            assertEquals("not_signed_in", e.code)
        }
        assertEquals(emptyList<GroupSummary>(), api().myGroups())
    }
}

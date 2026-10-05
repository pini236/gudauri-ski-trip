package io.github.pini236.skiapp.server

import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The client against a fake server: what it sends, and how it reads the answers. */
class ServerTest {
    private val now = 1_800_000_000_000L
    private val sent = mutableListOf<Request>()
    private val answers = ArrayDeque<Response>()
    private fun server(store: SessionStore = MemorySessionStore()) =
        Server("https://x.supabase.co", "pk", store, { r -> sent += r; answers.removeFirstOrNull() ?: Response(500, "") }, { now })

    private fun session(token: String, expires: Long = now / 1000 + 3600, anon: Boolean = true) =
        """{"access_token":"$token","refresh_token":"r-$token","expires_at":$expires,"user":{"id":"u1","is_anonymous":$anon}}"""

    @Test fun aGuestIsMadeOnlyToJoin() {
        val s = server()
        try {
            s.call("my_account"); fail()
        } catch (e: ServerError) {
            assertEquals("no_session", e.code)
        }
        assertTrue(sent.isEmpty())
        assertEquals(emptyList<Groups.Group>(), Groups(s).mine())
        assertTrue(sent.isEmpty())

        answers += Response(200, session("a"))
        answers += Response(200, """{"status":"joined","group_id":"g1"}""")
        val j = Groups(s).join(" abc-def ", " Pini ")
        assertEquals(Groups.Joined("joined", "g1"), j)
        assertEquals("https://x.supabase.co/auth/v1/signup", sent[0].url)
        val call = sent[1]
        assertEquals("https://x.supabase.co/functions/v1/api/join_group", call.url)
        assertEquals("Bearer a", call.headers["Authorization"])
        assertEquals("pk", call.headers["apikey"])
        assertEquals("eu-central-1", call.headers["x-region"])
        val body = JSONObject(call.body!!)
        assertEquals("ABCDEF", body.getString("code"))
        assertEquals("Pini", body.getString("display_name"))
        assertTrue(s.auth.current()!!.anonymous)
    }

    @Test fun anEndingTokenIsRenewedFirst() {
        val store = MemorySessionStore()
        val s = server(store)
        store.save(Session.fromAuth(JSONObject(session("old", expires = now / 1000 + 30)), now / 1000))
        answers += Response(200, session("new"))
        answers += Response(200, """{"id":"u1","display_name":null,"lang":"he","is_anonymous":true}""")
        val me = Account(s).me()!!
        assertNull(me.name)
        assertTrue(me.anonymous)
        assertEquals("https://x.supabase.co/auth/v1/token?grant_type=refresh_token", sent[0].url)
        assertEquals("r-old", JSONObject(sent[0].body!!).getString("refresh_token"))
        assertEquals("Bearer new", sent[1].headers["Authorization"])
    }

    @Test fun aRefusedTokenIsRenewedOnceAndADroppedSessionIsNotReplaced() {
        val store = MemorySessionStore()
        val s = server(store)
        store.save(Session.fromAuth(JSONObject(session("a")), now / 1000))
        answers += Response(401, """{"code":"PGRST301"}""")
        answers += Response(200, session("b"))
        answers += Response(200, "[]")
        assertEquals(0, s.select("groups", "select=id").length())
        assertEquals("Bearer b", sent[2].headers["Authorization"])

        // the server no longer knows the session: forgotten, and no new guest is made behind the person's back
        sent.clear()
        answers += Response(401, "")
        answers += Response(400, """{"error_code":"refresh_token_not_found"}""")
        try {
            s.call("join_group", JSONObject()); fail()
        } catch (e: ServerError) {
            assertEquals("no_session", e.code)
        }
        assertNull(store.load())
        assertEquals(2, sent.size)
    }

    @Test fun aBusyServerDoesNotEndTheSession() {
        val store = MemorySessionStore()
        val s = server(store)
        store.save(Session.fromAuth(JSONObject(session("a", expires = now / 1000 + 10)), now / 1000)) // about to end: renewed first
        answers += Response(429, """{"error_code":"over_request_rate_limit"}""")
        try {
            s.call("join_group", JSONObject()); fail()
        } catch (e: ServerError) {
            assertEquals(429, e.status)
        }
        assertEquals("still signed in", "a", store.load()?.accessToken)
    }

    @Test fun refusalsKeepTheServersCode() {
        val store = MemorySessionStore()
        store.save(Session.fromAuth(JSONObject(session("a")), now / 1000))
        val s = server(store)
        answers += Response(403, """{"error":"not_admin"}""")
        try {
            s.call("delete_group", JSONObject().put("group_id", "g")); fail()
        } catch (e: ServerError) {
            assertEquals("not_admin", e.code)
            assertEquals(403, e.status)
        }
        answers += Response(502, "<html>")
        try {
            s.call("leave_group"); fail()
        } catch (e: ServerError) {
            assertEquals("http_502", e.code)
        }
    }

    @Test fun tableRefusalsGetShortCodes() {
        assertEquals("too_many_trips", Server.code(Response(400, """{"code":"P0001","message":"too_many_trips"}""")))
        assertEquals("invalid_input", Server.code(Response(400, """{"code":"23514","message":"new row violates check"}""")))
        assertEquals("not_allowed", Server.code(Response(403, """{"code":"42501","message":"row-level security"}""")))
        assertEquals("http_500", Server.code(Response(500, "")))
    }

    @Test fun noAnswerIsOffline() {
        val store = MemorySessionStore()
        store.save(Session.fromAuth(JSONObject(session("a")), now / 1000))
        val s = Server("https://x", "pk", store, { throw Offline(java.io.IOException("no signal")) }, { now })
        try {
            s.call("leave_group"); fail()
        } catch (_: Offline) {
        }
        assertTrue(store.load() != null) // kept: it is the signal, not the session
    }

    @Test fun previewReadsTheGroup() {
        val store = MemorySessionStore()
        store.save(Session.fromAuth(JSONObject(session("a")), now / 1000))
        val s = server(store)
        answers += Response(200, """{"status":"ok","group_id":"g1","name":"גודאורי","starts_on":"2027-01-10","ends_on":null,
            "requires_approval":true,"already_member":false,"members":[{"user_id":"u2","display_name":"דובי"}]}""")
        val p = Groups(s).preview("https://example/j/AbCdEfGhIjKlMnOpQrStUvWxYz012345_-")
        assertEquals("AbCdEfGhIjKlMnOpQrStUvWxYz012345_-", JSONObject(sent[0].body!!).getString("code"))
        assertEquals(Groups.Group("g1", "גודאורי", LocalDate.of(2027, 1, 10), null), p.group)
        assertTrue(p.requiresApproval)
        assertFalse(p.alreadyMember)
        assertEquals(listOf(Groups.Member("u2", "דובי")), p.members)

        answers += Response(200, """{"status":"invalid_code"}""")
        assertEquals(Groups.Preview("invalid_code"), Groups(s).preview("zzzzzz"))
    }

    @Test fun aGuestSigningInWithGoogleIsLinkedNotReplaced() {
        val store = MemorySessionStore()
        store.save(Session.fromAuth(JSONObject(session("guest")), now / 1000))
        val s = server(store)
        answers += Response(200, session("g", anon = false))
        s.auth.signInWithIdToken("google", "id-token")
        assertEquals("https://x.supabase.co/auth/v1/token?grant_type=id_token", sent[0].url)
        assertEquals("Bearer guest", sent[0].headers["Authorization"])
        val body = JSONObject(sent[0].body!!)
        assertTrue(body.getBoolean("link_identity"))
        assertEquals("google", body.getString("provider"))
        assertFalse(s.auth.current()!!.anonymous)

        // the account exists already: the merge signs in plainly
        sent.clear()
        answers += Response(200, session("g2", anon = false))
        s.auth.signInWithIdToken("google", "id-token", link = false)
        assertNull(sent[0].headers["Authorization"])
        assertFalse(JSONObject(sent[0].body!!).has("link_identity"))
    }

    @Test fun theGroupPage() {
        val store = MemorySessionStore()
        store.save(Session.fromAuth(JSONObject(session("a")), now / 1000))
        val s = server(store)
        val g = Groups(s)
        answers += Response(200, """[{"user_id":"u2","display_name":"דובי","best":120},{"user_id":"u1","display_name":"פיני","best":90}]""")
        assertEquals(listOf(Groups.Score("u2", "דובי", 120), Groups.Score("u1", "פיני", 90)), g.leaderboard("g1", "descent"))
        answers += Response(200, """[{"id":"m1","group_id":"g1","station":"goodaura-top","meet_at":"2027-01-11T09:30:00+00:00","note":null}]""")
        val m = g.meetups("g1").single()
        assertEquals(java.time.Instant.parse("2027-01-11T09:30:00Z"), m.at)
        assertNull(m.note)
        assertTrue(sent[1].url.contains("/rest/v1/meetups?") && sent[1].url.contains("group_id=eq.g1"))
        answers += Response(200, """[{"user_id":"u1","display_name":"פיני","role":"admin","trip_id":null},{"user_id":"u2","display_name":"דובי","role":"member","trip_id":"t2"}]""")
        val members = g.members("g1")
        assertTrue(members[0].admin)
        assertNull(members[0].tripId)
        assertEquals("t2", members[1].tripId)
        answers += Response(409, """{"error":"last_admin"}""")
        try {
            g.setRole("g1", "u1", "member"); fail()
        } catch (e: ServerError) {
            assertEquals("last_admin", e.code)
        }
    }

    @Test fun aUsedUpOrExpiredInviteIsNotOffered() {
        val store = MemorySessionStore()
        store.save(Session.fromAuth(JSONObject(session("a")), now / 1000))
        val s = server(store)
        val t32 = "t".repeat(32)
        // newest first: used up, then expired by the trip's end, then a working one
        answers += Response(200, """[
            {"id":"i3","code":"CCCCCC","token":"$t32","requires_approval":false,"max_uses":5,"uses":5,"expires_at":null,"created_at":"2027-01-01T00:00:00+00:00","groups":{"ends_on":"2027-12-01"}},
            {"id":"i2","code":"BBBBBB","token":"$t32","requires_approval":false,"max_uses":null,"uses":0,"expires_at":null,"created_at":"2027-01-01T00:00:00+00:00","groups":{"ends_on":"2020-01-15"}},
            {"id":"i1","code":"AAAAAA","token":"$t32","requires_approval":false,"max_uses":10,"uses":3,"expires_at":"2099-01-01T00:00:00+00:00","created_at":"2027-01-01T00:00:00+00:00","groups":{"ends_on":null}}]""")
        val inv = Groups(s).invite("g1")!!
        assertEquals("i1", inv.id)
        assertEquals(3, inv.uses)
        assertTrue(sent[0].url.contains("select=*,groups(ends_on)"))
        answers += Response(200, """[{"id":"i3","code":"CCCCCC","token":"$t32","requires_approval":false,"max_uses":1,"uses":1,"expires_at":null,"created_at":"2027-01-01T00:00:00+00:00","groups":{"ends_on":null}}]""")
        assertNull(Groups(s).invite("g1"))
    }

    @Test fun sameFlightOverwritesTheTripThePhoneKeeps() {
        val store = MemorySessionStore()
        store.save(Session.fromAuth(JSONObject(session("a")), now / 1000))
        val s = server(store)
        answers += Response(200, """{"trip_id":"mine"}""")
        assertEquals("mine", Groups(s).sameFlight("g1", "t2", "mine"))
        assertEquals("mine", JSONObject(sent[0].body!!).getString("my_trip_id"))
        answers += Response(200, """{"trip_id":"new"}""")
        Groups(s).sameFlight("g1", "t2")
        assertFalse(JSONObject(sent[1].body!!).has("my_trip_id"))
    }

    @Test fun codes() {
        assertEquals("ABCDEF", Groups.normalizeCode("abc def"))
        assertEquals("ABCDEF", Groups.normalizeCode("ABC-DEF"))
        assertEquals("abcdefg", Groups.normalizeCode(" abcdefg "))
        val token = "AbCdEfGhIjKlMnOpQrStUvWxYz012345"
        assertEquals(token, Groups.normalizeCode("https://gudauri.vercel.app/join/$token?utm_source=app"))
        assertEquals(token, Groups.normalizeCode("https://gudauri.vercel.app/#join?t=$token"))
    }

    @Test fun theTripRoundTrips() {
        val d = { day: Int -> LocalDate.of(2027, 1, day) }
        val t = Trip(
            Leg(d(10), "6H 897", "TLV · תל אביב", "TBS · טביליסי", LocalTime.of(16, 0), LocalTime.of(20, 35)),
            Leg(d(15), "6H 892", "TBS · טביליסי", "TLV · תל אביב", LocalTime.of(1, 35), LocalTime.of(2, 15)),
        )
        val row = TripRow.of(t)
        assertEquals("TLV", row.getString("out_from"))
        assertEquals("TBS", row.getString("out_to"))
        assertEquals("טביליסי", row.getString("destination"))
        assertEquals("01:35", row.getString("ret_departs"))
        assertTrue(row.isNull("ski_from"))
        // the server answers times with seconds; places come back as codes
        val back = TripRow.trip(JSONObject(row.toString()).put("out_departs", "16:00:00"))!!
        assertEquals(t.skiDays(), back.skiDays())
        assertEquals("TLV", back.out.fromCode)
        assertEquals(LocalTime.of(16, 0), back.out.departs)
        // a place typed without a code stays on the phone
        assertTrue(TripRow.of(Trip(Leg(d(10), from = "Tel Aviv"))).isNull("out_from"))
        assertNull(TripRow.trip(JSONObject()))
    }

    @Test fun aSuccessThatIsNotJsonIsAnErrorNotACrash() {
        val store = MemorySessionStore(Session("a", "r", now / 1000 + 3600, "u1", false))
        val s = server(store)
        for (body in listOf("<html>proxy</html>", "{\"cut", "[1,")) {
            answers += Response(200, body)
            try {
                s.call("my_account"); fail()
            } catch (e: ServerError) {
                assertEquals("bad_response", e.code)
                assertEquals(200, e.status)
            }
        }
        answers += Response(200, "not a list")
        try {
            s.select("groups", "select=id"); fail()
        } catch (e: ServerError) {
            assertEquals("bad_response", e.code)
        }
        answers += Response(200, "")
        assertEquals(0, s.call("set_my_membership").length()) // an empty answer is fine: {}
    }
}

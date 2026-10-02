package io.github.pini236.skiapp.server

import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * The group, as the app sees it (docs/USERS.md, screens Q1 to Q10): typed calls over [Server], one per action of
 * the server's code (server/README.md, "הפעולות") and the reads the group page needs. The screens call these from a
 * background thread; every call may throw [Offline] or [ServerError] (its code is the server's: "not_admin",
 * "last_admin", "admin_must_register"...).
 */
class Groups(private val server: Server) {

    data class Member(val userId: String, val name: String, val role: String = "member", val tripId: String? = null) {
        val admin: Boolean get() = role == "admin"
    }

    data class Group(val id: String, val name: String, val startsOn: LocalDate?, val endsOn: LocalDate?)

    /** A working invite (Q3, Q4): the six letters to say aloud, and the token for the link. */
    data class Invite(
        val id: String, val code: String, val token: String, val requiresApproval: Boolean, val maxUses: Int?, val expiresAt: String?,
        /** How many joined with it; at [maxUses] it stops working. */
        val uses: Int = 0,
        /** When it stops working: [expiresAt], or else the day after the trip ends, or else 90 days after it was made. */
        val validUntil: Instant? = null,
    ) {
        /** Still lets people in (not used up, not expired); a revoked one is never returned. */
        fun working(now: Instant = Instant.now()): Boolean = (maxUses == null || uses < maxUses) && (validUntil == null || now < validUntil)
    }

    /** What an invite opens (Q5): the group and who is in it. [status] is "ok" or why not ("invalid_code"...). */
    data class Preview(
        val status: String,
        val group: Group? = null,
        val requiresApproval: Boolean = false,
        val alreadyMember: Boolean = false,
        val members: List<Member> = emptyList(),
    )

    /**
     * "joined", "pending" (waits for an admin), "already_member", or why not: "invalid_code", "rate_limited",
     * "group_full"; for "I'm already in the group" also "no_such_member" and "sign_in_instead".
     */
    data class Joined(val status: String, val groupId: String?)

    /** A request waiting for an admin (Q10): to join, or to be a member again ("reclaim", [reclaimUserId]). */
    data class JoinRequest(val id: String, val groupId: String, val userId: String, val name: String, val kind: String, val reclaimUserId: String?, val status: String)

    /** [createdBy]: who set it (null once that person deleted their account, or while it waits to be sent). */
    data class Meetup(val id: String, val groupId: String, val station: String, val at: Instant, val note: String?, val createdBy: String? = null)

    data class Score(val userId: String, val name: String, val best: Int)

    // --- Joining (Q4 to Q6) -------------------------------------------------

    /** A code is six letters (Q4) or a link's token; a whole link works too. Counts as an attempt. */
    fun preview(code: String): Preview {
        val o = server.call("invite_preview", JSONObject().put("code", normalizeCode(code)))
        val status = o.optString("status")
        if (status != "ok") return Preview(status)
        val members = objects(o.optJSONArray("members")).map { Member(it.getString("user_id"), it.getString("display_name")) }
        return Preview(status, group(o, "group_id"), o.optBoolean("requires_approval"), o.optBoolean("already_member"), members)
    }

    fun join(code: String, displayName: String): Joined =
        joined(server.call("join_group", JSONObject().put("code", normalizeCode(code)).put("display_name", displayName.trim())))

    /** "I'm already in the group" (Q6): be [memberId] again, a guest who lost their phone; an admin approves. */
    fun reclaim(code: String, memberId: String): Joined =
        joined(server.call("request_reclaim", JSONObject().put("code", normalizeCode(code)).put("member_id", memberId)))

    fun cancelRequest(requestId: String) = action("cancel_join_request", JSONObject().put("request_id", requestId))

    /** My requests still waiting (so the app can show "waiting for approval" after a restart). */
    fun myRequests(): List<JoinRequest> {
        val me = server.auth.current()?.userId ?: return emptyList()
        return objects(server.select("join_requests", "select=*&status=eq.pending&user_id=eq.${enc(me)}")).map(::request)
    }

    // --- My groups ----------------------------------------------------------

    /** Registered only (a guest gets "not_registered"); I am its admin, with a first invite ready. */
    fun create(name: String, displayName: String, startsOn: LocalDate? = null, endsOn: LocalDate? = null, tripId: String? = null): String =
        server.call("create_group", JSONObject().put("name", name.trim()).put("display_name", displayName.trim())
            .put("starts_on", startsOn?.toString() ?: JSONObject.NULL).put("ends_on", endsOn?.toString() ?: JSONObject.NULL)
            .put("trip_id", tripId ?: JSONObject.NULL)).getString("group_id")

    /** The groups I am in. Reading needs a session: without one there are none. */
    fun mine(): List<Group> {
        val me = server.auth.current()?.userId ?: return emptyList()
        return objects(server.select("group_members", "select=groups(id,name,starts_on,ends_on)&user_id=eq.${enc(me)}"))
            .mapNotNull { it.optJSONObject("groups") }.map { group(it, "id") }
    }

    /** My name in the group and which of my trips it shows (null: none). */
    fun setMine(groupId: String, displayName: String, tripId: String?) =
        action("set_my_membership", JSONObject().put("group_id", groupId).put("display_name", displayName.trim()).put("trip_id", tripId ?: JSONObject.NULL))

    /**
     * "I'm on the same flight" (Q7): that member's flight becomes mine, shown in the group. A person has one trip
     * (decision 27): [myTripId] (the one the phone keeps, [ServerTripSync.tripId]) is overwritten, or else the one this
     * group shows for me; only with neither is a new one made. Returns my trip's id.
     */
    fun sameFlight(groupId: String, tripId: String, myTripId: String? = null): String =
        server.call("same_flight", JSONObject().put("group_id", groupId).put("trip_id", tripId).apply { myTripId?.let { put("my_trip_id", it) } })
            .getString("trip_id")

    fun leave(groupId: String) = action("leave_group", JSONObject().put("group_id", groupId))

    /** A registered member of a group left without an admin takes it over. */
    fun claimAdmin(groupId: String) = action("claim_admin", JSONObject().put("group_id", groupId))

    // --- The group page (Q7 to Q9) ------------------------------------------

    fun members(groupId: String): List<Member> =
        objects(server.select("group_members", "select=user_id,display_name,role,trip_id&group_id=eq.${enc(groupId)}&order=joined_at,user_id"))
            .map { Member(it.getString("user_id"), it.getString("display_name"), it.getString("role"), str(it, "trip_id")) }

    /** A trip shown in the group, and who entered it: an admin for a member (Q7, "entered by") or its owner. */
    data class Flight(val id: String, val ownerId: String, val enteredBy: String?, val trip: Trip) {
        val byAdmin: Boolean get() = enteredBy != null && enteredBy != ownerId
    }

    /** The trips the group's members show (Q7), by trip id. */
    fun flights(groupId: String): Map<String, Trip> = flightDetails(groupId).mapValues { it.value.trip }

    fun flightDetails(groupId: String): Map<String, Flight> {
        val ids = members(groupId).mapNotNull { it.tripId }
        if (ids.isEmpty()) return emptyMap()
        return objects(server.select("trips", "select=*&id=in.(${ids.joinToString(",") { enc(it) }})")).mapNotNull { o ->
            TripRow.trip(o)?.let { o.getString("id") to Flight(o.getString("id"), o.getString("owner_id"), str(o, "entered_by"), it) }
        }.toMap()
    }

    fun meetups(groupId: String): List<Meetup> =
        objects(server.select("meetups", "select=id,group_id,station,meet_at,note,created_by&group_id=eq.${enc(groupId)}&order=meet_at")).map(::meetup)

    /** Any member may add, change or remove a meetup (Q8); the last one saved wins. */
    fun addMeetup(groupId: String, station: String, at: Instant, note: String? = null, id: String? = null): Meetup {
        val row = JSONObject().put("group_id", groupId).put("station", station).put("meet_at", at.toString()).put("note", note ?: JSONObject.NULL)
        // an id from the phone (the queue's own): a meetup made without signal and sent twice is still one meetup
        if (id != null) row.put("id", id)
        val made = (if (id != null) server.insertOnce("meetups", row) else server.insert("meetups", row))
            ?: server.select("meetups", "select=id,group_id,station,meet_at,note,created_by&id=eq.${enc(id!!)}").optJSONObject(0)
            ?: throw ServerError("conflict", 409)
        return meetup(made)
    }

    fun changeMeetup(id: String, station: String, at: Instant, note: String? = null): Meetup? =
        server.update("meetups", id, JSONObject().put("station", station).put("meet_at", at.toString()).put("note", note ?: JSONObject.NULL))?.let(::meetup)

    fun removeMeetup(id: String) = server.delete("meetups", id)

    /** My best is kept only if higher, so sending twice (after no signal) does no harm. The best kept. */
    fun submitScore(game: String, score: Int): Int = server.call("submit_score", JSONObject().put("game", game).put("score", score)).getInt("best")

    /** The group's table for one game (Q9), best first. */
    fun leaderboard(groupId: String, game: String): List<Score> =
        objects(server.callList("group_leaderboard", JSONObject().put("group_id", groupId).put("game", game)))
            .map { Score(it.getString("user_id"), it.getString("display_name"), it.getInt("best")) }

    // --- Admins (Q3, Q10) ---------------------------------------------------

    fun update(groupId: String, name: String, startsOn: LocalDate?, endsOn: LocalDate?) =
        action("update_group", JSONObject().put("group_id", groupId).put("name", name.trim())
            .put("starts_on", startsOn?.toString() ?: JSONObject.NULL).put("ends_on", endsOn?.toString() ?: JSONObject.NULL))

    fun delete(groupId: String) = action("delete_group", JSONObject().put("group_id", groupId))

    /**
     * The working invite, or null: every member sees it (the server makes one with the group). One used up to its
     * limit, or expired, is not returned: sharing it would only show "invalid code".
     */
    fun invite(groupId: String): Invite? =
        objects(server.select("invites", "select=*,groups(ends_on)&group_id=eq.${enc(groupId)}&revoked_at=is.null&order=created_at.desc&limit=5"))
            .map(::invite).firstOrNull { it.working(Instant.ofEpochMilli(server.nowSeconds() * 1000)) }

    /** A new invite that replaces the working one (a leaked code stops working). */
    fun newInvite(groupId: String, requiresApproval: Boolean = false, maxUses: Int? = null, expiresAt: Instant? = null): Invite =
        invite(server.call("create_invite", JSONObject().put("group_id", groupId).put("requires_approval", requiresApproval)
            .put("max_uses", maxUses ?: JSONObject.NULL).put("expires_at", expiresAt?.toString() ?: JSONObject.NULL)))

    fun revokeInvite(inviteId: String) = action("revoke_invite", JSONObject().put("invite_id", inviteId))

    fun requests(groupId: String): List<JoinRequest> =
        objects(server.select("join_requests", "select=*&status=eq.pending&group_id=eq.${enc(groupId)}&order=created_at")).map(::request)

    fun decide(requestId: String, approve: Boolean) = action("decide_join_request", JSONObject().put("request_id", requestId).put("approve", approve))

    fun remove(groupId: String, userId: String) = action("remove_member", JSONObject().put("group_id", groupId).put("user_id", userId))

    /** "admin" or "member"; at least one admin stays, and admins are registered. */
    fun setRole(groupId: String, userId: String, role: String) = action("set_member_role", JSONObject().put("group_id", groupId).put("user_id", userId).put("role", role))

    /** An admin fills in a member's flight (Q7, "entered by"); not over a trip the member keeps themselves. */
    fun setMemberTrip(groupId: String, userId: String, trip: Trip): String =
        server.call("set_member_trip", JSONObject().put("group_id", groupId).put("user_id", userId).put("trip", TripRow.of(trip))).getString("trip_id")

    private fun action(name: String, body: JSONObject) {
        server.call(name, body)
    }

    companion object {
        private fun enc(s: String) = Server.enc(s)
        private fun objects(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
        private fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
        private fun group(o: JSONObject, idKey: String) = Group(o.getString(idKey), o.getString("name"), date(o, "starts_on"), date(o, "ends_on"))
        private fun joined(o: JSONObject) = Joined(o.optString("status"), str(o, "group_id"))
        private fun meetup(o: JSONObject) = Meetup(o.getString("id"), o.getString("group_id"), o.getString("station"), instant(o.getString("meet_at")), str(o, "note"), str(o, "created_by"))
        private fun request(o: JSONObject) = JoinRequest(o.getString("id"), o.getString("group_id"), o.getString("user_id"), o.getString("display_name"),
            o.getString("kind"), str(o, "reclaim_user_id"), o.getString("status"))
        private fun invite(o: JSONObject): Invite {
            // the server's rule (server/CONTRACT.md, "תוקף הזמנה"): expires_at, or the day after the trip, or 90 days
            val until = str(o, "expires_at")?.let(::instant)
                ?: o.optJSONObject("groups")?.let { date(it, "ends_on") }?.plusDays(1)?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()
                ?: str(o, "created_at")?.let { instant(it).plus(java.time.Duration.ofDays(90)) }
            return Invite(o.getString("id"), o.getString("code"), o.getString("token"), o.optBoolean("requires_approval"),
                if (o.isNull("max_uses")) null else o.optInt("max_uses"), str(o, "expires_at"), o.optInt("uses", 0), until)
        }

        // the tables answer "2027-01-10T08:30:00+00:00"
        internal fun instant(s: String): Instant = OffsetDateTime.parse(s).toInstant()

        internal fun date(o: JSONObject, k: String): LocalDate? = str(o, k)?.takeIf { it.length >= 10 }?.let { LocalDate.parse(it.take(10)) }
        /**
         * "abc def" or "ABC-DEF" -> "ABCDEF"; a whole link -> its token (the last part of the path, or "t=" in it);
         * a token is kept as it is.
         */
        fun normalizeCode(code: String): String {
            val letters = code.filter { it.isLetter() }
            if (letters.length == 6 && code.all { it.isLetter() || it == ' ' || it == '-' }) return letters.uppercase()
            val t = code.trim()
            if (!t.contains('/')) return t
            Regex("[?&#]t=([A-Za-z0-9_-]+)").find(t)?.let { return it.groupValues[1] }
            return t.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        }
    }
}

/**
 * "Your trip" as a row of the server's trips table, and back: so a person can put their flight in a group (Q7, "I'm
 * on the same flight"). The server keeps airport codes only (three capitals): a place typed without a code is left
 * out there and stays on the phone.
 */
object TripRow {
    fun of(t: Trip): JSONObject = JSONObject().apply {
        put("destination", t.out.toCity.take(60).ifBlank { JSONObject.NULL })
        leg("out", t.out)
        t.ret?.let { leg("ret", it) }
        put("ski_from", t.ski?.start?.toString() ?: JSONObject.NULL)
        put("ski_to", t.ski?.endInclusive?.toString() ?: JSONObject.NULL)
    }

    private fun JSONObject.leg(p: String, l: Leg) {
        put("${p}_date", l.date.toString())
        put("${p}_flight", l.flight.trim().take(12).ifBlank { JSONObject.NULL })
        put("${p}_from", l.fromCode ?: JSONObject.NULL)
        put("${p}_to", l.toCode ?: JSONObject.NULL)
        put("${p}_departs", l.departs?.let(::hhmm) ?: JSONObject.NULL)
        put("${p}_arrives", l.arrives?.let(::hhmm) ?: JSONObject.NULL)
    }

    /** A row from the server (another member's flight), or null if it has no outbound date. */
    fun trip(o: JSONObject): Trip? {
        val out = leg(o, "out") ?: return null
        val from = Groups.date(o, "ski_from")
        val to = Groups.date(o, "ski_to")
        return Trip(out, leg(o, "ret"), if (from != null && to != null && from <= to) from..to else null)
    }

    private fun leg(o: JSONObject, p: String): Leg? {
        val date = Groups.date(o, "${p}_date") ?: return null
        return Leg(date, s(o, "${p}_flight"), s(o, "${p}_from"), s(o, "${p}_to"), time(o, "${p}_departs"), time(o, "${p}_arrives"))
    }

    private fun s(o: JSONObject, k: String) = if (o.isNull(k)) "" else o.optString(k)
    private fun time(o: JSONObject, k: String): LocalTime? = s(o, k).takeIf { it.length >= 5 }?.let { LocalTime.parse(it.take(5)) }
    private fun hhmm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
}

package io.github.pini236.skiapp.group

import io.github.pini236.skiapp.server.Account
import io.github.pini236.skiapp.server.Groups
import io.github.pini236.skiapp.server.Offline
import io.github.pini236.skiapp.server.Server
import io.github.pini236.skiapp.server.ServerError
import io.github.pini236.skiapp.server.SessionStore
import io.github.pini236.skiapp.server.Sync
import io.github.pini236.skiapp.server.TripRow
import io.github.pini236.skiapp.trip.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** My trip on the server, now, and its id there (server/TripSync.kt keeps it one row). */
interface MyTripOnServer {
    fun sendNow(trip: Trip): String
    /** The server's id of my trip, if it has one yet. */
    val tripId: String?
    /** The server wrote my trip row itself ("I'm on the same flight"): from now on that row is my trip. */
    fun adopt(id: String, trip: Trip)
}

/**
 * The screens' [GroupApi] over the real server (the client in server/, from the server session, 13.5). Every call
 * runs off the main thread; a refusal becomes [ApiException] with the server's code, no answer at all is "offline".
 *
 * [ready] waits for Google sign-in (the web client id): without it nobody can create a group, so the sign would lead
 * to a way in that goes nowhere. My name and Google are kept on the phone ([saved], [keep]) so [me] needs no network.
 *
 * The group page reads through [sync] (server/Sync.kt: the copy on the phone, Realtime, and the queue of writes), so
 * it opens at once and keeps working with no signal. My groups and the high scores fall back to that copy offline.
 */
class LiveGroupApi(
    private val server: Server,
    private val store: SessionStore,
    private val myTrip: MyTripOnServer,
    private val lang: () -> String,
    override val ready: Boolean,
    saved: String? = null,
    private val keep: (String?) -> Unit = {},
    private val sync: Sync? = null,
) : GroupApi {
    private val groups = Groups(server)
    private val account = Account(server)
    private var profile: JSONObject = saved?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: ServerError) {
            throw ApiException(if (e.code == "no_session") "not_signed_in" else e.code)
        } catch (e: Offline) {
            throw ApiException("offline")
        }
    }

    private fun remember(name: String?, google: Boolean?) {
        if (name != null) profile.put("name", name)
        if (google != null) profile.put("google", google)
        keep(profile.toString())
    }

    private fun forget() { profile = JSONObject(); keep(null) }

    override fun me(): Me? {
        val s = store.load() ?: return null
        return Me(s.userId, registered = !s.anonymous, name = profile.optString("name").ifBlank { null }, google = profile.optBoolean("google"))
    }

    override suspend fun signInGuest(): Me = io { server.auth.currentOrGuest() }.let { me()!! }

    override suspend fun signInWithGoogle(idToken: String, nonce: String): Me {
        io {
            try {
                server.auth.signInWithIdToken("google", idToken, nonce)
            } catch (e: ServerError) {
                // that Google account already has an identity: bring the guest's groups and trips over to it
                if (e.code != "identity_already_exists") throw e
                val ticket = account.mergeTicket()
                server.auth.signInWithIdToken("google", idToken, nonce, link = false)
                account.mergeGuest(ticket)
            }
            remember(account.me()?.name, true)
        }
        return me()!!
    }

    override suspend fun signOut() { io { server.auth.forget() }; forget(); sync?.forget() }
    override suspend fun deleteAccount() { io { account.delete() }; forget(); sync?.forget() }
    override suspend fun rename(name: String) { io { account.setProfile(name, lang()) }; remember(name.trim(), null) }

    override suspend fun myGroups(): List<GroupSummary> = io {
        if (store.load() == null) return@io emptyList()
        val list = try {
            groups.mine().also { sync?.refreshMine() }
        } catch (e: Offline) {
            // no signal: the groups as the phone last saw them
            sync?.myGroups?.value?.groups?.takeIf { it.isNotEmpty() } ?: throw e
        }
        list.map { g -> GroupSummary(g.id, g.name, g.startsOn, g.endsOn) }
    }

    override fun watch(id: String): GroupWatch {
        val kept = sync?.group(id) ?: return FetchedGroup(this, id)
        return object : GroupWatch {
            override val state: Flow<Shown> = kept.state.map(::shown)
            override val now: Shown get() = shown(kept.state.value)
            override fun open() = kept.open()
            override fun close() = kept.close()
            override fun refresh() = kept.refresh()
        }
    }

    /** The phone's copy of a group as the page shows it. */
    internal fun shown(s: Sync.Snapshot): Shown {
        val me = store.load()?.userId
        val head = s.group
        val names = s.members.associate { it.userId to it.name }
        val g = if (head == null || s.members.isEmpty()) null else Group(
            head.id, head.name, head.startsOn, head.endsOn,
            s.members.map { m ->
                Member(m.userId, m.name, if (m.admin) Role.ADMIN else Role.MEMBER, m.userId == me, m.tripId?.let { s.flights[it] }, m.tripId,
                    m.tripId?.let { s.enteredBy[it] }?.let { by -> names[by] ?: "" })
            },
            s.invite?.let(::working),
            s.requests.map { JoinRequest(it.id, it.userId, it.name, it.kind == "reclaim") },
            s.meetups.map { o -> Meetup(o.id, o.station, o.at, o.note, o.createdBy?.let { names[it] }) },
        )
        return Shown(g, s.readAt, s.offline, s.gone, s.waiting > 0)
    }

    override suspend fun group(id: String): Group = io {
        val me = store.load()?.userId ?: throw ServerError("no_session", 401)
        val head = rows(server.select("groups", "select=id,name,starts_on,ends_on&id=eq.${Server.enc(id)}")).firstOrNull() ?: throw ServerError("not_found", 404)
        val members = groups.members(id)
        val names = members.associate { it.userId to it.name }
        val trips = tripRows(members.mapNotNull { it.tripId })
        val admin = members.any { it.userId == me && it.admin }
        Group(
            id, head.getString("name"), Groups.date(head, "starts_on"), Groups.date(head, "ends_on"),
            members.map { m ->
                val t = m.tripId?.let { trips[it] }
                Member(m.userId, m.name, if (m.admin) Role.ADMIN else Role.MEMBER, m.userId == me, t?.trip, m.tripId,
                    t?.enteredBy?.let { by -> names[by] ?: "" })
            },
            runCatching { groups.invite(id) }.getOrNull()?.let(::working),
            if (admin) groups.requests(id).map { JoinRequest(it.id, it.userId, it.name, it.kind == "reclaim") } else emptyList(),
            rows(server.select("meetups", "select=id,station,meet_at,note,created_by&group_id=eq.${Server.enc(id)}&order=meet_at")).map { o ->
                Meetup(o.getString("id"), o.getString("station"), Groups.instant(o.getString("meet_at")), str(o, "note"), str(o, "created_by")?.let { names[it] })
            },
        )
    }

    /** The invite to show and share: none once it has expired (a used-up one still shows: the row's use count is not read yet). */
    private fun working(i: Groups.Invite): Invite? =
        Invite(i.id, i.code, i.token, i.requiresApproval, i.expiresAt?.let(Groups::instant)).takeIf { it.expiresAt?.isAfter(java.time.Instant.now()) != false }

    private class TripShown(val trip: Trip, val enteredBy: String?)

    /** The trips by id; "entered by" only when an admin filled it in and the member has not taken it over. */
    private fun tripRows(ids: List<String>): Map<String, TripShown> {
        if (ids.isEmpty()) return emptyMap()
        return rows(server.select("trips", "select=*&id=in.(${ids.joinToString(",") { Server.enc(it) }})")).mapNotNull { o ->
            val t = TripRow.trip(o) ?: return@mapNotNull null
            val by = str(o, "entered_by")?.takeIf { it != str(o, "owner_id") }
            o.getString("id") to TripShown(t, by)
        }.toMap()
    }

    override suspend fun createGroup(name: String, myName: String, startsOn: LocalDate?, endsOn: LocalDate?, myTrip: Trip?): String = io {
        val tripId = myTrip?.let(this.myTrip::sendNow)
        groups.create(name, myName, startsOn, endsOn, tripId).also { if (profile.optString("name").isBlank()) remember(myName.trim(), null) }
    }

    override suspend fun updateGroup(id: String, name: String, startsOn: LocalDate?, endsOn: LocalDate?) = io { groups.update(id, name, startsOn, endsOn) }
    override suspend fun deleteGroup(id: String) { io { groups.delete(id); sync?.refreshMine() } }

    override suspend fun newInvite(groupId: String, requiresApproval: Boolean): Invite = io {
        groups.newInvite(groupId, requiresApproval).let { Invite(it.id, it.code, it.token, it.requiresApproval, it.expiresAt?.let(Groups::instant)) }
    }

    override suspend fun preview(code: String): Preview = io {
        val p = groups.preview(code)
        val g = p.group ?: return@io Preview(JoinStatus.of(p.status))
        Preview(JoinStatus.OK, g.id, g.name, g.startsOn, g.endsOn, p.requiresApproval, p.alreadyMember, p.members.map { it.userId to it.name })
    }

    override suspend fun join(code: String, myName: String): JoinResult = io {
        groups.join(code, myName).let { JoinResult(JoinStatus.of(it.status), it.groupId) }.also { if (profile.optString("name").isBlank()) remember(myName.trim(), null) }
    }

    override suspend fun reclaim(code: String, memberId: String): JoinResult = io { groups.reclaim(code, memberId).let { JoinResult(JoinStatus.of(it.status), it.groupId) } }
    override suspend fun decide(requestId: String, approve: Boolean) = io { groups.decide(requestId, approve) }
    override suspend fun leave(groupId: String) { io { groups.leave(groupId); sync?.refreshMine() } }
    override suspend fun removeMember(groupId: String, userId: String) = io { groups.remove(groupId, userId) }
    override suspend fun setRole(groupId: String, userId: String, role: Role) = io { groups.setRole(groupId, userId, role.name.lowercase()) }
    override suspend fun claimAdmin(groupId: String) = io { groups.claimAdmin(groupId) }
    override suspend fun revokeInvite(inviteId: String) = io { groups.revokeInvite(inviteId) }
    override suspend fun cancelRequest(groupId: String) = io { groups.myRequests().filter { it.groupId == groupId }.forEach { groups.cancelRequest(it.id) } }

    override suspend fun showMyTrip(groupId: String, myName: String, trip: Trip?) = io { groups.setMine(groupId, myName, trip?.let(myTrip::sendNow)) }

    /**
     * "I'm on the same flight": that flight becomes my trip (on the phone, by the caller, and as my one row on the
     * server), shown in this group under my name there. The server overwrites the row the phone keeps (decision 27:
     * one trip a person), so the phone adopts the id it answers with, and its next save changes that row.
     */
    override suspend fun sameFlight(groupId: String, tripId: String): Trip = io {
        val t = tripRows(listOf(tripId))[tripId]?.trip ?: throw ServerError("trip_not_in_group", 404)
        myTrip.adopt(groups.sameFlight(groupId, tripId, myTrip.tripId), t)
        t
    }

    override suspend fun setMemberTrip(groupId: String, userId: String, trip: Trip) { io { groups.setMemberTrip(groupId, userId, trip) } }

    /** Mine, not one an admin filled in for me (entered_by empty or me), the newest first; the phone adopts its row. */
    override suspend fun myTripOnServer(): Trip? = io {
        val me = store.load()?.userId ?: return@io null
        val o = rows(server.select("trips", "select=*&owner_id=eq.${Server.enc(me)}&or=(entered_by.is.null,entered_by.eq.${Server.enc(me)})&order=updated_at.desc&limit=1"))
            .firstOrNull() ?: return@io null
        TripRow.trip(o)?.also { myTrip.adopt(o.getString("id"), it) }
    }

    override suspend fun leaderboard(groupId: String, game: String): List<Score> = io {
        val me = store.load()?.userId
        val rows = try {
            groups.leaderboard(groupId, game)
        } catch (e: Offline) {
            // no signal: the table the phone keeps for this game (with my own best that waits to be sent), if any
            sync?.group(groupId)?.state?.value?.scores?.get(game) ?: throw e
        }
        rows.map { Score(it.userId, it.name, it.best, it.userId == me) }
    }

    /** Through the queue on the phone ([Sync]): shown in the group at once, sent when there is signal (no signal is fine). */
    override suspend fun addMeetup(groupId: String, station: String, at: Instant) {
        io { val q = sync; if (q != null) q.group(groupId).addMeetup(station, at) else groups.addMeetup(groupId, station, at) }
    }

    /** Through the queue on the phone too: gone from the group at once, deleted on the server when there is signal. */
    override suspend fun removeMeetup(groupId: String, meetupId: String) {
        io { val q = sync; if (q != null) q.group(groupId).removeMeetup(meetupId) else groups.removeMeetup(meetupId) }
    }

    /** From the groups kept on the phone (no network: a reminder must be set on the mountain too). */
    override suspend fun allMeetups(): List<Pair<GroupSummary, Meetup>> {
        val q = sync ?: return super.allMeetups()
        return withContext(Dispatchers.IO) {
            q.myGroups.value.groups.flatMap { g ->
                q.group(g.id).state.value.meetups.map { m -> GroupSummary(g.id, g.name, g.startsOn, g.endsOn) to Meetup(m.id, m.station, m.at, m.note, null) }
            }
        }
    }

    private companion object {
        fun rows(a: JSONArray): List<JSONObject> = (0 until a.length()).map { a.getJSONObject(it) }
        fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
    }
}

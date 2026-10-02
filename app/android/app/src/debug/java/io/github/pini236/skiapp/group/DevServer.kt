package io.github.pini236.skiapp.group

import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * A pretend server for debug builds only (the emulator run and development), until the real client from server/ is
 * wired in. It follows the server's rules where the screens can see them (guests cannot create groups, admins only
 * for admin actions, the last admin cannot step down, join statuses). Made-up people and flights: never the crew's
 * (decision 27). Not in release builds (src/release has an empty one).
 */
object DevServer {
    fun create(): GroupApi? = FakeGroupApi()

    /**
     * The emulator run's starting points: none (nobody, no group), invited (a group with its invite, and nobody on this
     * phone yet), member (a guest in the group, without a flight), admin (a registered admin, with a request).
     */
    fun seed(api: GroupApi, kind: String) { (api as? FakeGroupApi)?.seed(kind) }
}

private class FakeGroupApi : GroupApi {
    override val ready = true
    private var meNow: Me? = null
    private val groups = LinkedHashMap<String, Group>()
    private val trips = HashMap<String, Trip>()

    private fun id() = UUID.randomUUID().toString()
    private suspend fun wait() = delay(250)
    private fun meOrThrow() = meNow ?: throw ApiException("not_signed_in")

    private val flight = Trip(
        Leg(LocalDate.of(2027, 1, 10), "GD 101", "TLV · תל אביב", "TBS · טביליסי", LocalTime.of(16, 0), LocalTime.of(20, 35)),
        Leg(LocalDate.of(2027, 1, 15), "GD 102", "TBS · טביליסי", "TLV · תל אביב", LocalTime.of(1, 35), LocalTime.of(2, 15)),
    )

    fun seed(kind: String) {
        groups.clear(); trips.clear(); meNow = null
        if (kind == "none") return
        val admin = kind == "admin"
        val me = Me("me", registered = admin, name = if (admin) "נועה כהן" else "איתי לוי", google = admin)
        meNow = me
        val t1 = id(); trips[t1] = flight
        val t2 = id(); trips[t2] = flight
        val names = listOf("נועה כהן", "איתי לוי", "דנה מזרחי", "עומר פרץ", "תמר אברהם", "יואב שחר")
        val members = names.mapIndexed { i, n ->
            val isMe = n == me.name
            Member(if (isMe) "me" else "u$i", n, if (i == 0 || i == 2) Role.ADMIN else Role.MEMBER, isMe,
                trip = if (i < 4 && !(isMe && !admin)) flight else null, tripId = if (i < 4 && !(isMe && !admin)) (if (i % 2 == 0) t1 else t2) else null,
                enteredByAdmin = if (i == 3) "נועה כהן" else null, registered = i == 0 || i == 2)
        }
        val gid = "g1"
        groups[gid] = Group(gid, "גודאורי 2027", LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15), members,
            Invite("i1", "KZBQRM", "t".repeat(40), false, null),
            if (admin) listOf(JoinRequest("r1", "u9", "יואב שחר", reclaim = true)) else emptyList(),
            listOf(
                Meetup("m1", "158744075b", LocalDate.of(2027, 1, 11).atTime(9, 30).toInstant(ZoneOffset.ofHours(4)), null, "נועה כהן"),
                Meetup("m2", "158744075t", LocalDate.of(2027, 1, 11).atTime(13, 0).toInstant(ZoneOffset.ofHours(4)), null, "דנה מזרחי"),
            ))
        if (kind == "invited") { groups[gid] = groups.getValue(gid).let { g -> g.copy(members = g.members.filter { it.userId != "me" }) }; meNow = null }
    }

    override fun me() = meNow

    override suspend fun signInGuest(): Me { wait(); return meNow ?: Me(id(), registered = false, name = null).also { meNow = it } }
    override suspend fun signInWithGoogle(idToken: String, nonce: String): Me {
        wait(); val m = (meNow ?: Me(id(), false, null)).copy(registered = true, google = true); meNow = m; return m
    }
    override suspend fun signOut() { meNow = null }
    override suspend fun deleteAccount() { wait(); val me = meOrThrow(); groups.keys.toList().forEach { g -> drop(g, me.userId) }; meNow = null }

    override suspend fun rename(name: String) { wait(); meNow = meOrThrow().copy(name = name.trim()) }

    override suspend fun myGroups(): List<GroupSummary> {
        wait(); val me = meNow ?: return emptyList()
        return groups.values.filter { g -> g.members.any { it.userId == me.userId } }
            .map { GroupSummary(it.id, it.name, it.startsOn, it.endsOn) }
    }

    override suspend fun group(id: String): Group {
        wait(); val me = meOrThrow()
        val g = groups[id] ?: throw ApiException("not_found")
        val mine = g.members.firstOrNull { it.userId == me.userId } ?: throw ApiException("not_member")
        return g.copy(members = g.members.map { it.copy(me = it.userId == me.userId) }, requests = if (mine.role == Role.ADMIN) g.requests else emptyList())
    }

    override suspend fun createGroup(name: String, myName: String, startsOn: LocalDate?, endsOn: LocalDate?, myTrip: Trip?): String {
        wait(); val me = meOrThrow()
        if (!me.registered) throw ApiException("must_register")
        val gid = id(); val tid = myTrip?.let { id().also { t -> trips[t] = it } }
        groups[gid] = Group(gid, name.trim(), startsOn, endsOn,
            listOf(Member(me.userId, myName.trim(), Role.ADMIN, true, myTrip, tid, null, true)),
            Invite(id(), randomCode(), id() + id(), false, null), emptyList(), emptyList())
        meNow = me.copy(name = myName.trim())
        return gid
    }

    private fun randomCode() = (1..6).map { InviteCode.LETTERS.random() }.joinToString("")
    private fun admin(gid: String): Group {
        val me = meOrThrow(); val g = groups[gid] ?: throw ApiException("not_found")
        if (g.members.none { it.userId == me.userId && it.role == Role.ADMIN }) throw ApiException("not_admin")
        return g
    }

    override suspend fun updateGroup(id: String, name: String, startsOn: LocalDate?, endsOn: LocalDate?) {
        wait(); groups[id] = admin(id).copy(name = name.trim(), startsOn = startsOn, endsOn = endsOn)
    }
    override suspend fun deleteGroup(id: String) { wait(); admin(id); groups.remove(id) }
    override suspend fun newInvite(groupId: String, requiresApproval: Boolean): Invite {
        wait(); val g = admin(groupId)
        val inv = Invite(id(), randomCode(), id() + id(), requiresApproval, null)
        groups[groupId] = g.copy(invite = inv); return inv
    }

    private fun byCode(code: String) = groups.values.firstOrNull { it.invite?.code == code.uppercase() || it.invite?.token == code }

    override suspend fun preview(code: String): Preview {
        wait(); val me = meOrThrow()
        val g = byCode(code) ?: return Preview(JoinStatus.INVALID_CODE)
        val member = g.members.any { it.userId == me.userId }
        val approval = g.invite?.requiresApproval == true
        // like the server: an invite that needs approval shows no names to who is not in the group
        return Preview(JoinStatus.OK, g.id, g.name, g.startsOn, g.endsOn, approval, member,
            if (approval && !member) emptyList() else g.members.map { it.userId to it.name })
    }

    override suspend fun join(code: String, myName: String): JoinResult {
        wait(); val me = meOrThrow()
        val g = byCode(code) ?: return JoinResult(JoinStatus.INVALID_CODE)
        if (g.members.any { it.userId == me.userId }) return JoinResult(JoinStatus.ALREADY_MEMBER, g.id)
        if (g.invite?.requiresApproval == true) {
            groups[g.id] = g.copy(requests = g.requests + JoinRequest(id(), me.userId, myName.trim(), false)); return JoinResult(JoinStatus.PENDING, g.id)
        }
        groups[g.id] = g.copy(members = g.members + Member(me.userId, myName.trim(), Role.MEMBER, true, registered = me.registered))
        meNow = me.copy(name = me.name ?: myName.trim())
        return JoinResult(JoinStatus.JOINED, g.id)
    }

    override suspend fun reclaim(code: String, memberId: String): JoinResult {
        wait(); val me = meOrThrow()
        val g = byCode(code) ?: return JoinResult(JoinStatus.INVALID_CODE)
        val m = g.members.firstOrNull { it.userId == memberId } ?: return JoinResult(JoinStatus.NO_SUCH_MEMBER)
        if (m.registered) return JoinResult(JoinStatus.SIGN_IN_INSTEAD)
        groups[g.id] = g.copy(requests = g.requests + JoinRequest(id(), me.userId, m.name, true))
        return JoinResult(JoinStatus.PENDING, g.id)
    }

    override suspend fun decide(requestId: String, approve: Boolean) {
        wait()
        val g = groups.values.firstOrNull { it.requests.any { r -> r.id == requestId } } ?: throw ApiException("request_closed")
        admin(g.id)
        val r = g.requests.first { it.id == requestId }
        var members = g.members
        if (approve && !r.reclaim) members = members + Member(r.userId, r.name, Role.MEMBER, false)
        groups[g.id] = g.copy(members = members, requests = g.requests - r)
    }

    private fun drop(gid: String, user: String) {
        val g = groups[gid] ?: return
        var rest = g.members.filter { it.userId != user }
        if (rest.isEmpty()) { groups.remove(gid); return }
        // the last admin leaves: the oldest registered member takes over (the server's default 1)
        if (rest.none { it.role == Role.ADMIN }) rest.indexOfFirst { it.registered }.takeIf { it >= 0 }?.let { i -> rest = rest.toMutableList().also { it[i] = it[i].copy(role = Role.ADMIN) } }
        groups[gid] = g.copy(members = rest)
    }

    override suspend fun leave(groupId: String) { wait(); drop(groupId, meOrThrow().userId) }
    override suspend fun removeMember(groupId: String, userId: String) { wait(); admin(groupId); drop(groupId, userId) }

    override suspend fun setRole(groupId: String, userId: String, role: Role) {
        wait(); val g = admin(groupId)
        val m = g.members.firstOrNull { it.userId == userId } ?: throw ApiException("not_member")
        if (role == Role.ADMIN && !m.registered) throw ApiException("admin_must_register")
        if (role == Role.MEMBER && g.members.count { it.role == Role.ADMIN && it.userId != userId } == 0) throw ApiException("last_admin")
        groups[groupId] = g.copy(members = g.members.map { if (it.userId == userId) it.copy(role = role) else it })
    }

    override suspend fun showMyTrip(groupId: String, myName: String, trip: Trip?) {
        wait(); val me = meOrThrow(); val g = groups[groupId] ?: throw ApiException("not_found")
        val tid = trip?.let { id().also { t -> trips[t] = it } }
        groups[groupId] = g.copy(members = g.members.map { if (it.userId == me.userId) it.copy(name = myName.trim(), trip = trip, tripId = tid, enteredByAdmin = null) else it })
    }

    override suspend fun sameFlight(groupId: String, tripId: String): Trip {
        wait(); val me = meOrThrow(); val g = groups[groupId] ?: throw ApiException("not_found")
        val t = trips[tripId] ?: throw ApiException("trip_not_in_group")
        val mine = id(); trips[mine] = t
        groups[groupId] = g.copy(members = g.members.map { if (it.userId == me.userId) it.copy(trip = t, tripId = mine, enteredByAdmin = null) else it })
        return t
    }

    override suspend fun setMemberTrip(groupId: String, userId: String, trip: Trip) {
        wait(); val g = admin(groupId); val me = meOrThrow()
        val by = g.members.first { it.userId == me.userId }.name
        val tid = id(); trips[tid] = trip
        groups[groupId] = g.copy(members = g.members.map { if (it.userId == userId) it.copy(trip = trip, tripId = tid, enteredByAdmin = by) else it })
    }

    override suspend fun leaderboard(groupId: String, game: String): List<Score> {
        wait(); val me = meOrThrow(); val g = groups[groupId] ?: throw ApiException("not_found")
        val best = listOf(12480, 11920, 9310, 8775, 6040)
        return g.members.take(5).mapIndexed { i, m -> Score(m.userId, m.name, best[i], m.userId == me.userId) }
    }
}

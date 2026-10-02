package io.github.pini236.skiapp.group

import io.github.pini236.skiapp.trip.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/*
 * Accounts and groups (docs/USERS.md, round 10: A1 to A5, Q1 to Q10). The screens talk to the server only through
 * [GroupApi]: [LiveGroupApi] over the client in server/ (the server session, stage 13.5), or the pretend server of
 * debug builds. Names and shapes follow server/README.md and server/supabase/functions/api/actions.ts.
 */

enum class Role { ADMIN, MEMBER }

/** Who this phone is on the server: nobody yet (null), a guest (an anonymous identity), or a registered account. */
data class Me(val userId: String, val registered: Boolean, val name: String?, val google: Boolean = false, val apple: Boolean = false)

data class GroupSummary(val id: String, val name: String, val startsOn: LocalDate?, val endsOn: LocalDate?)

data class Member(
    val userId: String,
    val name: String,
    val role: Role,
    val me: Boolean,
    /** The trip this member shows in the group, and its id there (for "I'm on the same flight"). */
    val trip: Trip? = null,
    val tripId: String? = null,
    /** An admin filled it in for them, and they have not changed it (docs/USERS.md, "the flight"). */
    val enteredByAdmin: String? = null,
    val registered: Boolean = false,
)

data class Invite(val id: String, val code: String, val token: String, val requiresApproval: Boolean, val expiresAt: Instant?)

data class JoinRequest(val id: String, val userId: String, val name: String, val reclaim: Boolean)

data class Meetup(val id: String, val station: String, val at: Instant, val note: String?, val byName: String?)

data class Group(
    val id: String,
    val name: String,
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val members: List<Member>,
    /** The working invite (every member shares it). */
    val invite: Invite?,
    /** Pending requests: only admins see them. */
    val requests: List<JoinRequest>,
    val meetups: List<Meetup>,
) {
    val me: Member? get() = members.firstOrNull { it.me }
    val admin: Boolean get() = me?.role == Role.ADMIN
    val admins: Int get() = members.count { it.role == Role.ADMIN }
}

/**
 * A group as its page shows it, from the phone first: [group] is null until it was read once, [readAt] (ms) is when,
 * [offline] says the last read had no signal, [gone] that I am no longer in it, [waiting] that changes made here wait
 * for signal.
 */
data class Shown(val group: Group?, val readAt: Long? = null, val offline: Boolean = false, val gone: Boolean = false, val waiting: Boolean = false)

/**
 * One group kept on the phone (server/Sync.kt): shows at once, also with no signal; reads again on [open] and on each
 * change until [close]. After a change made on the page (approving, roles, the same flight), [refresh].
 */
interface GroupWatch {
    val state: Flow<Shown>
    val now: Shown
    fun open()
    fun close()
    fun refresh()
}

/** A watch that just reads [GroupApi.group] (the pretend server; nothing kept on the phone). */
class FetchedGroup(private val api: GroupApi, private val id: String) : GroupWatch {
    private val flow = MutableStateFlow(Shown(null))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override val state: Flow<Shown> = flow
    override val now: Shown get() = flow.value
    override fun open() = refresh()
    override fun close() = scope.coroutineContext.cancelChildren()
    override fun refresh() {
        scope.launch {
            flow.value = try {
                Shown(api.group(id), System.currentTimeMillis())
            } catch (e: ApiException) {
                when (e.code) {
                    "offline" -> flow.value.copy(offline = true)
                    "not_member", "not_found" -> Shown(null, System.currentTimeMillis(), gone = true)
                    else -> flow.value
                }
            }
        }
    }
}

/** What an invite opens (invite_preview): the group, and its members' names for "I'm already in the group". */
data class Preview(
    val status: JoinStatus,
    val groupId: String? = null,
    val name: String = "",
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
    val requiresApproval: Boolean = false,
    val alreadyMember: Boolean = false,
    val members: List<Pair<String, String>> = emptyList(),
)

/** The join actions answer with a status, not an error (server/README.md). */
enum class JoinStatus { OK, JOINED, PENDING, ALREADY_MEMBER, INVALID_CODE, RATE_LIMITED, GROUP_FULL, NO_SUCH_MEMBER, SIGN_IN_INSTEAD;
    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: INVALID_CODE
    }
}

data class JoinResult(val status: JoinStatus, val groupId: String? = null)

data class Score(val userId: String, val name: String, val best: Int, val me: Boolean)

/** A refusal from the server, with its short code (not_admin, last_admin, admin_must_register...), or "offline". */
class ApiException(val code: String) : Exception(code)

interface GroupApi {
    /** A server is behind this. Without one, the group sign says the groups are on their way. */
    val ready: Boolean

    /** The identity this phone has now, from the saved session; no network. */
    fun me(): Me?

    suspend fun signInGuest(): Me
    /** Google's ID token from the system's sheet (Credential Manager), and the raw nonce it was asked with. */
    suspend fun signInWithGoogle(idToken: String, nonce: String): Me
    suspend fun signOut()
    suspend fun deleteAccount()
    /** The name on my profile (the groups each keep the name I joined with). */
    suspend fun rename(name: String)

    suspend fun myGroups(): List<GroupSummary>
    suspend fun group(id: String): Group
    /** The group as kept on the phone, for its page; the screens read the group only through this. */
    fun watch(id: String): GroupWatch = FetchedGroup(this, id)
    suspend fun createGroup(name: String, myName: String, startsOn: LocalDate?, endsOn: LocalDate?, myTrip: Trip?): String
    suspend fun updateGroup(id: String, name: String, startsOn: LocalDate?, endsOn: LocalDate?)
    suspend fun deleteGroup(id: String)
    /** A new invite, replacing the working one (revoke and replace). */
    suspend fun newInvite(groupId: String, requiresApproval: Boolean): Invite

    suspend fun preview(code: String): Preview
    suspend fun join(code: String, myName: String): JoinResult
    suspend fun reclaim(code: String, memberId: String): JoinResult
    suspend fun decide(requestId: String, approve: Boolean)
    suspend fun leave(groupId: String)
    suspend fun removeMember(groupId: String, userId: String)
    suspend fun setRole(groupId: String, userId: String, role: Role)

    /** My trip in this group (null: none shown). The client creates or updates my trip on the server. */
    suspend fun showMyTrip(groupId: String, myName: String, trip: Trip?)
    /** "I'm on the same flight": a copy of that trip becomes mine, in this group; returned to keep on the phone too. */
    suspend fun sameFlight(groupId: String, tripId: String): Trip
    /** An admin fills in a member's flight while the member has not. */
    suspend fun setMemberTrip(groupId: String, userId: String, trip: Trip)

    suspend fun leaderboard(groupId: String, game: String): List<Score>

    /** A meetup from the meeting point (Q8): kept on the phone at once and sent when there is signal. */
    suspend fun addMeetup(groupId: String, station: String, at: Instant)

    /** Every meetup of my groups, with its group: what the reminders are set from (Q8). */
    suspend fun allMeetups(): List<Pair<GroupSummary, Meetup>> = myGroups().flatMap { g -> group(g.id).meetups.map { g to it } }
}

/** Invite codes: six letters without I and O (the server's alphabet), typed in any case; or the long token from a link. */
object InviteCode {
    const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    fun clean(s: String): String = s.uppercase().filter { it in LETTERS }.take(6)
    fun isCode(s: String) = s.length == 6 && s.all { it in LETTERS }
    /**
     * The link the invite card shares (site route /j/..., docs/USERS.md: it opens the app, or the site). It carries the
     * long token, not the six letters: a token cannot be guessed, so the server does not count it against the guesses.
     */
    fun link(token: String) = "https://gudauri-ski-trip.vercel.app/j/$token"
}

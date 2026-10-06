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

/** My groups in the site's order (its account list, K-4): by start date, the undated last, ties as they came. */
fun List<GroupSummary>.byStart(): List<GroupSummary> = sortedWith(compareBy(nullsLast()) { it.startsOn })

/** A row of "my groups" in the account (K-4, as on the site): how many are in it (null: not known here) and whether I run it. */
data class MyGroup(val id: String, val name: String, val members: Int?, val admin: Boolean)

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
data class Shown(val group: Group?, val readAt: Long? = null, val offline: Boolean = false, val gone: Boolean = false, val waiting: Boolean = false,
                 /** A change made here that waited for signal, and the server refused: its code, until [GroupWatch.clearRefused]. */
                 val refused: String? = null)

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
    fun clearRefused() {}
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
    /**
     * The group sign on home (the site's groupBoardSub): my first group's name and how many are in it, from the phone's
     * copy when there is one; null when I am in none (or offline with nothing kept).
     */
    suspend fun firstGroup(): Pair<String, Int>? {
        if (me() == null) return null
        val g = runCatching { myGroups() }.getOrNull()?.firstOrNull() ?: return null
        val n = watch(g.id).now.group?.members?.size ?: runCatching { group(g.id).members.size }.getOrNull() ?: 0
        return g.name to n
    }
    /** "My groups" (K-4), each one's size and my role from the phone's copy when there is one. */
    suspend fun myGroupRows(): List<MyGroup> {
        if (me() == null) return emptyList()
        return myGroups().map { g ->
            val full = watch(g.id).now.group ?: runCatching { group(g.id) }.getOrNull()
            MyGroup(g.id, g.name, full?.members?.size, full?.admin == true)
        }
    }
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
    /** "Be the admin" (claim_admin): only while the group has no admin, and only for a registered member. */
    suspend fun claimAdmin(groupId: String)
    /** The admin cancels the working invite: its code and link stop working until a new one is made. */
    suspend fun revokeInvite(inviteId: String)
    /** My request to join this group, still waiting for an admin, is withdrawn (cancel_join_request). */
    suspend fun cancelRequest(groupId: String)
    /** The groups my requests still wait on (an admin has not decided): shown, and can be withdrawn, after a restart too. */
    suspend fun pendingRequests(): List<String> = emptyList()
    /**
     * Watch my groups while a screen waits on them (the group sign, a request waiting for an admin), live (Realtime
     * "me:"): [onChange] after each change, with the groups an admin has just let me into. Returns how to stop.
     */
    fun watchMine(onChange: (approved: List<String>) -> Unit): () -> Unit = {}

    /** My trip in this group (null: none shown). The client creates or updates my trip on the server. */
    suspend fun showMyTrip(groupId: String, myName: String, trip: Trip?)
    /** "I'm on the same flight": a copy of that trip becomes mine, in this group; returned to keep on the phone too. */
    suspend fun sameFlight(groupId: String, tripId: String): Trip
    /**
     * A phone with no trip of its own, just signed in (a new phone, or the app installed again): my newest trip on the
     * server, as the site's pullTrip, which this phone then keeps as its own row; null when there is none.
     */
    suspend fun myTripOnServer(): Trip? = null
    /** An admin fills in a member's flight while the member has not. */
    suspend fun setMemberTrip(groupId: String, userId: String, trip: Trip)

    suspend fun leaderboard(groupId: String, game: String): List<Score>
    /**
     * A game's high score on this phone goes to the groups' tables (submit_score; the server keeps only the best, and
     * the table is one for all my groups), as the site's sendBests: through the queue, so it waits for signal. False when
     * I am in no group, and then nothing is sent.
     */
    suspend fun submitBest(game: String, score: Int): Boolean = false

    /** A meetup from the meeting point (Q8): kept on the phone at once and sent when there is signal. */
    suspend fun addMeetup(groupId: String, station: String, at: Instant)
    /** A meetup goes for everyone (any member may, as on the site): gone from the phone at once, sent when there is signal. */
    suspend fun removeMeetup(groupId: String, meetupId: String)

    /** Every meetup of my groups, with its group: what the reminders are set from (Q8). */
    suspend fun allMeetups(): List<Pair<GroupSummary, Meetup>> = myGroups().flatMap { g -> group(g.id).meetups.map { g to it } }
}

/** Invite codes: six letters without I and O (the server's alphabet), typed in any case; or the long token from a link. */
object InviteCode {
    const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    fun clean(s: String): String = s.uppercase().filter { it in LETTERS }.take(6)
    fun isCode(s: String) = s.length == 6 && s.all { it in LETTERS }
    /** The long token of an invite link (/j/<token>): it cannot be guessed, so it is not typed. */
    fun isToken(s: String) = TOKEN.matches(s)
    private val TOKEN = Regex("[A-Za-z0-9_-]{32,64}")

    /**
     * A pasted invite link, as the site makes them: /j/<token> (the token) or /join/<code> (the six letters); null for
     * anything else, which is then typed as letters.
     */
    fun fromLink(s: String): String? {
        Regex("/j/([A-Za-z0-9_-]{32,64})(?![A-Za-z0-9_-])").find(s)?.let { return it.groupValues[1] }
        Regex("/join/([A-Za-z-]{6,9})(?![A-Za-z])").find(s)?.let { m -> return clean(m.groupValues[1]).takeIf(::isCode) }
        return null
    }

    /** How the invite came (group_join's `via`), as the site tells: the long token is a link, six letters were typed. */
    fun via(code: String) = if (code.trim().length > 8) "link" else "code"
    /**
     * The link the invite card shares (site route /j/..., docs/USERS.md: it opens the app, or the site). It carries the
     * long token, not the six letters: a token cannot be guessed, so the server does not count it against the guesses.
     */
    fun link(token: String) = "https://gudauri-ski-trip.vercel.app/j/$token"
    /** The link as it is shared (copied, sent): it says it came from the app ([io.github.pini236.skiapp.nav.Route.SHARED]). */
    fun shared(token: String) = link(token) + "?" + io.github.pini236.skiapp.nav.Route.SHARED
}

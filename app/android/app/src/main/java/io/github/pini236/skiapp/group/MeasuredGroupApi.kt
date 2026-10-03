package io.github.pini236.skiapp.group

import android.content.SharedPreferences
import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.Trip
import java.time.Instant
import java.time.LocalDate

/**
 * The account and group events of the measuring contract (docs/GROWTH.md, "after stage 13.5"), sent once an action
 * went through, the same with the real server and the pretend one. No ids, no member counts.
 */
class MeasuredGroupApi(
    private val api: GroupApi,
    /** How each request still waiting for an admin came (code or link), for its group_join once approved; kept on the phone. */
    private val kept: SharedPreferences? = null,
) : GroupApi by api {
    private val mem = HashMap<String, String>()
    private fun keepVia(group: String, via: String) { kept?.edit()?.putString(group, via)?.apply() ?: mem.put(group, via) }
    private fun takeVia(group: String): String? = kept?.getString(group, null)?.also { kept.edit().remove(group).apply() } ?: mem.remove(group)

    override suspend fun signInGuest(): Me = api.signInGuest().also { Telemetry.event("sign_in", mapOf("method" to "guest")) }

    override suspend fun signInWithGoogle(idToken: String, nonce: String): Me =
        api.signInWithGoogle(idToken, nonce).also { Telemetry.event("sign_in", mapOf("method" to "google")) }

    override suspend fun signOut() { api.signOut(); Telemetry.event("sign_out") }
    override suspend fun deleteAccount() { api.deleteAccount(); Telemetry.event("account_delete") }

    override suspend fun createGroup(name: String, myName: String, startsOn: LocalDate?, endsOn: LocalDate?, myTrip: Trip?): String =
        api.createGroup(name, myName, startsOn, endsOn, myTrip).also { Telemetry.event("group_create") }

    /**
     * Joined only, or a request an admin let in ([watchMine]): not already_member, not a request still waiting (the
     * contract's D4). The six letters were typed, a token came in a link ([InviteCode.via], as on the site).
     */
    override suspend fun join(code: String, myName: String): JoinResult = api.join(code, myName).also {
        when (it.status) {
            JoinStatus.JOINED -> Telemetry.event("group_join", mapOf("via" to InviteCode.via(code)))
            JoinStatus.PENDING -> it.groupId?.let { g -> keepVia(g, InviteCode.via(code)) }
            else -> {}
        }
    }

    override suspend fun reclaim(code: String, memberId: String): JoinResult = api.reclaim(code, memberId).also {
        if (it.status == JoinStatus.PENDING) it.groupId?.let { g -> keepVia(g, InviteCode.via(code)) }
    }

    override fun watchMine(onChange: (approved: List<String>) -> Unit): () -> Unit = api.watchMine { approved ->
        approved.forEach { g -> Telemetry.event("group_join", mapOf("via" to (takeVia(g) ?: "code"))) }
        onChange(approved)
    }

    override suspend fun cancelRequest(groupId: String) { api.cancelRequest(groupId); takeVia(groupId) }

    override suspend fun leave(groupId: String) { api.leave(groupId); Telemetry.event("group_leave") }

    /** reminder: a new meetup reminds a quarter of an hour before unless turned off (Q8, meet/Reminders.kt) */
    override suspend fun addMeetup(groupId: String, station: String, at: Instant) {
        api.addMeetup(groupId, station, at); Telemetry.event("meetup_create", mapOf("reminder" to true))
    }
}

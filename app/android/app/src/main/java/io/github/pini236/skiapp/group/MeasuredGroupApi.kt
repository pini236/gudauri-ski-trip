package io.github.pini236.skiapp.group

import io.github.pini236.skiapp.telemetry.Telemetry
import io.github.pini236.skiapp.trip.Trip
import java.time.LocalDate

/**
 * The account and group events of the measuring contract (docs/GROWTH.md, "after stage 13.5"), sent once an action
 * went through, the same with the real server and the pretend one. No ids, no member counts.
 */
class MeasuredGroupApi(private val api: GroupApi) : GroupApi by api {
    override suspend fun signInGuest(): Me = api.signInGuest().also { Telemetry.event("sign_in", mapOf("method" to "guest")) }

    override suspend fun signInWithGoogle(idToken: String, nonce: String): Me =
        api.signInWithGoogle(idToken, nonce).also { Telemetry.event("sign_in", mapOf("method" to "google")) }

    override suspend fun signOut() { api.signOut(); Telemetry.event("sign_out") }
    override suspend fun deleteAccount() { api.deleteAccount(); Telemetry.event("account_delete") }

    override suspend fun createGroup(name: String, myName: String, startsOn: LocalDate?, endsOn: LocalDate?, myTrip: Trip?): String =
        api.createGroup(name, myName, startsOn, endsOn, myTrip).also { Telemetry.event("group_create") }

    /** Joined only (a request that waits for an admin is not yet a join); the six letters were typed, a token came in a link. */
    override suspend fun join(code: String, myName: String): JoinResult = api.join(code, myName).also {
        if (it.status == JoinStatus.JOINED) Telemetry.event("group_join", mapOf("via" to if (InviteCode.isCode(InviteCode.clean(code))) "code" else "link"))
    }

    override suspend fun leave(groupId: String) { api.leave(groupId); Telemetry.event("group_leave") }
}

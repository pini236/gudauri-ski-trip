package io.github.pini236.skiapp.server

import android.content.Context
import io.github.pini236.skiapp.trip.Trip
import java.util.concurrent.Executors

/**
 * "Your trip" on the server too, so a group can show it (Q7). Called by the trip's owner ([trip.TripStore], in
 * trip/) after every save, with null after "delete the trip". Never blocks the caller.
 */
fun interface TripSync {
    fun pushed(trip: Trip?)
}

/**
 * Only for a phone that already has a session (it joined or made a group): a guest who never needed the server
 * stays off it. Without signal the latest trip waits and goes with the next [flush] (on launch, or when the group
 * page opens). Keeps the server's id of my trip, so it is one row that changes, not a new row each time.
 */
class ServerTripSync(context: Context, private val server: Server) : TripSync, io.github.pini236.skiapp.group.MyTripOnServer {
    private val prefs = context.getSharedPreferences("server_trip", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()

    /** The server's id of my trip, to show it in a group ([Groups.setMine], [Groups.create]). */
    val tripId: String? get() = prefs.getString("id", null)

    override fun pushed(trip: Trip?) {
        prefs.edit().putString("pending", trip?.toJson()?.toString() ?: DELETED).apply()
        worker.execute { runCatching { send() } }
    }

    fun flush() = worker.execute { runCatching { send() } }

    /** Now, on the caller's (background) thread: my trip on the server, and its id, to show it in a group. */
    override fun sendNow(trip: Trip): String {
        prefs.edit().putString("pending", trip.toJson().toString()).apply()
        send()
        return tripId ?: throw ServerError("no_session", 401)
    }

    @Synchronized
    private fun send() {
        val pending = prefs.getString("pending", null) ?: return
        if (server.auth.current() == null) return
        val id = tripId
        if (pending == DELETED) {
            if (id != null) server.delete("trips", id)
            prefs.edit().remove("id").remove("pending").apply()
            return
        }
        val row = TripRow.of(Trip.fromJson(pending) ?: run { prefs.edit().remove("pending").apply(); return })
        val saved = id?.let { server.update("trips", it, row) } ?: server.insert("trips", row)
        // only what was sent: a newer save while this one travelled stays pending
        prefs.edit().putString("id", saved.getString("id")).apply()
        if (prefs.getString("pending", null) == pending) prefs.edit().remove("pending").apply()
    }

    private companion object {
        const val DELETED = "deleted"
    }
}

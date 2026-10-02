package io.github.pini236.skiapp.server

import org.json.JSONObject

/** My account on the server (A4): who I am there, and deleting it with everything that is mine. */
class Account(private val server: Server) {

    data class Me(val id: String, val name: String?, val lang: String, val anonymous: Boolean)

    /** Null when this phone never needed the server. */
    fun me(): Me? {
        if (server.auth.current() == null) return null
        val o = server.call("my_account")
        return Me(o.getString("id"), o.optString("display_name").takeUnless { it.isBlank() || it == "null" }, o.optString("lang", "he"), o.optBoolean("is_anonymous"))
    }

    /** My name and language on the server (direct writes the row rules allow: only these two, only mine). */
    fun setProfile(name: String?, lang: String) {
        val me = server.auth.current()?.userId ?: return
        server.update("profiles", me, JSONObject().put("display_name", name?.trim()?.ifBlank { null } ?: JSONObject.NULL).put("lang", lang))
    }

    /**
     * A guest whose Google or Apple account already exists (see [Auth.signInWithIdToken]): step 1, as the guest, a
     * one-time ticket (15 minutes); step 2, after signing in to that account, [mergeGuest] brings the guest's groups
     * and trips over.
     */
    fun mergeTicket(): String = server.call("create_merge_ticket").getString("ticket")

    fun mergeGuest(ticket: String) {
        server.call("merge_guest", JSONObject().put("ticket", ticket))
    }

    /** Required by both stores: the account, its trips and memberships go; groups I ran get a new admin. */
    fun delete() {
        if (server.auth.current() == null) return
        server.call("delete_my_account")
        server.auth.forget()
    }
}

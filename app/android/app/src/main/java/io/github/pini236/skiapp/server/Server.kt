package io.github.pini236.skiapp.server

import android.content.Context
import io.github.pini236.skiapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * The server (decision 40, server/README.md): Supabase in the EU. Three doors, all plain HTTPS with JSON:
 * - Auth (`/auth/v1`): the session ([Auth]).
 * - The tables (`/rest/v1`): reads, filtered by the row rules; direct writes only to trips, meetups and the
 *   profile's name and language.
 * - The server's code (`/functions/v1/api/<action>`): every other write, one action per request ([call]).
 *
 * Nothing here runs by itself: every request comes from something the person did. The URL and key are public by
 * design (the protection is the row rules and the server's checks); a build may point elsewhere with the
 * SUPABASE_URL and SUPABASE_KEY environment variables.
 */
class Server(
    val url: String,
    val key: String,
    store: SessionStore,
    private val transport: Transport = UrlTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val auth = Auth(this, store)

    fun nowSeconds() = clock() / 1000

    // The function runs in the EU only when asked: about 0.55 s instead of 1.6 s from the US (server/README.md).
    private fun headers(token: String?) = buildMap {
        put("apikey", key)
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        put("x-region", REGION)
        if (token != null) put("Authorization", "Bearer $token")
    }

    internal fun auth(path: String, body: JSONObject, token: String? = null): JSONObject {
        val r = transport.send(Request("POST", "$url/auth/v1/$path", headers(token), body.toString()))
        if (!r.ok) throw ServerError(authCode(r), r.status)
        return obj(r)
    }

    /**
     * One action of the server's code. The answer's JSON, or [ServerError] with its code ("not_admin",
     * "invalid_input"...). The join actions answer {"status": ...} instead of an error. Only those make a guest
     * identity when there is no session yet (docs/USERS.md: a guest touches the server only to join); any other
     * action without a session is [ServerError] "no_session".
     */
    fun call(action: String, body: JSONObject = JSONObject()): JSONObject = send(action, body).let { if (it.body.isBlank()) JSONObject() else obj(it) }

    /** An action whose answer is a list (group_leaderboard). */
    fun callList(action: String, body: JSONObject = JSONObject()): JSONArray = list(send(action, body))

    private fun send(action: String, body: JSONObject): Response {
        require(action.matches(Regex("^[a-z_]+$"))) { action }
        val r = authorized(guest = action in GUEST_ACTIONS) { token -> Request("POST", "$url/functions/v1/api/$action", headers(token), body.toString()) }
        if (!r.ok) throw ServerError(code(r), r.status)
        return r
    }

    /** Rows the row rules let me see: [query] is PostgREST's ("select=id,name&group_id=eq.<id>&order=joined_at"). */
    fun select(table: String, query: String): JSONArray {
        val r = authorized { token -> Request("GET", "$url/rest/v1/$table?$query", headers(token)) }
        if (!r.ok) throw ServerError(code(r), r.status)
        return list(r)
    }

    /** A new row, returned as the server stored it. */
    fun insert(table: String, row: JSONObject): JSONObject = post(table, row, "return=representation")
        ?: throw ServerError("empty_answer", 200)

    /**
     * Like [insert], for a row that carries an id the phone chose: sending it again (a write that was sent but whose
     * answer never came back) changes nothing. The answer is then null, and the caller reads the row it already made.
     */
    fun insertOnce(table: String, row: JSONObject): JSONObject? = post(table, row, "return=representation,resolution=ignore-duplicates")

    private fun post(table: String, row: JSONObject, prefer: String): JSONObject? {
        val r = authorized { token -> Request("POST", "$url/rest/v1/$table", headers(token) + ("Prefer" to prefer), row.toString()) }
        if (!r.ok) throw ServerError(code(r), r.status)
        return list(r).optJSONObject(0)
    }

    /** Change the row with this id; null when the rules hide it (gone, or not mine). */
    fun update(table: String, id: String, row: JSONObject): JSONObject? {
        val r = authorized { token ->
            Request("PATCH", "$url/rest/v1/$table?id=eq.${enc(id)}", headers(token) + ("Prefer" to "return=representation"), row.toString())
        }
        if (!r.ok) throw ServerError(code(r), r.status)
        return list(r).optJSONObject(0)
    }

    fun delete(table: String, id: String) {
        val r = authorized { token -> Request("DELETE", "$url/rest/v1/$table?id=eq.${enc(id)}", headers(token)) }
        if (!r.ok) throw ServerError(code(r), r.status)
    }

    // With the session's token; a token the server refuses (revoked, or the clock was wrong) is renewed once. A
    // session the server has dropped is never silently replaced by a new guest: that would quietly leave every
    // group; the app offers "I'm already in the group" instead (Q6).
    private fun authorized(guest: Boolean = false, make: (String) -> Request): Response {
        val s = (if (guest) auth.currentOrGuest() else auth.current()) ?: throw ServerError("no_session", 401)
        val r = transport.send(make(s.accessToken))
        if (r.status != 401) return r
        val fresh = auth.refresh() ?: throw ServerError("no_session", 401)
        return transport.send(make(fresh.accessToken))
    }

    companion object {
        const val REGION = "eu-central-1"

        /** A success whose body is not the JSON it should be (a proxy's page, a cut answer): [BAD_RESPONSE], never a crash. */
        const val BAD_RESPONSE = "bad_response"

        internal fun obj(r: Response): JSONObject = try { JSONObject(r.body) } catch (_: org.json.JSONException) { throw ServerError(BAD_RESPONSE, r.status) }
        internal fun list(r: Response): JSONArray = try { JSONArray(r.body) } catch (_: org.json.JSONException) { throw ServerError(BAD_RESPONSE, r.status) }

        /** The actions a guest may start with, before the phone has any session. */
        val GUEST_ACTIONS = setOf("invite_preview", "join_group", "request_reclaim")

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        @Volatile private var instance: Server? = null

        /** The app's one server client, with the session kept on this phone (one, so the session is renewed once). */
        fun of(context: Context): Server = instance ?: synchronized(this) {
            instance ?: Server(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, PrefsSessionStore(context.applicationContext)).also { instance = it }
        }

        // {"error": "not_admin"} from the server's code. From the tables {"code": ..., "message": ...}: a guard's
        // refusal (P0001) carries its own short code in the message ("too_many_trips"); bad values and the row
        // rules get the same codes as the server's code uses (server/CONTRACT.md, "שגיאות").
        internal fun code(r: Response): String = runCatching {
            val o = JSONObject(r.body)
            o.optString("error").ifBlank {
                when (val c = o.optString("code")) {
                    "P0001" -> o.optString("message").takeIf { it.matches(Regex("^[a-z_]+$")) } ?: c
                    "23514", "22P02", "22007", "22008", "23502" -> "invalid_input"
                    "42501" -> "not_allowed"
                    "23503" -> "conflict"
                    else -> c
                }
            }
        }.getOrNull()?.ifBlank { null } ?: "http_${r.status}"

        // Auth answers {"error_code": "..."} (or {"error": "invalid_grant"} on older servers).
        private fun authCode(r: Response): String = runCatching {
            val o = JSONObject(r.body)
            o.optString("error_code").ifBlank { o.optString("error") }
        }.getOrNull()?.ifBlank { null } ?: "http_${r.status}"
    }
}

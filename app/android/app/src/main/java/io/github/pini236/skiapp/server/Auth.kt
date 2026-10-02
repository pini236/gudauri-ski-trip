package io.github.pini236.skiapp.server

import android.content.Context
import org.json.JSONObject

/**
 * Who this phone is to the server (docs/USERS.md): a guest is an anonymous identity, made only when a guest first
 * needs the server (joining a group); Google and Apple come later (A1 to A5), on the same identity.
 */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Seconds since 1970, from the server. */
    val expiresAt: Long,
    val userId: String,
    val anonymous: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().put("access", accessToken).put("refresh", refreshToken)
        .put("expires", expiresAt).put("user", userId).put("anon", anonymous)

    companion object {
        fun fromJson(s: String?): Session? = runCatching {
            val o = JSONObject(s ?: return null)
            Session(o.getString("access"), o.getString("refresh"), o.getLong("expires"), o.getString("user"), o.getBoolean("anon"))
        }.getOrNull()

        /** Supabase Auth's answer: access_token, refresh_token, expires_at (or expires_in), user {id, is_anonymous}. */
        internal fun fromAuth(o: JSONObject, nowSeconds: Long): Session {
            val user = o.getJSONObject("user")
            val expires = o.optLong("expires_at", 0).takeIf { it > 0 } ?: (nowSeconds + o.optLong("expires_in", 3600))
            return Session(o.getString("access_token"), o.getString("refresh_token"), expires, user.getString("id"), user.optBoolean("is_anonymous", false))
        }
    }
}

/** Where the session is kept between launches. */
interface SessionStore {
    fun load(): Session?
    fun save(s: Session?)
}

/** Only on this phone, in the app's private storage (backup is off in the manifest, so it is never copied off it). */
class PrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)
    override fun load() = Session.fromJson(prefs.getString("session", null))
    override fun save(s: Session?) {
        prefs.edit().apply { if (s == null) remove("session") else putString("session", s.toJson().toString()) }.apply()
    }
}

class MemorySessionStore(private var s: Session? = null) : SessionStore {
    override fun load() = s
    override fun save(s: Session?) { this.s = s }
}

/** A refusal from the server: a short code (docs: server/README.md, "הפעולות") that the app turns into its own words. */
class ServerError(val code: String, val status: Int) : Exception("$status $code")

/**
 * Supabase Auth over plain HTTP. Tokens last an hour; [current] renews one that is about to end, so callers never
 * see an expired token. Every method may throw [Offline]; a session the server no longer knows is dropped.
 */
class Auth internal constructor(private val server: Server, private val store: SessionStore) {

    /** The session, renewed if it ends within a minute; null when this phone has never needed the server. */
    @Synchronized
    fun current(): Session? {
        val s = store.load() ?: return null
        return if (s.expiresAt - server.nowSeconds() > 60) s else refresh()
    }

    /** The session, made anonymously if there is none yet: only from an action the person took (joining a group). */
    @Synchronized
    fun currentOrGuest(): Session = current() ?: signInAnonymously()

    @Synchronized
    fun signInAnonymously(): Session = keep(server.auth("signup", JSONObject().put("data", JSONObject())))

    /**
     * Sign in with Google (Android: Credential Manager's ID token, whose audience is the web client id set in
     * Supabase) or Apple. A guest is linked instead, so the same identity keeps its groups and trips (docs/USERS.md);
     * when that Google account already has one, the server answers "identity_already_exists" and the app runs the
     * merge: [Account.mergeTicket] as the guest, sign in with [link] false, then [Account.mergeGuest].
     */
    @Synchronized
    fun signInWithIdToken(provider: String, idToken: String, nonce: String? = null, accessToken: String? = null, link: Boolean = true): Session {
        val body = JSONObject().put("provider", provider).put("id_token", idToken)
        if (nonce != null) body.put("nonce", nonce)
        if (accessToken != null) body.put("access_token", accessToken)
        val guest = current()?.takeIf { it.anonymous && link }
        if (guest != null) body.put("link_identity", true)
        return keep(server.auth("token?grant_type=id_token", body, guest?.accessToken))
    }

    /** A new token from the refresh token. Null (and forgotten) when the server says the session is over. */
    @Synchronized
    fun refresh(): Session? {
        val s = store.load() ?: return null
        return try {
            keep(server.auth("token?grant_type=refresh_token", JSONObject().put("refresh_token", s.refreshToken)))
        } catch (e: ServerError) {
            if (e.status in 400..499) { store.save(null); null } else throw e
        }
    }

    /** Forget the session on this phone (after deleting the account, or signing out). */
    @Synchronized
    fun forget() = store.save(null)

    private fun keep(o: JSONObject): Session = Session.fromAuth(o, server.nowSeconds()).also { store.save(it) }
}

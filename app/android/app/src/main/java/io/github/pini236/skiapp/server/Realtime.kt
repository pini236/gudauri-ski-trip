package io.github.pini236.skiapp.server

import okhttp3.OkHttpClient
import okhttp3.Request as HttpRequest
import okhttp3.Response as HttpResponse
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Supabase Realtime (server/CONTRACT.md, "זמן אמת"): one WebSocket for the app, one channel per thing the screens
 * watch. A change only says "something in this table changed": the store then reads again through the row rules,
 * so nothing reaches the phone that the person may not read, whatever the message carried. While there is no
 * signal it retries with growing pauses (up to a minute) and, when back, tells every channel so it reads again
 * (changes made meanwhile were missed).
 */
class Realtime(
    private val server: Server,
    private val sockets: SocketFactory = OkHttpSockets,
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "realtime").apply { isDaemon = true } },
) {
    /** What one channel listens to: a table, and optionally a filter ("group_id=eq.<id>"). */
    data class Watch(val table: String, val filter: String? = null, val event: String = "*")

    /** A subscription. [onChange] gets the table name; [onReconnect] runs after a gap (read everything again). */
    class Channel internal constructor(
        val topic: String,
        val watches: List<Watch>,
        val onChange: (String) -> Unit,
        val onReconnect: () -> Unit,
    ) {
        @Volatile internal var joinRef: String? = null
    }

    fun interface SocketFactory {
        fun open(url: String, listener: Listener): Socket
    }

    interface Socket {
        fun send(text: String): Boolean
        fun close()
    }

    interface Listener {
        fun onOpen()
        fun onMessage(text: String)
        fun onClosed()
    }

    private val channels = LinkedHashMap<String, Channel>()
    private var socket: Socket? = null
    private var open = false
    private var ref = 0
    private var attempts = 0
    private var heartbeat: ScheduledFuture<*>? = null
    private var retry: ScheduledFuture<*>? = null
    private var hadGap = false
    private var lastToken: String? = null
    // Which socket is the current one. A late "closed" or message from a socket we already gave up must not reset the new one.
    private var generation = 0

    val connected: Boolean @Synchronized get() = open

    /** Listen until [leave]. Topics are names of our choosing ("group:<id>"); one channel per topic. */
    @Synchronized
    fun join(topic: String, watches: List<Watch>, onChange: (String) -> Unit, onReconnect: () -> Unit = {}): Channel {
        channels[topic]?.let { leave(it) }
        val c = Channel(topic, watches, onChange, onReconnect)
        channels[topic] = c
        if (open) sendJoin(c) else connect()
        return c
    }

    @Synchronized
    fun leave(c: Channel) {
        if (channels[c.topic] !== c) return
        channels.remove(c.topic)
        if (open && c.joinRef != null) send(c.topic, "phx_leave", JSONObject())
        if (channels.isEmpty()) disconnect()
    }

    /** A renewed session token must reach the channels, or the server stops sending after the old one ends. */
    @Synchronized
    fun tokenChanged() {
        val token = server.auth.current()?.accessToken ?: return
        if (!open || token == lastToken) return
        lastToken = token
        channels.values.filter { it.joinRef != null }.forEach { send(it.topic, "access_token", JSONObject().put("access_token", token)) }
    }

    @Synchronized
    private fun connect() {
        if (socket != null || channels.isEmpty()) return
        val url = server.url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${Server.enc(server.key)}&vsn=1.0.0"
        val mine = ++generation
        socket = sockets.open(url, object : Listener {
            override fun onOpen() = opened(mine)
            override fun onMessage(text: String) = received(mine, text)
            override fun onClosed() = closed(mine)
        })
    }

    @Synchronized
    private fun opened(gen: Int) {
        if (gen != generation) return
        open = true
        attempts = 0
        heartbeat?.cancel(false)
        heartbeat = timer.scheduleWithFixedDelay({ synchronized(this) { if (open) { send("phoenix", "heartbeat", JSONObject()); tokenChangedLocked() } } }, 25, 25, TimeUnit.SECONDS)
        channels.values.forEach { sendJoin(it) }
        if (hadGap) {
            hadGap = false
            val all = channels.values.toList()
            timer.execute { all.forEach { runCatching { it.onReconnect() } } }
        }
    }

    private fun tokenChangedLocked() = runCatching { tokenChanged() }

    private fun received(gen: Int, text: String) {
        if (synchronized(this) { gen != generation }) return
        debug?.invoke(text)
        val m = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (m.optString("event") != "postgres_changes") return
        val c = synchronized(this) { channels[m.optString("topic").removePrefix("realtime:")] } ?: return
        val table = m.optJSONObject("payload")?.optJSONObject("data")?.optString("table") ?: return
        runCatching { c.onChange(table) }
    }

    @Synchronized
    private fun closed(gen: Int) {
        if (gen != generation) return
        socket = null
        open = false
        heartbeat?.cancel(false)
        channels.values.forEach { it.joinRef = null }
        if (channels.isEmpty()) return
        hadGap = true
        val wait = minOf(60L, 1L shl minOf(attempts++, 6))
        retry?.cancel(false)
        retry = timer.schedule({ connect() }, wait, TimeUnit.SECONDS)
    }

    @Synchronized
    private fun disconnect() {
        retry?.cancel(false)
        heartbeat?.cancel(false)
        val s = socket
        generation++ // whatever this socket still says is old news
        socket = null
        open = false
        hadGap = false
        s?.close()
    }

    // The session may be null for a moment (renewing); the server then treats the channel as public, and the row
    // rules let nothing through. The next token change fixes it.
    private fun sendJoin(c: Channel) {
        val token = runCatching { server.auth.current()?.accessToken }.getOrNull()
        lastToken = token
        val changes = JSONArray()
        c.watches.forEach { w ->
            changes.put(JSONObject().put("event", w.event).put("schema", "public").put("table", w.table).apply { w.filter?.let { put("filter", it) } })
        }
        val config = JSONObject()
            .put("broadcast", JSONObject().put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("postgres_changes", changes)
            .put("private", false)
        val payload = JSONObject().put("config", config).apply { token?.let { put("access_token", it) } }
        c.joinRef = send(c.topic, "phx_join", payload, join = true)
    }

    private fun send(topic: String, event: String, payload: JSONObject, join: Boolean = false): String {
        val r = (++ref).toString()
        val full = if (topic == "phoenix") topic else "realtime:$topic"
        val msg = JSONObject().put("topic", full).put("event", event).put("payload", payload).put("ref", r)
        if (join) msg.put("join_ref", r)
        socket?.send(msg.toString())
        return r
    }

    companion object {
        /** Tests only: every message from the server. */
        @Volatile internal var debug: ((String) -> Unit)? = null
    }

    /** The real WebSocket. One client for the app; it pings so a dead connection is noticed. */
    object OkHttpSockets : SocketFactory {
        private val client by lazy { OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build() }

        override fun open(url: String, listener: Listener): Socket {
            val ws = client.newWebSocket(HttpRequest.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: HttpResponse) = listener.onOpen()
                override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed()
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: HttpResponse?) = listener.onClosed()
            })
            return object : Socket {
                override fun send(text: String) = ws.send(text)
                override fun close() { ws.close(1000, null) }
            }
        }
    }
}

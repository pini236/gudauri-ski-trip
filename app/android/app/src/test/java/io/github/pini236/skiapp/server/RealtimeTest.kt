package io.github.pini236.skiapp.server

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/** The Realtime protocol against a fake WebSocket. */
class RealtimeTest {
    private val now = 1_800_000_000_000L
    private val store = MemorySessionStore(Session("tok", "r", now / 1000 + 3600, "u1", false))
    private val server = Server("https://x.supabase.co", "pk", store, { Response(500, "") }, { now })

    private val sent = mutableListOf<JSONObject>()
    private var listener: Realtime.Listener? = null
    private var url = ""
    private var closes = 0
    private val sockets = Realtime.SocketFactory { u, l ->
        url = u; listener = l
        object : Realtime.Socket {
            override fun send(text: String): Boolean { sent += JSONObject(text); return true }
            override fun close() { closes++ }
        }
    }
    private val timer = Executors.newSingleThreadScheduledExecutor()

    @Test fun joinsWithTheTokenAndPassesChangesOn() {
        val rt = Realtime(server, sockets, timer)
        val changes = mutableListOf<String>()
        rt.join("group:g1", listOf(Realtime.Watch("meetups", "group_id=eq.g1"), Realtime.Watch("trips")), { changes += it })
        assertEquals("wss://x.supabase.co/realtime/v1/websocket?apikey=pk&vsn=1.0.0", url)
        listener!!.onOpen()

        val join = sent.single()
        assertEquals("realtime:group:g1", join.getString("topic"))
        assertEquals("phx_join", join.getString("event"))
        val p = join.getJSONObject("payload")
        assertEquals("tok", p.getString("access_token"))
        val pc = p.getJSONObject("config").getJSONArray("postgres_changes")
        assertEquals("group_id=eq.g1", pc.getJSONObject(0).getString("filter"))
        assertEquals("trips", pc.getJSONObject(1).getString("table"))

        listener!!.onMessage("""{"topic":"realtime:group:g1","event":"postgres_changes","payload":{"data":{"table":"meetups","type":"INSERT"}}}""")
        listener!!.onMessage("""{"topic":"realtime:other","event":"postgres_changes","payload":{"data":{"table":"trips"}}}""")
        listener!!.onMessage("""{"topic":"phoenix","event":"phx_reply","payload":{"status":"ok"}}""")
        assertEquals(listOf("meetups"), changes)
    }

    @Test fun aGapReconnectsAndReadsAgain() {
        val rt = Realtime(server, sockets, timer)
        var reads = 0
        rt.join("group:g1", listOf(Realtime.Watch("meetups")), {}, { reads++ })
        listener!!.onOpen()
        val first = listener
        first!!.onClosed()
        // it retries after a second
        val deadline = System.currentTimeMillis() + 5000
        while (listener === first && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(listener !== first)
        sent.clear()
        listener!!.onOpen()
        Thread.sleep(200)
        assertEquals("phx_join", sent.single().getString("event"))
        assertEquals(1, reads)
    }

    @Test fun leavingTheLastChannelCloses() {
        val rt = Realtime(server, sockets, timer)
        val c = rt.join("me:u1", listOf(Realtime.Watch("groups")), {})
        listener!!.onOpen()
        rt.leave(c)
        assertEquals("phx_leave", sent.last().getString("event"))
        assertEquals(1, closes)
    }

    @Test fun anOldSocketClosingLateDoesNotResetTheNewOne() {
        val rt = Realtime(server, sockets, timer)
        val c = rt.join("me:u1", listOf(Realtime.Watch("groups")), {})
        listener!!.onOpen()
        val old = listener!!
        rt.leave(c) // the last channel is gone: the socket is given up
        rt.join("me:u1", listOf(Realtime.Watch("groups")), {})
        val fresh = listener!!
        assertTrue(fresh !== old)
        fresh.onOpen()
        assertTrue(rt.connected)
        old.onClosed() // the old socket's close arrives late
        assertTrue("the new connection is still up", rt.connected)
        Thread.sleep(1500)
        assertTrue("and no second connection was started", listener === fresh)
    }
}

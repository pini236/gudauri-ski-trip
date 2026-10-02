package io.github.pini236.skiapp.server

import io.github.pini236.skiapp.BuildConfig
import io.github.pini236.skiapp.trip.Leg
import io.github.pini236.skiapp.trip.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

/**
 * Against the real server, only when asked (SERVER_LIVE=1): a guest from first request to deleted account, leaving
 * nothing behind. Not in the regular run, so a build never depends on the network.
 */
class LiveServerTest {
    // the JVM's own client does PATCH; Android's does too (UrlTransport), the JVM's HttpURLConnection does not
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val transport = Transport { r ->
        val b = HttpRequest.newBuilder(URI(r.url)).timeout(Duration.ofSeconds(30))
            .method(r.method, r.body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        r.headers.forEach { (k, v) -> b.header(k, v) }
        val res = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        Response(res.statusCode(), res.body())
    }

    @Test fun aGuestFromStartToDeleted() {
        assumeTrue(System.getenv("SERVER_LIVE") == "1")
        val s = Server(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, MemorySessionStore(), transport)
        val groups = Groups(s)
        try {
            run(s, groups)
        } finally {
            runCatching { Account(s).delete() } // nothing stays on the server, even when a check fails
        }
    }

    private fun run(s: Server, groups: Groups) {

        assertEquals("invalid_code", groups.preview("ZZZZZZ").status) // makes the guest
        val me = Account(s).me()!!
        assertTrue(me.anonymous)
        assertEquals(emptyList<Groups.Group>(), groups.mine())

        // realtime: a channel on my trips hears the insert below
        val heard = java.util.concurrent.LinkedBlockingQueue<String>()
        val subscribed = java.util.concurrent.CountDownLatch(1)
        Realtime.debug = { if (it.contains("Subscribed to PostgreSQL")) subscribed.countDown() }
        val rt = Realtime(s)
        val ch = rt.join("live-test", listOf(Realtime.Watch("trips")), { heard.add(it) })
        assertTrue(subscribed.await(30, java.util.concurrent.TimeUnit.SECONDS))

        val trip = Trip(Leg(LocalDate.of(2027, 1, 10), "6H 897", "TLV · תל אביב", "TBS · טביליסי"))
        val row = s.insert("trips", TripRow.of(trip))
        assertEquals("trips", heard.poll(15, java.util.concurrent.TimeUnit.SECONDS))
        rt.leave(ch)
        Realtime.debug = null
        assertEquals("TBS", row.getString("out_to"))
        val id = row.getString("id")
        val changed = s.update("trips", id, TripRow.of(trip.copy(ret = Leg(LocalDate.of(2027, 1, 15), "6H 892"))))!!
        assertEquals("2027-01-15", changed.getString("ret_date"))
        assertEquals(trip.out.date, TripRow.trip(changed)!!.out.date)
        s.delete("trips", id)
        assertEquals(0, s.select("trips", "select=id").length())

        val groupsNow = Groups(s)
        assertEquals(40, groupsNow.submitScore("descent", 40))
        assertEquals(40, groupsNow.submitScore("descent", 10)) // only a higher score counts
        Account(s).setProfile("בדיקה", "en")
        assertEquals("en", Account(s).me()!!.lang)

        Account(s).delete()
        assertNull(s.auth.current())
    }
}

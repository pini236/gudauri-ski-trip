package io.github.pini236.skiapp.telemetry

import io.github.pini236.skiapp.group.InviteCode
import io.github.pini236.skiapp.map.runLink
import io.github.pini236.skiapp.meet.Meet
import io.github.pini236.skiapp.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime

/** app_open's source, and the links the app shares: they say they came from the app, and still open the same place. */
class OpenSourceTest {
    @Test fun aSharedLinkIsAShareLink() {
        assertEquals("share_link", OpenSource.ofQuery("utm_source=app&utm_medium=share"))
        assertEquals("link", OpenSource.ofQuery("utm_source=whatsapp-il&utm_medium=group"))
        assertEquals("link", OpenSource.ofQuery(null))
    }

    @Test fun thePlayReferrersCampaign() {
        assertEquals(mapOf("utm_source" to "google-play", "utm_medium" to "organic"), OpenSource.utm("utm_source=google-play&utm_medium=organic"))
        assertEquals(mapOf("utm_source" to "whatsapp-il", "utm_campaign" to "season 27"), OpenSource.utm("utm_source=whatsapp-il&utm_campaign=season%2027&gclid=x"))
        assertEquals(emptyMap<String, String>(), OpenSource.utm(""))
    }

    @Test fun sharedLinksCarryTheAppAndOpenTheSamePlace() {
        val run = runLink("Tatra 2")
        assertEquals("utm_source=app&utm_medium=share", URI(run).rawQuery)
        assertEquals(Route.Map("Tatra 2"), Route.fromSiteLink(run))
        val meet = Meet.shared("158744075b", LocalTime.of(9, 30), LocalDate.of(2027, 1, 11))
        assertEquals("share_link", OpenSource.ofQuery(URI(meet).rawQuery))
        assertEquals(Route.Meet("158744075b", "0930", "20270111"), Route.fromSiteLink(meet))
        val token = "abcdefghijklmnopqrstuvwxyz012345"
        assertEquals(Route.Join(token), Route.fromSiteLink(InviteCode.shared(token)))
        assertEquals(token, InviteCode.fromLink(InviteCode.shared(token)))
    }
}

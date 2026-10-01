package com.pini.gudauri.data

import org.junit.Assert.*
import org.junit.Test

class AppLinkTest {
    @Test fun `shared link selects exactly the named run`() {
        assertEquals("https://gudauri-ski-trip.vercel.app/#map/run/Tatra%202",AppLinks.run("Tatra 2"))
        assertEquals(AppLink(AppPage.MAP,"Tatra 2"),AppLinks.parse(AppLinks.run("Tatra 2")))
    }
    @Test fun `spaces unicode and literal plus signs survive encoding`() {
        for(key in listOf("Soliko 2","אדום + כחול","Kudebi 1","100%")) assertEquals(key,AppLinks.parse(AppLinks.run(key))?.runKey)
        assertEquals("A+B",AppLinks.parse(AppLinks.site+"#map/run/A+B")?.runKey)
    }
    @Test fun `home and overview links work without a run`() {
        assertEquals(AppLink(AppPage.HOME),AppLinks.parse(AppLinks.site))
        assertEquals(AppLink(AppPage.HOME),AppLinks.parse(AppLinks.site+"#home"))
        assertEquals(AppLink(AppPage.MAP),AppLinks.parse(AppLinks.site+"#map"))
    }
    @Test fun `malformed unsupported and untrusted URLs are not accepted`() {
        for(value in listOf("", "javascript:alert(1)","http://gudauri-ski-trip.vercel.app/#map",
            "https://gudauri-ski-trip.vercel.app.evil.invalid/#map","https://user@gudauri-ski-trip.vercel.app/#map",
            "https://gudauri-ski-trip.vercel.app:443/#map",AppLinks.site+"games/descent/",AppLinks.site+"#meet/missing/1230/20270111",
            AppLinks.site+"#map/run/",AppLinks.site+"#map/run/%",AppLinks.site+"#map/run/A%00B",AppLinks.site+"#map/run/A%2FB",
            AppLinks.site+"#map/run/"+"a".repeat(4100))) assertNull(value,AppLinks.parse(value))
    }
    @Test fun `meeting links round trip with real calendar and clock validation`() {
        val choice = MeetingChoice("158744055b",java.time.LocalDate.of(2027,1,11),java.time.LocalTime.of(13,0))
        assertEquals(AppLink(AppPage.MEET,meeting=choice),AppLinks.parse(AppLinks.meeting(choice)))
        assertEquals(AppLink(AppPage.MEET),AppLinks.parse(AppLinks.site+"#meet"))
        for(suffix in listOf("2400/20270111", "1260/20270111", "1230/20270229", "1230/20270431", "1230/00000111", "1230/20270111/extra")) {
            assertNull(suffix,AppLinks.parse(AppLinks.site+"#meet/158744055b/$suffix"))
        }
    }
}

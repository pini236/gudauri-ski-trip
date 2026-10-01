package io.github.pini236.skiapp.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTest {
    @Test fun pathsRoundTrip() {
        val all = listOf(Route.Home, Route.Map(), Route.Map("Tatra 2"), Route.Map("Kudebi 1/2 (old)"), Route.Meet(), Route.Meet("lift-12", "0930", "20270112"),
            Route.Games, Route.Game("descent"), Route.Ticket, Route.About)
        for (r in all) assertEquals(r.path, r, Route.parse(r.path))
    }

    @Test fun readsTheSiteHashesAndLinks() {
        assertEquals(Route.Map("Tatra 2"), Route.parse("#map/run/Tatra%202"))
        assertEquals(Route.Map("Tatra 2"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/#map/run/Tatra%202"))
        assertEquals(Route.Home, Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/"))
        assertEquals(Route.Game("descent"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/games/descent/"))
        assertEquals(Route.Meet("g1", "1300", "20270113"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/#meet/g1/1300/20270113"))
    }

    @Test fun oddInputIsIgnored() {
        for (bad in listOf(null, "#nowhere", "map/run/", "map/x/y", "meet/a/9:30/2027", "games/../../etc", "games/" + "x".repeat(60), "#map/run/%E0%A4%A")) assertNull(bad, Route.parse(bad))
        assertNull(Route.fromSiteLink("http://gudauri-ski-trip.vercel.app/#map"))
        assertNull(Route.fromSiteLink("https://evil.example/#map"))
        assertNull(Route.fromSiteLink("not a url"))
    }

    @Test fun tabsReplaceEachOtherAndBackReturnsToTheStart() {
        val n = Nav(Route.Map())
        assertFalse(n.canBack)
        n.switchTo(Route.Game("descent")); n.switchTo(Route.Ticket)
        assertEquals(Route.Ticket, n.top)
        assertTrue(n.back()); assertEquals(Route.Map(), n.top)
        assertFalse(n.back())
    }

    @Test fun theChosenRunSurvivesARestart() {
        val n = Nav(Route.Map())
        n.replaceTop(Route.Map("Sadzele 2"))
        n.push(Route.About)
        val again = Nav(Route.Map(), n.save())
        assertEquals(Route.About, again.top)
        again.back()
        assertEquals(Route.Map("Sadzele 2"), again.top)
        // a saved state that makes no sense starts clean
        assertEquals(Route.Map(), Nav(Route.Map(), arrayListOf("garbage", "about")).top)
    }
}

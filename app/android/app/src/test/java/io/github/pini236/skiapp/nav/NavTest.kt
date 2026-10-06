package io.github.pini236.skiapp.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTest {
    @Test fun pathsRoundTrip() {
        val all = listOf(Route.Home, Route.Map(), Route.Map("Tatra 2"), Route.Map("Kudebi 1/2 (old)"), Route.Meet(), Route.Meet("lift-12", "0930", "20270112"),
            Route.Games, Route.Game("descent"), Route.About, Route.Trip, Route.Group(), Route.Group("g1"), Route.Group("5f0c-77", "scores"),
            Route.GroupNew, Route.GroupInvite("g1"), Route.TripFor("g1", "u-2"), Route.Join("KZBQRM"), Route.Join("t".repeat(40)), Route.Reclaim("KZBQRM"), Route.JoinCode, Route.Account,
            Route.Licenses, Route.License("plex-hebrew"))
        for (r in all) assertEquals(r.path, r, Route.parse(r.path))
    }

    @Test fun readsTheSiteHashesAndLinks() {
        assertEquals(Route.Map("Tatra 2"), Route.parse("#map/run/Tatra%202"))
        assertEquals(Route.Map("Tatra 2"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/#map/run/Tatra%202"))
        assertEquals(Route.Home, Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/"))
        assertEquals(Route.Game("descent"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/games/descent/"))
        assertEquals(Route.Meet("g1", "1300", "20270113"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/#meet/g1/1300/20270113"))
        // an invite link (InviteCode.link) opens the invitation, never the reclaim screen
        assertEquals(Route.Join("abcdefghijklmnopqrstuvwxyz012345"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/j/abcdefghijklmnopqrstuvwxyz012345"))
        assertNull(Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/j/KZBQRM/reclaim"))
        // the site's /join/<code> and #join/<code> are the same invite (A-31)
        assertEquals(Route.Join("KZBQRM"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/join/KZBQRM"))
        assertEquals(Route.Join("KZBQRM"), Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/#join/KZBQRM"))
        assertEquals(Route.JoinCode, Route.parse("join"))
        // the pages that must stay in the browser (Google Play's account deletion, the policy) are not the app's
        assertNull(Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/account"))
        assertNull(Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/privacy"))
        assertNull(Route.fromSiteLink("https://gudauri-ski-trip.vercel.app/join/KZBQRM/x"))
    }

    @Test fun oddInputIsIgnored() {
        for (bad in listOf(null, "#nowhere", "map/run/", "map/x/y", "meet/a/9:30/2027", "games/../../etc", "games/" + "x".repeat(60), "#map/run/%E0%A4%A",
                "group/g1/settings", "group/../x", "j/ABC", "j/" + "x".repeat(90), "j/KZBQRM/x", "account/x", "join/AB", "join/KZBQRM/x", "about/x", "about/licenses/../x")) assertNull(bad, Route.parse(bad))
        assertNull(Route.fromSiteLink("http://gudauri-ski-trip.vercel.app/#map"))
        assertNull(Route.fromSiteLink("https://evil.example/#map"))
        assertNull(Route.fromSiteLink("not a url"))
    }

    @Test fun signsStackOnHomeAndBackReturnsHome() {
        val n = Nav(Route.Home)
        assertFalse(n.canBack)
        n.push(Route.Map()); n.replaceTop(Route.Map("Tatra 2"))
        assertEquals(Route.Map("Tatra 2"), n.top)
        assertTrue(n.back()); assertEquals(Route.Home, n.top)
        n.push(Route.Trip); n.push(Route.Trip) // a double tap does not stack twice
        assertTrue(n.back()); assertEquals(Route.Home, n.top)
        assertFalse(n.back())
        // a top-level switch keeps home underneath
        n.switchTo(Route.Game("descent")); n.switchTo(Route.Group())
        assertEquals(listOf(Route.Home, Route.Group()), n.routes)
    }

    @Test fun theChosenRunSurvivesARestart() {
        val n = Nav(Route.Home)
        n.push(Route.Map("Sadzele 2"))
        n.push(Route.About)
        val again = Nav(Route.Home, n.save())
        assertEquals(Route.About, again.top)
        again.back()
        assertEquals(Route.Map("Sadzele 2"), again.top)
        // a saved state that makes no sense starts clean, and so does the spike's (it started on the map)
        assertEquals(Route.Home, Nav(Route.Home, arrayListOf("garbage", "about")).top)
        assertEquals(listOf<Route>(Route.Home), Nav(Route.Home, arrayListOf("map/run/Tatra%202", "ticket")).routes)
    }
}

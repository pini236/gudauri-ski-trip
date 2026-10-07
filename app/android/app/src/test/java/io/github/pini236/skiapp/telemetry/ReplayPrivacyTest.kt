package io.github.pini236.skiapp.telemetry

import io.github.pini236.skiapp.nav.Nav
import io.github.pini236.skiapp.nav.Route
import org.junit.Assert.*
import org.junit.Test

class ReplayPrivacyTest {
    private val allowed = ReplayPrivacy.Eligibility(true, true, true, true, true)
    private val id = "00000000000000000000000000000001"

    private fun recording(): ReplayPrivacy = ReplayPrivacy().also {
        it.update(allowed)
        assertTrue(it.bind(it.ticket()!!, id))
        assertTrue(it.permit(id, "segment"))
    }

    @Test fun replayStartsWithNoConsentAndNoUploadPermission() {
        val p = ReplayPrivacy()
        assertNull(p.ticket())
        assertFalse(p.maySend("segment"))
    }
    @Test fun eachPrivacyPrerequisiteMustBePresent() {
        val denied = listOf(allowed.copy(consent = false), allowed.copy(analytics = false), allowed.copy(device = false),
            allowed.copy(foreground = false), allowed.copy(publicScreen = false))
        denied.forEach { state ->
            val p = recording()
            p.update(state)
            assertNull(p.ticket())
            assertFalse(p.maySend("segment"))
        }
    }
    @Test fun revokedConsentNeverRevivesQueuedSegmentsAfterOptIn() {
        val p = recording()
        p.update(allowed.copy(consent = false))
        p.update(allowed)
        assertTrue(p.bind(p.ticket()!!, "new-replay"))
        assertFalse(p.maySend("segment"))
        assertFalse(p.permit(id, "late-old-segment"))
        assertTrue(p.permit("new-replay", "new-segment"))
        assertTrue(p.maySend("new-segment"))
    }
    @Test fun aSensitiveScreenInvalidatesTheWholeOldReplay() {
        val p = recording()
        p.update(allowed.copy(publicScreen = false))
        p.update(allowed)
        assertTrue(p.bind(p.ticket()!!, "public-replay"))
        assertFalse(p.permit(id, "private-pixel-arrived-late"))
        assertFalse(p.maySend("segment"))
    }
    @Test fun returningFromBackgroundCannotUploadTheOldVideo() {
        val p = recording()
        p.update(allowed.copy(foreground = false))
        p.update(allowed)
        assertFalse(p.maySend("segment"))
    }
    @Test fun pendingStartCannotBindAfterItsConsentWasRevoked() {
        val p = ReplayPrivacy()
        p.update(allowed)
        val oldTicket = p.ticket()!!
        p.update(allowed.copy(consent = false))
        p.update(allowed)
        assertFalse(p.bind(oldTicket, id))
    }
    @Test fun recoveredEnvelopeIsDeniedInTheNewProcessEvenWithConsent() {
        val freshProcess = ReplayPrivacy()
        freshProcess.update(allowed)
        assertFalse(freshProcess.maySend("persisted-video"))
        assertFalse(freshProcess.permit(id, "persisted-video"))
    }
    @Test fun revocationCancelsAnActiveRequestAndStopsAnotherQueuedRequest() {
        val p = recording()
        var cancelled = false
        assertTrue(p.beginSend("segment") { cancelled = true })
        p.update(allowed.copy(consent = false))
        assertTrue(cancelled)
        assertFalse(p.beginSend("segment") { fail("stale queued upload must not start") })
    }
    @Test fun endingARequestRemovesItsCancellationHandle() {
        val p = recording()
        var cancelled = false
        val cancel = { cancelled = true }
        assertTrue(p.beginSend("segment", cancel))
        p.endSend(cancel)
        p.update(allowed.copy(consent = false))
        assertFalse(cancelled)
    }
    @Test fun identicalRecompositionDoesNotResetAValidRecording() {
        val p = recording()
        assertFalse(p.update(allowed))
        assertTrue(p.maySend("segment"))
    }
    @Test fun routeAllowlistExcludesPrivateLocationAndFutureScreens() {
        listOf("home", "about", "map", "map/run/Tatra", "meet", "trip", "account", "group", "group/id/invite", "j/code", "join",
            "games/unknown", "about/licenses", "future-screen").forEach { assertFalse(it, ReplayPrivacy.publicScreen(it)) }
        listOf("games", "games/merge", "games/fresh", "games/school", "games/snowball", "games/descent")
            .forEach { assertTrue(it, ReplayPrivacy.publicScreen(it)) }
    }
    @Test fun navigationRevokesBeforeAnyPrivateRouteIsPutOnTheStack() {
        val p = recording()
        var nav: Nav? = null
        nav = Nav(Route.Games, beforeChange = {
            assertEquals(Route.Games, nav!!.top)
            p.update(allowed.copy(publicScreen = false))
        })
        nav.push(Route.Map())
        assertEquals(Route.Map(), nav.top)
        assertFalse(p.maySend("segment"))
    }
}

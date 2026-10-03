package io.github.pini236.skiapp.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Invite codes and links: what a pasted link becomes, and group_join's `via` (as the site tells it). */
class InviteCodeTest {
    private val token = "aB3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY"

    @Test fun aPastedLinkGoesStraightIn() {
        assertEquals(token, InviteCode.fromLink("https://gudauri-ski-trip.vercel.app/j/$token"))
        assertEquals(token, InviteCode.fromLink("בואו לקבוצה: https://gudauri-ski-trip.vercel.app/j/$token\nמי שמתקין מקליד KZBQRM"))
        assertEquals("KZBQRM", InviteCode.fromLink("https://gudauri-ski-trip.vercel.app/join/kzb-qrm"))
        // typing is typing: letters, and a broken link, are not a link
        assertNull(InviteCode.fromLink("KZBQRM"))
        assertNull(InviteCode.fromLink("https://gudauri-ski-trip.vercel.app/j/short"))
        assertNull(InviteCode.fromLink("https://gudauri-ski-trip.vercel.app/join/KZBQ"))
    }

    @Test fun viaAsOnTheSite() {
        // the site: joinCode.length > 8 ? 'link' : 'code'; the token used to be cleaned into six letters and read as a code
        assertEquals("link", InviteCode.via(token))
        assertEquals("code", InviteCode.via("KZBQRM"))
        assertEquals("code", InviteCode.via("kzb-qrm"))
        assertEquals(true, InviteCode.isToken(token))
        assertEquals(false, InviteCode.isToken("KZBQRM"))
    }
}

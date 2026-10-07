package io.github.pini236.skiapp.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLifecyclePolicyTest {
    @Test fun aProcessHasOnlyOneColdOpen() {
        val process = OpenLifecyclePolicy()
        assertTrue(process.claimColdOpenOnce())
        assertFalse(process.claimColdOpenOnce())
    }

    @Test fun recreationAndBackgroundReturnDoNotBecomeAnotherColdOpen() {
        val process = OpenLifecyclePolicy()
        assertTrue(process.claimColdOpenOnce())
        // The same companion instance remains after a rotation or language change.
        assertFalse(process.claimColdOpenOnce())
        // A new Activity in this process also leaves the existing warm-open flag to handle a background return.
        assertFalse(process.claimColdOpenOnce())
    }

    @Test fun aNewProcessCountsColdEvenWhenAndroidRestoresAnActivity() {
        val oldProcess = OpenLifecyclePolicy()
        assertTrue(oldProcess.claimColdOpenOnce())
        assertFalse(oldProcess.claimColdOpenOnce())
        // After process death Android can restore saved state, but the process policy itself starts fresh.
        val restoredProcess = OpenLifecyclePolicy()
        assertTrue(restoredProcess.claimColdOpenOnce())
    }
}

package io.github.pini236.skiapp.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protect the distinction between a fresh installation, an upgrade, and analytics enabled after a launch. */
class InstallAnalyticsTest {
    @Test fun aFreshInstallCanBeCountedOnce() {
        assertTrue(InstallAnalytics.qualifiesFirstOpen(false, false, 100L, 100L))
        assertFalse(InstallAnalytics.qualifiesFirstOpen(true, false, 100L, 100L))
    }

    @Test fun anUpgradeIsNotANewInstallEvenWithoutLegacyReferrerState() {
        // Older versions did not write the referrer marker when opened from a link.
        assertFalse(InstallAnalytics.qualifiesFirstOpen(false, false, 100L, 200L))
    }

    @Test fun theLegacyFirstLaunchMarkerSurvivesMigration() {
        assertFalse(InstallAnalytics.qualifiesFirstOpen(false, true, 100L, 100L))
    }

    @Test fun enablingAnalyticsLaterDoesNotCountAnotherFirstOpen() {
        assertFalse(InstallAnalytics.qualifiesFirstOpen(true, false, 100L, 100L))
    }

    @Test fun unknownInstallationTimesAreNotInventedAsANewInstall() {
        assertFalse(InstallAnalytics.qualifiesFirstOpen(false, false, 0L, 0L))
        assertFalse(InstallAnalytics.qualifiesFirstOpen(false, false, -1L, -1L))
    }
}

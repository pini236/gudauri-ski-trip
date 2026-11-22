package io.github.pini236.skiapp.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallChannelTest {
    @Test fun googlePlayIsStore() = assertEquals("store", InstallChannel.of(false, false, "com.android.vending"))

    @Test fun noInstallerOrSystemInstallerIsSideload() {
        for (i in listOf(null, "", "com.google.android.packageinstaller", "com.android.packageinstaller")) assertEquals(i, "sideload", InstallChannel.of(false, false, i))
    }

    @Test fun anyOtherInstallerIsUnknownAndNeverTheRawName() {
        for (i in listOf("com.sec.android.app.samsungapps", "com.amazon.venezia", "org.fdroid.fdroid", "com.example.weird")) {
            val v = InstallChannel.of(false, false, i)
            assertEquals(i, "unknown", v)
            assertFalse(v == i)
        }
    }

    @Test fun theEmulatorAndThePreviewBuildComeFromTheBuildNotTheInstaller() {
        assertEquals("emulator", InstallChannel.of(true, false, "com.android.vending"))
        assertEquals("emulator", InstallChannel.of(true, true, null))
        assertEquals("preview", InstallChannel.of(false, true, "com.android.vending"))
        assertEquals("preview", InstallChannel.of(false, true, null))
    }

    @Test fun everyAnswerIsInTheClosedList() {
        for (e in listOf(true, false)) for (p in listOf(true, false)) for (i in listOf(null, "", "com.android.vending", "x.y", "com.android.packageinstaller"))
            assertTrue(InstallChannel.of(e, p, i) in InstallChannel.ALL)
        assertEquals(setOf("store", "sideload", "preview", "emulator", "unknown"), InstallChannel.ALL)
    }

    @Test fun isInternalIsSentOnlyWhenMarked() {
        assertEquals(mapOf<String, Any>("is_internal" to true), InternalFlag.props(true))
        assertTrue(InternalFlag.props(false).isEmpty())
    }
}

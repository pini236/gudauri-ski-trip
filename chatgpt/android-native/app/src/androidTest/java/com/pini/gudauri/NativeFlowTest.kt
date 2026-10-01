package com.pini.gudauri

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import com.pini.gudauri.data.AppLinks
import com.pini.gudauri.data.MeetingChoice
import java.time.LocalDate
import java.time.LocalTime
import androidx.core.content.FileProvider
import android.graphics.BitmapFactory
import com.pini.gudauri.data.DataParser
import com.pini.gudauri.data.MeetingModel
import com.pini.gudauri.ui.renderMeetingCard
import org.junit.Assert.*

class NativeFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    // Compose semantics can remain available behind an Android system-error dialog.
    // Check the real active window as well, so that hidden UI is not a device pass.
    @Before @After fun noSystemErrorDialog() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val processes=android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("dumpsys activity processes")).bufferedReader().use { it.readText() }
        assertFalse("An Android process is marked crashing or not responding",
            Regex("m(?:Crashing|NotResponding)=true").containsMatchIn(processes))
        val root=automation.rootInActiveWindow
        assertNotNull("Cannot verify the active Android window",root)
        try {
            for(message in listOf("isn't responding","is not responding","keeps stopping")) {
                val errors=requireNotNull(root).findAccessibilityNodeInfosByText(message)
                assertTrue("Android system error dialog: $message",errors.isEmpty())
            }
        } finally {
            @Suppress("DEPRECATION")
            root?.recycle()
        }
    }
    private fun theme(label: String) {
        repeat(3) {
            if(compose.onAllNodes(hasContentDescription("מצב תצוגה: $label.",substring=true)).fetchSemanticsNodes().isNotEmpty()) return
            compose.onNode(hasContentDescription("מצב תצוגה:",substring=true)).performClick()
        }
        compose.onNode(hasContentDescription("מצב תצוגה: $label.",substring=true)).assertExists()
    }
    private fun home() {
        compose.waitUntil(90000){compose.onAllNodesWithTag("home-content").fetchSemanticsNodes().isNotEmpty()}
        theme("יום")
        compose.waitUntil(90000){compose.onAllNodesWithTag("home-ridge-ready").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun revealHome(text: String) {
        compose.onNodeWithTag("home-content").performScrollToNode(hasText(text))
    }
    private fun homeAction(text: String) {
        revealHome(text)
        compose.onNodeWithText(text).performClick()
    }
    private fun mapReady() {
        compose.waitUntil(90000){compose.onAllNodesWithTag("native-map-ready").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(750,10000)
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"qa").apply{mkdirs()}
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun sourceFlightMapSelectionAndBack() {
        home()
        screenshot("home-light")
        compose.onNodeWithText("10.1.2027").assertIsDisplayed()
        homeAction("מפת מסלולים")
        mapReady()
        screenshot("map-3d-light")
        compose.onNodeWithText("פתח").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Soliko 2")
        compose.onNode(hasText("Soliko 2") and !hasSetTextAction()).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Soliko 2").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Soliko 2").assertIsDisplayed()
        screenshot("selected-partial")
        if(compose.onAllNodesWithContentDescription("הסר ממועדפים").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithContentDescription("הסר ממועדפים").performClick()
        compose.onNodeWithContentDescription("שמור במועדפים").performClick()
        compose.onNodeWithContentDescription("הסר ממועדפים").assertExists()
        compose.onNodeWithText("Soliko 2").performClick()
        compose.onNodeWithText("מיפוי חלקי").performScrollTo().assertIsDisplayed()
        screenshot("details-partial")
        compose.onNodeWithText("Soliko 2").performClick()
        compose.onNodeWithText("מבט על").performClick()
        screenshot("map-top")
        compose.onNodeWithText("בית ←").performClick()
        revealHome("10.1.2027")
        compose.onNodeWithText("10.1.2027").assertIsDisplayed()
    }
    @Test fun lightDarkAndNativeMapControls() {
        home()
        homeAction("מפת מסלולים")
        mapReady()
        compose.onNodeWithContentDescription("התקרב").performClick()
        compose.onNodeWithContentDescription("התרחק").performClick()
        compose.onNodeWithContentDescription("הצג הכל").performClick()
        compose.onNodeWithText("צד Kobi").performClick()
        theme("לילה")
        compose.onNode(hasContentDescription("מצב תצוגה: לילה.",substring=true)).assertExists()
        screenshot("map-dark")
    }
    @Test fun completeReturnPassAndStubRestoration() {
        home()
        compose.onNodeWithText("TLV").assertExists()
        compose.onNodeWithText("TBS").assertExists()
        compose.onNodeWithTag("switch-flight").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("flight-return").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("15.1.2027").assertExists()
        compose.onNodeWithText("6H 892").assertExists()
        compose.onNodeWithText("02:15").assertExists()
        compose.onNodeWithContentDescription("תלישת ספח כרטיס חזור").performClick()
        compose.waitUntil(2000) { compose.onAllNodesWithTag("ticket-stub").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("ticket-stub").fetchSemanticsNodes().isNotEmpty() }
        screenshot("flight-return")
    }
    @Test fun importWebsiteRunLinkOpensExactSelection() {
        home()
        homeAction("פתיחת קישור מהאתר")
        compose.onNode(hasSetTextAction()).performTextInput(AppLinks.run("Tatra 2"))
        compose.onNodeWithText("פתח").performClick()
        mapReady()
        compose.onNodeWithText("Tatra 2").assertIsDisplayed()
    }
    @Test fun profileSelectionReadsTheRealStartAndEndAndIsAccessible() {
        home()
        homeAction("פתיחת קישור מהאתר")
        compose.onNode(hasSetTextAction()).performTextInput(AppLinks.run("Tatra 2"))
        compose.onNodeWithText("פתח").performClick()
        mapReady()
        compose.onNodeWithText("Tatra 2").performClick()
        compose.onNodeWithTag("profile-position").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile-reading").assertTextEquals("מההתחלה 0 מ׳ · גובה 2665 מ׳ · שיפוע כאן 6°")
        compose.onNodeWithTag("profile-position").performSemanticsAction(SemanticsActions.SetProgress) { action -> assertTrue(action(1f)) }
        compose.onNodeWithTag("profile-reading").assertTextEquals("מההתחלה 2294 מ׳ · גובה 2173 מ׳ · שיפוע כאן 5°")
        compose.onNodeWithTag("profile-position").performSemanticsAction(SemanticsActions.SetProgress) { action -> assertTrue(action(0f)) }
        compose.onNodeWithTag("profile-reading").assertTextEquals("מההתחלה 0 מ׳ · גובה 2665 מ׳ · שיפוע כאן 6°")
        screenshot("profile-selection")
    }
    @Test fun meetingSelectionClearUndoAndMapRoundTrip() {
        home()
        homeAction("נקודת מפגש")
        compose.onNodeWithTag("meeting-empty").performScrollTo().assertExists()
        compose.onNodeWithTag("meeting-preset-noon").performScrollTo().performClick()
        compose.onNodeWithTag("meeting-card").performScrollTo().assertExists()
        compose.onNodeWithText("Snow Park · 13:00").assertExists()
        screenshot("meeting-noon")
        compose.onNodeWithText("ביטול נקודת המפגש").performScrollTo().performClick()
        compose.onNodeWithTag("meeting-empty").assertExists()
        compose.onNodeWithTag("meeting-card").assertDoesNotExist()
        compose.onNodeWithTag("meeting-undo").assertIsDisplayed()
        compose.onNodeWithText("החזרה").assertIsDisplayed().performClick()
        compose.onNodeWithText("Snow Park · 13:00").assertExists()
        compose.onNodeWithText("לראות במפת המסלולים").performScrollTo().performClick()
        mapReady()
        compose.onNodeWithTag("selected-lift-158744055",useUnmergedTree=true).assertTextEquals("Snow Park").assertIsDisplayed()
        // Back clears the lift, then returns to the saved meeting instead of home.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("meeting-card").performScrollTo().assertExists()
        compose.onNodeWithText("Snow Park · 13:00").assertExists()
    }
    @Test fun importedMeetingRestoresWebsiteStationDayAndTime() {
        home()
        val choice=MeetingChoice("158744055b",LocalDate.of(2027,1,12),LocalTime.of(13,0))
        homeAction("פתיחת קישור מהאתר")
        compose.onNode(hasSetTextAction()).performTextInput(AppLinks.meeting(choice))
        compose.onNodeWithText("פתח").performClick()
        compose.onNodeWithTag("meeting-card").performScrollTo().assertExists()
        compose.onNodeWithText("Snow Park · 13:00").assertExists()
        compose.onNodeWithText("12.1.2027 · 2702 מ׳").assertExists()
        screenshot("meeting-imported")
    }
    @Test fun meetingImageIsLocalSquareAndProviderDoesNotExposeOtherFiles() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        fun asset(name: String)=context.assets.open("data/$name.json").bufferedReader().use { it.readText() }
        val model=MeetingModel(DataParser.parse(asset("runs-and-lifts"),asset("terrain"),trip=asset("trip")))
        val choice=MeetingChoice("158744055b",LocalDate.of(2027,1,11),LocalTime.of(13,0))
        val image=renderMeetingCard(context,model,choice)
        assertEquals(1080,image.width);assertEquals(1080,image.height)
        val directory=File(context.cacheDir,"meeting-cards").apply { mkdirs() }
        val file=File.createTempFile("meet-test-",".png",directory)
        try {
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            context.contentResolver.openInputStream(uri).use { stream ->
                val decoded=BitmapFactory.decodeStream(stream)
                assertNotNull(decoded);assertEquals(1080,decoded.width);decoded.recycle()
            }
            assertTrue(runCatching { FileProvider.getUriForFile(context,"${context.packageName}.files",File(context.filesDir,"not-shared.png")) }.isFailure)
        } finally { image.recycle();file.delete() }
    }
}

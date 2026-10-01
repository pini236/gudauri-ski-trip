package com.pini.gudauri

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class NativeFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun home() {
        compose.waitUntil(90000){compose.onAllNodesWithText("מפת מסלולים").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("☀").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("☀").performClick()
        compose.waitUntil(90000){compose.onAllNodesWithTag("home-ridge-ready").fetchSemanticsNodes().isNotEmpty()}
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
        compose.onNodeWithText("מפת מסלולים").performClick()
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
        compose.onNodeWithText("10.1.2027").assertIsDisplayed()
    }
    @Test fun lightDarkAndNativeMapControls() {
        home()
        compose.onNodeWithText("מפת מסלולים").performClick()
        mapReady()
        compose.onNodeWithContentDescription("התקרב").performClick()
        compose.onNodeWithContentDescription("התרחק").performClick()
        compose.onNodeWithContentDescription("הצג הכל").performClick()
        compose.onNodeWithText("צד Kobi").performClick()
        compose.onNodeWithText("☾").performClick()
        compose.onNodeWithText("☀").assertExists()
        screenshot("map-dark")
    }
}

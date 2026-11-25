package io.github.pini236.skiapp.redesign

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelRulesTest {
    @Test fun aNameOverAnotherIsLeftOut() {
        val placed = listOf(Rect(100f, 100f, 200f, 120f))
        assertTrue(LabelRules.clashes(Rect(150f, 110f, 260f, 130f), placed))
        assertFalse(LabelRules.clashes(Rect(100f, 125f, 200f, 145f), placed))
        assertFalse(LabelRules.clashes(Rect(0f, 0f, 10f, 10f), emptyList()))
    }
}

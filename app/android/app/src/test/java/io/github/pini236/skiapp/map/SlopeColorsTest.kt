package io.github.pini236.skiapp.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The slope colours are the site's (GudRelief.SLOPE in site/js/relief.js), and the shader paints with the same ones. */
class SlopeColorsTest {
    private val relief = File(File(System.getProperty("site.data") ?: "../../../site/data").parentFile, "js/relief.js").readText()

    @Test fun theSitesThresholdsAndColours() {
        val slope = Regex("""R\.SLOPE=\[(.*?)];""").find(relief)!!.groupValues[1]
        val steps = Regex("""\[(\d+),'(#[0-9A-Fa-f]{6})'""").findAll(slope).map { it.groupValues[1].toFloat() to it.groupValues[2].uppercase() }.toList()
        assertEquals(SlopeColors.HEX, steps.map { it.second })
        assertEquals(SlopeColors.LIMITS.toList(), steps.dropLast(1).map { it.first })
    }

    @Test fun theShaderHasTheSameColours() {
        assertTrue(SlopeColors.GLSL, SlopeColors.GLSL.startsWith("vec3 slopeCol(float d){ return d<15.0? vec3(0.247,0.659,0.373) : d<25.0?"))
        assertTrue(SlopeColors.GLSL, SlopeColors.GLSL.endsWith(": vec3(0.863,0.231,0.200); }"))
        assertEquals(0.949f, SlopeColors.of(20f)[0], 0.001f)
        assertEquals(0.863f, SlopeColors.of(45f)[0], 0.001f)
    }
}

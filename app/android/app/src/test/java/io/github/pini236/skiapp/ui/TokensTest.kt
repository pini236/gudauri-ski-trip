package io.github.pini236.skiapp.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The app's tokens are the site's tokens: a colour changed in site/css/site.css fails here until it is carried over. */
class TokensTest {
    private val css = File(File(System.getProperty("site.data") ?: "../../../site/data").parentFile, "css/site.css").readText()

    private fun block(selector: String): Map<String, String> {
        val start = css.indexOf(selector).also { require(it >= 0) { selector } }
        val body = css.substring(css.indexOf('{', start) + 1, css.indexOf('}', start))
        return Regex("--([a-z0-9-]+):\\s*(#[0-9A-Fa-f]{6})").findAll(body).associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private fun hex(c: Color): String {
        val v = (c.value shr 32).toLong()
        return "#%06X".format(v and 0xFFFFFF)
    }

    private fun check(css: Map<String, String>, c: SkiColors) {
        val app = mapOf(
            "snow" to c.snow, "paper" to c.paper, "ink" to c.ink, "muted" to c.muted, "rule" to c.rule, "glacier" to c.glacier,
            "grid" to c.grid, "lift" to c.lift, "casing" to c.casing,
            "p-green" to c.green, "p-blue" to c.blue, "p-red" to c.red, "p-black" to c.black, "on-board" to c.onBoard,
            "accent" to c.accent, "on-accent" to c.onAccent, "dash" to c.dash, "sky1" to c.sky1, "sky2" to c.sky2,
            "water" to c.water, "road" to c.road, "contour" to c.contour, "peak" to c.peak,
            "water-edge" to c.waterEdge, "road-main" to c.roadMain, "vill" to c.village, "contour-i" to c.contourI,
            "bp-paper" to c.bpPaper, "bp-paper2" to c.bpPaper2, "bp-ink" to c.bpInk, "bp-muted" to c.bpMuted,
            "bp-strip" to c.bpStrip, "bp-on-strip" to c.bpOnStrip, "bp-acc" to c.bpAccent,
        )
        for ((name, color) in app) assertEquals("--$name", css.getValue(name), hex(color))
    }

    @Test fun dayMatchesTheSite() = check(block("\n:root{\n  --snow"), DayColors)

    @Test fun nightMatchesTheSite() = check(block(":root[data-theme=\"dark\"]{"), NightColors)
}

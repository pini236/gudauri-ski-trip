package io.github.pini236.skiapp.game

import io.github.pini236.skiapp.R
import io.github.pini236.skiapp.data.Profile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The descent (game/DescentGame.kt): the runs laid out exactly as on the site (the numbers below are what the site's
 * own buildRun() makes, run in Node on design/round4/game), and the skier gets down.
 */
class DescentGameTest {
    private val profiles: List<Profile> by lazy {
        // the game's page, or the script of its own next to it (the site's content security policy moved it there)
        val dir = File(System.getProperty("site.data") ?: "../../../site/data").resolve("../games/descent")
        val html = (listOf(File(dir, "index.html")) + (dir.listFiles { f -> f.name.endsWith(".js") }?.sortedBy { it.name } ?: emptyList()))
            .filter { it.exists() }.map { it.readText() }.first { "const PROFILES = " in it }
        val start = html.indexOf("const PROFILES = ") + "const PROFILES = ".length
        Profile.parse(html.substring(start, html.indexOf("];", start) + 1))
    }
    private class E(val key: String, val len: Float, val top: Int, val bot: Int, val kick: IntArray, val gaps: FloatArray, val rocks: FloatArray, val cat: Float,
                    val zones: Int, val coins: Int, val spots: FloatArray, val g100: Float, val s100: Float)
    private val site = listOf(
        E("Pirveli", 1140f, 2152, 1993, intArrayOf(120,295,470,645,820,915), floatArrayOf(), floatArrayOf(164.172f,429.068f), 553.554f, 0, 58, floatArrayOf(182f,329.4f,504.8f,673.2f,855.6f), -16.1767f, -0.16687f),
        E("Kikilo 1", 1150f, 2766, 2450, intArrayOf(120,295,460,635,810,985), floatArrayOf(810.5f), floatArrayOf(353.667f,494.076f,783.118f,916.479f), 563.34f, 2, 38, floatArrayOf(148f,325f,509f,679f,863f), -36.5767f, -0.28081f),
        E("Tatra 2", 2270f, 2665, 2170, intArrayOf(120,295,470,565,740,915,1090,1260,1435,1530,1705,1880,2055), floatArrayOf(740.5f), floatArrayOf(329.231f,697.928f,1922.292f,2163.348f), 1044.768f, 1, 109, floatArrayOf(344f,602.2f,965.4f,1328.6f,1733.8f), -15.2767f, -0.16687f),
        E("Sadzele 3", 1320f, 3242, 2895, intArrayOf(120,245,340,515,655,775,950,1125), floatArrayOf(515.5f,950.5f), floatArrayOf(173.608f,284.861f,397.695f,563.668f,715.81f,893.173f,1084.089f,1223.604f), 700.52f, 3, 52, floatArrayOf(151f,369.2f,580.4f,805.6f,988.8f), -69.1267f, -0.54294f),
        E("Sadzele 1", 2010f, 3232, 2777, intArrayOf(120,215,390,505,665,790,965,1085,1260,1435,1570,1745), floatArrayOf(790.5f,505.5f), floatArrayOf(188.281f,340.574f,584.914f,736.147f,849.46f,1123.722f,1232.391f,1354.581f,1481.59f,1717.009f,1827.749f), 1131.22f, 4, 84, floatArrayOf(248f,534.6f,863.2f,1177.8f,1499.4f), -52.1767f, -0.67379f),
    )

    @Test fun theSiteRngGivesTheSameNumbers() {
        val r = SiteRng(17)
        assertEquals(0.6771502960473299, r.next(), 1e-12); assertEquals(0.19265692122280598, r.next(), 1e-12); assertEquals(0.5313839064911008, r.next(), 1e-12)
    }

    @Test fun everyRunIsLaidOutAsOnTheSite() {
        assertEquals(5, profiles.size)
        for ((i, e) in site.withIndex()) {
            val c = Course(profiles[i], i, 5)
            assertEquals(e.key, c.key); assertEquals(e.len, c.len, .01f); assertEquals(e.top, c.top); assertEquals(e.bot, c.bot)
            assertArrayEquals(e.key, e.kick, c.kickers.map { it.x.toInt() }.toIntArray())
            assertArrayEquals(e.key, e.gaps, c.gaps.map { it.x0 }.toFloatArray(), .001f)
            assertArrayEquals(e.key, e.rocks, c.rocks.map { it.x }.toFloatArray(), .01f)
            assertEquals(e.key, e.cat, c.cats[0].x, .01f)
            assertEquals(e.key, e.zones, c.zones.size); assertEquals(e.key, e.coins, c.coins.size)
            assertArrayEquals(e.key, e.spots, c.spots.map { it.x }.toFloatArray(), .01f)
            assertEquals(e.key, e.g100, c.ground(100f), .001f); assertEquals(e.key, e.s100, c.slope(100f), .0001f)
            assertEquals(listOf(0, 1, 2, 3, 4), c.spots.map { it.coat }) // the other five coats
        }
    }

    private class Ear : DescentGame.Out { val pops = ArrayList<Int>(); var done: Boolean? = null
        override fun pop(say: Say) { pops += say.id }; override fun finished(ok: Boolean) { done = ok } }

    @Test fun withNoInputTheSkierGetsDownEveryRun() {
        for ((i, p) in profiles.withIndex()) {
            val ear = Ear(); val g = DescentGame(Course(p, i, 5), 5, ear, avalancheOn = false, rnd = Random(i))
            var t = 0f
            while (g.state != DescentGame.State.END && t < 900f) { g.step(1 / 60f); t += 1 / 60f; assertTrue(p.key, g.x.isFinite() && g.y.isFinite()) }
            assertEquals(p.key, true, ear.done)
            assertTrue(p.key, g.score > 0); assertTrue(p.key, g.got in 0..5)
        }
    }

    @Test fun aPopAtTheLipIsPerfectAndAFlipIsCounted() {
        val ear = Ear(); val c = Course(profiles[2], 2, 5); val g = DescentGame(c, 5, ear, avalancheOn = false, rnd = Random(1))
        g.step(.05f); repeat(80) { g.step(.05f) } // the count, then off
        assertEquals(DescentGame.State.RUN, g.state)
        // on the snow just before the first kicker's lip, crouched a moment, then let go on the yellow line
        val k = c.kickers[0].x
        g.x = k - 2f; g.y = c.ground(g.x); g.air = false; g.s = 4f // slow, so the lip does not throw it into the air first
        g.press(); g.step(.1f); g.release()
        assertTrue("perfect pop", R.string.game_descent_pop_perfect in ear.pops); assertTrue(g.air); assertEquals(1, g.stats.pops)
        // hold in the air: a flip; let go to land it
        g.press(); var n = 0; while (g.air && n < 600) { if (g.airRot > 6.5f) g.release(); g.step(1 / 60f); n++ }
        assertTrue("flips ${g.flips}, falls ${g.stats.falls}", g.flips >= 1 || g.stats.falls >= 1)
    }

    @Test fun theEndCountsTheStarsAndTheBonus() {
        val ear = Ear(); val g = DescentGame(Course(profiles[0], 0, 5), 5, ear, avalancheOn = false)
        g.finish(true)
        assertEquals(true, ear.done); assertTrue(g.goals[0]); assertEquals(false, g.goals[1])
        assertEquals(600, g.score) // no crew yet, at t 0: 600 - 3t
        val ghost = g.record(); assertEquals(ghost.t, DescentGame.Ghost.load(ghost.save())!!.t)
    }
}

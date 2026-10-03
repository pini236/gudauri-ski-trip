package io.github.pini236.skiapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * The run view says what the site says about a run (13.3). The expected numbers were printed by the site's own code
 * (site/js/app.js runProfile, comparable and navList; site/js/relief.js GudRelief.stats) on the real data, 3.10.2026.
 * The site counts in double precision and the app's elevation model in float, hence the small tolerances.
 */
class RunFactsTest {
    private val dir = File(System.getProperty("site.data") ?: "../../../site/data")
    private val runs by lazy { Runs.parse(File(dir, "runs-and-lifts.json").readText()) }
    private val terrain by lazy { Terrain.parse(File(dir, "terrain.json").readText()) }

    private class Site(
        val n: Int, val dmax: Double, val h0: Double, val h1: Double, val steepD: Double, val steepG: Double, val g0: Double,
        val top: Int, val bot: Int, val drop: Int, val maxG: Double, val steepAs: String?, val longAs: String?,
    )

    private val site = mapOf(
        "Soliko 1" to Site(284, 2825.61, 2690.74, 2149.46, 1116.71, 0.32029, 0.11447, 2691, 2149, 541, 0.31808, "Kikilo 2", "Kobi"),
        "Kudebi 3" to Site(53, 526.67, 2985.06, 2808.27, 209.78, 0.47584, 0.26579, 2985, 2808, 177, 0.4751, "Sadzele 3", "Khada"),
        "Kudebi 1" to Site(85, 830.94, 2808.27, 2660.96, 157.7, 0.26407, 0.13182, 2988, 2661, 327, 0.38008, "Tatra 2", "Sadzele 3"),
        "Kikilo 1" to Site(117, 1150.35, 2765.72, 2449.71, 623.54, 0.34282, 0.23107, 2766, 2347, 419, 0.32976, "Sportuli 1", "Sadzele 2"),
        "Snow Park" to Site(97, 946.73, 2828.04, 2701.39, 470.78, 0.19441, 0.11616, 2830, 2701, 129, 0.19807, "Firni 2", "Pirveli"),
        "Pirveli" to Site(118, 1167.88, 2152.05, 1995.2, 563.34, 0.24613, 0.08552, 2152, 1993, 159, 0.24234, "Firni 1", "Snow Park"),
        "Khada" to Site(65, 617.82, 2803.96, 2689.1, 20.8, 0.27419, 0.21709, 2806, 2689, 117, 0.27242, "Tatra 1", "Firni 1"),
        "Goodaura 2" to Site(138, 1309.9, 2389.23, 2161.32, 0.0, 0.31113, 0.28412, 2611, 2160, 451, 0.34485, "Sportuli 2", "Tatra 2"),
        "Sportuli 1" to Site(191, 1914.33, 2568.66, 2150.32, 176.13, 0.34953, 0.19649, 2569, 2150, 418, 0.33314, "Goodaura 1", "Kikilo 1"),
        "Sadzele 2" to Site(88, 886.37, 3235.02, 3037.33, 669.32, 0.33436, 0.0984, 3236, 2931, 305, 0.33436, "Goodaura 1", "Firni 2"),
        "Sadzele 3" to Site(177, 1746.78, 3235.02, 2900.08, 677.53, 0.56505, 0.02069, 3241, 2825, 416, 0.48107, "Kudebi 3", "Kudebi 1"),
        "Kikilo 2" to Site(110, 1090.29, 2728.91, 2449.71, 761.85, 0.34597, 0.20489, 2729, 2450, 279, 0.32303, "Soliko 2", "Kudebi 2"),
        "Tatra 1" to Site(295, 2936.48, 2706.79, 2172.74, 1289.58, 0.29052, 0.09592, 2707, 2170, 536, 0.28604, "Kobi", "Kobi"),
        "u473280432" to null,
        "Zuma" to Site(37, 348.53, 2189.17, 2136.63, 165.41, 0.18966, 0.15971, 2189, 2136, 53, 0.2125, "Firni 1", "New Goodaura"),
        "u668794712" to Site(24, 230.93, 2200.38, 2171.01, 162.57, 0.15844, 0.11311, 2200, 2171, 29, 0.15337, null, null),
        "Goodaura 1" to Site(279, 2743.6, 2705.82, 2161.32, 762.62, 0.36735, 0.08681, 2706, 2159, 547, 0.33319, "Sportuli 1", "Soliko 1"),
        "Kobi" to Site(238, 2370.66, 2950.4, 2512.35, 667.2, 0.29095, 0.0571, 2950, 2512, 438, 0.28924, "Tatra 1", "Soliko 1"),
        "New Goodaura" to Site(70, 699.72, 2346.73, 2292.71, 376.23, 0.15072, 0.03468, 2347, 2293, 54, 0.14546, "Snow Park", "Zuma"),
        "u1217683225" to Site(38, 370.65, 2388.1, 2346.73, 41.08, 0.18502, 0.1182, 2391, 2347, 44, 0.1695, null, null),
        "u1234557981" to Site(52, 500.65, 2388.1, 2305.51, 37.42, 0.23383, 0.17009, 2390, 2306, 85, 0.20739, null, null),
        "Kudebi 2" to Site(65, 644.83, 2808.27, 2690.74, 213.43, 0.25195, 0.19032, 2975, 2691, 285, 0.49505, "Sadzele 3", "Kikilo 2"),
        "u1259783829" to Site(18, 170.5, 2840.5, 2824.99, 101.39, 0.13922, 0.08142, 2841, 2825, 16, 0.11805, null, null),
        "Sportuli 2" to Site(49, 491.08, 2368.95, 2276.79, 269.65, 0.23501, 0.17722, 2474, 2242, 232, 0.34502, "Goodaura 2", "Snow Park"),
        "u1259783838" to Site(13, 107.14, 2803.96, 2784.89, 19.78, 0.22328, 0.17804, 2806, 2785, 21, 0.22328, null, null),
        "u1259783839" to Site(11, 95.91, 2784.89, 2765.72, 0.0, 0.19984, 0.19984, 2785, 2766, 19, 0.19984, null, null),
        "Sadzele 1" to Site(223, 2227.49, 3231.99, 2817.47, 60.39, 0.54626, 0.41932, 3232, 2771, 460, 0.52208, "Kudebi 2", "Goodaura 2"),
        "Baby" to Site(17, 162.56, 2708.74, 2690.95, 98.11, 0.1276, 0.10974, 2709, 2691, 18, 0.11521, null, null),
        "Bombora" to Site(32, 310.25, 2689.1, 2663.96, 170.93, 0.13359, 0.05234, 2690, 2664, 26, 0.12621, null, null),
        "Shino" to Site(129, 1288.22, 2309.33, 2178.31, 984.64, 0.16766, 0.10647, 2309, 2178, 131, 0.13992, null, null),
        "Firni 1" to Site(71, 695.56, 2950.4, 2842.26, 76.47, 0.25343, 0.13667, 2950, 2842, 108, 0.22104, "Zuma", "Zuma"),
        "Firni 2" to Site(164, 1620.91, 2842.26, 2708.31, 714.72, 0.2294, 0.13653, 2842, 2708, 134, 0.20039, "Snow Park", "Sadzele 2"),
        "Tatra 2" to Site(229, 2294.48, 2664.57, 2172.74, 656.31, 0.36568, 0.08822, 2665, 2170, 494, 0.36267, "Kudebi 1", "Goodaura 2"),
        "Soliko 2" to Site(38, 373.94, 2495.01, 2404.99, 59.57, 0.3315, 0.32466, 2495, 2405, 90, 0.32606, "Kikilo 2", "Kudebi 3"),
    )

    @Test fun everyRunMatchesTheSite() {
        assertEquals(site.keys, runs.pistes.map { it.key }.toSet())
        val cache = HashMap<String, Pair<Double, Int>>()
        for (p in runs.pistes) {
            val want = site[p.key]
            val got = RunFacts.of(terrain, p)
            if (want == null) { assertNull(p.key, got); continue }
            got!!
            assertEquals("${p.key} points", want.n, got.points.size)
            assertEquals("${p.key} length", want.dmax, got.length.toDouble(), 0.5)
            assertEquals("${p.key} top of the line", want.h0, got.points.first().h.toDouble(), 0.5)
            assertEquals("${p.key} bottom of the line", want.h1, got.points.last().h.toDouble(), 0.5)
            assertEquals("${p.key} steepest 100 m starts", want.steepD, got.steepD.toDouble(), 0.5)
            assertEquals("${p.key} steepest 100 m", want.steepG, got.steepG.toDouble(), 0.001)
            assertEquals("${p.key} first 150 m", want.g0, got.g0.toDouble(), 0.001)
            assertEquals("${p.key} top", want.top, got.top)
            assertEquals("${p.key} bottom", want.bot, got.bot)
            assertEquals("${p.key} drop", want.drop, got.drop)
            assertEquals("${p.key} steepest stretch", want.maxG, got.maxG, 0.001)
            assertEquals("${p.key} as steep as", want.steepAs to want.longAs, RunFacts.compare(terrain, runs.pistes, p.key, cache) ?: (null to null))
        }
    }

    @Test fun theSignStepsThroughTheRunsAsOnTheSite() {
        assertEquals(listOf("Baby", "Bombora", "Pirveli", "Snow Park", "Zuma", "Firni 1", "Firni 2", "Goodaura 1", "Goodaura 2", "Khada", "Kikilo 1", "Kikilo 2", "Kobi", "New Goodaura", "Shino", "Soliko 1", "Soliko 2", "Sportuli 1", "Sportuli 2", "Tatra 1", "Tatra 2", "Kudebi 1", "Kudebi 2", "Kudebi 3", "Sadzele 3", "Sadzele 1", "Sadzele 2"), RunFacts.order(runs.pistes))
    }

    @Test fun numbersSortAsNumbers() {
        assertEquals(listOf("Kudebi 1", "Kudebi 2", "Kudebi 10", "kudebi 11"), listOf("Kudebi 10", "kudebi 11", "Kudebi 2", "Kudebi 1").sortedWith { a, b -> RunFacts.natural(a, b) })
    }
}

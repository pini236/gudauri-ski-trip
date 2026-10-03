package io.github.pini236.skiapp.data

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A point of a run's line from its top: projected metres, metres from the top, height, slope in degrees over about 40 m. */
class RunPoint(val x: Float, val y: Float, val d: Float, val h: Float, val a: Float)

/**
 * The run view's numbers (13.3, T2 and T3), computed as the site does (site/js/app.js runProfile, sampleLine,
 * comparable; site/js/relief.js GudRelief.stats), so the app and the site say the same thing about a run. Checked
 * against numbers the site's own code printed on the real data (RunFactsTest).
 *
 * [points]: the run's longest line, top to bottom, every ~10 m. [steepD], [steepG], [steepI], [steepJ]: the steepest
 * 100 m of it (where it starts, the grade, and its first and last points; [steepG] 0 when there is none). [top], [bot],
 * [drop], [maxG]: over all of the run's lines, the steepest ~100 m stretch anywhere ([maxG], the number in the details).
 * [g0]: the grade of the first 150 m.
 */
class RunFacts(
    val points: List<RunPoint>, val steepD: Float, val steepG: Float, val steepI: Int, val steepJ: Int,
    val top: Int, val bot: Int, val drop: Int, val maxG: Double, val g0: Float,
) {
    val length get() = points.last().d

    companion object {
        /** A line (x, y pairs) from its higher end. */
        fun topDown(t: Terrain, l: FloatArray): FloatArray {
            val n = l.size / 2
            if (n < 2 || t.elev(l[0], l[1]) >= t.elev(l[(n - 1) * 2], l[(n - 1) * 2 + 1])) return l
            return FloatArray(l.size).also { r -> for (i in 0 until n) { r[i * 2] = l[(n - 1 - i) * 2]; r[i * 2 + 1] = l[(n - 1 - i) * 2 + 1] } }
        }

        /** Points every ~[step] metres along a line, with the height and the slope over about 40 m (the site's sampleLine). */
        fun sample(t: Terrain, l: FloatArray, step: Float): List<RunPoint> {
            val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val ds = ArrayList<Float>()
            var d = 0.0
            for (i in 0 until l.size / 2) {
                if (i == 0) { xs += l[0]; ys += l[1]; ds += 0f; continue }
                val ax = l[i * 2 - 2]; val ay = l[i * 2 - 1]; val bx = l[i * 2]; val by = l[i * 2 + 1]
                val len = hypot((bx - ax).toDouble(), (by - ay).toDouble())
                val n = max(1, (len / step).roundToInt())
                for (k in 1..n) { val f = k.toDouble() / n; xs += (ax + (bx - ax) * f).toFloat(); ys += (ay + (by - ay) * f).toFloat(); ds += (d + len * f).toFloat() }
                d += len
            }
            val hs = FloatArray(xs.size) { t.elev(xs[it], ys[it]) }
            return List(xs.size) { i ->
                var a = i; var b = i
                while (a > 0 && ds[i] - ds[a] < 20) a--
                while (b < xs.size - 1 && ds[b] - ds[i] < 20) b++
                val dd = ds[b] - ds[a]
                val deg = if (dd > 0) Math.toDegrees(atan(abs(hs[b] - hs[a]).toDouble() / dd)).toFloat() else 0f
                RunPoint(xs[i], ys[i], ds[i], hs[i], deg)
            }
        }

        /** Highest and lowest point, and the steepest ~100 m stretch, over a run's lines (GudRelief.stats). */
        fun stats(t: Terrain, lines: List<FloatArray>): Triple<Double, Double, Double> {
            var top = -1e9; var bot = 1e9; var maxG = 0.0
            for (l in lines) {
                val n = l.size / 2
                val acc = DoubleArray(n); val h = DoubleArray(n)
                for (i in 0 until n) {
                    if (i > 0) acc[i] = acc[i - 1] + hypot((l[i * 2] - l[i * 2 - 2]).toDouble(), (l[i * 2 + 1] - l[i * 2 - 1]).toDouble())
                    h[i] = t.elev(l[i * 2], l[i * 2 + 1]).toDouble()
                    top = max(top, h[i]); bot = minOf(bot, h[i])
                }
                var j = 0
                for (i in 0 until n) {
                    while (j < n - 1 && acc[j] - acc[i] < 100) j++
                    val dl = acc[j] - acc[i]
                    if (dl >= 80) maxG = max(maxG, abs(h[j] - h[i]) / dl)
                }
            }
            return Triple(top, bot, maxG)
        }

        /** The run's facts, or null for a run with no line (an area only). */
        fun of(t: Terrain, p: Piste): RunFacts? {
            if (p.lines.isEmpty()) return null
            // the longest line, from its top (the first one of equal length, as the site's stable sort)
            val s = p.lines.map { sample(t, topDown(t, it), 10f) }.maxByOrNull { it.last().d } ?: return null
            var bestG = 0f; var bestD = 0f; var bestI = 0; var bestJ = 0
            for (i in s.indices) {
                var j = i
                while (j < s.size - 1 && s[j].d - s[i].d < 100) j++
                val dd = s[j].d - s[i].d
                if (dd >= 60) { val g = (s[i].h - s[j].h) / dd; if (g > bestG) { bestG = g; bestD = s[i].d; bestI = i; bestJ = j } }
            }
            val (top, bot, maxG) = stats(t, p.lines)
            val first = s.firstOrNull { it.d >= 150 } ?: s.last()
            val g0 = (s[0].h - first.h) / (if (first.d != 0f) first.d else 1f)
            return RunFacts(s, bestD, bestG, bestI, bestJ, top.roundToInt(), bot.roundToInt(), (top - bot).roundToInt(), maxG, g0)
        }

        /** A grade (rise over run) in whole degrees. */
        fun deg(g: Double): Int = Math.toDegrees(atan(g)).roundToInt()

        /**
         * "About as steep as X, about as long as Y" (T3, the site's comparable()): among the named runs (not ski ways or
         * beginner areas), the one whose steepest stretch is closest, and the one whose length is closest. Null for a
         * run that is not one of them.
         */
        fun compare(t: Terrain, pistes: List<Piste>, key: String, cache: MutableMap<String, Pair<Double, Int>>? = null): Pair<String, String>? {
            val all = pistes.filter { it.named && it.kind == "run" && it.lines.isNotEmpty() }.map { p ->
                val g = cache?.get(p.key)?.first ?: stats(t, p.lines).third
                cache?.put(p.key, g to p.len)
                Triple(p.key, g, p.len)
            }
            val me = all.firstOrNull { it.first == key } ?: return null
            if (all.size <= 2) return null
            val others = all.filter { it.first != key }
            return others.minBy { abs(it.second - me.second) }.first to others.minBy { abs(it.third - me.third) }.first
        }

        /** The named runs in the order the run sign steps through them: by colour, then by name (numbers as numbers). */
        fun order(pistes: List<Piste>): List<String> {
            val colors = listOf("green", "blue", "red", "black")
            return pistes.filter { it.named }.sortedWith(compareBy<Piste> { colors.indexOf(it.color) }.then { a, b -> natural(a.key, b.key) }).map { it.key }
        }

        /** "Kudebi 2" before "Kudebi 10": letters without case, runs of digits by their value (localeCompare, numeric). */
        fun natural(a: String, b: String): Int {
            var i = 0; var j = 0
            while (i < a.length && j < b.length) {
                if (a[i].isDigit() && b[j].isDigit()) {
                    var x = i; while (x < a.length && a[x].isDigit()) x++
                    var y = j; while (y < b.length && b[y].isDigit()) y++
                    val c = a.substring(i, x).toBigInteger().compareTo(b.substring(j, y).toBigInteger())
                    if (c != 0) return c
                    i = x; j = y
                } else {
                    val c = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                    if (c != 0) return c
                    i++; j++
                }
            }
            return (a.length - i).compareTo(b.length - j)
        }
    }
}

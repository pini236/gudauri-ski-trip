package io.github.pini236.skiapp.redesign

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.pini236.skiapp.data.Geo
import io.github.pini236.skiapp.dayrec.DayLog
import io.github.pini236.skiapp.dayrec.DayRec
import io.github.pini236.skiapp.dayrec.DayStore
import io.github.pini236.skiapp.dayrec.SampleDay
import org.json.JSONObject
import java.io.File

/** A window on the painted mountain (P.24): the day's descents in the speed light, the resort's runs faint behind them. */
internal class WinData(val faint: List<FloatArray>, val lines: List<Pair<FloatArray, IntArray>>, val pin: FloatArray? = null)

/** The day's descents projected onto the picture, off the main thread: points in the picture's coordinates with their bands. */
internal fun projectDay(m: StoryVideo.Mountain, lines: List<DayPaint.Line>): List<Pair<FloatArray, IntArray>> {
    val pj = m.view.proj ?: return emptyList()
    return lines.filter { it.t.isNotEmpty() }.map { OnMountain.project(pj, m.elev, it.xy, 2f) to it.band }
}

/** The resort's runs projected once, to be drawn faint behind every window. */
internal fun projectRuns(m: StoryVideo.Mountain, runs: io.github.pini236.skiapp.data.Runs?): List<FloatArray> {
    val pj = m.view.proj ?: return emptyList()
    return runs?.pistes?.flatMap { p -> p.lines.map { OnMountain.project(pj, m.elev, it, 2f) } }.orEmpty()
}

/** The lines of one analysed day (its descents only, by speed band), read from its log. Empty when the day cannot be read. */
internal fun dayLines(f: File, result: JSONObject): List<DayPaint.Line> = runCatching {
    val a = result.optJSONArray("segments")
    val segs = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.map { DayPaint.Seg(it.optString("type"), it.optLong("t0"), it.optLong("t1")) }
    val pts = DayLog.read(f).points.map { DayPaint.Pt(it.t, Geo.x(it.lon), Geo.y(it.lat), it.speed?.let { s -> s * 3.6 }) }
    DayPaint.build(pts, segs).lines
}.getOrDefault(emptyList())

/** The saved days (no example day) for the records' matching: id, start, analysis, moments. */
internal fun savedDays(files: List<File>): List<Records.SavedDay> = files.filter { !SampleDay.isSample(it) }.mapNotNull { f ->
    val res = runCatching { JSONObject(DayStore.resultOf(f).readText()) }.getOrNull() ?: return@mapNotNull null
    Records.SavedDay(f.name.removePrefix("day-").removeSuffix(".jsonl"), res.optLong("start"), res,
        runCatching { JSONObject(DayStore.momentsOf(f).readText()) }.getOrNull())
}

/**
 * The window itself: the picture framed on the day ([MyDaysRules.frame]), the runs faint, the descents lit over a white
 * case, and an optional pin. Without the picture, the plain tint.
 */
@Composable
internal fun MountainWindow(m: StoryVideo.Mountain?, win: WinData?, night: Boolean, modifier: Modifier, ramp: IntArray, line: Float = 3f, faintOn: Boolean = true,
                            overDraw: androidx.compose.ui.graphics.drawscope.DrawScope.(Offset, Float) -> Unit = { _, _ -> }) {
    val bg = if (night) Color(0xFF0E1A2C) else Color(0xFFE4EAF1)
    val img = remember(m) { m?.img?.asImageBitmap() }
    Canvas(modifier.background(bg).clipToBounds()) {
        if (m == null || img == null) return@Canvas
        val vw = m.view.w.toFloat(); val vh = m.view.h.toFloat()
        val f = MyDaysRules.frame(win?.lines?.map { it.first }.orEmpty(), size.width, size.height, vw, vh)
        drawImage(img, dstOffset = IntOffset(f.ox.toInt(), f.oy.toInt()), dstSize = IntSize((vw * f.k).toInt(), (vh * f.k).toInt()))
        if (win == null) return@Canvas
        fun at(q: FloatArray, i: Int) = Offset(f.ox + q[i * 2] * f.k, f.oy + q[i * 2 + 1] * f.k)
        if (faintOn) for (q in win.faint) for (i in 0 until q.size / 2 - 1) {
            if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
            drawLine(Color.White.copy(alpha = .38f), at(q, i), at(q, i + 1), 1.1f * density, cap = StrokeCap.Round)
        }
        val case = if (night) Color(0xFF050A16) else Color.White
        for (pass in 0..1) for ((q, b) in win.lines) for (i in 0 until q.size / 2 - 1) {
            if (q[i * 2].isNaN() || q[i * 2 + 2].isNaN()) continue
            if (pass == 0) drawLine(case, at(q, i), at(q, i + 1), (line + 1.8f) * density, cap = StrokeCap.Round)
            else drawLine(Color(0xFF000000.toInt() or ramp[b[i].coerceIn(0, ramp.size - 1)]), at(q, i), at(q, i + 1), line * density, cap = StrokeCap.Round)
        }
        win.pin?.let { p -> if (!p[0].isNaN()) overDraw(Offset(f.ox + p[0] * f.k, f.oy + p[1] * f.k), f.k) }
    }
}

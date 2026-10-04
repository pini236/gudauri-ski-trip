package io.github.pini236.skiapp.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The ski school's slope (the site's canvas): a bright training slope from behind the skier, forward up, with the
 * lesson's marks (stop lines, the green band, speed traps, gates), the tracks (two thin lines when clean, a wide smear
 * when skidding, a V in the wedge), the other skiers and the skier. The same in both themes. A finger on it is the
 * lesson's control; the screen around it holds the words and the buttons.
 */
@SuppressLint("ViewConstructor")
class SchoolView(context: Context, private val still: Boolean) : View(context) {
    var run: SchoolRun? = null
        set(v) { field = v; lastFrame = 0L; invalidate() }
    /** Each frame, after the slope moved. */
    var onFrame: ((SchoolRun) -> Unit)? = null
    var text: Typeface? = null
    /** The canvas's words: the stop line, the band, the trap and the tilt. */
    var words: (Say) -> String = { "" }

    private val d = resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { run?.let { place(it) } }

    /** The run's screen: this view's size, and a metre of slope by the site's PX (16 to 28 per CSS pixel). */
    fun place(r: SchoolRun) { r.w = width.toFloat(); r.h = height.toFloat(); r.px = (width / d / 19).coerceIn(16f, 28f) * d }

    // ---------- touch: a finger is the lesson's control; in the rhythm lesson every touch is a tap ----------
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val r = run ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { r.finger = SchoolRun.Finger(e.x, e.y); r.tap() }
            MotionEvent.ACTION_POINTER_DOWN -> r.tap()
            MotionEvent.ACTION_MOVE -> r.finger?.let { it.x = e.getX(0); it.y = e.getY(0) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> r.finger = null
        }
        return true
    }

    // ---------- drawing ----------
    private var lastFrame = 0L
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val lbl = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private val dash = DashPathEffect(floatArrayOf(4f, 6f), 0f)
    private val rnd = Random(5)

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) .016f else min(.033f, (now - lastFrame) / 1000f)
        lastFrame = now
        val r = run
        c.drawColor(0xFFFBFDFF.toInt())
        if (r == null) return
        if (r.w != width.toFloat()) place(r)
        r.update(dt)
        onFrame?.invoke(r)
        draw(c, r)
        postInvalidateOnAnimation()
    }

    private fun col(c: Long, a: Float = 1f): Int { val v = c.toInt(); return Color.argb((Color.alpha(v) * a).toInt(), Color.red(v), Color.green(v), Color.blue(v)) }

    private fun draw(c: Canvas, r: SchoolRun) {
        val w = r.w; val h = r.h; val px = r.px; val lv = r.lv; val s = r.s
        val edge = 15f
        val yTop = r.camY + (h * .7f) / px + 2; val yBot = r.camY - (h * .3f) / px - 2
        val left = r.sx(-edge); val right = r.sx(edge); val width = right - left
        fill.color = 0xFFEAF1F7.toInt(); c.drawRect(0f, 0f, left, h, fill); c.drawRect(right, 0f, w, h, fill)
        // the snow's texture
        fill.color = Color.argb(41, 159, 181, 203)
        var y = floor(yBot / 6) * 6
        while (y < yTop) {
            for (k in 0 until 4) {
                val x = ((y * 7.3f + k * 11) % 28) - 14
                val cx = r.sx(x); val cy = r.sy(y + ((k * 2.1f) % 6))
                oval.set(cx - px * 1.2f, cy - px * .25f, cx + px * 1.2f, cy + px * .25f); c.drawOval(oval, fill)
            }
            y += 6
        }
        y = floor(yBot / 9) * 9
        while (y < yTop) { for (side in intArrayOf(-1, 1)) tree(c, px, r.sx(side * (edge + 2 + ((y * 3.7f) % 5))), r.sy(y), h); y += 9 }
        // the drill's marks
        if (lv.mode == SchoolMode.WEDGE) {
            SchoolRun.STOPS.forEachIndexed { i, sy0 ->
                val yy = r.sy(sy0)
                val cc = if (i >= r.stops.size) 0xFFF08A3C else if (r.stops[i] == null) 0xFFD1342B else 0xFF1B8A4C
                fill.color = col(cc); c.drawRect(left, yy - 3 * d, right, yy + 3 * d, fill)
                fill.color = Color.argb(31, 27, 138, 76); c.drawRect(left, yy, right, yy + px * 1.5f, fill)
                label(c, words(Say(io.github.pini236.skiapp.R.string.game_school_canvas_line, listOf(i + 1))), left + 40 * d, yy - 8 * d, col(cc))
            }
            fill.color = Color.argb(33, 27, 138, 76); c.drawRect(left, r.sy(SchoolRun.BAND[1]), right, r.sy(SchoolRun.BAND[0]), fill)
            label(c, words(Say(io.github.pini236.skiapp.R.string.game_school_canvas_band)), right - 90 * d, r.sy(SchoolRun.BAND[0]) - 8 * d, 0xFF1B8A4C.toInt())
        }
        lv.traps?.forEachIndexed { i, ty ->
            val yy = r.sy(ty)
            val cc = if (i >= r.traps.size) 0xFF1F5FC4 else if (r.traps[i]) 0xFF1B8A4C else 0xFFD1342B
            fill.color = col(cc, .18f); c.drawRect(left, yy - px * 1.2f, right, yy, fill)
            fill.color = col(cc); c.drawRect(left, yy - 2 * d, right, yy + 2 * d, fill)
            label(c, words(Say(io.github.pini236.skiapp.R.string.game_school_canvas_trap, listOf(lv.trapLimit))), r.sx(0f), yy - px * 1.3f, col(cc))
        }
        // tracks: two thin lines when clean, a wide smear when skidding, a V in the wedge
        line.strokeCap = Paint.Cap.BUTT
        for (side in intArrayOf(-1, 1)) for (i in 1 until r.tracks.size) {
            val p = r.tracks[i - 1]; val q = r.tracks[i]
            if (q.y < yBot - 2 || p.y > yTop + 2) continue
            val op = (.17f + p.wd * .25f) * side; val oq = (.17f + q.wd * .25f) * side
            if (q.sk > .25f) { line.color = Color.argb(115, 159, 181, 203); line.strokeWidth = px * (.15f + q.sk * .5f) }
            else { line.color = Color.argb(140, 70, 95, 125); line.strokeWidth = 1.6f * d }
            c.drawLine(r.sx(p.x + cos(p.h) * op), r.sy(p.y - sin(p.h) * op), r.sx(q.x + cos(q.h) * oq), r.sy(q.y - sin(q.h) * oq), line)
        }
        // gates: in the look-ahead lesson they fade as you come close
        for (g in r.gates) {
            var al = 1f
            if (lv.fade && g.done == null) { val dd = g.y - s.y; al = ((dd - 7) / 6).coerceIn(0f, 1f) }
            val a = if (g.done != null) .6f else al
            val cc = if (g.x < 0) 0xFFD1342B else 0xFF1F5FC4
            fill.color = col(if (g.done == false) 0xFF9FB5CB else cc, a)
            for (k in intArrayOf(-1, 1)) {
                val xx = r.sx(g.x + k * g.w / 2); val yy = r.sy(g.y)
                c.drawRect(xx - 3 * d, yy - px * 1.4f, xx + 3 * d, yy, fill)
                val fx = xx - (if (k < 0) 0f else px * .8f)
                c.drawRect(fx, yy - px * 1.4f, fx + px * .8f, yy - px * .9f, fill)
            }
            if (g.done == true) { lbl.typeface = text; lbl.textSize = 14 * d; lbl.color = col(0xFF1B8A4C, a); c.drawText("✓", r.sx(g.x), r.sy(g.y) - px * 1.5f, lbl) }
        }
        // the finish line
        val fy = r.sy(lv.len); var fx = -edge
        while (fx < edge) { fill.color = if (((fx + edge).toInt()) % 2 == 1) 0xFF13233A.toInt() else Color.WHITE; c.drawRect(r.sx(fx), fy, r.sx(fx) + px, fy + px * .6f, fill); fx += 1 }
        for (p in r.people) person(c, px, r.sx(p.x), r.sy(p.y), p.color, cos(p.ph) * .6f, p.hit)
        val f = r.finger
        if (f != null && r.running && lv.mode == SchoolMode.STEER) {
            line.color = Color.argb(46, 19, 35, 58); line.strokeWidth = 2 * d; line.pathEffect = dash
            c.drawLine(r.sx(s.x), r.sy(s.y), f.x, f.y, line); line.pathEffect = null
            fill.color = Color.argb(128, 244, 185, 66); c.drawCircle(f.x, f.y, 14 * d, fill)
        }
        // the fall-line compass around the skier: red straight down, green across
        if (lv.traps != null) {
            val xx = r.sx(s.x); val yy = r.sy(s.y); val rr = px * 2.2f
            oval.set(xx - rr, yy - rr, xx + rr, yy + rr); line.strokeWidth = 6 * d
            for (k in -8..8) {
                val ang = k / 8f * 1.9f; val ah = abs(ang)
                line.color = if (ah < .35f) Color.argb(89, 209, 52, 43) else if (ah < 1) Color.argb(89, 244, 185, 66) else Color.argb(89, 27, 138, 76)
                c.drawArc(oval, Math.toDegrees((-Math.PI / 2 + ang - .12)).toFloat(), Math.toDegrees(.24).toFloat(), false, line)
            }
            line.color = 0xFF13233A.toInt(); line.strokeWidth = 3 * d
            c.drawLine(xx, yy, xx + sin(s.h) * rr, yy - cos(s.h) * rr, line)
        }
        if (lv.mode == SchoolMode.RHYTHM && r.pulse > 0) {
            line.color = Color.argb((r.pulse * 255).toInt(), 244, 185, 66); line.strokeWidth = 6 * d
            c.drawCircle(r.sx(s.x), r.sy(s.y), px * (1.6f + (1 - r.pulse) * 1.6f), line)
        }
        skier(c, r)
        if (lv.mode == SchoolMode.LEAN) leanGauge(c, r)
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, color: Int) {
        lbl.typeface = text; lbl.textSize = 13 * d
        val w = lbl.measureText(s) + 12 * d
        fill.color = Color.argb(230, 255, 255, 255); c.drawRect(x - w / 2, y - 15 * d, x + w / 2, y + 4 * d, fill)
        lbl.color = color; c.drawText(s, x, y, lbl)
    }

    private fun leanGauge(c: Canvas, r: SchoolRun) {
        val x = r.w / 2; val y = r.h - 70 * d; val rr = min(r.w * .36f, 150 * d)
        oval.set(x - rr, y - rr, x + rr, y + rr)
        line.strokeWidth = 12 * d; line.color = 0xFFEEF2F5.toInt()
        c.drawArc(oval, 180f * 1.15f, 180f * .7f, false, line)
        val ang = Math.PI * 1.5 + r.s.edge * Math.PI * .35
        line.color = if (r.s.skid > .25f) 0xFFD1342B.toInt() else 0xFF1B8A4C.toInt()
        val a0 = min(Math.PI * 1.5, ang); val a1 = max(Math.PI * 1.5, ang)
        c.drawArc(oval, Math.toDegrees(a0).toFloat(), Math.toDegrees(a1 - a0).toFloat(), false, line)
        fill.color = 0xFF13233A.toInt(); c.drawCircle(x + cos(ang).toFloat() * rr, y + sin(ang).toFloat() * rr, 9 * d, fill)
        label(c, words(Say(io.github.pini236.skiapp.R.string.game_school_canvas_tilt)), x, y - rr * .35f, 0xFF13233A.toInt())
    }

    private fun tree(c: Canvas, px: Float, x: Float, y: Float, h: Float) {
        if (y < -40 * d || y > h + 40 * d) return
        fill.color = Color.argb(31, 19, 35, 58); oval.set(x + 6 * d - px * 1.1f, y + 4 * d - px * .5f, x + 6 * d + px * 1.1f, y + 4 * d + px * .5f); c.drawOval(oval, fill)
        fill.color = 0xFF2F6B4F.toInt(); c.drawCircle(x, y, px * 1.1f, fill)
        fill.color = Color.WHITE; c.drawCircle(x - px * .3f, y - px * .3f, px * .45f, fill)
    }

    private fun person(c: Canvas, px: Float, x: Float, y: Float, color: Long, h: Float, hit: Boolean) {
        c.save(); c.translate(x, y); c.rotate(Math.toDegrees(h.toDouble()).toFloat())
        fill.color = 0xFF13233A.toInt()
        c.drawRect(-px * .28f, -px * .8f, -px * .16f, px * .8f, fill); c.drawRect(px * .16f, -px * .8f, px * .28f, px * .8f, fill)
        fill.color = if (hit) 0xFF9FB5CB.toInt() else color.toInt(); c.drawCircle(0f, 0f, px * .38f, fill)
        fill.color = 0xFFF2D2BE.toInt(); c.drawCircle(0f, -px * .1f, px * .18f, fill)
        c.restore()
    }

    private fun skier(c: Canvas, r: SchoolRun) {
        val s = r.s; val px = r.px
        c.save(); c.translate(r.sx(s.x), r.sy(s.y)); c.rotate(Math.toDegrees(s.h.toDouble()).toFloat())
        val wd = s.wedge; val len = px * 1.7f; val sp = px * (.2f + wd * .08f); val ang = wd * .32f
        val lean = if (r.lv.mode == SchoolMode.LEAN) s.edge * px * .35f else 0f
        fill.color = Color.argb(38, 19, 35, 58); oval.set(4 * d - px * .6f, 4 * d - px, 4 * d + px * .6f, 4 * d + px); c.drawOval(oval, fill)
        // in a wedge the tips (forward, up) come together and the tails spread
        for (k in intArrayOf(-1, 1)) {
            c.save(); c.translate(k * sp, 0f); c.rotate(Math.toDegrees((-k * ang).toDouble()).toFloat())
            val pressed = r.lv.mode == SchoolMode.SKIS && ((k < 0 && r.skiL) || (k > 0 && r.skiR))
            fill.color = if (pressed) 0xFFD1342B.toInt() else 0xFF13233A.toInt(); c.drawRect(-px * .09f, -len * .5f, px * .09f, len * .5f, fill)
            fill.color = 0xFFF4B942.toInt(); c.drawRect(-px * .09f, -len * .5f, px * .09f, -len * .5f + px * .12f, fill)
            c.restore()
        }
        if (s.skid > .3f && !still) { fill.color = Color.argb(242, 255, 255, 255); repeat(4) { c.drawCircle((rnd.nextFloat() - .5f) * px * 2, (rnd.nextFloat() - .2f) * px, px * .12f, fill) } }
        fill.color = 0xFFF07A2E.toInt(); oval.set(lean - px * .5f, -px * .36f, lean + px * .5f, px * .36f); c.drawOval(oval, fill)
        fill.color = 0xFF13233A.toInt(); c.drawCircle(lean * 1.3f, -px * .08f, px * .22f, fill)
        c.restore()
    }
}

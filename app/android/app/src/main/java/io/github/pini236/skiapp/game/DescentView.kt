package io.github.pini236.skiapp.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The descent's mountain from the side (the site's canvas): the sky and the real view from the village drifting by,
 * the run's snow with its gullies, ice and powder, your tracks, pines, markers every 25 m, the kickers with their pop
 * line, signs ahead of each surprise, the lift and the finish, rocks, khachapuri, the snowcat, the others waiting and
 * riding in your tracks, the ghost, the dog, you, the avalanche behind, the wind and the fog on the ridge, and the run's
 * profile across the top. One look, a bright winter day, in both themes. A finger anywhere is the button.
 */
@SuppressLint("ViewConstructor")
class DescentView(context: Context, private val still: Boolean) : View(context) {
    var game: DescentGame? = null
        set(v) { field = v; lastFrame = 0L; zoom = 0f; invalidate() }
    /** A course to show behind the menu, still. */
    var preview: Course? = null
        set(v) { field = v; invalidate() }
    var onFrame: ((DescentGame) -> Unit)? = null
    /** Where the run's profile goes: below the crew's squares, in pixels from the top. */
    var mapTop = 0f
    var text: Typeface? = null
    var display: Typeface? = null
    /** The words on the snow: the signs, the lift and the finish, and the ghost's label. */
    var words: (Int, Array<Any>) -> String = { _, _ -> "" }
    /** A name for the character in coat i, or none (the app's games have no names for now, Pini 4.10.2026; the idea
     *  of the group's names is in docs/ROADMAP.md). */
    var names: (Int) -> String? = { null }

    private val d = resources.displayMetrics.density
    @Volatile private var pano: Bitmap? = null
    init {
        Thread {
            pano = runCatching { context.assets.open("pano/pano-noon.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            postInvalidate()
        }.apply { isDaemon = true }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); g.press() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> g.release()
        }
        return true
    }

    private var lastFrame = 0L
    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) .016f else min(.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        val g = game
        if (g != null) { g.step(dt); onFrame?.invoke(g) }
        draw(c, g)
        if (g != null && g.state != DescentGame.State.END) postInvalidateOnAnimation()
    }

    // ---------- paints and buffers, made once ----------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val img = Paint(Paint.FILTER_BITMAP_FLAG)
    private val font = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF(); private val src = Rect(); private val dst = RectF()
    private val m = Matrix()
    private val skyShader = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(0xFF4F90D2.toInt(), 0xFFD2E4F3.toInt()), floatArrayOf(0f, .6f), Shader.TileMode.CLAMP)
    private val hazeShader = LinearGradient(0f, 0f, 0f, 1f, 0x00E2ECF6, 0xFFE2ECF6.toInt(), Shader.TileMode.CLAMP)
    private val snowShader = LinearGradient(0f, 0f, 0f, 1f, 0xFFFFFFFF.toInt(), 0xFFC9D8E7.toInt(), Shader.TileMode.CLAMP)
    private val avaShader = LinearGradient(0f, 0f, 1f, 0f, intArrayOf(0xFFFFFFFF.toInt(), 0xF2F0F6FC.toInt(), 0x00F0F6FC), floatArrayOf(0f, .8f, 1f), Shader.TileMode.CLAMP)
    private val fogShader = LinearGradient(0f, 0f, 1f, 0f, 0x00ECF2F8, 0xFFECF2F8.toInt(), Shader.TileMode.CLAMP)
    private var xs = FloatArray(256)
    private val shakeRnd = Random(9)
    private val nf by lazy { NumberFormat.getIntegerInstance(java.util.Locale.US) }

    // the camera (the site's draw): metres to pixels
    private var W = 0f; private var H = 0f; private var zoom = 0f; private var sc = 1f; private var camX = 0f; private var camY = 0f
    private fun sx(x: Float) = (x - camX) * sc
    private fun sy(y: Float) = H * .5f - (y - camY) * sc

    private fun hash(i: Int): Float { val v = sin(i * 127.1) * 43758.5; return (v - floor(v)).toFloat() }

    private fun draw(cv: Canvas, g: DescentGame?) {
        W = width.toFloat(); H = height.toFloat()
        val c = g?.c ?: preview
        val px = g?.x ?: 40f
        val py = if (g != null) g.y else c?.ground(40f) ?: 0f
        val ps = g?.s ?: 0f
        val hNow = if (c != null) max(0f, py - c.ground(px)) else 0f
        val want = min(W, H * .75f) / (14 + ps * .6f + min(hNow, 12f))
        zoom = if (zoom > 0) zoom + (want - zoom) * .06f else want
        sc = zoom * (1 - (g?.zoomKick ?: 0f))
        camX = px - W * .27f / sc; camY = py + min(hNow, 12f) * .35f + H * .1f / sc
        val t = g?.t ?: 0f
        val fog = if (c != null) ((c.realH(px) - 2950) / 400).coerceIn(0f, .55f) else 0f
        // sky and the real view from the village, drifting slowly
        m.setScale(1f, max(1f, H)); skyShader.setLocalMatrix(m); fill.shader = skyShader; cv.drawRect(0f, 0f, W, H, fill); fill.shader = null
        pano?.let { b ->
            val ph = max(H * .7f, W * .42f * b.height / b.width); val pw = ph * b.width / b.height; val top = H * .78f - ph
            val scroll = px * .6f * d; var k = floor(scroll / pw).toInt()
            src.set(0, 0, b.width, b.height)
            while (k * pw - scroll < W) { // mirrored tiles hide the seam
                val x = k * pw - scroll
                cv.save(); if (k % 2 != 0) { cv.translate(x + pw, 0f); cv.scale(-1f, 1f); dst.set(0f, top, pw, top + ph) } else dst.set(x, top, x + pw, top + ph)
                cv.drawBitmap(b, src, dst, img); cv.restore(); k++
            }
            m.setScale(1f, ph * .28f); m.postTranslate(0f, top + ph * .72f); hazeShader.setLocalMatrix(m); fill.shader = hazeShader
            cv.drawRect(0f, top + ph * .72f, W, top + ph, fill); fill.shader = null
            fill.color = 0xFFE2ECF6.toInt(); cv.drawRect(0f, top + ph - 1, W, H, fill)
        }
        if (fog > 0) { fill.color = 0xFFECF2F8.toInt(); fill.alpha = (min(1f, fog * 1.2f) * 255).toInt(); cv.drawRect(0f, 0f, W, H, fill); fill.alpha = 255 }
        if (c == null) return

        cv.save()
        if (g != null && g.shake > 0) cv.translate((shakeRnd.nextFloat() - .5f) * g.shake * d, (shakeRnd.nextFloat() - .5f) * g.shake * d)
        val x0 = camX - 3; val x1 = camX + W / sc + 3
        // the surface, with the gully walls exactly at their edges
        var n = 0
        fun add(v: Float) { if (n == xs.size) xs = xs.copyOf(n * 2); xs[n++] = v }
        var xx = x0; while (xx <= x1) { add(xx); xx += 1f }
        for (gp in c.gaps) if (gp.x1 > x0 && gp.x0 < x1) { add(gp.x0); add(gp.x0 + .001f); add(gp.x1 - .001f); add(gp.x1) }
        java.util.Arrays.sort(xs, 0, n)
        fun gy(x: Float) = if (c.inGap(x) != null) c.baseGround(x) - 7 else c.baseGround(x)
        path.reset(); path.moveTo(sx(x0), H + 10)
        for (i in 0 until n) path.lineTo(sx(xs[i]), sy(gy(xs[i])))
        path.lineTo(sx(x1), H + 10); path.close()
        m.setScale(1f, H * .7f); m.postTranslate(0f, H * .3f); snowShader.setLocalMatrix(m); fill.shader = snowShader; cv.drawPath(path, fill); fill.shader = null
        // gully: a dark cut with icy water at the bottom
        for (gp in c.gaps) {
            if (gp.x1 < x0 || gp.x0 > x1) continue
            val a = gp.x0; val b = gp.x1
            path.reset(); path.moveTo(sx(a), sy(c.baseGround(a))); path.lineTo(sx(a), sy(c.baseGround(a) - 7)); path.lineTo(sx(b), sy(c.baseGround(b) - 7)); path.lineTo(sx(b), sy(c.baseGround(b))); path.close()
            fill.color = 0xFF6F88A3.toInt(); cv.drawPath(path, fill)
            val wy = sy(c.baseGround(a) - 5.8f)
            fill.color = 0xFF3E6E9C.toInt(); cv.drawRect(sx(a), wy, sx(a) + (b - a) * sc, wy + 1.2f * sc, fill)
            line.color = 0x99FFFFFF.toInt(); line.strokeWidth = 1.5f * d
            for (q in 0 until 4) { val qx = a + ((q * 2 + t * 1.5f) % (b - a)); cv.drawLine(sx(qx), wy + 2 * d, sx(qx + .8f), wy + 2 * d, line) }
            fill.color = 0xFFFFFFFF.toInt(); cv.drawRect(sx(a) - 2 * d, sy(c.baseGround(a)), sx(a) + 2 * d, sy(c.baseGround(a)) + sc * 1.2f, fill)
            cv.drawRect(sx(b) - 2 * d, sy(c.baseGround(b)), sx(b) + 2 * d, sy(c.baseGround(b)) + sc * 1.2f, fill)
        }
        // ice sheen and powder pillows on the surface
        for (z in c.zones) {
            if (z.x1 < x0 || z.x0 > x1 || z.kind == Course.WIND) continue
            if (z.kind == Course.ICE) {
                val lw = max(3 * d, sc * .35f); line.color = 0xCC78C8F0.toInt(); line.strokeWidth = lw
                path.reset(); var x = z.x0; var first = true
                while (x <= z.x1) { val X = sx(x); val Y = sy(c.ground(x)) + lw / 2; if (first) path.moveTo(X, Y) else path.lineTo(X, Y); first = false; x += 1 }
                cv.drawPath(path, line)
                line.color = 0xE6FFFFFF.toInt(); line.strokeWidth = 1.5f * d
                x = z.x0 + 2; while (x < z.x1) { val X = sx(x); val Y = sy(c.ground(x)) + sc * .2f; cv.drawLine(X, Y, X + sc * 1.2f, Y - sc * .12f, line); x += 5 }
            } else {
                fill.color = 0xFFFFFFFF.toInt(); line.color = 0x808FA7C0.toInt(); line.strokeWidth = d
                var x = z.x0
                while (x <= z.x1) {
                    val r = sc * (.55f + .25f * hash((x * 3).roundToInt())); val X = sx(x); val Y = sy(c.ground(x))
                    cv.drawCircle(X, Y, r, fill); oval.set(X - r, Y - r, X + r, Y + r); cv.drawArc(oval, 180f, 180f, false, line); x += 1.6f
                }
            }
        }
        line.color = 0xFF8FA7C0.toInt(); line.strokeWidth = 2 * d
        path.reset(); var first = true
        for (i in 0 until n) { val x = xs[i]; if (c.inGap(x) != null) { first = true; continue }; val X = sx(x); val Y = sy(gy(x)); if (first) path.moveTo(X, Y) else path.lineTo(X, Y); first = false }
        cv.drawPath(path, line)
        // corduroy just under the surface, and your ski tracks carved into it
        line.color = 0x4D8FA7C0; line.strokeWidth = d
        for (k in 1 until 4) {
            path.reset(); first = true; var x = x0
            while (x <= x1) { if (c.inGap(x) != null) { first = true; x += 3; continue }; val X = sx(x); val Y = sy(c.baseGround(x)) + k * 7 * d; if (first) path.moveTo(X, Y) else path.lineTo(X, Y); first = false; x += 3 }
            cv.drawPath(path, line)
        }
        if (g != null) {
            line.color = 0x8C5F7DA0.toInt(); line.strokeWidth = max(d, sc * .09f)
            for (tr in g.tracks) {
                val a = max(tr[0], x0); val b = min(tr[1], x1); if (b <= a) continue
                for (off in TRACK_OFF) {
                    path.reset(); var x = a; path.moveTo(sx(x), sy(c.ground(x)) + off * sc)
                    while (x <= b) { path.lineTo(sx(x), sy(c.ground(x)) + off * sc); x += .8f }
                    path.lineTo(sx(b), sy(c.ground(b)) + off * sc); cv.drawPath(path, line)
                }
            }
        }
        // pines in front of the piste
        treeRow(cv, c, 8f, 8, 4.5f, 7.5f, 0xFF2F5A4A.toInt(), 1.15f)
        treeRow(cv, c, 12f, 13, 6f, 10f, 0xFF1F4436.toInt(), 1.3f)
        // piste markers every 25 m in the run's colour, kicker flags, warning signs
        val rc = runColor(c.color).toInt()
        var mk = ceil(x0 / 25) * 25
        while (mk < x1) { if (c.inGap(mk) == null) { val X = sx(mk); val Y = sy(c.ground(mk)); fill.color = rc; cv.drawRect(X - 2 * d, Y - sc * 1.6f, X + 2 * d, Y, fill); fill.color = 0xFFFFFFFF.toInt(); cv.drawRect(X - 2 * d, Y - sc * 1.6f, X + 2 * d, Y - sc * 1.25f, fill) }; mk += 25 }
        for (k in c.kickers) {
            if (k.x < x0 || k.x > x1) continue
            val X = sx(k.x); val Y = sy(c.ground(k.x))
            line.color = 0xE6F4B942.toInt(); line.strokeWidth = max(3 * d, sc * .3f); cv.drawLine(sx(k.x - 3.2f), sy(c.ground(k.x - 3.2f)) + d, X, Y + d, line) // the pop zone
            path.reset(); path.moveTo(X, Y - sc * 2.4f); path.lineTo(X + sc, Y - sc * 2.05f); path.lineTo(X, Y - sc * 1.7f); path.close()
            fill.color = if (k.gap) 0xFFD1342B.toInt() else 0xFFF08A3C.toInt(); cv.drawPath(path, fill)
            line.color = INK; line.strokeWidth = 1.5f * d; cv.drawLine(X, Y, X, Y - sc * 2.4f, line)
        }
        font.typeface = text; font.textSize = max(11 * d, (sc * .75f).roundToInt().toFloat())
        for (s in c.signs) sign(cv, c, s.x, words(s.text, emptyArray()), false)
        sign(cv, c, 6f, words(io.github.pini236.skiapp.R.string.game_descent_sign_start, arrayOf(nf.format(c.top))), true)
        // the lift and the finish arch
        val lx = c.len - 3
        if (lx > x0 - 20 && lx < x1 + 20) {
            val X = sx(lx); val Y = sy(c.ground(lx))
            fill.color = INK; cv.drawRect(X - 3 * d, Y - sc * 9, X + 3 * d, Y, fill); cv.drawRect(X - sc * 2.2f, Y - sc * 9, X + sc * 2.2f, Y - sc * 8.2f, fill)
            line.color = INK; line.strokeWidth = 2 * d; cv.drawLine(X, Y - sc * 8.6f, X + W, Y - sc * 8.6f - W * .35f, line)
            font.typeface = display; font.textSize = (sc * 1.6f).roundToInt().toFloat(); font.color = 0xFFF4B942.toInt()
            cv.drawText(if (c.lift.isNotEmpty()) words(io.github.pini236.skiapp.R.string.game_descent_lift_name, arrayOf(c.lift)) else words(io.github.pini236.skiapp.R.string.game_descent_finish_sign, emptyArray()), X, Y - sc * 10, font)
            val fx = c.len - 8; val FX = sx(fx); val FY = sy(c.ground(fx))
            fill.color = 0xFFD1342B.toInt(); cv.drawRect(FX - sc * 3, FY - sc * 4.2f, FX + sc * 3, FY - sc * 3.3f, fill)
            fill.color = INK; cv.drawRect(FX - sc * 3, FY - sc * 4.2f, FX - sc * 3 + 3 * d, FY, fill); cv.drawRect(FX + sc * 3 - 3 * d, FY - sc * 4.2f, FX + sc * 3, FY, fill)
            font.typeface = text; font.textSize = (sc * .8f).roundToInt().toFloat(); font.color = 0xFFFFFFFF.toInt()
            cv.drawText(words(io.github.pini236.skiapp.R.string.game_descent_finish_sign, emptyArray()), FX, FY - sc * 3.5f, font)
        }
        // the world in metres, y up
        cv.save(); cv.translate(0f, H * .5f); cv.scale(sc, -sc); cv.translate(-camX, -camY)
        line.strokeCap = Paint.Cap.ROUND
        for (r in c.rocks) {
            if (r.x < x0 - 3 || r.x > x1 + 3) continue
            cv.save(); cv.translate(r.x, c.ground(r.x)); cv.rotate(deg(c.slope(r.x)))
            path.reset(); path.moveTo(-.9f, 0f); path.lineTo(-.6f, r.h * .8f); path.lineTo(-.1f, r.h); path.lineTo(.5f, r.h * .85f); path.lineTo(.9f, 0f); path.close()
            fill.color = 0xFF5B6675.toInt(); cv.drawPath(path, fill)
            path.reset(); path.moveTo(-.62f, r.h * .78f); path.lineTo(-.1f, r.h + .08f); path.lineTo(.52f, r.h * .86f); path.lineTo(.2f, r.h * .7f); path.lineTo(-.3f, r.h * .72f); path.close()
            fill.color = 0xFFFFFFFF.toInt(); cv.drawPath(path, fill); cv.restore()
        }
        // khachapuri: a boat of bread with an egg
        for (k in c.coins) {
            if (k.got || k.x < x0 - 2 || k.x > x1 + 2) continue
            val y = c.baseGround(k.x) + k.dy + sin(t * 4 + k.x) * .12f
            fill.color = 0xFFC9822E.toInt(); oval.set(k.x - .62f, y - .3f, k.x + .62f, y + .3f); cv.drawOval(oval, fill)
            fill.color = 0xFFF7E3A1.toInt(); oval.set(k.x - .42f, y + .04f - .18f, k.x + .42f, y + .04f + .18f); cv.drawOval(oval, fill)
            fill.color = 0xFFF4A11D.toInt(); cv.drawCircle(k.x, y + .06f, .11f, fill)
        }
        // the snowcat grooming uphill
        for (k in c.cats) {
            if (k.x < x0 - 6 || k.x > x1 + 6) continue
            cv.save(); cv.translate(k.x, c.ground(k.x)); cv.rotate(deg(c.slope(k.x)))
            fill.color = 0xFF2A2F38.toInt(); oval.set(-2.2f, 0f, 2.2f, .8f); cv.drawRoundRect(oval, .35f, .35f, fill)
            fill.color = 0xFFFFFFFF.toInt(); var w = -1.8f; while (w <= 1.81f) { cv.drawCircle(w, .4f, .22f, fill); w += .9f }
            fill.color = 0xFFD1342B.toInt(); cv.drawRect(-1.9f, .8f, 1.5f, 1.6f, fill); cv.drawRect(-.3f, 1.6f, 1.4f, 2.5f, fill)
            fill.color = 0xFFBFE3F5.toInt(); cv.drawRect(0f, 1.75f, 1.2f, 2.35f, fill)
            path.reset(); path.moveTo(-2.3f, .1f); path.lineTo(-3f, .1f); path.lineTo(-3.1f, 1.3f); path.lineTo(-2.3f, 1.1f); path.close(); fill.color = 0xFF8F99A6.toInt(); cv.drawPath(path, fill) // blade
            fill.color = if (sin(t * 8) > 0) 0xFFF4B942.toInt() else 0xFF8A6A1F.toInt(); cv.drawRect(.4f, 2.5f, .8f, 2.72f, fill) // beacon
            cv.restore()
        }
        // the others waiting, facing you and waving
        for (sp in c.spots) {
            if (sp.lost || sp.got || sp.x < x0 - 5 || sp.x > x1 + 5) continue
            val a = c.slope(sp.x); skier(cv, sp.x, c.ground(sp.x), a, a * .4f, COATS[sp.coat].color.toInt(), wave = t + sp.coat)
        }
        if (g != null) {
            // the best run, as a ghost
            val gi = g.ghostAt(t)
            if (gi >= 0) { val r = g.ghost!!.r; cv.saveLayerAlpha(null, 89); skier(cv, r[gi + 1], r[gi + 2], r[gi + 3], r[gi + 3] * .5f, COATS[g.ghost.who.coerceIn(0, 5)].color.toInt()); cv.restore() }
            // the crew in your tracks, SPACING metres apart; they jump and flip where you did
            g.crew.forEachIndexed { k, rd ->
                val p = g.histAt(g.x - DescentGame.SPACING * (k + 1))
                var x = p.x; var y = if (p.air) p.y else c.ground(p.x); val ang = if (p.air) p.ang else c.slope(p.x)
                val sp = c.spots.firstOrNull { it.coat == rd.coat }
                if (sp != null && sp.joinT > 0) { val f = sp.joinT / .7f; x = x * (1 - f) + sp.x * f; y = y * (1 - f) + c.ground(sp.x) * f }
                if (p.air) shadow(cv, c, x, y - c.ground(x))
                skier(cv, x, y, ang, if (p.air) ang else ang * .4f, COATS[rd.coat].color.toInt(), crouch = p.crouch * .8f, scarf = t + k)
            }
            // the dog
            if (g.dogOn) {
                val dx = g.x - DescentGame.SPACING * (g.crew.size + 1) + sin(g.dogT * 1.3f) * 1.5f; val dy = c.ground(dx) + abs(sin(g.dogT * 12)) * .35f
                cv.save(); cv.translate(dx, dy); cv.rotate(deg(c.slope(dx)))
                fill.color = 0xFFC99A5B.toInt(); oval.set(-.6f, .27f, .6f, .83f); cv.drawOval(oval, fill); oval.set(.37f, .62f, .87f, 1.02f); cv.drawOval(oval, fill)
                fill.color = 0xFF8C6434.toInt(); cv.save(); cv.rotate(deg(-.4f), .62f, 1f); oval.set(.54f, .87f, .7f, 1.13f); cv.drawOval(oval, fill); cv.restore()
                line.color = 0xFFC99A5B.toInt(); line.strokeWidth = .13f; val ph = g.dogT * 14
                cv.drawLine(-.4f, .4f, -.4f + sin(ph) * .2f, 0f, line); cv.drawLine(.35f, .4f, .35f - sin(ph) * .2f, 0f, line); cv.drawLine(-.6f, .6f, -.9f, .85f + sin(ph) * .1f, line)
                fill.color = INK; cv.drawCircle(.72f, .86f, .04f, fill); cv.restore()
            }
            // you
            val a = c.slope(g.x); if (g.air) shadow(cv, c, g.x, hNow)
            val onSnow = !g.air && g.crash <= 0
            skier(cv, g.x, g.y, if (onSnow) a else g.ang, if (onSnow) a * .4f else g.ang, COATS[g.me].color.toInt(), crouch = g.crouch, scarf = t, squash = g.squash)
            for (p in g.bits) {
                fill.color = p.col; fill.alpha = (p.life.coerceIn(0f, 1f) * 255).toInt()
                if (p.conf) cv.drawRect(p.x - .12f, p.y - .08f, p.x + .12f, p.y + .08f, fill) else cv.drawCircle(p.x, p.y, p.r, fill)
            }
            fill.alpha = 255
        }
        line.strokeCap = Paint.Cap.BUTT
        cv.restore()
        // labels, in pixels: the ghost's, and the names of those waiting when they have one
        if (g != null) { val gi = g.ghostAt(t); if (gi >= 0) label(cv, sx(g.ghost!!.r[gi + 1]), sy(g.ghost.r[gi + 2]), words(io.github.pini236.skiapp.R.string.game_descent_ghost_label, emptyArray()), 0xFFFFFFFF.toInt(), 89) }
        for (sp in c.spots) { if (sp.lost || sp.got || sp.x < x0 - 5 || sp.x > x1 + 5) continue; names(sp.coat)?.let { label(cv, sx(sp.x), sy(c.ground(sp.x)), it, 0xFFF4B942.toInt(), 255) } }
        // the avalanche: a wall of powder behind you
        if (g != null && g.avalancheOn) {
            val X = sx(g.avaX)
            if (X > -200 * d) {
                m.setScale(300 * d, 1f); m.postTranslate(X - 260 * d, 0f); avaShader.setLocalMatrix(m); fill.shader = avaShader
                path.reset(); path.moveTo(-10f, H); var yy = H
                while (yy > H * .08f) { path.lineTo(X + (sin(yy / d * .05f + t * 6) * 16 + sin(yy / d * .013f + t * 2) * 24) * d, yy); yy -= 18 * d }
                path.lineTo(-10f, H * .08f); path.close(); cv.drawPath(path, fill); fill.shader = null
            }
        }
        cv.restore()
        if (g == null) return
        // ridge wind: streaks blowing up the run, against you
        if (c.zoneAt(g.x)?.kind == Course.WIND && !still) {
            line.color = 0xBFFFFFFF.toInt(); line.strokeWidth = 2 * d
            for (i in 0 until 22) { val y = (i * 53 * d) % H; val x = ((i * 211 * d + g.windT * 1100 * d) % (W + 200 * d)) - 100 * d; cv.drawLine(x, y, x - 60 * d, y + 4 * d, line) }
        }
        if (fog > 0) { m.setScale(W * .45f, 1f); m.postTranslate(W * .55f, 0f); fogShader.setLocalMatrix(m); fill.shader = fogShader; fill.alpha = (min(1f, fog * 1.4f) * 255).toInt(); cv.drawRect(0f, 0f, W, H, fill); fill.shader = null; fill.alpha = 255 }
        // speed lines when fast
        if (g.s > 18 && !still) {
            line.color = 0x8CFFFFFF.toInt(); line.strokeWidth = 2 * d
            for (i in 0 until 8) { val y = (i * 97 * d + t * 900 * d) % H; val x = (i * 151 * d + t * 1400 * d) % W; cv.drawLine(W - x, y, W - x - (40 + (g.s - 18) * 6) * d, y + 8 * d, line) }
        }
        if (g.state != DescentGame.State.END) miniMap(cv, g)
    }

    private fun deg(rad: Float) = (rad * 180 / PI).toFloat()

    private fun treeRow(cv: Canvas, c: Course, gap: Float, off: Int, hmin: Float, hmax: Float, col: Int, par: Float) {
        val cxw = camX * par
        var i = floor((cxw - 40) / gap).toInt()
        while (i < (cxw + W / sc + 40) / gap) {
            if (hash(i + off) >= .45f) {
                val wx = i * gap + hash(i * 3 + off) * gap * .8f; val gx = camX + (wx - cxw)
                if (gx >= 0 && gx <= c.len) tree(cv, sx(gx), sy(c.baseGround(gx)) + off * sc, (hmin + (hmax - hmin) * hash(i * 7 + off)) * sc, col)
            }
            i++
        }
    }
    private fun tree(cv: Canvas, X: Float, Y: Float, h: Float, col: Int) {
        fill.color = col
        for (k in 0 until 3) { val w = h * (.42f - k * .1f); val y = Y - h * (.28f + k * .24f); path.reset(); path.moveTo(X, y - h * .34f); path.lineTo(X + w, y + h * .06f); path.lineTo(X - w, y + h * .06f); path.close(); cv.drawPath(path, fill) }
        cv.drawRect(X - h * .04f, Y - h * .28f, X + h * .04f, Y, fill)
        fill.color = 0xD9FFFFFF.toInt()
        for (k in 0 until 3) { val w = h * (.42f - k * .1f); val y = Y - h * (.28f + k * .24f); path.reset(); path.moveTo(X, y - h * .34f); path.lineTo(X + w * .45f, y - h * .12f); path.lineTo(X - w * .45f, y - h * .12f); path.close(); cv.drawPath(path, fill) }
    }

    /** A warning sign on a post, the flag pointing back up the run (the start in the run's colour). */
    private fun sign(cv: Canvas, c: Course, x: Float, s: String, start: Boolean) {
        val x0 = camX - 3; val x1 = camX + W / sc + 3
        if (x < x0 - 10 || x > x1 + 10) return
        val X = sx(x); val Y = sy(c.ground(x)); font.typeface = text; font.textSize = max(11 * d, (sc * .75f).roundToInt().toFloat())
        val w = font.measureText(s) + sc * 1.2f; val hh = sc * 1.1f
        fill.color = INK; cv.drawRect(X - 2 * d, Y - sc * 3.2f, X + 2 * d, Y, fill)
        fill.color = if (start) runColor(c.color).toInt() else 0xFFF08A3C.toInt()
        path.reset(); path.moveTo(X - w / 2 - hh * .4f, Y - sc * 3.2f - hh / 2); path.lineTo(X - w / 2, Y - sc * 3.2f - hh); path.lineTo(X + w / 2, Y - sc * 3.2f - hh)
        path.lineTo(X + w / 2, Y - sc * 3.2f); path.lineTo(X - w / 2, Y - sc * 3.2f); path.close(); cv.drawPath(path, fill)
        font.color = if (start) 0xFFFFFFFF.toInt() else INK; cv.drawText(s, X, Y - sc * 3.2f - hh * .28f, font)
    }

    private fun label(cv: Canvas, X: Float, Y: Float, s: String, bg: Int, alpha: Int) {
        font.typeface = text; font.textSize = .8f * sc; font.color = INK
        val w = font.measureText(s) + .5f * sc
        fill.color = bg; fill.alpha = alpha; cv.drawRect(X - w / 2, Y - 4.3f * sc, X + w / 2, Y - 3.25f * sc, fill); fill.alpha = 255
        font.alpha = alpha; cv.drawText(s, X, Y - 3.5f * sc, font); font.alpha = 255
    }

    private fun shadow(cv: Canvas, c: Course, x: Float, hgt: Float) {
        if (c.inGap(x) != null) return
        val s = max(.2f, 1 - hgt / 10)
        cv.save(); cv.translate(x, c.ground(x) + .02f); cv.rotate(deg(c.slope(x))); cv.scale(1f, .28f)
        fill.color = 0xFF283C5A.toInt(); fill.alpha = (.25f * s * 255).toInt(); cv.drawCircle(0f, 0f, 1.3f * s, fill); fill.alpha = 255; cv.restore()
    }

    /**
     * A skier (the site's skier()): skis along the snow, the body upright over the boots and leaning into the fall line;
     * in the air everything turns together. A coat in [col], a helmet and goggles; [wave] waves, [scarf] flutters.
     */
    private fun skier(cv: Canvas, x: Float, y: Float, skiAng: Float, bodyAng: Float, col: Int, crouch: Float = 0f, wave: Float? = null, scarf: Float? = null, squash: Float = 0f) {
        val h = 1 - .32f * crouch; val k = 1.6f
        cv.save(); cv.translate(x, y)
        if (squash != 0f) { cv.rotate(deg(skiAng)); cv.scale(1 - squash * .18f, 1 + squash * .3f); cv.rotate(-deg(skiAng)) }
        cv.save(); cv.rotate(deg(skiAng)); cv.scale(k, k)
        line.color = INK; line.strokeWidth = .13f
        path.reset(); path.moveTo(-.95f, .04f); path.lineTo(1f, .04f); path.quadTo(1.22f, .04f, 1.28f, .2f); cv.drawPath(path, line)
        cv.restore()
        cv.rotate(deg(bodyAng)); cv.scale(k, k)
        val kneeX = .18f + .25f * crouch; val hipY = .85f * h
        line.color = 0xFF2A3B55.toInt(); line.strokeWidth = .2f; line.strokeJoin = Paint.Join.ROUND
        path.reset(); path.moveTo(0f, .1f); path.lineTo(kneeX, .45f * h); path.lineTo(-.05f, hipY); cv.drawPath(path, line) // legs
        fill.color = 0xFF1A2A40.toInt(); cv.drawRect(-.14f, .04f, .16f, .18f, fill) // boots
        val torso = .35f * crouch // tuck folds the torso forward
        cv.save(); cv.translate(-.05f, hipY); cv.rotate(-deg(torso))
        if (scarf != null) {
            line.color = col; line.strokeWidth = .12f; path.reset(); path.moveTo(.05f, .72f)
            for (s in 1..4) path.lineTo(.05f - s * .22f, .72f + sin(scarf * 14 + s * 1.3f) * .07f * s - s * .02f)
            cv.drawPath(path, line)
        }
        fill.color = col; path.reset(); path.moveTo(-.2f, -.05f); path.quadTo(-.26f, .7f, .1f, .78f); path.quadTo(.4f, .66f, .3f, -.05f); path.close(); cv.drawPath(path, fill)
        fill.color = 0x59FFFFFF; cv.drawRect(-.2f, .25f, .3f, .32f, fill) // jacket stripe
        line.color = col; line.strokeWidth = .17f
        if (wave != null) {
            val wv = sin(wave * 9) * .5f
            path.reset(); path.moveTo(.08f, .6f); path.lineTo(.35f, .95f + wv * .2f); path.lineTo(.5f + wv * .15f, 1.25f); cv.drawPath(path, line)
        } else {
            cv.drawLine(.08f, .58f, .5f, .3f - .1f * crouch, line)
            line.color = 0xFF3A4556.toInt(); line.strokeWidth = .05f; cv.drawLine(.5f, .3f - .1f * crouch, -.25f - .3f * crouch, -hipY + .08f, line) // arm and pole
        }
        fill.color = 0xFFF4F7FB.toInt(); cv.drawCircle(.12f, 1f, .25f, fill); line.color = INK; line.strokeWidth = .06f; cv.drawCircle(.12f, 1f, .25f, line) // helmet
        fill.color = INK; cv.drawRect(.16f, .93f, .41f, 1.03f, fill); fill.color = col; cv.drawRect(.18f, .95f, .38f, 1f, fill) // goggles
        cv.restore()
        line.strokeJoin = Paint.Join.MITER
        cv.restore()
    }

    /** The run's real profile across the top, with what is coming. */
    private fun miniMap(cv: Canvas, g: DescentGame) {
        val c = g.c; val top = mapTop; val h = 26 * d; val left = 12 * d; val w = W - 24 * d; val n = c.h.size
        fun px(x: Float) = left + w * (x / c.len)
        fun py(v: Float) = top + h * (1 - (v - c.bot) / max(1, c.top - c.bot))
        path.reset(); path.moveTo(left, top + h); var i = 0; while (i < n) { path.lineTo(px(i * 5f), py(c.h[i])); i += 2 }; path.lineTo(left + w, top + h); path.close()
        fill.color = 0x6113233A; cv.drawPath(path, fill)
        cv.save(); cv.clipRect(left, top - 4 * d, px(g.x), top + h + 4 * d); fill.color = 0xD9F4B942.toInt(); cv.drawPath(path, fill); cv.restore()
        fun mark(x: Float, col: Int, s: Float) { fill.color = col; cv.drawRect(px(x) - s / 2, py(c.realH(x)) - s - 2 * d, px(x) + s / 2, py(c.realH(x)) - 2 * d, fill) }
        for (gp in c.gaps) mark(gp.x0, 0xFFD1342B.toInt(), 5 * d)
        for (k in c.cats) mark(k.x, 0xFFD1342B.toInt(), 6 * d)
        for (sp in c.spots) if (!sp.got && !sp.lost) mark(sp.x, COATS[sp.coat].color.toInt(), 6 * d)
        if (g.avalancheOn && g.avaX > 0) { fill.color = 0xFFFFFFFF.toInt(); cv.drawRect(px(g.avaX) - 2 * d, top - 3 * d, px(g.avaX) + 2 * d, top + h + 3 * d, fill) }
        fill.color = COATS[g.me].color.toInt(); cv.drawCircle(px(g.x), py(c.realH(g.x)) - d, 5 * d, fill)
        line.color = 0xFFFFFFFF.toInt(); line.strokeWidth = 2 * d; cv.drawCircle(px(g.x), py(c.realH(g.x)) - d, 5 * d, line)
    }

    companion object { val INK = 0xFF13233A.toInt(); private val TRACK_OFF = floatArrayOf(.16f, .4f) }
}

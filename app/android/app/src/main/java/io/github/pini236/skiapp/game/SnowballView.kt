package io.github.pini236.skiapp.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import io.github.pini236.skiapp.game.SnowballFight.Companion.FLIGHT
import io.github.pini236.skiapp.game.SnowballFight.Companion.G
import io.github.pini236.skiapp.game.SnowballFight.Companion.HAND_Z
import io.github.pini236.skiapp.game.SnowballFight.Companion.WALL
import io.github.pini236.skiapp.game.SnowballFight.Companion.ZF
import io.github.pini236.skiapp.game.SnowballFight.Companion.ZO
import io.github.pini236.skiapp.game.SnowballFight.Companion.ZW
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * The snowball fight's field (the site's canvas), drawn far to near from behind your own wall: the sky and the real
 * view from the village (the wide noon picture), the snow between the walls, the characters rising from behind their
 * wall, its flag in the wind, the balls with their trails and shadows, hats in the air, your wall close in front, your
 * hand with the next ball, the aim, and snow on your face. One look, a bright winter afternoon, in both themes. With no
 * fight (the menu) only the sky, the view, the snow and your wall.
 */
@SuppressLint("ViewConstructor")
class SnowballView(context: Context, private val still: Boolean) : View(context) {
    var fight: SnowballFight? = null
        set(v) { field = v; lastFrame = 0L; v?.place(width.toFloat(), height.toFloat(), d); invalidate() }
    /** Each frame, after the fight moved. */
    var onFrame: ((SnowballFight) -> Unit)? = null

    private val d = resources.displayMetrics.density
    @Volatile private var pano: Bitmap? = null

    init {
        // the wide view, decoded off the main thread (site/img/pano, packed with the app)
        Thread {
            pano = runCatching { context.assets.open("pano/pano-wide-noon.webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            postInvalidate()
        }.apply { isDaemon = true }.start()
    }

    // the screen's projection when there is no fight (the menu): as at the start of one
    private var f0 = 600f; private var y00 = 300f
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        f0 = min(w * 1.75f, h * 1.15f); y00 = h * .42f
        fight?.place(w.toFloat(), h.toFloat(), d)
        sky.shader = LinearGradient(0f, 0f, 0f, y00 + 80 * d, 0xFF4F8FCF.toInt(), 0xFFD6E6F4.toInt(), Shader.TileMode.CLAMP)
    }

    // ---------- touch: down stands you up to aim, up throws ----------
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = fight ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); g.down(e.x, e.y) }
            MotionEvent.ACTION_MOVE -> g.move(e.x, e.y)
            MotionEvent.ACTION_UP -> g.up()
            MotionEvent.ACTION_CANCEL -> g.cancel()
        }
        return true
    }

    // ---------- drawing ----------
    private var lastFrame = 0L
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sky = Paint()
    private val img = Paint(Paint.FILTER_BITMAP_FLAG)
    private val field = Paint().apply { shader = LinearGradient(0f, 0f, 0f, 1f, 0xFFE9F1F8.toInt(), 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP) }
    private val wallFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, 1f, 0xFFFFFFFF.toInt(), 0xFFC9D8E7.toInt(), Shader.TileMode.CLAMP) }
    private val m = Matrix()
    private val path = Path()
    private val oval = RectF()
    private val src = Rect(); private val dst = RectF()
    private val dash = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    private val rnd = Random(3)

    // the projection (the site's P): x to the side, y up, z away, from the eye at camX, camH
    private var W = 0f; private var H = 0f; private var f = 600f; private var y0 = 300f; private var camX = 0f; private var camH = .98f
    private var pX = 0f; private var pY = 0f; private var pS = 0f
    private fun p(x: Float, y: Float, z: Float) { pS = f / max(.05f, z); pX = W / 2 + (x - camX) * pS; pY = y0 - (y - camH) * pS }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) .016f else min(.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        val g = fight
        W = width.toFloat(); H = height.toFloat()
        if (g != null) {
            if (g.w != W || g.h != H) g.place(W, H, d)
            g.update(dt)
            onFrame?.invoke(g)
            f = g.f; y0 = g.y0; camX = g.camX; camH = g.camH
        } else { f = f0; y0 = y00; camX = 0f; camH = .98f }
        draw(c, g)
        if (g != null && (g.state != SnowballFight.State.END || g.hats.any { it.y > 0 } || g.bits.isNotEmpty())) postInvalidateOnAnimation()
    }

    private fun draw(c: Canvas, g: SnowballFight?) {
        c.save()
        if (g != null && g.shake > 0) c.translate((rnd.nextFloat() * 2 - 1) * g.shake * d, (rnd.nextFloat() * 2 - 1) * g.shake * d)
        c.drawRect(-20 * d, -20 * d, W + 20 * d, H + 20 * d, sky)
        // the real view from the village, far away on the horizon
        p(0f, 0f, 400f); val hz = pY
        pano?.let { b ->
            // the wide view, once, always wider than the screen: no tiling, no seams
            val ar = b.width.toFloat() / b.height
            var ph = min(H * .5f, W * .95f); var pw = ph * ar
            if (pw < W * 1.25f) { pw = W * 1.25f; ph = pw / ar }
            val px = max(W - pw, min(0f, W / 2 - pw / 2 - camX * 20 * d))
            src.set(0, 0, b.width, b.height); dst.set(px, hz - ph * .9f, px + pw, hz - ph * .9f + ph)
            c.drawBitmap(b, src, dst, img)
        }
        // the snowfield between the walls
        m.setScale(1f, max(1f, H - hz)); m.postTranslate(0f, hz); field.shader.setLocalMatrix(m)
        c.drawRect(-20 * d, hz - 1, W + 20 * d, H, field)
        line.color = 0x409FB5CB; line.strokeWidth = d; line.pathEffect = null
        var z = 3f; while (z < ZF) { p(0f, 0f, z); c.drawLine(0f, pY, W, pY, line); z += 2.5f } // drifts, for depth
        if (g != null) {
            // ball shadows on the snow, so you can read how far a ball is
            fill.color = 0x2E13233A
            for (b in g.balls) { if (b.z < 1) continue; p(b.x, 0f, b.z); oval.set(pX - .14f * pS, pY - .05f * pS, pX + .14f * pS, pY + .05f * pS); c.drawOval(oval, fill) }
            // the characters, then their wall in front of them; nothing shows below the snow
            p(0f, 0f, ZF)
            c.save(); c.clipRect(-20 * d, -20 * d, W + 20 * d, pY)
            for (o in g.foes) foe(c, g, o)
            c.restore()
            farWall(c, g)
            for (o in g.hats) { p(o.x, o.y, o.z); c.save(); c.translate(pX, pY); c.rotate(Math.toDegrees(o.r.toDouble()).toFloat()); hat(c, 0f, 0f, COATS[o.coat].color.toInt(), pS); c.restore() }
            // snow bits, then the balls and their trails
            fill.color = 0xFFFFFFFF.toInt()
            for (q in g.bits) {
                if (q.z < .32f) continue
                p(q.x, q.y, q.z); fill.alpha = (min(1f, q.life * 2) * 255).toInt(); c.drawCircle(pX, pY, max(d, q.r * pS), fill)
            }
            fill.alpha = 255
            line.color = 0x8CFFFFFF.toInt(); line.strokeCap = Paint.Cap.ROUND
            for (b in g.balls) {
                if (b.z < .32f) continue
                for (i in 1 until b.tn) {
                    p(b.trail[i * 3 - 3], b.trail[i * 3 - 2], b.trail[i * 3 - 1]); val x1 = pX; val y1 = pY; line.strokeWidth = .12f * pS
                    p(b.trail[i * 3], b.trail[i * 3 + 1], b.trail[i * 3 + 2]); c.drawLine(x1, y1, pX, pY, line)
                }
                if (b.z > ZW || b.mine) { p(b.x, b.y, b.z); ball(c, pX, pY, .11f * pS) }
            }
            line.strokeCap = Paint.Cap.BUTT
        } else farWall(c, null)
        // your wall, close in front of you
        myWall(c)
        if (g != null) {
            // incoming balls that cleared your wall fly past your face
            for (b in g.balls) if (!b.mine && b.z <= ZW && b.z > .3f) { p(b.x, b.y, b.z); ball(c, pX, pY, .11f * pS) }
            // your hand with the next ball, while you are up
            if (camH > 1.25f) {
                val k = min(1f, (camH - 1.25f) / .45f); val hx = W - 56 * d; val hy = H - 130 * d * k + (if (g.reload > 0) 60 * d else 0f)
                fill.color = COATS[g.me].color.toInt()
                path.reset(); path.moveTo(hx - 22 * d, hy + 14 * d); path.lineTo(hx + 16 * d, hy + 18 * d); path.lineTo(W + 30 * d, H + 40 * d); path.lineTo(W - 90 * d, H + 40 * d); path.close()
                c.drawPath(path, fill)
                fill.color = INK; c.drawCircle(hx - 2 * d, hy + 10 * d, 20 * d, fill)
                if (g.reload <= 0) ball(c, hx - 2 * d, hy - 6 * d, 20 * d)
            }
            // the aim: a ring where the ball lands without wind, where the wind will push it, and the arc on the way
            val a = g.aim
            if (a != null && g.state == SnowballFight.State.PLAY) {
                val tx = g.ringX(a); val ty = g.ringY(a); val up = camH > 1.5f && g.reload <= 0
                val alpha = if (up) 255 else 89
                p(tx, ty, ZO); val rx = pX; val ry = pY; val s = pS
                line.color = 0xFFD1342B.toInt(); line.alpha = alpha; line.strokeWidth = 3 * d
                c.drawCircle(rx, ry, .3f * s, line)
                c.drawLine(rx - .42f * s, ry, rx + .42f * s, ry, line); c.drawLine(rx, ry - .42f * s, rx, ry + .42f * s, line)
                if (abs(g.wind) > .2f) {
                    p(tx + .5f * g.wind * .5f * FLIGHT * FLIGHT, ty, ZO)
                    line.color = 0x8013233A.toInt(); line.alpha = (128 * alpha / 255f).toInt(); line.strokeWidth = d; line.pathEffect = dash
                    c.drawCircle(pX, ry, .3f * s, line); line.pathEffect = null
                }
                val x0 = .32f; val yy = camH - .3f; val z0 = HAND_Z
                fill.color = 0x8013233A.toInt(); fill.alpha = (128 * alpha / 255f).toInt()
                for (k in 1 until 12) {
                    val tt = k / 12f * FLIGHT
                    val x = x0 + (tx - x0) * tt / FLIGHT; val zz = z0 + (ZO - z0) * tt / FLIGHT
                    val y = yy + ((ty - yy + .5f * G * FLIGHT * FLIGHT) / FLIGHT) * tt - .5f * G * tt * tt
                    if (zz < ZW + .2f) continue
                    p(x, y, zz); c.drawCircle(pX, pY, max(1.5f * d, .03f * pS), fill)
                }
                fill.alpha = 255; line.alpha = 255
            }
        }
        c.restore()
        if (g == null) return
        // snow on your face: white blobs that slide down and fade
        for (s in g.splats) {
            val a = min(1f, s.life / .6f); val slide = s.max - s.life; val yy = s.y + slide * 22 * d
            fill.color = 0xFFFFFFFF.toInt(); fill.alpha = (a * .92f * 255).toInt()
            c.drawCircle(s.x, yy, s.r * .62f, fill)
            for (i in 0 until 7) {
                val ang = s.seed + i * .9f; val dd = s.r * (.45f + .25f * sin(s.seed * 3 + i))
                c.drawCircle(s.x + cos(ang) * dd, yy + sin(ang) * dd, s.r * (.22f + .12f * cos(i + s.seed)), fill)
            }
            oval.set(s.x - s.r * .12f, yy, s.x + s.r * .12f, yy + slide * 36 * d + s.r * .4f); c.drawRoundRect(oval, s.r * .12f, s.r * .12f, fill)
            fill.color = 0xB3C9D8E7.toInt(); fill.alpha = (a * .92f * .7f * 255).toInt()
            c.drawCircle(s.x + s.r * .15f, yy + s.r * .18f, s.r * .3f, fill)
        }
        fill.alpha = 255
        // wind streaks
        if (abs(g.wind) > 2.5f && !still && g.state == SnowballFight.State.PLAY) {
            line.color = 0xA6FFFFFF.toInt(); line.strokeWidth = 2 * d
            val span = W + 120 * d
            for (i in 0 until 9) {
                val y = (i * 67 * d) % (y0 + 40 * d) + 20 * d
                val x = (((i * 173 * d + g.t * g.wind * 70 * d) % span) + span) % span - 60 * d
                c.drawLine(x, y, x + sign(g.wind) * 36 * d, y, line)
            }
        }
    }

    private fun ball(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, max(1.5f * d, r), fill)
        line.color = 0xFF9FB5CB.toInt(); line.strokeWidth = max(d, r * .12f); c.drawCircle(x, y, max(1.5f * d, r), line)
    }

    private fun farWall(c: Canvas, g: SnowballFight?) {
        val top = g?.farWall
        path.reset(); p(-6f, 0f, ZF); path.moveTo(pX, pY)
        for (i in 0 until 49) { p(-6 + i * .25f, top?.get(i) ?: (SnowballFight.FWALL + .06f * sin(i * 1.7f)), ZF); path.lineTo(pX, pY) }
        p(6f, 0f, ZF); path.lineTo(pX, pY); path.close()
        fill.color = 0xFFF7FAFD.toInt(); c.drawPath(path, fill)
        line.color = 0xFFA9BED3.toInt(); line.strokeWidth = 1.5f * d; c.drawPath(path, line)
        // a little flag that shows the wind
        val wind = g?.wind ?: 0f; val t = g?.t ?: 0f
        p(5.2f, g?.farWallTop(5.2f) ?: SnowballFight.FWALL, ZF); val fx = pX; val fy = pY; val s = pS
        line.color = 0xFF4B5A6F.toInt(); line.strokeWidth = 2 * d; c.drawLine(fx, fy, fx, fy - 1.3f * s, line)
        val fl = (wind / 4).coerceIn(-1f, 1f); val sg = if (fl == 0f) 1f else sign(fl)
        path.reset(); path.moveTo(fx, fy - 1.3f * s); path.lineTo(fx + (.2f + .5f * abs(fl)) * s * sg, fy - (1.2f + .04f * sin(t * 12)) * s); path.lineTo(fx, fy - 1.05f * s); path.close()
        fill.color = 0xFFD1342B.toInt(); c.drawPath(path, fill)
    }

    private fun wallTop(x: Float) = WALL + .02f * sin(x * 3.1f) + .012f * sin(x * 7.3f)
    private fun myWall(c: Canvas) {
        path.reset(); path.moveTo(-20 * d, H + 20 * d); p(-4f, wallTop(-4f), ZW); path.lineTo(-20 * d, pY)
        var x = -4f; while (x <= 4.0001f) { p(x, wallTop(x), ZW); path.lineTo(pX, pY); x += .1f }
        path.lineTo(W + 20 * d, pY); path.lineTo(W + 20 * d, H + 20 * d); path.close()
        p(0f, WALL, ZW); m.setScale(1f, max(1f, H - pY)); m.postTranslate(0f, pY); wallFill.shader.setLocalMatrix(m)
        c.drawPath(path, wallFill)
        line.color = 0xFFB8CADB.toInt(); line.strokeWidth = 2 * d; c.drawPath(path, line)
        // packed-snow texture
        fill.color = 0x2E9FB5CB
        for (i in 0 until 26) {
            p(-3.6f + (i * 1.37f) % 7.2f, .2f + (i * .53f) % (WALL - .35f), ZW)
            oval.set(pX - .12f * pS, pY - .05f * pS, pX + .12f * pS, pY + .05f * pS); c.drawOval(oval, fill)
        }
    }

    private fun hat(c: Canvas, x: Float, y: Float, col: Int, s: Float) = drawHat(c, fill, oval, x, y, col, s)

    /** A character facing you, rising from behind the far wall (the site's foe()). */
    private fun foe(c: Canvas, g: SnowballFight, o: SnowballFight.Foe) {
        if (o.hp <= 0 && o.up < .05f) return
        p(o.x, g.base(o), ZO)
        drawCharacter(c, fill, line, oval, pX, pY, pS, COATS[o.coat].color.toInt(), sin(g.t * 30) * o.wob * .12f,
            hit = o.hit > 0, arm = if (o.throwT > 0) -1.1f else if (o.phase == SnowballFight.AIM) 1.2f else .3f, holding = o.phase == SnowballFight.AIM,
            out = o.hp <= 0, hat = o.hat, minBall = 1.5f * d)
    }

    companion object {
        const val INK = 0xFF13233A.toInt()

        /** A woolly hat in [col] with a white band and a pompom, [s] pixels to the metre (the site's hat()). */
        fun drawHat(c: Canvas, fill: Paint, oval: RectF, x: Float, y: Float, col: Int, s: Float) {
            fill.color = col; oval.set(x - .2f * s, y - .16f * s, x + .2f * s, y + .16f * s); c.drawArc(oval, 180f, 180f, true, fill)
            fill.color = 0xFFFFFFFF.toInt(); c.drawRect(x - .21f * s, y - .02f * s, x + .21f * s, y + .05f * s, fill)
            c.drawCircle(x, y - .19f * s, .06f * s, fill)
        }

        /**
         * A character facing you, feet at ([x], [y]), [s] pixels to the metre: legs, a coat in [col] (white for a moment
         * when hit), the throwing arm at [arm] (with a ball while [holding]), a face, and the hat. The menu's coats too.
         */
        fun drawCharacter(c: Canvas, fill: Paint, line: Paint, oval: RectF, x: Float, y: Float, s: Float, col: Int, wob: Float,
                          hit: Boolean = false, arm: Float = .3f, holding: Boolean = false, out: Boolean = false, hat: Boolean = true, minBall: Float = 1.5f) {
            c.save(); c.translate(x, y); c.rotate(Math.toDegrees(wob.toDouble()).toFloat())
            fill.color = 0xFF2A3B55.toInt(); c.drawRect(-.2f * s, -.55f * s, -.05f * s, 0f, fill); c.drawRect(.05f * s, -.55f * s, .2f * s, 0f, fill)
            fill.color = if (hit) 0xFFFFFFFF.toInt() else col; c.drawRect(-.28f * s, -1.3f * s, .28f * s, -.5f * s, fill)
            line.color = col; line.strokeWidth = .13f * s; line.strokeCap = Paint.Cap.ROUND; line.pathEffect = null
            c.drawLine(-.25f * s, -1.15f * s, -.25f * s - cos(arm) * .1f * s, -1.15f * s - sin(arm) * .45f * s, line)
            line.strokeCap = Paint.Cap.BUTT
            if (holding) {
                val r = max(minBall, .11f * s)
                fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(-.27f * s, -1.65f * s, r, fill)
                line.color = 0xFF9FB5CB.toInt(); line.strokeWidth = max(1f, r * .12f); c.drawCircle(-.27f * s, -1.65f * s, r, line)
            }
            val hy = -1.62f * s
            fill.color = 0xFFF2D2BE.toInt(); c.drawCircle(0f, hy, .22f * s, fill)
            fill.color = INK; c.drawRect(-.09f * s, hy - .03f * s, -.04f * s, hy + .02f * s, fill); c.drawRect(.05f * s, hy - .03f * s, .1f * s, hy + .02f * s, fill)
            if (out) { line.color = INK; line.strokeWidth = .03f * s; c.drawLine(-.1f * s, hy + .1f * s, .1f * s, hy + .1f * s, line) }
            if (hat) drawHat(c, fill, oval, 0f, hy - .12f * s, col, s)
            c.restore()
        }
    }
}

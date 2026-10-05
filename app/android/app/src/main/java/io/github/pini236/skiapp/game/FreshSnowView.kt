package io.github.pini236.skiapp.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import io.github.pini236.skiapp.fx.Haptics
import io.github.pini236.skiapp.fx.Synth
import io.github.pini236.skiapp.fx.Synth.Filter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fresh snow (13.6), the site's game on the phone: touch the snow with a finger, a boot, skis, a ball or the snowcat,
 * each kind of snow with its own sound; or smash the frozen lake by the village, plate by plate. Several fingers at once.
 * The sizes are the site's, in dp; it draws only while something moves.
 */
@SuppressLint("ViewConstructor")
class FreshSnowView(context: Context, private val synth: Synth, private val haptics: Haptics, private val still: Boolean) : View(context) {
    enum class Tool { HAND, BOOT, SKI, BALL, CAT }
    interface Listener {
        /** The number on screen: tracks in the snow, or the lake broken, in percent. */
        fun onStat(percent: Int)
        fun onTouched()
        /** A new longest chain of plates on the lake. */
        fun onChain(n: Int, label: Boolean)
    }

    var listener: Listener? = null
    private val d = resources.displayMetrics.density
    private val rnd = Random.Default

    var lake = false
        set(v) { field = v; if (v && cells.isEmpty() && width > 0) initLake(); report(); invalidate() }
    var tool = Tool.HAND
    var kind = SnowKind.POWDER
        // the new kind applies to new touches only; the tracks already made stay (X-5: it used to wipe the field)
        set(v) { field = v; field2?.let { it.kind = v; dirty = true; invalidate() } }
    /** The chain's words ("Chain ×3"), in the app's language and font. */
    var chainText: (Int) -> String = { "×$it" }
    var display: Typeface? = null

    // ---------- the snow ----------
    private val cell = 3 * d
    private var field2: SnowField? = null
    private var bmp: Bitmap? = null
    private var px = IntArray(0)
    private var dirty = true
    private val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private class Finger { var lx = 0f; var ly = 0f; var has = false; var acc = 99f }
    private val fingers = HashMap<Int, Finger>()
    private var stepSide = 1
    private var lastGrain = 0L
    private var catX = 0f; private var catY = 0f; private var catAng = 0f; private var catT = 0f
    private var falling = 0f
    private class Flake(var x: Float, var y: Float, val v: Float, val r: Float, var s: Float)
    private val flakes = ArrayList<Flake>()
    private var areaT = 0f
    private var lastStat = -1

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val gw = ceil(w / cell).toInt(); val gh = ceil(h / cell).toInt()
        if (gw <= 0 || gh <= 0) return
        field2 = SnowField(gw, gh).also { it.kind = kind }
        bmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        px = IntArray(gw * gh); dirty = true
        if (lake || cells.isNotEmpty()) initLake()
        report()
    }

    /** New snow: it falls and slowly fills every track back in (the site's freshSnow). */
    fun newSnow() {
        if (still) { field2?.fresh(); dirty = true; report(); invalidate(); return }
        falling = 2.4f
        repeat(90) { flakes += Flake(rnd.nextFloat() * width, -rnd.nextFloat() * height, (40 + rnd.nextFloat() * 60) * d, (1 + rnd.nextFloat() * 2.5f) * d, rnd.nextFloat() * 6) }
        invalidate()
    }

    private fun stroke(f: Finger, x: Float, y: Float) {
        val gx = x / cell; val gy = y / cell
        if (!f.has) { f.has = true; f.lx = gx; f.ly = gy; stamp(gx, gy, 0f, true); return }
        val dx = gx - f.lx; val dy = gy - f.ly; val dist = hypot(dx, dy)
        if (dist < .5f) return
        val ang = atan2(dy, dx)
        if (tool == Tool.BOOT) {
            f.acc += dist
            if (f.acc > 30) {
                f.acc = 0f; stepSide = -stepSide
                stamp(gx - sin(ang) * 6 * stepSide, gy + cos(ang) * 6 * stepSide, ang + PI.toFloat() / 2, true)
            }
        } else {
            val steps = ceil(dist / 1.2f).toInt()
            for (s in 1..steps) stamp(f.lx + dx * s / steps, f.ly + dy * s / steps, ang, s == steps)
        }
        f.lx = gx; f.ly = gy
    }

    private fun stamp(gx: Float, gy: Float, ang: Float, sound: Boolean) {
        val f = field2 ?: return
        var m = 0f
        when (tool) {
            Tool.HAND -> m = f.press(gx, gy, 6f, 6f, 0f, 1.3f)
            Tool.BALL -> m = f.press(gx, gy, 13f, 13f, 0f, 1.7f)
            Tool.BOOT -> { m = f.press(gx, gy, 8f, 15f, ang, 1.5f, SnowField::boot); grains(10f, 1f) }
            Tool.CAT -> { m = f.groom(gx, gy, ang); catX = gx; catY = gy; catAng = ang; catT = 1.2f; dirty = true; if (sound) catSound(m); return }
            Tool.SKI -> for (s in intArrayOf(-1, 1)) m += f.press(gx - sin(ang) * 7 * s, gy + cos(ang) * 7 * s, 2.2f, 2.2f, 0f, 1f)
        }
        dirty = true
        if (sound && tool != Tool.BOOT) grains(min(4f, 1 + m * .6f), .5f)
    }

    /** Grains of filtered noise, different for every kind of snow (the site's grains()), and a buzz on the hard ones. */
    private fun grains(n0: Float, vol: Float) {
        val now = SystemClock.uptimeMillis(); if (now - lastGrain < 28) return; lastGrain = now
        val n = n0.roundToInt()
        var t = 0.0
        repeat(n) {
            when (kind) {
                SnowKind.POWDER -> {
                    synth.grain(t, .05 + rnd.nextDouble() * .05, .25f * vol, Filter.LOW, 900 + rnd.nextDouble() * 900, .7)
                    synth.grain(t, .03, .08f * vol, Filter.BAND, 2600 + rnd.nextDouble() * 1500, 1.2)
                }
                SnowKind.CRUST -> {
                    synth.grain(t, .012 + rnd.nextDouble() * .015, .55f * vol, Filter.HIGH, 2500 + rnd.nextDouble() * 2500, .8)
                    if (rnd.nextFloat() < .4f) synth.grain(t, .06, .3f * vol, Filter.LOW, 260.0, 1.0)
                }
                SnowKind.WET -> {
                    synth.grain(t, .04, .2f * vol, Filter.LOW, 600.0, 1.0)
                    if (rnd.nextFloat() < .5f) { val f0 = 700 + rnd.nextDouble() * 500; synth.tone(t, f0, f0 * 1.4, .07, .05f * vol, saw = true, band = f0 * 1.6, q = 6.0) }
                }
            }
            t += .012 + rnd.nextDouble() * .018
        }
        if (kind == SnowKind.CRUST) haptics.tick(.35f) else if (kind == SnowKind.WET) haptics.tick(.2f)
    }

    private fun catSound(m: Float) {
        val now = SystemClock.uptimeMillis(); if (now - lastGrain < 60) return; lastGrain = now
        synth.grain(0.0, .09, .22f, Filter.LOW, 160 + rnd.nextDouble() * 60, 1.2)
        synth.grain(.02, .06, .08f + min(.15f, m * .01f), Filter.BAND, 700 + rnd.nextDouble() * 400, .8)
    }

    private val catPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun drawCat(c: Canvas) {
        if (catT <= 0) return
        c.save(); c.translate(catX * cell, catY * cell); c.rotate(Math.toDegrees(catAng.toDouble()).toFloat())
        val a = (min(1f, catT * 2) * 255).toInt()
        fun r(col: Int, x: Float, y: Float, w: Float, h: Float) { catPaint.color = col; catPaint.alpha = (Color.alpha(col) * a / 255); c.drawRect(x * d, y * d, (x + w) * d, (y + h) * d, catPaint) }
        r(Color.argb(46, 19, 35, 58), -46f, -34f, 78f, 72f)
        r(0xFF2A3B55.toInt(), -44f, -36f, 62f, 14f); r(0xFF2A3B55.toInt(), -44f, 22f, 62f, 14f) // tracks
        r(0xFFD1342B.toInt(), -40f, -24f, 58f, 48f) // body
        r(0xFF9FC4E6.toInt(), 2f, -18f, 14f, 36f) // cabin glass
        r(0xFFF4B942.toInt(), 20f, -74f, 8f, 148f) // the blade in front
        r(0xFF4B5A6F.toInt(), -62f, -72f, 14f, 144f) // the tiller behind
        c.restore()
    }

    // ---------- the frozen lake: ice in plates, cracks that spread, and plates that give way into the water ----------
    private val g2 = 2 * d
    private var lw = 0; private var lh = 0
    private var cid = IntArray(0)
    private class Plate(val x: Float, val y: Float, var hp: Float) {
        var broken = false; val nb = HashSet<Int>()
        var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = 0; var y1 = 0
    }
    private var cells = ArrayList<Plate>()
    private var ice: Bitmap? = null
    private var ix: Canvas? = null
    private class Shard(val spr: Bitmap, val x: Float, val y: Float, val w: Float, val h: Float, val rot: Float) { var t = 0f }
    private val shards = ArrayList<Shard>()
    private class Ripple(val x: Float, val y: Float) { var r = 0f; var a = .6f }
    private val ripples = ArrayList<Ripple>()
    private class Chip(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val r: Float, val water: Boolean)
    private val chips = ArrayList<Chip>()
    private class Pending(val at: Long, val plate: Int, val depth: Int)
    private val pending = ArrayList<Pending>()
    private var broken = 0
    private var chain = 0; private var chainT = 0f; var bestChain = 0
    private var bigT = 0f; private var bigS = ""
    private var refreezeT = 0f
    private var lakeLast: Finger? = null

    /** The plates: a Voronoi pattern over the lake, each with its strength, its neighbours and its box. */
    private fun initLake() {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        lw = ceil(w / g2).toInt(); lh = ceil(h / g2).toInt()
        val n = max(60, ((w / d) * (h / d) / 2600).roundToInt())
        val cols = max(1, sqrt(n * w.toFloat() / h).roundToInt()); val rows = ceil(n / cols.toFloat()).toInt()
        val sx = FloatArray(rows * cols); val sy = FloatArray(rows * cols)
        for (r in 0 until rows) for (c in 0 until cols) { val k = r * cols + c; sx[k] = (c + .2f + rnd.nextFloat() * .6f) / cols * lw; sy[k] = (r + .2f + rnd.nextFloat() * .6f) / rows * lh }
        cells = ArrayList((0 until rows * cols).map { Plate(sx[it] * g2, sy[it] * g2, 1.4f + rnd.nextFloat() * 1.3f) })
        cid = IntArray(lw * lh)
        for (y in 0 until lh) for (x in 0 until lw) {
            var b = 0; var bd = Float.MAX_VALUE
            val r0 = (y.toFloat() / lh * rows).toInt(); val c0 = (x.toFloat() / lw * cols).toInt()
            for (r in max(0, r0 - 2)..min(rows - 1, r0 + 2)) for (c in max(0, c0 - 2)..min(cols - 1, c0 + 2)) {
                val k = r * cols + c; val dx = sx[k] - x; val dy = sy[k] - y; val dd = dx * dx + dy * dy
                if (dd < bd) { bd = dd; b = k }
            }
            cid[y * lw + x] = b
            val p = cells[b]
            if (x < p.x0) p.x0 = x; if (y < p.y0) p.y0 = y; if (x > p.x1) p.x1 = x; if (y > p.y1) p.y1 = y
        }
        for (y in 0 until lh - 1) for (x in 0 until lw - 1) {
            val a = cid[y * lw + x]; val r = cid[y * lw + x + 1]; val dn = cid[(y + 1) * lw + x]
            if (a != r) { cells[a].nb += r; cells[r].nb += a }
            if (a != dn) { cells[a].nb += dn; cells[dn].nb += a }
        }
        ice?.recycle()
        ice = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { ix = Canvas(it) }
        paintIce(w.toFloat(), h.toFloat())
        broken = 0; shards.clear(); ripples.clear(); chips.clear(); pending.clear()
    }

    private fun paintIce(w: Float, h: Float) {
        val c = ix ?: return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFFB9D9EE.toInt(), 0xFF8DBBD9.toInt(), 0xFFA9CDE6.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        // dark streaks where the ice froze clear, frost blooms, and trapped air bubbles
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.maskFilter = BlurMaskFilter(16 * d, BlurMaskFilter.Blur.NORMAL)
        repeat(14) {
            p.color = Color.argb(((.08f + rnd.nextFloat() * .1f) * 255).toInt(), 40, 90, 130); p.strokeWidth = (20 + rnd.nextFloat() * 60) * d
            val path = Path(); var x = rnd.nextFloat() * w; var y = rnd.nextFloat() * h; path.moveTo(x, y)
            repeat(5) { x += (rnd.nextFloat() - .5f) * w * .5f; y += (rnd.nextFloat() - .5f) * h * .3f; path.lineTo(x, y) }
            c.drawPath(path, p)
        }
        p.maskFilter = null; p.style = Paint.Style.FILL
        repeat(70) {
            val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h; val r = (10 + rnd.nextFloat() * 50) * d
            p.shader = RadialGradient(x, y, r, Color.argb(89, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
            c.drawRect(x - r, y - r, x + r, y + r, p)
        }
        p.shader = null
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .6f * d; color = Color.argb(64, 30, 70, 100) }
        p.color = Color.argb(140, 255, 255, 255)
        repeat(260) { val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h; val r = (.8f + rnd.nextFloat() * 3) * d; c.drawCircle(x, y, r, p); c.drawCircle(x, y, r, ring) }
        // a dusting of snow at the edges of the lake
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.argb(191, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(179, 255, 255, 255)),
            floatArrayOf(0f, .12f, .85f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
    }

    private val atop = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; xfermode = atop }

    private fun crackLine(x0: Float, y0: Float, a0: Float, len: Float, w: Float) {
        val c = ix ?: return
        val path = Path(); val shade = Path()
        var x = x0; var y = y0; var a = a0; var l = 0f
        path.moveTo(x, y); shade.moveTo(x + .8f * d, y + .8f * d)
        while (l < len) {
            val st = (4 + rnd.nextFloat() * 9) * d
            a += (rnd.nextFloat() - .5f) * .7f; x += cos(a) * st; y += sin(a) * st; l += st
            path.lineTo(x, y); shade.lineTo(x + .8f * d, y + .8f * d)
            if (rnd.nextFloat() < .08f && len - l > 20 * d) crackLine(x, y, a + (if (rnd.nextBoolean()) -1 else 1) * (.5f + rnd.nextFloat() * .6f), (len - l) * .5f, w * .7f)
        }
        crackPaint.color = Color.argb(115, 20, 55, 85); crackPaint.strokeWidth = w + 1.2f * d; c.drawPath(shade, crackPaint)
        crackPaint.color = Color.argb(235, 255, 255, 255); crackPaint.strokeWidth = w; c.drawPath(path, crackPaint)
    }

    /** A hit: cracks run out from the point, every plate nearby weakens, and a plate with nothing left gives way. */
    private fun hit(px: Float, py: Float, force: Float) {
        val c = ix ?: return
        val n = (4 + force * 5).roundToInt()
        for (i in 0 until n) crackLine(px, py, i.toFloat() / n * 2 * PI.toFloat() + rnd.nextFloat() * .5f, (30 + rnd.nextFloat() * 90) * force * d, (1.1f * force + .3f) * d)
        crackPaint.color = Color.argb(191, 255, 255, 255); crackPaint.strokeWidth = d
        var r = 12f
        while (r < 40 * force) {
            val a0 = rnd.nextFloat() * 360; c.drawArc(px - r * d, py - r * d, px + r * d, py + r * d, a0, (.6f + rnd.nextFloat() * 1.2f) * 57.3f, false, crackPaint)
            r += 10 + rnd.nextFloat() * 8
        }
        val glow = Paint().apply { xfermode = atop; shader = RadialGradient(px, py, max(1f, 14 * force * d), Color.argb(204, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP) }
        c.drawRect(px - 20 * d, py - 20 * d, px + 20 * d, py + 20 * d, glow)
        val reach = 70 * force * d
        val home = cid.getOrElse(min(lh - 1, max(0, (py / g2).toInt())) * lw + min(lw - 1, max(0, (px / g2).toInt()))) { -1 }
        var gave = 0
        val now = SystemClock.uptimeMillis()
        cells.forEachIndexed { k, p ->
            if (p.broken) return@forEachIndexed
            val dd = hypot(p.x - px, p.y - py)
            if (dd > reach && k != home) return@forEachIndexed
            p.hp -= force * (if (k == home) 1f else max(0f, 1 - dd / reach)) * 1.05f
            if (p.hp <= 0) { gave++; pending += Pending(now + (dd / d * 2.2f).toLong(), k, 0) }
        }
        crackSound(force)
        repeat((8 * force).toInt()) { chips += Chip(px, py, (rnd.nextFloat() - .5f) * 160 * d, (-60 - rnd.nextFloat() * 120) * d, .5f, (1 + rnd.nextFloat() * 2) * d, false) }
        if (gave > 0) haptics.thud(.7f) else haptics.tick(.5f)
        invalidate()
    }

    private fun breakPlate(k: Int, depth: Int) {
        val p = cells[k]; if (p.broken) return
        val src = ice ?: return
        p.broken = true; broken++
        val bx = (p.x0 * g2).toInt(); val by = (p.y0 * g2).toInt()
        val bw = min(src.width - bx, ceil((p.x1 - p.x0 + 1) * g2).toInt()); val bh = min(src.height - by, ceil((p.y1 - p.y0 + 1) * g2).toInt())
        if (bw > 0 && bh > 0) {
            // the plate's pixels leave the ice and become a piece that sinks
            val all = IntArray(bw * bh); src.getPixels(all, 0, bw, bx, by, bw, bh)
            val mine = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                val gx = min(lw - 1, ((bx + x) / g2).toInt()); val gy = min(lh - 1, ((by + y) / g2).toInt())
                if (cid[gy * lw + gx] == k) { val i = y * bw + x; mine[i] = all[i]; all[i] = 0 }
            }
            src.setPixels(all, 0, bw, bx, by, bw, bh)
            val spr = Bitmap.createBitmap(mine, bw, bh, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
            // the white broken edge of the plate
            Canvas(spr).drawRect(1.5f * d, 1.5f * d, bw - 1.5f * d, bh - 1.5f * d, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = Color.argb(230, 255, 255, 255); xfermode = atop })
            if (!still) shards += Shard(spr, bx.toFloat(), by.toFloat(), bw.toFloat(), bh.toFloat(), (rnd.nextFloat() - .5f) * .35f) else spr.recycle()
        }
        ripples += Ripple(p.x, p.y).also { it.r = 4 * d }
        val w = (p.x1 - p.x0 + 1) * g2; val h = (p.y1 - p.y0 + 1) * g2
        repeat(10) { chips += Chip(p.x + (rnd.nextFloat() - .5f) * w * .5f, p.y + (rnd.nextFloat() - .5f) * h * .5f, (rnd.nextFloat() - .5f) * 120 * d, (-40 - rnd.nextFloat() * 100) * d, .6f, (1.5f + rnd.nextFloat() * 2.5f) * d, rnd.nextBoolean()) }
        chain = if (chainT > 0) chain + 1 else 1; chainT = .5f
        if (chain > bestChain) bestChain = chain
        if (chain >= 3) { bigS = chainText(chain); bigT = 1f }
        listener?.onChain(chain, chain >= 3)
        breakSound()
        report()
        // weakened neighbours follow: the satisfying collapse
        val now = SystemClock.uptimeMillis()
        for (nb in p.nb) if (!cells[nb].broken && cells[nb].hp < .55f) pending += Pending(now + 70 + rnd.nextInt(90), nb, depth + 1)
    }

    /** The sound of lake ice: sharp crackles, a singing ping that falls, a deep thump, and water when a plate goes. */
    private fun crackSound(force: Float) {
        repeat((3 + force * 4).toInt()) { i -> synth.grain(i * .008 + rnd.nextDouble() * .01, .008 + rnd.nextDouble() * .01, .5f * force, Filter.HIGH, 3500 + rnd.nextDouble() * 3500, .7) }
        synth.grain(0.0, .08, .35f * force, Filter.LOW, 110.0, 1.4)
        synth.tone(0.0, 2400 + rnd.nextDouble() * 800, 380.0, .32, .12f * force, band = 1200.0, q = .8)
    }

    private fun breakSound() {
        val t = .01
        repeat(10) { i -> synth.grain(t + i * .018 + rnd.nextDouble() * .02, .01 + rnd.nextDouble() * .015, .45f, Filter.HIGH, 2500 + rnd.nextDouble() * 4000, .6) }
        synth.grain(t + .05, .35, .28f, Filter.LOW, 500 + rnd.nextDouble() * 200, .8)
        synth.tone(t + .1, 260.0, 90.0, .24, .12f)
    }

    /** The lake frozen again. */
    fun refreeze() {
        initLake(); refreezeT = if (still) 0f else 1f
        synth.grain(0.0, .6, .12f, Filter.HIGH, 6000.0, .5)
        report(); invalidate()
    }

    private fun report() {
        val v = if (lake) (if (cells.isEmpty()) 0 else (broken * 100f / cells.size).roundToInt()) else field2?.tracks() ?: 0
        if (v != lastStat) { lastStat = v; listener?.onStat(v) }
    }

    // ---------- touch ----------
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                listener?.onTouched()
                val x = e.getX(i); val y = e.getY(i)
                if (lake) {
                    if (e.actionMasked == MotionEvent.ACTION_DOWN && refreezeT <= 0) { lakeLast = Finger().also { it.lx = x; it.ly = y; it.acc = 0f }; hit(x, y, 1f) }
                } else {
                    val f = Finger(); fingers[e.getPointerId(i)] = f; stroke(f, x, y); invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (lake) {
                    val l = lakeLast ?: return true
                    if (refreezeT > 0) return true
                    val x = e.getX(0); val y = e.getY(0); val dd = hypot(x - l.lx, y - l.ly)
                    l.acc += dd
                    // dragging scratches a hairline crack, and every so often presses harder
                    if (dd > 3 * d) {
                        crackPaint.color = Color.argb(204, 255, 255, 255); crackPaint.strokeWidth = d
                        ix?.drawLine(l.lx, l.ly, x + (rnd.nextFloat() - .5f) * 3 * d, y + (rnd.nextFloat() - .5f) * 3 * d, crackPaint)
                        synth.grain(0.0, .01, .2f, Filter.HIGH, 4000 + rnd.nextDouble() * 2000, .7)
                        invalidate()
                    }
                    if (l.acc > 45 * d) { l.acc = 0f; hit(x, y, .45f) }
                    l.lx = x; l.ly = y
                } else {
                    for (p in 0 until e.pointerCount) {
                        val f = fingers[e.getPointerId(p)] ?: continue
                        for (hh in 0 until e.historySize) stroke(f, e.getHistoricalX(p, hh), e.getHistoricalY(p, hh))
                        stroke(f, e.getX(p), e.getY(p))
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                fingers.remove(e.getPointerId(i)); if (e.actionMasked != MotionEvent.ACTION_POINTER_UP) { fingers.clear(); lakeLast = null }
            }
        }
        return true
    }

    // ---------- drawing ----------
    private var lastFrame = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val water = Paint()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) .016f else min(.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        val more = if (lake) drawLake(c, dt, now) else drawSnow(c, dt)
        if (more) postInvalidateOnAnimation() else lastFrame = 0L
    }

    private fun drawSnow(c: Canvas, dt: Float): Boolean {
        val f = field2 ?: return false
        val b = bmp ?: return false
        if (falling > 0) {
            falling -= dt
            f.fill(1 - .25f.pow(dt)); dirty = true
            if (falling <= 0) f.fresh()
        }
        if (catT > 0) catT -= dt
        if (dirty) { f.render(px); b.setPixels(px, 0, f.gw, 0, 0, f.gw, f.gh); dirty = false }
        dst.set(0f, 0f, f.gw * cell, f.gh * cell)
        c.drawBitmap(b, null, dst, smooth)
        drawCat(c)
        if (flakes.isNotEmpty()) {
            paint.color = Color.WHITE; paint.alpha = 230
            val it = flakes.iterator()
            while (it.hasNext()) { val k = it.next(); k.y += k.v * dt; k.s += dt; k.x += sin(k.s) * .5f * d; c.drawCircle(k.x, k.y, k.r, paint); if (k.y > height + 10 * d) it.remove() }
            paint.alpha = 255
        }
        areaT += dt
        if (areaT > .4f) { areaT = 0f; report() }
        return falling > 0 || catT > 0 || flakes.isNotEmpty()
    }

    private fun drawLake(c: Canvas, dt: Float, now: Long): Boolean {
        val w = width.toFloat(); val h = height.toFloat()
        // plates waiting their turn to give way
        if (pending.isNotEmpty()) { val due = pending.filter { it.at <= now }; pending.removeAll(due.toSet()); due.forEach { breakPlate(it.plate, it.depth) } }
        chainT = max(0f, chainT - dt)
        // dark water under the ice, with slow light ripples
        water.shader = LinearGradient(0f, 0f, 0f, h, 0xFF16405C.toInt(), 0xFF0A2236.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, water)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d
        val ri = ripples.iterator()
        while (ri.hasNext()) { val r = ri.next(); r.r += dt * 40 * d; r.a -= dt * .35f; if (r.a <= 0) { ri.remove(); continue }; paint.color = Color.argb((r.a * 255).toInt(), 160, 210, 240); c.drawCircle(r.x, r.y, r.r, paint) }
        paint.style = Paint.Style.FILL
        ice?.let { val p = Paint(); if (refreezeT > 0) { refreezeT -= dt; p.alpha = (min(1f, 1 - refreezeT) * 255).toInt().coerceIn(0, 255) }; c.drawBitmap(it, 0f, 0f, p) }
        // plates sinking: they tip, darken and slide under
        val si = shards.iterator()
        while (si.hasNext()) {
            val s = si.next(); s.t += dt; val k = min(1f, s.t / 1.1f)
            if (s.t >= 1.1f) { si.remove(); s.spr.recycle(); continue }
            c.save(); c.translate(s.x + s.w / 2, s.y + s.h / 2 + k * 10 * d); c.rotate(Math.toDegrees((s.rot * k).toDouble()).toFloat()); c.scale(1 - .15f * k, 1 - .6f * k)
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = ((1 - k * .85f) * 255).toInt(); colorFilter = PorterDuffColorFilter(Color.argb((k * .8f * 255).toInt(), 10, 34, 54), PorterDuff.Mode.SRC_ATOP) }
            c.drawBitmap(s.spr, -s.w / 2, -s.h / 2, p); c.restore()
        }
        val ci = chips.iterator()
        while (ci.hasNext()) {
            val k = ci.next(); k.vy += 400 * d * dt; k.x += k.vx * dt; k.y += k.vy * dt; k.life -= dt
            if (k.life <= 0) { ci.remove(); continue }
            paint.color = if (k.water) Color.argb(230, 160, 210, 240) else Color.argb(242, 255, 255, 255); paint.alpha = (min(1f, k.life * 2) * paint.alpha).toInt()
            c.drawCircle(k.x, k.y, k.r, paint)
        }
        if (bigT > 0) {
            bigT -= dt
            val a = (min(1f, bigT * 2) * 255).toInt().coerceIn(0, 255)
            text.typeface = display; text.textSize = 52 * resources.displayMetrics.scaledDensity
            val y = h * .3f - (1 - bigT) * 30 * d
            text.style = Paint.Style.STROKE; text.strokeWidth = 4 * d; text.color = Color.argb(a * 153 / 255, 10, 34, 54); c.drawText(bigS, w / 2, y, text)
            text.style = Paint.Style.FILL; text.color = Color.argb(a, 255, 255, 255); c.drawText(bigS, w / 2, y, text)
        }
        return pending.isNotEmpty() || ripples.isNotEmpty() || shards.isNotEmpty() || chips.isNotEmpty() || bigT > 0 || refreezeT > 0
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shards.forEach { it.spr.recycle() }; shards.clear()
    }

    // ---------- for the emulator run: a stroke and a hit with no fingers ----------
    internal fun qaStroke(x0: Float, y0: Float, x1: Float, y1: Float) {
        val f = Finger(); val n = 24
        for (s in 0..n) stroke(f, x0 + (x1 - x0) * s / n, y0 + (y1 - y0) * s / n)
        invalidate()
    }

}

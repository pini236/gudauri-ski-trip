package io.github.pini236.skiapp.map

import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.util.Log
import io.github.pini236.skiapp.perf.FrameStats
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.hypot

private const val TERRAIN_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;
layout(location=3) in float aSlope;
layout(location=4) in float aShadow;
layout(location=5) in float aHi;
uniform mat4 uMvp; uniform vec3 uSun; uniform vec3 uCam; uniform float uDim; uniform vec2 uFog;
out vec3 vCol; out float vFog;
vec3 slopeCol(float d){ return d<15.0? vec3(0.247,0.659,0.373) : d<25.0? vec3(0.949,0.757,0.239) : d<30.0? vec3(0.941,0.541,0.235) : vec3(0.863,0.231,0.2); }
void main(){
  gl_Position = uMvp*vec4(aPos,1.0);
  vec3 n = normalize(aNormal);
  float sun = max(dot(n,uSun),0.0)*(1.0-0.8*aShadow);
  vec3 base = mix(aColor, slopeCol(aSlope), aHi*0.85);
  vec3 lit = base*(vec3(0.62,0.66,0.72)*(0.55+0.45*n.y) + vec3(1.0,0.96,0.90)*sun*0.62);
  lit = mix(lit, vec3(0.90,0.93,0.96), 0.55*uDim*(1.0-aHi));
  vCol = lit;
  vFog = clamp((length(aPos-uCam)-uFog.x)/(uFog.y-uFog.x),0.0,1.0);
}"""

private const val TERRAIN_FS = """#version 300 es
precision mediump float;
in vec3 vCol; in float vFog; uniform vec3 uFogCol; out vec4 o;
void main(){ o = vec4(mix(vCol,uFogCol,vFog),1.0); }"""

private const val LINE_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aPrev;
layout(location=2) in vec3 aNext;
layout(location=3) in float aSide;
layout(location=4) in vec4 aColor;
layout(location=5) in float aProg;
uniform mat4 uMvp; uniform vec2 uRes; uniform float uWidth; uniform float uBias;
out vec4 vCol; out float vProg;
void main(){
  vec4 c=uMvp*vec4(aPos,1.0), p=uMvp*vec4(aPrev,1.0), n=uMvp*vec4(aNext,1.0);
  vec2 a=c.xy/c.w*uRes*0.5, b=p.xy/p.w*uRes*0.5, e=n.xy/n.w*uRes*0.5;
  vec2 d1=a-b, d2=e-a;
  if(length(d1)<1e-4) d1=d2; if(length(d2)<1e-4) d2=d1;
  d1=normalize(d1); d2=normalize(d2);
  vec2 t=d1+d2; t = length(t)<1e-3 ? d1 : normalize(t);
  vec2 nn=vec2(-t.y,t.x); float ml=1.0/max(dot(nn,vec2(-d1.y,d1.x)),0.6);
  c.xy += nn*aSide*uWidth*ml/uRes*c.w;
  c.z -= uBias*c.w;
  gl_Position=c; vCol=aColor; vProg=aProg;
}"""

private const val LINE_FS = """#version 300 es
precision mediump float;
in vec4 vCol; in float vProg; uniform float uReveal; uniform float uAlpha; uniform vec4 uTint; out vec4 o;
void main(){ if(vProg>uReveal) discard; vec4 c = mix(vCol, vec4(uTint.rgb, vCol.a), uTint.a); o = vec4(c.rgb, c.a*uAlpha); }"""

/** A ribbon on the GPU. */
private class GpuRibbon(r: Ribbon) {
    val vao = IntArray(1); private val bufs = IntArray(2); val count = r.indices.size
    init {
        glGenVertexArrays(1, vao, 0); glBindVertexArray(vao[0])
        glGenBuffers(2, bufs, 0)
        glBindBuffer(GL_ARRAY_BUFFER, bufs[0]); glBufferData(GL_ARRAY_BUFFER, r.vertices.size * 4, floats(r.vertices), GL_STATIC_DRAW)
        val s = Ribbon.STRIDE * 4
        attr(0, 3, s, 0); attr(1, 3, s, 12); attr(2, 3, s, 24); attr(3, 1, s, 36); attr(4, 4, s, 40); attr(5, 1, s, 56)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufs[1]); glBufferData(GL_ELEMENT_ARRAY_BUFFER, r.indices.size * 4, ints(r.indices), GL_STATIC_DRAW)
        glBindVertexArray(0)
    }
    fun draw() { if (count == 0) return; glBindVertexArray(vao[0]); glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0) }
    fun release() { glDeleteBuffers(2, bufs, 0); glDeleteVertexArrays(1, vao, 0) }
}

private fun floats(a: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

private fun ints(a: IntArray): IntBuffer =
    ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(a); position(0) }

private fun attr(loc: Int, size: Int, stride: Int, offset: Int) {
    glEnableVertexAttribArray(loc); glVertexAttribPointer(loc, size, GL_FLOAT, false, stride, offset)
}

private fun program(vs: String, fs: String): Int {
    fun shader(type: Int, src: String): Int {
        val s = glCreateShader(type); glShaderSource(s, src); glCompileShader(s)
        val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("MapRenderer", glGetShaderInfoLog(s))
        return s
    }
    val p = glCreateProgram()
    glAttachShader(p, shader(GL_VERTEX_SHADER, vs)); glAttachShader(p, shader(GL_FRAGMENT_SHADER, fs)); glLinkProgram(p)
    val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
    if (ok[0] == 0) Log.e("MapRenderer", glGetProgramInfoLog(p))
    return p
}

/** What changes when a run is chosen: the glow on the snow and the run painted in slope colours. */
class Selection(val key: String, val highlight: FloatArray, val casing: Ribbon, val paint: Ribbon, val path: FloatArray)

/**
 * Draws the mountain. Runs only when something moves (render on demand), to save battery.
 * [onFrame] is called after every frame so the label overlay can follow.
 */
class MapRenderer(
    private val camera: OrbitCamera,
    private val stats: FrameStats,
    private val density: Float,
    private val onFrame: () -> Unit,
    private val requestRender: () -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var scene: MapScene? = null
    @Volatile private var pendingSelection: Selection? = null
    @Volatile private var clearSelection = false
    @Volatile private var shadowDirty = false

    private var uploaded = false
    private var terrainProg = 0; private var lineProg = 0
    private val terrainVao = IntArray(1); private val terrainBufs = IntArray(4); private var terrainCount = 0
    private var pistes = ArrayList<Pair<GpuRibbon, GpuRibbon>>(); private var lifts: GpuRibbon? = null
    private var selCasing: GpuRibbon? = null; private var selPaint: GpuRibbon? = null
    private var width = 1; private var height = 1
    private val mvp = FloatArray(16); private val eye = FloatArray(3)

    /** 0..1: how far the chosen run has been painted from the top; and how much the rest of the mountain is dimmed. */
    private var reveal = 0f; private var dim = 0f; private var dimTarget = 0f
    private var lastNs = 0L

    private var camAnim: Triple<OrbitCamera.State, OrbitCamera.State, FloatArray>? = null // from, to, [t, duration]
    @Volatile private var fly: FlyDown? = null
    @Volatile var onFlyEnded: (() -> Unit)? = null

    fun select(sel: Selection?) { if (sel == null) clearSelection = true else pendingSelection = sel; requestRender() }
    fun updateShadow() { shadowDirty = true; requestRender() }

    fun animateCamera(to: OrbitCamera.State, seconds: Float = 1.1f) {
        camAnim = Triple(camera.state(), to, floatArrayOf(0f, seconds)); requestRender()
    }

    fun startFly(path: FloatArray) { fly = FlyDown(path); camAnim = null; requestRender() }
    fun stopFly() { if (fly != null) { fly = null; onFlyEnded?.invoke() } }
    fun cancelCameraAnimation() { camAnim = null }
    val flying get() = fly != null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        terrainProg = program(TERRAIN_VS, TERRAIN_FS)
        lineProg = program(LINE_VS, LINE_FS)
        // a new GL context (first start, or the map tab came back): everything is uploaded again
        uploaded = false; pistes.clear(); lifts = null; selCasing = null; selPaint = null; dim = 0f; dimTarget = 0f
        glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE); glCullFace(GL_BACK)
        glClearColor(0.86f, 0.91f, 0.945f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h; glViewport(0, 0, w, h) }

    private fun upload(s: MapScene) {
        val m = s.mesh
        glGenVertexArrays(1, terrainVao, 0); glBindVertexArray(terrainVao[0])
        glGenBuffers(4, terrainBufs, 0)
        glBindBuffer(GL_ARRAY_BUFFER, terrainBufs[0]); glBufferData(GL_ARRAY_BUFFER, m.vertices.size * 4, floats(m.vertices), GL_STATIC_DRAW)
        val st = TerrainMesh.STRIDE * 4
        attr(0, 3, st, 0); attr(1, 3, st, 12); attr(2, 3, st, 24); attr(3, 1, st, 36)
        glBindBuffer(GL_ARRAY_BUFFER, terrainBufs[1]); glBufferData(GL_ARRAY_BUFFER, s.shadow.size * 4, floats(s.shadow), GL_DYNAMIC_DRAW)
        attr(4, 1, 4, 0)
        glBindBuffer(GL_ARRAY_BUFFER, terrainBufs[2]); glBufferData(GL_ARRAY_BUFFER, s.shadow.size * 4, floats(FloatArray(s.shadow.size)), GL_DYNAMIC_DRAW)
        attr(5, 1, 4, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, terrainBufs[3]); glBufferData(GL_ELEMENT_ARRAY_BUFFER, m.indices.size * 4, ints(m.indices), GL_STATIC_DRAW)
        glBindVertexArray(0)
        terrainCount = m.count
        for ((cas, core) in s.pisteRibbons) pistes += GpuRibbon(cas) to GpuRibbon(core)
        lifts = GpuRibbon(s.liftRibbon)
        uploaded = true
    }

    private fun setTerrainAttrib(buf: Int, data: FloatArray) {
        glBindBuffer(GL_ARRAY_BUFFER, terrainBufs[buf])
        glBufferSubData(GL_ARRAY_BUFFER, 0, data.size * 4, floats(data))
    }

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((t0 - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = t0
        val s = scene
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        if (s == null) return
        if (!uploaded) upload(s)
        if (shadowDirty) { shadowDirty = false; setTerrainAttrib(1, s.shadow) }
        pendingSelection?.let { sel ->
            pendingSelection = null
            selCasing?.release(); selPaint?.release()
            selCasing = GpuRibbon(sel.casing); selPaint = GpuRibbon(sel.paint)
            setTerrainAttrib(2, sel.highlight); reveal = 0f; dimTarget = 1f
        }
        if (clearSelection) {
            clearSelection = false
            selCasing?.release(); selPaint?.release(); selCasing = null; selPaint = null
            setTerrainAttrib(2, FloatArray(s.shadow.size)); dimTarget = 0f; stopFly()
        }

        var moving = false
        // camera: a scripted move (landing on a run, flying down it) or a fling
        fly?.let { f ->
            if (f.step(dt, camera)) moving = true else { fly = null; onFlyEnded?.invoke() }
        }
        camAnim?.let { (from, to, tt) ->
            tt[0] += dt
            val k = (tt[0] / tt[1]).coerceIn(0f, 1f)
            val e = if (k < 0.5f) 4 * k * k * k else 1 - Math.pow((-2.0 * k + 2), 3.0).toFloat() / 2
            camera.set(lerp(from, to, e))
            if (k >= 1f) camAnim = null else moving = true
        }
        if (camera.step(dt)) moving = true
        val t = s.terrain
        camera.clampTo(t.x0, t.x1, t.y0, t.y1) { x, z -> t.elev(x, z) }
        if (selPaint != null && reveal < 1f) { reveal = (reveal + dt / 1.2f).coerceAtMost(1f); moving = true }
        if (dim != dimTarget) { dim += (dimTarget - dim) * (1 - Math.exp(-6.0 * dt).toFloat()); if (kotlin.math.abs(dim - dimTarget) < 0.01f) dim = dimTarget; moving = true }

        val st = camera.state()
        OrbitCamera.mvp(st, width.toFloat() / height, mvp, eye) { x, z -> t.elev(x, z) }

        glUseProgram(terrainProg)
        glUniformMatrix4fv(glGetUniformLocation(terrainProg, "uMvp"), 1, false, mvp, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uSun"), 1, s.sunDir, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uCam"), 1, eye, 0)
        glUniform1f(glGetUniformLocation(terrainProg, "uDim"), dim)
        glUniform2f(glGetUniformLocation(terrainProg, "uFog"), st.dist * 1.2f + 6000f, st.dist * 4f + 30000f)
        glUniform3f(glGetUniformLocation(terrainProg, "uFogCol"), 0.86f, 0.91f, 0.945f)
        glBindVertexArray(terrainVao[0]); glDrawElements(GL_TRIANGLES, terrainCount, GL_UNSIGNED_INT, 0)

        glDisable(GL_CULL_FACE); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false)
        glUseProgram(lineProg)
        glUniformMatrix4fv(glGetUniformLocation(lineProg, "uMvp"), 1, false, mvp, 0)
        glUniform2f(glGetUniformLocation(lineProg, "uRes"), width.toFloat(), height.toFloat())
        val uW = glGetUniformLocation(lineProg, "uWidth"); val uB = glGetUniformLocation(lineProg, "uBias")
        val uR = glGetUniformLocation(lineProg, "uReveal"); val uA = glGetUniformLocation(lineProg, "uAlpha"); val uT = glGetUniformLocation(lineProg, "uTint")
        glUniform1f(uR, 2f); glUniform4f(uT, 0f, 0f, 0f, 0f)
        val others = 1f - 0.65f * dim
        glUniform1f(uA, others)
        glUniform1f(uW, 5.6f * density); glUniform1f(uB, 0.0006f); for (p in pistes) p.first.draw()
        glUniform1f(uW, 2.6f * density); glUniform1f(uB, 0.0008f); for (p in pistes) p.second.draw()
        glUniform1f(uW, 1.8f * density); glUniform1f(uB, 0.0010f); lifts?.draw()
        if (selPaint != null) {
            glUniform1f(uA, 1f); glUniform1f(uR, reveal)
            glUniform1f(uW, 9f * density); glUniform1f(uB, 0.0012f); selCasing?.draw()
            glUniform1f(uW, 5f * density); glUniform1f(uB, 0.0014f); selPaint?.draw()
        }
        glDepthMask(true); glDisable(GL_BLEND); glEnable(GL_CULL_FACE); glBindVertexArray(0)

        stats.onFrame(System.nanoTime(), System.nanoTime() - t0)
        onFrame()
        if (moving || fly != null) requestRender()
    }

    private fun lerp(a: OrbitCamera.State, b: OrbitCamera.State, k: Float): OrbitCamera.State {
        var dy = b.yaw - a.yaw
        while (dy > Math.PI) dy -= (2 * Math.PI).toFloat()
        while (dy < -Math.PI) dy += (2 * Math.PI).toFloat()
        return OrbitCamera.State(
            a.tx + (b.tx - a.tx) * k, a.ty + (b.ty - a.ty) * k, a.tz + (b.tz - a.tz) * k,
            a.dist * Math.pow((b.dist / a.dist).toDouble(), k.toDouble()).toFloat(),
            a.yaw + dy * k, a.pitch + (b.pitch - a.pitch) * k,
        )
    }

    companion object {
        fun runRgb(color: String) = when (color) {
            "green" -> floatArrayOf(0.106f, 0.541f, 0.298f, 1f)
            "blue" -> floatArrayOf(0.122f, 0.373f, 0.769f, 1f)
            "red" -> floatArrayOf(0.820f, 0.204f, 0.169f, 1f)
            else -> floatArrayOf(0.075f, 0.137f, 0.227f, 1f)
        }
    }
}

/** The camera flies down a run from the top: behind and above the line, looking ahead. Any touch stops it. */
class FlyDown(private val path: FloatArray) {
    private val n = path.size / 3
    private val cum = FloatArray(n).also { c -> for (i in 1 until n) c[i] = c[i - 1] + hypot(path[i * 3] - path[i * 3 - 3], path[i * 3 + 2] - path[i * 3 - 1]) }
    private var s = 0f
    private val speed = (cum[n - 1] / 26f).coerceIn(40f, 140f) // the whole run in about 26 seconds
    private var yaw = Float.NaN

    private fun at(d: Float, out: FloatArray) {
        var i = 0
        while (i < n - 2 && cum[i + 1] < d) i++
        val seg = (cum[i + 1] - cum[i]).coerceAtLeast(0.01f)
        val f = ((d - cum[i]) / seg).coerceIn(0f, 1f)
        for (j in 0..2) out[j] = path[i * 3 + j] + (path[(i + 1) * 3 + j] - path[i * 3 + j]) * f
    }

    fun step(dt: Float, cam: OrbitCamera): Boolean {
        if (n < 2) return false
        s += speed * dt
        if (s >= cum[n - 1]) return false
        val p = FloatArray(3); val a = FloatArray(3); val b = FloatArray(3)
        at(s, p); at((s - 60).coerceAtLeast(0f), a); at((s + 120).coerceAtMost(cum[n - 1]), b)
        val target = atan2(-(b[0] - a[0]), -(b[2] - a[2]))
        if (yaw.isNaN()) yaw = target
        var d = target - yaw
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        yaw += d * (1 - Math.exp(-2.5 * dt).toFloat())
        cam.set(OrbitCamera.State(p[0], p[1], p[2], 420f, yaw, 0.40f))
        return true
    }

    /** Where along the run the camera is, for the progress bar. */
    val progress get() = if (n < 2) 0f else s / cum[n - 1]
    val position get() = FloatArray(3).also { at(s, it) }
}

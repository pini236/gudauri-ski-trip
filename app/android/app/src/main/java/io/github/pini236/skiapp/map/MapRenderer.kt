package io.github.pini236.skiapp.map

import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
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

private val TERRAIN_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;
layout(location=3) in float aSlope;
layout(location=4) in float aShadow;
uniform mat4 uMvp; uniform vec3 uSun; uniform vec3 uSunCol; uniform vec3 uAmbTop; uniform vec3 uAmbGround;
uniform vec3 uCam; uniform vec2 uFog;
out vec3 vBase; out vec3 vLight; out vec2 vXZ; out float vFog;
void main(){
  gl_Position = uMvp*vec4(aPos,1.0);
  vec3 n = normalize(aNormal);
  float sun = max(dot(n,uSun),0.0)*(1.0-0.8*aShadow);
  // sky light from above and bounce from below, and the sun (or the moon), as the site's hemisphere and sun
  vBase = aColor;
  vLight = mix(uAmbGround, uAmbTop, 0.5+0.5*n.y)*1.25 + uSunCol*sun;
  vXZ = aPos.xz;
  vFog = clamp((length(aPos-uCam)-uFog.x)/(uFog.y-uFog.x),0.0,1.0);
}"""

// the ground around a chosen run in slope colours (X-3): a texture of 10 m laid on the terrain by its x and z, which
// also keeps that ground out of the dimming of the rest of the mountain
private const val TERRAIN_FS = """#version 300 es
precision highp float;
in vec3 vBase; in vec3 vLight; in vec2 vXZ; in float vFog;
uniform sampler2D uGround; uniform vec4 uGroundBox; uniform float uGroundOn;
uniform float uDim; uniform vec3 uDimCol; uniform vec3 uFogCol; out vec4 o;
void main(){
  vec2 uv = (vXZ - uGroundBox.xy)*uGroundBox.zw;
  vec4 g = (uGroundOn > 0.0 && uv.x >= 0.0 && uv.y >= 0.0 && uv.x <= 1.0 && uv.y <= 1.0) ? texture(uGround, uv) : vec4(0.0);
  float a = g.a*uGroundOn;
  vec3 lit = mix(vBase, g.rgb, a*0.85)*vLight;
  lit = mix(lit, uDimCol, 0.55*uDim*(1.0-a));
  o = vec4(mix(lit,uFogCol,vFog),1.0);
}"""

// the sky behind the mountain: the site's colours from the horizon up, and the sun and the moon where they are
private const val SKY_VS = """#version 300 es
out vec2 vNdc;
void main(){ vec2 p = vec2(gl_VertexID==1 ? 3.0 : -1.0, gl_VertexID==2 ? 3.0 : -1.0); vNdc = p; gl_Position = vec4(p, 0.999, 1.0); }"""

private const val SKY_FS = """#version 300 es
precision highp float;
in vec2 vNdc;
uniform mat4 uInv; uniform vec3 uEye; uniform vec3 uTop; uniform vec3 uBottom;
uniform vec3 uSunDir; uniform float uSunUp; uniform vec3 uMoonDir; uniform float uMoonUp; uniform float uGlow; uniform vec3 uGlowCol; uniform float uDark;
out vec4 o;
float hash(vec3 p){ return fract(sin(dot(p, vec3(12.9898,78.233,37.719)))*43758.5453); }
void main(){
  vec4 w = uInv*vec4(vNdc, 1.0, 1.0);
  vec3 d = normalize(w.xyz/w.w - uEye);
  vec3 c = mix(uBottom, uTop, smoothstep(-0.02, 0.6, d.y));
  // stars on a dark night, fixed to the sky
  if(uDark > 0.5 && d.y > 0.03){ vec3 cell = floor(d*420.0); float h = hash(cell); if(h > 0.9965) c += vec3(0.85)*(h-0.9965)/0.0035*smoothstep(0.03,0.2,d.y); }
  // the sun: a bright disc with a glow around it, warmer when low
  float s = dot(d, uSunDir);
  if(uSunUp > 0.5){
    c += uGlowCol*(0.10*pow(max(s,0.0), 8.0) + 0.45*pow(max(s,0.0), 400.0))*(0.4+uGlow);
    c = mix(c, vec3(1.0,0.97,0.90), smoothstep(0.99962, 0.99976, s));
  }
  // the moon, lit from the sun's side: its phase and the tilt of its crescent are real
  float m = dot(d, uMoonDir);
  if(uMoonUp > 0.5 && m > 0.9997){
    float sinR = 0.0245; // about 1.4°: larger than life, to be seen on a phone
    vec3 u = (d - uMoonDir*m)/sinR;
    float r2 = dot(u,u);
    if(r2 < 1.0){
      vec3 n = u - uMoonDir*sqrt(1.0-r2);
      float lit = smoothstep(-0.05, 0.12, dot(n, uSunDir));
      vec3 moon = mix(vec3(0.16,0.19,0.27), vec3(0.93,0.95,1.0), lit);
      c = mix(c, moon, smoothstep(1.0, 0.85, r2)*(uSunUp > 0.5 ? 0.55 : 1.0));
    }
  }
  if(uMoonUp > 0.5 && uSunUp < 0.5) c += vec3(0.6,0.65,0.8)*0.12*pow(max(m,0.0), 300.0);
  o = vec4(c, 1.0);
}"""

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

// vProg: 0..1 down the chosen run (painted from the top, uReveal), or metres along a line (the dashes of a closed one)
private const val LINE_FS = """#version 300 es
precision mediump float;
in vec4 vCol; in highp float vProg; uniform highp float uReveal; uniform float uAlpha; uniform vec4 uTint;
uniform highp float uDash; uniform float uOn; out vec4 o;
void main(){
  if(vProg>uReveal) discard;
  if(uDash>0.0 && fract(vProg/uDash)>uOn) discard;
  vec4 c = mix(vCol, vec4(uTint.rgb, vCol.a), uTint.a); o = vec4(c.rgb, c.a*uAlpha);
}"""

// the skier's dot during the fly-down: the ink dot of the site, a white ring and a soft halo, always on top
private const val MARK_VS = """#version 300 es
layout(location=0) in vec3 aPos;
uniform mat4 uMvp; uniform float uSize;
void main(){ gl_Position = uMvp*vec4(aPos,1.0); gl_PointSize = uSize; }"""

private const val MARK_FS = """#version 300 es
precision mediump float;
uniform float uPulse; out vec4 o;
void main(){
  float r = length(gl_PointCoord*2.0-1.0);
  if(r>1.0) discard;
  if(r<0.30) o = vec4(0.075,0.137,0.227,1.0);
  else if(r<0.42) o = vec4(1.0);
  else o = vec4(1.0,1.0,1.0,(0.55+0.2*uPulse)*(1.0-(r-0.42)/0.58));
}"""

// a lift's station, a dark dot in a white ring at a fixed size on screen (the site's station dots, A-14)
private const val STATION_FS = """#version 300 es
precision mediump float;
out vec4 o;
void main(){
  float r = length(gl_PointCoord*2.0-1.0);
  if(r>1.0) discard;
  o = r<0.58 ? vec4(0.165,0.184,0.220,1.0) : vec4(1.0);
}"""

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

/**
 * What the lift status changes on the map (S1, as the site draws it): a closed run grey and dashed, a closed lift grey
 * and dashed, and with "only what's open for me" the closed runs faded away.
 */
class StatusPaint(val closedRuns: Set<String>, val closedLifts: Set<String>, val forMe: Boolean) {
    companion object { val NONE = StatusPaint(emptySet(), emptySet(), false) }
}

/** What changes when a run is chosen: the glow on the snow and the run painted in slope colours. */
class Selection(val key: String, val ground: SlopeLayer, val casing: Ribbon, val paint: Ribbon, val path: FloatArray)

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
    private var terrainProg = 0; private var lineProg = 0; private var markProg = 0; private var skyProg = 0; private var markT = 0f
    private var stationProg = 0
    private val stationVao = IntArray(1); private val stationBuf = IntArray(1); private var stationCount = 0
    /** Each run's line width against a named run's, as on the site: a ski way 2.8 and a section without a name 2.6 to 4. */
    private val pisteW = HashMap<String, Float>()
    /** Per lift, in the order of [lifts]: out of use (drawn dashed, as the site's map does). */
    private val liftInactive = ArrayList<Boolean>()
    private val inv = FloatArray(16)
    private val terrainVao = IntArray(1); private val terrainBufs = IntArray(3); private var terrainCount = 0
    private val groundTex = IntArray(1); private var groundBox: SlopeLayer? = null
    private val pistes = ArrayList<Triple<String, GpuRibbon, GpuRibbon>>(); private val lifts = ArrayList<Pair<String, GpuRibbon>>()
    @Volatile var status: StatusPaint = StatusPaint.NONE
    /** The point of a chosen run under the finger on its elevation profile (T2): x, height, z; null when none. */
    @Volatile var marker: FloatArray? = null
    /** The map's filters: runs not drawn (by key), and the lifts off. */
    @Volatile var hidden: Set<String> = emptySet()
    @Volatile var hideLifts = false
    private var selCasing: GpuRibbon? = null; private var selPaint: GpuRibbon? = null
    private var width = 1; private var height = 1
    private val mvp = FloatArray(16); private val eye = FloatArray(3)

    /** 0..1: how far the chosen run has been painted from the top; and how much the rest of the mountain is dimmed. */
    private var reveal = 0f; private var dim = 0f; private var dimTarget = 0f
    private var lastNs = 0L

    private var camAnim: Triple<OrbitCamera.State, OrbitCamera.State, FloatArray>? = null // from, to, [t, duration]
    @Volatile private var fly: FlyDown? = null
    /** A flight ended: its token, and whether it reached the bottom (false: stopped). */
    @Volatile var onFlyEnded: ((Any, Boolean) -> Unit)? = null

    fun select(sel: Selection?) { if (sel == null) clearSelection = true else pendingSelection = sel; requestRender() }
    fun updateShadow() { shadowDirty = true; requestRender() }

    /** Reduced motion (ui/Motion.kt): the camera lands at once, the run is painted whole, the mountain dims at once. */
    @Volatile var still = false

    fun animateCamera(to: OrbitCamera.State, seconds: Float = 1.1f) {
        if (still) { camAnim = null; camera.set(to); requestRender(); return }
        camAnim = Triple(camera.state(), to, floatArrayOf(0f, seconds)); requestRender()
    }

    fun startFly(path: FloatArray, token: Any) { fly = FlyDown(path, token); camAnim = null; requestRender() }
    fun stopFly() { val f = fly ?: return; fly = null; onFlyEnded?.invoke(f.token, false) }
    fun cancelCameraAnimation() { camAnim = null }
    /** Distance along, run length, height and slope where the skier is, while flying. */
    val flyInfo: FloatArray? get() = fly?.info
    val flying get() = fly != null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        terrainProg = program(TERRAIN_VS, TERRAIN_FS)
        lineProg = program(LINE_VS, LINE_FS)
        markProg = program(MARK_VS, MARK_FS)
        stationProg = program(MARK_VS, STATION_FS)
        skyProg = program(SKY_VS, SKY_FS)
        // a new GL context (first start, or the map tab came back): everything is uploaded again
        uploaded = false; pistes.clear(); lifts.clear(); liftInactive.clear(); stationCount = 0; selCasing = null; selPaint = null; dim = 0f; dimTarget = 0f
        glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE); glCullFace(GL_BACK)
        glClearColor(0.86f, 0.91f, 0.945f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h; glViewport(0, 0, w, h) }

    private fun upload(s: MapScene) {
        val m = s.mesh
        glGenVertexArrays(1, terrainVao, 0); glBindVertexArray(terrainVao[0])
        glGenBuffers(3, terrainBufs, 0)
        glBindBuffer(GL_ARRAY_BUFFER, terrainBufs[0]); glBufferData(GL_ARRAY_BUFFER, m.vertices.size * 4, floats(m.vertices), GL_STATIC_DRAW)
        val st = TerrainMesh.STRIDE * 4
        attr(0, 3, st, 0); attr(1, 3, st, 12); attr(2, 3, st, 24); attr(3, 1, st, 36)
        glBindBuffer(GL_ARRAY_BUFFER, terrainBufs[1]); glBufferData(GL_ARRAY_BUFFER, s.shadow.size * 4, floats(s.shadow), GL_DYNAMIC_DRAW)
        attr(4, 1, 4, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, terrainBufs[2]); glBufferData(GL_ELEMENT_ARRAY_BUFFER, m.indices.size * 4, ints(m.indices), GL_STATIC_DRAW)
        glBindVertexArray(0)
        terrainCount = m.count
        glGenTextures(1, groundTex, 0); groundBox = null
        for (p in s.pisteRibbons) pistes += Triple(p.key, GpuRibbon(p.casing), GpuRibbon(p.core))
        for (p in s.runs.pistes) pisteW[p.key] = when { !p.named -> 0.65f; p.kind == "ski-way" -> 0.7f; else -> 1f }
        s.liftRibbons.forEachIndexed { i, (name, r) -> lifts += name to GpuRibbon(r); liftInactive += s.runs.lifts.getOrNull(i)?.status == "inactive" }
        // both ends of every lift's cable
        val ends = FloatArray(s.liftLines.size * 6)
        s.liftLines.forEachIndexed { i, l -> val n = l.size / 3; for (j in 0..2) { ends[i * 6 + j] = l[j]; ends[i * 6 + 3 + j] = l[(n - 1) * 3 + j] } }
        glGenVertexArrays(1, stationVao, 0); glBindVertexArray(stationVao[0])
        glGenBuffers(1, stationBuf, 0); glBindBuffer(GL_ARRAY_BUFFER, stationBuf[0]); glBufferData(GL_ARRAY_BUFFER, ends.size * 4, floats(ends), GL_STATIC_DRAW)
        attr(0, 3, 12, 0)
        glBindVertexArray(0)
        stationCount = ends.size / 3
        uploaded = true
    }

    private fun setGround(g: SlopeLayer) {
        glBindTexture(GL_TEXTURE_2D, groundTex[0])
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g.w, g.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap(g.rgba))
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        groundBox = g
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
            setGround(sel.ground); reveal = if (still) 1f else 0f; dimTarget = 1f; if (still) dim = 1f
        }
        if (clearSelection) {
            clearSelection = false
            selCasing?.release(); selPaint?.release(); selCasing = null; selPaint = null
            groundBox = null; dimTarget = 0f; if (still) dim = 0f; stopFly()
        }

        var moving = false
        // camera: a scripted move (landing on a run, flying down it) or a fling
        fly?.let { f ->
            if (f.step(dt, camera)) moving = true else { fly = null; onFlyEnded?.invoke(f.token, true) }
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

        val light = s.light
        drawSky(light)
        glUseProgram(terrainProg)
        glUniformMatrix4fv(glGetUniformLocation(terrainProg, "uMvp"), 1, false, mvp, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uSun"), 1, light.dir, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uSunCol"), 1, light.color, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uAmbTop"), 1, light.ambTop, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uAmbGround"), 1, light.ambGround, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uCam"), 1, eye, 0)
        glUniform1f(glGetUniformLocation(terrainProg, "uDim"), dim)
        val gb = groundBox
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, groundTex[0])
        glUniform1i(glGetUniformLocation(terrainProg, "uGround"), 0)
        // the ground fades in with the dimming of the rest, as the site's (opacity t*1.6)
        glUniform1f(glGetUniformLocation(terrainProg, "uGroundOn"), if (gb == null) 0f else minOf(1f, dim * 1.6f))
        if (gb != null) glUniform4f(glGetUniformLocation(terrainProg, "uGroundBox"), gb.x0, gb.z0, 1f / gb.width, 1f / gb.depth)
        glUniform2f(glGetUniformLocation(terrainProg, "uFog"), st.dist * 1.2f + 6000f, st.dist * 4f + 30000f)
        glUniform3fv(glGetUniformLocation(terrainProg, "uFogCol"), 1, light.skyBottom, 0)
        glUniform3fv(glGetUniformLocation(terrainProg, "uDimCol"), 1, light.skyBottom, 0)
        glBindVertexArray(terrainVao[0]); glDrawElements(GL_TRIANGLES, terrainCount, GL_UNSIGNED_INT, 0)

        glDisable(GL_CULL_FACE); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false)
        glUseProgram(lineProg)
        glUniformMatrix4fv(glGetUniformLocation(lineProg, "uMvp"), 1, false, mvp, 0)
        glUniform2f(glGetUniformLocation(lineProg, "uRes"), width.toFloat(), height.toFloat())
        val uW = glGetUniformLocation(lineProg, "uWidth"); val uB = glGetUniformLocation(lineProg, "uBias")
        val uR = glGetUniformLocation(lineProg, "uReveal"); val uA = glGetUniformLocation(lineProg, "uAlpha"); val uT = glGetUniformLocation(lineProg, "uTint")
        val uD = glGetUniformLocation(lineProg, "uDash"); val uOn = glGetUniformLocation(lineProg, "uOn")
        glUniform1f(uR, 1e9f); glUniform4f(uT, 0f, 0f, 0f, 0f); glUniform1f(uD, 0f)
        val others = 1f - 0.65f * dim
        // the lift status: dashes about as long on screen as the site's (6 on 5 off for a run, 4 and 4 for a lift)
        val paint = status
        val px = (2 * st.dist * kotlin.math.tan(Math.toRadians(20.0)) / height.coerceAtLeast(1)).toFloat()
        fun fade(key: String) = if (paint.forMe && key in paint.closedRuns) 0.15f else 1f
        val off = hidden
        glUniform1f(uB, 0.0006f)
        for (p in pistes) {
            if (p.first in off) continue
            glUniform1f(uW, (2.6f * (pisteW[p.first] ?: 1f) + 3f) * density); glUniform1f(uA, others * fade(p.first)); p.second.draw()
        }
        glUniform1f(uB, 0.0008f)
        for (p in pistes) {
            if (p.first in off) continue
            val shut = p.first in paint.closedRuns
            glUniform1f(uW, 2.6f * (pisteW[p.first] ?: 1f) * density)
            glUniform1f(uA, others * fade(p.first))
            if (shut) { glUniform4f(uT, 0.557f, 0.612f, 0.678f, 1f); glUniform1f(uD, 11f * density * px); glUniform1f(uOn, 6f / 11f) }
            p.third.draw()
            if (shut) { glUniform4f(uT, 0f, 0f, 0f, 0f); glUniform1f(uD, 0f) }
        }
        glUniform1f(uA, others); glUniform1f(uW, 1.8f * density); glUniform1f(uB, 0.0010f)
        if (!hideLifts) lifts.forEachIndexed { i, (name, r) ->
            val shut = name.isNotBlank() && name in paint.closedLifts
            val idle = liftInactive.getOrElse(i) { false }
            if (shut) { glUniform4f(uT, 0.604f, 0.647f, 0.702f, 1f); glUniform1f(uD, 8f * density * px); glUniform1f(uOn, 0.5f) }
            else if (idle) { glUniform1f(uD, 5f * density * px); glUniform1f(uOn, 0.4f) } // the site's 2 on, 3 off
            r.draw()
            if (shut || idle) { glUniform4f(uT, 0f, 0f, 0f, 0f); glUniform1f(uD, 0f) }
        }
        if (!hideLifts && stationCount > 0) {
            glUseProgram(stationProg)
            glUniformMatrix4fv(glGetUniformLocation(stationProg, "uMvp"), 1, false, mvp, 0)
            glUniform1f(glGetUniformLocation(stationProg, "uSize"), 9f * density)
            glBindVertexArray(stationVao[0]); glDrawArrays(GL_POINTS, 0, stationCount); glBindVertexArray(0)
            glUseProgram(lineProg)
        }
        if (selPaint != null) {
            glUniform1f(uA, 1f); glUniform1f(uR, reveal)
            glUniform1f(uW, 9f * density); glUniform1f(uB, 0.0012f); selCasing?.draw()
            glUniform1f(uW, 5f * density); glUniform1f(uB, 0.0014f); selPaint?.draw()
        }
        val dot = fly?.position ?: marker
        if (dot != null) {
            // the dot where the skier is now (flying down, or under the finger on the profile), over everything, so it
            // never hides behind the snow
            val p = dot
            markT += dt
            glDisable(GL_DEPTH_TEST); glBindVertexArray(0)
            glUseProgram(markProg)
            glUniformMatrix4fv(glGetUniformLocation(markProg, "uMvp"), 1, false, mvp, 0)
            glUniform1f(glGetUniformLocation(markProg, "uSize"), 34f * density)
            glUniform1f(glGetUniformLocation(markProg, "uPulse"), if (fly != null) 0.5f + 0.5f * kotlin.math.sin(markT * 4f) else 0.5f)
            glDisableVertexAttribArray(0); glVertexAttrib3f(0, p[0], p[1] + 6f, p[2])
            glDrawArrays(GL_POINTS, 0, 1)
            glEnable(GL_DEPTH_TEST)
        }
        glDepthMask(true); glDisable(GL_BLEND); glEnable(GL_CULL_FACE); glBindVertexArray(0)

        stats.onFrame(System.nanoTime(), System.nanoTime() - t0)
        onFrame()
        if (moving || fly != null) requestRender()
    }

    /** The sky first, behind everything: it writes no depth, so the mountain simply covers it. */
    private fun drawSky(l: Sky.Light) {
        Matrix.invertM(inv, 0, mvp, 0)
        glDisable(GL_DEPTH_TEST); glDepthMask(false)
        glUseProgram(skyProg)
        fun u(n: String) = glGetUniformLocation(skyProg, n)
        glUniformMatrix4fv(u("uInv"), 1, false, inv, 0)
        glUniform3fv(u("uEye"), 1, eye, 0)
        glUniform3fv(u("uTop"), 1, l.skyTop, 0); glUniform3fv(u("uBottom"), 1, l.skyBottom, 0)
        glUniform3fv(u("uSunDir"), 1, l.sunDir, 0); glUniform1f(u("uSunUp"), if (l.sunUp) 1f else 0f)
        glUniform3fv(u("uMoonDir"), 1, l.moonDir, 0); glUniform1f(u("uMoonUp"), if (l.moonUp) 1f else 0f)
        glUniform1f(u("uGlow"), l.glow); glUniform3fv(u("uGlowCol"), 1, l.glowColor, 0); glUniform1f(u("uDark"), if (l.dark) 1f else 0f)
        glBindVertexArray(0); glDisableVertexAttribArray(0)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glDepthMask(true); glEnable(GL_DEPTH_TEST)
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
class FlyDown(private val path: FloatArray, val token: Any = Unit) {
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
        cam.set(OrbitCamera.State(p[0], p[1], p[2], 520f, yaw, 0.48f))
        // the slope here, over 30 m of the run
        at((s - 15).coerceAtLeast(0f), a); at((s + 15).coerceAtMost(cum[n - 1]), b)
        val run = hypot(b[0] - a[0], b[2] - a[2]).coerceAtLeast(1f)
        info = floatArrayOf(s, cum[n - 1], p[1] - 4f, Math.toDegrees(kotlin.math.atan((a[1] - b[1]).coerceAtLeast(0f) / run).toDouble()).toFloat())
        return true
    }

    @Volatile var info: FloatArray = floatArrayOf(0f, cum[n - 1], path.getOrElse(1) { 0f }, 0f); private set

    /** Where along the run the camera is, for the progress bar. */
    val progress get() = if (n < 2) 0f else s / cum[n - 1]
    val position get() = FloatArray(3).also { at(s, it) }
}

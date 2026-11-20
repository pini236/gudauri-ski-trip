package io.github.pini236.skiapp.redesign

import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.util.Log
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.OrbitCamera
import io.github.pini236.skiapp.map.Ribbon
import io.github.pini236.skiapp.map.Sky
import io.github.pini236.skiapp.map.TerrainMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/*
 * Phase B of the redesign POC (decision 83, the architect's conditions of 20.11): the painted mountain as a live model.
 * The same elevation grid as today's map, drawn in the new look: the light of the hour is worked out in the shader from
 * the sun's direction and colours (map/Sky.kt), with one detail texture for the snow, the rock and the haze (not a
 * picture per hour). The runs and lifts are the map's own lines on top. Test build only.
 */

private val VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=3) in float aSlope;
layout(location=4) in float aShadow;
uniform mat4 uMvp;
out vec3 vPos; out vec3 vN; out float vSlope; out float vShadow;
void main(){ gl_Position = uMvp*vec4(aPos,1.0); vPos = aPos; vN = aNormal; vSlope = aSlope; vShadow = aShadow; }"""

// The painted look, by the hour: snow warm where the sun reaches it and blue in the shade (the sky's light), rock where
// the slope sheds the snow, banded by height, snow caught in its cracks, a glint on the snow facing the sun, and the
// haze of the valleys thickening with distance and depth. Everything comes from the uniforms of the moment.
private const val FS = """#version 300 es
precision highp float;
in vec3 vPos; in vec3 vN; in float vSlope; in float vShadow;
uniform sampler2D uDetail;
uniform vec3 uSun; uniform vec3 uSunCol; uniform vec3 uAmbTop; uniform vec3 uAmbGround;
uniform vec3 uCam; uniform vec3 uHaze; uniform vec2 uHazeK; uniform float uNight; uniform float uDim;
out vec4 o;
void main(){
  vec3 n = normalize(vN);
  vec4 big = texture(uDetail, vPos.xz/1400.0);
  vec4 mid = texture(uDetail, vPos.xz/190.0);
  vec4 fine = texture(uDetail, vPos.xz/26.0);
  float h = vPos.y;
  // rock: steep faces shed the snow; a ragged edge, never a clean line
  float rockA = smoothstep(31.0, 44.0, vSlope + (mid.b-0.5)*16.0 + (big.b-0.5)*10.0);
  float band = texture(uDetail, vec2(vPos.x/900.0 + vPos.z/1300.0, h/46.0)).g;
  vec3 rock = mix(vec3(0.30,0.285,0.28), vec3(0.50,0.47,0.44), band);
  rock = mix(rock, vec3(0.92,0.94,0.97), smoothstep(0.58, 0.82, fine.r)*0.55*(1.0-smoothstep(48.0,62.0,vSlope)));
  // the lower slopes in the valleys: snow thinner over the ground, a grey-brown under it (no forest: no source)
  float low = smoothstep(1900.0, 1550.0, h + (big.b-0.5)*220.0) * smoothstep(14.0, 26.0, vSlope);
  vec3 snow = mix(vec3(0.955,0.965,0.985), vec3(0.90,0.92,0.95), mid.r*0.6);
  vec3 base = mix(snow, rock, rockA);
  base = mix(base, vec3(0.55,0.53,0.49), low*0.55);
  // light: broad painted bands for the sun, the sky's light from above, the bounce from below
  float lam = dot(n, uSun);
  float sunT = smoothstep(-0.04, 0.55, lam) * (1.0 - 0.85*vShadow);
  vec3 sky = mix(uAmbGround, uAmbTop, 0.5 + 0.5*n.y);
  vec3 shadeTint = mix(vec3(0.80,0.88,1.06), vec3(0.75,0.80,1.0), uNight);
  vec3 lit = base * (sky*shadeTint*1.32 + uSunCol*sunT*1.15);
  // the glint of the snow in the sun
  vec3 v = normalize(uCam - vPos);
  float spec = pow(max(dot(reflect(-uSun, n), v), 0.0), 60.0) * step(0.86, fine.g) * sunT * (1.0-rockA);
  lit += uSunCol * spec * 0.9;
  // haze: with distance, thicker low in the valleys
  float dist = length(uCam - vPos);
  float depth = clamp((2900.0 - h)/1400.0, 0.0, 1.0);
  float f = 1.0 - exp(-dist*uHazeK.x*(0.55 + depth*0.9));
  lit = mix(lit, uHaze, clamp(f, 0.0, 0.82));
  lit = mix(lit, lit*vec3(0.62,0.66,0.76), uDim);
  o = vec4(lit, 1.0);
}"""

private const val SKY_VS = """#version 300 es
out vec2 vNdc;
void main(){ vec2 p = vec2(gl_VertexID==1 ? 3.0 : -1.0, gl_VertexID==2 ? 3.0 : -1.0); vNdc = p; gl_Position = vec4(p, 0.999, 1.0); }"""

private const val SKY_FS = """#version 300 es
precision highp float;
in vec2 vNdc;
uniform mat4 uInv; uniform vec3 uEye; uniform vec3 uTop; uniform vec3 uBottom; uniform vec3 uHaze;
uniform vec3 uSunDir; uniform float uSunUp; uniform vec3 uGlowCol; uniform float uGlow; uniform float uDark;
out vec4 o;
float hash(vec3 p){ return fract(sin(dot(p, vec3(12.9898,78.233,37.719)))*43758.5453); }
void main(){
  vec4 w = uInv*vec4(vNdc, 1.0, 1.0);
  vec3 d = normalize(w.xyz/w.w - uEye);
  vec3 c = mix(uHaze, uBottom, smoothstep(-0.05, 0.05, d.y));
  c = mix(c, uTop, smoothstep(0.05, 0.65, d.y));
  if(uDark > 0.5 && d.y > 0.03){ vec3 cell = floor(d*420.0); float h = hash(cell); if(h > 0.9963) c += vec3(0.9)*(h-0.9963)/0.0037*smoothstep(0.03,0.25,d.y); }
  float s = dot(d, uSunDir);
  if(uSunUp > 0.5){
    c += uGlowCol*(0.14*pow(max(s,0.0), 6.0) + 0.5*pow(max(s,0.0), 300.0))*(0.45+uGlow);
    c = mix(c, vec3(1.0,0.97,0.90), smoothstep(0.99962, 0.99976, s));
  }
  o = vec4(c, 1.0);
}"""

private const val LINE_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aPrev;
layout(location=2) in vec3 aNext;
layout(location=3) in float aSide;
layout(location=4) in vec4 aColor;
uniform mat4 uMvp; uniform vec2 uRes; uniform float uWidth; uniform float uBias;
out vec4 vCol; out float vSide;
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
  gl_Position=c; vCol=aColor; vSide=aSide;
}"""

// uTint: the colour (rgb) and how much it replaces the line's own (a); uSoft: a glow that fades to the edges
private const val LINE_FS = """#version 300 es
precision mediump float;
in vec4 vCol; in float vSide; uniform float uAlpha; uniform vec4 uTint; uniform float uSoft; out vec4 o;
void main(){
  vec3 c = mix(vCol.rgb, uTint.rgb, uTint.a);
  float a = uAlpha*vCol.a*mix(1.0, pow(1.0-abs(vSide), 1.6), uSoft);
  o = vec4(c, a);
}"""

private class Gpu(r: Ribbon) {
    val vao = IntArray(1); private val bufs = IntArray(2); val count = r.indices.size
    init {
        glGenVertexArrays(1, vao, 0); glBindVertexArray(vao[0])
        glGenBuffers(2, bufs, 0)
        glBindBuffer(GL_ARRAY_BUFFER, bufs[0]); glBufferData(GL_ARRAY_BUFFER, r.vertices.size * 4, floats(r.vertices), GL_STATIC_DRAW)
        val s = Ribbon.STRIDE * 4
        attr(0, 3, s, 0); attr(1, 3, s, 12); attr(2, 3, s, 24); attr(3, 1, s, 36); attr(4, 4, s, 40)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufs[1]); glBufferData(GL_ELEMENT_ARRAY_BUFFER, r.indices.size * 4, ints(r.indices), GL_STATIC_DRAW)
        glBindVertexArray(0)
    }
    fun draw() { if (count == 0) return; glBindVertexArray(vao[0]); glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0) }
    fun free() { glDeleteBuffers(2, bufs, 0); glDeleteVertexArrays(1, vao, 0) }
}

private fun floats(a: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }
private fun ints(a: IntArray): IntBuffer = ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(a); position(0) }
private fun attr(loc: Int, size: Int, stride: Int, offset: Int) { glEnableVertexAttribArray(loc); glVertexAttribPointer(loc, size, GL_FLOAT, false, stride, offset) }

private fun program(vs: String, fs: String): Int {
    fun shader(type: Int, src: String): Int {
        val s = glCreateShader(type); glShaderSource(s, src); glCompileShader(s)
        val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("LiveRenderer", glGetShaderInfoLog(s))
        return s
    }
    val p = glCreateProgram()
    glAttachShader(p, shader(GL_VERTEX_SHADER, vs)); glAttachShader(p, shader(GL_FRAGMENT_SHADER, fs)); glLinkProgram(p)
    val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
    if (ok[0] == 0) Log.e("LiveRenderer", glGetProgramInfoLog(p))
    return p
}

/** Draws the live mountain on demand (only while something moves). */
class LiveRenderer(
    private val scene: MapScene,
    private val camera: OrbitCamera,
    private val density: Float,
    private val requestRender: () -> Unit,
) : GLSurfaceView.Renderer {
    /** The light of the moment (the uniforms of the shader) and the shadows the sun casts at it (per grid point). */
    @Volatile var light: Sky.Light = Sky.at(System.currentTimeMillis())
    @Volatile private var shadow: FloatArray? = null
    /** One detail texture, made once off the GL thread (Detail.kt): RGBA, [DETAIL] square. */
    @Volatile var detail: ByteArray? = null
    /** The chosen run (by key), drawn as a line of light by day and of brighter light at night; the rest a little dimmer. */
    @Volatile var chosen: String? = null
    @Volatile var pal: PeakData.Pal? = null

    private var prog = 0; private var skyProg = 0; private var lineProg = 0
    private val vao = IntArray(1); private val bufs = IntArray(3); private var count = 0
    private val tex = IntArray(1); private var texOn = false
    private var uploaded = false
    private val runs = ArrayList<Triple<String, Gpu, Gpu>>(); private val lifts = ArrayList<Gpu>()
    private var width = 1; private var height = 1
    private val mvp = FloatArray(16); private val eye = FloatArray(3); private val inv = FloatArray(16)
    private var dim = 0f
    private var lastNs = 0L

    /** A camera move (the opening dolly, a double tap): from, to, [t, seconds]. */
    @Volatile private var anim: Triple<OrbitCamera.State, OrbitCamera.State, FloatArray>? = null
    fun animate(to: OrbitCamera.State, seconds: Float) { anim = Triple(camera.state(), to, floatArrayOf(0f, seconds)); requestRender() }
    fun stopAnim() { anim = null }

    fun setShadow(s: FloatArray) { shadow = s; shadowDirty = true; requestRender() }
    @Volatile private var shadowDirty = false

    companion object { const val DETAIL = 512 }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = program(VS, FS); skyProg = program(SKY_VS, SKY_FS); lineProg = program(LINE_VS, LINE_FS)
        uploaded = false; texOn = false; tex[0] = 0; runs.clear(); lifts.clear()
        glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE); glCullFace(GL_BACK)
        glClearColor(0.62f, 0.72f, 0.85f, 1f)
    }

    /**
     * Gives the GPU back everything this model holds (on the GL thread): preserveEGLContextOnPause keeps the context,
     * and with it the height model, alive behind the app otherwise (docs/ARCHITECTURE.md).
     */
    fun free() {
        if (uploaded) { glDeleteBuffers(3, bufs, 0); glDeleteVertexArrays(1, vao, 0) }
        runs.forEach { it.second.free(); it.third.free() }; lifts.forEach { it.free() }; runs.clear(); lifts.clear()
        if (tex[0] != 0) glDeleteTextures(1, tex, 0)
        tex[0] = 0; texOn = false; uploaded = false
        for (p in intArrayOf(prog, skyProg, lineProg)) if (p != 0) glDeleteProgram(p)
        prog = 0; skyProg = 0; lineProg = 0
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h; glViewport(0, 0, w, h) }

    private fun upload() {
        val m = scene.mesh
        glGenVertexArrays(1, vao, 0); glBindVertexArray(vao[0])
        glGenBuffers(3, bufs, 0)
        glBindBuffer(GL_ARRAY_BUFFER, bufs[0]); glBufferData(GL_ARRAY_BUFFER, m.vertices.size * 4, floats(m.vertices), GL_STATIC_DRAW)
        val st = TerrainMesh.STRIDE * 4
        attr(0, 3, st, 0); attr(1, 3, st, 12); attr(3, 1, st, 36)
        val sh = shadow ?: FloatArray(scene.terrain.nx * scene.terrain.ny)
        glBindBuffer(GL_ARRAY_BUFFER, bufs[1]); glBufferData(GL_ARRAY_BUFFER, sh.size * 4, floats(sh), GL_DYNAMIC_DRAW)
        attr(4, 1, 4, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufs[2]); glBufferData(GL_ELEMENT_ARRAY_BUFFER, m.indices.size * 4, ints(m.indices), GL_STATIC_DRAW)
        glBindVertexArray(0)
        count = m.count
        shadowDirty = false
        for (p in scene.pisteRibbons) runs += Triple(p.key, Gpu(p.casing), Gpu(p.core))
        for ((_, r) in scene.liftRibbons) lifts += Gpu(r)
        uploaded = true
    }

    private fun uploadDetail(px: ByteArray) {
        glActiveTexture(GL_TEXTURE0)
        if (tex[0] == 0) glGenTextures(1, tex, 0)
        glBindTexture(GL_TEXTURE_2D, tex[0])
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, DETAIL, DETAIL, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap(px))
        glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        texOn = true
    }

    private fun hex(c: androidx.compose.ui.graphics.Color): FloatArray = floatArrayOf(c.red, c.green, c.blue)

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((t0 - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = t0
        if (!uploaded) upload()
        if (shadowDirty) { shadowDirty = false; shadow?.let { glBindBuffer(GL_ARRAY_BUFFER, bufs[1]); glBufferSubData(GL_ARRAY_BUFFER, 0, it.size * 4, floats(it)) } }
        if (!texOn) detail?.let { uploadDetail(it) }

        var moving = false
        anim?.let { (from, to, tt) ->
            tt[0] += dt
            val k = (tt[0] / tt[1]).coerceIn(0f, 1f); val e = k * k * (3 - 2 * k)
            fun l(a: Float, b: Float) = a + (b - a) * e
            camera.set(OrbitCamera.State(l(from.tx, to.tx), l(from.ty, to.ty), l(from.tz, to.tz), l(from.dist, to.dist), l(from.yaw, to.yaw), l(from.pitch, to.pitch)))
            if (k >= 1f) anim = null else moving = true
        }
        if (camera.step(dt)) moving = true
        val t = scene.terrain
        camera.clampTo(t.x0 + 600, t.x1 - 600, t.y0 + 600, t.y1 - 600) { x, z -> t.elev(x, z) }
        val dimTarget = if (chosen != null) 1f else 0f
        if (dim != dimTarget) { dim += (dimTarget - dim) * (1f - Math.exp(-9.0 * dt).toFloat()); if (kotlin.math.abs(dim - dimTarget) < .01f) dim = dimTarget else moving = true }

        val s = camera.state()
        OrbitCamera.mvp(s, width.toFloat() / height, mvp, eye) { x, z -> t.elev(x, z) }
        val L = light
        val night = if (L.dark) 1f else 0f
        // the haze of the valleys: the colour of the sky at the horizon, a little paler
        val haze = FloatArray(3) { L.skyBottom[it] * .82f + (if (L.dark) .06f else .16f) }

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        // the sky
        android.opengl.Matrix.invertM(inv, 0, mvp, 0)
        glDisable(GL_DEPTH_TEST); glUseProgram(skyProg)
        glUniformMatrix4fv(glGetUniformLocation(skyProg, "uInv"), 1, false, inv, 0)
        glUniform3fv(glGetUniformLocation(skyProg, "uEye"), 1, eye, 0)
        glUniform3fv(glGetUniformLocation(skyProg, "uTop"), 1, L.skyTop, 0)
        glUniform3fv(glGetUniformLocation(skyProg, "uBottom"), 1, L.skyBottom, 0)
        glUniform3fv(glGetUniformLocation(skyProg, "uHaze"), 1, haze, 0)
        glUniform3fv(glGetUniformLocation(skyProg, "uSunDir"), 1, L.sunDir, 0)
        glUniform1f(glGetUniformLocation(skyProg, "uSunUp"), if (L.sunUp) 1f else 0f)
        glUniform3fv(glGetUniformLocation(skyProg, "uGlowCol"), 1, L.glowColor, 0)
        glUniform1f(glGetUniformLocation(skyProg, "uGlow"), L.glow)
        glUniform1f(glGetUniformLocation(skyProg, "uDark"), night)
        glBindVertexArray(0); glDrawArrays(GL_TRIANGLES, 0, 3)
        glEnable(GL_DEPTH_TEST)

        // the mountain
        glUseProgram(prog)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, tex[0])
        glUniform1i(glGetUniformLocation(prog, "uDetail"), 0)
        glUniformMatrix4fv(glGetUniformLocation(prog, "uMvp"), 1, false, mvp, 0)
        glUniform3fv(glGetUniformLocation(prog, "uSun"), 1, L.dir, 0)
        glUniform3fv(glGetUniformLocation(prog, "uSunCol"), 1, L.color, 0)
        glUniform3fv(glGetUniformLocation(prog, "uAmbTop"), 1, L.ambTop, 0)
        glUniform3fv(glGetUniformLocation(prog, "uAmbGround"), 1, L.ambGround, 0)
        glUniform3fv(glGetUniformLocation(prog, "uCam"), 1, eye, 0)
        glUniform3fv(glGetUniformLocation(prog, "uHaze"), 1, haze, 0)
        glUniform2f(glGetUniformLocation(prog, "uHazeK"), if (L.dark) 1f / 26000f else 1f / 17000f, 0f)
        glUniform1f(glGetUniformLocation(prog, "uNight"), night)
        glUniform1f(glGetUniformLocation(prog, "uDim"), dim * .55f)
        glBindVertexArray(vao[0]); glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0)

        // the live layer: the runs (a white case and the colour), the lifts' cables, the chosen run as light
        val p = pal
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false)
        glUseProgram(lineProg)
        glUniformMatrix4fv(glGetUniformLocation(lineProg, "uMvp"), 1, false, mvp, 0)
        glUniform2f(glGetUniformLocation(lineProg, "uRes"), width.toFloat(), height.toFloat())
        val uW = glGetUniformLocation(lineProg, "uWidth"); val uA = glGetUniformLocation(lineProg, "uAlpha")
        val uT = glGetUniformLocation(lineProg, "uTint"); val uS = glGetUniformLocation(lineProg, "uSoft"); val uB = glGetUniformLocation(lineProg, "uBias")
        if (p !== palCache) {
            palCache = p; runRgb.clear()
            if (p != null) for (pi in scene.runs.pistes) runColour(p, pi.color)?.let { runRgb[pi.key] = it }
            caseRgb = p?.let { hex(it.case) } ?: floatArrayOf(1f, 1f, 1f); wireRgb = p?.let { hex(it.wire) } ?: floatArrayOf(.13f, .19f, .25f)
        }
        val case = caseRgb; val wire = wireRgb
        // a closer view draws the lines a little wider, as the pictures' zoom does
        val zw = (9000f / s.dist).coerceIn(.8f, 1.7f).let { Math.pow(it.toDouble(), .5).toFloat() }
        glUniform1f(uB, 0.0004f); glUniform1f(uS, 0f)
        val rest = 1f - .55f * dim
        // lifts
        glUniform4f(uT, wire[0], wire[1], wire[2], 1f); glUniform1f(uW, 1.3f * density * zw); glUniform1f(uA, .85f * rest)
        for (g in lifts) g.draw()
        // runs: the case, then the colour (the palette of the hour: night lines glow)
        for ((key, cs, core) in runs) {
            if (key == chosen) continue
            glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, 3.6f * density * zw); glUniform1f(uA, (if (night > 0f) .7f else .95f) * rest); cs.draw()
            val c = runRgb[key]
            if (c != null) glUniform4f(uT, c[0], c[1], c[2], 1f) else glUniform4f(uT, 0f, 0f, 0f, 0f)
            glUniform1f(uW, 2.2f * density * zw); glUniform1f(uA, rest); core.draw()
        }
        // the chosen run: a line of light by day, a brighter glow at night (K11), over everything
        chosen?.let { k ->
            val r = runs.firstOrNull { it.first == k } ?: return@let
            glDisable(GL_DEPTH_TEST)
            val warm = if (night > 0f) WARM_NIGHT else WARM_DAY
            glUniform1f(uS, 1f); glUniform4f(uT, warm[0], warm[1], warm[2], 1f)
            glUniform1f(uW, 16f * density * zw); glUniform1f(uA, if (night > 0f) .55f else .42f); r.second.draw()
            glUniform1f(uS, 0f)
            glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, 5f * density * zw); glUniform1f(uA, 1f); r.second.draw()
            val c = runRgb[k] ?: case
            glUniform4f(uT, c[0], c[1], c[2], 1f); glUniform1f(uW, 3.2f * density * zw); r.third.draw()
            glEnable(GL_DEPTH_TEST)
        }
        glDepthMask(true); glDisable(GL_BLEND)
        if (moving) requestRender()
    }

    private var palCache: PeakData.Pal? = null
    private val runRgb = HashMap<String, FloatArray>()
    private var caseRgb = floatArrayOf(1f, 1f, 1f); private var wireRgb = floatArrayOf(.13f, .19f, .25f)
    private val WARM_DAY = floatArrayOf(1f, .97f, .86f); private val WARM_NIGHT = floatArrayOf(1f, .86f, .52f)

    private fun runColour(p: PeakData.Pal, c: String?): FloatArray? = when (c) {
        "green" -> hex(p.green); "blue" -> hex(p.blue); "red" -> hex(p.red); "black" -> hex(p.black); else -> null
    }
}

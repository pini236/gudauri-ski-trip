package io.github.pini236.skiapp.redesign

import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.util.Log
import io.github.pini236.skiapp.map.MapScene
import io.github.pini236.skiapp.map.OrbitCamera
import io.github.pini236.skiapp.map.Ribbon
import io.github.pini236.skiapp.map.TerrainMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/*
 * Phase B of the redesign POC (decision 83, the architect's conditions of 20.11): the painted mountain as a live model.
 * The same elevation grid as today's map, drawn as the designer's handoff (design/handoff/2026-11-20-redesign-live):
 * the light of the hour in the shader from seven keys (LiveLight), five small textures that do not depend on the hour,
 * the far horizon in the sky. The runs and lifts are the map's own lines on top. Test build only.
 */

// The designer's reference shaders (design/redesign/tools/live_ref.html, GLSL ES 3.00), as they are: light per pixel
// from the relief texture, snow in two colours (lit and shade), rock streaked down the fall line, the haze that moves
// with the camera, a gentle filmic curve. Added here only the dimming of the rest of the mountain under a chosen run.
private val VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=4) in float aShadow;
uniform mat4 uMvp;
out vec3 vPos; out float vShadow;
void main(){ vPos=aPos; vShadow=aShadow; gl_Position=uMvp*vec4(aPos,1.0); }"""

private const val FS = """#version 300 es
precision highp float;
in vec3 vPos; in float vShadow;
uniform sampler2D uRelief; uniform sampler2D uCover; uniform sampler2D uTile;
uniform vec4 uBox;
uniform vec3 uSun;
uniform vec3 uLit; uniform vec3 uShade; uniform vec3 uRock; uniform vec3 uRockS; uniform vec3 uHor;
uniform float uDirect; uniform float uGlow;
uniform vec3 uCam; uniform vec3 uHaze; uniform float uValley; uniform float uRipple;
uniform vec4 uDim;
out vec4 o;
void main(){
  vec2 uv=(vPos.xz-uBox.xy)*uBox.zw;
  vec3 rl=texture(uRelief,uv).rgb; vec3 cv=texture(uCover,uv).rgb; vec3 tl=texture(uTile,vPos.xz/640.0).rgb;
  vec2 s=(rl.rg-0.5)*3.2;
  float deg=degrees(atan(length(s)));
  float amp=(1.0-0.85*cv.g)*(1.0-smoothstep(30.0,40.0,deg));
  s+=(tl.rg-0.5)*0.5*amp*uRipple;
  vec3 n=normalize(vec3(-s.x,1.0,-s.y));
  float lam=max(dot(n,uSun),0.0);
  float direct=lam*(1.0-0.94*vShadow)*uDirect+(1.0-uDirect)*0.55*n.y;
  float t=pow(direct,0.85);
  t*=mix(1.0,0.35+0.65*smoothstep(2350.0,3000.0,vPos.y),uGlow);
  vec3 col=uShade*(0.86+0.14*n.y)+(uLit-uShade*0.86)*t;
  vec3 v=normalize(uCam-vPos); vec3 h=normalize(uSun+v);
  col+=pow(max(dot(n,h),0.0),38.0)*(1.0-vShadow)*uDirect*0.20*uLit;
  col*=0.94+0.06*smoothstep(2000.0,3100.0,vPos.y);
  col*=1.0+0.05*cv.g*(1.0-0.5*t);
  vec2 fall=s*s/(dot(s,s)+1e-4);
  float stk=texture(uTile,vPos.xz/160.0).b*fall.y+texture(uTile,vPos.zx/160.0).b*fall.x;
  float rk=smoothstep(28.5,38.0,18.0+30.0*cv.r+(stk-0.5)*12.0)*0.9;
  col=mix(col,mix(uRockS,uRock,t)*(2.0*cv.b)*(0.7+0.6*stk),rk);
  col*=0.7+0.6*rl.b;
  col=mix(col,uDim.rgb,uDim.a);
  float d=length(vPos-uCam);
  float far=uHaze.z*(1.0-exp(-max(d-uHaze.x,0.0)/uHaze.y));
  float low=smoothstep(2300.0,1750.0,vPos.y)*uValley;
  col=mix(col,uHor,clamp(far+low*(1.0-far),0.0,0.97));
  col=clamp(col,0.0,1.0); col=col*col*(3.0-2.0*col)*0.55+col*0.45;
  o=vec4(col,1.0);
}"""

private const val SKY_VS = """#version 300 es
out vec2 vNdc;
void main(){ vec2 p = vec2(gl_VertexID==1 ? 3.0 : -1.0, gl_VertexID==2 ? 3.0 : -1.0); vNdc = p; gl_Position = vec4(p, 0.9999, 1.0); }"""

private const val SKY_FS = """#version 300 es
precision highp float;
in vec2 vNdc;
uniform mat4 uInv; uniform vec3 uEye;
uniform sampler2D uHorizon; uniform sampler2D uCirrus;
uniform vec3 uSun; uniform vec3 uLit; uniform vec3 uShade; uniform vec3 uZen; uniform vec3 uHor; uniform vec3 uBand;
uniform float uBandOn; uniform float uDirect; uniform float uGlow; uniform float uStars; uniform float uSunUp;
uniform vec4 uStrip; uniform vec2 uAir; uniform float uSkyTop;
out vec4 o;
float hash(vec3 p){ return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }
void main(){
  vec4 w=uInv*vec4(vNdc,1.0,1.0); vec3 d=normalize(w.xyz/w.w-uEye);
  float el=degrees(asin(clamp(d.y,-1.0,1.0)));
  float az=atan(d.x,-d.z); if(az<0.0) az+=6.2831853;
  float k=clamp(el/uSkyTop,0.0,1.0);
  vec3 c=mix(uZen,uHor,pow(1.0-k,1.25));
  float away=0.6+0.4*(0.5-0.5*dot(normalize(d.xz+1e-5),normalize(uSun.xz+1e-5)));
  c=mix(c,uBand,exp(-pow((el-4.5)/3.2,2.0))*0.75*uBandOn*away);
  float ci=texture(uCirrus,vec2(az/6.2831853,1.0-clamp(el/24.0,0.0,1.0))).r*smoothstep(0.0,5.0,el);
  vec3 cc=uStars<1.0 ? uHor*0.4+0.6*uLit : uHor*1.6;
  float cm=ci*(uStars<1.0?0.34:0.08);
  c=mix(c,cc,cm);
  if(uStars>0.0 && el>1.0){ vec3 cell=floor(d*420.0); float hh=hash(cell); if(hh>0.9965) c+=vec3(0.95,0.97,1.0)*(hh-0.9965)/0.0035*uStars*(1.0-cm)*smoothstep(1.0,8.0,el); }
  float sd=max(dot(d,uSun),0.0);
  c+=uLit*(pow(sd,48.0)*0.35+pow(sd,900.0)*1.2)*uSunUp;
  if(el>uStrip.x && el<uStrip.y){
    vec3 hz=texture(uHorizon,vec2(az/6.2831853,(uStrip.y-el)/(uStrip.y-uStrip.x))).rgb;
    float land=1.0-smoothstep(0.90,0.99,hz.b);
    vec2 s=(hz.rg-0.5)*3.2; vec3 n=normalize(vec3(-s.x,1.0,-s.y));
    float dist=hz.b*uStrip.z/0.98;
    float t=pow(max(dot(n,uSun),0.0)*uDirect+(1.0-uDirect)*0.55*n.y,0.85);
    t*=mix(1.0,0.5,uGlow*(1.0-smoothstep(0.0,2.5,el+3.0)));
    vec3 g=uShade*(0.86+0.14*n.y)+(uLit-uShade*0.86)*t;
    float deg=degrees(atan(length(s)));
    g=mix(g,mix(uShade*0.55,uLit*0.5,t),smoothstep(24.0,34.0,deg)*0.7);
    float air=uAir.x+uAir.y*(1.0-exp(-max(dist-9000.0,0.0)/26000.0));
    g=mix(g,uHor,air);
    c=mix(c,g,land*smoothstep(uStrip.x,uStrip.x+1.5,el));
  }
  c=clamp(c,0.0,1.0); c=c*c*(3.0-2.0*c)*0.55+c*0.45;
  o=vec4(c,1.0);
}"""

// the village after dark: a warm point for every real building (peak-live.json, village)
private const val DOT_VS = """#version 300 es
layout(location=0) in vec3 aPos; uniform mat4 uMvp; uniform float uSize;
void main(){ gl_Position=uMvp*vec4(aPos,1.0); gl_PointSize=uSize; }"""
private const val DOT_FS = """#version 300 es
precision mediump float; uniform vec4 uCol; out vec4 o;
void main(){ vec2 p=gl_PointCoord*2.0-1.0; float r=dot(p,p); if(r>1.0) discard; o=vec4(uCol.rgb, uCol.a*(1.0-r*0.5)); }"""

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
    /** The light of the moment (the designer's keys blended by the sun's height, LiveLight) and its cast shadows. */
    @Volatile var light: LiveLight.Mix? = null
    @Volatile var data: LiveData? = null
    @Volatile private var shadow: FloatArray? = null
    /** The designer's five textures, decoded off the GL thread (LiveTex), uploaded on the next frame. */
    @Volatile var textures: List<LiveTex>? = null
    /**
     * A new GL context (the first one, or after the background) has none of the textures, and the decoded copy is not
     * kept once uploaded: this asks for them again (LiveView decodes them off the GL thread).
     */
    @Volatile var needTextures: (() -> Unit)? = null
    private var hadTextures = false
    private var drawn = false
    /** The chosen run (by key), drawn as a line of light by day and of brighter light at night; the rest a little dimmer. */
    @Volatile var chosen: String? = null
    @Volatile var pal: PeakData.Pal? = null
    /** The largest texture this GPU takes (the horizon is 4096 wide). */
    @Volatile var maxTex = 4096

    private var prog = 0; private var skyProg = 0; private var lineProg = 0; private var dotProg = 0
    private val vao = IntArray(1); private val bufs = IntArray(3); private var count = 0
    private val dots = IntArray(2); private var dotCount = 0
    /** relief, cover, tile, horizon, cirrus */
    private val tex = IntArray(5); private var texOn = false
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

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = program(VS, FS); skyProg = program(SKY_VS, SKY_FS); lineProg = program(LINE_VS, LINE_FS); dotProg = program(DOT_VS, DOT_FS)
        uploaded = false; texOn = false; tex.fill(0); runs.clear(); lifts.clear(); dotCount = 0
        if (hadTextures && textures == null) needTextures?.invoke()
        drawn = false
        val mx = IntArray(1); glGetIntegerv(GL_MAX_TEXTURE_SIZE, mx, 0); maxTex = mx[0]
        glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE); glCullFace(GL_BACK)
        glClearColor(0.62f, 0.72f, 0.85f, 1f)
    }

    /**
     * Gives the GPU back everything this model holds (on the GL thread): the context goes in the background anyway
     * (LiveView), and leaving the model frees it at once (docs/ARCHITECTURE.md).
     */
    fun free() {
        if (uploaded) { glDeleteBuffers(3, bufs, 0); glDeleteVertexArrays(1, vao, 0); glDeleteBuffers(2, dots, 0) }
        runs.forEach { it.second.free(); it.third.free() }; lifts.forEach { it.free() }; runs.clear(); lifts.clear()
        if (texOn) glDeleteTextures(5, tex, 0)
        tex.fill(0); texOn = false; uploaded = false; dotCount = 0
        for (p in intArrayOf(prog, skyProg, lineProg, dotProg)) if (p != 0) glDeleteProgram(p)
        prog = 0; skyProg = 0; lineProg = 0; dotProg = 0
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h; glViewport(0, 0, w, h) }

    private fun upload() {
        val m = scene.mesh
        glGenVertexArrays(1, vao, 0); glBindVertexArray(vao[0])
        glGenBuffers(3, bufs, 0)
        glBindBuffer(GL_ARRAY_BUFFER, bufs[0]); glBufferData(GL_ARRAY_BUFFER, m.vertices.size * 4, floats(m.vertices), GL_STATIC_DRAW)
        attr(0, 3, TerrainMesh.STRIDE * 4, 0)
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

    private fun uploadVillage(v: FloatArray) {
        glGenVertexArrays(1, dots, 0); glBindVertexArray(dots[0])
        glGenBuffers(1, dots, 1)
        glBindBuffer(GL_ARRAY_BUFFER, dots[1]); glBufferData(GL_ARRAY_BUFFER, v.size * 4, floats(v), GL_STATIC_DRAW)
        attr(0, 3, 12, 0)
        glBindVertexArray(0)
        dotCount = v.size / 3
    }

    private fun uploadTextures(list: List<LiveTex>) {
        glGenTextures(5, tex, 0)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        val aniso = 0x84FE; val maxAniso = 0x84FF
        val af = FloatArray(1)
        val hasAniso = glGetString(GL_EXTENSIONS)?.contains("texture_filter_anisotropic") == true
        if (hasAniso) glGetFloatv(maxAniso, af, 0)
        for ((i, name) in LiveData.TEXTURES.withIndex()) {
            var t = list.first { it.name == name }
            while (t.w > maxTex) t = LiveTex.half(t)
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, tex[i])
            if (name == "cirrus") {
                // one channel (the architect, 21.11): a quarter of the memory
                val r = ByteArray(t.w * t.h) { t.px[it * 4] }
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, t.w, t.h, 0, GL_RED, GL_UNSIGNED_BYTE, ByteBuffer.wrap(r))
            } else glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, t.w, t.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap(t.px))
            val sky = name == "horizon" || name == "cirrus"
            if (name == "horizon") glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            else { glGenerateMipmap(GL_TEXTURE_2D); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR) }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            val wrapS = if (name == "tile" || sky) GL_REPEAT else GL_CLAMP_TO_EDGE
            val wrapT = if (name == "tile") GL_REPEAT else GL_CLAMP_TO_EDGE
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT)
            if (hasAniso && !sky) glTexParameterf(GL_TEXTURE_2D, aniso, minOf(8f, af[0]))
        }
        texOn = true
    }

    private fun hex(c: androidx.compose.ui.graphics.Color): FloatArray = floatArrayOf(c.red, c.green, c.blue)
    private fun u(p: Int, n: String) = glGetUniformLocation(p, n)

    override fun onDrawFrame(gl: GL10?) {
        val t0 = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((t0 - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = t0
        if (!uploaded) upload()
        if (shadowDirty) { shadowDirty = false; shadow?.let { glBindBuffer(GL_ARRAY_BUFFER, bufs[1]); glBufferSubData(GL_ARRAY_BUFFER, 0, it.size * 4, floats(it)) } }
        if (!texOn) textures?.let { uploadTextures(it); textures = null; hadTextures = true }
        val D = data
        if (dotCount == 0 && D != null) uploadVillage(D.village)
        val L = light
        // until the textures and the light are in, the plain sky colour (a frame or two)
        if (!texOn || L == null || D == null) { glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT); return }

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
        OrbitCamera.mvp(s, width.toFloat() / height, mvp, eye, D.camera.fovDeg) { x, z -> t.elev(x, z) }
        val night = L.stars >= .25f

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        // the sky, the high cloud and the far horizon (the Caucasus, Kazbek)
        android.opengl.Matrix.invertM(inv, 0, mvp, 0)
        glDisable(GL_DEPTH_TEST); glUseProgram(skyProg)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, tex[3]); glUniform1i(u(skyProg, "uHorizon"), 0)
        glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, tex[4]); glUniform1i(u(skyProg, "uCirrus"), 1)
        glUniformMatrix4fv(u(skyProg, "uInv"), 1, false, inv, 0)
        glUniform3fv(u(skyProg, "uEye"), 1, eye, 0)
        glUniform3fv(u(skyProg, "uSun"), 1, L.dir, 0)
        glUniform3fv(u(skyProg, "uLit"), 1, L.lit, 0); glUniform3fv(u(skyProg, "uShade"), 1, L.shade, 0)
        glUniform3fv(u(skyProg, "uZen"), 1, L.zen, 0); glUniform3fv(u(skyProg, "uHor"), 1, L.hor, 0); glUniform3fv(u(skyProg, "uBand"), 1, L.band, 0)
        glUniform1f(u(skyProg, "uBandOn"), L.bandOn); glUniform1f(u(skyProg, "uDirect"), L.direct); glUniform1f(u(skyProg, "uGlow"), L.glow)
        glUniform1f(u(skyProg, "uStars"), L.stars); glUniform1f(u(skyProg, "uSunUp"), L.sunUp)
        glUniform4f(u(skyProg, "uStrip"), D.strip[0], D.strip[1], D.strip[2], 0f)
        glUniform2f(u(skyProg, "uAir"), D.air[0], D.air[1]); glUniform1f(u(skyProg, "uSkyTop"), D.skyTop)
        glBindVertexArray(0); glDrawArrays(GL_TRIANGLES, 0, 3)
        glEnable(GL_DEPTH_TEST)

        // the mountain
        glUseProgram(prog)
        for ((i, n) in listOf("uRelief", "uCover", "uTile").withIndex()) {
            glActiveTexture(GL_TEXTURE0 + i); glBindTexture(GL_TEXTURE_2D, tex[i]); glUniform1i(u(prog, n), i)
        }
        glUniformMatrix4fv(u(prog, "uMvp"), 1, false, mvp, 0)
        glUniform4f(u(prog, "uBox"), D.x0, D.z0, 1f / (D.x1 - D.x0), 1f / (D.z1 - D.z0))
        glUniform3fv(u(prog, "uSun"), 1, L.dir, 0)
        glUniform3fv(u(prog, "uLit"), 1, L.lit, 0); glUniform3fv(u(prog, "uShade"), 1, L.shade, 0)
        glUniform3fv(u(prog, "uRock"), 1, L.rock, 0); glUniform3fv(u(prog, "uRockS"), 1, L.rockS, 0); glUniform3fv(u(prog, "uHor"), 1, L.hor, 0)
        glUniform1f(u(prog, "uDirect"), L.direct); glUniform1f(u(prog, "uGlow"), L.glow)
        glUniform3fv(u(prog, "uCam"), 1, eye, 0)
        // the haze moves with the camera: whatever is behind what you look at is always a little in the air
        glUniform3f(u(prog, "uHaze"), D.haze[0] * s.dist, D.haze[1] * s.dist, D.haze[2])
        glUniform1f(u(prog, "uValley"), D.valley); glUniform1f(u(prog, "uRipple"), D.ripple)
        // the rest of the mountain under a chosen run (phase A: rgb(54,78,128) at 0.42 by day, rgb(2,6,18) at 0.62 at night)
        if (night) glUniform4f(u(prog, "uDim"), 2f / 255, 6f / 255, 18f / 255, .62f * dim)
        else glUniform4f(u(prog, "uDim"), 54f / 255, 78f / 255, 128f / 255, .42f * dim)
        glBindVertexArray(vao[0]); glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0)
        glActiveTexture(GL_TEXTURE0)

        // the live layer: the runs (a white case and the colour), the lifts' cables, the chosen run as light
        val p = pal
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false)
        // the village after dark
        if (L.stars >= .25f && dotCount > 0) {
            glUseProgram(dotProg)
            glUniformMatrix4fv(u(dotProg, "uMvp"), 1, false, mvp, 0)
            glUniform1f(u(dotProg, "uSize"), 2.2f * density)
            glUniform4f(u(dotProg, "uCol"), 1f, .773f, .431f, .5f + .5f * minOf(L.stars / .6f, 1f))
            glBindVertexArray(dots[0]); glDrawArrays(GL_POINTS, 0, dotCount)
        }
        glUseProgram(lineProg)
        glUniformMatrix4fv(u(lineProg, "uMvp"), 1, false, mvp, 0)
        glUniform2f(u(lineProg, "uRes"), width.toFloat(), height.toFloat())
        val uW = u(lineProg, "uWidth"); val uA = u(lineProg, "uAlpha")
        val uT = u(lineProg, "uTint"); val uS = u(lineProg, "uSoft"); val uB = u(lineProg, "uBias")
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
        glUniform4f(uT, wire[0], wire[1], wire[2], 1f); glUniform1f(uW, 1.3f * density * zw); glUniform1f(uA, .85f * rest)
        for (g in lifts) g.draw()
        for ((key, cs, core) in runs) {
            if (key == chosen) continue
            glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, 3.6f * density * zw); glUniform1f(uA, (if (night) .7f else .95f) * rest); cs.draw()
            val c = runRgb[key]
            if (c != null) glUniform4f(uT, c[0], c[1], c[2], 1f) else glUniform4f(uT, 0f, 0f, 0f, 0f)
            glUniform1f(uW, 2.2f * density * zw); glUniform1f(uA, rest); core.draw()
        }
        // the chosen run: a line of light by day, a brighter glow at night (K11), over everything
        chosen?.let { k ->
            val r = runs.firstOrNull { it.first == k } ?: return@let
            glDisable(GL_DEPTH_TEST)
            val warm = if (night) WARM_NIGHT else WARM_DAY
            glUniform1f(uS, 1f); glUniform4f(uT, warm[0], warm[1], warm[2], 1f)
            glUniform1f(uW, 16f * density * zw); glUniform1f(uA, if (night) .55f else .42f); r.second.draw()
            glUniform1f(uS, 0f)
            glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, 6f * density * zw); glUniform1f(uA, 1f); r.second.draw()
            val c = runRgb[k] ?: case
            glUniform4f(uT, c[0], c[1], c[2], 1f); glUniform1f(uW, 3.6f * density * zw); r.third.draw()
            glEnable(GL_DEPTH_TEST)
        }
        glDepthMask(true); glDisable(GL_BLEND)
        // the emulator waits for this: a frame with the mountain, not only the clear colour
        if (!drawn) { drawn = true; io.github.pini236.skiapp.qa.Qa.log("redesign live drawn") }
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

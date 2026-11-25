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
uniform sampler2D uDrape; uniform vec4 uDrapeBox; uniform float uDrapeOn; uniform float uDrapeK;
uniform sampler2D uGround; uniform vec4 uGroundBox; uniform float uGroundOn;
uniform vec4 uAir2;
// the front seam (the designer's step 4, Y03, terrain-seam.frag): over its last uSeam.y metres the model reads its maps
// from the band (ringband), the same maps the ring reads past the edge, so the two meet as one ground
uniform vec2 uSeam; uniform sampler2D uBand; uniform vec4 uBBox;
// the air of height (the designer's ring handoff, 25.11.2026; the architect allowed it on the mountain too): up to 1.5
// times the camera's distance the haze of before, then a slow one (25 km), thinner over 2,800 m, so the high snow stands
// over the haze of the valleys; uAir2.y = 0 is the haze of before
float airOf(float d,float y){
  if(uAir2.y<=0.0) return uHaze.z*(1.0-exp(-max(d-uHaze.x,0.0)/uHaze.y));
  float dens=mix(1.0,uAir2.w,smoothstep(uAir2.z,uAir2.z+1800.0,y));
  float od=(clamp(d,uHaze.x,uAir2.x)-uHaze.x)/uHaze.y+max(d-uAir2.x,0.0)/uAir2.y;
  return uHaze.z*(1.0-exp(-od*dens));
}
out vec4 o;
void main(){
  vec2 uv=(vPos.xz-uBox.xy)*uBox.zw;
  vec3 rl=texture(uRelief,uv).rgb; vec3 cv=texture(uCover,uv).rgb; vec3 tl=texture(uTile,vPos.xz/640.0).rgb;
  float wE=0.0; vec4 rr=vec4(rl,cv.r);
  if(uSeam.x>0.0){ vec2 qd=min(uv,1.0-uv)/uBox.zw; float te=1.0-clamp(min(qd.x,qd.y)/uSeam.y,0.0,1.0); wE=te*te*(3.0-2.0*te);
    rr=texture(uBand,(vPos.xz-uBBox.xy)*uBBox.zw); cv.r=mix(cv.r,rr.a,wE); }
  vec2 s=mix((rl.rg-0.5)*3.2,(rr.rg-0.5)*3.2,wE);
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
  col*=0.7+0.6*mix(rl.b,rr.b,wE);
  // what lies on the snow (Drape.kt, without the contours): roads, water and the village, premultiplied, lit by the
  // snow's light of the hour instead of the map's (the handoff, section 3)
  vec4 e=uDrapeOn>0.0?texture(uDrape,(vPos.xz-uDrapeBox.xy)*uDrapeBox.zw):vec4(0.0);
  // quiet, so the mountain leads (the designer, run 292): about a third by day
  e*=uDrapeK; col=col*(1.0-e.a)+e.rgb*mix(uShade,uLit,t);
  // a chosen run: a corridor of about 150 m each side (MapScene.slopeLayer's alpha, soft at its edge) stays in full
  // light and the rest of the mountain is dimmer; no colour on the snow, the slope is on the line itself (K09 to K11)
  vec2 gv=(vPos.xz-uGroundBox.xy)*uGroundBox.zw;
  vec4 g=(uGroundOn>0.0&&gv.x>=0.0&&gv.y>=0.0&&gv.x<=1.0&&gv.y<=1.0)?texture(uGround,gv):vec4(0.0);
  float ga=g.a*uGroundOn;
  col=mix(col,uDim.rgb,uDim.a*(1.0-ga));
  float d=length(vPos-uCam);
  float far=airOf(d,vPos.y);
  float low=smoothstep(2300.0,1750.0,vPos.y)*uValley;
  col=mix(col,uHor,clamp(far+low*(1.0-far),0.0,0.97));
  col=clamp(col,0.0,1.0); col=col*col*(3.0-2.0*col)*0.55+col*0.45;
  o=vec4(col,1.0);
}"""

// The far ground past the height model (LiveRing, schema 2: the designer's ring handoff of 25.11.2026, ring-ref.frag):
// the inner ring shaded by its relief map (ringrelief, 90 m) like the mountain, the model's rock grain read large, the
// cast shadow, the air of height; one draw
private const val RING_VS = """#version 300 es
layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNor; layout(location=2) in float aShadow;
uniform mat4 uMvp;
out vec3 vPos; out vec3 vNor; out float vShadow;
void main(){ vPos=aPos; vNor=aNor; vShadow=aShadow; gl_Position=uMvp*vec4(aPos,1.0); }"""

private const val RING_FS = """#version 300 es
precision highp float;
in vec3 vPos; in vec3 vNor; in float vShadow;
uniform sampler2D uTile;
uniform vec3 uSun; uniform vec3 uLit; uniform vec3 uShade; uniform vec3 uRock; uniform vec3 uRockS; uniform vec3 uHor;
uniform float uDirect; uniform float uGlow; uniform vec3 uCam; uniform vec3 uHaze; uniform float uValley; uniform vec4 uDim;
uniform float uRingR;
uniform sampler2D uRRel; uniform vec4 uRBox; uniform float uRTex; uniform vec4 uMBox;
uniform vec4 uAir2;
// the front seam (step 4, ring-ref.frag): near the model the ring reads the band, the model's own maps carried past its
// edge, and takes the model's glaze, height tone and rock, so the two meet as one ground; uSeam.y = the band's margin
uniform vec2 uSeam; uniform float uRip; uniform sampler2D uBand; uniform vec4 uBBox;
// the air of height (the designer's ring handoff, 25.11.2026; the architect allowed it on the mountain too): up to 1.5
// times the camera's distance the haze of before, then a slow one (25 km), thinner over 2,800 m, so the high snow stands
// over the haze of the valleys; uAir2.y = 0 is the haze of before
float airOf(float d,float y){
  if(uAir2.y<=0.0) return uHaze.z*(1.0-exp(-max(d-uHaze.x,0.0)/uHaze.y));
  float dens=mix(1.0,uAir2.w,smoothstep(uAir2.z,uAir2.z+1800.0,y));
  float od=(clamp(d,uHaze.x,uAir2.x)-uHaze.x)/uHaze.y+max(d-uAir2.x,0.0)/uAir2.y;
  return uHaze.z*(1.0-exp(-od*dens));
}
out vec4 o;
void main(){
  vec3 n0=normalize(vNor); vec2 s=-n0.xz/max(n0.y,0.2); float occ=1.0;
  vec2 ruv=(vPos.xz-uRBox.xy)*uRBox.zw; vec3 rr=texture(uRRel,clamp(ruv,0.0,1.0)).rgb;
  if(uRTex>0.0 && ruv.x>0.0 && ruv.y>0.0 && ruv.x<1.0 && ruv.y<1.0){
    float om=max(max(uMBox.x-vPos.x,vPos.x-uMBox.z),max(uMBox.y-vPos.z,vPos.z-uMBox.w));
    float w=smoothstep(0.0,0.04,min(min(ruv.x,ruv.y),min(1.0-ruv.x,1.0-ruv.y)))*(uSeam.x>0.0 ? 1.0 : smoothstep(0.0,500.0,om));
    s=mix(s,(rr.rg-0.5)*3.2,w); occ=mix(1.0,0.7+0.6*rr.b,w); }
  float wb=0.0; vec2 buv=(vPos.xz-uBBox.xy)*uBBox.zw; vec4 bb=texture(uBand,clamp(buv,0.0,1.0));   // outside any branch: the mip level needs the neighbours
  if(uSeam.x>0.0){ float om=max(max(uMBox.x-vPos.x,vPos.x-uMBox.z),max(uMBox.y-vPos.z,vPos.z-uMBox.w));
    wb=1.0-smoothstep(uSeam.y-1200.0,uSeam.y-100.0,om);
    s=mix(s,(bb.rg-0.5)*3.2,wb); occ=mix(occ,0.7+0.6*bb.b,wb); }
  float deg=degrees(atan(length(s)));
  s+=(texture(uTile,vPos.xz/640.0).rg-0.5)*0.5*(uSeam.x>0.0 ? uRip : 1.6)*(1.0-smoothstep(30.0,40.0,deg));
  vec3 n=normalize(vec3(-s.x,1.0,-s.y));
  float lam=max(dot(n,uSun),0.0);
  float direct=lam*(1.0-0.94*vShadow)*uDirect+(1.0-uDirect)*0.55*n.y;
  float t=pow(direct,0.85);
  t*=mix(1.0,0.35+0.65*smoothstep(2350.0,3000.0,vPos.y),uGlow);
  vec3 col=uShade*(0.86+0.14*n.y)+(uLit-uShade*0.86)*t;
  if(uSeam.x>0.0){ vec3 v=normalize(uCam-vPos); vec3 hh=normalize(uSun+v);   // the model's glaze of a low sun, and its height tone
    col+=pow(max(dot(n,hh),0.0),38.0)*(1.0-vShadow)*uDirect*0.20*uLit; col*=0.94+0.06*smoothstep(2000.0,3100.0,vPos.y); }
  float stk=texture(uTile,vPos.xz/160.0).b;
  float rk=smoothstep(27.0,36.0,deg+(stk-0.5)*6.0)*0.8;
  vec2 fall=s*s/(dot(s,s)+1e-4);
  float stk2=texture(uTile,vPos.xz/160.0).b*fall.y+texture(uTile,vPos.zx/160.0).b*fall.x;
  float tone=0.75+0.5*stk;
  if(uRTex>0.0){
    float m1=texture(uTile,vPos.xz/2400.0).b, m2=texture(uTile,vPos.zx/900.0).b, m3=texture(uTile,vPos.xz/5200.0+0.37).r;
    float m4=texture(uTile,vPos.xz/420.0+0.61).r;
    stk=mix(m2,stk2,0.35);
    float pot=deg+(stk2-0.5)*7.0+(m2-0.5)*11.0+(m4-0.5)*7.0+(m1-0.5)*8.0+(m3-0.5)*6.0;
    rk=smoothstep(27.0,36.0,pot)*0.92;
    tone=(0.82+0.36*m1)*(0.7+0.6*stk); }
  vec3 colS=col;
  col=mix(col,mix(uRockS,uRock,t)*tone,rk);
  if(wb>0.0){                          // the model's rock, from the band's rock potential (the same lines as the model)
    float stM=texture(uTile,vPos.xz/160.0).b*fall.y+texture(uTile,vPos.zx/160.0).b*fall.x;
    float rkM=smoothstep(28.5,38.0,18.0+30.0*bb.a+(stM-0.5)*12.0)*0.9;
    vec3 rockM=mix(uRockS,uRock,t)*(0.82+0.36*texture(uTile,vPos.xz/2400.0).b)*(0.7+0.6*stM);
    col=mix(col,mix(colS,rockM,rkM),wb); }
  col*=occ;
  col=mix(col,uDim.rgb,uDim.a);
  float d=length(vPos-uCam);
  float far=airOf(d,vPos.y);
  float low=smoothstep(2300.0,1750.0,vPos.y)*uValley;
  col=mix(col,uHor,clamp(far+low*(1.0-far),0.0,0.97));
  // its last 5 km go into the air, where the far horizon takes over
  col=mix(col,uHor,smoothstep(uRingR-5000.0,uRingR-500.0,max(abs(vPos.x),abs(vPos.z))));
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
  // under the far strip the eye sees the ground past the model's edge (the farthest pinch, Pini on 0.1.867): flat snow
  // lit by the hour and in the far air, never the horizon's pink (the architect's condition 6)
  if(el<uStrip.x+1.5){
    float tf=pow(max(uSun.y,0.0)*uDirect+(1.0-uDirect)*0.55,0.85);
    vec3 gf=mix(uShade+(uLit-uShade*0.86)*tf,uHor,clamp(uAir.x+uAir.y*0.2,0.0,0.9));
    c=mix(c,gf,smoothstep(uStrip.x+1.5,uStrip.x,el));
  }
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

// the village from the blue hour on: a small warm square for every real building (peak-live.json, village), larger close up
private const val DOT_VS = """#version 300 es
layout(location=0) in vec3 aPos; uniform mat4 uMvp; uniform float uSize;
void main(){ gl_Position=uMvp*vec4(aPos,1.0); gl_PointSize=clamp(uSize*7000.0/gl_Position.w,uSize,uSize*4.0); }"""
private const val DOT_FS = """#version 300 es
precision mediump float; uniform vec4 uCol; out vec4 o;
void main(){ vec2 p=abs(gl_PointCoord*2.0-1.0); float e=max(p.x,p.y); o=vec4(uCol.rgb, uCol.a*(1.0-smoothstep(0.7,1.0,e))); }"""

private const val LINE_VS = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aPrev;
layout(location=2) in vec3 aNext;
layout(location=3) in float aSide;
layout(location=4) in vec4 aColor;
uniform mat4 uMvp; uniform vec2 uRes; uniform float uWidth; uniform float uBias;
layout(location=5) in float aProg;
out vec4 vCol; out float vSide; out float vProg;
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
  gl_Position=c; vCol=aColor; vSide=aSide; vProg=aProg;
}"""

// uTint: the colour (rgb) and how much it replaces the line's own (a); uSoft: a glow that fades to the edges
private const val LINE_FS = """#version 300 es
precision mediump float;
in vec4 vCol; in float vSide; in highp float vProg; uniform float uAlpha; uniform vec4 uTint; uniform float uSoft; uniform highp float uReveal; uniform highp vec2 uDash; out vec4 o;
void main(){
  if(vProg>uReveal) discard;
  if(uDash.x>0.0 && mod(vProg, uDash.x+uDash.y)>uDash.x) discard;
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
        attr(0, 3, s, 0); attr(1, 3, s, 12); attr(2, 3, s, 24); attr(3, 1, s, 36); attr(4, 4, s, 40); attr(5, 1, s, 56)
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

/**
 * The mountain raised by [px] on the screen (the shelf and "from the station", K06 and K07: 120 points), the view's
 * direction and distance kept: the clip space moved up, so the picture and every place worked out from it move together.
 */
internal fun shiftUp(m: FloatArray, px: Float, h: Int) {
    if (px == 0f || h == 0) return
    val k = 2f * px / h
    for (c in 0..3) m[c * 4 + 1] += k * m[c * 4 + 3]
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
    @Volatile var drawn = false; private set
    /** The chosen run (by key), drawn as a line of light by day and of brighter light at night; the rest a little dimmer. */
    @Volatile var chosen: String? = null
    /** The mountain raised on the screen, in pixels (see [shiftUp]). */
    @Volatile var shiftPx = 0f
    /** The cast shadows now (1 in the mountain's shade), for the lens and the shelf "in the sun now". */
    fun shadowNow(): FloatArray? = shadow
    private val liftNames = ArrayList<String>()
    /**
     * The shelf's and the station's light (K06, K07): [lit] runs as lines of light in their colour ([core] dp on a
     * white case of [case] dp), every other run a ghost, the lifts dimmed but [liftsOn]. Null: the runs as usual.
     */
    class Focus(val lit: Set<String>, val core: Float, val case: Float, val liftsOn: Set<String>)
    @Volatile var focus: Focus? = null
    /**
     * K08, a lens: each run drawn by its [LensLine] instead of its colour (a run with none is not drawn), the lifts dimmed.
     * [case] the lens's case colour (white by day, #081020 at night).
     */
    class LensLine(val ribbon: io.github.pini236.skiapp.map.Ribbon?, val rgb: FloatArray?, val width: Float, val alpha: Float, val cased: Boolean,
                   val halo: FloatArray? = null, val haloW: Float = 9f, val dashed: Boolean = false)
    class Lens(val lines: Map<String, LensLine>, val case: FloatArray)
    @Volatile var lens: Lens? = null
    private var lensGpu = HashMap<String, Gpu>(); private var lensGpuOf: Lens? = null
    @Volatile var pal: PeakData.Pal? = null
    /** Roads, water and the village (Drape.kt without contours), kept to be uploaded again with a new context. */
    @Volatile var drape: android.graphics.Bitmap? = null
    private val drapeTex = IntArray(1); private var drapeOn: android.graphics.Bitmap? = null
    /** The slope colours around the chosen run (MapScene.slopeLayer), worked out off the GL thread. */
    @Volatile var ground: io.github.pini236.skiapp.map.SlopeLayer? = null
    /** The chosen run in slope colours point by point (as phase A), built off the GL thread. */
    @Volatile var chosenLine: Pair<String, io.github.pini236.skiapp.map.Ribbon>? = null
    private var chosenGpu: Gpu? = null; private var chosenGpuOf: Pair<String, io.github.pini236.skiapp.map.Ribbon>? = null
    private val groundTex = IntArray(1); private var groundOn: io.github.pini236.skiapp.map.SlopeLayer? = null
    /**
     * Round 4 (board L06): the day painted on the mountain. [light] holds the descents with each point's speed colour (its
     * case is the same buffer drawn wider in one colour), [lifts] the dotted chords of the rides. Built off the GL thread
     * (LiveView.showDay); while it is set the runs and the cables are not drawn, as on the board.
     */
    class DayLines(val light: Ribbon, val lifts: Ribbon, val night: Boolean, val withRuns: Boolean = false)
    @Volatile var day: DayLines? = null
    /** The runs and lifts quiet under the meeting point (Q04, Q05: low opacity), 1 otherwise. */
    @Volatile var linesQuiet = 1f
    /** The runs alone, under the lifts' quiet (Q10: the lifts low, no runs; the trip and the ski pass: neither). */
    @Volatile var runsQuiet = 1f
    /** How far the day is drawn (the film, board L08), 0 to 1 of the day's time; above 1 all of it. */
    @Volatile var dayReveal = 60000f
    private var dayGpu: Pair<Gpu, Gpu>? = null; private var dayGpuOf: DayLines? = null; private var dayLogged: DayLines? = null
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

    /**
     * A camera move (the opening dolly, a double tap, the turn to the Kobi side): from, to, [t, seconds], a pitch lift
     * in the middle (radians, times sin(pi t), the handoff's section 10) and what follows it (called on the GL thread).
     */
    private class Anim(val from: OrbitCamera.State, val to: OrbitCamera.State, val tt: FloatArray, val lift: Float, val then: (() -> Unit)?)
    @Volatile private var anim: Anim? = null
    fun animate(to: OrbitCamera.State, seconds: Float, lift: Float = 0f, then: (() -> Unit)? = null) {
        anim = Anim(camera.state(), to, floatArrayOf(0f, seconds), lift, then); requestRender()
    }
    fun stopAnim() { anim = null }
    /** When the chosen run's light starts down the line (the handoff's section 6: 600 ms, from the top). */
    @Volatile var revealFrom = 0L
    /** The emulator's held moment (ms since the choice, the designer's 0 to 900): no time passes, as phase A's frame:. */
    @Volatile var frozenMs: Float? = null
    private var heldDrawn: Float? = null
    /** Ms since the chosen run's light started down it (0 until its line is ready), or the held moment. */
    fun chosenMs(): Float = frozenMs ?: if (revealFrom == 0L) 0f else (System.currentTimeMillis() - revealFrom).toFloat()

    fun setShadow(s: FloatArray) { shadow = s; shadowDirty = true; requestRender() }
    @Volatile private var shadowDirty = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = program(VS, FS); skyProg = program(SKY_VS, SKY_FS); ringProg = program(RING_VS, RING_FS); lineProg = program(LINE_VS, LINE_FS); dotProg = program(DOT_VS, DOT_FS)
        uploaded = false; texOn = false; tex.fill(0); runs.clear(); lifts.clear(); dotCount = 0; ringOn = null; ringCount = 0; ringTex[0] = 0; bandTex[0] = 0; bandBox = null
        drapeTex[0] = 0; drapeOn = null; groundTex[0] = 0; groundOn = null; chosenGpu = null; chosenGpuOf = null
        dayGpu = null; dayGpuOf = null; dayLogged = null
        lensGpu.clear(); lensGpuOf = null
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
        runs.forEach { it.second.free(); it.third.free() }; lifts.forEach { it.free() }; runs.clear(); lifts.clear(); liftNames.clear()
        lensGpu.values.forEach { it.free() }; lensGpu.clear(); lensGpuOf = null
        if (ringCount > 0) { glDeleteBuffers(3, ringBufs, 0); glDeleteVertexArrays(1, ringVao, 0) }; ringCount = 0; ringOn = null
        if (ringTex[0] != 0) glDeleteTextures(1, ringTex, 0); ringTex[0] = 0
        if (texOn) glDeleteTextures(5, tex, 0)
        if (drapeTex[0] != 0) glDeleteTextures(1, drapeTex, 0)
        if (groundTex[0] != 0) glDeleteTextures(1, groundTex, 0)
        drapeTex[0] = 0; drapeOn = null; groundTex[0] = 0; groundOn = null
        chosenGpu?.free(); chosenGpu = null; chosenGpuOf = null
        dayGpu?.let { it.first.free(); it.second.free() }; dayGpu = null; dayGpuOf = null; dayLogged = null
        drape?.recycle(); drape = null; ground = null
        tex.fill(0); texOn = false; uploaded = false; dotCount = 0
        for (p in intArrayOf(prog, skyProg, lineProg, dotProg, ringProg)) if (p != 0) glDeleteProgram(p)
        prog = 0; skyProg = 0; lineProg = 0; dotProg = 0; ringProg = 0
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
        for ((n, r) in scene.liftRibbons) { lifts += Gpu(r); liftNames += n }
        uploaded = true
    }

    /** The far ground (LiveRing), set by LiveView once it is built off the GL thread; null until then (or never). */
    @Volatile var ring: LiveRing? = null
    @Volatile var ringShown = true
    private val RING_FROM = 7000f
    private var ringProg = 0; private val ringVao = IntArray(1); private val ringBufs = IntArray(3)
    private var ringOn: LiveRing? = null; private var ringCount = 0
    private val ringTex = IntArray(1)
    /** The band of the front seam (step 4): one RGBA texture; 0 until the ring brings it. */
    private val bandTex = IntArray(1)
    private var bandBox: FloatArray? = null
    /** The ring's cast shadow (LiveRing.shadows), set off the GL thread; uploaded on the next frame. */
    @Volatile private var ringShade: FloatArray? = null
    @Volatile private var ringShadowDirty = false
    fun setRingShadow(s: FloatArray) { ringShade = s; ringShadowDirty = true; requestRender() }
    /** The air of height (uAir2) on the mountain and the ring; the emulator turns it off for a picture before (qa air:off). */
    @Volatile var airOn = true
    private fun air(p: Int, dist: Float) = if (airOn) glUniform4f(u(p, "uAir2"), dist * 1.5f, 25000f, 2800f, .35f) else glUniform4f(u(p, "uAir2"), 0f, 0f, 0f, 0f)

    private fun uploadRing(r: LiveRing) {
        if (ringCount > 0) { glDeleteBuffers(3, ringBufs, 0); glDeleteVertexArrays(1, ringVao, 0) }
        if (ringTex[0] != 0) { glDeleteTextures(1, ringTex, 0); ringTex[0] = 0 }
        if (bandTex[0] != 0) { glDeleteTextures(1, bandTex, 0); bandTex[0] = 0; bandBox = null }
        glGenVertexArrays(1, ringVao, 0); glBindVertexArray(ringVao[0])
        glGenBuffers(3, ringBufs, 0)
        glBindBuffer(GL_ARRAY_BUFFER, ringBufs[0]); glBufferData(GL_ARRAY_BUFFER, r.vertices.size * 4, floats(r.vertices), GL_STATIC_DRAW)
        attr(0, 3, LiveRing.STRIDE * 4, 0); attr(1, 3, LiveRing.STRIDE * 4, 12)
        // the cast shadow, one float a vertex, written again every 10 minutes of the sun (none until the first)
        glBindBuffer(GL_ARRAY_BUFFER, ringBufs[2]); glBufferData(GL_ARRAY_BUFFER, r.points * 4, floats(ringShade?.takeIf { it.size == r.points } ?: FloatArray(r.points)), GL_DYNAMIC_DRAW)
        attr(2, 1, 4, 0); ringShadowDirty = false
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ringBufs[1]); glBufferData(GL_ELEMENT_ARRAY_BUFFER, r.indices.size * 4, ints(r.indices), GL_STATIC_DRAW)
        glBindVertexArray(0)
        // the relief map: RGBA8 with mipmaps, as relief.webp (the architect's sixth texture)
        r.relief?.let { t ->
            glGenTextures(1, ringTex, 0); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, ringTex[0])
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, t.w, t.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap(t.px))
            glGenerateMipmap(GL_TEXTURE_2D)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        }
        // the band (step 4): RGBA8 with mipmaps, no flip (row 0 north, as the shaders read)
        r.band?.let { b ->
            glGenTextures(1, bandTex, 0); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, bandTex[0])
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, b.w, b.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap(b.px))
            glGenerateMipmap(GL_TEXTURE_2D)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            bandBox = floatArrayOf(b.x0, b.z0, 1f / (b.x1 - b.x0), 1f / (b.z1 - b.z0), b.margin)
        }
        // the model's last 1,500 m bent to the ring's heights (step 4): its positions written again, once
        if (uploaded) {
            glBindBuffer(GL_ARRAY_BUFFER, bufs[0]); glBufferSubData(GL_ARRAY_BUFFER, 0, scene.mesh.vertices.size * 4, floats(r.bend(scene.mesh.vertices, TerrainMesh.STRIDE)))
            glBindBuffer(GL_ARRAY_BUFFER, 0)
        }
        ringOn = r; ringCount = r.indices.size
    }

    private fun uploadVillage(v: FloatArray) {
        glGenVertexArrays(1, dots, 0); glBindVertexArray(dots[0])
        glGenBuffers(1, dots, 1)
        glBindBuffer(GL_ARRAY_BUFFER, dots[1]); glBufferData(GL_ARRAY_BUFFER, v.size * 4, floats(v), GL_STATIC_DRAW)
        attr(0, 3, 12, 0)
        glBindVertexArray(0)
        dotCount = v.size / 3
    }

    /** Roads, water and the village to the GPU, with mipmaps so far away they fade and do not shimmer (as the map's). */
    private fun uploadDrape(b: android.graphics.Bitmap) {
        glActiveTexture(GL_TEXTURE3)
        if (drapeTex[0] == 0) glGenTextures(1, drapeTex, 0)
        glBindTexture(GL_TEXTURE_2D, drapeTex[0])
        android.opengl.GLUtils.texImage2D(GL_TEXTURE_2D, 0, b, 0)
        glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        drapeOn = b
    }

    private fun uploadGround(g: io.github.pini236.skiapp.map.SlopeLayer) {
        glActiveTexture(GL_TEXTURE4)
        if (groundTex[0] == 0) glGenTextures(1, groundTex, 0)
        glBindTexture(GL_TEXTURE_2D, groundTex[0])
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g.w, g.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap(g.rgba))
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        groundOn = g
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
        // (a second or so after the screen comes back, when the context went: the sky of this hour, never a day blue at
        // night; the review of 24.11, ש7)
        if (!texOn || L == null || D == null) {
            L?.hor?.let { h -> glClearColor(h[0], h[1], h[2], 1f) }
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT); return
        }

        var moving = false
        if (frozenMs == null) anim?.let { a ->
            val from = a.from; val to = a.to; val tt = a.tt
            tt[0] += dt
            val k = (tt[0] / tt[1]).coerceIn(0f, 1f); val e = k * k * (3 - 2 * k)
            fun l(x: Float, y: Float) = x + (y - x) * e
            val lift = a.lift * kotlin.math.sin(Math.PI * e).toFloat()
            camera.set(OrbitCamera.State(l(from.tx, to.tx), l(from.ty, to.ty), l(from.tz, to.tz), l(from.dist, to.dist), l(from.yaw, to.yaw), l(from.pitch, to.pitch) + lift))
            if (k >= 1f) { if (anim === a) anim = null; a.then?.invoke() } else moving = true
        }
        if (camera.step(dt)) moving = true
        val t = scene.terrain
        camera.clampKeep(t.x0 + 600, t.x1 - 600, t.y0 + 600, t.y1 - 600) { x, z -> t.elev(x, z) }
        val dimTarget = if (chosen != null) 1f else 0f
        val fz = frozenMs
        if (fz != null && chosen != null) dim = (fz / 600f).coerceIn(0f, 1f)
        else if (dim != dimTarget) { dim += (dimTarget - dim) * (1f - Math.exp(-9.0 * dt).toFloat()); if (kotlin.math.abs(dim - dimTarget) < .01f) dim = dimTarget else moving = true }

        val s = camera.state()
        OrbitCamera.mvp(s, width.toFloat() / height, mvp, eye, D.camera.fovDeg) { x, z -> t.elev(x, z) }; shiftUp(mvp, shiftPx, height)
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
        val dr = drape
        if (dr != null && dr !== drapeOn && !dr.isRecycled) uploadDrape(dr)
        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, drapeTex[0]); glUniform1i(u(prog, "uDrape"), 3)
        glUniform1f(u(prog, "uDrapeOn"), if (drapeOn != null && drapeTex[0] != 0) 1f else 0f)
        val tt = scene.terrain
        glUniform1f(u(prog, "uDrapeK"), if (L.stars >= .25f) .5f else .35f)
        glUniform4f(u(prog, "uDrapeBox"), tt.x0, tt.y0, 1f / (tt.x1 - tt.x0), 1f / (tt.y1 - tt.y0))
        val gr = if (chosen != null) ground else null
        if (gr != null && gr !== groundOn) uploadGround(gr)
        glActiveTexture(GL_TEXTURE4); glBindTexture(GL_TEXTURE_2D, groundTex[0]); glUniform1i(u(prog, "uGround"), 4)
        val go = groundOn
        glUniform1f(u(prog, "uGroundOn"), if (gr != null && go === gr) 1f else 0f)
        if (go != null) glUniform4f(u(prog, "uGroundBox"), go.x0, go.z0, 1f / go.width, 1f / go.depth)
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
        air(prog, s.dist)
        // the rest of the mountain under a chosen run (phase A: rgb(54,78,128) at 0.42 by day, rgb(2,6,18) at 0.62 at night)
        if (night) glUniform4f(u(prog, "uDim"), 2f / 255, 6f / 255, 18f / 255, .62f * dim)
        else glUniform4f(u(prog, "uDim"), 54f / 255, 78f / 255, 128f / 255, .42f * dim)
        // the front seam (step 4): the band, once the ring has brought it (the ring is uploaded before the mountain draws)
        ring?.let { r -> if (r !== ringOn) uploadRing(r) }
        val bx = bandBox
        glUniform2f(u(prog, "uSeam"), if (bx != null) 1f else 0f, LiveRing.SEAM_IN)
        glActiveTexture(GL_TEXTURE5); glBindTexture(GL_TEXTURE_2D, bandTex[0]); glUniform1i(u(prog, "uBand"), 5)
        if (bx != null) glUniform4f(u(prog, "uBBox"), bx[0], bx[1], bx[2], bx[3])
        glBindVertexArray(vao[0]); glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0)
        glActiveTexture(GL_TEXTURE0)
        // the far ground past the model's edge, after it (its middle lies under the mesh): one draw, no textures
        ring?.let { r -> if (r !== ringOn) uploadRing(r) }
        // only when the eye can see past the model: from outside it (the opening stands south of it) or from far; low
        // in the model, as in the film, the mesh hides it and it would cost the frame for nothing (run 419)
        val past = eye[0] < tt.x0 || eye[0] > tt.x1 || eye[2] < tt.y0 || eye[2] > tt.y1 || s.dist > RING_FROM
        if (ringCount > 0 && ringShown && past) {
            glUseProgram(ringProg)
            glUniformMatrix4fv(u(ringProg, "uMvp"), 1, false, mvp, 0)
            glUniform3fv(u(ringProg, "uSun"), 1, L.dir, 0)
            glUniform3fv(u(ringProg, "uLit"), 1, L.lit, 0); glUniform3fv(u(ringProg, "uShade"), 1, L.shade, 0)
            glUniform3fv(u(ringProg, "uRock"), 1, L.rock, 0); glUniform3fv(u(ringProg, "uRockS"), 1, L.rockS, 0); glUniform3fv(u(ringProg, "uHor"), 1, L.hor, 0)
            glUniform1f(u(ringProg, "uDirect"), L.direct); glUniform3fv(u(ringProg, "uCam"), 1, eye, 0)
            glUniform1f(u(ringProg, "uGlow"), L.glow); glUniform1f(u(ringProg, "uRingR"), ringOn?.half ?: 1e9f)
            glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, tex[2]); glUniform1i(u(ringProg, "uTile"), 2); glActiveTexture(GL_TEXTURE0)
            glUniform3f(u(ringProg, "uHaze"), D.haze[0] * s.dist, D.haze[1] * s.dist, D.haze[2]); glUniform1f(u(ringProg, "uValley"), D.valley)
            air(ringProg, s.dist)
            val rb = ringOn!!.reliefBox; val mb = ringOn!!.model
            glUniform4f(u(ringProg, "uRBox"), rb[0], rb[1], 1f / (rb[2] - rb[0]), 1f / (rb[3] - rb[1]))
            glUniform4f(u(ringProg, "uMBox"), mb[0], mb[1], mb[2], mb[3])
            glUniform1f(u(ringProg, "uRTex"), if (ringTex[0] != 0) 1f else 0f)
            if (ringTex[0] != 0) { glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, ringTex[0]); glUniform1i(u(ringProg, "uRRel"), 3); glActiveTexture(GL_TEXTURE0) }
            val rbx = bandBox
            glUniform2f(u(ringProg, "uSeam"), if (rbx != null) 1f else 0f, rbx?.get(4) ?: 0f); glUniform1f(u(ringProg, "uRip"), D.ripple)
            glActiveTexture(GL_TEXTURE4); glBindTexture(GL_TEXTURE_2D, bandTex[0]); glUniform1i(u(ringProg, "uBand"), 4); glActiveTexture(GL_TEXTURE0)
            if (rbx != null) glUniform4f(u(ringProg, "uBBox"), rbx[0], rbx[1], rbx[2], rbx[3])
            if (ringShadowDirty) ringShade?.takeIf { it.size == ringOn!!.points }?.let { sh ->
                glBindBuffer(GL_ARRAY_BUFFER, ringBufs[2]); glBufferSubData(GL_ARRAY_BUFFER, 0, sh.size * 4, floats(sh)); ringShadowDirty = false
            }
            if (night) glUniform4f(u(ringProg, "uDim"), 2f / 255, 6f / 255, 18f / 255, .62f * dim)
            else glUniform4f(u(ringProg, "uDim"), 54f / 255, 78f / 255, 128f / 255, .42f * dim)
            glBindVertexArray(ringVao[0]); glDrawElements(GL_TRIANGLES, ringCount, GL_UNSIGNED_INT, 0)
        }

        // the live layer: the runs (a white case and the colour), the lifts' cables, the chosen run as light
        val p = pal
        // the ribbons are widened on the screen, so half their triangles face away: no culling for them (as the map)
        glDisable(GL_CULL_FACE)
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDepthMask(false)
        // the village, lit from the blue hour on (the night key's stars 1, the blue key's .25)
        if (L.stars > .05f && dotCount > 0) {
            glUseProgram(dotProg)
            glUniformMatrix4fv(u(dotProg, "uMvp"), 1, false, mvp, 0)
            glUniform1f(u(dotProg, "uSize"), 2.2f * density)
            glUniform4f(u(dotProg, "uCol"), 1f, .773f, .431f, minOf(L.stars / .25f, 1f) * (.6f + .4f * minOf(L.stars, 1f)))
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
        // lifted towards the camera as the map lifts its own (0.0006 to 0.0014): at 0.0004 the terrain hid the lines in
        // the emulator's frames, all but specks on the ridges
        val uR = u(lineProg, "uReveal"); glUniform1f(uR, 60000f)
        // a dashed line (K08's closed runs): its progress in metres, the dash "6 5" in screen points at this distance
        val uD = u(lineProg, "uDash"); glUniform2f(uD, 0f, 0f)
        val mpp = s.dist * 2f * kotlin.math.tan(Math.toRadians(D.camera.fovDeg / 2.0)).toFloat() / height.coerceAtLeast(1)
        glUniform1f(uB, 0.0008f); glUniform1f(uS, 0f)
        val rest = 1f - .55f * dim
        val dl = day
        if (dl != null && dl !== dayGpuOf) { dayGpu?.let { it.first.free(); it.second.free() }; dayGpu = Gpu(dl.light) to Gpu(dl.lifts); dayGpuOf = dl }
        if (dl == null && dayGpu != null) { dayGpu?.let { it.first.free(); it.second.free() }; dayGpu = null; dayGpuOf = null }
        // the day painted (board L06) has no runs on it; the day while it is recorded (L03) has them, quiet, under it
        val showRuns = dl == null || dl.withRuns
        val quiet = (if (dl != null) .45f else 1f) * linesQuiet
        val fo = focus.takeIf { chosen == null && dl == null }
        val ln = lens.takeIf { chosen == null && dl == null && fo == null }
        if (ln !== lensGpuOf) { lensGpu.values.forEach { it.free() }; lensGpu.clear(); lensGpuOf = ln }
        glUniform4f(uT, wire[0], wire[1], wire[2], 1f); glUniform1f(uW, 1.3f * density * zw); glUniform1f(uA, .85f * rest * quiet)
        if (showRuns) for ((i, g) in lifts.withIndex()) {
            if (fo != null) glUniform1f(uA, .85f * quiet * (if (liftNames.getOrNull(i) in fo.liftsOn) 1f else .3f))
            if (ln != null) glUniform1f(uA, .85f * .3f * quiet)
            g.draw()
        }
        // a lens (K08): the weak lines first, then each strong one's halo, case and colour (point by point when it has a ribbon)
        if (showRuns && ln != null) {
            for (strong in listOf(false, true)) for ((key, cs, core) in runs) {
                val l = ln.lines[key] ?: continue
                if (l.cased != strong) continue
                val g = l.ribbon?.let { r -> lensGpu.getOrPut(key) { Gpu(r) } } ?: core
                glUniform1f(uB, 0.0012f)
                if (l.halo != null) {
                    glUniform1f(uS, 1f); glUniform4f(uT, l.halo[0], l.halo[1], l.halo[2], 1f)
                    glUniform1f(uW, l.haloW * density * zw); glUniform1f(uA, l.halo[3] * quiet); core.draw(); glUniform1f(uS, 0f)
                }
                if (l.cased) { glUniform4f(uT, ln.case[0], ln.case[1], ln.case[2], 1f); glUniform1f(uW, (l.width + 2f) * density * zw); glUniform1f(uA, quiet); cs.draw() }
                if (l.rgb != null) glUniform4f(uT, l.rgb[0], l.rgb[1], l.rgb[2], 1f) else glUniform4f(uT, 0f, 0f, 0f, 0f)
                if (l.dashed) glUniform2f(uD, 6f * density * mpp, 5f * density * mpp)
                glUniform1f(uW, l.width * density * zw); glUniform1f(uA, l.alpha * quiet); g.draw()
                if (l.dashed) glUniform2f(uD, 0f, 0f)
            }
        }
        // the shelf's and the station's ghosts (K06, K07): the colour faint, no case
        if (showRuns && fo != null) for ((key, _, core) in runs) {
            if (key in fo.lit) continue
            glUniform1f(uB, 0.0012f)
            val c = runRgb[key]
            if (c != null) glUniform4f(uT, c[0], c[1], c[2], 1f) else glUniform4f(uT, 0f, 0f, 0f, 0f)
            glUniform1f(uW, 1.8f * density * zw); glUniform1f(uA, .3f * quiet); core.draw()
        }
        if (showRuns && fo == null && ln == null) for ((key, cs, core) in runs) {
            if (key == chosen) continue
            glUniform1f(uB, 0.0010f)
            glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, 3.6f * density * zw); glUniform1f(uA, (if (night) .7f else .95f) * rest * quiet * runsQuiet); cs.draw()
            glUniform1f(uB, 0.0012f)
            val c = runRgb[key]
            if (c != null) glUniform4f(uT, c[0], c[1], c[2], 1f) else glUniform4f(uT, 0f, 0f, 0f, 0f)
            glUniform1f(uW, 2.2f * density * zw); glUniform1f(uA, rest * quiet * runsQuiet); core.draw()
        }
        if (showRuns && fo != null) {
            glDisable(GL_DEPTH_TEST)
            for ((key, cs, core) in runs) {
                if (key !in fo.lit) continue
                glUniform1f(uB, 0.0012f)
                glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, fo.case * density * zw); glUniform1f(uA, quiet); cs.draw()
                val c = runRgb[key] ?: case
                glUniform4f(uT, c[0], c[1], c[2], 1f); glUniform1f(uW, fo.core * density * zw); glUniform1f(uA, quiet); core.draw()
            }
            glEnable(GL_DEPTH_TEST)
        }
        val dg = dayGpu.takeIf { dl != null }
        if (dg != null && dl != null) {
            // the day (board L06, day4.py): the rides as a dotted straight line over everything, then the descents, a case
            // 2.2 wider and the light coloured by speed; hidden where the mountain is in front, as on the board
            val lc = if (dl.night) DayPaint.LIFT_NIGHT else DayPaint.LIFT_DAY
            glUniform1f(uR, dayReveal)
            glDisable(GL_DEPTH_TEST)
            glUniform4f(uT, rgb(lc, 0), rgb(lc, 8), rgb(lc, 16), 1f); glUniform1f(uW, 1.4f * density * zw); glUniform1f(uA, .8f); dg.second.draw()
            glEnable(GL_DEPTH_TEST)
            val cc = if (dl.night) DayPaint.CASE_NIGHT else DayPaint.CASE_DAY
            glUniform1f(uB, 0.0012f)
            glUniform4f(uT, rgb(cc, 0), rgb(cc, 8), rgb(cc, 16), 1f); glUniform1f(uW, 5.2f * density * zw); glUniform1f(uA, .9f); dg.first.draw()
            glUniform1f(uB, 0.0014f)
            glUniform4f(uT, 0f, 0f, 0f, 0f); glUniform1f(uW, 3f * density * zw); glUniform1f(uA, 1f); dg.first.draw()
            glUniform1f(uB, 0.0008f); glUniform1f(uR, 60000f)
        }
        // the chosen run, as phase A: a white case and the line in the slope's colours, point by point; at night a warm
        // glow under it (the beads of K11). No glow by day (the designer, run 292)
        chosen?.let { k ->
            val r = runs.firstOrNull { it.first == k } ?: return@let
            val cl = chosenLine?.takeIf { it.first == k }
            if (cl != null && cl !== chosenGpuOf) { chosenGpu?.free(); chosenGpu = Gpu(cl.second); chosenGpuOf = cl }
            glDisable(GL_DEPTH_TEST)
            if (night) {
                glUniform1f(uS, 1f); glUniform4f(uT, WARM_NIGHT[0], WARM_NIGHT[1], WARM_NIGHT[2], 1f)
                glUniform1f(uW, 16f * density * zw); glUniform1f(uA, .55f); r.second.draw()
                glUniform1f(uS, 0f)
            }
            // at night the line of light gives way to the beads of light (K11 c, drawn over the model) once the light
            // is down it: 600 ms, then 200 ms
            val ms = chosenMs()
            val la = if (night) 1f - ((ms - 600f) / 200f).coerceIn(0f, 1f) else 1f
            if (frozenMs == null && ms < 800f) moving = true
            if (la > 0f) {
                val g = chosenGpu.takeIf { chosenGpuOf?.first == k }
                val rv = (ms / 600f).coerceIn(0f, 1f)
                if (g != null) {
                    // the light running down it (K09, as phase A): a white halo and the white case grow with the light
                    // from the top, the halo going once the light is down (200 ms)
                    glUniform1f(uR, rv)
                    val ha = .35f * (1f - ((ms - 600f) / 200f).coerceIn(0f, 1f))
                    if (ha > 0f) { glUniform1f(uS, 1f); glUniform4f(uT, 1f, 1f, 1f, 1f); glUniform1f(uW, 16f * density * zw); glUniform1f(uA, ha * la); g.draw(); glUniform1f(uS, 0f) }
                    glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, 6f * density * zw); glUniform1f(uA, la); g.draw()
                    glUniform1f(uW, 3.6f * density * zw)
                    glUniform4f(uT, 0f, 0f, 0f, 0f); g.draw(); glUniform1f(uR, 60000f)
                }
                else {
                    // the line not built yet (a moment): the run's own case and colour
                    glUniform4f(uT, case[0], case[1], case[2], 1f); glUniform1f(uW, 6f * density * zw); glUniform1f(uA, la); r.second.draw()
                    glUniform1f(uW, 3.6f * density * zw); val c = runRgb[k] ?: case; glUniform4f(uT, c[0], c[1], c[2], 1f); r.third.draw()
                }
                glUniform1f(uA, 1f)
            }
            glEnable(GL_DEPTH_TEST)
        }
        glDepthMask(true); glDisable(GL_BLEND); glEnable(GL_CULL_FACE)
        // the emulator waits for this: a frame with the mountain, not only the clear colour
        if (!drawn) { drawn = true; io.github.pini236.skiapp.qa.Qa.log("redesign live drawn") }
        if (dg != null && dl !== dayLogged) { dayLogged = dl; io.github.pini236.skiapp.qa.Qa.log("redesign live day drawn ${dl!!.light.indices.size / 6} ${if (dl.night) "night" else "day"}") }
        // and, for a held moment, for a frame that has it: the run's line on the GPU and the time held
        val fh = frozenMs
        if (fh != null && chosenGpuOf?.first == chosen && heldDrawn != fh) { heldDrawn = fh; io.github.pini236.skiapp.qa.Qa.log("redesign live held ${fh.toInt()}") }
        if (fh == null) heldDrawn = null
        if (moving) requestRender()
    }

    private var palCache: PeakData.Pal? = null
    private val runRgb = HashMap<String, FloatArray>()
    private var caseRgb = floatArrayOf(1f, 1f, 1f); private var wireRgb = floatArrayOf(.13f, .19f, .25f)
    private val WARM_NIGHT = floatArrayOf(1f, .86f, .52f)

    /** One channel (0 to 1) of an 0xRRGGBB colour: [k] 0 for red, 8 green, 16 blue. */
    private fun rgb(c: Int, k: Int): Float = ((c shr (16 - k)) and 0xFF) / 255f

    private fun runColour(p: PeakData.Pal, c: String?): FloatArray? = when (c) {
        "green" -> hex(p.green); "blue" -> hex(p.blue); "red" -> hex(p.red); "black" -> hex(p.black); else -> null
    }
}

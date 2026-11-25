// the ring (alternative A of the edge board, 25.11.2026): the real terrain around the model, 240 m cells, lit like the snow of the model
precision highp float;
in vec3 vPos; in float vShadow; in vec3 vN;
uniform sampler2D uTile;
uniform vec3 uSun; uniform vec3 uLit; uniform vec3 uShade; uniform vec3 uRock; uniform vec3 uRockS; uniform vec3 uHor;
uniform float uDirect; uniform float uGlow; uniform vec3 uCam; uniform vec3 uHaze; uniform float uValley;
uniform float uRingR;                  // the half size of the ring: its last 5 km go into the air, where the far horizon takes over
uniform vec4 uAir2;                     // ring2 (25.11.2026): x = where the model's quick haze hands over (metres from the eye),
                                        // y = the length of the slow haze beyond it (0: off, as before), z..w = the thin air of the heights
float airOf(float d,float y){
  if(uAir2.y<=0.0) return uHaze.z*(1.0-exp(-max(d-uHaze.x,0.0)/uHaze.y));
  float dens=mix(1.0,uAir2.w,smoothstep(uAir2.z,uAir2.z+1800.0,y));          // high snow stands over the haze of the valleys
  float od=(clamp(d,uHaze.x,uAir2.x)-uHaze.x)/uHaze.y+max(d-uAir2.x,0.0)/uAir2.y;
  return uHaze.z*(1.0-exp(-od*dens));
}
uniform sampler2D uRRel; uniform vec4 uRBox; uniform float uRTex; uniform vec4 uMBox;   // ring2: the relief at 60 m (slopes, occlusion) over the inner box; uMBox: the model x0 z0 x1 z1
out vec4 o;
void main(){
  vec3 n0=normalize(vN); vec2 s=-n0.xz/max(n0.y,0.2); float occ=1.0;
  vec2 ruv=(vPos.xz-uRBox.xy)*uRBox.zw;
  if(uRTex>0.0 && ruv.x>0.0 && ruv.y>0.0 && ruv.x<1.0 && ruv.y<1.0){ vec3 rr=texture(uRRel,ruv).rgb;
    float om=max(max(uMBox.x-vPos.x,vPos.x-uMBox.z),max(uMBox.y-vPos.z,vPos.z-uMBox.w));
    float w=smoothstep(0.0,0.04,min(min(ruv.x,ruv.y),min(1.0-ruv.x,1.0-ruv.y)))*smoothstep(0.0,500.0,om);
    s=mix(s,(rr.rg-0.5)*3.2,w); occ=mix(1.0,0.7+0.6*rr.b,w); }
  float deg=degrees(atan(length(s)));
  s+=(texture(uTile,vPos.xz/640.0).rg-0.5)*0.5*1.6*(1.0-smoothstep(30.0,40.0,deg));   // the same wind ripples as on the model
  vec3 n=normalize(vec3(-s.x,1.0,-s.y));
  float lam=max(dot(n,uSun),0.0);
  float direct=lam*(1.0-0.94*vShadow)*uDirect+(1.0-uDirect)*0.55*n.y;
  float t=pow(direct,0.85);
  t*=mix(1.0,0.35+0.65*smoothstep(2350.0,3000.0,vPos.y),uGlow);
  vec3 col=uShade*(0.86+0.14*n.y)+(uLit-uShade*0.86)*t;
  float stk=texture(uTile,vPos.xz/160.0).b;
  float rk=smoothstep(27.0,36.0,deg+(stk-0.5)*6.0)*0.8;
  vec2 fall=s*s/(dot(s,s)+1e-4);
  float stk2=texture(uTile,vPos.xz/160.0).b*fall.y+texture(uTile,vPos.zx/160.0).b*fall.x;
  float tone=0.75+0.5*stk;
  if(uRTex>0.0){                       // ring2: the model's rock, with the grain that still shows from afar (the tile read large)
    float m1=texture(uTile,vPos.xz/2400.0).b, m2=texture(uTile,vPos.zx/900.0).b, m3=texture(uTile,vPos.xz/5200.0+0.37).r;
    float m4=texture(uTile,vPos.xz/420.0+0.61).r;
    stk=mix(m2,stk2,0.35);
    float pot=deg+(stk2-0.5)*7.0+(m2-0.5)*11.0+(m4-0.5)*7.0+(m1-0.5)*8.0+(m3-0.5)*6.0;
    rk=smoothstep(27.0,36.0,pot)*0.92;
    tone=(0.82+0.36*m1)*(0.7+0.6*stk); }
  col=mix(col,mix(uRockS,uRock,t)*tone,rk);
  col*=occ;
  float d=length(vPos-uCam);
  float far=airOf(d,vPos.y);
  float low=smoothstep(2300.0,1750.0,vPos.y)*uValley;
  col=mix(col,uHor,clamp(far+low*(1.0-far),0.0,0.97));
  float rim=smoothstep(uRingR-5000.0,uRingR-500.0,max(abs(vPos.x),abs(vPos.z)));
  col=mix(col,uHor,rim);
  col=clamp(col,0.0,1.0); col=col*col*(3.0-2.0*col)*0.55+col*0.45;
  o=vec4(col,1.0);
}

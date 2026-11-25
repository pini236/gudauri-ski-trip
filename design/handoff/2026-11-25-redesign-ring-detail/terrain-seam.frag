// the model's terrain shader (FS of the app), what the front seam adds (Y03, 25.11.2026). From design/redesign/tools/live_ref.html (tfs).
// uniforms:
uniform vec2 uSeam;                     // seam2 (25.11.2026): x = 1 on, y = metres inside the edge where the model hands over to the band
uniform sampler2D uBand; uniform vec4 uBBox;  // the band: the model's own maps carried past its edge (ringband.webp), and its box (x0, z0, 1/w, 1/h)
// in main, after rl = texture(relief) and cv = texture(cover):
  float wE=0.0; vec4 rr=vec4(rl,cv.r);
  if(uSeam.x>0.0){ vec2 qd=min(uv,1.0-uv)/uBox.zw; float te=1.0-clamp(min(qd.x,qd.y)/uSeam.y,0.0,1.0); wE=te*te*(3.0-2.0*te);
    rr=texture(uBand,(vPos.xz-uBBox.xy)*uBBox.zw); cv.r=mix(cv.r,rr.a,wE); }
// the slope that bends the normal (was: vec2 s=(rl.rg-0.5)*3.2):
  vec2 s=mix((rl.rg-0.5)*3.2,(rr.rg-0.5)*3.2,wE);
// occlusion (was: col*=0.7+0.6*rl.b):
  col*=0.7+0.6*mix(rl.b,rr.b,wE);                                   // hollows a little darker, crests a little lighter

/* Gudauri relief: terrain data helpers, top-view relief layers (SVG), and a 3D model (three.js r128). */
(function(){
'use strict';
const R={};
function b64(s,T){const bin=atob(s),u=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);return new T(u.buffer);}

/* ---------- terrain model ---------- */
R.load=function(T){
  const d=T.dem,H=b64(d.b64,Int16Array);
  const W=d.x1-d.x0,Hm=d.y1-d.y0,sx=W/(d.nx-1),sy=Hm/(d.ny-1);
  function elev(x,y){ // bilinear, meters, clamps to edge
    let c=(x-d.x0)/sx,r=(y-d.y0)/sy;
    c=Math.max(0,Math.min(d.nx-1.0001,c));r=Math.max(0,Math.min(d.ny-1.0001,r));
    const c0=c|0,r0=r|0,fc=c-c0,fr=r-r0,i=r0*d.nx+c0;
    return (H[i]*(1-fc)+H[i+1]*fc)*(1-fr)+(H[i+d.nx]*(1-fc)+H[i+d.nx+1]*fc)*fr;
  }
  // contours: [lev,n,x,y,dx,dy...]
  const C=b64(T.contours,Int16Array),contours=[];
  for(let i=0;i<C.length;){const lev=C[i],n=C[i+1];let x=C[i+2],y=C[i+3];const pts=[[x,y]];i+=4;
    for(let k=1;k<n;k++){x+=C[i];y+=C[i+1];pts.push([x,y]);i+=2;}contours.push({lev,pts});}
  const dec=a=>{const o=[];for(let i=0;i<a.length;i+=2)o.push([a[i],a[i+1]]);return o;};
  const env={roads:T.env.roads.map(r=>({k:r[0],pts:dec(r[1])})),water:T.env.water.map(w=>dec(w.g)),
    village:T.env.village.map(dec),rivers:T.env.rivers.map(dec),places:T.env.places};
  return {dem:d,H,W,Hm,sx,sy,elev,contours,env,peaks:T.peaks,hill:T.hill};
};

/* ---------- profile stats for a polyline set ---------- */
/* slope colours (approved thresholds: 15°, 25°, 30°) */
R.SLOPE=[[15,'#3FA85F','map.slope_upto15'],[25,'#F2C13D','15°–25°'],[30,'#F08A3C','25°–30°'],[91,'#DC3B33','map.slope_over30']];
R.slopeColor=deg=>R.SLOPE.find(x=>deg<x[0])[1];

/* the ground around a run, coloured by slope: a canvas over the run's box plus a margin.
   lines: [[x,y],...][] in projected metres. Alpha fades out towards `buffer` metres from the line. */
R.slopeCanvas=function(M,lines,opt){
  opt=opt||{};const buf=opt.buffer||150,px=opt.px||10,d=M.dem;
  let a=1e9,b=1e9,c=-1e9,e=-1e9;lines.forEach(L=>L.forEach(q=>{a=Math.min(a,q[0]);c=Math.max(c,q[0]);b=Math.min(b,q[1]);e=Math.max(e,q[1]);}));
  a=Math.max(d.x0,a-buf);b=Math.max(d.y0,b-buf);c=Math.min(d.x1,c+buf);e=Math.min(d.y1,e+buf);
  const w=Math.max(2,Math.round((c-a)/px)),h=Math.max(2,Math.round((e-b)/px));
  const cv=document.createElement('canvas');cv.width=w;cv.height=h;const g=cv.getContext('2d'),im=g.createImageData(w,h);
  const segs=[];lines.forEach(L=>{for(let i=1;i<L.length;i++)segs.push([L[i-1][0],L[i-1][1],L[i][0],L[i][1]]);});
  const dist=(x,y)=>{let m=1e9;for(const s of segs){const vx=s[2]-s[0],vy=s[3]-s[1],l=vx*vx+vy*vy;let t=l?((x-s[0])*vx+(y-s[1])*vy)/l:0;t=t<0?0:t>1?1:t;const dd=Math.hypot(x-s[0]-vx*t,y-s[1]-vy*t);if(dd<m)m=dd;}return m;};
  const hex=hc=>[1,3,5].map(i=>parseInt(hc.slice(i,i+2),16)),COL=R.SLOPE.map(z=>hex(z[1]));
  for(let j=0;j<h;j++)for(let i=0;i<w;i++){const x=a+(i+.5)*px,y=b+(j+.5)*px,dd=dist(x,y);if(dd>buf)continue;
    const gx=(M.elev(x+15,y)-M.elev(x-15,y))/30,gy=(M.elev(x,y+15)-M.elev(x,y-15))/30,deg=Math.atan(Math.hypot(gx,gy))*180/Math.PI;
    const k=R.SLOPE.findIndex(z=>deg<z[0]),o=(j*w+i)*4,cc=COL[k];
    im.data[o]=cc[0];im.data[o+1]=cc[1];im.data[o+2]=cc[2];im.data[o+3]=Math.round(255*Math.min(1,(buf-dd)/45)*0.6);}
  g.putImageData(im,0,0);
  return {canvas:cv,x0:a,y0:b,x1:c,y1:e};
};

/* the sun in Gudauri (UTC+4) for a date and a local hour: {alt, az} in degrees, az from north, clockwise */
R.sun=function(date,hour){
  const LAT=42.51,LON=44.495,TZ=4,rad=Math.PI/180;
  const n=Math.floor((Date.UTC(date.getUTCFullYear(),date.getUTCMonth(),date.getUTCDate())-Date.UTC(date.getUTCFullYear(),0,1))/864e5)+1;
  const dec=-23.44*Math.cos(2*Math.PI/365*(n+10))*rad,b=2*Math.PI/364*(n-81),eot=9.87*Math.sin(2*b)-7.53*Math.cos(b)-1.5*Math.sin(b);
  const ha=15*(hour+(LON-TZ*15)/15+eot/60-12)*rad,phi=LAT*rad;
  const alt=Math.asin(Math.sin(phi)*Math.sin(dec)+Math.cos(phi)*Math.cos(dec)*Math.cos(ha));
  const az=Math.atan2(Math.sin(ha),Math.cos(ha)*Math.sin(phi)-Math.tan(dec)*Math.cos(phi));
  return {alt:alt/rad,az:(az/rad+180)%360};
};

R.stats=function(M,lines){ // lines: [[x,y],...][]
  let top=-1e9,bot=1e9,maxG=0;
  lines.forEach(L=>{
    const s=[];let acc=0;
    for(let i=0;i<L.length;i++){if(i)acc+=Math.hypot(L[i][0]-L[i-1][0],L[i][1]-L[i-1][1]);s.push([acc,M.elev(L[i][0],L[i][1])]);}
    s.forEach(q=>{top=Math.max(top,q[1]);bot=Math.min(bot,q[1]);});
    // steepest ~100 m stretch
    for(let i=0,j=0;i<s.length;i++){while(j<s.length-1&&s[j][0]-s[i][0]<100)j++;const dl=s[j][0]-s[i][0];if(dl>=80)maxG=Math.max(maxG,Math.abs(s[j][1]-s[i][1])/dl);}
  });
  return {top:Math.round(top),bot:Math.round(bot),drop:Math.round(top-bot),maxG};
};

/* ---------- top view (SVG) ---------- */
const NS='http://www.w3.org/2000/svg';
function el(t,a,p){const e=document.createElementNS(NS,t);for(const k in a)e.setAttribute(k,a[k]);if(p)p.appendChild(e);return e;}
const dPath=(pts,close)=>pts.map((q,i)=>(i?'L':'M')+q[0]+' '+q[1]).join('')+(close?'Z':'');
R.svgRelief=function(M,root,before,opt){
  opt=opt||{};
  const g=el('g',{class:'relief'});root.insertBefore(g,before||root.firstChild);
  const d=M.dem;
  el('image',{href:M.hill.src,x:d.x0,y:d.y0,width:M.W,height:M.Hm,preserveAspectRatio:'none',class:'hill'},g);
  const gc=el('g',{class:'contours',fill:'none'},g);
  M.contours.forEach(c=>{const cls=c.lev%250===0?'ci':(c.lev%100===0?'c100':'c50');
    el('path',{d:dPath(c.pts),class:cls,'vector-effect':'non-scaling-stroke'},gc);});
  const ge=el('g',{class:'env'},g);
  M.env.village.forEach(p=>el('path',{d:dPath(p,true),class:'vill'},ge));
  M.env.rivers.forEach(p=>el('path',{d:dPath(p),class:'river','vector-effect':'non-scaling-stroke'},ge));
  M.env.water.forEach(p=>el('path',{d:dPath(p,true),class:'water','vector-effect':'non-scaling-stroke'},ge));
  const rs=[...M.env.roads].sort((a,b)=>b.k-a.k);
  rs.forEach(r=>el('path',{d:dPath(r.pts),class:'road rc','vector-effect':'non-scaling-stroke','data-k':r.k},ge));
  rs.forEach(r=>el('path',{d:dPath(r.pts),class:'road r'+r.k,'vector-effect':'non-scaling-stroke'},ge));
  return g;
};
// contour labels + peaks + places go in the label layer (collision-managed by the host)
R.svgMarks=function(M,lblRoot,markRoot,store){
  const peaks=[];
  M.peaks.forEach(p=>{
    const m=el('path',{d:'',class:p.pass?'pass-mk':'peak-mk'},markRoot);m._x=p.x;m._y=p.y;m._pass=p.pass;store.marks.push(m);
    const t=el('text',{x:p.x,y:p.y,class:'lbl peak','text-anchor':'middle'},lblRoot);
    const a=el('tspan',{},t);a.textContent=p.n;const b=el('tspan',{class:'ele'},t);b.textContent=' '+p.ele;
    t._below=true;peaks.push(t);
  });
  const places=M.env.places.filter(p=>['Gudauri','Kobi'].includes(p.n)).map(p=>{const t=el('text',{x:p.x,y:p.y,class:'lbl place-v','text-anchor':'middle'},lblRoot);t.textContent=p.n==='Gudauri'?T('map.place_gudauri'):'Kobi';return t;});
  return {peaks,places};
};

/* ---------- 3D ---------- */
R.View3D=function(opts){
  const THREE=window.THREE,M=opts.model,host=opts.host,d=M.dem;
  const dpr=Math.min(window.devicePixelRatio||1,2);
  const renderer=new THREE.WebGLRenderer({antialias:true,alpha:true,preserveDrawingBuffer:!!opts.preserve});
  renderer.setPixelRatio(dpr);host.appendChild(renderer.domElement);
  const cvs=renderer.domElement;cvs.style.cssText='display:block;width:100%;height:100%;touch-action:none;cursor:grab';
  cvs.setAttribute('aria-hidden','true');
  const scene=new THREE.Scene();
  const camera=new THREE.PerspectiveCamera(40,1,20,90000);
  const fog=new THREE.Fog(0xdde7f0,14000,52000);scene.fog=fog;

  /* terrain mesh with a base, like a relief model on a table */
  const nx=d.nx,ny=d.ny,base=Math.min(...(()=>{let m=1e9;for(let i=0;i<M.H.length;i+=7)m=Math.min(m,M.H[i]);return [m];})())-180;
  const pos=new Float32Array(nx*ny*3),uv=new Float32Array(nx*ny*2);
  for(let r=0;r<ny;r++)for(let c=0;c<nx;c++){const i=r*nx+c;pos[i*3]=d.x0+c*M.sx;pos[i*3+1]=M.H[i];pos[i*3+2]=d.y0+r*M.sy;uv[i*2]=c/(nx-1);uv[i*2+1]=1-r/(ny-1);}
  const idx=new Uint32Array((nx-1)*(ny-1)*6);let k=0;
  for(let r=0;r<ny-1;r++)for(let c=0;c<nx-1;c++){const a=r*nx+c,b=a+1,e=a+nx,f=e+1;idx[k++]=a;idx[k++]=e;idx[k++]=b;idx[k++]=b;idx[k++]=e;idx[k++]=f;}
  const geo=new THREE.BufferGeometry();geo.setAttribute('position',new THREE.BufferAttribute(pos,3));geo.setAttribute('uv',new THREE.BufferAttribute(uv,2));geo.setIndex(new THREE.BufferAttribute(idx,1));geo.computeVertexNormals();

  // texture: snow + rock by slope, contours, village, water, roads
  const TW=Math.min(renderer.capabilities.maxTextureSize,opts.texSize||2048),TH=Math.round(TW*M.Hm/M.W);
  const tex=document.createElement('canvas');tex.width=TW;tex.height=TH;const tc=tex.getContext('2d');
  (function paint(){
    const small=document.createElement('canvas');small.width=nx;small.height=ny;const sc=small.getContext('2d'),im=sc.createImageData(nx,ny);
    for(let r=0;r<ny;r++)for(let c=0;c<nx;c++){const i=r*nx+c;
      const gx=(M.H[r*nx+Math.min(c+1,nx-1)]-M.H[r*nx+Math.max(c-1,0)])/(2*M.sx),gy=(M.H[Math.min(r+1,ny-1)*nx+c]-M.H[Math.max(r-1,0)*nx+c])/(2*M.sy);
      const slope=Math.atan(Math.hypot(gx,gy))*180/Math.PI,h=M.H[i];
      let rock=Math.min(1,Math.max(0,(slope-36)/18))*0.75;             // steep faces shed snow
      let low=Math.min(1,Math.max(0,(1750-h)/350))*Math.min(1,Math.max(0,(slope-18)/14)); // steep lower valley walls: scrub
      let R0=246,G0=249,B0=252; // snow
      R0=R0*(1-rock)+150*rock;G0=G0*(1-rock)+153*rock;B0=B0*(1-rock)+160*rock;
      R0=R0*(1-low)+150*low;G0=G0*(1-low)+160*low;B0=B0*(1-low)+158*low;
      const cool=Math.min(1,Math.max(0,(3000-h)/1600))*6;       // a hint of cool tone lower down
      im.data[i*4]=R0-cool;im.data[i*4+1]=G0-cool*0.5;im.data[i*4+2]=B0;im.data[i*4+3]=255;}
    sc.putImageData(im,0,0);tc.imageSmoothingEnabled=true;tc.imageSmoothingQuality='high';tc.drawImage(small,0,0,TW,TH);
    const s=TW/M.W,X=x=>(x-d.x0)*s,Y=y=>(y-d.y0)*s;
    const path=(pts,close)=>{tc.beginPath();pts.forEach((q,i)=>i?tc.lineTo(X(q[0]),Y(q[1])):tc.moveTo(X(q[0]),Y(q[1])));if(close)tc.closePath();};
    tc.lineJoin='round';tc.lineCap='round';
    if(opts.plainTex)return;
    M.contours.forEach(c=>{if(c.lev%100)return;const idxl=c.lev%500===0;tc.strokeStyle=idxl?'rgba(70,90,115,.30)':'rgba(70,90,115,.14)';tc.lineWidth=idxl?1.3*s*4:0.8*s*4;path(c.pts);tc.stroke();});
    tc.fillStyle='rgba(214,203,190,.75)';M.env.village.forEach(p=>{path(p,true);tc.fill();});
    tc.strokeStyle='rgba(120,160,200,.7)';tc.lineWidth=Math.max(1.2,6*s);M.env.rivers.forEach(p=>{path(p);tc.stroke();});
    tc.fillStyle='#8db6d8';tc.strokeStyle='#6f9cc4';tc.lineWidth=1;M.env.water.forEach(p=>{path(p,true);tc.fill();tc.stroke();});
    const rw=[14,10,7,6,4];
    [...M.env.roads].sort((a,b)=>b.k-a.k).forEach(r=>{tc.strokeStyle=r.k===0?'#8a7f76':'rgba(140,128,118,.85)';tc.lineWidth=Math.max(1,rw[r.k]*s);path(r.pts);tc.stroke();});
  })();
  const texture=new THREE.CanvasTexture(tex);texture.anisotropy=renderer.capabilities.getMaxAnisotropy();
  if('encoding' in texture)texture.encoding=THREE.sRGBEncoding;
  const terrainMat=new THREE.MeshLambertMaterial({map:texture,vertexColors:true});
  const shade=new Float32Array(nx*ny*3).fill(1);geo.setAttribute('color',new THREE.BufferAttribute(shade,3));
  const terrain=new THREE.Mesh(geo,terrainMat);scene.add(terrain);
  // skirt
  (function(){const sp=[],si=[];const ring=[];
    for(let c=0;c<nx;c++)ring.push(c);for(let r=1;r<ny;r++)ring.push(r*nx+nx-1);for(let c=nx-2;c>=0;c--)ring.push((ny-1)*nx+c);for(let r=ny-2;r>0;r--)ring.push(r*nx);ring.push(0);
    ring.forEach((i,j)=>{sp.push(pos[i*3],pos[i*3+1],pos[i*3+2],pos[i*3],base,pos[i*3+2]);if(j){const a=(j-1)*2;si.push(a,a+1,a+2,a+2,a+1,a+3);}});
    const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(sp,3));g.setIndex(si);g.computeVertexNormals();
    scene.add(new THREE.Mesh(g,new THREE.MeshLambertMaterial({color:0xb9c5d2,side:THREE.DoubleSide})));
    const bg=new THREE.PlaneGeometry(M.W,M.Hm);bg.rotateX(Math.PI/2);const b=new THREE.Mesh(bg,new THREE.MeshBasicMaterial({color:0x9aa7b5,side:THREE.DoubleSide}));b.position.set(d.x0+M.W/2,base,d.y0+M.Hm/2);scene.add(b);
  })();
  const hemi=new THREE.HemisphereLight(0xe8f0f8,0x8a95a3,0.62);scene.add(hemi);
  const sun=new THREE.DirectionalLight(0xfff6ea,0.62);sun.position.set(-0.35,0.62,1).normalize();scene.add(sun); // low winter sun from the south-southwest

  /* screen-space lines */
  const uRes={value:new THREE.Vector2(1,1)};
  const vsh=`attribute float dist;varying float vDist;uniform vec2 res;uniform float width;attribute vec3 prev;attribute vec3 next;attribute float side;
  #include <fog_pars_vertex>
  void main(){mat4 m=projectionMatrix*modelViewMatrix;vec4 c=m*vec4(position,1.);vec4 p=m*vec4(prev,1.);vec4 n=m*vec4(next,1.);
  vec2 a=c.xy/c.w*res,b=p.xy/p.w*res,e=n.xy/n.w*res;vec2 d1=a-b,d2=e-a;
  if(length(d1)<1e-4)d1=d2;if(length(d2)<1e-4)d2=d1;d1=normalize(d1);d2=normalize(d2);vec2 t=normalize(d1+d2);if(length(d1+d2)<1e-3)t=d1;
  vec2 nn=vec2(-t.y,t.x);float ml=1./max(dot(nn,vec2(-d1.y,d1.x)),.6);
  c.xy+=nn*side*width*0.5*ml/res*c.w;gl_Position=c;vDist=dist;
  vec4 mvPosition=modelViewMatrix*vec4(position,1.);
  #include <fog_vertex>
  }`;
  // dash: a closed lift or run is dashed (in metres along the line, 0 = solid), as on the map from above
  const fsh=`uniform vec3 color;uniform float opacity;uniform float dash;varying float vDist;
  #include <fog_pars_fragment>
  void main(){if(dash>0.&&mod(vDist,dash)>dash*.55)discard;gl_FragColor=vec4(color,opacity);
  #include <fog_fragment>
  }`;
  function lineMat(color,width,off){
    return new THREE.ShaderMaterial({uniforms:THREE.UniformsUtils.merge([THREE.UniformsLib.fog,{color:{value:new THREE.Color(color)},opacity:{value:1},dash:{value:0},width:{value:width},res:{value:new THREE.Vector2(1,1)}}]),
      vertexShader:vsh.replace('uniform vec2 res;','uniform vec2 res;'),fragmentShader:fsh,transparent:true,fog:true,side:THREE.DoubleSide,depthWrite:false,polygonOffset:true,polygonOffsetFactor:off,polygonOffsetUnits:off});
  }
  function lineGeo(lines){
    const P=[],Pr=[],Nx=[],Sd=[],Ds=[],I=[];let base=0;
    lines.forEach(L=>{const n=L.length;if(n<2)return;let dd=0;
      for(let i=0;i<n;i++){const q=L[i],pr=L[Math.max(0,i-1)],nx_=L[Math.min(n-1,i+1)];if(i)dd+=Math.hypot(q[0]-pr[0],q[1]-pr[1],q[2]-pr[2]);
        for(const s of [-1,1]){P.push(q[0],q[1],q[2]);Pr.push(pr[0],pr[1],pr[2]);Nx.push(nx_[0],nx_[1],nx_[2]);Sd.push(s);Ds.push(dd);}
        if(i<n-1){const a=base+i*2;I.push(a,a+1,a+2,a+2,a+1,a+3);}}
      base+=n*2;});
    const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(P,3));g.setAttribute('prev',new THREE.Float32BufferAttribute(Pr,3));
    g.setAttribute('next',new THREE.Float32BufferAttribute(Nx,3));g.setAttribute('side',new THREE.Float32BufferAttribute(Sd,1));g.setAttribute('dist',new THREE.Float32BufferAttribute(Ds,1));g.setIndex(I);return g;
  }
  // painted run: slope colour per vertex, revealed from the top down (reveal 0..1 against each vertex's share of the way)
  const vshP=vsh.replace('attribute float side;','attribute float side;attribute vec3 acol;attribute float prog;varying vec3 vCol;varying float vProg;').replace('gl_Position=c;','gl_Position=c;vCol=acol;vProg=prog;');
  const fshP=`uniform float opacity;uniform float reveal;varying vec3 vCol;varying float vProg;
  #include <fog_pars_fragment>
  void main(){if(vProg>reveal)discard;gl_FragColor=vec4(vCol,opacity);
  #include <fog_fragment>
  }`;
  function paintGeo(lines,cols,progs){
    const g=lineGeo(lines),C=[],Pg=[];
    lines.forEach((L,j)=>{if(L.length<2)return;for(let i=0;i<L.length;i++){const c=cols[j][i];for(let s=0;s<2;s++){C.push(c.r,c.g,c.b);Pg.push(progs[j][i]);}}});
    g.setAttribute('acol',new THREE.Float32BufferAttribute(C,3));g.setAttribute('prog',new THREE.Float32BufferAttribute(Pg,1));return g;
  }
  function drape(pts,lift){ // pts [[x,y]] → [[x,h,z]] densified
    const o=[];
    for(let i=0;i<pts.length;i++){
      const a=pts[i];if(i){const b=pts[i-1],L=Math.hypot(a[0]-b[0],a[1]-b[1]),n=Math.ceil(L/18);for(let k=1;k<n;k++){const t=k/n,x=b[0]+(a[0]-b[0])*t,y=b[1]+(a[1]-b[1])*t;o.push([x,M.elev(x,y)+lift,y]);}}
      o.push([a[0],M.elev(a[0],a[1])+lift,a[1]]);}
    return o;
  }
  function cable(pts){ // lift line: straight chord between stations, kept above the snow
    const e0=M.elev(pts[0][0],pts[0][1])+10,e1=M.elev(pts[pts.length-1][0],pts[pts.length-1][1])+10;
    let tot=0;const cum=[0];for(let i=1;i<pts.length;i++){tot+=Math.hypot(pts[i][0]-pts[i-1][0],pts[i][1]-pts[i-1][1]);cum.push(tot);}
    const o=[];const dens=drape(pts,0);let acc=0;
    dens.forEach((q,i)=>{if(i)acc+=Math.hypot(q[0]-dens[i-1][0],q[2]-dens[i-1][2]);const t=tot?Math.min(1,acc/tot):0;o.push([q[0],Math.max(e0+(e1-e0)*t,q[1]+9),q[2]]);});
    return o;
  }
  const css=getComputedStyle(document.documentElement);
  const col=n=>(css.getPropertyValue(n)||'').trim()||'#888';
  const COL=Object.assign({green:'#1d9a52',blue:'#1f66d1',red:'#d4312a',black:'#11151c'},opts.colors||{});
  const pisteObjs={},liftObjs=[],pickables=[];
  (opts.noLines?[]:opts.pistes).forEach(p=>{
    const lines=p.segs.filter(s=>!s.area).map(s=>drape(s.g.map(opts.P),4));
    if(!lines.length)return;
    const w=p.named?(p.kind==='ski-way'?2.8:4):2.6;
    const cas=new THREE.Mesh(lineGeo(lines),lineMat('#ffffff',w+3.2,-2)),core=new THREE.Mesh(lineGeo(lines),lineMat(COL[p.color],w,-4));
    cas.renderOrder=2;core.renderOrder=3;scene.add(cas);scene.add(core);
    pisteObjs[p.key]={cas,core,lines,p,w};pickables.push({kind:'piste',key:p.key,lines});
  });
  const liftCas=lineMat('#ffffff',4.4,-3);
  const stations=[];
  (opts.noLines?[]:opts.lifts).forEach(l=>{const L=cable(l.g.map(opts.P));const g=lineGeo([L]);
    const c1=new THREE.Mesh(g,liftCas),c2=new THREE.Mesh(g,lineMat(opts.liftColor||'#2a2f38',2,-5));c1.renderOrder=4;c2.renderOrder=5;scene.add(c1);scene.add(c2);
    liftObjs.push({l,L,c1,c2});pickables.push({kind:'lift',id:l.id,lines:[L]});stations.push(L[0],L[L.length-1]);});
  // stations as screen-size dots
  (function(){const c=document.createElement('canvas');c.width=c.height=32;const x=c.getContext('2d');x.fillStyle='#2a2f38';x.strokeStyle='#fff';x.lineWidth=5;x.beginPath();x.arc(16,16,11,0,7);x.fill();x.stroke();
    if(!stations.length)return;const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(stations.flat(),3));
    const m=new THREE.PointsMaterial({size:9,sizeAttenuation:false,map:new THREE.CanvasTexture(c),transparent:true,depthWrite:false,alphaTest:.2});
    const pts=new THREE.Points(g,m);pts.renderOrder=6;scene.add(pts);})();

  /* labels (HTML overlay) */
  const lay=document.createElement('div');lay.className='r3-labels';host.appendChild(lay);
  const labels=[];
  function addLabel(html,cls,xyz,pri,data){const e=document.createElement('div');e.className='r3-lbl '+cls;e.innerHTML=html;if(data)Object.assign(e.dataset,data);lay.appendChild(e);const o={e,v:new THREE.Vector3(...xyz),pri,w:0,h:0};labels.push(o);return o;}
  const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  M.peaks.forEach(p=>addLabel(`<span class="pk-ico${p.pass?' pass':''}"></span><b>${esc(p.n)}</b> <span class="num">${p.ele}</span>`,'peak',[p.x,M.elev(p.x,p.y)+6,p.y],100));
  M.env.places.filter(p=>['Gudauri','Kobi'].includes(p.n)).forEach(p=>addLabel(p.n==='Gudauri'?T('map.place_gudauri'):'Kobi','place',[p.x,M.elev(p.x,p.y)+10,p.y],60));
  Object.values(pisteObjs).forEach(o=>{if(!o.p.named)return;const L=o.lines.slice().sort((a,b)=>b.length-a.length)[0];const q=L[Math.floor(L.length*0.45)];
    o.label=addLabel(esc(opts.dispName(o.p)),'piste c-'+o.p.color,[q[0],q[1]+8,q[2]],40,{key:o.p.key});});

  liftObjs.forEach(o=>{if(!o.l.name)return;const L=o.L,q=L[Math.floor(L.length/2)];
    addLabel(`<span class="lift-ico">⇡</span> ${esc(o.l.name)}`,'lift',[q[0],q[1]+12,q[2]],45,{lift:o.l.id});});

  /* camera rig */
  const cx0=opts.center[0],cz0=opts.center[1];
  const home={tx:cx0,tz:cz0,dist:opts.homeDist||11500,az:opts.homeAz||0,pol:opts.homePol||0.52};
  const st={...home};
  function ty(){return M.elev(st.tx,st.tz);}
  function place(){
    st.pol=Math.max(0.12,Math.min(1.45,st.pol));st.dist=Math.max(350,Math.min(32000,st.dist));
    st.tx=Math.max(d.x0,Math.min(d.x1,st.tx));st.tz=Math.max(d.y0,Math.min(d.y1,st.tz));
    const y0=ty();
    let cp=Math.cos(st.pol);let x=st.tx+st.dist*Math.sin(st.az)*cp,y=y0+st.dist*Math.sin(st.pol),z=st.tz+st.dist*Math.cos(st.az)*cp;
    const g=M.elev(x,z)+60;if(y<g)y=g;
    camera.position.set(x,y,z);camera.lookAt(st.tx,y0,st.tz);
    camera.near=Math.max(10,st.dist/200);camera.far=st.dist*6+30000;camera.updateProjectionMatrix();
    fog.near=st.dist*1.1+2000;fog.far=st.dist*3.6+18000;
    if(opts.onHeading)opts.onHeading(st.az);
  }
  let W=1,Hh=1;
  function resize(){const r=host.getBoundingClientRect();W=Math.max(1,r.width);Hh=Math.max(1,r.height);renderer.setSize(W,Hh,false);camera.aspect=W/Hh;
    uRes.value.set(W/2,Hh/2);// half-res: shader uses ndc*res → px
    scene.traverse(o=>{if(o.material&&o.material.uniforms&&o.material.uniforms.res)o.material.uniforms.res.value.set(W/2,Hh/2);});
    request();}
  new ResizeObserver(resize).observe(host);

  /* occlusion via heightfield ray march */
  function visible(v){const c=camera.position;const dx=v.x-c.x,dy=v.y-c.y,dz=v.z-c.z;const n=40;
    for(let i=4;i<n;i++){const t=i/n;const x=c.x+dx*t,y=c.y+dy*t,z=c.z+dz*t;if(M.elev(x,z)>y+8)return false;}return true;}
  function groundAt(px,py){ // screen → terrain point by marching
    const v=new THREE.Vector3((px/W)*2-1,-(py/Hh)*2+1,0.5).unproject(camera).sub(camera.position).normalize();
    const c=camera.position;let prev=0,step=Math.max(20,st.dist/150);
    for(let t=step;t<st.dist*8;t+=step){const x=c.x+v.x*t,y=c.y+v.y*t,z=c.z+v.z*t;
      if(x<d.x0||x>d.x1||z<d.y0||z>d.y1){if(t>st.dist*3)break;prev=t;continue;}
      if(M.elev(x,z)>=y){let a=prev,b=t;for(let k=0;k<12;k++){const m=(a+b)/2;const X=c.x+v.x*m,Y=c.y+v.y*m,Z=c.z+v.z*m;if(M.elev(X,Z)>=Y)b=m;else a=m;}
        return new THREE.Vector3(c.x+v.x*b,c.y+v.y*b,c.z+v.z*b);}prev=t;}
    return null;}

  /* lift status (6.9, S1 and S2 in 3D): closed lifts grey and dashed, chairs moving on the open ones (three each, a lap
     in len/60 seconds as on the map from above), closed runs dashed, and "only what's open for me" fades them */
  const SHUT='#9aa5b3';let chairs=null,chairT=0;
  function liftCum(L){const c=[0];for(let i=1;i<L.length;i++)c.push(c[i-1]+Math.hypot(L[i][0]-L[i-1][0],L[i][1]-L[i-1][1],L[i][2]-L[i-1][2]));return c;}
  function at(o,f){const c=o.cum||(o.cum=liftCum(o.L)),d=f*c[c.length-1];let i=1;while(i<c.length-1&&c[i]<d)i++;
    const a=o.L[i-1],b=o.L[i],t=c[i]>c[i-1]?(d-c[i-1])/(c[i]-c[i-1]):0;return [a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t+3,a[2]+(b[2]-a[2])*t];}
  function setChairs(list){if(chairs){scene.remove(chairs.pts);chairs.pts.geometry.dispose();chairs=null;}
    if(!list.length||matchMedia('(prefers-reduced-motion: reduce)').matches)return;
    const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(new Float32Array(list.length*9),3));
    const pts=new THREE.Points(g,new THREE.PointsMaterial({color:0xF4B942,size:6*Math.min(2,devicePixelRatio||1),sizeAttenuation:false}));pts.renderOrder=6;pts.frustumCulled=false;
    scene.add(pts);chairs={pts,list};}
  function moveChairs(){if(!chairs)return;const a=chairs.pts.geometry.attributes.position,now=performance.now()/1000;
    chairs.list.forEach((o,j)=>{const dur=Math.max(8,(o.l.len||1000)/60);for(let k=0;k<3;k++){const q=at(o,((now/dur)+k/3)%1);a.setXYZ(j*3+k,q[0],q[1],q[2]);}});
    a.needsUpdate=true;}
  const pst={m:null,forMe:false};
  function runLook(o){const sel=R3.sel,on=!sel||o.p.key===sel,shut=pst.m&&pst.m[o.p.key]===false,dim=pst.forMe&&shut?.15:1;
    o.core.material.uniforms.opacity.value=(on?1:.28)*dim;o.cas.material.uniforms.opacity.value=(on?1:.2)*dim;
    o.core.material.uniforms.color.value.set(shut?SHUT:COL[o.p.color]);o.core.material.uniforms.dash.value=shut?50:0;}

  /* rendering */
  let queued=false;const tmp=new THREE.Vector3();
  function request(){if(!queued){queued=true;requestAnimationFrame(frame);}}
  function frame(){queued=false;place();moveChairs();renderer.render(scene,camera);layoutLabels();if(opts.onRender)opts.onRender();
    if(chairs&&!document.hidden&&cvs.isConnected&&cvs.offsetParent!==null){clearTimeout(chairT);chairT=setTimeout(request,100);}} // the chairs at about 10 frames a second, only while seen
  function layoutLabels(){
    const kept=[];const sel=R3.sel;
    labels.slice().sort((a,b)=>(b===selLabel?1e3:b.pri)-(a===selLabel?1e3:a.pri)).forEach(o=>{
      tmp.copy(o.v).project(camera);let show=tmp.z<1&&tmp.x>-1.05&&tmp.x<1.05&&tmp.y>-1.05&&tmp.y<1.05;
      const isP=o.e.classList.contains('piste');
      if(show&&isP&&sel&&o!==selLabel)show=false;
      if(show&&isP&&!sel&&st.dist>9000)show=false;
      if(show&&o.hiddenByFilter)show=false;
      if(show&&!visible(o.v))show=false;
      if(show){if(!o.w){o.e.style.display='block';o.w=o.e.offsetWidth;o.h=o.e.offsetHeight;}
        const x=(tmp.x+1)/2*W,y=(1-tmp.y)/2*Hh;const r={a:x-o.w/2-3,b:y-o.h-10,c:x+o.w/2+3,d:y-4};
        if(kept.some(k=>r.a<k.c&&r.c>k.a&&r.b<k.d&&r.d>k.b))show=false;else{kept.push(r);o.e.style.transform=`translate(${(x-o.w/2).toFixed(1)}px,${(y-o.h-6).toFixed(1)}px)`;}}
      o.e.style.display=show?'block':'none';
    });
  }

  /* picking in screen space */
  function pick(px,py){
    const g=groundAt(px,py);const gd=g?g.distanceTo(camera.position):Infinity;
    let best=null,bd=14;
    pickables.forEach(k=>{if(k.hidden)return;k.lines.forEach(L=>{let prev=null;
      for(let i=0;i<L.length;i+=2){tmp.set(L[i][0],L[i][1],L[i][2]);const dist=tmp.distanceTo(camera.position);tmp.project(camera);
        const cur=tmp.z<1?[(tmp.x+1)/2*W,(1-tmp.y)/2*Hh,dist]:null;
        if(cur&&prev){const dd=segD(px,py,prev,cur);const occl=Math.min(prev[2],cur[2])>gd+Math.max(60,gd*0.03);if(!occl&&dd<bd){bd=dd;best=k;}}
        prev=cur;}});});
    return best;
  }
  function segD(x,y,a,b){const vx=b[0]-a[0],vy=b[1]-a[1],l=vx*vx+vy*vy;let t=l?((x-a[0])*vx+(y-a[1])*vy)/l:0;t=Math.max(0,Math.min(1,t));return Math.hypot(x-a[0]-vx*t,y-a[1]-vy*t);}

  /* input */
  const ptrs=new Map();let moved=0,mode=null,last=null,pinch=null,downT=0;
  cvs.addEventListener('contextmenu',e=>e.preventDefault());
  cvs.addEventListener('pointerdown',e=>{cvs.setPointerCapture(e.pointerId);ptrs.set(e.pointerId,{x:e.clientX,y:e.clientY});moved=0;downT=performance.now();stopAnim();
    mode=(e.button===2||e.shiftKey||e.ctrlKey)?'pan':'rot';cvs.style.cursor='grabbing';
    if(ptrs.size===2){const[a,b]=[...ptrs.values()];pinch={d:Math.hypot(a.x-b.x,a.y-b.y),mx:(a.x+b.x)/2,my:(a.y+b.y)/2,ang:Math.atan2(b.y-a.y,b.x-a.x)};}});
  cvs.addEventListener('pointermove',e=>{
    const p=ptrs.get(e.pointerId);
    if(!p){if(e.pointerType==='mouse'){const r=cvs.getBoundingClientRect();hover(e.clientX-r.left,e.clientY-r.top);}return;}
    const dx=e.clientX-p.x,dy=e.clientY-p.y;p.x=e.clientX;p.y=e.clientY;moved+=Math.abs(dx)+Math.abs(dy);
    if(ptrs.size===1){
      if(mode==='rot'){st.az-=dx*0.006;st.pol+=dy*0.004;}
      else pan(dx,dy);
      request();
    }else if(ptrs.size===2&&pinch){const[a,b]=[...ptrs.values()];const dd=Math.hypot(a.x-b.x,a.y-b.y),mx=(a.x+b.x)/2,my=(a.y+b.y)/2,ang=Math.atan2(b.y-a.y,b.x-a.x);
      st.dist*=pinch.d/dd;pan(mx-pinch.mx,my-pinch.my);st.az+=ang-pinch.ang;pinch={d:dd,mx,my,ang};moved+=10;request();}
  });
  function pan(dx,dy){const s=st.dist*0.0012*(700/Math.max(400,Hh));const ca=Math.cos(st.az),sa=Math.sin(st.az);
    st.tx+=-dx*s*ca-dy*s*sa/Math.max(.35,Math.sin(st.pol+.3));st.tz+=dx*s*sa-dy*s*ca/Math.max(.35,Math.sin(st.pol+.3));}
  function up(e){const was=ptrs.size;ptrs.delete(e.pointerId);if(ptrs.size<2)pinch=null;if(!ptrs.size)cvs.style.cursor='grab';
    if(was===1&&moved<7&&e.type==='pointerup'){const r=cvs.getBoundingClientRect();const k=pick(e.clientX-r.left,e.clientY-r.top);
      if(k){if(k.kind==='piste')opts.onPick&&opts.onPick(k.key);else opts.onLift&&opts.onLift(k.id);}else opts.onEmpty&&opts.onEmpty();}}
  cvs.addEventListener('pointerup',up);cvs.addEventListener('pointercancel',up);
  cvs.addEventListener('wheel',e=>{e.preventDefault();stopAnim();const r=cvs.getBoundingClientRect();zoomAt(Math.exp(e.deltaY*0.0012),e.clientX-r.left,e.clientY-r.top);},{passive:false});
  function zoomAt(f,px,py){const g=px==null?null:groundAt(px,py);
    if(g&&f<1){st.tx+=(g.x-st.tx)*(1-f);st.tz+=(g.z-st.tz)*(1-f);}
    st.dist*=f;request();}
  let hoverT=0;function hover(x,y){const now=performance.now();if(now-hoverT<60)return;hoverT=now;cvs.style.cursor=pick(x,y)?'pointer':'grab';}

  /* animation */
  let anim=null;
  function stopAnim(){clearTimeout(flyWait);flyWait=null;const pend=flyEnd;flyEnd=null;if(anim){cancelAnimationFrame(anim.raf);const cb=anim.onStop;anim=null;if(cb)cb();if(cb===pend)return;}if(pend)pend();}
  function flyTo(to,ms){stopAnim();const from={...st};
    let daz=((to.az??st.az)-from.az)%(2*Math.PI);if(daz>Math.PI)daz-=2*Math.PI;if(daz<-Math.PI)daz+=2*Math.PI;
    const tgt={tx:to.tx??st.tx,tz:to.tz??st.tz,dist:to.dist??st.dist,pol:to.pol??st.pol,az:from.az+daz};
    if(matchMedia('(prefers-reduced-motion: reduce)').matches||!ms){Object.assign(st,tgt);request();return;}
    const t0=performance.now(),me=anim={};
    const step=()=>{if(anim!==me)return;let t=Math.min(1,(performance.now()-t0)/ms);const e=t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;
      for(const k in tgt)st[k]=from[k]+(tgt[k]-from[k])*e;place();renderer.render(scene,camera);layoutLabels();
      if(t<1)me.raf=requestAnimationFrame(step);else anim=null;};
    anim.raf=requestAnimationFrame(step);}
  function focusLines(lines,extra,k){let a=1e9,b=1e9,c=-1e9,e=-1e9;lines.forEach(L=>L.forEach(q=>{a=Math.min(a,q[0]);c=Math.max(c,q[0]);b=Math.min(b,q[2]);e=Math.max(e,q[2]);}));
    const ext=Math.max(c-a,e-b,500);flyTo(Object.assign({tx:(a+c)/2,tz:(b+e)/2,dist:Math.max(k?1300:1600,ext*(k||2.3)),pol:0.62},extra||{}),k?1100:800);}

  /* selection/filter API */
  let selLabel=null,paintObj=null,markObj=null,groundObj=null,flyWait=0,flyEnd=null,lightKey='';
  const R3={sel:null};
  const api={
    select(key){R3.sel=key||null;selLabel=key&&pisteObjs[key]?pisteObjs[key].label:null;
      Object.values(pisteObjs).forEach(o=>{runLook(o);
        o.core.material.uniforms.width.value=o.p.key===key?o.w+2.4:o.w;o.cas.material.uniforms.width.value=o.p.key===key?o.w+7:o.w+3.2;
        o.cas.material.uniforms.color.value.set(o.p.key===key?'#ffe38a':'#ffffff');});
      liftObjs.forEach(o=>{o.c2.material.uniforms.opacity.value=key?.45:1;});
      request();},
    focus(key){const o=pisteObjs[key];if(o)focusLines(o.lines,{pol:0.5},1.75);}, // the camera settles low over the run
    focusLift(id){const o=liftObjs.find(x=>x.l.id===id);if(o)focusLines([o.L]);},
    filter(hidden){Object.values(pisteObjs).forEach(o=>{const h=hidden.has(o.p.color)||(!o.p.named&&hidden.has('unnamed'));o.core.visible=o.cas.visible=!h;if(o.label)o.label.hiddenByFilter=h;const pk=pickables.find(k=>k.key===o.p.key);if(pk)pk.hidden=h;});
      liftObjs.forEach(o=>{o.c1.visible=o.c2.visible=!hidden.has('lifts');const pk=pickables.find(k=>k.id===o.l.id);if(pk)pk.hidden=hidden.has('lifts');});request();},
    // light by the time of day: the sun where it really is in Gudauri, shadows cast by other mountains
    // (marched over the elevation grid), warm light low in the sky, moonlight at night.
    // l: {alt, az, dark, sky:[top,bottom]}. null goes back to the neutral light.
    setLight(l){
      const key=l?[Math.round(l.alt*2),Math.round(l.az*2),l.dark?1:0].join():'';if(key===lightKey)return;lightKey=key;
      if(!l){sun.color.setHex(0xfff6ea);sun.intensity=0.62;sun.position.set(-0.35,0.62,1).normalize();hemi.color.setHex(0xe8f0f8);hemi.groundColor.setHex(0x8a95a3);hemi.intensity=.62;shade.fill(1);geo.attributes.color.needsUpdate=true;host.style.background='';request();return;}
      const up=l.alt>0,src=up?l:{alt:38,az:150}; // moonlight from the south-east at night
      const a=src.alt*Math.PI/180,z=src.az*Math.PI/180;
      sun.position.set(Math.sin(z)*Math.cos(a),Math.sin(a),-Math.cos(z)*Math.cos(a));
      const lowK=up?Math.max(0,1-l.alt/12):0; // 1 at the horizon, 0 above 12°
      const warm=new THREE.Color(0xfff6ea).lerp(new THREE.Color(0xff9f7a),lowK);
      if(up){sun.color.copy(warm);sun.intensity=0.5+0.3*Math.min(1,l.alt/20);hemi.color.setHex(lowK>.5?0xb9b0d0:0xdde8f5);hemi.groundColor.setHex(0x7d879a);hemi.intensity=0.55-0.15*lowK;}
      else{sun.color.setHex(0x9fb4e0);sun.intensity=l.dark?0.22:0.3;hemi.color.setHex(0x4a5a86);hemi.groundColor.setHex(0x1c2438);hemi.intensity=l.dark?0.42:0.5;}
      // cast shadows on the grid: a cell is lit if nothing along the way to the light rises above its line
      const tA=Math.tan(a),sx=Math.sin(z),sy=-Math.cos(z),H=M.H,step=M.sx;let Hmax=-1e9;for(let i=0;i<H.length;i++)if(H[i]>Hmax)Hmax=H[i];
      const shadow=up||l.dark?(up?[0.62,0.68,0.84]:[0.7,0.74,0.86]):null;
      if(!shadow)shade.fill(1);else for(let r=0;r<ny;r++)for(let c=0;c<nx;c++){const i=r*nx+c,h0=H[i];let lit=true;
        for(let dd=1,k=1;k<110;k++,dd+=k<20?1:k<60?2:4){const top=h0+tA*dd*step+2;if(top>Hmax)break; // above every summit: nothing can block it
          const cc=Math.round(c+sx*dd),rr=Math.round(r+sy*dd*step/M.sy);if(cc<0||rr<0||cc>=nx||rr>=ny)break;if(H[rr*nx+cc]>top){lit=false;break;}}
        const f=lit?[1,1,1]:shadow;shade[i*3]=f[0];shade[i*3+1]=f[1];shade[i*3+2]=f[2];}
      geo.attributes.color.needsUpdate=true;
      if(l.sky){host.style.background=`linear-gradient(180deg,${l.sky[0]},${l.sky[1]} 70%)`;fog.color.set(l.sky[1]);}
      request();},
    // paint the selected run in slope colours, from its top to its bottom, over the ground coloured by slope
    paint(key,ms){
      if(groundObj){scene.remove(groundObj);groundObj.geometry.dispose();groundObj.material.map.dispose();groundObj.material.dispose();groundObj=null;}
      if(paintObj){scene.remove(paintObj.mesh);paintObj.mesh.geometry.dispose();paintObj.mesh.material.dispose();cancelAnimationFrame(paintObj.raf);paintObj=null;}
      const o=key&&pisteObjs[key];if(!o){request();return;}
      const lines=o.lines.map(L=>L[0][1]<L[L.length-1][1]?L.slice().reverse():L);
      const cols=[],progs=[];
      lines.forEach(L=>{let tot=0;const cum=[0];for(let i=1;i<L.length;i++){tot+=Math.hypot(L[i][0]-L[i-1][0],L[i][2]-L[i-1][2]);cum.push(tot);}
        progs.push(cum.map(c=>tot?c/tot:0));
        cols.push(L.map((q,i)=>{let a=i,b=i;while(a>0&&cum[i]-cum[a]<25)a--;while(b<L.length-1&&cum[b]-cum[i]<25)b++;
          const dd=cum[b]-cum[a],dh=Math.abs(L[b][1]-L[a][1]);return new THREE.Color(R.slopeColor(dd?Math.atan(dh/dd)*180/Math.PI:0));}));});
      const mat=new THREE.ShaderMaterial({uniforms:THREE.UniformsUtils.merge([THREE.UniformsLib.fog,{opacity:{value:1},reveal:{value:0},width:{value:o.w+2.4},res:{value:new THREE.Vector2(W/2,Hh/2)}}]),
        vertexShader:vshP,fragmentShader:fshP,transparent:true,fog:true,side:THREE.DoubleSide,depthWrite:false,polygonOffset:true,polygonOffsetFactor:-6,polygonOffsetUnits:-6});
      const mesh=new THREE.Mesh(paintGeo(lines,cols,progs),mat);mesh.renderOrder=7;scene.add(mesh);
      paintObj={mesh,raf:0};
      // the ground around the run, coloured by slope, draped on the terrain
      const sc=R.slopeCanvas(M,lines.map(L=>L.map(q=>[q[0],q[2]])));
      const gw=Math.max(2,Math.round((sc.x1-sc.x0)/25)),gh=Math.max(2,Math.round((sc.y1-sc.y0)/25));
      const pg=new THREE.PlaneGeometry(sc.x1-sc.x0,sc.y1-sc.y0,gw,gh);pg.rotateX(-Math.PI/2);
      const pp=pg.attributes.position;for(let i=0;i<pp.count;i++){const x=pp.getX(i)+(sc.x0+sc.x1)/2,zz=pp.getZ(i)+(sc.y0+sc.y1)/2;pp.setXYZ(i,x,M.elev(x,zz)+2.5,zz);}
      const gt=new THREE.CanvasTexture(sc.canvas);if('encoding' in gt)gt.encoding=THREE.sRGBEncoding;
      groundObj=new THREE.Mesh(pg,new THREE.MeshBasicMaterial({map:gt,transparent:true,opacity:0,depthWrite:false,fog:true,polygonOffset:true,polygonOffsetFactor:-1,polygonOffsetUnits:-1}));
      groundObj.renderOrder=1;scene.add(groundObj);
      if(matchMedia('(prefers-reduced-motion: reduce)').matches||!ms){mat.uniforms.reveal.value=1.01;groundObj.material.opacity=1;request();return;}
      const t0=performance.now(),step=()=>{const t=Math.min(1,(performance.now()-t0)/ms);mat.uniforms.reveal.value=t<1?(t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2):1.01;if(groundObj)groundObj.material.opacity=Math.min(1,t*1.6);
        renderer.render(scene,camera);if(t<1&&paintObj)paintObj.raf=requestAnimationFrame(step);};
      paintObj.raf=requestAnimationFrame(step);
    },
    // a dot on the terrain, e.g. the point chosen on the elevation profile
    marker(x,y){if(!markObj){const c=document.createElement('canvas');c.width=c.height=64;const k=c.getContext('2d');k.fillStyle='rgba(255,255,255,.45)';k.beginPath();k.arc(32,32,31,0,7);k.fill();k.fillStyle='#13233A';k.strokeStyle='#fff';k.lineWidth=7;k.beginPath();k.arc(32,32,17,0,7);k.fill();k.stroke();
        const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute([0,0,0],3));
        markObj=new THREE.Points(g,new THREE.PointsMaterial({size:30,sizeAttenuation:false,map:new THREE.CanvasTexture(c),transparent:true,depthTest:false,depthWrite:false}));markObj.renderOrder=9;markObj.frustumCulled=false;scene.add(markObj);}
      if(x==null){markObj.visible=false;request();return;}
      markObj.visible=true;markObj.geometry.attributes.position.setXYZ(0,x,M.elev(x,y)+8,y);markObj.geometry.attributes.position.needsUpdate=true;request();},
    // camera flies down a line (projected metres, from the top), behind and above it. Any touch on the map stops it.
    flyAlong(pts,onEnd){stopAnim();
      let tot=0;const cum=[0];for(let i=1;i<pts.length;i++){tot+=Math.hypot(pts[i][0]-pts[i-1][0],pts[i][1]-pts[i-1][1]);cum.push(tot);}
      const at=d=>{d=Math.max(0,Math.min(tot,d));let i=1;while(i<pts.length-1&&cum[i]<d)i++;const f=(d-cum[i-1])/((cum[i]-cum[i-1])||1);return [pts[i-1][0]+(pts[i][0]-pts[i-1][0])*f,pts[i-1][1]+(pts[i][1]-pts[i-1][1])*f];};
      const azAt=d=>{const a=at(d-60),b=at(d+180);return Math.atan2(-(b[0]-a[0]),-(b[1]-a[1]));};
      const ms=Math.max(15000,Math.min(40000,tot*14)),s0=at(0);
      flyTo({tx:s0[0],tz:s0[1],dist:650,pol:.42,az:azAt(0)},1200);if(anim)anim.onStop=onEnd;
      // until the flight itself starts, a stop from anywhere (a touch, another camera move) still ends it
      const wait=setTimeout(()=>{flyEnd=null;
        // on a slow machine the move to the top can still be running: the flight takes over, and each animation
        // only goes on while it is still the current one (the move ending used to wipe the flight after one frame)
        if(anim)cancelAnimationFrame(anim.raf);const t0=performance.now(),me=anim={};let az=st.az;
        const step=()=>{if(anim!==me)return;const t=Math.min(1,(performance.now()-t0)/ms),e=t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2,d=e*tot,p=at(d);
          let da=azAt(d)-az;da=Math.atan2(Math.sin(da),Math.cos(da));az+=da*0.04;
          Object.assign(st,{tx:p[0],tz:p[1],dist:650,pol:.42,az});place();renderer.render(scene,camera);layoutLabels();
          if(opts.onFly)opts.onFly(d/tot);
          if(t<1)me.raf=requestAnimationFrame(step);else{anim=null;if(onEnd)onEnd();}};
        me.raf=requestAnimationFrame(step);me.onStop=onEnd;},1250);
      flyWait=wait;flyEnd=onEnd;},
    stopFly(){stopAnim();},
    // lift status: {liftId: true|false|null}; closed lifts turn grey. null clears it.
    liftState(m){liftObjs.forEach(o=>{const v=m?m[o.l.id]:null;o.c2.material.uniforms.color.value.set(v===false?SHUT:(opts.liftColor||'#2a2f38'));o.c2.material.uniforms.dash.value=v===false?40:0;});
      setChairs(m?liftObjs.filter(o=>m[o.l.id]===true):[]);request();},
    runState(m,forMe){pst.m=m||null;pst.forMe=!!forMe;Object.values(pisteObjs).forEach(runLook);request();},
    home(){flyTo(home,900);},
    north(){flyTo({az:0},600);},
    zoom(f){zoomAt(f);},
    view(v){flyTo(v,900);},
    get state(){return {...st};},
    get liftStatus(){return {chairs:chairs?chairs.list.length*3:0,closed:liftObjs.filter(o=>o.c2.material.uniforms.dash.value>0).length,shut:Object.values(pisteObjs).filter(o=>o.core.material.uniforms.dash.value>0).length,faded:Object.values(pisteObjs).filter(o=>o.core.material.uniforms.opacity.value<.2).length};},
    setTheme(dark){const c=dark?0x16243a:0xdde7f0;fog.color.setHex(c);hemi.intensity=dark?0.5:0.62;request();},
    resize,request,canvas:cvs,renderer
  };
  resize();place();
  cvs.liftStatus=()=>api.liftStatus; // for the tests: what the lift status did to the view
  return api;
};
window.GudRelief=R;
})();


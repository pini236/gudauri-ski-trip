/* New Gudauri buildings in the 3D map: a POC behind ?b3d=1 (decision 77; the architect's review, docs/ARCHITECTURE.md).
   Footprints from OpenStreetMap; heights and looks from site/data/buildings.json, which says where each one came from
   (hsrc, lsrc). A rendering, not a photo: the legend says so, a tap on a building shows its sources, and "what is
   estimated" fades every building whose height or look is not from a source.
   One facade texture drawn here on a canvas (no images), three.js r128 from the site, no library, no CSP change.
   Data loads only when the camera comes near the village. Budget: 40,000 triangles; slow frames drop the decorations. */
(function(){
'use strict';
const TILE=128,COLS=8,ATLAS=1024,PAD=6;   // the facade atlas: one bay by one storey per tile
const BAY=3.2,STOREY=3.0,GROUND=4.0;      // metres
const NEAR=4500;                          // load and show only within this distance of the village
const bounce={value:0.42}; // lower at night (setDark)
const SNOW='#f5f8fb',SNOW_SHADE='#e7ecf2';

function attach(view,opts){
  const X=view.ext,THREE=X.THREE,T=window.T||(k=>k);
  let data=null,loading=false,group=null,deco=null,decoOn=true,meta=[],card=null,legend=null,fadeOn=false,mats=[];
  const tick=[];let tapOn=false;   // frame intervals, for the slow-frame fallback

  X.minDist(140);  // the village needs a closer camera than the mountain

  // load when the camera is near the village; the scene tells us each time it is drawn
  const prev=X.scene.onBeforeRender;
  X.scene.onBeforeRender=function(){if(prev)prev.apply(this,arguments);frame();};
  function near(){const st=view.state;if(!data&&!opts.center)return st.dist<NEAR;const c=data?data.center:opts.center;
    return st.dist<NEAR&&Math.hypot(st.tx-c[0],st.tz-c[1])<NEAR;}
  const onMap=()=>/^#map/.test(location.hash);let gen=0;   // gen: a response that comes back after leaving the map is dropped
  function frame(){
    if(!onMap())return;
    if(!data){if(!loading&&view.state.dist<NEAR)load();return;}
    bounce.value=opts.dark&&opts.dark()?0.1:0.42; // less light off the snow at night
    const on=near();if(group.visible!==on){group.visible=on;if(legend)legend.hidden=!on;}
    if(!on||!decoOn)return;
    const t=performance.now();tick.push(t);if(tick.length>31)tick.shift();
    if(tick.length===31){const d=[];for(let i=1;i<31;i++)d.push(tick[i]-tick[i-1]);d.sort((a,b)=>a-b);
      // only while the camera moves (gaps over 200 ms are idle time, not slow frames)
      const m=d[15];if(m<200&&m>33){decoOn=false;deco.forEach(o=>o.visible=false);console.info('b3d: decorations off, median frame',m.toFixed(0),'ms');}}
  }
  // leaving the map frees everything (the architect's condition: the canvas texture is disposed); it loads again on return
  function dispose(){gen++;loading=false;if(!group)return;X.scene.remove(group);
    group.traverse(o=>{if(o.geometry)o.geometry.dispose();});
    mats.forEach(m=>{if(m.map)m.map.dispose();m.dispose();});
    if(legend)legend.remove();if(card)card.remove();
    group=null;deco=null;data=null;loading=false;mats=[];meta=[];legend=card=null;decoOn=true;gen++;tick.length=0;delete X.host.dataset.b3dTriangles;}
  window.addEventListener('hashchange',()=>{if(!onMap())dispose();});
  function load(){loading=true;const g=gen;fetch(opts.url).then(r=>r.ok?r.json():null).then(j=>{if(g!==gen||!onMap()){loading=false;return;}if(!j||j.schema!==1)return;data=j;build();view.request();}).catch(e=>console.warn('b3d:',e));}

  /* ---------- the facade atlas ---------- */
  function atlas(styles){
    const c=document.createElement('canvas');c.width=c.height=ATLAS;const g=c.getContext('2d');const slot={};let n=0;
    const at=()=>{const i=n++;return [(i%COLS)*TILE,Math.floor(i/COLS)*TILE];};
    const win=(x,y,w,h,frame)=>{g.fillStyle=frame;g.fillRect(x-3,y-3,w+6,h+6);const gr=g.createLinearGradient(0,y,0,y+h);gr.addColorStop(0,'#4d6278');gr.addColorStop(1,'#1f2a36');
      g.fillStyle=gr;g.fillRect(x,y,w,h);g.fillStyle='rgba(255,255,255,.22)';g.fillRect(x+2,y+2,w*0.35,h*0.5);g.fillStyle=frame;g.fillRect(x+w/2-1.5,y,3,h);};
    const rail=(y,col,snow)=>{g.fillStyle='rgba(0,0,0,.18)';g.fillRect(0,y+24,TILE,5);g.fillStyle=col;g.fillRect(0,y,TILE,24);g.fillStyle='rgba(0,0,0,.22)';
      for(let x=4;x<TILE;x+=10)g.fillRect(x,y+3,3,20);if(snow){g.fillStyle=SNOW;g.fillRect(0,y-5,TILE,6);}};
    const boards=(col)=>{g.fillStyle=col;g.fillRect(0,0,TILE,TILE);g.fillStyle='rgba(0,0,0,.14)';for(let x=0;x<TILE;x+=9)g.fillRect(x,0,2,TILE);};
    const stone=(col)=>{g.fillStyle=col;g.fillRect(0,0,TILE,TILE);for(let r=0;r<8;r++)for(let k=0;k<5;k++){const o=(r%2)*13;g.fillStyle=`rgba(${r*17%40},${k*23%40},30,${0.10+((r*7+k*5)%6)/40})`;
      g.fillRect(k*26+o-13,r*16,24,14);}};
    for(const id in styles){const S=styles[id];slot[id]={};
      let p;
      // ground floor: stone or dark base with a wide window (a shop, a lobby)
      p=at();g.save();g.translate(p[0],p[1]);stone(S.base);if(S.facade!=='plain')win(18,30,TILE-36,TILE-44,'#2a2622');else win(40,46,48,40,'#3a332c');g.restore();slot[id].g=p;
      // the middle storeys
      p=at();g.save();g.translate(p[0],p[1]);
      if(S.facade==='chalet'){g.fillStyle=S.wall;g.fillRect(0,0,TILE,TILE);g.fillStyle='rgba(0,0,0,.06)';g.fillRect(0,0,TILE,3);win(38,20,52,58,'#f7f2e8');rail(92,S.bal,true);}
      else if(S.facade==='modern'){g.fillStyle=S.wall;g.fillRect(0,0,TILE,TILE);g.fillStyle=S.bal;g.fillRect(0,0,28,TILE);g.fillRect(TILE-14,0,14,TILE);
        win(40,10,64,88,'#2b2e33');g.fillStyle='rgba(170,200,220,.55)';g.fillRect(34,84,76,24);g.fillStyle=SNOW;g.fillRect(34,80,76,4);}
      else if(S.facade==='glass'){g.fillStyle=S.wall;g.fillRect(0,0,TILE,TILE);const gr=g.createLinearGradient(0,40,0,100);gr.addColorStop(0,'#6f8597');gr.addColorStop(1,'#2a3846');
        g.fillStyle=gr;g.fillRect(0,40,TILE,62);g.fillStyle='rgba(0,0,0,.25)';for(let x=0;x<TILE;x+=32)g.fillRect(x,40,3,62);g.fillStyle=SNOW;g.fillRect(0,36,TILE,4);}
      else if(S.facade==='louver'){g.fillStyle=S.wall;g.fillRect(0,0,TILE,TILE);   // Atrium, from Pini's photo: dark grey, floor-to-ceiling glass, a glass railing, vertical mustard louvers
        const gr=g.createLinearGradient(0,8,0,112);gr.addColorStop(0,'#5f7384');gr.addColorStop(1,'#26323d');g.fillStyle=gr;g.fillRect(20,8,88,104);
        g.fillStyle='rgba(180,210,225,.35)';g.fillRect(16,84,96,28);g.fillStyle='#1d1f22';g.fillRect(16,82,96,3);
        g.fillStyle=S.bal;for(const x of [0,108])g.fillRect(x,0,20,TILE);g.fillStyle='rgba(0,0,0,.22)';for(const x of [6,13,114,121])g.fillRect(x,0,2,TILE);
        g.fillStyle=SNOW;g.fillRect(16,79,96,3);}
      else{g.fillStyle=S.wall;g.fillRect(0,0,TILE,TILE);win(44,30,40,44,'#4a3a2e');}
      g.restore();slot[id].m=p;
      // the top storey: timber on a chalet, the same as the middle elsewhere
      p=at();g.save();g.translate(p[0],p[1]);
      if(S.facade==='chalet'){boards(S.upper);win(38,20,52,58,'#efe3cc');rail(92,S.bal,true);}else g.drawImage(c,slot[id].m[0],slot[id].m[1],TILE,TILE,0,0,TILE,TILE);
      g.restore();slot[id].t=p;
      // the gable end of a dormer: timber with a small window
      p=at();g.save();g.translate(p[0],p[1]);boards(S.facade==='chalet'?S.upper:S.eave);win(46,52,36,40,'#efe3cc');g.restore();slot[id].d=p;
    }
    const tex=new THREE.CanvasTexture(c);tex.anisotropy=4; // no sRGB decode: the renderer writes linear values, and the facade colours are picked as seen
    return {tex,slot};
  }
  // a Lambert material whose uv repeats inside one atlas tile (attribute tile = the tile's corner)
  function tiled(tex){
    // light bounced off the snow: walls in the shade keep their colour (a share of the texture added as emission)
    const m=new THREE.MeshLambertMaterial({map:tex,vertexColors:true});
    m.onBeforeCompile=sh=>{
      sh.vertexShader=sh.vertexShader.replace('#include <uv_pars_vertex>','#include <uv_pars_vertex>\nattribute vec2 tile;varying vec2 vTile;attribute float wash;varying float vWash;')
        .replace('#include <uv_vertex>','#include <uv_vertex>\nvTile=tile;vWash=wash;');
      sh.uniforms.bounce=bounce;sh.fragmentShader=sh.fragmentShader.replace('#include <map_pars_fragment>','#include <map_pars_fragment>\nvarying vec2 vTile;uniform float bounce;varying float vWash;')
        .replace('#include <dithering_fragment>','#include <dithering_fragment>\ngl_FragColor.rgb=mix(gl_FragColor.rgb,vec3(.9,.92,.95),vWash*.75);')
        .replace('#include <map_fragment>',`vec2 tuv=(vTile+${PAD.toFixed(1)}+fract(vUv)*${(TILE-2*PAD).toFixed(1)})/${ATLAS.toFixed(1)};
          vec4 texelColor=texture2D(map,vec2(tuv.x,1.0-tuv.y));texelColor=mapTexelToLinear(texelColor);diffuseColor*=texelColor;totalEmissiveRadiance+=texelColor.rgb*bounce;`);
    };
    return m;
  }

  /* ---------- geometry ---------- */
  function build(){
    const G=X.ground,S=data.styles,{tex,slot}=atlas(S);
    group=new THREE.Group();group.renderOrder=2;X.scene.add(group);
    const wall={p:[],uv:[],t:[],c:[]},flat={p:[],c:[]};let tri=0;
    const col=h=>{const c=new THREE.Color(h);return [c.r,c.g,c.b];};
    const quad=(A,a,b,c,d,uv,t,cl)=>{ // a,b,c,d: [x,y,z] counter-clockwise from outside
      [a,b,c,a,c,d].forEach((q,i)=>{A.p.push(...q);if(uv){A.uv.push(...uv[[0,1,2,0,2,3][i]]);A.t.push(...t);}A.c.push(...cl);});tri+=2;};
    const tris=(A,pts,cl)=>{pts.forEach(q=>{A.p.push(...q);A.c.push(...cl);});tri+=pts.length/3;};
    meta=[];
    data.b.forEach((b,bi)=>{
      const st=S[b.look],P=b.p,n=P.length;
      let gmin=1e9,gsum=0;P.forEach(q=>{const h=G(q[0],q[1]);gmin=Math.min(gmin,h);gsum+=h;});
      const g0=gsum/n,base=gmin-2,top=g0+b.h,f0=wall.p.length/3,ff0=flat.p.length/3;
      const white=[1,1,1];
      // walls in three bands: ground storey, middle storeys, top storey
      const bands=[[base,g0+GROUND,'g',-(g0+GROUND-base)/STOREY]];
      const up=st.facade==='chalet'&&b.lv>=5?2:1; // a chalet: timber on the top two storeys, as in the photos
      if(b.lv>up+1)bands.push([g0+GROUND,top-STOREY*up,'m',0]);
      bands.push([b.lv>1?top-STOREY*up:g0+GROUND,top,'t',0]);
      if(b.lv===1)bands.splice(1);
      if(b.lv===1)bands[0][1]=top;
      for(let i=0;i<n;i++){const a=P[i],c=P[(i+1)%n],L=Math.hypot(c[0]-a[0],c[1]-a[1]);if(L<0.5)continue;
        const bays=Math.max(1,Math.round(L/BAY));
        bands.forEach(([z0,z1,k,v0])=>{if(z1-z0<0.3)return;const v1=k==='g'?0:(z1-z0)/STOREY;const vv0=k==='g'?v0:0;
          quad(wall,[a[0],z0,a[1]],[c[0],z0,c[1]],[c[0],z1,c[1]],[a[0],z1,a[1]],[[0,vv0],[bays,vv0],[bays,v1],[0,v1]],slot[b.look][k],white);});}
      // roof: hip roof as an inset ring raised, or a flat roof; snow on all of it, the eaves in the style's colour
      const ov=b.rh?0.7:0.2,out=offset(P,-ov),eave=col(st.eave),snow=col(SNOW),snowSh=col(SNOW_SHADE);
      for(let i=0;i<n;i++){const a=out[i],c=out[(i+1)%n];quad(flat,[a[0],top-0.45,a[1]],[c[0],top-0.45,c[1]],[c[0],top,c[1]],[a[0],top,a[1]],null,null,eave);}
      let cap=out,capZ=top;
      if(b.rh){const w=meanWidth(P);const ins=offset(out,Math.min(w*0.42,b.rh/0.9));cap=ins;capZ=top+b.rh;
        for(let i=0;i<n;i++){const a=out[i],c=out[(i+1)%n],a2=ins[i],c2=ins[(i+1)%n];
          // a sloped face: lighter where it faces the low winter sun (south), shaded to the north
          const nz=c[0]-a[0],sh=nz>0?snow:snowSh;quad(flat,[a[0],top,a[1]],[c[0],top,c[1]],[c2[0],capZ,c2[1]],[a2[0],capZ,a2[1]],null,null,sh);}}
      const cv=THREE.ShapeUtils.triangulateShape(cap.map(q=>new THREE.Vector2(q[0],q[1])),[]);
      cv.forEach(t=>tris(flat,[[cap[t[0]][0],capZ,cap[t[0]][1]],[cap[t[2]][0],capZ,cap[t[2]][1]],[cap[t[1]][0],capZ,cap[t[1]][1]]],snow));
      meta.push({b,wall:[f0,wall.p.length/3],flat:[ff0,flat.p.length/3],top,g0});
    });
    const wg=new THREE.BufferGeometry();wg.setAttribute('position',new THREE.Float32BufferAttribute(wall.p,3));wg.setAttribute('uv',new THREE.Float32BufferAttribute(wall.uv,2));
    wg.setAttribute('tile',new THREE.Float32BufferAttribute(wall.t,2));wg.setAttribute('wash',new THREE.Float32BufferAttribute(new Float32Array(wall.p.length/3),1));wg.setAttribute('color',new THREE.Float32BufferAttribute(wall.c,3));wg.computeVertexNormals();
    const wm=tiled(tex);const wallMesh=new THREE.Mesh(wg,wm);group.add(wallMesh);
    const fg=new THREE.BufferGeometry();fg.setAttribute('position',new THREE.Float32BufferAttribute(flat.p,3));fg.setAttribute('color',new THREE.Float32BufferAttribute(flat.c,3));fg.computeVertexNormals();
    const fm=new THREE.MeshLambertMaterial({vertexColors:true,side:THREE.DoubleSide,emissive:0x3a3f46});  // snow on the roofs stays bright
    const roofs=new THREE.Mesh(fg,fm);group.add(roofs);
    mats=[wm,fm];
    tri+=snowGround(G);
    tri+=roads(G);
    group.userData.walls=wallMesh;group.userData.roofs=roofs;const d=decorations(G,slot);deco=d.objs;tri+=d.tri;
    group.userData.tri=tri;
    X.host.dataset.b3dTriangles=String(Math.round(tri)); // for the tests and the frame budget
    if(tri>40000)console.warn('b3d: over the triangle budget',tri);
    ui();if(!tapOn){tapOn=true;X.onTap(tap);}
  }
  // a polygon moved inward by d (negative: outward), corners mitred and clamped
  function offset(P,d){const n=P.length,o=[];const ccw=area(P)>0?1:-1;
    for(let i=0;i<n;i++){const a=P[(i-1+n)%n],b=P[i],c=P[(i+1)%n];
      const n1=norm([-(b[1]-a[1]),b[0]-a[0]]),n2=norm([-(c[1]-b[1]),c[0]-b[0]]);let m=norm([n1[0]+n2[0],n1[1]+n2[1]]);
      const k=Math.max(0.35,m[0]*n1[0]+m[1]*n1[1]);o.push([b[0]+m[0]*d*ccw/k,b[1]+m[1]*d*ccw/k]);}
    return o;}
  const norm=v=>{const l=Math.hypot(v[0],v[1])||1;return [v[0]/l,v[1]/l];};
  const area=P=>{let s=0;for(let i=0;i<P.length;i++){const a=P[i],b=P[(i+1)%P.length];s+=a[0]*b[1]-b[0]*a[1];}return s/2;};
  const meanWidth=P=>{let per=0;for(let i=0;i<P.length;i++){const a=P[i],b=P[(i+1)%P.length];per+=Math.hypot(b[0]-a[0],b[1]-a[1]);}return 2*Math.abs(area(P))/per;};

  // roads: ploughed asphalt with banks of cleared snow on both sides, draped over the ground
  function roads(G){const A={p:[],c:[]},asphalt=new THREE.Color('#57524d'),bank=new THREE.Color('#e6ecf2');let tri=0;
    const ribbon=(L,w,lift,c)=>{const pts=dense(L,5);for(let i=0;i<pts.length-1;i++){const a=pts[i],b=pts[i+1],nn=norm([-(b[1]-a[1]),b[0]-a[0]]),h=w/2;
      const q=(p,s)=>[p[0]+nn[0]*h*s,G(p[0]+nn[0]*h*s,p[1]+nn[1]*h*s)+lift,p[1]+nn[1]*h*s];
      const v=[q(a,-1),q(b,-1),q(b,1),q(a,-1),q(b,1),q(a,1)];v.forEach(p=>{A.p.push(...p);A.c.push(c.r,c.g,c.b);});tri+=2;}};
    data.roads.forEach(r=>{ribbon(r.p,r.w+2.6,1.3,bank);});
    data.roads.forEach(r=>{ribbon(r.p,r.w,1.5,asphalt);});
    const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(A.p,3));g.setAttribute('color',new THREE.Float32BufferAttribute(A.c,3));g.computeVertexNormals();
    const m=new THREE.MeshLambertMaterial({vertexColors:true,side:THREE.DoubleSide,polygonOffset:true,polygonOffsetFactor:-2,polygonOffsetUnits:-4});
    const mesh=new THREE.Mesh(g,m);mesh.renderOrder=1.5;group.add(mesh);mats.push(m);return tri;}
  // the village ground in winter: the base texture tints the built-up area beige; under the buildings it is snow
  function snowGround(G){const c=data.center,R=data.radius||560,step=12,n=Math.ceil(2*R/step)+1,p=[],idx=[],col=[];
    const snow=new THREE.Color('#f1f4f8'),edge=new THREE.Color('#f6f9fc');
    for(let r=0;r<n;r++)for(let k=0;k<n;k++){const x=c[0]-R+k*step,y=c[1]-R+r*step;p.push(x,G(x,y)+0.9,y);
      const d=Math.hypot(x-c[0],y-c[1])/R,t=Math.min(1,Math.max(0,(d-0.8)/0.2));const cc=snow.clone().lerp(edge,t);col.push(cc.r,cc.g,cc.b);}
    for(let r=0;r<n-1;r++)for(let k=0;k<n-1;k++){const a=r*n+k,b=a+1,e=a+n,f=e+1;
      const mid=Math.hypot(c[0]-R+(k+.5)*step-c[0],c[1]-R+(r+.5)*step-c[1]);if(mid>R)continue;idx.push(a,e,b,b,e,f);}
    const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(p,3));g.setAttribute('color',new THREE.Float32BufferAttribute(col,3));g.setIndex(idx);g.computeVertexNormals();
    const m=new THREE.MeshLambertMaterial({vertexColors:true,polygonOffset:true,polygonOffsetFactor:-1,polygonOffsetUnits:-2});
    const mesh=new THREE.Mesh(g,m);mesh.renderOrder=1.2;group.add(mesh);mats.push(m);return idx.length/3;}
  function dense(L,step){const o=[L[0]];for(let i=1;i<L.length;i++){const a=L[i-1],b=L[i],d=Math.hypot(b[0]-a[0],b[1]-a[1]),k=Math.max(1,Math.ceil(d/step));
    for(let j=1;j<=k;j++)o.push([a[0]+(b[0]-a[0])*j/k,a[1]+(b[1]-a[1])*j/k]);}return o;}

  // decorations (fill: true in the data): street lamps; and dormer gables along the long walls of a chalet,
  // which every chalet in the photos has (instanced, and dropped first when frames are slow)
  function decorations(G,slot){const objs=[];let tri=0;const m4=new THREE.Matrix4(),q=new THREE.Quaternion(),s=new THREE.Vector3(1,1,1),up=new THREE.Vector3(0,1,0);
    const lamps=data.deco.filter(o=>o.t==='lamp');
    if(lamps.length){const pole=new THREE.CylinderGeometry(0.12,0.16,6,6);pole.translate(0,3,0);const head=new THREE.BoxGeometry(0.5,0.25,1.1);head.translate(0,6,0.35);
      const pg=mergeTwo(pole,head),pm=new THREE.MeshLambertMaterial({color:0x2c3036});const im=new THREE.InstancedMesh(pg,pm,lamps.length);
      lamps.forEach((o,i)=>{m4.compose(new THREE.Vector3(o.x,G(o.x,o.y)+0.5,o.y),q.setFromAxisAngle(up,(i*1.7)%6.28),s);im.setMatrixAt(i,m4);});
      group.add(im);objs.push(im);mats.push(pm);tri+=lamps.length*(pg.index?pg.index.count:pg.attributes.position.count)/3;}
    // dormers: a little gabled box on the roof edge, facing out, every 9 m on walls of 8 m and more
    const spots=[];
    meta.forEach(M=>{const b=M.b,st=data.styles[b.look];if(!b.rh||st.facade!=='chalet')return;const P=b.p,n=P.length,ccw=area(P)>0?1:-1;
      for(let i=0;i<n;i++){const a=P[i],c=P[(i+1)%n],L=Math.hypot(c[0]-a[0],c[1]-a[1]);if(L<8)continue;const k=Math.floor(L/9);
        const nn=norm([(c[1]-a[1])*ccw,-(c[0]-a[0])*ccw]);const ang=Math.atan2(nn[0],nn[1]);
        for(let j=0;j<k;j++){const t=(j+0.5)/k;spots.push({x:a[0]+(c[0]-a[0])*t-nn[0]*(DD-0.3),y:a[1]+(c[1]-a[1])*t-nn[1]*(DD-0.3),z:M.top,ang,look:b.look});}}});
    if(spots.length){
      const fg=dormerFront(),fm=tiled(walls().material.map),front=new THREE.InstancedMesh(fg,fm,spots.length);
      fm.onBeforeCompile=(base=>sh=>{base(sh);sh.vertexShader=sh.vertexShader.replace('attribute vec2 tile;','attribute vec2 tile;attribute vec2 itile;').replace('vTile=tile;','vTile=itile;');})(fm.onBeforeCompile);
      const tileAttr=new THREE.InstancedBufferAttribute(new Float32Array(spots.length*2),2);
      const rg=dormerRoof(),rm=new THREE.MeshLambertMaterial({color:SNOW,emissive:0x3a3f46}),roof=new THREE.InstancedMesh(rg,rm,spots.length);
      spots.forEach((o,i)=>{m4.compose(new THREE.Vector3(o.x,o.z-0.2,o.y),q.setFromAxisAngle(up,o.ang),s);front.setMatrixAt(i,m4);roof.setMatrixAt(i,m4);
        const t=slot[o.look].d;tileAttr.setXY(i,t[0],t[1]);});
      fg.setAttribute('itile',tileAttr);group.add(front);group.add(roof);objs.push(front,roof);mats.push(fm,rm);
      tri+=spots.length*(fg.attributes.position.count+rg.attributes.position.count)/3;}
    return {objs,tri};}
  const walls=()=>group.userData.walls;
  // a dormer, 4 m wide, 3 m deep, its front gable 3.8 m tall: the front uses the atlas tile 'd', the roof is snow
  const DW=2,DH=2.2,DR=1.6,DD=3;
  function dormerFront(){const p=[],uv=[],c=[],t=[],k=1/STOREY;
    const add=(v,u)=>{v.forEach((q,i)=>{p.push(...q);uv.push(...u[i]);c.push(1,1,1);t.push(0,0);});};
    add([[-DW,0,DD],[DW,0,DD],[DW,DH,DD],[-DW,0,DD],[DW,DH,DD],[-DW,DH,DD]],[[0,0],[1,0],[1,DH*k],[0,0],[1,DH*k],[0,DH*k]]);
    add([[-DW,DH,DD],[DW,DH,DD],[0,DH+DR,DD]],[[0,DH*k],[1,DH*k],[0.5,(DH+DR)*k]]);
    const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(p,3));g.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));
    g.setAttribute('color',new THREE.Float32BufferAttribute(c,3));g.setAttribute('tile',new THREE.Float32BufferAttribute(t,2));g.setAttribute('wash',new THREE.Float32BufferAttribute(new Float32Array(p.length/3),1));g.computeVertexNormals();return g;}
  function dormerRoof(){const a=[-DW-0.3,DH-0.2,DD+0.4],b=[0,DH+DR+0.15,DD+0.4],c=[DW+0.3,DH-0.2,DD+0.4],a2=[-DW-0.3,DH-0.2,-0.5],b2=[0,DH+DR+0.15,-0.5],c2=[DW+0.3,DH-0.2,-0.5];
    const p=[a,b,b2,a,b2,a2,b,c,c2,b,c2,b2].flat();const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(p,3));g.computeVertexNormals();return g;}
  function mergeTwo(a,b){const A=a.toNonIndexed(),B=b.toNonIndexed(),p=new Float32Array(A.attributes.position.count*3+B.attributes.position.count*3);
    p.set(A.attributes.position.array,0);p.set(B.attributes.position.array,A.attributes.position.count*3);const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.BufferAttribute(p,3));g.computeVertexNormals();return g;}

  /* ---------- legend, tap card, "what is estimated" ---------- */
  function ui(){
    legend=document.createElement('div');legend.className='b3d-legend';
    legend.innerHTML=`<span>${esc(T('b3d.legend'))}</span> <button type="button" class="b3d-fade" aria-pressed="false">${esc(T('b3d.show_estimated'))}</button>`;
    X.host.appendChild(legend);
    legend.querySelector('button').onclick=e=>{fadeOn=!fadeOn;e.currentTarget.setAttribute('aria-pressed',String(fadeOn));fade();};
    card=document.createElement('div');card.className='b3d-card';card.hidden=true;card.setAttribute('role','status');X.host.appendChild(card);}
  const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  // fade every building whose height or look is not from a source: their vertex colours go pale
  function fade(){const A=group.userData.walls.geometry.attributes.wash;
    meta.forEach(M=>{const v=fadeOn&&(M.b.hsrc==='estimate'||M.b.lsrc==='fill')?1:0;for(let i=M.wall[0];i<M.wall[1];i++)A.setX(i,v);});
    A.needsUpdate=true;view.request();}
  function tap(px,py){if(!group||!group.visible)return false;const rc=X.ray(px,py);
    const hits=rc.intersectObjects([group.userData.walls,group.userData.roofs],false);if(!hits.length)return false;
    const h=hits[0],v=h.face?h.faceIndex*3:-1,key=h.object===group.userData.walls?'wall':'flat';
    const M=meta.find(m=>v>=m[key][0]&&v<m[key][1]);if(!M)return false;
    const b=M.b,hs={'osm-height':'b3d.h_osm','osm-levels':'b3d.h_osm',photo:'b3d.h_photo',estimate:'b3d.h_estimate'}[b.hsrc]||'b3d.h_estimate';
    card.innerHTML=`<button type="button" class="b3d-x" aria-label="${esc(T('b3d.close'))}">×</button><b>${esc(b.n||T('b3d.building'))}</b>
      <p>${esc(T(hs,{n:b.lv}))}</p><p>${esc(T(b.lsrc==='photo'?'b3d.l_photo':'b3d.l_fill'))}</p><p class="b3d-note">${esc(T('b3d.note'))}</p>`;
    card.hidden=false;card.querySelector('.b3d-x').onclick=()=>{card.hidden=true;};return true;}
}
window.GudBuildings={attach(view,opts){window.GudBuildings.view=view;attach(view,opts);}}; // .view: for the tests and the screenshots
})();

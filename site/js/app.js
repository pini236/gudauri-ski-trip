(async function main(){
const soft=(url,fallback)=>fetch(url).then(r=>r.ok?r.json():fallback).catch(()=>fallback);
const [D,TERR,TRIP,vids]=await Promise.all([
  fetch('data/runs-and-lifts.json').then(r=>{if(!r.ok)throw new Error('runs-and-lifts '+r.status);return r.json();}),
  soft('data/terrain.json',null),
  soft('data/trip.json',null),
  soft('data/videos-seed.json',[])
]);
const HEB={green:'ירוק',blue:'כחול',red:'אדום',black:'שחור'};
const RATE={green:[1,'מתחילים'],blue:[2,'קל'],red:[3,'בינוני'],black:[4,'קשה']};
const OSMD={novice:['מתחילים','green'],easy:['קל','blue'],intermediate:['בינוני','red'],advanced:['מתקדם','black'],expert:['מומחים','black']};
const LK={chair_lift:'רכבל כיסאות',gondola:'גונדולה',platter:'מעלית צלחת',magic_carpet:'מסוע',drag_lift:'מעלית גרירה',t_bar:'מעלית T'};
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmtLen=m=>m>=1000?(m/1000).toFixed(2)+' ק״מ':m+' מ׳';
const byKey=Object.fromEntries(D.pistes.map(p=>[p.key,p]));
const reduceMotion=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
// Kobi side = everything north of Kobi Pass (the top of Firni). Shown in its own inset.
const KOBI_LAT=42.5115;
const meanLat=gs=>{let t=0,n=0;gs.forEach(g=>g.forEach(q=>{t+=q[0];n++;}));return n?t/n:0;};
const isKobiP=p=>meanLat(p.segs.map(s=>s.g))>KOBI_LAT;
const isKobiL=l=>meanLat([l.g])>KOBI_LAT;
const mainPistes=D.pistes.filter(p=>!isKobiP(p)),kobiPistes=D.pistes.filter(isKobiP);
const mainLifts=D.lifts.filter(l=>!isKobiL(l)),kobiLifts=D.lifts.filter(isKobiL);
const kobiRun=byKey['Kobi'];const PASS=kobiRun?kobiRun.segs[0].g[0]:[42.5111,44.4929];
const dispName=p=>p.named?(p.key==='Firni ?'?'Firni (1/2?)':p.key):'קטע ללא שם';

// countdown (top bar + ticket stub)
(function(){const t=new Date((TRIP&&TRIP.outbound?TRIP.outbound.date:'2027-01-10')+'T00:00:00+02:00');const d=Math.ceil((t-new Date())/864e5);
 const tb=document.getElementById('tbDays'),stub=document.getElementById('tDays'),lbl=document.getElementById('tDaysLbl');
 if(d>0){tb.textContent=d;stub.textContent=d;}else{document.getElementById('tbCount').textContent='בדרך לגודאורי';stub.textContent='0';lbl.textContent='יוצאים לדרך';}})();

// projection (meters, north up)
const lat0=42.51,lon0=44.495,kx=111320*Math.cos(lat0*Math.PI/180),ky=111320;
const P=([la,lo])=>[(lo-lon0)*kx,-(la-lat0)*ky];
const NS='http://www.w3.org/2000/svg';
const svg=document.getElementById('map');
const mk=(t,a,parent)=>{const e=document.createElementNS(NS,t);for(const k in a)e.setAttribute(k,a[k]);(parent||svg).appendChild(e);return e;};
const pathD=(g,close)=>g.map((q,i)=>{const[x,y]=P(q);return(i?'L':'M')+x.toFixed(1)+' '+y.toFixed(1)}).join('')+(close?'Z':'');

function bbox(gs){let a=1e9,b=1e9,c=-1e9,d=-1e9;gs.forEach(g=>g.forEach(q=>{const[x,y]=P(q);a=Math.min(a,x);c=Math.max(c,x);b=Math.min(b,y);d=Math.max(d,y);}));return[a,b,c,d];}
const featGeoms=(ps,ls)=>[...ls.map(l=>l.g),...ps.flatMap(p=>p.segs.map(s=>s.g))];
const [minX,minY,maxX,maxY]=bbox(featGeoms(mainPistes,mainLifts));

// grid every 500 m
function grid(root,x0,y0,x1,y1){
  const g=mk('g',{stroke:'var(--grid)','stroke-width':1,'vector-effect':'non-scaling-stroke'},root);
  for(let x=Math.floor((x0-3000)/500)*500;x<x1+3000;x+=500)mk('line',{x1:x,x2:x,y1:y0-6000,y2:y1+6000,'vector-effect':'non-scaling-stroke'},g);
  for(let y=Math.floor((y0-6000)/500)*500;y<y1+6000;y+=500)mk('line',{y1:y,y2:y,x1:x0-3000,x2:x1+3000,'vector-effect':'non-scaling-stroke'},g);
}
const TM=window.GudRelief&&TERR?GudRelief.load(TERR):null;

const pisteEls={},labels=[],stations=[];
const vs={'vector-effect':'non-scaling-stroke',fill:'none','stroke-linecap':'round','stroke-linejoin':'round'};
function draw(root,pistes,lifts,store){
  const gAreas=mk('g',{},root),gP=mk('g',{},root),gL=mk('g',{},root),gLbl=mk('g',{},root),gHitL=mk('g',{},root),gHit=mk('g',{},root); // run hit-areas sit above lift hit-areas
  pistes.forEach(p=>{
    const g=mk('g',{class:'pg '+p.color+(p.named?'':' unnamed'),'data-key':p.key},gP);
    (pisteEls[p.key]=pisteEls[p.key]||[]).push(g);
    p.segs.forEach(s=>{
      if(s.area){const a=mk('path',{d:pathD(s.g,true),fill:`var(--p-${p.color})`,'fill-opacity':.14,stroke:'none',class:'pg '+p.color},gAreas);pisteEls[p.key].push(a);return;}
      const d=pathD(s.g);
      mk('path',{d,...vs,stroke:'var(--casing)','stroke-width':p.named?6.5:4.5},g);
      mk('path',{d,...vs,stroke:`var(--p-${p.color})`,'stroke-width':p.named?(p.kind==='ski-way'?2.6:3.4):2,...(p.named?(p.kind==='ski-way'?{'stroke-dasharray':'8 5'}:{}):{'stroke-dasharray':'5 4'})},g);
      mk('path',{d,class:'hit','stroke-width':16,'vector-effect':'non-scaling-stroke','data-key':p.key,'stroke-linecap':'round'},gHit);
    });
    if(p.named){
      const lin=p.segs.filter(s=>!s.area).sort((a,b)=>b.g.length-a.g.length)[0];
      if(lin){const q=P(lin.g[Math.floor(lin.g.length/2)]);
        const t=mk('text',{x:q[0],y:q[1],class:'lbl pg '+p.color,fill:`var(--p-${p.color})`,'text-anchor':'middle','data-key':p.key},gLbl);t.textContent=dispName(p);store.labels.push(t);pisteEls[p.key].push(t);}
    }
  });
  lifts.forEach(l=>{
    const g=mk('g',{class:'lg','data-lid':l.id},gL);const d=pathD(l.g);
    mk('path',{d,...vs,stroke:'var(--casing)','stroke-width':4.5},g);
    mk('path',{d,...vs,stroke:'var(--lift)','stroke-width':l.kind==='gondola'?2.4:1.6,...(l.status==='inactive'?{'stroke-dasharray':'2 3'}:{})},g);
    [l.g[0],l.g[l.g.length-1]].forEach(q=>{const[x,y]=P(q);store.stations.push(mk('circle',{cx:x,cy:y,r:10,fill:'var(--lift)',stroke:'var(--casing)','stroke-width':1.5,'vector-effect':'non-scaling-stroke'},g));});
    mk('path',{d,class:'hit','stroke-width':14,'vector-effect':'non-scaling-stroke','data-lift':l.id},gHitL);
    if(l.name){const q=P(l.g[Math.floor(l.g.length/2)]);const t=mk('text',{x:q[0],y:q[1],class:'lbl lift lg','text-anchor':'middle'},gLbl);t.textContent='⇡ '+l.name;store.labels.push(t);}
  });
  return gLbl;
}
const mainLbl=draw(svg,mainPistes,mainLifts,{labels,stations});
const marks=[];
function addRelief(root,lblRoot,store,skipPass){
  if(!TM)return;const rel=GudRelief.svgRelief(TM,root,root.firstChild);
  const mk=document.createElementNS(NS,'g');rel.after(mk);
  const r=GudRelief.svgMarks(TM,lblRoot,mk,{marks:store.marks});
  r.peaks.forEach(t=>{if(skipPass&&t.textContent.startsWith('Kobi Pass')){t.remove();return;}t.classList.add('peak');store.labels.unshift(t);});
  r.places.forEach(t=>store.labels.push(t));
}
addRelief(svg,mainLbl,{labels,marks},true);
// signpost at Kobi Pass: opens the Kobi-side inset. First in the list so it wins label collisions.
{const[x,y]=P(PASS);const t=mk('text',{x,y,class:'lbl kobi-link','text-anchor':'middle','data-kobi':'1',role:'button','aria-label':'פתיחת צד Kobi'},mainLbl);t.textContent='צד Kobi ▲';labels.unshift(t);}

function layoutLabels(labels,stations,u,mks){
  labels.forEach(t=>{const pk=t.classList.contains('peak');t.setAttribute('font-size',(t.classList.contains('lift')?11.5:pk?12:13)*u);t.setAttribute('stroke-width',3.2*u);t.setAttribute('dy',(pk?-11:-6)*u);});
  labels=[...labels].sort((a,b)=>b.classList.contains('on')-a.classList.contains('on'));
  (mks||[]).forEach(m=>{const x=m._x,y=m._y;m.setAttribute('d',m._pass?`M${x-6*u} ${y+2*u}Q${x} ${y-5*u} ${x+6*u} ${y+2*u}`:`M${x} ${y-8*u}L${x+5.5*u} ${y+1.5*u}L${x-5.5*u} ${y+1.5*u}Z`);m.setAttribute('stroke-width',(m._pass?2.2:1.2)*u);});
  stations.forEach(c=>c.setAttribute('r',3.2*u));
  const kept=[];const pad=2*u;
  labels.forEach(t=>{t.style.display='';const bb=t.getBBox();const r={a:bb.x-pad,b:bb.y-pad,c:bb.x+bb.width+pad,d:bb.y+bb.height+pad};
    if(kept.some(k=>r.a<k.c&&r.c>k.a&&r.b<k.d&&r.d>k.b)){t.style.display='none';}else kept.push(r);});
}

// view box
let vb={x:0,y:0,w:1,h:1};
function sz(){const r=svg.getBoundingClientRect();return[r.width||1,r.height||1];}
function apply(){
  const[cw,ch]=sz();vb.h=vb.w*ch/cw;
  svg.setAttribute('viewBox',`${vb.x} ${vb.y} ${vb.w} ${vb.h}`);
  const u=vb.w/cw; // meters per px
  layoutLabels(labels,stations,u,marks);svg.classList.toggle('far',u>9);svg.querySelectorAll('.chair').forEach(c=>c.setAttribute('r',3.6*u));runMark.setAttribute('r',7*u);
  const nice=[50,100,200,250,500,1000,2000];let m=nice.find(n=>n/u>=70)||2000;
  const sc=document.getElementById('scale');sc.querySelector('i').style.width=(m/u)+'px';sc.querySelector('span').textContent=m>=1000?m/1000+' km':m+' m';
}
function fit(){const[cw,ch]=sz();const top=minY-350,bw=maxX-minX,bh=maxY-top;const w=Math.max(bw,bh*cw/ch)*1.12;vb.w=w;vb.h=w*ch/cw;vb.x=(minX+maxX)/2-w/2;vb.y=(top+maxY)/2-vb.h/2;apply();}
function zoomAt(f,px,py){const[cw,ch]=sz();const mx=vb.x+px/cw*vb.w,my=vb.y+py/ch*vb.h;const nw=Math.min(Math.max(vb.w*f,150),30000);const r=nw/vb.w;vb.x=mx-(mx-vb.x)*r;vb.y=my-(my-vb.y)*r;vb.w=nw;apply();}
new ResizeObserver(()=>{if(vb.w===1)fit();else{const cx=vb.x+vb.w/2,cy=vb.y+vb.h/2;apply();vb.x=cx-vb.w/2;vb.y=cy-vb.h/2;apply();}}).observe(svg);
svg.addEventListener('wheel',e=>{e.preventDefault();const r=svg.getBoundingClientRect();zoomAt(Math.exp(e.deltaY*0.0015),e.clientX-r.left,e.clientY-r.top);},{passive:false});
const ptrs=new Map();let moved=0,pinch=null;
svg.addEventListener('pointerdown',e=>{svg.setPointerCapture(e.pointerId);ptrs.set(e.pointerId,{x:e.clientX,y:e.clientY});moved=0;svg.classList.add('drag');if(ptrs.size===2){const[a,b]=[...ptrs.values()];pinch=Math.hypot(a.x-b.x,a.y-b.y);}});
svg.addEventListener('pointermove',e=>{const p=ptrs.get(e.pointerId);if(!p)return;const[cw]=sz();const u=vb.w/cw;
  if(ptrs.size===1){const dx=e.clientX-p.x,dy=e.clientY-p.y;moved+=Math.abs(dx)+Math.abs(dy);vb.x-=dx*u;vb.y-=dy*u;p.x=e.clientX;p.y=e.clientY;apply();}
  else if(ptrs.size===2){p.x=e.clientX;p.y=e.clientY;const[a,b]=[...ptrs.values()];const d=Math.hypot(a.x-b.x,a.y-b.y);const r=svg.getBoundingClientRect();if(pinch){zoomAt(pinch/d,(a.x+b.x)/2-r.left,(a.y+b.y)/2-r.top);}pinch=d;moved+=10;}});
function up(e){const was=ptrs.size;ptrs.delete(e.pointerId);if(ptrs.size<2)pinch=null;if(!ptrs.size)svg.classList.remove('drag');
  if(was===1&&moved<6&&e.type==='pointerup'){const el=document.elementFromPoint(e.clientX,e.clientY);if(el&&el.dataset.kobi)openInset();else if(el&&el.dataset.key)select(el.dataset.key);else if(el&&el.dataset.lift)showLift(+el.dataset.lift);}}
svg.addEventListener('pointerup',up);svg.addEventListener('pointercancel',up);
document.getElementById('zin').onclick=()=>{const[cw,ch]=sz();zoomAt(1/1.5,cw/2,ch/2);};
document.getElementById('zout').onclick=()=>{const[cw,ch]=sz();zoomAt(1.5,cw/2,ch/2);};
document.getElementById('zfit').onclick=fit;

// Kobi-side inset
const kSvg=document.getElementById('kobimap'),card=document.getElementById('insetCard'),openBtn=document.getElementById('insetOpen');
const K={labels:[],stations:[]};
const [kx0,ky0,kx1,ky1]=bbox([...featGeoms(kobiPistes,kobiLifts),[PASS]]);
const kLbl=draw(kSvg,kobiPistes,kobiLifts,K);K.marks=[];
if(TM)addRelief(kSvg,kLbl,K,false);else{const[x,y]=P(PASS);const t=mk('text',{x,y,class:'lbl place','text-anchor':'middle'},kLbl);t.textContent='Kobi Pass';K.labels.unshift(t);}
function layoutInset(){
  if(card.hidden)return;const r=kSvg.getBoundingClientRect();const cw=r.width||1,ch=r.height||1;
  const w=Math.max(kx1-kx0,(ky1-ky0)*cw/ch)*1.3,h=w*ch/cw;
  kSvg.setAttribute('viewBox',`${(kx0+kx1)/2-w/2} ${(ky0+ky1)/2-h/2} ${w} ${h}`);
  layoutLabels(K.labels,K.stations,w/cw,K.marks);kSvg.classList.toggle('far',w/cw>9);
}
function openInset(){card.hidden=false;openBtn.hidden=true;openBtn.setAttribute('aria-expanded','true');layoutInset();}
function closeInset(){card.hidden=true;openBtn.hidden=false;openBtn.setAttribute('aria-expanded','false');openBtn.focus();}
openBtn.onclick=openInset;
document.getElementById('insetClose').onclick=closeInset;
new ResizeObserver(layoutInset).observe(kSvg);
kSvg.addEventListener('click',e=>{const el=e.target.closest('[data-key],[data-lift]');if(!el)return;if(el.dataset.key)select(el.dataset.key);else showLift(+el.dataset.lift);});

// filters
const hidden=new Set();
function applyFilters(){['green','blue','red','black','unnamed','lifts'].forEach(k=>[svg,kSvg].forEach(m=>m.classList.toggle('hide-'+k,hidden.has(k))));if(v3)v3.filter(hidden);}
const st=document.createElement('style');st.textContent='.hide-green .pg.green,.hide-blue .pg.blue,.hide-red .pg.red,.hide-black .pg.black,.hide-unnamed .pg.unnamed,.hide-lifts .lg{display:none}.has-sel .pg:not(.on){opacity:.28}.pg.on path{filter:drop-shadow(0 0 2.5px var(--glacier))}';document.head.appendChild(st);

// selection + panel
const panel=document.getElementById('panel');
let current=null;
function clearSel(){stopFly();paint2d(null);setMarker(null);if(v3){v3.select(null);v3.paint(null);}[svg,kSvg].forEach(m=>{m.classList.remove('has-sel');m.querySelectorAll('.pg.on').forEach(e=>e.classList.remove('on'));});}
// ---- run view: a run painted in slope colours, elevation profile, briefing (design round 3, T1 to T4) ----
// Lines of a run, each ordered from its top to its bottom, sampled every ~10 m along the line:
// {x,y} projected metres, d metres from the top, h metres, a slope in degrees (over about 40 m).
const topDown=L=>TM&&TM.elev(L[0][0],L[0][1])<TM.elev(L[L.length-1][0],L[L.length-1][1])?L.slice().reverse():L;
function sampleLine(L,step){
  const o=[];let d=0;
  for(let i=0;i<L.length;i++){
    if(i){const a=L[i-1],b=L[i],l=Math.hypot(b[0]-a[0],b[1]-a[1]),n=Math.max(1,Math.round(l/step));
      for(let k=1;k<=n;k++){const t=k/n;o.push({x:a[0]+(b[0]-a[0])*t,y:a[1]+(b[1]-a[1])*t,d:d+l*t});}d+=l;}
    else o.push({x:L[0][0],y:L[0][1],d:0});}
  o.forEach(q=>q.h=TM.elev(q.x,q.y));
  o.forEach((q,i)=>{let a=i,b=i;while(a>0&&q.d-o[a].d<20)a--;while(b<o.length-1&&o[b].d-q.d<20)b++;const dd=o[b].d-o[a].d;q.a=dd?Math.atan(Math.abs(o[b].h-o[a].h)/dd)*180/Math.PI:0;});
  return o;
}
const runLines=p=>p.segs.filter(s=>!s.area).map(s=>topDown(s.g.map(P)));
const profCache={};
function runProfile(key){ // the longest line of the run, top to bottom
  if(profCache[key])return profCache[key];const p=byKey[key];if(!p||!TM)return null;
  const Ls=runLines(p);if(!Ls.length)return null;
  const S=Ls.map(L=>sampleLine(L,10)).sort((a,b)=>b[b.length-1].d-a[a.length-1].d)[0];
  // the steepest 100 m, as a grade
  let best={g:0,d:0};for(let i=0;i<S.length;i++){let j=i;while(j<S.length-1&&S[j].d-S[i].d<100)j++;const dd=S[j].d-S[i].d;if(dd>=60){const g=(S[i].h-S[j].h)/dd;if(g>best.g)best={g,d:S[i].d,i,j};}}
  return profCache[key]={S,steep:best};
}
// 2D: the selected run, painted from the top down in slope colours
const gPaint=mk('g',{class:'runpaint','aria-hidden':'true'});svg.insertBefore(gPaint,mainLbl);
const runMark=mk('circle',{class:'runmark',r:10,cx:0,cy:0,'vector-effect':'non-scaling-stroke'});runMark.style.display='none';
function paint2d(key){
  gPaint.classList.remove('on');gPaint.innerHTML='';
  const p=key&&byKey[key];if(!p||!TM||isKobiP(p))return;
  const Ls=runLines(p),sc=GudRelief.slopeCanvas(TM,Ls);
  mk('image',{href:sc.canvas.toDataURL(),x:sc.x0,y:sc.y0,width:sc.x1-sc.x0,height:sc.y1-sc.y0,preserveAspectRatio:'none',class:'rp-ground'},gPaint);
  Ls.forEach(L=>{const S=sampleLine(L,12),tot=S[S.length-1].d||1,dur=1.3;
    mk('path',{d:'M'+S.map(q=>q.x.toFixed(1)+' '+q.y.toFixed(1)).join('L'),...vs,stroke:'#FFFFFF','stroke-width':10,class:'rp-cas'},gPaint);
    for(let i=0;i<S.length-1;i++)mk('line',{x1:S[i].x.toFixed(1),y1:S[i].y.toFixed(1),x2:S[i+1].x.toFixed(1),y2:S[i+1].y.toFixed(1),stroke:GudRelief.slopeColor(S[i].a),'stroke-width':6,'stroke-linecap':'round','vector-effect':'non-scaling-stroke',style:`transition-delay:${(S[i].d/tot*dur).toFixed(2)}s`},gPaint);});
  requestAnimationFrame(()=>requestAnimationFrame(()=>gPaint.classList.add('on')));
}
function setMarker(q){
  if(!q){runMark.style.display='none';if(v3)v3.marker(null);return;}
  runMark.style.display='';runMark.setAttribute('cx',q.x);runMark.setAttribute('cy',q.y);if(v3)v3.marker(q.x,q.y);
}
function focusOn(els){let a=1e9,b=1e9,c=-1e9,d=-1e9;els.forEach(g=>g.forEach(q=>{const[x,y]=P(q);a=Math.min(a,x);c=Math.max(c,x);b=Math.min(b,y);d=Math.max(d,y);}));
  const[cw,ch]=sz();const w=Math.max(c-a,(d-b)*cw/ch,900)*1.5;vb.w=w;vb.h=w*ch/cw;vb.x=(a+c)/2-w/2;vb.y=(b+d)/2-vb.h/2;apply();}
function select(key,{zoom=true,push=true}={}){
  const p=byKey[key];clearSel();
  if(p){[svg,kSvg].forEach(m=>m.classList.add('has-sel'));(pisteEls[key]||[]).forEach(e=>e.classList.add('on'));if(v3){v3.select(key);v3.paint(key,1300);}paint2d(key);
    if(zoom){if(view==='3d')v3.focus(key);else if(isKobiP(p))openInset();else focusOn(p.segs.map(s=>s.g));}}
  current=key;renderPiste(key);
  const want='#map/run/'+encodeURIComponent(key);if(push&&location.hash!==want)history.pushState(null,'',want);
  if(matchMedia('(max-width:760px)').matches&&!zoom)panel.scrollIntoView({behavior:'smooth',block:'start'});
}
function pips(c){const n=RATE[c][0];return `<span class="pips c-${c}">${[1,2,3,4].map(i=>`<i class="${i<=n?'on':''}"></i>`).join('')}</span>`;}
function liftBtn(name){const l=D.lifts.find(x=>x.name===name);return l?`<button class="tag" data-lift="${l.id}">⇡ ${esc(name)}</button>`:esc(name);}
function pisteBtn(k){const p=byKey[k];return p?`<button class="tag c-${p.color}" data-goto="${esc(k)}">${esc(dispName(p))}</button>`:'';}
function notesFor(p){
  const n=[];
  if(p.key==='Firni ?')n.push('ב-OSM הקו נקרא "Frini" ללא מספר. במפה הרשמית יש Firni 1 ו-Firni 2, ולא ברור לאיזה מהם הקו שייך. השני חסר בנתונים.');
  if(p.key==='Zuma')n.push('ב-OSM מופיע כשני קווים, Zuma-1 ו-Zuma-2. במפה הרשמית: Zuma.');
  const mism=p.osmDiff.filter(x=>x!=='—'&&OSMD[x]&&OSMD[x][1]!==p.color);
  if(p.named&&mism.length)n.push(`ב-OSM חלק מהקטעים מדורגים "${mism.map(x=>OSMD[x][0]).join(', ')}". הצבע כאן לפי המפה הרשמית (${HEB[p.color]}).`);
  if(p.named&&p.osmDiff.includes('—'))n.push('לחלק מהקטעים אין דירוג קושי ב-OSM. הצבע לפי המפה הרשמית.');
  const lin=p.segs.filter(s=>!s.area).length;if(lin>1)n.push(`${lin} קטעים נפרדים ב-OSM. האורך הוא סכום הקטעים.`);
  if(p.segs.some(s=>s.area))n.push('כולל גם שטח מסלול (פוליגון) שלא נספר באורך.');
  if(p.lit.includes('yes'))n.push('מסומן כמואר בערב.');
  if(p.kind==='ski-way')n.push('לפי המקרא של המפה הרשמית זו דרך מקשרת (Ski Way) בין אזורים, ולא מסלול.');
  if(p.kind==='beginner-area')n.push('אזור מתחילים. מוצג כקו המרכז שלו.');
  if(!p.named)n.push('קטע בנתוני OSM בלי שם. לא שויך למסלול רשמי. הצבע לפי דירוג OSM.');
  return n;
}
function hostOf(u){try{return new URL(u).hostname.replace(/^www\./,'')}catch{return u}}
const ytId=u=>{try{const x=new URL(u);if(/(^|\.)youtube\.com$/.test(x.hostname))return(x.searchParams.get('v')||'').match(/^[\w-]{11}$/)?x.searchParams.get('v'):null;if(x.hostname==='youtu.be'){const i=x.pathname.slice(1);return/^[\w-]{11}$/.test(i)?i:null;}}catch{}return null;};
function vidList(key){
  const list=vids.filter(v=>v.piste===key).sort((a,b)=>(b.at||0)-(a.at||0));
  return list.length?`<ul class="vids">${list.map(v=>{const id=ytId(v.url);const thumb=id?`<button type="button" class="vthumb" data-vid="${id}" data-title="${esc(v.title||'')}" aria-label="הפעלת הסרטון${v.title?': '+esc(v.title):''}"><img src="https://i.ytimg.com/vi/${id}/hqdefault.jpg" alt="" loading="lazy"><span class="play"></span></button>`:'';
    return `<li>${thumb}<a href="${esc(v.url)}" target="_blank" rel="noopener">${esc(v.title||hostOf(v.url))}</a><small>${esc(v.channel?[v.channel,v.length].filter(Boolean).join(' · '):[v.by||'',v.at?new Date(v.at).toLocaleDateString('he-IL'):''].filter(Boolean).join(' · '))}</small></li>`;}).join('')}</ul>`:'<p class="hint">עוד אין סרטונים למסלול הזה.</p>';
}
function vidBlock(key,label){
  return `<h3>סרטונים</h3>${vidList(key)}
  <p class="hint"><a href="https://www.youtube.com/results?search_query=${encodeURIComponent('Gudauri '+label+' ski')}" target="_blank" rel="noopener">חיפוש "Gudauri ${esc(label)}" ביוטיוב ↗</a></p>`;
}
function elevRows(p){
  if(!TM)return'';const lines=p.segs.filter(s=>!s.area).map(s=>s.g.map(P));if(!lines.length)return'';
  const s=GudRelief.stats(TM,lines);
  return `<dt>גובה</dt><dd><span class="num">${s.top}</span> מ׳ למעלה, <span class="num">${s.bot}</span> מ׳ למטה</dd>
  <dt>ירידה</dt><dd><span class="num">${s.drop}</span> מ׳${lines.length===1&&p.len?` · שיפוע ממוצע <span class="num">${Math.round(s.drop/p.len*100)}%</span>`:''}</dd>
  <dt>קטע תלול</dt><dd><span class="num">${Math.round(s.maxG*100)}%</span> <span class="hint">(100 מ׳ התלולים ביותר)</span></dd>`;
}
const CONFH={high:'גבוהה',medium:'בינונית',low:'נמוכה'};
const STATH={'osm-named':'דרך ב-OSM שנשאה את השם הזה','osm-unnamed-match':'דרך ב-OSM בלי שם, שהותאמה לפי המיקום במפה הרשמית','gps':'הקלטות GPS'};
function researchBlock(p){const r=p.research;
  return `<h3>מקור ומידת ודאות</h3>
  <dl class="kv"><dt>ודאות</dt><dd>${CONFH[r.conf]||esc(r.conf)}</dd>
  <dt>מקור</dt><dd>${esc(STATH[r.status]||r.status)}${r.historical?' (הדרך נמחקה מ-OSM ב-2024, נלקחה מהיסטוריית העריכה)':''}</dd>
  ${r.gps?`<dt>הקלטות GPS</dt><dd><span class="num">${r.gps}</span> הקלטות ציבוריות עוברות לאורכו</dd>`:''}
  ${r.partial?`<dt>היקף</dt><dd>${esc(r.partial)}</dd>`:''}</dl>
  <p class="hint">${esc(r.notes)}</p>
  <ul class="notes">${r.sources.map(x=>`<li class="hint">${esc(x)}</li>`).join('')}</ul>`;}
const navList=()=>{const order=['green','blue','red','black'];return D.pistes.filter(p=>p.named).sort((a,b)=>order.indexOf(a.color)-order.indexOf(b.color)||a.key.localeCompare(b.key,undefined,{numeric:true})).map(p=>p.key);};
function runNav(key){
  const L=navList(),i=L.indexOf(key);if(i<0)return `<div class="run-nav"><button type="button" class="rn-share" data-share="${esc(key)}">שיתוף</button></div>`;
  const prev=L[(i-1+L.length)%L.length],next=L[(i+1)%L.length];
  return `<div class="run-nav" role="group" aria-label="מעבר בין מסלולים"><button type="button" data-goto="${esc(prev)}" aria-label="המסלול הקודם: ${esc(prev)}">→ <span dir="ltr">${esc(prev)}</span></button><button type="button" class="rn-share" data-share="${esc(key)}">שיתוף</button><button type="button" data-goto="${esc(next)}" aria-label="המסלול הבא: ${esc(next)}"><span dir="ltr">${esc(next)}</span> ←</button></div>`;
}
let cmpStats=null;
function comparable(){ // steepness and length of every named run, once
  if(cmpStats)return cmpStats;cmpStats=[];
  D.pistes.forEach(p=>{if(!p.named||(p.kind&&p.kind!=='run')||!TM)return;const Ls=runLines(p);if(!Ls.length)return;const s=GudRelief.stats(TM,Ls);cmpStats.push({key:p.key,g:s.maxG,len:p.len});});
  return cmpStats;
}
function runViewBlock(key){
  const pr=runProfile(key),p=byKey[key];if(!pr||pr.S.length<4)return '';
  const S=pr.S,W=340,Hc=118,n=S.length,dmax=S[n-1].d,hs=S.map(q=>q.h),hmax=Math.max(...hs),hmin=Math.min(...hs);
  const X=d=>(8+d/dmax*(W-16)).toFixed(1),Y=h=>(8+(hmax-h)/((hmax-hmin)||1)*(Hc-30)).toFixed(1);
  const line=S.map(q=>X(q.d)+','+Y(q.h)).join(' ');
  let band='';for(let i=0,j=0;i<n-1;i=j){const c=GudRelief.slopeColor(S[i].a);j=i+1;while(j<n-1&&GudRelief.slopeColor(S[j].a)===c)j++;band+=`<rect x="${X(S[i].d)}" y="${Hc-14}" width="${(X(S[j].d)-X(S[i].d)+.6).toFixed(1)}" height="7" fill="${c}"/>`;}
  const st=pr.steep,deg=g=>Math.round(Math.atan(g)*180/Math.PI),maxG=GudRelief.stats(TM,runLines(p)).maxG; // the same number as in the details above
  const first=S.find(q=>q.d>=150)||S[n-1],g0=(S[0].h-first.h)/(first.d||1);
  const cmp=comparable(),me=cmp.find(x=>x.key===key);
  let cmpHtml='';if(me&&cmp.length>2){const others=cmp.filter(x=>x.key!==key);
    const bg=others.slice().sort((a,b)=>Math.abs(a.g-me.g)-Math.abs(b.g-me.g))[0],bl=others.slice().sort((a,b)=>Math.abs(a.len-me.len)-Math.abs(b.len-me.len))[0];
    cmpHtml=`<p class="run-cmp">תלול בערך כמו ${pisteBtn(bg.key)} ארוך בערך כמו ${pisteBtn(bl.key)}</p>`;}
  const endTxt=p.toLifts.length?`מגיעים לרכבל ${p.toLifts.map(liftBtn).join('')}`:p.joins.length?`ממשיכים אל ${p.joins.map(pisteBtn).join('')}`:'';
  const startTxt=p.fromLifts.length?`יורדים מרכבל ${p.fromLifts.map(liftBtn).join('')}`:'';
  const fly=can3d&&!reduceMotion()?`<button type="button" class="btn run-fly" data-fly="${esc(key)}">טיסה במורד המסלול</button>`:'';
  return `<h3>פרופיל הגובה</h3>
  <div class="prof"><svg viewBox="0 0 ${W} ${Hc}" preserveAspectRatio="none" aria-hidden="true">
    <polygon points="8,${Hc-16} ${line} ${W-8},${Hc-16}" class="pf-fill"/><polyline points="${line}" class="pf-line"/>${band}
    ${st.g?`<rect x="${X(st.d)}" y="0" width="${(X(S[st.j].d)-X(st.d)).toFixed(1)}" height="${Hc-16}" class="pf-steep"/>`:''}
    <line id="pfX" x1="8" x2="8" y1="0" y2="${Hc-8}" class="pf-x"/></svg><span class="pf-dot" id="pfDot" style="left:${(8/W*100).toFixed(2)}%;top:${Y(S[0].h)}px"></span>
    <input type="range" id="profRange" min="0" max="${n-1}" value="0" aria-label="מיקום לאורך המסלול, מלמעלה למטה" dir="ltr" data-key="${esc(key)}">
    <span class="pf-top num">${Math.round(hmax)}</span><span class="pf-bot num">${Math.round(hmin)}</span></div>
  <div class="prof-read"><span><small>מההתחלה</small><b class="num" id="pfD">0 מ׳</b></span><span><small>גובה</small><b class="num" id="pfH">${Math.round(S[0].h).toLocaleString('en-US')} מ׳</b></span><span><small>שיפוע כאן</small><b class="num" id="pfA">${Math.round(S[0].a)}°</b></span></div>
  <ul class="slope-key">${GudRelief.SLOPE.map(([,c,t])=>`<li><i style="background:${c}"></i>${t}</li>`).join('')}</ul>
  ${fly}
  <h3>מה מחכה לך</h3>
  <ol class="brief">
    <li><b>התחלה · <span class="num">${Math.round(S[0].h)}</span> מ׳</b><span>${deg(g0)}° ב-150 המטרים הראשונים. ${startTxt}</span></li>
    ${st.g?`<li class="b-steep"><b>הקטע התלול · אחרי <span class="num">${Math.round(st.d)}</span> מ׳</b><span>${deg(maxG)}° (<span class="num">${Math.round(maxG*100)}%</span>) לאורך 100 מ׳</span></li>`:''}
    <li><b>הסוף · <span class="num">${Math.round(S[n-1].h)}</span> מ׳</b><span>אחרי <span class="num">${Math.round(dmax)}</span> מ׳. ${endTxt}</span></li>
  </ol>
  ${cmpHtml}
  <p class="hint">פני השטח עד 150 מ׳ מהמסלול צבועים לפי אותו מקרא. הגבהים והשיפועים ממודל הגובה (כ-30 מ׳), לאורך הקו הארוך ביותר של המסלול. בקירות קצרים השיפוע האמיתי יכול להיות גבוה יותר.</p>`;
}
function profAt(i){
  const k=document.getElementById('profRange');if(!k)return;const pr=runProfile(k.dataset.key);if(!pr)return;const S=pr.S,q=S[Math.max(0,Math.min(S.length-1,i))];
  const svgp=panel.querySelector('.prof svg'),vb_=svgp.viewBox.baseVal,W=vb_.width,Hc=vb_.height,dmax=S[S.length-1].d,hs=S.map(z=>z.h),hmax=Math.max(...hs),hmin=Math.min(...hs);
  const x=8+q.d/dmax*(W-16),y=8+(hmax-q.h)/((hmax-hmin)||1)*(Hc-30);
  const X=document.getElementById('pfX');X.setAttribute('x1',x);X.setAttribute('x2',x);const dot=document.getElementById('pfDot');dot.style.left=(x/W*100)+'%';dot.style.top=y+'px';
  document.getElementById('pfD').textContent=Math.round(q.d).toLocaleString('en-US')+' מ׳';document.getElementById('pfH').textContent=Math.round(q.h).toLocaleString('en-US')+' מ׳';document.getElementById('pfA').textContent=Math.round(q.a)+'°';
  setMarker(q);
}
let flying=false;
function stopFly(){if(flying&&v3)v3.stopFly();flying=false;}
function renderPiste(key){
  const p=byKey[key];
  if(!p){const m=D.missing.find(x=>x.name===key);if(!m)return overview();
    panel.innerHTML=`<button class="back" data-back>→ כל המסלולים</button><h2 class="c-${m.color}">${esc(m.name)}</h2>
    <dl class="kv"><dt>צבע רשמי</dt><dd>${pips(m.color)}${HEB[m.color]} · ${RATE[m.color][1]}</dd><dt>אורך</dt><dd>—</dd></dl>
    <h3>הערות</h3><div class="notice">מסלול זה מופיע במפה הרשמית אבל אין לו קו בנתוני OpenStreetMap. לא ציירנו אותו כדי לא לנחש את התוואי.</div>${vidBlock(key,m.name)}`;
    return;}
  const c=p.color,label=p.named?(p.key==='Firni ?'?'Firni':p.key):'';
  panel.innerHTML=`<button class="back" data-back>→ כל המסלולים</button>
  <div class="run-sign"><h2 class="c-${c}">${esc(dispName(p))}</h2>${p.refs.length?`<span class="run-ref num" title="סימון המסלול">${esc(p.refs[0])}</span>`:''}</div>
  ${runNav(key)}
  <dl class="kv">
    <dt>אורך</dt><dd class="num">${fmtLen(p.len)}</dd>
    ${elevRows(p)}
    <dt>צבע</dt><dd><span class="sw ${c}"></span> ${HEB[c]}${p.named?' (מפה רשמית)':' (לפי OSM)'}</dd>
    <dt>קושי</dt><dd>${pips(c)}${RATE[c][1]}</dd>
    ${p.osmDiff.length?`<dt>דירוג OSM</dt><dd>${p.osmDiff.map(x=>OSMD[x]?OSMD[x][0]:'ללא').join(' / ')}</dd>`:''}
    ${p.refs.length?`<dt>סימון</dt><dd class="num">${esc(p.refs.join(', '))}</dd>`:''}
    ${p.groom.length?`<dt>הכשרה</dt><dd>${p.groom.includes('classic')?'מוכשר (ratrak)':esc(p.groom.join(', '))}</dd>`:''}
  </dl>
  ${runViewBlock(key)}
  <h3>חיבורים</h3>
  <dl class="kv">
    <dt>רכבל בראש</dt><dd>${p.fromLifts.map(liftBtn).join('')||'—'}</dd>
    <dt>רכבל בתחתית</dt><dd>${p.toLifts.map(liftBtn).join('')||'—'}</dd>
    <dt>מתחבר אל</dt><dd>${p.joins.map(pisteBtn).join('')||'—'}</dd>
    <dt>מגיעים מ־</dt><dd>${p.fromPistes.map(pisteBtn).join('')||'—'}</dd>
  </dl>
  ${TM?'<p class="hint">גבהים מתוך מודל פני השטח (רזולוציה של כ-30 מ׳), דיוק בערך ±15 מ׳.</p>':''}
  <p class="hint">חושב מקרבת קצוות הקווים (עד 200 מ׳ לתחנה, 60 מ׳ למסלול). כדאי לוודא מול המפה הרשמית.</p>
  <h3>הערות</h3><ul class="notes">${notesFor(p).map(x=>`<li>${esc(x)}</li>`).join('')||'<li>אין</li>'}</ul>
  ${p.research?researchBlock(p):''}
  <p class="hint">OSM: ${[...new Set(p.research&&p.research.osmIds.length?p.research.osmIds:p.segs.map(s=>s.id))].map(id=>`<a href="https://www.openstreetmap.org/way/${id}" target="_blank" rel="noopener">${id}</a>`).join(' · ')}</p>
  ${p.named?vidBlock(key,label):''}`;
}
function liftElev(l){
  if(!TM){return l.rise?`<dt>הפרש גובה</dt><dd class="num">${esc(l.rise)} מ׳</dd>`:'';}
  const a=P(l.g[0]),b=P(l.g[l.g.length-1]),ea=Math.round(TM.elev(a[0],a[1])),eb=Math.round(TM.elev(b[0],b[1]));
  return `<dt>תחנות</dt><dd><span class="num">${Math.min(ea,eb)}</span> מ׳ למטה, <span class="num">${Math.max(ea,eb)}</span> מ׳ למעלה</dd><dt>הפרש גובה</dt><dd><span class="num">${Math.abs(eb-ea)}</span> מ׳ (מהמודל)${l.rise?` · ב-OSM: <span class="num">${esc(l.rise)}</span> מ׳`:''}</dd>`;
}
function showLift(id){
  const l=D.lifts.find(x=>x.id===id);if(!l)return;clearSel();current=null;if(location.hash.startsWith('#map/run/'))history.pushState(null,'','#map');
  const top=D.pistes.filter(p=>p.fromLifts.includes(l.name)).map(p=>pisteBtn(p.key)).join('');
  const bot=D.pistes.filter(p=>p.toLifts.includes(l.name)).map(p=>pisteBtn(p.key)).join('');
  panel.innerHTML=`<button class="back" data-back>→ כל המסלולים</button><h2>⇡ ${esc(l.name||'ללא שם')}</h2>
  <dl class="kv"><dt>סוג</dt><dd>${LK[l.kind]||esc(l.kind)}${l.status==='inactive'?' (לא פעיל לפי OSM)':''}</dd>
  <dt>אורך</dt><dd class="num">${fmtLen(l.len)}</dd>
  ${l.dur?`<dt>זמן נסיעה</dt><dd class="num">${esc(l.dur)} דק׳</dd>`:''}
  ${l.occ?`<dt>מקומות</dt><dd class="num">${esc(l.occ)}${l.bubble==='yes'?' · עם כיפה':''}</dd>`:''}
  ${l.cap?`<dt>קיבולת</dt><dd class="num">${esc(l.cap)} לשעה</dd>`:''}
  ${liftElev(l)}
  ${l.year?`<dt>נבנה</dt><dd class="num">${esc(l.year)}</dd>`:''}</dl>
  <h3>מסלולים מהתחנה העליונה</h3><div>${top||'—'}</div>
  <h3>מסלולים שמסתיימים בתחנה התחתונה</h3><div>${bot||'—'}</div>
  <p class="hint">OSM: <a href="https://www.openstreetmap.org/way/${l.id}" target="_blank" rel="noopener">${l.id}</a></p>`;
}
function overview(){
  clearSel();current=null;
  const named=D.pistes.filter(p=>p.named);const order=['green','blue','red','black'];
  named.sort((a,b)=>order.indexOf(a.color)-order.indexOf(b.color)||a.key.localeCompare(b.key,undefined,{numeric:true}));
  const f=[...order.map(c=>[c,HEB[c]]),['unnamed','ללא שם'],['lifts','רכבלים']];
  panel.innerHTML=`
  <h2 class="ov">כל המסלולים</h2>
  ${LSTAT.block()}
  <p class="lead">לחצו על מסלול במפה או ברשימה לפרטים וסרטונים. קו מקווקו דק הוא קטע בלי שם בנתונים, וקו מקווקו כחול הוא דרך מקשרת. הצד הצפוני של Kobi נפתח מהכפתור בפינת המפה.</p>
  <h3>מסלולים במפה · ${named.length}</h3>
  <div class="index">${named.map(p=>`<button data-goto="${esc(p.key)}" data-zoom="1"><span class="sw ${p.color}"></span>${esc(dispName(p))}<span class="len">${fmtLen(p.len)}</span></button>`).join('')}</div>
  ${D.missing.length?`<h3>במפה הרשמית, חסרים בנתונים · ${D.missing.length}</h3>
  <div class="miss">${D.missing.map(m=>`<button class="chip" data-goto="${esc(m.name)}"><span class="sw ${m.color}"></span>${esc(m.name)}</button>`).join('')}</div>`:''}
  ${(()=>{const pr=D.pistes.filter(p=>p.research&&p.research.partial);return pr.length?`<h3>הושלמו חלקית · ${pr.length}</h3>
  <div class="miss">${pr.map(p=>`<button class="chip" data-goto="${esc(p.key)}" data-zoom="1"><span class="sw ${p.color}"></span>${esc(p.key)}</button>`).join('')}</div>
  <p class="hint">מסלולים שנמצא רק חלק מהם, או רק קו המרכז שלהם. בפרטים של כל אחד: מה נמצא, מאיפה ובאיזו ודאות. את החלקים החסרים לא ציירנו, כדי לא לנחש.</p>`:''})()}
  <h3>מקור</h3>
  <p class="hint">קווים: OpenStreetMap דרך Overpass, נמשך ${D.fetched}. שמות וצבעים לפי המפה הרשמית של Gudauri (MTA). אורכים אופקיים מהקואורדינטות. תבליט וגבהים: אריחי גובה Terrarium (AWS Open Data, בעיקר SRTM). פסגות: שמות וגבהים לפי המפה הרשמית, מיקום לפי OSM. הכפר, הכבישים והאגם: OSM. השלמת המסלולים החסרים: היסטוריית העריכה של OSM והקלטות GPS ציבוריות (api.openstreetmap.org), ${esc(D.research?D.research.date:'')}. © OpenStreetMap contributors, ODbL.</p>
  <p class="hint">במבט התלת-ממדי: גרירה מסובבת, גלגלת או צביטה מזיזות זום, מקש ימני או שתי אצבעות מזיזים את המפה.</p>`;
}
panel.addEventListener('input',e=>{if(e.target.id==='profRange')profAt(+e.target.value);});
panel.addEventListener('click',e=>{
  const b=e.target.closest('button');if(!b)return;
  if(b.dataset.share!==undefined){const k=b.dataset.share,url=location.origin+location.pathname+'#map/run/'+encodeURIComponent(k);
    if(navigator.share)navigator.share({title:'גודאורי 2027: '+k,url}).catch(()=>{});
    else if(navigator.clipboard)navigator.clipboard.writeText(url).then(()=>{b.textContent='הקישור הועתק';setTimeout(()=>{b.textContent='שיתוף';},2200);}).catch(()=>{});return;}
  if(b.dataset.fly){if(flying){stopFly();return;}const pr=runProfile(b.dataset.fly);if(!pr)return;if(view!=='3d')setView('3d',false);if(!v3)return;
    flying=true;b.textContent='עצירה';b.setAttribute('aria-pressed','true');
    v3.flyAlong(pr.S.map(q=>[q.x,q.y]),()=>{flying=false;const bb=panel.querySelector('[data-fly]');if(bb){bb.textContent='טיסה במורד המסלול';bb.setAttribute('aria-pressed','false');}});return;}
  if(b.dataset.back!==undefined){overview();if(location.hash!=='#map')history.pushState(null,'','#map');return;}
  if(b.dataset.filter){const k=b.dataset.filter;hidden.has(k)?hidden.delete(k):hidden.add(k);b.setAttribute('aria-pressed',!hidden.has(k));applyFilters();return;}
  if(b.dataset.vid){const w=document.createElement('div');w.className='vframe';const f=document.createElement('iframe');
    f.src='https://www.youtube-nocookie.com/embed/'+b.dataset.vid+'?autoplay=1&rel=0';f.title=b.dataset.title||'סרטון';f.allow='autoplay; encrypted-media; picture-in-picture; fullscreen';f.allowFullscreen=true;f.referrerPolicy='strict-origin-when-cross-origin';
    w.appendChild(f);b.replaceWith(w);return;}
  if(b.dataset.goto){select(b.dataset.goto,{zoom:true});panel.scrollTop=0;if(matchMedia('(max-width:760px)').matches)panel.scrollIntoView({block:'start'});return;}
  if(b.dataset.lift){const l=D.lifts.find(x=>x.id===+b.dataset.lift);showLift(+b.dataset.lift);if(l){if(view==='3d')v3.focusLift(l.id);else if(isKobiL(l))openInset();else focusOn([l.g]);}return;}
});

{let sx=0,sy=0,ok=false;
  panel.addEventListener('touchstart',e=>{const t=e.touches[0];ok=e.touches.length===1&&!e.target.closest('input,.prof');sx=t.clientX;sy=t.clientY;},{passive:true});
  panel.addEventListener('touchend',e=>{if(!ok||!current)return;const t=e.changedTouches[0],dx=t.clientX-sx,dy=t.clientY-sy;if(Math.abs(dx)<70||Math.abs(dy)>45)return;
    const L=navList(),i=L.indexOf(current);if(i<0)return;select(L[(i+(dx<0?1:-1)+L.length)%L.length]);},{passive:true});}
// ---- 3D view ----
let v3=null,view='2d';
const wrap=document.querySelector('.mapwrap'),m3=document.getElementById('map3d'),sw=document.getElementById('viewsw');
const compassSvg=document.querySelector('#compass svg');
const isDark=()=>{const t=document.documentElement.dataset.theme;return t?t==='dark':matchMedia('(prefers-color-scheme: dark)').matches;};
function hasGL(){try{const c=document.createElement('canvas');return !!(c.getContext('webgl')||c.getContext('experimental-webgl'));}catch(e){return false;}}
const can3d=!!(window.THREE&&TM&&hasGL());
function ensure3d(){
  if(v3)return true;if(!can3d)return false;
  try{
    const kc=P([42.532,44.4945]);
    v3=GudRelief.View3D({model:TM,host:m3,pistes:D.pistes,lifts:D.lifts,P,dispName,colors:{green:'#1B8A4C',blue:'#1F5FC4',red:'#D1342B',black:'#13233A'},liftColor:'#3A4556',
      center:[(minX+maxX)/2,(minY+maxY)/2+250],homeDist:7000,homeAz:0,homePol:0.44,
      onPick:k=>select(k),onLift:id=>showLift(id),
      onFly:f=>{const k=document.getElementById('profRange');if(k){const i=Math.round(f*(+k.max));k.value=i;profAt(i);}},
      onHeading:az=>{compassSvg.style.transform=`rotate(${(az*180/Math.PI).toFixed(1)}deg)`;}});
    v3.kobi=()=>v3.view({tx:kc[0],tz:kc[1],dist:6800,az:Math.PI*0.9,pol:0.6});
    v3.setTheme(isDark());DN.paint(false); // the 3D light follows the time in Gudauri, like the home page
    v3.filter(hidden);if(current&&byKey[current]){v3.select(current);v3.paint(current,0);}
    return true;
  }catch(e){console.warn('3D unavailable',e);v3=null;return false;}
}
function setView(m,save){
  if(m==='3d'&&!ensure3d())m='2d';view=m;
  m3.hidden=m!=='3d';svg.style.visibility=m==='3d'?'hidden':'';
  wrap.classList.toggle('mode3d',m==='3d');wrap.classList.toggle('mode2d',m!=='3d');
  sw.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.view===m)));
  if(m==='3d'){v3.resize();if(!card.hidden)closeInset();}else apply();
  if(save)try{localStorage.setItem('gud-view',m);}catch(e){}
}
sw.addEventListener('click',e=>{const b=e.target.closest('button[data-view]');if(b)setView(b.dataset.view,true);});
document.getElementById('zin').onclick=()=>{if(view==='3d')return v3.zoom(1/1.5);const[cw,ch]=sz();zoomAt(1/1.5,cw/2,ch/2);};
document.getElementById('zout').onclick=()=>{if(view==='3d')return v3.zoom(1.5);const[cw,ch]=sz();zoomAt(1.5,cw/2,ch/2);};
document.getElementById('zfit').onclick=()=>{if(view==='3d')return v3.home();fit();};
document.getElementById('compass').onclick=()=>{if(v3)v3.north();};
openBtn.onclick=()=>{if(view==='3d')v3.kobi();else openInset();};
{const _up=up;} // keep 2D handlers
wrap.classList.add('mode2d');
// filters live in the toolbar above the map
document.getElementById('filters').addEventListener('click',e=>{const b=e.target.closest('button[data-filter]');if(!b)return;const k=b.dataset.filter;
  hidden.has(k)?hidden.delete(k):hidden.add(k);b.setAttribute('aria-pressed',String(!hidden.has(k)));applyFilters();});
// the map (and its 3D model) is set up the first time the map page is opened
let mapReady=false;
function activateMap(){
  if(!mapReady){mapReady=true;fit();
    if(can3d){sw.hidden=false;let pref=null;try{pref=localStorage.getItem('gud-view');}catch(e){}setView(pref==='2d'?'2d':'3d');}}
  else if(view==='3d'&&v3)v3.resize();else apply();
}
// pages: #map shows the map, anything else the home page
const pgHome=document.getElementById('home'),pgMap=document.getElementById('mapPage'),pgMeet=document.getElementById('meetPage'),pgGames=document.getElementById('gamesPage');
function route(){
  const h=location.hash,m=h.startsWith('#map'),mt=h.startsWith('#meet'),gm=h.startsWith('#games'),run=h.startsWith('#map/run/')?decodeURIComponent(h.slice(9)):null,wasMap=!pgMap.hidden;
  pgHome.hidden=m||mt||gm;pgMap.hidden=!m;pgMeet.hidden=!mt;pgGames.hidden=!gm;
  const cur=m?'map':mt?'meet':gm?'games':'home';
  document.querySelectorAll('[data-nav]').forEach(a=>{if(a.dataset.nav===cur)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});
  requestAnimationFrame(()=>{if(gm)return;if(mt){if(MEET)MEET.open(h.slice(6));return;}if(!m){DN.layout();return;}activateMap();
    if(run&&run!==current&&(byKey[run]||D.missing.some(x=>x.name===run)))select(run,{push:false});
    else if(!run&&current)overview();});
  if(!wasMap||!m){window.scrollTo(0,0);pgHome.scrollTop=0;}
}
addEventListener('hashchange',route);

// home: day and night. Three modes (auto, day, night). Auto follows the clock in Gudauri (UTC+4) and the
// sunrise and sunset there on today's date. The mountains are the real view above New Gudauri, rendered
// from the elevation model in seven moments of the day (design/round3/panorama.py), and cross-faded.
const DN=(function(){
  const LAT=42.51,LON=44.495,TZ=4,rad=Math.PI/180;
  // positions in the images, as fractions (design/round3/pano.json)
  const PJ={phone:{w:390,pk:[['Sadzele',.6215,.3989],['Bidara',.3796,.4325]],vil:[.4525,.6926]},
            wide:{w:1440,pk:[['Sadzele',.5659,.3989],['Bidara',.4347,.4325]],vil:[.4742,.6926]}};
  const ELE=Object.fromEntries((TM?TM.peaks:[]).map(p=>[p.n,p.ele]));
  const gud=()=>{const g=new Date(Date.now()+TZ*36e5);return {h:g.getUTCHours()+g.getUTCMinutes()/60,g};};
  function sunTimes(g){
    const start=Date.UTC(g.getUTCFullYear(),0,1),n=Math.floor((g-start)/864e5)+1;
    const dec=-23.44*Math.cos(2*Math.PI/365*(n+10))*rad,b=2*Math.PI/364*(n-81);
    const eot=9.87*Math.sin(2*b)-7.53*Math.cos(b)-1.5*Math.sin(b);
    const half=Math.acos(-Math.tan(LAT*rad)*Math.tan(dec))/rad/15,noon=12-(LON-TZ*15)/15-eot/60;
    return {rise:noon-half,set:noon+half,noon};
  }
  const NIGHT={img:'night',sky:['#050A15','#1A2645'],glow:0,star:1,moon:1,win:1,snow:1};
  function keys(t){return [
    {h:0,...NIGHT},{h:t.rise-1.2,...NIGHT},
    {h:t.rise-.3,img:'dawn',sky:['#2C3B66','#E8A987'],glow:.7,glowC:'#FFB38A',star:.2,moon:.3,win:.8,snow:0},
    {h:t.rise+1.2,img:'morning',sky:['#6FA6DC','#DCEAF4'],glow:.35,glowC:'#FFF2D6',star:0,moon:0,win:0,snow:0},
    {h:t.noon,img:'noon',sky:['#4F90D2','#D2E4F3'],glow:.2,glowC:'#FFFFFF',star:0,moon:0,win:0,snow:0},
    {h:t.set-1.8,img:'gold',sky:['#6F9CCB','#F1DDC2'],glow:.6,glowC:'#FFD29A',star:0,moon:0,win:0,snow:0},
    {h:t.set-.35,img:'sunset',sky:['#3A4677','#F09A6A'],glow:1,glowC:'#FF9A6A',star:.1,moon:.2,win:.6,snow:0},
    {h:t.set+.45,img:'dusk',sky:['#1B2448','#6E5D86'],glow:.35,glowC:'#C98AA0',star:.6,moon:.8,win:1,snow:.3},
    {h:t.set+1.3,...NIGHT},{h:24,...NIGHT}];}
  const hex=c=>[1,3,5].map(i=>parseInt(c.slice(i,i+2),16));
  const mix=(a,b,t)=>'#'+hex(a).map((v,i)=>Math.round(v+(hex(b)[i]-v)*t).toString(16).padStart(2,'0')).join('');
  const lerp=(a,b,t)=>a+(b-a)*t;
  const MODES=['auto','day','night'],LBL={auto:'אוטומטי',day:'יום',night:'לילה'};
  let mode='auto';try{const m=localStorage.getItem('gud-daynight');if(MODES.includes(m))mode=m;}catch(e){}
  const sky=document.getElementById('homeSky'),pano=document.getElementById('pano'),root=document.documentElement;
  // stars and snowflakes: fixed positions, so the sky looks the same on every visit
  document.getElementById('skyStars').innerHTML=[[4,8],[11,19],[18,5],[25,14],[33,7],[40,21],[47,4],[55,16],[61,9],[68,24],[74,6],[81,15],[88,10],[94,22],[7,30],[29,28],[51,31],[72,33],[90,29],[15,40],[38,38],[63,41],[84,37],[97,4]].map(([x,y])=>`<i style="left:${x}%;top:${y}%;animation-delay:-${(x*y%37)/10}s"></i>`).join('');
  document.getElementById('skySnow').innerHTML=Array.from({length:14},(_,i)=>`<i style="left:${3+i*7}%;animation-duration:${7+i%4}s;animation-delay:-${(i*0.7).toFixed(1)}s"></i>`).join('');
  let variant=null,layer=null,shown='';
  function build(v){
    variant=v;shown='';pano.innerHTML='';layer=null;
    const P=PJ[v],vx=P.vil[0]*100,vy=P.vil[1]*100;
    const lights=[[-44,6],[-35,2],[-28,9],[-19,4],[-12,11],[-5,1],[3,7],[9,13],[16,3],[24,10],[31,5],[39,12],[-23,15],[0,16],[20,17],[46,8]]
      .map(([dx,dy])=>`<i style="left:calc(${vx}% + ${dx}px);top:calc(${vy}% + ${dy}px)"></i>`).join('');
    const lbl=P.pk.map(([n,x,y])=>`<span class="pk-lbl" style="left:${x*100}%;top:calc(${y*100}% - 6px)">${esc(n)}${ELE[n]?` <span class="e">${ELE[n]}</span>`:''}</span>`).join('');
    pano.insertAdjacentHTML('beforeend',`<div class="lights">${lights}</div>${lbl}`);
  }
  const src=k=>`img/pano/pano-${variant==='wide'?'wide-':''}${k}.webp`;
  function layout(){
    const W=sky.clientWidth,H=sky.clientHeight;if(!W||!H)return;
    const v=W>560?'wide':'phone';if(v!==variant)build(v);
    const iw=PJ[v].w,s=Math.max(W/iw,H/400),w=iw*s,h=400*s;
    Object.assign(pano.style,{width:w+'px',height:h+'px',left:((W-w)/2)+'px',top:Math.min(0,H*.9-.75*h)+'px'});
    paint(false);
  }
  function state(){
    const {h:now,g}=gud(),t=sunTimes(g);
    const h=mode==='day'?t.noon:mode==='night'?22:now;
    const K=keys(t);let i=0;while(i<K.length-2&&K[i+1].h<h)i++;
    const a=K[i],b=K[i+1],f=Math.min(1,Math.max(0,(h-a.h)/((b.h-a.h)||1)));
    const dark=mode==='night'||(mode==='auto'&&(h<t.rise-.3||h>t.set+.3));
    const phase=dark?(h>t.set&&h<t.set+1.3?'דמדומים':'לילה'):h<t.rise+.8?'זריחה':h<t.set-2?'יום':h<t.set-.6?'שעת זהב':'שקיעה';
    return {a,b,f,dark,now,h,g,phase,morning:h<t.noon};
  }
  function paint(anim){
    const s=state(),{a,b,f}=s,num=k=>lerp(a[k],b[k],f);
    root.dataset.theme=s.dark?'dark':'light';
    const hh=Math.floor(s.now),mm=Math.floor((s.now-hh)*60);
    document.querySelectorAll('[data-dn-clock]').forEach(e=>e.textContent=String(hh).padStart(2,'0')+':'+String(mm).padStart(2,'0'));
    document.querySelectorAll('[data-dn]').forEach(bt=>{bt.dataset.mode=mode;const nx=MODES[(MODES.indexOf(mode)+1)%3];
      bt.setAttribute('aria-label',`מצב תצוגה: ${LBL[mode]}. לחיצה עוברת ל${LBL[nx]}`);bt.title=bt.getAttribute('aria-label');});
    document.querySelectorAll('[data-dn-lbl]').forEach(e=>e.textContent=LBL[mode]);
    document.querySelectorAll('[data-days-word]').forEach(e=>e.textContent=s.dark?'לילות':'ימים');
    if(typeof v3!=='undefined'&&v3){v3.setTheme(s.dark);const p=GudRelief.sun(s.g,s.h);v3.setLight({alt:p.alt,az:p.az,dark:s.dark,sky:[mix(a.sky[0],b.sky[0],f),mix(a.sky[1],b.sky[1],f)]});}
    if(!variant)return;
    sky.style.setProperty('--sky-top',mix(a.sky[0],b.sky[0],f));sky.style.setProperty('--sky-bot',mix(a.sky[1],b.sky[1],f));
    root.style.setProperty('--sky-ink',s.dark?'#EAF0F7':'#13233A');root.style.setProperty('--sky-halo',s.dark?'rgba(13,21,34,.6)':'rgba(255,255,255,.6)');
    sky.style.setProperty('--sky-chip',s.dark?'rgba(13,21,34,.45)':'rgba(255,255,255,.55)');
    const g=document.getElementById('skyGlow');g.style.opacity=num('glow');g.style.left=s.morning?'96%':'4%';g.style.setProperty('--glow-c',(f<.5?a:b).glowC||'#fff');
    document.getElementById('skyStars').style.opacity=num('star');document.getElementById('skySnow').style.opacity=num('snow');
    const mo=document.getElementById('skyMoon');mo.style.opacity=num('moon');
    pano.querySelector('.lights').style.opacity=num('win');
    document.getElementById('skyPhase').textContent=s.phase;
    // the mountains: layer a, with b on top at opacity f. A new pair fades in over the old one.
    const want=a.img+'|'+b.img;
    if(want!==shown){
      const el=document.createElement('div');el.className='pl';
      el.innerHTML=`<img alt="" src="${src(a.img)}"><img alt="" class="b" src="${src(b.img)}">`;
      const old=layer;layer=el;shown=want;pano.insertBefore(el,pano.querySelector('.lights'));
      if(old&&anim){el.style.opacity=0;requestAnimationFrame(()=>requestAnimationFrame(()=>{el.style.opacity=1;}));setTimeout(()=>old.remove(),1000);}
      else if(old)old.remove();
    }
    layer.querySelector('.b').style.opacity=a.img===b.img?0:f;
  }
  document.querySelectorAll('[data-dn]').forEach(bt=>bt.addEventListener('click',()=>{
    mode=MODES[(MODES.indexOf(mode)+1)%3];try{localStorage.setItem('gud-daynight',mode);}catch(e){}paint(true);}));
  new ResizeObserver(()=>{if(!pgHome.hidden)layout();}).observe(sky);
  setInterval(()=>paint(true),60000);
  paint(false);
  return {layout,paint,mode:()=>mode};
})();

// ---- meeting point (design round 3, M1 to M3): pick a lift station and a time, share it. No database: all of it is in the link. ----
const MEET=(function(){
  const host=document.getElementById('meetMap');if(!host)return null;
  // stations: both ends of every named lift on the main side, merged when closer than 70 m (shared top stations)
  const st=[];
  mainLifts.filter(l=>l.name&&l.status!=='inactive').forEach(l=>{
    const a=P(l.g[0]),b=P(l.g[l.g.length-1]);let lo=a,hi=b;
    if(TM&&TM.elev(a[0],a[1])>TM.elev(b[0],b[1])){lo=b;hi=a;}
    [[lo,'b'],[hi,'t']].forEach(([q,end])=>{
      const near=st.find(s=>Math.hypot(s.x-q[0],s.y-q[1])<70);
      if(near){near.ends.push({l,end});return;}
      st.push({id:l.id+end,x:q[0],y:q[1],h:TM?Math.round(TM.elev(q[0],q[1])):null,ends:[{l,end}]});});
  });
  st.forEach(s=>{const b=s.ends.filter(e=>e.end==='b'),t=s.ends.filter(e=>e.end==='t'),n=a=>a.map(e=>e.l.name).join(' ו-');
    s.name=(b[0]||t[0]).l.name;
    s.where=[b.length?`התחנה התחתונה של ${n(b)}`:'',t.length?`התחנה העליונה של ${n(t)}`:''].filter(Boolean).join(', ');});
  const byId=Object.fromEntries(st.map(s=>[s.id,s]));
  const find=name=>(end)=>st.find(s=>s.ends.some(e=>e.l.name===name&&e.end===end));
  // ski days of the trip (11 to 14 January), and suggested fixed spots for the group
  const DAYS=[['2027-01-11','ב׳ 11.1'],['2027-01-12','ג׳ 12.1'],['2027-01-13','ד׳ 13.1'],['2027-01-14','ה׳ 14.1']];
  const TIMES=['09:30','11:00','12:30','13:30','15:00','16:30'];
  const PRE=[['am','רכבל הבוקר',find('Goodaura')('b'),'09:30'],['noon','צהריים',find('Goodaura')('t'),'13:00'],['pm','סוף יום',find('New Goodaura')('b'),'16:30']].filter(p=>p[2]);
  const S={sid:(find('Goodaura')('b')||st[0]).id,time:'12:30',day:DAYS[0][0],preset:''};
  // map
  const svgM=host;const m=(t,a,p)=>{const e=document.createElementNS(NS,t);for(const k in a)e.setAttribute(k,a[k]);(p||svgM).appendChild(e);return e;};
  const gLines=m('g',{class:'mm-lines'}),gPins=m('g',{class:'mm-pins'});
  if(TM)GudRelief.svgRelief(TM,svgM,gLines);
  mainPistes.filter(p=>p.named).forEach(p=>p.segs.filter(s=>!s.area).forEach(s=>m('path',{d:pathD(s.g),class:'mm-run','vector-effect':'non-scaling-stroke',stroke:`var(--p-${p.color})`},gLines)));
  mainLifts.filter(l=>l.name).forEach(l=>m('path',{d:pathD(l.g),class:'mm-lift','vector-effect':'non-scaling-stroke'},gLines));
  const pins={};
  st.forEach(s=>{const g=m('g',{class:'mm-pin','data-sid':s.id},gPins);
    m('ellipse',{class:'mm-ring',cx:0,cy:0,rx:11,ry:5},g);
    m('path',{d:'M0 0C0 0-14-17-14-28A14 14 0 0 1 14-28C14-17 0 0 0 0Z',class:'mm-body'},g);
    m('circle',{cx:0,cy:-28,r:5.5,class:'mm-dot'},g);pins[s.id]=g;});
  let vb={x:0,y:0,w:1,h:1},tween=0;
  const size=()=>{const r=svgM.getBoundingClientRect();return [r.width||1,r.height||1];};
  function applyVB(){const[w,h]=size();vb.h=vb.w*h/w;svgM.setAttribute('viewBox',`${vb.x} ${vb.y} ${vb.w} ${vb.h}`);const u=vb.w/w;
    st.forEach(s=>{const on=s.id===S.sid;pins[s.id].setAttribute('transform',`translate(${s.x} ${s.y}) scale(${u*(on?1.2:.8)})`);pins[s.id].classList.toggle('on',on);});
    const s=byId[S.sid],c=document.getElementById('meetCallout');
    if(s&&c){const x=(s.x-vb.x)/vb.w*w,y=(s.y-vb.y)/vb.h*h;c.style.left=Math.max(8,Math.min(w-208,x-100))+'px';c.style.top=Math.max(6,y-128)+'px';c.style.setProperty('--tip',Math.max(12,Math.min(184,x-Math.max(8,Math.min(w-208,x-100))-8))+'px');}}
  function goTo(x,y,span,ms){const[w,h]=size();const to={w:span,x:x-span/2,y:y-span*h/w*0.55};const from={...vb};
    cancelAnimationFrame(tween);if(reduceMotion()||!ms||from.w===1){Object.assign(vb,to);applyVB();return;}
    const t0=performance.now(),step=()=>{const t=Math.min(1,(performance.now()-t0)/ms),e=t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;
      vb.x=from.x+(to.x-from.x)*e;vb.y=from.y+(to.y-from.y)*e;vb.w=from.w+(to.w-from.w)*e;applyVB();if(t<1)tween=requestAnimationFrame(step);};tween=requestAnimationFrame(step);}
  svgM.addEventListener('click',e=>{const r=svgM.getBoundingClientRect(),x=vb.x+(e.clientX-r.left)/r.width*vb.w,y=vb.y+(e.clientY-r.top)/r.height*vb.h;
    const best=st.map(s=>[s,Math.hypot(s.x-x,s.y-y)]).sort((a,b)=>a[1]-b[1])[0];if(best)pick(best[0].id,'',true);});
  document.getElementById('meetAll').onclick=()=>{let a=1e9,b=1e9,c=-1e9,d=-1e9;st.forEach(s=>{a=Math.min(a,s.x);c=Math.max(c,s.x);b=Math.min(b,s.y);d=Math.max(d,s.y);});
    const[w,h]=size();const span=Math.max(c-a,(d-b)*w/h)*1.15;goTo((a+c)/2,(b+d)/2+ (d-b)*0.05,span,700);};
  new ResizeObserver(()=>{if(!document.getElementById('meetPage').hidden)applyVB();}).observe(svgM);
  // controls
  const ui=document.getElementById('meetUI');
  ui.querySelector('[data-days]').innerHTML=DAYS.map(([v,l])=>`<button type="button" data-day="${v}">${l}</button>`).join('');
  ui.querySelector('[data-times]').innerHTML=TIMES.map(t=>`<button type="button" class="num" data-time="${t}">${t}</button>`).join('');
  ui.querySelector('[data-pre]').innerHTML=PRE.map(([k,l,s,t],i)=>`<button type="button" class="mp-sign mp-${k}" data-pre="${k}"><b>${l}</b><span dir="ltr">${esc(s.name)} ${t}</span></button>`).join('');
  ui.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;
    if(b.dataset.day){S.day=b.dataset.day;S.preset='';render();}
    else if(b.dataset.time){S.time=b.dataset.time;S.preset='';ui.querySelector('#meetTime').value=S.time;render();}
    else if(b.dataset.pre){const p=PRE.find(x=>x[0]===b.dataset.pre);S.time=p[3];ui.querySelector('#meetTime').value=S.time;pick(p[2].id,p[0],true);}});
  ui.querySelector('#meetTime').addEventListener('input',e=>{if(/^\d\d:\d\d$/.test(e.target.value)){S.time=e.target.value;S.preset='';render();}});
  function pick(id,preset,fly){S.sid=id;S.preset=preset||'';const s=byId[id];if(fly&&s)goTo(s.x,s.y,Math.max(1400,vb.w<1500?vb.w:1800),650);render();}
  // how to get there, from the connections in the data only
  const chip=p=>`<span class="rt-run c-${p.color}" dir="ltr">${esc(dispName(p))}</span>`,liftChip=n=>`<span class="rt-lift" dir="ltr">⇡ ${esc(n)}</span>`,dot='<span class="rt-dot" aria-hidden="true"></span>',sep='<span class="rt-sep" aria-hidden="true"></span>';
  function routes(s){const out=[];
    s.ends.forEach(({l,end})=>{
      if(end==='b')D.pistes.filter(p=>p.named&&p.toLifts.includes(l.name)).forEach(p=>{const pr=p.fromPistes.map(k=>byKey[k]).find(x=>x&&x.named&&x.key!==p.key);
        out.push({from:pr?`מ-${pr.key}`:`מראש ${p.key}`,html:[pr?chip(pr):'',chip(p),dot].filter(Boolean).join(sep)});});
      else out.push({from:`מהתחנה התחתונה של ${l.name}`,html:[liftChip(l.name),dot].join(sep)});});
    return out.filter((r,i)=>out.findIndex(q=>q.html===r.html)===i).slice(0,4);}
  const pad=n=>String(n).padStart(2,'0');
  function link(){return location.origin+location.pathname+`#meet/${S.sid}/${S.time.replace(':','')}/${S.day.replace(/-/g,'')}`;}
  function dayLbl(){return (DAYS.find(d=>d[0]===S.day)||[0,S.day.split('-').reverse().join('.')])[1];}
  function message(){const s=byId[S.sid];return `נפגשים ב-${s.name} ביום ${dayLbl()} בשעה ${S.time}.\n${s.where}${s.h?`, ${s.h.toLocaleString('en-US')} מ׳`:''}.\nעל המפה: ${link()}`;}
  function countdown(){ // in Gudauri time (UTC+4)
    const [y,mo,d]=S.day.split('-').map(Number),[hh,mm]=S.time.split(':').map(Number);
    const t=Date.UTC(y,mo-1,d,hh-4,mm),diff=(t-Date.now())/6e4;
    if(diff<0)return ['','כבר עבר',''];if(diff<24*60)return diff<60?['עוד',Math.round(diff),'דקות למפגש']:['עוד',Math.floor(diff/60)+':'+pad(Math.round(diff%60)),'שעות למפגש'];
    return ['עוד',Math.ceil(diff/1440),'ימים למפגש'];}
  function render(){
    const s=byId[S.sid];if(!s)return;applyVB();
    const c=document.getElementById('meetCallout');c.querySelector('b').textContent=s.name;c.querySelector('.mc-alt').textContent=s.h?s.h.toLocaleString('en-US')+' מ׳':'';c.querySelector('.mc-where').textContent=s.where;
    ui.querySelectorAll('[data-day]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.day===S.day)));
    ui.querySelectorAll('[data-time]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.time===S.time)));
    ui.querySelectorAll('[data-pre]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.pre===S.preset)));
    const card=document.getElementById('meetCard'),[c1,c2,c3]=countdown();
    card.querySelector('[data-f="name"]').textContent=s.name;card.querySelector('[data-f="time"]').textContent=S.time;
    card.querySelector('[data-f="where"]').textContent=s.where;card.querySelector('[data-f="alt"]').textContent=s.h?s.h.toLocaleString('en-US')+' מ׳':'—';
    card.querySelector('[data-f="day"]').textContent=dayLbl();
    card.querySelector('[data-f="c1"]').textContent=c1;card.querySelector('[data-f="c2"]').textContent=c2;card.querySelector('[data-f="c3"]').textContent=c3;
    const R=routes(s);document.getElementById('meetRoutes').innerHTML=R.length?R.map(r=>`<li><small>${esc(r.from)}</small><div class="rt">${r.html}</div></li>`).join(''):'<li class="hint">אין בנתונים מסלול שמגיע לכאן.</li>';
    document.getElementById('meetWa').href='https://wa.me/?text='+encodeURIComponent(message());
    const want=`#meet/${S.sid}/${S.time.replace(':','')}/${S.day.replace(/-/g,'')}`;if(location.hash.startsWith('#meet')&&location.hash!==want)history.replaceState(null,'',want);
  }
  // the image for sharing: the mountain around the spot, the pin, the name and the time
  async function image(){
    const s=byId[S.sid],W=1080,H=1080,cv=document.createElement('canvas');cv.width=W;cv.height=H;const x=cv.getContext('2d');
    const span=2200,k=W/span,ox=s.x-span/2,oy=s.y-span*0.55,X=v=>(v-ox)*k,Y=v=>(v-oy)*k;
    x.fillStyle='#EEF2F5';x.fillRect(0,0,W,H);
    if(TM){const img=new Image();img.src=TM.hill.src;await img.decode().catch(()=>{});const d=TM.dem;x.globalAlpha=.55;x.drawImage(img,X(d.x0),Y(d.y0),TM.W*k,TM.Hm*k);x.globalAlpha=1;}
    x.lineCap=x.lineJoin='round';
    const line=(g,c,w)=>{x.strokeStyle=c;x.lineWidth=w;x.beginPath();g.forEach((q,i)=>{const[a,b]=P(q);i?x.lineTo(X(a),Y(b)):x.moveTo(X(a),Y(b));});x.stroke();};
    const COLS={green:'#1B8A4C',blue:'#1F5FC4',red:'#D1342B',black:'#13233A'};
    mainPistes.filter(p=>p.named).forEach(p=>p.segs.filter(z=>!z.area).forEach(z=>{line(z.g,'#fff',11);line(z.g,COLS[p.color],6);}));
    mainLifts.filter(l=>l.name).forEach(l=>{line(l.g,'#fff',6);line(l.g,'#3A4556',3);});
    const px=X(s.x),py=Y(s.y);x.fillStyle='#F4B942';x.strokeStyle='#13233A';x.lineWidth=6;
    x.beginPath();x.ellipse(px,py,46,18,0,0,7);x.stroke();
    x.beginPath();x.moveTo(px,py);x.bezierCurveTo(px,py,px-50,py-60,px-50,py-100);x.arc(px,py-100,50,Math.PI,0);x.bezierCurveTo(px+50,py-60,px,py,px,py);x.fill();x.stroke();
    x.fillStyle='#fff';x.beginPath();x.arc(px,py-100,19,0,7);x.fill();x.stroke();
    const g=x.createLinearGradient(0,H-420,0,H);g.addColorStop(0,'rgba(19,35,58,0)');g.addColorStop(.45,'rgba(19,35,58,.85)');g.addColorStop(1,'rgba(19,35,58,.95)');x.fillStyle=g;x.fillRect(0,H-420,W,420);
    await document.fonts.ready.catch(()=>{});
    const DISP='"Karantina","Arial Narrow",sans-serif',BODY='"IBM Plex Sans Hebrew",sans-serif';
    const fit=(t,max,px)=>{x.font=`700 ${px}px ${DISP}`;while(px>60&&x.measureText(t).width>max){px-=6;x.font=`700 ${px}px ${DISP}`;}return px;};
    x.direction='ltr';x.textAlign='left';
    const tw=Math.min(300,(fit(S.time,300,150),x.measureText(S.time).width)+50);
    const fs=fit(s.name,W-60-tw-30-110-40,150),nw=x.measureText(s.name).width+110;
    x.fillStyle='#1F5FC4';x.beginPath();x.moveTo(60,H-170);x.lineTo(110,H-270);x.lineTo(60+nw,H-270);x.lineTo(60+nw,H-70);x.lineTo(110,H-70);x.closePath();x.fill();
    x.fillStyle='#fff';x.font=`700 ${fs}px ${DISP}`;x.fillText(s.name,130,H-112);
    x.fillStyle='#F4B942';x.fillRect(W-60-tw,H-270,tw,200);fit(S.time,tw-40,150);x.fillStyle='#13233A';x.textAlign='center';x.fillText(S.time,W-60-tw/2,H-112);
    x.direction='rtl';x.textAlign='right';x.fillStyle='#fff';x.font=`600 40px ${BODY}`;x.fillText('כרטיס מפגש · '+dayLbl(),W-60,H-330);
    x.fillStyle='#13233A';x.fillRect(W-420,40,360,70);x.fillStyle='#fff';x.font=`700 34px ${BODY}`;x.fillText('גודאורי 2027',W-90,88);
    return new Promise(r=>cv.toBlob(r,'image/png'));
  }
  document.getElementById('meetShare').addEventListener('click',async e=>{const b=e.currentTarget,s=byId[S.sid];
    try{const blob=await image(),file=new File([blob],'meet.png',{type:'image/png'});
      if(navigator.canShare&&navigator.canShare({files:[file]})){await navigator.share({files:[file],text:message(),title:'נקודת מפגש: '+s.name});return;}
      if(navigator.share){await navigator.share({text:message(),title:'נקודת מפגש: '+s.name});return;}
      const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='meet-'+s.name.replace(/\W+/g,'-')+'.png';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),4000);
    }catch(err){}});
  document.getElementById('meetCopy').addEventListener('click',e=>{const b=e.currentTarget;if(!navigator.clipboard)return;navigator.clipboard.writeText(link()).then(()=>{b.textContent='הקישור הועתק';setTimeout(()=>{b.textContent='העתקת הקישור';},2200);}).catch(()=>{});});
  document.getElementById('meetOnMap').addEventListener('click',()=>{const s=byId[S.sid];location.hash='#map';requestAnimationFrame(()=>requestAnimationFrame(()=>{showLift(s.ends[0].l.id);if(view==='3d'&&v3)v3.focusLift(s.ends[0].l.id);else focusOn([s.ends[0].l.g]);}));});
  let shown=false;
  function open(arg){ // arg: "<station>/<HHMM>/<YYYYMMDD>" from a shared link, or empty
    if(arg){const [sid,t,d]=arg.split('/');if(byId[sid])S.sid=sid;if(/^\d{4}$/.test(t||''))S.time=t.slice(0,2)+':'+t.slice(2);if(/^\d{8}$/.test(d||''))S.day=`${d.slice(0,4)}-${d.slice(4,6)}-${d.slice(6)}`;
      ui.querySelector('#meetTime').value=S.time;}
    const s=byId[S.sid];requestAnimationFrame(()=>{if(!shown||arg){shown=true;goTo(s.x,s.y,1800,0);}render();if(arg)document.getElementById('meetCard').scrollIntoView({block:'center'});});
  }
  ui.querySelector('#meetTime').value=S.time;
  return {open};
})();

// ---- lift status (design round 3, S1 to S3). Source: /api/status, a Vercel function that reads the MTA status page
// (to be written when the page works again, in December). Format:
// {updated:"ISO time", lifts:{"<lift name>":{open:true|false, reason?:"wind"}}, pistes:{"<run key>":{open:true|false}}}
// No data, or data older than 30 minutes: "no current information", and the map stays as it is. We never guess.
const LSTAT=(function(){
  let data=null,forMe=false;const STALE=30*6e4;
  const names=mainLifts.filter(l=>l.name&&l.status!=='inactive').map(l=>l.name);
  const fresh=()=>data&&data.updated&&Date.now()-Date.parse(data.updated)<STALE;
  const isOpen=n=>fresh()&&data.lifts&&data.lifts[n]?!!data.lifts[n].open:null;
  const runOpen=p=>{if(!fresh())return null;const r=data.pistes&&data.pistes[p.key];if(r&&!r.open)return false;
    const up=p.fromLifts.filter(n=>data.lifts&&data.lifts[n]);return up.length?up.some(n=>data.lifts[n].open):(r?!!r.open:null);}; // open for me: the run and a lift up to it
  const REASON={wind:'רוח',weather:'מזג אוויר',maintenance:'תחזוקה',season:'מחוץ לעונה'};
  const ago=t=>{const m=Math.round((Date.now()-Date.parse(t))/6e4);return m<1?'עכשיו':m<60?`לפני ${m} דק׳`:`לפני ${Math.round(m/60)} שע׳`;};
  // snow on the signs: soft mounds and a few rounded drips (seeded, so every sign keeps its own pile)
  function snow(i,w){let r=(i+1)*9301%233280;const rnd=(a,b)=>{r=(r*9301+49297)%233280;return a+(b-a)*r/233280;};
    const top=26,x0=16,x1=w+6,up=[[x0-4,top+6],[x0+4,top-2]];let x=x0+14,pk=true;
    while(x<x1-20){const mid=1-Math.abs((x-x0)/(x1-x0)-.5)*1.1;up.push(pk?[x,top-8-mid*rnd(10,20)]:[x,top-rnd(1,6)]);x+=pk?rnd(30,48):rnd(22,34);pk=!pk;}
    up.push([x1-6,top-3],[x1+2,top+5]);const lo=[];x=x1-2;while(x>x0+8){lo.push([x,top+rnd(8,13)]);x-=rnd(26,44);}lo.push([x0+2,top+10]);
    const pts=[...up,...lo,up[0]];let d=`M${pts[0][0].toFixed(1)},${pts[0][1].toFixed(1)}`;
    for(let k=0;k<pts.length-1;k++){const p0=pts[Math.max(k-1,0)],p1=pts[k],p2=pts[k+1],p3=pts[Math.min(k+2,pts.length-1)];
      d+=` C${(p1[0]+(p2[0]-p0[0])/6).toFixed(1)},${(p1[1]+(p2[1]-p0[1])/6).toFixed(1)} ${(p2[0]-(p3[0]-p1[0])/6).toFixed(1)},${(p2[1]-(p3[1]-p1[1])/6).toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`;}
    let drips='';for(let k=0;k<2;k++){const dx=rnd(x0+40,x1-40),dl=rnd(7,13),dw=rnd(4,6),y0=top+8;drips+=`<path d="M${dx-dw},${y0}C${dx-dw},${y0+dl*.6} ${dx-dw/2},${y0+dl} ${dx},${y0+dl}C${dx+dw/2},${y0+dl} ${dx+dw},${y0+dl*.6} ${dx+dw},${y0}Z"/>`;}
    return `<svg class="snowcap" viewBox="0 0 ${w+16} 56" preserveAspectRatio="none" aria-hidden="true"><g class="sc-sh"><path d="${d}Z"/>${drips}</g><g class="sc"><path d="${d}Z"/>${drips}</g></svg>`;}
  function block(){
    if(!fresh()){const season=[11,0,1,2,3].includes(new Date().getMonth());
      return `<section class="lstat" aria-label="מצב הרכבלים"><h3>${season?'אין מידע עדכני':'ההר עוד ישן'}</h3>
      <p class="lead">${season?'כרגע אין דיווח עדכני מ-MTA על הרכבלים. המפה מוצגת כרגיל.':'עוד אין דיווח על רכבלים. העונה בגודאורי נפתחת בדרך כלל בדצמבר, ואז השלטים יתנקו מהשלג.'}</p>
      <div class="lstat-post">${names.slice(0,6).map((n,i)=>`<div class="lsign s${i%2}"><div class="ls-face"><b dir="ltr">${esc(n)}</b><span>?</span></div>${snow(i,300)}</div>`).join('')}</div>
      <p class="hint"><b>לא מנחשים מצב:</b> כשאין דיווח עדכני, כתוב כאן שאין.</p></section>`;}
    const open=names.filter(n=>isOpen(n)).length;
    let prev=null;try{prev=JSON.parse(localStorage.getItem('gud-lstat')||'null');}catch(e){}
    const changes=prev?names.filter(n=>prev[n]!==undefined&&prev[n]!==isOpen(n)).map(n=>`${n} ${isOpen(n)?'נפתח':'נסגר'} מאז שבדקת`):[];
    try{localStorage.setItem('gud-lstat',JSON.stringify(Object.fromEntries(names.map(n=>[n,isOpen(n)]))));}catch(e){}
    return `<section class="lstat" aria-label="מצב הרכבלים"><h3>מצב הרכבלים</h3>
      ${changes.length?`<ul class="lstat-changes">${changes.map(c=>`<li>${esc(c)}</li>`).join('')}</ul>`:''}
      <div class="board-dep" role="table" aria-label="רכבלים"><div class="bd-row bd-head" role="row"><span role="columnheader">רכבל</span><span role="columnheader">מצב</span><span role="columnheader">הערה</span></div>
      ${names.map((n,i)=>{const o=isOpen(n),r=data.lifts[n]&&data.lifts[n].reason;return `<div class="bd-row" role="row" style="--i:${i}"><span role="cell" dir="ltr">${esc(n)}</span><span role="cell" class="${o?'bd-open':o===false?'bd-closed':''}">${o?'פתוח':o===false?'סגור':'—'}</span><span role="cell">${r?esc(REASON[r]||r):''}</span></div>`;}).join('')}</div>
      <button type="button" class="fchip lstat-me" data-forme aria-pressed="${forMe}">רק מה שפתוח בשבילי</button>
      <p class="hint">מקור: דף הסטטוס של MTA. מסלול מסומן פתוח רק אם גם רכבל שמגיע לראשו פתוח. ${open} מתוך ${names.length} רכבלים פתוחים.</p></section>`;
  }
  // the map: closed lifts grey and dashed, chairs moving on open ones, and "only what's open for me"
  const gChairs=mk('g',{class:'chairs','aria-hidden':'true'});svg.insertBefore(gChairs,mainLbl);
  function applyMap(){
    const bar=document.getElementById('mstat');
    gChairs.innerHTML='';svg.classList.toggle('forme',forMe&&fresh());
    svg.querySelectorAll('g.lg[data-lid]').forEach(g=>g.classList.remove('closed'));
    if(!fresh()){bar.innerHTML=`<span class="ms-dot"></span>${data?'אין מידע עדכני על הרכבלים':'עוד אין מידע על הרכבלים'}`;bar.dataset.state='none';
      D.pistes.forEach(p=>(pisteEls[p.key]||[]).forEach(e=>e.classList.remove('shut')));if(v3&&v3.liftState)v3.liftState(null);return;}
    const open=names.filter(n=>isOpen(n)).length;
    bar.dataset.state='live';bar.innerHTML=`<span class="ms-dot"></span><b class="num">${open}</b> מתוך <b class="num">${names.length}</b> רכבלים פתוחים · עודכן ${ago(data.updated)}`;
    mainLifts.forEach(l=>{const g=svg.querySelector(`g.lg[data-lid="${l.id}"]`);const o=l.name?isOpen(l.name):null;if(g)g.classList.toggle('closed',o===false);
      if(o&&!reduceMotion()){const d=pathD(l.g),len=l.len||1000,dur=Math.max(8,len/60);
        for(let k=0;k<3;k++){const c=mk('circle',{r:12,class:'chair'},gChairs);const am=mk('animateMotion',{dur:dur+'s',begin:`-${(dur*k/3).toFixed(1)}s`,repeatCount:'indefinite',path:d},c);}}});
    D.pistes.forEach(p=>{const o=runOpen(p);(pisteEls[p.key]||[]).forEach(e=>e.classList.toggle('shut',o===false));});
    if(v3&&v3.liftState)v3.liftState(Object.fromEntries(mainLifts.filter(l=>l.name).map(l=>[l.id,isOpen(l.name)])));
    if(mapReady)apply();
  }
  panel.addEventListener('click',e=>{const b=e.target.closest('[data-forme]');if(!b)return;forMe=!forMe;b.setAttribute('aria-pressed',String(forMe));applyMap();});
  function load(){return fetch('api/status',{cache:'no-store'}).then(r=>r.ok&&/json/.test(r.headers.get('content-type')||'')?r.json():null).catch(()=>null)
    .then(j=>{data=j&&j.updated&&j.lifts?j:null;applyMap();if(!current&&!panel.querySelector('.back'))overview();});}
  setInterval(load,5*6e4);
  return {block,load,applyMap};
})();
LSTAT.load();

// flight ticket: one shared doc (trip/flight), editable by Contributors
const fmtDate=iso=>{const[y,m,d]=iso.split('-');return +d+'.'+ +m+'.'+y;};
function renderTicket(){
  const o=TRIP&&TRIP.outbound||{},r=TRIP&&TRIP.return,members=TRIP&&TRIP.members||[];
  const short=iso=>{const[,m,d]=iso.split('-');return +d+'.'+ +m;};
  const fill=(card,f)=>{
    card.querySelectorAll('[data-f]').forEach(el=>{
      const k=el.dataset.f,v={airline:TRIP&&TRIP.airline,dateShort:f.date&&short(f.date),date:f.date&&fmtDate(f.date),pax:members.length?members.length+' החבר׳ה':'',
        baggage:TRIP&&TRIP.baggage,pair:f.fromCode&&f.toCode?f.fromCode+' › '+f.toCode:'',note:f.departs&&+f.departs.split(':')[0]<6?' · בלילה':'',skiCount:skiCount,skiRange:sd?short(sd.from).split('.')[0]+'–'+short(sd.to):''}[k]??f[k];
      if(k==='note'){el.textContent=v||'';return;}
      el.textContent=v||(k==='from'?'מוצא':k==='to'?'יעד':'—');
      if(k==='from'||k==='to')el.classList.toggle('ph',!v);
    });
  };
  const sd=TRIP&&TRIP.skiDays,skiCount=sd?String(Math.round((new Date(sd.to)-new Date(sd.from))/864e5)+1):'';
  fill(document.querySelector('.bp[data-leg="out"]'),o);
  const rc=document.querySelector('.bp[data-leg="ret"]');
  if(r){fill(rc,r);rc.hidden=false;document.getElementById('bpHintSwap').hidden=false;}
  document.querySelectorAll('.bp-ridge').forEach(svg=>{
    /* the "barcode" is the ridge above New Gudauri drawn as bars: it is the mountain, not a code anyone could scan */
    const h=[.30,.42,.55,.48,.62,.80,.70,.58,.66,.92,1,.86,.74,.60,.68,.78,.64,.50,.44,.56,.70,.62,.48,.36,.42,.30,.24,.34,.28,.20,.26,.18],w=80/h.length;
    svg.innerHTML=h.map((v,i)=>`<rect x="${(i*w).toFixed(1)}" y="${(28-v*28).toFixed(1)}" width="${i%3===0?2:i%2?1.2:.7}" height="${(v*28).toFixed(1)}"/>`).join('');
  });
  document.getElementById('crewCount').textContent=members.length?'· '+members.length:'';
  document.getElementById('crewList').innerHTML=members.map(n=>`<li>${esc(n)}</li>`).join('');
  document.querySelector('.crew').hidden=!members.length;
}
// the passes: tap the one behind to bring it forward, tap the stub to tear it off (it comes back)
(function(){
  const stack=document.getElementById('bpStack');let busy=false,ac=null;const snd={};
  const buzz=p=>{try{navigator.vibrate&&navigator.vibrate(p);}catch(e){}};
  const ctx=()=>{try{if(!ac){const A=window.AudioContext||window.webkitAudioContext;if(!A)return null;ac=new A();}if(ac.state==='suspended')ac.resume();return ac;}catch(e){return null;}};
  /* recordings (credits at the bottom of the home page): fetched and decoded after the page is up, so the first tap already has sound */
  try{const O=window.OfflineAudioContext||window.webkitOfflineAudioContext;if(O){const oc=new O(1,1,44100);
    ['tear','slide','land'].forEach(k=>fetch('audio/ticket-'+k+'.wav').then(r=>r.ok?r.arrayBuffer():Promise.reject()).then(b=>new Promise((ok,no)=>oc.decodeAudioData(b,ok,no))).then(x=>{snd[k]=x;}).catch(()=>{}));}}catch(e){}
  const play=(k,at,g,lp)=>{const c=ctx();if(!c)return;const b=snd[k];if(!b)return;
    const s=c.createBufferSource(),f=c.createBiquadFilter(),v=c.createGain();s.buffer=b;f.type='lowpass';f.frequency.value=lp;f.Q.value=.5;v.gain.value=g;
    s.connect(f);f.connect(v);v.connect(c.destination);s.start(c.currentTime+at);};
  const front=()=>stack.querySelector('.bp.is-front');
  stack.addEventListener('click',e=>{
    const swap=e.target.closest('.bp-swap'),stub=e.target.closest('.bp-stub');
    if(busy)return;
    if(swap&&!document.querySelector('.bp[data-leg="ret"]').hidden){
      busy=true;play('slide',0,.7,6000);play('land',.42,.75,5000);buzz(8);
      stack.classList.add('shuffle');
      setTimeout(()=>{stack.querySelectorAll('.bp').forEach(c=>{const f=c.classList.contains('is-front');c.classList.toggle('is-front',!f);c.classList.toggle('is-back',f);});stack.classList.remove('shuffle');},250);
      setTimeout(()=>buzz(12),460);
      setTimeout(()=>{busy=false;},640);
    }else if(stub&&stub.closest('.bp')===front()){
      busy=true;play('tear',0,.85,7500);buzz([6,30,6,30,6,30,6,30,6,30,6,90,24]);
      stub.classList.add('torn1');
      setTimeout(()=>{stub.classList.add('torn2');},540);
      setTimeout(()=>{stub.classList.remove('torn1','torn2');busy=false;},2600);
    }
  });
})();
renderTicket();
route();
overview();applyFilters();
// קישור להורדת האפליקציה: מופיע רק אם הקובץ באמת קיים בשרת
fetch('downloads/gudauri-2027.apk',{method:'HEAD'}).then(r=>{
  if(!r.ok||/text\/html/.test(r.headers.get('content-type')||''))return;
  const mb=+r.headers.get('content-length')/1048576;
  document.getElementById('appMeta').textContent='להורדה'+(mb>0?' · '+mb.toFixed(mb<10?1:0)+' MB':'');
  document.getElementById('appBoard').hidden=false;document.getElementById('appHow').hidden=false;document.getElementById('boardNext').hidden=true;
}).catch(()=>{});
document.getElementById('loading').hidden=true;
})().catch(e=>{console.error(e);const l=document.getElementById('loading');l.hidden=false;l.textContent='שגיאה בטעינת האתר. נסו לרענן את הדף.';});

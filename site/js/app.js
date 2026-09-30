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
    const g=mk('g',{class:'lg'},gL);const d=pathD(l.g);
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
  layoutLabels(labels,stations,u,marks);svg.classList.toggle('far',u>9);
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
function clearSel(){if(v3)v3.select(null);[svg,kSvg].forEach(m=>{m.classList.remove('has-sel');m.querySelectorAll('.pg.on').forEach(e=>e.classList.remove('on'));});}
function focusOn(els){let a=1e9,b=1e9,c=-1e9,d=-1e9;els.forEach(g=>g.forEach(q=>{const[x,y]=P(q);a=Math.min(a,x);c=Math.max(c,x);b=Math.min(b,y);d=Math.max(d,y);}));
  const[cw,ch]=sz();const w=Math.max(c-a,(d-b)*cw/ch,900)*1.5;vb.w=w;vb.h=w*ch/cw;vb.x=(a+c)/2-w/2;vb.y=(b+d)/2-vb.h/2;apply();}
function select(key,{zoom=false}={}){
  const p=byKey[key];clearSel();
  if(p){[svg,kSvg].forEach(m=>m.classList.add('has-sel'));(pisteEls[key]||[]).forEach(e=>e.classList.add('on'));if(v3)v3.select(key);
    if(zoom){if(view==='3d')v3.focus(key);else if(isKobiP(p))openInset();else focusOn(p.segs.map(s=>s.g));}}
  current=key;renderPiste(key);
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
function renderPiste(key){
  const p=byKey[key];
  if(!p){const m=D.missing.find(x=>x.name===key);if(!m)return overview();
    panel.innerHTML=`<button class="back" data-back>→ כל המסלולים</button><h2 class="c-${m.color}">${esc(m.name)}</h2>
    <dl class="kv"><dt>צבע רשמי</dt><dd>${pips(m.color)}${HEB[m.color]} · ${RATE[m.color][1]}</dd><dt>אורך</dt><dd>—</dd></dl>
    <h3>הערות</h3><div class="notice">מסלול זה מופיע במפה הרשמית אבל אין לו קו בנתוני OpenStreetMap. לא ציירנו אותו כדי לא לנחש את התוואי.</div>${vidBlock(key,m.name)}`;
    return;}
  const c=p.color,label=p.named?(p.key==='Firni ?'?'Firni':p.key):'';
  panel.innerHTML=`<button class="back" data-back>→ כל המסלולים</button>
  <h2 class="c-${c}">${esc(dispName(p))}</h2>
  <dl class="kv">
    <dt>אורך</dt><dd class="num">${fmtLen(p.len)}</dd>
    ${elevRows(p)}
    <dt>צבע</dt><dd><span class="sw ${c}"></span> ${HEB[c]}${p.named?' (מפה רשמית)':' (לפי OSM)'}</dd>
    <dt>קושי</dt><dd>${pips(c)}${RATE[c][1]}</dd>
    ${p.osmDiff.length?`<dt>דירוג OSM</dt><dd>${p.osmDiff.map(x=>OSMD[x]?OSMD[x][0]:'ללא').join(' / ')}</dd>`:''}
    ${p.refs.length?`<dt>סימון</dt><dd class="num">${esc(p.refs.join(', '))}</dd>`:''}
    ${p.groom.length?`<dt>הכשרה</dt><dd>${p.groom.includes('classic')?'מוכשר (ratrak)':esc(p.groom.join(', '))}</dd>`:''}
  </dl>
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
  const l=D.lifts.find(x=>x.id===id);if(!l)return;clearSel();current=null;
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
panel.addEventListener('click',e=>{
  const b=e.target.closest('button');if(!b)return;
  if(b.dataset.back!==undefined){overview();return;}
  if(b.dataset.filter){const k=b.dataset.filter;hidden.has(k)?hidden.delete(k):hidden.add(k);b.setAttribute('aria-pressed',!hidden.has(k));applyFilters();return;}
  if(b.dataset.vid){const w=document.createElement('div');w.className='vframe';const f=document.createElement('iframe');
    f.src='https://www.youtube-nocookie.com/embed/'+b.dataset.vid+'?autoplay=1&rel=0';f.title=b.dataset.title||'סרטון';f.allow='autoplay; encrypted-media; picture-in-picture; fullscreen';f.allowFullscreen=true;f.referrerPolicy='strict-origin-when-cross-origin';
    w.appendChild(f);b.replaceWith(w);return;}
  if(b.dataset.goto){select(b.dataset.goto,{zoom:true});panel.scrollTop=0;return;}
  if(b.dataset.lift){const l=D.lifts.find(x=>x.id===+b.dataset.lift);showLift(+b.dataset.lift);if(l){if(view==='3d')v3.focusLift(l.id);else if(isKobiL(l))openInset();else focusOn([l.g]);}return;}
});

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
      onHeading:az=>{compassSvg.style.transform=`rotate(${(az*180/Math.PI).toFixed(1)}deg)`;}});
    v3.kobi=()=>v3.view({tx:kc[0],tz:kc[1],dist:6800,az:Math.PI*0.9,pol:0.6});
    v3.setTheme(isDark());matchMedia('(prefers-color-scheme: dark)').addEventListener('change',()=>v3.setTheme(isDark()));
    v3.filter(hidden);if(current&&byKey[current])v3.select(current);
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
const pgHome=document.getElementById('home'),pgMap=document.getElementById('mapPage');
function route(){
  const m=location.hash==='#map';pgHome.hidden=m;pgMap.hidden=!m;
  document.querySelectorAll('[data-nav]').forEach(a=>{if((a.dataset.nav==='map')===m)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});
  requestAnimationFrame(m?activateMap:DN.layout);
  window.scrollTo(0,0);pgHome.scrollTop=0;
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
    return {a,b,f,dark,now,h,phase,morning:h<t.noon};
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
    if(typeof v3!=='undefined'&&v3)v3.setTheme(s.dark);
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

// flight ticket: one shared doc (trip/flight), editable by Contributors
const fmtDate=iso=>{const[y,m,d]=iso.split('-');return +d+'.'+ +m+'.'+y;};
function renderTicket(){
  const put=(id,v,ph)=>{const el=document.getElementById(id);el.textContent=v||ph;el.classList.toggle('ph',!v);};
  const o=TRIP&&TRIP.outbound||{};
  put('tFrom',o.from,'מוצא');put('tTo',o.to,'יעד');put('tFlight',o.flight,'—');
  put('tDate',o.date&&fmtDate(o.date),'—');put('tDeparts',o.departs,'—');put('tArrives',o.arrives,'—');
  const r=TRIP&&TRIP.return,back=document.getElementById('tBack');
  if(r){back.innerHTML=`חזרה: <b class="num">${esc(fmtDate(r.date))}</b>, טיסה <b dir="ltr">${esc(r.flight)}</b>, המראה <b class="num">${esc(r.departs)}</b>${r.note?`<span class="t-note">${esc(r.note)}</span>`:''}`;back.hidden=false;}
  const members=TRIP&&TRIP.members||[];
  document.getElementById('crewCount').textContent=members.length?'· '+members.length:'';
  document.getElementById('crewList').innerHTML=members.map(n=>`<li>${esc(n)}</li>`).join('');
  document.querySelector('.crew').hidden=!members.length;
}
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

(async function main(){
const [D,TERR]=await Promise.all([
  fetch('data/runs-and-lifts.json').then(r=>{if(!r.ok)throw new Error('runs-and-lifts '+r.status);return r.json();}),
  fetch('data/terrain.json').then(r=>r.ok?r.json():null).catch(()=>null)
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
(function(){const t=new Date('2027-01-10T00:00:00+02:00');const d=Math.ceil((t-new Date())/864e5);
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
function vidBlock(key,label){
  return `<h3>סרטונים</h3><div id="vidlist"></div>
  <p class="hint"><a href="https://www.youtube.com/results?search_query=${encodeURIComponent('Gudauri '+label+' ski')}" target="_blank" rel="noopener">חיפוש "Gudauri ${esc(label)}" ביוטיוב ↗</a></p>
  <form class="add" id="addform" data-key="${esc(key)}" ${canWrite===false?'hidden':''}>
    <input type="url" id="v-url" required placeholder="https://youtube.com/…" aria-label="קישור לסרטון">
    <input type="text" id="v-title" maxlength="80" placeholder="תיאור קצר (לא חובה)" aria-label="תיאור">
    <input type="text" id="v-by" maxlength="30" placeholder="השם שלך" aria-label="השם שלך">
    <button class="btn" type="submit">הוספת סרטון</button><span class="err" id="v-err" role="alert"></span>
  </form>
  <p class="hint" id="v-ro" ${canWrite===false?'':'hidden'}>כדי להוסיף סרטונים צריך הרשאת Contributor לדף. בקשו מפיני.</p>`;
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
    bindForm();renderVids();return;}
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
  bindForm();renderVids();
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
  if(b.dataset.goto){select(b.dataset.goto,{zoom:true});panel.scrollTop=0;return;}
  if(b.dataset.lift){const l=D.lifts.find(x=>x.id===+b.dataset.lift);showLift(+b.dataset.lift);if(l){if(view==='3d')v3.focusLift(l.id);else if(isKobiL(l))openInset();else focusOn([l.g]);}return;}
  if(b.dataset.del){delVid(b.dataset.del);}
});

// shared video links (db)
let db=null,userCap=null,canWrite=null,canDel=false,vids=[];
function renderVids(){
  const box=document.getElementById('vidlist');if(!box||!current&&!document.getElementById('addform'))return;
  const key=document.getElementById('addform')?.dataset.key;if(!key){return;}
  const list=vids.filter(v=>v.piste===key).sort((a,b)=>(b.at||0)-(a.at||0));
  box.innerHTML=db===null?'<p class="hint">טוען סרטונים…</p>':list.length?`<ul class="vids">${list.map(v=>`<li>${canDel?`<button class="del" data-del="${esc(v.id)}" aria-label="מחיקה">✕</button>`:''}<a href="${esc(v.url)}" target="_blank" rel="noopener">${esc(v.title||hostOf(v.url))}</a><small>${esc(v.by||'')} ${v.at?'· '+new Date(v.at).toLocaleDateString('he-IL'):''}</small></li>`).join('')}</ul>`:'<p class="hint">עוד אין סרטונים למסלול הזה. הוסיפו את הראשון.</p>';
}
function hostOf(u){try{return new URL(u).hostname.replace(/^www\./,'')}catch{return u}}
function bindForm(){
  const f=document.getElementById('addform');if(!f)return;
  try{const n=localStorage.getItem('gud-by');if(n)f.querySelector('#v-by').value=n;}catch{}
  f.addEventListener('submit',async e=>{
    e.preventDefault();const err=f.querySelector('#v-err');err.textContent='';
    const url=f.querySelector('#v-url').value.trim(),title=f.querySelector('#v-title').value.trim(),by=f.querySelector('#v-by').value.trim();
    let ok=false;try{const u=new URL(url);ok=u.protocol==='https:'||u.protocol==='http:';}catch{}
    if(!ok){err.textContent='הקישור צריך להתחיל ב-https://';return;}
    if(!db){err.textContent='שמירת סרטונים לא זמינה בתצוגה הזו.';return;}
    try{localStorage.setItem('gud-by',by);}catch{}
    const btn=f.querySelector('button');btn.disabled=true;
    try{await db.collection('videos').add({piste:f.dataset.key,url,title,by,at:Date.now()});f.querySelector('#v-url').value='';f.querySelector('#v-title').value='';}
    catch(x){err.textContent=x&&x.code==='invalid_argument'?'אין לך הרשאה להוסיף. בקשו מפיני הרשאת Contributor.':x&&x.code==='quota_exceeded'?'המאגר מלא.':'השמירה נכשלה. נסו שוב.';}
    btn.disabled=false;
  });
}
async function delVid(id){if(!db)return;try{await db.doc('videos/'+id).delete();}catch{}}
async function initDb(){
  if(!window.claude||!claude.use){db=false;renderVids();return;}
  const [d,u]=await Promise.all([claude.use('db'),claude.use('user')]);
  userCap=u;
  if(u){try{canWrite=await u.can('data.write');}catch{} try{canDel=!!(await u.canEdit());}catch{}}
  if(!d){db=false;canWrite=false;if(current)renderPiste(current);return;}
  db=d;
  watchTrip();
  db.collection('videos').onSnapshot(s=>{vids=s.docs.map(x=>({id:x.id,...x.data()}));renderVids();},()=>{});
  if(current)renderPiste(current);
}
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
  requestAnimationFrame(m?activateMap:drawRidge);
  window.scrollTo(0,0);pgHome.scrollTop=0;
}
addEventListener('hashchange',route);

// home: the skyline as seen from Gudauri village, traced from the elevation model
const SKY=(function(){
  if(!TM)return null;
  const pl=TM.env.places.find(p=>p.n==='Gudauri');
  const ex=pl?pl.x:(minX+maxX)/2,ey=pl?pl.y:maxY,eh=TM.elev(ex,ey)+30,dm=TM.dem;
  const tx=(minX+maxX)/2,ty=minY+(maxY-minY)*0.2;
  const az0=Math.atan2(tx-ex,-(ty-ey)),FOV=1.9,N=260,far=[],mid=[],near=[];
  for(let i=0;i<=N;i++){const az=az0-FOV/2+FOV*i/N,dx=Math.sin(az),dy=-Math.cos(az);let a=-1,b=-1,c=-1;
    for(let dd=80;dd<16000;dd+=dd<2500?30:70){const x=ex+dx*dd,y=ey+dy*dd;if(x<dm.x0||x>dm.x1||y<dm.y0||y>dm.y1)break;
      const t=(TM.elev(x,y)-eh)/dd;if(t>a)a=t;if(dd<3500&&t>b)b=t;if(dd<800&&t>c)c=t;}
    far.push(Math.atan(a));mid.push(Math.atan(b));near.push(Math.atan(c));}
  const pk=TM.peaks.filter(p=>!p.pass).map(p=>{let r=Math.atan2(p.x-ex,-(p.y-ey))-az0;r=Math.atan2(Math.sin(r),Math.cos(r));
    const dd=Math.hypot(p.x-ex,p.y-ey);return {p,u:(r+FOV/2)/FOV,ang:Math.atan((TM.elev(p.x,p.y)-eh)/dd)};}).filter(o=>o.u>0&&o.u<1);
  return {far,mid,near,N,pk};
})();
function drawRidge(){
  const host=document.getElementById('homeSky'),rs=document.getElementById('ridgeSvg');
  const W=host.clientWidth,H=host.clientHeight;if(!W||!H)return;
  rs.setAttribute('viewBox',`0 0 ${W} ${H}`);
  if(!SKY){rs.innerHTML=`<rect width="${W}" height="${H}" class="rsky"/>`;return;}
  const i0=W<600?Math.round(SKY.N*0.14):0,i1=W<600?Math.round(SKY.N*0.86):SKY.N,n=i1-i0;
  let lo=1e9,hi=-1e9;for(let i=i0;i<=i1;i++){hi=Math.max(hi,SKY.far[i]);lo=Math.min(lo,SKY.mid[i]);}
  const top=H*0.3,bot=H*0.97,Y=a=>bot-(a-lo)/(hi-lo||1)*(bot-top),X=i=>(i-i0)/n*W;
  const poly=arr=>{let t='';for(let i=i0;i<=i1;i++)t+=`${X(i).toFixed(1)},${Y(arr[i]).toFixed(1)} `;return t+`${W},${H} 0,${H}`;};
  let lbl='';const kept=[];
  SKY.pk.slice().sort((a,b)=>b.p.ele-a.p.ele).forEach(o=>{const fi=o.u*SKY.N;if(fi<i0||fi>i1)return;const i=Math.round(fi);
    if(o.ang<SKY.far[i]-0.012)return;const x=X(fi),y=Y(SKY.far[i]);if(x<44||x>W-44||kept.some(k=>Math.abs(k-x)<96))return;kept.push(x);
    lbl+=`<path class="rk" d="M${x.toFixed(1)} ${(y-5).toFixed(1)}v-9"/><text class="rl" x="${x.toFixed(1)}" y="${(y-19).toFixed(1)}" text-anchor="middle" direction="ltr">${esc(o.p.n)} <tspan class="re">${o.p.ele}</tspan></text>`;});
  rs.innerHTML=`<rect width="${W}" height="${H}" class="rsky"/><polygon class="rfar" points="${poly(SKY.far)}"/><polygon class="rmid" points="${poly(SKY.mid)}"/><polygon class="rnear" points="${poly(SKY.near)}"/>${lbl}`;
}
new ResizeObserver(()=>{if(!pgHome.hidden)drawRidge();}).observe(document.getElementById('homeSky'));

// flight ticket: one shared doc (trip/flight), editable by Contributors
let trip={};
const tFrom=document.getElementById('tFrom'),tTo=document.getElementById('tTo'),tFlight=document.getElementById('tFlight');
const tEdit=document.getElementById('tEdit'),tForm=document.getElementById('tForm'),tErr=document.getElementById('tErr');
function renderTicket(){
  const put=(el,v,ph)=>{el.textContent=v||ph;el.classList.toggle('ph',!v);};
  put(tFrom,trip.from,'מוצא');put(tTo,trip.to,'יעד');put(tFlight,trip.flight,'—');
}
function watchTrip(){
  db.doc('trip/flight').onSnapshot(sn=>{trip=sn.exists?(sn.data()||{}):{};renderTicket();},()=>{});
  tEdit.hidden=canWrite===false||!tForm.hidden;
}
tEdit.addEventListener('click',()=>{tErr.textContent='';['from','to','flight'].forEach(k=>tForm.elements[k].value=trip[k]||'');tForm.hidden=false;tEdit.hidden=true;tForm.elements.from.focus();});
document.getElementById('tCancel').addEventListener('click',()=>{tForm.hidden=true;tEdit.hidden=false;tErr.textContent='';tEdit.focus();});
tForm.addEventListener('submit',async e=>{
  e.preventDefault();tErr.textContent='';
  const v=k=>tForm.elements[k].value.trim(),next={from:v('from'),to:v('to'),flight:v('flight')};
  if(['from','to','flight'].every(k=>(trip[k]||'')===next[k])){tForm.hidden=true;tEdit.hidden=false;return;}
  if(!db){tErr.textContent='שמירה לא זמינה בתצוגה הזו.';return;}
  const btn=tForm.querySelector('button[type=submit]');btn.disabled=true;
  try{await db.doc('trip/flight').set({...next,at:Date.now()});tForm.hidden=true;tEdit.hidden=false;tEdit.focus();}
  catch(x){if(x&&x.code==='invalid_argument'){canWrite=false;tErr.textContent='אין לך הרשאה לערוך. בקשו מפיני הרשאת Contributor.';}else tErr.textContent='השמירה נכשלה. נסו שוב.';}
  btn.disabled=false;
});
renderTicket();
route();
overview();applyFilters();
document.getElementById('loading').hidden=true;
setTimeout(initDb,0);
})().catch(e=>{console.error(e);const l=document.getElementById('loading');l.hidden=false;l.textContent='שגיאה בטעינת האתר. נסו לרענן את הדף.';});

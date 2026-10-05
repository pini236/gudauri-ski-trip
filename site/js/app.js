(async function main(){
await I18N.ready; // the words of the chosen language (js/i18n.js); everything below draws with T()
const soft=(url,fallback)=>fetch(url).then(r=>r.ok?r.json():fallback).catch(()=>fallback);
const [D,TERR,vids]=await Promise.all([
  fetch('data/runs-and-lifts.json').then(r=>{if(!r.ok)throw new Error('runs-and-lifts '+r.status);return r.json();}),
  soft('data/terrain.json',null),
  soft('data/videos-seed.json',[])
]);
const HEB=Object.fromEntries(['green','blue','red','black'].map(c=>[c,T('common.color_'+c)]));
const RATE={green:[1,T('map.difficulty_beginner')],blue:[2,T('map.difficulty_easy')],red:[3,T('map.difficulty_intermediate')],black:[4,T('map.difficulty_hard')]};
const OSMD={novice:[T('run.osm_grade_novice'),'green'],easy:[T('run.osm_grade_easy'),'blue'],intermediate:[T('run.osm_grade_intermediate'),'red'],advanced:[T('run.osm_grade_advanced'),'black'],expert:[T('run.osm_grade_expert'),'black']};
const LK=Object.fromEntries(['chair_lift','gondola','platter','magic_carpet','drag_lift','t_bar'].map(k=>[k,T('lift.kind_'+k)]));
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
// words from the strings file: E() escaped text; H() escaped text with markup slots (raw) in its {placeholders}.
// raw.n is special: {n} also picks the plural form, so the number is put in first and then wrapped.
const E=(k,v)=>esc(T(k,v));
const H=(key,vars={},raw={})=>{const v={...vars},m=[];for(const k in raw)if(k!=='n'){v[k]='\uE000'+m.length+'\uE001';m.push(raw[k]);}
  let out=esc(T(key,v)).replace(/\uE000(\d+)\uE001/g,(_,i)=>m[i]);if('n' in raw)out=out.replace(esc(String(vars.n)),raw.n);return out;};
const num=x=>`<span class="num">${x}</span>`;
// a counter drawn in three places (small text, big number, small text): the text before and after {n}
const slots=(key,vars)=>{const s=T(key,vars),v=String(vars.n!=null?vars.n:vars.hm),i=s.indexOf(v);return i<0?[s,'','']:[s.slice(0,i).trim(),v,s.slice(i+v.length).trim()];};
const fmtLen=m=>m>=1000?T('common.unit_km',{n:(m/1000).toFixed(2)}):T('common.unit_m',{n:m});
// static words that need a number or a link inside them; the Hebrew is already in the markup
if(I18N.lang!=='he'){
  document.querySelectorAll('[data-i18n-plural]').forEach(el=>{el.textContent=T(el.dataset.i18nPlural,{n:+el.dataset.n});});
  document.querySelectorAll('[data-i18n-tpl]').forEach(el=>{const raw={},vars={};el.querySelectorAll('[data-slot]').forEach(x=>{raw[x.dataset.slot]=x.outerHTML;});
    for(const a of el.attributes)if(a.name.startsWith('data-var-'))vars[a.name.slice(9)]=a.value;el.innerHTML=H(el.dataset.i18nTpl,vars,raw);});
  document.querySelectorAll('.bp-gone').forEach(el=>{el.innerHTML=E('ticket.torn')+'<br>'+E('ticket.see_you');});
}
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
const dispName=p=>p.named?(p.key==='Firni ?'?'Firni (1/2?)':p.key):T('map.unnamed_segment');

// countdown (top bar + ticket stub). Drawn again by the day and night switch: in the dark the days are nights.
// your trip (round 12, stage 13.9): kept only in this browser, never sent anywhere. {v:1, out:{date,flight,from,to,departs,arrives},
// ret:{date,flight,departs,arrives}|null (the outbound the other way round), ski:{from,to}|null (set by hand)}
const MYTRIP=(()=>{const K='gud-trip',ISO=/^\d{4}-\d\d-\d\d$/;
  const get=()=>{try{const t=JSON.parse(localStorage.getItem(K)||'null');return t&&t.v===1&&t.out&&ISO.test(t.out.date)?t:null;}catch(e){return null;}};
  const set=t=>{try{t?localStorage.setItem(K,JSON.stringify(t)):localStorage.removeItem(K);}catch(e){}};
  const addDays=(iso,n)=>{const d=new Date(iso+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);};
  // full ski days: from the day after landing (the same day when landing before noon) to the day before the return
  // (the same day when the return leaves at 18:00 or later). The crew: land 10.1 at 20:35, back 15.1 at 01:35, so 11 to 14.
  // An overnight flight (it lands earlier on the clock than it left) lands the next day, as in the app (S-27).
  const skiAuto=(o,r)=>{if(!o||!o.date||!r||!r.date)return null;
    const landed=o.departs&&o.arrives&&o.arrives<o.departs?addDays(o.date,1):o.date;
    const first=o.arrives&&o.arrives<'12:00'?landed:addDays(landed,1),last=r.departs&&r.departs>='18:00'?r.date:addDays(r.date,-1);
    return first<=last?{from:first,to:last}:null;};
  const ski=t=>t?(t.ski||skiAuto(t.out,t.ret)):null;
  const days=t=>t?Math.ceil((new Date(t.out.date+'T00:00:00')-new Date())/864e5):null;
  return {get,set,skiAuto,ski,days,ISO};})();
function countdown(dark){
  const tb=document.getElementById('tbCount'),stub=document.getElementById('tDays'),pre=document.getElementById('tDaysPre'),lbl=document.getElementById('tDaysLbl'),d=MYTRIP.days(MYTRIP.get());
  // the empty pass (no trip yet) has the same stub, with a question mark for the number (round 12, W1)
  {const [a,,c]=slots(dark?'ticket.stub_nights':'ticket.stub_days',{n:5});document.getElementById('beStubPre').textContent=a;document.getElementById('beStubPost').textContent=c;}
  tb.hidden=d===null;if(d===null)return;
  const [c1,,c3]=slots(dark?'ticket.stub_nights':'ticket.stub_days',{n:Math.max(d,0)});pre.textContent=c1;
  if(d>0){
    // the top bar is a flex row: its first word after the number in its own box, as in the markup
    const [a,,b]=slots(dark?'common.nights_to_flight':'common.days_to_flight',{n:d}),sp=b.indexOf(' ');
    tb.innerHTML=(a?`<span>${esc(a)}</span>`:'')+`<b class="num" id="tbDays">${d}</b>`+(sp>0?`<span>${esc(b.slice(0,sp))}</span>${esc(b.slice(sp))}`:esc(b));
    // the same boxes as the markup had (first word in a span), so the Hebrew renders exactly as before
    const sp3=c3.indexOf(' ');stub.textContent=d;lbl.innerHTML=sp3>0?`<span>${esc(c3.slice(0,sp3))}</span>${esc(c3.slice(sp3))}`:esc(c3);}
  else{tb.textContent=T('home.countdown_on_the_way');stub.textContent='0';lbl.textContent=T('ticket.stub_departing');}
}

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
{const[x,y]=P(PASS);const t=mk('text',{x,y,class:'lbl kobi-link','text-anchor':'middle','data-kobi':'1',role:'button',tabindex:'0','aria-label':T('map.kobi_side_aria')},mainLbl);t.textContent=T('map.kobi_side_label');labels.unshift(t);
  t.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openInset();}});} // the keyboard reaches it too

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
  if(was===1&&moved<6&&e.type==='pointerup'){const el=document.elementFromPoint(e.clientX,e.clientY);if(el&&el.dataset.kobi)openInset();else if(el&&el.dataset.key)select(el.dataset.key,{via:'map'});else if(el&&el.dataset.lift)showLift(+el.dataset.lift);}}
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
kSvg.addEventListener('click',e=>{const el=e.target.closest('[data-key],[data-lift]');if(!el)return;if(el.dataset.key)select(el.dataset.key,{via:'map'});else showLift(+el.dataset.lift);});

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
let scrubbed=null; // run_profile_scrub: once per opened run
function select(key,{zoom=true,push=true,via=''}={}){
  const p=byKey[key];clearSel();scrubbed=null;
  {const c=p?p.color:(D.missing.find(x=>x.name===key)||{}).color;if(via&&c)track('run_open',{run:key,color:c,via});}
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
  if(p.key==='Zuma')n.push(T('run.note_zuma_two_lines'));
  const mism=p.osmDiff.filter(x=>x!=='—'&&OSMD[x]&&OSMD[x][1]!==p.color);
  if(p.named&&mism.length)n.push(T('run.note_osm_grade_mismatch',{grades:mism.map(x=>OSMD[x][0]).join(', '),color:HEB[p.color]}));
  if(p.named&&p.osmDiff.includes('—'))n.push(T('run.note_osm_no_grade'));
  const lin=p.segs.filter(s=>!s.area).length;if(lin>1)n.push(T('run.note_separate_segments',{n:lin}));
  if(p.segs.some(s=>s.area))n.push(T('run.note_area_polygon'));
  if(p.lit.includes('yes'))n.push(T('run.note_lit'));
  if(p.kind==='ski-way')n.push(T('run.note_ski_way'));
  if(p.kind==='beginner-area')n.push(T('run.note_beginner_area'));
  if(!p.named)n.push(T('run.note_unnamed_osm'));
  return n;
}
function hostOf(u){try{return new URL(u).hostname.replace(/^www\./,'')}catch{return u}}
const ytId=u=>{try{const x=new URL(u);if(/(^|\.)youtube\.com$/.test(x.hostname))return(x.searchParams.get('v')||'').match(/^[\w-]{11}$/)?x.searchParams.get('v'):null;if(x.hostname==='youtu.be'){const i=x.pathname.slice(1);return/^[\w-]{11}$/.test(i)?i:null;}}catch{}return null;};
function vidList(key){
  const list=vids.filter(v=>v.piste===key).sort((a,b)=>(b.at||0)-(a.at||0));
  return list.length?`<ul class="vids">${list.map(v=>{const id=ytId(v.url);const thumb=id?`<button type="button" class="vthumb" data-vid="${id}" data-title="${esc(v.title||'')}" aria-label="${v.title?E('run.video_play_aria_titled',{title:v.title}):E('run.video_play_aria')}"><img src="https://i.ytimg.com/vi/${id}/hqdefault.jpg" alt="" loading="lazy"><span class="play"></span></button>`:'';
    return `<li>${thumb}<a href="${esc(v.url)}" target="_blank" rel="noopener">${esc(v.title||hostOf(v.url))}</a><small>${esc(v.channel?[v.channel,v.length].filter(Boolean).join(' · '):[v.by||'',v.at?I18N.date(v.at):''].filter(Boolean).join(' · '))}</small></li>`;}).join('')}</ul>`:`<p class="hint">${E('run.videos_empty')}</p>`;
}
function vidBlock(key,label){
  return `<h3>${E('run.videos_heading')}</h3>${vidList(key)}
  <p class="hint"><a href="https://www.youtube.com/results?search_query=${encodeURIComponent('Gudauri '+label+' ski')}" target="_blank" rel="noopener">${E('run.videos_search_youtube',{name:label})}</a></p>`;
}
function elevRows(p){
  if(!TM)return'';const lines=p.segs.filter(s=>!s.area).map(s=>s.g.map(P));if(!lines.length)return'';
  const s=GudRelief.stats(TM,lines);
  return `<dt>${E('run.stat_altitude_label')}</dt><dd>${H('run.stat_altitude_value',{},{top:num(s.top),bot:num(s.bot)})}</dd>
  <dt>${E('run.stat_drop_label')}</dt><dd>${H('common.unit_m',{n:s.drop},{n:num(s.drop)})}${lines.length===1&&p.len?' · '+H('run.stat_avg_gradient',{},{pct:num(Math.round(s.drop/p.len*100))}):''}</dd>
  <dt>${E('run.stat_steep_label')}</dt><dd>${num(Math.round(s.maxG*100)+'%')} <span class="hint">${E('run.stat_steep_hint')}</span></dd>`;
}
const CONFH={high:T('run.confidence_high'),medium:T('run.confidence_medium'),low:T('run.confidence_low')};
const STATH={'osm-named':T('run.source_osm_named'),'osm-unnamed-match':T('run.source_osm_unnamed_match'),'gps':T('run.source_gps')};
// the research notes are written in Hebrew in the data file; their translations live in the strings file (research.<run>.*),
// and a note with no translation shows as written
const RN=(key,he)=>{const v=T(key);return v===key?he:v;},rslug=p=>p.key.toLowerCase().replace(/ /g,'_');
function researchBlock(p){const r=p.research,k='research.'+rslug(p);
  return `<h3>${E('run.source_heading')}</h3>
  <dl class="kv"><dt>${E('run.confidence_label')}</dt><dd>${esc(CONFH[r.conf]||r.conf)}</dd>
  <dt>${E('run.source_label')}</dt><dd>${esc(STATH[r.status]||r.status)}${r.historical?E('run.source_historical'):''}</dd>
  ${r.gps?`<dt>${E('run.gps_tracks_label')}</dt><dd>${H('run.gps_tracks_value',{n:r.gps},{n:num(r.gps)})}</dd>`:''}
  ${r.partial?`<dt>${E('run.coverage_label')}</dt><dd>${esc(RN(k+'.partial',r.partial))}</dd>`:''}</dl>
  <p class="hint">${esc(RN(k+'.notes',r.notes))}</p>
  <ul class="notes">${r.sources.map(x=>`<li class="hint"><bdi>${esc(/[\u0590-\u05ff]/.test(x)?RN('research.source_mta',x):x)}</bdi></li>`).join('')}</ul>`;}
const navList=()=>{const order=['green','blue','red','black'];return D.pistes.filter(p=>p.named).sort((a,b)=>order.indexOf(a.color)-order.indexOf(b.color)||a.key.localeCompare(b.key,undefined,{numeric:true})).map(p=>p.key);};
function runNav(key){
  const L=navList(),i=L.indexOf(key);if(i<0)return `<div class="run-nav"><button type="button" class="rn-share" data-share="${esc(key)}">${E('run.share_button')}</button></div>`;
  const prev=L[(i-1+L.length)%L.length],next=L[(i+1)%L.length],[back,fwd]=I18N.ltr?['←','→']:['→','←'];
  return `<div class="run-nav" role="group" aria-label="${E('run.nav_group_aria')}"><button type="button" data-goto="${esc(prev)}" aria-label="${E('run.nav_prev_aria',{run:prev})}">${back} <span dir="ltr">${esc(prev)}</span></button><button type="button" class="rn-share" data-share="${esc(key)}">${E('run.share_button')}</button><button type="button" data-goto="${esc(next)}" aria-label="${E('run.nav_next_aria',{run:next})}"><span dir="ltr">${esc(next)}</span> ${fwd}</button></div>`;
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
    cmpHtml=`<p class="run-cmp">${H('run.compare_line',{},{steep_run:pisteBtn(bg.key),long_run:pisteBtn(bl.key)})}</p>`;}
  const endTxt=p.toLifts.length?H('run.end_to_lift',{},{lifts:p.toLifts.map(liftBtn).join('')}):p.joins.length?H('run.end_continue_to',{},{runs:p.joins.map(pisteBtn).join('')}):'';
  const startTxt=p.fromLifts.length?H('run.start_from_lift',{},{lifts:p.fromLifts.map(liftBtn).join('')}):'';
  const fly=can3d&&!reduceMotion()?`<button type="button" class="btn run-fly" data-fly="${esc(key)}">${E('run.fly_button')}</button>`:'';
  return `<h3>${E('run.profile_heading')}</h3>
  <div class="prof"><svg viewBox="0 0 ${W} ${Hc}" preserveAspectRatio="none" aria-hidden="true">
    <polygon points="8,${Hc-16} ${line} ${W-8},${Hc-16}" class="pf-fill"/><polyline points="${line}" class="pf-line"/>${band}
    ${st.g?`<rect x="${X(st.d)}" y="0" width="${(X(S[st.j].d)-X(st.d)).toFixed(1)}" height="${Hc-16}" class="pf-steep"/>`:''}
    <line id="pfX" x1="8" x2="8" y1="0" y2="${Hc-8}" class="pf-x"/></svg><span class="pf-dot" id="pfDot" style="left:${(8/W*100).toFixed(2)}%;top:${Y(S[0].h)}px"></span>
    <input type="range" id="profRange" min="0" max="${n-1}" value="0" aria-label="${E('run.profile_range_aria')}" dir="ltr" data-key="${esc(key)}">
    <span class="pf-top num">${Math.round(hmax)}</span><span class="pf-bot num">${Math.round(hmin)}</span></div>
  <div class="prof-read"><span><small>${E('run.profile_from_start')}</small><b class="num" id="pfD">${E('common.unit_m',{n:0})}</b></span><span><small>${E('run.profile_altitude_label')}</small><b class="num" id="pfH">${E('common.unit_m',{n:Math.round(S[0].h).toLocaleString('en-US')})}</b></span><span><small>${E('run.profile_slope_here')}</small><b class="num" id="pfA">${Math.round(S[0].a)}°</b></span></div>
  <ul class="slope-key">${GudRelief.SLOPE.map(([,c,t],i,a)=>`<li><i style="background:${c}"></i>${esc(i===0?T('map.slope_upto15'):i===a.length-1?T('map.slope_over30'):t)}</li>`).join('')}</ul>
  ${fly}
  <h3>${E('run.ahead_heading')}</h3>
  <ol class="brief">
    <li><b>${H('run.ahead_start_title',{},{alt:num(Math.round(S[0].h))})}</b><span>${H('run.ahead_start_text',{deg:deg(g0)},{start:startTxt})}</span></li>
    ${st.g?`<li class="b-steep"><b>${H('run.ahead_steep_title',{},{dist:num(Math.round(st.d))})}</b><span>${H('run.ahead_steep_text',{deg:deg(maxG)},{pct:num(Math.round(maxG*100))})}</span></li>`:''}
    <li><b>${H('run.ahead_end_title',{},{alt:num(Math.round(S[n-1].h))})}</b><span>${H('run.ahead_end_text',{},{dist:num(Math.round(dmax)),end:endTxt})}</span></li>
  </ol>
  ${cmpHtml}
  <p class="hint">${E('run.profile_hint')}</p>`;
}
function profAt(i){
  const k=document.getElementById('profRange');if(!k)return;const pr=runProfile(k.dataset.key);if(!pr)return;const S=pr.S,q=S[Math.max(0,Math.min(S.length-1,i))];
  const svgp=panel.querySelector('.prof svg'),vb_=svgp.viewBox.baseVal,W=vb_.width,Hc=vb_.height,dmax=S[S.length-1].d,hs=S.map(z=>z.h),hmax=Math.max(...hs),hmin=Math.min(...hs);
  const x=8+q.d/dmax*(W-16),y=8+(hmax-q.h)/((hmax-hmin)||1)*(Hc-30);
  const X=document.getElementById('pfX');X.setAttribute('x1',x);X.setAttribute('x2',x);const dot=document.getElementById('pfDot');dot.style.left=(x/W*100)+'%';dot.style.top=y+'px';
  document.getElementById('pfD').textContent=T('common.unit_m',{n:Math.round(q.d).toLocaleString('en-US')});document.getElementById('pfH').textContent=T('common.unit_m',{n:Math.round(q.h).toLocaleString('en-US')});document.getElementById('pfA').textContent=Math.round(q.a)+'°';
  setMarker(q);flyHudAt(q);
}
let flying=false,flyProg=0;
function stopFly(){if(flying&&v3)v3.stopFly();flying=false;}
// while flying: a bar on the map with where we are, and a stop button (on the phone the profile is out of view)
function flyHud(on,name){let h=document.getElementById('flyHud');
  if(!h){h=document.createElement('div');h.className='fly-hud';h.id='flyHud';h.setAttribute('role','status');h.innerHTML='<span class="fh-txt"><b class="fh-name"></b><span class="num fh-d"></span></span><button type="button" class="fh-stop">'+E('run.fly_stop')+'</button>';
    document.querySelector('.mapwrap').appendChild(h);h.querySelector('.fh-stop').addEventListener('click',()=>stopFly());}
  h.hidden=!on;h.parentNode.classList.toggle('flying',on);if(name)h.querySelector('.fh-name').textContent=name;}
function flyHudAt(q){const h=document.getElementById('flyHud');if(h&&!h.hidden)h.querySelector('.fh-d').textContent=T('run.fly_hud_readout',{dist:Math.round(q.d).toLocaleString('en-US'),alt:Math.round(q.h).toLocaleString('en-US'),deg:Math.round(q.a)});}
function renderPiste(key){
  const p=byKey[key];
  if(!p){const m=D.missing.find(x=>x.name===key);if(!m)return overview();
    panel.innerHTML=`<button class="back" data-back>${E('map.back_all_runs')}</button><h2 class="c-${m.color}">${esc(m.name)}</h2>
    <dl class="kv"><dt>${E('run.official_color_label')}</dt><dd>${pips(m.color)}${esc(HEB[m.color])} · ${esc(RATE[m.color][1])}</dd><dt>${E('common.length_label')}</dt><dd>—</dd></dl>
    <h3>${E('run.notes_heading')}</h3><div class="notice">${E('run.missing_notice')}</div>${vidBlock(key,m.name)}`;
    return;}
  const c=p.color,label=p.named?(p.key==='Firni ?'?'Firni':p.key):'';
  panel.innerHTML=`<button class="back" data-back>${E('map.back_all_runs')}</button>
  <div class="run-sign"><h2 class="c-${c}">${esc(dispName(p))}</h2>${p.refs.length?`<span class="run-ref num" title="${E('run.ref_title')}">${esc(p.refs[0])}</span>`:''}</div>
  ${runNav(key)}
  <dl class="kv">
    <dt>${E('common.length_label')}</dt><dd class="num">${esc(fmtLen(p.len))}</dd>
    ${elevRows(p)}
    <dt>${E('run.color_label')}</dt><dd><span class="sw ${c}"></span> ${esc(HEB[c])}${E(p.named?'run.color_official_suffix':'run.color_osm_suffix')}</dd>
    <dt>${E('run.difficulty_label')}</dt><dd>${pips(c)}${esc(RATE[c][1])}</dd>
    ${p.osmDiff.length?`<dt>${E('run.osm_grade_label')}</dt><dd>${esc(p.osmDiff.map(x=>OSMD[x]?OSMD[x][0]:T('run.osm_grade_none')).join(' / '))}</dd>`:''}
    ${p.refs.length?`<dt>${E('run.ref_label')}</dt><dd class="num">${esc(p.refs.join(', '))}</dd>`:''}
    ${p.groom.length?`<dt>${E('run.grooming_label')}</dt><dd>${p.groom.includes('classic')?E('run.groomed_value'):esc(p.groom.join(', '))}</dd>`:''}
  </dl>
  ${runViewBlock(key)}
  <h3>${E('run.connections_heading')}</h3>
  <dl class="kv">
    <dt>${E('run.lift_at_top')}</dt><dd>${p.fromLifts.map(liftBtn).join('')||'—'}</dd>
    <dt>${E('run.lift_at_bottom')}</dt><dd>${p.toLifts.map(liftBtn).join('')||'—'}</dd>
    <dt>${E('run.joins_label')}</dt><dd>${p.joins.map(pisteBtn).join('')||'—'}</dd>
    <dt>${E('run.from_runs_label')}</dt><dd>${p.fromPistes.map(pisteBtn).join('')||'—'}</dd>
  </dl>
  ${TM?`<p class="hint">${E('run.elevation_accuracy_hint')}</p>`:''}
  <p class="hint">${E('run.connections_hint')}</p>
  <h3>${E('run.notes_heading')}</h3><ul class="notes">${notesFor(p).map(x=>`<li>${esc(x)}</li>`).join('')||`<li>${E('run.notes_none')}</li>`}</ul>
  ${p.research?researchBlock(p):''}
  <p class="hint">OSM: ${[...new Set(p.research&&p.research.osmIds.length?p.research.osmIds:p.segs.map(s=>s.id))].map(id=>`<a href="https://www.openstreetmap.org/way/${id}" target="_blank" rel="noopener">${id}</a>`).join(' · ')}</p>
  ${p.named?vidBlock(key,label):''}`;
}
function liftElev(l){
  if(!TM){return l.rise?`<dt>${E('lift.rise_label')}</dt><dd class="num">${E('common.unit_m',{n:l.rise})}</dd>`:'';}
  const a=P(l.g[0]),b=P(l.g[l.g.length-1]),ea=Math.round(TM.elev(a[0],a[1])),eb=Math.round(TM.elev(b[0],b[1]));
  return `<dt>${E('lift.stations_label')}</dt><dd>${H('lift.stations_value',{},{bot:num(Math.min(ea,eb)),top:num(Math.max(ea,eb))})}</dd><dt>${E('lift.rise_label')}</dt><dd>${H('lift.rise_model_value',{n:Math.abs(eb-ea)},{n:num(Math.abs(eb-ea))})}${l.rise?H('lift.rise_osm_suffix',{n:l.rise},{n:num(esc(l.rise))}):''}</dd>`;
}
function showLift(id){
  const l=D.lifts.find(x=>x.id===id);if(!l)return;track('lift_open',{lift:l.name||null});clearSel();current=null;if(location.hash.startsWith('#map/run/'))history.pushState(null,'','#map');
  const top=D.pistes.filter(p=>p.fromLifts.includes(l.name)).map(p=>pisteBtn(p.key)).join('');
  const bot=D.pistes.filter(p=>p.toLifts.includes(l.name)).map(p=>pisteBtn(p.key)).join('');
  panel.innerHTML=`<button class="back" data-back>${E('map.back_all_runs')}</button><h2>⇡ ${esc(l.name||T('lift.unnamed'))}</h2>
  <dl class="kv"><dt>${E('lift.type_label')}</dt><dd>${esc(LK[l.kind]||l.kind)}${l.status==='inactive'?E('lift.inactive_suffix'):''}</dd>
  <dt>${E('common.length_label')}</dt><dd class="num">${esc(fmtLen(l.len))}</dd>
  ${l.dur?`<dt>${E('lift.ride_time_label')}</dt><dd class="num">${E('lift.ride_time_value',{n:l.dur})}</dd>`:''}
  ${l.occ?`<dt>${E('lift.seats_label')}</dt><dd class="num">${esc(l.occ)}${l.bubble==='yes'?E('lift.bubble_suffix'):''}</dd>`:''}
  ${l.cap?`<dt>${E('lift.capacity_label')}</dt><dd class="num">${E('lift.capacity_value',{n:l.cap})}</dd>`:''}
  ${liftElev(l)}
  ${l.year?`<dt>${E('lift.built_label')}</dt><dd class="num">${esc(l.year)}</dd>`:''}</dl>
  <h3>${E('lift.runs_from_top_heading')}</h3><div>${top||'—'}</div>
  <h3>${E('lift.runs_to_bottom_heading')}</h3><div>${bot||'—'}</div>
  <p class="hint">OSM: <a href="https://www.openstreetmap.org/way/${l.id}" target="_blank" rel="noopener">${l.id}</a></p>`;
}
function overview(){
  clearSel();current=null;
  const named=D.pistes.filter(p=>p.named);const order=['green','blue','red','black'];
  named.sort((a,b)=>order.indexOf(a.color)-order.indexOf(b.color)||a.key.localeCompare(b.key,undefined,{numeric:true}));
  panel.innerHTML=`
  <h2 class="ov">${E('map.overview_heading')}</h2>
  ${LSTAT.block()}
  <p class="lead">${E('map.overview_lead')}</p>
  <h3>${E('map.overview_on_map_count',{n:named.length})}</h3>
  <div class="index">${named.map(p=>`<button data-goto="${esc(p.key)}" data-zoom="1"><span class="sw ${p.color}"></span>${esc(dispName(p))}<span class="len">${fmtLen(p.len)}</span></button>`).join('')}</div>
  ${D.missing.length?`<h3>${E('map.overview_missing_count',{n:D.missing.length})}</h3>
  <div class="miss">${D.missing.map(m=>`<button class="chip" data-goto="${esc(m.name)}"><span class="sw ${m.color}"></span>${esc(m.name)}</button>`).join('')}</div>`:''}
  ${(()=>{const pr=D.pistes.filter(p=>p.research&&p.research.partial);return pr.length?`<h3>${E('map.overview_partial_count',{n:pr.length})}</h3>
  <div class="miss">${pr.map(p=>`<button class="chip" data-goto="${esc(p.key)}" data-zoom="1"><span class="sw ${p.color}"></span>${esc(p.key)}</button>`).join('')}</div>
  <p class="hint">${E('map.overview_partial_hint')}</p>`:''})()}
  <h3>${E('map.overview_source_heading')}</h3>
  <p class="hint">${E('map.overview_source_text',{fetched:D.fetched,research_date:D.research?D.research.date:''})}</p>
  <p class="hint">${E('map.overview_3d_controls_hint')}</p>`;
  SNOW.scan(panel);
}
panel.addEventListener('input',e=>{if(e.target.id==='profRange'){profAt(+e.target.value);if(scrubbed!==current){scrubbed=current;track('run_profile_scrub',{run:current});}}});
panel.addEventListener('click',e=>{
  const b=e.target.closest('button');if(!b)return;
  if(b.dataset.share!==undefined){const k=b.dataset.share,url=location.origin+location.pathname+'?utm_medium=share#map/run/'+encodeURIComponent(k);
    if(navigator.share)navigator.share({title:T('run.share_title',{run:k}),url}).then(()=>track('run_share',{run:k,method:'native'})).catch(()=>{});
    else if(navigator.clipboard)navigator.clipboard.writeText(url).then(()=>{track('run_share',{run:k,method:'copy'});b.textContent=T('common.link_copied');setTimeout(()=>{b.textContent=T('run.share_button');},2200);}).catch(()=>{});return;}
  if(b.dataset.fly){if(flying){stopFly();return;}const pr=runProfile(b.dataset.fly);if(!pr)return;if(view!=='3d')setView('3d',false);if(!v3)return;
    flying=true;b.textContent=T('run.fly_stop');b.setAttribute('aria-pressed','true');
    const p_=byKey[b.dataset.fly];flyHud(true,dispName(p_)||b.dataset.fly);flyHudAt(pr.S[0]);
    const fk=b.dataset.fly,ft0=performance.now();let fdone=false;flyProg=0;track('run_fly_start',{run:fk});
    // the map is above the panel on the phone: bring it into view so the flight is seen
    const r=document.querySelector('.mapwrap').getBoundingClientRect();if(r.top<-4||r.bottom>innerHeight+4)scrollTo({top:Math.max(0,r.top+scrollY-8),behavior:'smooth'});
    v3.flyAlong(pr.S.map(q=>[q.x,q.y]),()=>{if(!fdone){fdone=true;track('run_fly_end',{run:fk,completed:flyProg>=.999,seconds:Math.round((performance.now()-ft0)/1000)});}flying=false;flyHud(false);const bb=panel.querySelector('[data-fly]');if(bb){bb.textContent=T('run.fly_button');bb.setAttribute('aria-pressed','false');}});return;}
  if(b.dataset.back!==undefined){overview();if(location.hash!=='#map')history.pushState(null,'','#map');return;}
  if(b.dataset.filter){const k=b.dataset.filter;hidden.has(k)?hidden.delete(k):hidden.add(k);b.setAttribute('aria-pressed',!hidden.has(k));applyFilters();return;}
  if(b.dataset.vid){track('video_play',{run:current,video:b.dataset.vid});const w=document.createElement('div');w.className='vframe';const f=document.createElement('iframe');
    f.src='https://www.youtube-nocookie.com/embed/'+b.dataset.vid+'?autoplay=1&rel=0';f.title=b.dataset.title||T('run.video_iframe_title');f.allow='autoplay; encrypted-media; picture-in-picture; fullscreen';f.allowFullscreen=true;f.referrerPolicy='strict-origin-when-cross-origin';
    w.appendChild(f);b.replaceWith(w);return;}
  if(b.dataset.goto){select(b.dataset.goto,{zoom:true,via:b.closest('.run-nav')?'swipe':'list'});panel.scrollTop=0;if(matchMedia('(max-width:760px)').matches)panel.scrollIntoView({block:'start'});return;}
  if(b.dataset.lift){const l=D.lifts.find(x=>x.id===+b.dataset.lift);showLift(+b.dataset.lift);if(l){if(view==='3d')v3.focusLift(l.id);else if(isKobiL(l))openInset();else focusOn([l.g]);}return;}
});

{let sx=0,sy=0,ok=false;
  panel.addEventListener('touchstart',e=>{const t=e.touches[0];ok=e.touches.length===1&&!e.target.closest('input,.prof');sx=t.clientX;sy=t.clientY;},{passive:true});
  panel.addEventListener('touchend',e=>{if(!ok||!current)return;const t=e.changedTouches[0],dx=t.clientX-sx,dy=t.clientY-sy;if(Math.abs(dx)<70||Math.abs(dy)>45)return;
    const L=navList(),i=L.indexOf(current);if(i<0)return;select(L[(i+(dx<0?1:-1)+L.length)%L.length],{via:'swipe'});},{passive:true});}
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
      onPick:k=>select(k,{via:'map'}),onLift:id=>showLift(id),
      onFly:f=>{flyProg=f;const k=document.getElementById('profRange');if(k){const i=Math.round(f*(+k.max));k.value=i;profAt(i);}},
      onHeading:az=>{compassSvg.style.transform=`rotate(${(az*180/Math.PI).toFixed(1)}deg)`;}});
    v3.kobi=()=>v3.view({tx:kc[0],tz:kc[1],dist:6800,az:Math.PI*0.9,pol:0.6});
    v3.setTheme(isDark());DN.paint(false); // the 3D light follows the time in Gudauri, like the home page
    v3.filter(hidden);if(current&&byKey[current]){v3.select(current);v3.paint(current,0);}
    try{LSTAT.applyMap();}catch(e){} // the lift status, if it came before the 3D view
    return true;
  }catch(e){console.warn('3D unavailable',e);v3=null;if(!ensure3d.told){ensure3d.told=1;track('map_fallback',{reason:'no_webgl'});}return false;}
}
function setView(m,save){
  if(m==='3d'&&!ensure3d())m='2d';view=m;
  m3.hidden=m!=='3d';svg.style.visibility=m==='3d'?'hidden':'';
  wrap.classList.toggle('mode3d',m==='3d');wrap.classList.toggle('mode2d',m!=='3d');
  sw.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.view===m)));
  if(m==='3d'){v3.resize();if(!card.hidden)closeInset();}else apply();
  if(save)try{localStorage.setItem('gud-view',m);}catch(e){}
}
sw.addEventListener('click',e=>{const b=e.target.closest('button[data-view]');if(!b)return;const was=view;setView(b.dataset.view,true);if(view!==was)track('map_view',{view});});
document.getElementById('zin').onclick=()=>{if(view==='3d')return v3.zoom(1/1.5);const[cw,ch]=sz();zoomAt(1/1.5,cw/2,ch/2);};
document.getElementById('zout').onclick=()=>{if(view==='3d')return v3.zoom(1.5);const[cw,ch]=sz();zoomAt(1.5,cw/2,ch/2);};
document.getElementById('zfit').onclick=()=>{if(view==='3d')return v3.home();fit();};
document.getElementById('compass').onclick=()=>{if(v3)v3.north();};
openBtn.onclick=()=>{if(view==='3d')v3.kobi();else openInset();};
{const _up=up;} // keep 2D handlers
wrap.classList.add('mode2d');
// filters live in the toolbar above the map
document.getElementById('filters').addEventListener('click',e=>{const b=e.target.closest('button[data-filter]');if(!b)return;const k=b.dataset.filter;
  hidden.has(k)?hidden.delete(k):hidden.add(k);b.setAttribute('aria-pressed',String(!hidden.has(k)));applyFilters();track('map_filter',{filter:k,on:!hidden.has(k)});});
// the map (and its 3D model) is set up the first time the map page is opened
let mapReady=false;
function activateMap(){
  if(!mapReady){mapReady=true;fit();
    if(can3d){sw.hidden=false;let pref=null;try{pref=localStorage.getItem('gud-view');}catch(e){}setView(pref==='2d'?'2d':'3d');}
    else track('map_fallback',{reason:window.THREE&&TM?'no_webgl':'lib_failed'});}
  else if(view==='3d'&&v3)v3.resize();else apply();
}
// pages: #map shows the map, anything else the home page
const pgHome=document.getElementById('home'),pgMap=document.getElementById('mapPage'),pgMeet=document.getElementById('meetPage'),pgGames=document.getElementById('gamesPage'),pgAbout=document.getElementById('aboutPage'),pgTrip=document.getElementById('tripPage');
function route(){
  const h=location.hash,m=h.startsWith('#map'),mt=h.startsWith('#meet'),gm=h.startsWith('#games'),ab=h.startsWith('#about'),tr=h==='#trip',ac=/^#(signin|account|join|group)(\/|$)/.test(h),run=h.startsWith('#map/run/')?decodeURIComponent(h.slice(9)):null,wasMap=!pgMap.hidden;
  pgHome.hidden=m||mt||gm||ab||tr||ac;pgMap.hidden=!m;pgMeet.hidden=!mt;pgGames.hidden=!gm;pgAbout.hidden=!ab;pgTrip.hidden=!tr;if(tr)TRIPFORM.open();
  // accounts and groups: js/account.js shows its own pages (round 12)
  if(window.ACCOUNT)ACCOUNT.route(ac?h:'');
  const cur=m?'map':mt?'meet':gm?'games':ab?'about':tr||ac?'':'home';
  document.querySelectorAll('[data-nav]').forEach(a=>{if(a.dataset.nav===cur)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});
  if(m&&!wasMap)LSTAT.viewed();
  requestAnimationFrame(()=>{if(gm||ab||tr||ac)return;if(mt){if(MEET)MEET.open(h.slice(6));return;}if(!m){DN.layout();return;}activateMap();
    if(run&&run!==current&&(byKey[run]||D.missing.some(x=>x.name===run)))select(run,{push:false,via:'link'});
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
  const MODES=['auto','day','night'],LBL={auto:T('daynight.mode_auto'),day:T('daynight.mode_day'),night:T('daynight.mode_night')};
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
    const phase=T('daynight.phase_'+(dark?(h>t.set&&h<t.set+1.3?'twilight':'night'):h<t.rise+.8?'sunrise':h<t.set-2?'day':h<t.set-.6?'golden_hour':'sunset'));
    return {a,b,f,dark,now,h,g,phase,morning:h<t.noon};
  }
  function paint(anim){
    const s=state(),{a,b,f}=s,num=k=>lerp(a[k],b[k],f);
    root.dataset.theme=s.dark?'dark':'light';
    const hh=Math.floor(s.now),mm=Math.floor((s.now-hh)*60);
    document.querySelectorAll('[data-dn-clock]').forEach(e=>e.textContent=String(hh).padStart(2,'0')+':'+String(mm).padStart(2,'0'));
    document.querySelectorAll('[data-dn]').forEach(bt=>{bt.dataset.mode=mode;const nx=MODES[(MODES.indexOf(mode)+1)%3];
      bt.setAttribute('aria-label',T('daynight.button_aria',{mode:LBL[mode],next:LBL[nx]}));bt.title=bt.getAttribute('aria-label');});
    document.querySelectorAll('[data-dn-lbl]').forEach(e=>e.textContent=LBL[mode]);
    countdown(s.dark);
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
    mode=MODES[(MODES.indexOf(mode)+1)%3];try{localStorage.setItem('gud-daynight',mode);}catch(e){}paint(true);track('theme_set',{mode});}));
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
  // each name kept whole and apart from the words around it (a Latin name in a Hebrew sentence: "של Goodaura ו-New Goodaura")
  const iso=x=>'\u2068'+x.replace(/ /g,'\u00a0')+'\u2069';
  st.forEach(s=>{const b=s.ends.filter(e=>e.end==='b'),t=s.ends.filter(e=>e.end==='t'),n=a=>a.map(e=>iso(e.l.name)).join(T('meet.lift_names_join'));
    s.name=(b[0]||t[0]).l.name;
    s.where=[b.length?T('meet.where_bottom_station',{lifts:n(b)}):'',t.length?T('meet.where_top_station',{lifts:n(t)}):''].filter(Boolean).join(', ');});
  const byId=Object.fromEntries(st.map(s=>[s.id,s]));
  const find=name=>(end)=>st.find(s=>s.ends.some(e=>e.l.name===name&&e.end===end));
  // the ski days of your trip (round 12): from "your trip" (up to two weeks), or the next week when there is none
  function skiDays(){const t=MYTRIP.get(),r=MYTRIP.ski(t),out=[];
    const addD=(iso,n)=>{const d=new Date(iso+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);};
    let a,b;if(r){a=r.from;b=r.to;}else{a=new Date(Date.now()+4*36e5).toISOString().slice(0,10);if(t&&t.out.date>a)a=addD(t.out.date,1);b=addD(a,6);}
    for(let d=a;d<=b&&out.length<14;d=addD(d,1))out.push([d,dayName(d)]);return out;}
  function dayName(iso){const d=new Date(iso+'T12:00:00Z'),lang=document.documentElement.lang||'he';
    if(lang==='he')return new Intl.DateTimeFormat('he',{weekday:'narrow',timeZone:'UTC'}).format(d)+` ${d.getUTCDate()}.${d.getUTCMonth()+1}`;
    try{return I18N.date(d,{weekday:'short',day:'numeric',month:'short',timeZone:'UTC'});}catch(e){return iso.split('-').reverse().join('.');}}
  let DAYS=skiDays();
  const TIMES=['09:30','11:00','12:30','13:30','15:00','16:30'];
  const PRE=[['am',T('meet.preset_morning_lift'),find('Goodaura')('b'),'09:30'],['noon',T('meet.preset_noon'),find('Goodaura')('t'),'13:00'],['pm',T('meet.preset_end_of_day'),find('New Goodaura')('b'),'16:30']].filter(p=>p[2]);
  // nothing is picked at first (Pini, round 8): the map asks where to meet, and the card waits
  const S={sid:'',time:'12:30',day:DAYS[0][0],preset:''};
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
  // a tap near a station picks it; a tap on the picked pin, or on empty map, clears the choice
  svgM.addEventListener('click',e=>{const r=svgM.getBoundingClientRect(),x=vb.x+(e.clientX-r.left)/r.width*vb.w,y=vb.y+(e.clientY-r.top)/r.height*vb.h;
    // distance to the whole pin (tip and head), so a tap on the head counts for that pin
    const u=vb.w/r.width,dpin=s=>{const hy=s.y-26*u*(s.id===S.sid?1.2:.8);return Math.min(Math.hypot(s.x-x,s.y-y),Math.hypot(s.x-x,hy-y));};
    const best=st.map(s=>[s,dpin(s)]).sort((a,b)=>a[1]-b[1])[0];if(!best)return;
    const px=best[1]/u;
    if(px<=44){if(best[0].id===S.sid)clear('pin');else{pick(best[0].id,'',true);track('meet_pick',{kind:'station',station:best[0].name});}}else if(S.sid)clear('map');});
  // the whole mountain: every pin in view, its head clear of the buttons at the top and its point clear of the line at the bottom
  function fitAll(ms){let a=1e9,b=1e9,c=-1e9,d=-1e9;st.forEach(s=>{a=Math.min(a,s.x);c=Math.max(c,s.x);b=Math.min(b,s.y);d=Math.max(d,s.y);});
    const[w,h]=size(),hint=document.getElementById('meetHint'),top=86,bot=(hint&&hint.offsetHeight?hint.offsetHeight+12:0)+14;
    const sx=w-64,sy=Math.max(80,h-top-bot),span=Math.max((c-a)/sx,(d-b)/sy)*w,ys=top+sy/2;
    goTo((a+c)/2,(b+d)/2+(0.55*h-ys)*span/w,span,ms);}
  document.getElementById('meetAll').onclick=()=>fitAll(700);
  // zoom: buttons, the wheel, two fingers; and dragging moves the map (a drag is not a tap)
  const zoomAt=(f,cx_,cy_,ms)=>{const[w,h]=size();const nw=Math.max(300,Math.min(9000,vb.w*f));const px=cx_??vb.x+vb.w/2,py=cy_??vb.y+vb.h/2;
    const fx=(px-vb.x)/vb.w,fy=(py-vb.y)/vb.h;cancelAnimationFrame(tween);
    if(ms&&!reduceMotion()){const from={...vb},to={w:nw,x:px-fx*nw,y:py-fy*nw*h/w},t0=performance.now(),step=()=>{const t=Math.min(1,(performance.now()-t0)/ms),e=1-Math.pow(1-t,3);
      vb.w=from.w+(to.w-from.w)*e;vb.x=from.x+(to.x-from.x)*e;vb.y=from.y+(to.y-from.y)*e;applyVB();if(t<1)tween=requestAnimationFrame(step);};tween=requestAnimationFrame(step);}
    else{vb.x=px-fx*nw;vb.y=py-fy*nw*h/w;vb.w=nw;applyVB();}};
  document.getElementById('meetZin').onclick=()=>{const s=byId[S.sid];zoomAt(.6,s&&s.x,s&&s.y,250);};
  document.getElementById('meetZout').onclick=()=>zoomAt(1/.6,null,null,250);
  const toMap=(e)=>{const r=svgM.getBoundingClientRect();return[vb.x+(e.clientX-r.left)/r.width*vb.w,vb.y+(e.clientY-r.top)/r.height*vb.h];};
  svgM.addEventListener('wheel',e=>{e.preventDefault();const[x,y]=toMap(e);zoomAt(Math.exp(e.deltaY*.0015),x,y,0);},{passive:false});
  const ptrs=new Map();let drag=null,moved=false,pinch=null;
  svgM.addEventListener('pointerdown',e=>{ptrs.set(e.pointerId,{x:e.clientX,y:e.clientY});moved=false;
    if(ptrs.size===1)drag={x:e.clientX,y:e.clientY,vx:vb.x,vy:vb.y};
    else if(ptrs.size===2){const[a,b]=[...ptrs.values()];pinch={d:Math.hypot(a.x-b.x,a.y-b.y),w:vb.w};drag=null;}});
  svgM.addEventListener('pointermove',e=>{if(!ptrs.has(e.pointerId))return;ptrs.set(e.pointerId,{x:e.clientX,y:e.clientY});const r=svgM.getBoundingClientRect();
    if(pinch&&ptrs.size===2){const[a,b]=[...ptrs.values()],d=Math.hypot(a.x-b.x,a.y-b.y);moved=true;const[mx,my]=toMap({clientX:(a.x+b.x)/2,clientY:(a.y+b.y)/2});zoomAt(pinch.w*pinch.d/Math.max(20,d)/vb.w,mx,my,0);}
    else if(drag){const dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.hypot(dx,dy)>6){moved=true;svgM.setPointerCapture(e.pointerId);}
      if(moved){cancelAnimationFrame(tween);vb.x=drag.vx-dx/r.width*vb.w;vb.y=drag.vy-dy/r.height*vb.h;applyVB();}}});
  const lift=e=>{ptrs.delete(e.pointerId);if(ptrs.size<2)pinch=null;if(!ptrs.size)drag=null;};
  svgM.addEventListener('pointerup',lift);svgM.addEventListener('pointercancel',lift);
  svgM.addEventListener('click',e=>{if(moved){e.stopImmediatePropagation();moved=false;}},true);
  let undoSid='',undoPre='',toastT=0;const toast=document.getElementById('meetToast');
  function clear(via){if(!S.sid)return;track('meet_clear',{via});undoSid=S.sid;undoPre=S.preset;S.sid='';S.preset='';render();
    toast.hidden=false;clearTimeout(toastT);toastT=setTimeout(()=>{toast.hidden=true;},4500);}
  document.getElementById('meetClear').onclick=()=>clear('button');document.getElementById('meetCardX').onclick=()=>clear('card');
  document.getElementById('meetUndo').onclick=()=>{toast.hidden=true;clearTimeout(toastT);if(undoSid){track('meet_undo');pick(undoSid,undoPre,true);}};
  new ResizeObserver(()=>{if(!document.getElementById('meetPage').hidden)applyVB();}).observe(svgM);
  // controls
  const ui=document.getElementById('meetUI');
  function drawDays(){ui.querySelector('[data-days]').innerHTML=DAYS.map(([v,l])=>`<button type="button" data-day="${v}">${esc(l)}</button>`).join('');}
  drawDays();
  ui.querySelector('[data-times]').innerHTML=TIMES.map(t=>`<button type="button" class="num" data-time="${t}">${t}</button>`).join('');
  ui.querySelector('[data-pre]').innerHTML=PRE.map(([k,l,s,t],i)=>`<button type="button" class="mp-sign mp-${k}" data-pre="${k}"><b>${esc(l)}</b><span dir="ltr">${esc(s.name)} ${t}</span></button>`).join('');
  ui.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;
    if(b.dataset.day){S.day=b.dataset.day;S.preset='';render();}
    else if(b.dataset.time){S.time=b.dataset.time;S.preset='';ui.querySelector('#meetTime').value=S.time;render();}
    else if(b.dataset.pre){const p=PRE.find(x=>x[0]===b.dataset.pre);S.time=p[3];ui.querySelector('#meetTime').value=S.time;pick(p[2].id,p[0],true);track('meet_pick',{kind:'preset',preset:{am:'morning',noon:'noon',pm:'end'}[p[0]]});}});
  ui.querySelector('#meetTime').addEventListener('input',e=>{if(/^\d\d:\d\d$/.test(e.target.value)){S.time=e.target.value;S.preset='';render();}});
  function pick(id,preset,fly){S.sid=id;S.preset=preset||'';const s=byId[id];if(fly&&s)goTo(s.x,s.y,Math.max(1400,vb.w<1500?vb.w:1800),650);render();}
  // how to get there, from the connections in the data only
  const chip=p=>`<span class="rt-run c-${p.color}" dir="ltr">${esc(dispName(p))}</span>`,liftChip=n=>`<span class="rt-lift" dir="ltr">⇡ ${esc(n)}</span>`,dot='<span class="rt-dot" aria-hidden="true"></span>',sep='<span class="rt-sep" aria-hidden="true"></span>';
  function routes(s){const out=[];
    s.ends.forEach(({l,end})=>{
      if(end==='b')D.pistes.filter(p=>p.named&&p.toLifts.includes(l.name)).forEach(p=>{const pr=p.fromPistes.map(k=>byKey[k]).find(x=>x&&x.named&&x.key!==p.key);
        out.push({from:pr?T('meet.route_from_run',{run:pr.key}):T('meet.route_from_top_of',{run:p.key}),html:[pr?chip(pr):'',chip(p),dot].filter(Boolean).join(sep)});});
      else out.push({from:T('meet.route_from_bottom_station',{lift:l.name}),html:[liftChip(l.name),dot].join(sep)});});
    return out.filter((r,i)=>out.findIndex(q=>q.html===r.html)===i).slice(0,4);}
  const pad=n=>String(n).padStart(2,'0');
  function link(){return location.origin+location.pathname+`?utm_medium=share#meet/${S.sid}/${S.time.replace(':','')}/${S.day.replace(/-/g,'')}`;}
  function dayLbl(){return (DAYS.find(d=>d[0]===S.day)||[0,dayName(S.day)])[1];}
  function message(){const s=byId[S.sid];return T('meet.share_message',{place:s.name,day:dayLbl(),time:S.time,where:s.where,alt:s.h?T('meet.share_alt_suffix',{n:s.h.toLocaleString('en-US')}):'',link:link()});}
  function countdown(){ // in Gudauri time (UTC+4)
    const [y,mo,d]=S.day.split('-').map(Number),[hh,mm]=S.time.split(':').map(Number);
    const t=Date.UTC(y,mo-1,d,hh-4,mm),diff=(t-Date.now())/6e4;
    if(diff<0)return ['',T('meet.countdown_passed'),''];if(diff<24*60)return diff<60?slots('meet.countdown_minutes',{n:Math.round(diff)}):slots('meet.countdown_hours',{hm:Math.floor(diff/60)+':'+pad(Math.round(diff%60))});
    return slots('meet.countdown_days',{n:Math.ceil(diff/1440)});}
  function render(){
    const s=byId[S.sid],none=!s;applyVB();
    // the empty state, and everything that only makes sense once a spot is picked
    document.getElementById('meetCallout').hidden=none;document.getElementById('meetClear').hidden=none;
    document.getElementById('meetEmpty').hidden=!none;document.getElementById('meetCard').hidden=none;document.getElementById('meetCardX').hidden=none;
    document.getElementById('meetRoutesSec').hidden=none;document.getElementById('meetShareBox').hidden=none;
    const hint=document.getElementById('meetHint');hint.classList.toggle('ask',none);
    hint.textContent=none?T('meet.hint_pick',{n:st.length}):T('meet.hint_clear');
    if(none){ui.querySelectorAll('[data-pre]').forEach(b=>b.setAttribute('aria-pressed','false'));
      ui.querySelectorAll('[data-day]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.day===S.day)));
      ui.querySelectorAll('[data-time]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.time===S.time)));
      if(location.hash.startsWith('#meet')&&location.hash!=='#meet')history.replaceState(null,'','#meet');return;}
    const c=document.getElementById('meetCallout');c.querySelector('b').textContent=s.name;c.querySelector('.mc-alt').textContent=s.h?T('common.unit_m',{n:s.h.toLocaleString('en-US')}):'';c.querySelector('.mc-where').textContent=s.where;
    ui.querySelectorAll('[data-day]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.day===S.day)));
    ui.querySelectorAll('[data-time]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.time===S.time)));
    ui.querySelectorAll('[data-pre]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.pre===S.preset)));
    const card=document.getElementById('meetCard'),[c1,c2,c3]=countdown();
    card.querySelector('[data-f="name"]').textContent=s.name;card.querySelector('[data-f="time"]').textContent=S.time;
    card.querySelector('[data-f="where"]').textContent=s.where;card.querySelector('[data-f="alt"]').textContent=s.h?T('common.unit_m',{n:s.h.toLocaleString('en-US')}):'—';
    card.querySelector('[data-f="day"]').textContent=dayLbl();
    card.querySelector('[data-f="c1"]').textContent=c1;card.querySelector('[data-f="c2"]').textContent=c2;card.querySelector('[data-f="c3"]').textContent=c3;
    const R=routes(s);document.getElementById('meetRoutes').innerHTML=R.length?R.map(r=>`<li><small>${esc(r.from)}</small><div class="rt">${r.html}</div></li>`).join(''):`<li class="hint">${E('meet.routes_empty')}</li>`;
    document.getElementById('meetWa').href='https://wa.me/?text='+encodeURIComponent(message());
    const want=`#meet/${S.sid}/${S.time.replace(':','')}/${S.day.replace(/-/g,'')}`;if(location.hash.startsWith('#meet')&&location.hash!==want)history.replaceState(null,'',want);
  }
  // the countdown keeps going while the page is open (S-13)
  setInterval(()=>{if(document.hidden||document.getElementById('meetPage').hidden||!byId[S.sid])return;
    const card=document.getElementById('meetCard'),c=countdown();['c1','c2','c3'].forEach((f,i)=>{card.querySelector(`[data-f="${f}"]`).textContent=c[i];});},30000);
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
    // the fonts of the language (FT1): the same tokens as the page, so Russian and Georgian are not drawn in Karantina (S-15)
    const cs=getComputedStyle(document.documentElement),DISP=cs.getPropertyValue('--f-display').trim()||'"Karantina","Arial Narrow",sans-serif',BODY=cs.getPropertyValue('--f-body').trim()||'"IBM Plex Sans Hebrew",sans-serif';
    const fit=(t,max,px)=>{x.font=`700 ${px}px ${DISP}`;while(px>60&&x.measureText(t).width>max){px-=6;x.font=`700 ${px}px ${DISP}`;}return px;};
    x.direction='ltr';x.textAlign='left';
    const tw=Math.min(300,(fit(S.time,300,150),x.measureText(S.time).width)+50);
    const fs=fit(s.name,W-60-tw-30-110-40,150),nw=x.measureText(s.name).width+110;
    x.fillStyle='#1F5FC4';x.beginPath();x.moveTo(60,H-170);x.lineTo(110,H-270);x.lineTo(60+nw,H-270);x.lineTo(60+nw,H-70);x.lineTo(110,H-70);x.closePath();x.fill();
    x.fillStyle='#fff';x.font=`700 ${fs}px ${DISP}`;x.fillText(s.name,130,H-112);
    x.fillStyle='#F4B942';x.fillRect(W-60-tw,H-270,tw,200);fit(S.time,tw-40,150);x.fillStyle='#13233A';x.textAlign='center';x.fillText(S.time,W-60-tw/2,H-112);
    x.direction=I18N.ltr?'ltr':'rtl';x.textAlign='right';x.fillStyle='#fff';x.font=`600 40px ${BODY}`;x.fillText(T('meet.share_image_card_label',{day:dayLbl()}),W-60,H-330);
    x.fillStyle='#13233A';x.fillRect(W-420,40,360,70);x.fillStyle='#fff';x.font=`700 34px ${BODY}`;x.fillText(T('common.site_name'),W-90,88);
    return new Promise(r=>cv.toBlob(r,'image/png'));
  }
  document.getElementById('meetShare').addEventListener('click',async e=>{const b=e.currentTarget,s=byId[S.sid];
    try{const blob=await image(),file=new File([blob],'meet.png',{type:'image/png'});
      if(navigator.canShare&&navigator.canShare({files:[file]})){await navigator.share({files:[file],text:message(),title:T('meet.share_title',{place:s.name})});track('meet_share',{method:'image'});return;}
      if(navigator.share){await navigator.share({text:message(),title:T('meet.share_title',{place:s.name})});track('meet_share',{method:'image'});return;}
      const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='meet-'+s.name.replace(/\W+/g,'-')+'.png';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),4000);track('meet_share',{method:'image'});
    }catch(err){}});
  document.getElementById('meetCopy').addEventListener('click',e=>{const b=e.currentTarget;if(!navigator.clipboard)return;navigator.clipboard.writeText(link()).then(()=>{track('meet_share',{method:'copy'});b.textContent=T('common.link_copied');setTimeout(()=>{b.textContent=T('meet.copy_link');},2200);}).catch(()=>{});});
  document.getElementById('meetWa').addEventListener('click',()=>track('meet_share',{method:'whatsapp'}));
  document.getElementById('meetOnMap').addEventListener('click',()=>{const s=byId[S.sid];location.hash='#map';requestAnimationFrame(()=>requestAnimationFrame(()=>{showLift(s.ends[0].l.id);if(view==='3d'&&v3)v3.focusLift(s.ends[0].l.id);else focusOn([s.ends[0].l.g]);}));});
  let shown=false;
  function open(arg){ // arg: "<station>/<HHMM>/<YYYYMMDD>" from a shared link, or empty
    // your trip may have changed since the last visit
    const was=DAYS.map(d=>d[0]).join();DAYS=skiDays();if(DAYS.map(d=>d[0]).join()!==was){drawDays();if(!DAYS.some(d=>d[0]===S.day))S.day=DAYS[0][0];}
    if(arg){const [sid,t,d]=arg.split('/');if(byId[sid]){S.sid=sid;if(!shown)track('meet_link_open');}if(/^\d{4}$/.test(t||''))S.time=t.slice(0,2)+':'+t.slice(2);if(/^\d{8}$/.test(d||''))S.day=`${d.slice(0,4)}-${d.slice(4,6)}-${d.slice(6)}`;
      ui.querySelector('#meetTime').value=S.time;}
    const s=byId[S.sid];requestAnimationFrame(()=>{render();if(!shown||arg){shown=true;if(s)goTo(s.x,s.y,1800,0);else fitAll(0);}if(arg&&s)document.getElementById('meetCard').scrollIntoView({block:'center'});});
  }
  ui.querySelector('#meetTime').value=S.time;
  return {open,station:id=>byId[id]&&byId[id].name,current:()=>S.sid?{sid:S.sid,day:S.day,time:S.time}:null};
})();

// the games page: fresh snow on top of every game sign (round 8); js/snow.js draws it to each sign's width
document.querySelectorAll('.games-list .game-card').forEach((a,i)=>{a.dataset.snow=i+7;a.dataset.snowArrow=30;});SNOW.scan(document.getElementById('gamesPage'));

// ---- lift status (design round 3, S1 to S3). Source: /api/status, a Vercel function that reads the MTA status page
// (to be written when the page works again, in December). Format:
// {updated:"ISO time", lifts:{"<lift name>":{open:true|false, reason?:"wind"}}, pistes:{"<run key>":{open:true|false}}}
// No data, or data older than 30 minutes: "no current information", and the map stays as it is. We never guess.
const LSTAT=(function(){
  let data=null,forMe=false;const STALE=30*6e4;
  const names=mainLifts.filter(l=>l.name&&l.status!=='inactive').map(l=>l.name);
  const fresh=()=>data&&data.updated&&Date.now()-Date.parse(data.updated)<STALE;
  const inSeason=()=>[11,0,1,2,3].includes(new Date().getMonth()); // December to April
  const isOpen=n=>fresh()&&data.lifts&&data.lifts[n]?!!data.lifts[n].open:null;
  const runOpen=p=>{if(!fresh())return null;const r=data.pistes&&data.pistes[p.key];if(r&&!r.open)return false;
    const up=p.fromLifts.filter(n=>data.lifts&&data.lifts[n]);return up.length?up.some(n=>data.lifts[n].open):(r?!!r.open:null);}; // open for me: the run and a lift up to it
  const REASON=Object.fromEntries(['wind','weather','maintenance','season'].map(k=>[k,T('status.reason_'+k)]));
  const ago=t=>{const m=Math.round((Date.now()-Date.parse(t))/6e4);return m<1?T('status.ago_now'):m<60?T('status.ago_minutes',{n:m}):T('status.ago_hours',{n:Math.round(m/60)});};
  function block(){
    if(!fresh()){const season=inSeason();
      return `<section class="lstat" aria-label="${E('status.heading_lift_status')}"><h3>${E(season?'status.no_recent_data':'status.mountain_asleep')}</h3>
      <p class="lead">${E(season?'status.lead_in_season':'status.lead_off_season')}</p>
      <div class="lstat-post">${names.slice(0,6).map((n,i)=>`<div class="lsign s${i%2}" data-snow="${i}" data-snow-arrow="18" data-snow-low><div class="ls-face"><b dir="ltr">${esc(n)}</b><span>?</span></div></div>`).join('')}</div>
      <p class="hint"><b>${E('status.no_guessing_bold')}</b> ${E('status.no_guessing_text')}</p></section>`;}
    const open=names.filter(n=>isOpen(n)).length;
    let prev=null;try{prev=JSON.parse(localStorage.getItem('gud-lstat')||'null');}catch(e){}
    const changes=prev?names.filter(n=>prev[n]!==undefined&&prev[n]!==isOpen(n)).map(n=>T(isOpen(n)?'status.change_opened':'status.change_closed',{lift:n})):[];
    try{localStorage.setItem('gud-lstat',JSON.stringify(Object.fromEntries(names.map(n=>[n,isOpen(n)]))));}catch(e){}
    return `<section class="lstat" aria-label="${E('status.heading_lift_status')}"><h3>${E('status.heading_lift_status')}</h3>
      ${changes.length?`<ul class="lstat-changes">${changes.map(c=>`<li>${esc(c)}</li>`).join('')}</ul>`:''}
      <div class="board-dep" role="table" aria-label="${E('status.board_aria')}"><div class="bd-row bd-head" role="row"><span role="columnheader">${E('status.board_col_lift')}</span><span role="columnheader">${E('status.board_col_state')}</span><span role="columnheader">${E('status.board_col_note')}</span></div>
      ${names.map((n,i)=>{const o=isOpen(n),r=data.lifts[n]&&data.lifts[n].reason;return `<div class="bd-row" role="row" style="--i:${i}"><span role="cell"><bdi>${esc(n)}</bdi></span><span role="cell" class="${o?'bd-open':o===false?'bd-closed':''}">${o?E('status.lift_open'):o===false?E('status.lift_closed'):'—'}</span><span role="cell">${r?esc(REASON[r]||r):''}</span></div>`;}).join('')}</div>
      <button type="button" class="fchip lstat-me" data-forme aria-pressed="${forMe}">${E('status.only_open_for_me')}</button>
      <p class="hint">${E('status.board_hint',{open,total:names.length})}</p></section>`;
  }
  // the map: closed lifts grey and dashed, chairs moving on open ones, and "only what's open for me"
  const gChairs=mk('g',{class:'chairs','aria-hidden':'true'});svg.insertBefore(gChairs,mainLbl);
  function applyMap(){
    const bar=document.getElementById('mstat');
    gChairs.innerHTML='';svg.classList.toggle('forme',forMe&&fresh());
    svg.querySelectorAll('g.lg[data-lid]').forEach(g=>g.classList.remove('closed'));
    if(!fresh()){bar.innerHTML=`<span class="ms-dot"></span><span class="ms-txt">${E(data?'status.bar_no_recent':'status.bar_no_data_yet')}</span>`;bar.dataset.state='none';
      D.pistes.forEach(p=>(pisteEls[p.key]||[]).forEach(e=>e.classList.remove('shut')));if(v3&&v3.liftState){v3.liftState(null);v3.runState(null,false);}return;}
    const open=names.filter(n=>isOpen(n)).length;
    // on a phone the line breaks at the dot, between how many are open and when it was updated
    const parts=H('status.bar_summary',{ago:ago(data.updated)},{open:`<b class="num">${open}</b>`,total:`<b class="num">${names.length}</b>`}).split(' · ');
    bar.dataset.state='live';bar.innerHTML=`<span class="ms-dot"></span><span class="ms-txt">${parts.map(x=>`<span class="ms-part">${x}</span>`).join(' · ')}</span>`;
    mainLifts.forEach(l=>{const g=svg.querySelector(`g.lg[data-lid="${l.id}"]`);const o=l.name?isOpen(l.name):null;if(g)g.classList.toggle('closed',o===false);
      if(o&&!reduceMotion()){const d=pathD(l.g),len=l.len||1000,dur=Math.max(8,len/60);
        for(let k=0;k<3;k++){const c=mk('circle',{r:12,class:'chair'},gChairs);const am=mk('animateMotion',{dur:dur+'s',begin:`-${(dur*k/3).toFixed(1)}s`,repeatCount:'indefinite',path:d},c);}}});
    D.pistes.forEach(p=>{const o=runOpen(p);(pisteEls[p.key]||[]).forEach(e=>e.classList.toggle('shut',o===false));});
    // in 3D too (S-19): closed dashed, chairs on the open lifts, and "only what's open for me"
    if(v3&&v3.liftState){v3.liftState(Object.fromEntries(mainLifts.filter(l=>l.name).map(l=>[l.id,isOpen(l.name)])));v3.runState(Object.fromEntries(D.pistes.map(p=>[p.key,runOpen(p)])),forMe);}
    if(mapReady)apply();
  }
  // the season board on the home page (while there is no trip of your own): S3's snowy sign with no report, which in
  // season says there is no current information rather than that the mountain sleeps; with a fresh report the snow is
  // gone and it says how many lifts are open, in S1's words (as in the app, 13.4)
  function applyHome(){
    const el=document.getElementById('seasonBoard');if(!el)return;
    const em=el.querySelector(':scope>em'),b=el.querySelector(':scope>b'),sp=el.querySelector(':scope>span');
    if(fresh()){const open=names.filter(n=>isOpen(n)).length;el.dataset.state='live';
      b.innerHTML=`<span class="ms-dot" aria-hidden="true"></span>${E('status.heading_lift_status')}`;
      // the line breaks at the dot, between how many are open and when it was updated, as on the map
      const parts=H('status.bar_summary',{ago:ago(data.updated)},{open:`<b class="num">${open}</b>`,total:`<b class="num">${names.length}</b>`}).split(' · ');
      sp.innerHTML=parts.map(x=>`<span class="ms-part">${x}</span>`).join(' · ');return;}
    const s=inSeason();el.dataset.state=s?'season':'off';
    em.textContent=T('status.heading_lift_status');b.textContent=T(s?'status.no_recent_data':'status.mountain_asleep');
    sp.textContent=T(s?'status.lead_in_season':'status.lead_off_season');
  }
  applyHome();
  panel.addEventListener('click',e=>{const b=e.target.closest('[data-forme]');if(!b)return;forMe=!forMe;b.setAttribute('aria-pressed',String(forMe));applyMap();track('status_only_open',{on:forMe});});
  // status_view: once per visit to the map; on the first visit it waits for the first answer from /api/status
  let loaded=false,pend=false;
  function viewed(){if(!loaded){pend=true;return;}const f=fresh();track('status_view',f?{state:'fresh',open:names.filter(n=>isOpen(n)).length,total:names.length}:{state:data?'stale':'none'});}
  function load(){return fetch('api/status',{cache:'no-store'}).then(r=>r.ok&&/json/.test(r.headers.get('content-type')||'')?r.json():null).catch(()=>null)
    .then(j=>{j=j&&j.updated&&j.lifts?j:null;
      // the last report stays in this browser, as in the app (S-29): with no answer it is shown while it is fresh
      try{if(j)localStorage.setItem('gud-lstat-last',JSON.stringify(j));else j=JSON.parse(localStorage.getItem('gud-lstat-last')||'null');}catch(e){}
      data=j&&j.updated&&j.lifts?j:null;loaded=true;if(pend){pend=false;viewed();}applyMap();applyHome();if(!current&&!panel.querySelector('.back'))overview();});}
  setInterval(load,5*6e4);
  return {block,load,applyMap,viewed};
})();
LSTAT.load();

// flight ticket: one shared doc (trip/flight), editable by Contributors
const fmtDate=iso=>{const[y,m,d]=iso.split('-');return +d+'.'+ +m+'.'+y;};
function renderTicket(){
  const t=MYTRIP.get(),stack=document.getElementById('bpStack');
  stack.hidden=!t;document.getElementById('bpHint').hidden=!t;
  ['bpEmpty','tripNote','seasonBoard'].forEach(id=>{document.getElementById(id).hidden=!!t;});
  if(!t)return;
  const short=iso=>{const[,m,d]=iso.split('-');return +d+'.'+ +m;};
  const city=code=>{if(!code)return '';const k='ticket.city_'+code.toLowerCase(),w=T(k);return w===k?'':w;};
  const o=t.out,r=t.ret&&t.ret.date?t.ret:null,sd=MYTRIP.ski(t);
  const skiCount=sd?Math.round((new Date(sd.to)-new Date(sd.from))/864e5)+1:0;
  const [skiPre,,skiPost]=slots('ticket.stub_ski_first',{n:skiCount||0});
  const legs={out:{...o,fromCode:o.from,toCode:o.to,other:r&&r.date},ret:r&&{...r,fromCode:o.to,toCode:o.from,other:o.date}};
  const fill=(card,f)=>{
    const leg=card.dataset.leg;
    card.querySelector('.bp-strip > span').innerHTML=H(leg==='ret'?'ticket.strip_mine_return':'ticket.strip_mine_out',{},{note:'<span data-f="note"></span>'});
    // the second row of your own pass: you, the other leg's date, and the ski days (no airline and no baggage to show)
    const lab=(k,key)=>{const el=card.querySelector(`[data-f="${k}"]`),l=el&&el.parentElement.querySelector('small');if(l){l.removeAttribute('data-i18n');l.textContent=T(key);}};
    lab('pax','ticket.pax_one');lab('baggage',leg==='ret'?'ticket.leg_out':'ticket.leg_back');
    card.querySelectorAll('[data-f]').forEach(el=>{
      const k=el.dataset.f,v={from:city(f.fromCode),to:city(f.toCode),dateShort:f.date&&short(f.date),date:f.date&&fmtDate(f.date),pax:T('ticket.pax_guest'),
        baggage:f.other?short(f.other):'',pair:f.fromCode&&f.toCode?f.fromCode+' › '+f.toCode:'',note:f.departs&&+f.departs.split(':')[0]<6?T('ticket.note_overnight'):'',
        skiCount:skiCount?String(skiCount):'',skiRange:sd?short(sd.from).split('.')[0]+'–'+short(sd.to):'',skiPre:skiCount?skiPre:'',skiPost:skiCount?skiPost:''}[k]??f[k];
      if(k==='note'||k==='skiPre'||k==='skiPost'||k==='airline'){el.textContent=v||'';return;}
      el.textContent=v||(k==='from'?T('ticket.from_placeholder'):k==='to'?T('ticket.to_placeholder'):'—');
      if(k==='from'||k==='to')el.classList.toggle('ph',!v);
    });
  };
  fill(document.querySelector('.bp[data-leg="out"]'),legs.out);
  const rc=document.querySelector('.bp[data-leg="ret"]');
  if(r)fill(rc,legs.ret);
  rc.hidden=!r;document.getElementById('bpHintSwap').hidden=!r;
  if(!r&&rc.classList.contains('is-front')){rc.classList.replace('is-front','is-back');document.querySelector('.bp[data-leg="out"]').classList.replace('is-back','is-front');}
  document.querySelectorAll('.bp-ridge').forEach(svg=>{
    /* the "barcode" is the ridge above New Gudauri drawn as bars: it is the mountain, not a code anyone could scan */
    const h=[.30,.42,.55,.48,.62,.80,.70,.58,.66,.92,1,.86,.74,.60,.68,.78,.64,.50,.44,.56,.70,.62,.48,.36,.42,.30,.24,.34,.28,.20,.26,.18],w=80/h.length;
    svg.innerHTML=h.map((v,i)=>`<rect x="${(i*w).toFixed(1)}" y="${(28-v*28).toFixed(1)}" width="${i%3===0?2:i%2?1.2:.7}" height="${(v*28).toFixed(1)}"/>`).join('');
  });
  // the passenger is the account (round 12, P1 to P7)
  if(window.ACCOUNT)ACCOUNT.paintPass();
}
// the trip form (#trip, round 12 W2): the browser's own date and time pickers, the destination starts as Tbilisi,
// and the return is the outbound the other way round
const TRIPFORM=(()=>{
  const f=document.getElementById('tripForm'),AIR=['TLV','TBS','KUT'],q=n=>f.elements[n],err=document.getElementById('tfErr');
  let manual=false;
  const city=code=>{const k='ticket.city_'+code.toLowerCase(),w=T(k);return w===k?code:code+' · '+w;};
  ['ofr','oto'].forEach(n=>{q(n).innerHTML=AIR.map(c=>`<option value="${c}">${esc(city(c))}</option>`).join('')+`<option value="">${esc(T('trip.other_airport'))}</option>`;});
  q('of').placeholder=q('rf').placeholder=T('trip.number_hint',{example:'\u20686H 897\u2069'});
  const code=n=>q(n).value||q(n+'x').value.trim().toUpperCase();
  const read=()=>({out:{date:q('od').value,flight:q('of').value.trim().toUpperCase(),from:code('ofr'),to:code('oto'),departs:q('odp').value,arrives:q('oar').value},
    ret:q('rd').value?{date:q('rd').value,flight:q('rf').value.trim().toUpperCase(),departs:q('rdp').value,arrives:q('rar').value}:null,
    ski:manual&&q('sf').value&&q('sl').value?{from:q('sf').value,to:q('sl').value}:null});
  // dates that have passed cannot be picked (a stored one stays, so a trip under way can still be edited)
  const today=()=>new Date().toLocaleDateString('sv');
  const paint=()=>{const t=read(),d0=today(),was=MYTRIP.get();
    q('od').min=was&&was.out.date<d0?was.out.date:d0;q('rd').min=t.out.date||d0;q('sf').min=q('sl').min=t.out.date||'';
    document.getElementById('tfOther').hidden=!!(q('ofr').value&&q('oto').value);
    q('ofrx').parentElement.hidden=!!q('ofr').value;q('otox').parentElement.hidden=!!q('oto').value;
    const br=document.getElementById('tfBackRoute');br.textContent=t.out.from&&t.out.to?T('trip.back_auto',{route:t.out.to+' › '+t.out.from}):T('trip.back_optional');br.dir='auto';
    const sd=manual?t.ski:MYTRIP.skiAuto(t.out,t.ret),b=document.getElementById('tfSki');
    document.getElementById('tfSkiLbl').textContent=T(manual?'trip.ski_manual':'trip.ski_computed');
    document.getElementById('tfSkiBtn').textContent=T(manual?'trip.ski_auto':'trip.ski_change');
    document.getElementById('tfSkiManual').hidden=!manual;
    if(sd){const n=Math.round((new Date(sd.to)-new Date(sd.from))/864e5)+1,sh=iso=>{const[,m,d]=iso.split('-');return +d+'.'+ +m;};
      b.innerHTML=`<span dir="ltr">${sh(sd.from)===sh(sd.to)?sh(sd.from):sh(sd.from).split('.')[0]+'–'+sh(sd.to)}</span> · ${esc(T('trip.ski_count',{n}))}`;}
    else b.textContent=T('trip.ski_unknown');};
  const open=()=>{const t=MYTRIP.get(),o=t&&t.out||{},r=t&&t.ret||{};err.hidden=true;
    q('od').value=o.date||'';q('of').value=o.flight||'';q('odp').value=o.departs||'';q('oar').value=o.arrives||'';
    const pick=(n,c,def)=>{const v=c||def;q(n).value=AIR.includes(v)?v:'';q(n+'x').value=AIR.includes(v)?'':v;};
    pick('ofr',o.from,'TLV');pick('oto',o.to,'TBS');
    q('rd').value=r.date||'';q('rf').value=r.flight||'';q('rdp').value=r.departs||'';q('rar').value=r.arrives||'';
    manual=!!(t&&t.ski);q('sf').value=manual?t.ski.from:'';q('sl').value=manual?t.ski.to:'';
    const del=document.getElementById('tfDelete');del.hidden=!t;delArm=0;del.textContent=T('trip.delete');f.querySelectorAll('[aria-invalid]').forEach(e=>e.removeAttribute('aria-invalid'));paint();};
  const fail=(n,key)=>{err.textContent=T(key);err.hidden=false;if(n){q(n).setAttribute('aria-invalid','true');q(n).focus();}};
  f.addEventListener('input',e=>{if(e.target.name)e.target.removeAttribute('aria-invalid');paint();});
  f.addEventListener('change',paint);
  document.getElementById('tfSkiBtn').addEventListener('click',()=>{manual=!manual;if(manual&&!q('sf').value){const a=MYTRIP.skiAuto(read().out,read().ret);if(a){q('sf').value=a.from;q('sl').value=a.to;}}paint();});
  let delArm=0;
  document.getElementById('tfDelete').addEventListener('click',e=>{
    if(Date.now()-delArm>6000){delArm=Date.now();e.currentTarget.textContent=T('trip.delete_confirm');return;} // two taps, as in the app
    const was=MYTRIP.get();MYTRIP.set(null);window.ACCOUNT&&ACCOUNT.tripDeleted(was&&was.sid);renderTicket();countdown(document.documentElement.dataset.theme==='dark');location.hash='#home';});
  f.addEventListener('submit',e=>{e.preventDefault();err.hidden=true;const t=read();
    if(!MYTRIP.ISO.test(t.out.date))return fail('od','trip.need_date');
    const old=MYTRIP.get(),d0=today();
    if(t.out.date<d0&&!(old&&old.out.date===t.out.date))return fail('od','trip.past_date');
    if(t.ret&&t.ret.date<d0&&!(old&&old.ret&&old.ret.date===t.ret.date))return fail('rd','trip.past_date');
    for(const n of ['ofr','oto'])if(!q(n).value&&!/^[A-Z]{3}$/.test(q(n+'x').value.trim().toUpperCase()))return fail(n+'x','trip.bad_code');
    if(t.ret&&t.ret.date<t.out.date)return fail('rd','trip.bad_order');
    if(t.ski&&t.ski.to<t.ski.from)return fail('sl','trip.bad_order');
    const was=MYTRIP.get();MYTRIP.set({v:1,...t,...(was&&was.sid?{sid:was.sid}:{})});window.ACCOUNT&&ACCOUNT.tripSaved();renderTicket();countdown(document.documentElement.dataset.theme==='dark');location.hash='#home';});
  return {open};})();
// about and settings (#about): sound and vibration for the whole site and the games, and clearing the game records
(function(){const P=window.GUD_PREFS||{sound:true,haptics:true};
  const paint=()=>document.querySelectorAll('[data-pref]').forEach(b=>b.setAttribute('aria-pressed',String(!!P[b.dataset.pref])));paint();
  document.querySelectorAll('[data-pref]').forEach(b=>b.addEventListener('click',()=>{const k=b.dataset.pref;window.setGudPref(k,!P[k]);paint();if(k==='sound'||k==='haptics')track('settings_change',{setting:k,on:!!P[k]});
    if(k==='haptics'&&P.haptics)try{navigator.vibrate&&navigator.vibrate(20);}catch(e){}
    if(k==='sound'||k==='haptics')document.getElementById('abResetTxt').textContent=T('about.setting_saved');}));
  let armed=false;const r=document.getElementById('abReset'),rt=document.getElementById('abResetTxt');
  r.addEventListener('click',()=>{if(!armed){armed=true;r.classList.add('armed');rt.textContent=T('about.reset_confirm');setTimeout(()=>{armed=false;r.classList.remove('armed');},4000);return;}
    try{Object.keys(localStorage).filter(k=>(window.GUD_GAME_KEYS||[]).some(p=>k.startsWith(p))).forEach(k=>localStorage.removeItem(k));}catch(e){}
    armed=false;r.classList.remove('armed');rt.textContent=T('about.reset_done');track('best_reset');});})();
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
      track('ticket_swap',{to:front().dataset.leg==='out'?'ret':'out'});busy=true;play('slide',0,.7,6000);play('land',.42,.75,5000);buzz(8);
      stack.classList.add('shuffle');
      setTimeout(()=>{stack.querySelectorAll('.bp').forEach(c=>{const f=c.classList.contains('is-front');c.classList.toggle('is-front',!f);c.classList.toggle('is-back',f);});stack.classList.remove('shuffle');},250);
      setTimeout(()=>buzz(12),460);
      setTimeout(()=>{busy=false;},640);
    }else if(stub&&stub.closest('.bp')===front()){
      track('ticket_tear',{leg:front().dataset.leg});busy=true;play('tear',0,.85,7500);buzz([6,30,6,30,6,30,6,30,6,30,6,90,24]);
      stub.classList.add('torn1');
      setTimeout(()=>{stub.classList.add('torn2');},540);
      setTimeout(()=>{stub.classList.remove('torn1','torn2');busy=false;},2600);
    }
  });
})();
renderTicket();
// the top bar on a narrower computer or in a longer language (a tablet, Russian, Georgian): the clock's caption goes
// first, then the words on the buttons and the countdown, then the clock, then the signs break into two lines, and last
// the site's name goes, until it fits without scrolling sideways (nothing in it shrinks, so too long shows as overflow)
(function(){
  const bar=document.querySelector('.topbar'),L=['tb-fit1','tb-fit2','tb-fit3','tb-fit4','tb-fit5'];
  const fit=()=>{bar.classList.remove(...L);if(getComputedStyle(bar).display==='none')return;
    for(const c of L){if(bar.scrollWidth<=bar.clientWidth+1)break;bar.classList.add(c);}};
  new ResizeObserver(fit).observe(bar);new MutationObserver(fit).observe(bar,{childList:true,subtree:true,characterData:true});
  if(document.fonts)document.fonts.ready.then(fit);fit();
})();
// the header of a page on a phone, with the language button (3.10.2026): the title keeps its words whole. First the button
// gives up its globe; then on the home page the clock's caption goes (and then the clock), and on the other pages the
// title gets a line of its own under the button and the link back
(function(){
  const L=['hd-fit1','hd-fit2','hd-fit3'];
  const broken=t=>{if(t.scrollWidth>t.clientWidth+1)return true; // wider than its box, or a word split over two lines
    const w=document.createTreeWalker(t,NodeFilter.SHOW_TEXT);
    for(let n;(n=w.nextNode());){const re=/\S+/g;let m;
      while((m=re.exec(n.textContent))){const r=document.createRange();r.setStart(n,m.index);r.setEnd(n,m.index+m[0].length);
        if(new Set([...r.getClientRects()].filter(q=>q.width>.5).map(q=>Math.round(q.top))).size>1)return true;}}
    return false;};
  const fit=h=>{h.classList.remove(...L);const t=h.querySelector(':scope>h1'),b=h.querySelector('.lang-btn');
    if(!t||!b||!b.offsetWidth)return; // the button shows in the headers on phones only
    for(const c of L){if(!broken(t))break;h.classList.add(c);}};
  const heads=[...document.querySelectorAll('.home-top,.mhead,.ac-head,.tf-head')];
  const ro=new ResizeObserver(es=>es.forEach(e=>{const h=e.target,w=Math.round(e.contentRect.width);if(w!==h._fitW){h._fitW=w;fit(h);}}));
  heads.forEach(h=>{ro.observe(h);const t=h.querySelector(':scope>h1');if(t)new MutationObserver(()=>fit(h)).observe(t,{childList:true,subtree:true,characterData:true});});
  if(document.fonts)document.fonts.ready.then(()=>heads.forEach(fit));
})();
// accounts and groups (js/account.js) work with these, and draw on the pages before the first route
if(window.ACCOUNT)ACCOUNT.start({MYTRIP,esc,MEET,renderTicket,countdown});
route();
overview();applyFilters();
// the app download sign: shown only when the file really is on the server
fetch('downloads/gudauri-2027.apk',{method:'HEAD'}).then(r=>{
  if(!r.ok||/text\/html/.test(r.headers.get('content-type')||''))return;
  const mb=+r.headers.get('content-length')/1048576;
  document.getElementById('appMeta').textContent=mb>0?T('home.app_download_meta_size',{size:mb.toFixed(mb<10?1:0)}):T('home.app_download');
  document.getElementById('appBoard').hidden=false;document.getElementById('appBoard').addEventListener('click',()=>track('app_download',{store:'apk',placement:'home'}));document.getElementById('appHow').hidden=false;
}).catch(()=>{});
document.getElementById('loading').hidden=true;
})().catch(e=>{console.error(e);const l=document.getElementById('loading');l.hidden=false;l.textContent=typeof T==='function'?T('home.load_error'):'Error';});

// twelve steps, from a flake to the king of Kazbek (2 → 4096 in the classic game)
const L=window.L||((k,he,v)=>he.replace(/\{(\w+)\}/g,(m,x)=>v&&x in v?v[x]:m));
// the names are read once the language file is in (start, at the bottom)
let NAMES=[];
const names=()=>[L('game.merge.name_1','פתית'),L('game.merge.name_2','כדור קטן'),L('game.merge.name_3','כדור'),L('game.merge.name_4','כדור גדול'),L('game.merge.name_5','ראש'),L('game.merge.name_6','בטן'),
  L('game.merge.name_7','גוף'),L('game.merge.name_8','איש שלג'),L('game.merge.name_9','עם כובע'),L('game.merge.name_10','עם צעיף'),L('game.merge.name_11','איש שלג ענק'),L('game.merge.name_12','מלך קזבק')];
const store={get(k,d){try{const v=localStorage.getItem('mg-'+k);return v==null?d:JSON.parse(v);}catch(e){return d;}},set(k,v){try{localStorage.setItem('mg-'+k,JSON.stringify(v));}catch(e){}}};
const $=id=>document.getElementById(id), B=$('board'), FX=$('fx'), fx=FX.getContext('2d');
const reduce=matchMedia('(prefers-reduced-motion: reduce)').matches, SLIDE=reduce?0:110;
let grid, score, best=store.get('best',0), maxLv, hist=[], undos, nid=1, tiles=new Map(), wonShown, busy=null, geo={};
/* ---------- geometry: every tile is placed with a transform, so every move animates ---------- */
function measure(){ const bw=B.clientWidth, gap=Math.round(bw*.025), cs=(bw-5*gap)/4; geo={bw,gap,cs}; B.style.setProperty('--cs',cs+'px'); B.style.setProperty('--slide',SLIDE+'ms');
  const d=Math.min(2,devicePixelRatio||1); FX.width=bw*d; FX.height=bw*d; fx.setTransform(d,0,0,d,0,0);
  B.querySelectorAll('.cell').forEach(el=>place(el,+el.dataset.r,+el.dataset.c)); tiles.forEach((el,id)=>{ const t=findTile(id); if(t) place(el,t.r,t.c); }); }
const X=c=>geo.bw-geo.gap-(c+1)*geo.cs-c*geo.gap, Y=r=>geo.gap+r*(geo.cs+geo.gap); // columns run right to left
function place(el,r,c){ el.style.transform=`translate(${X(c)}px,${Y(r)}px)`; }
function findTile(id){ for(const row of grid||[]) for(const t of row) if(t&&t.id===id) return t; return null; }
for(let r=0;r<4;r++)for(let c=0;c<4;c++){ const d=document.createElement('div'); d.className='cell'; d.dataset.r=r; d.dataset.c=c; B.insertBefore(d,FX); }
function ballSVG(lv){ // more snow and more snowman with every level
  if(lv<7){ const s=28+lv*8; return `<div class="ball" style="width:${s}%;height:${s}%;margin-bottom:14%"></div>`; }
  const hat=lv>=8, scarf=lv>=9, big=lv>=10, gold=lv>=11;
  return `<svg viewBox="0 0 100 100" aria-hidden="true">
   ${big?`<circle cx="50" cy="50" r="48" fill="${gold?'#F4B942':'#F4F8FB'}" opacity=".25"/>`:''}
   <circle cx="50" cy="74" r="22" fill="#EDF3F8" stroke="#B8CADB" stroke-width="2"/>
   <circle cx="50" cy="44" r="16" fill="#F4F8FB" stroke="#B8CADB" stroke-width="2"/>
   <circle cx="50" cy="20" r="12" fill="#fff" stroke="#B8CADB" stroke-width="2"/>
   <circle cx="45" cy="18" r="1.8" fill="#0D1522"/><circle cx="55" cy="18" r="1.8" fill="#0D1522"/>
   <path d="M50 21 L62 23 L50 24Z" fill="#F07A2E"/>
   <circle cx="50" cy="40" r="1.8" fill="#0D1522"/><circle cx="50" cy="48" r="1.8" fill="#0D1522"/>
   ${scarf?`<path d="M37 31 Q50 37 63 31 L63 35 Q50 41 37 35Z" fill="#D1342B"/><rect x="56" y="33" width="5" height="13" fill="#D1342B"/>`:''}
   ${hat&&!gold?`<rect x="40" y="2" width="20" height="9" fill="#0D1522"/><rect x="35" y="10" width="30" height="3" fill="#0D1522"/>`:''}
   ${gold?`<path d="M40 6 L44 0 L50 5 L56 0 L60 6Z" fill="#F4B942"/>`:''}
  </svg>`; }
function mk(t,cls){ const el=document.createElement('div'); el.className='tile'+(cls?' '+cls:''); el.innerHTML=`<div class="in">${ballSVG(t.lv)}<span class="lab">${NAMES[t.lv]}</span></div>`; place(el,t.r,t.c); B.insertBefore(el,FX); tiles.set(t.id,el); return el; }

/* ---------- sound: a soft crunch, higher for bigger merges ---------- */
let AC=null, soundOn=store.get('snd',true);
function ac(){ if(!soundOn) return null; if(!AC){ try{ AC=new (window.AudioContext||window.webkitAudioContext)(); }catch(_){ return null; } } if(AC.state==='suspended') AC.resume(); return AC; }
function crunch(lv,vol=.25){ const a=ac(); if(!a) return; const d=.09+lv*.01, n=Math.floor(a.sampleRate*d), b=a.createBuffer(1,n,a.sampleRate), ch=b.getChannelData(0);
  for(let i=0;i<n;i++){ const env=(1-i/n)**2; ch[i]=(Math.random()*2-1)*env*(Math.random()<.3?1:.35); } // grainy, like packed snow
  const s=a.createBufferSource(); s.buffer=b; const f=a.createBiquadFilter(); f.type='bandpass'; f.frequency.value=700+lv*260; f.Q.value=1.4; const g=a.createGain(); g.gain.value=vol; s.connect(f); f.connect(g); g.connect(a.destination); s.start();
  if(lv>=4){ const o=a.createOscillator(), og=a.createGain(); o.type='sine'; o.frequency.value=330*Math.pow(2,(lv-4)/6); og.gain.setValueAtTime(.08,a.currentTime); og.gain.exponentialRampToValueAtTime(.001,a.currentTime+.35); o.connect(og); og.connect(a.destination); o.start(); o.stop(a.currentTime+.36); } }
function swish(){ const a=ac(); if(!a) return; const n=Math.floor(a.sampleRate*.07), b=a.createBuffer(1,n,a.sampleRate), ch=b.getChannelData(0); for(let i=0;i<n;i++) ch[i]=(Math.random()*2-1)*Math.sin(Math.PI*i/n); const s=a.createBufferSource(); s.buffer=b; const f=a.createBiquadFilter(); f.type='highpass'; f.frequency.value=2500; const g=a.createGain(); g.gain.value=.07; s.connect(f); f.connect(g); g.connect(a.destination); s.start(); }
$('snd').textContent=soundOn?'🔊':'🔇'; $('snd').setAttribute('aria-pressed',soundOn);
$('snd').onclick=()=>{ soundOn=!soundOn; store.set('snd',soundOn); $('snd').textContent=soundOn?'🔊':'🔇'; $('snd').setAttribute('aria-pressed',soundOn); };
function buzz(p){ try{ navigator.vibrate&&navigator.vibrate(p); }catch(_){} }

/* ---------- snow puffs on the fx canvas ---------- */
let puffs=[], fxOn=false;
function puff(r,c,lv){ if(reduce) return; const cx0=X(c)+geo.cs/2, cy0=Y(r)+geo.cs/2, n=10+lv*3;
  for(let i=0;i<n;i++){ const a=Math.random()*Math.PI*2, v=(.6+Math.random())*geo.cs*(.9+lv*.06); puffs.push({x:cx0,y:cy0,vx:Math.cos(a)*v,vy:Math.sin(a)*v-geo.cs*.4,r:2+Math.random()*geo.cs*.05,life:.5+Math.random()*.3,gold:lv>=10&&Math.random()<.4}); }
  if(!fxOn){ fxOn=true; requestAnimationFrame(fxStep); } }
let fxLast=0;
function fxStep(now){ const dt=fxLast?Math.min(.05,(now-fxLast)/1000):.016; fxLast=now; fx.clearRect(0,0,geo.bw,geo.bw);
  for(const p of puffs){ p.vy+=geo.cs*2.2*dt; p.vx*=Math.exp(-3*dt); p.x+=p.vx*dt; p.y+=p.vy*dt; p.life-=dt; fx.globalAlpha=Math.max(0,Math.min(1,p.life*2.5)); fx.fillStyle=p.gold?'#F4B942':'#fff'; fx.beginPath(); fx.arc(p.x,p.y,p.r,0,7); fx.fill(); }
  fx.globalAlpha=1; puffs=puffs.filter(p=>p.life>0); if(puffs.length) requestAnimationFrame(fxStep); else { fxOn=false; fxLast=0; fx.clearRect(0,0,geo.bw,geo.bw); } }
function plus(r,c,n){ const el=document.createElement('div'); el.className='plus'; el.textContent='+'+n; el.style.left=(X(c)+geo.cs/2)+'px'; el.style.top=(Y(r)+geo.cs*.3)+'px'; B.appendChild(el); setTimeout(()=>el.remove(),850); }
function toast(lv){ const el=document.createElement('div'); el.className='toast'; el.innerHTML=`<span class="ic" style="--cs:56px">${ballSVG(lv).replace('margin-bottom:14%','')}</span><span><small>${L('game.merge.toast_new','חדש!')}</small><b>${NAMES[lv]}</b></span>`; document.body.appendChild(el); setTimeout(()=>el.remove(),1700); }

/* ---------- the game ---------- */
function emptyCells(){ const e=[]; for(let r=0;r<4;r++)for(let c=0;c<4;c++) if(!grid[r][c]) e.push([r,c]); return e; }
function spawn(){ const e=emptyCells(); if(!e.length) return; const [r,c]=e[Math.floor(Math.random()*e.length)]; const t={id:nid++,lv:Math.random()<.9?0:1,r,c}; grid[r][c]=t; mk(t,'new'); }
function snapshot(){ return {g:grid.map(row=>row.map(t=>t&&{...t})), score, maxLv}; }
function render(){ tiles.forEach(el=>el.remove()); tiles.clear(); for(const row of grid) for(const t of row) if(t) mk(t); hud(); }
function hud(){ $('score').textContent=score; $('best').textContent=best; $('undo').textContent=L('game.merge.undo','ביטול מהלך ({n})',{n:undos}); $('undo').disabled=!undos||!hist.length;
  const nx=Math.min(NAMES.length-1,maxLv+1); $('goalTxt').textContent= maxLv>=NAMES.length-1 ? L('game.merge.goal_top','הגעת לפסגה') : L('game.merge.goal_next','הבא: {name}',{name:NAMES[nx]}); $('goalBar').style.width=(maxLv/(NAMES.length-1)*100)+'%';
  const got=store.get('got',0); $('ladder').innerHTML=NAMES.map((n,i)=>`<span class="${i<=Math.max(got,maxLv)?'got':''} ${i===maxLv?'now':''}">${n}</span>`).join(''); }
function newGame(){ gameEnd(); finishAnim(); grid=[...Array(4)].map(()=>Array(4).fill(null)); score=0; maxLv=0; hist=[]; undos=3; wonShown=false; tiles.forEach(el=>el.remove()); tiles.clear(); spawn(); spawn(); hud(); $('over').hidden=true; store.set('save',null); }
function nudge(dir){ if(reduce) return; const c=['nudge-l','nudge-r','nudge-u','nudge-d'][dir]; B.classList.remove(c); void B.offsetWidth; B.classList.add(c); setTimeout(()=>B.classList.remove(c),200); }
// a new move while the last one is still sliding finishes the last one at once, so fast swipes never get lost
function finishAnim(){ if(!busy) return; clearTimeout(busy.timer); const b=busy; busy=null; b.done(); }
function move(dir){ // dir: 0 left 1 right 2 up 3 down, as on screen
  if(!grid||!$('over').hidden) return; finishAnim();
  const before=snapshot(); let moved=false; const merges=[];
  for(let i=0;i<4;i++){
    const line=[]; for(let j=0;j<4;j++){ const [r,c]=cellOf(dir,i,j); line.push(grid[r][c]); }
    const items=line.filter(Boolean), out=[]; let k=0;
    while(k<items.length){ const a=items[k], b=items[k+1];
      if(b && a.lv===b.lv && a.lv<NAMES.length-1){ out.push({a,b}); k+=2; } else { out.push({a}); k++; } }
    for(let j=0;j<4;j++){ const [r,c]=cellOf(dir,i,j); grid[r][c]=null; }
    out.forEach((o,j)=>{ const [r,c]=cellOf(dir,i,j);
      if(o.b){ moved=true; const t={id:nid++,lv:o.a.lv+1,r,c}; grid[r][c]=t; merges.push({t,from:[o.a,o.b]}); }
      else { if(o.a.r!==r||o.a.c!==c) moved=true; o.a.r=r; o.a.c=c; grid[r][c]=o.a; } });
  }
  if(!moved){ nudge(dir); return; }
  hist.push(before); if(hist.length>3) hist.shift(); swish();
  // 1. everything slides; the two halves of a merge slide into the same cell
  for(const row of grid) for(const t of row) if(t && tiles.has(t.id)) place(tiles.get(t.id),t.r,t.c);
  merges.forEach(m=>m.from.forEach(f=>{ const el=tiles.get(f.id); if(el){ el.classList.add('gone'); place(el,m.t.r,m.t.c); } }));
  // 2. when they arrive: the bigger ball pops in with a puff, the score counts up, a new flake appears
  const done=()=>{ let gained=0; gameStart();
    merges.forEach(m=>{ m.from.forEach(f=>{ const el=tiles.get(f.id); el&&el.remove(); tiles.delete(f.id); }); mk(m.t,'merged'); const pts=2**(m.t.lv+1); gained+=pts; plus(m.t.r,m.t.c,pts); puff(m.t.r,m.t.c,m.t.lv); });
    tiles.forEach(el=>{ if(el.classList.contains('new')) setTimeout(()=>el.classList.remove('new'),400); });
    if(merges.length){ const top=Math.max(...merges.map(m=>m.t.lv)); crunch(top, .18+.05*merges.length); buzz(merges.length>1?[10,30,10]:10);
      score+=gained; const sb=$('score'); sb.classList.remove('bump'); void sb.offsetWidth; sb.classList.add('bump'); if(score>best){ best=score; store.set('best',best); }
      if(top>maxLv){ maxLv=top; if(top>store.get('got',0)){ store.set('got',top); if(top>=3) toast(top); } } }
    spawn(); hud(); store.set('save',{g:grid,score,maxLv,undos});
    if(maxLv>=7 && !wonShown){ wonShown=true; setTimeout(()=>show(L('game.merge.won_title','איש שלג!'),L('game.merge.won_text','בנית איש שלג שלם. אפשר להמשיך לבנות: כובע, צעיף, ענק, ועד מלך קזבק.'),'continue'),500); }
    else if(!canMove()) noRoom(); };
  busy={done, timer:setTimeout(()=>{ busy=null; done(); }, SLIDE)}; }
// the board is full and nothing merges: the end (also after "continue" on the snowman, when that merge filled the board)
function noRoom(){ gameEnd(); setTimeout(()=>show(L('game.merge.over_title','אין מקום'),L('game.merge.over_text','הגעת עד: {name}. ניקוד {score}.',{name:NAMES[maxLv],score}),'again'),400); }
function cellOf(dir,i,j){ // j=0 is the side the tiles slide toward; columns run right to left
  if(dir===0) return [i,3-j]; if(dir===1) return [i,j]; if(dir===2) return [j,i]; return [3-j,i]; }
function canMove(){ if(emptyCells().length) return true; for(let r=0;r<4;r++)for(let c=0;c<4;c++){ const v=grid[r][c].lv; if(c<3&&grid[r][c+1].lv===v) return true; if(r<3&&grid[r+1][c].lv===v) return true; } return false; }
// act: 'continue' (close and keep playing) or 'again' (a new game); compared by key, never by the shown words
function show(t,p,act){ $('ovT').textContent=t; $('ovP').textContent=p; const ob=$('ovB'); ob.dataset.act=act;
  ob.textContent= act==='continue' ? L('game.merge.continue','להמשיך') : L('game.merge.again','עוד פעם');
  $('ovU').hidden = !(act==='again' && undos && hist.length); $('over').hidden=false; ob.onclick=()=>{ if(ob.dataset.act==='continue'){ $('over').hidden=true; if(!canMove()) noRoom(); } else newGame(); }; }
$('new').onclick=newGame;
$('ovU').onclick=()=>{ $('over').hidden=true; $('undo').onclick(); };
$('undo').onclick=()=>{ finishAnim(); if(!grid||!undos||!hist.length) return; const s=hist.pop(); grid=s.g; score=s.score; maxLv=s.maxLv; undos--; render(); store.set('save',{g:grid,score,maxLv,undos}); };
addEventListener('keydown',e=>{ const m={ArrowLeft:0,ArrowRight:1,ArrowUp:2,ArrowDown:3}[e.key]; if(m!=null){ e.preventDefault(); move(m); } });
// the swipe fires while the finger is still moving, anywhere on the screen
let sw=null;
addEventListener('pointerdown',e=>{ if(e.target.closest('button')||!$('over').hidden) return; sw={x:e.clientX,y:e.clientY,done:false}; });
addEventListener('pointermove',e=>{ if(!sw||sw.done) return; const dx=e.clientX-sw.x, dy=e.clientY-sw.y; if(Math.max(Math.abs(dx),Math.abs(dy))<18) return; sw.done=true; move(Math.abs(dx)>Math.abs(dy) ? (dx<0?0:1) : (dy<0?2:3)); });
addEventListener('pointerup',()=>{ sw=null; }); addEventListener('pointercancel',()=>{ sw=null; });
addEventListener('resize',measure);
measure();
// usage statistics (site/js/telemetry.js; absent in the design preview): a game runs from the first move in this visit until
// the board is full, a new game, or leaving the page (the board itself is kept). completed = a whole snowman; no level
let g0=0, gBest=0;
function gameStart(){ if(g0) return; g0=performance.now(); gBest=best; window.track&&track('game_start',{game:'merge'}); }
function gameEnd(){ if(!g0) return; window.track&&track('game_end',{game:'merge',score,seconds:Math.round((performance.now()-g0)/1000),completed:maxLv>=7,best:score>gBest}); g0=0; }
addEventListener('pagehide',gameEnd);
// the first board is drawn once the words of the page language are in
function start(){ NAMES=names();
  const sv=store.get('save',null);
  if(sv&&sv.g){ grid=sv.g; score=sv.score; maxLv=sv.maxLv; undos=sv.undos??3; wonShown=maxLv>=7; nid=1+Math.max(0,...grid.flat().filter(Boolean).map(t=>t.id)); render(); } else newGame(); }
(window.I18N?I18N.ready:Promise.resolve()).then(start);

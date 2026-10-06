/* words: L('key','Hebrew',vars) from ../../js/i18n.js on the site; here it falls back to the Hebrew */
const L=window.L||((k,he,v)=>he.replace(/\{(\w+)\}/g,(m,x)=>v&&x in v?v[x]:m));
const G=9.81, KMH=3.6;
const store={get(k,d){try{const v=localStorage.getItem('school-'+k);return v==null?d:JSON.parse(v);}catch(e){return d;}},set(k,v){try{localStorage.setItem('school-'+k,JSON.stringify(v));}catch(e){}}};
const $=id=>document.getElementById(id);
const reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
const RUNCOL={green:'#1B8A4C',blue:'#1F5FC4',red:'#D1342B',black:'#13233A'};
/* the words of a drawing go in a legend under it (round 13), so they wrap in every language instead of running off the edge */
function fig(svg){ const t=document.createElement('template'); t.innerHTML=svg; const el=t.content.firstElementChild; const items=[...el.querySelectorAll('text')].map(x=>[x.textContent,x.getAttribute('fill')]); el.querySelectorAll('text').forEach(x=>x.remove());
  return el.outerHTML+(items.length?'<ul class="fig-legend">'+items.map(([w,c])=>'<li><i style="background:'+c+'"></i><span style="color:'+(c==='#4B5A6F'?'var(--muted)':c)+'">'+w+'</span></li>').join('')+'</ul>':''); }
/* small drawings for the lesson cards (forward is up, as on the slope) */
const D={
 get wedge(){return `<svg width="220" height="120" viewBox="0 0 220 120"><g stroke="#13233A" stroke-width="10" stroke-linecap="square"><line x1="98" y1="14" x2="60" y2="106"/><line x1="122" y1="14" x2="160" y2="106"/></g><text x="110" y="10" font-size="11" text-anchor="middle" fill="#4B5A6F">${L('game.school.d_tips_close','קצוות קרובים')}</text><text x="110" y="118" font-size="11" text-anchor="middle" fill="#4B5A6F">${L('game.school.d_tails_apart','זנבות רחוקים')}</text></svg>`;},
 get outer(){return `<svg width="220" height="130" viewBox="0 0 220 130"><path d="M110 120 Q110 60 50 14" fill="none" stroke="#CBD5DF" stroke-width="3" stroke-dasharray="6 5"/><g transform="translate(104 70) rotate(-20)"><rect x="-20" y="-36" width="8" height="70" fill="#13233A"/><rect x="12" y="-36" width="8" height="70" fill="#D1342B"/></g><text x="150" y="60" font-size="12" fill="#D1342B" font-weight="700">${L('game.school.d_press_right','לוחצים על הימני')}</text><text x="30" y="40" font-size="12" fill="#4B5A6F">${L('game.school.d_turn_left','…ופונים שמאלה')}</text></svg>`;},
 get shape(){return `<svg width="220" height="130" viewBox="0 0 220 130"><path d="M110 124 C200 104 200 80 110 66 C20 52 20 28 110 6" fill="none" stroke="#1F5FC4" stroke-width="4"/><path d="M110 6 L110 124" stroke="#D1342B" stroke-width="3" stroke-dasharray="5 5"/><text x="118" y="120" font-size="11" fill="#D1342B">${L('game.school.d_straight_fast','ישר במורד: מאיץ')}</text><text x="150" y="60" font-size="11" fill="#1F5FC4">${L('game.school.d_across_slow','לרוחב: מאט')}</text></svg>`;},
 get rhythm(){return `<svg width="220" height="120" viewBox="0 0 220 120"><path d="M110 116 C170 100 170 80 110 64 C50 48 50 28 110 8" fill="none" stroke="#1F5FC4" stroke-width="4"/><g fill="#F4B942"><circle cx="152" cy="90" r="7"/><circle cx="68" cy="36" r="7"/></g><text x="10" y="112" font-size="11" fill="#4B5A6F">${L('game.school.d_tick','טיק… טאק… טיק…')}</text></svg>`;},
 get look(){return `<svg width="220" height="120" viewBox="0 0 220 120"><circle cx="110" cy="108" r="10" fill="#F07A2E"/><path d="M110 98 L150 14" stroke="#4B5A6F" stroke-width="2" stroke-dasharray="4 4"/><g fill="#1F5FC4"><rect x="80" y="70" width="4" height="16" opacity=".25"/><rect x="132" y="42" width="4" height="16"/><rect x="150" y="12" width="4" height="16"/></g><text x="10" y="40" font-size="11" fill="#4B5A6F">${L('game.school.d_eyes_far','העיניים: רחוק קדימה')}</text></svg>`;},
 get carve(){return `<svg width="220" height="120" viewBox="0 0 220 120"><path d="M30 110 Q110 70 190 10" fill="none" stroke="#13233A" stroke-width="2"/><path d="M40 114 Q120 74 200 14" fill="none" stroke="#13233A" stroke-width="2"/><path d="M20 40 Q50 50 80 70" fill="none" stroke="#9FB5CB" stroke-width="16" opacity=".6"/><text x="120" y="110" font-size="11" fill="#13233A">${L('game.school.d_carve_lines','קרווינג: שני קווים דקים')}</text><text x="10" y="24" font-size="11" fill="#4B5A6F">${L('game.school.d_skid_smear','החלקה: מריחה רחבה')}</text></svg>`;},
 get final(){return `<svg width="220" height="110" viewBox="0 0 220 110"><path d="M10 100 L80 30 L120 60 L160 14 L210 100Z" fill="#E9F1F8" stroke="#9FB5CB" stroke-width="2"/><path d="M150 26 C120 50 170 60 130 90" fill="none" stroke="#1F5FC4" stroke-width="3"/></svg>`;}};
/* seven lessons. Each has its own control (mode) and its own drill. y runs down the slope; gates are [x, y, width] */
const LS=[
 {get name(){return L('game.school.lesson1_title','עוצרים בפיצה');}, col:'green', mode:'wedge', slope:9, len:125, start:4, get d(){return D.wedge;},
  get rule(){return L('game.school.lesson1_rule','בפיצה, קצות המגלשיים קרובים והזנבות רחוקים. דוחפים את הזנבות החוצה עם העקבים. ככל שהפיצה רחבה יותר, הבלימה חזקה יותר. פיצה צרה רק מאטה.');},
  get ctrl(){return L('game.school.lesson1_controls','המגלשיים ישרים לבד. גוררים אצבע למעלה ולמטה, והסרגל בצד קובע את רוחב הפיצה: למטה רחבה, למעלה צרה.');},
  get drill(){return L('game.school.lesson1_drill','שלושה קווי עצירה: לעצור לגמרי ממש לפניהם, ואז לצמצם את הפיצה ולהמשיך. בין קו 2 לקו 3, רצועה ירוקה: להחזיק בה מהירות של 6 עד 10 קמ״ש, בעזרת רוחב הפיצה.');},
  get goals(){return [L('game.school.lesson1_goal1','לעצור לפני שלושת הקווים'),L('game.school.lesson1_goal2','להחזיק את המהירות ברצועה הירוקה'),L('game.school.lesson1_goal3','לעצור במרחק ממוצע של עד 1.5 מ׳')];}},
 {get name(){return L('game.school.lesson2_title','פונים בפיצה');}, col:'green', mode:'skis', slope:10, len:175, start:3, get d(){return D.outer;},
  gates:[[-5,28,6],[5,52,6],[-5,76,6],[5,100,6],[-5,124,6],[5,148,6]],
  get rule(){return L('game.school.lesson2_rule','כדי לפנות שמאלה, לוחצים על המגלש הימני, החיצוני. כדי לפנות ימינה, על השמאלי. זה הפוך ממה שנדמה בהתחלה, ולכן מתרגלים את זה.');},
  get ctrl(){return L('game.school.lesson2_controls','שני כפתורים: מגלש שמאל ומגלש ימין. לוחצים על אחד מהם, וזה המגלש שעליו כל המשקל.');},
  get drill(){return L('game.school.lesson2_drill','שישה שערים, פעם משמאל ופעם מימין. לפני כל שער המאמן שואל לאן פונים, ואתם בוחרים על איזה מגלש ללחוץ.');},
  get goals(){return [L('game.school.lesson2_goal1','לעבור לפחות 4 שערים'),L('game.school.lesson2_goal2','לעבור את כל 6 השערים'),L('game.school.lesson2_goal3','כל השערים, ולכל היותר לחיצה הפוכה אחת')];}},
 {get name(){return L('game.school.lesson3_title','הסיבוב הוא הבלם');}, col:'blue', mode:'steer', slope:15, len:285, start:3, get d(){return D.shape;}, demo:3.5,
  traps:[70,130,190,250], trapLimit:22,
  get rule(){return L('game.school.lesson3_rule','בלי פיצה, את המהירות שולטים בצורת הסיבוב. ישר במורד מאיץ. באלכסון מאיץ פחות. לרוחב המדרון מאט. כל סיבוב שמסתיים לרוחב הוא בלימה.');},
  get ctrl(){return L('game.school.lesson3_controls','המגלשיים פונים לכיוון האצבע. בהתחלה רק צופים: כמה שניות ישר במורד, כדי להרגיש כמה מהר זה מאיץ.');},
  get drill(){return L('game.school.lesson3_drill','ארבע מכמונות מהירות: לעבור כל אחת מתחת ל-22 קמ״ש, בלי פיצה. המאמן אומר כל רגע אם אתם מאיצים או מאטים.');},
  get goals(){return [L('game.school.lesson3_goal1','לעבור 3 מכמונות'),L('game.school.lesson3_goal2','לעבור את כל 4 המכמונות'),L('game.school.lesson3_goal3','כל המכמונות, ואף פעם לא מעל 35 קמ״ש')];}},
 {get name(){return L('game.school.lesson4_title','מקבילי, בקצב');}, col:'blue', mode:'rhythm', slope:14, len:240, start:5, beat:1.15, get d(){return D.rhythm;},
  get rule(){return L('game.school.lesson4_rule','גולש טוב מסובב בקצב קבוע: כל סיבוב מתחיל כשהקודם נגמר. מתחילים מוקדם, לא כשהעץ כבר מולך. המגלשיים מקבילים, ברוחב הירכיים.');},
  get ctrl(){return L('game.school.lesson4_controls','נגיעה בכל מקום במסך מתחילה סיבוב לצד השני. אין צורך לכוון.');},
  get drill(){return L('game.school.lesson4_drill','מטרונום מתקתק. נוגעים בדיוק על הפעימה. כל נגיעה מקבלת ציון: מושלם, טוב, מוקדם או מאוחר. פעימה שמפספסים, והמהירות עולה.');},
  get goals(){return [L('game.school.lesson4_goal1','10 סיבובים בקצב (טוב או מושלם)'),L('game.school.lesson4_goal2','8 סיבובים מושלמים'),L('game.school.lesson4_goal3','רצף של 8 סיבובים בקצב')];}},
 {get name(){return L('game.school.lesson5_title','מבט קדימה');}, col:'red', mode:'steer', slope:15, len:260, start:4, get d(){return D.look;}, traffic:true, fade:true,
  gates:[[0,35,6],[-6,70,6],[5,105,6],[-4,140,6],[6,175,6],[-5,210,6],[3,245,6]],
  get rule(){return L('game.school.lesson5_rule','מסתכלים שלושה סיבובים קדימה, לא על קצות המגלשיים. מי שגולש לפניכם, נמוך מכם, תמיד קודם. עוקפים אותו במרחק.');},
  get ctrl(){return L('game.school.lesson5_controls','המגלשיים פונים לכיוון האצבע.');},
  get drill(){return L('game.school.lesson5_drill','הדגלים נעלמים כשמתקרבים אליהם, כמו שקורה כשמסתכלים על המגלשיים. רואים אותם רק מרחוק, ולכן מתכננים מרחוק. בדרך גולשים אחרים.');},
  get goals(){return [L('game.school.lesson5_goal1','לעבור 5 שערים'),L('game.school.lesson5_goal2','לעבור 6 שערים, בלי התנגשות'),L('game.school.lesson5_goal3','כל 7 השערים, בלי התנגשות')];}},
 {get name(){return L('game.school.lesson6_title','קרווינג');}, col:'red', mode:'lean', slope:16, len:285, start:6, get d(){return D.carve;},
  get rule(){return L('game.school.lesson6_rule','בקרווינג לא מסובבים את המגלשיים. מטים אותם על הקנט, והצורה שלהם מסובבת אתכם. יותר הטיה, סיבוב הדוק יותר. מעבר חד מקנט לקנט שובר את האחיזה ומחליק.');},
  get ctrl(){return L('game.school.lesson6_controls','הפעם האצבע לא מכוונת. היא קובעת כמה להטות: ימינה מהמרכז מטה ימינה, שמאלה מטה שמאלה. המד בתחתית מראה את ההטיה.');},
  get drill(){return L('game.school.lesson6_drill','לצייר קשתות נקיות: כל סיבוב שלם בלי החלקה הוא "קשת נקייה". שני קווים דקים בשלג אומרים שזה עבד. מעבר חד ומהיר מורח את השלג.');},
  get goals(){return [L('game.school.lesson6_goal1','4 קשתות נקיות'),L('game.school.lesson6_goal2','8 קשתות נקיות'),L('game.school.lesson6_goal3','8 קשתות, ופחות משנייה וחצי של החלקה')];}},
 {get name(){return L('game.school.lesson7_title','מבחן סיום');}, col:'blue', mode:'steer', slope:16, len:340, start:4, get d(){return D.final;}, wedgeBtn:true, traffic:true, limit:40,
  gates:[[-5,40,7],[5,85,7],[-6,130,7],[4,175,7],[-5,220,7],[5,265,7],[0,310,8]],
  get rule(){return L('game.school.lesson7_rule','הכל ביחד: מבט קדימה, צורת סיבוב ששולטת במהירות, ופיצה רק כשבאמת צריך.');},
  get ctrl(){return L('game.school.lesson7_controls','המגלשיים פונים לכיוון האצבע. כפתור "פיצה" לבלימה.');},
  get drill(){return L('game.school.lesson7_drill','שערים, גולשים אחרים, ומהירות עד 40 קמ״ש.');},
  get goals(){return [L('game.school.lesson7_goal1','לסיים, עם לפחות 5 שערים'),L('game.school.lesson7_goal2','כל השערים, בלי התנגשות'),L('game.school.lesson7_goal3','וגם מתחת ל-40 ב-90% מהזמן')];}}];

/* ---------- menus ---------- */
const stars=i=>store.get('stars-'+i,0);
function drawMenu(){ $('lessons').innerHTML=LS.map((l,i)=>{ const open=i===0||stars(i-1)>0, st=stars(i);
  return `<button class="les" type="button" style="--c:${RUNCOL[l.col]}" data-i="${i}" ${open?'':'disabled'}><span class="n">${i+1}</span><b>${l.name}</b><span class="st">${open?'★'.repeat(st)+'☆'.repeat(3-st):'🔒'}</span><small>${l.drill.split('.')[0]}.</small></button>`; }).join(''); }
let cur=0;
$('lessons').onclick=e=>{ const b=e.target.closest('[data-i]'); if(!b||b.disabled) return; brief(+b.dataset.i); };
function brief(i){ cur=i; const l=LS[i]; state='brief'; hideHud(); $('menu').hidden=true; $('result').hidden=true; $('brief').hidden=false;
  $('briefCard').style.setProperty('--c',RUNCOL[l.col]);
  $('briefCard').innerHTML=`<span class="k">${L('game.school.brief_header','שיעור {n} מתוך {total}',{n:i+1,total:LS.length})}</span><h3>${l.name}</h3>${fig(l.d)}<p class="rule">${l.rule}</p><div class="ctrl"><b>${L('game.school.brief_controls','השליטה:')}</b> ${l.ctrl}</div><div class="ctrl"><b>${L('game.school.brief_drill','התרגיל:')}</b> ${l.drill}</div><div class="goals">${l.goals.map(g=>`<span>${g}</span>`).join('')}</div>`; }
$('go').onclick=()=>start(); $('retry').onclick=()=>brief(cur);
$('next').onclick=()=>{ if(cur<LS.length-1) brief(cur+1); else toMenu(); };
$('back1').onclick=toMenu; $('back2').onclick=toMenu;
const HUD=['hud','task','coach','checks','slider','skis','wedge','hint'];
function hideHud(){ HUD.forEach(id=>$(id).hidden=true); }
function toMenu(){ ['brief','result'].forEach(id=>$(id).hidden=true); $('menu').hidden=false; hideHud(); drawMenu(); state='menu'; }

/* ---------- sound ---------- */
let AC=null, hiss=null, hissG=null, hissF=null;
function audio(){ if(AC) return; try{ AC=new (window.AudioContext||window.webkitAudioContext)(); const n=AC.sampleRate*2, b=AC.createBuffer(1,n,AC.sampleRate), a=b.getChannelData(0); for(let i=0;i<n;i++) a[i]=Math.random()*2-1;
  hiss=AC.createBufferSource(); hiss.buffer=b; hiss.loop=true; hissF=AC.createBiquadFilter(); hissF.type='bandpass'; hissF.frequency.value=1800; hissF.Q.value=.7; hissG=AC.createGain(); hissG.gain.value=0; hiss.connect(hissF); hissF.connect(hissG); hissG.connect(AC.destination); hiss.start(); }catch(e){ AC=null; } }
function ding(f=660,d=.3,v=.18,type='triangle'){ if(!AC) return; const o=AC.createOscillator(), g=AC.createGain(); o.type=type; o.frequency.value=f; g.gain.setValueAtTime(v,AC.currentTime); g.gain.exponentialRampToValueAtTime(.001,AC.currentTime+d); o.connect(g); g.connect(AC.destination); o.start(); o.stop(AC.currentTime+d+.02); }
function buzz(ms){ try{ navigator.vibrate&&navigator.vibrate(ms); }catch(_){} }

/* ---------- the slope, from behind the skier: forward (down the slope) is up the screen ---------- */
const cv=$('game'), cx=cv.getContext('2d'); let W=0,H=0,PX=20;
function resize(){ const d=Math.min(2,devicePixelRatio||1); W=innerWidth; H=innerHeight; cv.width=W*d; cv.height=H*d; cx.setTransform(d,0,0,d,0,0); PX=Math.max(16,Math.min(28,W/19)); }
addEventListener('resize',resize); resize();
let state='menu', lv, S, tracks, gates, people, finger=null, t=0, st, camY=0, camX=0, last=performance.now(), ctl={};
const sx=x=>W/2+(x-camX)*PX, sy=y=>H*.7-(y-camY)*PX;
function start(){ lv=LS[cur]; audio(); if(AC&&AC.state==='suspended') AC.resume();
  S={x:0,y:-2,h:0,v:lv.start||3,wedge:0,skid:0,edge:0,crashT:0};
  gates=(lv.gates||[]).map(g=>({x:g[0],y:g[1],w:g[2],done:null}));
  people=[]; if(lv.traffic){ for(let k=0;k<6;k++) people.push({x:(k%2?1:-1)*(2+((k*37)%6)),y:45+k*(lv.len-70)/6,v:2+(k%3)*.8,ph:k*1.3,amp:2.5+(k%3),c:['#1B8A4C','#7B4FC4','#F4B942','#1F5FC4'][k%4],hit:false}); }
  tracks=[]; t=0; camY=S.y; camX=0; finger=null;
  ctl={wedge:lv.mode==='wedge'?.15:0, skiL:false, skiR:false, wedgeBtn:false, turnDir:1, target:0};
  st={time:0, maxV:0, gates:0, crashes:0, under:0,
      stops:[], stopIdx:0, band:0, bandT:0,
      wrong:0, asked:null,
      traps:[], trapIdx:0,
      taps:0, good:0, perfect:0, streak:0, best:0, lastBeat:0, beatN:0,
      arcs:0, skidT:0, arc:null, beat0:.6, lastTap:0};
  ['menu','brief','result'].forEach(id=>$(id).hidden=true);
  ['hud','task','coach','checks'].forEach(id=>$(id).hidden=false);
  $('slider').hidden=lv.mode!=='wedge'; $('skis').hidden=lv.mode!=='skis'; $('wedge').hidden=!lv.wedgeBtn; $('wedge').classList.remove('on');
  $('hudName').textContent=(cur+1)+'. '+lv.name; $('taskTxt').textContent=lv.drill.split('.')[0];
  coach(lv.mode==='steer'&&lv.demo?L('game.school.coach_watch_demo','צופים: ישר במורד, בלי לגעת'):L('game.school.coach_start','מתחילים'));
  state='run'; checks(); g0=performance.now(); window.track&&track('game_start',{game:'school',level:cur+1}); }
// usage statistics (site/js/telemetry.js; absent in the design preview): level = lesson number, score = stars, completed = at least one star
let g0=0;
function gameEnd(n,best){ if(!g0) return; window.track&&track('game_end',{game:'school',level:cur+1,score:n,seconds:Math.round((performance.now()-g0)/1000),completed:n>0,best}); g0=0; }
addEventListener('pagehide',()=>{ if(state==='run'||state==='done') gameEnd(0,false); });
function popText(s){ const p=$('pop'); p.textContent=s; p.classList.remove('go'); void p.offsetWidth; p.classList.add('go'); }
let coachLast='';
function coach(txt,cls=''){ const k=txt+'|'+cls; if(k===coachLast) return; coachLast=k; const el=$('coachTxt'); el.textContent=txt; el.className=cls; }
/* ---------- input ---------- */
cv.addEventListener('pointerdown',e=>{ if(state!=='run') return; e.preventDefault(); finger={x:e.clientX,y:e.clientY}; try{cv.setPointerCapture(e.pointerId);}catch(_){} if(lv.mode==='rhythm') tap(); });
cv.addEventListener('pointermove',e=>{ if(finger){ finger.x=e.clientX; finger.y=e.clientY; } });
cv.addEventListener('pointerup',()=>{ finger=null; }); cv.addEventListener('pointercancel',()=>{ finger=null; });
function hold(el,key){ el.addEventListener('pointerdown',e=>{ e.preventDefault(); e.stopPropagation(); ctl[key]=true; el.classList.add('on'); try{el.setPointerCapture(e.pointerId);}catch(_){} if(key!=='wedgeBtn') pressSki(key); });
  ['pointerup','pointercancel','lostpointercapture'].forEach(ev=>el.addEventListener(ev,()=>{ ctl[key]=false; el.classList.remove('on'); })); }
hold($('skiL'),'skiL'); hold($('skiR'),'skiR'); hold($('wedge'),'wedgeBtn');
const keys={}; addEventListener('keydown',e=>{ if(keys[e.code]) return; keys[e.code]=true; if(e.code.startsWith('Arrow')||e.code==='Space') e.preventDefault(); if(state==='run'&&lv.mode==='rhythm'&&e.code==='Space') tap(); if(state==='run'&&lv.mode==='skis'){ if(e.code==='ArrowLeft'){ ctl.skiL=true; pressSki('skiL'); } if(e.code==='ArrowRight'){ ctl.skiR=true; pressSki('skiR'); } } });
addEventListener('keyup',e=>{ keys[e.code]=false; if(e.code==='ArrowLeft') ctl.skiL=false; if(e.code==='ArrowRight') ctl.skiR=false; });

/* lesson 2: the coach checks which ski you chose before each gate */
function nextGate(){ return gates.find(g=>g.done==null); }
function pressSki(key){ if(state!=='run'||lv.mode!=='skis') return; const g=nextGate(); const turnsLeft = key==='skiR';
  if(g){ const need = g.x < S.x-1 ? 'left' : g.x > S.x+1 ? 'right' : null;
    if(need && ((need==='left')!==turnsLeft)){ st.wrong++; coach(turnsLeft?L('game.school.coach_wrong_left','לחצת על הימני, ופנית שמאלה. השער מימין: לוחצים על השמאלי'):L('game.school.coach_wrong_right','לחצת על השמאלי, ופנית ימינה. השער משמאל: לוחצים על הימני'),'bad'); buzz(30); ding(200,.2,.15,'square'); checks(); }
    else if(need) coach(turnsLeft?L('game.school.coach_correct_left','נכון: ימני, החיצוני, מפנה שמאלה'):L('game.school.coach_correct_right','נכון: שמאלי, החיצוני, מפנה ימינה'),'good'); } }
/* lesson 4: every tap starts a turn; the coach grades the timing against the beat */
function tap(){ if(state!=='run') return; st.taps++; st.lastTap=t; const B=lv.beat, ph=((t-st.beat0)%B+B)%B, off=Math.min(ph,B-ph)*(ph<B/2?1:-1);
  ctl.turnDir*=-1; ctl.target=ctl.turnDir*.95;
  const a=Math.abs(off); let txt, cls;
  if(a<.12){ st.perfect++; st.good++; st.streak++; txt=L('game.school.beat_perfect','מושלם!'); cls='good'; ding(880,.12,.15); }
  else if(a<.25){ st.good++; st.streak++; txt=L('game.school.beat_good','טוב'); cls='good'; ding(660,.1,.12); }
  else { st.streak=0; txt= off>0?L('game.school.beat_late','מאוחר: מתחילים את הסיבוב קודם'):L('game.school.beat_early','מוקדם: לחכות לפעימה'); cls='bad'; ding(220,.12,.1,'square'); }
  st.best=Math.max(st.best,st.streak); coach(txt,cls); checks(); }

function frame(now){ const dt=Math.min(.033,(now-last)/1000); last=now; if(state==='run'||state==='done') update(dt); draw(); requestAnimationFrame(frame); }
function update(dt){
  t+=dt; const a=lv.slope*Math.PI/180;
  let want=S.h, rateMax=2.4, wedge=0;
  if(lv.mode==='wedge'){ want=0;
    if(finger){ const top=H*.3, bot=H*.82; ctl.wedge=Math.max(0,Math.min(1,(finger.y-top)/(bot-top))); }
    if(keys.ArrowDown) ctl.wedge=Math.min(1,ctl.wedge+dt*.8); if(keys.ArrowUp) ctl.wedge=Math.max(0,ctl.wedge-dt*.8);
    wedge=ctl.wedge; const hd=$('sliderHd'); hd.style.top=(ctl.wedge*100)+'%'; hd.textContent=L('game.school.slider_pct','פיצה {pct}%',{pct:Math.round(ctl.wedge*100)}); }
  else if(lv.mode==='skis'){ wedge=.25; rateMax=1.3; want = ctl.skiR&&!ctl.skiL ? -.75 : ctl.skiL&&!ctl.skiR ? .75 : S.h*.6; }
  else if(lv.mode==='steer'){
    if(lv.demo && t<lv.demo){ want=0; }
    else if(finger){ const dx=finger.x-sx(S.x), fwd=sy(S.y)-finger.y; want=Math.atan2(dx,Math.max(-PX*1.5,fwd)); }
    if(keys.ArrowLeft) want=S.h-1.5; if(keys.ArrowRight) want=S.h+1.5;
    wedge = lv.wedgeBtn && (ctl.wedgeBtn||keys.ArrowDown) ? 1 : 0; }
  else if(lv.mode==='rhythm'){ want=ctl.target; rateMax=1.6; }
  else if(lv.mode==='lean'){
    let e=S.edge; if(finger) e=Math.max(-1,Math.min(1,(finger.x-W/2)/(W*.32))); if(keys.ArrowLeft) e=-1; if(keys.ArrowRight) e=1; if(!finger&&!keys.ArrowLeft&&!keys.ArrowRight) e=S.edge*.98;
    // how fast the skis really change edge (not the finger): smoothed, so touch events that come in bursts do not count
    const e0=S.edge; S.edge+=Math.max(-3*dt,Math.min(3*dt,e-S.edge)); S.jerk=(S.jerk||0)+(Math.abs(S.edge-e0)/dt-(S.jerk||0))*Math.min(1,dt*10);
    want=null; }
  want = want==null? null : Math.max(-1.9,Math.min(1.9,want));
  S.wedge+=(wedge-S.wedge)*Math.min(1,dt*8);
  let rate=0;
  if(lv.mode==='lean'){ // the ski's shape turns you: more edge, tighter turn; snapping from edge to edge breaks the grip
    rate = S.v*S.edge*.11; const sk = Math.max(0,(S.jerk||0)-2.2)/.8; /* a full edge change in under ~0.9 s is a snap */ S.skid += (Math.min(1,sk) - S.skid)*Math.min(1,dt*8);
    if(S.h>1.9&&rate>0||S.h<-1.9&&rate<0) rate=0; }
  else { const d=want-S.h; rate=Math.max(-rateMax,Math.min(rateMax,d*3.2)); const carveRate=S.v/9+.25; const skid=lv.mode==='rhythm'?0:Math.max(0,Math.abs(rate)-carveRate); /* the rhythm lesson is about timing, not edging */ S.skid+=(Math.min(1,skid/1.2)-S.skid)*Math.min(1,dt*10); }
  S.h+=rate*dt;
  let acc = G*Math.sin(a)*Math.cos(S.h) - .05*G*Math.cos(a) - .0035*S.v*S.v - S.wedge*(2.6+S.v*.35) - S.skid*S.v*.9 - Math.abs(rate)*S.v*.06;
  if(S.crashT>0){ S.crashT-=dt; acc=-6; }
  S.v=Math.max((lv.mode==='skis'||lv.mode==='lean')&&state==='run'?1.2:0,S.v+acc*dt); /* in the turning and edging lessons you never stall: a push with the poles, so a skier who turned up the hill can turn back */ if(state==='done') S.v=Math.max(0,S.v-6*dt);
  S.x+=Math.sin(S.h)*S.v*dt; S.y+=Math.cos(S.h)*S.v*dt; S.x=Math.max(-14,Math.min(14,S.x));
  camY+=(S.y-camY)*Math.min(1,dt*6); camX+=(S.x*.55-camX)*Math.min(1,dt*3);
  const lt=tracks[tracks.length-1]; if(S.v>.3 && (!lt||Math.hypot(lt.x-S.x,lt.y-S.y)>.25)) tracks.push({x:S.x,y:S.y,h:S.h,sk:S.skid,wd:S.wedge}); if(tracks.length>2500) tracks.splice(0,500);
  if(state!=='run') return;
  st.time+=dt; const kmh=S.v*KMH; if(!(lv.demo && t<lv.demo+1.5)) st.maxV=Math.max(st.maxV,kmh); /* the top speed counts once you steer: not in the demo, nor the moment after it */ $('spd').textContent=Math.round(kmh);
  if(lv.limit){ if(kmh<=lv.limit) st.under+=dt; $('spdBox').classList.toggle('over',kmh>lv.limit); }
  if(AC&&hissG){ hissG.gain.value=Math.min(.22,S.v*.012+S.skid*.12+S.wedge*.05); hissF.frequency.value=900+S.v*80+S.skid*1500; }
  drills(dt,kmh);
  for(const g of gates){ if(g.done==null && S.y>=g.y){ g.done=Math.abs(S.x-g.x)<=g.w/2; if(g.done){ st.gates++; ding(660+st.gates*30); popText(L('game.school.pop_gate','שער!')); } else { popText(L('game.school.pop_gate_missed','פספסת שער')); buzz(20); } checks(); } }
  for(const p of people){ p.ph+=dt*.6; p.x+=Math.cos(p.ph)*p.amp*dt*.8; p.y+=p.v*dt;
    if(!p.hit && Math.hypot(p.x-S.x,p.y-S.y)<1.3 && S.crashT<=0){ p.hit=true; st.crashes++; S.crashT=.8; S.v*=.3; popText(L('game.school.pop_crash','התנגשות!')); coach(L('game.school.coach_priority','מי שלמטה ממך תמיד קודם. עוקפים במרחק'),'bad'); buzz([30,40,30]); checks(); }
    else if(!p.hit && p.y>S.y && p.y-S.y<9 && Math.abs(p.x-S.x)<3) coach(L('game.school.coach_skier_ahead','גולש לפניך: לעקוף במרחק')); }
  if(S.y>=lv.len) finishLesson();
}
/* ---------- what each drill watches and says ---------- */
const STOPS=[35,72,112], BAND=[80,102];
function drills(dt,kmh){
  if(lv.mode==='wedge'){
    const line=STOPS[st.stopIdx];
    if(line!=null){ const d=line-S.y;
      if(S.v<.12 && d>=0 && d<6 && t>1){ st.stops.push(d); st.stopIdx++; popText(d<1.5?L('game.school.pop_stop_perfect','עצירה מושלמת!'):L('game.school.pop_stopped','עצרת')); ding(d<1.5?880:660); coach(L('game.school.coach_narrow_continue','עכשיו לצמצם את הפיצה (למעלה) ולהמשיך'),'good'); checks(); if(st.stopIdx>=STOPS.length) setTimeout(finishLesson,900); }
      else if(S.y>line){ st.stops.push(null); st.stopIdx++; popText(L('game.school.pop_line_passed','עברת את הקו')); buzz(30); checks(); if(st.stopIdx>=STOPS.length) setTimeout(finishLesson,900); }
      else if(d<14 && S.v>2) coach(L('game.school.coach_line_close','הקו מתקרב: לפתוח פיצה רחבה (למטה)')); }
    if(S.y>BAND[0]&&S.y<BAND[1]){ st.bandT+=dt; if(kmh>=6&&kmh<=10){ st.band+=dt; coach(L('game.school.coach_in_band','בדיוק ברצועה: {kmh} קמ״ש',{kmh:Math.round(kmh)}),'good'); } else coach(kmh>10?L('game.school.coach_too_fast','מהר מדי: עוד קצת פיצה'):L('game.school.coach_too_slow','לאט מדי: פיצה צרה יותר'),'bad'); }
    else if(!(line!=null && line-S.y<14 && S.v>2)){ coach(ctl.wedge<.2?L('game.school.coach_skis_straight','מגלשיים כמעט ישרים: מאיצים'):ctl.wedge<.6?L('game.school.coach_narrow_pizza','פיצה צרה: מאטה בעדינות'):L('game.school.coach_wide_pizza','פיצה רחבה: בלימה חזקה'), ctl.wedge>=.6?'good':''); }
  }
  if(lv.mode==='skis'){ const g=nextGate(); if(g && !ctl.skiL && !ctl.skiR){ if(st.asked!==g){ st.asked=g; } coach(g.x<S.x?L('game.school.coach_next_gate_left','השער הבא משמאל. על איזה מגלש לוחצים?'):L('game.school.coach_next_gate_right','השער הבא מימין. על איזה מגלש לוחצים?')); } }
  if(lv.mode==='steer' && lv.traps){
    if(lv.demo && t<lv.demo){ coach(L('game.school.coach_demo_speed','צופים: ישר במורד, {kmh} קמ״ש ועולה',{kmh:Math.round(kmh)})); return; }
    if(lv.demo && t<lv.demo+.1) popText(L('game.school.pop_your_turn','עכשיו אתם!'));
    const tr=lv.traps[st.trapIdx];
    if(tr!=null && S.y>=tr){ const ok=kmh<=lv.trapLimit; st.traps.push(ok); st.trapIdx++; popText(ok?L('game.school.pop_trap_ok','עברת את המכמונת!'):L('game.school.pop_trap_caught','נתפסת: {kmh} קמ״ש',{kmh:Math.round(kmh)})); ok?ding(880):buzz(40); checks(); }
    const ah=Math.abs(S.h); coach(ah<.35?L('game.school.coach_straight','ישר במורד: מאיץ'):ah<1.0?L('game.school.coach_diagonal','באלכסון: מאיץ פחות'):L('game.school.coach_across','לרוחב המדרון: מאט'), ah<.35?'bad':ah>1?'good':''); }
  if(lv.mode==='rhythm'){ const B=lv.beat, n=Math.floor((t-st.beat0)/B);
    if(n>st.beatN){ st.beatN=n; ding(1200,.05,.12,'square'); st.pulse=1; if(t-st.lastTap>B*1.8&&st.taps>0){ st.streak=0; coach(L('game.school.coach_missed_beat','פספסת פעימה: המהירות עולה'),'bad'); } }
    st.pulse=Math.max(0,(st.pulse||0)-dt*3); if(st.taps===0) coach(L('game.school.coach_listen','מקשיבים לטיק… ונוגעים בדיוק על הפעימה')); }
  if(lv.mode==='lean'){
    if(S.skid>.25){ st.skidT+=dt; coach(L('game.school.coach_skid','מעבר חד מדי: המגלשיים מחליקים'),'bad'); } else if(Math.abs(S.edge)>.3) coach(L('game.school.coach_on_edge','על הקנט: לתת לו לסובב'),'good'); else coach(L('game.school.coach_flat','ישרים: להטות לאט לצד אחד'));
    // a clean arc: from one side to the other, long enough, without skidding
    const side=Math.abs(S.edge)>.3?Math.sign(S.edge):0;
    if(side){ if(!st.arc||st.arc.side!==side){ if(st.arc&&st.arc.clean&&st.arc.dur>.8&&Math.abs(S.h-st.arc.h0)>.6){ st.arcs++; popText(L('game.school.pop_clean_arc','קשת נקייה! {n}',{n:st.arcs})); ding(700+st.arcs*40); checks(); } st.arc={side,dur:0,clean:true,h0:S.h}; } st.arc.dur+=dt; if(S.skid>.25) st.arc.clean=false; } }
}
function checks(){ if(!lv) return; let items=[];
  if(lv.mode==='wedge'){ items=STOPS.map((_,i)=>[L('game.school.check_stop_line','עצירה בקו {n}',{n:i+1}), st.stops[i]===undefined?'':st.stops[i]===null?'no':'ok']); items.splice(2,0,[st.bandT>0?L('game.school.check_band_pct','רצועה ירוקה {pct}%',{pct:Math.round(st.band/Math.max(.01,st.bandT)*100)}):L('game.school.check_band','רצועה ירוקה'), st.bandT>0&&S.y>BAND[1]?(st.band/st.bandT>=.7?'ok':'no'):'']); }
  else if(lv.mode==='skis') items=[[L('game.school.check_gates','שערים {n} / {total}',{n:st.gates,total:gates.length}), gates.every(g=>g.done!=null)?(st.gates===gates.length?'ok':'no'):''],[L('game.school.check_wrong','לחיצות הפוכות: {n}',{n:st.wrong}), st.wrong>1?'no':'']];
  else if(lv.traps) items=lv.traps.map((_,i)=>[L('game.school.check_trap','מכמונת {n}',{n:i+1}), st.traps[i]===undefined?'':st.traps[i]?'ok':'no']);
  else if(lv.mode==='rhythm') items=[[L('game.school.check_on_beat','בקצב: {n} / 10',{n:st.good}), st.good>=10?'ok':''],[L('game.school.check_perfect','מושלמים: {n} / 8',{n:st.perfect}), st.perfect>=8?'ok':''],[L('game.school.check_streak','רצף: {n} (שיא {best})',{n:st.streak,best:st.best}), st.best>=8?'ok':'']];
  else if(lv.mode==='lean') items=[[L('game.school.check_arcs','קשתות נקיות: {n}',{n:st.arcs}), st.arcs>=8?'ok':''],[L('game.school.check_skid','החלקה: {sec} ש׳',{sec:st.skidT.toFixed(1)}), st.skidT>=1.5?'no':'']];
  else items=[[L('game.school.check_gates','שערים {n} / {total}',{n:st.gates,total:gates.length}), ''],[L('game.school.check_crashes','התנגשויות: {n}',{n:st.crashes}), st.crashes?'no':'']];
  $('checks').innerHTML=items.map(([s,c])=>`<span class="${c}">${s}</span>`).join(''); }
function finishLesson(){ if(state!=='run') return; state='done'; if(hissG) hissG.gain.value=0;
  let g, line;
  if(lv.mode==='wedge'){ const ok=st.stops.filter(d=>d!=null); const avg=ok.length?ok.reduce((a,b)=>a+b,0)/ok.length:99; const band=st.bandT?st.band/st.bandT:0;
    g=[ok.length===3, band>=.7, ok.length===3&&avg<=1.5]; line=L('game.school.res_wedge','{stops} עצירות מתוך 3 · ממוצע {avg} מהקו · {pct}% ברצועה',{stops:ok.length,avg:avg<90?L('game.school.res_meters','{m} מ׳',{m:avg.toFixed(1)}):'-',pct:Math.round(band*100)}); }
  else if(lv.mode==='skis'){ g=[st.gates>=4, st.gates===6, st.gates===6&&st.wrong<=1]; line=L('game.school.res_skis','{n} שערים מתוך 6 · {wrong} לחיצות הפוכות',{n:st.gates,wrong:st.wrong}); }
  else if(lv.traps){ const n=st.traps.filter(Boolean).length; g=[n>=3, n===4, n===4&&st.maxV<=35]; line=L('game.school.res_traps','{n} מכמונות מתוך 4 · שיא {max} קמ״ש',{n,max:Math.round(st.maxV)}); }
  else if(lv.mode==='rhythm'){ g=[st.good>=10, st.perfect>=8, st.best>=8]; line=L('game.school.res_rhythm','{good} בקצב · {perfect} מושלמים · רצף שיא {best}',{good:st.good,perfect:st.perfect,best:st.best}); }
  else if(lv.mode==='lean'){ g=[st.arcs>=4, st.arcs>=8, st.arcs>=8&&st.skidT<1.5]; line=L('game.school.res_lean','{arcs} קשתות נקיות · {sec} שניות של החלקה',{arcs:st.arcs,sec:st.skidT.toFixed(1)}); }
  else if(lv.fade){ g=[st.gates>=5, st.gates>=6&&st.crashes===0, st.gates===7&&st.crashes===0]; line=L('game.school.res_look','{n} שערים מתוך 7 · {crashes} התנגשויות',{n:st.gates,crashes:st.crashes}); }
  else { const u=st.under/st.time, all=st.gates===gates.length&&st.crashes===0; g=[st.gates>=5, all, all&&u>=.9]; line=L('game.school.res_final','{n}/{total} שערים · {crashes} התנגשויות · {pct}% מתחת ל-40',{n:st.gates,total:gates.length,crashes:st.crashes,pct:Math.round(u*100)}); }
  const n=g.filter(Boolean).length; gameEnd(n, n>stars(cur)); if(n>stars(cur)) store.set('stars-'+cur,n);
  setTimeout(()=>showResult(g,n,line), 900); }
const TIPS=()=>[L('game.school.tip_1','פיצה רחבה = בלימה חזקה. פיצה צרה = האטה עדינה. רוחב הפיצה הוא דוושת הבלם.'),L('game.school.tip_2','לפנות שמאלה: המשקל על המגלש הימני. לפנות ימינה: על השמאלי.'),L('game.school.tip_3','לא בולמים: מסיימים את הסיבוב לרוחב המדרון.'),L('game.school.tip_4','סיבוב חדש מתחיל כשהקודם נגמר. קצב קבוע = שליטה.'),L('game.school.tip_5','העיניים רחוק קדימה. מי שלמטה ממך תמיד קודם.'),L('game.school.tip_6','מטים בעדינות, ונותנים לקנט לעבוד.'),L('game.school.tip_7','כל העקרונות יחד. בהצלחה בגודאורי!')];
function showResult(g,n,line){ hideHud(); $('result').hidden=false; state='result';
  $('resCard').style.setProperty('--c',RUNCOL[lv.col]);
  $('resCard').innerHTML=`<span class="k">${L('game.school.result_header','שיעור {n} · {name}',{n:cur+1,name:lv.name})}</span><div class="big" style="color:var(--accent);letter-spacing:4px">${'★'.repeat(n)}<span style="color:var(--rule)">${'★'.repeat(3-n)}</span></div><p class="rule">${line}</p><div class="goals">${lv.goals.map((x,i)=>`<span class="${g[i]?'ok':''}">${x}</span>`).join('')}</div><div class="ctrl">${L('game.school.remember','לזכור: {tip}',{tip:TIPS()[cur]})}</div>`;
  $('next').textContent = cur<LS.length-1 ? (n>0?L('game.school.next_lesson','השיעור הבא'):L('game.school.try_again','לנסות שוב')) : L('game.school.back_to_lessons','לכל השיעורים');
  $('next').onclick = n>0 ? (()=>{ if(cur<LS.length-1) brief(cur+1); else toMenu(); }) : (()=>brief(cur)); }

/* ---------- drawing ---------- */
function draw(){
  cx.fillStyle='#FBFDFF'; cx.fillRect(0,0,W,H);
  if(!lv||state==='menu'||state==='brief'||state==='result') return;
  const edge=15, yTop=camY+(H*.7)/PX+2, yBot=camY-(H*.3)/PX-2;
  cx.fillStyle='#EAF1F7'; cx.fillRect(0,0,sx(-edge),H); cx.fillRect(sx(edge),0,W,H);
  cx.fillStyle='rgba(159,181,203,.16)'; for(let y=Math.floor(yBot/6)*6;y<yTop;y+=6){ for(let k=0;k<4;k++){ const x=((y*7.3+k*11)%28)-14; cx.beginPath(); cx.ellipse(sx(x),sy(y+((k*2.1)%6)),PX*1.2,PX*.25,0,0,7); cx.fill(); } }
  for(let y=Math.floor(yBot/9)*9; y<yTop; y+=9){ for(const side of [-1,1]){ tree(sx(side*(edge+2+((y*3.7)%5))),sy(y)); } }
  // drill markings
  if(lv.mode==='wedge'){ STOPS.forEach((y,i)=>{ const Y=sy(y), c=st.stops[i]===undefined?'#F08A3C':st.stops[i]===null?'#D1342B':'#1B8A4C'; cx.fillStyle=c; cx.fillRect(sx(-edge),Y-3,sx(edge)-sx(-edge),6); cx.fillStyle='rgba(27,138,76,.12)'; cx.fillRect(sx(-edge),Y,sx(edge)-sx(-edge),PX*1.5); label(L('game.school.canvas_line','קו {n}',{n:i+1}),sx(-edge)+40,Y-8,c); });
    cx.fillStyle='rgba(27,138,76,.13)'; cx.fillRect(sx(-edge),sy(BAND[1]),sx(edge)-sx(-edge),sy(BAND[0])-sy(BAND[1])); label(L('game.school.canvas_band','רצועה: 6 עד 10 קמ״ש'),sx(edge)-90,sy(BAND[0])-8,'#1B8A4C'); }
  if(lv.traps){ lv.traps.forEach((y,i)=>{ const Y=sy(y), c=st.traps[i]===undefined?'#1F5FC4':st.traps[i]?'#1B8A4C':'#D1342B'; cx.fillStyle=c; cx.globalAlpha=.18; cx.fillRect(sx(-edge),Y-PX*1.2,sx(edge)-sx(-edge),PX*1.2); cx.globalAlpha=1; cx.fillRect(sx(-edge),Y-2,sx(edge)-sx(-edge),4); label(L('game.school.canvas_trap','מכמונת: עד {limit}',{limit:lv.trapLimit}),sx(0),Y-PX*1.3,c); }); }
  // tracks: two thin lines when clean, a wide smear when skidding, a V in the wedge
  for(const side of [-1,1]){ for(let i=1;i<tracks.length;i++){ const p=tracks[i-1], q=tracks[i]; if(q.y<yBot-2||p.y>yTop+2) continue;
    const off=(o)=>(.17+o.wd*.25)*side; cx.strokeStyle=q.sk>.25?'rgba(159,181,203,.45)':'rgba(70,95,125,.55)'; cx.lineWidth=q.sk>.25?PX*(.15+q.sk*.5):1.6;
    cx.beginPath(); cx.moveTo(sx(p.x+Math.cos(p.h)*off(p)),sy(p.y-Math.sin(p.h)*off(p))); cx.lineTo(sx(q.x+Math.cos(q.h)*off(q)),sy(q.y-Math.sin(q.h)*off(q))); cx.stroke(); } }
  // gates: in the look-ahead lesson they fade as you come close
  for(const g of gates){ let al=1; if(lv.fade&&g.done==null){ const d=g.y-S.y; al=Math.max(0,Math.min(1,(d-7)/6)); }
    cx.globalAlpha=g.done!=null?.6:al; const col=g.x<0?'#D1342B':'#1F5FC4';
    for(const k of [-1,1]){ const X=sx(g.x+k*g.w/2), Y=sy(g.y); cx.fillStyle=g.done===false?'#9FB5CB':col; cx.fillRect(X-3,Y-PX*1.4,6,PX*1.4); cx.fillRect(X-(k<0?0:PX*.8),Y-PX*1.4,PX*.8,PX*.5); }
    if(g.done){ cx.fillStyle='#1B8A4C'; cx.font='700 14px "IBM Plex Sans Hebrew",sans-serif'; cx.textAlign='center'; cx.fillText('✓',sx(g.x),sy(g.y)-PX*1.5); } cx.globalAlpha=1; }
  { const Y=sy(lv.len); for(let x=-edge;x<edge;x+=1){ cx.fillStyle=((x+edge)%2)?'#13233A':'#fff'; cx.fillRect(sx(x),Y,PX,PX*.6); } }
  for(const p of people) person(sx(p.x),sy(p.y),p.c,Math.cos(p.ph)*.6,p.hit);
  if(finger&&state==='run'&&lv.mode==='steer'){ cx.strokeStyle='rgba(19,35,58,.18)'; cx.setLineDash([4,6]); cx.lineWidth=2; cx.beginPath(); cx.moveTo(sx(S.x),sy(S.y)); cx.lineTo(finger.x,finger.y); cx.stroke(); cx.setLineDash([]); cx.fillStyle='rgba(244,185,66,.5)'; cx.beginPath(); cx.arc(finger.x,finger.y,14,0,7); cx.fill(); }
  // the fall-line compass around the skier: red straight down, green across
  if(lv.traps){ const X=sx(S.x), Y=sy(S.y), r=PX*2.2; for(let k=-8;k<=8;k++){ const ang=k/8*1.9; const ah=Math.abs(ang); cx.strokeStyle=ah<.35?'rgba(209,52,43,.35)':ah<1?'rgba(244,185,66,.35)':'rgba(27,138,76,.35)'; cx.lineWidth=6; cx.beginPath(); cx.arc(X,Y,r,-Math.PI/2+ang-.12,-Math.PI/2+ang+.12); cx.stroke(); } cx.strokeStyle='#13233A'; cx.lineWidth=3; cx.beginPath(); cx.moveTo(X,Y); cx.lineTo(X+Math.sin(S.h)*r,Y-Math.cos(S.h)*r); cx.stroke(); }
  if(lv.mode==='rhythm'&&st.pulse){ cx.strokeStyle=`rgba(244,185,66,${st.pulse})`; cx.lineWidth=6; cx.beginPath(); cx.arc(sx(S.x),sy(S.y),PX*(1.6+(1-st.pulse)*1.6),0,7); cx.stroke(); }
  skier();
  if(lv.mode==='lean') leanGauge();
}
function label(s,X,Y,c){ cx.font='700 13px "IBM Plex Sans Hebrew",sans-serif'; cx.textAlign='center'; const w=cx.measureText(s).width+12; cx.fillStyle='rgba(255,255,255,.9)'; cx.fillRect(X-w/2,Y-15,w,19); cx.fillStyle=c; cx.fillText(s,X,Y); }
function leanGauge(){ const X=W/2, Y=H-70, r=Math.min(W*.36,150); cx.lineWidth=12; cx.strokeStyle='#EEF2F5'; cx.beginPath(); cx.arc(X,Y,r,Math.PI*1.15,Math.PI*1.85); cx.stroke();
  const ang=Math.PI*1.5+S.edge*Math.PI*.35; cx.strokeStyle=S.skid>.25?'#D1342B':'#1B8A4C'; cx.beginPath(); cx.arc(X,Y,r,Math.min(Math.PI*1.5,ang),Math.max(Math.PI*1.5,ang)); cx.stroke();
  cx.fillStyle='#13233A'; cx.beginPath(); cx.arc(X+Math.cos(ang)*r,Y+Math.sin(ang)*r,9,0,7); cx.fill(); label(L('game.school.canvas_tilt','הטיה'),X,Y-r*.35,'#13233A'); }
function tree(X,Y){ if(Y<-40||Y>H+40) return; cx.fillStyle='rgba(19,35,58,.12)'; cx.beginPath(); cx.ellipse(X+6,Y+4,PX*1.1,PX*.5,0,0,7); cx.fill(); cx.fillStyle='#2F6B4F'; cx.beginPath(); cx.arc(X,Y,PX*1.1,0,7); cx.fill(); cx.fillStyle='#fff'; cx.beginPath(); cx.arc(X-PX*.3,Y-PX*.3,PX*.45,0,7); cx.fill(); }
function person(X,Y,c,h,hit){ cx.save(); cx.translate(X,Y); cx.rotate(h); cx.fillStyle='#13233A'; cx.fillRect(-PX*.28,-PX*.8,PX*.12,PX*1.6); cx.fillRect(PX*.16,-PX*.8,PX*.12,PX*1.6); cx.fillStyle=hit?'#9FB5CB':c; cx.beginPath(); cx.arc(0,0,PX*.38,0,7); cx.fill(); cx.fillStyle='#F2D2BE'; cx.beginPath(); cx.arc(0,-PX*.1,PX*.18,0,7); cx.fill(); cx.restore(); }
function skier(){ const X=sx(S.x), Y=sy(S.y); cx.save(); cx.translate(X,Y); cx.rotate(S.h);
  const w=S.wedge, len=PX*1.7, sp=PX*(.2+w*.08), ang=w*.32, lean=lv.mode==='lean'?S.edge*PX*.35:0;
  cx.fillStyle='rgba(19,35,58,.15)'; cx.beginPath(); cx.ellipse(4,4,PX*.6,PX*1,0,0,7); cx.fill();
  // in a wedge the tips (forward, up) come together and the tails spread
  for(const k of [-1,1]){ cx.save(); cx.translate(k*sp,0); cx.rotate(-k*ang); cx.fillStyle=(lv.mode==='skis'&&((k<0&&ctl.skiL)||(k>0&&ctl.skiR)))?'#D1342B':'#13233A'; cx.fillRect(-PX*.09,-len*.5,PX*.18,len); cx.fillStyle='#F4B942'; cx.fillRect(-PX*.09,-len*.5,PX*.18,PX*.12); cx.restore(); }
  if(S.skid>.3&&!reduce){ cx.fillStyle='rgba(255,255,255,.95)'; for(let i=0;i<4;i++){ cx.beginPath(); cx.arc((Math.random()-.5)*PX*2,(Math.random()-.2)*PX,PX*.12,0,7); cx.fill(); } }
  cx.fillStyle='#F07A2E'; cx.beginPath(); cx.ellipse(lean,0,PX*.5,PX*.36,0,0,7); cx.fill();
  cx.fillStyle='#13233A'; cx.beginPath(); cx.arc(lean*1.3,-PX*.08,PX*.22,0,7); cx.fill();
  cx.restore(); }
/* the menu is drawn after the words arrived; the lessons section keeps its Hebrew label in the markup (the site build looks for it) */
(window.I18N?I18N.ready:Promise.resolve()).then(()=>{ $('menu').setAttribute('aria-label',L('game.school.lessons_label','שיעורים')); if(window.I18N&&I18N.lang!=='he') document.title=L('game.school.title','בית הספר לסקי'); drawMenu(); });
requestAnimationFrame(frame);

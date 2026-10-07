// Round 25: a readable map for large resorts. Drawn over the real site (design/round25/shots.mjs).
// One system of valleys: (A) the whole resort shows valleys, not 9,000 lines; (B) a row of valley chips frames one valley
// and dims the rest; the list is grouped by valley; (D) in 3D the camera sits low on the valley's side.
// Valleys are a grouping only (design/round25/data/): no line is drawn or moved. The soft area round a valley is its own runs,
// drawn very wide and faint, not a border. Not product code: the canvas shows it, the site implements it.
(function(){
  const NS='http://www.w3.org/2000/svg';
  const mk=(n,a,p)=>{const e=document.createElementNS(NS,n);for(const k in a)e.setAttribute(k,a[k]);p&&p.appendChild(e);return e;};
  const H=(t,a={},h='')=>{const e=document.createElement(t);for(const k in a)e.setAttribute(k,a[k]);e.innerHTML=h;return e;};
  const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
  const svg=()=>document.getElementById('map');
  const fmt=m=>m>=1000?(m/1000).toFixed(2)+' ק״מ':m+' מ׳';
  function css(){
    if(document.getElementById('r25-css'))return;
    const s=document.createElement('style');s.id='r25-css';s.textContent=`
      :root{--v-gardena:#C49A3A;--v-badia:#2F978B;--v-arabba:#8C6CC0;--v-fassa:#7E9136;--v-a:#C49A3A;--v-b:#2F978B;--v-c:#8C6CC0;--zone-op:.34;--zone-sel:.12}
      @media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--v-gardena:#E2BB5C;--v-badia:#4CC3B4;--v-arabba:#B69AE8;--v-fassa:#A9BE57;--v-a:#E2BB5C;--v-b:#4CC3B4;--v-c:#B69AE8;--zone-op:.3;--zone-sel:.1}}
      :root[data-theme="dark"]{--v-gardena:#E2BB5C;--v-badia:#4CC3B4;--v-arabba:#B69AE8;--v-fassa:#A9BE57;--v-a:#E2BB5C;--v-b:#4CC3B4;--v-c:#B69AE8;--zone-op:.3;--zone-sel:.1}
      .r25-vchips{display:flex;gap:8px;padding:8px 14px;background:var(--snow);border-bottom:1px solid var(--rule);overflow-x:auto;scrollbar-width:none}
      .r25-vchips button{display:flex;align-items:center;gap:7px;height:44px;padding:0 14px;border:1.5px solid var(--rule);background:var(--paper);color:var(--ink);font:inherit;font-size:14px;font-weight:600;white-space:nowrap;direction:ltr}
      .r25-vchips button i{width:12px;height:12px;flex:none}
      .r25-vchips button[aria-pressed="true"]{background:var(--ink);color:var(--snow);border-color:var(--ink)}
      .r25-vchips button.all{direction:rtl}
      .r25-vlab{position:absolute;z-index:2;transform:translate(-50%,-50%);display:flex;flex-direction:column;align-items:center;gap:3px;padding:5px 9px 5px;min-width:44px;min-height:44px;box-sizing:border-box;
        background:var(--paper);border:1.5px solid var(--rule);border-top:4px solid var(--vc);box-shadow:0 2px 8px var(--shadow);pointer-events:none;direction:ltr}
      .r25-vlab b{font:700 19px/1 var(--f-display);color:var(--ink);white-space:nowrap}
      .r25-vlab span{display:flex;gap:6px;align-items:center;font:600 11.5px/1 var(--f-body);color:var(--muted);white-space:nowrap}
      .r25-vlab span em{font-style:normal;display:flex;align-items:center;gap:3px;color:var(--ink)}
      .r25-vlab span em i{width:8px;height:8px;display:inline-block}
      .r25-vlab.sm b{font-size:16px}
      .q-blue{background:var(--p-blue)}.q-red{background:var(--p-red)}.q-black{background:var(--p-black)}.q-green{background:var(--p-green)}
      .r25-vhead{display:flex;align-items:center;gap:10px;width:100%;box-sizing:border-box;min-height:64px;padding:8px 4px;border:0;border-bottom:1px solid var(--rule);background:none;color:var(--ink);font:inherit;text-align:start;cursor:pointer}
      .r25-vhead .bar{width:6px;align-self:stretch;flex:none}
      .r25-vhead .nm{display:flex;flex-direction:column;gap:4px;min-width:0}
      .r25-vhead .nm b{font:700 24px/1 var(--f-display);direction:ltr;text-align:right}
      .r25-vhead .nm span{display:flex;gap:9px;font-size:12px;color:var(--muted)}
      .r25-vhead .nm span em{font-style:normal;display:flex;align-items:center;gap:4px;color:var(--ink);font-weight:600}
      .r25-vhead .nm span em i{width:9px;height:9px;display:inline-block}
      .r25-vhead .chev{margin-inline-start:auto;font-size:20px;color:var(--muted)}
      .r25-back{display:flex;align-items:center;gap:6px;min-height:44px;border:0;background:none;color:var(--glacier);font:inherit;font-weight:600;padding:0}
      #panel h2.r25-vtitle{display:flex;align-items:center;gap:10px}
      #panel h2.r25-vtitle i{width:8px;height:30px;flex:none}
      #panel h2.r25-vtitle bdi{direction:ltr}
      .r25-hov{filter:drop-shadow(0 0 0 transparent)}
      .r25-index button.hov{background:var(--snow);box-shadow:inset 3px 0 0 var(--glacier)}
      .r25-cmini .mapwrap{height:230px!important;min-height:0!important}
    `;
    document.head.appendChild(s);
  }
  // the site's map is a view box over projected metres: read it, and move it with the site's own wheel handler
  const VB=()=>{const v=svg().getAttribute('viewBox').split(/\s+/).map(Number);return{x:v[0],y:v[1],w:v[2],h:v[3]};};
  function wheel(f,px,py){const s=svg(),r=s.getBoundingClientRect();s.dispatchEvent(new WheelEvent('wheel',{deltaY:Math.log(f)/0.0015,clientX:r.left+px,clientY:r.top+py,cancelable:true,bubbles:true}));}
  function frame(b,pad){ // b: [x0,y0,x1,y1] in the site's metres
    const s=svg(),r=s.getBoundingClientRect(),cw=r.width,ch=r.height,top=52; // the toolbar row over the map
    const tx=(b[0]+b[2])/2,ty=(b[1]+b[3])/2;
    for(let i=0;i<12;i++){const v=VB(),u=v.w/cw,cx=v.x+v.w/2,cy=v.y+v.h/2,Tx=tx-cx,Ty=ty-cy;if(Math.abs(Tx)<u&&Math.abs(Ty)<u)break;
      // zoom in by 2 at p1, out by 2 at p2: a move of u/2*(p1-p2)
      const clamp=(q,m)=>Math.max(4,Math.min(m-4,q));const dx=Math.max(-cw/2+4,Math.min(cw/2-4,Tx/u)),dy=Math.max(-ch/2+4,Math.min(ch/2-4,Ty/u));
      wheel(.5,clamp(cw/2+dx,cw),clamp(ch/2+dy,ch));wheel(2,clamp(cw/2-dx,cw),clamp(ch/2-dy,ch));}
    const v=VB(),w=Math.max(b[2]-b[0],(b[3]-b[1])*cw/(ch-top))*(pad||1.18);wheel(w/v.w,cw/2,ch/2);
    // nudge down so the toolbar does not cover the top
    const u=VB().w/cw;wheel(.5,cw/2,ch/2+top/4);wheel(2,cw/2,ch/2-top/4);
  }
  const on=(el,show)=>{if(el)el.style.display=show?'':'none';};
  function zones(D,only){
    const s=svg();let g=s.querySelector('#r25-zones');if(g)g.remove();
    const first=s.querySelector('g.pg');if(!first)return;const before=first.parentNode;
    g=mk('g',{id:'r25-zones'},null);before.parentNode.insertBefore(g,before);
    Object.entries(D.sectors).forEach(([k,v])=>{if(only&&only!==k)return;
      const z=mk('g',{style:`opacity:${only?'var(--zone-sel)':'var(--zone-op)'}`},g);
      v.lines.forEach(L=>mk('path',{d:'M'+L.map(q=>q[0].toFixed(0)+' '+q[1].toFixed(0)).join('L'),fill:'none',stroke:`var(--v-${k})`,'stroke-width':v.zw||700,'stroke-linecap':'round','stroke-linejoin':'round'},z));});
  }
  function thin(){ // the whole resort: thin runs without casing, lifts thin and grey, no stations
    const s=svg();
    s.querySelectorAll('g.pg:not(.lbl) path').forEach(p=>{if(p.classList.contains('hit')||p.classList.contains('pg-area'))return;
      const st=p.getAttribute('stroke')||'';if(st.includes('casing')){p.style.display='none';return;}
      if(!p.dataset.w0)p.dataset.w0=p.getAttribute('stroke-width');p.setAttribute('stroke-width',(+p.dataset.w0)*.5);});
    s.querySelectorAll('path.pg-area').forEach(a=>a.setAttribute('fill-opacity',.55));
    s.querySelectorAll('g.lg path').forEach(p=>{if(p.classList.contains('hit'))return;const st=p.getAttribute('stroke')||'';
      if(st.includes('casing'))p.style.display='none';else{p.setAttribute('stroke','var(--muted)');p.setAttribute('stroke-width',.9);p.setAttribute('stroke-opacity',.75);}});
    s.querySelectorAll('g.lg circle').forEach(c=>c.style.display='none');
    s.querySelectorAll('text.lbl.pg, g.lbl.plate, text.lbl.lift').forEach(t=>on(t,false));
  }
  function labels(D,k){ // a valley: its own labels, the longest runs first, then its lifts
    const s=svg(),cw=s.getBoundingClientRect().width,u=VB().w/cw,pad=2*u,kept=[];
    const inV=key=>D.runOf[key]===k;
    s.querySelectorAll('text.peak, text.place-v').forEach(t=>{if(t.style.display==='none')return;const b=t.getBBox();kept.push({a:b.x-pad,b:b.y-pad,c:b.x+b.width+pad,d:b.y+b.height+pad});});
    s.querySelectorAll('g.lbl.plate').forEach(t=>{const me=inV(t.dataset.key);if(!me)on(t,false);else if(t.style.display!=='none'){const b=t.getBBox(),m=t.getCTM&&t.transform.baseVal.consolidate();
      if(m){const e=m.matrix.e,f=m.matrix.f,W=b.width*u/2,Hh=b.height*u/2;kept.push({a:e-W,b:f-Hh,c:e+W,d:f+Hh});}}});
    const v=VB();
    const cand=[...s.querySelectorAll('text.lbl.pg')].filter(t=>{const me=inV(t.dataset.key);on(t,false);return me;})
      .sort((a,b)=>(D.len[b.dataset.key]||0)-(D.len[a.dataset.key]||0));
    const lifts=[...s.querySelectorAll('text.lbl.lift')].filter(t=>{on(t,false);return D.liftLbl[t.textContent.replace('⇡ ','')]===k;});
    [...cand,...lifts].forEach(t=>{on(t,true);const b=t.getBBox(),r={a:b.x-pad,b:b.y-pad,c:b.x+b.width+pad,d:b.y+b.height+pad};
      const out=r.a<v.x+6*u||r.c>v.x+v.w-6*u||r.b<v.y+56*u||r.d>v.y+v.h-6*u;
      if(out||kept.some(q=>r.a<q.c&&r.c>q.a&&r.b<q.d&&r.d>q.b))on(t,false);else kept.push(r);});
    return cand.length;
  }
  function dim(D,k){
    const s=svg();
    s.querySelectorAll('g.pg[data-key]').forEach(g=>{if(g.classList.contains('lbl'))return;g.style.opacity=D.runOf[g.dataset.key]!==k?.16:'';});
    // a wide run's area carries no key: find it by its first point
    s.querySelectorAll('path.pg-area').forEach(a=>{const m=/M\s*(-?[\d.]+)[ ,](-?[\d.]+)/.exec(a.getAttribute('d'));if(!m)return;const x=+m[1],y=+m[2];
      const hit=D.areas.find(q=>Math.abs(q[0]-x)<2&&Math.abs(q[1]-y)<2);if(hit&&D.runOf[hit[2]]!==k)a.style.opacity=.16;});
    s.querySelectorAll('g.lg[data-lid]').forEach(g=>{g.style.opacity=D.liftOf[g.dataset.lid]!==k?.16:'';});
  }
  function vlabs(D,small){
    document.querySelectorAll('.r25-vlab').forEach(e=>e.remove());
    const wrap=document.querySelector('.mapwrap'),s=svg(),r=s.getBoundingClientRect(),wr=wrap.getBoundingClientRect(),v=VB(),u=v.w/r.width;
    Object.entries(D.sectors).forEach(([k,o])=>{const x=(o.label[0]-v.x)/u+r.left-wr.left,y=(o.label[1]-v.y)/u+r.top-wr.top;
      const c=['blue','red','black','green'].filter(q=>o.colors[q]).map(q=>`<em><i class="q-${q}"></i>${o.colors[q]}</em>`).join('');
      const e=H('div',{class:'r25-vlab'+(small?' sm':''),style:`left:${x}px;top:${y}px;--vc:var(--v-${k})`},`<b>${esc(o.name)}</b><span>${c}</span>`);e._x=x;e._y=y;wrap.appendChild(e);});
    // no plate on another: push apart, then keep inside the map
    const L=[...document.querySelectorAll('.r25-vlab')],W=wr.width,Hh=wr.height;
    for(let it=0;it<60;it++){let moved=false;
      for(let i=0;i<L.length;i++)for(let j=i+1;j<L.length;j++){const a=L[i].getBoundingClientRect(),b=L[j].getBoundingClientRect();
        const ox=Math.min(a.right,b.right)-Math.max(a.left,b.left)+6,oy=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top)+6;
        if(ox>0&&oy>0){moved=true;const s=oy<ox*1.4?[0,oy/2]:[ox/2,0],d=L[i]._y<=L[j]._y?1:-1,dx=L[i]._x<=L[j]._x?1:-1;
          if(s[1]){L[i]._y-=s[1]*d;L[j]._y+=s[1]*d;}else{L[i]._x-=s[0]*dx;L[j]._x+=s[0]*dx;}}}
      L.forEach(e=>{const r=e.getBoundingClientRect(),hw=r.width/2,hh=r.height/2;e._x=Math.max(hw+60,Math.min(W-hw-6,e._x));e._y=Math.max(hh+62,Math.min(Hh-hh-50,e._y));e.style.left=e._x+'px';e.style.top=e._y+'px';});
      if(!moved)break;}
  }
  function chips(D,k){
    document.querySelectorAll('.r25-vchips').forEach(e=>e.remove());
    const bar=H('div',{class:'r25-vchips',role:'group','aria-label':'עמקים'});
    bar.appendChild(H('button',{type:'button',class:'all','aria-pressed':String(!k)},'כל ההר'));
    Object.entries(D.sectors).forEach(([key,o])=>bar.appendChild(H('button',{type:'button','aria-pressed':String(key===k)},`<i style="background:var(--v-${key})"></i>${esc(o.name)}`)));
    const tb=document.querySelector('#mapPage .toolbar');tb.after(bar);
    const cur=bar.querySelector('[aria-pressed="true"]');if(cur)bar.scrollLeft+=cur.getBoundingClientRect().left<bar.getBoundingClientRect().left+8?cur.getBoundingClientRect().left-bar.getBoundingClientRect().left-14:0;
  }
  const cnt=o=>['blue','red','black','green'].filter(q=>o.colors[q]).map(q=>`<em><i class="q-${q}"></i>${o.colors[q]}</em>`).join('');
  function list(D,k,hov){
    const pn=document.getElementById('panel');
    if(!k){
      pn.innerHTML=`<h2 class="ov">כל המסלולים</h2><h3>${D.total} מסלולים · ${Object.keys(D.sectors).length} עמקים</h3>`+
        Object.entries(D.sectors).map(([key,o])=>`<button class="r25-vhead"><i class="bar" style="background:var(--v-${key})"></i><span class="nm"><b>${esc(o.name)}</b><span>${cnt(o)}<em>${o.runs} מסלולים</em><em>${o.km} ק״מ</em></span></span><span class="chev">‹</span></button>`).join('');
      return;}
    const o=D.sectors[k];
    pn.innerHTML=`<button class="r25-back">→ כל ההר</button><h2 class="ov r25-vtitle"><i style="background:var(--v-${k})"></i><bdi>${esc(o.name)}</bdi></h2>
      <h3>${o.runs} מסלולים · ${o.km} ק״מ</h3>
      <div class="index r25-index">${o.items.map(([key,name,c,len])=>`<button class="${hov===key?'hov':''}"><span class="sw ${c}"></span>${esc(name)}<span class="len">${fmt(len)}</span></button>`).join('')}</div>`;
    const h=pn.querySelector('.hov');if(h)pn.scrollTop=h.offsetTop-pn.clientHeight/2;
  }
  // D2 (phone, a resort wider than the screen): go round the mountain. The camera stands in one valley and looks in at the massif;
  // a swipe flies it round to the next valley (the Sellaronda is a ring, so the swipe follows it). The valley names are not
  // laid on the 3D any more, where the screen edge cuts them: the valley in front is a sign at the bottom, its neighbours are
  // tabs on the left and right edges (a tap flies there), the one behind the massif is a tab at the top, and a small ring shows
  // where you stand. Nothing is ever cut: every name sits on a screen edge, never past it.
  function orbit(q){
    css();const w=document.querySelector('.mapwrap');if(!w)return 'no wrap';
    document.querySelectorAll('.r3-labels .r25-v3').forEach(e=>e.remove());
    if(!document.getElementById('r25-orb-css')){const s=document.createElement('style');s.id='r25-orb-css';s.textContent=`
      .r25-orb{position:absolute;inset:0;pointer-events:none;z-index:6;direction:ltr;font-family:var(--f-body)}
      .r25-orb .tab{position:absolute;top:57%;display:flex;align-items:center;gap:6px;min-height:52px;padding:6px 10px;box-sizing:border-box;max-width:112px;
        background:var(--paper);border:1.5px solid var(--rule);box-shadow:0 2px 8px var(--shadow);color:var(--ink)}
      .r25-orb .tab.l{left:0;border-left:5px solid var(--vc);border-right-width:1.5px}
      .r25-orb .tab.r{right:0;border-right:5px solid var(--vc);flex-direction:row-reverse;text-align:right}
      .r25-orb .tab b{font:700 17px/1 var(--f-display);white-space:normal}
      .r25-orb .tab svg{flex:none;width:14px;height:22px;stroke:var(--ink);stroke-width:2.4;fill:none}
      .r25-orb .tab.t{top:10px;left:50%;transform:translateX(-50%);min-height:44px;max-width:none;border-top:4px solid var(--vc);gap:7px;padding:5px 11px;opacity:.94}
      .r25-orb .tab.t b{font-size:15px;white-space:nowrap}.r25-orb .tab.t small{font:600 11.5px/1 var(--f-body);color:var(--muted);white-space:nowrap;direction:rtl}
      .r25-orb .front{position:absolute;left:50%;bottom:14px;transform:translateX(-50%);display:flex;align-items:center;gap:12px;padding:8px 12px 8px 10px;
        background:var(--paper);border:1.5px solid var(--rule);border-top:5px solid var(--vc);box-shadow:0 3px 12px var(--shadow)}
      .r25-orb .front .nm{display:flex;flex-direction:column;gap:4px}
      .r25-orb .front b{font:700 24px/1 var(--f-display);color:var(--ink);white-space:nowrap}
      .r25-orb .front span{display:flex;gap:7px;font:600 12px/1 var(--f-body);color:var(--ink)}
      .r25-orb .front span em{font-style:normal;display:flex;align-items:center;gap:3px}.r25-orb .front span em i{width:9px;height:9px;display:inline-block}
      .r25-orb .front .dots{display:flex;gap:5px;margin-top:2px}.r25-orb .front .dots i{width:7px;height:7px;background:var(--rule)}.r25-orb .front .dots i.on{background:var(--ink)}
      .r25-orb .ring{width:58px;height:58px;flex:none}
      .r25-orb .hint{position:absolute;left:50%;bottom:100px;transform:translateX(-50%);display:flex;align-items:center;gap:8px;font:600 12px/1 var(--f-body);color:var(--ink);
        background:color-mix(in srgb,var(--paper) 86%,transparent);padding:6px 10px;direction:rtl;white-space:nowrap}
      .r25-orb .hint svg{width:34px;height:14px;stroke:var(--ink);stroke-width:2;fill:none}`;document.head.appendChild(s);}
    const old=w.querySelector('.r25-orb');old&&old.remove();
    // the run and lift names go (the valley is the unit here); a village or peak name stays only while it fits whole on the screen
    const h=document.getElementById('hint3d');h&&(h.style.display='none');const wr=w.getBoundingClientRect();
    document.querySelectorAll('.r3-labels .r3-lbl').forEach(e=>{if(e.classList.contains('piste')||e.classList.contains('lift')){e.remove();return;}
      const b=e.getBoundingClientRect();if(b.width&&(b.left<wr.left+4||b.right>wr.right-4||b.top<wr.top+60||b.bottom>wr.bottom-4))e.style.visibility='hidden';});
    const o=H('div',{class:'r25-orb'});
    const chev=d=>`<svg viewBox="0 0 14 22"><path d="${d==='l'?'M11 3 3 11l8 8':'M3 3l8 8-8 8'}"/></svg>`;
    if(q.L)o.appendChild(H('div',{class:'tab l',style:`--vc:${q.L.col}`},chev('l')+`<b>${esc(q.L.name)}</b>`));
    if(q.R)o.appendChild(H('div',{class:'tab r',style:`--vc:${q.R.col}`},chev('r')+`<b>${esc(q.R.name)}</b>`));
    if(q.B)o.appendChild(H('div',{class:'tab t',style:`--vc:${q.B.col}`},`<b>${esc(q.B.name)}</b><small>${esc(q.behind)}</small>`));
    // the ring: the valleys round the massif, turned so the one you stand in is at the bottom; the wedge is what the camera sees
    const R=24,c=29;let g=`<circle cx="${c}" cy="${c}" r="${R}" fill="none" stroke="var(--rule)" stroke-width="7"/>`;
    for(const v of q.ring){const a0=v.r-.62,a1=v.r+.62,p=a=>[c+R*Math.sin(a),c+R*Math.cos(a)];const [x0,y0]=p(a0),[x1,y1]=p(a1);
      g+=`<path d="M${x0.toFixed(1)} ${y0.toFixed(1)}A${R} ${R} 0 0 0 ${x1.toFixed(1)} ${y1.toFixed(1)}" fill="none" stroke="${v.col}" stroke-width="${v.cur?9:6}" opacity="${v.cur?1:.55}"/>`;}
    g+=`<path d="M${c} ${c+R-2}L${c-11} ${c-4}A16 16 0 0 1 ${c+11} ${c-4}Z" fill="var(--ink)" opacity=".16"/><circle cx="${c}" cy="${c+R}" r="4" fill="var(--ink)" stroke="var(--paper)" stroke-width="1.5"/>`;
    const f=H('div',{class:'front',style:`--vc:${q.cur.col}`},`<svg class="ring" viewBox="0 0 58 58" aria-hidden="true">${g}</svg>
      <div class="nm"><b>${esc(q.cur.name)}</b><span>${q.cur.counts}</span><div class="dots">${q.ring.map(v=>`<i class="${v.cur?'on':''}"></i>`).join('')}</div></div>`);
    o.appendChild(f);
    if(q.hint)o.appendChild(H('div',{class:'hint'},`<svg viewBox="0 0 34 14"><path d="M2 7h30M8 2 2 7l6 5M26 2l6 5-6 5"/></svg>${esc(q.hint)}`));
    w.appendChild(o);return 'ok';
  }

  window.R25={
    // q: {D, mode:'all'|'valley', k, chips, list:'groups'|'valley', hov, mini}
    apply(q){
      css();const D=q.D,out={};
      if(q.mini)document.body.classList.add('r25-cmini');
      if(q.chips)chips(D,q.mode==='valley'?q.k:null);
      if(q.frame)frame(q.frame,q.pad);
      if(q.mode==='all'){zones(D);thin();vlabs(D,q.small);}
      if(q.mode==='valley'){zones(D,q.k);dim(D,q.k);out.labels=labels(D,q.k);
        // the desk: the pointer over a row lifts its run on the map (a light casing, like the 3D selection), nothing else changes
        if(q.hov)svg().querySelectorAll(`g.pg[data-key="${CSS.escape(q.hov)}"] path`).forEach(p=>{if(p.classList.contains('hit'))return;const st=p.getAttribute('stroke')||'';
          if(st.includes('casing')){p.setAttribute('stroke','#FFD34D');p.setAttribute('stroke-width',11);}else p.setAttribute('stroke-width',5);const g=p.parentNode;g.parentNode.appendChild(g);});}
      if(q.list)list(D,q.list==='valley'?q.k:null,q.hov);
      return out;
    },
    // 3D: the camera low on the valley's side, looking up at the Sella (or the resort's middle)
    view3d(v){const a=window.__v3;if(!a)return 'no 3d';a.view(v);return 'ok';},
    chips(D,k){css();chips(D,k);},
    orbit(q){return orbit(q);}
  };
})();

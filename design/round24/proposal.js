// Round 24: ski routes as a kind of their own, and wide runs that show. Drawn over the real site (design/round24/shots.mjs).
// The routes come into the page as data (shots.mjs adds them to Sölden's runs-and-lifts.json, kind 'ski-route');
// this script only restyles what the site already drew. Not product code: the canvas shows it, the site implements it.
(function(){
  const night=()=>document.documentElement.classList.contains('night')||document.documentElement.dataset.theme==='dark'||matchMedia('(prefers-color-scheme: dark)').matches&&document.documentElement.dataset.theme!=='light';
  function css(){
    if(document.getElementById('r24-css'))return;
    const s=document.createElement('style');s.id='r24-css';s.textContent=`
      :root{--p-route:#D96A00;--route-ink:#1A1206}
      @media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--p-route:#FF9A3D;--route-ink:#1A1206}}
      :root[data-theme="dark"]{--p-route:#FF9A3D}
      .r24-tag{display:inline-flex;align-items:center;gap:8px;min-height:30px;padding:3px 10px;border:2px solid var(--p-route);font-weight:700;font-size:.92rem;line-height:1.25}
      .r24-tag i{flex:none;width:14px;height:14px;background:var(--p-route);transform:rotate(45deg)}
      #panel h2.r24-h{--board:var(--p-route);color:var(--route-ink)}
      .r24-dia{display:inline-block;flex:none;width:11px;height:11px;background:var(--p-route);transform:rotate(45deg);margin-inline-end:6px}
      .r24-legend{position:absolute;inset-inline-end:10px;bottom:34px;z-index:3;display:flex;flex-direction:column;gap:6px;padding:8px 10px;background:var(--paper,#fff);border:1px solid var(--grid);font-size:.8rem;font-weight:600}
      .r24-legend span{display:flex;align-items:center;gap:8px;white-space:nowrap}
      .r24-legend svg{flex:none}`;
    document.head.appendChild(s);
  }
  const NS='http://www.w3.org/2000/svg';
  const mk=(n,a,p)=>{const e=document.createElementNS(NS,n);for(const k in a)e.setAttribute(k,a[k]);p&&p.appendChild(e);return e;};
  function routes(keys){
    let n=0;
    keys.forEach(k=>{
      document.querySelectorAll(`#map2d g.pg[data-key="${CSS.escape(k)}"], svg g.pg[data-key="${CSS.escape(k)}"]`).forEach(g=>{
        if(g.classList.contains('lbl'))return;
        const ps=g.querySelectorAll('path');
        ps.forEach((p,i)=>{if(i%2===0){p.setAttribute('stroke-width',5.5);}else{p.setAttribute('stroke','var(--p-route)');p.setAttribute('stroke-width',2.8);p.setAttribute('stroke-dasharray','7 5');}});
        g.classList.remove('red','black','blue');n++;
      });
      // the plate: a diamond, so it reads as "not a run" even without colour
      document.querySelectorAll(`g.lbl.plate[data-key="${CSS.escape(k)}"]`).forEach(t=>{
        const r=t.querySelector('rect');if(!r)return;const w=k.length>1?40:32;
        const d=mk('polygon',{points:`${-w/2},0 0,${-16} ${w/2},0 0,16`,fill:'var(--p-route)',stroke:'var(--casing)','stroke-width':1.8},null);
        t.replaceChild(d,r);const tx=t.querySelector('text');tx.setAttribute("font-size",15);tx.style.fill='var(--route-ink)';
        t.classList.remove('red','black','blue');
      });
      // a name (Gaislachalm): the label in the route's colour
      document.querySelectorAll(`text.lbl[data-key="${CSS.escape(k)}"]`).forEach(t=>{t.setAttribute('fill','var(--p-route)');t.classList.remove('red','black','blue');});
    });
    return n;
  }
  // wide runs: the area of a run (an OSM polygon) at 30% (36% at night), with a thin edge in the run's colour
  function areas(mode){
    let n=0;
    document.querySelectorAll('path[fill-opacity]').forEach(a=>{
      if(!/var\(--p-/.test(a.getAttribute('fill')||''))return;
      const col=a.getAttribute('fill');
      if(mode==='solid'){a.setAttribute('fill-opacity',night()?.36:.30);a.setAttribute('stroke',col);a.setAttribute('stroke-width',1.3);a.setAttribute('stroke-opacity',.9);a.setAttribute('vector-effect','non-scaling-stroke');a.setAttribute('stroke-linejoin','round');}
      if(mode==='hatch'){const svg=a.ownerSVGElement,c=(col.match(/--p-(\w+)/)||[])[1],id='r24h-'+c;
        if(!svg.querySelector('#'+id)){const defs=svg.querySelector('defs')||mk('defs',{},svg);const pt=mk('pattern',{id,patternUnits:'userSpaceOnUse',width:14,height:14,patternTransform:'rotate(45)'},defs);mk('rect',{width:5,height:14,fill:col},pt);}
        a.setAttribute('fill',`url(#${id})`);a.setAttribute('fill-opacity',.55);a.setAttribute('stroke',col);a.setAttribute('stroke-width',1.3);a.setAttribute('vector-effect','non-scaling-stroke');}
      n++;
    });
    return n;
  }
  function legend(){
    const w=document.querySelector('.mapwrap');if(!w||w.querySelector('.r24-legend'))return;
    const d=document.createElement('div');d.className='r24-legend';
    d.innerHTML=`<span><svg width="34" height="12"><path d="M2 6H32" stroke="var(--casing)" stroke-width="6" stroke-linecap="round"/><path d="M2 6H32" stroke="var(--p-route)" stroke-width="3" stroke-dasharray="7 5"/></svg>דרך סקי</span>
      <span><svg width="34" height="12"><path d="M2 6H32" stroke="var(--casing)" stroke-width="5" stroke-linecap="round"/><path d="M2 6H32" stroke="var(--p-blue)" stroke-width="2.6" stroke-dasharray="8 5"/></svg>דרך מקשרת</span>
      <span><svg width="34" height="14"><rect x="2" y="2" width="30" height="10" fill="var(--p-red)" fill-opacity=".3" stroke="var(--p-red)" stroke-width="1.3"/></svg>מסלול רחב</span>`;
    w.appendChild(d);
  }
  // the run panel of a route: the kind instead of colour and difficulty
  // the run list: ski routes out of the runs, in a section of their own, with the diamond
  function list(keys){
    const idx=document.querySelector('#panel .index');if(!idx)return 0;
    const bs=[...idx.querySelectorAll('button[data-goto]')].filter(b=>keys.includes(b.dataset.goto));if(!bs.length)return 0;
    const h3=document.createElement('h3');h3.textContent=`דרכי סקי · ${bs.length}`;
    const nb=document.createElement('div');nb.className='index';
    bs.forEach(b=>{const sw=b.querySelector('.sw');if(sw){const d=document.createElement('span');d.className='r24-dia';sw.replaceWith(d);}nb.appendChild(b);});
    idx.after(h3,nb);return bs.length;
  }
  // ski routes without a line in the open map: their own row under the missing runs, with the diamond
  function missing(keys){
    const box=document.querySelector('#panel .miss');if(!box)return 0;
    const bs=[...box.querySelectorAll('button[data-goto]')].filter(b=>keys.includes(b.dataset.goto));if(!bs.length)return 0;
    const h=box.previousElementSibling;if(h&&h.tagName==='H3')h.textContent=h.textContent.replace(/\d+/,String(box.children.length-bs.length));
    const h3=document.createElement('h3');h3.textContent=`דרכי סקי בלי קו במפה · ${bs.length}`;
    const nb=document.createElement('div');nb.className='miss';
    bs.forEach(b=>{const sw=b.querySelector('.sw');if(sw){const d=document.createElement('span');d.className='r24-dia';sw.replaceWith(d);}nb.appendChild(b);});
    box.after(h3,nb);return bs.length;
  }
  function panel(k,hut){
    const pn=document.getElementById('panel');if(!pn)return false;
    const h=pn.querySelector('.run-sign h2')||pn.querySelector('h2');if(!h)return false;h.className='r24-h';
    const dl=pn.querySelector('dl.kv');if(!dl)return false;
    const dts=[...dl.querySelectorAll('dt')];
    const find=re=>dts.find(t=>re.test(t.textContent));
    const color=find(/צבע/),diff=find(/קושי/),osm=find(/OSM|במפה הפתוחה/),groom=find(/הכשר|הכנה/);
    if(color){color.textContent='סוג';color.nextElementSibling.innerHTML=(hut?'<span class="r24-tag"><i></i>דרך לבקתה · פרטית, לא מוכשרת</span>':'<span class="r24-tag"><i></i>דרך סקי · לא מוכשרת, לא נבדקת</span>');}
    [diff,osm,groom].forEach(t=>{if(t){t.nextElementSibling.remove();t.remove();}});
    return true;
  }
  window.R24={apply(o){css();const r={};
    if(o.routes){r.routes=routes(o.routes);r.list=list(o.routes);}
    if(o.areas)r.areas=areas(o.areas);
    if(o.legend)legend();
    if(o.panel)r.panel=panel(o.panel,o.hut);
    if(o.missing)r.missing=missing(o.missing);
    return r;}};
})();

// Round 24, the legend that folds (Pini approved going ahead, 7.10.2026). Drawn over the real site (design/round24/shots.mjs, prefix leg-).
// The same component as the Kobi button (.inset-toggle): a 44 px chip with a swatch and a short word, that opens a card upward.
// Not product code: the canvas shows it, the site implements it.
(function(){
  const SW={
    route:'<svg width="34" height="12"><path d="M2 6H32" stroke="var(--casing)" stroke-width="8" stroke-linecap="round"/><path d="M2 6H32" stroke="var(--p-route-edge)" stroke-width="6" stroke-dasharray="2 2.5"/><path d="M2 6H32" stroke="var(--p-route)" stroke-width="3.6"/></svg>',
    hut:'<svg width="34" height="12"><path d="M2 6H32" stroke="var(--casing)" stroke-width="4" stroke-linecap="round"/><path d="M2 6H32" stroke="var(--p-route-edge)" stroke-width="1.8" stroke-dasharray="4 3"/></svg>',
    way:'<svg width="34" height="12"><path d="M2 6H32" stroke="var(--casing)" stroke-width="5" stroke-linecap="round"/><path d="M2 6H32" stroke="var(--p-blue)" stroke-width="2.6" stroke-dasharray="8 5"/></svg>',
    area:'<svg width="34" height="14"><rect x="2" y="2" width="30" height="10" fill="var(--p-red)" stroke="var(--casing)" stroke-width="1.5"/></svg>'};
  const T={route:'דרך סקי',hut:'דרך לבקתה',way:'דרך מקשרת',area:'מסלול רחב'};
  // the chip's swatch: the first kind this resort has, small, so the chip says "lines" before it says "legend"
  const ICON='<svg width="22" height="16" aria-hidden="true"><path d="M2 4H20" stroke="var(--p-route)" stroke-width="3"/><path d="M2 8H20" stroke="var(--p-blue)" stroke-width="2" stroke-dasharray="4 3"/><rect x="2" y="11" width="18" height="4" fill="var(--p-red)"/></svg>';
  function css(){
    if(document.getElementById('r24l-css'))return;
    const s=document.createElement('style');s.id='r24l-css';s.textContent=`
      .mlegend{display:none!important}
      .r24l{position:absolute;bottom:12px;inset-inline-end:12px;z-index:3;display:flex;flex-direction:column;align-items:flex-end;gap:6px}
      .r24l-card{display:flex;flex-direction:column;gap:7px;padding:10px 12px 12px;background:var(--paper);border:1.5px solid var(--rule);font-size:.82rem;font-weight:600;color:var(--ink);min-width:150px}
      .r24l-head{display:flex;align-items:center;justify-content:space-between;gap:10px;font-weight:700;font-size:.9rem}
      .r24l-head b{font-weight:700}
      .r24l-x{width:44px;height:44px;margin-block:-12px -10px;margin-inline-end:-12px;border:0;background:none;color:var(--muted);font-size:16px}
      .r24l-card span{display:flex;align-items:center;gap:8px;white-space:nowrap}
      .r24l-card svg{flex:none}
      .r24l-chip{display:inline-flex;align-items:center;gap:7px;height:44px;padding:0 14px;font:inherit;font-weight:600;font-size:14px;border:1.5px solid var(--rule);background:var(--paper);color:var(--ink)}
      .r24l-chip[aria-expanded=true]{border-color:var(--ink)}
      .mapwrap.r24l-on .scale{bottom:64px}
      .mapwrap.r24l-open .scale{visibility:hidden}`;
    document.head.appendChild(s);
  }
  window.R24L={apply(o){
    css();
    const wrap=document.querySelector('#view-map .mapwrap')||document.querySelector('.mapwrap');
    const kinds=o.kinds||['route','hut','way','area'].filter(k=>{const lg=document.querySelector('.mlegend');return lg&&lg.textContent.includes(T[k]);});
    const box=document.createElement('div');box.className='r24l';
    const card=o.open?`<div class="r24l-card" id="r24lCard"><div class="r24l-head"><b>מקרא</b><button type="button" class="r24l-x" aria-label="סגירת המקרא">✕</button></div>${kinds.map(k=>`<span>${SW[k]}${T[k]}</span>`).join('')}</div>`:'';
    box.innerHTML=card+`<button type="button" class="r24l-chip" aria-expanded="${!!o.open}" aria-controls="r24lCard">${ICON}<span>מקרא</span></button>`;
    wrap.appendChild(box);wrap.classList.add('r24l-on');if(o.open)wrap.classList.add('r24l-open');
    return{kinds};
  }};
})();

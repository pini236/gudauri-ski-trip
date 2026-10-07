// Round 22 proposal: the map as the main screen. Drawn over the real site for the canvas only (not product code).
// window.R22.apply({v: 'A'|'B'|'C'|'D', st: 'pre'|'during'|'none', sheet: 'menu'|'ticket'|null, mapImg})
(function () {
  const I = {
    plane: '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M2 13.5v-2l8-4.5V2.5a1.5 1.5 0 0 1 3 0V7l8 4.5v2l-8-2.5v5l2.5 2v1.5L12 18.5 8.5 19.5V18l2.5-2v-5z"/></svg>',
    cal: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="1"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>',
    lift: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2 5l20-3M12 3.5V9"/><rect x="7" y="9" width="10" height="9" rx="1"/><path d="M7 13h10"/></svg>',
    snow: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M12 2v20M3.3 7l17.4 10M3.3 17L20.7 7"/></svg>',
    menu: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg>',
    layers: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M12 3l9 5-9 5-9-5z"/><path d="M3 13l9 5 9-5"/></svg>',
    mtn: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><path d="M2 20l7-12 4 6 3-4 6 10z"/></svg>',
    ticket: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 7h18v3a2 2 0 0 0 0 4v3H3v-3a2 2 0 0 0 0-4z"/><path d="M14 7v10" stroke-dasharray="2 2"/></svg>',
    game: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M8 10c2 2 6 2 8 0"/></svg>',
    more: '<svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>',
    chev: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>',
  };
  const css = `
  .r22-full .page-map .mhead,.r22-full .page-map .toolbar,.r22-full #panel,.r22-full .topbar,.r22-full .ctrls #compass{display:none!important}
  .r22-full .page-map,.r22-full .mbody,.r22-full .mcol{height:100svh}
  .r22-full .mapwrap{height:100svh!important;flex:1!important;border:0!important}
  .r22-full body{overflow:hidden}
  .r22-full .ctrls{top:112px!important}
  .r22-full .north{top:112px!important}
  .r22-full .mstat{top:62px!important}
  .r22-full .inset{display:none!important}
  .r22-top{position:fixed;top:10px;inset-inline:12px;display:flex;align-items:center;gap:8px;z-index:60;pointer-events:none}
  .r22-top>*{pointer-events:auto}
  .r22-resort{display:flex;align-items:center;gap:6px;height:44px;padding:0 14px;background:var(--paper);color:var(--ink);border:1.5px solid var(--ink);font:700 22px/1 var(--f-head)}
  .r22-resort small{font:600 12px var(--f-body);color:var(--muted)}
  .r22-sp{flex:1}
  .r22-ib{display:grid;place-items:center;width:44px;height:44px;background:var(--paper);color:var(--ink);border:1.5px solid var(--ink)}
  .r22-strip{position:fixed;inset-inline:10px;bottom:12px;z-index:60;display:flex;align-items:stretch;min-height:64px;background:var(--paper);color:var(--ink);box-shadow:0 6px 18px rgba(13,21,34,.22);transform:rotate(-1deg)}
  .r22-strip .m{flex:1;display:flex;align-items:center;gap:12px;padding:10px 14px;min-width:0}
  .r22-strip .m .t{display:flex;flex-direction:column;gap:2px;min-width:0}
  .r22-strip .m .t b{font:700 26px/1 var(--f-head);direction:ltr;text-align:start}
  .r22-strip .m .t span{font:500 13px var(--f-body);color:var(--muted)}
  .r22-strip .s{display:flex;flex-direction:column;align-items:center;justify-content:center;min-width:78px;padding:6px 10px;border-inline-start:2px dashed var(--rule);background:var(--p-blue);color:var(--on-board)}
  .r22-strip .s b{font:700 34px/1 var(--f-head)}
  .r22-strip .s small{font:600 12px var(--f-body)}
  .r22-strip .kv{display:flex;gap:14px;align-items:center}
  .r22-strip .kv span{display:flex;align-items:center;gap:5px;font:700 17px var(--f-body);color:var(--ink)}
  .r22-strip .kv i{display:inline-block;width:9px;height:9px;border-radius:50%;background:var(--p-green)}
  .r22-strip.none .m b{font-size:24px}
  .r22-strip.none .s{background:var(--ink);color:var(--paper)}
  .r22-scrim{position:fixed;inset:0;background:rgba(13,21,34,.42);z-index:70}
  .r22-sheet{position:fixed;inset-inline:0;bottom:0;z-index:71;max-height:86svh;overflow:hidden;background:var(--bg);padding:14px 16px 22px;box-shadow:0 -6px 24px rgba(13,21,34,.25)}
  .r22-sheet .grab{width:44px;height:5px;margin:0 auto 12px;background:var(--rule)}
  .r22-sheet .post{margin-top:6px}
  .r22-sheet .ticket-wrap{margin:0}
  .r22-tabs{position:fixed;inset-inline:0;bottom:0;z-index:60;display:grid;grid-template-columns:repeat(4,1fr);height:64px;background:var(--paper);border-top:1.5px solid var(--ink)}
  .r22-tabs a{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;font:600 12px var(--f-body);color:var(--muted);text-decoration:none}
  .r22-tabs a.on{color:var(--ink)}
  .r22-tabs a.on svg{color:var(--p-blue)}
  .r22-tabs a.on::before{content:"";position:absolute;top:0;width:25%;height:3px;background:var(--p-blue)}
  .r22-d .mapwrap{height:calc(100svh - 64px)!important}
  .r22-d .r22-strip{bottom:76px}
  .r22-stub{position:fixed;top:112px;inset-inline-start:12px;z-index:60;display:flex;flex-direction:column;align-items:center;justify-content:center;width:72px;padding:8px 6px 10px;background:var(--p-blue);color:var(--on-board);transform:rotate(3deg);box-shadow:0 4px 12px rgba(13,21,34,.25);
    -webkit-mask:radial-gradient(circle 4px at 50% 0,transparent 98%,#000) 0 0/12px 100% repeat-x;mask:radial-gradient(circle 4px at 50% 0,transparent 98%,#000) 0 0/12px 100% repeat-x}
  .r22-stub b{font:700 34px/1 var(--f-head)} .r22-stub small{font:600 11px var(--f-body);text-align:center}
  .r22-pole{position:fixed;bottom:14px;inset-inline-end:14px;z-index:60;display:flex;flex-direction:column;gap:8px;padding-inline-start:10px}
  .r22-pole::before{content:"";position:absolute;inset-block:-14px -14px;inset-inline-start:0;width:6px;background:var(--ink)}
  .r22-pole a{display:flex;align-items:center;min-height:44px;padding:0 14px 0 26px;background:var(--p-blue);color:var(--on-board);font:700 20px var(--f-head);text-decoration:none;clip-path:polygon(0 50%,16px 0,100% 0,100% 100%,16px 100%)}
  .r22-pole a.g{background:var(--p-green)} .r22-pole a.r{background:var(--p-red)} .r22-pole a.k{background:var(--ink);color:var(--paper)}
  .r22-win{position:relative;display:block;height:300px;margin:0 0 18px;overflow:hidden;border:1.5px solid var(--ink);background:var(--bg)}
  .r22-win img{width:100%;height:100%;object-fit:cover;display:block}
  .r22-win .go{position:absolute;bottom:10px;inset-inline-start:10px;display:flex;align-items:center;gap:6px;height:44px;padding:0 14px;background:var(--ink);color:var(--paper);font:700 20px var(--f-head)}
  .r22-win .tag{position:absolute;top:10px;inset-inline-start:10px;display:flex;align-items:center;gap:8px;padding:6px 10px;background:var(--paper);color:var(--ink);font:600 13px var(--f-body);border:1.5px solid var(--ink)}
  .r22-win .tag i{display:inline-block;width:9px;height:9px;border-radius:50%;background:var(--p-green)}
  @media (min-width:900px){
    .r22-full .topbar{display:flex!important} .r22-full .page-map,.r22-full .mbody,.r22-full .mcol{height:auto} .r22-full .mbody{display:block!important}
    .r22-full .mapwrap{height:calc(100svh - 70px)!important}
    .r22-strip{position:absolute;inset-inline:auto 16px;bottom:16px;width:420px}
    .r22-top{position:fixed;top:78px;inset-inline:16px} .r22-full .mstat{top:128px!important} .r22-full .ctrls,.r22-full .north{top:178px!important}
  }`;
  const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
  const h = (html) => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstChild; };

  // the folded ticket: what the strip says before, during and without a trip (sample numbers in the "during" state)
  function strip(state) {
    if (state === 'none') return h(`<div class="r22-strip none"><div class="m">${I.cal}<div class="t"><b style="direction:rtl">מתי טסים?</b><span>הטיסה, הספירה וימי הסקי. בלי הרשמה</span></div></div><div class="s">${I.plane}<small>הוספה</small></div></div>`);
    if (state === 'during') return h(`<div class="r22-strip"><div class="m"><div class="kv"><span>${I.snow} ‎-6°</span><span>${I.lift} 9/12</span><span><i></i>רוח חלשה</span></div></div><div class="s"><small>יום סקי</small><b>2/4</b></div></div>`);
    return h(`<div class="r22-strip"><div class="m">${I.plane}<div class="t"><b>TLV → TBS</b><span>10.1 · 6H 897</span></div></div><div class="s"><small>עוד</small><b>96</b><small>ימים</small></div></div>`);
  }
  const top = (extra = '') => h(`<div class="r22-top"><span class="r22-resort">גודאורי ${I.chev}</span><span class="r22-sp"></span>${extra}<button class="r22-ib" aria-label="שכבות">${I.layers}</button><button class="r22-ib" aria-label="תפריט">${I.menu}</button></div>`);

  function sheet(kind) {
    document.body.appendChild(h('<div class="r22-scrim"></div>'));
    const s = h('<div class="r22-sheet"><div class="grab"></div></div>');
    if (kind === 'menu') { const post = document.querySelector('nav.post').cloneNode(true); post.querySelector('a[href="#map"]')?.remove(); post.querySelectorAll('[hidden]').forEach(e => e.remove());
      const set = h('<a class="board" href="#about" style="background:var(--ink);color:var(--paper)"><b>הגדרות</b></a>'); post.appendChild(set); s.appendChild(post); }
    if (kind === 'ticket') { const tw = document.querySelector('.ticket-wrap'); if (tw) { const c = tw.cloneNode(true); c.querySelectorAll('.bp-hint').forEach(e => e.remove()); s.appendChild(c); } }
    document.body.appendChild(s);
  }

  window.R22 = { apply(o) {
    const html = document.documentElement;
    if (o.v === 'APP') { // over the app's overview map (emulator shot), the same folded ticket
      document.body.innerHTML = `<img alt="" src="${o.appImg}" style="position:fixed;inset:0;width:100%;height:100%;object-fit:cover">`;
      document.body.appendChild(strip(o.st)); return; }
    if (o.v === 'B') { // a window onto the mountain on top of the home page
      const ticket = document.querySelector('.ticket-wrap');
      const win = h(`<a class="r22-win" href="#map"><img alt="" src="${o.mapImg}"><span class="tag"><i></i>${o.st === 'during' ? '9/12 רכבלים · ‎-6°' : 'גודאורי · 27 מסלולים'}</span><span class="go">למפה ←</span></a>`);
      ticket.parentNode.insertBefore(win, ticket);
      document.querySelectorAll('.home-hero h1, .hero h1').forEach(e => e.style.fontSize = '');
      return;
    }
    html.classList.add('r22-full');
    if (o.v === 'A') { document.body.appendChild(top()); document.body.appendChild(strip(o.st)); }
    if (o.v === 'C') {
      document.body.appendChild(top().cloneNode(true));
      document.querySelector('.r22-top [aria-label="תפריט"]').remove();
      if (o.st !== 'none') document.body.appendChild(h(o.st === 'during' ? `<div class="r22-stub"><small>יום סקי</small><b>2/4</b><small>‎-6° · 9/12</small></div>` : `<div class="r22-stub"><small>עוד</small><b>96</b><small>ימים</small></div>`));
      else document.body.appendChild(h(`<div class="r22-stub" style="background:var(--ink);color:var(--paper)">${I.cal}<small>מתי<br>טסים?</small></div>`));
      document.body.appendChild(h(`<nav class="r22-pole"><a class="g" href="#games">משחקים</a><a href="#meet">מפגש</a><a class="r" href="#group">קבוצה</a><a class="k" href="#about">הגדרות</a></nav>`));
    }
    if (o.v === 'D') { html.classList.add('r22-d');
      document.body.appendChild(top().cloneNode(true)); document.querySelector('.r22-top [aria-label="תפריט"]').remove();
      document.body.appendChild(strip(o.st));
      document.body.appendChild(h(`<nav class="r22-tabs"><a class="on" href="#map">${I.mtn}ההר</a><a href="#trip">${I.ticket}הטיול</a><a href="#games">${I.game}משחקים</a><a href="#about">${I.more}עוד</a></nav>`));
    }
    window.dispatchEvent(new Event('resize'));
    setTimeout(() => document.getElementById('zfit')?.click(), 150);
    if (o.sheet) sheet(o.sheet);
  } };
})();

// Round 23 proposal: several ski resorts (decision 68). Drawn over the real site for the canvas only (not product code).
// window.R23.apply({resort: 'gud'|'sol', picker: true|false, plates: true|false, sel: '<run key>'|null})
(function () {
  const NS = 'http://www.w3.org/2000/svg';
  const RES = {
    gud: { name: 'גודאורי', place: 'גודאורי, גאורגיה', country: 'גאורגיה', runs: 27, lifts: 12, top: 'גודאורי 2027' },
    sol: { name: 'Sölden', place: 'Sölden, אוסטריה', country: 'אוסטריה', runs: 104, lifts: 39, alt: '1,350–3,340', km: 144 },
  };
  const chev = '<svg class="r23-chev" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>';
  const check = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true"><path d="M5 12l5 5 9-10"/></svg>';
  const css = `
  .r23-btn{display:inline-flex;align-items:center;gap:6px;min-height:44px;padding:0 10px;margin-inline-start:-10px;background:transparent;border:0;color:inherit;font:inherit;cursor:pointer}
  .r23-btn .r23-chev{flex:none;opacity:.8}
  .topbar .brand.r23-btn{margin-inline-start:0}
  .r23-scrim{position:fixed;inset:0;background:rgba(13,21,34,.42);z-index:90}
  .r23-sheet{position:fixed;inset-inline:0;bottom:0;z-index:91;background:var(--bg);padding:12px 16px 34px;box-shadow:0 -6px 24px rgba(13,21,34,.25)}
  .r23-sheet .grab{width:44px;height:5px;margin:0 auto 14px;background:var(--rule)}
  .r23-pop{position:fixed;top:60px;z-index:91;width:360px;background:var(--bg);padding:14px 14px 16px;border:1.5px solid var(--ink);box-shadow:0 10px 28px rgba(13,21,34,.22)}
  .r23-list{position:relative;display:flex;flex-direction:column;gap:12px;padding-inline-start:18px}
  .r23-list::before{content:"";position:absolute;inset-block:-6px -4px;inset-inline-start:6px;width:6px;background:var(--pole,#8A94A3)}
  .r23-res{display:flex;align-items:center;gap:12px;min-height:64px;padding:8px 16px 8px 34px;background:var(--p-blue);color:var(--on-board);text-decoration:none;clip-path:polygon(0 50%,22px 0,100% 0,100% 100%,22px 100%)}
  .r23-res .t{flex:1;display:flex;flex-direction:column;gap:3px;min-width:0}
  .r23-res b{font:700 32px/1 var(--f-display)}
  .r23-res span{font:500 13px var(--f-body);opacity:.92}
  .r23-res.off{background:var(--ink);color:var(--paper);width:94%;box-sizing:border-box}
  .r23-res.on{box-shadow:inset 0 0 0 3px var(--on-board)}
  .r23-res .ck{display:grid;place-items:center;width:30px;height:30px;border-radius:50%;background:var(--on-board);color:var(--p-blue)}
  /* the resort pass: the home card of a resort with no trip */
  .r23-pass{max-width:440px;background:var(--paper);overflow:hidden;box-shadow:0 10px 24px var(--shadow);transform:rotate(-1.5deg);margin:6px 0 0}
  .r23-pass .top{display:flex;justify-content:space-between;padding:8px 14px;background:var(--p-blue);color:var(--on-board);font:700 12px var(--f-body);letter-spacing:.06em}
  .r23-pass .body{padding:14px 16px 16px;display:flex;flex-direction:column;gap:12px;color:var(--ink)}
  .r23-pass .nm{display:flex;align-items:baseline;justify-content:space-between;gap:10px}
  .r23-pass .nm b{font:700 54px/0.95 var(--f-display)}
  .r23-pass .nm span{font:600 14px var(--f-body);color:var(--muted)}
  .r23-pass .kv{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;border-top:2px dashed var(--rule);padding-top:12px}
  .r23-pass .kv div{display:flex;flex-direction:column;gap:2px}
  .r23-pass .kv small{font:600 12px var(--f-body);color:var(--muted)}
  .r23-pass .kv b{font:700 26px/1 var(--f-display);direction:ltr;text-align:start}
  .r23-pass .chips{display:flex;gap:6px}
  .r23-pass .chips i{flex:1;height:8px}
  .r23-sol .pano,.r23-sol .sky-phase{display:none!important}
  .r23-sol .ticket-wrap>*:not(.r23-pass){display:none!important}
  .r23-sol .board-meet,.r23-sol .board-group,.r23-sol .tb-count,.r23-sol .signs a[data-nav="meet"]{display:none!important}
  .r23-sol .home-top .when{display:none}
  /* number plates on the map */
  .r23-plate rect{stroke:var(--casing);stroke-width:1.6;vector-effect:non-scaling-stroke}
  .r23-plate text{font-family:var(--f-display);font-weight:700;fill:var(--on-board)}
  `;
  const R = {};
  const el = (h) => { const t = document.createElement('template'); t.innerHTML = h.trim(); return t.content.firstElementChild; };
  const mk = (n, a, p) => { const e = document.createElementNS(NS, n); for (const k in a) e.setAttribute(k, a[k]); if (p) p.appendChild(e); return e; };
  const desk = () => innerWidth >= 900;

  function listHtml(cur) {
    return `<div class="r23-list">${Object.entries(RES).map(([k, r]) => `<a class="r23-res ${k === cur ? 'on' : 'off'}" href="#"><span class="t"><b>${r.name}</b><span>${r.country} · ${r.runs} מסלולים · ${r.lifts} רכבלים</span></span>${k === cur ? `<span class="ck">${check}</span>` : ''}</a>`).join('')}</div>`;
  }

  function turnIntoButton(node, label) {
    if (!node) return;
    node.classList.add('r23-btn'); node.innerHTML = `<span>${label}</span>${chev}`;
  }

  function solHome(r) {
    document.documentElement.classList.add('r23-sol');
    const tw = document.querySelector('.ticket-wrap');
    if (tw && !tw.querySelector('.r23-pass')) tw.prepend(el(`<article class="r23-pass" aria-label="${r.name}">
      <div class="top"><span dir="ltr">SKI AREA · ${r.name.toUpperCase()}</span><span>${r.country}</span></div>
      <div class="body"><div class="nm"><b dir="ltr">${r.name}</b><span>Ötztal</span></div>
        <div class="kv"><div><small>מסלולים</small><b>${r.runs}</b></div><div><small>רכבלים</small><b>${r.lifts}</b></div><div><small>גובה, מ׳</small><b>${r.alt}</b></div></div>
        <div class="chips" aria-hidden="true"><i style="background:var(--p-blue);flex:${76}"></i><i style="background:var(--p-red);flex:${40}"></i><i style="background:var(--p-black);flex:${29}"></i></div></div></article>`));
    document.querySelectorAll('[data-i18n="nav.gudauri_time"]').forEach(n => n.innerHTML = 'השעה ב<bdi>' + r.name + '</bdi>');
  }

  // A small plate in the run's colour with its number, upright, sitting on the line where nothing else passes.
  function plates(sel) {
    const svg = document.getElementById('map');
    svg.querySelectorAll('.r23-plate').forEach(e => e.remove());
    svg.querySelectorAll('text.lbl.pg').forEach(t => t.style.visibility = 'hidden');
    const vb = svg.viewBox.baseVal, u = vb.width / svg.clientWidth;
    const L = [];
    svg.querySelectorAll('path.hit[data-key]').forEach(p => { const len = p.getTotalLength(), n = Math.max(2, Math.ceil(len / (6 * u))), pts = []; for (let i = 0; i <= n; i++) { const q = p.getPointAtLength(len * i / n); pts.push([q.x, q.y]); } L.push({ key: p.dataset.key, pts, len }); });
    const color = k => { const g = svg.querySelector(`g.pg[data-key="${CSS.escape(k)}"]`); return g ? [...g.classList].find(c => ['green', 'blue', 'red', 'black'].includes(c)) || 'blue' : 'blue'; };
    // example numbers (the real ones come from the data: ref on the run); Gudauri's map is only the stage here
    const keys = [...new Set(L.map(l => l.key))].filter(k => !/^u\d+$/.test(k)).sort();
    const num = {}; keys.forEach((k, i) => num[k] = String(i + 1 + (i > 8 ? 20 : 0)));
    const top = mk('g', { class: 'r23-plate-layer' }, svg);
    const boxes = [];
    const order = keys.filter(k => !sel || k === sel).sort((a, b) => Math.max(...L.filter(l => l.key === b).map(l => l.len)) - Math.max(...L.filter(l => l.key === a).map(l => l.len)));
    const H = 20 * u;
    for (const k of order) {
      const t = num[k], W = (t.length > 1 ? 30 : 22) * u;
      const own = L.filter(l => l.key === k).sort((a, b) => b.len - a.len)[0];
      const others = L.filter(l => l.key !== k).flatMap(l => l.pts);
      const n = own.pts.length, cand = [];
      for (let s = 0; s < n; s++) { const i = Math.round(n / 2 + (s % 2 ? 1 : -1) * Math.ceil(s / 2)); if (i > 1 && i < n - 2) cand.push(i); }
      let ok = null;
      for (const i of cand) {
        const [x, y] = own.pts[i];
        const b = { x: x - W / 2 - 3 * u, y: y - H / 2 - 3 * u, w: W + 6 * u, h: H + 6 * u };
        if (b.x < vb.x + 60 * u || b.x + b.w > vb.x + vb.width - 6 * u || b.y < vb.y + 52 * u || b.y + b.h > vb.y + vb.height - 6 * u) continue;
        if (others.some(([px, py]) => px > b.x && px < b.x + b.w && py > b.y && py < b.y + b.h)) continue;
        if (boxes.some(o => b.x < o.x + o.w && b.x + b.w > o.x && b.y < o.y + o.h && b.y + b.h > o.y)) continue;
        ok = [x, y, b]; break;
      }
      if (!ok) continue;
      boxes.push(ok[2]);
      const g = mk('g', { class: 'r23-plate', transform: `translate(${ok[0]} ${ok[1]})` }, top);
      mk('rect', { x: -W / 2, y: -H / 2, width: W, height: H, fill: `var(--p-${color(k)})` }, g);
      const tx = mk('text', { x: 0, y: 1 * u, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-size': 17 * u }, g); tx.textContent = t;
    }
    return boxes.length;
  }

  R.apply = function (o = {}) {
    if (!document.getElementById('r23css')) { const s = document.createElement('style'); s.id = 'r23css'; s.textContent = css; document.head.appendChild(s); }
    const r = RES[o.resort || 'gud'];
    if (o.resort === 'sol') solHome(r);
    // the place name becomes the picker: the brand on desktop, the place title on the phone home, the map title on the phone map
    turnIntoButton(document.querySelector('.topbar .brand'), o.resort === 'sol' ? r.name : r.top);
    if (!desk()) turnIntoButton(document.querySelector('.home-top .loc'), r.place);
    else if (o.resort === 'sol') document.querySelector('.home-top .loc').textContent = r.place;
    turnIntoButton(document.querySelector('#mapPage .mhead h1, .page-map .mhead h1'), r.name);
    if (o.picker) {
      if (desk()) { const br = document.querySelector('.topbar .brand').getBoundingClientRect(); const p = el(`<div class="r23-pop">${listHtml(o.resort || 'gud')}</div>`); p.style.insetInlineStart = Math.max(12, innerWidth - br.right) + 'px'; document.body.append(p); }
      else { document.body.append(el('<div class="r23-scrim"></div>')); document.body.append(el(`<div class="r23-sheet"><div class="grab"></div>${listHtml(o.resort || 'gud')}</div>`)); }
    }
    if (o.plates) return plates(o.sel);
    return 0;
  };
  window.R23 = R;
})();

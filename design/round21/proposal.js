// Round 21 proposal, drawn on top of the real overview map (#map) for the canvas only. Not product code.
// window.R21.apply({along, ends, unnamed, arrows, sel}) redraws the run labels and markers for the current view.
(function () {
  const NS = 'http://www.w3.org/2000/svg';
  const svg = document.getElementById('map');
  let TM = null;
  const R = {};
  R.ready = fetch('/data/terrain.json').then(r => r.json()).then(t => { TM = window.GudRelief.load(t); });

  const mk = (n, a, p) => { const e = document.createElementNS(NS, n); for (const k in a) e.setAttribute(k, a[k]); if (p) p.appendChild(e); return e; };
  // every line of every run, as svg-unit points sampled along the hit paths the site already draws
  function lines() {
    const out = [];
    svg.querySelectorAll('path.hit[data-key]').forEach(p => {
      const L = p.getTotalLength(), n = Math.max(2, Math.ceil(L / 12)), pts = [];
      for (let i = 0; i <= n; i++) { const q = p.getPointAtLength(L * i / n); pts.push([q.x, q.y]); }
      out.push({ key: p.dataset.key, pts, len: L });
    });
    return out;
  }
  function liftPts() {
    const out = [];
    svg.querySelectorAll('path.hit[data-lift]').forEach(p => { const L = p.getTotalLength(), n = Math.ceil(L / 12); for (let i = 0; i <= n; i++) { const q = p.getPointAtLength(L * i / n); out.push([q.x, q.y]); } });
    return out;
  }
  const meta = () => { const vb = svg.viewBox.baseVal; return { u: vb.width / svg.clientWidth, vb }; };
  const named = k => !/^u\d+$/.test(k);

  R.apply = async function (o = {}) {
    await R.ready;
    svg.querySelectorAll('.r21').forEach(e => e.remove()); R.rings = [];
    const { u } = meta();
    const L = lines(), LP = liftPts();
    const layer = mk('g', { class: 'r21' }, svg.querySelector('text.lbl')?.parentNode || svg);
    const ends = mk('g', { class: 'r21' }, layer.parentNode); layer.parentNode.insertBefore(ends, layer);
    const color = k => { const g = svg.querySelector(`g.pg[data-key="${CSS.escape(k)}"]`); return g ? [...g.classList].find(c => ['green', 'blue', 'red', 'black'].includes(c)) : 'blue'; };

    // C. unnamed segments: thin, solid, quiet. The dash stays for ski ways (and for closed runs).
    if (o.unnamed) svg.querySelectorAll('g.pg.unnamed').forEach(g => { const ps = g.querySelectorAll('path'); if (ps.length < 2) return; ps[0].setAttribute('stroke-width', 3.2); ps[1].removeAttribute('stroke-dasharray'); ps[1].setAttribute('stroke-width', 1.4); ps[1].setAttribute('stroke-opacity', .55); });

    // B. open end: where a partial run stops with no lift or run near it, the line fades out into a small open ring
    if (o.ends) {
      // one open end: the line goes on a little in fading dots and stops in a ring with a question mark
      const openEnd = (e, back, c) => {
        const a = Math.atan2(e[1] - back[1], e[0] - back[0]);
        const tail = 40 * u, x2 = e[0] + Math.cos(a) * tail, y2 = e[1] + Math.sin(a) * tail;
        const gid = 'r21g' + Math.random().toString(36).slice(2, 7);
        const gr = mk('linearGradient', { id: gid, gradientUnits: 'userSpaceOnUse', x1: e[0], y1: e[1], x2, y2 }, ends);
        mk('stop', { offset: 0, 'stop-color': `var(--p-${c})`, 'stop-opacity': .9 }, gr); mk('stop', { offset: 1, 'stop-color': `var(--p-${c})`, 'stop-opacity': .15 }, gr);
        mk('line', { x1: e[0], y1: e[1], x2, y2, stroke: `url(#${gid})`, 'stroke-width': 3.2, 'stroke-dasharray': '1 6', 'stroke-linecap': 'round', 'vector-effect': 'non-scaling-stroke' }, ends);
        const cx = x2 + Math.cos(a) * 9 * u, cy = y2 + Math.sin(a) * 9 * u;
        mk('circle', { cx, cy, r: 8 * u, fill: 'var(--casing)', stroke: `var(--p-${c})`, 'stroke-width': 1.6, 'vector-effect': 'non-scaling-stroke' }, ends);
        R.rings.push([cx, cy]);
        const q = mk('text', { x: cx, y: cy, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-size': 11 * u, 'font-weight': 700, fill: `var(--p-${c})`, style: 'font-family:var(--f-body)' }, ends); q.textContent = '?';
      };
      const tip = (l, first) => first ? [l.pts[0], l.pts[Math.min(4, l.pts.length - 1)]] : [l.pts[l.pts.length - 1], l.pts[Math.max(0, l.pts.length - 5)]];
      // which end the source stops at (research.partial and research/map-audit.md say it in words; a real build would carry it as a field)
      const OPEN = { 'Soliko 2': 'low', 'Firni 1': 'low', 'Firni 2': 'high', 'Shino': 'west', 'Sadzele 2': 'low', 'Sadzele 3': 'low' };
      L.filter(l => OPEN[l.key]).forEach(l => {
        const A = tip(l, true), B = tip(l, false);
        const eA = TM.elev(...A[0]), eB = TM.elev(...B[0]);
        const pick = { low: eA < eB ? A : B, high: eA > eB ? A : B, west: A[0][0] < B[0][0] ? A : B }[OPEN[l.key]];
        // a run in several pieces: only the lowest piece's low end counts
        if (L.filter(m => m.key === l.key).some(m => m !== l && Math.min(TM.elev(...m.pts[0]), TM.elev(...m.pts[m.pts.length - 1])) < Math.min(eA, eB)) && OPEN[l.key] === 'low') return;
        openEnd(pick[0], pick[1], color(l.key));
      });
      // real holes inside a run (map-audit: Goodaura 2, 174 and 90 m; Sadzele 1, 196 m): both sides of the gap end open, nothing drawn across it
      ['Goodaura 2', 'Sadzele 1'].forEach(k => {
        const P = L.filter(l => l.key === k), d = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
        P.forEach((l, i) => [true, false].forEach(first => {
          const [e, back] = tip(l, first);
          const gap = Math.min(...P.filter((m, j) => j !== i).flatMap(m => [m.pts[0], m.pts[m.pts.length - 1]]).map(q => d(q, e)));
          const nearOther = L.some(m => m.key !== k && m.pts.some(q => d(q, e) < 25));
          if (gap > 60 && gap < 400 && !nearOther) openEnd(e, back, color(k));
        }));
      });
    }

    const top = mk('g', { class: 'r21' }, svg);
    // D. downhill chevrons on the selected run (direction from the elevation model)
    if (o.arrows && o.sel) L.filter(l => l.key === o.sel).forEach(l => {
      let pts = l.pts; if (TM && TM.elev(...pts[0]) < TM.elev(...pts[pts.length - 1])) pts = [...pts].reverse();
      let acc = 0, next = 60 * u;
      for (let i = 1; i < pts.length; i++) { acc += Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
        if (acc >= next && i < pts.length - 3) { next += 75 * u; const a = Math.atan2(pts[i + 1][1] - pts[i - 1][1], pts[i + 1][0] - pts[i - 1][0]) * 180 / Math.PI, s = 6 * u;
          mk('path', { d: `M${-s} ${-s}L${s * 0.6} 0L${-s} ${s}`, transform: `translate(${pts[i][0]} ${pts[i][1]}) rotate(${a})`, fill: 'none', stroke: 'var(--ink)', 'stroke-width': 2.4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'vector-effect': 'non-scaling-stroke' }, top); } }
    });

    // A. labels along the line, at an anchor where no other run, lift or label passes
    if (o.along) {
      const old = [...svg.querySelectorAll('text.lbl.pg')];
      old.forEach(t => t.style.visibility = 'hidden');
      const placed = (R.rings || []).map(([cx, cy]) => ({ cx, cy, c: 1, s: 0, hw: 10 * u, hh: 10 * u, ang: 0 }));
      const fs = 13 * u, gap = 8 * u, pad = 3 * u;
      const measure = mk('text', { 'font-size': fs, class: 'lbl', style: 'visibility:hidden' }, layer);
      const keys = [...new Set(L.map(l => l.key))].filter(named).filter(k => !o.sel || k === o.sel);
      // longest runs first: they have the most room
      keys.sort((a, b) => Math.max(...L.filter(l => l.key === b).map(l => l.len)) - Math.max(...L.filter(l => l.key === a).map(l => l.len)));
      const inBox = (p, b) => { const dx = p[0] - b.cx, dy = p[1] - b.cy, x = dx * b.c + dy * b.s, y = -dx * b.s + dy * b.c; return Math.abs(x) < b.hw && Math.abs(y) < b.hh; };
      const boxPts = b => { const r = []; for (const i of [-1, -.5, 0, .5, 1]) for (const j of [-1, 0, 1]) r.push([b.cx + i * b.hw * b.c - j * b.hh * b.s, b.cy + i * b.hw * b.s + j * b.hh * b.c]); return r; };
      const { vb } = meta();
      // the label must sit inside the view, clear of the zoom buttons on the left and the status pill on top
      const inView = bx => boxPts(bx).every(([x, y]) => x > vb.x + 64 * u && x < vb.x + vb.width - 8 * u && y > vb.y + 52 * u && y < vb.y + vb.height - 8 * u);
      const runPts = k => { const r = []; L.forEach(l => { if (l.key !== k) r.push(...l.pts); }); return r; };
      // the four real crossings (research/map-audit.md): no name within 100 m of the point where the lines cross
      const d2 = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
      const X = [['Snow Park', 'Goodaura 1'], ['Soliko 1', 'Goodaura 1'], ['Soliko 1', 'Shino'], ['Sportuli 1', 'Shino']].map(([a, b]) => {
        let best = null; L.filter(l => l.key === a).forEach(la => L.filter(l => l.key === b).forEach(lb => la.pts.forEach(p => lb.pts.forEach(q => { const dd = d2(p, q); if (!best || dd < best.d) best = { d: dd, x: [(p[0] + q[0]) / 2, (p[1] + q[1]) / 2] }; }))));
        return best && best.d < 20 ? best.x : null; }).filter(Boolean);
      const nearCross = bx => X.some(x => d2(x, [bx.cx, bx.cy]) < 100 + bx.hw);
      // a shared stretch (two runs on one line): one name there, either the main run's or both together
      const done = new Set();
      (o.shared || []).forEach(([a, b, text, skip]) => {
        const la = L.filter(l => l.key === a).sort((x, y) => y.len - x.len)[0], lb = L.filter(l => l.key === b);
        if (!la || !lb.length) return;
        const on = la.pts.map(p => lb.some(m => m.pts.some(q => d2(p, q) < 15)));
        let i0 = -1, best = [0, 0]; on.forEach((v, i) => { if (v && i0 < 0) i0 = i; if ((!v || i === on.length - 1) && i0 >= 0) { const e = v ? i : i - 1; if (e - i0 > best[1] - best[0]) best = [i0, e]; i0 = -1; } });
        const i = Math.round((best[0] + best[1]) / 2); measure.textContent = text; const w = measure.getBBox().width;
        const span = Math.ceil((w / 2 + pad) / 12) + 1; if (best[1] - best[0] < 2 * span) return;
        const a0 = la.pts[i - span], a1 = la.pts[i + span]; let ang = Math.atan2(a1[1] - a0[1], a1[0] - a0[0]); if (ang > Math.PI / 2) ang -= Math.PI; if (ang < -Math.PI / 2) ang += Math.PI;
        const bx = { cx: la.pts[i][0], cy: la.pts[i][1], c: Math.cos(ang), s: Math.sin(ang), hw: w / 2 + pad, hh: fs * 0.55 + pad, ang };
        placed.push(bx); skip.forEach(k => done.add(k));
        const t = mk('text', { x: 0, y: 0, transform: `translate(${bx.cx} ${bx.cy}) rotate(${ang * 180 / Math.PI})`, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-size': fs, 'stroke-width': 5 * u, class: 'lbl', fill: `var(--p-${color(a)})` }, layer);
        t.textContent = text;
      });
      keys.filter(k => !done.has(k)).forEach(k => {
        measure.textContent = k; const w = measure.getBBox().width;
        if (!w) return;
        const RP = runPts(k); let best = null;
        const judge = (bx, extra) => {
          if (!inView(bx) || nearCross(bx)) return;
          const bad = RP.filter(p => inBox(p, bx)).length + placed.reduce((t, q) => t + boxPts(bx).filter(p => inBox(p, q)).length + boxPts(q).filter(p => inBox(p, bx)).length, 0);
          if (bad) return; // never on another run or label
          const score = extra + LP.filter(p => inBox(p, bx)).length * 15; // a lift cable under the name is tolerable, but costs
          if (!best || score < best.score) best = { score, b: bx };
        };
        L.filter(l => l.key === k).forEach(l => {
          const pts = l.pts, n = pts.length, span = Math.ceil((w / 2 + pad) / 12) + 1;
          for (let i = span; i < n - span; i++) {
            const a0 = pts[i - span], a1 = pts[i + span];
            let ang = Math.atan2(a1[1] - a0[1], a1[0] - a0[0]); if (ang > Math.PI / 2) ang -= Math.PI; if (ang < -Math.PI / 2) ang += Math.PI;
            let bend = 0; const dx = a1[0] - a0[0], dy = a1[1] - a0[1], dl = Math.hypot(dx, dy) || 1;
            for (let j = i - span; j <= i + span; j++) { const p = pts[j]; bend = Math.max(bend, Math.abs((p[0] - a0[0]) * dy - (p[1] - a0[1]) * dx) / dl); }
            if (bend > 9 * u) continue; // the stretch under the name must be nearly straight
            const c = Math.cos(ang), sn = Math.sin(ang), f = i / (n - 1);
            for (const side of (o.onLine ? [0, -1, 1] : [-1, 1])) {
              const off = (gap + fs * 0.35) * side;
              judge({ cx: pts[i][0] - sn * off, cy: pts[i][1] + c * off, c, s: sn, hw: w / 2 + pad, hh: fs * 0.55 + pad, ang }, bend / u * 2 + Math.abs(f - 0.5) * 20 + (side ? 40 : 0) - l.len / u * 0.01);
            }
          }
          // short runs (beginner areas): a level name beside the middle
          const m = pts[Math.floor(n / 2)];
          for (const [ddx, ddy] of [[0, -1], [0, 1], [1, 0], [-1, 0]]) judge({ cx: m[0] + ddx * (w / 2 + gap), cy: m[1] + ddy * (fs * 0.6 + gap), c: 1, s: 0, hw: w / 2 + pad, hh: fs * 0.55 + pad, ang: 0 }, 80);
        });
        if (!best) return; // no clean place in this view: the run stays unnamed rather than wear another run's name
        placed.push(best.b);
        const { b } = best, deg = b.ang * 180 / Math.PI;
        const t = mk('text', { x: 0, y: 0, transform: `translate(${b.cx} ${b.cy}) rotate(${deg})`, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-size': fs, 'stroke-width': (o.onLine ? 5 : 3.2) * u, class: 'lbl', fill: `var(--p-${color(k)})` }, layer);
        t.textContent = k;
      });
      measure.remove();
      // lift and place labels that now sit under a run label give way
      svg.querySelectorAll('text.lbl:not(.pg)').forEach(t => { if (t.closest('.r21') || t.style.display === 'none') return; const bb = t.getBBox(); const c = [bb.x + bb.width / 2, bb.y + bb.height / 2];
        if (placed.some(q => inBox(c, { ...q, hw: q.hw + bb.width / 2, hh: q.hh + bb.height / 2 }))) t.style.display = 'none'; });
      R.placed = placed.length - R.rings.length; R.total = keys.length;
    }
  };
  window.R21 = R;
})();

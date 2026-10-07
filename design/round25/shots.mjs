// Round 25: a readable map for large resorts, drawn over the real site. node design/round25/shots.mjs [prefix] (site on :4199)
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const HERE = new URL('./', import.meta.url).pathname, ROOT = HERE + '../../';
const OUT = HERE + 'shots/'; fs.mkdirSync(OUT, { recursive: true });
const PROP = HERE + 'proposal.js';
const NOW = '2026-10-07T11:00:00+02:00';
const D2 = { 'gud-view': '2d' }, D3 = { 'gud-view': '3d' };

// ---- the valleys, from real sources only (design/round25/data/, research/resorts/soelden-audit.md): a grouping, no line drawn ----
const RES = JSON.parse(fs.readFileSync(ROOT + 'site/data/resorts.json', 'utf8')).resorts;
const ORDER = ['green', 'blue', 'red', 'black'];
function valleys(id) {
  const r = RES.find(x => x.id === id), d = JSON.parse(fs.readFileSync(ROOT + 'site/' + r.dir + 'runs-and-lifts.json', 'utf8'));
  const { lat0, lon0 } = r.proj, kx = 111320 * Math.cos(lat0 * Math.PI / 180), ky = 111320, P = ([la, lo]) => [(lo - lon0) * kx, -(la - lat0) * ky];
  let runOf = {}, liftOf = {}, names;
  if (id === 'sellaronda') {
    const s = JSON.parse(fs.readFileSync(HERE + 'data/sectors-sellaronda.json', 'utf8'));
    runOf = s.runs; liftOf = s.lifts; names = Object.fromEntries(Object.entries(s.sectors).map(([k, v]) => [k, v.name]));
  } else {
    // Sölden: the official list's three areas (Gaislachkogl 1-10 and 50, Giggijoch 11-30, the glacier 31-41); the rest by the nearest run
    names = { a: 'Gaislachkogl', b: 'Giggijoch', c: 'Gletscher' };
    const by = n => n >= 31 && n <= 41 ? 'c' : n >= 11 && n <= 30 ? 'b' : (n >= 1 && n <= 10) || n === 50 ? 'a' : null;
    d.pistes.forEach(p => { const m = /^(\d+)/.exec(p.key); const v = m && by(+m[1]); if (v) runOf[p.key] = v; });
    const pts = d.pistes.filter(p => runOf[p.key]).flatMap(p => p.segs.flatMap(s => s.g.map(q => [P(q), runOf[p.key]])));
    const near = q => { const a = P(q); let b = null, bd = 1e18; for (const [x, v] of pts) { const dd = (x[0] - a[0]) ** 2 + (x[1] - a[1]) ** 2; if (dd < bd) { bd = dd; b = v; } } return b; };
    d.pistes.forEach(p => { if (!runOf[p.key]) { const g = p.segs[0].g; runOf[p.key] = near(g[Math.floor(g.length / 2)]); } });
    d.lifts.forEach(l => { liftOf[l.id] = near(l.g[Math.floor(l.g.length / 2)]); });
  }
  const sectors = {}, len = {}, liftLbl = {};
  d.pistes.forEach(p => len[p.key] = p.len);
  d.lifts.forEach(l => { if (l.name && liftOf[l.id]) liftLbl[l.name] = liftOf[l.id]; });
  for (const [k, name] of Object.entries(names)) {
    const ps = d.pistes.filter(p => runOf[p.key] === k), named = ps.filter(p => p.named && p.kind !== 'ski-route');
    const lines = ps.flatMap(p => p.segs.filter(s => !s.area).map(s => s.g.map(P)));
    // the frame and the plate from the valley's own runs: not the long links (ski ways, ski routes) that reach into the next one
    const all = ps.filter(p => !p.kind || p.kind === 'run').flatMap(p => p.segs.map(s => s.g.map(P))).flat(), xs = all.map(q => q[0]).sort((a, b) => a - b), ys = all.map(q => q[1]).sort((a, b) => a - b), pc = (a, f) => a[Math.floor((a.length - 1) * f)];
    const colors = {}; named.forEach(p => { if (ORDER.includes(p.color)) colors[p.color] = (colors[p.color] || 0) + 1; });
    sectors[k] = { name, lines, zw: id === 'sellaronda' ? 1500 : 700, bbox: [pc(xs, .01), pc(ys, .01), pc(xs, .99), pc(ys, .99)], label: [pc(xs, .5), pc(ys, .5)],
      colors, runs: named.length, km: Math.round(named.reduce((t, p) => t + p.len, 0) / 1000),
      items: named.sort((a, b) => ORDER.indexOf(a.color) - ORDER.indexOf(b.color) || a.key.localeCompare(b.key, undefined, { numeric: true })).map(p => [p.key, p.key, p.color, p.len]) };
  }
  const areas = d.pistes.flatMap(p => p.segs.filter(s => s.area).map(s => [...P(s.g[0]), p.key]));
  const total = Object.values(sectors).reduce((t, v) => t + v.runs, 0);
  return { sectors, runOf, liftOf, len, liftLbl, total, areas, P };
}
const V = { sellaronda: valleys('sellaronda'), soelden: valleys('soelden') };
// 3D: low on the valley's side, looking in towards the massif in the middle (Sella group 46.51 N 11.80 E; Sölden: the middle of its areas)
function camAll(id, pol = .95, az = 0) { const L = Object.values(V[id].sectors).map(v => v.bbox), a = Math.min(...L.map(q => q[0])), b = Math.min(...L.map(q => q[1])), c = Math.max(...L.map(q => q[2])), d = Math.max(...L.map(q => q[3]));
  return { tx: (a + c) / 2, tz: (b + d) / 2 + 600, dist: Math.max(c - a, d - b) * (az ? 1.15 : 1.25), az, pol }; }
function cam(id, k, f = 1.15, pol = .4) {
  const D = V[id], s = D.sectors[k], b = s.bbox, c = [(b[0] + b[2]) / 2, (b[1] + b[3]) / 2];
  const m = id === 'sellaronda' ? D.P([46.512, 11.80]) : (() => { const L = Object.values(D.sectors).map(v => v.bbox); return [L.reduce((t, q) => t + (q[0] + q[2]) / 2, 0) / L.length, L.reduce((t, q) => t + (q[1] + q[3]) / 2, 0) / L.length]; })();
  let dx = c[0] - m[0], dz = c[1] - m[1]; const n = Math.hypot(dx, dz) || 1; dx /= n; dz /= n;
  return { tx: c[0] - dx * 300, tz: c[1] - dz * 300, dist: Math.max(b[2] - b[0], b[3] - b[1]) * f, az: Math.atan2(dx, dz), pol };
}
const strip = D => { const { P, ...o } = D; return o; };
const SR = '/?resort=sellaronda#map', SO = '/?resort=soelden#map', GU = '/#map';
const SRD = strip(V.sellaronda), SOD = strip(V.soelden);
const ALLB = D => { const L = Object.values(D.sectors).map(v => v.bbox); return [Math.min(...L.map(q => q[0])), Math.min(...L.map(q => q[1])), Math.max(...L.map(q => q[2])), Math.max(...L.map(q => q[3]))]; };
const ALL = (D, x = {}) => ({ D, mode: 'all', chips: 1, list: 'groups', frame: ALLB(D), pad: 1.05, ...x });
const VAL = (D, k, x = {}) => ({ D, mode: 'valley', k, chips: 1, list: 'valley', frame: D.sectors[k].bbox, ...x });
const DESK = { w: 1280, h: 800 };
const VCOL = { gardena: '#C49A3A', badia: '#2F978B', arabba: '#8C6CC0', fassa: '#7E9136', a: '#C49A3A', b: '#2F978B', c: '#8C6CC0' };
const QC = { blue: '#1F5FC4', red: '#D1342B', black: '#13233A', green: '#1B8A4C' };
const V3ALL = D => ({ mode: 'all', runOf: D.runOf, vcol: Object.fromEntries(Object.keys(D.sectors).map(k => [k, VCOL[k]])), plates: Object.entries(D.sectors).map(([k, v]) => ({ name: v.name, x: v.label[0], y: v.label[1], col: VCOL[k], pts: v.lines.flat().filter((_, i) => i % 7 === 0),
  counts: ['blue', 'red', 'black', 'green'].filter(c => v.colors[c]).map(c => `<em><i style="background:${QC[c]}"></i>${v.colors[c]}</em>`).join('') })) });
const V3VAL = (D, k) => ({ mode: 'valley', k, runOf: D.runOf, liftOf: D.liftOf, mute: '#AEB8C4' });

// D2, the phone: go round the mountain. For the valley in front: its neighbours to the left and right (by the camera's own
// right vector, the site's view maths: camera at target + dist*(sin az, cos az)), the one behind, and the ring.
function orbitQ(id, k, behind = 'מאחורי ההר', hint = 'החלקה: לעמק הבא') {
  const D = V[id], c = cam(id, k), ca = Math.cos(c.az), sa = Math.sin(c.az);
  const m = id === 'sellaronda' ? D.P([46.512, 11.80]) : (() => { const L = Object.values(D.sectors).map(v => v.bbox); return [L.reduce((t, q) => t + (q[0] + q[2]) / 2, 0) / L.length, L.reduce((t, q) => t + (q[1] + q[3]) / 2, 0) / L.length]; })();
  const ctr = v => [(v.bbox[0] + v.bbox[2]) / 2, (v.bbox[1] + v.bbox[3]) / 2];
  const it = Object.entries(D.sectors).map(([kk, v]) => { const [x, z] = ctr(v), a = Math.atan2(x - m[0], z - m[1]), r = a - c.az;
    return { k: kk, name: v.name, col: VCOL[kk], sx: Math.sin(r), near: Math.cos(r), r: Math.atan2(Math.sin(r), Math.cos(r)), cur: kk === k,
      counts: ['blue', 'red', 'black', 'green'].filter(q => v.colors[q]).map(q => `<em><i style="background:var(--p-${q})"></i>${v.colors[q]}</em>`).join('') }; });
  const others = it.filter(v => !v.cur), back = others.length > 2 ? others.reduce((b, v) => v.near < b.near ? v : b) : null, side = others.filter(v => v !== back);
  const L = side.filter(v => v.sx < 0).sort((a, b) => b.near - a.near)[0] || null, R = side.filter(v => v.sx >= 0).sort((a, b) => b.near - a.near)[0] || null;
  return { cur: it.find(v => v.cur), L, R, B: back, behind, hint, ring: it.map(v => ({ col: v.col, r: v.r, cur: v.cur })) };
}
const V3ORB = D => ({ ...V3ALL(D), plates: [] });
export const S = [
  // [name, url, proposal, opts]
  ['today-sr', SR, null, { st: D2 }],
  ['today-sr-night', SR, null, { st: D2, theme: 'night' }],
  ['today-sr-list', SR, null, { st: D2, scrollTo: '#panel' }],
  ['today-sr-3d', SR, null, { st: D3, wait: 7000 }],
  ['today-sr-desk', SR, null, { st: D2, ...DESK }],
  ['today-so', SO, null, { st: D2 }],
  ['today-gu', GU, null, { st: D2 }],
  // A: the whole resort as valleys
  ['a-sr', SR, ALL(SRD), { st: D2 }],
  ['a-sr-night', SR, ALL(SRD), { st: D2, theme: 'night' }],
  ['a-sr-list', SR, ALL(SRD), { st: D2, scrollTo: '#panel' }],
  ['a-sr-desk', SR, ALL(SRD), { st: D2, ...DESK }],
  ['a-sr-desk-night', SR, ALL(SRD), { st: D2, ...DESK, theme: 'night' }],
  ['a-so', SO, ALL(SOD, { small: 1 }), { st: D2 }],
  // B: one valley, framed; the rest dimmed; its labels by importance; the list of that valley
  ['b-sr-badia', SR, VAL(SRD, 'badia'), { st: D2 }],
  ['b-sr-badia-night', SR, VAL(SRD, 'badia'), { st: D2, theme: 'night' }],
  ['b-sr-gardena', SR, VAL(SRD, 'gardena'), { st: D2 }],
  ['b-sr-arabba', SR, VAL(SRD, 'arabba'), { st: D2 }],
  ['b-sr-badia-list', SR, VAL(SRD, 'badia'), { st: D2, scrollTo: '#panel' }],
  ['b-sr-badia-desk', SR, VAL(SRD, 'badia', { hov: 'Boè' }), { st: D2, ...DESK }],
  ['b-sr-gardena-desk-night', SR, VAL(SRD, 'gardena'), { st: D2, ...DESK, theme: 'night' }],
  ['b-so-c', SO, VAL(SOD, 'c'), { st: D2 }],
  ['b-so-b', SO, VAL(SOD, 'b'), { st: D2 }],
  ['b-so-b-night', SO, VAL(SOD, 'b'), { st: D2, theme: 'night' }],
  // C: the list first (Pini's idea): a short map strip, the valleys as rows
  ['c-sr', SR, ALL(SRD, { mini: 1, small: 1, pad: 1 }), { st: D2 }],
  // D: 3D, low on one valley's side
  ['d-sr-badia', SR, { D: SRD, chips3d: 'badia' }, { st: D3, wait: 7000, cam: cam('sellaronda', 'badia'), v3: V3VAL(SRD, 'badia') }],
  ['d-sr-gardena', SR, { D: SRD, chips3d: 'gardena' }, { st: D3, wait: 7000, cam: cam('sellaronda', 'gardena'), v3: V3VAL(SRD, 'gardena') }],
  ['d-sr-badia-night', SR, { D: SRD, chips3d: 'badia' }, { st: D3, wait: 7000, cam: cam('sellaronda', 'badia'), v3: V3VAL(SRD, 'badia'), theme: 'night' }],
  ['d-sr-badia-desk', SR, { D: SRD, chips3d: 'badia' }, { st: D3, wait: 7000, cam: cam('sellaronda', 'badia', 1), v3: V3VAL(SRD, 'badia'), ...DESK }],
  ['d-so-c', SO, { D: SOD, chips3d: 'c' }, { st: D3, wait: 7000, cam: cam('soelden', 'c', 1.3), v3: V3VAL(SOD, 'c') }],
  ['d-sr-all', SR, { D: SRD, chips3d: 'all' }, { st: D3, wait: 7000, v3: V3ALL(SRD), cam: camAll('sellaronda', .95, Math.PI / 2) }],
  ['d-sr-all-desk', SR, { D: SRD, chips3d: 'all' }, { st: D3, wait: 7000, v3: V3ALL(SRD), cam: camAll('sellaronda'), ...DESK }],
  ['d-so-all', SO, { D: SOD, chips3d: 'all' }, { st: D3, wait: 7000, v3: V3ALL(SOD), cam: camAll('soelden', .95, Math.PI / 2) }],
  // D2: the phone, round the mountain (whole-resort colours, the camera in one valley, the names on the screen's edges)
  ['e-sr-badia', SR, { D: SRD, orbit: orbitQ('sellaronda', 'badia') }, { st: D3, wait: 7000, v3: V3ORB(SRD), cam: cam('sellaronda', 'badia', 1.05, .5) }],
  ['e-sr-gardena', SR, { D: SRD, orbit: orbitQ('sellaronda', 'gardena', undefined, null) }, { st: D3, wait: 7000, v3: V3ORB(SRD), cam: cam('sellaronda', 'gardena', 1.05, .5) }],
  ['e-sr-badia-night', SR, { D: SRD, orbit: orbitQ('sellaronda', 'badia', undefined, null) }, { st: D3, wait: 7000, v3: V3ORB(SRD), cam: cam('sellaronda', 'badia', 1.05, .5), theme: 'night' }],
  ['e-so-b', SO, { D: SOD, orbit: orbitQ('soelden', 'b', 'מאחורי ההר', null) }, { st: D3, wait: 7000, v3: V3ORB(SOD), cam: cam('soelden', 'b', 1.3, .5) }],
];
const only = process.argv[2];
const b = await launch();
for (const [n, u, p, o] of S) {
  if (only && !n.startsWith(only)) continue;
  const { ctx, page } = await open(b, u, { extraStorage: o.st, wait: o.wait || 3500, now: NOW, trip: null, w: o.w, h: o.h, theme: o.theme || 'day',
    pre: o.v3 || o.cam ? c => c.addInitScript(cfg => { let R; Object.defineProperty(window, 'GudRelief', { configurable: true, get: () => R, set(v) { R = v; const V3 = v.View3D;
      // round 25, D: the 3D view knows the valleys. Whole resort: a plate per valley (laid as a place label, then restyled), no run
      // or lift names. One valley: its runs and lifts as today; the other valleys' runs thin grey and nameless, their lifts not drawn.
      v.View3D = function (o) {
        let added = [];
        if (cfg && cfg.mode === 'all') { // in 3D a plate at the valley floor hides behind the next ridge: lay it on a high run point near the valley's middle instead
          const sc = (q, t) => o.model.elev(t[0], t[1]) - .12 * Math.hypot(t[0] - q.x, t[1] - q.y), hi = q => (q.pts || [[q.x, q.y]]).reduce((b, t) => sc(q, t) > sc(q, b) ? t : b);
          // laid as peaks (the first labels to place), at the front, so no peak or village pushes a plate off
          added = cfg.plates.map(q => { const [x, y] = hi(q); return { n: '\u2063' + q.name, x, y, ele: '', _r25: 1 }; }); o.model.peaks.unshift(...added);
          // the whole resort from higher up, each valley's runs in the valley's colour; the difficulty comes back when one valley is picked
          o = { ...o, liftColor: '#8A96A6', colors: { ...o.colors, ...Object.fromEntries(Object.entries(cfg.vcol).map(([k, c]) => ['v_' + k, c])) },
            pistes: o.pistes.map(p => cfg.runOf[p.key] ? { ...p, color: 'v_' + cfg.runOf[p.key] } : p) }; }
        if (cfg && cfg.mode === 'valley') o = { ...o, colors: { ...o.colors, mute: cfg.mute },
          pistes: o.pistes.map(p => cfg.runOf[p.key] === cfg.k ? p : { ...p, color: 'mute' }),
          lifts: o.lifts.filter(l => cfg.liftOf[l.id] === cfg.k) };
        const api = V3.call(this, o); window.__v3 = api;
        if (added.length) { o.model.peaks.splice(0, added.length);
          document.querySelectorAll('.r3-labels .r3-lbl.peak').forEach(e => { if (e.textContent[0] !== '\u2063') return; const q = cfg.plates.find(t => t.name === e.textContent.slice(1).trim()); if (!q) return;
            e.classList.add('r25-v3'); e.classList.remove('peak'); e.style.setProperty('--vc', q.col); e.innerHTML = `<b>${q.name}</b><span>${q.counts}</span>`; });
          document.querySelectorAll('.r3-labels .r3-lbl.piste, .r3-labels .r3-lbl.lift, .r3-labels .r3-lbl.place, .r3-labels .r3-lbl.peak').forEach(e => e.remove()); }
        if (cfg && cfg.mode === 'valley') document.querySelectorAll('.r3-labels .r3-lbl.piste').forEach(e => { if (cfg.runOf[e.dataset.key] !== cfg.k) e.remove(); });
        const st = document.createElement('style'); st.textContent = `.r3-lbl.r25-v3{display:flex;flex-direction:column;align-items:center;gap:3px;padding:5px 9px;background:#fff;border:1.5px solid #CBD5DF;border-top:4px solid var(--vc);box-shadow:0 2px 8px rgba(19,35,58,.22);text-shadow:none;letter-spacing:0;color:#13233A}
          .r3-lbl.r25-v3 b{font:700 19px/1 Karantina,'Arial Narrow',sans-serif}.r3-lbl.r25-v3 span{display:flex;gap:6px;font:600 11.5px/1 'IBM Plex Sans',sans-serif;color:#13233A}
          .r3-lbl.r25-v3 span em{font-style:normal;display:flex;align-items:center;gap:3px}.r3-lbl.r25-v3 span em i{width:8px;height:8px;display:inline-block}`; document.head.appendChild(st);
        return api; }; } }); }, o.v3 || null) : null });
  if (p && p.orbit) { await page.evaluate(fs.readFileSync(PROP, 'utf8')); }
  if (p && p.chips3d) { await page.evaluate(fs.readFileSync(PROP, 'utf8')); await page.evaluate(q => { const all = q.chips3d === 'all'; window.R25.chips(q.D, all ? null : q.chips3d); window.R25.apply({ D: q.D, k: all ? null : q.chips3d, list: all ? 'groups' : 'valley' }); }, p); }
  if (o.cam) { console.log(n, await page.evaluate(c => window.R25.view3d(c), o.cam)); await page.waitForTimeout(2500); }
  if (p && p.orbit) console.log(n, await page.evaluate(q => window.R25.orbit(q), p.orbit));
  if (p && !p.chips3d && !p.orbit) { await page.evaluate(fs.readFileSync(PROP, 'utf8'));
    // the chip row first: the map gets shorter, and the site lays its labels out again; then the rest
    if (p.chips) { await page.evaluate(q => window.R25.chips(q.D, q.mode === 'valley' ? q.k : null), p); if (p.mini) await page.evaluate(() => document.body.classList.add('r25-cmini')); await page.waitForTimeout(700); }
    console.log(n, JSON.stringify(await page.evaluate(q => window.R25.apply({ ...q, chips: 0 }), p))); await page.waitForTimeout(o.after || 600); }
  if (o.scrollTo) await page.evaluate(q => { const e = document.querySelector(q); e && e.scrollIntoView({ block: 'start' }); }, o.scrollTo);
  if (process.env.DBG) console.log(n, JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('.r25-v3')].map(e => [e.textContent, e.style.display, e.style.transform]))));
  await page.screenshot({ path: OUT + n + '.png', fullPage: !!o.full });
  if (page._errors && page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();

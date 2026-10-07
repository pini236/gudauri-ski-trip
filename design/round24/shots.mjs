// Round 24: ski routes and wide runs, drawn over the real site. node design/round24/shots.mjs (site on :4199)
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const HERE = new URL('./', import.meta.url).pathname, ROOT = HERE + '../../';
const OUT = HERE + 'shots/'; fs.mkdirSync(OUT, { recursive: true });
const PROP = HERE + 'proposal.js';
const SR = JSON.parse(fs.readFileSync(HERE + 'data/skiroutes.json', 'utf8')).routes;
const KEYS = SR.map(r => r.key);
const hav = (a, b) => { const k = 111320, x = (b[1] - a[1]) * k * Math.cos(a[0] * Math.PI / 180), y = (b[0] - a[0]) * k; return Math.hypot(x, y); };
// the routes as runs of the site's schema, kind 'ski-route' (colour from OSM only so the site's panel can render; restyled)
const asPistes = () => SR.map(r => ({ key: r.key, name: r.key, osmNames: [r.key], color: 'red', named: true, kind: 'ski-route',
  len: Math.round(r.segs.reduce((s, x) => s + x.g.slice(1).reduce((t, q, i) => t + hav(x.g[i], q), 0), 0)),
  osmDiff: [], refs: /^\d/.test(r.key) ? [r.key] : [], groom: ['backcountry'], lit: [], fromLifts: [], toLifts: [], joins: [], fromPistes: [],
  segs: r.segs.map(s => ({ id: s.id, area: false, g: s.g })),
  research: { conf: 'high', status: 'osm-named', notes: '', sources: ['OpenStreetMap, ODbL'], osmIds: r.segs.map(s => s.id), checks: [] } }));
const NOW = '2026-10-07T11:00:00+02:00';
const D2 = { 'gud-view': '2d' }, D3 = { 'gud-view': '3d' };
const S = [
  // [name, url, proposal, opts]
  ['today-map', '/?resort=soelden#map', null, { st: D2 }],
  ['today-run4', '/?resort=soelden#map/run/4', null, { st: D2 }],
  ['today-run4-night', '/?resort=soelden#map/run/4', null, { st: D2, theme: 'night' }],
  ['today-gud', '/#map/run/Pirveli', null, { st: D2 }],
  ['prop-map', '/?resort=soelden#map', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1 }],
  ['prop-run4', '/?resort=soelden#map/run/4', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1 }],
  ['prop-run4-night', '/?resort=soelden#map/run/4', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1, theme: 'night' }],
  ['hatch-run4', '/?resort=soelden#map/run/4', { routes: 1, areas: 'hatch' }, { st: D2, inj: 1 }],
  ['prop-route61', '/?resort=soelden#map/run/61', { routes: 1, areas: 'solid', panel: '61' }, { st: D2, inj: 1, scroll: 1 }],
  ['prop-route61-night', '/?resort=soelden#map/run/61', { routes: 1, areas: 'solid', panel: '61' }, { st: D2, inj: 1, theme: 'night', scroll: 1 }],
  ['prop-3d', '/?resort=soelden#map/run/4', { routes: 1, areas: 'solid' }, { st: D3, inj: 1, wait: 6000 }],
  ['prop-desk', '/?resort=soelden#map/run/4', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1, w: 1280, h: 800 }],
  ['prop-gud', '/#map/run/Pirveli', { areas: 'solid' }, { st: D2 }],
  ['prop-gud-night', '/#map/run/Pirveli', { areas: 'solid' }, { st: D2, theme: 'night' }],
];
S.push(
  ['today-zoom', '/?resort=soelden#map/run/4', null, { st: D2, back: 1 }],
  ['prop-zoom', '/?resort=soelden#map/run/4', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1, back: 1 }],
  ['hatch-zoom', '/?resort=soelden#map/run/4', { routes: 1, areas: 'hatch', legend: 1 }, { st: D2, inj: 1, back: 1 }],
  ['today-zoom-night', '/?resort=soelden#map/run/4', null, { st: D2, back: 1, theme: 'night' }],
  ['prop-zoom-night', '/?resort=soelden#map/run/4', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1, back: 1, theme: 'night' }],
  ['today-zoom61', '/?resort=soelden#map/run/61', null, { st: D2, back: 1, inj: 1 }],
  ['prop-zoom61', '/?resort=soelden#map/run/61', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1, back: 1 }],
  ['prop-3d61', '/?resort=soelden#map/run/61', { routes: 1 }, { st: D3, inj: 1, back: 1, wait: 6000 }],
  ['prop-desk-zoom', '/?resort=soelden#map/run/4', { routes: 1, areas: 'solid', legend: 1 }, { st: D2, inj: 1, back: 1, w: 1280, h: 800 }],
  ['today-gud-zoom', '/#map/run/Pirveli', null, { st: D2, back: 1 }],
  ['prop-gud-zoom', '/#map/run/Pirveli', { areas: 'solid' }, { st: D2, back: 1 }],
);
const only = process.argv[2];
const b = await launch();
for (const [n, u, p, o] of S) {
  if (only && !n.startsWith(only)) continue;
  const { ctx, page } = await open(b, u, { theme: 'day', extraStorage: o.st, wait: o.wait || 3500, now: NOW, trip: null, w: o.w, h: o.h, theme: o.theme || 'day',
    pre: o.inj ? async (c) => {
      await c.route(/\/data\/resorts\/soelden\/runs-and-lifts\.json/, async r => { const d = JSON.parse(fs.readFileSync(ROOT + 'site/data/resorts/soelden/runs-and-lifts.json', 'utf8')); d.pistes.push(...asPistes()); await r.fulfill({ contentType: 'application/json', body: JSON.stringify(d) }); });
      // 3D: the routes in orange, in dashes of 45 m (the site would dash them in the line shader)
      await c.addInitScript(keys => { let R; Object.defineProperty(window, 'GudRelief', { configurable: true, get: () => R, set(v) { R = v; const V = v.View3D;
        v.View3D = function (o) { const dash = g => { const out = []; let cur = [g[0]], acc = 0, on = true; const m = (a, b) => Math.hypot((b[1] - a[1]) * 76000, (b[0] - a[0]) * 111320);
          for (let i = 1; i < g.length; i++) { const L = m(g[i - 1], g[i]); let t0 = 0; while (L - t0 > 0) { const need = (on ? 45 : 28) - acc, step = Math.min(need, L - t0); t0 += step; acc += step;
            const f = t0 / L, q = [g[i - 1][0] + (g[i][0] - g[i - 1][0]) * f, g[i - 1][1] + (g[i][1] - g[i - 1][1]) * f]; if (on) cur.push(q);
            if (acc >= (on ? 45 : 28) - 1e-6) { if (on && cur.length > 1) out.push(cur); on = !on; acc = 0; cur = [q]; } } } if (on && cur.length > 1) out.push(cur); return out; };
          o = { ...o, colors: { ...o.colors, orange: '#D96A00' }, pistes: o.pistes.map(p => keys.includes(p.key) ? { ...p, color: 'orange', kind: 'ski-way', segs: p.segs.flatMap(s => dash(s.g).map(g => ({ ...s, g }))) } : p) };
          return V.call(this, o); }; } }); }, KEYS);
    } : null });
  if (o.back) { await page.click('[data-back]').catch(e => console.log(n, 'no back')); await page.waitForTimeout(1200); await page.evaluate(() => window.scrollTo(0, 0)); }
  if (p) { await page.addScriptTag({ path: PROP }); const c = await page.evaluate(([q, KEYS]) => window.R24.apply({ ...q, routes: q.routes ? KEYS : null }), [p, KEYS]); console.log(n, JSON.stringify(c)); await page.waitForTimeout(500); }
  if (o.scroll) await page.evaluate(() => { const pn = document.getElementById('panel'); pn && pn.querySelector('dl.kv') && pn.querySelector('dl.kv').scrollIntoView({ block: 'center' }); });
  await page.screenshot({ path: OUT + n + '.png' });
  if (page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();

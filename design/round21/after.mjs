// Round 21: before and after on the real overview map. node design/round21/after.mjs (site served on :4199)
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const OUT = new URL('./shots/', import.meta.url).pathname; fs.mkdirSync(OUT, { recursive: true });
const PROP = new URL('./proposal.js', import.meta.url).pathname;
const D2 = { 'gud-view': '2d' };
const ALL = { along: true, onLine: true, ends: true, unnamed: true };
// [name, url, open options, zoom-in clicks, proposal options]
const S = [
  ['desk-zoom', '/#map', { w: 1280, h: 800 }, 2, ALL],
  ['phone-fit', '/#map', {}, 0, ALL],
  ['phone-zoom', '/#map', {}, 2, ALL],
  ['phone-night', '/#map', { theme: 'night' }, 2, ALL],
  ['phone-ends', '/#map', {}, 3, ALL, 'Soliko 2'],
  ['phone-tatra-combined', '/#map', {}, 3, { ...ALL, shared: [['Tatra 1', 'Tatra 2', 'Tatra 1 · 2', []]] }, ['Tatra 1', 0.8]],
  ['phone-tatra-primary', '/#map', {}, 3, { ...ALL, shared: [['Tatra 1', 'Tatra 2', 'Tatra 1', ['Tatra 1']]] }, ['Tatra 1', 0.8]],
  ['phone-sel', '/#map/run/Tatra%202', {}, 0, { along: true, onLine: true, unnamed: true, arrows: true, sel: 'Tatra 2' }],
];
const b = await launch();
const facts = {};
for (const [n, u, o, z, prop, center] of S) {
  for (const side of ['before', 'after']) {
    const { ctx, page } = await open(b, u, { theme: 'day', ...o, extraStorage: D2, wait: 2500 });
    for (let i = 0; i < z; i++) { await page.click('#zin'); await page.waitForTimeout(300); }
    if (center) await page.evaluate(([k, fr]) => { // pan so the partial run's open end is in view
      const p = document.querySelector(`path.hit[data-key="${k}"]`); const L = p.getTotalLength(); const q = p.getPointAtLength(L * (fr ?? 0.15));
      const svg = document.getElementById('map'); const vb = svg.viewBox.baseVal; const r = svg.getBoundingClientRect();
      const sx = r.left + (q.x - vb.x) / vb.width * r.width, sy = r.top + (q.y - vb.y) / vb.height * r.height;
      return [sx, sy]; }, Array.isArray(center) ? center : [center]).then(async ([sx, sy]) => { const r = await page.locator('#map').boundingBox();
        // drag in short strokes from the middle of the map, so every stroke starts on the map
        const cx = r.x + r.width / 2, cy = r.y + r.height / 2; let dx = sx - cx, dy = sy - cy;
        while (Math.hypot(dx, dy) > 2) { const k = Math.min(1, 120 / Math.hypot(dx, dy)), mx = dx * k, my = dy * k;
          await page.mouse.move(cx, cy); await page.mouse.down(); await page.mouse.move(cx - mx, cy - my, { steps: 6 }); await page.mouse.up(); dx -= mx; dy -= my; }
        await page.evaluate(() => getSelection().removeAllRanges()); await page.waitForTimeout(300); });
    if (side === 'after') { await page.addScriptTag({ path: PROP }); await page.evaluate(p => window.R21.apply(p), prop); }
    await page.locator('.mapwrap, #map').first().screenshot({ path: `${OUT}${n}-${side}.png` }).catch(async () => page.screenshot({ path: `${OUT}${n}-${side}.png` }));
    facts[`${n}-${side}`] = await page.evaluate(() => {
      const svg = document.getElementById('map');
      const lbls = [...svg.querySelectorAll('text.lbl')].filter(t => (t.closest('.r21') || t.classList.contains('pg')) && t.style.display !== 'none' && t.style.visibility !== 'hidden' && t.getBBox().width);
      let on = 0; const which = [];
      lbls.forEach(t => { const r = t.getBoundingClientRect(); const name = t.textContent; const others = new Set();
        for (const fx of [.15, .5, .85]) for (const fy of [.3, .7]) document.elementsFromPoint(r.left + r.width * fx, r.top + r.height * fy).forEach(e => { if (e.matches && e.matches('path.hit') && e.dataset.key && e.dataset.key !== name) others.add(e.dataset.key); });
        if (others.size) { on++; which.push(name + ' ← ' + [...others].join(', ')); } });
      return { shown: lbls.length, onOther: on, which };
    });
    if (page._errors.length) console.log(n, side, page._errors);
    await ctx.close();
  }
}
fs.writeFileSync(OUT + 'facts.json', JSON.stringify(facts, null, 1));
for (const k in facts) console.log(k, facts[k].shown, 'shown,', facts[k].onOther, 'on another run');
await b.close();

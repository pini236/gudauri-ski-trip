// Round 21, version 2 of the open end (Pini, 1.11.2026: natural and believable, no question mark). node design/round21/after3.mjs (site on :4199)
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const OUT = new URL('./shots/', import.meta.url).pathname;
const PROP = new URL('./proposal.js', import.meta.url).pathname;
const D2 = { 'gud-view': '2d' };
const BASE = { along: true, style: 'curve', fs: 10, gap: 3, ends: true, unnamed: true };
const FOCUS = 'Soliko 2';
const S = [];
for (const v of ['fade', 'dots', 'cap']) S.push([`e2-${v}`, { w: 390 }, v]);
S.push(['e2-fade-night', { theme: 'night' }, 'fade'], ['e2-fade-desk', { w: 1280, h: 800 }, 'fade']);
const b = await launch();
for (const [n, o, v] of S) {
  const { ctx, page } = await open(b, '/#map', { theme: 'day', ...o, extraStorage: D2, wait: 2500 });
  for (let i = 0; i < 3; i++) { await page.click('#zin'); await page.waitForTimeout(300); }
  await page.evaluate(([k, fr]) => { window.__c = (() => { const p = document.querySelector(`path.hit[data-key="${k}"]`); const L = p.getTotalLength(); const q = p.getPointAtLength(L * fr); const svg = document.getElementById('map'); const vb = svg.viewBox.baseVal; const r = svg.getBoundingClientRect(); return [r.left + (q.x - vb.x) / vb.width * r.width, r.top + (q.y - vb.y) / vb.height * r.height]; })(); }, [FOCUS, 0.15]);
  { const [sx, sy] = await page.evaluate(() => window.__c); const r = await page.locator('#map').boundingBox(); const cx = r.x + r.width / 2, cy = r.y + r.height / 2; let dx = sx - cx, dy = sy - cy;
    while (Math.hypot(dx, dy) > 2) { const k = Math.min(1, 120 / Math.hypot(dx, dy)), mx = dx * k, my = dy * k; await page.mouse.move(cx, cy); await page.mouse.down(); await page.mouse.move(cx - mx, cy - my, { steps: 6 }); await page.mouse.up(); dx -= mx; dy -= my; }
    await page.evaluate(() => getSelection().removeAllRanges()); await page.waitForTimeout(300); }
  await page.evaluate(fs.readFileSync(PROP, 'utf8')); await page.evaluate(p => window.R21.apply(p), { ...BASE, endStyle: v });
  await page.locator('.mapwrap, #map').first().screenshot({ path: `${OUT}${n}.png` });
  if (page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();

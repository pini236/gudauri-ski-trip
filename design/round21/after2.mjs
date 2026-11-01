// Round 21, version 2 (Pini, 31.10.2026: "the run names add clutter"): smaller names, close to the line, in three ways,
// drawn over the real overview map by proposal.js (canvas only). node design/round21/after2.mjs (site served on :4199)
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const OUT = new URL('./shots/', import.meta.url).pathname;
const PROP = new URL('./proposal.js', import.meta.url).pathname;
const D2 = { 'gud-view': '2d' };
const BASE = { ends: true, unnamed: true };
const V = {
  line: { ...BASE, along: true, onLine: true, style: 'line', fs: 10 },          // A: small, on the line
  curve: { ...BASE, along: true, style: 'curve', fs: 10, gap: 3 },              // B: small, beside the line, bending with it
  tag: { ...BASE, along: true, style: 'tag', fs: 9 },                           // C: a small tag on the line
};
// [name, url, open options, zoom-in clicks, variant]
const S = [];
for (const v of Object.keys(V)) S.push([`v2-${v}-fit`, {}, 0, v], [`v2-${v}-zoom`, {}, 2, v]);
S.push(['v2-curve-desk', { w: 1280, h: 800 }, 2, 'curve'], ['v2-curve-night', { theme: 'night' }, 2, 'curve']);
const b = await launch();
for (const [n, o, z, v] of S) {
  const { ctx, page } = await open(b, '/#map', { theme: 'day', ...o, extraStorage: D2, wait: 2500 });
  for (let i = 0; i < z; i++) { await page.click('#zin'); await page.waitForTimeout(300); }
  await page.evaluate(fs.readFileSync(PROP, 'utf8')); await page.evaluate(p => window.R21.apply(p), V[v]);
  await page.locator('.mapwrap, #map').first().screenshot({ path: `${OUT}${n}.png` });
  const r = await page.evaluate(() => [window.R21.placed, window.R21.total]);
  console.log(n, r.join('/'), 'named');
  if (page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();

// Round 23: several ski resorts, drawn over the real site. node design/round23/shots.mjs (site on :4199)
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const OUT = new URL('./shots/', import.meta.url).pathname; fs.mkdirSync(OUT, { recursive: true });
const PROP = new URL('./proposal.js', import.meta.url).pathname;
const D2 = { 'gud-view': '2d' };
const NOW = '2026-10-06T11:00:00+04:00';
const S = [
  ['today-home', '/#home', null, {}],
  ['picker-phone', '/#home', { resort: 'gud', picker: true }, {}],
  ['picker-desk', '/#home', { resort: 'gud', picker: true }, { w: 1280, h: 800 }],
  ['sol-home', '/#home', { resort: 'sol' }, {}],
  ['sol-home-night', '/#home', { resort: 'sol' }, { theme: 'night' }],
  ['sol-home-desk', '/#home', { resort: 'sol' }, { w: 1280, h: 800 }],
  ['sol-picker', '/#home', { resort: 'sol', picker: true }, {}],
  ['today-map', '/#map', null, {}],
  ['plates-phone', '/#map', { resort: 'sol', plates: true }, {}],
  ['plates-night', '/#map', { resort: 'sol', plates: true }, { theme: 'night' }],
  ['plates-desk', '/#map', { resort: 'sol', plates: true }, { w: 1280, h: 800 }],
];
const b = await launch();
for (const [n, u, p, o] of S) {
  const { ctx, page } = await open(b, u, { theme: 'day', extraStorage: D2, wait: 2500, now: NOW, trip: null, ...o });
  if (p) { await page.addScriptTag({ path: PROP }); const c = await page.evaluate(q => window.R23.apply(q), p); if (p.plates) console.log(n, 'plates', c); await page.waitForTimeout(700); }
  await page.screenshot({ path: OUT + n + '.png' });
  if (page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();
console.log(S.length, 'shots');

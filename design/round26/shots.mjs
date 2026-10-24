// Round 26: the Android app sign links to Google Play, drawn over the real site. node design/round26/shots.mjs [prefix] (site on :4199)
// The official badges and the QR come from R26_INLINE (badge PNGs from play.google.com, QR of the store link; not kept in the repo).
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const HERE = new URL('./', import.meta.url).pathname, OUT = HERE + 'shots/', PROP = HERE + 'proposal.js';
const IN = JSON.parse(fs.readFileSync(process.env.R26_INLINE, 'utf8'));
const NOW = '2026-10-24T11:00:00+03:00', DESK = { w: 1280, h: 800 };
const P = (mode, x = {}) => ({ mode, qr: IN.qr, badge: IN.badge, ...x });
export const S = [
  ['today', P('today'), {}], ['today-open', P('today', { open: 1 }), {}],
  ['a-he', P('sign'), {}], ['a-he-night', P('sign'), { theme: 'night' }], ['a-en', P('sign', { lang: 'en' }), { lang: 'en' }], ['a-en-night', P('sign', { lang: 'en' }), { lang: 'en', theme: 'night' }],
  ['a-iphone', P('none'), {}],
  ['b-he', P('badge'), {}], ['b-en-night', P('badge', { lang: 'en' }), { lang: 'en', theme: 'night' }],
  ['d-he', P('desk'), DESK], ['d-en-night', P('desk', { lang: 'en' }), { ...DESK, lang: 'en', theme: 'night' }],
  ['f-iphone', P('sign', { ios: 'only' }), {}], ['f-desk', P('desk', { ios: 1, future: 1 }), DESK],
];
const only = process.argv[2];
const b = await launch();
for (const [n, p, o] of S) {
  if (only && !n.startsWith(only)) continue;
  const { ctx, page } = await open(b, '/', { wait: 3000, now: NOW, trip: null, w: o.w, h: o.h, theme: o.theme || 'day', lang: o.lang || 'he' });
  await page.evaluate(fs.readFileSync(PROP, 'utf8'));
  console.log(n, await page.evaluate(q => window.R26.apply(q), { ...p, lang: o.lang || 'he' }));
  await page.waitForTimeout(400);
  await page.evaluate(() => { const e = document.querySelector('.post'); e && e.scrollIntoView({ block: 'center' }) });
  await page.screenshot({ path: OUT + n + '.png' });
  await ctx.close();
}
await b.close();

// Round 22: the map as the main screen, four directions drawn over the real site. node design/round22/shots.mjs (site on :4199)
import { launch, open, MYTRIP } from '../round16/lib.mjs';
import fs from 'node:fs';
const OUT = new URL('./shots/', import.meta.url).pathname; fs.mkdirSync(OUT, { recursive: true });
const PROP = new URL('./proposal.js', import.meta.url).pathname;
const D2 = { 'gud-view': '2d' };
const PRE = '2026-10-06T10:00:00+04:00', DURING = '2027-01-12T10:00:00+04:00';
const when = { pre: { now: PRE, trip: MYTRIP }, during: { now: DURING, trip: MYTRIP }, none: { now: PRE, trip: null } };
const b = await launch();
// the mountain as a picture, for the home-page window (B)
async function mapImg(theme) {
  const { ctx, page } = await open(b, '/#map', { theme, extraStorage: D2, wait: 2500, w: 390, h: 844 });
  const buf = await page.locator('.mapwrap').screenshot(); await ctx.close();
  return 'data:image/png;base64,' + buf.toString('base64');
}
const IMG = { day: await mapImg('day'), night: await mapImg('night') };
const S = [];
const APP = 'data:image/png;base64,' + fs.readFileSync(new URL('../round21/audit/app-overview.png', import.meta.url)).toString('base64');
for (const s of ['pre', 'during', 'none']) S.push([`APP-${s}`, '/#home', { v: 'APP', st: s }, { h: 866 }]);
for (const v of ['A', 'C', 'D']) for (const s of ['pre', 'during', 'none']) S.push([`${v}-${s}`, '/#map', { v, st: s }, {}]);
S.push(['A-pre-night', '/#map', { v: 'A', st: 'pre' }, { theme: 'night' }]);
S.push(['A-during-night', '/#map', { v: 'A', st: 'during' }, { theme: 'night' }]);
S.push(['A-menu', '/#map', { v: 'A', st: 'pre', sheet: 'menu' }, {}]);
S.push(['A-ticket', '/#map', { v: 'A', st: 'pre', sheet: 'ticket' }, {}]);
S.push(['A-desk', '/#map', { v: 'A', st: 'during' }, { w: 1280, h: 800 }]);
for (const s of ['pre', 'during', 'none']) S.push([`B-${s}`, '/#home', { v: 'B', st: s }, {}]);
S.push(['B-pre-night', '/#home', { v: 'B', st: 'pre' }, { theme: 'night' }]);
S.push(['B-desk', '/#home', { v: 'B', st: 'pre' }, { w: 1280, h: 800 }]);
S.push(['today-home', '/#home', null, {}]);
S.push(['today-map', '/#map', null, {}]);
for (const [n, u, p, o] of S) {
  const w = when[(p && p.st) || 'pre'];
  const { ctx, page } = await open(b, u, { theme: 'day', extraStorage: D2, wait: 2500, now: w.now, trip: w.trip, ...o });
  if (p) { await page.addScriptTag({ path: PROP }); await page.evaluate(q => window.R22.apply(q), { ...p, appImg: APP, mapImg: IMG[o.theme === 'night' ? 'night' : 'day'] }); await page.waitForTimeout(900); }
  await page.screenshot({ path: OUT + n + '.png' });
  if (page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();
console.log(S.length, 'shots');

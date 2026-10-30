// Round 29, stage 1: the day's maps, drawn on the site's own top-view map (the apps draw the same top view, A-30) with
// day.js, saved as PNGs for the boards, and the day itself as shots/day.json. Run from the repo root with the site served:
//   node tools/serve.mjs 4199 &   node design/round29/maps.mjs
import fs from 'node:fs';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname), OUT = path.join(HERE, 'shots');
fs.mkdirSync(OUT, { recursive: true });
const JS = fs.readFileSync(path.join(HERE, 'day.js'), 'utf8');
// only the map, the whole window
const CSS = `.mapwrap{position:fixed!important;inset:0!important;z-index:9999!important;height:100vh!important;width:100vw!important;margin:0!important;border:0!important}
.mapwrap>*:not(#map){display:none!important} #map{width:100vw!important;height:100vh!important}`;
const T = (h, m) => h * 3600 + m * 60;
// name: [w, h, theme, lang, draw options (box: a time window to frame or 'all')]
const MAPS = {
  'm-live': [402, 820, 'day', 'he', { mode: 'live', upto: T(10, 19) + 40, dot: true, win: [T(9, 49), T(10, 20)], pad: 0.25 }],
  'm-live-night': [402, 820, 'night', 'en', { mode: 'live', upto: T(10, 19) + 40, dot: true, win: [T(9, 49), T(10, 20)], pad: 0.25, night: true }],
  'm-day': [402, 470, 'day', 'he', { mode: 'speed', pad: 0.1 }],
  'm-day-night': [402, 470, 'night', 'en', { mode: 'speed', pad: 0.1, night: true }],
  'm-lift-night': [402, 820, 'night', 'en', { mode: 'live', upto: T(10, 31), dot: true, win: [T(9, 49), T(10, 31)], pad: 0.25, night: true }],
  'm-off-night': [402, 820, 'night', 'en', { mode: 'live', upto: T(11, 52) + 15, dot: true, offwin: true, pad: 0.6, night: true }],
  'm-share': [540, 470, 'day', 'he', { mode: 'speed', pad: 0.08, dimTo: '.3' }],
  'm-off': [402, 820, 'day', 'he', { mode: 'live', upto: T(11, 52) + 15, dot: true, offwin: true, pad: 0.6 }],
};
const b = await launch();
let saved = false;
for (const [name, [w, h, theme, lang, o]] of Object.entries(MAPS)) {
  const { ctx, page } = await open(b, '/#map', { w, h, theme, lang, wait: 2500, mobile: false });
  await page.addStyleTag({ content: CSS });
  await page.evaluate(JS);
  await page.evaluate(() => window.dispatchEvent(new Event('resize')));
  await page.waitForTimeout(600);
  const res = await page.evaluate(async o => {
    const day = await R29.day();
    if (o.win) o.box = R29.boxOf(o.win[0], o.win[1], 60);
    if (o.offwin) { const s = day.segs.find(q => q.offpiste); o.box = R29.boxOf(s.t0, s.t1, 40); }
    return { box: await R29.draw(o), day };
  }, o);
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(OUT, name + '.png') });
  if (!saved) { fs.writeFileSync(path.join(OUT, 'day.json'), JSON.stringify(res.day)); saved = true; console.log(JSON.stringify(res.day.stats), res.day.segs.length); }
  await ctx.close();
}
await b.close();
console.log('ok');

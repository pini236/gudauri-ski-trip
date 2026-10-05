// Round 19: the proposals drawn inside the real site (proto.css, proto.js) and photographed, as in rounds 12 and 16.
// Run from the repo root with the site served: npx http-server site -p 4199 -s -c-1 &  then: node design/round19/shots.mjs [name...]
import fs from 'node:fs';
import path from 'node:path';
import { launch, open, MYTRIP, shoot, checks } from '../round16/lib.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname), OUT = path.join(HERE, 'shots');
fs.mkdirSync(OUT, { recursive: true });
const CSS = fs.readFileSync(path.join(HERE, 'proto.css'), 'utf8'), JS = fs.readFileSync(path.join(HERE, 'proto.js'), 'utf8');
const TRIPDAY = '2027-01-12T10:15:00+04:00', AHEAD = '2026-12-29T09:10:00+04:00';
const PH = { w: 390, h: 844 }, DESK = { w: 1280, h: 800 };
const ON = { trip: MYTRIP, now: TRIPDAY };

// name: [url, open options, scene calls, how to photograph]
const SCENES = {
  'map-weather': ['/#map', { ...PH, ...ON }, [['weather', {}]], 'list'],
  'map-weather-none': ['/#map', { ...PH, ...ON }, [['weather', { nodata: true }]], 'list'],
  'map-weather-stale': ['/#map', { ...PH, ...ON }, [['weather', { stale: true }]], 'list'],
  'map-weather-night': ['/#map', { ...PH, ...ON, theme: 'night' }, [['weather', {}]], 'view'],
  'home-today': ['/#home', { ...PH, ...ON }, [['homeToday', {}]], 'full'],
  'home-today-live-night': ['/#home', { ...PH, ...ON, theme: 'night' }, [['homeToday', { live: true }]], 'full'],
  'home-ahead': ['/#home', { ...PH, trip: MYTRIP, now: AHEAD }, [['homeAhead', {}]], 'full'],
  'home-today-en': ['/#home', { ...PH, ...ON, lang: 'en', locale: 'en-US' }, [['homeToday', { lang: 'en' }]], 'full'],
  'run-cond': ['/#map/run/Tatra%202', { ...PH, ...ON }, [['runCond', {}]], 'full'],
  'loc-ask': ['/#map', { ...PH, ...ON }, [['locate', { state: 'ask' }]], 'view'],
  'loc-on': ['/#map', { ...PH, ...ON }, [['locate', { state: 'on' }]], 'view'],
  'loc-approx': ['/#map', { ...PH, ...ON }, [['locate', { state: 'approx' }]], 'view'],
  'loc-out': ['/#map', { ...PH, ...ON }, [['locate', { state: 'out' }]], 'view'],
  'loc-denied': ['/#map', { ...PH, ...ON }, [['locate', { state: 'denied' }]], 'view'],
  'loc-low': ['/#map', { ...PH, ...ON }, [['locate', { state: 'low' }]], 'view'],
  'loc-lift': ['/#map', { ...PH, ...ON }, [['locate', { state: 'lift' }]], 'view'],
  'loc-unavail': ['/#map', { ...PH, ...ON }, [['locate', { state: 'unavail' }]], 'view'],
  'loc-down': ['/#map', { ...PH, ...ON }, [['locate', { state: 'down' }]], 'list'],
  'desk-map': ['/#map/run/Tatra%202', { ...DESK, ...ON }, [['weather', { list: false }], ['runCond', {}], ['locate', { state: 'on', noFrame: true }]], 'view'],
  'desk-home': ['/#home', { ...DESK, ...ON }, [['homeToday', {}]], 'full'],
};

const want = process.argv.slice(2);
const b = await launch();
const report = {};
for (const [name, [url, o, calls, how]] of Object.entries(SCENES)) {
  if (want.length && !want.includes(name)) continue;
  const { ctx, page } = await open(b, url, { ...o, wait: 2500 });
  await page.addStyleTag({ content: CSS });
  await page.addScriptTag({ content: JS });
  for (const [fn, arg] of calls) await page.evaluate(([f, a]) => window.R19[f](a), [fn, arg]);
  await page.waitForTimeout(700);
  const file = path.join(OUT, name + '.png');
  if (how === 'full') await shoot(page, file); else await page.screenshot({ path: file });
  // the list under the map, photographed on its own: a full-page capture resizes the window and the site re-fits the map
  if (how === 'list') { await page.addStyleTag({ content: '.r19-wlist{padding:14px 16px 6px;margin:0 -16px 12px;background:var(--snow)}' }); await page.locator('.r19-wlist').screenshot({ path: path.join(OUT, name + '-list.png') }); }
  report[name] = { errors: page._errors, checks: (await checks(page)).filter(c => /r19|overlap|sideways|small touch/.test(c)).slice(0, 8) };
  await ctx.close();
}
await b.close();
fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify(report, null, 1));
console.log(JSON.stringify(report, null, 1));

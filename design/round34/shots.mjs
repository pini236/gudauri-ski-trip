// Round 34: snowball fight, can you see the friends? Before and after for three framings, drawn by the site's own game
// (site/games/snowball/game.js) with only the framing numbers changed in the browser. Android and iPhone use the same numbers.
//   node design/round34/shots.mjs   (the site on http://127.0.0.1:4199: node tools/serve.mjs 4199)
import { chromium } from 'playwright';
import path from 'node:path';
const OUT = path.join(path.dirname(new URL(import.meta.url).pathname), 'shots');
const SITE = 'http://127.0.0.1:4199';
// today, and the three framings proposed by the iPhone session
const V = {
  now: {},
  a: { ZF: 7, ZO: 7.6 },
  b: { Y0: .55 },
  c: { ZF: 7, ZO: 7.6, Y0: .55, FWALL: .85 },
  // ours: a longer lens instead of moving the wall, so distances, flight times and difficulty stay as they are
  d: { ZOOM: 1.4, Y0: .5 },
};
function patch(js, v) {
  let s = js;
  if (v.ZF) s = s.replace('ZF=10, ZO=10.6', `ZF=${v.ZF}, ZO=${v.ZO}`);
  if (v.FWALL) s = s.replace('FWALL=1.05', `FWALL=${v.FWALL}`);
  if (v.ZOOM) s = s.replace('f=Math.min(W*1.75, H*1.15)', `f=Math.min(W*1.75, H*1.15)*${v.ZOOM}`);
  if (v.Y0) s = s.replace('Y0=H*.42', `Y0=H*${v.Y0}`);
  return s;
}
const b = await chromium.launch();
for (const [k, v] of Object.entries(V).filter(([k]) => !process.argv[2] || process.argv.slice(2).includes(k))) for (const pose of ['up', 'peek', 'aim']) for (const lang of ['he']) {
  const ctx = await b.newContext({ viewport: { width: 393, height: 852 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'he-IL', reducedMotion: 'reduce' });
  await ctx.addInitScript(() => { try { localStorage.setItem('gud-lang', 'he'); localStorage.setItem('gud-analytics', 'off'); } catch (_) {} });
  await ctx.route(/game\.js(\?.*)?$/, async r => { const res = await r.fetch(); r.fulfill({ response: res, body: patch(await res.text(), v) }); });
  await ctx.route(/posthog|sentry/, r => r.abort());
  const page = await ctx.newPage();
  await page.goto(SITE + '/games/snowball/'); await page.waitForTimeout(1200);
  await page.evaluate(([pose]) => {
    rung = 2; start(); Math.random = () => .5;
    for (const o of foes) { o.timer = 1e9; o.up = o.want = pose === 'peek' ? .45 : 1; o.phase = pose === 'peek' ? 'peek' : 'aim'; }
    if (pose === 'aim') { camH = 1.75; aim = { x: innerWidth / 2, y: innerHeight * .78, y0: innerHeight * .9 }; }
    document.getElementById('hint').hidden = true;
  }, [pose]);
  await page.waitForTimeout(500);
  await page.evaluate(([pose]) => { for (const o of foes) { o.up = pose === 'peek' ? .45 : 1; } if (pose === 'aim') camH = 1.75; else camH = .98; balls.length = 0; }, [pose]);
  await page.waitForTimeout(80);
  await page.screenshot({ path: path.join(OUT, `s-${k}-${pose}.png`) });
  await ctx.close();
}
await b.close(); console.log('ok');

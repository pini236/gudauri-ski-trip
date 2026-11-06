// Round 34 check (game designer): rung 5, wind 5, the edge friend (x = 2 + 0.8 drift), aiming upwind to hit them.
import { chromium } from 'playwright';
import path from 'node:path';
const OUT = path.join(path.dirname(new URL(import.meta.url).pathname), 'shots');
const V = { now: {}, d: { ZOOM: 1.4, Y0: .5 }, d2: { ZOOM: 1.4, Y0: .5, SPREAD: 1 / 1.4, LEAN: 1.5 } };
const patch = (s, v) => { if (v.ZOOM) s = s.replace('f=Math.min(W*1.75, H*1.15)', `f=Math.min(W*1.75, H*1.15)*${v.ZOOM}`); if (v.Y0) s = s.replace('Y0=H*.42', `Y0=H*${v.Y0}`);
  // d2: the friends closer together across (by 1/1.4) and a view that follows your finger further (the ring includes the pan)
  if (v.SPREAD) { s = s.replace('xs=cfg.n===1?[0]:cfg.n===2?[-1.5,1.5]:[-2,0,2]', `xs=(cfg.n===1?[0]:cfg.n===2?[-1.5,1.5]:[-2,0,2]).map(x=>x*${v.SPREAD})`).replace('o.home+rnd(-.8,.8)', `o.home+rnd(-.8,.8)*${v.SPREAD}`); }
  if (v.LEAN) { s = s.replace('(aim.x-W/2)/W*.6', `(aim.x-W/2)/W*${v.LEAN}`).replace('return [ (a.x-W/2)/s,', 'return [ (a.x-W/2)/s+camX,'); }
  return s; };
const b = await chromium.launch();
for (const [k, v] of Object.entries(V)) {
  const ctx = await b.newContext({ viewport: { width: 393, height: 852 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'he-IL', reducedMotion: 'reduce' });
  await ctx.addInitScript(([e, l]) => { window.__edge = e; window.__lean = l; }, [v.SPREAD ? 2.8 * v.SPREAD : 2.8, v.LEAN || 0]);
  await ctx.addInitScript(() => { try { localStorage.setItem('gud-lang', 'he'); localStorage.setItem('gud-analytics', 'off'); } catch (_) {} });
  await ctx.route(/game\.js(\?.*)?$/, async r => { const res = await r.fetch(); r.fulfill({ response: res, body: patch(await res.text(), v) }); });
  await ctx.route(/posthog|sentry/, r => r.abort());
  const page = await ctx.newPage();
  await page.goto('http://127.0.0.1:4199/games/snowball/'); await page.waitForTimeout(1200);
  const info = await page.evaluate(() => {
    rung = 4; start(); Math.random = () => .5; wind = -5; windLabel();
    const edge = window.__edge; const xs = [-edge, 0, edge]; foes.forEach((o, i) => { o.x = o.home = xs[i]; o.goal = null; o.timer = 1e9; o.up = o.want = 1; o.phase = 'aim'; });
    // aim upwind of the friend at +2.8: the ball drifts .25*wind*FLIGHT^2 = -0.9 m, so aim at 3.7 m (clamped to the screen edge)
    const s = f / 10.6, need = edge + .25 * 5 * .85 * .85, l = window.__lean, dx = l ? need / (1 / s + l / innerWidth) : need * s, xNeed = innerWidth / 2 + dx, reach = innerWidth / 2 / s + (l ? l / 2 : 0);
    camH = 1.75; aim = { x: Math.min(innerWidth - 2, xNeed), y: innerHeight * .74, y0: innerHeight * .9 };
    document.getElementById('hint').hidden = true;
    return { halfWidthM: +(innerWidth / 2 / s).toFixed(2), reachM: +reach.toFixed(2), needM: +need.toFixed(2), foeEdgePx: Math.round(innerWidth / 2 + edge * s), aimPx: Math.round(xNeed) };
  });
  await page.waitForTimeout(500);
  await page.waitForTimeout(1500);
  await page.evaluate(() => { foes.forEach(o => o.up = 1); camH = 1.75; balls.length = 0; });
  await page.waitForTimeout(80);
  await page.screenshot({ path: path.join(OUT, `s-${k}-edge.png`) });
  console.log(k, JSON.stringify(info));
  await ctx.close();
}
await b.close();

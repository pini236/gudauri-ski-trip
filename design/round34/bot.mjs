// Snowball fight bot (from the game designer, 1.11.2026): same human-like policy on each variant, fast-forwarded.
//   node design/round34/bot.mjs   (site on http://127.0.0.1:4199; env: N, SIG, RT, FS="1/1/1 .85/.95/.95")
import { chromium } from 'playwright';
const N = +(process.env.N || 150);
const SIG = +(process.env.SIG || 14); // finger noise in px (x and y)
const RT0 = +(process.env.RT || .2);   // reaction time, + up to .15
const S = 1 / 1.4, LIM = (2 / 1.4).toFixed(3);
const base = { ZOOM: 1.4, Y0: .5, SPREAD: S, LEAN: 1.5, AIMFIX: 1, CLAMP: 1 };
const V = { now: {} };
for (const F of (process.env.FS || '1/1/1').split(' ')) V['d2 up ' + F] = { ...base, UPF: F };
const rep = (s, a, b) => { if (!s.includes(a)) throw new Error('patch miss: ' + a); return s.replace(a, b); };
const patch = (s, v) => {
  s = rep(s, 'requestAnimationFrame(frame)', '0'); // no live loop; the bot steps update()
  if (v.ZOOM) s = rep(s, 'f=Math.min(W*1.75, H*1.15)', `f=Math.min(W*1.75, H*1.15)*${v.ZOOM}`);
  if (v.Y0) s = rep(s, 'Y0=H*.42', `Y0=H*${v.Y0}`);
  if (v.SPREAD) { s = rep(s, 'xs=cfg.n===1?[0]:cfg.n===2?[-1.5,1.5]:[-2,0,2]', `xs=(cfg.n===1?[0]:cfg.n===2?[-1.5,1.5]:[-2,0,2]).map(x=>x*${v.SPREAD})`);
    s = rep(s, 'o.goal=o.home+rnd(-.8,.8)', v.CLAMP ? `o.goal=Math.max(-${LIM},Math.min(${LIM},o.home+rnd(-.8,.8)*${v.SPREAD}))` : `o.goal=o.home+rnd(-.8,.8)*${v.SPREAD}`); }
  if (v.LEAN) { s = rep(s, '(aim.x-W/2)/W*.6', `(aim.x-W/2)/W*${v.LEAN}`); s = rep(s, 'return [ (a.x-W/2)/s,', 'return [ (a.x-W/2)/s+camX,'); }
  if (v.AIMFIX) s = rep(s, 'const tx=rnd(-1,1)*cfg.err', 'const tx=camX+rnd(-1,1)*cfg.err');
  if (v.UPF) s = rep(s, 'cfg=RUNGS[rung];', `cfg=RUNGS[rung]; { const F=[1,1,${v.UPF.split('/').join(',')}][rung]; cfg={...cfg, up:cfg.up.map(x=>x*F)}; }`);
  return s;
};
const b = await chromium.launch();
const out = {};
for (const [k, v] of Object.entries(V)) {
  const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, locale: 'he-IL' });
  await ctx.addInitScript(() => { try { localStorage.setItem('gud-lang', 'he'); localStorage.setItem('gud-analytics', 'off'); localStorage.setItem('gud-sound', 'off'); } catch (_) {} });
  await ctx.route(/game\.js(\?.*)?$/, async r => { const res = await r.fetch(); r.fulfill({ response: res, body: patch(await res.text(), v) }); });
  await ctx.route(/posthog|sentry/, r => r.abort());
  const page = await ctx.newPage();
  page.on('pageerror', e => console.error(k, 'pageerror', e.message));
  await page.goto('http://127.0.0.1:4199/games/snowball/'); await page.waitForTimeout(800);
  out[k] = await page.evaluate(([N, SIG, RT0]) => {
    window.noise = window.tone = window.buzz = () => {}; window.popText = () => {};
    const gauss = () => { let u = 0, w = 0; while (!u) u = Math.random(); while (!w) w = Math.random(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * w); };
    const res = [];
    for (let r = 0; r < RUNGS.length; r++) {
      let wins = 0, s3 = 0, starsSum = 0, taken = 0, thrown = 0, hits = 0;
      for (let n = 0; n < N; n++) {
        rung = r; start();
        let target = null, react = 0, nx = 0, ny = 0, held = 0, tt = 0; const dt = 1 / 60;
        while (state !== 'end' && tt < 240) {
          tt += dt;
          if (state === 'play') {
            if (!target) { // react to a friend who starts to rise
              const up = foes.filter(o => o.hp > 0 && o.want === 1);
              if (up.length && reload <= 0) { if (react <= 0) react = RT0 + Math.random() * .15; react -= dt;
                if (react <= 0) { target = up[Math.floor(Math.random() * up.length)]; nx = gauss() * SIG; ny = gauss() * SIG; held = 0; } }
              else react = 0;
            }
            if (target) { // closed loop on the ring, like a person watching it, with the wind offset
              const s = f / ZO, tx = target.x - .25 * wind * FLIGHT * FLIGHT, ty = FWALL - 1.95 + target.up * .95 + 1.62;
              const lean = ringTarget({ x: W / 2, y: 0 })[0];
              const ax = W / 2 + (tx - lean) * s + nx, ay = Y0 + (1.75 - ty) * s + OFF + ny;
              aim = { x: ax, y: ay, y0: ay + 60 }; held += dt;
              if (camH >= 1.5 && held > .15 && target.up > .7) { letGo(); target = null; react = 0; }
              else if (target.hp <= 0) { aim = null; target = null; }
            }
          }
          update(dt);
        }
        const g = [won, won && stats.taken === 0, won && cfg.goal.ok(stats)], st = g.filter(Boolean).length;
        wins += won ? 1 : 0; s3 += st === 3 ? 1 : 0; starsSum += st; taken += stats.taken; thrown += stats.thrown; hits += stats.hits;
      }
      res.push({ rung: r + 1, win: Math.round(wins / N * 100), three: Math.round(s3 / N * 100), stars: +(starsSum / N).toFixed(2), acc: Math.round(hits / thrown * 100), takenPerGame: +(taken / N).toFixed(2) });
    }
    return res;
  }, [N, SIG, RT0]);
  await ctx.close();
}
await b.close();
for (const [k, rows] of Object.entries(out)) { console.log('\n' + k); console.table(rows); }

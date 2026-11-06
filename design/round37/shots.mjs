// Round 37: the iPhone app sign next to Google Play's, drawn over the real site; and the /app page states. node design/round37/shots.mjs [prefix] (site on :4199)
// R37_INLINE=<json with qrPlay, qrIos, badge> (built from site/img/badges and the store links; not kept in the repo).
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const HERE = new URL('./', import.meta.url).pathname, OUT = HERE + 'shots/', PROP = HERE + 'proposal.js';
const IN = JSON.parse(fs.readFileSync(process.env.R37_INLINE, 'utf8'));
const NOW = '2026-11-06T11:00:00+02:00', DESK = { w: 1280, h: 800 };
const P = (mode, x = {}) => ({ mode, qrPlay: IN.qrPlay, qrIos: IN.qrIos, badge: IN.badge, ...x });
export const S = [
  ['today-iphone', P('none'), {}],
  ['a-ios-he', P('ios'), {}], ['a-ios-he-night', P('ios'), { theme: 'night' }], ['a-ios-en', P('ios', { lang: 'en' }), { lang: 'en' }], ['a-ios-en-night', P('ios', { lang: 'en' }), { lang: 'en', theme: 'night' }],
  ['a-android', P('android'), {}],
  ['d-he', P('desk'), DESK], ['d-en-night', P('desk', { lang: 'en' }), { ...DESK, lang: 'en', theme: 'night' }],
];
const only = process.argv[2];
const b = await launch();
for (const [n, p, o] of S) {
  if (only && !n.startsWith(only)) continue;
  const { ctx, page } = await open(b, '/', { wait: 3000, now: NOW, trip: null, w: o.w, h: o.h, theme: o.theme || 'day', lang: o.lang || 'he' });
  if (p.mode !== 'none') { await page.evaluate(fs.readFileSync(PROP, 'utf8')); console.log(n, await page.evaluate(q => window.R37.apply(q), { ...p, lang: o.lang || 'he' })); }
  else await page.evaluate(() => { document.getElementById('appBoard').hidden = true; const q = document.getElementById('appQr'); q && (q.hidden = true); });
  await page.waitForTimeout(400);
  await page.evaluate(() => { const e = document.querySelector('.post'); e && e.scrollIntoView({ block: 'center' }) });
  await page.screenshot({ path: OUT + n + '.png' });
  await ctx.close();
}
// /app page states: the real page, its redirect script switched off, the state set by hand
const apple = '<span style="display:flex;flex-direction:column;justify-content:center;height:52px;padding:0 16px;box-sizing:border-box;background:#000;color:#fff;border-radius:9px;outline:1px dashed #9aa6b5;outline-offset:2px;font-family:-apple-system,Arial,sans-serif;direction:ltr;text-align:left;line-height:1"><i style="font:normal 10px/1 -apple-system,Arial">Download on the</i><b style="font:600 23px/1.05 -apple-system,Arial;color:#fff">App Store</b></span>';
const PG = [
  ['page-ios', { going: 'App Store' }, {}], ['page-ios-night', { going: 'App Store' }, { theme: 'night' }],
  ['page-desk', { both: 1 }, {}], ['page-desk-night', { both: 1 }, { theme: 'night' }],
  ['page-android', { going: 'Google Play' }, {}],
];
for (const [n, st, o] of PG) {
  if (only && !n.startsWith(only)) continue;
  const { ctx, page } = await open(b, '/app', { wait: 1500, now: NOW, trip: null, w: 390, h: 640, theme: o.theme || 'day', lang: 'he',
    pre: async c => c.route(/\/js\/app-link\.js/, r => r.fulfill({ contentType: 'text/javascript', body: '' })) });
  await page.evaluate(([s, ap, play]) => {
    const $ = i => document.getElementById(i);
    ['alGoing', 'alBadge', 'alIos', 'alBtn', 'alSite'].forEach(i => $(i).hidden = true);
    if (s.going) { $('alGoing').hidden = false; $('alGoing').firstElementChild.innerHTML = 'פותחים את <bdi>' + s.going + '</bdi>…'; }
    if (s.both) {
      const row = document.createElement('div'); row.style.cssText = 'display:flex;gap:12px;flex-wrap:wrap;justify-content:center;margin-top:6px';
      row.innerHTML = '<img alt="Google Play" height="52" style="display:block;height:52px;width:auto" src="' + play + '">' + ap; $('alGoing').parentNode.insertBefore(row, $('alSite'));
      $('alSite').hidden = false; $('alSite').textContent = 'או למפה באתר';
    }
  }, [st, apple, IN.badge.he]);
  await page.waitForTimeout(300);
  await page.screenshot({ path: OUT + n + '.png' });
  console.log(n);
  await ctx.close();
}
await b.close();

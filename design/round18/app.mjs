// Round 18 proposals on the app: drawn over real screenshots of the emulator run on main (release android-qa, qa.zip,
// shots/*.png, 1080x2400), at phone width (390). The app uses the site's tokens (its unit tests check them against site.css),
// so the drawings use site.css. Nothing here is in the app.
// Run from the repo root with the site served (fonts and tokens): node design/round18/app.mjs <folder with the qa shots> [out]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const SRC = process.argv[2], OUT = (process.argv[3] || path.join(os.tmpdir(), 'gud-r18')) + '/';
fs.mkdirSync(OUT, { recursive: true });
const img = f => 'data:image/png;base64,' + fs.readFileSync(path.join(SRC, f)).toString('base64');
const K = 390 / 1080;
const slice = (f, y0, y1) => `<div style="height:${Math.round((y1 - y0) * K)}px;overflow:hidden"><img src="${img(f)}" style="display:block;width:390px;margin-top:${-Math.round(y0 * K)}px"></div>`;
const ICON = {
  auto: '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor"/></svg>',
  day: '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="5" fill="#F4B942"/><path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8" stroke="#F4B942" stroke-width="2" stroke-linecap="round"/></svg>',
  night: '<svg width="24" height="24" viewBox="0 0 34 34" aria-hidden="true"><path d="M17 3A14 14 0 0 1 17 31A7.5 14 0 0 0 17 3Z" fill="currentColor"/></svg>',
};
// the day-night button over the app's header (the icon sits at x 199, y 246 on the 1080 shot): the site's round button
const dn = (f, mode, night) => `<div style="position:relative">${slice(f, 90, 420)}
  <span style="position:absolute;left:${Math.round(199 * K) - 22}px;top:${Math.round((246 - 90) * K) - 22}px;width:44px;height:44px;box-sizing:border-box;display:grid;place-items:center;border:1.5px solid ${night ? '#E6ECF3' : 'var(--ink)'};border-radius:22px;background:${night ? '#141E2E' : 'var(--paper)'};color:${night ? '#E6ECF3' : 'var(--ink)'}">${ICON[mode]}</span>
  <span style="position:absolute;right:12px;bottom:6px;padding:2px 8px;background:var(--ink);color:var(--paper);font-size:12px;font-weight:700">${{ auto: 'אוטומטי', day: 'יום', night: 'לילה' }[mode]}</span></div>`;
const ROW = (l, name, sub) => `<div style="display:flex;align-items:center;gap:12px;min-height:60px;border-bottom:1px solid var(--rule)">
  <span style="width:40px;height:40px;flex:none;display:grid;place-items:center;background:var(--glacier);color:var(--paper);font-family:var(--f-display);font-size:26px;line-height:1">${l}</span>
  <span style="flex:1;display:flex;flex-direction:column"><b style="font-size:15.5px">${name}</b><small style="font-size:13px;color:var(--muted)">${sub}</small></span>
  <span style="color:var(--glacier);font-weight:700;font-size:14px">פתיחה</span></div>`;
const TOP = (title, back) => `<div style="display:flex;align-items:flex-end;justify-content:space-between;padding:52px 16px 12px"><h1 style="margin:0;font-family:var(--f-display);font-weight:700;font-size:40px;line-height:1">${title}</h1>
  <span style="display:flex;align-items:center;gap:6px;color:var(--glacier);font-weight:700;font-size:15px"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="M13 6l6 6-6 6"/></svg>${back}</span></div>`;
const LIC = [['Karantina', 'SIL Open Font License 1.1'], ['IBM Plex Sans Hebrew', 'SIL Open Font License 1.1'], ['IBM Plex Sans', 'SIL Open Font License 1.1'], ['Oswald', 'SIL Open Font License 1.1'], ['Noto Sans Georgian', 'SIL Open Font License 1.1'],
  ['Jetpack Compose, AndroidX', 'Apache License 2.0'], ['Kotlin, kotlinx', 'Apache License 2.0'], ['OkHttp', 'Apache License 2.0'], ['PostHog Android', 'MIT'], ['Sentry Android', 'MIT'], ['Sign in with Google, Install Referrer', 'תנאי ה-SDK של אנדרואיד']];
const ofl = fs.readFileSync(new URL('../../app/android/licenses/OFL-Karantina.txt', import.meta.url), 'utf8').split('\n').slice(0, 30).join('\n');

const BOARDS = {
  // K-4: "my groups" on the account page, as on the site (between the ways in and "what syncs")
  'a-k4-account': () => slice('group-30-account.png', 0, 900) + `<section style="padding:6px 16px 14px;background:var(--snow)"><h2 style="margin:8px 0 4px;font-family:var(--f-display);font-weight:700;font-size:34px;line-height:1">הקבוצות שלי</h2>
      ${ROW('ג', 'גודאורי 2027', '6 חברים · מנהל')}${ROW('ס', 'סקי עם המשפחה', '3 חברים')}
      <p style="margin:8px 0 0;font-size:13px;color:var(--muted)">לפי תאריך ההתחלה, כמו באתר. השלט "קבוצה" בדף הבית פותח את הקבוצה הקרובה, ומכאן עוברים לאחרת.</p></section>` + slice('group-30-account.png', 940, 1500),
  // P-K2: the button shows the mode, with the site's three icons
  'a-pk2-auto': () => dn('home-10-home-trip-day.png', 'auto'),
  'a-pk2-day': () => dn('home-10-home-trip-day.png', 'day'),
  'a-pk2-night': () => dn('home-22-home-trip-night.png', 'night', true),
  // A-28: the map labels and the meet picture in English: IBM Plex Sans (the site) instead of IBM Plex Sans Hebrew
  'a-a28-fonts': () => `<div style="padding:16px;background:var(--paper)">
      ${[['היום באפליקציה', "'IBM Plex Sans Hebrew'"], ['ההצעה: כמו באתר', "'IBM Plex Sans'"]].map(([h, f]) => `<p style="margin:10px 0 4px;font-size:13px;font-weight:700;color:var(--muted)">${h} · <span dir="ltr">${f.replace(/'/g, '')}</span></p>
      <div dir="ltr" style="font-family:${f};font-weight:700;font-size:26px;line-height:1.3;color:var(--ink)">Tatra 2 · 2,665 m<br>New Goodaura · Kobi Pass<br><span style="font-size:17px">Sadzele 3,307 m · Bidara 3,174 m</span></div>`).join('<hr style="border:0;border-top:1px solid var(--rule);margin:14px 0">')}
      <p style="margin:14px 0 0;font-size:13px;line-height:1.5;color:var(--muted)">האותיות הלטיניות כמעט זהות. ההבדל בעיקר ברוחב ובספרות, וכך שני הצדדים מציירים אותו טקסט באותו גופן.</p></div>`,
  // A-24: a "licenses" row at the end of the credits, a list, and the text of one license
  'a-a24-about': () => slice('home-34-home-about-3.png', 1220, 2330) + `<div style="padding:0 16px 22px;background:var(--snow)"><div style="display:flex;align-items:center;gap:12px;min-height:60px;border-top:1px solid var(--rule);border-bottom:1px solid var(--rule)">
      <span style="flex:1;display:flex;flex-direction:column"><b style="font-size:16px">רישיונות קוד פתוח</b><small style="font-size:13px;color:var(--muted)">הטקסט המלא של כל רישיון, כמו שהם דורשים</small></span><span style="font-size:22px;color:var(--glacier)">‹</span></div></div>`,
  'a-a24-list': () => `<div style="min-height:844px;background:var(--snow)">${TOP('רישיונות', 'אודות')}<div style="padding:0 16px">
      ${LIC.map(([n, l]) => `<div style="display:flex;align-items:center;gap:10px;min-height:56px;border-bottom:1px solid var(--rule)"><span style="flex:1;display:flex;flex-direction:column"><b dir="ltr" style="font-size:15px;text-align:right">${n}</b><small style="font-size:12.5px;color:var(--muted)">${l}</small></span><span style="font-size:22px;color:var(--glacier)">‹</span></div>`).join('')}
      <p style="margin:12px 0 0;font-size:13px;line-height:1.5;color:var(--muted)">הנתונים, הצלילים והסרטונים בקרדיטים באודות. הטקסטים כבר ארוזים באפליקציה (<span dir="ltr">assets/licenses</span>).</p></div></div>`,
  'a-a24-text': () => `<div style="min-height:844px;background:var(--snow)">${TOP('Karantina', 'רישיונות')}<div style="padding:0 16px"><p style="margin:0 0 8px;font-size:13px;color:var(--muted)">SIL Open Font License 1.1</p>
      <pre dir="ltr" style="margin:0;white-space:pre-wrap;font-family:'IBM Plex Sans',sans-serif;font-size:12.5px;line-height:1.5;color:var(--ink)">${ofl.replace(/</g, '&lt;')}</pre></div></div>`,
};

const b = await launch();
const { ctx, page } = await open(b, '/', { w: 390, h: 844, wait: 300 });
for (const [name, html] of Object.entries(BOARDS)) {
  await page.setContent(`<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><link rel="stylesheet" href="http://127.0.0.1:4199/css/site.css">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=IBM+Plex+Sans+Hebrew:wght@400;600;700&family=IBM+Plex+Sans:wght@400;700&display=swap">
<style>html,body{height:auto;margin:0;background:var(--snow);color:var(--ink);font-family:var(--f-body)} .phone{width:390px;direction:rtl;overflow:hidden;position:relative}</style></head><body><div class="phone">${html()}</div></body></html>`, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready); await page.waitForTimeout(300);
  await page.locator('.phone').screenshot({ path: OUT + name + '.png' });
}
await ctx.close(); await b.close();
console.log('ok', Object.keys(BOARDS).join(' '));

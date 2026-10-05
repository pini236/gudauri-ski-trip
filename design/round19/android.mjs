// Round 19, Android: the proposals drawn over real screenshots of the app (the emulator run on main, release android-qa,
// store-*.png), at phone width. The app uses the site's tokens (checked against site.css by its unit tests), so the
// proposals use proto.css as on the site; where the app has its own component (the run sheet, the bottom bar of the map)
// the drawing follows the app. The heights are the ones the site computes from its terrain model (shots.mjs). Sample weather.
// Run from the repo root with the site served (fonts and tokens): node design/round19/android.mjs <folder with store-*.png>
import fs from 'node:fs';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname), OUT = path.join(HERE, 'shots');
const SRC = process.argv[2];
const img = f => 'data:image/png;base64,' + fs.readFileSync(path.join(SRC, f)).toString('base64');
const K = 390 / 1080; // the screenshots are 1080 wide
const CSS = fs.readFileSync(path.join(HERE, 'proto.css'), 'utf8');
const arrow = (from, s = 12) => `<svg class="r19-arrow" width="${s}" height="${s}" viewBox="0 0 16 16" aria-hidden="true" style="transform:rotate(${from + 180}deg)"><path d="M8 1.5v12M3.5 6 8 1.5 12.5 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const deg = t => `<span dir="ltr">${t < 0 ? '−' : ''}${Math.abs(t)}°</span>`;
const meter = n => `<span class="r19-meter" aria-hidden="true">${[0, 1, 2].map(i => `<i class="${i <= n ? 'on' : ''}"></i>`).join('')}</span>`;
// a slice of a screenshot, in 1080-px coordinates, shown at phone width
const slice = (f, y0, y1) => `<div style="height:${Math.round((y1 - y0) * K)}px;overflow:hidden"><img src="${img(f)}" style="display:block;width:390px;margin-top:${-Math.round(y0 * K)}px"></div>`;
const ALT = { village: 2170, goodaura: 2710, sadzele: 3240 };
const ROW = (name, alt, t, wind, gust, snow, dir) => `<div class="r19-row"><span class="rn"><b><bdi>${name}</bdi></b><span><bdi>${alt.toLocaleString('he-IL')} מ׳</bdi></span></span><span class="rt">${deg(t)}</span><span class="rw"><span>${arrow(dir, 13)} רוח ${wind} קמ״ש, משבים ${gust}</span><span>שלג חדש ${snow} ס״מ</span></span></div>`;
const LOC = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="6.5"/><circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none"/><path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3"/></svg>';
const CLOUD = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.5 2A3 3 0 0 0 7 18z"/></svg>';

const today = `<section class="r19-today" style="margin:18px 16px 22px;max-width:none">
  <div class="td-head"><b>היום על ההר</b><em>עודכן לפני 20 דק׳</em></div><p class="td-sub">יום סקי 2 מתוך 4 · שלישי, 12.1</p>
  <div class="r19-alts">
    <div><small><bdi>הכפר</bdi></small><small><bdi>2,170 מ׳</bdi></small><b class="tt">${deg(-4)}</b><span>${arrow(290)} 8 קמ״ש</span><span>שלג 5 ס״מ</span></div>
    <div><small><bdi>Goodaura העליונה</bdi></small><small><bdi>2,710 מ׳</bdi></small><b class="tt">${deg(-9)}</b><span>${arrow(300)} 24 קמ״ש</span><span>שלג 9 ס״מ</span></div>
    <div><small><bdi>Sadzele</bdi></small><small><bdi>3,240 מ׳</bdi></small><b class="tt">${deg(-13)}</b><span>${arrow(300)} 48 קמ״ש</span><span>שלג 12 ס״מ</span></div>
  </div>
  <div class="r19-risk"><b style="font-size:13px;color:var(--muted)">סגירה בגלל רוח · הערכה</b>
    <div class="rk"><b dir="ltr">Sadzele</b><span>גבוה</span>${meter(2)}</div><div class="rk"><b dir="ltr">Kudebi</b><span>בינוני</span>${meter(1)}</div>
    <span class="rk-why">הערכה לפי התחזית, לא דיווח של אתר הסקי. כשיש דיווח של MTA על הרכבלים, הוא קובע.</span></div>
  <a class="r19-lifts" href="#"><span class="ms-dot" style="background:var(--dash)"></span><span>עוד אין דיווח על הרכבלים</span><span class="go">מצב הרכבלים</span></a>
  <p class="r19-note" style="margin-top:6px">נתוני מזג האוויר: <a href="https://open-meteo.com/">Open-Meteo.com</a></p>
</section>`;

const BOARDS = {
  // the home page: the ticket, then the board where the season board is today, then the signs
  'and-home-today': () => slice('store-7-home.png', 0, 1110) + today + slice('store-7-home.png', 1300, 1920),
  // the run sheet, scrolled up: the conditions after the numbers, as on the site's panel
  'and-run-cond': () => slice('store-2-run.png', 0, 1840) + `<section class="r19-cond" style="padding:0 16px 18px;background:var(--paper)">
      <h3 style="margin:6px 0 2px;font-family:var(--f-display);font-size:28px;line-height:1">התנאים במסלול</h3>
      ${ROW('בראש המסלול', 2665, -8, 22, 36, 8, 300)}${ROW('בתחתית', 2170, -5, 10, 18, 6, 290)}
      <p class="r19-note">תחזית לגובה של ראש המסלול ושל התחתית, עודכנה לפני 20 דק׳. ההר עצמו יכול להיות קר וסוער יותר בקטעים חשופים. נתוני מזג האוויר: <a href="https://open-meteo.com/">Open-Meteo.com</a></p></section>`,
  // the map: a "weather" button beside "all runs" at the bottom, and the same list as the site in a bottom sheet
  'and-map-weather': () => `<div style="position:relative">${slice('store-1-map.png', 0, 1920)}
      <div class="r19-shade" style="background:rgba(13,21,34,.25)"></div>
      <div style="position:absolute;left:0;right:0;bottom:0;z-index:21;background:var(--paper);border-top:1px solid var(--rule);padding:8px 16px 20px">
        <div style="width:44px;height:4px;margin:0 auto 10px;background:var(--rule)"></div>
        <section class="r19-wlist" style="margin:0"><h2>מזג אוויר לפי גובה</h2><p class="wl-sub">תחזית לגובה של כל נקודה · עודכנה לפני 20 דק׳</p>
        ${ROW('Sadzele', 3240, -13, 48, 70, 12, 300)}${ROW('Goodaura העליונה', 2710, -9, 24, 40, 9, 300)}${ROW('הכפר', 2170, -4, 8, 15, 5, 290)}
        <p class="r19-note">בתלת-ממד הנקודות יושבות על ההר, כמו תוויות הפסגות. נגיעה בשורה מעבירה אליה. נתוני מזג האוויר: <a href="https://open-meteo.com/">Open-Meteo.com</a></p></section></div></div>`,
  // the map, layer off: where the button sits (beside "all runs"), so it is found
  'and-map-buttons': () => `<div style="position:relative">${slice('store-1-map.png', 0, 1920)}
      <button type="button" style="position:absolute;left:${Math.round(32 * K)}px;bottom:${Math.round(108 * K) + 50}px;z-index:5;display:flex;align-items:center;gap:8px;height:44px;padding:0 14px;border:1.5px solid var(--ink);background:var(--paper);color:var(--ink);font:inherit;font-weight:700;font-size:15px">${CLOUD}מזג אוויר</button>
      <button type="button" aria-label="איפה אני" style="position:absolute;right:16px;bottom:${Math.round(240 * K)}px;z-index:5;width:52px;height:52px;display:grid;place-items:center;border:1.5px solid var(--rule);background:var(--paper);color:var(--ink)">${LOC}</button></div>`,
  // where am I, on: the dot on Tatra 2 (the run lit on this screenshot), the button lit, the line at the bottom
  'and-loc-on': () => `<div style="position:relative">${slice('store-2-run.png', 0, 1130)}
      <div class="r19-me" style="left:${Math.round(497 * K)}px;top:${Math.round(520 * K)}px"><span class="acc" style="width:30px;height:30px"></span><span class="hd"></span><span class="pt"></span></div>
      <button type="button" aria-label="איפה אני" aria-pressed="true" style="position:absolute;right:16px;bottom:78px;z-index:5;width:52px;height:52px;display:grid;place-items:center;border:1.5px solid var(--glacier);background:var(--glacier);color:var(--paper)">${LOC}</button>
      <div class="r19-where" role="status"><span class="wd"></span><span class="wt"><b>על Tatra 2 · כחול</b><span>דיוק של כ-12 מ׳</span></span></div></div>`,
};
// the run-sheet board is long; the list boards are a phone screen

const b = await launch();
const { ctx, page } = await open(b, '/', { w: 390, h: 844, wait: 300 });
for (const [name, html] of Object.entries(BOARDS)) {
  await page.setContent(`<!doctype html><html lang="he" dir="rtl"><head><meta charset="utf-8"><link rel="stylesheet" href="http://127.0.0.1:4199/css/site.css">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&display=swap"><style>${CSS}
html,body{height:auto;margin:0;background:var(--snow)} .phone{width:390px;direction:rtl;overflow:hidden;position:relative}</style></head><body><div class="phone">${html()}</div></body></html>`, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  await page.locator('.phone').screenshot({ path: path.join(OUT, name + '.png') });
}
await ctx.close(); await b.close();
console.log('ok', Object.keys(BOARDS).join(' '));

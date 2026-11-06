// Round 29, row J: three contrast findings from the iPhone session, before and after, one fix for every platform.
//   node design/round29/contrast.mjs   (writes shots/j-contrast-day.png and shots/j-contrast-night.png)
import { chromium } from 'playwright';
import path from 'node:path';
const OUT = path.join(path.dirname(new URL(import.meta.url).pathname), 'shots');
const hg = c => `<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="${c}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 3h12M6 21h12M7 3c0 5 10 5 10 9s-10 4-10 9M17 3c0 5-10 5-10 9s10 4 10 9"/></svg>`;
function page(night) {
  const T = night ? { bg: '#0D1522', paper: '#16223A', ink: '#EAF0F7', muted: '#A3B3C8', dash0: '#51627D', dash1: '#6A7C99', amb0: '#F4B942', amb1: '#F4B942', g0: '#3CC47C', g1: '#3CC47C', on: '#0D1522' }
    : { bg: '#EEF2F5', paper: '#FFFFFF', ink: '#13233A', muted: '#4B5A6F', dash0: '#8E9CAD', dash1: '#77869A', amb0: '#C98A00', amb1: '#A87300', g0: '#1B8A4C', g1: '#198549', on: '#FFFFFF' };
  const row = (title, before, after, b, a) => `<div class="r"><h3>${title}</h3><div class="c"><div><small>היום · <bdi dir=ltr>${b}</bdi></small>${before}</div><div><small>ההצעה · <bdi dir=ltr>${a}</bdi></small>${after}</div></div></div>`;
  const sign = g => `<div class="sg" style="background:${g};color:${T.on}">בית הספר לסקי</div>`;
  const chip = d => `<div class="chip" style="border-color:${d}">כחול</div><div class="chip" style="border-color:${d}">אדום</div>`;
  const pend = a => `<div class="pend">${hg(a)}<span>ממתין לאישור המנהל</span></div>`;
  const note = night ? ['בלילה הירוק והשעון כבר עוברים; רק הקו המקווקו מתחת ל-3:1.'] : ['ערך אחד לכל הפלטפורמות: האתר, אנדרואיד ואייפון. הירוק כמעט לא משתנה לעין, והמפה נשארת כמו שהיא.'];
  return `<!doctype html><meta charset=utf-8><html dir=rtl><link rel=stylesheet href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=IBM+Plex+Sans+Hebrew:wght@400;600;700&display=swap">
<style>body{margin:0;background:${T.bg};color:${T.ink};font:15px/1.4 "IBM Plex Sans Hebrew";width:760px;padding:22px}h2{font:700 38px/1 Karantina;margin:0 0 4px}p.n{color:${T.muted};margin:0 0 14px}
.r{background:${T.paper};padding:12px 16px 16px;margin:0 0 12px}.r h3{margin:0 0 8px;font-size:16px}.c{display:grid;grid-template-columns:1fr 1fr;gap:18px}.c small{display:block;color:${T.muted};margin:0 0 6px;font-weight:600;direction:rtl}
.sg{display:inline-block;padding:10px 22px 10px 30px;font:700 30px/1 Karantina;clip-path:polygon(0 50%,14px 0,100% 0,100% 100%,14px 100%)}
.chip{display:inline-flex;align-items:center;min-height:40px;padding:0 14px;border:1.5px dashed;margin-inline-end:8px;font-weight:600;background:${T.bg}}
.pend{display:flex;align-items:center;gap:10px;font-weight:600}</style>
<h2>${night ? 'בלילה' : 'ביום'}</h2><p class="n">${note[0]}</p>
${row('1. טקסט לבן על הירוק', sign(T.g0), sign(T.g1), night ? 'עובר' : '4.39:1', night ? 'ללא שינוי' : '#198549 · 4.67:1')}
${row('2. הקו המקווקו (צ׳יפים, "הבא", ספח הכרטיס)', chip(T.dash0), chip(T.dash1), night ? '#51627D · 2.96:1' : '#8E9CAD · 2.48:1', night ? '#6A7C99 · 4.32:1' : '#77869A · 3.30:1')}
${row('3. שעון החול (ממתין לאישור)', pend(T.amb0), pend(T.amb1), night ? 'עובר' : '#C98A00 · 2.95:1', night ? 'ללא שינוי' : '#A87300 · 4.10:1')}`;
}
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 804, height: 600 }, deviceScaleFactor: 1 });
for (const night of [false, true]) {
  await p.setContent(page(night)); await p.waitForTimeout(600);
  await p.screenshot({ path: path.join(OUT, `j-contrast-${night ? 'night' : 'day'}.png`), fullPage: true });
}
await b.close(); console.log('ok');

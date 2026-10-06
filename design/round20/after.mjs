// Round 20: the same screens after the text pass, drawn inside the real site (round 16 harness). Only the site's
// classes and tokens; nothing here is in the site. Each change follows a rule in classify.py: delete, replace with a
// shape, or keep what must stay in a form that fits it.
// Run from the repo root with the site served on 4199:  node design/round20/after.mjs [out]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { launch, open, MYTRIP } from '../round16/lib.mjs';

const OUT = (process.argv[2] || path.join(os.tmpdir(), 'gud-r20')) + '/after/';
fs.mkdirSync(OUT, { recursive: true });
const D2 = { 'gud-view': '2d' };
const CSS = `
.r20-chip{display:inline-flex;align-items:center;gap:6px;font-size:12.5px;font-weight:600;color:var(--muted)}
.r20-chip svg{flex:none}
.r20-i{display:inline-grid;place-items:center;width:22px;height:22px;margin-inline-start:6px;border:1.5px solid var(--muted);border-radius:50%;color:var(--muted);font:700 12px/1 var(--f-body);vertical-align:2px}
.r20-badge{display:inline-grid;place-items:center;min-width:44px;height:36px;padding:0 8px;margin-inline-start:10px;background:var(--paper);color:var(--ink);font-family:var(--f-display);font-size:30px;line-height:1;vertical-align:6px}
.r20-steps{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:0;margin:6px 0 14px;border-top:1px solid var(--rule);border-bottom:1px solid var(--rule)}
.r20-steps>div{display:flex;flex-direction:column;gap:2px;padding:8px 6px 9px;border-inline-start:1px solid var(--rule)}
.r20-steps>div:first-child{border-inline-start:0;padding-inline-start:0}
.r20-steps b{font-family:var(--f-display);font-size:28px;line-height:1}
.r20-steps small{font-size:12px;color:var(--muted)}
.r20-steps .st b{color:var(--p-red)}
.r20-conf{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:6px 0 12px}
.r20-dots{display:flex;gap:4px}.r20-dots i{width:12px;height:12px;border-radius:50%;border:1.5px solid var(--ink);box-sizing:border-box}.r20-dots i.on{background:var(--ink)}
.r20-src{display:inline-flex;align-items:center;gap:6px;height:32px;padding:0 10px;border:1.5px solid var(--rule);font-size:13px;font-weight:600;background:var(--paper)}
.r20-more{color:var(--glacier);font-weight:700;font-size:14px}
.r20-why{display:flex;gap:18px;justify-content:space-between;margin:8px 0 4px}
.r20-why>span{flex:1;display:flex;flex-direction:column;align-items:center;gap:6px;font-size:13px;font-weight:600;text-align:center}
.r20-why svg{color:var(--glacier)}
`;
const I = {
  lock: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><rect x="5" y="11" width="14" height="10"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>',
  sound: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 9h4l5-4v14l-5-4H4z"/><path d="M16 9a4 4 0 0 1 0 6M18.5 6.5a8 8 0 0 1 0 11"/></svg>',
  key: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3M15 8l2 2"/><path d="M3 3l18 18" stroke-width="2.2"/></svg>',
  mail: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3" y="5" width="18" height="14"/><path d="M3 6l9 7 9-7"/><path d="M3 3l18 18" stroke-width="2.2"/></svg>',
  user: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/></svg>',
  group: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="9" cy="8" r="3.2"/><path d="M3 19c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5"/><circle cx="17" cy="9" r="2.5"/><path d="M16 13.6c2.8.3 5 2.2 5 5.4"/></svg>',
  pin: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z"/><circle cx="12" cy="9" r="2.5"/></svg>',
  cup: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M7 4h10v5a5 5 0 0 1-10 0z"/><path d="M7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4M12 14v4M8 21h8"/></svg>',
  plane: '<svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M21 15.5v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V8.5l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-6z"/></svg>',
  sync: '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.6 1.5A3.5 3.5 0 0 0 7 18z"/></svg>',
};
// in the page: hide the smallest element whose own text has the substring; put html in place of it
const helpers = () => {
  const own = el => [...el.childNodes].filter(t => t.nodeType === 3).map(t => t.textContent).join(' ');
  const find = sub => [...document.querySelectorAll('body *')].filter(el => own(el).includes(sub));
  window.r20kill = sub => find(sub).forEach(el => { el.style.display = 'none'; });
  window.r20swap = (sub, html) => find(sub).forEach(el => { el.outerHTML = html; });
  window.r20after = (sub, html) => { const el = find(sub)[0]; if (el) el.insertAdjacentHTML('beforeend', html); };
};
const STATES = [
  ['home-guest', '/#home', { trip: null }, () => {
    ['בוחרים תחנה ושעה', 'הירידה, בית הספר לסקי', 'יצירת קבוצה, או הצטרפות', 'כרגע אין דיווח עדכני מ-MTA'].forEach(r20kill);
    const sub = [...document.querySelectorAll('body *')].find(el => [...el.childNodes].some(t => t.nodeType === 3 && t.textContent.includes('27 מסלולים')));
    if (sub) { const t = sub.previousElementSibling || sub.parentElement.firstElementChild; if (t && t !== sub) t.insertAdjacentHTML('beforeend', '<span class="r20-badge">27</span>'); sub.style.display = 'none'; }
    r20swap('בלי הרשמה. נשמר רק בדפדפן', `<span class="r20-chip">${window.I.lock}רק במכשיר הזה</span>`);
  }],
  ['map-run', '/#map/run/Tatra%202', { extraStorage: D2, wait: 2500 }, () => {
    const pan = document.querySelector('.panel, #panel, aside') || document;
    const ol = document.querySelector('ol.brief');
    if (ol) {
      const li = [...ol.children].map(x => x.querySelector('b').textContent);
      const n = s => (s.match(/[\d,]+/) || [''])[0];
      ol.outerHTML = `<div class="r20-steps"><div><small>התחלה</small><b class="num">${n(li[0])}</b><small>מ׳ · 5°</small></div>
        <div class="st"><small>הקטע התלול</small><b class="num">20°</b><small>אחרי ${n(li[1])} מ׳</small></div>
        <div><small>סוף</small><b class="num">${n(li[2] || li[1])}</b><small>מ׳ · Shino</small></div></div>`;
    }
    document.querySelectorAll('.hint').forEach(h => { if (/פני השטח עד|גבהים מתוך מודל|חושב מקרבת|\(100 מ׳/.test(h.textContent)) h.style.display = 'none'; });
    document.querySelectorAll('h3').forEach(h => { if (/פרופיל|חיבורים/.test(h.textContent)) h.insertAdjacentHTML('beforeend', '<span class="r20-i" aria-label="איך זה חושב">i</span>'); });
    const src = [...document.querySelectorAll('h3')].find(h => /מקור/.test(h.textContent));
    if (src) {
      let el = src.nextElementSibling; const kill = [];
      while (el && el.tagName !== 'H3') { kill.push(el); el = el.nextElementSibling; }
      kill.forEach(k => k.style.display = 'none');
      src.insertAdjacentHTML('afterend', `<div class="r20-conf"><span class="r20-dots" aria-label="ודאות גבוהה"><i class="on"></i><i class="on"></i><i class="on"></i></span><b>ודאות גבוהה</b></div>
        <div class="r20-conf"><span class="r20-src">OSM · היסטוריה</span><span class="r20-src">GPS × 3</span><span class="r20-src">MTA · שם וצבע</span></div>
        <p><a class="r20-more" href="#">הפירוט המלא ‹</a></p>`);
    }
  }],
  ['meet', '/#meet', { trip: MYTRIP }, async () => {
    ['לחיצה על שטח ריק במפה', 'הקישור עובד בלי הרשמה'].forEach(r20kill);
    r20swap('לפי החיבורים בנתוני המסלולים', '');
    const h = [...document.querySelectorAll('h3,h2')].find(x => /איך מגיעים/.test(x.textContent));
    if (h) h.insertAdjacentHTML('beforeend', '<span class="r20-i">i</span>');
  }, '[data-pre="am"]'],
  ['games', '/#games', {}, () => {
    ['הירידה על המסלולים האמיתיים', 'מעצירה בפיצה ועד קרווינג', 'לגעת בשלג ולרסק', 'מפתית ועד מלך קזבק', 'מאחורי החומה, מול'].forEach(r20kill);
    r20swap('על השלג של גודאורי. השיאים', `<p class="r20-chip" style="margin:4px 0 10px">${window.I.sound}עם צליל</p>`);
  }],
  ['about', '/#about', { signed: true }, () => {
    ['אוטומטי לפי השעה בגודאורי', 'נבחרה כאן, ונשמרת', 'השיאים שנשמרו בדפדפן', 'בטלפונים שתומכים'].forEach(r20kill);
  }],
  ['signin', '/#signin', {}, () => {
    r20swap('בלי סיסמה, ובלי מיילים', `<div class="r20-why"><span>${window.I.key}בלי סיסמה</span><span>${window.I.mail}בלי מיילים</span><span>${window.I.user}רק השם</span></div>`);
    r20kill('כל השאר עובד בלי חשבון');
    r20swap('ליצור קבוצה ולהזמין', '<span>קבוצה והזמנות</span>');
    r20swap('הטיול והקבוצה, גם באפליקציה', '<span>גם באפליקציה</span>');
  }],
  ['group-empty', '/#group', { trip: null }, () => {
    r20swap('קבוצה היא דף משותף', `<div class="r20-why" style="margin:6px 0 14px"><span>${window.I.plane}הטיסות של כולם</span><span>${window.I.pin}מפגשים</span><span>${window.I.cup}שיאים</span></div>`);
  }],
  ['join', '/#join/KZBQRM', { wait: 1500 }, () => {
    ['בלי הרשמה. הדפדפן הזה זוכר', 'החלפת טלפון או דפדפן'].forEach(r20kill);
  }],
];

const b = await launch();
for (const [name, url, opts, fn, click] of STATES) {
  const { ctx, page } = await open(b, url, { w: 390, h: 844, now: '2026-12-01T10:00:00+04:00', ...opts });
  if (click) { await page.locator(click).first().click().catch(() => {}); await page.waitForTimeout(700); }
  await page.addStyleTag({ content: CSS });
  await page.evaluate(i => { window.I = i; }, I);
  await page.evaluate(helpers);
  await page.evaluate(fn);
  await page.waitForTimeout(400);
  await page.screenshot({ path: OUT + name + '.png', fullPage: true });
  console.log(name, page._errors.length ? 'errors: ' + page._errors.slice(0, 2).join(' | ') : '');
  await ctx.close();
}
await b.close();

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

.r20-steps b u{text-decoration:none;font-size:16px;margin-inline-start:3px}
.season.r20-off{display:flex;flex-direction:column;gap:10px;opacity:.62;filter:grayscale(1);cursor:default;pointer-events:none}
.season.r20-off>span{display:flex;margin:0}.season.r20-off em{float:none;color:var(--ink)}
.r20-off-h{display:flex;align-items:center;gap:8px}.r20-off-h em{font-style:normal;font-weight:700;font-size:15px}
.r20-lifts{display:flex;gap:6px;flex-wrap:wrap;direction:ltr}.r20-lifts i{flex:none;width:16px;height:16px;border-radius:50%;border:2px dashed var(--muted)}
.r20-off-f{display:flex;align-items:baseline;gap:10px}.r20-off-f b{font-family:var(--f-display);font-size:30px;line-height:1}.r20-off-f small{font-size:13px;color:var(--muted)}
.r20-tother{display:grid;place-items:center;min-height:48px;border:1.5px dashed var(--ink);background:var(--paper);color:var(--ink);padding:0}
.r20-day{display:block;font-size:13px;color:var(--muted);text-align:end}
.r20-ico{display:flex;gap:10px;justify-content:center;margin:14px 0}
.r20-ico button{display:grid;place-items:center;width:48px;height:48px;border:1.5px solid var(--ink);background:var(--paper);color:var(--ink);padding:0}
.r20-ico button:first-child{background:var(--ink);color:var(--paper)}
.r20-shade{position:fixed;inset:0;z-index:50;background:rgba(13,21,34,.42)}
.r20-sheet{position:fixed;left:0;right:0;bottom:0;z-index:51;display:flex;flex-direction:column;gap:12px;padding:10px 16px 24px;background:var(--paper);color:var(--ink);border-top:6px solid var(--ink)}
.r20-sheet .grab{width:44px;height:4px;margin:0 auto;background:var(--rule)}
.r20-sheet h2{margin:4px 0 4px;font-family:var(--f-display);font-weight:700;font-size:30px;line-height:1.05}
.r20-gbtn,.r20-abtn{display:flex;align-items:center;justify-content:center;gap:10px;min-height:52px;border:1.5px solid var(--rule);background:#fff;color:#1F1F1F;font:inherit;font-size:15px;font-weight:600}
.r20-abtn{border-style:dashed;color:var(--muted);background:none}.r20-abtn small{font-size:12px;border:1px solid var(--rule);padding:1px 6px}
.r20-pend{display:flex;align-items:center;gap:14px;margin:18px 0;padding:14px 16px;background:var(--paper);border-inline-start:6px solid #F4B942;box-shadow:0 2px 8px rgba(13,21,34,.08)}
.r20-pend svg{flex:none;color:#C98A00}.r20-pend span{display:flex;flex-direction:column;align-items:flex-start;gap:4px}
.r20-pend b{font-family:var(--f-display);font-size:24px;line-height:1}.r20-pend button{border:0;background:none;padding:6px 0;color:var(--glacier);font:inherit;font-weight:700;font-size:14px}
.r20-jh{display:flex;flex-direction:column;gap:6px}.r20-jh>small{font-size:13px;color:var(--muted)}
.r20-jh>b{font-family:var(--f-display);font-size:44px;line-height:1}
.r20-jm{display:flex;gap:18px;align-items:center;margin-top:4px}.r20-jm>span{display:flex;align-items:center;gap:6px}
.r20-jm b{font-family:var(--f-display);font-size:22px;line-height:1}
.r20-av{display:flex;align-items:center}.r20-av i{display:grid;place-items:center;width:28px;height:28px;margin-inline-start:-6px;border-radius:50%;border:2px solid var(--paper);background:var(--glacier);color:#fff;font:700 13px/1 var(--f-body);font-style:normal}
.r20-av i:first-child{margin-inline-start:0}.r20-av b{margin-inline-start:6px}
.r20-app{display:flex;align-items:center;gap:12px;margin:14px 0;padding:10px 12px;background:var(--paper);border:1.5px solid var(--rule)}
.r20-app svg{flex:none;color:var(--ink)}
.r20-code{display:flex;gap:4px;flex:1}.r20-code i{display:grid;place-items:center;width:30px;height:38px;border:1.5px solid var(--ink);font:700 20px/1 var(--f-body);font-style:normal}
.r20-app button{display:grid;place-items:center;width:44px;height:44px;border:0;background:none;color:var(--glacier);padding:0}
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
  lift: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2 5l20-3M12 3.5V8"/><rect x="6" y="8" width="12" height="10" rx="1"/><path d="M6 13h12"/></svg>',
  clock: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/><path d="M19 3v4M17 5h4"/></svg>',
  wa: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 20l1.3-4A8 8 0 1 1 8 18.7z"/><path d="M9 9c0 3 3 6 6 6l1-1.5-2-1-1 1c-1-.5-2-1.5-2.5-2.5l1-1-1-2z"/></svg>',
  img: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3" y="5" width="18" height="14"/><path d="M3 16l5-5 4 4 3-3 6 6"/><circle cx="16" cy="9" r="1.6"/></svg>',
  link: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/></svg>',
  map: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z"/><path d="M9 4v14M15 6v14"/></svg>',
  hour: '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M7 3h10M7 21h10M8 3c0 5 8 5 8 9s-8 4-8 9M16 3c0 5-8 5-8 9s8 4 8 9"/></svg>',
  phone: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="7" y="2" width="10" height="20" rx="2"/><path d="M11 18h2"/></svg>',
  copy: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="8" y="8" width="12" height="12"/><path d="M4 16V4h12"/></svg>',
  g: '<svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.7 13.3l7.9 6.1C12.5 13.6 17.8 9.5 24 9.5z"/><path fill="#4285F4" d="M46.1 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.4c-.5 2.9-2.2 5.3-4.6 7l7.5 5.8c4.4-4 6.8-10 6.8-17.3z"/><path fill="#FBBC05" d="M10.6 28.6A14.5 14.5 0 0 1 9.5 24c0-1.6.3-3.2.8-4.6l-7.9-6.1A24 24 0 0 0 0 24c0 3.9.9 7.5 2.7 10.7z"/><path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.5-5.8c-2.1 1.4-4.9 2.3-8.4 2.3-6.2 0-11.5-4.1-13.4-9.8l-7.9 6.1C6.6 42.6 14.6 48 24 48z"/></svg>',
  apple: '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M16.4 12.6c0-2.6 2.1-3.8 2.2-3.9-1.2-1.8-3.1-2-3.8-2-1.6-.2-3.1.9-3.9.9-.8 0-2-.9-3.4-.9-1.7 0-3.3 1-4.2 2.6-1.8 3.1-.5 7.7 1.3 10.2.9 1.2 1.9 2.6 3.2 2.6 1.3-.1 1.8-.8 3.3-.8 1.6 0 2 .8 3.4.8 1.4 0 2.3-1.3 3.1-2.5 1-1.4 1.4-2.8 1.4-2.9 0 0-2.6-1-2.6-4.1zM13.9 5c.7-.9 1.2-2 1.1-3.2-1 0-2.3.7-3 1.6-.7.8-1.2 2-1.1 3.1 1.1.1 2.3-.6 3-1.5z"/></svg>',
  cal: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="5" width="18" height="16"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>',
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

// v3 (Pini: "it looked like someone joining an existing group must say which group"): the link or code already sets
// the group, so the top of the page shows it as a ticket: the name, the dates and the members as shapes, then the name field
const joinHead = () => {
  const c = document.getElementById('joinCard'); if (!c) return;
  const lines = c.innerText.split('\n').map(x => x.trim()).filter(Boolean);
  const name = lines.find(l => /\d{4}|[א-ת]{3,}/.test(l) && !/הוזמנת|חברים/.test(l)) || lines[1] || '';
  const meta = lines.find(l => /חברים/.test(l)) || '';
  const n = (meta.match(/(\d+)\s*חברים/) || [, '4'])[1], dates = (meta.match(/(\d+\.\d+)\s*עד\s*(\d+\.\d+)/) || []).slice(1);
  const av = ['נ', 'ע', 'ד', 'י'].slice(0, Math.min(4, +n)).map(x => `<i>${x}</i>`).join('');
  c.querySelectorAll(':scope > :not(canvas):not(svg):not(.snowcap)').forEach(x => x.remove());
  c.insertAdjacentHTML('beforeend', `<div class="r20-jh"><small>מצטרפים ל</small><b>${name}</b><span class="r20-jm">
    <span>${window.I.cal}<b class="num" dir="ltr">${dates[0] || ''}–${dates[1] || ''}</b></span>
    <span class="r20-av">${av}<b class="num">${n}</b></span></span></div>`);
};
const STATES = [
  // v2 (6.10.2026, Pini's comments): no badge and no lock line, no tally under the signs, and the season board reads
  // as a lift-status widget that is switched off (twelve empty lift dots, greyed) instead of a sentence
  ['home-guest', '/#home', { trip: null }, () => {
    ['בוחרים תחנה ושעה', 'הירידה, בית הספר לסקי', 'יצירת קבוצה, או הצטרפות', '27 מסלולים, פרטים', 'בלי הרשמה. נשמר רק בדפדפן'].forEach(r20kill);
    ['.tally', '#tripNote'].forEach(q => { const t = document.querySelector(q); if (t) t.style.display = 'none'; });
    const sb = document.getElementById('seasonBoard');
    if (sb) {
      sb.classList.add('r20-off');
      sb.innerHTML = `<span class="r20-off-h">${window.I.lift}<em>מצב הרכבלים</em></span><span class="r20-lifts">${'<i></i>'.repeat(12)}</span><span class="r20-off-f"><b class="num" dir="ltr">0/12</b><small>אין דיווח</small></span>`;
    }
  }],
  // v2: big numbers only (no line under them), and no compare line, connections, notes or source block
  ['map-run', '/#map/run/Tatra%202', { extraStorage: D2, wait: 2500 }, () => {
    const ol = document.querySelector('ol.brief');
    if (ol) {
      const li = [...ol.children].map(x => x.querySelector('b').textContent);
      const n = s => (s.match(/[\d,]+/) || [''])[0];
      ol.outerHTML = `<div class="r20-steps"><div><small>התחלה</small><b class="num">${n(li[0])}<u>מ׳</u></b></div>
        <div class="st"><small>הקטע התלול</small><b class="num">20°</b></div>
        <div><small>סוף</small><b class="num">${n(li[2] || li[1])}<u>מ׳</u></b></div></div>`;
    }
    document.querySelectorAll('.hint').forEach(h => { if (/פני השטח עד|גבהים מתוך מודל|חושב מקרבת|\(100 מ׳/.test(h.textContent)) h.style.display = 'none'; });
    document.querySelectorAll('.run-cmp').forEach(x => x.style.display = 'none');
    document.querySelectorAll('h3').forEach(h => {
      if (/פרופיל/.test(h.textContent)) h.insertAdjacentHTML('beforeend', '<span class="r20-i" aria-label="איך זה חושב">i</span>');
      if (/חיבורים|הערות|מקור/.test(h.textContent)) {
        let el = h.nextElementSibling; h.style.display = 'none';
        while (el && el.tagName !== 'H3') { el.style.display = 'none'; el = el.nextElementSibling; }
      }
    });
  }],
  // v2: the pin callout is the station name only; "another time" is a seventh block in the time row; the card has the
  // station and the time (the day above it); no "how to get there"; sharing is a row of icon buttons
  ['meet', '/#meet', { trip: MYTRIP }, async () => {
    ['לחיצה על שטח ריק במפה', 'הקישור עובד בלי הרשמה'].forEach(r20kill);
    document.querySelectorAll('.mc-where, .mc-alt, .meet-own, .mc-meta, #meetRoutesSec, .mc-row small').forEach(x => x.style.display = 'none');
    const tm = document.querySelector('.meet-times');
    if (tm) { tm.style.gridTemplateColumns = 'repeat(7,minmax(0,1fr))'; tm.insertAdjacentHTML('beforeend', `<button type="button" class="r20-tother" aria-label="שעה אחרת">${window.I.clock}</button>`); }
    const tc = document.querySelector('.mc-end'); const day = document.querySelector('.mc-meta [data-f="day"]');
    if (tc && day) tc.insertAdjacentHTML('afterbegin', `<small class="r20-day">${day.textContent}</small>`);
    const sh = document.getElementById('meetShareBox');
    if (sh) sh.outerHTML = `<div class="r20-ico">${[['wa', 'וואטסאפ'], ['img', 'תמונה'], ['link', 'קישור'], ['map', 'במפה']].map(([k, l]) => `<button type="button" aria-label="${l}">${window.I[k]}</button>`).join('')}</div>`;
  }, '[data-pre="am"]'],
  // v2: no tags on the pictures and no sound line
  ['games', '/#games', {}, () => {
    ['הירידה על המסלולים האמיתיים', 'מעצירה בפיצה ועד קרווינג', 'לגעת בשלג ולרסק', 'מפתית ועד מלך קזבק', 'מאחורי החומה, מול', 'על השלג של גודאורי. השיאים', '5 מסלולים', '7 שיעורים', 'בלי ניקוד', 'משחק קצר', '5 יריבים'].forEach(r20kill);
  }],
  // v2: no subtitle under sound; the analytics line short and plain
  ['about', '/#about', { signed: true }, () => {
    ['אוטומטי לפי השעה בגודאורי', 'נבחרה כאן, ונשמרת', 'השיאים שנשמרו בדפדפן', 'בטלפונים שתומכים', 'בכרטיס הטיסה ובמשחקים'].forEach(r20kill);
    const a = document.getElementById('abAnaTxt'); if (a) a.textContent = 'בלי שם ובלי עוגיות. עוזר לנו לתקן תקלות.';
  }],
  // v2: the three promises move into the "why sign in" card; "(your trip)" goes
  ['signin', '/#signin', {}, () => {
    r20kill('בלי סיסמה, ובלי מיילים');
    r20kill('כל השאר עובד בלי חשבון');
    r20swap('ליצור קבוצה ולהזמין', '<span>קבוצה והזמנות</span>');
    r20swap('הטיול והקבוצה, גם באפליקציה', '<span>גם באפליקציה</span>');
    const ul = [...document.querySelectorAll('ul')].find(u => /קבוצה והזמנות/.test(u.textContent));
    if (ul) ul.insertAdjacentHTML('afterend', `<div class="r20-why" style="margin:12px 0 2px;padding-top:12px;border-top:1px solid var(--rule)"><span>${window.I.key}בלי סיסמה</span><span>${window.I.mail}בלי מיילים</span><span>${window.I.user}רק השם</span></div>`);
    [...document.querySelectorAll('body *')].forEach(el => { [...el.childNodes].forEach(t => { if (t.nodeType === 3 && t.textContent.includes('(הטיול שלך)')) t.textContent = t.textContent.replace(/\s*\(הטיול שלך\)/, ''); }); });
  }],
  ['group-empty', '/#group', { trip: null }, () => {
    r20swap('קבוצה היא דף משותף', `<div class="r20-why" style="margin:6px 0 14px"><span>${window.I.plane}הטיסות של כולם</span><span>${window.I.pin}מפגשים</span><span>${window.I.cup}שיאים</span></div>`);
    r20kill('יצירת קבוצה דורשת חשבון');
  }],
  // v2, new: tapping "create group" as a guest opens the sign-in sheet with one short line
  ['group-signin', '/#group', { trip: null }, () => {
    r20swap('קבוצה היא דף משותף', `<div class="r20-why" style="margin:6px 0 14px"><span>${window.I.plane}הטיסות של כולם</span><span>${window.I.pin}מפגשים</span><span>${window.I.cup}שיאים</span></div>`);
    r20kill('יצירת קבוצה דורשת חשבון');
    document.body.insertAdjacentHTML('beforeend', `<div class="r20-shade"></div><div class="r20-sheet" role="dialog"><span class="grab"></span>
      <h2>יצירת קבוצה דורשת התחברות</h2>
      <button type="button" class="r20-gbtn">${window.I.g}המשך עם Google</button>
      <button type="button" class="r20-abtn" disabled>${window.I.apple}המשך עם אפל <small>בקרוב</small></button></div>`);
  }],
  // v2: the pending request is a card (hourglass, title, cancel); the app code is a small card with the letters boxed
  ['join-name', '/#join/KZBQRM', { wait: 1500 }, () => {
    ['בלי הרשמה. הדפדפן הזה זוכר', 'החלפת טלפון או דפדפן'].forEach(r20kill);
    window.joinHead();
    const p = [...document.querySelectorAll('body *')].find(el => [...el.childNodes].some(t => t.nodeType === 3 && t.textContent.includes('הבקשה נשלחה למנהל')));
    if (p && p.parentElement) p.parentElement.style.display = 'none';
    const f = document.getElementById('joinForm'); if (f) f.hidden = false;
    document.querySelectorAll('[data-err]').forEach(e => e.hidden = true);
    const app = document.getElementById('joinApp');
    if (app) app.outerHTML = `<div class="r20-app">${window.I.phone}<span class="r20-code" dir="ltr">${'KZBQRM'.split('').map(c => `<i>${c}</i>`).join('')}</span><button type="button" aria-label="העתקה">${window.I.copy}</button></div>`;
    [...document.querySelectorAll('body *')].filter(el => el.offsetParent && !el.innerText.trim() && el.querySelector(':scope > svg') && !el.closest('button,a,header,.r20-app,.r20-jh')).forEach(el => el.style.display = 'none');
  }],
  ['join', '/#join/KZBQRM', { wait: 1500 }, () => {
    ['בלי הרשמה. הדפדפן הזה זוכר', 'החלפת טלפון או דפדפן'].forEach(r20kill);
    window.joinHead();
    [...document.querySelectorAll('body *')].filter(el => !el.children.length || el.querySelector('svg')).forEach(el => { if (el.offsetParent && !el.innerText.trim() && el.querySelector(':scope > svg') && !el.closest('button,a,header,.r20-app')) el.style.display = 'none'; });
    const p = [...document.querySelectorAll('body *')].find(el => [...el.childNodes].some(t => t.nodeType === 3 && t.textContent.includes('הבקשה נשלחה למנהל')));
    const box = p && p.parentElement;
    if (box) box.outerHTML = `<div class="r20-pend">${window.I.hour}<span><b>ממתין לאישור המנהל</b><button type="button">ביטול הבקשה</button></span></div>`;
    const app = document.getElementById('joinApp');
    if (app) app.outerHTML = `<div class="r20-app">${window.I.phone}<span class="r20-code" dir="ltr">${'KZBQRM'.split('').map(c => `<i>${c}</i>`).join('')}</span><button type="button" aria-label="העתקה">${window.I.copy}</button></div>`;
  }],
];

const b = await launch();
for (const [name, url, opts, fn, click] of STATES) {
  const { ctx, page } = await open(b, url, { w: 390, h: 844, now: '2026-12-01T10:00:00+04:00', ...opts });
  if (click) { await page.locator(click).first().click().catch(() => {}); await page.waitForTimeout(700); }
  await page.addStyleTag({ content: CSS });
  await page.evaluate(i => { window.I = i; }, I);
  await page.evaluate(helpers);
  await page.evaluate(`window.joinHead = ${joinHead.toString()}`);
  await page.evaluate(fn);
  await page.waitForTimeout(400);
  await page.screenshot({ path: OUT + name + '.png', fullPage: true });
  console.log(name, page._errors.length ? 'errors: ' + page._errors.slice(0, 2).join(' | ') : '');
  await ctx.close();
}
await b.close();

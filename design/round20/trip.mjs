// Round 20, point 2: "your trip" without a form. The ticket on the home page is the form: tap a field on the ticket,
// fill it in a sheet, and see it on the ticket at once. Only the date is needed (the countdown and the ski days come
// from it); the route starts as TLV to TBS, and the flight number and times are optional slots on the ticket.
// Drawn inside the real site (round 16 harness), with the site's own ticket. Nothing here is in the site.
// Run from the repo root with the site served on 4199:  node design/round20/trip.mjs [out]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const OUT = (process.argv[2] || path.join(os.tmpdir(), 'gud-r20')) + '/trip/';
fs.mkdirSync(OUT, { recursive: true });
const CSS = `
.r20-slot{display:inline-flex;align-items:center;gap:6px;padding:4px 8px;border:2px dashed var(--glacier);color:var(--glacier);background:color-mix(in srgb,var(--glacier) 7%,transparent);font:inherit;font-weight:700;cursor:pointer}
.r20-slot.big{font-family:var(--f-display);font-size:30px;line-height:1;padding:6px 12px}
.r20-slot.opt{border-color:var(--rule);color:var(--muted);background:none;font-size:13px;padding:2px 8px}
.r20-pulse{animation:none;box-shadow:0 0 0 4px color-mix(in srgb,var(--glacier) 22%,transparent)}
.r20-shade{position:fixed;inset:0;z-index:50;background:rgba(13,21,34,.42)}
.r20-sheet{position:fixed;left:0;right:0;bottom:0;z-index:51;display:flex;flex-direction:column;gap:10px;padding:10px 16px 20px;background:var(--paper);color:var(--ink);border-top:6px solid var(--ink)}
.r20-sheet .grab{width:44px;height:4px;margin:0 auto;background:var(--rule)}
.r20-sheet h2{margin:2px 0 0;font-family:var(--f-display);font-weight:700;font-size:34px;line-height:1}
.r20-cal{display:grid;grid-template-columns:repeat(7,1fr);gap:2px;text-align:center}
.r20-cal small{font-size:12px;color:var(--muted);padding:4px 0}
.r20-cal span{display:grid;place-items:center;height:44px;font-size:15px;font-weight:600}
.r20-cal .out,.r20-cal .ret{background:var(--ink);color:var(--paper)}
.r20-cal .ski{background:color-mix(in srgb,var(--glacier) 18%,transparent);color:var(--glacier)}
.r20-cal .past{color:var(--rule)}
.r20-legend{display:flex;gap:14px;font-size:12.5px;color:var(--muted);align-items:center}
.r20-legend i{display:inline-block;width:14px;height:14px;margin-inline-end:5px;vertical-align:-2px}
.r20-sheet .row{display:flex;gap:10px}
.r20-sheet button.pri{flex:1;min-height:52px;border:0;background:var(--accent);color:var(--on-accent);font:inherit;font-size:16px;font-weight:700}
.r20-route{display:flex;gap:8px;flex-wrap:wrap}
.r20-route button{min-height:44px;padding:0 14px;border:1.5px solid var(--rule);background:var(--paper);font:inherit;font-weight:700}
.r20-route button.on{border-color:var(--ink);background:var(--ink);color:var(--paper)}
`;
const cal = () => {
  const days = ['א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ש'];
  let h = days.map(d => `<small>${d}</small>`).join('');
  // January 2027 starts on a Friday (index 5)
  for (let i = 0; i < 5; i++) h += '<span></span>';
  for (let d = 1; d <= 31; d++) h += `<span class="${d === 10 ? 'out' : d === 15 ? 'ret' : d > 10 && d < 15 ? 'ski' : ''}">${d}</span>`;
  return h;
};
const STEPS = [
  // 1: the empty ticket is the form: one big slot for the date, the route already there, the rest optional
  ['t1-ticket', { trip: null }, () => {
    const e = document.getElementById('bpEmpty');
    e.querySelectorAll('.be-code')[0].innerHTML = '<span class="r20-slot">TLV</span>';
    e.querySelectorAll('.be-code')[1].innerHTML = '<span class="r20-slot">TBS</span>';
    const g = e.querySelectorAll('.be-grid i');
    g[0].outerHTML = '<span class="r20-slot opt">+ מספר</span>'; g[1].outerHTML = '<span class="r20-slot opt">+ שעה</span>'; g[2].outerHTML = '<span style="color:var(--muted)">—</span>';
    const add = e.querySelector('.be-add'); add.innerHTML = '<button type="button" class="r20-slot big r20-pulse"><svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><rect x="3" y="5" width="18" height="16"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>מתי טסים?</button>';
    [...document.querySelectorAll('body *')].forEach(el => { if ([...el.childNodes].some(t => t.nodeType === 3 && t.textContent.includes('בלי הרשמה. נשמר רק'))) el.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><rect x="5" y="11" width="14" height="10"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg> רק במכשיר הזה'; });
  }],
  // 2: one calendar, two taps: the outbound and the return; the ski days light up between them
  ['t2-dates', { trip: null }, c => {
    document.body.insertAdjacentHTML('beforeend', `<div class="r20-shade"></div><div class="r20-sheet" role="dialog"><span class="grab"></span>
      <h2>ינואר 2027</h2><div class="r20-cal">${c}</div>
      <div class="r20-legend"><span><i style="background:var(--ink)"></i>טיסות</span><span><i style="background:color-mix(in srgb,var(--glacier) 18%,transparent)"></i>4 ימי סקי</span></div>
      <div class="row"><button type="button" class="pri">שמירה</button></div></div>`);
  }, true],
  // 3: the ticket after two taps: dates, countdown and ski days; the flight number and times wait as slots
  ['t3-done', { trip: { v: 1, out: { date: '2027-01-10', flight: '', from: 'TLV', to: 'TBS', departs: '', arrives: '' }, ret: { date: '2027-01-15', flight: '', departs: '', arrives: '' }, ski: null } }, () => {
    const f = document.querySelector('.bp.is-front');
    if (!f) return;
    f.querySelectorAll('[data-f="flight"],[data-f="departs"],[data-f="arrives"]').forEach((el, i) => { if (!el.textContent.trim() || el.textContent.trim() === '—') el.innerHTML = `<span class="r20-slot opt">${['+ מספר', '+ שעה', '+ שעה'][i] || '+'}</span>`; });
  }],
  // 4: a slot opens just its own sheet: the route as two taps, the most common airports first
  ['t4-route', { trip: { v: 1, out: { date: '2027-01-10', flight: '', from: 'TLV', to: 'TBS', departs: '', arrives: '' }, ret: { date: '2027-01-15', flight: '', departs: '', arrives: '' }, ski: null } }, () => {
    document.body.insertAdjacentHTML('beforeend', `<div class="r20-shade"></div><div class="r20-sheet" role="dialog"><span class="grab"></span>
      <h2>לאן נוחתים?</h2><div class="r20-route"><button class="on">TBS · טביליסי</button><button>KUT · קוטאיסי</button><button>BUS · בטומי</button><button>אחר…</button></div>
      <h2 style="font-size:26px;margin-top:8px">מספר טיסה</h2><input style="height:52px;padding:0 12px;border:1.5px solid var(--rule);font:inherit;font-size:20px;letter-spacing:.06em" dir="ltr" value="6H 897">
      <div class="row"><button type="button" class="pri">שמירה</button></div></div>`);
  }],
];

const b = await launch();
for (const [name, opts, fn, withCal] of STEPS) {
  const { ctx, page } = await open(b, '/#home', { w: 390, h: 844, now: '2026-12-01T10:00:00+04:00', ...opts });
  await page.addStyleTag({ content: CSS });
  await page.evaluate(fn, withCal ? cal() : null);
  await page.waitForTimeout(400);
  await page.screenshot({ path: OUT + name + '.png' });
  console.log(name, page._errors.length ? 'errors: ' + page._errors.slice(0, 2).join(' | ') : '');
  await ctx.close();
}
await b.close();

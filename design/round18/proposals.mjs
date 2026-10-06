// Round 18 proposals on the site: drawn inside the real site (round 16 harness, fake server, made-up names), only with the
// site's own classes and tokens. Nothing here is in the site. The app side is in app.mjs.
// SITE=http://127.0.0.1:4199 OUT=<dir> node design/round18/proposals.mjs [filter]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const OUT = (process.env.OUT || path.join(os.tmpdir(), 'gud-r18')) + '/';
fs.mkdirSync(OUT, { recursive: true });
const ONLY = process.argv[2] ? new RegExp(process.argv[2]) : null;
const J = body => ({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' }, body: JSON.stringify(body) });
const TRIPS = [
  { id: 't-me', owner_id: 'u-me', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', ret_date: '2027-01-15', ret_flight: '6H 892', ret_departs: '01:35:00', entered_by: null },
  { id: 't-me2', owner_id: 'u-dan', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', ret_date: '2027-01-15', ret_flight: '6H 892', ret_departs: '01:35:00', entered_by: null },
  { id: 't-ron', owner_id: 'u-ron', out_date: '2027-01-11', out_flight: 'A9 691', out_from: 'TLV', out_to: 'KUT', out_departs: '07:40:00', ret_date: '2027-01-16', ret_flight: 'A9 692', ret_departs: '12:30:00', entered_by: 'u-me' },
];
const MEETUPS = [{ id: 'm1', station: '158744075b', meet_at: '2027-01-11T05:30:00+00:00', note: null, created_by: 'u-ron' }, { id: 'm2', station: '158744055t', meet_at: '2027-01-11T09:00:00+00:00', note: null, created_by: 'u-dan' }];
const ready = async p => { await p.waitForSelector('#loading', { state: 'hidden' }).catch(() => {}); await p.waitForTimeout(700); };
async function group(p, tab) {
  await p.route(/supabase\.co\/rest\/v1\/trips/, r => r.fulfill(J(TRIPS)));
  await p.route(/supabase\.co\/rest\/v1\/meetups/, r => r.request().method() === 'GET' ? r.fulfill(J(MEETUPS)) : r.fulfill(J([])));
  await p.goto(p.url().split('#')[0] + '#group/g1'); await p.reload(); await ready(p);
  await p.waitForSelector('#grSome:not([hidden])').catch(() => {});
  if (tab) { await p.locator(`#grTabs [data-tab="${tab}"]`).click(); await p.waitForTimeout(500); }
}
async function noGroups(p) {
  await p.route(/supabase\.co\/rest\/v1\/(group_members|groups)/, r => r.fulfill(J([])));
  await p.context().addInitScript(() => localStorage.setItem('gud-acct', JSON.stringify({ uid: 'u-me', anon: false, name: 'נועה ניסיון', providers: ['google'], groups: [] })));
  await p.goto(p.url().split('#')[0] + '#group'); await p.reload(); await ready(p);
}
const PLANE = '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M21 15.5v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V8.5l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-6z"/></svg>';
const CSS = `
.r18-pick{display:flex;flex-direction:column;gap:0;margin:0 0 4px}
.r18-pick button{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:10px;min-height:56px;padding:8px 0;border:0;border-bottom:1px solid var(--rule);background:none;color:var(--ink);font:inherit;text-align:start;cursor:pointer}
.r18-pick b{display:block;font-size:15px}.r18-pick small{display:block;font-size:12.5px;color:var(--muted)}
.r18-pick .go{color:var(--glacier);font-size:13px;font-weight:700;white-space:nowrap}
.r18-pick .oth{color:var(--glacier);font-weight:700}
.r18-sw{display:flex;align-items:center;gap:12px;min-height:56px;padding:6px 0;border-top:1px solid var(--rule);border-bottom:1px solid var(--rule)}
.r18-sw>span{flex:1;display:flex;flex-direction:column}.r18-sw b{font-size:15px}.r18-sw small{font-size:12.5px;color:var(--muted)}
.r18-shade{position:fixed;inset:0;z-index:50;background:rgba(13,21,34,.45)}
.r18-sheet{position:fixed;left:0;right:0;bottom:0;z-index:51;max-height:76vh;overflow:hidden;display:flex;flex-direction:column;gap:8px;padding:10px 16px 20px;background:var(--paper);color:var(--ink);border-top:6px solid var(--ink)}
.r18-sheet h2{margin:4px 0 0;font-family:var(--f-display);font-weight:700;font-size:34px;line-height:1}
.r18-sheet .grab{width:44px;height:4px;margin:0 auto;background:var(--rule)}
.r18-sheet input{height:48px;padding:0 12px;border:1.5px solid var(--rule);background:var(--paper);color:var(--ink);font:inherit;font-size:16px}
.r18-ap{display:flex;align-items:center;gap:14px;min-height:52px;border-bottom:1px solid var(--rule);font-size:15px}
.r18-ap b{width:64px;font-family:var(--f-display);font-weight:700;font-size:28px;line-height:1}
.r18-ap.code{color:var(--glacier);font-weight:700}
.r18-ap.code b{color:var(--glacier)}
.r18-note{margin:6px 0 0;font-size:12.5px;line-height:1.5;color:var(--muted)}
.r18-hint{display:flex;align-items:center;gap:8px;margin:0 0 10px;font-size:13px;color:var(--muted)}
`;
const inject = p => p.addStyleTag({ content: CSS });

const STEPS = [
  // K-2: the return flights as cards of their own, in date order, each with the people on it (as the app)
  ['p-k2-flights', async p => { await group(p); await inject(p); await p.evaluate(() => {
    const cards = [...document.querySelectorAll('#grMain .ac-flight')];
    const ret = [['15.1', '6H 892', 'TBS', 'TLV', '01:35', ['נועה ניסיון', 'דנה בדיקה']], ['16.1', 'A9 692', 'KUT', 'TLV', '12:30', ['רון לדוגמה']]];
    let last = cards[cards.length - 1];
    ret.forEach(([d, n, f, t, dep, ppl], i) => { const c = cards[i].cloneNode(true);
      c.querySelector('.fs').innerHTML = `<span>חזור · <span dir="ltr">${n}</span></span><span dir="ltr">${d}</span>`;
      const bs = c.querySelectorAll('.fb b'); bs[0].textContent = f; bs[1].textContent = n; bs[2].textContent = t;
      const sm = c.querySelectorAll('.fb .mid small'); if (sm[1]) sm[1].textContent = 'המראה ' + dep;
      c.querySelector('.fp').innerHTML = ppl.map(x => `<span${x === 'נועה ניסיון' ? ' class="me"' : ''}>${x}</span>`).join('');
      const fa = c.querySelector('.fa'); if (fa) fa.remove(); if (i === 1) { const a = document.createElement('div'); a.className = 'fa'; a.innerHTML = '<button type="button">אני על אותה טיסה</button>'; c.appendChild(a); }
      last.after(c); last = c; });
  }); }],
  // K-3: fill a flight for a member: first the flights already in the group, then "another flight" opens the trip form
  ['p-k3-pick', async p => { await group(p, 'members'); await inject(p); await p.locator('[data-mmenu="u-tal"]').click(); await p.waitForTimeout(200); await p.evaluate(() => {
    const menu = document.querySelector('.ac-menu'); const box = document.createElement('div'); box.className = 'ac-card ac-form ac-sub';
    box.innerHTML = `<h3>הטיסה של טל דוגמה</h3><p class="ac-lead" style="margin:0">בוחרים טיסה שכבר בקבוצה. החבר רואה אותה, ויכול לשנות.</p>
      <div class="r18-pick"><button type="button"><span><b>הלוך · 10.1 · <span dir="ltr">6H 897</span></b><small><span dir="ltr">TLV › TBS</span> · נועה ניסיון, דנה בדיקה</small></span><span class="go">בחירה</span></button>
      <button type="button"><span><b>הלוך · 11.1 · <span dir="ltr">A9 691</span></b><small><span dir="ltr">TLV › KUT</span> · רון לדוגמה</small></span><span class="go">בחירה</span></button>
      <button type="button"><span><b class="oth">+ טיסה אחרת</b><small>פותח את הטופס המלא, כמו "הטיול שלך"</small></span><span></span></button></div>`;
    menu.after(box); }); }],
  ['p-k3-form', async p => { await p.goto(p.url().split('#')[0] + '#trip'); await ready(p); await p.evaluate(() => {
    document.querySelector('#tripForm h1').textContent = 'הטיסה של טל דוגמה';
    document.querySelector('#tripForm .tf-lead').textContent = 'הטיסה נשמרת בקבוצה, עם סימון שמנהל מילא אותה. החבר רואה אותה ויכול לשנות.'; }); }, { trip: null }],
  // K-3: delete a meetup in two taps; the row opens its meet card; "saved at" as a note, not an error
  ['p-k3-meet', async p => { await group(p, 'meetups'); await inject(p); await p.evaluate(() => {
    const rows = [...document.querySelectorAll('#grMain .ac-meet')];
    rows.forEach(r => { r.style.cursor = 'pointer'; const t = r.querySelector('span[style]'); t.insertAdjacentHTML('beforeend', '<small style="display:block;color:var(--glacier);font-weight:700;font-size:12.5px">לכרטיס המפגש ›</small>'); });
    const a = rows[0]; a.style.outline = '2px solid var(--p-red)'; a.style.outlineOffset = '-2px'; const x = a.querySelector('.ac-x'); x.style.color = 'var(--p-red)';
    a.querySelector('span[style] small').outerHTML = '<small style="display:block;color:var(--p-red);font-weight:700;font-size:13px">בטוח? לחיצה נוספת מוחקת</small>';
    document.querySelector('#grMeta').insertAdjacentHTML('afterend', '<p class="r18-hint"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0"/><path d="M3 3l18 18"/></svg>בלי קליטה. מוצג מה שנשמר בדפדפן ב-09:12</p>'); }); }],
  ['t-k3-offline', async p => { await group(p, 'meetups'); await p.evaluate(() => { const e = document.querySelector('#groupPage [data-err]'); e.textContent = 'אין חיבור לשרת. מוצג מה שנשמר לאחרונה.'; e.hidden = false; }); }],
  // K-5: the airport field opens a sheet with search; a code that is not in the list is accepted, free text is not
  ['p-k5-sheet', async p => { await p.goto(p.url().split('#')[0] + '#trip'); await ready(p); await inject(p); await p.evaluate(() => {
    const L = [['TLV', 'תל אביב'], ['TBS', 'טביליסי'], ['KUT', 'קוטאיסי'], ['BUS', 'בטומי'], ['ETM', 'אילת'], ['IST', 'איסטנבול']];
    document.body.insertAdjacentHTML('beforeend', `<div class="r18-shade"></div><div class="r18-sheet" role="dialog"><span class="grab"></span><h2>מאיפה ממריאים?</h2>
      <label class="tf-fld"><span>חיפוש עיר או קוד שדה</span><input type="text" value=""></label>
      ${L.map(([c, n]) => `<div class="r18-ap"><b dir="ltr">${c}</b><span>${n}</span></div>`).join('')}</div>`); }); }, { trip: null }],
  ['p-k5-code', async p => { await p.goto(p.url().split('#')[0] + '#trip'); await ready(p); await inject(p); await p.evaluate(() => {
    document.body.insertAdjacentHTML('beforeend', `<div class="r18-shade"></div><div class="r18-sheet" role="dialog"><span class="grab"></span><h2>מאיפה ממריאים?</h2>
      <label class="tf-fld"><span>חיפוש עיר או קוד שדה</span><input type="text" value="LCA" dir="ltr"></label>
      <p class="r18-note">אין שדה כזה ברשימה.</p><div class="r18-ap code"><b dir="ltr">LCA</b><span>להשתמש בקוד הזה</span></div>
      <p class="r18-note">קוד של שלוש אותיות, כמו שכתוב בכרטיס הטיסה. בלי שם חופשי, כדי שהכרטיס בדף הבית יישאר קריא.</p></div>`); }); }, { trip: null }],
  // the switch "show my flight in the group" (and the dates, filled from the trip) when creating a group
  ['p-s1-create', async p => { await noGroups(p); await inject(p); await p.evaluate(() => {
    const f = document.querySelector('#grCreate'), me = f.querySelector('[name=me]').closest('.ac-fld');
    me.insertAdjacentHTML('beforebegin', `<div class="ac-grid"><label class="ac-fld"><span>מתאריך</span><input type="date" value="2027-01-10"></label><label class="ac-fld"><span>עד תאריך</span><input type="date" value="2027-01-15"></label></div>`);
    me.insertAdjacentHTML('afterend', `<button type="button" class="ab-row r18-sw" aria-pressed="true" style="width:100%;background:none;border-left:0;border-right:0;font:inherit;color:inherit;text-align:start"><span><b>להציג את הטיסה שלי בקבוצה</b><small style="display:block">הלוך · 10.1 · 16:00 · <bdi>6H 897</bdi></small></span><i class="ab-sw" aria-hidden="true"></i></button>`); }); }],
];

const b = await launch();
for (const [name, fn, o = {}] of STEPS) {
  if (ONLY && !ONLY.test(name)) continue;
  const { ctx, page } = await open(b, '/#home', { signed: true, trip: 'trip' in o ? o.trip : { v: 1, sid: 't-me', out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' }, ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null }, now: '2026-12-01T10:00:00+04:00', ...o });
  await fn(page);
  await page.waitForTimeout(400);
  await page.screenshot({ path: OUT + name + '.png', fullPage: !/k5|meet/.test(name) });
  console.log(name, page._errors.length ? 'errors: ' + page._errors.slice(0, 2).join(' | ') : '');
  await ctx.close();
}
await b.close();

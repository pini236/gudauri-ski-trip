/* Round 19 proposals, drawn inside the real site (shots.mjs injects this and proto.css, then calls R19.<scene>).
   The three weather points, their heights, Tatra 1, Tatra 2, Shino and the stations come from the site's own data
   (site/data/*.json, the same projection and terrain model as site/js/app.js). The weather numbers are sample data:
   every board says so. Nothing here goes into the site. */
(function () {
'use strict';
const lat0 = 42.51, lon0 = 44.495, kx = 111320 * Math.cos(lat0 * Math.PI / 180), ky = 111320;
const P = ([la, lo]) => [(lo - lon0) * kx, -(la - lat0) * ky];
const $ = (s, r) => (r || document).querySelector(s);
const el = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
const NS = 'http://www.w3.org/2000/svg';

// The three points (decision 57, FEATURES section c): the village at the bottom of New Goodaura, the top of the Goodaura
// gondola, the top of the Sadzele chair. Coordinates are the lift ends in site/data/runs-and-lifts.json.
const POINTS = {
  village: { ll: [42.471394, 44.49246], he: 'הכפר', en: 'The village' },
  goodaura: { ll: [42.492379, 44.494273], he: 'Goodaura העליונה', en: 'Upper Goodaura' },
  sadzele: { ll: [42.508985, 44.503209], he: 'Sadzele', en: 'Sadzele' },
};
// Sample weather for a January morning (made up, labelled on every board). dir = where the wind comes FROM, degrees.
const SAMPLE = {
  village: { t: -4, w: 8, g: 15, dir: 290, snow: 5 },
  goodaura: { t: -9, w: 24, g: 40, dir: 300, snow: 9 },
  sadzele: { t: -13, w: 48, g: 70, dir: 300, snow: 12 },
};
const L = {
  he: { m: 'מ׳', kmh: 'קמ״ש', wind: 'רוח', gust: 'משבים', snow: 'שלג חדש', cm: 'ס״מ', none: 'אין מידע', from: 'מ', dirs: ['צפון', 'צפון-מזרח', 'מזרח', 'דרום-מזרח', 'דרום', 'דרום-מערב', 'מערב', 'צפון-מערב'] },
  en: { m: 'm', kmh: 'km/h', wind: 'Wind', gust: 'gusts', snow: 'New snow', cm: 'cm', none: 'No data', from: 'from the ', dirs: ['north', 'northeast', 'east', 'southeast', 'south', 'southwest', 'west', 'northwest'] },
};
let M = null, D = null, lang = 'he';
const T = () => L[lang];
const fmt = n => n.toLocaleString(lang === 'he' ? 'he-IL' : 'en-US');
const deg = t => `<span dir="ltr">${t < 0 ? '−' : ''}${Math.abs(t)}°</span>`;
const dirWord = d => T().dirs[Math.round(((d % 360) + 360) % 360 / 45) % 8];
// the arrow points where the wind blows to (from + 180), north up, as on the map
const arrow = (from, size = 14) => `<svg class="r19-arrow" width="${size}" height="${size}" viewBox="0 0 16 16" aria-hidden="true" style="transform:rotate(${from + 180}deg)"><path d="M8 1.5v12M3.5 6 8 1.5 12.5 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const ICON = {
  cloud: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.5 2A3 3 0 0 0 7 18z"/></svg>',
  locate: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="6.5"/><circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none"/><path d="M12 1.5v3M12 19.5v3M1.5 12h3M19.5 12h3"/></svg>',
  phone: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="6" y="2.5" width="12" height="19" rx="1.5"/><path d="M10.5 18.5h3"/></svg>',
  map: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 4 3 6.5v13L9 17l6 2.5 6-2.5v-13L15 6.5 9 4z"/><path d="M9 4v13M15 6.5v13"/></svg>',
  sat: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/></svg>',
};

async function data() {
  if (M) return;
  const [t, d] = await Promise.all([fetch('data/terrain.json').then(r => r.json()), fetch('data/runs-and-lifts.json').then(r => r.json())]);
  M = GudRelief.load(t); D = d;
}
const altOf = ll => { const [x, y] = P(ll); return Math.round(M.elev(x, y) / 10) * 10; };
const pointAlt = k => altOf(POINTS[k].ll);

// map coordinates (projected metres = the svg's user units) to pixels inside .mapwrap
function toWrap(x, y) {
  const svg = $('#map'), wrap = $('.mapwrap'), m = svg.getScreenCTM(), r = wrap.getBoundingClientRect();
  const p = svg.createSVGPoint(); p.x = x; p.y = y; const q = p.matrixTransform(m);
  return [q.x - r.left, q.y - r.top];
}
async function topView() {
  const b = $('#viewsw [data-view="2d"]');
  if (b && b.getAttribute('aria-pressed') !== 'true') { b.click(); await new Promise(r => setTimeout(r, 900)); }
}
// frame a box of map coordinates in the svg (the site's own zoom only changes the viewBox)
function frame(x0, y0, x1, y1, pad = 0.18) {
  const svg = $('#map'), w = x1 - x0, h = y1 - y0, r = svg.getBoundingClientRect(), ar = r.width / r.height;
  let W = w * (1 + 2 * pad), H = h * (1 + 2 * pad);
  if (W / H > ar) H = W / ar; else W = H * ar;
  svg.setAttribute('viewBox', `${(x0 + x1) / 2 - W / 2} ${(y0 + y1) / 2 - H / 2} ${W} ${H}`);
}
function pathOf(key) { const g = $(`#map g.pg[data-key="${CSS.escape(key)}"]`); return g && g.querySelector('path'); }

/* ---------- 1. the weather layer on the map ---------- */
function chip(on) {
  const f = $('#filters');
  const c = el('button', 'r19-wchip', `${ICON.cloud}<span>${lang === 'he' ? 'מזג אוויר' : 'Weather'}</span>`);
  c.type = 'button'; c.setAttribute('aria-pressed', on ? 'true' : 'false');
  f.parentNode.insertBefore(c, f);
  return c;
}
function pin(k, o) {
  const s = o.nodata ? null : SAMPLE[k], [x, y] = P(POINTS[k].ll), [px, py] = toWrap(x, y), wrap = $('.mapwrap');
  const d = el('div', 'r19-dot'); d.style.left = px + 'px'; d.style.top = py + 'px';
  const t = T(), alt = fmt(pointAlt(k));
  const p = el('div', 'r19-pin' + (s ? '' : ' none'),
    `<span class="pn"><bdi>${POINTS[k][lang]}</bdi> · <bdi>${alt} ${t.m}</bdi></span>` + (o.stale ? '<span class="pn" style="color:var(--ink)">תחזית ישנה</span>' : '') +
    (s ? `<span class="pt"><b>${deg(s.t)}</b><span class="pw"><span>${arrow(s.dir, 12)} ${s.w} ${t.kmh}</span><span>${t.snow} ${s.snow} ${t.cm}</span></span></span>`
       : `<span class="pt"><b>—</b><span class="pw"><span>${t.none}</span></span></span>`));
  wrap.append(d, p);
  // place the card above the point, or below when there is no room, and keep it inside the map; the stem goes to the point
  const W = wrap.clientWidth, H = wrap.clientHeight, w = p.offsetWidth, h = p.offsetHeight, gap = 14;
  const prefer = o.place && o.place[k] || 'above';
  let top = prefer === 'above' ? py - h - gap : py + gap;
  if (top < 8) top = py + gap; if (top + h > H - 8) top = py - h - gap;
  const shift = o.shift && o.shift[k] || 0;
  const left = Math.max(8, Math.min(W - w - 8, px - w / 2 + shift));
  p.style.left = left + 'px'; p.style.top = top + 'px';
  const stem = el('div', 'r19-stem'); stem.style.left = (px - 0.75) + 'px';
  if (top > py) { stem.style.top = py + 'px'; stem.style.height = (top - py) + 'px'; } else { stem.style.top = (top + h) + 'px'; stem.style.height = (py - top - h) + 'px'; }
  wrap.append(stem);
}
function row(k, o, cls) {
  const s = o.nodata ? null : SAMPLE[k], t = T();
  const r = el('div', 'r19-row' + (s ? '' : ' none') + (cls ? ' ' + cls : ''));
  r.innerHTML = `<span class="rn"><b><bdi>${o.names && o.names[k] || POINTS[k][lang]}</bdi></b><span><bdi>${fmt(o.alts && o.alts[k] || pointAlt(k))} ${t.m}</bdi>${o.subs && o.subs[k] ? ' · ' + o.subs[k] : ''}</span></span>` +
    (s ? `<span class="rt">${deg(s.t)}</span><span class="rw"><span>${arrow(s.dir, 13)} ${t.wind} ${s.w} ${t.kmh}, ${t.gust} ${s.g}</span><span>${t.snow} ${s.snow} ${t.cm}</span></span>`
       : `<span class="rt">—</span><span class="rw"><span>${t.none}</span></span>`);
  return r;
}
async function weather(o = {}) {
  lang = o.lang || 'he'; await data(); await topView();
  // the whole ski area, Sadzele to the village, so all three points fit on a phone
  const pts = Object.values(POINTS).map(p => P(p.ll));
  const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
  frame(Math.min(...xs) - 300, Math.min(...ys) - 1000, Math.max(...xs) + 300, Math.max(...ys) + 120, 0.08);
  await new Promise(r => setTimeout(r, 300));
  chip(true);
  pin('sadzele', o); pin('goodaura', { ...o, shift: { goodaura: -40 } }); pin('village', { ...o, shift: { village: 30 } });
  if (o.list !== false) {
    const host = $('#panel'), box = el('section', 'r19-wlist');
    const sub = o.nodata ? 'אין מידע. התחזית האחרונה ישנה מיומיים, או שעוד לא הגיעה.'
      : o.stale ? '<b style="color:var(--ink)">תחזית ישנה</b> · עודכנה לפני 20 שעות, כנראה בלי קליטה'
      : 'תחזית לגובה של כל נקודה · עודכנה לפני 20 דק׳';
    box.innerHTML = `<h2>מזג אוויר לפי גובה</h2><p class="wl-sub">${sub}</p>`;
    ['sadzele', 'goodaura', 'village'].forEach(k => box.append(row(k, o)));
    box.append(el('p', 'r19-note', (o.nodata
      ? 'כשאין נתון לא מנחשים: הנקודות נשארות על המפה עם קו, עד שתחזית חדשה מגיעה.'
      : 'רוח ושלג חדש ב-24 השעות האחרונות. החץ מראה לאן הרוח נושבת.') + ' <span style="white-space:nowrap">נתוני מזג האוויר: <a href=\"https://open-meteo.com/\">Open-Meteo.com</a></span>'));
    host.prepend(box);
  }
}

/* ---------- 2. "today on the mountain" on the home page ---------- */
const STR = {
  he: {
    today: 'היום על ההר', ago: 'עודכן לפני 20 דק׳', day: 'יום סקי 2 מתוך 4 · שלישי, 12.1', risk: 'סגירה בגלל רוח · הערכה',
    lv: ['נמוך', 'בינוני', 'גבוה'], closed: 'סגור · לפי MTA',
    why: 'הערכה לפי התחזית, לא דיווח של אתר הסקי. כשיש דיווח של MTA על הרכבלים, הוא קובע.',
    whyLive: 'Sadzele סגור לפי הדיווח של MTA. ל-Kudebi עוד אין דיווח, ולכן מוצגת ההערכה לפי התחזית.',
    lifts: n => `<span class="num">${n}</span> מתוך 12 רכבלים פתוחים · עודכן לפני 6 דק׳`, go: 'מצב הרכבלים',
    asleep: 'עוד אין דיווח על הרכבלים', snowWord: 'שלג',
  },
  en: {
    today: 'Today on the mountain', ago: 'Updated 20 min ago', day: 'Ski day 2 of 4 · Tue 12 Jan', risk: 'Closure in the wind · estimate',
    lv: ['Low', 'Medium', 'High'], closed: 'Closed · per MTA',
    why: 'An estimate from the forecast, not a report from the ski resort. When MTA reports on the lifts, the report wins.',
    whyLive: 'Sadzele is closed per the MTA report. Kudebi has no report yet, so the estimate is shown.',
    lifts: n => `<span class="num">${n}</span> of 12 lifts open · updated 6 min ago`, go: 'Lift status',
    asleep: 'No lift report yet', snowWord: 'Snow',
  },
};
const meter = n => `<span class="r19-meter" aria-hidden="true">${[0, 1, 2].map(i => `<i class="${i <= n ? 'on' : ''}"></i>`).join('')}</span>`;
async function homeToday(o = {}) {
  lang = o.lang || 'he'; await data();
  const s = STR[lang], t = T();
  const card = el('section', 'r19-today'); card.setAttribute('aria-label', s.today);
  const alt = k => `<div><small><bdi>${POINTS[k][lang]}</bdi></small><small><bdi>${fmt(pointAlt(k))} ${t.m}</bdi></small><b class="tt">${deg(SAMPLE[k].t)}</b><span>${arrow(SAMPLE[k].dir, 12)} ${SAMPLE[k].w} ${t.kmh}</span><span>${s.snowWord} ${SAMPLE[k].snow} ${t.cm}</span></div>`;
  const kud = `<div class="rk"><b dir="ltr">Kudebi</b><span>${s.lv[1]}</span>${meter(1)}</div>`;
  const sad = o.live
    ? `<div class="rk"><b dir="ltr">Sadzele</b><span>${s.closed}</span><span></span></div>`
    : `<div class="rk"><b dir="ltr">Sadzele</b><span>${s.lv[2]}</span>${meter(2)}</div>`;
  card.innerHTML = `<div class="td-head"><b>${s.today}</b><em>${s.ago}</em></div><p class="td-sub">${s.day}</p>` +
    `<div class="r19-alts">${alt('village')}${alt('goodaura')}${alt('sadzele')}</div>` +
    `<div class="r19-risk"><b style="font-size:13px;color:var(--muted)">${s.risk}</b>${sad}${kud}<span class="rk-why">${o.live ? s.whyLive : s.why}</span></div>` +
    (o.live ? `<a class="r19-lifts" href="#map"><span class="ms-dot"></span><span>${s.lifts(10)}</span><span class="go">${s.go}</span></a>`
            : `<a class="r19-lifts" href="#map"><span class="ms-dot" style="background:var(--dash)"></span><span>${s.asleep}</span><span class="go">${s.go}</span></a>`) +
    `<p class="r19-note" style="margin-top:6px">${lang === 'he' ? 'נתוני מזג האוויר' : 'Weather data by'} <a href="https://open-meteo.com/">Open-Meteo.com</a></p>`;
  place(card);
}
async function homeAhead(o = {}) {
  lang = 'he'; await data();
  const card = el('section', 'r19-today'); card.setAttribute('aria-label', 'התחזית לימי הסקי שלך');
  const day = (d, w, t, w2, later) => `<div class="r19-day${later ? ' later' : ''}"><span class="dd"><b>${d}</b><small>${w}</small></span><span class="dw">${t}</span><span class="dt">${w2}</span></div>`;
  card.innerHTML = `<div class="td-head"><b>התחזית לימי הסקי שלך</b><em>עודכן לפני שעה</em></div>` +
    `<p class="td-sub">Goodaura העליונה, ${fmt(pointAlt('goodaura'))} מ׳ · עוד 12 ימים לטיסה</p>` +
    `<div class="r19-days">` +
    day('ב׳, 11.1', 'יום סקי 1', `${arrow(300, 12)} רוח 15 קמ״ש · שלג חדש 4 ס״מ`, deg(-7)) +
    day('ג׳, 12.1', 'יום סקי 2', `${arrow(300, 12)} רוח 32 קמ״ש, משבים 55 · שלג חדש 12 ס״מ`, deg(-10)) +
    day('ד׳, 13.1', 'יום סקי 3', `${arrow(240, 12)} רוח 10 קמ״ש · בלי שלג חדש`, deg(-6)) +
    day('ה׳, 14.1', 'יום סקי 4', 'התחזית ליום הזה תיפתח ב-30.12', '—', true) +
    `</div><p class="r19-note">תחזית עד 15 ימים קדימה; ככל שהיום קרוב, היא מדויקת יותר. הלוח מופיע כשיום הסקי הראשון שלך נכנס לתחזית, ובטיול עצמו הופך ל"היום על ההר". נתוני מזג האוויר: <a href="https://open-meteo.com/">Open-Meteo.com</a></p>`;
  place(card);
}
function place(card) {
  const sb = $('#seasonBoard'); if (sb) sb.hidden = true;
  const after = $('#tripNote') && !$('#tripNote').hidden ? $('#tripNote') : $('#bpHint');
  after.parentNode.insertBefore(card, after.nextSibling);
  if (window.GudSnow && GudSnow.paint) try { GudSnow.paint(); } catch (e) {}
}

/* ---------- 3. conditions at the top and the bottom of a run ---------- */
async function runCond(o = {}) {
  lang = 'he'; await data();
  const kv = $('#panel dl.kv'); if (!kv) return;
  const box = el('section', 'r19-cond');
  box.append(el('h3', '', 'התנאים במסלול'));
  // Tatra 2 (the run on the board): top 2,665 m, bottom 2,170 m, as the panel above says
  const rows = [
    row('goodaura', { names: { goodaura: 'בראש המסלול' }, alts: { goodaura: o.top || 2665 } }),
    row('village', { names: { village: 'בתחתית' }, alts: { village: o.bottom || 2170 } }),
  ];
  // the sample numbers for the top and bottom of this run, between the points
  rows[0].querySelector('.rt').innerHTML = deg(-8); rows[0].querySelector('.rw').innerHTML = `<span>${arrow(300, 13)} רוח 22 קמ״ש, משבים 36</span><span>שלג חדש 8 ס״מ</span>`;
  rows[1].querySelector('.rt').innerHTML = deg(-5); rows[1].querySelector('.rw').innerHTML = `<span>${arrow(290, 13)} רוח 10 קמ״ש, משבים 18</span><span>שלג חדש 6 ס״מ</span>`;
  rows.forEach(r => box.append(r));
  box.append(el('p', 'r19-note', 'תחזית לגובה של ראש המסלול ושל התחתית, עודכנה לפני 20 דק׳. ההר עצמו יכול להיות קר וסוער יותר בקטעים חשופים. נתוני מזג האוויר: <a href="https://open-meteo.com/">Open-Meteo.com</a>'));
  kv.parentNode.insertBefore(box, kv.nextSibling);
}

/* ---------- 4. where am I ---------- */
function locButton(on) {
  const c = $('.ctrls'); const b = el('button', 'r19-loc', ICON.locate);
  b.type = 'button'; b.setAttribute('aria-label', 'איפה אני'); b.setAttribute('aria-pressed', on ? 'true' : 'false');
  c.append(b); return b;
}
function tatra1Point(f = 0.42) { const p = pathOf('Tatra 1'), L0 = p.getTotalLength(), q = p.getPointAtLength(L0 * f); return { p, L0, at: L0 * f, x: q.x, y: q.y }; }
function me(x, y, accM, head) {
  const svg = $('#map'), [px, py] = toWrap(x, y), [ax] = toWrap(x + accM, y), r = Math.max(14, Math.abs(ax - px));
  const d = el('div', 'r19-me', `<span class="acc" style="width:${2 * r}px;height:${2 * r}px"></span>${head ? '<span class="hd"></span>' : ''}<span class="pt"></span>`);
  d.style.left = px + 'px'; d.style.top = py + 'px'; $('.mapwrap').append(d);
}
function where(dotOff, title, sub, action) {
  const w = el('div', 'r19-where', `<span class="wd${dotOff ? ' off' : ''}"></span><span class="wt"><b>${title}</b><span>${sub}</span></span>${action ? `<button type="button" class="wa">${action}</button>` : ''}`);
  w.setAttribute('role', 'status'); $('.mapwrap').append(w);
  const ins = $('#inset'); if (ins) ins.style.bottom = '76px';
  const sc = $('#scale'); if (sc) sc.style.visibility = 'hidden';
}
async function locate(o = {}) {
  lang = 'he'; await data(); await topView();
  const st = o.state || 'on';
  const t1 = tatra1Point();
  if (o.noFrame) {} else if (st === 'ask' || st === 'out' || st === 'denied') {
    const pts = Object.values(POINTS).map(p => P(p.ll)), xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
    frame(Math.min(...xs) - 300, Math.min(...ys) - 300, Math.max(...xs) + 300, Math.max(...ys) + 80, 0.1);
  } else frame(t1.x - 700, t1.y - 900, t1.x + 700, t1.y + 900, 0.05);
  await new Promise(r => setTimeout(r, 300));
  locButton(['on', 'approx', 'out', 'low', 'lift'].includes(st));
  const alt = fmt(Math.round(M.elev(t1.x, t1.y) / 10) * 10);
  if (st === 'on') { me(t1.x, t1.y, 12, true); where(false, 'על Tatra 1 · כחול', `${alt} מ׳ · דיוק של כ-12 מ׳`, ''); }
  if (st === 'approx') { me(t1.x + 120, t1.y - 60, 420, false); where(false, 'המיקום משוער · כ-400 מ׳', 'הטלפון נתן מיקום משוער, ולכן לא מצמידים למסלול', 'מיקום מדויק'); }
  if (st === 'low') { me(t1.x + 50, t1.y + 30, 80, false); where(false, 'המיקום לא מדויק · כ-80 מ׳', 'מחכים לאות טוב יותר כדי לדעת על איזה מסלול', ''); }
  if (st === 'lift') { const g = D.lifts.find(l => l.name === 'Goodaura').g, a = P(g[0]), z = P(g[g.length - 1]), q = [a[0] + (z[0] - a[0]) * 0.55, a[1] + (z[1] - a[1]) * 0.55];
    if (!o.noFrame) { frame(q[0] - 700, q[1] - 900, q[0] + 700, q[1] + 900, 0.05); await new Promise(r => setTimeout(r, 200)); }
    me(q[0], q[1], 10, false); where(false, 'על הרכבל Goodaura', 'בדרך למעלה · דיוק של כ-10 מ׳', ''); }
  if (st === 'unavail') where(true, 'אין מיקום במכשיר הזה', 'הדפדפן או המכשיר לא נותנים מיקום', '');
  if (st === 'out') where(true, 'לא בגודאורי כרגע', 'המיקום יופיע על המפה כשתהיו על ההר', '');
  if (st === 'denied') where(true, 'אין הרשאה למיקום', 'אפשר לאשר בהגדרות הדפדפן, ואז לנסות שוב', 'איך מאשרים');
  if (st === 'ask') {
    const wrap = $('.page-map'); wrap.style.position = 'relative';
    const shade = el('div', 'r19-shade'), sh = el('div', 'r19-sheet');
    sh.setAttribute('role', 'dialog'); sh.setAttribute('aria-label', 'איפה אני על ההר');
    sh.innerHTML = `<h2>איפה אני על ההר</h2><ul>` +
      `<li>${ICON.phone}<span><b>המיקום נשאר בטלפון.</b> הוא לא נשלח לשרת, לא נשמר ולא נמדד.</span></li>` +
      `<li>${ICON.map}<span>פועל רק כשהמפה פתוחה והכפתור דלוק, כדי לחסוך בסוללה.</span></li>` +
      `<li>${ICON.sat}<span>עובד גם בלי קליטה בהר: הטלפון מוצא את עצמו לבד.</span></li>` +
      `</ul><div class="bt"><button type="button" class="pri">להמשיך</button><button type="button">לא עכשיו</button></div>` +
      `<small>אחרי "להמשיך" הדפדפן ישאל אם לאשר. אפשר לכבות בכל רגע בנגיעה בכפתור.</small>`;
    document.body.append(shade, sh);
  }
  if (st === 'down') {
    // the way down from the dot on Tatra 1, only over runs that are in the data: the rest of Tatra 1, then Shino to the
    // bottom of Goodaura and New Goodaura (Tatra 1's and Shino's connections in the site's run panel)
    me(t1.x, t1.y, 12, true);
    const svg = $('#map'), g = document.createElementNS(NS, 'g');
    const sub = []; for (let l = t1.at; l <= t1.L0; l += 20) { const q = t1.p.getPointAtLength(l); sub.push(q.x.toFixed(1) + ' ' + q.y.toFixed(1)); }
    const sh = pathOf('Shino'); const sl = sh.getTotalLength(), a = sh.getPointAtLength(0), z = sh.getPointAtLength(sl);
    const down = M.elev(a.x, a.y) > M.elev(z.x, z.y);
    const sp = []; for (let i = 0; i <= 60; i++) { const q = sh.getPointAtLength(sl * (down ? i : 60 - i) / 60); sp.push(q.x.toFixed(1) + ' ' + q.y.toFixed(1)); }
    const w = svg.viewBox.baseVal.width / svg.getBoundingClientRect().width;
    [['M' + sub.join('L') + 'L' + sp.join('L'), 'var(--casing)', 11], ['M' + sub.join('L') + 'L' + sp.join('L'), 'var(--glacier)', 6]].forEach(([d, c, s]) => {
      const p = document.createElementNS(NS, 'path'); p.setAttribute('d', d); p.setAttribute('fill', 'none'); p.setAttribute('stroke', c); p.setAttribute('stroke-width', s * w);
      p.setAttribute('stroke-linecap', 'round'); p.setAttribute('stroke-linejoin', 'round'); if (c !== 'var(--casing)') p.setAttribute('stroke-dasharray', `${14 * w} ${8 * w}`); g.append(p); });
    svg.append(g);
    const x0 = Math.min(t1.x, z.x, a.x) - 450, x1 = Math.max(t1.x, z.x, a.x) + 250, y0 = Math.min(t1.y, a.y, z.y) - 700, y1 = Math.max(t1.y, a.y, z.y) + 350;
    frame(x0, y0, x1, y1, 0.06); await new Promise(r => setTimeout(r, 200));
    // re-place the dot after the new frame
    document.querySelectorAll('.r19-me').forEach(e => e.remove()); me(t1.x, t1.y, 12, true);
    const host = $('#panel'), box = el('section', 'r19-wlist');
    const rest = ((t1.L0 - t1.at) / 1000).toFixed(1), shl = (sl / 1000).toFixed(1);
    box.innerHTML = `<h2>הדרך למטה לכפר</h2><p class="wl-sub">רק בכחול, מהמקום שלך · כ-${(+rest + +shl).toFixed(1)} ק״מ</p>` +
      `<ol class="r19-steps"><li><b>ממשיכים על Tatra 1 עד הסוף</b><span>כחול · ${rest} ק״מ</span></li>` +
      `<li><b>Shino, הדרך המקשרת</b><span>כחול · ${shl} ק״מ · עד התחנות התחתונות</span></li>` +
      `<li class="lift"><b>מגיעים לתחתית Goodaura ו-New Goodaura</b><span>הכפר, New Gudauri</span></li></ol>` +
      `<p class="r19-note">לפי החיבורים בנתונים בלבד. מסלול שסגור לפי MTA לא נכנס לדרך.</p>`;
    host.prepend(box);
  }
}

window.R19 = { weather, homeToday, homeAhead, runCond, locate, POINTS, pointAlt: k => (M ? pointAlt(k) : null), data };
})();

// Round 12 screens, plugged into the running site (see proto.mjs). Hebrew only: the strings go to
// i18n/strings.json at implementation. Sample data says so on the screen.
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const ICON = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
  lock: '<rect x="5" y="11" width="14" height="9"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  back: '<path d="M9 6l6 6-6 6"/>',
  check: '<path d="M5 12l5 5 9-10"/>',
  x: '<path d="M6 6l12 12M18 6L6 18"/>',
  cloud: '<path d="M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.6 1.5A3.5 3.5 0 0 0 7 18z"/>',
  trash: '<path d="M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13"/>',
  out: '<path d="M14 5h5v14h-5M10 8l-4 4 4 4M6 12h10"/>',
  people: '<circle cx="9" cy="8" r="3.2"/><path d="M3 19c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5"/><circle cx="17" cy="9" r="2.5"/><path d="M16 13.6c2.8.3 5 2.2 5 5.4"/>',
  mail: '<rect x="3" y="5" width="18" height="14"/><path d="M3 6l9 7 9-7"/>',
  bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20h4"/>',
  phone: '<rect x="7" y="3" width="10" height="18"/><path d="M11 18h2"/>',
};
const ic = (n, s = 20) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">${ICON[n]}</svg>`;
const note = (t, i = 'lock') => `<p class="r12-note">${ic(i, 18)}<span>${t}</span></p>`;
const G = '<span class="r12-mark" aria-hidden="true">G</span>';
const A = '<span class="r12-mark" aria-hidden="true">A</span>';
const fld = (label, value = '', ph = '', ltr = false, cls = '') =>
  `<div class="r12-fld"><label>${label}</label><input type="text" value="${value}" placeholder="${ph}"${ltr ? ' dir="ltr"' : ''}${cls ? ` class="${cls}"` : ''}></div>`;
const snow = seed => {
  let r = seed * 9301 % 233280; const rnd = (a, b) => { r = (r * 9301 + 49297) % 233280; return a + (b - a) * r / 233280; };
  let d = 'M0 22 L4 12 '; for (let x = 18; x < 380; x += rnd(26, 44)) d += `Q${x - 9} ${rnd(0, 6)} ${x} ${rnd(8, 14)} `;
  return `<svg class="snowcap" viewBox="0 0 390 26" preserveAspectRatio="none" height="24" width="100%" aria-hidden="true"><path d="${d} L390 12 L390 26 L0 26 Z" fill="#fff" stroke="rgba(19,35,58,.12)"/></svg>`;
};
const CREW = ['יהודה וצלר', 'דובי אלבום', 'שרוליק לפקוביץ׳', 'מוישי בוקצ׳ין', 'יהודה הרש', 'פיני זולברג'];

// --- the home page

// which place for the account on the phone: 'corner' (the first draft), 'duo', 'tag' or 'line'
let ACCT = 'corner';
function acct(me) {
  if (ACCT !== 'corner' && innerWidth < 761) return acctPhone(me);
  const b = me ? `<a class="acct me" href="#account" aria-label="החשבון שלך: פיני זולברג">פ</a>`
                : `<a class="acct" href="#signin" aria-label="כניסה">${ic('people', 18)}<span class="acct-t">כניסה</span></a>`;
  $('.home-top .dn-home')?.insertAdjacentHTML('beforebegin', b.replace('class="acct', 'class="acct-m acct'));
  $('.topbar .dn')?.insertAdjacentHTML('afterend', b);
}
function groupBoard(sub) {
  const next = $('#boardNext'); if (next) next.hidden = true;
  $('.post').insertAdjacentHTML('beforeend', `<a class="board board-group" href="#group"><b>קבוצה</b><span>${sub}</span></a>`);
}
function noCrew() { const c = $('.crew'); if (c) c.hidden = true; }

function guestHome() {
  acct(false);
  // the top bar's count follows the user's own trip: none yet, so it goes
  const tb = $('#tbCount'); if (tb) tb.hidden = true;
  $('.ticket-wrap').innerHTML =
    `<div class="bp-empty"><div class="be-main"><div class="be-strip"><span>הטיול שלך</span><span>עוד לא הוגדר</span></div>
      <div class="be-row"><div><small>מ</small><span class="be-code" dir="ltr">???</span></div><div style="text-align:left"><small>אל</small><span class="be-code" dir="ltr">???</span></div></div>
      <div class="be-grid"><div><small>טיסה</small><i></i></div><div><small>המראה</small><i></i></div><div><small>ימי סקי</small><i></i></div></div></div>
      <div class="be-stub"><small>עוד</small><b>?</b><small>ימים לטיסה</small></div>
      <div class="be-add"><a class="btn-blue" href="#trip">${ic('plus')}הוספת הטיסה שלי</a></div></div>`
    + note('בלי הרשמה. נשמר רק בדפדפן הזה, ואפשר למחוק בכל רגע.')
    + `<a class="season" href="#map">${snow(7)}<em>מצב הרכבלים</em><b>ההר עוד ישן</b><span>עוד אין דיווח על רכבלים. העונה נפתחת בדרך כלל בדצמבר.</span></a>`;
  groupBoard('יצירת קבוצה, או הצטרפות בקוד');
  noCrew();
}

function tripHome() {
  acct(true);
  $$('.bp-strip > span:first-child').forEach((s, i) => { s.textContent = `הטיול שלך · ${s.closest('[data-leg="ret"]') ? 'חזור' : 'הלוך'}`; });
  $$('[data-f="pax"]').forEach(e => { e.textContent = 'אני'; });
  $$('.bp-grid small').forEach(e => { if (e.textContent === 'נוסעים') e.textContent = 'נוסע'; });
  $('.bp-hint')?.insertAdjacentHTML('beforeend', `<a class="r12-edit" href="#trip">${ic('edit', 16)}עריכה</a>`);
  groupBoard('גודאורי 2027 · 6 חברים');
  noCrew();
}

// --- new pages

function page(html, wide = false) {
  $$('main.page').forEach(m => { m.hidden = true; });
  $$('.topbar [aria-current]').forEach(a => a.removeAttribute('aria-current'));
  const m = document.createElement('main');
  m.className = 'page r12-page' + (wide ? ' wide' : '');
  m.innerHTML = html;
  $('.site').appendChild(m);
  scrollTo(0, 0);
  acct(page.me);
}
const head = (t, back = 'בית') => `<div class="r12-head"><h1>${t}</h1><a href="#home">${ic('back')}${back}</a></div>`;

function tripForm() {
  // Pini (2.10.2026, on the app's form): typing dates and numbers by hand is uncomfortable. So: the browser's own
  // date and time pickers, the destination starts as Tbilisi, and the return is the outbound the other way round.
  page.me = false;
  const inp = (label, type, value, extra = '') => `<div class="r12-fld"><label>${label}</label><input type="${type}" value="${value}"${extra}></div>`;
  const sel = (label, opts, pick) => `<div class="r12-fld"><label>${label}</label><select class="r12-sel">${opts.map(o => `<option${o === pick ? ' selected' : ''}>${o}</option>`).join('')}</select></div>`;
  const airports = ['TLV · תל אביב', 'TBS · טביליסי', 'KUT · קוטאיסי'];
  page(head('הטיול שלך', 'ביטול')
    + `<p class="r12-lead">רק תאריך ההלוך חובה. בלי הרשמה: נשמר רק בדפדפן הזה.</p>`
    + `<form style="display:flex;flex-direction:column;gap:12px">`
    + `<div class="r12-sec"><b>הלוך</b><i></i><span>חובה רק התאריך</span></div>`
    + `<div class="r12-grid">${inp('תאריך', 'date', '2027-01-10')}${inp('מספר טיסה', 'text', '6H 897', ' dir="ltr" style="text-transform:uppercase" autocapitalize="characters" placeholder="למשל 6H 897"')}</div>`
    + `<div class="r12-grid">${sel('מ', airports, 'TLV · תל אביב')}${sel('אל', airports, 'TBS · טביליסי')}</div>`
    + `<div class="r12-grid">${inp('המראה', 'time', '16:00')}${inp('נחיתה', 'time', '20:35')}</div>`
    + `<div class="r12-sec" style="margin-top:8px"><b>חזור</b><i></i><span dir="rtl">טביליסי › תל אביב, אוטומטית</span></div>`
    + `<div class="r12-grid">${inp('תאריך', 'date', '2027-01-15')}${inp('מספר טיסה', 'text', '6H 892', ' dir="ltr" style="text-transform:uppercase"')}</div>`
    + `<div class="r12-grid">${inp('המראה', 'time', '01:35')}${inp('נחיתה', 'time', '02:15')}</div>`
    + `<div class="r12-card" style="padding:12px 14px;display:flex;justify-content:space-between;align-items:center"><span><small style="font-size:12px;font-weight:600;color:var(--muted)">ימי סקי מלאים, מחושב מהטיסות</small><br><b style="font-family:var(--f-display);font-size:30px;line-height:1"><span dir="ltr">11–14.1</span> · 4 ימים</b></span><a href="#" style="font-weight:700;min-height:44px;display:inline-flex;align-items:center">שינוי</a></div>`
    + `<div class="r12-btns" style="margin-top:6px"><button type="button" class="r12-btn blue">${ic('check')}שמירה</button><button type="button" class="r12-btn quiet">מחיקת הטיול</button></div></form>`
    + note('תאריך ושעה נבחרים מהלוח של הדפדפן, בלי הקלדה. מתחברים? הטיול עובר לחשבון, ומופיע גם באפליקציה.', 'cloud'));
}

function signIn() {
  page.me = false;
  page(head('כניסה')
    + `<p class="r12-lead">בלי סיסמה, ובלי מיילים מאיתנו. רק השם, ומה שגוגל או אפל מעבירים.</p>`
    + `<div class="r12-card ink">${snow(21)}<h2>למה להתחבר</h2><ul class="r12-list" style="margin-top:10px">
        <li>${ic('people', 18)}<span>ליצור קבוצה ולהזמין את החבר׳ה</span></li>
        <li>${ic('cloud', 18)}<span>הטיול, המועדפים והשיאים, גם באפליקציה ובמחשב אחר</span></li></ul>
        <p style="margin:12px 0 0">כל השאר עובד בלי חשבון: המפה, הטיול שלך, המשחקים, וגם הצטרפות לקבוצה בקוד.</p></div>`
    + `<div class="r12-btns"><button type="button" class="r12-btn google">${G}המשך עם גוגל</button><button type="button" class="r12-btn apple">${A}המשך עם אפל</button><a class="r12-btn quiet" href="#home">לא עכשיו, להמשיך כאורח</a></div>`
    + note('מה שכבר שמור בדפדפן הזה (הטיול שלך והשיאים) עובר לחשבון.', 'cloud')
    + `<a href="privacy" style="font-size:13px;font-weight:600;min-height:44px;display:inline-flex;align-items:center;margin-top:-12px">מדיניות הפרטיות</a>`);
}

function account() {
  page.me = true;
  const row = (mark, t, s, act) => `<div class="r12-row">${mark}<span><b>${t}</b><small>${s}</small></span>${act}</div>`;
  page(head('חשבון')
    + `<div class="r12-card"><div class="r12-who"><span class="r12-av">פ</span><span style="display:flex;flex-direction:column"><b style="font-family:var(--f-display);font-size:32px;line-height:1">פיני זולברג</b><span style="font-size:13px;color:var(--muted)">השם שמופיע בקבוצות · <a href="#">שינוי</a></span></span></div></div>`
    + `<section><h2 style="margin:0;font-family:var(--f-display);font-size:30px">דרכי כניסה</h2><p class="r12-lead" style="margin:2px 0 4px">כמה דרכים לאותו חשבון, כדי שלא ייווצר חשבון שני.</p>`
    + row(`<span class="r12-mark" style="width:28px;height:28px">G</span>`, 'גוגל', 'מחובר', `<span class="r12-ok">${ic('check', 22)}</span>`)
    + row(`<span class="r12-mark" style="width:28px;height:28px;background:#000;color:#fff;border-color:#000">A</span>`, 'אפל', 'לא מחובר', `<button type="button" class="btn ghost">חיבור</button>`)
    + `</section>`
    + `<section><h2 style="margin:0;font-family:var(--f-display);font-size:30px">הקבוצות שלי</h2>`
    + row(`<span class="r12-av" style="width:40px;height:40px;font-size:26px;background:var(--p-blue)">ג</span>`, 'גודאורי 2027', '6 חברים · את/ה מנהל/ת', `<a href="#group" style="font-weight:700">פתיחה</a>`)
    + `</section>`
    + note('הטיול שלך, המועדפים, השיאים והקבוצות מסונכרנים בין האתר לאפליקציה.', 'cloud')
    + `<div class="r12-btns"><button type="button" class="r12-btn ghost">${ic('out')}יציאה מהחשבון</button><a class="r12-btn danger" href="#account/delete">${ic('trash')}מחיקת החשבון</a></div>`);
}

function deleteAccount() {
  // gudauri-ski-trip.vercel.app/account: the page Google Play links to. Works without the app.
  page.me = false;
  page(head('מחיקת חשבון')
    + `<p class="r12-lead">כאן מוחקים את החשבון של האתר ושל האפליקציה, גם בלי האפליקציה. נכנסים עם אותה דרך שבה נרשמת, ומאשרים.</p>`
    + `<div class="r12-card red">${snow(5)}<h2>מה נמחק</h2><ul class="r12-list" style="margin-top:10px">
        <li class="r12-no">${ic('x', 18)}<span style="color:var(--ink)">החשבון, השם ודרכי הכניסה</span></li>
        <li class="r12-no">${ic('x', 18)}<span style="color:var(--ink)">הטיול שלך, המועדפים והשיאים שבשרת</span></li>
        <li class="r12-no">${ic('x', 18)}<span style="color:var(--ink)">החברות בקבוצות, הטיסות שהזנת והמפגשים שיצרת</span></li></ul>
        <p style="margin:12px 0 0">הכל נמחק מיד, בלי תקופת המתנה. מה ששמור בטלפון או בדפדפן נשאר שם, עד שמוחקים את האפליקציה או מנקים את הדפדפן. קבוצה שרק את/ה מנהל/ת בה: המנהלות עוברת קודם לחבר אחר.</p></div>`
    + `<div class="r12-btns"><button type="button" class="r12-btn google">${G}כניסה עם גוגל כדי למחוק</button><button type="button" class="r12-btn apple">${A}כניסה עם אפל כדי למחוק</button></div>`
    + note('אין גישה לחשבון? כתבו ל-<a href="mailto:pinisagent@gmail.com" dir="ltr">pinisagent@gmail.com</a> מאותו מייל, ונמחק תוך 30 יום.', 'mail')
    + `<a href="privacy" style="font-size:13px;font-weight:600;min-height:44px;display:inline-flex;align-items:center;margin-top:-12px">מדיניות הפרטיות</a>`);
}

function join() {
  // gudauri-ski-trip.vercel.app/join/KZBQRM: the invite link, opened on a computer or a phone without the app
  page.me = false;
  page(head('הזמנה לקבוצה')
    + `<div class="r12-card" style="padding-top:22px">${snow(12)}<span class="r12-sample">דוגמה</span>
        <small style="display:block;font-size:12px;font-weight:600;color:var(--muted)">פיני זולברג מזמין אותך ל</small>
        <h2 style="font-size:44px">גודאורי 2027</h2><p style="margin:4px 0 0">6 חברים · 10 עד 15 בינואר</p></div>`
    + fld('איך קוראים לך בקבוצה?', '', 'השם שהחבר׳ה יראו')
    + `<div class="r12-btns" style="margin-top:-8px"><button type="button" class="r12-btn blue">${ic('people')}הצטרפות</button></div>`
    + note('בלי הרשמה. הדפדפן הזה זוכר אותך; אפשר לשמור חשבון עם גוגל או אפל אחר כך.', 'lock')
    + `<div class="r12-card ink"><h2 style="font-size:28px">אני כבר בקבוצה</h2><p>החלפת טלפון או דפדפן? בוחרים את השם שלך, והמנהל מאשר. הכל חוזר, בלי כפיל.</p><a class="r12-btn ghost" href="#">בחירת השם שלי</a></div>`
    + `<p class="r12-lead" style="margin:0">יש לך את האפליקציה? הקישור פותח אותה. אחרי ההתקנה מקלידים את הקוד <b dir="ltr" style="font-family:var(--f-display);font-size:20px;letter-spacing:.1em;color:var(--ink)">KZBQRM</b></p>`);
}

function group() {
  page.me = true;
  const wide = innerWidth >= 900;
  const flight = (cls, strip, d, from, to, fl, dep, people) => `<div class="r12-flight ${cls}"><div class="fs"><span>${strip}</span><span>${d}</span></div>
      <div class="fb"><span><small>מ</small><b dir="ltr">${from}</b></span><span style="text-align:center"><small>טיסה</small><b dir="ltr" style="font-size:22px">${fl}</b><small>${dep}</small></span><span style="text-align:left"><small>אל</small><b dir="ltr">${to}</b></span></div>
      <div class="fp">${people}</div></div>`;
  const ppl = list => list.map(n => `<span${n === 'פיני זולברג' ? ' class="me"' : ''}>${n}</span>`).join('');
  const flights = `<div style="display:flex;flex-direction:column;gap:14px">`
    + flight('', 'הלוך · ישראייר', '10.1', 'TLV', 'TBS', '6H 897', 'המראה 16:00', ppl(CREW.slice(0, 5)))
    + flight('other', 'הלוך · טיסה אחרת', '10.1', 'TLV', 'TBS', 'A9 691', 'המראה 07:40 · דוגמה', ppl([CREW[5]]).replace('<span', '<span title="הוזן על ידי מנהל"'))
    + `<p class="r12-note" style="margin:0">${ic('plus', 18)}<span><a href="#">אני על אותה טיסה</a> · <a href="#">הוספת טיסה אחרת</a></span></p></div>`;
  const meets = `<div style="display:flex;flex-direction:column;gap:10px"><div class="r12-sec"><b>המפגש הבא</b><i></i><span>${ic('bell', 16)} תזכורת רבע שעה לפני</span></div>`
    + `<div class="r12-meet"><span class="mt">09:30</span><span><b>תחתית Goodaura</b>יום ב׳, 11.1 · בוקר ראשון</span></div>`
    + `<div class="r12-meet" style="border-color:var(--gold,#F4B942)"><span class="mt">13:00</span><span><b>התחנה העליונה</b>יום ב׳, 11.1 · צהריים</span></div>`
    + `<a class="r12-btn ghost" href="#meet" style="min-height:46px">${ic('plus')}מפגש חדש</a></div>`;
  const invite = `<div class="r12-invite"><span><small style="display:block;font-size:12px;color:var(--muted);font-weight:600">קוד הזמנה · דוגמה</small><b dir="ltr">KZBQRM</b></span><button type="button" class="btn">שליחה בוואטסאפ</button></div>`;
  page(`<div class="r12-head"><h1>גודאורי 2027</h1><a href="#home">${ic('back')}בית</a></div>`
    + `<p class="r12-lead">6 חברים · 10 עד 15 בינואר · עוד <b style="color:var(--ink)">100</b> ימים</p>`
    + `<nav class="r12-tabs" aria-label="הקבוצה"><a href="#" aria-current="page">טיסות</a><a href="#">מפגשים</a><a href="#">שיאים</a><a href="#">חברים</a></nav>`
    + (wide ? `<div class="r12-cols"><div style="display:flex;flex-direction:column;gap:14px">${flights}</div><div style="display:flex;flex-direction:column;gap:18px">${meets}${invite}</div></div>`
            : flights + invite)
    + note('רק חברי הקבוצה רואים את הדף הזה. הכרטיס והשמות של החבר׳ה עברו לכאן מדף הבית הציבורי.', 'lock'), wide);
}

function acctPhone(me) {
  $('.topbar .dn')?.insertAdjacentHTML('afterend', me ? `<a class="acct me" href="#account">פ</a>` : `<a class="acct" href="#signin">${ic('people', 18)}כניסה</a>`);
  if (ACCT === 'duo') {
    const dn = $('.home-top .dn-home .dn-btn');
    const wrap = document.createElement('span'); wrap.className = 'duo';
    dn.replaceWith(wrap); wrap.appendChild(dn);
    wrap.insertAdjacentHTML('beforeend', '<i></i>' + (me ? `<a class="duo-me" href="#account" aria-label="החשבון שלך"><b>פ</b></a>` : `<a class="duo-me" href="#signin">${ic('people', 18)}כניסה</a>`));
  } else if (ACCT === 'tag') {
    $('.post').insertAdjacentHTML('afterbegin', me
      ? `<a class="skitag" href="#account"><b class="av">פ</b><span><b>פיני זולברג</b><small>החשבון שלך</small></span></a>`
      : `<a class="skitag" href="#signin"><b class="av" style="background:var(--p-blue)">${ic('people', 20)}</b><span><b>כניסה</b><small>הטיול שלך בכל מכשיר</small></span></a>`);
  } else if (ACCT === 'line') {
    $('.home-top .loc').insertAdjacentHTML('afterend', me
      ? `<a class="hello" href="#account"><b>פ</b>שלום, פיני${ic('back', 16).replace('M9 6l6 6-6 6', 'M15 6l-6 6 6 6')}</a>`
      : `<a class="hello" href="#signin">${ic('people', 18)}כניסה${ic('back', 16).replace('M9 6l6 6-6 6', 'M15 6l-6 6 6 6')}</a>`);
  }
}
// the part of the page each account screen shows
function clipTop(h = 330) { window.__clip = { x: 0, y: 0, width: innerWidth, height: h }; }
function clipPost() { const r = $('.post').getBoundingClientRect(); window.__clip = { x: 0, y: Math.max(0, r.top + scrollY - 80), width: innerWidth, height: 430 }; }
function k(variant, me) { ACCT = variant; (me ? tripHome : guestHome)(); variant === 'tag' ? clipPost() : clipTop(variant === 'line' ? 360 : 330); }

// ---------------------------------------------------------------- round 12b (Pini: all three places were bad)
const chev = ic('back', 14).replace('M9 6l6 6-6 6', 'M15 6l-6 6 6 6');
// 1. the pass is the account
function passWho(me) {
  ACCT = 'none';
  $$('[data-f="pax"]').forEach(e => {
    e.innerHTML = me ? `<a class="bp-who" href="#account">פיני זולברג</a>` : `<a class="bp-who guest" href="#signin">אורח</a>`;
  });
}
function p1() { ACCT = 'none'; tripHome(); passWho(true); clipTicket(); }
function p2() {
  ACCT = 'none'; tripHome(); passWho(false);
  clipTicket();
}
function p3() {
  ACCT = 'none'; guestHome();
  const n = $('.ticket-wrap .r12-note span'); if (n) n.innerHTML = 'בלי הרשמה. נשמר רק בדפדפן הזה. יש לך חשבון? <a href="#signin" style="font-weight:700">כניסה</a>';
  clipTop(700);
}
function clipTicket() { const r = $('.ticket-wrap').getBoundingClientRect(); window.__clip = { x: 0, y: Math.max(0, r.top + scrollY - 20), width: innerWidth, height: r.height + 60 }; }
function aboutMe(me) {
  $$('main.page').forEach(m => { m.hidden = true; }); const a = $('#aboutPage'); a.hidden = false; scrollTo(0, 0);
  const card = me
    ? `<div class="ab-pass"><div class="ab-pass-top"><span>SKI PASS · החשבון שלך</span><span>גוגל</span></div><div class="ab-pass-body"><span class="ab-av">פ</span>
        <div><b>פיני זולברג</b><span>גודאורי 2027 · הטיול, השיאים והקבוצה, באתר ובאפליקציה</span>
        <span class="ab-links"><a href="#account">ניהול החשבון${chev}</a></span></div></div></div>`
    : `<div class="ab-pass"><div class="ab-pass-top"><span>SKI PASS · אורח</span><span>בלי חשבון</span></div><div class="ab-pass-body"><span class="ab-av guest">?</span>
        <div><b>אורח</b><span>הכל עובד בלי חשבון. חשבון צריך רק כדי ליצור קבוצה, או לראות את הטיול גם באפליקציה.</span>
        <span class="ab-links"><button type="button" class="g"><span class="r12-mark">G</span>גוגל</button><button type="button" class="a"><span class="r12-mark">A</span>אפל</button></span></div></div></div>`;
  $('.about').insertAdjacentHTML('afterbegin', `<section class="ab-me" aria-label="החשבון"><h2>${me ? 'החשבון' : 'כניסה'}</h2>${card}</section>`);
  clipTop(760);
}
function p4() { aboutMe(true); }
function p5() { aboutMe(false); }
function p6() { ACCT = 'none'; tripHome(); passWho(true); clipTicket(); }
// 2. the lift cabin
function gondola(me) {
  ACCT = 'none'; (me ? tripHome : guestHome)();
  const sky = $('#homeSky');
  const ink = 'var(--ink)';
  sky.insertAdjacentHTML('afterend', `<div class="gondola">
    <svg width="100%" height="200" viewBox="0 0 390 200" preserveAspectRatio="none" style="position:absolute;top:0;left:0" aria-hidden="true">
      <path d="M-10 168 L400 58" stroke="#2A3346" stroke-width="1.6" fill="none"/></svg>
    <a href="${me ? '#account' : '#signin'}" aria-label="${me ? 'החשבון שלך: פיני זולברג' : 'כניסה'}" style="left:272px;top:80px">
      <svg width="56" height="84" viewBox="0 0 56 84" aria-hidden="true">
        <path d="M28 2 L28 22" stroke="#2A3346" stroke-width="2.4"/><circle cx="28" cy="4" r="4" fill="#2A3346"/>
        <path d="M14 22 H42 L46 30 H10 Z" fill="#2A3346"/>
        <rect x="6" y="30" width="44" height="46" fill="${me ? '#D1342B' : '#F4F7FA'}" stroke="#2A3346" stroke-width="2"/>
        <rect x="11" y="36" width="34" height="22" fill="${me ? '#13233A' : '#DCE8F1'}" stroke="#2A3346" stroke-width="1.5"/>
        ${me ? '<text x="28" y="54" text-anchor="middle" font-family="Karantina" font-weight="700" font-size="22" fill="#FFFFFF">פ</text>' : '<path d="M20 52 l4-6 4 4 5-8 5 10z" fill="#9FB4C8"/>'}
        <rect x="6" y="66" width="44" height="3" fill="${me ? '#B12A23' : '#CBD5DF'}"/>
      </svg>${me ? '' : '<span class="cab-tag">כניסה</span>'}</a></div>`);
  $('.page-home').style.position = 'relative';
  clipTop(330);
}
function g1() { gondola(false); }
function g2() { gondola(true); }

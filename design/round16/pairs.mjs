// Round 16: before and after, component by component. Every pair is the same state of the real site, once from main
// (BEFORE, served from a checkout of main) and once from the branch (AFTER), cut around the part that changed.
//   npx http-server <main checkout>/site -p 4198 -c-1 & npx http-server site -p 4199 -c-1 &
//   NET_CACHE=<folder> node design/round16/pairs.mjs            (ONLY=<regex> for some of them)
// Writes design/round16/shots/<id>-before.png and <id>-after.png (twice the pixels), and pairs.json for build.py;
// then `python3 design/round16/build.py webp` turns them into WebP for the canvas.
import { launch, open, MYTRIP } from './lib.mjs';
import fs from 'node:fs';
import path from 'node:path';

const OUT = path.join(path.dirname(new URL(import.meta.url).pathname), 'shots');
const SITES = { before: process.env.BEFORE || 'http://127.0.0.1:4198', after: process.env.AFTER || 'http://127.0.0.1:4199' };
const ONLY = process.env.ONLY ? new RegExp(process.env.ONLY) : null;
fs.mkdirSync(OUT, { recursive: true });

const LIVE = { updated: new Date(Date.now() - 6 * 60000).toISOString(), lifts: { 'Snow Park': { open: true }, 'Pirveli': { open: true }, 'Sadzele': { open: false, reason: 'wind' }, 'Khada': { open: true }, 'Goodaura': { open: true }, 'Soliko': { open: true }, 'Kudebi': { open: false, reason: 'wind' }, 'Tatra': { open: true }, 'New Goodaura': { open: true }, 'Kikilo': { open: true }, 'Shino': { open: true }, 'Zuma': { open: true } }, pistes: {} };
// January, during the trip, and a report from six minutes before (the season board on the home page)
const JAN = '2027-01-12T09:00:00Z', LIVE_JAN = { ...LIVE, updated: '2027-01-12T08:54:00Z' };
const D2 = { 'gud-view': '2d' };
const click = sel => async p => { await p.locator(sel).first().click(); await p.waitForTimeout(800); };
const seq = (...fs) => async p => { for (const f of fs) await f(p); };
const wait = ms => async p => p.waitForTimeout(ms);
const PHONE = { w: 390, h: 844 }, DESK = { w: 1280, h: 800, mobile: false };

// id, group, title, what changed, url, options, action, target (selector, or [x, y, w, h] in the window), padding [top, side, bottom]
const PAIRS = [
  // the snow on the signs (Pini's screenshot)
  ['snow-season', 'snow', 'השלט "ההר עוד ישן" בדף הבית', 'השלג יושב על כל הרוחב של השלט ומכסה את המסגרת, ונשפך מעט מעל הקצוות', '/#home', { ...PHONE, trip: null }, null, '#seasonBoard', [34, 16, 16]],
  ['snow-season-night', 'snow', 'אותו שלט בלילה', 'שלג באור ירח, לא כתם לבן שטוח', '/#home', { ...PHONE, trip: null, theme: 'night' }, null, '#seasonBoard', [34, 16, 16]],
  ['snow-lstat', 'snow', 'שלטי "ההר עוד ישן" במפה', 'שלג נמוך שלא נוגס בשלט שמעליו; השלטים בצבעי המסלולים ולא נעלמים ברקע', '/#map', { ...PHONE, extraStorage: D2, wait: 1500 }, null, '.lstat-post', [30, 16, 16]],
  ['snow-lstat-night', 'snow', 'אותם שלטים בלילה', 'השלט הכהה בהיר יותר מהלוח, והשלג באור ירח', '/#map', { ...PHONE, theme: 'night', extraStorage: D2, wait: 1500 }, null, '.lstat-post', [30, 16, 16]],
  ['snow-games', 'snow', 'שלטי המשחקים', 'השלג נגמר איפה שהחץ מתחיל, ולא תלוי באוויר', '/#games', { ...PHONE }, null, '.games-list', [10, 0, 0, 470]],
  ['snow-games-en', 'snow', 'שלטי המשחקים באנגלית', 'משמאל לימין השלג מתהפך יחד עם החץ', '/#games', { ...PHONE, lang: 'en' }, null, '.games-list', [10, 0, 0, 470]],
  ['snow-signin', 'snow', 'כרטיס "למה להתחבר"', 'השלג מתחיל בפינה ולא 11 פיקסלים פנימה, בלי פס מסגרת שמציץ', '/#signin', { ...PHONE }, null, '#signinPage .ac-card', [34, 16, 12]],
  ['snow-group', 'snow', 'קבוצה: הצטרפות בקוד ויצירה', 'השלג לא מכסה את השורה שמעליו, ושני הכרטיסים לא נוגעים זה בזה', '/#group', { ...PHONE }, null, '#grNone', [12, 16, 12]],
  ['snow-privacy', 'snow', 'דף הפרטיות: "בקצרה"', 'אותו שלג כמו בשאר האתר', '/privacy.html', { ...PHONE }, null, '.pv-short', [34, 16, 12]],
  // the language button from main (3.10.2026, decision 48) inside round 16's top bar and phone headers
  ['lang-home', 'lang', 'דף הבית בטלפון, בגאורגית', 'השעון והכפתורים לא יורדים לשורה שנייה על שמות הפסגות; כשצר, הכפתור בלי הגלובוס, ואחר כך השעון בלי הכיתוב', '/#home', { ...PHONE, trip: MYTRIP, lang: 'ka' }, null, [0, 0, 390, 150], [0, 0, 0]],
  ['lang-head', 'lang', 'כותרת של עמוד בטלפון, בגאורגית', 'בענף הראשי הקישור חזרה נדחק אל מחוץ למסך. כאן הכותרת לא שוברת מילים: הכפתור מוותר על הגלובוס, ואם עדיין לא נכנס, הכותרת עוברת לשורה משלה מתחת לכפתורים', '/#about', { ...PHONE, lang: 'ka' }, null, '#aboutPage .mhead', [8, 0, 8]],
  ['lang-arrow', 'lang', 'החץ חזרה באנגלית', 'בעמודי החשבון, הקבוצה והטיול החץ מצביע אחורה גם משמאל לימין, כמו בשאר העמודים', '/#account', { ...PHONE, lang: 'en', signed: true }, null, '#accountPage .ac-head', [8, 0, 8]],
  // the season board on the home page in season (added after the Android session's note, 3.10.2026)
  ['season-jan', 'season', 'לוח העונה בינואר, בלי דיווח', 'בעונה הוא אומר "אין מידע עדכני", כמו השלטים במפה (S3), ולא "ההר עוד ישן... העונה נפתחת בדרך כלל בדצמבר"', '/#home', { ...PHONE, trip: null, now: JAN }, null, '#seasonBoard', [34, 16, 16]],
  ['season-live', 'season', 'לוח העונה עם דיווח עדכני', 'השלג יורד, הפס למעלה בירוק של מסלול פתוח, ונכתב כמה רכבלים פתוחים ומתי עודכן, כמו בפס של המפה (S1) ובאפליקציה', '/#home', { ...PHONE, trip: null, now: JAN, status: LIVE_JAN }, null, '#seasonBoard', [34, 16, 16]],
  ['season-live-night', 'season', 'אותו לוח בלילה', 'אותו דבר על הנייר הכהה', '/#home', { ...PHONE, trip: null, now: JAN, status: LIVE_JAN, theme: 'night' }, null, '#seasonBoard', [34, 16, 16]],
  // home
  ['home-focus', 'home', 'דף הבית במחשב', 'בלי מסגרת הפוקוס של הדפדפן סביב כל הדף', '/#home', { ...DESK, trip: MYTRIP }, null, [0, 0, 1280, 800], [0, 0, 0]],
  ['home-pass', 'home', 'כרטיס הטיסה', 'הערכים מיושרים לכותרות שלהם ("6H 897" לא נדבק ל-"16:00"), והברקוד של הכרטיס שמאחור לא מציץ', '/#home', { ...PHONE, trip: MYTRIP }, null, '#bpStack', [30, 8, 16]],
  ['home-torn', 'home', 'הספח שנתלש', 'מתחת לספח שנתלש הנייר לא שקוף: הספח של הכרטיס שמאחור לא מתערבב עם "נתראה בשדה"', '/#home', { ...PHONE, trip: MYTRIP, reduce: false }, async p => { await p.locator('.bp.is-front .bp-stub').first().click(); await p.waitForTimeout(700); }, '#bpStack', [30, 8, 16]],
  ['home-empty', 'home', 'כרטיס ריק (אורח בלי טיול)', 'הכפתור לא מכסה חצאי שורות, והספח אומר "עוד ? ימים לטיסה" כמו בסבב 12', '/#home', { ...PHONE, trip: null }, null, '#bpEmpty', [16, 8, 16]],
  ['home-ka', 'home', 'דף הבית בגאורגית', 'השעון לא נופל לתוך שמות הפסגות, וכותרות השלטים לא נוגעות בשורה שמתחתיהן', '/#home', { ...PHONE, trip: MYTRIP, lang: 'ka' }, null, [0, 0, 390, 844], [0, 0, 0]],
  ['home-moon', 'home', 'הירח בלילה', 'הירח לא נוגע בשם הפסגה Bidara', '/#home', { ...PHONE, trip: MYTRIP, theme: 'night' }, null, [0, 0, 390, 300], [0, 0, 0]],
  ['home-who', 'home', 'השם על הכרטיס', 'שטח נגיעה של 44 פיקסלים לשם הנוסע (היה 24)', '/#home', { ...PHONE, trip: MYTRIP, signed: true }, click('.bp.is-front .bp-who'), '#bpStack', [30, 8, 120]],
  // the top bar
  ['top-ru', 'top', 'הסרגל העליון ברוסית, מחשב צר (900)', 'הסרגל מוותר על מילים לפי הסדר עד שהוא נכנס, בלי גלילה הצידה', '/#home', { w: 900, h: 300, mobile: false, lang: 'ru', trip: MYTRIP }, null, [0, 0, 900, 64], [0, 0, 0]],
  ['top-ka', 'top', 'הסרגל העליון בגאורגית, טאבלט (800)', 'השלטים נשברים לשתי שורות, ובסוף שם האתר יורד; כפתור החשבון לא נמעך', '/#home', { w: 800, h: 300, mobile: false, lang: 'ka', trip: MYTRIP, signed: true }, null, [0, 0, 800, 64], [0, 0, 0]],
  ['top-he', 'top', 'הסרגל העליון בעברית', 'גלגל שיניים להגדרות, במקום הסמל שנראה כמו שמש ליד כפתור היום והלילה', '/#home', { w: 1280, h: 300, mobile: false, trip: MYTRIP }, null, [0, 0, 1280, 64], [0, 0, 0]],
  ['top-current', 'top', 'השלט "בית" בעמודי החשבון', 'בדף הטיול, הכניסה, החשבון והקבוצה "בית" כבר לא מסומן כעמוד הנוכחי', '/#account', { w: 1280, h: 300, mobile: false, trip: MYTRIP, signed: true }, null, [0, 0, 1280, 64], [0, 0, 0]],
  // the map
  ['map-3d', 'map', 'מצב הרכבלים בתלת-ממד', 'הפס "11 מתוך 12 רכבלים פתוחים" מופיע גם מעל התלת-ממד (היה מוסתר מאחוריו)', '/#map', { ...DESK, status: LIVE, reduce: false, wait: 4200 }, null, [0, 64, 860, 200], [0, 0, 0]],
  ['map-pill', 'map', 'פס המצב בטלפון', 'בשתי שורות במקום "עודכן לפני 12..." חתוך, ולא נוגע בכפתור הזום', '/#map', { ...PHONE, status: LIVE, extraStorage: D2, wait: 1800 }, null, [0, 96, 390, 120], [0, 0, 0]],
  ['map-prof', 'map', 'פרופיל הגובה', 'הגובה הכי גבוה כתוב בפינה הפנויה, לא מתחת לנקודת ההתחלה', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '.prof', [12, 12, 12]],
  ['map-board', 'map', 'לוח הרכבלים', 'הכותרת "רכבל" באותו גודל כמו שאר הכותרות של הלוח', '/#map', { ...PHONE, status: LIVE, extraStorage: D2, wait: 1800 }, null, '.board-dep', [10, 10, 10, 300]],
  ['map-tags', 'map', 'כפתורי המסלולים והרכבלים בפאנל', 'גובה 40 לעין ו-44 לאצבע (היה 36), והחזרה 44', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '.brief', [12, 12, 12]],
  ['map-vids', 'map', 'כותרות הסרטונים', 'כותרת באנגלית שומרת על הסדר שלה: הנקודות והסוגריים בסוף, לא בהתחלה', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '.vids', [10, 10, 10, 520]],
  // the meeting point
  ['meet-card', 'meet', 'כרטיס המפגש', 'ה-X לא מכסה את "גודאורי 2027", ו"החבר׳ה" ירד מהכותרת (האתר לכל אחד)', '/#meet', { ...PHONE, trip: MYTRIP }, click('[data-pre="am"]'), '.mcard-wrap', [16, 8, 12]],
  ['meet-card-en', 'meet', 'כרטיס המפגש באנגלית', 'ה-X עובר לפינה של סוף השורה, כמו בעברית', '/#meet', { ...PHONE, trip: MYTRIP, lang: 'en' }, click('[data-pre="pm"]'), '.mcard-wrap', [16, 8, 12]],
  ['meet-callout', 'meet', 'הבועה מעל הסיכה', 'שמות הרכבלים שלמים ולא נשברים באמצע ("של Goodaura ו-New Goodaura")', '/#meet', { ...PHONE, trip: MYTRIP }, click('[data-pre="am"]'), '#meetCallout', [16, 24, 30]],
  ['meet-share', 'meet', 'כפתורי השיתוף', 'הכפתור האחרון לבד בשורה תופס את כל הרוחב', '/#meet', { ...PHONE, trip: MYTRIP }, click('[data-pre="am"]'), '#meetShareBox', [12, 12, 12]],
  ['meet-ka', 'meet', 'נקודת המפגש בגאורגית', 'השלטים הקבועים לא נחתכים ("Goodaura 16:3"), וימי השבוע בגאורגית ולא בעברית', '/#meet', { ...PHONE, trip: MYTRIP, lang: 'ka' }, null, '#meetUI', [8, 0, 0, 640]],
  ['meet-routes', 'meet', '"איך מגיעים"', 'הדרך נקראת בכיוון של הדף: מהמקום שלך אל הנקודה הצהובה, עם החצים של השלטים', '/#meet', { ...PHONE, trip: MYTRIP }, click('[data-pre="am"]'), '#meetRoutes', [12, 12, 12]],
  ['meet-routes-en', 'meet', '"איך מגיעים" באנגלית', 'מיושר להתחלת השורה (שמאל), לא לימין', '/#meet', { ...PHONE, trip: MYTRIP, lang: 'en' }, click('[data-pre="am"]'), '#meetRoutes', [12, 12, 12]],
  ['meet-map', 'meet', '"כל ההר"', 'כל הסיכות בתוך המפה: העליונה לא נחתכת והתחתונות לא מתחת לפס "איפה נפגשים?"', '/#meet', { ...PHONE, trip: MYTRIP }, null, '.meet-map', [0, 0, 0]],
  ['meet-night', 'meet', 'כרטיס המפגש בלילה', 'לכרטיס יש נייר וכותרת משלו, כך שרואים איפה הוא נגמר', '/#meet', { ...PHONE, trip: MYTRIP, theme: 'night' }, click('[data-pre="am"]'), '.mcard-wrap', [16, 8, 12]],
  // games, about
  ['games-ru', 'games', 'עמוד המשחקים ברוסית', 'בכרטיס של שלג טרי טקסט כהה על התכלת (היה לבן, ניגודיות 2.8), וסימן ההפעלה עלה כדי לא להתנגש בזמן', '/#games', { ...PHONE, lang: 'ru' }, null, '.games-list', [10, 0, 0, 640]],
  ['about-lang', 'about', 'השורה "שפה · Language"', 'הנקודה בין שתי המילים ולא בקצה', '/#about', { ...PHONE }, null, '#abLang', [8, 8, 8]],
  ['about-night', 'about', 'הגדרות בלילה', 'הפסים של הסקי־פס קריאים (היה לבן על בהיר, ניגודיות 1.15)', '/#about', { ...PHONE, signed: true, theme: 'night' }, null, '.about', [0, 0, 0, 760]],
  ['about-en', 'about', 'המתגים באנגלית', 'משמאל לימין: כבוי משמאל ודולק מימין', '/#about', { ...PHONE, lang: 'en' }, null, '.ab-set', [8, 8, 8]],
  // accounts and the group
  ['acct-me', 'acct', 'החשבון: השם ו"שינוי"', 'השורה שמתחת לשם בלי הצל והמסגרת של טופס המנהל; "שינוי" בגובה 44', '/#account', { ...PHONE, signed: true }, null, '#acMe', [12, 12, 12]],
  ['acct-ways', 'acct', 'החשבון: דרכי כניסה', 'אפל: "בקרוב" במקום "חיבור" שמוביל לכפתור כבוי', '/#account', { ...PHONE, signed: true }, null, '#acWays', [12, 12, 12]],
  ['signin-apple', 'acct', 'כניסה: גוגל ואפל', 'הכפתור של אפל מקווקו עם "בקרוב", בגובה של הכפתור של גוגל, במקום כפתור אפור שנראה שבור', '/#signin', { ...PHONE }, null, '#signinPage [data-providers]', [12, 12, 12]],
  ['group-night', 'acct', 'דף הקבוצה בלילה', 'טקסט כהה על הכחול הבהיר ועל הדיו הבהיר (היה לבן, ניגודיות 2.8 ו-1.15)', '/#group/g1', { ...PHONE, signed: true, trip: MYTRIP, theme: 'night', wait: 1500 }, null, '#grMain', [8, 8, 8, 600]],
  ['group-day', 'acct', 'מספר הטיסה בקבוצה', 'בגופן הטקסט: ב-Karantina ה-7 נראה כמו סימן שאלה ("6H 89?")', '/#group/g1', { ...PHONE, signed: true, trip: MYTRIP, wait: 1500 }, null, '#grMain .ac-flight', [8, 8, 8]],
  // narrow phones
  ['narrow-privacy', 'narrow', 'דף הפרטיות בטלפון קטן (320)', 'הכותרת העליונה נכנסת: שם האתר יורד, השפות והחזרה נשארים', '/privacy.html', { w: 320, h: 600 }, null, [0, 0, 320, 200], [0, 0, 0]],
  ['narrow-privacy-ru', 'narrow', 'אותו דף ברוסית', 'גם הכותרת הארוכה נכנסת', '/privacy.html#ru', { w: 320, h: 600, lang: 'ru' }, null, [0, 0, 320, 260], [0, 0, 0]],
  ['narrow-trip-ka', 'narrow', 'הטיול שלך בגאורגית (320)', 'הכותרת קטנה יותר, "ביטול" נשאר במסך, וההערה לא יוצאת מהמסך', '/#trip', { w: 320, h: 700, lang: 'ka', trip: MYTRIP }, null, [0, 0, 320, 380], [0, 0, 0]],
];

const b = await launch();
const meta = [];
for (const [id, group, title, note, url, opts, act, target, pad] of PAIRS) {
  if (ONLY && !ONLY.test(id)) continue;
  const one = {};
  for (const v of ['before', 'after']) {
    const { ctx, page } = await open(b, url, { dsf: 2, ...opts, site: SITES[v] });
    try {
      if (act) await act(page);
      await page.waitForTimeout(300);
      let clip;
      if (Array.isArray(target)) clip = { x: target[0], y: target[1], width: target[2], height: target[3] };
      else {
        const r = await page.evaluate(([sel, maxH]) => { const el = document.querySelector(sel); if (!el) return null;
          el.scrollIntoView({ block: 'start' }); const q = el.getBoundingClientRect(), sy = document.scrollingElement.scrollTop;
          return { x: q.left, y: q.top + sy, w: q.width, h: Math.min(q.height, maxH || 1e9) }; }, [target, pad[3]]);
        if (!r) throw new Error('no ' + target);
        await page.waitForTimeout(250);
        const vw = page.viewportSize().width;
        const x = Math.max(0, r.x - pad[1]), w = Math.min(vw - x, r.w + 2 * pad[1]);
        clip = { x, y: Math.max(0, r.y - pad[0]), width: w, height: r.h + pad[0] + pad[2] };
      }
      clip = Object.fromEntries(Object.entries(clip).map(([k, v]) => [k, Math.round(v)]));
      await page.screenshot({ path: path.join(OUT, `${id}-${v}.png`), clip, fullPage: true });
      one[v] = [clip.width, clip.height];
    } catch (e) { console.log(id, v, 'FAILED', e.message.split('\n')[0]); }
    await ctx.close();
  }
  meta.push({ id, group, title, note, before: one.before, after: one.after });
  console.log(id, JSON.stringify(one));
}
const mf = path.join(OUT, 'pairs.json');
const old = fs.existsSync(mf) && ONLY ? JSON.parse(fs.readFileSync(mf, 'utf8')) : [];
const merged = [...old.filter(o => !meta.some(m => m.id === o.id)), ...meta].sort((a, b2) => PAIRS.findIndex(p => p[0] === a.id) - PAIRS.findIndex(p => p[0] === b2.id));
fs.writeFileSync(mf, JSON.stringify(merged, null, 1) + '\n');
await b.close();

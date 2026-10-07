import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';

// שגיאות רשת של גופנים חיצוניים לא נחשבות. כל שגיאת קוד באתר נחשבת.
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => {
    if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text());
  });
  return errors;
}

// a trip saved in this browser (round 12: the home page shows your own trip, not the crew's)
const MY_TRIP = { v: 1, out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' },
  ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null };
async function withTrip(page: Page) {
  await page.addInitScript(t => { try { localStorage.setItem('gud-trip', JSON.stringify(t)); } catch (e) {} }, MY_TRIP);
}

async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  // the elevation model and three.js come after the home page (R-11): wait for them where they matter
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
  if (/^#map/.test(new URL(page.url()).hash)) await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
}

test('דף הבית, מפה, בחירת מסלול, סינון וחזרה', async ({ page }, info) => {
  const errors = watchErrors(page);
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('h1.loc')).toContainText('גודאורי');
  await expect(page.locator('#bpEmpty')).toBeVisible();
  await page.screenshot({ path: `test-results/${info.project.name}-home.png` });

  await page.goto('/#map');
  await loaded(page);
  await expect(page.locator('#mapPage')).toBeVisible();
  await expect(page.locator('#panel .index button').first()).toBeVisible();

  // תלת-ממד ומבט על (אם three.js נטען)
  const sw = page.locator('#viewsw');
  if (await sw.isVisible()) {
    await page.click('#viewsw [data-view="2d"]');
    await expect(page.locator('#viewsw [data-view="2d"]')).toHaveAttribute('aria-pressed', 'true');
    await page.screenshot({ path: `test-results/${info.project.name}-map-top.png` });
    await page.click('#viewsw [data-view="3d"]');
    await expect(page.locator('#viewsw [data-view="3d"]')).toHaveAttribute('aria-pressed', 'true');
    await page.screenshot({ path: `test-results/${info.project.name}-map-3d.png` });
  }

  // בחירת מסלול מהרשימה: הפאנל עובר לפרטים
  await page.locator('#panel .index button').first().click();
  await expect(page.locator('#panel h2').first()).toBeVisible();
  await page.screenshot({ path: `test-results/${info.project.name}-piste.png` });

  // סינון
  const chip = page.locator('.fchip').first();
  await chip.click();
  await expect(chip).toHaveAttribute('aria-pressed', 'false');
  await chip.click();

  // חזרה לבית
  await page.goto('/#home');
  await expect(page.locator('#home')).toBeVisible();
  expect(errors).toEqual([]);
});

test('בלי three.js האתר עובר למבט על ומסתיר את בורר התצוגה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.route('**/three-r128.min.js', r => r.abort());
  await page.goto('/#map');
  await loaded(page);
  await expect(page.locator('#viewsw')).toBeHidden();
  await expect(page.locator('#map')).toBeVisible();
  expect(errors).toEqual([]);
});

test('נתונים נטענים מקבצים נפרדים', async ({ page }) => {
  const seen: string[] = [];
  page.on('response', r => { if (/\/data\/.*\.json$/.test(r.url())) seen.push(r.url().split('/').pop()!); });
  await page.goto('/');
  await loaded(page);
  expect(seen.sort()).toEqual(['resorts.json', 'runs-and-lifts.json', 'terrain.json', 'videos-seed.json']);
});

test('תגיות ה-head: שפה, noindex, וקישור לשיתוף', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('lang', 'he');
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex');
  await expect(page.locator('meta[name="viewport"]')).toHaveAttribute('content', /viewport-fit=cover/);
  await expect(page.locator('meta[property="og:title"]')).toHaveCount(1);
  const img = await page.locator('meta[property="og:image"]').getAttribute('content');
  const res = await page.request.get('/' + img!.split('/').pop());
  expect(res.ok()).toBeTruthy();
  const icon = await page.locator('link[rel="icon"]').getAttribute('href');
  expect((await page.request.get('/' + icon)).ok()).toBeTruthy();
});

test('סרטונים מקובץ ההתחלה מוצגים בפרטי המסלול, בלי טופס הוספה', async ({ page }) => {
  await page.goto('/#map');
  await loaded(page);
  const key = await page.locator('#panel .index button').first().getAttribute('data-goto');
  await page.unroute('**/videos-seed.json').catch(() => {});
  await page.route('**/videos-seed.json', r => r.fulfill({ json: [{ piste: key, url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', title: 'סרטון בדיקה', by: 'מחקר', at: 1790000000000 }] }));
  await page.reload();
  await loaded(page);
  await page.goto('/#map');
  await page.locator(`#panel .index button[data-goto="${key}"]`).click();
  await expect(page.locator('#panel .vids a')).toHaveText('סרטון בדיקה');
  await expect(page.locator('#addform')).toHaveCount(0);
  // הסרטון מוצג באתר עצמו: תמונה ממוזערת, ובלחיצה נגן מוטמע
  await expect(page.locator('#panel .vthumb')).toBeVisible();
  await page.locator('#panel .vthumb').click();
  const frame = page.locator('#panel .vframe iframe');
  await expect(frame).toBeVisible();
  await expect(frame).toHaveAttribute('src', /youtube-nocookie\.com\/embed\/dQw4w9WgXcQ/);
});

test('בלי קובץ הסרטונים האתר ממשיך לעבוד', async ({ page }) => {
  const errors = watchErrors(page);
  await page.route('**/videos-seed.json', r => r.abort());
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#bpEmpty')).toBeVisible();
  expect(errors).toEqual([]);
});

test('בתלת-ממד יש תוויות לרכבלים', async ({ page }) => {
  await page.goto('/#map');
  await loaded(page);
  if (!(await page.locator('#viewsw').isVisible())) test.skip();
  await page.click('#viewsw [data-view="3d"]');
  await expect(page.locator('.r3-lbl.lift').first()).toBeAttached();
  const names = await page.locator('.r3-lbl.lift').allInnerTexts();
  expect(names.join(' ')).toContain('Goodaura');
});

test('טיסה במורד המסלול: המפה נגללת לתצוגה, פס מיקום עם עצירה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/#map/run/Tatra%202');
  await loaded(page);
  if (!(await page.locator('#viewsw').isVisible())) test.skip();
  const fly = page.locator('#panel [data-fly]');
  await fly.scrollIntoViewIfNeeded();
  await fly.click();
  const hud = page.locator('#flyHud');
  await expect(hud).toBeVisible();
  await expect(hud.locator('.fh-name')).toHaveText('Tatra 2');
  // המפה בתוך המסך, גם בטלפון שבו הכפתור מתחת למפה
  await expect.poll(async () => page.locator('.mapwrap').evaluate(e => { const r = e.getBoundingClientRect(); return r.top > -10 && r.top < innerHeight / 2; }), { timeout: 15000 }).toBe(true);
  // the flight eases in, and software 3D on a slow machine renders few frames: allow the whole flight
  await expect(page.locator('#pfD')).not.toHaveText('0 מ׳', { timeout: 30000 });
  await hud.locator('.fh-stop').click();
  await expect(hud).toBeHidden();
  await expect(fly).toHaveText('טיסה במורד המסלול');
  expect(errors).toEqual([]);
});

test('קישור להורדת האפליקציה מופיע רק כשהקובץ קיים', async ({ page }) => {
  await page.route('**/downloads/gudauri-2027.apk', r => r.fulfill({ status: 404, body: 'no' }));
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#appBoard')).toBeHidden();
  await expect(page.locator('#groupBoard')).toBeVisible();
});

test('כשהקובץ קיים מוצג שלט הורדה עם גודל והסבר התקנה', async ({ page }) => {
  await page.route('**/downloads/gudauri-2027.apk', r => r.fulfill({ status: 200, contentType: 'application/vnd.android.package-archive', headers: { 'content-length': String(12 * 1048576) }, body: 'x' }));
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#appBoard')).toBeVisible();
  await expect(page.locator('#appBoard')).toHaveAttribute('href', 'downloads/gudauri-2027.apk');
  await page.locator('#appHow summary').click();
  await expect(page.locator('#appHow li')).toHaveCount(3);
});

test('יום ולילה: שלושה מצבים, שעון גודאורי והנוף מתחלף', async ({ page }) => {
  const errors = watchErrors(page);
  await withTrip(page);
  await page.clock.setFixedTime(new Date('2026-12-12T09:00:00Z')); // 13:00 בגודאורי
  await page.goto('/');
  await loaded(page);
  const btn = page.locator('[data-dn]:visible').first();
  await expect(btn).toHaveAttribute('data-mode', 'auto');
  await expect(page.locator('[data-dn-clock]:visible').first()).toHaveText('13:00');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  // התמונות של ההר נטענות
  const img = page.locator('#pano .pl img').first();
  await expect(img).toHaveAttribute('src', /img\/pano\/pano-(wide-)?\w+\.webp/);
  await expect.poll(() => img.evaluate((i: HTMLImageElement) => i.naturalWidth)).toBeGreaterThan(0);
  await btn.click();
  await expect(btn).toHaveAttribute('data-mode', 'day');
  await btn.click();
  await expect(btn).toHaveAttribute('data-mode', 'night');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect(page.locator('#tDaysLbl')).toContainText('לילות');
  // הבחירה נשמרת
  await page.reload();
  await loaded(page);
  await expect(page.locator('[data-dn]:visible').first()).toHaveAttribute('data-mode', 'night');
  await page.locator('[data-dn]:visible').first().click();
  await expect(page.locator('[data-dn]:visible').first()).toHaveAttribute('data-mode', 'auto');
  expect(errors).toEqual([]);
});

test('יום ולילה: במצב אוטומטי בלילה בגודאורי האתר כהה', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-12-12T19:00:00Z')); // 23:00 בגודאורי
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect(page.locator('#skyPhase')).toHaveText('לילה');
});

test('תצוגת מסלול: קישור ישיר, צביעה לפי שיפוע, פרופיל, מעבר בין מסלולים וחזרה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.addInitScript(() => { try { localStorage.setItem('gud-view', '2d'); } catch (e) {} });
  await page.goto('/#map/run/Tatra%202');
  await loaded(page);
  await expect(page.locator('#panel h2')).toHaveText('Tatra 2');
  await expect(page.locator('.run-ref')).toHaveText('7A');
  // המסלול נצבע בצבעי השיפוע
  await expect(page.locator('#map .runpaint line').first()).toBeAttached();
  await expect(page.locator('#map .runpaint')).toHaveClass(/on/);
  // פני השטח סביב המסלול צבועים לפי שיפוע
  await expect(page.locator('#map .runpaint image.rp-ground')).toHaveCount(1);
  // הפרופיל מזיז נקודה על המפה
  const range = page.locator('#profRange');
  await range.evaluate((e: HTMLInputElement) => { e.value = String(Math.round(+e.max / 2)); e.dispatchEvent(new Event('input', { bubbles: true })); });
  await expect(page.locator('#pfD')).not.toHaveText('0 מ׳');
  await expect(page.locator('#map .runmark')).toBeVisible();
  // round 20: what's ahead is three big numbers; no compare line, connections, notes or source block (decision 64)
  await expect(page.locator('.steps > div')).toHaveCount(3);
  await expect(page.locator('.steps .st b')).toHaveText(/^\d+°$/);
  await expect(page.locator('.steps > div').first().locator('b')).toHaveText(/^[\d,]+ מ׳$/);
  await expect(page.locator('#panel')).not.toContainText('חיבורים');
  await expect(page.locator('#panel')).not.toContainText('רמת ודאות');
  await expect(page.locator('#panel .info-pop')).toBeHidden();
  await page.locator('#panel [data-info]').click();
  await expect(page.locator('#panel .info-pop')).toBeVisible();
  await expect(page.locator('#panel [data-info]')).toHaveAttribute('aria-expanded', 'true');
  // המסלול הבא, וכפתור חזרה בדפדפן
  await page.locator('.run-nav button').last().click();
  await expect(page.locator('#panel h2')).not.toHaveText('Tatra 2');
  expect(decodeURIComponent(new URL(page.url()).hash)).toMatch(/^#map\/run\//);
  await page.goBack();
  await expect(page.locator('#panel h2')).toHaveText('Tatra 2');
  await page.locator('#panel [data-back]').click();
  await expect(page.locator('#panel h2.ov')).toBeVisible();
  await expect(page.locator('#map .runpaint line')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('נקודת מפגש: בוחרים תחנה ושעה, כרטיס וקישור לשיתוף (סבב 20: בלי איך מגיעים, שעה אחרת כבלוק, שיתוף בסמלים)', async ({ page }) => {
  const errors = watchErrors(page);
  await withTrip(page);
  await page.goto('/#meet');
  await loaded(page);
  await expect(page.locator('#meetPage')).toBeVisible();
  await expect(page.locator('#meetMap .mm-pin').first()).toBeAttached();
  // נקודה קבועה של הקבוצה
  await page.locator('[data-pre="am"]').click();
  await expect(page.locator('[data-pre="am"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#meetCard [data-f="name"]')).toHaveText('Goodaura');
  await expect(page.locator('#meetCard [data-f="time"]')).toHaveText('09:30');
  await expect(page.locator('#meetRoutes')).toHaveCount(0);
  await expect(page.locator('#meetCallout')).toHaveText('Goodaura');
  await expect(page.locator('[data-times] button')).toHaveCount(7);
  await expect(page.locator('#meetShareBox .ms-ico')).toHaveCount(4);
  await expect(page.locator('#meetCopy')).toHaveAttribute('aria-label', 'העתקת הקישור');
  // another time: the seventh block shows the time once picked
  await page.locator('#meetTime').fill('10:15');
  await expect(page.locator('[data-other]')).toHaveText('10:15');
  await expect(page.locator('[data-other]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#meetCard [data-f="time"]')).toHaveText('10:15');
  // יום ושעה
  // ימי הסקי של הטיול שלך: 11 עד 14 בינואר
  await expect(page.locator('[data-days] button')).toHaveCount(4);
  await expect(page.locator('[data-days] button').first()).toHaveText('ב׳ 11.1');
  await page.locator('[data-day="2027-01-13"]').click();
  await page.locator('[data-time="15:00"]').click();
  await expect(page.locator('#meetCard [data-f="time"]')).toHaveText('15:00');
  await expect(page.locator('#meetCard [data-f="day"]')).toContainText('13.1');
  // הקישור והוואטסאפ מכילים את הבחירה
  await expect(page).toHaveURL(/#meet\/\d+[bt]\/1500\/20270113$/);
  const wa = await page.locator('#meetWa').getAttribute('href');
  expect(decodeURIComponent(wa || '')).toContain('בשעה 15:00');
  // קישור ששותף נפתח על אותה נקודה
  const url = page.url();
  await page.goto('/');
  await page.goto(url);
  await loaded(page);
  await expect(page.locator('#meetCard [data-f="time"]')).toHaveText('15:00');
  await expect(page.locator('#meetCard [data-f="name"]')).toHaveText('Goodaura');
  expect(errors).toEqual([]);
});

test('מצב רכבלים: בלי מידע שלטים מושלגים, עם מידע לוח רכבלים וסיכום', async ({ page }) => {
  const errors = watchErrors(page);
  // out of season the site asks once a day at most (P-D14); this test reloads to see each report, so every load asks
  await page.addInitScript(() => localStorage.removeItem('gud-lstat-asked'));
  await page.goto('/#map');
  await loaded(page);
  await expect(page.locator('#mstat')).toHaveAttribute('data-state', 'none');
  await expect(page.locator('.lstat .lsign').first()).toBeVisible();
  await expect(page.locator('.lstat .snowcap').first()).toBeAttached();
  // נתון עדכני מהפונקציה
  await page.route('**/api/status', r => r.fulfill({ contentType: 'application/json', body: JSON.stringify({
    updated: new Date(Date.now() - 5 * 60000).toISOString(),
    lifts: { Goodaura: { open: true }, Kudebi: { open: false, reason: 'wind' }, Sadzele: { open: true } }, pistes: {} }) }));
  await page.reload();
  await loaded(page);
  await expect(page.locator('#mstat')).toHaveAttribute('data-state', 'live');
  await expect(page.locator('#mstat')).toContainText('רכבלים פתוחים');
  await expect(page.locator('.board-dep .bd-closed')).toContainText('סגור');
  await expect(page.locator('#map .lg.closed')).toHaveCount(1);
  await page.locator('[data-forme]').click();
  await expect(page.locator('[data-forme]')).toHaveAttribute('aria-pressed', 'true');
  // in 3D too (S-19): Kudebi dashed, chairs on the two open lifts, and its runs faded for "only what's open for me"
  if (await page.locator('#viewsw').isVisible()) { // if three.js loaded
    await page.click('#viewsw [data-view="3d"]');
    await expect(page.locator('#viewsw [data-view="3d"]')).toHaveAttribute('aria-pressed', 'true');
    const st = await page.evaluate(() => (Array.from(document.querySelectorAll('canvas')).find((c: any) => c.liftStatus) as any).liftStatus());
    expect(st.closed).toBe(1);
    expect(st.shut).toBeGreaterThan(0);
    expect(st.faded).toBe(st.shut);
    expect(st.chairs).toBe(6);
  }
  // no answer from the function: the last report kept in the browser, while it is fresh (S-29)
  await page.unroute('**/api/status');
  await page.route('**/api/status', r => r.abort());
  await page.reload();
  await loaded(page);
  await expect(page.locator('#mstat')).toHaveAttribute('data-state', 'live');
  await page.unroute('**/api/status');
  // נתון ישן מחצי שעה: לא מציגים אותו
  await page.route('**/api/status', r => r.fulfill({ contentType: 'application/json', body: JSON.stringify({
    updated: new Date(Date.now() - 45 * 60000).toISOString(), lifts: { Goodaura: { open: false } }, pistes: {} }) }));
  await page.reload();
  await loaded(page);
  await expect(page.locator('#mstat')).toHaveAttribute('data-state', 'none');
  await expect(page.locator('#map .lg.closed')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('כרטיס הטיסה: שני כרטיסים שמתחלפים, וספח שנתלש וחוזר', async ({ page }) => {
  const errors = watchErrors(page);
  await withTrip(page);
  await page.goto('/');
  await loaded(page);
  const front = page.locator('.bp.is-front');
  await expect(front).toHaveAttribute('data-leg', 'out');
  await expect(page.locator('.bp[data-leg="ret"] [data-f="flight"]')).toHaveText('6H 892');
  await expect(page.locator('.bp[data-leg="out"] [data-f="fromCode"]')).toHaveText('TLV');
  // הכרטיס שמאחור עולה קדימה
  await page.locator('.bp.is-back .bp-swap').click({ position: { x: 150, y: 20 } });
  await expect(front).toHaveAttribute('data-leg', 'ret');
  await page.waitForTimeout(700);
  // תלישת הספח של הכרטיס הקדמי, והוא חוזר אחרי כמה שניות
  const stub = page.locator('.bp.is-front .bp-stub');
  await stub.click();
  await expect(stub).toHaveClass(/torn1/);
  await expect(stub).not.toHaveClass(/torn/, { timeout: 5000 });
  const wide = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  expect(wide).toBeLessThanOrEqual(1);
  expect(errors).toEqual([]);
});

test('נקודת מפגש: מתחילים בלי בחירה, ומבטלים בכפתור, בשטח ריק, בסיכה וב-X, עם החזרה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/#meet');
  await loaded(page);
  // בהתחלה שום נקודה לא נבחרה: הכרטיס מחכה, ואין דרכים ושיתוף
  await expect(page.locator('#meetEmpty')).toBeVisible();
  await expect(page.locator('#meetCard')).toBeHidden();
  await expect(page.locator('#meetShareBox')).toBeHidden();
  await expect(page.locator('#meetMap .mm-pin.on')).toHaveCount(0);
  await expect(page).toHaveURL(/#meet$/);
  // זום: הכפתור מקרב את המפה
  const vbw = async () => +((await page.locator('#meetMap').getAttribute('viewBox')) || '0 0 0 0').split(' ')[2];
  // the map gets its frame in the frame after the elevation model arrives (R-11): wait for it
  await expect.poll(vbw).toBeGreaterThan(1);
  const before = await vbw();
  await page.locator('#meetZin').click();
  await expect.poll(vbw).toBeLessThan(before * .8);
  // בוחרים, ומבטלים בכפתור, ואז מחזירים
  await page.locator('[data-pre="am"]').click();
  await expect(page.locator('#meetCard')).toBeVisible();
  await page.locator('#meetClear').click();
  await expect(page.locator('#meetEmpty')).toBeVisible();
  await expect(page).toHaveURL(/#meet$/);
  await page.locator('#meetUndo').click();
  await expect(page.locator('#meetCard [data-f="name"]')).toHaveText('Goodaura');
  // ה-X על הכרטיס
  await page.locator('#meetCardX').click();
  await expect(page.locator('#meetCard')).toBeHidden();
  // לחיצה על הסיכה בוחרת, ולחיצה שנייה עליה מבטלת
  await page.locator('[data-pre="am"]').click();
  await page.evaluate(() => scrollTo(0, 0));
  await page.waitForTimeout(800);
  const pin = page.locator('#meetMap .mm-pin.on .mm-dot');
  await pin.click({ force: true });
  await expect(page.locator('#meetCard')).toBeHidden();
  expect(errors).toEqual([]);
});

test('אודות והגדרות: צליל ורטט נשמרים, קרדיטים, ומי בנה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/#home');
  await loaded(page);
  await page.locator('.home-about').click();
  await expect(page.locator('#aboutPage')).toBeVisible();
  await expect(page.locator('main#home')).toBeHidden();
  const snd = page.locator('[data-pref="sound"]');
  await expect(snd).toHaveAttribute('aria-pressed', 'true');
  await snd.click();
  await expect(snd).toHaveAttribute('aria-pressed', 'false');
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('gud-prefs') || '{}').sound)).toBe(false);
  await page.reload();
  await loaded(page);
  await expect(page.locator('[data-pref="sound"]')).toHaveAttribute('aria-pressed', 'false');
  await expect(page.locator('.ab-credits')).toContainText('OpenStreetMap');
  await expect(page.locator('.ab-credits')).toContainText('CC BY 4.0');
  await expect(page.locator('.ab-gh')).toHaveAttribute('href', /github\.com\/pini236/);
  // הקרדיט כבר לא בדף הבית
  await expect(page.locator('p.credits')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('קובץ האימות לקישורים שפותחים את האפליקציה (S-28): נגיש, ועם טביעות האצבע שב-docs/APP-NATIVE.md', async ({ page }) => {
  const r = await page.request.get('/.well-known/assetlinks.json');
  expect(r.status()).toBe(200);
  const links = await r.json();
  const doc = require('fs').readFileSync(require('path').join(__dirname, '..', 'docs', 'APP-NATIVE.md'), 'utf8');
  const byPkg = Object.fromEntries(links.map((l: any) => [l.target.package_name, l.target.sha256_cert_fingerprints]));
  expect(Object.keys(byPkg).sort()).toEqual(['io.github.pini236.skiapp', 'io.github.pini236.skiapp.test']);
  // the store app: the upload key (installs from GitHub) and Google Play's signing key (installs from the store)
  expect(byPkg['io.github.pini236.skiapp']).toHaveLength(2);
  for (const fps of Object.values(byPkg) as string[][]) for (const fp of fps) {
    expect(fp).toMatch(/^([0-9A-F]{2}:){31}[0-9A-F]{2}$/);
    expect(doc).toContain(fp);
  }
  const vercel = JSON.parse(require('fs').readFileSync(require('path').join(__dirname, '..', 'site', 'vercel.json'), 'utf8'));
  const h = vercel.headers.find((x: any) => x.source === '/.well-known/assetlinks.json');
  expect(h.headers).toContainEqual({ key: 'Content-Type', value: 'application/json' });
});

// how often the site asks (P-D14, as in the app): out of season once a day at most, in season on every load
test('מצב רכבלים: מחוץ לעונה שואלים פעם ביום לכל היותר, בעונה כל פעם', async ({ page }) => {
  let asks = 0;
  await page.route('**/api/status', r => { asks++; return r.fulfill({ status: 404, body: '' }); });
  await page.clock.setFixedTime(new Date('2026-10-20T09:00:00Z'));
  await page.goto('/#map');
  await loaded(page);
  await page.reload();
  await loaded(page);
  expect(asks).toBe(1);
  await page.clock.setFixedTime(new Date('2026-10-21T09:30:00Z')); // a day later
  await page.reload();
  await loaded(page);
  expect(asks).toBe(2);
  await page.clock.setFixedTime(new Date('2026-12-21T09:30:00Z')); // in season
  await page.reload();
  await loaded(page);
  await page.reload();
  await loaded(page);
  expect(asks).toBe(4);
});

// "opened since you checked" (S-31): a lift the report says nothing about is not "closed", and drawing the panel
// again keeps the changes (they are against the board before this visit)
test('מצב רכבלים: "נפתח מאז שבדקת" בלי לנחש על רכבל בלי דיווח', async ({ page }) => {
  await page.route('**/api/status', r => r.fulfill({ contentType: 'application/json', body: JSON.stringify({
    updated: new Date(Date.now() - 5 * 60000).toISOString(),
    lifts: { Goodaura: { open: true }, Kudebi: { open: false } }, pistes: {} }) }));
  await page.addInitScript(() => { if (!sessionStorage.getItem('seeded')) { sessionStorage.setItem('seeded', '1');
    localStorage.setItem('gud-lstat', JSON.stringify({ Goodaura: false, Kudebi: false, Sadzele: true })); } });
  await page.goto('/#map');
  await loaded(page);
  const ch = page.locator('.lstat-changes li');
  await expect(ch).toHaveCount(1);
  await expect(ch.first()).toContainText('Goodaura');
  await page.locator('[data-forme]').click(); // draws the panel again
  await expect(page.locator('.lstat-changes li')).toHaveCount(1);
});

// R-11: the elevation model loads after the home page. A link to a run or a meeting point that comes before it is kept,
// and opens when the model arrives
test('קישור ישיר שמגיע לפני מודל הגובה נפתח כשהמודל מגיע (R-11)', async ({ page }) => {
  const errors = watchErrors(page);
  // held back until the page is up (the architect asked for two seconds; here it waits for the page, so it is never racy)
  let release = () => {};
  const held = new Promise<void>(f => { release = f; });
  await page.route('**/data/terrain.json', async r => { await held; await new Promise(f => setTimeout(f, 2000)); await r.continue(); });
  await page.goto('/#map/run/Tatra%202');
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  await expect(page.locator('html[data-model]')).not.toBeAttached(); // the page is up before the model
  release();
  await expect(page.locator('#panel')).toContainText('Tatra 2', { timeout: 20_000 });
  await expect(page.locator('html[data-model="ready"]')).toBeAttached();
  await expect(page.locator('#panel .prof, #panel #profRange').first()).toBeAttached();
  await page.goto('/#meet/12b/0930/20270111');
  await expect(page.locator('#meetPage')).toBeVisible();
  expect(errors).toEqual([]);
});

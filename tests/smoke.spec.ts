import { test, expect, Page } from '@playwright/test';

// שגיאות רשת של גופנים חיצוניים לא נחשבות. כל שגיאת קוד באתר נחשבת.
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => {
    if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text());
  });
  return errors;
}

async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
}

test('דף הבית, מפה, בחירת מסלול, סינון וחזרה', async ({ page }, info) => {
  const errors = watchErrors(page);
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('h1.loc')).toContainText('גודאורי');
  await expect(page.locator('#tDays')).toHaveText(/\d+|0/);
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
  await page.route('**/three.min.js', r => r.abort());
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
  expect(seen.sort()).toEqual(['runs-and-lifts.json', 'terrain.json', 'trip.json', 'videos-seed.json']);
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

test('כרטיס הטיסה והחבר׳ה מוצגים מקובץ הנתונים, בלי עריכה', async ({ page }) => {
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#tFrom')).toHaveText('תל אביב');
  await expect(page.locator('#tTo')).toHaveText('טביליסי');
  await expect(page.locator('#tFlight')).toHaveText('6H 897');
  await expect(page.locator('#tDate')).toHaveText('10.1.2027');
  await expect(page.locator('#tDeparts')).toHaveText('16:00');
  await expect(page.locator('#tArrives')).toHaveText('20:35');
  await expect(page.locator('.bp[data-leg="ret"] [data-f="date"]')).toHaveText('15.1.2027');
  await expect(page.locator('#crewList li')).toHaveCount(6);
  await expect(page.locator('#crewList')).toContainText('פיני זולברג');
  await expect(page.locator('#tEdit, #tForm, #addform')).toHaveCount(0);
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

test('בלי קבצי טיסה וסרטונים האתר ממשיך לעבוד', async ({ page }) => {
  const errors = watchErrors(page);
  await page.route('**/trip.json', r => r.abort());
  await page.route('**/videos-seed.json', r => r.abort());
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#tFrom')).toHaveText('מוצא');
  await expect(page.locator('.bp[data-leg="ret"]')).toBeHidden();
  await expect(page.locator('.crew')).toBeHidden();
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

test('קישור להורדת האפליקציה מופיע רק כשהקובץ קיים', async ({ page }) => {
  await page.route('**/downloads/gudauri-2027.apk', r => r.fulfill({ status: 404, body: 'no' }));
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#appBoard')).toBeHidden();
  await expect(page.locator('#boardNext')).toBeVisible();
});

test('כשהקובץ קיים מוצג שלט הורדה עם גודל והסבר התקנה', async ({ page }) => {
  await page.route('**/downloads/gudauri-2027.apk', r => r.fulfill({ status: 200, contentType: 'application/vnd.android.package-archive', headers: { 'content-length': String(12 * 1048576) }, body: 'x' }));
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#appBoard')).toBeVisible();
  await expect(page.locator('#appBoard')).toHaveAttribute('href', 'downloads/gudauri-2027.apk');
  await expect(page.locator('#boardNext')).toBeHidden();
  await page.locator('#appHow summary').click();
  await expect(page.locator('#appHow li')).toHaveCount(3);
});

test('יום ולילה: שלושה מצבים, שעון גודאורי והנוף מתחלף', async ({ page }) => {
  const errors = watchErrors(page);
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
  await expect(page.locator('.brief li')).toHaveCount(3);
  await expect(page.locator('.run-cmp .tag')).toHaveCount(2);
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

test('נקודת מפגש: בוחרים תחנה ושעה, כרטיס, איך מגיעים וקישור לשיתוף', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/#meet');
  await loaded(page);
  await expect(page.locator('#meetPage')).toBeVisible();
  await expect(page.locator('#meetMap .mm-pin').first()).toBeAttached();
  // נקודה קבועה של הקבוצה
  await page.locator('[data-pre="am"]').click();
  await expect(page.locator('[data-pre="am"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#meetCard [data-f="name"]')).toHaveText('Goodaura');
  await expect(page.locator('#meetCard [data-f="time"]')).toHaveText('09:30');
  await expect(page.locator('#meetRoutes li').first()).toBeVisible();
  // יום ושעה
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

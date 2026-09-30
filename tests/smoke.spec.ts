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
  await expect(page.locator('#tBack')).toContainText('15.1.2027');
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

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
  expect(seen.sort()).toEqual(['runs-and-lifts.json', 'terrain.json']);
});

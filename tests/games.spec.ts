import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';

// אזור המשחקים (החלטה 17): עמוד המשחקים באתר, וחמשת המשחקים שבאתר, כל אחד בעמוד משלו.
const GAMES = [
  { slug: 'descent', title: 'הירידה של החבר׳ה' },
  { slug: 'school', title: 'בית הספר לסקי' },
  { slug: 'fresh-snow', title: 'שלג טרי' },
  { slug: 'merge', title: 'איחוד כדורי שלג' },
  { slug: 'snowball', title: 'קרב כדורי שלג' },
];

function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => {
    if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text());
  });
  return errors;
}

test('עמוד המשחקים: שלט בבית, כרטיס לכל משחק, וחזרה מהמשחק', async ({ page }, info) => {
  const errors = watchErrors(page);
  await page.goto('/');
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  await expect(page.locator('.board-games')).toBeVisible();
  await page.click('.board-games');
  await expect(page.locator('#gamesPage')).toBeVisible();
  await expect(page.locator('#home')).toBeHidden();
  const cards = page.locator('#gamesPage .game-card');
  await expect(cards).toHaveCount(GAMES.length);
  for (const g of GAMES) await expect(page.locator(`#gamesPage a[href="games/${g.slug}/"]`)).toContainText(g.title);
  await page.screenshot({ path: `test-results/${info.project.name}-games.png`, fullPage: true });

  // נכנסים למשחק אחד, וחוזרים לעמוד המשחקים בקישור שבתוכו
  await page.click('#gamesPage a[href="games/school/"]');
  await expect(page).toHaveTitle('בית הספר לסקי');
  await page.click('.back-site');
  await expect(page.locator('#gamesPage')).toBeVisible({ timeout: 20_000 });
  expect(errors).toEqual([]);
});

for (const g of GAMES) {
  test(`המשחק נטען בלי שגיאות: ${g.title}`, async ({ page }, info) => {
    const errors = watchErrors(page);
    await page.goto(`/games/${g.slug}/`);
    await expect(page).toHaveTitle(g.title);
    const back = page.locator('.back-site');
    await expect(back).toBeVisible();
    await expect(back).toHaveAttribute('href', '../../#games');
    // בלי גלילה הצידה
    const wide = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
    expect(wide).toBeLessThanOrEqual(1);
    await page.waitForTimeout(400);
    await page.screenshot({ path: `test-results/${info.project.name}-game-${g.slug}.png` });
    expect(errors).toEqual([]);
  });
}

// בלי שמות החבר׳ה במשחקים באתר (החלטה 55, כמו באפליקציה): בוחרים מעיל לפי צבע
test('הירידה וקרב כדורי שלג: מעילים לפי צבע, בלי שמות', async ({ page }) => {
  const errors = watchErrors(page);
  for (const slug of ['descent', 'snowball']) {
    await page.goto(`/games/${slug}/`);
    const coats = page.locator('#friends button');
    await expect(coats).toHaveCount(6);
    await expect(coats.first()).toContainText('אדום');
    await expect(page.locator('#friends')).toHaveAttribute('aria-label', 'בחירת מעיל');
    for (const name of ['פיני', 'דובי', 'שרוליק', 'מוישי', 'יהודה']) await expect(page.locator('body')).not.toContainText(name);
  }
  expect(errors).toEqual([]);
});

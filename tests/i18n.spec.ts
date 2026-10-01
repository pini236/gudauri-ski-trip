import { test, expect, Page } from '@playwright/test';

// The four interface languages (decision 32, i18n/). The rest of the suite runs in Hebrew (locale he-IL in the config).
const KEY_LIKE = /\b(?:meta|nav|common|home|ticket|daynight|about|map|run|lift|status|meet|games|game)\.[a-z0-9_]+(?:\.[a-z0-9_]+)?\b/;
const HEBREW = /[֐-׿]/;

async function visibleText(page: Page) {
  // the crew's names and run data are content, not interface; leave them out
  return page.evaluate(() => {
    const clone = document.body.cloneNode(true) as HTMLElement;
    clone.querySelectorAll('#crewList, [hidden], script, style').forEach(e => e.remove());
    return clone.innerText;
  });
}

for (const lang of ['en', 'ru', 'ka']) {
  test(`home in ${lang}: left to right, no Hebrew and no raw keys`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', e => errors.push(String(e)));
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    await page.goto(`/?lang=${lang}#home`);
    await expect(page.locator('html')).toHaveAttribute('lang', lang);
    await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
    await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
    const text = await visibleText(page);
    expect(text).not.toMatch(KEY_LIKE);
    expect(text).not.toMatch(HEBREW);
    // no sideways scroll on the phone
    const over = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(over).toBeLessThanOrEqual(1);
    expect(errors.filter(e => !/fonts\.g|ERR_|net::/.test(e))).toEqual([]);
  });
}

test('map page in English', async ({ page }) => {
  await page.goto('/?lang=en#map');
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  const text = await visibleText(page);
  expect(text).not.toMatch(KEY_LIKE);
  expect(text).not.toMatch(HEBREW);
});

test('the choice is remembered, and ?lang=auto forgets it', async ({ page }) => {
  await page.goto('/?lang=en#home');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await page.goto('/#home');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await page.goto('/?lang=auto#home');
  await expect(page.locator('html')).toHaveAttribute('lang', 'he');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
});

test.describe('a Russian browser outside Israel', () => {
  test.use({ locale: 'ru-RU', timezoneId: 'Europe/Moscow' });
  test('gets English until Russian passes the native speaker review', async ({ page }) => {
    await page.goto('/#home');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });
});

test.describe('an English browser in Israel', () => {
  test.use({ locale: 'en-US', timezoneId: 'Asia/Jerusalem' });
  test('gets Hebrew', async ({ page }) => {
    await page.goto('/#home');
    await expect(page.locator('html')).toHaveAttribute('lang', 'he');
  });
});

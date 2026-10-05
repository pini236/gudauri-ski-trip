import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';

// The four interface languages (decision 32, i18n/). The rest of the suite runs in Hebrew (locale he-IL in the config).
const KEY_LIKE = /\b(?:meta|nav|common|home|ticket|daynight|about|map|run|lift|status|meet|games|game)\.[a-z0-9_]+(?:\.[a-z0-9_]+)?\b/;
const HEBREW = /[֐-׿]/;

async function visibleText(page: Page) {
  // the crew's names and run data are content, not interface; leave them out
  return page.evaluate(() => {
    const clone = document.body.cloneNode(true) as HTMLElement;
    clone.querySelectorAll('#crewList, [hidden], [translate="no"], dialog, script, style').forEach(e => e.remove());
    return clone.innerText;
  });
}

for (const lang of ['en', 'ru', 'ka']) {
  test(`home in ${lang}: left to right, no Hebrew and no raw keys`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', e => errors.push(String(e)));
    listenCsp(page); page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
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
    expect(errors.filter(e => !/fonts\.g|ERR_|net::|Failed to load resource/.test(e))).toEqual([]);
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

test.describe('a German browser', () => {
  test.use({ locale: 'de-DE', timezoneId: 'Europe/Berlin' });
  test('gets English', async ({ page }) => {
    await page.goto('/#home');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });
});

test.describe('an English browser in Israel', () => {
  test.use({ locale: 'en-US', timezoneId: 'Asia/Jerusalem' });
  test('gets English (only a Hebrew browser gets Hebrew)', async ({ page }) => {
    await page.goto('/#home');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });
});

test.describe('a Russian browser', () => {
  test.use({ locale: 'ru-RU', timezoneId: 'Europe/Moscow' });
  test('gets English too', async ({ page }) => {
    await page.goto('/#home');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await page.goto('/privacy');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });
});

test.describe('an old Hebrew browser (iw)', () => {
  test.use({ locale: 'iw-IL' });
  test('gets Hebrew', async ({ page }) => {
    await page.goto('/privacy');
    await expect(page.locator('html')).toHaveAttribute('lang', 'he');
  });
});

test('the language button sits after the clock, next to the day and night button, in the top bar and on the phone home page', async ({ page }) => {
  await page.goto('/#home');
  await expect(page.locator('.lang-btn:visible').first()).toBeVisible();
  // the round buttons side by side, not button, clock, button (Pini, on the canvas, 3.10.2026)
  const order = await page.evaluate(() => ['.topbar .dn', '.home-top .dn-home'].map(s => [...document.querySelector(s)!.children].map(e => e.classList[0])));
  expect(order).toEqual([['dn-clock', 'lang-btn', 'dn-btn'], ['dn-clock', 'lang-btn', 'dn-btn']]);
});

test('the language button in the top bar opens the list on every page, and choosing switches', async ({ page }) => {
  for (const hash of ['#home', '#map', '#about', '#signin']) {
    await page.goto('/' + hash);
    const btn = page.locator('.lang-btn:visible').first();
    await expect(btn).toBeVisible();
    await expect(btn).toContainText('HE');
    const box = await btn.boundingBox();
    expect(box!.height).toBeGreaterThanOrEqual(44);
  }
  await page.goto('/#home');
  await page.locator('.lang-btn:visible').first().click();
  await expect(page.locator('#langSheet')).toBeVisible();
  await page.locator('#langSheet label[lang="ru"]').click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
  await expect(page.locator('.lang-btn:visible').first()).toContainText('RU');
  await page.goto('/privacy');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
});

test('the language row in settings opens the list, and choosing switches and remembers', async ({ page }) => {
  await page.goto('/#about');
  await expect(page.locator('#abLangVal')).toHaveText('עברית');
  await page.locator('#abLang').click();
  await expect(page.locator('#langSheet')).toBeVisible();
  await page.locator('#langSheet label[lang="en"]').click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('#abLangVal')).toHaveText('English');
  await expect(page.locator('#abLangSub')).toHaveText('Chosen here, saved on this phone');
  await page.locator('#abLang').click();
  await page.locator('#langAuto').click();
  await expect(page.locator('html')).toHaveAttribute('lang', 'he');
  await expect(page.locator('#abLangSub')).toHaveText('נבחרה לפי הדפדפן');
});

// the research notes of a run are written in Hebrew in the data file and translated in the strings file (research.*)
for (const [lang, words] of [['en', 'connecting route'], ['ru', 'соединительная дорога'], ['ka', 'დამაკავშირებელი გზა']] as const) {
  test(`run research notes in ${lang}`, async ({ page }) => {
    await page.goto(`/?lang=${lang}#map/run/Shino`);
    await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
    const notes = page.locator('#panel p.hint').filter({ hasText: words });
    await expect(notes.first()).toBeVisible({ timeout: 15_000 });
    const panel = await page.locator('#panel').innerText();
    expect(panel).not.toMatch(HEBREW);
  });
}

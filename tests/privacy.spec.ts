import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { listenCsp } from './csp-listen';

// The privacy page is generated from docs/PRIVACY.md (tools/build-privacy.py). It must stay in step with it.
test('דף הפרטיות מעודכן מול docs/PRIVACY.md', () => {
  const md = readFileSync('docs/PRIVACY.md', 'utf8');
  const page = readFileSync('site/privacy.html', 'utf8');
  const heads = [...md.matchAll(/^### (.+)$/gm)].map(m => m[1].trim());
  expect(heads.length).toBeGreaterThan(15);
  for (const h of heads) expect(page, `חסר: ${h}. להריץ python3 tools/build-privacy.py`).toContain(`<h2>${h}</h2>`);
  for (const d of md.match(/בתוקף מ-[\d.]+|Effective [A-Za-z]+ \d+, \d{4}/g) || []) expect(page).toContain(d);
  // four languages (decision 46), each with all its sections
  for (const head of ['## עברית', '## English', '## Русский', '## ქართული']) expect(md).toContain(head);
  expect(heads.length % 4).toBe(0);
});

test('דף הפרטיות: עברית ואנגלית, בלי שגיאות ובלי גלילה הצידה', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  listenCsp(page); page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  await page.goto('/privacy.html');
  await expect(page.locator('#he h1')).toHaveText('מדיניות פרטיות');
  await expect(page.locator('#en')).toBeHidden();
  await expect(page.locator('#he .pv-sign')).toContainText('לא רשמית');
  await expect(page.locator('#he a[href^="mailto:"]').first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.getByRole('button', { name: 'English' }).click();
  await expect(page.locator('#en h1')).toHaveText('Privacy Policy');
  await expect(page.locator('#he')).toBeHidden();
  expect(await page.evaluate(() => document.documentElement.dir)).toBe('ltr');
  expect(page.url()).toContain('#en');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: `test-results/privacy-${test.info().project.name}.png`, fullPage: false });
  await page.goto('/privacy.html#en');
  await expect(page.locator('#en')).toBeVisible();
  await expect(page.locator('#he')).toBeHidden();
  expect(errors).toEqual([]);
});

test('קישור לדף הפרטיות מדף הבית ומעמוד האודות', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.home-privacy')).toHaveAttribute('href', 'privacy');
  await expect(page.locator('.ab-privacy a')).toHaveAttribute('href', 'privacy');
});

test('דף הפרטיות ברוסית ובגאורגית, ולפי השפה שנבחרה באתר', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('/privacy.html#ru');
  await expect(page.locator('#ru h1')).toHaveText('Политика конфиденциальности');
  await expect(page.locator('#he')).toBeHidden();
  expect(await page.evaluate(() => document.documentElement.lang)).toBe('ru');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.getByRole('button', { name: 'ქართული' }).click();
  await expect(page.locator('#ka h1')).toHaveText('კონფიდენციალურობის პოლიტიკა');
  expect(page.url()).toContain('#ka');
  // the language chosen on the site, with no hash
  await page.evaluate(() => localStorage.setItem('gud-lang', 'ru'));
  await page.goto('/privacy.html');
  await expect(page.locator('#ru')).toBeVisible();
  await expect(page.locator('[data-back="ru"]')).toBeVisible();
  expect(errors).toEqual([]);
});

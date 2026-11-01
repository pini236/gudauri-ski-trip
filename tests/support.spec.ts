import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { listenCsp } from './csp-listen';

// The support page (/support, round 33) is generated from i18n/strings.json (support.*) by tools/build-support.py.
const S = JSON.parse(readFileSync('i18n/strings.json', 'utf8')).strings;
const LANGS = ['he', 'en', 'ru', 'ka'] as const;
const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

test('דף התמיכה מעודכן מול i18n/strings.json', () => {
  const page = readFileSync('site/support.html', 'utf8');
  const keys = Object.keys(S).filter(k => k.startsWith('support.') && k !== 'support.meta_desc');
  expect(keys.length).toBeGreaterThanOrEqual(21);
  for (const k of keys) for (const l of LANGS) {
    const text = esc(S[k][l]).replace('{account}', '<a href="/account" dir="ltr">gudauri-ski-trip.vercel.app/account</a>');
    expect(page, `${k} (${l}) חסר. להריץ python3 tools/build-support.py`).toContain(text);
  }
  // in the sitemap, with a clean address, and no script written inside the page (the content security policy)
  expect(readFileSync('site/sitemap.xml', 'utf8')).toContain('<loc>https://gudauri-ski-trip.vercel.app/support</loc>');
  expect(page).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>/);
});

for (const lang of LANGS) {
  test(`דף התמיכה ב-${lang}: נטען, השאלות נפתחות, בלי שגיאות ובלי גלילה הצידה`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', e => errors.push(e.message));
    listenCsp(page);
    page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
    await page.goto('/support#' + lang);
    const doc = page.locator('#' + lang);
    await expect(doc).toBeVisible();
    for (const other of LANGS.filter(l => l !== lang)) await expect(page.locator('#' + other)).toBeHidden();
    await expect(doc.locator('h1')).toHaveText(S['support.title'][lang]);
    expect(await page.evaluate(() => document.documentElement.dir)).toBe(lang === 'he' ? 'rtl' : 'ltr');
    await expect(doc.locator('.sp-btn')).toHaveAttribute('href', 'mailto:pinisagent@gmail.com');
    await expect(doc.locator('.pv-sign')).toContainText(S['support.sign_title'][lang]);
    // five questions, each a touch target of 54, closed until tapped
    const qs = doc.locator('.sp-faq details');
    await expect(qs).toHaveCount(5);
    for (let i = 0; i < 5; i++) {
      const q = qs.nth(i);
      expect((await q.locator('summary').boundingBox())!.height).toBeGreaterThanOrEqual(54);
      await expect(q.locator('p')).toBeHidden();
    }
    await qs.nth(3).locator('summary').click();
    await expect(qs.nth(3).locator('p')).toBeVisible();
    await expect(qs.nth(3).locator('a[href="/account"]')).toBeVisible();
    for (const a of await doc.locator('.sp-btn, .sp-links a').all()) expect((await a.boundingBox())!.height).toBeGreaterThanOrEqual(44);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    if (lang === 'he') await page.screenshot({ path: `test-results/support-${test.info().project.name}.png`, fullPage: true });
    expect(errors).toEqual([]);
  });
}

test('דף התמיכה: הקישורים לפרטיות ולמחיקת החשבון, והשורה באודות', async ({ page }) => {
  await page.goto('/support#en');
  await page.locator('#en .sp-links a[href^="/privacy"]').click();
  await expect(page).toHaveURL(/\/privacy#en$/);
  await expect(page.locator('#en h1')).toHaveText('Privacy Policy');
  await page.goto('/support#he');
  const del = page.locator('#he .sp-links a[href="/account"]');
  await expect(del).toBeVisible();
  // on Vercel /account redirects to the deletion page (tests/account.spec.ts covers that page)
  const redirects = JSON.parse(readFileSync('site/vercel.json', 'utf8')).redirects;
  expect(redirects.find((r: { source: string }) => r.source === '/account')?.destination).toBe('/#account/delete');
  // the "support" row in about, next to privacy (shared key about.support with the apps)
  await page.goto('/');
  await expect(page.locator('.ab-privacy a[href="support"]')).toHaveText(S['about.support'].he);
  // the language chosen on the site, with no hash
  await page.evaluate(() => localStorage.setItem('gud-lang', 'ka'));
  await page.goto('/support');
  await expect(page.locator('#ka')).toBeVisible();
  await expect(page.locator('[data-back="ka"]')).toBeVisible();
});

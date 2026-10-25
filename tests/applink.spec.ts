import { test, expect, Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { listenCsp } from './csp-listen';

// Round 28: /app, the short link to the app. WhatsApp reads only the first 300KB of a page for its preview, so the
// preview tags must be there (on the Google Play page they come after about 1MB), and in Hebrew in the page itself.
const PLAY = 'https://play.google.com/store/apps/details?id=io.github.pini236.skiapp';

test('הקישור הקצר: תגיות התצוגה בתחילת הדף', () => {
  const html = readFileSync('site/app.html', 'utf8');
  expect(Buffer.byteLength(html)).toBeLessThan(300_000);
  const head = html.slice(0, html.indexOf('</head>'));
  expect(head).toContain('<meta property="og:title" content="Gudi: מפת הסקי של גודאורי">');
  expect(head).toContain('<meta property="og:image" content="https://gudauri-ski-trip.vercel.app/og.jpg?v=27">');
  expect(head).toContain('<meta property="og:url" content="https://gudauri-ski-trip.vercel.app/app">');
  expect(head).toContain('<meta name="robots" content="noindex">');
  // a redirect page: not in the sitemap
  expect(readFileSync('site/sitemap.xml', 'utf8')).not.toContain('/app<');
});

function watch(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  listenCsp(page);
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  return errors;
}

test('הקישור הקצר: באנדרואיד ובמחשב עובר לגוגל פליי, עם מקור השיתוף', async ({ page }) => {
  const errors = watch(page);
  await page.route('https://play.google.com/**', r => r.fulfill({ contentType: 'text/html', body: '<!doctype html><title>play</title>' }));
  await page.goto('/app?s=whatsapp');
  await page.waitForURL(u => u.href.startsWith(PLAY));
  const u = new URL(page.url());
  expect(u.searchParams.get('id')).toBe('io.github.pini236.skiapp');
  expect(u.searchParams.get('referrer')).toBe('utm_source=whatsapp&utm_medium=app_link');
  await page.goBack().catch(() => {});
  expect(errors).toEqual([]);
});

test('הקישור הקצר: מקור ברירת המחדל, ושום תו זר במקור', async ({ page }) => {
  await page.route('https://play.google.com/**', r => r.fulfill({ contentType: 'text/html', body: '<!doctype html><title>play</title>' }));
  await page.goto('/app?s=a%26b%3Dc');
  await page.waitForURL(u => u.href.startsWith(PLAY));
  expect(new URL(page.url()).searchParams.get('referrer')).toBe('utm_source=abc&utm_medium=app_link');
  await page.goto('/app');
  await page.waitForURL(u => u.href.startsWith(PLAY));
  expect(new URL(page.url()).searchParams.get('referrer')).toBe('utm_source=share&utm_medium=app_link');
});

test('הקישור הקצר: באייפון נשאר ומציע את האתר', async ({ browser }, info) => {
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, locale: 'he-IL',
    colorScheme: info.project.name.endsWith('dark') ? 'dark' : 'light',
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
  });
  const page = await ctx.newPage();
  const errors = watch(page);
  let play = false;
  await page.route('https://play.google.com/**', r => { play = true; return r.abort(); });
  await page.goto('/app');
  await expect(page.locator('#alIos h2')).toHaveText('באייפון עוד אין אפליקציה');
  await expect(page.locator('#alBtn')).toBeVisible();
  await expect(page.locator('#alBtn')).toHaveAttribute('href', '/');
  await expect(page.locator('#alBadge')).toBeHidden();
  await expect(page.locator('#alGoing')).toBeHidden();
  expect(play).toBe(false);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: `test-results/applink-ios-${info.project.name}.png` });
  expect(errors).toEqual([]);
  await ctx.close();
});

test('הקישור הקצר: בלי סקריפטים, התג והאתר', async ({ browser }) => {
  const ctx = await browser.newContext({ javaScriptEnabled: false, locale: 'he-IL' });
  const page = await ctx.newPage();
  await page.goto('/app');
  await expect(page.locator('#alBadge')).toBeVisible();
  await expect(page.locator('#alBadge')).toHaveAttribute('href', `${PLAY}&referrer=utm_source%3Dshare%26utm_medium%3Dapp_link`);
  await expect(page.locator('#alSite')).toBeVisible();
  await expect(page.locator('#alGoing')).toBeHidden();
  await ctx.close();
});

test('הקישור הקצר: באנגלית, בדף שנשאר (אייפון)', async ({ browser }) => {
  const ctx = await browser.newContext({ locale: 'en-US', userAgent: 'Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1' });
  const page = await ctx.newPage();
  const errors = watch(page);
  await page.goto('/app');
  await expect(page.locator('#alIos h2')).toHaveText('No iPhone app yet');
  await expect(page).toHaveTitle('Gudi: Gudauri Ski Map');
  expect(await page.evaluate(() => document.documentElement.dir)).toBe('ltr');
  expect(errors).toEqual([]);
  await ctx.close();
});

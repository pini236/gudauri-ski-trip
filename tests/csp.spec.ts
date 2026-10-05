import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';

// R-3, S-22: the site's content security policy (site/vercel.json) blocks nothing the site itself uses, on every page:
// the main screens, the privacy page and the five games. The sign-in flow is covered in tests/account.spec.ts.
function violations(page: Page) {
  const v: string[] = [];
  listenCsp(page);
  page.on('console', m => { if (m.type() === 'error' && /CSP violation|Content Security Policy/.test(m.text())) v.push(m.text()); });
  page.on('pageerror', e => v.push('pageerror: ' + e.message));
  return v;
}

test('מדיניות אבטחת התוכן: הכותרות נשלחות, ושום דבר באתר לא נחסם', async ({ page }) => {
  const v = violations(page);
  const res = await page.goto('/');
  const h = res!.headers();
  const csp = h['content-security-policy'] || h['content-security-policy-report-only'];
  expect(csp).toContain("script-src 'self' https://accounts.google.com/gsi/client");
  expect(csp).toContain("frame-ancestors 'none'");
  expect(csp).not.toMatch(/script-src[^;]*'unsafe-inline'/);
  expect(h['x-frame-options']).toBe('DENY');
  expect(h['cross-origin-opener-policy']).toBe('same-origin-allow-popups');
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  for (const hash of ['#map', '#map/run/Tatra%202', '#meet', '#games', '#about', '#trip', '#home']) {
    await page.goto('/' + hash);
    await page.waitForTimeout(400);
  }
  await page.goto('/#map'); await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
  if (await page.locator('#viewsw').isVisible()) { await page.click('#viewsw [data-view="3d"]'); await page.waitForTimeout(800); }
  await page.goto('/privacy');
  for (const g of ['descent', 'school', 'fresh-snow', 'merge', 'snowball']) {
    await page.goto(`/games/${g}/`);
    await page.waitForTimeout(500);
  }
  expect(v).toEqual([]);
  // the libraries the site serves itself (telemetry does not run under test, so fetch them)
  for (const f of ['three-r128.min.js', 'posthog-1.436.1.no-external.js', 'sentry-8.38.0.min.js', 'supabase-2.117.2.js'])
    expect((await page.request.get('/js/vendor/' + f)).status()).toBe(200);
  // and the watch itself works: a picture from a server the policy does not list is reported
  await page.evaluate(() => { const i = new Image(); i.src = 'https://example.com/x.png'; document.body.appendChild(i); });
  await expect.poll(() => v.join(' ')).toContain('img-src');
});

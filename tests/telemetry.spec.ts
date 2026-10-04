import { test, expect } from '@playwright/test';
import { readFileSync } from 'fs';
import { join } from 'path';

// the invite code, the link token and the group id never leave the browser in an address (docs/GROWTH.md)
test('מדידה: הכתובת שנשלחת ל-PostHog ול-Sentry בלי קוד הזמנה, אסימון או מזהה קבוצה', async ({ page }) => {
  await page.goto('/#home');
  const o = 'http://127.0.0.1:4173';
  const out = await page.evaluate(urls => urls.map(u => (window as any).GUD_CLEAN_URL(u)), [
    o + '/j/abcdefghijklmnopqrstuvwxyz0123456789',
    o + '/join/KZBQRM',
    o + '/#join/KZBQRM',
    o + '/#group/6b1f0c2e-1111-2222-3333-444455556666',
    o + '/?utm_source=app&utm_medium=share&code=KZBQRM#meet/12b/0930/20270111',
    o + '/games/descent/',
  ]);
  expect(out).toEqual([
    o + '/j', o + '/join', o + '/#join', o + '/#group',
    o + '/?utm_source=app&utm_medium=share#meet', o + '/games/descent/',
  ]);
  for (const u of out) expect(u).not.toMatch(/KZBQRM|abcdefghij|6b1f0c2e/);
  // both tools get the cleaned address: PostHog before every event, Sentry in the report and its breadcrumbs
  const src = readFileSync(join(__dirname, '..', 'site', 'js', 'telemetry.js'), 'utf8');
  expect(src).toMatch(/before_send:function\(ev\)\{if\(ev\)\{cleanProps\(ev\.properties\)/);
  expect(src).toMatch(/ev\.request\.url=cleanUrl\(ev\.request\.url\)/);
  expect(src).toMatch(/breadcrumbs/);
});

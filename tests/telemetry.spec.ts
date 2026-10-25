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

// best in game_end is a new record of the score in every game (docs/GROWTH.md, S-16, S-33)
test('מדידה: best ב-game_end הוא שיא חדש של הניקוד, גם בירידה ובבית הספר לסקי', async ({ page }) => {
  await page.goto('/#home');
  const out = await page.evaluate(() => {
    localStorage.removeItem('gud-best');
    const end = (game: string, score: number, level?: string) => { const p: any = { game, score, level, best: true }; (window as any).track('game_end', p); return p.best; };
    return [end('descent', 900), end('descent', 400), end('descent', 1200),
      end('school', 3, '1'), end('school', 2, '2'), end('school', 2, '2'), end('school', 1, '1')];
  });
  expect(out).toEqual([true, false, true, true, true, false, false]);
});

// a browser whose clock is in Europe keeps no visitor id (docs/ARCHITECTURE.md, m-5): PostHog runs from memory there
for (const [tz, eu] of [['Europe/Berlin', true], ['Europe/London', true], ['Atlantic/Canary', true], ['Asia/Nicosia', true],
  ['Asia/Jerusalem', false], ['Asia/Tbilisi', false], ['America/New_York', false]] as const) {
  test.describe(`מדידה: אזור הזמן ${tz}`, () => {
    test.use({ timezoneId: tz });
    test(`מזהה מבקר נשמר בדפדפן: ${eu ? 'לא' : 'כן'}`, async ({ page }) => {
      await page.goto('/#home');
      expect(await page.evaluate(() => (window as any).GUD_EUROPE())).toBe(eu);
    });
  });
}
test('מדידה: בדפדפן באירופה PostHog בלי שמירה בדפדפן', async () => {
  const src = readFileSync(join(__dirname, '..', 'site', 'js', 'telemetry.js'), 'utf8');
  expect(src).toMatch(/persistence:europe\(\)\?'memory':'localStorage'/);
});

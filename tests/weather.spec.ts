import { test, expect, Page } from '@playwright/test';
import { readFileSync } from 'fs';
import { join } from 'path';
import { listenCsp } from './csp-listen';

// Weather by altitude (round 19, decision 58; docs/ARCHITECTURE.md m-6, server/CONTRACT.md "/api/weather"). The rule is
// shared with the app through tools/fixtures/weather-m6.json; the server decides the closure risk.
const fixture = JSON.parse(readFileSync(join(__dirname, '..', 'tools', 'fixtures', 'weather-m6.json'), 'utf8'));
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  return errors;
}
async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
}
const trip = (out: string, ret: string) => ({ v: 1, out: { date: out, flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' },
  ret: { date: ret, flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null });
async function setup(page: Page, t: object | null, answer = true) {
  await page.clock.setFixedTime(new Date('2027-01-12T07:25:00Z')); // 11:25 in Gudauri, 20 minutes after the answer
  if (t) await page.addInitScript(v => localStorage.setItem('gud-trip', v), JSON.stringify(t));
  await page.route('**/api/weather', r => answer ? r.fulfill({ contentType: 'application/json', body: JSON.stringify(fixture.answer) }) : r.fulfill({ status: 404, body: '' }));
}

test('מזג אוויר: הכלל זהה לערכי הבדיקה המשותפים', async ({ page }) => {
  await page.goto('/');
  await loaded(page);
  const got = await page.evaluate((fx: any) => {
    const G = (window as any).GudWeather, a = fx.answer, ms = (s: string) => Date.parse(s + ':00+04:00');
    return {
      state: fx.cases.state.map((c: any) => G.state(a, ms(c.now))),
      now: fx.cases.now.map((c: any) => G.now(a, c.point, ms(c.now))),
      altitude: fx.cases.altitude.map((c: any) => G.at(a, c.alt, ms(c.now))),
      risk: fx.cases.risk.map((c: any) => G.risk(a, c.date)),
    };
  }, fixture);
  expect(got.state).toEqual(fixture.cases.state.map((c: any) => c.state));
  fixture.cases.now.forEach((c: any, i: number) => expect(got.now[i]).toMatchObject(c.expect));
  fixture.cases.altitude.forEach((c: any, i: number) => expect({ alt: c.alt, ...got.altitude[i] }).toMatchObject({ alt: c.alt, ...c.expect }));
  fixture.cases.risk.forEach((c: any, i: number) => expect(got.risk[i]).toEqual(c.expect));
});

test('מזג אוויר: "היום על ההר" בטיול, השכבה במפה, והתנאים במסלול', async ({ page }) => {
  const errors = watchErrors(page);
  await setup(page, trip('2027-01-10', '2027-01-15'));
  await page.goto('/');
  await loaded(page);
  const board = page.locator('#wxToday');
  await expect(board).toContainText('היום על ההר');
  await expect(board).toContainText('יום סקי 2 מתוך 4');
  await expect(board).toContainText('עודכן לפני 20 דק׳');
  await expect(board.locator('.wx-alts > div')).toHaveCount(3);
  await expect(board.locator('.wx-alts > div').first()).toContainText('−2°');
  await expect(board.locator('.rk')).toHaveCount(2); // Sadzele high, Kudebi medium on 12.1
  await expect(board).toContainText('גבוה');
  await expect(board.locator('a[href="https://open-meteo.com/"]')).toBeVisible();
  // the layer: off until the chip, three pins and the list
  await page.goto('/#map');
  await loaded(page);
  const chip = page.locator('.wx-chip');
  await expect(chip).toHaveAttribute('aria-pressed', 'false');
  await expect(page.locator('.wx-list')).toBeHidden();
  await chip.click();
  await expect(page.locator('.wx-pin')).toHaveCount(3);
  await expect(page.locator('.wx-list .wx-row')).toHaveCount(3);
  await expect(page.locator('.wx-list')).toContainText('עודכנה לפני 20 דק׳');
  await chip.click();
  await expect(page.locator('.wx-list')).toBeHidden();
  // the run: the top and the bottom of Tatra 2, between the points
  await page.goto('/#map/run/Tatra%202');
  await loaded(page);
  const cond = page.locator('.wx-cond');
  await expect(cond).toContainText('בראש המסלול');
  await expect(cond.locator('.wx-row')).toHaveCount(2);
  expect(errors).toEqual([]);
});

test('מזג אוויר: לפני הטיול הימים שבתחזית, ובלי תשובה אין לוח ואין ניחוש', async ({ page }) => {
  const errors = watchErrors(page);
  await setup(page, trip('2027-01-12', '2027-01-31')); // ski days 13 to 30: the last ones beyond the 15 days
  await page.goto('/');
  await loaded(page);
  const board = page.locator('#wxToday');
  await expect(board).toContainText('התחזית לימי הסקי שלך');
  await expect(board.locator('.wx-day').first()).toContainText('יום סקי 1');
  await expect(board.locator('.wx-day.later').first()).toContainText('התחזית ליום הזה תיפתח ב-');
  expect(errors).toEqual([]);
});

test('מזג אוויר: בלי תשובה מהשרת', async ({ page }) => {
  const errors = watchErrors(page);
  await setup(page, trip('2027-01-10', '2027-01-15'), false);
  // never an answer: no chip (before the server's first forecast it would only say "no data")
  await page.goto('/#map');
  await loaded(page);
  await expect(page.locator('.wx-chip')).toBeHidden();
  // the last answer kept in this browser is three days old: no data, no guess
  await page.clock.setFixedTime(new Date('2027-01-15T09:00:00Z'));
  await page.evaluate(a => localStorage.setItem('gud-wx-last', a), JSON.stringify(fixture.answer));
  await page.reload();
  await loaded(page);
  await expect(page.locator('#wxToday')).toBeHidden();
  await page.locator('.wx-chip').click();
  await expect(page.locator('.wx-list')).toContainText('אין מידע. התחזית האחרונה ישנה מיומיים, או שעוד לא הגיעה.');
  await expect(page.locator('.wx-pin.none')).toHaveCount(3);
  expect(errors).toEqual([]);
});

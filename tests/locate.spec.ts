import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';
import { readFileSync } from 'fs';
import { join } from 'path';
const fixture = JSON.parse(readFileSync(join(__dirname, '..', 'tools', 'fixtures', 'location-m7.json'), 'utf8'));

// "Where am I" (round 19, decision 58; docs/ARCHITECTURE.md m-7). The rule is shared with the app through
// tools/fixtures/location-m7.json; the position never leaves the browser.
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

test('איפה אני: כלל ההצמדה זהה לערכי הבדיקה המשותפים', async ({ page }) => {
  await page.goto('/');
  await loaded(page);
  const got = await page.evaluate(async (fx: any) => {
    const [t, d] = await Promise.all([fetch('data/terrain.json').then(r => r.json()), fetch('data/runs-and-lifts.json').then(r => r.json())]);
    const M = (window as any).GudRelief.load(t);
    return fx.cases.map((c: any) => { const L = (window as any).GudLocate.make(d, M);
      return c.readings.map((r: any) => { const g = L.feed(r); return { state: g.state, run: g.run, lift: g.lift }; }); });
  }, fixture);
  fixture.cases.forEach((c: any, i: number) => c.readings.forEach((r: any, j: number) => {
    const e = r.expect, g = got[i][j];
    expect({ case: c.name, j, ...g, run: e.run ? g.run : undefined, lift: e.lift ? g.lift : undefined })
      .toEqual({ case: c.name, j, state: e.state, run: e.run, lift: e.lift });
  }));
});

test.describe('איפה אני: הכפתור במפה', () => {
  test.use({ permissions: ['geolocation'], geolocation: { latitude: 42.482631, longitude: 44.483401, accuracy: 8 } });
  test('הסבר, נקודה על Tatra 2, מחוץ להר, ובלי קואורדינטות ברשת', async ({ page, context }) => {
    const errors = watchErrors(page);
    const sent: string[] = [];
    page.on('request', r => { const u = r.url() + ' ' + (r.postData() || ''); if (/42\.48|44\.48/.test(u)) sent.push(u); });
    await page.goto('/#map');
    await loaded(page);
    const btn = page.locator('.loc-btn');
    await expect(btn).toBeEnabled();
    await expect(btn).toHaveAttribute('aria-pressed', 'false');
    await btn.click();
    // the short sheet before the browser asks
    const sheet = page.locator('.loc-sheet');
    await expect(sheet).toContainText('המיקום נשאר בטלפון.');
    await sheet.locator('[data-go]').click();
    await expect(sheet).toHaveCount(0);
    const where = page.locator('.where');
    await expect(where).toContainText('על Tatra 2');
    await expect(where).toContainText('דיוק של כ-8 מ׳');
    await expect(btn).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('#map g.me')).not.toHaveAttribute('style', /display: none/); // the 2D dot (the phone opens in 3D, with its own dot)
    // outside the mountain: no dot at the edge of the map
    await context.setGeolocation({ latitude: 41.7, longitude: 44.8, accuracy: 10 });
    await expect(where).toContainText('לא בגודאורי כרגע', { timeout: 10_000 });
    await expect(page.locator('#map g.me')).toHaveAttribute('style', /display: none/);
    // off with the button; the next time no sheet (it was seen in this browser)
    await btn.click();
    await expect(btn).toHaveAttribute('aria-pressed', 'false');
    await expect(where).toBeHidden();
    await btn.click();
    await expect(sheet).toHaveCount(0);
    await expect(btn).toHaveAttribute('aria-pressed', 'true');
    // leaving the map turns it off
    await page.evaluate(() => { location.hash = '#home'; });
    await page.evaluate(() => { location.hash = '#map'; });
    await expect(btn).toHaveAttribute('aria-pressed', 'false');
    expect(sent).toEqual([]);
    expect(errors).toEqual([]);
  });
});

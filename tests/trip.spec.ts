import { test, expect, Page } from '@playwright/test';

// "Your trip" on the site (round 12, stage 13.9): kept only in this browser. The crew's ticket and names are off the home page.
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  return errors;
}
async function loaded(page: Page) { await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 }); }

test('אורח: כרטיס ריק, מצב העונה, ובלי הכרטיס והשמות של החבר׳ה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#bpEmpty')).toBeVisible();
  await expect(page.locator('#bpStack')).toBeHidden();
  await expect(page.locator('#seasonBoard')).toBeVisible();
  await expect(page.locator('#tripNote')).toBeVisible();
  await expect(page.locator('#tbCount')).toBeHidden();
  await expect(page.locator('body')).not.toContainText('שרוליק');
  await expect(page.locator('.crew')).toHaveCount(0);
  // the group's flight and names are no longer published (Pini, 2.10.2026)
  expect((await page.request.get('/data/trip.json')).status()).toBe(404);
  const wide = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  expect(wide).toBeLessThanOrEqual(1);
  expect(errors).toEqual([]);
});

test('הטיול שלך: טופס, שמירה בדפדפן, הכרטיס, עריכה ומחיקה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.clock.setFixedTime(new Date('2026-12-31T09:00:00Z'));
  await page.goto('/');
  await loaded(page);
  await page.locator('#bpEmpty .btn-blue').click();
  await expect(page).toHaveURL(/#trip$/);
  const f = page.locator('#tripForm');
  await expect(f).toBeVisible();
  // the destination starts as Tbilisi, the origin as Tel Aviv
  await expect(f.locator('select[name="ofr"]')).toHaveValue('TLV');
  await expect(f.locator('select[name="oto"]')).toHaveValue('TBS');
  // saving without a date says what is missing
  await f.locator('.tf-save').click();
  await expect(page.locator('#tfErr')).toBeVisible();
  await f.locator('input[name="od"]').fill('2027-01-10');
  await f.locator('input[name="of"]').fill('6h 897');
  await f.locator('input[name="odp"]').fill('16:00');
  await f.locator('input[name="oar"]').fill('20:35');
  await f.locator('input[name="rd"]').fill('2027-01-15');
  await f.locator('input[name="rdp"]').fill('01:35');
  // the return route fills itself; the ski days are worked out like the crew's: 11 to 14
  await expect(page.locator('#tfBackRoute')).toContainText('TBS › TLV');
  await expect(page.locator('#tfSki')).toContainText('11–14.1');
  await expect(page.locator('#tfSki')).toContainText('4 ימים');
  await f.locator('.tf-save').click();
  await expect(page).toHaveURL(/#home$/);
  await expect(page.locator('#bpStack')).toBeVisible();
  await expect(page.locator('#bpEmpty')).toBeHidden();
  await expect(page.locator('#tFlight')).toHaveText('6H 897');
  await expect(page.locator('#tFrom')).toHaveText('תל אביב');
  await expect(page.locator('#tTo')).toHaveText('טביליסי');
  await expect(page.locator('.bp[data-leg="out"] [data-f="pax"]')).toHaveText('אורח');
  await expect(page.locator('.bp[data-leg="out"] [data-f="skiRange"]')).toHaveText('11–14.1');
  await expect(page.locator('.bp[data-leg="ret"] [data-f="fromCode"]')).toHaveText('TBS');
  await expect(page.locator('#tDays')).toHaveText('10');
  // kept in this browser
  await page.reload();
  await loaded(page);
  await expect(page.locator('#tFlight')).toHaveText('6H 897');
  // edit, then delete
  await page.locator('.bp-edit').click();
  await expect(f.locator('input[name="od"]')).toHaveValue('2027-01-10');
  await page.locator('#tfDelete').click();
  await expect(page.locator('#bpEmpty')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('gud-trip'))).toBeNull();
  expect(errors).toEqual([]);
});

test('הטיול שלך: ימי סקי ידניים, ושדה תעופה אחר', async ({ page }) => {
  await page.goto('/#trip');
  await loaded(page);
  const f = page.locator('#tripForm');
  await f.locator('select[name="ofr"]').selectOption('');
  await f.locator('input[name="ofrx"]').fill('ist');
  await f.locator('input[name="od"]').fill('2027-02-01');
  await f.locator('input[name="rd"]').fill('2027-02-06');
  await page.locator('#tfSkiBtn').click();
  await f.locator('input[name="sf"]').fill('2027-02-02');
  await f.locator('input[name="sl"]').fill('2027-02-03');
  await expect(page.locator('#tfSki')).toContainText('יומיים');
  await f.locator('.tf-save').click();
  await expect(page.locator('.bp[data-leg="out"] [data-f="fromCode"]')).toHaveText('IST');
  await expect(page.locator('.bp[data-leg="out"] [data-f="skiRange"]')).toHaveText('2–3.2');
});

test('נקודת מפגש בלי טיול: שבוע מהיום', async ({ page }) => {
  const errors = watchErrors(page);
  await page.clock.setFixedTime(new Date('2026-12-31T09:00:00Z'));
  await page.goto('/#meet');
  await loaded(page);
  await expect(page.locator('[data-days] button')).toHaveCount(7);
  await expect(page.locator('[data-days] button').first()).toHaveAttribute('data-day', '2026-12-31');
  expect(errors).toEqual([]);
});

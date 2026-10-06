import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';

// "Your trip" on the site (round 12, stage 13.9): kept only in this browser. The crew's ticket and names are off the home page.
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  return errors;
}
async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  // the elevation model and three.js come after the home page (R-11): wait for them where they matter
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
  if (/^#map/.test(new URL(page.url()).hash)) await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
}

test('אורח: כרטיס ריק, מצב העונה, ובלי הכרטיס והשמות של החבר׳ה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#bpEmpty')).toBeVisible();
  await expect(page.locator('#bpStack')).toBeHidden();
  await expect(page.locator('#seasonBoard')).toBeVisible();
  // round 20: no lock line, no tally, signs without a line under them
  await expect(page.locator('#tripNote')).toHaveCount(0);
  await expect(page.locator('section.tally')).toHaveCount(0);
  await expect(page.locator('.board:visible > span:visible')).toHaveCount(0);
  await expect(page.locator('#tbCount')).toBeHidden();
  await expect(page.locator('body')).not.toContainText('שרוליק');
  await expect(page.locator('.crew')).toHaveCount(0);
  // the group's flight and names are no longer published (Pini, 2.10.2026)
  expect((await page.request.get('/data/trip.json')).status()).toBe(404);
  const wide = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  expect(wide).toBeLessThanOrEqual(1);
  expect(errors).toEqual([]);
});

test('מצב הרכבלים בדף הבית (סבב 20): כבוי עם "אין דיווח" בלי דיווח, ועם דיווח עדכני הרכבלים הפתוחים מתמלאים', async ({ page }) => {
  const errors = watchErrors(page);
  const board = page.locator('#seasonBoard');
  for (const t of ['2026-10-20T09:00:00Z', '2027-01-12T09:00:00Z']) {
    await page.clock.setFixedTime(new Date(t));
    await page.goto('/');
    await loaded(page);
    await expect(board).toHaveAttribute('data-state', 'off');
    await expect(board).toContainText('מצב הרכבלים');
    await expect(board).toContainText('אין דיווח');
    await expect(board.locator('.sb-f b')).toHaveText(/^0\/\d+$/);
    await expect(board.locator('.sb-dots i.on')).toHaveCount(0);
    expect(await board.locator('.sb-dots i').count()).toBeGreaterThan(5);
  }
  // a fresh report: the open lifts fill in, and the snow is gone
  await page.route('**/api/status', r => r.fulfill({ contentType: 'application/json', body: JSON.stringify({
    updated: '2027-01-12T08:55:00Z', lifts: { Goodaura: { open: true }, Kudebi: { open: false, reason: 'wind' }, Sadzele: { open: true } }, pistes: {} }) }));
  await page.reload();
  await loaded(page);
  await expect(board).toHaveAttribute('data-state', 'live');
  await expect(board.locator('.sb-f b')).toHaveText(/^2\/\d+$/);
  await expect(board.locator('.sb-dots i.on')).toHaveCount(2);
  await expect(board).not.toContainText('אין דיווח');
  await expect(board.locator('.snowcap')).toBeHidden();
  expect(errors).toEqual([]);
});

test('הטיול שלך: טופס, שמירה בדפדפן, הכרטיס, עריכה ומחיקה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.clock.setFixedTime(new Date('2026-12-31T09:00:00Z'));
  await page.goto('/');
  await loaded(page);
  // the full form stays behind "edit" (round 20: the empty ticket opens the calendar instead)
  await page.evaluate(() => { location.hash = '#trip'; });
  const f = page.locator('#tripForm');
  await expect(f).toBeVisible();
  // the destination starts as Tbilisi, the origin as Tel Aviv
  await expect(f.locator('input[name="ofr"]')).toHaveValue('TLV');
  await expect(f.locator('[data-place="ofr"]')).toContainText('TLV · תל אביב');
  await expect(f.locator('input[name="oto"]')).toHaveValue('TBS');
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
  // two taps, as in the app
  await page.locator('#tfDelete').click();
  await expect(page.locator('#tfDelete')).toHaveText('בטוח? לחיצה נוספת מוחקת');
  await page.locator('#tfDelete').click();
  await expect(page.locator('#bpEmpty')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('gud-trip'))).toBeNull();
  expect(errors).toEqual([]);
});

test('הכרטיס הוא הטופס (סבב 20): שתי נגיעות בלוח שנה, TLV ו-TBS כתובים, ומספר הטיסה מחריץ על הכרטיס', async ({ page }) => {
  const errors = watchErrors(page);
  await page.clock.setFixedTime(new Date('2026-12-01T09:00:00Z'));
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#bpEmpty .be-code').first()).toHaveText('TLV');
  await page.locator('#bpEmpty [data-when]').click();
  const sh = page.locator('#tripSheet');
  await expect(sh).toBeVisible();
  await expect(sh.locator('[data-save]')).toBeDisabled();
  await expect(sh.locator('[data-d="2026-11-30"]')).toHaveCount(0);
  await sh.locator('[data-mon="1"]').click();
  await sh.locator('[data-d="2027-01-10"]').click();
  await sh.locator('[data-d="2027-01-15"]').click();
  await expect(sh.locator('[data-d="2027-01-12"]')).toHaveClass(/ski/);
  await expect(sh.locator('.ts-legend')).toContainText('4 ימי סקי');
  await sh.locator('[data-save]').click();
  await expect(sh).toHaveCount(0);
  await expect(page.locator('#bpStack')).toBeVisible();
  await expect(page.locator('.bp[data-leg="out"] [data-f="fromCode"]')).toHaveText('TLV');
  await expect(page.locator('.bp[data-leg="out"] [data-f="toCode"]')).toHaveText('TBS');
  await expect(page.locator('.bp[data-leg="out"] [data-f="skiRange"]')).toHaveText('11–14.1');
  // the flight number is an optional slot on the ticket
  await page.locator('#tFlight [data-slot="out"]').click();
  await expect(sh).toBeVisible();
  await sh.locator('[data-to="KUT"]').click();
  await sh.locator('[name="fl"]').fill('6h 897');
  await sh.locator('[name="dp"]').fill('16:00');
  await sh.locator('[data-save]').click();
  await expect(page.locator('#tFlight')).toHaveText('6H 897');
  await expect(page.locator('#tDeparts')).toHaveText('16:00');
  await expect(page.locator('#tArrives [data-slot]')).toBeVisible();
  await expect(page.locator('.bp[data-leg="out"] [data-f="toCode"]')).toHaveText('KUT');
  const t = await page.evaluate(() => JSON.parse(localStorage.getItem('gud-trip') || 'null'));
  expect(t.out).toMatchObject({ date: '2027-01-10', from: 'TLV', to: 'KUT', flight: '6H 897', departs: '16:00' });
  expect(t.ret.date).toBe('2027-01-15');
  expect(errors).toEqual([]);
});

test('הטיול שלך: תאריך שעבר נחסם, וטיסת לילה נוחתת למחרת (S-10, S-27)', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-10-20T09:00:00Z'));
  await page.goto('/#trip');
  await loaded(page);
  const f = page.locator('#tripForm');
  await expect(f.locator('input[name="od"]')).toHaveAttribute('min', '2026-10-20');
  await f.locator('input[name="od"]').fill('2026-10-01');
  await f.locator('.tf-save').click();
  await expect(page.locator('#tfErr')).toHaveText('התאריך הזה כבר עבר.');
  // leaves 23:00, lands 05:30 the next morning: skiing starts that same day
  await f.locator('input[name="od"]').fill('2027-01-10');
  await f.locator('input[name="odp"]').fill('23:00');
  await f.locator('input[name="oar"]').fill('05:30');
  await f.locator('input[name="rd"]').fill('2027-01-15');
  await expect(page.locator('#tfSki')).toContainText('11–14');
});

test('הטיול שלך: ימי סקי ידניים, ושדה תעופה אחר', async ({ page }) => {
  await page.goto('/#trip');
  await loaded(page);
  const f = page.locator('#tripForm');
  // the airport from the sheet (K-5): search by city, and a three-letter code that is not listed; no free text
  await f.locator('[data-place="oto"]').click();
  const sheet = page.locator('.tf-sheet');
  await sheet.locator('input').fill('קוטא');
  await expect(sheet.locator('.tf-ap')).toHaveCount(1);
  await sheet.locator('.tf-ap').click();
  await expect(f.locator('input[name="oto"]')).toHaveValue('KUT');
  await f.locator('[data-place="ofr"]').click();
  await sheet.locator('input').fill('lca');
  await expect(sheet.locator('.tf-ap.code')).toContainText('LCA');
  await sheet.locator('input').fill('לרנקה שלי');
  await expect(sheet.locator('.tf-ap')).toHaveCount(0);
  await sheet.locator('input').fill('ist');
  await sheet.locator('.tf-ap[data-code="IST"]').click();
  await expect(f.locator('[data-place="ofr"]')).toContainText('IST · איסטנבול');
  await f.locator('input[name="od"]').fill('2027-02-01');
  await f.locator('input[name="rd"]').fill('2027-02-06');
  await page.locator('#tfSkiBtn').click();
  // a ski day after the return is refused, as in the app (S-34)
  await expect(f.locator('input[name="sl"]')).toHaveAttribute('max', '2027-02-06');
  await f.locator('input[name="sf"]').fill('2027-02-02');
  await f.locator('input[name="sl"]').fill('2027-02-08');
  await f.locator('.tf-save').click();
  await expect(page.locator('#tfErr')).toHaveText('ימי הסקי צריכים להיות בתוך הטיול');
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

test('הטיול שלך: אין תווית ריקה בעברית (גם לפני שמילוי השפה רץ)', async ({ page }) => {
  await page.goto('/');
  await loaded(page);
  await page.evaluate(() => { location.hash = '#trip'; });
  await expect(page.locator('#tripForm')).toBeVisible();
  const empty = await page.locator('#tripForm .tf-fld > span').evaluateAll(els => els.filter(e => !e.textContent?.trim()).length);
  expect(empty).toBe(0);
  await expect(page.locator('#tripForm .tf-fld > span').first()).toHaveText('תאריך');
});

// during the trip the stub and the top bar say "ski day n of m", and after the return there is no count (S-35, decision 58)
test('בזמן הטיול "יום סקי 2 מתוך 4", ואחרי החזרה בלי ספירה', async ({ page }) => {
  const errors = watchErrors(page);
  await page.addInitScript(() => localStorage.setItem('gud-trip', JSON.stringify({ v: 1,
    out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' },
    ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null })));
  const step = async (iso: string) => { await page.clock.setFixedTime(new Date(iso)); await page.reload(); await loaded(page); };
  await page.clock.setFixedTime(new Date('2027-01-09T09:00:00Z'));
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#tDays')).toHaveText('1');
  await step('2027-01-10T09:00:00Z'); // the flight's day, before the first ski day
  await expect(page.locator('#tDays')).toHaveText('0');
  await step('2027-01-12T09:00:00Z');
  await expect(page.locator('#tbCount')).toHaveText('יום סקי 2 מתוך 4');
  await expect(page.locator('#tDaysPre')).toHaveText('יום סקי');
  await expect(page.locator('#tDays')).toHaveText('2');
  await expect(page.locator('#tDaysLbl')).toHaveText('מתוך 4');
  await step('2027-01-15T09:00:00Z'); // the return's day: still 4 of 4
  await expect(page.locator('#tDays')).toHaveText('4');
  await step('2027-01-16T09:00:00Z');
  await expect(page.locator('#tbCount')).toBeHidden();
  await expect(page.locator('#tDays')).toHaveText('');
  expect(errors).toEqual([]);
});

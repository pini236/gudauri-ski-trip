import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';

// Food and services near the stations (round 31, decision 77): a lean places.json (docs/ARCHITECTURE.md, "places"), read
// by the site for Gudauri only. Here a small made-up file stands in for it, so the test checks the screens and does not
// depend on the data: names are invented, the lift ids are Goodaura's (158744075) as they stand in runs-and-lifts.json.
const FILE = {
  schema: 1, kinds: ['food', 'cafe', 'aid', 'wc'], maxDistance: 250, attribution: '(c) OpenStreetMap contributors',
  stations: [
    { id: 'g-bottom', label: 'Goodaura bottom', ll: [42.471213, 44.492689], ends: [{ lift: 'Goodaura', liftId: 158744075, end: 'bottom' }],
      info: 'some', places: [{ p: 'n1', d: 50 }, { p: 'n2', d: 80 }, { p: 'n3', d: 90 }, { p: 'n4', d: 120 }, { p: 'n5', d: 150 }] },
    { id: 'g-top', label: 'Goodaura top', ll: [42.492531, 44.494212], ends: [{ lift: 'Goodaura', liftId: 158744075, end: 'top' }], info: 'none', places: [] },
  ],
  places: [
    { id: 'n1', kind: 'food', name: 'Test Grill', ll: [42.4712, 44.4927], cert: 'a', seasonal: false, slope: false },
    { id: 'n2', kind: 'cafe', name: 'Test Cafe', ll: [42.4713, 44.4927], cert: 'b', seasonal: false, slope: false },
    { id: 'n3', kind: 'aid', name: null, ll: [42.4714, 44.4927], cert: 'b', seasonal: false, slope: false },
    { id: 'n4', kind: 'wc', name: null, ll: [42.4715, 44.4927], cert: 'b', seasonal: false, slope: false },
    { id: 'n5', kind: 'food', name: 'Test Hut', ll: [42.4716, 44.4927], cert: 'b', seasonal: true, slope: false },
    { id: 'n6', kind: 'cafe', name: 'Slope Cafe', ll: [42.4800, 44.4950], cert: 'b', seasonal: true, slope: true },
  ],
};
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  return errors;
}
async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
  if (/^#map/.test(new URL(page.url()).hash)) await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
}
const withFile = (page: Page, body: unknown = FILE) =>
  page.route('**/data/places.json', r => r.fulfill({ contentType: 'application/json', body: JSON.stringify(body) }));

// the lift panel opens from the meeting card ("see on the routes map"): the run panel no longer lists lifts (round 20)
async function openLift(page: Page) {
  await page.goto('/?lang=en#meet/158744075b/0930/20270111');
  await loaded(page);
  await page.locator('#meetOnMap').click();
  await expect(page.locator('#panel h2')).toContainText('Goodaura');
}

test('places: dots on the map, the chip turns them into tags, and a cafe on the slope gets its own ring', async ({ page }) => {
  const errors = watchErrors(page);
  await withFile(page);
  await page.goto('/?lang=en#map');
  await loaded(page);
  await page.locator('#viewsw [data-view="2d"]').click(); // the map opens on the 3D view; the layer is drawn on the top view
  const station = page.locator('.pl-pins .pl-st:not(.slope)');
  await expect(station).toHaveCount(1); // the station with no information gets no dot
  await expect(station.locator('.pl-dot')).toHaveCount(4); // one dot for each kind that is there
  await expect(station.locator('b')).toHaveCount(0);
  await expect(page.locator('.pl-pins .pl-st.slope')).toHaveCount(1);
  const chip = page.locator('.pl-chip');
  await expect(chip).toHaveAttribute('aria-pressed', 'false');
  await chip.click();
  await expect(chip).toHaveAttribute('aria-pressed', 'true');
  await expect(station.locator('b')).toHaveText('5');
  await expect(page.locator('.pl-pins .pl-st.slope bdi')).toHaveText('Slope Cafe');
  // the 3D view draws no layer of its own, and the chip brings the top view back
  await page.locator('#viewsw [data-view="3d"]').click();
  await expect(page.locator('.pl-pins')).toBeHidden();
  expect(errors).toEqual([]);
});

test('places: the lift panel tells what is near the top and the bottom, three first and then all of them', async ({ page }) => {
  const errors = watchErrors(page);
  await withFile(page);
  await openLift(page);
  const top = page.locator('#panel .pl-near').nth(0), bottom = page.locator('#panel .pl-near').nth(1);
  await expect(top).toContainText('Near the top station');
  await expect(top).toContainText('No information on places here'); // not "nothing here"
  await expect(bottom).toContainText('Near the bottom station');
  await expect(bottom.locator('ul').first().locator('li')).toHaveCount(3);
  await expect(bottom.locator('ul').first()).toContainText('Test Grill');
  await expect(bottom.locator('ul').first()).toContainText('Rescue post'); // no name: the kind is the name
  await expect(bottom.locator('summary')).toHaveText('All 5');
  await bottom.locator('summary').click();
  await expect(bottom).toContainText('Test Hut');
  await expect(bottom).toContainText('Seasonal');
  await expect(bottom).toContainText('Worth checking on the spot'); // no opening hours in the data
  expect(errors).toEqual([]);
});

test('places: one line under the meeting card', async ({ page }) => {
  const errors = watchErrors(page);
  await withFile(page);
  await page.goto('/?lang=en#meet');
  await loaded(page);
  await expect(page.locator('#meetPlaces')).toBeHidden();
  await page.goto('/?lang=en#meet/' + '158744075b' + '/0930/20270111');
  await loaded(page);
  await expect(page.locator('#meetPlaces')).toContainText('Places near the station: 5');
  expect(errors).toEqual([]);
});

test('places: no file, no layer; and a kind this version does not know is ignored', async ({ page }) => {
  const errors = watchErrors(page);
  await page.route('**/data/places.json', r => r.fulfill({ status: 404, body: '' }));
  await page.goto('/?lang=en#map');
  await loaded(page);
  await expect(page.locator('.pl-chip')).toHaveCount(0);
  await expect(page.locator('.pl-pins')).toHaveCount(0);
  const odd = JSON.parse(JSON.stringify(FILE)); odd.places[0].kind = 'sauna';
  await page.unroute('**/data/places.json'); await withFile(page, odd);
  await page.goto('about:blank'); // a hash-only change would keep the page that has no file
  await openLift(page);
  await expect(page.locator('#panel .pl-near').nth(1)).not.toContainText('Test Grill');
  expect(errors).toEqual([]);
});

test('places: only Gudauri has them', async ({ page }) => {
  const seen: string[] = [];
  page.on('request', r => { const u = new URL(r.url()); if (/places\.json/.test(u.pathname)) seen.push(u.pathname); });
  await withFile(page);
  await page.goto('/?resort=soelden#map');
  await loaded(page);
  expect(seen).toEqual([]);
  await expect(page.locator('.pl-chip')).toHaveCount(0);
});

test('places: the real file is read and every station end is a lift the site knows', async ({ page }) => {
  const errors = watchErrors(page);
  const file = await (await page.request.get('/data/places.json')).json();
  const lifts = await (await page.request.get('/data/runs-and-lifts.json')).json();
  const ids = new Set(lifts.lifts.map((l: any) => l.id));
  expect(file.schema).toBe(1);
  for (const s of file.stations) for (const e of s.ends) expect(ids.has(e.liftId), `${s.id}: ${e.liftId}`).toBe(true);
  for (const s of file.stations) for (const r of s.places) expect(file.places.some((q: any) => q.id === r.p), `${s.id}: ${r.p}`).toBe(true);
  await page.goto('/?lang=en#map');
  await loaded(page);
  await page.locator('#viewsw [data-view="2d"]').click();
  await expect(page.locator('.pl-pins .pl-st:not(.slope)').first()).toBeAttached();
  expect(errors).toEqual([]);
});

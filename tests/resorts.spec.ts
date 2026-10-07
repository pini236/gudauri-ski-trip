import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';

// Several resorts (decision 68, round 23): Sölden next to Gudauri. Gudauri stays the default and keeps everything;
// another resort has the map, the 3D view and the runs, from its own folder, and nothing of Gudauri's.
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => {
    if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text());
  });
  return errors;
}
async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
  if (/^#map/.test(new URL(page.url()).hash)) await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
}

test('Sölden: its own data, the pass card, and nothing of Gudauri', async ({ page }) => {
  const errors = watchErrors(page);
  const seen: string[] = [];
  page.on('request', r => { const u = new URL(r.url()); if (/\/data\/|\/api\//.test(u.pathname)) seen.push(u.pathname); });
  await page.goto('/?resort=soelden');
  await loaded(page);
  await expect(page.locator('html')).toHaveAttribute('data-resort', 'soelden');
  // only this resort's model: never Gudauri's (the architect's review, 6.10.2026), and no lift status or weather
  expect(seen).toContain('/data/resorts/soelden/runs-and-lifts.json');
  expect(seen).toContain('/data/resorts/soelden/terrain.json');
  expect(seen).not.toContain('/data/terrain.json');
  expect(seen).not.toContain('/data/runs-and-lifts.json');
  expect(seen.filter(u => u.startsWith('/api/'))).toEqual([]);
  // the pass card, its numbers from the data, in place of the trip
  const pass = page.locator('#resortPass');
  await expect(pass).toBeVisible();
  await expect(pass).toContainText('Sölden');
  await expect(page.locator('.ticket-wrap')).toBeHidden();
  await expect(page.locator('.board-meet')).toBeHidden();
  await expect(page.locator('#groupBoard')).toBeHidden();
  await expect(page.locator('.home-top .when')).toBeHidden();
  expect(errors).toEqual([]);
});

test('Sölden map: number plates, a run panel, and a share link that keeps the resort', async ({ page, context }) => {
  const errors = watchErrors(page);
  await page.goto('/?resort=soelden#map');
  await loaded(page);
  await expect(page.locator('#panel .index button').first()).toBeVisible();
  await expect(page.locator('#inset')).toBeHidden(); // no Kobi side here
  await expect(page.locator('#mstat')).toBeHidden(); // no lift status yet
  // the top view: plates, never on another run's line
  const top = page.locator('#viewsw button[data-view="2d"]');
  if (await top.isVisible()) await top.click();
  await expect.poll(() => page.locator('#map .lbl.plate:visible').count()).toBeGreaterThan(5);
  await page.evaluate(() => { location.hash = '#map/run/30'; });
  await expect(page.locator('#panel .run-sign h2')).toHaveText('30');
  await expect(page.locator('#panel .run-ref')).toHaveCount(0); // the number once, not twice
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.evaluate(() => { (navigator as any).share = undefined; });
  await page.locator('#panel [data-share]').click();
  const url = await page.evaluate(() => navigator.clipboard.readText());
  expect(url).toContain('resort=soelden');
  expect(url).toContain('#map/run/30');
  expect(errors).toEqual([]);
});

test('the picker: the place name opens the list, and a choice loads the other resort', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/');
  await loaded(page);
  const btn = page.locator('.rs-btn:visible').first();
  await btn.click();
  const list = page.locator('.rs-list');
  await expect(list.locator('.rs-res')).toHaveCount(3); // Gudauri, Sölden, Sella Ronda
  await expect(list.locator('.rs-res.on')).toHaveAttribute('data-resort', 'gudauri');
  await list.locator('[data-resort="soelden"]').click();
  await page.waitForURL(/resort=soelden/);
  await loaded(page);
  await expect(page.locator('html')).toHaveAttribute('data-resort', 'soelden');
  // kept in this browser: the bare address opens Sölden again, and says so in the address
  await page.goto('/#home');
  await loaded(page);
  await expect(page.locator('html')).toHaveAttribute('data-resort', 'soelden');
  expect(new URL(page.url()).searchParams.get('resort')).toBe('soelden');
  // a link into a Gudauri run without ?resort is a Gudauri link, whatever was chosen here
  await page.goto('/#map/run/Tatra%201');
  await loaded(page);
  await expect(page.locator('html')).toHaveAttribute('data-resort', 'gudauri');
  await expect(page.locator('#panel .run-sign h2')).toHaveText('Tatra 1');
  expect(errors).toEqual([]);
});

test('Sölden: ski routes are a kind of their own, with no colour or difficulty (round 24)', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/?resort=soelden#map/run/62');
  await loaded(page);
  // the panel: a type tag in place of colour and difficulty
  await expect(page.locator('#panel .run-sign h2')).toHaveText('62');
  await expect(page.locator('#panel .run-sign h2')).toHaveClass(/c-route/);
  await expect(page.locator('#panel .route-tag')).toBeVisible();
  await expect(page.locator('#panel .pips')).toHaveCount(0);
  // a chip for them, after the colours; Gudauri has none
  const chip = page.locator('#filters [data-filter="route"]');
  await expect(chip).toBeVisible();
  const top = page.locator('#viewsw button[data-view="2d"]');
  if (await top.isVisible()) await top.click();
  await expect(page.locator('#map .pg.route').first()).toBeAttached();
  await chip.click();
  await expect(page.locator('#map .pg.route path').first()).toBeHidden();
  // as on the official map (round 24, 1b): a light band with dotted red edges
  await expect(page.locator('#map .pg.route path[stroke="var(--p-route-edge)"][stroke-dasharray="2 2.5"]').first()).toBeAttached();
  // wide runs (2b): the area in the run's own colour, solid
  const area = page.locator('#map path.pg-area').first();
  await expect(area).toHaveAttribute('stroke', /var\(--p-/);
  expect(await area.evaluate(e => getComputedStyle(e).fillOpacity)).toBe('1');
  // a selected wide run (2c): its area joins the run's paint, in slope colours over its own colour
  await page.evaluate(() => { location.hash = '#map/run/11'; });
  await expect(page.locator('#panel .run-sign h2')).toHaveText('11');
  await expect(page.locator('#map .runpaint path.rp-area')).toHaveCount(1);
  await expect(page.locator('#map .runpaint image.rp-area')).toHaveCount(1);
  expect(errors).toEqual([]);
});

test('Gudauri: no ski-route chip', async ({ page }) => {
  await page.goto('/#map');
  await loaded(page);
  await expect(page.locator('#filters [data-filter="route"]')).toHaveCount(0);
});

test('the legend folds into a chip, closed on every visit (round 24)', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/?resort=soelden#map');
  await loaded(page);
  const chip = page.locator('.mlegend .ml-chip'), card = page.locator('.mlegend .ml-card');
  await expect(chip).toBeVisible();
  await expect(chip).toHaveAttribute('aria-expanded', 'false');
  await expect(card).toBeHidden();
  const box = await chip.boundingBox();
  expect(box!.height).toBeGreaterThanOrEqual(44);
  await chip.click();
  await expect(card).toBeVisible();
  await expect(chip).toHaveAttribute('aria-expanded', 'true');
  await expect(card.locator('span')).toHaveCount(4);   // ski route, hut route, link way, wide run
  await card.locator('.ml-x').click();
  await expect(card).toBeHidden();
  await chip.click();
  await chip.click();
  await expect(card).toBeHidden();
  // Gudauri has it too now: a link way (Shino) and a wide run, across from the Kobi chip
  await page.goto('/?resort=gudauri#map');
  await loaded(page);
  await expect(page.locator('.mlegend .ml-card')).toBeHidden();
  await page.locator('.mlegend .ml-chip').click();
  await expect(page.locator('.mlegend .ml-card span')).toHaveCount(2);
  const kobi = await page.locator('#insetOpen').boundingBox(), leg = await page.locator('.mlegend .ml-chip').boundingBox();
  expect(leg!.x + leg!.width).toBeLessThan(kobi!.x);
  expect(errors).toEqual([]);
});

test('3D draws what the map from above does: wide runs, ski-route bands, dashes and plates (round 24)', async ({ page }) => {
  const errors = watchErrors(page);
  await page.goto('/?resort=soelden#map/run/11');
  await loaded(page);
  if (!(await page.locator('#viewsw').isVisible())) test.skip(true, 'no WebGL here');
  await page.click('#viewsw [data-view="3d"]');
  await expect(page.locator('#viewsw [data-view="3d"]')).toHaveAttribute('aria-pressed', 'true');
  const k = await page.evaluate(() => (Array.from(document.querySelectorAll('canvas')).find((c: any) => c.kinds) as any).kinds());
  expect(k.areas).toBeGreaterThan(5);      // wide runs in their own colour
  expect(k.bands).toBeGreaterThan(3);      // ski routes: the orange band with dotted red edges
  expect(k.dashed).toBeGreaterThan(2);     // link ways, unnamed sections, the route to a hut
  expect(k.plates).toBeGreaterThan(20);    // number plates
  expect(k.selArea).toBe(true);            // the selected wide run's area, by slope
  // Gudauri: its wide run (Pirveli) and the dashed link way (Shino)
  await page.goto('/?resort=gudauri#map');
  await loaded(page);
  await page.click('#viewsw [data-view="3d"]');
  const g = await page.evaluate(() => (Array.from(document.querySelectorAll('canvas')).find((c: any) => c.kinds) as any).kinds());
  expect(g.areas).toBeGreaterThan(0);
  expect(g.dashed).toBeGreaterThan(0);
  expect(errors).toEqual([]);
});

test('Sölden 38: the slope numbers skip the glacier tunnel', async ({ page }) => {
  // the elevation model measures the hill above the tunnel; with it, a blue run showed a wall of about 48°
  await page.goto('/?resort=soelden#map/run/38');
  await loaded(page);
  const steep = await page.locator('#panel .steps .st b').innerText();
  expect(parseInt(steep, 10)).toBeLessThan(30);
});

// Sella Ronda (the third resort, built by .claude/skills/new-resort): runs keyed by name, colours from the valleys' own lists,
// a name that starts with a number reads left to right, and no filter for a colour this resort has no run in.
test('Sella Ronda: its own data, names on the lines, the official colour, no green filter', async ({ page }) => {
  const errors = watchErrors(page);
  const seen: string[] = [];
  page.on('request', r => { const u = new URL(r.url()); if (/\/data\//.test(u.pathname)) seen.push(u.pathname); });
  await page.goto('/?resort=sellaronda#map');
  await loaded(page);
  await expect(page.locator('html')).toHaveAttribute('data-resort', 'sellaronda');
  expect(seen).toContain('/data/resorts/sellaronda/terrain.json');
  expect(seen).not.toContain('/data/terrain.json');
  expect(seen).not.toContain('/data/resorts/soelden/terrain.json');
  await expect(page.locator('#filters [data-filter="green"]')).toBeHidden();
  await expect(page.locator('#filters [data-filter="blue"]')).toBeVisible();
  const top = page.locator('#viewsw button[data-view="2d"]');
  if (await top.isVisible()) await top.click();
  // names, not plates: in Italy the numbers repeat in every valley
  await expect(page.locator('#map .lbl.plate')).toHaveCount(0);
  await expect(page.locator('#map text.lbl.pg[data-key="3-Tre"]')).toHaveAttribute('direction', 'ltr');
  // Charly is blue in the open map and red on Val Gardena's list: the list wins
  await page.evaluate(() => { location.hash = '#map/run/Charly'; });
  await expect(page.locator('#panel .run-sign h2')).toHaveText('Charly');
  await expect(page.locator('#panel .run-sign h2')).toHaveClass(/c-red/);
  // a name with a space, and the circuit's names stripped from it
  await page.evaluate(() => { location.hash = '#map/run/' + encodeURIComponent('Gran Risa'); });
  await expect(page.locator('#panel .run-sign h2')).toHaveText('Gran Risa');
  expect(errors).toEqual([]);
});

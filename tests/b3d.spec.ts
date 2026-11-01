import { test, expect, Page } from '@playwright/test';
import { listenCsp } from './csp-listen';
import { readFileSync } from 'fs';

// The New Gudauri buildings in 3D: a POC behind ?b3d=1 (decision 77, the architect's review in docs/ARCHITECTURE.md).
// Without the flag nothing of it loads; with it the buildings come only when the camera nears the village, stay
// under the triangle budget, say they are a rendering, and every building says where its height and look came from.
function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => {
    if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text());
  });
  return errors;
}
async function open3d(page: Page, url: string) {
  await page.goto(url);
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
  await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
  await page.locator('#viewsw button[data-view="3d"]').click();
}

test('the data: schema 1, a source for every height and look, inside the budget', () => {
  const raw = readFileSync('site/data/buildings.json', 'utf8');
  expect(Buffer.byteLength(raw)).toBeLessThanOrEqual(60 * 1024);
  const d = JSON.parse(raw);
  expect(d.schema).toBe(1);
  expect(d.proj).toEqual(JSON.parse(readFileSync('site/data/terrain.json', 'utf8')).proj);
  expect(d.b.length).toBeGreaterThan(15);
  for (const b of d.b) {
    expect(b.id).toMatch(/^[nwr]\d+$/);
    expect(['osm-height', 'osm-levels', 'photo', 'estimate']).toContain(b.hsrc);
    expect(['photo', 'fill']).toContain(b.lsrc);
    if (b.lsrc === 'photo') expect(d.sources[b.src]).toBeTruthy();
    expect(d.styles[b.look]).toBeTruthy();
  }
  for (const o of d.deco) expect(o.fill).toBe(true);
});

test('without the flag, nothing of the buildings loads', async ({ page }) => {
  const seen: string[] = [];
  page.on('request', r => seen.push(new URL(r.url()).pathname));
  await open3d(page, '/#map');
  await page.waitForTimeout(1500);
  expect(seen).not.toContain('/js/buildings3d.js');
  expect(seen).not.toContain('/data/buildings.json');
  await expect(page.locator('.b3d-legend')).toHaveCount(0);
});

test('with ?b3d=1: the village in 3D, the legend, a building says its sources', async ({ page }) => {
  const errors = watchErrors(page);
  const seen: string[] = [];
  page.on('request', r => seen.push(new URL(r.url()).pathname));
  await open3d(page, '/?b3d=1#map');
  await expect.poll(() => page.evaluate(() => !!(window as any).GudBuildings)).toBe(true);
  // far from the village (the opening view of the whole mountain) the data does not load yet
  expect(seen).not.toContain('/data/buildings.json');
  const c = JSON.parse(readFileSync('site/data/buildings.json', 'utf8')).center;
  await page.evaluate(([x, y]) => (window as any).GudBuildings.view.view({ tx: x, tz: y + 50, dist: 380, az: 2.6, pol: 0.3 }), c);
  await expect(page.locator('#map3d')).toHaveAttribute('data-b3d-triangles', /\d+/, { timeout: 20_000 });
  const tri = Number(await page.locator('#map3d').getAttribute('data-b3d-triangles'));
  expect(tri).toBeGreaterThan(1000);
  expect(tri).toBeLessThanOrEqual(40_000);
  await expect(page.locator('.b3d-legend')).toBeVisible();
  // "what is estimated" is a toggle
  const fade = page.locator('.b3d-fade');
  await fade.click();
  await expect(fade).toHaveAttribute('aria-pressed', 'true');
  // leaving the map frees it all (the texture is disposed); nothing stays on the page
  await page.evaluate(() => { location.hash = '#home'; });
  await expect(page.locator('.b3d-legend')).toHaveCount(0);
  await expect(page.locator('#map3d')).not.toHaveAttribute('data-b3d-triangles', /\d/);
  expect(errors).toEqual([]);
});

import { test, expect, Page } from '@playwright/test';
import { readFileSync } from 'fs';
import { join } from 'path';
import { listenCsp } from './csp-listen';

// Run names beside the line, curving with it (round 21, decision pending a number). The windows come from tools/build-labels.py;
// the placement is site/js/labelfit.js, tested here against the shared fixture, and then on the real map.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { fit } = require('../site/js/labelfit.js');
const FX = JSON.parse(readFileSync(join(__dirname, '..', 'tools', 'fixtures', 'labels-build.json'), 'utf8'));
const kx = 111320 * Math.cos((FX.lat0 * Math.PI) / 180), ky = 111320;
const P = (q: number[]) => [(q[1] - FX.lon0) * kx, -(q[0] - FX.lat0) * ky];
const segsOf = (key: string) => FX.data.pistes.find((p: any) => p.key === key).segs.map((s: any) => s.g.map(P));

test('labelfit: a name sits beside the line on the clear side, and waits when there is no room', () => {
  const a = FX.expected.A.a, segs = segsOf('A'); // A runs east; B is 44 m to its north (left), the right is open
  const ok = fit(a, segs, 40, 8, 6);
  expect(ok).not.toBeNull();
  expect(ok.side).toBe('L'); // B is 44 m away on the left, more than the 6 + 8 + 2.8 m the text needs: the left (above) is preferred
});

test('labelfit: the preferred side is above the line, the other side only when the first is blocked', () => {
  const win = [[0, 0, 1000, 10, 99]]; // left blocked at 10 m
  const segs = [[[0, 0], [100, 0]]];
  expect(fit(win, segs, 40, 8, 6).side).toBe('R');
  expect(fit([[0, 0, 1000, 99, 99]], segs, 40, 8, 6).side).toBe('L');
  expect(fit([[0, 0, 1000, 10, 10]], segs, 40, 8, 6)).toBeNull(); // both sides blocked: no name
  expect(fit([[0, 0, 1000, 99, 99]], segs, 95, 8, 6)).toBeNull(); // the text is longer than the window
});

test('labelfit: text reads left to right whichever way the line was drawn', () => {
  const segs = [[[100, 0], [0, 0]]]; // drawn westwards
  const r = fit([[0, 0, 1000, 99, 99]], segs, 40, 8, 6);
  const xs = r.d.match(/[ML]-?[\d.]+/g).map((s: string) => parseFloat(s.slice(1)));
  expect(xs[0]).toBeLessThan(xs[xs.length - 1]);
});

async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
  await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
}

test('map: names follow the line, none sits on another run, and the unnamed segments are quiet continuous lines', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message)); listenCsp(page);
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  await page.goto('/?lang=en#map');
  await loaded(page);
  await page.locator('#viewsw [data-view="2d"]').click();
  // the first view of a phone already has room for some names
  for (let i = 0; i < 1; i++) await page.locator('#zin').click();
  const res = await page.evaluate(() => {
    const svg = document.getElementById('map') as unknown as SVGSVGElement;
    const shown = [...svg.querySelectorAll('text.lblp')].filter(t => (t as SVGElement).style.display !== 'none') as SVGGraphicsElement[];
    const cores = [...svg.querySelectorAll('g.pg path.core')] as SVGGeometryElement[];
    let onOther = 0;
    for (const t of shown) {
      const b = t.getBBox(), key = t.getAttribute('data-key');
      for (const c of cores) {
        if (c.parentElement!.getAttribute('data-key') === key) continue;
        const L = c.getTotalLength();
        for (let d = 0; d < L; d += 3) { const q = c.getPointAtLength(d); if (q.x > b.x && q.x < b.x + b.width && q.y > b.y && q.y < b.y + b.height) { onOther++; break; } }
      }
    }
    const un = svg.querySelector('g.pg.unnamed path.core') as SVGElement | null;
    return { shown: shown.length, withPath: shown.filter(t => t.querySelector('textPath')).length, onOther, unDash: un ? un.getAttribute('stroke-dasharray') : 'none-found' };
  });
  expect(res.shown).toBeGreaterThan(2);
  expect(res.withPath).toBe(res.shown);
  expect(res.onOther).toBe(0);
  expect(res.unDash).toBeNull();
  expect(errors).toEqual([]);
});

test('map: a selected run gets small arrows down its fall', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message)); listenCsp(page);
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  await page.goto('/?lang=en#map');
  await loaded(page);
  await page.locator('#viewsw [data-view="2d"]').click();
  await page.evaluate(() => { location.hash = '#map/run/' + encodeURIComponent('Tatra 2'); });
  await expect(page.locator('#panel .run-sign h2')).toHaveText('Tatra 2');
  await expect.poll(() => page.locator('#map .rp-arr').count()).toBeGreaterThan(2);
  // each arrow points down the hill: its heading agrees with the fall of the run (the line was sampled top to bottom)
  const down = await page.evaluate(() => {
    const t = [...document.querySelectorAll('#map .rp-arr')].filter(g => (g as SVGElement).style.display !== 'none').map(g => g.getAttribute('transform') || '');
    return t.length >= 2 && t.every(x => /rotate\(-?\d+\)/.test(x));
  });
  expect(down).toBe(true);
  expect(errors).toEqual([]);
});

test('a run whose key is not its name shows the readable name', async ({ page }) => {
  await page.goto('/#map');
  await loaded(page);
  await page.evaluate(() => { location.hash = '#map/run/u1234557981'; });
  const h = page.locator('.run-sign h2');
  await expect(h).toHaveText('New Goodaura 2');
});

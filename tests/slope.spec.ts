import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'fs';
import { join } from 'path';

// X-3 (docs/ARCHITECTURE.md): one rule for how steep, on the site and in the apps. The values on Tatra 2 are in
// tools/fixtures/slope-x3.json, written from this site's own code; the apps' unit tests compare to the same file.
// To write it again after a deliberate change: SLOPE_FIXTURE=write npm run test:quick -- tests/slope.spec.ts
const FILE = join(__dirname, '..', 'tools', 'fixtures', 'slope-x3.json');

test('השיפוע לפי כלל אחד (X-3): קו המסלול ופני השטח של Tatra 2', async ({ page }) => {
  await page.goto('/#home');
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  const got = await page.evaluate(async () => {
    const R = (window as any).GudRelief;
    const [D, T] = await Promise.all(['data/runs-and-lifts.json', 'data/terrain.json'].map(u => fetch(u).then(r => r.json())));
    const M = R.load(T);
    // the site's projection and sampling (js/app.js: P, sampleLine every 10 m, the run's longest line from the top)
    const kx = 111320 * Math.cos(42.51 * Math.PI / 180), ky = 111320;
    const P = ([la, lo]: number[]) => [(lo - 44.495) * kx, -(la - 42.51) * ky];
    const run = D.pistes.find((p: any) => p.key === 'Tatra 2');
    const lines = run.segs.filter((s: any) => !s.area).map((s: any) => {
      const L = s.g.map(P); return M.elev(L[0][0], L[0][1]) < M.elev(L[L.length - 1][0], L[L.length - 1][1]) ? L.reverse() : L; });
    const sample = (L: number[][]) => { const o: any[] = [{ x: L[0][0], y: L[0][1], d: 0 }]; let d = 0;
      for (let i = 1; i < L.length; i++) { const a = L[i - 1], b = L[i], l = Math.hypot(b[0] - a[0], b[1] - a[1]), n = Math.max(1, Math.round(l / 10));
        for (let k = 1; k <= n; k++) { const t = k / n; o.push({ x: a[0] + (b[0] - a[0]) * t, y: a[1] + (b[1] - a[1]) * t, d: d + l * t }); } d += l; }
      return o; };
    const S = lines.map(sample).sort((a: any, b: any) => b[b.length - 1].d - a[a.length - 1].d)[0];
    const cum = S.map((q: any) => q.d), hs = S.map((q: any) => M.elev(q.x, q.y));
    const r2 = (v: number) => Math.round(v * 100) / 100;
    const at = [20, 80, 150].map(i => Math.min(i, S.length - 1));
    return {
      run: 'Tatra 2', half: R.SLOPE_HALF, step: 10,
      line: at.map(i => ({ i, x: r2(S[i].x), y: r2(S[i].y), d: r2(S[i].d), deg: r2(R.lineSlope(cum, hs, i)) })),
      ground: at.map(i => ({ x: r2(S[i].x), y: r2(S[i].y), deg: r2(R.groundSlope(M, S[i].x, S[i].y)) })),
    };
  });
  if (process.env.SLOPE_FIXTURE === 'write') writeFileSync(FILE, JSON.stringify(got, null, 1) + '\n');
  expect(got).toEqual(JSON.parse(readFileSync(FILE, 'utf8')));
  expect(got.half).toBe(20);
});

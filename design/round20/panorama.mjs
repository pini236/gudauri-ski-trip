// Round 20, point 3: a map that feels like a resort's panorama trail map, from our own data only. The official MTA map
// is a protected illustration that bends the mountain on purpose; it is not copied, traced or stored (accuracy rule 2).
// What changes here: a lower camera from the village side, heights exaggerated (x1.2 above the valley floor), a low
// winter sun with long shadows, and a wider frame. Every line, lift and label stays where the data puts it.
// Drawn with the site's own GudRelief.View3D on a scaled copy of the elevation model, in a layer over the real map page.
// Run from the repo root with the site served on 4199:  node design/round20/panorama.mjs [out]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const OUT = (process.argv[2] || path.join(os.tmpdir(), 'gud-r20')) + '/';
fs.mkdirSync(OUT, { recursive: true });
const SHOTS = [
  // name, viewport, exaggeration, camera (null = the site's view today), sun
  ['pano-today-desk', 1280, 800, 1, null],
  ['pano-today-phone', 390, 844, 1, null],
  ['pano-desk', 1280, 800, 1.2, { dist: 8800, az: 0.08, pol: 0.36, dz: 700 }, { alt: 30, az: 200 }],
  ['pano-phone', 390, 844, 1.2, { dist: 11000, az: 0.08, pol: 0.4, dz: 700 }, { alt: 30, az: 200 }],
  ['pano-west-desk', 1280, 800, 1.2, { dist: 8800, az: -0.5, pol: 0.36, dz: 700 }, { alt: 30, az: 200 }],
];

const b = await launch();
for (const [name, w, h, ex, cam, sun] of SHOTS) {
  const { ctx, page } = await open(b, '/#map', { w, h, mobile: w < 700, wait: 300, now: '2027-01-12T10:30:00+04:00' });
  await page.waitForFunction(() => window.THREE && document.querySelector('#m3 canvas'), null, { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(2500);
  if (cam) {
    await page.evaluate(async ([ex, cam, sun]) => {
      const t = await (await fetch('data/terrain.json')).json(), D = await (await fetch('data/runs-and-lifts.json')).json();
      const M = GudRelief.load(t);
      let base = 1e9; for (const v of M.H) if (v > 0 && v < base) base = v;
      for (let i = 0; i < M.H.length; i++) M.H[i] = Math.round(base + (M.H[i] - base) * ex);
      const lat0 = 42.51, lon0 = 44.495, kx = 111320 * Math.cos(lat0 * Math.PI / 180), ky = 111320;
      const P = ([la, lo]) => [(lo - lon0) * kx, -(la - lat0) * ky];
      const xs = [], ys = []; D.lifts.forEach(l => (l.g || []).forEach(q => { const [x, y] = P(q); xs.push(x); ys.push(y); }));
      const cx = (Math.min(...xs) + Math.max(...xs)) / 2, cy = (Math.min(...ys) + Math.max(...ys)) / 2;
      const host = document.createElement('div');
      host.style.cssText = 'position:fixed;inset:0;z-index:9999;background:#cfdbe6';
      document.body.appendChild(host);
      const v = GudRelief.View3D({ model: M, host, pistes: D.pistes, lifts: D.lifts, P, dispName: p => p.named ? p.key : '',
        colors: { green: '#1B8A4C', blue: '#1F5FC4', red: '#D1342B', black: '#13233A' }, liftColor: '#3A4556', center: [cx, cy + cam.dz], homeDist: cam.dist, homeAz: cam.az, homePol: cam.pol, onPick() {}, onLift() {} });
      v.setLight({ alt: sun.alt, az: sun.az, dark: false, sky: ['#9fc0e0', '#e9f0f6'] });
      v.view({ tx: cx, tz: cy + cam.dz, dist: cam.dist, az: cam.az, pol: cam.pol });
      v.resize();
    }, [ex, cam, sun]);
    await page.waitForTimeout(3500);
  }
  await page.screenshot({ path: OUT + name + '.png' });
  console.log(name, page._errors.length ? 'errors: ' + page._errors.slice(0, 2).join(' | ') : '');
  await ctx.close();
}
await b.close();

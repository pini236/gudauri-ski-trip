// Sella Ronda on the real site, for the self-check of the new-resort process (.claude/skills/new-resort, step 4).
// node design/sellaronda/shots.mjs  (site on :4173 by default, SITE=...)  -> design/sellaronda/built/
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const OUT = new URL('./built/', import.meta.url).pathname; fs.mkdirSync(OUT, { recursive: true });
const D2 = { 'gud-view': '2d' }, D3 = { 'gud-view': '3d' };
const only = process.argv[2];
const S = [
  ['home', '/?resort=sellaronda', {}],
  ['map', '/?resort=sellaronda#map', { extraStorage: D2 }],
  ['map-night', '/?resort=sellaronda#map', { extraStorage: D2, theme: 'night' }],
  ['3d', '/?resort=sellaronda#map', { extraStorage: D3, wait: 9000 }],
  ['run-saslong', '/?resort=sellaronda#map/run/Saslong', { extraStorage: D2 }],
  ['run-granrisa', '/?resort=sellaronda#map/run/Gran%20Risa', { extraStorage: D2 }],
  ['desk-map', '/?resort=sellaronda#map', { extraStorage: D2, w: 1280, h: 800 }],
  ['desk-3d', '/?resort=sellaronda#map', { extraStorage: D3, w: 1280, h: 800, wait: 9000 }],
  ['desk-home', '/?resort=sellaronda', { w: 1280, h: 800 }],
];
const browser = await launch();
for (const [name, url, o] of S) {
  if (only && !name.includes(only)) continue;
  const { ctx, page } = await open(browser, url, { ...o, site: process.env.SITE || 'http://127.0.0.1:4173', wait: o.wait || 4000 });
  await page.screenshot({ path: OUT + name + '.png' });
  console.log(name, page._errors.length ? 'ERRORS ' + page._errors.join(' | ') : 'ok');
  await ctx.close();
}
await browser.close();

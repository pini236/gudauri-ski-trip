// Round 27: generic name and share image, drawn over the real site. node design/round27/shots.mjs [prefix] (site on :4199)
// Home page and top bar today and with the proposed name; the real 3D map as share image option B.
import { launch, open } from '../round16/lib.mjs';
const HERE = new URL('./', import.meta.url).pathname, OUT = HERE + 'shots/';
const NOW = '2026-10-25T11:00:00+03:00', DESK = { w: 1280, h: 800 };
// brand: the top bar name; when: the line under "Gudauri, Georgia" on the phone home (null hides it)
const NAME = (brand, when) => ({ brand, when });
const S = [
  ['today-ph', null, {}], ['today-desk', null, DESK], ['today-desk-en', null, { ...DESK, lang: 'en' }],
  ['a-ph', NAME('גודאורי', null), {}], ['a-desk', NAME('גודאורי', null), DESK], ['a-desk-en', NAME('Gudauri', null), { ...DESK, lang: 'en' }],
  ['b-ph', NAME('Gudi', 'Gudi · מפת הסקי'), {}], ['b-desk', NAME('Gudi', null), DESK],
];
const only = process.argv[2];
const b = await launch();
for (const [n, p, o] of S) {
  if (only && !n.startsWith(only)) continue;
  const { ctx, page } = await open(b, '/', { wait: 2500, now: NOW, trip: null, w: o.w, h: o.h, theme: 'day', lang: o.lang || 'he' });
  if (p) await page.evaluate(p => {
    document.querySelectorAll('.topbar .brand, .topbar .brand span').forEach(e => { if (!e.children.length) e.textContent = p.brand; });
    const w = document.querySelector('.home-top .when');
    if (w) { if (p.when) { w.textContent = p.when; w.dir = 'auto'; } else w.hidden = true; }
  }, p);
  await page.waitForTimeout(300);
  await page.screenshot({ path: OUT + n + '.png' });
  await ctx.close();
}
// option B: the real 3D mountain at the share size, no interface, with the same sign drawn on top by build (og-b.png)
if (!only || 'og-b'.startsWith(only)) {
  const { ctx, page } = await open(b, '/#map', { wait: 9000, now: '2026-10-25T12:30:00+03:00', trip: null, w: 1200, h: 630, theme: 'day', extraStorage: { 'gud-view': '3d' } });
  const r = await page.evaluate(() => { const e = [...document.querySelectorAll('#map3d, #map3d canvas, canvas')].find(x => x.offsetWidth > 300); if (!e) return null;
    const q = e.getBoundingClientRect(); return { x: q.x, y: q.y, width: q.width, height: q.height, id: e.id || e.tagName }; });
  console.log('3d', r);
  await page.screenshot({ path: OUT + 'og-b-raw.png' });
  await ctx.close();
}
await b.close();

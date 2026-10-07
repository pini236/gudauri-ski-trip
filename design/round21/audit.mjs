// Round 21 audit: the map on the site (overview) in several states, plus geometry facts about label placement.
import { launch, open } from '../round16/lib.mjs';
import fs from 'node:fs';
const OUT = new URL('./audit/', import.meta.url).pathname;
const D2 = { 'gud-view': '2d' };
const states = [
  ['phone-day-he', '/#map', { theme: 'day' }],
  ['phone-night-he', '/#map', { theme: 'night' }],
  ['phone-day-en', '/#map', { theme: 'day', lang: 'en' }],
  ['phone-day-ka', '/#map', { theme: 'day', lang: 'ka' }],
  ['desk-day-he', '/#map', { theme: 'day', w: 1280, h: 800 }],
  ['desk-night-ru', '/#map', { theme: 'night', w: 1280, h: 800, lang: 'ru' }],
  ['phone-run-he', '/#map/run/Tatra%202', { theme: 'day' }],
  ['desk-run-he', '/#map/run/Soliko%202', { theme: 'day', w: 1280, h: 800 }],
];
const b = await launch();
const zoom = process.env.ZOOM;
for (const [n, u, o] of states) {
  const { ctx, page } = await open(b, u, { ...o, extraStorage: D2, wait: 2500 });
  await page.screenshot({ path: OUT + n + '.png' });
  if (n === 'desk-day-he') {
    // zoom into the top of the mountain twice
    for (const k of [1, 2]) { await page.click('#zin'); await page.waitForTimeout(400); }
    await page.screenshot({ path: OUT + 'desk-day-he-zoom.png' });
    // facts: labels hidden, labels sitting on another run's line
    const f = await page.evaluate(() => {
      const svg = document.getElementById('map');
      const lbls = [...svg.querySelectorAll('text.lbl.pg')];
      const hits = [...svg.querySelectorAll('path.hit[data-key]')];
      const res = { total: lbls.length, hidden: lbls.filter(t => t.style.display === 'none').map(t => t.textContent), onOther: [] };
      lbls.filter(t => t.style.display !== 'none').forEach(t => {
        const r = t.getBoundingClientRect(); const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        const others = new Set();
        for (const dx of [-r.width / 2, 0, r.width / 2]) for (const dy of [0, r.height / 2]) {
          document.elementsFromPoint(cx + dx, cy + dy).forEach(e => { if (e.dataset && e.dataset.key && e.dataset.key !== t.dataset.key && e.matches('path.hit')) others.add(e.dataset.key); });
        }
        if (others.size) res.onOther.push(t.textContent + ' ← ' + [...others].join(', '));
      });
      return res;
    });
    fs.writeFileSync(OUT + 'facts-zoom.json', JSON.stringify(f, null, 1));
  }
  if (page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();

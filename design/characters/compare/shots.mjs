// Companion character (Pini's sheet 2, flat) next to real parts of the site, for the canvas only. node design/characters/compare/shots.mjs (site on :4199)
import { launch, open, MYTRIP } from '../../round16/lib.mjs';
import fs from 'node:fs';
const HERE = new URL('./', import.meta.url).pathname, OUT = HERE + 'shots/'; fs.mkdirSync(OUT, { recursive: true });
const uri = n => 'data:image/png;base64,' + fs.readFileSync(`${HERE}cut/${n}.png`).toString('base64');
const OUTLINE = 'drop-shadow(1.6px 0 0 #fff) drop-shadow(-1.6px 0 0 #fff) drop-shadow(0 1.6px 0 #fff) drop-shadow(0 -1.6px 0 #fff)';
// [name, url, options, figure, height, anchor selector, side, clip]
const S = [
  ['empty', '/', {}, 'stand', 122, '.ticket-wrap', 'left', [0, 0, 390, 440]],
  ['trip', '/', { trip: MYTRIP }, 'mug', 122, '.ticket-wrap', 'left', [0, 0, 390, 440]],
  ['red', '/#map/run/Kudebi%201', { extra: { 'gud-view': '2d' } }, 'stand', 122, '.run-sign', 'left', [0, 560, 390, 284]],
  ['green', '/#map/run/Bombora', { extra: { 'gud-view': '2d' } }, 'laugh', 122, '.run-sign', 'left', [0, 560, 390, 284]],
  ['signs', '/', {}, 'stand', 140, '.post', 'left', [0, 560, 390, 500]],
];
const b = await launch();
for (const [n, url, o, fig, h, sel, side, clip] of S) for (const theme of ['day', 'night']) for (const v of theme === 'night' ? ['raw', 'outline'] : ['raw']) {
  const { ctx, page } = await open(b, url, { theme, trip: o.trip, extraStorage: o.extra, wait: n === 'red' || n === 'green' ? 2500 : 1500, dsf: 2 });
  await page.evaluate(([src, h, sel, side, filter]) => {
    const a = document.querySelector(sel), q = a.getBoundingClientRect();
    const img = new Image(); img.src = src; img.style.cssText = `position:absolute;height:${h}px;z-index:50;pointer-events:none;${filter ? 'filter:' + filter : ''}`;
    document.body.appendChild(img);
    return img.decode().then(() => { const w = img.naturalWidth / img.naturalHeight * h; img.style.left = (side === 'left' ? q.left + 4 : q.right - w - 4) + 'px';
      const bottom = sel === '.post' ? q.bottom - 8 : sel === '.run-sign' ? q.bottom + 4 : q.top + 6; img.style.top = (bottom + scrollY - h) + 'px'; });
  }, [uri(fig), h, sel, side, v === 'outline' ? OUTLINE : '']);
  await page.waitForTimeout(200);
  await page.screenshot({ path: `${OUT}${n}-${theme}${v === 'outline' ? '-outline' : ''}.png`, fullPage: true, clip: { x: clip[0], y: clip[1], width: clip[2], height: clip[3] } });
  if (page._errors.length) console.log(n, page._errors);
  await ctx.close();
}
await b.close();

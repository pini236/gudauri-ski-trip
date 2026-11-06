// Round 35 (aligning Android with the iPhone direction): "before" is the real emulator screenshot (qa.zip of the android-qa release),
// "after" is a MOCK drawn on the same screenshot (rounded sheet corners, floating drawer); not an implementation.
// QA=<dir with extracted shots/>  node design/round35/shots.mjs   ->  design/round35/shots/m-*.png
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const QA = process.env.QA, OUT = path.resolve('design/round35/shots/');
const SRC = {
  run: ['run-02-run-panel', 786], lift: ['run-08-lift-panel', 1026],
  langday: ['store,lang-02-lang-he-open', 1140], langnight: ['store,lang-07-lang-en-open-night', 1140], cal: ['home-03-trip-calendar', 1126],
};
const jobs = [
  ['run', 'before'], ['run', 'round'], ['run', 'float-full'], ['run', 'float-half'], ['run', 'float-peek'],
  ['lift', 'before'], ['lift', 'round'], ['lift', 'float-full'],
  ['langday', 'before'], ['langday', 'round'], ['langnight', 'before'], ['langnight', 'round'], ['cal', 'before'], ['cal', 'round'],
];
const br = await chromium.launch(); const pg = await br.newPage({ viewport: { width: 1080, height: 2400 } });
await pg.setContent('<canvas id=c width=1080 height=2400></canvas>');
for (const [k, mode] of jobs) {
  const [file, top] = SRC[k];
  const b64 = fs.readFileSync(path.join(QA, 'shots', file + '.png')).toString('base64');
  const png = await pg.evaluate(async ([b64, top, mode]) => {
    const img = new Image(); img.src = 'data:image/png;base64,' + b64; await img.decode();
    const c = document.getElementById('c'), x = c.getContext('2d'); x.clearRect(0, 0, 1080, 2400); x.drawImage(img, 0, 0);
    const R = 60;
    if (mode === 'round') {
      const id = x.getImageData(0, top - 1, 1080, 1).data;
      for (let yy = 0; yy < R; yy++) for (let xx = 0; xx < R; xx++) {
        if (Math.hypot(R - xx - 0.5, R - yy - 0.5) <= R) continue;
        for (const px of [xx, 1079 - xx]) { const o = px * 4; x.fillStyle = `rgb(${id[o]},${id[o + 1]},${id[o + 2]})`; x.fillRect(px, top + yy, 1, 1); }
      }
    } else if (mode.startsWith('float')) {
      const kind = mode.slice(6), s = 936 / 1080, bottom = 2304;
      const rows = kind === 'full' ? 2400 - 70 - top : kind === 'half' ? 1100 : 740;
      const ch = Math.round(rows * s), cardTop = bottom - ch;
      // the map under the old sheet: the strip above it, mirrored (the real map would continue)
      x.save(); x.filter = 'blur(28px)'; x.drawImage(img, -40, top - 140, 1160, 140, -40, top, 1160, 2400 - top + 40); x.restore();
      x.save(); x.shadowColor = 'rgba(19,35,58,.35)'; x.shadowBlur = 48; x.shadowOffsetY = 12;
      x.beginPath(); x.roundRect(72, cardTop, 936, ch, R); x.fillStyle = '#fff'; x.fill(); x.restore();
      x.save(); x.beginPath(); x.roundRect(72, cardTop, 936, ch, R); x.clip();
      x.drawImage(img, 0, top, 1080, rows, 72, cardTop, 936, ch); x.restore();
    }
    return c.toDataURL('image/png').split(',')[1];
  }, [b64, top, mode]);
  fs.writeFileSync(path.join(OUT, `m-${k}-${mode}.png`), Buffer.from(png, 'base64'));
}
await br.close(); console.log('done', jobs.length);

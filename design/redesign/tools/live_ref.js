// Screenshots of the live-model reference (live_ref.html). Serve the repo root first:
//   python3 -m http.server 8765        (from the repo root)
//   node design/redesign/tools/live_ref.js <out dir> name=query [name=query ...]
// query: light=<dawn|morning|noon|after|sunset|blue|night> or h=<hour>; camera: tx ty tz dist yaw pitch (degrees);
//        w hh dpr; detail=0 (no detail textures); far=0 (no far horizon); runs=0
const { chromium } = require('playwright');
const path = require('path'), fs = require('fs');
(async () => {
  const [out, ...shots] = process.argv.slice(2);
  fs.mkdirSync(out, { recursive: true });
  const exe = '/opt/pw-browsers/chromium';
  const browser = await chromium.launch(Object.assign({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] }, fs.existsSync(exe) ? { executablePath: exe } : {}));
  for (const s of shots) {
    const i = s.indexOf('='), name = s.slice(0, i), query = s.slice(i + 1), p = new URLSearchParams(query);
    const w = +(p.get('w') || 390), h = +(p.get('hh') || 844);
    const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') console.log(name, m.text().slice(0, 400)); });
    page.on('pageerror', e => console.log(name, 'ERR', String(e).slice(0, 400)));
    await page.goto('http://localhost:8765/design/redesign/tools/live_ref.html?' + query);
    await page.waitForFunction('window.done === true', null, { timeout: 180000 });
    const data = await page.evaluate(() => document.querySelector('canvas').toDataURL('image/jpeg', 0.9));
    fs.writeFileSync(path.join(out, name + '.jpg'), Buffer.from(data.split(',')[1], 'base64'));
    console.log(name); await page.close();
  }
  await browser.close();
})();

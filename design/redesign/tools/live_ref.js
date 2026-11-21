// Screenshots of the live-model reference (live_ref.html). Serve the repo root first:
//   python3 -m http.server 8765        (from the repo root)
//   node design/redesign/tools/live_ref.js <out dir> name=query [name=query ...]
// or, without a server (the pages are answered from the files of the repo):  FROM_DISK=1 node design/redesign/tools/live_ref.js ...
// query: light=<dawn|morning|noon|after|sunset|blue|night> or h=<hour>; camera: tx ty tz dist yaw pitch (degrees);
//        w hh dpr; detail=0 (no detail textures); far=0 (no far horizon); runs=0
//        sel=<run key> (the chosen run as a line of light over a dimmed screen); dim=<0..1>; selw=<width factor>
const { chromium } = require('playwright');
const path = require('path'), fs = require('fs');
(async () => {
  const [out, ...shots] = process.argv.slice(2);
  fs.mkdirSync(out, { recursive: true });
  const root = path.resolve(__dirname, '..', '..', '..'), types = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.png': 'image/png' };
  const exe = '/opt/pw-browsers/chromium';
  const browser = await chromium.launch(Object.assign({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] }, fs.existsSync(exe) ? { executablePath: exe } : {}));
  for (const s of shots) {
    const i = s.indexOf('='), name = s.slice(0, i), query = s.slice(i + 1), p = new URLSearchParams(query);
    const w = +(p.get('w') || 390), h = +(p.get('hh') || 844);
    const page = await browser.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') console.log(name, m.text().slice(0, 400)); });
    page.on('pageerror', e => console.log(name, 'ERR', String(e).slice(0, 400)));
    if (process.env.FROM_DISK) await page.route('http://localhost:8765/**', route => {   // no server: answer from the files of the repo
      const f = path.join(root, decodeURIComponent(new URL(route.request().url()).pathname));
      if (!f.startsWith(root + path.sep) || !fs.existsSync(f)) return route.fulfill({ status: 404, body: '' });
      route.fulfill({ status: 200, contentType: types[path.extname(f)] || 'application/octet-stream', body: fs.readFileSync(f) }); });
    await page.goto('http://localhost:8765/design/redesign/tools/live_ref.html?' + query);
    await page.waitForFunction('window.done === true', null, { timeout: 180000 });
    const data = await page.evaluate(() => document.querySelector('canvas').toDataURL('image/jpeg', 0.9));
    fs.writeFileSync(path.join(out, name + '.jpg'), Buffer.from(data.split(',')[1], 'base64'));
    console.log(name); await page.close();
  }
  await browser.close();
})();

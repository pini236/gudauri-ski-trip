// Renders one SVG to PNG with the repo's Playwright and the preinstalled Chromium (used by export.py).
// node design/round9/render.js in.svg out.png width height transparent(0|1)
const { chromium } = require('../../node_modules/playwright-core');
const fs = require('fs');
(async () => {
  const [, , src, out, w, h, transparent] = process.argv;
  const b = await chromium.launch({ executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium', args: ['--no-sandbox', '--allow-file-access-from-files'] });
  const p = await b.newPage({ viewport: { width: +w, height: +h } });
  const html = '<!doctype html><body style="margin:0;background:transparent">' + fs.readFileSync(src, 'utf8') + '</body>';
  fs.writeFileSync(src + '.html', html);
  await p.goto('file://' + require('path').resolve(src + '.html'));
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(150);
  await p.screenshot({ path: out, omitBackground: transparent === '1', clip: { x: 0, y: 0, width: +w, height: +h } });
  fs.unlinkSync(src + '.html');
  await b.close();
})();

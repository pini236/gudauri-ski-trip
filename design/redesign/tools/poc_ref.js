// Screenshots of the reference frames (see poc_ref.py). Usage: node design/redesign/tools/poc_ref.js
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const dir = path.join(__dirname, '../../handoff/2026-11-20-redesign-poc/ref');
  const fs = require('fs');
  const exe = '/opt/pw-browsers/chromium';   // the cloud environment's browser, when the project's Playwright pins another build
  const browser = await chromium.launch(fs.existsSync(exe) ? { executablePath: exe } : {});
  const page = await browser.newPage({ viewport: { width: 1300, height: 1900 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.join(dir, 'ref.html'));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(800);
  for (const el of await page.$$('.f')) {
    const id = await el.getAttribute('id');
    await el.screenshot({ path: path.join(dir, id + '.jpg'), type: 'jpeg', quality: 82 });
    console.log(id);
  }
  await browser.close();
})();

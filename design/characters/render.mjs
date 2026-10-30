// Renders design/characters/boards/*.html to shots/*.png (the boards size themselves). Run from the repo root.
import { chromium } from 'playwright';
import { readdirSync, mkdirSync } from 'fs';
import { join, resolve } from 'path';

const here = resolve('design/characters');
mkdirSync(join(here, 'shots'), { recursive: true });
const browser = await chromium.launch({ executablePath: process.env.CHROME || '/opt/pw-browsers/chromium' });
const page = await browser.newPage({ deviceScaleFactor: 2 });
for (const f of readdirSync(join(here, 'boards')).filter(f => f.endsWith('.html')).sort()) {
  await page.goto('file://' + join(here, 'boards', f));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  const { w, h } = await page.evaluate(() => ({ w: document.body.scrollWidth, h: document.body.scrollHeight }));
  await page.setViewportSize({ width: w, height: h });
  await page.screenshot({ path: join(here, 'shots', f.replace('.html', '.png')), fullPage: true });
  console.log(f, w, h);
}
await browser.close();

// Round 15: the privacy page in four languages (decision 46). The real page before (main) and after (this branch).
// Run from the repo root:
//   npx http-server <a copy of main's site> -p 4198 -s &   npx http-server site -p 4199 -s &   node design/round15/shots.mjs [out dir]
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname);
const out = process.argv[2] || path.join(here, 'shots');
fs.mkdirSync(out, { recursive: true });
const font = f => fs.readFileSync(path.join(here, '../../app/android/app/src/main/res/font', f)).toString('base64');
const FONTS = [['Karantina', '700', 'karantina_bold.ttf'], ['IBM Plex Sans Hebrew', '400 500', 'plex_hebrew_regular.ttf'], ['IBM Plex Sans Hebrew', '600 700', 'plex_hebrew_bold.ttf'],
  ['IBM Plex Sans', '400 700', 'plex_sans.ttf'], ['Oswald', '400 700', 'oswald.ttf'], ['Noto Sans Georgian', '400 800', 'noto_sans_georgian.ttf']]
  .map(([fam, w, f]) => `@font-face{font-family:'${fam}';font-weight:${w};font-display:block;src:url(data:font/ttf;base64,${font(f)}) format('truetype')}`).join('\n');

const b = await chromium.launch();
async function shot(site, hash, name, w = 390, h = 844) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, locale: 'he-IL', timezoneId: 'Asia/Jerusalem', reducedMotion: 'reduce' });
  await ctx.route('**/fonts.googleapis.com/**', r => r.fulfill({ contentType: 'text/css', body: FONTS }));
  await ctx.route('**/fonts.gstatic.com/**', r => r.abort());
  await ctx.route(/posthog|sentry/, r => r.abort());
  const p = await ctx.newPage();
  await p.goto(site + '/privacy.html' + hash);
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(400);
  await p.screenshot({ path: path.join(out, name + '.png') });
  console.log(name);
  await ctx.close();
}
await shot('http://127.0.0.1:4198', '', 'P0-Before');
for (const [l, n] of [['', 'P1-He'], ['#en', 'P2-En'], ['#ru', 'P3-Ru'], ['#ka', 'P4-Ka']]) await shot('http://127.0.0.1:4199', l, n);
await shot('http://127.0.0.1:4199', '#ru', 'P5-RuDesktop', 1280, 800);
await b.close();

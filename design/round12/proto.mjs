// Round 12: the site's own "your trip", sign-in, account and group screens (stage 13.9, decision 18: canvas first).
// The screens are drawn inside the real site: it loads site/, plugs the new pieces in with the site's own classes
// (plus the few new ones in proto.css), and takes a screenshot of each. Run from the repo root:
//   python3 -m http.server 4199 -d site &   node design/round12/proto.mjs [out dir]
// Sample data: the trip is Pini's own (site/data/trip.json); the group's second flight and the code are made up.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname);
const out = process.argv[2] || path.join(here, 'shots');
fs.mkdirSync(out, { recursive: true });
const css = fs.readFileSync(path.join(here, 'proto.css'), 'utf8');
const lib = fs.readFileSync(path.join(here, 'proto-lib.js'), 'utf8');
// Google Fonts may be out of reach (the cloud sandbox): serve the same fonts from the copies packed in the app
const font = f => fs.readFileSync(path.join(here, '../../app/android/app/src/main/res/font', f)).toString('base64');
const FONTS = [['Karantina', 700, 'karantina_bold.ttf'], ['IBM Plex Sans Hebrew', 400, 'plex_hebrew_regular.ttf'],
  ['IBM Plex Sans Hebrew', 500, 'plex_hebrew_regular.ttf'], ['IBM Plex Sans Hebrew', 600, 'plex_hebrew_bold.ttf'], ['IBM Plex Sans Hebrew', 700, 'plex_hebrew_bold.ttf']]
  .map(([fam, w, f]) => `@font-face{font-family:'${fam}';font-weight:${w};font-display:block;src:url(data:font/ttf;base64,${font(f)}) format('truetype')}`).join('\n');

const SCREENS = [
  // [file, width, height (0 = whole page), theme, screen function in proto-lib.js]
  ['W1-GuestHome', 390, 0, 'day', 'guestHome'],
  ['W2-TripForm', 390, 0, 'day', 'tripForm'],
  ['W3-TripHome', 390, 0, 'day', 'tripHome'],
  ['W4-SignIn', 390, 844, 'day', 'signIn'],
  ['W5-Account', 390, 0, 'day', 'account'],
  ['W6-DeleteAccount', 390, 0, 'day', 'deleteAccount'],
  ['W7-Join', 390, 844, 'day', 'join'],
  ['W8-Group', 390, 0, 'day', 'group'],
  ['W9-TripHomeDesktop', 1280, 0, 'day', 'tripHome'],
  ['W10-GroupDesktop', 1280, 0, 'day', 'group'],
  ['W11-TripHomeNight', 390, 0, 'night', 'tripHome'],
];

const b = await chromium.launch();
for (const [name, w, h, theme, fn] of SCREENS) {
  const ctx = await b.newContext({ viewport: { width: w, height: h || 844 }, locale: 'he-IL', timezoneId: 'Asia/Tbilisi', reducedMotion: 'reduce' });
  await ctx.addInitScript(t => { try { localStorage.setItem('gud-lang', 'he'); localStorage.setItem('gud-daynight', t); } catch (e) {} }, theme);
  await ctx.route('**/fonts.googleapis.com/**', r => r.fulfill({ contentType: 'text/css', body: FONTS }));
  await ctx.route('**/fonts.gstatic.com/**', r => r.abort());
  const p = await ctx.newPage();
  await p.goto('http://127.0.0.1:4199/#home');
  await p.waitForSelector('#loading', { state: 'hidden' }).catch(() => {});
  await p.evaluate(() => document.fonts.ready);
  await p.waitForTimeout(1500);
  await p.addStyleTag({ content: css });
  await p.evaluate(`${lib}\n;${fn}();`);
  await p.waitForTimeout(600);
  await p.screenshot({ path: path.join(out, name + '.png'), fullPage: !h });
  await ctx.close();
  console.log(name);
}
await b.close();

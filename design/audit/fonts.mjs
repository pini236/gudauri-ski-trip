// Design language audit, fonts: for every visible text on every page state, in four languages and two widths, the
// fonts Chromium actually rendered (CDP CSS.getPlatformFontsForNode), the computed family, weight and stretch, and
// the font faces the page loaded. Writes fonts.jsonl (one record per element) and faces.json to $OUT (default:
// gud-audit in the system temp folder), never into the repo. GAMES=1 runs the five games instead of the site.
import { launch, open, MYTRIP } from '../round16/lib.mjs';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
const OUT = (process.env.OUT || path.join(os.tmpdir(), 'gud-audit')) + '/'; fs.mkdirSync(OUT, { recursive: true });
const LIVE = { updated: new Date(Date.now() - 6 * 60000).toISOString(), lifts: { 'Snow Park': { open: true }, 'Pirveli': { open: true }, 'Sadzele': { open: false, reason: 'wind' }, 'Khada': { open: true }, 'Goodaura': { open: true } }, pistes: {} };
const D2 = { 'gud-view': '2d' };
const click = sel => async p => { await p.locator(sel).first().click().catch(() => {}); await p.waitForTimeout(700); };
const STATES = [
  ['home', '/#home', { trip: MYTRIP }],
  ['home-guest', '/#home', { trip: null }],
  ['map-run', '/#map/run/Tatra%202', { extraStorage: D2, wait: 2500 }],
  ['map-board', '/#map', { status: LIVE, extraStorage: D2, wait: 1800 }, click('.mstat')],
  ['meet', '/#meet', { trip: MYTRIP }, click('[data-pre="am"]')],
  ['games', '/#games', {}],
  ['about', '/#about', { signed: true }],
  ['trip', '/#trip', { trip: MYTRIP }],
  ['signin', '/#signin', {}],
  ['account', '/#account', { signed: true }],
  ['group', '/#group/g1', { signed: true, trip: MYTRIP, wait: 1500 }],
  ['join', '/#join/KZBQRM', { wait: 1500 }],
  ['delete', '/#account/delete', {}],
  ['privacy', '/privacy.html', {}],
];
const GAMES = [
  ['g-descent', '/games/descent/', { wait: 1500 }],
  ['g-school', '/games/school/', { wait: 1500 }],
  ['g-fresh', '/games/fresh-snow/', { wait: 1500 }],
  ['g-merge', '/games/merge/', { wait: 1500 }],
  ['g-snowball', '/games/snowball/', { wait: 1500 }],
];
// GAMES=1: the five games (phone width only); otherwise the site's 14 states at phone and desktop width
const RUN = process.env.GAMES ? GAMES : STATES, WIDTHS = process.env.GAMES ? [390] : [390, 1280];
const ONLY = process.env.ONLY ? new RegExp(process.env.ONLY) : null;
const out = fs.createWriteStream(OUT + 'fonts.jsonl', { flags: process.env.APPEND ? 'a' : 'w' });
const faces = {};
const b = await launch();
for (const [name, url, opts, act] of RUN) for (const lang of ['he', 'en', 'ru', 'ka']) for (const w of WIDTHS) {
  if (ONLY && !ONLY.test(name)) continue;
  const { ctx, page } = await open(b, url + (url.startsWith('/privacy') && lang !== 'he' ? '#' + lang : ''), { w, h: w < 700 ? 844 : 800, mobile: w < 700, lang, ...opts });
  try {
    if (act) await act(page);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(300);
    const n = await page.evaluate(() => {
      let i = 0;
      const vis = el => { const r = el.getBoundingClientRect(); if (!r.width || !r.height) return false; const s = getComputedStyle(el); return s.visibility !== 'hidden' && s.display !== 'none' && +s.opacity !== 0; };
      for (const el of document.querySelectorAll('body *')) {
        if (el.closest('script,style,noscript,template,.pano,.home-sky .sky-stars')) continue;
        const own = [...el.childNodes].filter(t => t.nodeType === 3 && t.textContent.trim()).map(t => t.textContent.trim()).join(' ');
        if (!own || !vis(el)) continue;
        // hidden ancestors (a closed page) make the element invisible too
        let a = el, ok = true; while (a && a !== document.body) { const s = getComputedStyle(a); if (s.display === 'none' || s.visibility === 'hidden') { ok = false; break; } a = a.parentElement; }
        if (!ok) continue;
        el.setAttribute('data-fa', i++);
      }
      return i;
    });
    const cdp = await ctx.newCDPSession(page);
    await cdp.send('DOM.enable'); await cdp.send('CSS.enable');
    const { root } = await cdp.send('DOM.getDocument', { depth: -1 });
    const { nodeIds } = await cdp.send('DOM.querySelectorAll', { nodeId: root.nodeId, selector: '[data-fa]' });
    const info = await page.evaluate(() => [...document.querySelectorAll('[data-fa]')].map(el => {
      const s = getComputedStyle(el), own = [...el.childNodes].filter(t => t.nodeType === 3 && t.textContent.trim()).map(t => t.textContent.trim()).join(' ');
      const path = []; let a = el; for (let k = 0; a && a !== document.body && k < 4; k++, a = a.parentElement) path.unshift(a.tagName.toLowerCase() + (a.id ? '#' + a.id : '') + (typeof a.className === 'string' && a.className.trim() ? '.' + a.className.trim().split(/\s+/).slice(0, 2).join('.') : (a.className && a.className.baseVal ? '.' + a.className.baseVal.split(/\s+/)[0] : '')));
      return { i: +el.getAttribute('data-fa'), path: path.join(' > '), text: own.slice(0, 40), family: s.fontFamily, weight: s.fontWeight, stretch: s.fontStretch, style: s.fontStyle, size: s.fontSize, dir: s.direction, svg: el instanceof SVGElement };
    }));
    const byI = new Map(info.map(x => [x.i, x]));
    const ids = await page.evaluate(() => [...document.querySelectorAll('[data-fa]')].map(el => +el.getAttribute('data-fa')));
    for (let k = 0; k < nodeIds.length; k++) {
      const rec = byI.get(ids[k]); if (!rec) continue;
      try { const r = await cdp.send('CSS.getPlatformFontsForNode', { nodeId: nodeIds[k] }); rec.used = r.fonts.map(f => [f.familyName, f.glyphCount, f.isCustomFont]); } catch (e) { rec.used = [['?', 0, false]]; }
      out.write(JSON.stringify({ state: name, lang, w, ...rec }) + '\n');
    }
    const fl = await page.evaluate(() => [...document.fonts].filter(f => f.status === 'loaded').map(f => [f.family.replace(/"/g, ''), f.weight, f.stretch, f.style].join('|')));
    faces[`${name}|${lang}|${w}`] = [...new Set(fl)];
    console.log(name, lang, w, n, 'texts');
  } catch (e) { console.log(name, lang, w, 'FAILED', e.message.split('\n')[0]); }
  await ctx.close();
}
out.end();
fs.writeFileSync(OUT + 'faces.json', JSON.stringify(faces, null, 1));
await b.close();

// Design language audit, components: every control, sign, card and rounded thing on every page state, with its
// look in tokens (colors named by the token they match in that theme), size, corners and type. Writes comps.jsonl to
// $OUT (default: gud-audit in the system temp folder), never into the repo.
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
  ['map', '/#map', { status: LIVE, extraStorage: D2, wait: 1800 }],
  ['map-board', '/#map', { status: LIVE, extraStorage: D2, wait: 1800 }, click('.mstat')],
  ['map-lstat', '/#map', { extraStorage: D2, wait: 1500 }, click('.mstat')],
  ['meet', '/#meet', { trip: MYTRIP }, click('[data-pre="am"]')],
  ['games', '/#games', {}],
  ['about', '/#about', { signed: true }],
  ['trip', '/#trip', { trip: MYTRIP }],
  ['signin', '/#signin', {}],
  ['account', '/#account', { signed: true }],
  ['group', '/#group/g1', { signed: true, trip: MYTRIP, wait: 1500 }],
  ['group-none', '/#group', {}],
  ['join', '/#join/KZBQRM', { wait: 1500 }],
  ['delete', '/#account/delete', {}],
  ['privacy', '/privacy.html', {}],
];
const out = fs.createWriteStream(OUT + 'comps.jsonl');
const b = await launch();
for (const [name, url, opts, act] of STATES) for (const theme of ['day', 'night']) for (const w of [390, 1280]) {
  const { ctx, page } = await open(b, url, { w, h: w < 700 ? 844 : 800, mobile: w < 700, theme, ...opts });
  try {
    if (act) await act(page);
    await page.waitForTimeout(300);
    const recs = await page.evaluate(() => {
      const root = getComputedStyle(document.documentElement);
      const names = ['snow', 'paper', 'ink', 'muted', 'rule', 'glacier', 'grid', 'lift', 'casing', 'p-green', 'p-blue', 'p-red', 'p-black', 'on-board', 'accent', 'on-accent', 'dash', 'shadow', 'ls-dark', 'mc-paper', 'mc-head', 'sn1', 'sn2', 'sn3', 'bp-paper', 'bp-paper2', 'bp-ink', 'bp-muted', 'bp-strip', 'bp-on-strip', 'bp-acc'];
      const cv = document.createElement('canvas').getContext('2d');
      const norm = c => { if (!c || c === 'transparent' || c === 'rgba(0, 0, 0, 0)') return ''; cv.fillStyle = '#000'; cv.fillStyle = c; return cv.fillStyle.toLowerCase(); };
      const tok = {}; for (const n of names) { const v = norm(root.getPropertyValue('--' + n).trim()); if (v && !(v in tok)) tok[v] = n; }
      const name = c => { const v = norm(c); return v ? (tok[v] || v) : ''; };
      const vis = el => { const r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2) return false; let a = el; while (a && a !== document.documentElement) { const s = getComputedStyle(a); if (s.display === 'none' || s.visibility === 'hidden' || +s.opacity === 0) return false; a = a.parentElement; } return true; };
      const sel = el => { const p = []; let a = el; for (let k = 0; a && a !== document.body && k < 3; k++, a = a.parentElement) p.unshift(a.tagName.toLowerCase() + (a.id ? '#' + a.id : '') + (typeof a.className === 'string' && a.className.trim() ? '.' + a.className.trim().split(/\s+/).slice(0, 3).join('.') : '')); return p.join(' > '); };
      const out = [], seen = new Set();
      const add = (el, kind) => { if (seen.has(el) || !vis(el)) return; seen.add(el); const s = getComputedStyle(el), r = el.getBoundingClientRect();
        out.push({ kind, sel: sel(el), cls: typeof el.className === 'string' ? el.className.trim().split(/\s+/)[0] || el.tagName.toLowerCase() : el.tagName.toLowerCase(), text: (el.innerText || el.value || el.getAttribute('aria-label') || '').trim().replace(/\s+/g, ' ').slice(0, 30),
          w: Math.round(r.width), h: Math.round(r.height), x: Math.round(r.left), y: Math.round(r.top + scrollY),
          radius: s.borderTopLeftRadius + ' ' + s.borderTopRightRadius, clip: s.clipPath.slice(0, 40),
          border: ['Top', 'Right', 'Bottom', 'Left'].map(k => s['border' + k + 'Width'] === '0px' ? '' : s['border' + k + 'Width'] + ' ' + s['border' + k + 'Style'] + ' ' + name(s['border' + k + 'Color'])).join('|'),
          bg: name(s.backgroundColor), fg: name(s.color), font: s.fontFamily.split(',')[0].replace(/"/g, ''), fs: s.fontSize, fw: s.fontWeight, shadow: s.boxShadow === 'none' ? '' : s.boxShadow.replace(/rgba?\([^)]*\)/, m => name(m)).slice(0, 60),
          transform: s.transform === 'none' ? '' : s.transform.slice(0, 40) }); };
      document.querySelectorAll('button,a[href],input:not([type=hidden]),select,textarea,[role=button],[role=tab],label.ab-sw,.chip,.tag').forEach(el => add(el, 'control'));
      document.querySelectorAll('body *').forEach(el => { const s = getComputedStyle(el);
        if (s.clipPath && s.clipPath.startsWith('polygon')) add(el, 'sign');
        else if (parseFloat(s.borderTopLeftRadius) > 0 && !el.closest('svg') && el.tagName !== 'IMG') add(el, 'rounded');
        else if ((parseFloat(s.borderTopWidth) >= 4 || s.boxShadow !== 'none') && name(s.backgroundColor)) add(el, 'card'); });
      return out;
    });
    for (const r of recs) out.write(JSON.stringify({ state: name, theme, w, ...r }) + '\n');
    console.log(name, theme, w, recs.length);
  } catch (e) { console.log(name, theme, w, 'FAILED', e.message.split('\n')[0]); }
  await ctx.close();
}
out.end();
await b.close();

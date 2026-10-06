// Round 20 (less text, more experience): every visible text on every page state of the site, at phone width, in
// Hebrew, with its box, words and the kind of element it sits in. Writes inventory.json and a full-page shot per
// state to $OUT (default: <tmp>/gud-r20), never into the repo. Uses the round 16 harness (fake server, made-up names).
// Run from the repo root with the site served on 4199:  node design/round20/inventory.mjs [filter]
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { launch, open, MYTRIP } from '../round16/lib.mjs';

const OUT = (process.env.OUT || path.join(os.tmpdir(), 'gud-r20')) + '/';
fs.mkdirSync(OUT + 'inv', { recursive: true });
const ONLY = process.argv[2] ? new RegExp(process.argv[2]) : null;
const J = body => ({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' }, body: JSON.stringify(body) });
const D2 = { 'gud-view': '2d' };
const LIVE = { updated: new Date(Date.now() - 6 * 60000).toISOString(), lifts: { 'Goodaura': { open: true }, 'Sadzele': { open: false, reason: 'wind' }, 'Kudebi': { open: true } }, pistes: {} };
const click = sel => async p => { await p.locator(sel).first().click().catch(() => {}); await p.waitForTimeout(700); };
const tab = t => async p => { await p.waitForSelector('#grSome:not([hidden])').catch(() => {}); if (t) await click(`#grTabs [data-tab="${t}"]`)(p); };
const STATES = [
  ['home', '/#home', { trip: MYTRIP }],
  ['home-guest', '/#home', { trip: null }],
  ['map', '/#map', { extraStorage: D2, wait: 2500 }],
  ['map-run', '/#map/run/Tatra%202', { extraStorage: D2, wait: 2500 }],
  ['map-board', '/#map', { status: LIVE, extraStorage: D2, wait: 1800 }, click('.mstat')],
  ['meet', '/#meet', { trip: MYTRIP }, click('[data-pre="am"]')],
  ['games', '/#games', {}],
  ['about', '/#about', { signed: true }],
  ['trip', '/#trip', { trip: null }],
  ['signin', '/#signin', {}],
  ['account', '/#account', { signed: true }],
  ['group-flights', '/#group/g1', { signed: true, trip: MYTRIP, wait: 1500 }, tab()],
  ['group-meetups', '/#group/g1', { signed: true, trip: MYTRIP, wait: 1500 }, tab('meetups')],
  ['group-members', '/#group/g1', { signed: true, trip: MYTRIP, wait: 1500 }, tab('members')],
  ['group-empty', '/#group', { trip: null }],
  ['join', '/#join/KZBQRM', { wait: 1500 }],
];

const b = await launch();
const all = [];
for (const [name, url, opts, act] of STATES) {
  if (ONLY && !ONLY.test(name)) continue;
  const { ctx, page } = await open(b, url, { w: 390, h: 844, now: '2026-12-01T10:00:00+04:00', ...opts });
  try {
    if (act) await act(page);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(500);
    const items = await page.evaluate(() => {
      const out = [], vis = el => { const r = el.getBoundingClientRect(); if (!r.width || !r.height) return false; let a = el; while (a && a !== document.body) { const s = getComputedStyle(a); if (s.display === 'none' || s.visibility === 'hidden' || +s.opacity === 0) return false; a = a.parentElement; } return true; };
      for (const el of document.querySelectorAll('body *')) {
        if (el.closest('script,style,noscript,template,svg,.pano,.sky-stars,.r3-labels,#map')) continue;
        const own = [...el.childNodes].filter(t => t.nodeType === 3 && t.textContent.trim()).map(t => t.textContent.replace(/\s+/g, ' ').trim()).join(' ');
        if (!own || !vis(el)) continue;
        const r = el.getBoundingClientRect(), s = getComputedStyle(el);
        const host = el.closest('button,a,label,h1,h2,h3,.chip,[role=tab],input,select,summary');
        out.push({ text: own, x: r.x, y: r.y + scrollY, w: r.width, h: r.height, size: parseFloat(s.fontSize), tag: el.tagName.toLowerCase(),
          cls: (typeof el.className === 'string' ? el.className : '').split(' ')[0], host: host ? host.tagName.toLowerCase() : '' });
      }
      return out;
    });
    const words = t => t.split(/\s+/).filter(w => /[\p{L}\p{N}]/u.test(w)).length;
    for (const it of items) it.words = words(it.text);
    all.push({ state: name, items, words: items.reduce((a, x) => a + x.words, 0), height: await page.evaluate(() => document.documentElement.scrollHeight) });
    await page.screenshot({ path: OUT + 'inv/' + name + '.png', fullPage: true });
    console.log(name, items.length, 'texts', all.at(-1).words, 'words', page._errors.length ? 'errors: ' + page._errors[0] : '');
  } catch (e) { console.log(name, 'FAILED', e.message.split('\n')[0]); }
  await ctx.close();
}
await b.close();
fs.writeFileSync(OUT + 'inventory.json', JSON.stringify(all, null, 1));

// Design language audit: crops of the findings from the real site (now), and the same crops with the proposed fix
// applied on top of the page at capture time (proposal). Nothing in site/ changes. PNGs go to $OUT/shots (default:
// gud-audit in the system temp folder); the canvas has them as WebP (PIL, quality 88), kept in design/audit/shots/.
//   NET_CACHE=... SITE=http://127.0.0.1:4199 node design/audit/shots.mjs [regex]
import { launch, open, MYTRIP } from '../round16/lib.mjs';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
const OUT = path.join(process.env.OUT || path.join(os.tmpdir(), 'gud-audit'), 'shots') + '/'; fs.mkdirSync(OUT, { recursive: true });
const ONLY = process.argv[2] ? new RegExp(process.argv[2]) : null;
const LIVE = { updated: new Date(Date.now() - 6 * 60000).toISOString(), lifts: { 'Snow Park': { open: true }, 'Pirveli': { open: true }, 'Sadzele': { open: false, reason: 'wind' }, 'Khada': { open: true }, 'Goodaura': { open: true }, 'Soliko': { open: true }, 'Kudebi': { open: false, reason: 'wind' }, 'Tatra': { open: true }, 'New Goodaura': { open: true }, 'Kikilo': { open: true }, 'Shino': { open: true }, 'Zuma': { open: true } }, pistes: {} };
const D2 = { 'gud-view': '2d' };
const PHONE = { w: 390, h: 844 }, DESK = { w: 1280, h: 800, mobile: false };
const click = sel => async p => { await p.locator(sel).first().click().catch(() => {}); await p.waitForTimeout(700); };

// The proposals, as they would be in css/site.css and the scripts
const FIX_CSS = `
html[lang="he"]{--f-body:"IBM Plex Sans Hebrew","IBM Plex Sans",system-ui,-apple-system,"Segoe UI",sans-serif}
html[lang="ru"]{--f-display:"Oswald","Karantina","Arial Narrow",sans-serif;--f-body:"IBM Plex Sans","IBM Plex Sans Hebrew",system-ui,-apple-system,"Segoe UI",sans-serif}
html[lang="ka"]{--f-display:"Noto Sans Georgian","IBM Plex Sans","Karantina","Arial Narrow",sans-serif;--f-body:"Noto Sans Georgian","IBM Plex Sans","IBM Plex Sans Hebrew",system-ui,sans-serif}
.ac-mark{font-family:var(--f-body);font-weight:700}
.mstat b{font-weight:700}
.games-list::after{background:var(--sn1)}
.ic{display:inline-block;width:1.05em;height:1.05em;vertical-align:-.16em;flex:none}
.north .ic{width:14px;height:14px;vertical-align:0}
:root{--g-descent:var(--p-blue);--g-school:var(--p-green);--g-fresh:#5B9BFF;--g-merge:#F4B942;--g-snowball:var(--p-black)}
:root[data-theme="dark"]{--g-fresh:#8DB9FF}
.games-list li:nth-child(1) .game-card{--gc:var(--g-descent)!important}
.games-list li:nth-child(2) .game-card{--gc:var(--g-school)!important}
.games-list li:nth-child(3) .game-card{--gc:var(--g-fresh)!important}
.games-list li:nth-child(4) .game-card{--gc:var(--g-merge)!important}
.games-list li:nth-child(5) .game-card{--gc:var(--g-snowball)!important}
.gc-face{color:var(--on-board)}
.gc-ink .gc-face{color:#13233A}`;
const FIX_JS = () => {
  const s = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"';
  const I = {
    '⇡': `<svg class="ic" viewBox="0 0 16 16" ${s} aria-hidden="true"><path d="M1.5 4.8L14.5 1.8M8 3.3V6.2"/><rect x="4.6" y="6.2" width="6.8" height="6.4"/><path d="M4.6 9.4h6.8"/></svg>`,
    '→': `<svg class="ic" viewBox="0 0 16 16" ${s} aria-hidden="true"><path d="M2.5 8h11M9 3.5L13.5 8 9 12.5"/></svg>`,
    '←': `<svg class="ic" viewBox="0 0 16 16" ${s} aria-hidden="true"><path d="M13.5 8h-11M7 3.5L2.5 8 7 12.5"/></svg>`,
    '⤢': `<svg class="ic" viewBox="0 0 16 16" ${s} aria-hidden="true"><path d="M2 6V2h4M10 2h4v4M14 10v4h-4M6 14H2v-4"/></svg>`,
    '↗': `<svg class="ic" viewBox="0 0 16 16" ${s} aria-hidden="true"><path d="M6 3h7v7M13 3L3.5 12.5"/></svg>`,
    '▲': `<svg class="ic" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 1.5l5.5 11h-11z" fill="currentColor"/></svg>`,
  };
  const esc = t => t.replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
  const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT), nodes = [];
  for (let n; (n = w.nextNode());) if (/[⇡←→⤢▲↗]/.test(n.textContent) && !n.parentElement.closest('svg')) nodes.push(n);
  for (const n of nodes) { const sp = document.createElement('span'); sp.innerHTML = esc(n.textContent).replace(/[⇡←→⤢▲↗]\s?|\s?[↗]/g, m => { const g = m.trim(); return I[g] + (m.endsWith(' ') ? ' ' : '') + (m.startsWith(' ') ? '' : ''); }); n.replaceWith(...sp.childNodes); }
  // the lift names on the map: the same icon, drawn in the map's own SVG beside the name
  const NS = 'http://www.w3.org/2000/svg';
  document.querySelectorAll('text.lbl').forEach(t => { if (!t.textContent.includes('⇡')) return;
    t.textContent = t.textContent.replace('⇡ ', ''); const b = t.getBBox(), cs = getComputedStyle(t), h = b.height * .72;
    const g = document.createElementNS(NS, 'g'); g.setAttribute('transform', `translate(${b.x - h * 1.15} ${b.y + (b.height - h) / 2}) scale(${h / 16})`);
    const d = ['M1.5 4.8L14.5 1.8M8 3.3V6.2', 'M4.6 6.2h6.8v6.4H4.6z', 'M4.6 9.4h6.8'];
    for (const [stroke, wd] of [[cs.stroke || '#fff', 5], [cs.fill, 1.8]]) for (const p of d) { const e = document.createElementNS(NS, 'path'); e.setAttribute('d', p); e.setAttribute('fill', 'none'); e.setAttribute('stroke', stroke); e.setAttribute('stroke-width', wd); e.setAttribute('stroke-linecap', 'round'); e.setAttribute('stroke-linejoin', 'round'); g.appendChild(e); }
    t.parentNode.insertBefore(g, t); });
};

// id, url, options, action, target (selector, or [x, y, w, h]), padding [top, side, bottom, maxHeight], which ('both' or 'now')
const SHOTS = [
  // fonts: icons drawn as characters the language's fonts don't have
  ['ic-panel', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '.brief', [10, 10, 10, 260], 'both'],
  ['ic-back', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '#panel .back', [8, 12, 8], 'both'],
  ['ic-runnav', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '.run-nav', [8, 10, 8], 'both'],
  ['ic-label', '/#map', { ...PHONE, extraStorage: D2, wait: 1800 }, null, 'text.lbl.lift', [40, 70, 40], 'both'],
  ['ic-zoom', '/#map', { ...PHONE, extraStorage: D2, wait: 1800 }, null, '#zfit', [8, 8, 8], 'both'],
  ['ic-north', '/#map', { ...PHONE, extraStorage: D2, wait: 1800 }, null, '.north', [8, 12, 8], 'both'],
  ['ic-yt', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500, lang: 'en' }, null, 'a[href*="youtube.com/results"]', [8, 12, 8], 'both'],
  // fonts: text in another script than the page's
  ['sc-names-ru', '/#group/g1', { ...PHONE, signed: true, trip: MYTRIP, wait: 1500, lang: 'ru' }, null, '#grMain .ac-flight', [10, 10, 10, 300], 'both'],
  ['sc-cyr-he', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '.vids li > a', [8, 12, 8], 'both'],
  ['sc-acct-ru', '/#about', { ...DESK, signed: true, lang: 'ru' }, null, '.topbar', [0, 0, 0], 'both'],
  // fonts: the account marks in Arial, the status numbers in a fake bold
  ['mk-ways', '/#account', { ...PHONE, signed: true }, null, '#acWays', [10, 10, 10], 'both'],
  ['mk-signin', '/#signin', { ...PHONE }, null, '#signinPage [data-providers]', [10, 10, 10], 'both'],
  ['fb-mstat', '/#map', { ...PHONE, status: LIVE, extraStorage: D2, wait: 1800 }, null, '.mstat', [8, 8, 8], 'both'],
  // colors: the game signs and the pole's snow at night
  ['cl-games-night', '/#games', { ...PHONE, theme: 'night' }, null, '.games-list', [40, 0, 0, 900], 'both'],
  ['cl-games-day', '/#games', { ...PHONE }, null, '.games-list', [40, 0, 0, 900], 'both'],
  ['cl-map-night', '/#map', { ...PHONE, theme: 'night', extraStorage: D2, wait: 1500 }, click('.mstat'), '.lstat-post', [30, 16, 16, 260], 'now'],
  // the components the language defines, as they are on the site
  ['cp-signs', '/#home', { ...PHONE, trip: MYTRIP }, null, '.post', [16, 0, 16, 560], 'now'],
  ['cp-chips', '/#map', { ...PHONE, extraStorage: D2, wait: 1800 }, null, '.filters', [10, 10, 10], 'now'],
  ['cp-switch', '/#map', { ...PHONE, extraStorage: D2, wait: 1800 }, null, '.viewsw', [10, 10, 10], 'now'],
  ['cp-pass', '/#home', { ...PHONE, trip: MYTRIP }, null, '#bpStack', [30, 8, 16], 'now'],
  ['cp-tally', '/#map', { ...DESK, extraStorage: D2, wait: 1800 }, null, '.tally', [10, 10, 10], 'now'],
  ['cp-snow', '/#home', { ...PHONE, trip: null }, null, '#seasonBoard', [34, 16, 16], 'now'],
  ['cp-topbar', '/#home', { ...DESK, trip: MYTRIP }, null, '.topbar', [0, 0, 0], 'now'],
  ['cp-runsign', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '#panel h2', [8, 12, 8], 'now'],
  // shapes outside the definition
  ['cp-pills', '/#home', { ...PHONE, trip: MYTRIP }, null, '.home-top', [6, 6, 6], 'now'],
  ['cp-skipass', '/#about', { ...PHONE, signed: true }, null, '.ab-pass', [16, 12, 16], 'now'],
  ['cp-board', '/#map', { ...PHONE, status: LIVE, extraStorage: D2, wait: 1800 }, click('.mstat'), '.board-dep', [10, 10, 10, 220], 'now'],
  ['cp-cards', '/#signin', { ...PHONE }, null, '#signinPage .ac-card', [34, 16, 12], 'now'],
  // buttons: one of each look, to show how many there are
  ['bt-addtrip', '/#home', { ...PHONE, trip: null }, null, '#bpEmpty .btn-blue', [10, 10, 10], 'now'],
  ['bt-save', '/#trip', { ...PHONE, trip: MYTRIP }, null, '.tf-save', [10, 10, 10], 'now'],
  ['bt-continue', '/#group', { ...PHONE }, null, '#grCode .ac-btn', [10, 10, 10], 'now'],
  ['bt-github', '/#about', { ...PHONE }, null, '.ab-gh', [10, 10, 10], 'now'],
  ['bt-share', '/#meet', { ...PHONE, trip: MYTRIP }, click('[data-pre="am"]'), '#meetShareBox', [10, 10, 10], 'now'],
  ['bt-signout', '/#account', { ...PHONE, signed: true }, null, '#acOut', [10, 10, 10], 'now'],
  ['bt-runshare', '/#map/run/Tatra%202', { ...PHONE, extraStorage: D2, wait: 2500 }, null, '.rn-share', [10, 10, 10], 'now'],
  ['bt-clear', '/#meet', { ...PHONE, trip: MYTRIP }, click('[data-pre="am"]'), '.meet-clear', [10, 10, 10], 'now'],
  ['bt-days', '/#meet', { ...PHONE, trip: MYTRIP }, click('[data-pre="am"]'), '[data-days]', [10, 10, 10], 'now'],
  // the games: emoji and symbols in place of the language's icons
  ['gm-school', '/games/school/', { ...PHONE, wait: 1500 }, null, [0, 0, 390, 520], [0, 0, 0], 'now'],
  ['gm-fresh', '/games/fresh-snow/', { ...PHONE, wait: 1500 }, null, [0, 0, 390, 520], [0, 0, 0], 'now'],
  ['gm-descent', '/games/descent/', { ...PHONE, wait: 1500 }, null, [0, 0, 390, 520], [0, 0, 0], 'now'],
  ['gm-snowball', '/games/snowball/', { ...PHONE, wait: 1500 }, null, [0, 0, 390, 520], [0, 0, 0], 'now'],
];

const b = await launch();
for (const [id, url, opts, act, target, pad, which] of SHOTS) {
  if (ONLY && !ONLY.test(id)) continue;
  for (const v of which === 'both' ? ['now', 'fix'] : ['now']) {
    const { ctx, page } = await open(b, url, { dsf: 2, ...opts });
    try {
      if (v === 'fix') {
        await page.addStyleTag({ content: FIX_CSS });
        await page.addStyleTag({ url: 'https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&display=swap' });
        await page.evaluate(() => document.fonts.ready); await page.waitForTimeout(400);
      }
      if (act) await act(page);
      if (v === 'fix') { await page.evaluate(FIX_JS); await page.evaluate(() => document.fonts.ready); }
      await page.waitForTimeout(400);
      let clip;
      if (Array.isArray(target)) clip = { x: target[0], y: target[1], width: target[2], height: target[3] };
      else {
        const r = await page.evaluate(([sel, maxH]) => { const el = [...document.querySelectorAll(sel)].find(e => e.getBoundingClientRect().width > 0); if (!el) return null;
          el.scrollIntoView({ block: 'center' }); const q = el.getBoundingClientRect(), sy = document.scrollingElement.scrollTop, sx = document.scrollingElement.scrollLeft;
          return { x: q.left + sx, y: q.top + sy, w: q.width, h: Math.min(q.height, maxH || 1e9) }; }, [target, pad[3]]);
        if (!r) throw new Error('no ' + target);
        await page.waitForTimeout(250);
        const vw = page.viewportSize().width;
        const x = Math.max(0, r.x - pad[1]), w = Math.min(vw - x, r.w + 2 * pad[1]);
        clip = { x, y: Math.max(0, r.y - pad[0]), width: w, height: r.h + pad[0] + pad[2] };
      }
      clip = Object.fromEntries(Object.entries(clip).map(([k, val]) => [k, Math.round(val)]));
      await page.screenshot({ path: `${OUT}${id}-${v}.png`, clip, fullPage: true });
      console.log(id, v, clip.width, clip.height);
    } catch (e) { console.log(id, v, 'FAILED', e.message.split('\n')[0]); }
    await ctx.close();
  }
}
await b.close();

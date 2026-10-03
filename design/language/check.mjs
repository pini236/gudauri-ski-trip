// New design language: a quick layout check of one canvas board (.dc.html) outside the canvas runtime.
// Renders the board's markup at its $preview size with its Google Fonts, saves a PNG, and lists text that
// overflows its box or the board. Holes ({{...}}) and <sc-*> logic are not run, so use it on static boards.
// Usage, from the repo root:  node design/language/check.mjs <board.dc.html> [out.png]
import { chromium } from 'playwright';
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const [file, out = file.replace(/\.dc\.html$/, '.check.png')] = process.argv.slice(2);
const src = fs.readFileSync(file, 'utf8');
const body = (src.match(/<x-dc>([\s\S]*)<\/x-dc>/) || [, ''])[1];
const helmet = (body.match(/<helmet>([\s\S]*?)<\/helmet>/) || [, ''])[1];
const markup = body.replace(/<helmet>[\s\S]*?<\/helmet>/, '');
const props = JSON.parse(((src.match(/data-props='([^']*)'/) || [, '{}'])[1]).replace(/&#39;/g, "'").replace(/&amp;/g, '&'));
const { width = 1280, height = 800 } = props.$preview || {};
const html = `<!doctype html><html><head><meta charset="utf-8">${helmet}</head><body style="margin:0"><div id="root" style="width:${width}px">${markup}</div></body></html>`;
const b = await chromium.launch(); const p = await b.newPage({ viewport: { width, height } });
// Google Fonts through curl (it knows the sandbox proxy; Chromium here does not), cached per run
const cache = new Map();
await p.route(/fonts\.(googleapis|gstatic)\.com/, async r => { const u = r.request().url();
  try { if (!cache.has(u)) cache.set(u, execFileSync('curl', ['-sS', '-m', '20', '-A', 'Mozilla/5.0 Chrome/120', u], { maxBuffer: 1 << 26 }));
    await r.fulfill({ status: 200, body: cache.get(u), contentType: u.includes('css2') ? 'text/css' : 'font/woff2', headers: { 'access-control-allow-origin': '*' } }); } catch (e) { await r.abort(); } });
await p.setContent(html, { waitUntil: 'networkidle' }).catch(() => {});
await p.evaluate(() => document.fonts.ready);
await p.waitForTimeout(400);
const issues = await p.evaluate(({ width }) => {
  const res = []; const root = document.querySelector('#root').firstElementChild;
  const R = root.getBoundingClientRect();
  for (const e of root.querySelectorAll('*')) {
    if (e.closest('svg') && e.tagName !== 'svg') continue;
    const r = e.getBoundingClientRect(); if (!r.width) continue;
    const txt = (e.innerText || e.textContent || '').trim().slice(0, 40);
    const cs = getComputedStyle(e);
    if (r.right > R.right + 1 || r.left < R.left - 1) res.push(`outside board: <${e.tagName.toLowerCase()}> "${txt}" ${Math.round(r.left)}..${Math.round(r.right)}`);
    else if (e.scrollWidth > e.clientWidth + 2 && cs.overflow !== 'visible' && e.children.length === 0) res.push(`clipped text: "${txt}"`);
    else if (e.children.length === 0 && txt && e.scrollWidth > e.clientWidth + 2 && cs.display !== 'inline') res.push(`text wider than its box: "${txt}" (${e.scrollWidth} > ${e.clientWidth})`);
  }
  const h = root.getBoundingClientRect().height;
  const fonts = [...document.fonts].filter(f => f.status === 'loaded').map(f => f.family + ' ' + f.weight);
  return { res: [...new Set(res)].slice(0, 30), h: Math.round(h), fonts: [...new Set(fonts)] };
}, { width });
await p.setViewportSize({ width, height: Math.max(height, issues.h) });
await p.screenshot({ path: out, fullPage: true });
await b.close();
console.log(JSON.stringify({ board: file, size: `${width}x${height}`, renderedHeight: issues.h, fontsLoaded: issues.fonts, issues: issues.res }, null, 1));

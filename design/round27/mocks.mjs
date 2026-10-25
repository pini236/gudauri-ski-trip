// Round 27: how the site looks in a Google result and in a WhatsApp link preview, today and with the proposal.
// Drawn as close as we can to how each one renders (sizes and colours measured from their pages, Arial/system font),
// from copy.json and the share images (shots/og-*.png). node design/round27/mocks.mjs
import { chromium } from 'playwright';
import fs from 'node:fs';
const HERE = new URL('./', import.meta.url).pathname, OUT = HERE + 'shots/', ROOT = HERE + '../../';
const C = JSON.parse(fs.readFileSync(HERE + 'copy.json', 'utf8'));
const uri = (f, t) => `data:${t};base64,` + fs.readFileSync(f).toString('base64');
const FAV = uri(ROOT + 'site/favicon.svg', 'image/svg+xml');
const OG = { today: uri(ROOT + 'site/og.jpg', 'image/jpeg'), a: uri(OUT + 'og-a.png', 'image/png'), b: uri(OUT + 'og-b.png', 'image/png') };
const URL_ = 'gudauri-ski-trip.vercel.app';
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const dir = l => l === 'he' ? 'rtl' : 'ltr';

// Google: the result as on a desktop (600 wide) and on a phone (a card, 360 wide)
function google(t, l, phone) {
  const w = phone ? 360 : 600;
  return `<div dir="${dir(l)}" style="width:${w}px;padding:${phone ? 16 : 0}px;background:#fff;font-family:Arial,sans-serif;${phone ? 'border-radius:16px;box-shadow:0 1px 6px rgba(32,33,36,.28)' : ''}">
  <div style="display:flex;align-items:center;gap:12px;margin-bottom:${phone ? 10 : 6}px">
    <span style="width:28px;height:28px;border-radius:50%;background:#f1f3f4;border:1px solid #dadce0;display:grid;place-items:center"><img src="${FAV}" width="18" height="18"></span>
    <span style="display:flex;flex-direction:column;line-height:20px"><span style="font-size:14px;color:#202124">${esc(t.site_name)}</span><span dir="ltr" style="font-size:12px;color:#4d5156;text-align:${l === 'he' ? 'right' : 'left'}">https://${URL_}</span></span></div>
  <div style="font-size:${phone ? 18 : 20}px;line-height:${phone ? 24 : 26}px;color:#1a0dab;${phone ? 'display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden' : 'white-space:nowrap;overflow:hidden;text-overflow:ellipsis'}" class="t">${esc(t.title)}</div>
  <div style="margin-top:4px;font-size:14px;line-height:22px;color:#4d5156;display:-webkit-box;-webkit-line-clamp:${phone ? 3 : 2};-webkit-box-orient:vertical;overflow:hidden" class="d">${esc(t.description)}</div></div>`;
}
// WhatsApp: an outgoing message with the link, the large preview (wide image) and the small one (square crop)
function wa(t, l, og, small) {
  const msg = l === 'he' ? 'מצאתי מפה של גודאורי' : 'Found a map of Gudauri';
  const prev = small
    ? `<div style="display:flex;background:#d1f4cc;border-radius:6px;overflow:hidden;height:96px"><img src="${og}" style="width:96px;height:96px;object-fit:cover;flex:none">
       <div style="padding:6px 10px;min-width:0"><div style="font-size:14px;font-weight:700;color:#111b21;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(t.title)}</div>
       <div style="font-size:12.5px;color:#54656f;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(t.og_description)}</div></div></div>`
    : `<div style="background:#d1f4cc;border-radius:6px;overflow:hidden"><img src="${og}" style="display:block;width:100%;aspect-ratio:1.91/1;object-fit:cover">
       <div style="padding:6px 10px 8px"><div style="font-size:14px;font-weight:700;color:#111b21;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(t.title)}</div>
       <div style="font-size:12.5px;color:#54656f;margin-top:2px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(t.og_description)}</div>
       <div dir="ltr" style="font-size:12px;color:#667781;margin-top:4px;text-align:${l === 'he' ? 'right' : 'left'}">${URL_}</div></div></div>`;
  return `<div style="width:390px;padding:16px 12px;background:#efeae2;font-family:-apple-system,'Segoe UI',Helvetica,Arial,sans-serif;box-sizing:border-box">
  <div dir="${dir(l)}" style="margin-${l === 'he' ? 'left' : 'right'}:auto;margin-${l === 'he' ? 'right' : 'left'}:44px;width:300px;background:#d9fdd3;border-radius:8px;padding:4px;box-shadow:0 1px .5px rgba(11,20,26,.13);${l === 'he' ? 'margin-right:0;margin-left:auto' : 'margin-left:auto;margin-right:0'}">
  ${prev}<div style="padding:6px 6px 2px;font-size:14.2px;color:#111b21">${msg} <span dir="ltr" style="color:#027eb5">https://${URL_}</span>
  <span style="float:${l === 'he' ? 'left' : 'right'};font-size:11px;color:#667781;margin-top:6px">11:02</span></div></div></div>`;
}
const S = [
  ['g-today-he', google(C.today.he, 'he')], ['g-today-en', google(C.today.en, 'en')],
  ['g-he', google(C.new.he, 'he')], ['g-en', google(C.new.en, 'en')], ['g-ru', google(C.new.ru, 'ru')], ['g-ka', google(C.new.ka, 'ka')],
  ['g-he-ph', google(C.new.he, 'he', 1)], ['g-en-ph', google(C.new.en, 'en', 1)],
  ['w-today-he', wa(C.today.he, 'he', OG.today)], ['w-today-he-sm', wa(C.today.he, 'he', OG.today, 1)],
  ['w-he', wa(C.new.he, 'he', OG.a)], ['w-en', wa(C.new.en, 'en', OG.a)], ['w-he-sm', wa(C.new.he, 'he', OG.a, 1)], ['w-en-sm', wa(C.new.en, 'en', OG.a, 1)],
  ['w-he-b', wa(C.new.he, 'he', OG.b)], ['w-he-b-sm', wa(C.new.he, 'he', OG.b, 1)],
];
const b = await chromium.launch({ executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
const p = await b.newPage({ viewport: { width: 640, height: 400 }, deviceScaleFactor: 1 });
for (const [n, h] of S) {
  await p.setContent(`<!doctype html><meta charset="utf-8"><body style="margin:0;display:inline-block;background:#fff">${h}</body>`);
  await p.waitForTimeout(100);
  const fit = await p.evaluate(() => { const t = document.querySelector('.t'), d = document.querySelector('.d'); return t ? { title: t.scrollWidth > t.clientWidth + 1 || t.scrollHeight > t.clientHeight + 1, desc: d.scrollHeight > d.clientHeight + 1 } : null; });
  const el = await p.$('body > div');
  await el.screenshot({ path: OUT + n + '.png' });
  console.log(n, fit ? JSON.stringify(fit) : '');
}
await b.close();

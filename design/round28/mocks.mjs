// Round 28: the /app page (page.html) on a phone, and how the short link looks in WhatsApp, from copy.json.
// The WhatsApp previews are drawn as close as we can to how it renders; they are not a capture of WhatsApp. node design/round28/mocks.mjs
import { chromium } from 'playwright';
import fs from 'node:fs';
const HERE = new URL('./', import.meta.url).pathname, OUT = HERE + 'shots/', ROOT = HERE + '../../';
const C = JSON.parse(fs.readFileSync(HERE + 'copy.json', 'utf8'));
const uri = (f, t) => `data:${t};base64,` + fs.readFileSync(f).toString('base64');
const IMG = { a: uri(ROOT + 'design/round9/export/feature-1024x500.png', 'image/png'), b: uri(ROOT + 'site/og.jpg', 'image/jpeg') };
const LINK = 'https://gudauri-ski-trip.vercel.app/app', HOST = 'gudauri-ski-trip.vercel.app';
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const t = C.share;

// WhatsApp on a phone (light) and on the computer (dark, like Pini's screenshot): an outgoing message with the link
function wa(img, small, dark) {
  const bg = dark ? '#0b141a' : '#efeae2', bub = dark ? '#005c4b' : '#d9fdd3', card = dark ? '#025144' : '#d1f4cc',
    h = dark ? '#e9edef' : '#111b21', m = dark ? '#aebac1' : '#54656f', u = dark ? '#53bdeb' : '#027eb5', ti = dark ? '#8696a0' : '#667781';
  const head = `<div style="font-size:14px;font-weight:700;color:${h};display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(t.title)}</div>
    <div style="font-size:12.5px;color:${m};margin-top:2px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${esc(t.description)}</div>`;
  const prev = small
    ? `<div style="display:flex;background:${card};border-radius:6px;overflow:hidden;height:96px"><img src="${img}" style="width:96px;height:96px;object-fit:cover;flex:none"><div style="padding:6px 10px;min-width:0">${head}</div></div>`
    : `<div style="background:${card};border-radius:6px;overflow:hidden"><img src="${img}" style="display:block;width:100%;aspect-ratio:1.91/1;object-fit:cover">
       <div style="padding:6px 10px 8px">${head}<div dir="ltr" style="font-size:12px;color:${ti};margin-top:4px;text-align:right">${HOST}</div></div></div>`;
  return `<div style="width:${dark ? 520 : 390}px;padding:16px 12px;background:${bg};font-family:-apple-system,'Segoe UI',Helvetica,Arial,sans-serif;box-sizing:border-box">
  <div dir="rtl" style="margin-right:0;margin-left:auto;width:${dark ? 400 : 300}px;background:${bub};border-radius:8px;padding:4px;box-shadow:0 1px .5px rgba(11,20,26,.13)">
  ${prev}<div style="padding:6px 6px 2px;font-size:14.2px;color:${h}"><span dir="ltr" style="color:${u};text-decoration:underline">${LINK}</span>
  <span style="float:left;font-size:11px;color:${ti};margin-top:6px">16:31</span></div></div></div>`;
}
const b = await chromium.launch({ executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium', args: ['--no-sandbox'] });
const p = await b.newPage({ viewport: { width: 640, height: 400 }, deviceScaleFactor: 1 });
for (const [n, h] of [['wa-a', wa(IMG.a)], ['wa-a-sm', wa(IMG.a, 1)], ['wa-b', wa(IMG.b)], ['wa-b-sm', wa(IMG.b, 1)], ['wa-a-dark', wa(IMG.a, 0, 1)], ['wa-b-dark', wa(IMG.b, 0, 1)]]) {
  await p.setContent(`<!doctype html><meta charset="utf-8"><body style="margin:0;display:inline-block">${h}</body>`);
  await (await p.$('body > div')).screenshot({ path: OUT + n + '.png' });
  console.log(n);
}
const ph = await b.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
await ph.addInitScript(c => { window.COPY = c; }, C);
for (const [n, q] of [['page-he', ''], ['page-he-dark', 'theme=dark'], ['page-ios', 's=iphone'], ['page-ios-dark', 's=iphone&theme=dark'], ['page-en', 'lang=en'], ['page-ka', 'lang=ka']]) {
  await ph.goto('file://' + HERE + 'page.html?' + q);
  await ph.evaluate(() => document.fonts.ready);
  await ph.waitForTimeout(300);
  await ph.screenshot({ path: OUT + n + '.png' });
  console.log(n);
}
await b.close();

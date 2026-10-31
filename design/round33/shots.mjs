// Round 33: the support page (/support), drawn inside the real privacy page (site/privacy.html) with its header,
// tokens, signs and snow, so what is approved is what will be built. The text is support.json. Nothing in site/ changes.
//   node design/round33/shots.mjs        (the site on http://127.0.0.1:4199: node tools/serve.mjs 4199)
import fs from 'node:fs';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname), OUT = path.join(HERE, 'shots');
fs.mkdirSync(OUT, { recursive: true });
const T = JSON.parse(fs.readFileSync(path.join(HERE, 'support.json'), 'utf8'));
const MAIL = 'pinisagent@gmail.com';

const CSS = `
.sp-mail{position:relative;margin:26px 0 22px;padding:16px 16px 14px;background:var(--paper);border-top:6px solid var(--p-blue);box-shadow:0 6px 14px var(--shadow)}
.sp-mail h2{margin:0 0 6px}
.sp-addr{display:block;margin:0 0 12px;font:600 21px/1.2 "IBM Plex Sans",var(--f-body);color:var(--ink);text-decoration:none;direction:ltr;unicode-bidi:isolate;text-align:start}
.pv-doc[dir="rtl"] .sp-addr{text-align:right}
.sp-btn{display:inline-flex;align-items:center;gap:10px;min-height:48px;padding:0 18px;background:var(--ink);color:var(--snow)!important;font:700 16px/1 var(--f-body);text-decoration:none}
.sp-tip{margin:12px 0 0!important;color:var(--muted);font-size:13.5px;line-height:1.5}
.sp-faq{margin:28px 0 0}
.sp-faq details{border-top:1px solid var(--rule)}
.sp-faq details:last-child{border-bottom:1px solid var(--rule)}
.sp-faq summary{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:54px;padding:6px 0;font-weight:700;font-size:16.5px;line-height:1.3;cursor:pointer;list-style:none}
.sp-faq summary::-webkit-details-marker{display:none}
.sp-faq summary svg{flex:none;transition:transform .2s}
.sp-faq details[open] summary svg{transform:rotate(180deg)}
.sp-faq details p{margin:0 0 14px}
.sp-links{display:flex;flex-wrap:wrap;gap:10px;margin:10px 0 0}
.sp-links a{display:inline-flex;align-items:center;gap:8px;min-height:44px;padding:0 14px;border:2px solid var(--ink);color:var(--ink)!important;font-weight:600;font-size:15px;text-decoration:none}
.sp-more{margin:30px 0 0}
`;
const mailIco = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14"/><path d="M3 6l9 7 9-7"/></svg>';
const chev = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>';
const lock = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="11" width="14" height="10"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>';
const bin = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>';

function doc(l, open) {
  const t = T[l], acct = `<a href="/account" dir="ltr">gudauri-ski-trip.vercel.app/account</a>`;
  const faq = t.faq.map(([q, a], i) => `<details${open === i ? ' open' : ''}><summary><span>${q}</span>${chev}</summary><p>${a.replace('{account}', acct)}</p></details>`).join('');
  return `<h1>${t.title}</h1>
<p class="pv-intro">${t.intro}</p>
<section class="sp-mail" data-snow="${l.length * 7}" data-snow-pile><h2>${t.mail_title}</h2>
<a class="sp-addr" href="mailto:${MAIL}">${MAIL}</a><a class="sp-btn" href="mailto:${MAIL}">${mailIco}<span>${t.mail_btn}</span></a>
<p class="sp-tip">${t.mail_tip}</p></section>
<div class="pv-sign"><b>${t.sign_title}</b><span>${t.sign_text}</span></div>
<section class="sp-faq"><h2>${t.faq_title}</h2>${faq}</section>
<section class="sp-more"><h2>${t.links_title}</h2><div class="sp-links"><a href="/privacy">${lock}<span>${t.link_privacy}</span></a><a href="/account">${bin}<span>${t.link_delete}</span></a></div></section>`;
}

async function shot(b, name, { lang = 'he', theme = 'day', w = 390, h = 844, dsf = 2, openQ = -1, scrollTo = null, full = true }) {
  const { ctx, page } = await open(b, '/privacy#' + lang, { w, h, dsf, theme, lang, wait: 400 });
  await page.evaluate(([html, css, docs]) => {
    const s = document.createElement('style'); s.textContent = css; document.head.appendChild(s);
    for (const [l, inner] of Object.entries(docs)) document.getElementById(l).innerHTML = inner;
    document.querySelector('.pv-foot').innerHTML = '<span dir="ltr">gudauri-ski-trip.vercel.app/support</span>';
    document.title = 'תמיכה · Support · Gudi: Gudauri Ski Map';
    if (window.SNOW) window.SNOW.scan(document);
  }, ['', CSS, Object.fromEntries(['he', 'en', 'ru', 'ka'].map(l => [l, doc(l, openQ)]))]);
  await page.click(`[data-lang="${lang}"]`);
  await page.evaluate(() => window.SNOW && window.SNOW.scan(document));
  await page.waitForTimeout(500);
  if (scrollTo) await page.evaluate(sel => { const e = document.querySelector(sel); if (e) window.scrollTo(0, e.getBoundingClientRect().top + scrollY - 70); }, scrollTo);
  await page.waitForTimeout(200);
  await page.screenshot({ path: path.join(OUT, name + '.png'), fullPage: full });
  if (page._errors.length) console.log(name, page._errors);
  await ctx.close();
}

const b = await launch();
await shot(b, 'y-he-day', {});
await shot(b, 'y-he-open', { openQ: 2, scrollTo: '#he .sp-faq', full: false });
await shot(b, 'y-he-night', { theme: 'night' });
await shot(b, 'y-en-day', { lang: 'en' });
await shot(b, 'y-ru-day', { lang: 'ru' });
await shot(b, 'y-ka-day', { lang: 'ka' });
await shot(b, 'y-desk-he', { w: 1280, h: 800, dsf: 1 });
await b.close();
console.log('ok');

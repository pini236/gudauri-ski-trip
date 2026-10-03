// Round 16 harness: the real site in Chromium, with the real Google Fonts, three.js and YouTube thumbnails fetched once
// through Node (the proxy) and cached outside the repo; analytics blocked; the account server faked with made-up names
// (as in design/round14/shots.mjs). Used by pairs.mjs. SITE=<server> picks the site.
import { chromium } from 'playwright';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';

export const SCR = path.dirname(new URL(import.meta.url).pathname);
const CACHE = process.env.NET_CACHE || path.join(os.tmpdir(), 'gud-netcache');
fs.mkdirSync(CACHE, { recursive: true });
const UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36';

async function cached(url) {
  const k = crypto.createHash('sha1').update(url).digest('hex');
  const f = path.join(CACHE, k), m = f + '.json';
  if (fs.existsSync(f) && fs.existsSync(m)) return { body: fs.readFileSync(f), ...JSON.parse(fs.readFileSync(m, 'utf8')) };
  const r = await fetch(url, { headers: { 'user-agent': UA } });
  const body = Buffer.from(await r.arrayBuffer());
  const meta = { status: r.status, type: r.headers.get('content-type') || 'application/octet-stream' };
  if (r.ok) { fs.writeFileSync(f, body); fs.writeFileSync(m, JSON.stringify(meta)); }
  return { body, ...meta };
}
const AVATAR = `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><rect width="160" height="160" fill="#6b7f99"/><circle cx="80" cy="62" r="30" fill="#dfe6ee"/><path d="M24 160c4-36 28-54 56-54s52 18 56 54z" fill="#dfe6ee"/></svg>`;

const GSI = `window.google={accounts:{id:{initialize(){},renderButton(el,o){el.innerHTML='<div style="box-sizing:border-box;display:flex;align-items:center;justify-content:center;gap:10px;height:40px;width:'+(o.width||360)+'px;max-width:100%;border:1px solid #dadce0;border-radius:4px;background:#fff;color:#3c4043;font:500 14px Roboto,Arial,sans-serif" dir="rtl"><svg width="18" height="18" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.6 13.3l7.9 6.1C12.4 13.6 17.7 9.5 24 9.5z"/><path fill="#4285F4" d="M46.1 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.4c-.5 2.9-2.2 5.3-4.6 6.9l7.3 5.7c4.3-3.9 7-9.8 7-17.1z"/><path fill="#FBBC05" d="M10.5 28.6c-.5-1.4-.8-3-.8-4.6s.3-3.2.8-4.6l-7.9-6.1C1 16.6 0 20.2 0 24s1 7.4 2.6 10.7l7.9-6.1z"/><path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.3-5.7c-2 1.4-4.7 2.3-8.6 2.3-6.3 0-11.6-4.1-13.5-9.8l-7.9 6.1C6.6 42.6 14.6 48 24 48z"/></svg><span>המשך עם Google</span></div>';}}}};`;
export const SITE = process.env.SITE || 'http://127.0.0.1:4199';

// The fake server (as in design/round14/shots.mjs): a signed-in person in one group, made-up names.
const b64 = o => Buffer.from(JSON.stringify(o)).toString('base64url');
const exp = Math.floor(Date.now() / 1000) + 3600 * 24 * 30;
export const ME = { id: 'u-me', aud: 'authenticated', role: 'authenticated', is_anonymous: false, identities: [{ provider: 'google' }], app_metadata: {}, user_metadata: {} };
export const SESSION = { access_token: `${b64({ alg: 'HS256' })}.${b64({ sub: ME.id, role: 'authenticated', exp })}.c2ln`, refresh_token: 'r', token_type: 'bearer', expires_in: 3600, expires_at: exp, user: ME };
export const MYTRIP = { v: 1, sid: 't-me', out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' }, ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null };
export const TRIP_ONEWAY = { v: 1, out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' }, ret: null, ski: null };
const G1 = { id: 'g1', name: 'גודאורי 2027', starts_on: '2027-01-10', ends_on: '2027-01-15' };
const members = [
  { group_id: 'g1', user_id: 'u-me', role: 'admin', display_name: 'נועה ניסיון', trip_id: 't-me', joined_at: '1' },
  { group_id: 'g1', user_id: 'u-dan', role: 'admin', display_name: 'דנה בדיקה', trip_id: 't-me2', joined_at: '2' },
  { group_id: 'g1', user_id: 'u-tal', role: 'member', display_name: 'טל דוגמה', trip_id: null, joined_at: '3' },
  { group_id: 'g1', user_id: 'u-ron', role: 'member', display_name: 'רון לדוגמה', trip_id: 't-ron', joined_at: '4' },
];
const trips = [
  { id: 't-me', owner_id: 'u-me', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', ret_date: '2027-01-15', entered_by: null },
  { id: 't-me2', owner_id: 'u-dan', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', ret_date: '2027-01-15', entered_by: null },
  { id: 't-ron', owner_id: 'u-ron', out_date: '2027-01-11', out_flight: 'A9 691', out_from: 'TLV', out_to: 'KUT', out_departs: '07:40:00', ret_date: '2027-01-15', entered_by: 'u-me' },
];
const json = (r, body, status = 200) => r.fulfill({ status, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' }, body: JSON.stringify(body) });
const groupsCache = (two, anon) => ({ uid: 'u-me', anon: !!anon, name: 'נועה ניסיון', providers: anon ? [] : ['google'], groups: [{ ...G1, role: 'admin', me: 'נועה ניסיון', trip: 't-me', count: 4 }, ...(two ? [{ id: 'g2', name: 'סקי עם המשפחה', starts_on: '2027-02-01', ends_on: '2027-02-05', role: 'member', me: 'נועה', trip: null, count: 3 }] : [])] });

// opts: w,h, theme ('day'|'night'|'auto'), lang, trip (object|null), signed (bool), anon, two, now (Date|ISO for a fixed clock),
// dsf (device scale), mobile, scheme ('light'|'dark'), reduce (reduced motion), status (fake /api/status JSON), extraStorage {}
export async function open(browser, url, o = {}) {
  const w = o.w || 390, h = o.h || 844, mobile = o.mobile ?? w < 700;
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: o.dsf || 1, isMobile: mobile, hasTouch: mobile,
    locale: o.locale || 'he-IL', timezoneId: o.tz || 'Asia/Jerusalem', colorScheme: o.scheme || (o.theme === 'night' ? 'dark' : 'light'), reducedMotion: o.reduce === false ? 'no-preference' : 'reduce' });
  const storage = { 'gud-lang': o.lang || 'he', 'gud-daynight': o.theme || 'day', ...(o.extraStorage || {}) };
  if (o.trip) storage['gud-trip'] = JSON.stringify(o.trip);
  if (o.signed) { storage['sb-vanuhuzuhnljvcoihvys-auth-token'] = JSON.stringify(o.anon ? { ...SESSION, user: { ...ME, is_anonymous: true, identities: [] } } : SESSION); storage['gud-acct'] = JSON.stringify(groupsCache(o.two, o.anon)); }
  await ctx.addInitScript(s => { try { for (const k in s) localStorage.setItem(k, s[k]); } catch (e) {} }, storage);
  if (o.now) await ctx.addInitScript(t => { const real = Date, fixed = new real(t).getTime(), t0 = real.now();
    // a clock that starts at the given time and runs on
    class D extends real { constructor(...a) { if (a.length) super(...a); else super(fixed + (real.now() - t0)); } static now() { return fixed + (real.now() - t0); } }
    globalThis.Date = D; }, new Date(o.now).toISOString());
  await ctx.route(/fonts\.googleapis\.com|fonts\.gstatic\.com|cdnjs\.cloudflare\.com|i\.ytimg\.com/, async r => {
    try { const c = await cached(r.request().url()); await r.fulfill({ status: c.status, contentType: c.type, body: c.body, headers: { 'access-control-allow-origin': '*' } }); } catch (e) { await r.abort(); } });
  await ctx.route(/github\.com\/pini236\.png/, r => r.fulfill({ contentType: 'image/svg+xml', body: AVATAR }));
  await ctx.route(/posthog|sentry|youtube-nocookie/, r => r.abort());
  // Google's sign-in button, drawn the way Google draws it (outline, large, 'continue with'), so the page has its real size
  await ctx.route(/accounts\.google\.com\/gsi\/client/, r => r.fulfill({ contentType: 'text/javascript', body: GSI }));
  await ctx.route(/\/api\/status/, r => o.status ? json(r, o.status) : r.fulfill({ status: 404, body: '' }));
  await ctx.routeWebSocket(/supabase\.co\/realtime/, () => {});
  await ctx.route('https://vanuhuzuhnljvcoihvys.supabase.co/**', r => { const q = r.request(), u = new URL(q.url()), pth = u.pathname;
    if (q.method() === 'OPTIONS') return r.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' } });
    if (pth === '/auth/v1/user') return json(r, o.anon ? { ...ME, is_anonymous: true, identities: [] } : ME);
    if (pth.endsWith('/group_leaderboard')) { const g = JSON.parse(q.postData() || '{}').game;
      return json(r, { descent: [{ user_id: 'u-dan', display_name: 'דנה בדיקה', best: 1840 }, { user_id: 'u-me', display_name: 'נועה ניסיון', best: 1520 }, { user_id: 'u-ron', display_name: 'רון לדוגמה', best: 960 }],
        school: [{ user_id: 'u-me', display_name: 'נועה ניסיון', best: 17 }, { user_id: 'u-tal', display_name: 'טל דוגמה', best: 12 }], merge: [{ user_id: 'u-ron', display_name: 'רון לדוגמה', best: 4096 }] }[g] || []); }
    if (pth.includes('/functions/v1/api/invite_preview')) return json(r, { status: 'ok', group_id: 'g1', name: G1.name, starts_on: G1.starts_on, ends_on: G1.ends_on, members: members.map(m => ({ user_id: m.user_id, display_name: m.display_name })), requires_approval: false, already_member: false });
    if (pth === '/auth/v1/signup' || pth === '/auth/v1/token') return json(r, { ...SESSION, user: { ...ME, is_anonymous: true, identities: [] } });
    if (pth.startsWith('/functions/')) return json(r, {});
    const t = pth.slice(9);
    if (t === 'profiles') return json(r, [{ display_name: 'נועה ניסיון' }]);
    const G2 = { id: 'g2', name: 'סקי עם המשפחה', starts_on: '2027-02-01', ends_on: '2027-02-05' };
    if (t === 'groups') return json(r, u.searchParams.get('id') === 'eq.g1' ? [G1] : o.two ? [G1, G2] : [G1]);
    if (t === 'group_members') return json(r, u.searchParams.get('user_id') ? (o.two ? [members[0], { ...members[0], group_id: 'g2', role: 'member', display_name: 'נועה' }] : [members[0]]) : members);
    if (t === 'trips') return json(r, trips);
    if (t === 'meetups') return json(r, [{ id: 'm1', station: '1009101185b', meet_at: '2027-01-11T05:30:00+00:00', note: null }, { id: 'm2', station: 'x', meet_at: '2027-01-12T09:00:00+00:00', note: null }]);
    if (t === 'invites') return json(r, [{ id: 'i1', code: 'KZBQRM', token: 'tok', revoked_at: null, requires_approval: false, uses: 0, max_uses: null, expires_at: null, created_at: new Date().toISOString() }]);
    if (t === 'join_requests') return json(r, [{ id: 'q1', display_name: 'שי חדש', kind: 'approval', reclaim_user_id: null, status: 'pending' }]);
    return json(r, []); });
  const page = await ctx.newPage();
  page._errors = [];
  page.on('pageerror', e => page._errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/status of 404/.test(m.text())) page._errors.push('console: ' + m.text()); });
  await page.goto((o.site || SITE) + url);
  await page.waitForSelector('#loading', { state: 'hidden', timeout: 30000 }).catch(() => {});
  await page.evaluate(() => document.fonts && document.fonts.ready).catch(() => {});
  await page.waitForTimeout(o.wait ?? 600);
  return { ctx, page };
}

export async function launch() {
  return chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
}

// Automated checks on the current page: things wider than the screen, text cut off, small touch targets, overlapping controls.
export async function checks(page) {
  return page.evaluate(() => {
    const out = [], vw = document.documentElement.clientWidth;
    const vis = el => { const s = getComputedStyle(el); if (s.display === 'none' || s.visibility === 'hidden' || +s.opacity === 0) return false; const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const name = el => { let s = el.tagName.toLowerCase(); if (el.id) s += '#' + el.id; const c = [...el.classList].slice(0, 3).join('.'); if (c) s += '.' + c; const t = (el.innerText || el.getAttribute('aria-label') || '').trim().replace(/\s+/g, ' ').slice(0, 40); return s + (t ? ` "${t}"` : ''); };
    const se = document.scrollingElement;
    if (se.scrollWidth > se.clientWidth + 1) out.push(`page scrolls sideways: ${se.scrollWidth} > ${se.clientWidth}`);
    // things that stick out past the right or left edge of the screen
    document.querySelectorAll('body *').forEach(el => { if (!vis(el)) return; const r = el.getBoundingClientRect(); const s = getComputedStyle(el);
      if (s.position === 'fixed') return;
      if ((r.right > vw + 1 || r.left < -1) && r.width < vw * 3 && !el.closest('svg') && !el.closest('.r3-labels') && !el.closest('.pano') && !el.closest('.home-sky') && !el.closest('.toolbar') && !el.closest('.ac-tabs')) {
        // only report the outermost
        if (!el.parentElement || (() => { const p = el.parentElement.getBoundingClientRect(); return !(p.right > vw + 1 || p.left < -1); })()) out.push(`sticks out of the screen: ${name(el)} [${Math.round(r.left)}..${Math.round(r.right)}]`); } });
    // text cut off: an element whose content is wider than its box, with overflow hidden and no ellipsis meant
    document.querySelectorAll('body *').forEach(el => { if (!vis(el) || el.closest('svg')) return; const s = getComputedStyle(el);
      if ((s.overflowX === 'hidden' || s.overflow === 'hidden' || s.overflowX === 'clip') && el.scrollWidth > el.clientWidth + 2 && el.innerText && el.innerText.trim() && !el.closest('.toolbar') && !el.matches('.pano,.home-sky,.r3-labels,.mapwrap,.meet-map'))
        out.push(`text wider than its box (${el.scrollWidth}>${el.clientWidth}, ellipsis:${s.textOverflow === 'ellipsis'}): ${name(el)}`); });
    // touch targets smaller than 44 (links inside running text are fine)
    document.querySelectorAll('a[href],button,input:not([type=hidden]),select,summary,[role=button]').forEach(el => { if (!vis(el)) return; const r = el.getBoundingClientRect();
      if (el.closest('p,li,dd') && el.tagName === 'A' && !el.matches('.ab-gh,.bp-edit,.ac-small,.home-about,.home-privacy')) return;
      if (el.matches('.bp-swap')) return;
      if (el.matches('input[type=range]') || el.matches('input[type=radio]')) return;
      if (r.width < 43.5 || r.height < 43.5) out.push(`small touch target ${Math.round(r.width)}x${Math.round(r.height)}: ${name(el)}`); });
    // controls on top of each other
    const ctl = [...document.querySelectorAll('a[href],button,input,select')].filter(vis).filter(el => !el.matches('.bp-swap,.bp-stub,input[type=range]'));
    for (let i = 0; i < ctl.length; i++) for (let j = i + 1; j < ctl.length; j++) { const a = ctl[i], b = ctl[j]; if (a.contains(b) || b.contains(a)) continue;
      const A = a.getBoundingClientRect(), B = b.getBoundingClientRect(); const ix = Math.min(A.right, B.right) - Math.max(A.left, B.left), iy = Math.min(A.bottom, B.bottom) - Math.max(A.top, B.top);
      if (ix > 4 && iy > 4) out.push(`controls overlap (${Math.round(ix)}x${Math.round(iy)}): ${name(a)} / ${name(b)}`); }
    return out;
  });
}

export function save(page, file, opts = {}) { return page.screenshot({ path: file, ...opts }); }

// A whole page: on the computer the pages scroll inside .site (the document does not), so the window grows to fit them.
export async function shoot(page, file, { full = true, max = 5000 } = {}) {
  if (!full) return page.screenshot({ path: file });
  const vp = page.viewportSize();
  const need = await page.evaluate(() => { const p = [...document.querySelectorAll('.site > .page')].find(x => !x.hidden && getComputedStyle(x).display !== 'none');
    if (!p || document.scrollingElement.scrollHeight > innerHeight + 2) return 0;
    const top = p.getBoundingClientRect().top; return Math.ceil(top + p.scrollHeight); });
  if (need > vp.height + 2) { await page.setViewportSize({ width: vp.width, height: Math.min(need, max) }); await page.waitForTimeout(400); }
  await page.screenshot({ path: file, fullPage: true });
  if (need > vp.height + 2) { await page.setViewportSize(vp); await page.waitForTimeout(200); }
}

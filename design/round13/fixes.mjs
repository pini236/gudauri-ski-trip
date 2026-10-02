// Round 13: small fixes, before and after, drawn inside the real site (like round 12). Asked by the fixes session
// (branch claude/fixes-night); nothing here is in the code until Pini approves (decision 18). Run from the repo root:
//   npx http-server site -p 4199 -s &   node design/round13/fixes.mjs [out dir]
// The server is faked for the group screens; names and the code are made up.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname);
const out = process.argv[2] || path.join(here, 'shots');
fs.mkdirSync(out, { recursive: true });
const font = f => fs.readFileSync(path.join(here, '../../app/android/app/src/main/res/font', f)).toString('base64');
const FONTS = [['Karantina', 700, 'karantina_bold.ttf'], ['IBM Plex Sans Hebrew', 400, 'plex_hebrew_regular.ttf'], ['IBM Plex Sans Hebrew', 600, 'plex_hebrew_bold.ttf'], ['IBM Plex Sans Hebrew', 700, 'plex_hebrew_bold.ttf'],
  ['IBM Plex Sans', 400, 'plex_hebrew_regular.ttf'], ['IBM Plex Sans', 600, 'plex_hebrew_bold.ttf']]
  .map(([fam, w, f]) => `@font-face{font-family:'${fam}';font-weight:${w};font-display:block;src:url(data:font/ttf;base64,${font(f)}) format('truetype')}`).join('\n');
const SITE = 'http://127.0.0.1:4199';
// Google's "G", as in their sign-in branding guidelines
const GOOGLE_G = '<svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>';

const b = await chromium.launch();
async function page(w, lang = 'he', h = 844) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, locale: 'he-IL', timezoneId: 'Asia/Tbilisi', reducedMotion: 'reduce' });
  await ctx.addInitScript(([l]) => { try { localStorage.setItem('gud-lang', l); localStorage.setItem('gud-daynight', 'day');
    for (let k = 0; k < 7; k++) localStorage.setItem('school-stars-' + k, '3'); } catch (e) {} }, [lang]);
  await ctx.route('**/fonts.googleapis.com/**', r => r.fulfill({ contentType: 'text/css', body: FONTS }));
  await ctx.route('**/fonts.gstatic.com/**', r => r.abort());
  await ctx.route('https://accounts.google.com/**', r => r.abort());
  return [ctx, await ctx.newPage()];
}
const shot = async (p, name, clip) => { await p.waitForTimeout(400); await p.screenshot({ path: path.join(out, name + '.png'), ...(clip ? { clip } : { fullPage: true }) }); console.log(name); };

const ONLY = process.env.ONLY || '1234';
// 1. the language buttons on the privacy page: 40 px today, the rule is 44
if (ONLY.includes('1')) {
  const [ctx, p] = await page(390);
  await p.goto(SITE + '/privacy'); await p.waitForTimeout(800);
  const box = async () => { const r = await p.locator('.pv-lang').boundingBox(); return { x: 0, y: 0, width: 390, height: Math.ceil(r.y + r.height + 70) }; };
  await p.evaluate(() => { document.querySelectorAll('.pv-lang button').forEach(b => b.style.outline = '2px dashed #D1342B'); });
  await shot(p, 'X1-PrivacyLang-before', await box());
  await p.addStyleTag({ content: '.pv-lang button{min-height:44px}' });
  await shot(p, 'X1-PrivacyLang-after', await box());
  await ctx.close();
}

// 2. the drawings on the ski school's lesson cards: the labels run off the edges. After: no words inside the drawing;
// a small legend under it, with the drawing's own colours, that wraps in every language.
if (ONLY.includes('2')) for (const lang of ['he', 'en']) for (const i of [0, 1]) {
  const [ctx, p] = await page(390, lang);
  await p.goto(SITE + '/games/school/'); await p.waitForTimeout(1500);
  await p.locator(`[data-i="${i}"]`).click(); await p.waitForTimeout(700);
  const clip = async () => { const r = await p.locator('.card svg').first().boundingBox(); const l = await p.locator('.card .fig-legend').boundingBox().catch(() => null);
    const bottom = l ? l.y + l.height : r.y + r.height; return { x: 0, y: Math.max(0, r.y - 70), width: 390, height: Math.ceil(bottom - r.y + 100) }; };
  await shot(p, `X2-School${i + 1}-${lang}-before`, await clip());
  await p.addStyleTag({ content: `.fig-legend{list-style:none;margin:6px 0 0;padding:0;display:flex;flex-wrap:wrap;gap:6px 14px;justify-content:center;font-size:13px;line-height:1.35}
    .fig-legend li{display:flex;align-items:center;gap:6px}.fig-legend i{flex:none;width:12px;height:4px;border-radius:2px}` });
  await p.evaluate(() => { const svg = document.querySelector('.card svg'); const items = [...svg.querySelectorAll('text')].map(t => [t.textContent, t.getAttribute('fill')]);
    svg.querySelectorAll('text').forEach(t => t.remove());
    svg.insertAdjacentHTML('afterend', `<ul class="fig-legend">${items.map(([s, c]) => `<li><i style="background:${c}"></i><span style="color:${c === '#4B5A6F' ? 'var(--muted,#4B5A6F)' : c}">${s}</span></li>`).join('')}</ul>`); });
  await shot(p, `X2-School${i + 1}-${lang}-after`, await clip());
  await ctx.close();
}

// 3. the "continue with Google" button: a plain G in a circle today (the site's first version, and the app's A2).
// After: Google's own mark, as their branding guidelines ask. On the site Google draws the button itself since today.
if (ONLY.includes('3')) {
  const [ctx, p] = await page(390);
  await p.goto(SITE + '/#signin'); await p.waitForSelector('#loading', { state: 'hidden' }); await p.waitForTimeout(800);
  const clip = async () => { const r = await p.locator('#signinPage [data-providers]').boundingBox(); return { x: 0, y: Math.max(0, r.y - 30), width: 390, height: Math.ceil(r.height + 60) }; };
  const draw = (after) => p.evaluate(([after, G]) => { const host = document.querySelector('#signinPage [data-providers]');
    host.innerHTML = (after ? `<button type="button" class="ac-btn google" style="font-family:Roboto,'IBM Plex Sans Hebrew',sans-serif;font-weight:500;border-radius:4px">${G}המשך עם Google</button>`
      : `<button type="button" class="ac-btn google"><span class="ac-mark">G</span>המשך עם גוגל</button>`)
      + `<button type="button" class="ac-btn apple"><span class="ac-mark">A</span>המשך עם אפל</button>`; }, [after, GOOGLE_G]);
  await draw(false); await shot(p, 'X3-Google-before', await clip());
  await draw(true); await shot(p, 'X3-Google-after', await clip());
  await ctx.close();
}

// 4. "I'm already in the group": the request reaches the admin with the member's name only, so the admin can't tell
// who is asking. After: the person types their own name, and the admin sees "<who> wants to come back as <member>".
if (ONLY.includes('4')) {
  const json = (r, body, status = 200) => r.fulfill({ status, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' }, body: JSON.stringify(body) });
  const b64 = o => Buffer.from(JSON.stringify(o)).toString('base64url');
  const exp = Math.floor(Date.now() / 1000) + 3600;
  const me = { id: 'u-dan', aud: 'authenticated', role: 'authenticated', is_anonymous: false, identities: [{ provider: 'google' }], app_metadata: {}, user_metadata: {} };
  const session = { access_token: `${b64({ alg: 'HS256' })}.${b64({ sub: me.id, role: 'authenticated', exp })}.c2ln`, refresh_token: 'r', token_type: 'bearer', expires_in: 3600, expires_at: exp, user: me };
  const members = [{ group_id: 'g1', user_id: 'u-dan', role: 'admin', display_name: 'דנה בדיקה', trip_id: null, joined_at: '2026-10-01' }, { group_id: 'g1', user_id: 'u-tal', role: 'member', display_name: 'טל דוגמה', trip_id: null, joined_at: '2026-10-01' }];
  const fake = async (ctx, reqName) => {
    await ctx.routeWebSocket(/supabase\.co\/realtime/, () => {});
    await ctx.route('https://vanuhuzuhnljvcoihvys.supabase.co/**', r => { const q = r.request(), u = new URL(q.url()), pth = u.pathname;
      if (q.method() === 'OPTIONS') return r.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' } });
      if (pth === '/auth/v1/signup') return json(r, session);
      if (pth === '/auth/v1/user') return json(r, me);
      if (pth.endsWith('/invite_preview')) return json(r, { status: 'ok', group_id: 'g1', name: 'קבוצת בדיקה', starts_on: '2027-01-10', ends_on: '2027-01-15', already_member: false, members: members.map(m => ({ user_id: m.user_id, display_name: m.display_name })) });
      if (pth.endsWith('/group_leaderboard')) return json(r, []);
      const t = pth.slice(9);
      if (t === 'profiles') return json(r, [{ display_name: 'דנה בדיקה' }]);
      if (t === 'groups') return json(r, [{ id: 'g1', name: 'קבוצת בדיקה', starts_on: '2027-01-10', ends_on: '2027-01-15' }]);
      if (t === 'group_members') return json(r, u.searchParams.get('user_id') ? [members[0]] : members);
      if (t === 'join_requests') return json(r, [{ id: 'q1', display_name: reqName, kind: 'reclaim', reclaim_user_id: 'u-tal', status: 'pending' }]);
      return json(r, []); });
  };
  // the person asking: the join page
  for (const after of [false, true]) {
    const [ctx, p] = await page(390);
    await fake(ctx, '');
    await p.goto(SITE + '/#join/KZBQRM'); await p.waitForSelector('#joinCard .ac-big');
    await p.locator('#joinReclaimBtn').click(); await p.waitForTimeout(300);
    if (after) await p.evaluate(() => { const box = document.getElementById('joinNames');
      box.insertAdjacentHTML('beforebegin', `<label class="ac-fld" style="margin-top:4px"><span>איך קוראים לך?</span><input type="text" value="טל" style="background:var(--snow)"></label><p style="margin:2px 0 0;font-size:13px">ובאיזה שם היית בקבוצה?</p>`); });
    const r = await p.locator('#joinReclaim').boundingBox();
    await shot(p, `X4-Reclaim-ask-${after ? 'after' : 'before'}`, { x: 0, y: Math.max(0, r.y - 20), width: 390, height: Math.ceil(r.height + 40) });
    await ctx.close();
  }
  // the admin: the members tab with the request (before, the request carries the member's name)
  for (const after of [false, true]) {
    const [ctx, p] = await page(390);
    await fake(ctx, 'טל דוגמה');
    await ctx.addInitScript(s => { localStorage.setItem('sb-vanuhuzuhnljvcoihvys-auth-token', JSON.stringify(s));
      localStorage.setItem('gud-acct', JSON.stringify({ uid: 'u-dan', anon: false, name: 'דנה בדיקה', providers: ['google'], groups: [{ id: 'g1', name: 'קבוצת בדיקה', starts_on: '2027-01-10', ends_on: '2027-01-15', role: 'admin', me: 'דנה בדיקה', trip: null, count: 2 }] })); }, session);
    await p.goto(SITE + '/#group/g1'); await p.waitForSelector('#grTitle'); await p.waitForTimeout(800);
    await p.locator('#grTabs [data-tab="members"]').click(); await p.waitForTimeout(500);
    if (after) await p.evaluate(() => { const row = document.querySelector('#grMain .ac-sec-box .ac-row .ac-txt');
      row.innerHTML = '<b>טל <span style="font-weight:400;color:var(--muted)">(השם שהקליד עכשיו)</span></b><small>רוצה לחזור בתור <b style="font-size:inherit;color:var(--ink)">טל דוגמה</b></small>'; });
    const r = await p.locator('#grMain .ac-sec-box').boundingBox();
    await shot(p, `X4-Reclaim-admin-${after ? 'after' : 'before'}`, { x: 0, y: Math.max(0, r.y - 60), width: 390, height: Math.ceil(r.height + 80) });
    await ctx.close();
  }
}
await b.close();

// New design language: screenshots of the site as it is today (main), the baseline for the canvas.
// Same fake server and fonts as round 14; names and codes are made up.
// Run from the repo root:   npx http-server site -p 4199 -s &   node design/language/today.mjs [out dir]
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const here = path.dirname(new URL(import.meta.url).pathname);
const out = process.argv[2] || path.join(here, 'shots/today');
fs.mkdirSync(out, { recursive: true });
const font = f => fs.readFileSync(path.join(here, '../../app/android/app/src/main/res/font', f)).toString('base64');
const FONTS = [['Karantina', 700, 'karantina_bold.ttf'], ['IBM Plex Sans Hebrew', 400, 'plex_hebrew_regular.ttf'], ['IBM Plex Sans Hebrew', 500, 'plex_hebrew_regular.ttf'], ['IBM Plex Sans Hebrew', 600, 'plex_hebrew_bold.ttf'], ['IBM Plex Sans Hebrew', 700, 'plex_hebrew_bold.ttf']]
  .map(([fam, w, f]) => `@font-face{font-family:'${fam}';font-weight:${w};font-display:block;src:url(data:font/ttf;base64,${font(f)}) format('truetype')}`).join('\n');
const SITE = 'http://127.0.0.1:4199';
const ONLY = process.env.ONLY ? new RegExp(process.env.ONLY) : null;
const b64 = o => Buffer.from(JSON.stringify(o)).toString('base64url');
const exp = Math.floor(Date.now() / 1000) + 3600;
const me = { id: 'u-me', aud: 'authenticated', role: 'authenticated', is_anonymous: false, identities: [{ provider: 'google' }], app_metadata: {}, user_metadata: {} };
const session = { access_token: `${b64({ alg: 'HS256' })}.${b64({ sub: me.id, role: 'authenticated', exp })}.c2ln`, refresh_token: 'r', token_type: 'bearer', expires_in: 3600, expires_at: exp, user: me };
const MYTRIP = { v: 1, sid: 't-me', out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' }, ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null };
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
const groupsCache = two => ({ uid: 'u-me', anon: false, name: 'נועה ניסיון', providers: ['google'], groups: [{ ...G1, role: 'admin', me: 'נועה ניסיון', trip: 't-me', count: 4 }, ...(two ? [{ id: 'g2', name: 'סקי עם המשפחה', starts_on: '2027-02-01', ends_on: '2027-02-05', role: 'member', me: 'נועה', trip: null, count: 3 }] : [])] });

const b = await chromium.launch();
async function page(w, { signed = true, two = false, trip = true, h = 844 } = {}) {
  const ctx = await b.newContext({ viewport: { width: w, height: h }, locale: 'he-IL', timezoneId: 'Asia/Tbilisi', reducedMotion: 'reduce' });
  await ctx.addInitScript(([s, c, t]) => { try { localStorage.setItem('gud-lang', 'he'); localStorage.setItem('gud-daynight', 'day');
    if (t) localStorage.setItem('gud-trip', JSON.stringify(t));
    if (s) { localStorage.setItem('sb-vanuhuzuhnljvcoihvys-auth-token', JSON.stringify(s)); localStorage.setItem('gud-acct', JSON.stringify(c)); } } catch (e) {} },
    [signed ? session : null, groupsCache(two), trip ? MYTRIP : null]);
  await ctx.route('**/fonts.googleapis.com/**', r => r.fulfill({ contentType: 'text/css', body: FONTS }));
  await ctx.route('**/fonts.gstatic.com/**', r => r.abort());
  await ctx.route('https://accounts.google.com/**', r => r.abort());
  await ctx.routeWebSocket(/supabase\.co\/realtime/, () => {});
  await ctx.route('https://vanuhuzuhnljvcoihvys.supabase.co/**', r => { const q = r.request(), u = new URL(q.url()), pth = u.pathname;
    if (q.method() === 'OPTIONS') return r.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' } });
    if (pth === '/auth/v1/user') return json(r, me);
    if (pth.endsWith('/group_leaderboard')) { const g = JSON.parse(q.postData() || '{}').game;
      return json(r, { descent: [{ user_id: 'u-dan', display_name: 'דנה בדיקה', best: 1840 }, { user_id: 'u-me', display_name: 'נועה ניסיון', best: 1520 }, { user_id: 'u-ron', display_name: 'רון לדוגמה', best: 960 }],
        school: [{ user_id: 'u-me', display_name: 'נועה ניסיון', best: 17 }, { user_id: 'u-tal', display_name: 'טל דוגמה', best: 12 }], merge: [{ user_id: 'u-ron', display_name: 'רון לדוגמה', best: 4096 }] }[g] || []); }
    if (pth.startsWith('/functions/')) return json(r, {});
    const t = pth.slice(9);
    if (t === 'profiles') return json(r, [{ display_name: 'נועה ניסיון' }]);
    const G2 = { id: 'g2', name: 'סקי עם המשפחה', starts_on: '2027-02-01', ends_on: '2027-02-05' };
    if (t === 'groups') return json(r, u.searchParams.get('id') === 'eq.g1' ? [G1] : two ? [G1, G2] : [G1]);
    if (t === 'group_members') return json(r, u.searchParams.get('user_id') ? (two ? [members[0], { ...members[0], group_id: 'g2', role: 'member', display_name: 'נועה' }] : [members[0]]) : members);
    if (t === 'trips') return json(r, trips);
    if (t === 'meetups') return json(r, [{ id: 'm1', station: 'x', meet_at: '2027-01-11T05:30:00+00:00', note: null }]);
    if (t === 'invites') return json(r, [{ id: 'i1', code: 'KZBQRM', token: 'tok', revoked_at: null, requires_approval: false }]);
    if (t === 'join_requests') return json(r, [{ id: 'q1', display_name: 'שי חדש', kind: 'approval', reclaim_user_id: null, status: 'pending' }]);
    return json(r, []); });
  return [ctx, await ctx.newPage()];
}
async function shot(p, name, clip) { await p.waitForTimeout(500); await p.screenshot({ path: path.join(out, name + '.png'), ...(clip ? { clip } : { fullPage: true }) }); console.log(name); }
const want = n => !ONLY || ONLY.test(n);
const group = async (p, tab) => { await p.goto(SITE + '/#group/g1'); await p.waitForSelector('#loading', { state: 'hidden' }); await p.waitForSelector('#grSome:not([hidden])'); await p.waitForTimeout(600);
  if (tab) { await p.locator(`#grTabs [data-tab="${tab}"]`).click(); await p.waitForTimeout(500); } };

const go = async (p, h) => { await p.goto(SITE + '/' + h); await p.waitForSelector('#loading', { state: 'hidden' }); await p.waitForTimeout(900); };
for (const [name, fn, w, opts] of [
  ['T1-Home', async p => go(p, '#home'), 390],
  ['T2-GuestHome', async p => go(p, '#home'), 390, { signed: false, trip: false }],
  ['T3-Map', async p => go(p, '#map'), 390],
  ['T4-Run', async p => { await go(p, '#map/run/Tatra%202'); await p.waitForTimeout(1800); }, 390],
  ['T5-Meet', async p => { await go(p, '#meet'); await p.locator('[data-pre="am"]').click(); await p.waitForTimeout(500); }, 390],
  ['T6-Games', async p => go(p, '#games'), 390],
  ['T7-About', async p => go(p, '#about'), 390],
  ['T8-Group', async p => group(p), 390],
  ['T9-HomeNight', async p => { await p.context().addInitScript(() => { try { localStorage.setItem('gud-daynight', 'night'); } catch (e) {} }); await go(p, '#home'); }, 390],
  ['T10-DesktopHome', async p => go(p, '#home'), 1280, { h: 800 }],
  ['T11-DesktopRun', async p => { await go(p, '#map/run/Tatra%202'); await p.waitForTimeout(1800); }, 1280, { h: 800 }],
  ['T12-Trip', async p => go(p, '#trip'), 390],
]) {
  if (!want(name)) continue;
  const [ctx, p] = await page(w, opts || {});
  await fn(p);
  await shot(p, name, { x: 0, y: 0, width: w, height: (opts && opts.h) || 844 });
  await ctx.close();
}
await b.close();

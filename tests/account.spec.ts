import { test, expect, Page, Route } from '@playwright/test';
import { listenCsp } from './csp-listen';

// Accounts and groups on the site (round 12, stage 13.9). The server is faked here (server/CONTRACT.md): the tests never
// reach the real one. Names and the code are made up.
const SB = 'https://vanuhuzuhnljvcoihvys.supabase.co';
const MY_TRIP = { v: 1, out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' },
  ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null };

function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  listenCsp(page); page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  return errors;
}
async function loaded(page: Page) {
  await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 });
  // the elevation model and three.js come after the home page (R-11): wait for them where they matter
  await expect(page.locator('html[data-model]')).toBeAttached({ timeout: 20_000 });
  if (/^#map/.test(new URL(page.url()).hash)) await expect(page.locator('.mapwrap[data-three]:not([data-three="loading"])')).toBeAttached({ timeout: 20_000 });
}
const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');

// a small fake of the server: one group, one other member, and whoever joins
async function fakeServer(page: Page, opts: { admin?: boolean; meetFull?: boolean; approval?: boolean; tal?: boolean } = {}) {
  const calls: string[] = [];
  const bodies: Record<string, any> = {};
  const me = { id: 'u-me', aud: 'authenticated', role: 'authenticated', is_anonymous: true, identities: [], app_metadata: {}, user_metadata: {} };
  const exp = Math.floor(Date.now() / 1000) + 3600;
  const token = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: me.id, role: 'authenticated', aud: 'authenticated', exp, is_anonymous: true })}.c2ln`;
  const session = { access_token: token, refresh_token: 'r1', token_type: 'bearer', expires_in: 3600, expires_at: exp, user: me };
  const db = {
    members: [{ group_id: 'g1', user_id: 'u-dan', role: 'admin', display_name: 'דנה בדיקה', trip_id: 't-dan', joined_at: '2026-10-01T10:00:00Z' }] as any[],
    trips: [{ id: 't-dan', owner_id: 'u-dan', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', ret_date: '2027-01-15', entered_by: null }] as any[],
    requests: [] as any[],
    meetups: [{ id: 'm1', group_id: 'g1', station: 'x', meet_at: '2027-01-11T05:30:00+00:00', note: null }] as any[],
  };
  if (opts.tal) db.members.push({ group_id: 'g1', user_id: 'u-tal', role: 'member', display_name: 'טל דוגמה', trip_id: null, joined_at: '2026-10-01T11:00:00Z' });
  const group = { id: 'g1', name: 'קבוצת בדיקה', starts_on: '2027-01-10', ends_on: '2027-01-15' };
  const json = (r: Route, body: unknown, status = 200) => r.fulfill({ status, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' }, body: JSON.stringify(body) });
  await page.routeWebSocket(/supabase\.co\/realtime/, () => {});
  await page.route('https://accounts.google.com/gsi/client', r => r.fulfill({ contentType: 'text/javascript', body:
    "window.google={accounts:{id:{initialize:function(o){window.__gsi=o;},renderButton:function(el){var b=document.createElement('button');b.type='button';b.setAttribute('data-fake-gsi','');b.textContent='Google';el.appendChild(b);}}}};" }));
  await page.route(`${SB}/**`, async r => {
    const req = r.request(), u = new URL(req.url()), path = u.pathname;
    if (req.method() === 'OPTIONS') return r.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' } });
    calls.push(req.method() + ' ' + path);
    if (path === '/auth/v1/signup') return json(r, session);
    if (path === '/auth/v1/token') { Object.assign(me, { is_anonymous: false, identities: [{ provider: 'google' }], app_metadata: { provider: 'google', providers: ['google'] } }); return json(r, { ...session, user: me }); }
    if (path === '/auth/v1/user') return json(r, me);
    if (path === '/auth/v1/logout') return r.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*' } });
    if (path.startsWith('/functions/v1/api/')) {
      const action = path.split('/').pop(), body = req.postDataJSON() || {};
      if (action === 'invite_preview') return json(r, body.code === 'KZBQRM' ? { status: 'ok', group_id: 'g1', ...group, requires_approval: false, already_member: false, members: db.members.map(m => ({ user_id: m.user_id, display_name: m.display_name })) } : { status: 'invalid_code' });
      if (action === 'join_group' && opts.approval) { db.requests.push({ id: 'rq1', group_id: 'g1', user_id: me.id, status: 'pending' }); return json(r, { status: 'pending' }); }
      if (action === 'cancel_join_request') { db.requests.forEach(q => { if (q.id === body.request_id) q.status = 'cancelled'; }); return json(r, {}); }
      if (action === 'join_group') { db.members.push({ group_id: 'g1', user_id: me.id, role: opts.admin ? 'admin' : 'member', display_name: body.display_name, trip_id: null, joined_at: '2026-10-02T10:00:00Z' }); return json(r, { status: 'joined', group_id: 'g1' }); }
      if (action === 'set_my_membership') { const m = db.members.find(x => x.user_id === me.id); m.trip_id = body.trip_id; return json(r, {}); }
      if (action === 'group_leaderboard') return json(r, body.game === 'descent' ? [{ user_id: 'u-dan', display_name: 'דנה בדיקה', best: 1200, achieved_at: '2026-10-01T10:00:00Z' }] : []);
      bodies[action!] = body;
      if (action === 'submit_score' || action === 'update_group') return json(r, {});
      if (action === 'same_flight') { const src = db.trips.find(t => t.id === body.trip_id); db.trips.push({ ...src, id: 't-copy', owner_id: me.id, entered_by: me.id }); db.members.find(x => x.user_id === me.id).trip_id = 't-copy'; return json(r, { trip_id: 't-copy' }); }
      if (action === 'set_member_role') { db.members.find(x => x.user_id === body.user_id).role = body.role; return json(r, {}); }
      if (action === 'set_member_trip') { db.trips.push({ ...body.trip, id: 't-tal', owner_id: body.user_id, entered_by: me.id }); db.members.find(x => x.user_id === body.user_id).trip_id = 't-tal'; return json(r, { trip_id: 't-tal' }); }
      if (action === 'remove_member') { db.members = db.members.filter(x => x.user_id !== body.user_id); return json(r, {}); }
      if (action === 'delete_my_account') { db.members = db.members.filter(x => x.user_id !== me.id); return json(r, { deleted: true }); }
      return json(r, { error: 'unknown_action' }, 404);
    }
    if (path.startsWith('/rest/v1/')) {
      const table = path.slice(9), q = u.searchParams;
      if (req.method() === 'POST' && table === 'trips') { const row = { ...req.postDataJSON(), id: 't-me', owner_id: me.id, entered_by: null }; db.trips.push(row); return json(r, [{ id: 't-me' }], 201); }
      if (req.method() === 'DELETE' && table === 'trips') { const id = (q.get('id') || '').slice(3); db.trips = db.trips.filter(t => t.id !== id); db.members.forEach(m => { if (m.trip_id === id) m.trip_id = null; }); return json(r, null, 204); }
      if (req.method() === 'POST' && table === 'meetups' && opts.meetFull) return json(r, { code: 'P0001', message: 'too_many_meetups' }, 400);
      if (req.method() === 'POST' && table === 'meetups') { db.meetups.push({ id: 'm' + (db.meetups.length + 1), ...req.postDataJSON() }); return json(r, null, 201); }
      if (table === 'profiles') return json(r, []);
      if (table === 'groups') return json(r, db.members.some(m => m.user_id === me.id) ? [group] : []);
      if (table === 'group_members') {
        const uid = q.get('user_id');
        return json(r, uid ? db.members.filter(m => 'eq.' + m.user_id === uid) : db.members);
      }
      if (table === 'trips') { const id = q.get('id'); return json(r, id && id.startsWith('eq.') ? db.trips.filter(t => t.id === id.slice(3)) : db.trips); }
      if (table === 'meetups') return json(r, db.meetups);
      if (table === 'join_requests') return json(r, db.requests.filter(x => 'eq.' + x.status === q.get('status') && (!q.get('user_id') || 'eq.' + x.user_id === q.get('user_id'))));
      return json(r, []);
    }
    return json(r, {}, 404);
  });
  return { calls, db, bodies };
}

test('אורח: האתר לא פונה לשרת, ושלט הקבוצה מזמין ליצור או להצטרף', async ({ page }) => {
  const errors = watchErrors(page);
  const server = await fakeServer(page);
  const lib: string[] = [];
  page.on('request', q => { if (/vendor\/supabase/.test(q.url())) lib.push(q.url()); });
  await page.goto('/');
  await loaded(page);
  await expect(page.locator('#groupBoard')).toBeVisible();
  await expect(page.locator('#groupBoardSub')).toHaveText('יצירת קבוצה, או הצטרפות בקוד');
  await page.goto('/#signin');
  await expect(page.locator('#signinPage')).toBeVisible();
  // Google's own button (Google Identity Services, faked here), and Apple waits for its account
  await expect(page.locator('#signinPage .ac-gsi [data-fake-gsi]')).toBeVisible();
  await expect(page.locator('#signinPage .ac-btn.apple')).toBeDisabled();
  await page.goto('/#group');
  await expect(page.locator('#grCode')).toBeVisible();
  await expect(page.locator('#grCreate button')).toBeDisabled();
  expect(server.calls).toEqual([]);
  expect(lib).toEqual([]);
  expect(errors).toEqual([]);
});

test('הצטרפות בקוד: אורח, הטיסה שלי בקבוצה, החשבון בכרטיס, ומחיקת החשבון', async ({ page }) => {
  const errors = watchErrors(page);
  const server = await fakeServer(page);
  await page.addInitScript(t => { try { if (!sessionStorage.getItem('seeded')) { localStorage.setItem('gud-trip', JSON.stringify(t)); sessionStorage.setItem('seeded', '1'); } } catch (e) {} }, MY_TRIP);
  await page.goto('/#group');
  await loaded(page);
  await page.locator('#grCode input').fill('kzb qrm');
  await page.locator('#grCode button').click();
  await expect(page).toHaveURL(/#join\/KZBQRM/i);
  await expect(page.locator('#joinCard .ac-big')).toHaveText('קבוצת בדיקה');
  await expect(page.locator('#joinReclaim')).toBeVisible();
  await page.locator('#joinForm input').fill('נועה ניסיון');
  await page.locator('#joinForm button').click();
  await expect(page).toHaveURL(/#group\/g1/);
  await expect(page.locator('#grTitle')).toHaveText('קבוצת בדיקה');
  // my trip went up, but the group shows it only once I choose it (CONTRACT, D1)
  expect(server.calls).toContain('POST /rest/v1/trips');
  expect(server.calls).not.toContain('POST /functions/v1/api/set_my_membership');
  await expect(page.locator('.ac-flight[data-leg="out"] .fp span')).toHaveText(['דנה בדיקה']);
  await page.locator('[data-showtrip]').click();
  await expect(page.locator('.ac-flight[data-leg="out"] .fp span')).toHaveText(['דנה בדיקה', 'נועה ניסיון']);
  await expect(page.locator('.ac-flight[data-leg="out"] .fp span.me')).toHaveText('נועה ניסיון');
  // the return flights have cards of their own (K-2), with the people on them
  await expect(page.locator('.ac-flight[data-leg="ret"] .fs').first()).toContainText('חזור');
  await expect(page.locator('.ac-flight[data-leg="ret"] .fp span.me')).toHaveText('נועה ניסיון');
  expect(server.db.members.find(m => m.display_name === 'נועה ניסיון').trip_id).toBe('t-me');
  await expect(page.locator('.ac-invite')).toHaveCount(0);
  await page.locator('#grTabs [data-tab="members"]').click();
  await expect(page.locator('#grMain .ac-row b')).toHaveText(['דנה בדיקה', 'נועה ניסיון']);
  await page.locator('#grTabs [data-tab="scores"]').click();
  await expect(page.locator('.ac-board h3')).toHaveText('הירידה של החבר׳ה');
  await page.locator('#grTabs [data-tab="meetups"]').click();
  await expect(page.locator('.ac-meet .mt')).toHaveText('09:30');
  // home: the group's sign, and the passenger is me
  await page.goto('/#home');
  await expect(page.locator('#groupBoardSub')).toHaveText('קבוצת בדיקה · 2 חברים');
  await page.locator('.bp.is-front [data-who]').click();
  await expect(page.locator('.who-pop')).toBeVisible();
  await expect(page.locator('.who-pop b')).toHaveText('נועה ניסיון');
  // a guest in a group has no sign-out here (it would lose the place), only keeping it with an account
  await expect(page.locator('.who-pop [data-signout]')).toHaveCount(0);
  await page.locator('.who-pop a[href="#account"]').click();
  await expect(page.locator('#accountPage')).toBeVisible();
  await expect(page.locator('#acGroups .ac-row b')).toHaveText('קבוצת בדיקה');
  await expect(page.locator('#acOut')).toBeHidden();
  // delete: two taps
  await page.locator('#accountPage a[href="#account/delete"]').click();
  await expect(page.locator('#deletePage')).toBeVisible();
  await page.locator('#delGo').click();
  await expect(page.locator('#delGo')).toHaveText(/לחיצה נוספת/);
  await page.locator('#delGo').click();
  await expect(page.locator('#delBtns')).toContainText('החשבון נמחק.');
  expect(server.calls).toContain('POST /functions/v1/api/delete_my_account');
  await page.goto('/#home');
  await expect(page.locator('#groupBoardSub')).toHaveText('יצירת קבוצה, או הצטרפות בקוד');
  // what is in this browser stays: the trip
  await expect(page.locator('#bpStack')).toBeVisible();
  const wide = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  expect(wide).toBeLessThanOrEqual(1);
  expect(errors).toEqual([]);
});

test('קישור הזמנה וקוד שגוי, ודף מחיקת החשבון בכתובת /account', async ({ page }) => {
  const errors = watchErrors(page);
  await fakeServer(page);
  await page.goto('/#join/WRONGX');
  await loaded(page);
  await expect(page.locator('#joinCard')).toContainText('הקוד לא תקף');
  await expect(page.locator('#joinForm')).toBeHidden();
  // the page Google Play links to (on Vercel /account redirects to it; the JS does the same for a direct hit)
  await page.route('**/account', async r => r.fulfill({ path: 'site/index.html', contentType: 'text/html' }));
  await page.goto('/account');
  await loaded(page);
  await expect(page).toHaveURL(/#account\/delete$/);
  await expect(page.locator('#deletePage')).toBeVisible();
  await expect(page.locator('#deletePage a[href^="mailto:"]')).toBeVisible();
  expect(errors).toEqual([]);
});

test('מנהל באתר: תפריט לכל חבר, פרטי הקבוצה, והשיאים מהמשחקים עולים לקבוצה (סבב 14)', async ({ page }) => {
  const errors = watchErrors(page);
  const server = await fakeServer(page, { admin: true });
  // a best score this browser kept from a game on the site (js/telemetry.js)
  await page.addInitScript(() => { try { localStorage.setItem('gud-best', JSON.stringify({ descent: 1500 })); } catch (e) {} });
  await page.goto('/#join/KZBQRM');
  await loaded(page);
  await page.locator('#joinForm input').fill('נועה ניסיון');
  await page.locator('#joinForm button').click();
  await expect(page).toHaveURL(/#group\/g1/);
  await expect.poll(() => server.bodies.submit_score).toEqual({ game: 'descent', score: 1500 });
  await page.locator('#grTabs [data-tab="members"]').click();
  // the other member has a menu; I don't
  await expect(page.locator('#grMain .ac-more')).toHaveCount(1);
  await page.locator('#grMain .ac-more').click();
  await expect(page.locator('.ac-menu [data-role="member"]')).toBeVisible();
  await page.locator('.ac-menu [data-role="member"]').click();
  await expect.poll(() => server.bodies.set_member_role).toEqual({ group_id: 'g1', user_id: 'u-dan', role: 'member' });
  // remove: two taps
  await page.locator('#grMain .ac-more').click();
  await page.locator('.ac-menu [data-remove]').click();
  expect(server.bodies.remove_member).toBeUndefined();
  await page.locator('.ac-menu [data-remove]').click();
  await expect.poll(() => server.bodies.remove_member).toEqual({ group_id: 'g1', user_id: 'u-dan' });
  await expect(page.locator('#grMain .ac-row b').first()).toHaveText('נועה ניסיון');
  // the group's name
  await page.locator('[data-panel="name"]').click();
  await page.locator('[data-nameform] input[name="n"]').fill('קבוצה חדשה');
  await page.locator('[data-nameform] button[type="submit"]').click();
  await expect.poll(() => server.bodies.update_group?.name).toBe('קבוצה חדשה');
  const wide = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  expect(wide).toBeLessThanOrEqual(1);
  expect(errors).toEqual([]);
});

test('"אני על אותה טיסה": הטיסה בדפדפן נקשרת לעותק שלי, כך שהשמירה הבאה לא מחזירה את הקבוצה לטיסה אחרת', async ({ page }) => {
  const errors = watchErrors(page);
  await fakeServer(page);
  await page.goto('/#join/KZBQRM');
  await loaded(page);
  await page.locator('#joinForm input').fill('נועה ניסיון');
  await page.locator('#joinForm button').click();
  await expect(page).toHaveURL(/#group\/g1/);
  await page.locator('[data-same]').first().click();
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('gud-trip') || '{}').sid)).toBe('t-copy');
  await expect(page.locator('#bpStack')).toHaveCount(1);
  expect(errors).toEqual([]);
});

test('מחיקת "הטיול שלך" מוחקת גם את השורה בשרת, והקבוצה מפסיקה להציג אותו (ד2)', async ({ page }) => {
  const errors = watchErrors(page);
  const server = await fakeServer(page);
  await page.addInitScript(t => { try { if (!sessionStorage.getItem('seeded')) { localStorage.setItem('gud-trip', JSON.stringify(t)); sessionStorage.setItem('seeded', '1'); } } catch (e) {} }, MY_TRIP);
  await page.goto('/#join/KZBQRM');
  await loaded(page);
  await page.locator('#joinForm input').fill('נועה ניסיון');
  await page.locator('#joinForm button').click();
  await expect(page).toHaveURL(/#group\/g1/);
  await page.locator('[data-showtrip]').click();
  await expect(page.locator('.ac-flight[data-leg="out"] .fp span.me')).toHaveText('נועה ניסיון');
  await page.goto('/#trip');
  await page.locator('#tfDelete').click();
  await page.locator('#tfDelete').click();
  await expect.poll(() => server.calls.includes('DELETE /rest/v1/trips')).toBe(true);
  expect(server.db.trips.map(t => t.id)).toEqual(['t-dan']);
  await page.goto('/#group/g1');
  await expect(page.locator('.ac-flight[data-leg="out"] .fp span')).toHaveText(['דנה בדיקה']);
  expect(errors).toEqual([]);
});

test('שמירת מפגש שהשרת סירב לה: ההודעה אומרת למה (ד5)', async ({ page }) => {
  const errors = watchErrors(page);
  await fakeServer(page, { meetFull: true });
  await page.goto('/#join/KZBQRM');
  await loaded(page);
  await page.locator('#joinForm input').fill('נועה ניסיון');
  await page.locator('#joinForm button').click();
  await expect(page).toHaveURL(/#group\/g1/);
  await page.goto('/#meet');
  await page.locator('[data-pre="am"]').click();
  await page.locator('#meetGroup').click();
  await expect(page.locator('#meetGroup')).toHaveText('יש כבר יותר מדי מפגשים בקבוצה.');
  expect(errors).toEqual([]);
});

test('בקשה שממתינה למנהל: נשארת אחרי טעינה מחדש, ואפשר לבטל אותה (S-6)', async ({ page }) => {
  const errors = watchErrors(page);
  const server = await fakeServer(page, { approval: true });
  await page.goto('/#join/KZBQRM');
  await loaded(page);
  await page.locator('#joinForm input').fill('נועה ניסיון');
  await page.locator('#joinForm button').click();
  await expect(page.locator('#joinPage [data-cancelreq]')).toBeVisible();
  await expect(page.locator('#joinForm')).toBeHidden();
  await page.reload();
  await loaded(page);
  await expect(page.locator('#joinPage [data-err]')).toContainText('הבקשה נשלחה למנהל');
  await page.locator('#joinPage [data-cancelreq]').click();
  await expect(page.locator('#joinForm')).toBeVisible();
  expect(server.calls).toContain('POST /functions/v1/api/cancel_join_request');
  expect(server.db.requests[0].status).toBe('cancelled');
  expect(errors).toEqual([]);
});

test('/account למשתמש רשום: נכנסים עם גוגל וחוזרים ישר למחיקה', async ({ page }) => {
  const errors = watchErrors(page);
  const server = await fakeServer(page);
  await page.goto('/#account/delete');
  await loaded(page);
  await expect(page.locator('#delBtns [data-fake-gsi]')).toBeVisible();
  // Google answers with an ID token (faked): the site signs in and comes back to the delete page
  await page.evaluate(() => (window as any).__gsi.callback({ credential: 'x.y.z' }));
  await expect(page).toHaveURL(/#account\/delete$/);
  await expect(page.locator('#delGo')).toBeVisible();
  await page.locator('#delGo').click();
  await page.locator('#delGo').click();
  await expect(page.locator('#delBtns')).toContainText('החשבון נמחק.');
  expect(server.calls).toContain('POST /auth/v1/token');
  expect(server.calls).toContain('POST /functions/v1/api/delete_my_account');
  expect(errors).toEqual([]);
});

// X-4: the same four cases as the app's unit test (docs/ARCHITECTURE.md)
test('קיבוץ החברים לכרטיסי טיסה לפי כיוון, תאריך ומספר הטיסה (X-4)', async ({ page }) => {
  await page.goto('/#home');
  await loaded(page);
  const same = await page.evaluate(() => {
    const k = (window as any).ACCOUNT.flightKey;
    const t = (o: any) => ({ out_date: '2027-01-10', ...o });
    return [
      k(t({ out_flight: '897', out_from: 'TLV', out_to: 'TBS' })) === k(t({ out_flight: '897' })), // with airports and without: one card
      k(t({ out_flight: 'IZ 897' })) === k(t({ out_flight: 'iz-897' })), // a space or not: one card
      k(t({ out_from: 'TLV', out_to: 'TBS' })) === k(t({ out_from: 'TLV', out_to: 'KUT' })), // no number, other airports: two cards
      k(t({ out_flight: '897', out_from: 'TLV', out_to: 'TBS' })) === k(t({ out_from: 'TLV', out_to: 'TBS' })), // number and none: two cards
    ];
  });
  expect(same).toEqual([true, true, false, false]);
});

// round 18 (K-3, decision 62): a flight for a member from the group's flights or the trip form, the meetup row opens its
// card and deleting it takes two taps, and without the server the kept copy says when it was kept
test('מנהל: טיסה לחבר מהטיסות שבקבוצה או מהטופס, מפגש בשתי נגיעות, ו"נשמר ב-" (סבב 18)', async ({ page }) => {
  const errors = watchErrors(page);
  const server = await fakeServer(page, { admin: true, tal: true });
  await page.goto('/#join/KZBQRM');
  await loaded(page);
  await page.locator('#joinForm input').fill('נועה ניסיון');
  await page.locator('#joinForm button').click();
  await expect(page).toHaveURL(/#group\/g1/);
  await page.locator('#grTabs [data-tab="members"]').click();
  await page.locator('[data-mmenu="u-tal"]').click();
  await page.locator('.ac-menu [data-mtrip="u-tal"]').click();
  await expect(page.locator('.ac-pick h3, .ac-sub h3').first()).toHaveText('הטיסה של טל דוגמה');
  await expect(page.locator('.ac-pick [data-trip]')).toHaveCount(1);
  await page.locator('.ac-pick [data-trip="t-dan"]').click();
  await expect.poll(() => server.bodies.set_member_trip?.trip?.out_flight).toBe('6H 897');
  expect(server.bodies.set_member_trip.user_id).toBe('u-tal');
  // another flight: the trip form, for her
  await page.locator('[data-mmenu="u-tal"]').click();
  await page.locator('.ac-menu [data-mtrip="u-tal"]').click();
  await page.locator('[data-otherfor="u-tal"]').click();
  await expect(page).toHaveURL(/#trip$/);
  await expect(page.locator('#tripForm h1')).toHaveText('הטיסה של טל דוגמה');
  await expect(page.locator('#tfDelete')).toBeHidden();
  await page.locator('#tripForm input[name="od"]').fill('2027-01-11');
  await page.locator('#tripForm input[name="of"]').fill('a9 691');
  await page.locator('#tripForm .tf-save').click();
  await expect(page).toHaveURL(/#group\/g1/);
  expect(server.bodies.set_member_trip.trip).toMatchObject({ out_date: '2027-01-11', out_flight: 'A9 691', out_from: 'TLV' });
  // my own trip in this browser was not touched
  expect(await page.evaluate(() => localStorage.getItem('gud-trip'))).toBeNull();
  // meetups: the row opens the meet card; the X asks once more, then deletes for everyone
  await page.locator('#grTabs [data-tab="meetups"]').click();
  await expect(page.locator('.ac-meet-go')).toHaveAttribute('href', /^#meet\/x\/0930\/20270111$/);
  await page.locator('[data-delmeet="m1"]').click();
  await expect(page.locator('.ac-meet.armed')).toContainText('לחיצה נוספת מוחקת');
  expect(server.calls).not.toContain('DELETE /rest/v1/meetups');
  await page.locator('[data-delmeet="m1"]').click();
  await expect.poll(() => server.calls.includes('DELETE /rest/v1/meetups')).toBe(true);
  // without the server: a note with the time it was kept, not an error
  await page.route(`${SB}/rest/v1/**`, r => r.abort());
  await page.reload();
  await loaded(page);
  await expect(page.locator('.ac-offline')).toContainText('בלי קליטה. מוצג מה שנשמר בטלפון ב-');
  await expect(page.locator('#groupPage [data-err]')).toBeHidden();
  expect(errors).toEqual([]);
});

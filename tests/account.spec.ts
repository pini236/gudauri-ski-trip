import { test, expect, Page, Route } from '@playwright/test';

// Accounts and groups on the site (round 12, stage 13.9). The server is faked here (server/CONTRACT.md): the tests never
// reach the real one. Names and the code are made up.
const SB = 'https://vanuhuzuhnljvcoihvys.supabase.co';
const MY_TRIP = { v: 1, out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' },
  ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null };

function watchErrors(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
  return errors;
}
async function loaded(page: Page) { await expect(page.locator('#loading')).toBeHidden({ timeout: 20_000 }); }
const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');

// a small fake of the server: one group, one other member, and whoever joins
async function fakeServer(page: Page, opts: { admin?: boolean } = {}) {
  const calls: string[] = [];
  const bodies: Record<string, any> = {};
  const me = { id: 'u-me', aud: 'authenticated', role: 'authenticated', is_anonymous: true, identities: [], app_metadata: {}, user_metadata: {} };
  const exp = Math.floor(Date.now() / 1000) + 3600;
  const token = `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64({ sub: me.id, role: 'authenticated', aud: 'authenticated', exp, is_anonymous: true })}.c2ln`;
  const session = { access_token: token, refresh_token: 'r1', token_type: 'bearer', expires_in: 3600, expires_at: exp, user: me };
  const db = {
    members: [{ group_id: 'g1', user_id: 'u-dan', role: 'admin', display_name: 'דנה בדיקה', trip_id: 't-dan', joined_at: '2026-10-01T10:00:00Z' }] as any[],
    trips: [{ id: 't-dan', owner_id: 'u-dan', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', ret_date: '2027-01-15', entered_by: null }] as any[],
    meetups: [{ id: 'm1', group_id: 'g1', station: 'x', meet_at: '2027-01-11T05:30:00+00:00', note: null }] as any[],
  };
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
    if (path === '/auth/v1/user') return json(r, me);
    if (path === '/auth/v1/logout') return r.fulfill({ status: 204, headers: { 'access-control-allow-origin': '*' } });
    if (path.startsWith('/functions/v1/api/')) {
      const action = path.split('/').pop(), body = req.postDataJSON() || {};
      if (action === 'invite_preview') return json(r, body.code === 'KZBQRM' ? { status: 'ok', group_id: 'g1', ...group, requires_approval: false, already_member: false, members: db.members.map(m => ({ user_id: m.user_id, display_name: m.display_name })) } : { status: 'invalid_code' });
      if (action === 'join_group') { db.members.push({ group_id: 'g1', user_id: me.id, role: opts.admin ? 'admin' : 'member', display_name: body.display_name, trip_id: null, joined_at: '2026-10-02T10:00:00Z' }); return json(r, { status: 'joined', group_id: 'g1' }); }
      if (action === 'set_my_membership') { const m = db.members.find(x => x.user_id === me.id); m.trip_id = body.trip_id; return json(r, {}); }
      if (action === 'group_leaderboard') return json(r, body.game === 'descent' ? [{ user_id: 'u-dan', display_name: 'דנה בדיקה', best: 1200, achieved_at: '2026-10-01T10:00:00Z' }] : []);
      bodies[action!] = body;
      if (action === 'submit_score' || action === 'update_group') return json(r, {});
      if (action === 'set_member_role') { db.members.find(x => x.user_id === body.user_id).role = body.role; return json(r, {}); }
      if (action === 'remove_member') { db.members = db.members.filter(x => x.user_id !== body.user_id); return json(r, {}); }
      if (action === 'delete_my_account') { db.members = db.members.filter(x => x.user_id !== me.id); return json(r, { deleted: true }); }
      return json(r, { error: 'unknown_action' }, 404);
    }
    if (path.startsWith('/rest/v1/')) {
      const table = path.slice(9), q = u.searchParams;
      if (req.method() === 'POST' && table === 'trips') { const row = { ...req.postDataJSON(), id: 't-me', owner_id: me.id, entered_by: null }; db.trips.push(row); return json(r, [{ id: 't-me' }], 201); }
      if (req.method() === 'POST' && table === 'meetups') { db.meetups.push({ id: 'm' + (db.meetups.length + 1), ...req.postDataJSON() }); return json(r, null, 201); }
      if (table === 'profiles') return json(r, []);
      if (table === 'groups') return json(r, db.members.some(m => m.user_id === me.id) ? [group] : []);
      if (table === 'group_members') {
        const uid = q.get('user_id');
        return json(r, uid ? db.members.filter(m => 'eq.' + m.user_id === uid) : db.members);
      }
      if (table === 'trips') return json(r, db.trips);
      if (table === 'meetups') return json(r, db.meetups);
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
  // my trip went up, and sits on the same flight as the other member
  await expect(page.locator('.ac-flight .fp span')).toHaveText(['דנה בדיקה', 'נועה ניסיון']);
  await expect(page.locator('.ac-flight .fp span.me')).toHaveText('נועה ניסיון');
  expect(server.calls).toContain('POST /rest/v1/trips');
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

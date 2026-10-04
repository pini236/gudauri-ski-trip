// Tests for the server's actions (supabase/functions/api/actions.ts),
// against a real database: either a plain Postgres built by
// `server/test.sh --fresh`, or the real Supabase database on GitHub.
// Users are inserted straight into auth.users; deleting a user (which the
// server does through the Auth admin API) is a plain delete here.
//
//   DB_URL=... deno test -A --config supabase/functions/api/deno.json tests/api.test.ts

import postgres from "postgres";
import { ACTION_NAMES, clientIp, handle, type Deps, type Meta } from "../supabase/functions/api/actions.ts";

const sql = postgres(Deno.env.get("DB_URL") ?? "postgresql://postgres:postgres@127.0.0.1:54322/postgres", {
  onnotice: () => {},
});
const deps: Deps = {
  sql,
  deleteAuthUser: async (id) => {
    await sql`delete from auth.users where id = ${id}`;
  },
};

function eq(actual: unknown, expected: unknown, what: string) {
  const a = JSON.stringify(actual), e = JSON.stringify(expected);
  if (a !== e) throw new Error(`${what}: expected ${e}, got ${a}`);
}

async function user(name: string, anonymous = false): Promise<string> {
  const id = crypto.randomUUID();
  await sql`insert into auth.users (id, email, is_anonymous, raw_user_meta_data)
            values (${id}, ${anonymous ? null : `${name}.${id}@example.com`}, ${anonymous},
                    ${sql.json(anonymous ? {} : { full_name: name })})`;
  return id;
}

// Call an action; returns the body, and checks the HTTP status.
// deno-lint-ignore no-explicit-any
async function call(u: string | null, action: string, body: Record<string, unknown> = {}, status = 200, meta: Meta = {}): Promise<any> {
  const r = await handle(deps, u, action, body, meta);
  if (r.status !== status) throw new Error(`${action}: expected ${status}, got ${r.status} ${JSON.stringify(r.body)}`);
  return r.body;
}

async function fails(u: string | null, action: string, body: Record<string, unknown>, error: string) {
  const r = await handle(deps, u, action, body);
  eq((r.body as { error?: string }).error, error, `${action} error`);
}

async function invite(group: string) {
  const [i] = await sql`select code, token from public.invites where group_id = ${group} and revoked_at is null
                        order by created_at desc limit 1`;
  return i as { code: string; token: string };
}

async function count(q: Promise<postgres.RowList<postgres.Row[]>>) {
  return Number((await q)[0].n);
}

async function newGroup(admin: string, extra: Record<string, unknown> = {}): Promise<string> {
  return (await call(admin, "create_group", { name: "Ski", display_name: "Admin", ...extra })).group_id;
}

// One connection pool for all the tests, so the per-test leak checks are off.
function test(name: string, fn: () => Promise<void>) {
  Deno.test({ name, fn, sanitizeOps: false, sanitizeResources: false });
}

// ---------------------------------------------------------------------

test("the way in: unknown actions, no session, bad input", async () => {
  const pini = await user("pini");
  eq((await handle(deps, pini, "drop_everything", {})).status, 404, "unknown action");
  eq((await handle(deps, pini, "__proto__", {})).status, 404, "no prototype tricks");
  await fails(null, "create_group", { name: "x", display_name: "y" }, "not_signed_in");
  await fails(pini, "update_group", { group_id: "not-a-uuid" }, "invalid_input");
  await fails(pini, "create_group", { name: "x", display_name: "   " }, "invalid_name");
  await fails(pini, "create_group", { name: "x", display_name: "a\u0000b" }, "invalid_name");
  await fails(pini, "create_group", { name: "  ", display_name: "Pini" }, "invalid_name");
  await fails(pini, "create_group", { name: "x".repeat(61), display_name: "Pini" }, "invalid_name");
  eq((await handle(deps, pini, "create_group", [] as unknown as Record<string, unknown>)).status, 400, "array body");
});

test("groups: create, see, manage, and always keep an admin", async () => {
  const pini = await user("pini"), dobi = await user("dobi"), out = await user("outsider");
  const guest = await user("guest", true);

  await fails(guest, "create_group", { name: "x", display_name: "x" }, "must_register");
  const g = await newGroup(pini, { starts_on: "2027-01-10", ends_on: "2027-01-15" });
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g} and role = 'admin'`), 1, "creator is admin");
  eq(await count(sql`select count(*) n from public.invites where group_id = ${g}`), 1, "starts with an invite");

  const code = (await invite(g)).code;
  eq((await call(dobi, "join_group", { code, display_name: "דובי" })).status, "joined", "dobi joins");
  eq((await call(guest, "join_group", { code, display_name: "אורח" })).status, "joined", "a guest joins");

  for (const [action, body] of [
    ["update_group", { group_id: g, name: "x" }],
    ["create_invite", { group_id: g }],
    ["remove_member", { group_id: g, user_id: guest }],
    ["set_member_role", { group_id: g, user_id: dobi, role: "admin" }],
    ["delete_group", { group_id: g }],
  ] as const) {
    await fails(dobi, action, body, "not_admin");
    await fails(out, action, body, "not_admin");
  }

  await fails(pini, "set_member_role", { group_id: g, user_id: guest, role: "admin" }, "admin_must_register");
  await fails(pini, "set_member_role", { group_id: g, user_id: pini, role: "member" }, "last_admin");
  await fails(pini, "remove_member", { group_id: g, user_id: pini }, "use_leave_group");
  await call(pini, "set_member_role", { group_id: g, user_id: dobi, role: "admin" });
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g} and role = 'admin'`), 2, "two admins");

  // The last admin leaves: the earliest registered member takes over.
  await call(pini, "set_member_role", { group_id: g, user_id: dobi, role: "member" });
  await call(pini, "leave_group", { group_id: g });
  eq((await sql`select role from public.group_members where group_id = ${g} and user_id = ${dobi}`)[0].role, "admin",
    "a registered member became admin");

  // Only guests left: no admin until someone registers and claims it.
  await call(dobi, "leave_group", { group_id: g });
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g} and role = 'admin'`), 0, "no admin");
  await fails(guest, "claim_admin", { group_id: g }, "must_register");
  await sql`update auth.users set is_anonymous = false where id = ${guest}`;
  await call(guest, "claim_admin", { group_id: g });
  await fails(guest, "claim_admin", { group_id: g }, "group_has_admin");

  await call(guest, "update_group", { group_id: g, name: "Renamed", starts_on: null, ends_on: null });
  eq((await sql`select name from public.groups where id = ${g}`)[0].name, "Renamed", "admin renames");
  await fails(guest, "update_group", { group_id: g, name: "", starts_on: null, ends_on: null }, "invalid_name");

  await call(guest, "leave_group", { group_id: g });
  eq(await count(sql`select count(*) n from public.groups where id = ${g}`), 0, "the last member leaving deletes the group");
  eq(await count(sql`select count(*) n from private.audit_log where group_id = ${g} and action = 'joined'`), 2,
    "joins are in the audit log");
});

test("invites: code, link, revoke, expiry, limits, approval", async () => {
  const admin = await user("admin");
  const today = new Date().toISOString().slice(0, 10);
  const plus = (d: number) => new Date(Date.now() + d * 86400e3).toISOString().slice(0, 10);
  const g = await newGroup(admin, { starts_on: today, ends_on: plus(5) });
  const [a, b, c, d, e, x] = await Promise.all(["a", "b", "c", "d", "e", "x"].map((n) => user(n, true)));
  let inv = await invite(g);

  eq((await call(a, "join_group", { code: inv.code.toLowerCase(), display_name: "A" })).status, "joined", "lower case");
  eq((await call(b, "join_group", { code: inv.code.slice(0, 3) + "-" + inv.code.slice(3), display_name: "B" })).status,
    "joined", "with a dash");
  eq((await call(c, "join_group", { code: inv.token, display_name: "C" })).status, "joined", "the link token");
  eq((await call(c, "join_group", { code: inv.token, display_name: "C" })).status, "already_member", "twice is harmless");
  eq((await sql`select uses from public.invites where code = ${inv.code}`)[0].uses, 3, "uses counted");

  const preview = await call(d, "invite_preview", { code: inv.code });
  eq([preview.status, preview.members.length, preview.already_member], ["ok", 4, false], "preview: group and names");
  eq(preview.starts_on, today, "dates come back as plain dates");

  const old = inv.code;
  const fresh = await call(admin, "create_invite", { group_id: g });
  if (fresh.code === old) throw new Error("replace gave the same code");
  eq((await call(d, "join_group", { code: old, display_name: "D" })).status, "invalid_code", "the old code is dead");
  eq((await call(d, "join_group", { code: fresh.code, display_name: "D" })).status, "joined", "the new one works");
  const revoked = (await sql`select id from public.invites where group_id = ${g} and revoked_at is null`)[0].id;
  await call(admin, "revoke_invite", { invite_id: revoked });
  eq((await call(e, "join_group", { code: fresh.code, display_name: "E" })).status, "invalid_code", "revoked");

  // Expiry: by default the day after the group ends.
  inv = await call(admin, "create_invite", { group_id: g });
  await call(admin, "update_group", { group_id: g, name: "Ski", starts_on: plus(-10), ends_on: plus(-2) });
  eq((await call(e, "invite_preview", { code: inv.code })).status, "invalid_code", "expires after the trip");
  await call(admin, "update_group", { group_id: g, name: "Ski", starts_on: today, ends_on: plus(5) });
  eq((await call(e, "invite_preview", { code: inv.code })).status, "ok", "moving the dates brings it back");
  inv = await call(admin, "create_invite", { group_id: g, expires_at: new Date(Date.now() - 60e3).toISOString() });
  eq((await call(e, "invite_preview", { code: inv.code })).status, "invalid_code", "explicit expiry");
  inv = await call(admin, "create_invite", { group_id: g, max_uses: 1 });
  eq((await call(e, "join_group", { code: inv.code, display_name: "E" })).status, "joined", "one use works once");
  eq((await call(x, "join_group", { code: inv.code, display_name: "X" })).status, "invalid_code", "and not twice");

  // Manual approval
  inv = await call(admin, "create_invite", { group_id: g, requires_approval: true });
  await call(admin, "remove_member", { group_id: g, user_id: e });
  const hidden = await call(x, "invite_preview", { code: inv.code });
  eq([hidden.status, hidden.requires_approval, hidden.members.length], ["ok", true, 0], "an invitation that needs approval hides the names");
  const shown = await call(admin, "invite_preview", { code: inv.code });
  eq([shown.already_member, shown.members.length > 0], [true, true], "but a member still sees them");
  eq((await call(e, "join_group", { code: inv.code, display_name: "E again" })).status, "pending", "waits");
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g} and user_id = ${e}`), 0, "not yet");
  const req = (await sql`select id from public.join_requests where user_id = ${e} and status = 'pending'`)[0].id;
  await fails(a, "decide_join_request", { request_id: req, approve: true }, "not_admin");
  await call(admin, "decide_join_request", { request_id: req, approve: true });
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g} and user_id = ${e}`), 1, "approved");
  await fails(admin, "decide_join_request", { request_id: req, approve: true }, "request_closed");
});

test("guessing the code: per person, per day, and per network address", async () => {
  const admin = await user("admin");
  const g = await newGroup(admin);
  const inv = await invite(g);
  const attacker = await user("attacker", true), other = await user("other", true);

  for (const c of ["AAAAAA", "BBBBBB", "CCCCCC", "DDDDDD", "EEEEEE"]) {
    await call(attacker, "join_group", { code: c, display_name: "X" });
  }
  eq((await call(attacker, "join_group", { code: inv.code, display_name: "X" })).status, "rate_limited", "blocked");
  eq((await call(attacker, "invite_preview", { code: inv.code })).status, "rate_limited", "preview too");
  eq((await call(attacker, "request_reclaim", { code: inv.code, member_id: admin })).status, "rate_limited", "reclaim too");
  eq((await call(other, "invite_preview", { code: inv.code })).status, "ok", "others are not blocked");

  await sql`insert into private.invite_attempts (user_id, at, ok)
            select ${other}, now() - interval '1 hour' * i, false from generate_series(1, 20) i`;
  eq((await call(other, "invite_preview", { code: inv.code })).status, "rate_limited", "twenty a day");

  // Many identities from one address: blocked by the address, while everyone else still joins (R-13).
  const fresh = await user("fresh", true), elsewhere = await user("elsewhere", true);
  const there = { ipKey: "abc" }, here = { ipKey: "def" };
  await sql`insert into private.invite_attempts (user_id, ok, ip_key) select gen_random_uuid(), false, 'abc' from generate_series(1, 60)`;
  await sql`insert into private.invite_attempts (user_id, ok, ip_key) select gen_random_uuid(), false, 'xyz' from generate_series(1, 5000)`;
  try {
    eq((await call(fresh, "invite_preview", { code: inv.code }, 200, there)).status, "rate_limited", "sixty an hour per address");
    eq((await call(fresh, "invite_preview", { code: inv.token }, 200, there)).status, "ok", "links keep working");
    eq((await call(elsewhere, "invite_preview", { code: inv.code }, 200, here)).status, "ok",
      "thousands of wrong guesses from elsewhere do not close joining for others");
    eq((await call(elsewhere, "invite_preview", { code: inv.code })).status, "ok", "no address known: per person only");
    await call(elsewhere, "invite_preview", { code: "ZZZZZZ" }, 200, here);
    eq(await count(sql`select count(*) n from private.invite_attempts where user_id = ${elsewhere} and ip_key = 'def'`), 2,
      "attempts keep the address key");
  } finally {
    await sql`delete from private.invite_attempts`;
  }
  eq(await count(sql`select count(*) n from private.audit_log where user_id = ${attacker} and action = 'invite_rate_limited'`),
    3, "blocked attempts are in the audit log");
});

test("trips: same flight, an admin filling in, one trip per group", async () => {
  const pini = await user("pini"), dobi = await user("dobi"), out = await user("outsider");
  const guest = await user("guest", true);
  const [ski] = await sql`insert into public.trips (owner_id, destination, out_flight, out_from, out_to, out_date)
                          values (${pini}, 'Gudauri', '6H 897', 'TLV', 'TBS', '2027-01-10') returning id`;
  const [work] = await sql`insert into public.trips (owner_id, destination) values (${pini}, 'Elsewhere') returning id`;
  const g = await newGroup(pini, { trip_id: ski.id });
  const w = await newGroup(pini, { trip_id: work.id });
  const code = (await invite(g)).code;

  await call(dobi, "join_group", { code, display_name: "Dobi" });
  await fails(dobi, "set_my_membership", { group_id: g, display_name: "Dobi", trip_id: ski.id }, "trip_not_yours");
  const mine = (await call(dobi, "same_flight", { group_id: g, trip_id: ski.id })).trip_id;
  const [copy] = await sql`select owner_id, out_flight from public.trips where id = ${mine}`;
  eq([copy.owner_id, copy.out_flight], [dobi, "6H 897"], "same flight copies into a trip of their own");
  await fails(out, "same_flight", { group_id: g, trip_id: ski.id }, "not_member");
  await fails(dobi, "same_flight", { group_id: g, trip_id: work.id }, "trip_not_in_group");

  // One trip per person (decision 27): again, it overwrites the trip this group shows, no new row.
  await sql`update public.trips set out_flight = '6H 899' where id = ${ski.id}`;
  eq((await call(dobi, "same_flight", { group_id: g, trip_id: ski.id })).trip_id, mine, "the same trip again");
  eq(await count(sql`select count(*) n from public.trips where owner_id = ${dobi}`), 1, "still one trip");
  const [again] = await sql`select out_flight, entered_by from public.trips where id = ${mine}`;
  eq([again.out_flight, again.entered_by], ["6H 899", dobi], "updated in place, entered by them");
  // The trip the app keeps (my_trip_id), even when this group shows none yet: that one is overwritten.
  const yuda = await user("yuda");
  await call(yuda, "join_group", { code, display_name: "Yuda" });
  const [kept] = await sql`insert into public.trips (owner_id, out_flight) values (${yuda}, 'LY 1') returning id`;
  eq((await call(yuda, "same_flight", { group_id: g, trip_id: ski.id, my_trip_id: kept.id })).trip_id, kept.id, "the kept trip");
  const [k] = await sql`select out_flight, out_to from public.trips where id = ${kept.id}`;
  eq([k.out_flight, k.out_to], ["6H 899", "TBS"], "the kept trip now has the flight");
  eq(await count(sql`select count(*) n from public.trips where owner_id = ${yuda}`), 1, "no second trip");
  await fails(yuda, "same_flight", { group_id: g, trip_id: ski.id, my_trip_id: mine }, "trip_not_yours");

  // An admin fills in for a member who has not.
  await call(guest, "join_group", { code, display_name: "Guest" });
  await fails(dobi, "set_member_trip", { group_id: g, user_id: guest, trip: { out_flight: "6H 897" } }, "not_admin");
  const t = (await call(pini, "set_member_trip", {
    group_id: g, user_id: guest, trip: { out_date: "2027-01-10", out_flight: "6H 897", out_from: "TLV", out_to: "TBS" },
  })).trip_id;
  const [filled] = await sql`select owner_id, entered_by, out_flight from public.trips where id = ${t}`;
  eq([filled.owner_id, filled.entered_by, filled.out_flight], [guest, pini, "6H 897"], "theirs, entered by the admin");
  await call(pini, "set_member_trip", { group_id: g, user_id: guest, trip: { out_flight: "6H 111" } });
  // The member edits it (as the app does, directly): now it is theirs.
  await sql.begin(async (tx) => {
    await tx`select set_config('request.jwt.claims', ${JSON.stringify({ sub: guest, role: "authenticated" })}, true)`;
    await tx`set local role authenticated`;
    await tx`update public.trips set out_flight = '6H 897' where id = ${t}`;
  });
  eq((await sql`select entered_by from public.trips where id = ${t}`)[0].entered_by, guest, "edited: now theirs");
  await fails(pini, "set_member_trip", { group_id: g, user_id: guest, trip: { out_flight: "6H 111" } }, "member_owns_trip");
  await fails(pini, "set_member_trip", { group_id: g, user_id: dobi, trip: { out_from: "tel" } }, "member_owns_trip");
  const fresh = await user("fresh", true);
  await call(fresh, "join_group", { code, display_name: "Fresh" });
  await fails(pini, "set_member_trip", { group_id: g, user_id: fresh, trip: { out_from: "tel" } }, "invalid_input");

  // Each group sees only its own trip of Pini's (checked through the policies).
  await call(dobi, "join_group", { code: (await invite(w)).code, display_name: "Dobi" });
  const seen = await sql.begin(async (tx) => {
    await tx`select set_config('request.jwt.claims', ${JSON.stringify({ sub: dobi, role: "authenticated" })}, true)`;
    await tx`set local role authenticated`;
    return await tx`select destination from public.trips where owner_id = ${pini} order by destination`;
  });
  eq(seen.map((r) => r.destination), ["Elsewhere", "Gudauri"], "in both groups: both trips, one per group");
});

test("scores: the best is kept, and the group sees a leaderboard", async () => {
  const pini = await user("pini"), out = await user("outsider"), guest = await user("guest", true);
  const g = await newGroup(pini, { display_name: "Pini" });
  await call(guest, "join_group", { code: (await invite(g)).code, display_name: "Guest" });
  eq((await call(guest, "submit_score", { game: "downhill", score: 500 })).best, 500, "first");
  eq((await call(guest, "submit_score", { game: "downhill", score: 300 })).best, 500, "lower does not replace");
  eq((await call(guest, "submit_score", { game: "downhill", score: 900 })).best, 900, "higher does");
  await fails(guest, "submit_score", { game: "downhill", score: -1 }, "invalid_input");
  await fails(guest, "submit_score", { game: "Not A Game!", score: 1 }, "invalid_input");
  await fails(guest, "submit_score", { game: "downhill", score: 1.5 }, "invalid_input");
  await call(pini, "submit_score", { game: "downhill", score: 700 });
  const board = await call(pini, "group_leaderboard", { group_id: g, game: "downhill" });
  eq(board.map((r: { display_name: string; best: number }) => [r.display_name, r.best]), [["Guest", 900], ["Pini", 700]],
    "best first, names in the group");
  await fails(out, "group_leaderboard", { group_id: g, game: "downhill" }, "not_member");
});

test("I'm already in the group: the admin approves, only that group moves", async () => {
  const pini = await user("pini"), stranger = await user("stranger", true);
  const oldPhone = await user("old_phone", true), newPhone = await user("new_phone", true);
  const g = await newGroup(pini), other = await newGroup(pini);
  const code = (await invite(g)).code;
  await call(oldPhone, "join_group", { code, display_name: "Moishi" });
  await call(oldPhone, "join_group", { code: (await invite(other)).code, display_name: "Moishi" });
  const [t] = await sql`insert into public.trips (owner_id, out_flight) values (${oldPhone}, '6H 897') returning id`;
  await call(oldPhone, "set_my_membership", { group_id: g, display_name: "Moishi", trip_id: t.id });
  await call(oldPhone, "submit_score", { game: "downhill", score: 400 });

  eq((await call(newPhone, "request_reclaim", { code, member_id: oldPhone })).status, "pending", "asks");
  eq((await call(newPhone, "request_reclaim", { code, member_id: pini })).status, "sign_in_instead", "registered: sign in");
  eq((await call(newPhone, "request_reclaim", { code, member_id: crypto.randomUUID() })).status, "no_such_member", "unknown");
  const req = (await sql`select id from public.join_requests where user_id = ${newPhone} and status = 'pending'`)[0].id;
  await call(pini, "decide_join_request", { request_id: req, approve: true });

  const [m] = await sql`select gm.display_name, t.out_flight from public.group_members gm
                        left join public.trips t on t.id = gm.trip_id where gm.group_id = ${g} and gm.user_id = ${newPhone}`;
  eq([m.display_name, m.out_flight], ["Moishi", "6H 897"], "back as Moishi, with the flight");
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g} and display_name = 'Moishi'`), 1,
    "no duplicate");
  eq((await sql`select best from public.scores where user_id = ${newPhone}`)[0].best, 400, "and the score");
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${other} and user_id = ${newPhone}`), 0,
    "not the other group");

  await call(stranger, "request_reclaim", { code, member_id: newPhone });
  const r2 = (await sql`select id from public.join_requests where user_id = ${stranger} and status = 'pending'`)[0].id;
  await call(pini, "decide_join_request", { request_id: r2, approve: false });
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g} and user_id = ${stranger}`), 0,
    "rejected");
});

test("guest to an existing account: ticket, merge, the guest is gone", async () => {
  const pini = await user("pini"), existing = await user("existing"), guest = await user("guest", true);
  const g = await newGroup(pini);
  await call(guest, "join_group", { code: (await invite(g)).code, display_name: "Shruli" });
  const [t] = await sql`insert into public.trips (owner_id, out_flight) values (${guest}, '6H 123') returning id`;
  await call(guest, "set_my_membership", { group_id: g, display_name: "Shruli", trip_id: t.id });
  await call(guest, "submit_score", { game: "downhill", score: 800 });
  await call(existing, "submit_score", { game: "downhill", score: 100 });

  const { ticket } = await call(guest, "create_merge_ticket");
  await fails(pini, "create_merge_ticket", {}, "not_a_guest");
  await fails(guest, "merge_guest", { ticket }, "must_register");
  await fails(existing, "merge_guest", { ticket: "wrong" }, "invalid_ticket");
  await call(existing, "merge_guest", { ticket });
  const [m] = await sql`select display_name, trip_id from public.group_members where group_id = ${g} and user_id = ${existing}`;
  eq([m.display_name, m.trip_id], ["Shruli", t.id], "in the group, same name, same trip");
  eq((await sql`select owner_id from public.trips where id = ${t.id}`)[0].owner_id, existing, "owns the trip");
  eq((await sql`select best from public.scores where user_id = ${existing}`)[0].best, 800, "keeps the better score");
  eq(await count(sql`select count(*) n from auth.users where id = ${guest}`), 0, "the guest identity is gone");
  await fails(existing, "merge_guest", { ticket }, "invalid_ticket");
});

test("deleting an account", async () => {
  const pini = await user("pini"), dobi = await user("dobi"), guest = await user("guest", true);
  const g = await newGroup(pini), alone = await newGroup(pini), guests = await newGroup(pini);
  const stays = await user("stays", true);
  await call(dobi, "join_group", { code: (await invite(g)).code, display_name: "Dobi" });
  await call(stays, "join_group", { code: (await invite(g)).code, display_name: "Stays" });
  await call(guest, "join_group", { code: (await invite(guests)).code, display_name: "Guest" });
  await sql`insert into public.meetups (group_id, station, meet_at, created_by) values (${g}, 'kudebi-top', now(), ${dobi})`;
  await sql`insert into public.trips (owner_id, entered_by) values (${dobi}, ${pini})`;
  await call(pini, "submit_score", { game: "downhill", score: 1 });

  await call(pini, "delete_my_account");
  eq(await count(sql`select count(*) n from auth.users where id = ${pini}`), 0, "the user is gone");
  eq(await count(sql`select count(*) n from public.profiles where id = ${pini}`), 0, "the profile");
  eq(await count(sql`select count(*) n from public.scores where user_id = ${pini}`), 0, "the scores");
  eq((await sql`select role from public.group_members where group_id = ${g} and user_id = ${dobi}`)[0].role, "admin",
    "a registered member became admin");
  eq(await count(sql`select count(*) n from public.groups where id = ${alone}`), 0, "a group left empty is deleted");
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${guests} and role = 'admin'`), 0,
    "only guests left: waits for someone to claim it");
  eq(await count(sql`select count(*) n from private.audit_log where user_id = ${pini} or target = ${pini}`), 0,
    "not left in the audit log");

  await call(dobi, "delete_my_account");
  eq(await count(sql`select count(*) n from public.meetups where group_id = ${g} and created_by is null`), 1,
    "a meetup stays in the group without its author's name");
});

test("keepalive: open to anyone, cleans once a day", async () => {
  const pini = await user("pini");
  const oldGuest = await user("old_guest", true), newGuest = await user("new_guest", true);
  const member = await user("member_guest", true);
  await sql`update auth.users set created_at = now() - interval '40 days' where id in (${oldGuest}, ${member})`;
  const g = await newGroup(pini);
  await call(member, "join_group", { code: (await invite(g)).code, display_name: "Guest" });
  await sql`update private.heartbeat set cleaned_at = null`;
  const first = await call(null, "keepalive");
  const second = await call(null, "keepalive");
  eq(first.removed_guests >= 1, true, "the first call of the day cleans");
  eq(second.removed_guests, 0, "later calls that day do not");
  eq(second.beats, first.beats + 1, "each call counts");
  eq(await count(sql`select count(*) n from auth.users where id = ${oldGuest}`), 0, "an old guest in no group is gone");
  eq(await count(sql`select count(*) n from auth.users where id in (${newGuest}, ${member})`), 2,
    "new guests and guests in a group stay");
});

test("the account name reaches every group (decision 50)", async () => {
  const pini = await user("pini");
  const g1 = await newGroup(pini);
  const g2 = await newGroup(pini);
  const other = await user("other");
  const g3 = await newGroup(other);
  await call(pini, "set_my_membership", { group_id: g2, display_name: "Pini in g2", trip_id: null });
  await sql`update public.profiles set display_name = 'Pinchas' where id = ${pini}`;
  const names = await sql`select group_id, display_name from public.group_members where user_id = ${pini}`;
  eq(names.length, 2, "two groups");
  for (const n of names) eq(n.display_name, "Pinchas", "the new name in every group, also one named by hand");
  eq((await sql`select display_name from public.group_members where group_id = ${g3}`)[0].display_name, "Admin",
    "nobody else changes");
  await sql`update public.profiles set lang = 'en' where id = ${pini}`;
  await call(pini, "set_my_membership", { group_id: g1, display_name: "P", trip_id: null });
  await sql`update public.profiles set lang = 'ru' where id = ${pini}`;
  eq((await sql`select display_name from public.group_members where group_id = ${g1} and user_id = ${pini}`)[0]
    .display_name, "P", "only a name change spreads, not a change of language");
});

// R-2: the function runs as the database's owner, so every action checks by itself who may act. Every action has a
// row here: a stranger (registered, not in the group) and, for admin actions, a plain member must be refused, and
// nothing in the group may change. A new action without a row fails this test.
test("authorization matrix: every action against a stranger, a member and an admin", async () => {
  const admin = await user("admin"), member = await user("member"), stranger = await user("stranger");
  const g = await newGroup(admin);
  await call(member, "join_group", { code: (await invite(g)).code, display_name: "Member" });
  const [t] = await sql`insert into public.trips (owner_id, out_date) values (${member}, '2027-01-10') returning id`;
  await call(member, "set_my_membership", { group_id: g, display_name: "Member", trip_id: t.id });
  const inv = await invite(g);
  const [inviteRow] = await sql`select id from public.invites where group_id = ${g} and revoked_at is null`;
  const [req] = await sql`insert into public.join_requests (group_id, user_id, display_name, kind)
                          values (${g}, ${await user("asker", true)}, 'Asker', 'approval') returning id`;

  // who may: "any" = anyone signed in, "member" = members of the group, "admin" = its admins
  const matrix: Record<string, { who: "any" | "member" | "admin" | "self"; body: Record<string, unknown> }> = {
    my_account: { who: "self", body: {} },
    delete_my_account: { who: "self", body: {} },
    create_merge_ticket: { who: "self", body: {} },
    merge_guest: { who: "self", body: { ticket: "x".repeat(32) } },
    create_group: { who: "self", body: { name: "Mine", display_name: "Me" } },
    update_group: { who: "admin", body: { group_id: g, name: "Taken" } },
    delete_group: { who: "admin", body: { group_id: g } },
    create_invite: { who: "admin", body: { group_id: g } },
    revoke_invite: { who: "admin", body: { invite_id: inviteRow.id } },
    invite_preview: { who: "any", body: { code: inv.code } },
    join_group: { who: "any", body: { code: inv.code, display_name: "X" } },
    request_reclaim: { who: "any", body: { code: inv.code, member_id: member } },
    cancel_join_request: { who: "self", body: { request_id: req.id } },
    decide_join_request: { who: "admin", body: { request_id: req.id, approve: false } },
    leave_group: { who: "self", body: { group_id: g } },
    remove_member: { who: "admin", body: { group_id: g, user_id: member } },
    set_member_role: { who: "admin", body: { group_id: g, user_id: member, role: "admin" } },
    claim_admin: { who: "member", body: { group_id: g } },
    set_my_membership: { who: "self", body: { group_id: g, display_name: "Y" } },
    same_flight: { who: "member", body: { group_id: g, trip_id: t.id } },
    set_member_trip: { who: "admin", body: { group_id: g, user_id: member, trip: { out_date: "2027-01-11" } } },
    submit_score: { who: "self", body: { game: "descent", score: 1 } },
    group_leaderboard: { who: "member", body: { group_id: g, game: "descent" } },
  };
  eq(Object.keys(matrix).sort(), [...ACTION_NAMES].sort(), "every action has a row in the matrix");

  const snapshot = async () =>
    JSON.stringify(await sql`select
      (select json_agg(g order by id) from public.groups g where id = ${g}) as groups,
      (select json_agg(m order by user_id) from public.group_members m where group_id = ${g}) as members,
      (select json_agg(i order by id) from public.invites i where group_id = ${g}) as invites,
      (select json_agg(q order by id) from public.join_requests q where group_id = ${g}) as requests,
      (select json_agg(t order by id) from public.trips t where owner_id = ${member}) as trips`);
  const before = await snapshot();
  const refused = ["not_member", "not_admin", "trip_not_in_group"];
  for (const [action, row] of Object.entries(matrix)) {
    if (row.who === "admin" || row.who === "member") {
      const r = await handle(deps, stranger, action, row.body);
      eq(refused.includes((r.body as { error?: string }).error ?? ""), true, `${action}: a stranger is refused (${JSON.stringify(r.body)})`);
    }
    if (row.who === "admin") {
      const r = await handle(deps, member, action, row.body);
      eq((r.body as { error?: string }).error, "not_admin", `${action}: a member who is not an admin is refused`);
    }
  }
  eq(await snapshot(), before, "nothing in the group changed after all the refusals");

  // a stranger acting on their own things never reaches the group
  await call(stranger, "set_my_membership", { group_id: g, display_name: "Y" }, 403);
  await call(stranger, "leave_group", { group_id: g });
  await call(stranger, "cancel_join_request", { request_id: req.id });
  eq(await snapshot(), before, "and not after acting on their own things");

  // and the admin may do the admin actions
  for (const action of ["update_group", "create_invite", "decide_join_request", "set_member_trip"]) {
    await call(admin, action, matrix[action].body);
  }
});

test("a field that was not sent does not change (R-14)", async () => {
  const admin = await user("admin");
  const g = await newGroup(admin, { starts_on: "2027-01-10", ends_on: "2027-01-15" });
  const [t] = await sql`insert into public.trips (owner_id, out_date) values (${admin}, '2027-01-10') returning id`;
  await call(admin, "set_my_membership", { group_id: g, display_name: "Admin", trip_id: t.id });

  await call(admin, "update_group", { group_id: g, name: "Renamed" });
  const [a] = await sql`select name, starts_on::text, ends_on::text from public.groups where id = ${g}`;
  eq([a.name, a.starts_on, a.ends_on], ["Renamed", "2027-01-10", "2027-01-15"], "the dates stay when not sent");
  await call(admin, "update_group", { group_id: g, ends_on: null });
  const [b] = await sql`select name, starts_on::text, ends_on from public.groups where id = ${g}`;
  eq([b.name, b.starts_on, b.ends_on], ["Renamed", "2027-01-10", null], "a date sent as null is cleared, the rest stays");

  await call(admin, "set_my_membership", { group_id: g, display_name: "New name" });
  const [m] = await sql`select display_name, trip_id from public.group_members where group_id = ${g} and user_id = ${admin}`;
  eq([m.display_name, m.trip_id], ["New name", t.id], "the trip stays when not sent");
  await call(admin, "set_my_membership", { group_id: g, trip_id: null });
  const [n] = await sql`select display_name, trip_id from public.group_members where group_id = ${g} and user_id = ${admin}`;
  eq([n.display_name, n.trip_id], ["New name", null], "trip_id null hides it, the name stays");
  await call(admin, "update_group", { group_id: g, name: "" }, 400);
});

test("deleting an account finishes even when the identity cannot be deleted at once (R-15)", async () => {
  const pini = await user("pini");
  await newGroup(pini);
  let down = true;
  const flaky: Deps = {
    sql,
    deleteAuthUser: async (id) => {
      if (down) throw new Error("auth is down");
      await sql`delete from auth.users where id = ${id}`;
    },
  };
  const r = await handle(flaky, pini, "delete_my_account", {});
  eq(r.status, 200, "the person is told it is done");
  eq(await count(sql`select count(*) n from public.group_members where user_id = ${pini}`), 0, "their rows are gone");
  eq(await count(sql`select count(*) n from private.pending_deletions where user_id = ${pini}`), 1, "the identity waits");
  await sql`update private.heartbeat set cleaned_at = null`;
  eq((await handle(flaky, null, "keepalive", {})).status, 200, "a keepalive that cannot finish it still answers");
  eq(await count(sql`select count(*) n from auth.users where id = ${pini}`), 1, "still there");
  down = false;
  await sql`update private.heartbeat set cleaned_at = null`;
  const k = (await handle(flaky, null, "keepalive", {})).body as { finished_deletions: number };
  eq(k.finished_deletions >= 1, true, "the next keepalive finishes it");
  eq(await count(sql`select count(*) n from auth.users where id = ${pini}`), 0, "the identity is gone");
  eq(await count(sql`select count(*) n from private.pending_deletions where user_id = ${pini}`), 0, "and nothing waits");
});

test("the last use of an invite goes to one person only (R-20)", async () => {
  const admin = await user("admin");
  const g = await newGroup(admin);
  const inv = await call(admin, "create_invite", { group_id: g, max_uses: 1 });
  const a = await user("a", true), b = await user("b", true);
  const [ra, rb] = await Promise.all([
    call(a, "join_group", { code: inv.code, display_name: "A" }),
    call(b, "join_group", { code: inv.code, display_name: "B" }),
  ]);
  eq([ra.status, rb.status].sort(), ["invalid_code", "joined"], "one joins, the other is told the code is used up");
  eq(await count(sql`select count(*) n from public.group_members where group_id = ${g}`), 2, "admin and one more");
  eq((await sql`select uses from public.invites where id = ${inv.id}`)[0].uses, 1, "one use counted");
});

test("keepalive says when the last beat is more than a day and a half old (R-12)", async () => {
  await sql`update private.heartbeat set beat_at = now() - interval '2 days'`;
  eq((await call(null, "keepalive")).stale, true, "a missed day");
  eq((await call(null, "keepalive")).stale, false, "the next one is on time");
});

test("protection mode: a crowd of wrong guesses makes a right code ask an admin (R-13)", async () => {
  const admin = await user("admin");
  const g = await newGroup(admin);
  const inv = await invite(g);
  const a = await user("a", true), b = await user("b", true), c = await user("c", true);
  await sql`insert into private.invite_attempts (user_id, ok, ip_key)
            select gen_random_uuid(), false, 'k' || (i % 50) from generate_series(1, 1000) i`;
  try {
    const p = await call(a, "invite_preview", { code: inv.code }, 200, { ipKey: "a1" });
    eq([p.status, p.requires_approval, p.members.length], ["ok", true, 0], "the preview says it needs an admin, no names");
    eq((await call(a, "join_group", { code: inv.code, display_name: "A" }, 200, { ipKey: "a1" })).status, "pending",
      "a right code still works, but waits for an admin");
    eq((await call(b, "join_group", { code: inv.token, display_name: "B" })).status, "joined", "links are not affected");
    eq((await call(null, "keepalive")).guarded_last_day >= 2, true, "the daily task reports it");
  } finally {
    await sql`delete from private.invite_attempts`;
  }
  eq((await call(c, "join_group", { code: inv.code, display_name: "C" })).status, "joined", "below the line, as before");
});

test("the caller's address comes from the proxy, not from what the caller sent (R-13)", () => {
  eq(clientIp(new Headers({ "cf-connecting-ip": "1.1.1.1", "x-forwarded-for": "6.6.6.6, 2.2.2.2" })), "1.1.1.1", "Cloudflare's first");
  eq(clientIp(new Headers({ "x-real-ip": "3.3.3.3", "x-forwarded-for": "6.6.6.6" })), "3.3.3.3", "then the proxy's");
  eq(clientIp(new Headers({ "x-forwarded-for": "6.6.6.6, 7.7.7.7, 2.2.2.2" })), "2.2.2.2", "else the last one, the proxy's");
  eq(clientIp(new Headers({})), undefined, "none: not counted per address");
  return Promise.resolve();
});

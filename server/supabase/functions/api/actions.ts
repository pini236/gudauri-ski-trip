// The server's actions (decision 40). Apps call them over HTTP through
// index.ts: POST /functions/v1/api/<action> with a JSON body.
//
// Every action runs in one database transaction: all of it happens, or
// none of it. The database still enforces its own rules underneath
// (row level security for what apps read directly, and the guards in
// server/supabase/migrations/), so a bug here cannot open other
// people's data to an app.
//
// Errors are short codes (for example "not_admin"); each app maps them
// to a message in its own language. The list is in server/README.md.

import type postgres from "postgres";

type Sql = postgres.Sql;
type Tx = postgres.TransactionSql;
type Body = Record<string, unknown>;

export interface Deps {
  sql: Sql;
  // Deletes a user from Supabase Auth (cascades to their profile and
  // everything that references it). In production: the Auth admin API.
  deleteAuthUser: (id: string) => Promise<void>;
  log?: (entry: Record<string, unknown>) => void;
}

export interface Result {
  status: number;
  body: unknown;
}

export class ApiError extends Error {
  constructor(public code: string, public status = 400) {
    super(code);
  }
}

// Abuse protection; the numbers are a first guess (docs/USERS.md).
export const LIMITS = {
  tripsPerUser: 20, // enforced by the database trigger
  groupsCreatedPerUser: 20,
  membersPerGroup: 100,
  failsPer15Min: 5,
  failsPerDay: 20,
  globalFailsPerHour: 300,
  anonCleanupDays: 30,
  auditDays: 180,
};

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I or O
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TRIP_FIELDS = [
  "destination",
  "out_date", "out_flight", "out_from", "out_to", "out_departs", "out_arrives",
  "ret_date", "ret_flight", "ret_from", "ret_to", "ret_departs", "ret_arrives",
  "ski_from", "ski_to",
] as const;

// ---------------------------------------------------------------------
// Input
// ---------------------------------------------------------------------

function uuid(body: Body, key: string): string {
  const v = body[key];
  if (typeof v !== "string" || !UUID.test(v)) throw new ApiError("invalid_input");
  return v.toLowerCase();
}

function optUuid(body: Body, key: string): string | null {
  return body[key] == null ? null : uuid(body, key);
}

function str(body: Body, key: string): string {
  const v = body[key];
  if (typeof v !== "string") throw new ApiError("invalid_input");
  return v;
}

function optDate(body: Body, key: string): string | null {
  const v = body[key];
  if (v == null) return null;
  if (typeof v !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(v) || isNaN(Date.parse(v))) {
    throw new ApiError("invalid_input");
  }
  return v;
}

function bool(body: Body, key: string, fallback: boolean): boolean {
  const v = body[key];
  if (v == null) return fallback;
  if (typeof v !== "boolean") throw new ApiError("invalid_input");
  return v;
}

// A display name: spaces collapsed, 1 to 40 characters, no control characters.
export function cleanName(v: unknown): string {
  if (typeof v !== "string") throw new ApiError("invalid_name");
  const s = v.replace(/\s+/g, " ").trim();
  if ([...s].length < 1 || [...s].length > 40 || /\p{Cc}/u.test(s)) throw new ApiError("invalid_name");
  return s;
}

function groupName(v: unknown): string {
  if (typeof v !== "string") throw new ApiError("invalid_input");
  const s = v.trim();
  if ([...s].length < 1 || [...s].length > 60 || /\p{Cc}/u.test(s)) throw new ApiError("invalid_input");
  return s;
}

// Six letters typed by a person (case and dashes ignored), or a link token.
function normalizeCode(v: unknown): { kind: "code" | "token" | "bad"; value: string } {
  if (typeof v !== "string") return { kind: "bad", value: "" };
  const letters = v.replace(/[\s-]/g, "").toUpperCase();
  if (/^[A-Z]{6}$/.test(letters)) return { kind: "code", value: letters };
  const t = v.trim();
  if (t.length >= 32 && t.length <= 64) return { kind: "token", value: t };
  return { kind: "bad", value: "" };
}

// ---------------------------------------------------------------------
// Random values
// ---------------------------------------------------------------------

function randomCode(): string {
  let out = "";
  while (out.length < 6) {
    const bytes = crypto.getRandomValues(new Uint8Array(12));
    for (const b of bytes) {
      // 240 = 24 * 10: drop the rest so every letter is equally likely.
      if (b < 240 && out.length < 6) out += CODE_ALPHABET[b % 24];
    }
  }
  return out;
}

export function randomToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_");
}

async function sha256(s: string): Promise<string> {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// ---------------------------------------------------------------------
// Checks
// ---------------------------------------------------------------------

async function isRegistered(tx: Tx, user: string): Promise<boolean> {
  const [r] = await tx`select not is_anonymous as ok from auth.users where id = ${user}`;
  return !!r?.ok;
}

async function requireRegistered(tx: Tx, user: string) {
  if (!(await isRegistered(tx, user))) throw new ApiError("must_register", 403);
}

async function role(tx: Tx, group: string, user: string): Promise<string | null> {
  const [r] = await tx`select role from public.group_members where group_id = ${group} and user_id = ${user}`;
  return r?.role ?? null;
}

async function requireMember(tx: Tx, group: string, user: string) {
  if (!(await role(tx, group, user))) throw new ApiError("not_member", 403);
}

async function requireAdmin(tx: Tx, group: string, user: string) {
  if ((await role(tx, group, user)) !== "admin") throw new ApiError("not_admin", 403);
}

// Serializes changes to one group's membership (two people joining the
// last free place, two admins removing each other).
async function lockGroup(tx: Tx, group: string): Promise<boolean> {
  const r = await tx`select 1 from public.groups where id = ${group} for update`;
  return r.length > 0;
}

async function audit(
  tx: Tx, user: string | null, action: string,
  group: string | null = null, target: string | null = null, details: Body = {},
) {
  await tx`insert into private.audit_log (user_id, action, group_id, target, details)
           values (${user}, ${action}, ${group}, ${target}, ${tx.json(details as postgres.JSONValue)})`;
}

// ---------------------------------------------------------------------
// Invites
// ---------------------------------------------------------------------

interface Invite {
  id: string;
  group_id: string;
  requires_approval: boolean;
}

async function newCode(tx: Tx): Promise<string> {
  for (;;) {
    const c = randomCode();
    const r = await tx`select 1 from public.invites where code = ${c}`;
    if (r.length === 0) return c;
  }
}

// Too many wrong guesses by this person, or (for six-letter codes, the
// only guessable kind) by everyone together.
async function attemptsBlocked(tx: Tx, user: string, short: boolean): Promise<boolean> {
  const [r] = await tx`
    select
      count(*) filter (where user_id = ${user} and at > now() - interval '15 minutes') as mine15,
      count(*) filter (where user_id = ${user}) as mine_day,
      count(*) filter (where at > now() - interval '1 hour') as everyone_hour
    from private.invite_attempts
    where not ok and at > now() - interval '1 day'`;
  return Number(r.mine15) >= LIMITS.failsPer15Min ||
    Number(r.mine_day) >= LIMITS.failsPerDay ||
    (short && Number(r.everyone_hour) >= LIMITS.globalFailsPerHour);
}

// A working invite for a code or token, counting the attempt.
async function useCode(tx: Tx, user: string, raw: unknown): Promise<{ invite?: Invite; status: string }> {
  const key = normalizeCode(raw);
  if (await attemptsBlocked(tx, user, key.kind !== "token")) {
    await audit(tx, user, "invite_rate_limited");
    return { status: "rate_limited" };
  }
  let invite: Invite | undefined;
  if (key.kind !== "bad") {
    const rows = await tx<Invite[]>`
      select i.id, i.group_id, i.requires_approval
      from public.invites i join public.groups g on g.id = i.group_id
      where ${key.kind === "code" ? tx`i.code = ${key.value}` : tx`i.token = ${key.value}`}
        and i.revoked_at is null
        and (i.max_uses is null or i.uses < i.max_uses)
        and coalesce(i.expires_at, (g.ends_on + 1)::timestamptz, i.created_at + interval '90 days') > now()`;
    invite = rows[0];
  }
  await tx`insert into private.invite_attempts (user_id, ok) values (${user}, ${!!invite})`;
  return invite ? { invite, status: "ok" } : { status: "invalid_code" };
}

// ---------------------------------------------------------------------
// Membership
// ---------------------------------------------------------------------

async function addMember(tx: Tx, group: string, user: string, name: string, memberRole = "member") {
  const [r] = await tx`select count(*)::int as n from public.group_members where group_id = ${group}`;
  if (r.n >= LIMITS.membersPerGroup) throw new ApiError("group_full", 409);
  await tx`insert into public.group_members (group_id, user_id, role, display_name)
           values (${group}, ${user}, ${memberRole}, ${name})`;
}

// After someone leaves: no members left, delete the group; no admin
// left, the earliest registered member becomes admin; nobody registered,
// the group waits for a registered member to claim it.
async function afterMemberLeft(tx: Tx, group: string) {
  const [r] = await tx`
    select count(*)::int as members, count(*) filter (where role = 'admin')::int as admins
    from public.group_members where group_id = ${group}`;
  if (r.members === 0) {
    await tx`delete from public.groups where id = ${group}`;
    return;
  }
  if (r.admins > 0) return;
  const [next] = await tx`
    select m.user_id from public.group_members m join auth.users u on u.id = m.user_id
    where m.group_id = ${group} and not u.is_anonymous
    order by m.joined_at, m.user_id limit 1`;
  if (next) {
    await tx`update public.group_members set role = 'admin' where group_id = ${group} and user_id = ${next.user_id}`;
    await audit(tx, null, "admin_promoted", group, next.user_id);
  }
}

async function copyTrip(tx: Tx, trip: string, owner: string, enteredBy: string): Promise<string> {
  const [r] = await tx`
    insert into public.trips (owner_id, destination,
      out_date, out_flight, out_from, out_to, out_departs, out_arrives,
      ret_date, ret_flight, ret_from, ret_to, ret_departs, ret_arrives, ski_from, ski_to, entered_by)
    select ${owner}, destination,
      out_date, out_flight, out_from, out_to, out_departs, out_arrives,
      ret_date, ret_flight, ret_from, ret_to, ret_departs, ret_arrives, ski_from, ski_to, ${enteredBy}
    from public.trips where id = ${trip}
    returning id`;
  return r.id;
}

// Move what belongs to `from` into `to`.
//   group null: everything (a guest merging into their own account).
//   group set:  only that group ("I'm already in the group", approved by
//               its admin, who must not hand out access to other groups;
//               so the trip is copied, not moved).
async function absorb(tx: Tx, from: string, to: string, group: string | null) {
  if (group === null) {
    await tx`update public.trips set owner_id = ${to} where owner_id = ${from}`;
    await tx`update public.trips set entered_by = ${to} where entered_by = ${from}`;
  }
  const rows = await tx`
    select group_id, display_name, trip_id, joined_at from public.group_members
    where user_id = ${from} and (${group}::uuid is null or group_id = ${group}::uuid)`;
  for (const m of rows) {
    let trip: string | null = m.trip_id;
    if (group !== null && trip) trip = await copyTrip(tx, trip, to, to);
    await tx`delete from public.group_members where group_id = ${m.group_id} and user_id = ${from}`;
    const has = await tx`select 1 from public.group_members where group_id = ${m.group_id} and user_id = ${to}`;
    if (has.length) {
      await tx`update public.group_members set trip_id = coalesce(trip_id, ${trip})
               where group_id = ${m.group_id} and user_id = ${to}`;
    } else {
      await tx`insert into public.group_members (group_id, user_id, role, display_name, trip_id, joined_at)
               values (${m.group_id}, ${to}, 'member', ${m.display_name}, ${trip}, ${m.joined_at})`;
    }
    await tx`update public.meetups set created_by = ${to} where group_id = ${m.group_id} and created_by = ${from}`;
    await tx`update public.meetups set updated_by = ${to} where group_id = ${m.group_id} and updated_by = ${from}`;
  }
  if (group === null) await tx`delete from public.join_requests where user_id = ${from}`;
  await tx`
    insert into public.scores (user_id, game, best, achieved_at)
    select ${to}, game, best, achieved_at from public.scores where user_id = ${from}
    on conflict (user_id, game) do update set best = excluded.best, achieved_at = excluded.achieved_at
      where excluded.best > public.scores.best`;
}

// Leave every group (keeping each one administered), before deleting a user.
async function leaveAllGroups(tx: Tx, user: string) {
  const groups = await tx`select group_id from public.group_members where user_id = ${user} order by group_id`;
  for (const g of groups) {
    await lockGroup(tx, g.group_id);
    await tx`delete from public.group_members where group_id = ${g.group_id} and user_id = ${user}`;
    await afterMemberLeft(tx, g.group_id);
  }
}

// ---------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------

interface Ctx {
  deps: Deps;
  tx: Tx;
  user: string;
  body: Body;
  after: Array<() => Promise<void>>; // runs after the transaction commits
}

type Action = (c: Ctx) => Promise<unknown>;

const actions: Record<string, Action> = {
  // --- Account -------------------------------------------------------

  async my_account({ tx, user }) {
    const [p] = await tx`
      select p.id, p.display_name, p.lang, u.is_anonymous
      from public.profiles p join auth.users u on u.id = p.id where p.id = ${user}`;
    if (!p) throw new ApiError("not_found", 404);
    return p;
  },

  // Delete my account and everything that is mine (required by Google
  // Play and the App Store). Groups I ran get a new admin; empty groups
  // go; meetups I made stay in the group without my name.
  async delete_my_account({ deps, tx, user, after }) {
    await leaveAllGroups(tx, user);
    await tx`update private.audit_log set user_id = null where user_id = ${user}`;
    await tx`update private.audit_log set target = null where target = ${user}`;
    await audit(tx, null, "account_deleted");
    after.push(() => deps.deleteAuthUser(user));
    return { deleted: true };
  },

  // A guest whose Google or Apple account already exists: step 1, as the
  // guest, a one-time ticket (15 minutes); step 2, merge_guest as the account.
  async create_merge_ticket({ tx, user }) {
    if (await isRegistered(tx, user)) throw new ApiError("not_a_guest", 409);
    const secret = randomToken();
    await tx`delete from private.merge_tickets where anon_user_id = ${user} or expires_at < now()`;
    await tx`insert into private.merge_tickets (secret_hash, anon_user_id, expires_at)
             values (${await sha256(secret)}, ${user}, now() + interval '15 minutes')`;
    return { ticket: secret };
  },

  async merge_guest({ deps, tx, user, body, after }) {
    await requireRegistered(tx, user);
    const [t] = await tx`
      delete from private.merge_tickets
      where secret_hash = ${await sha256(str(body, "ticket"))} and expires_at > now()
      returning anon_user_id`;
    const anon: string | undefined = t?.anon_user_id;
    if (!anon || anon === user || (await isRegistered(tx, anon))) throw new ApiError("invalid_ticket", 400);
    await absorb(tx, anon, user, null);
    await leaveAllGroups(tx, anon);
    await audit(tx, user, "guest_merged", null, anon);
    after.push(() => deps.deleteAuthUser(anon));
    return { merged: true };
  },

  // --- Groups --------------------------------------------------------

  async create_group({ tx, user, body }) {
    await requireRegistered(tx, user);
    const name = groupName(body.name);
    const display = cleanName(body.display_name);
    const starts = optDate(body, "starts_on");
    const ends = optDate(body, "ends_on");
    const trip = optUuid(body, "trip_id");
    const [c] = await tx`select count(*)::int as n from public.groups where created_by = ${user}`;
    if (c.n >= LIMITS.groupsCreatedPerUser) throw new ApiError("too_many_groups", 409);
    const [g] = await tx`insert into public.groups (name, starts_on, ends_on, created_by)
                         values (${name}, ${starts}, ${ends}, ${user}) returning id`;
    await addMember(tx, g.id, user, display, "admin");
    if (trip) await tx`update public.group_members set trip_id = ${trip} where group_id = ${g.id} and user_id = ${user}`;
    await tx`insert into public.invites (group_id, code, token, created_by)
             values (${g.id}, ${await newCode(tx)}, ${randomToken()}, ${user})`;
    await audit(tx, user, "group_created", g.id);
    return { group_id: g.id };
  },

  async update_group({ tx, user, body }) {
    const group = uuid(body, "group_id");
    await requireAdmin(tx, group, user);
    await tx`update public.groups set name = ${groupName(body.name)},
               starts_on = ${optDate(body, "starts_on")}, ends_on = ${optDate(body, "ends_on")}
             where id = ${group}`;
    await audit(tx, user, "group_updated", group);
    return {};
  },

  async delete_group({ tx, user, body }) {
    const group = uuid(body, "group_id");
    await requireAdmin(tx, group, user);
    await tx`delete from public.groups where id = ${group}`;
    await audit(tx, user, "group_deleted", group);
    return {};
  },

  // A new invite replaces the group's working ones (revoke and replace).
  async create_invite({ tx, user, body }) {
    const group = uuid(body, "group_id");
    await requireAdmin(tx, group, user);
    const maxUses = body.max_uses == null ? null : Number(body.max_uses);
    if (maxUses !== null && (!Number.isInteger(maxUses) || maxUses < 1)) throw new ApiError("invalid_input");
    const expires = body.expires_at == null ? null : str(body, "expires_at");
    if (expires !== null && isNaN(Date.parse(expires))) throw new ApiError("invalid_input");
    await tx`update public.invites set revoked_at = now() where group_id = ${group} and revoked_at is null`;
    const [inv] = await tx`
      insert into public.invites (group_id, code, token, created_by, requires_approval, max_uses, expires_at)
      values (${group}, ${await newCode(tx)}, ${randomToken()}, ${user},
              ${bool(body, "requires_approval", false)}, ${maxUses}, ${expires})
      returning id, code, token, requires_approval, max_uses, expires_at`;
    await audit(tx, user, "invite_created", group, inv.id);
    return inv;
  },

  async revoke_invite({ tx, user, body }) {
    const id = uuid(body, "invite_id");
    const [inv] = await tx`select group_id from public.invites where id = ${id}`;
    if (!inv) throw new ApiError("not_admin", 403);
    await requireAdmin(tx, inv.group_id, user);
    await tx`update public.invites set revoked_at = now() where id = ${id} and revoked_at is null`;
    await audit(tx, user, "invite_revoked", inv.group_id, id);
    return {};
  },

  // What an invite opens: the group and its members' names (for "I'm
  // already in the group"; not for an invitation that needs approval, see
  // below). Counts as an attempt.
  async invite_preview({ tx, user, body }) {
    const r = await useCode(tx, user, body.code);
    if (!r.invite) return { status: r.status };
    const group = r.invite.group_id;
    // Dates as plain text (2027-01-10), not as a moment in some time zone.
    const [g] = await tx`select id, name, starts_on::text, ends_on::text from public.groups where id = ${group}`;
    const members = await tx`select user_id, display_name from public.group_members
                             where group_id = ${group} order by joined_at, user_id`;
    const alreadyMember = members.some((m) => m.user_id === user);
    return {
      status: "ok",
      group_id: g.id, name: g.name, starts_on: g.starts_on, ends_on: g.ends_on,
      requires_approval: r.invite.requires_approval,
      already_member: alreadyMember,
      // An invitation that needs approval is for people the admin has not accepted yet: it does not show them who is in
      // the group. (Members see the list; so does everyone for an open invitation, which anyone holding it may use.)
      members: r.invite.requires_approval && !alreadyMember ? [] : members,
    };
  },

  // Join with a code or link token; guests (anonymous identities) too.
  async join_group({ tx, user, body }) {
    const name = cleanName(body.display_name);
    const r = await useCode(tx, user, body.code);
    if (!r.invite) return { status: r.status };
    const group = r.invite.group_id;
    await lockGroup(tx, group);
    if (await role(tx, group, user)) return { status: "already_member", group_id: group };
    const [c] = await tx`select count(*)::int as n from public.group_members where group_id = ${group}`;
    if (c.n >= LIMITS.membersPerGroup) return { status: "group_full" };
    if (r.invite.requires_approval) {
      await tx`
        insert into public.join_requests (group_id, user_id, display_name, kind)
        values (${group}, ${user}, ${name}, 'approval')
        on conflict (group_id, user_id) where status = 'pending'
        do update set display_name = excluded.display_name, kind = 'approval', reclaim_user_id = null`;
      await audit(tx, user, "join_requested", group);
      return { status: "pending", group_id: group };
    }
    await addMember(tx, group, user, name);
    await tx`update public.invites set uses = uses + 1 where id = ${r.invite.id}`;
    await audit(tx, user, "joined", group);
    return { status: "joined", group_id: group };
  },

  // "I'm already in the group": someone who lost their guest identity asks
  // to be that member again; an admin approves. Registered members just
  // sign in again.
  async request_reclaim({ tx, user, body }) {
    const member = uuid(body, "member_id");
    const r = await useCode(tx, user, body.code);
    if (!r.invite) return { status: r.status };
    const group = r.invite.group_id;
    if (await role(tx, group, user)) return { status: "already_member", group_id: group };
    const [m] = await tx`select display_name from public.group_members where group_id = ${group} and user_id = ${member}`;
    if (!m) return { status: "no_such_member" };
    if (await isRegistered(tx, member)) return { status: "sign_in_instead" };
    await tx`
      insert into public.join_requests (group_id, user_id, display_name, kind, reclaim_user_id)
      values (${group}, ${user}, ${m.display_name}, 'reclaim', ${member})
      on conflict (group_id, user_id) where status = 'pending'
      do update set display_name = excluded.display_name, kind = 'reclaim', reclaim_user_id = excluded.reclaim_user_id`;
    await audit(tx, user, "reclaim_requested", group, member);
    return { status: "pending", group_id: group };
  },

  async cancel_join_request({ tx, user, body }) {
    await tx`update public.join_requests set status = 'cancelled', decided_at = now()
             where id = ${uuid(body, "request_id")} and user_id = ${user} and status = 'pending'`;
    return {};
  },

  async decide_join_request({ tx, user, body }) {
    const id = uuid(body, "request_id");
    const approve = bool(body, "approve", false);
    const [q] = await tx`select * from public.join_requests where id = ${id}`;
    if (!q) throw new ApiError("not_admin", 403);
    await requireAdmin(tx, q.group_id, user);
    await lockGroup(tx, q.group_id);
    if (q.status !== "pending") throw new ApiError("request_closed", 409);
    await tx`update public.join_requests set status = ${approve ? "approved" : "rejected"},
               decided_by = ${user}, decided_at = now() where id = ${id}`;
    await audit(tx, user, approve ? "request_approved" : "request_rejected", q.group_id, q.user_id, { kind: q.kind });
    if (!approve) return {};
    if (q.kind === "approval") {
      if (!(await role(tx, q.group_id, q.user_id))) await addMember(tx, q.group_id, q.user_id, q.display_name);
      return {};
    }
    if (!(await role(tx, q.group_id, q.reclaim_user_id))) throw new ApiError("member_gone", 409);
    if (await isRegistered(tx, q.reclaim_user_id)) throw new ApiError("sign_in_instead", 409);
    if (await role(tx, q.group_id, q.user_id)) throw new ApiError("already_member", 409);
    await absorb(tx, q.reclaim_user_id, q.user_id, q.group_id);
    return {};
  },

  // Leave any time; my trip stops showing there.
  async leave_group({ tx, user, body }) {
    const group = uuid(body, "group_id");
    if (!(await lockGroup(tx, group))) return {};
    const r = await tx`delete from public.group_members where group_id = ${group} and user_id = ${user} returning 1`;
    if (r.length) {
      await audit(tx, user, "left", group);
      await afterMemberLeft(tx, group);
    }
    return {};
  },

  async remove_member({ tx, user, body }) {
    const group = uuid(body, "group_id");
    const member = uuid(body, "user_id");
    await requireAdmin(tx, group, user);
    if (member === user) throw new ApiError("use_leave_group", 409);
    await lockGroup(tx, group);
    await tx`delete from public.group_members where group_id = ${group} and user_id = ${member}`;
    await audit(tx, user, "member_removed", group, member);
    await afterMemberLeft(tx, group);
    return {};
  },

  // Admins must be registered; the last admin cannot step down.
  async set_member_role({ tx, user, body }) {
    const group = uuid(body, "group_id");
    const member = uuid(body, "user_id");
    const newRole = str(body, "role");
    await requireAdmin(tx, group, user);
    if (newRole !== "admin" && newRole !== "member") throw new ApiError("invalid_input");
    await lockGroup(tx, group);
    if (!(await role(tx, group, member))) throw new ApiError("not_member", 404);
    if (newRole === "admin" && !(await isRegistered(tx, member))) throw new ApiError("admin_must_register", 409);
    if (newRole === "member") {
      const [c] = await tx`select count(*)::int as n from public.group_members
                           where group_id = ${group} and role = 'admin' and user_id <> ${member}`;
      if (c.n === 0) throw new ApiError("last_admin", 409);
    }
    await tx`update public.group_members set role = ${newRole} where group_id = ${group} and user_id = ${member}`;
    await audit(tx, user, "role_set", group, member, { role: newRole });
    return {};
  },

  // A group left without an admin: a registered member takes it over.
  async claim_admin({ tx, user, body }) {
    const group = uuid(body, "group_id");
    await requireRegistered(tx, user);
    await requireMember(tx, group, user);
    await lockGroup(tx, group);
    const [c] = await tx`select count(*)::int as n from public.group_members where group_id = ${group} and role = 'admin'`;
    if (c.n > 0) throw new ApiError("group_has_admin", 409);
    await tx`update public.group_members set role = 'admin' where group_id = ${group} and user_id = ${user}`;
    await audit(tx, user, "admin_claimed", group);
    return {};
  },

  // My name in a group, and which of my trips it shows (null: none).
  async set_my_membership({ tx, user, body }) {
    const group = uuid(body, "group_id");
    const r = await tx`update public.group_members
                       set display_name = ${cleanName(body.display_name)}, trip_id = ${optUuid(body, "trip_id")}
                       where group_id = ${group} and user_id = ${user} returning 1`;
    if (!r.length) throw new ApiError("not_member", 403);
    return {};
  },

  // "I'm on the same flight": that member's flight becomes mine, shown in
  // this group. A person has one trip (decision 27), so a trip of mine is
  // overwritten rather than copied next to it: the one the app keeps
  // (my_trip_id), or else the one this group already shows for me. Only
  // with neither is a new trip made. Other groups showing that trip see
  // the change too.
  async same_flight({ tx, user, body }) {
    const group = uuid(body, "group_id");
    const trip = uuid(body, "trip_id");
    await requireMember(tx, group, user);
    const shown = await tx`select 1 from public.group_members where group_id = ${group} and trip_id = ${trip}`;
    if (!shown.length) throw new ApiError("trip_not_in_group", 404);
    let target = optUuid(body, "my_trip_id");
    if (target) {
      const own = await tx`select 1 from public.trips where id = ${target} and owner_id = ${user}`;
      if (!own.length) throw new ApiError("trip_not_yours", 409);
    } else {
      const [m] = await tx`
        select t.id from public.group_members m join public.trips t on t.id = m.trip_id and t.owner_id = ${user}
        where m.group_id = ${group} and m.user_id = ${user}`;
      target = m?.id ?? null;
    }
    let mine: string;
    if (!target) {
      mine = await copyTrip(tx, trip, user, user);
    } else {
      if (target !== trip) {
        await tx`
          update public.trips t set
            destination = s.destination,
            out_date = s.out_date, out_flight = s.out_flight, out_from = s.out_from, out_to = s.out_to,
            out_departs = s.out_departs, out_arrives = s.out_arrives,
            ret_date = s.ret_date, ret_flight = s.ret_flight, ret_from = s.ret_from, ret_to = s.ret_to,
            ret_departs = s.ret_departs, ret_arrives = s.ret_arrives,
            ski_from = s.ski_from, ski_to = s.ski_to, entered_by = ${user}
          from public.trips s where t.id = ${target} and s.id = ${trip}`;
      }
      mine = target;
    }
    await tx`update public.group_members set trip_id = ${mine} where group_id = ${group} and user_id = ${user}`;
    return { trip_id: mine };
  },

  // An admin fills in a member's flight, while the member has not set
  // their own. Once the member edits it, it is theirs.
  async set_member_trip({ tx, user, body }) {
    const group = uuid(body, "group_id");
    const member = uuid(body, "user_id");
    await requireAdmin(tx, group, user);
    const fields = (body.trip ?? {}) as Body;
    if (typeof fields !== "object" || Array.isArray(fields)) throw new ApiError("invalid_input");
    const [m] = await tx`select trip_id from public.group_members where group_id = ${group} and user_id = ${member}`;
    if (!m) throw new ApiError("not_member", 404);
    let trip: string | null = m.trip_id;
    if (trip) {
      const own = await tx`select 1 from public.trips where id = ${trip} and entered_by is not distinct from owner_id`;
      if (own.length) throw new ApiError("member_owns_trip", 409);
    } else {
      [{ id: trip }] = await tx`insert into public.trips (owner_id, entered_by) values (${member}, ${user}) returning id`;
    }
    const values: Record<string, unknown> = { entered_by: user };
    for (const f of TRIP_FIELDS) values[f] = fields[f] ?? null;
    // deno-lint-ignore no-explicit-any
    await tx`update public.trips set ${tx(values as any)} where id = ${trip}`;
    await tx`update public.group_members set trip_id = ${trip} where group_id = ${group} and user_id = ${member}`;
    await audit(tx, user, "member_trip_set", group, member);
    return { trip_id: trip };
  },

  // --- Scores --------------------------------------------------------

  async submit_score({ tx, user, body }) {
    const game = str(body, "game");
    const score = body.score;
    if (!/^[a-z0-9-]{1,32}$/.test(game) || !Number.isInteger(score) || (score as number) < 0 ||
        (score as number) > 2147483647) {
      throw new ApiError("invalid_input");
    }
    const [r] = await tx`
      insert into public.scores (user_id, game, best) values (${user}, ${game}, ${score as number})
      on conflict (user_id, game) do update set best = excluded.best, achieved_at = now()
        where excluded.best > public.scores.best
      returning best`;
    if (r) return { best: r.best };
    const [b] = await tx`select best from public.scores where user_id = ${user} and game = ${game}`;
    return { best: b.best };
  },

  async group_leaderboard({ tx, user, body }) {
    const group = uuid(body, "group_id");
    const game = str(body, "game");
    await requireMember(tx, group, user);
    return await tx`
      select m.user_id, m.display_name, s.best, s.achieved_at
      from public.group_members m join public.scores s on s.user_id = m.user_id and s.game = ${game}
      where m.group_id = ${group}
      order by s.best desc, s.achieved_at`;
  },
};

// The daily keepalive (decision 28): open to anyone, it only bumps a
// counter, and at most once a day cleans up. Guests in no group, with no
// pending request, older than the limit, are removed (their own data
// stays on their phone); old invite attempts and audit entries go too.
async function keepalive(d: Deps): Promise<Result> {
  let removed: string[] = [];
  const beat = await d.sql.begin(async (tx) => {
    const [b] = await tx`
      update private.heartbeat set beat_at = now(), beats = beats + 1 where id = 1
      returning beat_at, beats, cleaned_at`;
    if (!b.cleaned_at || Date.now() - new Date(b.cleaned_at).getTime() > 20 * 3600 * 1000) {
      await tx`update private.heartbeat set cleaned_at = now() where id = 1`;
      await tx`delete from private.invite_attempts where at < now() - interval '2 days'`;
      await tx`delete from private.merge_tickets where expires_at < now()`;
      await tx`delete from private.audit_log where at < now() - make_interval(days => ${LIMITS.auditDays})`;
      removed = (await tx`
        select u.id from auth.users u
        where u.is_anonymous
          and u.created_at < now() - make_interval(days => ${LIMITS.anonCleanupDays})
          and not exists (select 1 from public.group_members m where m.user_id = u.id)
          and not exists (select 1 from public.join_requests q where q.user_id = u.id and q.status = 'pending')
          and not exists (select 1 from private.merge_tickets t where t.anon_user_id = u.id)
        limit 500`).map((r) => r.id);
    }
    return b;
  });
  for (const id of removed) await d.deleteAuthUser(id);
  return { status: 200, body: { ok: true, beats: Number(beat.beats), removed_guests: removed.length } };
}

// Run one action for one caller. `user` is the verified user id from the
// session, or null when the request has none.
export async function handle(d: Deps, user: string | null, action: string, body: Body): Promise<Result> {
  if (action === "keepalive") return await keepalive(d);
  const fn = Object.hasOwn(actions, action) ? actions[action] : undefined;
  if (!fn) return { status: 404, body: { error: "unknown_action" } };
  if (!user) return { status: 401, body: { error: "not_signed_in" } };
  if (typeof body !== "object" || body === null || Array.isArray(body)) {
    return { status: 400, body: { error: "invalid_input" } };
  }
  const after: Array<() => Promise<void>> = [];
  try {
    const data = await d.sql.begin((tx) => fn({ deps: d, tx, user, body, after }));
    for (const f of after) await f();
    return { status: 200, body: data };
  } catch (e) {
    if (e instanceof ApiError) return { status: e.status, body: { error: e.code } };
    // A rule in the database said no (a check constraint, or a guard trigger).
    const pg = e as { code?: string; message?: string };
    if (pg.code === "23514" || pg.code === "22P02" || pg.code === "22007" || pg.code === "22008") {
      return { status: 400, body: { error: "invalid_input" } };
    }
    if (pg.code === "P0001" && pg.message && /^[a-z_]+$/.test(pg.message)) {
      return { status: 409, body: { error: pg.message } };
    }
    if (pg.code === "23503") return { status: 409, body: { error: "conflict" } };
    throw e;
  }
}

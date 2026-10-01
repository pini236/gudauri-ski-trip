-- The actions apps call (supabase.rpc('<name>', {...})). Everything that
-- changes a group, its members or its invites goes through here; the
-- tables themselves are read-only for the API (previous migration).
--
-- Errors: functions raise an exception whose message is a short code
-- (for example 'not_admin'); apps map the code to a message in their own
-- language. The join functions instead return {"status": ...}, because a
-- failed guess must be recorded for the rate limit, and an exception would
-- roll that record back.

-- ---------------------------------------------------------------------
-- Internal helpers
-- ---------------------------------------------------------------------

create function private.require_user() returns uuid
language plpgsql stable set search_path = '' as $$
declare v uuid := auth.uid();
begin
  if v is null then raise exception 'not_signed_in' using errcode = '42501'; end if;
  return v;
end $$;

create function private.require_registered() returns uuid
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := private.require_user();
begin
  if not private.is_registered(v) then raise exception 'must_register' using errcode = '42501'; end if;
  return v;
end $$;

create function private.require_admin(p_group uuid) returns uuid
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := private.require_user();
begin
  if not private.is_admin(p_group) then raise exception 'not_admin' using errcode = '42501'; end if;
  return v;
end $$;

create function private.clean_name(p_name text) returns text
language plpgsql immutable set search_path = '' as $$
declare v text := btrim(regexp_replace(coalesce(p_name, ''), '\s+', ' ', 'g'));
begin
  if char_length(v) not between 1 and 40 or v ~ '[[:cntrl:]]' then
    raise exception 'invalid_name' using errcode = '22023';
  end if;
  return v;
end $$;

create function private.new_code() returns text
language plpgsql volatile set search_path = '' as $$
declare
  v_alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  v_bytes bytea;
  v_code text;
begin
  loop
    v_bytes := extensions.gen_random_bytes(6);
    v_code := '';
    for i in 0..5 loop
      v_code := v_code || substr(v_alphabet, (get_byte(v_bytes, i) % 24) + 1, 1);
    end loop;
    exit when not exists (select 1 from public.invites where code = v_code);
  end loop;
  return v_code;
end $$;

create function private.new_token() returns text
language sql volatile set search_path = '' as $$
  select translate(encode(extensions.gen_random_bytes(24), 'base64'), '+/', '-_')
$$;

-- When an invite stops working.
create function private.invite_expiry(p_invite public.invites) returns timestamptz
language sql stable security definer set search_path = '' as $$
  select coalesce(
    p_invite.expires_at,
    (select (g.ends_on + 1)::timestamptz from public.groups g where g.id = p_invite.group_id and g.ends_on is not null),
    p_invite.created_at + interval '90 days')
$$;

-- Find a working invite by its six letters or its link token.
create function private.find_invite(p_code text) returns public.invites
language plpgsql stable security definer set search_path = '' as $$
declare
  v_key text := btrim(coalesce(p_code, ''));
  v_inv public.invites;
begin
  if upper(regexp_replace(v_key, '[\s-]', '', 'g')) ~ '^[A-Z]{6}$' then
    select * into v_inv from public.invites where code = upper(regexp_replace(v_key, '[\s-]', '', 'g'));
  elsif char_length(v_key) between 32 and 64 then
    select * into v_inv from public.invites where token = v_key;
  end if;
  if v_inv.id is null
     or v_inv.revoked_at is not null
     or private.invite_expiry(v_inv) <= now()
     or (v_inv.max_uses is not null and v_inv.uses >= v_inv.max_uses) then
    return null;
  end if;
  return v_inv;
end $$;

-- Too many wrong guesses: by this user, or (for six-letter codes, the
-- only guessable kind) by everyone together.
create function private.attempts_blocked(p_user uuid, p_short boolean) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select count(*) from private.invite_attempts
          where user_id = p_user and not ok and at > now() - interval '15 minutes')
           >= (private.limits() ->> 'fails_per_15_min')::integer
      or (select count(*) from private.invite_attempts
          where user_id = p_user and not ok and at > now() - interval '1 day')
           >= (private.limits() ->> 'fails_per_day')::integer
      or p_short and (select count(*) from private.invite_attempts
          where not ok and at > now() - interval '1 hour')
           >= (private.limits() ->> 'global_fails_per_hour')::integer
$$;

-- Resolve an invite for the caller, counting the attempt.
-- invite_id is null when it fails, and status says why.
create function private.use_code(p_user uuid, p_code text, out invite_id uuid, out status text)
language plpgsql security definer set search_path = '' as $$
begin
  if private.attempts_blocked(p_user, upper(regexp_replace(btrim(coalesce(p_code, '')), '[\s-]', '', 'g')) ~ '^[A-Z]{6}$') then
    status := 'rate_limited';
    return;
  end if;
  invite_id := (private.find_invite(p_code)).id;
  insert into private.invite_attempts (user_id, ok) values (p_user, invite_id is not null);
  status := case when invite_id is null then 'invalid_code' else 'ok' end;
end $$;

-- Keep a group alive and administered after someone leaves:
-- no members left: delete it; no admin left: promote the earliest
-- registered member; nobody registered: the group stays without an
-- admin until a registered member calls claim_admin().
create function private.after_member_left(p_group uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v_next uuid;
begin
  if not exists (select 1 from public.group_members where group_id = p_group) then
    delete from public.groups where id = p_group;
    return;
  end if;
  if not exists (select 1 from public.group_members where group_id = p_group and role = 'admin') then
    select m.user_id into v_next from public.group_members m
      where m.group_id = p_group and private.is_registered(m.user_id)
      order by m.joined_at, m.user_id limit 1;
    if v_next is not null then
      update public.group_members set role = 'admin' where group_id = p_group and user_id = v_next;
    end if;
  end if;
end $$;

create function private.add_member(p_group uuid, p_user uuid, p_name text, p_role text default 'member')
returns void language plpgsql security definer set search_path = '' as $$
begin
  if (select count(*) from public.group_members where group_id = p_group)
     >= (private.limits() ->> 'members_per_group')::integer then
    raise exception 'group_full' using errcode = 'P0001';
  end if;
  insert into public.group_members (group_id, user_id, role, display_name)
  values (p_group, p_user, p_role, p_name);
end $$;

-- Copy a trip to a new owner. Returns the new trip's id.
create function private.copy_trip(p_trip uuid, p_owner uuid, p_entered_by uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
  insert into public.trips (owner_id, destination,
    out_date, out_flight, out_from, out_to, out_departs, out_arrives,
    ret_date, ret_flight, ret_from, ret_to, ret_departs, ret_arrives, ski_from, ski_to, entered_by)
  select p_owner, destination,
    out_date, out_flight, out_from, out_to, out_departs, out_arrives,
    ret_date, ret_flight, ret_from, ret_to, ret_departs, ret_arrives, ski_from, ski_to, p_entered_by
  from public.trips where id = p_trip
  returning id into v_id;
  return v_id;
end $$;

-- Move what belongs to p_from into p_to.
--   p_group null: everything (a guest merging into their own account).
--   p_group set:  only that group (an admin approved "I'm already in the
--                 group"). An admin of one group must not hand out
--                 access to another, so there the trip is copied, not
--                 moved, and other groups are left alone.
create function private.absorb(p_from uuid, p_to uuid, p_group uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
  m record;
  v_trip uuid;
begin
  if p_group is null then
    update public.trips set owner_id = p_to where owner_id = p_from;
    update public.trips set entered_by = p_to where entered_by = p_from;
  end if;

  for m in select * from public.group_members
           where user_id = p_from and (p_group is null or group_id = p_group) loop
    v_trip := m.trip_id;
    if p_group is not null and v_trip is not null then
      v_trip := private.copy_trip(v_trip, p_to, p_to);
    end if;
    delete from public.group_members where group_id = m.group_id and user_id = p_from;
    if exists (select 1 from public.group_members where group_id = m.group_id and user_id = p_to) then
      update public.group_members set trip_id = coalesce(trip_id, v_trip)
        where group_id = m.group_id and user_id = p_to;
    else
      insert into public.group_members (group_id, user_id, role, display_name, trip_id, joined_at)
      values (m.group_id, p_to, 'member', m.display_name, v_trip, m.joined_at);
    end if;
    update public.meetups set created_by = p_to where group_id = m.group_id and created_by = p_from;
    update public.meetups set updated_by = p_to where group_id = m.group_id and updated_by = p_from;
  end loop;

  if p_group is null then
    delete from public.join_requests where user_id = p_from;
  end if;

  -- Scores: keep the better one.
  insert into public.scores (user_id, game, best, achieved_at)
  select p_to, game, best, achieved_at from public.scores where user_id = p_from
  on conflict (user_id, game) do update
    set best = excluded.best, achieved_at = excluded.achieved_at
    where excluded.best > public.scores.best;
end $$;

-- Delete an auth user, keeping their groups administered.
create function private.delete_user(p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v_groups uuid[];
begin
  select coalesce(array_agg(group_id), '{}') into v_groups from public.group_members where user_id = p_user;
  delete from public.group_members where user_id = p_user;
  perform private.after_member_left(g) from unnest(v_groups) as g;
  delete from auth.users where id = p_user;
end $$;

-- Internal functions are not for the API roles, except the ones the row
-- level security policies and the trips trigger call as the caller.
revoke all on all functions in schema private from public, anon, authenticated;
grant execute on function private.is_registered(uuid), private.is_member(uuid), private.is_admin(uuid),
  private.shares_group(uuid), private.can_see_trip(uuid), private.limits() to authenticated;

-- ---------------------------------------------------------------------
-- Account
-- ---------------------------------------------------------------------

-- Who am I: profile plus whether this is a guest (anonymous) identity.
create function public.my_account() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v uuid := private.require_user();
begin
  return (select jsonb_build_object(
    'id', p.id, 'display_name', p.display_name, 'lang', p.lang,
    'is_anonymous', not private.is_registered(p.id))
    from public.profiles p where p.id = v);
end $$;

-- Delete my account and everything that is mine (Google Play and the
-- App Store require this in the app). Groups I administered get a new
-- admin; groups left empty are deleted; meetups I created stay in the
-- group without my name.
create function public.delete_my_account() returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.delete_user(private.require_user());
end $$;

-- Guest to account, when the Google or Apple account already exists
-- (linking in place is not possible then). Step 1, while still signed in
-- as the guest: get a one-time ticket (valid 15 minutes).
create function public.create_merge_ticket() returns text
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_user();
  v_secret text := private.new_token();
begin
  if private.is_registered(v) then raise exception 'not_a_guest' using errcode = 'P0001'; end if;
  delete from private.merge_tickets where anon_user_id = v or expires_at < now();
  insert into private.merge_tickets (secret_hash, anon_user_id, expires_at)
  values (encode(extensions.digest(v_secret, 'sha256'), 'hex'), v, now() + interval '15 minutes');
  return v_secret;
end $$;

-- Step 2, after signing in to the existing account: bring everything
-- of the guest over, then delete the guest identity.
create function public.merge_guest(p_ticket text) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_registered();
  v_anon uuid;
begin
  delete from private.merge_tickets
    where secret_hash = encode(extensions.digest(coalesce(p_ticket, ''), 'sha256'), 'hex')
      and expires_at > now()
    returning anon_user_id into v_anon;
  if v_anon is null or v_anon = v or private.is_registered(v_anon) then
    raise exception 'invalid_ticket' using errcode = 'P0001';
  end if;
  perform private.absorb(v_anon, v, null);
  perform private.delete_user(v_anon);
end $$;

-- ---------------------------------------------------------------------
-- Groups
-- ---------------------------------------------------------------------

-- Create a group (registered users only). The creator is its admin, and
-- the group starts with an invite.
create function public.create_group(
  p_name text, p_display_name text,
  p_starts_on date default null, p_ends_on date default null, p_trip_id uuid default null)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_registered();
  v_group uuid;
begin
  if (select count(*) from public.groups where created_by = v)
     >= (private.limits() ->> 'groups_created_per_user')::integer then
    raise exception 'too_many_groups' using errcode = 'P0001';
  end if;
  insert into public.groups (name, starts_on, ends_on, created_by)
  values (btrim(p_name), p_starts_on, p_ends_on, v) returning id into v_group;
  perform private.add_member(v_group, v, private.clean_name(p_display_name), 'admin');
  if p_trip_id is not null then
    update public.group_members set trip_id = p_trip_id where group_id = v_group and user_id = v;
  end if;
  insert into public.invites (group_id, code, token, created_by)
  values (v_group, private.new_code(), private.new_token(), v);
  return v_group;
end $$;

create function public.update_group(p_group uuid, p_name text, p_starts_on date, p_ends_on date)
returns void language plpgsql security definer set search_path = '' as $$
begin
  perform private.require_admin(p_group);
  update public.groups set name = btrim(p_name), starts_on = p_starts_on, ends_on = p_ends_on
    where id = p_group;
end $$;

create function public.delete_group(p_group uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.require_admin(p_group);
  delete from public.groups where id = p_group;
end $$;

-- A new invite replaces the group's working ones (revoke and replace).
create function public.create_invite(
  p_group uuid, p_requires_approval boolean default false,
  p_max_uses integer default null, p_expires_at timestamptz default null)
returns public.invites language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_admin(p_group);
  v_inv public.invites;
begin
  update public.invites set revoked_at = now() where group_id = p_group and revoked_at is null;
  insert into public.invites (group_id, code, token, created_by, requires_approval, max_uses, expires_at)
  values (p_group, private.new_code(), private.new_token(), v, coalesce(p_requires_approval, false), p_max_uses, p_expires_at)
  returning * into v_inv;
  return v_inv;
end $$;

create function public.revoke_invite(p_invite uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v_group uuid := (select group_id from public.invites where id = p_invite);
begin
  perform private.require_admin(v_group);
  update public.invites set revoked_at = now() where id = p_invite and revoked_at is null;
end $$;

-- What an invite opens: the group and its members' names (for "I'm
-- already in the group"). Counts as an attempt.
create function public.invite_preview(p_code text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_user();
  v_inv public.invites;
  v_status text;
begin
  select u.status, u.invite_id into v_status, v_inv.id from private.use_code(v, p_code) u;
  select * into v_inv from public.invites where id = v_inv.id;
  if v_status <> 'ok' then return jsonb_build_object('status', v_status); end if;
  return (select jsonb_build_object(
    'status', 'ok',
    'group_id', g.id, 'name', g.name, 'starts_on', g.starts_on, 'ends_on', g.ends_on,
    'requires_approval', v_inv.requires_approval,
    'already_member', exists (select 1 from public.group_members where group_id = g.id and user_id = v),
    'members', coalesce((select jsonb_agg(jsonb_build_object('user_id', m.user_id, 'display_name', m.display_name) order by m.joined_at)
                         from public.group_members m where m.group_id = g.id), '[]'))
    from public.groups g where g.id = v_inv.group_id);
end $$;

-- Join with a code or link token. Guests (anonymous identities) may join.
-- Returns {"status": "joined" | "pending" | "already_member" |
--          "invalid_code" | "rate_limited" | "group_full", "group_id"?}.
create function public.join_group(p_code text, p_display_name text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_user();
  v_name text := private.clean_name(p_display_name);
  v_inv public.invites;
  v_status text;
  v_group uuid;
begin
  select u.status, u.invite_id into v_status, v_inv.id from private.use_code(v, p_code) u;
  select * into v_inv from public.invites where id = v_inv.id;
  if v_status <> 'ok' then return jsonb_build_object('status', v_status); end if;
  v_group := v_inv.group_id;
  if exists (select 1 from public.group_members where group_id = v_group and user_id = v) then
    return jsonb_build_object('status', 'already_member', 'group_id', v_group);
  end if;
  if (select count(*) from public.group_members where group_id = v_group)
     >= (private.limits() ->> 'members_per_group')::integer then
    return jsonb_build_object('status', 'group_full');
  end if;
  if v_inv.requires_approval then
    insert into public.join_requests (group_id, user_id, display_name, kind)
    values (v_group, v, v_name, 'approval')
    on conflict (group_id, user_id) where status = 'pending'
    do update set display_name = excluded.display_name, kind = 'approval', reclaim_user_id = null;
    return jsonb_build_object('status', 'pending', 'group_id', v_group);
  end if;
  perform private.add_member(v_group, v, v_name);
  update public.invites set uses = uses + 1 where id = v_inv.id;
  return jsonb_build_object('status', 'joined', 'group_id', v_group);
end $$;

-- "I'm already in the group": someone who lost their guest identity
-- asks to be p_member again. An admin approves; nothing is duplicated.
-- Registered members just sign in again ('sign_in_instead').
create function public.request_reclaim(p_code text, p_member uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_user();
  v_inv public.invites;
  v_status text;
  v_group uuid;
  v_name text;
begin
  select u.status, u.invite_id into v_status, v_inv.id from private.use_code(v, p_code) u;
  select * into v_inv from public.invites where id = v_inv.id;
  if v_status <> 'ok' then return jsonb_build_object('status', v_status); end if;
  v_group := v_inv.group_id;
  if exists (select 1 from public.group_members where group_id = v_group and user_id = v) then
    return jsonb_build_object('status', 'already_member', 'group_id', v_group);
  end if;
  select display_name into v_name from public.group_members where group_id = v_group and user_id = p_member;
  if v_name is null then return jsonb_build_object('status', 'no_such_member'); end if;
  if private.is_registered(p_member) then return jsonb_build_object('status', 'sign_in_instead'); end if;
  insert into public.join_requests (group_id, user_id, display_name, kind, reclaim_user_id)
  values (v_group, v, v_name, 'reclaim', p_member)
  on conflict (group_id, user_id) where status = 'pending'
  do update set display_name = excluded.display_name, kind = 'reclaim', reclaim_user_id = excluded.reclaim_user_id;
  return jsonb_build_object('status', 'pending', 'group_id', v_group);
end $$;

create function public.cancel_join_request(p_request uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v uuid := private.require_user();
begin
  update public.join_requests set status = 'cancelled', decided_at = now()
    where id = p_request and user_id = v and status = 'pending';
end $$;

create function public.decide_join_request(p_request uuid, p_approve boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v uuid;
  q public.join_requests;
begin
  select * into q from public.join_requests where id = p_request;
  v := private.require_admin(q.group_id);
  if q.status <> 'pending' then raise exception 'request_closed' using errcode = 'P0001'; end if;
  update public.join_requests set status = case when p_approve then 'approved' else 'rejected' end,
    decided_by = v, decided_at = now() where id = p_request;
  if not p_approve then return; end if;
  if q.kind = 'approval' then
    if not exists (select 1 from public.group_members where group_id = q.group_id and user_id = q.user_id) then
      perform private.add_member(q.group_id, q.user_id, q.display_name);
    end if;
  else
    if not exists (select 1 from public.group_members where group_id = q.group_id and user_id = q.reclaim_user_id) then
      raise exception 'member_gone' using errcode = 'P0001';
    end if;
    if private.is_registered(q.reclaim_user_id) then
      raise exception 'sign_in_instead' using errcode = 'P0001';
    end if;
    if exists (select 1 from public.group_members where group_id = q.group_id and user_id = q.user_id) then
      raise exception 'already_member' using errcode = 'P0001';
    end if;
    perform private.absorb(q.reclaim_user_id, q.user_id, q.group_id);
  end if;
end $$;

-- Leave a group, any time. My trip stops showing there.
create function public.leave_group(p_group uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v uuid := private.require_user();
begin
  delete from public.group_members where group_id = p_group and user_id = v;
  if found then perform private.after_member_left(p_group); end if;
end $$;

create function public.remove_member(p_group uuid, p_user uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v uuid := private.require_admin(p_group);
begin
  if p_user = v then raise exception 'use_leave_group' using errcode = 'P0001'; end if;
  delete from public.group_members where group_id = p_group and user_id = p_user;
  perform private.after_member_left(p_group);
end $$;

-- Appoint or demote an admin. Admins must be registered; the last admin
-- cannot step down (appoint another first).
create function public.set_member_role(p_group uuid, p_user uuid, p_role text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.require_admin(p_group);
  if p_role not in ('admin', 'member') then raise exception 'invalid_role' using errcode = '22023'; end if;
  if not exists (select 1 from public.group_members where group_id = p_group and user_id = p_user) then
    raise exception 'not_member' using errcode = 'P0001';
  end if;
  if p_role = 'admin' and not private.is_registered(p_user) then
    raise exception 'admin_must_register' using errcode = 'P0001';
  end if;
  if p_role = 'member' and (select count(*) from public.group_members
                             where group_id = p_group and role = 'admin' and user_id <> p_user) = 0 then
    raise exception 'last_admin' using errcode = 'P0001';
  end if;
  update public.group_members set role = p_role where group_id = p_group and user_id = p_user;
end $$;

-- A group left without an admin (its admins left and nobody else was
-- registered then): a registered member takes it over.
create function public.claim_admin(p_group uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v uuid := private.require_registered();
begin
  if not private.is_member(p_group) then raise exception 'not_member' using errcode = '42501'; end if;
  if exists (select 1 from public.group_members where group_id = p_group and role = 'admin') then
    raise exception 'group_has_admin' using errcode = 'P0001';
  end if;
  update public.group_members set role = 'admin' where group_id = p_group and user_id = v;
end $$;

-- My name in a group, and which of my trips it shows (null: none).
create function public.set_my_membership(p_group uuid, p_display_name text, p_trip_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v uuid := private.require_user();
begin
  update public.group_members set display_name = private.clean_name(p_display_name), trip_id = p_trip_id
    where group_id = p_group and user_id = v;
  if not found then raise exception 'not_member' using errcode = '42501'; end if;
end $$;

-- "I'm on the same flight": copy a trip shown in the group into a new
-- trip of mine, and show it in the group. Returns the new trip's id.
create function public.same_flight(p_group uuid, p_trip uuid) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_user();
  v_new uuid;
begin
  if not private.is_member(p_group) then raise exception 'not_member' using errcode = '42501'; end if;
  if not exists (select 1 from public.group_members where group_id = p_group and trip_id = p_trip) then
    raise exception 'trip_not_in_group' using errcode = 'P0001';
  end if;
  v_new := private.copy_trip(p_trip, v, v);
  update public.group_members set trip_id = v_new where group_id = p_group and user_id = v;
  return v_new;
end $$;

-- An admin fills in a member's flight for them. Allowed while the member
-- has not set their own: once the member edits it, it is theirs.
-- p_trip holds trip fields by column name (out_date, out_flight, ...).
create function public.set_member_trip(p_group uuid, p_member uuid, p_trip jsonb) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_admin(p_group);
  v_current uuid;
  v_t public.trips;
begin
  select trip_id into v_current from public.group_members where group_id = p_group and user_id = p_member;
  if not found then raise exception 'not_member' using errcode = 'P0001'; end if;
  if v_current is not null and exists (
       select 1 from public.trips where id = v_current and entered_by is not distinct from owner_id) then
    raise exception 'member_owns_trip' using errcode = 'P0001';
  end if;
  v_t := jsonb_populate_record(null::public.trips, coalesce(p_trip, '{}'));
  if v_current is null then
    insert into public.trips (owner_id, entered_by) values (p_member, v) returning id into v_current;
  end if;
  update public.trips set
    destination = v_t.destination,
    out_date = v_t.out_date, out_flight = v_t.out_flight, out_from = v_t.out_from, out_to = v_t.out_to,
    out_departs = v_t.out_departs, out_arrives = v_t.out_arrives,
    ret_date = v_t.ret_date, ret_flight = v_t.ret_flight, ret_from = v_t.ret_from, ret_to = v_t.ret_to,
    ret_departs = v_t.ret_departs, ret_arrives = v_t.ret_arrives,
    ski_from = v_t.ski_from, ski_to = v_t.ski_to, entered_by = v
  where id = v_current;
  update public.group_members set trip_id = v_current where group_id = p_group and user_id = p_member;
  return v_current;
end $$;

-- ---------------------------------------------------------------------
-- Scores
-- ---------------------------------------------------------------------

-- Keep my best score for a game. Returns the best.
create function public.submit_score(p_game text, p_score integer) returns integer
language plpgsql security definer set search_path = '' as $$
declare
  v uuid := private.require_user();
  v_best integer;
begin
  insert into public.scores (user_id, game, best) values (v, p_game, p_score)
  on conflict (user_id, game) do update set best = excluded.best, achieved_at = now()
    where excluded.best > public.scores.best;
  select best into v_best from public.scores where user_id = v and game = p_game;
  return v_best;
end $$;

create function public.group_leaderboard(p_group uuid, p_game text)
returns table (user_id uuid, display_name text, best integer, achieved_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
#variable_conflict use_column
begin
  if not private.is_member(p_group) then raise exception 'not_member' using errcode = '42501'; end if;
  return query
    select m.user_id, m.display_name, s.best, s.achieved_at
    from public.group_members m join public.scores s on s.user_id = m.user_id and s.game = p_game
    where m.group_id = p_group
    order by s.best desc, s.achieved_at;
end $$;

-- ---------------------------------------------------------------------
-- Who may call what
-- ---------------------------------------------------------------------

revoke all on all functions in schema public from public, anon;
grant execute on all functions in schema public to authenticated;

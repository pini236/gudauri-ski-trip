-- Gudauri 2027: users, groups, invites, flights, meetups, scores.
-- The spec is docs/USERS.md. Every table has row level security, and
-- tables that only change through the functions in the next migration
-- are read-only for the API roles.
--
-- Roles, as Supabase uses them:
--   anon           no session at all (only keepalive() is open to it)
--   authenticated  a signed-in user: Google, Apple, or an anonymous
--                  identity (a guest who joined a group by invite).
--                  "Registered" = not anonymous (auth.users.is_anonymous).

create extension if not exists pgcrypto with schema extensions;

-- Helpers live in their own schema, which the API does not expose.
create schema if not exists private;
grant usage on schema private to authenticated;

-- ---------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------

-- One row per auth user, created by a trigger on auth.users.
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  -- Null until the user picks a name; apps show a localized "Guest".
  display_name text check (display_name is null or (char_length(btrim(display_name)) between 1 and 40 and display_name !~ '[[:cntrl:]]')),
  lang text not null default 'he' check (lang in ('he', 'en', 'ru', 'ka')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- A person's trip ("your trip"): flights there and back, and ski days.
-- Same fields as site/data/trip.json. Times are local to the airport.
create table public.trips (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  destination text check (char_length(destination) <= 60),
  out_date date,
  out_flight text check (char_length(out_flight) <= 12),
  out_from text check (out_from ~ '^[A-Z]{3}$'),
  out_to text check (out_to ~ '^[A-Z]{3}$'),
  out_departs time,
  out_arrives time,
  ret_date date,
  ret_flight text check (char_length(ret_flight) <= 12),
  ret_from text check (ret_from ~ '^[A-Z]{3}$'),
  ret_to text check (ret_to ~ '^[A-Z]{3}$'),
  ret_departs time,
  ret_arrives time,
  ski_from date,
  ski_to date,
  -- Who typed it in: the owner, or a group admin on the owner's behalf.
  -- Set by a trigger; once the owner edits the trip it is theirs again.
  entered_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (ski_from is null or ski_to is null or ski_from <= ski_to),
  check (out_date is null or ret_date is null or out_date <= ret_date)
);
create index trips_owner_idx on public.trips (owner_id);

create table public.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(btrim(name)) between 1 and 60 and name !~ '[[:cntrl:]]'),
  -- The group's own dates: its countdown, and when its invites expire.
  starts_on date,
  ends_on date,
  created_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (starts_on is null or ends_on is null or starts_on <= ends_on)
);

create table public.group_members (
  group_id uuid not null references public.groups (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  role text not null default 'member' check (role in ('admin', 'member')),
  -- The name shown in this group.
  display_name text not null check (char_length(btrim(display_name)) between 1 and 40 and display_name !~ '[[:cntrl:]]'),
  -- Which of the member's trips this group sees (only this one).
  trip_id uuid references public.trips (id) on delete set null,
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index group_members_user_idx on public.group_members (user_id);
create index group_members_trip_idx on public.group_members (trip_id);

create table public.invites (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  -- Six letters for typing (no I or O), and a long token for the link.
  code text not null unique check (code ~ '^[ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$'),
  token text not null unique check (char_length(token) >= 32),
  created_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  -- Null: valid until the day after the group's end date (or 90 days
  -- after creation when the group has no dates).
  expires_at timestamptz,
  max_uses integer check (max_uses is null or max_uses > 0),
  uses integer not null default 0,
  requires_approval boolean not null default false,
  revoked_at timestamptz
);
create index invites_group_idx on public.invites (group_id);

-- "Let me in" requests: joins that wait for an admin, and "I'm already
-- in the group" requests from someone who lost their guest identity.
create table public.join_requests (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  display_name text not null check (char_length(btrim(display_name)) between 1 and 40 and display_name !~ '[[:cntrl:]]'),
  kind text not null check (kind in ('approval', 'reclaim')),
  -- For 'reclaim': the member they say they are.
  reclaim_user_id uuid references public.profiles (id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected', 'cancelled')),
  created_at timestamptz not null default now(),
  decided_by uuid references public.profiles (id) on delete set null,
  decided_at timestamptz,
  check ((kind = 'reclaim') = (reclaim_user_id is not null))
);
create unique index join_requests_one_pending on public.join_requests (group_id, user_id) where status = 'pending';

create table public.meetups (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  -- A lift station id from site/data/runs-and-lifts.json, as in #meet/<station>/...
  station text not null check (station ~ '^[A-Za-z0-9_.:-]{1,64}$'),
  meet_at timestamptz not null,
  note text check (char_length(note) <= 140),
  created_by uuid references public.profiles (id) on delete set null,
  updated_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index meetups_group_idx on public.meetups (group_id, meet_at);

-- Best score per person per game. Group leaderboards are built from these.
create table public.scores (
  user_id uuid not null references public.profiles (id) on delete cascade,
  game text not null check (game ~ '^[a-z0-9-]{1,32}$'),
  best integer not null check (best >= 0),
  achieved_at timestamptz not null default now(),
  primary key (user_id, game)
);

-- Internal: invite code attempts, for rate limiting guesses.
create table private.invite_attempts (
  id bigserial primary key,
  user_id uuid not null,
  at timestamptz not null default now(),
  ok boolean not null
);
create index invite_attempts_user_idx on private.invite_attempts (user_id, at);
create index invite_attempts_at_idx on private.invite_attempts (at);

-- Internal: one-time tickets for merging a guest into an existing account.
create table private.merge_tickets (
  secret_hash text primary key,
  anon_user_id uuid not null references public.profiles (id) on delete cascade,
  expires_at timestamptz not null
);

-- Internal: the daily keepalive (decision 28).
create table private.heartbeat (
  id integer primary key default 1 check (id = 1),
  beat_at timestamptz not null default now(),
  beats bigint not null default 0,
  cleaned_at timestamptz
);
insert into private.heartbeat (id) values (1);

-- ---------------------------------------------------------------------
-- Limits (abuse protection; numbers are a first guess, see server/README.md)
-- ---------------------------------------------------------------------

create function private.limits() returns jsonb language sql immutable as $$
  select jsonb_build_object(
    'trips_per_user', 20,
    'groups_created_per_user', 20,
    'members_per_group', 100,
    'meetups_per_group', 300,
    'fails_per_15_min', 5,
    'fails_per_day', 20,
    'global_fails_per_hour', 300,
    'anon_cleanup_days', 30
  )
$$;

-- ---------------------------------------------------------------------
-- Helpers used by the policies. Security definer, so they can read the
-- membership table without recursing into its own policy.
-- ---------------------------------------------------------------------

create function private.is_registered(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from auth.users u where u.id = p_user and not u.is_anonymous)
$$;

create function private.is_member(p_group uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members m where m.group_id = p_group and m.user_id = auth.uid())
$$;

create function private.is_admin(p_group uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members m
                 where m.group_id = p_group and m.user_id = auth.uid() and m.role = 'admin')
$$;

-- True when the caller and p_user are together in at least one group.
create function private.shares_group(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.group_members a
                 join public.group_members b on b.group_id = a.group_id
                 where a.user_id = auth.uid() and b.user_id = p_user)
$$;

-- A trip is visible to its owner, and to the members of each group that
-- shows it. A group sees only the trip its member chose for it.
create function private.can_see_trip(p_trip uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.trips t where t.id = p_trip and t.owner_id = auth.uid())
      or exists (select 1 from public.group_members shown
                 join public.group_members me on me.group_id = shown.group_id and me.user_id = auth.uid()
                 where shown.trip_id = p_trip)
$$;

revoke all on all functions in schema private from public, anon, authenticated;
grant execute on function private.is_registered(uuid), private.is_member(uuid), private.is_admin(uuid),
  private.shares_group(uuid), private.can_see_trip(uuid), private.limits() to authenticated;

-- ---------------------------------------------------------------------
-- Triggers
-- ---------------------------------------------------------------------

-- A profile for every new auth user (Google, Apple or anonymous).
create function private.on_auth_user_created() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_name text := nullif(btrim(left(coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name', ''), 40)), '');
begin
  if v_name ~ '[[:cntrl:]]' then v_name := null; end if;
  insert into public.profiles (id, display_name) values (new.id, v_name) on conflict (id) do nothing;
  return new;
end $$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function private.on_auth_user_created();

create function private.touch_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end $$;

create trigger profiles_touch before update on public.profiles for each row execute function private.touch_updated_at();
create trigger groups_touch before update on public.groups for each row execute function private.touch_updated_at();

-- Trips written through the API belong to the caller and were entered by
-- the caller. Trips written by the admin function (running as the table
-- owner, not as 'authenticated') keep the entered_by it sets.
-- Security invoker on purpose: current_user must be the caller's role.
create function private.trips_guard() returns trigger
language plpgsql set search_path = '' as $$
declare
  v_limit integer := (private.limits() ->> 'trips_per_user')::integer;
begin
  if tg_op = 'INSERT' then
    if (select count(*) from public.trips where owner_id = new.owner_id) >= v_limit then
      raise exception 'too_many_trips' using errcode = 'P0001';
    end if;
  end if;
  if tg_op = 'UPDATE' and (row(new.*) is not distinct from row(old.*)
     or (new.destination, new.out_date, new.out_flight, new.out_from, new.out_to, new.out_departs, new.out_arrives,
         new.ret_date, new.ret_flight, new.ret_from, new.ret_to, new.ret_departs, new.ret_arrives, new.ski_from, new.ski_to)
        is not distinct from
        (old.destination, old.out_date, old.out_flight, old.out_from, old.out_to, old.out_departs, old.out_arrives,
         old.ret_date, old.ret_flight, old.ret_from, old.ret_to, old.ret_departs, old.ret_arrives, old.ski_from, old.ski_to)) then
    -- Not an edit of the trip (for example entered_by cleared when that
    -- person deleted their account).
    return new;
  end if;
  if current_user = 'authenticated' then
    new.entered_by := auth.uid();
  end if;
  new.updated_at := now();
  return new;
end $$;

create trigger trips_guard before insert or update on public.trips
  for each row execute function private.trips_guard();

-- A member's shown trip must be their own, and an admin must be registered.
create function private.group_members_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.trip_id is not null and not exists (
    select 1 from public.trips t where t.id = new.trip_id and t.owner_id = new.user_id
  ) then
    raise exception 'trip_not_yours' using errcode = 'P0001';
  end if;
  if new.role = 'admin' and not private.is_registered(new.user_id) then
    raise exception 'admin_must_register' using errcode = 'P0001';
  end if;
  return new;
end $$;

create trigger group_members_guard before insert or update on public.group_members
  for each row execute function private.group_members_guard();

create function private.meetups_guard() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    if (select count(*) from public.meetups where group_id = new.group_id)
       >= (private.limits() ->> 'meetups_per_group')::integer then
      raise exception 'too_many_meetups' using errcode = 'P0001';
    end if;
    new.created_by := coalesce(auth.uid(), new.created_by);
  elsif new.group_id <> old.group_id then
    raise exception 'cannot_move_meetup' using errcode = 'P0001';
  elsif (new.station, new.meet_at, new.note) is not distinct from (old.station, old.meet_at, old.note) then
    -- Not an edit (for example created_by cleared when its author deleted
    -- their account): leave the marks alone.
    return new;
  end if;
  new.updated_by := coalesce(auth.uid(), new.updated_by);
  new.updated_at := now();
  return new;
end $$;

create trigger meetups_guard before insert or update on public.meetups
  for each row execute function private.meetups_guard();

-- ---------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.trips enable row level security;
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.invites enable row level security;
alter table public.join_requests enable row level security;
alter table public.meetups enable row level security;
alter table public.scores enable row level security;
alter table private.invite_attempts enable row level security;
alter table private.merge_tickets enable row level security;
alter table private.heartbeat enable row level security;

-- Start from nothing, then grant exactly what each role may do.
revoke all on all tables in schema public from anon, authenticated;
revoke all on all tables in schema private from anon, authenticated;
revoke all on all sequences in schema private from anon, authenticated;

-- profiles: your own only. Group members see each other through the
-- name in group_members, not through profiles.
grant select on public.profiles to authenticated;
grant update (display_name, lang) on public.profiles to authenticated;
create policy profiles_select_own on public.profiles for select to authenticated
  using (id = (select auth.uid()));
create policy profiles_update_own on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- trips: the owner reads and writes; groups read the trip shown to them.
grant select, delete on public.trips to authenticated;
grant insert (destination, out_date, out_flight, out_from, out_to, out_departs, out_arrives,
              ret_date, ret_flight, ret_from, ret_to, ret_departs, ret_arrives, ski_from, ski_to)
  on public.trips to authenticated;
grant update (destination, out_date, out_flight, out_from, out_to, out_departs, out_arrives,
              ret_date, ret_flight, ret_from, ret_to, ret_departs, ret_arrives, ski_from, ski_to)
  on public.trips to authenticated;
-- The owner check comes first and reads the row itself, so insert ...
-- returning works (the helper cannot see a row inserted by the same statement).
create policy trips_select on public.trips for select to authenticated
  using (owner_id = (select auth.uid()) or private.can_see_trip(id));
create policy trips_insert_own on public.trips for insert to authenticated
  with check (owner_id = (select auth.uid()));
create policy trips_update_own on public.trips for update to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy trips_delete_own on public.trips for delete to authenticated
  using (owner_id = (select auth.uid()));

-- groups, group_members, invites: members read; changes only through functions.
grant select on public.groups, public.group_members, public.invites to authenticated;
create policy groups_select_member on public.groups for select to authenticated
  using (private.is_member(id));
create policy group_members_select_member on public.group_members for select to authenticated
  using (private.is_member(group_id));
-- Every member may share the invite (docs/USERS.md), so every member sees it.
create policy invites_select_member on public.invites for select to authenticated
  using (private.is_member(group_id));

-- join_requests: your own, and the group's admins.
grant select on public.join_requests to authenticated;
create policy join_requests_select on public.join_requests for select to authenticated
  using (user_id = (select auth.uid()) or private.is_admin(group_id));

-- meetups: every member reads, creates, edits and deletes (docs/USERS.md).
grant select, delete on public.meetups to authenticated;
grant insert (group_id, station, meet_at, note) on public.meetups to authenticated;
grant update (station, meet_at, note) on public.meetups to authenticated;
create policy meetups_select on public.meetups for select to authenticated
  using (private.is_member(group_id));
create policy meetups_insert on public.meetups for insert to authenticated
  with check (private.is_member(group_id));
create policy meetups_update on public.meetups for update to authenticated
  using (private.is_member(group_id)) with check (private.is_member(group_id));
create policy meetups_delete on public.meetups for delete to authenticated
  using (private.is_member(group_id));

-- scores: yours, and those of people you share a group with. Written by submit_score().
grant select on public.scores to authenticated;
create policy scores_select on public.scores for select to authenticated
  using (user_id = (select auth.uid()) or private.shares_group(user_id));

-- ---------------------------------------------------------------------
-- Realtime: changes the apps listen to (only on the real Supabase).
-- Realtime applies the same row level security.
-- ---------------------------------------------------------------------

do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table
      public.groups, public.group_members, public.trips, public.invites,
      public.join_requests, public.meetups, public.scores;
  end if;
end $$;

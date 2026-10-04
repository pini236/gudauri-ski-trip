-- From the architect's review (docs/ARCHITECTURE.md, section 9).

-- R-15: deleting an account is two steps, the rows (in one transaction) and then the identity (Supabase Auth, outside
-- it). A person whose identity is still there after the first step is kept here; the daily keepalive tries again. The
-- row goes by itself when the identity is deleted.
create table private.pending_deletions (
  user_id uuid primary key references auth.users (id) on delete cascade,
  at timestamptz not null default now()
);
alter table private.pending_deletions enable row level security;

-- R-13: wrong code guesses are counted per network address too (a keyed hash of it that changes every day, never the
-- address itself), instead of one limit for everyone, which let anyone close joining by code for all. Above
-- guard_fails_per_hour wrong guesses in the whole system, "protection mode": a right six-letter code still works, but
-- asks an admin (pending) instead of joining, so a code guessed by a crowd of addresses lets no one in by itself.
alter table private.invite_attempts add column ip_key text;
create index invite_attempts_ip_idx on private.invite_attempts (ip_key, at) where ip_key is not null;

create or replace function private.limits() returns jsonb language sql immutable set search_path = '' as $$
  select jsonb_build_object(
    'trips_per_user', 20,
    'groups_created_per_user', 20,
    'members_per_group', 100,
    'meetups_per_group', 300,
    'fails_per_15_min', 5,
    'fails_per_day', 20,
    'fails_per_ip_hour', 60,
    'guard_fails_per_hour', 1000,
    'anon_cleanup_days', 30,
    'audit_days', 180
  )
$$;

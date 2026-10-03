-- One place for the limits: private.limits() (the database rules and the server's code both read it; the code
-- used to keep its own copy). The one number only the code used is added: how long the audit log is kept (audit_days).
create or replace function private.limits() returns jsonb language sql immutable set search_path = '' as $$
  select jsonb_build_object(
    'trips_per_user', 20,
    'groups_created_per_user', 20,
    'members_per_group', 100,
    'meetups_per_group', 300,
    'fails_per_15_min', 5,
    'fails_per_day', 20,
    'global_fails_per_hour', 300,
    'anon_cleanup_days', 30,
    'audit_days', 180
  )
$$;

-- An index for every foreign key that had none (Supabase's performance check). Deleting an account removes or
-- clears the rows that point at it, and without these each delete reads the whole table.
create index if not exists join_requests_user_idx on public.join_requests (user_id);
create index if not exists join_requests_reclaim_user_idx on public.join_requests (reclaim_user_id);
create index if not exists join_requests_decided_by_idx on public.join_requests (decided_by);
create index if not exists trips_entered_by_idx on public.trips (entered_by);
create index if not exists groups_created_by_idx on public.groups (created_by);
create index if not exists invites_created_by_idx on public.invites (created_by);
create index if not exists meetups_created_by_idx on public.meetups (created_by);
create index if not exists meetups_updated_by_idx on public.meetups (updated_by);
create index if not exists merge_tickets_anon_user_idx on private.merge_tickets (anon_user_id);

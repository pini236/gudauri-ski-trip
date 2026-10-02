-- The actions moved to server code (decision 39): one Supabase Edge
-- Function, server/supabase/functions/api/. The database keeps the
-- tables, the row level security and the guards (previous migration).
--
-- 1. Remove the SQL versions of the actions. On the live project a part
--    of them was applied before the decision; elsewhere they never existed.
-- 2. An audit log: who did what, kept beyond the one day of logs on the
--    free plan. Written only by the server code.

drop function if exists private.require_user();
drop function if exists private.require_registered();
drop function if exists private.require_admin(uuid);
drop function if exists private.clean_name(text);
drop function if exists private.new_code();
drop function if exists private.new_token();
drop function if exists private.use_code(uuid, text);
drop function if exists private.attempts_blocked(uuid, boolean);
drop function if exists private.find_invite(text);
drop function if exists private.invite_expiry(public.invites);
drop function if exists private.after_member_left(uuid);
drop function if exists private.add_member(uuid, uuid, text, text);
drop function if exists private.copy_trip(uuid, uuid, uuid);

create table private.audit_log (
  id bigserial primary key,
  at timestamptz not null default now(),
  -- Who did it (null after that account was deleted).
  user_id uuid,
  action text not null,
  group_id uuid,
  -- Whom it was done to (a member, a request), when there is one.
  target uuid,
  details jsonb not null default '{}'
);
create index audit_log_at_idx on private.audit_log (at);
create index audit_log_group_idx on private.audit_log (group_id, at);
alter table private.audit_log enable row level security;
revoke all on private.audit_log from anon, authenticated;
revoke all on sequence private.audit_log_id_seq from anon, authenticated;

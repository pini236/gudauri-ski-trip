-- The daily keepalive (decision 28). On the free plan a project with too
-- little database activity for a week is paused. A scheduled GitHub
-- workflow (.github/workflows/server-keepalive.yml) calls this once a
-- day through the public API, which is real activity on the database.
--
-- Open to everyone (anon included): it only bumps a counter, and at most
-- once a day removes guest identities nobody uses any more.

create function public.keepalive() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_beat private.heartbeat;
  v_removed integer := 0;
begin
  update private.heartbeat set beat_at = now(), beats = beats + 1 where id = 1
    returning * into v_beat;

  if v_beat.cleaned_at is null or v_beat.cleaned_at < now() - interval '20 hours' then
    v_removed := private.cleanup();
    update private.heartbeat set cleaned_at = now() where id = 1;
  end if;

  return jsonb_build_object('ok', true, 'at', v_beat.beat_at, 'removed_guests', v_removed);
end $$;

-- Guest (anonymous) identities that are in no group, have no pending
-- request, and are older than the limit. They hold nothing anyone sees;
-- the guest's own data stays on their phone. Also old invite attempts.
create function private.cleanup() returns integer
language plpgsql security definer set search_path = '' as $$
declare v_count integer;
begin
  delete from private.invite_attempts where at < now() - interval '2 days';
  delete from private.merge_tickets where expires_at < now();

  with gone as (
    delete from auth.users u
    where u.is_anonymous
      and u.created_at < now() - make_interval(days => (private.limits() ->> 'anon_cleanup_days')::integer)
      and not exists (select 1 from public.group_members m where m.user_id = u.id)
      and not exists (select 1 from public.join_requests q where q.user_id = u.id and q.status = 'pending')
      and not exists (select 1 from private.merge_tickets t where t.anon_user_id = u.id)
    returning 1)
  select count(*) into v_count from gone;
  return v_count;
end $$;

revoke all on function private.cleanup() from public, anon, authenticated;
revoke all on function public.keepalive() from public;
grant execute on function public.keepalive() to anon, authenticated;

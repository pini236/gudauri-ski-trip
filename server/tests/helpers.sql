-- Test helpers, loaded by server/test.sh at the start of every test file,
-- inside the same transaction (which is rolled back at the end).
--
--   select tests.user('dana');           -- a registered user (Google or Apple)
--   select tests.user('gil', true);      -- a guest (anonymous identity)
--   select tests.as('dana');             -- act as dana (role authenticated)
--   select tests.as_anon();              -- no session (role anon)
--   select tests.as_owner();             -- back to the database owner
--   tests.id('dana')                     -- dana's user id

create schema tests;
grant usage on schema tests to anon, authenticated;

create table tests.users (name text primary key, id uuid not null);
grant select on tests.users to anon, authenticated;

create function tests.user(p_name text, p_anonymous boolean default false) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v uuid := gen_random_uuid();
begin
  insert into auth.users (id, email, is_anonymous, raw_user_meta_data)
  values (v, case when p_anonymous then null else p_name || '@example.com' end, p_anonymous,
          case when p_anonymous then '{}'::jsonb else jsonb_build_object('full_name', initcap(p_name)) end);
  insert into tests.users values (p_name, v);
  return v;
end $$;

create function tests.id(p_name text) returns uuid language sql stable as $$
  select id from tests.users where name = p_name
$$;

create function tests.as(p_name text) returns void language plpgsql as $$
declare v uuid := tests.id(p_name);
begin
  if v is null then raise exception 'no test user %', p_name; end if;
  perform set_config('role', 'none', true);
  perform set_config('request.jwt.claims',
    (select jsonb_build_object('sub', u.id, 'role', 'authenticated', 'is_anonymous', u.is_anonymous)::text
     from auth.users u where u.id = v), true);
  perform set_config('role', 'authenticated', true);
end $$;

create function tests.as_anon() returns void language plpgsql as $$
begin
  perform set_config('role', 'none', true);
  perform set_config('request.jwt.claims', '{"role":"anon"}', true);
  perform set_config('role', 'anon', true);
end $$;

create function tests.as_owner() returns void language plpgsql as $$
begin
  perform set_config('role', 'none', true);
  perform set_config('request.jwt.claims', '', true);
end $$;

-- The current invite code of a group (read as the owner).
create function tests.code(p_group uuid) returns text
language sql stable security definer set search_path = '' as $$
  select code from public.invites where group_id = p_group and revoked_at is null order by created_at desc limit 1
$$;

create function tests.token(p_group uuid) returns text
language sql stable security definer set search_path = '' as $$
  select token from public.invites where group_id = p_group and revoked_at is null order by created_at desc limit 1
$$;

grant execute on all functions in schema tests to anon, authenticated;

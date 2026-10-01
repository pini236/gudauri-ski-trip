-- Who can reach what at all: row level security everywhere, nothing for
-- the anon role except keepalive(), and internal functions closed.
select plan(18);

select ok(bool_and(c.relrowsecurity), 'row level security is on for every table')
  from pg_class c join pg_namespace n on n.oid = c.relnamespace
  where n.nspname in ('public', 'private') and c.relkind = 'r';

select is((select count(*)::int from pg_class c join pg_namespace n on n.oid = c.relnamespace
           where n.nspname = 'public' and c.relkind = 'r'), 8, 'eight tables in public');

select tests.user('dana');
select tests.as('dana');
select is((select count(*)::int from profiles), 1, 'a new user gets a profile, and sees only it');
select is((select display_name from profiles), 'Dana', 'the name comes from Google or Apple');
select is((my_account() ->> 'is_anonymous')::boolean, false, 'a Google user is registered');

select tests.as_anon();
select throws_ok($$select * from profiles$$, '42501', null, 'anon cannot read profiles');
select throws_ok($$select * from groups$$, '42501', null, 'anon cannot read groups');
select throws_ok($$select * from trips$$, '42501', null, 'anon cannot read trips');
select throws_ok($$select * from meetups$$, '42501', null, 'anon cannot read meetups');
select throws_ok($$select * from scores$$, '42501', null, 'anon cannot read scores');
select throws_ok($$select create_group('x', 'y')$$, '42501', null, 'anon cannot create a group');
select throws_ok($$select join_group('ABCDEF', 'y')$$, '42501', null, 'anon cannot join');
select is((keepalive() ->> 'ok')::boolean, true, 'anon can call keepalive');

select tests.as('dana');
select throws_ok($$select private.delete_user(auth.uid())$$, '42501', null, 'internal functions are closed to users');
select throws_ok($$select private.absorb(auth.uid(), auth.uid(), null)$$, '42501', null, 'absorb is closed');
select throws_ok($$select * from private.invite_attempts$$, '42501', null, 'attempts are hidden');
select throws_ok($$insert into profiles (id) values (gen_random_uuid())$$, '42501', null, 'cannot create profiles');
select throws_ok($$update profiles set id = gen_random_uuid()$$, '42501', null, 'cannot change a profile id');

select * from finish();

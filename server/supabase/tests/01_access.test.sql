-- Who can reach what in the database directly: row level security on
-- every table, nothing for the anon role, the internal schema closed,
-- and no SQL actions left (they live in the server code, decision 40).
select plan(17);

select ok(bool_and(c.relrowsecurity), 'row level security is on for every table')
  from pg_class c join pg_namespace n on n.oid = c.relnamespace
  where n.nspname in ('public', 'private') and c.relkind = 'r';

select is((select count(*)::int from pg_class c join pg_namespace n on n.oid = c.relnamespace
           where n.nspname = 'public' and c.relkind = 'r'), 8, 'eight tables in public');

select is((select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
           where n.nspname = 'public'), 0, 'no functions in public: the API has no SQL actions');

select tests.user('dana');
select tests.as('dana');
select is((select count(*)::int from profiles), 1, 'a new user gets a profile, and sees only it');
select is((select display_name from profiles), 'Dana', 'the name comes from Google or Apple');

select tests.as_anon();
select throws_ok($$select * from profiles$$, '42501', null, 'anon cannot read profiles');
select throws_ok($$select * from groups$$, '42501', null, 'anon cannot read groups');
select throws_ok($$select * from group_members$$, '42501', null, 'anon cannot read members');
select throws_ok($$select * from trips$$, '42501', null, 'anon cannot read trips');
select throws_ok($$select * from meetups$$, '42501', null, 'anon cannot read meetups');
select throws_ok($$select * from scores$$, '42501', null, 'anon cannot read scores');

select tests.as('dana');
select throws_ok($$select * from private.invite_attempts$$, '42501', null, 'attempts are hidden');
select throws_ok($$select * from private.audit_log$$, '42501', null, 'the audit log is hidden');
select throws_ok($$select * from private.weather_cache$$, '42501', null, 'the stored weather is hidden');
select throws_ok($$insert into profiles (id) values (gen_random_uuid())$$, '42501', null, 'cannot create profiles');
select throws_ok($$update profiles set id = gen_random_uuid()$$, '42501', null, 'cannot change a profile id');
select lives_ok($$update profiles set display_name = 'Dana B', lang = 'en'$$, 'can change own name and language');

select * from finish();

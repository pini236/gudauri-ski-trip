-- The daily keepalive, and the cleanup of guest identities nobody uses.
select plan(6);

create temp table g (id uuid);
grant all on g to authenticated;

select tests.user('pini');
select tests.user('old_guest', true); select tests.user('new_guest', true); select tests.user('member_guest', true);
select tests.as_owner();
update auth.users set created_at = now() - interval '40 days' where id in (tests.id('old_guest'), tests.id('member_guest'));
update private.heartbeat set cleaned_at = null, beats = 0;

select tests.as('pini');
insert into g select create_group('Ski', 'Pini');
select tests.as('member_guest');
select join_group(tests.code(id), 'Guest') from g;

select tests.as_anon();
select is((keepalive() ->> 'removed_guests')::int, 1, 'the first call of the day removes one unused old guest');
select is((keepalive() ->> 'removed_guests')::int, 0, 'later calls that day do not clean again');
select tests.as_owner();
select is((select beats from private.heartbeat), 2::bigint, 'each call counts');
select is((select count(*)::int from auth.users where id = tests.id('old_guest')), 0, 'the old guest in no group is gone');
select is((select count(*)::int from auth.users where id = tests.id('new_guest')), 1, 'a new guest stays');
select is((select count(*)::int from auth.users where id = tests.id('member_guest')), 1, 'a guest in a group stays');

select * from finish();

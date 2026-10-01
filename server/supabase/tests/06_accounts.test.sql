-- Losing and keeping a guest identity: "I'm already in the group",
-- a guest becoming an account, and deleting an account.
select plan(28);

create temp table g (name text, id uuid);
create temp table k (ticket text);
grant all on g, k to authenticated;

select tests.user('pini'); select tests.user('dobi'); select tests.user('existing');
select tests.user('old_phone', true); select tests.user('new_phone', true); select tests.user('stranger', true);
select tests.user('guest', true);

select tests.as('pini');
insert into g select 'ski', create_group('Ski', 'Pini');
insert into g select 'other', create_group('Other', 'Pini');
select tests.as('old_phone');
select join_group(tests.code(id), 'Moishi') from g where name = 'ski';
select join_group(tests.code(id), 'Moishi') from g where name = 'other';
insert into trips (out_flight) values ('6H 897');
select set_my_membership(id, 'Moishi', (select id from trips)) from g where name = 'ski';
select submit_score('downhill', 400);

-- "I'm already in the group": the old phone is gone
select tests.as('new_phone');
select is((select request_reclaim(tests.code(id), tests.id('old_phone')) ->> 'status' from g where name = 'ski'), 'pending',
  'the new phone asks to be Moishi again');
select is((select request_reclaim(tests.code(id), tests.id('pini')) ->> 'status' from g where name = 'ski'), 'sign_in_instead',
  'a registered member just signs in again');
select is((select request_reclaim(tests.code(id), gen_random_uuid()) ->> 'status' from g where name = 'ski'), 'no_such_member',
  'only someone in the group');
select tests.as('pini');
select lives_ok($$select decide_join_request(id, true) from join_requests where status = 'pending'$$, 'the admin approves');
select tests.as('new_phone');
select is((select display_name from group_members where user_id = auth.uid()), 'Moishi', 'back as Moishi');
select is((select count(*)::int from group_members where display_name = 'Moishi'), 1, 'without a duplicate');
select is((select out_flight from trips t join group_members gm on gm.trip_id = t.id where gm.user_id = auth.uid()), '6H 897',
  'with the flight');
select is((select best from scores where user_id = auth.uid() and game = 'downhill'), 400, 'and the score');
select is((select count(*)::int from groups), 1, 'only the group whose admin approved, not the other one');

-- A stranger asking to be someone: the admin rejects
select tests.as('stranger');
select request_reclaim(tests.code(id), tests.id('new_phone')) from g where name = 'ski';
select tests.as('pini');
select decide_join_request(id, false) from join_requests where status = 'pending';
select tests.as('stranger');
select is((select count(*)::int from groups), 0, 'rejected: not a member');
select is((select status from join_requests), 'rejected', 'and they can see it was rejected');

-- A guest becoming an account that already exists (Google account
-- used before): ticket as the guest, merge as the account.
select tests.as('guest');
select join_group(tests.code(id), 'Shruli') from g where name = 'ski';
insert into trips (out_flight) values ('6H 123');
select set_my_membership(id, 'Shruli', (select id from trips where owner_id = auth.uid())) from g where name = 'ski';
select submit_score('downhill', 800);
insert into k select create_merge_ticket();
select tests.as('existing');
select submit_score('downhill', 100);
select throws_ok($$select merge_guest('wrong')$$, 'invalid_ticket', 'a wrong ticket fails');
select lives_ok($$select merge_guest(ticket) from k$$, 'the account takes over the guest');
select is((select display_name from group_members where user_id = auth.uid()), 'Shruli', 'now in the group, same name');
select is((select count(*)::int from trips where owner_id = auth.uid()), 1, 'owns the trip');
select is((select best from scores where user_id = auth.uid()), 800, 'keeps the better score');
select throws_ok($$select merge_guest(ticket) from k$$, 'invalid_ticket', 'a ticket works once');
select tests.as_owner();
select is((select count(*)::int from auth.users where id = tests.id('guest')), 0, 'the guest identity is gone');
select tests.as('dobi');
select throws_ok($$select create_merge_ticket()$$, 'not_a_guest', 'only guests get a merge ticket');

-- A guest linking Google to the same identity keeps everything (no merge needed)
select tests.as('new_phone');
select tests.as_owner();
update auth.users set is_anonymous = false where id = tests.id('new_phone');
select tests.as('new_phone');
select is((my_account() ->> 'is_anonymous')::boolean, false, 'a guest who links Google is registered, same id');

-- Deleting an account
select tests.as('dobi');
select join_group(tests.code(id), 'Dobi') from g where name = 'ski';
insert into meetups (group_id, station, meet_at) select id, 'kudebi-top', now() from g where name = 'ski';
-- (everything here happens in one transaction, so set the join order by hand)
select tests.as_owner();
update group_members set joined_at = now() - interval '1 day' where user_id = tests.id('new_phone');
select tests.as('pini');
select lives_ok($$select delete_my_account()$$, 'Pini deletes his account');
select tests.as_owner();
select is((select count(*)::int from auth.users where id = tests.id('pini')), 0, 'the user is gone');
select is((select count(*)::int from profiles where id = tests.id('pini')), 0, 'the profile is gone');
select is((select count(*)::int from group_members where user_id = tests.id('pini')), 0, 'the memberships are gone');
select is((select user_id from group_members gm join g on g.id = gm.group_id and g.name = 'ski' where gm.role = 'admin'),
  tests.id('new_phone'), 'the earliest registered member became admin');
select is((select count(*)::int from meetups), 1, 'meetups by others stay');
select tests.as('dobi');
select lives_ok($$select delete_my_account()$$, 'Dobi deletes his account too');
select tests.as_owner();
select is((select count(*)::int from meetups m join g on g.id = m.group_id and g.name = 'ski' where m.created_by is null), 1,
  'his meetup stays in the group without his name');

select * from finish();

-- What apps read and write directly (everything else goes through the
-- server code): each group sees only its own things, a group sees only
-- the trip a member chose for it, and the tables the server owns are
-- read-only for apps.
select plan(32);

create temp table ids (name text primary key, id uuid);
grant all on ids to authenticated;

select tests.user('pini'); select tests.user('dobi'); select tests.user('outsider');
select tests.user('guest', true);

-- The data, written as the server would (the database owner).
insert into ids values ('ski', gen_random_uuid()), ('work', gen_random_uuid()),
  ('pini_ski', gen_random_uuid()), ('pini_work', gen_random_uuid()), ('pini_private', gen_random_uuid());
insert into groups (id, name, created_by) select id, 'Ski', tests.id('pini') from ids where name = 'ski';
insert into groups (id, name, created_by) select id, 'Work', tests.id('pini') from ids where name = 'work';
insert into trips (id, owner_id, destination, out_flight) select id, tests.id('pini'), 'Gudauri', '6H 897' from ids where name = 'pini_ski';
insert into trips (id, owner_id, destination) select id, tests.id('pini'), 'Elsewhere' from ids where name = 'pini_work';
insert into trips (id, owner_id, destination) select id, tests.id('pini'), 'Secret' from ids where name = 'pini_private';
insert into group_members (group_id, user_id, role, display_name, trip_id)
  select (select id from ids where name = 'ski'), tests.id('pini'), 'admin', 'Pini', (select id from ids where name = 'pini_ski');
insert into group_members (group_id, user_id, role, display_name, trip_id)
  select (select id from ids where name = 'work'), tests.id('pini'), 'admin', 'Pini', (select id from ids where name = 'pini_work');
insert into group_members (group_id, user_id, display_name)
  select (select id from ids where name = 'ski'), tests.id('dobi'), 'Dobi';
insert into group_members (group_id, user_id, display_name)
  select (select id from ids where name = 'ski'), tests.id('guest'), 'Guest';
insert into invites (group_id, code, token) select id, 'ABCDEF', repeat('t', 32) from ids where name = 'ski';
insert into scores (user_id, game, best) values (tests.id('pini'), 'downhill', 700), (tests.id('outsider'), 'downhill', 5);
insert into meetups (group_id, station, meet_at, created_by) select id, 'goodaura-bottom', now(), tests.id('pini') from ids where name = 'ski';
insert into join_requests (group_id, user_id, display_name, kind) select id, tests.id('outsider'), 'Out', 'approval' from ids where name = 'ski';

-- An outsider
select tests.as('outsider');
select is((select count(*)::int from groups), 0, 'an outsider sees no group');
select is((select count(*)::int from group_members), 0, 'no members');
select is((select count(*)::int from invites), 0, 'no invites');
select is((select count(*)::int from trips), 0, 'no trips');
select is((select count(*)::int from meetups), 0, 'no meetups');
select is((select count(*)::int from scores), 1, 'only their own score');
select is((select count(*)::int from join_requests), 1, 'their own request');
update trips set destination = 'hacked';
delete from trips;
update meetups set note = 'hacked';
delete from meetups;
select throws_ok($$insert into meetups (group_id, station, meet_at) select id, 'x', now() from ids where name = 'ski'$$,
  '42501', null, 'cannot add a meetup to a group they are not in');

-- A member
select tests.as('dobi');
select is((select count(*)::int from groups), 1, 'a member sees their group');
select is((select count(*)::int from group_members), 3, 'and all its members');
select is((select count(*)::int from invites), 1, 'and the invite, to share it');
select is((select count(*)::int from trips), 1, 'only the trip shown in the group');
select is((select destination from trips), 'Gudauri', 'the right one');
select is((select count(*)::int from join_requests), 0, 'not other people''s requests');
select is((select count(*)::int from scores), 1, 'scores of people in the same group');
update trips set destination = 'mine';
select is((select destination from trips where owner_id = tests.id('pini')), 'Gudauri',
  'cannot change someone else''s trip');

-- Tables the server owns are read-only for apps
select throws_ok($$insert into groups (name) values ('mine')$$, '42501', null, 'no direct insert into groups');
select throws_ok($$update group_members set role = 'admin'$$, '42501', null, 'no direct role change');
select throws_ok($$delete from group_members$$, '42501', null, 'no direct removal of members');
select throws_ok($$update invites set uses = 0$$, '42501', null, 'no direct change of invites');
select throws_ok($$insert into join_requests (group_id, user_id, display_name, kind) select id, auth.uid(), 'x', 'approval' from ids where name = 'ski'$$,
  '42501', null, 'no direct requests');
select throws_ok($$insert into scores (user_id, game, best) values (auth.uid(), 'downhill', 99999)$$, '42501', null,
  'scores go through the server');

-- What apps do write directly: their trips and the group's meetups
with x as (insert into trips (destination, out_from) values ('Mine', 'TLV') returning entered_by)
select is((select entered_by from x), tests.id('dobi'), 'a trip of my own, marked as entered by me');
select throws_ok($$insert into trips (owner_id, destination) values (tests.id('pini'), 'x')$$, '42501', null,
  'not for someone else');
select throws_ok($$insert into trips (destination, out_from) values ('x', 'tel aviv')$$, '23514', null,
  'airport codes are three capital letters');
select throws_ok($$update trips set owner_id = auth.uid()$$, '42501', null, 'the owner cannot be changed');
update meetups set meet_at = now() + interval '1 hour';
select is((select updated_by from meetups), tests.id('dobi'), 'a member edits a meetup, marked as theirs');
select throws_ok($$update meetups set group_id = (select id from ids where name = 'work')$$, '42501', null,
  'cannot move a meetup to another group');
select throws_ok($$insert into meetups (group_id, station, meet_at) select id, 'bad station!', now() from ids where name = 'ski'$$,
  '23514', null, 'the station is an id from the map data');

-- A meetup made without signal is sent with an id the phone chose, and may be sent twice
insert into meetups (id, group_id, station, meet_at) select '11111111-1111-4111-8111-111111111111', id, 'goodaura-bottom', now() from ids where name = 'ski';
select is((select count(*)::int from meetups where id = '11111111-1111-4111-8111-111111111111'), 1, 'a member sends a new meetup with an id of their own');
insert into meetups (id, group_id, station, meet_at) select '11111111-1111-4111-8111-111111111111', id, 'goodaura-bottom', now() from ids where name = 'ski'
  on conflict (id) do nothing;
select is((select count(*)::int from meetups where id = '11111111-1111-4111-8111-111111111111'), 1, 'the same meetup sent again is still one');

select tests.as_owner();
select is((select count(*)::int from trips where destination in ('hacked', 'mine')), 0, 'nobody changed others'' trips');

select * from finish();

-- Trips belong to their owner. A group sees only the trip its member
-- chose for it. "I'm on the same flight", and an admin filling in.
select plan(27);

create temp table g (name text, id uuid);
create temp table t (name text, id uuid);
grant all on g, t to authenticated;

select tests.user('pini'); select tests.user('dobi'); select tests.user('outsider');
select tests.user('guest', true);

-- Pini's two trips, and two groups
select tests.as('pini');
with x as (insert into trips (destination, out_date, out_flight, out_from, out_to, out_departs,
  ret_date, ret_flight, ret_from, ret_to)
  values ('Gudauri', '2027-01-10', '6H 897', 'TLV', 'TBS', '16:00', '2027-01-15', '6H 892', 'TBS', 'TLV') returning id)
insert into t select 'pini-ski', id from x;
with x as (insert into trips (destination) values ('Elsewhere') returning id) insert into t select 'pini-other', id from x;
select is((select count(*)::int from trips), 2, 'the owner sees their trips');
select is((select entered_by from trips where id = (select id from t where name = 'pini-ski')), tests.id('pini'), 'entered by the owner');
insert into g select 'ski', create_group('Ski', 'Pini', null, null, (select id from t where name = 'pini-ski'));
insert into g select 'work', create_group('Work', 'Pini');
select set_my_membership((select id from g where name = 'work'), 'Pini', (select id from t where name = 'pini-other'));

select throws_ok($$insert into trips (owner_id, destination) values (tests.id('dobi'), 'x')$$, '42501', null,
  'cannot create a trip for someone else');
select throws_ok($$insert into trips (destination, out_from) values ('x', 'tel aviv')$$, '23514', null,
  'airport codes are three capital letters');

-- Others
select tests.as('outsider');
select is((select count(*)::int from trips), 0, 'an outsider sees no trips');
update trips set destination = 'hacked';
delete from trips;
select tests.as('pini');
select is((select count(*)::int from trips where destination <> 'hacked'), 2, 'an outsider cannot change or delete them');
select tests.as('outsider');

select tests.as('dobi');
select join_group(tests.code(id), 'Dobi') from g where name = 'ski';
select is((select count(*)::int from trips), 1, 'a member sees only the trip shown in their group');
select is((select destination from trips), 'Gudauri', 'the right one');
select throws_ok($$select set_my_membership((select id from g where name = 'ski'), 'Dobi', (select id from t where name = 'pini-ski'))$$,
  'trip_not_yours', 'a member cannot show someone else''s trip as theirs');
select throws_ok($$update trips set owner_id = auth.uid()$$, '42501', null, 'the owner cannot be changed');

-- "I'm on the same flight"
insert into t select 'dobi-ski', same_flight((select id from g where name = 'ski'), (select id from t where name = 'pini-ski'));
select is((select out_flight from trips where id = (select id from t where name = 'dobi-ski')), '6H 897', 'same flight copies the flights');
select is((select owner_id from trips where id = (select id from t where name = 'dobi-ski')), tests.id('dobi'), 'into a trip of their own');
select is((select trip_id from group_members where user_id = tests.id('dobi')), (select id from t where name = 'dobi-ski'),
  'shown in the group');
update trips set out_flight = '6H 999' where id = (select id from t where name = 'dobi-ski');
select is((select out_flight from trips where id = (select id from t where name = 'pini-ski')), '6H 897',
  'changing my copy does not change the original');

select tests.as('outsider');
select throws_ok($$select same_flight((select id from g where name = 'ski'), (select id from t where name = 'pini-ski'))$$,
  'not_member', 'an outsider cannot copy a flight');

-- Leaving: the trip stops showing there
select tests.as('dobi');
select leave_group(id) from g where name = 'ski';
select is((select count(*)::int from trips), 1, 'after leaving, only their own trip is left');

-- An admin fills in for a member who has not
select tests.as('guest');
select join_group(tests.code(id), 'Guest') from g where name = 'ski';
select tests.as('dobi');
select join_group(tests.code(id), 'Dobi') from g where name = 'ski';
select throws_ok($$select set_member_trip((select id from g where name = 'ski'), tests.id('guest'), '{"out_flight":"6H 897"}')$$,
  'not_admin', 'a member cannot fill in for another');
select tests.as('pini');
insert into t select 'guest-ski', set_member_trip((select id from g where name = 'ski'), tests.id('guest'),
  '{"out_date":"2027-01-10","out_flight":"6H 897","out_from":"TLV","out_to":"TBS"}');
select is((select out_flight from trips where id = (select id from t where name = 'guest-ski')), '6H 897', 'the admin filled it in');
select tests.as('guest');
select is((select owner_id from trips where id = (select id from t where name = 'guest-ski')), tests.id('guest'), 'it belongs to the member');
select is((select entered_by from trips where id = (select id from t where name = 'guest-ski')), tests.id('pini'), 'marked as entered by the admin');
select tests.as('pini');
select lives_ok($$select set_member_trip((select id from g where name = 'ski'), tests.id('guest'), '{"out_flight":"6H 111"}')$$,
  'the admin can still correct it');
select tests.as('guest');
update trips set out_flight = '6H 897' where id = (select id from t where name = 'guest-ski');
select is((select entered_by from trips where id = (select id from t where name = 'guest-ski')), tests.id('guest'),
  'once the member edits it, it is theirs');
select tests.as('pini');
select throws_ok($$select set_member_trip((select id from g where name = 'ski'), tests.id('guest'), '{"out_flight":"6H 111"}')$$,
  'member_owns_trip', 'and the admin can no longer change it');
select throws_ok($$select set_member_trip((select id from g where name = 'ski'), tests.id('dobi'), '{"out_from":"tel"}')$$,
  '23514', null, 'admin input is checked like any other');

-- A member in two groups: each group sees only its own trip
select tests.as('dobi');
select join_group(tests.code(id), 'Dobi') from g where name = 'work';
select is((select count(*)::int from trips where owner_id = tests.id('pini')), 2, 'in both groups: both of Pini''s trips');
select leave_group(id) from g where name = 'ski';
select is((select destination from trips where owner_id = tests.id('pini')), 'Elsewhere', 'only work: only the work trip');

select tests.as('pini');
select throws_ok($$insert into trips (destination) select 'x' from generate_series(1, 30)$$, 'too_many_trips', 'a limit on trips per person');

select * from finish();

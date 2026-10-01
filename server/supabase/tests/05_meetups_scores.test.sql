-- Meetups: every member creates and edits; nobody else sees them.
-- Scores: the best one is kept, and group members see each other's.
select plan(19);

create temp table g (name text, id uuid);
create temp table m (id uuid);
grant all on g, m to authenticated;

select tests.user('pini'); select tests.user('dobi'); select tests.user('outsider');
select tests.user('guest', true);

select tests.as('pini');
insert into g select 'ski', create_group('Ski', 'Pini');
select tests.as('outsider');
insert into g select 'other', create_group('Other', 'Outsider');
select tests.as('guest');
select join_group(tests.code(id), 'Guest') from g where name = 'ski';

-- Meetups
select tests.as('guest');
with x as (insert into meetups (group_id, station, meet_at, note)
           select id, 'goodaura-bottom', '2027-01-11 09:30+04', 'Coffee first' from g where name = 'ski' returning id)
insert into m select id from x;
select is((select created_by from meetups), tests.id('guest'), 'a guest member creates a meetup, marked as theirs');
select tests.as('pini');
select is((select count(*)::int from meetups), 1, 'every member sees it');
update meetups set meet_at = '2027-01-11 10:00+04';
select is((select updated_by from meetups), tests.id('pini'), 'another member edits it');
select throws_ok($$insert into meetups (group_id, station, meet_at) select id, 'x', now() from g where name = 'other'$$,
  '42501', null, 'cannot add a meetup to a group you are not in');
select throws_ok($$update meetups set group_id = (select id from g where name = 'other')$$,
  '42501', null, 'cannot move a meetup to another group');
select throws_ok($$insert into meetups (group_id, station, meet_at) select id, 'bad station!', now() from g where name = 'ski'$$,
  '23514', null, 'the station is an id from the map data');
select throws_ok($$insert into meetups (group_id, station, meet_at, created_by) select id, 'x', now(), tests.id('dobi') from g where name = 'ski'$$,
  '42501', null, 'cannot write as someone else');

select tests.as('outsider');
select is((select count(*)::int from meetups), 0, 'an outsider sees no meetups');
update meetups set note = 'hacked';
delete from meetups;
select tests.as('pini');
select is((select note from meetups), 'Coffee first', 'an outsider cannot change or delete them');
select tests.as('guest');
delete from meetups;
select is((select count(*)::int from meetups), 0, 'a member deletes a meetup');

-- Scores
select tests.as('guest');
select is(submit_score('downhill', 500), 500, 'the first score is the best');
select is(submit_score('downhill', 300), 500, 'a lower score does not replace it');
select is(submit_score('downhill', 900), 900, 'a higher one does');
select throws_ok($$select submit_score('downhill', -1)$$, '23514', null, 'no negative scores');
select throws_ok($$select submit_score('Not A Game!', 1)$$, '23514', null, 'game ids are simple names');
select throws_ok($$insert into scores (user_id, game, best) values (auth.uid(), 'downhill', 99999)$$, '42501', null,
  'scores are written only through submit_score');

select tests.as('pini');
select submit_score('downhill', 700);
select results_eq($$select display_name, best from group_leaderboard((select id from g where name = 'ski'), 'downhill')$$,
  $$values ('Guest'::text, 900), ('Pini'::text, 700)$$, 'the group leaderboard, best first, with names in the group');

select tests.as('outsider');
select is((select count(*)::int from scores where user_id <> auth.uid()), 0, 'an outsider sees no one else''s scores');
select throws_ok($$select * from group_leaderboard((select id from g where name = 'ski'), 'downhill')$$,
  'not_member', 'or the leaderboard');

select * from finish();

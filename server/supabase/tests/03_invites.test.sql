-- Invites: code and link, expiry, revoke, limits, manual approval, and
-- guessing attacks on the six-letter code.
select plan(34);

create temp table g (id uuid);
grant all on g to authenticated;

select tests.user('admin');
select tests.user('a', true); select tests.user('b', true); select tests.user('c', true);
select tests.user('d', true); select tests.user('e', true); select tests.user('attacker', true);
select tests.user('attacker2', true);

select tests.as('admin');
insert into g select create_group('Trip', 'Admin', current_date, current_date + 5);

-- Code and link
select tests.as('a');
select is((select join_group(lower(tests.code(id)), 'A') ->> 'status' from g), 'joined', 'the code works in lower case');
select tests.as('b');
select is((select join_group(substr(tests.code(id), 1, 3) || '-' || substr(tests.code(id), 4), 'B') ->> 'status' from g),
  'joined', 'the code works with a dash');
select tests.as('c');
select is((select join_group(tests.token(id), 'C') ->> 'status' from g), 'joined', 'the link token works');
select is((select join_group(tests.token(id), 'C') ->> 'status' from g), 'already_member', 'joining twice is harmless');
select is((select uses from invites where revoked_at is null), 3, 'uses are counted');

-- Preview: group and names, for "I'm already in the group"
select tests.as('d');
select is((select invite_preview(tests.code(id)) ->> 'name' from g), 'Trip', 'the preview shows the group name');
select is((select jsonb_array_length(invite_preview(tests.code(id)) -> 'members') from g), 4, 'and the member names');
select is((select count(*)::int from groups), 0, 'a preview does not make you a member');

-- Revoke and replace
select tests.as('admin');
create temp table old_code as select tests.code(id) as code from g;
grant select on old_code to authenticated;
select lives_ok($$select create_invite(id) from g$$, 'the admin replaces the invite');
select isnt((select tests.code(id) from g), (select code from old_code), 'with a new code');
select tests.as('d');
select is((select join_group(code, 'D') ->> 'status' from old_code), 'invalid_code', 'the old code no longer works');
select is((select join_group(tests.code(id), 'D') ->> 'status' from g), 'joined', 'the new code works');

select tests.as('admin');
select revoke_invite(i.id) from invites i where revoked_at is null;
select tests.as('e');
select is((select join_group(code, 'E') ->> 'status' from old_code), 'invalid_code', 'a revoked code fails');

-- Expiry: by default the day after the group ends
select tests.as('admin');
select create_invite(id) from g;
select update_group(id, 'Trip', current_date - 10, current_date - 2) from g;
select tests.as('e');
select is((select join_group(tests.code(id), 'E') ->> 'status' from g), 'invalid_code', 'an invite expires after the trip');
select tests.as('admin');
select update_group(id, 'Trip', current_date, current_date + 5) from g;
select tests.as('e');
select is((select invite_preview(tests.code(id)) ->> 'status' from g), 'ok', 'moving the dates brings it back');

-- An explicit expiry, and a use limit
select tests.as('admin');
select create_invite(id, false, null, now() - interval '1 minute') from g;
select tests.as('e');
select is((select join_group(tests.code(id), 'E') ->> 'status' from g), 'invalid_code', 'an explicit expiry is respected');
select tests.as('admin');
select create_invite(id, false, 1) from g;
select tests.as('e');
select is((select join_group(tests.code(id), 'E') ->> 'status' from g), 'joined', 'a one-use invite works once');
select tests.as('attacker2');
select is((select join_group(tests.code(id), 'X') ->> 'status' from g), 'invalid_code', 'and not twice');

-- Manual approval
select tests.as('admin');
select create_invite(id, true) from g;
select remove_member(id, tests.id('e')) from g;
select tests.as('e');
select is((select join_group(tests.code(id), 'E again') ->> 'status' from g), 'pending', 'with approval on, joining waits');
select is((select count(*)::int from groups), 0, 'a pending person is not a member');
select is((select count(*)::int from join_requests where status = 'pending'), 1, 'they see their own request');
select tests.as_owner();
create temp table req as select id from join_requests where status = 'pending';
grant select on req to authenticated;
select tests.as('a');
select is((select count(*)::int from join_requests), 0, 'other members do not see requests');
select throws_ok($$select decide_join_request(id, true) from req$$, 'not_admin', 'a member cannot approve');
select throws_ok($$select decide_join_request(id, false) from req$$, 'not_admin', 'or reject');
select tests.as('admin');
select is((select count(*)::int from join_requests where status = 'pending'), 1, 'the admin sees the request');
select lives_ok($$select decide_join_request(id, true) from join_requests where status = 'pending'$$, 'the admin approves');
select tests.as('e');
select is((select count(*)::int from groups), 1, 'approved: now a member');

-- Guessing the code: five wrong guesses in 15 minutes, then blocked,
-- even with the right code. Other people are not affected.
select tests.as('attacker');
select join_group(c, 'X') from unnest(array['AAAAAA','BBBBBB','CCCCCC','DDDDDD','EEEEEE']) c;
select is((select join_group(tests.code(id), 'X') ->> 'status' from g), 'rate_limited', 'after five wrong guesses: blocked');
select is((select invite_preview(tests.code(id)) ->> 'status' from g), 'rate_limited', 'the preview too');
select is((select request_reclaim(tests.code(id), tests.id('a')) ->> 'status' from g), 'rate_limited', 'and "I am already in"');
select tests.as('attacker2');
select is((select invite_preview(tests.code(id)) ->> 'status' from g), 'ok', 'another person is not blocked');

-- Twenty wrong guesses a day, even spread out
select tests.as_owner();
insert into private.invite_attempts (user_id, at, ok)
  select tests.id('attacker2'), now() - interval '1 hour' * i, false from generate_series(1, 20) i;
select tests.as('attacker2');
select is((select invite_preview(tests.code(id)) ->> 'status' from g), 'rate_limited', 'twenty wrong guesses in a day: blocked');

-- Many people guessing together (new guest identities): a global brake
select tests.as_owner();
delete from private.invite_attempts;
insert into private.invite_attempts (user_id, ok) select gen_random_uuid(), false from generate_series(1, 300);
select tests.as('a');
select is((select invite_preview(tests.code(id)) ->> 'status' from g), 'rate_limited', 'three hundred wrong guesses an hour: codes pause for everyone');
select is((select invite_preview(tests.token(id)) ->> 'status' from g), 'ok', 'links (long tokens) keep working');

select * from finish();

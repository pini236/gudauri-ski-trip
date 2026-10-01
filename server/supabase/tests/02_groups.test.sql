-- Groups, members and roles: who sees a group, who manages it, and that
-- a group always keeps a registered admin when it can.
select plan(31);

create temp table g (id uuid);
grant all on g to authenticated;

select tests.user('pini');  select tests.user('dobi');  select tests.user('outsider');
select tests.user('guest', true);

-- Creating
select tests.as('guest');
select throws_ok($$select create_group('Gudauri', 'Guest')$$, 'must_register', 'a guest cannot create a group');

select tests.as('pini');
insert into g select create_group('Gudauri 2027', 'פיני', '2027-01-10', '2027-01-15');
select is((select count(*)::int from groups), 1, 'the creator sees the group');
select is((select role from group_members where user_id = tests.id('pini')), 'admin', 'the creator is admin');
select is((select count(*)::int from invites where revoked_at is null), 1, 'a new group starts with an invite');
select throws_ok($$select create_group('x', '   ')$$, 'invalid_name', 'an empty name is refused');

-- Direct writes are closed: everything goes through the functions.
select throws_ok($$insert into groups (name) values ('mine')$$, '42501', null, 'no direct insert into groups');
select throws_ok($$update group_members set role = 'admin'$$, '42501', null, 'no direct role change');
select throws_ok($$insert into group_members (group_id, user_id, display_name) select id, tests.id('outsider'), 'x' from g$$,
  '42501', null, 'no direct adding of members');
select throws_ok($$delete from groups$$, '42501', null, 'no direct delete of groups');

-- Joining (details in 03_invites)
select tests.as('dobi');
select is((select join_group(tests.code(g.id), 'דובי') ->> 'status' from g), 'joined', 'dobi joins with the code');
select tests.as('guest');
select is((select join_group(tests.code(g.id), 'אורח') ->> 'status' from g), 'joined', 'a guest joins with the code');
select is((select count(*)::int from group_members), 3, 'a member sees all members');

-- Outsiders see nothing
select tests.as('outsider');
select is((select count(*)::int from groups), 0, 'an outsider does not see the group');
select is((select count(*)::int from group_members), 0, 'an outsider does not see members');
select is((select count(*)::int from invites), 0, 'an outsider does not see invites');
select throws_ok($$select update_group(id, 'mine', null, null) from g$$, 'not_admin', 'an outsider cannot rename');

-- Members are not admins
select tests.as('dobi');
select is((select count(*)::int from invites), 1, 'a member sees the invite (to share it)');
select throws_ok($$select update_group(id, 'x', null, null) from g$$, 'not_admin', 'a member cannot edit the group');
select throws_ok($$select create_invite(id) from g$$, 'not_admin', 'a member cannot replace the invite');
select throws_ok($$select remove_member(id, tests.id('guest')) from g$$, 'not_admin', 'a member cannot remove members');
select throws_ok($$select set_member_role(id, tests.id('dobi'), 'admin') from g$$, 'not_admin', 'a member cannot make themselves admin');
select throws_ok($$select delete_group(id) from g$$, 'not_admin', 'a member cannot delete the group');

-- Admins
select tests.as('pini');
select throws_ok($$select set_member_role(id, tests.id('guest'), 'admin') from g$$, 'admin_must_register', 'an admin must be registered');
select throws_ok($$select set_member_role(id, tests.id('pini'), 'member') from g$$, 'last_admin', 'the last admin cannot step down');
select lives_ok($$select set_member_role(id, tests.id('dobi'), 'admin') from g$$, 'appoint a second admin');
select is((select count(*)::int from group_members where role = 'admin'), 2, 'two admins');

-- The last admin leaving: the earliest registered member takes over
select set_member_role(id, tests.id('dobi'), 'member') from g;
select leave_group(id) from g;
select tests.as('dobi');
select is((select role from group_members where user_id = tests.id('dobi')), 'admin', 'when the admin leaves, a registered member becomes admin');

-- Only guests left: no admin, until someone registers and claims it
select leave_group(id) from g;
select tests.as('guest');
select is((select count(*)::int from group_members where role = 'admin'), 0, 'only guests left: no admin');
select throws_ok($$select claim_admin(id) from g$$, 'must_register', 'a guest cannot claim it');
select tests.as_owner();
update auth.users set is_anonymous = false where id = tests.id('guest');  -- signs in with Google, same id
select tests.as('guest');
select lives_ok($$select claim_admin(id) from g$$, 'after registering, they can claim it');

-- The last member leaving deletes the group
select leave_group(id) from g;
select tests.as_owner();
select is((select count(*)::int from groups where id = (select id from g)), 0, 'the last member leaving deletes the group');
select * from finish();

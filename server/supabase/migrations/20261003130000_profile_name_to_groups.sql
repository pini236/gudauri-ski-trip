-- Decision 50: changing the name on your account changes it in every group you are in. One place, here in the
-- database, so the site and the apps only update profiles.display_name (directly, as before) and need no loop
-- over the groups. A name chosen for one group (set_my_membership) stays until the account name changes again.
create function private.profile_name_to_groups() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.display_name is not null and new.display_name is distinct from old.display_name then
    update public.group_members set display_name = new.display_name
    where user_id = new.id and display_name is distinct from new.display_name;
  end if;
  return null;
end $$;
revoke all on function private.profile_name_to_groups() from public;

create trigger profiles_name_to_groups after update of display_name on public.profiles
  for each row execute function private.profile_name_to_groups();

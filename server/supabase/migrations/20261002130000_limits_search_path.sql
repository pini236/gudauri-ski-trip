-- From Supabase's security check: give private.limits() a fixed search
-- path, like every other function here. No change in behavior.
alter function private.limits() set search_path = '';
